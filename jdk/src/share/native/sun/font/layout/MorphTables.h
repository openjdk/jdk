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

#ifndef __MORPHTABLES_H
#define __MORPHTABLES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "LayoutTables.h"

U_NAMESPACE_BEGIN

class LEGlyphStorage;

typedef le_uint32 FeatureFlags;

typedef le_int16 FeatureType;
typedef le_int16 FeatureSetting;

struct FeatureTableEntry
{
    FeatureType     featureType;
    FeatureSetting  featureSetting;
    FeatureFlags    enableFlags;
    FeatureFlags    disableFlags;
};

struct ChainHeader
{
    FeatureFlags        defaultFlags;
    le_uint32           chainLength;
    le_int16           nFeatureEntries;
    le_int16           nSubtables;
    FeatureTableEntry   featureTable[ANY_NUMBER];
};

struct MorphTableHeader
{
    le_int32    version;
    le_uint32   nChains;
    ChainHeader chains[ANY_NUMBER];

    void process(LEGlyphStorage &glyphStorage) const;
};

typedef le_int16 SubtableCoverage;

enum SubtableCoverageFlags
{
    scfVertical = 0x8000,
    scfReverse  = 0x4000,
    scfIgnoreVt = 0x2000,
    scfReserved = 0x1FF8,
    scfTypeMask = 0x0007
};

enum MorphSubtableType
{
    mstIndicRearrangement               = 0,
    mstContextualGlyphSubstitution      = 1,
    mstLigatureSubstitution             = 2,
    mstReservedUnused                   = 3,
    mstNonContextualGlyphSubstitution   = 4,
    mstContextualGlyphInsertion         = 5
};

struct MorphSubtableHeader
{
    le_int16           length;
    SubtableCoverage    coverage;
    FeatureFlags        subtableFeatures;

    void process(LEGlyphStorage &glyphStorage) const;
};

U_NAMESPACE_END
#endif

