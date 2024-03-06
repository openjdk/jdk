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

/*
 * @test
 * @bug 8315113
 * @key printer
 * @summary javax.print: Print request Chromaticity.MONOCHROME attribute does not work on macOS
 * @requires (os.family == "mac")
 * @run main/manual ChromaticityAttributeTest
 */

import javax.print.PrintService;
import javax.print.attribute.Attribute;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Chromaticity;
import javax.print.attribute.standard.MediaSizeName;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ChromaticityAttributeTest {

    private static final long TIMEOUT = 10 * 60_000;
    private static volatile boolean testPassed = true;
    private static volatile boolean testFinished = false;
    private static volatile boolean timeout = false;

    private static volatile int testCount;
    private static volatile int testTotalCount;

    private static final Color TEXT_COLOR = Color.BLUE;
    private static final Color SHAPE_COLOR = Color.ORANGE;

    public static void main(String[] args) throws Exception {

        final Set<Chromaticity> chromaticities = Set.of(Chromaticity.MONOCHROME, Chromaticity.COLOR);

        SwingUtilities.invokeLater(() -> {
            testTotalCount = chromaticities.size();
            for (Chromaticity chromaticity : chromaticities) {
                testPrint(chromaticity, chromaticities);
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

        timeout = true;

        closeDialogs();

        if (!testPassed) {
            throw new Exception("Test failed!");
        }

        if (testCount != testTotalCount) {
            throw new Exception(
                    "Timeout: " + testCount + " tests passed out from " + testTotalCount);
        }
    }

    private static void print(Chromaticity chromaticity) throws PrinterException {
        PrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();
        attr.add(MediaSizeName.ISO_A4);
        attr.add(chromaticity);

        for (Attribute attribute : attr.toArray()) {
            System.out.printf("Used print request attribute: %s%n", attribute);
        }

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Test" + chromaticity + " chromaticity");
        job.setPrintable(new ChromaticityAttributePrintable(getPageText(chromaticity)));

        job.print(attr);
    }

    private static class ChromaticityAttributePrintable implements Printable {

        private final String text;

        public ChromaticityAttributePrintable(String text) {
            this.text = text;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {

            if (pageIndex != 0) {
                return NO_SUCH_PAGE;
            }

            int w = (int) pageFormat.getImageableWidth();
            int h = (int) pageFormat.getImageableHeight();
            int x = (int) (pageFormat.getImageableX() + w / 10);
            int y = (int) (pageFormat.getImageableY() + h / 5);

            int r = w / 4;

            Graphics2D g = (Graphics2D) graphics;
            g.setColor(SHAPE_COLOR);
            g.fillOval(x, y - r, 2 * r, 2 * r);

            g.setColor(TEXT_COLOR);
            g.setFont(g.getFont().deriveFont(18.0f));
            g.drawString(text, x, y);
            return PAGE_EXISTS;
        }
    }


    private static boolean isColor(Chromaticity chromaticity) {
        if (Chromaticity.MONOCHROME.equals(chromaticity)) {
            return false;
        } else if (Chromaticity.COLOR.equals(chromaticity)) {
            return true;
        }
        throw new RuntimeException("Unsupported chromaticity: " + chromaticity);
    }

    private static String getPageColor(Chromaticity chromaticity) {
        return isColor(chromaticity) ? "Color" : "Black & White";
    }

    private static String getPageText(Chromaticity chromaticity) {
        return String.format("%s page (test %s chromaticity)", getPageColor(chromaticity), chromaticity);
    }

    private static Set<Chromaticity> getSupportedChromaticityAttributes() {
        Set<Chromaticity> attributes = new HashSet<>();

        PrintService service = PrinterJob.getPrinterJob().getPrintService();
        if (service == null) {
            return attributes;
        }

        Object obj = service.getSupportedAttributeValues(Chromaticity.class, null, null);

        if (obj instanceof Attribute[]) {
            Chromaticity[] attrs = (Chromaticity[]) obj;
            Collections.addAll(attributes, attrs);
        }

        return attributes;
    }

    private static void pass() {
        testCount++;
    }

    private static void fail(Chromaticity chromaticity) {
        System.out.printf("Failed test: %s%n", getPageText(chromaticity));
        testPassed = false;
    }

    private static void runPrint(Chromaticity chromaticity) {
        try {
            print(chromaticity);
        } catch (PrinterException e) {
            e.printStackTrace();
            fail(chromaticity);
        }
    }

    private static void testPrint(Chromaticity chromaticity, Set<Chromaticity> supportedChromaticities) {

        boolean isColor = isColor(chromaticity);
        String pageColor = getPageColor(chromaticity);
        String pageText = getPageText(chromaticity);

        String[] instructions = {
                "Up to " + testTotalCount + " tests will run and it will test all chromaticities",
                supportedChromaticities.toString() + " supported by the printer.",
                "",
                "The test is " + (testCount + 1) + " from " + testTotalCount + ".",
                "",
                "On-screen inspection is not possible for this printing-specific test",
                "therefore its only output is a " + pageColor + " page printed to the printer",
                "with " + chromaticity + " chromaticity.",
                "To be able to run this test it is required to have a default",
                "printer configured in your user environment.",
                "",
                "Visual inspection of the printed pages is needed.",
                "",
                "A passing test will print the " + pageColor + " page with",
                isColor
                        ? "a blue '" + pageText + "' text and an orange circle."
                        : "a black '" + pageText + "' text and a circle.",
                "The test fails if the page is not printed as " + pageColor + ".",
        };

        String title = String.format("Print %s page test: %d from %d",
                pageColor, testCount + 1, testTotalCount);
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
            fail(chromaticity);
            dialog.dispose();
        });
        testButton.addActionListener((e) -> {
            testButton.setEnabled(false);
            runPrint(chromaticity);
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

    private static void closeDialogs() {
        for (Window w : Dialog.getWindows()) {
            w.dispose();
        }
    }
}

