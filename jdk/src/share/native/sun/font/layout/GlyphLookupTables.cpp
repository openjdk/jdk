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
 * (C) Copyright IBM Corp. 1998-2004 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "OpenTypeTables.h"
#include "ScriptAndLanguage.h"
#include "GlyphLookupTables.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

le_bool GlyphLookupTableHeader::coversScript(LETag scriptTag) const
{
    const ScriptListTable *scriptListTable = (const ScriptListTable *) ((char *)this + SWAPW(scriptListOffset));

    return scriptListOffset != 0 && scriptListTable->findScript(scriptTag) != NULL;
}

le_bool GlyphLookupTableHeader::coversScriptAndLanguage(LETag scriptTag, LETag languageTag, le_bool exactMatch) const
{
    const ScriptListTable *scriptListTable = (const ScriptListTable *) ((char *)this + SWAPW(scriptListOffset));
    const LangSysTable    *langSysTable    = scriptListTable->findLanguage(scriptTag, languageTag, exactMatch);

    // FIXME: could check featureListOffset, lookupListOffset, and lookup count...
    // Note: don't have to SWAPW langSysTable->featureCount to check for non-zero.
    return langSysTable != NULL && langSysTable->featureCount != 0;
}

U_NAMESPACE_END
