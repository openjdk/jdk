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
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Component;
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
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    private volatile static boolean focusGained = false;
    private volatile static boolean requestStatus;
    private static final int waitDelay = 500;
    private static final CountDownLatch buttonFocusLatch =
        new CountDownLatch(1);
    private static final CountDownLatch checkboxFocusLatch =
        new CountDownLatch(1);
    private static final CountDownLatch choiceFocusLatch =
        new CountDownLatch(1);

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

    private volatile static FocusListener choiceListener = new FocusListener() {
        @Override
        public void focusLost(FocusEvent e) {
            System.out.println(e.getSource() + ": lost focus.");
        }

        @Override
        public void focusGained(FocusEvent e) {
            System.out.println(e.getSource() + ": gained focus.");
            choiceFocusLatch.countDown();
        }
    };

    private volatile static FocusListener butonListener = new FocusListener() {
        @Override
        public void focusLost(FocusEvent e) {
            System.out.println(e.getSource() + ": lost focus.");
        }

        @Override
        public void focusGained(FocusEvent e) {
            System.out.println(e.getSource() + ": gained focus.");
            buttonFocusLatch.countDown();
        }
    };

    private volatile static FocusListener checkboxListener =
        new FocusListener() {
            @Override
            public void focusLost(FocusEvent e) {
                System.out.println(e.getSource() + ": lost focus.");
            }

            @Override
            public void focusGained(FocusEvent e) {
                System.out.println(e.getSource() + ": gained focus.");
                checkboxFocusLatch.countDown();
            }
        };

    private static void initializeGUI() {
        frame = new Frame("Test Frame");
        frame.setLayout(new FlowLayout());
        button = new Button("Button");
        button.addFocusListener(butonListener);
        frame.add(button);
        textField = new TextField(15);
        textField.addFocusListener(listener);
        frame.add(textField);
        choice = new Choice();
        choice.addItem("One");
        choice.addItem("Two");
        choice.addFocusListener(choiceListener);
        choice.setEnabled(false);
        frame.add(choice);
        checkbox = new Checkbox("Checkbox");
        checkbox.addFocusListener(checkboxListener);
        frame.add(checkbox);
        list = new List();
        list.add("One");
        list.add("Two");
        list.add("Three");
        list.add("Four");
        list.add("Five");
        list.addFocusListener(listener);
        list.setFocusable(false);
        frame.add(list);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args)
        throws InvocationTargetException, InterruptedException, AWTException {
        try {

            EventQueue.invokeAndWait(RequestFocusOwnerTest::initializeGUI);

            Robot robot = new Robot();
            robot.setAutoDelay(500);
            robot.setAutoWaitForIdle(true);
            EventQueue.invokeAndWait(() -> {
                buttonAt = button.getLocationOnScreen();
                buttonSize = button.getSize();
            });

            if (!button.isFocusOwner()) {

                robot.mouseMove(buttonAt.x + buttonSize.width / 2,
                    buttonAt.y + buttonSize.height / 2);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                if (!buttonFocusLatch.await(waitDelay, TimeUnit.MILLISECONDS)) {
                    throw new RuntimeException(
                        "FAIL: Button did not gain focus when clicked!");
                }
            }

            EventQueue.invokeAndWait(() -> {
                checkbox.requestFocusInWindow();
            });

            checkComponentGainFocus(checkboxFocusLatch, checkbox);

            EventQueue.invokeAndWait(() -> {
                choice.requestFocusInWindow();
            });

            checkComponentGainFocus(choiceFocusLatch, choice);

            focusGained = false;
            EventQueue.invokeAndWait(() -> {
                textField.setVisible(false);
                requestStatus = textField.requestFocusInWindow();
            });

            if (requestStatus) {
                throw new RuntimeException(
                    "FAIL: TextField.requestFocusInWindow returned true for"
                        + " hidden TextField");
            }
            checkFocusOwner(textField, focusGained);

            focusGained = false;
            EventQueue.invokeAndWait(() -> {
                requestStatus = list.requestFocusInWindow();
            });

            if (requestStatus) {
                throw new RuntimeException(
                    "FAIL: List.requestFocusInWindow returned true for"
                        + " non-focusable List");
            }
            checkFocusOwner(list, focusGained);

            System.out.println("Test passed!");

        } finally {
            EventQueue.invokeAndWait(RequestFocusOwnerTest::disposeFrame);
        }
    }

    private static void checkComponentGainFocus(CountDownLatch latch,
        Component comp) throws InterruptedException {
        if (!latch.await(waitDelay, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException(
                "FAIL: requestFocusInWindow did not transfer the focus to"
                    + comp);
        }
        if (!comp.isFocusOwner()) {
            throw new RuntimeException("FAIL: isFocusOwner returns false for "
                + comp + " after calling requestFocusInWindow");
        }
    }

    private static void checkFocusOwner(Component comp, boolean focusGained) {
        if (focusGained) {
            throw new RuntimeException(
                "FAIL: Wrong component gained focus while calling requestFocusInWindow for "
                    + comp);
        }
        if (comp.isFocusOwner()) {
            throw new RuntimeException(
                "FAIL: isFocusOwner for non-focusable component returns true"
                    + " after calling requestFocusInWindow for " + comp);
        }
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }
}
