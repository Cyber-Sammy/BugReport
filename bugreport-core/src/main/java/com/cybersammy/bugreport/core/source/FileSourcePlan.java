package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.DiagnosticSourceKind;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import java.util.List;
import java.util.Objects;

/** One or more exact regular files selected for later bounded collection. */
public final class FileSourcePlan implements SourceSelectionPlan {
    private final DiagnosticSourceSpecification source;
    private final List<ResolvedSourceFile> files;

    FileSourcePlan(
            DiagnosticSourceSpecification source, List<ResolvedSourceFile> files) {
        this.source = Objects.requireNonNull(source, "source");
        this.files = List.copyOf(Objects.requireNonNull(files, "files"));
        if (this.files.isEmpty() || this.files.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("A file source plan requires selected files");
        }
        if (source.kind() != DiagnosticSourceKind.FILTERED_DIRECTORY
                && this.files.size() != 1) {
            throw new IllegalArgumentException(
                    "A non-directory selector must resolve to exactly one file");
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
}
