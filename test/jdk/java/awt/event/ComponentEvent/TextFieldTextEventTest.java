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
import java.awt.TextField;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/*
 * @test
 * @key headful
 * @bug 8297489
 * @summary Verify the content changes of a TextField via TextListener.
 * @run main TextFieldTextEventTest
 */
public class TextFieldTextEventTest {

    private static Frame frame;
    private static Robot robot = null;
    private volatile static TextField textField;
    private volatile static boolean textChanged = false;
    private volatile static Point textFieldAt;
    private volatile static Dimension textFieldSize;

    private static void initializeGUI() {
        frame = new Frame("Test Frame");
        frame.setLayout(new FlowLayout());
        textField = new TextField(20);
        textField.addTextListener((event) -> {
            textChanged = true;
            System.out.println("Got a text event: " + event);
        });
        frame.add(textField);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(TextFieldTextEventTest::initializeGUI);
            robot = new Robot();
            robot.setAutoDelay(100);
            robot.setAutoWaitForIdle(true);

            robot.waitForIdle();
            EventQueue.invokeAndWait(() -> {
                textFieldAt = textField.getLocationOnScreen();
                textFieldSize = textField.getSize();
            });

            robot.mouseMove(textFieldAt.x + textFieldSize.width / 2,
                textFieldAt.y + textFieldSize.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            typeKey(KeyEvent.VK_ENTER);

            robot.waitForIdle();
            if (textChanged) {
                throw new RuntimeException(
                    "FAIL: TextEvent triggered when Enter pressed on TextField!");
            }

            typeKey(KeyEvent.VK_T);

            robot.waitForIdle();
            if (!textChanged) {
                throw new RuntimeException(
                    "FAIL: TextEvent not triggered when text entered in TextField!");
            }

            typeKey(KeyEvent.VK_E);
            typeKey(KeyEvent.VK_S);
            typeKey(KeyEvent.VK_T);

            textChanged = false;
            robot.mouseMove(textFieldAt.x + 4, textFieldAt.y + 10);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (int i = 0; i < textFieldSize.width / 2; i++) {
                robot.mouseMove(textFieldAt.x + 4 + i, textFieldAt.y + 10);
            }
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.waitForIdle();
            if (textChanged) {
                throw new RuntimeException(
                    "FAIL: TextEvent triggered when selection made in TextField!");
            }

            textChanged = false;
            typeKey(KeyEvent.VK_F3);

            robot.waitForIdle();
            if (textChanged) {
                throw new RuntimeException(
                    "FAIL: TextEvent triggered when F3 pressed on TextField!");
            }
            System.out.println("Test passed!");
        } finally {
            EventQueue.invokeAndWait(TextFieldTextEventTest::disposeFrame);
        }
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }

    private static void typeKey(int key){
        robot.keyPress(key);
        robot.keyRelease(key);
    }
}
