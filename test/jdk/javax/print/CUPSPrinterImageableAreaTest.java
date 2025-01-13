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
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

/*
 * @test
 * @bug 8344119
 * @key printer
 * @requires (os.family == "linux" | os.family == "mac")
 * @summary CUPSPrinter imageable area
 * @run main/manual CUPSPrinterImageableAreaTest
 */

public class CUPSPrinterImageableAreaTest {

    private static PrintService printService;
    private static final long TIMEOUT = 5 * 60_000;
    private static volatile boolean testPassed = false;
    private static volatile boolean testFinished = false;
    private static volatile boolean intime = false;

    public static void main(String[] args) throws Exception {
        printService = PrintServiceLookup.lookupDefaultPrintService();
        if (printService == null) {
            throw new RuntimeException("Print service not found");
        }
        Media testMedia = (Media) printService.getDefaultAttributeValue(Media.class);
        if (testMedia == null) {
            Media[] medias = (Media[]) printService
                    .getSupportedAttributeValues(Media.class, null, null);
            if (medias == null || medias.length == 0) {
                throw new RuntimeException("Medias not found");
            }
            for (Media media: medias) {
                if (media == MediaSizeName.ISO_A4 || media == MediaSizeName.NA_LETTER) {
                    testMedia = media;
                    break;
                }
            }
        }
        if (!(testMedia instanceof MediaSizeName)) {
            throw new RuntimeException("Test media not found");
        }
        MediaSize ms = MediaSize.getMediaSizeForName((MediaSizeName)testMedia);
        if (ms == null) {
            throw new RuntimeException("Media size not defined");
        }
        MediaPrintableArea mpa = getMaximumMediaPrintableArea((MediaSizeName)testMedia, ms);
        if (mpa == null) {
            throw new RuntimeException("Media printable area not defined");
        }

        final PageFormat pageFormat = new PageFormat();
        pageFormat.setOrientation(PageFormat.PORTRAIT);
        Paper paper = new Paper();

        paper.setSize(ms.getX(MediaSize.INCH) * 72, ms.getY(MediaSize.INCH) * 72);
        paper.setImageableArea(mpa.getX(MediaPrintableArea.INCH) * 72,
                mpa.getY(MediaPrintableArea.INCH) * 72,
                mpa.getWidth(MediaPrintableArea.INCH) * 72,
                mpa.getHeight(MediaPrintableArea.INCH) * 72);
        pageFormat.setPaper(paper);

        // margins: left, bottom, right, top
        final float[] margins = new float[] {
                mpa.getX(MediaPrintableArea.MM),
                ms.getY(MediaSize.MM) - mpa.getY(MediaPrintableArea.MM) - mpa.getHeight(MediaPrintableArea.MM),
                ms.getX(MediaSize.MM) - mpa.getX(MediaPrintableArea.MM) - mpa.getWidth(MediaPrintableArea.MM),
                mpa.getY(MediaPrintableArea.MM),
        };

        final String testMediaSizeName = testMedia.toString();

        SwingUtilities.invokeLater(() -> {
            try {
                testPrint(pageFormat, testMediaSizeName, margins);
            } catch (PrinterException e) {
                fail();
            }
            testFinished = true;
        });

        long time = System.currentTimeMillis() + TIMEOUT;

        while (intime = (System.currentTimeMillis() < time)) {
            if (testFinished) {
                break;
            }
            Thread.sleep(500);
        }

        closeDialogs();

        if (!intime) {
            throw new Exception("Timeout");
        }

        if (!testPassed) {
            throw new Exception("Test failed!");
        }
    }

    private static void testPrint(PageFormat pageFormat, String mediaName, float[] margins) throws PrinterException {
        String[] instructions = {
                "This test checks that the media margins ",
                "fetched from the printer's PPD file are correct.",
                "The '" + mediaName + "' will be used for the test.",
                "Press the 'Start Test' button to print a test page.",
                "A passing test will print the page with ",
                "a black rectangle along the printable area.",
                "Ensure that all sides of the rectangle are printed.",
                String.format("Margin sizes: left %.1f mm., bottom %.1f mm., right %.1f mm., top %.1f mm.",
                        margins[0], margins[1], margins[2], margins[3])
        };

        String title = "Media margins test";
        final JDialog dialog = new JDialog((Frame) null, title, Dialog.ModalityType.DOCUMENT_MODAL);
        JTextArea textArea = new JTextArea(String.join("\n", instructions));
        textArea.setEditable(false);
        final JButton testButton = new JButton("Start Test");
        final JButton passButton = new JButton("PASS");
        passButton.setEnabled(false);
        passButton.addActionListener((e) -> {
            pass();
            dialog.dispose();
        });
        final JButton failButton = new JButton("FAIL");
        failButton.setEnabled(false);
        failButton.addActionListener((e) -> {
            fail();
            dialog.dispose();
        });
        testButton.addActionListener((e) -> {
            testButton.setEnabled(false);
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintable(new RectPrintable(), pageFormat);
            try {
                job.print();
            } catch (PrinterException ex) {
                fail();
            }
            passButton.setEnabled(true);
            failButton.setEnabled(true);
        });

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(textArea, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(testButton);
        buttonPanel.add(passButton);
        buttonPanel.add(failButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        dialog.add(mainPanel);
        dialog.pack();
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                fail();
            }
        });
        dialog.setVisible(true);
    }

    private static void closeDialogs() {
        for (Window w : Dialog.getWindows()) {
            w.dispose();
        }
    }

    private static void pass() {
        testPassed = true;
    }

    private static void fail() {
        testPassed = false;
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
