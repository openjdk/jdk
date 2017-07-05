/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

mlib_d64 vis_d64_div_tbl[256] = {
    0           , 1.0000000000, 0.5000000000, 0.3333333333,
    0.2500000000, 0.2000000000, 0.1666666667, 0.1428571429,
    0.1250000000, 0.1111111111, 0.1000000000, 0.0909090909,
    0.0833333333, 0.0769230769, 0.0714285714, 0.0666666667,
    0.0625000000, 0.0588235294, 0.0555555556, 0.0526315789,
    0.0500000000, 0.0476190476, 0.0454545455, 0.0434782609,
    0.0416666667, 0.0400000000, 0.0384615385, 0.0370370370,
    0.0357142857, 0.0344827586, 0.0333333333, 0.0322580645,
    0.0312500000, 0.0303030303, 0.0294117647, 0.0285714286,
    0.0277777778, 0.0270270270, 0.0263157895, 0.0256410256,
    0.0250000000, 0.0243902439, 0.0238095238, 0.0232558140,
    0.0227272727, 0.0222222222, 0.0217391304, 0.0212765957,
    0.0208333333, 0.0204081633, 0.0200000000, 0.0196078431,
    0.0192307692, 0.0188679245, 0.0185185185, 0.0181818182,
    0.0178571429, 0.0175438596, 0.0172413793, 0.0169491525,
    0.0166666667, 0.0163934426, 0.0161290323, 0.0158730159,
    0.0156250000, 0.0153846154, 0.0151515152, 0.0149253731,
    0.0147058824, 0.0144927536, 0.0142857143, 0.0140845070,
    0.0138888889, 0.0136986301, 0.0135135135, 0.0133333333,
    0.0131578947, 0.0129870130, 0.0128205128, 0.0126582278,
    0.0125000000, 0.0123456790, 0.0121951220, 0.0120481928,
    0.0119047619, 0.0117647059, 0.0116279070, 0.0114942529,
    0.0113636364, 0.0112359551, 0.0111111111, 0.0109890110,
    0.0108695652, 0.0107526882, 0.0106382979, 0.0105263158,
    0.0104166667, 0.0103092784, 0.0102040816, 0.0101010101,
    0.0100000000, 0.0099009901, 0.0098039216, 0.0097087379,
    0.0096153846, 0.0095238095, 0.0094339623, 0.0093457944,
    0.0092592593, 0.0091743119, 0.0090909091, 0.0090090090,
    0.0089285714, 0.0088495575, 0.0087719298, 0.0086956522,
    0.0086206897, 0.0085470085, 0.0084745763, 0.0084033613,
    0.0083333333, 0.0082644628, 0.0081967213, 0.0081300813,
    0.0080645161, 0.0080000000, 0.0079365079, 0.0078740157,
    0.0078125000, 0.0077519380, 0.0076923077, 0.0076335878,
    0.0075757576, 0.0075187970, 0.0074626866, 0.0074074074,
    0.0073529412, 0.0072992701, 0.0072463768, 0.0071942446,
    0.0071428571, 0.0070921986, 0.0070422535, 0.0069930070,
    0.0069444444, 0.0068965517, 0.0068493151, 0.0068027211,
    0.0067567568, 0.0067114094, 0.0066666667, 0.0066225166,
    0.0065789474, 0.0065359477, 0.0064935065, 0.0064516129,
    0.0064102564, 0.0063694268, 0.0063291139, 0.0062893082,
    0.0062500000, 0.0062111801, 0.0061728395, 0.0061349693,
    0.0060975610, 0.0060606061, 0.0060240964, 0.0059880240,
    0.0059523810, 0.0059171598, 0.0058823529, 0.0058479532,
    0.0058139535, 0.0057803468, 0.0057471264, 0.0057142857,
    0.0056818182, 0.0056497175, 0.0056179775, 0.0055865922,
    0.0055555556, 0.0055248619, 0.0054945055, 0.0054644809,
    0.0054347826, 0.0054054054, 0.0053763441, 0.0053475936,
    0.0053191489, 0.0052910053, 0.0052631579, 0.0052356021,
    0.0052083333, 0.0051813472, 0.0051546392, 0.0051282051,
    0.0051020408, 0.0050761421, 0.0050505051, 0.0050251256,
    0.0050000000, 0.0049751244, 0.0049504950, 0.0049261084,
    0.0049019608, 0.0048780488, 0.0048543689, 0.0048309179,
    0.0048076923, 0.0047846890, 0.0047619048, 0.0047393365,
    0.0047169811, 0.0046948357, 0.0046728972, 0.0046511628,
    0.0046296296, 0.0046082949, 0.0045871560, 0.0045662100,
    0.0045454545, 0.0045248869, 0.0045045045, 0.0044843049,
    0.0044642857, 0.0044444444, 0.0044247788, 0.0044052863,
    0.0043859649, 0.0043668122, 0.0043478261, 0.0043290043,
    0.0043103448, 0.0042918455, 0.0042735043, 0.0042553191,
    0.0042372881, 0.0042194093, 0.0042016807, 0.0041841004,
    0.0041666667, 0.0041493776, 0.0041322314, 0.0041152263,
    0.0040983607, 0.0040816327, 0.0040650407, 0.0040485830,
    0.0040322581, 0.0040160643, 0.0040000000, 0.0039840637,
    0.0039682540, 0.0039525692, 0.0039370079, 0.0039215686
};

/***************************************************************/

#define D64_FROM_F32x2(ff)         \
    vis_freg_pair(ff, ff)

/***************************************************************/

#define RGB2GRAY(r, g, b)         \
    (((77 * (r)) + (150 * (g)) + (29 * (b)) + 128) >> 8)

/***************************************************************/

static void vis_ByteGrayBlendMask(mlib_u8  *rasBase,
                                  mlib_u8  *pMask,
                                  mlib_s32 rasScan,
                                  mlib_s32 maskScan,
                                  mlib_s32 width,
                                  mlib_s32 height,
                                  mlib_s32 *a0_S32,
                                  mlib_s32 srcG)
{
    mlib_f32 ff, srcG_f;
    mlib_d64 dd, a0, a1;
    mlib_d64 d_one = vis_to_double_dup(0x7FFF7FFF);
    mlib_d64 d_round = vis_to_double_dup(((1 << 16) | 1) << 6);
    mlib_s32 j, pathA;

    maskScan -= width;

    srcG = (srcG << 8) | srcG;
    srcG_f = vis_to_float((srcG << 16) | srcG);

    vis_write_gsr((0 << 3) | 6);

    for (j = 0; j < height; j++) {
        mlib_u8 *dst = rasBase;
        mlib_u8 *dst_end;

        dst_end = dst + width;

        while (((mlib_s32)dst & 3) && dst < dst_end) {
            dd = vis_ld_u8(dst);
            pathA = *pMask++;
            a0 = vis_ld_u16(a0_S32 + pathA);
            a1 = vis_fpsub16(d_one, a0);
            a0 = vis_fmul8x16(vis_read_lo(dd), a0);
            a1 = vis_fmul8x16(srcG_f, a1);
            a0 = vis_fpadd16(a0, d_round);
            a0 = vis_fpadd16(a0, a1);
            ff = vis_fpack16(a0);
            dd = D64_FROM_F32x2(ff);
            vis_st_u8(dd, dst);
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 4); dst += 4) {
            ff = *(mlib_f32*)dst;
            a0 = vis_faligndata(vis_ld_u16(a0_S32 + pMask[3]), a0);
            a0 = vis_faligndata(vis_ld_u16(a0_S32 + pMask[2]), a0);
            a0 = vis_faligndata(vis_ld_u16(a0_S32 + pMask[1]), a0);
            a0 = vis_faligndata(vis_ld_u16(a0_S32 + pMask[0]), a0);
            a1 = vis_fpsub16(d_one, a0);
            a0 = vis_fmul8x16(ff, a0);
            a1 = vis_fmul8x16(srcG_f, a1);
            a0 = vis_fpadd16(a0, d_round);
            a0 = vis_fpadd16(a0, a1);
            ff = vis_fpack16(a0);
            *(mlib_f32*)dst = ff;
            pMask += 4;
        }

        while (dst < dst_end) {
            dd = vis_ld_u8(dst);
            pathA = *pMask++;
            a0 = vis_ld_u16(a0_S32 + pathA);
            a1 = vis_fpsub16(d_one, a0);
            a0 = vis_fmul8x16(vis_read_lo(dd), a0);
            a1 = vis_fmul8x16(srcG_f, a1);
            a0 = vis_fpadd16(a0, d_round);
            a0 = vis_fpadd16(a0, a1);
            ff = vis_fpack16(a0);
            dd = D64_FROM_F32x2(ff);
            vis_st_u8(dd, dst);
            dst++;
        }

        PTR_ADD(rasBase, rasScan);
        PTR_ADD(pMask, maskScan);
    }
}

/***************************************************************/

static void vis_ByteGrayBlendMask2(mlib_u8  *rasBase,
                                  mlib_u8  *pMask,
                                  mlib_s32 rasScan,
                                  mlib_s32 maskScan,
                                  mlib_s32 width,
                                  mlib_s32 height,
                                  mlib_s32 *a0_S32,
                                  mlib_s16 *d1_S16)
{
    mlib_f32 ff;
    mlib_d64 dd, a0, a1;
    mlib_s32 j, pathA;

    maskScan -= width;

    vis_write_gsr((0 << 3) | 6);

    for (j = 0; j < height; j++) {
        mlib_u8 *dst = rasBase;
        mlib_u8 *dst_end;

        dst_end = dst + width;

        while (((mlib_s32)dst & 3) && dst < dst_end) {
            dd = vis_ld_u8(dst);
            pathA = *pMask++;
            a0 = vis_ld_u16(a0_S32 + pathA);
            a1 = vis_ld_u16(d1_S16 + pathA);
            a0 = vis_fmul8x16(vis_read_lo(dd), a0);
            a0 = vis_fpadd16(a0, a1);
            ff = vis_fpack16(a0);
            dd = D64_FROM_F32x2(ff);
            vis_st_u8(dd, dst);
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 4); dst += 4) {
            ff = *(mlib_f32*)dst;
            a0 = vis_faligndata(vis_ld_u16(a0_S32 + pMask[3]), a0);
            a0 = vis_faligndata(vis_ld_u16(a0_S32 + pMask[2]), a0);
            a0 = vis_faligndata(vis_ld_u16(a0_S32 + pMask[1]), a0);
            a0 = vis_faligndata(vis_ld_u16(a0_S32 + pMask[0]), a0);
            a1 = vis_faligndata(vis_ld_u16(d1_S16 + pMask[3]), a1);
            a1 = vis_faligndata(vis_ld_u16(d1_S16 + pMask[2]), a1);
            a1 = vis_faligndata(vis_ld_u16(d1_S16 + pMask[1]), a1);
            a1 = vis_faligndata(vis_ld_u16(d1_S16 + pMask[0]), a1);
            a0 = vis_fmul8x16(ff, a0);
            a0 = vis_fpadd16(a0, a1);
            ff = vis_fpack16(a0);
            *(mlib_f32*)dst = ff;
            pMask += 4;
        }

        while (dst < dst_end) {
            dd = vis_ld_u8(dst);
            pathA = *pMask++;
            a0 = vis_ld_u16(a0_S32 + pathA);
            a1 = vis_ld_u16(d1_S16 + pathA);
            a0 = vis_fmul8x16(vis_read_lo(dd), a0);
            a0 = vis_fpadd16(a0, a1);
            ff = vis_fpack16(a0);
            dd = D64_FROM_F32x2(ff);
            vis_st_u8(dd, dst);
            dst++;
        }

        PTR_ADD(rasBase, rasScan);
        PTR_ADD(pMask, maskScan);
    }
}

/***************************************************************/

static void vis_ByteGrayBlend(mlib_u8  *rasBase,
                              mlib_s32 rasScan,
                              mlib_s32 width,
                              mlib_s32 height,
                              mlib_f32 a0,
                              mlib_d64 d1)
{
    mlib_f32 ff;
    mlib_d64 dd;
    mlib_s32 j;

    vis_write_gsr((0 << 3) | 6);

    for (j = 0; j < height; j++) {
        mlib_u8 *dst = rasBase;
        mlib_u8 *dst_end;

        dst_end = dst + width;

        while (((mlib_s32)dst & 3) && dst < dst_end) {
            dd = vis_ld_u8(dst);
            dd = vis_fmul8x16al(vis_read_lo(dd), a0);
            dd = vis_fpadd16(dd, d1);
            ff = vis_fpack16(dd);
            dd = D64_FROM_F32x2(ff);
            vis_st_u8(dd, dst);
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 4); dst += 4) {
            ff = *(mlib_f32*)dst;
            dd = vis_fmul8x16al(ff, a0);
            dd = vis_fpadd16(dd, d1);
            ff = vis_fpack16(dd);
            *(mlib_f32*)dst = ff;
        }

        while (dst < dst_end) {
            dd = vis_ld_u8(dst);
            dd = vis_fmul8x16al(vis_read_lo(dd), a0);
            dd = vis_fpadd16(dd, d1);
            ff = vis_fpack16(dd);
            dd = D64_FROM_F32x2(ff);
            vis_st_u8(dd, dst);
            dst++;
        }

        PTR_ADD(rasBase, rasScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteGraySrcMaskFill)(void *rasBase,
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
    mlib_s32 rasScan = pRasInfo->scanStride;
    mlib_s32 r, g, b, i, j;
    mlib_s32 a0_S32[256];
    mlib_s32 resA, resG, dstF, pathA, srcA, srcG;
    mlib_d64 dscale;

    b = (fgColor) & 0xff;
    g = (fgColor >> 8) & 0xff;
    r = (fgColor >> 16) & 0xff;
    srcA = (fgColor >> 24) & 0xff;
    srcG = RGB2GRAY(r, g, b);

#ifdef LOOPS_OLD_VERSION
    if (srcA == 0) return;

    if (pMask == NULL) {
        AnyByteSetRect(pRasInfo, 0, 0, width, height, srcG, pPrim, pCompInfo);
        return;
    }
#else
    if (pMask == NULL) {
        if (srcA == 0) srcG = 0;
        ADD_SUFF(AnyByteSetRect)(pRasInfo,
                                 pRasInfo->bounds.x1, pRasInfo->bounds.y1,
                                 pRasInfo->bounds.x2, pRasInfo->bounds.y2,
                                 srcG, pPrim, pCompInfo);
        return;
    }
#endif

    pMask += maskOff;

    if (width < 32) {
        srcG = mul8table[srcA][srcG];

        for (j = 0; j < height; j++) {
            mlib_u8 *dst = rasBase;

            for (i = 0; i < width; i++) {
                pathA = pMask[i];
                resG = dst[i];
                dstF = 0xff - pathA;
                resA = dstF + mul8table[pathA][srcA];
                resG = mul8table[dstF][resG] + mul8table[pathA][srcG];
                resG = div8table[resA][resG];
                dst[i] = resG;
            }

            PTR_ADD(rasBase, rasScan);
            PTR_ADD(pMask, maskScan);
        }
        return;
    }

    dscale = (mlib_d64)(1 << 15)*(1 << 16);
    a0_S32[0] = dscale - 1;
#pragma pipeloop(0)
    for (pathA = 1; pathA < 256; pathA++) {
        dstF = 0xff - pathA;
        resA = dstF + mul8table[pathA][srcA];
        dstF = dscale*dstF*vis_d64_div_tbl[resA];
        a0_S32[pathA] = dstF;
    }

    vis_ByteGrayBlendMask(rasBase, pMask, rasScan, maskScan,
                          width, height, a0_S32, srcG);
}

/***************************************************************/

void ADD_SUFF(ByteGraySrcOverMaskFill)(void *rasBase,
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
    mlib_s32 rasScan = pRasInfo->scanStride;
    mlib_s32 r, g, b, i, j;
    mlib_s32 dstA, pathA, srcA, srcG;

    b = (fgColor) & 0xff;
    g = (fgColor >> 8) & 0xff;
    r = (fgColor >> 16) & 0xff;
    srcA = (fgColor >> 24) & 0xff;
    srcG = RGB2GRAY(r, g, b);

    if (srcA == 0) return;

    if (pMask != NULL) pMask += maskOff;

    if (width < 16) {
        srcG = mul8table[srcA][srcG];

        if (pMask != NULL) {
            for (j = 0; j < height; j++) {
                mlib_u8 *dst = rasBase;

                for (i = 0; i < width; i++) {
                    pathA = pMask[i];
                    dstA = 0xff - mul8table[pathA][srcA];
                    dst[i] = mul8table[dstA][dst[i]] + mul8table[pathA][srcG];
                }

                PTR_ADD(rasBase, rasScan);
                PTR_ADD(pMask, maskScan);
            }
        } else {
            mlib_u8 *mul8_dstA = mul8table[0xff - srcA];

            for (j = 0; j < height; j++) {
                mlib_u8 *dst = rasBase;

                for (i = 0; i < width; i++) {
                    dst[i] = mul8_dstA[dst[i]] + srcG;
                }

                PTR_ADD(rasBase, rasScan);
            }
        }
        return;
    }

    if (pMask != NULL) {
        mlib_s32 a0_S32[256];
        mlib_d64 dscale = (mlib_d64)(1 << 15)*(1 << 16);

        a0_S32[0] = dscale - 1;
#pragma pipeloop(0)
        for (pathA = 1; pathA < 256; pathA++) {
            a0_S32[pathA] = dscale - pathA*srcA*(dscale*(1.0/(255*255)));
        }

        vis_ByteGrayBlendMask(rasBase, pMask, rasScan, maskScan,
                              width, height, a0_S32, srcG);
    } else {
        mlib_s32 a0_int = (1 << 15)*(1.0 - srcA*(1.0/255));
        mlib_f32 a0, a1, srcG_f;
        mlib_d64 d1;
        mlib_d64 d_round = vis_to_double_dup(((1 << 16) | 1) << 6);

        srcG = (srcG << 8) | srcG;
        srcG_f = vis_to_float((srcG << 16) | srcG);

        a0 = vis_to_float(a0_int);
        a1 = vis_to_float(0x7FFF - a0_int);
        d1 = vis_fmul8x16al(srcG_f, a1);
        d1 = vis_fpadd16(d1, d_round);

        vis_ByteGrayBlend(rasBase, rasScan, width, height, a0, d1);
    }
}

/***************************************************************/

void ADD_SUFF(ByteGrayAlphaMaskFill)(void *rasBase,
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
    mlib_s32 rasScan = pRasInfo->scanStride;
    mlib_s32 pathA, srcA, srcG, dstA, dstFbase, srcFbase;
    mlib_s32 SrcOpAnd, SrcOpXor, SrcOpAdd;
    mlib_s32 DstOpAnd, DstOpXor, DstOpAdd;
    mlib_s32 r, g, b;
    mlib_s32 resA, resG, srcF, i, j;

    b = (fgColor) & 0xff;
    g = (fgColor >> 8) & 0xff;
    r = (fgColor >> 16) & 0xff;
    srcA = (fgColor >> 24) & 0xff;
    srcG = RGB2GRAY(r, g, b);

    SrcOpAnd = (AlphaRules[pCompInfo->rule].srcOps).andval;
    SrcOpXor = (AlphaRules[pCompInfo->rule].srcOps).xorval;
    SrcOpAdd = (AlphaRules[pCompInfo->rule].srcOps).addval - SrcOpXor;

    DstOpAnd = (AlphaRules[pCompInfo->rule].dstOps).andval;
    DstOpXor = (AlphaRules[pCompInfo->rule].dstOps).xorval;
    DstOpAdd = (AlphaRules[pCompInfo->rule].dstOps).addval - DstOpXor;

    dstFbase = ((((srcA) & DstOpAnd) ^ DstOpXor) + DstOpAdd);
    srcFbase = ((((0xff) & SrcOpAnd) ^ SrcOpXor) + SrcOpAdd);

    if (pMask != NULL) pMask += maskOff;

    srcG = mul8table[srcA][srcG];

    if (width < 100) {
        if (pMask != NULL) {
            for (j = 0; j < height; j++) {
                mlib_u8 *dst = rasBase;

                for (i = 0; i < width; i++) {
                    pathA = pMask[i];
                    srcF = mul8table[pathA][srcFbase];
                    dstA = 0xff - pathA + mul8table[pathA][dstFbase];

                    resA = dstA + mul8table[srcF][srcA];
                    resG = mul8table[dstA][dst[i]] + mul8table[srcF][srcG];

                    dst[i] = div8table[resA][resG];
                }

                PTR_ADD(rasBase, rasScan);
                PTR_ADD(pMask, maskScan);
            }
        } else {
            mlib_u8 *mul8_dstA;

            srcF = srcFbase;
            dstA = dstFbase;
            resA = dstA + mul8table[srcF][srcA];
            srcG = mul8table[srcF][srcG];
            mul8_dstA = mul8table[dstA];

            for (j = 0; j < height; j++) {
                mlib_u8 *dst = rasBase;

                for (i = 0; i < width; i++) {
                    resG = mul8_dstA[dst[i]] + srcG;
                    dst[i] = div8table[resA][resG];
                }

                PTR_ADD(rasBase, rasScan);
            }
        }
        return;
    }

    if (pMask != NULL) {
        mlib_s32 a0_S32[256];
        mlib_s16 d1_S16[256];
        mlib_d64 dscale = (mlib_d64)(1 << 15)*(1 << 16);

        a0_S32[0] = dscale - 1;
        d1_S16[0] = (1 << 6);
#pragma pipeloop(0)
        for (pathA = 1; pathA < 256; pathA++) {
            srcF = mul8table[pathA][srcFbase];
            dstA = 0xff - pathA + mul8table[pathA][dstFbase];
            resA = dstA + mul8table[srcF][srcA];
            a0_S32[pathA] = dscale*dstA*vis_d64_div_tbl[resA] + (1 << 15);
            d1_S16[pathA] = (1 << 7)*srcG*srcF*vis_d64_div_tbl[resA] + (1 << 6);
        }

        vis_ByteGrayBlendMask2(rasBase, pMask, rasScan, maskScan,
                               width, height, a0_S32, d1_S16);
    } else {
        mlib_d64 dscale = (mlib_d64)(1 << 15)*(1 << 16);
        mlib_s32 _a0, _d1;
        mlib_f32 a0;
        mlib_d64 d1;

        srcF = srcFbase;
        dstA = dstFbase;
        resA = dstA + mul8table[srcF][srcA];
        _a0 = dscale*dstA*vis_d64_div_tbl[resA] + (1 << 15);
        _d1 = (1 << 7)*vis_d64_div_tbl[resA]*srcF*srcG + (1 << 6);

        a0 = vis_to_float(_a0 >> 16);
        d1 = vis_to_double_dup((_d1 << 16) | _d1);

        vis_ByteGrayBlend(rasBase, rasScan, width, height, a0, d1);
    }
}

/***************************************************************/

#define TBL_MUL ((mlib_s16*)vis_mul8s_tbl + 1)

void ADD_SUFF(ByteGrayDrawGlyphListAA)(GLYPH_LIST_PARAMS)
{
    mlib_s32 glyphCounter;
    mlib_s32 scan = pRasInfo->scanStride;
    mlib_u8  *pPix;
    mlib_s32 srcG;
    int i, j, r, g, b;
    mlib_d64 mix0, mix1, dd, d0, d1, e0, e1, fgpixel_d;
    mlib_d64 done, d_half;
    mlib_s32 pix, mask0, mask1;
    mlib_f32 fgpixel_f, srcG_f;

    b = (argbcolor) & 0xff;
    g = (argbcolor >> 8) & 0xff;
    r = (argbcolor >> 16) & 0xff;
    srcG = RGB2GRAY(r, g, b);

    if (clipRight - clipLeft >= 16) {
        done = vis_to_double_dup(0x7fff7fff);
        d_half = vis_to_double_dup((1 << (16 + 6)) | (1 << 6));

        fgpixel &= 0xff;
        fgpixel_f = F32_FROM_U8x4(fgpixel, fgpixel, fgpixel, fgpixel);
        fgpixel_d = vis_freg_pair(fgpixel_f, fgpixel_f);
        srcG_f = F32_FROM_U8x4(srcG, srcG, srcG, srcG);

        vis_write_gsr((0 << 3) | 6);
    }

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

        pPix = pRasInfo->rasBase;
        PTR_ADD(pPix, top * scan + left);

        if (width < 16) {
            for (j = 0; j < height; j++) {
                for (i = 0; i < width; i++) {
                    jint dstG;
                    jint mixValSrc = pixels[i];
                    if (mixValSrc) {
                        if (mixValSrc < 255) {
                            jint mixValDst = 255 - mixValSrc;
                            dstG = pPix[i];
                            dstG =
                                mul8table[mixValDst][dstG] +
                                mul8table[mixValSrc][srcG];
                            pPix[i] = dstG;
                        } else {
                            pPix[i] = fgpixel;
                        }
                    }
                }

                PTR_ADD(pPix, scan);
                pixels += rowBytes;
            }
        } else {
            for (j = 0; j < height; j++) {
                mlib_u8 *src = (void*)pixels;
                mlib_u8 *dst = pPix;
                mlib_u8 *dst_end = dst + width;

                while (((mlib_s32)dst & 7) && dst < dst_end) {
                    pix = *src++;
                    d0 = vis_fpadd16(MUL8_VIS(srcG_f, pix), d_half);
                    d1 = MUL8_VIS(vis_read_lo(vis_ld_u8(dst)), 255 - pix);
                    dd = vis_fpadd16(d0, d1);
                    vis_st_u8(D64_FROM_F32x2(vis_fpack16(dd)), dst);
                    if (pix == 255) *dst = fgpixel;
                    dst++;
                }

#pragma pipeloop(0)
                for (; dst <= (dst_end - 8); dst += 8) {
                    mix0 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*src[3]), mix0);
                    mix1 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*src[7]), mix1);
                    mix0 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*src[2]), mix0);
                    mix1 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*src[6]), mix1);
                    mix0 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*src[1]), mix0);
                    mix1 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*src[5]), mix1);
                    mix0 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*src[0]), mix0);
                    mix1 = vis_faligndata(vis_ld_u16(TBL_MUL + 2*src[4]), mix1);
                    src += 8;

                    dd = *(mlib_d64*)dst;
                    d0 = vis_fpadd16(vis_fmul8x16(srcG_f, mix0), d_half);
                    d1 = vis_fpadd16(vis_fmul8x16(srcG_f, mix1), d_half);
                    e0 = vis_fmul8x16(vis_read_hi(dd), vis_fpsub16(done, mix0));
                    e1 = vis_fmul8x16(vis_read_lo(dd), vis_fpsub16(done, mix1));
                    d0 = vis_fpadd16(e0, d0);
                    d1 = vis_fpadd16(e1, d1);
                    dd = vis_fpack16_pair(d0, d1);

                    mask0 = vis_fcmplt16(mix0, done);
                    mask1 = vis_fcmplt16(mix1, done);

                    *(mlib_d64*)dst = fgpixel_d;
                    vis_pst_8(dd, dst, (mask0 << 4) | mask1);
                }

                while (dst < dst_end) {
                    pix = *src++;
                    d0 = vis_fpadd16(MUL8_VIS(srcG_f, pix), d_half);
                    d1 = MUL8_VIS(vis_read_lo(vis_ld_u8(dst)), 255 - pix);
                    dd = vis_fpadd16(d0, d1);
                    vis_st_u8(D64_FROM_F32x2(vis_fpack16(dd)), dst);
                    if (pix == 255) *dst = fgpixel;
                    dst++;
                }

                PTR_ADD(pPix, scan);
                pixels += rowBytes;
            }
        }
    }
}

/***************************************************************/

#endif
