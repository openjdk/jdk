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
#include "OpenTypeTables.h"
#include "GlyphDefinitionTables.h"
#include "GlyphPositionAdjustments.h"
#include "GlyphIterator.h"
#include "LEGlyphStorage.h"
#include "Lookups.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

GlyphIterator::GlyphIterator(LEGlyphStorage &theGlyphStorage, GlyphPositionAdjustments *theGlyphPositionAdjustments, le_bool rightToLeft, le_uint16 theLookupFlags,
                             FeatureMask theFeatureMask, const GlyphDefinitionTableHeader *theGlyphDefinitionTableHeader)
  : direction(1), position(-1), nextLimit(-1), prevLimit(-1),
    glyphStorage(theGlyphStorage), glyphPositionAdjustments(theGlyphPositionAdjustments),
    srcIndex(-1), destIndex(-1), lookupFlags(theLookupFlags), featureMask(theFeatureMask),
    glyphClassDefinitionTable(NULL), markAttachClassDefinitionTable(NULL)

{
    le_int32 glyphCount = glyphStorage.getGlyphCount();

    if (theGlyphDefinitionTableHeader != NULL) {
        glyphClassDefinitionTable = theGlyphDefinitionTableHeader->getGlyphClassDefinitionTable();
        markAttachClassDefinitionTable = theGlyphDefinitionTableHeader->getMarkAttachClassDefinitionTable();
    }

    nextLimit = glyphCount;

    if (rightToLeft) {
        direction = -1;
        position = glyphCount;
        nextLimit = -1;
        prevLimit = glyphCount;
    }
}

GlyphIterator::GlyphIterator(GlyphIterator &that)
  : glyphStorage(that.glyphStorage)
{
    direction    = that.direction;
    position     = that.position;
    nextLimit    = that.nextLimit;
    prevLimit    = that.prevLimit;

    glyphPositionAdjustments = that.glyphPositionAdjustments;
    srcIndex = that.srcIndex;
    destIndex = that.destIndex;
    lookupFlags = that.lookupFlags;
    featureMask = that.featureMask;
    glyphClassDefinitionTable = that.glyphClassDefinitionTable;
    markAttachClassDefinitionTable = that.markAttachClassDefinitionTable;
}

GlyphIterator::GlyphIterator(GlyphIterator &that, FeatureMask newFeatureMask)
  : glyphStorage(that.glyphStorage)
{
    direction    = that.direction;
    position     = that.position;
    nextLimit    = that.nextLimit;
    prevLimit    = that.prevLimit;

    glyphPositionAdjustments = that.glyphPositionAdjustments;
    srcIndex = that.srcIndex;
    destIndex = that.destIndex;
    lookupFlags = that.lookupFlags;
    featureMask = newFeatureMask;
    glyphClassDefinitionTable = that.glyphClassDefinitionTable;
    markAttachClassDefinitionTable = that.markAttachClassDefinitionTable;
}

GlyphIterator::GlyphIterator(GlyphIterator &that, le_uint16 newLookupFlags)
  : glyphStorage(that.glyphStorage)
{
    direction    = that.direction;
    position     = that.position;
    nextLimit    = that.nextLimit;
    prevLimit    = that.prevLimit;

    glyphPositionAdjustments = that.glyphPositionAdjustments;
    srcIndex = that.srcIndex;
    destIndex = that.destIndex;
    lookupFlags = newLookupFlags;
    featureMask = that.featureMask;
    glyphClassDefinitionTable = that.glyphClassDefinitionTable;
    markAttachClassDefinitionTable = that.markAttachClassDefinitionTable;
}

GlyphIterator::~GlyphIterator()
{
    // nothing to do, right?
}

void GlyphIterator::reset(le_uint16 newLookupFlags, FeatureMask newFeatureMask)
{
    position     = prevLimit;
    featureMask  = newFeatureMask;
    lookupFlags  = newLookupFlags;
}

LEGlyphID *GlyphIterator::insertGlyphs(le_int32 count)
{
    return glyphStorage.insertGlyphs(position, count);
}

le_int32 GlyphIterator::applyInsertions()
{
    le_int32 newGlyphCount = glyphStorage.applyInsertions();

    if (direction < 0) {
        prevLimit = newGlyphCount;
    } else {
        nextLimit = newGlyphCount;
    }

    return newGlyphCount;
}

le_int32 GlyphIterator::getCurrStreamPosition() const
{
    return position;
}

le_bool GlyphIterator::isRightToLeft() const
{
    return direction < 0;
}

le_bool GlyphIterator::ignoresMarks() const
{
    return (lookupFlags & lfIgnoreMarks) != 0;
}

le_bool GlyphIterator::baselineIsLogicalEnd() const
{
    return (lookupFlags & lfBaselineIsLogicalEnd) != 0;
}

LEGlyphID GlyphIterator::getCurrGlyphID() const
{
    if (direction < 0) {
        if (position <= nextLimit || position >= prevLimit) {
            return 0xFFFF;
        }
    } else {
        if (position <= prevLimit || position >= nextLimit) {
            return 0xFFFF;
        }
    }

    return glyphStorage[position];
}

void GlyphIterator::getCursiveEntryPoint(LEPoint &entryPoint) const
{
    if (direction < 0) {
        if (position <= nextLimit || position >= prevLimit) {
            return;
        }
    } else {
        if (position <= prevLimit || position >= nextLimit) {
            return;
        }
    }

    glyphPositionAdjustments->getEntryPoint(position, entryPoint);
}

void GlyphIterator::getCursiveExitPoint(LEPoint &exitPoint) const
{
    if (direction < 0) {
        if (position <= nextLimit || position >= prevLimit) {
            return;
        }
    } else {
        if (position <= prevLimit || position >= nextLimit) {
            return;
        }
    }

    glyphPositionAdjustments->getExitPoint(position, exitPoint);
}

void GlyphIterator::setCurrGlyphID(TTGlyphID glyphID)
{
    LEGlyphID glyph = glyphStorage[position];

    glyphStorage[position] = LE_SET_GLYPH(glyph, glyphID);
}

void GlyphIterator::setCurrStreamPosition(le_int32 newPosition)
{
    if (direction < 0) {
        if (newPosition >= prevLimit) {
            position = prevLimit;
            return;
        }

        if (newPosition <= nextLimit) {
            position = nextLimit;
            return;
        }
    } else {
        if (newPosition <= prevLimit) {
            position = prevLimit;
            return;
        }

        if (newPosition >= nextLimit) {
            position = nextLimit;
            return;
        }
    }

    position = newPosition - direction;
    next();
}

void GlyphIterator::setCurrGlyphBaseOffset(le_int32 baseOffset)
{
    if (direction < 0) {
        if (position <= nextLimit || position >= prevLimit) {
            return;
        }
    } else {
        if (position <= prevLimit || position >= nextLimit) {
            return;
        }
    }

    glyphPositionAdjustments->setBaseOffset(position, baseOffset);
}

void GlyphIterator::adjustCurrGlyphPositionAdjustment(float xPlacementAdjust, float yPlacementAdjust,
                                                      float xAdvanceAdjust, float yAdvanceAdjust)
{
    if (direction < 0) {
        if (position <= nextLimit || position >= prevLimit) {
            return;
        }
    } else {
        if (position <= prevLimit || position >= nextLimit) {
            return;
        }
    }

    glyphPositionAdjustments->adjustXPlacement(position, xPlacementAdjust);
    glyphPositionAdjustments->adjustYPlacement(position, yPlacementAdjust);
    glyphPositionAdjustments->adjustXAdvance(position, xAdvanceAdjust);
    glyphPositionAdjustments->adjustYAdvance(position, yAdvanceAdjust);
}

void GlyphIterator::setCurrGlyphPositionAdjustment(float xPlacementAdjust, float yPlacementAdjust,
                                                      float xAdvanceAdjust, float yAdvanceAdjust)
{
    if (direction < 0) {
        if (position <= nextLimit || position >= prevLimit) {
            return;
        }
    } else {
        if (position <= prevLimit || position >= nextLimit) {
            return;
        }
    }

    glyphPositionAdjustments->setXPlacement(position, xPlacementAdjust);
    glyphPositionAdjustments->setYPlacement(position, yPlacementAdjust);
    glyphPositionAdjustments->setXAdvance(position, xAdvanceAdjust);
    glyphPositionAdjustments->setYAdvance(position, yAdvanceAdjust);
}

void GlyphIterator::setCursiveEntryPoint(LEPoint &entryPoint)
{
    if (direction < 0) {
        if (position <= nextLimit || position >= prevLimit) {
            return;
        }
    } else {
        if (position <= prevLimit || position >= nextLimit) {
            return;
        }
    }

    glyphPositionAdjustments->setEntryPoint(position, entryPoint, baselineIsLogicalEnd());
}

void GlyphIterator::setCursiveExitPoint(LEPoint &exitPoint)
{
    if (direction < 0) {
        if (position <= nextLimit || position >= prevLimit) {
            return;
        }
    } else {
        if (position <= prevLimit || position >= nextLimit) {
            return;
        }
    }

    glyphPositionAdjustments->setExitPoint(position, exitPoint, baselineIsLogicalEnd());
}

void GlyphIterator::setCursiveGlyph()
{
    if (direction < 0) {
        if (position <= nextLimit || position >= prevLimit) {
            return;
        }
    } else {
        if (position <= prevLimit || position >= nextLimit) {
            return;
        }
    }

    glyphPositionAdjustments->setCursiveGlyph(position, baselineIsLogicalEnd());
}

le_bool GlyphIterator::filterGlyph(le_uint32 index) const
{
    LEGlyphID glyphID = glyphStorage[index];
    le_int32 glyphClass = gcdNoGlyphClass;

    if (LE_GET_GLYPH(glyphID) >= 0xFFFE) {
        return TRUE;
    }

    if (glyphClassDefinitionTable != NULL) {
        glyphClass = glyphClassDefinitionTable->getGlyphClass(glyphID);
    }

    switch (glyphClass)
    {
    case gcdNoGlyphClass:
        return FALSE;

    case gcdSimpleGlyph:
        return (lookupFlags & lfIgnoreBaseGlyphs) != 0;

    case gcdLigatureGlyph:
        return (lookupFlags & lfIgnoreLigatures) != 0;

    case gcdMarkGlyph:
    {
        if ((lookupFlags & lfIgnoreMarks) != 0) {
            return TRUE;
        }

        le_uint16 markAttachType = (lookupFlags & lfMarkAttachTypeMask) >> lfMarkAttachTypeShift;

        if ((markAttachType != 0) && (markAttachClassDefinitionTable != NULL)) {
            return markAttachClassDefinitionTable->getGlyphClass(glyphID) != markAttachType;
        }

        return FALSE;
    }

    case gcdComponentGlyph:
        return (lookupFlags & lfIgnoreBaseGlyphs) != 0;

    default:
        return FALSE;
    }
}

le_bool GlyphIterator::hasFeatureTag() const
{
    if (featureMask == 0) {
        return TRUE;
    }

    LEErrorCode success = LE_NO_ERROR;
    FeatureMask fm = glyphStorage.getAuxData(position, success);

    return (fm & featureMask) != 0;
}

le_bool GlyphIterator::findFeatureTag()
{
    while (nextInternal()) {
        if (hasFeatureTag()) {
            prevInternal();
            return TRUE;
        }
    }

    return FALSE;
}


le_bool GlyphIterator::nextInternal(le_uint32 delta)
{
    le_int32 newPosition = position;

    while (newPosition != nextLimit && delta > 0) {
        do {
            newPosition += direction;
        } while (newPosition != nextLimit && filterGlyph(newPosition));

        delta -= 1;
    }

    position = newPosition;

    return position != nextLimit;
}

le_bool GlyphIterator::next(le_uint32 delta)
{
    return nextInternal(delta) && hasFeatureTag();
}

le_bool GlyphIterator::prevInternal(le_uint32 delta)
{
    le_int32 newPosition = position;

    while (newPosition != prevLimit && delta > 0) {
        do {
            newPosition -= direction;
        } while (newPosition != prevLimit && filterGlyph(newPosition));

        delta -= 1;
    }

    position = newPosition;

    return position != prevLimit;
}

le_bool GlyphIterator::prev(le_uint32 delta)
{
    return prevInternal(delta) && hasFeatureTag();
}

le_int32 GlyphIterator::getMarkComponent(le_int32 markPosition) const
{
    le_int32 component = 0;
    le_int32 posn;

    for (posn = position; posn != markPosition; posn += direction) {
        if (glyphStorage[posn] == 0xFFFE) {
            component += 1;
        }
    }

    return component;
}

// This is basically prevInternal except that it
// doesn't take a delta argument, and it doesn't
// filter out 0xFFFE glyphs.
le_bool GlyphIterator::findMark2Glyph()
{
    le_int32 newPosition = position;

    do {
        newPosition -= direction;
    } while (newPosition != prevLimit && glyphStorage[newPosition] != 0xFFFE && filterGlyph(newPosition));

    position = newPosition;

    return position != prevLimit;
}

U_NAMESPACE_END
