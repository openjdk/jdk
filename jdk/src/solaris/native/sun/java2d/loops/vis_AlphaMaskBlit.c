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

#if !defined(JAVA2D_NO_MLIB) || defined(MLIB_ADD_SUFF)

#include "vis_AlphaMacros.h"

/***************************************************************/

/* ##############################################################
 * IntArgbToIntArgbAlphaMaskBlit()
 * IntArgbToFourByteAbgrAlphaMaskBlit()
 */

#define MASK_FILL(rr, pathA, dstA, dstARGB, srcA, srcARGB)     \
{                                                              \
    mlib_s32 srcF, dstF;                                       \
                                                               \
    srcA = mul8_extra[srcA];                                   \
                                                               \
    srcF = ((dstA & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;          \
    dstF = ((srcA & DstOpAnd) ^ DstOpXor) + DstOpAdd;          \
                                                               \
    srcF = MUL8_INT(pathA, srcF);                              \
    dstF = MUL8_INT(pathA, dstF) + (0xff - pathA);             \
                                                               \
    srcA = MUL8_INT(srcF, srcA);                               \
    dstA = MUL8_INT(dstF, dstA);                               \
                                                               \
    BLEND_VIS(rr, dstARGB, srcARGB, dstA, srcA);               \
}

/***************************************************************/

static void IntArgbToIntArgbAlphaMaskBlit_line(mlib_f32 *dst_ptr,
                                               mlib_f32 *src_ptr,
                                               mlib_u8  *pMask,
                                               mlib_s32 width,
                                               mlib_s32 *log_val,
                                               mlib_u8  *mul8_extra,
                                               mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, srcA0, srcA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0, srcARGB0, srcARGB1;
    mlib_s32 SrcOpAnd = log_val[0];
    mlib_s32 SrcOpXor = log_val[1];
    mlib_s32 SrcOpAdd = log_val[2];
    mlib_s32 DstOpAnd = log_val[3];
    mlib_s32 DstOpXor = log_val[4];
    mlib_s32 DstOpAdd = log_val[5];

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];
        if (pathA0) {
            dstA0 = *(mlib_u8*)(dst_ptr + i);
            srcA0 = *(mlib_u8*)(src_ptr + i);
            dstARGB0 = dst_ptr[i];
            srcARGB0 = src_ptr[i];
            MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
            dst_ptr[i] = vis_fpack16(res0);
            *(mlib_u8*)(dst_ptr + i) = dstA0;
        }

        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        pathA0 = pMask[i];
        pathA1 = pMask[i + 1];
        dstA0 = *(mlib_u8*)(dst_ptr + i);
        dstA1 = *(mlib_u8*)(dst_ptr + i + 1);
        dstARGB = *(mlib_d64*)(dst_ptr + i);
        srcA0 = *(mlib_u8*)(src_ptr + i);
        srcA1 = *(mlib_u8*)(src_ptr + i + 1);
        srcARGB0 = src_ptr[i];
        srcARGB1 = src_ptr[i + 1];

        MASK_FILL(res0, pathA0, dstA0, vis_read_hi(dstARGB), srcA0, srcARGB0);
        MASK_FILL(res1, pathA1, dstA1, vis_read_lo(dstARGB), srcA1, srcARGB1);

        res0 = vis_fpack16_pair(res0, res1);

        msk = (((-pathA0) & (1 << 11)) | ((-pathA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);

        *(mlib_u8*)(dst_ptr + i    ) = dstA0;
        *(mlib_u8*)(dst_ptr + i + 1) = dstA1;
    }

    if (i < width) {
        pathA0 = pMask[i];
        if (pathA0) {
            dstA0 = *(mlib_u8*)(dst_ptr + i);
            srcA0 = *(mlib_u8*)(src_ptr + i);
            dstARGB0 = dst_ptr[i];
            srcARGB0 = src_ptr[i];
            MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
            dst_ptr[i] = vis_fpack16(res0);
            *(mlib_u8*)(dst_ptr + i) = dstA0;
        }
    }
}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB, srcA, srcARGB)     \
{                                                              \
    mlib_s32 srcF, dstF;                                       \
                                                               \
    srcA = mul8_extra[srcA];                                   \
                                                               \
    srcF = ((dstA & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;          \
    dstF = ((srcA & DstOpAnd) ^ DstOpXor) + DstOpAdd;          \
                                                               \
    srcA = MUL8_INT(srcF, srcA);                               \
    dstA = MUL8_INT(dstF, dstA);                               \
                                                               \
    BLEND_VIS(rr, dstARGB, srcARGB, dstA, srcA);               \
}

/***************************************************************/

static void IntArgbToIntArgbAlphaMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                  mlib_f32 *src_ptr,
                                                  mlib_u8  *pMask,
                                                  mlib_s32 width,
                                                  mlib_s32 *log_val,
                                                  mlib_u8  *mul8_extra,
                                                  mlib_u8  *mul8_tbl)
{
    mlib_s32 i;
    mlib_s32 dstA0, srcA0;
    mlib_d64 res0;
    mlib_f32 dstARGB0, srcARGB0;
    mlib_s32 SrcOpAnd = log_val[0];
    mlib_s32 SrcOpXor = log_val[1];
    mlib_s32 SrcOpAdd = log_val[2];
    mlib_s32 DstOpAnd = log_val[3];
    mlib_s32 DstOpXor = log_val[4];
    mlib_s32 DstOpAdd = log_val[5];

#pragma pipeloop(0)
    for (i = 0; i < width; i++) {
        dstA0 = *(mlib_u8*)(dst_ptr + i);
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        dst_ptr[i] = vis_fpack16(res0);
        *(mlib_u8*)(dst_ptr + i) = dstA0;
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntArgbAlphaMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_s32 extraA;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 log_val[6];
    mlib_s32 j;
    mlib_s32 SrcOpAnd;
    mlib_s32 SrcOpXor;
    mlib_s32 SrcOpAdd;
    mlib_s32 DstOpAnd;
    mlib_s32 DstOpXor;
    mlib_s32 DstOpAdd;
    mlib_u8  *mul8_extra;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

    mul8_extra = mul8table[extraA];

    SrcOpAnd = (AlphaRules[pCompInfo->rule].srcOps).andval;
    SrcOpXor = (AlphaRules[pCompInfo->rule].srcOps).xorval;
    SrcOpAdd = (AlphaRules[pCompInfo->rule].srcOps).addval;
    SrcOpAdd -= SrcOpXor;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval;
    DstOpAdd -= DstOpXor;

    log_val[0] = SrcOpAnd;
    log_val[1] = SrcOpXor;
    log_val[2] = SrcOpAdd;
    log_val[3] = DstOpAnd;
    log_val[4] = DstOpXor;
    log_val[5] = DstOpAdd;

    vis_write_gsr(7 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        if (dstScan == 4*width && srcScan == dstScan && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntArgbToIntArgbAlphaMaskBlit_line(dstBase, srcBase, pMask,
                                               width, log_val, mul8_extra,
                                               (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        if (dstScan == 4*width && srcScan == dstScan) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntArgbToIntArgbAlphaMaskBlit_A1_line(dstBase, srcBase, pMask,
                                                  width, log_val, mul8_extra,
                                                  (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToFourByteAbgrAlphaMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_d64 buff[BUFF_SIZE/2];
    void     *src_buff = buff, *dst_buff;
    mlib_s32 extraA;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 log_val[6];
    mlib_s32 j;
    mlib_s32 SrcOpAnd;
    mlib_s32 SrcOpXor;
    mlib_s32 SrcOpAdd;
    mlib_s32 DstOpAnd;
    mlib_s32 DstOpXor;
    mlib_s32 DstOpAdd;
    mlib_u8  *mul8_extra;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

    mul8_extra = mul8table[extraA];

    SrcOpAnd = (AlphaRules[pCompInfo->rule].srcOps).andval;
    SrcOpXor = (AlphaRules[pCompInfo->rule].srcOps).xorval;
    SrcOpAdd = (AlphaRules[pCompInfo->rule].srcOps).addval;
    SrcOpAdd -= SrcOpXor;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval;
    DstOpAdd -= DstOpXor;

    log_val[0] = SrcOpAnd;
    log_val[1] = SrcOpXor;
    log_val[2] = SrcOpAdd;
    log_val[3] = DstOpAnd;
    log_val[4] = DstOpXor;
    log_val[5] = DstOpAdd;

    vis_write_gsr(7 << 3);

    if (2*width > BUFF_SIZE) src_buff = mlib_malloc(2*width*sizeof(mlib_s32));
    dst_buff = (mlib_s32*)src_buff + width;

    if (pMask != NULL) {
        pMask += maskOff;

        for (j = 0; j < height; j++) {
            IntArgbToIntAbgrConvert_line(srcBase, src_buff, width);
            if (!((mlib_s32)dstBase & 3)) {
                IntArgbToIntArgbAlphaMaskBlit_line(dstBase, src_buff, pMask,
                                                   width, log_val, mul8_extra,
                                                   (void*)mul8table);
            } else {
                mlib_ImageCopy_na(dstBase, dst_buff, width*sizeof(mlib_s32));
                IntArgbToIntArgbAlphaMaskBlit_line(dst_buff, src_buff, pMask,
                                                   width, log_val, mul8_extra,
                                                   (void*)mul8table);
                mlib_ImageCopy_na(dst_buff, dstBase, width*sizeof(mlib_s32));
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        for (j = 0; j < height; j++) {
            IntArgbToIntAbgrConvert_line(srcBase, src_buff, width);
            if (!((mlib_s32)dstBase & 3)) {
                IntArgbToIntArgbAlphaMaskBlit_A1_line(dstBase, src_buff,
                                                      pMask, width, log_val,
                                                      mul8_extra,
                                                      (void*)mul8table);
            } else {
                mlib_ImageCopy_na(dstBase, dst_buff, width*sizeof(mlib_s32));
                IntArgbToIntArgbAlphaMaskBlit_A1_line(dst_buff, src_buff,
                                                      pMask, width, log_val,
                                                      mul8_extra,
                                                      (void*)mul8table);
                mlib_ImageCopy_na(dst_buff, dstBase, width*sizeof(mlib_s32));
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }

    if (src_buff != buff) {
        mlib_free(src_buff);
    }
}

/***************************************************************/

/* ##############################################################
 * IntArgbToIntRgbAlphaMaskBlit()
 */

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB, srcA, srcARGB)     \
{                                                              \
    mlib_s32 srcF, dstF;                                       \
                                                               \
    srcA = mul8_extra[srcA];                                   \
    dstF = ((srcA & DstOpAnd) ^ DstOpXor) + DstOpAdd;          \
                                                               \
    srcF = mul8_srcF[pathA];                                   \
    dstA = MUL8_INT(dstF, pathA) + (0xff - pathA);             \
                                                               \
    pathA = dstA - 0xff - srcF;                                \
    /* (pathA == 0) if (dstA == 0xFF && srcF == 0) */          \
                                                               \
    srcA = MUL8_INT(srcA, srcF);                               \
                                                               \
    BLEND_VIS_RGB(rr, dstARGB, srcARGB, dstA, srcA);           \
}

/***************************************************************/

static void IntArgbToIntRgbAlphaMaskBlit_line(mlib_f32 *dst_ptr,
                                              mlib_f32 *src_ptr,
                                              mlib_u8  *pMask,
                                              mlib_s32 width,
                                              mlib_s32 *log_val,
                                              mlib_u8  *mul8_extra,
                                              mlib_u8  *mul8_srcF,
                                              mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, srcA0, srcA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0, srcARGB0, srcARGB1;
    mlib_s32 DstOpAnd = log_val[3];
    mlib_s32 DstOpXor = log_val[4];
    mlib_s32 DstOpAdd = log_val[5];

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (pathA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }

        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        pathA0 = pMask[i];
        pathA1 = pMask[i + 1];
        dstARGB = *(mlib_d64*)(dst_ptr + i);
        srcA0 = *(mlib_u8*)(src_ptr + i);
        srcA1 = *(mlib_u8*)(src_ptr + i + 1);
        srcARGB0 = src_ptr[i];
        srcARGB1 = src_ptr[i + 1];

        MASK_FILL(res0, pathA0, dstA0, vis_read_hi(dstARGB), srcA0, srcARGB0);
        MASK_FILL(res1, pathA1, dstA1, vis_read_lo(dstARGB), srcA1, srcARGB1);

        res0 = vis_fpack16_pair(res0, res1);

        msk = (((pathA0) & (1 << 11)) | ((pathA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);
    }

    if (i < width) {
        pathA0 = pMask[i];
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (pathA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB, srcA, srcARGB)     \
{                                                              \
    srcA = mul8_extra[srcA];                                   \
    dstA = ((srcA & DstOpAnd) ^ DstOpXor) + DstOpAdd;          \
                                                               \
    srcA = mul8_srcF[srcA];                                    \
                                                               \
    pathA = dstA - srcF_255;                                   \
    /* (pathA == 0) if (dstA == 0xFF && srcF == 0) */          \
                                                               \
    BLEND_VIS_RGB(rr, dstARGB, srcARGB, dstA, srcA);           \
}

/***************************************************************/

static void IntArgbToIntRgbAlphaMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                 mlib_f32 *src_ptr,
                                                 mlib_u8  *pMask,
                                                 mlib_s32 width,
                                                 mlib_s32 *log_val,
                                                 mlib_u8  *mul8_extra,
                                                 mlib_u8  *mul8_srcF,
                                                 mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, srcA0, srcA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0, srcARGB0, srcARGB1;
    mlib_s32 DstOpAnd = log_val[3];
    mlib_s32 DstOpXor = log_val[4];
    mlib_s32 DstOpAdd = log_val[5];
    mlib_s32 srcF_255 = mul8_srcF[0xff] + 0xff;

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (pathA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }

        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        dstARGB = *(mlib_d64*)(dst_ptr + i);
        srcA0 = *(mlib_u8*)(src_ptr + i);
        srcA1 = *(mlib_u8*)(src_ptr + i + 1);
        srcARGB0 = src_ptr[i];
        srcARGB1 = src_ptr[i + 1];

        MASK_FILL(res0, pathA0, dstA0, vis_read_hi(dstARGB), srcA0, srcARGB0);
        MASK_FILL(res1, pathA1, dstA1, vis_read_lo(dstARGB), srcA1, srcARGB1);

        res0 = vis_fpack16_pair(res0, res1);

        msk = (((pathA0) & (1 << 11)) | ((pathA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);
    }

    if (i < width) {
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (pathA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntRgbAlphaMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_s32 extraA, srcF;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 log_val[6];
    mlib_s32 j;
    mlib_s32 SrcOpAnd;
    mlib_s32 SrcOpXor;
    mlib_s32 SrcOpAdd;
    mlib_s32 DstOpAnd;
    mlib_s32 DstOpXor;
    mlib_s32 DstOpAdd;
    mlib_u8  *mul8_extra, *mul8_srcF;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

    mul8_extra = mul8table[extraA];

    SrcOpAnd = (AlphaRules[pCompInfo->rule].srcOps).andval;
    SrcOpXor = (AlphaRules[pCompInfo->rule].srcOps).xorval;
    SrcOpAdd = (AlphaRules[pCompInfo->rule].srcOps).addval;
    SrcOpAdd -= SrcOpXor;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval;
    DstOpAdd -= DstOpXor;

    log_val[3] = DstOpAnd;
    log_val[4] = DstOpXor;
    log_val[5] = DstOpAdd;

    srcF = ((0xff & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;

    mul8_srcF = mul8table[srcF];

    vis_write_gsr(7 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        if (dstScan == 4*width && srcScan == dstScan && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntArgbToIntRgbAlphaMaskBlit_line(dstBase, srcBase, pMask,
                                              width, log_val, mul8_extra,
                                              mul8_srcF, (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        if (dstScan == 4*width && srcScan == dstScan) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntArgbToIntRgbAlphaMaskBlit_A1_line(dstBase, srcBase, pMask,
                                                 width, log_val, mul8_extra,
                                                 mul8_srcF, (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}

/***************************************************************/

/* ##############################################################
 * IntRgbToIntArgbAlphaMaskBlit()
 */

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB, srcAX, srcARGB)    \
{                                                              \
    mlib_s32 pathAx256 = pathA << 8;                           \
    srcF = ((dstA & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;          \
                                                               \
    srcF = mul8_tbl[pathAx256 + srcF];                         \
    dstFX = mul8_tbl[pathAx256 + dstF] + (0xff - pathA);       \
                                                               \
    srcAX = mul8_tbl[srcF + srcAx256];                         \
    dstA = mul8_tbl[dstFX + (dstA << 8)];                      \
                                                               \
    BLEND_VIS(rr, dstARGB, srcARGB, dstA, srcAX);              \
}

/***************************************************************/

static void IntRgbToIntArgbAlphaMaskBlit_line(mlib_f32 *dst_ptr,
                                               mlib_f32 *src_ptr,
                                               mlib_u8  *pMask,
                                               mlib_s32 width,
                                               mlib_s32 *log_val,
                                               mlib_u8  *mul8_extra,
                                               mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, srcA, srcA0, srcA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0, srcARGB0, srcARGB1;
    mlib_s32 SrcOpAnd = log_val[0];
    mlib_s32 SrcOpXor = log_val[1];
    mlib_s32 SrcOpAdd = log_val[2];
    mlib_s32 DstOpAnd = log_val[3];
    mlib_s32 DstOpXor = log_val[4];
    mlib_s32 DstOpAdd = log_val[5];
    mlib_s32 srcF, dstF, dstFX, srcAx256;

    i = i0 = 0;

    srcA = 0xFF;
    srcA = mul8_extra[srcA];
    srcAx256 = srcA << 8;
    dstF = ((srcA & DstOpAnd) ^ DstOpXor) + DstOpAdd;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];
        if (pathA0) {
            dstA0 = *(mlib_u8*)(dst_ptr + i);
            dstARGB0 = dst_ptr[i];
            srcARGB0 = src_ptr[i];
            MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
            dst_ptr[i] = vis_fpack16(res0);
            *(mlib_u8*)(dst_ptr + i) = dstA0;
        }
        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        pathA0 = pMask[i];
        pathA1 = pMask[i + 1];
        dstA0 = *(mlib_u8*)(dst_ptr + i);
        dstA1 = *(mlib_u8*)(dst_ptr + i + 1);
        dstARGB = *(mlib_d64*)(dst_ptr + i);
        srcARGB0 = src_ptr[i];
        srcARGB1 = src_ptr[i + 1];

        MASK_FILL(res0, pathA0, dstA0, vis_read_hi(dstARGB), srcA0, srcARGB0);
        MASK_FILL(res1, pathA1, dstA1, vis_read_lo(dstARGB), srcA1, srcARGB1);

        res0 = vis_fpack16_pair(res0, res1);

        msk = (((-pathA0) & (1 << 11)) | ((-pathA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);

        *(mlib_u8*)(dst_ptr + i    ) = dstA0;
        *(mlib_u8*)(dst_ptr + i + 1) = dstA1;
    }

    if (i < width) {
        pathA0 = pMask[i];
        if (pathA0) {
            dstA0 = *(mlib_u8*)(dst_ptr + i);
            dstARGB0 = dst_ptr[i];
            srcARGB0 = src_ptr[i];
            MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
            dst_ptr[i] = vis_fpack16(res0);
            *(mlib_u8*)(dst_ptr + i) = dstA0;
        }
    }
}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB, srcA, srcARGB)     \
{                                                              \
    srcF = ((dstA & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;          \
                                                               \
    srcA = mul8_tbl[srcF + srcAx256];                          \
    dstA = mul8_tbl[dstF + (dstA << 8)];                       \
                                                               \
    BLEND_VIS(rr, dstARGB, srcARGB, dstA, srcA);               \
}

/***************************************************************/

static void IntRgbToIntArgbAlphaMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                  mlib_f32 *src_ptr,
                                                  mlib_u8  *pMask,
                                                  mlib_s32 width,
                                                  mlib_s32 *log_val,
                                                  mlib_u8  *mul8_extra,
                                                  mlib_u8  *mul8_tbl)
{
    mlib_s32 i;
    mlib_s32 dstA0, srcA, srcA0;
    mlib_d64 res0;
    mlib_f32 dstARGB0, srcARGB0;
    mlib_s32 SrcOpAnd = log_val[0];
    mlib_s32 SrcOpXor = log_val[1];
    mlib_s32 SrcOpAdd = log_val[2];
    mlib_s32 DstOpAnd = log_val[3];
    mlib_s32 DstOpXor = log_val[4];
    mlib_s32 DstOpAdd = log_val[5];
    mlib_s32 srcF, dstF, srcAx256;

    srcA = 0xFF;
    srcA = mul8_extra[srcA];
    srcAx256 = srcA << 8;
    dstF = ((srcA & DstOpAnd) ^ DstOpXor) + DstOpAdd;

#pragma pipeloop(0)
    for (i = 0; i < width; i++) {
        dstA0 = *(mlib_u8*)(dst_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        dst_ptr[i] = vis_fpack16(res0);
        *(mlib_u8*)(dst_ptr + i) = dstA0;
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbToIntArgbAlphaMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_s32 extraA;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 log_val[6];
    mlib_s32 j;
    mlib_s32 SrcOpAnd;
    mlib_s32 SrcOpXor;
    mlib_s32 SrcOpAdd;
    mlib_s32 DstOpAnd;
    mlib_s32 DstOpXor;
    mlib_s32 DstOpAdd;
    mlib_u8  *mul8_extra;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

    mul8_extra = mul8table[extraA];

    SrcOpAnd = (AlphaRules[pCompInfo->rule].srcOps).andval;
    SrcOpXor = (AlphaRules[pCompInfo->rule].srcOps).xorval;
    SrcOpAdd = (AlphaRules[pCompInfo->rule].srcOps).addval;
    SrcOpAdd -= SrcOpXor;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval;
    DstOpAdd -= DstOpXor;

    log_val[0] = SrcOpAnd;
    log_val[1] = SrcOpXor;
    log_val[2] = SrcOpAdd;
    log_val[3] = DstOpAnd;
    log_val[4] = DstOpXor;
    log_val[5] = DstOpAdd;

    vis_write_gsr(7 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        if (dstScan == 4*width && srcScan == dstScan && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntRgbToIntArgbAlphaMaskBlit_line(dstBase, srcBase, pMask,
                                               width, log_val, mul8_extra,
                                               (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        if (dstScan == 4*width && srcScan == dstScan) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntRgbToIntArgbAlphaMaskBlit_A1_line(dstBase, srcBase, pMask,
                                                  width, log_val, mul8_extra,
                                                  (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}


/***************************************************************/

void ADD_SUFF(IntRgbToFourByteAbgrAlphaMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_d64 buff[BUFF_SIZE/2];
    void     *src_buff = buff, *dst_buff;
    mlib_s32 extraA;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 log_val[6];
    mlib_s32 j;
    mlib_s32 SrcOpAnd;
    mlib_s32 SrcOpXor;
    mlib_s32 SrcOpAdd;
    mlib_s32 DstOpAnd;
    mlib_s32 DstOpXor;
    mlib_s32 DstOpAdd;
    mlib_u8  *mul8_extra;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

    mul8_extra = mul8table[extraA];

    SrcOpAnd = (AlphaRules[pCompInfo->rule].srcOps).andval;
    SrcOpXor = (AlphaRules[pCompInfo->rule].srcOps).xorval;
    SrcOpAdd = (AlphaRules[pCompInfo->rule].srcOps).addval;
    SrcOpAdd -= SrcOpXor;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval;
    DstOpAdd -= DstOpXor;

    log_val[0] = SrcOpAnd;
    log_val[1] = SrcOpXor;
    log_val[2] = SrcOpAdd;
    log_val[3] = DstOpAnd;
    log_val[4] = DstOpXor;
    log_val[5] = DstOpAdd;

    vis_write_gsr(7 << 3);

    if (2*width > BUFF_SIZE) src_buff = mlib_malloc(2*width*sizeof(mlib_s32));
    dst_buff = (mlib_s32*)src_buff + width;

    if (pMask != NULL) {
        pMask += maskOff;

        for (j = 0; j < height; j++) {
            IntArgbToIntAbgrConvert_line(srcBase, src_buff, width);
            if (!((mlib_s32)dstBase & 3)) {
                IntRgbToIntArgbAlphaMaskBlit_line(dstBase, src_buff, pMask,
                                                  width, log_val, mul8_extra,
                                                  (void*)mul8table);
            } else {
                mlib_ImageCopy_na(dstBase, dst_buff, width*sizeof(mlib_s32));
                IntRgbToIntArgbAlphaMaskBlit_line(dst_buff, src_buff, pMask,
                                                  width, log_val, mul8_extra,
                                                  (void*)mul8table);
                mlib_ImageCopy_na(dst_buff, dstBase, width*sizeof(mlib_s32));
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        for (j = 0; j < height; j++) {
            IntArgbToIntAbgrConvert_line(srcBase, src_buff, width);
            if (!((mlib_s32)dstBase & 3)) {
                IntRgbToIntArgbAlphaMaskBlit_A1_line(dstBase, src_buff, pMask,
                                                     width, log_val,
                                                     mul8_extra,
                                                     (void*)mul8table);
            } else {
                mlib_ImageCopy_na(dstBase, dst_buff, width*sizeof(mlib_s32));
                IntRgbToIntArgbAlphaMaskBlit_A1_line(dst_buff, src_buff, pMask,
                                                     width, log_val,
                                                     mul8_extra,
                                                     (void*)mul8table);
                mlib_ImageCopy_na(dst_buff, dstBase, width*sizeof(mlib_s32));
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }

    if (src_buff != buff) {
        mlib_free(src_buff);
    }
}

/***************************************************************/

/* ##############################################################
 * IntArgbToIntBgrAlphaMaskBlit()
 */

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB, srcA, srcARGB)     \
    srcA = mul8_extra[srcA];                                   \
    dstF = ((srcA & DstOpAnd) ^ DstOpXor) + DstOpAdd;          \
                                                               \
    srcF = mul8_srcF[pathA];                                   \
    dstA = mul8_tbl[(pathA << 8) + dstF] + (0xff - pathA);     \
                                                               \
    pathA = dstA - 0xff - srcF;                                \
    /* (pathA == 0) if (dstA == 0xFF && srcF == 0) */          \
                                                               \
    srcA = MUL8_INT(srcA, srcF);                               \
                                                               \
    BLEND_VIS_RGB(rr, dstARGB, srcARGB, dstA, srcA)

/***************************************************************/

static void IntArgbToIntBgrAlphaMaskBlit_line(mlib_f32 *dst_ptr,
                                              mlib_f32 *src_ptr,
                                              mlib_u8  *pMask,
                                              mlib_s32 width,
                                              mlib_s32 *log_val,
                                              mlib_u8  *mul8_extra,
                                              mlib_u8  *mul8_srcF,
                                              mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, srcA0, srcA1, msk;
    mlib_d64 res0, res1, dstARGB, srcARGB;
    mlib_f32 dstARGB0, srcARGB0;
    mlib_s32 DstOpAnd = log_val[3];
    mlib_s32 DstOpXor = log_val[4];
    mlib_s32 DstOpAdd = log_val[5];
    mlib_s32 srcF, dstF;

#if VIS >= 0x200
    vis_write_bmask(0x03214765, 0);
#endif

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        ARGB2ABGR_FL(srcARGB0)
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (pathA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }

        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        pathA0 = pMask[i];
        pathA1 = pMask[i + 1];
        dstARGB = *(mlib_d64*)(dst_ptr + i);
        srcA0 = *(mlib_u8*)(src_ptr + i);
        srcA1 = *(mlib_u8*)(src_ptr + i + 1);
        srcARGB = vis_freg_pair(src_ptr[i], src_ptr[i + 1]);
        ARGB2ABGR_DB(srcARGB)

        MASK_FILL(res0, pathA0, dstA0, vis_read_hi(dstARGB),
                                srcA0, vis_read_hi(srcARGB));
        MASK_FILL(res1, pathA1, dstA1, vis_read_lo(dstARGB),
                                srcA1, vis_read_lo(srcARGB));

        res0 = vis_fpack16_pair(res0, res1);

        msk = (((pathA0) & (1 << 11)) | ((pathA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);
    }

    if (i < width) {
        pathA0 = pMask[i];
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        ARGB2ABGR_FL(srcARGB0)
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (pathA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB, srcA, srcARGB)     \
    srcA = mul8_extra[srcA];                                   \
    dstA = ((srcA & DstOpAnd) ^ DstOpXor) + DstOpAdd;          \
                                                               \
    srcA = mul8_srcF[srcA];                                    \
                                                               \
    pathA = dstA - srcF_255;                                   \
    /* (pathA == 0) if (dstA == 0xFF && srcF == 0) */          \
                                                               \
    BLEND_VIS(rr, dstARGB, srcARGB, dstA, srcA)

/***************************************************************/

static void IntArgbToIntBgrAlphaMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                 mlib_f32 *src_ptr,
                                                 mlib_u8  *pMask,
                                                 mlib_s32 width,
                                                 mlib_s32 *log_val,
                                                 mlib_u8  *mul8_extra,
                                                 mlib_u8  *mul8_srcF,
                                                 mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, srcA0, srcA1, msk;
    mlib_d64 res0, res1, dstARGB, srcARGB;
    mlib_f32 dstARGB0, srcARGB0;
    mlib_s32 DstOpAnd = log_val[3];
    mlib_s32 DstOpXor = log_val[4];
    mlib_s32 DstOpAdd = log_val[5];
    mlib_s32 srcF_255 = mul8_srcF[0xff] + 0xff;

#if VIS >= 0x200
    vis_write_bmask(0x03214765, 0);
#endif

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        ARGB2ABGR_FL(srcARGB0)
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (pathA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }

        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        dstARGB = *(mlib_d64*)(dst_ptr + i);
        srcA0 = *(mlib_u8*)(src_ptr + i);
        srcA1 = *(mlib_u8*)(src_ptr + i + 1);
        srcARGB = vis_freg_pair(src_ptr[i], src_ptr[i + 1]);
        ARGB2ABGR_DB(srcARGB)

        MASK_FILL(res0, pathA0, dstA0, vis_read_hi(dstARGB),
                                srcA0, vis_read_hi(srcARGB));
        MASK_FILL(res1, pathA1, dstA1, vis_read_lo(dstARGB),
                                srcA1, vis_read_lo(srcARGB));

        res0 = vis_fpack16_pair(res0, res1);

        msk = (((pathA0) & (1 << 11)) | ((pathA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);
    }

    if (i < width) {
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        ARGB2ABGR_FL(srcARGB0)
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (pathA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntBgrAlphaMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_s32 extraA, srcF;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 log_val[6];
    mlib_s32 j;
    mlib_s32 SrcOpAnd;
    mlib_s32 SrcOpXor;
    mlib_s32 SrcOpAdd;
    mlib_s32 DstOpAnd;
    mlib_s32 DstOpXor;
    mlib_s32 DstOpAdd;
    mlib_u8  *mul8_extra, *mul8_srcF;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

    mul8_extra = mul8table[extraA];

    SrcOpAnd = (AlphaRules[pCompInfo->rule].srcOps).andval;
    SrcOpXor = (AlphaRules[pCompInfo->rule].srcOps).xorval;
    SrcOpAdd = (AlphaRules[pCompInfo->rule].srcOps).addval;
    SrcOpAdd -= SrcOpXor;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval;
    DstOpAdd -= DstOpXor;

    log_val[3] = DstOpAnd;
    log_val[4] = DstOpXor;
    log_val[5] = DstOpAdd;

    srcF = ((0xff & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;

    mul8_srcF = mul8table[srcF];

    vis_write_gsr(7 << 3);

    if (pMask != NULL) {
        if (dstScan == 4*width && srcScan == dstScan && maskScan == width) {
            width *= height;
            height = 1;
        }

        pMask += maskOff;

        for (j = 0; j < height; j++) {
            IntArgbToIntBgrAlphaMaskBlit_line(dstBase, srcBase, pMask,
                                              width, log_val, mul8_extra,
                                              mul8_srcF, (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        if (dstScan == 4*width && srcScan == dstScan) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntArgbToIntBgrAlphaMaskBlit_A1_line(dstBase, srcBase, pMask,
                                                 width, log_val, mul8_extra,
                                                 mul8_srcF, (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}

/***************************************************************/

/* ##############################################################
 * IntRgbToIntRgbAlphaMaskBlit()
 * IntRgbToIntBgrAlphaMaskBlit()
 * IntBgrToIntBgrAlphaMaskBlit()
 */

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB, srcAX, srcARGB)    \
    srcF = mul8_srcF[pathA];                                   \
    dstA = mul8_tbl[(pathA << 8) + dstF] + (0xff - pathA);     \
    pathA = dstA - 0xff - srcF;                                \
    srcAX = mul8_tbl[srcA + (srcF << 8)];                      \
                                                               \
    BLEND_VIS_RGB(rr, dstARGB, srcARGB, dstA, srcAX)

/***************************************************************/

static void IntRgbToIntRgbAlphaMaskBlit_line(mlib_f32 *dst_ptr,
                                              mlib_f32 *src_ptr,
                                              mlib_u8  *pMask,
                                              mlib_s32 width,
                                              mlib_s32 *log_val,
                                              mlib_u8  *mul8_extra,
                                              mlib_u8  *mul8_srcF,
                                              mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, srcA, srcA0, srcA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0, srcARGB0, srcARGB1;
    mlib_s32 DstOpAnd = log_val[3];
    mlib_s32 DstOpXor = log_val[4];
    mlib_s32 DstOpAdd = log_val[5];
    mlib_s32 srcF, dstF;

    i = i0 = 0;

    srcA = 0xFF;
    srcA = mul8_extra[srcA];
    dstF = ((srcA & DstOpAnd) ^ DstOpXor) + DstOpAdd;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (pathA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }

        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        pathA0 = pMask[i];
        pathA1 = pMask[i + 1];
        dstARGB = *(mlib_d64*)(dst_ptr + i);
        srcARGB0 = src_ptr[i];
        srcARGB1 = src_ptr[i + 1];

        MASK_FILL(res0, pathA0, dstA0, vis_read_hi(dstARGB), srcA0, srcARGB0);
        MASK_FILL(res1, pathA1, dstA1, vis_read_lo(dstARGB), srcA1, srcARGB1);

        res0 = vis_fpack16_pair(res0, res1);

        msk = (((pathA0) & (1 << 11)) | ((pathA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);
    }

    if (i < width) {
        pathA0 = pMask[i];
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (pathA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

static void IntRgbToIntBgrAlphaMaskBlit_line(mlib_f32 *dst_ptr,
                                              mlib_f32 *src_ptr,
                                              mlib_u8  *pMask,
                                              mlib_s32 width,
                                              mlib_s32 *log_val,
                                              mlib_u8  *mul8_extra,
                                              mlib_u8  *mul8_srcF,
                                              mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, srcA, srcA0, srcA1, msk;
    mlib_d64 res0, res1, dstARGB, srcARGB;
    mlib_f32 dstARGB0, srcARGB0;
    mlib_s32 DstOpAnd = log_val[3];
    mlib_s32 DstOpXor = log_val[4];
    mlib_s32 DstOpAdd = log_val[5];
    mlib_s32 srcF, dstF;

#if VIS >= 0x200
    vis_write_bmask(0x03214765, 0);
#endif

    i = i0 = 0;

    srcA = 0xFF;
    srcA = mul8_extra[srcA];
    dstF = ((srcA & DstOpAnd) ^ DstOpXor) + DstOpAdd;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        ARGB2ABGR_FL(srcARGB0)
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (pathA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }
        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        pathA0 = pMask[i];
        pathA1 = pMask[i + 1];
        dstARGB = *(mlib_d64*)(dst_ptr + i);
        srcARGB = vis_freg_pair(src_ptr[i], src_ptr[i + 1]);
        ARGB2ABGR_DB(srcARGB)

        MASK_FILL(res0, pathA0, dstA0, vis_read_hi(dstARGB),
                                srcA0, vis_read_hi(srcARGB));
        MASK_FILL(res1, pathA1, dstA1, vis_read_lo(dstARGB),
                                srcA1, vis_read_lo(srcARGB));

        res0 = vis_fpack16_pair(res0, res1);

        msk = (((pathA0) & (1 << 11)) | ((pathA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);
    }

    if (i < width) {
        pathA0 = pMask[i];
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        ARGB2ABGR_FL(srcARGB0)
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (pathA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, dstARGB, srcARGB)                \
    t0 = vis_fmul8x16al(srcARGB, srcA_mul);            \
    t1 = vis_fmul8x16al(dstARGB, dstA_mul);            \
    rr = vis_fpadd16(t0, t1);                          \
    rr = vis_fpadd16(vis_fmul8sux16(rr, dstA_div),     \
                     vis_fmul8ulx16(rr, dstA_div))

/***************************************************************/

static void IntRgbToIntRgbAlphaMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                 mlib_f32 *src_ptr,
                                                 mlib_u8  *pMask,
                                                 mlib_s32 width,
                                                 mlib_s32 *log_val,
                                                 mlib_u8  *mul8_extra,
                                                 mlib_u8  *mul8_srcF,
                                                 mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA, dstA, srcA, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0, srcARGB0, srcARGB1, srcA_mul, dstA_mul;
    mlib_s32 DstOpAnd = log_val[3];
    mlib_s32 DstOpXor = log_val[4];
    mlib_s32 DstOpAdd = log_val[5];
    mlib_s32 srcF_255 = mul8_srcF[0xff] + 0xff;
    mlib_d64 t0, t1, dstA_div;

    i = i0 = 0;

    srcA = 0xFF;
    srcA = mul8_extra[srcA];
    dstA = ((srcA & DstOpAnd) ^ DstOpXor) + DstOpAdd;
    srcA = mul8_srcF[srcA];
    pathA = dstA - srcF_255;
    srcA_mul = ((mlib_f32*)vis_mul8s_tbl)[srcA];
    dstA_mul = ((mlib_f32*)vis_mul8s_tbl)[dstA];
    dstA += srcA;
    dstA_div = ((mlib_d64*)vis_div8_tbl)[dstA];

    if ((mlib_s32)dst_ptr & 7) {
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, dstARGB0, srcARGB0);
        if (pathA) {
            dst_ptr[i] = vis_fpack16(res0);
        }
        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        dstARGB = *(mlib_d64*)(dst_ptr + i);
        srcARGB0 = src_ptr[i];
        srcARGB1 = src_ptr[i + 1];

        MASK_FILL(res0, vis_read_hi(dstARGB), srcARGB0);
        MASK_FILL(res1, vis_read_lo(dstARGB), srcARGB1);

        res0 = vis_fpack16_pair(res0, res1);

        msk = (((pathA) & (1 << 11)) | ((pathA) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);
    }

    if (i < width) {
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, dstARGB0, srcARGB0);
        if (pathA) {
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

static void IntRgbToIntBgrAlphaMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                 mlib_f32 *src_ptr,
                                                 mlib_u8  *pMask,
                                                 mlib_s32 width,
                                                 mlib_s32 *log_val,
                                                 mlib_u8  *mul8_extra,
                                                 mlib_u8  *mul8_srcF,
                                                 mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA, dstA, srcA, msk;
    mlib_d64 res0, res1, dstARGB, srcARGB;
    mlib_f32 dstARGB0, srcARGB0, srcA_mul, dstA_mul;
    mlib_s32 DstOpAnd = log_val[3];
    mlib_s32 DstOpXor = log_val[4];
    mlib_s32 DstOpAdd = log_val[5];
    mlib_s32 srcF_255 = mul8_srcF[0xff] + 0xff;
    mlib_d64 t0, t1, dstA_div;

#if VIS >= 0x200
    vis_write_bmask(0x03214765, 0);
#endif

    i = i0 = 0;

    srcA = 0xFF;
    srcA = mul8_extra[srcA];
    dstA = ((srcA & DstOpAnd) ^ DstOpXor) + DstOpAdd;
    srcA = mul8_srcF[srcA];
    pathA = dstA - srcF_255;
    srcA_mul = ((mlib_f32*)vis_mul8s_tbl)[srcA];
    dstA_mul = ((mlib_f32*)vis_mul8s_tbl)[dstA];
    dstA += srcA;
    dstA_div = ((mlib_d64*)vis_div8_tbl)[dstA];

    if ((mlib_s32)dst_ptr & 7) {
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        ARGB2ABGR_FL(srcARGB0)
        MASK_FILL(res0, dstARGB0, srcARGB0);
        if (pathA) {
            dst_ptr[i] = vis_fpack16(res0);
        }
        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        dstARGB = *(mlib_d64*)(dst_ptr + i);
        srcARGB = vis_freg_pair(src_ptr[i], src_ptr[i + 1]);
        ARGB2ABGR_DB(srcARGB)

        MASK_FILL(res0, vis_read_hi(dstARGB), vis_read_hi(srcARGB));
        MASK_FILL(res1, vis_read_lo(dstARGB), vis_read_lo(srcARGB));

        res0 = vis_fpack16_pair(res0, res1);

        msk = (((pathA) & (1 << 11)) | ((pathA) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);
    }

    if (i < width) {
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        ARGB2ABGR_FL(srcARGB0)
        MASK_FILL(res0, dstARGB0, srcARGB0);
        if (pathA) {
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbToIntRgbAlphaMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_s32 extraA, srcF;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 log_val[6];
    mlib_s32 j;
    mlib_s32 SrcOpAnd;
    mlib_s32 SrcOpXor;
    mlib_s32 SrcOpAdd;
    mlib_s32 DstOpAnd;
    mlib_s32 DstOpXor;
    mlib_s32 DstOpAdd;
    mlib_u8  *mul8_extra, *mul8_srcF;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

    mul8_extra = mul8table[extraA];

    SrcOpAnd = (AlphaRules[pCompInfo->rule].srcOps).andval;
    SrcOpXor = (AlphaRules[pCompInfo->rule].srcOps).xorval;
    SrcOpAdd = (AlphaRules[pCompInfo->rule].srcOps).addval;
    SrcOpAdd -= SrcOpXor;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval;
    DstOpAdd -= DstOpXor;

    log_val[3] = DstOpAnd;
    log_val[4] = DstOpXor;
    log_val[5] = DstOpAdd;

    srcF = ((0xff & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;

    mul8_srcF = mul8table[srcF];

    vis_write_gsr(7 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        if (dstScan == 4*width && srcScan == dstScan && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntRgbToIntRgbAlphaMaskBlit_line(dstBase, srcBase, pMask,
                                              width, log_val, mul8_extra,
                                              mul8_srcF, (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        if (dstScan == 4*width && srcScan == dstScan) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntRgbToIntRgbAlphaMaskBlit_A1_line(dstBase, srcBase, pMask,
                                                 width, log_val, mul8_extra,
                                                 mul8_srcF, (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbToIntBgrAlphaMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_s32 extraA, srcF;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 log_val[6];
    mlib_s32 j;
    mlib_s32 SrcOpAnd;
    mlib_s32 SrcOpXor;
    mlib_s32 SrcOpAdd;
    mlib_s32 DstOpAnd;
    mlib_s32 DstOpXor;
    mlib_s32 DstOpAdd;
    mlib_u8  *mul8_extra, *mul8_srcF;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

    mul8_extra = mul8table[extraA];

    SrcOpAnd = (AlphaRules[pCompInfo->rule].srcOps).andval;
    SrcOpXor = (AlphaRules[pCompInfo->rule].srcOps).xorval;
    SrcOpAdd = (AlphaRules[pCompInfo->rule].srcOps).addval;
    SrcOpAdd -= SrcOpXor;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval;
    DstOpAdd -= DstOpXor;

    log_val[3] = DstOpAnd;
    log_val[4] = DstOpXor;
    log_val[5] = DstOpAdd;

    srcF = ((0xff & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;

    mul8_srcF = mul8table[srcF];

    vis_write_gsr(7 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        if (dstScan == 4*width && srcScan == dstScan && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntRgbToIntBgrAlphaMaskBlit_line(dstBase, srcBase, pMask,
                                              width, log_val, mul8_extra,
                                              mul8_srcF, (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        if (dstScan == 4*width && srcScan == dstScan) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntRgbToIntBgrAlphaMaskBlit_A1_line(dstBase, srcBase, pMask,
                                                 width, log_val, mul8_extra,
                                                 mul8_srcF, (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}

/***************************************************************/

#ifdef MLIB_ADD_SUFF
#pragma weak IntBgrToIntBgrAlphaMaskBlit_F = IntRgbToIntRgbAlphaMaskBlit_F
#else
#pragma weak IntBgrToIntBgrAlphaMaskBlit   = IntRgbToIntRgbAlphaMaskBlit
#endif

/***************************************************************/

/*
    mlib_d64 buff[BUFF_SIZE/2];
    void     *pbuff = buff;

    if (width > BUFF_SIZE) pbuff = mlib_malloc(width*sizeof(mlib_s32));

        ADD_SUFF(ThreeByteBgrToIntArgbConvert)(rasBase, pbuff, width, 1,
                                               pRasInfo, pRasInfo,
                                               pPrim, pCompInfo);

        ADD_SUFF(IntArgbToThreeByteBgrConvert)(pbuff, rasBase, width, 1,
                                               pRasInfo, pRasInfo,
                                               pPrim, pCompInfo);


    if (pbuff != buff) {
        mlib_free(pbuff);
    }
*/

#endif
