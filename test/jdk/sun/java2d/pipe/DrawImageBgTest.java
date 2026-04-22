/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4532352
 * @summary This test verifies that the specified background color is rendered
 *          in the special case of:
 *          Graphics.drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
 *                             int sx1, int sy1, int sx2, int sy2,
 *                             Color bgColor, ImageObserver observer)
 *          where no scaling takes place because the source and destination
 *          bounds have the same width and height.
 */

import java.io.File;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class DrawImageBgTest {

    public static void main(String argv[]) throws Exception {

        int dx, dy, dw, dh;
        int sx, sy, sw, sh;

        int iw = 250, ih = 250;
        String sep = System.getProperty("file.separator");
        String dir = System.getProperty("test.src", ".");
        String prefix = dir+sep;
        BufferedImage img = ImageIO.read(new File(prefix + "duke.gif"));
        BufferedImage dest = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = dest.createGraphics();
        g.setColor(Color.blue);
        g.fillRect(0, 0, iw, ih);

        // source and destination dimensions are different, results in scaling
        dx = 10;
        dy = 10;
        dw = 100;
        dh = 200;
        sx = 10;
        sy = 10;
        sw = 50;
        sh = 100;
        g.drawImage(img,
                    dx, dy, dx + dw, dy + dh,
                    sx, sy, sx + sw, sy + sh,
                    Color.yellow, null);

        int pix1 = dest.getRGB(dx + 1, dy + 1);

        // source and destination dimensions are the same, no scaling
        dx = 120;
        dy = 10;
        sx = 10;
        sy = 10;
        sw = dw = 50;
        sh = dh = 100;
        g.drawImage(img,
                    dx, dy, dx + dw, dy + dh,
                    sx, sy, sx + sw, sy + sh,
                    Color.yellow, null);

        int pix2 = dest.getRGB(dx + 1, dy + 1);
        int yellow = Color.yellow.getRGB();

        if (pix1 != yellow || pix2 != yellow) {
            ImageIO.write(dest, "gif", new File("op.gif"));
            throw new RuntimeException("pix1=" + Integer.toHexString(pix1) +
                                       " pix2=" + Integer.toHexString(pix2));
        }
    }
}
