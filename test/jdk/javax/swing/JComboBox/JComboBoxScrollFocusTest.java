/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicComboPopup;

/*
 * @test
 * @key headful
 * @bug 6672644
 * @summary Tests JComboBox scrollbar behavior when alt-tabbing
 * @run main JComboBoxScrollFocusTest
 */

public class JComboBoxScrollFocusTest {
    private static Robot robot;
    private static JFrame comboboxFrame;
    private static JComboBox<String> combobox;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(JComboBoxScrollFocusTest::createAndShowGUI);
            doTest();
        } finally {
            SwingUtilities.invokeAndWait(comboboxFrame::dispose);
        }
    }

    private static void createAndShowGUI() {
        comboboxFrame = new JFrame("JComboBoxScrollFocusTest Test Frame");
        combobox = new JComboBox<>();
        for (int i = 0; i < 100; i++) {
            combobox.addItem(String.valueOf(i));
        }
        comboboxFrame.add(combobox);
        comboboxFrame.setSize(400, 200);
        comboboxFrame.setLocationRelativeTo(null);
        comboboxFrame.setVisible(true);
    }

    static Rectangle getOnScreenBoundsOnEDT(Component component) throws Exception {
        robot.waitForIdle();
        FutureTask<Rectangle> task = new FutureTask<>(()
                -> new Rectangle(component.getLocationOnScreen(),
                component.getSize()));
        SwingUtilities.invokeLater(task);
        return task.get(500, TimeUnit.MILLISECONDS);
    }

    private static JScrollBar getScrollBar() {
        BasicComboPopup popup = (BasicComboPopup) combobox
                .getAccessibleContext().getAccessibleChild(0);
        JScrollPane scrollPane = (JScrollPane) popup
                .getAccessibleContext().getAccessibleChild(0);
        return scrollPane.getVerticalScrollBar();
    }

    private static int getScrollbarValue() throws Exception {
        FutureTask<Integer> task = new FutureTask<>(() -> {
            JScrollBar scrollBar = getScrollBar();
            return scrollBar.getValue();
        });
        SwingUtilities.invokeAndWait(task);

        return task.get(500, TimeUnit.MILLISECONDS);
    }

    private static void doTest() throws Exception {
        robot.waitForIdle();
        robot.delay(500);

        Rectangle rectangle = getOnScreenBoundsOnEDT(combobox);

        Point ptOpenComboboxPopup = new Point(rectangle.x + rectangle.width - 5,
                rectangle.y + rectangle.height / 2);

        robot.mouseMove(ptOpenComboboxPopup.x, ptOpenComboboxPopup.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        robot.delay(500);

        JScrollBar scrollBar = getScrollBar();

        // Start scrolling
        Rectangle scrollbarBounds = getOnScreenBoundsOnEDT(scrollBar);
        robot.mouseMove(scrollbarBounds.x + scrollbarBounds.width / 2,
                scrollbarBounds.y + scrollbarBounds.height - 5);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);

        robot.delay(1000);

        if (getScrollbarValue() == 0) {
            throw new RuntimeException("The scrollbar is not scrolling");
        }

        // closing popup by moving focus to the main window
        comboboxFrame.requestFocus();
        robot.waitForIdle();
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        robot.waitForIdle();
        robot.delay(500);

        // open popup again
        robot.mouseMove(ptOpenComboboxPopup.x, ptOpenComboboxPopup.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        robot.delay(500);

        if (getScrollbarValue() != 0) {
            throw new RuntimeException("The scroll bar is scrolling");
        }
    }
}
