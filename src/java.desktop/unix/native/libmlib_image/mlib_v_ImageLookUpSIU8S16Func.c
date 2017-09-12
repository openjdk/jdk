/*
 * Copyright (c) 1998, 2003, Oracle and/or its affiliates. All rights reserved.
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



#include "vis_proto.h"
#include "mlib_image.h"
#include "mlib_v_ImageLookUpFunc.h"

/***************************************************************/
static void mlib_v_ImageLookUpSI_U8_S16_2_SrcOff0_D1(const mlib_u8  *src,
                                                     mlib_s16       *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_f32 *table);

static void mlib_v_ImageLookUpSI_U8_S16_2_DstNonAl_D1(const mlib_u8  *src,
                                                      mlib_s16       *dst,
                                                      mlib_s32       xsize,
                                                      const mlib_f32 *table);

static void mlib_v_ImageLookUpSI_U8_S16_2_DstA8D1_SMALL(const mlib_u8  *src,
                                                        mlib_s16       *dst,
                                                        mlib_s32       xsize,
                                                        const mlib_s16 **table);

static void mlib_v_ImageLookUpSI_U8_S16_2_D1_SMALL(const mlib_u8  *src,
                                                   mlib_s16       *dst,
                                                   mlib_s32       xsize,
                                                   const mlib_s16 **table);

static void mlib_v_ImageLookUpSI_U8_S16_3_SrcOff0_D1(const mlib_u8  *src,
                                                     mlib_s16       *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_S16_3_SrcOff1_D1(const mlib_u8  *src,
                                                     mlib_s16       *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_S16_3_SrcOff2_D1(const mlib_u8  *src,
                                                     mlib_s16       *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_S16_3_SrcOff3_D1(const mlib_u8  *src,
                                                     mlib_s16       *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_S16_3_D1_SMALL(const mlib_u8  *src,
                                                   mlib_s16       *dst,
                                                   mlib_s32       xsize,
                                                   const mlib_s16 **table);

static void mlib_v_ImageLookUpSI_U8_S16_4_DstA8D1_D1(const mlib_u8  *src,
                                                     mlib_s16       *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_S16_4_DstNonAl_D1(const mlib_u8  *src,
                                                      mlib_s16       *dst,
                                                      mlib_s32       xsize,
                                                      const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_S16_4_DstOff0_D1_SMALL(const mlib_u8  *src,
                                                           mlib_s16       *dst,
                                                           mlib_s32       xsize,
                                                           const mlib_s16 **table);

static void mlib_v_ImageLookUpSI_U8_S16_4_DstOff1_D1_SMALL(const mlib_u8  *src,
                                                           mlib_s16       *dst,
                                                           mlib_s32       xsize,
                                                           const mlib_s16 **table);

static void mlib_v_ImageLookUpSI_U8_S16_4_DstOff2_D1_SMALL(const mlib_u8  *src,
                                                           mlib_s16       *dst,
                                                           mlib_s32       xsize,
                                                           const mlib_s16 **table);

static void mlib_v_ImageLookUpSI_U8_S16_4_DstOff3_D1_SMALL(const mlib_u8  *src,
                                                           mlib_s16       *dst,
                                                           mlib_s32       xsize,
                                                           const mlib_s16 **table);

/***************************************************************/
#define VIS_LD_U16_I(X, Y)      vis_ld_u16_i((void *)(X), (Y))

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_2_SrcOff0_D1(const mlib_u8  *src,
                                              mlib_s16       *dst,
                                              mlib_s32       xsize,
                                              const mlib_f32 *table)
{
  mlib_u32 *sa;                        /* aligned pointer to source data */
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 s0;                         /* source data */
  mlib_f32 *dp;                        /* aligned pointer to destination */
  mlib_f32 acc0, acc1;                 /* destination data */
  mlib_f32 acc2, acc3;                 /* destination data */
  mlib_s32 i;                          /* loop variable */
  mlib_u32 s00, s01, s02, s03;

  sa = (mlib_u32 *) src;
  dp = (mlib_f32 *) dst;

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 22) & 0x3FC;
    s01 = (s0 >> 14) & 0x3FC;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 8; i += 4, dp += 4) {
      s02 = (s0 >> 6) & 0x3FC;
      s03 = (s0 << 2) & 0x3FC;
      acc0 = *(mlib_f32 *) ((mlib_u8 *) table + s00);
      acc1 = *(mlib_f32 *) ((mlib_u8 *) table + s01);
      acc2 = *(mlib_f32 *) ((mlib_u8 *) table + s02);
      acc3 = *(mlib_f32 *) ((mlib_u8 *) table + s03);
      s0 = *sa++;
      s00 = (s0 >> 22) & 0x3FC;
      s01 = (s0 >> 14) & 0x3FC;
      dp[0] = acc0;
      dp[1] = acc1;
      dp[2] = acc2;
      dp[3] = acc3;
    }

    s02 = (s0 >> 6) & 0x3FC;
    s03 = (s0 << 2) & 0x3FC;
    acc0 = *(mlib_f32 *) ((mlib_u8 *) table + s00);
    acc1 = *(mlib_f32 *) ((mlib_u8 *) table + s01);
    acc2 = *(mlib_f32 *) ((mlib_u8 *) table + s02);
    acc3 = *(mlib_f32 *) ((mlib_u8 *) table + s03);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    dp[3] = acc3;
    dp += 4;
    i += 4;
  }

  sp = (mlib_u8 *) sa;

  if (i <= xsize - 2) {
    *dp++ = table[sp[0]];
    *dp++ = table[sp[1]];
    i += 2;
    sp += 2;
  }

  if (i < xsize)
    *dp = table[sp[0]];
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_2_DstNonAl_D1(const mlib_u8  *src,
                                               mlib_s16       *dst,
                                               mlib_s32       xsize,
                                               const mlib_f32 *table)
{
  mlib_u32 *sa;                        /* aligned pointer to source data */
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 s0;                         /* source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 acc0, acc1, acc2;           /* destination data */
  mlib_s32 i;                          /* loop variable */
  mlib_s16 *dend;                      /* pointer to end of destination */
  mlib_s32 emask;                      /* edge mask */
  mlib_s32 off;
  mlib_u32 s00, s01, s02, s03;

  sa = (mlib_u32 *) src;
  sp = (void *)src;
  dl = dst;
  dend = dl + (xsize << 1) - 1;
  dp = (mlib_d64 *) ((mlib_addr) dl & (~7));
  off = (mlib_addr) dp - (mlib_addr) dl;
  vis_alignaddr(dp, off);

  emask = vis_edge16(dl, dend);
  acc0 = vis_freg_pair(table[sp[0]], table[sp[1]]);
  vis_pst_16(vis_faligndata(acc0, acc0), dp++, emask);
  sp += 2;

  xsize -= 2;

  if (xsize >= 2) {
    acc1 = vis_freg_pair(table[sp[0]], table[sp[1]]);
    *dp++ = vis_faligndata(acc0, acc1);
    acc0 = acc1;
    sp += 2;
    xsize -= 2;
  }

  sa++;

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 22) & 0x3FC;
    s01 = (s0 >> 14) & 0x3FC;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 8; i += 4, dp += 2) {
      s02 = (s0 >> 6) & 0x3FC;
      s03 = (s0 << 2) & 0x3FC;
      acc1 = vis_freg_pair(*(mlib_f32 *) ((mlib_u8 *) table + s00),
                           *(mlib_f32 *) ((mlib_u8 *) table + s01));
      acc2 = vis_freg_pair(*(mlib_f32 *) ((mlib_u8 *) table + s02),
                           *(mlib_f32 *) ((mlib_u8 *) table + s03));
      s0 = *sa++;
      s00 = (s0 >> 22) & 0x3FC;
      s01 = (s0 >> 14) & 0x3FC;
      dp[0] = vis_faligndata(acc0, acc1);
      dp[1] = vis_faligndata(acc1, acc2);
      acc0 = acc2;
    }

    s02 = (s0 >> 6) & 0x3FC;
    s03 = (s0 << 2) & 0x3FC;
    acc1 = vis_freg_pair(*(mlib_f32 *) ((mlib_u8 *) table + s00),
                         *(mlib_f32 *) ((mlib_u8 *) table + s01));
    acc2 = vis_freg_pair(*(mlib_f32 *) ((mlib_u8 *) table + s02),
                         *(mlib_f32 *) ((mlib_u8 *) table + s03));
    dp[0] = vis_faligndata(acc0, acc1);
    dp[1] = vis_faligndata(acc1, acc2);
    acc0 = acc2;
    sp = (mlib_u8 *) sa;
    dp += 2;
    i += 4;
  }

  if (i <= xsize - 2) {
    acc1 = vis_freg_pair(table[sp[0]], table[sp[1]]);
    *dp++ = vis_faligndata(acc0, acc1);
    acc0 = acc1;
    i += 2;
    sp += 2;
  }

  if ((mlib_addr) dp <= (mlib_addr) dend) {
    emask = vis_edge16(dp, dend);
    acc1 = vis_freg_pair(table[sp[0]], table[sp[1]]);
    vis_pst_16(vis_faligndata(acc0, acc1), dp++, emask);
  }

  if ((mlib_addr) dp <= (mlib_addr) dend) {
    emask = vis_edge16(dp, dend);
    vis_pst_16(vis_faligndata(acc1, acc1), dp++, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_2_DstA8D1_SMALL(const mlib_u8  *src,
                                                 mlib_s16       *dst,
                                                 mlib_s32       xsize,
                                                 const mlib_s16 **table)
{
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 s0, s1;                     /* source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;                 /* destination data */
  mlib_d64 t3, acc;                    /* destination data */
  mlib_s32 i;                          /* loop variable */
  const mlib_s16 *tab0 = table[0];
  const mlib_s16 *tab1 = table[1];

  sp = (void *)src;
  dl = dst;
  dp = (mlib_d64 *) dl;

  vis_alignaddr((void *)0, 6);

  if (xsize >= 2) {

    s0 = (sp[0] << 1);
    s1 = (sp[1] << 1);
    sp += 2;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 4; i += 2, sp += 2) {
      t3 = VIS_LD_U16_I(tab1, s1);
      t2 = VIS_LD_U16_I(tab0, s1);
      t1 = VIS_LD_U16_I(tab1, s0);
      t0 = VIS_LD_U16_I(tab0, s0);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = (sp[0] << 1);
      s1 = (sp[1] << 1);
      *dp++ = acc;
    }

    t3 = VIS_LD_U16_I(tab1, s1);
    t2 = VIS_LD_U16_I(tab0, s1);
    t1 = VIS_LD_U16_I(tab1, s0);
    t0 = VIS_LD_U16_I(tab0, s0);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    *dp++ = acc;
  }

  if ((xsize & 1) != 0) {
    s0 = (sp[0] << 1);
    t1 = VIS_LD_U16_I(tab1, s0);
    t0 = VIS_LD_U16_I(tab0, s0);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    *(mlib_f32 *) dp = vis_read_hi(acc);
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_2_D1_SMALL(const mlib_u8  *src,
                                            mlib_s16       *dst,
                                            mlib_s32       xsize,
                                            const mlib_s16 **table)
{
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 s0, s1, s2;                 /* source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;                 /* destination data */
  mlib_d64 t3, acc;                    /* destination data */
  mlib_s32 i;                          /* loop variable */
  const mlib_s16 *tab0 = table[0];
  const mlib_s16 *tab1 = table[1];

  sp = (void *)src;
  dl = dst;

  vis_alignaddr((void *)0, 6);

  s0 = *sp++;
  *dl++ = tab0[s0];
  dp = (mlib_d64 *) dl;
  xsize--;
  s0 <<= 1;

  if (xsize >= 2) {

    s1 = (sp[0] << 1);
    s2 = (sp[1] << 1);
    sp += 2;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 4; i += 2, sp += 2) {
      t3 = VIS_LD_U16_I(tab0, s2);
      t2 = VIS_LD_U16_I(tab1, s1);
      t1 = VIS_LD_U16_I(tab0, s1);
      t0 = VIS_LD_U16_I(tab1, s0);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = s2;
      s1 = (sp[0] << 1);
      s2 = (sp[1] << 1);
      *dp++ = acc;
    }

    t3 = VIS_LD_U16_I(tab0, s2);
    t2 = VIS_LD_U16_I(tab1, s1);
    t1 = VIS_LD_U16_I(tab0, s1);
    t0 = VIS_LD_U16_I(tab1, s0);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    s0 = s2;
    *dp++ = acc;
  }

  dl = (mlib_s16 *) dp;

  if ((xsize & 1) != 0) {
    s1 = (sp[0] << 1);
    t1 = VIS_LD_U16_I(tab0, s1);
    t0 = VIS_LD_U16_I(tab1, s0);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    *(mlib_f32 *) dp = vis_read_hi(acc);
    s0 = s1;
    dl += 2;
  }

  s0 >>= 1;
  *dl = tab1[s0];
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_2(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_s16       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_s16 **table)
{
  if ((xsize * ysize) < 550) {
    mlib_u8 *sl;
    mlib_s16 *dl;
    mlib_s32 j;
    const mlib_s16 *tab0 = table[0];
    const mlib_s16 *tab1 = table[1];

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j++) {
      mlib_u8 *sp = sl;
      mlib_s16 *dp = dl;
      mlib_s32 off, s0, size = xsize;

      off = ((8 - ((mlib_addr) dp & 7)) & 7);

      if ((off >= 4) && (size > 0)) {
        s0 = *sp++;
        *dp++ = tab0[s0];
        *dp++ = tab1[s0];
        size--;
      }

      if (size > 0) {

        if (((mlib_addr) dp & 7) == 0) {
          mlib_v_ImageLookUpSI_U8_S16_2_DstA8D1_SMALL(sp, dp, size, table);
        }
        else {
          mlib_v_ImageLookUpSI_U8_S16_2_D1_SMALL(sp, dp, size, table);
        }
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else {
    mlib_u8 *sl;
    mlib_s16 *dl;
    mlib_u32 tab[256];
    mlib_u16 *tab0 = (mlib_u16 *) table[0];
    mlib_u16 *tab1 = (mlib_u16 *) table[1];
    mlib_s32 i, j;
    mlib_u32 s0, s1, s2;

    s0 = tab0[0];
    s1 = tab1[0];
    for (i = 1; i < 256; i++) {
      s2 = (s0 << 16) + s1;
      s0 = tab0[i];
      s1 = tab1[i];
      tab[i - 1] = s2;
    }

    s2 = (s0 << 16) + s1;
    tab[255] = s2;

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j++) {
      mlib_u8 *sp = sl;
      mlib_s16 *dp = dl;
      mlib_s32 off, s0, size = xsize;

      if (((mlib_addr) dp & 3) == 0) {

        off = (4 - (mlib_addr) sp & 3) & 3;

        off = (off < size) ? off : size;

#pragma pipeloop(0)
        for (i = 0; i < off; i++, sp++) {
          *(mlib_u32 *) dp = tab[(*sp)];
          dp += 2;
        }

        size -= off;

        if (size > 0) {
          mlib_v_ImageLookUpSI_U8_S16_2_SrcOff0_D1(sp, dp, size,
                                                   (mlib_f32 *) tab);
        }
      }
      else {

        off = ((4 - ((mlib_addr) sp & 3)) & 3);
        off = (off < size) ? off : size;

        for (i = 0; i < off; i++) {
          s0 = tab[(*sp)];
          *dp++ = (s0 >> 16);
          *dp++ = (s0 & 0xFFFF);
          size--;
          sp++;
        }

        if (size > 0) {
          mlib_v_ImageLookUpSI_U8_S16_2_DstNonAl_D1(sp, dp, size,
                                                    (mlib_f32 *) tab);
        }
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_3_SrcOff0_D1(const mlib_u8  *src,
                                              mlib_s16       *dst,
                                              mlib_s32       xsize,
                                              const mlib_d64 *table)
{
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 *sa;                        /* aligned pointer to source data */
  mlib_u32 s0;                         /* source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3;             /* destination data */
  mlib_d64 acc0, acc1, acc2;           /* destination data */
  mlib_s32 i;                          /* loop variable */
  mlib_s16 *ptr;

  dl = dst;
  sp = (void *)src;
  dp = (mlib_d64 *) dl;
  sa = (mlib_u32 *) sp;

  vis_alignaddr((void *)0, 6);

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 8; i += 4, dp += 3) {
      t0 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 >> 21) & 0x7F8));
      t1 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 >> 13) & 0x7F8));
      t2 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 >> 5) & 0x7F8));
      t3 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 << 3) & 0x7F8));
      acc0 = vis_faligndata(t0, t0);
      acc1 = vis_faligndata(acc0, acc0);
      acc2 = vis_faligndata(acc0, t1);
      acc0 = vis_faligndata(acc1, acc1);
      acc1 = vis_faligndata(acc1, acc2);
      acc2 = vis_faligndata(acc2, t2);
      acc0 = vis_faligndata(acc0, acc1);
      acc1 = vis_faligndata(acc1, acc2);
      acc2 = vis_faligndata(acc2, t3);
      s0 = *sa++;
      dp[0] = acc0;
      dp[1] = acc1;
      dp[2] = acc2;
    }

    t0 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 >> 21) & 0x7F8));
    t1 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 >> 13) & 0x7F8));
    t2 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 >> 5) & 0x7F8));
    t3 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 << 3) & 0x7F8));
    acc0 = vis_faligndata(t0, t0);
    acc1 = vis_faligndata(acc0, acc0);
    acc2 = vis_faligndata(acc0, t1);
    acc0 = vis_faligndata(acc1, acc1);
    acc1 = vis_faligndata(acc1, acc2);
    acc2 = vis_faligndata(acc2, t2);
    acc0 = vis_faligndata(acc0, acc1);
    acc1 = vis_faligndata(acc1, acc2);
    acc2 = vis_faligndata(acc2, t3);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    i += 4;
    dp += 3;
  }

  dl = (mlib_s16 *) dp;

#pragma pipeloop(0)
  for (; i < xsize; i++) {
    ptr = (mlib_s16 *) (table + src[i]);
    dl[0] = ptr[0];
    dl[1] = ptr[1];
    dl[2] = ptr[2];
    dl += 3;
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_3_SrcOff1_D1(const mlib_u8  *src,
                                              mlib_s16       *dst,
                                              mlib_s32       xsize,
                                              const mlib_d64 *table)
{
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 *sa;                        /* aligned pointer to source data */
  mlib_u32 s0, s1;                     /* source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3;             /* destination data */
  mlib_d64 acc0, acc1, acc2;           /* destination data */
  mlib_s32 i;                          /* loop variable */
  mlib_s16 *ptr;

  dl = dst;
  sp = (void *)src;
  dp = (mlib_d64 *) dl;
  sa = (mlib_u32 *) (sp - 1);

  i = 0;
  s0 = *sa++;

  vis_alignaddr((void *)0, 6);

  if (xsize >= 4) {

    s1 = *sa++;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 8; i += 4, dp += 3) {
      t0 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 >> 13) & 0x7F8));
      t1 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 >> 5) & 0x7F8));
      t2 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 << 3) & 0x7F8));
      t3 = *(mlib_d64 *) ((mlib_u8 *) table + ((s1 >> 21) & 0x7F8));
      acc0 = vis_faligndata(t0, t0);
      acc1 = vis_faligndata(acc0, acc0);
      acc2 = vis_faligndata(acc0, t1);
      acc0 = vis_faligndata(acc1, acc1);
      acc1 = vis_faligndata(acc1, acc2);
      acc2 = vis_faligndata(acc2, t2);
      acc0 = vis_faligndata(acc0, acc1);
      acc1 = vis_faligndata(acc1, acc2);
      acc2 = vis_faligndata(acc2, t3);
      s0 = s1;
      s1 = *sa++;
      dp[0] = acc0;
      dp[1] = acc1;
      dp[2] = acc2;
    }

    t0 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 >> 13) & 0x7F8));
    t1 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 >> 5) & 0x7F8));
    t2 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 << 3) & 0x7F8));
    t3 = *(mlib_d64 *) ((mlib_u8 *) table + ((s1 >> 21) & 0x7F8));
    acc0 = vis_faligndata(t0, t0);
    acc1 = vis_faligndata(acc0, acc0);
    acc2 = vis_faligndata(acc0, t1);
    acc0 = vis_faligndata(acc1, acc1);
    acc1 = vis_faligndata(acc1, acc2);
    acc2 = vis_faligndata(acc2, t2);
    acc0 = vis_faligndata(acc0, acc1);
    acc1 = vis_faligndata(acc1, acc2);
    acc2 = vis_faligndata(acc2, t3);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    i += 4;
    dp += 3;
  }

  dl = (mlib_s16 *) dp;

#pragma pipeloop(0)
  for (; i < xsize; i++) {
    ptr = (mlib_s16 *) (table + src[i]);
    dl[0] = ptr[0];
    dl[1] = ptr[1];
    dl[2] = ptr[2];
    dl += 3;
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_3_SrcOff2_D1(const mlib_u8  *src,
                                              mlib_s16       *dst,
                                              mlib_s32       xsize,
                                              const mlib_d64 *table)
{
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 *sa;                        /* aligned pointer to source data */
  mlib_u32 s0, s1;                     /* source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3;             /* destination data */
  mlib_d64 acc0, acc1, acc2;           /* destination data */
  mlib_s32 i;                          /* loop variable */
  mlib_s16 *ptr;

  dl = dst;
  sp = (void *)src;
  dp = (mlib_d64 *) dl;
  sa = (mlib_u32 *) (sp - 2);

  i = 0;
  s0 = *sa++;

  vis_alignaddr((void *)0, 6);

  if (xsize >= 4) {

    s1 = *sa++;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 8; i += 4, dp += 3) {
      t0 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 >> 5) & 0x7F8));
      t1 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 << 3) & 0x7F8));
      t2 = *(mlib_d64 *) ((mlib_u8 *) table + ((s1 >> 21) & 0x7F8));
      t3 = *(mlib_d64 *) ((mlib_u8 *) table + ((s1 >> 13) & 0x7F8));
      acc0 = vis_faligndata(t0, t0);
      acc1 = vis_faligndata(acc0, acc0);
      acc2 = vis_faligndata(acc0, t1);
      acc0 = vis_faligndata(acc1, acc1);
      acc1 = vis_faligndata(acc1, acc2);
      acc2 = vis_faligndata(acc2, t2);
      acc0 = vis_faligndata(acc0, acc1);
      acc1 = vis_faligndata(acc1, acc2);
      acc2 = vis_faligndata(acc2, t3);
      s0 = s1;
      s1 = *sa++;
      dp[0] = acc0;
      dp[1] = acc1;
      dp[2] = acc2;
    }

    t0 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 >> 5) & 0x7F8));
    t1 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 << 3) & 0x7F8));
    t2 = *(mlib_d64 *) ((mlib_u8 *) table + ((s1 >> 21) & 0x7F8));
    t3 = *(mlib_d64 *) ((mlib_u8 *) table + ((s1 >> 13) & 0x7F8));
    acc0 = vis_faligndata(t0, t0);
    acc1 = vis_faligndata(acc0, acc0);
    acc2 = vis_faligndata(acc0, t1);
    acc0 = vis_faligndata(acc1, acc1);
    acc1 = vis_faligndata(acc1, acc2);
    acc2 = vis_faligndata(acc2, t2);
    acc0 = vis_faligndata(acc0, acc1);
    acc1 = vis_faligndata(acc1, acc2);
    acc2 = vis_faligndata(acc2, t3);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    i += 4;
    dp += 3;
  }

  dl = (mlib_s16 *) dp;

#pragma pipeloop(0)
  for (; i < xsize; i++) {
    ptr = (mlib_s16 *) (table + src[i]);
    dl[0] = ptr[0];
    dl[1] = ptr[1];
    dl[2] = ptr[2];
    dl += 3;
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_3_SrcOff3_D1(const mlib_u8  *src,
                                              mlib_s16       *dst,
                                              mlib_s32       xsize,
                                              const mlib_d64 *table)
{
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 *sa;                        /* aligned pointer to source data */
  mlib_u32 s0, s1;                     /* source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3;             /* destination data */
  mlib_d64 acc0, acc1, acc2;           /* destination data */
  mlib_s32 i;                          /* loop variable */
  mlib_s16 *ptr;

  dl = dst;
  sp = (void *)src;
  dp = (mlib_d64 *) dl;
  sa = (mlib_u32 *) (sp - 3);

  i = 0;
  s0 = *sa++;

  vis_alignaddr((void *)0, 6);

  if (xsize >= 4) {

    s1 = *sa++;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 8; i += 4, dp += 3) {
      t0 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 << 3) & 0x7F8));
      t1 = *(mlib_d64 *) ((mlib_u8 *) table + ((s1 >> 21) & 0x7F8));
      t2 = *(mlib_d64 *) ((mlib_u8 *) table + ((s1 >> 13) & 0x7F8));
      t3 = *(mlib_d64 *) ((mlib_u8 *) table + ((s1 >> 5) & 0x7F8));
      acc0 = vis_faligndata(t0, t0);
      acc1 = vis_faligndata(acc0, acc0);
      acc2 = vis_faligndata(acc0, t1);
      acc0 = vis_faligndata(acc1, acc1);
      acc1 = vis_faligndata(acc1, acc2);
      acc2 = vis_faligndata(acc2, t2);
      acc0 = vis_faligndata(acc0, acc1);
      acc1 = vis_faligndata(acc1, acc2);
      acc2 = vis_faligndata(acc2, t3);
      s0 = s1;
      s1 = *sa++;
      dp[0] = acc0;
      dp[1] = acc1;
      dp[2] = acc2;
    }

    t0 = *(mlib_d64 *) ((mlib_u8 *) table + ((s0 << 3) & 0x7F8));
    t1 = *(mlib_d64 *) ((mlib_u8 *) table + ((s1 >> 21) & 0x7F8));
    t2 = *(mlib_d64 *) ((mlib_u8 *) table + ((s1 >> 13) & 0x7F8));
    t3 = *(mlib_d64 *) ((mlib_u8 *) table + ((s1 >> 5) & 0x7F8));
    acc0 = vis_faligndata(t0, t0);
    acc1 = vis_faligndata(acc0, acc0);
    acc2 = vis_faligndata(acc0, t1);
    acc0 = vis_faligndata(acc1, acc1);
    acc1 = vis_faligndata(acc1, acc2);
    acc2 = vis_faligndata(acc2, t2);
    acc0 = vis_faligndata(acc0, acc1);
    acc1 = vis_faligndata(acc1, acc2);
    acc2 = vis_faligndata(acc2, t3);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    i += 4;
    dp += 3;
  }

  dl = (mlib_s16 *) dp;

#pragma pipeloop(0)
  for (; i < xsize; i++) {
    ptr = (mlib_s16 *) (table + src[i]);
    dl[0] = ptr[0];
    dl[1] = ptr[1];
    dl[2] = ptr[2];
    dl += 3;
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_3_D1_SMALL(const mlib_u8  *src,
                                            mlib_s16       *dst,
                                            mlib_s32       xsize,
                                            const mlib_s16 **table)
{
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3;             /* destination data */
  mlib_d64 acc0, acc1, acc2;           /* destination data */
  mlib_s32 i;                          /* loop variable */
  const mlib_s16 *tab0 = table[0];
  const mlib_s16 *tab1 = table[1];
  const mlib_s16 *tab2 = table[2];
  mlib_u32 s00, s01, s02, s03;

  sp = (void *)src;
  dl = dst;
  dp = (mlib_d64 *) dl;

  vis_alignaddr((void *)0, 6);

  i = 0;

  if (xsize >= 4) {

    s00 = (sp[0] << 1);
    s01 = (sp[1] << 1);
    s02 = (sp[2] << 1);
    s03 = (sp[3] << 1);
    sp += 4;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 8; i += 4, sp += 4) {
      t3 = VIS_LD_U16_I(tab0, s01);
      t2 = VIS_LD_U16_I(tab2, s00);
      t1 = VIS_LD_U16_I(tab1, s00);
      t0 = VIS_LD_U16_I(tab0, s00);
      acc0 = vis_faligndata(t3, acc0);
      acc0 = vis_faligndata(t2, acc0);
      acc0 = vis_faligndata(t1, acc0);
      acc0 = vis_faligndata(t0, acc0);
      t3 = VIS_LD_U16_I(tab1, s02);
      t2 = VIS_LD_U16_I(tab0, s02);
      t1 = VIS_LD_U16_I(tab2, s01);
      t0 = VIS_LD_U16_I(tab1, s01);
      acc1 = vis_faligndata(t3, acc1);
      acc1 = vis_faligndata(t2, acc1);
      acc1 = vis_faligndata(t1, acc1);
      acc1 = vis_faligndata(t0, acc1);
      t3 = VIS_LD_U16_I(tab2, s03);
      t2 = VIS_LD_U16_I(tab1, s03);
      t1 = VIS_LD_U16_I(tab0, s03);
      t0 = VIS_LD_U16_I(tab2, s02);
      acc2 = vis_faligndata(t3, acc2);
      acc2 = vis_faligndata(t2, acc2);
      acc2 = vis_faligndata(t1, acc2);
      acc2 = vis_faligndata(t0, acc2);
      s00 = (sp[0] << 1);
      s01 = (sp[1] << 1);
      s02 = (sp[2] << 1);
      s03 = (sp[3] << 1);
      *dp++ = acc0;
      *dp++ = acc1;
      *dp++ = acc2;
    }

    t3 = VIS_LD_U16_I(tab0, s01);
    t2 = VIS_LD_U16_I(tab2, s00);
    t1 = VIS_LD_U16_I(tab1, s00);
    t0 = VIS_LD_U16_I(tab0, s00);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    t3 = VIS_LD_U16_I(tab1, s02);
    t2 = VIS_LD_U16_I(tab0, s02);
    t1 = VIS_LD_U16_I(tab2, s01);
    t0 = VIS_LD_U16_I(tab1, s01);
    acc1 = vis_faligndata(t3, acc1);
    acc1 = vis_faligndata(t2, acc1);
    acc1 = vis_faligndata(t1, acc1);
    acc1 = vis_faligndata(t0, acc1);
    t3 = VIS_LD_U16_I(tab2, s03);
    t2 = VIS_LD_U16_I(tab1, s03);
    t1 = VIS_LD_U16_I(tab0, s03);
    t0 = VIS_LD_U16_I(tab2, s02);
    acc2 = vis_faligndata(t3, acc2);
    acc2 = vis_faligndata(t2, acc2);
    acc2 = vis_faligndata(t1, acc2);
    acc2 = vis_faligndata(t0, acc2);
    *dp++ = acc0;
    *dp++ = acc1;
    *dp++ = acc2;
    i += 4;
  }

  dl = (mlib_s16 *) dp;

#pragma pipeloop(0)
  for (; i < xsize; i++) {
    s00 = sp[0];
    dl[0] = tab0[s00];
    dl[1] = tab1[s00];
    dl[2] = tab2[s00];
    dl += 3;
    sp++;
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_3(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_s16       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_s16 **table)
{
  if ((xsize * ysize) < 550) {
    mlib_u8 *sl;
    mlib_s16 *dl;
    mlib_s32 i, j;
    const mlib_s16 *tab0 = table[0];
    const mlib_s16 *tab1 = table[1];
    const mlib_s16 *tab2 = table[2];

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j++) {
      mlib_u8 *sp = sl;
      mlib_s16 *dp = dl;
      mlib_s32 off, s0, size = xsize;

      off = ((mlib_addr) dp & 7) >> 1;
      off = (off < size) ? off : size;

      for (i = 0; i < off; i++) {
        s0 = *sp++;
        *dp++ = tab0[s0];
        *dp++ = tab1[s0];
        *dp++ = tab2[s0];
        size--;
      }

      if (size > 0) {
        mlib_v_ImageLookUpSI_U8_S16_3_D1_SMALL(sp, dp, size, table);
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else {
    mlib_u8 *sl;
    mlib_s16 *dl;
    mlib_u32 tab[512];
    mlib_u16 *tab0 = (mlib_u16 *) table[0];
    mlib_u16 *tab1 = (mlib_u16 *) table[1];
    mlib_u16 *tab2 = (mlib_u16 *) table[2];
    mlib_s32 i, j;
    mlib_u32 s0, s1, s2, s3;

    s0 = tab0[0];
    s1 = tab1[0];
    s2 = tab2[0];
    for (i = 1; i < 256; i++) {
      s3 = (s0 << 16) + s1;
      s0 = tab0[i];
      s1 = tab1[i];
      tab[2 * i - 2] = s3;
      tab[2 * i - 1] = (s2 << 16);
      s2 = tab2[i];
    }

    s3 = (s0 << 16) + s1;
    tab[510] = s3;
    tab[511] = (s2 << 16);

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j++) {
      mlib_u8 *sp = sl;
      mlib_s16 *dp = dl;
      mlib_s32 off, size = xsize;
      mlib_s16 *ptr;

      off = ((mlib_addr) dp & 7) >> 1;
      off = (off < size) ? off : size;

#pragma pipeloop(0)
      for (i = 0; i < off; i++) {
        ptr = (mlib_s16 *) (tab + 2 * sp[i]);
        dp[0] = ptr[0];
        dp[1] = ptr[1];
        dp[2] = ptr[2];
        dp += 3;
      }

      size -= off;
      sp += off;

      if (size > 0) {
        off = (mlib_addr) sp & 3;

        if (off == 0) {
          mlib_v_ImageLookUpSI_U8_S16_3_SrcOff0_D1(sp, dp, size,
                                                   (mlib_d64 *) tab);
        }
        else if (off == 1) {
          mlib_v_ImageLookUpSI_U8_S16_3_SrcOff1_D1(sp, dp, size,
                                                   (mlib_d64 *) tab);
        }
        else if (off == 2) {
          mlib_v_ImageLookUpSI_U8_S16_3_SrcOff2_D1(sp, dp, size,
                                                   (mlib_d64 *) tab);
        }
        else if (off == 3) {
          mlib_v_ImageLookUpSI_U8_S16_3_SrcOff3_D1(sp, dp, size,
                                                   (mlib_d64 *) tab);
        }
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_4_DstA8D1_D1(const mlib_u8  *src,
                                              mlib_s16       *dst,
                                              mlib_s32       xsize,
                                              const mlib_d64 *table)
{
  mlib_u32 *sa;                        /* aligned pointer to source data */
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 s0;                         /* source data */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 acc0, acc1;                 /* destination data */
  mlib_d64 acc2, acc3;                 /* destination data */
  mlib_s32 i;                          /* loop variable */
  mlib_u32 s00, s01, s02, s03;

  sa = (mlib_u32 *) src;
  dp = (mlib_d64 *) dst;

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 21) & 0x7F8;
    s01 = (s0 >> 13) & 0x7F8;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 8; i += 4, dp += 4) {
      s02 = (s0 >> 5) & 0x7F8;
      s03 = (s0 << 3) & 0x7F8;
      acc0 = *(mlib_d64 *) ((mlib_u8 *) table + s00);
      acc1 = *(mlib_d64 *) ((mlib_u8 *) table + s01);
      acc2 = *(mlib_d64 *) ((mlib_u8 *) table + s02);
      acc3 = *(mlib_d64 *) ((mlib_u8 *) table + s03);
      s0 = *sa++;
      s00 = (s0 >> 21) & 0x7F8;
      s01 = (s0 >> 13) & 0x7F8;
      dp[0] = acc0;
      dp[1] = acc1;
      dp[2] = acc2;
      dp[3] = acc3;
    }

    s02 = (s0 >> 5) & 0x7F8;
    s03 = (s0 << 3) & 0x7F8;
    acc0 = *(mlib_d64 *) ((mlib_u8 *) table + s00);
    acc1 = *(mlib_d64 *) ((mlib_u8 *) table + s01);
    acc2 = *(mlib_d64 *) ((mlib_u8 *) table + s02);
    acc3 = *(mlib_d64 *) ((mlib_u8 *) table + s03);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    dp[3] = acc3;
    dp += 4;
    i += 4;
  }

  sp = (mlib_u8 *) sa;

  if (i <= xsize - 2) {
    *dp++ = table[sp[0]];
    *dp++ = table[sp[1]];
    i += 2;
    sp += 2;
  }

  if (i < xsize)
    *dp++ = table[sp[0]];
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_4_DstNonAl_D1(const mlib_u8  *src,
                                               mlib_s16       *dst,
                                               mlib_s32       xsize,
                                               const mlib_d64 *table)
{
  mlib_u32 *sa;                        /* aligned pointer to source data */
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 s0;                         /* source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 acc0, acc1;                 /* destination data */
  mlib_d64 acc2, acc3, acc4;           /* destination data */
  mlib_s32 i;                          /* loop variable */
  mlib_s16 *dend;                      /* pointer to end of destination */
  mlib_s32 emask;                      /* edge mask */
  mlib_s32 off;
  mlib_u32 s00, s01, s02, s03;

  sp = (void *)src;
  dl = dst;
  dend = dl + (xsize << 2) - 1;
  dp = (mlib_d64 *) ((mlib_addr) dl & (~7));
  off = (mlib_addr) dp - (mlib_addr) dl;
  vis_alignaddr(dp, off);

  emask = vis_edge16(dl, dend);
  acc0 = table[sp[0]];
  vis_pst_16(vis_faligndata(acc0, acc0), dp++, emask);
  sp++;

  sa = (mlib_u32 *) sp;

  xsize--;

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 21) & 0x7F8;
    s01 = (s0 >> 13) & 0x7F8;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 8; i += 4, dp += 4) {
      s02 = (s0 >> 5) & 0x7F8;
      s03 = (s0 << 3) & 0x7F8;
      acc1 = *(mlib_d64 *) ((mlib_u8 *) table + s00);
      acc2 = *(mlib_d64 *) ((mlib_u8 *) table + s01);
      acc3 = *(mlib_d64 *) ((mlib_u8 *) table + s02);
      acc4 = *(mlib_d64 *) ((mlib_u8 *) table + s03);
      s0 = *sa++;
      s00 = (s0 >> 21) & 0x7F8;
      s01 = (s0 >> 13) & 0x7F8;
      dp[0] = vis_faligndata(acc0, acc1);
      dp[1] = vis_faligndata(acc1, acc2);
      dp[2] = vis_faligndata(acc2, acc3);
      dp[3] = vis_faligndata(acc3, acc4);
      acc0 = acc4;
    }

    s02 = (s0 >> 5) & 0x7F8;
    s03 = (s0 << 3) & 0x7F8;
    acc1 = *(mlib_d64 *) ((mlib_u8 *) table + s00);
    acc2 = *(mlib_d64 *) ((mlib_u8 *) table + s01);
    acc3 = *(mlib_d64 *) ((mlib_u8 *) table + s02);
    acc4 = *(mlib_d64 *) ((mlib_u8 *) table + s03);
    dp[0] = vis_faligndata(acc0, acc1);
    dp[1] = vis_faligndata(acc1, acc2);
    dp[2] = vis_faligndata(acc2, acc3);
    dp[3] = vis_faligndata(acc3, acc4);
    acc0 = acc4;
    dp += 4;
    i += 4;
  }

  sp = (mlib_u8 *) sa;

  if (i <= xsize - 2) {
    acc1 = table[sp[0]];
    acc2 = table[sp[1]];
    *dp++ = vis_faligndata(acc0, acc1);
    *dp++ = vis_faligndata(acc1, acc2);
    i += 2;
    sp += 2;
    acc0 = acc2;
  }

  if (i < xsize) {
    acc1 = table[sp[0]];
    *dp++ = vis_faligndata(acc0, acc1);
    acc0 = acc1;
  }

  emask = vis_edge16(dp, dend);
  vis_pst_16(vis_faligndata(acc0, acc0), dp++, emask);
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_4_DstOff0_D1_SMALL(const mlib_u8  *src,
                                                    mlib_s16       *dst,
                                                    mlib_s32       xsize,
                                                    const mlib_s16 **table)
{
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 s0;                         /* source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3;             /* destination data */
  mlib_d64 acc;                        /* destination data */
  mlib_s32 i;                          /* loop variable */
  const mlib_s16 *tab0 = table[0];
  const mlib_s16 *tab1 = table[1];
  const mlib_s16 *tab2 = table[2];
  const mlib_s16 *tab3 = table[3];

  sp = (void *)src;
  dl = dst;
  dp = (mlib_d64 *) dl;

  vis_alignaddr((void *)0, 6);

  if (xsize >= 1) {

    s0 = (*sp++) << 1;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 2; i++) {
      t3 = VIS_LD_U16_I(tab3, s0);
      t2 = VIS_LD_U16_I(tab2, s0);
      t1 = VIS_LD_U16_I(tab1, s0);
      t0 = VIS_LD_U16_I(tab0, s0);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = (*sp++) << 1;
      *dp++ = acc;
    }

    t3 = VIS_LD_U16_I(tab3, s0);
    t2 = VIS_LD_U16_I(tab2, s0);
    t1 = VIS_LD_U16_I(tab1, s0);
    t0 = VIS_LD_U16_I(tab0, s0);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    *dp++ = acc;
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_4_DstOff1_D1_SMALL(const mlib_u8  *src,
                                                    mlib_s16       *dst,
                                                    mlib_s32       xsize,
                                                    const mlib_s16 **table)
{
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 s0, s1;                     /* source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3;             /* destination data */
  mlib_d64 acc;                        /* destination data */
  mlib_s32 i;                          /* loop variable */
  const mlib_s16 *tab0 = table[0];
  const mlib_s16 *tab1 = table[1];
  const mlib_s16 *tab2 = table[2];
  const mlib_s16 *tab3 = table[3];

  sp = (void *)src;
  dl = dst;
  dp = (mlib_d64 *) dl;

  vis_alignaddr((void *)0, 6);

  s0 = (*sp++) << 1;

  if (xsize >= 1) {

    s1 = (*sp++) << 1;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 2; i++) {
      t3 = VIS_LD_U16_I(tab0, s1);
      t2 = VIS_LD_U16_I(tab3, s0);
      t1 = VIS_LD_U16_I(tab2, s0);
      t0 = VIS_LD_U16_I(tab1, s0);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = s1;
      s1 = (*sp++) << 1;
      *dp++ = acc;
    }

    t3 = VIS_LD_U16_I(tab0, s1);
    t2 = VIS_LD_U16_I(tab3, s0);
    t1 = VIS_LD_U16_I(tab2, s0);
    t0 = VIS_LD_U16_I(tab1, s0);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    s0 = s1;
    *dp++ = acc;
  }

  dl = (mlib_s16 *) dp;
  s0 >>= 1;

  dl[0] = tab1[s0];
  dl[1] = tab2[s0];
  dl[2] = tab3[s0];
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_4_DstOff2_D1_SMALL(const mlib_u8  *src,
                                                    mlib_s16       *dst,
                                                    mlib_s32       xsize,
                                                    const mlib_s16 **table)
{
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 s0, s1;                     /* source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3;             /* destination data */
  mlib_d64 acc;                        /* destination data */
  mlib_s32 i;                          /* loop variable */
  const mlib_s16 *tab0 = table[0];
  const mlib_s16 *tab1 = table[1];
  const mlib_s16 *tab2 = table[2];
  const mlib_s16 *tab3 = table[3];

  sp = (void *)src;
  dl = dst;
  dp = (mlib_d64 *) dl;

  vis_alignaddr((void *)0, 6);

  s0 = (*sp++) << 1;

  if (xsize >= 1) {

    s1 = (*sp++) << 1;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 2; i++) {
      t3 = VIS_LD_U16_I(tab1, s1);
      t2 = VIS_LD_U16_I(tab0, s1);
      t1 = VIS_LD_U16_I(tab3, s0);
      t0 = VIS_LD_U16_I(tab2, s0);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = s1;
      s1 = (*sp++) << 1;
      *dp++ = acc;
    }

    t3 = VIS_LD_U16_I(tab1, s1);
    t2 = VIS_LD_U16_I(tab0, s1);
    t1 = VIS_LD_U16_I(tab3, s0);
    t0 = VIS_LD_U16_I(tab2, s0);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    s0 = s1;
    *dp++ = acc;
  }

  dl = (mlib_s16 *) dp;
  s0 >>= 1;

  dl[0] = tab2[s0];
  dl[1] = tab3[s0];
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_4_DstOff3_D1_SMALL(const mlib_u8  *src,
                                                    mlib_s16       *dst,
                                                    mlib_s32       xsize,
                                                    const mlib_s16 **table)
{
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 s0, s1;                     /* source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3;             /* destination data */
  mlib_d64 acc;                        /* destination data */
  mlib_s32 i;                          /* loop variable */
  const mlib_s16 *tab0 = table[0];
  const mlib_s16 *tab1 = table[1];
  const mlib_s16 *tab2 = table[2];
  const mlib_s16 *tab3 = table[3];

  sp = (void *)src;
  dl = dst;
  dp = (mlib_d64 *) dl;

  vis_alignaddr((void *)0, 6);

  s0 = (*sp++) << 1;

  if (xsize >= 1) {

    s1 = (*sp++) << 1;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 2; i++) {
      t3 = VIS_LD_U16_I(tab2, s1);
      t2 = VIS_LD_U16_I(tab1, s1);
      t1 = VIS_LD_U16_I(tab0, s1);
      t0 = VIS_LD_U16_I(tab3, s0);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = s1;
      s1 = (*sp++) << 1;
      *dp++ = acc;
    }

    t3 = VIS_LD_U16_I(tab2, s1);
    t2 = VIS_LD_U16_I(tab1, s1);
    t1 = VIS_LD_U16_I(tab0, s1);
    t0 = VIS_LD_U16_I(tab3, s0);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    s0 = s1;
    *dp++ = acc;
  }

  dl = (mlib_s16 *) dp;
  s0 >>= 1;

  dl[0] = tab3[s0];
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S16_4(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_s16       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_s16 **table)
{
  if ((xsize * ysize) < 550) {
    mlib_u8 *sl;
    mlib_s16 *dl;
    mlib_s32 j;
    const mlib_s16 *tab0 = table[0];
    const mlib_s16 *tab1 = table[1];
    const mlib_s16 *tab2 = table[2];

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j++) {
      mlib_u8 *sp = sl;
      mlib_s16 *dp = dl;
      mlib_s32 off, s0, size = xsize;

      if (size > 0) {
        off = ((8 - ((mlib_addr) dp & 7)) & 7) >> 1;

        if (off == 0) {
          mlib_v_ImageLookUpSI_U8_S16_4_DstOff0_D1_SMALL(sp, dp, size, table);
        }
        else if (off == 1) {
          s0 = *sp;
          *dp++ = tab0[s0];
          size--;
          mlib_v_ImageLookUpSI_U8_S16_4_DstOff1_D1_SMALL(sp, dp, size, table);
        }
        else if (off == 2) {
          s0 = *sp;
          *dp++ = tab0[s0];
          *dp++ = tab1[s0];
          size--;
          mlib_v_ImageLookUpSI_U8_S16_4_DstOff2_D1_SMALL(sp, dp, size, table);
        }
        else if (off == 3) {
          s0 = *sp;
          *dp++ = tab0[s0];
          *dp++ = tab1[s0];
          *dp++ = tab2[s0];
          size--;
          mlib_v_ImageLookUpSI_U8_S16_4_DstOff3_D1_SMALL(sp, dp, size, table);
        }
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else {
    mlib_u8 *sl;
    mlib_s16 *dl;
    mlib_u32 tab[512];
    mlib_u16 *tab0 = (mlib_u16 *) table[0];
    mlib_u16 *tab1 = (mlib_u16 *) table[1];
    mlib_u16 *tab2 = (mlib_u16 *) table[2];
    mlib_u16 *tab3 = (mlib_u16 *) table[3];
    mlib_s32 i, j;
    mlib_u32 s0, s1, s2, s3, s4, s5;

    s0 = tab0[0];
    s1 = tab1[0];
    s2 = tab2[0];
    s3 = tab3[0];
    for (i = 1; i < 256; i++) {
      s4 = (s0 << 16) + s1;
      s5 = (s2 << 16) + s3;
      s0 = tab0[i];
      s1 = tab1[i];
      s2 = tab2[i];
      s3 = tab3[i];
      tab[2 * i - 2] = s4;
      tab[2 * i - 1] = s5;
    }

    s4 = (s0 << 16) + s1;
    s5 = (s2 << 16) + s3;
    tab[510] = s4;
    tab[511] = s5;

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j++) {
      mlib_u8 *sp = sl;
      mlib_s16 *dp = dl;
      mlib_s32 off, s0, size = xsize;
      mlib_s16 *ptr;

      if (((mlib_addr) dp & 7) == 0) {

        off = ((4 - (mlib_addr) sp & 3) & 3);
        off = (off < size) ? off : size;

#pragma pipeloop(0)
        for (i = 0; i < off; i++) {
          s0 = (*sp++);
          *(mlib_u32 *) dp = tab[2 * s0];
          *(mlib_u32 *) (dp + 2) = tab[2 * s0 + 1];
          dp += 4;
        }

        size -= off;

        if (size > 0) {
          mlib_v_ImageLookUpSI_U8_S16_4_DstA8D1_D1(sp, dp, size,
                                                   (mlib_d64 *) tab);
        }
      }
      else {

        off = (3 - ((mlib_addr) sp & 3));
        off = (off < size) ? off : size;

        for (i = 0; i < off; i++) {
          ptr = (mlib_s16 *) (tab + 2 * sp[i]);
          dp[0] = ptr[0];
          dp[1] = ptr[1];
          dp[2] = ptr[2];
          dp[3] = ptr[3];
          dp += 4;
        }

        sp += off;
        size -= off;

        if (size > 0) {
          mlib_v_ImageLookUpSI_U8_S16_4_DstNonAl_D1(sp, dp, size,
                                                    (mlib_d64 *) tab);
        }
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
