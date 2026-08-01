package com.cybersammy.bugreport.api.specification;

import java.util.Objects;

/** Data-only target of a support destination declaration. */
public sealed interface SupportDestinationTarget
        permits SupportDestinationTarget.EmailTarget,
                SupportDestinationTarget.LocalArchiveTarget,
                SupportDestinationTarget.WebTarget {
    /**
     * Returns the local-archive target.
     *
     * @return shared local target
     */
    static SupportDestinationTarget localArchive() {
        return LocalArchiveTarget.INSTANCE;
    }

    /**
     * Creates an HTTPS web target.
     *
     * @param url validated HTTPS address
     * @return web target
     */
    static SupportDestinationTarget web(HttpsUrl url) {
        return new WebTarget(url);
    }

    /**
     * Creates an email composition target.
     *
     * @param address validated support address
     * @return email target
     */
    static SupportDestinationTarget email(EmailAddress address) {
        return new EmailTarget(address);
    }

    /** Local archive target with no provider-selected filesystem location. */
    enum LocalArchiveTarget implements SupportDestinationTarget {
        /** Shared local-archive target. */
        INSTANCE
    }

    /**
     * External HTTPS target.
     *
     * @param url exact validated address
     */
    record WebTarget(HttpsUrl url) implements SupportDestinationTarget {
        /** Validates and creates a web target. */
        public WebTarget {
            Objects.requireNonNull(url, "url");
        }
    }

    /**
     * External email composition target.
     *
     * @param address exact validated address
     */
    record EmailTarget(EmailAddress address) implements SupportDestinationTarget {
        /** Validates and creates an email target. */
        public EmailTarget {
            Objects.requireNonNull(address, "address");
        }
    }
}
