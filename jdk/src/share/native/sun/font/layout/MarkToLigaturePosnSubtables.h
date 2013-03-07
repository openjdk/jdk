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

#ifndef __MARKTOLIGATUREPOSITIONINGSUBTABLES_H
#define __MARKTOLIGATUREPOSITIONINGSUBTABLES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "LEFontInstance.h"
#include "OpenTypeTables.h"
#include "GlyphPositioningTables.h"
#include "AttachmentPosnSubtables.h"
#include "GlyphIterator.h"

U_NAMESPACE_BEGIN

struct MarkToLigaturePositioningSubtable : AttachmentPositioningSubtable
{
  le_int32   process(const LETableReference &base, GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode &success) const;
    LEGlyphID  findLigatureGlyph(GlyphIterator *glyphIterator) const;
};

struct ComponentRecord
{
    Offset ligatureAnchorTableOffsetArray[ANY_NUMBER];
};
LE_VAR_ARRAY(ComponentRecord, ligatureAnchorTableOffsetArray)

struct LigatureAttachTable
{
    le_uint16 componentCount;
    ComponentRecord componentRecordArray[ANY_NUMBER];
};
LE_VAR_ARRAY(LigatureAttachTable, componentRecordArray)

struct LigatureArray
{
    le_uint16 ligatureCount;
    Offset ligatureAttachTableOffsetArray[ANY_NUMBER];
};
LE_VAR_ARRAY(LigatureArray, ligatureAttachTableOffsetArray)

U_NAMESPACE_END
#endif

