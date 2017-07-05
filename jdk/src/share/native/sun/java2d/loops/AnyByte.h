/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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
 */

#include "GraphicsPrimitiveMgr.h"
#include "LoopMacros.h"

/*
 * This file contains macro and type definitions used by the macros in
 * LoopMacros.h to manipulate a surface of type "AnyByte".
 */

typedef jbyte   AnyByteDataType;

#define AnyBytePixelStride      1

#define DeclareAnyByteLoadVars(PREFIX)
#define DeclareAnyByteStoreVars(PREFIX)
#define InitAnyByteLoadVars(PREFIX, pRasInfo)
#define InitAnyByteStoreVarsY(PREFIX, pRasInfo)
#define InitAnyByteStoreVarsX(PREFIX, pRasInfo)
#define NextAnyByteStoreVarsX(PREFIX)
#define NextAnyByteStoreVarsY(PREFIX)

#define DeclareAnyBytePixelData(PREFIX)

#define ExtractAnyBytePixelData(PIXEL, PREFIX)

#define StoreAnyBytePixelData(pPix, x, pixel, PREFIX) \
    (pPix)[x] = (jbyte) (pixel)

#define CopyAnyBytePixelData(pSrc, sx, pDst, dx) \
    (pDst)[dx] = (pSrc)[sx]

#define XorCopyAnyBytePixelData(pSrc, pDst, x, xorpixel, XORPREFIX) \
    (pDst)[x] ^= (pSrc)[x] ^ (xorpixel)

#define XorAnyBytePixelData(srcpixel, SRCPREFIX, pDst, x, \
                            xorpixel, XORPREFIX, mask, MASKPREFIX) \
    (pDst)[x] ^= (((srcpixel) ^ (xorpixel)) & ~(mask))

DECLARE_ISOCOPY_BLIT(AnyByte);
DECLARE_ISOSCALE_BLIT(AnyByte);
DECLARE_ISOXOR_BLIT(AnyByte);

#define REGISTER_ANYBYTE_ISOCOPY_BLIT(BYTETYPE) \
    REGISTER_ISOCOPY_BLIT(BYTETYPE, AnyByte)

#define REGISTER_ANYBYTE_ISOSCALE_BLIT(BYTETYPE) \
    REGISTER_ISOSCALE_BLIT(BYTETYPE, AnyByte)

#define REGISTER_ANYBYTE_ISOXOR_BLIT(BYTETYPE) \
    REGISTER_ISOXOR_BLIT(BYTETYPE, AnyByte)
