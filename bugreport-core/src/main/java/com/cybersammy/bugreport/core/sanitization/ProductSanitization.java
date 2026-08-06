package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.List;
import java.util.Objects;

/** Constructs the complete product-owned sanitization policy for one artifact. */
public final class ProductSanitization {
    private ProductSanitization() {}

    public static SanitizationPipeline textPipeline(
            SanitizationPolicy policy,
            String homeDirectory,
            String username,
            SanitizationCaseSensitivity caseSensitivity) {
        SanitizationPolicy configured = Objects.requireNonNull(policy, "policy");
        SanitizationCaseSensitivity sensitivity =
                Objects.requireNonNull(caseSensitivity, "caseSensitivity");
        return new SanitizationPipeline(List.of(
                new MinecraftAuthenticationSanitizationStage(),
                new WebhookSanitizationStage(),
                new BearerTokenSanitizationStage(),
                new ApiKeySanitizationStage(),
                new SessionIdentifierSanitizationStage(),
                new EmailAddressSanitizationStage(action(
                        configured,
                        EmailAddressSanitizationStage.ID,
                        PrivacyClassification.PERSONAL)),
                new NetworkAddressSanitizationStage(action(
                        configured,
                        NetworkAddressSanitizationStage.ID,
                        PrivacyClassification.PERSONAL)),
                new ServerAddressSanitizationStage(action(
                        configured,
                        ServerAddressSanitizationStage.ID,
                        PrivacyClassification.PERSONAL)),
                new HomeDirectoryMaskingStage(
                        homeDirectory,
                        sensitivity,
                        action(
                                configured,
                                HomeDirectoryMaskingStage.ID,
                                PrivacyClassification.PERSONAL)),
                new UsernameMaskingStage(
                        username,
                        sensitivity,
                        action(
                                configured,
                                UsernameMaskingStage.ID,
                                PrivacyClassification.PERSONAL))));
    }

    public static BinarySanitizationAssessment assessBinary(
            String artifactName, PrivacyClassification declaredClassification) {
        PrivacyClassification effective = PrivacyClassification.mostRestrictive(
                PrivacyClassification.SENSITIVE,
                Objects.requireNonNull(declaredClassification, "declaredClassification"));
        return new BinarySanitizationAssessment(
                artifactName,
                effective,
                SanitizationAction.UNRESOLVED_WARNING,
                true);
    }

    private static SanitizationAction action(
            SanitizationPolicy policy,
            SanitizationStageId stageId,
            PrivacyClassification classification) {
        return policy.actionFor(stageId, classification);
    }
}
