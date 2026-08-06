package com.cybersammy.bugreport.core.sanitization;

import java.util.List;

/**
 * Trusted product sanitization rule evaluated against one bounded logical text line.
 *
 * <p>This Core extension boundary is not provider filesystem or workspace authority.
 */
public interface TextSanitizationStage {
    SanitizationStageId id();

    /** Lower values execute first; equal values are ordered by {@link #id()}. */
    int order();

    /**
     * Finds non-overlapping matches in the supplied current pipeline value.
     *
     * <p>Implementations must not retain the line or include matched text in exceptions or
     * returned metadata.
     */
    List<SanitizationMatch> findMatches(String line) throws Exception;
}
