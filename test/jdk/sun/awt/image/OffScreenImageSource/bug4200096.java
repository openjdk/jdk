/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageObserver;
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * @test
 * @bug 4200096
 * @summary OffScreenImageSource.removeConsumer can cause NullPointerException
 * @author Jeremy Wood
 */

public class bug4200096 {
    public static void main(String[] args) {
        // OffScreenImageSourceTest wraps image production in a try/catch.
        // It catches NullPointerExceptions and prints them to System.err.
        // So here we override System.err to intercept them and fail this test:
        System.setErr(new PrintStream(System.err) {
            @Override
            public void println(Object x) {
                super.println(x);
                if (x instanceof NullPointerException e)
                    throw new RuntimeException(e);
            }
        });

        runImageDimensionTest();
        runImageConsumerTest();
    }

    /**
     * This adds an ImageObserver that is only interested in identifying the dimensions of an ImageProducer.
     * <p>
     * Once it has the dimensions: {@link java.awt.image.ImageObserver#imageUpdate(Image, int, int, int, int, int)}
     * returns false. This triggers the caller to remove the ImageObserver for us. And removing an ImageObserver
     * while an OffScreenImageSource is mid-production triggers the NPE that is JDK-4200096.
     * <p>
     * The expected behavior is for this method to complete without OffScreenImageSource throwing/catching a NPE.
     * <p>
     * What's interesting about this test is: we never even explicitly call {@link java.awt.image.ImageProducer#addConsumer(ImageConsumer)}
     * or {@link java.awt.image.ImageProducer#removeConsumer(ImageConsumer)} (ImageConsumer)}.
     * </p>
     */
    private static void runImageDimensionTest() {
        BufferedImage bufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Image img = bufferedImage.getScaledInstance(2, 2, Image.SCALE_SMOOTH);
        ImageObserver observer = new ImageObserver() {
            Integer imageWidth, imageHeight;
            @Override
            public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
                if ( (infoflags | ImageObserver.WIDTH) > 0) {
                    imageWidth = width;
                }
                if ( (infoflags | ImageObserver.HEIGHT) > 0) {
                    imageHeight = height;
                }

                if (imageWidth != null || imageHeight != null)
                    return false;
                return true;

            }
        };
        img.getWidth(observer);
    }

    /**
     * This test removes an ImageConsumer from an OffScreenImageSource at different notifications.
     *
     * The expected behavior is for the OffScreenImageSource to never throw/catch a NPE.
     */
    private static void runImageConsumerTest() {
        enum TestCase {
            SET_DIMENSIONS, SET_PROPERTIES, SET_COLOR_MODEL, SET_PIXELS, IMAGE_COMPLETE
        }

        for (TestCase testCase : TestCase.values()) {
            BufferedImage bufferedImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
            Image img = bufferedImage.getScaledInstance(20, 20, Image.SCALE_SMOOTH);

            ImageConsumer consumer = new ImageConsumer() {

                private void run(TestCase methodInvocation) {
                    if (!img.getSource().isConsumer(this))
                        throw new IllegalStateException();
                    if (testCase == methodInvocation)
                        img.getSource().removeConsumer(this);
                }

                @Override
                public void setDimensions(int width, int height) {
                    run(TestCase.SET_DIMENSIONS);
                }

                @Override
                public void setProperties(Hashtable<?, ?> props) {
                    run(TestCase.SET_PROPERTIES);
                }

                @Override
                public void setColorModel(ColorModel model) {
                    run(TestCase.SET_COLOR_MODEL);
                }

                @Override
                public void setHints(int hintflags) {
                    // intentionally empty.
                    // OffScreenImageSource does not use this method, which is probably a separate (unrelated) bug
                }

                @Override
                public void setPixels(int x, int y, int w, int h, ColorModel model, byte[] pixels, int off, int scansize) {
                    throw new UnsupportedOperationException("this test should use int[] pixels");
                }

                @Override
                public void setPixels(int x, int y, int w, int h, ColorModel model, int[] pixels, int off, int scansize) {
                    if (y == 5) {
                        run(TestCase.SET_PIXELS);
                    }
                }

                @Override
                public void imageComplete(int status) {
                    run(TestCase.IMAGE_COMPLETE);
                }
            };

            img.getSource().startProduction(consumer);

            if (img.getSource().isConsumer(consumer)) {
                // this confirms our calls to .removeConsumer above were being invoked as expected
                throw new IllegalStateException("This test is not executing as expected.");
            }
        }
    }
}