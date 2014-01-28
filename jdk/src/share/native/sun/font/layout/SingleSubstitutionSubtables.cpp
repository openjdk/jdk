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

#include "LETypes.h"
#include "LEGlyphFilter.h"
#include "OpenTypeTables.h"
#include "GlyphSubstitutionTables.h"
#include "SingleSubstitutionSubtables.h"
#include "GlyphIterator.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

le_uint32 SingleSubstitutionSubtable::process(const LEReferenceTo<SingleSubstitutionSubtable> &base, GlyphIterator *glyphIterator, LEErrorCode &success, const LEGlyphFilter *filter) const
{
    switch(SWAPW(subtableFormat))
    {
    case 0:
        return 0;

    case 1:
    {
      const LEReferenceTo<SingleSubstitutionFormat1Subtable> subtable(base, success, (const SingleSubstitutionFormat1Subtable *) this);

      return subtable->process(subtable, glyphIterator, success, filter);
    }

    case 2:
    {
      const LEReferenceTo<SingleSubstitutionFormat2Subtable> subtable(base, success, (const SingleSubstitutionFormat2Subtable *) this);

      return subtable->process(subtable, glyphIterator, success, filter);
    }

    default:
        return 0;
    }
}

le_uint32 SingleSubstitutionFormat1Subtable::process(const LEReferenceTo<SingleSubstitutionFormat1Subtable> &base, GlyphIterator *glyphIterator, LEErrorCode &success, const LEGlyphFilter *filter) const
{
    LEGlyphID glyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(base, glyph, success);
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (coverageIndex >= 0) {
        TTGlyphID substitute = ((TTGlyphID) LE_GET_GLYPH(glyph)) + SWAPW(deltaGlyphID);

        if (filter == NULL || filter->accept(LE_SET_GLYPH(glyph, substitute), success)) {
            glyphIterator->setCurrGlyphID(substitute);
        }

        return 1;
    }

    return 0;
}

le_uint32 SingleSubstitutionFormat2Subtable::process(const LEReferenceTo<SingleSubstitutionFormat2Subtable> &base, GlyphIterator *glyphIterator, LEErrorCode &success, const LEGlyphFilter *filter) const
{
    LEGlyphID glyph = glyphIterator->getCurrGlyphID();
    le_int32 coverageIndex = getGlyphCoverage(base, glyph, success);
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (coverageIndex >= 0) {
        TTGlyphID substitute = SWAPW(substituteArray[coverageIndex]);

        if (filter == NULL || filter->accept(LE_SET_GLYPH(glyph, substitute), success)) {
            glyphIterator->setCurrGlyphID(substitute);
        }

        return 1;
    }

    return 0;
}

U_NAMESPACE_END
