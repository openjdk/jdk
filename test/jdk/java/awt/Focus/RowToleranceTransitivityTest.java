/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug       5070991
  @key headful
  @summary   Tests for a transitivity problem with ROW_TOLERANCE in SortingFTP.
  @run       main RowToleranceTransitivityTest
*/

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.util.concurrent.atomic.AtomicBoolean;

public class RowToleranceTransitivityTest {
    static JFrame frame;
    static JPanel panel;
    static JFormattedTextField ft;
    static JCheckBox cb;
    static GridBagConstraints gc;
    static Robot robot;

    static final AtomicBoolean focusGained = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(100);
        robot.setAutoWaitForIdle(true);
        try {
            EventQueue.invokeAndWait(() -> {
                gc = new GridBagConstraints();
                frame = new JFrame("JFrame");
                JPanel panel = new JPanel(new GridBagLayout());
                ft = new JFormattedTextField();
                cb = new JCheckBox("JCheckBox");
                Dimension dim = new Dimension(100, ft.getPreferredSize().height);
                ft.setPreferredSize(dim);
                ft.setMinimumSize(dim);
                gc.gridx = 5;
                gc.gridy = 1;
                gc.gridwidth = 10;
                panel.add(ft, gc);

                gc.gridy = 3;
                panel.add(cb, gc);

                cb.addFocusListener(new FocusAdapter() {
                    public void focusGained(FocusEvent e) {
                        System.out.println(e.toString());
                        synchronized (focusGained) {
                            focusGained.set(true);
                            focusGained.notifyAll();
                        }
                    }
                });

                gc.weightx = 1.0;
                gc.gridwidth = 1;
                gc.gridy = 0;
                gc.gridx = 0;
                for (int n = 0; n < 7; n++) {
                    panel.add(getlabel(), gc);
                    gc.gridy++;
                }

                gc.gridx = 0;
                gc.gridy = 0;
                for (int n = 0; n < 7; n++) {
                    panel.add(getlabel(), gc);
                    gc.gridx++;
                }

                frame.getContentPane().add(panel);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                frame.setAlwaysOnTop(true);

            });
            robot.waitForIdle();
            robot.delay(1000);
            test();
        } finally {
            robot.keyRelease(KeyEvent.VK_TAB);
            if (frame != null) {
                frame.dispose();
            }
        }
    }

    static void test() throws Exception {
        robot.delay(500);

        // Set focus on the first component to start traversal
        if (!setFocusOn(ft, new Runnable() {
            public void run() {
                clickOn(ft);
            }
            })) {
            System.out.println("Couldn't set focus on " + ft);
            throw new RuntimeException("Test couldn't be performed.");
        }

        robot.delay(500);

        // Try to traverse
        if (!setFocusOn(cb, new Runnable() {
            public void run() {
                robot.keyPress(KeyEvent.VK_TAB);
            }
            })) {
            System.out.println("Focus got stuck while traversing.");
            throw new RuntimeException("Test failed!");
        }

        System.out.println("Test passed.");
    }

    static boolean setFocusOn(Component comp, Runnable action) {

        if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == comp) {
            System.out.println("Already focus owner: " + comp);
            return true;
        }

        focusGained.set(false);

        System.out.println("Setting focus on " + comp);

        comp.addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) {
                    System.out.println(e.toString());
                    synchronized (focusGained) {
                        focusGained.set(true);
                        focusGained.notifyAll();
                    }
                }
            });

        action.run();

        synchronized (focusGained) {
            if (!focusGained.get()) {
                try {
                    focusGained.wait(3000);
                } catch (InterruptedException e) {
                    System.out.println("Unexpected exception caught!");
                    throw new RuntimeException(e);
                }
            }
        }

        return focusGained.get();
    }

    static JLabel getlabel(){
        Dimension dim = new Dimension(5, 9); // LayoutComparator.ROW_TOLERANCE = 10;
        JLabel l = new JLabel("*");
        l.setMinimumSize(dim);
        l.setMaximumSize(dim);
        l.setPreferredSize(dim);
        return l;
    }

    static void clickOn(Component c) {
        Point p = c.getLocationOnScreen();
        Dimension d = c.getSize();

        System.out.println("Clicking " + c);

        if (c instanceof Frame) {
            robot.mouseMove(p.x + (int)(d.getWidth()/2), p.y + ((Frame)c).getInsets().top/2);
        } else {
            robot.mouseMove(p.x + (int)(d.getWidth()/2), p.y + (int)(d.getHeight()/2));
        }
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
    }

}
