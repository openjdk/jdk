/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4382876
 * @summary Tests how PgUp and PgDn keys work with JSlider
 * @key headful
 * @run main bug4382876
 */

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

public class bug4382876 {
    private static Robot r;
    private static JFrame f;
    private static JSlider slider;
    private static boolean upFail;
    private static boolean downFail;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                f = new JFrame("JSlider PageUp/Down Test");
                f.setSize(300, 200);
                f.setLocationRelativeTo(null);
                f.setVisible(true);
                slider = new JSlider(-1000, -900, -1000);
                slider.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
                slider.putClientProperty("JSlider.isFilled", Boolean.TRUE);
                f.add(slider, BorderLayout.CENTER);
            });

            r = new Robot();
            r.setAutoDelay(100);
            r.waitForIdle();
            r.delay(1000);

            r.keyPress(KeyEvent.VK_PAGE_UP);
            SwingUtilities.invokeAndWait(() -> {
                if (slider.getValue() < -1000) {
                    System.out.println("PAGE_UP VAL: " + slider.getValue());
                    upFail = true;
                }
            });
            if (upFail) {
                writeFailImage();
                throw new RuntimeException("Slider value did NOT change with PAGE_UP");
            }
            r.keyPress(KeyEvent.VK_PAGE_DOWN);
            SwingUtilities.invokeAndWait(() -> {
                if (slider.getValue() > -1000) {
                    System.out.println("PAGE_DOWN VAL: " + slider.getValue());
                    downFail = true;
                }
            });
            if (downFail) {
                writeFailImage();
                throw new RuntimeException("Slider value did NOT change with PAGE_DOWN");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    private static void writeFailImage() throws IOException {
        GraphicsConfiguration ge = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration();
        BufferedImage failImage = r.createScreenCapture(ge.getBounds());
        ImageIO.write(failImage, "png", new File("failImage.png"));
    }
}
