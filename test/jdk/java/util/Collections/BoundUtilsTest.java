import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

/**
 * Unit tests for lowerBound() and upperBound() utility methods.
 * <p>
 * Covers edge cases for:
 * - Collection<T>
 * <p>
 * @author Adarsh Sharma
 * @since 1.0
 */
public class BoundUtilsTest {

    /**
     * Tests lowerBound with an empty collection.
     */
    @Test
    public void testLowerBound_Collection_Empty() {
        List<Integer> list = Collections.emptyList();
        int index = Collections.lowerBound(list, 10);
        Assert.assertEquals(index, list.size());
    }

    /**
     * Tests upperBound with an empty collection.
     */
    @Test
    public void testUpperBound_Collection_Empty() {
        List<Integer> list = Collections.emptyList();
        int index = Collections.upperBound(list, 10);
        Assert.assertEquals(index, list.size());
    }

    /**
     * Tests lowerBound at start, middle and end of sorted collection.
     */
    @Test
    public void testLowerBound_Collection_NormalCases() {
        List<Integer> list = Arrays.asList(1, 3, 5, 7, 9);
        Assert.assertEquals(Collections.lowerBound(list, 1), 0);  // Exact match at start
        Assert.assertEquals(Collections.lowerBound(list, 4), 2);  // Between 3 and 5
        Assert.assertEquals(Collections.lowerBound(list, 10), list.size()); // Out of bounds
    }

    /**
     * Tests upperBound at start, middle and end of sorted collection.
     */
    @Test
    public void testUpperBound_Collection_NormalCases() {
        List<Integer> list = Arrays.asList(1, 3, 5, 7, 9);
        Assert.assertEquals(Collections.upperBound(list, 1), 1);  // Next index after 1
        Assert.assertEquals(Collections.upperBound(list, 4), 2);  // Next after 3
        Assert.assertEquals(Collections.upperBound(list, 9), list.size()); // No greater element
    }
}
