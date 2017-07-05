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
#include "vis_AlphaMacros.h"

/***************************************************************/

#define STORE_INT      \
    *dst ^= fgpixel

#define STORE_D64(TSIZE, dst, mask)    \
    vis_pst_##TSIZE(vis_fxor(*(mlib_d64*)(dst), fgpixel_d), dst, mask)

/***************************************************************/

#define INIT_FG                                                \
    fgpixel = (fgpixel ^ pCompInfo->details.xorPixel)          \
              &~ pCompInfo->alphaMask;

/***************************************************************/

#define DEF_GLYPH(TSIZE)                                       \
    const jubyte *pixels;                                      \
    unsigned int rowBytes;                                     \
    int left, top;                                             \
    int width, height;                                         \
    int right, bottom;                                         \
                                                               \
    pixels = (const jubyte *) glyphs[glyphCounter].pixels;     \
                                                               \
    if (!pixels) continue;                                     \
                                                               \
    left = glyphs[glyphCounter].x;                             \
    top = glyphs[glyphCounter].y;                              \
    width = glyphs[glyphCounter].width;                        \
    height = glyphs[glyphCounter].height;                      \
    rowBytes = width;                                          \
    right = left + width;                                      \
    bottom = top + height;                                     \
    if (left < clipLeft) {                                     \
        pixels += clipLeft - left;                             \
        left = clipLeft;                                       \
    }                                                          \
    if (top < clipTop) {                                       \
        pixels += (clipTop - top) * rowBytes;                  \
        top = clipTop;                                         \
    }                                                          \
    if (right > clipRight) {                                   \
        right = clipRight;                                     \
    }                                                          \
    if (bottom > clipBottom) {                                 \
        bottom = clipBottom;                                   \
    }                                                          \
    if (right <= left || bottom <= top) {                      \
        continue;                                              \
    }                                                          \
    width = right - left;                                      \
    height = bottom - top;                                     \
                                                               \
    dstBase = pRasInfo->rasBase;                               \
    PTR_ADD(dstBase, top*scan + TSIZE*left)

/***************************************************************/

void ADD_SUFF(AnyByteDrawGlyphListXor)(GLYPH_LIST_PARAMS)
{
    mlib_s32 glyphCounter;
    mlib_s32 scan = pRasInfo->scanStride;
    mlib_u8  *dstBase;
    mlib_s32 j;
    mlib_d64 fgpixel_d;
    mlib_d64 dzero;
    mlib_s32 pix, mask0, mask1, mask_h, mask_l, off;
    mlib_f32 fzero;

    INIT_FG

    fzero = vis_fzeros();
    dzero = vis_fzero();
    D64_FROM_U8x8(fgpixel_d, fgpixel);

    for (glyphCounter = 0; glyphCounter < totalGlyphs; glyphCounter++) {
        DEF_GLYPH(1);

        for (j = 0; j < height; j++) {
            mlib_u8 *src = (void*)pixels;
            mlib_u8 *dst, *dst_end;
            mlib_d64 ss, s0, s1;

            dst = (void*)dstBase;
            dst_end = dst + width;

            while (((mlib_s32)dst & 7) && (dst < dst_end)) {
                pix = *src++;
                if (pix) STORE_INT;
                dst++;
            }

            off = (mlib_s32)src & 7;
            ss = *(mlib_d64*)(src - off);
            mask_h = vis_fcmpne16(vis_fpmerge(vis_read_hi(ss), fzero), dzero);
            mask_l = vis_fcmpne16(vis_fpmerge(vis_read_lo(ss), fzero), dzero);
            mask1 = (mask_h << 4) | mask_l;

#pragma pipeloop(0)
            for (; dst <= (dst_end - 8); dst += 8) {
                mask0 = mask1;
                src += 8;
                ss = *(mlib_d64*)(src - off);
                s0 = vis_fpmerge(vis_read_hi(ss), fzero);
                s1 = vis_fpmerge(vis_read_lo(ss), fzero);
                mask_h = vis_fcmpne16(s0, dzero);
                mask_l = vis_fcmpne16(s1, dzero);
                mask1 = (mask_h << 4) | mask_l;
                STORE_D64(8, dst, (mask0 << off) | (mask1 >> (8 - off)));
            }

            while (dst < dst_end) {
                pix = *src++;
                if (pix) STORE_INT;
                dst++;
            }

            PTR_ADD(dstBase, scan);
            pixels += rowBytes;
        }
    }
}

/***************************************************************/

void ADD_SUFF(AnyShortDrawGlyphListXor)(GLYPH_LIST_PARAMS)
{
    mlib_s32 glyphCounter;
    mlib_s32 scan = pRasInfo->scanStride;
    mlib_u8  *dstBase;
    mlib_s32 j;
    mlib_d64 fgpixel_d;
    mlib_d64 dzero;
    mlib_s32 pix, mask0, mask1, off;
    mlib_f32 fzero;

    INIT_FG

    fzero = vis_fzeros();
    dzero = vis_fzero();
    D64_FROM_U16x4(fgpixel_d, fgpixel);

    for (glyphCounter = 0; glyphCounter < totalGlyphs; glyphCounter++) {
        DEF_GLYPH(2);

        for (j = 0; j < height; j++) {
            mlib_u8 *src = (void*)pixels;
            mlib_u16 *dst, *dst_end;
            mlib_f32 ss;

            dst = (void*)dstBase;
            dst_end = dst + width;

            while (((mlib_s32)dst & 7) && (dst < dst_end)) {
                pix = *src++;
                if (pix) STORE_INT;
                dst++;
            }

            off = (mlib_s32)src & 3;
            ss = *(mlib_f32*)(src - off);
            mask1 = vis_fcmpne16(vis_fpmerge(ss, fzero), dzero);

#pragma pipeloop(0)
            for (; dst <= (dst_end - 4); dst += 4) {
                mask0 = mask1;
                src += 4;
                ss = *(mlib_f32*)(src - off);
                mask1 = vis_fcmpne16(vis_fpmerge(ss, fzero), dzero);
                STORE_D64(16, dst, (mask0 << off) | (mask1 >> (4 - off)));
            }

            while (dst < dst_end) {
                pix = *src++;
                if (pix) STORE_INT;
                dst++;
            }

            PTR_ADD(dstBase, scan);
            pixels += rowBytes;
        }
    }
}

/***************************************************************/

void ADD_SUFF(AnyIntDrawGlyphListXor)(GLYPH_LIST_PARAMS)
{
    mlib_s32 glyphCounter;
    mlib_s32 scan = pRasInfo->scanStride;
    mlib_u8  *dstBase;
    mlib_s32 j;
    mlib_d64 fgpixel_d;
    mlib_d64 dzero;
    mlib_s32 pix, mask0, mask1, mask, off;
    mlib_f32 fzero;

    INIT_FG

    fzero = vis_fzeros();
    dzero = vis_fzero();
    fgpixel_d = vis_to_double_dup(fgpixel);

    for (glyphCounter = 0; glyphCounter < totalGlyphs; glyphCounter++) {
        DEF_GLYPH(4);

        for (j = 0; j < height; j++) {
            mlib_u8 *src = (void*)pixels;
            mlib_u32 *dst, *dst_end;
            mlib_f32 ss;

            dst = (void*)dstBase;
            dst_end = dst + width;

            while (((mlib_s32)dst & 7) && (dst < dst_end)) {
                pix = *src++;
                if (pix) STORE_INT;
                dst++;
            }

            off = (mlib_s32)src & 3;
            ss = *(mlib_f32*)(src - off);
            mask1 = vis_fcmpne16(vis_fpmerge(ss, fzero), dzero);

#pragma pipeloop(0)
            for (; dst <= (dst_end - 4); dst += 4) {
                mask0 = mask1;
                src += 4;
                ss = *(mlib_f32*)(src - off);
                mask1 = vis_fcmpne16(vis_fpmerge(ss, fzero), dzero);
                mask = (mask0 << off) | (mask1 >> (4 - off));
                STORE_D64(32, dst, mask >> 2);
                STORE_D64(32, dst + 2, mask);
            }

            while (dst < dst_end) {
                pix = *src++;
                if (pix) STORE_INT;
                dst++;
            }

            PTR_ADD(dstBase, scan);
            pixels += rowBytes;
        }
    }
}

/***************************************************************/

void ADD_SUFF(Any4ByteDrawGlyphListXor)(GLYPH_LIST_PARAMS)
{
    mlib_d64 buff[BUFF_SIZE/2];
    void     *pbuff = buff;
    mlib_s32 glyphCounter;
    mlib_s32 scan = pRasInfo->scanStride;
    mlib_u8  *dstBase;
    mlib_s32 j;
    mlib_d64 fgpixel_d;
    mlib_d64 dzero;
    mlib_s32 pix, mask0, mask1, mask, off;
    mlib_f32 fzero, fgpixel_f;
    mlib_s32 max_width = BUFF_SIZE;

    INIT_FG

    fzero = vis_fzeros();
    dzero = vis_fzero();
    fgpixel_f = vis_ldfa_ASI_PL(&fgpixel);
    fgpixel_d = vis_freg_pair(fgpixel_f, fgpixel_f);
    fgpixel = *(mlib_u32*)&fgpixel_f;

    for (glyphCounter = 0; glyphCounter < totalGlyphs; glyphCounter++) {
        DEF_GLYPH(4);

        if (((mlib_s32)dstBase | scan) & 3) {
            if (width > max_width) {
                if (pbuff != buff) {
                    mlib_free(pbuff);
                }
                pbuff = mlib_malloc(width*sizeof(mlib_s32));
                if (pbuff == NULL) return;
                max_width = width;
            }
        }

        for (j = 0; j < height; j++) {
            mlib_u8 *src = (void*)pixels;
            mlib_u32 *dst, *dst_end;
            mlib_f32 ss;

            if ((mlib_s32)dstBase & 3) {
                COPY_NA(dstBase, pbuff, width*sizeof(mlib_s32));
                dst = pbuff;
            } else {
                dst = (void*)dstBase;
            }
            dst_end = dst + width;

            while (((mlib_s32)dst & 7) && (dst < dst_end)) {
                pix = *src++;
                if (pix) STORE_INT;
                dst++;
            }

            off = (mlib_s32)src & 3;
            ss = *(mlib_f32*)(src - off);
            mask1 = vis_fcmpne16(vis_fpmerge(ss, fzero), dzero);

#pragma pipeloop(0)
            for (; dst <= (dst_end - 4); dst += 4) {
                mask0 = mask1;
                src += 4;
                ss = *(mlib_f32*)(src - off);
                mask1 = vis_fcmpne16(vis_fpmerge(ss, fzero), dzero);
                mask = (mask0 << off) | (mask1 >> (4 - off));
                STORE_D64(32, dst, mask >> 2);
                STORE_D64(32, dst + 2, mask);
            }

            while (dst < dst_end) {
                pix = *src++;
                if (pix) STORE_INT;
                dst++;
            }

            if ((mlib_s32)dstBase & 3) {
                COPY_NA(pbuff, dstBase, width*sizeof(mlib_s32));
            }

            PTR_ADD(dstBase, scan);
            pixels += rowBytes;
        }
    }

    if (pbuff != buff) {
        mlib_free(pbuff);
    }
}

/***************************************************************/

#endif /* JAVA2D_NO_MLIB */
