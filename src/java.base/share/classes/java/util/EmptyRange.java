package java.util;

/**
 * Represents an empty range of values.
 *
 * @param <T> the type of the values
 */
public final class EmptyRange<T extends Comparable<T>> implements Range<T> {

    /**
     * Creates an empty range.
     */
    public EmptyRange() {
    }

    @Override
    public boolean contains(T instant) {
        return false;
    }

    @Override
    public boolean overlaps(Range<? extends T> other) {
        return false;
    }

    @Override
    public Range<? extends T> intersection(Range<? extends T> other) {
        return this;
    }

    @Override
    public Range<? extends T> union(Range<? extends T> other) {
        return other;
    }

    @Override
    public Range<? extends T> difference(Range<? extends T> other) {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EmptyRange;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
