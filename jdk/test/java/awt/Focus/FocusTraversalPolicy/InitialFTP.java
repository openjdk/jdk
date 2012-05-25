/*
  @test
  @bug       7125044
  @summary   Tests defaut focus traversal policy in AWT & Swing toplevel windows.
  @author    anton.tarasov@sun.com: area=awt.focus
  @run       main InitialFTP_AWT
  @run       main InitialFTP_Swing
*/

import java.awt.FocusTraversalPolicy;
import java.awt.Window;

public class InitialFTP {
    public static void test(Window win, Class<? extends FocusTraversalPolicy> expectedPolicy) {
        FocusTraversalPolicy ftp = win.getFocusTraversalPolicy();

        System.out.println("==============" + "\n" +
                           "Tested window:    " + win + "\n" +
                           "Expected policy:  " + expectedPolicy + "\n" +
                           "Effective policy: " + ftp.getClass());

        if (!expectedPolicy.equals(ftp.getClass())) {
            throw new RuntimeException("Test failed: wrong effective focus policy");
        }
    }
}
