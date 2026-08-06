package com.cybersammy.bugreport.core.sanitization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.specification.CancellationSignal;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

final class IdentityMaskingStageTest {
    private static final String ARTIFACT = "source-" + "b".repeat(64) + ".data";

    @Test
    void masksWindowsHomeAcrossSeparatorsEscapingAndCase() {
        HomeDirectoryMaskingStage stage = new HomeDirectoryMaskingStage(
                "C:\\Users\\Alice\\",
                SanitizationCaseSensitivity.INSENSITIVE);

        String output = sanitize(
                List.of(stage),
                "C:\\Users\\Alice\\.minecraft "
                        + "c:/users/alice/logs "
                        + "C:\\\\Users\\\\Alice\\\\config");

        assertEquals(
                "<home>\\.minecraft <home>/logs <home>\\\\config",
                output);
    }

    @Test
    void masksPosixHomeCaseSensitivelyAndSupportsUncHome() {
        HomeDirectoryMaskingStage posix = new HomeDirectoryMaskingStage(
                "/home/Жанна", SanitizationCaseSensitivity.SENSITIVE);
        HomeDirectoryMaskingStage unc = new HomeDirectoryMaskingStage(
                "\\\\server\\profiles\\Alice",
                SanitizationCaseSensitivity.INSENSITIVE);

        assertEquals(
                "<home>/logs /home/жанна/logs",
                sanitize(List.of(posix), "/home/Жанна/logs /home/жанна/logs"));
        assertEquals(
                "<home>\\save",
                sanitize(List.of(unc), "\\\\SERVER\\profiles\\alice\\save"));
    }

    @Test
    void doesNotMaskHomeAsSuffixOrPrefixOfAnotherPathSegment() {
        HomeDirectoryMaskingStage stage = new HomeDirectoryMaskingStage(
                "/home/alice", SanitizationCaseSensitivity.SENSITIVE);

        String output = sanitize(
                List.of(stage),
                "/opt/home/alice /home/alice2 x/home/alice /home/alice-file");

        assertEquals(
                "/opt/home/alice /home/alice2 x/home/alice /home/alice-file",
                output);
    }

    @Test
    void masksUsernameOnlyAtUnicodeTokenBoundaries() {
        UsernameMaskingStage stage = new UsernameMaskingStage(
                "Alice", SanitizationCaseSensitivity.INSENSITIVE);

        String output = sanitize(
                List.of(stage),
                "Alice ALICE alice@example.test malice alice2 alice-name _alice");

        assertEquals(
                "<user> <user> <user>@example.test malice alice2 <user>-name _alice",
                output);
    }

    @Test
    void handlesUnicodeUsernameWithExplicitCasePolicy() {
        UsernameMaskingStage insensitive = new UsernameMaskingStage(
                "Жанна", SanitizationCaseSensitivity.INSENSITIVE);
        UsernameMaskingStage sensitive = new UsernameMaskingStage(
                "Жанна", SanitizationCaseSensitivity.SENSITIVE);

        assertEquals("<user> <user>", sanitize(List.of(insensitive), "Жанна жанна"));
        assertEquals("<user> жанна", sanitize(List.of(sensitive), "Жанна жанна"));
    }

    @Test
    void homeRunsBeforeUsernameAndPreventsDuplicateFindingInsidePath() {
        HomeDirectoryMaskingStage home = new HomeDirectoryMaskingStage(
                "/home/alice", SanitizationCaseSensitivity.SENSITIVE);
        UsernameMaskingStage username = new UsernameMaskingStage(
                "alice", SanitizationCaseSensitivity.SENSITIVE);
        SanitizationPipeline pipeline = new SanitizationPipeline(List.of(username, home));
        StringWriter output = new StringWriter();

        SanitizationResult result = pipeline.sanitize(
                ARTIFACT,
                new StringReader("/home/alice/logs owner=alice"),
                output,
                CancellationSignal.neverCancelled());

        assertEquals(
                List.of(HomeDirectoryMaskingStage.ID, UsernameMaskingStage.ID),
                pipeline.stageOrder());
        assertEquals("<home>/logs owner=<user>", output.toString());
        assertEquals(2, result.findings().size());
        assertEquals(HomeDirectoryMaskingStage.ID, result.findings().get(0).stageId());
        assertEquals(UsernameMaskingStage.ID, result.findings().get(1).stageId());
        assertEquals(24, result.findings().get(1).startColumn());
        assertEquals(29, result.findings().get(1).endColumn());
    }

    @Test
    void laterStageCannotRemaskAnEarlierSafeReplacement() {
        HomeDirectoryMaskingStage home = new HomeDirectoryMaskingStage(
                "/home/home", SanitizationCaseSensitivity.SENSITIVE);
        UsernameMaskingStage username = new UsernameMaskingStage(
                "home", SanitizationCaseSensitivity.SENSITIVE);
        SanitizationPipeline pipeline = new SanitizationPipeline(List.of(username, home));
        StringWriter output = new StringWriter();

        SanitizationResult result = pipeline.sanitize(
                ARTIFACT,
                new StringReader("/home/home/logs owner=home"),
                output,
                CancellationSignal.neverCancelled());

        assertEquals("<home>/logs owner=<user>", output.toString());
        assertEquals(2, result.findings().size());
    }

    @Test
    void rejectsUnsafeHomeAndAmbiguousShortUsername() {
        for (String invalid : List.of(
                "relative/home",
                "/",
                "C:\\",
                "/home/../secret",
                " /home/alice",
                "/home/alice ",
                "/home/alice\nsecret")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new HomeDirectoryMaskingStage(
                            invalid, SanitizationCaseSensitivity.SENSITIVE));
        }
        for (String invalid : List.of(
                "a", "xy", "a/b", " alice", "alice ", "alice\nsecret")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new UsernameMaskingStage(
                            invalid, SanitizationCaseSensitivity.SENSITIVE));
        }
    }

    @Test
    void stageStringRepresentationsDoNotExposeConfiguredIdentity() {
        String home = "/private/home/unique-person";
        String username = "unique-person";
        HomeDirectoryMaskingStage homeStage = new HomeDirectoryMaskingStage(
                home, SanitizationCaseSensitivity.SENSITIVE);
        UsernameMaskingStage usernameStage = new UsernameMaskingStage(
                username, SanitizationCaseSensitivity.SENSITIVE);

        assertFalse(homeStage.toString().contains(home));
        assertFalse(usernameStage.toString().contains(username));
    }

    private static String sanitize(
            List<? extends TextSanitizationStage> stages, String input) {
        StringWriter output = new StringWriter();
        new SanitizationPipeline(stages).sanitize(
                ARTIFACT,
                new StringReader(input),
                output,
                CancellationSignal.neverCancelled());
        return output.toString();
    }
}
