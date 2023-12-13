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
 * @bug JDK-8314070
 * @key printer
 * @requires (os.family == "mac")
 * @summary javax.print: Support IPP output-bin attribute extension
 * @run main/manual OutputBinAttributePrintDialogTest COMMON
 * @run main/manual OutputBinAttributePrintDialogTest NATIVE
 */

import javax.print.PrintService;
import javax.print.attribute.Attribute;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.DialogTypeSelection;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OutputBin;
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

public class OutputBinAttributePrintDialogTest {

    private static final long TIMEOUT = 10 * 60_000;
    private static volatile boolean testPassed = true;
    private static volatile boolean testFinished = false;
    private static volatile boolean timeout = false;

    private static volatile int testCount;
    private static volatile int testTotalCount;

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            throw new RuntimeException("COMMON or NATIVE print dialog type argument is not provided!");
        }

        final DialogTypeSelection dialogTypeSelection = getDialogTypeSelection(args[0]);

        OutputBin[] supportedOutputBins = getSupportedOutputBinttributes();
        if (supportedOutputBins.length < 1) {
            return;
        }

        // Test only the first and the last output bins to reduce number of tests
        final Set<OutputBin> outputBins = new HashSet<>();
        outputBins.add(supportedOutputBins[0]);
        outputBins.add(supportedOutputBins[supportedOutputBins.length - 1]);


        SwingUtilities.invokeLater(() -> {
            testTotalCount = outputBins.size();
            for (OutputBin outputBin : outputBins) {
                testPrint(dialogTypeSelection, outputBin, outputBins);
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

    private static void print(DialogTypeSelection dialogTypeSelection, OutputBin outputBin) throws PrinterException {
        PrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();
        attr.add(MediaSizeName.ISO_A4);
        attr.add(dialogTypeSelection);

        for (Attribute attribute : attr.toArray()) {
            System.out.printf("Used print request attribute: %s%n", attribute);
        }

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Print to " + outputBin + " output bin through " + dialogTypeSelection + " print dialog");
        job.setPrintable(new OutputBinAttributePrintable(outputBin));

        if (job.printDialog(attr)) {
            job.print();
        } else {
            throw new RuntimeException(dialogTypeSelection + " print dialog for " + outputBin + " is canceled!");
        }
    }

    private static class OutputBinAttributePrintable implements Printable {

        private final OutputBin outputBinAttr;

        public OutputBinAttributePrintable(OutputBin outputBinAttr) {
            this.outputBinAttr = outputBinAttr;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {

            if (pageIndex != 0) {
                return NO_SUCH_PAGE;
            }

            int x = (int) (pageFormat.getImageableX() + pageFormat.getImageableWidth() / 10);
            int y = (int) (pageFormat.getImageableY() + pageFormat.getImageableHeight() / 5);

            Graphics2D g = (Graphics2D) graphics;
            g.setColor(Color.BLACK);
            g.drawString(getPageText(outputBinAttr), x, y);
            return PAGE_EXISTS;
        }
    }

    private static String getPageText(OutputBin outputBin) {
        return String.format("Output bin: %s", outputBin);
    }

    private static OutputBin[] getSupportedOutputBinttributes() {

        PrinterJob printerJob = PrinterJob.getPrinterJob();
        PrintService service = printerJob.getPrintService();
        if (service == null) {
            return new OutputBin[0];
        }

        Object obj = service.getSupportedAttributeValues(OutputBin.class, null, null);

        if (obj instanceof Attribute[]) {
            return (OutputBin[]) obj;
        }

        return new OutputBin[0];
    }

    private static void pass() {
        testCount++;
    }

    private static void fail(OutputBin outputBin) {
        System.out.printf("Failed test: %s%n", getPageText(outputBin));
        testPassed = false;
    }

    private static void runPrint(DialogTypeSelection dialogTypeSelection, OutputBin outputBin) {
        try {
            print(dialogTypeSelection, outputBin);
        } catch (PrinterException e) {
            e.printStackTrace();
            fail(outputBin);
        }
    }

    private static void testPrint(DialogTypeSelection dialogTypeSelection, OutputBin outputBin, Set<OutputBin> supportedOutputBins) {

        System.out.printf("Test dialog: %s%n", dialogTypeSelection);

        String[] instructions = {
                "Up to " + testTotalCount + " tests will run and it will test all output bins:",
                supportedOutputBins.toString(),
                "supported by the printer.",
                "",
                "The test is " + (testCount + 1) + " from " + testTotalCount + ".",
                "",
                "On-screen inspection is not possible for this printing-specific",
                "test therefore its only output is a page printed to the printer",
                outputBin + " output bin.",
                "",
                "To be able to run this test it is required to have a default",
                "printer configured in your user environment.",
                "",
                " - Press 'Start Test' button.",
                "   The " + dialogTypeSelection + " print dialog should appear.",
                String.join("\n", getPrintDialogInstructions(dialogTypeSelection, outputBin)),
                "",
                "Visual inspection of the printed pages is needed.",
                "",
                "A passing test will print the page with the text: '" + getPageText(outputBin) + "'",
                "to the corresponding printer " + outputBin + " ouput bin.",
                "",
                "The test fails if the page is not printed in to the corresponding output bin.",
        };

        String title = String.format("Print %s dialog with %s output bin test: %d from %d",
                dialogTypeSelection, outputBin, testCount + 1, testTotalCount);
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
            fail(outputBin);
            dialog.dispose();
        });
        testButton.addActionListener((e) -> {
            testButton.setEnabled(false);
            runPrint(dialogTypeSelection, outputBin);
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
                fail(outputBin);
            }
        });
    }

    private static void closeDialogs() {
        for (Window w : Dialog.getWindows()) {
            w.dispose();
        }
    }

    private static DialogTypeSelection getDialogTypeSelection(String dialogTypeSelection) {
        switch (dialogTypeSelection) {
            case "COMMON":
                return DialogTypeSelection.COMMON;
            case "NATIVE":
                return DialogTypeSelection.NATIVE;
            default:
                throw new RuntimeException("Unknown dialog type selection: " + dialogTypeSelection);
        }
    }

    private static String[] getPrintDialogInstructions(DialogTypeSelection dialogTypeSelection, OutputBin outputBin) {
        if (dialogTypeSelection == DialogTypeSelection.COMMON) {
            return new String[]{
                    " - Select 'Page Setup' tab.",
                    " - Select '" + outputBin + "' output tray from 'Output trays' combo box.",
                    " - Pres 'Print' button."
            };
        } else if (dialogTypeSelection == DialogTypeSelection.NATIVE) {
            return new String[]{
                    " - Press 'Show Details' buttons if the details are hidded.",
                    " - Select 'Finishing Options' from the drop-down list.",
                    " - Select '" + outputBin + "' Output Bin.",
                    " - Press 'Print' button."
            };
        }
        throw new RuntimeException("Unknown dialog type selection: " + dialogTypeSelection);
    }
}
