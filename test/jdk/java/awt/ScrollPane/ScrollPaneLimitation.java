/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4046446
  @requires os.family=="windows"
  @summary Tests 16-bit limitations of scroll pane, child's position and size
  and mouse coordinates
  @key headful
*/

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.ScrollPane;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;

import java.util.Properties;

public class ScrollPaneLimitation {
    final static int SCROLL_POS = 50000;
    public static boolean mouseWasPressed = false;
    public static Component child = null;
    private Object lock = new Object();
    public Frame frame;
    Robot robot;
    Point p;
    ScrollPane pane;

    public static void main(String[] args) throws Exception {
        ScrollPaneLimitation scrollTest = new ScrollPaneLimitation();

        scrollTest.init();
        scrollTest.start();
    }

    public void init() throws Exception {
        EventQueue.invokeAndWait(() -> {
            frame = new Frame("Scroll Pane Limitation");
            frame.setLayout(new BorderLayout());
        });
        robot = new Robot();
    }

    public void start() throws Exception {
        // Check for Win32 environment. This test is only for Win32.
        // Even more: not for Windows 98: it doesn't support scroll children that long.
        try {
            Properties prop = System.getProperties();
            String os = prop.getProperty("os.name", "");
            if (os != null && (os.indexOf("98") == -1)) {
                EventQueue.invokeAndWait(() -> {
                    pane = new ScrollPane();
                    frame.add(pane);
                    child = new MyPanel();
                    child.addMouseListener(new MouseAdapter() {
                        public void mousePressed(MouseEvent e) {
                            if (e.getID() == MouseEvent.MOUSE_PRESSED) {
                                if (e.getSource() == ScrollPaneLimitation.child
                                        && e.getY() > SCROLL_POS) {
                                    mouseWasPressed = true;
                                    synchronized (lock) {
                                        lock.notify();
                                    }
                                }
                            }
                        }
                    });
                    pane.add(child);
                    frame.setSize(200, 200);
                    frame.pack();
                    frame.setLocationRelativeTo(null);
                    frame.setVisible(true);
                    pane.doLayout();
                });

                robot.delay(1000);
                robot.waitForIdle();

                EventQueue.invokeAndWait(() -> {
                    p = child.getLocation();
                    System.out.println("Child's initial location " + p);
                    System.out.println("Pane's insets " + pane.getInsets());
                    pane.setScrollPosition(0, SCROLL_POS);
                    p = pane.getScrollPosition();
                    System.out.println("Scroll pos = " + p);
                    if (p.y != SCROLL_POS) {
                        throw new RuntimeException("wrong scroll position");
                    }
                    p = child.getLocation();
                    System.out.println("Child pos = " + p);
                    if (p.y != -SCROLL_POS) {
                        if (child.isLightweight()) {
                            // If it is lightweight it will always have (0, 0) location.
                            // Check location of its parent - it is Panel and it should
                            // be at (inset left, inset top + position)
                            Container cp = child.getParent();
                            p = cp.getLocation();
                            if (p.y != -SCROLL_POS) {
                                throw new RuntimeException("wrong child location");
                            }
                        } else {
                            throw new RuntimeException("wrong child location");
                        }
                    }

                    p = pane.getLocationOnScreen();
                    Dimension d = pane.getSize();
                    p.x += d.width / 2;
                    p.y += d.height / 2;
                });
                robot.mouseMove(p.x, p.y);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                robot.waitForIdle();
                synchronized (lock) {
                    try {
                        lock.wait(3000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException("test was interrupted");
                    }
                }
                if (!mouseWasPressed) {
                    throw new RuntimeException("mouse was not pressed");
                }
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static class MyPanel extends Component {
        public Dimension getPreferredSize() {
            return new Dimension(100, 100000);
        }
    }
}
