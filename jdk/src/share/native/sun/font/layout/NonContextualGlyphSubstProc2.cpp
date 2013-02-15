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
 * (C) Copyright IBM Corp.  and others 1998-2013 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "MorphTables.h"
#include "SubtableProcessor2.h"
#include "NonContextualGlyphSubst.h"
#include "NonContextualGlyphSubstProc2.h"
#include "SimpleArrayProcessor2.h"
#include "SegmentSingleProcessor2.h"
#include "SegmentArrayProcessor2.h"
#include "SingleTableProcessor2.h"
#include "TrimmedArrayProcessor2.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

NonContextualGlyphSubstitutionProcessor2::NonContextualGlyphSubstitutionProcessor2()
{
}

NonContextualGlyphSubstitutionProcessor2::NonContextualGlyphSubstitutionProcessor2(const MorphSubtableHeader2 *morphSubtableHeader)
    : SubtableProcessor2(morphSubtableHeader)
{
}

NonContextualGlyphSubstitutionProcessor2::~NonContextualGlyphSubstitutionProcessor2()
{
}

SubtableProcessor2 *NonContextualGlyphSubstitutionProcessor2::createInstance(const MorphSubtableHeader2 *morphSubtableHeader)
{
    const NonContextualGlyphSubstitutionHeader2 *header = (const NonContextualGlyphSubstitutionHeader2 *) morphSubtableHeader;

    switch (SWAPW(header->table.format))
    {
    case ltfSimpleArray:
        return new SimpleArrayProcessor2(morphSubtableHeader);

    case ltfSegmentSingle:
        return new SegmentSingleProcessor2(morphSubtableHeader);

    case ltfSegmentArray:
        return new SegmentArrayProcessor2(morphSubtableHeader);

    case ltfSingleTable:
        return new SingleTableProcessor2(morphSubtableHeader);

    case ltfTrimmedArray:
        return new TrimmedArrayProcessor2(morphSubtableHeader);

    default:
        return NULL;
    }
}

U_NAMESPACE_END
