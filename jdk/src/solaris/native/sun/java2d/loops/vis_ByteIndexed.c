/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

#if !defined(JAVA2D_NO_MLIB) || defined(MLIB_ADD_SUFF)

#include <vis_proto.h>
#include "java2d_Mlib.h"
#include "vis_AlphaMacros.h"

/***************************************************************/

const mlib_u8 vis_sat_sh3_tbl[128 + 256 + 128] = {
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   0,  0,  0,  0,  0,  0,  0,  0,
   1,  1,  1,  1,  1,  1,  1,  1,
   2,  2,  2,  2,  2,  2,  2,  2,
   3,  3,  3,  3,  3,  3,  3,  3,
   4,  4,  4,  4,  4,  4,  4,  4,
   5,  5,  5,  5,  5,  5,  5,  5,
   6,  6,  6,  6,  6,  6,  6,  6,
   7,  7,  7,  7,  7,  7,  7,  7,
   8,  8,  8,  8,  8,  8,  8,  8,
   9,  9,  9,  9,  9,  9,  9,  9,
    10, 10, 10, 10, 10, 10, 10, 10,
    11, 11, 11, 11, 11, 11, 11, 11,
    12, 12, 12, 12, 12, 12, 12, 12,
    13, 13, 13, 13, 13, 13, 13, 13,
    14, 14, 14, 14, 14, 14, 14, 14,
    15, 15, 15, 15, 15, 15, 15, 15,
    16, 16, 16, 16, 16, 16, 16, 16,
    17, 17, 17, 17, 17, 17, 17, 17,
    18, 18, 18, 18, 18, 18, 18, 18,
    19, 19, 19, 19, 19, 19, 19, 19,
    20, 20, 20, 20, 20, 20, 20, 20,
    21, 21, 21, 21, 21, 21, 21, 21,
    22, 22, 22, 22, 22, 22, 22, 22,
    23, 23, 23, 23, 23, 23, 23, 23,
    24, 24, 24, 24, 24, 24, 24, 24,
    25, 25, 25, 25, 25, 25, 25, 25,
    26, 26, 26, 26, 26, 26, 26, 26,
    27, 27, 27, 27, 27, 27, 27, 27,
    28, 28, 28, 28, 28, 28, 28, 28,
    29, 29, 29, 29, 29, 29, 29, 29,
    30, 30, 30, 30, 30, 30, 30, 30,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
    31, 31, 31, 31, 31, 31, 31, 31,
};

/***************************************************************/

#define CHECK_LUT

/***************************************************************/

#define FUNC_CONVERT(FUNC, SRC_T)                                      \
void ADD_SUFF(SRC_T##ToByteIndexed##FUNC)(BLIT_PARAMS)                 \
{                                                                      \
    const mlib_u8 *p_tbl = vis_sat_sh3_tbl + 128;                      \
    mlib_s32 DstWriteXDither, DstWriteYDither;                         \
    mlib_s8 *DstWritererr, *DstWritegerr, *DstWriteberr;               \
    mlib_u8 *DstWriteInvLut;                                           \
    mlib_s32 srcScan = pSrcInfo->scanStride;                           \
    mlib_s32 dstScan = pDstInfo->scanStride;                           \
    mlib_s32 r, g, b;                                                  \
    mlib_s32 i, j;                                                     \
    CHECK_LUT                                                          \
                                                                       \
    DstWriteYDither = (pDstInfo->bounds.y1 & 7) << 3;                  \
    DstWriteInvLut = pDstInfo->invColorTable;                          \
                                                                       \
    for (j = 0; j < height; j++) {                                     \
        mlib_u8 *pSrc = srcBase;                                       \
        mlib_u8 *pDst = dstBase;                                       \
                                                                       \
        DstWritererr = pDstInfo->redErrTable + DstWriteYDither;        \
        DstWritegerr = pDstInfo->grnErrTable + DstWriteYDither;        \
        DstWriteberr = pDstInfo->bluErrTable + DstWriteYDither;        \
                                                                       \
        DstWriteXDither = pDstInfo->bounds.x1 & 7;                     \
                                                                       \
        for (i = 0; i < width; i++) {                                  \
            GET_RGB_##SRC_T(i)                                         \
            {                                                          \
                r = p_tbl[r + DstWritererr[DstWriteXDither]];          \
                g = p_tbl[g + DstWritegerr[DstWriteXDither]];          \
                b = p_tbl[b + DstWriteberr[DstWriteXDither]];          \
                                                                       \
                pDst[i] = DstWriteInvLut[(r << 10) + (g << 5) + b];    \
            }                                                          \
                                                                       \
            DstWriteXDither = (DstWriteXDither + 1) & 7;               \
        }                                                              \
                                                                       \
        PTR_ADD(dstBase, dstScan);                                     \
        PTR_ADD(srcBase, srcScan);                                     \
                                                                       \
        DstWriteYDither = (DstWriteYDither + (1 << 3)) & (7 << 3);     \
    }                                                                  \
}

/***************************************************************/

#define FUNC_SCALE_CONVERT(FUNC, SRC_T)                                \
void ADD_SUFF(SRC_T##ToByteIndexed##FUNC)(SCALE_PARAMS)                \
{                                                                      \
    const mlib_u8 *p_tbl = vis_sat_sh3_tbl + 128;                      \
    mlib_s32 DstWriteXDither, DstWriteYDither;                         \
    mlib_s8 *DstWritererr, *DstWritegerr, *DstWriteberr;               \
    mlib_u8 *DstWriteInvLut;                                           \
    mlib_s32 srcScan = pSrcInfo->scanStride;                           \
    mlib_s32 dstScan = pDstInfo->scanStride;                           \
    mlib_s32 r, g, b;                                                  \
    mlib_s32 i, j;                                                     \
    CHECK_LUT                                                          \
                                                                       \
    DstWriteYDither = (pDstInfo->bounds.y1 & 7) << 3;                  \
    DstWriteInvLut = pDstInfo->invColorTable;                          \
                                                                       \
    for (j = 0; j < height; j++) {                                     \
        mlib_u8 *pSrc = srcBase;                                       \
        mlib_u8 *pDst = dstBase;                                       \
        mlib_s32 tmpsxloc = sxloc;                                     \
                                                                       \
        PTR_ADD(pSrc, (syloc >> shift) * srcScan);                     \
                                                                       \
        DstWritererr = pDstInfo->redErrTable + DstWriteYDither;        \
        DstWritegerr = pDstInfo->grnErrTable + DstWriteYDither;        \
        DstWriteberr = pDstInfo->bluErrTable + DstWriteYDither;        \
                                                                       \
        DstWriteXDither = pDstInfo->bounds.x1 & 7;                     \
                                                                       \
        for (i = 0; i < width; i++) {                                  \
            mlib_s32 ii = tmpsxloc >> shift;                           \
            GET_RGB_##SRC_T(ii)                                        \
            {                                                          \
                r = p_tbl[r + DstWritererr[DstWriteXDither]];          \
                g = p_tbl[g + DstWritegerr[DstWriteXDither]];          \
                b = p_tbl[b + DstWriteberr[DstWriteXDither]];          \
                                                                       \
                pDst[i] = DstWriteInvLut[(r << 10) + (g << 5) + b];    \
            }                                                          \
                                                                       \
            DstWriteXDither = (DstWriteXDither + 1) & 7;               \
            tmpsxloc += sxinc;                                         \
        }                                                              \
                                                                       \
        PTR_ADD(dstBase, dstScan);                                     \
        syloc += syinc;                                                \
                                                                       \
        DstWriteYDither = (DstWriteYDither + (1 << 3)) & (7 << 3);     \
    }                                                                  \
}

/***************************************************************/

#define GET_PIX_IntArgbBm(i)                           \
    mlib_s32 pixel = *(mlib_s32*)(pSrc + 4*i);         \
    if (pixel >> 24)

#define GET_PIX_ByteIndexedBm(i)               \
    mlib_s32 pixel = SrcReadLut[pSrc[i]];      \
    if (pixel < 0)

#define FUNC_BGCOPY(SRC_T)                                             \
void ADD_SUFF(SRC_T##ToByteIndexedXparBgCopy)(BCOPY_PARAMS)            \
{                                                                      \
    const mlib_u8 *p_tbl = vis_sat_sh3_tbl + 128;                      \
    mlib_s32 DstWriteXDither, DstWriteYDither;                         \
    mlib_s8 *DstWritererr, *DstWritegerr, *DstWriteberr;               \
    mlib_u8 *DstWriteInvLut;                                           \
    mlib_s32 srcScan = pSrcInfo->scanStride;                           \
    mlib_s32 dstScan = pDstInfo->scanStride;                           \
    mlib_s32 r, g, b;                                                  \
    mlib_s32 i, j;                                                     \
    jint *SrcReadLut = pSrcInfo->lutBase;                              \
                                                                       \
    DstWriteYDither = (pDstInfo->bounds.y1 & 7) << 3;                  \
    DstWriteInvLut = pDstInfo->invColorTable;                          \
                                                                       \
    for (j = 0; j < height; j++) {                                     \
        mlib_u8 *pSrc = srcBase;                                       \
        mlib_u8 *pDst = dstBase;                                       \
                                                                       \
        DstWritererr = pDstInfo->redErrTable + DstWriteYDither;        \
        DstWritegerr = pDstInfo->grnErrTable + DstWriteYDither;        \
        DstWriteberr = pDstInfo->bluErrTable + DstWriteYDither;        \
                                                                       \
        DstWriteXDither = pDstInfo->bounds.x1 & 7;                     \
                                                                       \
        for (i = 0; i < width; i++) {                                  \
            GET_PIX_##SRC_T(i)                                         \
            {                                                          \
                b = (pixel) & 0xff;                                    \
                g = (pixel >> 8) & 0xff;                               \
                r = (pixel >> 16) & 0xff;                              \
                                                                       \
                r = p_tbl[r + DstWritererr[DstWriteXDither]];          \
                g = p_tbl[g + DstWritegerr[DstWriteXDither]];          \
                b = p_tbl[b + DstWriteberr[DstWriteXDither]];          \
                                                                       \
                pDst[i] = DstWriteInvLut[(r << 10) + (g << 5) + b];    \
            } else {                                                   \
                pDst[i] = bgpixel;                                     \
            }                                                          \
                                                                       \
            DstWriteXDither = (DstWriteXDither + 1) & 7;               \
        }                                                              \
                                                                       \
        PTR_ADD(dstBase, dstScan);                                     \
        PTR_ADD(srcBase, srcScan);                                     \
                                                                       \
        DstWriteYDither = (DstWriteYDither + (1 << 3)) & (7 << 3);     \
    }                                                                  \
}

FUNC_BGCOPY(ByteIndexedBm)
FUNC_BGCOPY(IntArgbBm)

/***************************************************************/

#define GET_RGB_IntArgb(i)                             \
    mlib_u32 pixel = *(mlib_u32*)(pSrc + 4*i);         \
    b = (pixel) & 0xff;                                \
    g = (pixel >> 8) & 0xff;                           \
    r = (pixel >> 16) & 0xff;

#define GET_RGB_ThreeByteBgr(i)        \
    b = pSrc[3*i];                     \
    g = pSrc[3*i + 1];                 \
    r = pSrc[3*i + 2];

#define GET_RGB_ByteGray(i)    \
    r = g = b = pSrc[i];

#define GET_RGB_Index12Gray(i)                         \
    r = SrcReadLut[((mlib_u16*)pSrc)[i] & 0xfff];      \
    r &= 0xff;                                         \
    g = b = r;

#define GET_RGB_ByteIndexed(i)                 \
    mlib_u32 pixel = SrcReadLut[pSrc[i]];      \
    b = (pixel) & 0xff;                        \
    g = (pixel >> 8) & 0xff;                   \
    r = (pixel >> 16) & 0xff;

#define GET_RGB_IntArgbBm(i)                           \
    mlib_s32 pixel = *(mlib_s32*)(pSrc + 4*i);         \
    b = (pixel) & 0xff;                                \
    g = (pixel >> 8) & 0xff;                           \
    r = (pixel >> 16) & 0xff;                          \
    if (pixel >> 24)

#define GET_RGB_ByteIndexedBm(i)               \
    mlib_s32 pixel = SrcReadLut[pSrc[i]];      \
    b = (pixel) & 0xff;                        \
    g = (pixel >> 8) & 0xff;                   \
    r = (pixel >> 16) & 0xff;                  \
    if (pixel < 0)

/***************************************************************/

FUNC_CONVERT(Convert, IntArgb)
FUNC_CONVERT(Convert, ThreeByteBgr)
FUNC_CONVERT(Convert, ByteGray)
FUNC_CONVERT(XparOver, IntArgbBm)
FUNC_SCALE_CONVERT(ScaleConvert, IntArgb)
FUNC_SCALE_CONVERT(ScaleConvert, ThreeByteBgr)
FUNC_SCALE_CONVERT(ScaleConvert, ByteGray)
FUNC_SCALE_CONVERT(ScaleXparOver, IntArgbBm)

/***************************************************************/

#undef  CHECK_LUT
#define CHECK_LUT      \
    jint *SrcReadLut = pSrcInfo->lutBase;

FUNC_CONVERT(Convert, Index12Gray)
FUNC_SCALE_CONVERT(ScaleConvert, Index12Gray)

FUNC_CONVERT(XparOver, ByteIndexedBm)
FUNC_SCALE_CONVERT(ScaleXparOver, ByteIndexedBm)

/***************************************************************/

#undef  CHECK_LUT
#define CHECK_LUT                                                      \
    jint *SrcReadLut = pSrcInfo->lutBase;                              \
    jint *DstReadLut = pDstInfo->lutBase;                              \
    if (checkSameLut(SrcReadLut, DstReadLut, pSrcInfo, pDstInfo)) {    \
        ADD_SUFF(AnyByteIsomorphicCopy)(BLIT_CALL_PARAMS);             \
        return;                                                        \
    }

FUNC_CONVERT(Convert, ByteIndexed)

#undef  CHECK_LUT
#define CHECK_LUT                                                      \
    jint *SrcReadLut = pSrcInfo->lutBase;                              \
    jint *DstReadLut = pDstInfo->lutBase;                              \
    if (checkSameLut(SrcReadLut, DstReadLut, pSrcInfo, pDstInfo)) {    \
        ADD_SUFF(AnyByteIsomorphicScaleCopy)(SCALE_CALL_PARAMS);       \
        return;                                                        \
    }

FUNC_SCALE_CONVERT(ScaleConvert, ByteIndexed)

/***************************************************************/

void ADD_SUFF(IntArgbToByteIndexedXorBlit)(BLIT_PARAMS)
{
    mlib_u8  *DstWriteInvLut;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 xorpixel = pCompInfo->details.xorPixel;
    mlib_s32 alphamask = pCompInfo->alphaMask;
    mlib_s32 i, j;

    DstWriteInvLut = pDstInfo->invColorTable;

    for (j = 0; j < height; j++) {
        mlib_s32 *pSrc = srcBase;
        mlib_u8  *pDst = dstBase;

        for (i = 0; i < width; i++) {
            mlib_s32 spix = pSrc[i];
            mlib_s32 dpix;
            if (spix < 0) {
                dpix = DstWriteInvLut[((spix >> 9) & 0x7C00) +
                                      ((spix >> 6) & 0x03E0) +
                                      ((spix >> 3) & 0x001F)];
                pDst[i] ^= (dpix ^ xorpixel) &~ alphamask;
            }
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

#define MASK_FILL(rr, pathA, dstA, dstARGB)                    \
{                                                              \
    mlib_d64 t0, t1;                                           \
    mlib_s32 srcF, dstF, srcA;                                 \
                                                               \
    srcF = ((dstA & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;          \
                                                               \
    srcF = MUL8_INT(srcF, pathA);                              \
    dstF = MUL8_INT(dstFbase, pathA) + (0xff - pathA);         \
                                                               \
    srcA = MUL8_INT(cnstA, srcF);                              \
    dstA = MUL8_INT(dstF, dstA);                               \
                                                               \
    t0 = MUL8_VIS(cnstARGB0, srcF);                            \
    t1 = MUL8_VIS(dstARGB, dstA);                              \
    rr = vis_fpadd16(t0, t1);                                  \
                                                               \
    dstA += srcA;                                              \
    DIV_ALPHA(rr, dstA);                                       \
}

/***************************************************************/

void ADD_SUFF(ByteIndexedAlphaMaskFill)(void *dstBase,
                                        jubyte *pMask,
                                        jint maskOff,
                                        jint maskScan,
                                        jint width,
                                        jint height,
                                        jint fgColor,
                                        SurfaceDataRasInfo *pDstInfo,
                                        NativePrimitive *pPrim,
                                        CompositeInfo *pCompInfo)
{
    const mlib_u8 *mul8_tbl = (void*)mul8table;
    const mlib_u8 *p_tbl = vis_sat_sh3_tbl + 128;
    mlib_s32 DstWriteXDither, DstWriteYDither;
    mlib_s8 *DstWritererr, *DstWritegerr, *DstWriteberr;
    mlib_u8 *DstWriteInvLut;
    mlib_s32 r, g, b;
    mlib_f32 *DstReadLut = (void*)(pDstInfo->lutBase);
    mlib_s32 cnstA, cnstR, cnstG, cnstB;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_f32 cnstARGB0;
    mlib_s32 SrcOpAnd, SrcOpXor, SrcOpAdd;
    mlib_s32 DstOpAnd, DstOpXor, DstOpAdd;
    mlib_s32 dstFbase;
    mlib_s32 j;

    cnstA = (fgColor >> 24) & 0xff;
    cnstR = (fgColor >> 16) & 0xff;
    cnstG = (fgColor >>  8) & 0xff;
    cnstB = (fgColor      ) & 0xff;

    if (cnstA != 0xff) {
        cnstR = mul8table[cnstA][cnstR];
        cnstG = mul8table[cnstA][cnstG];
        cnstB = mul8table[cnstA][cnstB];
    }

    cnstARGB0 = F32_FROM_U8x4(cnstA, cnstR, cnstG, cnstB);

    SrcOpAnd = (AlphaRules[pCompInfo->rule].srcOps).andval;
    SrcOpXor = (AlphaRules[pCompInfo->rule].srcOps).xorval;
    SrcOpAdd = (AlphaRules[pCompInfo->rule].srcOps).addval;
    SrcOpAdd -= SrcOpXor;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval;
    DstOpAdd -= DstOpXor;

    dstFbase = (((cnstA) & DstOpAnd) ^ DstOpXor) + DstOpAdd;

    vis_write_gsr(7 << 3);

    if (pMask != NULL) {
        DstWriteYDither = (pDstInfo->bounds.y1 & 7) << 3;
        DstWriteInvLut = pDstInfo->invColorTable;

        pMask += maskOff;

        if (dstScan == width && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            mlib_u8  *pDst = dstBase;
            mlib_s32 i;
            mlib_s32 pathA0, dstA0, dst_val, pixel;
            mlib_d64 res0;
            mlib_f32 dstARGB0;

            DstWritererr = pDstInfo->redErrTable + DstWriteYDither;
            DstWritegerr = pDstInfo->grnErrTable + DstWriteYDither;
            DstWriteberr = pDstInfo->bluErrTable + DstWriteYDither;

            DstWriteXDither = pDstInfo->bounds.x1 & 7;

#pragma pipeloop(0)
            for (i = 0; i < width; i++) {
                dst_val = pDst[i];
                pathA0 = pMask[i];
                dstA0 = *(mlib_u8*)(DstReadLut + dst_val);
                dstARGB0 = DstReadLut[dst_val];
                MASK_FILL(res0, pathA0, dstA0, dstARGB0);
                dstARGB0 = vis_fpack16(res0);

                pixel = *(mlib_s32*)&dstARGB0;
                b = (pixel) & 0xff;
                g = (pixel >> 8) & 0xff;
                r = (pixel >> 16) & 0xff;
                r = p_tbl[r + DstWritererr[DstWriteXDither]];
                g = p_tbl[g + DstWritegerr[DstWriteXDither]];
                b = p_tbl[b + DstWriteberr[DstWriteXDither]];
                pDst[i] = DstWriteInvLut[(r << 10) + (g << 5) + b];

                DstWriteXDither = (DstWriteXDither + 1) & 7;
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(pMask, maskScan);
            DstWriteYDither = (DstWriteYDither + (1 << 3)) & (7 << 3);
        }
    }/* else {
        if (dstScan == 4*width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntArgbAlphaMaskFill_A1_line(dstBase, pMask, width,
                                         cnstARGB0,
                                         log_val, mul8_cnstA, mul8_dstF,
                                         (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
        }
    }*/
}

/***************************************************************/

#endif
