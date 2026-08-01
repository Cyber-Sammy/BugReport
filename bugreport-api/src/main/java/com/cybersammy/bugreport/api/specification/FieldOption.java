package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.identifier.FieldOptionId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import java.util.Objects;

/**
 * One stable localized option in a single-select or multi-select field.
 *
 * @param id option identity within its field
 * @param labelKey localized display label
 */
public record FieldOption(FieldOptionId id, LocalizationKey labelKey)
        implements Comparable<FieldOption> {
    /** Validates and creates a field option. */
    public FieldOption {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(labelKey, "labelKey");
    }

    @Override
    public int compareTo(FieldOption other) {
        return id.compareTo(other.id);
    }
}
