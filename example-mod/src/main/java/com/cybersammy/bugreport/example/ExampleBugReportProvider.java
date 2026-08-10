package com.cybersammy.bugreport.example;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.extension.ExtensionValue;
import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DestinationId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.ExtensionMetadataKey;
import com.cybersammy.bugreport.api.identifier.GeneratedArtifactId;
import com.cybersammy.bugreport.api.identifier.NamespaceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.identifier.TransportId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CapabilityOffer;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticGeneratorSpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.specification.GeneratedDiagnosticRequest;
import com.cybersammy.bugreport.api.specification.GeneratedDiagnosticSink;
import com.cybersammy.bugreport.api.specification.GeneratorExecutionContext;
import com.cybersammy.bugreport.api.specification.HttpsUrl;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.specification.ReportQualityRole;
import com.cybersammy.bugreport.api.specification.StandardFields;
import com.cybersammy.bugreport.api.specification.SupportDestinationSpecification;
import com.cybersammy.bugreport.api.specification.SupportDestinationTarget;
import com.cybersammy.bugreport.api.specification.SupportDestinationType;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.time.Duration;
import java.util.Optional;

public final class ExampleBugReportProvider implements BugReportProvider {
    private static final ProviderId PROVIDER_ID =
            ProviderId.defaultProvider(NamespaceId.of(ExampleMod.MOD_ID));
    private static final DiagnosticSourceId LATEST_LOG_ID = DiagnosticSourceId.of("latest_log");
    private static final DiagnosticSourceId SCREENSHOT_ID = DiagnosticSourceId.of("screenshot");
    private static final DiagnosticGeneratorId ENVIRONMENT_ID =
            DiagnosticGeneratorId.of("environment");
    private static final DestinationId LOCAL_ARCHIVE_ID =
            DestinationId.of("bugreport_example:local_archive");
    private static final ProviderSpecification SPECIFICATION = createSpecification();

    public ExampleBugReportProvider() {}

    @Override
    public String providerId() {
        return ExampleMod.MOD_ID;
    }

    @Override
    public String providerVersion() {
        return "1.0.0";
    }

    @Override
    public Optional<ProviderSpecification> specification() {
        return Optional.of(SPECIFICATION);
    }

    private static ProviderSpecification createSpecification() {
        DiagnosticSourceSpecification latestLog =
                DiagnosticSourceSpecification.latestLog(LATEST_LOG_ID)
                        .labelKey(key("source.latest_log"))
                        .descriptionKey(key("source.latest_log.description"))
                        .privacy(PrivacyClassification.PERSONAL)
                        .contentType(DiagnosticContentType.TEXT)
                        .qualityRole(ReportQualityRole.RECOMMENDED)
                        .supportSide(SupportedSide.PHYSICAL_CLIENT)
                        .constraints(
                                CollectionConstraints.builder()
                                        .maxMatchedFiles(1)
                                        .maxBytesPerFile(2_000_000)
                                        .maxTotalBytes(2_000_000)
                                        .build())
                        .build();

        DiagnosticGeneratorSpecification environment =
                DiagnosticGeneratorSpecification.builder(
                                ENVIRONMENT_ID, ExampleBugReportProvider::generateEnvironment)
                        .labelKey(key("generator.environment"))
                        .privacy(PrivacyClassification.LOW)
                        .contentType(DiagnosticContentType.JSON)
                        .qualityRole(ReportQualityRole.RECOMMENDED)
                        .supportSide(SupportedSide.PHYSICAL_CLIENT)
                        .supportSide(SupportedSide.DEDICATED_SERVER)
                        .executionContext(GeneratorExecutionContext.WORKER)
                        .constraints(
                                CollectionConstraints.builder()
                                        .maxBytesPerFile(8_192)
                                        .maxTotalBytes(8_192)
                                        .maxGeneratedArtifacts(1)
                                        .callbackTimeout(Duration.ofSeconds(2))
                                        .build())
                        .build();

        DiagnosticSourceSpecification screenshot =
                DiagnosticSourceSpecification.userSelectedScreenshot(SCREENSHOT_ID)
                        .labelKey(key("source.screenshot"))
                        .descriptionKey(key("source.screenshot.description"))
                        .privacy(PrivacyClassification.SENSITIVE)
                        .contentType(DiagnosticContentType.BINARY)
                        .qualityRole(ReportQualityRole.OPTIONAL)
                        .supportSide(SupportedSide.PHYSICAL_CLIENT)
                        .build();

        SupportDestinationSpecification localArchive =
                SupportDestinationSpecification.builder(
                                LOCAL_ARCHIVE_ID,
                                SupportDestinationType.LOCAL_ARCHIVE,
                                TransportId.of("bugreport:local_zip"),
                                SupportDestinationTarget.localArchive())
                        .labelKey(key("destination.local_archive"))
                        .build();

        CategorySpecification general =
                CategorySpecification.builder(CategoryId.of("general"), key("category.general"))
                        .descriptionKey(key("category.general.description"))
                        .addField(StandardFields.summary())
                        .addField(StandardFields.reproductionSteps())
                        .useSource(LATEST_LOG_ID)
                        .useSource(SCREENSHOT_ID)
                        .useGenerator(ENVIRONMENT_ID)
                        .useDestination(LOCAL_ARCHIVE_ID)
                        .build();

        return ProviderSpecification.builder(
                        PROVIDER_ID, ProviderVersion.parse("1.0.0"), key("provider.name"))
                .descriptionKey(key("provider.description"))
                .privacyNoticeKey(key("provider.privacy_notice"))
                .documentationUrl(HttpsUrl.of("https://github.com/Cyber-Sammy/BugReport"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .supportSide(SupportedSide.DEDICATED_SERVER)
                .addSource(latestLog)
                .addSource(screenshot)
                .addGenerator(environment)
                .addDestination(localArchive)
                .offerCapability(
                        new CapabilityOffer(
                                CapabilityId.of("bugreport_example:environment_json"),
                                new CapabilityVersion(1, 0)))
                .addCategory(general)
                .build();
    }

    private static void generateEnvironment(
            GeneratedDiagnosticRequest request, GeneratedDiagnosticSink sink) {
        if (request.cancellation().isCancellationRequested()) {
            return;
        }
        ExtensionMetadata content = ExtensionMetadata.builder()
                .put(
                        ExtensionMetadataKey.of("bugreport_example:physical_side"),
                        ExtensionValue.of(request.side().name()))
                .build();
        sink.emitJson(GeneratedArtifactId.of("environment"), content);
    }

    private static LocalizationKey key(String suffix) {
        return LocalizationKey.of("bugreport_example." + suffix);
    }
}
