/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @key     headful
 * @bug     8387593
 * @summary Tests that clearing a polyline using XOR mode does not
 *          leave any traces. Using uiScale 1 helps us to
 *          reproduce the issue.
 * @run     main/othervm -Dsun.java2d.uiScale=1 ClearPolyLineUsingXORTest
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;

public class ClearPolyLineUsingXORTest {
    private static final int SIZE = 500;

    private static BufferedImage clearUsingXOR(GraphicsConfiguration gc) {
        VolatileImage vImg = gc.createCompatibleVolatileImage(SIZE, SIZE);
        int attempt = 0;
        while (true) {
            if (++attempt > 10) {
                throw new RuntimeException("Unable to use VolatileImage after "
                    + attempt + " attempts");
            }

            int status = vImg.validate(gc);
            if (status == VolatileImage.IMAGE_INCOMPATIBLE) {
                vImg = gc.createCompatibleVolatileImage(SIZE, SIZE);
            }

            Graphics2D g2d = vImg.createGraphics();
            g2d.setBackground(Color.BLACK);
            g2d.clearRect(0, 0, 500, 500);

            int min = 10;
            int max = 210;
            int mid = 110;

            int xdp[] = {min, max, min, max, min, max};
            int ydp[] = {min, min, mid, max, max, mid};

            g2d.setXORMode(Color.GREEN);
            g2d.drawPolygon(xdp, ydp, xdp.length);
            g2d.drawPolygon(xdp, ydp, xdp.length);

            BufferedImage snapshot = vImg.getSnapshot();
            if (vImg.contentsLost()) {
                continue;
            }
            return snapshot;
        }
    }
    public static void main(String[] args) {
        GraphicsConfiguration gc =
            GraphicsEnvironment.getLocalGraphicsEnvironment().
                getDefaultScreenDevice().getDefaultConfiguration();
        BufferedImage bImg = clearUsingXOR(gc);

        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                if (bImg.getRGB(x, y) != Color.BLACK.getRGB()) {
                    throw new RuntimeException("Clear using XOR is not" +
                        " working at x: " + x + " y: " + y);
                }
            }
        }
    }
}
