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
 **********************************************************************
 *   Copyright (C) 1998-2005, International Business Machines
 *   Corporation and others.  All Rights Reserved.
 **********************************************************************
 */

#include "LETypes.h"
#include "LEInsertionList.h"
#include "LEGlyphStorage.h"

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(LEGlyphStorage)

LEGlyphStorage::LEGlyphStorage()
    : fGlyphCount(0), fGlyphs(NULL), fCharIndices(NULL), fPositions(NULL),
      fAuxData(NULL), fInsertionList(NULL), fSrcIndex(0), fDestIndex(0)
{
    // nothing else to do!
}

LEGlyphStorage::~LEGlyphStorage()
{
    reset();
}

void LEGlyphStorage::reset()
{
    fGlyphCount = 0;

    if (fPositions != NULL) {
        LE_DELETE_ARRAY(fPositions);
        fPositions = NULL;
    }

    if (fAuxData != NULL) {
        LE_DELETE_ARRAY(fAuxData);
        fAuxData = NULL;
    }

    if (fInsertionList != NULL) {
        delete fInsertionList;
        fInsertionList = NULL;
    }

    if (fCharIndices != NULL) {
        LE_DELETE_ARRAY(fCharIndices);
        fCharIndices = NULL;
    }

    if (fGlyphs != NULL) {
        LE_DELETE_ARRAY(fGlyphs);
        fGlyphs = NULL;
    }
}

// FIXME: This might get called more than once, for various reasons. Is
// testing for pre-existing glyph and charIndices arrays good enough?
void LEGlyphStorage::allocateGlyphArray(le_int32 initialGlyphCount, le_bool rightToLeft, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return;
    }

    if (initialGlyphCount <= 0) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return;
    }

    if (fGlyphs == NULL) {
        fGlyphCount = initialGlyphCount;
        fGlyphs = LE_NEW_ARRAY(LEGlyphID, fGlyphCount);

        if (fGlyphs == NULL) {
            success = LE_MEMORY_ALLOCATION_ERROR;
            return;
        }
    }

    if (fCharIndices == NULL) {
        fCharIndices = LE_NEW_ARRAY(le_int32, fGlyphCount);

        if (fCharIndices == NULL) {
            LE_DELETE_ARRAY(fGlyphs);
            fGlyphs = NULL;
            success = LE_MEMORY_ALLOCATION_ERROR;
            return;
        }

        // Initialize the charIndices array
        le_int32 i, count = fGlyphCount, dir = 1, out = 0;

        if (rightToLeft) {
            out = fGlyphCount - 1;
            dir = -1;
        }

        for (i = 0; i < count; i += 1, out += dir) {
            fCharIndices[out] = i;
        }
    }

    if (fInsertionList == NULL) {
        // FIXME: check this for failure?
        fInsertionList = new LEInsertionList(rightToLeft);
    }
}

// FIXME: do we want to initialize the positions to [0, 0]?
le_int32 LEGlyphStorage::allocatePositions(LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return -1;
    }

    fPositions = LE_NEW_ARRAY(float, 2 * (fGlyphCount + 1));

    if (fPositions == NULL) {
        success = LE_MEMORY_ALLOCATION_ERROR;
        return -1;
    }

    return fGlyphCount;
}

// FIXME: do we want to initialize the aux data to NULL?
le_int32 LEGlyphStorage::allocateAuxData(LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return -1;
    }

    fAuxData = LE_NEW_ARRAY(le_uint32, fGlyphCount);

    if (fAuxData == NULL) {
        success = LE_MEMORY_ALLOCATION_ERROR;
        return -1;
    }

    return fGlyphCount;
}

void LEGlyphStorage::getCharIndices(le_int32 charIndices[], le_int32 indexBase, LEErrorCode &success) const
{
    le_int32 i;

    if (LE_FAILURE(success)) {
        return;
    }

    if (charIndices == NULL) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return;
    }

    if (fCharIndices == NULL) {
        success = LE_NO_LAYOUT_ERROR;
        return;
    }

    for (i = 0; i < fGlyphCount; i += 1) {
        charIndices[i] = fCharIndices[i] + indexBase;
    }
}

void LEGlyphStorage::getCharIndices(le_int32 charIndices[], LEErrorCode &success) const
{
    if (LE_FAILURE(success)) {
      return;
    }

    if (charIndices == NULL) {
      success = LE_ILLEGAL_ARGUMENT_ERROR;
      return;
    }

    if (fCharIndices == NULL) {
      success = LE_NO_LAYOUT_ERROR;
      return;
    }

    LE_ARRAY_COPY(charIndices, fCharIndices, fGlyphCount);
}

// Copy the glyphs into caller's (32-bit) glyph array, OR in extraBits
void LEGlyphStorage::getGlyphs(le_uint32 glyphs[], le_uint32 extraBits, LEErrorCode &success) const
{
    le_int32 i;

    if (LE_FAILURE(success)) {
        return;
    }

    if (glyphs == NULL) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return;
    }

    if (fGlyphs == NULL) {
        success = LE_NO_LAYOUT_ERROR;
        return;
    }

    for (i = 0; i < fGlyphCount; i += 1) {
        glyphs[i] = fGlyphs[i] | extraBits;
    }
}

void LEGlyphStorage::getGlyphs(LEGlyphID glyphs[], LEErrorCode &success) const
{
    if (LE_FAILURE(success)) {
      return;
    }

    if (glyphs == NULL) {
      success = LE_ILLEGAL_ARGUMENT_ERROR;
      return;
    }

    if (fGlyphs == NULL) {
      success = LE_NO_LAYOUT_ERROR;
      return;
    }

    LE_ARRAY_COPY(glyphs, fGlyphs, fGlyphCount);
}

LEGlyphID LEGlyphStorage::getGlyphID(le_int32 glyphIndex, LEErrorCode &success) const
{
    if (LE_FAILURE(success)) {
        return 0xFFFF;
    }

    if (fGlyphs == NULL) {
        success = LE_NO_LAYOUT_ERROR;
        return 0xFFFF;
    }

    if (glyphIndex < 0 || glyphIndex >= fGlyphCount) {
        success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
        return 0xFFFF;
    }

    return fGlyphs[glyphIndex];
}

void LEGlyphStorage::setGlyphID(le_int32 glyphIndex, LEGlyphID glyphID, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return;
    }

    if (fGlyphs == NULL) {
        success = LE_NO_LAYOUT_ERROR;
        return;
    }

    if (glyphIndex < 0 || glyphIndex >= fGlyphCount) {
        success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
        return;
    }

    fGlyphs[glyphIndex] = glyphID;
}

le_int32 LEGlyphStorage::getCharIndex(le_int32 glyphIndex, LEErrorCode &success) const
{
    if (LE_FAILURE(success)) {
        return -1;
    }

    if (fCharIndices == NULL) {
        success = LE_NO_LAYOUT_ERROR;
        return -1;
    }

    if (glyphIndex < 0 || glyphIndex >= fGlyphCount) {
        success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
        return -1;
    }

    return fCharIndices[glyphIndex];
}

void LEGlyphStorage::setCharIndex(le_int32 glyphIndex, le_int32 charIndex, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return;
    }

    if (fCharIndices == NULL) {
        success = LE_NO_LAYOUT_ERROR;
        return;
    }

    if (glyphIndex < 0 || glyphIndex >= fGlyphCount) {
        success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
        return;
    }

    fCharIndices[glyphIndex] = charIndex;
}

void LEGlyphStorage::getAuxData(le_uint32 auxData[], LEErrorCode &success) const
{
    if (LE_FAILURE(success)) {
      return;
    }

    if (auxData == NULL) {
      success = LE_ILLEGAL_ARGUMENT_ERROR;
      return;
    }

    if (fAuxData == NULL) {
      success = LE_NO_LAYOUT_ERROR;
      return;
    }

    LE_ARRAY_COPY(auxData, fAuxData, fGlyphCount);
}

le_uint32 LEGlyphStorage::getAuxData(le_int32 glyphIndex, LEErrorCode &success) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (fAuxData == NULL) {
        success = LE_NO_LAYOUT_ERROR;
        return 0;
    }

    if (glyphIndex < 0 || glyphIndex >= fGlyphCount) {
        success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
        return 0;
    }

    return fAuxData[glyphIndex];
}

void LEGlyphStorage::setAuxData(le_int32 glyphIndex, le_uint32 auxData, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return;
    }

    if (fAuxData == NULL) {
        success = LE_NO_LAYOUT_ERROR;
        return;
    }

    if (glyphIndex < 0 || glyphIndex >= fGlyphCount) {
        success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
        return;
    }

    fAuxData[glyphIndex] = auxData;
}

void LEGlyphStorage::getGlyphPositions(float positions[], LEErrorCode &success) const
{
    if (LE_FAILURE(success)) {
      return;
    }

    if (positions == NULL) {
      success = LE_ILLEGAL_ARGUMENT_ERROR;
      return;
    }

    if (fPositions == NULL) {
      success = LE_NO_LAYOUT_ERROR;
      return;
    }

    LE_ARRAY_COPY(positions, fPositions, fGlyphCount * 2 + 2);
}

void LEGlyphStorage::getGlyphPosition(le_int32 glyphIndex, float &x, float &y, LEErrorCode &success) const
{
    if (LE_FAILURE(success)) {
      return;
    }

    if (glyphIndex < 0 || glyphIndex > fGlyphCount) {
      success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
      return;
    }

    if (fPositions == NULL) {
      success = LE_NO_LAYOUT_ERROR;
      return;
    }

    x = fPositions[glyphIndex * 2];
    y = fPositions[glyphIndex * 2 + 1];
}

void LEGlyphStorage::setPosition(le_int32 glyphIndex, float x, float y, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return;
    }

    if (glyphIndex < 0 || glyphIndex > fGlyphCount) {
      success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
      return;
    }

    fPositions[glyphIndex * 2]     = x;
    fPositions[glyphIndex * 2 + 1] = y;
}

void LEGlyphStorage::adjustPosition(le_int32 glyphIndex, float xAdjust, float yAdjust, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return;
    }

    if (glyphIndex < 0 || glyphIndex > fGlyphCount) {
      success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
      return;
    }

    fPositions[glyphIndex * 2]     += xAdjust;
    fPositions[glyphIndex * 2 + 1] += yAdjust;
}

void LEGlyphStorage::adoptGlyphArray(LEGlyphStorage &from)
{
    if (fGlyphs != NULL) {
        LE_DELETE_ARRAY(fGlyphs);
    }

    fGlyphs = from.fGlyphs;
    from.fGlyphs = NULL;

    if (fInsertionList != NULL) {
        delete fInsertionList;
    }

    fInsertionList = from.fInsertionList;
    from.fInsertionList = NULL;
}

void LEGlyphStorage::adoptCharIndicesArray(LEGlyphStorage &from)
{
    if (fCharIndices != NULL) {
        LE_DELETE_ARRAY(fCharIndices);
    }

    fCharIndices = from.fCharIndices;
    from.fCharIndices = NULL;
}

void LEGlyphStorage::adoptPositionArray(LEGlyphStorage &from)
{
    if (fPositions != NULL) {
        LE_DELETE_ARRAY(fPositions);
    }

    fPositions = from.fPositions;
    from.fPositions = NULL;
}

void LEGlyphStorage::adoptAuxDataArray(LEGlyphStorage &from)
{
    if (fAuxData != NULL) {
        LE_DELETE_ARRAY(fAuxData);
    }

    fAuxData = from.fAuxData;
    from.fAuxData = NULL;
}

void LEGlyphStorage::adoptGlyphCount(LEGlyphStorage &from)
{
    fGlyphCount = from.fGlyphCount;
}

void LEGlyphStorage::adoptGlyphCount(le_int32 newGlyphCount)
{
    fGlyphCount = newGlyphCount;
}

// FIXME: add error checking?
LEGlyphID *LEGlyphStorage::insertGlyphs(le_int32  atIndex, le_int32 insertCount)
{
    return fInsertionList->insert(atIndex, insertCount);
}

le_int32 LEGlyphStorage::applyInsertions()
{
    le_int32 growAmount = fInsertionList->getGrowAmount();

    if (growAmount == 0) {
        return fGlyphCount;
    }

    le_int32 newGlyphCount = fGlyphCount + growAmount;

    fGlyphs      = (LEGlyphID *) LE_GROW_ARRAY(fGlyphs,      newGlyphCount);
    fCharIndices = (le_int32 *)  LE_GROW_ARRAY(fCharIndices, newGlyphCount);

    if (fAuxData != NULL) {
        fAuxData = (le_uint32 *) LE_GROW_ARRAY(fAuxData,     newGlyphCount);
    }

    fSrcIndex  = fGlyphCount - 1;
    fDestIndex = newGlyphCount - 1;

#if 0
    // If the current position is at the end of the array
    // update it to point to the end of the new array. The
    // insertion callback will handle all other cases.
    // FIXME: this is left over from GlyphIterator, but there's no easy
    // way to implement this here... it seems that GlyphIterator doesn't
    // really need it 'cause the insertions don't get  applied until after a
    // complete pass over the glyphs, after which the iterator gets reset anyhow...
    // probably better to just document that for LEGlyphStorage and GlyphIterator...
    if (position == glyphCount) {
        position = newGlyphCount;
    }
#endif

    fInsertionList->applyInsertions(this);

    fInsertionList->reset();

    return fGlyphCount = newGlyphCount;
}

le_bool LEGlyphStorage::applyInsertion(le_int32 atPosition, le_int32 count, LEGlyphID newGlyphs[])
{
#if 0
    // if the current position is within the block we're shifting
    // it needs to be updated to the current glyph's
    // new location.
    // FIXME: this is left over from GlyphIterator, but there's no easy
    // way to implement this here... it seems that GlyphIterator doesn't
    // really need it 'cause the insertions don't get  applied until after a
    // complete pass over the glyphs, after which the iterator gets reset anyhow...
    // probably better to just document that for LEGlyphStorage and GlyphIterator...
    if (position >= atPosition && position <= fSrcIndex) {
        position += fDestIndex - fSrcIndex;
    }
#endif

    if (fAuxData != NULL) {
        le_int32 src = fSrcIndex, dest = fDestIndex;

        while (src > atPosition) {
            fAuxData[dest--] = fAuxData[src--];
        }

        for (le_int32 i = count - 1; i >= 0; i -= 1) {
            fAuxData[dest--] = fAuxData[atPosition];
        }
    }

    while (fSrcIndex > atPosition) {
        fGlyphs[fDestIndex]      = fGlyphs[fSrcIndex];
        fCharIndices[fDestIndex] = fCharIndices[fSrcIndex];

        fDestIndex -= 1;
        fSrcIndex  -= 1;
    }

    for (le_int32 i = count - 1; i >= 0; i -= 1) {
        fGlyphs[fDestIndex]      = newGlyphs[i];
        fCharIndices[fDestIndex] = fCharIndices[atPosition];

        fDestIndex -= 1;
    }

    // the source glyph we're pointing at
    // just got replaced by the insertion
    fSrcIndex -= 1;

    return FALSE;
}

U_NAMESPACE_END

