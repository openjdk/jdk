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
 * (C) Copyright IBM Corp. 1998-2005 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "LEFontInstance.h"
#include "OpenTypeTables.h"
#include "GlyphSubstitutionTables.h"
#include "ContextualSubstSubtables.h"
#include "GlyphIterator.h"
#include "LookupProcessor.h"
#include "CoverageTables.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

/*
    NOTE: This could be optimized somewhat by keeping track
    of the previous sequenceIndex in the loop and doing next()
    or prev() of the delta between that and the current
    sequenceIndex instead of always resetting to the front.
*/
void ContextualSubstitutionBase::applySubstitutionLookups(
        const LookupProcessor *lookupProcessor,
        const LEReferenceToArrayOf<SubstitutionLookupRecord>& substLookupRecordArray,
        le_uint16 substCount,
        GlyphIterator *glyphIterator,
        const LEFontInstance *fontInstance,
        le_int32 position,
        LEErrorCode& success)
{
    if (LE_FAILURE(success)) {
        return;
    }

    GlyphIterator tempIterator(*glyphIterator);
    const SubstitutionLookupRecord *substLookupRecordArrayPtr = substLookupRecordArray.getAlias(); // OK to dereference, range checked against substCount below.

    for (le_int16 subst = 0; subst < substCount && LE_SUCCESS(success); subst += 1) {
        le_uint16 sequenceIndex = SWAPW(substLookupRecordArrayPtr[subst].sequenceIndex);
        le_uint16 lookupListIndex = SWAPW(substLookupRecordArrayPtr[subst].lookupListIndex);

        tempIterator.setCurrStreamPosition(position);
        tempIterator.next(sequenceIndex);

        lookupProcessor->applySingleLookup(lookupListIndex, &tempIterator, fontInstance, success);
    }
}

le_bool ContextualSubstitutionBase::matchGlyphIDs(const LEReferenceToArrayOf<TTGlyphID>& glyphArray, le_uint16 glyphCount,
                                               GlyphIterator *glyphIterator, le_bool backtrack)
{
    le_int32 direction = 1;
    le_int32 match = 0;

    if (backtrack) {
        match = glyphCount -1;
        direction = -1;
    }

    while (glyphCount > 0) {
        if (! glyphIterator->next()) {
            return FALSE;
        }

        TTGlyphID glyph = (TTGlyphID) glyphIterator->getCurrGlyphID();

        if (glyph != SWAPW(glyphArray[match])) {
            return FALSE;
        }

        glyphCount -= 1;
        match += direction;
    }

    return TRUE;
}

le_bool ContextualSubstitutionBase::matchGlyphClasses(
    const LEReferenceToArrayOf<le_uint16> &classArray,
    le_uint16 glyphCount,
    GlyphIterator *glyphIterator,
    const LEReferenceTo<ClassDefinitionTable> &classDefinitionTable,
    LEErrorCode &success,
    le_bool backtrack)
{
    if (LE_FAILURE(success)) { return FALSE; }

    le_int32 direction = 1;
    le_int32 match = 0;

    if (backtrack) {
        match = glyphCount - 1;
        direction = -1;
    }

    while (glyphCount > 0) {
        if (! glyphIterator->next()) {
            return FALSE;
        }

        LEGlyphID glyph = glyphIterator->getCurrGlyphID();
        le_int32 glyphClass = classDefinitionTable->getGlyphClass(classDefinitionTable, glyph, success);
        le_int32 matchClass = SWAPW(classArray[match]);

        if (glyphClass != matchClass) {
            // Some fonts, e.g. Traditional Arabic, have classes
            // in the class array which aren't in the class definition
            // table. If we're looking for such a class, pretend that
            // we found it.
            if (classDefinitionTable->hasGlyphClass(classDefinitionTable, matchClass, success)) {
                return FALSE;
            }
        }

        glyphCount -= 1;
        match += direction;
    }

    return TRUE;
}

le_bool ContextualSubstitutionBase::matchGlyphCoverages(const LEReferenceToArrayOf<Offset> &coverageTableOffsetArray, le_uint16 glyphCount,
GlyphIterator *glyphIterator, const LETableReference &offsetBase, LEErrorCode &success, le_bool backtrack)
{
    le_int32 direction = 1;
    le_int32 glyph = 0;

    if (backtrack) {
        glyph = glyphCount - 1;
        direction = -1;
    }

    while (glyphCount > 0) {
        Offset coverageTableOffset = SWAPW(coverageTableOffsetArray[glyph]);
        LEReferenceTo<CoverageTable> coverageTable(offsetBase, success, coverageTableOffset);

        if (LE_FAILURE(success) || ! glyphIterator->next()) {
            return FALSE;
        }

        if (coverageTable->getGlyphCoverage(coverageTable,
                                            (LEGlyphID) glyphIterator->getCurrGlyphID(),
                                            success) < 0) {
            return FALSE;
        }

        glyphCount -= 1;
        glyph += direction;
    }

    return TRUE;
}

le_uint32 ContextualSubstitutionSubtable::process(const LETableReference &base, const LookupProcessor *lookupProcessor,
                                                  GlyphIterator *glyphIterator,
                                                  const LEFontInstance *fontInstance,
                                                  LEErrorCode& success) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    switch(SWAPW(subtableFormat))
    {
    case 0:
        return 0;

    case 1:
    {
      LEReferenceTo<ContextualSubstitutionFormat1Subtable> subtable(base, success, (const ContextualSubstitutionFormat1Subtable *) this);
      if( LE_FAILURE(success) ) {
        return 0;
      }
      return subtable->process(subtable, lookupProcessor, glyphIterator, fontInstance, success);
    }

    case 2:
    {
      LEReferenceTo<ContextualSubstitutionFormat2Subtable> subtable(base, success, (const ContextualSubstitutionFormat2Subtable *) this);
      if( LE_FAILURE(success) ) {
        return 0;
      }
      return subtable->process(subtable, lookupProcessor, glyphIterator, fontInstance, success);
    }

    case 3:
    {
      LEReferenceTo<ContextualSubstitutionFormat3Subtable> subtable(base, success, (const ContextualSubstitutionFormat3Subtable *) this);
      if( LE_FAILURE(success) ) {
        return 0;
      }
      return subtable->process(subtable, lookupProcessor, glyphIterator, fontInstance, success);
    }

    default:
        return 0;
    }
}

le_uint32 ContextualSubstitutionFormat1Subtable::process(const LETableReference &base, const LookupProcessor *lookupProcessor,
                                                         GlyphIterator *glyphIterator,
                                                         const LEFontInstance *fontInstance,
                                                         LEErrorCode& success) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    LEGlyphID glyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(lookupProcessor->getReference(), glyph, success);
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (coverageIndex >= 0) {
        le_uint16 srSetCount = SWAPW(subRuleSetCount);

        if (coverageIndex < srSetCount) {
            LEReferenceToArrayOf<Offset> subRuleSetTableOffsetArrayRef(base, success,
                    &subRuleSetTableOffsetArray[coverageIndex], 1);
            if (LE_FAILURE(success)) {
                return 0;
            }
            Offset subRuleSetTableOffset = SWAPW(subRuleSetTableOffsetArray[coverageIndex]);
            LEReferenceTo<SubRuleSetTable>
                 subRuleSetTable(base, success, (const SubRuleSetTable *) ((char *) this + subRuleSetTableOffset));
            le_uint16 subRuleCount = SWAPW(subRuleSetTable->subRuleCount);
            le_int32 position = glyphIterator->getCurrStreamPosition();

            LEReferenceToArrayOf<Offset> subRuleTableOffsetArrayRef(base, success,
                    subRuleSetTable->subRuleTableOffsetArray, subRuleCount);
            if (LE_FAILURE(success)) {
                return 0;
            }
            for (le_uint16 subRule = 0; subRule < subRuleCount; subRule += 1) {
                Offset subRuleTableOffset =
                    SWAPW(subRuleSetTable->subRuleTableOffsetArray[subRule]);
                LEReferenceTo<SubRuleTable>
                     subRuleTable(subRuleSetTable, success, subRuleTableOffset);
                le_uint16 matchCount = SWAPW(subRuleTable->glyphCount) - 1;
                le_uint16 substCount = SWAPW(subRuleTable->substCount);
                LEReferenceToArrayOf<TTGlyphID> inputGlyphArray(base, success, subRuleTable->inputGlyphArray, matchCount+2);
                if (LE_FAILURE(success)) { return 0; }
                if (matchGlyphIDs(inputGlyphArray, matchCount, glyphIterator)) {
                  LEReferenceToArrayOf<SubstitutionLookupRecord>
                    substLookupRecordArray(base, success, (const SubstitutionLookupRecord *) &subRuleTable->inputGlyphArray[matchCount], substCount);

                    applySubstitutionLookups(lookupProcessor, substLookupRecordArray, substCount, glyphIterator, fontInstance, position, success);

                    return matchCount + 1;
                }

                glyphIterator->setCurrStreamPosition(position);
            }
        }

        // XXX If we get here, the table is mal-formed...
    }

    return 0;
}

le_uint32 ContextualSubstitutionFormat2Subtable::process(const LETableReference &base,
         const LookupProcessor *lookupProcessor,
         GlyphIterator *glyphIterator,
         const LEFontInstance *fontInstance,
         LEErrorCode& success) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    LEGlyphID glyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(lookupProcessor->getReference(), glyph, success);
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (coverageIndex >= 0) {
        LEReferenceTo<ClassDefinitionTable> classDefinitionTable(base, success,
                                                                 (const ClassDefinitionTable *) ((char *) this + SWAPW(classDefTableOffset)));
        le_uint16 scSetCount = SWAPW(subClassSetCount);
        le_int32 setClass = classDefinitionTable->getGlyphClass(classDefinitionTable,
                                                                glyphIterator->getCurrGlyphID(),
                                                                success);

        if (setClass < scSetCount) {
            LEReferenceToArrayOf<Offset>
                 subClassSetTableOffsetArrayRef(base, success, subClassSetTableOffsetArray, setClass);
            if (LE_FAILURE(success)) { return 0; }
            if (subClassSetTableOffsetArray[setClass] != 0) {

                Offset subClassSetTableOffset = SWAPW(subClassSetTableOffsetArray[setClass]);
                LEReferenceTo<SubClassSetTable>
                    subClassSetTable(base, success, (const SubClassSetTable *) ((char *) this + subClassSetTableOffset));
                le_uint16 subClassRuleCount = SWAPW(subClassSetTable->subClassRuleCount);
                le_int32 position = glyphIterator->getCurrStreamPosition();
                LEReferenceToArrayOf<Offset>
                    subClassRuleTableOffsetArrayRef(base, success, subClassSetTable->subClassRuleTableOffsetArray, subClassRuleCount);
                if (LE_FAILURE(success)) {
                    return 0;
                }
                for (le_uint16 scRule = 0; scRule < subClassRuleCount; scRule += 1) {
                    Offset subClassRuleTableOffset =
                        SWAPW(subClassSetTable->subClassRuleTableOffsetArray[scRule]);
                    LEReferenceTo<SubClassRuleTable>
                        subClassRuleTable(subClassSetTable, success, subClassRuleTableOffset);
                    le_uint16 matchCount = SWAPW(subClassRuleTable->glyphCount) - 1;
                    le_uint16 substCount = SWAPW(subClassRuleTable->substCount);

                    LEReferenceToArrayOf<le_uint16> classArray(base, success, subClassRuleTable->classArray, matchCount+1);

                    if (LE_FAILURE(success)) { return 0; }
                    if (matchGlyphClasses(classArray, matchCount, glyphIterator, classDefinitionTable, success)) {
                        LEReferenceToArrayOf<SubstitutionLookupRecord>
                          substLookupRecordArray(base, success, (const SubstitutionLookupRecord *) &subClassRuleTable->classArray[matchCount], substCount);

                        applySubstitutionLookups(lookupProcessor, substLookupRecordArray, substCount, glyphIterator, fontInstance, position, success);

                        return matchCount + 1;
                    }

                    glyphIterator->setCurrStreamPosition(position);
                }
            }
        }

        // XXX If we get here, the table is mal-formed...
    }

    return 0;
}

le_uint32 ContextualSubstitutionFormat3Subtable::process(const LETableReference &base,
                                                         const LookupProcessor *lookupProcessor,
                                                         GlyphIterator *glyphIterator,
                                                         const LEFontInstance *fontInstance,
                                                         LEErrorCode& success)const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    le_uint16 gCount = SWAPW(glyphCount);
    le_uint16 subCount = SWAPW(substCount);
    le_int32 position = glyphIterator->getCurrStreamPosition();

    // Back up the glyph iterator so that we
    // can call next() before the check, which
    // will leave it pointing at the last glyph
    // that matched when we're done.
    glyphIterator->prev();

    LEReferenceToArrayOf<Offset> covTableOffsetArray(base, success, coverageTableOffsetArray, gCount);

    if( LE_FAILURE(success) ) { return 0; }

    if (ContextualSubstitutionBase::matchGlyphCoverages(covTableOffsetArray, gCount, glyphIterator, base, success)) {
        LEReferenceToArrayOf<SubstitutionLookupRecord>
          substLookupRecordArray(base, success, (const SubstitutionLookupRecord *) &coverageTableOffsetArray[gCount], subCount);

        ContextualSubstitutionBase::applySubstitutionLookups(lookupProcessor, substLookupRecordArray, subCount, glyphIterator, fontInstance, position, success);

        return gCount + 1;
    }

    glyphIterator->setCurrStreamPosition(position);

    return 0;
}

le_uint32 ChainingContextualSubstitutionSubtable::process(const LEReferenceTo<ChainingContextualSubstitutionSubtable> &base,
                                                          const LookupProcessor *lookupProcessor,
                                                          GlyphIterator *glyphIterator,
                                                          const LEFontInstance *fontInstance,
                                                          LEErrorCode& success) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    switch(SWAPW(subtableFormat))
    {
    case 0:
        return 0;

    case 1:
    {
      LEReferenceTo<ChainingContextualSubstitutionFormat1Subtable> subtable(base, success,  (ChainingContextualSubstitutionFormat1Subtable *) this);
      if(LE_FAILURE(success)) return 0;
      return subtable->process(subtable, lookupProcessor, glyphIterator, fontInstance, success);
    }

    case 2:
    {
      LEReferenceTo<ChainingContextualSubstitutionFormat2Subtable> subtable(base, success, (const ChainingContextualSubstitutionFormat2Subtable *) this);
      if( LE_FAILURE(success) ) { return 0; }
      return subtable->process(subtable, lookupProcessor, glyphIterator, fontInstance, success);
    }

    case 3:
    {
      LEReferenceTo<ChainingContextualSubstitutionFormat3Subtable> subtable(base, success, (const ChainingContextualSubstitutionFormat3Subtable *) this);
      if( LE_FAILURE(success) ) { return 0; }
      return subtable->process(subtable, lookupProcessor, glyphIterator, fontInstance, success);
    }

    default:
        return 0;
    }
}

// NOTE: This could be a #define, but that seems to confuse
// the Visual Studio .NET 2003 compiler on the calls to the
// GlyphIterator constructor. It somehow can't decide if
// emptyFeatureList matches an le_uint32 or an le_uint16...
static const FeatureMask emptyFeatureList = 0x00000000UL;

le_uint32 ChainingContextualSubstitutionFormat1Subtable::process(const LETableReference &base, const LookupProcessor *lookupProcessor,
                                                                 GlyphIterator *glyphIterator,
                                                                 const LEFontInstance *fontInstance,
                                                                 LEErrorCode& success) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    LEGlyphID glyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(lookupProcessor->getReference(), glyph, success);
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (coverageIndex >= 0) {
        le_uint16 srSetCount = SWAPW(chainSubRuleSetCount);

        if (coverageIndex < srSetCount) {
            LEReferenceToArrayOf<Offset>
                chainSubRuleSetTableOffsetArrayRef(base, success, chainSubRuleSetTableOffsetArray, coverageIndex);
            if (LE_FAILURE(success)) {
                return 0;
            }
            Offset chainSubRuleSetTableOffset = SWAPW(chainSubRuleSetTableOffsetArray[coverageIndex]);
            LEReferenceTo<ChainSubRuleSetTable>
                 chainSubRuleSetTable(base, success, (const ChainSubRuleSetTable *) ((char *) this + chainSubRuleSetTableOffset));
            le_uint16 chainSubRuleCount = SWAPW(chainSubRuleSetTable->chainSubRuleCount);
            le_int32 position = glyphIterator->getCurrStreamPosition();
            GlyphIterator tempIterator(*glyphIterator, emptyFeatureList);
            LEReferenceToArrayOf<Offset>
                chainSubRuleTableOffsetArrayRef(base, success, chainSubRuleSetTable->chainSubRuleTableOffsetArray, chainSubRuleCount);
            if (LE_FAILURE(success)) {
                return 0;
            }
            for (le_uint16 subRule = 0; subRule < chainSubRuleCount; subRule += 1) {
                Offset chainSubRuleTableOffset =
                    SWAPW(chainSubRuleSetTable->chainSubRuleTableOffsetArray[subRule]);
                LEReferenceTo<ChainSubRuleTable>
                     chainSubRuleTable = LEReferenceTo<ChainSubRuleTable>(chainSubRuleSetTable, success, chainSubRuleTableOffset);
                if( LE_FAILURE(success) ) { return 0; }
                le_uint16 backtrackGlyphCount = SWAPW(chainSubRuleTable->backtrackGlyphCount);
                LEReferenceToArrayOf<TTGlyphID> backtrackGlyphArray(base, success, chainSubRuleTable->backtrackGlyphArray, backtrackGlyphCount);
                if( LE_FAILURE(success) ) { return 0; }
                le_uint16 inputGlyphCount = (le_uint16) SWAPW(chainSubRuleTable->backtrackGlyphArray[backtrackGlyphCount]) - 1;
                LEReferenceToArrayOf<TTGlyphID>   inputGlyphArray(base, success, &chainSubRuleTable->backtrackGlyphArray[backtrackGlyphCount + 1], inputGlyphCount+2);

                if( LE_FAILURE(success) ) { return 0; }
                le_uint16 lookaheadGlyphCount = (le_uint16) SWAPW(inputGlyphArray[inputGlyphCount]);
                LEReferenceToArrayOf<TTGlyphID>   lookaheadGlyphArray(base, success, inputGlyphArray.getAlias(inputGlyphCount + 1,success), lookaheadGlyphCount+2);
                if( LE_FAILURE(success) ) { return 0; }
                le_uint16 substCount = (le_uint16) SWAPW(lookaheadGlyphArray[lookaheadGlyphCount]);

                tempIterator.setCurrStreamPosition(position);

                if (! tempIterator.prev(backtrackGlyphCount)) {
                    continue;
                }

                tempIterator.prev();

                if (! matchGlyphIDs(backtrackGlyphArray, backtrackGlyphCount, &tempIterator, TRUE)) {
                    continue;
                }

                tempIterator.setCurrStreamPosition(position);
                tempIterator.next(inputGlyphCount);
                if (!matchGlyphIDs(lookaheadGlyphArray, lookaheadGlyphCount, &tempIterator)) {
                    continue;
                }

                if (matchGlyphIDs(inputGlyphArray, inputGlyphCount, glyphIterator)) {
                    LEReferenceToArrayOf<SubstitutionLookupRecord>
                      substLookupRecordArray(base, success, (const SubstitutionLookupRecord *) lookaheadGlyphArray.getAlias(lookaheadGlyphCount + 1,success), substCount);

                    applySubstitutionLookups(lookupProcessor, substLookupRecordArray, substCount, glyphIterator, fontInstance, position, success);

                    return inputGlyphCount + 1;
                }

                glyphIterator->setCurrStreamPosition(position);
            }
        }

        // XXX If we get here, the table is mal-formed...
    }

    return 0;
}

le_uint32 ChainingContextualSubstitutionFormat2Subtable::process(const LETableReference &base, const LookupProcessor *lookupProcessor,
                                                                 GlyphIterator *glyphIterator,
                                                                 const LEFontInstance *fontInstance,
                                                                 LEErrorCode& success) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    LEGlyphID glyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(lookupProcessor->getReference(), glyph, success);
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (coverageIndex >= 0) {
        LEReferenceTo<ClassDefinitionTable>
             backtrackClassDefinitionTable(base, success, (const ClassDefinitionTable *) ((char *) this + SWAPW(backtrackClassDefTableOffset)));
        LEReferenceTo<ClassDefinitionTable>
             inputClassDefinitionTable(base, success, (const ClassDefinitionTable *) ((char *) this + SWAPW(inputClassDefTableOffset)));
        LEReferenceTo<ClassDefinitionTable>
             lookaheadClassDefinitionTable(base, success, (const ClassDefinitionTable *) ((char *) this + SWAPW(lookaheadClassDefTableOffset)));
        le_uint16 scSetCount = SWAPW(chainSubClassSetCount);
        le_int32 setClass = inputClassDefinitionTable->getGlyphClass(inputClassDefinitionTable,
                                                                     glyphIterator->getCurrGlyphID(),
                                                                     success);
        LEReferenceToArrayOf<Offset>
            chainSubClassSetTableOffsetArrayRef(base, success, chainSubClassSetTableOffsetArray, setClass);
        if (LE_FAILURE(success)) {
            return 0;
        }

        if (setClass < scSetCount && chainSubClassSetTableOffsetArray[setClass] != 0) {
            Offset chainSubClassSetTableOffset = SWAPW(chainSubClassSetTableOffsetArray[setClass]);
            LEReferenceTo<ChainSubClassSetTable>
                 chainSubClassSetTable(base, success, (const ChainSubClassSetTable *) ((char *) this + chainSubClassSetTableOffset));
            le_uint16 chainSubClassRuleCount = SWAPW(chainSubClassSetTable->chainSubClassRuleCount);
            le_int32 position = glyphIterator->getCurrStreamPosition();
            GlyphIterator tempIterator(*glyphIterator, emptyFeatureList);
            LEReferenceToArrayOf<Offset>
                chainSubClassRuleTableOffsetArrayRef(base, success, chainSubClassSetTable->chainSubClassRuleTableOffsetArray, chainSubClassRuleCount);
            if (LE_FAILURE(success)) {
                return 0;
            }
            for (le_uint16 scRule = 0; scRule < chainSubClassRuleCount; scRule += 1) {
                Offset chainSubClassRuleTableOffset =
                    SWAPW(chainSubClassSetTable->chainSubClassRuleTableOffsetArray[scRule]);
                LEReferenceTo<ChainSubClassRuleTable>
                     chainSubClassRuleTable(chainSubClassSetTable, success, chainSubClassRuleTableOffset);
                le_uint16 backtrackGlyphCount = SWAPW(chainSubClassRuleTable->backtrackGlyphCount);
                le_uint16 inputGlyphCount = SWAPW(chainSubClassRuleTable->backtrackClassArray[backtrackGlyphCount]) - 1;
                LEReferenceToArrayOf<le_uint16>   inputClassArray(base, success, &chainSubClassRuleTable->backtrackClassArray[backtrackGlyphCount + 1],inputGlyphCount+2); // +2 for the lookaheadGlyphCount count
                le_uint16 lookaheadGlyphCount = SWAPW(inputClassArray.getObject(inputGlyphCount, success));
                LEReferenceToArrayOf<le_uint16>   lookaheadClassArray(base, success, inputClassArray.getAlias(inputGlyphCount + 1,success), lookaheadGlyphCount+2); // +2 for the substCount

                if( LE_FAILURE(success) ) { return 0; }
                le_uint16 substCount = SWAPW(lookaheadClassArray[lookaheadGlyphCount]);


                tempIterator.setCurrStreamPosition(position);

                if (! tempIterator.prev(backtrackGlyphCount)) {
                    continue;
                }

                tempIterator.prev();
                LEReferenceToArrayOf<le_uint16>   backtrackClassArray(base, success, chainSubClassRuleTable->backtrackClassArray, backtrackGlyphCount);
                if( LE_FAILURE(success) ) { return 0; }
                if (! matchGlyphClasses(backtrackClassArray, backtrackGlyphCount,
                                        &tempIterator, backtrackClassDefinitionTable, success, TRUE)) {
                    continue;
                }

                tempIterator.setCurrStreamPosition(position);
                tempIterator.next(inputGlyphCount);
                if (! matchGlyphClasses(lookaheadClassArray, lookaheadGlyphCount, &tempIterator, lookaheadClassDefinitionTable, success)) {
                    continue;
                }

                if (matchGlyphClasses(inputClassArray, inputGlyphCount, glyphIterator, inputClassDefinitionTable, success)) {
                    LEReferenceToArrayOf<SubstitutionLookupRecord>
                      substLookupRecordArray(base, success, (const SubstitutionLookupRecord *) lookaheadClassArray.getAlias(lookaheadGlyphCount + 1, success), substCount);
                    if (LE_FAILURE(success)) { return 0; }
                    applySubstitutionLookups(lookupProcessor, substLookupRecordArray, substCount, glyphIterator, fontInstance, position, success);

                    return inputGlyphCount + 1;
                }

                glyphIterator->setCurrStreamPosition(position);
            }
        }

        // XXX If we get here, the table is mal-formed...
    }

    return 0;
}

le_uint32 ChainingContextualSubstitutionFormat3Subtable::process(const LETableReference &base, const LookupProcessor *lookupProcessor,
                                                                 GlyphIterator *glyphIterator,
                                                                 const LEFontInstance *fontInstance,
                                                                 LEErrorCode & success) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    le_uint16 backtrkGlyphCount = SWAPW(backtrackGlyphCount);
    LEReferenceToArrayOf<Offset> backtrackGlyphArrayRef(base, success, backtrackCoverageTableOffsetArray, backtrkGlyphCount);
    if (LE_FAILURE(success)) {
        return 0;
    }
    le_uint16 inputGlyphCount = (le_uint16) SWAPW(backtrackCoverageTableOffsetArray[backtrkGlyphCount]);
    LEReferenceToArrayOf<Offset>   inputCoverageTableOffsetArray(base, success, &backtrackCoverageTableOffsetArray[backtrkGlyphCount + 1], inputGlyphCount+2); // offset
    if (LE_FAILURE(success)) { return 0; }
    const le_uint16 lookaheadGlyphCount = (le_uint16) SWAPW(inputCoverageTableOffsetArray[inputGlyphCount]);
    LEReferenceToArrayOf<Offset>   lookaheadCoverageTableOffsetArray(base, success, inputCoverageTableOffsetArray.getAlias(inputGlyphCount + 1, success), lookaheadGlyphCount+2);

    if( LE_FAILURE(success) ) { return 0; }
    le_uint16 substCount = (le_uint16) SWAPW(lookaheadCoverageTableOffsetArray[lookaheadGlyphCount]);
    le_int32 position = glyphIterator->getCurrStreamPosition();
    GlyphIterator tempIterator(*glyphIterator, emptyFeatureList);

    if (! tempIterator.prev(backtrkGlyphCount)) {
        return 0;
    }

    tempIterator.prev();
    if (! ContextualSubstitutionBase::matchGlyphCoverages(backtrackCoverageTableOffsetArray,
                       backtrkGlyphCount, &tempIterator, base, success, TRUE)) {
        return 0;
    }

    tempIterator.setCurrStreamPosition(position);
    tempIterator.next(inputGlyphCount - 1);
    if (! ContextualSubstitutionBase::matchGlyphCoverages(lookaheadCoverageTableOffsetArray,
                        lookaheadGlyphCount, &tempIterator, base, success)) {
        return 0;
    }

    // Back up the glyph iterator so that we
    // can call next() before the check, which
    // will leave it pointing at the last glyph
    // that matched when we're done.
    glyphIterator->prev();

    if (ContextualSubstitutionBase::matchGlyphCoverages(inputCoverageTableOffsetArray,
                                                        inputGlyphCount, glyphIterator, base, success)) {
        LEReferenceToArrayOf<SubstitutionLookupRecord>
          substLookupRecordArray(base, success,
                                 (const SubstitutionLookupRecord *) lookaheadCoverageTableOffsetArray.getAlias(lookaheadGlyphCount + 1,success), substCount);

        ContextualSubstitutionBase::applySubstitutionLookups(lookupProcessor, substLookupRecordArray, substCount, glyphIterator, fontInstance, position, success);

        return inputGlyphCount;
    }

    glyphIterator->setCurrStreamPosition(position);

    return 0;
}

U_NAMESPACE_END
