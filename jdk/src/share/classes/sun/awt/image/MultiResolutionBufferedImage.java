/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Graphics;
import java.awt.geom.Dimension2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class MultiResolutionBufferedImage extends BufferedImage
        implements MultiResolutionImage {

    private final BiFunction<Integer, Integer, Image> mapper;
    private final Dimension2D[] sizes;
    private int availableInfo;

    public MultiResolutionBufferedImage(Image baseImage,
            Dimension2D[] sizes, BiFunction<Integer, Integer, Image> mapper) {
        super(baseImage.getWidth(null), baseImage.getHeight(null),
                BufferedImage.TYPE_INT_ARGB_PRE);
        this.sizes = sizes;
        this.mapper = mapper;
        this.availableInfo = getInfo(baseImage);
        Graphics g = getGraphics();
        g.drawImage(baseImage, 0, 0, null);
        g.dispose();
    }

    @Override
    public Image getResolutionVariant(int width, int height) {
        int baseWidth = getWidth();
        int baseHeight = getHeight();

        if (baseWidth == width && baseHeight == height) {
            return this;
        }

        ImageCache cache = ImageCache.getInstance();
        ImageCacheKey key = new ImageCacheKey(this, width, height);
        Image resolutionVariant = cache.getImage(key);
        if (resolutionVariant == null) {
            resolutionVariant = mapper.apply(width, height);
            cache.setImage(key, resolutionVariant);
            preload(resolutionVariant, availableInfo);
        }

        return resolutionVariant;
    }

    @Override
    public List<Image> getResolutionVariants() {
        return Arrays.stream(sizes).map((Function<Dimension2D, Image>) size
                -> getResolutionVariant((int) size.getWidth(),
                        (int) size.getHeight())).collect(Collectors.toList());
    }

    public MultiResolutionBufferedImage map(Function<Image, Image> mapper) {
        return new MultiResolutionBufferedImage(mapper.apply(this), sizes,
                (width, height) ->
                        mapper.apply(getResolutionVariant(width, height)));
    }

    @Override
    public int getWidth(ImageObserver observer) {
        availableInfo |= ImageObserver.WIDTH;
        return super.getWidth(observer);
    }

    @Override
    public int getHeight(ImageObserver observer) {
        availableInfo |= ImageObserver.HEIGHT;
        return super.getHeight(observer);
    }

    @Override
    public Object getProperty(String name, ImageObserver observer) {
        availableInfo |= ImageObserver.PROPERTIES;
        return super.getProperty(name, observer);
    }

    private static int getInfo(Image image) {
        if (image instanceof ToolkitImage) {
            return ((ToolkitImage) image).getImageRep().check(
                    (img, infoflags, x, y, w, h) -> false);
        }
        return 0;
    }

    private static void preload(Image image, int availableInfo) {
        if (image instanceof ToolkitImage) {
            ((ToolkitImage) image).preload(new ImageObserver() {
                int flags = availableInfo;

                @Override
                public boolean imageUpdate(Image img, int infoflags,
                        int x, int y, int width, int height) {
                    flags &= ~infoflags;
                    return (flags != 0) && ((infoflags
                            & (ImageObserver.ERROR | ImageObserver.ABORT)) == 0);
                }
            });
        }
    }

    private static class ImageCacheKey implements ImageCache.PixelsKey {

        private final int pixelCount;
        private final int hash;

        private final int w;
        private final int h;
        private final Image baseImage;

        ImageCacheKey(final Image baseImage,
                final int w, final int h) {
            this.baseImage = baseImage;
            this.w = w;
            this.h = h;
            this.pixelCount = w * h;
            hash = hash();
        }

        @Override
        public int getPixelCount() {
            return pixelCount;
        }

        private int hash() {
            int hash = baseImage.hashCode();
            hash = 31 * hash + w;
            hash = 31 * hash + h;
            return hash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ImageCacheKey) {
                ImageCacheKey key = (ImageCacheKey) obj;
                return baseImage == key.baseImage && w == key.w && h == key.h;
            }
            return false;
        }
    }
}