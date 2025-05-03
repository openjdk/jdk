import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Objects;
import java.util.Stack;

/**
 * Unit tests for the {@link Pair} class.
 * <p>
 * This class validates all functionalities of the {@code Pair} class including construction, getters/setters,
 * equality, comparison, hash code generation, string representation, and utility methods like {@code swap} and
 * {@code makePair}. It ensures correct behavior across different data types and edge cases (nulls, identity, etc.).
 * </p>
 *
 * @author Adarsh Sharma
 * @since 1.0
 */
public class PairTest {

    /**
     * Verifies that the constructor initializes the first and second elements correctly.
     */
    @Test
    public void testConstructor() {
        Pair<Integer, String> pair = new Pair<>(1, "one");
        Assert.assertEquals(pair.getFirst(), Integer.valueOf(1));
        Assert.assertEquals(pair.getSecond(), "one");
    }

    /**
     * Verifies that the getter and setter methods update and retrieve values as expected.
     */
    @Test
    public void testGettersAndSetters() {
        Pair<Integer, String> pair = new Pair<>(1, "one");
        pair.setFirst(2);
        pair.setSecond("two");
        Assert.assertEquals(pair.getFirst(), Integer.valueOf(2));
        Assert.assertEquals(pair.getSecond(), "two");
    }

    /**
     * Verifies that the {@code swap} method correctly exchanges first and second elements.
     */
    @Test
    public void testSwap() {
        Pair<Integer, String> pair = new Pair<>(1, "one");
        pair.swap();
        Assert.assertEquals(pair.getFirst(), "one");
        Assert.assertEquals(pair.getSecond(), Integer.valueOf(1));
    }

    /**
     * Tests {@code swap} behavior when both elements are {@code null}.
     */
    @Test
    public void testSwapNulls() {
        Pair<String, String> pair = new Pair<>(null, null);
        pair.swap();
        Assert.assertNull(pair.getFirst());
        Assert.assertNull(pair.getSecond());
    }

    /**
     * Verifies that the {@code equals} method correctly evaluates object equality.
     */
    @Test
    public void testEquals() {
        Pair<Integer, String> pair1 = new Pair<>(1, "one");
        Pair<Integer, String> pair2 = new Pair<>(1, "one");
        Pair<Integer, String> pair3 = new Pair<>(2, "two");

        Assert.assertTrue(pair1.equals(pair2));
        Assert.assertFalse(pair1.equals(pair3));
        Assert.assertFalse(pair1.equals(null));
        Assert.assertFalse(pair1.equals("not a pair"));
        Assert.assertTrue(pair1.equals(pair1)); // Self-comparison
    }

    /**
     * Verifies equality behavior when one or both elements are {@code null}.
     */
    @Test
    public void testEqualsWithNullElements() {
        Pair<String, String> pair1 = new Pair<>(null, "one");
        Pair<String, String> pair2 = new Pair<>(null, "one");
        Assert.assertTrue(pair1.equals(pair2));
    }

    /**
     * Validates that {@code hashCode} is consistent for equal pairs.
     */
    @Test
    public void testHashCode() {
        Pair<Integer, String> pair1 = new Pair<>(1, "one");
        Pair<Integer, String> pair2 = new Pair<>(1, "one");
        Assert.assertEquals(pair1.hashCode(), pair2.hashCode());
    }

    /**
     * Ensures {@code hashCode} handles {@code null} elements correctly.
     */
    @Test
    public void testHashCodeWithNulls() {
        Pair<String, String> pair = new Pair<>(null, null);
        Assert.assertEquals(pair.hashCode(), Objects.hash(null, null));
    }

    /**
     * Validates the static factory method {@code makePair} creates an equivalent {@code Pair}.
     */
    @Test
    public void testMakePair() {
        Pair<Integer, String> pair = Pair.makePair(1, "one");
        Assert.assertEquals(pair.getFirst(), Integer.valueOf(1));
        Assert.assertEquals(pair.getSecond(), "one");
    }

    /**
     * Tests usage of {@code Pair} with custom objects such as {@code Stack}.
     */
    @Test
    public void testCustomObjects() {
        Pair<Stack<Integer>, String> pair = new Pair<>(new Stack<>(), "test");
        pair.getFirst().push(1);
        pair.getFirst().push(2);
        Assert.assertEquals(pair.getFirst().size(), 2);
        Assert.assertEquals(pair.getSecond(), "test");
    }

    /**
     * Validates the {@code toString} representation for non-null elements.
     */
    @Test
    public void testToString() {
        Pair<Integer, String> pair = new Pair<>(1, "one");
        Assert.assertEquals(pair.toString(), "(1, one)");
    }

    /**
     * Validates the {@code toString} representation when both elements are {@code null}.
     */
    @Test
    public void testToStringWithNulls() {
        Pair<String, String> pair = new Pair<>(null, null);
        Assert.assertEquals(pair.toString(), "(null, null)");
    }

    /**
     * Validates that {@code compareTo} returns zero for equal pairs.
     */
    @Test
    public void testCompareToEqual() {
        Pair<String, String> pair1 = new Pair<>("a", "b");
        Pair<String, String> pair2 = new Pair<>("a", "b");
        Assert.assertEquals(pair1.compareTo(pair2), 0);
    }

    /**
     * Validates that {@code compareTo} returns a negative or positive value
     * when the first elements differ.
     */
    @Test
    public void testCompareToFirstDifferent() {
        Pair<String, String> pair1 = new Pair<>("a", "b");
        Pair<String, String> pair2 = new Pair<>("b", "b");
        Assert.assertTrue(pair1.compareTo(pair2) < 0);
        Assert.assertTrue(pair2.compareTo(pair1) > 0);
    }

    /**
     * Validates that {@code compareTo} evaluates second elements when first are equal.
     */
    @Test
    public void testCompareToSecondDifferent() {
        Pair<String, String> pair1 = new Pair<>("a", "b");
        Pair<String, String> pair2 = new Pair<>("a", "c");
        Assert.assertTrue(pair1.compareTo(pair2) < 0);
    }

    /**
     * Tests {@code compareTo} behavior when one or both elements are {@code null}.
     */
    @Test
    public void testCompareToWithNulls() {
        Pair<String, String> pair1 = new Pair<>(null, "b");
        Pair<String, String> pair2 = new Pair<>("a", "b");
        Assert.assertTrue(pair1.compareTo(pair2) < 0);

        Pair<String, String> pair3 = new Pair<>("a", null);
        Pair<String, String> pair4 = new Pair<>("a", "b");
        Assert.assertTrue(pair3.compareTo(pair4) < 0);

        Pair<String, String> pair5 = new Pair<>(null, null);
        Pair<String, String> pair6 = new Pair<>(null, null);
        Assert.assertEquals(pair5.compareTo(pair6), 0);
    }

    /**
     * Verifies that passing {@code null} to {@code compareTo} throws {@code NullPointerException}.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCompareToNull() {
        Pair<String, String> pair = new Pair<>("a", "b");
        pair.compareTo(null);
    }
}
