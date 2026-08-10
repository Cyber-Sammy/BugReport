package com.cybersammy.bugreport.neoforge.command;

import com.cybersammy.bugreport.api.specification.RelativePath;
import com.cybersammy.bugreport.core.source.ReviewedCollectionPlan;
import com.cybersammy.bugreport.core.source.ScreenshotCollectionRequest;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;

/** Explicit bounded picker for recent local screenshots and a new in-game capture. */
public final class ScreenshotSelectionScreen extends Screen {
    private static final int MAX_DIRECTORY_ENTRIES = 128;
    private static final int PAGE_SIZE = 5;
    private static final DateTimeFormatter CAPTURE_NAME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd_HH.mm.ss.SSS", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private final BugReportCommandService commands;
    private final Screen parent;
    private final ReportSessionId sessionId;
    private final ReviewedCollectionPlan reviewed;
    private final Consumer<BugReportCommandService.CollectionExecutionRequest> completion;
    private final Path screenshotsDirectory;
    private final Set<RelativePath> selected = new LinkedHashSet<>();
    private List<Candidate> candidates = List.of();
    private int page;
    private boolean loading;
    private boolean completing;
    private Component status = Component.translatable("bugreport.screen.screenshot.loading");
    private ResourceLocation previewTexture;
    private int previewWidth;
    private int previewHeight;
    private long previewGeneration;

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
        int first = page * PAGE_SIZE;
        int last = Math.min(first + PAGE_SIZE, candidates.size());
        for (int index = first; index < last; index++) {
            Candidate candidate = candidates.get(index);
            boolean included = selected.contains(candidate.path());
            Button button = Button.builder(
                            Component.literal((included ? "[x] " : "[ ] ")
                                    + candidate.path().value()
                                    + "  "
                                    + candidate.width()
                                    + "x"
                                    + candidate.height()),
                            ignored -> toggle(candidate))
                    .bounds(width / 2 - 196, 72 + (index - first) * 24, 210, 20)
                    .build();
            button.active = !completing;
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
        next.active = (page + 1) * PAGE_SIZE < candidates.size() && !completing;
        addRenderableWidget(next);

        Button capture = Button.builder(
                        Component.translatable("bugreport.screen.screenshot.capture"), ignored -> capture())
                .bounds(width / 2 - 150, height - 56, 96, 20)
                .build();
        capture.active = !completing
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
        continueButton.active = !selected.isEmpty() && !completing;
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
                selected.retainAll(result.stream()
                        .map(Candidate::path)
                        .collect(java.util.stream.Collectors.toSet()));
                status = result.isEmpty()
                        ? Component.translatable("bugreport.screen.screenshot.empty")
                        : Component.translatable(
                                "bugreport.screen.screenshot.available", result.size());
                page = Math.min(page, Math.max(0, (result.size() - 1) / PAGE_SIZE));
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
                    || attributes.size() > 32L * 1024L * 1024L) {
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
        if (!selected.remove(path)) {
            if (selected.size() >= ScreenshotCollectionRequest.PRODUCT_MAX_SELECTED_IMAGES) {
                status = Component.translatable(
                        "bugreport.screen.screenshot.limit",
                        ScreenshotCollectionRequest.PRODUCT_MAX_SELECTED_IMAGES);
                return;
            }
            selected.add(path);
        }
        loadPreview(candidate);
        rebuildWidgets();
    }

    private void loadPreview(Candidate candidate) {
        long generation = ++previewGeneration;
        Thread.ofVirtual().name("bugreport-screenshot-preview").start(() -> {
            NativeImage image = null;
            try {
                Path path = screenshotsDirectory.resolve(candidate.path().value()).normalize();
                if (!screenshotsDirectory.equals(path.getParent())
                        || Files.size(path) > 32L * 1024L * 1024L) {
                    return;
                }
                image = NativeImage.read(Files.readAllBytes(path));
            } catch (IOException | RuntimeException failure) {
                return;
            }
            NativeImage loaded = image;
            Minecraft.getInstance().execute(() -> {
                if (generation != previewGeneration || minecraft.screen != this) {
                    loaded.close();
                    return;
                }
                releasePreview();
                previewWidth = loaded.getWidth();
                previewHeight = loaded.getHeight();
                previewTexture = minecraft.getTextureManager().register(
                        "bugreport-screenshot-preview", new DynamicTexture(loaded));
            });
        });
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

    private void capture() {
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
                        ignored -> Minecraft.getInstance().execute(() -> finishCapture(filename)))));
    }

    private void finishCapture(String filename) {
        minecraft.setScreen(this);
        Path captured = screenshotsDirectory.resolve(filename);
        if (!Files.isRegularFile(captured, LinkOption.NOFOLLOW_LINKS)) {
            completing = false;
            status = Component.translatable("bugreport.screen.screenshot.capture_failed");
            rebuildWidgets();
            return;
        }
        selected.add(RelativePath.of(filename));
        candidates = List.of();
        completing = false;
        loadCandidates();
    }

    private void complete() {
        completing = true;
        status = Component.translatable("bugreport.screen.screenshot.preparing");
        rebuildWidgets();
        try {
            ScreenshotCollectionRequest request = ScreenshotCollectionRequest.from(
                    reviewed, List.copyOf(selected));
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
        releasePreview();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, 42, 0xE0E0E0);
        graphics.drawCenteredString(
                font,
                Component.translatable("bugreport.screen.screenshot.selected", selected.size()),
                width / 2,
                56,
                0xFFCC66);
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
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private record Candidate(RelativePath path, Instant modified, int width, int height) {}
}
