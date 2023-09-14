/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4703690
 * @summary JTabbedPane should focus proper component at the tab container
 * @key headful
 * @run main bug4703690
 */

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public class bug4703690 {
    static JFrame fr;
    static JTabbedPane tabbedPane;
    static JPanel panel;
    static JButton one, two;

    static volatile boolean focusButtonTwo = false;
    static volatile boolean switchToTabTwo = false;
    static volatile boolean focusButtonOne = false;
    static Robot robot;

    static Point p;
    static Rectangle rect;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        bug4703690 test = new bug4703690();
        try {
            SwingUtilities.invokeAndWait(() -> {
                fr = new JFrame("bug4703690");

                panel = new JPanel();
                one = new JButton("Button 1");
                panel.add(one);
                two = new JButton("Button 2");
                panel.add(two);

                tabbedPane = new JTabbedPane();
                fr.getContentPane().add(tabbedPane);
                tabbedPane.addTab("Tab one", panel);
                tabbedPane.addTab("Tab two", new JPanel());

                two.addFocusListener(new FocusAdapter() {
                    public void focusGained(FocusEvent e) {
                        focusButtonTwo = true;
                    }
                });

                tabbedPane.addChangeListener(e -> {
                    if (tabbedPane.getSelectedIndex() == 1) {
                        switchToTabTwo = true;
                    }
                });

                fr.setBounds(10, 10, 200, 200);
                fr.setVisible(true);
                fr.setLocationRelativeTo(null);
            });

            test.execute();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
    }

    public void execute() {
        try {
            robot = new Robot();
            robot.setAutoDelay(50);
            robot.delay(1000);
            two.requestFocus();

            one.addFocusListener(new FocusAdapter() {
                    public void focusGained(FocusEvent e) {
                        focusButtonOne = true;
                    }
                });

            SwingUtilities.invokeAndWait(() -> {
                p = tabbedPane.getLocationOnScreen();
                rect = tabbedPane.getBoundsAt(1);
            });

            robot.delay(1000);
            robot.mouseMove(p.x + rect.x + rect.width / 2,
                            p.y + rect.y + rect.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                p = tabbedPane.getLocationOnScreen();
                rect = tabbedPane.getBoundsAt(0);
            });

            robot.delay(1000);
            robot.mouseMove(p.x + rect.x + rect.width / 2,
                            p.y + rect.y + rect.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } catch (Exception t) {
            throw new RuntimeException("Test failed", t);
        }

        if (!focusButtonOne) {
            throw new RuntimeException("The 'Button 1' button doesn't have focus");
        }
    }
}
