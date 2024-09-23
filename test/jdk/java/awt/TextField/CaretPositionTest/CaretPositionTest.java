/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @bug 4038580
 * @key headful
 * @requires os.family != "windows"
 * @summary Caret position not accurate in presence of selected areas
 * @run main CaretPositionTest
 */

public class CaretPositionTest extends Frame {
    private TextField text_field;
    private Button caretpos_button;
    private Point onScreen;
    private Dimension size;
    String text = "12 45 789";
    private static volatile int position = -1;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        CaretPositionTest test = new CaretPositionTest();
        EventQueue.invokeAndWait(test::setupGUI);
        try {
            test.test();
            if (position != 9) {
                throw new RuntimeException("Caret position should be at the end of the string");
            }
        } finally {
            EventQueue.invokeAndWait(test::dispose);
        }
    }

    public void setupGUI() {
        setLocationRelativeTo(null);
        setTitle("CaretPositionTest");
        setLayout(new FlowLayout());
        text_field = new TextField(text, 9);
        caretpos_button=new Button("CaretPosition");
        add(text_field);
        add(caretpos_button);
        pack();
        setVisible(true);
        toFront();
    }

    public void test() throws AWTException, InterruptedException,
            InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
                    onScreen = text_field.getLocationOnScreen();
                    size = text_field.getSize();
                });
        Robot robot = new Robot();
        robot.setAutoDelay(50);
        robot.delay(1000);
        int y = onScreen.y + (size.height / 2);
        robot.mouseMove(onScreen.x + (size.width / 2), y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseMove(onScreen.x + 3, y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int x = onScreen.x + 5; x < onScreen.x + size.width; x += 2) {
            robot.mouseMove(x, y);
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        EventQueue.invokeAndWait(() -> {
            position = text_field.getCaretPosition();
        });
    }
}
