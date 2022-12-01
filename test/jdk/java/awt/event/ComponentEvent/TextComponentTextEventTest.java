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
import java.awt.TextComponent;
import java.awt.TextField;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/*
 * @test
 * @key headful
 * @bug 8297489 8296632
 * @summary Verify the content changes of a TextComponent via TextListener.
 * @run main TextComponentTextEventTest
 */
public class TextComponentTextEventTest {

    private static Frame frame;
    private static Robot robot = null;
    private volatile static TextComponent[] components;
    private volatile static boolean textChanged = false;
    private volatile static Point textCompAt;
    private volatile static Dimension textCompSize;

    private static void initializeGUI() {
        TextField textField = new TextField(20);
        textField.addTextListener((event) -> {
            textChanged = true;
            System.out.println("TextField got a text event: " + event);
        });

        TextArea textArea = new TextArea(5, 15);
        textArea.addTextListener((event) -> {
            System.out.println("TextArea got a text event: " + event);
            textChanged = true;
        });

        components = new TextComponent[] { textField, textArea };

        frame = new Frame("Test Frame");
        frame.setLayout(new FlowLayout());
        for (TextComponent textComp : components) {
            frame.add(textComp);
        }
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(TextComponentTextEventTest::initializeGUI);
            robot = new Robot();
            robot.setAutoDelay(100);
            robot.setAutoWaitForIdle(true);

            for (TextComponent textComp : components) {
                robot.waitForIdle();
                EventQueue.invokeAndWait(() -> {
                    textCompAt = textComp.getLocationOnScreen();
                    textCompSize = textComp.getSize();
                });

                robot.mouseMove(textCompAt.x + textCompSize.width / 2,
                    textCompAt.y + textCompSize.height / 2);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                typeKey(KeyEvent.VK_T);

                robot.waitForIdle();
                if (!textChanged) {
                    throw new RuntimeException(
                        "FAIL: TextEvent not triggered when text entered in " + textComp);
                }

                typeKey(KeyEvent.VK_E);
                typeKey(KeyEvent.VK_S);
                typeKey(KeyEvent.VK_T);

                textChanged = false;
                typeKey(KeyEvent.VK_ENTER);

                robot.waitForIdle();
                if (textComp instanceof TextField && textChanged) {
                    throw new RuntimeException(
                        "FAIL: TextEvent triggered when Enter pressed on " + textComp);
                } else if (textComp instanceof TextArea && !textChanged) {
                    throw new RuntimeException(
                        "FAIL: TextEvent not triggered when Enter pressed on " + textComp);
                }

                textChanged = false;
                robot.mouseMove(textCompAt.x + 4, textCompAt.y + 10);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                for (int i = 0; i < textCompSize.width / 2; i++) {
                    robot.mouseMove(textCompAt.x + 4 + i, textCompAt.y + 10);
                }
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                robot.waitForIdle();
                if (textChanged) {
                    throw new RuntimeException(
                        "FAIL: TextEvent triggered when selection made in " + textComp);
                }

                textChanged = false;
                typeKey(KeyEvent.VK_F3);

                robot.waitForIdle();
                if (textChanged) {
                    throw new RuntimeException(
                        "FAIL: TextEvent triggered when F3 pressed on " + textComp);
                }
            }
            System.out.println("Test passed!");
        } finally {
            EventQueue.invokeAndWait(TextComponentTextEventTest::disposeFrame);
        }
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }

    private static void typeKey(int key) {
        robot.keyPress(key);
        robot.keyRelease(key);
    }
}
