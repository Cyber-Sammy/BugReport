package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.identifier.GeneratedArtifactId;
import java.util.Objects;

/** Immutable generated value captured without workspace or filesystem authority. */
sealed interface CapturedGeneratedEmission {
    GeneratedArtifactId artifactId();

    void replay(BoundedGeneratedDiagnosticSink sink);

    record Text(GeneratedArtifactId artifactId, String content)
            implements CapturedGeneratedEmission {
        public Text {
            Objects.requireNonNull(artifactId, "artifactId");
            Objects.requireNonNull(content, "content");
        }

        @Override
        public void replay(BoundedGeneratedDiagnosticSink sink) {
            sink.emitText(artifactId, content);
        }
    }

    record Json(GeneratedArtifactId artifactId, ExtensionMetadata content)
            implements CapturedGeneratedEmission {
        public Json {
            Objects.requireNonNull(artifactId, "artifactId");
            Objects.requireNonNull(content, "content");
        }

        @Override
        public void replay(BoundedGeneratedDiagnosticSink sink) {
            sink.emitJson(artifactId, content);
        }
    }
}
