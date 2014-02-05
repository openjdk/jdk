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
 *
 * (C) Copyright IBM Corp. 1998-2005 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "LEFontInstance.h"
#include "DeviceTables.h"
#include "AnchorTables.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

void AnchorTable::getAnchor(const LETableReference &base, LEGlyphID glyphID, const LEFontInstance *fontInstance,
                            LEPoint &anchor, LEErrorCode &success) const
{
  switch(SWAPW(anchorFormat)) {
    case 1:
    {
        LEReferenceTo<Format1AnchorTable> f1(base, success);
        f1->getAnchor(f1, fontInstance, anchor, success);
        break;
    }

    case 2:
    {
        LEReferenceTo<Format2AnchorTable> f2(base, success);
        f2->getAnchor(f2, glyphID, fontInstance, anchor, success);
        break;
    }

    case 3:
    {
        LEReferenceTo<Format3AnchorTable> f3(base, success);
        f3->getAnchor(f3, fontInstance, anchor, success);
        break;
    }

    default:
    {
        // unknown format: just use x, y coordinate, like format 1...
        LEReferenceTo<Format1AnchorTable> f1(base, success);
        f1->getAnchor(f1, fontInstance, anchor, success);
        break;
    }
  }
}

void Format1AnchorTable::getAnchor(const LEReferenceTo<Format1AnchorTable>& base, const LEFontInstance *fontInstance, LEPoint &anchor, LEErrorCode &success) const
{
    le_int16 x = SWAPW(xCoordinate);
    le_int16 y = SWAPW(yCoordinate);
    LEPoint pixels;

    fontInstance->transformFunits(x, y, pixels);
    fontInstance->pixelsToUnits(pixels, anchor);
}

void Format2AnchorTable::getAnchor(const LEReferenceTo<Format2AnchorTable>& base,
                                   LEGlyphID glyphID, const LEFontInstance *fontInstance, LEPoint &anchor
                                   , LEErrorCode &success) const
{
    LEPoint point;

    if (! fontInstance->getGlyphPoint(glyphID, SWAPW(anchorPoint), point)) {
        le_int16 x = SWAPW(xCoordinate);
        le_int16 y = SWAPW(yCoordinate);

        fontInstance->transformFunits(x, y, point);
    }


    fontInstance->pixelsToUnits(point, anchor);
}

void Format3AnchorTable::getAnchor(const LEReferenceTo<Format3AnchorTable> &base, const LEFontInstance *fontInstance,
                                   LEPoint &anchor, LEErrorCode &success) const
{
    le_int16 x = SWAPW(xCoordinate);
    le_int16 y = SWAPW(yCoordinate);
    LEPoint pixels;
    Offset dtxOffset = SWAPW(xDeviceTableOffset);
    Offset dtyOffset = SWAPW(yDeviceTableOffset);

    fontInstance->transformFunits(x, y, pixels);

    if (dtxOffset != 0) {
        LEReferenceTo<DeviceTable> dt(base, success, dtxOffset);
        le_int16 adjx = dt->getAdjustment(dt, (le_int16) fontInstance->getXPixelsPerEm(), success);

        pixels.fX += adjx;
    }

    if (dtyOffset != 0) {
        LEReferenceTo<DeviceTable> dt(base, success, dtyOffset);
        le_int16 adjy = dt->getAdjustment(dt, (le_int16) fontInstance->getYPixelsPerEm(), success);

        pixels.fY += adjy;
    }

    fontInstance->pixelsToUnits(pixels, anchor);
}

U_NAMESPACE_END

