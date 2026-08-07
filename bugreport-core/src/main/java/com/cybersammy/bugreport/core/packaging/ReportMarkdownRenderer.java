package com.cybersammy.bugreport.core.packaging;

import com.cybersammy.bugreport.core.form.FieldValue;
import com.cybersammy.bugreport.core.manifest.ReportManifest;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** Deterministic bounded technical summary containing only reviewed manifest values. */
final class ReportMarkdownRenderer {
    static final int MAX_ENCODED_BYTES = 1024 * 1024;
    private static final int MAX_CHARACTERS = MAX_ENCODED_BYTES;
    private static final String MARKDOWN_PUNCTUATION = "\\`*{}_[]()#+-.!|>";

    private ReportMarkdownRenderer() {}

    static byte[] render(ReportManifest manifest) {
        ReportManifest value = java.util.Objects.requireNonNull(manifest, "manifest");
        BoundedText output = new BoundedText();
        output.append("# Bug Report\n\n");
        output.append("- Report ID: `").append(value.reportId().toString()).append("`\n");
        output.append("- Created: `").append(value.createdAt().toString()).append("`\n");
        output.append("- Producer: `").append(value.producer().modVersion()).append("`\n");
        value.target().ifPresent(target -> output
                .append("- Provider: `")
                .append(target.providerId().value())
                .append("` `")
                .append(target.providerVersion().value())
                .append("`\n- Category: `")
                .append(target.categoryId().value())
                .append("`\n"));
        output.append("\n## Reviewed fields\n");
        if (value.reviewedFields().values().isEmpty()) {
            output.append("\n_None._\n");
        } else {
            value.reviewedFields().values().forEach((id, field) -> output
                    .append("\n### `")
                    .append(id.value())
                    .append("`\n\n")
                    .append(escape(renderValue(field)))
                    .append("\n"));
        }
        byte[] encoded = output.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_ENCODED_BYTES) {
            throw limit();
        }
        return encoded;
    }

    private static String renderValue(FieldValue value) {
        return switch (value) {
            case FieldValue.Text text -> text.value();
            case FieldValue.TextList list -> String.join("\n", list.values());
            case FieldValue.Checkbox checkbox -> Boolean.toString(checkbox.checked());
            case FieldValue.Selection selection -> selection.optionId().value();
            case FieldValue.MultiSelection selection -> selection.optionIds().stream()
                    .map(option -> option.value())
                    .collect(Collectors.joining(", "));
            case FieldValue.IntegerNumber number -> number.value().toString();
            case FieldValue.DecimalNumber number -> number.value().toString();
            case FieldValue.Severity severity -> severity.value().value();
            case FieldValue.SideContext side -> side.value().value();
        };
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(Math.min(value.length() * 2, MAX_CHARACTERS));
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\r') {
                if (index + 1 < value.length() && value.charAt(index + 1) == '\n') {
                    index++;
                }
                escaped.append("  \n");
            } else if (character == '\n') {
                escaped.append("  \n");
            } else if (character == '<') {
                escaped.append("&lt;");
            } else if (character == '>') {
                escaped.append("&gt;");
            } else if (character == '&') {
                escaped.append("&amp;");
            } else {
                if (MARKDOWN_PUNCTUATION.indexOf(character) >= 0) {
                    escaped.append('\\');
                }
                escaped.append(character);
            }
            if (escaped.length() > MAX_CHARACTERS) {
                throw limit();
            }
        }
        return escaped.toString();
    }

    private static ReportPackagePlanException limit() {
        return new ReportPackagePlanException(
                ReportPackagePlanCode.MARKDOWN_LIMIT_EXCEEDED,
                null,
                "Human-readable report exceeds the product limit");
    }

    private static final class BoundedText {
        private final StringBuilder value = new StringBuilder();

        private BoundedText append(String text) {
            if (value.length() > MAX_CHARACTERS - text.length()) {
                throw limit();
            }
            value.append(text);
            return this;
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}
