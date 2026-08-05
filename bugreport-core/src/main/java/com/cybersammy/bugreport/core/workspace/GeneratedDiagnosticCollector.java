package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.GeneratedArtifactId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticGeneratorSpecification;
import com.cybersammy.bugreport.api.specification.GeneratedDiagnosticRequest;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import java.util.Objects;

/** Executes one trusted provider generator through a private bounded workspace sink. */
public final class GeneratedDiagnosticCollector {
    public static final int PRODUCT_MAX_ARTIFACTS = 16;
    public static final long PRODUCT_MAX_BYTES_PER_ARTIFACT = 8L * 1024L * 1024L;
    public static final long PRODUCT_MAX_GENERATOR_BYTES = 32L * 1024L * 1024L;

    private GeneratedDiagnosticCollector() {}

    /**
     * Executes one generator referenced by a registered provider category.
     *
     * <p>The caller supplies the remaining report-wide byte authority. Timeout, execution
     * context handoff, and isolation between several generators belong to the category
     * executor built on top of this single-invocation boundary.
     */
    public static GeneratedDiagnosticResult collect(
            ProviderRegistrySnapshot registry,
            ProviderId providerId,
            CategoryId categoryId,
            DiagnosticGeneratorId generatorId,
            SupportedSide side,
            ReportWorkspace workspace,
            CancellationSignal cancellation,
            long remainingCollectionBytes) {
        GeneratedDiagnosticInvocation invocation = resolve(
                registry,
                providerId,
                categoryId,
                generatorId,
                side,
                workspace);
        if (remainingCollectionBytes < 0
                || remainingCollectionBytes
                        > FileCollectionCoordinator.PRODUCT_MAX_COLLECTION_BYTES) {
            throw new IllegalArgumentException(
                    "Remaining collection bytes must be within the report product ceiling");
        }
        CancellationSignal cancellationSignal =
                Objects.requireNonNull(cancellation, "cancellation");
        BoundedGeneratedDiagnosticSink sink = new BoundedGeneratedDiagnosticSink(
                invocation,
                workspace,
                cancellationSignal,
                remainingCollectionBytes);
        try {
            requireNotCancelled(invocation, workspace, cancellationSignal, null);
            invocation.generator().producer().generate(
                    new GeneratedDiagnosticRequest(side, cancellationSignal), sink);
            return sink.finish();
        } catch (GeneratedSinkViolation exception) {
            throw sink.rollback(failure(
                    exception.code(),
                    invocation,
                    workspace,
                    exception.artifactId(),
                    exception.getMessage(),
                    exception));
        } catch (Exception exception) {
            throw sink.rollback(failure(
                    GeneratedDiagnosticCode.PROVIDER_FAILURE,
                    invocation,
                    workspace,
                    null,
                    "Generated diagnostic provider callback failed",
                    exception));
        }
    }

    static void requireNotCancelled(
            GeneratedDiagnosticInvocation invocation,
            ReportWorkspace workspace,
            CancellationSignal cancellation,
            GeneratedArtifactId artifactId) {
        if (cancellation.isCancellationRequested()) {
            throw new GeneratedSinkViolation(
                    GeneratedDiagnosticCode.CANCELLED,
                    artifactId,
                    "Generated diagnostic collection was cancelled");
        }
    }

    static GeneratedDiagnosticException failure(
            GeneratedDiagnosticCode code,
            GeneratedDiagnosticInvocation invocation,
            ReportWorkspace workspace,
            GeneratedArtifactId artifactId,
            String message,
            Throwable cause) {
        return new GeneratedDiagnosticException(
                code,
                workspace.sessionId(),
                invocation.provider().id(),
                invocation.category().id(),
                invocation.generator().id(),
                artifactId,
                message,
                cause);
    }

    private static GeneratedDiagnosticInvocation resolve(
            ProviderRegistrySnapshot registry,
            ProviderId providerId,
            CategoryId categoryId,
            DiagnosticGeneratorId generatorId,
            SupportedSide side,
            ReportWorkspace workspace) {
        ProviderRegistrySnapshot trustedRegistry = Objects.requireNonNull(registry, "registry");
        ProviderId requestedProvider = Objects.requireNonNull(providerId, "providerId");
        CategoryId requestedCategory = Objects.requireNonNull(categoryId, "categoryId");
        DiagnosticGeneratorId requestedGenerator =
                Objects.requireNonNull(generatorId, "generatorId");
        SupportedSide requestedSide = Objects.requireNonNull(side, "side");
        ReportWorkspace destination = Objects.requireNonNull(workspace, "workspace");
        RegisteredProvider provider = trustedRegistry.find(requestedProvider)
                .orElseThrow(() -> requestFailure(
                        GeneratedDiagnosticCode.PROVIDER_NOT_REGISTERED,
                        requestedProvider,
                        requestedCategory,
                        requestedGenerator,
                        destination,
                        "Cannot generate diagnostics for an unregistered provider"));
        if (provider.support().state() == ProviderSupportState.DISABLED) {
            throw requestFailure(
                    GeneratedDiagnosticCode.PROVIDER_DISABLED,
                    requestedProvider,
                    requestedCategory,
                    requestedGenerator,
                    destination,
                    "Cannot generate diagnostics for a disabled provider");
        }
        ProviderSpecification specification = provider.specification();
        CategorySpecification category = specification.categories().get(requestedCategory);
        if (category == null) {
            throw requestFailure(
                    GeneratedDiagnosticCode.CATEGORY_NOT_DECLARED,
                    requestedProvider,
                    requestedCategory,
                    requestedGenerator,
                    destination,
                    "Cannot generate diagnostics for an undeclared category");
        }
        DiagnosticGeneratorSpecification generator =
                specification.generators().get(requestedGenerator);
        if (generator == null) {
            throw requestFailure(
                    GeneratedDiagnosticCode.GENERATOR_NOT_DECLARED,
                    requestedProvider,
                    requestedCategory,
                    requestedGenerator,
                    destination,
                    "Cannot invoke an undeclared diagnostic generator");
        }
        if (!category.generatorIds().contains(requestedGenerator)) {
            throw requestFailure(
                    GeneratedDiagnosticCode.GENERATOR_NOT_USED_BY_CATEGORY,
                    requestedProvider,
                    requestedCategory,
                    requestedGenerator,
                    destination,
                    "Cannot invoke a generator outside the selected category");
        }
        if (!generator.supportedSides().contains(requestedSide)) {
            throw requestFailure(
                    GeneratedDiagnosticCode.UNSUPPORTED_SIDE,
                    requestedProvider,
                    requestedCategory,
                    requestedGenerator,
                    destination,
                    "Diagnostic generator does not support the current physical side");
        }
        return new GeneratedDiagnosticInvocation(specification, category, generator);
    }

    private static GeneratedDiagnosticException requestFailure(
            GeneratedDiagnosticCode code,
            ProviderId providerId,
            CategoryId categoryId,
            DiagnosticGeneratorId generatorId,
            ReportWorkspace workspace,
            String message) {
        return new GeneratedDiagnosticException(
                code,
                workspace.sessionId(),
                providerId,
                categoryId,
                generatorId,
                null,
                message,
                null);
    }
}
