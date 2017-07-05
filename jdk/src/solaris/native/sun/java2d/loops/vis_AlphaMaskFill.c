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

#include "vis_AlphaMacros.h"

/***************************************************************/

/* ##############################################################
 * IntArgbAlphaMaskFill()
 * FourByteAbgrAlphaMaskFill()
 */

#define MASK_FILL(rr, pathA, dstA, dstARGB)                    \
{                                                              \
    mlib_d64 t0, t1;                                           \
    mlib_s32 srcF, dstF, srcA;                                 \
                                                               \
    srcF = ((dstA & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;          \
                                                               \
    srcF = MUL8_INT(srcF, pathA);                              \
    dstF = mul8_dstF[pathA] + (0xff - pathA);                  \
                                                               \
    srcA = mul8_cnstA[srcF];                                   \
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

static void IntArgbAlphaMaskFill_line(mlib_f32 *dst_ptr,
                                      mlib_u8  *pMask,
                                      mlib_s32 width,
                                      mlib_f32 cnstARGB0,
                                      mlib_s32 *log_val,
                                      mlib_u8  *mul8_cnstA,
                                      mlib_u8  *mul8_dstF,
                                      mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;
    mlib_s32 SrcOpAnd, SrcOpXor, SrcOpAdd;

    SrcOpAnd = log_val[0];
    SrcOpXor = log_val[1];
    SrcOpAdd = log_val[2];

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];

        if (pathA0) {
            dstA0 = *(mlib_u8*)(dst_ptr + i);
            dstARGB0 = dst_ptr[i];
            MASK_FILL(res0, pathA0, dstA0, dstARGB0);
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

        MASK_FILL(res0, pathA0, dstA0, vis_read_hi(dstARGB));
        MASK_FILL(res1, pathA1, dstA1, vis_read_lo(dstARGB));

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
            MASK_FILL(res0, pathA0, dstA0, dstARGB0);
            dst_ptr[i] = vis_fpack16(res0);
            *(mlib_u8*)(dst_ptr + i) = dstA0;
        }
    }
}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB)                    \
{                                                              \
    mlib_d64 t0, t1;                                           \
    mlib_s32 srcA, alp1;                                       \
                                                               \
    srcA = ((dstA & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;          \
    alp1 = mul8_dstF[dstA];                                    \
    dstA = mul8_cnstA[srcA] + alp1;                            \
                                                               \
    t0 = MUL8_VIS(cnstARGB0, srcA);                            \
    t1 = MUL8_VIS(dstARGB, alp1);                              \
    rr = vis_fpadd16(t0, t1);                                  \
                                                               \
    DIV_ALPHA(rr, dstA);                                       \
}

/***************************************************************/

static void IntArgbAlphaMaskFill_A1_line(mlib_f32 *dst_ptr,
                                         mlib_u8  *pMask,
                                         mlib_s32 width,
                                         mlib_f32 cnstARGB0,
                                         mlib_s32 *log_val,
                                         mlib_u8  *mul8_cnstA,
                                         mlib_u8  *mul8_dstF,
                                         mlib_u8  *mul8_tbl)
{
    mlib_s32 i;
    mlib_s32 dstA0;
    mlib_d64 res0;
    mlib_f32 dstARGB0;
    mlib_s32 SrcOpAnd, SrcOpXor, SrcOpAdd;

    SrcOpAnd = log_val[0];
    SrcOpXor = log_val[1];
    SrcOpAdd = log_val[2];

#pragma pipeloop(0)
    for (i = 0; i < width; i++) {
        dstA0 = *(mlib_u8*)(dst_ptr + i);
        dstARGB0 = dst_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0);
        dst_ptr[i] = vis_fpack16(res0);
        *(mlib_u8*)(dst_ptr + i) = dstA0;
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbAlphaMaskFill)(void *rasBase,
                                    jubyte *pMask,
                                    jint maskOff,
                                    jint maskScan,
                                    jint width,
                                    jint height,
                                    jint fgColor,
                                    SurfaceDataRasInfo *pRasInfo,
                                    NativePrimitive *pPrim,
                                    CompositeInfo *pCompInfo)
{
    mlib_s32 cnstA, cnstR, cnstG, cnstB;
    mlib_s32 rasScan = pRasInfo->scanStride;
    mlib_f32 cnstARGB0;
    mlib_u8  *mul8_cnstA, *mul8_dstF;
    mlib_s32 SrcOpAnd, SrcOpXor, SrcOpAdd;
    mlib_s32 DstOpAnd, DstOpXor, DstOpAdd;
    mlib_s32 dstF;
    mlib_s32 log_val[3];
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

    log_val[0] = SrcOpAnd;
    log_val[1] = SrcOpXor;
    log_val[2] = SrcOpAdd;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval;
    DstOpAdd -= DstOpXor;

    dstF = (((cnstA) & DstOpAnd) ^ DstOpXor) + DstOpAdd;

    mul8_cnstA = mul8table[cnstA];
    mul8_dstF = mul8table[dstF];

    vis_write_gsr(7 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        if (rasScan == 4*width && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntArgbAlphaMaskFill_line(rasBase, pMask, width, cnstARGB0,
                                      log_val, mul8_cnstA, mul8_dstF,
                                      (void*)mul8table);

            PTR_ADD(rasBase, rasScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        if (rasScan == 4*width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntArgbAlphaMaskFill_A1_line(rasBase, pMask, width, cnstARGB0,
                                         log_val, mul8_cnstA, mul8_dstF,
                                         (void*)mul8table);

            PTR_ADD(rasBase, rasScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(FourByteAbgrAlphaMaskFill)(void *rasBase,
                                         jubyte *pMask,
                                         jint maskOff,
                                         jint maskScan,
                                         jint width,
                                         jint height,
                                         jint fgColor,
                                         SurfaceDataRasInfo *pRasInfo,
                                         NativePrimitive *pPrim,
                                         CompositeInfo *pCompInfo)
{
    mlib_d64 buff[BUFF_SIZE/2];
    void     *pbuff = buff;
    mlib_s32 cnstA, cnstR, cnstG, cnstB;
    mlib_s32 rasScan = pRasInfo->scanStride;
    mlib_f32 cnstARGB0;
    mlib_u8  *mul8_cnstA, *mul8_dstF;
    mlib_s32 SrcOpAnd, SrcOpXor, SrcOpAdd;
    mlib_s32 DstOpAnd, DstOpXor, DstOpAdd;
    mlib_s32 dstF;
    mlib_s32 log_val[3];
    mlib_s32 j;

    cnstA = (mlib_u32)fgColor >> 24;
    cnstR = (fgColor >> 16) & 0xff;
    cnstG = (fgColor >>  8) & 0xff;
    cnstB = (fgColor      ) & 0xff;

    if (cnstA != 0xff) {
        cnstR = mul8table[cnstA][cnstR];
        cnstG = mul8table[cnstA][cnstG];
        cnstB = mul8table[cnstA][cnstB];
    }

    cnstARGB0 = F32_FROM_U8x4(cnstA, cnstB, cnstG, cnstR);

    SrcOpAnd = (AlphaRules[pCompInfo->rule].srcOps).andval;
    SrcOpXor = (AlphaRules[pCompInfo->rule].srcOps).xorval;
    SrcOpAdd = (AlphaRules[pCompInfo->rule].srcOps).addval;
    SrcOpAdd -= SrcOpXor;

    log_val[0] = SrcOpAnd;
    log_val[1] = SrcOpXor;
    log_val[2] = SrcOpAdd;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval;
    DstOpAdd -= DstOpXor;

    dstF = (((cnstA) & DstOpAnd) ^ DstOpXor) + DstOpAdd;

    mul8_cnstA = mul8table[cnstA];
    mul8_dstF = mul8table[dstF];

    vis_write_gsr(7 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        if (((mlib_s32)rasBase | rasScan) & 3) {
            if (width > BUFF_SIZE) pbuff = mlib_malloc(width*sizeof(mlib_s32));
        } else {
            if (rasScan == 4*width && maskScan == width) {
                width *= height;
                height = 1;
            }
        }

        for (j = 0; j < height; j++) {
            if (!((mlib_s32)rasBase & 3)) {
                IntArgbAlphaMaskFill_line(rasBase, pMask, width, cnstARGB0,
                                          log_val, mul8_cnstA, mul8_dstF,
                                          (void*)mul8table);
            } else {
                mlib_ImageCopy_na(rasBase, pbuff, width*sizeof(mlib_s32));
                IntArgbAlphaMaskFill_line(pbuff, pMask, width, cnstARGB0,
                                          log_val, mul8_cnstA, mul8_dstF,
                                          (void*)mul8table);
                mlib_ImageCopy_na(pbuff, rasBase, width*sizeof(mlib_s32));
            }

            PTR_ADD(rasBase, rasScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        if (((mlib_s32)rasBase | rasScan) & 3) {
            if (width > BUFF_SIZE) pbuff = mlib_malloc(width*sizeof(mlib_s32));
        } else {
            if (rasScan == 4*width) {
                width *= height;
                height = 1;
            }
        }

        for (j = 0; j < height; j++) {
            if (!((mlib_s32)rasBase & 3)) {
                IntArgbAlphaMaskFill_A1_line(rasBase, pMask, width, cnstARGB0,
                                             log_val, mul8_cnstA, mul8_dstF,
                                             (void*)mul8table);
            } else {
                mlib_ImageCopy_na(rasBase, pbuff, width*sizeof(mlib_s32));
                IntArgbAlphaMaskFill_A1_line(pbuff, pMask, width, cnstARGB0,
                                             log_val, mul8_cnstA, mul8_dstF,
                                             (void*)mul8table);
                mlib_ImageCopy_na(pbuff, rasBase, width*sizeof(mlib_s32));
            }

            PTR_ADD(rasBase, rasScan);
        }
    }

    if (pbuff != buff) {
        mlib_free(pbuff);
    }
}

/***************************************************************/

/* ##############################################################
 * IntRgbAlphaMaskFill()
 * IntBgrAlphaMaskFill()
 */

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB)                    \
{                                                              \
    mlib_d64 t0, t1;                                           \
    mlib_s32 srcF, srcA;                                       \
                                                               \
    srcF = mul8_srcF[pathA];                                   \
    srcA = mul8_cnstA[srcF];                                   \
    dstA = mul8_dstF[pathA] + (0xff - pathA);                  \
                                                               \
    t0 = MUL8_VIS(cnstARGB0, srcF);                            \
    t1 = MUL8_VIS(dstARGB, dstA);                              \
    rr = vis_fpadd16(t0, t1);                                  \
                                                               \
    dstA += srcA;                                              \
    DIV_ALPHA_RGB(rr, dstA);                                   \
                                                               \
    pathA = dstA - 0xff - srcF;                                \
    /* (pathA == 0) if (dstA == 0xFF && srcF == 0) */          \
}

/***************************************************************/

static void IntRgbAlphaMaskFill_line(mlib_f32 *dst_ptr,
                                     mlib_u8  *pMask,
                                     mlib_s32 width,
                                     mlib_f32 cnstARGB0,
                                     mlib_u8  *mul8_cnstA,
                                     mlib_u8  *mul8_dstF,
                                     mlib_u8  *mul8_srcF,
                                     mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];

        dstARGB0 = dst_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0);
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

        MASK_FILL(res0, pathA0, dstA0, vis_read_hi(dstARGB));
        MASK_FILL(res1, pathA1, dstA1, vis_read_lo(dstARGB));

        res0 = vis_fpack16_pair(res0, res1);

        msk = (((pathA0) & (1 << 11)) | ((pathA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);
    }

    if (i < width) {
        pathA0 = pMask[i];

        dstARGB0 = dst_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0);
        if (pathA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, _dstA_, dstARGB)          \
{                                                      \
    rr = MUL8_VIS(dstARGB, dstF);                      \
    rr = vis_fpadd16(rr, cnstARGB);                    \
                                                       \
    DIV_ALPHA_RGB(rr, dstA);                           \
}

/***************************************************************/

static void IntRgbAlphaMaskFill_A1_line(mlib_f32 *dst_ptr,
                                         mlib_u8  *pMask,
                                         mlib_s32 width,
                                         mlib_d64 cnstARGB,
                                         mlib_s32 dstF,
                                         mlib_s32 dstA)
{
    mlib_s32 i;
    mlib_d64 res0;
    mlib_f32 dstARGB0;

#pragma pipeloop(0)
    for (i = 0; i < width; i++) {
        dstARGB0 = dst_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0);
        dst_ptr[i] = vis_fpack16(res0);
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbAlphaMaskFill)(void *rasBase,
                                    jubyte *pMask,
                                    jint maskOff,
                                    jint maskScan,
                                    jint width,
                                    jint height,
                                    jint fgColor,
                                    SurfaceDataRasInfo *pRasInfo,
                                    NativePrimitive *pPrim,
                                    CompositeInfo *pCompInfo)
{
    mlib_s32 cnstA, cnstR, cnstG, cnstB;
    mlib_s32 rasScan = pRasInfo->scanStride;
    mlib_f32 cnstARGB0;
    mlib_u8  *mul8_cnstA, *mul8_dstF, *mul8_srcF;
    mlib_s32 SrcOpAnd, SrcOpXor, SrcOpAdd;
    mlib_s32 DstOpAnd, DstOpXor, DstOpAdd;
    mlib_s32 srcF, dstF;
    mlib_s32 log_val[3];
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

    log_val[0] = SrcOpAnd;
    log_val[1] = SrcOpXor;
    log_val[2] = SrcOpAdd;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval;
    DstOpAdd -= DstOpXor;

    dstF = (((cnstA) & DstOpAnd) ^ DstOpXor) + DstOpAdd;
    srcF = (((  255) & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;

    vis_write_gsr(7 << 3);

    mul8_cnstA = mul8table[cnstA];

    if (pMask != NULL) {
        pMask += maskOff;

        mul8_dstF  = mul8table[dstF];
        mul8_srcF  = mul8table[srcF];

        if (rasScan == 4*width && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntRgbAlphaMaskFill_line(rasBase, pMask, width, cnstARGB0,
                                     mul8_cnstA, mul8_dstF, mul8_srcF,
                                     (void*)mul8table);

            PTR_ADD(rasBase, rasScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        mlib_s32 dstA;
        mlib_d64 cnstARGB;

        if (dstF == 0xFF && srcF == 0) return;

        cnstARGB = MUL8_VIS(cnstARGB0, srcF);
        dstA = dstF + mul8_cnstA[srcF];

        if (rasScan == 4*width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntRgbAlphaMaskFill_A1_line(rasBase, pMask, width, cnstARGB,
                                        dstF, dstA);

            PTR_ADD(rasBase, rasScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntBgrAlphaMaskFill)(void *rasBase,
                                   jubyte *pMask,
                                   jint maskOff,
                                   jint maskScan,
                                   jint width,
                                   jint height,
                                   jint fgColor,
                                   SurfaceDataRasInfo *pRasInfo,
                                   NativePrimitive *pPrim,
                                   CompositeInfo *pCompInfo)
{
    mlib_s32 cnstA, cnstR, cnstG, cnstB;
    mlib_s32 rasScan = pRasInfo->scanStride;
    mlib_f32 cnstARGB0;
    mlib_u8  *mul8_cnstA, *mul8_dstF, *mul8_srcF;
    mlib_s32 SrcOpAnd, SrcOpXor, SrcOpAdd;
    mlib_s32 DstOpAnd, DstOpXor, DstOpAdd;
    mlib_s32 srcF, dstF;
    mlib_s32 log_val[3];
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

    cnstARGB0 = F32_FROM_U8x4(cnstA, cnstB, cnstG, cnstR);

    SrcOpAnd = (AlphaRules[pCompInfo->rule].srcOps).andval;
    SrcOpXor = (AlphaRules[pCompInfo->rule].srcOps).xorval;
    SrcOpAdd = (AlphaRules[pCompInfo->rule].srcOps).addval;
    SrcOpAdd -= SrcOpXor;

    log_val[0] = SrcOpAnd;
    log_val[1] = SrcOpXor;
    log_val[2] = SrcOpAdd;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval;
    DstOpAdd -= DstOpXor;

    dstF = (((cnstA) & DstOpAnd) ^ DstOpXor) + DstOpAdd;
    srcF = (((  255) & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;

    vis_write_gsr(7 << 3);

    mul8_cnstA = mul8table[cnstA];

    if (pMask != NULL) {
        pMask += maskOff;

        mul8_dstF  = mul8table[dstF];
        mul8_srcF  = mul8table[srcF];

        if (rasScan == 4*width && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntRgbAlphaMaskFill_line(rasBase, pMask, width, cnstARGB0,
                                     mul8_cnstA, mul8_dstF, mul8_srcF,
                                     (void*)mul8table);

            PTR_ADD(rasBase, rasScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        mlib_s32 dstA;
        mlib_d64 cnstARGB;

        if (dstF == 0xFF && srcF == 0) return;

        cnstARGB = MUL8_VIS(cnstARGB0, srcF);
        dstA = dstF + mul8_cnstA[srcF];

        if (rasScan == 4*width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntRgbAlphaMaskFill_A1_line(rasBase, pMask, width, cnstARGB,
                                        dstF, dstA);

            PTR_ADD(rasBase, rasScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(ThreeByteBgrAlphaMaskFill)(void *rasBase,
                                         jubyte *pMask,
                                         jint maskOff,
                                         jint maskScan,
                                         jint width,
                                         jint height,
                                         jint fgColor,
                                         SurfaceDataRasInfo *pRasInfo,
                                         NativePrimitive *pPrim,
                                         CompositeInfo *pCompInfo)
{
    mlib_d64 buff[BUFF_SIZE/2];
    void     *pbuff = buff;
    mlib_s32 cnstA, cnstR, cnstG, cnstB;
    mlib_s32 rasScan = pRasInfo->scanStride;
    mlib_f32 cnstARGB0;
    mlib_u8  *mul8_cnstA, *mul8_dstF, *mul8_srcF;
    mlib_s32 SrcOpAnd, SrcOpXor, SrcOpAdd;
    mlib_s32 DstOpAnd, DstOpXor, DstOpAdd;
    mlib_s32 srcF, dstF;
    mlib_s32 log_val[3];
    mlib_s32 j;

    if (width > BUFF_SIZE) pbuff = mlib_malloc(width*sizeof(mlib_s32));

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

    log_val[0] = SrcOpAnd;
    log_val[1] = SrcOpXor;
    log_val[2] = SrcOpAdd;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval;
    DstOpAdd -= DstOpXor;

    dstF = (((cnstA) & DstOpAnd) ^ DstOpXor) + DstOpAdd;
    srcF = (((  255) & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;

    vis_write_gsr(7 << 3);

    mul8_cnstA = mul8table[cnstA];

    if (pMask != NULL) {
        pMask += maskOff;

        mul8_dstF  = mul8table[dstF];
        mul8_srcF  = mul8table[srcF];

        for (j = 0; j < height; j++) {
            ADD_SUFF(ThreeByteBgrToIntArgbConvert)(rasBase, pbuff, width, 1,
                                                   pRasInfo, pRasInfo,
                                                   pPrim, pCompInfo);

            IntRgbAlphaMaskFill_line(pbuff, pMask, width, cnstARGB0,
                                     mul8_cnstA, mul8_dstF, mul8_srcF,
                                     (void*)mul8table);

            IntArgbToThreeByteBgrConvert(pbuff, rasBase, width, 1,
                                         pRasInfo, pRasInfo, pPrim, pCompInfo);

            PTR_ADD(rasBase, rasScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        mlib_s32 dstA;
        mlib_d64 cnstARGB;

        if (dstF == 0xFF && srcF == 0) return;

        cnstARGB = MUL8_VIS(cnstARGB0, srcF);
        dstA = dstF + mul8_cnstA[srcF];

        for (j = 0; j < height; j++) {
            ADD_SUFF(ThreeByteBgrToIntArgbConvert)(rasBase, pbuff, width, 1,
                                                   pRasInfo, pRasInfo,
                                                   pPrim, pCompInfo);

            IntRgbAlphaMaskFill_A1_line(pbuff, pMask, width, cnstARGB,
                                        dstF, dstA);

            IntArgbToThreeByteBgrConvert(pbuff, rasBase, width, 1,
                                         pRasInfo, pRasInfo, pPrim, pCompInfo);

            PTR_ADD(rasBase, rasScan);
        }
    }

    if (pbuff != buff) {
        mlib_free(pbuff);
    }
}

/***************************************************************/

#endif /* JAVA2D_NO_MLIB */
