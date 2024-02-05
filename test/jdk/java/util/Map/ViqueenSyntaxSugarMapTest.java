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

    @Test
    public void testSingletonMap() {
        Map singleton = {
                "sweden": "stockholm"
        };
        assertEquals(singleton.size(), 1);
    }

    @Test
    public void testFullMapWithStringKeyValuePairs() {
        Map<String, String> capitals = {
                "norway": "oslo",
                "australia": "canberra",
                "morocco": "rabat",
                "sweden": "stockholm"
        };
        assertEquals(capitals.size(), 4);
    }
}
