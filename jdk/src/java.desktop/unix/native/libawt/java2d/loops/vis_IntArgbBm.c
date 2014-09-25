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

static mlib_u64 vis_amask_arr[] = {
    0x0000000000000000,
    0x00000000FF000000,
    0xFF00000000000000,
    0xFF000000FF000000,
};

/***************************************************************/

void ADD_SUFF(IntArgbBmToIntArgbConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dmask, dFF;
    mlib_s32 i, i0, j, x, mask;

    if (dstScan == 4*width && srcScan == 4*width) {
        width *= height;
        height = 1;
    }

    dmask = vis_to_double_dup(0xFFFFFF);
    dFF = vis_to_double_dup(0xFFFFFFFF);

    for (j = 0; j < height; j++) {
        mlib_s32 *src = srcBase;
        mlib_s32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            x = src[i];
            dst[i] = (x << 7) >> 7;
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            mlib_u8 *pp0 = (mlib_u8*)(src + i);
            mlib_u8 *pp1 = (mlib_u8*)(src + i + 1);
            dd = vis_freg_pair(*(mlib_f32*)pp0, *(mlib_f32*)pp1);
            dd = vis_fand(dd, dmask);
#if 1
            mask = ((*pp0 & 1) << 7) | ((*pp1 & 1) << 3);
            *(mlib_d64*)(dst + i) = dd;
            vis_pst_8(dFF, dst + i, mask);
#else
            mask = ((*pp0 & 1) << 1) | (*pp1 & 1);
            dd = vis_for(dd, ((mlib_d64*)vis_amask_arr)[mask]);
            *(mlib_d64*)(dst + i) = dd;
#endif
        }

        if (i < width) {
            x = src[i];
            dst[i] = (x << 7) >> 7;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntArgbBmConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dFF;
    mlib_s32 i, i0, j, x, mask;

    if (dstScan == 4*width && srcScan == 4*width) {
        width *= height;
        height = 1;
    }

    dFF = vis_to_double_dup(0xFFFFFFFF);

    for (j = 0; j < height; j++) {
        mlib_s32 *src = srcBase;
        mlib_s32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            x = src[i];
            dst[i] = x | ((x >> 31) << 24);
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            dd = vis_freg_pair(((mlib_f32*)src)[i], ((mlib_f32*)src)[i + 1]);
#ifdef VIS_USE_FCMP
            mask = vis_fcmplt32(dd, dzero);
            mask = ((mask << 3) | (mask << 6)) & 0x88;
#else
            mask = (*(mlib_u8*)(src + i) & 0x80) |
                   ((*(mlib_u8*)(src + i + 1) >> 4) & 0x8);
#endif
            *(mlib_d64*)(dst + i) = dd;
            vis_pst_8(dFF, dst + i, mask);
        }

        if (i < width) {
            x = src[i];
            dst[i] = x | ((x >> 31) << 24);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntArgbBmScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dFF;
    mlib_s32 j, x, mask;

    dFF = vis_to_double_dup(0xFFFFFFFF);

    for (j = 0; j < height; j++) {
        mlib_u32 *src = srcBase;
        mlib_u32 *dst = dstBase;
        mlib_u32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            x = src[tmpsxloc >> shift];
            *dst++ = x | ((x >> 31) << 24);
            tmpsxloc += sxinc;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            mlib_u8 *pp0 = (mlib_u8*)(src + (tmpsxloc >> shift));
            mlib_u8 *pp1 = (mlib_u8*)(src + ((tmpsxloc + sxinc) >> shift));
            dd = vis_freg_pair(*(mlib_f32*)pp0, *(mlib_f32*)pp1);
#ifdef VIS_USE_FCMP
            mask = vis_fcmplt32(dd, dzero);
            mask = ((mask << 3) | (mask << 6)) & 0x88;
#else
            mask = (*pp0 & 0x80) | ((*pp1 >> 4) & 0x8);
#endif
            *(mlib_d64*)dst = dd;
            vis_pst_8(dFF, dst, mask);
            tmpsxloc += 2*sxinc;
        }

        for (; dst < dst_end; dst++) {
            x = src[tmpsxloc >> shift];
            *dst++ = x | ((x >> 31) << 24);
            tmpsxloc += sxinc;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedToIntArgbBmConvert)(BLIT_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dFF;
    mlib_s32 i, i0, j, x, mask;

    if (srcScan == width && dstScan == 4*width) {
        width *= height;
        height = 1;
    }

    dFF = vis_to_double_dup(0xFFFFFFFF);

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            x = pixLut[src[i]];
            dst[i] =  x | ((x >> 31) << 24);
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            mlib_u8 *pp0 = (mlib_u8*)(pixLut + src[i]);
            mlib_u8 *pp1 = (mlib_u8*)(pixLut + src[i + 1]);
            dd = vis_freg_pair(*(mlib_f32*)pp0, *(mlib_f32*)pp1);
#ifdef VIS_USE_FCMP
            mask = vis_fcmplt32(dd, dzero);
            mask = ((mask << 3) | (mask << 6)) & 0x88;
#else
            mask = (*pp0 & 0x80) | ((*pp1 >> 4) & 0x8);
#endif
            *(mlib_d64*)(dst + i) = dd;
            vis_pst_8(dFF, dst + i, mask);
        }

        for (; i < width; i++) {
            x = pixLut[src[i]];
            dst[i] =  x | ((x >> 31) << 24);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedToIntArgbBmScaleConvert)(SCALE_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dFF;
    mlib_s32 j, x, mask;

    dFF = vis_to_double_dup(0xFFFFFFFF);

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;
        mlib_s32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            x = pixLut[src[tmpsxloc >> shift]];
            *dst++ = x | ((x >> 31) << 24);
            tmpsxloc += sxinc;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            mlib_u8 *pp0 = (void*)(pixLut + src[tmpsxloc >> shift]);
            mlib_u8 *pp1 = (void*)(pixLut + src[(tmpsxloc + sxinc) >> shift]);
            dd = vis_freg_pair(*(mlib_f32*)pp0, *(mlib_f32*)pp1);
#ifdef VIS_USE_FCMP
            mask = vis_fcmplt32(dd, dzero);
            mask = ((mask << 3) | (mask << 6)) & 0x88;
#else
            mask = (*pp0 & 0x80) | ((*pp1 >> 4) & 0x8);
#endif
            *(mlib_d64*)dst = dd;
            vis_pst_8(dFF, dst, mask);
            tmpsxloc += 2*sxinc;
        }

        for (; dst < dst_end; dst++) {
            x = pixLut[src[tmpsxloc >> shift]];
            *dst++ = x | ((x >> 31) << 24);
            tmpsxloc += sxinc;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToIntArgbBmXparOver)(BLIT_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dFF;
    mlib_s32 i, i0, j, x, mask;

    if (srcScan == width && dstScan == 4*width) {
        width *= height;
        height = 1;
    }

    dFF = vis_to_double_dup(0xFF000000);

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            x = pixLut[src[i]];
            if (x < 0) {
                dst[i] = x | 0xFF000000;
            }
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            mlib_u8 *pp0 = (mlib_u8*)(pixLut + src[i]);
            mlib_u8 *pp1 = (mlib_u8*)(pixLut + src[i + 1]);
            dd = vis_freg_pair(*(mlib_f32*)pp0, *(mlib_f32*)pp1);
#ifdef VIS_USE_FCMP
            mask = vis_fcmplt32(dd, dzero);
#else
            mask = ((*pp0 & 0x80) >> 6) | ((*pp1 & 0x80) >> 7);
#endif
            dd = vis_for(dd, dFF);
            vis_pst_32(dd, dst + i, mask);
        }

        for (; i < width; i++) {
            x = pixLut[src[i]];
            if (x < 0) {
                dst[i] = x | 0xFF000000;
            }
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToIntArgbBmScaleXparOver)(SCALE_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dFF;
    mlib_s32 j, x, mask;

    dFF = vis_to_double_dup(0xFF000000);

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;
        mlib_s32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            x = pixLut[src[tmpsxloc >> shift]];
            tmpsxloc += sxinc;
            if (x < 0) {
                *dst = x | 0xFF000000;
            }
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            mlib_u8 *pp0 = (void*)(pixLut + src[tmpsxloc >> shift]);
            mlib_u8 *pp1 = (void*)(pixLut + src[(tmpsxloc + sxinc) >> shift]);
            dd = vis_freg_pair(*(mlib_f32*)pp0, *(mlib_f32*)pp1);
#ifdef VIS_USE_FCMP
            mask = vis_fcmplt32(dd, dzero);
#else
            mask = ((*pp0 & 0x80) >> 6) | ((*pp1 & 0x80) >> 7);
#endif
            dd = vis_for(dd, dFF);
            vis_pst_32(dd, dst, mask);
            tmpsxloc += 2*sxinc;
        }

        for (; dst < dst_end; dst++) {
            x = pixLut[src[tmpsxloc >> shift]];
            tmpsxloc += sxinc;
            if (x < 0) {
                *dst = x | 0xFF000000;
            }
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToIntArgbBmXparBgCopy)(BCOPY_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dFF, d_bgpixel;
    mlib_s32 j, x, mask;

    if (srcScan == width && dstScan == 4*width) {
        width *= height;
        height = 1;
    }

    dFF = vis_to_double_dup(0xFF000000);
    d_bgpixel = vis_to_double_dup(bgpixel);

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;
        mlib_s32 *dst_end;

        dst_end = dst + width;

        if ((mlib_s32)dst & 7) {
            x = pixLut[*src++];
            if (x < 0) {
                *dst = x | 0xFF000000;
            } else {
                *dst = bgpixel;
            }
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            mlib_u8 *pp0 = (mlib_u8*)(pixLut + src[0]);
            mlib_u8 *pp1 = (mlib_u8*)(pixLut + src[1]);
            dd = vis_freg_pair(*(mlib_f32*)pp0, *(mlib_f32*)pp1);
#ifdef VIS_USE_FCMP
            mask = vis_fcmplt32(dd, dzero);
#else
            mask = ((*pp0 & 0x80) >> 6) | ((*pp1 & 0x80) >> 7);
#endif
            dd = vis_for(dd, dFF);
            *(mlib_d64*)dst = d_bgpixel;
            vis_pst_32(dd, dst, mask);
            src += 2;
        }

        while (dst < dst_end) {
            x = pixLut[*src++];
            if (x < 0) {
                *dst = x | 0xFF000000;
            } else {
                *dst = bgpixel;
            }
            dst++;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
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
                                    CompositeInfo *pCompInfo);

void ADD_SUFF(IntArgbBmAlphaMaskFill)(void *rasBase,
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
    mlib_u8  *dst = rasBase;
    mlib_s32 rasScan = pRasInfo->scanStride;
    mlib_s32 i, j;

    if (rasScan == 4*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        for (i = 0; i < width; i++) {
            dst[4*i] = ((mlib_s32)dst[4*i] << 31) >> 31;
        }
        PTR_ADD(dst, rasScan);
    }

    ADD_SUFF(IntArgbAlphaMaskFill)(rasBase, pMask, maskOff, maskScan,
                                   width, height,
                                   fgColor, pRasInfo, pPrim, pCompInfo);

    for (j = 0; j < height; j++) {
        for (i = 0; i < width; i++) {
            dst[4*i] = ((mlib_s32)dst[4*i] << 31) >> 31;
        }
        PTR_ADD(dst, rasScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbBmDrawGlyphListAA)(GLYPH_LIST_PARAMS)
{
    mlib_s32 glyphCounter;
    mlib_s32 scan = pRasInfo->scanStride;
    mlib_u8  *dstBase;
    mlib_s32 j;
    mlib_d64 dmix0, dmix1, dd, d0, d1, e0, e1, fgpixel_d;
    mlib_d64 done, done16, d_half;
    mlib_s32 pix, mask, srcA, dstA;
    mlib_f32 srcG_f;

    done = vis_to_double_dup(0x7fff7fff);
    done16 = vis_to_double_dup(0x7fff);
    d_half = vis_to_double_dup((1 << (16 + 6)) | (1 << 6));

    fgpixel_d = vis_to_double_dup(fgpixel);
    srcG_f = vis_to_float(argbcolor);

    srcA = (mlib_u32)argbcolor >> 24;

    vis_write_gsr(0 << 3);

    for (glyphCounter = 0; glyphCounter < totalGlyphs; glyphCounter++) {
        const jubyte *pixels;
        unsigned int rowBytes;
        int left, top;
        int width, height;
        int right, bottom;

        pixels = (const jubyte *) glyphs[glyphCounter].pixels;

        if (!pixels) continue;

        left = glyphs[glyphCounter].x;
        top = glyphs[glyphCounter].y;
        width = glyphs[glyphCounter].width;
        height = glyphs[glyphCounter].height;
        rowBytes = width;
        right = left + width;
        bottom = top + height;
        if (left < clipLeft) {
            pixels += clipLeft - left;
            left = clipLeft;
        }
        if (top < clipTop) {
            pixels += (clipTop - top) * rowBytes;
            top = clipTop;
        }
        if (right > clipRight) {
            right = clipRight;
        }
        if (bottom > clipBottom) {
            bottom = clipBottom;
        }
        if (right <= left || bottom <= top) {
            continue;
        }
        width = right - left;
        height = bottom - top;

        dstBase = pRasInfo->rasBase;
        PTR_ADD(dstBase, top*scan + 4*left);

        for (j = 0; j < height; j++) {
            mlib_u8  *src = (void*)pixels;
            mlib_s32 *dst, *dst_end;

            dst = (void*)dstBase;
            dst_end = dst + width;

            if ((mlib_s32)dst & 7) {
                pix = *src++;
                if (pix) {
                    dd = vis_fpadd16(MUL8_VIS(srcG_f, pix), d_half);
                    dd = vis_fpadd16(MUL8_VIS(*(mlib_f32*)dst, 255 - pix), dd);
                    *(mlib_f32*)dst = vis_fpack16(dd);
                    dstA = ((dst[0] << 7) >> 31) & 0xff;
                    dstA = mul8table[dstA][255 - pix] + mul8table[srcA][pix];
                    ((mlib_u8*)dst)[0] = dstA >> 7;
                    if (pix == 255) *(mlib_f32*)dst = vis_read_hi(fgpixel_d);
                }
                dst++;
            }

#pragma pipeloop(0)
            for (; dst <= (dst_end - 2); dst += 2) {
                mlib_s32 pix0 = src[0];
                mlib_s32 pix1 = src[1];
                dmix0 = vis_freg_pair(((mlib_f32 *)vis_mul8s_tbl)[pix0],
                                      ((mlib_f32 *)vis_mul8s_tbl)[pix1]);
                mask = vis_fcmplt32(dmix0, done16);
                dmix1 = vis_fpsub16(done, dmix0);
                src += 2;

                dd = *(mlib_d64*)dst;
                d0 = vis_fmul8x16al(srcG_f, vis_read_hi(dmix0));
                d1 = vis_fmul8x16al(srcG_f, vis_read_lo(dmix0));
                e0 = vis_fmul8x16al(vis_read_hi(dd), vis_read_hi(dmix1));
                e1 = vis_fmul8x16al(vis_read_lo(dd), vis_read_lo(dmix1));
                d0 = vis_fpadd16(vis_fpadd16(d0, d_half), e0);
                d1 = vis_fpadd16(vis_fpadd16(d1, d_half), e1);
                dd = vis_fpack16_pair(d0, d1);

                *(mlib_d64*)dst = dd;
                dstA = ((dst[0] << 7) >> 31) & 0xff;
                dstA = mul8table[dstA][255 - pix0] + mul8table[srcA][pix0];
                pix0 = (-pix0) >> 31;
                ((mlib_u8*)dst)[0] = ((dstA >> 7) & pix0) |
                                     (((mlib_u8*)dst)[0] &~ pix0);
                dstA = ((dst[1] << 7) >> 31) & 0xff;
                dstA = mul8table[dstA][255 - pix1] + mul8table[srcA][pix1];
                pix1 = (-pix1) >> 31;
                ((mlib_u8*)dst)[4] = ((dstA >> 7) & pix1) |
                                     (((mlib_u8*)dst)[4] &~ pix1);

                vis_pst_32(fgpixel_d, dst, ~mask);
            }

            while (dst < dst_end) {
                pix = *src++;
                if (pix) {
                    dd = vis_fpadd16(MUL8_VIS(srcG_f, pix), d_half);
                    dd = vis_fpadd16(MUL8_VIS(*(mlib_f32*)dst, 255 - pix), dd);
                    *(mlib_f32*)dst = vis_fpack16(dd);
                    dstA = ((dst[0] << 7) >> 31) & 0xff;
                    dstA = mul8table[dstA][255 - pix] + mul8table[srcA][pix];
                    ((mlib_u8*)dst)[0] = dstA >> 7;
                    if (pix == 255) *(mlib_f32*)dst = vis_read_hi(fgpixel_d);
                }
                dst++;
            }

            PTR_ADD(dstBase, scan);
            pixels += rowBytes;
        }
    }
}

/***************************************************************/

#endif /* JAVA2D_NO_MLIB */
