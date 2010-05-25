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

#ifndef __GLYPHDEFINITIONTABLES_H
#define __GLYPHDEFINITIONTABLES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "OpenTypeTables.h"
#include "ClassDefinitionTables.h"

U_NAMESPACE_BEGIN

typedef ClassDefinitionTable GlyphClassDefinitionTable;

enum GlyphClassDefinitions
{
    gcdNoGlyphClass     = 0,
    gcdSimpleGlyph      = 1,
    gcdLigatureGlyph    = 2,
    gcdMarkGlyph        = 3,
    gcdComponentGlyph   = 4
};

struct AttachmentListTable
{
    Offset  coverageTableOffset;
    le_uint16  glyphCount;
    Offset  attachPointTableOffsetArray[ANY_NUMBER];
};

struct AttachPointTable
{
    le_uint16  pointCount;
    le_uint16  pointIndexArray[ANY_NUMBER];
};

struct LigatureCaretListTable
{
    Offset  coverageTableOffset;
    le_uint16  ligGlyphCount;
    Offset  ligGlyphTableOffsetArray[ANY_NUMBER];
};

struct LigatureGlyphTable
{
    le_uint16  caretCount;
    Offset  caretValueTableOffsetArray[ANY_NUMBER];
};

struct CaretValueTable
{
    le_uint16  caretValueFormat;
};

struct CaretValueFormat1Table : CaretValueTable
{
    le_int16   coordinate;
};

struct CaretValueFormat2Table : CaretValueTable
{
    le_uint16  caretValuePoint;
};

struct CaretValueFormat3Table : CaretValueTable
{
    le_int16   coordinate;
    Offset  deviceTableOffset;
};

typedef ClassDefinitionTable MarkAttachClassDefinitionTable;

struct GlyphDefinitionTableHeader
{
    fixed32 version;
    Offset  glyphClassDefOffset;
    Offset  attachListOffset;
    Offset  ligCaretListOffset;
    Offset  MarkAttachClassDefOffset;

    const GlyphClassDefinitionTable *getGlyphClassDefinitionTable() const;
    const AttachmentListTable *getAttachmentListTable()const ;
    const LigatureCaretListTable *getLigatureCaretListTable() const;
    const MarkAttachClassDefinitionTable *getMarkAttachClassDefinitionTable() const;
};

U_NAMESPACE_END
#endif
