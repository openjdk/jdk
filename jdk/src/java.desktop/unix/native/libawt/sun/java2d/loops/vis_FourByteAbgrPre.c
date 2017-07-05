/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

#define FUNC_CONVERT(TYPE, OPER)                                             \
void ADD_SUFF(TYPE##ToFourByteAbgrPre##OPER)(BLIT_PARAMS)                    \
{                                                                            \
    mlib_d64 buff[BUFF_SIZE/2];                                              \
    void     *pbuff = buff;                                                  \
    mlib_s32 dstScan = pDstInfo->scanStride;                                 \
    mlib_s32 srcScan = pSrcInfo->scanStride;                                 \
    mlib_s32 j;                                                              \
                                                                             \
    if (width > BUFF_SIZE) pbuff = mlib_malloc(width*sizeof(mlib_s32));      \
                                                                             \
    for (j = 0; j < height; j++) {                                           \
        ADD_SUFF(TYPE##ToIntArgbPre##OPER)(srcBase, pbuff, width, 1,         \
                                           pSrcInfo, pDstInfo,               \
                                           pPrim, pCompInfo);                \
                                                                             \
        ADD_SUFF(IntArgbToFourByteAbgrConvert)(pbuff, dstBase, width, 1,     \
                                               pSrcInfo, pDstInfo,           \
                                               pPrim, pCompInfo);            \
                                                                             \
        PTR_ADD(dstBase, dstScan);                                           \
        PTR_ADD(srcBase, srcScan);                                           \
    }                                                                        \
                                                                             \
    if (pbuff != buff) {                                                     \
        mlib_free(pbuff);                                                    \
    }                                                                        \
}

/***************************************************************/

#define FUNC_SCALE_1(TYPE, OPER)                                             \
void ADD_SUFF(TYPE##ToFourByteAbgrPre##OPER)(SCALE_PARAMS)                   \
{                                                                            \
    mlib_d64 buff[BUFF_SIZE/2];                                              \
    void     *pbuff = buff;                                                  \
    mlib_s32 dstScan = pDstInfo->scanStride;                                 \
    mlib_s32 j;                                                              \
                                                                             \
    if (width > BUFF_SIZE) pbuff = mlib_malloc(width*sizeof(mlib_s32));      \
                                                                             \
    for (j = 0; j < height; j++) {                                           \
        ADD_SUFF(TYPE##ToIntArgbPre##OPER)(srcBase, pbuff, width, 1,         \
                                           sxloc, syloc,                     \
                                           sxinc, syinc, shift,              \
                                           pSrcInfo, pDstInfo,               \
                                           pPrim, pCompInfo);                \
                                                                             \
        ADD_SUFF(IntArgbToFourByteAbgrConvert)(pbuff, dstBase, width, 1,     \
                                               pSrcInfo, pDstInfo,           \
                                               pPrim, pCompInfo);            \
                                                                             \
        PTR_ADD(dstBase, dstScan);                                           \
        syloc += syinc;                                                      \
    }                                                                        \
                                                                             \
    if (pbuff != buff) {                                                     \
        mlib_free(pbuff);                                                    \
    }                                                                        \
}

/***************************************************************/

#define FUNC_INDEXED(TYPE, OPER, PARAMS, CALL_PARAMS)                  \
void ADD_SUFF(TYPE##ToFourByteAbgrPre##OPER)(PARAMS)                   \
{                                                                      \
    SurfaceDataRasInfo new_src[1];                                     \
    jint *pixLut = pSrcInfo->lutBase;                                  \
    mlib_s32 buff[256];                                                \
                                                                       \
    ADD_SUFF(IntArgbToIntArgbPreConvert)(pixLut, buff, 256, 1,         \
                                         pSrcInfo, pDstInfo,           \
                                         pPrim, pCompInfo);            \
                                                                       \
    new_src->lutBase = buff;                                           \
    new_src->scanStride = pSrcInfo->scanStride;                        \
    pSrcInfo = new_src;                                                \
                                                                       \
    ADD_SUFF(TYPE##ToFourByteAbgr##OPER)(CALL_PARAMS);                 \
}

/***************************************************************/

void ADD_SUFF(FourByteAbgrPreToIntArgbConvert)(BLIT_PARAMS)
{
    ADD_SUFF(FourByteAbgrToIntArgbConvert)(BLIT_CALL_PARAMS);
    pSrcInfo = pDstInfo;
    srcBase = dstBase;
    ADD_SUFF(IntArgbPreToIntArgbConvert)(BLIT_CALL_PARAMS);
}

/***************************************************************/

void ADD_SUFF(FourByteAbgrPreToIntArgbScaleConvert)(SCALE_PARAMS)
{
    ADD_SUFF(FourByteAbgrToIntArgbScaleConvert)(SCALE_CALL_PARAMS);
    pSrcInfo = pDstInfo;
    srcBase = dstBase;
    ADD_SUFF(IntArgbPreToIntArgbConvert)(BLIT_CALL_PARAMS);
}

/***************************************************************/

FUNC_CONVERT(ByteGray, Convert)
FUNC_CONVERT(IntArgb,  Convert)
FUNC_CONVERT(IntRgb,   Convert)
FUNC_CONVERT(ThreeByteBgr, Convert)

FUNC_SCALE_1(ByteGray, ScaleConvert)
FUNC_SCALE_1(IntArgb,  ScaleConvert)
FUNC_SCALE_1(IntRgb,   ScaleConvert)
FUNC_SCALE_1(ThreeByteBgr, ScaleConvert)

FUNC_INDEXED(ByteIndexed,   Convert,       BLIT_PARAMS,  BLIT_CALL_PARAMS)
FUNC_INDEXED(ByteIndexedBm, XparOver,      BLIT_PARAMS,  BLIT_CALL_PARAMS)
FUNC_INDEXED(ByteIndexedBm, XparBgCopy,    BCOPY_PARAMS, BCOPY_CALL_PARAMS)
FUNC_INDEXED(ByteIndexedBm, ScaleXparOver, SCALE_PARAMS, SCALE_CALL_PARAMS)
FUNC_INDEXED(ByteIndexed,   ScaleConvert,  SCALE_PARAMS, SCALE_CALL_PARAMS)

/***************************************************************/

void ADD_SUFF(FourByteAbgrPreDrawGlyphListAA)(SurfaceDataRasInfo * pRasInfo,
                                              ImageRef *glyphs,
                                              jint totalGlyphs,
                                              jint fgpixel, jint argbcolor,
                                              jint clipLeft, jint clipTop,
                                              jint clipRight, jint clipBottom,
                                              NativePrimitive * pPrim,
                                              CompositeInfo * pCompInfo)
{
    mlib_d64 buff[BUFF_SIZE/2];
    void     *pbuff = buff;
    mlib_s32 glyphCounter;
    mlib_s32 scan = pRasInfo->scanStride;
    mlib_u8  *dstBase;
    mlib_s32 solidpix0, solidpix1, solidpix2, solidpix3;
    mlib_s32 i, j;
    mlib_d64 dmix0, dmix1, dd, d0, d1, e0, e1;
    mlib_d64 done, d_half;
    mlib_s32 pix;
    mlib_f32 srcG_f;
    mlib_s32 max_width = BUFF_SIZE;

    solidpix0 = fgpixel;
    solidpix1 = fgpixel >> 8;
    solidpix2 = fgpixel >> 16;
    solidpix3 = fgpixel >> 24;

    done = vis_to_double_dup(0x7fff7fff);
    d_half = vis_to_double_dup((1 << (16 + 6)) | (1 << 6));

    srcG_f = vis_to_float(argbcolor);
    ARGB2ABGR_FL(srcG_f);

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
            mlib_u8  *src = (void*)pixels;
            mlib_s32 *dst, *dst_end;
            mlib_u8  *dst8;
            mlib_u8* dst_start = dstBase;

            /*
             * Typically the inner loop here works on Argb input data, an
             * Argb color, and produces ArgbPre output data.  To use that
             * standard approach we would need a FourByteAbgrPre to IntArgb
             * converter for the front end and an IntArgbPre to FourByteAbgrPre
             * converter for the back end.  The converter exists for the
             * front end, but it is a workaround implementation that uses a 2
             * stage conversion and an intermediate buffer that is allocated
             * on every call.  The converter for the back end doesn't really
             * exist, but we could reuse the IntArgb to FourByteAbgr converter
             * to do the same work - at the cost of swapping the components as
             * we copy the data back.  All of this is more work than we really
             * need so we use an alternate procedure:
             * - Copy the data into an int-aligned temporary buffer (if needed)
             * - Convert the data from FourByteAbgrPre to IntAbgr by using the
             * IntArgbPre to IntArgb converter in the int-aligned buffer.
             * - Swap the color data to Abgr so that the inner loop goes from
             * IntAbgr data to IntAbgrPre data
             * - Simply copy the IntAbgrPre data back into place.
             */
            if (((mlib_s32)dstBase) & 3) {
                COPY_NA(dstBase, pbuff, width*sizeof(mlib_s32));
                dst_start = pbuff;
            }
            ADD_SUFF(IntArgbPreToIntArgbConvert)(dst_start, pbuff, width, 1,
                                                      pRasInfo, pRasInfo,
                                                      pPrim, pCompInfo);

            vis_write_gsr(0 << 3);

            dst = pbuff;
            dst_end = dst + width;

            if ((mlib_s32)dst & 7) {
                pix = *src++;
                dd = vis_fpadd16(MUL8_VIS(srcG_f, pix), d_half);
                dd = vis_fpadd16(MUL8_VIS(*(mlib_f32*)dst, 255 - pix), dd);
                *(mlib_f32*)dst = vis_fpack16(dd);
                dst++;
            }

#pragma pipeloop(0)
            for (; dst <= (dst_end - 2); dst += 2) {
                dmix0 = vis_freg_pair(((mlib_f32 *)vis_mul8s_tbl)[src[0]],
                                      ((mlib_f32 *)vis_mul8s_tbl)[src[1]]);
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
            }

            while (dst < dst_end) {
                pix = *src++;
                dd = vis_fpadd16(MUL8_VIS(srcG_f, pix), d_half);
                dd = vis_fpadd16(MUL8_VIS(*(mlib_f32*)dst, 255 - pix), dd);
                *(mlib_f32*)dst = vis_fpack16(dd);
                dst++;
            }

            COPY_NA(pbuff, dstBase, width*sizeof(mlib_s32));

            src = (void*)pixels;
            dst8 = (void*)dstBase;

#pragma pipeloop(0)
            for (i = 0; i < width; i++) {
                if (src[i] == 255) {
                    dst8[4*i    ] = solidpix0;
                    dst8[4*i + 1] = solidpix1;
                    dst8[4*i + 2] = solidpix2;
                    dst8[4*i + 3] = solidpix3;
                }
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
