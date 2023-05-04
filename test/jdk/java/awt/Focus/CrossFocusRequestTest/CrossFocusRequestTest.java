/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTException;
import java.awt.Button;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/*
 * @test
 * @key headful
 * @bug 8307325
 * @summary Verify the focus owner when non focused windows requesting focus.
 * @run main CrossFocusRequestTest
 */
public class CrossFocusRequestTest {

    private static Frame frame1, frame2;
    private volatile static Button button;
    private volatile static TextField textField;
    private volatile static int waitTimeout = 1000;
    private static final CountDownLatch clickLatch = new CountDownLatch(1);
    private static final CountDownLatch focusLatch = new CountDownLatch(1);
    private volatile static Point compAt;
    private volatile static Dimension compSize;

    public static void main(String[] args)
        throws InvocationTargetException, InterruptedException, AWTException {

        try {
            EventQueue.invokeAndWait(CrossFocusRequestTest::initializeGUI);

            Robot robot = new Robot();
            robot.setAutoDelay(100);
            robot.setAutoWaitForIdle(true);

            EventQueue.invokeAndWait(() -> {
                compAt = button.getLocationOnScreen();
                compSize = button.getSize();
            });

            robot.mouseMove(compAt.x + compSize.width / 2,
                compAt.y + compSize.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.waitForIdle();
            if (!clickLatch.await(waitTimeout, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException(
                    "FAIL: Button did not trigger actionPerformed when clicked."
                        + " Test cannot proceed!");
            }

            robot.waitForIdle();

            EventQueue.invokeAndWait(() -> {
                compAt = frame1.getLocationOnScreen();
                compSize = frame1.getSize();
            });

            robot.mouseMove(compAt.x + compSize.width / 2,
                compAt.y + compSize.height - 20);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.waitForIdle();
            if (!focusLatch.await(waitTimeout, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException(
                    "FAIL: TextField did not gained focus. requestFocus "
                        + "called when parent Frame is not having focus."
                        + " Failed checking if focus request is remembered");
            }
            System.out.println("Test passed!");
        } finally {
            EventQueue.invokeAndWait(CrossFocusRequestTest::disposeFrame);
        }
    }

    private static void initializeGUI() {
        frame1 = new Frame("Test Frame1");
        frame1.setLayout(new FlowLayout());
        frame2 = new Frame("Test Frame2");
        frame2.setLayout(new FlowLayout());

        button = new Button("Shift focus to TextField");
        button.addActionListener((event) -> {
            textField.requestFocus();
            clickLatch.countDown();
        });
        frame2.add(button);

        textField = new TextField(15);
        textField.addFocusListener(new FocusListener() {
            public void focusGained(FocusEvent event) {
                focusLatch.countDown();
            }

            public void focusLost(FocusEvent event) {
            }
        });
        frame1.add(textField);
        frame1.pack();
        frame1.setLocation(0, 0);
        frame1.setVisible(true);
        frame2.pack();
        frame2.setLocation(250, 0);
        frame2.setVisible(true);
    }

    public static void disposeFrame() {
        if (frame1 != null) {
            frame1.dispose();
        }
        if (frame2 != null) {
            frame2.dispose();
        }
    }
}
