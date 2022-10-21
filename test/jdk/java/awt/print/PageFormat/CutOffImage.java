/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, BELLSOFT. All rights reserved.
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
 * @bug 8295737
 * @summary macOS: Print content cut off when width > height with portrait orientation
 * @run main/othervm/manual CutOffImage
 */

import javax.print.PrintServiceLookup;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import java.awt.print.Book;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.print.Paper;


public class CutOffImage {

    private static final String DESCRIPTION =
            " 1. Setup printer on the system.\n" +
                    " 2. Press Print button to print 4 rectangles.\n" +
                    "    - rectangle size: 300x100, orientation portrait\n" +
                    "    - rectangle size: 300x100, orientation landscape\n" +
                    "    - rectangle size: 100x300, orientation portrait\n" +
                    "    - rectangle size: 100x300, orientation landscape\n" +
                    " 3. Check that 4 printed rectangles have fully drawn 8 vertical areas labeled from 1 to 8.\n" +
                    " 4. If so, press PASS button, otherwise press FAIL button.\n";


    private static final CountDownLatch testEndedSignal = new CountDownLatch(1);
    private static final int testTimeout = 300000;
    private static volatile String testFailureMsg;
    private static volatile boolean testPassed;
    private static volatile boolean testFinished;

    private static final double DOC_WIDTH = 300;
    private static final double DOC_HEIGHT = 100;

    public static void main(String[] args) throws Exception {

        SwingUtilities.invokeLater(() -> createAndShowTestDialog());

        try {
            if (!testEndedSignal.await(testTimeout, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException(String.format(
                        "Test timeout '%d ms' elapsed.", testTimeout));
            }
            if (!testPassed) {
                String failureMsg = testFailureMsg;
                if ((failureMsg != null) && (!failureMsg.trim().isEmpty())) {
                    throw new RuntimeException(failureMsg);
                } else {
                    throw new RuntimeException("Test failed.");
                }
            }
        } catch (InterruptedException ie) {
            throw new RuntimeException(ie);
        } finally {
            testFinished = true;
        }
    }

    private static void pass() {
        testPassed = true;
        testEndedSignal.countDown();
    }

    private static void fail(String failureMsg) {
        testFailureMsg = failureMsg;
        testPassed = false;
        testEndedSignal.countDown();
    }

    private static String convertMillisToTimeStr(int millis) {
        if (millis < 0) {
            return "00:00:00";
        }
        int hours = millis / 3600000;
        int minutes = (millis - hours * 3600000) / 60000;
        int seconds = (millis - hours * 3600000 - minutes * 60000) / 1000;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static void createAndShowTestDialog() {

        final JDialog dialog = new JDialog();
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dialog.dispose();
                fail("Main dialog was closed.");
            }
        });

        final JLabel testTimeoutLabel = new JLabel(String.format(
                "Test timeout: %s", convertMillisToTimeStr(testTimeout)));
        final long startTime = System.currentTimeMillis();
        final Timer timer = new Timer(0, null);
        timer.setDelay(1000);
        timer.addActionListener((e) -> {
            int leftTime = testTimeout - (int) (System.currentTimeMillis() - startTime);
            if ((leftTime < 0) || testFinished) {
                timer.stop();
                dialog.dispose();
            }
            testTimeoutLabel.setText(String.format(
                    "Test timeout: %s", convertMillisToTimeStr(leftTime)));
        });
        timer.start();

        JTextArea textArea = new JTextArea(DESCRIPTION);
        textArea.setEditable(false);

        final JButton testButton = new JButton("Print");
        final JButton passButton = new JButton("PASS");
        final JButton failButton = new JButton("FAIL");

        testButton.addActionListener((e) -> {
            testButton.setEnabled(false);
            new Thread(() -> {
                try {
                    doTest();

                    SwingUtilities.invokeLater(() -> {
                        passButton.setEnabled(true);
                        failButton.setEnabled(true);
                    });
                } catch (Throwable t) {
                    t.printStackTrace();
                    dialog.dispose();
                    fail("Exception occurred in a thread executing the test.");
                }
            }).start();
        });
        passButton.setEnabled(false);
        passButton.addActionListener((e) -> {
            dialog.dispose();
            pass();
        });
        failButton.setEnabled(false);
        failButton.addActionListener((e) -> {
            dialog.dispose();
            fail("TitledBorder label is cut off");
        });

        JPanel mainPanel = new JPanel(new BorderLayout());

        JPanel labelPanel = new JPanel(new FlowLayout());
        labelPanel.add(testTimeoutLabel);
        mainPanel.add(labelPanel, BorderLayout.NORTH);
        mainPanel.add(textArea, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(testButton);
        buttonPanel.add(passButton);
        buttonPanel.add(failButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        dialog.add(mainPanel);

        dialog.pack();
        dialog.setVisible(true);
    }

    private static void doTest() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                print(DOC_WIDTH, DOC_HEIGHT);
            } catch (PrinterException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static String getOrientation(int orientation) {
        switch (orientation) {
            case PageFormat.LANDSCAPE:
                return "LANDSCAPE";
            case PageFormat.PORTRAIT:
                return "PORTRAIT";
            case PageFormat.REVERSE_LANDSCAPE:
                return "REVERSE_LANDSCAPE";
            default:
                return "UNKNOWN";
        }
    }

    private static void paintImage(Graphics2D g, int width, int height, int orientation) {
        BufferedImage img = createImage(width, height, orientation);
        g.drawImage(img, 0, 0, null);
    }

    private static void appendToBook(PrinterJob job, Book book, double width, double height, int orientation) {

        PageFormat page = job.getPageFormat(null);
        page.setOrientation(orientation);
        Paper paper = page.getPaper();

        boolean isPortrait = (orientation == PageFormat.PORTRAIT);
        double w = (isPortrait) ? width : height;
        double h = (isPortrait) ? height : width;
        paper.setSize(w, h);
        paper.setImageableArea(0, 0, w, h);

        page.setPaper(paper);
        page.setOrientation(orientation);
        book.append(new TestPrintable(width, height), page);
    }

    private static void print(double width, double height) throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(PrintServiceLookup.lookupDefaultPrintService());

        Book book = new Book();
        appendToBook(job, book, width, height, PageFormat.PORTRAIT);
        appendToBook(job, book, width, height, PageFormat.LANDSCAPE);
        appendToBook(job, book, height, width, PageFormat.PORTRAIT);
        appendToBook(job, book, height, width, PageFormat.LANDSCAPE);

        job.setPageable(book);

        if (job.printDialog()) {
            job.print();
        } else {
            throw new RuntimeException("Printing was canceled!");
        }
    }

    private static String getLabel(int width, int height, int orientation) {
        return String.format("%dx%d %s", width, height, getOrientation(orientation));
    }

    private static BufferedImage createImage(int w, int h, int orientation) {

        int x = 0;
        int y = 0;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setColor(Color.ORANGE);
        g.fillRect(x, y, w, h);

        g.setColor(Color.BLUE);
        g.drawRect(x, y, w, h);
        g.drawRect(x + 1, y + 1, w - 2, h - 2);
        g.drawLine(x, y, x + w, y + h);
        g.drawLine(x, y + h, x + w, y);

        g.setFont(g.getFont().deriveFont(10.0f));

        int N = 8;
        int dx = w / N;

        for (int i = 0; i < N; i++) {
            int xx = i * dx + x;
            g.setColor(Color.BLUE);
            g.drawLine(xx, y, xx, y + h);
            g.setColor(Color.BLUE);
            g.drawString("" + (i + 1), xx + 3, y + h / 2);
        }

        g.setColor(Color.RED);
        String label = getLabel(w, h, orientation);
        g.drawString(label, x + w / 10, y + h / 4);

        g.dispose();
        return img;
    }

    private static class TestPrintable implements Printable {

        private final double width;
        private final double height;

        TestPrintable(double width, double height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int index) {
            paintImage((Graphics2D) graphics, (int) width, (int) height, pageFormat.getOrientation());
            return PAGE_EXISTS;
        }
    }
}
