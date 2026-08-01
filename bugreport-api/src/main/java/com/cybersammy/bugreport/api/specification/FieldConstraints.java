package com.cybersammy.bugreport.api.specification;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Immutable declarative bounds interpreted according to a field's kind. */
public final class FieldConstraints {
    private static final FieldConstraints NONE = new FieldConstraints(new Builder());

    private final Integer minimumLength;
    private final Integer maximumLength;
    private final Integer minimumItems;
    private final Integer maximumItems;
    private final BigDecimal minimumNumber;
    private final BigDecimal maximumNumber;

    private FieldConstraints(Builder builder) {
        minimumLength = builder.minimumLength;
        maximumLength = builder.maximumLength;
        minimumItems = builder.minimumItems;
        maximumItems = builder.maximumItems;
        minimumNumber = builder.minimumNumber;
        maximumNumber = builder.maximumNumber;
    }

    /**
     * Returns an unconstrained declaration.
     *
     * @return empty constraints
     */
    public static FieldConstraints none() {
        return NONE;
    }

    /**
     * Creates a constraints builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns declared minimum character count.
     *
     * @return declared minimum character count
     */
    public OptionalInt minimumLength() {
        return optionalInt(minimumLength);
    }

    /**
     * Returns declared maximum character count.
     *
     * @return declared maximum character count
     */
    public OptionalInt maximumLength() {
        return optionalInt(maximumLength);
    }

    /**
     * Returns declared minimum list or selection count.
     *
     * @return declared minimum list or selection count
     */
    public OptionalInt minimumItems() {
        return optionalInt(minimumItems);
    }

    /**
     * Returns declared maximum list or selection count.
     *
     * @return declared maximum list or selection count
     */
    public OptionalInt maximumItems() {
        return optionalInt(maximumItems);
    }

    /**
     * Returns declared inclusive numeric minimum.
     *
     * @return declared inclusive numeric minimum
     */
    public Optional<BigDecimal> minimumNumber() {
        return Optional.ofNullable(minimumNumber);
    }

    /**
     * Returns declared inclusive numeric maximum.
     *
     * @return declared inclusive numeric maximum
     */
    public Optional<BigDecimal> maximumNumber() {
        return Optional.ofNullable(maximumNumber);
    }

    boolean hasLengthBounds() {
        return minimumLength != null || maximumLength != null;
    }

    boolean hasItemBounds() {
        return minimumItems != null || maximumItems != null;
    }

    boolean hasNumericBounds() {
        return minimumNumber != null || maximumNumber != null;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof FieldConstraints constraints)) {
            return false;
        }
        return Objects.equals(minimumLength, constraints.minimumLength)
                && Objects.equals(maximumLength, constraints.maximumLength)
                && Objects.equals(minimumItems, constraints.minimumItems)
                && Objects.equals(maximumItems, constraints.maximumItems)
                && Objects.equals(minimumNumber, constraints.minimumNumber)
                && Objects.equals(maximumNumber, constraints.maximumNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                minimumLength,
                maximumLength,
                minimumItems,
                maximumItems,
                minimumNumber,
                maximumNumber);
    }

    private static OptionalInt optionalInt(Integer value) {
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    /** Builder for immutable field constraints. */
    public static final class Builder {
        private Integer minimumLength;
        private Integer maximumLength;
        private Integer minimumItems;
        private Integer maximumItems;
        private BigDecimal minimumNumber;
        private BigDecimal maximumNumber;

        private Builder() {}

        /**
         * Sets the inclusive minimum character count.
         *
         * @param value non-negative minimum
         * @return this builder
         */
        public Builder minimumLength(int value) {
            minimumLength = requireNonNegative(value, "minimumLength");
            return this;
        }

        /**
         * Sets the inclusive maximum character count.
         *
         * @param value positive maximum
         * @return this builder
         */
        public Builder maximumLength(int value) {
            maximumLength = requirePositive(value, "maximumLength");
            return this;
        }

        /**
         * Sets the inclusive minimum item count.
         *
         * @param value non-negative minimum
         * @return this builder
         */
        public Builder minimumItems(int value) {
            minimumItems = requireNonNegative(value, "minimumItems");
            return this;
        }

        /**
         * Sets the inclusive maximum item count.
         *
         * @param value positive maximum
         * @return this builder
         */
        public Builder maximumItems(int value) {
            maximumItems = requirePositive(value, "maximumItems");
            return this;
        }

        /**
         * Sets the inclusive numeric minimum.
         *
         * @param value decimal minimum
         * @return this builder
         */
        public Builder minimumNumber(BigDecimal value) {
            minimumNumber = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Sets the inclusive numeric maximum.
         *
         * @param value decimal maximum
         * @return this builder
         */
        public Builder maximumNumber(BigDecimal value) {
            maximumNumber = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Creates immutable, internally consistent constraints.
         *
         * @return field constraints
         */
        public FieldConstraints build() {
            if (minimumLength != null
                    && maximumLength != null
                    && minimumLength > maximumLength) {
                throw new IllegalArgumentException("minimumLength cannot exceed maximumLength");
            }
            if (minimumItems != null
                    && maximumItems != null
                    && minimumItems > maximumItems) {
                throw new IllegalArgumentException("minimumItems cannot exceed maximumItems");
            }
            if (minimumNumber != null
                    && maximumNumber != null
                    && minimumNumber.compareTo(maximumNumber) > 0) {
                throw new IllegalArgumentException("minimumNumber cannot exceed maximumNumber");
            }
            if (minimumLength == null
                    && maximumLength == null
                    && minimumItems == null
                    && maximumItems == null
                    && minimumNumber == null
                    && maximumNumber == null) {
                return NONE;
            }
            return new FieldConstraints(this);
        }

        private static int requireNonNegative(int value, String name) {
            if (value < 0) {
                throw new IllegalArgumentException(name + " must be non-negative");
            }
            return value;
        }

        private static int requirePositive(int value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }
    }
}
