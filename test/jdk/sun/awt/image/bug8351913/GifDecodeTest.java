/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8351913
 * @summary Checks that sun.awt.image.GifImageDecoder decodes gifs like the
 *          ImageIO decoder does.
 */

import javax.imageio.ImageIO;
import javax.tools.Tool;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

public class GifDecodeTest {

    /**
     * This scans all folders in this directory and makes sure that the sun/ImageToolkit decoder
     * resembles ImageIO's decoder. This test only focuses on the first frame of the gif.
     */
    public static void main(String[] args) throws IOException {
        File dir = new File(System.getProperty("test.src", "."));
        boolean allTestsPassed = true;
        boolean anyTestsPassed = false;
        for (File gifFile : dir.listFiles((dir1, name) -> name.endsWith(".gif"))) {
            BufferedImage expectedFrame = ImageIO.read(gifFile);
            BufferedImage actualFrame = getFrame(gifFile, 0);

            // If images are added to this test dir: you should also visually inspect
            // the expected/actual results at least once. (That is: don't just assume
            // ImageIO is doing the correct thing; it's possible both implementations
            // are flawed.)

            expectedFrame = trimTransparentEdges(expectedFrame);
            actualFrame = trimTransparentEdges(actualFrame);

            boolean passed = testEquals(expectedFrame, actualFrame);
            if (!passed) {
                allTestsPassed = false;
                System.out.println("\tfailed");
            } else {
                anyTestsPassed = true;
                System.out.println("\tpassed");
            }
        }

        if (!allTestsPassed) {
            throw new Error("One or more tests failed.");
        } else  if (!anyTestsPassed) {
            throw new Error("This test did not identify any gif files to test in " + dir.getAbsolutePath());
        }
    }

    /**
     * Return a subimage of the argument that strips away transparent edges.
     */
    private static BufferedImage trimTransparentEdges(BufferedImage bi) {
        Point topLeft = null;
        Point bottomRight = null;
        for (int y = 0; y < bi.getHeight(); y++) {
            for (int x = 0; x < bi.getWidth(); x++) {
                int argb = bi.getRGB(x, y);
                int alpha = (argb >> 24) & 0xff;
                if (alpha > 128) {
                    if (topLeft == null) {
                        topLeft = new Point(x, y);
                        bottomRight = new Point(x, y);
                    } else {
                        topLeft.x = Math.min(x, topLeft.x);
                        topLeft.y = Math.min(y, topLeft.y);
                        bottomRight.x = Math.max(x, bottomRight.x);
                        bottomRight.y = Math.max(y, bottomRight.y);
                    }
                }
            }
        }
        return bi.getSubimage(topLeft.x, topLeft.y, bottomRight.x - topLeft.x, bottomRight.y - topLeft.y);
    }

    private static boolean testEquals(BufferedImage expectedImage, BufferedImage actualImage) {
        if (expectedImage.getWidth() != actualImage.getWidth()) {
            return false;
        }
        if (expectedImage.getHeight() != actualImage.getHeight()) {
            return false;
        }
        int tolerance = 0;

        for (int y = 0; y < expectedImage.getHeight(); y++) {
            for (int x = 0; x < expectedImage.getWidth(); x++) {
                int argb1 = expectedImage.getRGB(x, y);
                int argb2 = actualImage.getRGB(x, y);

                int a1 = (argb1 >> 24) & 0xff;
                int r1 = (argb1 >> 16) & 0xff;
                int g1 = (argb1 >> 8) & 0xff;
                int b1 = (argb1 >> 0) & 0xff;

                int a2 = (argb2 >> 24) & 0xff;
                int r2 = (argb2 >> 16) & 0xff;
                int g2 = (argb2 >> 8) & 0xff;
                int b2 = (argb2 >> 0) & 0xff;

                // transparency should be 0% or 100%
                if (a1 != a2) {
                    return false;
                }

                if (a1 == 255) {
                    if (Math.abs(r1 - r2) > tolerance) {
                        return false;
                    }
                    if (Math.abs(g1 - g2) > tolerance) {
                        return false;
                    }
                    if (Math.abs(b1 - b2) > tolerance) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static BufferedImage getFrame(File gifFile, int frameIndex) throws IOException {
        Image image = Toolkit.getDefaultToolkit().createImage(gifFile.toURI().toURL());
        AtomicReference<BufferedImage> returnValue = new AtomicReference<>();

        Semaphore semaphore = new Semaphore(1);
        semaphore.acquireUninterruptibly();
        image.getSource().startProduction(new ImageConsumer() {
            BufferedImage bi;
            int frameCtr = 0;

            @Override
            public void setDimensions(int width, int height) {
                bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                returnValue.set(bi);
            }

            @Override
            public void setProperties(Hashtable<?, ?> props) {}

            @Override
            public void setColorModel(ColorModel model) {}

            @Override
            public void setHints(int hintflags) {}

            @Override
            public void setPixels(int x, int y, int w, int h, ColorModel model, byte[] pixels, int off, int scansize) {
                try {
                    final int yMax = y + h;
                    final int xMax = x + w;

                    IndexColorModel icm = (IndexColorModel) model;
                    int[] colorModelRGBs = new int[icm.getMapSize()];
                    icm.getRGBs(colorModelRGBs);
                    int[] argbRow = new int[bi.getWidth()];

                    for (int y_ = y; y_ < yMax; y_++) {
                        int i = y_ * scansize + off;
                        for (int x_ = x; x_ < xMax; x_++, i++) {
                            int pixel = pixels[i] & 0xff;
                            argbRow[x_ - x] = colorModelRGBs[pixel];
                        }
                        bi.getRaster().setDataElements(x, y_, w, 1, argbRow);
                    }
                } catch (RuntimeException e) {
                    // we don't expect this to happen, but if something goes wrong nobody else
                    // will print our stacktrace for us:
                    e.printStackTrace();
                    throw e;
                }
            }

            @Override
            public void setPixels(int x, int y, int w, int h, ColorModel model, int[] pixels, int off, int scansize) {}

            @Override
            public void imageComplete(int status) {
                try {
                    if (frameCtr++ == frameIndex) {
                        semaphore.release();
                        // if we don't detach this consumer the producer will loop forever
                        image.getSource().removeConsumer(this);
                        image.flush();
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        });

        // wait for producer thread to finish:
        semaphore.acquireUninterruptibly();

        return returnValue.get();
    }
}
