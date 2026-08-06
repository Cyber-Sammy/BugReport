package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.ReportQualityRole;
import java.util.Objects;

/** Path-free metadata for one exact artifact included in a reviewed snapshot. */
public sealed interface ReviewedWorkspaceArtifact
        permits ReviewedWorkspaceArtifact.Source, ReviewedWorkspaceArtifact.Generated {
    String artifactName();

    long byteCount();

    Sha256Checksum checksum();

    DiagnosticContentType contentType();

    PrivacyClassification privacy();

    ReportQualityRole qualityRole();

    /** Retains the complete collected-source provenance without exposing its local path. */
    record Source(CollectedSourceFile collected) implements ReviewedWorkspaceArtifact {
        public Source {
            Objects.requireNonNull(collected, "collected");
        }

        @Override
        public String artifactName() {
            return collected.artifactName();
        }

        @Override
        public long byteCount() {
            return collected.byteCount();
        }

        @Override
        public Sha256Checksum checksum() {
            return collected.checksum();
        }

        @Override
        public DiagnosticContentType contentType() {
            return collected.contentType();
        }

        @Override
        public PrivacyClassification privacy() {
            return collected.privacy();
        }

        @Override
        public ReportQualityRole qualityRole() {
            return collected.qualityRole();
        }
    }

    /** Retains provider/generator provenance for one generated diagnostic artifact. */
    record Generated(CollectedGeneratedArtifact collected)
            implements ReviewedWorkspaceArtifact {
        public Generated {
            Objects.requireNonNull(collected, "collected");
        }

        @Override
        public String artifactName() {
            return collected.artifactName();
        }

        @Override
        public long byteCount() {
            return collected.byteCount();
        }

        @Override
        public Sha256Checksum checksum() {
            return collected.checksum();
        }

        @Override
        public DiagnosticContentType contentType() {
            return collected.contentType();
        }

        @Override
        public PrivacyClassification privacy() {
            return collected.privacy();
        }

        @Override
        public ReportQualityRole qualityRole() {
            return collected.qualityRole();
        }
    }
}
