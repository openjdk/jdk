/**
 * @test
 * @bug     6520152
 * @summary ACC_FINAL flag for anonymous classes shouldn't be set
 * @compile T.java
 * @run main/othervm T6520152
 */

import java.lang.reflect.Method;
import static java.lang.reflect.Modifier.*;

public class T6520152 {
    public static void main(String [] args) throws Exception {
        Class clazz = Class.forName("T$1");
        if ((clazz.getModifiers() & FINAL) != 0) {
            throw new RuntimeException("Failed: " + clazz.getName() + " shouldn't be marked final.");
        }
    }
}
