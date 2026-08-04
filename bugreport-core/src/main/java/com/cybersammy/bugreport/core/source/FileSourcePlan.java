package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.DiagnosticSourceKind;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import java.util.List;
import java.util.Objects;

/** One or more exact regular files selected for later bounded collection. */
public final class FileSourcePlan implements SourceSelectionPlan {
    private final DiagnosticSourceSpecification source;
    private final List<ResolvedSourceFile> files;
    private final SourceSizeEstimate estimate;

    FileSourcePlan(
            DiagnosticSourceSpecification source,
            List<ResolvedSourceFile> files,
            SourceSizeEstimate estimate) {
        this.source = Objects.requireNonNull(source, "source");
        this.files = List.copyOf(Objects.requireNonNull(files, "files"));
        this.estimate = Objects.requireNonNull(estimate, "estimate");
        if (this.files.isEmpty() || this.files.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("A file source plan requires selected files");
        }
        if (source.kind() != DiagnosticSourceKind.FILTERED_DIRECTORY
                && this.files.size() != 1) {
            throw new IllegalArgumentException(
                    "A non-directory selector must resolve to exactly one file");
        }
        if (!estimate.complete() || estimate.selectedFileCount() != this.files.size()) {
            throw new IllegalArgumentException(
                    "A file source plan requires a complete matching size estimate");
        }
        long observedBytes = 0;
        try {
            for (ResolvedSourceFile file : this.files) {
                observedBytes = Math.addExact(observedBytes, file.observedSize());
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Selected file sizes overflow long", exception);
        }
        if (estimate.knownBytes() != observedBytes) {
            throw new IllegalArgumentException(
                    "A file source plan estimate must match selected file observations");
        }
    }

    @Override
    public DiagnosticSourceSpecification source() {
        return source;
    }

    /** Returns canonically ordered trusted file observations. */
    public List<ResolvedSourceFile> files() {
        return files;
    }

    @Override
    public SourceSizeEstimate estimate() {
        return estimate;
    }
}
