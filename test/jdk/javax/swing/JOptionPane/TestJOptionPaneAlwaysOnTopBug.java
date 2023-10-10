/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6690019
 * @key headful
 * @summary  Verifies if JOptionPane obscured behind an always on top JFrame
 *           hangs UI
 * @run main TestJOptionPaneAlwaysOnTopBug
 */

import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;

public class TestJOptionPaneAlwaysOnTopBug {
    static JFrame alwaysOnTopFrame;
    static JFrame normalFrame;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                alwaysOnTopFrame = new JFrame("Test JOptionPane");
                alwaysOnTopFrame.setSize(600, 400);
                centreFrame(alwaysOnTopFrame);
                alwaysOnTopFrame.setAlwaysOnTop(true);
                alwaysOnTopFrame.setVisible(true);

                normalFrame = new JFrame();
                normalFrame.setSize(800, 600);
                centreFrame(normalFrame);
                normalFrame.setVisible(true);

                new Thread() {
                    public void run() {
                        robot.waitForIdle();
                        robot.delay(3000);
                        robot.keyPress(KeyEvent.VK_ENTER);
                        robot.keyRelease(KeyEvent.VK_ENTER);
                        System.out.println("always-on-top: " +
                            KeyboardFocusManager.getCurrentKeyboardFocusManager().
                                           getFocusedWindow().isAlwaysOnTop());
                        if (KeyboardFocusManager.getCurrentKeyboardFocusManager().
                                    getFocusedWindow() instanceof JDialog dialog) {
                            if (!dialog.isAlwaysOnTop()) {
                                throw new RuntimeException("Dialog not on top");
                            }
                        }
                    }
                }.start();
                JOptionPane option = new JOptionPane();
                option.showMessageDialog(normalFrame, "Can't you see me?");
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (alwaysOnTopFrame != null) {
                    alwaysOnTopFrame.dispose();
                }
                if (normalFrame != null) {
                    normalFrame.dispose();
                }
            });
        }
    }

    private static void centreFrame(JFrame myFrame)  {
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (d.width - myFrame.getWidth()) / 2;
        int y = (d.height - myFrame.getHeight()) / 2;
        myFrame.setLocation(new Point(x, y));
    }
}

