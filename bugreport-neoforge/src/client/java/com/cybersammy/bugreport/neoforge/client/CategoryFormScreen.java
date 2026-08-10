package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.FieldOptionId;
import com.cybersammy.bugreport.api.specification.FieldOption;
import com.cybersammy.bugreport.api.specification.FieldSpecification;
import com.cybersammy.bugreport.api.validation.ValidationIssue;
import com.cybersammy.bugreport.core.form.FieldValue;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.form.ReportSeverity;
import com.cybersammy.bugreport.core.form.ReportSideContext;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Paged first-party editor for every declarative M1 field kind. */
final class CategoryFormScreen extends Screen {
    private static final int CONTROL_WIDTH = 280;
    private static final int MAX_EDIT_CHARACTERS = FieldValue.MAX_TEXT_CODE_POINTS;
    private static final int AUTOSAVE_DELAY_TICKS = 40;

    private final BugReportCommandService commands;
    private final String sessionId;
    private final Screen parent;
    private final List<FieldSpecification> fields;
    private final Map<FieldId, Object> draft = new LinkedHashMap<>();
    private final Map<FieldId, FieldOptionId> multiSelectCursors = new LinkedHashMap<>();
    private final Map<FieldId, Component> fieldErrors = new LinkedHashMap<>();
    private int page;
    private Component status;
    private boolean validationSuccessful;
    private long editGeneration = 1;
    private long savedGeneration;
    private int autosaveDelay = AUTOSAVE_DELAY_TICKS;
    private boolean saveInProgress;
    private Runnable afterSave;
    private boolean discardInProgress;
    private boolean confirmationInProgress;

    CategoryFormScreen(BugReportCommandService commands, String sessionId, Screen parent) {
        this(commands, sessionId, parent, FormSubmission.empty());
    }

    CategoryFormScreen(
            BugReportCommandService commands,
            String sessionId,
            Screen parent,
            FormSubmission recoveredSubmission) {
        super(Component.translatable("bugreport.screen.form.title"));
        this.commands = commands;
        this.sessionId = sessionId;
        this.parent = parent;
        BugReportCommandService.FormView form = commands.form(sessionId).orElseThrow();
        fields = List.copyOf(form.category().fields().values());
        for (FieldSpecification field : fields) {
            switch (field.kind()) {
                case CHECKBOX -> draft.put(field.id(), false);
                case MULTI_SELECT -> draft.put(field.id(), new TreeSet<FieldOptionId>());
                default -> {
                    // Absence is meaningful for all other editable field kinds.
                }
            }
        }
        restoreDraft(recoveredSubmission);
    }

    @Override
    protected void init() {
        int left = width / 2 - CONTROL_WIDTH / 2;
        if (!fields.isEmpty()) {
            addFieldControl(fields.get(page), left, 78);
        }

        Button previous = Button.builder(Component.translatable("bugreport.screen.form.previous"),
                        ignored -> changePage(-1))
                .bounds(left, height - 56, 64, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);

        Button next = Button.builder(Component.translatable("bugreport.screen.form.next"),
                        ignored -> changePage(1))
                .bounds(left + 72, height - 56, 64, 20).build();
        next.active = page + 1 < fields.size();
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(Component.translatable("bugreport.screen.form.validate"),
                        ignored -> validateForm())
                .bounds(left + 144, height - 56, 64, 20).build());
        Button continueButton = Button.builder(
                        Component.translatable("bugreport.screen.form.continue"),
                        ignored -> continueToPlan())
                .bounds(left + 216, height - 56, 64, 20).build();
        continueButton.active = validationSuccessful;
        addRenderableWidget(continueButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                        ignored -> saveAndReturn())
                .bounds(left, height - 30, 136, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> cancelSession())
                .bounds(left + 144, height - 30, 136, 20).build());
    }

    private void addFieldControl(FieldSpecification field, int left, int top) {
        switch (field.kind()) {
            case SINGLE_LINE_TEXT, INTEGER, DECIMAL -> addSingleLine(field, left, top);
            case MULTILINE_TEXT, REPRODUCTION_STEPS, EXPECTED_BEHAVIOR, ACTUAL_BEHAVIOR ->
                    addMultiline(field, left, top);
            case CHECKBOX -> addCheckbox(field, left, top);
            case SINGLE_SELECT -> addSingleSelect(field, left, top);
            case MULTI_SELECT -> addMultiSelect(field, left, top);
            case SEVERITY -> addSeverity(field, left, top);
            case SIDE_CONTEXT -> addSideContext(field, left, top);
            case READ_ONLY_INFORMATION -> {
                // The localized label and description rendered below are the complete control.
            }
        }
    }

    private void addSingleLine(FieldSpecification field, int left, int top) {
        EditBox input = new EditBox(font, left, top, CONTROL_WIDTH, 20,
                Component.translatable(field.labelKey().value()));
        input.setMaxLength(maximumCharacters(field));
        input.setValue((String) draft.getOrDefault(field.id(), ""));
        input.setResponder(value -> {
            draft.put(field.id(), value);
            markDirty(field.id());
        });
        addRenderableWidget(input);
    }

    private void addMultiline(FieldSpecification field, int left, int top) {
        MultiLineEditBox input = new MultiLineEditBox(font, left, top, CONTROL_WIDTH, 72,
                Component.translatable(field.labelKey().value()),
                Component.translatable("bugreport.screen.form.text_placeholder"));
        input.setCharacterLimit(maximumCharacters(field));
        input.setValue((String) draft.getOrDefault(field.id(), ""));
        input.setValueListener(value -> {
            draft.put(field.id(), value);
            markDirty(field.id());
        });
        addRenderableWidget(input);
    }

    private void addCheckbox(FieldSpecification field, int left, int top) {
        Button button = Button.builder(checkboxLabel(field), ignored -> {
            draft.put(field.id(), !Boolean.TRUE.equals(draft.get(field.id())));
            markDirty(field.id());
            ignored.setMessage(checkboxLabel(field));
        }).bounds(left, top, CONTROL_WIDTH, 20).build();
        addRenderableWidget(button);
    }

    private Component checkboxLabel(FieldSpecification field) {
        boolean checked = Boolean.TRUE.equals(draft.get(field.id()));
        return Component.translatable(checked
                ? "bugreport.screen.form.checkbox.checked"
                : "bugreport.screen.form.checkbox.unchecked");
    }

    private void addSingleSelect(FieldSpecification field, int left, int top) {
        Button button = Button.builder(selectionLabel(field), ignored -> {
            draft.put(field.id(), nextOption(field, (FieldOptionId) draft.get(field.id())));
            markDirty(field.id());
            ignored.setMessage(selectionLabel(field));
        }).bounds(left, top, CONTROL_WIDTH, 20).build();
        addRenderableWidget(button);
    }

    private Component selectionLabel(FieldSpecification field) {
        FieldOptionId selected = (FieldOptionId) draft.get(field.id());
        if (selected == null) {
            return Component.translatable("bugreport.screen.form.not_selected");
        }
        return Component.translatable(field.options().get(selected).labelKey().value());
    }

    @SuppressWarnings("unchecked")
    private void addMultiSelect(FieldSpecification field, int left, int top) {
        List<FieldOption> options = List.copyOf(field.options().values());
        FieldOptionId first = options.getFirst().id();
        draft.putIfAbsent(field.id(), new TreeSet<FieldOptionId>());
        FieldOptionId current = multiSelectCursors.get(field.id());
        if (current == null) {
            current = first;
            multiSelectCursors.put(field.id(), current);
        }

        Button option = Button.builder(optionLabel(field, current), ignored -> {
            FieldOptionId selected = multiSelectCursors.get(field.id());
            FieldOptionId next = nextDeclaredOption(field, selected);
            multiSelectCursors.put(field.id(), next);
            ignored.setMessage(optionLabel(field, next));
            rebuildWidgets();
        }).bounds(left, top, CONTROL_WIDTH, 20).build();
        addRenderableWidget(option);

        Button toggle = Button.builder(multiToggleLabel(field), ignored -> {
            Set<FieldOptionId> selected = (Set<FieldOptionId>) draft.get(field.id());
            FieldOptionId cursor = multiSelectCursors.get(field.id());
            if (!selected.add(cursor)) {
                selected.remove(cursor);
            }
            markDirty(field.id());
            ignored.setMessage(multiToggleLabel(field));
        }).bounds(left, top + 26, CONTROL_WIDTH, 20).build();
        addRenderableWidget(toggle);
    }

    @SuppressWarnings("unchecked")
    private Component multiToggleLabel(FieldSpecification field) {
        Set<FieldOptionId> selected = (Set<FieldOptionId>) draft.get(field.id());
        FieldOptionId cursor = multiSelectCursors.get(field.id());
        return Component.translatable(selected.contains(cursor)
                ? "bugreport.screen.form.multi.remove"
                : "bugreport.screen.form.multi.add",
                selected.size());
    }

    private void addSeverity(FieldSpecification field, int left, int top) {
        Button button = Button.builder(severityLabel(field), ignored -> {
            draft.put(field.id(), nextEnum(ReportSeverity.values(),
                    (ReportSeverity) draft.get(field.id())));
            markDirty(field.id());
            ignored.setMessage(severityLabel(field));
        }).bounds(left, top, CONTROL_WIDTH, 20).build();
        addRenderableWidget(button);
    }

    private Component severityLabel(FieldSpecification field) {
        ReportSeverity value = (ReportSeverity) draft.get(field.id());
        return value == null ? Component.translatable("bugreport.screen.form.not_selected")
                : Component.translatable(value.labelKey().value());
    }

    private void addSideContext(FieldSpecification field, int left, int top) {
        Button button = Button.builder(sideLabel(field), ignored -> {
            draft.put(field.id(), nextEnum(ReportSideContext.values(),
                    (ReportSideContext) draft.get(field.id())));
            markDirty(field.id());
            ignored.setMessage(sideLabel(field));
        }).bounds(left, top, CONTROL_WIDTH, 20).build();
        addRenderableWidget(button);
    }

    private Component sideLabel(FieldSpecification field) {
        ReportSideContext value = (ReportSideContext) draft.get(field.id());
        return value == null ? Component.translatable("bugreport.screen.form.not_selected")
                : Component.translatable(value.labelKey().value());
    }

    private void changePage(int offset) {
        page += offset;
        status = null;
        rebuildWidgets();
    }

    private void validateForm() {
        fieldErrors.clear();
        validationSuccessful = false;
        SubmissionAttempt attempt = buildSubmission();
        if (attempt.errorField() != null) {
            fieldErrors.put(attempt.errorField(), attempt.errorMessage());
            showFirstError();
            status = Component.translatable("bugreport.screen.form.invalid");
            rebuildWidgets();
            return;
        }

        BugReportCommandService.FormResult result = commands.submitForm(
                sessionId, attempt.submission());
        if (result.unknownSession()) {
            status = Component.translatable("bugreport.command.error.unknown_session");
            return;
        }
        applyValidation(result.validation());
        rebuildWidgets();
    }

    private void continueToPlan() {
        if (confirmationInProgress) {
            return;
        }
        persistThen(this::confirmAndContinue);
    }

    private void confirmAndContinue() {
        fieldErrors.clear();
        validationSuccessful = false;
        SubmissionAttempt attempt = buildSubmission();
        if (attempt.errorField() != null) {
            fieldErrors.put(attempt.errorField(), attempt.errorMessage());
            status = Component.translatable("bugreport.screen.form.invalid");
            showFirstError();
            rebuildWidgets();
            return;
        }
        FormSubmission submission = attempt.submission();
        confirmationInProgress = true;
        long generation = editGeneration;
        Thread.ofVirtual()
                .name("bugreport-form-confirmation")
                .start(
                        () -> {
                            BugReportCommandService.FormConfirmationResult result =
                                    commands.confirmForm(sessionId, submission);
                            Minecraft.getInstance()
                                    .execute(() -> presentConfirmation(result, generation));
                        });
    }

    private void presentConfirmation(
            BugReportCommandService.FormConfirmationResult result, long generation) {
        confirmationInProgress = false;
        if (discardInProgress || Minecraft.getInstance().screen != this) {
            return;
        }
        if (result.status() == BugReportCommandService.FormConfirmationStatus.ACCEPTED
                && generation != editGeneration) {
            commands.returnToForm(sessionId);
            requireFreshDraftPersistence();
            status = Component.translatable("bugreport.screen.form.changed_during_confirmation");
            rebuildWidgets();
            return;
        }
        if (result.status() == BugReportCommandService.FormConfirmationStatus.INVALID) {
            applyValidation(result.validation().orElseThrow());
            rebuildWidgets();
            return;
        }
        if (result.status() == BugReportCommandService.FormConfirmationStatus.UNKNOWN_SESSION) {
            status = Component.translatable("bugreport.command.error.unknown_session");
            rebuildWidgets();
            return;
        }
        if (result.status() == BugReportCommandService.FormConfirmationStatus.INVALID_STATE) {
            status = Component.translatable("bugreport.screen.form.error.invalid_state");
            rebuildWidgets();
            return;
        }
        if (result.status() == BugReportCommandService.FormConfirmationStatus.PERSISTENCE_FAILED) {
            status = Component.translatable("bugreport.screen.form.error.draft_save_failed");
            rebuildWidgets();
            return;
        }
        minecraft.setScreen(new CollectionPlanScreen(
                commands, result.planRequest().orElseThrow(), this));
    }

    private void applyValidation(com.cybersammy.bugreport.api.validation.ValidationResult validation) {
        for (ValidationIssue issue : validation.issues()) {
            fieldFor(issue).ifPresent(field -> fieldErrors.putIfAbsent(
                    field.id(), validationMessage(issue)));
        }
        status = Component.translatable(validation.isValid()
                ? "bugreport.screen.form.valid"
                : "bugreport.screen.form.invalid");
        validationSuccessful = validation.isValid();
        if (!validation.isValid()) {
            showFirstError();
        }
    }

    @SuppressWarnings("unchecked")
    private SubmissionAttempt buildSubmission() {
        FormSubmission.Builder submission = FormSubmission.builder();
        for (FieldSpecification field : fields) {
            Object raw = draft.get(field.id());
            try {
                switch (field.kind()) {
                    case SINGLE_LINE_TEXT, MULTILINE_TEXT, EXPECTED_BEHAVIOR, ACTUAL_BEHAVIOR -> {
                        if (raw != null) submission.put(field.id(), new FieldValue.Text((String) raw));
                    }
                    case REPRODUCTION_STEPS -> {
                        if (raw != null) submission.put(field.id(), new FieldValue.TextList(
                                Arrays.asList(((String) raw).split("\\R", -1))));
                    }
                    case CHECKBOX -> submission.put(field.id(),
                            new FieldValue.Checkbox(Boolean.TRUE.equals(raw)));
                    case SINGLE_SELECT -> {
                        if (raw != null) submission.put(field.id(),
                                new FieldValue.Selection((FieldOptionId) raw));
                    }
                    case MULTI_SELECT -> submission.put(field.id(),
                            new FieldValue.MultiSelection((Set<FieldOptionId>) raw));
                    case INTEGER -> {
                        if (raw != null && !((String) raw).isBlank()) submission.put(field.id(),
                                new FieldValue.IntegerNumber(new BigInteger(((String) raw).trim())));
                    }
                    case DECIMAL -> {
                        if (raw != null && !((String) raw).isBlank()) submission.put(field.id(),
                                new FieldValue.DecimalNumber(new BigDecimal(((String) raw).trim())));
                    }
                    case SEVERITY -> {
                        if (raw != null) submission.put(field.id(),
                                new FieldValue.Severity((ReportSeverity) raw));
                    }
                    case SIDE_CONTEXT -> {
                        if (raw != null) submission.put(field.id(),
                                new FieldValue.SideContext((ReportSideContext) raw));
                    }
                    case READ_ONLY_INFORMATION -> {
                        // Display-only declarations never enter FormSubmission.
                    }
                }
            } catch (IllegalArgumentException exception) {
                return SubmissionAttempt.failure(field.id(), Component.translatable(
                        field.kind() == com.cybersammy.bugreport.api.specification.FieldKind.INTEGER
                                || field.kind() == com.cybersammy.bugreport.api.specification.FieldKind.DECIMAL
                                ? "bugreport.screen.form.error.number_format"
                                : "bugreport.screen.form.error.invalid_value"));
            }
        }
        return SubmissionAttempt.success(submission.build());
    }

    private java.util.Optional<FieldSpecification> fieldFor(ValidationIssue issue) {
        String path = issue.path().toString();
        return fields.stream().filter(field -> path.equals("$.fields." + field.id().value())
                        || path.startsWith("$.fields." + field.id().value() + "["))
                .findFirst();
    }

    private static Component validationMessage(ValidationIssue issue) {
        String localCode = issue.code().value().replace("bugreport:field_", "");
        return Component.translatable("bugreport.screen.form.error." + localCode);
    }

    private void showFirstError() {
        for (int index = 0; index < fields.size(); index++) {
            if (fieldErrors.containsKey(fields.get(index).id())) {
                page = index;
                return;
            }
        }
    }

    private void markDirty(FieldId fieldId) {
        fieldErrors.remove(fieldId);
        status = null;
        validationSuccessful = false;
        editGeneration++;
        autosaveDelay = AUTOSAVE_DELAY_TICKS;
    }

    private static int maximumCharacters(FieldSpecification field) {
        if (field.kind()
                == com.cybersammy.bugreport.api.specification.FieldKind.REPRODUCTION_STEPS) {
            return MAX_EDIT_CHARACTERS;
        }
        return field.constraints().maximumLength().orElse(MAX_EDIT_CHARACTERS);
    }

    private static FieldOptionId nextOption(FieldSpecification field, FieldOptionId current) {
        if (current == null) {
            return field.options().keySet().iterator().next();
        }
        List<FieldOptionId> ids = List.copyOf(field.options().keySet());
        int index = ids.indexOf(current);
        return index + 1 == ids.size() ? null : ids.get(index + 1);
    }

    private static FieldOptionId nextDeclaredOption(FieldSpecification field, FieldOptionId current) {
        List<FieldOptionId> ids = List.copyOf(field.options().keySet());
        int index = ids.indexOf(current);
        return ids.get((index + 1) % ids.size());
    }

    private static Component optionLabel(FieldSpecification field, FieldOptionId optionId) {
        return Component.translatable(field.options().get(optionId).labelKey().value());
    }

    private static <T> T nextEnum(T[] values, T current) {
        if (current == null) return values[0];
        int index = List.of(values).indexOf(current);
        return index + 1 == values.length ? null : values[index + 1];
    }

    void discardSession(Consumer<Boolean> completion) {
        Objects.requireNonNull(completion, "completion");
        if (discardInProgress) {
            return;
        }
        discardInProgress = true;
        Thread.ofVirtual()
                .name("bugreport-draft-session-discard")
                .start(
                        () -> {
                            boolean discarded = "bugreport.command.discard.success"
                                    .equals(
                                            commands.discard(sessionId)
                                                    .getFirst()
                                                    .translationKey());
                            Minecraft.getInstance()
                                    .execute(
                                            () -> {
                                                discardInProgress = false;
                                                completion.accept(discarded);
                                            });
                        });
    }

    void requireFreshDraftPersistence() {
        editGeneration++;
        autosaveDelay = 0;
    }

    private void saveAndReturn() {
        if (confirmationInProgress) {
            return;
        }
        persistThen(() -> minecraft.setScreen(parent));
    }

    private void persistThen(Runnable action) {
        afterSave = Objects.requireNonNull(action, "action");
        startDraftSave(true);
    }

    private void startDraftSave(boolean reportInvalidInput) {
        if (saveInProgress) {
            return;
        }
        if (savedGeneration == editGeneration) {
            runAfterSave();
            return;
        }
        SubmissionAttempt attempt = buildSubmission();
        if (attempt.errorField() != null) {
            autosaveDelay = AUTOSAVE_DELAY_TICKS;
            if (reportInvalidInput) {
                fieldErrors.put(attempt.errorField(), attempt.errorMessage());
                showFirstError();
                status = Component.translatable("bugreport.screen.form.invalid");
                rebuildWidgets();
            }
            return;
        }
        saveInProgress = true;
        long generation = editGeneration;
        FormSubmission submission = attempt.submission();
        Thread.ofVirtual()
                .name("bugreport-draft-save")
                .start(
                        () -> {
                            BugReportCommandService.DraftSaveStatus result =
                                    commands.saveFormDraft(sessionId, submission);
                            Minecraft.getInstance()
                                    .execute(() -> finishDraftSave(result, generation));
                        });
    }

    private void finishDraftSave(
            BugReportCommandService.DraftSaveStatus result, long generation) {
        saveInProgress = false;
        if (result != BugReportCommandService.DraftSaveStatus.SAVED
                && result != BugReportCommandService.DraftSaveStatus.UNAVAILABLE) {
            afterSave = null;
            autosaveDelay = AUTOSAVE_DELAY_TICKS;
            status = Component.translatable("bugreport.screen.form.error.draft_save_failed");
            rebuildWidgets();
            return;
        }
        savedGeneration = Math.max(savedGeneration, generation);
        if (savedGeneration != editGeneration) {
            autosaveDelay = afterSave == null ? AUTOSAVE_DELAY_TICKS : 0;
            if (afterSave != null) {
                startDraftSave(true);
            }
            return;
        }
        runAfterSave();
    }

    private void runAfterSave() {
        Runnable action = afterSave;
        afterSave = null;
        if (action != null) {
            action.run();
        }
    }

    private void cancelSession() {
        discardSession(
                discarded -> {
                    if (discarded) {
                        minecraft.setScreen(null);
                    } else {
                        status = Component.translatable(
                                "bugreport.command.error.draft_discard_failed");
                        rebuildWidgets();
                    }
                });
    }

    @Override
    public void onClose() {
        saveAndReturn();
    }

    @Override
    public void tick() {
        super.tick();
        if (savedGeneration == editGeneration || saveInProgress || afterSave != null) {
            return;
        }
        if (autosaveDelay > 0) {
            autosaveDelay--;
        }
        if (autosaveDelay == 0) {
            startDraftSave(false);
        }
    }

    private void restoreDraft(FormSubmission recoveredSubmission) {
        Objects.requireNonNull(recoveredSubmission, "recoveredSubmission")
                .values()
                .forEach(
                        (fieldId, value) -> {
                            if (value instanceof FieldValue.Text text) {
                                draft.put(fieldId, text.value());
                            } else if (value instanceof FieldValue.TextList list) {
                                draft.put(fieldId, String.join("\n", list.values()));
                            } else if (value instanceof FieldValue.Checkbox checkbox) {
                                draft.put(fieldId, checkbox.checked());
                            } else if (value instanceof FieldValue.Selection selection) {
                                draft.put(fieldId, selection.optionId());
                            } else if (value instanceof FieldValue.MultiSelection selection) {
                                draft.put(fieldId, new TreeSet<>(selection.optionIds()));
                            } else if (value instanceof FieldValue.IntegerNumber number) {
                                draft.put(fieldId, number.value().toString());
                            } else if (value instanceof FieldValue.DecimalNumber number) {
                                draft.put(fieldId, number.value().toString());
                            } else if (value instanceof FieldValue.Severity severity) {
                                draft.put(fieldId, severity.value());
                            } else if (value instanceof FieldValue.SideContext sideContext) {
                                draft.put(fieldId, sideContext.value());
                            }
                        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        if (fields.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("bugreport.screen.form.no_fields"),
                    width / 2, 70, 0xA0A0A0);
        } else {
            FieldSpecification field = fields.get(page);
            Component label = Component.translatable(field.labelKey().value())
                    .append(field.required() ? Component.literal(" *") : Component.empty());
            graphics.drawCenteredString(font, label, width / 2, 42, 0xFFFFFF);
            field.descriptionKey().ifPresent(description -> graphics.drawCenteredString(
                    font, Component.translatable(description.value()), width / 2, 58, 0xA0A0A0));
            graphics.drawCenteredString(font,
                    Component.translatable("bugreport.screen.form.page", page + 1, fields.size()),
                    width / 2, height - 70, 0xA0A0A0);
            Component error = fieldErrors.get(field.id());
            if (error != null) {
                graphics.drawCenteredString(font, error, width / 2, 158, 0xFF6060);
            }
        }
        if (status != null) {
            graphics.drawCenteredString(font, status, width / 2, 180,
                    validationSuccessful ? 0x60FF60 : 0xFF6060);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private record SubmissionAttempt(
            FormSubmission submission, FieldId errorField, Component errorMessage) {
        private static SubmissionAttempt success(FormSubmission submission) {
            return new SubmissionAttempt(submission, null, null);
        }

        private static SubmissionAttempt failure(FieldId field, Component message) {
            return new SubmissionAttempt(null, field, message);
        }
    }
}
