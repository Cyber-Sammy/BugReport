package com.cybersammy.bugreport.neoforge;

import com.cybersammy.bugreport.core.error.DomainFailure;
import java.util.Objects;
import org.slf4j.Logger;

/** Emits Core failures without logging exception messages, paths, content, or causes. */
final class StructuredDomainFailureLogger {
    private final Logger logger;

    StructuredDomainFailureLogger(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void warn(DomainFailure failure) {
        Objects.requireNonNull(failure, "failure");
        logger.warn(
                "Bug Report operation failed: code={}, context={}",
                failure.errorCode().value(),
                failure.errorContext().logToken());
    }
}
