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
 *   Internal functions for mlib_ImageConv* on U8/S16/U16 type and
 *   MLIB_EDGE_SRC_EXTEND mask
 */

#include "mlib_image.h"
#include "mlib_ImageConv.h"
#include "mlib_c_ImageConv.h"

/*
 * This define switches between functions of different data types
 */

#define IMG_TYPE 3

/***************************************************************/
#if IMG_TYPE == 1

#define DTYPE             mlib_u8
#define CONV_FUNC(KERN)   mlib_c_conv##KERN##ext_u8(PARAM)
#define CONV_FUNC_MxN     mlib_c_convMxNext_u8(PARAM_MxN)
#define CONV_FUNC_I(KERN) mlib_i_conv##KERN##ext_u8(PARAM)
#define CONV_FUNC_MxN_I   mlib_i_convMxNext_u8(PARAM_MxN)
#define DSCALE            (1 << 24)
#define FROM_S32(x)       (((x) >> 24) ^ 128)
#define S64TOS32(x)       (x)
#define SAT_OFF           -(1u << 31)

#elif IMG_TYPE == 2

#define DTYPE             mlib_s16
#define CONV_FUNC(KERN)   mlib_conv##KERN##ext_s16(PARAM)
#define CONV_FUNC_MxN     mlib_convMxNext_s16(PARAM_MxN)
#define CONV_FUNC_I(KERN) mlib_i_conv##KERN##ext_s16(PARAM)
#define CONV_FUNC_MxN_I   mlib_i_convMxNext_s16(PARAM_MxN)
#define DSCALE            65536.0
#define FROM_S32(x)       ((x) >> 16)
#define S64TOS32(x)       ((x) & 0xffffffff)
#define SAT_OFF

#elif IMG_TYPE == 3

#define DTYPE             mlib_u16
#define CONV_FUNC(KERN)   mlib_conv##KERN##ext_u16(PARAM)
#define CONV_FUNC_MxN     mlib_convMxNext_u16(PARAM_MxN)
#define CONV_FUNC_I(KERN) mlib_i_conv##KERN##ext_u16(PARAM)
#define CONV_FUNC_MxN_I   mlib_i_convMxNext_u16(PARAM_MxN)
#define DSCALE            65536.0
#define FROM_S32(x)       (((x) >> 16) ^ 0x8000)
#define S64TOS32(x)       (x)
#define SAT_OFF           -(1u << 31)

#endif /* IMG_TYPE == 1 */

/***************************************************************/
#define KSIZE1 (KSIZE - 1)

/***************************************************************/
#define PARAM                                                   \
  mlib_image       *dst,                                        \
  const mlib_image *src,                                        \
  mlib_s32         dx_l,                                        \
  mlib_s32         dx_r,                                        \
  mlib_s32         dy_t,                                        \
  mlib_s32         dy_b,                                        \
  const mlib_s32   *kern,                                       \
  mlib_s32         scalef_expon,                                \
  mlib_s32         cmask

/***************************************************************/
#define PARAM_MxN                                               \
  mlib_image       *dst,                                        \
  const mlib_image *src,                                        \
  const mlib_s32   *kernel,                                     \
  mlib_s32         m,                                           \
  mlib_s32         n,                                           \
  mlib_s32         dx_l,                                        \
  mlib_s32         dx_r,                                        \
  mlib_s32         dy_t,                                        \
  mlib_s32         dy_b,                                        \
  mlib_s32         scale,                                       \
  mlib_s32         cmask

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
#define MLIB_D2_24 16777216.0f

/***************************************************************/
typedef union {
  mlib_d64 d64;
  struct {
    mlib_s32 i0;
    mlib_s32 i1;
  } i32s;
} d64_2x32;

/***************************************************************/
#define BUFF_LINE 256

/***************************************************************/
#define DEF_VARS(type)                                          \
  type     *adr_src, *sl, *sp, *sl1;                            \
  type     *adr_dst, *dl, *dp;                                  \
  FTYPE    *pbuff = buff;                                       \
  mlib_s32 *buffi, *buffo;                                      \
  mlib_s32 wid, hgt, sll, dll;                                  \
  mlib_s32 nchannel, chan1, chan2;                              \
  mlib_s32 i, j, c, swid

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

/*
 * Test for the presence of any "1" bit in bits
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

mlib_status CONV_FUNC(3x3)
{
  FTYPE    buff[(KSIZE + 2)*BUFF_LINE], *buff0, *buff1, *buff2, *buff3, *buffT;
  DEF_VARS(DTYPE);
  DTYPE *sl2;
#ifndef __sparc
  mlib_s32 d0, d1;
#endif /* __sparc */
  LOAD_KERNEL3();
  GET_SRC_DST_PARAMETERS(DTYPE);

  swid = wid + KSIZE1;

  if (swid > BUFF_LINE) {
    pbuff = mlib_malloc((KSIZE + 2)*sizeof(FTYPE   )*swid);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  buff0 = pbuff;
  buff1 = buff0 + swid;
  buff2 = buff1 + swid;
  buff3 = buff2 + swid;
  buffo = (mlib_s32*)(buff3 + swid);
  buffi = buffo + (swid &~ 1);

  swid -= (dx_l + dx_r);

  chan1 = nchannel;
  chan2 = chan1 + chan1;

  for (c = 0; c < nchannel; c++) {
    if (!(cmask & (1 << (nchannel - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    if ((1 > dy_t) && (1 < hgt + KSIZE1 - dy_b)) sl1 = sl + sll;
    else sl1 = sl;

    if ((hgt - dy_b) > 0) sl2 = sl1 + sll;
    else sl2 = sl1;

    for (i = 0; i < dx_l; i++) {
      buff0[i] = (FTYPE)sl[0];
      buff1[i] = (FTYPE)sl1[0];
      buff2[i] = (FTYPE)sl2[0];
    }

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < swid; i++) {
      buff0[i + dx_l] = (FTYPE)sl[i*chan1];
      buff1[i + dx_l] = (FTYPE)sl1[i*chan1];
      buff2[i + dx_l] = (FTYPE)sl2[i*chan1];
    }

    for (i = 0; i < dx_r; i++) {
      buff0[swid + dx_l + i] = buff0[swid + dx_l - 1];
      buff1[swid + dx_l + i] = buff1[swid + dx_l - 1];
      buff2[swid + dx_l + i] = buff2[swid + dx_l - 1];
    }

    if ((hgt - dy_b) > 1) sl = sl2 + sll;
    else sl = sl2;

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
        buff3[i + dx_l    ] = (FTYPE)dd.i32s.i0;
        buff3[i + dx_l + 1] = (FTYPE)dd.i32s.i1;

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
        buff3[i + dx_l] = (FTYPE)buffi[i];

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

      for (; i < swid; i++) {
        buffi[i] = (mlib_s32)sp[0];
        buff3[i + dx_l] = (FTYPE)buffi[i];
        sp += chan1;
      }

      for (i = 0; i < dx_l; i++) buff3[i] = buff3[dx_l];
      for (i = 0; i < dx_r; i++) buff3[swid + dx_l + i] = buff3[swid + dx_l - 1];

      if (j < hgt - dy_b - 2) sl += sll;
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

mlib_status CONV_FUNC_I(3x3)
{
  DTYPE    *adr_src, *sl, *sp0, *sp1, *sp2, *sp_1, *sp_2;
  DTYPE    *adr_dst, *dl, *dp;
  mlib_s32 wid, hgt, sll, dll;
  mlib_s32 nchannel, chan1, chan2, delta_chan;
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
  delta_chan = 0;

  if ((1 > dx_l) && (1 < wid + KSIZE1 - dx_r)) delta_chan = chan1;

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    sp_1 = sl;

    if ((1 > dy_t) && (1 < hgt + KSIZE1 - dy_b)) sl += sll;
    sp_2 = sl;

    if ((hgt - dy_b) > 0) sl += sll;

    for (j = 0; j < hgt; j++) {
      mlib_s32 s0, s1;
      mlib_s32 pix0, pix1;

      dp  = dl;
      sp0 = sp_1;
      sp_1 = sp_2;
      sp_2 = sl;

      sp1 = sp_1;
      sp2 = sp_2;

      p02 = sp0[0];
      p12 = sp1[0];
      p22 = sp2[0];

      p03 = sp0[delta_chan];
      p13 = sp1[delta_chan];
      p23 = sp2[delta_chan];

      s0 = p02 * k0 + p03 * k1 + p12 * k3 + p13 * k4 + p22 * k6 + p23 * k7;
      s1 = p03 * k0 + p13 * k3 + p23 * k6;

      sp0 += (chan1 + delta_chan);
      sp1 += (chan1 + delta_chan);
      sp2 += (chan1 + delta_chan);

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - dx_r - 2); i += 2) {
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

      p02 = p03; p12 = p13; p22 = p23;

      for (; i < wid - dx_r; i++) {
        p03 = sp0[0]; p13 = sp1[0]; p23 = sp2[0];
        pix0 = (s0 + p03 * k2 + p13 * k5 + p23 * k8) >> shift2;
        CLAMP_STORE(dp[0], pix0);
        s0 = p02 * k0 + p03 * k1 + p12 * k3 + p13 * k4 + p22 * k6 + p23 * k7;
        p02 = p03; p12 = p13; p22 = p23;
        sp0 += chan1;
        sp1 += chan1;
        sp2 += chan1;
        dp += chan1;
      }

      sp0 -= chan1;
      sp1 -= chan1;
      sp2 -= chan1;

      for (; i < wid; i++) {
        p03 = sp0[0]; p13 = sp1[0]; p23 = sp2[0];
        pix0 = (s0 + p03 * k2 + p13 * k5 + p23 * k8) >> shift2;
        CLAMP_STORE(dp[0], pix0);
        s0 = p02 * k0 + p03 * k1 + p12 * k3 + p13 * k4 + p22 * k6 + p23 * k7;
        p02 = p03; p12 = p13; p22 = p23;
        dp += chan1;
      }

      if (j < hgt - dy_b - 1) sl += sll;
      dl += dll;
    }
  }

  return MLIB_SUCCESS;
}

#endif /* __sparc ( for x86, using integer multiplies is faster ) */

/***************************************************************/
#undef  KSIZE
#define KSIZE 4

mlib_status CONV_FUNC(4x4)
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
  DTYPE *sl2, *sl3;
  LOAD_KERNEL(KSIZE*KSIZE);
  GET_SRC_DST_PARAMETERS(DTYPE);

  swid = wid + KSIZE1;

  if (swid > BUFF_LINE) {
    pbuff = mlib_malloc((KSIZE + 3)*sizeof(FTYPE   )*swid);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  buff0 = pbuff;
  buff1 = buff0 + swid;
  buff2 = buff1 + swid;
  buff3 = buff2 + swid;
  buff4 = buff3 + swid;
  buffd = buff4 + swid;
  buffo = (mlib_s32*)(buffd + swid);
  buffi = buffo + (swid &~ 1);

  swid -= (dx_l + dx_r);

  chan1 = nchannel;
  chan2 = chan1 + chan1;

  for (c = 0; c < nchannel; c++) {
    if (!(cmask & (1 << (nchannel - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    if ((1 > dy_t) && (1 < hgt + KSIZE1 - dy_b)) sl1 = sl + sll;
    else sl1 = sl;

    if ((2 > dy_t) && (2 < hgt + KSIZE1 - dy_b)) sl2 = sl1 + sll;
    else sl2 = sl1;

    if ((hgt - dy_b) > 0) sl3 = sl2 + sll;
    else sl3 = sl2;

    for (i = 0; i < dx_l; i++) {
      buff0[i] = (FTYPE)sl[0];
      buff1[i] = (FTYPE)sl1[0];
      buff2[i] = (FTYPE)sl2[0];
      buff3[i] = (FTYPE)sl3[0];
    }

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < swid; i++) {
      buff0[i + dx_l] = (FTYPE)sl[i*chan1];
      buff1[i + dx_l] = (FTYPE)sl1[i*chan1];
      buff2[i + dx_l] = (FTYPE)sl2[i*chan1];
      buff3[i + dx_l] = (FTYPE)sl3[i*chan1];
    }

    for (i = 0; i < dx_r; i++) {
      buff0[swid + dx_l + i] = buff0[swid + dx_l - 1];
      buff1[swid + dx_l + i] = buff1[swid + dx_l - 1];
      buff2[swid + dx_l + i] = buff2[swid + dx_l - 1];
      buff3[swid + dx_l + i] = buff3[swid + dx_l - 1];
    }

    if ((hgt - dy_b) > 1) sl = sl3 + sll;
    else sl = sl3;

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
        buff4[i + dx_l    ] = (FTYPE)dd.i32s.i0;
        buff4[i + dx_l + 1] = (FTYPE)dd.i32s.i1;

        buffd[i    ] = (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 +
                        p10 * k4 + p11 * k5 + p12 * k6 + p13 * k7);
        buffd[i + 1] = (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 +
                        p11 * k4 + p12 * k5 + p13 * k6 + p14 * k7);

        sp += chan2;
      }

      /*
       *  Second loop on two last lines of kernel
       */
      k0 = k[ 8]; k1 = k[ 9]; k2 = k[10]; k3 = k[11];
      k4 = k[12]; k5 = k[13]; k6 = k[14]; k7 = k[15];

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

        dp += chan2;
      }

      /* last pixels */
      for (; i < wid; i++) {
        p00 = buff0[i];     p10 = buff1[i];     p20 = buff2[i];     p30 = buff3[i];
        p01 = buff0[i + 1]; p11 = buff1[i + 1]; p21 = buff2[i + 1]; p31 = buff3[i + 1];
        p02 = buff0[i + 2]; p12 = buff1[i + 2]; p22 = buff2[i + 2]; p32 = buff3[i + 2];
        p03 = buff0[i + 3]; p13 = buff1[i + 3]; p23 = buff2[i + 3]; p33 = buff3[i + 3];

        buff4[i + dx_l] = (FTYPE)sp[0];

        buffo[i] = D2I(p00 * k[0] + p01 * k[1] + p02 * k[2] + p03 * k[3] +
                       p10 * k[4] + p11 * k[5] + p12 * k[6] + p13 * k[7] +
                       p20 * k[ 8] + p21 * k[ 9] + p22 * k[10] + p23 * k[11] +
                       p30 * k[12] + p31 * k[13] + p32 * k[14] + p33 * k[15]);

        dp[0] = FROM_S32(buffo[i]);

        sp += chan1;
        dp += chan1;
      }

      for (; i < swid; i++) {
        buff4[i + dx_l] = (FTYPE)sp[0];
        sp += chan1;
      }

      for (i = 0; i < dx_l; i++) buff4[i] = buff4[dx_l];
      for (i = 0; i < dx_r; i++) buff4[swid + dx_l + i] = buff4[swid + dx_l - 1];

      /* next line */

      if (j < hgt - dy_b - 2) sl += sll;
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

mlib_status CONV_FUNC(5x5)
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
  DTYPE *sl2, *sl3, *sl4;
  LOAD_KERNEL(KSIZE*KSIZE);
  GET_SRC_DST_PARAMETERS(DTYPE);

  swid = wid + KSIZE1;

  if (swid > BUFF_LINE) {
    pbuff = mlib_malloc((KSIZE + 3)*sizeof(FTYPE   )*swid);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  buff0 = pbuff;
  buff1 = buff0 + swid;
  buff2 = buff1 + swid;
  buff3 = buff2 + swid;
  buff4 = buff3 + swid;
  buff5 = buff4 + swid;
  buffd = buff5 + swid;
  buffo = (mlib_s32*)(buffd + swid);
  buffi = buffo + (swid &~ 1);

  swid -= (dx_l + dx_r);

  chan1 = nchannel;
  chan2 = chan1 + chan1;

  for (c = 0; c < nchannel; c++) {
    if (!(cmask & (1 << (nchannel - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    if ((1 > dy_t) && (1 < hgt + KSIZE1 - dy_b)) sl1 = sl + sll;
    else sl1 = sl;

    if ((2 > dy_t) && (2 < hgt + KSIZE1 - dy_b)) sl2 = sl1 + sll;
    else sl2 = sl1;

    if ((3 > dy_t) && (3 < hgt + KSIZE1 - dy_b)) sl3 = sl2 + sll;
    else sl3 = sl2;

    if ((hgt - dy_b) > 0) sl4 = sl3 + sll;
    else sl4 = sl3;

    for (i = 0; i < dx_l; i++) {
      buff0[i] = (FTYPE)sl[0];
      buff1[i] = (FTYPE)sl1[0];
      buff2[i] = (FTYPE)sl2[0];
      buff3[i] = (FTYPE)sl3[0];
      buff4[i] = (FTYPE)sl4[0];
    }

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < swid; i++) {
      buff0[i + dx_l] = (FTYPE)sl[i*chan1];
      buff1[i + dx_l] = (FTYPE)sl1[i*chan1];
      buff2[i + dx_l] = (FTYPE)sl2[i*chan1];
      buff3[i + dx_l] = (FTYPE)sl3[i*chan1];
      buff4[i + dx_l] = (FTYPE)sl4[i*chan1];
    }

    for (i = 0; i < dx_r; i++) {
      buff0[swid + dx_l + i] = buff0[swid + dx_l - 1];
      buff1[swid + dx_l + i] = buff1[swid + dx_l - 1];
      buff2[swid + dx_l + i] = buff2[swid + dx_l - 1];
      buff3[swid + dx_l + i] = buff3[swid + dx_l - 1];
      buff4[swid + dx_l + i] = buff4[swid + dx_l - 1];
    }

    if ((hgt - dy_b) > 1) sl = sl4 + sll;
    else sl = sl4;

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
      }

      /*
       *  Second loop
       */
      k0 = k[10]; k1 = k[11]; k2 = k[12]; k3 = k[13]; k4 = k[14];
      k5 = k[15]; k6 = k[16]; k7 = k[17]; k8 = k[18]; k9 = k[19];

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

        dd.d64 = *(FTYPE   *)(buffi + i);
        buff5[i + dx_l    ] = (FTYPE)dd.i32s.i0;
        buff5[i + dx_l + 1] = (FTYPE)dd.i32s.i1;

        buffd[i    ] += (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                         p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
        buffd[i + 1] += (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 + p05 * k4 +
                         p11 * k5 + p12 * k6 + p13 * k7 + p14 * k8 + p15 * k9);
      }

      /*
       *  3 loop
       */
      k0 = k[20]; k1 = k[21]; k2 = k[22]; k3 = k[23]; k4 = k[24];

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

        buff5[i + dx_l] = (FTYPE)sp[0];

        buffo[i] = D2I(p00 * k[0] + p01 * k[1] + p02 * k[2] + p03 * k[3] + p04 * k[4] +
                       p10 * k[5] + p11 * k[6] + p12 * k[7] + p13 * k[8] + p14 * k[9] +
                       p20 * k[10] + p21 * k[11] + p22 * k[12] + p23 * k[13] + p24 * k[14] +
                       p30 * k[15] + p31 * k[16] + p32 * k[17] + p33 * k[18] + p34 * k[19] +
                       p40 * k[20] + p41 * k[21] + p42 * k[22] + p43 * k[23] + p44 * k[24]);

        dp[0] = FROM_S32(buffo[i]);

        sp += chan1;
        dp += chan1;
      }

      for (; i < swid; i++) {
        buff5[i + dx_l] = (FTYPE)sp[0];
        sp += chan1;
      }

      for (i = 0; i < dx_l; i++) buff5[i] = buff5[dx_l];
      for (i = 0; i < dx_r; i++) buff5[swid + dx_l + i] = buff5[swid + dx_l - 1];

      /* next line */

      if (j < hgt - dy_b - 2) sl += sll;
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

mlib_status CONV_FUNC_I(5x5)
{
  mlib_s32 buff[BUFF_LINE];
  mlib_s32 *buffd;
  mlib_s32 k[KSIZE*KSIZE];
  mlib_s32 shift1, shift2;
  mlib_s32 k0, k1, k2, k3, k4, k5, k6, k7, k8, k9;
  mlib_s32 p00, p01, p02, p03, p04, p05,
           p10, p11, p12, p13, p14, p15;
  DTYPE    *adr_src, *sl, *sp0, *sp1, *sp2, *sp3, *sp4;
  DTYPE    *sp_1, *sp_2, *sp_3, *sp_4;
  DTYPE    *adr_dst, *dl, *dp;
  mlib_s32 *pbuff = buff;
  mlib_s32 wid, hgt, sll, dll;
  mlib_s32 nchannel, chan1, chan2, chan4;
  mlib_s32 delta_chan1, delta_chan2, delta_chan3;
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

  if ((1 > dx_l) && (1 < wid + KSIZE1 - dx_r)) delta_chan1 = chan1;
  else delta_chan1 = 0;

  if ((2 > dx_l) && (2 < wid + KSIZE1 - dx_r)) delta_chan2 = delta_chan1 + chan1;
  else delta_chan2 = delta_chan1;

  if ((3 > dx_l) && (3 < wid + KSIZE1 - dx_r)) delta_chan3 = delta_chan2 + chan1;
  else delta_chan3 = delta_chan2;

  chan4 = chan1 + delta_chan3;

  for (c = 0; c < chan1; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    sp_1 = sl;

    if ((1 > dy_t) && (1 < hgt + KSIZE1 - dy_b)) sl += sll;
    sp_2 = sl;

    if ((2 > dy_t) && (2 < hgt + KSIZE1 - dy_b)) sl += sll;
    sp_3 = sl;

    if ((3 > dy_t) && (3 < hgt + KSIZE1 - dy_b)) sl += sll;
    sp_4 = sl;

    if ((hgt - dy_b) > 0) sl += sll;

    for (j = 0; j < hgt; j++) {
      mlib_s32 pix0, pix1;

      dp  = dl;
      sp0 = sp_1;
      sp_1 = sp_2;
      sp_2 = sp_3;
      sp_3 = sp_4;
      sp_4 = sl;

      sp1 = sp_1;
      sp2 = sp_2;
      sp3 = sp_3;
      sp4 = sp_4;

      /*
       *  First loop
       */

      k0 = k[0]; k1 = k[1]; k2 = k[2]; k3 = k[3]; k4 = k[4];
      k5 = k[5]; k6 = k[6]; k7 = k[7]; k8 = k[8]; k9 = k[9];

      p02 = sp0[0];           p12 = sp1[0];
      p03 = sp0[delta_chan1]; p13 = sp1[delta_chan1];
      p04 = sp0[delta_chan2]; p14 = sp1[delta_chan2];
      p05 = sp0[delta_chan3]; p15 = sp1[delta_chan3];

      sp0 += chan4;
      sp1 += chan4;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - dx_r - 2); i += 2) {
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
      }

      p01 = p02; p02 = p03; p03 = p04; p04 = p05;
      p11 = p12; p12 = p13; p13 = p14; p14 = p15;

      for (; i < wid - dx_r; i++) {
        p00 = p01; p10 = p11;
        p01 = p02; p11 = p12;
        p02 = p03; p12 = p13;
        p03 = p04; p13 = p14;

        p04 = sp0[0];     p14 = sp1[0];

        buffd[i] = (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                    p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);

        sp0 += chan1;
        sp1 += chan1;
      }

      sp0 -= chan1;
      sp1 -= chan1;

      for (; i < wid; i++) {
        p00 = p01; p10 = p11;
        p01 = p02; p11 = p12;
        p02 = p03; p12 = p13;
        p03 = p04; p13 = p14;

        p04 = sp0[0];     p14 = sp1[0];

        buffd[i] = (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                    p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
      }

      /*
       *  Second loop
       */

      k0 = k[10]; k1 = k[11]; k2 = k[12]; k3 = k[13]; k4 = k[14];
      k5 = k[15]; k6 = k[16]; k7 = k[17]; k8 = k[18]; k9 = k[19];

      p02 = sp2[0];           p12 = sp3[0];
      p03 = sp2[delta_chan1]; p13 = sp3[delta_chan1];
      p04 = sp2[delta_chan2]; p14 = sp3[delta_chan2];
      p05 = sp2[delta_chan3]; p15 = sp3[delta_chan3];

      sp2 += chan4;
      sp3 += chan4;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - dx_r - 2); i += 2) {
        p00 = p02; p10 = p12;
        p01 = p03; p11 = p13;
        p02 = p04; p12 = p14;
        p03 = p05; p13 = p15;

        p04 = sp2[0];     p14 = sp3[0];
        p05 = sp2[chan1]; p15 = sp3[chan1];

        buffd[i    ] += (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                         p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
        buffd[i + 1] += (p01 * k0 + p02 * k1 + p03 * k2 + p04 * k3 + p05 * k4 +
                         p11 * k5 + p12 * k6 + p13 * k7 + p14 * k8 + p15 * k9);

        sp2 += chan2;
        sp3 += chan2;
      }

      p01 = p02; p02 = p03; p03 = p04; p04 = p05;
      p11 = p12; p12 = p13; p13 = p14; p14 = p15;

      for (; i < wid - dx_r; i++) {
        p00 = p01; p10 = p11;
        p01 = p02; p11 = p12;
        p02 = p03; p12 = p13;
        p03 = p04; p13 = p14;

        p04 = sp2[0];     p14 = sp3[0];

        buffd[i] += (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                     p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);

        sp2 += chan1;
        sp3 += chan1;
      }

      sp2 -= chan1;
      sp3 -= chan1;

      for (; i < wid; i++) {
        p00 = p01; p10 = p11;
        p01 = p02; p11 = p12;
        p02 = p03; p12 = p13;
        p03 = p04; p13 = p14;

        p04 = sp2[0];     p14 = sp3[0];

        buffd[i] += (p00 * k0 + p01 * k1 + p02 * k2 + p03 * k3 + p04 * k4 +
                     p10 * k5 + p11 * k6 + p12 * k7 + p13 * k8 + p14 * k9);
      }

      /*
       *  3 loop
       */

      k0 = k[20]; k1 = k[21]; k2 = k[22]; k3 = k[23]; k4 = k[24];

      p02 = sp4[0];
      p03 = sp4[delta_chan1];
      p04 = sp4[delta_chan2];
      p05 = sp4[delta_chan3];

      sp4 += chan4;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i <= (wid - dx_r - 2); i += 2) {
        p00 = p02; p01 = p03; p02 = p04; p03 = p05;

        p04 = sp4[0]; p05 = sp4[chan1];

        pix0 = (buffd[i    ] + p00 * k0 + p01 * k1 + p02 * k2 +
                p03 * k3 + p04 * k4) >> shift2;
        pix1 = (buffd[i + 1] + p01 * k0 + p02 * k1 + p03 * k2 +
                p04 * k3 + p05 * k4) >> shift2;

        CLAMP_STORE(dp[0],     pix0);
        CLAMP_STORE(dp[chan1], pix1);

        dp  += chan2;
        sp4 += chan2;
      }

      p01 = p02; p02 = p03; p03 = p04; p04 = p05;

      for (; i < wid - dx_r; i++) {
        p00 = p01; p01 = p02; p02 = p03; p03 = p04;

        p04 = sp4[0];

        pix0 = (buffd[i    ] + p00 * k0 + p01 * k1 + p02 * k2 +
                p03 * k3 + p04 * k4) >> shift2;
        CLAMP_STORE(dp[0],     pix0);

        dp  += chan1;
        sp4 += chan1;
      }

      sp4 -= chan1;

      for (; i < wid; i++) {
        p00 = p01; p01 = p02; p02 = p03; p03 = p04;

        p04 = sp4[0];

        pix0 = (buffd[i    ] + p00 * k0 + p01 * k1 + p02 * k2 +
                p03 * k3 + p04 * k4) >> shift2;
        CLAMP_STORE(dp[0],     pix0);

        dp  += chan1;
      }

      /* next line */

      if (j < hgt - dy_b - 1) sl += sll;
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

mlib_status CONV_FUNC(7x7)
{
  FTYPE    buff[(KSIZE + 3)*BUFF_LINE], *buffs[2*(KSIZE + 1)], *buffd;
  FTYPE    k[KSIZE*KSIZE];
  mlib_s32 l, m, buff_ind;
  mlib_s32 d0, d1;
  FTYPE    k0, k1, k2, k3, k4, k5, k6;
  FTYPE    p0, p1, p2, p3, p4, p5, p6, p7;
  DTYPE *sl2, *sl3, *sl4, *sl5, *sl6;
  DEF_VARS(DTYPE);
  LOAD_KERNEL(KSIZE*KSIZE);
  GET_SRC_DST_PARAMETERS(DTYPE);

  swid = wid + KSIZE1;

  if (wid > BUFF_LINE) {
    pbuff = mlib_malloc((KSIZE + 3)*sizeof(FTYPE   )*wid);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  for (l = 0; l < KSIZE + 1; l++) buffs[l] = pbuff + l*swid;
  for (l = 0; l < KSIZE + 1; l++) buffs[l + (KSIZE + 1)] = buffs[l];
  buffd = buffs[KSIZE] + swid;
  buffo = (mlib_s32*)(buffd + swid);
  buffi = buffo + (swid &~ 1);

  swid -= (dx_l + dx_r);

  chan1 = nchannel;
  chan2 = chan1 + chan1;

  for (c = 0; c < nchannel; c++) {
    if (!(cmask & (1 << (nchannel - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    if ((1 > dy_t) && (1 < hgt + KSIZE1 - dy_b)) sl1 = sl + sll;
    else sl1 = sl;

    if ((2 > dy_t) && (2 < hgt + KSIZE1 - dy_b)) sl2 = sl1 + sll;
    else sl2 = sl1;

    if ((3 > dy_t) && (3 < hgt + KSIZE1 - dy_b)) sl3 = sl2 + sll;
    else sl3 = sl2;

    if ((4 > dy_t) && (4 < hgt + KSIZE1 - dy_b)) sl4 = sl3 + sll;
    else sl4 = sl3;

    if ((5 > dy_t) && (5 < hgt + KSIZE1 - dy_b)) sl5 = sl4 + sll;
    else sl5 = sl4;

    if ((hgt - dy_b) > 0) sl6 = sl5 + sll;
    else sl6 = sl5;

    for (i = 0; i < dx_l; i++) {
      buffs[0][i] = (FTYPE)sl[0];
      buffs[1][i] = (FTYPE)sl1[0];
      buffs[2][i] = (FTYPE)sl2[0];
      buffs[3][i] = (FTYPE)sl3[0];
      buffs[4][i] = (FTYPE)sl4[0];
      buffs[5][i] = (FTYPE)sl5[0];
      buffs[6][i] = (FTYPE)sl6[0];
    }

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < swid; i++) {
      buffs[0][i + dx_l] = (FTYPE)sl[i*chan1];
      buffs[1][i + dx_l] = (FTYPE)sl1[i*chan1];
      buffs[2][i + dx_l] = (FTYPE)sl2[i*chan1];
      buffs[3][i + dx_l] = (FTYPE)sl3[i*chan1];
      buffs[4][i + dx_l] = (FTYPE)sl4[i*chan1];
      buffs[5][i + dx_l] = (FTYPE)sl5[i*chan1];
      buffs[6][i + dx_l] = (FTYPE)sl6[i*chan1];
    }

    for (i = 0; i < dx_r; i++) {
      buffs[0][swid + dx_l + i] = buffs[0][swid + dx_l - 1];
      buffs[1][swid + dx_l + i] = buffs[1][swid + dx_l - 1];
      buffs[2][swid + dx_l + i] = buffs[2][swid + dx_l - 1];
      buffs[3][swid + dx_l + i] = buffs[3][swid + dx_l - 1];
      buffs[4][swid + dx_l + i] = buffs[4][swid + dx_l - 1];
      buffs[5][swid + dx_l + i] = buffs[5][swid + dx_l - 1];
      buffs[6][swid + dx_l + i] = buffs[6][swid + dx_l - 1];
    }

    buff_ind = 0;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < wid; i++) buffd[i] = 0.0;

    if ((hgt - dy_b) > 1) sl = sl6 + sll;
    else sl = sl6;

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
            buffn[i + dx_l    ] = (FTYPE)dd.i32s.i0;
            buffn[i + dx_l + 1] = (FTYPE)dd.i32s.i1;

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

        buffn[i + dx_l] = (FTYPE)sp[0];

        sp += chan1;
        dp += chan1;
      }

      for (; i < swid; i++) {
        buffn[i + dx_l] = (FTYPE)sp[0];
        sp += chan1;
      }

      for (i = 0; i < dx_l; i++) buffn[i] = buffn[dx_l];
      for (i = 0; i < dx_r; i++) buffn[swid + dx_l + i] = buffn[swid + dx_l - 1];

      /* next line */

      if (j < hgt - dy_b - 2) sl += sll;
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
#define BUFF_SIZE   1600
#define CACHE_SIZE  (64*1024)

static mlib_status mlib_ImageConv1xN_ext(mlib_image       *dst,
                                         const mlib_image *src,
                                         const mlib_d64   *k,
                                         mlib_s32         n,
                                         mlib_s32         dy_t,
                                         mlib_s32         dy_b,
                                         mlib_s32         cmask)
{
  DTYPE    *adr_src, *sl;
  DTYPE    *adr_dst, *dl, *dp;
  FTYPE    buff[BUFF_SIZE];
  FTYPE    *buffd;
  FTYPE    *pbuff = buff;
  const FTYPE    *pk;
  FTYPE    k0, k1, k2, k3;
  FTYPE    p0, p1, p2, p3, p4;
  FTYPE    *sbuff;
  mlib_s32 l, k_off, off, bsize;
  mlib_s32 max_hsize, smax_hsize, shgt, hsize, kh;
  mlib_s32 d0, d1, ii;
  mlib_s32 wid, hgt, sll, dll;
  mlib_s32 nchannel;
  mlib_s32 i, j, c;
  GET_SRC_DST_PARAMETERS(DTYPE);

  max_hsize = ((CACHE_SIZE/sizeof(DTYPE))/sll) - (n - 1);

  if (max_hsize < 1) max_hsize = 1;
  if (max_hsize > hgt) max_hsize = hgt;

  shgt = hgt + (n - 1);
  smax_hsize = max_hsize + (n - 1);

  bsize = 2 * (smax_hsize + 1);

  if (bsize > BUFF_SIZE) {
    pbuff = mlib_malloc(sizeof(FTYPE)*bsize);

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  sbuff = pbuff;
  buffd = sbuff + smax_hsize;

  shgt -= (dy_t + dy_b);
  k_off = 0;

  for (l = 0; l < hgt; l += hsize) {
    hsize = hgt - l;

    if (hsize > max_hsize) hsize = max_hsize;

    smax_hsize = hsize + (n - 1);

    for (c = 0; c < nchannel; c++) {
      if (!(cmask & (1 << (nchannel - 1 - c)))) continue;

      sl = adr_src + c;
      dl = adr_dst + c;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i < hsize; i++) buffd[i] = 0.0;

      for (j = 0; j < wid; j++) {
        FTYPE    *buff = sbuff;

        for (i = k_off, ii = 0; (i < dy_t) && (ii < smax_hsize); i++, ii++) {
          sbuff[i - k_off] = (FTYPE)sl[0];
        }

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
        for (; (i < shgt + dy_t) && (ii < smax_hsize); i++, ii++) {
          sbuff[i - k_off] = (FTYPE)sl[(i - dy_t)*sll];
        }

        for (; (i < shgt + dy_t + dy_b) && (ii < smax_hsize); i++, ii++) {
          sbuff[i - k_off] = (FTYPE)sl[(shgt - 1)*sll];
        }

        pk = k;

        for (off = 0; off < (n - 4); off += 4) {

          p2 = buff[0]; p3 = buff[1]; p4 = buff[2];
          k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
          for (i = 0; i < hsize; i += 2) {
            p0 = p2; p1 = p3; p2 = p4;

            p3 = buff[i + 3]; p4 = buff[i + 4];

            buffd[i    ] += p0*k0 + p1*k1 + p2*k2 + p3*k3;
            buffd[i + 1] += p1*k0 + p2*k1 + p3*k2 + p4*k3;
          }

          pk += 4;
          buff += 4;
        }

        dp = dl;
        kh = n - off;

        if (kh == 4) {
          p2 = buff[0]; p3 = buff[1]; p4 = buff[2];
          k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
          for (i = 0; i <= (hsize - 2); i += 2) {
            p0 = p2; p1 = p3; p2 = p4;

            p3 = buff[i + 3]; p4 = buff[i + 4];

            d0 = D2I(p0*k0 + p1*k1 + p2*k2 + p3*k3 + buffd[i    ]);
            d1 = D2I(p1*k0 + p2*k1 + p3*k2 + p4*k3 + buffd[i + 1]);

            dp[0  ] = FROM_S32(d0);
            dp[dll] = FROM_S32(d1);

            buffd[i    ] = 0.0;
            buffd[i + 1] = 0.0;

            dp += 2*dll;
          }

          if (i < hsize) {
            p0 = p2; p1 = p3; p2 = p4;
            p3 = buff[i + 3];
            d0 = D2I(p0*k0 + p1*k1 + p2*k2 + p3*k3 + buffd[i]);
            dp[0] = FROM_S32(d0);
            buffd[i] = 0.0;
          }

        } else if (kh == 3) {

          p2 = buff[0]; p3 = buff[1];
          k0 = pk[0]; k1 = pk[1]; k2 = pk[2];

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
          for (i = 0; i <= (hsize - 2); i += 2) {
            p0 = p2; p1 = p3;

            p2 = buff[i + 2]; p3 = buff[i + 3];

            d0 = D2I(p0*k0 + p1*k1 + p2*k2 + buffd[i    ]);
            d1 = D2I(p1*k0 + p2*k1 + p3*k2 + buffd[i + 1]);

            dp[0  ] = FROM_S32(d0);
            dp[dll] = FROM_S32(d1);

            buffd[i    ] = 0.0;
            buffd[i + 1] = 0.0;

            dp += 2*dll;
          }

          if (i < hsize) {
            p0 = p2; p1 = p3;
            p2 = buff[i + 2];
            d0 = D2I(p0*k0 + p1*k1 + p2*k2 + buffd[i]);
            dp[0] = FROM_S32(d0);

            buffd[i] = 0.0;
          }

        } else if (kh == 2) {

          p2 = buff[0];
          k0 = pk[0]; k1 = pk[1];

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
          for (i = 0; i <= (hsize - 2); i += 2) {
            p0 = p2;

            p1 = buff[i + 1]; p2 = buff[i + 2];

            d0 = D2I(p0*k0 + p1*k1 + buffd[i    ]);
            d1 = D2I(p1*k0 + p2*k1 + buffd[i + 1]);

            dp[0  ] = FROM_S32(d0);
            dp[dll] = FROM_S32(d1);

            buffd[i    ] = 0.0;
            buffd[i + 1] = 0.0;

            dp += 2*dll;
          }

          if (i < hsize) {
            p0 = p2;
            p1 = buff[i + 1];
            d0 = D2I(p0*k0 + p1*k1 + buffd[i]);
            dp[0] = FROM_S32(d0);

            buffd[i] = 0.0;
          }

        } else /* kh == 1 */{

          k0 = pk[0];

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
          for (i = 0; i <= (hsize - 2); i += 2) {
            p0 = buff[i]; p1 = buff[i + 1];

            d0 = D2I(p0*k0 + buffd[i    ]);
            d1 = D2I(p1*k0 + buffd[i + 1]);

            dp[0  ] = FROM_S32(d0);
            dp[dll] = FROM_S32(d1);

            buffd[i    ] = 0.0;
            buffd[i + 1] = 0.0;

            dp += 2*dll;
          }

          if (i < hsize) {
            p0 = buff[i];
            d0 = D2I(p0*k0 + buffd[i]);
            dp[0] = FROM_S32(d0);

            buffd[i] = 0.0;
          }
        }

        /* next line */
        sl += nchannel;
        dl += nchannel;
      }
    }

    k_off += max_hsize;
    adr_dst += max_hsize*dll;
  }

  if (pbuff != buff) mlib_free(pbuff);

  return MLIB_SUCCESS;
}

/***************************************************************/
mlib_status CONV_FUNC_MxN
{
  DTYPE    *adr_src, *sl, *sp = NULL;
  DTYPE    *adr_dst, *dl, *dp = NULL;
  FTYPE    buff[BUFF_SIZE], *buffs_arr[2*(MAX_N + 1)];
  FTYPE    **buffs = buffs_arr, *buffd;
  FTYPE    akernel[256], *k = akernel, fscale = DSCALE;
  FTYPE    *pbuff = buff;
  FTYPE    k0, k1, k2, k3, k4, k5, k6;
  FTYPE    p0, p1, p2, p3, p4, p5, p6, p7;
  mlib_s32 *buffi;
  mlib_s32 mn, l, off, kw, bsize, buff_ind;
  mlib_s32 d0, d1;
  mlib_s32 wid, hgt, sll, dll;
  mlib_s32 nchannel, chan1, chan2;
  mlib_s32 i, j, c, swid;
  d64_2x32 dd;
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

  if (m == 1) return mlib_ImageConv1xN_ext(dst, src, k, n, dy_t, dy_b, cmask);

  swid = wid + (m - 1);

  bsize = (n + 3)*swid;

  if ((bsize > BUFF_SIZE) || (n > MAX_N)) {
    pbuff = mlib_malloc(sizeof(FTYPE)*bsize + sizeof(FTYPE *)*2*(n + 1));

    if (pbuff == NULL) return MLIB_FAILURE;
    buffs = (FTYPE   **)(pbuff + bsize);
  }

  for (l = 0; l < (n + 1); l++) buffs[l] = pbuff + l*swid;
  for (l = 0; l < (n + 1); l++) buffs[l + (n + 1)] = buffs[l];
  buffd = buffs[n] + swid;
  buffi = (mlib_s32*)(buffd + swid);

  chan1 = nchannel;
  chan2 = chan1 + chan1;

  swid -= (dx_l + dx_r);

  for (c = 0; c < nchannel; c++) {
    if (!(cmask & (1 << (chan1 - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    for (l = 0; l < n; l++) {
      FTYPE    *buff = buffs[l];

      for (i = 0; i < dx_l; i++) {
        buff[i] = (FTYPE)sl[0];
      }

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i < swid; i++) {
        buff[i + dx_l] = (FTYPE)sl[i*chan1];
      }

      for (i = 0; i < dx_r; i++) {
        buff[swid + dx_l + i] = buff[swid + dx_l - 1];
      }

      if ((l >= dy_t) && (l < hgt + n - dy_b - 2)) sl += sll;
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

          if (kw == 7) {

            p2 = buff[0]; p3 = buff[1]; p4 = buff[2];
            p5 = buff[3]; p6 = buff[4]; p7 = buff[5];

            k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];
            k4 = pk[4]; k5 = pk[5]; k6 = pk[6];

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
                buffn[i + dx_l    ] = (FTYPE)dd.i32s.i0;
                buffn[i + dx_l + 1] = (FTYPE)dd.i32s.i1;

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

            p2 = buff[0]; p3 = buff[1]; p4 = buff[2];
            p5 = buff[3]; p6 = buff[4];

            k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];
            k4 = pk[4]; k5 = pk[5];

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

                LOAD_BUFF(buffi);

                dd.d64 = *(FTYPE   *)(buffi + i);
                buffn[i + dx_l    ] = (FTYPE)dd.i32s.i0;
                buffn[i + dx_l + 1] = (FTYPE)dd.i32s.i1;

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

            p2 = buff[0]; p3 = buff[1]; p4 = buff[2];
            p5 = buff[3];

            k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];
            k4 = pk[4];

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

                LOAD_BUFF(buffi);

                dd.d64 = *(FTYPE   *)(buffi + i);
                buffn[i + dx_l    ] = (FTYPE)dd.i32s.i0;
                buffn[i + dx_l + 1] = (FTYPE)dd.i32s.i1;

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

            p2 = buff[0]; p3 = buff[1]; p4 = buff[2];

            k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];

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

                LOAD_BUFF(buffi);

                dd.d64 = *(FTYPE   *)(buffi + i);
                buffn[i + dx_l    ] = (FTYPE)dd.i32s.i0;
                buffn[i + dx_l + 1] = (FTYPE)dd.i32s.i1;

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

            p2 = buff[0]; p3 = buff[1];
            k0 = pk[0]; k1 = pk[1]; k2 = pk[2];

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

                LOAD_BUFF(buffi);

                dd.d64 = *(FTYPE   *)(buffi + i);
                buffn[i + dx_l    ] = (FTYPE)dd.i32s.i0;
                buffn[i + dx_l + 1] = (FTYPE)dd.i32s.i1;

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

          } else /* if (kw == 2) */ {

            p2 = buff[0];
            k0 = pk[0]; k1 = pk[1];

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

                LOAD_BUFF(buffi);

                dd.d64 = *(FTYPE   *)(buffi + i);
                buffn[i + dx_l    ] = (FTYPE)dd.i32s.i0;
                buffn[i + dx_l + 1] = (FTYPE)dd.i32s.i1;

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

          pk += kw;
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

        buffn[i + dx_l] = (FTYPE)sp[0];

        sp += chan1;
        dp += chan1;
      }

      for (; i < swid; i++) {
        buffn[i + dx_l] = (FTYPE)sp[0];
        sp += chan1;
      }

      for (i = 0; i < dx_l; i++) buffn[i] = buffn[dx_l];
      for (i = 0; i < dx_r; i++) buffn[swid + dx_l + i] = buffn[swid + dx_l - 1];

      /* next line */

      if (j < hgt - dy_b - 2) sl += sll;
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

mlib_status CONV_FUNC_MxN_I
{
  DTYPE    *adr_src, *sl, *sp = NULL;
  DTYPE    *adr_dst, *dl, *dp = NULL;
  mlib_s32 buff[BUFF_SIZE], *buffs_arr[2*(MAX_N + 1)];
  mlib_s32 *pbuff = buff;
  mlib_s32 **buffs = buffs_arr, *buffd;
  mlib_s32 l, off, kw, bsize, buff_ind;
  mlib_s32 d0, d1, shift1, shift2;
  mlib_s32 k0, k1, k2, k3, k4, k5, k6;
  mlib_s32 p0, p1, p2, p3, p4, p5, p6, p7;
  mlib_s32 wid, hgt, sll, dll;
  mlib_s32 nchannel, chan1;
  mlib_s32 i, j, c, swid;
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

  swid = wid + (m - 1);

  bsize = (n + 2)*swid;

  if ((bsize > BUFF_SIZE) || (n > MAX_N)) {
    pbuff = mlib_malloc(sizeof(mlib_s32)*bsize + sizeof(mlib_s32 *)*2*(n + 1));

    if (pbuff == NULL) return MLIB_FAILURE;
    buffs = (mlib_s32 **)(pbuff + bsize);
  }

  for (l = 0; l < (n + 1); l++) buffs[l] = pbuff + l*swid;
  for (l = 0; l < (n + 1); l++) buffs[l + (n + 1)] = buffs[l];
  buffd = buffs[n] + swid;

  if (m*n > MAX_N*MAX_N) {
    k = mlib_malloc(sizeof(mlib_s32)*(m*n));

    if (k == NULL) {
      if (pbuff != buff) mlib_free(pbuff);
      return MLIB_FAILURE;
    }
  }

  for (i = 0; i < m*n; i++) {
    k[i] = kernel[i] >> shift1;
  }

  swid -= (dx_l + dx_r);

  for (c = 0; c < nchannel; c++) {
    if (!(cmask & (1 << (nchannel - 1 - c)))) continue;

    sl = adr_src + c;
    dl = adr_dst + c;

    for (l = 0; l < n; l++) {
      mlib_s32  *buff = buffs[l];

      for (i = 0; i < dx_l; i++) {
        buff[i] = (mlib_s32)sl[0];
      }

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
      for (i = 0; i < swid; i++) {
        buff[i + dx_l] = (mlib_s32)sl[i*chan1];
      }

      for (i = 0; i < dx_r; i++) {
        buff[swid + dx_l + i] = buff[swid + dx_l - 1];
      }

      if ((l >= dy_t) && (l < hgt + n - dy_b - 2)) sl += sll;
    }

    buff_ind = 0;

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (i = 0; i < wid; i++) buffd[i] = 0;

    for (j = 0; j < hgt; j++) {
      mlib_s32 **buffc = buffs + buff_ind;
      mlib_s32 *buffn = buffc[n];
      mlib_s32 *pk = k;

      for (l = 0; l < n; l++) {
        mlib_s32  *buff_l = buffc[l];

        for (off = 0; off < m;) {
          mlib_s32 *buff = buff_l + off;

          sp = sl;
          dp = dl;

          kw = m - off;

          if (kw > 2*MAX_KER) kw = MAX_KER; else
            if (kw > MAX_KER) kw = kw/2;
          off += kw;

          if (kw == 7) {

            p2 = buff[0]; p3 = buff[1]; p4 = buff[2];
            p5 = buff[3]; p6 = buff[4]; p7 = buff[5];

            k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];
            k4 = pk[4]; k5 = pk[5]; k6 = pk[6];

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

                buffn[i + dx_l    ] = (mlib_s32)sp[0];
                buffn[i + dx_l + 1] = (mlib_s32)sp[chan1];

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

            p2 = buff[0]; p3 = buff[1]; p4 = buff[2];
            p5 = buff[3]; p6 = buff[4];

            k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];
            k4 = pk[4]; k5 = pk[5];

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

                buffn[i + dx_l    ] = (mlib_s32)sp[0];
                buffn[i + dx_l + 1] = (mlib_s32)sp[chan1];

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

            p2 = buff[0]; p3 = buff[1]; p4 = buff[2];
            p5 = buff[3];

            k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];
            k4 = pk[4];

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

                buffn[i + dx_l    ] = (mlib_s32)sp[0];
                buffn[i + dx_l + 1] = (mlib_s32)sp[chan1];

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

            p2 = buff[0]; p3 = buff[1]; p4 = buff[2];

            k0 = pk[0]; k1 = pk[1]; k2 = pk[2]; k3 = pk[3];

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

                buffn[i + dx_l    ] = (mlib_s32)sp[0];
                buffn[i + dx_l + 1] = (mlib_s32)sp[chan1];

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

            p2 = buff[0]; p3 = buff[1];
            k0 = pk[0]; k1 = pk[1]; k2 = pk[2];

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

                buffn[i + dx_l    ] = (mlib_s32)sp[0];
                buffn[i + dx_l + 1] = (mlib_s32)sp[chan1];

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

            p2 = buff[0];
            k0 = pk[0]; k1 = pk[1];

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

                buffn[i + dx_l    ] = (mlib_s32)sp[0];
                buffn[i + dx_l + 1] = (mlib_s32)sp[chan1];

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

          } else /* kw == 1 */{

            k0 = pk[0];

            if (l < (n - 1) || off < m) {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = buff[i]; p1 = buff[i + 1];

                buffd[i    ] += p0*k0;
                buffd[i + 1] += p1*k0;
              }

            } else {
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
              for (i = 0; i <= (wid - 2); i += 2) {
                p0 = buff[i]; p1 = buff[i + 1];

                buffn[i + dx_l    ] = (mlib_s32)sp[0];
                buffn[i + dx_l + 1] = (mlib_s32)sp[chan1];

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

          pk += kw;
        }
      }

      /* last pixels */
      for (; i < wid; i++) {
        mlib_s32 *pk = k, x, s = 0;

        for (l = 0; l < n; l++) {
          mlib_s32 *buff = buffc[l] + i;

          for (x = 0; x < m; x++) s += buff[x] * (*pk++);
        }

        STORE_RES(dp[0], s);

        buffn[i + dx_l] = (mlib_s32)sp[0];

        sp += chan1;
        dp += chan1;
      }

      for (; i < swid; i++) {
        buffn[i + dx_l] = (mlib_s32)sp[0];
        sp += chan1;
      }

      for (i = 0; i < dx_l; i++) buffn[i] = buffn[dx_l];
      for (i = 0; i < dx_r; i++) buffn[swid + dx_l + i] = buffn[swid + dx_l - 1];

      /* next line */

      if (j < hgt - dy_b - 2) sl += sll;
      dl += dll;

      buff_ind++;

      if (buff_ind >= n + 1) buff_ind = 0;
    }
  }

  if (pbuff != buff) mlib_free(pbuff);
  if (k != k_locl) mlib_free(k);

  return MLIB_SUCCESS;
}

#endif /* __sparc ( for x86, using integer multiplies is faster ) */

/***************************************************************/
