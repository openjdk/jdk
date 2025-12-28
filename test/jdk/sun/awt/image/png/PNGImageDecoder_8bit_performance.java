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
 * @summary This test makes sure the PNGImageProducer performs comparable to
 * ImageIO when reading an 8-bit non-interlaced png.
 */

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * This test makes sure an 8-bit PNG image is converted into a BufferedImage
 * in approximately the same amount of time whether we use ImageIO or
 * an ImageConsumer/ImageProducer.
 */
public class PNGImageDecoder_8bit_performance {

    interface Model {
        BufferedImage load(byte[] imagedata) throws Exception;
    }

    /**
     * This creates a BufferedImage using ImageIO.
     */
    static class ImageIOModel implements Model {

        @Override
        public BufferedImage load(byte[] imagedata) throws Exception {
            try (InputStream in = new ByteArrayInputStream(imagedata)) {
                return ImageIO.read(in);
            }
        }
    }

    /**
     * This creates a BufferedImage using an ImageConsumer and ImageProducer.
     */
    static class ImageConsumerModel implements Model {

        @Override
        public BufferedImage load(byte[] imagedata) throws Exception {
            Image img = Toolkit.getDefaultToolkit().createImage(imagedata);
            return createBufferedImage(img);
        }
    }

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
        for (int squareSize = 4_000; squareSize <= 10_000;
             squareSize += 2_000) {
            byte[] imagedata = createImageData(squareSize);

            Model[] models = new Model[]{
                    new ImageIOModel(),
                    new ImageConsumerModel()
            };

            BufferedImage expected = models[0].load(imagedata);
            BufferedImage actual = models[1].load(imagedata);

            testCorrectness(expected, actual);

            // both of these constants are arbitrary. IMO they help demonstrate
            // the problem with reasonable accuracy & without excess waiting

            // run our test sampleCount-many times, and only report the median:
            int sampleCount = 7;

            // each sample creates the image loopCount-many times
            int loopCount = 10;

            long[][] samples = new long[models.length][sampleCount];
            for (int sampleIndex = 0; sampleIndex < sampleCount;
                 sampleIndex++) {
                for (int modelIndex = 0; modelIndex < models.length;
                     modelIndex++) {

                    long t = System.currentTimeMillis();
                    for (int b = 0; b < loopCount; b++) {
                        models[modelIndex].load(imagedata).flush();
                    }
                    t = System.currentTimeMillis() - t;
                    samples[modelIndex][sampleIndex] = t;
                }
            }
            long[] results = new long[models.length];
            long firstMedian = -1;
            for (int modelIndex = 0; modelIndex < models.length;
                 modelIndex++) {
                long[] modelSamples = samples[modelIndex];
                Arrays.sort(modelSamples);
                long median = modelSamples[modelSamples.length / 2];
                results[modelIndex] = median;
            }

            System.out.println();
            System.out.println("Square Size\t%\tImageIOModel\t" +
                    "ImageConsumerModel");

            StringBuilder sb = new StringBuilder(Integer.toString(squareSize));
            int imageConsumerPercentRelImageIO = Math.round(
                    results[1] * 100f / results[0]);
            sb.append("\t" + imageConsumerPercentRelImageIO);

            for (int a = 0; a < results.length; a++) {
                sb.append("\t" + results[a]);
            }

            System.out.println(sb);

            System.out.println("The ImageConsumer approach took " +
                    imageConsumerPercentRelImageIO + "% of the time the " +
                    "ImageIO approach took.");

            // in my tests the MINIMUM percent that we saw before this
            // enhancement was 109.9% and the MAXIMUM we saw after this
            // enhancement was 104.4%, so a midway point is 107%. We should
            // always be under 107%

            if (imageConsumerPercentRelImageIO > 107) {
                throw new Error("The ImageConsumer model should always take " +
                        "close to 100% of what the ImageIO model takes.");
            }
        }
    }

    /**
     * Create a large sample image stored as an 8-bit PNG.
     *
     * @return the byte representation of the PNG image.
     */
    private static byte[] createImageData(int squareSize) throws Exception {
        BufferedImage bi = new BufferedImage(squareSize, squareSize,
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

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(bi, "png", out);
            return out.toByteArray();
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
            throw new Error();
        }
        if (expected.getHeight() != actual.getHeight()) {
            throw new Error();
        }
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                int argb1 = expected.getRGB(x, y);
                int argb2 = actual.getRGB(x, y);
                if (argb1 != argb2) {
                    throw new Error("x = " + x + ", y = " + y);
                }
            }
        }
    }
}
