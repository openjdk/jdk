
/* Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Robot;
import java.awt.desktop.QuitEvent;
import java.awt.desktop.QuitHandler;
import java.awt.desktop.QuitResponse;
import java.awt.desktop.QuitStrategy;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/*
 * @test
 * @key headful
 * @bug 8297095
 * @summary To determine the quit request of the application
 * @run main ActiveWindowCloseTest
 */
public class ActiveWindowCloseTest extends JFrame implements QuitHandler {
    private static ActiveWindowCloseTest frame = null;
    private static volatile int count = 0;
    private static Robot robot;
    private static boolean passed = false;

    private ActiveWindowCloseTest(String name) {
        super(name);
    }

    @Override
    public void handleQuitRequestWith(QuitEvent e, QuitResponse response) {
        if (count++ == 2) {
            System.out.println("Received performQuit()");
            response.performQuit();
        } else {
            System.out.println("Received cancelQuit()");
            response.cancelQuit();
        }
    }

    private static void createAndShow() {
        try {
            frame = new ActiveWindowCloseTest("Close windows");
            Desktop.getDesktop().setQuitHandler(frame);
            Desktop.getDesktop().disableSuddenTermination();
            Desktop.getDesktop()
                .setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            passed = true;
        } catch (UnsupportedOperationException usoe) {
            System.out.println("Test skipped, as the action "
                + "is not supported on the current platform!");
        }
    }

    public static void main(String args[])
        throws AWTException, InvocationTargetException, InterruptedException {
        boolean isMac = false;
        try {
            if (System.getProperty("os.name").toLowerCase().contains("os x")) {
                isMac = true;
            }
            SwingUtilities.invokeAndWait(ActiveWindowCloseTest::createAndShow);

            if (passed) {
                robot = new Robot();
                robot.setAutoDelay(50);
                robot.setAutoWaitForIdle(true);
                robot.waitForIdle();

                for (int i = 0; i < 3; i++) {
                    if (frame.isActive()) {
                        if (isMac) {
                            robot.keyPress(KeyEvent.VK_META);
                            robot.keyPress(KeyEvent.VK_Q);
                            robot.keyRelease(KeyEvent.VK_Q);
                            robot.keyRelease(KeyEvent.VK_META);
                        } else {
                            robot.keyPress(KeyEvent.VK_ALT);
                            robot.keyPress(KeyEvent.VK_F4);
                            robot.keyRelease(KeyEvent.VK_F4);
                            robot.keyRelease(KeyEvent.VK_ALT);
                        }
                        robot.waitForIdle();
                    }
                }
                System.out.println("Test passed, by closing the application.");
            }
        } finally {
            SwingUtilities.invokeAndWait(ActiveWindowCloseTest::disposeFrame);
        }
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }

}
