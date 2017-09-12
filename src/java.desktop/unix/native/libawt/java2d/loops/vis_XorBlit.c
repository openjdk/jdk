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

#include <vis_proto.h>
#include "java2d_Mlib.h"

/***************************************************************/

#define ARGB_XOR(index, chan)                                                \
{                                                                            \
    jint srcpixel = src_ptr[index];                                          \
    jint neg_mask = srcpixel >> 31;                                          \
    dst_ptr[index] ^= (srcpixel ^ xorpixel) & (neg_mask &~ alphamask);       \
}

/***************************************************************/

#define BGR_XOR(index, chan)                                                 \
{                                                                            \
    jint srcpixel = src_ptr[index];                                          \
    jint neg_mask = srcpixel >> 31;                                          \
    srcpixel = (srcpixel << 16) | (srcpixel & 0xff00) |                      \
               ((srcpixel >> 16) & 0xff);                                    \
    dst_ptr[index] ^= (srcpixel ^ xorpixel) & (neg_mask &~ alphamask);       \
}

/***************************************************************/

#define ARGB_BM_XOR(index, chan)                                             \
{                                                                            \
    jint srcpixel = src_ptr[index];                                          \
    jint neg_mask = srcpixel >> 31;                                          \
    srcpixel |= 0xFF000000;                                                  \
    dst_ptr[index] ^= (srcpixel ^ xorpixel) & (neg_mask &~ alphamask);       \
}

/***************************************************************/

#define RGBX_XOR(index, chan)                          \
{                                                      \
    jint srcpixel = src_ptr[index];                    \
    jint neg_mask = srcpixel >> 31;                    \
    dst_ptr[index] ^= ((srcpixel << 8) ^ xorpixel) &   \
                      (neg_mask &~ alphamask);         \
}

/***************************************************************/

#define ARGB_to_GBGR_FL2(dst, src0, src1) {                    \
    mlib_d64 t0, t1, t2;                                       \
    t0 = vis_fpmerge(src0, src1);                              \
    t1 = vis_fpmerge(vis_read_lo(t0), vis_read_hi(t0));        \
    t2 = vis_fpmerge(vis_read_lo(t0), vis_read_lo(t0));        \
    dst = vis_fpmerge(vis_read_hi(t2), vis_read_lo(t1));       \
}

/***************************************************************/

#ifdef MLIB_ADD_SUFF
#pragma weak IntArgbToIntRgbXorBlit_F = IntArgbToIntArgbXorBlit_F
#else
#pragma weak IntArgbToIntRgbXorBlit   = IntArgbToIntArgbXorBlit
#endif

/***************************************************************/

void ADD_SUFF(IntArgbToIntArgbXorBlit)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 xorpixel = pCompInfo->details.xorPixel;
    mlib_s32 alphamask = pCompInfo->alphaMask;
    mlib_s32 i, j;
    mlib_d64 res, xorpixel64, alphamask64, dzero;

    if (width < 8) {
        LOOP_DST_SRC(AnyInt, 1, dstBase, dstScan, srcBase, srcScan, ARGB_XOR);
        return;
    }

    if (dstScan == 4*width && srcScan == dstScan) {
        width *= height;
        height = 1;
    }

    xorpixel64 = vis_to_double_dup(xorpixel);
    alphamask64 = vis_to_double_dup(alphamask);
    dzero = vis_fzero();

    for (j = 0; j < height; j++) {
        mlib_s32 *dst_ptr = dstBase;
        mlib_s32 *src_ptr = srcBase;
        mlib_s32 size = width;

        if ((mlib_s32)dst_ptr & 7) {
            ARGB_XOR(0, 0);
            dst_ptr++;
            src_ptr++;
            size--;
        }

#pragma pipeloop(0)
        for (i = 0; i <= size - 2; i += 2) {
            mlib_s32 neg_mask;
            mlib_f32 *pp0 = (mlib_f32*)src_ptr + i;
            mlib_f32 *pp1 = (mlib_f32*)src_ptr + i + 1;
            neg_mask = (((*(mlib_u8*)pp0) >> 6) & 2) | ((*(mlib_u8*)pp1) >> 7);
            res = vis_freg_pair(*pp0, *pp1);
            res = vis_fxor(res, xorpixel64);
            res = vis_fandnot(alphamask64, res);
            res = vis_fxor(res, *(mlib_d64*)(dst_ptr + i));
            vis_pst_32(res, dst_ptr + i, neg_mask);
        }

        if (i < size) {
            ARGB_XOR(i, 0);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntBgrXorBlit)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 xorpixel = pCompInfo->details.xorPixel;
    mlib_s32 alphamask = pCompInfo->alphaMask;
    mlib_s32 i, j;
    mlib_d64 res, xorpixel64, alphamask64, dzero;

    if (width < 8) {
        LOOP_DST_SRC(AnyInt, 1, dstBase, dstScan, srcBase, srcScan, BGR_XOR);
        return;
    }

    if (dstScan == 4*width && srcScan == dstScan) {
        width *= height;
        height = 1;
    }

    xorpixel64 = vis_to_double_dup(xorpixel);
    alphamask64 = vis_to_double_dup(alphamask);
    dzero = vis_fzero();

    for (j = 0; j < height; j++) {
        mlib_s32 *dst_ptr = dstBase;
        mlib_s32 *src_ptr = srcBase;
        mlib_s32 size = width;

        if ((mlib_s32)dst_ptr & 7) {
            BGR_XOR(0, 0);
            dst_ptr++;
            src_ptr++;
            size--;
        }

#pragma pipeloop(0)
        for (i = 0; i <= size - 2; i += 2) {
            mlib_s32 neg_mask;
            mlib_f32 *pp0 = (mlib_f32*)src_ptr + i;
            mlib_f32 *pp1 = (mlib_f32*)src_ptr + i + 1;
            neg_mask = (((*(mlib_u8*)pp0) >> 6) & 2) | ((*(mlib_u8*)pp1) >> 7);
            ARGB_to_GBGR_FL2(res, *pp0, *pp1);
            res = vis_fxor(res, xorpixel64);
            res = vis_fandnot(alphamask64, res);
            res = vis_fxor(res, *(mlib_d64*)(dst_ptr + i));
            vis_pst_32(res, dst_ptr + i, neg_mask);
        }

        if (i < size) {
            BGR_XOR(i, 0);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntArgbBmXorBlit)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 xorpixel = pCompInfo->details.xorPixel;
    mlib_s32 alphamask = pCompInfo->alphaMask;
    mlib_s32 i, j, neg_mask;
    mlib_d64 res, xorpixel64, alphamask64, dzero, dFF;

    if (width < 8) {
        LOOP_DST_SRC(AnyInt, 1, dstBase, dstScan, srcBase, srcScan,
                     ARGB_BM_XOR);
        return;
    }

    if (dstScan == 4*width && srcScan == dstScan) {
        width *= height;
        height = 1;
    }

    xorpixel64 = vis_to_double_dup(xorpixel);
    alphamask64 = vis_to_double_dup(alphamask);
    dzero = vis_fzero();
    dFF = vis_to_double_dup(0xFF000000);

    for (j = 0; j < height; j++) {
        mlib_s32 *dst_ptr = dstBase;
        mlib_s32 *src_ptr = srcBase;
        mlib_s32 size = width;

        if ((mlib_s32)dst_ptr & 7) {
            ARGB_BM_XOR(0, 0);
            dst_ptr++;
            src_ptr++;
            size--;
        }

#pragma pipeloop(0)
        for (i = 0; i <= size - 2; i += 2) {
            mlib_s32 neg_mask;
            mlib_f32 *pp0 = (mlib_f32*)src_ptr + i;
            mlib_f32 *pp1 = (mlib_f32*)src_ptr + i + 1;
            neg_mask = (((*(mlib_u8*)pp0) >> 6) & 2) | ((*(mlib_u8*)pp1) >> 7);
            res = vis_freg_pair(*pp0, *pp1);
            res = vis_for(res, dFF);
            res = vis_fxor(res, xorpixel64);
            res = vis_fandnot(alphamask64, res);
            res = vis_fxor(res, *(mlib_d64*)(dst_ptr + i));
            vis_pst_32(res, dst_ptr + i, neg_mask);
        }

        if (i < size) {
            ARGB_BM_XOR(i, 0);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntRgbxXorBlit)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 xorpixel = pCompInfo->details.xorPixel;
    mlib_s32 alphamask = pCompInfo->alphaMask;
    mlib_s32 i, j, neg_mask;
    mlib_d64 res, xorpixel64, alphamask64, rgbx_mask, dzero;

    if (width < 8) {
        LOOP_DST_SRC(AnyInt, 1, dstBase, dstScan, srcBase, srcScan, RGBX_XOR);
        return;
    }

    if (dstScan == 4*width && srcScan == dstScan) {
        width *= height;
        height = 1;
    }

    xorpixel64 = vis_to_double_dup(xorpixel);
    alphamask64 = vis_to_double_dup(alphamask);
    rgbx_mask = vis_to_double_dup(0xFFFFFF00);
    dzero = vis_fzero();

    vis_alignaddr(NULL, 1);

    for (j = 0; j < height; j++) {
        mlib_s32 *dst_ptr = dstBase;
        mlib_s32 *src_ptr = srcBase;
        mlib_s32 size = width;

        if ((mlib_s32)dst_ptr & 7) {
            RGBX_XOR(0, 0);
            dst_ptr++;
            src_ptr++;
            size--;
        }

#pragma pipeloop(0)
        for (i = 0; i <= size - 2; i += 2) {
            mlib_s32 neg_mask;
            mlib_f32 *pp0 = (mlib_f32*)src_ptr + i;
            mlib_f32 *pp1 = (mlib_f32*)src_ptr + i + 1;
            neg_mask = (((*(mlib_u8*)pp0) >> 6) & 2) | ((*(mlib_u8*)pp1) >> 7);
            res = vis_freg_pair(*pp0, *pp1);
            res = vis_fand(vis_faligndata(res, res), rgbx_mask);
            res = vis_fxor(res, xorpixel64);
            res = vis_fandnot(alphamask64, res);
            res = vis_fxor(res, *(mlib_d64*)(dst_ptr + i));
            vis_pst_32(res, dst_ptr + i, neg_mask);
        }

        if (i < size) {
            RGBX_XOR(i, 0);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToFourByteAbgrPreXorBlit)(BLIT_PARAMS)
{
    jint   xorpixel = pCompInfo->details.xorPixel;
    juint  alphamask = pCompInfo->alphaMask;
    jint   xor0, xor1, xor2, xor3;
    jint   mask0, mask1, mask2, mask3;
    jint   *pSrc = srcBase;
    jubyte *pDst = dstBase;
    jint   srcScan = pSrcInfo->scanStride;
    jint   dstScan = pDstInfo->scanStride;

    xor0 = xorpixel;
    xor1 = xorpixel >> 8;
    xor2 = xorpixel >> 16;
    xor3 = xorpixel >> 24;
    mask0 = alphamask;
    mask1 = alphamask >> 8;
    mask2 = alphamask >> 16;
    mask3 = alphamask >> 24;

    srcScan -= width * 4;
    dstScan -= width * 4;

    do {
        juint w = width;;
        do {
            jint srcpixel;
            jint a, r, g, b;

            srcpixel = pSrc[0];
            b = srcpixel & 0xff;
            g = (srcpixel >> 8) & 0xff;
            r = (srcpixel >> 16) & 0xff;
            a = (mlib_u32)srcpixel >> 24;

            if (srcpixel < 0) {
                r = mul8table[a][r];
                g = mul8table[a][g];
                b = mul8table[a][b];

                pDst[0] ^= (a ^ xor0) & ~mask0;
                pDst[1] ^= (b ^ xor1) & ~mask1;
                pDst[2] ^= (g ^ xor2) & ~mask2;
                pDst[3] ^= (r ^ xor3) & ~mask3;
            }
            pSrc = ((void *) (((intptr_t) (pSrc)) + (4)));
            pDst = ((void *) (((intptr_t) (pDst)) + (4)));;
        }
        while (--w > 0);
        pSrc = ((void *) (((intptr_t) (pSrc)) + (srcScan)));
        pDst = ((void *) (((intptr_t) (pDst)) + (dstScan)));;
    }
    while (--height > 0);
}

/***************************************************************/

#endif
