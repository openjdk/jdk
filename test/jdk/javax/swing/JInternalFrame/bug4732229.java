/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4732229
 * @summary Ctrl+Space, bringing up System menu on a JIF errors using Win LAF
 * @key headful
 * @run main bug4732229
 */

import javax.swing.JFrame;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;

public class bug4732229 {

    JFrame frame;
    JDesktopPane desktop;
    JInternalFrame jif;
    JTextArea ta;
    Robot robot;
    volatile boolean keyTyped = false;

    public static void main(String[] args) throws Exception {
        bug4732229 b = new bug4732229();
        b.init();
    }

    public void init() throws Exception {
        robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("bug4732229");
                desktop = new JDesktopPane();
                frame.getContentPane().add(desktop);

                ta = new JTextArea();
                ta.addFocusListener(new FocusAdapter() {
                    public void focusGained(FocusEvent e) {
                        synchronized (bug4732229.this) {
                            keyTyped = true;
                            bug4732229.this.notifyAll();
                        }
                    }
                });
                frame.setSize(200, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                jif = new JInternalFrame("Internal Frame", true, false, true,
                        true);
                jif.setBounds(10, 10, 100, 100);
                jif.getContentPane().add(ta);
                jif.setVisible(true);
                desktop.add(jif);
                try {
                    jif.setSelected(true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            });
            synchronized (this) {
                while (!keyTyped) {
                    bug4732229.this.wait();
                }
            }
            robot.waitForIdle();
            robot.delay(200);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_SPACE);
            robot.keyRelease(KeyEvent.VK_SPACE);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            robot.waitForIdle();
            robot.delay(200);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    jif.setSelected(false);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                jif.setVisible(false);
                desktop.remove(jif);
                try {
                    UIManager.setLookAndFeel(
                            UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                desktop.updateUI();

                jif = new JInternalFrame("Internal Frame", true, false, true,
                        true);
                jif.setBounds(10, 10, 100, 100);
                jif.getContentPane().add(ta);
                jif.setVisible(true);
                desktop.add(jif);
                try {
                    jif.setSelected(true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            synchronized (this) {
                while (!keyTyped) {
                    bug4732229.this.wait();
                }
            }
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_SPACE);
            robot.keyRelease(KeyEvent.VK_SPACE);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            robot.waitForIdle();
            robot.delay(200);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
