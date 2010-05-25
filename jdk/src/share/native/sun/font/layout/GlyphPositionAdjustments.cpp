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
#include "GlyphPositionAdjustments.h"
#include "LEGlyphStorage.h"
#include "LEFontInstance.h"

U_NAMESPACE_BEGIN

#define CHECK_ALLOCATE_ARRAY(array, type, size) \
    if (array == NULL) { \
        array = (type *) new type[size]; \
    }

GlyphPositionAdjustments::GlyphPositionAdjustments(le_int32 glyphCount)
    : fGlyphCount(glyphCount), fEntryExitPoints(NULL), fAdjustments(NULL)
{
    fAdjustments = (Adjustment *) new Adjustment[glyphCount];
}

GlyphPositionAdjustments::~GlyphPositionAdjustments()
{
    delete[] fEntryExitPoints;
    delete[] fAdjustments;
}

const LEPoint *GlyphPositionAdjustments::getEntryPoint(le_int32 index, LEPoint &entryPoint) const
{
    if (fEntryExitPoints == NULL) {
        return NULL;
    }

    return fEntryExitPoints[index].getEntryPoint(entryPoint);
}

const LEPoint *GlyphPositionAdjustments::getExitPoint(le_int32 index, LEPoint &exitPoint)const
{
    if (fEntryExitPoints == NULL) {
        return NULL;
    }

    return fEntryExitPoints[index].getExitPoint(exitPoint);
}

void GlyphPositionAdjustments::setEntryPoint(le_int32 index, LEPoint &newEntryPoint, le_bool baselineIsLogicalEnd)
{
    CHECK_ALLOCATE_ARRAY(fEntryExitPoints, EntryExitPoint, fGlyphCount);

    fEntryExitPoints[index].setEntryPoint(newEntryPoint, baselineIsLogicalEnd);
}

void GlyphPositionAdjustments::setExitPoint(le_int32 index, LEPoint &newExitPoint, le_bool baselineIsLogicalEnd)
{
    CHECK_ALLOCATE_ARRAY(fEntryExitPoints, EntryExitPoint, fGlyphCount);

    fEntryExitPoints[index].setExitPoint(newExitPoint, baselineIsLogicalEnd);
}

void GlyphPositionAdjustments::setCursiveGlyph(le_int32 index, le_bool baselineIsLogicalEnd)
{
    CHECK_ALLOCATE_ARRAY(fEntryExitPoints, EntryExitPoint, fGlyphCount);

    fEntryExitPoints[index].setCursiveGlyph(baselineIsLogicalEnd);
}

void GlyphPositionAdjustments::applyCursiveAdjustments(LEGlyphStorage &glyphStorage, le_bool rightToLeft, const LEFontInstance *fontInstance)
{
    if (! hasCursiveGlyphs()) {
        return;
    }

    le_int32 start = 0, end = fGlyphCount, dir = 1;
    le_int32 firstExitPoint = -1, lastExitPoint = -1;
    LEPoint entryAnchor, exitAnchor, pixels;
    LEGlyphID lastExitGlyphID = 0;
    float baselineAdjustment = 0;

    // This removes a possible warning about
    // using exitAnchor before it's been initialized.
    exitAnchor.fX = exitAnchor.fY = 0;

    if (rightToLeft) {
        start = fGlyphCount - 1;
        end = -1;
        dir = -1;
    }

    for (le_int32 i = start; i != end; i += dir) {
        LEGlyphID glyphID = glyphStorage[i];

        if (isCursiveGlyph(i)) {
            if (lastExitPoint >= 0 && getEntryPoint(i, entryAnchor) != NULL) {
                float anchorDiffX = exitAnchor.fX - entryAnchor.fX;
                float anchorDiffY = exitAnchor.fY - entryAnchor.fY;

                baselineAdjustment += anchorDiffY;
                adjustYPlacement(i, baselineAdjustment);

                if (rightToLeft) {
                    LEPoint secondAdvance;

                    fontInstance->getGlyphAdvance(glyphID, pixels);
                    fontInstance->pixelsToUnits(pixels, secondAdvance);

                    adjustXAdvance(i, -(anchorDiffX + secondAdvance.fX));
                } else {
                    LEPoint firstAdvance;

                    fontInstance->getGlyphAdvance(lastExitGlyphID, pixels);
                    fontInstance->pixelsToUnits(pixels, firstAdvance);

                    adjustXAdvance(lastExitPoint, anchorDiffX - firstAdvance.fX);
                }
            }

            lastExitPoint = i;

            if (getExitPoint(i, exitAnchor) != NULL) {
                if (firstExitPoint < 0) {
                    firstExitPoint = i;
                }

                lastExitGlyphID = glyphID;
            } else {
                if (baselineIsLogicalEnd(i) && firstExitPoint >= 0 && lastExitPoint >= 0) {
                    le_int32 limit = lastExitPoint + dir;

                    for (le_int32 j = firstExitPoint; j != limit; j += dir) {
                        if (isCursiveGlyph(j)) {
                            adjustYPlacement(j, -baselineAdjustment);
                        }
                    }
                }

                firstExitPoint = lastExitPoint = -1;
                baselineAdjustment = 0;
            }
        }
    }
}

LEPoint *GlyphPositionAdjustments::EntryExitPoint::getEntryPoint(LEPoint &entryPoint) const
{
    if (fFlags & EEF_HAS_ENTRY_POINT) {
        entryPoint = fEntryPoint;
        return &entryPoint;
    }

    return NULL;
}

LEPoint *GlyphPositionAdjustments::EntryExitPoint::getExitPoint(LEPoint &exitPoint) const
{
    if (fFlags & EEF_HAS_EXIT_POINT) {
        exitPoint = fExitPoint;
        return &exitPoint;
    }

    return NULL;
}

U_NAMESPACE_END
