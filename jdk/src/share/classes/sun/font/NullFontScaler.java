/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.awt.geom.Rectangle2D;

class NullFontScaler extends FontScaler {
    NullFontScaler() {}

    public NullFontScaler(Font2D font, int indexInCollection,
        boolean supportsCJK, int filesize) {}

    StrikeMetrics getFontMetrics(long pScalerContext) {
        return new StrikeMetrics(0xf0,0xf0,0xf0,0xf0,0xf0,0xf0,
        0xf0,0xf0,0xf0,0xf0);
    }

    float getGlyphAdvance(long pScalerContext, int glyphCode) {
        return 0.0f;
    }

    void getGlyphMetrics(long pScalerContext, int glyphCode,
        Point2D.Float metrics) {
        metrics.x = 0;
        metrics.y = 0;
    }

    Rectangle2D.Float getGlyphOutlineBounds(long pContext, int glyphCode) {
        return new Rectangle2D.Float(0, 0, 0, 0);
    }

    GeneralPath getGlyphOutline(long pScalerContext, int glyphCode,
        float x, float y) {
        return new GeneralPath();
    }

    GeneralPath getGlyphVectorOutline(long pScalerContext, int[] glyphs,
        int numGlyphs, float x, float y) {
        return new GeneralPath();
    }

    long getLayoutTableCache() {return 0L;}

    long createScalerContext(double[] matrix, boolean fontType, int aa,
        int fm, float boldness, float italic) {
        return getNullScalerContext();
    }

    void invalidateScalerContext(long ppScalerContext) {
        //nothing to do
    }

    int getNumGlyphs() throws FontScalerException {
        return 1;
    }

    int getMissingGlyphCode() throws FontScalerException {
        return 0;
    }

    int getGlyphCode(char charCode) throws FontScalerException {
        return 0;
    }

    long getUnitsPerEm() {
        return 2048;
    }

    Point2D.Float getGlyphPoint(long pScalerContext,
                                int glyphCode, int ptNumber) {
        return null;
    }

    /* Ideally NullFontScaler should not have native code.
       However, at this moment we need these methods to be native because:
         - glyph cache code assumes null pointers to GlyphInfo structures
         - FileFontStrike needs native context
    */
    static native long getNullScalerContext();
    native long getGlyphImage(long pScalerContext, int glyphCode);
}
