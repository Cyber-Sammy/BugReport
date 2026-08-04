package com.cybersammy.bugreport.core.session;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import java.io.Serial;
import java.util.Objects;

/** Typed rejection of a category not declared by the session provider. */
public final class UnknownReportCategoryException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String sessionId;
    private final String providerId;
    private final String categoryId;

    UnknownReportCategoryException(
            ReportSessionId sessionId,
            ProviderId providerId,
            CategoryId categoryId) {
        super(
                "Unknown report category "
                        + categoryId
                        + " for provider "
                        + providerId
                        + " in session "
                        + sessionId);
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId").toString();
        this.providerId = Objects.requireNonNull(providerId, "providerId").value();
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId").value();
    }

    /** Returns the rejected session identity. */
    public ReportSessionId sessionId() {
        return ReportSessionId.parse(sessionId);
    }

    /** Returns the provider whose declarations were consulted. */
    public ProviderId providerId() {
        return ProviderId.parse(providerId);
    }

    /** Returns the unknown category identity. */
    public CategoryId categoryId() {
        return CategoryId.of(categoryId);
    }
}
