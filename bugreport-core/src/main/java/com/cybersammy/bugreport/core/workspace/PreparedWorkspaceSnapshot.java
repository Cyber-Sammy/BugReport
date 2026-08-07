package com.cybersammy.bugreport.core.workspace;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Factory-issued post-sanitization inventory required by package planning. */
public final class PreparedWorkspaceSnapshot {
    private final ReviewedWorkspaceSnapshot reviewedSnapshot;
    private final List<PreparedWorkspaceArtifact> artifacts;

    PreparedWorkspaceSnapshot(
            ReviewedWorkspaceSnapshot reviewedSnapshot,
            List<PreparedWorkspaceArtifact> artifacts) {
        this.reviewedSnapshot = Objects.requireNonNull(reviewedSnapshot, "reviewedSnapshot");
        this.artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        if (this.artifacts.size() != reviewedSnapshot.artifacts().size()
                || this.artifacts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Prepared artifacts must cover the reviewed snapshot exactly");
        }
        for (int index = 0; index < this.artifacts.size(); index++) {
            if (this.artifacts.get(index).artifact() != reviewedSnapshot.artifacts().get(index)) {
                throw new IllegalArgumentException(
                        "Prepared artifacts must retain exact reviewed artifact identities");
            }
            if (index > 0
                    && Comparator.comparing(
                                    (PreparedWorkspaceArtifact artifact) ->
                                            artifact.artifact().artifactName())
                            .compare(this.artifacts.get(index - 1), this.artifacts.get(index))
                            >= 0) {
                throw new IllegalArgumentException(
                        "Prepared artifacts must use canonical artifact order");
            }
        }
    }

    public ReviewedWorkspaceSnapshot reviewedSnapshot() {
        return reviewedSnapshot;
    }

    public List<PreparedWorkspaceArtifact> artifacts() {
        return artifacts;
    }
}
