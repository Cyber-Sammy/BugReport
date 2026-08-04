package com.cybersammy.bugreport.core.form;

import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable submitted values in canonical field-ID order. */
public final class FormSubmission {
    private static final FormSubmission EMPTY = new FormSubmission(new TreeMap<>());

    private final Map<FieldId, FieldValue> values;

    private FormSubmission(TreeMap<FieldId, FieldValue> values) {
        this.values = Collections.unmodifiableMap(new TreeMap<>(values));
    }

    /**
     * Returns a submission with no values.
     *
     * @return shared immutable empty submission
     */
    public static FormSubmission empty() {
        return EMPTY;
    }

    /**
     * Creates a bounded submission builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns values in canonical field-ID order.
     *
     * @return immutable submitted values
     */
    public Map<FieldId, FieldValue> values() {
        return values;
    }

    /**
     * Finds one submitted value.
     *
     * @param id field identity
     * @return submitted value when present
     */
    public Optional<FieldValue> find(FieldId id) {
        return Optional.ofNullable(values.get(Objects.requireNonNull(id, "id")));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FormSubmission submission && values.equals(submission.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    /** Builder that rejects duplicate field identities. */
    public static final class Builder {
        private final TreeMap<FieldId, FieldValue> values = new TreeMap<>();

        private Builder() {}

        /**
         * Adds one submitted value.
         *
         * @param id field identity
         * @param value bounded immutable value
         * @return this builder
         */
        public Builder put(FieldId id, FieldValue value) {
            FieldId validatedId = Objects.requireNonNull(id, "id");
            FieldValue validatedValue = Objects.requireNonNull(value, "value");
            if (values.containsKey(validatedId)) {
                throw new IllegalArgumentException(
                        "Duplicate submitted field: " + validatedId);
            }
            if (values.size() >= CategorySpecification.MAX_FIELDS) {
                throw new IllegalArgumentException(
                        "Form submission exceeds "
                                + CategorySpecification.MAX_FIELDS
                                + " fields");
            }
            values.put(validatedId, validatedValue);
            return this;
        }

        /**
         * Creates the immutable submission.
         *
         * @return immutable canonical submission
         */
        public FormSubmission build() {
            return values.isEmpty() ? EMPTY : new FormSubmission(values);
        }
    }
}
