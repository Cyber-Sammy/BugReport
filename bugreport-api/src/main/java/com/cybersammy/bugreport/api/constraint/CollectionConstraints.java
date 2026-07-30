package com.cybersammy.bugreport.api.constraint;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Immutable provider-requested upper bounds for diagnostic collection.
 *
 * <p>Unset values request the product default. Providers may request tighter
 * values but cannot raise product ceilings; Core applies that policy when it
 * creates a collection plan.
 */
public final class CollectionConstraints {
    private static final CollectionConstraints EMPTY = new Builder().build();

    private final Integer maxTraversalDepth;
    private final Integer maxMatchedFiles;
    private final Long maxBytesPerFile;
    private final Long maxTotalBytes;
    private final Integer maxGeneratedArtifacts;
    private final Duration callbackTimeout;

    private CollectionConstraints(Builder builder) {
        maxTraversalDepth = builder.maxTraversalDepth;
        maxMatchedFiles = builder.maxMatchedFiles;
        maxBytesPerFile = builder.maxBytesPerFile;
        maxTotalBytes = builder.maxTotalBytes;
        maxGeneratedArtifacts = builder.maxGeneratedArtifacts;
        callbackTimeout = builder.callbackTimeout;
    }

    /**
     * Returns a constraint set with every value left to product defaults.
     *
     * @return empty constraint set
     */
    public static CollectionConstraints defaults() {
        return EMPTY;
    }

    /**
     * Creates a mutable builder whose output is immutable.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the requested maximum traversal depth.
     *
     * @return requested maximum
     */
    public OptionalInt maxTraversalDepth() {
        return optionalInt(maxTraversalDepth);
    }

    /**
     * Returns the requested maximum matched-file count.
     *
     * @return requested maximum
     */
    public OptionalInt maxMatchedFiles() {
        return optionalInt(maxMatchedFiles);
    }

    /**
     * Returns the requested maximum bytes accepted from one file.
     *
     * @return requested maximum
     */
    public OptionalLong maxBytesPerFile() {
        return optionalLong(maxBytesPerFile);
    }

    /**
     * Returns the requested aggregate source-byte maximum.
     *
     * @return requested maximum
     */
    public OptionalLong maxTotalBytes() {
        return optionalLong(maxTotalBytes);
    }

    /**
     * Returns the requested generated-artifact count maximum.
     *
     * @return requested maximum
     */
    public OptionalInt maxGeneratedArtifacts() {
        return optionalInt(maxGeneratedArtifacts);
    }

    /**
     * Returns the requested generated-callback timeout.
     *
     * @return requested timeout
     */
    public Optional<Duration> callbackTimeout() {
        return Optional.ofNullable(callbackTimeout);
    }

    private static OptionalInt optionalInt(Integer value) {
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    private static OptionalLong optionalLong(Long value) {
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CollectionConstraints constraints)) {
            return false;
        }
        return Objects.equals(maxTraversalDepth, constraints.maxTraversalDepth)
                && Objects.equals(maxMatchedFiles, constraints.maxMatchedFiles)
                && Objects.equals(maxBytesPerFile, constraints.maxBytesPerFile)
                && Objects.equals(maxTotalBytes, constraints.maxTotalBytes)
                && Objects.equals(maxGeneratedArtifacts, constraints.maxGeneratedArtifacts)
                && Objects.equals(callbackTimeout, constraints.callbackTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                maxTraversalDepth,
                maxMatchedFiles,
                maxBytesPerFile,
                maxTotalBytes,
                maxGeneratedArtifacts,
                callbackTimeout);
    }

    /** Builder for provider-requested collection bounds. */
    public static final class Builder {
        private Integer maxTraversalDepth;
        private Integer maxMatchedFiles;
        private Long maxBytesPerFile;
        private Long maxTotalBytes;
        private Integer maxGeneratedArtifacts;
        private Duration callbackTimeout;

        private Builder() {}

        /**
         * Sets the traversal-depth maximum. Zero permits only the selected node.
         *
         * @param value non-negative maximum
         * @return this builder
         */
        public Builder maxTraversalDepth(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("maxTraversalDepth must be non-negative");
            }
            maxTraversalDepth = value;
            return this;
        }

        /**
         * Sets the matched-file maximum.
         *
         * @param value positive maximum
         * @return this builder
         */
        public Builder maxMatchedFiles(int value) {
            maxMatchedFiles = requirePositive(value, "maxMatchedFiles");
            return this;
        }

        /**
         * Sets the per-file byte maximum.
         *
         * @param value positive maximum
         * @return this builder
         */
        public Builder maxBytesPerFile(long value) {
            maxBytesPerFile = requirePositive(value, "maxBytesPerFile");
            return this;
        }

        /**
         * Sets the aggregate source-byte maximum.
         *
         * @param value positive maximum
         * @return this builder
         */
        public Builder maxTotalBytes(long value) {
            maxTotalBytes = requirePositive(value, "maxTotalBytes");
            return this;
        }

        /**
         * Sets the generated-artifact maximum.
         *
         * @param value positive maximum
         * @return this builder
         */
        public Builder maxGeneratedArtifacts(int value) {
            maxGeneratedArtifacts = requirePositive(value, "maxGeneratedArtifacts");
            return this;
        }

        /**
         * Sets the generated-callback timeout.
         *
         * @param value positive duration
         * @return this builder
         */
        public Builder callbackTimeout(Duration value) {
            Objects.requireNonNull(value, "value");
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("callbackTimeout must be positive");
            }
            callbackTimeout = value;
            return this;
        }

        /**
         * Creates the immutable constraints.
         *
         * @return immutable constraints
         */
        public CollectionConstraints build() {
            if (maxBytesPerFile != null
                    && maxTotalBytes != null
                    && maxBytesPerFile > maxTotalBytes) {
                throw new IllegalArgumentException(
                        "maxBytesPerFile cannot exceed maxTotalBytes");
            }
            return new CollectionConstraints(this);
        }

        private static int requirePositive(int value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }

        private static long requirePositive(long value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }
    }
}
