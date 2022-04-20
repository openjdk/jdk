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

/*
 * @test
 * @bug 4959409
 * @summary Check whether pressing SHIFT + 1 triggers key event
 * @key headful
 */

import java.awt.Container;
import java.awt.Robot;
import java.awt.Rectangle;
import java.awt.Point;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.InputEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

public class bug4959409 {

    public final static int TIMEOUT = 20;
    public final static int DELAY = 300;
    private static JFrame frame;
    private static JTextField jTextField;
    private static JLabel jLabel;

    public static void createUIAndTest() throws Exception {
        CountDownLatch frameVisibleLatch = new CountDownLatch(1);
        CountDownLatch keyPressedEventLatch = new CountDownLatch(1);
        final Point[] points = new Point[1];
        final Rectangle[] rect = new Rectangle[1];

        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("Test bug4959409");
            jTextField = new JTextField();
            jLabel = new JLabel();
            frame.setLayout(new BorderLayout());
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowOpened(WindowEvent e) {
                    super.windowOpened(e);
                    frameVisibleLatch.countDown();
                    System.out.println("Frame opened");
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
                        jLabel.setText("keyPressed received for Shift+1");
                        System.out.println("keyPressed received for Shift+1");
                    } else {
                        jLabel.setText("Did not receive keyPressed for Shift+1");
                        System.out.println("Did not receive keyPressed for Shift+1");
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

        Robot robot = new Robot();
        robot.setAutoDelay(DELAY);
        robot.waitForIdle();

        if (!frameVisibleLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
            throw new RuntimeException("Frame is not visible after " + TIMEOUT + "  seconds");
        }

        SwingUtilities.invokeAndWait(() -> {
            points[0] = jTextField.getLocationOnScreen();
            rect[0] = jTextField.getBounds();
        });

        clickTextField(robot, points[0].x + rect[0].width / 2,
                points[0].y + rect[0].height / 2);

        // Press SHIFT + 1 keys
        robot.waitForIdle();
        robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(KeyEvent.VK_1);
        robot.keyRelease(KeyEvent.VK_1);
        robot.keyRelease(KeyEvent.VK_SHIFT);
        robot.waitForIdle();

        if (!keyPressedEventLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
            throw new RuntimeException("Did not receive keyPressed for Shift + 1 , test failed");
        }
    }

    public static void clickTextField(final Robot robot, final int X, final int Y) {
        robot.delay(DELAY);
        robot.waitForIdle();
        robot.mouseMove(X, Y);
        robot.waitForIdle();

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
    }

    public static void main(String[] args) throws Exception {
        try {
            createUIAndTest();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}

