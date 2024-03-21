/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Graphics2D;
import java.awt.Point;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.UIManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/*
 * @test
 * @bug 4129681
 * @summary Tests disabling of titled border's caption
 * @run main/othervm -Dsun.java2d.uiScale=1 Test4129681
 */

public class Test4129681 {
    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        int correctColoredPixels = 0;
        int totalPixels = 0;
        int tolerance = 20;
        JLabel label;
        Color labelDisableColor = Color.RED;
        Dimension SIZE = new Dimension(100, 40);
        Point startPoint = new Point(8, 4);
        Point endPoint = new Point(18, 14);

        label = new JLabel("Label");
        label.setBorder(BorderFactory.createTitledBorder("\u2588".repeat(5)));
        UIManager.getDefaults().put("Label.disabledForeground", labelDisableColor);
        label.setSize(SIZE);
        label.setEnabled(false);
        BufferedImage image = new BufferedImage(label.getWidth(), label.getHeight(),
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = image.createGraphics();
        label.paint(g2d);
        g2d.dispose();

        for (int x = startPoint.x; x < endPoint.x; x++) {
            for (int y = startPoint.y; y < endPoint.y; y++) {
                if (image.getRGB(x, y) == labelDisableColor.getRGB()) {
                    correctColoredPixels++;
                }
                totalPixels++;
            }
        }

        if (((double) correctColoredPixels / totalPixels * 100) <= tolerance) {
            ImageIO.write(image, "png", new File("failureImage.png"));
            throw new RuntimeException("Label with border is not disabled");
        }
        System.out.println("Test Passed");
    }
}
