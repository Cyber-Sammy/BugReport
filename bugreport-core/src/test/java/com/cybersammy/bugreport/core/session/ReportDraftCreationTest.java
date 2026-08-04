package com.cybersammy.bugreport.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.FieldKind;
import com.cybersammy.bugreport.api.specification.FieldSpecification;
import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.core.draft.ReportDraft;
import com.cybersammy.bugreport.core.form.FieldValue;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ReportDraftCreationTest {
    @Test
    void capturesPersistenceSafeIdentityFromTrustedSessionSnapshot() {
        CategoryId categoryId = CategoryId.of("details");
        FieldId fieldId = FieldId.of("notes");
        var specification =
                SessionProviderFixture.specificationBuilder("example_mod")
                        .addCategory(
                                CategorySpecification.builder(
                                                categoryId,
                                                LocalizationKey.of(
                                                        "example_mod.category.details"))
                                        .addField(
                                                FieldSpecification.builder(
                                                                fieldId,
                                                                FieldKind.MULTILINE_TEXT,
                                                                LocalizationKey.of(
                                                                        "example_mod.field.notes"),
                                                                PrivacyClassification.PERSONAL)
                                                        .build())
                                        .build())
                        .build();
        ProviderRegistrySnapshot registry = SessionProviderFixture.registry(specification);
        ReportSessionId id =
                new ReportSessionId(
                        UUID.fromString("00000000-0000-4000-8000-000000000013"));
        ReportSession session =
                new ReportSessionFactory(registry).create(id, ProviderId.parse("example_mod"));
        ReportSessionSnapshot snapshot = session.selectCategory(categoryId);
        FormSubmission submission =
                FormSubmission.builder()
                        .put(fieldId, new FieldValue.Text("Local draft"))
                        .build();

        ReportDraft draft = ReportDraft.from(snapshot, submission);

        assertEquals(id, draft.sessionId());
        assertEquals(snapshot.revision(), draft.revision());
        assertEquals(specification.id(), draft.providerId());
        assertEquals(specification.version(), draft.providerVersion());
        assertEquals(categoryId, draft.categoryId().orElseThrow());
        assertEquals(submission, draft.formSubmission());
    }

    @Test
    void rejectsValuesOutsideTheSelectedTrustedCategory() {
        ProviderRegistrySnapshot registry =
                SessionProviderFixture.registry(
                        SessionProviderFixture.specification("example_mod"));
        ReportSession session =
                new ReportSessionFactory(registry)
                        .create(
                                new ReportSessionId(
                                        UUID.fromString(
                                                "00000000-0000-4000-8000-000000000014")),
                                ProviderId.parse("example_mod"));
        ReportSessionSnapshot snapshot = session.selectCategory(CategoryId.of("general"));
        FormSubmission submission =
                FormSubmission.builder()
                        .put(FieldId.of("foreign"), new FieldValue.Text("value"))
                        .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> ReportDraft.from(snapshot, submission));
    }
}
