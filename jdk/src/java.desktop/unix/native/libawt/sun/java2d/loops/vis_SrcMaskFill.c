/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include <vis_AlphaMacros.h>

/***************************************************************/

/* ##############################################################
 * IntArgbSrcMaskFill()
 * FourByteAbgrSrcMaskFill()
 */

#define MASK_FILL(rr, pathA, dstA, dstARGB)         \
{                                                   \
    mlib_d64 t0, t1;                                \
                                                    \
    dstA = MUL8_INT(dstA, 0xff - pathA);            \
                                                    \
    t0 = MUL8_VIS(cnstARGB0, pathA);                \
    t1 = MUL8_VIS(dstARGB, dstA);                   \
    rr = vis_fpadd16(t0, t1);                       \
                                                    \
    dstA = dstA + mul8_cnstA[pathA];                \
    DIV_ALPHA(rr, dstA);                            \
}

/***************************************************************/

static void IntArgbSrcMaskFill_line(mlib_f32 *dst_ptr,
                                    mlib_u8  *pMask,
                                    mlib_s32 width,
                                    mlib_d64 fgARGB,
                                    mlib_f32 cnstARGB0,
                                    mlib_u8  *mul8_cnstA,
                                    mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];

        if (pathA0 == 0xff) {
            dst_ptr[i] = vis_read_hi(fgARGB);
        } else if (pathA0) {
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

        msk = (((254 - pathA0) & (1 << 11)) |
               ((254 - pathA1) & (1 << 10))) >> 10;
        vis_pst_32(fgARGB, dst_ptr + i, msk);
    }

    if (i < width) {
        pathA0 = pMask[i];

        if (pathA0 == 0xff) {
            dst_ptr[i] = vis_read_hi(fgARGB);
        } else if (pathA0) {
            dstA0 = *(mlib_u8*)(dst_ptr + i);
            dstARGB0 = dst_ptr[i];
            MASK_FILL(res0, pathA0, dstA0, dstARGB0);
            dst_ptr[i] = vis_fpack16(res0);
            *(mlib_u8*)(dst_ptr + i) = dstA0;
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbSrcMaskFill)(void *rasBase,
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
    mlib_u8  *mul8_cnstA;
    mlib_s32 j;

    cnstA = (fgColor >> 24) & 0xff;
    cnstR = (fgColor >> 16) & 0xff;
    cnstG = (fgColor >>  8) & 0xff;
    cnstB = (fgColor      ) & 0xff;

    if (cnstA == 0) {
        fgColor = 0;
    }

    if (pMask == NULL) {
        void *pBase = pRasInfo->rasBase;
        pRasInfo->rasBase = rasBase;
        ADD_SUFF(AnyIntSetRect)(pRasInfo,
                                0, 0, width, height,
                                fgColor, pPrim, pCompInfo);
        pRasInfo->rasBase = pBase;
        return;
    }

    mul8_cnstA = mul8table[cnstA];
    if (cnstA != 0xff) {
        cnstR = mul8_cnstA[cnstR];
        cnstG = mul8_cnstA[cnstG];
        cnstB = mul8_cnstA[cnstB];
    }

    cnstARGB0 = F32_FROM_U8x4(cnstA, cnstR, cnstG, cnstB);

    fgARGB = vis_to_double_dup(fgColor);

    pMask += maskOff;

    if (rasScan == 4*width && maskScan == width) {
        width *= height;
        height = 1;
    }

    vis_write_gsr(7 << 3);

    for (j = 0; j < height; j++) {
        IntArgbSrcMaskFill_line(rasBase, pMask, width, fgARGB, cnstARGB0,
                                mul8_cnstA, (void*)mul8table);

        PTR_ADD(rasBase, rasScan);
        PTR_ADD(pMask, maskScan);
    }
}

/***************************************************************/

void ADD_SUFF(FourByteAbgrSrcMaskFill)(void *rasBase,
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
    mlib_d64 fgARGB;
    mlib_u8  *mul8_cnstA;
    mlib_s32 j;

    cnstA = (mlib_u32)fgColor >> 24;
    cnstR = (fgColor >> 16) & 0xff;
    cnstG = (fgColor >>  8) & 0xff;
    cnstB = (fgColor      ) & 0xff;

    if (pMask == NULL) {
        void *pBase = pRasInfo->rasBase;
        pRasInfo->rasBase = rasBase;
        if (cnstA == 0) {
            fgColor = 0;
        } else {
            fgColor = (fgColor << 8) | cnstA;
        }
        ADD_SUFF(Any4ByteSetRect)(pRasInfo,
                                  0, 0, width, height,
                                  fgColor, pPrim, pCompInfo);
        pRasInfo->rasBase = pBase;
        return;
    }

    mul8_cnstA = mul8table[cnstA];

    if (cnstA == 0) {
        fgColor = 0;
        cnstR = cnstG = cnstB = 0;
    } else {
        fgColor = (cnstA << 24) | (cnstB << 16) | (cnstG << 8) | cnstR;
        if (cnstA != 0xff) {
            cnstR = mul8_cnstA[cnstR];
            cnstG = mul8_cnstA[cnstG];
            cnstB = mul8_cnstA[cnstB];
        }
    }

    cnstARGB0 = F32_FROM_U8x4(cnstA, cnstB, cnstG, cnstR);

    fgARGB = vis_to_double_dup(fgColor);

    pMask += maskOff;

    if (((mlib_s32)rasBase | rasScan) & 3) {
        if (width > BUFF_SIZE) pbuff = mlib_malloc(width*sizeof(mlib_s32));
    } else {
        if (rasScan == 4*width && maskScan == width) {
            width *= height;
            height = 1;
        }
    }

    vis_write_gsr(7 << 3);

    for (j = 0; j < height; j++) {
        if (!((mlib_s32)rasBase & 3)) {
            IntArgbSrcMaskFill_line(rasBase, pMask, width, fgARGB, cnstARGB0,
                                    mul8_cnstA, (void*)mul8table);
        } else {
            mlib_ImageCopy_na(rasBase, pbuff, width*sizeof(mlib_s32));
            IntArgbSrcMaskFill_line(pbuff, pMask, width, fgARGB, cnstARGB0,
                                    mul8_cnstA, (void*)mul8table);
            mlib_ImageCopy_na(pbuff, rasBase, width*sizeof(mlib_s32));
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
 * IntRgbSrcMaskFill()
 * IntBgrSrcMaskFill()
 */

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB)         \
{                                                   \
    mlib_d64 t0, t1;                                \
                                                    \
    dstA = 0xff - pathA;                            \
                                                    \
    t0 = MUL8_VIS(cnstARGB0, pathA);                \
    t1 = MUL8_VIS(dstARGB, dstA);                   \
    rr = vis_fpadd16(t0, t1);                       \
                                                    \
    dstA = dstA + mul8_cnstA[pathA];                \
    DIV_ALPHA_RGB(rr, dstA);                        \
}

/***************************************************************/

static void IntRgbSrcMaskFill_line(mlib_f32 *dst_ptr,
                                   mlib_u8  *pMask,
                                   mlib_s32 width,
                                   mlib_d64 fgARGB,
                                   mlib_f32 cnstARGB0,
                                   mlib_u8  *mul8_cnstA,
                                   mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];

        if (pathA0 == 0xff) {
            dst_ptr[i] = vis_read_hi(fgARGB);
        } else if (pathA0) {
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
        dstARGB = *(mlib_d64*)(dst_ptr + i);

        MASK_FILL(res0, pathA0, dstA0, vis_read_hi(dstARGB));
        MASK_FILL(res1, pathA1, dstA1, vis_read_lo(dstARGB));

        res0 = vis_fpack16_pair(res0, res1);

        msk = (((-pathA0) & (1 << 11)) | ((-pathA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);

        msk = (((254 - pathA0) & (1 << 11)) |
               ((254 - pathA1) & (1 << 10))) >> 10;
        vis_pst_32(fgARGB, dst_ptr + i, msk);
    }

    if (i < width) {
        pathA0 = pMask[i];

        if (pathA0 == 0xff) {
            dst_ptr[i] = vis_read_hi(fgARGB);
        } else if (pathA0) {
            dstARGB0 = dst_ptr[i];
            MASK_FILL(res0, pathA0, dstA0, dstARGB0);
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbSrcMaskFill)(void *rasBase,
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
    mlib_u8  *mul8_cnstA;
    mlib_s32 j;

    cnstA = (fgColor >> 24) & 0xff;
    cnstR = (fgColor >> 16) & 0xff;
    cnstG = (fgColor >>  8) & 0xff;
    cnstB = (fgColor      ) & 0xff;

    if (cnstA == 0) fgColor = 0;

    if (pMask == NULL) {
        void *pBase = pRasInfo->rasBase;
        pRasInfo->rasBase = rasBase;
        ADD_SUFF(AnyIntSetRect)(pRasInfo,
                                0, 0, width, height,
                                fgColor, pPrim, pCompInfo);
        pRasInfo->rasBase = pBase;
        return;
    }

    mul8_cnstA = mul8table[cnstA];
    if (cnstA != 0xff) {
        cnstR = mul8_cnstA[cnstR];
        cnstG = mul8_cnstA[cnstG];
        cnstB = mul8_cnstA[cnstB];
    }

    cnstARGB0 = F32_FROM_U8x4(cnstA, cnstR, cnstG, cnstB);

    fgARGB = vis_to_double_dup(fgColor);

    pMask += maskOff;

    if (rasScan == 4*width && maskScan == width) {
        width *= height;
        height = 1;
    }

    vis_write_gsr(7 << 3);

    for (j = 0; j < height; j++) {
        IntRgbSrcMaskFill_line(rasBase, pMask, width, fgARGB, cnstARGB0,
                               mul8_cnstA, (void*)mul8table);

        PTR_ADD(rasBase, rasScan);
        PTR_ADD(pMask, maskScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntBgrSrcMaskFill)(void *rasBase,
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
    mlib_u8  *mul8_cnstA;
    mlib_s32 j;

    cnstA = (fgColor >> 24) & 0xff;
    cnstR = (fgColor >> 16) & 0xff;
    cnstG = (fgColor >>  8) & 0xff;
    cnstB = (fgColor      ) & 0xff;

    if (cnstA == 0) {
        fgColor = 0;
    } else {
        fgColor = (cnstB << 16) | (cnstG << 8) | (cnstR);
    }

    if (pMask == NULL) {
        void *pBase = pRasInfo->rasBase;
        pRasInfo->rasBase = rasBase;
        ADD_SUFF(AnyIntSetRect)(pRasInfo,
                                0, 0, width, height,
                                fgColor, pPrim, pCompInfo);
        pRasInfo->rasBase = pBase;
        return;
    }

    mul8_cnstA = mul8table[cnstA];
    if (cnstA != 0xff) {
        cnstR = mul8_cnstA[cnstR];
        cnstG = mul8_cnstA[cnstG];
        cnstB = mul8_cnstA[cnstB];
    }

    cnstARGB0 = F32_FROM_U8x4(cnstA, cnstB, cnstG, cnstR);

    fgARGB = vis_to_double_dup(fgColor);

    pMask += maskOff;

    if (rasScan == 4*width && maskScan == width) {
        width *= height;
        height = 1;
    }

    vis_write_gsr(7 << 3);

    for (j = 0; j < height; j++) {
        IntRgbSrcMaskFill_line(rasBase, pMask, width, fgARGB, cnstARGB0,
                               mul8_cnstA, (void*)mul8table);

        PTR_ADD(rasBase, rasScan);
        PTR_ADD(pMask, maskScan);
    }
}

/***************************************************************/

void ADD_SUFF(ThreeByteBgrSrcMaskFill)(void *rasBase,
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
    mlib_d64 fgARGB;
    mlib_u8  *mul8_cnstA;
    mlib_s32 j;

    cnstA = (fgColor >> 24) & 0xff;
    cnstR = (fgColor >> 16) & 0xff;
    cnstG = (fgColor >>  8) & 0xff;
    cnstB = (fgColor      ) & 0xff;

    if (cnstA == 0) {
        fgColor = 0;
    }

    if (pMask == NULL) {
        void *pBase = pRasInfo->rasBase;
        pRasInfo->rasBase = rasBase;
        ADD_SUFF(Any3ByteSetRect)(pRasInfo,
                                  0, 0, width, height,
                                  fgColor, pPrim, pCompInfo);
        pRasInfo->rasBase = pBase;
        return;
    }

    mul8_cnstA = mul8table[cnstA];
    if (cnstA != 0xff) {
        cnstR = mul8_cnstA[cnstR];
        cnstG = mul8_cnstA[cnstG];
        cnstB = mul8_cnstA[cnstB];
    }

    cnstARGB0 = F32_FROM_U8x4(cnstA, cnstR, cnstG, cnstB);

    fgARGB = vis_to_double_dup(fgColor);

    pMask += maskOff;

    if (width > BUFF_SIZE) pbuff = mlib_malloc(width*sizeof(mlib_s32));

    vis_write_gsr(7 << 3);

    for (j = 0; j < height; j++) {
        ADD_SUFF(ThreeByteBgrToIntArgbConvert)(rasBase, pbuff, width, 1,
                                               pRasInfo, pRasInfo,
                                               pPrim, pCompInfo);

        IntRgbSrcMaskFill_line(pbuff, pMask, width, fgARGB, cnstARGB0,
                               mul8_cnstA, (void*)mul8table);

        IntArgbToThreeByteBgrConvert(pbuff, rasBase, width, 1,
                                     pRasInfo, pRasInfo, pPrim, pCompInfo);

        PTR_ADD(rasBase, rasScan);
        PTR_ADD(pMask, maskScan);
    }

    if (pbuff != buff) {
        mlib_free(pbuff);
    }
}

/***************************************************************/

#endif
