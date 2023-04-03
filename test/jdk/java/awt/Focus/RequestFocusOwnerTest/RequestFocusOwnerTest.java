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

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;

/*
 * @test
 * @key headful
 * @bug 8305427
 * @summary Verify that focus changes when requestFocus is used to traverse randomly.
 * @run main RequestFocusOwnerTest
 */
public class RequestFocusOwnerTest {

    private static Frame frame;
    private volatile static Button button;
    private volatile static Choice choice;
    private volatile static TextField textField;
    private volatile static Checkbox checkbox;
    private volatile static List list;
    private volatile static Point buttonAt;
    private volatile static Dimension buttonSize;
    private static boolean focusGained = false;
    private static boolean requestStatus;
    private static Robot robot = null;

    private volatile static FocusListener listener = new FocusListener() {

        @Override
        public void focusLost(FocusEvent e) {
            System.out.println(e.getSource() + ": lost focus.");
        }

        @Override
        public void focusGained(FocusEvent e) {
            System.out.println(e.getSource() + ": gained focus.");
            focusGained = true;
        }
    };

    private static void initializeGUI() {
        frame = new Frame("Test Frame");
        frame.setLayout(new FlowLayout());
        button = new Button("Button");
        button.addFocusListener(listener);
        frame.add(button);
        textField = new TextField(15);
        textField.addFocusListener(listener);
        frame.add(textField);
        choice = new Choice();
        choice.addItem("One");
        choice.addItem("Two");
        choice.addFocusListener(listener);
        choice.setEnabled(false);
        frame.add(choice);
        list = new List();
        list.add("One");
        list.add("Two");
        list.add("Three");
        list.add("Four");
        list.add("Five");
        list.addFocusListener(listener);
        list.setFocusable(false);
        checkbox = new Checkbox("Checkbox");
        checkbox.addFocusListener(listener);
        frame.add(checkbox);
        frame.add(list);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {

            EventQueue.invokeAndWait(RequestFocusOwnerTest::initializeGUI);

            robot = new Robot();
            robot.setAutoDelay(500);
            robot.setAutoWaitForIdle(true);

            EventQueue.invokeAndWait(() -> {
                buttonAt = button.getLocationOnScreen();
                buttonSize = button.getSize();
            });

            if (!button.hasFocus()) {
                focusGained = false;

                robot.mouseMove(buttonAt.x + buttonSize.width / 2,
                    buttonAt.y + buttonSize.height / 2);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                robot.waitForIdle();

                if (!focusGained) {
                    throw new RuntimeException(
                        "FAIL: Button did not gain focus when clicked. Test cannot proceed!");
                }
            }

            focusGained = false;
            EventQueue.invokeAndWait(() -> {
                requestStatus = checkbox.requestFocusInWindow();
            });

            robot.waitForIdle();

            if (!requestStatus) {
                throw new RuntimeException(
                    "FAIL: Checkbox.requestFocusInWindow returned false");
            }

            if (!focusGained) {
                throw new RuntimeException(
                    "FAIL: Checkbox.requestFocusInWindow did not transfer the focus to Checkbox");
            }

            if (!checkbox.hasFocus()) {
                throw new RuntimeException(
                    "FAIL: CheckBox.hasFocus for Checkbox returns false after calling Checkbox.requestFocusInWindow");
            }
            focusGained = false;
            EventQueue.invokeAndWait(() -> {
                requestStatus = choice.requestFocusInWindow();
            });

            robot.waitForIdle();

            if (!requestStatus) {
                throw new RuntimeException(
                    "FAIL: Choice.requestFocusInWindow for a disabled Choice returned false");
            }

            if (!focusGained) {
                throw new RuntimeException(
                    "FAIL: Choice.requestFocusInWindow did not transfer the focus to disabled Choice");
            }

            if (!choice.hasFocus()) {
                throw new RuntimeException(
                    "FAIL: Choice.hasFocus for disabled Choice returns false after calling Choice.requestFocusInWindow");
            }
            focusGained = false;
            EventQueue.invokeAndWait(() -> {
                textField.setVisible(false);
                requestStatus = textField.requestFocusInWindow();
            });

            if (requestStatus) {
                throw new RuntimeException(
                    "FAIL: TextField.requestFocusInWindow returned true for hidden TextField");
            }

            if (focusGained) {
                throw new RuntimeException(
                    "FAIL: Wrong component gained focus: textField.requestFocusInWindow()");
            }
            if (textField.hasFocus()) {
                throw new RuntimeException(
                    "FAIL: TextField.hasFocus for hidden TextField returns true after calling TextField.requestFocusInWindow");
            }

            focusGained = false;
            EventQueue.invokeAndWait(() -> {
                requestStatus = list.requestFocusInWindow();
            });

            robot.waitForIdle();

            if (requestStatus) {
                throw new RuntimeException(
                    "FAIL: List.requestFocusInWindow returned true for non-focusable List");
            }

            if (focusGained) {
                throw new RuntimeException(
                    "FAIL: Wrong component gained focus: list.requestFocusInWindow()");
            }
            if (list.hasFocus()) {
                throw new RuntimeException(
                    "FAIL: List.hasFocus for non-focusable List returns true after calling List.requestFocusInWindow");
            }

            System.out.println("Test passed!");

        } finally {
            EventQueue.invokeAndWait(RequestFocusOwnerTest::disposeFrame);
        }
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }
}
