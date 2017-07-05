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

#include <vis_AlphaMacros.h>

/***************************************************************/

/* ##############################################################
 * IntArgbSrcOverMaskFill()
 * FourByteAbgrSrcOverMaskFill()
 */

#define MASK_FILL(rr, pathA, dstA, dstARGB)    \
{                                              \
    mlib_d64 t0, t1;                           \
    mlib_s32 alp0;                             \
                                               \
    alp0 = mul8_cnstA[pathA];                  \
    dstA = MUL8_INT(dstA, 0xff - alp0);        \
                                               \
    t0 = MUL8_VIS(cnstARGB0, pathA);           \
    t1 = MUL8_VIS(dstARGB, dstA);              \
    rr = vis_fpadd16(t0, t1);                  \
                                               \
    dstA = dstA + alp0;                        \
    DIV_ALPHA(rr, dstA);                       \
}

/***************************************************************/

static void IntArgbSrcOverMaskFill_line(mlib_f32 *dst_ptr,
                                        mlib_u8  *pMask,
                                        mlib_s32 width,
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
#define MASK_FILL(rr, pathA, dstA, dstARGB)    \
{                                              \
    dstA = mul8_cnstA[dstA];                   \
                                               \
    rr = MUL8_VIS(dstARGB, dstA);              \
    rr = vis_fpadd16(rr, cnstARGB);            \
                                               \
    dstA = dstA + cnstA;                       \
    DIV_ALPHA(rr, dstA);                       \
}

/***************************************************************/

static void IntArgbSrcOverMaskFill_A1_line(mlib_f32 *dst_ptr,
                                           mlib_u8  *pMask,
                                           mlib_s32 width,
                                           mlib_d64 cnstARGB,
                                           mlib_s32 cnstA,
                                           mlib_u8  *mul8_cnstA,
                                           mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 dstA0, dstA1;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        {
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
        dstA0 = *(mlib_u8*)(dst_ptr + i);
        dstA1 = *(mlib_u8*)(dst_ptr + i + 1);
        dstARGB = *(mlib_d64*)(dst_ptr + i);

        MASK_FILL(res0, pathA0, dstA0, vis_read_hi(dstARGB));
        MASK_FILL(res1, pathA1, dstA1, vis_read_lo(dstARGB));

        res0 = vis_fpack16_pair(res0, res1);

        *(mlib_d64*)(dst_ptr + i) = res0;

        *(mlib_u8*)(dst_ptr + i    ) = dstA0;
        *(mlib_u8*)(dst_ptr + i + 1) = dstA1;
    }

    if (i < width) {
        {
            dstA0 = *(mlib_u8*)(dst_ptr + i);
            dstARGB0 = dst_ptr[i];
            MASK_FILL(res0, pathA0, dstA0, dstARGB0);
            dst_ptr[i] = vis_fpack16(res0);
            *(mlib_u8*)(dst_ptr + i) = dstA0;
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbSrcOverMaskFill)(void *rasBase,
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

    vis_write_gsr(7 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        if (rasScan == 4*width && maskScan == width) {
            width *= height;
            height = 1;
        }

        mul8_cnstA = mul8table[cnstA];

        cnstARGB0 = F32_FROM_U8x4(cnstA, cnstR, cnstG, cnstB);

        for (j = 0; j < height; j++) {
            IntArgbSrcOverMaskFill_line(rasBase, pMask, width, cnstARGB0,
                                        mul8_cnstA, (void*)mul8table);

            PTR_ADD(rasBase, rasScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        if (rasScan == 4*width) {
            width *= height;
            height = 1;
        }

        mul8_cnstA = mul8table[255 - cnstA];

        cnstARGB = vis_to_double((cnstA << 23) | (cnstR << 7),
                                 (cnstG << 23) | (cnstB << 7));

        for (j = 0; j < height; j++) {
            IntArgbSrcOverMaskFill_A1_line(rasBase, pMask, width, cnstARGB,
                                           cnstA,mul8_cnstA, (void*)mul8table);

            PTR_ADD(rasBase, rasScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(FourByteAbgrSrcOverMaskFill)(void *rasBase,
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
    mlib_d64 cnstARGB;
    mlib_u8  *mul8_cnstA;
    mlib_s32 j;

    cnstA = (mlib_u32)fgColor >> 24;
    cnstR = (fgColor >> 16) & 0xff;
    cnstG = (fgColor >>  8) & 0xff;
    cnstB = (fgColor      ) & 0xff;

    if (cnstA != 0xff) {
        if (cnstA == 0) return;

        cnstR = mul8table[cnstA][cnstR];
        cnstG = mul8table[cnstA][cnstG];
        cnstB = mul8table[cnstA][cnstB];
    }

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

        mul8_cnstA = mul8table[cnstA];

        cnstARGB0 = F32_FROM_U8x4(cnstA, cnstB, cnstG, cnstR);

        for (j = 0; j < height; j++) {
            if (!((mlib_s32)rasBase & 3)) {
                IntArgbSrcOverMaskFill_line(rasBase, pMask, width, cnstARGB0,
                                            mul8_cnstA, (void*)mul8table);
            } else {
                mlib_ImageCopy_na(rasBase, pbuff, width*sizeof(mlib_s32));
                IntArgbSrcOverMaskFill_line(pbuff, pMask, width, cnstARGB0,
                                            mul8_cnstA, (void*)mul8table);
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

        mul8_cnstA = mul8table[255 - cnstA];

        cnstARGB = vis_to_double((cnstA << 23) | (cnstB << 7),
                                 (cnstG << 23) | (cnstR << 7));

        for (j = 0; j < height; j++) {
            if (!((mlib_s32)rasBase & 3)) {
                IntArgbSrcOverMaskFill_A1_line(rasBase, pMask, width, cnstARGB,
                                               cnstA, mul8_cnstA,
                                               (void*)mul8table);
            } else {
                mlib_ImageCopy_na(rasBase, pbuff, width*sizeof(mlib_s32));
                IntArgbSrcOverMaskFill_A1_line(pbuff, pMask, width, cnstARGB,
                                               cnstA, mul8_cnstA,
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
 * IntRgbSrcOverMaskFill()
 * IntBgrSrcOverMaskFill()
 */

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB)    \
{                                              \
    mlib_d64 t0, t1;                           \
    mlib_s32 srcA;                             \
                                               \
    srcA = mul8_cnstA[pathA];                  \
    dstA  = 0xff - srcA;                       \
                                               \
    t0 = MUL8_VIS(cnstARGB0, pathA);           \
    t1 = MUL8_VIS(dstARGB, dstA);              \
    rr = vis_fpadd16(t0, t1);                  \
    rr = vis_fand(rr, maskRGB);                \
}

/***************************************************************/

static void IntRgbSrcOverMaskFill_line(mlib_f32 *dst_ptr,
                                       mlib_u8  *pMask,
                                       mlib_s32 width,
                                       mlib_f32 cnstARGB0,
                                       mlib_u8  *mul8_cnstA,
                                       mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;
    /*mlib_d64 maskRGB = vis_to_double_dup(0x00FFFFFF);*/
    mlib_d64 maskRGB = vis_to_double(0x0000FFFF, 0xFFFFFFFF);

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];

        if (pathA0) {
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
    }

    if (i < width) {
        pathA0 = pMask[i];

        if (pathA0) {
            dstARGB0 = dst_ptr[i];
            MASK_FILL(res0, pathA0, dstA0, dstARGB0);
            dst_ptr[i] = vis_fpack16(res0);
        }
    }
}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB)    \
{                                              \
    rr = MUL8_VIS(dstARGB, cnstA);             \
    rr = vis_fpadd16(rr, cnstARGB);            \
    rr = vis_fand(rr, maskRGB);                \
}

/***************************************************************/

static void IntRgbSrcOverMaskFill_A1_line(mlib_f32 *dst_ptr,
                                          mlib_u8  *pMask,
                                          mlib_s32 width,
                                          mlib_d64 cnstARGB,
                                          mlib_s32 cnstA,
                                          mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;
    mlib_d64 maskRGB = vis_to_double(0x0000FFFF, 0xFFFFFFFF);

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        dstARGB0 = dst_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0);
        dst_ptr[i] = vis_fpack16(res0);

        i0 = 1;
    }

#pragma pipeloop(0)
    for (i = i0; i <= width - 2; i += 2) {
        dstARGB = *(mlib_d64*)(dst_ptr + i);

        MASK_FILL(res0, pathA0, dstA0, vis_read_hi(dstARGB));
        MASK_FILL(res1, pathA1, dstA1, vis_read_lo(dstARGB));

        res0 = vis_fpack16_pair(res0, res1);

        *(mlib_d64*)(dst_ptr + i) = res0;
    }

    if (i < width) {
        dstARGB0 = dst_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0);
        dst_ptr[i] = vis_fpack16(res0);
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbSrcOverMaskFill)(void *rasBase,
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
            IntRgbSrcOverMaskFill_line(rasBase, pMask, width, cnstARGB0,
                                       mul8_cnstA, (void*)mul8table);

            PTR_ADD(rasBase, rasScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        if (rasScan == 4*width) {
            width *= height;
            height = 1;
        }

        cnstARGB = vis_to_double((cnstR << 7), (cnstG << 23) | (cnstB << 7));

        cnstA = 255 - cnstA;

        for (j = 0; j < height; j++) {
            IntRgbSrcOverMaskFill_A1_line(rasBase, pMask, width, cnstARGB,
                                          cnstA, (void*)mul8table);

            PTR_ADD(rasBase, rasScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntBgrSrcOverMaskFill)(void *rasBase,
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

        cnstARGB0 = F32_FROM_U8x4(cnstA, cnstB, cnstG, cnstR);

        for (j = 0; j < height; j++) {
            IntRgbSrcOverMaskFill_line(rasBase, pMask, width, cnstARGB0,
                                       mul8_cnstA, (void*)mul8table);

            PTR_ADD(rasBase, rasScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        if (rasScan == 4*width) {
            width *= height;
            height = 1;
        }

        cnstARGB = vis_to_double((cnstB << 7), (cnstG << 23) | (cnstR << 7));

        cnstA = 255 - cnstA;

        for (j = 0; j < height; j++) {
            IntRgbSrcOverMaskFill_A1_line(rasBase, pMask, width, cnstARGB,
                                          cnstA, (void*)mul8table);

            PTR_ADD(rasBase, rasScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(ThreeByteBgrSrcOverMaskFill)(void *rasBase,
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

        cnstARGB0 = F32_FROM_U8x4(cnstA, cnstR, cnstG, cnstB);

        for (j = 0; j < height; j++) {
            ADD_SUFF(ThreeByteBgrToIntArgbConvert)(rasBase, pbuff, width, 1,
                                                   pRasInfo, pRasInfo,
                                                   pPrim, pCompInfo);

            IntRgbSrcOverMaskFill_line(pbuff, pMask, width, cnstARGB0,
                                       mul8_cnstA, (void*)mul8table);

            IntArgbToThreeByteBgrConvert(pbuff, rasBase, width, 1,
                                         pRasInfo, pRasInfo, pPrim, pCompInfo);

            PTR_ADD(rasBase, rasScan);
            PTR_ADD(pMask, maskScan);
        }
    } else {
        cnstARGB = vis_to_double((cnstR << 7), (cnstG << 23) | (cnstB << 7));

        cnstA = 255 - cnstA;

        for (j = 0; j < height; j++) {
            ADD_SUFF(ThreeByteBgrToIntArgbConvert)(rasBase, pbuff, width, 1,
                                                   pRasInfo, pRasInfo,
                                                   pPrim, pCompInfo);

            IntRgbSrcOverMaskFill_A1_line(pbuff, pMask, width, cnstARGB,
                                          cnstA, (void*)mul8table);

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

#endif
