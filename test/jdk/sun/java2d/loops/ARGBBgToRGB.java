/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4238978
 * @summary This test verifies that the correct blitting loop is being used.
 *          The correct output should have a yellow border on the top and
 *          left sides of a red box.  The incorrect output would have only
 *          a red box -- no yellow border."
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class ARGBBgToRGB {

    public static void main(String[] argv) {
        BufferedImage bi = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D big = bi.createGraphics();
        big.setColor(Color.red);
        big.fillRect(30, 30, 150, 150);

        BufferedImage bi2 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D big2 = bi2.createGraphics();
        big2.drawImage(bi, 0, 0, Color.yellow, null);

        int expectYellowPix = bi2.getRGB(0, 0);
        int expectRedPix = bi2.getRGB(50, 50);
        if ((expectYellowPix != Color.yellow.getRGB()) ||
            (expectRedPix != Color.red.getRGB()))
        {
           throw new RuntimeException("Unexpected colors " + expectYellowPix + " " + expectRedPix);
        }
    }
}
