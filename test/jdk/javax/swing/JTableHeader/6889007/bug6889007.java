/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
   @bug 6889007
   @summary No resize cursor during hovering mouse over JTable
*/

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.plaf.basic.BasicTableHeaderUI;
import javax.swing.table.JTableHeader;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

public class bug6889007 {

    static JFrame frame;
    static Robot robot;
    static volatile Point point;
    static volatile int width;
    static volatile int height;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();

            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame();
                frame.setUndecorated(true);

                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                JTableHeader th = new JTableHeader();
                th.setColumnModel(new JTable(20, 5).getColumnModel());

                th.setUI(new MyTableHeaderUI());

                frame.add(th);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setAlwaysOnTop(true);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                point = frame.getLocationOnScreen();
                width = frame.getWidth();
                height = frame.getHeight();
            });
            int shift = 10;
            int x = point.x;
            int y = point.y + height/2;
            for(int i = -shift; i < width + 2*shift; i++) {
                robot.mouseMove(x++, y);
                robot.delay(100);
            }
            robot.waitForIdle();
            // 9 is a magic test number
            if (MyTableHeaderUI.getTestValue() != 9) {
                throw new RuntimeException("Unexpected test number "
                        + MyTableHeaderUI.getTestValue());
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
        System.out.println("ok");
    }

    static class MyTableHeaderUI extends BasicTableHeaderUI {
        private static int testValue;

        protected void rolloverColumnUpdated(int oldColumn, int newColumn) {
            increaseTestValue(newColumn);
            Cursor cursor = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            if (oldColumn != -1 && newColumn != -1 &&
                    header.getCursor() != cursor) {
                System.out.println("oldColumn " + oldColumn + " newColumn " + newColumn +
                        "header.getCursor " + header.getCursor() + " cursor " + cursor);
                try {
                    Dimension screenSize =
                               Toolkit.getDefaultToolkit().getScreenSize();
                    Rectangle screen = new Rectangle(0, 0,
                                               (int) screenSize.getWidth(),
                                               (int) screenSize.getHeight());
                    BufferedImage img = robot.createScreenCapture(screen);
                    ImageIO.write(img, "png", new java.io.File("image.png"));
                } catch (Exception e) {}
                throw new RuntimeException("Wrong type of cursor!");
            }
        }

        private static synchronized void increaseTestValue(int increment) {
            testValue += increment;
        }

        public static synchronized int getTestValue() {
            return testValue;
        }
    }
}
