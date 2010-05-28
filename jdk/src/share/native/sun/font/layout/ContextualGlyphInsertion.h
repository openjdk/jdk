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

#ifndef __CONTEXTUALGLYPHINSERTION_H
#define __CONTEXTUALGLYPHINSERTION_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "LayoutTables.h"
#include "StateTables.h"
#include "MorphTables.h"
#include "MorphStateTables.h"

U_NAMESPACE_BEGIN

struct ContextualGlyphInsertionHeader : MorphStateTableHeader
{
};

enum ContextualGlyphInsertionFlags
{
    cgiSetMark                  = 0x8000,
    cgiDontAdvance              = 0x4000,
    cgiCurrentIsKashidaLike     = 0x2000,
    cgiMarkedIsKashidaLike      = 0x1000,
    cgiCurrentInsertBefore      = 0x0800,
    cgiMarkInsertBefore         = 0x0400,
    cgiCurrentInsertCountMask   = 0x03E0,
    cgiMarkedInsertCountMask    = 0x001F
};

struct LigatureSubstitutionStateEntry : StateEntry
{
    ByteOffset currentInsertionListOffset;
    ByteOffset markedInsertionListOffset;
};

U_NAMESPACE_END
#endif
