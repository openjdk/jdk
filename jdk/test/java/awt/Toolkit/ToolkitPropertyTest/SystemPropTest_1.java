/*
  @test %I% %E%
  @bug 6315717
  @summary verifies that system property sun.awt.enableExtraMouseButtons is true by default
  @author Andrei Dmitriev : area=awt.mouse
  @run main SystemPropTest_1
 */
//1) Verifies that System.getProperty("sun.awt.enableExtraMouseButtons") returns false initially.
//2) Verifies that Toolkit.areExtraMouseButtonsEnabled() returns true by default.
// This must initlizes the Toolkit class.
//3) Verifies that System.getProperty("sun.awt.enableExtraMouseButtons") returns true (default).
import java.awt.*;

public class SystemPropTest_1 {

    public static void main(String []s){
        boolean propValue = Boolean.parseBoolean(System.getProperty("sun.awt.enableExtraMouseButtons"));
        System.out.println("1. System.getProperty = " + propValue);
        if (propValue){
            throw new RuntimeException("TEST FAILED(1) : System property sun.awt.enableExtraMouseButtons = " + propValue);
        }
        if (!Toolkit.getDefaultToolkit().areExtraMouseButtonsEnabled()){
            throw new RuntimeException("TEST FAILED : Toolkit.areExtraMouseButtonsEnabled() returns false");
        }

        System.getProperties().list(System.out);
        System.out.println("XXXX. System.getProperty = " + System.getProperty("sun.awt.enableExtraMouseButtons"));

        propValue = Boolean.parseBoolean(System.getProperty("sun.awt.enableExtraMouseButtons"));
        System.out.println("2. System.getProperty = " + propValue);
        if (!propValue){
            throw new RuntimeException("TEST FAILED(2) : System property sun.awt.enableExtraMouseButtons = " + propValue);
        }
        System.out.println("Test passed.");
    }
}
