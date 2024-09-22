package java.time;

import java.io.Serializable;
import java.util.Optional;

/**
 * Represents a range of values.
 * @param <T> the type of the values
 */
public interface Range<T extends Comparable<T>> {

    /**
     * Returns the start of the span (inclusive).
     *
     * @return the start of the span
     * @throws UnsupportedOperationException if the span is unbounded at the start. This can be checked with the {@link #isBoundedAtStart()} method.
     */
    T start();

    /**
     * Returns the end of the span (inclusive).
     *
     * @return the end of the span
     * @throws UnsupportedOperationException if the span is unbounded at the end. This can be checked with the {@link #isBoundedAtEnd()} method.
     */
    T end();

    /**
     * Returns whether the span contains the given instant.
     *
     * @param instant the instant to check
     * @return true if the span contains the instant, false otherwise
     */
    boolean contains(T instant);

    /**
     * Returns whether the span overlaps with the given span.
     *
     * @param other the span to check
     * @return true if the span overlaps with the given span, false otherwise
     */
    boolean overlaps(Range<? extends T> other);

    /**
     * Returns whether the span is before the given span.
     *
     * @param other the span to check
     * @return true if the span is before the given span, false otherwise
     */
    boolean isBefore(Range<? extends T> other);

    /**
     * Returns whether the span is before the given point.
     *
     * @param point the instant to check
     * @return true if the span is before the given instant, false otherwise
     */
    boolean isBefore(T point);

    /**
     * Returns whether the span is after the given span.
     *
     * @param other the span to check
     * @return true if the span is after the given span, false otherwise
     */
    boolean isAfter(Range<? extends T> other);

    /**
     * Returns whether the span is after the given point.
     *
     * @param point the instant to check
     * @return true if the span is after the given instant, false otherwise
     */
    boolean isAfter(T point);

    /**
     * Returns whether the span is bounded at the start.
     * This method returning true guarantees that the {@link #start()} method will not throw an {@code UnsupportedOperationException},
     * while returning false guarantees that the {@link #start()} method will throw an {@code UnsupportedOperationException}.
     *
     * @return true if the span is bounded at the start, false otherwise
     */
    boolean isBoundedAtStart();

    /**
     * Returns whether the span is bounded at the end.
     * This method returning true guarantees that the {@link #end()} method will not throw an {@code UnsupportedOperationException},
     * while returning false guarantees that the {@link #end()} method will throw an {@code UnsupportedOperationException}.
     *
     * @return true if the span is bounded at the end, false otherwise
     */
    boolean isBoundedAtEnd();

    /**
     * Returns whether the span is negative.
     * A span is considered negative if the start is after the end.
     *
     * @return true if the span is negative, false otherwise
     */
    boolean isNegative();

    /**
     * Returns the intersection of the span with the given span.
     *
     * @param other the span to intersect with
     * @return the intersection of the span with the given span. If the spans do not overlap, the result is an empty optional.
     */
    Optional<Range<T>> intersection(Range<? extends T> other);

    /**
     * Returns the union of the span with the given span.
     *
     * @param other the span to union with
     * @return the union of the span with the given span.
     * If the spans do not overlap, the result is an array of two spans.
     * If the spans overlap, the result is an array of one combined span.
     */
    Range<T>[] union(Range<? extends T> other);

    /**
     * Returns the gap between the span and the given span.
     *
     * @param other the span to gap with
     * @return the gap between the span and the given span. If the spans overlap, the result is an empty optional.
     */
    Optional<Range<T>> gap(Range<? extends T> other);

}
