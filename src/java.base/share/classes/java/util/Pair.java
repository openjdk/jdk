package java.util;

/**
 * The {@code Pair} class represents a mutable, ordered pair of elements.
 * <p>
 * This class mimics the behavior of the C++ Standard Library's `std::pair`, providing two values, referred to as
 * the "first" and the "second" elements, of possibly different types.
 * <p>
 * The {@code Pair} class provides a parameterized constructor to initialize both elements and allows access to them
 * through getter and setter methods.
 *
 * @param <K> the type of the first element
 * @param <V> the type of the second element
 *
 * @author Adarsh Sharma
 * @since 1.0
 */
public class Pair<K, V> {
    private K first;
    private V second;

    /**
     * Constructs a new pair with the specified values.
     *
     * @param first the first element of the pair
     * @param second the second element of the pair
     */
    public Pair(K first, V second) {
        this.first = first;
        this.second = second;
    }

    /**
     * Returns the first element of the pair.
     *
     * @return the first element of the pair
     */
    public K getFirst() {
        return first;
    }

    /**
     * Sets the first element of the pair.
     *
     * @param first the first element of the pair
     */
    public void setFirst(K first) {
        this.first = first;
    }

    /**
     * Returns the second element of the pair.
     *
     * @return the second element of the pair
     */
    public V getSecond() {
        return second;
    }

    /**
     * Sets the second element of the pair.
     *
     * @param second the second element of the pair
     */
    public void setSecond(V second) {
        this.second = second;
    }

    /**
     * Swaps the first and second elements of the pair.
     */
    public void swap() {
        K temp = first;
        first = second;
        second = temp;
    }

    /**
     * Compares this pair with the specified pair for order.
     * <p>
     * Comparison is based first on the {@code first} elements, and if they are equal,
     * then on the {@code second} elements. Both elements are compared using their natural ordering,
     * unless they are {@code null}, in which case {@code null} is considered less than non-null.
     * <p>
     * This method is {@code null}-safe and consistent with equals when both elements are non-null.
     *
     * @param other the pair to be compared
     * @return a negative integer, zero, or a positive integer as this pair
     *         is less than, equal to, or greater than the specified pair
     * @throws NullPointerException if {@code other} is {@code null}
     */
    @Override
    public int compareTo(Pair<K, V> other) {
        if (other == null) {
            throw new NullPointerException("Compared pair must not be null");
        }

        int cmp = compareNullable(this.first, other.first);
        if (cmp != 0) {
            return cmp;
        }

        return compareNullable(this.second, other.second);
    }

    /**
     * Helper method to compare two Comparable objects, handling nulls.
     * {@code null} is considered less than any non-null value.
     *
     * @param a first value
     * @param b second value
     * @return comparison result
     */
    @SuppressWarnings("unchecked")
    private <T extends Comparable<? super T>> int compareNullable(T a, T b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    /**
     * Returns a string representation of this pair in the format {@code (first, second)}.
     *
     * @return a string representation of this pair
     */
    @Override
    public String toString() {
        return "(" + String.valueOf(first) + ", " + String.valueOf(second) + ")";
    }

    /**
     * Checks if this pair is equal to another object. Two pairs are considered equal if both the first and second
     * elements are equal.
     *
     * @param o the object to compare this pair with
     * @return {@code true} if this pair is equal to the specified object
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return first.equals(pair.first) && second.equals(pair.second);
    }

    /**
     * Returns the hash code for this pair, computed based on the hash codes of the first and second elements.
     *
     * @return the hash code for this pair
     */
    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    /**
     * Creates a new {@code Pair} with the given first and second elements.
     *
     * @param first the first element of the pair
     * @param second the second element of the pair
     * @param <K> the type of the first element
     * @param <V> the type of the second element
     * @return a new {@code Pair} containing the given elements
     */
    public static <K, V> Pair<K, V> makePair(K first, V second) {
        return new Pair<>(first, second);
    }
}
