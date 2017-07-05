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

#include "java2d_Mlib.h"
#include "SurfaceData.h"

#include "mlib_ImageZoom.h"

/***************************************************************/

#define DEFINE_ISO_COPY(FUNC, ANYTYPE)               \
void ADD_SUFF(ANYTYPE##FUNC)(BLIT_PARAMS)            \
{                                                    \
    mlib_s32 srcScan = pSrcInfo->scanStride;         \
    mlib_s32 dstScan = pDstInfo->scanStride;         \
    mlib_s32 xsize = width*ANYTYPE##PixelStride;     \
    mlib_s32 i;                                      \
                                                     \
    if (srcScan == xsize && dstScan == xsize) {      \
        xsize *= height;                             \
        height = 1;                                  \
    }                                                \
                                                     \
    for (i = 0; i < height; i++) {                   \
        mlib_ImageCopy_na(srcBase, dstBase, xsize);  \
        srcBase = (mlib_u8*)srcBase + srcScan;       \
        dstBase = (mlib_u8*)dstBase + dstScan;       \
    }                                                \
}

DEFINE_ISO_COPY(IsomorphicCopy, Any3Byte)
DEFINE_ISO_COPY(IsomorphicCopy, Any4Byte)
DEFINE_ISO_COPY(IsomorphicCopy, AnyByte)
DEFINE_ISO_COPY(IsomorphicCopy, AnyInt)
DEFINE_ISO_COPY(IsomorphicCopy, AnyShort)

/***************************************************************/

#define SET_PIX(index, chan)         \
    dst_ptr[index] = pixel##chan

#define W_LEVEL_1   8
#define W_LEVEL_3  16
#define W_LEVEL_4   8

#define DEFINE_SET_RECT(FUNC, ANYTYPE, NCHAN)                       \
void ADD_SUFF(ANYTYPE##FUNC)(SurfaceDataRasInfo * pRasInfo,         \
                             jint lox, jint loy, jint hix,          \
                             jint hiy, jint pixel,                  \
                             NativePrimitive * pPrim,               \
                             CompositeInfo * pCompInfo)             \
{                                                                   \
    mlib_image dst[1];                                              \
    mlib_s32 dstScan = pRasInfo->scanStride;                        \
    mlib_s32 height = hiy - loy;                                    \
    mlib_s32 width  = hix - lox;                                    \
    mlib_u8  *dstBase = (mlib_u8*)(pRasInfo->rasBase);              \
    mlib_s32 c_arr[4];                                              \
                                                                    \
    dstBase += loy*dstScan + lox*ANYTYPE##PixelStride;              \
                                                                    \
    if (width <= W_LEVEL_##NCHAN) {                                 \
        EXTRACT_CONST_##NCHAN(pixel);                               \
                                                                    \
        LOOP_DST(ANYTYPE, NCHAN, dstBase, dstScan, SET_PIX);        \
        return;                                                     \
    }                                                               \
                                                                    \
    STORE_CONST_##NCHAN(c_arr, pixel);                              \
                                                                    \
    MLIB_IMAGE_SET(dst, MLIB_##ANYTYPE, NCHAN,                      \
                   width, height, dstScan, dstBase);                \
                                                                    \
    mlib_ImageClear(dst, c_arr);                                    \
}

DEFINE_SET_RECT(SetRect, Any3Byte, 3)
DEFINE_SET_RECT(SetRect, Any4Byte, 4)
DEFINE_SET_RECT(SetRect, AnyByte,  1)
DEFINE_SET_RECT(SetRect, AnyInt,   1)
DEFINE_SET_RECT(SetRect, AnyShort, 1)

/***************************************************************/

#define XOR_PIX(index, chan)         \
    dst_ptr[index] ^= pixel##chan

#define DEFINE_XOR_RECT(FUNC, ANYTYPE, NCHAN)                       \
void ADD_SUFF(ANYTYPE##FUNC)(SurfaceDataRasInfo * pRasInfo,         \
                             jint lox, jint loy, jint hix,          \
                             jint hiy, jint pixel,                  \
                             NativePrimitive * pPrim,               \
                             CompositeInfo * pCompInfo)             \
{                                                                   \
    mlib_image dst[1];                                              \
    mlib_s32 dstScan = pRasInfo->scanStride;                        \
    mlib_s32 height = hiy - loy;                                    \
    mlib_s32 width  = hix - lox;                                    \
    mlib_u8  *dstBase = (mlib_u8*)(pRasInfo->rasBase);              \
    mlib_s32 c_arr[4];                                              \
    mlib_s32 xorpixel = pCompInfo->details.xorPixel;                \
    mlib_s32 alphamask = pCompInfo->alphaMask;                      \
                                                                    \
    pixel = (pixel ^ xorpixel) &~ alphamask;                        \
                                                                    \
    dstBase += loy*dstScan + lox*ANYTYPE##PixelStride;              \
                                                                    \
    if (width < 8) {                                                \
        EXTRACT_CONST_##NCHAN(pixel);                               \
                                                                    \
        LOOP_DST(ANYTYPE, NCHAN, dstBase, dstScan, XOR_PIX);        \
        return;                                                     \
    }                                                               \
                                                                    \
    STORE_CONST_##NCHAN(c_arr, pixel);                              \
                                                                    \
    MLIB_IMAGE_SET(dst, MLIB_##ANYTYPE, NCHAN,                      \
                   width, height, dstScan, dstBase);                \
                                                                    \
    mlib_ImageConstXor(dst, dst, c_arr);                            \
}

DEFINE_XOR_RECT(XorRect, Any3Byte, 3)
DEFINE_XOR_RECT(XorRect, Any4Byte, 4)
DEFINE_XOR_RECT(XorRect, AnyByte,  1)
DEFINE_XOR_RECT(XorRect, AnyInt,   1)
DEFINE_XOR_RECT(XorRect, AnyShort, 1)

/***************************************************************/

#define XOR_COPY(index, chan)         \
    dst_ptr[index] = dst_ptr[index] ^ src_ptr[index] ^ pixel##chan

#define DEFINE_XOR_COPY(FUNC, ANYTYPE, NCHAN)                  \
void ADD_SUFF(ANYTYPE##FUNC)(void *srcBase,                    \
                             void *dstBase,                    \
                             juint width,                      \
                             juint height,                     \
                             SurfaceDataRasInfo *pSrcInfo,     \
                             SurfaceDataRasInfo *pDstInfo,     \
                             NativePrimitive *pPrim,           \
                             CompositeInfo *pCompInfo)         \
{                                                              \
    mlib_image src[1], dst[1];                                 \
    mlib_s32 srcScan = pSrcInfo->scanStride;                   \
    mlib_s32 dstScan = pDstInfo->scanStride;                   \
    mlib_s32 c_arr[4];                                         \
    mlib_s32 pixel  = pCompInfo->details.xorPixel;             \
                                                               \
    if (width < 8*sizeof(ANYTYPE##DataType)) {                 \
        EXTRACT_CONST_##NCHAN(pixel);                          \
                                                               \
        LOOP_DST_SRC(ANYTYPE, NCHAN, dstBase, dstScan,         \
                     srcBase, srcScan, XOR_COPY);              \
        return;                                                \
    }                                                          \
                                                               \
    STORE_CONST_##NCHAN(c_arr, pixel);                         \
                                                               \
    MLIB_IMAGE_SET(src, MLIB_##ANYTYPE, NCHAN,                 \
                   width, height, srcScan, srcBase);           \
    MLIB_IMAGE_SET(dst, MLIB_##ANYTYPE, NCHAN,                 \
                   width, height, dstScan, dstBase);           \
                                                               \
    mlib_ImageXor(dst, dst, src);                              \
    mlib_ImageConstXor(dst, dst, c_arr);                       \
}

DEFINE_XOR_COPY(IsomorphicXorCopy, Any3Byte, 3)
DEFINE_XOR_COPY(IsomorphicXorCopy, Any4Byte, 4)
DEFINE_XOR_COPY(IsomorphicXorCopy, AnyByte,  1)
DEFINE_XOR_COPY(IsomorphicXorCopy, AnyInt,   1)
DEFINE_XOR_COPY(IsomorphicXorCopy, AnyShort, 1)

/***************************************************************/

#define DEFINE_SET_SPANS(FUNC, ANYTYPE, NCHAN)                      \
void ADD_SUFF(ANYTYPE##FUNC)(SurfaceDataRasInfo * pRasInfo,         \
                             SpanIteratorFuncs * pSpanFuncs,        \
                             void *siData, jint pixel,              \
                             NativePrimitive * pPrim,               \
                             CompositeInfo * pCompInfo)             \
{                                                                   \
    mlib_image dst[1];                                              \
    mlib_s32 dstScan = pRasInfo->scanStride;                        \
    mlib_s32 height;                                                \
    mlib_s32 width;                                                 \
    mlib_u8  *dstBase = (mlib_u8*)(pRasInfo->rasBase), *pdst;       \
    mlib_s32 c_arr[4];                                              \
    jint     bbox[4];                                               \
                                                                    \
    STORE_CONST_##NCHAN(c_arr, pixel);                              \
                                                                    \
    while ((*pSpanFuncs->nextSpan)(siData, bbox)) {                 \
        mlib_s32 lox = bbox[0];                                     \
        mlib_s32 loy = bbox[1];                                     \
        mlib_s32 width  = bbox[2] - lox;                            \
        mlib_s32 height = bbox[3] - loy;                            \
                                                                    \
        pdst = dstBase + loy*dstScan + lox*ANYTYPE##PixelStride;    \
                                                                    \
        MLIB_IMAGE_SET(dst, MLIB_##ANYTYPE, NCHAN_##ANYTYPE,        \
                       width, height, dstScan, pdst);               \
                                                                    \
        mlib_ImageClear(dst, c_arr);                                \
    }                                                               \
}

DEFINE_SET_SPANS(SetSpans, Any3Byte, 3)
DEFINE_SET_SPANS(SetSpans, Any4Byte, 4)
DEFINE_SET_SPANS(SetSpans, AnyByte,  1)
DEFINE_SET_SPANS(SetSpans, AnyInt,   1)
DEFINE_SET_SPANS(SetSpans, AnyShort, 1)

/***************************************************************/

#define DEFINE_XOR_SPANS(FUNC, ANYTYPE, NCHAN)                      \
void ADD_SUFF(ANYTYPE##FUNC)(SurfaceDataRasInfo * pRasInfo,         \
                             SpanIteratorFuncs * pSpanFuncs,        \
                             void *siData, jint pixel,              \
                             NativePrimitive * pPrim,               \
                             CompositeInfo * pCompInfo)             \
{                                                                   \
    mlib_image dst[1];                                              \
    mlib_s32 dstScan = pRasInfo->scanStride;                        \
    mlib_s32 height;                                                \
    mlib_s32 width;                                                 \
    mlib_u8  *dstBase = (mlib_u8*)(pRasInfo->rasBase), *pdst;       \
    mlib_s32 c_arr[4];                                              \
    mlib_s32 xorpixel = pCompInfo->details.xorPixel;                \
    mlib_s32 alphamask = pCompInfo->alphaMask;                      \
    jint     bbox[4];                                               \
                                                                    \
    pixel = (pixel ^ xorpixel) &~ alphamask;                        \
                                                                    \
    STORE_CONST_##NCHAN(c_arr, pixel);                              \
                                                                    \
    while ((*pSpanFuncs->nextSpan)(siData, bbox)) {                 \
        mlib_s32 lox = bbox[0];                                     \
        mlib_s32 loy = bbox[1];                                     \
        mlib_s32 width  = bbox[2] - lox;                            \
        mlib_s32 height = bbox[3] - loy;                            \
                                                                    \
        pdst = dstBase + loy*dstScan + lox*ANYTYPE##PixelStride;    \
                                                                    \
        MLIB_IMAGE_SET(dst, MLIB_##ANYTYPE, NCHAN_##ANYTYPE,        \
                       width, height, dstScan, pdst);               \
                                                                    \
        mlib_ImageConstXor(dst, dst, c_arr);                        \
    }                                                               \
}

DEFINE_XOR_SPANS(XorSpans, Any3Byte, 3)
DEFINE_XOR_SPANS(XorSpans, Any4Byte, 4)
DEFINE_XOR_SPANS(XorSpans, AnyByte,  1)
DEFINE_XOR_SPANS(XorSpans, AnyInt,   1)
DEFINE_XOR_SPANS(XorSpans, AnyShort, 1)

/***************************************************************/

#define SCALE_COPY(index, chan)         \
    pDst[chan] = pSrc[index]

#define MLIB_ZOOM_NN_AnyByte  mlib_ImageZoom_U8_1_Nearest(param);
#define MLIB_ZOOM_NN_Any3Byte mlib_ImageZoom_U8_3_Nearest(param);
#define MLIB_ZOOM_NN_AnyShort mlib_ImageZoom_S16_1_Nearest(param);
#define MLIB_ZOOM_NN_AnyInt   mlib_ImageZoom_S32_1_Nearest(param);

#define MLIB_ZOOM_NN_Any4Byte                                      \
{                                                                  \
    mlib_s32 b_align = (mlib_s32)srcBase | (mlib_s32)dstBase |     \
                       srcScan | dstScan;                          \
                                                                   \
    if (!(b_align & 3)) {                                          \
        mlib_ImageZoom_S32_1_Nearest(param);                       \
    } else if (!(b_align & 1)) {                                   \
        mlib_ImageZoom_S16_2_Nearest(param);                       \
    } else {                                                       \
        mlib_ImageZoom_U8_4_Nearest(param);                        \
    }                                                              \
}

#define DEFINE_ISO_SCALE(FUNC, ANYTYPE, NCHAN)                     \
void ADD_SUFF(ANYTYPE##FUNC)(void *srcBase, void *dstBase,         \
                             juint width, juint height,            \
                             jint sxloc, jint syloc,               \
                             jint sxinc, jint syinc,               \
                             jint shift,                           \
                             SurfaceDataRasInfo *pSrcInfo,         \
                             SurfaceDataRasInfo *pDstInfo,         \
                             NativePrimitive *pPrim,               \
                             CompositeInfo *pCompInfo)             \
{                                                                  \
    mlib_work_image param[1];                                      \
    mlib_clipping current[1];                                      \
    mlib_s32 srcScan = pSrcInfo->scanStride;                       \
    mlib_s32 dstScan = pDstInfo->scanStride;                       \
                                                                   \
    if (width <= 32) {                                             \
        ANYTYPE##DataType *pSrc;                                   \
        ANYTYPE##DataType *pDst = dstBase;                         \
        dstScan -= (width) * ANYTYPE##PixelStride;                 \
                                                                   \
        do {                                                       \
            juint w = width;                                       \
            jint  tmpsxloc = sxloc;                                \
            pSrc = srcBase;                                        \
            PTR_ADD(pSrc, (syloc >> shift) * srcScan);             \
            do {                                                   \
                jint i = (tmpsxloc >> shift);                      \
                PROCESS_PIX_##NCHAN(SCALE_COPY);                   \
                pDst += NCHAN;                                     \
                tmpsxloc += sxinc;                                 \
            }                                                      \
            while (--w > 0);                                       \
            PTR_ADD(pDst, dstScan);                                \
            syloc += syinc;                                        \
        }                                                          \
        while (--height > 0);                                      \
        return;                                                    \
    }                                                              \
                                                                   \
    param->current = current;                                      \
                                                                   \
    if (shift <= MLIB_SHIFT /* 16 */) {                            \
        jint dshift = MLIB_SHIFT - shift;                          \
        sxloc <<= dshift;                                          \
        syloc <<= dshift;                                          \
        sxinc <<= dshift;                                          \
        syinc <<= dshift;                                          \
    } else {                                                       \
        jint dshift = shift - MLIB_SHIFT;                          \
        sxloc >>= dshift;                                          \
        syloc >>= dshift;                                          \
        sxinc >>= dshift;                                          \
        syinc >>= dshift;                                          \
    }                                                              \
                                                                   \
    current->width  = width;                                       \
    current->height = height;                                      \
    param->DX = sxinc;                                             \
    param->DY = syinc;                                             \
    param->src_stride = srcScan;                                   \
    param->dst_stride = dstScan;                                   \
    current->srcX = sxloc;                                         \
    current->srcY = syloc;                                         \
    current->sp = (mlib_u8*)srcBase                                \
          + (sxloc >> MLIB_SHIFT)*ANYTYPE##PixelStride             \
          + (syloc >> MLIB_SHIFT)*srcScan;                         \
    current->dp = dstBase;                                         \
                                                                   \
    MLIB_ZOOM_NN_##ANYTYPE                                         \
}

DEFINE_ISO_SCALE(IsomorphicScaleCopy, Any3Byte, 3)
DEFINE_ISO_SCALE(IsomorphicScaleCopy, Any4Byte, 4)
DEFINE_ISO_SCALE(IsomorphicScaleCopy, AnyByte,  1)
DEFINE_ISO_SCALE(IsomorphicScaleCopy, AnyInt,   1)
DEFINE_ISO_SCALE(IsomorphicScaleCopy, AnyShort, 1)

/***************************************************************/

#endif /* JAVA2D_NO_MLIB */
