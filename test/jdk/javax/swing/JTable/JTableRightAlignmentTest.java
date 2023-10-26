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

import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.imageio.ImageIO;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
 * @test
 * @bug 5108458
 * @summary Test to check Right alignment of JTable data w.r.t Table Header.
 * @run main JTableRightAlignmentTest
 */

public class JTableRightAlignmentTest {
    static JFrame frame;
    static CustomTable table;
    static boolean passed = true;
    static String failureString;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("Test JTable");

                JPanel panel = new JPanel(new GridBagLayout());
                frame.setContentPane(panel);
                table = new CustomTable();
                panel.add(new JScrollPane(table.table),
                        new GridBagConstraints(0, 0, -1, -1, 1.0, 1.0,
                                GridBagConstraints.PAGE_START, GridBagConstraints.BOTH,
                                new Insets(2, 2, 2, 2), 0, 0));
                frame.pack();
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                frame.setVisible(true);
            });

            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                int maxHeight = (int) (((double) table.table.getTableHeader().getHeight())
                        + ((double) table.table.getHeight()));
                int yPos = table.table.getTableHeader().getLocationOnScreen().y;
                int xPos = table.table.getLocationOnScreen().x + table.table.getWidth() -
                        table.table.getTableHeader().getColumnModel()
                                .getColumn(2)
                                .getWidth() - 1;
                Color expectedRGB = robot.getPixelColor(xPos, yPos);
                for (int y = yPos; y < yPos + maxHeight; y++) {
                    if (expectedRGB.getRGB() != robot.getPixelColor(xPos, y).getRGB()) {
                        BufferedImage failImage = robot.createScreenCapture(
                                new Rectangle(xPos, yPos, 3, maxHeight));
                        saveImage(failImage, "failureImage.png");
                        passed = false;
                        failureString = "Test Failed at <" + xPos + ", " + y + ">";
                        break;
                    }
                }
            });
            if (!passed) {
                throw new RuntimeException(failureString);
            } else {
                System.out.println("Test Passed!");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void saveImage(BufferedImage image, String fileName) {
        try {
            ImageIO.write(image, "png", new File(fileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class CustomTable {
    public final static int COL_FIRSTNAME = 0;
    public final static int COL_LASTNAME = 1;
    public final static int COL_SALARY = 2;

    static final Class[] classes = {
            String.class,
            String.class,
            Float.class,
    };

    String[] cols = {
            "First name",
            "Last name",
            "Salary",
    };
    List data = new ArrayList();
    JTable table;

    public CustomTable() {
        data.add(new CustomTable.Data("First1", "Last1", 10000f));
        data.add(new CustomTable.Data("First2", "Last2", 10000f));
        data.add(new CustomTable.Data("First3", "Last3", 10000f));
        table = new JTable(new CustomTable.Model());
        table.getColumnModel().getColumn(COL_FIRSTNAME).setMaxWidth(90);
        table.getColumnModel().getColumn(COL_LASTNAME).setMaxWidth(90);
        table.getColumnModel().getColumn(COL_SALARY).setMaxWidth(90);
    }

    class Data {
        String firstname;
        String lastname;
        float salary;

        public Data(String firstname, String lastname, float salary) {
            this.firstname = firstname;
            this.lastname = lastname;
            this.salary = salary;
        }
    }

    class Model extends AbstractTableModel {

        public int getColumnCount() {
            return cols.length;
        }

        public int getRowCount() {
            return data.size();
        }

        public Object getValueAt(int rowIndex, int columnIndex) {
            CustomTable.Data item = (CustomTable.Data) data.get(rowIndex);
            switch (columnIndex) {
                case COL_FIRSTNAME:
                    return item.firstname;
                case COL_LASTNAME:
                    return item.lastname;
                case COL_SALARY:
                    return Float.valueOf(item.salary);
            }
            return null;
        }

        public String getColumnName(int column) {
            return cols[column];
        }

        public Class getColumnClass(int columnIndex) {
            return classes[columnIndex];
        }
    }
}
