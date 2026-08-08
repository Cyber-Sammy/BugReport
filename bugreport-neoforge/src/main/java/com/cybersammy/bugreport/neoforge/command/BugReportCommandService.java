package com.cybersammy.bugreport.neoforge.command;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import com.cybersammy.bugreport.core.session.CancellationReason;
import com.cybersammy.bugreport.core.session.ReportSession;
import com.cybersammy.bugreport.core.session.ReportSessionFactory;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.ReportSessionSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public synchronized List<Message> create(String providerValue, String categoryValue) {
        final ProviderId providerId;
        try {
            providerId = ProviderId.parse(providerValue);
        } catch (IllegalArgumentException exception) {
            return List.of(new Message("bugreport.command.error.invalid_provider"));
        }
        if (registry().find(providerId).isEmpty()) {
            return List.of(new Message("bugreport.command.error.unknown_provider", providerId.toString()));
        }

        ReportSession session;
        try {
            session = new ReportSessionFactory(registry()).create(ReportSessionId.random(), providerId);
            if (categoryValue != null) {
                session.selectCategory(CategoryId.of(categoryValue));
            }
        } catch (IllegalArgumentException exception) {
            return List.of(new Message("bugreport.command.error.invalid_category"));
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
        sessions.remove(id).cancel(CancellationReason.USER_REQUESTED);
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
}
