package com.cybersammy.bugreport.neoforge.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import java.util.List;
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
}
