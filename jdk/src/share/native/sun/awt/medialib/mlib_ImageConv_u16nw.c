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
 *   Internal functions for mlib_ImageConv* on U8/S16/U16 types and
 *   MLIB_EDGE_DST_NO_WRITE mask
 */

#include "mlib_image.h"
#include "mlib_c_ImageConv.h"

/*
  This define switches between functions of different data types
*/
#define IMG_TYPE 3

/***************************************************************/
#if IMG_TYPE == 1

#define DTYPE             mlib_u8
#define CONV_FUNC(KERN)   mlib_c_conv##KERN##nw_u8
#define CONV_FUNC_I(KERN) mlib_i_conv##KERN##nw_u8
#define DSCALE            (1 << 24)
#define FROM_S32(x)       (((x) >> 24) ^ 128)
#define S64TOS32(x)       (x)
#define SAT_OFF           -(1u << 31)

#elif IMG_TYPE == 2

#define DTYPE             mlib_s16
#define CONV_FUNC(KERN)   mlib_conv##KERN##nw_s16
#define CONV_FUNC_I(KERN) mlib_i_conv##KERN##nw_s16
#define DSCALE            65536.0
#define FROM_S32(x)       ((x) >> 16)
#define S64TOS32(x)       ((x) & 0xffffffff)
#define SAT_OFF

#elif IMG_TYPE == 3

#define DTYPE             mlib_u16
#define CONV_FUNC(KERN)   mlib_conv##KERN##nw_u16
#define CONV_FUNC_I(KERN) mlib_i_conv##KERN##nw_u16
#define DSCALE            65536.0
#define FROM_S32(x)       (((x) >> 16) ^ 0x8000)
#define S64TOS32(x)       (x)
#define SAT_OFF           -(1u << 31)

#endif /* IMG_TYPE == 1 */

/***************************************************************/
#define BUFF_SIZE   1600

#define CACHE_SIZE  (64*1024)

/***************************************************************/
#define FTYPE mlib_d64

#ifndef MLIB_USE_FTOI_CLAMPING

#define CLAMP_S32(x)                                            \
  (((x) <= MLIB_S32_MIN) ? MLIB_S32_MIN : (((x) >= MLIB_S32_MAX) ? MLIB_S32_MAX : (mlib_s32)(x)))

#else

#define CLAMP_S32(x) ((mlib_s32)(x))

#endif /* MLIB_USE_FTOI_CLAMPING */

/***************************************************************/
#define D2I(x) CLAMP_S32((x) SAT_OFF)

/***************************************************************/
#ifdef _LITTLE_ENDIAN

#define STORE2(res0, res1)                                      \
  dp[0    ] = res1;                                             \
  dp[chan1] = res0

#else

#define STORE2(res0, res1)                                      \
  dp[0    ] = res0;                                             \
  dp[chan1] = res1

#endif /* _LITTLE_ENDIAN */

/***************************************************************/
#ifdef _NO_LONGLONG

#define LOAD_BUFF(buff)                                         \
  buff[i    ] = sp[0];                                          \
  buff[i + 1] = sp[chan1]

#else /* _NO_LONGLONG */

#ifdef _LITTLE_ENDIAN

#define LOAD_BUFF(buff)                                         \
  *(mlib_s64*)(buff + i) = (((mlib_s64)sp[chan1]) << 32) | S64TOS32((mlib_s64)sp[0])

#else /* _LITTLE_ENDIAN */

#define LOAD_BUFF(buff)                                         \
  *(mlib_s64*)(buff + i) = (((mlib_s64)sp[0]) << 32) | S64TOS32((mlib_s64)sp[chan1])

#endif /* _LITTLE_ENDIAN */
#endif /* _NO_LONGLONG */

/***************************************************************/
typedef union {
  mlib_d64 d64;
  struct {
    mlib_s32 i0;
    mlib_s32 i1;
  } i32s;
  struct {
    mlib_s32 f0;
    mlib_s32 f1;
  } f32s;
} d64_2x32;

/***************************************************************/
#define BUFF_LINE 256

/***************************************************************/
#define DEF_VARS(type)                                          \
  type     *adr_src, *sl, *sp;                                  \
  type     *adr_dst, *dl, *dp;                                  \
  FTYPE    *pbuff = buff;                                       \
  mlib_s32 wid, hgt, sll, dll;                                  \
  mlib_s32 nchannel, chan1;                                     \
  mlib_s32 i, j, c

/***************************************************************/
#define LOAD_KERNEL3()                                                   \
  FTYPE    scalef = DSCALE;                                              \
  FTYPE    k0, k1, k2, k3, k4, k5, k6, k7, k8;                           \
  FTYPE    p00, p01, p02, p03,                                           \
           p10, p11, p12, p13,                                           \
           p20, p21, p22, p23;                                           \
                                                                         \
  while (scalef_expon > 30) {                                            \
    scalef /= (1 << 30);                                                 \
    scalef_expon -= 30;                                                  \
  }                                                                      \
                                                                         \
  scalef /= (1 << scalef_expon);                                         \
                                                                         \
  /* keep kernel in regs */                                              \
  k0 = scalef * kern[0];  k1 = scalef * kern[1];  k2 = scalef * kern[2]; \
  k3 = scalef * kern[3];  k4 = scalef * kern[4];  k5 = scalef * kern[5]; \
  k6 = scalef * kern[6];  k7 = scalef * kern[7];  k8 = scalef * kern[8]

/***************************************************************/
#define LOAD_KERNEL(SIZE)                                       \
  FTYPE    scalef = DSCALE;                                     \
                                                                \
  while (scalef_expon > 30) {                                   \
    scalef /= (1 << 30);                                        \
    scalef_expon -= 30;                                         \
  }                                                             \
                                                                \
  scalef /= (1 << scalef_expon);                                \
                                                                \
  for (j = 0; j < SIZE; j++) k[j] = scalef * kern[j]

/***************************************************************/
#define GET_SRC_DST_PARAMETERS(type)                            \
  hgt = mlib_ImageGetHeight(src);                               \
  wid = mlib_ImageGetWidth(src);                                \
  nchannel = mlib_ImageGetChannels(src);                        \
  sll = mlib_ImageGetStride(src) / sizeof(type);                \
  dll = mlib_ImageGetStride(dst) / sizeof(type);                \
  adr_src = (type *)mlib_ImageGetData(src);                     \
  adr_dst = (type *)mlib_ImageGetData(dst)

/***************************************************************/
#ifndef __sparc

#if IMG_TYPE == 1

/* Test for the presence of any "1" bit in bits
   8 to 31 of val. If present, then val is either
   negative or >255. If over/underflows of 8 bits
   are uncommon, then this technique can be a win,
   since only a single test, rather than two, is
   necessary to determine if clamping is needed.
   On the other hand, if over/underflows are common,
   it adds an extra test.
*/
#define CLAMP_STORE(dst, val)                                   \
  if (val & 0xffffff00) {                                       \
    if (val < MLIB_U8_MIN)                                      \
      dst = MLIB_U8_MIN;                                        \
    else                                                        \
      dst = MLIB_U8_MAX;                                        \
  } else {                                                      \
    dst = (mlib_u8)val;                                         \
  }

#elif IMG_TYPE == 2

#define CLAMP_STORE(dst, val)                                   \
  if (val >= MLIB_S16_MAX)                                      \
    dst = MLIB_S16_MAX;                                         \
  else if (val <= MLIB_S16_MIN)                                 \
    dst = MLIB_S16_MIN;                                         \
  else                                                          \
    dst = (mlib_s16)val

#elif IMG_TYPE == 3

#define CLAMP_STORE(dst, val)                                   \
  if (val >= MLIB_U16_MAX)                                      \
    dst = MLIB_U16_MAX;                                         \
  else if (val <= MLIB_U16_MIN)                                 \
    dst = MLIB_U16_MIN;                                         \
  else                                                          \
    dst = (mlib_u16)val

#endif /* IMG_TYPE == 1 */
#endif /* __sparc */

/***************************************************************/
#define KSIZE  3

mlib_status CONV_FUNC(3x3)(mlib_image       *dst,
                           const mlib_image *src,
                           const mlib_s32   *kern,
                           mlib_s32         scalef_expon,
                           mlib_s32         cmask)
{
  FTYPE    buff[(KSIZE + 2)*BUFF_LINE], *buff0, *buff1, *buff2, *buff3, *buffT;
  DEF_VARS(DTYPE);
  DTYPE *sl1;
  mlib_s32 chan2;
  mlib_s32 *buffo, *buffi;
  DTYPE *sl2;
#ifndef __sparc
  mlib_s32 d0, d1;
#endif /* __sparc */
  LOAD_KERNEL3();
  GET_SRC_DST_PARAMETERS(DTYPE);

  if (wid > BUFF_LINE) {
    pbuff = mlib_malloc((KSIZE + 2)*sizeof(FTYPE)*wid);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  buff0 = pbuff;
  buff1 = buff0 + wid;
  buff2 = buff1 + wid;
  buff3 = buff2 + wid;
  buffo = (mlib_s32*)(buff3 + wid);
  buffi = buffo + (wid &~ 1);

  chan1 = nchannel;
  chan2 = chan1 + chan1;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  adr_dst += ((KSIZE - 1)/2)*(dll + chan1);

  for (c = 0; c < nchannel; c++) {
    if (!(cmask & (1 << (nchannel - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    sl1 = sl  + sll;
    sl2 = sl1 + sll;
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < wid + (KSIZE - 1); i++) {
      buff0[i] = (FTYPE)sl[i*chan1];
      buff1[i] = (FTYPE)sl1[i*chan1];
      buff2[i] = (FTYPE)sl2[i*chan1];
    }

    sl += KSIZE*sll;

    for (j = 0; j < hgt; j++) {
      FTYPE    s0, s1;

      p02 = buff0[0];
      p12 = buff1[0];
      p22 = buff2[0];

      p03 = buff0[1];
      p13 = buff1[1];
      p23 = buff2[1];

      s0 = p02 * k0 + p03 * k1 + p12 * k3 + p13 * k4 + p22 * k6 + p23 * k7;
      s1 = p03 * k0 + p13 * k3 + p23 * k6;

      sp = sl;
      dp = dl;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - 2); i += 2) {
#ifdef __sparc
#ifdef _NO_LONGLONG
        mlib_s32 o64_1, o64_2;
#else /* _NO_LONGLONG */
        mlib_s64 o64;
#endif /* _NO_LONGLONG */
#endif /* __sparc */
        d64_2x32 dd;

        p02 = buff0[i + 2]; p12 = buff1[i + 2]; p22 = buff2[i + 2];
        p03 = buff0[i + 3]; p13 = buff1[i + 3]; p23 = buff2[i + 3];

        LOAD_BUFF(buffi);

        dd.d64 = *(FTYPE   *)(buffi + i);
        buff3[i    ] = (FTYPE)dd.i32s.i0;
        buff3[i + 1] = (FTYPE)dd.i32s.i1;

#ifndef __sparc
        d0 = D2I(s0 + p02 * k2 + p12 * k5 + p22 * k8);
        d1 = D2I(s1 + p02 * k1 + p03 * k2 + p12 * k4 + p13 * k5 + p22 * k7 + p23 * k8);

        s0 = p02 * k0 + p03 * k1 + p12 * k3 + p13 * k4 + p22 * k6 + p23 * k7;
        s1 = p03 * k0 + p13 * k3 + p23 * k6;

        dp[0    ] = FROM_S32(d0);
        dp[chan1] = FROM_S32(d1);

#else /* __sparc */

        dd.i32s.i0 = D2I(s0 + p02 * k2 + p12 * k5 + p22 * k8);
        dd.i32s.i1 = D2I(s1 + p02 * k1 + p03 * k2 + p12 * k4 + p13 * k5 + p22 * k7 + p23 * k8);
        *(FTYPE   *)(buffo + i) = dd.d64;

        s0 = p02 * k0 + p03 * k1 + p12 * k3 + p13 * k4 + p22 * k6 + p23 * k7;
        s1 = p03 * k0 + p13 * k3 + p23 * k6;

#ifdef _NO_LONGLONG

        o64_1 = buffo[i];
        o64_2 = buffo[i+1];
#if IMG_TYPE != 1
        STORE2(FROM_S32(o64_1), FROM_S32(o64_2));
#else
        STORE2(o64_1 >> 24, o64_2 >> 24);
#endif /* IMG_TYPE != 1 */

#else /* _NO_LONGLONG */

        o64 = *(mlib_s64*)(buffo + i);
#if IMG_TYPE != 1
        STORE2(FROM_S32(o64 >> 32), FROM_S32(o64));
#else
        STORE2(o64 >> 56, o64 >> 24);
#endif /* IMG_TYPE != 1 */
#endif /* _NO_LONGLONG */
#endif /* __sparc */

        sp += chan2;
        dp += chan2;
      }

      for (; i < wid; i++) {
        p00 = buff0[i];     p10 = buff1[i];     p20 = buff2[i];
        p01 = buff0[i + 1]; p11 = buff1[i + 1]; p21 = buff2[i + 1];
        p02 = buff0[i + 2]; p12 = buff1[i + 2]; p22 = buff2[i + 2];

        buffi[i] = (mlib_s32)sp[0];
        buff3[i] = (FTYPE)buffi[i];

#ifndef __sparc

        d0 = D2I(p00 * k0 + p01 * k1 + p02 * k2 + p10 * k3 + p11 * k4 +
                 p12 * k5 + p20 * k6 + p21 * k7 + p22 * k8);

        dp[0] = FROM_S32(d0);

#else  /* __sparc */

        buffo[i] = D2I(p00 * k0 + p01 * k1 + p02 * k2 + p10 * k3 + p11 * k4 +
                       p12 * k5 + p20 * k6 + p21 * k7 + p22 * k8);
#if IMG_TYPE != 1
        dp[0] = FROM_S32(buffo[i]);
#else
        dp[0] = buffo[i] >> 24;
#endif /* IMG_TYPE != 1 */
#endif /* __sparc */

        sp += chan1;
        dp += chan1;
      }

      buffi[wid] = (mlib_s32)sp[0];
      buff3[wid] = (FTYPE)buffi[wid];
      buffi[wid + 1] = (mlib_s32)sp[chan1];
      buff3[wid + 1] = (FTYPE)buffi[wid + 1];

      sl += sll;
      dl += dll;

      buffT = buff0;
      buff0 = buff1;
      buff1 = buff2;
      buff2 = buff3;
      buff3 = buffT;
    }
  }

#ifdef __sparc
#if IMG_TYPE == 1
  {
    mlib_s32 amask = (1 << nchannel) - 1;

    if ((cmask & amask) != amask) {
      mlib_ImageXor80(adr_dst, wid, hgt, dll, nchannel, cmask);
    } else {
      mlib_ImageXor80_aa(adr_dst, wid*nchannel, hgt, dll);
    }
  }

#endif /* IMG_TYPE == 1 */
#endif /* __sparc */

  if (pbuff != buff) mlib_free(pbuff);

  return MLIB_SUCCESS;
}

/***************************************************************/
#ifndef __sparc /* for x86, using integer multiplies is faster */

mlib_status CONV_FUNC_I(3x3)(mlib_image       *dst,
                             const mlib_image *src,
                             const mlib_s32   *kern,
                             mlib_s32         scalef_expon,
                             mlib_s32         cmask)
{
  DTYPE    *adr_src, *sl, *sp0, *sp1, *sp2;
  DTYPE    *adr_dst, *dl, *dp;
  mlib_s32 wid, hgt, sll, dll;
  mlib_s32 nchannel, chan1, chan2;
  mlib_s32 i, j, c;
  mlib_s32 shift1, shift2;
  mlib_s32 k0, k1, k2, k3, k4, k5, k6, k7, k8;
  mlib_s32 p02, p03,
           p12, p13,
           p22, p23;

#if IMG_TYPE != 1
  shift1 = 16;
#else
  shift1 = 8;
#endif /* IMG_TYPE != 1 */

  shift2 = scalef_expon - shift1;

  /* keep kernel in regs */
  k0 = kern[0] >> shift1;  k1 = kern[1] >> shift1;  k2 = kern[2] >> shift1;
  k3 = kern[3] >> shift1;  k4 = kern[4] >> shift1;  k5 = kern[5] >> shift1;
  k6 = kern[6] >> shift1;  k7 = kern[7] >> shift1;  k8 = kern[8] >> shift1;

  GET_SRC_DST_PARAMETERS(DTYPE);

  chan1 = nchannel;
  chan2 = chan1 + chan1;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  adr_dst += ((KSIZE - 1)/2)*(dll + chan1);

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    for (j = 0; j < hgt; j++) {
      mlib_s32 s0, s1;
      mlib_s32 pix0, pix1;

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

        pix0 = (s0 + p02 * k2 + p12 * k5 + p22 * k8) >> shift2;
        pix1 = (s1 + p02 * k1 + p03 * k2 + p12 * k4 +
                p13 * k5 + p22 * k7 + p23 * k8) >> shift2;

        CLAMP_STORE(dp[0],     pix0);
        CLAMP_STORE(dp[chan1], pix1);

        s0 = p02 * k0 + p03 * k1 + p12 * k3 + p13 * k4 + p22 * k6 + p23 * k7;
        s1 = p03 * k0 + p13 * k3 + p23 * k6;

        sp0 += chan2;
        sp1 += chan2;
        sp2 += chan2;
        dp += chan2;
      }

      if (wid & 1) {
        p02 = sp0[0]; p12 = sp1[0]; p22 = sp2[0];
        pix0 = (s0 + p02 * k2 + p12 * k5 + p22 * k8) >> shift2;
        CLAMP_STORE(dp[0], pix0);
      }

      sl += sll;
      dl += dll;
    }
  }

  return MLIB_SUCCESS;
}

#endif /* __sparc ( for x86, using integer multiplies is faster ) */

/***************************************************************/
#undef  KSIZE
#define KSIZE 4

mlib_status CONV_FUNC(4x4)(mlib_image       *dst,
                           const mlib_image *src,
                           const mlib_s32   *kern,
                           mlib_s32         scalef_expon,
                           mlib_s32         cmask)
{
  FTYPE    buff[(KSIZE + 3)*BUFF_LINE];
  FTYPE    *buff0, *buff1, *buff2, *buff3, *buff4, *buffd, *buffT;
  FTYPE    k[KSIZE*KSIZE];
  mlib_s32 d0, d1;
  FTYPE    k0, k1, k2, k3, k4, k5, k6, k7;
  FTYPE    p00, p01, p02, p03, p04,
           p10, p11, p12, p13, p14,
           p20, p21, p22, p23,
           p30, p31, p32, p33;
  DEF_VARS(DTYPE);
  DTYPE *sl1;
  mlib_s32 chan2;
  mlib_s32 *buffo, *buffi;
  DTYPE *sl2, *sl3;
  LOAD_KERNEL(KSIZE*KSIZE);
  GET_SRC_DST_PARAMETERS(DTYPE);

  if (wid > BUFF_LINE) {
    pbuff = mlib_malloc((KSIZE + 3)*sizeof(FTYPE)*wid);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  buff0 = pbuff;
  buff1 = buff0 + wid;
  buff2 = buff1 + wid;
  buff3 = buff2 + wid;
  buff4 = buff3 + wid;
  buffd = buff4 + wid;
  buffo = (mlib_s32*)(buffd + wid);
  buffi = buffo + (wid &~ 1);

  chan1 = nchannel;
  chan2 = chan1 + chan1;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  adr_dst += ((KSIZE - 1)/2)*(dll + chan1);

  for (c = 0; c < nchannel; c++) {
    if (!(cmask & (1 << (nchannel - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    sl1 = sl  + sll;
    sl2 = sl1 + sll;
    sl3 = sl2 + sll;
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < wid + (KSIZE - 1); i++) {
      buff0[i] = (FTYPE)sl[i*chan1];
      buff1[i] = (FTYPE)sl1[i*chan1];
      buff2[i] = (FTYPE)sl2[i*chan1];
      buff3[i] = (FTYPE)sl3[i*chan1];
    }

    sl += KSIZE*sll;

    for (j = 0; j < hgt; j++) {
      d64_2x32 dd;

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

        LOAD_BUFF(buffi);

        dd.d64 = *(FTYPE   *)(buffi + i);
        buff4[i    ] = (FTYPE)dd.i32s.i0;
        buff4[i + 1] = (FTYPE)dd.i32s.i1;

        buffd[i    ] = (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 +
                        p10 * k4 + p11 * k5 + p12 * k6 + p13 * k7);
        buffd[i + 1] = (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 +
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

        d0 = D2I(p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 +
                 p10 * k4 + p11 * k5 + p12 * k6 + p13 * k7 + buffd[i]);
        d1 = D2I(p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 +
                 p11 * k4 + p12 * k5 + p13 * k6 + p14 * k7 + buffd[i + 1]);

        dp[0    ] = FROM_S32(d0);
        dp[chan1] = FROM_S32(d1);

        sp += chan2;
        dp += chan2;
      }

      /* last pixels */
      for (; i < wid; i++) {
        p00 = buff0[i];     p10 = buff1[i];     p20 = buff2[i];     p30 = buff3[i];
        p01 = buff0[i + 1]; p11 = buff1[i + 1]; p21 = buff2[i + 1]; p31 = buff3[i + 1];
        p02 = buff0[i + 2]; p12 = buff1[i + 2]; p22 = buff2[i + 2]; p32 = buff3[i + 2];
        p03 = buff0[i + 3]; p13 = buff1[i + 3]; p23 = buff2[i + 3]; p33 = buff3[i + 3];

        buff4[i] = (FTYPE)sp[0];

        buffo[i] = D2I(p00 * k[0] + p01 * k[1] + p02 * k[2] + p03 * k[3] +
                       p10 * k[4] + p11 * k[5] + p12 * k[6] + p13 * k[7] +
                       p20 * k[ 8] + p21 * k[ 9] + p22 * k[10] + p23 * k[11] +
                       p30 * k[12] + p31 * k[13] + p32 * k[14] + p33 * k[15]);

        dp[0] = FROM_S32(buffo[i]);

        sp += chan1;
        dp += chan1;
      }

      buff4[wid    ] = (FTYPE)sp[0];
      buff4[wid + 1] = (FTYPE)sp[chan1];
      buff4[wid + 2] = (FTYPE)sp[chan2];

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
  FTYPE    buff[(KSIZE + 3)*BUFF_LINE];
  FTYPE    *buff0, *buff1, *buff2, *buff3, *buff4, *buff5, *buffd, *buffT;
  FTYPE    k[KSIZE*KSIZE];
  mlib_s32 d0, d1;
  FTYPE    k0, k1, k2, k3, k4, k5, k6, k7, k8, k9;
  FTYPE    p00, p01, p02, p03, p04, p05,
           p10, p11, p12, p13, p14, p15,
           p20, p21, p22, p23, p24,
           p30, p31, p32, p33, p34,
           p40, p41, p42, p43, p44;
  DEF_VARS(DTYPE);
  DTYPE *sl1;
  mlib_s32 chan2;
  mlib_s32 *buffo, *buffi;
  DTYPE *sl2, *sl3, *sl4;
  LOAD_KERNEL(KSIZE*KSIZE);
  GET_SRC_DST_PARAMETERS(DTYPE);

  if (wid > BUFF_LINE) {
    pbuff = mlib_malloc((KSIZE + 3)*sizeof(FTYPE)*wid);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  buff0 = pbuff;
  buff1 = buff0 + wid;
  buff2 = buff1 + wid;
  buff3 = buff2 + wid;
  buff4 = buff3 + wid;
  buff5 = buff4 + wid;
  buffd = buff5 + wid;
  buffo = (mlib_s32*)(buffd + wid);
  buffi = buffo + (wid &~ 1);

  chan1 = nchannel;
  chan2 = chan1 + chan1;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  adr_dst += ((KSIZE - 1)/2)*(dll + chan1);

  for (c = 0; c < nchannel; c++) {
    if (!(cmask & (1 << (nchannel - 1 - c)))) continue;

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
      buff0[i] = (FTYPE)sl[i*chan1];
      buff1[i] = (FTYPE)sl1[i*chan1];
      buff2[i] = (FTYPE)sl2[i*chan1];
      buff3[i] = (FTYPE)sl3[i*chan1];
      buff4[i] = (FTYPE)sl4[i*chan1];
    }

    sl += KSIZE*sll;

    for (j = 0; j < hgt; j++) {
      d64_2x32 dd;

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

        LOAD_BUFF(buffi);

        p03 = buff0[i + 3]; p13 = buff1[i + 3];
        p04 = buff0[i + 4]; p14 = buff1[i + 4];
        p05 = buff0[i + 5]; p15 = buff1[i + 5];

        buffd[i    ] = (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                        p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
        buffd[i + 1] = (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 + p05 * k4 +
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
      p04 = buff2[2];
      p14 = buff3[2];

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

        dd.d64 = *(FTYPE   *)(buffi + i);
        buff5[i    ] = (FTYPE)dd.i32s.i0;
        buff5[i + 1] = (FTYPE)dd.i32s.i1;

        buffd[i    ] += (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                         p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
        buffd[i + 1] += (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 + p05 * k4 +
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

        d0 = D2I(p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 + buffd[i]);
        d1 = D2I(p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 + p05 * k4 + buffd[i + 1]);

        dp[0    ] = FROM_S32(d0);
        dp[chan1] = FROM_S32(d1);

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

        p40 = buff4[i];     p41 = buff4[i + 1]; p42 = buff4[i + 2];
        p43 = buff4[i + 3]; p44 = buff4[i + 4];

        buff5[i] = (FTYPE)sp[0];

        buffo[i] = D2I(p00 * k[0] + p01 * k[1] + p02 * k[2] + p03 * k[3] + p04 * k[4] +
                       p10 * k[5] + p11 * k[6] + p12 * k[7] + p13 * k[8] + p14 * k[9] +
                       p20 * k[10] + p21 * k[11] + p22 * k[12] + p23 * k[13] + p24 * k[14] +
                       p30 * k[15] + p31 * k[16] + p32 * k[17] + p33 * k[18] + p34 * k[19] +
                       p40 * k[20] + p41 * k[21] + p42 * k[22] + p43 * k[23] + p44 * k[24]);

        dp[0] = FROM_S32(buffo[i]);

        sp += chan1;
        dp += chan1;
      }

      buff5[wid    ] = (FTYPE)sp[0];
      buff5[wid + 1] = (FTYPE)sp[chan1];
      buff5[wid + 2] = (FTYPE)sp[chan2];
      buff5[wid + 3] = (FTYPE)sp[chan2 + chan1];

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
#ifndef __sparc /* for x86, using integer multiplies is faster */

mlib_status CONV_FUNC_I(5x5)(mlib_image       *dst,
                             const mlib_image *src,
                             const mlib_s32   *kern,
                             mlib_s32         scalef_expon,
                             mlib_s32         cmask)
{
  mlib_s32 buff[BUFF_LINE];
  mlib_s32 *buffd;
  mlib_s32 k[KSIZE*KSIZE];
  mlib_s32 shift1, shift2;
  mlib_s32 k0, k1, k2, k3, k4, k5, k6, k7, k8, k9;
  mlib_s32 p00, p01, p02, p03, p04, p05,
           p10, p11, p12, p13, p14, p15;
  DTYPE    *adr_src, *sl, *sp0, *sp1;
  DTYPE    *adr_dst, *dl, *dp;
  mlib_s32 *pbuff = buff;
  mlib_s32 wid, hgt, sll, dll;
  mlib_s32 nchannel, chan1, chan2, chan3, chan4;
  mlib_s32 i, j, c;

#if IMG_TYPE != 1
  shift1 = 16;
#else
  shift1 = 8;
#endif /* IMG_TYPE != 1 */

  shift2 = scalef_expon - shift1;

  for (j = 0; j < KSIZE*KSIZE; j++) k[j] = kern[j] >> shift1;

  GET_SRC_DST_PARAMETERS(DTYPE);

  if (wid > BUFF_LINE) {
    pbuff = mlib_malloc(sizeof(mlib_s32)*wid);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  buffd = pbuff;

  chan1 = nchannel;
  chan2 = chan1 + chan1;
  chan3 = chan2 + chan1;
  chan4 = chan3 + chan1;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  adr_dst += ((KSIZE - 1)/2)*(dll + chan1);

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    for (j = 0; j < hgt; j++) {
      mlib_s32 pix0, pix1;
      /*
       *  First loop
       */
      sp0 = sl;
      sp1 = sp0 + sll;
      dp = dl;

      k0 = k[0]; k1 = k[1]; k2 = k[2]; k3 = k[3]; k4 = k[4];
      k5 = k[5]; k6 = k[6]; k7 = k[7]; k8 = k[8]; k9 = k[9];

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

        buffd[i    ] = (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                        p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
        buffd[i + 1] = (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 + p05 * k4 +
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

        buffd[i] = (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                    p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
      }

      /*
       *  Second loop
       */
      sp0 = sl + 2*sll;
      sp1 = sp0 + sll;
      dp = dl;

      k0 = k[10]; k1 = k[11]; k2 = k[12]; k3 = k[13]; k4 = k[14];
      k5 = k[15]; k6 = k[16]; k7 = k[17]; k8 = k[18]; k9 = k[19];

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

        buffd[i    ] += (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                         p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
        buffd[i + 1] += (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 + p05 * k4 +
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

        buffd[i] += (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                     p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
      }

      /*
       *  3 loop
       */
      dp = dl;
      sp0 = sl + 4*sll;

      k0 = k[20]; k1 = k[21]; k2 = k[22]; k3 = k[23]; k4 = k[24];

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

        pix0 = (buffd[i    ] + p00 * k0 + p01 * k1 + p02 * k2 +
                p03 * k3 + p04 * k4) >> shift2;
        pix1 = (buffd[i + 1] + p01 * k0 + p02 * k1 + p03 * k2 +
                p04 * k3 + p05 * k4) >> shift2;

        CLAMP_STORE(dp[0],     pix0);
        CLAMP_STORE(dp[chan1], pix1);

        dp  += chan2;
        sp0 += chan2;
      }

      if (wid & 1) {
        p00 = p02; p01 = p03; p02 = p04; p03 = p05;

        p04 = sp0[0];

        pix0 = (buffd[i    ] + p00 * k0 + p01 * k1 + p02 * k2 +
                p03 * k3 + p04 * k4) >> shift2;
        CLAMP_STORE(dp[0],     pix0);
      }

      /* next line */
      sl += sll;
      dl += dll;
    }
  }

  if (pbuff != buff) mlib_free(pbuff);

  return MLIB_SUCCESS;
}

#endif /* __sparc ( for x86, using integer multiplies is faster ) */

/***************************************************************/
#if IMG_TYPE == 1

#undef  KSIZE
#define KSIZE 7

mlib_status CONV_FUNC(7x7)(mlib_image       *dst,
                           const mlib_image *src,
                           const mlib_s32   *kern,
                           mlib_s32         scalef_expon,
                           mlib_s32         cmask)
{
  FTYPE    buff[(KSIZE + 3)*BUFF_LINE], *buffs[2*(KSIZE + 1)], *buffd;
  FTYPE    k[KSIZE*KSIZE];
  mlib_s32 l, m, buff_ind;
  mlib_s32 d0, d1;
  FTYPE    k0, k1, k2, k3, k4, k5, k6;
  FTYPE    p0, p1, p2, p3, p4, p5, p6, p7;
  DTYPE *sl2, *sl3, *sl4, *sl5, *sl6;
  DEF_VARS(DTYPE);
  DTYPE *sl1;
  mlib_s32 chan2;
  mlib_s32 *buffo, *buffi;
  LOAD_KERNEL(KSIZE*KSIZE);
  GET_SRC_DST_PARAMETERS(DTYPE);

  if (wid > BUFF_LINE) {
    pbuff = mlib_malloc((KSIZE + 3)*sizeof(FTYPE)*wid);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  for (l = 0; l < KSIZE + 1; l++) buffs[l] = pbuff + l*wid;
  for (l = 0; l < KSIZE + 1; l++) buffs[l + (KSIZE + 1)] = buffs[l];
  buffd = buffs[KSIZE] + wid;
  buffo = (mlib_s32*)(buffd + wid);
  buffi = buffo + (wid &~ 1);

  chan1 = nchannel;
  chan2 = chan1 + chan1;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);

  adr_dst += ((KSIZE - 1)/2)*(dll + chan1);

  for (c = 0; c < nchannel; c++) {
    if (!(cmask & (1 << (nchannel - 1 - c)))) continue;

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
      buffs[0][i] = (FTYPE)sl[i*chan1];
      buffs[1][i] = (FTYPE)sl1[i*chan1];
      buffs[2][i] = (FTYPE)sl2[i*chan1];
      buffs[3][i] = (FTYPE)sl3[i*chan1];
      buffs[4][i] = (FTYPE)sl4[i*chan1];
      buffs[5][i] = (FTYPE)sl5[i*chan1];
      buffs[6][i] = (FTYPE)sl6[i*chan1];
    }

    buff_ind = 0;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < wid; i++) buffd[i] = 0.0;

    sl += KSIZE*sll;

    for (j = 0; j < hgt; j++) {
      FTYPE    **buffc = buffs + buff_ind;
      FTYPE    *buffn = buffc[KSIZE];
      FTYPE    *pk = k;

      for (l = 0; l < KSIZE; l++) {
        FTYPE    *buff = buffc[l];
        d64_2x32 dd;

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

            LOAD_BUFF(buffi);

            dd.d64 = *(FTYPE   *)(buffi + i);
            buffn[i    ] = (FTYPE)dd.i32s.i0;
            buffn[i + 1] = (FTYPE)dd.i32s.i1;

            d0 = D2I(p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5 + p6*k6 + buffd[i    ]);
            d1 = D2I(p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5 + p7*k6 + buffd[i + 1]);

            dp[0    ] = FROM_S32(d0);
            dp[chan1] = FROM_S32(d1);

            buffd[i    ] = 0.0;
            buffd[i + 1] = 0.0;

            sp += chan2;
            dp += chan2;
          }
        }
      }

      /* last pixels */
      for (; i < wid; i++) {
        FTYPE    *pk = k, s = 0;
        mlib_s32 d0;

        for (l = 0; l < KSIZE; l++) {
          FTYPE    *buff = buffc[l] + i;

          for (m = 0; m < KSIZE; m++) s += buff[m] * (*pk++);
        }

        d0 = D2I(s);
        dp[0] = FROM_S32(d0);

        buffn[i] = (FTYPE)sp[0];

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

#endif /* IMG_TYPE == 1 */

/***************************************************************/
#define MAX_KER   7
#define MAX_N    15

static mlib_status mlib_ImageConv1xN(mlib_image       *dst,
                                     const mlib_image *src,
                                     const mlib_d64   *k,
                                     mlib_s32         n,
                                     mlib_s32         dn,
                                     mlib_s32         cmask)
{
  FTYPE    buff[BUFF_SIZE];
  mlib_s32 off, kh;
  mlib_s32 d0, d1;
  const FTYPE    *pk;
  FTYPE    k0, k1, k2, k3;
  FTYPE    p0, p1, p2, p3, p4;
  DEF_VARS(DTYPE);
  DTYPE    *sl_c, *dl_c, *sl0;
  mlib_s32 l, hsize, max_hsize;
  GET_SRC_DST_PARAMETERS(DTYPE);

  hgt -= (n - 1);
  adr_dst += dn*dll;

  max_hsize = (CACHE_SIZE/sizeof(DTYPE))/sll;

  if (!max_hsize) max_hsize = 1;

  if (max_hsize > BUFF_SIZE) {
    pbuff = mlib_malloc(sizeof(FTYPE)*max_hsize);
  }

  chan1 = nchannel;

  sl_c = adr_src;
  dl_c = adr_dst;

  for (l = 0; l < hgt; l += hsize) {
    hsize = hgt - l;

    if (hsize > max_hsize) hsize = max_hsize;

    for (c = 0; c < nchannel; c++) {
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

            d0 = D2I(p0*k0 + p1*k1 + p2*k2 + p3*k3 + pbuff[j]);
            d1 = D2I(p1*k0 + p2*k1 + p3*k2 + p4*k3 + pbuff[j + 1]);

            dp[0  ] = FROM_S32(d0);
            dp[dll] = FROM_S32(d1);

            pbuff[j] = 0;
            pbuff[j + 1] = 0;

            sp += 2*sll;
            dp += 2*dll;
          }

          if (j < hsize) {
            p0 = p2; p1 = p3; p2 = p4;
            p3 = sp[0];

            d0 = D2I(p0*k0 + p1*k1 + p2*k2 + p3*k3 + pbuff[j]);

            pbuff[j] = 0;

            dp[0] = FROM_S32(d0);
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

            d0 = D2I(p0*k0 + p1*k1 + p2*k2 + pbuff[j]);
            d1 = D2I(p1*k0 + p2*k1 + p3*k2 + pbuff[j + 1]);

            dp[0  ] = FROM_S32(d0);
            dp[dll] = FROM_S32(d1);

            pbuff[j] = 0;
            pbuff[j + 1] = 0;

            sp += 2*sll;
            dp += 2*dll;
          }

          if (j < hsize) {
            p0 = p2; p1 = p3;
            p2 = sp[0];

            d0 = D2I(p0*k0 + p1*k1 + p2*k2 + pbuff[j]);

            pbuff[j] = 0;

            dp[0] = FROM_S32(d0);
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

            d0 = D2I(p0*k0 + p1*k1 + pbuff[j]);
            d1 = D2I(p1*k0 + p2*k1 + pbuff[j + 1]);

            dp[0  ] = FROM_S32(d0);
            dp[dll] = FROM_S32(d1);

            pbuff[j] = 0;
            pbuff[j + 1] = 0;

            sp += 2*sll;
            dp += 2*dll;
          }

          if (j < hsize) {
            p0 = p2;
            p1 = sp[0];

            d0 = D2I(p0*k0 + p1*k1 + pbuff[j]);

            pbuff[j] = 0;

            dp[0] = FROM_S32(d0);
          }

        } else /* if (kh == 1) */ {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
          for (j = 0; j < hsize; j++) {
            p0 = sp[0];

            d0 = D2I(p0*k0 + pbuff[j]);

            dp[0] = FROM_S32(d0);

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
  FTYPE    buff[BUFF_SIZE], *buffs_arr[2*(MAX_N + 1)];
  FTYPE    **buffs = buffs_arr, *buffd;
  FTYPE    akernel[256], *k = akernel, fscale = DSCALE;
  mlib_s32 mn, l, off, kw, bsize, buff_ind;
  mlib_s32 d0, d1;
  FTYPE    k0, k1, k2, k3, k4, k5, k6;
  FTYPE    p0, p1, p2, p3, p4, p5, p6, p7;
  d64_2x32 dd;
  DEF_VARS(DTYPE);
  mlib_s32 chan2;
  mlib_s32 *buffo, *buffi;
  GET_SRC_DST_PARAMETERS(DTYPE);

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

  if (m == 1) return mlib_ImageConv1xN(dst, src, k, n, dn, cmask);

  bsize = (n + 3)*wid;

  if ((bsize > BUFF_SIZE) || (n > MAX_N)) {
    pbuff = mlib_malloc(sizeof(FTYPE)*bsize + sizeof(FTYPE *)*2*(n + 1));

    if (pbuff == NULL) return MLIB_FAILURE;
    buffs = (FTYPE   **)(pbuff + bsize);
  }

  for (l = 0; l < (n + 1); l++) buffs[l] = pbuff + l*wid;
  for (l = 0; l < (n + 1); l++) buffs[l + (n + 1)] = buffs[l];
  buffd = buffs[n] + wid;
  buffo = (mlib_s32*)(buffd + wid);
  buffi = buffo + (wid &~ 1);

  chan1 = nchannel;
  chan2 = chan1 + chan1;

  wid -= (m - 1);
  hgt -= (n - 1);
  adr_dst += dn*dll + dm*nchannel;

  for (c = 0; c < nchannel; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    for (l = 0; l < n; l++) {
      FTYPE    *buff = buffs[l];

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i < wid + (m - 1); i++) {
        buff[i] = (FTYPE)sl[i*chan1];
      }

      sl += sll;
    }

    buff_ind = 0;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < wid; i++) buffd[i] = 0.0;

    for (j = 0; j < hgt; j++) {
      FTYPE    **buffc = buffs + buff_ind;
      FTYPE    *buffn = buffc[n];
      FTYPE    *pk = k;

      for (l = 0; l < n; l++) {
        FTYPE    *buff_l = buffc[l];

        for (off = 0; off < m;) {
          FTYPE    *buff = buff_l + off;

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

                LOAD_BUFF(buffi);

                dd.d64 = *(FTYPE   *)(buffi + i);
                buffn[i    ] = (FTYPE)dd.i32s.i0;
                buffn[i + 1] = (FTYPE)dd.i32s.i1;

                d0 = D2I(p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5 + p6*k6 + buffd[i    ]);
                d1 = D2I(p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5 + p7*k6 + buffd[i + 1]);

                dp[0    ] = FROM_S32(d0);
                dp[chan1] = FROM_S32(d1);

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

                buffn[i    ] = (FTYPE)sp[0];
                buffn[i + 1] = (FTYPE)sp[chan1];

                d0 = D2I(p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5 + buffd[i    ]);
                d1 = D2I(p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5 + buffd[i + 1]);

                dp[0    ] = FROM_S32(d0);
                dp[chan1] = FROM_S32(d1);

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

                buffn[i    ] = (FTYPE)sp[0];
                buffn[i + 1] = (FTYPE)sp[chan1];

                d0 = D2I(p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + buffd[i    ]);
                d1 = D2I(p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + buffd[i + 1]);

                dp[0    ] = FROM_S32(d0);
                dp[chan1] = FROM_S32(d1);

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

                buffn[i    ] = (FTYPE)sp[0];
                buffn[i + 1] = (FTYPE)sp[chan1];

                d0 = D2I(p0*k0 + p1*k1 + p2*k2 + p3*k3 + buffd[i    ]);
                d1 = D2I(p1*k0 + p2*k1 + p3*k2 + p4*k3 + buffd[i + 1]);

                dp[0    ] = FROM_S32(d0);
                dp[chan1] = FROM_S32(d1);

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

                buffn[i    ] = (FTYPE)sp[0];
                buffn[i + 1] = (FTYPE)sp[chan1];

                d0 = D2I(p0*k0 + p1*k1 + p2*k2 + buffd[i    ]);
                d1 = D2I(p1*k0 + p2*k1 + p3*k2 + buffd[i + 1]);

                dp[0    ] = FROM_S32(d0);
                dp[chan1] = FROM_S32(d1);

                buffd[i    ] = 0.0;
                buffd[i + 1] = 0.0;

                sp += chan2;
                dp += chan2;
              }
            }

          } else /*if (kw == 2)*/ {

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

                buffn[i    ] = (FTYPE)sp[0];
                buffn[i + 1] = (FTYPE)sp[chan1];

                d0 = D2I(p0*k0 + p1*k1 + buffd[i    ]);
                d1 = D2I(p1*k0 + p2*k1 + buffd[i + 1]);

                dp[0    ] = FROM_S32(d0);
                dp[chan1] = FROM_S32(d1);

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
        FTYPE    *pk = k, s = 0;
        mlib_s32 x, d0;

        for (l = 0; l < n; l++) {
          FTYPE    *buff = buffc[l] + i;

          for (x = 0; x < m; x++) s += buff[x] * (*pk++);
        }

        d0 = D2I(s);
        dp[0] = FROM_S32(d0);

        buffn[i] = (FTYPE)sp[0];

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

  if (pbuff != buff) mlib_free(pbuff);

  return MLIB_SUCCESS;
}

/***************************************************************/
#ifndef __sparc /* for x86, using integer multiplies is faster */

#define STORE_RES(res, x)                                       \
  x >>= shift2;                                                 \
  CLAMP_STORE(res, x)

mlib_status CONV_FUNC_I(MxN)(mlib_image       *dst,
                             const mlib_image *src,
                             const mlib_s32   *kernel,
                             mlib_s32         m,
                             mlib_s32         n,
                             mlib_s32         dm,
                             mlib_s32         dn,
                             mlib_s32         scale,
                             mlib_s32         cmask)
{
  mlib_s32 buff[BUFF_SIZE], *buffd = buff;
  mlib_s32 l, off, kw;
  mlib_s32 d0, d1, shift1, shift2;
  mlib_s32 k0, k1, k2, k3, k4, k5, k6;
  mlib_s32 p0, p1, p2, p3, p4, p5, p6, p7;
  DTYPE    *adr_src, *sl, *sp;
  DTYPE    *adr_dst, *dl, *dp;
  mlib_s32 wid, hgt, sll, dll;
  mlib_s32 nchannel, chan1;
  mlib_s32 i, j, c;
  mlib_s32 chan2;
  mlib_s32 k_locl[MAX_N*MAX_N], *k = k_locl;
  GET_SRC_DST_PARAMETERS(DTYPE);

#if IMG_TYPE != 1
  shift1 = 16;
#else
  shift1 = 8;
#endif /* IMG_TYPE != 1 */
  shift2 = scale - shift1;

  chan1 = nchannel;
  chan2 = chan1 + chan1;

  wid -= (m - 1);
  hgt -= (n - 1);
  adr_dst += dn*dll + dm*nchannel;

  if (wid > BUFF_SIZE) {
    buffd = mlib_malloc(sizeof(mlib_s32)*wid);

    if (buffd == NULL) return MLIB_FAILURE;
  }

  if (m*n > MAX_N*MAX_N) {
    k = mlib_malloc(sizeof(mlib_s32)*(m*n));

    if (k == NULL) {
      if (buffd != buff) mlib_free(buffd);
      return MLIB_FAILURE;
    }
  }

  for (i = 0; i < m*n; i++) {
    k[i] = kernel[i] >> shift1;
  }

  for (c = 0; c < nchannel; c++) {
    if (!(cmask & (1 << (nchannel - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < wid; i++) buffd[i] = 0;

    for (j = 0; j < hgt; j++) {
      mlib_s32 *pk = k;

      for (l = 0; l < n; l++) {
        DTYPE *sp0 = sl + l*sll;

        for (off = 0; off < m;) {
          sp = sp0 + off*chan1;
          dp = dl;

          kw = m - off;

          if (kw > 2*MAX_KER) kw = MAX_KER; else
            if (kw > MAX_KER) kw = kw/2;
          off += kw;

          p2 = sp[0]; p3 = sp[chan1]; p4 = sp[chan2];
          p5 = sp[chan2 + chan1]; p6 = sp[chan2 + chan2]; p7 = sp[5*chan1];

          k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];
          k4 = pk[4]; k5 = pk[5]; k6 = pk[6];
          pk += kw;

          sp += (kw - 1)*chan1;

          if (kw == 7) {

            if (l < (n - 1) || off < m) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5; p4 = p6; p5 = p7;
                p6 = sp[0];
                p7 = sp[chan1];

                buffd[i    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5 + p6*k6;
                buffd[i + 1] += p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5 + p7*k6;

                sp += chan2;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5; p4 = p6; p5 = p7;
                p6 = sp[0];
                p7 = sp[chan1];

                d0 = (p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5 + p6*k6 + buffd[i    ]);
                d1 = (p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5 + p7*k6 + buffd[i + 1]);

                STORE_RES(dp[0    ], d0);
                STORE_RES(dp[chan1], d1);

                buffd[i    ] = 0;
                buffd[i + 1] = 0;

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
                p5 = sp[0];
                p6 = sp[chan1];

                buffd[i    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5;
                buffd[i + 1] += p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5;

                sp += chan2;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5; p4 = p6;
                p5 = sp[0];
                p6 = sp[chan1];

                d0 = (p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + p5*k5 + buffd[i    ]);
                d1 = (p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + p6*k5 + buffd[i + 1]);

                STORE_RES(dp[0    ], d0);
                STORE_RES(dp[chan1], d1);

                buffd[i    ] = 0;
                buffd[i + 1] = 0;

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
                p4 = sp[0];
                p5 = sp[chan1];

                buffd[i    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4;
                buffd[i + 1] += p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4;

                sp += chan2;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4; p3 = p5;
                p4 = sp[0];
                p5 = sp[chan1];

                d0 = (p0*k0 + p1*k1 + p2*k2 + p3*k3 + p4*k4 + buffd[i    ]);
                d1 = (p1*k0 + p2*k1 + p3*k2 + p4*k3 + p5*k4 + buffd[i + 1]);

                STORE_RES(dp[0    ], d0);
                STORE_RES(dp[chan1], d1);

                buffd[i    ] = 0;
                buffd[i + 1] = 0;

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
                p3 = sp[0];
                p4 = sp[chan1];

                buffd[i    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3;
                buffd[i + 1] += p1*k0 + p2*k1 + p3*k2 + p4*k3;

                sp += chan2;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3; p2 = p4;
                p3 = sp[0];
                p4 = sp[chan1];

                d0 = (p0*k0 + p1*k1 + p2*k2 + p3*k3 + buffd[i    ]);
                d1 = (p1*k0 + p2*k1 + p3*k2 + p4*k3 + buffd[i + 1]);

                STORE_RES(dp[0    ], d0);
                STORE_RES(dp[chan1], d1);

                buffd[i    ] = 0;
                buffd[i + 1] = 0;

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
                p2 = sp[0];
                p3 = sp[chan1];

                buffd[i    ] += p0*k0 + p1*k1 + p2*k2;
                buffd[i + 1] += p1*k0 + p2*k1 + p3*k2;

                sp += chan2;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2; p1 = p3;
                p2 = sp[0];
                p3 = sp[chan1];

                d0 = (p0*k0 + p1*k1 + p2*k2 + buffd[i    ]);
                d1 = (p1*k0 + p2*k1 + p3*k2 + buffd[i + 1]);

                STORE_RES(dp[0    ], d0);
                STORE_RES(dp[chan1], d1);

                buffd[i    ] = 0;
                buffd[i + 1] = 0;

                sp += chan2;
                dp += chan2;
              }
            }

          } else if (kw == 2) {

            if (l < (n - 1) || off < m) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2;
                p1 = sp[0];
                p2 = sp[chan1];

                buffd[i    ] += p0*k0 + p1*k1;
                buffd[i + 1] += p1*k0 + p2*k1;

                sp += chan2;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = p2;
                p1 = sp[0];
                p2 = sp[chan1];

                d0 = (p0*k0 + p1*k1 + buffd[i    ]);
                d1 = (p1*k0 + p2*k1 + buffd[i + 1]);

                STORE_RES(dp[0    ], d0);
                STORE_RES(dp[chan1], d1);

                buffd[i    ] = 0;
                buffd[i + 1] = 0;

                sp += chan2;
                dp += chan2;
              }
            }

          } else /*if (kw == 1)*/ {

            if (l < (n - 1) || off < m) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = sp[0];
                p1 = sp[chan1];

                buffd[i    ] += p0*k0;
                buffd[i + 1] += p1*k0;

                sp += chan2;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = sp[0];
                p1 = sp[chan1];

                d0 = (p0*k0 + buffd[i    ]);
                d1 = (p1*k0 + buffd[i + 1]);

                STORE_RES(dp[0    ], d0);
                STORE_RES(dp[chan1], d1);

                buffd[i    ] = 0;
                buffd[i + 1] = 0;

                sp += chan2;
                dp += chan2;
              }
            }
          }
        }
      }

      /* last pixels */
      for (; i < wid; i++) {
        mlib_s32 *pk = k, s = 0;
        mlib_s32 x;

        for (l = 0; l < n; l++) {
          sp = sl + l*sll + i*chan1;

          for (x = 0; x < m; x++) {
            s += sp[0] * pk[0];
            sp += chan1;
            pk ++;
          }
        }

        STORE_RES(dp[0], s);

        sp += chan1;
        dp += chan1;
      }

      sl += sll;
      dl += dll;
    }
  }

  if (buffd != buff) mlib_free(buffd);
  if (k != k_locl) mlib_free(k);

  return MLIB_SUCCESS;
}

/***************************************************************/
#endif /* __sparc ( for x86, using integer multiplies is faster ) */

/***************************************************************/
