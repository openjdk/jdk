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

#define Gray2Argb(x)   \
    0xff000000 | (x << 16) | (x << 8) | x

/***************************************************************/

#if VIS >= 0x200

#define BMASK_FOR_ARGB         \
    vis_write_bmask(0x03214765, 0);

#else

#define BMASK_FOR_ARGB

#endif

/***************************************************************/

#define RGB2ABGR_DB(x)         \
    x = vis_for(x, amask);     \
    ARGB2ABGR_DB(x)

/***************************************************************/

#define INSERT_U8_34R                                          \
    sda = vis_fpmerge(vis_read_hi(sd0), vis_read_lo(sd1));     \
    sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_hi(sd2));     \
    sdc = vis_fpmerge(vis_read_hi(sd1), vis_read_lo(sd2));     \
    sdd = vis_fpmerge(vis_read_hi(sda), vis_read_lo(sdb));     \
    sde = vis_fpmerge(vis_read_lo(sda), vis_read_hi(sdc));     \
    sdf = vis_fpmerge(vis_read_hi(sdb), vis_read_lo(sdc));     \
    sdg = vis_fpmerge(vis_read_hi(sdd), vis_read_lo(sde));     \
    sdh = vis_fpmerge(vis_read_lo(sdd), vis_read_hi(sdf));     \
    sdi = vis_fpmerge(vis_read_hi(sde), vis_read_lo(sdf));     \
    sdj = vis_fpmerge(vis_read_hi(sdg), vis_read_hi(sdi));     \
    sdk = vis_fpmerge(vis_read_lo(sdg), vis_read_lo(sdi));     \
    sdl = vis_fpmerge(vis_read_hi(sFF), vis_read_hi(sdh));     \
    sdm = vis_fpmerge(vis_read_lo(sFF), vis_read_lo(sdh));     \
    dd0 = vis_fpmerge(vis_read_hi(sdl), vis_read_hi(sdj));     \
    dd1 = vis_fpmerge(vis_read_lo(sdl), vis_read_lo(sdj));     \
    dd2 = vis_fpmerge(vis_read_hi(sdm), vis_read_hi(sdk));     \
    dd3 = vis_fpmerge(vis_read_lo(sdm), vis_read_lo(sdk))

/***************************************************************/

void IntArgbToIntAbgrConvert_line(mlib_s32 *srcBase,
                                  mlib_s32 *dstBase,
                                  mlib_s32 width)
{
    mlib_s32 *dst_end = dstBase + width;
    mlib_d64 dd;
    mlib_f32 ff;

    BMASK_FOR_ARGB

    if ((mlib_s32)srcBase & 7) {
        ff = *(mlib_f32*)srcBase;
        ARGB2ABGR_FL(ff)
        *(mlib_f32*)dstBase = ff;
        srcBase++;
        dstBase++;
    }

    if ((mlib_s32)dstBase & 7) {
#pragma pipeloop(0)
        for (; dstBase <= (dst_end - 2); dstBase += 2) {
            dd = *(mlib_d64*)srcBase;
            ARGB2ABGR_DB(dd)
            ((mlib_f32*)dstBase)[0] = vis_read_hi(dd);
            ((mlib_f32*)dstBase)[1] = vis_read_lo(dd);
            srcBase += 2;
        }
    } else {
#pragma pipeloop(0)
        for (; dstBase <= (dst_end - 2); dstBase += 2) {
            dd = *(mlib_d64*)srcBase;
            ARGB2ABGR_DB(dd)
            *(mlib_d64*)dstBase = dd;
            srcBase += 2;
        }
    }

    if (dstBase < dst_end) {
        ff = *(mlib_f32*)srcBase;
        ARGB2ABGR_FL(ff)
        *(mlib_f32*)dstBase = ff;
    }
}

/***************************************************************/

void ADD_SUFF(FourByteAbgrToIntArgbConvert)(BLIT_PARAMS)
{
    mlib_u32 *argb = (mlib_u32 *)dstBase;
    mlib_u8  *pabgr = (mlib_u8 *)srcBase;
    mlib_s32 dstScan = (pDstInfo)->scanStride;
    mlib_s32 srcScan = (pSrcInfo)->scanStride;
    mlib_s32 i, j, count, left;
    mlib_d64 w_abgr;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_s32 *dst = dstBase;

            for (i = 0; i < width; i++) {
                *dst++ = (src[0] << 24) | (src[3] << 16) |
                         (src[2] << 8) | (src[1]);
                src += 4;
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (dstScan == 4*width && srcScan == dstScan) {
        width *= height;
        height = 1;
    }
    count = width >> 1;
    left = width & 1;

    BMASK_FOR_ARGB

    if ((((mlib_addr)pabgr & 3) == 0) && ((srcScan & 3) == 0)) {
        mlib_u32 *abgr = (mlib_u32 *)pabgr;

        dstScan >>= 2;
        srcScan >>= 2;

        for (i = 0; i < height; i++, argb += dstScan, abgr += srcScan) {
            if ((((mlib_addr) argb | (mlib_addr) abgr) & 7) == 0) {
                mlib_d64 *d_abgr = (mlib_d64 *) abgr;
                mlib_d64 *d_argb = (mlib_d64 *) argb;

#pragma pipeloop(0)
                for (j = 0; j < count; j++) {
                    w_abgr = d_abgr[j];
                    ARGB2ABGR_DB(w_abgr)
                    d_argb[j] = w_abgr;
                }

                if (left) {
                    w_abgr = d_abgr[count];
                    ARGB2ABGR_DB(w_abgr)
                    ((mlib_f32 *) argb)[2 * count] = vis_read_hi(w_abgr);
                }
            } else {
                mlib_f32 v_abgr0, v_abgr1;

#pragma pipeloop(0)
                for (j = 0; j < count; j++) {
                    v_abgr0 = ((mlib_f32 *) abgr)[2 * j];
                    v_abgr1 = ((mlib_f32 *) abgr)[2 * j + 1];
                    w_abgr = vis_freg_pair(v_abgr0, v_abgr1);
                    ARGB2ABGR_DB(w_abgr)
                    ((mlib_f32 *) argb)[2 * j] = vis_read_hi(w_abgr);
                    ((mlib_f32 *) argb)[2 * j + 1] = vis_read_lo(w_abgr);
                }

                if (left) {
                    v_abgr0 = ((mlib_f32 *) abgr)[2 * count];
                    w_abgr = vis_freg_pair(v_abgr0, 0);
                    ARGB2ABGR_DB(w_abgr)
                    ((mlib_f32 *) argb)[2 * count] = vis_read_hi(w_abgr);
                }
            }
        }
    } else {      /* abgr is not aligned */
        mlib_u8 *abgr = pabgr;
        mlib_d64 *d_abgr, db0, db1;

        dstScan >>= 2;

        for (i = 0; i < height; i++, argb += dstScan, abgr += srcScan) {
            d_abgr = vis_alignaddr(abgr, 0);
            db0 = *d_abgr++;

            if (((mlib_addr) argb & 7) == 0) {
                mlib_d64 *d_argb = (mlib_d64 *) argb;

#pragma pipeloop(0)
                for (j = 0; j < count; j++) {
                    db1 = d_abgr[j];
                    w_abgr = vis_faligndata(db0, db1);
                    db0 = db1;
                    ARGB2ABGR_DB(w_abgr)
                    d_argb[j] = w_abgr;
                }

                if (left) {
                    db1 = d_abgr[j];
                    w_abgr = vis_faligndata(db0, db1);
                    ARGB2ABGR_DB(w_abgr)
                    ((mlib_f32 *) argb)[2 * count] = vis_read_hi(w_abgr);
                }
            } else {
                mlib_d64 w_abgr;

                db1 = *d_abgr++;
                w_abgr = vis_faligndata(db0, db1);
                db0 = db1;
#pragma pipeloop(0)
                for (j = 0; j < count; j++) {
                    ARGB2ABGR_DB(w_abgr)
                    ((mlib_f32 *) argb)[2 * j] = vis_read_hi(w_abgr);
                    ((mlib_f32 *) argb)[2 * j + 1] = vis_read_lo(w_abgr);
                    db1 = d_abgr[j];
                    w_abgr = vis_faligndata(db0, db1);
                    db0 = db1;
                }

                if (left) {
                    ARGB2ABGR_DB(w_abgr)
                    ((mlib_f32 *) argb)[2 * count] = vis_read_hi(w_abgr);
                }
            }
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToFourByteAbgrConvert)(BLIT_PARAMS)
{
    mlib_u32 *argb = (mlib_u32 *)srcBase;
    mlib_u8 *abgr = (mlib_u8 *)dstBase;
    mlib_s32 dstScan = (pDstInfo)->scanStride;
    mlib_s32 srcScan = (pSrcInfo)->scanStride;
    mlib_s32 i, j, count, left;
    mlib_d64 w_abgr;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_s32 *src = srcBase;
            mlib_u8  *dst = dstBase;

            for (i = 0; i < width; i++) {
                mlib_u32 x = *src++;
                dst[0] = x >> 24;
                dst[1] = x;
                dst[2] = x >> 8;
                dst[3] = x >> 16;
                dst += 4;
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (dstScan == 4*width && srcScan == dstScan) {
        width *= height;
        height = 1;
    }
    count = width >> 1;
    left = width & 1;

    BMASK_FOR_ARGB

    srcScan >>= 2;

    for (i = 0; i < height; i++, argb += srcScan, abgr += dstScan) {

        if ((((mlib_addr) abgr | (mlib_addr) argb) & 7) == 0) {
            mlib_d64 *d_argb = (mlib_d64 *) argb;
            mlib_d64 *d_abgr = (mlib_d64 *) abgr;

#pragma pipeloop(0)
            for (j = 0; j < count; j++) {
                w_abgr = d_argb[j];
                ARGB2ABGR_DB(w_abgr)
                d_abgr[j] = w_abgr;
            }

            if (left) {
                w_abgr = d_argb[count];
                ARGB2ABGR_DB(w_abgr)
                ((mlib_f32 *) abgr)[2 * count] = vis_read_hi(w_abgr);
            }

        } else if (((mlib_addr) abgr & 3) == 0) {
            mlib_f32 v_argb0, v_argb1;

#pragma pipeloop(0)
            for (j = 0; j < count; j++) {
                v_argb0 = ((mlib_f32 *) argb)[2 * j];
                v_argb1 = ((mlib_f32 *) argb)[2 * j + 1];
                w_abgr = vis_freg_pair(v_argb0, v_argb1);

                ARGB2ABGR_DB(w_abgr)
                ((mlib_f32 *) abgr)[2 * j] = vis_read_hi(w_abgr);
                ((mlib_f32 *) abgr)[2 * j + 1] = vis_read_lo(w_abgr);
            }

            if (left) {
                v_argb0 = ((mlib_f32 *) argb)[2 * count];
                w_abgr = vis_freg_pair(v_argb0, vis_fzeros());

                ARGB2ABGR_DB(w_abgr)
                ((mlib_f32 *) abgr)[2 * count] = vis_read_hi(w_abgr);
            }

        } else {      /* abgr is not aligned */

            mlib_u8 *pend = abgr + (width << 2) - 1;
            mlib_d64 *d_abgr, db0, db1;
            mlib_s32 emask, off;
            mlib_f32 *f_argb = (mlib_f32 *) argb;

            off = (mlib_addr)abgr & 7;
            vis_alignaddr((void *)(8 - off), 0);
            d_abgr = (mlib_d64 *) (abgr - off);

            db1 = vis_freg_pair(*f_argb++, *f_argb++);
            ARGB2ABGR_DB(db1)
            w_abgr = vis_faligndata(db1, db1);
            emask = vis_edge8(abgr, pend);
            vis_pst_8(w_abgr, d_abgr++, emask);
            db0 = db1;

            db1 = vis_freg_pair(f_argb[0], f_argb[1]);
#pragma pipeloop(0)
            for (; (mlib_addr)d_abgr < (mlib_addr)(pend - 6); ) {
                ARGB2ABGR_DB(db1)
                w_abgr = vis_faligndata(db0, db1);
                *d_abgr++ = w_abgr;
                db0 = db1;
                f_argb += 2;
                db1 = vis_freg_pair(f_argb[0], f_argb[1]);
            }

            if ((mlib_addr)d_abgr <= (mlib_addr)pend) {
                ARGB2ABGR_DB(db1)
                w_abgr = vis_faligndata(db0, db1);
                emask = vis_edge8(d_abgr, pend);
                vis_pst_8(w_abgr, d_abgr, emask);
            }
        }
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbToFourByteAbgrConvert)(BLIT_PARAMS)
{
    mlib_u32 *argb = (mlib_u32 *)srcBase;
    mlib_u8  *abgr = (mlib_u8 *)dstBase;
    mlib_s32 dstScan = (pDstInfo)->scanStride;
    mlib_s32 srcScan = (pSrcInfo)->scanStride;
    mlib_s32 i, j, count, left;
    mlib_d64 w_abgr;
    mlib_d64 amask = vis_to_double_dup(0xFF000000);

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_s32 *src = srcBase;
            mlib_u8  *dst = dstBase;

            for (i = 0; i < width; i++) {
                mlib_u32 x = *src++;
                dst[0] = 0xFF;
                dst[1] = x;
                dst[2] = x >> 8;
                dst[3] = x >> 16;
                dst += 4;
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (dstScan == 4*width && srcScan == dstScan) {
        width *= height;
        height = 1;
    }
    count = width >> 1;
    left = width & 1;

    BMASK_FOR_ARGB

    srcScan >>= 2;

    for (i = 0; i < height; i++, argb += srcScan, abgr += dstScan) {

        if ((((mlib_addr) abgr | (mlib_addr) argb) & 7) == 0) {
            mlib_d64 *d_argb = (mlib_d64 *) argb;
            mlib_d64 *d_abgr = (mlib_d64 *) abgr;

#pragma pipeloop(0)
            for (j = 0; j < count; j++) {
                w_abgr = d_argb[j];
                RGB2ABGR_DB(w_abgr)
                d_abgr[j] = w_abgr;
            }

            if (left) {
                w_abgr = d_argb[count];
                RGB2ABGR_DB(w_abgr)
                ((mlib_f32 *) abgr)[2 * count] = vis_read_hi(w_abgr);
            }

        } else if (((mlib_addr) abgr & 3) == 0) {
            mlib_f32 v_argb0, v_argb1;

#pragma pipeloop(0)
            for (j = 0; j < count; j++) {
                v_argb0 = ((mlib_f32 *) argb)[2 * j];
                v_argb1 = ((mlib_f32 *) argb)[2 * j + 1];
                w_abgr = vis_freg_pair(v_argb0, v_argb1);

                RGB2ABGR_DB(w_abgr)
                ((mlib_f32 *) abgr)[2 * j] = vis_read_hi(w_abgr);
                ((mlib_f32 *) abgr)[2 * j + 1] = vis_read_lo(w_abgr);
            }

            if (left) {
                v_argb0 = ((mlib_f32 *) argb)[2 * count];
                w_abgr = vis_freg_pair(v_argb0, vis_fzeros());

                RGB2ABGR_DB(w_abgr)
                ((mlib_f32 *) abgr)[2 * count] = vis_read_hi(w_abgr);
            }

        } else {      /* abgr is not aligned */

            mlib_u8 *pend = abgr + (width << 2) - 1;
            mlib_d64 *d_abgr, db0, db1;
            mlib_s32 emask, off;
            mlib_f32 *f_argb = (mlib_f32 *) argb;

            off = (mlib_addr)abgr & 7;
            vis_alignaddr((void *)(8 - off), 0);
            d_abgr = (mlib_d64 *) (abgr - off);

            db1 = vis_freg_pair(*f_argb++, *f_argb++);
            RGB2ABGR_DB(db1)
            w_abgr = vis_faligndata(db1, db1);
            emask = vis_edge8(abgr, pend);
            vis_pst_8(w_abgr, d_abgr++, emask);
            db0 = db1;

            db1 = vis_freg_pair(f_argb[0], f_argb[1]);
#pragma pipeloop(0)
            for (; (mlib_addr)d_abgr < (mlib_addr)(pend - 6); ) {
                RGB2ABGR_DB(db1)
                w_abgr = vis_faligndata(db0, db1);
                *d_abgr++ = w_abgr;
                db0 = db1;
                f_argb += 2;
                db1 = vis_freg_pair(f_argb[0], f_argb[1]);
            }

            if ((mlib_addr)d_abgr <= (mlib_addr)pend) {
                RGB2ABGR_DB(db1)
                w_abgr = vis_faligndata(db0, db1);
                emask = vis_edge8(d_abgr, pend);
                vis_pst_8(w_abgr, d_abgr, emask);
            }
        }
    }
}

/***************************************************************/

void ADD_SUFF(ThreeByteBgrToFourByteAbgrConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 sd0, sd1, sd2;
    mlib_d64 dd0, dd1, dd2, dd3;
    mlib_d64 sda, sdb, sdc, sdd;
    mlib_d64 sde, sdf, sdg, sdh;
    mlib_d64 sdi, sdj, sdk, sdl;
    mlib_d64 sdm;
    mlib_d64 sFF;
    mlib_s32 r, g, b;
    mlib_s32 i, j;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8 *src = srcBase;
            mlib_u8 *dst = dstBase;

#pragma pipeloop(0)
            for (i = 0; i < width; i++) {
                dst[0] = 0xFF;
                dst[1] = src[0];
                dst[2] = src[1];
                dst[3] = src[2];
                src += 3;
                dst += 4;
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (dstScan == 4*width && srcScan == 3*width) {
        width *= height;
        height = 1;
    }

    sFF = vis_fone();

    for (j = 0; j < height; j++) {
        mlib_u8 *pSrc = srcBase;
        mlib_u8 *pDst = dstBase;

        if (!(((mlib_s32)pSrc | (mlib_s32)pDst) & 7)) {
#pragma pipeloop(0)
            for (i = 0; i <= ((mlib_s32)width - 8); i += 8) {
                sd0 = ((mlib_d64*)pSrc)[0];
                sd1 = ((mlib_d64*)pSrc)[1];
                sd2 = ((mlib_d64*)pSrc)[2];
                pSrc += 3*8;
                INSERT_U8_34R;
                ((mlib_d64*)pDst)[0] = dd0;
                ((mlib_d64*)pDst)[1] = dd1;
                ((mlib_d64*)pDst)[2] = dd2;
                ((mlib_d64*)pDst)[3] = dd3;
                pDst += 4*8;
            }

            for (; i < width; i++) {
                b = pSrc[0];
                g = pSrc[1];
                r = pSrc[2];
                ((mlib_u16*)pDst)[0] = 0xff00 | b;
                ((mlib_u16*)pDst)[1] = (g << 8) | r;
                pSrc += 3;
                pDst += 4;
            }
        } else if (!((mlib_s32)pDst & 1)) {
#pragma pipeloop(0)
            for (i = 0; i < width; i++) {
                b = pSrc[0];
                g = pSrc[1];
                r = pSrc[2];
                ((mlib_u16*)pDst)[0] = 0xff00 | b;
                ((mlib_u16*)pDst)[1] = (g << 8) | r;
                pSrc += 3;
                pDst += 4;
            }
        } else {
            *pDst++ = 0xff;
#pragma pipeloop(0)
            for (i = 0; i < (mlib_s32)width - 1; i++) {
                b = pSrc[0];
                g = pSrc[1];
                r = pSrc[2];
                ((mlib_u16*)pDst)[0] = (b << 8) | g;
                ((mlib_u16*)pDst)[1] = (r << 8) | 0xff;
                pSrc += 3;
                pDst += 4;
            }
            if (width) {
                pDst[0] = pSrc[0];
                pDst[1] = pSrc[1];
                pDst[2] = pSrc[2];
            }
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

#if 1

#define LOAD_BGR(dd) {                                 \
    mlib_u8  *sp = pSrc - 1 + 3*(tmpsxloc >> shift);   \
    mlib_d64 *ap = (void*)((mlib_addr)sp &~ 7);        \
    vis_alignaddr(sp, 0);                              \
    dd = vis_faligndata(ap[0], ap[1]);                 \
    tmpsxloc += sxinc;                                 \
}

#else

#define LOAD_BGR(dd) {                                 \
    mlib_u8 *sp = pSrc + 3*(tmpsxloc >> shift);        \
    dd = vis_faligndata(vis_ld_u8(sp + 2), dd);        \
    dd = vis_faligndata(vis_ld_u8(sp + 1), dd);        \
    dd = vis_faligndata(vis_ld_u8(sp    ), dd);        \
    dd = vis_faligndata(amask, dd);                    \
    tmpsxloc += sxinc;                                 \
}

#endif

/***************************************************************/

void ADD_SUFF(ThreeByteBgrToFourByteAbgrScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 d0;
    mlib_d64 amask;
    mlib_s32 r, g, b;
    mlib_s32 i, j;

    if (width < 16 /*|| (((mlib_s32)dstBase | dstScan) & 3)*/) {
        for (j = 0; j < height; j++) {
            mlib_u8  *pSrc = srcBase;
            mlib_u8  *pDst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(pSrc, (syloc >> shift) * srcScan);

#pragma pipeloop(0)
            for (i = 0; i < width; i++) {
                mlib_u8 *pp = pSrc + 3*(tmpsxloc >> shift);
                pDst[0] = 0xff;
                pDst[1] = pp[0];
                pDst[2] = pp[1];
                pDst[3] = pp[2];
                tmpsxloc += sxinc;
                pDst += 4;
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    vis_alignaddr(NULL, 7);
    amask = vis_to_double_dup(0xFF000000);

    for (j = 0; j < height; j++) {
        mlib_u8 *pSrc = srcBase;
        mlib_u8 *pDst = dstBase;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(pSrc, (syloc >> shift) * srcScan);

        if (!((mlib_s32)pDst & 3)) {
#pragma pipeloop(0)
            for (i = 0; i < width; i++) {
                LOAD_BGR(d0);
                ((mlib_f32*)pDst)[0] = vis_fors(vis_read_hi(d0),
                                                vis_read_hi(amask));
                pDst += 4;
            }
        } else if (!((mlib_s32)pDst & 1)) {
#pragma pipeloop(0)
            for (i = 0; i < width; i++) {
                mlib_u8 *pp = pSrc + 3*(tmpsxloc >> shift);
                tmpsxloc += sxinc;
                b = pp[0];
                g = pp[1];
                r = pp[2];
                ((mlib_u16*)pDst)[2*i    ] = 0xff00 | b;
                ((mlib_u16*)pDst)[2*i + 1] = (g << 8) | r;
            }
        } else {
            *pDst++ = 0xff;
#pragma pipeloop(0)
            for (i = 0; i < (mlib_s32)width - 1; i++) {
                mlib_u8 *pp = pSrc + 3*(tmpsxloc >> shift);
                tmpsxloc += sxinc;
                b = pp[0];
                g = pp[1];
                r = pp[2];
                ((mlib_u16*)pDst)[2*i    ] = (b << 8) | g;
                ((mlib_u16*)pDst)[2*i + 1] = (r << 8) | 0xff;
            }
            if (width) {
                mlib_u8 *pp = pSrc + 3*(tmpsxloc >> shift);
                tmpsxloc += sxinc;
                pDst[4*i  ] = pp[0];
                pDst[4*i+1] = pp[1];
                pDst[4*i+2] = pp[2];
            }
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteGrayToFourByteAbgrConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 d0, d1, d2, d3;
    mlib_f32 ff, aa = vis_fones();
    mlib_s32 i, j, x;

    if (!(((mlib_s32)dstBase | dstScan) & 3)) {
        ADD_SUFF(ByteGrayToIntArgbConvert)(BLIT_CALL_PARAMS);
        return;
    }

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8 *src = srcBase;
            mlib_u8 *dst = dstBase;

            for (i = 0; i < width; i++) {
                x = *src++;
                dst[0] = 0xff;
                dst[1] = x;
                dst[2] = x;
                dst[3] = x;
                dst += 4;
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
        mlib_u8 *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end;

        dst_end = dst + 4*width;

        while (((mlib_s32)src & 3) && dst < dst_end) {
            x = *src++;
            dst[0] = 0xff;
            dst[1] = x;
            dst[2] = x;
            dst[3] = x;
            dst += 4;
        }

        if (!((mlib_s32)dst & 3)) {
#pragma pipeloop(0)
            for (; dst <= (dst_end - 4*4); dst += 4*4) {
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
        } else {
            mlib_d64 *dp;

            dp = vis_alignaddr(dst, 0);
            d3 = vis_faligndata(dp[0], dp[0]);
            vis_alignaddrl(dst, 0);

#pragma pipeloop(0)
            for (; dst <= (dst_end - 4*4); dst += 4*4) {
                ff = *(mlib_f32*)src;
                d0 = vis_fpmerge(aa, ff);
                d1 = vis_fpmerge(ff, ff);
                d2 = vis_fpmerge(vis_read_hi(d0), vis_read_hi(d1));
                *dp++ = vis_faligndata(d3, d2);
                d3 = vis_fpmerge(vis_read_lo(d0), vis_read_lo(d1));
                *dp++ = vis_faligndata(d2, d3);
                src += 4;
            }

            vis_pst_8(vis_faligndata(d3, d3), dp, vis_edge8(dp, dst - 1));
        }

        while (dst < dst_end) {
            x = *src++;
            dst[0] = 0xff;
            dst[1] = x;
            dst[2] = x;
            dst[3] = x;
            dst += 4;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToFourByteAbgrXorBlit)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_u32 xorpixel = pCompInfo->details.xorPixel;
    mlib_u32 alphamask = pCompInfo->alphaMask;
    mlib_d64 dd, d_xorpixel, d_alphamask, d_zero;
    mlib_s32 i, j, x, neg_mask;

    if (width < 16) {
        xorpixel = (xorpixel << 24) | (xorpixel >> 8);
        alphamask = (alphamask << 24) | (alphamask >> 8);

        for (j = 0; j < height; j++) {
            mlib_s32 *src = srcBase;
            mlib_u8  *dst = dstBase;

            for (i = 0; i < width; i++) {
                x = src[i];
                neg_mask = x >> 31;
                x = (x ^ xorpixel) & (neg_mask &~ alphamask);
                dst[0] ^= x >> 24;
                dst[1] ^= x;
                dst[2] ^= x >> 8;
                dst[3] ^= x >> 16;
                dst += 4;
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (srcScan == 4*width && dstScan == 4*width) {
        width *= height;
        height = 1;
    }

    d_zero = vis_fzero();
    d_xorpixel = vis_freg_pair(vis_ldfa_ASI_PL(&xorpixel),
                               vis_ldfa_ASI_PL(&xorpixel));
    d_alphamask = vis_freg_pair(vis_ldfa_ASI_PL(&alphamask),
                                vis_ldfa_ASI_PL(&alphamask));

    dd = vis_freg_pair(vis_read_hi(d_xorpixel), vis_read_hi(d_alphamask));
    ARGB2ABGR_DB(dd)
    xorpixel = ((mlib_s32*)&dd)[0];
    alphamask = ((mlib_s32*)&dd)[1];

    for (j = 0; j < height; j++) {
        mlib_s32 *src = srcBase;
        mlib_u8  *dst = dstBase;
        mlib_u8  *dst_end;

        dst_end = dst + 4*width;

        if (!((mlib_s32)dst & 7)) {
#pragma pipeloop(0)
            for (; dst <= (dst_end - 8); dst += 8) {
                dd = vis_freg_pair(((mlib_f32*)src)[0], ((mlib_f32*)src)[1]);
                src += 2;
                neg_mask = vis_fcmplt32(dd, d_zero);
                ARGB2ABGR_DB(dd)
                dd = vis_fxor(dd, d_xorpixel);
                dd = vis_fandnot(d_alphamask, dd);
                dd = vis_fxor(dd, *(mlib_d64*)dst);
                vis_pst_32(dd, dst, neg_mask);
            }
        }

        while (dst < dst_end) {
            x = *src++;
            neg_mask = x >> 31;
            x = (x ^ xorpixel) & (neg_mask &~ alphamask);
            dst[0] ^= x >> 24;
            dst[1] ^= x;
            dst[2] ^= x >> 8;
            dst[3] ^= x >> 16;
            dst += 4;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteGrayToFourByteAbgrScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 d0, d1, d2, d3, dd;
    mlib_f32 ff, aa;
    mlib_s32 i, j, x;

/*  if (!(((mlib_s32)dstBase | dstScan) & 3)) {
    ADD_SUFF(ByteGrayToIntArgbScaleConvert)(SCALE_CALL_PARAMS);
    return;
    }*/

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8 *src = srcBase;
            mlib_u8 *dst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                x = src[tmpsxloc >> shift];
                tmpsxloc += sxinc;
                dst[4*i    ] = 0xff;
                dst[4*i + 1] = x;
                dst[4*i + 2] = x;
                dst[4*i + 3] = x;
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    aa = vis_fones();

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        dst_end = dst + 4*width;

        if (!((mlib_s32)dst & 3)) {
            vis_alignaddr(NULL, 7);
#pragma pipeloop(0)
            for (; dst <= (dst_end - 4*4); dst += 4*4) {
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
        } else {
            mlib_d64 *dp;

            dp = vis_alignaddr(dst, 0);
            d3 = vis_faligndata(dp[0], dp[0]);
            vis_alignaddrl(dst, 0);

#pragma pipeloop(0)
            for (; dst <= (dst_end - 4*4); dst += 4*4) {
                mlib_d64 s0, s1, s2, s3;
                s0 = vis_ld_u8(src + ((tmpsxloc          ) >> shift));
                s1 = vis_ld_u8(src + ((tmpsxloc +   sxinc) >> shift));
                s2 = vis_ld_u8(src + ((tmpsxloc + 2*sxinc) >> shift));
                s3 = vis_ld_u8(src + ((tmpsxloc + 3*sxinc) >> shift));
                tmpsxloc += 4*sxinc;
                s0 = vis_fpmerge(vis_read_lo(s0), vis_read_lo(s2));
                s1 = vis_fpmerge(vis_read_lo(s1), vis_read_lo(s3));
                dd = vis_fpmerge(vis_read_lo(s0), vis_read_lo(s1));
                ff = vis_read_lo(dd);
                d0 = vis_fpmerge(aa, ff);
                d1 = vis_fpmerge(ff, ff);
                d2 = vis_fpmerge(vis_read_hi(d0), vis_read_hi(d1));
                *dp++ = vis_faligndata(d3, d2);
                d3 = vis_fpmerge(vis_read_lo(d0), vis_read_lo(d1));
                *dp++ = vis_faligndata(d2, d3);
            }

            vis_pst_8(vis_faligndata(d3, d3), dp, vis_edge8(dp, dst - 1));
        }

        while (dst < dst_end) {
            x = src[tmpsxloc >> shift];
            tmpsxloc += sxinc;
            dst[0] = 0xff;
            dst[1] = x;
            dst[2] = x;
            dst[3] = x;
            dst += 4;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedToFourByteAbgrConvert)(BLIT_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, d_old;
    mlib_s32 i, j, x;

/*  if (!(((mlib_s32)dstBase | dstScan) & 3)) {
    ADD_SUFF(ByteIndexedToIntAbgrConvert)(BLIT_CALL_PARAMS);
    return;
    }*/

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_u8 *src = srcBase;
            mlib_u8 *dst = dstBase;

            for (i = 0; i < width; i++) {
                x = pixLut[src[i]];
                dst[4*i    ] = x >> 24;
                dst[4*i + 1] = x;
                dst[4*i + 2] = x >> 8;
                dst[4*i + 3] = x >> 16;
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

    BMASK_FOR_ARGB

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end;

        dst_end = dst + 4*width;

        if (!((mlib_s32)dst & 7)) {
#pragma pipeloop(0)
            for (; dst <= (dst_end - 2*4); dst += 2*4) {
                dd = vis_freg_pair(((mlib_f32*)pixLut)[src[0]],
                                   ((mlib_f32*)pixLut)[src[1]]);
                ARGB2ABGR_DB(dd)
                *(mlib_d64*)dst = dd;
                src += 2;
            }
        } else {
            mlib_d64 *dp;

            dp = vis_alignaddr(dst, 0);
            dd = vis_faligndata(dp[0], dp[0]);
            vis_alignaddrl(dst, 0);

#pragma pipeloop(0)
            for (; dst <= (dst_end - 2*4); dst += 2*4) {
                d_old = dd;
                dd = vis_freg_pair(((mlib_f32*)pixLut)[src[0]],
                                   ((mlib_f32*)pixLut)[src[1]]);
                ARGB2ABGR_DB(dd)
                *dp++ = vis_faligndata(d_old, dd);
                src += 2;
            }

            vis_pst_8(vis_faligndata(dd, dd), dp, vis_edge8(dp, dst - 1));
        }

        while (dst < dst_end) {
            x = pixLut[*src++];
            dst[0] = x >> 24;
            dst[1] = x;
            dst[2] = x >> 8;
            dst[3] = x >> 16;
            dst += 4;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToFourByteAbgrXparOver)(BLIT_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dzero;
    mlib_s32 i, j, x, mask;

/*  if (!(((mlib_s32)dstBase | dstScan) & 3)) {
    ADD_SUFF(ByteIndexedToIntAbgrConvert)(BLIT_CALL_PARAMS);
    return;
    }*/

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_u8 *src = srcBase;
            mlib_u8 *dst = dstBase;

            for (i = 0; i < width; i++) {
                x = pixLut[src[i]];
                if (x < 0) {
                    dst[4*i    ] = x >> 24;
                    dst[4*i + 1] = x;
                    dst[4*i + 2] = x >> 8;
                    dst[4*i + 3] = x >> 16;
                }
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

    BMASK_FOR_ARGB

    dzero = vis_fzero();

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end;

        dst_end = dst + 4*width;

        if (!((mlib_s32)dst & 7)) {
#pragma pipeloop(0)
            for (; dst <= (dst_end - 2*4); dst += 2*4) {
                dd = vis_freg_pair(((mlib_f32*)pixLut)[src[0]],
                                   ((mlib_f32*)pixLut)[src[1]]);
                mask = vis_fcmplt32(dd, dzero);
                ARGB2ABGR_DB(dd)
                vis_pst_32(dd, dst, mask);
                src += 2;
            }
        }

        while (dst < dst_end) {
            x = pixLut[*src++];
            if (x < 0) {
                dst[0] = x >> 24;
                dst[1] = x;
                dst[2] = x >> 8;
                dst[3] = x >> 16;
            }
            dst += 4;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToFourByteAbgrXparBgCopy)(BCOPY_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dzero, d_bgpixel;
    mlib_s32 i, j, x, mask;
    mlib_s32 bgpix0 = bgpixel;
    mlib_s32 bgpix1 = bgpixel >> 8;
    mlib_s32 bgpix2 = bgpixel >> 16;
    mlib_s32 bgpix3 = bgpixel >> 24;

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_u8 *src = srcBase;
            mlib_u8 *dst = dstBase;

            for (i = 0; i < width; i++) {
                x = pixLut[src[i]];
                if (x < 0) {
                    dst[4*i    ] = x >> 24;
                    dst[4*i + 1] = x;
                    dst[4*i + 2] = x >> 8;
                    dst[4*i + 3] = x >> 16;
                } else {
                    dst[4*i    ] = bgpix0;
                    dst[4*i + 1] = bgpix1;
                    dst[4*i + 2] = bgpix2;
                    dst[4*i + 3] = bgpix3;
                }
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

    BMASK_FOR_ARGB

    dzero = vis_fzero();
    d_bgpixel = vis_freg_pair(vis_ldfa_ASI_PL(&bgpixel),
                              vis_ldfa_ASI_PL(&bgpixel));

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end;

        dst_end = dst + 4*width;

        if (!((mlib_s32)dst & 7)) {
#pragma pipeloop(0)
            for (; dst <= (dst_end - 2*4); dst += 2*4) {
                dd = vis_freg_pair(((mlib_f32*)pixLut)[src[0]],
                                   ((mlib_f32*)pixLut)[src[1]]);
                mask = vis_fcmplt32(dd, dzero);
                ARGB2ABGR_DB(dd)
                *(mlib_d64*)dst = d_bgpixel;
                vis_pst_32(dd, dst, mask);
                src += 2;
            }
        }

        while (dst < dst_end) {
            x = pixLut[*src++];
            if (x < 0) {
                dst[0] = x >> 24;
                dst[1] = x;
                dst[2] = x >> 8;
                dst[3] = x >> 16;
            } else {
                dst[0] = bgpix0;
                dst[1] = bgpix1;
                dst[2] = bgpix2;
                dst[3] = bgpix3;
            }
            dst += 4;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedToFourByteAbgrScaleConvert)(SCALE_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, d_old;
    mlib_s32 i, j, x;

/*
    if (!(((mlib_s32)dstBase | dstScan) & 3)) {
        ADD_SUFF(ByteIndexedToIntAbgrScaleConvert)(SCALE_CALL_PARAMS);
        return;
    }
*/

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_u8 *src = srcBase;
            mlib_u8 *dst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                x = pixLut[src[tmpsxloc >> shift]];
                tmpsxloc += sxinc;
                dst[4*i    ] = x >> 24;
                dst[4*i + 1] = x;
                dst[4*i + 2] = x >> 8;
                dst[4*i + 3] = x >> 16;
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    BMASK_FOR_ARGB

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        dst_end = dst + 4*width;

        if (!((mlib_s32)dst & 7)) {
#pragma pipeloop(0)
            for (; dst <= (dst_end - 2*4); dst += 2*4) {
                dd = LOAD_2F32(pixLut, src[tmpsxloc >> shift],
                                       src[(tmpsxloc + sxinc) >> shift]);
                tmpsxloc += 2*sxinc;
                ARGB2ABGR_DB(dd)
                *(mlib_d64*)dst = dd;
            }
        } else {
            mlib_d64 *dp;

            dp = vis_alignaddr(dst, 0);
            dd = vis_faligndata(dp[0], dp[0]);
            vis_alignaddrl(dst, 0);

#pragma pipeloop(0)
            for (; dst <= (dst_end - 2*4); dst += 2*4) {
                d_old = dd;
                dd = LOAD_2F32(pixLut, src[tmpsxloc >> shift],
                                       src[(tmpsxloc + sxinc) >> shift]);
                tmpsxloc += 2*sxinc;
                ARGB2ABGR_DB(dd)
                *dp++ = vis_faligndata(d_old, dd);
            }

            vis_pst_8(vis_faligndata(dd, dd), dp, vis_edge8(dp, dst - 1));
        }

        while (dst < dst_end) {
            x = pixLut[src[tmpsxloc >> shift]];
            tmpsxloc += sxinc;
            dst[0] = x >> 24;
            dst[1] = x;
            dst[2] = x >> 8;
            dst[3] = x >> 16;
            dst += 4;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToFourByteAbgrScaleXparOver)(SCALE_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dzero;
    mlib_s32 i, j, x, mask;

/*
    if (!(((mlib_s32)dstBase | dstScan) & 3)) {
        ADD_SUFF(ByteIndexedToIntAbgrScaleConvert)(SCALE_CALL_PARAMS);
        return;
    }
*/

    if (width < 8) {
        for (j = 0; j < height; j++) {
            mlib_u8 *src = srcBase;
            mlib_u8 *dst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                x = pixLut[src[tmpsxloc >> shift]];
                tmpsxloc += sxinc;
                if (x < 0) {
                    dst[4*i    ] = x >> 24;
                    dst[4*i + 1] = x;
                    dst[4*i + 2] = x >> 8;
                    dst[4*i + 3] = x >> 16;
                }
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    BMASK_FOR_ARGB

    dzero = vis_fzero();

    for (j = 0; j < height; j++) {
        mlib_u8 *src = srcBase;
        mlib_u8 *dst = dstBase;
        mlib_u8 *dst_end;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        dst_end = dst + 4*width;

        if (!((mlib_s32)dst & 7)) {
#pragma pipeloop(0)
            for (; dst <= (dst_end - 2*4); dst += 2*4) {
                dd = LOAD_2F32(pixLut, src[tmpsxloc >> shift],
                                       src[(tmpsxloc + sxinc) >> shift]);
                tmpsxloc += 2*sxinc;
                mask = vis_fcmplt32(dd, dzero);
                ARGB2ABGR_DB(dd)
                vis_pst_32(dd, dst, mask);
            }
        }

        while (dst < dst_end) {
            x = pixLut[src[tmpsxloc >> shift]];
            tmpsxloc += sxinc;
            if (x < 0) {
                dst[0] = x >> 24;
                dst[1] = x;
                dst[2] = x >> 8;
                dst[3] = x >> 16;
            }
            dst += 4;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbBmToFourByteAbgrScaleXparOver)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, amask;
    mlib_s32 i, j, x, mask;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_s32 *src = srcBase;
            mlib_u8  *dst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                x = src[tmpsxloc >> shift];
                tmpsxloc += sxinc;
                if (x >> 24) {
                    dst[4*i    ] = 0xFF;
                    dst[4*i + 1] = x;
                    dst[4*i + 2] = x >> 8;
                    dst[4*i + 3] = x >> 16;
                }
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    BMASK_FOR_ARGB

    amask = vis_to_double_dup(0xFF000000);

    for (j = 0; j < height; j++) {
        mlib_s32 *src = srcBase;
        mlib_u8  *dst = dstBase;
        mlib_u8  *dst_end;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        dst_end = dst + 4*width;

        if (!((mlib_s32)dst & 7)) {
#pragma pipeloop(0)
            for (; dst <= (dst_end - 2*4); dst += 2*4) {
                mlib_s32 *pp0 = src + (tmpsxloc >> shift);
                mlib_s32 *pp1 = src + ((tmpsxloc + sxinc) >> shift);
                dd = vis_freg_pair(*(mlib_f32*)pp0, *(mlib_f32*)pp1);
                tmpsxloc += 2*sxinc;
                ARGB2ABGR_DB(dd)
                dd = vis_for(dd, amask);
                mask = (((-*(mlib_u8*)pp0) >> 31) & 2) |
                       (((-*(mlib_u8*)pp1) >> 31) & 1);
                vis_pst_32(dd, dst, mask);
            }
        }

        while (dst < dst_end) {
            x = src[tmpsxloc >> shift];
            tmpsxloc += sxinc;
            if (x >> 24) {
                dst[0] = 0xFF;
                dst[1] = x;
                dst[2] = x >> 8;
                dst[3] = x >> 16;
            }
            dst += 4;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

#ifdef MLIB_ADD_SUFF
#pragma weak IntArgbBmToFourByteAbgrPreScaleXparOver_F =       \
             IntArgbBmToFourByteAbgrScaleXparOver_F
#else
#pragma weak IntArgbBmToFourByteAbgrPreScaleXparOver =         \
             IntArgbBmToFourByteAbgrScaleXparOver
#endif

/***************************************************************/

void ADD_SUFF(FourByteAbgrToIntArgbScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_s32 *dst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                mlib_u8 *pp = src + 4*(tmpsxloc >> shift);
                *dst++ = (pp[0] << 24) | (pp[3] << 16) | (pp[2] << 8) | pp[1];
                tmpsxloc += sxinc;
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    BMASK_FOR_ARGB

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;
        mlib_s32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;
        mlib_s32 off;
        mlib_d64 dd, dd0, dd1;
        mlib_f32 *pp0, *pp1;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            mlib_u8 *pp = src + 4*(tmpsxloc >> shift);
            *dst++ = (pp[0] << 24) | (pp[3] << 16) | (pp[2] << 8) | pp[1];
            tmpsxloc += sxinc;
        }

        off = (mlib_s32)src & 3;
        if (!off) {
#pragma pipeloop(0)
            for (; dst <= (dst_end - 2); dst += 2) {
                pp0 = (mlib_f32*)src + (tmpsxloc >> shift);
                pp1 = (mlib_f32*)src + ((tmpsxloc + sxinc) >> shift);
                tmpsxloc += 2*sxinc;
                dd = vis_freg_pair(pp0[0], pp1[0]);
                ARGB2ABGR_DB(dd)
                *(mlib_d64*)dst = dd;
            }
        } else {
            vis_alignaddr(NULL, off);
#pragma pipeloop(0)
            for (; dst <= (dst_end - 2); dst += 2) {
                pp0 = (mlib_f32*)(src - off) + (tmpsxloc >> shift);
                pp1 = (mlib_f32*)(src - off) + ((tmpsxloc + sxinc) >> shift);
                tmpsxloc += 2*sxinc;
                dd0 = vis_freg_pair(pp0[0], pp0[1]);
                dd1 = vis_freg_pair(pp1[0], pp1[1]);
                dd0 = vis_faligndata(dd0, dd0);
                dd1 = vis_faligndata(dd1, dd1);
                ARGB2ABGR_FL2(dd, vis_read_hi(dd0), vis_read_hi(dd1))
                *(mlib_d64*)dst = dd;
            }
        }

        if (dst < dst_end) {
            mlib_u8 *pp = src + 4*(tmpsxloc >> shift);
            *dst++ = (pp[0] << 24) | (pp[3] << 16) | (pp[2] << 8) | pp[1];
            tmpsxloc += sxinc;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToFourByteAbgrScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j;
    mlib_s32 x;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_s32 *src = srcBase;
            mlib_u8  *dst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                x = src[tmpsxloc >> shift];
                tmpsxloc += sxinc;
                dst[4*i    ] = x >> 24;
                dst[4*i + 1] = x;
                dst[4*i + 2] = x >> 8;
                dst[4*i + 3] = x >> 16;
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    BMASK_FOR_ARGB

    for (j = 0; j < height; j++) {
        mlib_s32 *src = srcBase;
        mlib_u8  *dst = dstBase;
        mlib_u8  *dst_end = dst + 4*width;
        mlib_s32 tmpsxloc = sxloc;
        mlib_d64 dd, d_old;
        mlib_f32 *pp0, *pp1;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if (!((mlib_s32)dst & 3)) {
            if ((mlib_s32)dst & 7) {
                x = src[tmpsxloc >> shift];
                tmpsxloc += sxinc;
                dst[0] = x >> 24;
                dst[1] = x;
                dst[2] = x >> 8;
                dst[3] = x >> 16;
                dst += 4;
            }
#pragma pipeloop(0)
            for (; dst <= (dst_end - 2*4); dst += 2*4) {
                pp0 = (mlib_f32*)src + (tmpsxloc >> shift);
                pp1 = (mlib_f32*)src + ((tmpsxloc + sxinc) >> shift);
                tmpsxloc += 2*sxinc;
                dd = vis_freg_pair(pp0[0], pp1[0]);
                ARGB2ABGR_DB(dd)
                *(mlib_d64*)dst = dd;
            }
        } else {
            mlib_d64 *dp;

            dp = vis_alignaddr(dst, 0);
            dd = vis_faligndata(dp[0], dp[0]);
            vis_alignaddrl(dst, 0);

#pragma pipeloop(0)
            for (; dst <= (dst_end - 2*4); dst += 2*4) {
                d_old = dd;
                pp0 = (mlib_f32*)src + (tmpsxloc >> shift);
                pp1 = (mlib_f32*)src + ((tmpsxloc + sxinc) >> shift);
                tmpsxloc += 2*sxinc;
                dd = vis_freg_pair(pp0[0], pp1[0]);
                ARGB2ABGR_DB(dd)
                *dp++ = vis_faligndata(d_old, dd);
            }

            vis_pst_8(vis_faligndata(dd, dd), dp, vis_edge8(dp, dst - 1));
        }

        if (dst < dst_end) {
            x = src[tmpsxloc >> shift];
            tmpsxloc += sxinc;
            dst[0] = x >> 24;
            dst[1] = x;
            dst[2] = x >> 8;
            dst[3] = x >> 16;
            dst += 4;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbToFourByteAbgrScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j;
    mlib_s32 x;
    mlib_d64 amask = vis_to_double_dup(0xFF000000);

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_s32 *src = srcBase;
            mlib_u8  *dst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                x = src[tmpsxloc >> shift];
                tmpsxloc += sxinc;
                dst[4*i    ] = 0xFF;
                dst[4*i + 1] = x;
                dst[4*i + 2] = x >> 8;
                dst[4*i + 3] = x >> 16;
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    BMASK_FOR_ARGB

    for (j = 0; j < height; j++) {
        mlib_s32 *src = srcBase;
        mlib_u8  *dst = dstBase;
        mlib_u8  *dst_end = dst + 4*width;
        mlib_s32 tmpsxloc = sxloc;
        mlib_d64 dd, d_old;
        mlib_f32 *pp0, *pp1;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if (!((mlib_s32)dst & 3)) {
            if ((mlib_s32)dst & 7) {
                x = src[tmpsxloc >> shift];
                tmpsxloc += sxinc;
                dst[0] = 0xFF;
                dst[1] = x;
                dst[2] = x >> 8;
                dst[3] = x >> 16;
                dst += 4;
            }
#pragma pipeloop(0)
            for (; dst <= (dst_end - 2*4); dst += 2*4) {
                pp0 = (mlib_f32*)src + (tmpsxloc >> shift);
                pp1 = (mlib_f32*)src + ((tmpsxloc + sxinc) >> shift);
                tmpsxloc += 2*sxinc;
                dd = vis_freg_pair(pp0[0], pp1[0]);
                RGB2ABGR_DB(dd)
                *(mlib_d64*)dst = dd;
            }
        } else {
            mlib_d64 *dp;

            dp = vis_alignaddr(dst, 0);
            dd = vis_faligndata(dp[0], dp[0]);
            vis_alignaddrl(dst, 0);

#pragma pipeloop(0)
            for (; dst <= (dst_end - 2*4); dst += 2*4) {
                d_old = dd;
                pp0 = (mlib_f32*)src + (tmpsxloc >> shift);
                pp1 = (mlib_f32*)src + ((tmpsxloc + sxinc) >> shift);
                tmpsxloc += 2*sxinc;
                dd = vis_freg_pair(pp0[0], pp1[0]);
                RGB2ABGR_DB(dd)
                *dp++ = vis_faligndata(d_old, dd);
            }

            vis_pst_8(vis_faligndata(dd, dd), dp, vis_edge8(dp, dst - 1));
        }

        if (dst < dst_end) {
            x = src[tmpsxloc >> shift];
            tmpsxloc += sxinc;
            dst[0] = 0xFF;
            dst[1] = x;
            dst[2] = x >> 8;
            dst[3] = x >> 16;
            dst += 4;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(FourByteAbgrDrawGlyphListAA)(SurfaceDataRasInfo * pRasInfo,
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
    mlib_s32 i, j;
    mlib_d64 dmix0, dmix1, dd, d0, d1, e0, e1, fgpixel_d;
    mlib_d64 done, done16, d_half;
    mlib_s32 pix, mask;
    mlib_f32 fgpixel_f, srcG_f;
    mlib_s32 max_width = BUFF_SIZE;

    done = vis_to_double_dup(0x7fff7fff);
    done16 = vis_to_double_dup(0x7fff);
    d_half = vis_to_double_dup((1 << (16 + 6)) | (1 << 6));

    fgpixel_f = vis_ldfa_ASI_PL(&fgpixel);
    fgpixel_d = vis_freg_pair(fgpixel_f, fgpixel_f);
    srcG_f = vis_to_float(argbcolor);
    ARGB2ABGR_FL(srcG_f)

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
            mlib_u8 *dst_start;

            if ((mlib_s32)dstBase & 3) {
                COPY_NA(dstBase, pbuff, width*sizeof(mlib_s32));
                dst = pbuff;
            } else {
                dst = (void*)dstBase;
            }
            dst_start = (void*)dst;
            dst_end = dst + width;

            /* Need to reset the GSR from the values set by the
             * convert call near the end of this loop.
             */
            vis_write_gsr(7 << 0);

            if ((mlib_s32)dst & 7) {
                pix = *src++;
                dd = vis_fpadd16(MUL8_VIS(srcG_f, pix), d_half);
                dd = vis_fpadd16(MUL8_VIS(*(mlib_f32*)dst, 255 - pix), dd);
                *(mlib_f32*)dst = vis_fpack16(dd);
                if (pix == 255) *(mlib_f32*)dst = vis_read_hi(fgpixel_d);
                dst++;
            }

#pragma pipeloop(0)
            for (; dst <= (dst_end - 2); dst += 2) {
                dmix0 = vis_freg_pair(((mlib_f32 *)vis_mul8s_tbl)[src[0]],
                                      ((mlib_f32 *)vis_mul8s_tbl)[src[1]]);
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

                *(mlib_d64*)dst = fgpixel_d;
                vis_pst_32(dd, dst, mask);
            }

            while (dst < dst_end) {
                pix = *src++;
                dd = vis_fpadd16(MUL8_VIS(srcG_f, pix), d_half);
                dd = vis_fpadd16(MUL8_VIS(*(mlib_f32*)dst, 255 - pix), dd);
                *(mlib_f32*)dst = vis_fpack16(dd);
                if (pix == 255) *(mlib_f32*)dst = vis_read_hi(fgpixel_d);
                dst++;
            }

            ADD_SUFF(IntArgbPreToIntArgbConvert)(dst_start, dst_start,
                                                 width, 1,
                                                 pRasInfo, pRasInfo,
                                                 pPrim, pCompInfo);

            if ((mlib_s32)dstBase & 3) {
                COPY_NA(dst_start, dstBase, width*sizeof(mlib_s32));
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
