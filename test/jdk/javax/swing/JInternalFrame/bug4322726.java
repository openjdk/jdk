/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4322726
 * @summary Tests that JInternalFrame throws ArrayIndexOutOfBoundsException when Control-F4 pressed
 * @key headful
 * @run main bug4322726
 */

import java.awt.event.KeyEvent;
import java.awt.Robot;

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.beans.PropertyVetoException;

public class bug4322726 {

    private static JFrame frame;
    private static JInternalFrame internalFrame;
    private static volatile boolean failed;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("bug4322726");
                frame.setSize(600, 400);
                TestDesktopPane desktopPane = new TestDesktopPane();
                frame.setContentPane(desktopPane);
                internalFrame = new JInternalFrame();
                internalFrame.setClosable(true);
                internalFrame.setMaximizable(true);
                internalFrame.setIconifiable(true);
                internalFrame.setResizable(true);
                internalFrame.setTitle("Internal Frame");
                internalFrame.setSize(300, 200);
                internalFrame.setVisible(true);
                desktopPane.add(internalFrame);

                frame.setSize(400, 400);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    internalFrame.setSelected(true);
                } catch (PropertyVetoException e) {
                    throw new RuntimeException("PropertyVetoException thrown");
                }
            });
            robot.waitForIdle();
            robot.delay(200);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_F4);
            robot.keyRelease(KeyEvent.VK_F4);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            robot.waitForIdle();
            robot.delay(200);
            if (failed) {
                throw new RuntimeException("Failed: index is out of bounds");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    static class TestDesktopPane extends JDesktopPane {
        protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
            try {
                return super.processKeyBinding(ks, e, condition, pressed);
            } catch (ArrayIndexOutOfBoundsException ex) {
                failed = true;
            }
            return failed;
        }
    }
}
