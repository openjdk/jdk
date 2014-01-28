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

#ifndef __GLYPHITERATOR_H
#define __GLYPHITERATOR_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "OpenTypeTables.h"
#include "GlyphDefinitionTables.h"

U_NAMESPACE_BEGIN

class LEGlyphStorage;
class GlyphPositionAdjustments;

class GlyphIterator : public UMemory {
public:
    GlyphIterator(LEGlyphStorage &theGlyphStorage, GlyphPositionAdjustments *theGlyphPositionAdjustments, le_bool rightToLeft, le_uint16 theLookupFlags,
                  FeatureMask theFeatureMask, const LEReferenceTo<GlyphDefinitionTableHeader> &theGlyphDefinitionTableHeader, LEErrorCode &success);

    GlyphIterator(GlyphIterator &that);

    GlyphIterator(GlyphIterator &that, FeatureMask newFeatureMask);

    GlyphIterator(GlyphIterator &that, le_uint16 newLookupFlags);

    virtual ~GlyphIterator();

    void reset(le_uint16 newLookupFlags, LETag newFeatureTag);

    le_bool next(le_uint32 delta = 1);
    le_bool prev(le_uint32 delta = 1);
    le_bool findFeatureTag();

    le_bool isRightToLeft() const;
    le_bool ignoresMarks() const;

    le_bool baselineIsLogicalEnd() const;

    LEGlyphID getCurrGlyphID() const;
    le_int32  getCurrStreamPosition() const;

    le_int32  getMarkComponent(le_int32 markPosition) const;
    le_bool   findMark2Glyph();

    void getCursiveEntryPoint(LEPoint &entryPoint) const;
    void getCursiveExitPoint(LEPoint &exitPoint) const;

    void setCurrGlyphID(TTGlyphID glyphID);
    void setCurrStreamPosition(le_int32 position);
    void setCurrGlyphBaseOffset(le_int32 baseOffset);
    void adjustCurrGlyphPositionAdjustment(float xPlacementAdjust, float yPlacementAdjust,
                                           float xAdvanceAdjust,   float yAdvanceAdjust);

    void setCurrGlyphPositionAdjustment(float xPlacementAdjust, float yPlacementAdjust,
                                        float xAdvanceAdjust,   float yAdvanceAdjust);

    void clearCursiveEntryPoint();
    void clearCursiveExitPoint();
    void setCursiveEntryPoint(LEPoint &entryPoint);
    void setCursiveExitPoint(LEPoint &exitPoint);
    void setCursiveGlyph();

    LEGlyphID *insertGlyphs(le_int32 count, LEErrorCode& success);
    le_int32 applyInsertions();

private:
    le_bool filterGlyph(le_uint32 index);
    le_bool hasFeatureTag(le_bool matchGroup) const;
    le_bool nextInternal(le_uint32 delta = 1);
    le_bool prevInternal(le_uint32 delta = 1);

    le_int32  direction;
    le_int32  position;
    le_int32  nextLimit;
    le_int32  prevLimit;

    LEGlyphStorage &glyphStorage;
    GlyphPositionAdjustments *glyphPositionAdjustments;

    le_int32    srcIndex;
    le_int32    destIndex;
    le_uint16   lookupFlags;
    FeatureMask featureMask;
    le_int32    glyphGroup;

    LEReferenceTo<GlyphClassDefinitionTable> glyphClassDefinitionTable;
    LEReferenceTo<MarkAttachClassDefinitionTable> markAttachClassDefinitionTable;

    GlyphIterator &operator=(const GlyphIterator &other); // forbid copying of this class

    struct {
      LEGlyphID   id;
      le_bool     result;
    } filterCache;
    le_bool   filterCacheValid;

    void filterResetCache(void);
};

U_NAMESPACE_END
#endif
