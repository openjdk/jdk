/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, BELLSOFT. All rights reserved.
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
 * @bug JDK-8311033
 * @summary [macos] PrinterJob does not take into account Sides attribute
 * @run main/manual SidesAttributeTest
 */

import javax.print.PrintService;
import javax.print.attribute.Attribute;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Sides;
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

public class SidesAttributeTest {

    private static final long TIMEOUT = 10 * 60_000;
    private static volatile boolean testPassed = true;
    private static volatile boolean testFinished = false;
    private static volatile boolean timeout = false;

    private static volatile int testCount;
    private static volatile int testTotalCount;

    public static void main(String[] args) throws Exception {

        SwingUtilities.invokeLater(() -> {

            Set<Attribute> supportedSides = getSupportedSidesAttributes();
            if (supportedSides.size() > 1) {
                testTotalCount = supportedSides.size();
                testPrint(Sides.ONE_SIDED, supportedSides);
                testPrint(Sides.DUPLEX, supportedSides);
                testPrint(Sides.TUMBLE, supportedSides);
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

    private static void print(Sides sides) throws PrinterException {
        PrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();
        attr.add(sides);

        for (Attribute attribute : attr.toArray()) {
            System.out.printf("Used print request attribute: %s%n", attribute);
        }

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable(new SidesAttributePrintable(sides));

        job.print(attr);
    }

    private static class SidesAttributePrintable implements Printable {

        private final Sides sidesAttr;

        public SidesAttributePrintable(Sides sidesAttr) {
            this.sidesAttr = sidesAttr;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {

            if (pageIndex >= 2) {
                return NO_SUCH_PAGE;
            }

            int x = (int) (pageFormat.getImageableX() + pageFormat.getImageableWidth() / 10);
            int y = (int) (pageFormat.getImageableY() + pageFormat.getImageableHeight() / 5);

            Graphics2D g = (Graphics2D) graphics;
            String text = getPageText(sidesAttr, pageIndex + 1);
            g.drawString(text, x, y);
            return PAGE_EXISTS;
        }
    }

    private static String getPageText(Sides sides, int page) {
        return String.format("Page: %d - %s", page, getSidesText(sides));
    }

    private static String getSidesText(Sides sides) {
        if (Sides.ONE_SIDED.equals(sides)) {
            return "ONE_SIDED";
        } else if (Sides.TWO_SIDED_SHORT_EDGE.equals(sides)) {
            return "TWO_SIDED_SHORT_EDGE (TUMBLE)";
        } else if (Sides.TWO_SIDED_LONG_EDGE.equals(sides)) {
            return "TWO_SIDED_LONG_EDGE (DUPLEX)";
        }
        throw new RuntimeException("Unknown sides attribute: " + sides);
    }

    private static String getSidesDescription(Sides sides) {
        if (Sides.ONE_SIDED.equals(sides)) {
            return "a one-sided document";
        } else if (Sides.TWO_SIDED_SHORT_EDGE.equals(sides)) {
            return "double-sided document along the short edge of the paper";
        } else if (Sides.TWO_SIDED_LONG_EDGE.equals(sides)) {
            return "double-sided document along the long edge of the paper";
        }
        throw new RuntimeException("Unknown sides attribute: " + sides);
    }

    private static Set<Attribute> getSupportedSidesAttributes() {
        Set<Attribute> supportedSides = new HashSet<>();

        PrinterJob printerJob = PrinterJob.getPrinterJob();
        PrintService service = printerJob.getPrintService();

        Object obj = service.getSupportedAttributeValues(Sides.class, null, null);
        if (obj instanceof Attribute[]) {
            Attribute[] attr = (Attribute[]) obj;
            Collections.addAll(supportedSides, attr);
        }

        return supportedSides;
    }

    private static void pass() {
        testCount++;
    }

    private static void fail(Sides sides) {
        System.out.printf("Failed test: %s%n", getSidesText(sides));
        testPassed = false;
    }

    private static void runPrint(Sides sides) {
        try {
            print(sides);
        } catch (PrinterException e) {
            fail(sides);
            e.printStackTrace();
        }
    }

    private static void testPrint(Sides sides, Set<Attribute> supportedSides) {

        if (!supportedSides.contains(sides) || !testPassed || timeout) {
            return;
        }

        String[] instructions = {
                "Up to " + testTotalCount + " tests will run and it will test all the cases",
                "supported by the printer.",
                "",
                "The test is " + (testCount + 1) + " from " + testTotalCount + ".",
                "",
                "On-screen inspection is not possible for this printing-specific",
                "test therefore its only output is two printed pages (one or two sided).",
                "To be able to run this test it is required to have a default",
                "printer configured in your user environment.",
                "",
                "Visual inspection of the printed pages is needed.",
                "A passing test will print 2 pages:",
                "  - the first page with the text: " + getPageText(sides, 1),
                "  - the second page with the text: " + getPageText(sides, 2),
                "",
                "The test fails if the pages are not printed according to the tested",
                getSidesText(sides) + " attribute where " + getSidesDescription(sides),
                "needs to be printed.",
                "",
        };

        String title = String.format("Print %s sides test: %d from %d",
                getSidesText(sides), testCount + 1, testTotalCount);
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
            fail(sides);
            dialog.dispose();
        });
        testButton.addActionListener((e) -> {
            testButton.setEnabled(false);
            runPrint(sides);
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
                fail(sides);
            }
        });
    }

    private static void closeDialogs() {
        for (Window w : Dialog.getWindows()) {
            w.dispose();
        }
    }
}
