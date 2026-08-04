package com.cybersammy.bugreport.api.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable exact path to a value in a nested contract or submitted form. */
public final class ValidationPath implements Comparable<ValidationPath> {
    private static final int MAX_PROPERTY_LENGTH = 64;
    private static final int MAX_SEGMENTS = 64;
    private static final Pattern PROPERTY =
            Pattern.compile("[a-z][A-Za-z0-9_]{0,63}");
    private static final ValidationPath ROOT = new ValidationPath(List.of());

    private final List<Segment> segments;
    private final String display;

    private ValidationPath(List<Segment> segments) {
        this.segments = List.copyOf(segments);
        display = render(segments);
    }

    /**
     * Returns the root path {@code $}.
     *
     * @return root path
     */
    public static ValidationPath root() {
        return ROOT;
    }

    /**
     * Appends a property segment.
     *
     * @param property canonical contract property name
     * @return child path
     */
    public ValidationPath property(String property) {
        if (property == null
                || property.length() > MAX_PROPERTY_LENGTH
                || !PROPERTY.matcher(property).matches()) {
            throw new IllegalArgumentException(
                    "Validation property must be a canonical lower-camel ASCII name");
        }
        return append(new PropertySegment(property));
    }

    /**
     * Appends a zero-based list index.
     *
     * @param index non-negative index
     * @return indexed path
     */
    public ValidationPath index(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Validation path index must be non-negative");
        }
        return append(new IndexSegment(index));
    }

    private ValidationPath append(Segment segment) {
        if (segments.size() >= MAX_SEGMENTS) {
            throw new IllegalArgumentException(
                    "Validation path exceeds " + MAX_SEGMENTS + " segments");
        }
        ArrayList<Segment> child = new ArrayList<>(segments.size() + 1);
        child.addAll(segments);
        child.add(segment);
        return new ValidationPath(child);
    }

    private static String render(List<Segment> segments) {
        StringBuilder builder = new StringBuilder("$");
        for (Segment segment : segments) {
            segment.appendTo(builder);
        }
        return builder.toString();
    }

    @Override
    public int compareTo(ValidationPath other) {
        return display.compareTo(other.display);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ValidationPath path && segments.equals(path.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(segments);
    }

    @Override
    public String toString() {
        return display;
    }

    private sealed interface Segment permits IndexSegment, PropertySegment {
        void appendTo(StringBuilder builder);
    }

    private record PropertySegment(String value) implements Segment {
        @Override
        public void appendTo(StringBuilder builder) {
            builder.append('.').append(value);
        }
    }

    private record IndexSegment(int value) implements Segment {
        @Override
        public void appendTo(StringBuilder builder) {
            builder.append('[').append(value).append(']');
        }
    }
}
