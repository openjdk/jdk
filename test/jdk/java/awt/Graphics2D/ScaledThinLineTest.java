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

/**
 * @test
 * @bug 4210466 4417756
 * @summary thin lines are not draw correctly under large scales
 */
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.geom.Ellipse2D;

public class ScaledThinLineTest {

    public static void main(String[] args) {
        ScaledThinLineTest c1 = new ScaledThinLineTest(200, 200);
        ScaledThinLineTest c2 = new ScaledThinLineTest(1, 10000);
        ScaledThinLineTest c3 = new ScaledThinLineTest(10000, 1);
        ScaledThinLineTest c4 = new ScaledThinLineTest(0.01, 10000);
        ScaledThinLineTest c5 = new ScaledThinLineTest(10000, 0.01);
        compare(c1.bi, c2.bi);
        compare(c2.bi, c3.bi);
        compare(c3.bi, c4.bi);
        compare(c4.bi, c5.bi);
    }

    private final Shape shape;
    private final double scaleX,scaleY;
    private BufferedImage bi = null;

    public ScaledThinLineTest(double width, double height) {
        shape = new Ellipse2D.Double(0.25*width, 0.25*height, width, height);
        scaleX = 200/width;
        scaleY = 200/height;
        int iw = 300, ih = 300;
        bi = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = bi.createGraphics();
        g2.setColor(Color.white);
        g2.fillRect(0, 0, iw, ih);
        g2.setColor(Color.black);
        g2.scale(scaleX,scaleY);
        g2.setStroke(new BasicStroke(0));
        g2.draw(shape);
    }


    static void compare(BufferedImage i1, BufferedImage i2) {
        int w = i1.getWidth(), h = i1.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p1 = i1.getRGB(x, y);
                int p2 = i2.getRGB(x, y);
                if (p1 != p2) {
                    System.out.println("images differ at " + x + " " + y);
                }
            }
        }
    }
}
