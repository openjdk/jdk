/*
 * @test
 * @bug 7122796
 * @summary Tests 7122796
 * @author anthony.petrov@oracle.com
 */

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import sun.awt.*;

public class MainAppContext {

    public static void main(String[] args) {
        ThreadGroup secondGroup = new ThreadGroup("test");
        new Thread(secondGroup, new Runnable() {
                public void run() {
                    SunToolkit.createNewAppContext();
                    test(true);
                }
            }).start();

        // Sleep on the main thread so that the AWT Toolkit is initialized
        // in a user AppContext first
        try { Thread.sleep(2000); } catch (Exception e) {}

        test(false);
    }

    private static void test(boolean userAppContext) {
        if (Toolkit.getDefaultToolkit().getSystemEventQueue() == null) {
            throw new RuntimeException("No EventQueue for the current app context! userAppContext: " + userAppContext);
        }
    }
}
