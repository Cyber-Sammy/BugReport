package com.cybersammy.bugreport.neoforge.command;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import com.cybersammy.bugreport.core.session.CancellationReason;
import com.cybersammy.bugreport.core.session.ReportSession;
import com.cybersammy.bugreport.core.session.ReportSessionFactory;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.ReportSessionSnapshot;
import com.cybersammy.bugreport.core.session.UnknownReportCategoryException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Client-command application service bound to the current immutable provider registry. */
public final class BugReportCommandService {
    private final Supplier<ProviderRegistrySnapshot> registrySupplier;
    private final Map<ReportSessionId, ReportSession> sessions = new LinkedHashMap<>();

    public BugReportCommandService(Supplier<ProviderRegistrySnapshot> registrySupplier) {
        this.registrySupplier = Objects.requireNonNull(registrySupplier, "registrySupplier");
    }

    public List<Message> help() {
        return List.of(new Message("bugreport.command.help"));
    }

    public List<Message> listProviders() {
        List<RegisteredProvider> providers = registry().providers();
        if (providers.isEmpty()) {
            return List.of(new Message("bugreport.command.list.empty"));
        }
        return providers.stream()
                .map(provider -> new Message(
                        "bugreport.command.list.provider",
                        provider.id().toString(), provider.support().state().name()))
                .toList();
    }

    /** Returns immutable provider data suitable for a first-party client selector. */
    public List<ProviderChoice> providerChoices() {
        return registry().providers().stream()
                .map(provider -> new ProviderChoice(provider.id(), provider.specification().labelKey(),
                        provider.support().state()))
                .toList();
    }

    /** Finds one registered provider choice from the current trusted registry. */
    public Optional<ProviderChoice> providerChoice(ProviderId providerId) {
        return registry().find(Objects.requireNonNull(providerId, "providerId"))
                .map(provider -> new ProviderChoice(provider.id(), provider.specification().labelKey(),
                        provider.support().state()));
    }

    /** Returns categories declared by the requested registered provider. */
    public List<CategoryChoice> categoryChoices(ProviderId providerId) {
        return registry().find(Objects.requireNonNull(providerId, "providerId"))
                .map(provider -> provider.specification().categories().values().stream()
                        .map(category -> new CategoryChoice(category.id(), category.labelKey()))
                        .toList())
                .orElse(List.of());
    }

    public synchronized List<Message> create(String providerValue, String categoryValue) {
        final ProviderId providerId;
        try {
            providerId = ProviderId.parse(providerValue);
        } catch (IllegalArgumentException exception) {
            return List.of(new Message("bugreport.command.error.invalid_provider"));
        }
        RegisteredProvider provider = registry().find(providerId).orElse(null);
        if (provider == null) {
            return List.of(new Message("bugreport.command.error.unknown_provider", providerId.toString()));
        }
        if (provider.support().state() == ProviderSupportState.DISABLED) {
            return List.of(new Message("bugreport.command.error.provider_unavailable", providerId.toString()));
        }

        final CategoryId categoryId;
        try {
            categoryId = categoryValue == null ? null : CategoryId.of(categoryValue);
        } catch (IllegalArgumentException exception) {
            return List.of(new Message("bugreport.command.error.invalid_category"));
        }
        ReportSession session = new ReportSessionFactory(registry()).create(ReportSessionId.random(), providerId);
        if (categoryId != null) {
            try {
                session.selectCategory(categoryId);
            } catch (UnknownReportCategoryException exception) {
                return List.of(new Message("bugreport.command.error.unknown_category", categoryId.toString()));
            }
        }
        sessions.put(session.snapshot().id(), session);
        return List.of(new Message(
                "bugreport.command.create.success", session.snapshot().id().toString()));
    }

    public synchronized List<Message> open(String sessionValue) {
        ReportSession session = session(sessionValue);
        if (session == null) {
            return List.of(new Message("bugreport.command.error.unknown_session"));
        }
        ReportSessionSnapshot snapshot = session.snapshot();
        return List.of(new Message(
                "bugreport.command.open.summary",
                snapshot.id().toString(), snapshot.providerSpecification().id().toString(),
                snapshot.state().name()));
    }

    public synchronized List<Message> discard(String sessionValue) {
        ReportSessionId id = parseSessionId(sessionValue);
        if (id == null || !sessions.containsKey(id)) {
            return List.of(new Message("bugreport.command.error.unknown_session"));
        }
        ReportSession session = sessions.get(id);
        session.cancel(CancellationReason.USER_REQUESTED);
        sessions.remove(id);
        return List.of(new Message("bugreport.command.discard.success", id.toString()));
    }

    private ProviderRegistrySnapshot registry() {
        return Objects.requireNonNull(registrySupplier.get(), "provider registry");
    }

    private ReportSession session(String value) {
        ReportSessionId id = parseSessionId(value);
        return id == null ? null : sessions.get(id);
    }

    private static ReportSessionId parseSessionId(String value) {
        try {
            return ReportSessionId.parse(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Safe, localized command response without exception text or filesystem data. */
    public record Message(String translationKey, Object... arguments) {
        public Message {
            Objects.requireNonNull(translationKey, "translationKey");
            arguments = arguments == null ? new Object[0] : arguments.clone();
        }
    }

    public record ProviderChoice(ProviderId id, LocalizationKey labelKey,
            ProviderSupportState supportState) {}

    public record CategoryChoice(CategoryId id, LocalizationKey labelKey) {}
}
