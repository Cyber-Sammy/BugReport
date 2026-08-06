package com.cybersammy.bugreport.core.sanitization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ProductSanitizationTest {
    private static final String ARTIFACT = "latest-" + "c".repeat(64) + ".log";

    @Test
    void completeConfigurationPolicyRedactsSupportedSensitivePatterns() {
        SanitizationPipeline pipeline = ProductSanitization.textPipeline(
                SanitizationPolicy.standard(SanitizationArtifactPolicy.CONFIGURATION),
                "C:\\Users\\Alice",
                "Alice",
                SanitizationCaseSensitivity.INSENSITIVE);
        String input = String.join(
                "\n",
                "email=Alice@example.test",
                "local=192.168.1.42:25565 remote=[2001:db8::7]:24454",
                "serverAddress=play.example.test:25565",
                "Authorization: Bearer abcdefghijklmnop",
                "api_key=key_1234567890",
                "sessionId=123e4567-e89b-12d3-a456-426614174000",
                "accessToken=mc_access_token_123456",
                "webhook=https://discord.com/api/webhooks/123456789/token_value_123",
                "path=C:/Users/Alice/.minecraft owner=Alice");

        Sanitized sanitized = sanitize(pipeline, input);

        assertEquals(
                String.join(
                        "\n",
                        "email=<email>",
                        "local=<network-address> remote=<network-address>",
                        "serverAddress=<server-address>",
                        "Authorization: <bearer-token>",
                        "api_key=<api-key>",
                        "sessionId=<session-id>",
                        "accessToken=<minecraft-auth>",
                        "webhook=<webhook>",
                        "path=<home>/.minecraft owner=<user>"),
                sanitized.output());
        assertEquals(11, sanitized.result().findings().size());
        assertFalse(sanitized.result().hasUnresolvedWarnings());
        assertFalse(sanitized.result().toString().contains("token_value_123"));
        assertFalse(sanitized.result().toString().contains("Alice@example.test"));
    }

    @Test
    void networkDetectorCoversIpv4Ipv6PortsAndInvalidPortFallback() {
        NetworkAddressSanitizationStage stage = new NetworkAddressSanitizationStage(
                SanitizationAction.AUTOMATIC_REDACTION);

        assertEquals(
                "<network-address> <network-address> <network-address>:70000 "
                        + "<network-address>:70000",
                sanitize(
                                new SanitizationPipeline(List.of(stage)),
                                "10.0.0.1:25565 2001:db8::1 10.0.0.2:70000 "
                                        + "[2001:db8::2]:70000")
                        .output());
    }

    @Test
    void supportedPatternFixturesRemainDetected() {
        SanitizationPipeline pipeline = ProductSanitization.textPipeline(
                SanitizationPolicy.strictPrivacy(SanitizationArtifactPolicy.CONFIGURATION),
                "/home/alice",
                "alice",
                SanitizationCaseSensitivity.SENSITIVE);
        List<Fixture> fixtures = List.of(
                new Fixture(
                        "\"clientToken\":\"abcdefghi12345\"",
                        "\"clientToken\":\"<minecraft-auth>\"",
                        MinecraftAuthenticationSanitizationStage.ID),
                new Fixture(
                        "https://hooks.slack.com/services/T00000000/B00000000/abcdefghijkl",
                        "<webhook>",
                        WebhookSanitizationStage.ID),
                new Fixture(
                        "authorization=bearer eyJhbGciOiJIUzI1NiJ9.payload.signature",
                        "authorization=<bearer-token>",
                        BearerTokenSanitizationStage.ID),
                new Fixture(
                        "x-api-key: sk-live-123456789",
                        "x-api-key: <api-key>",
                        ApiKeySanitizationStage.ID),
                new Fixture(
                        "session_token=abcdefghijklmnop",
                        "session_token=<session-id>",
                        SessionIdentifierSanitizationStage.ID),
                new Fixture(
                        "contact=ж@example.test",
                        "contact=<email>",
                        EmailAddressSanitizationStage.ID),
                new Fixture(
                        "peer=::ffff:192.0.2.128",
                        "peer=<network-address>",
                        NetworkAddressSanitizationStage.ID),
                new Fixture(
                        "server-ip=modded.example.test:25565",
                        "server-ip=<server-address>",
                        ServerAddressSanitizationStage.ID));

        for (Fixture fixture : fixtures) {
            Sanitized sanitized = sanitize(pipeline, fixture.input());
            assertEquals(fixture.expected(), sanitized.output(), fixture.stageId().toString());
            assertEquals(1, sanitized.result().findings().size());
            assertEquals(fixture.stageId(), sanitized.result().findings().getFirst().stageId());
        }
    }

    @Test
    void standardLogLeavesAmbiguousNetworkLocationsForExplicitReview() {
        SanitizationPipeline pipeline = ProductSanitization.textPipeline(
                SanitizationPolicy.standard(SanitizationArtifactPolicy.LOG),
                "/home/alice",
                "alice",
                SanitizationCaseSensitivity.SENSITIVE);

        Sanitized sanitized = sanitize(
                pipeline,
                "connected=203.0.113.8 server=play.example.test:25565 "
                        + "email=alice@example.test");

        assertEquals(
                "connected=203.0.113.8 server=play.example.test:25565 email=<email>",
                sanitized.output());
        assertTrue(sanitized.result().hasUnresolvedWarnings());
        assertEquals(
                List.of(
                        SanitizationAction.AUTOMATIC_REDACTION,
                        SanitizationAction.UNRESOLVED_WARNING,
                        SanitizationAction.UNRESOLVED_WARNING),
                sanitized.result().findings().stream()
                        .map(SanitizationFinding::action)
                        .toList());
    }

    @Test
    void strictPrivacyRedactsNetworkLocationsInLogs() {
        SanitizationPipeline pipeline = ProductSanitization.textPipeline(
                SanitizationPolicy.strictPrivacy(SanitizationArtifactPolicy.LOG),
                "/home/alice",
                "alice",
                SanitizationCaseSensitivity.SENSITIVE);

        assertEquals(
                "connected=<network-address> server=<server-address>",
                sanitize(
                                pipeline,
                                "connected=203.0.113.8 server=play.example.test:25565")
                        .output());
    }

    @Test
    void invalidHostnamePortsDoNotExposeTheHostname() {
        SanitizationPipeline strictLog = ProductSanitization.textPipeline(
                SanitizationPolicy.strictPrivacy(SanitizationArtifactPolicy.LOG),
                "/home/alice",
                "alice",
                SanitizationCaseSensitivity.SENSITIVE);
        SanitizationPipeline standardConfiguration = ProductSanitization.textPipeline(
                SanitizationPolicy.standard(SanitizationArtifactPolicy.CONFIGURATION),
                "/home/alice",
                "alice",
                SanitizationCaseSensitivity.SENSITIVE);
        String input = "serverAddress=private.example.test:70000 "
                + "hostname=localhost:0 address=family.example.net:99999";
        String expected = "serverAddress=<server-address>:70000 "
                + "hostname=<server-address>:0 address=<server-address>:99999";

        assertEquals(expected, sanitize(strictLog, input).output());
        assertEquals(expected, sanitize(standardConfiguration, input).output());
    }

    @Test
    void customReviewCannotWeakenProhibitedCredentialHandling() {
        SanitizationPolicy policy = SanitizationPolicy.customReview(
                SanitizationArtifactPolicy.LOG,
                Map.of(
                        EmailAddressSanitizationStage.ID,
                        SanitizationAction.AUTOMATIC_REDACTION,
                        ApiKeySanitizationStage.ID,
                        SanitizationAction.UNRESOLVED_WARNING));
        SanitizationPipeline pipeline = ProductSanitization.textPipeline(
                policy,
                "/home/alice",
                "alice",
                SanitizationCaseSensitivity.SENSITIVE);

        Sanitized sanitized = sanitize(
                pipeline,
                "alice@example.test api_key=abcdefgh1234 owner=alice");

        assertEquals("<email> api_key=<api-key> owner=alice", sanitized.output());
        assertTrue(sanitized.result().hasUnresolvedWarnings());
        assertEquals(
                SanitizationAction.AUTOMATIC_REDACTION,
                policy.actionFor(
                        ApiKeySanitizationStage.ID,
                        PrivacyClassification.PROHIBITED));
    }

    @Test
    void rejectsUnsupportedLookalikesAndUnlabelledSecrets() {
        SanitizationPipeline pipeline = ProductSanitization.textPipeline(
                SanitizationPolicy.strictPrivacy(SanitizationArtifactPolicy.LOG),
                "/home/alice",
                "alice",
                SanitizationCaseSensitivity.SENSITIVE);
        String input = String.join(
                " ",
                "version=1.2.3.4",
                "invalid=999.1.1.1",
                "leading=010.0.0.1",
                "time=12:34:56",
                "mail=a@localhost",
                "domain=docs.example.test",
                "Bearer short",
                "api_key=short",
                "accessToken=tiny",
                "session=short",
                "https://discord.com/channels/123/456",
                "123e4567-e89b-12d3-a456-426614174000");

        Sanitized sanitized = sanitize(pipeline, input);

        assertEquals(input, sanitized.output());
        assertTrue(sanitized.result().findings().isEmpty());
    }

    @Test
    void emailStageOwnsCompleteAddressBeforeUsernameStage() {
        SanitizationPipeline pipeline = ProductSanitization.textPipeline(
                SanitizationPolicy.standard(SanitizationArtifactPolicy.LOG),
                "/home/alice",
                "alice",
                SanitizationCaseSensitivity.SENSITIVE);

        Sanitized sanitized = sanitize(pipeline, "alice@example.test alice");

        assertEquals("<email> <user>", sanitized.output());
        assertEquals(
                EmailAddressSanitizationStage.ID,
                sanitized.result().findings().get(0).stageId());
        assertEquals(UsernameMaskingStage.ID, sanitized.result().findings().get(1).stageId());
    }

    @Test
    void binaryContentIsSensitiveAndExcludedPendingReview() {
        BinarySanitizationAssessment personal = ProductSanitization.assessBinary(
                "screenshot.png", PrivacyClassification.PERSONAL);
        BinarySanitizationAssessment prohibited = ProductSanitization.assessBinary(
                "opaque.bin", PrivacyClassification.PROHIBITED);

        assertEquals(PrivacyClassification.SENSITIVE, personal.classification());
        assertEquals(PrivacyClassification.PROHIBITED, prohibited.classification());
        assertEquals(SanitizationAction.UNRESOLVED_WARNING, personal.action());
        assertTrue(personal.excludedPendingReview());
        assertThrows(
                IllegalArgumentException.class,
                () -> new BinarySanitizationAssessment(
                        "unsafe.bin",
                        PrivacyClassification.LOW,
                        SanitizationAction.AUTOMATIC_REDACTION,
                        false));
    }

    @Test
    void policyConstructionRejectsInconsistentCustomConfiguration() {
        Map<SanitizationStageId, SanitizationAction> invalid = new HashMap<>();
        invalid.put(EmailAddressSanitizationStage.ID, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> SanitizationPolicy.customReview(
                        SanitizationArtifactPolicy.LOG,
                        invalid));
    }

    private static Sanitized sanitize(SanitizationPipeline pipeline, String input) {
        StringWriter output = new StringWriter();
        SanitizationResult result = pipeline.sanitize(
                ARTIFACT,
                new StringReader(input),
                output,
                CancellationSignal.neverCancelled());
        return new Sanitized(output.toString(), result);
    }

    private record Sanitized(String output, SanitizationResult result) {}

    private record Fixture(
            String input, String expected, SanitizationStageId stageId) {}
}
