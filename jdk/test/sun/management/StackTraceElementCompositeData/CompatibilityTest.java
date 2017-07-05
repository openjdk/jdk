
import java.util.HashMap;
import java.util.Map;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import sun.management.StackTraceElementCompositeData;

import org.testng.annotations.*;
import static org.testng.Assert.*;

/*
 * @test
 * @bug     8139587
 * @summary Check backward compatibility of StackTraceElementCompositeData
 * @modules java.management/sun.management
 * @run testng CompatibilityTest
 * @author  Jaroslav Bachorik
 */

public class CompatibilityTest {
    private static CompositeType compositeTypeV6;
    private static Map<String, Object> itemsV6;
    private static CompositeData compositeDataV6;

    @BeforeClass
    public static void setup() throws Exception {
        compositeTypeV6 = new CompositeType(
            StackTraceElement.class.getName(),
            "StackTraceElement",
            new String[]{
                "className", "methodName", "fileName", "nativeMethod", "lineNumber"
            },
            new String[]{
                "className", "methodName", "fileName", "nativeMethod", "lineNumber"
            },
            new OpenType[]{
                SimpleType.STRING,
                SimpleType.STRING,
                SimpleType.STRING,
                SimpleType.BOOLEAN,
                SimpleType.INTEGER
            }
        );

        itemsV6 = new HashMap<>();
        itemsV6.put("className", "MyClass");
        itemsV6.put("methodName", "myMethod");
        itemsV6.put("fileName", "MyClass.java");
        itemsV6.put("nativeMethod", false);
        itemsV6.put("lineNumber", 123);

        compositeDataV6 = new CompositeDataSupport(compositeTypeV6, itemsV6);
    }

    @Test
    public void testV6Compatibility() throws Exception {
        StackTraceElement ste = StackTraceElementCompositeData.from(compositeDataV6);

        assertNotNull(ste);
        assertEquals(ste.getClassName(), "MyClass");
        assertEquals(ste.getMethodName(), "myMethod");
        assertEquals(ste.getFileName(), "MyClass.java");
        assertEquals(ste.isNativeMethod(), false);
        assertEquals(ste.getLineNumber(), 123);

        assertNull(ste.getModuleName());
        assertNull(ste.getModuleVersion());
    }
}