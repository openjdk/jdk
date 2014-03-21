/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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


/*
 * FUNCTION
 *   Internal functions for mlib_ImageConv* on S32 type and
 *   MLIB_EDGE_DST_NO_WRITE mask
 *
 */

#include "mlib_image.h"
#include "mlib_ImageConv.h"

/***************************************************************/
#define BUFF_LINE  256

#define CACHE_SIZE (64*1024)

/***************************************************************/
#define CONV_FUNC(KERN) mlib_conv##KERN##nw_s32

/***************************************************************/
#ifndef MLIB_USE_FTOI_CLAMPING

#define CLAMP_S32(dst, src)                                       \
  if (src > (mlib_d64)MLIB_S32_MAX) src = (mlib_d64)MLIB_S32_MAX; \
  if (src < (mlib_d64)MLIB_S32_MIN) src = (mlib_d64)MLIB_S32_MIN; \
  dst = (mlib_s32)src

#else

#define CLAMP_S32(dst, src) dst = (mlib_s32)(src)

#endif /* MLIB_USE_FTOI_CLAMPING */

/***************************************************************/
#define GET_SRC_DST_PARAMETERS(type)                            \
  mlib_s32 hgt = mlib_ImageGetHeight(src);                      \
  mlib_s32 wid = mlib_ImageGetWidth(src);                       \
  mlib_s32 sll = mlib_ImageGetStride(src) / sizeof(type);       \
  mlib_s32 dll = mlib_ImageGetStride(dst) / sizeof(type);       \
  type*    adr_src = mlib_ImageGetData(src);                    \
  type*    adr_dst = mlib_ImageGetData(dst);                    \
  mlib_s32 chan1 = mlib_ImageGetChannels(src)
/*  mlib_s32 chan2 = chan1 + chan1 */

/***************************************************************/
#define DEF_VARS(type)                                          \
  GET_SRC_DST_PARAMETERS(type);                                 \
  type     *sl, *sp, *sl1, *dl, *dp;                            \
  mlib_d64 *pbuff = buff, *buff0, *buff1, *buff2, *buffT;       \
  mlib_s32 i, j, c;                                             \
  mlib_d64 scalef, d0, d1

/***************************************************************/
#define DEF_VARS_MxN(type)                                      \
  GET_SRC_DST_PARAMETERS(type);                                 \
  type     *sl, *sp = NULL, *dl, *dp = NULL;                    \
  mlib_d64 *pbuff = buff;                                       \
  mlib_s32 i, j, c

/***************************************************************/
#define CALC_SCALE()                                            \
  scalef = 1.0;                                                 \
  while (scalef_expon > 30) {                                   \
    scalef /= (1 << 30);                                        \
    scalef_expon -= 30;                                         \
  }                                                             \
                                                                \
  scalef /= (1 << scalef_expon)

/***************************************************************/
#undef  KSIZE
#define KSIZE 2

mlib_status CONV_FUNC(2x2)(mlib_image       *dst,
                           const mlib_image *src,
                           const mlib_s32   *kern,
                           mlib_s32         scalef_expon,
                           mlib_s32         cmask)
{
  mlib_d64 buff[(KSIZE + 1)*BUFF_LINE];
  mlib_d64 k0, k1, k2, k3;
  mlib_d64 p00, p01, p02, p03,
           p10, p11, p12, p13;
  mlib_d64 d2;
  DEF_VARS(mlib_s32);
  mlib_s32 chan2 = chan1 + chan1;
  mlib_s32 chan3 = chan1 + chan2;

  if (wid > BUFF_LINE) {
    pbuff = mlib_malloc((KSIZE + 1)*sizeof(mlib_d64)*wid);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  buff0 = pbuff;
  buff1 = buff0 + wid;
  buff2 = buff1 + wid;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  /* keep kernel in regs */
  CALC_SCALE();
  k0 = scalef * kern[0];  k1 = scalef * kern[1];
  k2 = scalef * kern[2];  k3 = scalef * kern[3];

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    sl1 = sl + sll;
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < wid + (KSIZE - 1); i++) {
      buff0[i] = (mlib_d64)sl[i*chan1];
      buff1[i] = (mlib_d64)sl1[i*chan1];
    }

    sl += KSIZE*sll;

    for (j = 0; j < hgt; j++) {
      p03 = buff0[0];
      p13 = buff1[0];

      sp = sl;
      dp = dl;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 3); i += 3) {

        p00 = p03; p10 = p13;

        p01 = buff0[i + 1]; p11 = buff1[i + 1];
        p02 = buff0[i + 2]; p12 = buff1[i + 2];
        p03 = buff0[i + 3]; p13 = buff1[i + 3];

        buff2[i    ] = (mlib_d64)sp[0];
        buff2[i + 1] = (mlib_d64)sp[chan1];
        buff2[i + 2] = (mlib_d64)sp[chan2];

        d0 = p00 * k0 + p01 * k1 + p10 * k2 + p11 * k3;
        d1 = p01 * k0 + p02 * k1 + p11 * k2 + p12 * k3;
        d2 = p02 * k0 + p03 * k1 + p12 * k2 + p13 * k3;

        CLAMP_S32(dp[0    ], d0);
        CLAMP_S32(dp[chan1], d1);
        CLAMP_S32(dp[chan2], d2);

        sp += chan3;
        dp += chan3;
      }

      for (; i < wid; i++) {
        p00 = buff0[i];     p10 = buff1[i];
        p01 = buff0[i + 1]; p11 = buff1[i + 1];

        buff2[i] = (mlib_d64)sp[0];

        d0 = p00 * k0 + p01 * k1 + p10 * k2 + p11 * k3;
        CLAMP_S32(dp[0], d0);

        sp += chan1;
        dp += chan1;
      }

      buff2[wid] = (mlib_d64)sp[0];

      sl += sll;
      dl += dll;

      buffT = buff0;
      buff0 = buff1;
      buff1 = buff2;
      buff2 = buffT;
    }
  }

  if (pbuff != buff) mlib_free(pbuff);

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  KSIZE
#define KSIZE 3

mlib_status CONV_FUNC(3x3)(mlib_image       *dst,
                           const mlib_image *src,
                           const mlib_s32   *kern,
                           mlib_s32         scalef_expon,
                           mlib_s32         cmask)
{
  mlib_d64 buff[(KSIZE + 1)*BUFF_LINE], *buff3;
  mlib_d64 k0, k1, k2, k3, k4, k5, k6, k7, k8;
  mlib_d64 p00, p01, p02, p03,
           p10, p11, p12, p13,
           p20, p21, p22, p23;
  mlib_s32 *sl2;
  DEF_VARS(mlib_s32);
  mlib_s32 chan2 = chan1 + chan1;

  if (wid > BUFF_LINE) {
    pbuff = mlib_malloc((KSIZE + 1)*sizeof(mlib_d64)*wid);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  buff0 = pbuff;
  buff1 = buff0 + wid;
  buff2 = buff1 + wid;
  buff3 = buff2 + wid;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  adr_dst += ((KSIZE - 1)/2)*(dll + chan1);

  CALC_SCALE();
  k0 = scalef * kern[0];  k1 = scalef * kern[1];  k2 = scalef * kern[2];
  k3 = scalef * kern[3];  k4 = scalef * kern[4];  k5 = scalef * kern[5];
  k6 = scalef * kern[6];  k7 = scalef * kern[7];  k8 = scalef * kern[8];

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    sl1 = sl  + sll;
    sl2 = sl1 + sll;
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < wid + (KSIZE - 1); i++) {
      buff0[i] = (mlib_d64)sl[i*chan1];
      buff1[i] = (mlib_d64)sl1[i*chan1];
      buff2[i] = (mlib_d64)sl2[i*chan1];
    }

    sl += KSIZE*sll;

    for (j = 0; j < hgt; j++) {
      mlib_d64 s0, s1;

      p02 = buff0[0];
      p12 = buff1[0];
      p22 = buff2[0];

      p03 = buff0[1];
      p13 = buff1[1];
      p23 = buff2[1];

      sp = sl;
      dp = dl;

      s0 = p02 * k0 + p03 * k1 + p12 * k3 + p13 * k4 + p22 * k6 + p23 * k7;
      s1 = p03 * k0 + p13 * k3 + p23 * k6;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 2); i += 2) {
        p02 = buff0[i + 2]; p12 = buff1[i + 2]; p22 = buff2[i + 2];
        p03 = buff0[i + 3]; p13 = buff1[i + 3]; p23 = buff2[i + 3];

        buff3[i    ] = (mlib_d64)sp[0];
        buff3[i + 1] = (mlib_d64)sp[chan1];

        d0 = s0 + p02 * k2 + p12 * k5 + p22 * k8;
        d1 = s1 + p02 * k1 + p03 * k2 + p12 * k4 + p13 * k5 + p22 * k7 + p23 * k8;

        CLAMP_S32(dp[0    ], d0);
        CLAMP_S32(dp[chan1], d1);

        s0 = p02 * k0 + p03 * k1 + p12 * k3 + p13 * k4 + p22 * k6 + p23 * k7;
        s1 = p03 * k0 + p13 * k3 + p23 * k6;

        sp += chan2;
        dp += chan2;
      }

      for (; i < wid; i++) {
        p00 = buff0[i];     p10 = buff1[i];     p20 = buff2[i];
        p01 = buff0[i + 1]; p11 = buff1[i + 1]; p21 = buff2[i + 1];
        p02 = buff0[i + 2]; p12 = buff1[i + 2]; p22 = buff2[i + 2];

        buff3[i] = (mlib_d64)sp[0];

        d0 = (p00 * k0 + p01 * k1 + p02 * k2 + p10 * k3 + p11 * k4 +
              p12 * k5 + p20 * k6 + p21 * k7 + p22 * k8);

        CLAMP_S32(dp[0], d0);

        sp += chan1;
        dp += chan1;
      }

      buff3[wid    ] = (mlib_d64)sp[0];
      buff3[wid + 1] = (mlib_d64)sp[chan1];

      sl += sll;
      dl += dll;

      buffT = buff0;
      buff0 = buff1;
      buff1 = buff2;
      buff2 = buff3;
      buff3 = buffT;
    }
  }

  if (pbuff != buff) mlib_free(pbuff);

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  KSIZE
#define KSIZE 4

mlib_status CONV_FUNC(4x4)(mlib_image       *dst,
                           const mlib_image *src,
                           const mlib_s32   *kern,
                           mlib_s32         scalef_expon,
                           mlib_s32         cmask)
{
  mlib_d64 buff[(KSIZE + 2)*BUFF_LINE], *buff3, *buff4, *buff5;
  mlib_d64 k[KSIZE*KSIZE];
  mlib_d64 k0, k1, k2, k3, k4, k5, k6, k7;
  mlib_d64 p00, p01, p02, p03, p04,
           p10, p11, p12, p13, p14,
           p20, p21, p22, p23,
           p30, p31, p32, p33;
  mlib_s32 *sl2, *sl3;
  DEF_VARS(mlib_s32);
  mlib_s32 chan2 = chan1 + chan1;

  if (wid > BUFF_LINE) {
    pbuff = mlib_malloc((KSIZE + 2)*sizeof(mlib_d64)*wid);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  buff0 = pbuff;
  buff1 = buff0 + wid;
  buff2 = buff1 + wid;
  buff3 = buff2 + wid;
  buff4 = buff3 + wid;
  buff5 = buff4 + wid;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  adr_dst += ((KSIZE - 1)/2)*(dll + chan1);

  CALC_SCALE();
  for (j = 0; j < 16; j++) k[j] = scalef * kern[j];

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    sl1 = sl  + sll;
    sl2 = sl1 + sll;
    sl3 = sl2 + sll;
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < wid + (KSIZE - 1); i++) {
      buff0[i] = (mlib_d64)sl[i*chan1];
      buff1[i] = (mlib_d64)sl1[i*chan1];
      buff2[i] = (mlib_d64)sl2[i*chan1];
      buff3[i] = (mlib_d64)sl3[i*chan1];
    }

    sl += KSIZE*sll;

    for (j = 0; j < hgt; j++) {
      /*
       *  First loop on two first lines of kernel
       */
      k0 = k[0]; k1 = k[1]; k2 = k[2]; k3 = k[3];
      k4 = k[4]; k5 = k[5]; k6 = k[6]; k7 = k[7];

      sp = sl;
      dp = dl;

      p02 = buff0[0];
      p12 = buff1[0];
      p03 = buff0[1];
      p13 = buff1[1];
      p04 = buff0[2];

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 2); i += 2) {
        p00 = p02; p10 = p12;
        p01 = p03; p11 = p13;
        p02 = p04; p12 = buff1[i + 2];
        p03 = buff0[i + 3]; p13 = buff1[i + 3];
        p04 = buff0[i + 4]; p14 = buff1[i + 4];

        buff4[i] = (mlib_d64)sp[0];
        buff4[i + 1] = (mlib_d64)sp[chan1];

        buff5[i    ] = (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 +
                        p10 * k4 + p11 * k5 + p12 * k6 + p13 * k7);
        buff5[i + 1] = (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 +
                        p11 * k4 + p12 * k5 + p13 * k6 + p14 * k7);

        sp += chan2;
        dp += chan2;
      }

      /*
       *  Second loop on two last lines of kernel
       */
      k0 = k[ 8]; k1 = k[ 9]; k2 = k[10]; k3 = k[11];
      k4 = k[12]; k5 = k[13]; k6 = k[14]; k7 = k[15];

      sp = sl;
      dp = dl;

      p02 = buff2[0];
      p12 = buff3[0];
      p03 = buff2[1];
      p13 = buff3[1];
      p04 = buff2[2];

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 2); i += 2) {
        p00 = p02; p10 = p12;
        p01 = p03; p11 = p13;
        p02 = p04; p12 = buff3[i + 2];
        p03 = buff2[i + 3]; p13 = buff3[i + 3];
        p04 = buff2[i + 4]; p14 = buff3[i + 4];

        d0 = (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 +
              p10 * k4 + p11 * k5 + p12 * k6 + p13 * k7 + buff5[i]);
        d1 = (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 +
              p11 * k4 + p12 * k5 + p13 * k6 + p14 * k7 + buff5[i + 1]);

        CLAMP_S32(dp[0    ], d0);
        CLAMP_S32(dp[chan1], d1);

        sp += chan2;
        dp += chan2;
      }

      /* last pixels */
      for (; i < wid; i++) {
        p00 = buff0[i];     p10 = buff1[i];     p20 = buff2[i];     p30 = buff3[i];
        p01 = buff0[i + 1]; p11 = buff1[i + 1]; p21 = buff2[i + 1]; p31 = buff3[i + 1];
        p02 = buff0[i + 2]; p12 = buff1[i + 2]; p22 = buff2[i + 2]; p32 = buff3[i + 2];
        p03 = buff0[i + 3]; p13 = buff1[i + 3]; p23 = buff2[i + 3]; p33 = buff3[i + 3];

        buff4[i] = (mlib_d64)sp[0];

        d0 = (p00 * k[0] + p01 * k[1] + p02 * k[2] + p03 * k[3] +
              p10 * k[4] + p11 * k[5] + p12 * k[6] + p13 * k[7] +
              p20 * k[ 8] + p21 * k[ 9] + p22 * k[10] + p23 * k[11] +
              p30 * k[12] + p31 * k[13] + p32 * k[14] + p33 * k[15]);

        CLAMP_S32(dp[0], d0);

        sp += chan1;
        dp += chan1;
      }

      buff4[wid    ] = (mlib_d64)sp[0];
      buff4[wid + 1] = (mlib_d64)sp[chan1];
      buff4[wid + 2] = (mlib_d64)sp[chan2];

      /* next line */
      sl += sll;
      dl += dll;

      buffT = buff0;
      buff0 = buff1;
      buff1 = buff2;
      buff2 = buff3;
      buff3 = buff4;
      buff4 = buffT;
    }
  }

  if (pbuff != buff) mlib_free(pbuff);

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  KSIZE
#define KSIZE 5

mlib_status CONV_FUNC(5x5)(mlib_image       *dst,
                           const mlib_image *src,
                           const mlib_s32   *kern,
                           mlib_s32         scalef_expon,
                           mlib_s32         cmask)
{
  mlib_d64 buff[(KSIZE + 2)*BUFF_LINE], *buff3, *buff4, *buff5, *buff6;
  mlib_d64 k[KSIZE*KSIZE];
  mlib_d64 k0, k1, k2, k3, k4, k5, k6, k7, k8, k9;
  mlib_d64 p00, p01, p02, p03, p04, p05,
           p10, p11, p12, p13, p14, p15,
           p20, p21, p22, p23, p24,
           p30, p31, p32, p33, p34,
           p40, p41, p42, p43, p44;
  mlib_s32 *sl2, *sl3, *sl4;
  DEF_VARS(mlib_s32);
  mlib_s32 chan2 = chan1 + chan1;
  mlib_s32 chan3 = chan1 + chan2;

  if (wid > BUFF_LINE) {
    pbuff = mlib_malloc((KSIZE + 2)*sizeof(mlib_d64)*wid);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  buff0 = pbuff;
  buff1 = buff0 + wid;
  buff2 = buff1 + wid;
  buff3 = buff2 + wid;
  buff4 = buff3 + wid;
  buff5 = buff4 + wid;
  buff6 = buff5 + wid;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  adr_dst += ((KSIZE - 1)/2)*(dll + chan1);

  CALC_SCALE();
  for (j = 0; j < 25; j++) k[j] = scalef * kern[j];

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    sl1 = sl  + sll;
    sl2 = sl1 + sll;
    sl3 = sl2 + sll;
    sl4 = sl3 + sll;
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < wid + (KSIZE - 1); i++) {
      buff0[i] = (mlib_d64)sl[i*chan1];
      buff1[i] = (mlib_d64)sl1[i*chan1];
      buff2[i] = (mlib_d64)sl2[i*chan1];
      buff3[i] = (mlib_d64)sl3[i*chan1];
      buff4[i] = (mlib_d64)sl4[i*chan1];
    }

    sl += KSIZE*sll;

    for (j = 0; j < hgt; j++) {
      /*
       *  First loop
       */
      k0 = k[0]; k1 = k[1]; k2 = k[2]; k3 = k[3]; k4 = k[4];
      k5 = k[5]; k6 = k[6]; k7 = k[7]; k8 = k[8]; k9 = k[9];

      sp = sl;
      dp = dl;

      p02 = buff0[0];
      p12 = buff1[0];
      p03 = buff0[1];
      p13 = buff1[1];
      p04 = buff0[2];
      p14 = buff1[2];

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 2); i += 2) {
        p00 = p02; p10 = p12;
        p01 = p03; p11 = p13;
        p02 = p04; p12 = p14;

        p03 = buff0[i + 3]; p13 = buff1[i + 3];
        p04 = buff0[i + 4]; p14 = buff1[i + 4];
        p05 = buff0[i + 5]; p15 = buff1[i + 5];

        buff6[i    ] = (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                        p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
        buff6[i + 1] = (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 + p05 * k4 +
                        p11 * k5 + p12 * k6 + p13 * k7 + p14 * k8 + p15 * k9);

        sp += chan2;
        dp += chan2;
      }

      /*
       *  Second loop
       */
      k0 = k[10]; k1 = k[11]; k2 = k[12]; k3 = k[13]; k4 = k[14];
      k5 = k[15]; k6 = k[16]; k7 = k[17]; k8 = k[18]; k9 = k[19];

      sp = sl;
      dp = dl;

      p02 = buff2[0];
      p12 = buff3[0];
      p03 = buff2[1];
      p13 = buff3[1];

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 2); i += 2) {
        p00 = p02; p10 = p12;
        p01 = p03; p11 = p13;

        p02 = buff2[i + 2]; p12 = buff3[i + 2];
        p03 = buff2[i + 3]; p13 = buff3[i + 3];
        p04 = buff2[i + 4]; p14 = buff3[i + 4];
        p05 = buff2[i + 5]; p15 = buff3[i + 5];

        buff6[i    ] += (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                         p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
        buff6[i + 1] += (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 + p05 * k4 +
                         p11 * k5 + p12 * k6 + p13 * k7 + p14 * k8 + p15 * k9);

        sp += chan2;
        dp += chan2;
      }

      /*
       *  3 loop
       */
      k0 = k[20]; k1 = k[21]; k2 = k[22]; k3 = k[23]; k4 = k[24];

      sp = sl;
      dp = dl;

      p02 = buff4[0];
      p03 = buff4[1];
      p04 = buff4[2];
      p05 = buff4[3];

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 2); i += 2) {
        p00 = p02; p01 = p03; p02 = p04; p03 = p05;

        p04 = buff4[i + 4]; p05 = buff4[i + 5];

        buff5[i    ] = (mlib_d64)sp[0];
        buff5[i + 1] = (mlib_d64)sp[chan1];

        d0 = p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 + buff6[i];
        d1 = p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 + p05 * k4 + buff6[i + 1];

        CLAMP_S32(dp[0    ], d0);
        CLAMP_S32(dp[chan1], d1);

        sp += chan2;
        dp += chan2;
      }

      /* last pixels */
      for (; i < wid; i++) {
        p00 = buff0[i];     p10 = buff1[i];     p20 = buff2[i];     p30 = buff3[i];
        p01 = buff0[i + 1]; p11 = buff1[i + 1]; p21 = buff2[i + 1]; p31 = buff3[i + 1];
        p02 = buff0[i + 2]; p12 = buff1[i + 2]; p22 = buff2[i + 2]; p32 = buff3[i + 2];
        p03 = buff0[i + 3]; p13 = buff1[i + 3]; p23 = buff2[i + 3]; p33 = buff3[i + 3];
        p04 = buff0[i + 4]; p14 = buff1[i + 4]; p24 = buff2[i + 4]; p34 = buff3[i + 4];

        p40 = buff4[i];        p41 = buff4[i + 1]; p42 = buff4[i + 2];
        p43 = buff4[i + 3]; p44 = buff4[i + 4];

        buff5[i] = (mlib_d64)sp[0];

        d0 = (p00 * k[0] + p01 * k[1] + p02 * k[2] + p03 * k[3] + p04 * k[4] +
              p10 * k[5] + p11 * k[6] + p12 * k[7] + p13 * k[8] + p14 * k[9] +
              p20 * k[10] + p21 * k[11] + p22 * k[12] + p23 * k[13] + p24 * k[14] +
              p30 * k[15] + p31 * k[16] + p32 * k[17] + p33 * k[18] + p34 * k[19] +
              p40 * k[20] + p41 * k[21] + p42 * k[22] + p43 * k[23] + p44 * k[24]);

        CLAMP_S32(dp[0], d0);

        sp += chan1;
        dp += chan1;
      }

      buff5[wid    ] = (mlib_d64)sp[0];
      buff5[wid + 1] = (mlib_d64)sp[chan1];
      buff5[wid + 2] = (mlib_d64)sp[chan2];
      buff5[wid + 3] = (mlib_d64)sp[chan3];

      /* next line */
      sl += sll;
      dl += dll;

      buffT = buff0;
      buff0 = buff1;
      buff1 = buff2;
      buff2 = buff3;
      buff3 = buff4;
      buff4 = buff5;
      buff5 = buffT;
    }
  }

  if (pbuff != buff) mlib_free(pbuff);

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  KSIZE
#define KSIZE 7

mlib_status CONV_FUNC(7x7)(mlib_image       *dst,
                           const mlib_image *src,
                           const mlib_s32   *kern,
                           mlib_s32         scalef_expon,
                           mlib_s32         cmask)
{
  mlib_d64 buff[(KSIZE + 2)*BUFF_LINE], *buffs[2*(KSIZE + 1)], *buffd;
  mlib_d64 k[KSIZE*KSIZE];
  mlib_d64 k0, k1, k2, k3, k4, k5, k6;
  mlib_d64 p0, p1, p2, p3, p4, p5, p6, p7;
  mlib_d64 d0, d1;
  mlib_s32 l, m, buff_ind, *sl2, *sl3, *sl4, *sl5, *sl6;
  mlib_d64 scalef;
  DEF_VARS_MxN(mlib_s32);
  mlib_s32 chan2 = chan1 + chan1;
  mlib_s32 *sl1;

  if (wid > BUFF_LINE) {
    pbuff = mlib_malloc((KSIZE + 2)*sizeof(mlib_d64)*wid);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  for (l = 0; l < KSIZE + 1; l++) buffs[l] = pbuff + l*wid;
  for (l = 0; l < KSIZE + 1; l++) buffs[l + (KSIZE + 1)] = buffs[l];
  buffd = buffs[KSIZE] + wid;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  adr_dst += ((KSIZE - 1)/2)*(dll + chan1);

  CALC_SCALE();
  for (j = 0; j < 49; j++) k[j] = scalef * kern[j];

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    sl1 = sl  + sll;
    sl2 = sl1 + sll;
    sl3 = sl2 + sll;
    sl4 = sl3 + sll;
    sl5 = sl4 + sll;
    sl6 = sl5 + sll;
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < wid + (KSIZE - 1); i++) {
      buffs[0][i] = (mlib_d64)sl[i*chan1];
      buffs[1][i] = (mlib_d64)sl1[i*chan1];
      buffs[2][i] = (mlib_d64)sl2[i*chan1];
      buffs[3][i] = (mlib_d64)sl3[i*chan1];
      buffs[4][i] = (mlib_d64)sl4[i*chan1];
      buffs[5][i] = (mlib_d64)sl5[i*chan1];
      buffs[6][i] = (mlib_d64)sl6[i*chan1];
    }

    buff_ind = 0;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < wid; i++) buffd[i] = 0.0;

    sl += KSIZE*sll;

    for (j = 0; j < hgt; j++) {
      mlib_d64 **buffc = buffs + buff_ind;
      mlib_d64 *buffn = buffc[KSIZE];
      mlib_d64 *pk = k;

      for (l = 0; l < KSIZE; l++) {
        mlib_d64 *buff = buffc[l];

        sp = sl;
        dp = dl;

        p2 = buff[0]; p3 = buff[1]; p4 = buff[2];
        p5 = buff[3]; p6 = buff[4]; p7 = buff[5];

        k0 = *pk++; k1 = *pk++; k2 = *pk++; k3 = *pk++;
        k4 = *pk++; k5 = *pk++; k6 = *pk++;

        if (l < (KSIZE - 1)) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
          for (i = 0; i <= (wid - 2); i += 2) {
            p0 = p2; p1 = p3; p2 = p4; p3 = p5; p4 = p6; p5 = p7;

            p6 = buff[i + 6]; p7 = buff[i + 7];

            buffd[i    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5 + p6*k6;
            buffd[i + 1] += p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5 + p7*k6;
          }

        } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
          for (i = 0; i <= (wid - 2); i += 2) {
            p0 = p2; p1 = p3; p2 = p4; p3 = p5; p4 = p6; p5 = p7;

            p6 = buff[i + 6]; p7 = buff[i + 7];

            buffn[i    ] = (mlib_d64)sp[0];
            buffn[i + 1] = (mlib_d64)sp[chan1];

            d0 = p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5 + p6*k6 + buffd[i    ];
            d1 = p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5 + p7*k6 + buffd[i + 1];

            CLAMP_S32(dp[0    ], d0);
            CLAMP_S32(dp[chan1], d1);

            buffd[i    ] = 0.0;
            buffd[i + 1] = 0.0;

            sp += chan2;
            dp += chan2;
          }
        }
      }

      /* last pixels */
      for (; i < wid; i++) {
        mlib_d64 *pk = k, s = 0;

        for (l = 0; l < KSIZE; l++) {
          mlib_d64 *buff = buffc[l] + i;

          for (m = 0; m < KSIZE; m++) s += buff[m] * (*pk++);
        }

        CLAMP_S32(dp[0], s);

        buffn[i] = (mlib_d64)sp[0];

        sp += chan1;
        dp += chan1;
      }

      for (l = 0; l < (KSIZE - 1); l++) buffn[wid + l] = sp[l*chan1];

      /* next line */
      sl += sll;
      dl += dll;

      buff_ind++;

      if (buff_ind >= KSIZE + 1) buff_ind = 0;
    }
  }

  if (pbuff != buff) mlib_free(pbuff);

  return MLIB_SUCCESS;
}

/***************************************************************/
#define FTYPE  mlib_d64
#define DTYPE  mlib_s32

#define BUFF_SIZE  1600

static mlib_status mlib_ImageConv1xN(mlib_image       *dst,
                                     const mlib_image *src,
                                     const mlib_d64   *k,
                                     mlib_s32         n,
                                     mlib_s32         dn,
                                     mlib_s32         cmask)
{
  FTYPE    buff[BUFF_SIZE];
  mlib_s32 off, kh;
  const FTYPE    *pk;
  FTYPE    k0, k1, k2, k3, d0, d1;
  FTYPE    p0, p1, p2, p3, p4;
  DTYPE    *sl_c, *dl_c, *sl0;
  mlib_s32 l, hsize, max_hsize;
  DEF_VARS_MxN(DTYPE);

  hgt -= (n - 1);
  adr_dst += dn*dll;

  max_hsize = (CACHE_SIZE/sizeof(DTYPE))/sll;

  if (!max_hsize) max_hsize = 1;

  if (max_hsize > BUFF_SIZE) {
    pbuff = mlib_malloc(sizeof(FTYPE)*max_hsize);
  }

  sl_c = adr_src;
  dl_c = adr_dst;

  for (l = 0; l < hgt; l += hsize) {
    hsize = hgt - l;

    if (hsize > max_hsize) hsize = max_hsize;

    for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

      sl = sl_c + c;
      dl = dl_c + c;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (j = 0; j < hsize; j++) pbuff[j] = 0.0;

      for (i = 0; i < wid; i++) {
        sl0 = sl;

        for (off = 0; off < (n - 4); off += 4) {
          pk = k + off;
          sp = sl0;

          k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];
          p2 = sp[0]; p3 = sp[sll]; p4 = sp[2*sll];
          sp += 3*sll;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
          for (j = 0; j < hsize; j += 2) {
            p0 = p2; p1 = p3; p2 = p4;
            p3 = sp[0];
            p4 = sp[sll];

            pbuff[j    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3;
            pbuff[j + 1] += p1*k0 + p2*k1 + p3*k2 + p4*k3;

            sp += 2*sll;
          }

          sl0 += 4*sll;
        }

        pk = k + off;
        sp = sl0;

        k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];
        p2 = sp[0]; p3 = sp[sll]; p4 = sp[2*sll];

        dp = dl;
        kh = n - off;

        if (kh == 4) {
          sp += 3*sll;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
          for (j = 0; j <= (hsize - 2); j += 2) {
            p0 = p2; p1 = p3; p2 = p4;
            p3 = sp[0];
            p4 = sp[sll];

            d0 = p0*k0 + p1*k1 + p2*k2 + p3*k3 + pbuff[j];
            d1 = p1*k0 + p2*k1 + p3*k2 + p4*k3 + pbuff[j + 1];
            CLAMP_S32(dp[0  ], d0);
            CLAMP_S32(dp[dll], d1);

            pbuff[j] = 0;
            pbuff[j + 1] = 0;

            sp += 2*sll;
            dp += 2*dll;
          }

          if (j < hsize) {
            p0 = p2; p1 = p3; p2 = p4;
            p3 = sp[0];

            d0 = p0*k0 + p1*k1 + p2*k2 + p3*k3 + pbuff[j];
            CLAMP_S32(dp[0], d0);

            pbuff[j] = 0;
          }

        } else if (kh == 3) {
          sp += 2*sll;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
          for (j = 0; j <= (hsize - 2); j += 2) {
            p0 = p2; p1 = p3;
            p2 = sp[0];
            p3 = sp[sll];

            d0 = p0*k0 + p1*k1 + p2*k2 + pbuff[j];
            d1 = p1*k0 + p2*k1 + p3*k2 + pbuff[j + 1];
            CLAMP_S32(dp[0  ], d0);
            CLAMP_S32(dp[dll], d1);

            pbuff[j] = 0;
            pbuff[j + 1] = 0;

            sp += 2*sll;
            dp += 2*dll;
          }

          if (j < hsize) {
            p0 = p2; p1 = p3;
            p2 = sp[0];

            d0 = p0*k0 + p1*k1 + p2*k2 + pbuff[j];
            CLAMP_S32(dp[0], d0);

            pbuff[j] = 0;
          }

        } else if (kh == 2) {
          sp += sll;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
          for (j = 0; j <= (hsize - 2); j += 2) {
            p0 = p2;
            p1 = sp[0];
            p2 = sp[sll];

            d0 = p0*k0 + p1*k1 + pbuff[j];
            d1 = p1*k0 + p2*k1 + pbuff[j + 1];
            CLAMP_S32(dp[0  ], d0);
            CLAMP_S32(dp[dll], d1);

            pbuff[j] = 0;
            pbuff[j + 1] = 0;

            sp += 2*sll;
            dp += 2*dll;
          }

          if (j < hsize) {
            p0 = p2;
            p1 = sp[0];

            d0 = p0*k0 + p1*k1 + pbuff[j];
            CLAMP_S32(dp[0], d0);

            pbuff[j] = 0;
          }

        } else /* if (kh == 1) */ {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
          for (j = 0; j < hsize; j++) {
            p0 = sp[0];

            d0 = p0*k0 + pbuff[j];
            CLAMP_S32(dp[0], d0);

            pbuff[j] = 0;

            sp += sll;
            dp += dll;
          }
        }

        sl += chan1;
        dl += chan1;
      }
    }

    sl_c += max_hsize*sll;
    dl_c += max_hsize*dll;
  }

  if (pbuff != buff) mlib_free(pbuff);

  return MLIB_SUCCESS;
}

/***************************************************************/
#define MAX_KER 7

#define MAX_N     15

#undef  BUFF_SIZE
#define BUFF_SIZE 1500

mlib_status CONV_FUNC(MxN)(mlib_image       *dst,
                           const mlib_image *src,
                           const mlib_s32   *kernel,
                           mlib_s32         m,
                           mlib_s32         n,
                           mlib_s32         dm,
                           mlib_s32         dn,
                           mlib_s32         scale,
                           mlib_s32         cmask)
{
  mlib_d64  buff[BUFF_SIZE], *buffs_arr[2*(MAX_N + 1)];
  mlib_d64  **buffs = buffs_arr, *buffd;
  mlib_d64  akernel[256], *k = akernel, fscale = 1.0;
  mlib_s32  l, off, kw, bsize, buff_ind, mn;
  mlib_d64  d0, d1;
  mlib_d64  k0, k1, k2, k3, k4, k5, k6;
  mlib_d64  p0, p1, p2, p3, p4, p5, p6, p7;
  DEF_VARS_MxN(mlib_s32);
  mlib_s32 chan2 = chan1 + chan1;

  mlib_status status = MLIB_SUCCESS;

  if (scale > 30) {
    fscale *= 1.0/(1 << 30);
    scale -= 30;
  }

  fscale /= (1 << scale);

  mn = m*n;

  if (mn > 256) {
    k = mlib_malloc(mn*sizeof(mlib_d64));

    if (k == NULL) return MLIB_FAILURE;
  }

  for (i = 0; i < mn; i++) {
    k[i] = kernel[i]*fscale;
  }

  if (m == 1) {
    status = mlib_ImageConv1xN(dst, src, k, n, dn, cmask);
    FREE_AND_RETURN_STATUS;
  }

  bsize = (n + 2)*wid;

  if ((bsize > BUFF_SIZE) || (n > MAX_N)) {
    pbuff = mlib_malloc(sizeof(mlib_d64)*bsize + sizeof(mlib_d64*)*2*(n + 1));

    if (pbuff == NULL) {
      status = MLIB_FAILURE;
      FREE_AND_RETURN_STATUS;
    }
    buffs = (mlib_d64**)(pbuff + bsize);
  }

  for (l = 0; l < (n + 1); l++) buffs[l] = pbuff + l*wid;
  for (l = 0; l < (n + 1); l++) buffs[l + (n + 1)] = buffs[l];
  buffd = buffs[n] + wid;

  wid -= (m - 1);
  hgt -= (n - 1);
  adr_dst += dn*dll + dm*chan1;

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    for (l = 0; l < n; l++) {
      mlib_d64 *buff = buffs[l];

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i < wid + (m - 1); i++) {
        buff[i] = (mlib_d64)sl[i*chan1];
      }

      sl += sll;
    }

    buff_ind = 0;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < wid; i++) buffd[i] = 0.0;

    for (j = 0; j < hgt; j++) {
      mlib_d64 **buffc = buffs + buff_ind;
      mlib_d64 *buffn = buffc[n];
      mlib_d64 *pk = k;

      for (l = 0; l < n; l++) {
        mlib_d64 *buff_l = buffc[l];

        for (off = 0; off < m;) {
          mlib_d64 *buff = buff_l + off;

          kw = m - off;

          if (kw > 2*MAX_KER) kw = MAX_KER; else
            if (kw > MAX_KER) kw = kw/2;
          off += kw;

          sp = sl;
          dp = dl;

          p2 = buff[0]; p3 = buff[1]; p4 = buff[2];
          p5 = buff[3]; p6 = buff[4]; p7 = buff[5];

          k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];
          k4 = pk[4]; k5 = pk[5]; k6 = pk[6];
          pk += kw;

          if (kw == 7) {

            if (l < (n - 1) || off < m) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5; p4 = p6; p5 = p7;

                p6 = buff[i + 6]; p7 = buff[i + 7];

                buffd[i    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5 + p6*k6;
                buffd[i + 1] += p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5 + p7*k6;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5; p4 = p6; p5 = p7;

                p6 = buff[i + 6]; p7 = buff[i + 7];

                buffn[i    ] = (mlib_d64)sp[0];
                buffn[i + 1] = (mlib_d64)sp[chan1];

                d0 = p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5 + p6*k6 + buffd[i    ];
                d1 = p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5 + p7*k6 + buffd[i + 1];

                CLAMP_S32(dp[0],     d0);
                CLAMP_S32(dp[chan1], d1);

                buffd[i    ] = 0.0;
                buffd[i + 1] = 0.0;

                sp += chan2;
                dp += chan2;
              }
            }

          } else if (kw == 6) {

            if (l < (n - 1) || off < m) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5; p4 = p6;

                p5 = buff[i + 5]; p6 = buff[i + 6];

                buffd[i    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5;
                buffd[i + 1] += p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5; p4 = p6;

                p5 = buff[i + 5]; p6 = buff[i + 6];

                buffn[i    ] = (mlib_d64)sp[0];
                buffn[i + 1] = (mlib_d64)sp[chan1];

                d0 = p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5 + buffd[i    ];
                d1 = p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5 + buffd[i + 1];

                CLAMP_S32(dp[0],     d0);
                CLAMP_S32(dp[chan1], d1);

                buffd[i    ] = 0.0;
                buffd[i + 1] = 0.0;

                sp += chan2;
                dp += chan2;
              }
            }

          } else if (kw == 5) {

            if (l < (n - 1) || off < m) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5;

                p4 = buff[i + 4]; p5 = buff[i + 5];

                buffd[i    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4;
                buffd[i + 1] += p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5;

                p4 = buff[i + 4]; p5 = buff[i + 5];

                buffn[i    ] = (mlib_d64)sp[0];
                buffn[i + 1] = (mlib_d64)sp[chan1];

                d0 = p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + buffd[i    ];
                d1 = p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + buffd[i + 1];

                CLAMP_S32(dp[0],     d0);
                CLAMP_S32(dp[chan1], d1);

                buffd[i    ] = 0.0;
                buffd[i + 1] = 0.0;

                sp += chan2;
                dp += chan2;
              }
            }

          } else if (kw == 4) {

            if (l < (n - 1) || off < m) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4;

                p3 = buff[i + 3]; p4 = buff[i + 4];

                buffd[i    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3;
                buffd[i + 1] += p1*k0 + p2*k1 + p3*k2 + p4*k3;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4;

                p3 = buff[i + 3]; p4 = buff[i + 4];

                buffn[i    ] = (mlib_d64)sp[0];
                buffn[i + 1] = (mlib_d64)sp[chan1];

                d0 = p0*k0 + p1*k1 + p2*k2 + p3*k3 + buffd[i    ];
                d1 = p1*k0 + p2*k1 + p3*k2 + p4*k3 + buffd[i + 1];

                CLAMP_S32(dp[0],     d0);
                CLAMP_S32(dp[chan1], d1);

                buffd[i    ] = 0.0;
                buffd[i + 1] = 0.0;

                sp += chan2;
                dp += chan2;
              }
            }

          } else if (kw == 3) {

            if (l < (n - 1) || off < m) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3;

                p2 = buff[i + 2]; p3 = buff[i + 3];

                buffd[i    ] += p0*k0 + p1*k1 + p2*k2;
                buffd[i + 1] += p1*k0 + p2*k1 + p3*k2;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3;

                p2 = buff[i + 2]; p3 = buff[i + 3];

                buffn[i    ] = (mlib_d64)sp[0];
                buffn[i + 1] = (mlib_d64)sp[chan1];

                d0 = p0*k0 + p1*k1 + p2*k2 + buffd[i    ];
                d1 = p1*k0 + p2*k1 + p3*k2 + buffd[i + 1];

                CLAMP_S32(dp[0],     d0);
                CLAMP_S32(dp[chan1], d1);

                buffd[i    ] = 0.0;
                buffd[i + 1] = 0.0;

                sp += chan2;
                dp += chan2;
              }
            }

          } else { /* kw == 2 */

            if (l < (n - 1) || off < m) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2;

                p1 = buff[i + 1]; p2 = buff[i + 2];

                buffd[i    ] += p0*k0 + p1*k1;
                buffd[i + 1] += p1*k0 + p2*k1;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2;

                p1 = buff[i + 1]; p2 = buff[i + 2];

                buffn[i    ] = (mlib_d64)sp[0];
                buffn[i + 1] = (mlib_d64)sp[chan1];

                d0 = p0*k0 + p1*k1 + buffd[i    ];
                d1 = p1*k0 + p2*k1 + buffd[i + 1];

                CLAMP_S32(dp[0],     d0);
                CLAMP_S32(dp[chan1], d1);

                buffd[i    ] = 0.0;
                buffd[i + 1] = 0.0;

                sp += chan2;
                dp += chan2;
              }
            }
          }
        }
      }

      /* last pixels */
      for (; i < wid; i++) {
        mlib_d64 *pk = k, s = 0;
        mlib_s32 x;

        for (l = 0; l < n; l++) {
          mlib_d64 *buff = buffc[l] + i;

          for (x = 0; x < m; x++) s += buff[x] * (*pk++);
        }

        CLAMP_S32(dp[0], s);

        buffn[i] = (mlib_d64)sp[0];

        sp += chan1;
        dp += chan1;
      }

      for (l = 0; l < (m - 1); l++) buffn[wid + l] = sp[l*chan1];

      /* next line */
      sl += sll;
      dl += dll;

      buff_ind++;

      if (buff_ind >= n + 1) buff_ind = 0;
    }
  }

  FREE_AND_RETURN_STATUS;
}

/***************************************************************/
