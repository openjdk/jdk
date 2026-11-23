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
 * @bug 6968794
 * @summary This test confirms the PNGImageProducer reports failures for
 * incomplete file streams.
 */

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageObserver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.Reference;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class PngImageDecoderPrematureEOFTest {
    boolean failed;
    byte[] pngFile;

    public static void main(String[] args) throws Exception {
        PngImageDecoderPrematureEOFTest t =
                new PngImageDecoderPrematureEOFTest();
        t.run();
    }

    private PngImageDecoderPrematureEOFTest() throws IOException {
        // The dimensions here matter. At 100x100px the PNGImageDecoder fails
        // with an IOException. At 105x105px the PNGImageDecoder fails with
        // a ArrayIndexOutOfBoundsException. The two are caught/handled
        // separately.

        BufferedImage sampleImage = new BufferedImage(100, 100,
                BufferedImage.TYPE_INT_RGB);

        // give the image random data that won't compress well:
        Random random = new Random(0);
        for (int y = 0; y < sampleImage.getHeight(); y++) {
            for (int x = 0; x < sampleImage.getWidth(); x++) {
                sampleImage.setRGB(x, y, random.nextInt(0xffffff));
            }
        }

        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream()) {
            ImageIO.write(sampleImage, "png", byteOut);
            pngFile = byteOut.toByteArray();
        }

        System.out.println("Created PNG file: " + pngFile.length + " bytes");
    }

    private void run() throws Exception {
        testImageObserver();
        testImageConsumer();

        if (failed) {
            throw new RuntimeException("Test failed; see log for details");
        }
    }

    /**
     * The ImageObserver was receiving an ALLBITS notification instead of an
     * ERROR notification.
     * <p>
     * The JDK-6968794 ticket focuses on the ImageObserver.
     */
    private void testImageObserver() throws ExecutionException,
            InterruptedException {
        System.out.println("\n#### testImageObserver ####");

        // only read 50% of the file, which should result in an error:
        Image img = Toolkit.getDefaultToolkit().createImage(pngFile, 0,
                pngFile.length / 2);

        CompletableFuture flagsFuture = new CompletableFuture();
        ImageObserver observer = new ImageObserver() {
            @Override
            public boolean imageUpdate(Image img, int infoflags, int x, int y,
                                       int width, int height) {
                if ((infoflags & ALLBITS) != 0) {
                    System.out.println(
                        "Received infoflags containing ALLBITS; test failed");
                    failed = true;
                    flagsFuture.complete(infoflags);
                } else if ((infoflags & ERROR) != 0) {
                    System.out.println(
                        "Received infoflags containing ERROR; test passed");
                    flagsFuture.complete(infoflags);
                }

                return true;
            }
        };
        img.getWidth(observer);
        flagsFuture.get();
        Reference.reachabilityFence(observer);
    }

    /**
     * The ImageConsumer similarly received an imageComplete notification that
     * the image finished without an error.
     */
    private void testImageConsumer() throws ExecutionException,
            InterruptedException {
        System.out.println("\n#### testImageConsumer ####");

        // only read 50% of the file, which should result in an error:
        Image img = Toolkit.getDefaultToolkit().createImage(pngFile, 0,
                pngFile.length / 2);

        CompletableFuture<Integer> completionConstantFuture =
                new CompletableFuture<>();

        // these are pixel rows we received at least partial pixels for
        Collection<Integer> rows = new TreeSet<>();
        ImageConsumer consumer = new ImageConsumer() {
            @Override
            public void setDimensions(int width, int height) {
                // intentionally empty
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
            public void setPixels(int x, int y, int w, int h,
                                  ColorModel model, byte[] pixels, int off,
                                  int scansize) {
                for (int a = 0; a < h; a++) {
                    rows.add(y + a);
                }
            }

            @Override
            public void setPixels(int x, int y, int w, int h,
                                  ColorModel model, int[] pixels, int off,
                                  int scansize) {
                for (int a = 0; a < h; a++) {
                    rows.add(y + a);
                }
            }

            @Override
            public void imageComplete(int status) {
                completionConstantFuture.complete(status);
            }
        };
        img.getSource().startProduction(consumer);
        int completion = completionConstantFuture.get();

        System.out.println("Read " + rows.size() +
                " rows out of a 100px-tall image.");

        if (completion == ImageConsumer.IMAGEERROR) {
            System.out.println(
                    "imageComplete(IMAGEERROR) called, test passed");
            return;
        }
        failed = true;
        if (completion == ImageConsumer.SINGLEFRAMEDONE) {
            System.out.println(
                    "failed: imageComplete(SINGLEFRAMEDONE) called, " +
                            "IMAGEERROR expected");
        } else if (completion == ImageConsumer.STATICIMAGEDONE) {
            System.out.println(
                    "failed: imageComplete(STATICIMAGEDONE) called, " +
                            "IMAGEERROR expected");
        } else if (completion == ImageConsumer.IMAGEABORTED) {
            System.out.println(
                    "failed: imageComplete(IMAGEABORTED) called, " +
                            "IMAGEERROR expected");
        } else {
            System.out.println(
                    "failed: imageComplete(" + completion +
                            ") called, IMAGEERROR expected");
        }
    }
}
