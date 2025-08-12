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

import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import javax.imageio.ImageIO;
/*
 * @test
 * @key headful
 * @bug 5108458
 * @summary Test to check Right alignment of JTable data
 * @run main JTableRightOrientationTest
 */

public class JTableRightOrientationTest {
    static JFrame frame;
    static CustomTable customTableObj;
    static volatile Rectangle tableBounds;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing LAF : " + laf.getClassName());
            SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
            try {
                SwingUtilities.invokeAndWait(() -> {
                    frame = new JFrame("JTable RTL column layout");
                    JPanel panel = new JPanel(new GridBagLayout());
                    frame.setContentPane(panel);
                    customTableObj = new CustomTable();
                    panel.add(new JScrollPane(customTableObj.table),
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
                    int allColumnWidths = 0;
                    for (int i = 0; i < customTableObj.table.getColumnCount(); i++) {
                        allColumnWidths += customTableObj.table.getTableHeader().getColumnModel()
                                .getColumn(i)
                                .getWidth();
                    }
                    Point tableLocation = customTableObj.table.getLocationOnScreen();
                    Dimension tableSize = customTableObj.table.getSize();
                    tableSize.width -= allColumnWidths;
                    tableBounds = new Rectangle(tableLocation, tableSize);
                });

                BufferedImage bufferedImage = robot.createScreenCapture(tableBounds);

                int expectedRGB = bufferedImage.getRGB(0, 0);
                for (int x = 0; x < bufferedImage.getWidth(); x++) {
                    for (int y = 0; y < bufferedImage.getHeight(); y++) {
                        if (expectedRGB != bufferedImage.getRGB(x, y)) {
                            saveImage(bufferedImage);
                            throw new RuntimeException("Test Failed at <" + x + ", " + y + ">");
                        }
                    }
                }

                System.out.println("Test Passed!");
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    if (frame != null) {
                        frame.dispose();
                    }
                });
            }
            robot.waitForIdle();
            robot.delay(200);
        }
    }

    private static void saveImage(BufferedImage image) {
        try {
            ImageIO.write(image, "png", new File("failureImage.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported LAF: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}

class CustomTable {
    private static final int COL_FIRSTNAME = 0;
    private static final int COL_LASTNAME = 1;
    private static final int COL_SALARY = 2;

    static final Class<?>[] classes = {
            String.class,
            String.class,
            Float.class,
    };

    String[] cols = {
            "First name",
            "Last name",
            "Salary",
    };
    List<CustomTable.Data> data = new ArrayList<>();
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

    record Data(String firstName, String lastName, float salary) {}

    class Model extends AbstractTableModel {

        public int getColumnCount() {
            return cols.length;
        }

        public int getRowCount() {
            return data.size();
        }

        public Object getValueAt(int rowIndex, int columnIndex) {
            CustomTable.Data item = data.get(rowIndex);
            switch (columnIndex) {
                case COL_FIRSTNAME:
                    return item.firstName;
                case COL_LASTNAME:
                    return item.lastName;
                case COL_SALARY:
                    return item.salary;
            }
            return null;
        }

        public String getColumnName(int column) {
            return cols[column];
        }

        public Class<?> getColumnClass(int columnIndex) {
            return classes[columnIndex];
        }
    }
}
