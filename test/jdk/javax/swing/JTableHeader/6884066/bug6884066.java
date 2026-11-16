/*
 * Copyright (c) 2010, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
   @test
   @key headful
   @bug 6884066
   @summary JTableHeader listens mouse in disabled state and doesn't work when not attached to a table
   @run main bug6884066
*/

import java.awt.event.InputEvent;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableColumn;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class bug6884066 {
    private static JFrame frame;
    private static JTableHeader header;
    private static volatile Point start;
    private static volatile Point end;

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            Robot robot = new Robot();
            robot.setAutoDelay(100);
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    // just to quickly grab a column model
                    JTable table = new JTable(10, 5);
                    header = new JTableHeader(table.getColumnModel());
                    checkColumn(0, "A");
                    frame = new JFrame("standalone header");
                    frame.add(header);
                    frame.pack();
                    frame.setLocationRelativeTo(null);
                    frame.setVisible(true);
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                }
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                Point location = header.getLocationOnScreen();
                Rectangle first = header.getHeaderRect(0);
                Rectangle last = header.getHeaderRect(header.getColumnModel().getColumnCount() - 1);
                int y = location.y + header.getHeight() / 2;
                start = new Point(location.x + first.x + first.width / 2, y);
                end = new Point(location.x + last.x + last.width / 2, y);
            });
            robot.mouseMove(start.x, start.y);
            robot.waitForIdle();
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (int x = start.x + 1; x < end.x; x++) {
                robot.mouseMove(x, start.y);
            }
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    TableColumnModel model = header.getColumnModel();
                    checkColumn(model.getColumnCount() - 1, "A");
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void checkColumn(int index, String str) {
        TableColumnModel model = header.getColumnModel();
        Object value = model.getColumn(index).getHeaderValue();
        if (!str.equals(value)) {
            throw new RuntimeException("Unexpected header's value; " +
                    "index = " + index + " value = " + value);
        }
    }
}
