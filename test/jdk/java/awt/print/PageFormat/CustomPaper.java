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

import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import jtreg.SkippedException;

/*
 * @test
 * @bug 4355514
 * @key printer
 * @summary Prints a rectangle to show the imageable area of a
 *          12in x 14in custom paper size.
 * @library /java/awt/regtesthelpers
 * @library /test/lib
 * @build PassFailJFrame
 * @build jtreg.SkippedException
 * @run main/manual CustomPaper 4355514
 */

/*
 * @test
 * @bug 4385157
 * @key printer
 * @summary Prints a rectangle to show the imageable area of a
 *          12in x 14in custom paper size.
 * @library /java/awt/regtesthelpers
 * @library /test/lib
 * @build PassFailJFrame
 * @build jtreg.SkippedException
 * @run main/manual CustomPaper 4385157
 */
public class CustomPaper implements Pageable, Printable {

    private static final double PIXELS_PER_INCH = 72.0;

    private final PrinterJob printerJob;
    private PageFormat pageFormat;

    CustomPaper() {
        printerJob = PrinterJob.getPrinterJob();
        createPageFormat();
    }

    private void createPageFormat() {
        pageFormat = new PageFormat();
        Paper p = new Paper();
        double width = 12.0 * PIXELS_PER_INCH;
        double height = 14.0 * PIXELS_PER_INCH;
        double iwidth = width - 2.0 * PIXELS_PER_INCH;
        double iheight = height - 2.0 * PIXELS_PER_INCH;
        p.setSize(width, height);
        p.setImageableArea(PIXELS_PER_INCH, PIXELS_PER_INCH, iwidth, iheight);
        pageFormat.setPaper(p);
    }

    @Override
    public Printable getPrintable(int index) {
        return this;
    }

    @Override
    public PageFormat getPageFormat(int index) {
        return pageFormat;
    }

    @Override
    public int getNumberOfPages() {
        return 1;
    }

    private void print() throws PrinterException {
        if (printerJob.printDialog()) {
            printerJob.setPageable(this);
            printerJob.print();
        } else {
            PassFailJFrame.forceFail("Printing canceled by user");
        }
    }

    @Override
    public int print(Graphics g, PageFormat pf, int pageIndex) {
        if (pageIndex == 0) {
            Graphics2D g2 = (Graphics2D) g;
            Rectangle2D r = new Rectangle2D.Double(pf.getImageableX(),
                    pf.getImageableY(),
                    pf.getImageableWidth(),
                    pf.getImageableHeight());
            g2.setStroke(new BasicStroke(3.0f));
            g2.draw(r);
            return PAGE_EXISTS;
        } else {
            return NO_SUCH_PAGE;
        }
    }

    private static final String TOP = """
         You must have a printer that supports custom paper size of
         at least 12 x 14 inches to perform this test. It requires
         user interaction and you must have a 12 x 14 inch paper available.

        """;

    private static final String BOTTOM = """

         Visual inspection of the one-page printout is needed. A passing
         test will print a rectangle of the imageable area which is
         approximately 10 x 12 inches.
        """;

    private static final String INSTRUCTIONS_4355514 = """
         Select the printer in the Print Setup dialog and add a custom
         paper size under 'Printer properties' Paper selection menu.
         Set the dimension to width=12 inches and height=14 inches.
         Select this custom paper size before proceeding to print.
        """;

    private static final String INSTRUCTIONS_4385157 = """
         Click OK on print dialog box to print.
        """;

    public static void main(String[] args) throws Exception {
        String instructions;

        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new SkippedException("Printer not configured or available."
                    + " Test cannot continue.");
        }

        if (args.length != 1) {
            throw new RuntimeException("Select a test by passing 4355514 or 4385157");
        }

        instructions = switch (args[0]) {
            case "4355514" -> TOP + INSTRUCTIONS_4355514 + BOTTOM;
            case "4385157" -> TOP + INSTRUCTIONS_4385157 + BOTTOM;
            default -> throw new RuntimeException("Unknown bugid " + args[0] + "."
                    + "Valid values: 4355514 or 4385157");
        };

        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .title("CustomPaper Test Instructions")
                .instructions(instructions)
                .testTimeOut(5)
                .rows((int) instructions.lines().count() + 1)
                .columns(45)
                .build();

        CustomPaper pt = new CustomPaper();
        pt.print();
        passFailJFrame.awaitAndCheck();
    }
}
