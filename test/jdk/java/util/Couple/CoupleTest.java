import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Objects;
import java.util.Stack;

/**
 * Unit tests for the {@link Couple} class.
 * <p>
 * This class validates all functionalities of the {@code Couple} class including construction, getters/setters,
 * equality, comparison, hash code generation, string representation, and utility methods like {@code swap} and
 * {@code makeCouple}. It ensures correct behavior across different data types and edge cases (nulls, identity, etc.).
 * </p>
 *
 * @author Adarsh Sharma
 * @since 1.0
 */
public class CoupleTest {

    /**
     * Verifies that the constructor initializes the first and second elements correctly.
     */
    @Test
    public void testConstructor() {
        Couple<Integer, String> Couple = new Couple<>(1, "one");
        Assert.assertEquals(Couple.getFirst(), Integer.valueOf(1));
        Assert.assertEquals(Couple.getSecond(), "one");
    }

    /**
     * Verifies that the getter and setter methods update and retrieve values as expected.
     */
    @Test
    public void testGettersAndSetters() {
        Couple<Integer, String> Couple = new Couple<>(1, "one");
        Couple.setFirst(2);
        Couple.setSecond("two");
        Assert.assertEquals(Couple.getFirst(), Integer.valueOf(2));
        Assert.assertEquals(Couple.getSecond(), "two");
    }

    /**
     * Verifies that the {@code equals} method correctly evaluates object equality.
     */
    @Test
    public void testEquals() {
        Couple<Integer, String> Couple1 = new Couple<>(1, "one");
        Couple<Integer, String> Couple2 = new Couple<>(1, "one");
        Couple<Integer, String> Couple3 = new Couple<>(2, "two");

        Assert.assertTrue(Couple1.equals(Couple2));
        Assert.assertFalse(Couple1.equals(Couple3));
        Assert.assertFalse(Couple1.equals(null));
        Assert.assertFalse(Couple1.equals("not a Couple"));
        Assert.assertTrue(Couple1.equals(Couple1)); // Self-comparison
    }

    /**
     * Verifies equality behavior when one or both elements are {@code null}.
     */
    @Test
    public void testEqualsWithNullElements() {
        Couple<String, String> Couple1 = new Couple<>(null, "one");
        Couple<String, String> Couple2 = new Couple<>(null, "one");
        Assert.assertTrue(Couple1.equals(Couple2));
        Couple<String, String> Couple3 = new Couple<>(null, null);
        Couple<String, String> Couple4 = new Couple<>(null, null);
        Assert.assertTrue(Couple1.equals(Couple2));
        Assert.assertTrue(Couple2.equals(Couple1));
    }

    /**
     * Validates that {@code hashCode} is consistent for equal Couples.
     */
    @Test
    public void testHashCode() {
        Couple<Integer, String> Couple1 = new Couple<>(1, "one");
        Couple<Integer, String> Couple2 = new Couple<>(1, "one");
        Assert.assertEquals(Couple1.hashCode(), Couple2.hashCode());
    }

    /**
     * Ensures {@code hashCode} handles {@code null} elements correctly.
     */
    @Test
    public void testHashCodeWithNulls() {
        Couple<String, String> Couple = new Couple<>(null, null);
        Assert.assertEquals(Couple.hashCode(), Objects.hash(null, null));
    }

    /**
     * Validates the static factory method {@code makeCouple} creates an equivalent {@code Couple}.
     */
    @Test
    public void testMakeCouple() {
        Couple<Integer, String> Couple = Couple.makeCouple(1, "one");
        Assert.assertEquals(Couple.getFirst(), Integer.valueOf(1));
        Assert.assertEquals(Couple.getSecond(), "one");
    }

    /**
     * Tests usage of {@code Couple} with custom objects such as {@code Stack}.
     */
    @Test
    public void testCustomObjects() {
        Couple<Stack<Integer>, String> Couple = new Couple<>(new Stack<>(), "test");
        Couple.getFirst().push(1);
        Couple.getFirst().push(2);
        Assert.assertEquals(Couple.getFirst().size(), 2);
        Assert.assertEquals(Couple.getSecond(), "test");
    }

    /**
     * Validates the {@code toString} representation for non-null elements.
     */
    @Test
    public void testToString() {
        Couple<Integer, String> Couple = new Couple<>(1, "one");
        Assert.assertEquals(Couple.toString(), "(1, one)");
    }

    /**
     * Validates the {@code toString} representation when both elements are {@code null}.
     */
    @Test
    public void testToStringWithNulls() {
        Couple<String, String> Couple = new Couple<>(null, null);
        Assert.assertEquals(Couple.toString(), "(null, null)");
    }

    /**
     * Validates that {@code compareTo} returns zero for equal Couples.
     */
    @Test
    public void testCompareToEqual() {
        Couple<String, String> Couple1 = new Couple<>("a", "b");
        Couple<String, String> Couple2 = new Couple<>("a", "b");
        Assert.assertEquals(Couple1.compareTo(Couple2), 0);
    }

    /**
     * Validates that {@code compareTo} returns a negative or positive value
     * when the first elements differ.
     */
    @Test
    public void testCompareToFirstDifferent() {
        Couple<String, String> Couple1 = new Couple<>("a", "b");
        Couple<String, String> Couple2 = new Couple<>("b", "b");
        Assert.assertTrue(Couple1.compareTo(Couple2) < 0);
        Assert.assertTrue(Couple2.compareTo(Couple1) > 0);
    }

    /**
     * Validates that {@code compareTo} evaluates second elements when first are equal.
     */
    @Test
    public void testCompareToSecondDifferent() {
        Couple<String, String> Couple1 = new Couple<>("a", "b");
        Couple<String, String> Couple2 = new Couple<>("a", "c");
        Assert.assertTrue(Couple1.compareTo(Couple2) < 0);
    }

    /**
     * Tests {@code compareTo} behavior when one or both elements are {@code null}.
     */
    @Test
    public void testCompareToWithNulls() {
        Couple<String, String> Couple1 = new Couple<>(null, "b");
        Couple<String, String> Couple2 = new Couple<>("a", "b");
        Assert.assertTrue(Couple1.compareTo(Couple2) < 0);

        Couple<String, String> Couple3 = new Couple<>("a", null);
        Couple<String, String> Couple4 = new Couple<>("a", "b");
        Assert.assertTrue(Couple3.compareTo(Couple4) < 0);

        Couple<String, String> Couple5 = new Couple<>(null, null);
        Couple<String, String> Couple6 = new Couple<>(null, null);
        Assert.assertEquals(Couple5.compareTo(Couple6), 0);
        // null is considered less than non-null for comparison
    }

    /**
     * Verifies that passing {@code null} to {@code compareTo} throws {@code NullPointerException}.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCompareToNull() {
        Couple<String, String> Couple = new Couple<>("a", "b");
        Couple.compareTo(null);
    }
}
