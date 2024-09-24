package java.util;

public sealed interface Range<T extends Comparable<T>> permits Range.BoundedAtEnd, Range.BoundedAtStart, Ranges.UnboundedRange {
    /**
     * Returns an unbounded range.
     *
     * @param <T> the type of the values
     * @return an unbounded range
     */
    static <T extends Comparable<T>> Range<T> unbounded() {
        return new Ranges.UnboundedRange<>();
    }

    /**
     * Returns an unbounded range ending at the given value.
     *
     * @param end the end of the range
     * @param <T> the type of the values
     * @return an unbounded range ending at the given value
     */
    static <T extends Comparable<T>> Range<T> unboundedEndingAt(T end) {
        return new Ranges.UnboundedStartRange<>(end);
    }

    /**
     * Returns an unbounded range ending at the given value.
     *
     * @param end        the end of the range
     * @param excludeEnd whether the end is exclusive
     * @param <T>        the type of the values
     * @return an unbounded range ending at the given value
     */
    static <T extends Comparable<T>> Range<T> unboundedEndingAt(T end, boolean excludeEnd) {
        return new Ranges.UnboundedStartRange<>(end, excludeEnd);
    }

    /**
     * Returns an unbounded range starting at the given value with the start included.
     *
     * @param start the start of the range
     * @param <T>   the type of the values
     * @return an unbounded range starting at the given value
     */
    static <T extends Comparable<T>> Range<T> unboundedStartingAt(T start) {
        return new Ranges.UnboundedEndRange<>(start);
    }

    /**
     * Returns an unbounded range starting at the given value.
     *
     * @param start        the start of the range
     * @param excludeStart whether the start is exclusive
     * @param <T>          the type of the values
     * @return an unbounded range starting at the given value
     */
    static <T extends Comparable<T>> Range<T> unboundedStartingAt(T start, boolean excludeStart) {
        return new Ranges.UnboundedEndRange<>(start, excludeStart);
    }

    /**
     * Returns a bounded range with both bounds included.
     *
     * @param start the start of the range
     * @param end   the end of the range
     * @param <T>   the type of the values
     * @return a bounded range
     */
    static <T extends Comparable<T>> Range<T> of(T start, T end) {
        return of(start, end, BoundsExclusion.NONE);
    }

    /**
     * Returns a bounded range with the given bounds exclusion.
     *
     * @param start           the start of the range
     * @param end             the end of the range
     * @param boundsExclusion the bounds exclusion
     * @param <T>             the type of the values
     * @return a bounded range with the given bounds exclusion
     */
    static <T extends Comparable<T>> Range<T> of(T start, T end, BoundsExclusion boundsExclusion) {
        if (start.compareTo(end) > 0) {
            throw new IllegalArgumentException("Start must be less than or equal to end");
        }
        return new Ranges.BoundedRange<>(start, end, boundsExclusion);
    }

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
     * Returns the intersection of the span with the given span.
     *
     * @param other the span to intersect with
     * @return the intersection of the span with the given span. If the spans do not overlap, the result is an empty optional.
     */
    Optional<Range<? extends T>> intersection(Range<? extends T> other);

    /**
     * Returns the union of the span with the given span.
     *
     * @param other the span to union with
     * @return the union of the span with the given span.
     * If the spans do not overlap, the result is an array of two spans.
     * If the spans overlap, the result is an array of one combined span.
     */
    Ranges.Union<? extends T> union(Range<? extends T> other);

    /**
     * Returns the gap between the span and the given span.
     *
     * @param other the span to gap with
     * @return the gap between the span and the given span. If the spans overlap, the result is an empty optional.
     */
    Optional<Range<T>> gap(Range<? extends T> other);

    enum BoundsExclusion {
        NONE,
        START,
        END,
        BOTH
    }

    sealed interface BoundedAtEnd<T extends Comparable<T>> extends Range<T> permits Ranges.BoundedRange, Ranges.UnboundedStartRange {

        T end();

        boolean isEndExclusive();

        /**
         * Returns whether the span is before the given span.
         *
         * @param other the span to check
         * @return true if the span is before the given span, false otherwise
         */
        boolean isBefore(BoundedAtStart<? extends T> other);

        /**
         * Returns whether the span is before the given point.
         *
         * @param point the instant to check
         * @return true if the span is before the given instant, false otherwise
         */
        boolean isBefore(T point);
    }

    sealed interface BoundedAtStart<T extends Comparable<T>> extends Range<T> permits Ranges.BoundedRange, Ranges.UnboundedEndRange {

        T start();

        boolean isStartExclusive();

        /**
         * Returns whether the span is after the given span.
         *
         * @param other the span to check
         * @return true if the span is after the given span, false otherwise
         */
        boolean isAfter(BoundedAtEnd<? extends T> other);

        /**
         * Returns whether the span is after the given point.
         *
         * @param point the instant to check
         * @return true if the span is after the given instant, false otherwise
         */
        boolean isAfter(T point);
    }
}
