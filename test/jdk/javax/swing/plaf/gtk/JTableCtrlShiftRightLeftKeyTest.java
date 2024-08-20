/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/*
 * @test
 * @bug 8338041
 * @key headful
 * @summary Verify that Ctrl Shift RIGHT/LEFT key extends columns till
 * Last/First Columns in JTable
 * @requires (os.family == "linux")
 * @run main JTableCtrlShiftRightLeftKeyTest
 */

public class JTableCtrlShiftRightLeftKeyTest {
    private static JFrame frame;
    private static JTable table;
    private static volatile Point tableLoc;
    private static volatile Rectangle cellRect;
    private static volatile int[] selectedColumnAfterKeyPress;
    private static Robot robot;
    private static final int SELECTED_COLUMN = 2;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        robot = new Robot();
        robot.setAutoDelay(50);
        try {
            SwingUtilities.invokeAndWait(JTableCtrlShiftRightLeftKeyTest::createAndShowUI);
            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                tableLoc = table.getLocationOnScreen();
                cellRect = table.getCellRect(0, SELECTED_COLUMN, true);
            });

            robot.mouseMove(tableLoc.x + cellRect.x + cellRect.width / 2,
                    tableLoc.y + cellRect.y + cellRect.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(100);

            testCtrlShift(KeyEvent.VK_RIGHT, SELECTED_COLUMN,
                    table.getColumnCount() - 1, "RIGHT");

            robot.waitForIdle();
            robot.delay(100);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(100);

            testCtrlShift(KeyEvent.VK_LEFT, 0,
                    SELECTED_COLUMN, "LEFT");
            robot.waitForIdle();
            robot.delay(100);
            System.out.println("Test Passed!");

        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void testCtrlShift(int keySelected, int startCellCheck,
                                      int endCellCheck, String key) throws Exception {
        robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(keySelected);
        robot.keyRelease(keySelected);
        robot.keyRelease(KeyEvent.VK_SHIFT);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.waitForIdle();
        robot.delay(100);

        SwingUtilities.invokeAndWait(() -> {
            selectedColumnAfterKeyPress = table.getSelectedColumns();
        });

        if (selectedColumnAfterKeyPress[0] != startCellCheck ||
                selectedColumnAfterKeyPress[selectedColumnAfterKeyPress.length - 1] !=
                        endCellCheck) {
            System.out.println("Selected Columns: ");
            for (int columnsSelected : selectedColumnAfterKeyPress) {
                System.out.println(columnsSelected);
            }
            String failureMsg = "Test Failure. Failed to select cells for Ctrl" +
                    " Shift " + key + " selection";
            throw new RuntimeException(failureMsg);
        }
    }

    private static void createAndShowUI() {
        frame = new JFrame("Test Ctrl Shift RIGHT/LEFT Key Press");
        table = new JTable(2, 5);
        table.setColumnSelectionAllowed(true);
        frame.getContentPane().add(table);

        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
