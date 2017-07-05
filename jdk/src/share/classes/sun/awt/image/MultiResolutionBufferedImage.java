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
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class MultiResolutionBufferedImage extends BufferedImage
        implements MultiResolutionImage {

    Image[] resolutionVariants;
    int baseIndex;

    public MultiResolutionBufferedImage(int imageType, int baseIndex, Image... images) {
        super(images[baseIndex].getWidth(null), images[baseIndex].getHeight(null),
                imageType);
        this.baseIndex = baseIndex;
        this.resolutionVariants = images;
        Graphics g = getGraphics();
        g.drawImage(images[baseIndex], 0, 0, null);
        g.dispose();
        images[baseIndex] = this;
    }

    @Override
    public Image getResolutionVariant(int width, int height) {
        for (Image image : resolutionVariants) {
            if (width <= image.getWidth(null) && height <= image.getHeight(null)) {
                return image;
            }
        }
        return this;
    }

    @Override
    public List<Image> getResolutionVariants() {
        return Arrays.asList(resolutionVariants);
    }

    public MultiResolutionBufferedImage map(Function<Image, Image> mapper) {
        return new MultiResolutionBufferedImage(getType(), baseIndex,
                Arrays.stream(resolutionVariants).map(mapper)
                        .toArray(length -> new Image[length]));
    }
}
