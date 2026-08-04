package com.cybersammy.bugreport.core.session;

import com.cybersammy.bugreport.core.form.FormSubmission;
import java.util.Objects;

/** Safely resumed session together with its persistence-neutral form values. */
public final class RecoveredReportSession {
    private final ReportSession session;
    private final FormSubmission formSubmission;
    private final ReportSessionState recordedState;

    RecoveredReportSession(
            ReportSession session,
            FormSubmission formSubmission,
            ReportSessionState recordedState) {
        this.session = Objects.requireNonNull(session, "session");
        this.formSubmission = Objects.requireNonNull(formSubmission, "formSubmission");
        this.recordedState = Objects.requireNonNull(recordedState, "recordedState");
    }

    /** Returns the live session restored into a safe state. */
    public ReportSession session() {
        return session;
    }

    /** Returns the recovered immutable form values. */
    public FormSubmission formSubmission() {
        return formSubmission;
    }

    /** Returns the state recorded before the normal restart. */
    public ReportSessionState recordedState() {
        return recordedState;
    }
}
