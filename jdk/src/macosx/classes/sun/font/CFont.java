/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.font;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

// Right now this class is final to avoid a problem with native code.
// For some reason the JNI IsInstanceOf was not working correctly
// so we are checking the class specifically. If we subclass this
// we need to modify the native code in CFontWrapper.m
public final class CFont extends PhysicalFont {

    /* CFontStrike doesn't call these methods so they are unimplemented.
     * They are here to meet the requirements of PhysicalFont, needed
     * because a CFont can sometimes be returned where a PhysicalFont
     * is expected.
     */
    StrikeMetrics getFontMetrics(long pScalerContext) {
       throw new InternalError("Not implemented");
    }

    float getGlyphAdvance(long pScalerContext, int glyphCode) {
       throw new InternalError("Not implemented");
    }

    void getGlyphMetrics(long pScalerContext, int glyphCode,
                                  Point2D.Float metrics) {
       throw new InternalError("Not implemented");
    }

    long getGlyphImage(long pScalerContext, int glyphCode) {
       throw new InternalError("Not implemented");
    }

    Rectangle2D.Float getGlyphOutlineBounds(long pScalerContext,
                                                     int glyphCode) {
       throw new InternalError("Not implemented");
    }

    GeneralPath getGlyphOutline(long pScalerContext, int glyphCode,
                                         float x, float y) {
       throw new InternalError("Not implemented");
    }

    GeneralPath getGlyphVectorOutline(long pScalerContext,
                                               int[] glyphs, int numGlyphs,
                                               float x, float y) {
       throw new InternalError("Not implemented");
    }

    private static native long createNativeFont(final String nativeFontName,
                                                final int style,
                                                final boolean isFakeItalic);
    private static native void disposeNativeFont(final long nativeFontPtr);

    private boolean isFakeItalic;
    private String nativeFontName;
    private long nativeFontPtr;

    // this constructor is called from CFontWrapper.m
    public CFont(String name) {
        this(name, name);
    }

    public CFont(String name, String inFamilyName) {
        handle = new Font2DHandle(this);
        fullName = name;
        familyName = inFamilyName;
        nativeFontName = inFamilyName;
        setStyle();
    }

    public CFont(CFont other, String logicalFamilyName) {
        handle = new Font2DHandle(this);
        fullName = logicalFamilyName;
        familyName = logicalFamilyName;
        nativeFontName = other.nativeFontName;
        style = other.style;
        isFakeItalic = other.isFakeItalic;
    }

    public CFont createItalicVariant() {
        CFont font = new CFont(this, familyName);
        font.fullName =
            fullName + (style == Font.BOLD ? "" : "-") + "Italic-Derived";
        font.style |= Font.ITALIC;
        font.isFakeItalic = true;
        return font;
    }

    protected synchronized long getNativeFontPtr() {
        if (nativeFontPtr == 0L) {
            nativeFontPtr = createNativeFont(nativeFontName, style, isFakeItalic);
}
        return nativeFontPtr;
    }

    protected synchronized void finalize() {
        if (nativeFontPtr != 0) {
            disposeNativeFont(nativeFontPtr);
        }
        nativeFontPtr = 0;
    }

    protected CharToGlyphMapper getMapper() {
        if (mapper == null) {
            mapper = new CCharToGlyphMapper(this);
        }
        return mapper;
    }

    protected FontStrike createStrike(FontStrikeDesc desc) {
        if (isFakeItalic) {
            desc = new FontStrikeDesc(desc);
            desc.glyphTx.concatenate(AffineTransform.getShearInstance(-0.2, 0));
        }
        return new CStrike(this, desc);
    }

    // <rdar://problem/5321707> sun.font.Font2D caches the last used strike,
    // but does not check if the properties of the strike match the properties
    // of the incoming java.awt.Font object (size, style, etc).
    // Simple answer: don't cache.
    private static FontRenderContext DEFAULT_FRC =
        new FontRenderContext(null, false, false);
    public FontStrike getStrike(final Font font) {
        return getStrike(font, DEFAULT_FRC);
    }

    public String toString() {
        return "CFont { fullName: " + fullName +
            ",  familyName: " + familyName + ", style: " + style +
            " } aka: " + super.toString();
    }
}
