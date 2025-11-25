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
 * @bug     8351110
 * @summary Test verifies that when a JFIF thumbnail may exceed 65535 bytes
 *          we still write a valid JPEG file by clipping the thumbnail.
 * @run     main WriteJPEGThumbnailTest
 */

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

public class WriteJPEGThumbnailTest {

    private static void assertEquals(int expected, int observed) {
        if (expected != observed) {
            throw new Error("expected " + expected + ", but observed " + observed);
        }
    }

    public static void main(String[] args) throws Exception {
        // this always passed. 21800 * 3 = 65400, which fits in a 65535-byte segment.
        boolean b1 = new WriteJPEGThumbnailTest(100, 218).run();

        // this failed prior to resolving 8351110. 21900 * 3 = 65700, which is too large
        // for a JPEG segment. Now we clip the thumbnail to make it fit. (Previously
        // we wrote a corrupt JPEG file.)
        boolean b2 = new WriteJPEGThumbnailTest(100, 219).run();

        if (!(b1 && b2)) {
            System.err.println("Test failed");
            throw new Error("Test failed");
        }
    }

    final int thumbWidth;
    final int thumbHeight;

    public WriteJPEGThumbnailTest(int thumbWidth, int thumbHeight) {
        this.thumbWidth = thumbWidth;
        this.thumbHeight = thumbHeight;
    }

    public boolean run() throws Exception {
        System.out.println("Testing thumbnail " + thumbWidth + "x" + thumbHeight + "...");
        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            BufferedImage thumbnail = writeImage(byteOut);
            byte[] jpegData = byteOut.toByteArray();
            ImageReader reader = getJPEGImageReader();
            ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(jpegData));
            reader.setInput(stream);
            assertEquals(1, reader.getNumThumbnails(0));

            // we may have a subset of our original thumbnail, that's OK
            BufferedImage readThumbnail = reader.readThumbnail(0, 0);
            for (int y = 0; y < readThumbnail.getHeight(); y++) {
                for (int x = 0; x < readThumbnail.getWidth(); x++) {
                    int rgb1 = thumbnail.getRGB(x, y);
                    int rgb2 = readThumbnail.getRGB(x, y);
                    assertEquals(rgb1, rgb2);
                }
            }
            System.out.println("\tTest passed");
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private BufferedImage writeImage(OutputStream out) throws IOException {
        BufferedImage thumbnail = createImage(thumbWidth, thumbHeight);
        BufferedImage bi = createImage(thumbnail.getWidth() * 10, thumbnail.getHeight() * 10);

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();

        try (ImageOutputStream outputStream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(outputStream);

            // Write the main image
            IIOImage img = new IIOImage(bi, List.of(thumbnail), null);
            writer.write(null, img, null);
        } finally {
            writer.dispose();
        }
        return thumbnail;
    }

    private static BufferedImage createImage(int width, int height) {
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        double sx = ((double)width) / 1000.0;
        double sy = ((double)height) / 1000.0;
        g.transform(AffineTransform.getScaleInstance(sx, sy));
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.GREEN);
        g.fillRect(900, 0, 900, 100);
        g.setColor(Color.ORANGE);
        g.fillRect(0, 900, 100, 100);
        g.setColor(Color.MAGENTA);
        g.fillRect(900, 900, 100, 100);
        g.dispose();
        return bi;
    }

    private static ImageReader getJPEGImageReader() {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
        ImageReader reader;
        while (readers.hasNext()) {
            reader = readers.next();
            if (reader.canReadRaster()) {
                return reader;
            }
        }
        return null;
    }
}
