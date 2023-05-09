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
import java.awt.image.ImageObserver;

/**
 * @test
 * @bug 4200096
 * @summary this real-world example detaches an ImageConsumer from an OSIS immediately after it learns the dimensions.
 * @author Jeremy Wood
 */

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
public class ImageSizeTest {
    public static void main(String[] args) throws Exception {
        try (AutoCloseable setup = bug4200096.setupTest(false)) {
            Image img = createAbstractImage();
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
    }

    /**
     * This creates a ToolkitImage, so it is not a BufferedImage.
     * <p>
     * This specific implementation happens to rely on scaling an existing
     * BufferedImage, because that seemed like an easy way to avoid bundling a
     * JPG/PNG with this unit test. But this return value still happens to be
     * a ToolkitImage, which is what a JPG/PNG would also be (when loaded
     * via the Toolkit class and not ImageIO).
     * </p>
     */
    private static Image createAbstractImage() {
        BufferedImage bufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Image img = bufferedImage.getScaledInstance(2, 2, Image.SCALE_SMOOTH);
        return img;
    }
}