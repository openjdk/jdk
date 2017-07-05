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

#define Gray2Rgb(x)    \
    (x << 16) | (x << 8) | x

/***************************************************************/

#define INT_RGB(r, g, b)       \
    ((r << 16) | (g << 8) | b)

/***************************************************************/

void ADD_SUFF(IntRgbToIntArgbConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, mask;
    mlib_s32 i, i0, j;

    if (dstScan == 4*width && srcScan == 4*width) {
        width *= height;
        height = 1;
    }

    mask = vis_to_double_dup(0xFF000000);

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_f32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            dst[i] = vis_fors(src[i], vis_read_hi(mask));
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            dd = vis_freg_pair(src[i], src[i + 1]);

            *(mlib_d64*)(dst + i) = vis_for(dd, mask);
        }

        if (i < width) {
            dst[i] = vis_fors(src[i], vis_read_hi(mask));
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbToIntArgbScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, mask;
    mlib_s32 j;

    mask = vis_to_double_dup(0xFF000000);

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_f32 *dst = dstBase;
        mlib_f32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            *dst++ = vis_fors(src[tmpsxloc >> shift], vis_read_hi(mask));
            tmpsxloc += sxinc;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            dd = vis_freg_pair(src[tmpsxloc >> shift],
                               src[(tmpsxloc + sxinc) >> shift]);
            *(mlib_d64*)dst = vis_for(dd, mask);
            tmpsxloc += 2*sxinc;
        }

        for (; dst < dst_end; dst++) {
            *dst++ = vis_fors(src[tmpsxloc >> shift], vis_read_hi(mask));
            tmpsxloc += sxinc;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

#define BGR_TO_ARGB {                                          \
    mlib_d64 sda, sdb, sdc, sdd, sde, sdf;                     \
    mlib_d64 s_1, s_2, s_3, a13, b13, a02, b02;                \
                                                               \
    sda = vis_fpmerge(vis_read_hi(sd0), vis_read_lo(sd1));     \
    sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_hi(sd2));     \
    sdc = vis_fpmerge(vis_read_hi(sd1), vis_read_lo(sd2));     \
                                                               \
    sdd = vis_fpmerge(vis_read_hi(sda), vis_read_lo(sdb));     \
    sde = vis_fpmerge(vis_read_lo(sda), vis_read_hi(sdc));     \
    sdf = vis_fpmerge(vis_read_hi(sdb), vis_read_lo(sdc));     \
                                                               \
    s_3 = vis_fpmerge(vis_read_hi(sdd), vis_read_lo(sde));     \
    s_2 = vis_fpmerge(vis_read_lo(sdd), vis_read_hi(sdf));     \
    s_1 = vis_fpmerge(vis_read_hi(sde), vis_read_lo(sdf));     \
                                                               \
    a13 = vis_fpmerge(vis_read_hi(s_1), vis_read_hi(s_3));     \
    b13 = vis_fpmerge(vis_read_lo(s_1), vis_read_lo(s_3));     \
    a02 = vis_fpmerge(vis_read_hi(s_0), vis_read_hi(s_2));     \
    b02 = vis_fpmerge(vis_read_lo(s_0), vis_read_lo(s_2));     \
                                                               \
    dd0 = vis_fpmerge(vis_read_hi(a02), vis_read_hi(a13));     \
    dd1 = vis_fpmerge(vis_read_lo(a02), vis_read_lo(a13));     \
    dd2 = vis_fpmerge(vis_read_hi(b02), vis_read_hi(b13));     \
    dd3 = vis_fpmerge(vis_read_lo(b02), vis_read_lo(b13));     \
}

/***************************************************************/

void ADD_SUFF(ThreeByteBgrToIntRgbConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 *sp;
    mlib_d64 s_0;
    mlib_d64 s0, s1, s2, s3, sd0, sd1, sd2, dd0, dd1, dd2, dd3;
    mlib_s32 i, i0, j;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_s32 *dst = dstBase;

            for (i = 0; i < width; i++) {
                dst[i] = INT_RGB(src[3*i + 2], src[3*i + 1], src[3*i]);
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (srcScan == 3*width && dstScan == 4*width) {
        width *= height;
        height = 1;
    }

    s_0 = vis_fzero();

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_f32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            ((mlib_s32*)dst)[i] = INT_RGB(src[3*i + 2], src[3*i + 1], src[3*i]);
            i0 = 1;
        }

        sp = vis_alignaddr(src, 3*i0);
        s3 = *sp++;

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 8; i += 8) {
            s0 = s3;
            s1 = *sp++;
            s2 = *sp++;
            s3 = *sp++;
            sd0 = vis_faligndata(s0, s1);
            sd1 = vis_faligndata(s1, s2);
            sd2 = vis_faligndata(s2, s3);

            BGR_TO_ARGB

            *(mlib_d64*)(dst + i    ) = dd0;
            *(mlib_d64*)(dst + i + 2) = dd1;
            *(mlib_d64*)(dst + i + 4) = dd2;
            *(mlib_d64*)(dst + i + 6) = dd3;
        }

        for (; i < width; i++) {
            ((mlib_s32*)dst)[i] = INT_RGB(src[3*i + 2], src[3*i + 1], src[3*i]);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ThreeByteBgrToIntRgbScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, maskFF;
    mlib_s32 i, i0, i1, j;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_s32 *dst = dstBase;
            mlib_s32 *dst_end = dst + width;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (; dst < dst_end; dst++) {
                i = tmpsxloc >> shift;
                tmpsxloc += sxinc;
                *(mlib_s32*)dst = INT_RGB(src[3*i + 2], src[3*i + 1], src[3*i]);
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    maskFF = vis_fzero();

    vis_alignaddr(NULL, 7);

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_f32 *dst = dstBase;
        mlib_f32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            i = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            *(mlib_s32*)dst = INT_RGB(src[3*i + 2], src[3*i + 1], src[3*i]);
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            i0 = tmpsxloc >> shift;
            i1 = (tmpsxloc + sxinc) >> shift;
            tmpsxloc += 2*sxinc;

            dd = vis_faligndata(vis_ld_u8(src + 3*i1    ), dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i1 + 1), dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i1 + 2), dd);
            dd = vis_faligndata(maskFF, dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i0    ), dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i0 + 1), dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i0 + 2), dd);
            dd = vis_faligndata(maskFF, dd);

            *(mlib_d64*)dst = dd;
        }

        for (; dst < dst_end; dst++) {
            i = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            *(mlib_s32*)dst = INT_RGB(src[3*i + 2], src[3*i + 1], src[3*i]);
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteGrayToIntRgbConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 d0, d1, d2, d3;
    mlib_f32 ff, aa = vis_fzero();
    mlib_s32 i, j, x;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_s32 *dst = dstBase;

            for (i = 0; i < width; i++) {
                x = src[i];
                dst[i] = Gray2Rgb(x);
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
            *dst++ = Gray2Rgb(x);
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
            *dst++ = Gray2Rgb(x);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteGrayToIntRgbScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 d0, d1, d2, d3, dd;
    mlib_f32 ff, aa = vis_fzero();
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
                dst[i] = Gray2Rgb(x);
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
            *dst++ = Gray2Rgb(x);
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbBmToIntRgbXparOver)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd;
    mlib_s32 i, i0, j, mask;

    if (dstScan == 4*width && srcScan == 4*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_f32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            if (*(mlib_u8*)(src + i)) {
                dst[i] = src[i];
            }
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            dd = vis_freg_pair(src[i], src[i + 1]);

            mask = (((-*(mlib_u8*)(src + i)) >> 31) & 2) |
                   (((-*(mlib_u8*)(src + i + 1)) >> 31) & 1);
            vis_pst_32(dd, dst + i, mask);
        }

        if (i < width) {
            if (*(mlib_u8*)(src + i)) {
                dst[i] = src[i];
            }
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbBmToIntRgbXparBgCopy)(BCOPY_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, d_bgpixel;
    mlib_s32 i, i0, j, mask;

    if (dstScan == 4*width && srcScan == 4*width) {
        width *= height;
        height = 1;
    }

    d_bgpixel = vis_to_double_dup(bgpixel);

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_f32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            if (*(mlib_u8*)(src + i)) {
                dst[i] = src[i];
            } else {
                dst[i] = vis_read_hi(d_bgpixel);
            }
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            dd = vis_freg_pair(src[i], src[i + 1]);

            mask = (((-*(mlib_u8*)(src + i)) >> 31) & 2) |
                   (((-*(mlib_u8*)(src + i + 1)) >> 31) & 1);
            *(mlib_d64*)(dst + i) = d_bgpixel;
            vis_pst_32(dd, dst + i, mask);
        }

        if (i < width) {
            if (*(mlib_u8*)(src + i)) {
                dst[i] = src[i];
            } else {
                dst[i] = vis_read_hi(d_bgpixel);
            }
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbDrawGlyphListAA)(GLYPH_LIST_PARAMS)
{
    mlib_s32 glyphCounter;
    mlib_s32 scan = pRasInfo->scanStride;
    mlib_u8  *dstBase;
    mlib_s32 j;
    mlib_d64 dmix0, dmix1, dd, d0, d1, e0, e1, fgpixel_d;
    mlib_d64 done, done16, d_half, maskRGB, dzero;
    mlib_s32 pix, mask, mask_z;
    mlib_f32 srcG_f;

    done = vis_to_double_dup(0x7fff7fff);
    done16 = vis_to_double_dup(0x7fff);
    d_half = vis_to_double_dup((1 << (16 + 6)) | (1 << 6));

    fgpixel_d = vis_to_double_dup(fgpixel);
    srcG_f = vis_to_float(argbcolor);
    maskRGB = vis_to_double_dup(0xffffff);
    dzero = vis_fzero();

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
                    *(mlib_f32*)dst = vis_fands(vis_fpack16(dd),
                                                vis_read_hi(maskRGB));
                    if (pix == 255) *(mlib_f32*)dst = vis_read_hi(fgpixel_d);
                }
                dst++;
            }

#pragma pipeloop(0)
            for (; dst <= (dst_end - 2); dst += 2) {
                dmix0 = vis_freg_pair(((mlib_f32 *)vis_mul8s_tbl)[src[0]],
                                      ((mlib_f32 *)vis_mul8s_tbl)[src[1]]);
                mask = vis_fcmplt32(dmix0, done16);
                mask_z = vis_fcmpne32(dmix0, dzero);
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
                dd = vis_fand(dd, maskRGB);

                vis_pst_32(fgpixel_d, dst, mask_z);
                vis_pst_32(dd, dst, mask & mask_z);
            }

            while (dst < dst_end) {
                pix = *src++;
                if (pix) {
                    dd = vis_fpadd16(MUL8_VIS(srcG_f, pix), d_half);
                    dd = vis_fpadd16(MUL8_VIS(*(mlib_f32*)dst, 255 - pix), dd);
                    *(mlib_f32*)dst = vis_fands(vis_fpack16(dd),
                                                vis_read_hi(maskRGB));
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
