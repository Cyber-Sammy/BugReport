package com.cybersammy.bugreport.core.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.FieldOptionId;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class FieldValueTest {
    @Test
    void defensivelyCopiesCollectionsInCanonicalOrder() {
        ArrayList<String> steps = new ArrayList<>(List.of("first"));
        HashSet<FieldOptionId> options =
                new HashSet<>(Set.of(FieldOptionId.of("second"), FieldOptionId.of("first")));

        FieldValue.TextList textList = new FieldValue.TextList(steps);
        FieldValue.MultiSelection selection = new FieldValue.MultiSelection(options);
        steps.add("mutated");
        options.clear();

        assertEquals(List.of("first"), textList.values());
        assertEquals(
                List.of(FieldOptionId.of("first"), FieldOptionId.of("second")),
                List.copyOf(selection.optionIds()));
        assertThrows(UnsupportedOperationException.class, () -> textList.values().clear());
        assertThrows(UnsupportedOperationException.class, () -> selection.optionIds().clear());
    }

    @Test
    void rejectsUnboundedOrUnsafeValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FieldValue.Text("x".repeat(FieldValue.MAX_TEXT_CODE_POINTS + 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FieldValue.Text("unsafe\u0000value"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FieldValue.Text("unpaired" + (char) 0xD83D));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FieldValue.TextList(
                        java.util.Collections.nCopies(FieldValue.MAX_TEXT_ITEMS + 1, "step")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FieldValue.MultiSelection(optionIds(FieldValue.MAX_SELECTION_ITEMS + 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FieldValue.IntegerNumber(
                        BigInteger.ONE.shiftLeft(FieldValue.MAX_INTEGER_BITS)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FieldValue.DecimalNumber(
                        new BigDecimal(BigInteger.TEN.pow(FieldValue.MAX_DECIMAL_COMPONENT + 1))));
    }

    @Test
    void submissionRejectsDuplicateAndExcessFields() {
        FormSubmission.Builder duplicate = FormSubmission.builder()
                .put(FieldId.of("summary"), new FieldValue.Text("first"));
        assertThrows(
                IllegalArgumentException.class,
                () -> duplicate.put(FieldId.of("summary"), new FieldValue.Text("second")));

        FormSubmission.Builder excessive = FormSubmission.builder();
        for (int index = 0; index < CategorySpecification.MAX_FIELDS; index++) {
            excessive.put(FieldId.of("field_" + index), new FieldValue.Checkbox(false));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> excessive.put(FieldId.of("overflow"), new FieldValue.Checkbox(false)));
    }

    private static Set<FieldOptionId> optionIds(int count) {
        HashSet<FieldOptionId> values = new HashSet<>();
        for (int index = 0; index < count; index++) {
            values.add(FieldOptionId.of("option_" + index));
        }
        return values;
    }
}
