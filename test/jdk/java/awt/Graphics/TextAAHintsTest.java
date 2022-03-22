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

/*
 * @test
 * @bug 6263951
 * @summary Text should be B&W, grayscale, and LCD.
 * @requires (os.family != "mac")
 * @run main/manual TextAAHintsTest
 */

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.ImageCapabilities;
import java.awt.Panel;
import java.awt.RenderingHints;
import java.awt.TextArea;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TextAAHintsTest extends Component {

    private static final String black = "This text should be solid black";
    private static final String gray  = "This text should be gray scale anti-aliased";
    private static final String lcd   = "This text should be LCD sub-pixel text (coloured).";
    private static final CountDownLatch countDownLatch = new CountDownLatch(1);
    private static volatile String failureReason;
    private static volatile boolean testPassed = false;
    private static Frame frame;

    public void paint(Graphics g) {

        Graphics2D g2d = (Graphics2D)g.create();
        g2d.setColor(Color.white);
        g2d.fillRect(0,0,getSize().width, getSize().height);

        drawText(g.create(0, 0, 500, 100));
        bufferedImageText(g.create(0, 100, 500, 100));
        volatileImageText(g.create(0, 200, 500, 100));
    }

    private void drawText(Graphics g) {

        Graphics2D g2d = (Graphics2D)g;

        g2d.setColor(Color.white);
        g2d.fillRect(0,0,500,100);

        g2d.setColor(Color.black);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                             RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g2d.drawString(black, 10, 20);

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                             RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT);
        g2d.drawString(gray, 10, 35);

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                             RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.drawString(gray, 10, 50);

        /* For visual comparison, render grayscale with graphics AA off */
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.drawString(gray, 10, 65);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                             RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2d.drawString(lcd, 10, 80);
    }

    public void bufferedImageText(Graphics g) {
        BufferedImage bi =
                 new BufferedImage(500, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bi.createGraphics();

        drawText(g2d);
        g.drawImage(bi, 0, 0, null);
    }

    public VolatileImage getVolatileImage(int w, int h) {
        VolatileImage image;
        try {
            image = createVolatileImage(w, h, new ImageCapabilities(true));
        } catch (AWTException e) {
            System.out.println(e);
            System.out.println("Try creating non-accelerated VI instead.");
            try {
                image = createVolatileImage(w, h,
                                            new ImageCapabilities(false));
            } catch (AWTException e1) {
                System.out.println("Skipping volatile image test.");
                image = null;
            }
        }
        return image;
    }

    public void volatileImageText(Graphics g) {
        VolatileImage image = getVolatileImage(500, 100);
        if (image == null) {
            return;
        }
        boolean painted = false;
        while (!painted) {
            int status = image.validate(getGraphicsConfiguration());
            if (status == VolatileImage.IMAGE_INCOMPATIBLE) {
                image = getVolatileImage(500, 100);
                if (image == null) {
                    return;
                }
            }
            drawText(image.createGraphics());
            g.drawImage(image, 0, 0, null);
            painted = !image.contentsLost();
            System.out.println("painted = " + painted);
        }
    }

    public Dimension getPreferredSize() {
        return new Dimension(500,300);
    }

    public static void createTestUI() {
        frame = new Frame("Composite and Text Test");
        TextAAHintsTest textAAHintsTestObject = new TextAAHintsTest();
        frame.add(textAAHintsTestObject, BorderLayout.NORTH);

        String instructions = """
                Note: Texts are rendered with different TEXT_ANTIALIASING &
                   VALUE_TEXT_ANTIALIAS. Text should be B&W, grayscale, and LCD.
                   Note: The results may be visually the same.
                1. Verify that first set of text are rendered correctly.
                2. Second set of text are created using BufferedImage of the first text.
                3. Third set of text are created using VolatileImage of the first text.
                """;
        TextArea instructionTextArea = new TextArea(instructions, 8, 50);
        instructionTextArea.setEditable(false);
        frame.add(instructionTextArea, BorderLayout.CENTER);

        Panel controlPanel = new Panel();
        Button passButton = new Button("Pass");
        passButton.addActionListener(e -> {
            testPassed = true;
            countDownLatch.countDown();
            frame.dispose();
        });
        Button failButton = new Button("Fail");
        failButton.addActionListener(e -> {
            getFailureReason();
            testPassed = false;
            countDownLatch.countDown();
            frame.dispose();
        });
        controlPanel.add(passButton);
        controlPanel.add(failButton);
        frame.add(controlPanel, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void getFailureReason() {
        // Show dialog to read why the testcase was failed and append the
        // testcase failure reason to the output
        final Dialog dialog = new Dialog(frame, "TestCase" +
                " failure reason", true);
        TextArea textArea = new TextArea("", 5, 60, TextArea.SCROLLBARS_BOTH);
        dialog.add(textArea, BorderLayout.CENTER);

        Button okButton = new Button("OK");
        okButton.addActionListener(e1 -> {
            failureReason = textArea.getText();
            dialog.dispose();
        });
        Panel ctlPanel = new Panel();
        ctlPanel.add(okButton);
        dialog.add(ctlPanel, BorderLayout.SOUTH);
        dialog.setLocationRelativeTo(null);
        dialog.pack();
        dialog.setVisible(true);
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(TextAAHintsTest::createTestUI);
        if (!countDownLatch.await(2, TimeUnit.MINUTES)) {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
            throw new RuntimeException("Timeout : No action was taken on the test.");
        }

        if (!testPassed) {
            throw new RuntimeException("Test failed : Reason : " + failureReason);
        }
    }
}

