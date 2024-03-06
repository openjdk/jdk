/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;

/*
 * @test
 * @bug 4396835
 * @summary Compound font string not printing.
 * @key printer
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PrintCompoundString
 */
public class PrintCompoundString implements Printable {
    private static final String STR = "Test string compound printing \u2203\u2200\u2211";

    private static final String INSTRUCTIONS =
            "This test should print following text\n\n" +
            STR +"\n\n" +
            "If an exception is thrown, or the page doesn't print properly\n" +
            "then the test fails";

    public static void main(String[] args) throws Exception {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .splitUI(PrintCompoundString::createTestUI)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build()
                .awaitAndCheck();
    }

    private static JComponent createTestUI() {
        JButton b = new JButton("Print");
        b.addActionListener((ae) -> {
            try {
                PrinterJob job = PrinterJob.getPrinterJob();
                job.setPrintable(new PrintCompoundString());
                if (job.printDialog()) {
                    job.print();
                }
            } catch (PrinterException ex) {
                ex.printStackTrace();
                String msg = "PrinterException: " + ex.getMessage();
                JOptionPane.showMessageDialog(b, msg, "Error occurred",
                        JOptionPane.ERROR_MESSAGE);
                PassFailJFrame.forceFail(msg);
            }
        });

        Box main = Box.createHorizontalBox();
        main.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        main.add(Box.createHorizontalGlue());
        main.add(b);
        main.add(Box.createHorizontalGlue());
        return main;
    }

    @Override
    public int print(Graphics g, PageFormat pgFmt, int pgIndex) {
        if (pgIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.translate(pgFmt.getImageableX(), pgFmt.getImageableY());
        g2d.drawString(STR, 20, 40);

        return Printable.PAGE_EXISTS;
    }
}
