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
import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.Robot;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/*
 * @test
 * @key headful
 * @bug  8277369
 * @summary Verifies arrow traversal in RTL orientation in JMenuBar
 */
public class MenuBarRTLBug {

    static JFrame frame;
    static JMenuBar menuBar;
    static JMenu firstMenu;
    static JMenuItem a;
    static JMenuItem b;
    static JMenu secondMenu;
    static JMenuItem c;
    static JMenuItem d;
    static JMenu thirdMenu;
    static JMenuItem e;
    static JMenuItem f;
    static JMenu forthMenu;
    static JMenu fifthMenu;

    static Point p;
    static int width;
    static int height;

    static volatile boolean passed = false;

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

    public static void main(String[] args) throws  Exception {

        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing L&F: " + laf.getClassName());
            passed = false;
            SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
            try {
                SwingUtilities.invokeAndWait(() -> {
                    frame = new JFrame();
                    frame.setLayout(new BorderLayout());

                    firstMenu = new JMenu("first");
                    a = new JMenuItem("a");
                    b = new JMenuItem("b");
                    firstMenu.add(a);
                    firstMenu.add(b);

                    secondMenu = new JMenu("second");
                    c = new JMenuItem("c");
                    d = new JMenuItem("d");
                    secondMenu.add(c);
                    secondMenu.add(d);
                    secondMenu.addMenuListener(new MenuListener() {
                        @Override
                        public void menuSelected(MenuEvent e) {
                            passed = true;
                        }
                        @Override
                        public void menuDeselected(MenuEvent e) {
                        }

                        @Override
                        public void menuCanceled(MenuEvent e) {
                        }
                    });

                    thirdMenu = new JMenu("third");
                    e = new JMenuItem("e");
                    f = new JMenuItem("f");
                    thirdMenu.add(e);
                    thirdMenu.add(f);

                    forthMenu = new JMenu("fourth");
                    e = new JMenuItem("e");
                    f = new JMenuItem("f");
                    forthMenu.add(e);
                    forthMenu.add(f);

                    fifthMenu = new JMenu("fifth");
                    e = new JMenuItem("e");
                    f = new JMenuItem("f");
                    fifthMenu.add(e);
                    fifthMenu.add(f);

                    menuBar = new JMenuBar();
                    menuBar.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                    menuBar.add(firstMenu);
                    menuBar.add(secondMenu);
                    menuBar.add(thirdMenu);
                    menuBar.add(forthMenu);
                    menuBar.add(fifthMenu);
                    frame.setJMenuBar(menuBar);

                    frame.setLocationRelativeTo(null);
                    frame.pack();
                    frame.setVisible(true);
                });
                Robot robot = new Robot();
                robot.setAutoDelay(100);
                robot.waitForIdle();
                robot.delay(1000);
                SwingUtilities.invokeAndWait(() -> {
                    p = thirdMenu.getLocationOnScreen();
                    width = thirdMenu.getWidth();
                    height = thirdMenu.getHeight();
                });
                robot.mouseMove(p.x + width / 2, p.y + height / 2);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                robot.delay(1000);
                robot.keyPress(KeyEvent.VK_RIGHT);
                robot.keyRelease(KeyEvent.VK_RIGHT);
                robot.delay(1000);
                if (!passed) {
                    throw new RuntimeException("Arrow traversal order not correct in RTL orientation");
                }
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    if (frame != null) {
                        frame.dispose();
                    }
                });
            }
        }
    }
}
