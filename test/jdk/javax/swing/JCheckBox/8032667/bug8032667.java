/*
 * Copyright (c) 2014, 2021 Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/* @test
 * @bug 8032667
 * @summary [macosx] Components cannot be rendered in HiDPI to BufferedImage
 * @run main bug8032667
 */
public class bug8032667 {

    static final int scale = 2;
    static final int width = 100;
    static final int height = 50;
    static final int scaledWidth = scale * width;
    static final int scaledHeight = scale * height;
    private static JFrame frame;

    private static volatile boolean passed = false;
    private static final CountDownLatch latch = new CountDownLatch(1);

    public static final String INSTRUCTIONS = "INSTRUCTIONS:\n\n"
            + "  Verify that scaled components are rendered smoothly to image.\n\n"
            + "  1. Run the test.\n"
            + "  2. Check that Selected and Deselected JCheckBox icons are drawn smoothly.\n\n"
            + "  If so, press PASS, else press FAIL.\n";

    public static void main(String args[]) throws Exception {
        try {
            SwingUtilities.invokeLater(() -> createTestGUI());

            if (!latch.await(5, TimeUnit.MINUTES)) {
                throw new RuntimeException("Test has timed out!");
            }
            if (!passed) {
                throw new RuntimeException("Test failed!");
            }
        } finally {
              SwingUtilities.invokeAndWait(() -> {
                  if (frame != null) {
                      frame.dispose();
                  }
              });
        }
    }

    private static void createTestGUI() {
        JPanel panel = new JPanel();
        BoxLayout layout = new BoxLayout(panel, BoxLayout.Y_AXIS);
        panel.setLayout(layout);

        frame = new JFrame("bug8032667");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        final Image image1 = getImage(getCheckBox("Deselected", false));
        final Image image2 = getImage(getCheckBox("Selected", true));

        Canvas canvas = new Canvas() {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                g.drawImage(image1, 0, 0, scaledWidth, scaledHeight, this);
                g.drawImage(image2, 0, scaledHeight + 5,
                        scaledWidth, scaledHeight, this);
            }
        };
        panel.add(canvas);

        frame.getContentPane().add(panel, BorderLayout.CENTER);
        frame.setSize(200, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        JTextComponent textComponent = new JTextArea(INSTRUCTIONS);
        textComponent.setEditable(false);
        panel.add(textComponent, BorderLayout.CENTER);
        frame.getContentPane().add(panel, BorderLayout.SOUTH);

        JPanel buttonsPanel = new JPanel(new FlowLayout());
        JButton passButton = new JButton("Pass");
        passButton.addActionListener((e) -> {
            System.out.println("Test passed!");
            passed = true;
            latch.countDown();
        });
        JButton failsButton = new JButton("Fail");
        failsButton.addActionListener((e) -> {
            passed = false;
            latch.countDown();
        });

        buttonsPanel.add(passButton);
        buttonsPanel.add(failsButton);
        panel.add(buttonsPanel, BorderLayout.SOUTH);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                latch.countDown();
                frame.dispose();
            }
        });

        frame.getContentPane().add(panel);
        frame.pack();
    }

    static JCheckBox getCheckBox(String text, boolean selected) {
        JCheckBox checkBox = new JCheckBox(text);
        checkBox.setSelected(selected);
        checkBox.setSize(new Dimension(width, height));
        return checkBox;
    }

    static Image getImage(JComponent component) {
        final BufferedImage image = new BufferedImage(
                scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        final Graphics g = image.getGraphics();
        ((Graphics2D) g).scale(scale, scale);
        component.paint(g);
        g.dispose();

        return image;
    }
}
