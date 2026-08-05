package com.cybersammy.bugreport.api.specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class DynamicSourcePathSpecificationTest {
    private static final DynamicSourcePathProducer PRODUCER =
            (request, sink) -> sink.emit(RelativePath.of("current/client.log"));

    @Test
    void createsBoundedDynamicLogSourceWithoutFilesystemAuthority() {
        DiagnosticSourceSpecification source = dynamicBuilder(LogicalRoot.GAME_LOGS, PRODUCER)
                .build();

        assertEquals(DiagnosticSourceKind.DYNAMIC_FILES, source.kind());
        assertEquals(LogicalRoot.GAME_LOGS, source.root().orElseThrow());
        assertEquals(java.util.Optional.empty(), source.path());
        assertEquals(java.util.Optional.empty(), source.pattern());
        assertSame(PRODUCER, source.dynamicPathProducer().orElseThrow());
    }

    @Test
    void rejectsConfigurationRootAndImplicitCallbackBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> dynamicBuilder(LogicalRoot.MOD_CONFIGURATION, PRODUCER).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> commonBuilder(
                                DiagnosticSourceSpecification.dynamicFiles(
                                        DiagnosticSourceId.of("dynamic"),
                                        LogicalRoot.GAME_LOGS,
                                        PRODUCER))
                        .build());
    }

    @Test
    void rejectsIrrelevantFilesystemAndArtifactConstraints() {
        CollectionConstraints traversal = CollectionConstraints.builder()
                .maxTraversalDepth(0)
                .maxMatchedFiles(1)
                .callbackTimeout(Duration.ofMillis(100))
                .build();
        CollectionConstraints artifacts = CollectionConstraints.builder()
                .maxMatchedFiles(1)
                .maxGeneratedArtifacts(1)
                .callbackTimeout(Duration.ofMillis(100))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> commonBuilder(DiagnosticSourceSpecification.dynamicFiles(
                                        DiagnosticSourceId.of("traversal"),
                                        LogicalRoot.GAME_LOGS,
                                        PRODUCER))
                        .constraints(traversal)
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> commonBuilder(DiagnosticSourceSpecification.dynamicFiles(
                                        DiagnosticSourceId.of("artifacts"),
                                        LogicalRoot.GAME_LOGS,
                                        PRODUCER))
                        .constraints(artifacts)
                        .build());
    }

    @Test
    void relativeResultTypeRejectsEscapesBeforeEmission() {
        for (String path : new String[] {
            "../saves/world/level.dat", "C:/minecraft/latest.log", "/etc/passwd"
        }) {
            assertThrows(IllegalArgumentException.class, () -> RelativePath.of(path));
        }
    }

    private static DiagnosticSourceSpecification.Builder dynamicBuilder(
            LogicalRoot root, DynamicSourcePathProducer producer) {
        return commonBuilder(DiagnosticSourceSpecification.dynamicFiles(
                        DiagnosticSourceId.of("dynamic"), root, producer))
                .constraints(CollectionConstraints.builder()
                        .maxMatchedFiles(4)
                        .maxBytesPerFile(1024)
                        .maxTotalBytes(4096)
                        .callbackTimeout(Duration.ofMillis(250))
                        .build());
    }

    private static DiagnosticSourceSpecification.Builder commonBuilder(
            DiagnosticSourceSpecification.Builder builder) {
        return builder.labelKey(LocalizationKey.of("example.source.dynamic"))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .supportSide(SupportedSide.PHYSICAL_CLIENT);
    }
}
