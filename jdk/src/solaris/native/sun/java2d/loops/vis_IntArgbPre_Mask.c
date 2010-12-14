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
 * IntArgbPreAlphaMaskFill()
 */

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB)               \
{                                                         \
    mlib_d64 t0, t1;                                      \
    mlib_s32 srcF, dstF;                                  \
                                                          \
    srcF = ((dstA & ConstAnd) ^ ConstXor) + ConstAdd;     \
    srcF = MUL8_INT(srcF, pathA);                         \
    dstF = mul8_cnstF[pathA] + (255 - pathA);             \
                                                          \
    t0 = MUL8_VIS(cnstARGB0, srcF);                       \
    t1 = MUL8_VIS(dstARGB, dstF);                         \
    rr = vis_fpadd16(t0, t1);                             \
}

/***************************************************************/

void IntArgbPreAlphaMaskFill_line(mlib_f32 *dst_ptr,
                                  mlib_u8  *pMask,
                                  mlib_s32 width,
                                  mlib_f32 cnstARGB0,
                                  mlib_s32 *log_val,
                                  mlib_u8  *mul8_cnstF,
                                  mlib_u8  *mul8_tbl);

#pragma no_inline(IntArgbPreAlphaMaskFill_line)

void IntArgbPreAlphaMaskFill_line(mlib_f32 *dst_ptr,
                                  mlib_u8  *pMask,
                                  mlib_s32 width,
                                  mlib_f32 cnstARGB0,
                                  mlib_s32 *log_val,
                                  mlib_u8  *mul8_cnstF,
                                  mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;
    mlib_s32 ConstAnd, ConstXor, ConstAdd;

    ConstAnd = log_val[0];
    ConstXor = log_val[1];
    ConstAdd = log_val[2];

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];

        if (pathA0) {
            dstA0 = *(mlib_u8*)(dst_ptr + i);
            dstARGB0 = dst_ptr[i];
            MASK_FILL(res0, pathA0, dstA0, dstARGB0);
            dst_ptr[i] = vis_fpack16(res0);
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
    }

    if (i < width) {
        pathA0 = pMask[i];

        if (pathA0) {
            dstA0 = *(mlib_u8*)(dst_ptr + i);
            dstARGB0 = dst_ptr[i];
            MASK_FILL(res0, pathA0, dstA0, dstARGB0);
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, cnstF, dstA, dstARGB)               \
{                                                         \
    mlib_d64 t0, t1;                                      \
    mlib_s32 srcF, dstF;                                  \
                                                          \
    srcF = ((dstA & ConstAnd) ^ ConstXor) + ConstAdd;     \
    dstF = cnstF;                                         \
                                                          \
    t0 = MUL8_VIS(cnstARGB0, srcF);                       \
    t1 = MUL8_VIS(dstARGB, dstF);                         \
    rr = vis_fpadd16(t0, t1);                             \
}

/***************************************************************/

void IntArgPrebAlphaMaskFill_A1_line(mlib_f32 *dst_ptr,
                                     mlib_s32 width,
                                     mlib_f32 cnstARGB0,
                                     mlib_s32 *log_val,
                                     mlib_s32 cnstF);

#pragma no_inline(IntArgPrebAlphaMaskFill_A1_line)

void IntArgPrebAlphaMaskFill_A1_line(mlib_f32 *dst_ptr,
                                     mlib_s32 width,
                                     mlib_f32 cnstARGB0,
                                     mlib_s32 *log_val,
                                     mlib_s32 cnstF)
{
    mlib_s32 i, i0;
    mlib_s32 dstA0, dstA1;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;
    mlib_s32 ConstAnd, ConstXor, ConstAdd;

    ConstAnd = log_val[0];
    ConstXor = log_val[1];
    ConstAdd = log_val[2];

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        {
            dstA0 = *(mlib_u8*)(dst_ptr + i);
            dstARGB0 = dst_ptr[i];
            MASK_FILL(res0, cnstF, dstA0, dstARGB0);
            dst_ptr[i] = vis_fpack16(res0);
        }

        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        dstA0 = *(mlib_u8*)(dst_ptr + i);
        dstA1 = *(mlib_u8*)(dst_ptr + i + 1);
        dstARGB = *(mlib_d64*)(dst_ptr + i);

        MASK_FILL(res0, cnstF, dstA0, vis_read_hi(dstARGB));
        MASK_FILL(res1, cnstF, dstA1, vis_read_lo(dstARGB));

        res0 = vis_fpack16_pair(res0, res1);

        *(mlib_d64*)(dst_ptr + i) = res0;
    }

    if (i < width) {
        {
            dstA0 = *(mlib_u8*)(dst_ptr + i);
            dstARGB0 = dst_ptr[i];
            MASK_FILL(res0, cnstF, dstA0, dstARGB0);
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbPreAlphaMaskFill)(void *rasBase,
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
    mlib_u8  *mul8_cnstF;
    mlib_s32 SrcOpAnd, SrcOpXor, SrcOpAdd;
    mlib_s32 DstOpAnd, DstOpXor, DstOpAdd;
    mlib_s32 dstFbase;
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

    dstFbase = (((cnstA) & DstOpAnd) ^ DstOpXor) + DstOpAdd;

    mul8_cnstF = mul8table[dstFbase];

    vis_write_gsr(0 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        if (rasScan == 4*width && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntArgbPreAlphaMaskFill_line(rasBase, pMask, width, cnstARGB0,
                                         log_val, mul8_cnstF,
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
            IntArgPrebAlphaMaskFill_A1_line(rasBase, width, cnstARGB0,
                                            log_val, dstFbase);

            PTR_ADD(rasBase, rasScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(FourByteAbgrPreAlphaMaskFill)(void *rasBase,
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
    void     *pbuff = buff, *p_dst;
    mlib_s32 cnstA, cnstR, cnstG, cnstB;
    mlib_s32 rasScan = pRasInfo->scanStride;
    mlib_f32 cnstARGB0;
    mlib_u8  *mul8_cnstF;
    mlib_s32 SrcOpAnd, SrcOpXor, SrcOpAdd;
    mlib_s32 DstOpAnd, DstOpXor, DstOpAdd;
    mlib_s32 dstFbase;
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

    dstFbase = (((cnstA) & DstOpAnd) ^ DstOpXor) + DstOpAdd;

    mul8_cnstF = mul8table[dstFbase];

    vis_write_gsr(0 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        for (j = 0; j < height; j++) {
            if ((mlib_s32)rasBase & 3) {
                mlib_ImageCopy_na(rasBase, pbuff, width*sizeof(mlib_s32));
                p_dst = pbuff;
            } else {
                p_dst = rasBase;
            }

            IntArgbPreAlphaMaskFill_line(p_dst, pMask, width, cnstARGB0,
                                         log_val, mul8_cnstF,
                                         (void*)mul8table);

            if (p_dst != rasBase) {
                mlib_ImageCopy_na(p_dst, rasBase, width*sizeof(mlib_s32));
            }

            PTR_ADD(rasBase, rasScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        for (j = 0; j < height; j++) {
            if ((mlib_s32)rasBase & 3) {
                mlib_ImageCopy_na(rasBase, pbuff, width*sizeof(mlib_s32));
                p_dst = pbuff;
            } else {
                p_dst = rasBase;
            }

            IntArgPrebAlphaMaskFill_A1_line(p_dst, width, cnstARGB0,
                                            log_val, dstFbase);

            if (p_dst != rasBase) {
                mlib_ImageCopy_na(p_dst, rasBase, width*sizeof(mlib_s32));
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
 * IntArgbPreSrcMaskFill()
 */

#undef MASK_FILL
#define MASK_FILL(rr, pathA, dstARGB)           \
{                                               \
    mlib_d64 t0, t1;                            \
                                                \
    t0 = MUL8_VIS(cnstARGB0, pathA);            \
    t1 = MUL8_VIS(dstARGB, (0xff - pathA));     \
    rr = vis_fpadd16(t0, t1);                   \
}

/***************************************************************/

void IntArgbPreSrcMaskFill_line(mlib_f32 *dst_ptr,
                                mlib_u8  *pMask,
                                mlib_s32 width,
                                mlib_d64 fgARGB,
                                mlib_f32 cnstARGB0);

#pragma no_inline(IntArgbPreSrcMaskFill_line)

void IntArgbPreSrcMaskFill_line(mlib_f32 *dst_ptr,
                                mlib_u8  *pMask,
                                mlib_s32 width,
                                mlib_d64 fgARGB,
                                mlib_f32 cnstARGB0)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];
        if (pathA0 == 0xff) {
            dst_ptr[i] = vis_read_hi(fgARGB);
        } else if (pathA0) {
            dstARGB0 = dst_ptr[i];
            MASK_FILL(res0, pathA0, dstARGB0);
            dst_ptr[i] = vis_fpack16(res0);
        }

        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        pathA0 = pMask[i];
        pathA1 = pMask[i + 1];

        dstARGB = *(mlib_d64*)(dst_ptr + i);

        msk = (((254 - pathA0) & (1 << 11)) |
               ((254 - pathA1) & (1 << 10))) >> 10;

        MASK_FILL(res0, pathA0, vis_read_hi(dstARGB));
        MASK_FILL(res1, pathA1, vis_read_lo(dstARGB));

        res0 = vis_fpack16_pair(res0, res1);

        *(mlib_d64*)(dst_ptr + i) = res0;

        vis_pst_32(fgARGB, dst_ptr + i, msk);
    }

    if (i < width) {
        pathA0 = pMask[i];
        if (pathA0 == 0xff) {
            dst_ptr[i] = vis_read_hi(fgARGB);
        } else if (pathA0) {
            dstARGB0 = dst_ptr[i];
            MASK_FILL(res0, pathA0, dstARGB0);
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbPreSrcMaskFill)(void *rasBase,
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
    mlib_d64 fgARGB;
    mlib_s32 j;

    cnstA = (fgColor >> 24) & 0xff;
    cnstR = (fgColor >> 16) & 0xff;
    cnstG = (fgColor >>  8) & 0xff;
    cnstB = (fgColor      ) & 0xff;

    if (cnstA != 0xff) {
#ifdef LOOPS_OLD_VERSION
        if (cnstA == 0) return;
#endif
        cnstR = mul8table[cnstA][cnstR];
        cnstG = mul8table[cnstA][cnstG];
        cnstB = mul8table[cnstA][cnstB];
    }

    if (pMask == NULL) {
#ifdef LOOPS_OLD_VERSION
        ADD_SUFF(AnyIntSetRect)(pRasInfo, 0, 0, width, height,
                                fgColor, pPrim, pCompInfo);
#else
        void *pBase = pRasInfo->rasBase;
        pRasInfo->rasBase = rasBase;
        if (cnstA != 0xff) {
            fgColor = (cnstA << 24) | (cnstR << 16) | (cnstG << 8) | cnstB;
        }
        ADD_SUFF(AnyIntSetRect)(pRasInfo,
                                0, 0, width, height,
                                fgColor, pPrim, pCompInfo);
        pRasInfo->rasBase = pBase;
#endif
        return;
    }

    cnstARGB0 = F32_FROM_U8x4(cnstA, cnstR, cnstG, cnstB);

    fgARGB = vis_to_double_dup(fgColor);

    pMask += maskOff;

    if (rasScan == 4*width && maskScan == width) {
        width *= height;
        height = 1;
    }

    vis_write_gsr(0 << 3);

    for (j = 0; j < height; j++) {
        IntArgbPreSrcMaskFill_line(rasBase, pMask, width, fgARGB, cnstARGB0);

        PTR_ADD(rasBase, rasScan);
        PTR_ADD(pMask, maskScan);
    }
}

/***************************************************************/

void ADD_SUFF(FourByteAbgrPreSrcMaskFill)(void *rasBase,
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
    void     *pbuff = buff, *p_dst;
    mlib_s32 cnstA, cnstR, cnstG, cnstB;
    mlib_s32 rasScan = pRasInfo->scanStride;
    mlib_f32 cnstARGB0;
    mlib_d64 fgARGB;
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

    if (pMask == NULL) {
        void *pBase = pRasInfo->rasBase;
        pRasInfo->rasBase = rasBase;
        fgColor = (cnstR << 24) | (cnstG << 16) | (cnstB << 8) | cnstA;
        ADD_SUFF(Any4ByteSetRect)(pRasInfo,
                                  0, 0, width, height,
                                  fgColor, pPrim, pCompInfo);
        pRasInfo->rasBase = pBase;
        return;
    }

    fgColor = (cnstA << 24) | (cnstB << 16) | (cnstG << 8) | cnstR;
    cnstARGB0 = F32_FROM_U8x4(cnstA, cnstB, cnstG, cnstR);

    fgARGB = vis_to_double_dup(fgColor);

    pMask += maskOff;

    vis_write_gsr(0 << 3);

    if (width > BUFF_SIZE) pbuff = mlib_malloc(width*sizeof(mlib_s32));

    for (j = 0; j < height; j++) {
        if ((mlib_s32)rasBase & 3) {
            mlib_ImageCopy_na(rasBase, pbuff, width*sizeof(mlib_s32));
            p_dst = pbuff;
        } else {
            p_dst = rasBase;
        }

        IntArgbPreSrcMaskFill_line(p_dst, pMask, width, fgARGB, cnstARGB0);

        if (p_dst != rasBase) {
            mlib_ImageCopy_na(p_dst, rasBase, width*sizeof(mlib_s32));
        }

        PTR_ADD(rasBase, rasScan);
        PTR_ADD(pMask, maskScan);
    }

    if (pbuff != buff) {
        mlib_free(pbuff);
    }
}

/***************************************************************/

/* ##############################################################
 * IntArgbPreSrcOverMaskFill()
 */

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstARGB)         \
{                                             \
    mlib_d64 t0, t1;                          \
    mlib_s32 dstA;                            \
                                              \
    dstA = 0xff - mul8_cnstA[pathA];          \
                                              \
    t0 = MUL8_VIS(cnstARGB0, pathA);          \
    t1 = MUL8_VIS(dstARGB, dstA);             \
    rr = vis_fpadd16(t0, t1);                 \
}

/***************************************************************/

static void IntArgbPreSrcOverMaskFill_line(mlib_f32 *dst_ptr,
                                           mlib_u8  *pMask,
                                           mlib_s32 width,
                                           mlib_f32 cnstARGB0,
                                           mlib_u8  *mul8_cnstA);

#pragma no_inline(IntArgbPreSrcOverMaskFill_line)

static void IntArgbPreSrcOverMaskFill_line(mlib_f32 *dst_ptr,
                                           mlib_u8  *pMask,
                                           mlib_s32 width,
                                           mlib_f32 cnstARGB0,
                                           mlib_u8  *mul8_cnstA)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];

        if (pathA0) {
            dstARGB0 = dst_ptr[i];
            MASK_FILL(res0, pathA0, dstARGB0);
            dst_ptr[i] = vis_fpack16(res0);
        }

        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        pathA0 = pMask[i];
        pathA1 = pMask[i + 1];
        dstARGB = *(mlib_d64*)(dst_ptr + i);

        MASK_FILL(res0, pathA0, vis_read_hi(dstARGB));
        MASK_FILL(res1, pathA1, vis_read_lo(dstARGB));

        res0 = vis_fpack16_pair(res0, res1);

        *(mlib_d64 *)(dst_ptr + i) = res0;
    }

    if (i < width) {
        pathA0 = pMask[i];

        if (pathA0) {
            dstARGB0 = dst_ptr[i];
            MASK_FILL(res0, pathA0, dstARGB0);
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, dstARGB)          \
{                                       \
    rr = MUL8_VIS(dstARGB, cnstA);      \
    rr = vis_fpadd16(rr, cnstARGB);     \
}

/***************************************************************/

static void IntArgbPreSrcOverMaskFill_A1_line(mlib_f32 *dst_ptr,
                                              mlib_s32 width,
                                              mlib_d64 cnstARGB,
                                              mlib_s32 cnstA);

#pragma no_inline(IntArgbPreSrcOverMaskFill_A1_line)

static void IntArgbPreSrcOverMaskFill_A1_line(mlib_f32 *dst_ptr,
                                              mlib_s32 width,
                                              mlib_d64 cnstARGB,
                                              mlib_s32 cnstA)
{
    mlib_s32 i, i0;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;

    cnstA = 0xff - cnstA;

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        dstARGB0 = dst_ptr[i];
        MASK_FILL(res0, dstARGB0);
        dst_ptr[i] = vis_fpack16(res0);
        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        dstARGB = *(mlib_d64*)(dst_ptr + i);

        MASK_FILL(res0, vis_read_hi(dstARGB));
        MASK_FILL(res1, vis_read_lo(dstARGB));

        res0 = vis_fpack16_pair(res0, res1);

        *(mlib_d64*)(dst_ptr + i) = res0;
    }

    if (i < width) {
        dstARGB0 = dst_ptr[i];
        MASK_FILL(res0, dstARGB0);
        dst_ptr[i] = vis_fpack16(res0);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbPreSrcOverMaskFill)(void *rasBase,
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
    mlib_d64 cnstARGB;
    mlib_u8  *mul8_cnstA;
    mlib_s32 j;

    cnstA = (fgColor >> 24) & 0xff;
    cnstR = (fgColor >> 16) & 0xff;
    cnstG = (fgColor >>  8) & 0xff;
    cnstB = (fgColor      ) & 0xff;

    if (cnstA != 0xff) {
        if (cnstA == 0) return;

        cnstR = mul8table[cnstA][cnstR];
        cnstG = mul8table[cnstA][cnstG];
        cnstB = mul8table[cnstA][cnstB];
    }

    vis_write_gsr(0 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        if (rasScan == 4*width && maskScan == width) {
            width *= height;
            height = 1;
        }

        mul8_cnstA = mul8table[cnstA];

        cnstARGB0 = F32_FROM_U8x4(cnstA, cnstR, cnstG, cnstB);

        for (j = 0; j < height; j++) {
            IntArgbPreSrcOverMaskFill_line(rasBase, pMask, width, cnstARGB0,
                                           mul8_cnstA);

            PTR_ADD(rasBase, rasScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        if (rasScan == 4*width) {
            width *= height;
            height = 1;
        }

        cnstARGB = vis_to_double((cnstA << 23) | (cnstR << 7),
                                 (cnstG << 23) | (cnstB << 7));

        for (j = 0; j < height; j++) {
            IntArgbPreSrcOverMaskFill_A1_line(rasBase, width, cnstARGB, cnstA);

            PTR_ADD(rasBase, rasScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(FourByteAbgrPreSrcOverMaskFill)(void *rasBase,
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
    void     *pbuff = buff, *p_dst;
    mlib_s32 cnstA, cnstR, cnstG, cnstB;
    mlib_s32 rasScan = pRasInfo->scanStride;
    mlib_f32 cnstARGB0;
    mlib_d64 cnstARGB;
    mlib_u8  *mul8_cnstA;
    mlib_s32 j;

    if (width > BUFF_SIZE) pbuff = mlib_malloc(width*sizeof(mlib_s32));

    cnstA = (fgColor >> 24) & 0xff;
    cnstR = (fgColor >> 16) & 0xff;
    cnstG = (fgColor >>  8) & 0xff;
    cnstB = (fgColor      ) & 0xff;

    if (cnstA != 0xff) {
        if (cnstA == 0) return;

        cnstR = mul8table[cnstA][cnstR];
        cnstG = mul8table[cnstA][cnstG];
        cnstB = mul8table[cnstA][cnstB];
    }

    vis_write_gsr(0 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        mul8_cnstA = mul8table[cnstA];

        cnstARGB0 = F32_FROM_U8x4(cnstA, cnstB, cnstG, cnstR);

        for (j = 0; j < height; j++) {
            if ((mlib_s32)rasBase & 3) {
                mlib_ImageCopy_na(rasBase, pbuff, width*sizeof(mlib_s32));
                p_dst = pbuff;
            } else {
                p_dst = rasBase;
            }

            IntArgbPreSrcOverMaskFill_line(p_dst, pMask, width, cnstARGB0,
                                           mul8_cnstA);

            if (p_dst != rasBase) {
                mlib_ImageCopy_na(p_dst, rasBase, width*sizeof(mlib_s32));
            }

            PTR_ADD(rasBase, rasScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        cnstARGB = vis_to_double((cnstA << 23) | (cnstB << 7),
                                 (cnstG << 23) | (cnstR << 7));

        for (j = 0; j < height; j++) {
            if ((mlib_s32)rasBase & 3) {
                mlib_ImageCopy_na(rasBase, pbuff, width*sizeof(mlib_s32));
                p_dst = pbuff;
            } else {
                p_dst = rasBase;
            }

            IntArgbPreSrcOverMaskFill_A1_line(p_dst, width, cnstARGB, cnstA);

            if (p_dst != rasBase) {
                mlib_ImageCopy_na(p_dst, rasBase, width*sizeof(mlib_s32));
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
 * IntArgbToIntArgbPreSrcOverMaskBlit()
 */

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstARGB, srcA, srcARGB)         \
{                                                            \
    mlib_d64 t0, t1;                                         \
    mlib_s32 dstF;                                           \
                                                             \
    srcA = MUL8_INT(mul8_extra[pathA], srcA);                \
    dstF = 0xff - srcA;                                      \
                                                             \
    t0 = MUL8_VIS(srcARGB, srcA);                            \
    t1 = MUL8_VIS(dstARGB, dstF);                            \
    rr = vis_fpadd16(t0, t1);                                \
}

/***************************************************************/

static void IntArgbToIntArgbPreSrcOverMaskBlit_line(mlib_f32 *dst_ptr,
                                                    mlib_f32 *src_ptr,
                                                    mlib_u8  *pMask,
                                                    mlib_s32 width,
                                                    mlib_u8  *mul8_extra,
                                                    mlib_u8  *mul8_tbl);

#pragma no_inline(IntArgbToIntArgbPreSrcOverMaskBlit_line)

static void IntArgbToIntArgbPreSrcOverMaskBlit_line(mlib_f32 *dst_ptr,
                                                    mlib_f32 *src_ptr,
                                                    mlib_u8  *pMask,
                                                    mlib_s32 width,
                                                    mlib_u8  *mul8_extra,
                                                    mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, srcA0, srcA1;
    mlib_d64 res0, res1, dstARGB, srcARGB;
    mlib_f32 dstARGB0, srcARGB0;
    mlib_d64 or_alpha = vis_to_double_dup(0xff000000);

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        srcARGB0 = vis_fors(vis_read_hi(or_alpha), srcARGB0);
        MASK_FILL(res0, pathA0, dstARGB0, srcA0, srcARGB0);
        if (srcA0) {
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
        srcARGB = vis_for(or_alpha, srcARGB);

        MASK_FILL(res0, pathA0, vis_read_hi(dstARGB),
                  srcA0, vis_read_hi(srcARGB));
        MASK_FILL(res1, pathA1, vis_read_lo(dstARGB),
                  srcA1, vis_read_lo(srcARGB));

        res0 = vis_fpack16_pair(res0, res1);

        *(mlib_d64*)(dst_ptr + i) = res0;
    }

    if (i < width) {
        pathA0 = pMask[i];
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        srcARGB0 = vis_fors(vis_read_hi(or_alpha), srcARGB0);
        MASK_FILL(res0, pathA0, dstARGB0, srcA0, srcARGB0);
        if (srcA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, dstARGB, srcA, srcARGB)         \
{                                                     \
    mlib_d64 t0, t1;                                  \
    mlib_s32 dstF;                                    \
                                                      \
    srcA = mul8_extra[srcA];                          \
    dstF = 0xff - srcA;                               \
                                                      \
    t0 = MUL8_VIS(srcARGB, srcA);                     \
    t1 = MUL8_VIS(dstARGB, dstF);                     \
    rr = vis_fpadd16(t0, t1);                         \
}

/***************************************************************/

static void IntArgbToIntArgbPreSrcOverMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                       mlib_f32 *src_ptr,
                                                       mlib_s32 width,
                                                       mlib_u8  *mul8_extra);

#pragma no_inline(IntArgbToIntArgbPreSrcOverMaskBlit_A1_line)

static void IntArgbToIntArgbPreSrcOverMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                       mlib_f32 *src_ptr,
                                                       mlib_s32 width,
                                                       mlib_u8  *mul8_extra)
{
    mlib_s32 i, i0;
    mlib_s32 srcA0, srcA1;
    mlib_d64 res0, res1, dstARGB, srcARGB;
    mlib_f32 dstARGB0, srcARGB0;
    mlib_d64 or_alpha = vis_to_double_dup(0xff000000);

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        srcARGB0 = vis_fors(vis_read_hi(or_alpha), srcARGB0);
        MASK_FILL(res0, dstARGB0, srcA0, srcARGB0);
        if (srcA0) {
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
        srcARGB = vis_for(or_alpha, srcARGB);

        MASK_FILL(res0, vis_read_hi(dstARGB), srcA0, vis_read_hi(srcARGB));
        MASK_FILL(res1, vis_read_lo(dstARGB), srcA1, vis_read_lo(srcARGB));

        res0 = vis_fpack16_pair(res0, res1);
        *(mlib_d64*)(dst_ptr + i) = res0;
    }

    if (i < width) {
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        srcARGB0 = vis_fors(vis_read_hi(or_alpha), srcARGB0);
        MASK_FILL(res0, dstARGB0, srcA0, srcARGB0);
        if (srcA0) {
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntArgbPreSrcOverMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_s32 extraA;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *mul8_extra;
    mlib_s32 j;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

    mul8_extra = mul8table[extraA];

    vis_write_gsr(0 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        if (dstScan == 4*width && srcScan == dstScan && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntArgbToIntArgbPreSrcOverMaskBlit_line(dstBase, srcBase, pMask,
                                                    width, mul8_extra,
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
            IntArgbToIntArgbPreSrcOverMaskBlit_A1_line(dstBase, srcBase, width,
                                                       mul8_extra);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToFourByteAbgrPreSrcOverMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_d64 buff[BUFF_SIZE/2];
    void     *pbuff = buff;
    mlib_s32 extraA;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *mul8_extra;
    mlib_s32 j;

    if (width > BUFF_SIZE) pbuff = mlib_malloc(width*sizeof(mlib_s32));

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

    mul8_extra = mul8table[extraA];

    vis_write_gsr(0 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        for (j = 0; j < height; j++) {
            ADD_SUFF(FourByteAbgrToIntArgbConvert)(dstBase, pbuff, width, 1,
                                                   pSrcInfo, pDstInfo,
                                                   pPrim, pCompInfo);

            IntArgbToIntArgbPreSrcOverMaskBlit_line(pbuff, srcBase, pMask,
                                                    width, mul8_extra,
                                                    (void*)mul8table);

            ADD_SUFF(IntArgbToFourByteAbgrConvert)(pbuff, dstBase, width, 1,
                                                   pSrcInfo, pDstInfo,
                                                   pPrim, pCompInfo);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        for (j = 0; j < height; j++) {
            ADD_SUFF(FourByteAbgrToIntArgbConvert)(dstBase, pbuff, width, 1,
                                                   pSrcInfo, pDstInfo,
                                                   pPrim, pCompInfo);

            IntArgbToIntArgbPreSrcOverMaskBlit_A1_line(pbuff, srcBase, width,
                                                       mul8_extra);

            ADD_SUFF(IntArgbToFourByteAbgrConvert)(pbuff, dstBase, width, 1,
                                                   pSrcInfo, pDstInfo,
                                                   pPrim, pCompInfo);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }

    if (pbuff != buff) {
        mlib_free(pbuff);
    }
}

/***************************************************************/

/* ##############################################################
 * IntArgbToIntArgbPreAlphaMaskBlit()
 */

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB, srcA, srcARGB)         \
{                                                                  \
    mlib_d64 t0, t1;                                               \
    mlib_s32 srcF, dstF;                                           \
                                                                   \
    srcA = mul8_extra[srcA];                                       \
                                                                   \
    srcF = ((dstA & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;              \
    dstF = ((srcA & DstOpAnd) ^ DstOpXor) + DstOpAdd;              \
                                                                   \
    srcF = MUL8_INT(pathA, srcF);                                  \
    dstF = MUL8_INT(pathA, dstF) + (0xff - pathA);                 \
                                                                   \
    srcA = MUL8_INT(srcF, srcA);                                   \
                                                                   \
    t0 = MUL8_VIS(srcARGB, srcA);                                  \
    t1 = MUL8_VIS(dstARGB, dstF);                                  \
    rr = vis_fpadd16(t0, t1);                                      \
}

/**************************************************************/

static void IntArgbToIntArgbPreAlphaMaskBlit_line(mlib_f32 *dst_ptr,
                                                  mlib_f32 *src_ptr,
                                                  mlib_u8  *pMask,
                                                  mlib_s32 width,
                                                  mlib_s32 *log_val,
                                                  mlib_u8  *mul8_extra,
                                                  mlib_u8  *mul8_tbl);

#pragma no_inline(IntArgbToIntArgbPreAlphaMaskBlit_line)

static void IntArgbToIntArgbPreAlphaMaskBlit_line(mlib_f32 *dst_ptr,
                                                  mlib_f32 *src_ptr,
                                                  mlib_u8  *pMask,
                                                  mlib_s32 width,
                                                  mlib_s32 *log_val,
                                                  mlib_u8  *mul8_extra,
                                                  mlib_u8  *mul8_tbl)
{
    mlib_s32 i;
    mlib_s32 pathA0, dstA0, srcA0;
    mlib_d64 res0;
    mlib_f32 dstARGB0, srcARGB0;
    mlib_s32 SrcOpAnd = log_val[0];
    mlib_s32 SrcOpXor = log_val[1];
    mlib_s32 SrcOpAdd = log_val[2];
    mlib_s32 DstOpAnd = log_val[3];
    mlib_s32 DstOpXor = log_val[4];
    mlib_s32 DstOpAdd = log_val[5];
    mlib_f32 or_alpha = vis_to_float(0xff000000);

#pragma pipeloop(0)
    for (i = 0; i < width; i++) {

        pathA0 = pMask[i];

        dstA0 = *(mlib_u8*)dst_ptr;

        dstARGB0 = *dst_ptr;
        srcA0 = *(mlib_u8*)src_ptr;
        srcARGB0 = *src_ptr;
        srcARGB0 = vis_fors(or_alpha, srcARGB0);

        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);

        *dst_ptr = vis_fpack16(res0);
        dst_ptr++;
        src_ptr++;
    }

}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, dstA, dstARGB, srcA, srcARGB)         \
{                                                           \
    mlib_d64 t0, t1;                                        \
    mlib_s32 srcF, dstF;                                    \
                                                            \
    srcA = mul8_extra[srcA];                                \
                                                            \
    srcF = ((dstA & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;       \
    dstF = ((srcA & DstOpAnd) ^ DstOpXor) + DstOpAdd;       \
                                                            \
    srcA = MUL8_INT(srcF, srcA);                            \
                                                            \
    t0 = MUL8_VIS(srcARGB, srcA);                           \
    t1 = MUL8_VIS(dstARGB, dstF);                           \
    rr = vis_fpadd16(t0, t1);                               \
}

/***************************************************************/

static void IntArgbToIntArgbPreAlphaMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                     mlib_f32 *src_ptr,
                                                     mlib_s32 width,
                                                     mlib_s32 *log_val,
                                                     mlib_u8  *mul8_extra,
                                                     mlib_u8  *mul8_tbl);

#pragma no_inline(IntArgbToIntArgbPreAlphaMaskBlit_A1_line)

static void IntArgbToIntArgbPreAlphaMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                     mlib_f32 *src_ptr,
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
    mlib_f32 or_alpha = vis_to_float(0xff000000);

#pragma pipeloop(0)
    for (i = 0; i < width; i++) {
        dstA0 = *(mlib_u8*)(dst_ptr + i);
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        srcARGB0 = vis_fors(or_alpha, srcARGB0);

        MASK_FILL(res0, dstA0, dstARGB0, srcA0, srcARGB0);

        dst_ptr[i] = vis_fpack16(res0);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntArgbPreAlphaMaskBlit)(MASKBLIT_PARAMS)
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

    vis_write_gsr(0 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        if (dstScan == 4*width && srcScan == dstScan && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntArgbToIntArgbPreAlphaMaskBlit_line(dstBase, srcBase, pMask,
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
            IntArgbToIntArgbPreAlphaMaskBlit_A1_line(dstBase, srcBase,
                                                     width, log_val, mul8_extra,
                                                     (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToFourByteAbgrPreAlphaMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_d64 buff[BUFF_SIZE/2];
    void     *pbuff = buff;
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

    if (width > BUFF_SIZE) pbuff = mlib_malloc(width*sizeof(mlib_s32));

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

    vis_write_gsr(0 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        for (j = 0; j < height; j++) {
            ADD_SUFF(FourByteAbgrToIntArgbConvert)(dstBase, pbuff, width, 1,
                                                   pSrcInfo, pDstInfo,
                                                   pPrim, pCompInfo);

            IntArgbToIntArgbPreAlphaMaskBlit_line(pbuff, srcBase, pMask,
                                                  width, log_val, mul8_extra,
                                                  (void*)mul8table);

            ADD_SUFF(IntArgbToFourByteAbgrConvert)(pbuff, dstBase, width, 1,
                                                   pSrcInfo, pDstInfo,
                                                   pPrim, pCompInfo);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        for (j = 0; j < height; j++) {
            ADD_SUFF(FourByteAbgrToIntArgbConvert)(dstBase, pbuff, width, 1,
                                                   pSrcInfo, pDstInfo,
                                                   pPrim, pCompInfo);

            IntArgbToIntArgbPreAlphaMaskBlit_A1_line(pbuff, srcBase,
                                                     width, log_val, mul8_extra,
                                                     (void*)mul8table);

            ADD_SUFF(IntArgbToFourByteAbgrConvert)(pbuff, dstBase, width, 1,
                                                   pSrcInfo, pDstInfo,
                                                   pPrim, pCompInfo);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }

    if (pbuff != buff) {
        mlib_free(pbuff);
    }
}

/***************************************************************/

/* ##############################################################
 * IntRgbToIntArgbPreAlphaMaskBlit()
 */

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB, srcA, srcARGB)         \
{                                                                  \
    mlib_d64 t0, t1;                                               \
    mlib_s32 srcF, dstF;                                           \
                                                                   \
    srcF = ((dstA & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;              \
                                                                   \
    srcF = MUL8_INT(pathA, srcF);                                  \
    dstF = mul8_tbl[pathA + dstF_0] + (0xff - pathA);              \
                                                                   \
    srcF = mul8_tbl[srcF + srcA];                                  \
                                                                   \
    t0 = MUL8_VIS(srcARGB, srcF);                                  \
    t1 = MUL8_VIS(dstARGB, dstF);                                  \
    rr = vis_fpadd16(t0, t1);                                      \
}

/**************************************************************/

static void IntRgbToIntArgbPreAlphaMaskBlit_line(mlib_f32 *dst_ptr,
                                                 mlib_f32 *src_ptr,
                                                 mlib_u8  *pMask,
                                                 mlib_s32 width,
                                                 mlib_s32 *log_val,
                                                 mlib_s32 extraA,
                                                 mlib_s32 dstF_0,
                                                 mlib_u8  *mul8_tbl);

#pragma no_inline(IntRgbToIntArgbPreAlphaMaskBlit_line)

static void IntRgbToIntArgbPreAlphaMaskBlit_line(mlib_f32 *dst_ptr,
                                                 mlib_f32 *src_ptr,
                                                 mlib_u8  *pMask,
                                                 mlib_s32 width,
                                                 mlib_s32 *log_val,
                                                 mlib_s32 extraA,
                                                 mlib_s32 dstF_0,
                                                 mlib_u8  *mul8_tbl)
{
    mlib_s32 i;
    mlib_s32 pathA0, dstA0, srcA0;
    mlib_d64 res0;
    mlib_f32 dstARGB0, srcARGB0;
    mlib_s32 SrcOpAnd = log_val[0];
    mlib_s32 SrcOpXor = log_val[1];
    mlib_s32 SrcOpAdd = log_val[2];
    mlib_f32 or_alpha = vis_to_float(0xff000000);

    srcA0 = extraA*256;
    dstF_0 *= 256;

#pragma pipeloop(0)
    for (i = 0; i < width; i++) {
        pathA0 = pMask[i];

        dstA0 = *(mlib_u8*)dst_ptr;
        dstARGB0 = *dst_ptr;
        srcARGB0 = *src_ptr;

        srcARGB0 = vis_fors(or_alpha, srcARGB0);

        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);

        *dst_ptr = vis_fpack16(res0);
        dst_ptr++;
        src_ptr++;
    }
}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, dstA, dstARGB, srcA, srcARGB)         \
{                                                           \
    mlib_d64 t0, t1;                                        \
    mlib_s32 srcF;                                          \
                                                            \
    srcF = ((dstA & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd;       \
                                                            \
    srcF = mul8_tbl[srcF + srcA];                           \
                                                            \
    t0 = MUL8_VIS(srcARGB, srcF);                           \
    t1 = MUL8_VIS(dstARGB, dstF_0);                         \
    rr = vis_fpadd16(t0, t1);                               \
}

/***************************************************************/

static void IntRgbToIntArgbPreAlphaMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                    mlib_f32 *src_ptr,
                                                    mlib_s32 width,
                                                    mlib_s32 *log_val,
                                                    mlib_s32 extraA,
                                                    mlib_s32 dstF_0,
                                                    mlib_u8  *mul8_tbl);

#pragma no_inline(IntRgbToIntArgbPreAlphaMaskBlit_A1_line)

static void IntRgbToIntArgbPreAlphaMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                    mlib_f32 *src_ptr,
                                                    mlib_s32 width,
                                                    mlib_s32 *log_val,
                                                    mlib_s32 extraA,
                                                    mlib_s32 dstF_0,
                                                    mlib_u8  *mul8_tbl)
{
    mlib_s32 i;
    mlib_s32 dstA0, srcA0;
    mlib_d64 res0;
    mlib_f32 dstARGB0, srcARGB0;
    mlib_s32 SrcOpAnd = log_val[0];
    mlib_s32 SrcOpXor = log_val[1];
    mlib_s32 SrcOpAdd = log_val[2];
    mlib_f32 or_alpha = vis_to_float(0xff000000);

    srcA0 = extraA*256;

#pragma pipeloop(0)
    for (i = 0; i < width; i++) {
        dstA0 = *(mlib_u8*)dst_ptr;

        dstARGB0 = *dst_ptr;
        srcARGB0 = *src_ptr;
        srcARGB0 = vis_fors(or_alpha, srcARGB0);

        MASK_FILL(res0, dstA0, dstARGB0, srcA0, srcARGB0);

        *dst_ptr = vis_fpack16(res0);

        dst_ptr++;
        src_ptr++;
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbToIntArgbPreAlphaMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_s32 extraA;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 log_val[3];
    mlib_s32 j;
    mlib_s32 SrcOpAnd;
    mlib_s32 SrcOpXor;
    mlib_s32 SrcOpAdd;
    mlib_s32 DstOpAnd;
    mlib_s32 DstOpXor;
    mlib_s32 DstOpAdd;
    mlib_s32 dstF_0;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

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

    vis_write_gsr(0 << 3);

    dstF_0 = ((extraA & DstOpAnd) ^ DstOpXor) + DstOpAdd;

    if (pMask != NULL) {
        pMask += maskOff;

        if (dstScan == 4*width && srcScan == dstScan && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntRgbToIntArgbPreAlphaMaskBlit_line(dstBase, srcBase, pMask,
                                                 width, log_val, extraA, dstF_0,
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
            IntRgbToIntArgbPreAlphaMaskBlit_A1_line(dstBase, srcBase, width,
                                                    log_val, extraA, dstF_0,
                                                    (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbToFourByteAbgrPreAlphaMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_d64 buff[BUFF_SIZE/2];
    void     *pbuff = buff;
    mlib_s32 extraA;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 log_val[3];
    mlib_s32 j;
    mlib_s32 SrcOpAnd;
    mlib_s32 SrcOpXor;
    mlib_s32 SrcOpAdd;
    mlib_s32 DstOpAnd;
    mlib_s32 DstOpXor;
    mlib_s32 DstOpAdd;
    mlib_s32 dstF_0;

    if (width > BUFF_SIZE) pbuff = mlib_malloc(width*sizeof(mlib_s32));

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

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

    vis_write_gsr(0 << 3);

    dstF_0 = ((extraA & DstOpAnd) ^ DstOpXor) + DstOpAdd;

    if (pMask != NULL) {
        pMask += maskOff;

        for (j = 0; j < height; j++) {
            ADD_SUFF(FourByteAbgrToIntArgbConvert)(dstBase, pbuff, width, 1,
                                                   pSrcInfo, pDstInfo,
                                                   pPrim, pCompInfo);

            IntRgbToIntArgbPreAlphaMaskBlit_line(pbuff, srcBase, pMask, width,
                                                 log_val, extraA, dstF_0,
                                                 (void*)mul8table);

            ADD_SUFF(IntArgbToFourByteAbgrConvert)(pbuff, dstBase, width, 1,
                                                   pSrcInfo, pDstInfo,
                                                   pPrim, pCompInfo);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        for (j = 0; j < height; j++) {
            ADD_SUFF(FourByteAbgrToIntArgbConvert)(dstBase, pbuff, width, 1,
                                                   pSrcInfo, pDstInfo,
                                                   pPrim, pCompInfo);

            IntRgbToIntArgbPreAlphaMaskBlit_A1_line(pbuff, srcBase, width,
                                                    log_val, extraA, dstF_0,
                                                    (void*)mul8table);

            ADD_SUFF(IntArgbToFourByteAbgrConvert)(pbuff, dstBase, width, 1,
                                                   pSrcInfo, pDstInfo,
                                                   pPrim, pCompInfo);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }

    if (pbuff != buff) {
        mlib_free(pbuff);
    }
}

/***************************************************************/

#endif /* JAVA2D_NO_MLIB */
