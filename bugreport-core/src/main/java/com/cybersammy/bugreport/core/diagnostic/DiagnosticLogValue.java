package com.cybersammy.bugreport.core.diagnostic;

/** Renders untrusted diagnostic values as bounded single-line log tokens. */
public final class DiagnosticLogValue {
    private static final int MAX_RENDERED_INPUT_LENGTH = 256;
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private DiagnosticLogValue() {}

    /**
     * Returns a bounded representation that cannot inject log-token separators
     * or line breaks.
     *
     * @param value untrusted diagnostic value
     * @return safe single-line representation
     */
    public static String render(String value) {
        if (value == null) {
            return "<null>";
        }
        if (value.isEmpty()) {
            return "<empty>";
        }

        int renderedLength = Math.min(value.length(), MAX_RENDERED_INPUT_LENGTH);
        StringBuilder rendered = new StringBuilder(renderedLength);
        for (int index = 0; index < renderedLength; index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '|') {
                rendered.append('\\').append(character);
            } else if (character >= 0x21 && character <= 0x7e) {
                rendered.append(character);
            } else {
                appendUnicodeEscape(rendered, character);
            }
        }
        if (renderedLength < value.length()) {
            rendered.append("...(length=").append(value.length()).append(')');
        }
        return rendered.toString();
    }

    private static void appendUnicodeEscape(StringBuilder target, char character) {
        target.append("\\u");
        target.append(HEX_DIGITS[(character >>> 12) & 0xf]);
        target.append(HEX_DIGITS[(character >>> 8) & 0xf]);
        target.append(HEX_DIGITS[(character >>> 4) & 0xf]);
        target.append(HEX_DIGITS[character & 0xf]);
    }
}
