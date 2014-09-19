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
 * IntArgbToIntArgbSrcOverMaskBlit()
 * IntArgbToFourByteAbgrSrcOverMaskBlit()
 */

#define MASK_FILL(rr, pathA, dstA, dstARGB, srcA, srcARGB)     \
{                                                              \
    mlib_d64 t0, t1;                                           \
                                                               \
    srcA = MUL8_INT(srcA, mul8_extra[pathA]);                  \
    dstA = MUL8_INT(dstA, 0xff - srcA);                        \
                                                               \
    t0 = MUL8_VIS(srcARGB, srcA);                              \
    t1 = MUL8_VIS(dstARGB, dstA);                              \
    rr = vis_fpadd16(t0, t1);                                  \
                                                               \
    dstA += srcA;                                              \
    DIV_ALPHA(rr, dstA);                                       \
}

/***************************************************************/

static void IntArgbToIntArgbSrcOverMaskBlit_line(mlib_f32 *dst_ptr,
                                                 mlib_f32 *src_ptr,
                                                 mlib_u8  *pMask,
                                                 mlib_s32 width,
                                                 mlib_u8  *mul8_extra,
                                                 mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, dstA0, dstA1, srcA0, srcA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0, srcARGB0, srcARGB1;

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];
        dstA0 = *(mlib_u8*)(dst_ptr + i);
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (srcA0) {
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

        msk = (((-srcA0) & (1 << 11)) | ((-srcA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);

        *(mlib_u8*)(dst_ptr + i    ) = dstA0;
        *(mlib_u8*)(dst_ptr + i + 1) = dstA1;
    }

    if (i < width) {
        pathA0 = pMask[i];
        dstA0 = *(mlib_u8*)(dst_ptr + i);
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (srcA0) {
            dst_ptr[i] = vis_fpack16(res0);
            *(mlib_u8*)(dst_ptr + i) = dstA0;
        }
    }
}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB, srcA, srcARGB)     \
{                                                              \
    mlib_d64 t0, t1;                                           \
                                                               \
    srcA = mul8_extra[srcA];                                   \
    dstA = MUL8_INT(dstA, 0xff - srcA);                        \
                                                               \
    t0 = MUL8_VIS(srcARGB, srcA);                              \
    t1 = MUL8_VIS(dstARGB, dstA);                              \
    rr = vis_fpadd16(t0, t1);                                  \
                                                               \
    dstA += srcA;                                              \
    DIV_ALPHA(rr, dstA);                                       \
}

/***************************************************************/

static void IntArgbToIntArgbSrcOverMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                    mlib_f32 *src_ptr,
                                                    mlib_u8  *pMask,
                                                    mlib_s32 width,
                                                    mlib_u8  *mul8_extra,
                                                    mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 dstA0, dstA1, srcA0, srcA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0, srcARGB0, srcARGB1;

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        dstA0 = *(mlib_u8*)(dst_ptr + i);
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (srcA0) {
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
        srcA0 = *(mlib_u8*)(src_ptr + i);
        srcA1 = *(mlib_u8*)(src_ptr + i + 1);
        srcARGB0 = src_ptr[i];
        srcARGB1 = src_ptr[i + 1];

        MASK_FILL(res0, pathA0, dstA0, vis_read_hi(dstARGB), srcA0, srcARGB0);
        MASK_FILL(res1, pathA1, dstA1, vis_read_lo(dstARGB), srcA1, srcARGB1);

        res0 = vis_fpack16_pair(res0, res1);

        msk = (((-srcA0) & (1 << 11)) | ((-srcA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);

        *(mlib_u8*)(dst_ptr + i    ) = dstA0;
        *(mlib_u8*)(dst_ptr + i + 1) = dstA1;
    }

    if (i < width) {
        dstA0 = *(mlib_u8*)(dst_ptr + i);
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (srcA0) {
            dst_ptr[i] = vis_fpack16(res0);
            *(mlib_u8*)(dst_ptr + i) = dstA0;
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntArgbSrcOverMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_s32 extraA;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *mul8_extra;
    mlib_s32 j;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

    mul8_extra = mul8table[extraA];

    vis_write_gsr(7 << 3);

    if (pMask != NULL) {
        pMask += maskOff;

        if (dstScan == 4*width && srcScan == dstScan && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            IntArgbToIntArgbSrcOverMaskBlit_line(dstBase, srcBase, pMask,
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
            IntArgbToIntArgbSrcOverMaskBlit_A1_line(dstBase, srcBase, pMask,
                                                    width, mul8_extra,
                                                    (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToFourByteAbgrSrcOverMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_d64 buff[BUFF_SIZE/2];
    void     *src_buff = buff, *dst_buff;
    mlib_s32 extraA;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *mul8_extra;
    mlib_s32 j;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

    mul8_extra = mul8table[extraA];

    vis_write_gsr(7 << 3);

    if (2*width > BUFF_SIZE) src_buff = mlib_malloc(2*width*sizeof(mlib_s32));
    dst_buff = (mlib_s32*)src_buff + width;

    if (pMask != NULL) {
        pMask += maskOff;

        for (j = 0; j < height; j++) {
            IntArgbToIntAbgrConvert_line(srcBase, src_buff, width);
            if (!((mlib_s32)dstBase & 3)) {
                IntArgbToIntArgbSrcOverMaskBlit_line(dstBase, src_buff, pMask,
                                                     width, mul8_extra,
                                                     (void*)mul8table);
            } else {
                mlib_ImageCopy_na(dstBase, dst_buff, width*sizeof(mlib_s32));
                IntArgbToIntArgbSrcOverMaskBlit_line(dst_buff, src_buff, pMask,
                                                     width, mul8_extra,
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
                IntArgbToIntArgbSrcOverMaskBlit_A1_line(dstBase, src_buff,
                                                        pMask, width,
                                                        mul8_extra,
                                                        (void*)mul8table);
            } else {
                mlib_ImageCopy_na(dstBase, dst_buff, width*sizeof(mlib_s32));
                IntArgbToIntArgbSrcOverMaskBlit_A1_line(dst_buff, src_buff,
                                                        pMask, width,
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
 * IntArgbToIntRgbSrcOverMaskBlit()
 * IntArgbToIntBgrSrcOverMaskBlit()
 */

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB, srcA, srcARGB)     \
{                                                              \
    mlib_d64 t0, t1;                                           \
    mlib_f32 srcAf, dstAf;                                     \
                                                               \
    srcA = MUL8_INT(srcA, mul8_extra[pathA]);                  \
    srcAf = ((mlib_f32 *)vis_mul8s_tbl)[srcA];                 \
    dstAf = vis_fpsub16s(cnst1, srcAf);                        \
                                                               \
    t0 = vis_fmul8x16al(srcARGB, srcAf);                       \
    t1 = vis_fmul8x16al(dstARGB, dstAf);                       \
    rr = vis_fpadd16(t0, t1);                                  \
}

/***************************************************************/

static void IntArgbToIntRgbSrcOverMaskBlit_line(mlib_f32 *dst_ptr,
                                                mlib_f32 *src_ptr,
                                                mlib_u8  *pMask,
                                                mlib_s32 width,
                                                mlib_u8  *mul8_extra,
                                                mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, srcA0, srcA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0, srcARGB0, srcARGB1;
    mlib_d64 maskRGB = vis_to_double_dup(0x00FFFFFF);
    mlib_f32 cnst1 = vis_to_float(0x8000);

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        pathA0 = pMask[i];
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (srcA0) {
            dst_ptr[i] = vis_fands(vis_fpack16(res0), vis_read_hi(maskRGB));
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
        res0 = vis_fand(res0, maskRGB);

        msk = (((-srcA0) & (1 << 11)) | ((-srcA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);
    }

    if (i < width) {
        pathA0 = pMask[i];
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (srcA0) {
            dst_ptr[i] = vis_fands(vis_fpack16(res0), vis_read_hi(maskRGB));
        }
    }
}

/***************************************************************/

static void IntArgbToIntBgrSrcOverMaskBlit_line(mlib_f32 *dst_ptr,
                                                mlib_f32 *src_ptr,
                                                mlib_u8  *pMask,
                                                mlib_s32 width,
                                                mlib_u8  *mul8_extra,
                                                mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 pathA0, pathA1, srcA0, srcA1, msk;
    mlib_d64 res0, res1, dstARGB, srcARGB;
    mlib_f32 dstARGB0, srcARGB0;
    mlib_d64 maskRGB = vis_to_double_dup(0x00FFFFFF);
    mlib_f32 cnst1 = vis_to_float(0x8000);

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
        if (srcA0) {
            dst_ptr[i] = vis_fands(vis_fpack16(res0), vis_read_hi(maskRGB));
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
        res0 = vis_fand(res0, maskRGB);

        msk = (((-srcA0) & (1 << 11)) | ((-srcA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);
    }

    if (i < width) {
        pathA0 = pMask[i];
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        ARGB2ABGR_FL(srcARGB0)
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (srcA0) {
            dst_ptr[i] = vis_fands(vis_fpack16(res0), vis_read_hi(maskRGB));
        }
    }
}

/***************************************************************/

#undef  MASK_FILL
#define MASK_FILL(rr, pathA, dstA, dstARGB, srcA, srcARGB)     \
{                                                              \
    mlib_d64 t0, t1;                                           \
    mlib_f32 srcAf, dstAf;                                     \
                                                               \
    srcA = mul8_extra[srcA];                                   \
    srcAf = ((mlib_f32 *)vis_mul8s_tbl)[srcA];                 \
    dstAf = vis_fpsub16s(cnst1, srcAf);                        \
                                                               \
    t0 = vis_fmul8x16al(srcARGB, srcAf);                       \
    t1 = vis_fmul8x16al(dstARGB, dstAf);                       \
    rr = vis_fpadd16(t0, t1);                                  \
}

/***************************************************************/

static void IntArgbToIntRgbSrcOverMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                   mlib_f32 *src_ptr,
                                                   mlib_u8  *pMask,
                                                   mlib_s32 width,
                                                   mlib_u8  *mul8_extra,
                                                   mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 srcA0, srcA1, msk;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0, srcARGB0, srcARGB1;
    mlib_d64 maskRGB = vis_to_double_dup(0x00FFFFFF);
    mlib_f32 cnst1 = vis_to_float(0x8000);

    i = i0 = 0;

    if ((mlib_s32)dst_ptr & 7) {
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (srcA0) {
            dst_ptr[i] = vis_fands(vis_fpack16(res0), vis_read_hi(maskRGB));
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
        res0 = vis_fand(res0, maskRGB);

        msk = (((-srcA0) & (1 << 11)) | ((-srcA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);
    }

    if (i < width) {
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (srcA0) {
            dst_ptr[i] = vis_fands(vis_fpack16(res0), vis_read_hi(maskRGB));
        }
    }
}

/***************************************************************/

static void IntArgbToIntBgrSrcOverMaskBlit_A1_line(mlib_f32 *dst_ptr,
                                                   mlib_f32 *src_ptr,
                                                   mlib_u8  *pMask,
                                                   mlib_s32 width,
                                                   mlib_u8  *mul8_extra,
                                                   mlib_u8  *mul8_tbl)
{
    mlib_s32 i, i0;
    mlib_s32 srcA0, srcA1, msk;
    mlib_d64 res0, res1, dstARGB, srcARGB;
    mlib_f32 dstARGB0, srcARGB0;
    mlib_d64 maskRGB = vis_to_double_dup(0x00FFFFFF);
    mlib_f32 cnst1 = vis_to_float(0x8000);

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
        if (srcA0) {
            dst_ptr[i] = vis_fands(vis_fpack16(res0), vis_read_hi(maskRGB));
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
        res0 = vis_fand(res0, maskRGB);

        msk = (((-srcA0) & (1 << 11)) | ((-srcA1) & (1 << 10))) >> 10;
        vis_pst_32(res0, dst_ptr + i, msk);
    }

    if (i < width) {
        srcA0 = *(mlib_u8*)(src_ptr + i);
        dstARGB0 = dst_ptr[i];
        srcARGB0 = src_ptr[i];
        ARGB2ABGR_FL(srcARGB0)
        MASK_FILL(res0, pathA0, dstA0, dstARGB0, srcA0, srcARGB0);
        if (srcA0) {
            dst_ptr[i] = vis_fands(vis_fpack16(res0), vis_read_hi(maskRGB));
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntRgbSrcOverMaskBlit)(MASKBLIT_PARAMS)
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
            IntArgbToIntRgbSrcOverMaskBlit_line(dstBase, srcBase, pMask,
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
            IntArgbToIntRgbSrcOverMaskBlit_A1_line(dstBase, srcBase, pMask,
                                                    width, mul8_extra,
                                                    (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntBgrSrcOverMaskBlit)(MASKBLIT_PARAMS)
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
            IntArgbToIntBgrSrcOverMaskBlit_line(dstBase, srcBase, pMask,
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
            IntArgbToIntBgrSrcOverMaskBlit_A1_line(dstBase, srcBase, pMask,
                                                    width, mul8_extra,
                                                    (void*)mul8table);

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}

/***************************************************************/

#endif
