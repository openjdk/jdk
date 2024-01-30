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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.RescaleOp;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import jtreg.SkippedException;

/*
 * @test
 * @bug 4329866
 * @key printer
 * @summary Confirm that no printing exception is generated.
 * @library /java/awt/regtesthelpers
 * @library /test/lib
 * @build PassFailJFrame
 * @build jtreg.SkippedException
 * @run main/manual DrawImage
 */
public class DrawImage {

    protected static final int _objectBorder = 15;

    protected BufferedImage _image;

    protected PageFormat _pageFormat;

    public DrawImage(BufferedImage image) {
        _image = image;
        PrinterJob pj = PrinterJob.getPrinterJob();
        _pageFormat = pj.defaultPage();
    }

    protected int printImage(Graphics g, PageFormat pf, BufferedImage image) {
        Graphics2D g2D = (Graphics2D) g;
        g2D.transform(new AffineTransform(_pageFormat.getMatrix()));

        int paperW = (int) pf.getImageableWidth(), paperH =
                (int) pf.getImageableHeight();

        int x = (int) pf.getImageableX(), y = (int) pf.getImageableY();
        g2D.setClip(x, y, paperW, paperH);

        // print images
        if (image != null) {
            int imageH = image.getHeight(), imageW = image.getWidth();
            // make slightly smaller (25) than max possible width
            float scaleFactor = ((float) ((paperW - 25) - _objectBorder -
                    _objectBorder) / (float) (imageW));

            BufferedImageOp scaleOp = new RescaleOp(scaleFactor, 0, null);
            g2D.drawImage(image, scaleOp, x + _objectBorder, y + _objectBorder);
            return Printable.PAGE_EXISTS;
        } else {
            return Printable.NO_SUCH_PAGE;
        }
    }

    public void print() {
        try {
            final PrinterJob pj = PrinterJob.getPrinterJob();
            pj.setJobName("Print Image");
            pj.setPrintable((g, pf, pageIndex) -> {
                int result = Printable.NO_SUCH_PAGE;
                if (pageIndex == 0) {
                    result = printImage(g, _pageFormat, _image);
                }
                return result;
            });
            if (pj.printDialog()) {
                try {
                    pj.print();
                } catch (PrinterException e) {
                    System.out.println(e);
                }
            }

        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    private static final String INSTRUCTIONS =
            "You must have a printer available to perform this test.\n" +
            "\n" +
            "The test passes if you get a printout of a gray rectangle\n" +
            "with white text without any exception.";

    public static void main(String[] args) throws Exception {

        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new SkippedException("Printer not configured or available."
                    + " Test cannot continue.");
        }

        PassFailJFrame passFailJFrame = new PassFailJFrame.Builder()
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build();

        BufferedImage image = prepareFrontImage();
        DrawImage pt = new DrawImage(image);
        pt.print();
        passFailJFrame.awaitAndCheck();
    }

    public static BufferedImage prepareFrontImage() {
        // build my own test images
        BufferedImage result = new BufferedImage(400, 200,
                BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D g2D = (Graphics2D) result.getGraphics();
        g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        int w = result.getWidth(), h = result.getHeight();

        g2D.setColor(Color.gray);
        g2D.fill(new Rectangle(0, 0, w, h));

        g2D.setColor(Color.white);

        AffineTransform original = g2D.getTransform();
        AffineTransform originXform = AffineTransform.getTranslateInstance(w /
                5, h / 5);
        g2D.transform(originXform);

        g2D.drawString("Front Side", 20, h / 2);

        return result;
    }
}
