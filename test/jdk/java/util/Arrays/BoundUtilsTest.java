import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;

/**
 * Unit tests for lowerBound() and upperBound() utility methods.
 * <p>
 * Covers edge cases for:
 * - Wrapper arrays
 * - Primitive arrays (int[], long[], etc.)
 * <p>
 * @author Adarsh Sharma
 * @since 1.0
 */
public class BoundsUtilsTest {

    // ----------------------------------------
    // Wrapper array tests (Integer[])
    // ----------------------------------------

    @Test
    public void testLowerBound_WrapperArray_Empty() {
        Integer[] array = new Integer[]{};
        Assert.assertEquals(Arrays.lowerBound(array, 5), -1);
    }

    @Test
    public void testUpperBound_WrapperArray_Empty() {
        Integer[] array = new Integer[]{};
        Assert.assertEquals(Arrays.upperBound(array, 5), -1);
    }

    @Test
    public void testLowerBound_WrapperArray_NormalCases() {
        Integer[] array = new Integer[]{2, 4, 6, 8};
        Assert.assertEquals(Arrays.lowerBound(array, 3), 1);
        Assert.assertEquals(Arrays.lowerBound(array, 8), 3);
        Assert.assertEquals(Arrays.lowerBound(array, 10), -1);
    }

    @Test
    public void testUpperBound_WrapperArray_NormalCases() {
        Integer[] array = new Integer[]{2, 4, 6, 8};
        Assert.assertEquals(Arrays.upperBound(array, 2), 1);
        Assert.assertEquals(Arrays.upperBound(array, 7), 3);
        Assert.assertEquals(Arrays.upperBound(array, 8), -1);
    }

    // ----------------------------------------
    // Primitive Arrays
    // ----------------------------------------

    // int[]
    @Test
    public void testLowerBound_IntArray() {
        int[] array = {1, 3, 5, 7};
        Assert.assertEquals(Arrays.lowerBound(array, 5), 2);
        Assert.assertEquals(Arrays.lowerBound(array, 8), -1);
    }

    @Test
    public void testUpperBound_IntArray() {
        int[] array = {1, 3, 5, 7};
        Assert.assertEquals(Arrays.upperBound(array, 5), 3);
        Assert.assertEquals(Arrays.upperBound(array, 7), -1);
    }

    // long[]
    @Test
    public void testLowerBound_LongArray() {
        long[] array = {1L, 3L, 5L, 7L};
        Assert.assertEquals(Arrays.lowerBound(array, 4L), 2);
        Assert.assertEquals(Arrays.lowerBound(array, 10L), -1);
    }

    @Test
    public void testUpperBound_LongArray() {
        long[] array = {1L, 3L, 5L, 7L};
        Assert.assertEquals(Arrays.upperBound(array, 3L), 2);
        Assert.assertEquals(Arrays.upperBound(array, 7L), -1);
    }

    // char[]
    @Test
    public void testLowerBound_CharArray() {
        char[] array = {'a', 'c', 'e'};
        Assert.assertEquals(Arrays.lowerBound(array, 'b'), 1);
        Assert.assertEquals(Arrays.lowerBound(array, 'f'), -1);
    }

    @Test
    public void testUpperBound_CharArray() {
        char[] array = {'a', 'c', 'e'};
        Assert.assertEquals(Arrays.upperBound(array, 'c'), 2);
        Assert.assertEquals(Arrays.upperBound(array, 'e'), -1);
    }

    // short[]
    @Test
    public void testLowerBound_ShortArray() {
        short[] array = {1, 2, 4, 6};
        Assert.assertEquals(Arrays.lowerBound(array, (short) 3), 2);
        Assert.assertEquals(Arrays.lowerBound(array, (short) 7), -1);
    }

    @Test
    public void testUpperBound_ShortArray() {
        short[] array = {1, 2, 4, 6};
        Assert.assertEquals(Arrays.upperBound(array, (short) 2), 2);
        Assert.assertEquals(Arrays.upperBound(array, (short) 6), -1);
    }

    // float[]
    @Test
    public void testLowerBound_FloatArray() {
        float[] array = {1.1f, 2.2f, 4.4f};
        Assert.assertEquals(Arrays.lowerBound(array, 2.5f), 2);
        Assert.assertEquals(Arrays.lowerBound(array, 5.0f), -1);
    }

    @Test
    public void testUpperBound_FloatArray() {
        float[] array = {1.1f, 2.2f, 4.4f};
        Assert.assertEquals(Arrays.upperBound(array, 2.2f), 2);
        Assert.assertEquals(Arrays.upperBound(array, 4.4f), -1);
    }

    // double[]
    @Test
    public void testLowerBound_DoubleArray() {
        double[] array = {1.0, 3.0, 5.0};
        Assert.assertEquals(Arrays.lowerBound(array, 4.0), 2);
        Assert.assertEquals(Arrays.lowerBound(array, 6.0), -1);
    }

    @Test
    public void testUpperBound_DoubleArray() {
        double[] array = {1.0, 3.0, 5.0};
        Assert.assertEquals(Arrays.upperBound(array, 3.0), 2);
        Assert.assertEquals(Arrays.upperBound(array, 5.0), -1);
    }

    // String[] (Object array)
    @Test
    public void testLowerBound_StringArray() {
        String[] array = {"apple", "banana", "cherry"};
        Assert.assertEquals(Arrays.lowerBound(array, "banana"), 1);
        Assert.assertEquals(Arrays.lowerBound(array, "coconut"), 2);
    }

    @Test
    public void testUpperBound_StringArray() {
        String[] array = {"apple", "banana", "cherry"};
        Assert.assertEquals(Arrays.upperBound(array, "banana"), 2);
        Assert.assertEquals(Arrays.upperBound(array, "cherry"), -1);
    }
}
