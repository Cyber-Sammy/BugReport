package com.cybersammy.bugreport.core.sanitization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class SanitizationPipelineTest {
    private static final String ARTIFACT = "source-" + "a".repeat(64) + ".data";

    @Test
    void runsStagesInCanonicalOrderAndPreservesOriginalLocations() {
        TextSanitizationStage later = stage("later", 20, line -> {
            int start = line.indexOf("token");
            return start < 0
                    ? List.of()
                    : List.of(SanitizationMatch.warn(
                            start, start + 5, PrivacyClassification.SENSITIVE));
        });
        TextSanitizationStage earlier = stage("earlier", 10, line -> {
            int start = line.indexOf("private-user");
            return start < 0
                    ? List.of()
                    : List.of(SanitizationMatch.redact(
                            start,
                            start + "private-user".length(),
                            PrivacyClassification.PERSONAL,
                            "<user>"));
        });
        SanitizationPipeline pipeline = new SanitizationPipeline(List.of(later, earlier));
        StringWriter output = new StringWriter();

        SanitizationResult result = pipeline.sanitize(
                ARTIFACT,
                new StringReader("private-user token\r\nnext\n"),
                output,
                CancellationSignal.neverCancelled());

        assertEquals(
                List.of(new SanitizationStageId("earlier"), new SanitizationStageId("later")),
                pipeline.stageOrder());
        assertEquals("<user> token\r\nnext\n", output.toString());
        assertEquals(2, result.findings().size());
        assertEquals(1, result.findings().get(0).startColumn());
        assertEquals(13, result.findings().get(0).endColumn());
        assertEquals(14, result.findings().get(1).startColumn());
        assertEquals(19, result.findings().get(1).endColumn());
        assertTrue(result.hasUnresolvedWarnings());
        assertEquals(25, result.inputCharacters());
        assertEquals(19, result.outputCharacters());
    }

    @Test
    void ordersEqualPriorityByIdAndRejectsDuplicateIds() {
        TextSanitizationStage beta = stage("beta", 10, line -> List.of());
        TextSanitizationStage alpha = stage("alpha", 10, line -> List.of());

        SanitizationPipeline pipeline = new SanitizationPipeline(List.of(beta, alpha));

        assertEquals(
                List.of(new SanitizationStageId("alpha"), new SanitizationStageId("beta")),
                pipeline.stageOrder());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SanitizationPipeline(List.of(alpha, alpha)));
    }

    @Test
    void snapshotsStageIdentityBeforeExecution() {
        AtomicInteger identityReads = new AtomicInteger();
        TextSanitizationStage unstable = new TextSanitizationStage() {
            @Override
            public SanitizationStageId id() {
                return new SanitizationStageId(
                        identityReads.getAndIncrement() == 0 ? "stable" : "changed");
            }

            @Override
            public int order() {
                return 1;
            }

            @Override
            public List<SanitizationMatch> findMatches(String line) {
                throw new IllegalStateException("failure");
            }
        };
        SanitizationPipeline pipeline = new SanitizationPipeline(List.of(unstable));

        SanitizationException failure = assertThrows(
                SanitizationException.class,
                () -> pipeline.sanitize(
                        ARTIFACT,
                        new StringReader("value"),
                        new StringWriter(),
                        CancellationSignal.neverCancelled()));

        assertEquals(List.of(new SanitizationStageId("stable")), pipeline.stageOrder());
        assertEquals(new SanitizationStageId("stable"), failure.stageId().orElseThrow());
        assertEquals(1, identityReads.get());
    }

    @Test
    void stageFailureIsTerminalBeforeLaterSecretsCanBypassRedaction() {
        String secret = "bearer-super-secret";
        TextSanitizationStage credentials = stage("credentials", 1, line -> {
            if (line.equals("break")) {
                throw new IOException("stage accidentally included " + secret);
            }
            return line.equals(secret)
                    ? List.of(SanitizationMatch.redact(
                            0,
                            line.length(),
                            PrivacyClassification.PROHIBITED,
                            "<redacted>"))
                    : List.of();
        });
        StringWriter output = new StringWriter();

        SanitizationException failure = assertThrows(
                SanitizationException.class,
                () -> new SanitizationPipeline(List.of(credentials)).sanitize(
                        ARTIFACT,
                        new StringReader(secret + "\nbreak\n" + secret),
                        output,
                        CancellationSignal.neverCancelled()));

        assertEquals(SanitizationCode.STAGE_FAILED, failure.code());
        assertEquals(new SanitizationStageId("credentials"), failure.stageId().orElseThrow());
        assertEquals(2, failure.line().orElseThrow());
        assertEquals("<redacted>\n", output.toString());
        assertFalse(failure.getMessage().contains(secret));
        assertFalse(failure.toString().contains(secret));
    }

    @Test
    void appliesMultipleEditsAgainstOneStageValue() {
        TextSanitizationStage stage = stage("emails", 1, line -> List.of(
                SanitizationMatch.redact(0, 3, PrivacyClassification.PERSONAL, "X"),
                SanitizationMatch.redact(4, 7, PrivacyClassification.PERSONAL, "Y")));
        StringWriter output = new StringWriter();

        SanitizationResult result = new SanitizationPipeline(List.of(stage)).sanitize(
                ARTIFACT,
                new StringReader("abc def"),
                output,
                CancellationSignal.neverCancelled());

        assertEquals("X Y", output.toString());
        assertEquals(List.of(1, 5), result.findings().stream()
                .map(SanitizationFinding::startColumn)
                .toList());
    }

    @Test
    void linkageNullAndInvalidResultsFailClosed() {
        List<TextSanitizationStage> failingStages = List.of(
                stage("linkage", 1, line -> {
                    throw new NoClassDefFoundError("missing optional dependency");
                }),
                stage("null_result", 1, line -> null),
                stage("overlap", 1, line -> List.of(
                        SanitizationMatch.warn(0, 2, PrivacyClassification.PERSONAL),
                        SanitizationMatch.warn(1, 3, PrivacyClassification.PERSONAL))),
                stage("out_of_bounds", 1, line -> List.of(
                        SanitizationMatch.warn(0, 4, PrivacyClassification.PERSONAL))),
                stage("null_match", 1, line ->
                        java.util.Arrays.asList((SanitizationMatch) null)));

        for (TextSanitizationStage invalid : failingStages) {
            SanitizationException failure = assertThrows(
                    SanitizationException.class,
                    () -> new SanitizationPipeline(List.of(invalid)).sanitize(
                            ARTIFACT,
                            new StringReader("abc"),
                            new StringWriter(),
                            CancellationSignal.neverCancelled()));
            assertEquals(SanitizationCode.STAGE_FAILED, failure.code());
            assertEquals(invalid.id(), failure.stageId().orElseThrow());
            assertEquals(1, failure.line().orElseThrow());
        }
    }

    @Test
    void enforcesLineAndTotalLimitsWithoutPublishingSuccess() {
        SanitizationPipeline lineLimited = new SanitizationPipeline(List.of(), 3, 20, 20);
        SanitizationPipeline totalLimited = new SanitizationPipeline(List.of(), 20, 4, 20);

        SanitizationException lineFailure = assertThrows(
                SanitizationException.class,
                () -> lineLimited.sanitize(
                        ARTIFACT,
                        new StringReader("four"),
                        new StringWriter(),
                        CancellationSignal.neverCancelled()));
        SanitizationException totalFailure = assertThrows(
                SanitizationException.class,
                () -> totalLimited.sanitize(
                        ARTIFACT,
                        new StringReader("a\r\nbb"),
                        new StringWriter(),
                        CancellationSignal.neverCancelled()));

        assertEquals(SanitizationCode.LINE_LIMIT_EXCEEDED, lineFailure.code());
        assertEquals(SanitizationCode.INPUT_LIMIT_EXCEEDED, totalFailure.code());
    }

    @Test
    void rejectsStageExpansionAndBoundsTotalOutput() {
        TextSanitizationStage expansion = stage("expansion", 1, line -> List.of(
                SanitizationMatch.redact(
                        0, 1, PrivacyClassification.PERSONAL, "expanded")));
        SanitizationException expansionFailure = assertThrows(
                SanitizationException.class,
                () -> new SanitizationPipeline(List.of(expansion), 3, 20, 20).sanitize(
                        ARTIFACT,
                        new StringReader("a"),
                        new StringWriter(),
                        CancellationSignal.neverCancelled()));
        SanitizationException outputFailure = assertThrows(
                SanitizationException.class,
                () -> new SanitizationPipeline(List.of(), 20, 20, 3).sanitize(
                        ARTIFACT,
                        new StringReader("four"),
                        new StringWriter(),
                        CancellationSignal.neverCancelled()));
        assertEquals(SanitizationCode.STAGE_FAILED, expansionFailure.code());
        assertEquals(SanitizationCode.OUTPUT_LIMIT_EXCEEDED, outputFailure.code());
    }

    @Test
    void cancellationAndIoFailureAreTypedAndPathFree() {
        AtomicBoolean cancelled = new AtomicBoolean();
        Reader cancellingReader = new StringReader("first\nsecond") {
            @Override
            public int read() throws IOException {
                int value = super.read();
                if (value == '\n') {
                    cancelled.set(true);
                }
                return value;
            }
        };
        SanitizationException cancellation = assertThrows(
                SanitizationException.class,
                () -> new SanitizationPipeline(List.of()).sanitize(
                        ARTIFACT, cancellingReader, new StringWriter(), cancelled::get));
        SanitizationException ioFailure = assertThrows(
                SanitizationException.class,
                () -> new SanitizationPipeline(List.of()).sanitize(
                        ARTIFACT,
                        new Reader() {
                            @Override
                            public int read(char[] buffer, int offset, int length)
                                    throws IOException {
                                throw new IOException("secret filesystem path");
                            }

                            @Override
                            public void close() {}
                        },
                        new StringWriter(),
                        CancellationSignal.neverCancelled()));

        assertEquals(SanitizationCode.CANCELLED, cancellation.code());
        assertEquals(SanitizationCode.IO_FAILURE, ioFailure.code());
        assertFalse(ioFailure.getMessage().contains("secret filesystem path"));
    }

    @Test
    void validatesArtifactNamesAndPipelineLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SanitizationPipeline(List.of()).sanitize(
                        "../secret",
                        new StringReader("value"),
                        new StringWriter(),
                        CancellationSignal.neverCancelled()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SanitizationPipeline(List.of(), 0, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SanitizationPipeline(
                        List.of(),
                        SanitizationPipeline.PRODUCT_MAX_LINE_CHARACTERS,
                        SanitizationPipeline.PRODUCT_MAX_INPUT_CHARACTERS + 1,
                        SanitizationPipeline.PRODUCT_MAX_OUTPUT_CHARACTERS));
    }

    private static TextSanitizationStage stage(
            String id, int order, MatchFinder finder) {
        return new TextSanitizationStage() {
            @Override
            public SanitizationStageId id() {
                return new SanitizationStageId(id);
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public List<SanitizationMatch> findMatches(String line) throws Exception {
                return finder.find(line);
            }
        };
    }

    @FunctionalInterface
    private interface MatchFinder {
        List<SanitizationMatch> find(String line) throws Exception;
    }
}
