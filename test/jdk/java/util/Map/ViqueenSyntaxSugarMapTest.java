/*
 * @test
 * @run testng ViqueenSyntaxSugarMapTest
 * @author Hasnae R.
 */
import java.util.Map;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ViqueenSyntaxSugarMapTest {
    @Test
    public void testEmptyMap() {
        Map empty = {};
        assertTrue(empty.isEmpty());
    }
}
