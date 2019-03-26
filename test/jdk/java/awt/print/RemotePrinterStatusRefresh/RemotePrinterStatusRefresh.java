/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8153732 8212202
 * @requires (os.family == "Windows")
 * @summary Windows remote printer changes do not reflect in lookupPrintServices()
 * @ignore Requires a new network printer installation\removal
 * @run main/manual RemotePrinterStatusRefresh
 */

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.print.PrinterJob;
import javax.print.PrintService;

public class RemotePrinterStatusRefresh
{
    private static TestUI test = null;
    public static void main(String args[]) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        // Test UI creation
        test = new TestUI(latch);

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    test.createUI();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        // RemotePrinterStatusRefresh creation
        RemotePrinterStatusRefresh RemotePrinterStatusRefresh = new RemotePrinterStatusRefresh();
        SwingUtilities.invokeAndWait(() -> {
            collectPrintersList(test.resultsTextArea, true);
        });

        // 8 min = 480000 msec
        if(waitForFlag(480000)) {
            SwingUtilities.invokeAndWait(() -> {
                collectPrintersList(test.resultsTextArea, false);
            });
        } else {
            dispose();
            throw new RuntimeException("No new network printer got added/removed!! Test timed out!!");
        }

        boolean status = latch.await(1, TimeUnit.MINUTES);
        if (!status) {
            dispose();
            throw new RuntimeException("Test timed out.");
        }

        if (test.testResult == false) {
            dispose();
            throw new RuntimeException("Test Failed.");
        }

        dispose();
    }

    public static void dispose() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            test.disposeUI();
        });
    }

    public static boolean waitForFlag (long maxTimeoutInMsec) throws Exception {
        while(!test.isAdded && maxTimeoutInMsec > 0) {
            maxTimeoutInMsec -= 100;
            Thread.sleep(100);
        }

        if(maxTimeoutInMsec <= 0) {
            return false;
        } else {
            return true;
        }
    }

    private static void collectPrintersList(JTextArea textArea, boolean before) {
        if(before) {
            System.out.println("List of printers(before): ");
            textArea.setText("List of printers(before): \n");
            for (PrintService printServiceBefore : PrinterJob.lookupPrintServices()) {
                System.out.println(printServiceBefore);
                textArea.append(printServiceBefore.toString());
                textArea.append("\n");
            }
        } else {
            textArea.append("\n");
            System.out.println("List of printers(after): ");
            textArea.append("List of printers(after): \n");
            for (PrintService printServiceAfter : PrinterJob.lookupPrintServices()) {
                System.out.println(printServiceAfter);
                textArea.append(printServiceAfter.toString());
                textArea.append("\n");
            }
        }
    }
}

class TestUI {
    private static JFrame mainFrame;
    private static JPanel mainControlPanel;

    private static JTextArea instructionTextArea;

    private static JPanel resultButtonPanel;
    private static JButton passButton;
    private static JButton failButton;
    private static JButton addedButton;

    private static JPanel testPanel;
    private static JButton testButton;
    private static JLabel buttonPressCountLabel;

    private static GridBagLayout layout;
    private final CountDownLatch latch;
    public boolean testResult = false;
    public volatile Boolean isAdded = false;
    public static JTextArea resultsTextArea;

    public TestUI(CountDownLatch latch) throws Exception {
        this.latch = latch;
    }

    public final void createUI() {
        mainFrame = new JFrame("RemotePrinterStatusRefresh");
        layout = new GridBagLayout();
        mainControlPanel = new JPanel(layout);
        resultButtonPanel = new JPanel(layout);
        testPanel = new JPanel(layout);
        GridBagConstraints gbc = new GridBagConstraints();

        // Create Test instructions
        String instructions
                = "This test displays the current list of printers(before) attached to \n"
                + "this computer in the results panel.\n\n"
                + "Please follow the below steps for this manual test\n"
                + "--------------------------------------------------------------------\n"
                + "Step 1: Add/Remove a new network printer and Wait for 4 minutes after adding/removing\n"
                + "Step 2: Then click on 'Printer Added/Removed' button\n"
                + "Step 2: Once the new network printer is added/removed, see if it is \n"
                + "        the same as displayed/not displayed in the results panel.\n"
                + "Step 3: If displayed/not displayed, then click 'Pass' else click on 'Fail' button";

        instructionTextArea = new JTextArea();
        instructionTextArea.setText(instructions);
        instructionTextArea.setEditable(false);
        instructionTextArea.setBorder(BorderFactory.
                createTitledBorder("Test Instructions"));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainControlPanel.add(instructionTextArea, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        testPanel.add(Box.createVerticalStrut(50));
        mainControlPanel.add(testPanel);

        addedButton = new JButton("Printer Added/Removed");
        addedButton.setActionCommand("Added");
        addedButton.addActionListener((ActionEvent e) -> {
            System.out.println("Added Button pressed!");
            isAdded = true;
        });

        // Create resultButtonPanel with Pass, Fail buttons
        passButton = new JButton("Pass");
        passButton.setActionCommand("Pass");
        passButton.addActionListener((ActionEvent e) -> {
            System.out.println("Pass Button pressed!");
            testResult = true;
            latch.countDown();
            disposeUI();
        });

        failButton = new JButton("Fail");
        failButton.setActionCommand("Fail");
        failButton.addActionListener((ActionEvent e) -> {
            System.out.println("Fail Button pressed!");
            testResult = false;
            latch.countDown();
            disposeUI();
        });

        gbc.gridx = 0;
        gbc.gridy = 0;
        resultButtonPanel.add(addedButton, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        resultButtonPanel.add(passButton, gbc);

        gbc.gridx = 2;
        gbc.gridy = 0;
        resultButtonPanel.add(failButton, gbc);

        resultsTextArea = new JTextArea();
        resultsTextArea.setEditable(false);
        resultsTextArea.setBorder(BorderFactory.
                createTitledBorder("Results"));

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainControlPanel.add(resultsTextArea, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        mainControlPanel.add(resultButtonPanel, gbc);

        mainFrame.add(mainControlPanel);
        mainFrame.pack();
        mainFrame.setVisible(true);
    }

    public void disposeUI() {
        mainFrame.dispose();
    }
}
