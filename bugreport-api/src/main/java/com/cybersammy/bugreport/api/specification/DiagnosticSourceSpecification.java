package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable declarative diagnostic-source specification. */
public final class DiagnosticSourceSpecification {
    private final DiagnosticSourceId id;
    private final DiagnosticSourceKind kind;
    private final LogicalRoot root;
    private final RelativePath path;
    private final FilenamePattern pattern;
    private final LocalizationKey labelKey;
    private final LocalizationKey descriptionKey;
    private final PrivacyClassification privacy;
    private final DiagnosticContentType contentType;
    private final ReportQualityRole qualityRole;
    private final InclusionDefault inclusionDefault;
    private final Set<SupportedSide> supportedSides;
    private final CollectionConstraints constraints;
    private final ExtensionMetadata extensions;

    private DiagnosticSourceSpecification(Builder builder) {
        id = builder.id;
        kind = builder.kind;
        root = builder.root;
        path = builder.path;
        pattern = builder.pattern;
        labelKey = builder.labelKey;
        descriptionKey = builder.descriptionKey;
        privacy = builder.privacy;
        contentType = builder.contentType;
        qualityRole = builder.qualityRole;
        inclusionDefault = builder.inclusionDefault;
        supportedSides = SpecificationChecks.copyNonEmptyEnumSet(
                builder.supportedSides, SupportedSide.class, "supportedSides");
        constraints = builder.constraints;
        extensions = builder.extensions;
    }

    /**
     * Selects one exact file below the game-logs or crash-reports root.
     *
     * @param id source ID within its provider
     * @param root approved logs or crash root
     * @param path exact relative file path
     * @return new source builder
     */
    public static Builder exactFile(
            DiagnosticSourceId id, LogicalRoot root, RelativePath path) {
        return new Builder(id, DiagnosticSourceKind.EXACT_FILE, root, path, null);
    }

    /**
     * Selects the newest matching file below an approved root.
     *
     * @param id source ID within its provider
     * @param root approved logs or crash root
     * @param pattern non-recursive filename pattern
     * @return new source builder
     */
    public static Builder latestFile(
            DiagnosticSourceId id, LogicalRoot root, FilenamePattern pattern) {
        return new Builder(id, DiagnosticSourceKind.LATEST_FILE, root, null, pattern);
    }

    /**
     * Selects bounded non-recursive matches directly below the logs root.
     *
     * @param id source ID within its provider
     * @param pattern non-recursive filename pattern
     * @return new source builder
     */
    public static Builder filteredLogDirectory(
            DiagnosticSourceId id, FilenamePattern pattern) {
        return new Builder(
                id, DiagnosticSourceKind.FILTERED_DIRECTORY, LogicalRoot.GAME_LOGS, null, pattern);
    }

    /**
     * Selects bounded non-recursive matches below one relative logs directory.
     *
     * @param id source ID within its provider
     * @param directory relative logs directory
     * @param pattern non-recursive filename pattern
     * @return new source builder
     */
    public static Builder filteredLogDirectory(
            DiagnosticSourceId id, RelativePath directory, FilenamePattern pattern) {
        return new Builder(
                id,
                DiagnosticSourceKind.FILTERED_DIRECTORY,
                LogicalRoot.GAME_LOGS,
                directory,
                pattern);
    }

    /**
     * Selects one exact mod-configuration file.
     *
     * @param id source ID within its provider
     * @param path exact relative configuration path
     * @return new source builder
     */
    public static Builder modConfiguration(DiagnosticSourceId id, RelativePath path) {
        return new Builder(
                id,
                DiagnosticSourceKind.MOD_CONFIGURATION,
                LogicalRoot.MOD_CONFIGURATION,
                path,
                null);
    }

    /**
     * Accepts source ID and returns product-owned latest-log source builder.
     *
     * @param id source ID
     * @return product-owned latest-log source builder
     */
    public static Builder latestLog(DiagnosticSourceId id) {
        return builtIn(id, DiagnosticSourceKind.LATEST_LOG);
    }

    /**
     * Accepts source ID and returns product-owned latest-crash source builder.
     *
     * @param id source ID
     * @return product-owned latest-crash source builder
     */
    public static Builder latestCrashReport(DiagnosticSourceId id) {
        return builtIn(id, DiagnosticSourceKind.LATEST_CRASH_REPORT);
    }

    /**
     * Accepts source ID and returns user-selected screenshot request builder.
     *
     * @param id source ID
     * @return user-selected screenshot request builder
     */
    public static Builder userSelectedScreenshot(DiagnosticSourceId id) {
        return builtIn(id, DiagnosticSourceKind.USER_SELECTED_SCREENSHOT);
    }

    /**
     * Accepts source ID and returns product-owned mod-list source builder.
     *
     * @param id source ID
     * @return product-owned mod-list source builder
     */
    public static Builder modList(DiagnosticSourceId id) {
        return builtIn(id, DiagnosticSourceKind.MOD_LIST);
    }

    /**
     * Accepts source ID and returns product-owned environment-summary source builder.
     *
     * @param id source ID
     * @return product-owned environment-summary source builder
     */
    public static Builder environmentSummary(DiagnosticSourceId id) {
        return builtIn(id, DiagnosticSourceKind.ENVIRONMENT_SUMMARY);
    }

    private static Builder builtIn(DiagnosticSourceId id, DiagnosticSourceKind kind) {
        return new Builder(id, kind, null, null, null);
    }

    /**
     * Returns source ID.
     *
     * @return source ID
     */
    public DiagnosticSourceId id() {
        return id;
    }

    /**
     * Returns selector kind.
     *
     * @return selector kind
     */
    public DiagnosticSourceKind kind() {
        return kind;
    }

    /**
     * Returns approved logical root when this is a filesystem selector.
     *
     * @return approved logical root when this is a filesystem selector
     */
    public Optional<LogicalRoot> root() {
        return Optional.ofNullable(root);
    }

    /**
     * Returns exact path or filtered-directory path when declared.
     *
     * @return exact path or filtered-directory path when declared
     */
    public Optional<RelativePath> path() {
        return Optional.ofNullable(path);
    }

    /**
     * Returns non-recursive filename pattern when declared.
     *
     * @return non-recursive filename pattern when declared
     */
    public Optional<FilenamePattern> pattern() {
        return Optional.ofNullable(pattern);
    }

    /**
     * Returns localized source label.
     *
     * @return localized source label
     */
    public LocalizationKey labelKey() {
        return labelKey;
    }

    /**
     * Returns optional localized source description.
     *
     * @return optional localized source description
     */
    public Optional<LocalizationKey> descriptionKey() {
        return Optional.ofNullable(descriptionKey);
    }

    /**
     * Returns provider-declared privacy floor.
     *
     * @return provider-declared privacy floor
     */
    public PrivacyClassification privacy() {
        return privacy;
    }

    /**
     * Returns declared content representation.
     *
     * @return declared content representation
     */
    public DiagnosticContentType contentType() {
        return contentType;
    }

    /**
     * Returns report-quality role, independent of inclusion authority.
     *
     * @return report-quality role, independent of inclusion authority
     */
    public ReportQualityRole qualityRole() {
        return qualityRole;
    }

    /**
     * Returns provider-requested initial inclusion state.
     *
     * @return provider-requested initial inclusion state
     */
    public InclusionDefault inclusionDefault() {
        return inclusionDefault;
    }

    /**
     * Returns immutable supported physical sides.
     *
     * @return immutable supported physical sides
     */
    public Set<SupportedSide> supportedSides() {
        return supportedSides;
    }

    /**
     * Returns provider-requested collection limits.
     *
     * @return provider-requested collection limits
     */
    public CollectionConstraints constraints() {
        return constraints;
    }

    /**
     * Returns bounded optional extension metadata.
     *
     * @return bounded optional extension metadata
     */
    public ExtensionMetadata extensions() {
        return extensions;
    }

    /** Builder for common diagnostic-source metadata and policy. */
    public static final class Builder {
        private final DiagnosticSourceId id;
        private final DiagnosticSourceKind kind;
        private final LogicalRoot root;
        private final RelativePath path;
        private final FilenamePattern pattern;
        private final EnumSet<SupportedSide> supportedSides =
                EnumSet.noneOf(SupportedSide.class);
        private LocalizationKey labelKey;
        private LocalizationKey descriptionKey;
        private PrivacyClassification privacy;
        private DiagnosticContentType contentType;
        private ReportQualityRole qualityRole = ReportQualityRole.OPTIONAL;
        private InclusionDefault inclusionDefault = InclusionDefault.EXCLUDED;
        private CollectionConstraints constraints = CollectionConstraints.defaults();
        private ExtensionMetadata extensions = ExtensionMetadata.empty();

        private Builder(
                DiagnosticSourceId id,
                DiagnosticSourceKind kind,
                LogicalRoot root,
                RelativePath path,
                FilenamePattern pattern) {
            this.id = Objects.requireNonNull(id, "id");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.root = root;
            this.path = path;
            this.pattern = pattern;
        }

        /**
         * Accepts localized label and returns this builder.
         *
         * @param value localized label
         * @return this builder
         */
        public Builder labelKey(LocalizationKey value) {
            labelKey = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts localized description and returns this builder.
         *
         * @param value localized description
         * @return this builder
         */
        public Builder descriptionKey(LocalizationKey value) {
            descriptionKey = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts declared privacy floor and returns this builder.
         *
         * @param value declared privacy floor
         * @return this builder
         */
        public Builder privacy(PrivacyClassification value) {
            privacy = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts declared content representation and returns this builder.
         *
         * @param value declared content representation
         * @return this builder
         */
        public Builder contentType(DiagnosticContentType value) {
            contentType = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts report-quality role and returns this builder.
         *
         * @param value report-quality role
         * @return this builder
         */
        public Builder qualityRole(ReportQualityRole value) {
            qualityRole = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts requested initial inclusion state and returns this builder.
         *
         * @param value requested initial inclusion state
         * @return this builder
         */
        public Builder inclusionDefault(InclusionDefault value) {
            inclusionDefault = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts supported physical side and returns this builder.
         *
         * @param value supported physical side
         * @return this builder
         */
        public Builder supportSide(SupportedSide value) {
            supportedSides.add(Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Accepts provider-requested collection limits and returns this builder.
         *
         * @param value provider-requested collection limits
         * @return this builder
         */
        public Builder constraints(CollectionConstraints value) {
            constraints = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts bounded extension metadata and returns this builder.
         *
         * @param value bounded extension metadata
         * @return this builder
         */
        public Builder extensions(ExtensionMetadata value) {
            extensions = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Returns validated immutable source specification.
         *
         * @return validated immutable source specification
         */
        public DiagnosticSourceSpecification build() {
            Objects.requireNonNull(labelKey, "labelKey");
            Objects.requireNonNull(privacy, "privacy");
            Objects.requireNonNull(contentType, "contentType");
            validateSelector();
            validatePrivacyAndContent();
            if (constraints.maxTraversalDepth().isPresent()
                    && constraints.maxTraversalDepth().getAsInt() > 0) {
                throw new IllegalArgumentException("Declarative sources are non-recursive");
            }
            return new DiagnosticSourceSpecification(this);
        }

        private void validateSelector() {
            switch (kind) {
                case EXACT_FILE -> {
                    if (root != LogicalRoot.GAME_LOGS && root != LogicalRoot.CRASH_REPORTS) {
                        throw new IllegalArgumentException(
                                "Exact files require the logs or crash-reports root");
                    }
                    requireSelector(path != null && pattern == null);
                }
                case LATEST_FILE -> {
                    if (root != LogicalRoot.GAME_LOGS && root != LogicalRoot.CRASH_REPORTS) {
                        throw new IllegalArgumentException(
                                "Latest files require the logs or crash-reports root");
                    }
                    requireSelector(path == null && pattern != null);
                }
                case FILTERED_DIRECTORY -> {
                    if (root != LogicalRoot.GAME_LOGS) {
                        throw new IllegalArgumentException(
                                "Filtered directories are allowed only below game logs");
                    }
                    requireSelector(pattern != null);
                }
                case MOD_CONFIGURATION -> requireSelector(
                        root == LogicalRoot.MOD_CONFIGURATION && path != null && pattern == null);
                case LATEST_LOG,
                        LATEST_CRASH_REPORT,
                        USER_SELECTED_SCREENSHOT,
                        MOD_LIST,
                        ENVIRONMENT_SUMMARY -> requireSelector(
                                root == null && path == null && pattern == null);
            }
        }

        private static void requireSelector(boolean valid) {
            if (!valid) {
                throw new IllegalArgumentException("Invalid selector for diagnostic source kind");
            }
        }

        private void validatePrivacyAndContent() {
            if (privacy == PrivacyClassification.PROHIBITED) {
                throw new IllegalArgumentException("A prohibited diagnostic source cannot be requested");
            }
            PrivacyClassification minimum = switch (kind) {
                case MOD_CONFIGURATION, USER_SELECTED_SCREENSHOT -> PrivacyClassification.SENSITIVE;
                case EXACT_FILE,
                        LATEST_FILE,
                        FILTERED_DIRECTORY,
                        LATEST_LOG,
                        LATEST_CRASH_REPORT -> PrivacyClassification.PERSONAL;
                case MOD_LIST, ENVIRONMENT_SUMMARY -> PrivacyClassification.LOW;
            };
            if (!privacy.isAtLeast(minimum)) {
                throw new IllegalArgumentException(
                        "Diagnostic source privacy is below the product floor for " + kind);
            }
            if (inclusionDefault == InclusionDefault.INCLUDED
                    && privacy != PrivacyClassification.LOW) {
                throw new IllegalArgumentException(
                        "Only LOW diagnostic data may request default inclusion");
            }
            if (kind == DiagnosticSourceKind.USER_SELECTED_SCREENSHOT
                    && contentType != DiagnosticContentType.BINARY) {
                throw new IllegalArgumentException("Screenshot sources must declare BINARY content");
            }
            if ((kind == DiagnosticSourceKind.MOD_LIST
                            || kind == DiagnosticSourceKind.ENVIRONMENT_SUMMARY)
                    && contentType == DiagnosticContentType.BINARY) {
                throw new IllegalArgumentException(
                        "Built-in summaries must declare TEXT or JSON content");
            }
        }
    }
}
