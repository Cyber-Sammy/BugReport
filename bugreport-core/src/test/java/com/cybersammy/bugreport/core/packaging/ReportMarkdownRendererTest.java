package com.cybersammy.bugreport.core.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.version.ApiVersion;
import com.cybersammy.bugreport.core.form.FieldValue;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.manifest.ManifestEnvironment;
import com.cybersammy.bugreport.core.manifest.ManifestProducer;
import com.cybersammy.bugreport.core.manifest.ReportManifest;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class ReportMarkdownRendererTest {
    @Test
    void rejectsAggregateMarkdownAboveProductLimit() {
        FormSubmission.Builder fields = FormSubmission.builder();
        for (int index = 0; index < 17; index++) {
            fields.put(
                    FieldId.of("field_" + index),
                    new FieldValue.Text("a".repeat(FieldValue.MAX_TEXT_CODE_POINTS)));
        }
        ReportManifest manifest = manifest(fields.build());

        ReportPackagePlanException failure = assertThrows(
                ReportPackagePlanException.class,
                () -> ReportMarkdownRenderer.render(manifest));

        assertEquals(ReportPackagePlanCode.MARKDOWN_LIMIT_EXCEEDED, failure.code());
    }

    private static ReportManifest manifest(FormSubmission fields) {
        return ReportManifest.builder(
                        ReportSessionId.parse("11111111-1111-4111-8111-111111111111"),
                        Instant.parse("2026-08-07T00:00:00Z"),
                        new ManifestProducer("0.0.1-spike", ApiVersion.parse("0.2.0")),
                        new ManifestEnvironment(
                                "1.21.1",
                                "neoforge",
                                "21.1.227",
                                SupportedSide.PHYSICAL_CLIENT))
                .reviewedFields(fields)
                .build();
    }
}
