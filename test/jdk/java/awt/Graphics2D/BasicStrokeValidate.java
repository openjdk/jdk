/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4363534
 * @summary This test verifies that setting a non-thin-line BasicStroke
 *     on a Graphics2D obtained from a BufferedImage will correctly validate
 *     the pipelines for the line-widening pipeline even if that is the only
 *     non-default attribute on the graphics.
 *
 */

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class BasicStrokeValidate  {

    public static final int TESTW = 100;
    public static final int TESTH = 100;

    public static void main(String[] args) {
        BufferedImage bi1 = createImage(false);
        BufferedImage bi2 = createImage(true);
        compare(bi1, bi2); // images should differ
    }

    static BufferedImage createImage(boolean dashed) {
        BufferedImage bi = new BufferedImage(TESTW, TESTH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bi.createGraphics();
        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, TESTW, TESTH);
        g2d.setColor(Color.black);
        if (dashed) {
            g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_SQUARE,
                                          BasicStroke.JOIN_MITER, 10.0f,
                                          new float[] {2.5f, 3.5f},
                                          0.0f));
        }
        g2d.drawRect(10, 10, TESTW-20, TESTH-20);
        g2d.setStroke(new BasicStroke(10f));
        g2d.drawRect(20, 20, TESTW-40, TESTH-40);
        return bi;
    }

    static void compare(BufferedImage i1, BufferedImage i2) {
        boolean same = true;
        int w = i1.getWidth(), h = i1.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p1 = i1.getRGB(x, y);
                int p2 = i2.getRGB(x, y);
                if (p1 != p2) {
                    same = false;
                }
            }
            if (!same) {
                break;
            }
        }
        if (same) {
             throw new RuntimeException("No difference");
        }
    }
}
