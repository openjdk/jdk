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

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/*
 * @test
 * @key headful
 * @bug 8210807
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test to check if JTable can be printed when JScrollPane added to it.
 * @run main/manual JTableScrollPrintTest
 */

public class JTableScrollPrintTest {
    public static JFrame frame;
    public static PassFailJFrame passFailJFrame;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                initialize();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        passFailJFrame.awaitAndCheck();
    }

    public static void initialize() throws Exception {
        final String INSTRUCTIONS = """
                Instructions to Test:
                1. Print table onto Paper/PDF, using the Print Dialog.
                2. If entire table is printed, then the Test is PASS.
                3. If table is partially printed without table cells,
                then the Test is FAIL.
                """;
        TestTable testTable = new TestTable(true);
        frame = new JFrame("JTable Print Test");
        passFailJFrame = new PassFailJFrame("Test Instructions", INSTRUCTIONS, 5L, 6, 35);

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.VERTICAL);
        frame.add(testTable);
        frame.pack();
        frame.setVisible(true);
        PrintUtilities printerJob = new PrintUtilities(testTable);
        printerJob.print("Test BackingStore Image Print");
    }

    public static class TestTable extends JPanel {
        public TestTable(Boolean useScrollPane) {

            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            DefaultTableModel model = new DefaultTableModel();
            model.addColumn("Column 1");
            model.addColumn("Column 2");
            model.addColumn("Column 3");
            model.addColumn("Column 4");

            for (int row = 1; row <= 5; row++) {
                model.addRow(new Object[]{
                        "R" + row + " C1", "R" + row + " C2", "R" + row + " C3", "R" + row + " C4"});
            }

            JTable table = new JTable(model);

            if (useScrollPane == true) {
                JScrollPane sp = new JScrollPane(table,
                        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                sp.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
                add(sp);
            } else {
                add(table.getTableHeader());
                add(table);
            }
        }
    }

    static class PrintUtilities implements Printable {
        private Component componentToBePrinted;

        public void printComponent(Component c, String jobname) {
            new PrintUtilities(c).print(jobname);
        }

        public PrintUtilities(Component componentToBePrinted) {
            this.componentToBePrinted = componentToBePrinted;
        }

        public void print(String jobname) {
            PrinterJob printJob = PrinterJob.getPrinterJob();
            PageFormat pf = printJob.defaultPage();
            pf.setOrientation(PageFormat.PORTRAIT);

            // set margins to 1/2"
            Paper p = new Paper();
            p.setImageableArea(36, 36, p.getWidth() - 72, p.getHeight() - 72);
            pf.setPaper(p);

            printJob.setPrintable(this, pf);
            printJob.setJobName(jobname);

            if (printJob.printDialog()) {
                try {
                    printJob.print();
                } catch (PrinterException pe) {
                    System.out.println("Error printing: " + pe);
                }
            }
        }

        public int print(Graphics g, PageFormat pageFormat, int pageIndex) {
            if (pageIndex > 0) {
                return NO_SUCH_PAGE;
            } else {
                Graphics2D g2d = (Graphics2D)g;
                g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
                Component c = componentToBePrinted;
                c.setSize(c.getPreferredSize());

                double panelX = c.getWidth();
                double panelY = c.getHeight();
                float imageableX = (float) pageFormat.getImageableWidth() - 1;
                float imageableY = (float) pageFormat.getImageableHeight() - 1;

                double xscale = imageableX/panelX;
                double yscale = imageableY/panelY;
                double optimalScale;
                if (xscale < yscale) {
                    optimalScale = xscale;
                } else {
                    optimalScale = yscale;
                }

                if (optimalScale > 1) {
                    optimalScale = 1;
                }

                g2d.scale(optimalScale, optimalScale);
                c.paint(g2d);
                return PAGE_EXISTS;
            }
        }
    }
}
