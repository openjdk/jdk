/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8337681
 * @summary Test that raster use optimization does not cause any regressions.
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

public class RasterReuseWriteTest {

    public static void main(String[] args) throws Exception {
        test(BufferedImage.TYPE_INT_RGB);
        test(BufferedImage.TYPE_INT_ARGB);
        test(BufferedImage.TYPE_INT_ARGB_PRE);
        test(BufferedImage.TYPE_4BYTE_ABGR);
        test(BufferedImage.TYPE_4BYTE_ABGR_PRE);
    }

    private static void test(int type) throws Exception {

        // swaps blue and red
        int bands = (type == BufferedImage.TYPE_INT_RGB ? 3 : 4);
        int[] sourceBands = bands == 3 ? new int[] { 2, 1, 0 } :
                                         new int[] { 2, 1, 0, 3 };

        // test writing a BufferedImage without source bands
        BufferedImage img1 = createImage(256, 256, type);
        byte[] bytes1 = writePng(img1, null);
        BufferedImage img2 = ImageIO.read(new ByteArrayInputStream(bytes1));
        compare(img1, img2, false);

        // test writing a BufferedImage with source bands
        BufferedImage img3 = createImage(256, 256, type);
        byte[] bytes3 = writePng(img3, sourceBands);
        BufferedImage img4 = ImageIO.read(new ByteArrayInputStream(bytes3));
        compare(img3, img4, true);

        // test writing a non-BufferedImage with source bands and one tile
        RenderedImage img5 = toTiledImage(img1, 256);
        byte[] bytes5 = writePng(img5, sourceBands);
        BufferedImage img6 = ImageIO.read(new ByteArrayInputStream(bytes5));
        compare(img5, img6, true);

        // test writing a non-BufferedImage with source bands and multiple tiles
        RenderedImage img7 = toTiledImage(img1, 128);
        byte[] bytes7 = writePng(img7, sourceBands);
        BufferedImage img8 = ImageIO.read(new ByteArrayInputStream(bytes7));
        compare(img7, img8, true);
    }

    private static BufferedImage createImage(int w, int h, int type) throws Exception {
        BufferedImage img = new BufferedImage(w, h, type);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.GREEN);
        g2d.drawRect(20, 20, 100, 50);
        g2d.setColor(Color.RED);
        g2d.drawRect(80, 10, 100, 40);
        g2d.setColor(Color.BLUE);
        g2d.fillRect(40, 60, 120, 30);
        g2d.dispose();
        return img;
    }

    private static byte[] writePng(RenderedImage img, int[] sourceBands) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setSourceBands(sourceBands);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageOutputStream stream = new MemoryCacheImageOutputStream(baos);
        writer.setOutput(stream);
        writer.write(null, new IIOImage(img, null, null), param);
        writer.dispose();
        stream.flush();
        return baos.toByteArray();
    }

    private static void compare(RenderedImage img1, RenderedImage img2, boolean blueAndRedSwapped) {
        int[] pixels1 = getRgbPixels(img1);
        int[] pixels2 = getRgbPixels(img2);
        for (int i = 0; i < pixels1.length; i++) {
            int expected;
            if (blueAndRedSwapped && pixels1[i] == 0xFFFF0000) {
                expected = 0xFF0000FF; // red -> blue
            } else if (blueAndRedSwapped && pixels1[i] == 0xFF0000FF) {
                expected = 0xFFFF0000; // blue -> red
            } else {
                expected = pixels1[i]; // no change
            }
            int actual = pixels2[i];
            if (actual != expected) {
                throw new RuntimeException("Pixel " + i + ": expected " +
                    Integer.toHexString(expected) + ", but got " +
                    Integer.toHexString(actual));
            }
        }
    }

    private static int[] getRgbPixels(RenderedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (img instanceof BufferedImage bi) {
            return bi.getRGB(0, 0, w, h, null, 0, w);
        } else {
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = bi.createGraphics();
            g2d.drawRenderedImage(img, new AffineTransform());
            g2d.dispose();
            return bi.getRGB(0, 0, w, h, null, 0, w);
        }
    }

    private static RenderedImage toTiledImage(BufferedImage img, int tileSize) throws Exception {

        // write to TIFF
        ImageWriter writer = ImageIO.getImageWritersByFormatName("tiff").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setTilingMode(ImageWriteParam.MODE_EXPLICIT);
        param.setTiling(tileSize, tileSize, 0, 0);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageOutputStream stream = new MemoryCacheImageOutputStream(baos);
        writer.setOutput(stream);
        writer.write(null, new IIOImage(img, null, null), param);
        writer.dispose();
        stream.flush();
        byte[] bytes = baos.toByteArray();

        // read from TIFF
        ImageReader reader = ImageIO.getImageReadersByFormatName("tiff").next();
        ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
        reader.setInput(input);
        RenderedImage ri = reader.readAsRenderedImage(0, null);
        if (ri instanceof BufferedImage) {
            throw new RuntimeException("Unexpected BufferedImage");
        }
        int tw = ri.getTileWidth();
        int th = ri.getTileHeight();
        if (tw != tileSize || th != tileSize) {
            throw new RuntimeException("Expected tile size " + tileSize +
                ", but found " + tw + "x" + th);
        }
        return ri;
    }
}
