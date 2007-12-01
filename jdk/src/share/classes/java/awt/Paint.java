/*
 * Copyright 1997-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.awt;

import java.awt.image.ColorModel;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/**
 * This <code>Paint</code> interface defines how color patterns
 * can be generated for {@link Graphics2D} operations.  A class
 * implementing the <code>Paint</code> interface is added to the
 * <code>Graphics2D</code> context in order to define the color
 * pattern used by the <code>draw</code> and <code>fill</code> methods.
 * <p>
 * Instances of classes implementing <code>Paint</code> must be
 * read-only because the <code>Graphics2D</code> does not clone
 * these objects when they are set as an attribute with the
 * <code>setPaint</code> method or when the <code>Graphics2D</code>
 * object is itself cloned.
 * @see PaintContext
 * @see Color
 * @see GradientPaint
 * @see TexturePaint
 * @see Graphics2D#setPaint
 */

public interface Paint extends Transparency {
    /**
     * Creates and returns a {@link PaintContext} used to
     * generate the color pattern.
     * Since the ColorModel argument to createContext is only a
     * hint, implementations of Paint should accept a null argument
     * for ColorModel.  Note that if the application does not
     * prefer a specific ColorModel, the null ColorModel argument
     * will give the Paint implementation full leeway in using the
     * most efficient ColorModel it prefers for its raster processing.
     * <p>
     * Since the API documentation was not specific about this in
     * releases before 1.4, there may be implementations of
     * <code>Paint</code> that do not accept a null
     * <code>ColorModel</code> argument.
     * If a developer is writing code which passes a null
     * <code>ColorModel</code> argument to the
     * <code>createContext</code> method of <code>Paint</code>
     * objects from arbitrary sources it would be wise to code defensively
     * by manufacturing a non-null <code>ColorModel</code> for those
     * objects which throw a <code>NullPointerException</code>.
     * @param cm the {@link ColorModel} that receives the
     * <code>Paint</code> data. This is used only as a hint.
     * @param deviceBounds the device space bounding box
     *                     of the graphics primitive being rendered
     * @param userBounds the user space bounding box
     *                     of the graphics primitive being rendered
     * @param xform the {@link AffineTransform} from user
     *      space into device space
     * @param hints the hint that the context object uses to
     *              choose between rendering alternatives
     * @return the <code>PaintContext</code> for
     *              generating color patterns
     * @see PaintContext
     */
    public PaintContext createContext(ColorModel cm,
                                      Rectangle deviceBounds,
                                      Rectangle2D userBounds,
                                      AffineTransform xform,
                                      RenderingHints hints);

}
