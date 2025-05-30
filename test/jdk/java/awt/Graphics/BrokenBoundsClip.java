/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8357299
 * @summary Verifies if Graphics copyArea doesn't copy any pixels
 *          when there is overflow
 * @run main BrokenBoundsClip
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;

public final class BrokenBoundsClip {

     public static final int SIZE = 100;

     public static void main(String[] args) {
         BufferedImage bi = new BufferedImage(SIZE, SIZE, TYPE_INT_RGB);

         Graphics2D g2d = bi.createGraphics();
         g2d.setColor(Color.RED);
         g2d.fillRect(SIZE / 2, SIZE / 2, SIZE / 2, SIZE / 2);

         g2d.copyArea(bi.getWidth() / 2, bi.getHeight() / 2,
                      Integer.MAX_VALUE , Integer.MAX_VALUE ,
                      -bi.getWidth() / 2, -bi.getHeight() / 2);
         int actual = bi.getRGB(0, 0);
         int expected = Color.RED.getRGB();
         if (actual != expected) {
             System.err.println("Actual:   " + Integer.toHexString(actual));
             System.err.println("Expected: " + Integer.toHexString(expected));
             throw new RuntimeException("Wrong color");
         }
     }
}
