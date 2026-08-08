package com.cybersammy.bugreport.core.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.core.configuration.ConfigurationStoreCode;
import org.junit.jupiter.api.Test;

final class DomainErrorModelTest {
    @Test
    void namespacedCodesAreStableAndLowercase() {
        assertEquals(
                "configuration.atomic_move_unsupported",
                DomainErrorCode.from(
                                "configuration", ConfigurationStoreCode.ATOMIC_MOVE_UNSUPPORTED)
                        .value());
    }

    @Test
    void contextRendersInCanonicalKeyOrder() {
        DomainErrorContext context = DomainErrorContext.builder()
                .put(DomainErrorContextKey.SOURCE_ID, "latest_log")
                .put(DomainErrorContextKey.SESSION_ID, "00000000-0000-4000-8000-000000000001")
                .put(DomainErrorContextKey.PROVIDER_ID, "example_mod")
                .build();

        assertEquals(
                "session=00000000-0000-4000-8000-000000000001,provider=example_mod,source=latest_log",
                context.logToken());
    }

    @Test
    void contextRejectsPathsContentAndDuplicateKeys() {
        DomainErrorContext.Builder builder = DomainErrorContext.builder();
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.put(DomainErrorContextKey.ARTIFACT_NAME, "logs/latest log.txt"));

        builder.put(DomainErrorContextKey.ARTIFACT_NAME, "latest_log.txt");
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.put(DomainErrorContextKey.ARTIFACT_NAME, "other.txt"));
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.put(DomainErrorContextKey.OPERATION, "arbitrary.operation"));
    }

    @Test
    void operationIsBoundedAndRenderedFirst() {
        DomainErrorContext context = DomainErrorContext.builder()
                .put(DomainErrorContextKey.TRANSPORT_ID, "bugreport:local_zip")
                .operation(DomainOperation.TRANSPORT_EXECUTE)
                .build();

        assertEquals("operation=transport.execute,transport=bugreport:local_zip", context.logToken());
    }
}
