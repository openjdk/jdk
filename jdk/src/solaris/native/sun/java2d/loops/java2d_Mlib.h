/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef Java2d_Mlib_h_Included
#define Java2d_Mlib_h_Included

#include <mlib_image.h>
#include "mlib_ImageCopy.h"

#include "AnyByte.h"
#include "Any3Byte.h"
#include "Any4Byte.h"
#include "AnyShort.h"
#include "AnyInt.h"
#include "IntArgb.h"
#include "IntArgbBm.h"
#include "IntRgb.h"
#include "ByteGray.h"
#include "ByteIndexed.h"
#include "Index8Gray.h"
#include "Index12Gray.h"

/***************************************************************/

#ifdef MLIB_ADD_SUFF
#define ADD_SUFF(x) x##_F
#else
#define ADD_SUFF(x) x
#endif

/***************************************************************/

#define MLIB_AnyByte   MLIB_BYTE
#define MLIB_Any3Byte  MLIB_BYTE
#define MLIB_Any4Byte  MLIB_BYTE
#define MLIB_AnyShort  MLIB_SHORT
#define MLIB_AnyInt    MLIB_INT

/***************************************************************/

#define NCHAN_AnyByte   1
#define NCHAN_Any3Byte  3
#define NCHAN_Any4Byte  4
#define NCHAN_AnyShort  1
#define NCHAN_AnyInt    1

/***************************************************************/

#define BLIT_PARAMS                    \
    void *srcBase, void *dstBase,      \
    juint width, juint height,         \
    SurfaceDataRasInfo *pSrcInfo,      \
    SurfaceDataRasInfo *pDstInfo,      \
    NativePrimitive *pPrim,            \
    CompositeInfo *pCompInfo

#define BLIT_CALL_PARAMS               \
    srcBase, dstBase, width, height,   \
    pSrcInfo, pDstInfo, pPrim, pCompInfo

/***************************************************************/

#define SCALE_PARAMS                           \
    void *srcBase, void *dstBase,              \
    juint width, juint height,                 \
    jint sxloc, jint syloc,                    \
    jint sxinc, jint syinc, jint shift,        \
    SurfaceDataRasInfo * pSrcInfo,             \
    SurfaceDataRasInfo * pDstInfo,             \
    NativePrimitive * pPrim,                   \
    CompositeInfo * pCompInfo

#define SCALE_CALL_PARAMS                      \
    srcBase, dstBase, width, height,           \
    sxloc, syloc, sxinc, syinc, shift,         \
    pSrcInfo, pDstInfo, pPrim, pCompInfo

/***************************************************************/

#define BCOPY_PARAMS                   \
    void *srcBase, void *dstBase,      \
    juint width, juint height,         \
    jint bgpixel,                      \
    SurfaceDataRasInfo * pSrcInfo,     \
    SurfaceDataRasInfo * pDstInfo,     \
    NativePrimitive * pPrim,           \
    CompositeInfo * pCompInfo

#define BCOPY_CALL_PARAMS              \
    srcBase, dstBase, width, height,   \
    bgpixel,                           \
    pSrcInfo, pDstInfo, pPrim, pCompInfo

/***************************************************************/

#define MASKBLIT_PARAMS                \
    void *dstBase,                     \
    void *srcBase,                     \
    jubyte *pMask,                     \
    jint maskOff,                      \
    jint maskScan,                     \
    jint width,                        \
    jint height,                       \
    SurfaceDataRasInfo *pDstInfo,      \
    SurfaceDataRasInfo *pSrcInfo,      \
    NativePrimitive *pPrim,            \
    CompositeInfo *pCompInfo

#define MASKBLIT_CALL_PARAMS                   \
    dstBase, srcBase, pMask,                   \
    maskOff, maskScan, width, height,          \
    pSrcInfo, pDstInfo, pPrim, pCompInfo

/***************************************************************/

#define GLYPH_LIST_PARAMS              \
    SurfaceDataRasInfo * pRasInfo,     \
    ImageRef *glyphs,                  \
    jint totalGlyphs,                  \
    jint fgpixel, jint argbcolor,      \
    jint clipLeft, jint clipTop,       \
    jint clipRight, jint clipBottom,   \
    NativePrimitive * pPrim,           \
    CompositeInfo * pCompInfo

/***************************************************************/

#define MLIB_IMAGE_SET(image, data_type, nchan, w, h, scan, data_ptr)        \
    image->type     = data_type;                                             \
    image->channels = nchan;                                                 \
    image->width    = w;                                                     \
    image->height   = h;                                                     \
    image->stride   = scan;                                                  \
    image->data     = (void*)(data_ptr)

/***************************************************************/

#define PTR_ADD(ptr, scan)     \
    ptr = (void*)((mlib_u8*)(ptr) + (scan))

/***************************************************************/

#define EXTRACT_CONST_1(pixel)         \
    mlib_s32 pixel##0 = pixel

#define EXTRACT_CONST_3(pixel)         \
    mlib_s32 pixel##0 = pixel;         \
    mlib_s32 pixel##1 = pixel >> 8;    \
    mlib_s32 pixel##2 = pixel >> 16

#define EXTRACT_CONST_4(pixel)         \
    mlib_s32 pixel##0 = pixel;         \
    mlib_s32 pixel##1 = pixel >> 8;    \
    mlib_s32 pixel##2 = pixel >> 16;   \
    mlib_s32 pixel##3 = pixel >> 24

/***************************************************************/

#define STORE_CONST_1(ptr, pixel)      \
    ptr[0] = pixel

#define STORE_CONST_3(ptr, pixel)      \
    ptr[0] = pixel;                    \
    ptr[1] = pixel >> 8;               \
    ptr[2] = pixel >> 16

#define STORE_CONST_4(ptr, pixel)      \
    ptr[0] = pixel;                    \
    ptr[1] = pixel >> 8;               \
    ptr[2] = pixel >> 16;              \
    ptr[3] = pixel >> 24

/***************************************************************/

#define PROCESS_PIX_1(BODY)    \
    BODY(i, 0)

#define PROCESS_PIX_3(BODY)    \
    BODY(3*i,     0);          \
    BODY(3*i + 1, 1);          \
    BODY(3*i + 2, 2)

#define PROCESS_PIX_4(BODY)    \
    BODY(4*i,     0);          \
    BODY(4*i + 1, 1);          \
    BODY(4*i + 2, 2);          \
    BODY(4*i + 3, 3)

/***************************************************************/

#define LOOP_DST(TYPE, NCHAN, dstBase, dstScan, BODY)          \
{                                                              \
    TYPE##DataType *dst_ptr = (void*)(dstBase);                \
    mlib_s32 i, j;                                             \
    j = 0;                                                     \
    do {                                                       \
        i = 0;                                                 \
        do {                                                   \
            PROCESS_PIX_##NCHAN(BODY);                         \
            i++;                                               \
        } while (i < width);                                   \
        PTR_ADD(dst_ptr, dstScan);                             \
        j++;                                                   \
    } while (j < height);                                      \
}

#define LOOP_DST_SRC(TYPE, NCHAN, dstBase, dstScan,    \
                     srcBase, srcScan, BODY)           \
{                                                      \
    TYPE##DataType *dst_ptr = (void*)(dstBase);        \
    TYPE##DataType *src_ptr = (void*)(srcBase);        \
    mlib_s32 i, j;                                     \
    for (j = 0; j < height; j++) {                     \
        for (i = 0; i < width; i++) {                  \
            PROCESS_PIX_##NCHAN(BODY);                 \
        }                                              \
        PTR_ADD(dst_ptr, dstScan);                     \
        PTR_ADD(src_ptr, srcScan);                     \
    }                                                  \
}

/***************************************************************/

#define LOAD_2F32(ptr, ind0, ind1)     \
    vis_freg_pair(((mlib_f32*)(ptr))[ind0], ((mlib_f32*)(ptr))[ind1])

/***************************************************************/

#define LOAD_NEXT_U8(dd, ptr)          \
    dd = vis_faligndata(vis_ld_u8(ptr), dd)

/***************************************************************/

#define LOAD_NEXT_U16(dd, ptr)         \
    dd = vis_faligndata(vis_ld_u16(ptr), dd)

/***************************************************************/

jboolean checkSameLut(jint * SrcReadLut,
                      jint * DstReadLut,
                      SurfaceDataRasInfo * pSrcInfo,
                      SurfaceDataRasInfo * pDstInfo);

void ADD_SUFF(AnyByteIsomorphicCopy)(BLIT_PARAMS);

void ADD_SUFF(AnyByteIsomorphicScaleCopy)(SCALE_PARAMS);

void ADD_SUFF(AnyByteSetRect)(SurfaceDataRasInfo * pRasInfo,
                              jint lox, jint loy, jint hix,
                              jint hiy, jint pixel,
                              NativePrimitive * pPrim,
                              CompositeInfo * pCompInfo);

void ADD_SUFF(Any4ByteSetRect)(SurfaceDataRasInfo * pRasInfo,
                               jint lox, jint loy, jint hix,
                               jint hiy, jint pixel,
                               NativePrimitive * pPrim,
                               CompositeInfo * pCompInfo);

void ADD_SUFF(Any3ByteSetRect)(SurfaceDataRasInfo * pRasInfo,
                               jint lox, jint loy, jint hix,
                               jint hiy, jint pixel,
                               NativePrimitive * pPrim,
                               CompositeInfo * pCompInfo);

void ADD_SUFF(AnyIntSetRect)(SurfaceDataRasInfo * pRasInfo,
                             jint lox, jint loy, jint hix,
                             jint hiy, jint pixel,
                             NativePrimitive * pPrim,
                             CompositeInfo * pCompInfo);

void AnyByteSetRect(SurfaceDataRasInfo * pRasInfo,
                    jint lox, jint loy, jint hix,
                    jint hiy, jint pixel,
                    NativePrimitive * pPrim,
                    CompositeInfo * pCompInfo);

void AnyIntSetRect(SurfaceDataRasInfo * pRasInfo,
                   jint lox, jint loy, jint hix,
                   jint hiy, jint pixel,
                   NativePrimitive * pPrim,
                   CompositeInfo * pCompInfo);

void ADD_SUFF(IntArgbToByteGrayConvert)(BLIT_PARAMS);
void ADD_SUFF(ByteGrayToIntArgbConvert)(BLIT_PARAMS);
void ADD_SUFF(FourByteAbgrToIntArgbConvert)(BLIT_PARAMS);
void ADD_SUFF(IntArgbToFourByteAbgrConvert)(BLIT_PARAMS);
void ADD_SUFF(ThreeByteBgrToIntArgbConvert)(BLIT_PARAMS);
void ADD_SUFF(TreeByteBgrToIntArgbConvert)(BLIT_PARAMS);
void ADD_SUFF(IntArgbPreToIntArgbConvert)(BLIT_PARAMS);
void ADD_SUFF(FourByteAbgrToIntArgbScaleConvert)(SCALE_PARAMS);
void ADD_SUFF(ByteGrayToIntArgbPreConvert)(BLIT_PARAMS);
void ADD_SUFF(IntArgbToIntArgbPreConvert)(BLIT_PARAMS);
void ADD_SUFF(IntRgbToIntArgbPreConvert)(BLIT_PARAMS);
void ADD_SUFF(ThreeByteBgrToIntArgbPreConvert)(BLIT_PARAMS);
void ADD_SUFF(ByteGrayToIntArgbPreScaleConvert)(SCALE_PARAMS);
void ADD_SUFF(IntArgbToIntArgbPreScaleConvert)(SCALE_PARAMS);
void ADD_SUFF(IntRgbToIntArgbPreScaleConvert)(SCALE_PARAMS);
void ADD_SUFF(ThreeByteBgrToIntArgbPreScaleConvert)(SCALE_PARAMS);
void ADD_SUFF(ByteIndexedToFourByteAbgrConvert)(BLIT_PARAMS);
void ADD_SUFF(ByteIndexedBmToFourByteAbgrXparOver)(BLIT_PARAMS);
void ADD_SUFF(ByteIndexedBmToFourByteAbgrScaleXparOver)(SCALE_PARAMS);
void ADD_SUFF(ByteIndexedToFourByteAbgrScaleConvert)(SCALE_PARAMS);
void ADD_SUFF(IntArgbToThreeByteBgrConvert)(BLIT_PARAMS);
void ADD_SUFF(IntArgbToUshortGrayConvert)(BLIT_PARAMS);
void ADD_SUFF(ByteIndexedBmToFourByteAbgrXparBgCopy)(BCOPY_PARAMS);

void IntArgbToThreeByteBgrConvert(BLIT_PARAMS);

/***************************************************************/

#endif /* Java2d_Mlib_h_Included */
