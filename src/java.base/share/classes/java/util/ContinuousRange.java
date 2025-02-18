package java.util;

/**
 * Represents a continuous range of values.
 *
 * @param <T> the type of the values
 */
public sealed interface ContinuousRange<T extends Comparable<T>> extends Range<T> permits ContinuousRange.BoundedAtEnd, ContinuousRange.BoundedAtStart, ContinuousRanges.UnboundedRange {

    /**
     * Returns an unbounded range.
     *
     * @param <T> the type of the values
     * @return an unbounded range
     */
    static <T extends Comparable<T>> ContinuousRange<T> unbounded() {
        return new ContinuousRanges.UnboundedRange<>();
    }

    /**
     * Returns an unbounded range ending at the given value.
     *
     * @param end the end of the range
     * @param <T> the type of the values
     * @return an unbounded range ending at the given value
     */
    static <T extends Comparable<T>> ContinuousRange<T> unboundedEndingAt(T end) {
        return new ContinuousRanges.UnboundedStartRange<>(end);
    }

    /**
     * Returns an unbounded range ending at the given value.
     *
     * @param end        the end of the range
     * @param excludeEnd whether the end is exclusive
     * @param <T>        the type of the values
     * @return an unbounded range ending at the given value
     */
    static <T extends Comparable<T>> ContinuousRange<T> unboundedEndingAt(T end, boolean excludeEnd) {
        return excludeEnd ? new ContinuousRanges.UnboundedStartRange<>(end, true) : new ContinuousRanges.UnboundedStartRange<>(end);
    }

    /**
     * Returns an unbounded range starting at the given value with the start included.
     *
     * @param start the start of the range
     * @param <T>   the type of the values
     * @return an unbounded range starting at the given value
     */
    static <T extends Comparable<T>> ContinuousRange<T> unboundedStartingAt(T start) {
        return new ContinuousRanges.UnboundedEndRange<>(start);
    }

    /**
     * Returns an unbounded range starting at the given value.
     *
     * @param start        the start of the range
     * @param excludeStart whether the start is exclusive
     * @param <T>          the type of the values
     * @return an unbounded range starting at the given value
     */
    static <T extends Comparable<T>> ContinuousRange<T> unboundedStartingAt(T start, boolean excludeStart) {
        return excludeStart ? new ContinuousRanges.UnboundedEndRange<>(start, true) : new ContinuousRanges.UnboundedEndRange<>(start);
    }

    /**
     * Returns a bounded range with both bounds included.
     *
     * @param start the start of the range
     * @param end   the end of the range
     * @param <T>   the type of the values
     * @return a bounded range
     */
    static <T extends Comparable<T>> ContinuousRange<T> of(T start, T end) {
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
    static <T extends Comparable<T>> ContinuousRange<T> of(T start, T end, BoundsExclusion boundsExclusion) {
        if (start.compareTo(end) > 0) {
            throw new IllegalArgumentException("Start must be less than or equal to end");
        }
        return new ContinuousRanges.BoundedRange<>(start, end, boundsExclusion);
    }

    /**
     * Configures the exclusion of bounds for a bounded range.
     */
    enum BoundsExclusion {
        /**
         * No bounds are excluded.
         */
        NONE,
        /**
         * The start is excluded.
         */
        START,
        /**
         * The end is excluded.
         */
        END,
        /**
         * Both bounds are excluded.
         */
        BOTH
    }

    /**
     * Represents a continuous range of values that is bounded at the end.
     *
     * @param <T> the type of the values
     */
    sealed interface BoundedAtEnd<T extends Comparable<T>> extends ContinuousRange<T> permits ContinuousRanges.BoundedRange, ContinuousRanges.UnboundedStartRange {

        /**
         * Returns the end of the range.
         *
         * @return the end of the range
         */
        T end();

        /**
         * Returns whether the end is exclusive.
         *
         * @return true if the end is exclusive, false otherwise
         */
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

    /**
     * Represents a continuous range of values that is bounded at the start.
     *
     * @param <T> the type of the values
     */
    sealed interface BoundedAtStart<T extends Comparable<T>> extends ContinuousRange<T> permits ContinuousRanges.BoundedRange, ContinuousRanges.UnboundedEndRange {

        /**
         * Returns the start of the range.
         *
         * @return the start of the range
         */
        T start();

        /**
         * Returns whether the start is exclusive.
         *
         * @return true if the start is exclusive, false otherwise
         */
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
