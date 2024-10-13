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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

/*
 * @test
 * @bug 4236095
 * @summary  Confirm that you get three pages of output, one
 *           each in portrait, landscape and reverse landscape
 *           orientations.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @key printer
 * @run main/manual Orient
 */
public class Orient implements Printable {
    private static final String INSTRUCTIONS =
            """
             This test will automatically initiate a print.

             A passing test will print three pages each containing a large oval
             with the text describing the orientation: PORTRAIT, LANDSCAPE
             or REVERSE_LANDSCAPE, inside of it. The first page will
             be emitted in portrait orientation, the second page in landscape
             orientation and the third page in reverse-landscape orientation.

             On each page the oval will be wholly within the imageable area of the page.
             Axes will indicate the direction of increasing X and Y.

             Test failed if the oval on the page clipped against the imageable area.
            """;

    private static void printOrientationJob() throws PrinterException {
        PrinterJob pjob = PrinterJob.getPrinterJob();
        Book book = new Book();
        // Page 1
        PageFormat portrait = pjob.defaultPage();
        portrait.setOrientation(PageFormat.PORTRAIT);
        book.append(new Orient(), portrait);

        // Page 2
        PageFormat landscape = pjob.defaultPage();
        landscape.setOrientation(PageFormat.LANDSCAPE);
        book.append(new Orient(), landscape);

        // Page 3
        PageFormat reverseLandscape = pjob.defaultPage();
        reverseLandscape.setOrientation(PageFormat.REVERSE_LANDSCAPE);
        book.append(new Orient(), reverseLandscape);

        pjob.setPageable(book);

        pjob.print();
    }

    @Override
    public int print(Graphics g, PageFormat pf, int pageIndex) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.translate(pf.getImageableX(), pf.getImageableY());
        drawGraphics(g2d, pf);
        return Printable.PAGE_EXISTS;
    }

    void drawGraphics(Graphics2D g, PageFormat pf) {
        String orientation = switch (pf.getOrientation()) {
            case PageFormat.PORTRAIT -> "PORTRAIT";
            case PageFormat.LANDSCAPE -> "LANDSCAPE";
            case PageFormat.REVERSE_LANDSCAPE -> "REVERSE_LANDSCAPE";
            default -> "INVALID";
        };
        g.setColor(Color.black);
        g.drawString(orientation, 100, 300);
        g.draw(new Ellipse2D.Double(0, 0,
                pf.getImageableWidth(), pf.getImageableHeight()));
        g.drawString("(0,0)", 5, 15);
        g.drawLine(0, 0, 300, 0);
        g.drawString("X", 300, 15);
        g.drawLine(0, 0, 0, 300);
        g.drawString("Y", 5, 300);
    }

    public static void main(String args[]) throws Exception {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build();
        printOrientationJob();
        passFailJFrame.awaitAndCheck();
    }
}
