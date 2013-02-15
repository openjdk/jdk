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
 * (C) Copyright IBM Corp.  and others 1998-2013 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "MorphTables.h"
#include "SubtableProcessor2.h"
#include "NonContextualGlyphSubst.h"
#include "NonContextualGlyphSubstProc2.h"
#include "SingleTableProcessor2.h"
#include "LEGlyphStorage.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(SingleTableProcessor2)

SingleTableProcessor2::SingleTableProcessor2()
{
}

SingleTableProcessor2::SingleTableProcessor2(const MorphSubtableHeader2 *moprhSubtableHeader)
  : NonContextualGlyphSubstitutionProcessor2(moprhSubtableHeader)
{
    const NonContextualGlyphSubstitutionHeader2 *header = (const NonContextualGlyphSubstitutionHeader2 *) moprhSubtableHeader;

    singleTableLookupTable = (const SingleTableLookupTable *) &header->table;
}

SingleTableProcessor2::~SingleTableProcessor2()
{
}

void SingleTableProcessor2::process(LEGlyphStorage &glyphStorage)
{
    const LookupSingle *entries = singleTableLookupTable->entries;
    le_int32 glyph;
    le_int32 glyphCount = glyphStorage.getGlyphCount();

    for (glyph = 0; glyph < glyphCount; glyph += 1) {
        const LookupSingle *lookupSingle = singleTableLookupTable->lookupSingle(entries, glyphStorage[glyph]);

        if (lookupSingle != NULL) {
            glyphStorage[glyph] = SWAPW(lookupSingle->value);
        }
    }
}

U_NAMESPACE_END
