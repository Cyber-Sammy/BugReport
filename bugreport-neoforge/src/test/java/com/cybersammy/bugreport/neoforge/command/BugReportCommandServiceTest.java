package com.cybersammy.bugreport.neoforge.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.core.transport.NeoForgeLocalExportTransportAdapter;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.FieldOptionId;
import com.cybersammy.bugreport.api.identifier.NamespaceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.specification.FieldKind;
import com.cybersammy.bugreport.api.specification.FieldOption;
import com.cybersammy.bugreport.api.specification.FieldSpecification;
import com.cybersammy.bugreport.api.validation.ValidationPath;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.specification.InclusionDefault;
import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import com.cybersammy.bugreport.api.specification.ReportQualityRole;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import com.cybersammy.bugreport.core.registry.ProviderRegistry;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.CategorySourcePlan;
import com.cybersammy.bugreport.core.source.CategorySourcePlanner;
import com.cybersammy.bugreport.core.source.ReviewedCollectionPlan;
import com.cybersammy.bugreport.core.source.ScreenshotCollectionRequest;
import com.cybersammy.bugreport.core.workspace.CollectionRunControl;
import com.cybersammy.bugreport.core.workspace.FileCollectionCoordinator;
import com.cybersammy.bugreport.core.workspace.FileCollectionResult;
import com.cybersammy.bugreport.core.workspace.FileReportWorkspaceStore;
import com.cybersammy.bugreport.core.form.FieldValue;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.form.ReportSeverity;
import com.cybersammy.bugreport.core.form.ReportSideContext;
import com.cybersammy.bugreport.core.draft.FileDraftStore;
import com.cybersammy.bugreport.core.draft.DraftLoadBatch;
import com.cybersammy.bugreport.core.draft.ReportDraft;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.ReportSessionState;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BugReportCommandServiceTest {

    @Test
    void screenshotAwareCollectionIssuanceIsNotPublicApplicationApi() throws Exception {
        var method = BugReportCommandService.class.getDeclaredMethod(
                "beginCollectionWithScreenshots", String.class, ScreenshotCollectionRequest.class);
        assertFalse(Modifier.isPublic(method.getModifiers()));
        assertFalse(Modifier.isPublic(BugReportCommandService.class
                .getDeclaredMethod(
                        "beginScreenshotCapture", String.class, ReviewedCollectionPlan.class)
                .getModifiers()));
        assertFalse(Modifier.isPublic(
                BugReportCommandService.ScreenshotCaptureRequest.class.getModifiers()));
        assertFalse(Modifier.isPublic(BugReportCommandService.class
                .getDeclaredMethod(
                        "acceptScreenshotCapture",
                        BugReportCommandService.ScreenshotCaptureRequest.class)
                .getModifiers()));
        assertTrue(java.util.Arrays.stream(
                        BugReportCommandService.ScreenshotCaptureRequest.class
                                .getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
        assertTrue(Modifier.isPublic(
                BugReportCommandService.class
                        .getDeclaredMethod("beginCollection", String.class)
                        .getModifiers()));
    }

    @Test
    void screenshotCaptureAuthorityIsExactSingleUseAndRevokedWithSession(
            @TempDir Path gameDirectory) throws Exception {
        Files.createDirectories(gameDirectory.resolve("logs"));
        Files.createDirectories(gameDirectory.resolve("crash-reports"));
        Files.createDirectories(gameDirectory.resolve("config"));
        ProviderRegistrySnapshot registry = screenshotRegistry();
        BugReportCommandService service = new BugReportCommandService(() -> registry);
        String sessionId = (String) service.create("screenshot_mod", "general")
                .getFirst()
                .arguments()[0];
        var planRequest = service.confirmForm(sessionId, FormSubmission.empty())
                .planRequest()
                .orElseThrow();
        var plan = new com.cybersammy.bugreport.core.source.CategoryCollectionPlanner(
                        registry,
                        ApprovedSourceRoots.forGameDirectory(gameDirectory.toAbsolutePath()),
                        SupportedSide.PHYSICAL_CLIENT)
                .plan(ProviderId.parse("screenshot_mod"), CategoryId.of("general"));
        var reviewed = ReviewedCollectionPlan.of(
                plan, Set.of(DiagnosticSourceId.of("screenshot")), Set.of());
        assertTrue(service.acceptCollectionPlan(planRequest, reviewed));

        var issued = service.beginScreenshotCapture(sessionId, reviewed).orElseThrow();
        var constructor = BugReportCommandService.ScreenshotCaptureRequest.class
                .getDeclaredConstructor(
                        ReportSessionId.class, long.class, ReviewedCollectionPlan.class);
        constructor.setAccessible(true);
        var synthetic = constructor.newInstance(
                ReportSessionId.parse(sessionId), service.form(sessionId).orElseThrow().revision(), reviewed);
        assertFalse(service.acceptScreenshotCapture(synthetic));
        assertTrue(service.acceptScreenshotCapture(issued));
        assertFalse(service.acceptScreenshotCapture(issued));

        var revoked = service.beginScreenshotCapture(sessionId, reviewed).orElseThrow();
        assertEquals("bugreport.command.discard.success", service.discard(sessionId)
                .getFirst()
                .translationKey());
        assertFalse(service.acceptScreenshotCapture(revoked));
    }
    private final BugReportCommandService commands =
            new BugReportCommandService(ProviderRegistrySnapshot::empty);

    @Test
    void helpUsesOneLocalizedSafeMessage() {
        assertEquals(
                List.of("bugreport.command.help"),
                commands.help().stream().map(BugReportCommandService.Message::translationKey).toList());
    }

    @Test
    void emptyRegistryIsReportedWithoutInternalDiagnostics() {
        assertEquals(
                List.of("bugreport.command.list.empty"),
                commands.listProviders().stream()
                        .map(BugReportCommandService.Message::translationKey)
                        .toList());
    }

    @Test
    void invalidIdentifiersAndUnknownSessionsAreSafeCommandErrors() {
        assertEquals(
                "bugreport.command.error.invalid_provider",
                commands.create("NOT A PROVIDER", null).getFirst().translationKey());
        assertEquals(
                "bugreport.command.error.unknown_session",
                commands.open("not-a-session").getFirst().translationKey());
        assertEquals(
                "bugreport.command.error.unknown_session",
                commands.discard("not-a-session").getFirst().translationKey());
    }

    @Test
    void createOpenAndDiscardUseTheTrustedRegistrySessionLifecycle() {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        BugReportCommandService.Message created = service.create("example_mod", "general").getFirst();

        assertEquals("bugreport.command.create.success", created.translationKey());
        String sessionId = (String) created.arguments()[0];
        assertEquals("bugreport.command.open.summary", service.open(sessionId).getFirst().translationKey());
        assertEquals("bugreport.command.discard.success", service.discard(sessionId).getFirst().translationKey());
        assertEquals("bugreport.command.error.unknown_session", service.open(sessionId).getFirst().translationKey());
    }

    @Test
    void discardRemovesAnActiveFormSessionFromTheCommandService() {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        String sessionId = (String) service.create("example_mod", "general")
                .getFirst().arguments()[0];
        assertTrue(service.form(sessionId).isPresent());

        assertEquals("bugreport.command.discard.success",
                service.discard(sessionId).getFirst().translationKey());
        assertEquals("bugreport.command.error.unknown_session",
                service.open(sessionId).getFirst().translationKey());
        assertTrue(service.form(sessionId).isEmpty());
    }

    @Test
    void categoryErrorsKeepMalformedAndUndeclaredInputsDistinct() {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        assertEquals("bugreport.command.error.invalid_category",
                service.create("example_mod", "not a category").getFirst().translationKey());
        BugReportCommandService.Message unknown = service.create("example_mod", "other").getFirst();
        assertEquals("bugreport.command.error.unknown_category", unknown.translationKey());
        assertEquals("other", unknown.arguments()[0]);
    }

    @Test
    void trustedFormSupportsEveryEditableFieldKindWithoutMutatingTheSession() {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        String sessionId = (String) service.create("example_mod", "general")
                .getFirst().arguments()[0];
        BugReportCommandService.FormView before = service.form(sessionId).orElseThrow();

        FormSubmission submission = validSubmission();

        BugReportCommandService.FormResult first = service.submitForm(sessionId, submission);
        BugReportCommandService.FormResult second = service.submitForm(sessionId, submission);
        BugReportCommandService.FormView after = service.form(sessionId).orElseThrow();

        assertTrue(first.validation().isValid());
        assertTrue(second.validation().isValid());
        assertEquals(before.state(), after.state());
        assertEquals(before.revision(), after.revision());
        assertEquals(CategoryId.of("general"), after.category().id());

        BugReportCommandService.FormConfirmationResult confirmation =
                service.confirmForm(sessionId, submission);
        BugReportCommandService.FormView planned = service.form(sessionId).orElseThrow();
        assertEquals(BugReportCommandService.FormConfirmationStatus.ACCEPTED,
                confirmation.status());
        assertEquals("example_mod", confirmation.planRequest().orElseThrow().providerId().toString());
        assertEquals(CategoryId.of("general"),
                confirmation.planRequest().orElseThrow().categoryId());
        assertEquals(com.cybersammy.bugreport.core.session.ReportSessionState.COLLECTION_PLANNED,
                planned.state());
        assertEquals(after.revision() + 1, planned.revision());
        assertEquals(submission, service.confirmedForm(sessionId).orElseThrow());

        assertTrue(service.returnToForm(sessionId));
        assertEquals(com.cybersammy.bugreport.core.session.ReportSessionState.FORM_IN_PROGRESS,
                service.form(sessionId).orElseThrow().state());
        assertTrue(service.confirmedForm(sessionId).isEmpty());
    }

    @Test
    void collectionPlanIsAcceptedOnlyForTheConfirmedCollectionPlannedSession(
            @TempDir Path gameDirectory) {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        String sessionId = (String) service.create("example_mod", "general")
                .getFirst().arguments()[0];
        CategorySourcePlan plan = emptyCategoryPlan(gameDirectory);
        assertFalse(service.acceptCollectionPlan(
                new BugReportCommandService.CollectionPlanRequest(
                        com.cybersammy.bugreport.core.session.ReportSessionId.parse(sessionId),
                        0,
                        ProviderId.parse("example_mod"),
                        ProviderVersion.parse("1.0.0"),
                        CategoryId.of("general")),
                ReviewedCollectionPlan.defaults(plan)));

        BugReportCommandService.CollectionPlanRequest request = service
                .confirmForm(sessionId, validSubmission()).planRequest().orElseThrow();
        ReviewedCollectionPlan reviewed = ReviewedCollectionPlan.defaults(plan);
        assertTrue(service.acceptCollectionPlan(request, reviewed));
        assertSame(reviewed, service.collectionPlan(sessionId).orElseThrow());

        assertTrue(service.returnToForm(sessionId));
        assertTrue(service.collectionPlan(sessionId).isEmpty());
    }

    @Test
    void rejectsAStalePlanFromAnEarlierFormConfirmation(@TempDir Path gameDirectory) {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        String sessionId = (String) service.create("example_mod", "general")
                .getFirst().arguments()[0];
        CategorySourcePlan plan = emptyCategoryPlan(gameDirectory);

        BugReportCommandService.CollectionPlanRequest first = service
                .confirmForm(sessionId, validSubmission()).planRequest().orElseThrow();
        assertTrue(service.returnToForm(sessionId));
        BugReportCommandService.CollectionPlanRequest second = service
                .confirmForm(sessionId, validSubmission()).planRequest().orElseThrow();

        assertFalse(service.acceptCollectionPlan(first, ReviewedCollectionPlan.defaults(plan)));
        assertTrue(service.acceptCollectionPlan(second, ReviewedCollectionPlan.defaults(plan)));
    }

    @Test
    void collectionBeginsOnlyOnceFromTheAcceptedReviewedPlan(@TempDir Path gameDirectory) {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        String sessionId = (String) service.create("example_mod", "general")
                .getFirst().arguments()[0];

        assertTrue(service.beginCollection(sessionId).isEmpty());
        BugReportCommandService.CollectionPlanRequest request = service
                .confirmForm(sessionId, validSubmission()).planRequest().orElseThrow();
        ReviewedCollectionPlan reviewed = ReviewedCollectionPlan.defaults(emptyCategoryPlan(gameDirectory));
        assertTrue(service.acceptCollectionPlan(request, reviewed));

        BugReportCommandService.CollectionExecutionRequest execution =
                service.beginCollection(sessionId).orElseThrow();
        assertSame(reviewed, execution.reviewedPlan());
        assertEquals(com.cybersammy.bugreport.core.session.ReportSessionState.COLLECTING,
                service.form(sessionId).orElseThrow().state());
        assertTrue(service.beginCollection(sessionId).isEmpty());
        assertFalse(service.returnToForm(sessionId));
    }

    @Test
    void acceptsOnlyTheCurrentCollectionExecutionResult(@TempDir Path gameDirectory) {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        String sessionId = (String) service.create("example_mod", "general")
                .getFirst().arguments()[0];
        BugReportCommandService.CollectionPlanRequest request = service
                .confirmForm(sessionId, validSubmission()).planRequest().orElseThrow();
        ReviewedCollectionPlan reviewed = ReviewedCollectionPlan.defaults(emptyCategoryPlan(gameDirectory));
        assertTrue(service.acceptCollectionPlan(request, reviewed));
        BugReportCommandService.CollectionExecutionRequest execution =
                service.beginCollection(sessionId).orElseThrow();
        var workspace = new FileReportWorkspaceStore(gameDirectory.resolve("workspaces").toAbsolutePath())
                .create(execution.sessionId());
        FileCollectionResult result = FileCollectionCoordinator.collect(
                reviewed.selectedFilePlan(),
                ApprovedSourceRoots.forGameDirectory(gameDirectory.toAbsolutePath()),
                workspace,
                new CollectionRunControl());

        assertTrue(service.acceptCollectionResult(execution, result, workspace));
        assertFalse(service.acceptCollectionResult(execution, result, workspace));
        assertSame(result, service.collectionResult(sessionId).orElseThrow());
        assertSame(workspace, service.collectionWorkspace(sessionId).orElseThrow());
        assertEquals(com.cybersammy.bugreport.core.session.ReportSessionState.SANITIZING,
                service.form(sessionId).orElseThrow().state());
    }

    @Test
    void advancesOnlyServiceIssuedSanitizationAndReviewAuthorityToReady(
            @TempDir Path gameDirectory)
            throws Exception {
        Files.createDirectories(gameDirectory.resolve("logs"));
        Files.createDirectories(gameDirectory.resolve("crash-reports"));
        Files.createDirectories(gameDirectory.resolve("config"));
        Files.writeString(
                gameDirectory.resolve("logs/client.log"),
                "Authorization: Bearer secret_token_123456\n");
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        String sessionId = (String) service.create("example_mod", "general")
                .getFirst().arguments()[0];
        var planRequest = service.confirmForm(sessionId, validSubmission())
                .planRequest().orElseThrow();
        ReviewedCollectionPlan reviewed = ReviewedCollectionPlan.of(
                emptyCategoryPlan(gameDirectory), Set.of(DiagnosticSourceId.of("client_log")));
        assertTrue(service.acceptCollectionPlan(planRequest, reviewed));
        var collection = service.beginCollection(sessionId).orElseThrow();
        var workspace = new FileReportWorkspaceStore(
                        gameDirectory.resolve("workspaces").toAbsolutePath())
                .create(collection.sessionId());
        FileCollectionResult files = FileCollectionCoordinator.collect(
                reviewed.selectedFilePlan(),
                ApprovedSourceRoots.forGameDirectory(gameDirectory.toAbsolutePath()),
                workspace,
                new CollectionRunControl());
        assertTrue(service.acceptCollectionResult(collection, files, workspace));

        var sanitization = service.beginSanitization(sessionId).orElseThrow();
        assertTrue(service.beginSanitization(sessionId).isEmpty());
        var sanitizationConstructor =
                BugReportCommandService.SanitizationExecutionRequest.class.getDeclaredConstructor(
                        com.cybersammy.bugreport.core.session.ReportSessionId.class,
                        long.class,
                        com.cybersammy.bugreport.core.session.ReportSessionSnapshot.class,
                        com.cybersammy.bugreport.core.workspace.CategoryCollectionResult.class,
                        com.cybersammy.bugreport.core.workspace.ReportWorkspace.class);
        sanitizationConstructor.setAccessible(true);
        var syntheticSanitization = sanitizationConstructor.newInstance(
                sanitization.sessionId(),
                sanitization.sanitizationRevision(),
                sanitization.session(),
                sanitization.collection(),
                sanitization.workspace());
        assertTrue(service.executeSanitization(
                syntheticSanitization, CancellationSignal.neverCancelled()).isEmpty());
        var review = service.executeSanitization(
                sanitization, CancellationSignal.neverCancelled()).orElseThrow();
        Set<String> includedArtifacts = Set.of(
                review.batch().artifacts().getFirst().artifactName());
        var reviewConstructor = BugReportCommandService.WorkspaceReviewRequest.class
                .getDeclaredConstructor(
                        com.cybersammy.bugreport.core.session.ReportSessionId.class,
                        long.class,
                        com.cybersammy.bugreport.core.session.ReportSessionSnapshot.class,
                        com.cybersammy.bugreport.core.workspace.WorkspaceReviewCoordinator
                                .SanitizationBatch.class);
        reviewConstructor.setAccessible(true);
        var syntheticReview = reviewConstructor.newInstance(
                review.sessionId(),
                review.reviewRevision(),
                review.session(),
                review.batch());
        assertTrue(service.confirmReview(
                syntheticReview,
                new BugReportCommandService.ReviewDecision(includedArtifacts, Set.of())).isEmpty());
        var prepared = service.confirmReview(
                review,
                new BugReportCommandService.ReviewDecision(includedArtifacts, Set.of()))
                .orElseThrow();

        assertSame(prepared, service.preparedSnapshot(sessionId).orElseThrow());
        String artifactName = prepared.artifacts().getFirst()
                .artifact().artifactName();
        assertEquals(
                "Authorization: <bearer-token>\n",
                Files.readString(workspace.directory().resolve(artifactName)));
        assertEquals(com.cybersammy.bugreport.core.session.ReportSessionState.READY,
                service.form(sessionId).orElseThrow().state());
        assertTrue(service.confirmReview(
                review,
                new BugReportCommandService.ReviewDecision(includedArtifacts, Set.of())).isEmpty());

        var exportPreparation = service.beginLocalExport(sessionId).orElseThrow();
        assertTrue(service.beginLocalExport(sessionId).isEmpty());
        var export = service.prepareLocalExport(exportPreparation).orElseThrow();
        assertEquals(3, export.summary().entryCount());
        assertFalse(service.executeLocalExport(
                export, gameDirectory, "../unsafe.bugreport.zip",
                new com.cybersammy.bugreport.core.transport.TransportRunControl()).isPresent());
        assertEquals(com.cybersammy.bugreport.core.session.ReportSessionState.READY,
                service.form(sessionId).orElseThrow().state());
        assertEquals(com.cybersammy.bugreport.core.transport.ReportTransportResult.Status.SUCCESS,
                service.executeLocalExport(
                                export, gameDirectory, "report.bugreport.zip",
                                new com.cybersammy.bugreport.core.transport.TransportRunControl())
                        .orElseThrow().status());
        assertTrue(Files.isRegularFile(gameDirectory.resolve("bugreport-exports/report.bugreport.zip")));
        assertEquals(com.cybersammy.bugreport.core.session.ReportSessionState.COMPLETED,
                service.form(sessionId).orElseThrow().state());
    }

    @Test
    void sanitizationAndReviewAuthorityTokensCannotBeConstructedByCallers() {
        assertTrue(java.util.Arrays.stream(
                        BugReportCommandService.SanitizationExecutionRequest.class
                                .getDeclaredConstructors())
                .allMatch(constructor -> java.lang.reflect.Modifier.isPrivate(
                        constructor.getModifiers())));
        assertTrue(java.util.Arrays.stream(
                        BugReportCommandService.LocalExportPreparationRequest.class
                                .getDeclaredConstructors())
                .allMatch(constructor -> java.lang.reflect.Modifier.isPrivate(
                        constructor.getModifiers())));
        assertTrue(java.util.Arrays.stream(BugReportCommandService.LocalExportRequest.class
                        .getDeclaredConstructors())
                .allMatch(constructor -> java.lang.reflect.Modifier.isPrivate(
                        constructor.getModifiers())));
        assertTrue(java.util.Arrays.stream(BugReportCommandService.ConfirmedLocalExport.class
                        .getDeclaredConstructors())
                .allMatch(constructor -> java.lang.reflect.Modifier.isPrivate(
                        constructor.getModifiers())));
        assertEquals(1, NeoForgeLocalExportTransportAdapter.class.getDeclaredMethods().length);
        assertEquals(BugReportCommandService.ConfirmedLocalExport.class,
                NeoForgeLocalExportTransportAdapter.class.getDeclaredMethods()[0]
                        .getParameterTypes()[0]);
        assertTrue(java.util.Arrays.stream(
                        BugReportCommandService.WorkspaceReviewRequest.class
                                .getDeclaredConstructors())
                .allMatch(constructor -> java.lang.reflect.Modifier.isPrivate(
                        constructor.getModifiers())));
    }

    @Test
    void rejectsAResultProducedFromDifferentSelectedFiles(@TempDir Path gameDirectory)
            throws Exception {
        Files.createDirectories(gameDirectory.resolve("logs"));
        Files.writeString(gameDirectory.resolve("logs/client.log"), "diagnostic data");
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        String sessionId = (String) service.create("example_mod", "general")
                .getFirst().arguments()[0];
        BugReportCommandService.CollectionPlanRequest request = service
                .confirmForm(sessionId, validSubmission()).planRequest().orElseThrow();
        CategorySourcePlan plan = emptyCategoryPlan(gameDirectory);
        ReviewedCollectionPlan withoutLog = ReviewedCollectionPlan.defaults(plan);
        assertTrue(service.acceptCollectionPlan(request, withoutLog));
        BugReportCommandService.CollectionExecutionRequest execution =
                service.beginCollection(sessionId).orElseThrow();
        ReviewedCollectionPlan withLog = ReviewedCollectionPlan.of(
                plan, Set.of(DiagnosticSourceId.of("client_log")));
        FileCollectionResult mismatched = FileCollectionCoordinator.collect(
                withLog.selectedFilePlan(),
                ApprovedSourceRoots.forGameDirectory(gameDirectory.toAbsolutePath()),
                new FileReportWorkspaceStore(gameDirectory.resolve("workspaces").toAbsolutePath())
                        .create(execution.sessionId()),
                new CollectionRunControl());

        assertFalse(service.acceptCollectionResult(execution, mismatched,
                new FileReportWorkspaceStore(gameDirectory.resolve("other-workspaces").toAbsolutePath())
                        .create(execution.sessionId())));
        assertEquals(com.cybersammy.bugreport.core.session.ReportSessionState.COLLECTING,
                service.form(sessionId).orElseThrow().state());
    }

    @Test
    void acceptsOnlyCombinedResultForExactReviewedGeneratorSelection(@TempDir Path gameDirectory)
            throws Exception {
        Files.createDirectories(gameDirectory.resolve("logs"));
        Files.createDirectories(gameDirectory.resolve("crash-reports"));
        Files.createDirectories(gameDirectory.resolve("config"));
        java.util.concurrent.atomic.AtomicBoolean invoked =
                new java.util.concurrent.atomic.AtomicBoolean();
        ProviderRegistrySnapshot registry = generatedRegistry(invoked);
        BugReportCommandService service = new BugReportCommandService(() -> registry);
        String sessionId = (String) service.create("generated_mod", "general")
                .getFirst().arguments()[0];
        var request = service.confirmForm(sessionId, FormSubmission.empty())
                .planRequest().orElseThrow();
        var roots = ApprovedSourceRoots.forGameDirectory(gameDirectory.toAbsolutePath());
        var plan = new com.cybersammy.bugreport.core.source.CategoryCollectionPlanner(
                        registry, roots, SupportedSide.PHYSICAL_CLIENT)
                .plan(ProviderId.parse("generated_mod"), CategoryId.of("general"));
        var reviewed = ReviewedCollectionPlan.of(
                plan,
                Set.of(),
                Set.of(com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId.of("runtime")));
        assertTrue(service.acceptCollectionPlan(request, reviewed));
        var execution = service.beginCollection(sessionId).orElseThrow();
        var workspace = new FileReportWorkspaceStore(
                        gameDirectory.resolve("workspaces").toAbsolutePath())
                .create(execution.sessionId());
        var otherReviewed = ReviewedCollectionPlan.of(
                plan,
                Set.of(),
                Set.of(com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId.of("other")));
        var otherWorkspace = new FileReportWorkspaceStore(
                        gameDirectory.resolve("other-workspaces").toAbsolutePath())
                .create(execution.sessionId());
        var otherResult =
                com.cybersammy.bugreport.core.workspace.CategoryCollectionCoordinator.collect(
                        registry,
                        otherReviewed,
                        roots,
                        SupportedSide.PHYSICAL_CLIENT,
                        otherWorkspace,
                        new com.cybersammy.bugreport.core.workspace.CategoryCollectionRunControl(),
                        command -> false);
        var resultConstructor = com.cybersammy.bugreport.core.workspace.CategoryCollectionResult.class
                .getDeclaredConstructor(
                        com.cybersammy.bugreport.core.workspace.CategoryCollectionResult.Status.class,
                        FileCollectionResult.class,
                        com.cybersammy.bugreport.core.workspace.CategoryGeneratedDiagnosticResult.class,
                        com.cybersammy.bugreport.core.source.CategoryCollectionFingerprint.class);
        resultConstructor.setAccessible(true);
        var syntheticOtherResult = resultConstructor.newInstance(
                otherResult.status(),
                otherResult.files(),
                otherResult.generated(),
                execution.planFingerprint());
        assertFalse(service.acceptCollectionResult(execution, syntheticOtherResult, workspace));
        assertEquals(
                com.cybersammy.bugreport.core.session.ReportSessionState.COLLECTING,
                service.form(sessionId).orElseThrow().state());
        var result = com.cybersammy.bugreport.core.workspace.CategoryCollectionCoordinator.collect(
                registry,
                reviewed,
                roots,
                SupportedSide.PHYSICAL_CLIENT,
                workspace,
                new com.cybersammy.bugreport.core.workspace.CategoryCollectionRunControl(),
                command -> false);

        assertTrue(service.acceptCollectionResult(execution, result, workspace));
        assertTrue(invoked.get());
        assertSame(result, service.categoryCollectionResult(sessionId).orElseThrow());
        assertEquals(1, result.generated().outcomes().size());

        var sanitization = service.beginSanitization(sessionId).orElseThrow();
        var review = service.executeSanitization(
                        sanitization, CancellationSignal.neverCancelled())
                .orElseThrow();
        String artifactName = review.batch().artifacts().getFirst().artifactName();
        service.confirmReview(
                        review,
                        new BugReportCommandService.ReviewDecision(
                                Set.of(artifactName), Set.of()))
                .orElseThrow();
        var export = service.prepareLocalExport(
                        service.beginLocalExport(sessionId).orElseThrow())
                .orElseThrow();
        assertEquals(
                com.cybersammy.bugreport.core.transport.ReportTransportResult.Status.SUCCESS,
                service.executeLocalExport(
                                export,
                                gameDirectory,
                                "generated.bugreport.zip",
                                new com.cybersammy.bugreport.core.transport.TransportRunControl())
                        .orElseThrow()
                        .status());
        Path archive = gameDirectory.resolve("bugreport-exports/generated.bugreport.zip");
        try (var zip = new java.util.zip.ZipFile(archive.toFile())) {
            byte[] manifestBytes;
            try (var input = zip.getInputStream(zip.getEntry("manifest.json"))) {
                manifestBytes = input.readAllBytes();
            }
            var manifest = com.cybersammy.bugreport.core.manifest.ReportManifestJsonCodec
                    .decode(manifestBytes)
                    .manifest();
            var generatedEntry = manifest.entries().stream()
                    .filter(entry -> entry.archivePath().equals("content/" + artifactName))
                    .findFirst()
                    .orElseThrow();
            assertEquals(
                    com.cybersammy.bugreport.core.manifest.ManifestDeclarationKind.GENERATOR,
                    generatedEntry.provenances().getFirst().declarationKind());
            assertEquals("runtime", generatedEntry.provenances().getFirst().declarationId());
        }
    }

    @Test
    void formLookupAndSubmissionFailClosedForUnknownSession() {
        assertTrue(commands.form("not-a-session").isEmpty());
        BugReportCommandService.FormResult result = commands.submitForm(
                "not-a-session", FormSubmission.empty());
        assertTrue(result.unknownSession());
        assertEquals(null, result.validation());
    }

    @Test
    void invalidFormReturnsStableFieldCodeAndPathWithoutChangingSession() {
        BugReportCommandService service = new BugReportCommandService(BugReportCommandServiceTest::registry);
        String sessionId = (String) service.create("example_mod", "general")
                .getFirst().arguments()[0];
        BugReportCommandService.FormView before = service.form(sessionId).orElseThrow();

        BugReportCommandService.FormResult result = service.submitForm(
                sessionId, FormSubmission.empty());
        BugReportCommandService.FormView after = service.form(sessionId).orElseThrow();

        assertFalse(result.validation().isValid());
        assertEquals("bugreport:field_required",
                result.validation().issues().getFirst().code().value());
        assertEquals(ValidationPath.root().property("fields").property("single"),
                result.validation().issues().getFirst().path());
        assertEquals(before.state(), after.state());
        assertEquals(before.revision(), after.revision());

        BugReportCommandService.FormConfirmationResult confirmation =
                service.confirmForm(sessionId, FormSubmission.empty());
        assertEquals(BugReportCommandService.FormConfirmationStatus.INVALID,
                confirmation.status());
        assertEquals(before.state(), service.form(sessionId).orElseThrow().state());
        assertTrue(service.confirmedForm(sessionId).isEmpty());
    }

    @Test
    void restartRecoversTypedFormButNoCollectionOrDeliveryAuthority(@TempDir Path directory) {
        FileReportDraftPersistence persistence =
                new FileReportDraftPersistence(new FileDraftStore(directory.resolve("drafts")));
        BugReportCommandService first = new BugReportCommandService(
                BugReportCommandServiceTest::registry,
                BugReportCommandService.ReportHistoryRecorder.empty(),
                persistence);
        String sessionId = (String) first.create("example_mod", "general")
                .getFirst().arguments()[0];
        FormSubmission submission = validSubmission();

        assertEquals(
                BugReportCommandService.DraftSaveStatus.SAVED,
                first.saveFormDraft(sessionId, submission));

        BugReportCommandService restarted = new BugReportCommandService(
                BugReportCommandServiceTest::registry,
                BugReportCommandService.ReportHistoryRecorder.empty(),
                new FileReportDraftPersistence(new FileDraftStore(directory.resolve("drafts"))));
        BugReportCommandService.DraftRecoveryOverview recovery = restarted.draftRecovery();
        assertEquals(1, recovery.choices().size());
        assertEquals(
                BugReportCommandService.DraftRecoveryStatus.READY,
                recovery.choices().getFirst().status());

        BugReportCommandService.DraftResume resumed = restarted
                .resumeDraft(ReportSessionId.parse(sessionId))
                .orElseThrow();
        assertEquals(submission, resumed.formSubmission());
        assertEquals(ReportSessionState.FORM_IN_PROGRESS,
                restarted.form(sessionId).orElseThrow().state());
        assertTrue(restarted.beginCollection(sessionId).isEmpty());
        assertTrue(restarted.beginLocalExport(sessionId).isEmpty());
        assertTrue(restarted.submitForm(sessionId, resumed.formSubmission()).validation().isValid());
        assertEquals(
                BugReportCommandService.FormConfirmationStatus.PERSISTENCE_FAILED,
                restarted.confirmForm(sessionId, submission).status());
        assertEquals(
                BugReportCommandService.DraftSaveStatus.SAVED,
                restarted.saveFormDraft(sessionId, submission));
        assertEquals(
                BugReportCommandService.FormConfirmationStatus.ACCEPTED,
                restarted.confirmForm(sessionId, submission).status());
        assertFalse(Files.exists(directory.resolve("drafts").resolve(sessionId + ".json")));
        BugReportCommandService afterPlanningRestart = new BugReportCommandService(
                BugReportCommandServiceTest::registry,
                BugReportCommandService.ReportHistoryRecorder.empty(),
                new FileReportDraftPersistence(new FileDraftStore(directory.resolve("drafts"))));
        assertTrue(afterPlanningRestart.draftRecovery().choices().isEmpty());
    }

    @Test
    void failedDraftDeletionBlocksPlanningAndExplicitDiscard(@TempDir Path directory) {
        FailingDeleteDraftPersistence persistence =
                new FailingDeleteDraftPersistence(
                        new FileReportDraftPersistence(
                                new FileDraftStore(directory.resolve("drafts"))));
        BugReportCommandService service = new BugReportCommandService(
                BugReportCommandServiceTest::registry,
                BugReportCommandService.ReportHistoryRecorder.empty(),
                persistence);
        String sessionId = (String) service.create("example_mod", "general")
                .getFirst().arguments()[0];
        assertEquals(
                BugReportCommandService.DraftSaveStatus.SAVED,
                service.saveFormDraft(sessionId, validSubmission()));
        persistence.failDeletes = true;

        assertEquals(
                BugReportCommandService.FormConfirmationStatus.PERSISTENCE_FAILED,
                service.confirmForm(sessionId, validSubmission()).status());
        assertEquals(
                ReportSessionState.FORM_IN_PROGRESS,
                service.form(sessionId).orElseThrow().state());
        assertEquals(
                "bugreport.command.error.draft_discard_failed",
                service.discard(sessionId).getFirst().translationKey());
        assertEquals(
                "bugreport.command.open.summary",
                service.open(sessionId).getFirst().translationKey());
        assertTrue(Files.exists(directory.resolve("drafts").resolve(sessionId + ".json")));

        persistence.failDeletes = false;
        assertEquals(
                "bugreport.command.discard.success",
                service.discard(sessionId).getFirst().translationKey());
        assertFalse(Files.exists(directory.resolve("drafts").resolve(sessionId + ".json")));
    }

    @Test
    void corruptAndUnavailableDraftsAreIsolatedAndExplicitlyDiscardable(
            @TempDir Path directory) throws Exception {
        Path root = directory.resolve("drafts");
        Files.createDirectories(root);
        ReportSessionId corruptId =
                ReportSessionId.parse("00000000-0000-4000-8000-000000000301");
        ReportSessionId missingProviderId =
                ReportSessionId.parse("00000000-0000-4000-8000-000000000302");
        ReportSessionId missingCategoryId =
                ReportSessionId.parse("00000000-0000-4000-8000-000000000303");
        Files.writeString(root.resolve(corruptId + ".json"), "{");
        new FileDraftStore(root).save(new ReportDraft(
                missingProviderId,
                1,
                ProviderId.parse("missing_mod"),
                ProviderVersion.parse("1.0.0"),
                Optional.of(CategoryId.of("general")),
                ReportSessionState.FORM_IN_PROGRESS,
                FormSubmission.empty()));
        new FileDraftStore(root).save(new ReportDraft(
                missingCategoryId,
                1,
                ProviderId.parse("example_mod"),
                ProviderVersion.parse("1.0.0"),
                Optional.of(CategoryId.of("removed")),
                ReportSessionState.FORM_IN_PROGRESS,
                FormSubmission.empty()));
        BugReportCommandService service = new BugReportCommandService(
                BugReportCommandServiceTest::registry,
                BugReportCommandService.ReportHistoryRecorder.empty(),
                new FileReportDraftPersistence(new FileDraftStore(root)));

        List<BugReportCommandService.DraftRecoveryChoice> choices =
                service.draftRecovery().choices();
        assertEquals(3, choices.size());
        assertTrue(choices.stream().anyMatch(choice ->
                choice.sessionId().equals(corruptId)
                        && choice.status()
                                == BugReportCommandService.DraftRecoveryStatus.FILE_REJECTED));
        assertTrue(choices.stream().anyMatch(choice ->
                choice.sessionId().equals(missingProviderId)
                        && choice.status()
                                == BugReportCommandService.DraftRecoveryStatus.PROVIDER_MISSING));
        assertTrue(choices.stream().anyMatch(choice ->
                choice.sessionId().equals(missingCategoryId)
                        && choice.status()
                                == BugReportCommandService.DraftRecoveryStatus.CATEGORY_MISSING));
        assertTrue(service.discardRecoveredDraft(corruptId));
        assertTrue(service.discardRecoveredDraft(missingProviderId));
        assertTrue(service.discardRecoveredDraft(missingCategoryId));
        assertTrue(service.draftRecovery().choices().isEmpty());
        assertFalse(Files.exists(root.resolve(corruptId + ".json")));
        assertFalse(Files.exists(root.resolve(missingProviderId + ".json")));
        assertFalse(Files.exists(root.resolve(missingCategoryId + ".json")));
    }

    private static ProviderRegistrySnapshot registry() {
        return ProviderRegistry.createSnapshot(List.of(new DiscoveredProvider(
                NamespaceId.of("example_mod"), TestProvider.class.getName(), new TestProvider())));
    }

    private static ProviderRegistrySnapshot generatedRegistry(
            java.util.concurrent.atomic.AtomicBoolean invoked) {
        var generatorId =
                com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId.of("runtime");
        var generator = com.cybersammy.bugreport.api.specification.DiagnosticGeneratorSpecification
                .builder(generatorId, (request, sink) -> {
                    invoked.set(true);
                    sink.emitText(
                            com.cybersammy.bugreport.api.identifier.GeneratedArtifactId.of("state"),
                            "runtime state");
                })
                .labelKey(LocalizationKey.of("generated_mod.generator.runtime"))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .executionContext(com.cybersammy.bugreport.api.specification.GeneratorExecutionContext.WORKER)
                .constraints(com.cybersammy.bugreport.api.constraint.CollectionConstraints.builder()
                        .maxGeneratedArtifacts(1)
                        .maxBytesPerFile(4096)
                        .maxTotalBytes(4096)
                        .callbackTimeout(java.time.Duration.ofSeconds(1))
                        .build())
                .build();
        var otherGenerator =
                com.cybersammy.bugreport.api.specification.DiagnosticGeneratorSpecification
                        .builder(
                                com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId.of("other"),
                                (request, sink) -> sink.emitText(
                                        com.cybersammy.bugreport.api.identifier.GeneratedArtifactId
                                                .of("other"),
                                        "other runtime state"))
                        .labelKey(LocalizationKey.of("generated_mod.generator.other"))
                        .privacy(PrivacyClassification.PERSONAL)
                        .contentType(DiagnosticContentType.TEXT)
                        .supportSide(SupportedSide.PHYSICAL_CLIENT)
                        .executionContext(com.cybersammy.bugreport.api.specification
                                .GeneratorExecutionContext.WORKER)
                        .constraints(com.cybersammy.bugreport.api.constraint.CollectionConstraints
                                .builder()
                                .maxGeneratedArtifacts(1)
                                .maxBytesPerFile(4096)
                                .maxTotalBytes(4096)
                                .callbackTimeout(java.time.Duration.ofSeconds(1))
                                .build())
                        .build();
        var category = CategorySpecification.builder(
                        CategoryId.of("general"),
                        LocalizationKey.of("generated_mod.category.general"))
                .useGenerator(generatorId)
                .useGenerator(otherGenerator.id())
                .build();
        var specification = ProviderSpecification.builder(
                        ProviderId.parse("generated_mod"),
                        ProviderVersion.parse("1.0.0"),
                        LocalizationKey.of("generated_mod.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addGenerator(generator)
                .addGenerator(otherGenerator)
                .addCategory(category)
                .build();
        BugReportProvider provider = new BugReportProvider() {
            @Override public String providerId() { return "generated_mod"; }
            @Override public String providerVersion() { return "1.0.0"; }
            @Override public Optional<ProviderSpecification> specification() {
                return Optional.of(specification);
            }
        };
        return ProviderRegistry.createSnapshot(List.of(new DiscoveredProvider(
                NamespaceId.of("generated_mod"), "GeneratedProvider", provider)));
    }

    private static ProviderRegistrySnapshot screenshotRegistry() {
        DiagnosticSourceId sourceId = DiagnosticSourceId.of("screenshot");
        DiagnosticSourceSpecification source =
                DiagnosticSourceSpecification.userSelectedScreenshot(sourceId)
                        .labelKey(LocalizationKey.of("screenshot_mod.source.screenshot"))
                        .privacy(PrivacyClassification.SENSITIVE)
                        .contentType(DiagnosticContentType.BINARY)
                        .qualityRole(ReportQualityRole.OPTIONAL)
                        .supportSide(SupportedSide.PHYSICAL_CLIENT)
                        .build();
        ProviderSpecification specification = ProviderSpecification.builder(
                        ProviderId.parse("screenshot_mod"),
                        ProviderVersion.parse("1.0.0"),
                        LocalizationKey.of("screenshot_mod.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addSource(source)
                .addCategory(CategorySpecification.builder(
                                CategoryId.of("general"),
                                LocalizationKey.of("screenshot_mod.category.general"))
                        .useSource(sourceId)
                        .build())
                .build();
        BugReportProvider provider = new BugReportProvider() {
            @Override
            public String providerId() {
                return "screenshot_mod";
            }

            @Override
            public String providerVersion() {
                return "1.0.0";
            }

            @Override
            public Optional<ProviderSpecification> specification() {
                return Optional.of(specification);
            }
        };
        return ProviderRegistry.createSnapshot(List.of(new DiscoveredProvider(
                NamespaceId.of("screenshot_mod"), "ScreenshotProvider", provider)));
    }

    private static CategorySourcePlan emptyCategoryPlan(Path gameDirectory) {
        return new CategorySourcePlanner(
                registry(),
                ApprovedSourceRoots.forGameDirectory(gameDirectory.toAbsolutePath()),
                SupportedSide.PHYSICAL_CLIENT).plan(ProviderId.parse("example_mod"), CategoryId.of("general"));
    }

    private static FormSubmission validSubmission() {
        return FormSubmission.builder()
                .put(FieldId.of("single"), new FieldValue.Text("summary"))
                .put(FieldId.of("multi_line"), new FieldValue.Text("line one\nline two"))
                .put(FieldId.of("steps"), new FieldValue.TextList(List.of("first", "second")))
                .put(FieldId.of("checkbox"), new FieldValue.Checkbox(false))
                .put(FieldId.of("single_select"),
                        new FieldValue.Selection(FieldOptionId.of("one")))
                .put(FieldId.of("multi_select"),
                        new FieldValue.MultiSelection(Set.of(FieldOptionId.of("two"))))
                .put(FieldId.of("integer"), new FieldValue.IntegerNumber(BigInteger.TEN))
                .put(FieldId.of("decimal"), new FieldValue.DecimalNumber(new BigDecimal("10.5")))
                .put(FieldId.of("expected"), new FieldValue.Text("expected"))
                .put(FieldId.of("actual"), new FieldValue.Text("actual"))
                .put(FieldId.of("severity"), new FieldValue.Severity(ReportSeverity.HIGH))
                .put(FieldId.of("side"),
                        new FieldValue.SideContext(ReportSideContext.SINGLEPLAYER))
                .build();
    }

    private static final class TestProvider implements BugReportProvider {
        private static final DiagnosticSourceSpecification CLIENT_LOG =
                DiagnosticSourceSpecification.exactFile(
                                DiagnosticSourceId.of("client_log"),
                                LogicalRoot.GAME_LOGS,
                                RelativePath.of("client.log"))
                        .labelKey(LocalizationKey.of("example_mod.source.client_log"))
                        .privacy(PrivacyClassification.PERSONAL)
                        .contentType(DiagnosticContentType.TEXT)
                        .qualityRole(ReportQualityRole.OPTIONAL)
                        .inclusionDefault(InclusionDefault.EXCLUDED)
                        .supportSide(SupportedSide.PHYSICAL_CLIENT)
                        .build();
        private static final ProviderSpecification SPECIFICATION = ProviderSpecification.builder(
                        ProviderId.parse("example_mod"), ProviderVersion.parse("1.0.0"),
                        LocalizationKey.of("example_mod.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addSource(CLIENT_LOG)
                .addCategory(category())
                .build();

        @Override
        public String providerId() { return "example_mod"; }

        @Override
        public String providerVersion() { return "1.0.0"; }

        @Override
        public Optional<ProviderSpecification> specification() { return Optional.of(SPECIFICATION); }
    }

    private static CategorySpecification category() {
        CategorySpecification.Builder category = CategorySpecification.builder(
                CategoryId.of("general"), LocalizationKey.of("example_mod.category.general"));
        category.addField(field("single", FieldKind.SINGLE_LINE_TEXT, PrivacyClassification.PERSONAL));
        category.addField(field("multi_line", FieldKind.MULTILINE_TEXT, PrivacyClassification.PERSONAL));
        category.addField(field("steps", FieldKind.REPRODUCTION_STEPS, PrivacyClassification.PERSONAL));
        category.addField(field("checkbox", FieldKind.CHECKBOX, PrivacyClassification.LOW));
        category.addField(selectionField("single_select", FieldKind.SINGLE_SELECT));
        category.addField(selectionField("multi_select", FieldKind.MULTI_SELECT));
        category.addField(field("integer", FieldKind.INTEGER, PrivacyClassification.LOW));
        category.addField(field("decimal", FieldKind.DECIMAL, PrivacyClassification.LOW));
        category.addField(field("expected", FieldKind.EXPECTED_BEHAVIOR, PrivacyClassification.PERSONAL));
        category.addField(field("actual", FieldKind.ACTUAL_BEHAVIOR, PrivacyClassification.PERSONAL));
        category.addField(field("severity", FieldKind.SEVERITY, PrivacyClassification.LOW));
        category.addField(field("side", FieldKind.SIDE_CONTEXT, PrivacyClassification.LOW));
        category.addField(field("information", FieldKind.READ_ONLY_INFORMATION,
                PrivacyClassification.LOW));
        category.useSource(TestProvider.CLIENT_LOG.id());
        return category.build();
    }

    private static FieldSpecification field(
            String id, FieldKind kind, PrivacyClassification privacy) {
        return FieldSpecification.builder(FieldId.of(id), kind,
                        LocalizationKey.of("example_mod.field." + id), privacy)
                .required("single".equals(id))
                .build();
    }

    private static FieldSpecification selectionField(String id, FieldKind kind) {
        return FieldSpecification.builder(FieldId.of(id), kind,
                        LocalizationKey.of("example_mod.field." + id), PrivacyClassification.LOW)
                .addOption(new FieldOption(
                        FieldOptionId.of("one"), LocalizationKey.of("example_mod.option.one")))
                .addOption(new FieldOption(
                        FieldOptionId.of("two"), LocalizationKey.of("example_mod.option.two")))
                .build();
    }

    private static final class FailingDeleteDraftPersistence
            implements BugReportCommandService.ReportDraftPersistence {
        private final BugReportCommandService.ReportDraftPersistence delegate;
        private boolean failDeletes;

        private FailingDeleteDraftPersistence(
                BugReportCommandService.ReportDraftPersistence delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public void save(ReportDraft draft) {
            delegate.save(draft);
        }

        @Override
        public DraftLoadBatch loadAll() {
            return delegate.loadAll();
        }

        @Override
        public boolean delete(ReportSessionId sessionId) {
            if (failDeletes) {
                throw new IllegalStateException("injected delete failure");
            }
            return delegate.delete(sessionId);
        }
    }
}
