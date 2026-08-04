package com.cybersammy.bugreport.core.draft;

import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.util.Objects;

/** Isolated result of loading one canonical draft filename. */
public sealed interface DraftLoadOutcome {
    /** Returns the session identity derived from the trusted filename. */
    ReportSessionId sessionId();

    /** Successfully decoded and optionally migrated draft. */
    record Loaded(ReportSessionId sessionId, DecodedReportDraft decoded)
            implements DraftLoadOutcome {
        public Loaded {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(decoded, "decoded");
            if (!sessionId.equals(decoded.draft().sessionId())) {
                throw new IllegalArgumentException("Loaded draft identity must match its filename");
            }
        }
    }

    /** One rejected file which did not prevent other drafts from loading. */
    record Rejected(ReportSessionId sessionId, DraftLoadFailureCode code)
            implements DraftLoadOutcome {
        public Rejected {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(code, "code");
        }
    }
}
