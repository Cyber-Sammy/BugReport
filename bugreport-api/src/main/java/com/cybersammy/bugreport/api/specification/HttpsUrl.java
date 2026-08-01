package com.cybersammy.bugreport.api.specification;

/**
 * Bounded HTTPS destination address represented without network implementation types.
 *
 * @param value exact address
 */
public record HttpsUrl(String value) {
    /** Validates and creates an HTTPS address. */
    public HttpsUrl {
        value = SpecificationChecks.requireHttpsUrl(value);
    }

    /**
     * Creates a validated HTTPS address.
     *
     * @param value exact address
     * @return validated address
     */
    public static HttpsUrl of(String value) {
        return new HttpsUrl(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
