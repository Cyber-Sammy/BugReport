package com.cybersammy.bugreport.core.draft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CapabilityRequirement;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.specification.StandardFields;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.form.FieldValue;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import com.cybersammy.bugreport.core.registry.ProviderRegistry;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.ReportSessionState;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DraftResolverTest {
    private static final ProviderId PROVIDER_ID = ProviderId.parse("example_mod");
    private static final CategoryId CATEGORY_ID = CategoryId.of("general");

    @Test
    void rebindsToExactTrustedDeclarationsWhileAllowingIncompleteValues() {
        ProviderRegistrySnapshot registry = registry(specification("1.0.0", false));
        ReportDraft draft =
                draft(
                        "1.0.0",
                        CATEGORY_ID,
                        FormSubmission.builder()
                                .put(
                                        StandardFields.summary().id(),
                                        new FieldValue.Text(" "))
                                .build());

        ResolvedReportDraft resolved = DraftResolver.resolve(draft, registry);

        var provider = registry.find(PROVIDER_ID).orElseThrow();
        assertSame(provider.specification(), resolved.providerSpecification());
        assertSame(provider.support(), resolved.providerSupport());
        assertSame(
                provider.specification().categories().get(CATEGORY_ID),
                resolved.category().orElseThrow());
    }

    @Test
    void rejectsMissingDisabledAndVersionChangedProvidersWithExactCodes() {
        assertCode(
                DraftResolutionCode.PROVIDER_MISSING,
                () -> DraftResolver.resolve(draft(), ProviderRegistrySnapshot.empty()));
        assertCode(
                DraftResolutionCode.PROVIDER_DISABLED,
                () -> DraftResolver.resolve(draft(), registry(specification("1.0.0", true))));
        assertCode(
                DraftResolutionCode.PROVIDER_VERSION_MISMATCH,
                () -> DraftResolver.resolve(draft(), registry(specification("2.0.0", false))));
    }

    @Test
    void rejectsMissingCategoryAndStructurallyIncompatibleFields() {
        ProviderRegistrySnapshot registry = registry(specification("1.0.0", false));
        assertCode(
                DraftResolutionCode.CATEGORY_MISSING,
                () ->
                        DraftResolver.resolve(
                                draft("1.0.0", CategoryId.of("removed"), FormSubmission.empty()),
                                registry));

        FormSubmission wrongType =
                FormSubmission.builder()
                        .put(FieldId.of("summary"), new FieldValue.Checkbox(true))
                        .build();
        assertCode(
                DraftResolutionCode.INVALID_FORM_STRUCTURE,
                () ->
                        DraftResolver.resolve(
                                draft("1.0.0", CATEGORY_ID, wrongType), registry));
    }

    private static ReportDraft draft() {
        return draft("1.0.0", CATEGORY_ID, FormSubmission.empty());
    }

    private static ReportDraft draft(
            String version, CategoryId categoryId, FormSubmission submission) {
        return new ReportDraft(
                ReportSessionId.parse("00000000-0000-4000-8000-000000000012"),
                2,
                PROVIDER_ID,
                ProviderVersion.parse(version),
                Optional.of(categoryId),
                ReportSessionState.FORM_IN_PROGRESS,
                submission);
    }

    private static ProviderSpecification specification(String version, boolean disabled) {
        CategorySpecification category =
                CategorySpecification.builder(
                                CATEGORY_ID,
                                LocalizationKey.of("example_mod.category.general"))
                        .addField(StandardFields.summary())
                        .build();
        ProviderSpecification.Builder builder =
                ProviderSpecification.builder(
                                PROVIDER_ID,
                                ProviderVersion.parse(version),
                                LocalizationKey.of("example_mod.provider"))
                        .supportSide(SupportedSide.PHYSICAL_CLIENT)
                        .addCategory(category);
        if (disabled) {
            builder.requireCapability(
                    new CapabilityRequirement(
                            CapabilityId.of("example_mod:missing"),
                            new CapabilityVersion(1, 0),
                            true));
        }
        return builder.build();
    }

    private static ProviderRegistrySnapshot registry(ProviderSpecification specification) {
        BugReportProvider provider = new BugReportProvider() {
            @Override
            public String providerId() {
                return specification.id().value();
            }

            @Override
            public String providerVersion() {
                return specification.version().value();
            }

            @Override
            public Optional<ProviderSpecification> specification() {
                return Optional.of(specification);
            }
        };
        return ProviderRegistry.createSnapshot(
                List.of(
                        new DiscoveredProvider(
                                PROVIDER_ID.namespace(),
                                "DraftFixtureProvider",
                                provider)));
    }

    private static void assertCode(DraftResolutionCode expected, Runnable operation) {
        DraftResolutionException exception =
                assertThrows(DraftResolutionException.class, operation::run);
        assertEquals(expected, exception.code());
    }
}
