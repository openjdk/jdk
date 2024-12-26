/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, BELLSOFT. All rights reserved.
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
import javax.print.attribute.Attribute;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Chromaticity;
import javax.print.attribute.standard.MediaSizeName;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.Arrays;

/*
 * @test
 * @bug 8315113
 * @key printer
 * @requires (os.family == "linux" | os.family == "mac")
 * @summary javax.print: Support monochrome printing
 */

public class MonochromePrintTest {

    private static final long TIMEOUT = 10 * 60_000;
    private static volatile boolean testPassed = true;
    private static volatile boolean testSkipped = false;
    private static volatile boolean testFinished = false;

    private static volatile int testCount;
    private static volatile int testTotalCount;

    public static void main(String[] args) throws Exception {

        final Chromaticity[] supportedChromaticity = getSupportedChromaticity();
        if (supportedChromaticity == null || supportedChromaticity.length < 2) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            testTotalCount = supportedChromaticity.length;
            for (Chromaticity chromaticity : supportedChromaticity) {
                if (testSkipped) {
                    break;
                }
                testPrint(chromaticity, supportedChromaticity);
            }
            testFinished = true;
        });

        long time = System.currentTimeMillis() + TIMEOUT;

        while (System.currentTimeMillis() < time) {
            if (!testPassed || testFinished) {
                break;
            }
            Thread.sleep(500);
        }

        closeDialogs();

        if (testSkipped) {
            System.out.printf("Test is skipped!%n");
            return;
        }

        if (!testPassed) {
            throw new Exception("Test failed!");
        }

        if (testCount != testTotalCount) {
            throw new Exception(
                    "Timeout: " + testCount + " tests passed out from " + testTotalCount);
        }
    }

    private static void testPrint(Chromaticity chromaticity, Chromaticity[] supportedChromaticity) {

        String[] instructions = {
                "Two tests will run and it will test all available color apearances:",
                Arrays.toString(supportedChromaticity) + "supported by the printer.",
                "",
                "The test is " + (testCount + 1) + " from " + testTotalCount + ".",
                "",
                "On-screen inspection is not possible for this printing-specific",
                "test therefore its only output is a page printed to the printer",
                "",
                "To be able to run this test it is required to have a default",
                "printer configured in your user environment.",
                "",
                " - Press 'Start Test' button.",
                "   The print dialog should appear.",
                " - Select 'Appearance' tab.",
                " - Select '" + chromaticity + "' on the 'Color apearance' panel.",
                " - Press 'Print' button.",
                "",
                "Visual inspection of the printed pages is needed.",
                "",
                "A passing test will print the page with color appearance '" + chromaticity + "'.",
                "The text, shapes and image should be " + chromaticity + ".",
                "The test fails if the page is not printed with required color apearance.",
        };

        String title = String.format("Print with %s chromacity test: %d from %d", chromaticity, testCount + 1, testTotalCount);
        final JDialog dialog = new JDialog(null, title, Dialog.ModalityType.DOCUMENT_MODAL);
        JTextArea textArea = new JTextArea(String.join("\n", instructions));
        textArea.setEditable(false);
        final JButton testButton = new JButton("Start Test");
        final JButton skipButton = new JButton("Skip Test");
        final JButton passButton = new JButton("PASS");
        skipButton.setEnabled(false);
        passButton.setEnabled(false);
        passButton.addActionListener((e) -> {
            pass();
            dialog.dispose();
        });
        skipButton.addActionListener((e) -> {
            skip();
            dialog.dispose();
        });
        final JButton failButton = new JButton("FAIL");
        failButton.setEnabled(false);
        failButton.addActionListener((e) -> {
            fail(chromaticity);
            dialog.dispose();
        });
        testButton.addActionListener((e) -> {
            testButton.setEnabled(false);
            runPrint(chromaticity);
            skipButton.setEnabled(true);
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
        dialog.setVisible(true);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("Dialog closing");
                fail(chromaticity);
            }
        });
    }

    private static void print(Chromaticity chromaticity) throws PrinterException {
        PrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();
        attr.add(MediaSizeName.ISO_A4);
        attr.add(chromaticity);

        for (Attribute attribute : attr.toArray()) {
            System.out.printf("Used print request attribute: %s%n", attribute);
        }

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Print with " + chromaticity);
        job.setPrintable(new ChromacityAttributePrintable(chromaticity));

        if (job.printDialog(attr)) {
            job.print();
        } else {
            throw new RuntimeException("Test for " + chromaticity + " is canceled!");
        }
    }

    private static void closeDialogs() {
        for (Window w : Dialog.getWindows()) {
            w.dispose();
        }
    }

    private static void pass() {
        testCount++;
    }

    private static void skip() {
        testSkipped = true;
    }

    private static void fail(Chromaticity chromacity) {
        System.out.printf("Failed test: %s", chromacity.toString());
        testPassed = false;
    }

    private static Chromaticity[] getSupportedChromaticity() {

        PrinterJob printerJob = PrinterJob.getPrinterJob();
        PrintService service = printerJob.getPrintService();
        if (service == null) {
            System.out.printf("No print service found.");
            return null;
        }

        if (!service.isAttributeCategorySupported(Chromaticity.class)) {
            System.out.printf("Skipping the test as Chromaticity category is not supported for this printer.");
            return null;
        }

        Object obj = service.getSupportedAttributeValues(Chromaticity.class, null, null);

        if (obj instanceof Chromaticity[]) {
            return (Chromaticity[]) obj;
        }

        throw new RuntimeException("Chromaticity category is supported but no supported attribute values are returned.");
    }

    private static void runPrint(Chromaticity chromaticity) {
        try {
            print(chromaticity);
        } catch (PrinterException e) {
            e.printStackTrace();
            fail(chromaticity);
            closeDialogs();
        }
    }


    private static class ChromacityAttributePrintable implements Printable {

        private final Chromaticity chromacity;

        public ChromacityAttributePrintable(Chromaticity chromacity) {
            this.chromacity = chromacity;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {

            if (pageIndex != 0) {
                return NO_SUCH_PAGE;
            }

            final int sx = (int) Math.ceil(pageFormat.getImageableX());
            final int sy = (int) Math.ceil(pageFormat.getImageableY());

            Graphics2D g = (Graphics2D) graphics;

            BufferedImage bufferdImage = getBufferedImage((int) Math.ceil(pageFormat.getImageableWidth() / 3), (int) Math.ceil(pageFormat.getImageableHeight() / 7));
            g.drawImage(bufferdImage, null, sx, sy);

            int imh = sy + (int) Math.ceil(pageFormat.getImageableHeight() / 7) + 10;

            g.setColor(Color.ORANGE);
            g.drawRect(sx, imh, 50, 50);
            imh += 60;

            g.setColor(Color.BLUE);
            g.fillOval(sx, imh, 50, 50);
            imh += 60;

            Paint paint = new LinearGradientPaint(0, 0, 20, 5, new float[]{0.0f, 0.2f, 1.0f},
                    new Color[]{Color.RED, Color.GREEN, Color.CYAN}, MultipleGradientPaint.CycleMethod.REPEAT);
            g.setPaint(paint);
            g.setStroke(new BasicStroke(50));
            g.fillRect(sx, imh + 10, 50, 50);
            imh += 60;

            paint = new RadialGradientPaint(10, 10, 5, new float[]{0.0f, 0.5f, 1.0f},
                    new Color[]{Color.RED, Color.GREEN, Color.CYAN}, MultipleGradientPaint.CycleMethod.REPEAT);
            g.setPaint(paint);
            g.fillRect(sx, imh + 10, 50, 50);
            imh += 60;

            g.setStroke(new BasicStroke(5));
            g.setColor(Color.PINK);
            g.drawString("Chromacity: " + chromacity, sx, imh + 30);

            return PAGE_EXISTS;
        }

        private BufferedImage getBufferedImage(int width, int height) {
            Color[] colors = new Color[]{
                    Color.RED, Color.ORANGE, Color.BLUE,
                    Color.CYAN, Color.MAGENTA, Color.GREEN
            };
            BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            final int secondSquareOffset = width / 3;
            final int thirdSquareOffset = secondSquareOffset * 2;
            final int squareHeight = height / 2;

            int offset = 0;
            Color color;
            for (int y = 0; y < height; y++) {
                if (y > squareHeight) {
                    offset = 3;
                }
                for (int x = 0; x < width; x++) {
                    if (x >= thirdSquareOffset) {
                        color = colors[offset + 2];
                    } else if (x >= secondSquareOffset) {
                        color = colors[offset + 1];
                    } else {
                        color = colors[offset];
                    }
                    bufferedImage.setRGB(x, y, color.getRGB());
                }
            }
            return bufferedImage;
        }
    }

}