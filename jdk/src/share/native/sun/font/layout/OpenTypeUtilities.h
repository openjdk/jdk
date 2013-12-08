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

#ifndef __OPENTYPEUTILITIES_H
#define __OPENTYPEUTILITIES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "OpenTypeTables.h"

U_NAMESPACE_BEGIN

class OpenTypeUtilities /* not : public UObject because all methods are static */ {
public:
    static le_int8 highBit(le_int32 value);
    static Offset getTagOffset(LETag tag, const LEReferenceToArrayOf<TagAndOffsetRecord> &records, LEErrorCode &success);
#if LE_ENABLE_RAW
    static le_int32 getGlyphRangeIndex(TTGlyphID glyphID, const GlyphRangeRecord *records, le_int32 recordCount) {
      LEErrorCode success = LE_NO_ERROR;
      LETableReference recordRef0((const le_uint8*)records);
      LEReferenceToArrayOf<GlyphRangeRecord> recordRef(recordRef0, success, (size_t)0, recordCount);
      return getGlyphRangeIndex(glyphID, recordRef, success);
    }
#endif
    static le_int32 getGlyphRangeIndex(TTGlyphID glyphID, const LEReferenceToArrayOf<GlyphRangeRecord> &records, LEErrorCode &success);
    static le_int32 search(le_uint16 value, const le_uint16 array[], le_int32 count);
    static le_int32 search(le_uint32 value, const le_uint32 array[], le_int32 count);
    static void sort(le_uint16 *array, le_int32 count);

private:
    OpenTypeUtilities() {} // private - forbid instantiation
};

U_NAMESPACE_END
#endif
