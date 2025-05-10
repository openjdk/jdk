package java.util;

/**
 * The {@code Couple} class represents a mutable, ordered Couple of elements.
 * <p>
 * This class mimics the behavior of the C++ Standard Library's `std::Couple`, providing two values, referred to as
 * the "first" and the "second" elements, of possibly different types.
 * <p>
 * The {@code Couple} class provides a parameterized constructor to initialize both elements and allows access to them
 * through getter and setter methods.
 *
 * @param <K> the type of the first element
 * @param <V> the type of the second element
 *
 * @author Adarsh Sharma
 * @since 1.0
 */
public class Couple<K extends Comparable<? super K>, V extends Comparable<? super V>>
        implements Comparable<Couple<K, V>> {
    private K first;
    private V second;

    /**
     * Constructs a new Couple with the specified values.
     *
     * @param first the first element of the Couple
     * @param second the second element of the Couple
     */
    public Couple(K first, V second) {
        this.first = first;
        this.second = second;
    }

    /**
     * Returns the first element of the Couple.
     *
     * @return the first element of the Couple
     */
    public K getFirst() {
        return first;
    }

    /**
     * Sets the first element of the Couple.
     *
     * @param first the first element of the Couple
     */
    public void setFirst(K first) {
        this.first = first;
    }

    /**
     * Returns the second element of the Couple.
     *
     * @return the second element of the Couple
     */
    public V getSecond() {
        return second;
    }

    /**
     * Sets the second element of the Couple.
     *
     * @param second the second element of the Couple
     */
    public void setSecond(V second) {
        this.second = second;
    }

    /**
     * Compares this Couple with the specified Couple for order.
     * <p>
     * Comparison is based first on the {@code first} elements, and if they are equal,
     * then on the {@code second} elements. Both elements are compared using their natural ordering,
     * unless they are {@code null}, in which case {@code null} is considered less than non-null.
     * <p>
     * This method is {@code null}-safe and consistent with equals when both elements are non-null.
     *
     * @param other the Couple to be compared
     * @return a negative integer, zero, or a positive integer as this Couple
     *         is less than, equal to, or greater than the specified Couple
     * @throws NullPointerException if {@code other} is {@code null}
     */
    @Override
    public int compareTo(Couple<K, V> other) {
        if (other == null) {
            throw new NullPointerException("Compared Couple must not be null");
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
     * Returns a string representation of this Couple in the format {@code (first, second)}.
     *
     * @return a string representation of this Couple
     */
    @Override
    public String toString() {
        return "(" + first + ", " + second + ")";
    }

    /**
     * Checks if this Couple is equal to another object. Two Couples are considered equal if both the first and second
     * elements are equal.
     *
     * @param o the object to compare this Couple with
     * @return {@code true} if this Couple is equal to the specified object
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Couple<?, ?> Couple = (Couple<?, ?>) o;
        return Objects.equals(first, Couple.first) && Objects.equals(second, Couple.second);
    }

    /**
     * Returns the hash code for this Couple, computed based on the hash codes of the first and second elements.
     *
     * @return the hash code for this Couple
     */
    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    /**
     * Creates a new {@code Couple} with the given first and second elements.
     *
     * @param first the first element of the Couple
     * @param second the second element of the Couple
     * @param <K> the type of the first element, must be Comparable
     * @param <V> the type of the second element, must be Comparable
     * @return a new {@code Couple} containing the given elements
     */
    public static <K extends Comparable<? super K>, V extends Comparable<? super V>>
    Couple<K, V> makeCouple(K first, V second) {
        return new Couple<>(first, second);
    }
}
