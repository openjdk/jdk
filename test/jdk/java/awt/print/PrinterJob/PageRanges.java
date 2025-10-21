/*
 * Copyright (c) 2007, 2025 Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 6575331 8320677
 * @key printer
 * @summary The specified pages should be printed.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PageRanges
 */

import java.awt.Graphics;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

public class PageRanges implements Printable {
    private static final String INSTRUCTIONS = """
                "This test prints two jobs, and tests that the specified range",
                "of pages is printed. You must have a printer installed for this test.",
                "In the first dialog, select a page range of 2 to 3, and press OK",
                "In the second dialog, select ALL, to print all pages (in total 5 pages).",
                "Collect the two print outs and confirm the jobs printed correctly",
                """;

    public static void main(String args[]) throws Exception {
        PassFailJFrame passFailJFrame = new PassFailJFrame(INSTRUCTIONS);
        passFailJFrame.positionTestWindow(null, PassFailJFrame.Position.HORIZONTAL);

        PrinterJob job = PrinterJob.getPrinterJob();
        if (job.getPrintService() == null) {
            System.out.println("No printer available");
            PassFailJFrame.forcePass();
        }
        job.setPrintable(new PageRanges());
        if (job.printDialog()) {
            job.print();
        }
        if (job.printDialog()) {
            job.print();
        }

        passFailJFrame.awaitAndCheck();
    }

    public int print(Graphics g, PageFormat pf, int pi)
                     throws PrinterException {

        if (pi >= 5) {
            return NO_SUCH_PAGE;
        }

        g.drawString("Page : " + (pi+1), 200, 200);
        return PAGE_EXISTS;
    }
}
