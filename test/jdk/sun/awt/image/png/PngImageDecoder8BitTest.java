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
 * @bug 8374377
 * @summary This test confirms the PNGImageProducer decodes 8-bit interlaced
 * and non-interlaced PNGs correctly.
 */

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * The proposed change for 8374377 affects how 8-bit PNGs are decoded.
 * So this test confirms that 8-bit PNGs (both interlaced and non-interlaced)
 * are still decoded by the PNGImageDecoder so they match what ImageIO decodes.
 *
 * This test has never failed.
 */
public class PngImageDecoder8BitTest {

    static BufferedImage createBufferedImage(Image img)
            throws ExecutionException, InterruptedException {
        CompletableFuture<BufferedImage> future = new CompletableFuture<>();
        img.getSource().startProduction(new ImageConsumer() {
            private int imageWidth, imageHeight;
            private BufferedImage bi;

            @Override
            public void setDimensions(int width, int height) {
                imageWidth = width;
                imageHeight = height;
            }

            @Override
            public void setProperties(Hashtable<?, ?> props) {
                // intentionally empty
            }

            @Override
            public void setColorModel(ColorModel model) {
                // intentionally empty
            }

            @Override
            public void setHints(int hintflags) {
                // intentionally empty
            }

            @Override
            public void setPixels(int x, int y, int w, int h, ColorModel model,
                                  byte[] pixels, int off, int scansize) {
                if (bi == null) {
                    bi = new BufferedImage(imageWidth, imageHeight,
                            BufferedImage.TYPE_BYTE_INDEXED,
                            (IndexColorModel) model);
                }

                if (w == imageWidth && h == imageHeight) {
                    // this is how interlaced PNGs are decoded:
                    bi.getRaster().setDataElements(0, 0,
                            imageWidth, imageHeight, pixels);
                    return;
                }

                if (h != 1) {
                    throw new UnsupportedOperationException(
                            "this test requires h = 1");
                }
                if (off != 0) {
                    throw new UnsupportedOperationException(
                            "this test requires off = 0");
                }

                bi.getRaster().setDataElements(x, y, w, 1, pixels);
            }

            @Override
            public void setPixels(int x, int y, int w, int h, ColorModel model,
                                  int[] pixels, int off, int scansize) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void imageComplete(int status) {
                future.complete(bi);
            }
        });
        return future.get();
    }

    public static void main(String[] args) throws Exception {
        BufferedImage expected = createImageData();
        for (boolean interlace : new boolean[] { false, true} ) {
            System.out.println("Testing interlacing = "+ interlace);
            byte[] imageData = encodePNG(expected, interlace);

            Image i = Toolkit.getDefaultToolkit().createImage(imageData);
            BufferedImage actual = createBufferedImage(i);

            testCorrectness(expected, actual);
        }
        System.out.println("Confirmed that 8-bit PNGs decode correctly " +
                "whether we use interlacing or not.");
    }

    /**
     * Create a large sample image stored as an 8-bit PNG.
     */
    private static BufferedImage createImageData() {
        BufferedImage bi = new BufferedImage(6000, 6000,
                BufferedImage.TYPE_BYTE_INDEXED);
        Random r = new Random(0);
        Graphics2D g = bi.createGraphics();
        for (int a = 0; a < 20000; a++) {
            g.setColor(new Color(r.nextInt(0xffffff)));
            int radius = 10 + r.nextInt(90);
            g.fillOval(r.nextInt(bi.getWidth()), r.nextInt(bi.getHeight()),
                    radius, radius);
        }
        g.dispose();
        return bi;
    }

    /**
     * Encode an image as 8-bit PNG.
     */
    private static byte[] encodePNG(BufferedImage bi, boolean interlace)
            throws IOException {
        Iterator<ImageWriter> writers =
                ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No PNG writers found");
        }
        ImageWriter writer = writers.next();

        ImageWriteParam param = writer.getDefaultWriteParam();
        if (interlace) {
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
        }

        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             ImageOutputStream imageOut =
                     ImageIO.createImageOutputStream(byteOut)) {
            writer.setOutput(imageOut);
            writer.write(null, new IIOImage(bi, null, null), param);
            return byteOut.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    /**
     * This throws an Error if the two images are not identical.
     * <p>
     * This unit test is intended to accompany a performance enhancement for
     * PNGImageDecoder. This method makes sure the enhancement didn't cost us
     * any accuracy.
     */
    private static void testCorrectness(BufferedImage expected,
                                        BufferedImage actual) {
        if (expected.getWidth() != actual.getWidth()) {
            throw new RuntimeException("expected.getWidth() = " +
                    expected.getWidth() + ", actual.getWidth() = " +
                    actual.getWidth());
        }
        if (expected.getHeight() != actual.getHeight()) {
            throw new RuntimeException("expected.getHeight() = " +
                    expected.getHeight() + ", actual.getHeight() = " +
                    actual.getHeight());
        }
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                int argb1 = expected.getRGB(x, y);
                int argb2 = actual.getRGB(x, y);
                if (argb1 != argb2) {
                    throw new RuntimeException("x = " + x + ", y = " + y +
                            " argb1 = " + Integer.toUnsignedString(argb1, 16) +
                            " argb2 = " + Integer.toUnsignedString(argb2, 16));
                }
            }
        }
    }
}
