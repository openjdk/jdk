/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.font;

import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

public class NativeStrike extends PhysicalStrike {

    NativeFont nativeFont;

    NativeStrike(NativeFont nativeFont, FontStrikeDesc desc) {
        super(nativeFont, desc);

        throw new RuntimeException("NativeFont not used on Windows");
    }

    NativeStrike(NativeFont nativeFont, FontStrikeDesc desc,
                 boolean nocache) {
        super(nativeFont, desc);

        throw new RuntimeException("NativeFont not used on Windows");
    }


    void getGlyphImagePtrs(int[] glyphCodes, long[] images,int  len) {
    }

    long getGlyphImagePtr(int glyphCode) {
        return 0L;
    }

    long getGlyphImagePtrNoCache(int glyphCode) {
        return 0L;
    }

    void getGlyphImageBounds(int glyphcode,
                             Point2D.Float pt,
                             Rectangle result) {
    }

    Point2D.Float getGlyphMetrics(int glyphCode) {
        return null;
    }

    float getGlyphAdvance(int glyphCode) {
        return 0f;
    }

    Rectangle2D.Float getGlyphOutlineBounds(int glyphCode) {
        return null;
    }
    GeneralPath getGlyphOutline(int glyphCode, float x, float y) {
        return null;
    }

    GeneralPath getGlyphVectorOutline(int[] glyphs, float x, float y) {
        return null;
    }

}
