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
 * @run main/timeout=60 TestKeyEventTriggeredForShiftPlusOne
 */

import java.awt.AWTException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class TestKeyEventTriggeredForShiftPlusOne {

    public final static int TIMEOUT = 30;
    public final static int DELAY = 300;
    private static JFrame frame;
    private static JTextField jTextField;
    private static final CountDownLatch keyPressedEventLatch =
            new CountDownLatch(1);

    public static void createUI() throws InterruptedException, InvocationTargetException {

        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("Test bug4959409");
            jTextField = new JTextField();

            jTextField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent keyEvent) {
                    super.keyPressed(keyEvent);
                    int code = keyEvent.getKeyCode();
                    int mod = keyEvent.getModifiersEx();
                    if (code == '1' && mod == KeyEvent.SHIFT_DOWN_MASK) {
                        keyPressedEventLatch.countDown();
                        System.out.println("Triggered keyPressed event when " +
                                "Shift + 1 is pressed.");
                    }
                }
            });

            frame.setContentPane(jTextField);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setAlwaysOnTop(true);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }

    public static void test() throws AWTException, InterruptedException, InvocationTargetException {
        final Point[] points = new Point[1];
        final Rectangle[] rect = new Rectangle[1];
        Robot robot = new Robot();
        robot.setAutoDelay(DELAY);
        robot.waitForIdle();

        // Making sure that JTextField and its parent is visible
        // before performing any interaction with the UI
        waitForComponentToVisible(jTextField);

        SwingUtilities.invokeAndWait(() -> {
            points[0] = jTextField.getLocationOnScreen();
            rect[0] = jTextField.getBounds();
        });

        robot.waitForIdle();
        robot.mouseMove(points[0].x + rect[0].width / 2,
                points[0].y + rect[0].height / 2);

        robot.waitForIdle();
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();

        // Press SHIFT + 1 keys
        robot.waitForIdle();
        robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(KeyEvent.VK_1);
        robot.keyRelease(KeyEvent.VK_1);
        robot.keyRelease(KeyEvent.VK_SHIFT);
        robot.waitForIdle();

        if (!keyPressedEventLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
            throw new RuntimeException("KeyPress event did not trigger after " +
                    "pressing Shift + 1 keys");
        }
    }

    public static boolean isComponentVisible(JTextField jTextField)
            throws InterruptedException, InvocationTargetException {
        AtomicBoolean componentVisibleFlag = new AtomicBoolean(false);
        SwingUtilities.invokeAndWait(() -> {
            componentVisibleFlag.set(jTextField.isShowing());
        });
        return componentVisibleFlag.get();
    }

    public static void waitForComponentToVisible(JTextField jTextField)
            throws InterruptedException, InvocationTargetException {
        int count = 0;
        do {
            if ( isComponentVisible(jTextField) ) {
                return;
            }
            TimeUnit.SECONDS.sleep(1);
        } while (++count <=5 );
        throw new RuntimeException(jTextField + " is not visible after 5 seconds");
    }

    public static void main(String[] args)
            throws InterruptedException, InvocationTargetException, AWTException {
        try {
            createUI();
            test();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
