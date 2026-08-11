package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceKind;
import com.cybersammy.bugreport.api.specification.RelativePath;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Exact user-selected screenshot inputs for one reviewed category plan.
 *
 * <p>Only portable names below the product-owned screenshots directory are retained. Absolute
 * paths, original paths outside that directory, and provider-supplied filesystem authority never
 * enter this value.
 */
public final class ScreenshotCollectionRequest {
    public static final int PRODUCT_MAX_SELECTED_IMAGES = 8;

    /**
     * Portable evidence for the exact bounded bytes the user selected and previewed.
     *
     * <p>The checksum closes the replacement window even on filesystems whose file keys are not
     * available or reusable. No absolute path or platform-specific identity leaves the picker.
     */
    public record SelectedImage(
            RelativePath relativePath,
            long observedSize,
            Instant observedLastModified,
            String sha256) {
        private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

        public SelectedImage {
            Objects.requireNonNull(relativePath, "relativePath");
            Objects.requireNonNull(observedLastModified, "observedLastModified");
            Objects.requireNonNull(sha256, "sha256");
            if (relativePath.value().indexOf('/') >= 0) {
                throw new IllegalArgumentException(
                        "Screenshot selection must be a direct child of the screenshots directory");
            }
            if (observedSize < 0 || !SHA_256.matcher(sha256).matches()) {
                throw new IllegalArgumentException("Screenshot observation is invalid");
            }
        }
    }

    /** One screenshot source declaration paired with one explicitly selected local image. */
    public record Entry(SourceProvenance provenance, SelectedImage selectedImage) {
        public Entry {
            Objects.requireNonNull(provenance, "provenance");
            Objects.requireNonNull(selectedImage, "selectedImage");
            if (provenance.kind() != DiagnosticSourceKind.USER_SELECTED_SCREENSHOT) {
                throw new IllegalArgumentException("Screenshot entry requires screenshot provenance");
            }
        }

        public RelativePath relativePath() {
            return selectedImage.relativePath();
        }
    }

    private final ProviderId providerId;
    private final ProviderVersion providerVersion;
    private final CategoryId categoryId;
    private final List<Entry> entries;
    private final ScreenshotCollectionFingerprint fingerprint;

    private ScreenshotCollectionRequest(
            ProviderId providerId,
            ProviderVersion providerVersion,
            CategoryId categoryId,
            List<Entry> entries) {
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.providerVersion = Objects.requireNonNull(providerVersion, "providerVersion");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (this.entries.size() > PRODUCT_MAX_SELECTED_IMAGES) {
            throw new IllegalArgumentException("Too many screenshot attachments were selected");
        }
        Set<String> identities = new HashSet<>();
        for (Entry entry : this.entries) {
            SourceProvenance provenance = entry.provenance();
            if (!providerId.equals(provenance.providerId())
                    || !providerVersion.equals(provenance.providerVersion())
                    || !categoryId.equals(provenance.categoryId())
                    || !identities.add(provenance.sourceId() + "\0" + entry.relativePath().value())) {
                throw new IllegalArgumentException(
                        "Screenshot inputs must have unique matching category provenance");
            }
        }
        fingerprint = new ScreenshotCollectionFingerprint(this.entries);
    }

    /**
     * Binds explicitly selected screenshot names to the exact reviewed screenshot declarations.
     *
     * <p>Every included screenshot declaration must receive at least one image. When several
     * declarations request screenshots, each declaration is paired with the selection in canonical
     * declaration order without allowing the provider to select a path.
     */
    public static ScreenshotCollectionRequest from(
            ReviewedCollectionPlan plan,
            List<SelectedImage> selectedImages) {
        ReviewedCollectionPlan reviewed = Objects.requireNonNull(plan, "plan");
        List<SelectedImage> selected = List.copyOf(
                Objects.requireNonNull(selectedImages, "selectedImages"));
        if (selected.size() > PRODUCT_MAX_SELECTED_IMAGES
                || selected.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Screenshot selection exceeds the product limit");
        }
        List<SourceProvenance> screenshotSources = reviewed.includedSources().stream()
                .map(CoordinatedSourcePlan::provenance)
                .filter(provenance -> provenance.kind()
                        == DiagnosticSourceKind.USER_SELECTED_SCREENSHOT)
                .toList();
        if (screenshotSources.isEmpty() != selected.isEmpty()) {
            throw new IllegalArgumentException(
                    "Included screenshot sources require an explicit non-empty selection");
        }
        if ((long) screenshotSources.size() * selected.size() > PRODUCT_MAX_SELECTED_IMAGES) {
            throw new IllegalArgumentException("Screenshot declarations exceed the artifact limit");
        }
        List<Entry> entries = new ArrayList<>();
        for (SourceProvenance provenance : screenshotSources) {
            selected.forEach(image -> entries.add(new Entry(provenance, image)));
        }
        CategorySourcePlan sourcePlan = reviewed.plan();
        return new ScreenshotCollectionRequest(
                sourcePlan.providerId(),
                sourcePlan.providerVersion(),
                sourcePlan.categoryId(),
                entries);
    }

    public ProviderId providerId() {
        return providerId;
    }

    public ProviderVersion providerVersion() {
        return providerVersion;
    }

    public CategoryId categoryId() {
        return categoryId;
    }

    public List<Entry> entries() {
        return entries;
    }

    public ScreenshotCollectionFingerprint fingerprint() {
        return fingerprint;
    }

    /** Opaque structural identity containing no absolute filesystem path. */
    public static final class ScreenshotCollectionFingerprint {
        private final List<String> entries;

        private ScreenshotCollectionFingerprint(List<Entry> values) {
            entries = values.stream().map(ScreenshotCollectionFingerprint::identity).toList();
        }

        private static String identity(Entry entry) {
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("Required SHA-256 is unavailable", exception);
            }
            digest.update(entry.provenance().sourceId().toString()
                    .getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(entry.relativePath().value().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Long.toString(entry.selectedImage().observedSize())
                    .getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(entry.selectedImage().observedLastModified().toString()
                    .getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(entry.selectedImage().sha256().getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest.digest());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ScreenshotCollectionFingerprint fingerprint
                    && entries.equals(fingerprint.entries);
        }

        @Override
        public int hashCode() {
            return entries.hashCode();
        }
    }
}
