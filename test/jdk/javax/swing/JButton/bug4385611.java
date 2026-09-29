/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/*
 * @test
 * @bug 4385611 8078655
 * @requires (os.family == "windows")
 * @summary The button's preferred width/height calculation.
 * @run main bug4385611
 */

public class bug4385611 {
    static JButton bt1, bt2;
    static final ImageIcon icon32x32 = generateImageIcon();
    static final Dimension DIM_32X32 = new Dimension(32, 32);
    static final Dimension DIM_33X33 = new Dimension(33, 33);

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
                bt1 = new JButton(icon32x32);
                bt1.setMargin(new Insets(0, 0, 0, 0));
                bt1.setBorder(null);
                bt1.setFocusPainted(true);

                bt2 = new JButton(icon32x32);
                bt2.setMargin(new Insets(0, 0, 0, 0));
                bt2.setBorder(null);
                bt2.setFocusPainted(false);

                if (!bt1.getPreferredSize().equals(DIM_32X32) ||
                        !bt2.getPreferredSize().equals(DIM_32X32)) {
                    throw new RuntimeException("The button's preferred size should be 32x32");
                }
            } catch (Exception e) {
                throw new RuntimeException("Can not initialize Metal LnF", e);
            }

            try {
                UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
                bt1.updateUI();
                bt1.setBorder(null);
                bt2.updateUI();
                bt2.setBorder(null);
                if (!bt1.getPreferredSize().equals(DIM_33X33) ||
                        !bt2.getPreferredSize().equals(DIM_32X32)) {
                    throw new RuntimeException("The button's preferred size should be "
                            + "33x33 and 32x32 correspondingly.");
                }
            } catch (Exception e) {
                throw new RuntimeException("Can not initialize Windows LnF", e);
            }
        });
    }

    private static ImageIcon generateImageIcon() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.createGraphics();
        g.setColor(Color.YELLOW);
        g.fillRect(0, 0, 32, 32);
        g.dispose();
        return new ImageIcon(image);
    }
}
