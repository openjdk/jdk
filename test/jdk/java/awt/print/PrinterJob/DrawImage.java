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

/*
 * @test
 * @bug 4329866
 * @key printer
 * @summary Confirm that no printing exception is generated.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DrawImage
 */
public class DrawImage {
    private static final int OBJECT_BORDER = 15;

    private static final String INSTRUCTIONS =
            "This test will automatically initiate a print\n\n" +
            "Test passes if you get a printout of a gray rectangle\n" +
            "with white text without any exception.";

    private final BufferedImage image;
    private final PageFormat pageFormat;

    private DrawImage(BufferedImage image) {
        this.image = image;
        PrinterJob pj = PrinterJob.getPrinterJob();
        pageFormat = pj.defaultPage();
    }

    private int printImage(Graphics g, PageFormat pf, int pageIndex) {
        if (pageIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        }

        int paperW = (int) pageFormat.getImageableWidth();
        int paperH = (int) pageFormat.getImageableHeight();
        int x = (int) pageFormat.getImageableX();
        int y = (int) pageFormat.getImageableY();

        // Make the image slightly smaller (25) than max possible width
        float scaleFactor = ((float) ((paperW - 25) - OBJECT_BORDER - OBJECT_BORDER)
                                   / (float) (image.getWidth()));

        BufferedImageOp scaleOp = new RescaleOp(scaleFactor, 0, null);

        Graphics2D g2D = (Graphics2D) g;
        g2D.transform(new AffineTransform(pageFormat.getMatrix()));
        g2D.setClip(x, y, paperW, paperH);
        g2D.drawImage(image, scaleOp, x + OBJECT_BORDER, y + OBJECT_BORDER);

        return Printable.PAGE_EXISTS;
    }

    private void print() throws PrinterException {
        final PrinterJob pj = PrinterJob.getPrinterJob();
        pj.setJobName("Print Image");
        pj.setPrintable(this::printImage);
        if (pj.printDialog()) {
            pj.print();
        } else {
            PassFailJFrame.forceFail("User cancelled printing");
        }
    }

    public static void main(String[] args) throws Exception {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        BufferedImage image = prepareFrontImage();

        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build();

        DrawImage pt = new DrawImage(image);
        pt.print();
        passFailJFrame.awaitAndCheck();
    }

    private static BufferedImage prepareFrontImage() {
        // build my own test images
        BufferedImage result = new BufferedImage(400, 200,
                                   BufferedImage.TYPE_BYTE_GRAY);
        int w = result.getWidth();
        int h = result.getHeight();

        Graphics2D g2D = (Graphics2D) result.getGraphics();
        g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_OFF);
        g2D.setColor(Color.gray);
        g2D.fill(new Rectangle(0, 0, w, h));
        g2D.setColor(Color.white);

        AffineTransform originXform = AffineTransform.getTranslateInstance(
                w / 5.0, h / 5.0);
        g2D.transform(originXform);
        g2D.drawString("Front Side", 20, h / 2);
        g2D.dispose();

        return result;
    }
}
