package com.cybersammy.bugreport.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
                                        candidate("mod_a", DuplicateB.class),
                                        candidate("mod_b", Independent.class)),
                                List.of());

        assertEquals(List.of("mod_b"), snapshot.providerIds());
        assertEquals(
                List.of(
                        ProviderDiagnosticCode.DUPLICATE_PROVIDER_ID,
                        ProviderDiagnosticCode.DUPLICATE_PROVIDER_ID),
                snapshot.diagnostics().stream()
                        .map(ProviderDiagnostic::code)
                        .toList());
        assertEquals(
                List.of("mod_a:duplicate", "mod_a:duplicate"),
                snapshot.diagnostics().stream()
                        .map(ProviderDiagnostic::providerId)
                        .toList());
    }

    @Test
    void reportsForeignNamespaceWhileRetainingItsOwner()
            throws NoSuchMethodException {
        ProviderDiscoverySnapshot snapshot =
                new ProviderRegistryBuilder()
                        .build(
                                List.of(
                                        candidate("mod_a", ForeignNamespace.class),
                                        candidate("mod_b", Independent.class)),
                                List.of());

        assertEquals(List.of("mod_b"), snapshot.providerIds());
        assertEquals(1, snapshot.diagnostics().size());
        assertEquals(
                ProviderDiagnosticCode.INVALID_PROVIDER_ID,
                snapshot.diagnostics().getFirst().code());
        assertEquals("mod_b", snapshot.diagnostics().getFirst().providerId());
    }

    @Test
    void distinguishesProviderIdFailureFromReturnedNull()
            throws NoSuchMethodException {
        ProviderDiscoverySnapshot snapshot =
                new ProviderRegistryBuilder()
                        .build(
                                List.of(
                                        candidate("mod_a", ThrowingId.class),
                                        candidate("mod_b", NullId.class)),
                                List.of());

        assertEquals(List.of(), snapshot.providerIds());
        assertEquals(
                List.of(
                        ProviderDiagnosticCode.PROVIDER_ID_FAILED,
                        ProviderDiagnosticCode.INVALID_PROVIDER_ID),
                snapshot.diagnostics().stream()
                        .map(ProviderDiagnostic::code)
                        .toList());
        assertNull(snapshot.diagnostics().get(0).providerId());
        assertNull(snapshot.diagnostics().get(1).providerId());
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
            return "mod_a:duplicate";
        }
    }

    public static final class DuplicateB implements BugReportProvider {
        public DuplicateB() {}

        @Override
        public String providerId() {
            return "mod_a:duplicate";
        }
    }

    public static final class Independent implements BugReportProvider {
        public Independent() {}

        @Override
        public String providerId() {
            return "mod_b";
        }
    }

    public static final class ForeignNamespace implements BugReportProvider {
        public ForeignNamespace() {}

        @Override
        public String providerId() {
            return "mod_b";
        }
    }

    public static final class ThrowingId implements BugReportProvider {
        public ThrowingId() {}

        @Override
        public String providerId() {
            throw new IllegalStateException("failure");
        }
    }

    public static final class NullId implements BugReportProvider {
        public NullId() {}

        @Override
        public String providerId() {
            return null;
        }
    }
}
