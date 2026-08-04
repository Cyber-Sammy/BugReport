package com.cybersammy.bugreport.core.draft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.FieldOptionId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.api.version.SchemaVersion;
import com.cybersammy.bugreport.core.form.FieldValue;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.form.ReportSeverity;
import com.cybersammy.bugreport.core.form.ReportSideContext;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.ReportSessionState;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DraftJsonCodecTest {
    @Test
    void currentGoldenFixtureRoundTripsByteForByte() throws IOException {
        byte[] fixture = fixture("report-draft-v1.0.json");

        DecodedReportDraft decoded = DraftJsonCodec.decode(fixture);

        assertEquals(new SchemaVersion(1, 0), decoded.sourceVersion());
        assertFalse(decoded.migrated());
        assertArrayEquals(stripFinalNewline(fixture), DraftJsonCodec.encode(decoded.draft()));
    }

    @Test
    void legacyFixtureMigratesToCurrentCanonicalShape() throws IOException {
        DecodedReportDraft legacy =
                DraftJsonCodec.decode(fixture("report-draft-v0.1.json"));
        DecodedReportDraft current =
                DraftJsonCodec.decode(fixture("report-draft-v1.0.json"));

        assertEquals(new SchemaVersion(0, 1), legacy.sourceVersion());
        assertTrue(legacy.migrated());
        assertEquals(current.draft(), legacy.draft());
        assertArrayEquals(DraftJsonCodec.encode(current.draft()), DraftJsonCodec.encode(legacy.draft()));
    }

    @Test
    void roundTripsEveryFieldRepresentationDeterministically() {
        FormSubmission submission = FormSubmission.builder()
                .put(FieldId.of("text"), new FieldValue.Text("line one\nline two"))
                .put(FieldId.of("steps"), new FieldValue.TextList(List.of("one", "two")))
                .put(FieldId.of("checked"), new FieldValue.Checkbox(true))
                .put(
                        FieldId.of("choice"),
                        new FieldValue.Selection(FieldOptionId.of("first")))
                .put(
                        FieldId.of("choices"),
                        new FieldValue.MultiSelection(
                                Set.of(FieldOptionId.of("second"), FieldOptionId.of("first"))))
                .put(
                        FieldId.of("integer"),
                        new FieldValue.IntegerNumber(new BigInteger("12345678901234567890")))
                .put(
                        FieldId.of("decimal"),
                        new FieldValue.DecimalNumber(new BigDecimal("1.2300")))
                .put(
                        FieldId.of("severity"),
                        new FieldValue.Severity(ReportSeverity.HIGH))
                .put(
                        FieldId.of("side"),
                        new FieldValue.SideContext(ReportSideContext.MULTIPLAYER_CLIENT))
                .build();
        ReportDraft draft = draft(submission);

        byte[] first = DraftJsonCodec.encode(draft);
        byte[] second = DraftJsonCodec.encode(draft);

        assertArrayEquals(first, second);
        assertEquals(draft, DraftJsonCodec.decode(first).draft());
    }

    @Test
    void rejectsDuplicateMembersUnsupportedVersionsAndWrongRepresentations() {
        String base =
                "{\"schemaId\":\"bugreport:report_draft\","
                        + "\"schemaVersion\":\"1.0\","
                        + "\"sessionId\":\"00000000-0000-4000-8000-000000000010\","
                        + "\"revision\":0,\"providerId\":\"example_mod\","
                        + "\"providerVersion\":\"1.0.0\",\"state\":\"CREATED\","
                        + "\"fields\":{}}";

        assertThrows(
                DraftFormatException.class,
                () -> DraftJsonCodec.decode(base.replaceFirst("\\{", "{\"revision\":0,")
                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                DraftFormatException.class,
                () -> DraftJsonCodec.decode(base.replace("1.0\"", "2.0\"")
                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                DraftFormatException.class,
                () -> DraftJsonCodec.decode(
                        base.replace(
                                        "\"fields\":{}",
                                        "\"fields\":{\"bad\":{\"type\":\"checkbox\",\"value\":\"true\"}}")
                                .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsOversizedAndMalformedUtf8InputsBeforeDomainConstruction() {
        byte[] oversized = new byte[DraftJsonCodec.MAX_ENCODED_BYTES + 1];
        Arrays.fill(oversized, (byte) ' ');

        assertThrows(DraftFormatException.class, () -> DraftJsonCodec.decode(oversized));
        assertThrows(
                DraftFormatException.class,
                () -> DraftJsonCodec.decode(new byte[] {(byte) 0xc3, (byte) 0x28}));
    }

    @Test
    void rejectsDraftWhoseCanonicalEncodingExceedsTheDocumentCeiling() {
        String maximumText = "x".repeat(FieldValue.MAX_TEXT_CODE_POINTS);
        FormSubmission.Builder fields = FormSubmission.builder();
        for (int index = 0; index < 17; index++) {
            fields.put(FieldId.of("text_" + index), new FieldValue.Text(maximumText));
        }

        ReportDraft draft = draft(fields.build());

        assertThrows(IllegalArgumentException.class, () -> DraftJsonCodec.encode(draft));
    }

    @Test
    void acceptsBoundedUnknownMembersButRejectsExcessiveNesting() {
        String created =
                "{\"schemaId\":\"bugreport:report_draft\","
                        + "\"schemaVersion\":\"1.0\","
                        + "\"sessionId\":\"00000000-0000-4000-8000-000000000010\","
                        + "\"revision\":0,\"providerId\":\"example_mod\","
                        + "\"providerVersion\":\"1.0.0\",\"state\":\"CREATED\","
                        + "\"future\":{\"nested\":[1,true,null]},\"fields\":{}}";

        ReportDraft decoded =
                DraftJsonCodec.decode(created.getBytes(StandardCharsets.UTF_8)).draft();

        assertEquals(Optional.empty(), decoded.categoryId());
        assertEquals(FormSubmission.empty(), decoded.formSubmission());

        String deeplyNested = "[".repeat(18) + "null" + "]".repeat(18);
        String unsafe = created.replace("{\"nested\":[1,true,null]}", deeplyNested);
        assertThrows(
                DraftFormatException.class,
                () -> DraftJsonCodec.decode(unsafe.getBytes(StandardCharsets.UTF_8)));
    }

    private static ReportDraft draft(FormSubmission submission) {
        return new ReportDraft(
                ReportSessionId.parse("00000000-0000-4000-8000-000000000011"),
                7,
                ProviderId.parse("example_mod"),
                ProviderVersion.parse("1.0.0"),
                Optional.of(CategoryId.of("general")),
                ReportSessionState.FORM_IN_PROGRESS,
                submission);
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream input =
                DraftJsonCodecTest.class.getResourceAsStream("/drafts/" + name)) {
            if (input == null) {
                throw new IOException("Missing fixture: " + name);
            }
            return input.readAllBytes();
        }
    }

    private static byte[] stripFinalNewline(byte[] value) {
        int length = value.length;
        while (length > 0 && (value[length - 1] == '\n' || value[length - 1] == '\r')) {
            length--;
        }
        return Arrays.copyOf(value, length);
    }
}
