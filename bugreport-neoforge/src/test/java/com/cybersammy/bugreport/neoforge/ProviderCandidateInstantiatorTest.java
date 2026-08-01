package com.cybersammy.bugreport.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cybersammy.bugreport.api.BugReportProvider;
import org.junit.jupiter.api.Test;

final class ProviderCandidateInstantiatorTest {
    @Test
    void createsAProviderWithTrustedProvenance() throws NoSuchMethodException {
        ProviderCandidate candidate =
                new ProviderCandidate(
                        "mod_a",
                        ValidProvider.class.getName(),
                        ValidProvider.class.getConstructor());

        ProviderCandidateInstantiator.Evaluation evaluation =
                new ProviderCandidateInstantiator().instantiate(candidate);

        assertNotNull(evaluation.provider());
        assertEquals("mod_a", evaluation.provider().ownerNamespace().value());
        assertEquals(ValidProvider.class.getName(), evaluation.provider().implementationClass());
        assertNull(evaluation.diagnostic());
    }

    @Test
    void isolatesConstructorFailure() throws NoSuchMethodException {
        ProviderCandidate candidate =
                new ProviderCandidate(
                        "mod_a",
                        ThrowingConstructorProvider.class.getName(),
                        ThrowingConstructorProvider.class.getConstructor());

        ProviderCandidateInstantiator.Evaluation evaluation =
                new ProviderCandidateInstantiator().instantiate(candidate);

        assertNull(evaluation.provider());
        assertEquals(
                ProviderDiagnosticCode.INSTANTIATION_FAILED,
                evaluation.diagnostic().code());
    }

    public static final class ValidProvider implements BugReportProvider {
        public ValidProvider() {}

        @Override
        public String providerId() {
            return "mod_a";
        }
    }

    public static final class ThrowingConstructorProvider implements BugReportProvider {
        public ThrowingConstructorProvider() {
            throw new IllegalStateException("constructor failure");
        }

        @Override
        public String providerId() {
            return "mod_a";
        }
    }
}
