/*
 * Copyright (c) 2007, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6357858
 * @key printer
 * @summary Job must reports the number of copies set in the dialog.
 * @run main/manual DialogCopies
 */

import java.awt.Frame;
import java.awt.TextArea;
import java.awt.BorderLayout;
import java.awt.print.PrinterJob;

public class DialogCopies {

    private static Frame createInstructionUI() {
        final String instruction = """
                This test requires that you have a printer.

                Press Cancel if your system has only virtual printers such as
                Microsoft Print to PDF or Microsoft XPS Document Writer since
                they don't allow setting copies to anything but 1.

                If a real printer is installed, select it from the drop-down
                list in the Print dialog and increase the number of copies,
                then press OK button.""";

        TextArea instructionTextArea = new TextArea(instruction);
        instructionTextArea.setEditable(false);

        Frame instructionFrame = new Frame();
        instructionFrame.add(instructionTextArea, BorderLayout.CENTER);
        instructionFrame.pack();
        instructionFrame.setLocationRelativeTo(null);
        instructionFrame.setVisible(true);
        return instructionFrame;
    }

    public static void showPrintDialog() {
        PrinterJob job = PrinterJob.getPrinterJob();
        if (job.getPrintService() == null) {
            System.out.println("Looks like printer is not configured. Please install printer " +
                    " and re-run the test case.");
            return;
        }
        checkNoOfCopies(job, job.printDialog());
    }

    public static void checkNoOfCopies(PrinterJob job, boolean pdReturnValue) {
        if (pdReturnValue) {
            System.out.println("User has selected OK/Print button on the PrintDialog");
            int copies = job.getCopies();
            if (copies <= 1) {
                throw new RuntimeException("Expected the number of copies to be more than 1 but got " + copies);
            } else {
                System.out.println("Total number of copies : " + copies);
            }
        } else {
            System.out.println("User has selected Cancel button on the PrintDialog.");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Frame frame = createInstructionUI();
        try {
            showPrintDialog();
        } finally {
            frame.dispose();
        }
    }
}
