/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4184565
 * @summary Confirm that the default foreground color on a printer
 *          graphics object is black so that rendering will appear
 *          without having to execute setColor first.
 * @run main/manual InitToBlack
 */

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class InitToBlack implements Printable {

    private static volatile JFrame frame;
    private static volatile boolean testResult = false;
    private static volatile CountDownLatch printButtonCountDownLatch =
            new CountDownLatch(1);
    private static volatile CountDownLatch CountDownLatch =
            new CountDownLatch(1);
    private static volatile String failureReason;

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        Graphics2D g2d = (Graphics2D) graphics;
        g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
        graphics.drawString("Test Passes", 200, 200);
        return PAGE_EXISTS;
    }

    private void test() {
        PrinterJob pjob = PrinterJob.getPrinterJob();
        if (pjob.getPrintService() == null) {
            System.out.println("There is no printer configured on this system");
            return;
        }

        Book book = new Book();
        book.append(this, pjob.defaultPage());
        pjob.setPageable(book);

        try {
            pjob.print();
        } catch (PrinterException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private static void createTestUI() {
        frame = new JFrame("Test InitToBlack");
        String INSTRUCTION = """
                Aim: This test checks whether the default foreground color on a printer
                graphics object is black so that rendering will appear without having
                to execute setColor.
                Step:
                1) Click on the "Print" button. Check whether page is printed on the printer.
                2) Check whether "Test Passes" is printed on the page and it should be in
                black color. If yes then press "Pass" button else press "Fail" button.
                """;
        JTextArea instructionTextArea = new JTextArea(INSTRUCTION, 4, 40);
        instructionTextArea.setEditable(false);

        JPanel buttonPanel = new JPanel();
        JButton printButton = new JButton("Print");
        printButton.addActionListener((ae) -> {
            InitToBlack initToBlack = new InitToBlack();
            initToBlack.test();
            printButtonCountDownLatch.countDown();
        });

        JButton passButton = new JButton("Pass");
        passButton.addActionListener((ae) -> {
            testResult = true;
            CountDownLatch.countDown();
            frame.dispose();
        });
        JButton failButton = new JButton("Fail");
        failButton.addActionListener((ae) -> {
            getFailureReason();
            frame.dispose();
        });
        buttonPanel.add(printButton);
        buttonPanel.add(passButton);
        buttonPanel.add(failButton);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(instructionTextArea, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        frame.add(panel);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    public static void getFailureReason() {
        final JDialog dialog = new JDialog();
        dialog.setTitle("Read testcase failure reason");
        JPanel jPanel = new JPanel(new BorderLayout());
        JTextArea jTextArea = new JTextArea(5, 20);

        JButton okButton = new JButton("Ok");
        okButton.addActionListener((ae) -> {
            failureReason = jTextArea.getText();
            testResult = false;
            CountDownLatch.countDown();
            dialog.dispose();
        });

        jPanel.add(new JLabel("Enter the testcase failed reason below and " +
                "click OK button", JLabel.CENTER), BorderLayout.NORTH);
        jPanel.add(jTextArea, BorderLayout.CENTER);

        JPanel okayBtnPanel = new JPanel();
        okayBtnPanel.add(okButton);

        jPanel.add(okayBtnPanel, BorderLayout.SOUTH);
        dialog.add(jPanel);
        dialog.setLocationRelativeTo(null);
        dialog.pack();
        dialog.setVisible(true);
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(InitToBlack::createTestUI);
        if (!printButtonCountDownLatch.await(2, TimeUnit.MINUTES)) {
            throw new RuntimeException("Timeout: User did not perform action " +
                    "on Print button.");
        }
        if (!CountDownLatch.await(2, TimeUnit.MINUTES)) {
            throw new RuntimeException("Timeout : User did not decide " +
                    "whether test passed or failed");
        }

        if (!testResult) {
            throw new RuntimeException("Test failed : " + failureReason);
        } else {
            System.out.println("Test Passed");
        }
    }
}
