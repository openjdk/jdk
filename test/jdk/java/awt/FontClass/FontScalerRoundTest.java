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

/*
 * @test
 * @bug 8299255
 * @summary Verify no round error in Font scaling
 */

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public class FontScalerRoundTest {
    public static void main(String[] args) {
        final double SCALE = 4096.0;
        final double STEP = 0.0001;
        final double LIMIT = STEP * 100.0;

        BufferedImage img = new BufferedImage(100, 100,
                                    BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        FontRenderContext frc = g2d.getFontRenderContext();

        Font font = new Font(Font.DIALOG, Font.PLAIN, 1);
        float h1 = getScaledHeight(font, frc, SCALE);
        float h2 = getScaledHeight(font, frc, SCALE + STEP);
        float diff = Math.abs(h1 - h2);

        if (diff > LIMIT) {
            throw new RuntimeException("Font metrix had round error " +
                                       h1 + "," + h2);
        }
    }

    private static float getScaledHeight(Font font,
                                         FontRenderContext frc,
                                         double scale) {
        AffineTransform at = new AffineTransform(scale, 0.0, 0.0, scale,
                                                 0.0, 0.0);
        Font transFont = font.deriveFont(at);
        LineMetrics m = transFont.getLineMetrics("0", frc);
        return m.getHeight();
    }
}

