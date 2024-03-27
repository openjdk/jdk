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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;

import javax.swing.JButton;

/*
 * @test
 * @bug 5024549
 * @key printer
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Pass if dialogs are modal.
 * @run main/manual PrintModalDialog
 */

public class PrintModalDialog {
    private static JButton jButton1;
    private static final String INSTRUCTIONS =
            """
             Click the "PRINT" button in the test window. A new dialog
             should appear to print the page. Test if this print new dialog
             is actually modal.

             Modal in this case means that it blocks the user from interacting
             with other windows in the same application. You may still be able
             to interact with unrelated applications on the desktop.
             One sure way to test this is to first show the print dialog and
             then press "Fail", because if you can click on "Fail" and have it
             respond, then the print dialog was not modal. If clicking on it
             does nothing then cancel the print dialog and do the same for the
             other print dialog. If all is well, then press Pass.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame
                .builder()
                .title("PrintModalDialog Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(40)
                .splitUIBottom(PrintModalDialog::createAndShowGUI)
                .build()
                .awaitAndCheck();
    }

    public static JButton createAndShowGUI() {
        jButton1 = new JButton("PRINT");
        jButton1.addActionListener(e -> jButton1_actionPerformed(e));
        return jButton1;
    }

    static void jButton1_actionPerformed(ActionEvent e) {
        PrinterJob printJob = null;
        PageFormat pageFormat = null;
        Paper prtPaper = null;
        boolean bPrintFlg = true;

        try {
            printJob = PrinterJob.getPrinterJob();
        }
        catch (SecurityException se) {
            bPrintFlg = false;
        }

        if (bPrintFlg) {
            pageFormat = printJob.pageDialog(printJob.defaultPage());
            System.out.println("PrintModalDialog: pageFormat = " +
                    pageFormat.getWidth() / 72.0 + " x " +
                    pageFormat.getHeight() / 72.0);
            if (pageFormat != null) {
                prtPaper = pageFormat.getPaper();
                pageFormat.setPaper(prtPaper);
                printJob.setPrintable((g, pf, page) -> {
                    System.out.println("Calling print");
                    if (page == 0) {
                        Graphics2D g2 = (Graphics2D)g;
                        g2.translate(pf.getImageableX(), pf.getImageableY());
                        g2.setColor(Color.black);
                        g2.drawString("Hello World", 20, 100);

                        return Printable.PAGE_EXISTS;
                    }
                    return Printable.NO_SUCH_PAGE;
                }, pageFormat);
            }

            if (printJob.printDialog()) {
                try {
                    printJob.print();
                }
                catch (java.awt.print.PrinterException ex) {
                    ex.printStackTrace();
                    String msg = "PrinterException: " + ex.getMessage();
                    PassFailJFrame.forceFail(msg);
                }
            }
        }
    }
}
