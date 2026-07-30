package com.cybersammy.bugreport.api.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.identifier.ExtensionMetadataKey;
import com.cybersammy.bugreport.api.identifier.IdentifierCollisionException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ExtensionMetadataTest {
    @Test
    void defensivelyCopiesAndCanonicallyOrdersMetadata() {
        ArrayList<ExtensionValue> sourceArray =
                new ArrayList<>(List.of(ExtensionValue.of("first")));
        LinkedHashMap<String, ExtensionValue> sourceObject = new LinkedHashMap<>();
        sourceObject.put("zeta", ExtensionValue.of(true));
        sourceObject.put("alpha", ExtensionValue.array(sourceArray));

        ExtensionMetadata metadata =
                ExtensionMetadata.builder()
                        .put(
                                ExtensionMetadataKey.of("example_mod:zeta"),
                                ExtensionValue.object(sourceObject))
                        .put(
                                ExtensionMetadataKey.of("example_mod:alpha"),
                                ExtensionValue.nullValue())
                        .build();
        sourceArray.add(ExtensionValue.of("later"));
        sourceObject.clear();

        assertEquals(
                List.of("example_mod:alpha", "example_mod:zeta"),
                metadata.values().keySet().stream().map(ExtensionMetadataKey::value).toList());
        ExtensionValue.ObjectValue objectValue =
                (ExtensionValue.ObjectValue)
                        metadata.values().get(ExtensionMetadataKey.of("example_mod:zeta"));
        assertEquals(List.of("alpha", "zeta"), objectValue.values().keySet().stream().toList());
        ExtensionValue.ArrayValue arrayValue =
                (ExtensionValue.ArrayValue) objectValue.values().get("alpha");
        assertEquals(1, arrayValue.values().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> metadata.values().clear());
    }

    @Test
    void rejectsDuplicateNamespacedKeys() {
        ExtensionMetadata.Builder builder =
                ExtensionMetadata.builder()
                        .put(
                                ExtensionMetadataKey.of("example_mod:details"),
                                ExtensionValue.of("first"));

        assertThrows(
                IdentifierCollisionException.class,
                () ->
                        builder.put(
                                ExtensionMetadataKey.of("example_mod:details"),
                                ExtensionValue.of("second")));
    }

    @Test
    void rejectsOversizedOrExcessivelyNestedValues() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ExtensionValue.of(
                                "a".repeat(
                                        ExtensionMetadata.MAX_STRING_BYTES + 1)));

        ExtensionValue nested = ExtensionValue.of("leaf");
        for (int depth = 0; depth <= ExtensionMetadata.MAX_DEPTH; depth++) {
            nested = ExtensionValue.array(List.of(nested));
        }
        ExtensionValue excessivelyNested = nested;

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ExtensionMetadata.builder()
                                .put(
                                        ExtensionMetadataKey.of("example_mod:nested"),
                                        excessivelyNested)
                                .build());
    }

    @Test
    void acceptsJsonCompatibleScalarTypes() {
        ExtensionMetadata metadata =
                ExtensionMetadata.builder()
                        .put(
                                ExtensionMetadataKey.of("example_mod:boolean"),
                                ExtensionValue.of(false))
                        .put(
                                ExtensionMetadataKey.of("example_mod:number"),
                                ExtensionValue.of(BigDecimal.valueOf(12.5)))
                        .put(
                                ExtensionMetadataKey.of("example_mod:object"),
                                ExtensionValue.object(Map.of("value", ExtensionValue.of("text"))))
                        .build();

        assertEquals(3, metadata.values().size());
    }

    @Test
    void rejectsUnboundedValuesBeforeTheyEnterMetadata() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ExtensionValue.array(
                                Collections.nCopies(
                                        ExtensionMetadata.MAX_CONTAINER_ENTRIES + 1,
                                        ExtensionValue.nullValue())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ExtensionValue.of(
                                new BigDecimal(
                                        "1".repeat(
                                        ExtensionMetadata.MAX_NUMBER_PRECISION + 1))));
    }

    @Test
    void countsUtf8BytesAndAggregateValuesExactly() {
        String fourByteCodePoint = "\uD83D\uDE80";
        ExtensionValue.of(
                fourByteCodePoint.repeat(ExtensionMetadata.MAX_STRING_BYTES / 4));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ExtensionValue.of(
                                fourByteCodePoint.repeat(
                                        ExtensionMetadata.MAX_STRING_BYTES / 4 + 1)));

        ExtensionMetadata.Builder builder = ExtensionMetadata.builder();
        for (int index = 0; index < 4; index++) {
            builder.put(
                    ExtensionMetadataKey.of("example_mod:values_" + index),
                    ExtensionValue.array(
                            Collections.nCopies(
                                    ExtensionMetadata.MAX_CONTAINER_ENTRIES,
                                    ExtensionValue.nullValue())));
        }

        assertThrows(IllegalArgumentException.class, builder::build);
    }
}
