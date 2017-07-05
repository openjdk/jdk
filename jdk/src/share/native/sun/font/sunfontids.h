/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef SunFontIDIncludesDefined
#define SunFontIDIncludesDefined

#include "jni.h"

#ifdef  __cplusplus
extern "C" {
#endif

typedef struct FontManagerNativeIDs {

    /* sun/font/Font2D methods */
    jmethodID getMapperMID;
    jmethodID getTableBytesMID;
    jmethodID canDisplayMID;
    jmethodID f2dCharToGlyphMID;

    /* sun/font/CharToGlyphMapper methods */
    jmethodID charToGlyphMID;

    /* sun/font/PhysicalStrike methods */
    jmethodID getGlyphMetricsMID;
    jmethodID getGlyphPointMID;
    jmethodID adjustPointMID;
    jfieldID  pScalerContextFID;

    /* java/awt/geom/Rectangle2D.Float */
    jclass rect2DFloatClass;
    jmethodID rect2DFloatCtr;
    jmethodID rect2DFloatCtr4;
    jfieldID rectF2DX, rectF2DY, rectF2DWidth, rectF2DHeight;

    /* java/awt/geom/Point2D.Float */
    jclass pt2DFloatClass;
    jmethodID pt2DFloatCtr;
    jfieldID xFID, yFID;

    /* java/awt/geom/GeneralPath */
    jclass gpClass;
    jmethodID gpCtr;
    jmethodID gpCtrEmpty;

    /* sun/font/StrikeMetrics */
    jclass strikeMetricsClass;
    jmethodID strikeMetricsCtr;

    /* sun/font/TrueTypeFont */
    jmethodID ttReadBlockMID;
    jmethodID ttReadBytesMID;

    /* sun/font/Type1Font */
    jmethodID readFileMID;

    /* sun/font/GlyphList */
    jfieldID glyphListX, glyphListY, glyphListLen,
      glyphImages, glyphListUsePos, glyphListPos, lcdRGBOrder, lcdSubPixPos;
} FontManagerNativeIDs;

/* Note: we share variable in the context of fontmanager lib
   but we need access method to use it from separate rasterizer lib */
extern FontManagerNativeIDs sunFontIDs;
JNIEXPORT FontManagerNativeIDs getSunFontIDs();

#ifdef  __cplusplus
}
#endif

#endif
