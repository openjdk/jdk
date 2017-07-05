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
 * (C) Copyright IBM Corp. 1998-2013 - All Rights Reserved
 *
 */

#ifndef __INDICREARRANGEMENT_H
#define __INDICREARRANGEMENT_H

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

struct IndicRearrangementSubtableHeader : MorphStateTableHeader
{
};

struct IndicRearrangementSubtableHeader2 : MorphStateTableHeader2
{
};

enum IndicRearrangementFlags
{
    irfMarkFirst    = 0x8000,
    irfDontAdvance  = 0x4000,
    irfMarkLast     = 0x2000,
    irfReserved     = 0x1FF0,
    irfVerbMask     = 0x000F
};

enum IndicRearrangementVerb
{
    irvNoAction = 0x0000,               /*   no action    */
    irvxA       = 0x0001,               /*    Ax => xA    */
    irvDx       = 0x0002,               /*    xD => Dx    */
    irvDxA      = 0x0003,               /*   AxD => DxA   */

    irvxAB      = 0x0004,               /*   ABx => xAB   */
    irvxBA      = 0x0005,               /*   ABx => xBA   */
    irvCDx      = 0x0006,               /*   xCD => CDx   */
    irvDCx      = 0x0007,               /*   xCD => DCx   */

    irvCDxA     = 0x0008,               /*  AxCD => CDxA  */
    irvDCxA     = 0x0009,               /*  AxCD => DCxA  */
    irvDxAB     = 0x000A,               /*  ABxD => DxAB  */
    irvDxBA     = 0x000B,               /*  ABxD => DxBA  */

    irvCDxAB    = 0x000C,               /* ABxCD => CDxAB */
    irvCDxBA    = 0x000D,               /* ABxCD => CDxBA */
    irvDCxAB    = 0x000E,               /* ABxCD => DCxAB */
    irvDCxBA    = 0x000F                /* ABxCD => DCxBA */
};

struct IndicRearrangementStateEntry : StateEntry
{
};

struct IndicRearrangementStateEntry2 : StateEntry2
{
};

U_NAMESPACE_END
#endif

