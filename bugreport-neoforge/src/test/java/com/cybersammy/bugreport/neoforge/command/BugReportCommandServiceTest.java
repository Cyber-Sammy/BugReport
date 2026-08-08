package com.cybersammy.bugreport.neoforge.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.NamespaceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import com.cybersammy.bugreport.core.registry.ProviderRegistry;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import java.util.List;
import java.util.Optional;
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
    void categoryErrorsKeepMalformedAndUndeclaredInputsDistinct() {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        assertEquals("bugreport.command.error.invalid_category",
                service.create("example_mod", "not a category").getFirst().translationKey());
        BugReportCommandService.Message unknown = service.create("example_mod", "other").getFirst();
        assertEquals("bugreport.command.error.unknown_category", unknown.translationKey());
        assertEquals("other", unknown.arguments()[0]);
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
                .addCategory(CategorySpecification.builder(
                        CategoryId.of("general"), LocalizationKey.of("example_mod.category.general")).build())
                .build();

        @Override
        public String providerId() { return "example_mod"; }

        @Override
        public String providerVersion() { return "1.0.0"; }

        @Override
        public Optional<ProviderSpecification> specification() { return Optional.of(SPECIFICATION); }
    }
}
