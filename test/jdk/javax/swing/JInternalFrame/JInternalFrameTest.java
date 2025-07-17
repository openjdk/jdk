/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
/*
 * @test
 * @key headful
 * @bug 6603771
 * @summary  Verifies Nimbus L&F: Ctrl+F7 keybinding for Jinternal Frame throws a NPE
 * @run main JInternalFrameTest
 */
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class JInternalFrameTest {
    static JFrame jFrame;
    static JInternalFrame iFrame;
    static boolean isAquaLAF;
    static int controlKey;

    volatile static boolean failed = false;

     private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported L&F: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        EventQueue.invokeAndWait(
            () -> Thread.currentThread().setUncaughtExceptionHandler(
                (t, e) -> {
                    failed = true;
                }
        ));
        for (UIManager.LookAndFeelInfo laf :
                 UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing L&F: " + laf.getClassName());
            SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
            try {
                SwingUtilities.invokeAndWait(() -> createUI());
                robot.waitForIdle();
                robot.delay(1000);

                Point pt = iFrame.getLocationOnScreen();
                Rectangle dim = iFrame.getBounds();
                robot.mouseMove(pt.x + dim.width/3, pt.y+10);
                robot.waitForIdle();
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                robot.waitForIdle();
                robot.delay(1000);
                robot.keyPress(KeyEvent.VK_CONTROL);
                robot.keyPress(KeyEvent.VK_F7);
                robot.keyRelease(KeyEvent.VK_F7);
                robot.keyRelease(KeyEvent.VK_CONTROL);
                robot.waitForIdle();
                robot.delay(1000);
                robot.keyPress(KeyEvent.VK_UP);
                robot.keyRelease(KeyEvent.VK_UP);
                robot.waitForIdle();
                robot.delay(1000);
                if (failed) {
                    throw new RuntimeException("Exception thrown");
                }
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    if (jFrame != null) {
                        jFrame.dispose();
                    }
                });
            }
        }
    }

    private static void createUI() {
        jFrame = new JFrame();

        JDesktopPane desktopPane = new JDesktopPane();

        iFrame =  new JInternalFrame("Test");
        iFrame.setTitle("InternalFrame");
        iFrame.setLocation(50, 50);
        iFrame.setSize(200, 200);
        iFrame.setVisible(true);

        desktopPane.add(iFrame);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(desktopPane, BorderLayout.CENTER);

        jFrame.add(panel, BorderLayout.CENTER);
        jFrame.setSize(400, 400);
        jFrame.setLocationRelativeTo(null);
        jFrame.setVisible(true);
    }
}
