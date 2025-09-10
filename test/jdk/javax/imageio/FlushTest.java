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
 * @bug 8364768
 * @summary Tests that the standard plugins flush the stream after writing a complete image.
 */

import static java.awt.Color.WHITE;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileCacheImageOutputStream;

public class FlushTest {

    static final int SZ = 1000;
    static BufferedImage bi;
    static final String[] FORMATS = { "jpg", "png", "gif", "tiff", "bmp", "wbmp" } ;
    static boolean failed = false;

    public static void main(String[] args) throws IOException {

        bi = new BufferedImage(SZ, SZ, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = bi.createGraphics();
        g2d.setPaint(WHITE);
        g2d.fillRect(0, 0, SZ, SZ);

        for (String f : FORMATS) {
            testWrite(f);
        }
        if (failed) {
           throw new RuntimeException("Stream sizes differ.");
        }
    }

    static void testWrite(String fmt) throws IOException {
        ImageWriter iw = ImageIO.getImageWritersBySuffix(fmt).next();
        System.out.println(iw);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileCacheImageOutputStream fcs = new FileCacheImageOutputStream(baos, null);
        iw.setOutput(fcs);
        iw.write(bi);
        int sz0 = baos.size();
        fcs.close();
        int sz1 = baos.size();
        System.out.println("fmt=" + fmt + " sizes=" + sz0 + ", " + sz1);
        if (sz0 != sz1) {
           failed = true;
        }
    }
}
