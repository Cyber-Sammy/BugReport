package com.cybersammy.bugreport.core.session;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.core.registry.ProviderSupport;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import java.util.Objects;
import java.util.Optional;

/** Mutable state-machine boundary created from one accepted registry provider. */
public final class ReportSession {
    private final ReportSessionId id;
    private final ProviderSpecification providerSpecification;
    private final ProviderSupport providerSupport;

    private CategorySpecification selectedCategory;
    private ReportSessionState state = ReportSessionState.CREATED;
    private long revision;

    ReportSession(
            ReportSessionId id,
            RegisteredProvider provider) {
        this.id = Objects.requireNonNull(id, "id");
        RegisteredProvider acceptedProvider = Objects.requireNonNull(provider, "provider");
        if (acceptedProvider.support().state() == ProviderSupportState.DISABLED) {
            throw new IllegalArgumentException(
                    "Cannot create a report session for a disabled provider: "
                            + acceptedProvider.id());
        }
        providerSpecification = acceptedProvider.specification();
        providerSupport = acceptedProvider.support();
    }

    /** Returns an immutable, internally consistent point-in-time snapshot. */
    public synchronized ReportSessionSnapshot snapshot() {
        return currentSnapshot();
    }

    /**
     * Selects one category declared by the session provider.
     *
     * <p>The first successful selection atomically starts form entry. Re-selecting the same
     * category while editing is idempotent; selecting another declared category replaces the
     * selection and advances the revision.
     *
     * @param categoryId category declared by the session provider
     * @return resulting immutable session snapshot
     */
    public synchronized ReportSessionSnapshot selectCategory(CategoryId categoryId) {
        CategoryId requestedCategoryId = Objects.requireNonNull(categoryId, "categoryId");
        if (state != ReportSessionState.CREATED
                && state != ReportSessionState.FORM_IN_PROGRESS) {
            throw new InvalidReportCategorySelectionStateException(id, state, requestedCategoryId);
        }

        CategorySpecification requestedCategory =
                Optional.ofNullable(providerSpecification.categories().get(requestedCategoryId))
                        .orElseThrow(
                                () ->
                                        new UnknownReportCategoryException(
                                                id,
                                                providerSpecification.id(),
                                                requestedCategoryId));
        if (selectedCategory == requestedCategory) {
            return currentSnapshot();
        }

        selectedCategory = requestedCategory;
        state = ReportSessionState.FORM_IN_PROGRESS;
        revision++;
        return currentSnapshot();
    }

    /** Applies one valid direct transition and returns the resulting snapshot. */
    public synchronized ReportSessionSnapshot transitionTo(ReportSessionState requestedState) {
        Objects.requireNonNull(requestedState, "requestedState");
        if (!state.canTransitionTo(requestedState)
                || (state == ReportSessionState.CREATED
                        && requestedState == ReportSessionState.FORM_IN_PROGRESS)) {
            throw new InvalidReportSessionTransitionException(id, state, requestedState);
        }
        state = requestedState;
        revision++;
        return currentSnapshot();
    }

    private ReportSessionSnapshot currentSnapshot() {
        return new ReportSessionSnapshot(
                id,
                providerSpecification,
                providerSupport,
                Optional.ofNullable(selectedCategory),
                state,
                revision);
    }
}
