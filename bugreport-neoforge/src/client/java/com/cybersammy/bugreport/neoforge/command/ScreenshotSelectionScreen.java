package com.cybersammy.bugreport.neoforge.command;

import com.cybersammy.bugreport.api.specification.RelativePath;
import com.cybersammy.bugreport.core.source.ReviewedCollectionPlan;
import com.cybersammy.bugreport.core.source.ScreenshotCollectionRequest;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Explicit bounded picker for recent local screenshots and a new in-game capture. */
public final class ScreenshotSelectionScreen extends Screen {
    private static final int MAX_DIRECTORY_ENTRIES = 128;
    private static final int MAX_INPUT_BYTES = 32 * 1024 * 1024;
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final int MAX_SCREENSHOTS_PER_PAGE = 5;
    private static final int SCREENSHOT_ROW_HEIGHT = 40;
    private static final long PREVIEW_TIMEOUT_SECONDS = 5;
    private static final DateTimeFormatter CAPTURE_NAME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd_HH.mm.ss.SSS", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private final BugReportCommandService commands;
    private final Screen parent;
    private final ReportSessionId sessionId;
    private final ReviewedCollectionPlan reviewed;
    private final Consumer<BugReportCommandService.CollectionExecutionRequest> completion;
    private final Path screenshotsDirectory;
    private final Map<RelativePath, ScreenshotCollectionRequest.SelectedImage> selected =
            new LinkedHashMap<>();
    private List<Candidate> candidates = List.of();
    private int page;
    private boolean loading;
    private boolean completing;
    private Component status = Component.translatable("bugreport.screen.screenshot.loading");
    private ResourceLocation previewTexture;
    private int previewWidth;
    private int previewHeight;
    private long previewGeneration;
    private RelativePath previewingPath;
    private Thread previewWorker;

    public ScreenshotSelectionScreen(
            BugReportCommandService commands,
            Screen parent,
            ReportSessionId sessionId,
            ReviewedCollectionPlan reviewed,
            Consumer<BugReportCommandService.CollectionExecutionRequest> completion) {
        super(Component.translatable("bugreport.screen.screenshot.title"));
        this.commands = java.util.Objects.requireNonNull(commands, "commands");
        this.parent = java.util.Objects.requireNonNull(parent, "parent");
        this.sessionId = java.util.Objects.requireNonNull(sessionId, "sessionId");
        this.reviewed = java.util.Objects.requireNonNull(reviewed, "reviewed");
        this.completion = java.util.Objects.requireNonNull(completion, "completion");
        screenshotsDirectory = Minecraft.getInstance().gameDirectory.toPath()
                .toAbsolutePath()
                .normalize()
                .resolve("screenshots");
    }

    @Override
    protected void init() {
        rebuildWidgets();
        if (!loading && candidates.isEmpty()) {
            loadCandidates();
        }
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int pageSize = screenshotsPerPage();
        int first = page * pageSize;
        int last = Math.min(first + pageSize, candidates.size());
        for (int index = first; index < last; index++) {
            Candidate candidate = candidates.get(index);
            boolean included = selected.containsKey(candidate.path());
            Button button = Button.builder(
                            Component.translatable(
                                    included
                                            ? "bugreport.screen.screenshot.selected_state"
                                            : "bugreport.screen.screenshot.excluded_state"),
                            ignored -> toggle(candidate))
                    .bounds(
                            width / 2 - 196,
                            86 + (index - first) * SCREENSHOT_ROW_HEIGHT,
                            210,
                            20)
                    .build();
            button.active = !completing && !candidate.path().equals(previewingPath);
            addRenderableWidget(button);
        }

        Button previous = Button.builder(
                        Component.translatable("bugreport.screen.form.previous"), ignored -> changePage(-1))
                .bounds(width / 2 - 150, height - 82, 70, 20)
                .build();
        previous.active = page > 0 && !completing;
        addRenderableWidget(previous);
        Button next = Button.builder(
                        Component.translatable("bugreport.screen.form.next"), ignored -> changePage(1))
                .bounds(width / 2 + 80, height - 82, 70, 20)
                .build();
        next.active = (page + 1) * pageSize < candidates.size() && !completing;
        addRenderableWidget(next);

        Button capture = Button.builder(
                        Component.translatable("bugreport.screen.screenshot.capture"), ignored -> capture())
                .bounds(width / 2 - 150, height - 56, 96, 20)
                .build();
        capture.active = !completing
                && previewingPath == null
                && selected.size() < ScreenshotCollectionRequest.PRODUCT_MAX_SELECTED_IMAGES;
        addRenderableWidget(capture);
        Button back = Button.builder(Component.translatable("gui.back"), ignored -> minecraft.setScreen(parent))
                .bounds(width / 2 - 48, height - 56, 96, 20)
                .build();
        back.active = !completing;
        addRenderableWidget(back);
        Button continueButton = Button.builder(
                        Component.translatable("bugreport.screen.screenshot.continue"), ignored -> complete())
                .bounds(width / 2 + 54, height - 56, 96, 20)
                .build();
        continueButton.active = !selected.isEmpty() && !completing && previewingPath == null;
        addRenderableWidget(continueButton);
    }

    private void loadCandidates() {
        loading = true;
        Thread.ofVirtual().name("bugreport-screenshot-index").start(() -> {
            List<Candidate> loaded;
            try {
                loaded = scanCandidates();
            } catch (IOException | RuntimeException failure) {
                loaded = List.of();
            }
            List<Candidate> result = loaded;
            Minecraft.getInstance().execute(() -> {
                loading = false;
                candidates = result;
                selected.keySet().retainAll(result.stream()
                        .map(Candidate::path)
                        .collect(java.util.stream.Collectors.toSet()));
                status = result.isEmpty()
                        ? Component.translatable("bugreport.screen.screenshot.empty")
                        : Component.translatable(
                                "bugreport.screen.screenshot.available", result.size());
                int pageSize = screenshotsPerPage();
                page = Math.min(page, Math.max(0, (result.size() - 1) / pageSize));
                rebuildWidgets();
            });
        });
    }

    private List<Candidate> scanCandidates() throws IOException {
        if (!Files.isDirectory(screenshotsDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(screenshotsDirectory)) {
            return List.of();
        }
        List<Path> children;
        try (var stream = Files.list(screenshotsDirectory)) {
            children = stream.limit(MAX_DIRECTORY_ENTRIES + 1L).toList();
        }
        if (children.size() > MAX_DIRECTORY_ENTRIES) {
            throw new IOException("Screenshots directory exceeds the product entry limit");
        }
        List<Candidate> result = new ArrayList<>();
        for (Path child : children) {
            String name = child.getFileName().toString();
            String lower = name.toLowerCase(Locale.ROOT);
            if ((!lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg"))
                    || Files.isSymbolicLink(child)) {
                continue;
            }
            BasicFileAttributes attributes = Files.readAttributes(
                    child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()
                    || attributes.size() > MAX_INPUT_BYTES) {
                continue;
            }
            int[] dimensions = imageDimensions(child);
            if (dimensions == null) {
                continue;
            }
            result.add(new Candidate(
                    RelativePath.of(name),
                    attributes.lastModifiedTime().toInstant(),
                    dimensions[0],
                    dimensions[1]));
        }
        result.sort(Comparator.comparing(Candidate::modified)
                .reversed()
                .thenComparing(candidate -> candidate.path().value()));
        return List.copyOf(result);
    }

    private static int[] imageDimensions(Path path) {
        try (var input = javax.imageio.ImageIO.createImageInputStream(path.toFile())) {
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0
                        || height <= 0
                        || width > 8192
                        || height > 8192
                        || (long) width * height > 16_777_216L) {
                    return null;
                }
                return new int[] {width, height};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }

    private void toggle(Candidate candidate) {
        RelativePath path = candidate.path();
        if (selected.remove(path) != null) {
            rebuildWidgets();
            return;
        }
        if (selected.size() >= ScreenshotCollectionRequest.PRODUCT_MAX_SELECTED_IMAGES) {
            status = Component.translatable(
                    "bugreport.screen.screenshot.limit",
                    ScreenshotCollectionRequest.PRODUCT_MAX_SELECTED_IMAGES);
            return;
        }
        observeAndSelect(path);
    }

    private void observeAndSelect(RelativePath relativePath) {
        cancelPreviewObservation();
        previewingPath = relativePath;
        status = Component.translatable(
                "bugreport.screen.screenshot.previewing", relativePath.value());
        rebuildWidgets();
        long generation = ++previewGeneration;
        Thread worker = Thread.ofVirtual().name("bugreport-screenshot-preview").unstarted(() -> {
            ObservedPreview observed;
            try {
                observed = readObservedPreview(relativePath);
            } catch (IOException | RuntimeException | LinkageError failure) {
                observed = null;
            }
            ObservedPreview result = observed;
            Minecraft.getInstance().execute(
                    () -> finishPreviewObservation(generation, relativePath, result));
        });
        previewWorker = worker;
        worker.start();
        CompletableFuture.delayedExecutor(PREVIEW_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .execute(() -> Minecraft.getInstance().execute(
                        () -> timeoutPreviewObservation(generation, relativePath)));
    }

    private void finishPreviewObservation(
            long generation, RelativePath relativePath, ObservedPreview result) {
        if (generation != previewGeneration || !relativePath.equals(previewingPath)) {
            closePreview(result);
            return;
        }
        previewingPath = null;
        previewWorker = null;
        if (minecraft.screen != this) {
            closePreview(result);
            return;
        }
        if (result == null) {
            status = Component.translatable("bugreport.screen.screenshot.invalid");
            rebuildWidgets();
            return;
        }
        selected.put(relativePath, result.selection());
        releasePreview();
        previewWidth = result.image().getWidth();
        previewHeight = result.image().getHeight();
        previewTexture = minecraft.getTextureManager().register(
                "bugreport-screenshot-preview", new DynamicTexture(result.image()));
        status = Component.translatable(
                "bugreport.screen.screenshot.selected", selected.size());
        rebuildWidgets();
    }

    private void timeoutPreviewObservation(long generation, RelativePath relativePath) {
        if (generation != previewGeneration || !relativePath.equals(previewingPath)) {
            return;
        }
        Thread worker = previewWorker;
        previewingPath = null;
        previewWorker = null;
        previewGeneration++;
        if (worker != null) {
            worker.interrupt();
        }
        status = Component.translatable("bugreport.screen.screenshot.preview_timeout");
        rebuildWidgets();
    }

    private void cancelPreviewObservation() {
        previewGeneration++;
        previewingPath = null;
        Thread worker = previewWorker;
        previewWorker = null;
        if (worker != null) {
            worker.interrupt();
        }
    }

    private static void closePreview(ObservedPreview preview) {
        if (preview != null) {
            preview.image().close();
        }
    }

    private ObservedPreview readObservedPreview(RelativePath relativePath) throws IOException {
        Path path = screenshotsDirectory.resolve(relativePath.value()).normalize();
        if (!screenshotsDirectory.equals(path.getParent())) {
            throw new IOException("Selected screenshot escaped the screenshots directory");
        }
        BasicFileAttributes before = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile()
                || before.isSymbolicLink()
                || before.size() > MAX_INPUT_BYTES) {
            throw new IOException("Selected screenshot is unsafe");
        }
        byte[] bytes = readBounded(path);
        BasicFileAttributes after = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!sameSnapshot(before, after) || bytes.length != after.size()) {
            throw new IOException("Selected screenshot changed while previewing");
        }
        NativeImage image;
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            // The byte[] overload copies the complete compressed image into LWJGL's
            // small thread-local MemoryStack. Real screenshots routinely exceed it.
            image = NativeImage.read(input);
        }
        ScreenshotCollectionRequest.SelectedImage selection =
                new ScreenshotCollectionRequest.SelectedImage(
                        relativePath,
                        after.size(),
                        after.lastModifiedTime().toInstant(),
                        sha256(bytes));
        return new ObservedPreview(selection, image);
    }

    private static byte[] readBounded(Path path) throws IOException {
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (FileChannel channel = FileChannel.open(path, options)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(COPY_BUFFER_BYTES);
            int total = 0;
            while (true) {
                int read = channel.read(buffer);
                if (read < 0) {
                    return output.toByteArray();
                }
                if (read == 0) {
                    continue;
                }
                total = Math.addExact(total, read);
                if (total > MAX_INPUT_BYTES) {
                    throw new IOException("Selected screenshot exceeds the preview byte limit");
                }
                buffer.flip();
                output.write(buffer.array(), 0, buffer.remaining());
                buffer.clear();
            }
        }
    }

    private static boolean sameSnapshot(
            BasicFileAttributes first, BasicFileAttributes second) {
        boolean sameIdentity = first.fileKey() != null && second.fileKey() != null
                ? first.fileKey().equals(second.fileKey())
                : first.creationTime().equals(second.creationTime());
        return sameIdentity
                && first.isRegularFile() == second.isRegularFile()
                && first.size() == second.size()
                && first.lastModifiedTime().equals(second.lastModifiedTime());
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 is unavailable", exception);
        }
    }

    private void releasePreview() {
        if (previewTexture != null) {
            minecraft.getTextureManager().release(previewTexture);
            previewTexture = null;
            previewWidth = 0;
            previewHeight = 0;
        }
    }

    private void changePage(int delta) {
        page += delta;
        rebuildWidgets();
    }

    private int screenshotsPerPage() {
        int rowsAboveNavigation = Math.floorDiv(height - 200, SCREENSHOT_ROW_HEIGHT) + 1;
        return Math.max(1, Math.min(MAX_SCREENSHOTS_PER_PAGE, rowsAboveNavigation));
    }

    private void capture() {
        commands.beginScreenshotCapture(sessionId.toString(), reviewed).ifPresentOrElse(
                this::capture,
                () -> {
                    status = Component.translatable("bugreport.screen.screenshot.invalid");
                    rebuildWidgets();
                });
    }

    private void capture(BugReportCommandService.ScreenshotCaptureRequest request) {
        completing = true;
        status = Component.translatable("bugreport.screen.screenshot.capturing");
        rebuildWidgets();
        String filename = "bugreport-" + CAPTURE_NAME.format(Instant.now()) + ".png";
        minecraft.setScreen(null);
        CompletableFuture.delayedExecutor(150, TimeUnit.MILLISECONDS).execute(() ->
                Minecraft.getInstance().execute(() -> Screenshot.grab(
                        Minecraft.getInstance().gameDirectory,
                        filename,
                        Minecraft.getInstance().getMainRenderTarget(),
                        ignored -> Minecraft.getInstance()
                                .execute(() -> finishCapture(request, filename)))));
    }

    private void finishCapture(
            BugReportCommandService.ScreenshotCaptureRequest request, String filename) {
        minecraft.setScreen(this);
        Path captured = screenshotsDirectory.resolve(filename);
        boolean authorityAccepted = commands.acceptScreenshotCapture(request);
        if (!authorityAccepted || !Files.isRegularFile(captured, LinkOption.NOFOLLOW_LINKS)) {
            completing = false;
            status = Component.translatable("bugreport.screen.screenshot.capture_failed");
            rebuildWidgets();
            return;
        }
        candidates = List.of();
        completing = false;
        observeAndSelect(RelativePath.of(filename));
        loadCandidates();
    }

    private void complete() {
        completing = true;
        status = Component.translatable("bugreport.screen.screenshot.preparing");
        rebuildWidgets();
        try {
            ScreenshotCollectionRequest request = ScreenshotCollectionRequest.from(
                    reviewed, List.copyOf(selected.values()));
            commands.beginCollectionWithScreenshots(sessionId.toString(), request).ifPresentOrElse(
                    completion,
                    () -> {
                        completing = false;
                        status = Component.translatable("bugreport.screen.screenshot.invalid");
                        rebuildWidgets();
                    });
        } catch (IllegalArgumentException failure) {
            completing = false;
            status = Component.translatable("bugreport.screen.screenshot.invalid");
            rebuildWidgets();
        }
    }

    @Override
    public void onClose() {
        releasePreview();
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        cancelPreviewObservation();
        releasePreview();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, 42, 0xE0E0E0);
        graphics.drawCenteredString(
                font,
                Component.translatable("bugreport.screen.screenshot.selected", selected.size()),
                width / 2,
                56,
                0xFFCC66);
        int pageSize = screenshotsPerPage();
        int first = page * pageSize;
        int last = Math.min(first + pageSize, candidates.size());
        for (int index = first; index < last; index++) {
            Candidate candidate = candidates.get(index);
            graphics.drawString(
                    font,
                    Component.literal(candidate.path().value()
                            + "  "
                            + candidate.width()
                            + "x"
                            + candidate.height()),
                    width / 2 - 196,
                    72 + (index - first) * SCREENSHOT_ROW_HEIGHT,
                    0xE0E0E0);
        }
        if (previewTexture != null && previewWidth > 0 && previewHeight > 0) {
            int maximumWidth = 170;
            int maximumHeight = 118;
            double scale = Math.min(
                    (double) maximumWidth / previewWidth,
                    (double) maximumHeight / previewHeight);
            int renderedWidth = Math.max(1, (int) Math.round(previewWidth * scale));
            int renderedHeight = Math.max(1, (int) Math.round(previewHeight * scale));
            int x = width / 2 + 28 + (maximumWidth - renderedWidth) / 2;
            int y = 72 + (maximumHeight - renderedHeight) / 2;
            graphics.blit(
                    previewTexture,
                    x,
                    y,
                    renderedWidth,
                    renderedHeight,
                    0,
                    0,
                    previewWidth,
                    previewHeight,
                    previewWidth,
                    previewHeight);
        }
    }

    private record Candidate(RelativePath path, Instant modified, int width, int height) {}

    private record ObservedPreview(
            ScreenshotCollectionRequest.SelectedImage selection, NativeImage image) {}
}
