/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
package org.jdesktop.swingx.designer.effects;

import org.jdesktop.beans.AbstractBean;
import org.jdesktop.swingx.designer.BlendingMode;

import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;

/**
 * Effect
 *
 * @author Created by Jasper Potts (Jun 18, 2007)
 */
public abstract class Effect extends AbstractBean {
    protected boolean visible = true;

    public enum EffectType {
        UNDER, BLENDED, OVER
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        boolean old = isVisible();
        this.visible = visible;
        firePropertyChange("visible", old, isVisible());
    }

    public String toString() {
        return getDisplayName();
    }

    // =================================================================================================================
    // Abstract Methods

    /**
     * Get the display name for this effect
     *
     * @return The user displayable name
     */
    public abstract String getDisplayName();

    /**
     * Get the type of this effect, one of UNDER,BLENDED,OVER. UNDER means the result of apply effect should be painted
     * under the src image. BLENDED means the result of apply sffect contains a modified src image so just it should be
     * painted. OVER means the result of apply effect should be painted over the src image.
     *
     * @return The effect type
     */
    public abstract EffectType getEffectType();

    /**
     * Get the blending mode to use to paint the result effected image if the EffectType is UNDER or OVER.
     *
     * @return The blending mode for the effect
     */
    public abstract BlendingMode getBlendingMode();

    /**
     * Get the opacity to use to paint the result effected image if the EffectType is UNDER or OVER.
     *
     * @return The opactity for the effect, 0.0f -> 1.0f
     */
    public abstract float getOpacity();

    /**
     * Apply the effect to the src image generating the result . The result image may or may not contain the source
     * image depending on what the effect type is.
     *
     * @param src The source image for applying the effect to
     * @param dst The dstination image to paint effect result into. If this is null then a new image will be created
     * @param w   The width of the src image to apply effect to, this allow the src and dst buffers to be bigger than
     *            the area the need effect applied to it
     * @param h   The height of the src image to apply effect to, this allow the src and dst buffers to be bigger than
     *            the area the need effect applied to it
     * @return The result of appl
     */
    public abstract BufferedImage applyEffect(BufferedImage src, BufferedImage dst, int w, int h);

    // =================================================================================================================
    // Static data cache

    private static SoftReference<int[]> tmpIntArray = null;
    private static SoftReference<byte[]> tmpByteArray1 = null;
    private static SoftReference<byte[]> tmpByteArray2 = null;
    private static SoftReference<byte[]> tmpByteArray3 = null;

    protected static int[] getTmpIntArray(int size) {
        int[] tmp;
        if (tmpIntArray == null || (tmp = tmpIntArray.get()) == null || tmp.length < size) {
            // create new array
            tmp = new int[size];
            tmpIntArray = new SoftReference<int[]>(tmp);
        }
        return tmp;
    }

    protected static byte[] getTmpByteArray1(int size) {
        byte[] tmp;
        if (tmpByteArray1 == null || (tmp = tmpByteArray1.get()) == null || tmp.length < size) {
            // create new array
            tmp = new byte[size];
            tmpByteArray1 = new SoftReference<byte[]>(tmp);
        }
        return tmp;
    }

    protected static byte[] getTmpByteArray2(int size) {
        byte[] tmp;
        if (tmpByteArray2 == null || (tmp = tmpByteArray2.get()) == null || tmp.length < size) {
            // create new array
            tmp = new byte[size];
            tmpByteArray2 = new SoftReference<byte[]>(tmp);
        }
        return tmp;
    }

    protected static byte[] getTmpByteArray3(int size) {
        byte[] tmp;
        if (tmpByteArray3 == null || (tmp = tmpByteArray3.get()) == null || tmp.length < size) {
            // create new array
            tmp = new byte[size];
            tmpByteArray3 = new SoftReference<byte[]>(tmp);
        }
        return tmp;
    }
}
