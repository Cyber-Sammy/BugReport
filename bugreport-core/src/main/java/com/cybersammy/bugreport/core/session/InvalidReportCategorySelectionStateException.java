package com.cybersammy.bugreport.core.session;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import java.io.Serial;
import java.util.Objects;

/** Typed rejection of category selection outside the form-entry boundary. */
public final class InvalidReportCategorySelectionStateException extends IllegalStateException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String sessionId;
    private final ReportSessionState state;
    private final String categoryId;

    InvalidReportCategorySelectionStateException(
            ReportSessionId sessionId,
            ReportSessionState state,
            CategoryId categoryId) {
        super(
                "Cannot select report category "
                        + categoryId
                        + " for session "
                        + sessionId
                        + " while in state "
                        + state);
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId").toString();
        this.state = Objects.requireNonNull(state, "state");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId").value();
    }

    /** Returns the rejected session identity. */
    public ReportSessionId sessionId() {
        return ReportSessionId.parse(sessionId);
    }

    /** Returns the state that rejected category selection. */
    public ReportSessionState state() {
        return state;
    }

    /** Returns the requested category identity. */
    public CategoryId categoryId() {
        return CategoryId.of(categoryId);
    }
}
