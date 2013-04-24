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
 * (C) Copyright IBM Corp. 1998 - 2004 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "OpenTypeTables.h"
#include "GlyphDefinitionTables.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

const LEReferenceTo<GlyphClassDefinitionTable>
GlyphDefinitionTableHeader::getGlyphClassDefinitionTable(const LEReferenceTo<GlyphDefinitionTableHeader>& base,
                                                         LEErrorCode &success) const
{
  if(LE_FAILURE(success)) return LEReferenceTo<GlyphClassDefinitionTable>();
  return LEReferenceTo<GlyphClassDefinitionTable>(base, success, SWAPW(glyphClassDefOffset));
}

const LEReferenceTo<AttachmentListTable>
GlyphDefinitionTableHeader::getAttachmentListTable(const LEReferenceTo<GlyphDefinitionTableHeader>& base,
                                                         LEErrorCode &success) const
{
    if(LE_FAILURE(success)) return LEReferenceTo<AttachmentListTable>();
    return LEReferenceTo<AttachmentListTable>(base, success, SWAPW(attachListOffset));
}

const LEReferenceTo<LigatureCaretListTable>
GlyphDefinitionTableHeader::getLigatureCaretListTable(const LEReferenceTo<GlyphDefinitionTableHeader>& base,
                                                         LEErrorCode &success) const
{
    if(LE_FAILURE(success)) return LEReferenceTo<LigatureCaretListTable>();
    return LEReferenceTo<LigatureCaretListTable>(base, success, SWAPW(ligCaretListOffset));
}

const LEReferenceTo<MarkAttachClassDefinitionTable>
GlyphDefinitionTableHeader::getMarkAttachClassDefinitionTable(const LEReferenceTo<GlyphDefinitionTableHeader>& base,
                                                         LEErrorCode &success) const
{
    if(LE_FAILURE(success)) return LEReferenceTo<MarkAttachClassDefinitionTable>();
    return LEReferenceTo<MarkAttachClassDefinitionTable>(base, success, SWAPW(MarkAttachClassDefOffset));
}

U_NAMESPACE_END
