package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.io.Serial;
import java.util.Objects;

/** Privacy-safe typed failure resolving a category-level generated diagnostic request. */
public final class CategoryGeneratedDiagnosticException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    private final GeneratedDiagnosticCode code;
    private final String sessionId;
    private final String providerId;
    private final String categoryId;

    CategoryGeneratedDiagnosticException(
            GeneratedDiagnosticCode code,
            ReportSessionId sessionId,
            ProviderId providerId,
            CategoryId categoryId,
            String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId").toString();
        this.providerId = Objects.requireNonNull(providerId, "providerId").value();
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId").value();
    }

    public GeneratedDiagnosticCode code() {
        return code;
    }

    public ReportSessionId sessionId() {
        return ReportSessionId.parse(sessionId);
    }

    public ProviderId providerId() {
        return ProviderId.parse(providerId);
    }

    public CategoryId categoryId() {
        return CategoryId.of(categoryId);
    }
}
