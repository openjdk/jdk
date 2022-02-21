/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @key headful
 * @bug 8236907
 * @summary  Verifies if JTable last row is visible.
 * @run main LastVisibleRow
 */

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class LastVisibleRow {
    static JFrame frame;
    static JTable table;
    static Robot testRobot;
    private static BufferedImage bufferedImageAfter;
    private static BufferedImage bufferedImageBefore;

    public static void main(String[] args) throws Exception {
        try {
            testRobot = new Robot();

            SwingUtilities.invokeAndWait(new Runnable() {

                public void run() {
                    try {
                        createAndShowGUI();
                    } catch (Exception e) {
                        e.printStackTrace();
						throw new RuntimeException("Exception while creating UI");
                    }
                }
            });
            testRobot.waitForIdle();
            CaptureBeforeClick();
            testRobot.waitForIdle();
            MouseActions();
            testRobot.waitForIdle();
            clearSelect();
            testRobot.waitForIdle();
            CaptureAfterClick();

            if (!compare(bufferedImageBefore, bufferedImageAfter)) {
                throw new RuntimeException("Test Case Failed!!");
            }
        } finally {
            if (frame != null) SwingUtilities.invokeAndWait(() -> frame.dispose());
        }
    }

    /***
     *
     * Get clickable screen point for particular row and column of a table
     * @param row   Row Number
     * @param column    Column Number
     * @return Point
     */
    private static Point getCellClickPoint(final int row, final int column) {
        Point result;

        Rectangle rect = table.getCellRect(row, column, false);
        Point point = new Point(rect.x + rect.width / 2,
                rect.y + rect.height / 2);
        SwingUtilities.convertPointToScreen(point, table);
        result = point;

        return result;
    }

    private static void createAndShowGUI() {
        final PrintRequestAttributeSet printReqAttr = new HashPrintRequestAttributeSet();
        printReqAttr.add(javax.print.attribute.standard.OrientationRequested.LANDSCAPE);
        frame = new JFrame();
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        Container contentPane = frame.getContentPane();
        JPanel centerPane = new JPanel(new BorderLayout());
        centerPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel tablePaneContainer = new JPanel(new BorderLayout());
        JPanel tablePane = new JPanel(new BorderLayout());
        table = new JTable(new Object[][]{{"row_1_col_1", "row_1_col_2", "row_1_col_3"}, {"row_2_col_1", "row_2_col_2", "row_2_col_3"}, {"row_3_col_1", "row_3_col_2", "row_3_col_3"}, {"row_4_col_1", "row_4_col_2", "row_4_col_3"}}, new String[]{"Col1", "Col2", "Col3"});
        table.setPreferredSize(new Dimension(0, (table.getRowHeight() * 3)));

        tablePane.add(table.getTableHeader(), BorderLayout.NORTH);
        tablePane.add(table, BorderLayout.CENTER);
        tablePaneContainer.add(tablePane, BorderLayout.CENTER);
        centerPane.add(tablePaneContainer, BorderLayout.NORTH);
        contentPane.add(centerPane, BorderLayout.CENTER);
        frame.setSize(400, 120);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);

    }

    /***
     *
     * Capture the screen before click and sets the Before click Image
     * @throws Exception
     */
    private static void CaptureBeforeClick() throws Exception {

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                bufferedImageBefore = captureImage();
                testRobot.delay(1000);
            }
        });
    }

    /***
     *
     * Mouse Actions for last row click
     * @throws Exception
     */

    private static void MouseActions() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    Point clickPoint = getCellClickPoint(2, 0);

                    testRobot.mouseMove(clickPoint.x, clickPoint.y);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                testRobot.delay(50);
                testRobot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                testRobot.delay(50);
                testRobot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                testRobot.delay(50);
            }
        });
    }

    /***
     *
     * Clears the selected table row
     * @throws Exception
     */

    private static void clearSelect() throws Exception {

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                table.getSelectionModel().clearSelection();
                table.setFocusable(false);
            }
        });
    }

    /***
     *
     * Capture the screen after click and sets the After click Image
     * @throws Exception
     */

    private static void CaptureAfterClick() throws Exception {

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                bufferedImageAfter = captureImage();
            }
        });
    }

    /****
     * Capture Method - To Screen Capture the Last Row for comparison
     * @return BufferedImage
     */

    private static BufferedImage captureImage() {
        Rectangle cellRect = table.getCellRect(2, 0, true);
        Point point = new Point(cellRect.x, cellRect.y);
        SwingUtilities.convertPointToScreen(point, table);

        Rectangle captureRect = new Rectangle(point.x, point.y, table.getColumnCount() * cellRect.width, cellRect.height);
        return testRobot.createScreenCapture(captureRect);
    }

    /***
     * Compare method - to compare two images.
     * @param bufferedImage1    Buffered Image Before click
     * @param bufferedImage2    Buffered Image After click
     * @return Boolean
     */

    static Boolean compare(BufferedImage bufferedImage1, BufferedImage bufferedImage2) {
        if (bufferedImage1.getWidth() == bufferedImage2.getWidth()
                && bufferedImage1.getHeight() == bufferedImage2.getHeight()) {
            for (int x = 0; x < bufferedImage1.getWidth(); x++) {
                for (int y = 0; y < bufferedImage1.getHeight(); y++) {
                    if (bufferedImage1.getRGB(x, y) != bufferedImage2.getRGB(x, y)) {
                        return false;
                    }
                }
            }
        } else {
            return false;
        }
        return true;
    }
}
