/*
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

void AnchorTable::getAnchor(LEGlyphID glyphID, const LEFontInstance *fontInstance,
                            LEPoint &anchor) const
{
    switch(SWAPW(anchorFormat)) {
    case 1:
    {
        const Format1AnchorTable *f1 = (const Format1AnchorTable *) this;

        f1->getAnchor(fontInstance, anchor);
        break;
    }

    case 2:
    {
        const Format2AnchorTable *f2 = (const Format2AnchorTable *) this;

        f2->getAnchor(glyphID, fontInstance, anchor);
        break;
    }

    case 3:
    {
        const Format3AnchorTable *f3 = (const Format3AnchorTable *) this;

        f3->getAnchor(fontInstance, anchor);
        break;
    }

    default:
        // unknown format: just use x, y coordinate, like format 1...
        const Format1AnchorTable *f1 = (const Format1AnchorTable *) this;

        f1->getAnchor(fontInstance, anchor);
        break;
    }
}

void Format1AnchorTable::getAnchor(const LEFontInstance *fontInstance, LEPoint &anchor) const
{
    le_int16 x = SWAPW(xCoordinate);
    le_int16 y = SWAPW(yCoordinate);
    LEPoint pixels;

    fontInstance->transformFunits(x, y, pixels);

    fontInstance->pixelsToUnits(pixels, anchor);
}

void Format2AnchorTable::getAnchor(LEGlyphID glyphID, const LEFontInstance *fontInstance, LEPoint &anchor) const
{
    LEPoint point;

    if (! fontInstance->getGlyphPoint(glyphID, SWAPW(anchorPoint), point)) {
        le_int16 x = SWAPW(xCoordinate);
        le_int16 y = SWAPW(yCoordinate);

        fontInstance->transformFunits(x, y, point);
    }


    fontInstance->pixelsToUnits(point, anchor);
}

void Format3AnchorTable::getAnchor(const LEFontInstance *fontInstance, LEPoint &anchor) const
{
    le_int16 x = SWAPW(xCoordinate);
    le_int16 y = SWAPW(yCoordinate);
    LEPoint pixels;
    Offset dtxOffset = SWAPW(xDeviceTableOffset);
    Offset dtyOffset = SWAPW(yDeviceTableOffset);

    fontInstance->transformFunits(x, y, pixels);

    if (dtxOffset != 0) {
        const DeviceTable *dtx = (const DeviceTable *) ((char *) this + dtxOffset);
        le_int16 adjx = dtx->getAdjustment((le_int16) fontInstance->getXPixelsPerEm());

        pixels.fX += adjx;
    }

    if (dtyOffset != 0) {
        const DeviceTable *dty = (const DeviceTable *) ((char *) this + dtyOffset);
        le_int16 adjy = dty->getAdjustment((le_int16) fontInstance->getYPixelsPerEm());

        pixels.fY += adjy;
    }

    fontInstance->pixelsToUnits(pixels, anchor);
}

U_NAMESPACE_END

