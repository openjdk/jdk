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
 *
 * (C) Copyright IBM Corp. 1998-2010 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "OpenTypeTables.h"
#include "OpenTypeUtilities.h"
#include "ScriptAndLanguage.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

LEReferenceTo<LangSysTable> ScriptTable::findLanguage(const LETableReference& base, LETag languageTag, LEErrorCode &success, le_bool exactMatch) const
{
    le_uint16 count = SWAPW(langSysCount);
    Offset langSysTableOffset = exactMatch? 0 : SWAPW(defaultLangSysTableOffset);

    if (count > 0) {
      LEReferenceToArrayOf<TagAndOffsetRecord> langSysRecords(base, success, langSysRecordArray, count);
      Offset foundOffset =
        OpenTypeUtilities::getTagOffset(languageTag, langSysRecords, success);

      if (foundOffset != 0 && LE_SUCCESS(success)) {
        langSysTableOffset = foundOffset;
      }
    }

    if (langSysTableOffset != 0) {
      return LEReferenceTo<LangSysTable>(base, success, langSysTableOffset);
    }

    return LEReferenceTo<LangSysTable>();
}

LEReferenceTo<ScriptTable> ScriptListTable::findScript(const LETableReference &base, LETag scriptTag, LEErrorCode &success) const
{
    if (LE_FAILURE(success) ) {
      return LEReferenceTo<ScriptTable>(); // get out
    }
    /*
     * There are some fonts that have a large, bogus value for scriptCount. To try
     * and protect against this, we use the offset in the first scriptRecord,
     * which we know has to be past the end of the scriptRecordArray, to compute
     * a value which is greater than or equal to the actual script count.
     *
     * Note: normally, the first offset will point to just after the scriptRecordArray,
     * but there's no guarantee of this, only that it's *after* the scriptRecordArray.
     * Because of this, a binary serach isn't safe, because the new count may include
     * data that's not actually in the scriptRecordArray and hence the array will appear
     * to be unsorted.
     */
    le_uint16 count = SWAPW(scriptCount);

    if (count == 0) {
      return LEReferenceTo<ScriptTable>(); // no items, no search
    }

    // attempt to construct a ref with at least one element
    LEReferenceToArrayOf<ScriptRecord> oneElementTable(base, success, &scriptRecordArray[0], 1);

    if( LE_FAILURE(success) ) {
      return LEReferenceTo<ScriptTable>(); // couldn't even read the first record - bad font.
    }

    le_uint16 limit = ((SWAPW(scriptRecordArray[0].offset) - sizeof(ScriptListTable)) / sizeof(scriptRecordArray)) + ANY_NUMBER;
    Offset scriptTableOffset = 0;


    if (count > limit) {
        // the scriptCount value is bogus; do a linear search
        // because limit may still be too large.
        LEReferenceToArrayOf<ScriptRecord> scriptRecordArrayRef(base, success, &scriptRecordArray[0], limit);
        for(le_int32 s = 0; (s < limit)&&LE_SUCCESS(success); s += 1) {
          if (SWAPT(scriptRecordArrayRef(s,success).tag) == scriptTag) {
            scriptTableOffset = SWAPW(scriptRecordArrayRef(s,success).offset);
            break;
          }
        }
    } else {
      LEReferenceToArrayOf<ScriptRecord> scriptRecordArrayRef(base, success, &scriptRecordArray[0], count);

      scriptTableOffset = OpenTypeUtilities::getTagOffset(scriptTag, scriptRecordArrayRef, success);
    }

    if (scriptTableOffset != 0) {
      return LEReferenceTo<ScriptTable>(base, success, scriptTableOffset);
    }

  return LEReferenceTo<ScriptTable>();
}

LEReferenceTo<LangSysTable>  ScriptListTable::findLanguage(const LETableReference &base, LETag scriptTag, LETag languageTag, LEErrorCode &success, le_bool exactMatch) const
{
  const LEReferenceTo<ScriptTable> scriptTable = findScript(base, scriptTag, success);

  if (scriptTable.isEmpty()) {
    return LEReferenceTo<LangSysTable>();
  }

  return scriptTable->findLanguage(scriptTable, languageTag, success, exactMatch).reparent(base);
}

U_NAMESPACE_END
