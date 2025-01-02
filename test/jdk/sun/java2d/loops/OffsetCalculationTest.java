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
  @test
  @bug 4236576
  @summary tests that a BufferedImage in TYPE_3BYTE_BGR format is correctly
           drawn when there is an offset between the Graphics clip bounds
           and the clip box of the underlying device context.
  @run main OffsetCalculationTest
*/

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;

public class OffsetCalculationTest {

    public static void main(String[] args) {
        BufferedImage srcImage = new BufferedImage(500, 500, BufferedImage.TYPE_3BYTE_BGR);

        DataBuffer buffer = srcImage.getRaster().getDataBuffer();
        for (int i = 2; i < buffer.getSize(); i+=3) {
            // setting each pixel to blue via the data buffer elements.
            buffer.setElem(i - 2, 0xff);
            buffer.setElem(i - 1, 0);
            buffer.setElem(i, 0);
        }

        int w = 200, h = 200;
        BufferedImage destImage = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = destImage.createGraphics();
        Rectangle r = new Rectangle(0, 0, w, h);
        g.setClip(r.x - 1, r.y, r.width + 1, r.height);
        g.drawImage(srcImage, 0, 0, null);

        int bluepix = Color.blue.getRGB();
        for (int y = 0; y < w; y++) {
            for (int x = 0; x < h; x++) {
                if (destImage.getRGB(x, y) != bluepix) {
                     throw new RuntimeException("Not Blue");
                }
            }
        }
    }
}
