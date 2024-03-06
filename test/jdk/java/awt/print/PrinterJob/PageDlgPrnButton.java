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
import java.awt.Rectangle;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import jtreg.SkippedException;

/*
 * @test
 * @bug 4956397
 * @key printer
 * @requires os.family=="windows"
 * @library /test/lib /java/awt/regtesthelpers
 * @build PassFailJFrame jtreg.SkippedException
 * @run main/manual PageDlgPrnButton
 */
public class PageDlgPrnButton implements Printable {
    private static final String INSTRUCTIONS =
            "This test brings up a native Windows page dialog.\n" +
            "Click on the Printer... button and change the selected printer. \n" +
            "Test passes if the printout comes from the new selected printer.";

    public static void main(String[] args) throws Exception {
        final int serviceCount = PrinterJob.lookupPrintServices().length;
        if (serviceCount == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }
        if (serviceCount < 2) {
            throw new SkippedException("The test requires at least 2 printers.");
        }

        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build();

        pageDialogExample();
        passFailJFrame.awaitAndCheck();
    }

    // This example just displays the page dialog - you cannot change
    // the printer (press the "Printer..." button and choose one if you like).
    public static void pageDialogExample() throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        PageFormat originalPageFormat = job.defaultPage();
        PageFormat pageFormat = job.pageDialog(originalPageFormat);

        job.setPrintable(new PageDlgPrnButton(), pageFormat);
        if (job.printDialog()) {
            job.print();
        }
    }

    @Override
    public int print(Graphics g, PageFormat pageFormat, int pageIndex) {
        final int boxWidth = 100;
        final int boxHeight = 100;
        final Rectangle rect = new Rectangle(0, 0, boxWidth, boxHeight);
        final double pageH = pageFormat.getImageableHeight();
        final double pageW = pageFormat.getImageableWidth();
        final Graphics2D g2d = (Graphics2D) g;

        if (pageIndex > 0) {
            return NO_SUCH_PAGE;
        }

        // Move the (x,y) origin to account for the left-hand and top margins
        g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

        // Draw the page bounding box
        g2d.drawRect(0, 0, (int) pageW, (int) pageH);

        // Select the smaller scaling factor so that the figure
        // fits on the page in both dimensions
        final double scale = Math.min((pageW / boxWidth), (pageH / boxHeight));

        if (scale < 1.0) {
            g2d.scale(scale, scale);
        }

        // Paint the scaled component on the printer
        g2d.fillRect(rect.x, rect.y, rect.width, rect.height);

        return PAGE_EXISTS;
    }
}
