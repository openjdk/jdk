/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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
 *      Internal functions for mlib_ImageConv* on U8 type
 *      and MLIB_EDGE_DST_NO_WRITE mask
 *
 */

/***************************************************************/

#include <vis_proto.h>
#include <mlib_image.h>
#include <mlib_ImageCheck.h>
#include <mlib_ImageColormap.h>

/*
  This defines switches between functions in
  files: mlib_v_ImageConv_8nw.c,
         mlib_v_ImageConvIndex3_8_8nw.c,
         mlib_v_ImageConvIndex4_8_8nw.c,
         mlib_v_ImageConvIndex3_8_16nw.c,
         mlib_v_ImageConvIndex4_8_16nw.c
*/

#define CONV_INDEX

#define DTYPE mlib_u8
#define LTYPE mlib_u8

/***************************************************************/

#ifdef CONV_INDEX

#define CONV_FUNC(KERN)                                 \
  mlib_conv##KERN##_Index3_8_8nw(mlib_image *dst,       \
                                 mlib_image *src,       \
                                 mlib_s32   *kern,      \
                                 mlib_s32   scale,      \
                                 void       *colormap)

#else

#define CONV_FUNC(KERN)                         \
  mlib_conv##KERN##_8nw_f(mlib_image *dst,      \
                          mlib_image *src,      \
                          mlib_s32   *kern,     \
                          mlib_s32   scale)

#endif

/***************************************************************/

#ifdef CONV_INDEX

#define NCHAN  3

#else

#define NCHAN  nchan

#endif

/***************************************************************/

#define DEF_VARS                                                \
  DTYPE    *sl, *sp, *dl;                                       \
  mlib_s32 hgt = mlib_ImageGetHeight(src);                      \
  mlib_s32 wid = mlib_ImageGetWidth(src);                       \
  mlib_s32 sll = mlib_ImageGetStride(src) / sizeof(DTYPE);      \
  mlib_s32 dll = mlib_ImageGetStride(dst) / sizeof(DTYPE);      \
  DTYPE    *adr_src = (DTYPE *)mlib_ImageGetData(src);          \
  DTYPE    *adr_dst = (DTYPE *)mlib_ImageGetData(dst);          \
  mlib_s32 ssize, xsize, dsize, esize, emask, buff_ind = 0;     \
  mlib_d64 *pbuff, *dp;                                         \
  mlib_f32 *karr = (mlib_f32 *)kern;                            \
  mlib_s32 gsr_scale = (31 - scale) << 3;                       \
  mlib_d64 drnd = vis_to_double_dup(mlib_round_8[31 - scale]);  \
  mlib_s32 i, j, l

/***************************************************************/

#ifdef CONV_INDEX

#define DEF_EXTRA_VARS                                                  \
  int    offset = mlib_ImageGetLutOffset(colormap);                     \
  LTYPE  **lut_table = (LTYPE**)mlib_ImageGetLutData(colormap);         \
  LTYPE  *ltbl0 = lut_table[0] - offset;                                \
  LTYPE  *ltbl1 = lut_table[1] - offset;                                \
  LTYPE  *ltbl2 = lut_table[2] - offset;                                \
  LTYPE  *ltbl3 = (NCHAN > 3) ? lut_table[3] - offset : ltbl2

#else

#define DEF_EXTRA_VARS                          \
  mlib_s32 nchan = mlib_ImageGetChannels(dst)

#endif

/***************************************************************/

#if NCHAN == 3

#define LOAD_SRC() {                                            \
    mlib_s32 s0 = sp[0], s1 = sp[1], s2 = sp[2], s3 = sp[3];    \
    mlib_s32 s4 = sp[4], s5 = sp[5], s6 = sp[6], s7 = sp[7];    \
    mlib_d64 t0, t1, t2;                                        \
                                                                \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl2, s7), t2);            \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl1, s7), t2);            \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl0, s7), t2);            \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl2, s6), t2);            \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl1, s6), t2);            \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl0, s6), t2);            \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl2, s5), t2);            \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl1, s5), t2);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl0, s5), t1);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl2, s4), t1);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl1, s4), t1);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl0, s4), t1);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl2, s3), t1);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl1, s3), t1);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl0, s3), t1);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl2, s2), t1);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl1, s2), t0);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl0, s2), t0);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl2, s1), t0);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl1, s1), t0);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl0, s1), t0);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl2, s0), t0);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl1, s0), t0);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl0, s0), t0);            \
                                                                \
    buffn[i] = t0;                                              \
    buffn[i + 1] = t1;                                          \
    buffn[i + 2] = t2;                                          \
                                                                \
    sp += 8;                                                    \
  }

#else

#define LOAD_SRC() {                                            \
    mlib_s32 s0 = sp[0], s1 = sp[1], s2 = sp[2], s3 = sp[3];    \
    mlib_s32 s4 = sp[4], s5 = sp[5], s6 = sp[6], s7 = sp[7];    \
    mlib_d64 t0, t1, t2;                                        \
                                                                \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl3, s5), t2);            \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl2, s5), t2);            \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl1, s5), t2);            \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl0, s5), t2);            \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl3, s4), t2);            \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl2, s4), t2);            \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl1, s4), t2);            \
    t2 = vis_faligndata(vis_ld_u8_i(ltbl0, s4), t2);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl3, s3), t1);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl2, s3), t1);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl1, s3), t1);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl0, s3), t1);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl3, s2), t1);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl2, s2), t1);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl1, s2), t1);            \
    t1 = vis_faligndata(vis_ld_u8_i(ltbl0, s2), t1);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl3, s1), t0);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl2, s1), t0);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl1, s1), t0);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl0, s1), t0);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl3, s0), t0);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl2, s0), t0);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl1, s0), t0);            \
    t0 = vis_faligndata(vis_ld_u8_i(ltbl0, s0), t0);            \
                                                                \
    buffn[i] = t0;                                              \
    buffn[i + 1] = t1;                                          \
    buffn[i + 2] = t2;                                          \
                                                                \
    sp += 6;                                                    \
  }

#endif

/***************************************************************/

static mlib_s32 mlib_round_8[16] = { 0x00400040, 0x00200020, 0x00100010, 0x00080008,
                                    0x00040004, 0x00020002, 0x00010001, 0x00000000,
                                    0x00000000, 0x00000000, 0x00000000, 0x00000000,
                                    0x00000000, 0x00000000, 0x00000000, 0x00000000 };

/***************************************************************/

void mlib_ImageCopy_na(mlib_u8 *sa, mlib_u8 *da, int size);

/***************************************************************/

#define KSIZE  2

mlib_status CONV_FUNC(2x2)
{
  mlib_d64 *buffs[2*(KSIZE + 1)];
  mlib_d64 *buff0, *buff1, *buffn, *buffd, *buffe;
  mlib_d64 s00, s01, s10, s11, s0, s1;
  mlib_d64 d0, d1, d00, d01, d10, d11;
  DEF_VARS;
  DEF_EXTRA_VARS;

  sl = adr_src;
  dl = adr_dst;

  ssize = NCHAN*wid;
  dsize = (ssize + 7)/8;
  esize = dsize + 4;
  pbuff = mlib_malloc((KSIZE + 4)*esize*sizeof(mlib_d64));
  if (pbuff == NULL) return MLIB_FAILURE;

  for (i = 0; i < (KSIZE + 1); i++) buffs[i] = pbuff + i*esize;
  for (i = 0; i < (KSIZE + 1); i++) buffs[(KSIZE + 1) + i] = buffs[i];
  buffd = buffs[KSIZE] + esize;
  buffe = buffd + 2*esize;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);
  xsize = ssize - NCHAN*(KSIZE - 1);
  emask = (0xFF00 >> (xsize & 7)) & 0xFF;

  vis_write_gsr(gsr_scale + 7);

  for (l = 0; l < KSIZE; l++) {
    mlib_d64 *buffn = buffs[l];
    sp = sl + l*sll;

#ifndef CONV_INDEX
    if ((mlib_addr)sp & 7) mlib_ImageCopy_na((void*)sp, (void*)buffn, ssize);

#else
#pragma pipeloop(0)
    for (i = 0; i < dsize; i += 3) {
      LOAD_SRC();
    }
#endif /* CONV_INDEX */
  }

  for (j = 0; j < hgt; j++) {
    mlib_d64 **buffc = buffs + buff_ind;
    mlib_f32 *pk = karr, k0, k1;
    sp = sl + KSIZE*sll;

    buff0 = buffc[0];
    buff1 = buffc[1];
    buffn = buffc[KSIZE];

#ifndef CONV_INDEX
    if ((((mlib_addr)(sl      )) & 7) == 0) buff0 = (mlib_d64*)sl;
    if ((((mlib_addr)(sl + sll)) & 7) == 0) buff1 = (mlib_d64*)(sl + sll);
    if ((mlib_addr)sp & 7) mlib_ImageCopy_na((void*)sp, (void*)buffn, ssize);
#endif

    k0 = pk[1];
    k1 = pk[3];
    vis_write_gsr(gsr_scale + NCHAN);

    s01 = buff0[0];
    s11 = buff1[0];
#pragma pipeloop(0)
    for (i = 0; i < (xsize + 7)/8; i++) {
      s00 = s01;
      s10 = s11;
      s01 = buff0[i + 1];
      s11 = buff1[i + 1];
      s0  = vis_faligndata(s00, s01);
      s1  = vis_faligndata(s10, s11);

      d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
      d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
      d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
      d11 = vis_fmul8x16au(vis_read_lo(s1), k1);

      d0 = vis_fpadd16(d00, d10);
      d1 = vis_fpadd16(d01, d11);
      buffd[2*i] = d0;
      buffd[2*i + 1] = d1;
    }

    k0 = pk[0];
    k1 = pk[2];
#ifndef CONV_INDEX
    dp = ((mlib_addr)dl & 7) ? buffe : (mlib_d64*)dl;

#pragma pipeloop(0)
    for (i = 0; i < xsize/8; i++) {
      s0 = buff0[i];
      s1 = buff1[i];

      d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
      d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
      d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
      d11 = vis_fmul8x16au(vis_read_lo(s1), k1);

      d0 = buffd[2*i];
      d1 = buffd[2*i + 1];
      d00 = vis_fpadd16(d00, d10);
      d0  = vis_fpadd16(d0, drnd);
      d0  = vis_fpadd16(d0, d00);
      d01 = vis_fpadd16(d01, d11);
      d1  = vis_fpadd16(d1, drnd);
      d1  = vis_fpadd16(d1, d01);
      dp[i] = vis_fpack16_pair(d0, d1);
    }

    if (emask) {
      s0 = buff0[i];
      s1 = buff1[i];

      d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
      d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
      d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
      d11 = vis_fmul8x16au(vis_read_lo(s1), k1);

      d0 = buffd[2*i];
      d1 = buffd[2*i + 1];
      d00 = vis_fpadd16(d00, d10);
      d0  = vis_fpadd16(d0, drnd);
      d0  = vis_fpadd16(d0, d00);
      d01 = vis_fpadd16(d01, d11);
      d1  = vis_fpadd16(d1, drnd);
      d1  = vis_fpadd16(d1, d01);

      d0 = vis_fpack16_pair(d0, d1);
      vis_pst_8(d0, dp + i, emask);
    }

    if ((mlib_u8*)dp != dl) mlib_ImageCopy_na((void*)buffe, dl, xsize);

#else
    vis_write_gsr(gsr_scale + 7);

#pragma pipeloop(0)
    for (i = 0; i < dsize; i += 3) {
      mlib_d64 d00, d01, d02, d03, d04, d05;
      mlib_d64 d10, d11, d12, d13, d14, d15;
      mlib_d64 d0, d1, d2, d3, d4, d5;
      mlib_d64 s00 = buff0[i];
      mlib_d64 s01 = buff0[i + 1];
      mlib_d64 s02 = buff0[i + 2];
      mlib_d64 s10 = buff1[i];
      mlib_d64 s11 = buff1[i + 1];
      mlib_d64 s12 = buff1[i + 2];

      d00 = vis_fmul8x16au(vis_read_hi(s00), k0);
      d01 = vis_fmul8x16au(vis_read_lo(s00), k0);
      d02 = vis_fmul8x16au(vis_read_hi(s01), k0);
      d03 = vis_fmul8x16au(vis_read_lo(s01), k0);
      d04 = vis_fmul8x16au(vis_read_hi(s02), k0);
      d05 = vis_fmul8x16au(vis_read_lo(s02), k0);
      d10 = vis_fmul8x16au(vis_read_hi(s10), k1);
      d11 = vis_fmul8x16au(vis_read_lo(s10), k1);
      d12 = vis_fmul8x16au(vis_read_hi(s11), k1);
      d13 = vis_fmul8x16au(vis_read_lo(s11), k1);
      d14 = vis_fmul8x16au(vis_read_hi(s12), k1);
      d15 = vis_fmul8x16au(vis_read_lo(s12), k1);

      d0 = buffd[2*i];
      d1 = buffd[2*i + 1];
      d2 = buffd[2*i + 2];
      d3 = buffd[2*i + 3];
      d4 = buffd[2*i + 4];
      d5 = buffd[2*i + 5];
      d00 = vis_fpadd16(d00, d10);
      d0  = vis_fpadd16(d0, drnd);
      d0  = vis_fpadd16(d0, d00);
      d01 = vis_fpadd16(d01, d11);
      d1  = vis_fpadd16(d1, drnd);
      d1  = vis_fpadd16(d1, d01);
      d02 = vis_fpadd16(d02, d12);
      d2  = vis_fpadd16(d2, drnd);
      d2  = vis_fpadd16(d2, d02);
      d03 = vis_fpadd16(d03, d13);
      d3  = vis_fpadd16(d3, drnd);
      d3  = vis_fpadd16(d3, d03);
      d04 = vis_fpadd16(d04, d14);
      d4  = vis_fpadd16(d4, drnd);
      d4  = vis_fpadd16(d4, d04);
      d05 = vis_fpadd16(d05, d15);
      d5  = vis_fpadd16(d5, drnd);
      d5  = vis_fpadd16(d5, d05);

      buffe[i    ] = vis_fpack16_pair(d0, d1);
      buffe[i + 1] = vis_fpack16_pair(d2, d3);
      buffe[i + 2] = vis_fpack16_pair(d4, d5);

      LOAD_SRC();
    }

    mlib_ImageColorTrue2IndexLine_U8_U8_3((void*)buffe, dl, wid, colormap);
#endif /* CONV_INDEX */

    sl += sll;
    dl += dll;

    buff_ind++;
    if (buff_ind >= (KSIZE + 1)) buff_ind = 0;
  }

  mlib_free(pbuff);

  return MLIB_SUCCESS;
}

/***************************************************************/

#undef  KSIZE
#define KSIZE  3

mlib_status CONV_FUNC(3x3)
{
  mlib_d64 *buffs[2*(KSIZE + 1)];
  mlib_d64 *buff0, *buff1, *buff2, *buffn, *buffd, *buffe;
  mlib_d64 s00, s01, s10, s11, s20, s21, s0, s1, s2;
  mlib_d64 dd, d0, d1, d00, d01, d10, d11, d20, d21;
  mlib_s32 ik, ik_last, off, doff;
  DEF_VARS;
  DEF_EXTRA_VARS;

  sl = adr_src;
#ifdef CONV_INDEX
  dl = adr_dst + ((KSIZE - 1)/2)*(dll + 1);
#else
  dl = adr_dst + ((KSIZE - 1)/2)*(dll + NCHAN);
#endif

  ssize = NCHAN*wid;
  dsize = (ssize + 7)/8;
  esize = dsize + 4;
  pbuff = mlib_malloc((KSIZE + 4)*esize*sizeof(mlib_d64));
  if (pbuff == NULL) return MLIB_FAILURE;

  for (i = 0; i < (KSIZE + 1); i++) buffs[i] = pbuff + i*esize;
  for (i = 0; i < (KSIZE + 1); i++) buffs[(KSIZE + 1) + i] = buffs[i];
  buffd = buffs[KSIZE] + esize;
  buffe = buffd + 2*esize;

  wid -= (KSIZE - 1);
  hgt -= (KSIZE - 1);
  xsize = ssize - NCHAN*(KSIZE - 1);
  emask = (0xFF00 >> (xsize & 7)) & 0xFF;

  vis_write_gsr(gsr_scale + 7);

  for (l = 0; l < KSIZE; l++) {
    mlib_d64 *buffn = buffs[l];
    sp = sl + l*sll;

#ifndef CONV_INDEX
    if ((mlib_addr)sp & 7) mlib_ImageCopy_na((void*)sp, (void*)buffn, ssize);
#else
#pragma pipeloop(0)
    for (i = 0; i < dsize; i += 3) {
      LOAD_SRC();
    }
#endif /* CONV_INDEX */
  }

  /* init buffer */
#pragma pipeloop(0)
  for (i = 0; i < (xsize + 7)/8; i++) {
    buffd[2*i    ] = drnd;
    buffd[2*i + 1] = drnd;
  }

  for (j = 0; j < hgt; j++) {
    mlib_d64 **buffc = buffs + buff_ind, *pbuff0, *pbuff1, *pbuff2;
    mlib_f32 *pk = karr, k0, k1, k2;
    sp = sl + KSIZE*sll;

    pbuff0 = buffc[0];
    pbuff1 = buffc[1];
    pbuff2 = buffc[2];
    buffn  = buffc[KSIZE];

#ifndef CONV_INDEX
    if ((((mlib_addr)(sl        )) & 7) == 0) pbuff0 = (mlib_d64*)sl;
    if ((((mlib_addr)(sl +   sll)) & 7) == 0) pbuff1 = (mlib_d64*)(sl + sll);
    if ((((mlib_addr)(sl + 2*sll)) & 7) == 0) pbuff2 = (mlib_d64*)(sl + 2*sll);

    if ((mlib_addr)sp & 7) mlib_ImageCopy_na((void*)sp, (void*)buffn, ssize);
#endif

#ifdef CONV_INDEX
    ik_last = 0;
#else
    ik_last = (KSIZE - 1);
#endif

    for (ik = 0; ik < KSIZE; ik++) {
      k0 = pk[ik];
      k1 = pk[ik + KSIZE];
      k2 = pk[ik + 2*KSIZE];

      off  = ik*NCHAN;
      doff = off/8;
      off &= 7;
      buff0 = pbuff0 + doff;
      buff1 = pbuff1 + doff;
      buff2 = pbuff2 + doff;
      vis_write_gsr(gsr_scale + off);

      if (ik == ik_last) continue;
      /*if (!ik_last) {
        if ((off & 3) || (ik == (KSIZE - 1))) {
          ik_last = ik;
          continue;
        }
      }*/

      if (off == 0) {
#pragma pipeloop(0)
        for (i = 0; i < (xsize + 7)/8; i++) {
          s0 = buff0[i];
          s1 = buff1[i];
          s2 = buff2[i];

          d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
          d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
          d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
          d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
          d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
          d21 = vis_fmul8x16au(vis_read_lo(s2), k2);

          d0 = buffd[2*i];
          d1 = buffd[2*i + 1];
          d0 = vis_fpadd16(d00, d0);
          d0 = vis_fpadd16(d10, d0);
          d0 = vis_fpadd16(d20, d0);
          d1 = vis_fpadd16(d01, d1);
          d1 = vis_fpadd16(d11, d1);
          d1 = vis_fpadd16(d21, d1);
          buffd[2*i] = d0;
          buffd[2*i + 1] = d1;
        }

      } else if (off == 4) {
        s01 = buff0[0];
        s11 = buff1[0];
        s21 = buff2[0];
#pragma pipeloop(0)
        for (i = 0; i < (xsize + 7)/8; i++) {
          s00 = s01;
          s10 = s11;
          s20 = s21;
          s01 = buff0[i + 1];
          s11 = buff1[i + 1];
          s21 = buff2[i + 1];

          d00 = vis_fmul8x16au(vis_read_lo(s00), k0);
          d01 = vis_fmul8x16au(vis_read_hi(s01), k0);
          d10 = vis_fmul8x16au(vis_read_lo(s10), k1);
          d11 = vis_fmul8x16au(vis_read_hi(s11), k1);
          d20 = vis_fmul8x16au(vis_read_lo(s20), k2);
          d21 = vis_fmul8x16au(vis_read_hi(s21), k2);

          d0 = buffd[2*i];
          d1 = buffd[2*i + 1];
          d0 = vis_fpadd16(d00, d0);
          d0 = vis_fpadd16(d10, d0);
          d0 = vis_fpadd16(d20, d0);
          d1 = vis_fpadd16(d01, d1);
          d1 = vis_fpadd16(d11, d1);
          d1 = vis_fpadd16(d21, d1);
          buffd[2*i] = d0;
          buffd[2*i + 1] = d1;
        }

      } else {
        s01 = buff0[0];
        s11 = buff1[0];
        s21 = buff2[0];
#pragma pipeloop(0)
        for (i = 0; i < (xsize + 7)/8; i++) {
          s00 = s01;
          s10 = s11;
          s20 = s21;
          s01 = buff0[i + 1];
          s11 = buff1[i + 1];
          s21 = buff2[i + 1];
          s0  = vis_faligndata(s00, s01);
          s1  = vis_faligndata(s10, s11);
          s2  = vis_faligndata(s20, s21);

          d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
          d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
          d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
          d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
          d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
          d21 = vis_fmul8x16au(vis_read_lo(s2), k2);

          d0 = buffd[2*i];
          d1 = buffd[2*i + 1];
          d0 = vis_fpadd16(d00, d0);
          d0 = vis_fpadd16(d10, d0);
          d0 = vis_fpadd16(d20, d0);
          d1 = vis_fpadd16(d01, d1);
          d1 = vis_fpadd16(d11, d1);
          d1 = vis_fpadd16(d21, d1);
          buffd[2*i] = d0;
          buffd[2*i + 1] = d1;
        }
      }
    }

    k0 = pk[ik_last];
    k1 = pk[ik_last + KSIZE];
    k2 = pk[ik_last + 2*KSIZE];

    off  = ik_last*NCHAN;
    doff = off/8;
    off &= 7;
    buff0 = pbuff0 + doff;
    buff1 = pbuff1 + doff;
    buff2 = pbuff2 + doff;
    vis_write_gsr(gsr_scale + off);

#ifndef CONV_INDEX
    dp = ((mlib_addr)dl & 7) ? buffe : (mlib_d64*)dl;

    s01 = buff0[0];
    s11 = buff1[0];
    s21 = buff2[0];
#pragma pipeloop(0)
    for (i = 0; i < xsize/8; i++) {
      s00 = s01;
      s10 = s11;
      s20 = s21;
      s01 = buff0[i + 1];
      s11 = buff1[i + 1];
      s21 = buff2[i + 1];
      s0  = vis_faligndata(s00, s01);
      s1  = vis_faligndata(s10, s11);
      s2  = vis_faligndata(s20, s21);

      d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
      d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
      d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
      d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
      d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
      d21 = vis_fmul8x16au(vis_read_lo(s2), k2);

      d0 = buffd[2*i];
      d1 = buffd[2*i + 1];
      d0 = vis_fpadd16(d0, d00);
      d0 = vis_fpadd16(d0, d10);
      d0 = vis_fpadd16(d0, d20);
      d1 = vis_fpadd16(d1, d01);
      d1 = vis_fpadd16(d1, d11);
      d1 = vis_fpadd16(d1, d21);

      dd = vis_fpack16_pair(d0, d1);
      dp[i] = dd;

      buffd[2*i    ] = drnd;
      buffd[2*i + 1] = drnd;
    }

    if (emask) {
      s00 = s01;
      s10 = s11;
      s20 = s21;
      s01 = buff0[i + 1];
      s11 = buff1[i + 1];
      s21 = buff2[i + 1];
      s0  = vis_faligndata(s00, s01);
      s1  = vis_faligndata(s10, s11);
      s2  = vis_faligndata(s20, s21);

      d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
      d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
      d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
      d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
      d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
      d21 = vis_fmul8x16au(vis_read_lo(s2), k2);

      d0 = buffd[2*i];
      d1 = buffd[2*i + 1];
      d0 = vis_fpadd16(d0, d00);
      d0 = vis_fpadd16(d0, d10);
      d0 = vis_fpadd16(d0, d20);
      d1 = vis_fpadd16(d1, d01);
      d1 = vis_fpadd16(d1, d11);
      d1 = vis_fpadd16(d1, d21);

      dd = vis_fpack16_pair(d0, d1);
      vis_pst_8(dd, dp + i, emask);

      buffd[2*i    ] = drnd;
      buffd[2*i + 1] = drnd;
    }

    if ((mlib_u8*)dp != dl) mlib_ImageCopy_na((void*)buffe, dl, xsize);

#else
    vis_write_gsr(gsr_scale + 7);

#pragma pipeloop(0)
    for (i = 0; i < dsize; i += 3) {
      mlib_d64 d00, d01, d02, d03, d04, d05;
      mlib_d64 d10, d11, d12, d13, d14, d15;
      mlib_d64 d20, d21, d22, d23, d24, d25;
      mlib_d64 d0, d1, d2, d3, d4, d5;
      mlib_d64 s00 = buff0[i];
      mlib_d64 s01 = buff0[i + 1];
      mlib_d64 s02 = buff0[i + 2];
      mlib_d64 s10 = buff1[i];
      mlib_d64 s11 = buff1[i + 1];
      mlib_d64 s12 = buff1[i + 2];
      mlib_d64 s20 = buff2[i];
      mlib_d64 s21 = buff2[i + 1];
      mlib_d64 s22 = buff2[i + 2];

      d00 = vis_fmul8x16au(vis_read_hi(s00), k0);
      d01 = vis_fmul8x16au(vis_read_lo(s00), k0);
      d02 = vis_fmul8x16au(vis_read_hi(s01), k0);
      d03 = vis_fmul8x16au(vis_read_lo(s01), k0);
      d04 = vis_fmul8x16au(vis_read_hi(s02), k0);
      d05 = vis_fmul8x16au(vis_read_lo(s02), k0);
      d10 = vis_fmul8x16au(vis_read_hi(s10), k1);
      d11 = vis_fmul8x16au(vis_read_lo(s10), k1);
      d12 = vis_fmul8x16au(vis_read_hi(s11), k1);
      d13 = vis_fmul8x16au(vis_read_lo(s11), k1);
      d14 = vis_fmul8x16au(vis_read_hi(s12), k1);
      d15 = vis_fmul8x16au(vis_read_lo(s12), k1);
      d20 = vis_fmul8x16au(vis_read_hi(s20), k2);
      d21 = vis_fmul8x16au(vis_read_lo(s20), k2);
      d22 = vis_fmul8x16au(vis_read_hi(s21), k2);
      d23 = vis_fmul8x16au(vis_read_lo(s21), k2);
      d24 = vis_fmul8x16au(vis_read_hi(s22), k2);
      d25 = vis_fmul8x16au(vis_read_lo(s22), k2);

      d0 = buffd[2*i];
      d1 = buffd[2*i + 1];
      d2 = buffd[2*i + 2];
      d3 = buffd[2*i + 3];
      d4 = buffd[2*i + 4];
      d5 = buffd[2*i + 5];
      d0 = vis_fpadd16(d0, d00);
      d0 = vis_fpadd16(d0, d10);
      d0 = vis_fpadd16(d0, d20);
      d1 = vis_fpadd16(d1, d01);
      d1 = vis_fpadd16(d1, d11);
      d1 = vis_fpadd16(d1, d21);
      d2 = vis_fpadd16(d2, d02);
      d2 = vis_fpadd16(d2, d12);
      d2 = vis_fpadd16(d2, d22);
      d3 = vis_fpadd16(d3, d03);
      d3 = vis_fpadd16(d3, d13);
      d3 = vis_fpadd16(d3, d23);
      d4 = vis_fpadd16(d4, d04);
      d4 = vis_fpadd16(d4, d14);
      d4 = vis_fpadd16(d4, d24);
      d5 = vis_fpadd16(d5, d05);
      d5 = vis_fpadd16(d5, d15);
      d5 = vis_fpadd16(d5, d25);

      buffe[i    ] = vis_fpack16_pair(d0, d1);
      buffe[i + 1] = vis_fpack16_pair(d2, d3);
      buffe[i + 2] = vis_fpack16_pair(d4, d5);

      buffd[2*i    ] = drnd;
      buffd[2*i + 1] = drnd;
      buffd[2*i + 2] = drnd;
      buffd[2*i + 3] = drnd;
      buffd[2*i + 4] = drnd;
      buffd[2*i + 5] = drnd;

      LOAD_SRC();
    }

    mlib_ImageColorTrue2IndexLine_U8_U8_3((void*)buffe, dl, wid, colormap);
#endif /* CONV_INDEX */

    sl += sll;
    dl += dll;

    buff_ind++;
    if (buff_ind >= (KSIZE + 1)) buff_ind = 0;
  }

  mlib_free(pbuff);

  return MLIB_SUCCESS;
}

/***************************************************************/

#undef  KSIZE
#define MAX_N   11

#ifdef CONV_INDEX

mlib_status mlib_convMxN_Index3_8_8nw(mlib_image *dst,
                                      mlib_image *src,
                                      mlib_s32   m,
                                      mlib_s32   n,
                                      mlib_s32   dm,
                                      mlib_s32   dn,
                                      mlib_s32   *kern,
                                      mlib_s32   scale,
                                      void       *colormap)

#else

mlib_status mlib_convMxN_8nw_f(mlib_image *dst,
                               mlib_image *src,
                               mlib_s32   m,
                               mlib_s32   n,
                               mlib_s32   dm,
                               mlib_s32   dn,
                               mlib_s32   *kern,
                               mlib_s32   scale)

#endif
{
  mlib_d64 *buffs_local[3*(MAX_N + 1)], **buffs = buffs_local, **buff;
  mlib_d64 *buff0, *buff1, *buff2, *buff3, *buffn, *buffd, *buffe;
  mlib_d64 s00, s01, s10, s11, s20, s21, s30, s31, s0, s1, s2, s3;
  mlib_d64 d00, d01, d10, d11, d20, d21, d30, d31;
  mlib_d64 dd, d0, d1;
  mlib_s32 ik, jk, ik_last, jk_size, coff, off, doff;
  DEF_VARS;
  DEF_EXTRA_VARS;

  if (n > MAX_N) {
    buffs = mlib_malloc(3*(n + 1)*sizeof(mlib_d64*));
    if (buffs == NULL) return MLIB_FAILURE;
  }

  buff = buffs + 2*(n + 1);

  sl = adr_src;
#ifdef CONV_INDEX
  dl = adr_dst + dn*dll + dm;
#else
  dl = adr_dst + dn*dll + dm*NCHAN;
#endif

  ssize = NCHAN*wid;
  dsize = (ssize + 7)/8;
  esize = dsize + 4;
  pbuff = mlib_malloc((n + 4)*esize*sizeof(mlib_d64));
  if (pbuff == NULL) {
    if (buffs != buffs_local) mlib_free(buffs);
    return MLIB_FAILURE;
  }

  for (i = 0; i < (n + 1); i++) buffs[i] = pbuff + i*esize;
  for (i = 0; i < (n + 1); i++) buffs[(n + 1) + i] = buffs[i];
  buffd = buffs[n] + esize;
  buffe = buffd + 2*esize;

  wid -= (m - 1);
  hgt -= (n - 1);
  xsize = ssize - NCHAN*(m - 1);
  emask = (0xFF00 >> (xsize & 7)) & 0xFF;

  vis_write_gsr(gsr_scale + 7);

  for (l = 0; l < n; l++) {
    mlib_d64 *buffn = buffs[l];
    sp = sl + l*sll;

#ifndef CONV_INDEX
    if ((mlib_addr)sp & 7) mlib_ImageCopy_na((void*)sp, (void*)buffn, ssize);
#else
#pragma pipeloop(0)
    for (i = 0; i < dsize; i += 3) {
      LOAD_SRC();
    }
#endif /* CONV_INDEX */
  }

  /* init buffer */
#pragma pipeloop(0)
  for (i = 0; i < (xsize + 7)/8; i++) {
    buffd[2*i    ] = drnd;
    buffd[2*i + 1] = drnd;
  }

  for (j = 0; j < hgt; j++) {
    mlib_d64 **buffc = buffs + buff_ind;
    mlib_f32 *pk = karr, k0, k1, k2, k3;
    sp = sl + n*sll;

    for (l = 0; l < n; l++) {
      buff[l] = buffc[l];
    }
    buffn  = buffc[n];

#ifndef CONV_INDEX
    for (l = 0; l < n; l++) {
      if ((((mlib_addr)(sl + l*sll)) & 7) == 0) buff[l] = (mlib_d64*)(sl + l*sll);
    }
    if ((mlib_addr)sp & 7) mlib_ImageCopy_na((void*)sp, (void*)buffn, ssize);
#endif

#ifdef CONV_INDEX
    ik_last = 0;
#else
    ik_last = (m - 1);
#endif

    for (jk = 0; jk < n; jk += jk_size) {
      jk_size = n - jk;
#ifdef CONV_INDEX
      if (jk_size >= 5) jk_size = 3;
      if (jk_size == 4) jk_size = 2;
#else
      if (jk_size >= 6) jk_size = 4;
      if (jk_size == 5) jk_size = 3;
#endif
      coff = 0;

      if (jk_size == 2) {

        for (ik = 0; ik < m; ik++, coff += NCHAN) {
          if (!jk && ik == ik_last) continue;

          k0 = pk[ik];
          k1 = pk[ik + m];

          doff  = coff/8;
          buff0 = buff[jk    ] + doff;
          buff1 = buff[jk + 1] + doff;

          off = coff & 7;
          vis_write_gsr(gsr_scale + off);

          s01 = buff0[0];
          s11 = buff1[0];
#pragma pipeloop(0)
          for (i = 0; i < (xsize + 7)/8; i++) {
            s00 = s01;
            s10 = s11;
            s01 = buff0[i + 1];
            s11 = buff1[i + 1];
            s0  = vis_faligndata(s00, s01);
            s1  = vis_faligndata(s10, s11);

            d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
            d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
            d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
            d11 = vis_fmul8x16au(vis_read_lo(s1), k1);

            d0 = buffd[2*i];
            d1 = buffd[2*i + 1];
            d0 = vis_fpadd16(d00, d0);
            d0 = vis_fpadd16(d10, d0);
            d1 = vis_fpadd16(d01, d1);
            d1 = vis_fpadd16(d11, d1);
            buffd[2*i] = d0;
            buffd[2*i + 1] = d1;
          }

        }

        pk += 2*m;

      } else if (jk_size == 3) {

        for (ik = 0; ik < m; ik++, coff += NCHAN) {
          if (!jk && ik == ik_last) continue;

          k0 = pk[ik];
          k1 = pk[ik + m];
          k2 = pk[ik + 2*m];

          doff  = coff/8;
          buff0 = buff[jk    ] + doff;
          buff1 = buff[jk + 1] + doff;
          buff2 = buff[jk + 2] + doff;

          off = coff & 7;
          vis_write_gsr(gsr_scale + off);

          if (off == 0) {
#pragma pipeloop(0)
            for (i = 0; i < (xsize + 7)/8; i++) {
              d0 = buffd[2*i];
              d1 = buffd[2*i + 1];

              s0 = buff0[i];
              s1 = buff1[i];
              s2 = buff2[i];

              d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
              d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
              d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
              d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
              d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
              d21 = vis_fmul8x16au(vis_read_lo(s2), k2);

              d00 = vis_fpadd16(d00, d10);
              d0  = vis_fpadd16(d20, d0);
              d0  = vis_fpadd16(d00, d0);
              d01 = vis_fpadd16(d01, d11);
              d1  = vis_fpadd16(d21, d1);
              d1  = vis_fpadd16(d01, d1);
              buffd[2*i] = d0;
              buffd[2*i + 1] = d1;
            }

          } else if (off == 4) {
            s01 = buff0[0];
            s11 = buff1[0];
            s21 = buff2[0];
#pragma pipeloop(0)
            for (i = 0; i < (xsize + 7)/8; i++) {
              d0 = buffd[2*i];
              d1 = buffd[2*i + 1];

              s00 = s01;
              s10 = s11;
              s20 = s21;
              s01 = buff0[i + 1];
              s11 = buff1[i + 1];
              s21 = buff2[i + 1];

              d00 = vis_fmul8x16au(vis_read_lo(s00), k0);
              d01 = vis_fmul8x16au(vis_read_hi(s01), k0);
              d10 = vis_fmul8x16au(vis_read_lo(s10), k1);
              d11 = vis_fmul8x16au(vis_read_hi(s11), k1);
              d20 = vis_fmul8x16au(vis_read_lo(s20), k2);
              d21 = vis_fmul8x16au(vis_read_hi(s21), k2);

              d00 = vis_fpadd16(d00, d10);
              d0  = vis_fpadd16(d20, d0);
              d0  = vis_fpadd16(d00, d0);
              d01 = vis_fpadd16(d01, d11);
              d1  = vis_fpadd16(d21, d1);
              d1  = vis_fpadd16(d01, d1);
              buffd[2*i] = d0;
              buffd[2*i + 1] = d1;
            }

          } else {
            s01 = buff0[0];
            s11 = buff1[0];
            s21 = buff2[0];
#pragma pipeloop(0)
            for (i = 0; i < (xsize + 7)/8; i++) {
              d0 = buffd[2*i];
              d1 = buffd[2*i + 1];

              s00 = s01;
              s10 = s11;
              s20 = s21;
              s01 = buff0[i + 1];
              s11 = buff1[i + 1];
              s21 = buff2[i + 1];
              s0  = vis_faligndata(s00, s01);
              s1  = vis_faligndata(s10, s11);
              s2  = vis_faligndata(s20, s21);

              d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
              d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
              d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
              d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
              d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
              d21 = vis_fmul8x16au(vis_read_lo(s2), k2);

              d00 = vis_fpadd16(d00, d10);
              d0  = vis_fpadd16(d20, d0);
              d0  = vis_fpadd16(d00, d0);
              d01 = vis_fpadd16(d01, d11);
              d1  = vis_fpadd16(d21, d1);
              d1  = vis_fpadd16(d01, d1);
              buffd[2*i] = d0;
              buffd[2*i + 1] = d1;
            }
          }
        }

        pk += 3*m;

      } else { /* jk_size == 4 */

        for (ik = 0; ik < m; ik++, coff += NCHAN) {
          if (!jk && ik == ik_last) continue;

          k0 = pk[ik];
          k1 = pk[ik + m];
          k2 = pk[ik + 2*m];
          k3 = pk[ik + 3*m];

          doff  = coff/8;
          buff0 = buff[jk    ] + doff;
          buff1 = buff[jk + 1] + doff;
          buff2 = buff[jk + 2] + doff;
          buff3 = buff[jk + 3] + doff;

          off = coff & 7;
          vis_write_gsr(gsr_scale + off);

          if (off == 0) {

#pragma pipeloop(0)
            for (i = 0; i < (xsize + 7)/8; i++) {
              d0 = buffd[2*i];
              d1 = buffd[2*i + 1];

              s0 = buff0[i];
              s1 = buff1[i];
              s2 = buff2[i];
              s3 = buff3[i];

              d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
              d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
              d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
              d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
              d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
              d21 = vis_fmul8x16au(vis_read_lo(s2), k2);
              d30 = vis_fmul8x16au(vis_read_hi(s3), k3);
              d31 = vis_fmul8x16au(vis_read_lo(s3), k3);

              d00 = vis_fpadd16(d00, d10);
              d20 = vis_fpadd16(d20, d30);
              d0  = vis_fpadd16(d0,  d00);
              d0  = vis_fpadd16(d0,  d20);
              d01 = vis_fpadd16(d01, d11);
              d21 = vis_fpadd16(d21, d31);
              d1  = vis_fpadd16(d1,  d01);
              d1  = vis_fpadd16(d1,  d21);
              buffd[2*i] = d0;
              buffd[2*i + 1] = d1;
            }

          } else if (off == 4) {

            s01 = buff0[0];
            s11 = buff1[0];
            s21 = buff2[0];
            s31 = buff3[0];
#pragma pipeloop(0)
            for (i = 0; i < (xsize + 7)/8; i++) {
              d0 = buffd[2*i];
              d1 = buffd[2*i + 1];

              s00 = s01;
              s10 = s11;
              s20 = s21;
              s30 = s31;
              s01 = buff0[i + 1];
              s11 = buff1[i + 1];
              s21 = buff2[i + 1];
              s31 = buff3[i + 1];

              d00 = vis_fmul8x16au(vis_read_lo(s00), k0);
              d01 = vis_fmul8x16au(vis_read_hi(s01), k0);
              d10 = vis_fmul8x16au(vis_read_lo(s10), k1);
              d11 = vis_fmul8x16au(vis_read_hi(s11), k1);
              d20 = vis_fmul8x16au(vis_read_lo(s20), k2);
              d21 = vis_fmul8x16au(vis_read_hi(s21), k2);
              d30 = vis_fmul8x16au(vis_read_lo(s30), k3);
              d31 = vis_fmul8x16au(vis_read_hi(s31), k3);

              d00 = vis_fpadd16(d00, d10);
              d20 = vis_fpadd16(d20, d30);
              d0  = vis_fpadd16(d0,  d00);
              d0  = vis_fpadd16(d0,  d20);
              d01 = vis_fpadd16(d01, d11);
              d21 = vis_fpadd16(d21, d31);
              d1  = vis_fpadd16(d1,  d01);
              d1  = vis_fpadd16(d1,  d21);
              buffd[2*i] = d0;
              buffd[2*i + 1] = d1;
            }

          } else {

            s01 = buff0[0];
            s11 = buff1[0];
            s21 = buff2[0];
            s31 = buff3[0];
#pragma pipeloop(0)
            for (i = 0; i < (xsize + 7)/8; i++) {
              d0 = buffd[2*i];
              d1 = buffd[2*i + 1];

              s00 = s01;
              s10 = s11;
              s20 = s21;
              s30 = s31;
              s01 = buff0[i + 1];
              s11 = buff1[i + 1];
              s21 = buff2[i + 1];
              s31 = buff3[i + 1];
              s0  = vis_faligndata(s00, s01);
              s1  = vis_faligndata(s10, s11);
              s2  = vis_faligndata(s20, s21);
              s3  = vis_faligndata(s30, s31);

              d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
              d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
              d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
              d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
              d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
              d21 = vis_fmul8x16au(vis_read_lo(s2), k2);
              d30 = vis_fmul8x16au(vis_read_hi(s3), k3);
              d31 = vis_fmul8x16au(vis_read_lo(s3), k3);

              d00 = vis_fpadd16(d00, d10);
              d20 = vis_fpadd16(d20, d30);
              d0  = vis_fpadd16(d0,  d00);
              d0  = vis_fpadd16(d0,  d20);
              d01 = vis_fpadd16(d01, d11);
              d21 = vis_fpadd16(d21, d31);
              d1  = vis_fpadd16(d1,  d01);
              d1  = vis_fpadd16(d1,  d21);
              buffd[2*i] = d0;
              buffd[2*i + 1] = d1;
            }
          }
        }

        pk += 4*m;
      }
    }

    /*****************************************
     *****************************************
     **          Final iteration            **
     *****************************************
     *****************************************/

    jk_size = n;
#ifdef CONV_INDEX
    if (jk_size >= 5) jk_size = 3;
    if (jk_size == 4) jk_size = 2;
#else
    if (jk_size >= 6) jk_size = 4;
    if (jk_size == 5) jk_size = 3;
#endif

    k0 = karr[ik_last];
    k1 = karr[ik_last + m];
    k2 = karr[ik_last + 2*m];
    k3 = karr[ik_last + 3*m];

    off  = ik_last*NCHAN;
    doff = off/8;
    off &= 7;
    buff0 = buff[0] + doff;
    buff1 = buff[1] + doff;
    buff2 = buff[2] + doff;
    buff3 = buff[3] + doff;
    vis_write_gsr(gsr_scale + off);

#ifndef CONV_INDEX
    if (jk_size == 2) {
      dp = ((mlib_addr)dl & 7) ? buffe : (mlib_d64*)dl;

      s01 = buff0[0];
      s11 = buff1[0];
#pragma pipeloop(0)
      for (i = 0; i < xsize/8; i++) {
        s00 = s01;
        s10 = s11;
        s01 = buff0[i + 1];
        s11 = buff1[i + 1];
        s0  = vis_faligndata(s00, s01);
        s1  = vis_faligndata(s10, s11);

        d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
        d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
        d11 = vis_fmul8x16au(vis_read_lo(s1), k1);

        d0 = buffd[2*i];
        d1 = buffd[2*i + 1];
        d0 = vis_fpadd16(d0, d00);
        d0 = vis_fpadd16(d0, d10);
        d1 = vis_fpadd16(d1, d01);
        d1 = vis_fpadd16(d1, d11);

        dd = vis_fpack16_pair(d0, d1);
        dp[i] = dd;

        buffd[2*i    ] = drnd;
        buffd[2*i + 1] = drnd;
      }

      if (emask) {
        s00 = s01;
        s10 = s11;
        s01 = buff0[i + 1];
        s11 = buff1[i + 1];
        s0  = vis_faligndata(s00, s01);
        s1  = vis_faligndata(s10, s11);

        d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
        d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
        d11 = vis_fmul8x16au(vis_read_lo(s1), k1);

        d0 = buffd[2*i];
        d1 = buffd[2*i + 1];
        d0 = vis_fpadd16(d0, d00);
        d0 = vis_fpadd16(d0, d10);
        d1 = vis_fpadd16(d1, d01);
        d1 = vis_fpadd16(d1, d11);

        dd = vis_fpack16_pair(d0, d1);
        vis_pst_8(dd, dp + i, emask);

        buffd[2*i    ] = drnd;
        buffd[2*i + 1] = drnd;
      }

      if ((mlib_u8*)dp != dl) mlib_ImageCopy_na((void*)buffe, dl, xsize);

    } else if (jk_size == 3) {

      dp = ((mlib_addr)dl & 7) ? buffe : (mlib_d64*)dl;

      s01 = buff0[0];
      s11 = buff1[0];
      s21 = buff2[0];
#pragma pipeloop(0)
      for (i = 0; i < xsize/8; i++) {
        s00 = s01;
        s10 = s11;
        s20 = s21;
        s01 = buff0[i + 1];
        s11 = buff1[i + 1];
        s21 = buff2[i + 1];
        s0  = vis_faligndata(s00, s01);
        s1  = vis_faligndata(s10, s11);
        s2  = vis_faligndata(s20, s21);

        d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
        d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
        d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
        d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
        d21 = vis_fmul8x16au(vis_read_lo(s2), k2);

        d0 = buffd[2*i];
        d1 = buffd[2*i + 1];
        d0 = vis_fpadd16(d0, d00);
        d0 = vis_fpadd16(d0, d10);
        d0 = vis_fpadd16(d0, d20);
        d1 = vis_fpadd16(d1, d01);
        d1 = vis_fpadd16(d1, d11);
        d1 = vis_fpadd16(d1, d21);

        dd = vis_fpack16_pair(d0, d1);
        dp[i] = dd;

        buffd[2*i    ] = drnd;
        buffd[2*i + 1] = drnd;
      }

      if (emask) {
        s00 = s01;
        s10 = s11;
        s20 = s21;
        s01 = buff0[i + 1];
        s11 = buff1[i + 1];
        s21 = buff2[i + 1];
        s0  = vis_faligndata(s00, s01);
        s1  = vis_faligndata(s10, s11);
        s2  = vis_faligndata(s20, s21);

        d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
        d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
        d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
        d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
        d21 = vis_fmul8x16au(vis_read_lo(s2), k2);

        d0 = buffd[2*i];
        d1 = buffd[2*i + 1];
        d0 = vis_fpadd16(d0, d00);
        d0 = vis_fpadd16(d0, d10);
        d0 = vis_fpadd16(d0, d20);
        d1 = vis_fpadd16(d1, d01);
        d1 = vis_fpadd16(d1, d11);
        d1 = vis_fpadd16(d1, d21);

        dd = vis_fpack16_pair(d0, d1);
        vis_pst_8(dd, dp + i, emask);

        buffd[2*i    ] = drnd;
        buffd[2*i + 1] = drnd;
      }

      if ((mlib_u8*)dp != dl) mlib_ImageCopy_na((void*)buffe, dl, xsize);

    } else /* if (jk_size == 4) */ {

      dp = ((mlib_addr)dl & 7) ? buffe : (mlib_d64*)dl;

      s01 = buff0[0];
      s11 = buff1[0];
      s21 = buff2[0];
      s31 = buff3[0];
#pragma pipeloop(0)
      for (i = 0; i < xsize/8; i++) {
        s00 = s01;
        s10 = s11;
        s20 = s21;
        s30 = s31;
        s01 = buff0[i + 1];
        s11 = buff1[i + 1];
        s21 = buff2[i + 1];
        s31 = buff3[i + 1];
        s0  = vis_faligndata(s00, s01);
        s1  = vis_faligndata(s10, s11);
        s2  = vis_faligndata(s20, s21);
        s3  = vis_faligndata(s30, s31);

        d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
        d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
        d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
        d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
        d21 = vis_fmul8x16au(vis_read_lo(s2), k2);
        d30 = vis_fmul8x16au(vis_read_hi(s3), k3);
        d31 = vis_fmul8x16au(vis_read_lo(s3), k3);

        d0 = buffd[2*i];
        d1 = buffd[2*i + 1];
        d0 = vis_fpadd16(d0, d00);
        d0 = vis_fpadd16(d0, d10);
        d0 = vis_fpadd16(d0, d20);
        d0 = vis_fpadd16(d0, d30);
        d1 = vis_fpadd16(d1, d01);
        d1 = vis_fpadd16(d1, d11);
        d1 = vis_fpadd16(d1, d21);
        d1 = vis_fpadd16(d1, d31);

        dd = vis_fpack16_pair(d0, d1);
        dp[i] = dd;

        buffd[2*i    ] = drnd;
        buffd[2*i + 1] = drnd;
      }

      if (emask) {
        s00 = s01;
        s10 = s11;
        s20 = s21;
        s30 = s31;
        s01 = buff0[i + 1];
        s11 = buff1[i + 1];
        s21 = buff2[i + 1];
        s31 = buff3[i + 1];
        s0  = vis_faligndata(s00, s01);
        s1  = vis_faligndata(s10, s11);
        s2  = vis_faligndata(s20, s21);
        s3  = vis_faligndata(s30, s31);

        d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
        d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
        d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
        d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
        d21 = vis_fmul8x16au(vis_read_lo(s2), k2);
        d30 = vis_fmul8x16au(vis_read_hi(s3), k3);
        d31 = vis_fmul8x16au(vis_read_lo(s3), k3);

        d0 = buffd[2*i];
        d1 = buffd[2*i + 1];
        d0 = vis_fpadd16(d0, d00);
        d0 = vis_fpadd16(d0, d10);
        d0 = vis_fpadd16(d0, d20);
        d0 = vis_fpadd16(d0, d30);
        d1 = vis_fpadd16(d1, d01);
        d1 = vis_fpadd16(d1, d11);
        d1 = vis_fpadd16(d1, d21);
        d1 = vis_fpadd16(d1, d31);

        dd = vis_fpack16_pair(d0, d1);
        vis_pst_8(dd, dp + i, emask);

        buffd[2*i    ] = drnd;
        buffd[2*i + 1] = drnd;
      }

      if ((mlib_u8*)dp != dl) mlib_ImageCopy_na((void*)buffe, dl, xsize);
    }

#else /* CONV_INDEX */

    if (jk_size == 2) {
      vis_write_gsr(gsr_scale + 7);

#pragma pipeloop(0)
      for (i = 0; i < dsize; i += 3) {
        mlib_d64 d00, d01, d02, d03, d04, d05;
        mlib_d64 d10, d11, d12, d13, d14, d15;
        mlib_d64 d0, d1, d2, d3, d4, d5;
        mlib_d64 s00 = buff0[i];
        mlib_d64 s01 = buff0[i + 1];
        mlib_d64 s02 = buff0[i + 2];
        mlib_d64 s10 = buff1[i];
        mlib_d64 s11 = buff1[i + 1];
        mlib_d64 s12 = buff1[i + 2];

        d00 = vis_fmul8x16au(vis_read_hi(s00), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s00), k0);
        d02 = vis_fmul8x16au(vis_read_hi(s01), k0);
        d03 = vis_fmul8x16au(vis_read_lo(s01), k0);
        d04 = vis_fmul8x16au(vis_read_hi(s02), k0);
        d05 = vis_fmul8x16au(vis_read_lo(s02), k0);
        d10 = vis_fmul8x16au(vis_read_hi(s10), k1);
        d11 = vis_fmul8x16au(vis_read_lo(s10), k1);
        d12 = vis_fmul8x16au(vis_read_hi(s11), k1);
        d13 = vis_fmul8x16au(vis_read_lo(s11), k1);
        d14 = vis_fmul8x16au(vis_read_hi(s12), k1);
        d15 = vis_fmul8x16au(vis_read_lo(s12), k1);

        d0 = buffd[2*i];
        d1 = buffd[2*i + 1];
        d2 = buffd[2*i + 2];
        d3 = buffd[2*i + 3];
        d4 = buffd[2*i + 4];
        d5 = buffd[2*i + 5];
        d0 = vis_fpadd16(d0, d00);
        d0 = vis_fpadd16(d0, d10);
        d1 = vis_fpadd16(d1, d01);
        d1 = vis_fpadd16(d1, d11);
        d2 = vis_fpadd16(d2, d02);
        d2 = vis_fpadd16(d2, d12);
        d3 = vis_fpadd16(d3, d03);
        d3 = vis_fpadd16(d3, d13);
        d4 = vis_fpadd16(d4, d04);
        d4 = vis_fpadd16(d4, d14);
        d5 = vis_fpadd16(d5, d05);
        d5 = vis_fpadd16(d5, d15);

        buffe[i    ] = vis_fpack16_pair(d0, d1);
        buffe[i + 1] = vis_fpack16_pair(d2, d3);
        buffe[i + 2] = vis_fpack16_pair(d4, d5);

        buffd[2*i    ] = drnd;
        buffd[2*i + 1] = drnd;
        buffd[2*i + 2] = drnd;
        buffd[2*i + 3] = drnd;
        buffd[2*i + 4] = drnd;
        buffd[2*i + 5] = drnd;

        LOAD_SRC();
      }

    } else /* if (jk_size == 3) */ {
      vis_write_gsr(gsr_scale + 7);

#pragma pipeloop(0)
      for (i = 0; i < dsize; i += 3) {
        mlib_d64 d00, d01, d02, d03, d04, d05;
        mlib_d64 d10, d11, d12, d13, d14, d15;
        mlib_d64 d20, d21, d22, d23, d24, d25;
        mlib_d64 d0, d1, d2, d3, d4, d5;
        mlib_d64 s00 = buff0[i];
        mlib_d64 s01 = buff0[i + 1];
        mlib_d64 s02 = buff0[i + 2];
        mlib_d64 s10 = buff1[i];
        mlib_d64 s11 = buff1[i + 1];
        mlib_d64 s12 = buff1[i + 2];
        mlib_d64 s20 = buff2[i];
        mlib_d64 s21 = buff2[i + 1];
        mlib_d64 s22 = buff2[i + 2];

        d00 = vis_fmul8x16au(vis_read_hi(s00), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s00), k0);
        d02 = vis_fmul8x16au(vis_read_hi(s01), k0);
        d03 = vis_fmul8x16au(vis_read_lo(s01), k0);
        d04 = vis_fmul8x16au(vis_read_hi(s02), k0);
        d05 = vis_fmul8x16au(vis_read_lo(s02), k0);
        d10 = vis_fmul8x16au(vis_read_hi(s10), k1);
        d11 = vis_fmul8x16au(vis_read_lo(s10), k1);
        d12 = vis_fmul8x16au(vis_read_hi(s11), k1);
        d13 = vis_fmul8x16au(vis_read_lo(s11), k1);
        d14 = vis_fmul8x16au(vis_read_hi(s12), k1);
        d15 = vis_fmul8x16au(vis_read_lo(s12), k1);
        d20 = vis_fmul8x16au(vis_read_hi(s20), k2);
        d21 = vis_fmul8x16au(vis_read_lo(s20), k2);
        d22 = vis_fmul8x16au(vis_read_hi(s21), k2);
        d23 = vis_fmul8x16au(vis_read_lo(s21), k2);
        d24 = vis_fmul8x16au(vis_read_hi(s22), k2);
        d25 = vis_fmul8x16au(vis_read_lo(s22), k2);

        d0 = buffd[2*i];
        d1 = buffd[2*i + 1];
        d2 = buffd[2*i + 2];
        d3 = buffd[2*i + 3];
        d4 = buffd[2*i + 4];
        d5 = buffd[2*i + 5];
        d0 = vis_fpadd16(d0, d00);
        d0 = vis_fpadd16(d0, d10);
        d0 = vis_fpadd16(d0, d20);
        d1 = vis_fpadd16(d1, d01);
        d1 = vis_fpadd16(d1, d11);
        d1 = vis_fpadd16(d1, d21);
        d2 = vis_fpadd16(d2, d02);
        d2 = vis_fpadd16(d2, d12);
        d2 = vis_fpadd16(d2, d22);
        d3 = vis_fpadd16(d3, d03);
        d3 = vis_fpadd16(d3, d13);
        d3 = vis_fpadd16(d3, d23);
        d4 = vis_fpadd16(d4, d04);
        d4 = vis_fpadd16(d4, d14);
        d4 = vis_fpadd16(d4, d24);
        d5 = vis_fpadd16(d5, d05);
        d5 = vis_fpadd16(d5, d15);
        d5 = vis_fpadd16(d5, d25);

        buffe[i    ] = vis_fpack16_pair(d0, d1);
        buffe[i + 1] = vis_fpack16_pair(d2, d3);
        buffe[i + 2] = vis_fpack16_pair(d4, d5);

        buffd[2*i    ] = drnd;
        buffd[2*i + 1] = drnd;
        buffd[2*i + 2] = drnd;
        buffd[2*i + 3] = drnd;
        buffd[2*i + 4] = drnd;
        buffd[2*i + 5] = drnd;

        LOAD_SRC();
      }
    }
#endif /* CONV_INDEX */

#ifdef CONV_INDEX
    mlib_ImageColorTrue2IndexLine_U8_U8_3((void*)buffe, dl, wid, colormap);
#endif /* CONV_INDEX */

    sl += sll;
    dl += dll;

    buff_ind++;
    if (buff_ind >= (n + 1)) buff_ind = 0;
  }

  mlib_free(pbuff);
  if (buffs != buffs_local) mlib_free(buffs);

  return MLIB_SUCCESS;
}

/***************************************************************/
