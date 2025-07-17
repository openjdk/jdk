/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4403749
 * @summary Tests that keyboard accelerator implementation in JMenuBar is
            MenuElement aware
 * @key headful
 * @run main bug4403749
 */

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

public class bug4403749 {
    static JFrame frame;
    static volatile Point pt;
    static volatile Dimension dim;
    static volatile boolean passed;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("bug4403749");
                JMenuBar mbar = new JMenuBar();
                JMenu menu = new JMenu("Menu");
                JPanel panel = new TestMenuElement();
                menu.add(panel);
                mbar.add(menu);
                frame.setJMenuBar(mbar);

                frame.getContentPane().add(new JButton(""));
                frame.setSize(200, 200);
                frame.setLocationRelativeTo(null);
                frame.setAlwaysOnTop(true);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                pt = frame.getLocationOnScreen();
                dim = frame.getSize();
            });
            robot.mouseMove(pt.x + dim.width / 2, pt.y + dim.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(200);
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_ALT);
            if (!passed) {
                throw new RuntimeException("Failed: processKeyBinding wasn't called");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                 }
            });
        }
    }

    static class TestMenuElement extends JPanel implements MenuElement {
        public void processMouseEvent(MouseEvent event,
                                      MenuElement[] path,
                                      MenuSelectionManager manager) {}

        public void processKeyEvent(KeyEvent event,
                                    MenuElement[] path,
                                    MenuSelectionManager manager) {}

        public void menuSelectionChanged(boolean isIncluded) {}

        public MenuElement[] getSubElements() {
            return new MenuElement[0];
        }

        public Component getComponent() {
            return this;
        }

        protected boolean processKeyBinding(KeyStroke ks, KeyEvent e,
                                            int condition, boolean pressed) {
            passed = true;
            return super.processKeyBinding(ks, e, condition, pressed);
        }
    }
}
