/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Sides;
import javax.swing.JFrame;

import static java.awt.print.PageFormat.LANDSCAPE;
import static java.awt.print.PageFormat.PORTRAIT;
import static java.awt.print.PageFormat.REVERSE_LANDSCAPE;

/*
 * @test
 * @bug 8307246
 * @key printer
 * @library ../../../regtesthelpers
 * @build PassFailJFrame
 * @summary Test for comparing offsets of images drawn with opaque
 *          and translucent colors printed in all orientations
 * @run main/manual AlphaPrintingOffsets
 */

public class AlphaPrintingOffsets {
    private static final String INSTRUCTIONS =
                    "Tested bug occurs only on-paper printing " +
                    "so you mustn't use PDF printer\n\n" +
                    "1. Java print dialog should appear.\n" +
                    "2. Press the Print button on the Java Print dialog.\n" +
                    "3. Please check the page margin rectangle are properly " +
                    "drawn and visible on all sides on all pages.\n" +
                    "If so, press PASS, else press FAIL.\n\n" +
                    "Also you may run this test in paper-saving mode. Due " +
                    "to tested bug affects pages printed with transparency " +
                    "in LANDSCAPE and REVERSE_LANDSCAPE orientations there " +
                    "is an option to print only 2 pages affected. " +
                    "To do it pass PaperSavingMode parameter.";

    private static boolean isPaperSavingMode = false;

    public static void main(String[] args) throws Exception {
        if (PrinterJob.lookupPrintServices().length > 0) {

            String instructionsHeader = "This test prints 6 pages with page " +
                    "margin rectangle and a text message. \n";
            if (args.length > 0) {
                isPaperSavingMode = args[0].equals("PaperSavingMode");
            }
            if (isPaperSavingMode) {
                instructionsHeader = "This test prints 2 pages with page " +
                        "margin rectangle and a text message. \n";
            }
            PassFailJFrame.builder()
                    .instructions(instructionsHeader + INSTRUCTIONS)
                    .rows(15)
                    .testUI(() -> createTestUI())
                    .build()
                    .awaitAndCheck();

        } else {
            throw new RuntimeException("Test failed : Printer not configured or available.");
        }

    }

    public static JFrame createTestUI() {
        JFrame testUI = new JFrame("Print images");
        testUI.setSize(1, 1);
        testUI.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                super.windowOpened(e);
                print();
            }

            @Override
            public void windowActivated(WindowEvent e) {
                super.windowActivated(e);
                testUI.setVisible(false);
            }
        });

        return testUI;
    }

    private static void print() {
        PrinterJob printerJob = PrinterJob.getPrinterJob();
        PageFormat pageFormatP = printerJob.defaultPage();

        Paper paper = pageFormatP.getPaper();
        paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
        pageFormatP.setPaper(paper);

        PageFormat pageFormatL = (PageFormat) pageFormatP.clone();
        PageFormat pageFormatRL = (PageFormat) pageFormatP.clone();

        pageFormatL.setOrientation(LANDSCAPE);
        pageFormatRL.setOrientation(REVERSE_LANDSCAPE);

        Printable printableTransparent = new CustomPrintable(254);
        if (isPaperSavingMode) {
            Book bookPageSavingTest = new Book();
            bookPageSavingTest.append(printableTransparent, pageFormatL);
            bookPageSavingTest.append(printableTransparent, pageFormatRL);
            printerJob.setPageable(bookPageSavingTest);
        } else {
            Printable printableOpaque = new CustomPrintable(255);
            Book bookDefaultTest = new Book();
            bookDefaultTest.append(printableOpaque, pageFormatP);
            bookDefaultTest.append(printableTransparent, pageFormatP);
            bookDefaultTest.append(printableOpaque, pageFormatL);
            bookDefaultTest.append(printableTransparent, pageFormatL);
            bookDefaultTest.append(printableOpaque, pageFormatRL);
            bookDefaultTest.append(printableTransparent, pageFormatRL);
            printerJob.setPageable(bookDefaultTest);
        }
        PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
        aset.add(Sides.ONE_SIDED);

        if (printerJob.printDialog()) {
            try {
                printerJob.print(aset);
            } catch (PrinterException e) {
                e.printStackTrace();
                throw new RuntimeException("Exception whilst printing.");
            }
        } else {
            throw new RuntimeException("Test failed : " +
                    "User selected 'Cancel' button on the print dialog");
        }
    }

    static class CustomPrintable implements Printable {
        private static final int THICKNESS = 10;
        private static final int MARGIN = 15;
        private static final int SMALL_RECTANGLE_SIZE = 5;
        private final int alphaValue;

        public CustomPrintable(int alpha) {
            alphaValue = alpha;
        }

        private String getOrientStr(int orient) {
            switch (orient) {
                case PORTRAIT:
                    return "PORTRAIT";
                case LANDSCAPE:
                    return "LANDSCAPE";
                case REVERSE_LANDSCAPE:
                    return "REVERSE_LANDSCAPE";
                default:
                    return "BAD Orientation";
            }
        }

        @Override
        public int print(Graphics g, PageFormat pageFormat, int pageIndex) {

            if (pageIndex > 5) {
                return Printable.NO_SUCH_PAGE;
            }

            drawRectangle(g, pageFormat.getImageableX(), pageFormat.getImageableY(),
                    pageFormat.getImageableWidth(), pageFormat.getImageableHeight());

            drawSmallRectangle(g, pageFormat.getImageableX(), pageFormat.getImageableY(),
                    pageFormat.getImageableWidth(), pageFormat.getImageableHeight());

            drawMsg(g, 300, 300, pageFormat.getOrientation());
            return Printable.PAGE_EXISTS;
        }

        private void drawRectangle(Graphics g, double x, double y, double width, double height) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setStroke(new BasicStroke(THICKNESS));

            // Draw rectangle with thick border lines
            g2d.drawRect((int) x + MARGIN, (int) y + MARGIN,
                    (int) width - MARGIN * 2, (int) height - MARGIN * 2);
        }

        private void drawSmallRectangle(Graphics g, double x, double y, double width, double height) {
            Graphics2D g2d = (Graphics2D) g;
            Color originalColor = g2d.getColor();

            g2d.setColor(new Color(0, 0, 0, alphaValue));
            // Calculate the position to center the smaller rectangle
            double centerX = x + (width - SMALL_RECTANGLE_SIZE) / 2;
            double centerY = y + (height - SMALL_RECTANGLE_SIZE) / 2;

            g2d.fillRect((int) centerX, (int) centerY, SMALL_RECTANGLE_SIZE, SMALL_RECTANGLE_SIZE);

            g2d.setColor(originalColor);
        }

        private void drawMsg(Graphics g, int x, int y, int orient) {
            Graphics2D g2d = (Graphics2D) g;

            String msg = "Orient= " + getOrientStr(orient);
            msg += " Color=" + (alphaValue != 255 ? " ALPHA" : " OPAQUE");
            g2d.drawString(msg, x, y);
        }
    }
}

