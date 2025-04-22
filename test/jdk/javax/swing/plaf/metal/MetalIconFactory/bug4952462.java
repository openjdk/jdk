/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4952462
 * @summary Ocean: Tests that disabled selected JRadioButton dot is NOT
 *          painted with the foreground color
 * @modules java.desktop/sun.awt
 * @library /test/lib
 * @key headful
 * @run main/othervm -Dsun.java2d.uiScale=1 bug4952462
 */

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Robot;

import javax.swing.JFrame;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;

public class bug4952462 {
    private static JFrame frame;
    private static JRadioButton rb;

    public static void main(String[] args) throws Exception {
        try {
            MetalLookAndFeel.setCurrentTheme(new OceanTheme());
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            Robot r = new Robot();
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("Metal JRadioButton Foreground Color Test");
                frame.getContentPane().setLayout(new FlowLayout());
                rb = new JRadioButton("RadioButton", true);
                rb.setEnabled(false);
                rb.setForeground(Color.RED);
                frame.getContentPane().add(rb);
                frame.setSize(250, 100);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });

            r.waitForIdle();
            r.delay(500);

            SwingUtilities.invokeAndWait(() -> {
                Point p = rb.getLocationOnScreen();
                for (int i = 0; i < 50; i++) {
                    Color c = r.getPixelColor(p.x + 10 + i, p.y + (rb.getHeight() / 2));
                    System.out.println(c);
                    if (c.getRed() > 200 && c.getBlue() < 80 && c.getGreen() < 80) {
                        throw new RuntimeException("Test failed. Radiobutton is red " +
                                "and not grey.");
                    }
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
