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
static void mlib_v_ImageLookUpSI_U8_S32_2_SrcOff0_D1(const mlib_u8  *src,
                                                     mlib_s32       *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_S32_2_DstNonAl_D1(const mlib_u8  *src,
                                                      mlib_s32       *dst,
                                                      mlib_s32       xsize,
                                                      const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_S32_2_SMALL(const mlib_u8  *src,
                                                mlib_s32       *dst,
                                                mlib_s32       xsize,
                                                const mlib_s32 **table);

static void mlib_v_ImageLookUpSI_U8_S32_3_SrcOff0_D1(const mlib_u8  *src,
                                                     mlib_s32       *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_S32_3_DstNonAl_D1(const mlib_u8  *src,
                                                      mlib_s32       *dst,
                                                      mlib_s32       xsize,
                                                      const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_S32_3_SMALL(const mlib_u8  *src,
                                                mlib_s32       *dst,
                                                mlib_s32       xsize,
                                                const mlib_s32 **table);

static void mlib_v_ImageLookUpSI_U8_S32_4_SrcOff0_D1(const mlib_u8  *src,
                                                     mlib_s32       *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_S32_4_DstNonAl_D1(const mlib_u8  *src,
                                                      mlib_s32       *dst,
                                                      mlib_s32       xsize,
                                                      const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_S32_4_SMALL(const mlib_u8  *src,
                                                mlib_s32       *dst,
                                                mlib_s32       xsize,
                                                const mlib_s32 **table);

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S32_2_SrcOff0_D1(const mlib_u8  *src,
                                              mlib_s32       *dst,
                                              mlib_s32       xsize,
                                              const mlib_d64 *table)
{
  mlib_u32 *sa;          /* aligned pointer to source data */
  mlib_u8  *sp;          /* pointer to source data */
  mlib_u32 s0;           /* source data */
  mlib_d64 *dp;          /* aligned pointer to destination */
  mlib_d64 acc0, acc1;   /* destination data */
  mlib_d64 acc2, acc3;   /* destination data */
  mlib_s32 i;            /* loop variable */
  mlib_u32 s00, s01, s02, s03;

  sa   = (mlib_u32*)src;
  dp   = (mlib_d64 *) dst;

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 21) & 0x7F8;
    s01 = (s0 >> 13) & 0x7F8;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp += 4) {
      s02 = (s0 >> 5) & 0x7F8;
      s03 = (s0 << 3) & 0x7F8;
      acc0 = *(mlib_d64*)((mlib_u8*)table + s00);
      acc1 = *(mlib_d64*)((mlib_u8*)table + s01);
      acc2 = *(mlib_d64*)((mlib_u8*)table + s02);
      acc3 = *(mlib_d64*)((mlib_u8*)table + s03);
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
    acc0 = *(mlib_d64*)((mlib_u8*)table + s00);
    acc1 = *(mlib_d64*)((mlib_u8*)table + s01);
    acc2 = *(mlib_d64*)((mlib_u8*)table + s02);
    acc3 = *(mlib_d64*)((mlib_u8*)table + s03);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    dp[3] = acc3;
    dp += 4;
    i += 4;
  }

  sp = (mlib_u8*)sa;

  if ( i <= xsize - 2) {
    *dp++ = table[sp[0]];
    *dp++ = table[sp[1]];
    i+=2; sp += 2;
  }

  if ( i < xsize) *dp++ = table[sp[0]];
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S32_2_DstNonAl_D1(const mlib_u8  *src,
                                               mlib_s32       *dst,
                                               mlib_s32       xsize,
                                               const mlib_d64 *table)
{
  mlib_u32 *sa;              /* aligned pointer to source data */
  mlib_u8  *sp;              /* pointer to source data */
  mlib_u32 s0;               /* source data */
  mlib_s32 *dl;              /* pointer to start of destination */
  mlib_d64 *dp;              /* aligned pointer to destination */
  mlib_d64 acc0, acc1;       /* destination data */
  mlib_d64 acc2, acc3, acc4; /* destination data */
  mlib_s32 i;                /* loop variable */
  mlib_u32 s00, s01, s02, s03;

  sa = (mlib_u32*)src;
  dl = dst;
  dp   = (mlib_d64 *) ((mlib_addr) dl & (~7)) + 1;
  vis_alignaddr(dp, 4);

  s0 = *sa++;
  s00 = (s0 >> 21) & 0x7F8;
  acc0 = *(mlib_d64*)((mlib_u8*)table + s00);
  *(mlib_f32*)dl = vis_read_hi(acc0);
  xsize--;
  sp = (mlib_u8*)sa - 3;

  if (xsize >= 3) {
    s01 = (s0 >> 13) & 0x7F8;
    s02 = (s0 >> 5) & 0x7F8;
    s03 = (s0 << 3) & 0x7F8;
    acc1 = *(mlib_d64*)((mlib_u8*)table + s01);
    acc2 = *(mlib_d64*)((mlib_u8*)table + s02);
    acc3 = *(mlib_d64*)((mlib_u8*)table + s03);
    dp[0] = vis_faligndata(acc0, acc1);
    dp[1] = vis_faligndata(acc1, acc2);
    dp[2] = vis_faligndata(acc2, acc3);
    acc0 = acc3; dp += 3; xsize -= 3;
    sp = (mlib_u8*)sa;
  }

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 21) & 0x7F8;
    s01 = (s0 >> 13) & 0x7F8;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp += 4) {
      s02 = (s0 >> 5) & 0x7F8;
      s03 = (s0 << 3) & 0x7F8;
      acc1 = *(mlib_d64*)((mlib_u8*)table + s00);
      acc2 = *(mlib_d64*)((mlib_u8*)table + s01);
      acc3 = *(mlib_d64*)((mlib_u8*)table + s02);
      acc4 = *(mlib_d64*)((mlib_u8*)table + s03);
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
    acc1 = *(mlib_d64*)((mlib_u8*)table + s00);
    acc2 = *(mlib_d64*)((mlib_u8*)table + s01);
    acc3 = *(mlib_d64*)((mlib_u8*)table + s02);
    acc4 = *(mlib_d64*)((mlib_u8*)table + s03);
    dp[0] = vis_faligndata(acc0, acc1);
    dp[1] = vis_faligndata(acc1, acc2);
    dp[2] = vis_faligndata(acc2, acc3);
    dp[3] = vis_faligndata(acc3, acc4);
    acc0 = acc4;
    dp += 4;
    i += 4;
    sp = (mlib_u8*)sa;
  }

  if ( i <= xsize - 2) {
    acc1 = table[sp[0]];
    acc2 = table[sp[1]];
    *dp++ = vis_faligndata(acc0, acc1);
    *dp++ = vis_faligndata(acc1, acc2);
    i+=2; sp += 2;
    acc0 = acc2;
  }

  if ( i < xsize) {
    acc1 = table[sp[0]];
    *dp++ = vis_faligndata(acc0, acc1);
    acc0 = acc1;
  }

  *(mlib_f32*) dp = vis_read_lo(acc0);
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S32_2_SMALL(const mlib_u8  *src,
                                         mlib_s32       *dst,
                                         mlib_s32       xsize,
                                         const mlib_s32 **table)
{
  mlib_u32 *sa;          /* aligned pointer to source data */
  mlib_u8  *sp;          /* pointer to source data */
  mlib_u32 s0;           /* source data */
  mlib_f32 *dp;          /* aligned pointer to destination */
  mlib_f32 acc0, acc1;   /* destination data */
  mlib_f32 acc2, acc3;   /* destination data */
  mlib_f32 acc4, acc5;   /* destination data */
  mlib_f32 acc6, acc7;   /* destination data */
  mlib_f32 *table0 = (mlib_f32*)table[0];
  mlib_f32 *table1 = (mlib_f32*)table[1];
  mlib_s32 i;            /* loop variable */
  mlib_u32 s00, s01, s02, s03;

  sa   = (mlib_u32*)src;
  dp   = (mlib_f32*)dst;

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 22) & 0x3FC;
    s01 = (s0 >> 14) & 0x3FC;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp += 8) {
      s02 = (s0 >> 6) & 0x3FC;
      s03 = (s0 << 2) & 0x3FC;
      acc0 = *(mlib_f32*)((mlib_u8*)table0 + s00);
      acc1 = *(mlib_f32*)((mlib_u8*)table1 + s00);
      acc2 = *(mlib_f32*)((mlib_u8*)table0 + s01);
      acc3 = *(mlib_f32*)((mlib_u8*)table1 + s01);
      acc4 = *(mlib_f32*)((mlib_u8*)table0 + s02);
      acc5 = *(mlib_f32*)((mlib_u8*)table1 + s02);
      acc6 = *(mlib_f32*)((mlib_u8*)table0 + s03);
      acc7 = *(mlib_f32*)((mlib_u8*)table1 + s03);
      s0 = *sa++;
      s00 = (s0 >> 22) & 0x3FC;
      s01 = (s0 >> 14) & 0x3FC;
      dp[0] = acc0;
      dp[1] = acc1;
      dp[2] = acc2;
      dp[3] = acc3;
      dp[4] = acc4;
      dp[5] = acc5;
      dp[6] = acc6;
      dp[7] = acc7;
    }

    s02 = (s0 >> 6) & 0x3FC;
    s03 = (s0 << 2) & 0x3FC;
    acc0 = *(mlib_f32*)((mlib_u8*)table0 + s00);
    acc1 = *(mlib_f32*)((mlib_u8*)table1 + s00);
    acc2 = *(mlib_f32*)((mlib_u8*)table0 + s01);
    acc3 = *(mlib_f32*)((mlib_u8*)table1 + s01);
    acc4 = *(mlib_f32*)((mlib_u8*)table0 + s02);
    acc5 = *(mlib_f32*)((mlib_u8*)table1 + s02);
    acc6 = *(mlib_f32*)((mlib_u8*)table0 + s03);
    acc7 = *(mlib_f32*)((mlib_u8*)table1 + s03);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    dp[3] = acc3;
    dp[4] = acc4;
    dp[5] = acc5;
    dp[6] = acc6;
    dp[7] = acc7;
    dp += 8;
    i += 4;
  }

  sp = (mlib_u8*)sa;

  if ( i < xsize ) {
    *dp++ = table0[sp[0]];
    *dp++ = table1[sp[0]];
    i++; sp++;
  }

  if ( i < xsize ) {
    *dp++ = table0[sp[0]];
    *dp++ = table1[sp[0]];
    i++; sp++;
  }

  if ( i < xsize ) {
    *dp++ = table0[sp[0]];
    *dp++ = table1[sp[0]];
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S32_2(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_s32       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_s32 **table)
{
  if ((xsize * ysize) < 600) {
    mlib_u8  *sl;
    mlib_s32 *dl;
    mlib_s32 j, i;
    const mlib_s32 *tab0 = table[0];
    const mlib_s32 *tab1 = table[1];

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j ++) {
      mlib_u8  *sp = sl;
      mlib_s32 *dp = dl;
      mlib_s32 off, size = xsize;

      off = (mlib_s32)((4 - ((mlib_addr)sp & 3)) & 3);

      off = (off < size) ? off : size;

      for (i = 0; i < off; i++) {
        *dp++ = tab0[sp[0]];
        *dp++ = tab1[sp[0]];
        size--; sp++;
      }

      if (size > 0) {
        mlib_v_ImageLookUpSI_U8_S32_2_SMALL(sp, (mlib_s32*)dp, size, table);
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_s32 *) ((mlib_u8 *) dl + dlb);
    }

  } else {
    mlib_u8  *sl;
    mlib_s32 *dl;
    mlib_d64 dtab[256];
    mlib_u32 *tab;
    mlib_u32 *tab0 = (mlib_u32*)table[0];
    mlib_u32 *tab1 = (mlib_u32*)table[1];
    mlib_s32 i, j;
    mlib_u32 s0, s1;

    tab = (mlib_u32*)dtab;
    s0 = tab0[0];
    s1 = tab1[0];
    for (i = 0; i < 255; i++) {
      tab[2*i] = s0;
      tab[2*i+1] = s1;
      s0 = tab0[i+1];
      s1 = tab1[i+1];
    }

    tab[510] = s0;
    tab[511] = s1;

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j ++) {
      mlib_u8  *sp = sl;
      mlib_u32 *dp = (mlib_u32*)dl;
      mlib_s32 off, size = xsize;

      off = (mlib_s32)((4 - ((mlib_addr)sp & 3)) & 3);

      off = (off < size) ? off : size;

#pragma pipeloop(0)
      for (i = 0; i < off; i++) {
        dp[0] = tab0[sp[0]];
        dp[1] = tab1[sp[0]];
        dp += 2; sp++;
      }

      size -= off;

      if (size > 0) {
        if (((mlib_addr)dp & 7) == 0) {
          mlib_v_ImageLookUpSI_U8_S32_2_SrcOff0_D1(sp, (mlib_s32*)dp, size, dtab);
        } else {
          mlib_v_ImageLookUpSI_U8_S32_2_DstNonAl_D1(sp, (mlib_s32*)dp, size, dtab);
        }
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_s32 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S32_3_SrcOff0_D1(const mlib_u8  *src,
                                              mlib_s32       *dst,
                                              mlib_s32       xsize,
                                              const mlib_d64 *table)
{
  mlib_u8  *sp;              /* pointer to source data */
  mlib_u32 *sa;              /* aligned pointer to source data */
  mlib_u32 s0;               /* source data */
  mlib_s32 *dl;              /* pointer to start of destination */
  mlib_d64 *dp;              /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3;   /* destination data */
  mlib_d64 t4, t5, t6, t7;   /* destination data */
  mlib_s32 i;                /* loop variable */
  mlib_s32 *ptr;
  mlib_u32 s00, s01, s02, s03;

  dl  = dst;
  sp  = (void *)src;
  dp  = (mlib_d64 *) dl;
  sa  = (mlib_u32*)sp;

  vis_alignaddr((void *) 0, 4);

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 20) & 0xFF0;
    s01 = (s0 >> 12) & 0xFF0;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp+=6) {
      s02 = (s0 >> 4) & 0xFF0;
      s03 = (s0 << 4) & 0xFF0;
      t0 = *(mlib_d64*)((mlib_u8*)table + s00);
      t1 = *(mlib_d64*)((mlib_u8*)table + s00 + 8);
      t2 = *(mlib_d64*)((mlib_u8*)table + s01);
      t3 = *(mlib_d64*)((mlib_u8*)table + s01 + 8);
      t4 = *(mlib_d64*)((mlib_u8*)table + s02);
      t5 = *(mlib_d64*)((mlib_u8*)table + s02 + 8);
      t6 = *(mlib_d64*)((mlib_u8*)table + s03);
      t7 = *(mlib_d64*)((mlib_u8*)table + s03 + 8);
      t1 = vis_faligndata(t1, t1);
      t1 = vis_faligndata(t1, t2);
      t2 = vis_faligndata(t2, t3);
      t5 = vis_faligndata(t5, t5);
      t5 = vis_faligndata(t5, t6);
      t6 = vis_faligndata(t6, t7);
      s0 = *sa++;
      s00 = (s0 >> 20) & 0xFF0;
      s01 = (s0 >> 12) & 0xFF0;
      dp[0] = t0;
      dp[1] = t1;
      dp[2] = t2;
      dp[3] = t4;
      dp[4] = t5;
      dp[5] = t6;
    }

    s02 = (s0 >> 4) & 0xFF0;
    s03 = (s0 << 4) & 0xFF0;
    t0 = *(mlib_d64*)((mlib_u8*)table + s00);
    t1 = *(mlib_d64*)((mlib_u8*)table + s00 + 8);
    t2 = *(mlib_d64*)((mlib_u8*)table + s01);
    t3 = *(mlib_d64*)((mlib_u8*)table + s01 + 8);
    t4 = *(mlib_d64*)((mlib_u8*)table + s02);
    t5 = *(mlib_d64*)((mlib_u8*)table + s02 + 8);
    t6 = *(mlib_d64*)((mlib_u8*)table + s03);
    t7 = *(mlib_d64*)((mlib_u8*)table + s03 + 8);
    t1 = vis_faligndata(t1, t1);
    t1 = vis_faligndata(t1, t2);
    t2 = vis_faligndata(t2, t3);
    t5 = vis_faligndata(t5, t5);
    t5 = vis_faligndata(t5, t6);
    t6 = vis_faligndata(t6, t7);
    dp[0] = t0;
    dp[1] = t1;
    dp[2] = t2;
    dp[3] = t4;
    dp[4] = t5;
    dp[5] = t6;
    i += 4; dp += 6;
  }

  dl = (mlib_s32*)dp;
  sp = (mlib_u8*)sa;

#pragma pipeloop(0)
  for (; i < xsize; i++) {
    ptr = (mlib_s32*)(table + (sp[0] << 1));
    dl[0] = ptr[0];
    dl[1] = ptr[1];
    dl[2] = ptr[2];
    dl += 3; sp ++;
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S32_3_DstNonAl_D1(const mlib_u8  *src,
                                               mlib_s32       *dst,
                                               mlib_s32       xsize,
                                               const mlib_d64 *table)
{
  mlib_u8  *sp;              /* pointer to source data */
  mlib_u32 *sa;              /* aligned pointer to source data */
  mlib_u32 s0;               /* source data */
  mlib_s32 *dl;              /* pointer to start of destination */
  mlib_d64 *dp;              /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3;   /* destination data */
  mlib_d64 t4, t5, t6, t7;   /* destination data */
  mlib_s32 i;                /* loop variable */
  mlib_s32 *ptr;
  mlib_u32 s00, s01, s02, s03;

  dl  = dst;
  sp  = (void *)src;
  dp   = (mlib_d64 *) ((mlib_addr) dl & (~7));
  sa  = (mlib_u32*)sp;

  vis_alignaddr((void *) 0, 4);

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 20) & 0xFF0;
    s01 = (s0 >> 12) & 0xFF0;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp+=6) {
      s02 = (s0 >> 4) & 0xFF0;
      s03 = (s0 << 4) & 0xFF0;
      t0 = *(mlib_d64*)((mlib_u8*)table + s00);
      t1 = *(mlib_d64*)((mlib_u8*)table + s00 + 8);
      t2 = *(mlib_d64*)((mlib_u8*)table + s01);
      t3 = *(mlib_d64*)((mlib_u8*)table + s01 + 8);
      t4 = *(mlib_d64*)((mlib_u8*)table + s02);
      t5 = *(mlib_d64*)((mlib_u8*)table + s02 + 8);
      t6 = *(mlib_d64*)((mlib_u8*)table + s03);
      t7 = *(mlib_d64*)((mlib_u8*)table + s03 + 8);
      t1 = vis_faligndata(t0, t1);
      t3 = vis_faligndata(t3, t3);
      t3 = vis_faligndata(t3, t4);
      t4 = vis_faligndata(t4, t5);
      s0 = *sa++;
      s00 = (s0 >> 20) & 0xFF0;
      s01 = (s0 >> 12) & 0xFF0;
      *(mlib_f32*)((mlib_f32*)dp + 1) = vis_read_hi(t0);
      dp[1] = t1;
      dp[2] = t2;
      dp[3] = t3;
      dp[4] = t4;
      dp[5] = t6;
      *(mlib_f32*)((mlib_f32*)dp + 12) = vis_read_hi(t7);
    }

    s02 = (s0 >> 4) & 0xFF0;
    s03 = (s0 << 4) & 0xFF0;
    t0 = *(mlib_d64*)((mlib_u8*)table + s00);
    t1 = *(mlib_d64*)((mlib_u8*)table + s00 + 8);
    t2 = *(mlib_d64*)((mlib_u8*)table + s01);
    t3 = *(mlib_d64*)((mlib_u8*)table + s01 + 8);
    t4 = *(mlib_d64*)((mlib_u8*)table + s02);
    t5 = *(mlib_d64*)((mlib_u8*)table + s02 + 8);
    t6 = *(mlib_d64*)((mlib_u8*)table + s03);
    t7 = *(mlib_d64*)((mlib_u8*)table + s03 + 8);
    t1 = vis_faligndata(t0, t1);
    t3 = vis_faligndata(t3, t3);
    t3 = vis_faligndata(t3, t4);
    t4 = vis_faligndata(t4, t5);
    *(mlib_f32*)((mlib_f32*)dp + 1) = vis_read_hi(t0);
    dp[1] = t1;
    dp[2] = t2;
    dp[3] = t3;
    dp[4] = t4;
    dp[5] = t6;
    *(mlib_f32*)((mlib_f32*)dp + 12) = vis_read_hi(t7);
    i += 4; dp += 6;
  }

  dl = (mlib_s32*)dp + 1;
  sp = (mlib_u8*)sa;

#pragma pipeloop(0)
  for (; i < xsize; i++) {
    ptr = (mlib_s32*)(table + (sp[0] << 1));
    dl[0] = ptr[0];
    dl[1] = ptr[1];
    dl[2] = ptr[2];
    dl += 3; sp ++;
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S32_3_SMALL(const mlib_u8  *src,
                                         mlib_s32       *dst,
                                         mlib_s32       xsize,
                                         const mlib_s32 **table)
{
  mlib_u32 *sa;          /* aligned pointer to source data */
  mlib_u8  *sp;          /* pointer to source data */
  mlib_u32 s0;           /* source data */
  mlib_f32 *dp;          /* aligned pointer to destination */
  mlib_f32 acc0, acc1;   /* destination data */
  mlib_f32 acc2, acc3;   /* destination data */
  mlib_f32 acc4, acc5;   /* destination data */
  mlib_f32 acc6, acc7;   /* destination data */
  mlib_f32 acc8, acc9;   /* destination data */
  mlib_f32 acc10, acc11; /* destination data */
  mlib_f32 *table0 = (mlib_f32*)table[0];
  mlib_f32 *table1 = (mlib_f32*)table[1];
  mlib_f32 *table2 = (mlib_f32*)table[2];
  mlib_s32 i;            /* loop variable */
  mlib_u32 s00, s01, s02, s03;

  sa   = (mlib_u32*)src;
  dp   = (mlib_f32*)dst;

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 22) & 0x3FC;
    s01 = (s0 >> 14) & 0x3FC;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp += 12) {
      s02 = (s0 >> 6) & 0x3FC;
      s03 = (s0 << 2) & 0x3FC;
      acc0 = *(mlib_f32*)((mlib_u8*)table0 + s00);
      acc1 = *(mlib_f32*)((mlib_u8*)table1 + s00);
      acc2 = *(mlib_f32*)((mlib_u8*)table2 + s00);
      acc3 = *(mlib_f32*)((mlib_u8*)table0 + s01);
      acc4 = *(mlib_f32*)((mlib_u8*)table1 + s01);
      acc5 = *(mlib_f32*)((mlib_u8*)table2 + s01);
      acc6 = *(mlib_f32*)((mlib_u8*)table0 + s02);
      acc7 = *(mlib_f32*)((mlib_u8*)table1 + s02);
      acc8 = *(mlib_f32*)((mlib_u8*)table2 + s02);
      acc9 = *(mlib_f32*)((mlib_u8*)table0 + s03);
      acc10 = *(mlib_f32*)((mlib_u8*)table1 + s03);
      acc11 = *(mlib_f32*)((mlib_u8*)table2 + s03);
      s0 = *sa++;
      s00 = (s0 >> 22) & 0x3FC;
      s01 = (s0 >> 14) & 0x3FC;
      dp[0] = acc0;
      dp[1] = acc1;
      dp[2] = acc2;
      dp[3] = acc3;
      dp[4] = acc4;
      dp[5] = acc5;
      dp[6] = acc6;
      dp[7] = acc7;
      dp[8] = acc8;
      dp[9] = acc9;
      dp[10] = acc10;
      dp[11] = acc11;
    }

    s02 = (s0 >> 6) & 0x3FC;
    s03 = (s0 << 2) & 0x3FC;
    acc0 = *(mlib_f32*)((mlib_u8*)table0 + s00);
    acc1 = *(mlib_f32*)((mlib_u8*)table1 + s00);
    acc2 = *(mlib_f32*)((mlib_u8*)table2 + s00);
    acc3 = *(mlib_f32*)((mlib_u8*)table0 + s01);
    acc4 = *(mlib_f32*)((mlib_u8*)table1 + s01);
    acc5 = *(mlib_f32*)((mlib_u8*)table2 + s01);
    acc6 = *(mlib_f32*)((mlib_u8*)table0 + s02);
    acc7 = *(mlib_f32*)((mlib_u8*)table1 + s02);
    acc8 = *(mlib_f32*)((mlib_u8*)table2 + s02);
    acc9 = *(mlib_f32*)((mlib_u8*)table0 + s03);
    acc10 = *(mlib_f32*)((mlib_u8*)table1 + s03);
    acc11 = *(mlib_f32*)((mlib_u8*)table2 + s03);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    dp[3] = acc3;
    dp[4] = acc4;
    dp[5] = acc5;
    dp[6] = acc6;
    dp[7] = acc7;
    dp[8] = acc8;
    dp[9] = acc9;
    dp[10] = acc10;
    dp[11] = acc11;
    dp += 12;
    i += 4;
  }

  sp = (mlib_u8*)sa;

  if ( i < xsize ) {
    *dp++ = table0[sp[0]];
    *dp++ = table1[sp[0]];
    *dp++ = table2[sp[0]];
    i++; sp++;
  }

  if ( i < xsize ) {
    *dp++ = table0[sp[0]];
    *dp++ = table1[sp[0]];
    *dp++ = table2[sp[0]];
    i++; sp++;
  }

  if ( i < xsize ) {
    *dp++ = table0[sp[0]];
    *dp++ = table1[sp[0]];
    *dp++ = table2[sp[0]];
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S32_3(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_s32       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_s32 **table)
{
  if ((xsize * ysize) < 600) {
    mlib_u8  *sl;
    mlib_s32 *dl;
    mlib_s32 j, i;
    const mlib_s32 *tab0 = table[0];
    const mlib_s32 *tab1 = table[1];
    const mlib_s32 *tab2 = table[2];

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j ++) {
      mlib_u8  *sp = sl;
      mlib_s32 *dp = dl;
      mlib_s32 off, size = xsize;

      off = (mlib_s32)((4 - ((mlib_addr)sp & 3)) & 3);

      off = (off < size) ? off : size;

      for (i = 0; i < off; i++) {
        *dp++ = tab0[sp[0]];
        *dp++ = tab1[sp[0]];
        *dp++ = tab2[sp[0]];
        size--; sp++;
      }

      if (size > 0) {
        mlib_v_ImageLookUpSI_U8_S32_3_SMALL(sp, (mlib_s32*)dp, size, table);
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_s32 *) ((mlib_u8 *) dl + dlb);
    }

  } else {
    mlib_u8  *sl;
    mlib_s32 *dl;
    mlib_d64 dtab[512];
    mlib_u32 *tab;
    mlib_u32 *tab0 = (mlib_u32*)table[0];
    mlib_u32 *tab1 = (mlib_u32*)table[1];
    mlib_u32 *tab2 = (mlib_u32*)table[2];
    mlib_s32 i, j;
    mlib_u32 s0, s1, s2;

    tab = (mlib_u32*)dtab;
    s0 = tab0[0];
    s1 = tab1[0];
    s2 = tab2[0];
    for (i = 0; i < 255; i++) {
      tab[4*i] = s0;
      tab[4*i+1] = s1;
      tab[4*i+2] = s2;
      s0 = tab0[i+1];
      s1 = tab1[i+1];
      s2 = tab2[i+1];
    }

    tab[1020] = s0;
    tab[1021] = s1;
    tab[1022] = s2;

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j ++) {
      mlib_u8  *sp = sl;
      mlib_u32 *dp = (mlib_u32*)dl;
      mlib_s32 off, size = xsize;

      off = (mlib_s32)((4 - ((mlib_addr)sp & 3)) & 3);

      off = (off < size) ? off : size;

#pragma pipeloop(0)
      for (i = 0; i < off; i++) {
        dp[0] = tab0[sp[0]];
        dp[1] = tab1[sp[0]];
        dp[2] = tab2[sp[0]];
        dp += 3; sp++;
      }

      size -= off;

      if (size > 0) {
        if (((mlib_addr)dp & 7) == 0) {
          mlib_v_ImageLookUpSI_U8_S32_3_SrcOff0_D1(sp, (mlib_s32*)dp, size, dtab);
        } else {
          mlib_v_ImageLookUpSI_U8_S32_3_DstNonAl_D1(sp, (mlib_s32*)dp, size, dtab);
        }
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_s32 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S32_4_SrcOff0_D1(const mlib_u8  *src,
                                              mlib_s32       *dst,
                                              mlib_s32       xsize,
                                              const mlib_d64 *table)
{
  mlib_u32 *sa;            /* aligned pointer to source data */
  mlib_u8  *sp;            /* pointer to source data */
  mlib_u32 s0;             /* source data */
  mlib_d64 *dp;            /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3; /* destination data */
  mlib_d64 t4, t5, t6, t7; /* destination data */
  mlib_s32 i;              /* loop variable */
  mlib_u32 s00, s01, s02, s03;

  sa   = (mlib_u32*)src;
  dp   = (mlib_d64 *) dst;

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 20) & 0xFF0;
    s01 = (s0 >> 12) & 0xFF0;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp+=8) {
      s02 = (s0 >> 4) & 0xFF0;
      s03 = (s0 << 4) & 0xFF0;
      t0 = *(mlib_d64*)((mlib_u8*)table + s00);
      t1 = *(mlib_d64*)((mlib_u8*)table + s00 + 8);
      t2 = *(mlib_d64*)((mlib_u8*)table + s01);
      t3 = *(mlib_d64*)((mlib_u8*)table + s01 + 8);
      t4 = *(mlib_d64*)((mlib_u8*)table + s02);
      t5 = *(mlib_d64*)((mlib_u8*)table + s02 + 8);
      t6 = *(mlib_d64*)((mlib_u8*)table + s03);
      t7 = *(mlib_d64*)((mlib_u8*)table + s03 + 8);
      s0 = *sa++;
      s00 = (s0 >> 20) & 0xFF0;
      s01 = (s0 >> 12) & 0xFF0;
      dp[0] = t0;
      dp[1] = t1;
      dp[2] = t2;
      dp[3] = t3;
      dp[4] = t4;
      dp[5] = t5;
      dp[6] = t6;
      dp[7] = t7;
    }

    s02 = (s0 >> 4) & 0xFF0;
    s03 = (s0 << 4) & 0xFF0;
    t0 = *(mlib_d64*)((mlib_u8*)table + s00);
    t1 = *(mlib_d64*)((mlib_u8*)table + s00 + 8);
    t2 = *(mlib_d64*)((mlib_u8*)table + s01);
    t3 = *(mlib_d64*)((mlib_u8*)table + s01 + 8);
    t4 = *(mlib_d64*)((mlib_u8*)table + s02);
    t5 = *(mlib_d64*)((mlib_u8*)table + s02 + 8);
    t6 = *(mlib_d64*)((mlib_u8*)table + s03);
    t7 = *(mlib_d64*)((mlib_u8*)table + s03 + 8);
    dp[0] = t0;
    dp[1] = t1;
    dp[2] = t2;
    dp[3] = t3;
    dp[4] = t4;
    dp[5] = t5;
    dp[6] = t6;
    dp[7] = t7;
    dp += 8;
    i += 4;
  }

  sp = (mlib_u8*)sa;

  if ( i < xsize ) {
    *dp++ = table[2*sp[0]];
    *dp++ = table[2*sp[0] + 1];
    i++; sp++;
  }

  if ( i < xsize ) {
    *dp++ = table[2*sp[0]];
    *dp++ = table[2*sp[0] + 1];
    i++; sp++;
  }

  if ( i < xsize ) {
    *dp++ = table[2*sp[0]];
    *dp++ = table[2*sp[0] + 1];
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S32_4_DstNonAl_D1(const mlib_u8  *src,
                                               mlib_s32       *dst,
                                               mlib_s32       xsize,
                                               const mlib_d64 *table)
{
  mlib_u32 *sa;                /* aligned pointer to source data */
  mlib_u8  *sp;                /* pointer to source data */
  mlib_u32 s0;                 /* source data */
  mlib_s32 *dl;                /* pointer to start of destination */
  mlib_d64 *dp;                /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3;     /* destination data */
  mlib_d64 t4, t5, t6, t7, t8; /* destination data */
  mlib_s32 i;                  /* loop variable */
  mlib_u32 s00, s01, s02, s03;

  sa = (mlib_u32*)src;
  dl = dst;
  dp   = (mlib_d64 *) ((mlib_addr) dl & (~7)) + 1;
  vis_alignaddr(dp, 4);
  s0 = *sa++;
  s00 = (s0 >> 20) & 0xFF0;
  t0 = *(mlib_d64*)((mlib_u8*)table + s00);
  t1 = *(mlib_d64*)((mlib_u8*)table + s00 + 8);
  *(mlib_f32*)dl = vis_read_hi(t0);
  dp[0] = vis_faligndata(t0, t1);
  t0 = t1;
  xsize--; dp++;
  sp = (mlib_u8*)sa - 3;

  if (xsize >= 3) {
    s01 = (s0 >> 12) & 0xFF0;
    s02 = (s0 >> 4) & 0xFF0;
    s03 = (s0 << 4) & 0xFF0;
    t1 = *(mlib_d64*)((mlib_u8*)table + s01);
    t2 = *(mlib_d64*)((mlib_u8*)table + s01 + 8);
    t3 = *(mlib_d64*)((mlib_u8*)table + s02);
    t4 = *(mlib_d64*)((mlib_u8*)table + s02 + 8);
    t5 = *(mlib_d64*)((mlib_u8*)table + s03);
    t6 = *(mlib_d64*)((mlib_u8*)table + s03 + 8);
    dp[0] = vis_faligndata(t0, t1);
    dp[1] = vis_faligndata(t1, t2);
    dp[2] = vis_faligndata(t2, t3);
    dp[3] = vis_faligndata(t3, t4);
    dp[4] = vis_faligndata(t4, t5);
    dp[5] = vis_faligndata(t5, t6);
    t0 = t6; dp += 6; xsize -= 3;
    sp = (mlib_u8*)sa;
  }

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 20) & 0xFF0;
    s01 = (s0 >> 12) & 0xFF0;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp += 8) {
      s02 = (s0 >> 4) & 0xFF0;
      s03 = (s0 << 4) & 0xFF0;
      t1 = *(mlib_d64*)((mlib_u8*)table + s00);
      t2 = *(mlib_d64*)((mlib_u8*)table + s00 + 8);
      t3 = *(mlib_d64*)((mlib_u8*)table + s01);
      t4 = *(mlib_d64*)((mlib_u8*)table + s01 + 8);
      t5 = *(mlib_d64*)((mlib_u8*)table + s02);
      t6 = *(mlib_d64*)((mlib_u8*)table + s02 + 8);
      t7 = *(mlib_d64*)((mlib_u8*)table + s03);
      t8 = *(mlib_d64*)((mlib_u8*)table + s03 + 8);
      s0 = *sa++;
      s00 = (s0 >> 20) & 0xFF0;
      s01 = (s0 >> 12) & 0xFF0;
      dp[0] = vis_faligndata(t0, t1);
      dp[1] = vis_faligndata(t1, t2);
      dp[2] = vis_faligndata(t2, t3);
      dp[3] = vis_faligndata(t3, t4);
      dp[4] = vis_faligndata(t4, t5);
      dp[5] = vis_faligndata(t5, t6);
      dp[6] = vis_faligndata(t6, t7);
      dp[7] = vis_faligndata(t7, t8);
      t0 = t8;
    }

    s02 = (s0 >> 4) & 0xFF0;
    s03 = (s0 << 4) & 0xFF0;
    t1 = *(mlib_d64*)((mlib_u8*)table + s00);
    t2 = *(mlib_d64*)((mlib_u8*)table + s00 + 8);
    t3 = *(mlib_d64*)((mlib_u8*)table + s01);
    t4 = *(mlib_d64*)((mlib_u8*)table + s01 + 8);
    t5 = *(mlib_d64*)((mlib_u8*)table + s02);
    t6 = *(mlib_d64*)((mlib_u8*)table + s02 + 8);
    t7 = *(mlib_d64*)((mlib_u8*)table + s03);
    t8 = *(mlib_d64*)((mlib_u8*)table + s03 + 8);
    dp[0] = vis_faligndata(t0, t1);
    dp[1] = vis_faligndata(t1, t2);
    dp[2] = vis_faligndata(t2, t3);
    dp[3] = vis_faligndata(t3, t4);
    dp[4] = vis_faligndata(t4, t5);
    dp[5] = vis_faligndata(t5, t6);
    dp[6] = vis_faligndata(t6, t7);
    dp[7] = vis_faligndata(t7, t8);
    t0 = t8;
    dp += 8;
    i += 4;
    sp = (mlib_u8*)sa;
  }

  if ( i < xsize ) {
    t1 = table[2*sp[0]];
    t2 = table[2*sp[0] + 1];
    *dp++ = vis_faligndata(t0, t1);
    *dp++ = vis_faligndata(t1, t2);
    i++; sp++;
    t0 = t2;
  }

  if ( i < xsize ) {
    t1 = table[2*sp[0]];
    t2 = table[2*sp[0] + 1];
    *dp++ = vis_faligndata(t0, t1);
    *dp++ = vis_faligndata(t1, t2);
    i++; sp++;
    t0 = t2;
  }

  if ( i < xsize ) {
    t1 = table[2*sp[0]];
    t2 = table[2*sp[0] + 1];
    *dp++ = vis_faligndata(t0, t1);
    *dp++ = vis_faligndata(t1, t2);
    t0 = t2;
  }

  *(mlib_f32*) dp = vis_read_lo(t0);
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S32_4_SMALL(const mlib_u8  *src,
                                         mlib_s32       *dst,
                                         mlib_s32       xsize,
                                         const mlib_s32 **table)
{
  mlib_u32 *sa;          /* aligned pointer to source data */
  mlib_u8  *sp;          /* pointer to source data */
  mlib_u32 s0;           /* source data */
  mlib_f32 *dp;          /* aligned pointer to destination */
  mlib_f32 acc0, acc1;   /* destination data */
  mlib_f32 acc2, acc3;   /* destination data */
  mlib_f32 acc4, acc5;   /* destination data */
  mlib_f32 acc6, acc7;   /* destination data */
  mlib_f32 acc8, acc9;   /* destination data */
  mlib_f32 acc10, acc11; /* destination data */
  mlib_f32 acc12, acc13; /* destination data */
  mlib_f32 acc14, acc15; /* destination data */
  mlib_f32 *table0 = (mlib_f32*)table[0];
  mlib_f32 *table1 = (mlib_f32*)table[1];
  mlib_f32 *table2 = (mlib_f32*)table[2];
  mlib_f32 *table3 = (mlib_f32*)table[3];
  mlib_s32 i;            /* loop variable */
  mlib_u32 s00, s01, s02, s03;

  sa   = (mlib_u32*)src;
  dp   = (mlib_f32*)dst;

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 22) & 0x3FC;
    s01 = (s0 >> 14) & 0x3FC;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp += 16) {
      s02 = (s0 >> 6) & 0x3FC;
      s03 = (s0 << 2) & 0x3FC;
      acc0 = *(mlib_f32*)((mlib_u8*)table0 + s00);
      acc1 = *(mlib_f32*)((mlib_u8*)table1 + s00);
      acc2 = *(mlib_f32*)((mlib_u8*)table2 + s00);
      acc3 = *(mlib_f32*)((mlib_u8*)table3 + s00);
      acc4 = *(mlib_f32*)((mlib_u8*)table0 + s01);
      acc5 = *(mlib_f32*)((mlib_u8*)table1 + s01);
      acc6 = *(mlib_f32*)((mlib_u8*)table2 + s01);
      acc7 = *(mlib_f32*)((mlib_u8*)table3 + s01);
      acc8 = *(mlib_f32*)((mlib_u8*)table0 + s02);
      acc9 = *(mlib_f32*)((mlib_u8*)table1 + s02);
      acc10 = *(mlib_f32*)((mlib_u8*)table2 + s02);
      acc11 = *(mlib_f32*)((mlib_u8*)table3 + s02);
      acc12 = *(mlib_f32*)((mlib_u8*)table0 + s03);
      acc13 = *(mlib_f32*)((mlib_u8*)table1 + s03);
      acc14 = *(mlib_f32*)((mlib_u8*)table2 + s03);
      acc15 = *(mlib_f32*)((mlib_u8*)table3 + s03);
      s0 = *sa++;
      s00 = (s0 >> 22) & 0x3FC;
      s01 = (s0 >> 14) & 0x3FC;
      dp[0] = acc0;
      dp[1] = acc1;
      dp[2] = acc2;
      dp[3] = acc3;
      dp[4] = acc4;
      dp[5] = acc5;
      dp[6] = acc6;
      dp[7] = acc7;
      dp[8] = acc8;
      dp[9] = acc9;
      dp[10] = acc10;
      dp[11] = acc11;
      dp[12] = acc12;
      dp[13] = acc13;
      dp[14] = acc14;
      dp[15] = acc15;
    }

    s02 = (s0 >> 6) & 0x3FC;
    s03 = (s0 << 2) & 0x3FC;
    acc0 = *(mlib_f32*)((mlib_u8*)table0 + s00);
    acc1 = *(mlib_f32*)((mlib_u8*)table1 + s00);
    acc2 = *(mlib_f32*)((mlib_u8*)table2 + s00);
    acc3 = *(mlib_f32*)((mlib_u8*)table3 + s00);
    acc4 = *(mlib_f32*)((mlib_u8*)table0 + s01);
    acc5 = *(mlib_f32*)((mlib_u8*)table1 + s01);
    acc6 = *(mlib_f32*)((mlib_u8*)table2 + s01);
    acc7 = *(mlib_f32*)((mlib_u8*)table3 + s01);
    acc8 = *(mlib_f32*)((mlib_u8*)table0 + s02);
    acc9 = *(mlib_f32*)((mlib_u8*)table1 + s02);
    acc10 = *(mlib_f32*)((mlib_u8*)table2 + s02);
    acc11 = *(mlib_f32*)((mlib_u8*)table3 + s02);
    acc12 = *(mlib_f32*)((mlib_u8*)table0 + s03);
    acc13 = *(mlib_f32*)((mlib_u8*)table1 + s03);
    acc14 = *(mlib_f32*)((mlib_u8*)table2 + s03);
    acc15 = *(mlib_f32*)((mlib_u8*)table3 + s03);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    dp[3] = acc3;
    dp[4] = acc4;
    dp[5] = acc5;
    dp[6] = acc6;
    dp[7] = acc7;
    dp[8] = acc8;
    dp[9] = acc9;
    dp[10] = acc10;
    dp[11] = acc11;
    dp[12] = acc12;
    dp[13] = acc13;
    dp[14] = acc14;
    dp[15] = acc15;
    dp += 16;
    i += 4;
  }

  sp = (mlib_u8*)sa;

  if ( i < xsize ) {
    *dp++ = table0[sp[0]];
    *dp++ = table1[sp[0]];
    *dp++ = table2[sp[0]];
    *dp++ = table3[sp[0]];
    i++; sp++;
  }

  if ( i < xsize ) {
    *dp++ = table0[sp[0]];
    *dp++ = table1[sp[0]];
    *dp++ = table2[sp[0]];
    *dp++ = table3[sp[0]];
    i++; sp++;
  }

  if ( i < xsize ) {
    *dp++ = table0[sp[0]];
    *dp++ = table1[sp[0]];
    *dp++ = table2[sp[0]];
    *dp++ = table3[sp[0]];
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_S32_4(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_s32       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_s32 **table)
{
  if ((xsize * ysize) < 600) {
    mlib_u8  *sl;
    mlib_s32 *dl;
    mlib_s32 j, i;
    const mlib_s32 *tab0 = table[0];
    const mlib_s32 *tab1 = table[1];
    const mlib_s32 *tab2 = table[2];
    const mlib_s32 *tab3 = table[3];

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j ++) {
      mlib_u8  *sp = sl;
      mlib_s32 *dp = dl;
      mlib_s32 off, size = xsize;

      off = (mlib_s32)((4 - ((mlib_addr)sp & 3)) & 3);

      off = (off < size) ? off : size;

      for (i = 0; i < off; i++) {
        *dp++ = tab0[sp[0]];
        *dp++ = tab1[sp[0]];
        *dp++ = tab2[sp[0]];
        *dp++ = tab3[sp[0]];
        size--; sp++;
      }

      if (size > 0) {
        mlib_v_ImageLookUpSI_U8_S32_4_SMALL(sp, (mlib_s32*)dp, size, table);
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_s32 *) ((mlib_u8 *) dl + dlb);
    }

  } else {
    mlib_u8  *sl;
    mlib_s32 *dl;
    mlib_d64 dtab[512];
    mlib_u32 *tab;
    mlib_u32 *tab0 = (mlib_u32*)table[0];
    mlib_u32 *tab1 = (mlib_u32*)table[1];
    mlib_u32 *tab2 = (mlib_u32*)table[2];
    mlib_u32 *tab3 = (mlib_u32*)table[3];
    mlib_s32 i, j;
    mlib_u32 s0, s1, s2, s3;

    tab = (mlib_u32*)dtab;
    s0 = tab0[0];
    s1 = tab1[0];
    s2 = tab2[0];
    s3 = tab3[0];
    for (i = 0; i < 255; i++) {
      tab[4*i] = s0;
      tab[4*i+1] = s1;
      tab[4*i+2] = s2;
      tab[4*i+3] = s3;
      s0 = tab0[i+1];
      s1 = tab1[i+1];
      s2 = tab2[i+1];
      s3 = tab3[i+1];
    }

    tab[1020] = s0;
    tab[1021] = s1;
    tab[1022] = s2;
    tab[1023] = s3;

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j ++) {
      mlib_u8  *sp = sl;
      mlib_u32 *dp = (mlib_u32*)dl;
      mlib_s32 off, size = xsize;

      off = (mlib_s32)((4 - ((mlib_addr)sp & 3)) & 3);

      off = (off < size) ? off : size;

#pragma pipeloop(0)
      for (i = 0; i < off; i++) {
        dp[0] = tab0[sp[0]];
        dp[1] = tab1[sp[0]];
        dp[2] = tab2[sp[0]];
        dp[3] = tab3[sp[0]];
        dp += 4; sp++;
      }

      size -= off;

      if (size > 0) {
        if (((mlib_addr)dp & 7) == 0) {
          mlib_v_ImageLookUpSI_U8_S32_4_SrcOff0_D1(sp, (mlib_s32*)dp, size, dtab);
        } else {
          mlib_v_ImageLookUpSI_U8_S32_4_DstNonAl_D1(sp, (mlib_s32*)dp, size, dtab);
        }
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_s32 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
