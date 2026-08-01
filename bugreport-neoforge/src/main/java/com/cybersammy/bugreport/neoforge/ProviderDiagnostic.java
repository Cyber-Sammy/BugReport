package com.cybersammy.bugreport.neoforge;

import com.cybersammy.bugreport.core.diagnostic.DiagnosticLogValue;
import java.util.Objects;

record ProviderDiagnostic(
        ProviderDiagnosticCode code,
        String ownerModId,
        String className) {
    ProviderDiagnostic {
        Objects.requireNonNull(code);
        Objects.requireNonNull(ownerModId);
        Objects.requireNonNull(className);
    }

    static ProviderDiagnostic forClass(
            ProviderDiagnosticCode code,
            String ownerModId,
            String className) {
        return new ProviderDiagnostic(code, ownerModId, className);
    }

    String logToken() {
        return code.logToken()
                + "|owner="
                + DiagnosticLogValue.render(ownerModId)
                + "|class="
                + DiagnosticLogValue.render(className);
    }

    @Override
    public String toString() {
        return logToken();
    }
}
