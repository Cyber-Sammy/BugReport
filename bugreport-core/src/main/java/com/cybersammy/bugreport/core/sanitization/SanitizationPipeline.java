package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.specification.CancellationSignal;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic bounded streaming pipeline for product-owned text sanitizers. */
public final class SanitizationPipeline {
    public static final int PRODUCT_MAX_LINE_CHARACTERS = 1024 * 1024;
    public static final long PRODUCT_MAX_INPUT_CHARACTERS = 128L * 1024 * 1024;
    public static final long PRODUCT_MAX_OUTPUT_CHARACTERS = 128L * 1024 * 1024;
    public static final int PRODUCT_MAX_FINDINGS = 10_000;
    public static final int PRODUCT_MAX_STAGES = 128;
    private static final int MAXIMUM_MATCHES_PER_STAGE_LINE = 4_096;
    private static final int CANCELLATION_CHECK_INTERVAL = 4_096;

    private final List<ConfiguredStage> stages;
    private final int maximumLineCharacters;
    private final long maximumInputCharacters;
    private final long maximumOutputCharacters;

    public SanitizationPipeline(List<? extends TextSanitizationStage> stages) {
        this(
                stages,
                PRODUCT_MAX_LINE_CHARACTERS,
                PRODUCT_MAX_INPUT_CHARACTERS,
                PRODUCT_MAX_OUTPUT_CHARACTERS);
    }

    SanitizationPipeline(
            List<? extends TextSanitizationStage> stages,
            int maximumLineCharacters,
            long maximumInputCharacters,
            long maximumOutputCharacters) {
        if (maximumLineCharacters < 1
                || maximumLineCharacters > PRODUCT_MAX_LINE_CHARACTERS
                || maximumInputCharacters < 1
                || maximumInputCharacters > PRODUCT_MAX_INPUT_CHARACTERS
                || maximumOutputCharacters < 1
                || maximumOutputCharacters > PRODUCT_MAX_OUTPUT_CHARACTERS) {
            throw new IllegalArgumentException(
                    "Sanitization limits must be positive and within product ceilings");
        }
        List<TextSanitizationStage> configured = List.copyOf(
                Objects.requireNonNull(stages, "stages"));
        if (configured.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Sanitization stages must not contain null");
        }
        if (configured.size() > PRODUCT_MAX_STAGES) {
            throw new IllegalArgumentException("Sanitization stages exceeded the product limit");
        }
        Set<SanitizationStageId> identifiers = new HashSet<>();
        List<ConfiguredStage> descriptors = new ArrayList<>(configured.size());
        for (TextSanitizationStage stage : configured) {
            SanitizationStageId id = Objects.requireNonNull(stage.id(), "stage ID");
            if (!identifiers.add(id)) {
                throw new IllegalArgumentException("Sanitization stage IDs must be unique");
            }
            descriptors.add(new ConfiguredStage(id, stage.order(), stage));
        }
        this.stages = descriptors.stream()
                .sorted(Comparator.comparingInt(ConfiguredStage::order)
                        .thenComparing(ConfiguredStage::id))
                .toList();
        this.maximumLineCharacters = maximumLineCharacters;
        this.maximumInputCharacters = maximumInputCharacters;
        this.maximumOutputCharacters = maximumOutputCharacters;
    }

    public List<SanitizationStageId> stageOrder() {
        return stages.stream().map(ConfiguredStage::id).toList();
    }

    /**
     * Streams sanitized text to caller-owned temporary output.
     *
     * <p>The caller must discard output when this method throws. Input and output lifecycle,
     * atomic publication, charset decoding, and workspace ownership remain orchestration
     * responsibilities outside this pure text pipeline.
     */
    public SanitizationResult sanitize(
            String artifactName,
            Reader input,
            Writer output,
            CancellationSignal cancellation) {
        String safeArtifact = requireArtifactName(artifactName);
        Reader source = Objects.requireNonNull(input, "input");
        Writer destination = Objects.requireNonNull(output, "output");
        CancellationSignal signal = Objects.requireNonNull(cancellation, "cancellation");
        List<SanitizationFinding> findings = new ArrayList<>();
        long inputCharacters = 0;
        long outputCharacters = 0;
        long lineNumber = 0;
        try {
            PushbackReader reader = new PushbackReader(source, 1);
            while (true) {
                requireNotCancelled(signal, safeArtifact);
                Line line = readLine(reader, signal, safeArtifact, inputCharacters);
                if (line == null) {
                    break;
                }
                lineNumber++;
                inputCharacters = Math.addExact(inputCharacters, line.characterCount());
                MappedLine mapped = new MappedLine(line.content());
                for (ConfiguredStage stage : stages) {
                    applyStage(safeArtifact, lineNumber, mapped, stage, findings);
                }
                requireNotCancelled(signal, safeArtifact);
                long nextOutput = Math.addExact(
                        outputCharacters,
                        (long) mapped.value().length() + line.terminator().length());
                if (nextOutput > maximumOutputCharacters) {
                    throw failure(
                            SanitizationCode.OUTPUT_LIMIT_EXCEEDED,
                            safeArtifact,
                            "Sanitization output exceeded the product character limit",
                            null);
                }
                destination.write(mapped.value());
                destination.write(line.terminator());
                outputCharacters = nextOutput;
            }
            destination.flush();
            return new SanitizationResult(
                    safeArtifact,
                    inputCharacters,
                    outputCharacters,
                    findings);
        } catch (SanitizationException exception) {
            throw exception;
        } catch (IOException | ArithmeticException exception) {
            throw failure(
                    SanitizationCode.IO_FAILURE,
                    safeArtifact,
                    "Sanitization stream failed",
                    exception);
        }
    }

    private Line readLine(
            PushbackReader reader,
            CancellationSignal cancellation,
            String artifactName,
            long consumed)
            throws IOException {
        StringBuilder content = new StringBuilder();
        String terminator = "";
        int sinceCancellationCheck = 0;
        while (true) {
            int value = reader.read();
            if (value < 0) {
                return content.isEmpty() ? null : new Line(content.toString(), terminator);
            }
            if (consumed + content.length() + 1 > maximumInputCharacters) {
                throw failure(
                        SanitizationCode.INPUT_LIMIT_EXCEEDED,
                        artifactName,
                        "Sanitization input exceeded the product character limit",
                        null);
            }
            if (value == '\n') {
                terminator = "\n";
                break;
            }
            if (value == '\r') {
                int next = reader.read();
                if (next == '\n') {
                    if (consumed + content.length() + 2 > maximumInputCharacters) {
                        throw failure(
                                SanitizationCode.INPUT_LIMIT_EXCEEDED,
                                artifactName,
                                "Sanitization input exceeded the product character limit",
                                null);
                    }
                    terminator = "\r\n";
                } else {
                    terminator = "\r";
                    if (next >= 0) {
                        reader.unread(next);
                    }
                }
                break;
            }
            if (content.length() == maximumLineCharacters) {
                throw failure(
                        SanitizationCode.LINE_LIMIT_EXCEEDED,
                        artifactName,
                        "Sanitization line exceeded the product character limit",
                        null);
            }
            content.append((char) value);
            if (++sinceCancellationCheck == CANCELLATION_CHECK_INTERVAL) {
                requireNotCancelled(cancellation, artifactName);
                sinceCancellationCheck = 0;
            }
        }
        return new Line(content.toString(), terminator);
    }

    private void applyStage(
            String artifactName,
            long lineNumber,
            MappedLine line,
            ConfiguredStage stage,
            List<SanitizationFinding> findings) {
        List<SanitizationMatch> matches;
        try {
            matches = stage.implementation().findMatches(line.value());
        } catch (Exception | LinkageError exception) {
            throw stageFailure(
                    artifactName,
                    stage.id(),
                    lineNumber,
                    "Sanitization stage callback failed");
        }
        if (matches == null) {
            throw stageFailure(
                    artifactName,
                    stage.id(),
                    lineNumber,
                    "Sanitization stage returned null");
        }
        List<SanitizationMatch> validated;
        try {
            validated = validateMatches(matches, line.value().length());
            validated = validated.stream()
                    .filter(match -> !line.overlapsProtected(match.start(), match.end()))
                    .toList();
            line.requireApplicable(validated, maximumLineCharacters);
        } catch (RuntimeException exception) {
            throw stageFailure(
                    artifactName,
                    stage.id(),
                    lineNumber,
                    "Sanitization stage returned an invalid result");
        }
        List<SanitizationFinding> stageFindings = new ArrayList<>(validated.size());
        for (SanitizationMatch match : validated) {
            stageFindings.add(new SanitizationFinding(
                    artifactName,
                    stage.id(),
                    lineNumber,
                    line.originalBoundary(match.start()) + 1,
                    line.originalBoundary(match.end()) + 1,
                    match.classification(),
                    match.action()));
        }
        if ((long) findings.size() + stageFindings.size() > PRODUCT_MAX_FINDINGS) {
            throw failure(
                    SanitizationCode.METADATA_LIMIT_EXCEEDED,
                    artifactName,
                    "Sanitization findings exceeded the product limit",
                    null);
        }
        line.apply(validated);
        findings.addAll(stageFindings);
    }

    private static List<SanitizationMatch> validateMatches(
            List<SanitizationMatch> matches, int lineLength) {
        if (matches.size() > MAXIMUM_MATCHES_PER_STAGE_LINE) {
            throw new IllegalArgumentException("Sanitization stage returned too many matches");
        }
        List<SanitizationMatch> copy = List.copyOf(matches);
        int previousEnd = 0;
        for (SanitizationMatch match : copy) {
            Objects.requireNonNull(match, "match");
            if (match.start() < previousEnd || match.end() > lineLength) {
                throw new IllegalArgumentException("Sanitization matches are invalid or overlap");
            }
            previousEnd = match.end();
        }
        return copy;
    }

    private static SanitizationException stageFailure(
            String artifactName,
            SanitizationStageId stageId,
            long line,
            String message) {
        return new SanitizationException(
                SanitizationCode.STAGE_FAILED,
                artifactName,
                stageId,
                line,
                message,
                null);
    }

    private static String requireArtifactName(String artifactName) {
        return SanitizationContract.requireArtifactName(artifactName);
    }

    private static void requireNotCancelled(
            CancellationSignal cancellation, String artifactName) {
        if (cancellation.isCancellationRequested()) {
            throw failure(
                    SanitizationCode.CANCELLED,
                    artifactName,
                    "Sanitization was cancelled",
                    null);
        }
    }

    private static SanitizationException failure(
            SanitizationCode code, String artifactName, String message, Throwable cause) {
        return new SanitizationException(code, artifactName, message, cause);
    }

    private record Line(String content, String terminator) {
        private long characterCount() {
            return (long) content.length() + terminator.length();
        }
    }

    private record ConfiguredStage(
            SanitizationStageId id, int order, TextSanitizationStage implementation) {}

    private static final class MappedLine {
        private String value;
        private int[] originalBoundaries;
        private boolean[] protectedCharacters;

        private MappedLine(String value) {
            this.value = value;
            this.originalBoundaries = new int[value.length() + 1];
            this.protectedCharacters = new boolean[value.length()];
            for (int index = 0; index <= value.length(); index++) {
                originalBoundaries[index] = index;
            }
        }

        private String value() {
            return value;
        }

        private int originalBoundary(int index) {
            return originalBoundaries[index];
        }

        private boolean overlapsProtected(int start, int end) {
            for (int index = start; index < end; index++) {
                if (protectedCharacters[index]) {
                    return true;
                }
            }
            return false;
        }

        private void requireApplicable(
                List<SanitizationMatch> matches, int maximumCharacters) {
            long length = value.length();
            for (SanitizationMatch match : matches) {
                if (match.replacement().isPresent()) {
                    length = Math.addExact(
                            length,
                            (long) match.replacement().orElseThrow().length()
                                    - (match.end() - match.start()));
                }
            }
            if (length > maximumCharacters) {
                throw new IllegalArgumentException(
                        "Sanitization stage output exceeded the line limit");
            }
        }

        private void apply(List<SanitizationMatch> matches) {
            List<SanitizationMatch> replacements = matches.stream()
                    .filter(match -> match.replacement().isPresent())
                    .toList();
            if (replacements.isEmpty()) {
                return;
            }
            int newLength = value.length();
            for (SanitizationMatch match : replacements) {
                newLength += match.replacement().orElseThrow().length()
                        - (match.end() - match.start());
            }
            StringBuilder updatedValue = new StringBuilder(newLength);
            int[] updatedBoundaries = new int[newLength + 1];
            boolean[] updatedProtected = new boolean[newLength];
            int oldCursor = 0;
            int newCursor = 0;
            updatedBoundaries[0] = originalBoundaries[0];
            for (SanitizationMatch match : replacements) {
                updatedValue.append(value, oldCursor, match.start());
                for (int index = oldCursor + 1; index <= match.start(); index++) {
                    updatedBoundaries[++newCursor] = originalBoundaries[index];
                }
                System.arraycopy(
                        protectedCharacters,
                        oldCursor,
                        updatedProtected,
                        newCursor - (match.start() - oldCursor),
                        match.start() - oldCursor);
                String replacement = match.replacement().orElseThrow();
                updatedValue.append(replacement);
                if (replacement.isEmpty()) {
                    updatedBoundaries[newCursor] = originalBoundaries[match.end()];
                } else {
                    for (int index = 1; index < replacement.length(); index++) {
                        updatedBoundaries[++newCursor] = originalBoundaries[match.start()];
                    }
                    updatedBoundaries[++newCursor] = originalBoundaries[match.end()];
                    java.util.Arrays.fill(
                            updatedProtected,
                            newCursor - replacement.length(),
                            newCursor,
                            true);
                }
                oldCursor = match.end();
            }
            updatedValue.append(value, oldCursor, value.length());
            for (int index = oldCursor + 1; index < originalBoundaries.length; index++) {
                updatedBoundaries[++newCursor] = originalBoundaries[index];
            }
            System.arraycopy(
                    protectedCharacters,
                    oldCursor,
                    updatedProtected,
                    newCursor - (value.length() - oldCursor),
                    value.length() - oldCursor);
            value = updatedValue.toString();
            originalBoundaries = updatedBoundaries;
            protectedCharacters = updatedProtected;
        }
    }
}
