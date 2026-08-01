package com.cybersammy.bugreport.api.specification;

/**
 * Bounded ASCII support email address represented without delivery behavior.
 *
 * @param value exact address
 */
public record EmailAddress(String value) {
    /** Validates and creates an email address. */
    public EmailAddress {
        value = SpecificationChecks.requireEmailAddress(value);
    }

    /**
     * Creates a validated email address.
     *
     * @param value exact address
     * @return validated address
     */
    public static EmailAddress of(String value) {
        return new EmailAddress(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
