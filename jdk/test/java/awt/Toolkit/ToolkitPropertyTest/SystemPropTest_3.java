/*
  @test %I% %E%
  @bug 6315717
  @summary verifies that system property sun.awt.enableExtraMouseButtons might be set to false by the command line
  @author Andrei Dmitriev : area=awt.mouse
  @run main/othervm -Dsun.awt.enableExtraMouseButtons=false SystemPropTest_3
 */
//1) Verifies that System.getProperty("sun.awt.enableExtraMouseButtons") returns false if set through the command line.
//2) Verifies that Toolkit.areExtraMouseButtonsEnabled() returns false if the proprty is set through the command line.
import java.awt.*;

public class SystemPropTest_3 {

    public static void main(String []s){
        boolean propValue = Boolean.parseBoolean(System.getProperty("sun.awt.enableExtraMouseButtons"));
        System.out.println("Test System.getProperty = " + System.getProperty("sun.awt.enableExtraMouseButtons"));
        System.out.println("System.getProperty = " + propValue);
        if (propValue){
            throw new RuntimeException("TEST FAILED : System property sun.awt.enableExtraMouseButtons = " + propValue);
        }
        if (Toolkit.getDefaultToolkit().areExtraMouseButtonsEnabled()){
            throw new RuntimeException("TEST FAILED : Toolkit.areExtraMouseButtonsEnabled() returns true");
        }
        System.out.println("Test passed.");
    }
}
