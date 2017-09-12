/*
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
 *
 */

/*
 *******************************************************************************
 *
 *   Copyright (C) 1999-2007, International Business Machines
 *   Corporation and others.  All Rights Reserved.
 *
 *******************************************************************************
 *   file name:  LEFontInstance.cpp
 *
 *   created on: 02/06/2003
 *   created by: Eric R. Mader
 */

#include "LETypes.h"
#include "LEScripts.h"
#include "LEFontInstance.h"
#include "LEGlyphStorage.h"

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(LEFontInstance)

LECharMapper::~LECharMapper()
{
    // nothing to do.
}

LEFontInstance::~LEFontInstance()
{
    // nothing to do
}

const LEFontInstance *LEFontInstance::getSubFont(const LEUnicode chars[], le_int32 *offset, le_int32 limit,
                                                       le_int32 script, LEErrorCode &success) const
{
    if (LE_FAILURE(success)) {
        return NULL;
    }

    if (chars == NULL || *offset < 0 || limit < 0 || *offset >= limit || script < 0 || script >= scriptCodeCount) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return NULL;
    }

    *offset = limit;
    return this;
}

void LEFontInstance::mapCharsToGlyphs(const LEUnicode chars[], le_int32 offset, le_int32 count,
                                      le_bool reverse, const LECharMapper *mapper, le_bool filterZeroWidth, LEGlyphStorage &glyphStorage) const
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

        glyphStorage[out] = mapCharToGlyph(code, mapper, filterZeroWidth);

        if (code >= 0x10000) {
            i += 1;
            glyphStorage[out += dir] = 0xFFFF;
        }
    }
}

LEGlyphID LEFontInstance::mapCharToGlyph(LEUnicode32 ch, const LECharMapper *mapper) const
{
    return mapCharToGlyph(ch, mapper, TRUE);
}

LEGlyphID LEFontInstance::mapCharToGlyph(LEUnicode32 ch, const LECharMapper *mapper, le_bool filterZeroWidth) const
{
    LEUnicode32 mappedChar = mapper->mapChar(ch);

    if (mappedChar == 0xFFFE || mappedChar == 0xFFFF) {
        return 0xFFFF;
    }

    if (filterZeroWidth && (mappedChar == 0x200C || mappedChar == 0x200D)) {
        return canDisplay(mappedChar)? 0x0001 : 0xFFFF;
    }

    return mapCharToGlyph(mappedChar);
}

le_bool LEFontInstance::canDisplay(LEUnicode32 ch) const
{
    return LE_GET_GLYPH(mapCharToGlyph(ch)) != 0;
}

float LEFontInstance::xUnitsToPoints(float xUnits) const
{
    return (xUnits * getXPixelsPerEm()) / (float) getUnitsPerEM();
}

float LEFontInstance::yUnitsToPoints(float yUnits) const
{
    return (yUnits * getYPixelsPerEm()) / (float) getUnitsPerEM();
}

void LEFontInstance::unitsToPoints(LEPoint &units, LEPoint &points) const
{
    points.fX = xUnitsToPoints(units.fX);
    points.fY = yUnitsToPoints(units.fY);
}

float LEFontInstance::xPixelsToUnits(float xPixels) const
{
    return (xPixels * getUnitsPerEM()) / (float) getXPixelsPerEm();
}

float LEFontInstance::yPixelsToUnits(float yPixels) const
{
    return (yPixels * getUnitsPerEM()) / (float) getYPixelsPerEm();
}

void LEFontInstance::pixelsToUnits(LEPoint &pixels, LEPoint &units) const
{
    units.fX = xPixelsToUnits(pixels.fX);
    units.fY = yPixelsToUnits(pixels.fY);
}

void LEFontInstance::transformFunits(float xFunits, float yFunits, LEPoint &pixels) const
{
    pixels.fX = xUnitsToPoints(xFunits) * getScaleFactorX();
    pixels.fY = yUnitsToPoints(yFunits) * getScaleFactorY();
}

le_int32 LEFontInstance::getLineHeight() const
{
    return getAscent() + getDescent() + getLeading();
}

U_NAMESPACE_END

