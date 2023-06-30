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

public class SidesAttributeTest {

    private static final long TIMEOUT = 10 * 60_000;
    private static volatile boolean testPassed = true;
    private static volatile boolean testFinished = false;
    private static volatile boolean timeout = false;

    private static volatile int testCount;

    public static void main(String[] args) throws Exception {

        SwingUtilities.invokeLater(() -> {
            testPrint(Sides.ONE_SIDED);
            testPrint(Sides.DUPLEX);
            testPrint(Sides.TUMBLE);
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

        if (testCount < 3) {
            throw new Exception("Timeout: less than 3 tests were running!");
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
        return String.format("Page: %d - %s", page, getSidesDescription(sides));
    }

    private static String getSidesDescription(Sides sides) {
        if (Sides.ONE_SIDED.equals(sides)) {
            return "ONE_SIDED";
        } else if (Sides.TWO_SIDED_SHORT_EDGE.equals(sides)) {
            return "TWO_SIDED_SHORT_EDGE (TUMBLE)";
        } else if (Sides.TWO_SIDED_LONG_EDGE.equals(sides)) {
            return "TWO_SIDED_LONG_EDGE (DUPLEX)";
        }
        throw new RuntimeException("Unknown sides attribute: " + sides);
    }

    private static void pass() {
        testCount++;
    }

    private static void fail() {
        testPassed = false;
    }

    private static void runPrint(Sides sides) {
        try {
            print(sides);
        } catch (PrinterException e) {
            fail();
            e.printStackTrace();
        }
    }

    private static void testPrint(Sides sides) {

        if (!testPassed || timeout) {
            return;
        }

        String[] instructions = {
                "On-screen inspection is not possible for this printing-specific",
                "test therefore its only output is two printed pages (one or two sided).",
                "To be able to run this test it is required to have a default",
                "printer configured in your user environment.",
                "",
                "Visual inspection of the printed pages is needed.",
                "A passing test will print 2 pages:",
                "  - the first page with the text: " + getPageText(sides, 1),
                "  - the second page with the text: " + getPageText(sides, 2),
                "The test fails only if the text on the pages is not printed",
                "or pages are not printed according to the sides attribute",
                getSidesDescription(sides)
        };

        String title = "Print sides test: " + getSidesDescription(sides);
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
                fail();
            }
        });
    }

    private static void closeDialogs() {
        for (Window w : Dialog.getWindows()) {
            w.dispose();
        }
    }
}
