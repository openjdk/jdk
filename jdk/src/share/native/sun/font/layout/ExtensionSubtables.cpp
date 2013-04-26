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
 * (C) Copyright IBM Corp. 2002 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "OpenTypeTables.h"
#include "GlyphSubstitutionTables.h"
#include "LookupProcessor.h"
#include "ExtensionSubtables.h"
#include "GlyphIterator.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

// read a 32-bit value that might only be 16-bit-aligned in memory
#define READ_LONG(code) (le_uint32)((SWAPW(*(le_uint16*)&code) << 16) + SWAPW(*(((le_uint16*)&code) + 1)))

// FIXME: should look at the format too... maybe have a sub-class for it?
le_uint32 ExtensionSubtable::process(const LEReferenceTo<ExtensionSubtable> &thisRef,
                                     const LookupProcessor *lookupProcessor, le_uint16 lookupType,
                                      GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode& success) const
{

    if (LE_FAILURE(success)) {
        return 0;
    }

    le_uint16 elt = SWAPW(extensionLookupType);

    if (elt != lookupType) {
        le_uint32 extOffset = READ_LONG(extensionOffset);
        LEReferenceTo<LookupSubtable> subtable(thisRef, success,  extOffset);

        if(LE_SUCCESS(success)) {
          return lookupProcessor->applySubtable(subtable, elt, glyphIterator, fontInstance, success);
        }
    }

    return 0;
}

U_NAMESPACE_END
