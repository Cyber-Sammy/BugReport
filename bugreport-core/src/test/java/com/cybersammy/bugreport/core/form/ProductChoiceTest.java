package com.cybersammy.bugreport.core.form;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ProductChoiceTest {
    @Test
    void severityValuesAndLocalizationKeysAreStable() {
        assertEquals(
                List.of("low", "moderate", "high", "blocking"),
                java.util.Arrays.stream(ReportSeverity.values())
                        .map(ReportSeverity::value)
                        .toList());
        for (ReportSeverity severity : ReportSeverity.values()) {
            assertEquals(
                    "bugreport.field.severity.option." + severity.value(),
                    severity.labelKey().value());
        }
    }

    @Test
    void sideContextValuesAndLocalizationKeysAreStable() {
        assertEquals(
                List.of(
                        "client_general",
                        "singleplayer",
                        "multiplayer_client",
                        "dedicated_server",
                        "unknown"),
                java.util.Arrays.stream(ReportSideContext.values())
                        .map(ReportSideContext::value)
                        .toList());
        for (ReportSideContext context : ReportSideContext.values()) {
            assertEquals(
                    "bugreport.field.side_context.option." + context.value(),
                    context.labelKey().value());
        }
    }
}
