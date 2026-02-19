/*
 * Copyright (c) 2007, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Font;
import java.awt.Graphics;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.IntStream;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Destination;
import javax.print.attribute.standard.PageRanges;

/*
 * @test
 * @bug 6575331 8297191
 * @key printer
 * @summary Automatically verifies all the pages in a range are printed
 * @run main PageRangesAuto
 */
public class PageRangesAuto implements Pageable, Printable {

    private static final Font font = new Font(Font.SERIF, Font.PLAIN, 50);

    private static final int MAX_PAGE = 10;

    private static final int[][] ranges = {
            {1, 1},
            {1, MAX_PAGE},
            {2, 3},
            {3, 6},
            {4, 7},
            {7, 7},
            {9, MAX_PAGE},
            {MAX_PAGE, MAX_PAGE},
    };

    private enum Type {
        PRINTABLE,
        PAGEABLE
    }

    private final BitSet printedPages = new BitSet();

    /**
     * Configures a printer job and prints it.
     * @param type the type of the interface tested
     *             ({@code Printable} or {@code Pageable})
     * @param pageRange the range of pages to print;
     *                  if {@code null}, print all pages
     * @return a bit set of printed page numbers
     */
    private static BitSet printJob(final Type type,
                                   final PageRanges pageRange)
            throws PrinterException {
        final PageRangesAuto test = new PageRangesAuto();

        final PrinterJob job = PrinterJob.getPrinterJob();
        final String baseName = type.name().toLowerCase();

        switch (type) {
            case PRINTABLE -> job.setPrintable(test);
            case PAGEABLE -> job.setPageable(test);
        }

        String fileName = pageRange == null
                          ? baseName + "-all.pdf"
                          : String.format("%s-%d-%d.pdf",
                                          baseName,
                                          pageRange.getMembers()[0][0],
                                          pageRange.getMembers()[0][1]);

        PrintRequestAttributeSet set = new HashPrintRequestAttributeSet();
        set.add(new Destination(new File(fileName)
                                        .toURI()));
        if (pageRange != null) {
            set.add(pageRange);
        }

        job.print(set);

        return test.printedPages;
    }

    public static void main(String[] args) throws Exception {
        final List<Error> errors = new ArrayList<>();

        for (Type type : Type.values()) {
            BitSet pages; // Printed pages

            // Print all pages
            System.out.println(type + " - all pages");
            pages = printJob(type, null);
            if (!IntStream.range(0, MAX_PAGE)
                          .allMatch(pages::get)) {
                errors.add(new Error("Not all pages printed in " + type + ": "
                                     + pages));
            }

            // Print page range
            for (int[] range : ranges) {
                System.out.println(type + " - " + Arrays.toString(range));
                pages = printJob(type, new PageRanges(range[0], range[1]));
                if (!IntStream.range(range[0] - 1, range[1])
                              .allMatch(pages::get)) {
                    errors.add(new Error("Not all pages printed in " + type
                                         + " within the range "
                                         + Arrays.toString(range)
                                         + ": " + pages));
                }
            }
        }

        if (!errors.isEmpty()) {
            errors.forEach(System.err::println);
            throw new RuntimeException("Errors detected: " + errors.size()
                                       + ". - " + errors.getFirst());
        }
    }

    @Override
    public int print(Graphics g, PageFormat format, int pageIndex)
            throws PrinterException {
        printedPages.set(pageIndex);

        final int pageNo = pageIndex + 1;
        System.out.println("    test.printPage " + pageNo);
        if (pageIndex >= MAX_PAGE) {
            return NO_SUCH_PAGE;
        }

        g.setFont(font);
        g.drawString("Page: " + pageNo,
                     100, 150);

        return PAGE_EXISTS;
    }

    @Override
    public int getNumberOfPages() {
        System.out.println("  test.getNumberOfPages = " + MAX_PAGE);
        return MAX_PAGE;
    }

    @Override
    public PageFormat getPageFormat(int pageIndex)
            throws IndexOutOfBoundsException {
        checkPageIndex(pageIndex);
        return new PageFormat();
    }

    @Override
    public Printable getPrintable(int pageIndex)
            throws IndexOutOfBoundsException {
        checkPageIndex(pageIndex);
        System.out.println("  test.getPrintable(" + (pageIndex + 1) + ")");
        return this;
    }

    private static void checkPageIndex(int pageIndex)
            throws IndexOutOfBoundsException {
        if (pageIndex < 0) {
            throw new IndexOutOfBoundsException("pageIndex < 0");
        }

        if (pageIndex >= MAX_PAGE) {
            throw new IndexOutOfBoundsException("pageIndex >= " + MAX_PAGE);
        }
    }
}
