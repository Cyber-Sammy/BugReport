package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import java.io.Serial;
import java.util.Objects;

/** Typed rejection before any category source selector is executed. */
public final class CategorySourcePlanException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    private final CategorySourcePlanRequestCode code;
    private final String providerId;
    private final String categoryId;

    CategorySourcePlanException(
            CategorySourcePlanRequestCode code,
            ProviderId providerId,
            CategoryId categoryId,
            String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.providerId = Objects.requireNonNull(providerId, "providerId").value();
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId").value();
    }

    /** Returns the stable request rejection reason. */
    public CategorySourcePlanRequestCode code() {
        return code;
    }

    /** Returns the requested provider identity. */
    public ProviderId providerId() {
        return ProviderId.parse(providerId);
    }

    /** Returns the requested category identity. */
    public CategoryId categoryId() {
        return CategoryId.of(categoryId);
    }
}
