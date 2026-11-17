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

/*
 * @test
 * @bug 4337898
 * @key headful
 * @summary Verifies Serializing DefaultTableCellRenderer doesn't change colors
 * @run main DefRendererSerialize
 */

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.SwingUtilities;
import java.util.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class DefRendererSerialize {

    private static JFrame frame;
    private static JTable table;
    private static volatile DefaultTableCellRenderer tcr;
    private static String[][] rowData = { {"1-1","1-2","1-3"},
                                          {"2-1","","2-3"},
                                          {"3-1","3-2","3-3"} };

    private static String[] columnData = {"Column 1", "Column 2", "Column 3"};
    private static volatile Rectangle tableRect;
    private static volatile Point tableOnScreen;
    private static volatile Point p;
    private static Color fg, bg;

    public static void main (String[] args) throws Exception {
        try {

            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame();
                table = new JTable(rowData, columnData);

                DefaultTableCellRenderer tcr = new DefaultTableCellRenderer();
                table.setDefaultRenderer(table.getColumnClass(1), tcr);

                fg = tcr.getForeground();
                bg = tcr.getBackground();
                System.out.println("renderer fg " + fg + " bg " + bg);
                tcr = (DefaultTableCellRenderer) table.getDefaultRenderer(table.getColumnClass(1));

                // If this try block is removed, table text remains black on white.
                byte[] serializedObject = null;
                try {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    ObjectOutputStream ostream = new ObjectOutputStream(bytes);
                    ostream.writeObject(tcr);
                    ostream.flush();
                    serializedObject = bytes.toByteArray();
                } catch (IOException ioex) {
                    throw new RuntimeException(ioex);
                }

                if (serializedObject == null) {
                    throw new RuntimeException("FAILED: Serialized byte array in null");
                }
                try {
                    DefaultTableCellRenderer destcr;
                    try (ObjectInputStream inputStream =
                            new ObjectInputStream(new ByteArrayInputStream(serializedObject))) {
                        destcr = (DefaultTableCellRenderer) inputStream.readObject();
                    }
                    System.out.println("deserialized renderer fg " + fg + " bg " + bg);
                    if (!(fg == destcr.getForeground()) || !(bg == destcr.getBackground())) {
                        throw new RuntimeException("Deserialized foreground and background color not same");
                    }
                } catch (IOException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }

                frame.add(table);

                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                tableRect = table.getCellRect(1, 1, true);
                tableOnScreen = table.getLocationOnScreen();

                p = new Point(tableOnScreen.x + tableRect.x + tableRect.width / 2,
                                tableOnScreen.y + tableRect.y + tableRect.height / 2);

            });
            Color pixelColor = robot.getPixelColor(p.x, p.y);
            System.out.println("pixelColor " + pixelColor);
            if (!pixelColor.equals(Color.white)) {
                throw new RuntimeException("Serializing DefaultTableCellRenderer changes colors");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
