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
 *
 * (C) Copyright IBM Corp. 1998 - 2005 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "OpenTypeTables.h"
#include "DeviceTables.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

const le_uint16 DeviceTable::fieldMasks[]    = {0x0003, 0x000F, 0x00FF};
const le_uint16 DeviceTable::fieldSignBits[] = {0x0002, 0x0008, 0x0080};
const le_uint16 DeviceTable::fieldBits[]     = {     2,      4,      8};

le_int16 DeviceTable::getAdjustment(le_uint16 ppem) const
{
    le_uint16 start = SWAPW(startSize);
    le_uint16 format = SWAPW(deltaFormat) - 1;
    le_int16 result = 0;

    if (ppem >= start && ppem <= SWAPW(endSize)) {
        le_uint16 sizeIndex = ppem - start;
        le_uint16 bits = fieldBits[format];
        le_uint16 count = 16 / bits;
        le_uint16 word = SWAPW(deltaValues[sizeIndex / count]);
        le_uint16 fieldIndex = sizeIndex % count;
        le_uint16 shift = 16 - (bits * (fieldIndex + 1));
        le_uint16 field = (word >> shift) & fieldMasks[format];

        result = field;

        if ((field & fieldSignBits[format]) != 0) {
            result |= ~ fieldMasks[format];
        }
    }

    return result;
}

U_NAMESPACE_END
