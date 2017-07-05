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
 * (C) Copyright IBM Corp. 1998-2004 - All Rights Reserved
 *
 */

#ifndef __ANCHORTABLES_H
#define __ANCHORTABLES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "LEFontInstance.h"
#include "OpenTypeTables.h"

U_NAMESPACE_BEGIN

struct AnchorTable
{
    le_uint16  anchorFormat;
    le_int16   xCoordinate;
    le_int16   yCoordinate;

    void    getAnchor(LEGlyphID glyphID, const LEFontInstance *fontInstance,
                      LEPoint &anchor) const;
};

struct Format1AnchorTable : AnchorTable
{
    void getAnchor(const LEFontInstance *fontInstance, LEPoint &anchor) const;
};

struct Format2AnchorTable : AnchorTable
{
    le_uint16  anchorPoint;

    void getAnchor(LEGlyphID glyphID, const LEFontInstance *fontInstance, LEPoint &anchor) const;
};

struct Format3AnchorTable : AnchorTable
{
    Offset  xDeviceTableOffset;
    Offset  yDeviceTableOffset;

    void getAnchor(const LEFontInstance *fontInstance, LEPoint &anchor) const;
};

U_NAMESPACE_END
#endif


