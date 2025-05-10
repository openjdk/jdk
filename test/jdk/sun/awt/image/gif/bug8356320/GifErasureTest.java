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
 * @bug 8356320
 * @summary This test verifies that we enforce the transparent background
 * when the disposal code is DISPOSAL_BGCOLOR and the transparent pixel
 * index changes.
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.IndexColorModel;
import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.concurrent.Semaphore;

public class GifErasureTest {
    public static void main(String[] args) throws Exception {
        URL srcURL = GifErasureTest.class.getResource("leo.gif");
        BufferedImage[] frames = getFrames(srcURL, 3);

        if (new Color(frames[2].getRGB(20, 20), true).getAlpha() != 0) {
            throw new Error("The pixel at (20, 20) should be transparent.");
        }
    }

    private static BufferedImage[] getFrames(URL gifURL, int numberOfFrames) {
        Image image = Toolkit.getDefaultToolkit().createImage(gifURL);
        ArrayList<BufferedImage> returnValue = new ArrayList<>(numberOfFrames);

        Semaphore semaphore = new Semaphore(1);
        semaphore.acquireUninterruptibly();
        image.getSource().startProduction(new ImageConsumer() {
            BufferedImage bi;
            int frameCtr = 0;

            @Override
            public void setDimensions(int width, int height) {
                bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
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
                    // we don't expect this to happen, but if something goes
                    // wrong nobody else will print our stacktrace for us:
                    e.printStackTrace();
                    throw e;
                }
            }

            @Override
            public void setPixels(int x, int y, int w, int h, ColorModel model, int[] pixels, int off, int scansize) {}

            @Override
            public void imageComplete(int status) {
                try {
                    frameCtr++;

                    BufferedImage copy = new BufferedImage(bi.getWidth(),
                            bi.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = copy.createGraphics();
                    g.drawImage(bi, 0, 0, null);
                    g.dispose();
                    returnValue.add(copy);

                    if (frameCtr == numberOfFrames) {
                        semaphore.release();
                        // if we don't detach this consumer the producer will
                        // loop forever
                        image.getSource().removeConsumer(this);
                        image.flush();
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        });

        semaphore.acquireUninterruptibly();

        return returnValue.toArray(new BufferedImage[0]);
    }
}
