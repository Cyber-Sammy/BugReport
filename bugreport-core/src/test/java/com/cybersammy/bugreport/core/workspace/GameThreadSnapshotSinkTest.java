package com.cybersammy.bugreport.core.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.identifier.GeneratedArtifactId;
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import org.junit.jupiter.api.Test;

final class GameThreadSnapshotSinkTest {
    @Test
    void materializesMutableTextBeforeLeavingCaptureThread() {
        GameThreadSnapshotSink sink = textSink(new GeneratedDiagnosticLimits(2, 100, 100, 100));
        sink.bindOwnerThread();
        StringBuilder providerValue = new StringBuilder("before");

        sink.emitText(GeneratedArtifactId.of("state"), providerValue);
        providerValue.replace(0, providerValue.length(), "after");

        List<CapturedGeneratedEmission> captured = sink.finish();
        CapturedGeneratedEmission.Text text = (CapturedGeneratedEmission.Text) captured.get(0);
        assertEquals("before", text.content());
    }

    @Test
    void enforcesCaptureRepresentationAndDuplicateIdentity() {
        GameThreadSnapshotSink mismatch =
                textSink(new GeneratedDiagnosticLimits(2, 100, 100, 100));
        mismatch.bindOwnerThread();
        GeneratedSinkViolation representation = assertThrows(
                GeneratedSinkViolation.class,
                () -> mismatch.emitJson(
                        GeneratedArtifactId.of("state"), ExtensionMetadata.empty()));
        assertEquals(GeneratedDiagnosticCode.CONTENT_TYPE_MISMATCH, representation.code());

        GameThreadSnapshotSink duplicate =
                textSink(new GeneratedDiagnosticLimits(2, 100, 100, 100));
        duplicate.bindOwnerThread();
        duplicate.emitText(GeneratedArtifactId.of("state"), "first");
        GeneratedSinkViolation repeated = assertThrows(
                GeneratedSinkViolation.class,
                () -> duplicate.emitText(GeneratedArtifactId.of("state"), "second"));
        assertEquals(GeneratedDiagnosticCode.DUPLICATE_ARTIFACT_ID, repeated.code());
    }

    @Test
    void appliesRemainingAggregateCaptureBoundAcrossTextValues() {
        GameThreadSnapshotSink sink = textSink(new GeneratedDiagnosticLimits(2, 10, 5, 100));
        sink.bindOwnerThread();
        sink.emitText(GeneratedArtifactId.of("first"), "123");

        GeneratedSinkViolation failure = assertThrows(
                GeneratedSinkViolation.class,
                () -> sink.emitText(GeneratedArtifactId.of("second"), "456"));

        assertEquals(GeneratedDiagnosticCode.TOTAL_BYTE_LIMIT_EXCEEDED, failure.code());
    }

    @Test
    void rejectsEmissionFromThreadOtherThanDispatchedOwner() throws Exception {
        GameThreadSnapshotSink sink = textSink(new GeneratedDiagnosticLimits(1, 10, 10, 10));
        sink.bindOwnerThread();
        FutureTask<Void> otherThreadEmission = new FutureTask<>(() -> {
            sink.emitText(GeneratedArtifactId.of("state"), "value");
            return null;
        });
        Thread.startVirtualThread(otherThreadEmission);

        ExecutionException failure = assertThrows(ExecutionException.class, otherThreadEmission::get);

        assertEquals(
                GeneratedDiagnosticCode.PROVIDER_FAILURE,
                ((GeneratedSinkViolation) failure.getCause()).code());
    }

    private static GameThreadSnapshotSink textSink(GeneratedDiagnosticLimits limits) {
        return new GameThreadSnapshotSink(
                DiagnosticContentType.TEXT, limits, CancellationSignal.neverCancelled());
    }
}
