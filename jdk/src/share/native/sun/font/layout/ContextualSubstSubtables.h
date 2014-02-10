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
 * (C) Copyright IBM Corp. 1998-2004 - All Rights Reserved
 *
 */

#ifndef __CONTEXTUALSUBSTITUTIONSUBTABLES_H
#define __CONTEXTUALSUBSTITUTIONSUBTABLES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "LEFontInstance.h"
#include "OpenTypeTables.h"
#include "GlyphSubstitutionTables.h"
#include "GlyphIterator.h"
#include "LookupProcessor.h"
#include "LETableReference.h"

U_NAMESPACE_BEGIN

struct SubstitutionLookupRecord
{
    le_uint16  sequenceIndex;
    le_uint16  lookupListIndex;
};

struct ContextualSubstitutionBase : GlyphSubstitutionSubtable
{
    static le_bool matchGlyphIDs(
                                 const LEReferenceToArrayOf<TTGlyphID> &glyphArray, le_uint16 glyphCount, GlyphIterator *glyphIterator,
        le_bool backtrack = FALSE);

    static le_bool matchGlyphClasses(
                                     const LEReferenceToArrayOf<le_uint16> &classArray, le_uint16 glyphCount, GlyphIterator *glyphIterator,
        const LEReferenceTo<ClassDefinitionTable> &classDefinitionTable, LEErrorCode &success, le_bool backtrack = FALSE);

    static le_bool matchGlyphCoverages(
                                       const LEReferenceToArrayOf<Offset> &coverageTableOffsetArray, le_uint16 glyphCount,
        GlyphIterator *glyphIterator, const LETableReference& offsetBase, LEErrorCode &success, le_bool backtrack = FALSE);

    /**
     * little shim to wrap the Offset array in range checking
     * @private
     */
    static le_bool matchGlyphCoverages(
                                       const Offset *coverageTableOffsetArray, le_uint16 glyphCount,
                                       GlyphIterator *glyphIterator, const LETableReference& offsetBase, LEErrorCode &success, le_bool backtrack = FALSE) {
      LEReferenceToArrayOf<Offset> ref(offsetBase, success, coverageTableOffsetArray, glyphCount);
      if( LE_FAILURE(success) ) { return FALSE; }
      return matchGlyphCoverages(ref, glyphCount, glyphIterator, offsetBase, success, backtrack);
    }

    static void applySubstitutionLookups(
        const LookupProcessor *lookupProcessor,
        const LEReferenceToArrayOf<SubstitutionLookupRecord>& substLookupRecordArray,
        le_uint16 substCount,
        GlyphIterator *glyphIterator,
        const LEFontInstance *fontInstance,
        le_int32 position,
        LEErrorCode& success);
};

struct ContextualSubstitutionSubtable : ContextualSubstitutionBase
{
    le_uint32  process(const LETableReference &base, const LookupProcessor *lookupProcessor,
                       GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode& success) const;
};

struct ContextualSubstitutionFormat1Subtable : ContextualSubstitutionSubtable
{
    le_uint16  subRuleSetCount;
    Offset  subRuleSetTableOffsetArray[ANY_NUMBER];

    le_uint32  process(const LETableReference &base, const LookupProcessor *lookupProcessor, GlyphIterator *glyphIterator,
                       const LEFontInstance *fontInstance, LEErrorCode& success) const;
};
LE_VAR_ARRAY(ContextualSubstitutionFormat1Subtable, subRuleSetTableOffsetArray)


struct SubRuleSetTable
{
    le_uint16  subRuleCount;
    Offset  subRuleTableOffsetArray[ANY_NUMBER];

};
LE_VAR_ARRAY(SubRuleSetTable, subRuleTableOffsetArray)

// NOTE: Multiple variable size arrays!!
struct SubRuleTable
{
    le_uint16  glyphCount;
    le_uint16  substCount;
    TTGlyphID inputGlyphArray[ANY_NUMBER];
  //SubstitutionLookupRecord substLookupRecordArray[ANY_NUMBER];
};
LE_VAR_ARRAY(SubRuleTable, inputGlyphArray)

struct ContextualSubstitutionFormat2Subtable : ContextualSubstitutionSubtable
{
    Offset  classDefTableOffset;
    le_uint16  subClassSetCount;
    Offset  subClassSetTableOffsetArray[ANY_NUMBER];

    le_uint32  process(const LETableReference &base, const LookupProcessor *lookupProcessor, GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode& success) const;
};
LE_VAR_ARRAY(ContextualSubstitutionFormat2Subtable, subClassSetTableOffsetArray)


struct SubClassSetTable
{
    le_uint16  subClassRuleCount;
    Offset  subClassRuleTableOffsetArray[ANY_NUMBER];
};
LE_VAR_ARRAY(SubClassSetTable, subClassRuleTableOffsetArray)


// NOTE: Multiple variable size arrays!!
struct SubClassRuleTable
{
    le_uint16  glyphCount;
    le_uint16  substCount;
    le_uint16  classArray[ANY_NUMBER];
  //SubstitutionLookupRecord substLookupRecordArray[ANY_NUMBER];
};
LE_VAR_ARRAY(SubClassRuleTable, classArray)


// NOTE: This isn't a subclass of GlyphSubstitutionSubtable 'cause
// it has an array of coverage tables instead of a single coverage table...
//
// NOTE: Multiple variable size arrays!!
struct ContextualSubstitutionFormat3Subtable
{
    le_uint16  substFormat;
    le_uint16  glyphCount;
    le_uint16  substCount;
    Offset  coverageTableOffsetArray[ANY_NUMBER];
  //SubstitutionLookupRecord substLookupRecord[ANY_NUMBER];

    le_uint32  process(const LETableReference &base, const LookupProcessor *lookupProcessor, GlyphIterator *glyphIterator,
                       const LEFontInstance *fontInstance, LEErrorCode& success) const;
};
LE_VAR_ARRAY(ContextualSubstitutionFormat3Subtable, coverageTableOffsetArray)

struct ChainingContextualSubstitutionSubtable : ContextualSubstitutionBase
{
    le_uint32  process(const LEReferenceTo<ChainingContextualSubstitutionSubtable> &base, const LookupProcessor *lookupProcessor, GlyphIterator *glyphIterator,
                       const LEFontInstance *fontInstance, LEErrorCode& success) const;
};

struct ChainingContextualSubstitutionFormat1Subtable : ChainingContextualSubstitutionSubtable
{
    le_uint16  chainSubRuleSetCount;
    Offset  chainSubRuleSetTableOffsetArray[ANY_NUMBER];

    le_uint32  process(const LETableReference &base, const LookupProcessor *lookupProcessor, GlyphIterator *glyphIterator,
                       const LEFontInstance *fontInstance, LEErrorCode& success) const;
};
LE_VAR_ARRAY(ChainingContextualSubstitutionFormat1Subtable, chainSubRuleSetTableOffsetArray)


struct ChainSubRuleSetTable
{
    le_uint16  chainSubRuleCount;
    Offset  chainSubRuleTableOffsetArray[ANY_NUMBER];

};
LE_VAR_ARRAY(ChainSubRuleSetTable, chainSubRuleTableOffsetArray)

// NOTE: Multiple variable size arrays!!
struct ChainSubRuleTable
{
    le_uint16  backtrackGlyphCount;
    TTGlyphID backtrackGlyphArray[ANY_NUMBER];
  //le_uint16  inputGlyphCount;
  //TTGlyphID inputGlyphArray[ANY_NUMBER];
  //le_uint16  lookaheadGlyphCount;
  //TTGlyphID lookaheadGlyphArray[ANY_NUMBER];
  //le_uint16  substCount;
  //SubstitutionLookupRecord substLookupRecordArray[ANY_NUMBER];
};
LE_VAR_ARRAY(ChainSubRuleTable, backtrackGlyphArray)

struct ChainingContextualSubstitutionFormat2Subtable : ChainingContextualSubstitutionSubtable
{
    Offset  backtrackClassDefTableOffset;
    Offset  inputClassDefTableOffset;
    Offset  lookaheadClassDefTableOffset;
    le_uint16  chainSubClassSetCount;
    Offset  chainSubClassSetTableOffsetArray[ANY_NUMBER];

    le_uint32  process(const LETableReference &base, const LookupProcessor *lookupProcessor, GlyphIterator *glyphIterator,
                       const LEFontInstance *fontInstance, LEErrorCode& success) const;
};
LE_VAR_ARRAY(ChainingContextualSubstitutionFormat2Subtable, chainSubClassSetTableOffsetArray)

struct ChainSubClassSetTable
{
    le_uint16  chainSubClassRuleCount;
    Offset  chainSubClassRuleTableOffsetArray[ANY_NUMBER];
};
LE_VAR_ARRAY(ChainSubClassSetTable, chainSubClassRuleTableOffsetArray)


// NOTE: Multiple variable size arrays!!
struct ChainSubClassRuleTable
{
    le_uint16  backtrackGlyphCount;
    le_uint16  backtrackClassArray[ANY_NUMBER];
  //le_uint16  inputGlyphCount;
  //le_uint16  inputClassArray[ANY_NUMBER];
  //le_uint16  lookaheadGlyphCount;
  //le_uint16  lookaheadClassArray[ANY_NUMBER];
  //le_uint16  substCount;
  //SubstitutionLookupRecord substLookupRecordArray[ANY_NUMBER];
};
LE_VAR_ARRAY(ChainSubClassRuleTable, backtrackClassArray)

// NOTE: This isn't a subclass of GlyphSubstitutionSubtable 'cause
// it has arrays of coverage tables instead of a single coverage table...
//
// NOTE: Multiple variable size arrays!!
struct ChainingContextualSubstitutionFormat3Subtable
{
    le_uint16  substFormat;
    le_uint16  backtrackGlyphCount;
    Offset  backtrackCoverageTableOffsetArray[ANY_NUMBER];
  //le_uint16  inputGlyphCount;
  //Offset  inputCoverageTableOffsetArray[ANY_NUMBER];
  //le_uint16  lookaheadGlyphCount;
  //le_uint16  lookaheadCoverageTableOffsetArray[ANY_NUMBER];
  //le_uint16  substCount;
  //SubstitutionLookupRecord substLookupRecord[ANY_NUMBER];

    le_uint32  process(const LETableReference &base, const LookupProcessor *lookupProcessor,
                       GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode& success) const;
};
LE_VAR_ARRAY(ChainingContextualSubstitutionFormat3Subtable, backtrackCoverageTableOffsetArray)


U_NAMESPACE_END
#endif
