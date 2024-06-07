/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.PopupMenuEvent;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 5091257
 * @summary Application key does not display a pop-up menu in users view.
 * @key headful
 * @run main bug5091257
 */

public class bug5091257 {
    public static volatile boolean passed = false;
    public static volatile boolean isKeyOk = false;
    public static JFrame mainFrame;
    public static JButton button;
    public static Robot robot;
    public static JPopupMenu popup;
    public static volatile Point loc;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.setAutoDelay(50);
            SwingUtilities.invokeAndWait(() -> {
                button = new JButton("Popup button");
                button.addKeyListener(new KeyListener() {
                    public void keyTyped(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_CONTEXT_MENU) {
                            isKeyOk = true;
                        }
                    }

                    public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_CONTEXT_MENU) {
                            isKeyOk = true;
                        }
                    }

                    public void keyReleased(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_CONTEXT_MENU) {
                            isKeyOk = true;
                        }
                    }
                });
                mainFrame = new JFrame("Bug5091257");
                mainFrame.setLayout(new BorderLayout());
                mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                mainFrame.add(button, BorderLayout.CENTER);
                mainFrame.pack();
                mainFrame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                loc = button.getLocationOnScreen();
                loc.x = loc.x + (int) (button.getWidth() / 2);
                loc.y = loc.y + (int) (button.getHeight() / 2);
            });
            robot.mouseMove(loc.x, loc.y);
            robot.mousePress(MouseEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(MouseEvent.BUTTON3_DOWN_MASK);

            robot.waitForIdle();
            robot.delay(100);

            try {
                robot.keyPress(KeyEvent.VK_CONTEXT_MENU);
                robot.keyRelease(KeyEvent.VK_CONTEXT_MENU);
            } catch (IllegalArgumentException ex) {
                isKeyOk = false;
            }

            if (!isKeyOk) {
                System.out.println("KeyEvent can't create or deliver " +
                        "VK_CONTEXT_MENU event to component. Testing skipped.");
                passed = true;
            } else {
                SwingUtilities.invokeAndWait(() -> {
                    popup = new JPopupMenu();
                    popup.add("Item to make popup not empty");
                    popup.addPopupMenuListener(new PopupMenuListener() {
                        public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                            System.out.println("Popup menu became visible " +
                                    "on context menu key press. Test passed.");
                            passed = true;
                        }

                        public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                        }

                        public void popupMenuCanceled(PopupMenuEvent e) {
                        }
                    });
                    button.setComponentPopupMenu(popup);
                });
                robot.keyPress(KeyEvent.VK_CONTEXT_MENU);
                robot.keyRelease(KeyEvent.VK_CONTEXT_MENU);

                robot.waitForIdle();
                robot.delay(100);

                if (!passed) {
                    throw new RuntimeException("Popup did not open on " +
                            "VK_CONTEXT_MENU press. Test failed.");
                }
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (mainFrame != null) {
                    mainFrame.dispose();
                }
            });
        }
    }
}
