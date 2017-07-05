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
        const SubstitutionLookupRecord *substLookupRecordArray,
        le_uint16 substCount,
        GlyphIterator *glyphIterator,
        const LEFontInstance *fontInstance,
        le_int32 position)
{
    GlyphIterator tempIterator(*glyphIterator);

    for (le_int16 subst = 0; subst < substCount; subst += 1) {
        le_uint16 sequenceIndex = SWAPW(substLookupRecordArray[subst].sequenceIndex);
        le_uint16 lookupListIndex = SWAPW(substLookupRecordArray[subst].lookupListIndex);

        tempIterator.setCurrStreamPosition(position);
        tempIterator.next(sequenceIndex);

        lookupProcessor->applySingleLookup(lookupListIndex, &tempIterator, fontInstance);
    }
}

le_bool ContextualSubstitutionBase::matchGlyphIDs(const TTGlyphID *glyphArray, le_uint16 glyphCount,
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

le_bool ContextualSubstitutionBase::matchGlyphClasses(const le_uint16 *classArray, le_uint16 glyphCount,
                                               GlyphIterator *glyphIterator,
                                               const ClassDefinitionTable *classDefinitionTable,
                                               le_bool backtrack)
{
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
        le_int32 glyphClass = classDefinitionTable->getGlyphClass(glyph);
        le_int32 matchClass = SWAPW(classArray[match]);

        if (glyphClass != matchClass) {
            // Some fonts, e.g. Traditional Arabic, have classes
            // in the class array which aren't in the class definition
            // table. If we're looking for such a class, pretend that
            // we found it.
            if (classDefinitionTable->hasGlyphClass(matchClass)) {
                return FALSE;
            }
        }

        glyphCount -= 1;
        match += direction;
    }

    return TRUE;
}

le_bool ContextualSubstitutionBase::matchGlyphCoverages(const Offset *coverageTableOffsetArray, le_uint16 glyphCount,
                                                     GlyphIterator *glyphIterator, const char *offsetBase, le_bool backtrack)
{
    le_int32 direction = 1;
    le_int32 glyph = 0;

    if (backtrack) {
        glyph = glyphCount - 1;
        direction = -1;
    }

    while (glyphCount > 0) {
        Offset coverageTableOffset = SWAPW(coverageTableOffsetArray[glyph]);
        const CoverageTable *coverageTable = (const CoverageTable *) (offsetBase + coverageTableOffset);

        if (! glyphIterator->next()) {
            return FALSE;
        }

        if (coverageTable->getGlyphCoverage((LEGlyphID) glyphIterator->getCurrGlyphID()) < 0) {
            return FALSE;
        }

        glyphCount -= 1;
        glyph += direction;
    }

    return TRUE;
}

le_uint32 ContextualSubstitutionSubtable::process(const LookupProcessor *lookupProcessor, GlyphIterator *glyphIterator,
                                               const LEFontInstance *fontInstance) const
{
    switch(SWAPW(subtableFormat))
    {
    case 0:
        return 0;

    case 1:
    {
        const ContextualSubstitutionFormat1Subtable *subtable = (const ContextualSubstitutionFormat1Subtable *) this;

        return subtable->process(lookupProcessor, glyphIterator, fontInstance);
    }

    case 2:
    {
        const ContextualSubstitutionFormat2Subtable *subtable = (const ContextualSubstitutionFormat2Subtable *) this;

        return subtable->process(lookupProcessor, glyphIterator, fontInstance);
    }

    case 3:
    {
        const ContextualSubstitutionFormat3Subtable *subtable = (const ContextualSubstitutionFormat3Subtable *) this;

        return subtable->process(lookupProcessor, glyphIterator, fontInstance);
    }

    default:
        return 0;
    }
}

le_uint32 ContextualSubstitutionFormat1Subtable::process(const LookupProcessor *lookupProcessor, GlyphIterator *glyphIterator,
                                                      const LEFontInstance *fontInstance) const
{
    LEGlyphID glyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(glyph);

    if (coverageIndex >= 0) {
        le_uint16 srSetCount = SWAPW(subRuleSetCount);

        if (coverageIndex < srSetCount) {
            Offset subRuleSetTableOffset = SWAPW(subRuleSetTableOffsetArray[coverageIndex]);
            const SubRuleSetTable *subRuleSetTable =
                (const SubRuleSetTable *) ((char *) this + subRuleSetTableOffset);
            le_uint16 subRuleCount = SWAPW(subRuleSetTable->subRuleCount);
            le_int32 position = glyphIterator->getCurrStreamPosition();

            for (le_uint16 subRule = 0; subRule < subRuleCount; subRule += 1) {
                Offset subRuleTableOffset =
                    SWAPW(subRuleSetTable->subRuleTableOffsetArray[subRule]);
                const SubRuleTable *subRuleTable =
                    (const SubRuleTable *) ((char *) subRuleSetTable + subRuleTableOffset);
                le_uint16 matchCount = SWAPW(subRuleTable->glyphCount) - 1;
                le_uint16 substCount = SWAPW(subRuleTable->substCount);

                if (matchGlyphIDs(subRuleTable->inputGlyphArray, matchCount, glyphIterator)) {
                    const SubstitutionLookupRecord *substLookupRecordArray =
                        (const SubstitutionLookupRecord *) &subRuleTable->inputGlyphArray[matchCount];

                    applySubstitutionLookups(lookupProcessor, substLookupRecordArray, substCount, glyphIterator, fontInstance, position);

                    return matchCount + 1;
                }

                glyphIterator->setCurrStreamPosition(position);
            }
        }

        // XXX If we get here, the table is mal-formed...
    }

    return 0;
}

le_uint32 ContextualSubstitutionFormat2Subtable::process(const LookupProcessor *lookupProcessor, GlyphIterator *glyphIterator,
                                                      const LEFontInstance *fontInstance) const
{
    LEGlyphID glyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(glyph);

    if (coverageIndex >= 0) {
        const ClassDefinitionTable *classDefinitionTable =
            (const ClassDefinitionTable *) ((char *) this + SWAPW(classDefTableOffset));
        le_uint16 scSetCount = SWAPW(subClassSetCount);
        le_int32 setClass = classDefinitionTable->getGlyphClass(glyphIterator->getCurrGlyphID());

        if (setClass < scSetCount && subClassSetTableOffsetArray[setClass] != 0) {
            Offset subClassSetTableOffset = SWAPW(subClassSetTableOffsetArray[setClass]);
            const SubClassSetTable *subClassSetTable =
                (const SubClassSetTable *) ((char *) this + subClassSetTableOffset);
            le_uint16 subClassRuleCount = SWAPW(subClassSetTable->subClassRuleCount);
            le_int32 position = glyphIterator->getCurrStreamPosition();

            for (le_uint16 scRule = 0; scRule < subClassRuleCount; scRule += 1) {
                Offset subClassRuleTableOffset =
                    SWAPW(subClassSetTable->subClassRuleTableOffsetArray[scRule]);
                const SubClassRuleTable *subClassRuleTable =
                    (const SubClassRuleTable *) ((char *) subClassSetTable + subClassRuleTableOffset);
                le_uint16 matchCount = SWAPW(subClassRuleTable->glyphCount) - 1;
                le_uint16 substCount = SWAPW(subClassRuleTable->substCount);

                if (matchGlyphClasses(subClassRuleTable->classArray, matchCount, glyphIterator, classDefinitionTable)) {
                    const SubstitutionLookupRecord *substLookupRecordArray =
                        (const SubstitutionLookupRecord *) &subClassRuleTable->classArray[matchCount];

                    applySubstitutionLookups(lookupProcessor, substLookupRecordArray, substCount, glyphIterator, fontInstance, position);

                    return matchCount + 1;
                }

                glyphIterator->setCurrStreamPosition(position);
            }
        }

        // XXX If we get here, the table is mal-formed...
    }

    return 0;
}

le_uint32 ContextualSubstitutionFormat3Subtable::process(const LookupProcessor *lookupProcessor, GlyphIterator *glyphIterator,
                                                      const LEFontInstance *fontInstance)const
{
    le_uint16 gCount = SWAPW(glyphCount);
    le_uint16 subCount = SWAPW(substCount);
    le_int32 position = glyphIterator->getCurrStreamPosition();

    // Back up the glyph iterator so that we
    // can call next() before the check, which
    // will leave it pointing at the last glyph
    // that matched when we're done.
    glyphIterator->prev();

    if (ContextualSubstitutionBase::matchGlyphCoverages(coverageTableOffsetArray, gCount, glyphIterator, (const char *) this)) {
        const SubstitutionLookupRecord *substLookupRecordArray =
            (const SubstitutionLookupRecord *) &coverageTableOffsetArray[gCount];

        ContextualSubstitutionBase::applySubstitutionLookups(lookupProcessor, substLookupRecordArray, subCount, glyphIterator, fontInstance, position);

        return gCount + 1;
    }

    glyphIterator->setCurrStreamPosition(position);

    return 0;
}

le_uint32 ChainingContextualSubstitutionSubtable::process(const LookupProcessor *lookupProcessor, GlyphIterator *glyphIterator,
                                                       const LEFontInstance *fontInstance) const
{
    switch(SWAPW(subtableFormat))
    {
    case 0:
        return 0;

    case 1:
    {
        const ChainingContextualSubstitutionFormat1Subtable *subtable = (const ChainingContextualSubstitutionFormat1Subtable *) this;

        return subtable->process(lookupProcessor, glyphIterator, fontInstance);
    }

    case 2:
    {
        const ChainingContextualSubstitutionFormat2Subtable *subtable = (const ChainingContextualSubstitutionFormat2Subtable *) this;

        return subtable->process(lookupProcessor, glyphIterator, fontInstance);
    }

    case 3:
    {
        const ChainingContextualSubstitutionFormat3Subtable *subtable = (const ChainingContextualSubstitutionFormat3Subtable *) this;

        return subtable->process(lookupProcessor, glyphIterator, fontInstance);
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

le_uint32 ChainingContextualSubstitutionFormat1Subtable::process(const LookupProcessor *lookupProcessor, GlyphIterator *glyphIterator,
                                                              const LEFontInstance *fontInstance) const
{
    LEGlyphID glyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(glyph);

    if (coverageIndex >= 0) {
        le_uint16 srSetCount = SWAPW(chainSubRuleSetCount);

        if (coverageIndex < srSetCount) {
            Offset chainSubRuleSetTableOffset = SWAPW(chainSubRuleSetTableOffsetArray[coverageIndex]);
            const ChainSubRuleSetTable *chainSubRuleSetTable =
                (const ChainSubRuleSetTable *) ((char *) this + chainSubRuleSetTableOffset);
            le_uint16 chainSubRuleCount = SWAPW(chainSubRuleSetTable->chainSubRuleCount);
            le_int32 position = glyphIterator->getCurrStreamPosition();
            GlyphIterator tempIterator(*glyphIterator, emptyFeatureList);

            for (le_uint16 subRule = 0; subRule < chainSubRuleCount; subRule += 1) {
                Offset chainSubRuleTableOffset =
                    SWAPW(chainSubRuleSetTable->chainSubRuleTableOffsetArray[subRule]);
                const ChainSubRuleTable *chainSubRuleTable =
                    (const ChainSubRuleTable *) ((char *) chainSubRuleSetTable + chainSubRuleTableOffset);
                le_uint16 backtrackGlyphCount = SWAPW(chainSubRuleTable->backtrackGlyphCount);
                le_uint16 inputGlyphCount = (le_uint16) SWAPW(chainSubRuleTable->backtrackGlyphArray[backtrackGlyphCount]) - 1;
                const TTGlyphID *inputGlyphArray = &chainSubRuleTable->backtrackGlyphArray[backtrackGlyphCount + 1];
                le_uint16 lookaheadGlyphCount = (le_uint16) SWAPW(inputGlyphArray[inputGlyphCount]);
                const TTGlyphID *lookaheadGlyphArray = &inputGlyphArray[inputGlyphCount + 1];
                le_uint16 substCount = (le_uint16) SWAPW(lookaheadGlyphArray[lookaheadGlyphCount]);

                tempIterator.setCurrStreamPosition(position);

                if (! tempIterator.prev(backtrackGlyphCount)) {
                    continue;
                }

                tempIterator.prev();
                if (! matchGlyphIDs(chainSubRuleTable->backtrackGlyphArray, backtrackGlyphCount, &tempIterator, TRUE)) {
                    continue;
                }

                tempIterator.setCurrStreamPosition(position);
                tempIterator.next(inputGlyphCount);
                if (!matchGlyphIDs(lookaheadGlyphArray, lookaheadGlyphCount, &tempIterator)) {
                    continue;
                }

                if (matchGlyphIDs(inputGlyphArray, inputGlyphCount, glyphIterator)) {
                    const SubstitutionLookupRecord *substLookupRecordArray =
                        (const SubstitutionLookupRecord *) &lookaheadGlyphArray[lookaheadGlyphCount + 1];

                    applySubstitutionLookups(lookupProcessor, substLookupRecordArray, substCount, glyphIterator, fontInstance, position);

                    return inputGlyphCount + 1;
                }

                glyphIterator->setCurrStreamPosition(position);
            }
        }

        // XXX If we get here, the table is mal-formed...
    }

    return 0;
}

le_uint32 ChainingContextualSubstitutionFormat2Subtable::process(const LookupProcessor *lookupProcessor, GlyphIterator *glyphIterator,
                                                              const LEFontInstance *fontInstance) const
{
    LEGlyphID glyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(glyph);

    if (coverageIndex >= 0) {
        const ClassDefinitionTable *backtrackClassDefinitionTable =
            (const ClassDefinitionTable *) ((char *) this + SWAPW(backtrackClassDefTableOffset));
        const ClassDefinitionTable *inputClassDefinitionTable =
            (const ClassDefinitionTable *) ((char *) this + SWAPW(inputClassDefTableOffset));
        const ClassDefinitionTable *lookaheadClassDefinitionTable =
            (const ClassDefinitionTable *) ((char *) this + SWAPW(lookaheadClassDefTableOffset));
        le_uint16 scSetCount = SWAPW(chainSubClassSetCount);
        le_int32 setClass = inputClassDefinitionTable->getGlyphClass(glyphIterator->getCurrGlyphID());

        if (setClass < scSetCount && chainSubClassSetTableOffsetArray[setClass] != 0) {
            Offset chainSubClassSetTableOffset = SWAPW(chainSubClassSetTableOffsetArray[setClass]);
            const ChainSubClassSetTable *chainSubClassSetTable =
                (const ChainSubClassSetTable *) ((char *) this + chainSubClassSetTableOffset);
            le_uint16 chainSubClassRuleCount = SWAPW(chainSubClassSetTable->chainSubClassRuleCount);
            le_int32 position = glyphIterator->getCurrStreamPosition();
            GlyphIterator tempIterator(*glyphIterator, emptyFeatureList);

            for (le_uint16 scRule = 0; scRule < chainSubClassRuleCount; scRule += 1) {
                Offset chainSubClassRuleTableOffset =
                    SWAPW(chainSubClassSetTable->chainSubClassRuleTableOffsetArray[scRule]);
                const ChainSubClassRuleTable *chainSubClassRuleTable =
                    (const ChainSubClassRuleTable *) ((char *) chainSubClassSetTable + chainSubClassRuleTableOffset);
                le_uint16 backtrackGlyphCount = SWAPW(chainSubClassRuleTable->backtrackGlyphCount);
                le_uint16 inputGlyphCount = SWAPW(chainSubClassRuleTable->backtrackClassArray[backtrackGlyphCount]) - 1;
                const le_uint16 *inputClassArray = &chainSubClassRuleTable->backtrackClassArray[backtrackGlyphCount + 1];
                le_uint16 lookaheadGlyphCount = SWAPW(inputClassArray[inputGlyphCount]);
                const le_uint16 *lookaheadClassArray = &inputClassArray[inputGlyphCount + 1];
                le_uint16 substCount = SWAPW(lookaheadClassArray[lookaheadGlyphCount]);


                tempIterator.setCurrStreamPosition(position);

                if (! tempIterator.prev(backtrackGlyphCount)) {
                    continue;
                }

                tempIterator.prev();
                if (! matchGlyphClasses(chainSubClassRuleTable->backtrackClassArray, backtrackGlyphCount,
                    &tempIterator, backtrackClassDefinitionTable, TRUE)) {
                    continue;
                }

                tempIterator.setCurrStreamPosition(position);
                tempIterator.next(inputGlyphCount);
                if (! matchGlyphClasses(lookaheadClassArray, lookaheadGlyphCount, &tempIterator, lookaheadClassDefinitionTable)) {
                    continue;
                }

                if (matchGlyphClasses(inputClassArray, inputGlyphCount, glyphIterator, inputClassDefinitionTable)) {
                    const SubstitutionLookupRecord *substLookupRecordArray =
                        (const SubstitutionLookupRecord *) &lookaheadClassArray[lookaheadGlyphCount + 1];

                    applySubstitutionLookups(lookupProcessor, substLookupRecordArray, substCount, glyphIterator, fontInstance, position);

                    return inputGlyphCount + 1;
                }

                glyphIterator->setCurrStreamPosition(position);
            }
        }

        // XXX If we get here, the table is mal-formed...
    }

    return 0;
}

le_uint32 ChainingContextualSubstitutionFormat3Subtable::process(const LookupProcessor *lookupProcessor, GlyphIterator *glyphIterator,
                                                              const LEFontInstance *fontInstance) const
{
    le_uint16 backtrkGlyphCount = SWAPW(backtrackGlyphCount);
    le_uint16 inputGlyphCount = (le_uint16) SWAPW(backtrackCoverageTableOffsetArray[backtrkGlyphCount]);
    const Offset *inputCoverageTableOffsetArray = &backtrackCoverageTableOffsetArray[backtrkGlyphCount + 1];
    const le_uint16 lookaheadGlyphCount = (le_uint16) SWAPW(inputCoverageTableOffsetArray[inputGlyphCount]);
    const Offset *lookaheadCoverageTableOffsetArray = &inputCoverageTableOffsetArray[inputGlyphCount + 1];
    le_uint16 substCount = (le_uint16) SWAPW(lookaheadCoverageTableOffsetArray[lookaheadGlyphCount]);
    le_int32 position = glyphIterator->getCurrStreamPosition();
    GlyphIterator tempIterator(*glyphIterator, emptyFeatureList);

    if (! tempIterator.prev(backtrkGlyphCount)) {
        return 0;
    }

    tempIterator.prev();
    if (! ContextualSubstitutionBase::matchGlyphCoverages(backtrackCoverageTableOffsetArray,
        backtrkGlyphCount, &tempIterator, (const char *) this, TRUE)) {
        return 0;
    }

    tempIterator.setCurrStreamPosition(position);
    tempIterator.next(inputGlyphCount - 1);
    if (! ContextualSubstitutionBase::matchGlyphCoverages(lookaheadCoverageTableOffsetArray,
        lookaheadGlyphCount, &tempIterator, (const char *) this)) {
        return 0;
    }

    // Back up the glyph iterator so that we
    // can call next() before the check, which
    // will leave it pointing at the last glyph
    // that matched when we're done.
    glyphIterator->prev();

    if (ContextualSubstitutionBase::matchGlyphCoverages(inputCoverageTableOffsetArray,
        inputGlyphCount, glyphIterator, (const char *) this)) {
        const SubstitutionLookupRecord *substLookupRecordArray =
            (const SubstitutionLookupRecord *) &lookaheadCoverageTableOffsetArray[lookaheadGlyphCount + 1];

        ContextualSubstitutionBase::applySubstitutionLookups(lookupProcessor, substLookupRecordArray, substCount, glyphIterator, fontInstance, position);

        return inputGlyphCount;
    }

    glyphIterator->setCurrStreamPosition(position);

    return 0;
}

U_NAMESPACE_END
