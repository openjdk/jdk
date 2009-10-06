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
#include "LEGlyphFilter.h"
#include "LEFontInstance.h"
#include "OpenTypeTables.h"
#include "Features.h"
#include "Lookups.h"
#include "ScriptAndLanguage.h"
#include "GlyphDefinitionTables.h"
#include "GlyphSubstitutionTables.h"
#include "SingleSubstitutionSubtables.h"
#include "MultipleSubstSubtables.h"
#include "AlternateSubstSubtables.h"
#include "LigatureSubstSubtables.h"
#include "ContextualSubstSubtables.h"
#include "ExtensionSubtables.h"
#include "LookupProcessor.h"
#include "GlyphSubstLookupProc.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

GlyphSubstitutionLookupProcessor::GlyphSubstitutionLookupProcessor(
        const GlyphSubstitutionTableHeader *glyphSubstitutionTableHeader,
        LETag scriptTag, LETag languageTag, const LEGlyphFilter *filter, const FeatureMap *featureMap, le_int32 featureMapCount, le_bool featureOrder)
    : LookupProcessor(
                      (char *) glyphSubstitutionTableHeader,
                      SWAPW(glyphSubstitutionTableHeader->scriptListOffset),
                      SWAPW(glyphSubstitutionTableHeader->featureListOffset),
                      SWAPW(glyphSubstitutionTableHeader->lookupListOffset),
                      scriptTag, languageTag, featureMap, featureMapCount, featureOrder), fFilter(filter)
{
    // anything?
}

GlyphSubstitutionLookupProcessor::GlyphSubstitutionLookupProcessor()
{
}

le_uint32 GlyphSubstitutionLookupProcessor::applySubtable(const LookupSubtable *lookupSubtable, le_uint16 lookupType,
                                                       GlyphIterator *glyphIterator, const LEFontInstance *fontInstance) const
{
    le_uint32 delta = 0;

    switch(lookupType)
    {
    case 0:
        break;

    case gsstSingle:
    {
        const SingleSubstitutionSubtable *subtable = (const SingleSubstitutionSubtable *) lookupSubtable;

        delta = subtable->process(glyphIterator, fFilter);
        break;
    }

    case gsstMultiple:
    {
        const MultipleSubstitutionSubtable *subtable = (const MultipleSubstitutionSubtable *) lookupSubtable;

        delta = subtable->process(glyphIterator, fFilter);
        break;
    }

    case gsstAlternate:
    {
        const AlternateSubstitutionSubtable *subtable = (const AlternateSubstitutionSubtable *) lookupSubtable;

        delta = subtable->process(glyphIterator, fFilter);
        break;
    }

    case gsstLigature:
    {
        const LigatureSubstitutionSubtable *subtable = (const LigatureSubstitutionSubtable *) lookupSubtable;

        delta = subtable->process(glyphIterator, fFilter);
        break;
    }

    case gsstContext:
    {
        const ContextualSubstitutionSubtable *subtable = (const ContextualSubstitutionSubtable *) lookupSubtable;

        delta = subtable->process(this, glyphIterator, fontInstance);
        break;
    }

    case gsstChainingContext:
    {
        const ChainingContextualSubstitutionSubtable *subtable = (const ChainingContextualSubstitutionSubtable *) lookupSubtable;

        delta = subtable->process(this, glyphIterator, fontInstance);
        break;
    }

    case gsstExtension:
    {
        const ExtensionSubtable *subtable = (const ExtensionSubtable *) lookupSubtable;

        delta = subtable->process(this, lookupType, glyphIterator, fontInstance);
        break;
    }

    default:
        break;
    }

    return delta;
}

GlyphSubstitutionLookupProcessor::~GlyphSubstitutionLookupProcessor()
{
}

U_NAMESPACE_END
