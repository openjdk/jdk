/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package lib;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.NORTH;
import static java.lang.String.format;
import static javax.swing.SwingUtilities.invokeAndWait;
import static javax.swing.SwingUtilities.invokeLater;

/**
 * Allows ti take screenshot, possible with a delay to preapare the UI.
 */
class ScreenImagePane extends JPanel {
    private final JPanel imagePanel;
    private final JLabel imageLabel;
    private final AtomicReference<BufferedImage> image = new AtomicReference<>();
    private final Rectangle screenRect;
    private final JFormattedTextField delayField;
    private final Consumer<Throwable> exceptionHandler;

    /**
     *
     * @param handler should an exception appear on other threads
     */
    ScreenImagePane(Consumer<Throwable> handler) {
        exceptionHandler = handler;
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        screenRect = new Rectangle(0, 0, screenSize.width, screenSize.height);
        JPanel controls = new JPanel();
        delayField = new JFormattedTextField(NumberFormat.getNumberInstance());
        delayField.setText("0");
        delayField.setColumns(3);
        JButton capture = new JButton("Retake screenshot");
        controls.add(new JLabel("in "));
        controls.add(delayField);
        controls.add(new JLabel(" seconds "));
        controls.add(capture);
        capture.addActionListener((e) -> capture());
        imagePanel = new JPanel();
        imageLabel = new JLabel();
        imagePanel.add(imageLabel);

        setLayout(new BorderLayout());
        add(controls, NORTH);
        add(imagePanel, CENTER);
    }

    public void capture() {
        new Thread(() -> {
            try {
                int delay = Integer.parseInt(delayField.getText());
                invokeAndWait(() -> imageLabel.setIcon(null));
                while (delay > 0) {
                    String message = format("Retaking screenshot in %d seconds", delay);
                    invokeLater(() -> imageLabel.setText(message));
                    delay--;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                }
                BufferedImage image = new Robot().createScreenCapture(screenRect);
                ScreenImagePane.this.image.set(image);
                int newWidth = imagePanel.getWidth();
                int newHeight = imagePanel.getHeight();
                float xratio = (float) newWidth / (float) image.getWidth();
                float yratio = (float) newHeight / (float) image.getHeight();
                if (xratio < yratio) {
                    newHeight = (int) (image.getHeight() * xratio);
                } else {
                    newWidth = (int) (image.getWidth() * yratio);
                }
                Image scaled = image.getScaledInstance(newWidth, newHeight, Image.SCALE_FAST);
                invokeAndWait(() -> {
                    imageLabel.setText(null);
                    imageLabel.setIcon(new ImageIcon(scaled));
                });
            } catch (Throwable e) {
                exceptionHandler.accept(e);
            }
        }).start();
    }

    public BufferedImage getImage() {
        return image.get();
    }
}
