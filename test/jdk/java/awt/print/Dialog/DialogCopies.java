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

/**
 * @test
 * @bug 6357858
 * @summary Job must reports the number of copies set in the dialog.
 * @run main/manual DialogCopies
 */

import java.awt.Frame;
import java.awt.TextArea;
import java.awt.Panel;
import java.awt.BorderLayout;
import java.awt.print.PrinterJob;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DialogCopies {

    private static final Frame instructionFrame = new Frame();
    private static volatile boolean testResult;
    private static volatile CountDownLatch countDownLatch;

    private static void createInstructionUI() {

        final String instruction = """
                This test assumes and requires that you have a printer installed
                When the dialog appears, increment the number of copies then
                press OK/Print.The test will throw an exception if you fail
                to do this, since,it cannot distinguish that from a failure.""";

        Panel mainControlPanel = new Panel(new BorderLayout());
        TextArea instructionTextArea = new TextArea();
        instructionTextArea.setText(instruction);
        instructionTextArea.setEditable(false);
        mainControlPanel.add(instructionTextArea, BorderLayout.CENTER);
        instructionFrame.add(mainControlPanel);
        instructionFrame.pack();
        instructionFrame.setVisible(true);
    }

    public static void showPrintDialog() {
        PrinterJob job = PrinterJob.getPrinterJob();
        if (job.getPrintService() == null) {
            System.out.println("Looks like printer is not configured. Please install printer and " +
                    "re-run the test case.");
            testResult = false;
            countDownLatch.countDown();
            return;
        }
        checkNoOfCopies(job, job.printDialog());
    }

    public static void checkNoOfCopies(PrinterJob job, boolean pdReturnValue) {
        if (pdReturnValue) {
            System.out.println("User has selected OK/Print button on the PrintDialog");
        } else {
            System.out.println("User has selected Cancel button on the PrintDialog");
        }
        int copies = job.getCopies();
        if (copies <= 1) {
            testResult = false;
            System.out.println("Expected the number of copies to be more than 1 but got " + copies);
        } else {
            testResult = true;
            System.out.println("Total number of copies : " + copies);
        }
        countDownLatch.countDown();
        instructionFrame.dispose();
    }

    public static void main(String[] args) throws InterruptedException {
        countDownLatch = new CountDownLatch(1);
        createInstructionUI();
        showPrintDialog();
        if (!countDownLatch.await(5, TimeUnit.MINUTES)) {
            throw new RuntimeException("Timeout : No action was performed on the test UI.");
        }
        if (!testResult) {
            throw new RuntimeException("Test failed!");
        }
    }
}

