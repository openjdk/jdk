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
 * (C) Copyright IBM Corp. 1998-2005 - All Rights Reserved
 *
 */

#ifndef __DEFAULTCHARMAPPER_H
#define __DEFAULTCHARMAPPER_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "LEFontInstance.h"

U_NAMESPACE_BEGIN

/**
 * This class is an instance of LECharMapper which
 * implements control character filtering and bidi
 * mirroring.
 *
 * @see LECharMapper
 */
class DefaultCharMapper : public UMemory, public LECharMapper
{
private:
    le_bool fFilterControls;
    le_bool fMirror;
    le_bool fZWJ;

    static const LEUnicode32 controlChars[];

    static const le_int32 controlCharsCount;

    static const LEUnicode32 controlCharsZWJ[];

    static const le_int32 controlCharsZWJCount;

    static const LEUnicode32 mirroredChars[];
    static const LEUnicode32 srahCderorrim[];

    static const le_int32 mirroredCharsCount;

public:
    DefaultCharMapper(le_bool filterControls, le_bool mirror, le_bool zwj = 0)
        : fFilterControls(filterControls), fMirror(mirror), fZWJ(zwj)
    {
        // nothing
    };

    ~DefaultCharMapper()
    {
        // nada
    };

    LEUnicode32 mapChar(LEUnicode32 ch) const;
};

U_NAMESPACE_END
#endif
