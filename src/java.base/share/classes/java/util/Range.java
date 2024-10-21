package java.util;

/**
 * Represents a range of values.
 *
 * @param <T> the type of the values
 */
public sealed interface Range<T extends Comparable<T>> permits ContinuousRange, Union, EmptyRange {

    /**
     * Returns whether the range contains the given instant.
     *
     * @param instant the instant to check
     * @return true if the range contains the instant, false otherwise
     */
    boolean contains(T instant);

    /**
     * Returns whether the range overlaps with the given range.
     *
     * @param other the range to check
     * @return true if the range overlaps with the given range, false otherwise
     */
    boolean overlaps(Range<? extends T> other);

    /**
     * Returns the intersection of the range with the given range.
     *
     * @param other the range to intersect with
     * @return the intersection of the range with the given range. If the ranges do not overlap, the result is an empty optional.
     */
    Range<? extends T> intersection(Range<? extends T> other);

    /**
     * Returns the union of the range with the given range.
     *
     * @param other the range to union with
     * @return the union of the range with the given range, represented as other range.
     */
    Range<? extends T> union(Range<? extends T> other);

    /**
     * Returns the difference of the range with the given range.
     *
     * @param other the range to subtract from this range
     * @return the difference of the range with the given range.
     */
    Range<? extends T> difference(Range<? extends T> other);

}
