package java.util;

/**
 * Represents a result of union operation where the result is two spans.
 *
 * @param <T> the type of the values
 */
public final class Union<T extends Comparable<T>> implements Range<T> {

    private final Range<T> first;

    private final Range<T> second;

    Union(Range<T> first, Range<T> second) {
        this.first = first;
        this.second = second;
    }

    /**
     * Returns the first span.
     *
     * @return the first span
     */
    public Range<T> first() {
        return first;
    }

    /**
     * Returns the second span.
     *
     * @return the second span
     */
    public Range<T> second() {
        return second;
    }

    @Override
    public boolean contains(T instant) {
        return first.contains(instant) || second.contains(instant);
    }

    @Override
    public boolean overlaps(Range<? extends T> other) {
        return first.overlaps(other) || second.overlaps(other);
    }

    @Override
    public Range<? extends T> intersection(Range<? extends T> other) {
        return null;
    }

    @Override
    public Range<? extends T> union(Range<? extends T> other) {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Range<? extends T> difference(Range<? extends T> other) {
        var firstDifference = first.difference(other);
        var secondDifference = second.difference(other);
        if (firstDifference instanceof EmptyRange<?> && secondDifference instanceof EmptyRange<?>) {
            return new EmptyRange<>();
        }
        if (firstDifference instanceof EmptyRange<?>) {
            return secondDifference;
        }
        if (secondDifference instanceof EmptyRange<?>) {
            return firstDifference;
        }
        return new Union<>((Range<T>) firstDifference, (Range<T>) secondDifference);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Union<?> that)) {
            return false;
        }
        return Objects.equals(first, that.first) && Objects.equals(second, that.second) ||
                Objects.equals(first, that.second) && Objects.equals(second, that.first);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    @Override
    public String toString() {
        return "(" + first + ") U (" + second + ")";
    }
}
