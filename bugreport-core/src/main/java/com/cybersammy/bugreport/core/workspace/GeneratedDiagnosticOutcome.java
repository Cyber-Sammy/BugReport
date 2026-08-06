package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import java.util.Objects;
import java.util.Optional;

/** Immutable privacy-safe outcome for one declared category generator. */
public record GeneratedDiagnosticOutcome(
        DiagnosticGeneratorId generatorId,
        GeneratedDiagnosticOutcomeStatus status,
        Optional<GeneratedDiagnosticResult> result,
        Optional<GeneratedDiagnosticCode> failureCode) {
    public GeneratedDiagnosticOutcome {
        Objects.requireNonNull(generatorId, "generatorId");
        Objects.requireNonNull(status, "status");
        result = Objects.requireNonNull(result, "result");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        boolean successful = status == GeneratedDiagnosticOutcomeStatus.COLLECTED;
        if (successful != result.isPresent() || successful == failureCode.isPresent()) {
            throw new IllegalArgumentException(
                    "Collected outcomes require only a result; failures require only a code");
        }
        result.ifPresent(value -> {
            if (!generatorId.equals(value.generatorId())) {
                throw new IllegalArgumentException("Outcome generator ID does not match result");
            }
        });
    }

    static GeneratedDiagnosticOutcome collected(GeneratedDiagnosticResult result) {
        return new GeneratedDiagnosticOutcome(
                result.generatorId(),
                GeneratedDiagnosticOutcomeStatus.COLLECTED,
                Optional.of(result),
                Optional.empty());
    }

    static GeneratedDiagnosticOutcome failed(
            DiagnosticGeneratorId generatorId,
            GeneratedDiagnosticOutcomeStatus status,
            GeneratedDiagnosticCode code) {
        if (status == GeneratedDiagnosticOutcomeStatus.COLLECTED) {
            throw new IllegalArgumentException("Failure outcome cannot be collected");
        }
        return new GeneratedDiagnosticOutcome(
                generatorId, status, Optional.empty(), Optional.of(code));
    }
}
