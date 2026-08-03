package com.cybersammy.bugreport.core.session;

import java.util.Objects;

/** Lifecycle state of one loader-neutral report session. */
public enum ReportSessionState {
    CREATED,
    FORM_IN_PROGRESS,
    COLLECTION_PLANNED,
    COLLECTING,
    PARTIALLY_COLLECTED,
    SANITIZING,
    REVIEW_REQUIRED,
    READY,
    DELIVERING,
    COMPLETED,
    FAILED_VALIDATION,
    FAILED_COLLECTION,
    FAILED_SANITIZATION,
    FAILED_DELIVERY,
    CANCELLED;

    /** Reports whether no later state is allowed. */
    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** Reports whether the requested state is a valid direct transition. */
    public boolean canTransitionTo(ReportSessionState target) {
        Objects.requireNonNull(target, "target");
        if (target == CANCELLED) {
            return !terminal();
        }
        return switch (this) {
            case CREATED -> target == FORM_IN_PROGRESS;
            case FORM_IN_PROGRESS ->
                    target == COLLECTION_PLANNED || target == FAILED_VALIDATION;
            case FAILED_VALIDATION -> target == FORM_IN_PROGRESS;
            case COLLECTION_PLANNED ->
                    target == COLLECTING || target == FORM_IN_PROGRESS;
            case COLLECTING ->
                    target == SANITIZING
                            || target == PARTIALLY_COLLECTED
                            || target == FAILED_COLLECTION;
            case PARTIALLY_COLLECTED ->
                    target == SANITIZING || target == COLLECTION_PLANNED;
            case FAILED_COLLECTION -> target == COLLECTION_PLANNED;
            case SANITIZING ->
                    target == REVIEW_REQUIRED || target == FAILED_SANITIZATION;
            case FAILED_SANITIZATION -> target == SANITIZING;
            case REVIEW_REQUIRED -> target == READY || target == SANITIZING;
            case READY -> target == DELIVERING || target == REVIEW_REQUIRED;
            case DELIVERING -> target == COMPLETED || target == FAILED_DELIVERY;
            case FAILED_DELIVERY -> target == READY || target == DELIVERING;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
