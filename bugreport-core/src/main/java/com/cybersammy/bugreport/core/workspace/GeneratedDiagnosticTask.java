package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import java.util.Objects;

/** Package-private revocable execution state for one generated callback. */
final class GeneratedDiagnosticTask {
    private final GeneratedDiagnosticInvocation invocation;
    private final SupportedSide side;
    private final ReportWorkspace workspace;
    private final CancellationSignal cancellation;
    private final GeneratedDiagnosticLimits limits;
    private final BoundedGeneratedDiagnosticSink sink;

    GeneratedDiagnosticTask(
            GeneratedDiagnosticInvocation invocation,
            SupportedSide side,
            ReportWorkspace workspace,
            CancellationSignal cancellation,
            long remainingCollectionBytes) {
        this.invocation = Objects.requireNonNull(invocation, "invocation");
        this.side = Objects.requireNonNull(side, "side");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        limits = GeneratedDiagnosticLimits.from(
                invocation.generator().constraints(), remainingCollectionBytes);
        sink = new BoundedGeneratedDiagnosticSink(invocation, workspace, cancellation, limits);
    }

    GeneratedDiagnosticInvocation invocation() {
        return invocation;
    }

    SupportedSide side() {
        return side;
    }

    ReportWorkspace workspace() {
        return workspace;
    }

    CancellationSignal cancellation() {
        return cancellation;
    }

    CancellationSignal effectiveCancellation() {
        return () -> cancellation.isCancellationRequested() || sink.isRevoked();
    }

    BoundedGeneratedDiagnosticSink sink() {
        return sink;
    }

    GeneratedDiagnosticLimits limits() {
        return limits;
    }

    GeneratedDiagnosticCode requestRevocation(GeneratedDiagnosticCode code, String message) {
        return sink.requestRevocation(code, message);
    }
}
