/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.DialogTypeSelection;
import javax.print.attribute.standard.PageRanges;
import jtreg.SkippedException;

/*
 * @test
 * @bug 8061267
 * @key printer
 * @library /java/awt/regtesthelpers
 * @library /test/lib
 * @build PassFailJFrame
 * @build jtreg.SkippedException
 * @summary The specified page range should be displayed in the dialog
 * @run main/manual PageRangesDlgTest
 */

public class PageRangesDlgTest implements Printable {

    private static void showPrintDialogs() throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable(new PageRangesDlgTest());
        PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
        aset.add(new PageRanges(2, 3));
        if (job.printDialog(aset)) {
            job.print(aset);
        }

        job = PrinterJob.getPrinterJob();
        job.setPrintable(new PageRangesDlgTest());
        aset.add(DialogTypeSelection.NATIVE);
        if (job.printDialog(aset)) {
            job.print();
        }
    }

    public static void main(String[] args) throws Exception {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new SkippedException("Printer not configured or available."
                    + " Test cannot continue.");
        }

        String instruction = """
                Note: You must have a printer installed for this test.
                If printer is not installed then the test passes automatically.

                This test is to check that the print dialog displays the specified,
                page ranges. It is valid only on dialogs which support page ranges,
                In each dialog, check that a page range of 2 to 3 is requested,
                Optionally press Print instead of Cancel, and verify that the,
                correct number/set of pages is printed.
                """;

        PassFailJFrame passFailJFrame = new PassFailJFrame(instruction, 10);
        PassFailJFrame.positionTestWindow(null, PassFailJFrame.Position.HORIZONTAL);
        showPrintDialogs();
        passFailJFrame.awaitAndCheck();
    }

    public int print(Graphics g, PageFormat pf, int pi)
                     throws PrinterException {

        System.out.println("pi="+pi);
        if (pi >= 5) {
            return NO_SUCH_PAGE;
        }

        g.drawString("Page : " + (pi+1), 200, 200);

        return PAGE_EXISTS;
    }
}
