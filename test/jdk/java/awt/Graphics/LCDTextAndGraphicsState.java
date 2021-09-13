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
 * @bug 6576507
 * @summary Both lines of text should be readable
 * @run main/manual LCDTextAndGraphicsState
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.RenderingHints;
import java.awt.AlphaComposite;
import java.awt.GradientPaint;
import java.awt.TextArea;
import java.awt.Shape;
import java.awt.Panel;
import java.awt.Insets;

import java.awt.event.ActionEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LCDTextAndGraphicsState extends Component {

    private static final Frame instructionFrame = new Frame();
    private static volatile boolean testResult = false;
    private static volatile CountDownLatch countDownLatch;
    private static String text = "This test passes only if this text appears SIX TIMES";
    private static final String INSTRUCTIONS = "INSTRUCTIONS:\n\n" +
            "You should see the text \' " + text + " \' on the frame. \n" +
            "If you see the same then test pass else test fail.";

    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, getSize().width, getSize().height);

        test1(g.create(0, 0, 500, 200));
        test2(g.create(0, 200, 500, 200));
        test3(g.create(0, 400, 500, 200));
    }

    public void test1(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2d.setColor(Color.black);
        g2d.drawString(text, 10, 50);
        g2d.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, 0.9f));
        g2d.drawString(text, 10, 80);
    }

    public void test2(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2d.setColor(Color.black);
        g2d.drawString(text, 10, 50);
        g2d.setPaint(new GradientPaint(
                0f, 0f, Color.BLACK, 100f, 100f, Color.GRAY));
        g2d.drawString(text, 10, 80);
    }

    public void test3(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2d.setColor(Color.black);
        g2d.drawString(text, 10, 50);
        Shape s = new RoundRectangle2D.Double(0, 60, 400, 50, 5, 5);
        g2d.clip(s);
        g2d.drawString(text, 10, 80);
    }

    public Dimension getPreferredSize() {
        return new Dimension(500, 600);
    }

    private static void createInstructionUI() {
        GridBagLayout layout = new GridBagLayout();
        Panel mainControlPanel = new Panel(layout);
        Panel resultButtonPanel = new Panel(layout);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(5, 15, 5, 15);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        TextArea instructionTextArea = new TextArea();
        instructionTextArea.setText(INSTRUCTIONS);
        instructionTextArea.setEditable(false);
        instructionTextArea.setBackground(Color.white);
        mainControlPanel.add(instructionTextArea, gbc);

        Button passButton = new Button("Pass");
        passButton.setActionCommand("Pass");
        passButton.addActionListener((ActionEvent e) -> {
            testResult = true;
            countDownLatch.countDown();
        });

        Button failButton = new Button("Fail");
        failButton.setActionCommand("Fail");
        failButton.addActionListener(e -> {
            countDownLatch.countDown();
        });

        gbc.gridx = 0;
        gbc.gridy = 0;

        resultButtonPanel.add(passButton, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        resultButtonPanel.add(failButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        mainControlPanel.add(resultButtonPanel, gbc);

        instructionFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                countDownLatch.countDown();
            }
        });
        instructionFrame.pack();
        instructionFrame.add(mainControlPanel);
        instructionFrame.pack();
        instructionFrame.setVisible(true);
    }

    public static void creatUI() {
        Frame f = new Frame("Composite and Text Test");
        f.add(new LCDTextAndGraphicsState(), BorderLayout.CENTER);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    public static void main(String[] args) throws InterruptedException {
        countDownLatch = new CountDownLatch(1);
        createInstructionUI();
        creatUI();
        if (!countDownLatch.await(15, TimeUnit.MINUTES)) {
            throw new RuntimeException("Timeout : No action was performed on the test UI.");
        }
        if (!testResult) {
            throw new RuntimeException("Test failed!");
        }
    }
}
