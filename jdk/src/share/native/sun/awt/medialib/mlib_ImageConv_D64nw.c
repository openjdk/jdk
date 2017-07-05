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


/*
 * FUNCTION
 *   Internal functions for mlib_ImageConv* on D64/F32 type and
 *   MLIB_EDGE_DST_NO_WRITE mask
 *
 */

#include "mlib_image.h"
#include "mlib_ImageConv.h"

/***************************************************************/
/*
  This define switches between functions of MLIB_DOUBLE and MLIB_FLOAT types:
  Files mlib_ImageConv_D64nw.c and mlib_ImageConv_F32nw.c
*/

#define TYPE_DOUBLE

/***************************************************************/
#ifdef TYPE_DOUBLE

#define CONV_FUNC(KERN) mlib_conv##KERN##nw_d64

#define DTYPE mlib_d64

#else

#define CONV_FUNC(KERN) mlib_conv##KERN##nw_f32

#define DTYPE mlib_f32

#endif /* TYPE_DOUBLE */

/***************************************************************/
#define GET_SRC_DST_PARAMETERS(type)                            \
  mlib_s32 hgt = mlib_ImageGetHeight(src);                      \
  mlib_s32 wid = mlib_ImageGetWidth(src);                       \
  mlib_s32 sll = mlib_ImageGetStride(src) / sizeof(type);       \
  mlib_s32 dll = mlib_ImageGetStride(dst) / sizeof(type);       \
  type*    adr_src = mlib_ImageGetData(src);                    \
  type*    adr_dst = mlib_ImageGetData(dst);                    \
  mlib_s32 chan1 = mlib_ImageGetChannels(src)

/***************************************************************/
#define DEF_VARS(type)                                          \
  GET_SRC_DST_PARAMETERS(type);                                 \
  type     *sl;                                                 \
  type     *dl, *dp;                                            \
  mlib_s32 i, j, c

/***************************************************************/
#undef  KSIZE
#define KSIZE 2

mlib_status CONV_FUNC(2x2)(mlib_image       *dst,
                           const mlib_image *src,
                           const mlib_d64   *kern,
                           mlib_s32         cmask)
{
  DEF_VARS(DTYPE);
  DTYPE    *sp0, *sp1;
  mlib_s32 chan2 = chan1 + chan1;
  mlib_s32 chan3 = chan1 + chan2;
  mlib_s32 chan4 = chan3 + chan1;
  DTYPE k0, k1, k2, k3;
  DTYPE p00, p01, p02, p03, p04,
        p10, p11, p12, p13, p14;

  /* keep kernel in regs */
  k0 = (DTYPE)kern[0];  k1 = (DTYPE)kern[1];
  k2 = (DTYPE)kern[2];  k3 = (DTYPE)kern[3];

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    dl = adr_dst + c;
    sl = adr_src + c;

    for (j = 0; j < hgt; j++) {
      dp  = dl;
      sp0 = sl;
      sp1 = sp0 + sll;

      p04 = sp0[0];
      p14 = sp1[0];

      sp0 += chan1;
      sp1 += chan1;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 4); i += 4) {
        p00 = p04; p10 = p14;

        p01 = sp0[0];     p11 = sp1[0];
        p02 = sp0[chan1]; p12 = sp1[chan1];
        p03 = sp0[chan2]; p13 = sp1[chan2];
        p04 = sp0[chan3]; p14 = sp1[chan3];

        dp[0    ] = p00 * k0 + p01 * k1 + p10 * k2 + p11 * k3;
        dp[chan1] = p01 * k0 + p02 * k1 + p11 * k2 + p12 * k3;
        dp[chan2] = p02 * k0 + p03 * k1 + p12 * k2 + p13 * k3;
        dp[chan3] = p03 * k0 + p04 * k1 + p13 * k2 + p14 * k3;

        dp  += chan4;
        sp0 += chan4;
        sp1 += chan4;
      }

      if (i < wid) {
        p00 = p04;    p10 = p14;
        p01 = sp0[0]; p11 = sp1[0];
        dp[0] = p00 * k0 + p01 * k1 + p10 * k2 + p11 * k3;

        if ((i + 1) < wid) {
          p02 = sp0[chan1]; p12 = sp1[chan1];
          dp[chan1] = p01 * k0 + p02 * k1 + p11 * k2 + p12 * k3;

          if ((i + 2) < wid) {
            p03 = sp0[chan2]; p13 = sp1[chan2];
            dp[chan2] = p02 * k0 + p03 * k1 + p12 * k2 + p13 * k3;
          }
        }
      }

      sl += sll;
      dl += dll;
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  KSIZE
#define KSIZE 3

mlib_status CONV_FUNC(3x3)(mlib_image       *dst,
                           const mlib_image *src,
                           const mlib_d64   *kern,
                           mlib_s32         cmask)
{
  DEF_VARS(DTYPE);
  mlib_s32 chan2 = chan1 + chan1;
  DTYPE    *sp0, *sp1;
  DTYPE *sp2;
  DTYPE k0, k1, k2, k3, k4, k5, k6, k7, k8;
  DTYPE p02, p03, p12, p13, p22, p23;

  /* keep kernel in regs */
  k0 = (DTYPE)kern[0];  k1 = (DTYPE)kern[1];  k2 = (DTYPE)kern[2];
  k3 = (DTYPE)kern[3];  k4 = (DTYPE)kern[4];  k5 = (DTYPE)kern[5];
  k6 = (DTYPE)kern[6];  k7 = (DTYPE)kern[7];  k8 = (DTYPE)kern[8];

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  adr_dst += ((KSIZE - 1)/2)*(dll + chan1);

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    for (j = 0; j < hgt; j++) {
      DTYPE s0, s1;

      dp  = dl;
      sp0 = sl;
      sp1 = sp0 + sll;
      sp2 = sp1 + sll;

      p02 = sp0[0];
      p12 = sp1[0];
      p22 = sp2[0];

      p03 = sp0[chan1];
      p13 = sp1[chan1];
      p23 = sp2[chan1];

      s0 = p02 * k0 + p03 * k1 + p12 * k3 + p13 * k4 + p22 * k6 + p23 * k7;
      s1 = p03 * k0 + p13 * k3 + p23 * k6;

      sp0 += chan2;
      sp1 += chan2;
      sp2 += chan2;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 2); i += 2) {
        p02 = sp0[0];     p12 = sp1[0];     p22 = sp2[0];
        p03 = sp0[chan1]; p13 = sp1[chan1]; p23 = sp2[chan1];

        dp[0    ] = s0 + p02 * k2 + p12 * k5 + p22 * k8;
        dp[chan1] = s1 + p02 * k1 + p03 * k2 + p12 * k4 + p13 * k5 + p22 * k7 + p23 * k8;

        s0 = p02 * k0 + p03 * k1 + p12 * k3 + p13 * k4 + p22 * k6 + p23 * k7;
        s1 = p03 * k0 + p13 * k3 + p23 * k6;

        sp0 += chan2;
        sp1 += chan2;
        sp2 += chan2;
        dp += chan2;
      }

      if (wid & 1) {
        p02 = sp0[0]; p12 = sp1[0]; p22 = sp2[0];
        dp[0] = s0 + p02 * k2 + p12 * k5 + p22 * k8;
      }

      sl += sll;
      dl += dll;
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  KSIZE
#define KSIZE 4

mlib_status CONV_FUNC(4x4)(mlib_image       *dst,
                           const mlib_image *src,
                           const mlib_d64   *k,
                           mlib_s32         cmask)
{
  DTYPE k0, k1, k2, k3, k4, k5, k6, k7;
  DTYPE p00, p01, p02, p03, p04,
        p10, p11, p12, p13, p14;
  DEF_VARS(DTYPE);
  DTYPE    *sp0, *sp1;
  mlib_s32 chan2 = chan1 + chan1;
  mlib_s32 chan3 = chan1 + chan2;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  adr_dst += ((KSIZE - 1)/2)*(dll + chan1);

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    for (j = 0; j < hgt; j++) {
      /*
       *  First loop on two first lines of kernel
       */
      sp0 = sl;
      sp1 = sp0 + sll;
      dp = dl;

      k0 = (DTYPE)k[0]; k1 = (DTYPE)k[1]; k2 = (DTYPE)k[2]; k3 = (DTYPE)k[3];
      k4 = (DTYPE)k[4]; k5 = (DTYPE)k[5]; k6 = (DTYPE)k[6]; k7 = (DTYPE)k[7];

      p02 = sp0[0];     p12 = sp1[0];
      p03 = sp0[chan1]; p13 = sp1[chan1];
      p04 = sp0[chan2]; p14 = sp1[chan2];

      sp0 += chan3;
      sp1 += chan3;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 2); i += 2) {
        p00 = p02; p10 = p12;
        p01 = p03; p11 = p13;
        p02 = p04; p12 = p14;

        p03 = sp0[0];     p13 = sp1[0];
        p04 = sp0[chan1]; p14 = sp1[chan1];

        dp[0    ] = (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 +
                     p10 * k4 + p11 * k5 + p12 * k6 + p13 * k7);
        dp[chan1] = (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 +
                     p11 * k4 + p12 * k5 + p13 * k6 + p14 * k7);

        sp0 += chan2;
        sp1 += chan2;
        dp += chan2;
      }

      if (wid & 1) {
        p00 = p02; p10 = p12;
        p01 = p03; p11 = p13;
        p02 = p04; p12 = p14;
        p03 = sp0[0]; p13 = sp1[0];

        dp[0] = (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 +
                 p10 * k4 + p11 * k5 + p12 * k6 + p13 * k7);
      }

      /*
       *  Second loop on two last lines of kernel
       */
      sp0 = sl + 2*sll;
      sp1 = sp0 + sll;
      dp = dl;

      k0 = (DTYPE)k[ 8]; k1 = (DTYPE)k[ 9]; k2 = (DTYPE)k[10]; k3 = (DTYPE)k[11];
      k4 = (DTYPE)k[12]; k5 = (DTYPE)k[13]; k6 = (DTYPE)k[14]; k7 = (DTYPE)k[15];

      p02 = sp0[0];     p12 = sp1[0];
      p03 = sp0[chan1]; p13 = sp1[chan1];
      p04 = sp0[chan2]; p14 = sp1[chan2];

      sp0 += chan3;
      sp1 += chan3;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 2); i += 2) {
        p00 = p02; p10 = p12;
        p01 = p03; p11 = p13;
        p02 = p04; p12 = p14;

        p03 = sp0[0];     p13 = sp1[0];
        p04 = sp0[chan1]; p14 = sp1[chan1];

        dp[0    ] += (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 +
                      p10 * k4 + p11 * k5 + p12 * k6 + p13 * k7);
        dp[chan1] += (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 +
                      p11 * k4 + p12 * k5 + p13 * k6 + p14 * k7);

        sp0 += chan2;
        sp1 += chan2;
        dp += chan2;
      }

      if (wid & 1) {
        p00 = p02; p10 = p12;
        p01 = p03; p11 = p13;
        p02 = p04; p12 = p14;
        p03 = sp0[0]; p13 = sp1[0];

        dp[0] += (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 +
                  p10 * k4 + p11 * k5 + p12 * k6 + p13 * k7);
      }

      /* next line */
      sl += sll;
      dl += dll;
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  KSIZE
#define KSIZE 5

mlib_status CONV_FUNC(5x5)(mlib_image       *dst,
                           const mlib_image *src,
                           const mlib_d64   *k,
                           mlib_s32         cmask)
{
  DTYPE k0, k1, k2, k3, k4, k5, k6, k7, k8, k9;
  DTYPE p00, p01, p02, p03, p04, p05,
        p10, p11, p12, p13, p14, p15;
  DEF_VARS(DTYPE);
  DTYPE    *sp0, *sp1;
  mlib_s32 chan2 = chan1 + chan1;
  mlib_s32 chan3 = chan1 + chan2;
  mlib_s32 chan4 = chan3 + chan1;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  adr_dst += ((KSIZE - 1)/2)*(dll + chan1);

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    for (j = 0; j < hgt; j++) {
      /*
       *  First loop
       */
      sp0 = sl;
      sp1 = sp0 + sll;
      dp = dl;

      k0 = (DTYPE)k[0]; k1 = (DTYPE)k[1]; k2 = (DTYPE)k[2]; k3 = (DTYPE)k[3]; k4 = (DTYPE)k[4];
      k5 = (DTYPE)k[5]; k6 = (DTYPE)k[6]; k7 = (DTYPE)k[7]; k8 = (DTYPE)k[8]; k9 = (DTYPE)k[9];

      p02 = sp0[0];     p12 = sp1[0];
      p03 = sp0[chan1]; p13 = sp1[chan1];
      p04 = sp0[chan2]; p14 = sp1[chan2];
      p05 = sp0[chan3]; p15 = sp1[chan3];

      sp0 += chan4;
      sp1 += chan4;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 2); i += 2) {
        p00 = p02; p10 = p12;
        p01 = p03; p11 = p13;
        p02 = p04; p12 = p14;
        p03 = p05; p13 = p15;

        p04 = sp0[0];     p14 = sp1[0];
        p05 = sp0[chan1]; p15 = sp1[chan1];

        dp[    0] = (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                     p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
        dp[chan1] = (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 + p05 * k4 +
                     p11 * k5 + p12 * k6 + p13 * k7 + p14 * k8 + p15 * k9);

        sp0 += chan2;
        sp1 += chan2;
        dp += chan2;
      }

      if (wid & 1) {
        p00 = p02; p10 = p12;
        p01 = p03; p11 = p13;
        p02 = p04; p12 = p14;
        p03 = p05; p13 = p15;

        p04 = sp0[0];     p14 = sp1[0];

        dp[0] = (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                 p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
      }

      /*
       *  Second loop
       */
      sp0 = sl + 2*sll;
      sp1 = sp0 + sll;
      dp = dl;

      k0 = (DTYPE)k[10]; k1 = (DTYPE)k[11]; k2 = (DTYPE)k[12]; k3 = (DTYPE)k[13]; k4 = (DTYPE)k[14];
      k5 = (DTYPE)k[15]; k6 = (DTYPE)k[16]; k7 = (DTYPE)k[17]; k8 = (DTYPE)k[18]; k9 = (DTYPE)k[19];

      p02 = sp0[0];     p12 = sp1[0];
      p03 = sp0[chan1]; p13 = sp1[chan1];
      p04 = sp0[chan2]; p14 = sp1[chan2];
      p05 = sp0[chan3]; p15 = sp1[chan3];

      sp0 += chan4;
      sp1 += chan4;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 2); i += 2) {
        p00 = p02; p10 = p12;
        p01 = p03; p11 = p13;
        p02 = p04; p12 = p14;
        p03 = p05; p13 = p15;

        p04 = sp0[0];     p14 = sp1[0];
        p05 = sp0[chan1]; p15 = sp1[chan1];

        dp[    0] += (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                      p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
        dp[chan1] += (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 + p05 * k4 +
                      p11 * k5 + p12 * k6 + p13 * k7 + p14 * k8 + p15 * k9);

        sp0 += chan2;
        sp1 += chan2;
        dp += chan2;
      }

      if (wid & 1) {
        p00 = p02; p10 = p12;
        p01 = p03; p11 = p13;
        p02 = p04; p12 = p14;
        p03 = p05; p13 = p15;

        p04 = sp0[0];     p14 = sp1[0];

        dp[0] += (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                  p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
      }

      /*
       *  3 loop
       */
      dp = dl;
      sp0 = sl + 4*sll;

      k0 = (DTYPE)k[20]; k1 = (DTYPE)k[21]; k2 = (DTYPE)k[22]; k3 = (DTYPE)k[23]; k4 = (DTYPE)k[24];

      p02 = sp0[0];
      p03 = sp0[chan1];
      p04 = sp0[chan2];
      p05 = sp0[chan3];

      sp0 += chan2 + chan2;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 2); i += 2) {
        p00 = p02; p01 = p03; p02 = p04; p03 = p05;

        p04 = sp0[0]; p05 = sp0[chan1];

        dp[0    ] += p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4;
        dp[chan1] += p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 + p05 * k4;

        dp  += chan2;
        sp0 += chan2;
      }

      if (wid & 1) {
        p00 = p02; p01 = p03; p02 = p04; p03 = p05;

        p04 = sp0[0];

        dp[0] += p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4;
      }

      /* next line */
      sl += sll;
      dl += dll;
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#define BUFF_SIZE  1600

#define CACHE_SIZE (64*1024)

static mlib_status mlib_ImageConv1xN(mlib_image       *dst,
                                     const mlib_image *src,
                                     const DTYPE      *k,
                                     mlib_s32         n,
                                     mlib_s32         dn,
                                     mlib_s32         cmask)
{
  DTYPE    buff[BUFF_SIZE], *pbuff = buff;
  const DTYPE    *pk;
  DTYPE    k0, k1, k2, k3;
  DTYPE    p0, p1, p2, p3, p4;
  DTYPE    *sp, *sl_c, *dl_c, *sl0;
  DEF_VARS(DTYPE);
  mlib_s32 off, kh;
  mlib_s32 l, hsize, max_hsize;

  hgt -= (n - 1);
  adr_dst += dn*dll;

  max_hsize = (CACHE_SIZE/sizeof(DTYPE))/sll;

  if (!max_hsize) max_hsize = 1;

  if (max_hsize > BUFF_SIZE) {
    pbuff = mlib_malloc(sizeof(DTYPE)*max_hsize);
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

            dp[0  ] = p0*k0 + p1*k1 + p2*k2 + p3*k3 + pbuff[j];
            dp[dll] = p1*k0 + p2*k1 + p3*k2 + p4*k3 + pbuff[j + 1];

            pbuff[j] = 0;
            pbuff[j + 1] = 0;

            sp += 2*sll;
            dp += 2*dll;
          }

          if (j < hsize) {
            p0 = p2; p1 = p3; p2 = p4;
            p3 = sp[0];

            dp[0] = p0*k0 + p1*k1 + p2*k2 + p3*k3 + pbuff[j];

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

            dp[0  ] = p0*k0 + p1*k1 + p2*k2 + pbuff[j];
            dp[dll] = p1*k0 + p2*k1 + p3*k2 + pbuff[j + 1];

            pbuff[j] = 0;
            pbuff[j + 1] = 0;

            sp += 2*sll;
            dp += 2*dll;
          }

          if (j < hsize) {
            p0 = p2; p1 = p3;
            p2 = sp[0];

            dp[0] = p0*k0 + p1*k1 + p2*k2 + pbuff[j];

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

            dp[0  ] = p0*k0 + p1*k1 + pbuff[j];
            dp[dll] = p1*k0 + p2*k1 + pbuff[j + 1];

            pbuff[j] = 0;
            pbuff[j + 1] = 0;

            sp += 2*sll;
            dp += 2*dll;
          }

          if (j < hsize) {
            p0 = p2;
            p1 = sp[0];

            dp[0] = p0*k0 + p1*k1 + pbuff[j];

            pbuff[j] = 0;
          }

        } else /* if (kh == 1) */ {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
          for (j = 0; j < hsize; j++) {
            p0 = sp[0];

            dp[0] = p0*k0 + pbuff[j];

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
#define MAX_NM  81

mlib_status CONV_FUNC(MxN)(mlib_image       *dst,
                           const mlib_image *src,
                           const mlib_d64   *ker,
                           mlib_s32         m,
                           mlib_s32         n,
                           mlib_s32         dm,
                           mlib_s32         dn,
                           mlib_s32         cmask)
{
  DTYPE k0, k1, k2, k3, k4, k5, k6, *sp;
  DTYPE p0, p1, p2, p3, p4, p5, p6, p7;
  mlib_s32 l, off, kw;
  DEF_VARS(DTYPE);
  mlib_s32 chan2 = chan1 + chan1;
  mlib_s32 chan3 = chan1 + chan2;

#ifdef TYPE_DOUBLE
  const mlib_d64 *k = ker;
#else
  mlib_f32 k_arr[MAX_NM], *k = k_arr;

  if (n*m > MAX_NM) {
    k = mlib_malloc(n*m*sizeof(mlib_f32));

    if (k == NULL) return MLIB_FAILURE;
  }

  for (i = 0; i < n*m; i++) k[i] = (mlib_f32)ker[i];
#endif /* TYPE_DOUBLE */

  if (m == 1) return mlib_ImageConv1xN(dst, src, k, n, dn, cmask);

  wid -= (m - 1);
  hgt -= (n - 1);
  adr_dst += dn*dll + dm*chan1;

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    for (j = 0; j < hgt; j++) {
      const DTYPE *pk = k;

      for (l = 0; l < n; l++) {
        DTYPE *sp0 = sl + l*sll;

        for (off = 0; off < m; off += kw, pk += kw, sp0 += chan1) {
          kw = m - off;

          if (kw > 2*MAX_KER) kw = MAX_KER; else
            if (kw > MAX_KER) kw = kw/2;

          p2 = sp0[0]; p3 = sp0[chan1]; p4 = sp0[chan2];
          sp0 += chan3;
          p5 = sp0[0]; p6 = sp0[chan1]; p7 = sp0[chan2];

          k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];
          k4 = pk[4]; k5 = pk[5]; k6 = pk[6];

          dp = dl;

          if (kw == 7) {
            sp = sp0 += chan3;

            if (pk == k) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5; p4 = p6;

                p5 = sp[- chan1]; p6 = sp[0]; p7 = sp[chan1];

                dp[0    ] = p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5 + p6*k6;
                dp[chan1] = p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5 + p7*k6;

                sp += chan2;
                dp += chan2;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5; p4 = p6;

                p5 = sp[- chan1]; p6 = sp[0]; p7 = sp[chan1];

                dp[0    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5 + p6*k6;
                dp[chan1] += p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5 + p7*k6;

                sp += chan2;
                dp += chan2;
              }
            }

          } else if (kw == 6) {
            sp = sp0 += chan2;

            if (pk == k) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5; p4 = p6;

                p5 = sp[0]; p6 = sp[chan1];

                dp[0    ] = p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5;
                dp[chan1] = p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5;

                sp += chan2;
                dp += chan2;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5; p4 = p6;

                p5 = sp[0]; p6 = sp[chan1];

                dp[0    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5;
                dp[chan1] += p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5;

                sp += chan2;
                dp += chan2;
              }
            }

          } else if (kw == 5) {
            sp = sp0 += chan1;

            if (pk == k) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5;

                p4 = sp[0]; p5 = sp[chan1];

                dp[0    ] = p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4;
                dp[chan1] = p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4;

                sp += chan2;
                dp += chan2;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5;

                p4 = sp[0]; p5 = sp[chan1];

                dp[0    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4;
                dp[chan1] += p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4;

                sp += chan2;
                dp += chan2;
              }
            }

          } else if (kw == 4) {

            sp = sp0;

            if (pk == k) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4;

                p3 = sp[0]; p4 = sp[chan1];

                dp[0    ] = p0*k0 + p1*k1 + p2*k2 + p3*k3;
                dp[chan1] = p1*k0 + p2*k1 + p3*k2 + p4*k3;

                sp += chan2;
                dp += chan2;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4;

                p3 = sp[0]; p4 = sp[chan1];

                dp[0    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3;
                dp[chan1] += p1*k0 + p2*k1 + p3*k2 + p4*k3;

                sp += chan2;
                dp += chan2;
              }
            }

          } else if (kw == 3) {
            sp = sp0 -= chan1;

            if (pk == k) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3;

                p2 = sp[0]; p3 = sp[chan1];

                dp[0    ] = p0*k0 + p1*k1 + p2*k2;
                dp[chan1] = p1*k0 + p2*k1 + p3*k2;

                sp += chan2;
                dp += chan2;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3;

                p2 = sp[0]; p3 = sp[chan1];

                dp[0    ] += p0*k0 + p1*k1 + p2*k2;
                dp[chan1] += p1*k0 + p2*k1 + p3*k2;

                sp += chan2;
                dp += chan2;
              }
            }

          } else { /* kw == 2 */
            sp = sp0 -= chan2;

            if (pk == k) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2;

                p1 = sp[0]; p2 = sp[chan1];

                dp[0    ] = p0*k0 + p1*k1;
                dp[chan1] = p1*k0 + p2*k1;

                sp += chan2;
                dp += chan2;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2;

                p1 = sp[0]; p2 = sp[chan1];

                dp[0    ] += p0*k0 + p1*k1;
                dp[chan1] += p1*k0 + p2*k1;

                sp += chan2;
                dp += chan2;
              }
            }
          }
        }
      }

      /* last pixels */

      if (wid & 1) {
        DTYPE *sp0 = sl + i*chan1, s = 0;
        const DTYPE *pk = k;
        mlib_s32 x;

        for (l = 0; l < n; l++) {
          DTYPE *sp = sp0 + l*sll;

          for (x = 0; x < m; x++) s += sp[x*chan1] * (*pk++);
        }

        dp[0] = s;
      }

      /* next line */
      sl += sll;
      dl += dll;
    }
  }

#ifndef TYPE_DOUBLE

  if (k != k_arr) mlib_free(k);
#endif /* TYPE_DOUBLE */

  return MLIB_SUCCESS;
}

/***************************************************************/
