package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.InclusionDefault;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceKind;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.core.sanitization.ProductSanitization;
import com.cybersammy.bugreport.core.sanitization.SanitizationArtifactPolicy;
import com.cybersammy.bugreport.core.sanitization.SanitizationCaseSensitivity;
import com.cybersammy.bugreport.core.sanitization.SanitizationPipeline;
import com.cybersammy.bugreport.core.sanitization.SanitizationPolicy;
import com.cybersammy.bugreport.core.sanitization.SanitizationResult;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.ReportSessionSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Trusted boundary from collected workspace bytes to explicit review and package authority. */
public final class WorkspaceReviewCoordinator {
    private WorkspaceReviewCoordinator() {}

    /**
     * Sanitizes every collected text artifact and assesses opaque binary artifacts.
     *
     * <p>Each text artifact is independently fail closed. A failed artifact remains excluded and
     * cannot be admitted by {@link #prepare}. The returned batch is opaque authority issued only
     * after the coordinator published and checksummed the exact final bytes.
     */
    /** Executes the fixed product sanitization policy for one trusted session collection. */
    public static SanitizationBatch sanitizeProduct(
            ReportSessionSnapshot session,
            FileCollectionResult files,
            ReportWorkspace workspace,
            String homeDirectory,
            String username,
            SanitizationCaseSensitivity caseSensitivity,
            CancellationSignal cancellation) {
        ReportSessionSnapshot trustedSession = Objects.requireNonNull(session, "session");
        FileCollectionResult result = Objects.requireNonNull(files, "files");
        if (!trustedSession.id().equals(Objects.requireNonNull(workspace, "workspace").sessionId())
                || !trustedSession.providerSpecification().id().equals(result.providerId())
                || !trustedSession.providerSpecification().version().equals(result.providerVersion())
                || trustedSession.selectedCategory().stream()
                        .noneMatch(category -> category.id().equals(result.categoryId()))) {
            throw new IllegalArgumentException(
                    "Sanitization input does not belong to the trusted report session");
        }
        SanitizationCaseSensitivity sensitivity =
                Objects.requireNonNull(caseSensitivity, "caseSensitivity");
        return sanitize(
                trustedSession,
                result,
                workspace,
                source -> ProductSanitization.textPipeline(
                        SanitizationPolicy.standard(artifactPolicy(source)),
                        homeDirectory,
                        username,
                        sensitivity),
                cancellation);
    }

    static SanitizationBatch sanitize(
            ReportSessionSnapshot session,
            FileCollectionResult files,
            ReportWorkspace workspace,
            Function<CollectedSourceFile, SanitizationPipeline> pipelines,
            CancellationSignal cancellation) {
        ReportSessionSnapshot trustedSession = Objects.requireNonNull(session, "session");
        FileCollectionResult result = Objects.requireNonNull(files, "files");
        ReportWorkspace trustedWorkspace = Objects.requireNonNull(workspace, "workspace");
        Function<CollectedSourceFile, SanitizationPipeline> pipelineFactory =
                Objects.requireNonNull(pipelines, "pipelines");
        CancellationSignal signal = Objects.requireNonNull(cancellation, "cancellation");
        List<WorkspaceSanitizationCoordinator.SanitizedSource> evidence = new ArrayList<>();
        List<ArtifactReview> reviews = new ArrayList<>();
        for (FileCollectionResult.SourceOutcome outcome : result.outcomes()) {
            outcome.collectedFile().ifPresent(source -> {
                if (source.contentType() == DiagnosticContentType.BINARY) {
                    var assessment = ProductSanitization.assessBinary(
                            source.artifactName(), source.privacy());
                    reviews.add(ArtifactReview.binary(
                            source, labelKey(trustedSession, source), assessment.classification()));
                    return;
                }
                try {
                    WorkspaceSanitizationCoordinator.SanitizedSource sanitized =
                            WorkspaceSanitizationCoordinator.sanitize(
                                    source,
                                    trustedWorkspace,
                                    Objects.requireNonNull(
                                            pipelineFactory.apply(source), "sanitization pipeline"),
                                    signal);
                    evidence.add(sanitized);
                    reviews.add(ArtifactReview.sanitized(
                            sanitized.source(),
                            labelKey(trustedSession, source),
                            sanitized.result()));
                } catch (RuntimeException failure) {
                    reviews.add(ArtifactReview.failed(
                            source, labelKey(trustedSession, source)));
                }
            });
        }
        reviews.sort(java.util.Comparator.comparing(ArtifactReview::artifactName));
        FileCollectionResult finalFiles = finalFileResult(result, evidence);
        return new SanitizationBatch(
                trustedWorkspace.sessionId(), result, finalFiles, trustedWorkspace, evidence, reviews);
    }

    private static SanitizationArtifactPolicy artifactPolicy(CollectedSourceFile source) {
        return source.provenances().stream()
                        .anyMatch(provenance -> provenance.kind()
                                == DiagnosticSourceKind.MOD_CONFIGURATION)
                ? SanitizationArtifactPolicy.CONFIGURATION
                : SanitizationArtifactPolicy.LOG;
    }

    private static LocalizationKey labelKey(
            ReportSessionSnapshot session, CollectedSourceFile source) {
        var sourceId = source.provenances().getFirst().sourceId();
        var declaration = session.providerSpecification().sources().get(sourceId);
        if (declaration == null) {
            throw new IllegalArgumentException(
                    "Collected source is not declared by the trusted report provider");
        }
        return declaration.labelKey();
    }

    /** Reports whether a batch belongs to the exact accepted collection boundary. */
    public static boolean matches(
            SanitizationBatch batch, FileCollectionResult files, ReportWorkspace workspace) {
        SanitizationBatch trusted = Objects.requireNonNull(batch, "batch");
        return trusted.files == Objects.requireNonNull(files, "files")
                && trusted.workspace == Objects.requireNonNull(workspace, "workspace")
                && trusted.sessionId.equals(workspace.sessionId());
    }

    /**
     * Seals the workspace and issues package authority for the exact selected and reviewed bytes.
     * This operation performs filesystem I/O and must not run on a UI or game thread.
     */
    public static PreparedReview prepare(
            ReportSessionSnapshot reviewSession,
            SanitizationBatch batch,
            Set<String> includedArtifacts,
            Set<String> explicitlyReviewedArtifacts) {
        ReportSessionSnapshot session = Objects.requireNonNull(reviewSession, "reviewSession");
        SanitizationBatch trusted = Objects.requireNonNull(batch, "batch");
        Set<String> included = Set.copyOf(
                Objects.requireNonNull(includedArtifacts, "includedArtifacts"));
        Set<String> explicit = Set.copyOf(
                Objects.requireNonNull(explicitlyReviewedArtifacts, "explicitlyReviewedArtifacts"));
        if (!session.id().equals(trusted.sessionId)
                || included.stream().anyMatch(Objects::isNull)
                || explicit.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Review authority does not match the report session");
        }

        Set<String> selectable = new HashSet<>();
        Set<String> reviewRequired = new HashSet<>();
        for (ArtifactReview review : trusted.reviews) {
            if (review.status() == ArtifactReviewStatus.FAILED) {
                continue;
            }
            selectable.add(review.artifactName());
            if (review.explicitReviewRequired()) {
                reviewRequired.add(review.artifactName());
            }
        }
        if (!selectable.containsAll(included)
                || !included.containsAll(explicit)
                || !reviewRequired.containsAll(explicit)
                || !explicit.containsAll(intersection(included, reviewRequired))) {
            throw new IllegalArgumentException(
                    "Included artifacts require exact successful and explicit review evidence");
        }

        CategoryGeneratedDiagnosticResult generated = new CategoryGeneratedDiagnosticResult(
                trusted.finalFiles.providerId(), trusted.finalFiles.categoryId(), List.of(), 0);
        ReviewedWorkspaceSnapshot reviewed = ReviewedWorkspaceSnapshotFactory.create(
                session, trusted.workspace, trusted.finalFiles, generated, included);
        List<WorkspaceSanitizationCoordinator.SanitizedSource> selectedEvidence = trusted.evidence
                .stream()
                .filter(value -> included.contains(value.source().artifactName()))
                .toList();
        return new PreparedReview(
                trusted,
                WorkspacePreparationCoordinator.prepare(reviewed, selectedEvidence, explicit));
    }

    private static Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private static FileCollectionResult finalFileResult(
            FileCollectionResult original,
            List<WorkspaceSanitizationCoordinator.SanitizedSource> evidence) {
        java.util.Map<String, CollectedSourceFile> sanitized = evidence.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        value -> value.source().artifactName(),
                        WorkspaceSanitizationCoordinator.SanitizedSource::source));
        List<FileCollectionResult.SourceOutcome> outcomes = original.outcomes().stream()
                .map(outcome -> outcome.collectedFile()
                        .map(source -> FileCollectionResult.SourceOutcome.collected(
                                outcome.ordinal(),
                                sanitized.getOrDefault(source.artifactName(), source)))
                        .orElseGet(() -> outcome.status() == FileCollectionResult.SourceStatus.CANCELLED
                                ? FileCollectionResult.SourceOutcome.cancelled(
                                        outcome.ordinal(), outcome.provenances())
                                : FileCollectionResult.SourceOutcome.failed(
                                        outcome.ordinal(),
                                        outcome.provenances(),
                                        outcome.failureCode().orElseThrow())))
                .toList();
        return new FileCollectionResult(
                original.providerId(),
                original.providerVersion(),
                original.categoryId(),
                original.planFingerprint().orElse(null),
                original.status(),
                outcomes,
                original.progress());
    }

    /** Opaque, non-constructible evidence for one exact sanitization pass. */
    public static final class SanitizationBatch {
        private final ReportSessionId sessionId;
        private final FileCollectionResult files;
        private final FileCollectionResult finalFiles;
        private final ReportWorkspace workspace;
        private final List<WorkspaceSanitizationCoordinator.SanitizedSource> evidence;
        private final List<ArtifactReview> reviews;

        private SanitizationBatch(
                ReportSessionId sessionId,
                FileCollectionResult files,
                FileCollectionResult finalFiles,
                ReportWorkspace workspace,
                List<WorkspaceSanitizationCoordinator.SanitizedSource> evidence,
                List<ArtifactReview> reviews) {
            this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
            this.files = Objects.requireNonNull(files, "files");
            this.finalFiles = Objects.requireNonNull(finalFiles, "finalFiles");
            this.workspace = Objects.requireNonNull(workspace, "workspace");
            this.evidence = List.copyOf(evidence);
            this.reviews = List.copyOf(reviews);
        }

        public ReportSessionId sessionId() {
            return sessionId;
        }

        public List<ArtifactReview> artifacts() {
            return reviews;
        }
    }

    /** Opaque package authority bound to the exact sanitization batch reviewed by the user. */
    public static final class PreparedReview {
        private final SanitizationBatch batch;
        private final PreparedWorkspaceSnapshot snapshot;

        private PreparedReview(
                SanitizationBatch batch, PreparedWorkspaceSnapshot snapshot) {
            this.batch = Objects.requireNonNull(batch, "batch");
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }

        public PreparedWorkspaceSnapshot snapshot() {
            return snapshot;
        }

        public boolean belongsTo(SanitizationBatch expected) {
            return batch == Objects.requireNonNull(expected, "expected");
        }
    }

    /** Privacy-safe metadata rendered by first-party review UI without paths or contents. */
    public record ArtifactReview(
            String artifactName,
            LocalizationKey labelKey,
            DiagnosticContentType contentType,
            com.cybersammy.bugreport.api.classification.PrivacyClassification privacy,
            com.cybersammy.bugreport.api.specification.ReportQualityRole qualityRole,
            InclusionDefault inclusionDefault,
            long byteCount,
            ArtifactReviewStatus status,
            int findingCount,
            boolean explicitReviewRequired) {
        public ArtifactReview {
            Objects.requireNonNull(artifactName, "artifactName");
            Objects.requireNonNull(labelKey, "labelKey");
            Objects.requireNonNull(contentType, "contentType");
            Objects.requireNonNull(privacy, "privacy");
            Objects.requireNonNull(qualityRole, "qualityRole");
            Objects.requireNonNull(inclusionDefault, "inclusionDefault");
            Objects.requireNonNull(status, "status");
            if (byteCount < 0 || findingCount < 0
                    || (status == ArtifactReviewStatus.FAILED && explicitReviewRequired)) {
                throw new IllegalArgumentException("Artifact review metadata is inconsistent");
            }
        }

        private static ArtifactReview sanitized(
                CollectedSourceFile source, LocalizationKey labelKey, SanitizationResult result) {
            return new ArtifactReview(
                    source.artifactName(), labelKey, source.contentType(), source.privacy(),
                    source.qualityRole(), source.inclusionDefault(), source.byteCount(),
                    ArtifactReviewStatus.SANITIZED, result.findings().size(),
                    result.hasUnresolvedWarnings());
        }

        private static ArtifactReview binary(
                CollectedSourceFile source,
                LocalizationKey labelKey,
                com.cybersammy.bugreport.api.classification.PrivacyClassification privacy) {
            return new ArtifactReview(
                    source.artifactName(), labelKey, source.contentType(), privacy,
                    source.qualityRole(), source.inclusionDefault(), source.byteCount(),
                    ArtifactReviewStatus.BINARY_REVIEW_REQUIRED, 0, true);
        }

        private static ArtifactReview failed(
                CollectedSourceFile source, LocalizationKey labelKey) {
            return new ArtifactReview(
                    source.artifactName(), labelKey, source.contentType(), source.privacy(),
                    source.qualityRole(), InclusionDefault.EXCLUDED, source.byteCount(),
                    ArtifactReviewStatus.FAILED, 0, false);
        }
    }

    public enum ArtifactReviewStatus {
        SANITIZED,
        BINARY_REVIEW_REQUIRED,
        FAILED
    }
}
