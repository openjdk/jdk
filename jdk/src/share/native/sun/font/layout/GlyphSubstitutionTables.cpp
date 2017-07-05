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
#include "LEGlyphFilter.h"
#include "OpenTypeTables.h"
#include "Lookups.h"
#include "GlyphDefinitionTables.h"
#include "GlyphSubstitutionTables.h"
#include "GlyphSubstLookupProc.h"
#include "ScriptAndLanguage.h"
#include "LEGlyphStorage.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

le_int32 GlyphSubstitutionTableHeader::process(const LEReferenceTo<GlyphSubstitutionTableHeader> &base,
                                               LEGlyphStorage &glyphStorage,
                                               le_bool rightToLeft,
                                               LETag scriptTag,
                                               LETag languageTag,
                                               const LEReferenceTo<GlyphDefinitionTableHeader> &glyphDefinitionTableHeader,
                                               const LEGlyphFilter *filter,
                                               const FeatureMap *featureMap,
                                               le_int32 featureMapCount,
                                               le_bool featureOrder,
                                               LEErrorCode &success) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    GlyphSubstitutionLookupProcessor processor(base, scriptTag, languageTag, filter, featureMap, featureMapCount, featureOrder, success);
    return processor.process(glyphStorage, NULL, rightToLeft, glyphDefinitionTableHeader, NULL, success);
}

U_NAMESPACE_END
