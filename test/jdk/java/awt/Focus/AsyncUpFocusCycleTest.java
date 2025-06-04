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
  @test
  @bug 4394789
  @summary KeyboardFocusManager.upFocusCycle is not working for Swing properly
  @key headful
  @run main AsyncUpFocusCycleTest
*/


import javax.swing.DefaultFocusManager;
import javax.swing.JButton;
import javax.swing.JFrame;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.EventQueue;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;

public class AsyncUpFocusCycleTest {
    volatile boolean isFailed = true;
    Object sema = new Object();
    JFrame frame;
    Point location;
    JButton button;
    Insets insets;
    int width;

    public void start() throws InterruptedException,
            InvocationTargetException {
        try {
            Robot robot = new Robot();
            robot.mouseMove(100, 100);

            EventQueue.invokeAndWait(() -> {
                frame = new JFrame("AsyncUpFocusCycleTest") {
                    public void requestFocus() {
                        boolean ret = super.requestFocus(false);
                        System.err.println("requestFocus() on Frame " + ret);
                    }

                    protected boolean requestFocus(boolean temporary) {
                        boolean ret = super.requestFocus(temporary);
                        System.err.println("requestFocus(" + temporary + ") on Frame " + ret);
                        return ret;
                    }

                    public boolean requestFocusInWindow() {
                        boolean ret = super.requestFocusInWindow();
                        System.err.println("requestFocusInWindow() on Frame " + ret);
                        return ret;
                    }

                    protected boolean requestFocusInWindow(boolean temporary) {
                        boolean ret = super.requestFocusInWindow(temporary);
                        System.err.println("requestFocusInWindow(" + temporary + ") on Frame " + ret);
                        return ret;
                    }
                };

                Container container1 = frame.getContentPane();
                container1.setBackground(Color.yellow);

                button = new JButton("Button") {
                    public void requestFocus() {
                        boolean ret = super.requestFocus(false);
                        System.err.println("requestFocus() on Button " + ret);
                    }

                    public boolean requestFocus(boolean temporary) {
                        boolean ret = super.requestFocus(temporary);
                        System.err.println("requestFocus(" + temporary + ") on Button " + ret);
                        return ret;
                    }

                    public boolean requestFocusInWindow() {
                        boolean ret = super.requestFocusInWindow();
                        System.err.println("requestFocusInWindow() on Button " + ret);
                        return ret;
                    }

                    protected boolean requestFocusInWindow(boolean temporary) {
                        boolean ret = super.requestFocusInWindow(temporary);
                        System.err.println("requestFocusInWindow(" + temporary + ") on Button " + ret);
                        return ret;
                    }
                };
                button.addFocusListener(new FocusAdapter() {
                    public void focusGained(FocusEvent fe) {
                        System.out.println("Button receive focus");
                        frame.addFocusListener(new FocusAdapter() {
                            public void focusGained(FocusEvent fe) {
                                System.out.println("Frame receive focus");
                                synchronized (sema) {
                                    isFailed = false;
                                    sema.notifyAll();
                                }
                            }
                        });
                    }
                });
                container1.add(new JButton("empty button"), BorderLayout.WEST);
                container1.add(button, BorderLayout.EAST);
                frame.setBounds(0, 0, 300, 300);
                frame.setVisible(true);
            });

            robot.delay(2000);
            robot.waitForIdle();

            EventQueue.invokeAndWait(() -> {
                location = frame.getLocationOnScreen();
                insets = frame.getInsets();
                width = frame.getWidth();
            });

            robot.mouseMove(location.x + width / 2, location.y + insets.top / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            DefaultKeyboardFocusManager manager = new DefaultFocusManager();
            robot.delay(1000);
            EventQueue.invokeAndWait(button::requestFocus);
            robot.delay(1000);
            EventQueue.invokeAndWait(() -> {
                manager.upFocusCycle(button);
            });

            try {
                synchronized (sema) {
                    sema.wait(5000);
                }

                if (isFailed) {
                    System.out.println("Test FAILED");
                    throw new RuntimeException("Test FAILED");
                } else {
                    System.out.println("Test PASSED");
                }
            } catch (InterruptedException ie) {
                throw new RuntimeException("Test was interrupted");
            }
        } catch (AWTException e) {
            System.out.println("Problem creating Robot.");
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        AsyncUpFocusCycleTest test = new AsyncUpFocusCycleTest();
        test.start();
    }
}
