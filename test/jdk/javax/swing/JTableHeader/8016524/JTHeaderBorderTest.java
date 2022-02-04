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
 * @bug 8016524
 * @key headful
 * @requires (os.family=="mac")
 * @summary Tests whether the bottom line of JTableHeader border is visible for MacOS default LAF
 * @run main JTHeaderBorderTest
 */

import java.awt.Point;
import java.awt.Robot;
import java.awt.Color;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.JTableHeader;
import javax.swing.UnsupportedLookAndFeelException;
import java.util.concurrent.atomic.AtomicReference;

public class JTHeaderBorderTest {

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
    public static final int TABLE_COLS = 3;
    public static final int TABLE_ROWS = 2;

    public static void main(String[] args) throws Exception {

        try {
            //to keep track of header dimensions
            final int[] header_dim = new int[2];
            Robot robot = new Robot();
            AtomicReference<Color> tableColor = new AtomicReference<>();
            AtomicReference<Color> tableHeaderColor = new AtomicReference<>();

            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                    | UnsupportedLookAndFeelException e) {
                throw new RuntimeException("Unsupported Look&Feel Class");
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
                // retrieve height and width of the header
                header = table.getTableHeader();
                header_dim[0] = header.getHeight();
                header_dim[1] = header.getWidth();
                tableColor.set(table.getBackground());
                tableHeaderColor.set(table.getTableHeader().getBackground());
            });

            robot.delay(200);
            robot.waitForIdle();

            // to check mouse pointer position on screen
            robot.mouseMove(point.x + X_OFFSET, point.y + header_dim[0] - Y_OFFSET);
            robot.delay(500);
            robot.waitForIdle();

            // get pixel color at lower left of JTableHeader
            Color lowerLeft = robot.getPixelColor(point.x + X_OFFSET, point.y + header_dim[0] - Y_OFFSET);
            robot.delay(500);

            // to check mouse pointer position on screen
            robot.mouseMove(point.x + header_dim[1] - X_OFFSET, point.y + header_dim[0] - Y_OFFSET);
            robot.delay(500);
            robot.waitForIdle();
            // get pixel color at lower right of JTableHeader
            Color lowerRight = robot.getPixelColor(point.x + header_dim[1] - X_OFFSET, point.y + header_dim[0] - Y_OFFSET);
            robot.delay(500);


            System.out.println("RGB Lower Left: " + lowerLeft.toString());
            System.out.println("RGB Lower Right: " + lowerRight.toString());
            System.out.println("Table-Header Background Color: " + tableHeaderColor.get().toString());
            System.out.println("Table Background Color: " + tableColor.get().toString());


            // if pixel color is either table-header or table background color then throw an Exception
            if (lowerLeft.getRGB() == tableColor.get().getRGB() || lowerLeft.getRGB() == tableHeaderColor.get().getRGB()
                    || lowerRight.getRGB() == tableColor.get().getRGB() || lowerRight.getRGB() == tableHeaderColor.get().getRGB()) {
                throw new RuntimeException("JTableHeader Bottom Border not visible");
            }
        }
        finally {
            if (frame != null) {
                SwingUtilities.invokeAndWait(()-> frame.dispose());
            }
        }
    }
}
