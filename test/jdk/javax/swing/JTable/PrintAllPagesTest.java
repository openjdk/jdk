/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8257810 8322135
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary  Verifies if all pages are printed if scrollRectToVisible is set.
 * @run main/manual PrintAllPagesTest
 */
import java.awt.print.PrinterJob;
import java.awt.print.PrinterException;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.WindowConstants;

public class PrintAllPagesTest {
    static JFrame f;
    static JTable table;

    static final String INSTRUCTIONS = """
         Note: You must have a printer installed for this test.
         If printer is not available, the test passes automatically.

         A JTable with 1000 rows and a print dialog will be shown.
         If only 1 page is printed,
         then press fail else press pass.
            """;

    public static void main(String[] args) throws Exception {

        PrinterJob pj = PrinterJob.getPrinterJob();
        if (pj.getPrintService() == null) {
            System.out.println("Printer not configured or available."
                    + " Test cannot continue.");
            PassFailJFrame.forcePass();
        }

        PassFailJFrame passFailJFrame = new PassFailJFrame(INSTRUCTIONS);

        SwingUtilities.invokeAndWait(() -> {
            printAllPagesTest();
            // add the test frame to dispose
            PassFailJFrame.addTestWindow(f);

            // Arrange the test instruction frame and test frame side by side
            PassFailJFrame.positionTestWindow(f, PassFailJFrame.Position.HORIZONTAL);
            f.setVisible(true);

            boolean ret;
            try {
                ret = table.print();
            } catch (PrinterException ex) {
                ret = false;
            }
            if (!ret) {
                PassFailJFrame.forceFail("Printing cancelled/failed");
            }
        });
        passFailJFrame.awaitAndCheck();
    }


    private static void printAllPagesTest() {
        TableModel dataModel = new AbstractTableModel() {
            @Override
            public int getColumnCount() {
                return 10;
            }

            @Override
            public int getRowCount() {
                return 1000;
            }

            @Override
            public Object getValueAt(int row, int col) {
                return Integer.valueOf(0 == col ? row + 1 : row * col);
            }
        };
        table = new JTable(dataModel);
        JScrollPane scrollpane = new JScrollPane(table);
        table.scrollRectToVisible(table.getCellRect(table.getRowCount() - 1,
                0, false));

        f = new JFrame("Table test");
        f.add(scrollpane);
        f.setSize(500, 400);
    }
}
