package com.cybersammy.bugreport.api.specification;

/** Provider preference before authoritative privacy and user policy is applied. */
public enum InclusionDefault {
    /** Request initial inclusion when product policy permits it. */
    INCLUDED,
    /** Keep the item excluded until the user includes it. */
    EXCLUDED
}
