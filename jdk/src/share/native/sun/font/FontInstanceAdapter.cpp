/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
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

/*
 * (C) Copyright IBM Corp. 1998-2001 - All Rights Reserved
 *
 * The original version of this source code and documentation is
 * copyrighted and owned by IBM. These materials are provided
 * under terms of a License Agreement between IBM and Sun.
 * This technology is protected by multiple US and International
 * patents. This notice and attribution to IBM may not be removed.
 */

#include "FontInstanceAdapter.h"

FontInstanceAdapter::FontInstanceAdapter(JNIEnv *theEnv,
                                         jobject theFont2D,
                                         jobject theFontStrike,
                                         float *matrix,
                                         le_int32 xRes, le_int32 yRes,
                                         le_int32 theUPEM,
                                         TTLayoutTableCache *ltables)
    : env(theEnv), font2D(theFont2D), fontStrike(theFontStrike),
      xppem(0), yppem(0),
      xScaleUnitsToPoints(0), yScaleUnitsToPoints(0),
      xScalePixelsToUnits(0), yScalePixelsToUnits(0),
      upem(theUPEM), layoutTables(ltables)
{
    xPointSize = euclidianDistance(matrix[0], matrix[1]);
    yPointSize = euclidianDistance(matrix[2], matrix[3]);

    txMat[0] = matrix[0]/xPointSize;
    txMat[1] = matrix[1]/xPointSize;
    txMat[2] = matrix[2]/yPointSize;
    txMat[3] = matrix[3]/yPointSize;

    xppem = ((float) xRes / 72) * xPointSize;
    yppem = ((float) yRes / 72) * yPointSize;

    xScaleUnitsToPoints = xPointSize / upem;
    yScaleUnitsToPoints = yPointSize / upem;

    xScalePixelsToUnits = upem / xppem;
    yScalePixelsToUnits = upem / yppem;
};

const void *FontInstanceAdapter::getFontTable(LETag tableTag) const
{
  if (!layoutTables) { // t1 font
    return 0;
  }

  // cache in font's pscaler object
  // font disposer will handle for us

  switch(tableTag) {
  case GSUB_TAG: if (layoutTables->gsub_len != -1) return (void*)layoutTables->gsub; break;
  case GPOS_TAG: if (layoutTables->gpos_len != -1) return (void*)layoutTables->gpos; break;
  case GDEF_TAG: if (layoutTables->gdef_len != -1) return (void*)layoutTables->gdef; break;
  case MORT_TAG: if (layoutTables->mort_len != -1) return (void*)layoutTables->mort; break;
  case KERN_TAG: if (layoutTables->kern_len != -1) return (void*)layoutTables->kern; break;
  default:
   //fprintf(stderr, "unexpected table request from font instance adapter: %x\n", tableTag);
    return 0;
  }

  jbyte* result = 0;
  jsize  len = 0;
  jbyteArray tableBytes = (jbyteArray)
    env->CallObjectMethod(font2D, sunFontIDs.getTableBytesMID, tableTag);
  if (!IS_NULL(tableBytes)) {
    len = env->GetArrayLength(tableBytes);
    result = new jbyte[len];
    env->GetByteArrayRegion(tableBytes, 0, len, result);
  }

  switch(tableTag) {
  case GSUB_TAG: layoutTables->gsub = (void*)result; layoutTables->gsub_len = len; break;
  case GPOS_TAG: layoutTables->gpos = (void*)result; layoutTables->gpos_len = len; break;
  case GDEF_TAG: layoutTables->gdef = (void*)result; layoutTables->gdef_len = len; break;
  case MORT_TAG: layoutTables->mort = (void*)result; layoutTables->mort_len = len; break;
  case KERN_TAG: layoutTables->kern = (void*)result; layoutTables->kern_len = len; break;
  default: break;
  }

  return (void*)result;
};

LEGlyphID FontInstanceAdapter::mapCharToGlyph(LEUnicode32 ch, const LECharMapper *mapper) const
{
    LEUnicode32 mappedChar = mapper->mapChar(ch);

    if (mappedChar == 0xFFFF || mappedChar == 0xFFFE) {
        return 0xFFFF;
    }

    if (mappedChar == 0x200C || mappedChar == 0x200D) {
        return 1;
    }

    LEGlyphID id = (LEGlyphID)env->CallIntMethod(font2D, sunFontIDs.f2dCharToGlyphMID, (jint)mappedChar);
    return id;
}

LEGlyphID FontInstanceAdapter::mapCharToGlyph(LEUnicode32 ch) const
{
    LEGlyphID id = (LEGlyphID)env->CallIntMethod(font2D, sunFontIDs.f2dCharToGlyphMID, ch);
    return id;
}

void FontInstanceAdapter::mapCharsToWideGlyphs(const LEUnicode chars[],
    le_int32 offset, le_int32 count, le_bool reverse,
    const LECharMapper *mapper, le_uint32 glyphs[]) const
{
    le_int32 i, out = 0, dir = 1;

    if (reverse) {
        out = count - 1;
        dir = -1;
    }

    for (i = offset; i < offset + count; i += 1, out += dir) {
                LEUnicode16 high = chars[i];
                LEUnicode32 code = high;

                if (i < offset + count - 1 && high >= 0xD800 && high <= 0xDBFF) {
                        LEUnicode16 low = chars[i + 1];

                        if (low >= 0xDC00 && low <= 0xDFFF) {
                                code = (high - 0xD800) * 0x400 + low - 0xDC00 + 0x10000;
                        }
                }

        glyphs[out] = mapCharToWideGlyph(code, mapper);

                if (code >= 0x10000) {
                        i += 1;
                        glyphs[out += dir] = 0xFFFF;
                }
    }
}

le_uint32 FontInstanceAdapter::mapCharToWideGlyph(LEUnicode32 ch, const LECharMapper *mapper) const
{
    LEUnicode32 mappedChar = mapper->mapChar(ch);

    if (mappedChar == 0xFFFF) {
        return 0xFFFF;
    }

    if (mappedChar == 0x200C || mappedChar == 0x200D) {
        return 1;
    }

    return (LEGlyphID)env->CallIntMethod(font2D, sunFontIDs.charToGlyphMID,
                                         mappedChar);
}

void FontInstanceAdapter::getGlyphAdvance(LEGlyphID glyph, LEPoint &advance) const
{
    getWideGlyphAdvance((le_uint32)glyph, advance);
}

void FontInstanceAdapter::getKerningAdjustment(LEPoint &adjustment) const
{
    float xx, xy, yx, yy;
    le_bool isIdentityMatrix;

    isIdentityMatrix = (txMat[0] == 1 && txMat[1] == 0 &&
                        txMat[2] == 0 && txMat[3] == 1);

    if (!isIdentityMatrix) {
      xx = adjustment.fX;
      xy = xx * txMat[1];
      xx = xx * txMat[0];

      yy = adjustment.fY;
      yx = yy * txMat[2];
      yy = yy * txMat[3];

      adjustment.fX = xx + yx;
      adjustment.fY = xy + yy;
    }

    jobject pt = env->NewObject(sunFontIDs.pt2DFloatClass,
                                sunFontIDs.pt2DFloatCtr,
                                adjustment.fX, adjustment.fY);
    env->CallObjectMethod(fontStrike, sunFontIDs.adjustPointMID, pt);
    adjustment.fX = env->GetFloatField(pt, sunFontIDs.xFID);
    adjustment.fY = env->GetFloatField(pt, sunFontIDs.yFID);
}

void FontInstanceAdapter::getWideGlyphAdvance(le_uint32 glyph, LEPoint &advance) const
{
    if ((glyph & 0xfffe) == 0xfffe) {
        advance.fX = 0;
        advance.fY = 0;
        return;
    }
    jobject pt = env->CallObjectMethod(fontStrike,
                                       sunFontIDs.getGlyphMetricsMID, glyph);
    if (pt != NULL) {
        advance.fX = env->GetFloatField(pt, sunFontIDs.xFID);
        advance.fY = env->GetFloatField(pt, sunFontIDs.yFID);
        env->DeleteLocalRef(pt);
    }
}

le_bool FontInstanceAdapter::getGlyphPoint(LEGlyphID glyph,
                                           le_int32 pointNumber,
                                           LEPoint &point) const
{
  /* This upcall is not ideal, since it will make another down call.
   * The intention is to move up some of this code into Java. But
   * a HashMap has been added to the Java PhysicalStrike object to cache
   * these points so that they don't need to be repeatedly recalculated
   * which is expensive as it needs the font scaler to re-generate the
   * hinted glyph outline. This turns out to be a huge win over 1.4.x
   */
     jobject pt = env->CallObjectMethod(fontStrike,
                                        sunFontIDs.getGlyphPointMID,
                                        glyph, pointNumber);
     if (pt != NULL) {
       /* point is a java.awt.geom.Point2D.Float */
        point.fX = env->GetFloatField(pt, sunFontIDs.xFID);
        /* convert from java coordinate system to internal '+y up' coordinate system */
        point.fY = -env->GetFloatField(pt, sunFontIDs.yFID);
        return true;
     } else {
        return false;
     }
}

void FontInstanceAdapter::transformFunits(float xFunits, float yFunits, LEPoint &pixels) const
{
    float xx, xy, yx, yy;
    le_bool isIdentityMatrix;

    isIdentityMatrix = (txMat[0] == 1 && txMat[1] == 0 &&
                        txMat[2] == 0 && txMat[3] == 1);

    xx = xFunits * xScaleUnitsToPoints;
    xy = 0;
    if (!isIdentityMatrix) {
        xy = xx * txMat[1];
        xx = xx * txMat[0];
    };

    yx = 0;
    yy = yFunits * yScaleUnitsToPoints;
    if (!isIdentityMatrix) {
        yx = yy * txMat[2];
        yy = yy * txMat[3];
    };
    pixels.fX = xx + yx;
    pixels.fY = xy + yy;
}


float FontInstanceAdapter::euclidianDistance(float a, float b)
{
    if (a < 0) {
        a = -a;
    }

    if (b < 0) {
        b = -b;
    }

    if (a == 0) {
        return b;
    }

    if (b == 0) {
        return a;
    }

    float root = a > b ? a + (b / 2) : b + (a / 2); /* Do an initial approximation, in root */

        /* An unrolled Newton-Raphson iteration sequence */
    root = (root + (a * (a / root)) + (b * (b / root)) + 1) / 2;
    root = (root + (a * (a / root)) + (b * (b / root)) + 1) / 2;
    root = (root + (a * (a / root)) + (b * (b / root)) + 1) / 2;

    return root;
}
