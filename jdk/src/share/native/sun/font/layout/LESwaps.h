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
 * (C) Copyright IBM Corp. 1998-2005 - All Rights Reserved
 *
 */

#ifndef __LESWAPS_H
#define __LESWAPS_H

#include "LETypes.h"

/**
 * \file
 * \brief C++ API: Endian independent access to data for LayoutEngine
 */

U_NAMESPACE_BEGIN

/**
 * A convenience macro which invokes the swapWord member function
 * from a concise call.
 *
 * @stable ICU 2.8
 */
#if defined(U_IS_BIG_ENDIAN)
    #if U_IS_BIG_ENDIAN
        #define SWAPW(value) (value)
    #else
        #define SWAPW(value) LESwaps::swapWord(value)
    #endif
#else
    #define SWAPW(value) (LESwaps::isBigEndian() ? (value) : LESwaps::swapWord(value))
#endif

/**
 * A convenience macro which invokes the swapLong member function
 * from a concise call.
 *
 * @stable ICU 2.8
 */
#if defined(U_IS_BIG_ENDIAN)
    #if U_IS_BIG_ENDIAN
        #define SWAPL(value) (value)
    #else
        #define SWAPL(value) LESwaps::swapLong(value)
    #endif
#else
    #define SWAPL(value) (LESwaps::isBigEndian() ? (value) : LESwaps::swapLong(value))
#endif

/**
 * This class is used to access data which stored in big endian order
 * regardless of the conventions of the platform. It has been designed
 * to automatically detect the endian-ness of the platform, so that a
 * compilation flag is not needed.
 *
 * All methods are static and inline in an attempt to induce the compiler
 * to do most of the calculations at compile time.
 *
 * @stable ICU 2.8
 */
class U_LAYOUT_API LESwaps /* not : public UObject because all methods are static */ {
public:

#if !defined(U_IS_BIG_ENDIAN)
    /**
     * This method detects the endian-ness of the platform by
     * casting a pointer to a word to a pointer to a byte. On
     * big endian platforms the FF will be in the byte with the
     * lowest address. On little endian platforms, the FF will
     * be in the byte with the highest address.
     *
     * @return TRUE if the platform is big endian
     *
     * @stable ICU 2.8
     */
    static le_uint8 isBigEndian()
    {
        const le_uint16 word = 0xFF00;

        return *((le_uint8 *) &word);
    };
#endif

    /**
     * This method does the byte swap required on little endian platforms
     * to correctly access a (16-bit) word.
     *
     * @param value - the word to be byte swapped
     *
     * @return the byte swapped word
     *
     * @stable ICU 2.8
     */
    static le_uint16 swapWord(le_uint16 value)
    {
        return (((le_uint8) (value >> 8)) | (value << 8));
    };

    /**
     * This method does the byte swapping required on little endian platforms
     * to correctly access a (32-bit) long.
     *
     * @param value - the long to be byte swapped
     *
     * @return the byte swapped long
     *
     * @stable ICU 2.8
     */
    static le_uint32 swapLong(le_uint32 value)
    {
        return swapWord((le_uint16) (value >> 16)) | (swapWord((le_uint16) value) << 16);
    };

private:
    LESwaps() {} // private - forbid instantiation
};

U_NAMESPACE_END
#endif
