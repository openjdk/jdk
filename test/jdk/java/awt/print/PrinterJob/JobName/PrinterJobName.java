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
import java.awt.print.PrinterJob;

/*
 * @test
 * @bug 4205601
 * @summary setJobName should be used by PrinterJob
 * @key printer
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PrinterJobName
 */
public class PrinterJobName implements Printable {
    private static final String THE_NAME = "Testing the Job name setting";

    private static final String INSTRUCTIONS =
            "This test prints a page with a banner/job name of\n\n" +
            THE_NAME;

    public static void main(String[] args) throws Exception {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build();

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName(THE_NAME);
        job.setPrintable(new PrinterJobName());
        job.print();
        passFailJFrame.awaitAndCheck();
    }

    @Override
    public int print(Graphics g, PageFormat pgFmt, int pgIndex) {
        if (pgIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.translate(pgFmt.getImageableX(), pgFmt.getImageableY());
        g2d.drawString("Name is: " + THE_NAME, 20, 20);
        return Printable.PAGE_EXISTS;
    }
}
