package com.cybersammy.bugreport.neoforge.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.FieldOptionId;
import com.cybersammy.bugreport.api.identifier.NamespaceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.FieldKind;
import com.cybersammy.bugreport.api.specification.FieldOption;
import com.cybersammy.bugreport.api.specification.FieldSpecification;
import com.cybersammy.bugreport.api.validation.ValidationPath;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import com.cybersammy.bugreport.core.registry.ProviderRegistry;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.form.FieldValue;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.form.ReportSeverity;
import com.cybersammy.bugreport.core.form.ReportSideContext;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BugReportCommandServiceTest {
    private final BugReportCommandService commands =
            new BugReportCommandService(ProviderRegistrySnapshot::empty);

    @Test
    void helpUsesOneLocalizedSafeMessage() {
        assertEquals(
                List.of("bugreport.command.help"),
                commands.help().stream().map(BugReportCommandService.Message::translationKey).toList());
    }

    @Test
    void emptyRegistryIsReportedWithoutInternalDiagnostics() {
        assertEquals(
                List.of("bugreport.command.list.empty"),
                commands.listProviders().stream()
                        .map(BugReportCommandService.Message::translationKey)
                        .toList());
    }

    @Test
    void invalidIdentifiersAndUnknownSessionsAreSafeCommandErrors() {
        assertEquals(
                "bugreport.command.error.invalid_provider",
                commands.create("NOT A PROVIDER", null).getFirst().translationKey());
        assertEquals(
                "bugreport.command.error.unknown_session",
                commands.open("not-a-session").getFirst().translationKey());
        assertEquals(
                "bugreport.command.error.unknown_session",
                commands.discard("not-a-session").getFirst().translationKey());
    }

    @Test
    void createOpenAndDiscardUseTheTrustedRegistrySessionLifecycle() {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        BugReportCommandService.Message created = service.create("example_mod", "general").getFirst();

        assertEquals("bugreport.command.create.success", created.translationKey());
        String sessionId = (String) created.arguments()[0];
        assertEquals("bugreport.command.open.summary", service.open(sessionId).getFirst().translationKey());
        assertEquals("bugreport.command.discard.success", service.discard(sessionId).getFirst().translationKey());
        assertEquals("bugreport.command.error.unknown_session", service.open(sessionId).getFirst().translationKey());
    }

    @Test
    void discardRemovesAnActiveFormSessionFromTheCommandService() {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        String sessionId = (String) service.create("example_mod", "general")
                .getFirst().arguments()[0];
        assertTrue(service.form(sessionId).isPresent());

        assertEquals("bugreport.command.discard.success",
                service.discard(sessionId).getFirst().translationKey());
        assertEquals("bugreport.command.error.unknown_session",
                service.open(sessionId).getFirst().translationKey());
        assertTrue(service.form(sessionId).isEmpty());
    }

    @Test
    void categoryErrorsKeepMalformedAndUndeclaredInputsDistinct() {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        assertEquals("bugreport.command.error.invalid_category",
                service.create("example_mod", "not a category").getFirst().translationKey());
        BugReportCommandService.Message unknown = service.create("example_mod", "other").getFirst();
        assertEquals("bugreport.command.error.unknown_category", unknown.translationKey());
        assertEquals("other", unknown.arguments()[0]);
    }

    @Test
    void trustedFormSupportsEveryEditableFieldKindWithoutMutatingTheSession() {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        String sessionId = (String) service.create("example_mod", "general")
                .getFirst().arguments()[0];
        BugReportCommandService.FormView before = service.form(sessionId).orElseThrow();

        FormSubmission submission = FormSubmission.builder()
                .put(FieldId.of("single"), new FieldValue.Text("summary"))
                .put(FieldId.of("multi_line"), new FieldValue.Text("line one\nline two"))
                .put(FieldId.of("steps"), new FieldValue.TextList(List.of("first", "second")))
                .put(FieldId.of("checkbox"), new FieldValue.Checkbox(false))
                .put(FieldId.of("single_select"),
                        new FieldValue.Selection(FieldOptionId.of("one")))
                .put(FieldId.of("multi_select"),
                        new FieldValue.MultiSelection(Set.of(FieldOptionId.of("two"))))
                .put(FieldId.of("integer"), new FieldValue.IntegerNumber(BigInteger.TEN))
                .put(FieldId.of("decimal"), new FieldValue.DecimalNumber(new BigDecimal("10.5")))
                .put(FieldId.of("expected"), new FieldValue.Text("expected"))
                .put(FieldId.of("actual"), new FieldValue.Text("actual"))
                .put(FieldId.of("severity"), new FieldValue.Severity(ReportSeverity.HIGH))
                .put(FieldId.of("side"),
                        new FieldValue.SideContext(ReportSideContext.SINGLEPLAYER))
                .build();

        BugReportCommandService.FormResult first = service.submitForm(sessionId, submission);
        BugReportCommandService.FormResult second = service.submitForm(sessionId, submission);
        BugReportCommandService.FormView after = service.form(sessionId).orElseThrow();

        assertTrue(first.validation().isValid());
        assertTrue(second.validation().isValid());
        assertEquals(before.state(), after.state());
        assertEquals(before.revision(), after.revision());
        assertEquals(CategoryId.of("general"), after.category().id());
    }

    @Test
    void formLookupAndSubmissionFailClosedForUnknownSession() {
        assertTrue(commands.form("not-a-session").isEmpty());
        BugReportCommandService.FormResult result = commands.submitForm(
                "not-a-session", FormSubmission.empty());
        assertTrue(result.unknownSession());
        assertEquals(null, result.validation());
    }

    @Test
    void invalidFormReturnsStableFieldCodeAndPathWithoutChangingSession() {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        String sessionId = (String) service.create("example_mod", "general")
                .getFirst().arguments()[0];
        BugReportCommandService.FormView before = service.form(sessionId).orElseThrow();

        BugReportCommandService.FormResult result = service.submitForm(
                sessionId, FormSubmission.empty());
        BugReportCommandService.FormView after = service.form(sessionId).orElseThrow();

        assertFalse(result.validation().isValid());
        assertEquals("bugreport:field_required",
                result.validation().issues().getFirst().code().value());
        assertEquals(ValidationPath.root().property("fields").property("single"),
                result.validation().issues().getFirst().path());
        assertEquals(before.state(), after.state());
        assertEquals(before.revision(), after.revision());
    }

    private static ProviderRegistrySnapshot registry() {
        return ProviderRegistry.createSnapshot(List.of(new DiscoveredProvider(
                NamespaceId.of("example_mod"), TestProvider.class.getName(), new TestProvider())));
    }

    private static final class TestProvider implements BugReportProvider {
        private static final ProviderSpecification SPECIFICATION = ProviderSpecification.builder(
                        ProviderId.parse("example_mod"), ProviderVersion.parse("1.0.0"),
                        LocalizationKey.of("example_mod.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addCategory(category())
                .build();

        @Override
        public String providerId() { return "example_mod"; }

        @Override
        public String providerVersion() { return "1.0.0"; }

        @Override
        public Optional<ProviderSpecification> specification() { return Optional.of(SPECIFICATION); }
    }

    private static CategorySpecification category() {
        CategorySpecification.Builder category = CategorySpecification.builder(
                CategoryId.of("general"), LocalizationKey.of("example_mod.category.general"));
        category.addField(field("single", FieldKind.SINGLE_LINE_TEXT, PrivacyClassification.PERSONAL));
        category.addField(field("multi_line", FieldKind.MULTILINE_TEXT, PrivacyClassification.PERSONAL));
        category.addField(field("steps", FieldKind.REPRODUCTION_STEPS, PrivacyClassification.PERSONAL));
        category.addField(field("checkbox", FieldKind.CHECKBOX, PrivacyClassification.LOW));
        category.addField(selectionField("single_select", FieldKind.SINGLE_SELECT));
        category.addField(selectionField("multi_select", FieldKind.MULTI_SELECT));
        category.addField(field("integer", FieldKind.INTEGER, PrivacyClassification.LOW));
        category.addField(field("decimal", FieldKind.DECIMAL, PrivacyClassification.LOW));
        category.addField(field("expected", FieldKind.EXPECTED_BEHAVIOR, PrivacyClassification.PERSONAL));
        category.addField(field("actual", FieldKind.ACTUAL_BEHAVIOR, PrivacyClassification.PERSONAL));
        category.addField(field("severity", FieldKind.SEVERITY, PrivacyClassification.LOW));
        category.addField(field("side", FieldKind.SIDE_CONTEXT, PrivacyClassification.LOW));
        category.addField(field("information", FieldKind.READ_ONLY_INFORMATION,
                PrivacyClassification.LOW));
        return category.build();
    }

    private static FieldSpecification field(
            String id, FieldKind kind, PrivacyClassification privacy) {
        return FieldSpecification.builder(FieldId.of(id), kind,
                        LocalizationKey.of("example_mod.field." + id), privacy)
                .required("single".equals(id))
                .build();
    }

    private static FieldSpecification selectionField(String id, FieldKind kind) {
        return FieldSpecification.builder(FieldId.of(id), kind,
                        LocalizationKey.of("example_mod.field." + id), PrivacyClassification.LOW)
                .addOption(new FieldOption(
                        FieldOptionId.of("one"), LocalizationKey.of("example_mod.option.one")))
                .addOption(new FieldOption(
                        FieldOptionId.of("two"), LocalizationKey.of("example_mod.option.two")))
                .build();
    }
}
