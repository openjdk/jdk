/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4222153
 * @key headful
 * @summary Verify that when tab is pressed, the focus shift to next row first cell,
 *          if the current selected cell is the last cell in that row.
 * @run main bug4222153
 */

public class bug4222153 {
    private static JFrame frame;
    private static JTable table;
    private static volatile Point tableLoc;
    private static volatile Rectangle cellRect;
    private static volatile int selectedRowBeforeTabPress;
    private static volatile int selectedColumnBeforeTabPress;
    private static volatile int selectedRowAfterTabPress;
    private static volatile int selectedColumnAfterTabPress;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        Robot robot = new Robot();
        robot.setAutoDelay(50);
        try {
            SwingUtilities.invokeAndWait(bug4222153::createAndShowUI);
            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                tableLoc = table.getLocationOnScreen();
                cellRect = table.getCellRect(0, 0, true);
            });

            robot.mouseMove(tableLoc.x + cellRect.x + cellRect.width / 2,
                    tableLoc.y + cellRect.y + cellRect.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(20);

            SwingUtilities.invokeAndWait(() -> {
                selectedRowBeforeTabPress = table.getSelectedRow();
                selectedColumnBeforeTabPress = table.getSelectedColumn();
            });

            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.waitForIdle();
            robot.delay(20);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.waitForIdle();
            robot.delay(20);

            SwingUtilities.invokeAndWait(() -> {
                selectedRowAfterTabPress = table.getSelectedRow();
                selectedColumnAfterTabPress = table.getSelectedColumn();
            });

            if (selectedRowAfterTabPress != (selectedRowBeforeTabPress + 1)
               && selectedColumnAfterTabPress != selectedColumnBeforeTabPress) {
                throw new RuntimeException("JTable's cell focus didn't shift to next" +
                        " row first cell on TAB press");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new JFrame("Test JTable Tab Press");
        table = new JTable(2, 2);
        frame.getContentPane().add(table);
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
