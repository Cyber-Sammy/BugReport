package com.cybersammy.bugreport.api.specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DestinationId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.identifier.TransportId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.version.ApiVersion;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProviderSpecificationTest {
    @Test
    void buildsValidatedGraphInCanonicalImmutableOrder() {
        DiagnosticSourceSpecification source = latestLog("latest_log");
        SupportDestinationSpecification destination = localArchive("example:local_archive");
        CategorySpecification category = CategorySpecification.builder(
                        CategoryId.of("general"), key("general"))
                .useSource(source.id())
                .useDestination(destination.id())
                .build();

        ProviderSpecification specification = baseBuilder()
                .addSource(source)
                .addDestination(destination)
                .addCategory(category)
                .build();

        assertEquals(ApiVersion.parse("1.0.0"), specification.version());
        assertEquals(List.of(CategoryId.of("general")), List.copyOf(specification.categories().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> specification.categories().clear());
        assertThrows(UnsupportedOperationException.class, () -> specification.supportedSides().clear());
    }

    @Test
    void rejectsUnknownCategoryReferences() {
        CategorySpecification category = CategorySpecification.builder(
                        CategoryId.of("general"), key("general"))
                .useSource(DiagnosticSourceId.of("missing"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> baseBuilder().addCategory(category).build());
    }

    @Test
    void rejectsChildSidesNotSupportedByProvider() {
        ProviderSpecification.Builder builder = ProviderSpecification.builder(
                        ProviderId.parse("example"), ApiVersion.parse("1.0.0"), key("provider"))
                .supportSide(SupportedSide.DEDICATED_SERVER)
                .addSource(latestLog("latest_log"))
                .addCategory(CategorySpecification.builder(CategoryId.of("general"), key("general"))
                        .build());

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void rejectsDestinationOwnedByAnotherNamespace() {
        assertThrows(
                IllegalArgumentException.class,
                () -> baseBuilder()
                        .addDestination(localArchive("other:local_archive"))
                        .addCategory(CategorySpecification.builder(
                                        CategoryId.of("general"), key("general"))
                                .build())
                        .build());
    }

    @Test
    void legacyProviderMayOmitDeclarativeSpecification() {
        BugReportProvider provider = () -> "example";

        assertEquals("unknown", provider.providerVersion());
        assertEquals(false, provider.specification().isPresent());
    }

    private static ProviderSpecification.Builder baseBuilder() {
        return ProviderSpecification.builder(
                        ProviderId.parse("example"), ApiVersion.parse("1.0.0"), key("provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT);
    }

    private static DiagnosticSourceSpecification latestLog(String id) {
        return DiagnosticSourceSpecification.latestLog(DiagnosticSourceId.of(id))
                .labelKey(key(id))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .build();
    }

    private static SupportDestinationSpecification localArchive(String id) {
        return SupportDestinationSpecification.builder(
                        DestinationId.of(id),
                        SupportDestinationType.LOCAL_ARCHIVE,
                        TransportId.of("bugreport:local_zip"),
                        SupportDestinationTarget.localArchive())
                .labelKey(key("local_archive"))
                .build();
    }

    private static LocalizationKey key(String suffix) {
        return LocalizationKey.of("example." + suffix);
    }
}
