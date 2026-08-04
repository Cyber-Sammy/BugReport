package com.cybersammy.bugreport.core.draft;

import com.cybersammy.bugreport.api.identifier.ValidationCode;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.validation.ValidationResult;
import com.cybersammy.bugreport.core.form.FieldValidationCode;
import com.cybersammy.bugreport.core.form.FieldValidator;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Rebinds persistence-safe draft identities to current trusted registry declarations. */
public final class DraftResolver {
    private static final Set<ValidationCode> STRUCTURAL_FIELD_ERRORS =
            Set.of(
                    FieldValidationCode.UNDECLARED_FIELD.code(),
                    FieldValidationCode.TYPE_MISMATCH.code(),
                    FieldValidationCode.UNKNOWN_OPTION.code(),
                    FieldValidationCode.READ_ONLY.code());

    private DraftResolver() {}

    /**
     * Resolves a draft against an accepted immutable provider registry.
     *
     * <p>Ordinary incomplete or constraint-invalid form values remain recoverable. Only values
     * that cannot be interpreted under the trusted category declaration are rejected.
     *
     * @param draft parsed persistence-safe draft
     * @param registry current trusted registry snapshot
     * @return draft rebound to exact current declarations
     */
    public static ResolvedReportDraft resolve(
            ReportDraft draft, ProviderRegistrySnapshot registry) {
        ReportDraft parsedDraft = Objects.requireNonNull(draft, "draft");
        ProviderRegistrySnapshot trustedRegistry = Objects.requireNonNull(registry, "registry");
        RegisteredProvider provider =
                trustedRegistry
                        .find(parsedDraft.providerId())
                        .orElseThrow(
                                () ->
                                        failure(
                                                DraftResolutionCode.PROVIDER_MISSING,
                                                "Draft provider is not registered: "
                                                        + parsedDraft.providerId()));
        if (provider.support().state() == ProviderSupportState.DISABLED) {
            throw failure(
                    DraftResolutionCode.PROVIDER_DISABLED,
                    "Draft provider is disabled: " + parsedDraft.providerId());
        }
        if (!provider.specification().version().equals(parsedDraft.providerVersion())) {
            throw failure(
                    DraftResolutionCode.PROVIDER_VERSION_MISMATCH,
                    "Draft provider version does not match the current specification: "
                            + parsedDraft.providerId());
        }

        Optional<CategorySpecification> category =
                parsedDraft
                        .categoryId()
                        .map(
                                categoryId ->
                                        Optional.ofNullable(
                                                        provider
                                                                .specification()
                                                                .categories()
                                                                .get(categoryId))
                                                .orElseThrow(
                                                        () ->
                                                                failure(
                                                                        DraftResolutionCode
                                                                                .CATEGORY_MISSING,
                                                                        "Draft category is not declared by the current provider: "
                                                                                + categoryId)));
        category.ifPresent(value -> validateFormStructure(value, parsedDraft));
        return new ResolvedReportDraft(
                parsedDraft,
                provider.specification(),
                provider.support(),
                category);
    }

    private static void validateFormStructure(
            CategorySpecification category, ReportDraft draft) {
        ValidationResult validation = FieldValidator.validate(category, draft.formSubmission());
        boolean structurallyInvalid =
                validation.issues().stream()
                        .anyMatch(issue -> STRUCTURAL_FIELD_ERRORS.contains(issue.code()));
        if (structurallyInvalid) {
            throw failure(
                    DraftResolutionCode.INVALID_FORM_STRUCTURE,
                    "Draft fields do not match the current category structure: "
                            + category.id());
        }
    }

    private static DraftResolutionException failure(
            DraftResolutionCode code, String message) {
        return new DraftResolutionException(code, message);
    }
}
