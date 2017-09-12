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

#define RGB2GRAY(r, g, b)      \
    (((19672 * (r)) + (38621 * (g)) + (7500 * (b))) >> 8)

/***************************************************************/

#define Gray2Argb(x)   \
    0xff000000 | (x << 16) | (x << 8) | x

/***************************************************************/

void ADD_SUFF(ByteGrayToUshortGrayConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 s0, s1, ss, d0, d1;
    mlib_s32 i, j, x;

    if (width <= 8) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_u16 *dst = dstBase;

            for (i = 0; i < width; i++) {
                x = src[i];
                dst[i] = x | (x << 8);
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (srcScan == width && dstScan == 2*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_u16 *dst = dstBase;
        mlib_u16 *dst_end;
        mlib_d64 *sp;

        dst_end = dst + width;

        while (((mlib_s32)dst & 7) && dst < dst_end) {
            x = *src++;
            *dst++ = x | (x << 8);
        }

        if ((mlib_s32)src & 7) {
            sp = vis_alignaddr(src, 0);
            s1 = *sp++;

#pragma pipeloop(0)
            for (; dst <= (dst_end - 8); dst += 8) {
                s0 = s1;
                s1 = *sp++;
                ss = vis_faligndata(s0, s1);
                d0 = vis_fpmerge(vis_read_hi(ss), vis_read_hi(ss));
                d1 = vis_fpmerge(vis_read_lo(ss), vis_read_lo(ss));
                ((mlib_d64*)dst)[0] = d0;
                ((mlib_d64*)dst)[1] = d1;
                src += 8;
            }
        } else {
#pragma pipeloop(0)
            for (; dst <= (dst_end - 8); dst += 8) {
                ss = *(mlib_d64*)src;
                d0 = vis_fpmerge(vis_read_hi(ss), vis_read_hi(ss));
                d1 = vis_fpmerge(vis_read_lo(ss), vis_read_lo(ss));
                ((mlib_d64*)dst)[0] = d0;
                ((mlib_d64*)dst)[1] = d1;
                src += 8;
            }
        }

        while (dst < dst_end) {
            x = *src++;
            *dst++ = x | (x << 8);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(UshortGrayToIntArgbConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 ss, d0, d1, d2, d3;
    mlib_f32 ff, aa = vis_fones();
    mlib_s32 i, j, x;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_u16 *src = srcBase;
            mlib_s32 *dst = dstBase;

            for (i = 0; i < width; i++) {
                x = src[i] >> 8;
                dst[i] = Gray2Argb(x);
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (srcScan == 2*width && dstScan == 4*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_u16 *src = srcBase;
        mlib_s32 *dst = dstBase;
        mlib_s32 *dst_end;

        dst_end = dst + width;

        while (((mlib_s32)src & 7) && dst < dst_end) {
            x = *src++ >> 8;
            *dst++ = Gray2Argb(x);
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 4); dst += 4) {
            ss = *(mlib_d64*)src;
            ss = vis_fpmerge(vis_read_hi(ss), vis_read_lo(ss));
            ss = vis_fpmerge(vis_read_hi(ss), vis_read_lo(ss));
            ff = vis_read_hi(ss);

            d0 = vis_fpmerge(aa, ff);
            d1 = vis_fpmerge(ff, ff);
            d2 = vis_fpmerge(vis_read_hi(d0), vis_read_hi(d1));
            d3 = vis_fpmerge(vis_read_lo(d0), vis_read_lo(d1));
            ((mlib_f32*)dst)[0] = vis_read_hi(d2);
            ((mlib_f32*)dst)[1] = vis_read_lo(d2);
            ((mlib_f32*)dst)[2] = vis_read_hi(d3);
            ((mlib_f32*)dst)[3] = vis_read_lo(d3);
            src += 4;
        }

        while (dst < dst_end) {
            x = *src++ >> 8;
            *dst++ = Gray2Argb(x);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(UshortGrayToIntArgbScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 d0, d1, d2, d3, dd;
    mlib_f32 ff, aa = vis_fones();
    mlib_s32 i, j, x;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u16 *src = srcBase;
            mlib_s32 *dst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                x = src[tmpsxloc >> shift] >> 8;
                tmpsxloc += sxinc;
                dst[i] = Gray2Argb(x);
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    vis_alignaddr(NULL, 7);

    for (j = 0; j < height; j++) {
        mlib_u16 *src = srcBase;
        mlib_s32 *dst = dstBase;
        mlib_s32 *dst_end;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        dst_end = dst + width;

#pragma pipeloop(0)
        for (; dst <= (dst_end - 4); dst += 4) {
            LOAD_NEXT_U8(dd, src + ((tmpsxloc + 3*sxinc) >> shift));
            LOAD_NEXT_U8(dd, src + ((tmpsxloc + 2*sxinc) >> shift));
            LOAD_NEXT_U8(dd, src + ((tmpsxloc +   sxinc) >> shift));
            LOAD_NEXT_U8(dd, src + ((tmpsxloc          ) >> shift));
            tmpsxloc += 4*sxinc;
            ff = vis_read_hi(dd);
            d0 = vis_fpmerge(aa, ff);
            d1 = vis_fpmerge(ff, ff);
            d2 = vis_fpmerge(vis_read_hi(d0), vis_read_hi(d1));
            d3 = vis_fpmerge(vis_read_lo(d0), vis_read_lo(d1));
            ((mlib_f32*)dst)[0] = vis_read_hi(d2);
            ((mlib_f32*)dst)[1] = vis_read_lo(d2);
            ((mlib_f32*)dst)[2] = vis_read_hi(d3);
            ((mlib_f32*)dst)[3] = vis_read_lo(d3);
        }

        while (dst < dst_end) {
            x = src[tmpsxloc >> shift] >> 8;
            tmpsxloc += sxinc;
            *dst++ = Gray2Argb(x);
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteGrayToUshortGrayScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd;
    mlib_s32 i, j, x;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_u16 *dst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                x = src[tmpsxloc >> shift];
                tmpsxloc += sxinc;
                dst[i] = x | (x << 8);
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    vis_alignaddr(NULL, 7);

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_u16 *dst = dstBase;
        mlib_u16 *dst_end;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        dst_end = dst + width;

        while (((mlib_s32)dst & 7) && dst < dst_end) {
            x = src[tmpsxloc >> shift];
            tmpsxloc += sxinc;
            *dst++ = x | (x << 8);
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 4); dst += 4) {
            LOAD_NEXT_U8(dd, src + ((tmpsxloc + 3*sxinc) >> shift));
            LOAD_NEXT_U8(dd, src + ((tmpsxloc + 2*sxinc) >> shift));
            LOAD_NEXT_U8(dd, src + ((tmpsxloc +   sxinc) >> shift));
            LOAD_NEXT_U8(dd, src + ((tmpsxloc          ) >> shift));
            tmpsxloc += 4*sxinc;
            *(mlib_d64*)dst = vis_fpmerge(vis_read_hi(dd), vis_read_hi(dd));
        }

        while (dst < dst_end) {
            x = src[tmpsxloc >> shift];
            tmpsxloc += sxinc;
            *dst++ = x | (x << 8);
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedToUshortGrayConvert)(BLIT_PARAMS)
{
    jint  *srcLut = pSrcInfo->lutBase;
    juint lutSize = pSrcInfo->lutSize;
    mlib_u16 LutU16[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_u16 *dst = dstBase;

            for (i = 0; i < width; i++) {
                jint argb = srcLut[src[i]];
                int r, g, b;
                b = (argb) & 0xff;
                g = (argb >> 8) & 0xff;
                r = (argb >> 16) & 0xff;
                dst[i] = RGB2GRAY(r, g, b);
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (lutSize >= 256) lutSize = 256;

    ADD_SUFF(IntArgbToUshortGrayConvert)(srcLut, LutU16, lutSize, 1,
                                         pSrcInfo, pDstInfo, pPrim, pCompInfo);

    for (i = lutSize; i < 256; i++) {
        LutU16[i] = 0;
    }

    if (srcScan == width && dstScan == 2*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u16 *dst = dstBase;
        mlib_u16 *dst_end = dst + width;

        if ((mlib_s32)dst & 3) {
            *dst++ = LutU16[*src];
            src++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            ((mlib_u32*)dst)[0] = (LutU16[src[0]] << 16) | LutU16[src[1]];
            src += 2;
        }

        if (dst < dst_end) {
            *dst++ = LutU16[*src];
            src++;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedToUshortGrayScaleConvert)(SCALE_PARAMS)
{
    jint  *srcLut = pSrcInfo->lutBase;
    juint lutSize = pSrcInfo->lutSize;
    mlib_u16 LutU16[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_u16 *dst = dstBase;
            jint  tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                jint argb = srcLut[src[tmpsxloc >> shift]];
                int r, g, b;
                b = (argb) & 0xff;
                g = (argb >> 8) & 0xff;
                r = (argb >> 16) & 0xff;
                dst[i] = RGB2GRAY(r, g, b);
                tmpsxloc += sxinc;
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    if (lutSize >= 256) lutSize = 256;

    ADD_SUFF(IntArgbToUshortGrayConvert)(srcLut, LutU16, lutSize, 1,
                                       pSrcInfo, pDstInfo, pPrim, pCompInfo);

    for (i = lutSize; i < 256; i++) {
        LutU16[i] = 0;
    }

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u16 *dst = dstBase;
        mlib_u16 *dst_end = dst + width;
        jint  tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 3) {
            *dst++ = LutU16[src[tmpsxloc >> shift]];
            tmpsxloc += sxinc;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            ((mlib_u32*)dst)[0] = (LutU16[src[tmpsxloc >> shift]] << 16) |
                                   LutU16[src[(tmpsxloc + sxinc) >> shift]];
            tmpsxloc += 2*sxinc;
        }

        if (dst < dst_end) {
            *dst = LutU16[src[tmpsxloc >> shift]];
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToUshortGrayXparOver)(BLIT_PARAMS)
{
    jint  *srcLut = pSrcInfo->lutBase;
    juint lutSize = pSrcInfo->lutSize;
    mlib_u16 LutU16[256];
    mlib_u32 LutU32[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j, x0, mask, res;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_u16 *dst = dstBase;

            for (i = 0; i < width; i++) {
                mlib_s32 argb = srcLut[src[i]];
                if (argb < 0) {
                    int r, g, b;
                    b = (argb) & 0xff;
                    g = (argb >> 8) & 0xff;
                    r = (argb >> 16) & 0xff;
                    dst[i] = RGB2GRAY(r, g, b);
                }
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (lutSize >= 256) lutSize = 256;

    ADD_SUFF(IntArgbToUshortGrayConvert)(srcLut, LutU16, lutSize, 1,
                                         pSrcInfo, pDstInfo, pPrim, pCompInfo);

    for (i = lutSize; i < 256; i++) {
        LutU16[i] = 0;
    }

#pragma pipeloop(0)
    for (i = 0; i < 256; i++) {
        LutU32[i] = ((srcLut[i] >> 31) & 0xFFFF0000) | LutU16[i];
    }

    if (srcScan == width && dstScan == 2*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_u16 *dst = dstBase;
        mlib_u16 *dst_end = dst + width;

#pragma pipeloop(0)
        for (; dst < dst_end; dst++) {
            x0 = *src;
            res = LutU32[x0];
            mask = res >> 16;
            *dst = (res & mask) | (*dst &~ mask);
            src++;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToUshortGrayXparBgCopy)(BCOPY_PARAMS)
{
    jint  *srcLut = pSrcInfo->lutBase;
    juint lutSize = pSrcInfo->lutSize;
    mlib_u16 LutU16[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_u16 *dst = dstBase;

            for (i = 0; i < width; i++) {
                mlib_s32 argb = srcLut[src[i]];
                if (argb < 0) {
                    int r, g, b;
                    b = (argb) & 0xff;
                    g = (argb >> 8) & 0xff;
                    r = (argb >> 16) & 0xff;
                    dst[i] = RGB2GRAY(r, g, b);
                } else {
                    dst[i] = bgpixel;
                }
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (lutSize >= 256) lutSize = 256;

    ADD_SUFF(IntArgbToUshortGrayConvert)(srcLut, LutU16, lutSize, 1,
                                         pSrcInfo, pDstInfo, pPrim, pCompInfo);

    for (i = lutSize; i < 256; i++) {
        LutU16[i] = 0;
    }

#pragma pipeloop(0)
    for (i = 0; i < 256; i++) {
        if (srcLut[i] >= 0) LutU16[i] = bgpixel;
    }

    if (srcScan == width && dstScan == 2*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u16 *dst = dstBase;
        mlib_u16 *dst_end = dst + width;

        if ((mlib_s32)dst & 3) {
            *dst++ = LutU16[*src];
            src++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            ((mlib_u32*)dst)[0] = (LutU16[src[0]] << 16) | LutU16[src[1]];
            src += 2;
        }

        if (dst < dst_end) {
            *dst++ = LutU16[*src];
            src++;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToUshortGrayScaleXparOver)(SCALE_PARAMS)
{
    jint  *srcLut = pSrcInfo->lutBase;
    juint lutSize = pSrcInfo->lutSize;
    mlib_u16 LutU16[256];
    mlib_u32 LutU32[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j, x0, mask, res;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_u16 *dst = dstBase;
            jint  tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                mlib_s32 argb = srcLut[src[tmpsxloc >> shift]];
                if (argb < 0) {
                    int r, g, b;
                    b = (argb) & 0xff;
                    g = (argb >> 8) & 0xff;
                    r = (argb >> 16) & 0xff;
                    dst[i] = RGB2GRAY(r, g, b);
                }
                tmpsxloc += sxinc;
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    if (lutSize >= 256) lutSize = 256;

    ADD_SUFF(IntArgbToUshortGrayConvert)(srcLut, LutU16, lutSize, 1,
                                         pSrcInfo, pDstInfo, pPrim, pCompInfo);

    for (i = lutSize; i < 256; i++) {
        LutU16[i] = 0;
    }

#pragma pipeloop(0)
    for (i = 0; i < 256; i++) {
        LutU32[i] = ((srcLut[i] >> 31) & 0xFFFF0000) | LutU16[i];
    }

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_u16 *dst = dstBase;
        mlib_u16 *dst_end = dst + width;
        jint  tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

#pragma pipeloop(0)
        for (; dst < dst_end; dst++) {
            x0 = src[tmpsxloc >> shift];
            res = LutU32[x0];
            mask = res >> 16;
            *dst = (res & mask) | (*dst &~ mask);
            tmpsxloc += sxinc;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

#endif
