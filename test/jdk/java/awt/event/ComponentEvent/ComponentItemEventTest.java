/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

/*
 * @test
 * @key headful
 * @bug 8299296
 * @summary Verify that Component selection via mouse generates ItemEvent.
 * @run main ComponentItemEventTest
 */
public class ComponentItemEventTest {

    private static Frame frame;
    private volatile static Choice choice;
    private volatile static Checkbox cb;
    private static Robot robot;
    private volatile static boolean cbStateChanged = false;
    private volatile static boolean choiceStateChanged = false;
    private volatile static Point compAt;
    private volatile static Dimension compSize;

    private static void initializeGUI() {
        frame = new Frame("Test Frame");
        frame.setLayout(new FlowLayout());
        choice = new Choice();
        for (int i = 0; i < 8; i++) {
            choice.add("Choice "+i);
        }
        choice.select(3);
        choice.addItemListener((event) -> {
            System.out.println("Choice got an ItemEvent: " + event);
            choiceStateChanged = true;
        });

        cb = new Checkbox("CB");
        cb.addItemListener((event) -> {
            System.out.println("Checkbox got an ItemEvent: " + event);
            cbStateChanged = true;
        });
        frame.add(choice);
        frame.add(cb);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(ComponentItemEventTest::initializeGUI);
            robot = new Robot();
            robot.setAutoDelay(1000);
            robot.setAutoWaitForIdle(true);

            robot.waitForIdle();
            EventQueue.invokeAndWait(() -> {
                compAt = choice.getLocationOnScreen();
                compSize = choice.getSize();
            });
            robot.mouseMove(compAt.x + choice.getSize().width - 10,
                compAt.y + compSize.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseMove(compAt.x + compSize.width / 2,
                compAt.y + compSize.height + 15);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.waitForIdle();
            if (!choiceStateChanged) {
                throw new RuntimeException(
                    "FAIL: Choice did not trigger ItemEvent when item selected!");
            }

            EventQueue.invokeAndWait(() -> {
                compAt = cb.getLocationOnScreen();
                compSize = cb.getSize();
            });
            robot.mouseMove(compAt.x + compSize.width / 2,
                compAt.y + compSize.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.waitForIdle();
            if (!cbStateChanged) {
                throw new RuntimeException(
                    "FAIL: Checkbox did not trigger ItemEvent when item selected!");
            }
            System.out.println("Test passed!");
        } finally {
            EventQueue.invokeAndWait(ComponentItemEventTest::disposeFrame);
        }
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }
}
