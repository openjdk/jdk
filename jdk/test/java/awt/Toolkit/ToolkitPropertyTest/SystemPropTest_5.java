/*
  @test %I% %E%
  @bug 6315717
  @summary verifies that system property sun.awt.enableExtraMouseButtons might be set to false by the System class API.
  @author Andrei Dmitriev : area=awt.mouse
  @run main SystemPropTest_5
 */
//1)
// - Use System.setProperty("sun.awt.enableExtraMouseButtons", "false")
// - Verifies that System.getProperty("sun.awt.enableExtraMouseButtons") returns false
// - Verifies that Toolkit.areExtraMouseButtonsEnabled() returns false.
//2)
// - Use System.setProperty("sun.awt.enableExtraMouseButtons", "true")
// - Verifies that System.getProperty("sun.awt.enableExtraMouseButtons") returns true
// - Verifies that Toolkit.areExtraMouseButtonsEnabled() returns false still.

import java.awt.*;

public class SystemPropTest_5 {
    public static void main(String []s){
        System.out.println("STAGE 1");
        System.setProperty("sun.awt.enableExtraMouseButtons", "false");
        boolean propValue = Boolean.parseBoolean(System.getProperty("sun.awt.enableExtraMouseButtons"));
        if (propValue){
            throw new RuntimeException("TEST FAILED(1) : System property sun.awt.enableExtraMouseButtons = " + propValue);
        }
        if (Toolkit.getDefaultToolkit().areExtraMouseButtonsEnabled()){
            throw new RuntimeException("TEST FAILED(1) : Toolkit.areExtraMouseButtonsEnabled() returns true");
        }

        System.out.println("STAGE 2");
        System.setProperty("sun.awt.enableExtraMouseButtons", "true");
        propValue = Boolean.parseBoolean(System.getProperty("sun.awt.enableExtraMouseButtons"));
        if (!propValue){
            throw new RuntimeException("TEST FAILED(2) : System property sun.awt.enableExtraMouseButtons = " + propValue);
        }
        if (Toolkit.getDefaultToolkit().areExtraMouseButtonsEnabled()){
            throw new RuntimeException("TEST FAILED(2) : Toolkit.areExtraMouseButtonsEnabled() returns true");
        }
        System.out.println("Test passed.");
    }
}
