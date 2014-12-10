/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package sun.awt.image;

import java.awt.Image;
import java.awt.image.ImageObserver;
import java.util.Arrays;
import java.util.List;
import sun.misc.SoftCache;

public class MultiResolutionToolkitImage extends ToolkitImage implements MultiResolutionImage {

    Image resolutionVariant;

    public MultiResolutionToolkitImage(Image lowResolutionImage, Image resolutionVariant) {
        super(lowResolutionImage.getSource());
        this.resolutionVariant = resolutionVariant;
    }

    @Override
    public Image getResolutionVariant(int width, int height) {
        return ((width <= getWidth() && height <= getHeight()))
                ? this : resolutionVariant;
    }

    public Image getResolutionVariant() {
        return resolutionVariant;
    }

    @Override
    public List<Image> getResolutionVariants() {
        return Arrays.<Image>asList(this, resolutionVariant);
    }

    private static final int BITS_INFO = ImageObserver.SOMEBITS
            | ImageObserver.FRAMEBITS | ImageObserver.ALLBITS;

    private static class ObserverCache {

        static final SoftCache INSTANCE = new SoftCache();
    }

    public static ImageObserver getResolutionVariantObserver(
            final Image image, final ImageObserver observer,
            final int imgWidth, final int imgHeight,
            final int rvWidth, final int rvHeight) {
        return getResolutionVariantObserver(image, observer,
                imgWidth, imgHeight, rvWidth, rvHeight, false);
    }

    public static ImageObserver getResolutionVariantObserver(
            final Image image, final ImageObserver observer,
            final int imgWidth, final int imgHeight,
            final int rvWidth, final int rvHeight, boolean concatenateInfo) {

        if (observer == null) {
            return null;
        }

        synchronized (ObserverCache.INSTANCE) {
            ImageObserver o = (ImageObserver) ObserverCache.INSTANCE.get(observer);

            if (o == null) {

                o = (Image resolutionVariant, int flags,
                        int x, int y, int width, int height) -> {

                            if ((flags & (ImageObserver.WIDTH | BITS_INFO)) != 0) {
                                width = (width + 1) / 2;
                            }

                            if ((flags & (ImageObserver.HEIGHT | BITS_INFO)) != 0) {
                                height = (height + 1) / 2;
                            }

                            if ((flags & BITS_INFO) != 0) {
                                x /= 2;
                                y /= 2;
                            }

                            if(concatenateInfo){
                                flags &= ((ToolkitImage) image).
                                        getImageRep().check(null);
                            }

                            return observer.imageUpdate(
                                    image, flags, x, y, width, height);
                        };

                ObserverCache.INSTANCE.put(observer, o);
            }
            return o;
        }
    }
}
