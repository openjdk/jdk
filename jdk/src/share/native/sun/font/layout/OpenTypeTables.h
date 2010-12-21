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
 * (C) Copyright IBM Corp. 1998-2010 - All Rights Reserved
 *
 */

#ifndef __OPENTYPETABLES_H
#define __OPENTYPETABLES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"

U_NAMESPACE_BEGIN

#define ANY_NUMBER 1

typedef le_uint16 Offset;
typedef le_uint8  ATag[4];
typedef le_uint32 fixed32;

#define LE_GLYPH_GROUP_MASK 0x00000001UL
typedef le_uint32 FeatureMask;

#define SWAPT(atag) ((LETag) ((atag[0] << 24) + (atag[1] << 16) + (atag[2] << 8) + atag[3]))

struct TagAndOffsetRecord
{
    ATag   tag;
    Offset offset;
};

struct GlyphRangeRecord
{
    TTGlyphID firstGlyph;
    TTGlyphID lastGlyph;
    le_int16  rangeValue;
};

struct FeatureMap
{
    LETag       tag;
    FeatureMask mask;
};

U_NAMESPACE_END
#endif
