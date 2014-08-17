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
import java.util.List;

/**
 * This interface is designed to provide a set of images at various resolutions.
 *
 * The <code>MultiResolutionImage</code> interface should be implemented by any
 * class whose instances are intended to provide image resolution variants
 * according to the given image width and height.
 *
 * For example,
 * <pre>
 * {@code
 *  public class ScaledImage extends BufferedImage
 *         implements MultiResolutionImage {
 *
 *    @Override
 *    public Image getResolutionVariant(int width, int height) {
 *      return ((width <= getWidth() && height <= getHeight()))
 *             ? this : highResolutionImage;
 *    }
 *
 *    @Override
 *    public List<Image> getResolutionVariants() {
 *        return Arrays.asList(this, highResolutionImage);
 *    }
 *  }
 * }</pre>
 *
 * It is recommended to cache image variants for performance reasons.
 *
 * <b>WARNING</b>: This class is an implementation detail. This API may change
 * between update release, and it may even be removed or be moved in some other
 * package(s)/class(es).
 */
public interface MultiResolutionImage {

    /**
     * Provides an image with necessary resolution which best fits to the given
     * image width and height.
     *
     * @param width the desired image resolution width.
     * @param height the desired image resolution height.
     * @return image resolution variant.
     *
     * @since 1.8
     */
    public Image getResolutionVariant(int width, int height);

    /**
     * Gets list of all resolution variants including the base image
     *
     * @return list of resolution variants.
     * @since 1.8
     */
    public List<Image> getResolutionVariants();
}
