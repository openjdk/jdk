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
#include "LayoutEngine.h"
#include "GXLayoutEngine.h"
#include "LEGlyphStorage.h"

#include "MorphTables.h"

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(GXLayoutEngine)

GXLayoutEngine::GXLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode, const MorphTableHeader *morphTable)
    : LayoutEngine(fontInstance, scriptCode, languageCode, 0), fMorphTable(morphTable)
{
    // nothing else to do?
}

GXLayoutEngine::~GXLayoutEngine()
{
    reset();
}

// apply 'mort' table
le_int32 GXLayoutEngine::computeGlyphs(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft, LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (chars == NULL || offset < 0 || count < 0 || max < 0 || offset >= max || offset + count > max) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return 0;
    }

    mapCharsToGlyphs(chars, offset, count, FALSE, rightToLeft, glyphStorage, success);

    if (LE_FAILURE(success)) {
        return 0;
    }

    fMorphTable->process(glyphStorage);

    return count;
}

// apply positional tables
void GXLayoutEngine::adjustGlyphPositions(const LEUnicode chars[], le_int32 offset, le_int32 count, le_bool /*reverse*/,
                                          LEGlyphStorage &/*glyphStorage*/, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return;
    }

    if (chars == NULL || offset < 0 || count < 0) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return;
    }

    // FIXME: no positional processing yet...
}

U_NAMESPACE_END
