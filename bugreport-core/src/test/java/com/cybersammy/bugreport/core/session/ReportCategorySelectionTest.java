package com.cybersammy.bugreport.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ReportCategorySelectionTest {
    private static final ReportSessionId SESSION_ID =
            new ReportSessionId(UUID.fromString("00000000-0000-4000-8000-000000000002"));
    private static final CategoryId GENERAL = CategoryId.of("general");
    private static final CategoryId CRASH = CategoryId.of("crash");

    @Test
    void firstSelectionUsesTrustedDeclarationAndStartsFormAtomically() {
        ProviderSpecification specification = specification();
        ReportSession session = session(specification);

        ReportSessionSnapshot selected = session.selectCategory(CRASH);

        assertEquals(ReportSessionState.FORM_IN_PROGRESS, selected.state());
        assertEquals(1, selected.revision());
        assertSame(specification.categories().get(CRASH), selected.selectedCategory().orElseThrow());
    }

    @Test
    void unknownCategoryLeavesSessionUnchangedWithTypedContext() {
        ReportSession session = session(specification());
        ReportSessionSnapshot before = session.snapshot();
        CategoryId unknown = CategoryId.of("missing");

        UnknownReportCategoryException exception =
                assertThrows(
                        UnknownReportCategoryException.class,
                        () -> session.selectCategory(unknown));

        assertEquals(SESSION_ID, exception.sessionId());
        assertEquals(ProviderId.parse("example_mod"), exception.providerId());
        assertEquals(unknown, exception.categoryId());
        assertEquals(before, session.snapshot());
    }

    @Test
    void repeatedSelectionIsIdempotentAndReplacementAdvancesRevision() {
        ProviderSpecification specification = specification();
        ReportSession session = session(specification);
        ReportSessionSnapshot first = session.selectCategory(GENERAL);

        ReportSessionSnapshot repeated = session.selectCategory(GENERAL);
        ReportSessionSnapshot replaced = session.selectCategory(CRASH);

        assertEquals(first, repeated);
        assertEquals(2, replaced.revision());
        assertSame(specification.categories().get(CRASH), replaced.selectedCategory().orElseThrow());
    }

    @Test
    void selectionRequiresFormBoundaryAndRetainsCategoryAcrossValidationRecovery() {
        ProviderSpecification specification = specification();
        ReportSession session = session(specification);
        session.selectCategory(GENERAL);
        session.transitionTo(ReportSessionState.FAILED_VALIDATION);
        ReportSessionSnapshot failed = session.snapshot();

        InvalidReportCategorySelectionStateException exception =
                assertThrows(
                        InvalidReportCategorySelectionStateException.class,
                        () -> session.selectCategory(CRASH));

        assertEquals(SESSION_ID, exception.sessionId());
        assertEquals(ReportSessionState.FAILED_VALIDATION, exception.state());
        assertEquals(CRASH, exception.categoryId());
        assertEquals(failed, session.snapshot());

        ReportSessionSnapshot editing =
                session.transitionTo(ReportSessionState.FORM_IN_PROGRESS);
        assertSame(specification.categories().get(GENERAL), editing.selectedCategory().orElseThrow());

        ReportSessionSnapshot replaced = session.selectCategory(CRASH);
        assertSame(specification.categories().get(CRASH), replaced.selectedCategory().orElseThrow());
    }

    private static ReportSession session(ProviderSpecification specification) {
        ProviderRegistrySnapshot registry = SessionProviderFixture.registry(specification);
        return new ReportSessionFactory(registry).create(SESSION_ID, specification.id());
    }

    private static ProviderSpecification specification() {
        return SessionProviderFixture.specificationBuilder("example_mod")
                .addCategory(
                        CategorySpecification.builder(
                                        CRASH,
                                        LocalizationKey.of(
                                                "example_mod.bugreport.category.crash"))
                                .build())
                .build();
    }
}
