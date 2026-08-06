package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable identity and canonical inventory of the exact workspace bytes approved for review. */
public final class ReviewedWorkspaceSnapshot {
    private final ReportSessionId sessionId;
    private final long sessionRevision;
    private final ProviderId providerId;
    private final ProviderVersion providerVersion;
    private final CategoryId categoryId;
    private final List<ReviewedWorkspaceArtifact> artifacts;
    private final long totalBytes;
    private final Sha256Checksum snapshotChecksum;

    ReviewedWorkspaceSnapshot(
            ReportSessionId sessionId,
            long sessionRevision,
            ProviderId providerId,
            ProviderVersion providerVersion,
            CategoryId categoryId,
            List<ReviewedWorkspaceArtifact> artifacts,
            long totalBytes,
            Sha256Checksum snapshotChecksum) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        if (sessionRevision < 0) {
            throw new IllegalArgumentException("Reviewed session revision must be non-negative");
        }
        this.sessionRevision = sessionRevision;
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.providerVersion = Objects.requireNonNull(providerVersion, "providerVersion");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        if (this.artifacts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Reviewed artifacts must not contain null");
        }
        for (int index = 1; index < this.artifacts.size(); index++) {
            if (Comparator.comparing(ReviewedWorkspaceArtifact::artifactName)
                            .compare(this.artifacts.get(index - 1), this.artifacts.get(index))
                    >= 0) {
                throw new IllegalArgumentException(
                        "Reviewed artifacts must be strictly ordered by artifact name");
            }
        }
        long actualBytes = this.artifacts.stream()
                .mapToLong(ReviewedWorkspaceArtifact::byteCount)
                .reduce(0L, Math::addExact);
        if (totalBytes < 0 || totalBytes != actualBytes) {
            throw new IllegalArgumentException("Reviewed artifact byte total is inconsistent");
        }
        this.totalBytes = totalBytes;
        this.snapshotChecksum = Objects.requireNonNull(snapshotChecksum, "snapshotChecksum");
    }

    public ReportSessionId sessionId() {
        return sessionId;
    }

    public long sessionRevision() {
        return sessionRevision;
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

    public List<ReviewedWorkspaceArtifact> artifacts() {
        return artifacts;
    }

    public long totalBytes() {
        return totalBytes;
    }

    public Sha256Checksum snapshotChecksum() {
        return snapshotChecksum;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReviewedWorkspaceSnapshot snapshot)) {
            return false;
        }
        return sessionRevision == snapshot.sessionRevision
                && totalBytes == snapshot.totalBytes
                && sessionId.equals(snapshot.sessionId)
                && providerId.equals(snapshot.providerId)
                && providerVersion.equals(snapshot.providerVersion)
                && categoryId.equals(snapshot.categoryId)
                && artifacts.equals(snapshot.artifacts)
                && snapshotChecksum.equals(snapshot.snapshotChecksum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                sessionId,
                sessionRevision,
                providerId,
                providerVersion,
                categoryId,
                artifacts,
                totalBytes,
                snapshotChecksum);
    }

    @Override
    public String toString() {
        return "ReviewedWorkspaceSnapshot[sessionId="
                + sessionId
                + ", sessionRevision="
                + sessionRevision
                + ", providerId="
                + providerId
                + ", providerVersion="
                + providerVersion
                + ", categoryId="
                + categoryId
                + ", artifacts="
                + artifacts
                + ", totalBytes="
                + totalBytes
                + ", snapshotChecksum="
                + snapshotChecksum
                + ']';
    }
}
