/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, BELLSOFT. All rights reserved.
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

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.Size2DSyntax;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.swing.JButton;
import javax.swing.JFrame;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.List;

/*
 * @test
 * @bug 8344119
 * @key printer
 * @requires (os.family == "linux" | os.family == "mac")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary CUPSPrinter imageable area
 * @run main/manual CUPSPrinterImageableAreaTest
 */

public class CUPSPrinterImageableAreaTest {

    private static PrintService printService;
    private static final List<MediaSizeName> ALLOWED_MEDIA_LIST = List.of(MediaSizeName.ISO_A4, MediaSizeName.NA_LETTER);
    private static final double DPI = 72.0;
    private static final double MM_PER_INCH = 2.54;
    private static final String INSTRUCTIONS_FORMAT = """
            <html>
                <h3>The <b>'%s'</b> paper size should be used for this test.</h3>
                <div>
                The test checks that the media margins fetched from the printer's PPD file are correct.<br>
                Press the '<b>Print sample</b>' button to print a test page.<br>
                A passing test will print the page with a black rectangle along the printable area.
                Ensure that all sides of the rectangle are printed.<br>
                Expected margins: 
                <ul>
                    <li>left: %.1f mm.</li>
                    <li>bottom: %.1f mm.</li>
                    <li>right: %.1f mm.</li>
                    <li>top: %.1f mm.</li>
                </ul>
                Click '<b>Pass</b>' button, or click '<b>Fail</b>' button if the test failed.
                </div>
            <html>
            """;


    public static void main(String[] args) throws Exception {
        printService = PrintServiceLookup.lookupDefaultPrintService();
        if (printService == null) {
            throw new RuntimeException("Print service not found");
        }
        MediaSizeName testMedia = getTestMediaSizeName();
        PageFormat pageFormat = createTestPageFormat(testMedia);

        // margins: left, bottom, right, top
        double[] margins = new double[] {
                pageFormat.getImageableX(),
                pageFormat.getHeight() - pageFormat.getImageableHeight() - pageFormat.getImageableY(),
                pageFormat.getWidth() - pageFormat.getImageableWidth() - pageFormat.getImageableX(),
                pageFormat.getImageableY()
        };

        final String instructions = INSTRUCTIONS_FORMAT.formatted(testMedia.toString(),
                inchesToMM(margins[0]), inchesToMM(margins[1]), inchesToMM(margins[2]), inchesToMM(margins[3]));

        PassFailJFrame.builder()
                .instructions(instructions)
                .testUI(createTestUI(pageFormat))
                .columns(55)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI(PageFormat pageFormat) {
        final JFrame f = new JFrame("CUPS Printer imageable area test");
        JButton printButton = new JButton("Print sample");
        printButton.setPreferredSize(new Dimension(200, 80));
        printButton.addActionListener((e) -> {
            printButton.setEnabled(false);
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintable(new RectPrintable(), pageFormat);
            try {
                job.print();
            } catch (PrinterException ex) {
                throw new RuntimeException(ex);
            }
        });
        f.add(printButton);
        f.pack();
        return f;
    }

    private static MediaSizeName getTestMediaSizeName() {
        //Use printer's default media or one of the alloed medias
        Media testMedia = (Media) printService.getDefaultAttributeValue(Media.class);
        if (testMedia == null) {
            Media[] medias = (Media[]) printService
                    .getSupportedAttributeValues(Media.class, null, null);
            if (medias == null || medias.length == 0) {
                throw new RuntimeException("Medias not found");
            }
            for (Media media: medias) {
                if (ALLOWED_MEDIA_LIST.contains(media)) {
                    testMedia = media;
                    break;
                }
            }
        }
        if (!(testMedia instanceof MediaSizeName)) {
            throw new RuntimeException("Test media not found");
        }
        return (MediaSizeName)testMedia;
    }

    private static PageFormat createTestPageFormat(MediaSizeName testMedia) {
        MediaSize ms = MediaSize.getMediaSizeForName(testMedia);
        if (ms == null) {
            throw new RuntimeException("Media size not defined");
        }
        MediaPrintableArea mpa = getMaximumMediaPrintableArea(testMedia, ms);
        if (mpa == null) {
            throw new RuntimeException("Media printable area not defined");
        }

        PageFormat pageFormat = new PageFormat();
        pageFormat.setOrientation(PageFormat.PORTRAIT);
        Paper paper = new Paper();
        paper.setSize(ms.getX(MediaSize.INCH) * DPI, ms.getY(MediaSize.INCH) * DPI);
        paper.setImageableArea(mpa.getX(MediaPrintableArea.INCH) * DPI,
                mpa.getY(MediaPrintableArea.INCH) * DPI,
                mpa.getWidth(MediaPrintableArea.INCH) * DPI,
                mpa.getHeight(MediaPrintableArea.INCH) * DPI);
        pageFormat.setPaper(paper);
        return pageFormat;
    }

    private static MediaPrintableArea getMaximumMediaPrintableArea(MediaSizeName msn, MediaSize ms) {
        final float paperSize = ms.getX(Size2DSyntax.MM) * ms.getY(Size2DSyntax.MM);
        final float sizeDev = 0.2f * 0.2f;
        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
        attrs.add(msn);
        MediaPrintableArea[] mpas = (MediaPrintableArea[]) printService
                .getSupportedAttributeValues(MediaPrintableArea.class, null, attrs);
        if (mpas == null || mpas.length == 0) {
            throw new RuntimeException("Printable area not found");
        }

        MediaPrintableArea mpa = null;
        for (MediaPrintableArea area: mpas) {
            float mpaSize = area.getWidth(MediaPrintableArea.MM) * area.getHeight(MediaPrintableArea.MM);
            //do not use borderless printable area
            if (sizeDev >= Math.abs(paperSize - mpaSize)) {
                continue;
            }
            if (mpa == null) {
                mpa = area;
            } else if (mpaSize > (area.getWidth(MediaPrintableArea.MM) * area.getHeight(MediaPrintableArea.MM))) {
                mpa = area;
            }
        }
        return mpa;
    }

    private static double inchesToMM(double inches) {
        return inches / MM_PER_INCH;
    }

    private static class RectPrintable implements Printable {

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
            if (pageIndex == 0) {
                Graphics2D g = (Graphics2D) graphics;
                g.setStroke(new BasicStroke(3));
                g.drawRect((int)pageFormat.getImageableX(), (int)pageFormat.getImageableY(),
                        (int)pageFormat.getImageableWidth(), (int)pageFormat.getImageableHeight());
                return PAGE_EXISTS;
            }
            return NO_SUCH_PAGE;
        }
    }

}
