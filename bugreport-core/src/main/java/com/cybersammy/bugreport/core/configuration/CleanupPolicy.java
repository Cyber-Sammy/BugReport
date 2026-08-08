package com.cybersammy.bugreport.core.configuration;

/** Retention policy for completed report history; it never authorizes abandoned-workspace cleanup. */
public record CleanupPolicy(int retentionDays, int maximumRetainedReports) {
    public static final int MIN_RETENTION_DAYS = 1;
    public static final int MAX_RETENTION_DAYS = 3_650;
    public static final int MIN_RETAINED_REPORTS = 1;
    public static final int MAX_RETAINED_REPORTS = 10_000;

    public CleanupPolicy {
        if (retentionDays < MIN_RETENTION_DAYS
                || retentionDays > MAX_RETENTION_DAYS
                || maximumRetainedReports < MIN_RETAINED_REPORTS
                || maximumRetainedReports > MAX_RETAINED_REPORTS) {
            throw new IllegalArgumentException("Cleanup policy is outside product bounds");
        }
    }

    /** Returns conservative product defaults for future completed-report history. */
    public static CleanupPolicy productDefault() {
        return new CleanupPolicy(30, 100);
    }
}
