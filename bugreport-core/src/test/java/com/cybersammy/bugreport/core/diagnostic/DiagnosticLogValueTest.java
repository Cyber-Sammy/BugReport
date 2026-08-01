package com.cybersammy.bugreport.core.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DiagnosticLogValueTest {
    @Test
    void rendersSpecialValuesWithoutLogInjection() {
        assertEquals("<null>", DiagnosticLogValue.render(null));
        assertEquals("<empty>", DiagnosticLogValue.render(""));
        assertEquals(
                "value\\|line\\u000a\\u00e9",
                DiagnosticLogValue.render("value|line\né"));
    }

    @Test
    void boundsRenderedInput() {
        String rendered = DiagnosticLogValue.render("a".repeat(300));

        assertTrue(rendered.endsWith("...(length=300)"));
        assertTrue(rendered.length() < 300);
    }
}
