/*
 * Copyright (c) 2007, 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4959409
 * @summary Check whether pressing SHIFT + 1 triggers key event
 * @key headful
 */

import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.event.KeyAdapter;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class bug4959409 {

    public final static int TIMEOUT = 30;
    public final static int DELAY = 300;
    private static JFrame frame;
    private static JTextField jTextField;
    private static JLabel jLabel;

    public JFrame createUIAndTest() throws Exception {
        final CountDownLatch frameVisibleLatch = new CountDownLatch(1);
        final CountDownLatch componentVisibleLatch = new CountDownLatch(1);
        final CountDownLatch keyPressedEventLatch = new CountDownLatch(1);
        final Point points[] = new Point[1];
        final Rectangle rect[] = new Rectangle[1];

        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("Test bug4959409");
            jTextField = new JTextField();
            jLabel = new JLabel();
            frame.setLayout(new BorderLayout());
            frame.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(ComponentEvent e) {
                    super.componentShown(e);
                    System.out.println("Frame is visible " + e.toString());
                    frameVisibleLatch.countDown();
                }
            });

            jTextField.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(ComponentEvent e) {
                    super.componentShown(e);
                    System.out.println("Component is visible + " + e.toString());
                    componentVisibleLatch.countDown();
                }
            });

            jTextField.addKeyListener(new KeyAdapter() {

                @Override
                public void keyPressed(KeyEvent keyEvent) {
                    super.keyPressed(keyEvent);
                    int code = keyEvent.getKeyCode();
                    int mod = keyEvent.getModifiersEx();
                    if (code == '1' && mod == KeyEvent.SHIFT_DOWN_MASK) {
                        keyPressedEventLatch.countDown();
                        jLabel.setText("KEYPRESS received for Shift+1");
                        System.out.println("KEYPRESS received for Shift+1");
                    } else {
                        jLabel.setText("Did not received KEY PRESS for Shift+1");
                        System.out.println("Did not received KEY PRESS for Shift+1");
                    }
                }
            });

            Container container = frame.getContentPane();
            container.add(jTextField, BorderLayout.SOUTH);
            container.add(jLabel, BorderLayout.CENTER);
            frame.setSize(300, 300);
            frame.setLocationRelativeTo(null);
            frame.setAlwaysOnTop(true);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });

        boolean isFrameVisible = frameVisibleLatch.await(TIMEOUT, TimeUnit.SECONDS);
        if (!isFrameVisible) {
            throw new RuntimeException("Frame is not visible after " + TIMEOUT + "  seconds");
        }

        if (!isComponentVisible(jTextField, componentVisibleLatch)) {
            throw new RuntimeException("Component is not visible after " + TIMEOUT + "   seconds");
        }

        Robot robot = new Robot();
        robot.delay(DELAY);
        robot.waitForIdle();

        SwingUtilities.invokeAndWait(() -> {
            points[0] = jTextField.getLocationOnScreen();
            rect[0] = jTextField.getBounds();
        });

        performMouseAction(robot, points[0].x + rect[0].width / 2, points[0].y + rect[0].height / 2);

        // Press SHIFT + 1 keys
        robot.delay(DELAY);
        robot.keyPress(KeyEvent.VK_SHIFT);
        robot.delay(DELAY);
        robot.keyPress(KeyEvent.VK_1);
        robot.delay(DELAY);
        robot.keyRelease(KeyEvent.VK_SHIFT);
        robot.delay(DELAY);
        robot.keyRelease(KeyEvent.VK_1);
        robot.delay(DELAY);
        robot.waitForIdle();

        if (!keyPressedEventLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
            throw new RuntimeException("Did not received KEY PRESS for Shift + 1 , test failed");
        }
        return frame;
    }

    public static void performMouseAction(final Robot robot, final int X, final int Y) {
        robot.waitForIdle();
        robot.delay(DELAY);
        robot.mouseMove(X, Y);
        robot.delay(DELAY);
        robot.waitForIdle();

        robot.delay(DELAY);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(DELAY);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(DELAY);
        robot.waitForIdle();
    }

    public static boolean isComponentVisible(java.awt.Component component, CountDownLatch componentVisibleLatch) throws Exception {
        int count = 1;
        if (!component.isVisible()) {
            while (count <= 5) {
                TimeUnit.SECONDS.sleep(1);
                if (component.isVisible()) {
                    componentVisibleLatch.countDown();
                    System.out.println("Component is visible " + component.toString());
                    break;
                }
                count++;
            }
        } else {
            componentVisibleLatch.countDown();
            System.out.println("Component is visible " + component.toString());
        }
        return componentVisibleLatch.await(TIMEOUT, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws Exception {
        bug4959409 bug4959409 = new bug4959409();
        final JFrame[] jFrames = new JFrame[1];
        try {
            jFrames[0] = bug4959409.createUIAndTest();
        } finally {
            if (jFrames[0] != null) {
                SwingUtilities.invokeAndWait(()-> {
                    jFrames[0].dispose();
                });
            }
        }
    }
}

