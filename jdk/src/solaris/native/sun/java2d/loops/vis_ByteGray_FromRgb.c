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
#include "vis_AlphaMacros.h"

/***************************************************************/

extern mlib_d64 vis_d64_div_tbl[256];

/***************************************************************/

#define RGB2GRAY(r, g, b)      \
    (((77 * (r)) + (150 * (g)) + (29 * (b)) + 128) >> 8)

/***************************************************************/

static const mlib_s32 RGB_weight[] = {
    128*77,
    128*150,
    128*29,
    (1 << (16 + 6)) | (1 << 6)
};

/***************************************************************/

#define RGB_VARS                                               \
    mlib_d64 r, g, b, ar, gb, s02, s13;                        \
    mlib_f32 ff;                                               \
    mlib_f32 alpha = ((mlib_f32*)RGB_weight)[0];               \
    mlib_f32 beta  = ((mlib_f32*)RGB_weight)[1];               \
    mlib_f32 gamma = ((mlib_f32*)RGB_weight)[2];               \
    mlib_d64 d_half = vis_to_double_dup(RGB_weight[3]);        \
                                                               \
    vis_write_gsr((0 << 3) | 6)

/***************************************************************/

#define GRAY_U8(ff, r, g, b)           \
{                                      \
    mlib_d64 dr, dg, db;               \
    dr = vis_fmul8x16al(r, alpha);     \
    dg = vis_fmul8x16al(g, beta);      \
    db = vis_fmul8x16al(b, gamma);     \
    dr = vis_fpadd16(dr, dg);          \
    db = vis_fpadd16(db, d_half);      \
    dr = vis_fpadd16(dr, db);          \
    ff = vis_fpack16(dr);              \
}

/***************************************************************/

#define GRAY_S16(dd, r, g, b)          \
{                                      \
    mlib_d64 dr, dg, db;               \
    dr = vis_fmul8x16al(r, alpha);     \
    dg = vis_fmul8x16al(g, beta);      \
    db = vis_fmul8x16al(b, gamma);     \
    dr = vis_fpadd16(dr, dg);          \
    db = vis_fpadd16(db, d_half);      \
    dd = vis_fpadd16(dr, db);          \
}

/***************************************************************/

#define LOAD_BGR(ind)                                          \
    b = vis_faligndata(vis_ld_u8(src + (ind    )), b);         \
    g = vis_faligndata(vis_ld_u8(src + (ind + 1)), g);         \
    r = vis_faligndata(vis_ld_u8(src + (ind + 2)), r)

/***************************************************************/

void ADD_SUFF(IntArgbToByteGrayConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *dst_end;
    mlib_s32 j;
    RGB_VARS;

    if (dstScan == width && srcScan == 4*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_u8  *dst = dstBase;

        dst_end = dst + width;

        while (((mlib_s32)dst & 3) && dst < dst_end) {
            r = vis_ld_u8((mlib_u8*)src + 1);
            g = vis_ld_u8((mlib_u8*)src + 2);
            b = vis_ld_u8((mlib_u8*)src + 3);
            GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
            vis_st_u8(D64_FROM_F32x2(ff), dst);
            dst++;
            src++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 4); dst += 4) {
            s02 = vis_fpmerge(src[0], src[2]);
            s13 = vis_fpmerge(src[1], src[3]);
            ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
            gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
            GRAY_U8(ff, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));
            *(mlib_f32*)dst = ff;
            src += 4;
        }

        while (dst < dst_end) {
            r = vis_ld_u8((mlib_u8*)src + 1);
            g = vis_ld_u8((mlib_u8*)src + 2);
            b = vis_ld_u8((mlib_u8*)src + 3);
            GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
            vis_st_u8(D64_FROM_F32x2(ff), dst);
            dst++;
            src++;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ThreeByteBgrToByteGrayConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *dst_end;
    mlib_s32 j;
    RGB_VARS;

    vis_alignaddr(NULL, 7);

    if (dstScan == width && srcScan == 3*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u8 *dst = dstBase;

        dst_end = dst + width;

        while (((mlib_s32)dst & 3) && dst < dst_end) {
            b = vis_ld_u8(src);
            g = vis_ld_u8(src + 1);
            r = vis_ld_u8(src + 2);
            GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
            vis_st_u8(D64_FROM_F32x2(ff), dst);
            dst++;
            src += 3;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 4); dst += 4) {
            LOAD_BGR(9);
            LOAD_BGR(6);
            LOAD_BGR(3);
            LOAD_BGR(0);
            GRAY_U8(ff, vis_read_hi(r), vis_read_hi(g), vis_read_hi(b));
            *(mlib_f32*)dst = ff;
            src += 3*4;
        }

        while (dst < dst_end) {
            b = vis_ld_u8(src);
            g = vis_ld_u8(src + 1);
            r = vis_ld_u8(src + 2);
            GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
            vis_st_u8(D64_FROM_F32x2(ff), dst);
            dst++;
            src += 3;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToByteGrayScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *dst_end;
    mlib_s32 i, j;
    RGB_VARS;

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_u8  *dst = dstBase;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        dst_end = dst + width;

        while (((mlib_s32)dst & 3) && dst < dst_end) {
            i = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            r = vis_ld_u8((mlib_u8*)(src + i) + 1);
            g = vis_ld_u8((mlib_u8*)(src + i) + 2);
            b = vis_ld_u8((mlib_u8*)(src + i) + 3);
            GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
            vis_st_u8(D64_FROM_F32x2(ff), dst);
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 4); dst += 4) {
            s02 = vis_fpmerge(src[(tmpsxloc          ) >> shift],
                              src[(tmpsxloc + 2*sxinc) >> shift]);
            s13 = vis_fpmerge(src[(tmpsxloc +   sxinc) >> shift],
                              src[(tmpsxloc + 3*sxinc) >> shift]);
            tmpsxloc += 4*sxinc;
            ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
            gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
            GRAY_U8(ff, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));
            *(mlib_f32*)dst = ff;
        }

        while (dst < dst_end) {
            i = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            r = vis_ld_u8((mlib_u8*)(src + i) + 1);
            g = vis_ld_u8((mlib_u8*)(src + i) + 2);
            b = vis_ld_u8((mlib_u8*)(src + i) + 3);
            GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
            vis_st_u8(D64_FROM_F32x2(ff), dst);
            dst++;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ThreeByteBgrToByteGrayScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *dst_end;
    mlib_s32 j, i0, i1, i2, i3;
    RGB_VARS;

    vis_alignaddr(NULL, 7);

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_u8  *dst = dstBase;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        dst_end = dst + width;

        while (((mlib_s32)dst & 3) && dst < dst_end) {
            i0 = 3*(tmpsxloc >> shift);
            tmpsxloc += sxinc;
            b = vis_ld_u8(src + i0);
            g = vis_ld_u8(src + i0 + 1);
            r = vis_ld_u8(src + i0 + 2);
            GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
            vis_st_u8(D64_FROM_F32x2(ff), dst);
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 4); dst += 4) {
            i0 = 3*(tmpsxloc >> shift);
            tmpsxloc += sxinc;
            i1 = 3*(tmpsxloc >> shift);
            tmpsxloc += sxinc;
            i2 = 3*(tmpsxloc >> shift);
            tmpsxloc += sxinc;
            i3 = 3*(tmpsxloc >> shift);
            tmpsxloc += sxinc;
            LOAD_BGR(i3);
            LOAD_BGR(i2);
            LOAD_BGR(i1);
            LOAD_BGR(i0);
            GRAY_U8(ff, vis_read_hi(r), vis_read_hi(g), vis_read_hi(b));
            *(mlib_f32*)dst = ff;
        }

        while (dst < dst_end) {
            i0 = 3*(tmpsxloc >> shift);
            tmpsxloc += sxinc;
            b = vis_ld_u8(src + i0);
            g = vis_ld_u8(src + i0 + 1);
            r = vis_ld_u8(src + i0 + 2);
            GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
            vis_st_u8(D64_FROM_F32x2(ff), dst);
            dst++;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbBmToByteGrayXparOver)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *dst_end;
    mlib_d64 dzero = vis_fzero();
    mlib_f32 f0, f1;
    mlib_s32 i, j, mask0, mask1;
    RGB_VARS;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_u8  *dst = dstBase;

            for (i = 0; i < width; i++) {
                if (src[4*i]) {
                    dst[i] = RGB2GRAY(src[4*i + 1], src[4*i + 2], src[4*i + 3]);
                }
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_u8  *dst = dstBase;

        dst_end = dst + width;

        while (((mlib_s32)dst & 7) && dst < dst_end) {
            if (*(mlib_u8*)src) {
                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                vis_st_u8(D64_FROM_F32x2(ff), dst);
            }
            dst++;
            src++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 8); dst += 8) {
            s02 = vis_fpmerge(src[0], src[2]);
            s13 = vis_fpmerge(src[1], src[3]);
            src += 4;
            ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
            gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
            mask0 = vis_fcmpne16(vis_fpmerge(vis_read_hi(ar), vis_read_hi(ar)),
                                 dzero);
            GRAY_U8(f0, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));

            s02 = vis_fpmerge(src[0], src[2]);
            s13 = vis_fpmerge(src[1], src[3]);
            src += 4;
            ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
            gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
            mask1 = vis_fcmpne16(vis_fpmerge(vis_read_hi(ar), vis_read_hi(ar)),
                                 dzero);
            GRAY_U8(f1, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));

            vis_pst_8(vis_freg_pair(f0, f1), dst, (mask0 << 4) | mask1);
        }

        while (dst < dst_end) {
            if (*(mlib_u8*)src) {
                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                vis_st_u8(D64_FROM_F32x2(ff), dst);
            }
            dst++;
            src++;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbBmToByteGrayXparBgCopy)(BCOPY_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *dst_end;
    mlib_d64 dzero = vis_fzero(), d_bgpixel;
    mlib_f32 f0, f1;
    mlib_s32 i, j, mask0, mask1;
    RGB_VARS;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_u8  *dst = dstBase;

            for (i = 0; i < width; i++) {
                if (src[4*i]) {
                    dst[i] = RGB2GRAY(src[4*i + 1], src[4*i + 2], src[4*i + 3]);
                } else {
                    dst[i] = bgpixel;
                }
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    D64_FROM_U8x8(d_bgpixel, bgpixel);

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_u8  *dst = dstBase;

        dst_end = dst + width;

        while (((mlib_s32)dst & 7) && dst < dst_end) {
            if (*(mlib_u8*)src) {
                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                vis_st_u8(D64_FROM_F32x2(ff), dst);
            } else {
                *dst = bgpixel;
            }
            dst++;
            src++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 8); dst += 8) {
            s02 = vis_fpmerge(src[0], src[2]);
            s13 = vis_fpmerge(src[1], src[3]);
            src += 4;
            ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
            gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
            mask0 = vis_fcmpne16(vis_fpmerge(vis_read_hi(ar), vis_read_hi(ar)),
                                 dzero);
            GRAY_U8(f0, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));

            s02 = vis_fpmerge(src[0], src[2]);
            s13 = vis_fpmerge(src[1], src[3]);
            src += 4;
            ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
            gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
            mask1 = vis_fcmpne16(vis_fpmerge(vis_read_hi(ar), vis_read_hi(ar)),
                                 dzero);
            GRAY_U8(f1, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));

            *(mlib_d64*)dst = d_bgpixel;
            vis_pst_8(vis_freg_pair(f0, f1), dst, (mask0 << 4) | mask1);
        }

        while (dst < dst_end) {
            if (*(mlib_u8*)src) {
                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                vis_st_u8(D64_FROM_F32x2(ff), dst);
            } else {
                *dst = bgpixel;
            }
            dst++;
            src++;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToByteGrayXorBlit)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *dst_end;
    mlib_d64 dd, d_xorpixel, d_alphamask, dzero = vis_fzero();
    mlib_f32 f0, f1;
    mlib_s32 i, j, mask0, mask1;
    jint  xorpixel = pCompInfo->details.xorPixel;
    juint alphamask = pCompInfo->alphaMask;
    RGB_VARS;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_s32 *src = srcBase;
            mlib_u8  *dst = dstBase;
            mlib_s32 srcpixel, r, g, b;

            for (i = 0; i < width; i++) {
                srcpixel = src[i];
                if (srcpixel >= 0) continue;
                b = (srcpixel) & 0xff;
                g = (srcpixel >> 8) & 0xff;
                r = (srcpixel >> 16) & 0xff;
                srcpixel = (77*r + 150*g + 29*b + 128) / 256;
                dst[i]  ^= (((srcpixel) ^ (xorpixel)) & ~(alphamask));
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    D64_FROM_U8x8(d_xorpixel,  xorpixel);
    D64_FROM_U8x8(d_alphamask, alphamask);

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_u8  *dst = dstBase;

        dst_end = dst + width;

        while (((mlib_s32)dst & 7) && dst < dst_end) {
            if ((*(mlib_u8*)src) & 0x80) {
                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                dd = vis_fxor(D64_FROM_F32x2(ff), d_xorpixel);
                dd = vis_fandnot(d_alphamask, dd);
                vis_st_u8(vis_fxor(vis_ld_u8(dst), dd), dst);
            }
            dst++;
            src++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 8); dst += 8) {
            s02 = vis_fpmerge(src[0], src[2]);
            s13 = vis_fpmerge(src[1], src[3]);
            src += 4;
            ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
            gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
            mask0 = vis_fcmplt16(vis_fpmerge(vis_read_hi(ar), vis_read_hi(ar)),
                                 dzero);
            GRAY_U8(f0, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));

            s02 = vis_fpmerge(src[0], src[2]);
            s13 = vis_fpmerge(src[1], src[3]);
            src += 4;
            ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
            gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
            mask1 = vis_fcmplt16(vis_fpmerge(vis_read_hi(ar), vis_read_hi(ar)),
                                 dzero);
            GRAY_U8(f1, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));

            dd = vis_freg_pair(f0, f1);
            dd = vis_fandnot(d_alphamask, vis_fxor(dd, d_xorpixel));
            vis_pst_8(vis_fxor(*(mlib_d64*)dst, dd), dst, (mask0 << 4) | mask1);
        }

        while (dst < dst_end) {
            if ((*(mlib_u8*)src) & 0x80) {
                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                dd = vis_fxor(D64_FROM_F32x2(ff), d_xorpixel);
                dd = vis_fandnot(d_alphamask, dd);
                vis_st_u8(vis_fxor(vis_ld_u8(dst), dd), dst);
            }
            dst++;
            src++;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbBmToByteGrayScaleXparOver)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *dst_end;
    mlib_d64 dzero = vis_fzero();
    mlib_f32 f0, f1;
    mlib_s32 i, j, mask0, mask1;
    RGB_VARS;

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_u8  *dst = dstBase;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        dst_end = dst + width;

        while (((mlib_s32)dst & 7) && dst < dst_end) {
            i = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            if (*(mlib_u8*)(src + i)) {
                r = vis_ld_u8((mlib_u8*)(src + i) + 1);
                g = vis_ld_u8((mlib_u8*)(src + i) + 2);
                b = vis_ld_u8((mlib_u8*)(src + i) + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                vis_st_u8(D64_FROM_F32x2(ff), dst);
            }
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 8); dst += 8) {
            s02 = vis_fpmerge(src[(tmpsxloc          ) >> shift],
                              src[(tmpsxloc + 2*sxinc) >> shift]);
            s13 = vis_fpmerge(src[(tmpsxloc +   sxinc) >> shift],
                              src[(tmpsxloc + 3*sxinc) >> shift]);
            tmpsxloc += 4*sxinc;
            ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
            gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
            mask0 = vis_fcmpne16(vis_fpmerge(vis_read_hi(ar), vis_read_hi(ar)),
                                 dzero);
            GRAY_U8(f0, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));

            s02 = vis_fpmerge(src[(tmpsxloc          ) >> shift],
                              src[(tmpsxloc + 2*sxinc) >> shift]);
            s13 = vis_fpmerge(src[(tmpsxloc +   sxinc) >> shift],
                              src[(tmpsxloc + 3*sxinc) >> shift]);
            tmpsxloc += 4*sxinc;
            ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
            gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
            mask1 = vis_fcmpne16(vis_fpmerge(vis_read_hi(ar), vis_read_hi(ar)),
                                 dzero);
            GRAY_U8(f1, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));

            vis_pst_8(vis_freg_pair(f0, f1), dst, (mask0 << 4) | mask1);
        }

        while (dst < dst_end) {
            i = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            if (*(mlib_u8*)(src + i)) {
                r = vis_ld_u8((mlib_u8*)(src + i) + 1);
                g = vis_ld_u8((mlib_u8*)(src + i) + 2);
                b = vis_ld_u8((mlib_u8*)(src + i) + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                vis_st_u8(D64_FROM_F32x2(ff), dst);
            }
            dst++;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

#define TBL_MUL ((mlib_s16*)vis_mul8s_tbl + 1)
#define TBL_DIV ((mlib_u8*)vis_div8_tbl + 2)

void ADD_SUFF(IntArgbToByteGraySrcOverMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_s32 extraA;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *mul8_extra;
    mlib_u8  *dst_end;
    mlib_d64 srcAx4, dd, d0, d1;
    mlib_d64 done = vis_to_double_dup(0x7fff7fff);
    mlib_s32 j, srcA0, srcA1, srcA2, srcA3;
    RGB_VARS;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);
    mul8_extra = mul8table[extraA];

    if (pMask != NULL) {
        pMask += maskOff;

        if (dstScan == width && srcScan == 4*width && maskScan == width) {
            width *= height;
            height = 1;
        }

        maskScan -= width;

        for (j = 0; j < height; j++) {
            mlib_f32 *src = srcBase;
            mlib_u8  *dst = dstBase;

            dst_end = dst + width;

            while (((mlib_s32)dst & 3) && dst < dst_end) {
                srcA0 = mul8table[mul8_extra[*pMask++]][*(mlib_u8*)src];
                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                d0 = vis_fpadd16(MUL8_VIS(ff, srcA0), d_half);
                d1 = MUL8_VIS(vis_read_lo(vis_ld_u8(dst)), 255 - srcA0);
                dd = vis_fpadd16(d0, d1);
                vis_st_u8(D64_FROM_F32x2(vis_fpack16(dd)), dst);
                dst++;
                src++;
            }

#pragma pipeloop(0)
            for (; dst <= (dst_end - 4); dst += 4) {
                srcA0 = mul8table[mul8_extra[*pMask++]][*(mlib_u8*)src];
                srcA1 = mul8table[mul8_extra[*pMask++]][*(mlib_u8*)(src + 1)];
                srcA2 = mul8table[mul8_extra[*pMask++]][*(mlib_u8*)(src + 2)];
                srcA3 = mul8table[mul8_extra[*pMask++]][*(mlib_u8*)(src + 3)];
                srcAx4 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*srcA3), srcAx4);
                srcAx4 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*srcA2), srcAx4);
                srcAx4 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*srcA1), srcAx4);
                srcAx4 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*srcA0), srcAx4);

                s02 = vis_fpmerge(src[0], src[2]);
                s13 = vis_fpmerge(src[1], src[3]);
                ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
                gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
                GRAY_U8(ff, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));
                d0 = vis_fpadd16(vis_fmul8x16(ff, srcAx4), d_half);
                d1 = vis_fmul8x16(*(mlib_f32*)dst, vis_fpsub16(done, srcAx4));
                dd = vis_fpadd16(d0, d1);
                *(mlib_f32*)dst = vis_fpack16(dd);
                src += 4;
            }

            while (dst < dst_end) {
                srcA0 = mul8table[mul8_extra[*pMask++]][*(mlib_u8*)src];
                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                d0 = vis_fpadd16(MUL8_VIS(ff, srcA0), d_half);
                d1 = MUL8_VIS(vis_read_lo(vis_ld_u8(dst)), 255 - srcA0);
                dd = vis_fpadd16(d0, d1);
                vis_st_u8(D64_FROM_F32x2(vis_fpack16(dd)), dst);
                dst++;
                src++;
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
            PTR_ADD(pMask,  maskScan);
        }
    } else {

        if (dstScan == width && srcScan == 4*width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            mlib_f32 *src = srcBase;
            mlib_u8  *dst = dstBase;

            dst_end = dst + width;

            while (((mlib_s32)dst & 3) && dst < dst_end) {
                srcA0 = mul8_extra[*(mlib_u8*)src];
                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                d0 = vis_fpadd16(MUL8_VIS(ff, srcA0), d_half);
                d1 = MUL8_VIS(vis_read_lo(vis_ld_u8(dst)), 255 - srcA0);
                dd = vis_fpadd16(d0, d1);
                vis_st_u8(D64_FROM_F32x2(vis_fpack16(dd)), dst);
                dst++;
                src++;
            }

#pragma pipeloop(0)
            for (; dst <= (dst_end - 4); dst += 4) {
                srcA0 = mul8_extra[*(mlib_u8*)src];
                srcA1 = mul8_extra[*(mlib_u8*)(src + 1)];
                srcA2 = mul8_extra[*(mlib_u8*)(src + 2)];
                srcA3 = mul8_extra[*(mlib_u8*)(src + 3)];
                srcAx4 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*srcA3), srcAx4);
                srcAx4 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*srcA2), srcAx4);
                srcAx4 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*srcA1), srcAx4);
                srcAx4 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*srcA0), srcAx4);

                s02 = vis_fpmerge(src[0], src[2]);
                s13 = vis_fpmerge(src[1], src[3]);
                ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
                gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
                GRAY_U8(ff, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));
                d0 = vis_fpadd16(vis_fmul8x16(ff, srcAx4), d_half);
                d1 = vis_fmul8x16(*(mlib_f32*)dst, vis_fpsub16(done, srcAx4));
                dd = vis_fpadd16(d0, d1);
                *(mlib_f32*)dst = vis_fpack16(dd);
                src += 4;
            }

            while (dst < dst_end) {
                srcA0 = mul8_extra[*(mlib_u8*)src];
                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                d0 = vis_fpadd16(MUL8_VIS(ff, srcA0), d_half);
                d1 = MUL8_VIS(vis_read_lo(vis_ld_u8(dst)), 255 - srcA0);
                dd = vis_fpadd16(d0, d1);
                vis_st_u8(D64_FROM_F32x2(vis_fpack16(dd)), dst);
                dst++;
                src++;
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}

/***************************************************************/

#define GET_COEF(i)                                                    \
    pathA = pMask[i];                                                  \
    srcA = *(mlib_u8*)(src + i);                                       \
    srcA = mul8table[extraA][srcA];                                    \
    dstF = ((((srcA) & DstOpAnd) ^ DstOpXor) + DstOpAdd);              \
    srcF = mul8table[pathA][srcFbase];                                 \
    dstA = 0xff - pathA + mul8table[pathA][dstF];                      \
    srcA = mul8table[srcF][srcA];                                      \
    resA = srcA + dstA;                                                \
    srcAx4 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*srcA), srcAx4);     \
    divAx4 = vis_faligndata(vis_ld_u16(TBL_DIV + 8*resA), divAx4)

/***************************************************************/

void ADD_SUFF(IntArgbToByteGrayAlphaMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_s32 extraA;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *dst_end;
    mlib_d64 srcAx4, dstAx4, divAx4, dd, ds;
    mlib_d64 done = vis_to_double_dup(0x01000100);
    mlib_f32 fscale = vis_to_float(0x02020202);
    mlib_s32 j;
    mlib_s32 SrcOpAnd, SrcOpXor, SrcOpAdd;
    mlib_s32 DstOpAnd, DstOpXor, DstOpAdd;
    mlib_s32 pathA, srcFbase, resA, resG, srcF, dstF, srcA, dstA;

    RGB_VARS;

    SrcOpAnd = (AlphaRules[pCompInfo->rule].srcOps).andval;
    SrcOpXor = (AlphaRules[pCompInfo->rule].srcOps).xorval;
    SrcOpAdd =
        (jint) (AlphaRules[pCompInfo->rule].srcOps).addval - SrcOpXor;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd =
        (jint) (AlphaRules[pCompInfo->rule].dstOps).addval - DstOpXor;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

    srcFbase = ((((0xff) & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd);

    vis_write_gsr((7 << 3) | 6);

    if (pMask != NULL) {
        pMask += maskOff;

        if (dstScan == width && srcScan == 4*width && maskScan == width) {
            width *= height;
            height = 1;
        }

        maskScan -= width;

        for (j = 0; j < height; j++) {
            mlib_f32 *src = srcBase;
            mlib_u8  *dst = dstBase;

            dst_end = dst + width;

            while (((mlib_s32)dst & 3) && dst < dst_end) {
                pathA = *pMask++;
                srcA = *(mlib_u8*)src;
                srcA = mul8table[extraA][srcA];
                dstF = ((((srcA) & DstOpAnd) ^ DstOpXor) + DstOpAdd);
                srcF = mul8table[pathA][srcFbase];
                dstA = 0xff - pathA + mul8table[pathA][dstF];
                srcA = mul8table[srcF][srcA];
                resA = srcA + dstA;

                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_S16(dd, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                dd = vis_fmul8x16(fscale, dd);
                ff = vis_fpack16(dd);

                dd = vis_freg_pair(vis_fzeros(),
                                   ((mlib_f32*)vis_mul8s_tbl)[dstA]);
                DIV_ALPHA(dd, resA);
                ds = vis_fpsub16(done, dd);
                dd = vis_fmul8x16(vis_read_lo(vis_ld_u8(dst)), dd);
                ds = vis_fmul8x16(ff, ds);
                dd = vis_fpadd16(dd, ds);
                ff = vis_fpack16(dd);
                vis_st_u8(D64_FROM_F32x2(ff), dst);

                dst++;
                src++;
            }

#pragma pipeloop(0)
            for (; dst <= (dst_end - 4); dst += 4) {
                GET_COEF(3);
                GET_COEF(2);
                GET_COEF(1);
                GET_COEF(0);
                pMask += 4;
                srcAx4 = FMUL_16x16(srcAx4, divAx4);
                dstAx4 = vis_fpsub16(done, srcAx4);

                s02 = vis_fpmerge(src[0], src[2]);
                s13 = vis_fpmerge(src[1], src[3]);
                ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
                gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
                GRAY_S16(dd, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));
                dd = vis_fmul8x16(fscale, dd);
                ff = vis_fpack16(dd);

                dd = vis_fmul8x16(*(mlib_f32*)dst, dstAx4);
                ds = vis_fmul8x16(ff, srcAx4);
                dd = vis_fpadd16(dd, ds);
                *(mlib_f32*)dst = vis_fpack16(dd);

                src += 4;
            }

            while (dst < dst_end) {
                pathA = *pMask++;
                srcA = *(mlib_u8*)src;
                srcA = mul8table[extraA][srcA];
                dstF = ((((srcA) & DstOpAnd) ^ DstOpXor) + DstOpAdd);
                srcF = mul8table[pathA][srcFbase];
                dstA = 0xff - pathA + mul8table[pathA][dstF];
                srcA = mul8table[srcF][srcA];
                resA = srcA + dstA;

                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_S16(dd, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                dd = vis_fmul8x16(fscale, dd);
                ff = vis_fpack16(dd);

                dd = vis_freg_pair(vis_fzeros(),
                                   ((mlib_f32*)vis_mul8s_tbl)[dstA]);
                DIV_ALPHA(dd, resA);
                ds = vis_fpsub16(done, dd);
                dd = vis_fmul8x16(vis_read_lo(vis_ld_u8(dst)), dd);
                ds = vis_fmul8x16(ff, ds);
                dd = vis_fpadd16(dd, ds);
                ff = vis_fpack16(dd);
                vis_st_u8(D64_FROM_F32x2(ff), dst);

                dst++;
                src++;
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
            PTR_ADD(pMask,  maskScan);
        }
    } else {

        if (dstScan == width && srcScan == 4*width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            mlib_f32 *src = srcBase;
            mlib_u8  *dst = dstBase;

            dst_end = dst + width;

            while (dst < dst_end) {
                srcA = *(mlib_u8*)src;
                srcA = mul8table[extraA][srcA];
                dstA = ((((srcA) & DstOpAnd) ^ DstOpXor) + DstOpAdd);
                srcA = mul8table[srcFbase][srcA];
                resA = srcA + dstA;

                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_S16(dd, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                dd = vis_fmul8x16(fscale, dd);
                ff = vis_fpack16(dd);

                resG = mul8table[dstA][*dst] +
                       mul8table[srcA][((mlib_u8*)&ff)[3]];
                *dst = div8table[resA][resG];

                dst++;
                src++;
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbToByteGrayAlphaMaskBlit)(MASKBLIT_PARAMS)
{
    mlib_s32 extraA;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *dst_end;
    mlib_d64 srcA_d, dstA_d, dd, d0, d1;
    mlib_s32 i, j, srcG;
    mlib_s32 SrcOpAnd, SrcOpXor, SrcOpAdd;
    mlib_s32 DstOpAnd, DstOpXor, DstOpAdd;
    mlib_s32 pathA, srcFbase, dstFbase, resA, resG, srcA, dstA;

    RGB_VARS;

    SrcOpAnd = (AlphaRules[pCompInfo->rule].srcOps).andval;
    SrcOpXor = (AlphaRules[pCompInfo->rule].srcOps).xorval;
    SrcOpAdd =
        (jint) (AlphaRules[pCompInfo->rule].srcOps).addval - SrcOpXor;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd =
        (jint) (AlphaRules[pCompInfo->rule].dstOps).addval - DstOpXor;

    extraA = (mlib_s32)(pCompInfo->details.extraAlpha * 255.0 + 0.5);

    srcFbase = ((((0xff) & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd);
    dstFbase = (((extraA & DstOpAnd) ^ DstOpXor) + DstOpAdd);

    srcFbase = mul8table[srcFbase][extraA];

    if (width < 16) {
        if (pMask != NULL) {
            pMask += maskOff;

            for (j = 0; j < height; j++) {
                mlib_u8 *dst = dstBase;
                mlib_u8 *src = srcBase;

                for (i = 0; i < width; i++) {
                    pathA = pMask[i];
                    dstA = 0xff - pathA + mul8table[dstFbase][pathA];
                    srcA = mul8table[srcFbase][pathA];
                    resA = srcA + dstA;

                    srcG = RGB2GRAY(src[4*i + 1], src[4*i + 2], src[4*i + 3]);
                    resG = mul8table[dstA][dst[i]] + mul8table[srcA][srcG];
                    resG = div8table[resA][resG];
                    dst[i] = resG;
                }

                PTR_ADD(dstBase, dstScan);
                PTR_ADD(srcBase, srcScan);
                PTR_ADD(pMask,  maskScan);
            }
        } else {
            dstA = dstFbase;
            srcA = srcFbase;
            resA = srcA + dstA;

            for (j = 0; j < height; j++) {
                mlib_u8 *dst = dstBase;
                mlib_u8 *src = srcBase;

                for (i = 0; i < width; i++) {
                    srcG = RGB2GRAY(src[4*i + 1], src[4*i + 2], src[4*i + 3]);
                    resG = mul8table[dstA][dst[i]] + mul8table[srcA][srcG];
                    resG = div8table[resA][resG];
                    dst[i] = resG;
                }

                PTR_ADD(dstBase, dstScan);
                PTR_ADD(srcBase, srcScan);
            }
        }
        return;
    }

    if (pMask != NULL) {
        mlib_s32 srcA_buff[256];
        mlib_d64 dscale = (mlib_d64)(1 << 15)*(1 << 16), ddiv;
        mlib_d64 d_one = vis_to_double_dup(0x7FFF7FFF);

        srcA_buff[0] = 0;
#pragma pipeloop(0)
        for (pathA = 1; pathA < 256; pathA++) {
            dstA = 0xff - pathA + mul8table[dstFbase][pathA];
            srcA = mul8table[srcFbase][pathA];
            resA = dstA + srcA;
            ddiv = dscale*vis_d64_div_tbl[resA];
            srcA_buff[pathA] = srcA*ddiv + (1 << 15);
        }

        pMask += maskOff;
        maskScan -= width;

        if (dstScan == width && srcScan == 4*width && maskScan == width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            mlib_f32 *src = srcBase;
            mlib_u8  *dst = dstBase;

            dst_end = dst + width;

            while (((mlib_s32)dst & 3) && dst < dst_end) {
                pathA = *pMask++;
                srcA_d = vis_ld_u16(srcA_buff + pathA);
                dstA_d = vis_fpsub16(d_one, srcA_d);
                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                d0 = vis_fpadd16(vis_fmul8x16(ff, srcA_d), d_half);
                d1 = vis_fmul8x16(vis_read_lo(vis_ld_u8(dst)), dstA_d);
                dd = vis_fpadd16(d0, d1);
                vis_st_u8(D64_FROM_F32x2(vis_fpack16(dd)), dst);
                dst++;
                src++;
            }

#pragma pipeloop(0)
            for (; dst <= (dst_end - 4); dst += 4) {
                LOAD_NEXT_U16(srcA_d, srcA_buff + pMask[3]);
                LOAD_NEXT_U16(srcA_d, srcA_buff + pMask[2]);
                LOAD_NEXT_U16(srcA_d, srcA_buff + pMask[1]);
                LOAD_NEXT_U16(srcA_d, srcA_buff + pMask[0]);
                dstA_d = vis_fpsub16(d_one, srcA_d);
                pMask += 4;

                s02 = vis_fpmerge(src[0], src[2]);
                s13 = vis_fpmerge(src[1], src[3]);
                ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
                gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
                GRAY_U8(ff, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));
                dd = vis_fpadd16(vis_fmul8x16(ff, srcA_d), d_half);
                dd = vis_fpadd16(vis_fmul8x16(*(mlib_f32*)dst, dstA_d), dd);
                *(mlib_f32*)dst = vis_fpack16(dd);
                src += 4;
            }

            while (dst < dst_end) {
                pathA = *pMask++;
                srcA_d = vis_ld_u16(srcA_buff + pathA);
                dstA_d = vis_fpsub16(d_one, srcA_d);
                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                d0 = vis_fpadd16(vis_fmul8x16(ff, srcA_d), d_half);
                d1 = vis_fmul8x16(vis_read_lo(vis_ld_u8(dst)), dstA_d);
                dd = vis_fpadd16(d0, d1);
                ff = vis_fpack16(dd);
                vis_st_u8(D64_FROM_F32x2(ff), dst);
                dst++;
                src++;
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
            PTR_ADD(pMask,  maskScan);
        }
    } else {
        mlib_d64 dscale = (mlib_d64)(1 << 15)*(1 << 16), ddiv;
        mlib_d64 d_one = vis_to_double_dup(0x7FFF7FFF);

        dstA = dstFbase;
        srcA = srcFbase;
        resA = dstA + srcA;
        ddiv = dscale*vis_d64_div_tbl[resA];
        srcA = (mlib_s32)(srcA*ddiv + (1 << 15)) >> 16;
        srcA_d = vis_to_double_dup((srcA << 16) | srcA);
        dstA_d = vis_fpsub16(d_one, srcA_d);

        if (dstScan == width && srcScan == 4*width) {
            width *= height;
            height = 1;
        }

        for (j = 0; j < height; j++) {
            mlib_f32 *src = srcBase;
            mlib_u8  *dst = dstBase;

            dst_end = dst + width;

            while (((mlib_s32)dst & 3) && dst < dst_end) {
                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                d0 = vis_fpadd16(vis_fmul8x16(ff, srcA_d), d_half);
                d1 = vis_fmul8x16(vis_read_lo(vis_ld_u8(dst)), dstA_d);
                dd = vis_fpadd16(d0, d1);
                vis_st_u8(D64_FROM_F32x2(vis_fpack16(dd)), dst);
                dst++;
                src++;
            }

#pragma pipeloop(0)
            for (; dst <= (dst_end - 4); dst += 4) {
                s02 = vis_fpmerge(src[0], src[2]);
                s13 = vis_fpmerge(src[1], src[3]);
                ar = vis_fpmerge(vis_read_hi(s02), vis_read_hi(s13));
                gb = vis_fpmerge(vis_read_lo(s02), vis_read_lo(s13));
                GRAY_U8(ff, vis_read_lo(ar), vis_read_hi(gb), vis_read_lo(gb));
                dd = vis_fpadd16(vis_fmul8x16(ff, srcA_d), d_half);
                dd = vis_fpadd16(vis_fmul8x16(*(mlib_f32*)dst, dstA_d), dd);
                *(mlib_f32*)dst = vis_fpack16(dd);
                src += 4;
            }

            while (dst < dst_end) {
                r = vis_ld_u8((mlib_u8*)src + 1);
                g = vis_ld_u8((mlib_u8*)src + 2);
                b = vis_ld_u8((mlib_u8*)src + 3);
                GRAY_U8(ff, vis_read_lo(r), vis_read_lo(g), vis_read_lo(b));
                d0 = vis_fpadd16(vis_fmul8x16(ff, srcA_d), d_half);
                d1 = vis_fmul8x16(vis_read_lo(vis_ld_u8(dst)), dstA_d);
                dd = vis_fpadd16(d0, d1);
                ff = vis_fpack16(dd);
                vis_st_u8(D64_FROM_F32x2(ff), dst);
                dst++;
                src++;
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
    }
}

/***************************************************************/

#endif
