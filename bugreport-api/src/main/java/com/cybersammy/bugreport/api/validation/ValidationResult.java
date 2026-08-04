package com.cybersammy.bugreport.api.validation;

import com.cybersammy.bugreport.api.identifier.ValidationCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable deterministic result of validating one public contract. */
public final class ValidationResult {
    /** Maximum issues retained in one validation result. */
    public static final int MAX_ISSUES = 1_024;

    private static final Comparator<ValidationIssue> ISSUE_ORDER =
            Comparator.comparing(ValidationIssue::path)
                    .thenComparing(ValidationIssue::severity)
                    .thenComparing(ValidationIssue::code);
    private static final ValidationResult VALID = new ValidationResult(List.of());

    private final List<ValidationIssue> issues;

    private ValidationResult(List<ValidationIssue> issues) {
        ArrayList<ValidationIssue> ordered = new ArrayList<>(issues);
        ordered.sort(ISSUE_ORDER);
        this.issues = List.copyOf(ordered);
    }

    /**
     * Returns a result with no issues.
     *
     * @return valid result
     */
    public static ValidationResult valid() {
        return VALID;
    }

    /**
     * Creates a builder.
     *
     * @return new result builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns all issues in deterministic path, severity, and code order.
     *
     * <p>Issues with identical stable keys retain their insertion order. The
     * developer-facing message is deliberately not part of canonical ordering.
     *
     * @return immutable issue list
     */
    public List<ValidationIssue> issues() {
        return issues;
    }

    /**
     * Reports whether the result contains no errors.
     *
     * @return {@code true} when the validated operation may continue
     */
    public boolean isValid() {
        return issues.stream().noneMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
    }

    /**
     * Reports whether any warning is present.
     *
     * @return {@code true} when the contract has a non-blocking limitation
     */
    public boolean hasWarnings() {
        return issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.WARNING);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ValidationResult result && issues.equals(result.issues);
    }

    @Override
    public int hashCode() {
        return issues.hashCode();
    }

    /** Builder for a deterministic validation result. */
    public static final class Builder {
        private final ArrayList<ValidationIssue> issues = new ArrayList<>();

        private Builder() {}

        /**
         * Adds an issue.
         *
         * @param issue immutable issue
         * @return this builder
         */
        public Builder add(ValidationIssue issue) {
            if (issues.size() >= MAX_ISSUES) {
                throw new IllegalArgumentException(
                        "Validation result exceeds " + MAX_ISSUES + " issues");
            }
            issues.add(Objects.requireNonNull(issue, "issue"));
            return this;
        }

        /**
         * Adds an error.
         *
         * @param code stable error code
         * @param path exact contract path
         * @param message developer-facing explanation
         * @return this builder
         */
        public Builder error(ValidationCode code, ValidationPath path, String message) {
            return add(new ValidationIssue(ValidationSeverity.ERROR, code, path, message));
        }

        /**
         * Adds a warning.
         *
         * @param code stable warning code
         * @param path exact contract path
         * @param message developer-facing explanation
         * @return this builder
         */
        public Builder warning(ValidationCode code, ValidationPath path, String message) {
            return add(new ValidationIssue(ValidationSeverity.WARNING, code, path, message));
        }

        /**
         * Adds all issues from another result.
         *
         * @param result result to merge
         * @return this builder
         */
        public Builder addAll(ValidationResult result) {
            ValidationResult validated = Objects.requireNonNull(result, "result");
            if (issues.size() + validated.issues().size() > MAX_ISSUES) {
                throw new IllegalArgumentException(
                        "Validation result exceeds " + MAX_ISSUES + " issues");
            }
            issues.addAll(validated.issues());
            return this;
        }

        /**
         * Creates the immutable sorted result.
         *
         * @return validation result
         */
        public ValidationResult build() {
            return issues.isEmpty() ? VALID : new ValidationResult(issues);
        }
    }
}
