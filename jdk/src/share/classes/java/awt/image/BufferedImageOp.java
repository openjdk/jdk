/*
 * Copyright (c) 1997, 2006, Oracle and/or its affiliates. All rights reserved.
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

package java.awt.image;

import java.awt.geom.Rectangle2D;
import java.awt.geom.Point2D;
import java.awt.RenderingHints;

/**
 * This interface describes single-input/single-output
 * operations performed on <CODE>BufferedImage</CODE> objects.
 * It is implemented by <CODE>AffineTransformOp</CODE>,
 * <CODE>ConvolveOp</CODE>, <CODE>ColorConvertOp</CODE>, <CODE>RescaleOp</CODE>,
 * and <CODE>LookupOp</CODE>.  These objects can be passed into
 * a <CODE>BufferedImageFilter</CODE> to operate on a
 * <CODE>BufferedImage</CODE> in the
 * ImageProducer-ImageFilter-ImageConsumer paradigm.
 * <p>
 * Classes that implement this
 * interface must specify whether or not they allow in-place filtering--
 * filter operations where the source object is equal to the destination
 * object.
 * <p>
 * This interface cannot be used to describe more sophisticated operations
 * such as those that take multiple sources. Note that this restriction also
 * means that the values of the destination pixels prior to the operation are
 * not used as input to the filter operation.

 * @see BufferedImage
 * @see BufferedImageFilter
 * @see AffineTransformOp
 * @see BandCombineOp
 * @see ColorConvertOp
 * @see ConvolveOp
 * @see LookupOp
 * @see RescaleOp
 */
public interface BufferedImageOp {
    /**
     * Performs a single-input/single-output operation on a
     * <CODE>BufferedImage</CODE>.
     * If the color models for the two images do not match, a color
     * conversion into the destination color model is performed.
     * If the destination image is null,
     * a <CODE>BufferedImage</CODE> with an appropriate <CODE>ColorModel</CODE>
     * is created.
     * <p>
     * An <CODE>IllegalArgumentException</CODE> may be thrown if the source
     * and/or destination image is incompatible with the types of images       $
     * allowed by the class implementing this filter.
     *
     * @param src The <CODE>BufferedImage</CODE> to be filtered
     * @param dest The <CODE>BufferedImage</CODE> in which to store the results$
     *
     * @return The filtered <CODE>BufferedImage</CODE>.
     *
     * @throws IllegalArgumentException If the source and/or destination
     * image is not compatible with the types of images allowed by the class
     * implementing this filter.
     */
    public BufferedImage filter(BufferedImage src, BufferedImage dest);

    /**
     * Returns the bounding box of the filtered destination image.
     * An <CODE>IllegalArgumentException</CODE> may be thrown if the source
     * image is incompatible with the types of images allowed
     * by the class implementing this filter.
     *
     * @param src The <CODE>BufferedImage</CODE> to be filtered
     *
     * @return The <CODE>Rectangle2D</CODE> representing the destination
     * image's bounding box.
     */
    public Rectangle2D getBounds2D (BufferedImage src);

    /**
     * Creates a zeroed destination image with the correct size and number of
     * bands.
     * An <CODE>IllegalArgumentException</CODE> may be thrown if the source
     * image is incompatible with the types of images allowed
     * by the class implementing this filter.
     *
     * @param src The <CODE>BufferedImage</CODE> to be filtered
     * @param destCM <CODE>ColorModel</CODE> of the destination.  If null,
     * the <CODE>ColorModel</CODE> of the source is used.
     *
     * @return The zeroed destination image.
     */
    public BufferedImage createCompatibleDestImage (BufferedImage src,
                                                    ColorModel destCM);

    /**
     * Returns the location of the corresponding destination point given a
     * point in the source image.  If <CODE>dstPt</CODE> is specified, it
     * is used to hold the return value.
     * @param srcPt the <code>Point2D</code> that represents the point in
     * the source image
     * @param dstPt The <CODE>Point2D</CODE> in which to store the result
     *
     * @return The <CODE>Point2D</CODE> in the destination image that
     * corresponds to the specified point in the source image.
     */
    public Point2D getPoint2D (Point2D srcPt, Point2D dstPt);

    /**
     * Returns the rendering hints for this operation.
     *
     * @return The <CODE>RenderingHints</CODE> object for this
     * <CODE>BufferedImageOp</CODE>.  Returns
     * null if no hints have been set.
     */
    public RenderingHints getRenderingHints();
}
