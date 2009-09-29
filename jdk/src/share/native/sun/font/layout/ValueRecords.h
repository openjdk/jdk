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

#ifndef __VALUERECORDS_H
#define __VALUERECORDS_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "LEFontInstance.h"
#include "OpenTypeTables.h"
#include "GlyphIterator.h"

U_NAMESPACE_BEGIN

typedef le_uint16 ValueFormat;
typedef le_int16 ValueRecordField;

struct ValueRecord
{
    le_int16   values[ANY_NUMBER];

    le_int16   getFieldValue(ValueFormat valueFormat, ValueRecordField field) const;
    le_int16   getFieldValue(le_int16 index, ValueFormat valueFormat, ValueRecordField field) const;
    void    adjustPosition(ValueFormat valueFormat, const char *base, GlyphIterator &glyphIterator,
                const LEFontInstance *fontInstance) const;
    void    adjustPosition(le_int16 index, ValueFormat valueFormat, const char *base, GlyphIterator &glyphIterator,
                const LEFontInstance *fontInstance) const;

    static le_int16    getSize(ValueFormat valueFormat);

private:
    static le_int16    getFieldCount(ValueFormat valueFormat);
    static le_int16    getFieldIndex(ValueFormat valueFormat, ValueRecordField field);
};

enum ValueRecordFields
{
    vrfXPlacement   = 0,
    vrfYPlacement   = 1,
    vrfXAdvance     = 2,
    vrfYAdvance     = 3,
    vrfXPlaDevice   = 4,
    vrfYPlaDevice   = 5,
    vrfXAdvDevice   = 6,
    vrfYAdvDevice   = 7
};

enum ValueFormatBits
{
    vfbXPlacement   = 0x0001,
    vfbYPlacement   = 0x0002,
    vfbXAdvance     = 0x0004,
    vfbYAdvance     = 0x0008,
    vfbXPlaDevice   = 0x0010,
    vfbYPlaDevice   = 0x0020,
    vfbXAdvDevice   = 0x0040,
    vfbYAdvDevice   = 0x0080,
    vfbReserved     = 0xFF00,
    vfbAnyDevice    = vfbXPlaDevice + vfbYPlaDevice + vfbXAdvDevice + vfbYAdvDevice
};

U_NAMESPACE_END
#endif


