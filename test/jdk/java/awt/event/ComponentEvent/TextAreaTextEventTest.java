/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextArea;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/*
 * @test
 * @key headful
 * @bug 8296632
 * @summary Verify the content changes of a TextArea via TextListener.
 * @run main TextAreaTextEventTest
 */
public class TextAreaTextEventTest {

    private static Frame frame;
    private volatile static TextArea textArea;
    private volatile static boolean textChanged = false;
    private volatile static Point textAreaAt;
    private volatile static Dimension textAreaSize;
    private static Robot robot = null;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(TextAreaTextEventTest::initializeGUI);

            robot = new Robot();
            robot.setAutoDelay(100);

            robot.waitForIdle();
            EventQueue.invokeAndWait(() -> {
                textAreaAt = textArea.getLocationOnScreen();
                textAreaSize = textArea.getSize();
            });
            robot.mouseMove(textAreaAt.x + textAreaSize.width / 2,
                textAreaAt.y + textAreaSize.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            typeKey(KeyEvent.VK_T);

            robot.waitForIdle();
            if (!textChanged) {
                throw new RuntimeException(
                    "FAIL: TextEvent not triggered when key 'T' typed on TextArea");
            }

            typeKey(KeyEvent.VK_E);
            typeKey(KeyEvent.VK_S);
            typeKey(KeyEvent.VK_T);

            textChanged = false;
            typeKey(KeyEvent.VK_ENTER);

            robot.waitForIdle();
            if (!textChanged) {
                throw new RuntimeException(
                    "FAIL: TextEvent not triggered when Enter pressed on TextArea");
            }

            textChanged = false;
            robot.mouseMove(textAreaAt.x + 4, textAreaAt.y + 10);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (int i = 0; i < textAreaSize.width / 2; i++) {
                robot.mouseMove(textAreaAt.x + 4 + i, textAreaAt.y + 10);
            }
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.waitForIdle();
            if (textChanged) {
                throw new RuntimeException(
                    "FAIL: TextEvent triggered when text is selected on TextArea!");
            }

            textChanged = false;
            typeKey(KeyEvent.VK_F3);

            robot.waitForIdle();
            if (textChanged) {
                throw new RuntimeException(
                    "FAIL: TextEvent triggered when special key F3 is pressed on TextArea!");
            }
            System.out.println("Test passed!");
        } finally {
            EventQueue.invokeAndWait(TextAreaTextEventTest::disposeFrame);
        }
    }

    private static void initializeGUI() {
        frame = new Frame("Test Frame");
        frame.setLayout(new FlowLayout());
        textArea = new TextArea(5, 15);
        textArea.addTextListener((event) -> {
            System.out.println("Got a text event: " + event);
            textChanged = true;
        });
        frame.add(textArea);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }

    private static void typeKey(int key) throws Exception {
        robot.keyPress(key);
        robot.keyRelease(key);
    }
}
