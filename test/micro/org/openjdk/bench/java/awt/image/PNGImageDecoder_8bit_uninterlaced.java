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
package org.openjdk.bench.java.awt.image;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

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
import java.util.Hashtable;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 20)
@Fork(3)
@State(Scope.Thread)
public class PNGImageDecoder_8bit_uninterlaced {

    byte[] pngImageData;

    @Setup
    public void setup() throws Exception {
        pngImageData = createImageData(2_500);
    }

    @Benchmark
    public void measurePNGImageDecoder(Blackhole bh) throws Exception {
        Image img = Toolkit.getDefaultToolkit().createImage(pngImageData);
        BufferedImage bi = createBufferedImage(img);
        bi.flush();
        bh.consume(bi);
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
                if (h != 1)
                    throw new UnsupportedOperationException(
                            "this test expects sequential rows of pixels");
                if (off != 0)
                    throw new UnsupportedOperationException(
                            "this test expects the incoming pixels to start " +
                                    "at index zero");

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
}