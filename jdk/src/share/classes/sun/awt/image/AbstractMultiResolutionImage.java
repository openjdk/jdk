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

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.*;

/**
 * This class provides default implementations for the
 * <code>MultiResolutionImage</code> interface. The developer needs only
 * to subclass this abstract class and define the <code>getResolutionVariant</code>,
 * <code>getResolutionVariants</code>, and <code>getBaseImage</code> methods.
 *
 *
 * For example,
 * {@code
 * public class CustomMultiResolutionImage extends AbstractMultiResolutionImage {
 *
 *     int baseImageIndex;
 *     Image[] resolutionVariants;
 *
 *     public CustomMultiResolutionImage(int baseImageIndex,
 *             Image... resolutionVariants) {
 *          this.baseImageIndex = baseImageIndex;
 *          this.resolutionVariants = resolutionVariants;
 *     }
 *
 *     @Override
 *     public Image getResolutionVariant(float logicalDPIX, float logicalDPIY,
 *             float baseImageWidth, float baseImageHeight,
 *             float destImageWidth, float destImageHeight) {
 *         // return a resolution variant based on the given logical DPI,
 *         // base image size, or destination image size
 *     }
 *
 *     @Override
 *     public List<Image> getResolutionVariants() {
 *         return Arrays.asList(resolutionVariants);
 *     }
 *
 *     protected Image getBaseImage() {
 *         return resolutionVariants[baseImageIndex];
 *     }
 * }
 * }
 *
 * @see java.awt.Image
 * @see java.awt.image.MultiResolutionImage
 *
 * @since 1.9
 */
public abstract class AbstractMultiResolutionImage extends java.awt.Image
        implements MultiResolutionImage {

    /**
     * @inheritDoc
     */
    @Override
    public int getWidth(ImageObserver observer) {
        return getBaseImage().getWidth(null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public int getHeight(ImageObserver observer) {
        return getBaseImage().getHeight(null);
    }

    /**
     * @inheritDoc
     */
    @Override
    public ImageProducer getSource() {
        return getBaseImage().getSource();
    }

    /**
     * @inheritDoc
     */
    @Override
    public Graphics getGraphics() {
        return getBaseImage().getGraphics();

    }

    /**
     * @inheritDoc
     */
    @Override
    public Object getProperty(String name, ImageObserver observer) {
        return getBaseImage().getProperty(name, observer);
    }

    /**
     * @return base image
     */
    protected abstract Image getBaseImage();
}
