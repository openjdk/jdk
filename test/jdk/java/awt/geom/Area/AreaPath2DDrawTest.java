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

/*
 * @test
 * @bug     8386576
 * @summary Checks that when we draw a circle using area of approximated
 *          straight line segments, no horizontal spurious lines are
 *          drawn within the circle.
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

public class AreaPath2DDrawTest {
    public static void main(String[] args) throws Exception {
        int size = 500;
        int segments = 2000;
        double center = size / 2.0;
        double radius = size / 2.0;

        // Build a simple closed polygon: a circle approximated by straight
        // line segments.
        Path2D.Double path = new Path2D.Double();
        path.moveTo(center + radius, center);

        for (int i = 1; i < segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            path.lineTo(
                center + Math.cos(angle) * radius,
                center + Math.sin(angle) * radius);
        }

        path.closePath();

        BufferedImage image =
            new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, size, size);
        g.setColor(Color.BLACK);
        g.draw(new Area(path));
        g.dispose();

        // We are drawing a circle with diameter of 500, check for horizontal
        // spurious lines within the circle
        for (int y = 10; y < size - 10; y++) {
            int color = image.getRGB(size / 2, y);
            if (color != Color.WHITE.getRGB()) {
                throw new RuntimeException("Seeing horizontal spurious" +
                    " line at y: " + y);
            }
        }
    }
}
