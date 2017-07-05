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
 * (C) Copyright IBM Corp. 1998 - 2005 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "LEFontInstance.h"
#include "OpenTypeTables.h"
#include "ICUFeatures.h"
#include "Lookups.h"
#include "ScriptAndLanguage.h"
#include "GlyphDefinitionTables.h"
#include "GlyphPositioningTables.h"
#include "SinglePositioningSubtables.h"
#include "PairPositioningSubtables.h"
#include "CursiveAttachmentSubtables.h"
#include "MarkToBasePosnSubtables.h"
#include "MarkToLigaturePosnSubtables.h"
#include "MarkToMarkPosnSubtables.h"
//#include "ContextualPositioningSubtables.h"
#include "ContextualSubstSubtables.h"
#include "ExtensionSubtables.h"
#include "LookupProcessor.h"
#include "GlyphPosnLookupProc.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

// Aside from the names, the contextual positioning subtables are
// the same as the contextual substitution subtables.
typedef ContextualSubstitutionSubtable ContextualPositioningSubtable;
typedef ChainingContextualSubstitutionSubtable ChainingContextualPositioningSubtable;

GlyphPositioningLookupProcessor::GlyphPositioningLookupProcessor(
        const LEReferenceTo<GlyphPositioningTableHeader> &glyphPositioningTableHeader,
        LETag scriptTag,
        LETag languageTag,
        const FeatureMap *featureMap,
        le_int32 featureMapCount,
        le_bool featureOrder,
        LEErrorCode& success)
    : LookupProcessor(
                      glyphPositioningTableHeader,
                      SWAPW(glyphPositioningTableHeader->scriptListOffset),
                      SWAPW(glyphPositioningTableHeader->featureListOffset),
                      SWAPW(glyphPositioningTableHeader->lookupListOffset),
                      scriptTag,
                      languageTag,
                      featureMap,
                      featureMapCount,
                      featureOrder,
                      success
                      )
{
    // anything?
}

GlyphPositioningLookupProcessor::GlyphPositioningLookupProcessor()
{
}

le_uint32 GlyphPositioningLookupProcessor::applySubtable(const LEReferenceTo<LookupSubtable> &lookupSubtable, le_uint16 lookupType,
                                                       GlyphIterator *glyphIterator,
                                                       const LEFontInstance *fontInstance,
                                                       LEErrorCode& success) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    le_uint32 delta = 0;

    //_LETRACE("attempting lookupType #%d", lookupType);

    switch(lookupType)
    {
    case 0:
        break;

    case gpstSingle:
    {
      LEReferenceTo<SinglePositioningSubtable> subtable(lookupSubtable, success);

        delta = subtable->process(subtable, glyphIterator, fontInstance, success);
        break;
    }

    case gpstPair:
    {
        LEReferenceTo<PairPositioningSubtable> subtable(lookupSubtable, success);

        delta = subtable->process(subtable, glyphIterator, fontInstance, success);
        break;
    }

    case gpstCursive:
    {
        LEReferenceTo<CursiveAttachmentSubtable> subtable(lookupSubtable, success);

        delta = subtable->process(subtable, glyphIterator, fontInstance, success);
        break;
    }

    case gpstMarkToBase:
    {
        LEReferenceTo<MarkToBasePositioningSubtable> subtable(lookupSubtable, success);

        delta = subtable->process(subtable, glyphIterator, fontInstance, success);
        break;
    }

     case gpstMarkToLigature:
    {
        LEReferenceTo<MarkToLigaturePositioningSubtable> subtable(lookupSubtable, success);

        delta = subtable->process(subtable, glyphIterator, fontInstance, success);
        break;
    }

    case gpstMarkToMark:
    {
        LEReferenceTo<MarkToMarkPositioningSubtable> subtable(lookupSubtable, success);

        delta = subtable->process(subtable, glyphIterator, fontInstance, success);
        break;
    }

   case gpstContext:
    {
        LEReferenceTo<ContextualPositioningSubtable> subtable(lookupSubtable, success);

        delta = subtable->process(subtable, this , glyphIterator, fontInstance, success);
        break;
    }

    case gpstChainedContext:
    {
        const LEReferenceTo<ChainingContextualPositioningSubtable> subtable(lookupSubtable, success);

        delta = subtable->process(subtable, this, glyphIterator, fontInstance, success);
        break;
    }

    case gpstExtension:
    {
        const LEReferenceTo<ExtensionSubtable> subtable(lookupSubtable, success);

        delta = subtable->process(subtable, this, lookupType, glyphIterator, fontInstance, success);
        break;
    }

    default:
        break;
    }

#if LE_TRACE
    if(delta != 0) {
      _LETRACE("GlyphPositioningLookupProcessor applied #%d -> delta %d @ %d", lookupType, delta, glyphIterator->getCurrStreamPosition());
    }
#endif

    return delta;
}

GlyphPositioningLookupProcessor::~GlyphPositioningLookupProcessor()
{
}

U_NAMESPACE_END
