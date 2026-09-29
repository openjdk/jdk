/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4198822
 * @summary Tests that the bottom line drawn by
 *          BasicGraphicsUtils.drawEtchedRect extends to the end.
 * @run main DrawEtchedRectTest
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.plaf.basic.BasicGraphicsUtils;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

public class DrawEtchedRectTest {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 200;
    private static final int RANGE = 10;

    public static void main(String[] args) throws Exception {
        // Draw etched rectangle to a BufferedImage
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        Component sq = new Component() {
            public void paint(Graphics g) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, WIDTH, HEIGHT);
                BasicGraphicsUtils.drawEtchedRect(g, 0, 0, WIDTH, HEIGHT,
                        Color.black, Color.black,
                        Color.black, Color.black);
            }
        };
        sq.paint(g2d);
        g2d.dispose();

        // Check if connected at bottom-right corner
        int c1;
        int c2;
        for (int i = 1; i < RANGE; i++) {
            c1 = image.getRGB(WIDTH - i, HEIGHT - 1);
            c2 = image.getRGB(WIDTH - 1, HEIGHT - i);
            if (c1 == Color.WHITE.getRGB() || c2 == Color.WHITE.getRGB()) {
                ImageIO.write(image, "png", new File("failImage.png"));
                throw new RuntimeException("Bottom line is not connected!");
            }
        }
    }
}
