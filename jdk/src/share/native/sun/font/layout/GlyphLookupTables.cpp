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
#include "OpenTypeTables.h"
#include "ScriptAndLanguage.h"
#include "GlyphLookupTables.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

le_bool GlyphLookupTableHeader::coversScript(const LETableReference &base, LETag scriptTag, LEErrorCode &success) const
{
  LEReferenceTo<ScriptListTable> scriptListTable(base, success, SWAPW(scriptListOffset));

  return (scriptListOffset != 0) && scriptListTable->findScript(scriptListTable, scriptTag, success) .isValid();
}

le_bool GlyphLookupTableHeader::coversScriptAndLanguage(const LETableReference &base, LETag scriptTag, LETag languageTag, LEErrorCode &success, le_bool exactMatch) const
{
  LEReferenceTo<ScriptListTable> scriptListTable(base, success, SWAPW(scriptListOffset));
  LEReferenceTo<LangSysTable> langSysTable = scriptListTable->findLanguage(scriptListTable,
                                    scriptTag, languageTag, success, exactMatch);

    // FIXME: could check featureListOffset, lookupListOffset, and lookup count...
    // Note: don't have to SWAPW langSysTable->featureCount to check for non-zero.
  return LE_SUCCESS(success)&&langSysTable.isValid() && langSysTable->featureCount != 0;
}

U_NAMESPACE_END
