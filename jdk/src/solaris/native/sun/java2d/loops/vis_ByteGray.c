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
    (((77 * (r)) + (150 * (g)) + (29 * (b)) + 128) >> 8)

/***************************************************************/

#define Gray2Argb(x)   \
    0xff000000 | (x << 16) | (x << 8) | x

/***************************************************************/

#define LUT(x)         \
    ((mlib_u8*)LutU8)[4 * (x)]

#define LUT12(x)       \
    ((mlib_u8*)LutU8)[4 * ((x) & 0xfff)]

/***************************************************************/

void ADD_SUFF(UshortGrayToByteGrayConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u8  *dst_end;
    mlib_d64 s0, s1, ss;
    mlib_s32 i, j;

    if (width <= 8) {
        for (j = 0; j < height; j++) {
            mlib_u8 *src = srcBase;
            mlib_u8 *dst = dstBase;

            for (i = 0; i < width; i++) {
                dst[i] = src[2*i];
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (srcScan == 2*width && dstScan == width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_d64 *sp;

        dst_end = dst + width;

        while (((mlib_s32)dst & 3) && dst < dst_end) {
            *dst++ = *src;
            src += 2;
        }

        if ((mlib_s32)src & 7) {
            sp = vis_alignaddr(src, 0);
            s1 = *sp++;

#pragma pipeloop(0)
            for (; dst <= (dst_end - 4); dst += 4) {
                s0 = s1;
                s1 = *sp++;
                ss = vis_faligndata(s0, s1);
                ss = vis_fpmerge(vis_read_hi(ss), vis_read_lo(ss));
                ss = vis_fpmerge(vis_read_hi(ss), vis_read_lo(ss));
                *(mlib_f32*)dst = vis_read_hi(ss);
                src += 2*4;
            }
        } else {
#pragma pipeloop(0)
            for (; dst <= (dst_end - 4); dst += 4) {
                ss = *(mlib_d64*)src;
                ss = vis_fpmerge(vis_read_hi(ss), vis_read_lo(ss));
                ss = vis_fpmerge(vis_read_hi(ss), vis_read_lo(ss));
                *(mlib_f32*)dst = vis_read_hi(ss);
                src += 2*4;
            }
        }

        while (dst < dst_end) {
            *dst++ = *src;
            src += 2;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteGrayToIntArgbConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 d0, d1, d2, d3;
    mlib_f32 ff, aa = vis_fones();
    mlib_s32 i, j, x;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_s32 *dst = dstBase;

            for (i = 0; i < width; i++) {
                x = src[i];
                dst[i] = Gray2Argb(x);
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (srcScan == width && dstScan == 4*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;
        mlib_s32 *dst_end;

        dst_end = dst + width;

        while (((mlib_s32)src & 3) && dst < dst_end) {
            x = *src++;
            *dst++ = Gray2Argb(x);
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 4); dst += 4) {
            ff = *(mlib_f32*)src;
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
            x = *src++;
            *dst++ = Gray2Argb(x);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteGrayToIntArgbScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 d0, d1, d2, d3, dd;
    mlib_f32 ff, aa = vis_fones();
    mlib_s32 i, j, x;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_s32 *dst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                x = src[tmpsxloc >> shift];
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
        mlib_u8  *src = srcBase;
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
            x = src[tmpsxloc >> shift];
            tmpsxloc += sxinc;
            *dst++ = Gray2Argb(x);
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

#if 1

#ifdef MLIB_ADD_SUFF
#pragma weak ByteGrayToIntArgbPreConvert_F = ByteGrayToIntArgbConvert_F
#else
#pragma weak ByteGrayToIntArgbPreConvert   = ByteGrayToIntArgbConvert
#endif

#ifdef MLIB_ADD_SUFF
#pragma weak ByteGrayToIntArgbPreScaleConvert_F =      \
             ByteGrayToIntArgbScaleConvert_F
#else
#pragma weak ByteGrayToIntArgbPreScaleConvert   =      \
             ByteGrayToIntArgbScaleConvert
#endif

#else

void ADD_SUFF(ByteGrayToIntArgbPreConvert)(BLIT_PARAMS)
{
    ADD_SUFF(ByteGrayToIntArgbConvert)(BLIT_CALL_PARAMS);
}

void ADD_SUFF(ByteGrayToIntArgbPreScaleConvert)(SCALE_PARAMS)
{
    ADD_SUFF(ByteGrayToIntArgbScaleConvert)(SCALE_CALL_PARAMS);
}

#endif

/***************************************************************/

void ADD_SUFF(UshortGrayToByteGrayScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 i, j, w, tmpsxloc;

    for (j = 0; j < height; j++) {
        mlib_u8 *pSrc = srcBase;
        mlib_u8 *pDst = dstBase;

        tmpsxloc = sxloc;
        w = width;

        PTR_ADD(pSrc, (syloc >> shift) * srcScan);

        if ((mlib_s32)pDst & 1) {
            *pDst++ = pSrc[2*(tmpsxloc >> shift)];
            tmpsxloc += sxinc;
            w--;
        }

#pragma pipeloop(0)
        for (i = 0; i <= (w - 2); i += 2) {
            mlib_s32 x0, x1;
            x0 = pSrc[2*(tmpsxloc >> shift)];
            x1 = pSrc[2*((tmpsxloc + sxinc) >> shift)];
            *(mlib_u16*)pDst = (x0 << 8) | x1;
            pDst += 2;
            tmpsxloc += 2*sxinc;
        }

        if (i < w) {
            *pDst = pSrc[2*(tmpsxloc >> shift)];
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(Index8GrayToByteGrayConvert)(BLIT_PARAMS)
{
    jint *SrcReadLut = pSrcInfo->lutBase;
    mlib_u8 *LutU8 = (mlib_u8*)SrcReadLut + 3;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            Index8GrayDataType *src = srcBase;
            mlib_u8 *dst = dstBase;

            for (i = 0; i < width; i++) {
                dst[i] = LUT(src[i]);
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (srcScan == width && dstScan == width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        Index8GrayDataType *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end = dst + width;

        if ((mlib_s32)dst & 1) {
            *dst++ = LUT(*src);
            src++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            ((mlib_u16*)dst)[0] = (LUT(src[0]) << 8) | LUT(src[1]);
            src += 2;
        }

        if (dst < dst_end) {
            *dst++ = LUT(*src);
            src++;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(Index12GrayToByteGrayConvert)(BLIT_PARAMS)
{
    jint *SrcReadLut = pSrcInfo->lutBase;
    mlib_u8 *LutU8 = (mlib_u8*)SrcReadLut + 3;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            Index12GrayDataType *src = srcBase;
            mlib_u8 *dst = dstBase;

            for (i = 0; i < width; i++) {
                dst[i] = LUT12(src[i]);
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (srcScan == 2*width && dstScan == width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        Index12GrayDataType *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end = dst + width;

        if ((mlib_s32)dst & 1) {
            *dst++ = LUT12(*src);
            src++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            ((mlib_u16*)dst)[0] = (LUT12(src[0]) << 8) | LUT12(src[1]);
            src += 2;
        }

        if (dst < dst_end) {
            *dst++ = LUT12(*src);
            src++;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(Index8GrayToByteGrayScaleConvert)(SCALE_PARAMS)
{
    jint *SrcReadLut = pSrcInfo->lutBase;
    mlib_u8 *LutU8 = (mlib_u8*)SrcReadLut + 3;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            Index8GrayDataType *src = srcBase;
            mlib_u8 *dst = dstBase;
            jint  tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                dst[i] = LUT(src[tmpsxloc >> shift]);
                tmpsxloc += sxinc;
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    for (j = 0; j < height; j++) {
        Index8GrayDataType *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end = dst + width;
        jint  tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 1) {
            *dst++ = LUT(src[tmpsxloc >> shift]);
            tmpsxloc += sxinc;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            ((mlib_u16*)dst)[0] = (LUT(src[tmpsxloc >> shift]) << 8) |
            LUT(src[(tmpsxloc + sxinc) >> shift]);
            tmpsxloc += 2*sxinc;
        }

        if (dst < dst_end) {
            *dst = LUT(src[tmpsxloc >> shift]);
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(Index12GrayToByteGrayScaleConvert)(SCALE_PARAMS)
{
    jint *SrcReadLut = pSrcInfo->lutBase;
    mlib_u8 *LutU8 = (mlib_u8*)SrcReadLut + 3;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            Index12GrayDataType *src = srcBase;
            mlib_u8 *dst = dstBase;
            jint  tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                dst[i] = LUT12(src[tmpsxloc >> shift]);
                tmpsxloc += sxinc;
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    for (j = 0; j < height; j++) {
        Index12GrayDataType *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end = dst + width;
        jint  tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 1) {
            *dst++ = LUT12(src[tmpsxloc >> shift]);
            tmpsxloc += sxinc;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            ((mlib_u16*)dst)[0] = (LUT12(src[tmpsxloc >> shift]) << 8) |
            LUT12(src[(tmpsxloc + sxinc) >> shift]);
            tmpsxloc += 2*sxinc;
        }

        if (dst < dst_end) {
            *dst = LUT12(src[tmpsxloc >> shift]);
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedToByteGrayConvert)(BLIT_PARAMS)
{
    jint  *srcLut = pSrcInfo->lutBase;
    juint lutSize = pSrcInfo->lutSize;
    mlib_u8  LutU8[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_u8 *src = srcBase;
            mlib_u8 *dst = dstBase;

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

    ADD_SUFF(IntArgbToByteGrayConvert)(srcLut, LutU8, lutSize, 1,
                                       pSrcInfo, pDstInfo, pPrim, pCompInfo);

    for (i = lutSize; i < 256; i++) {
        LutU8[i] = 0;
    }

    if (srcScan == width && dstScan == width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end = dst + width;

        if ((mlib_s32)dst & 1) {
            *dst++ = LutU8[*src];
            src++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            ((mlib_u16*)dst)[0] = (LutU8[src[0]] << 8) | LutU8[src[1]];
            src += 2;
        }

        if (dst < dst_end) {
            *dst++ = LutU8[*src];
            src++;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedToByteGrayScaleConvert)(SCALE_PARAMS)
{
    jint  *srcLut = pSrcInfo->lutBase;
    juint lutSize = pSrcInfo->lutSize;
    mlib_u8  LutU8[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_u8 *src = srcBase;
            mlib_u8 *dst = dstBase;
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

    ADD_SUFF(IntArgbToByteGrayConvert)(srcLut, LutU8, lutSize, 1,
                                       pSrcInfo, pDstInfo, pPrim, pCompInfo);

    for (i = lutSize; i < 256; i++) {
        LutU8[i] = 0;
    }

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end = dst + width;
        jint  tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 1) {
            *dst++ = LutU8[src[tmpsxloc >> shift]];
            tmpsxloc += sxinc;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            ((mlib_u16*)dst)[0] = (LutU8[src[tmpsxloc >> shift]] << 8) |
                                   LutU8[src[(tmpsxloc + sxinc) >> shift]];
            tmpsxloc += 2*sxinc;
        }

        if (dst < dst_end) {
            *dst = LutU8[src[tmpsxloc >> shift]];
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToByteGrayXparOver)(BLIT_PARAMS)
{
    jint  *srcLut = pSrcInfo->lutBase;
    juint lutSize = pSrcInfo->lutSize;
    mlib_u8  LutU8[256];
    mlib_u32 LutU32[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j, x0, x1, mask, res;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8 *src = srcBase;
            mlib_u8 *dst = dstBase;

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

    ADD_SUFF(IntArgbToByteGrayConvert)(srcLut, LutU8, lutSize, 1,
                                       pSrcInfo, pDstInfo, pPrim, pCompInfo);

    for (i = lutSize; i < 256; i++) {
        LutU8[i] = 0;
    }

#pragma pipeloop(0)
    for (i = 0; i < 256; i++) {
        LutU32[i] = ((srcLut[i] >> 31) & 0xFF0000) | LutU8[i];
    }

    if (srcScan == width && dstScan == width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end = dst + width;

        if ((mlib_s32)dst & 1) {
            x0 = *src;
            res = LutU32[x0];
            mask = res >> 16;
            *dst++ = (res & mask) | (*dst &~ mask);
            src++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            x0 = src[0];
            x1 = src[1];
            res = (LutU32[x0] << 8) | LutU32[x1];
            mask = res >> 16;
            ((mlib_u16*)dst)[0] = (res & mask) | (((mlib_u16*)dst)[0] &~ mask);
            src += 2;
        }

        if (dst < dst_end) {
            x0 = *src;
            res = LutU32[x0];
            mask = res >> 16;
            *dst = (res & mask) | (*dst &~ mask);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToByteGrayXparBgCopy)(BCOPY_PARAMS)
{
    jint  *srcLut = pSrcInfo->lutBase;
    juint lutSize = pSrcInfo->lutSize;
    mlib_u8  LutU8[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8 *src = srcBase;
            mlib_u8 *dst = dstBase;

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

    ADD_SUFF(IntArgbToByteGrayConvert)(srcLut, LutU8, lutSize, 1,
                                       pSrcInfo, pDstInfo, pPrim, pCompInfo);

    for (i = lutSize; i < 256; i++) {
        LutU8[i] = 0;
    }

#pragma pipeloop(0)
    for (i = 0; i < 256; i++) {
        if (srcLut[i] >= 0) LutU8[i] = bgpixel;
    }

    if (srcScan == width && dstScan == width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end = dst + width;

        if ((mlib_s32)dst & 1) {
            *dst++ = LutU8[*src];
            src++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            ((mlib_u16*)dst)[0] = (LutU8[src[0]] << 8) | LutU8[src[1]];
            src += 2;
        }

        if (dst < dst_end) {
            *dst++ = LutU8[*src];
            src++;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToByteGrayScaleXparOver)(SCALE_PARAMS)
{
    jint  *srcLut = pSrcInfo->lutBase;
    juint lutSize = pSrcInfo->lutSize;
    mlib_u8  LutU8[256];
    mlib_u32 LutU32[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j, x0, x1, mask, res;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8 *src = srcBase;
            mlib_u8 *dst = dstBase;
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

    ADD_SUFF(IntArgbToByteGrayConvert)(srcLut, LutU8, lutSize, 1,
                                       pSrcInfo, pDstInfo, pPrim, pCompInfo);

    for (i = lutSize; i < 256; i++) {
        LutU8[i] = 0;
    }

#pragma pipeloop(0)
    for (i = 0; i < 256; i++) {
        LutU32[i] = ((srcLut[i] >> 31) & 0xFF0000) | LutU8[i];
    }

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end = dst + width;
        jint  tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 1) {
            x0 = src[tmpsxloc >> shift];
            res = LutU32[x0];
            mask = res >> 16;
            *dst++ = (res & mask) | (*dst &~ mask);
            tmpsxloc += sxinc;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            x0 = src[tmpsxloc >> shift];
            x1 = src[(tmpsxloc + sxinc) >> shift];
            res = (LutU32[x0] << 8) | LutU32[x1];
            mask = res >> 16;
            ((mlib_u16*)dst)[0] = (res & mask) | (((mlib_u16*)dst)[0] &~ mask);
            tmpsxloc += 2*sxinc;
        }

        if (dst < dst_end) {
            x0 = src[tmpsxloc >> shift];
            res = LutU32[x0];
            mask = res >> 16;
            *dst = (res & mask) | (*dst &~ mask);
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

#endif
