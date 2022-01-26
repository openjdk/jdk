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
@test
@bug 8016524
@key headful
@summary Tests whether the bottom line of JTableHeader border is visible for MacOS default LAF
@run main Bug8016524
*/

import java.awt.Point;
import java.awt.Robot;
import java.awt.Color;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.SwingUtilities;
import javax.swing.table.JTableHeader;

public class Bug8016524 {

    private static JFrame frame;
    private static JTable table;
    private static JScrollPane scrollableTable;
    private static JTableHeader header;
    private static Point point;

    // added so as to get the correct pixel value of the bottom border
    public static final int X_OFFSET = 10;
    public static final int Y_OFFSET = 1;

    public static final int FRAME_HT = 300;
    public static final int FRAME_WT = 300;
    public static final int TABLE_COLS = 5;
    public static final int TABLE_ROWS = 2;

    private static final int WHITE_RGB = Color.WHITE.getRGB();

    public static void main(String[] args) throws Exception {

        //to keep track of header dimensions
        final int header_dim[] = new int[2];
        Robot robot = new Robot();
        robot.setAutoDelay(20);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
             throw new RuntimeException("Unsupported LookAndFeel Class: ");
        }

        SwingUtilities.invokeAndWait(() -> {
            table = new JTable(TABLE_ROWS, TABLE_COLS);
            scrollableTable = new JScrollPane(table);
            frame = new JFrame();
            frame.getContentPane().add(scrollableTable);
            frame.setSize(FRAME_WT, FRAME_HT);
            frame.setLocationRelativeTo(null);
            frame.pack();
            frame.setVisible(true);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // retrieve JTableHeader coordinate position on screen (x,y)
            point = table.getTableHeader().getLocationOnScreen();
            // retrieve height and width of the table
            header = table.getTableHeader();
            header_dim[0] = header.getHeight();
            header_dim[1] = header.getWidth();
        });

        robot.waitForIdle();

        // to check mouse pointer position on screen
        robot.mouseMove(point.x + X_OFFSET, point.y + header_dim[0] - Y_OFFSET);
        robot.waitForIdle();

        // get pixel color at lower left and lower right on the JTableHeader border
        Color lowerLeft = robot.getPixelColor(point.x + X_OFFSET, point.y + header_dim[0] - Y_OFFSET);
        Color lowerRight = robot.getPixelColor(point.x + header_dim[1] - X_OFFSET, point.y + header_dim[0] - Y_OFFSET);

        // if pixel color is white then border not visible, throw Exception
        if(lowerLeft.getRGB() == WHITE_RGB || lowerRight.getRGB() == WHITE_RGB)
        {
            throw new RuntimeException("JTableHeader Bottom Border not visible");
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        frame.dispose();
    }
}
