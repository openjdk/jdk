/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 6191390 8158380
 * @summary Verify that ActionEvent is received with correct modifiers set.
 * @run main ActionEventTest
 */

import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class ActionEventTest {

    static List list;
    static Frame frame;
    static Robot robot;

    static final CountDownLatch countDownLatch = new CountDownLatch(1);
    static volatile boolean failed;

    static void initAndShowGui() {
        list = new List(1, false);
        list.add("0");

        list.addActionListener(ae -> {
            int md = ae.getModifiers();
            int expectedMask = ActionEvent.ALT_MASK | ActionEvent.CTRL_MASK
                    | ActionEvent.SHIFT_MASK;

            if ((md & expectedMask) != expectedMask) {
                failed = true;
            }
            countDownLatch.countDown();
        });

        frame = new Frame("ActionEventTest");
        frame.add(list);
        frame.setSize(400, 400);
        frame.setLayout(new FlowLayout());
        frame.setLocationRelativeTo(null);
        frame.pack();
        frame.setVisible(true);
        list.select(0);
    }

    static void performTest() {
        robot.waitForIdle();
        robot.delay(500);

        robot.keyPress(KeyEvent.VK_ALT);
        robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(KeyEvent.VK_CONTROL);
        // Press Enter on list item, to generate action event.
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_SHIFT);
        robot.keyRelease(KeyEvent.VK_ALT);
    }

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(100);
        robot.setAutoWaitForIdle(true);

        try {
            EventQueue.invokeAndWait(ActionEventTest::initAndShowGui);
            performTest();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }

        if (!countDownLatch.await(10, TimeUnit.SECONDS)) {
            throw new RuntimeException("Action Listener is not triggered");
        }

        if (failed) {
            throw new RuntimeException("Action Event modifiers are not"
                    + " set correctly.");
        }
    }
}
