package com.cybersammy.bugreport.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cybersammy.bugreport.api.BugReportProvider;
import java.lang.reflect.Constructor;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProviderRegistryBuilderTest {
    @Test
    void rejectsEveryDuplicateWhileRetainingAnIndependentProvider()
            throws NoSuchMethodException {
        ProviderDiscoverySnapshot snapshot =
                new ProviderRegistryBuilder()
                        .build(
                                List.of(
                                        candidate("mod_a", DuplicateA.class),
                                        candidate("mod_b", DuplicateB.class),
                                        candidate("mod_c", Independent.class)),
                                List.of());

        assertEquals(List.of("independent"), snapshot.providerIds());
        assertEquals(
                List.of(
                        ProviderDiagnosticCode.DUPLICATE_PROVIDER_ID,
                        ProviderDiagnosticCode.DUPLICATE_PROVIDER_ID),
                snapshot.diagnostics().stream()
                        .map(ProviderDiagnostic::code)
                        .toList());
        assertEquals(
                List.of("duplicate", "duplicate"),
                snapshot.diagnostics().stream()
                        .map(ProviderDiagnostic::providerId)
                        .toList());
    }

    private static ProviderCandidate candidate(
            String ownerModId,
            Class<? extends BugReportProvider> providerType)
            throws NoSuchMethodException {
        Constructor<? extends BugReportProvider> constructor =
                providerType.getConstructor();
        return new ProviderCandidate(
                ownerModId,
                providerType.getName(),
                constructor);
    }

    public static final class DuplicateA implements BugReportProvider {
        public DuplicateA() {}

        @Override
        public String providerId() {
            return "duplicate";
        }
    }

    public static final class DuplicateB implements BugReportProvider {
        public DuplicateB() {}

        @Override
        public String providerId() {
            return "duplicate";
        }
    }

    public static final class Independent implements BugReportProvider {
        public Independent() {}

        @Override
        public String providerId() {
            return "independent";
        }
    }
}
