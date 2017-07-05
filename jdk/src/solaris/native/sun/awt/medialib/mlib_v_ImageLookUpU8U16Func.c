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



#include "vis_proto.h"
#include "mlib_image.h"
#include "mlib_v_ImageLookUpFunc.h"

/***************************************************************/
static void mlib_v_ImageLookUp_U8_U16_124_SrcOff0_D1(const mlib_u8  *src,
                                                     mlib_u16       *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_u16 *table0,
                                                     const mlib_u16 *table1,
                                                     const mlib_u16 *table2,
                                                     const mlib_u16 *table3);

static void mlib_v_ImageLookUp_U8_U16_124_SrcOff1_D1(const mlib_u8  *src,
                                                     mlib_u16       *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_u16 *table0,
                                                     const mlib_u16 *table1,
                                                     const mlib_u16 *table2,
                                                     const mlib_u16 *table3);

static void mlib_v_ImageLookUp_U8_U16_124_SrcOff2_D1(const mlib_u8  *src,
                                                     mlib_u16       *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_u16 *table0,
                                                     const mlib_u16 *table1,
                                                     const mlib_u16 *table2,
                                                     const mlib_u16 *table3);

static void mlib_v_ImageLookUp_U8_U16_124_SrcOff3_D1(const mlib_u8  *src,
                                                     mlib_u16       *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_u16 *table0,
                                                     const mlib_u16 *table1,
                                                     const mlib_u16 *table2,
                                                     const mlib_u16 *table3);

static void mlib_v_ImageLookUp_U8_U16_3_SrcOff0_D1(const mlib_u8  *src,
                                                   mlib_u16       *dst,
                                                   mlib_s32       xsize,
                                                   const mlib_u16 *table0,
                                                   const mlib_u16 *table1,
                                                   const mlib_u16 *table2);

static void mlib_v_ImageLookUp_U8_U16_3_SrcOff1_D1(const mlib_u8  *src,
                                                   mlib_u16       *dst,
                                                   mlib_s32       xsize,
                                                   const mlib_u16 *table0,
                                                   const mlib_u16 *table1,
                                                   const mlib_u16 *table2);

static void mlib_v_ImageLookUp_U8_U16_3_SrcOff2_D1(const mlib_u8  *src,
                                                   mlib_u16       *dst,
                                                   mlib_s32       xsize,
                                                   const mlib_u16 *table0,
                                                   const mlib_u16 *table1,
                                                   const mlib_u16 *table2);

static void mlib_v_ImageLookUp_U8_U16_3_SrcOff3_D1(const mlib_u8  *src,
                                                   mlib_u16       *dst,
                                                   mlib_s32       xsize,
                                                   const mlib_u16 *table0,
                                                   const mlib_u16 *table1,
                                                   const mlib_u16 *table2);

/***************************************************************/
#define VIS_LD_U16_I(X, Y)      vis_ld_u16_i((void *)(X), (Y))

/***************************************************************/
void mlib_v_ImageLookUp_U8_U16_124_SrcOff0_D1(const mlib_u8  *src,
                                              mlib_u16       *dst,
                                              mlib_s32       xsize,
                                              const mlib_u16 *table0,
                                              const mlib_u16 *table1,
                                              const mlib_u16 *table2,
                                              const mlib_u16 *table3)
{
  mlib_u32 *sa;          /* aligned pointer to source data */
  mlib_u8  *sp;          /* pointer to source data */
  mlib_u32 s0;           /* source data */
  mlib_u16 *dl;          /* pointer to start of destination */
  mlib_u16 *dend;        /* pointer to end of destination */
  mlib_d64 *dp;          /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;   /* destination data */
  mlib_d64 t3, acc0;     /* destination data */
  mlib_s32 emask;        /* edge mask */
  mlib_s32 i, num;       /* loop variable */

  sa   = (mlib_u32*)src;
  dl   = dst;
  dp   = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *) 0, 6);

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4) {
      t3 = VIS_LD_U16_I(table3, (s0 << 1) & 0x1FE);
      t2 = VIS_LD_U16_I(table2, (s0 >> 7) & 0x1FE);
      t1 = VIS_LD_U16_I(table1, (s0 >> 15) & 0x1FE);
      t0 = VIS_LD_U16_I(table0, (s0 >> 23) & 0x1FE);
      acc0 = vis_faligndata(t3, acc0);
      acc0 = vis_faligndata(t2, acc0);
      acc0 = vis_faligndata(t1, acc0);
      acc0 = vis_faligndata(t0, acc0);
      s0 = *sa++;
      *dp++ = acc0;
    }

    t3 = VIS_LD_U16_I(table3, (s0 << 1) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s0 >> 7) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s0 >> 15) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 >> 23) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    *dp++ = acc0;
  }

  sp = (mlib_u8*)sa;

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_u16*) dend - (mlib_u16*) dp;
    sp  += num;
    num ++;

    if (num == 1) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num  == 2) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num == 3) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table2, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc0, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_U16_124_SrcOff1_D1(const mlib_u8  *src,
                                              mlib_u16       *dst,
                                              mlib_s32       xsize,
                                              const mlib_u16 *table0,
                                              const mlib_u16 *table1,
                                              const mlib_u16 *table2,
                                              const mlib_u16 *table3)
{
  mlib_u32 *sa;          /* aligned pointer to source data */
  mlib_u8  *sp;          /* pointer to source data */
  mlib_u32 s0, s1;       /* source data */
  mlib_u16 *dl;          /* pointer to start of destination */
  mlib_u16 *dend;        /* pointer to end of destination */
  mlib_d64 *dp;          /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;   /* destination data */
  mlib_d64 t3, acc0;     /* destination data */
  mlib_s32 emask;        /* edge mask */
  mlib_s32 i, num;       /* loop variable */

  sa   = (mlib_u32*)(src - 1);
  dl   = dst;
  dp   = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *) 0, 6);

  s0 = *sa++;

  if (xsize >= 4) {

    s1 = *sa++;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4) {
      t3 = VIS_LD_U16_I(table3, (s1 >> 23) & 0x1FE);
      t2 = VIS_LD_U16_I(table2, (s0 << 1) & 0x1FE);
      t1 = VIS_LD_U16_I(table1, (s0 >> 7) & 0x1FE);
      t0 = VIS_LD_U16_I(table0, (s0 >> 15) & 0x1FE);
      acc0 = vis_faligndata(t3, acc0);
      acc0 = vis_faligndata(t2, acc0);
      acc0 = vis_faligndata(t1, acc0);
      acc0 = vis_faligndata(t0, acc0);
      s0 = s1;
      s1 = *sa++;
      *dp++ = acc0;
    }

    t3 = VIS_LD_U16_I(table3, (s1 >> 23) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s0 << 1) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s0 >> 7) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 >> 15) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    s0 = s1;
    *dp++ = acc0;
  }

  sp = (mlib_u8*)sa;
  sp -= 3;

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_u16*) dend - (mlib_u16*) dp;
    sp  += num;
    num ++;

    if (num == 1) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num  == 2) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num == 3) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table2, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc0, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_U16_124_SrcOff2_D1(const mlib_u8  *src,
                                              mlib_u16       *dst,
                                              mlib_s32       xsize,
                                              const mlib_u16 *table0,
                                              const mlib_u16 *table1,
                                              const mlib_u16 *table2,
                                              const mlib_u16 *table3)
{
  mlib_u32 *sa;          /* aligned pointer to source data */
  mlib_u8  *sp;          /* pointer to source data */
  mlib_u32 s0, s1;       /* source data */
  mlib_u16 *dl;          /* pointer to start of destination */
  mlib_u16 *dend;        /* pointer to end of destination */
  mlib_d64 *dp;          /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;   /* destination data */
  mlib_d64 t3, acc0;     /* destination data */
  mlib_s32 emask;        /* edge mask */
  mlib_s32 i, num;       /* loop variable */

  sa   = (mlib_u32*)(src - 2);
  dl   = dst;
  dp   = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *) 0, 6);

  s0 = *sa++;

  if (xsize >= 4) {

    s1 = *sa++;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4) {
      t3 = VIS_LD_U16_I(table3, (s1 >> 15) & 0x1FE);
      t2 = VIS_LD_U16_I(table2, (s1 >> 23) & 0x1FE);
      t1 = VIS_LD_U16_I(table1, (s0 << 1) & 0x1FE);
      t0 = VIS_LD_U16_I(table0, (s0 >> 7) & 0x1FE);
      acc0 = vis_faligndata(t3, acc0);
      acc0 = vis_faligndata(t2, acc0);
      acc0 = vis_faligndata(t1, acc0);
      acc0 = vis_faligndata(t0, acc0);
      s0 = s1;
      s1 = *sa++;
      *dp++ = acc0;
    }

    t3 = VIS_LD_U16_I(table3, (s1 >> 15) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s1 >> 23) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s0 << 1) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 >> 7) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    s0 = s1;
    *dp++ = acc0;
  }

  sp = (mlib_u8*)sa;
  sp -= 2;

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_u16*) dend - (mlib_u16*) dp;
    sp  += num;
    num ++;

    if (num == 1) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num  == 2) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num == 3) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table2, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc0, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_U16_124_SrcOff3_D1(const mlib_u8  *src,
                                              mlib_u16       *dst,
                                              mlib_s32       xsize,
                                              const mlib_u16 *table0,
                                              const mlib_u16 *table1,
                                              const mlib_u16 *table2,
                                              const mlib_u16 *table3)
{
  mlib_u32 *sa;          /* aligned pointer to source data */
  mlib_u8  *sp;          /* pointer to source data */
  mlib_u32 s0, s1;       /* source data */
  mlib_u16 *dl;          /* pointer to start of destination */
  mlib_u16 *dend;        /* pointer to end of destination */
  mlib_d64 *dp;          /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;   /* destination data */
  mlib_d64 t3, acc0;     /* destination data */
  mlib_s32 emask;        /* edge mask */
  mlib_s32 i, num;       /* loop variable */

  sa   = (mlib_u32*)(src - 3);
  dl   = dst;
  dp   = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *) 0, 6);

  s0 = *sa++;

  if (xsize >= 4) {

    s1 = *sa++;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4) {
      t3 = VIS_LD_U16_I(table3, (s1 >> 7) & 0x1FE);
      t2 = VIS_LD_U16_I(table2, (s1 >> 15) & 0x1FE);
      t1 = VIS_LD_U16_I(table1, (s1 >> 23) & 0x1FE);
      t0 = VIS_LD_U16_I(table0, (s0 << 1) & 0x1FE);
      acc0 = vis_faligndata(t3, acc0);
      acc0 = vis_faligndata(t2, acc0);
      acc0 = vis_faligndata(t1, acc0);
      acc0 = vis_faligndata(t0, acc0);
      s0 = s1;
      s1 = *sa++;
      *dp++ = acc0;
    }

    t3 = VIS_LD_U16_I(table3, (s1 >> 7) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s1 >> 15) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s1 >> 23) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 << 1) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    s0 = s1;
    *dp++ = acc0;
  }

  sp = (mlib_u8*)sa;
  sp -= 1;

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_u16*) dend - (mlib_u16*) dp;
    sp  += num;
    num ++;

    if (num == 1) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num  == 2) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num == 3) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table2, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc0, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_U16_1(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_u16       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u16 **table)
{
  mlib_u8  *sl;
  mlib_u16 *dl;
  const mlib_u16 *tab = table[0];
  mlib_s32 j, i;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j ++) {
    mlib_u8  *sp = sl;
    mlib_u16 *dp = dl;
    mlib_s32 off, size = xsize;

    off = ((8 - ((mlib_addr)dp & 7)) & 7) >> 1;

    off = (off < size) ? off : size;

    for (i = 0; i < off; i++) {
      *dp++ = tab[(*sp++)];
      size--;
    }

    if (size > 0) {

      off = (mlib_addr)sp & 3;

      if (off == 0) {
        mlib_v_ImageLookUp_U8_U16_124_SrcOff0_D1(sp, dp, size, tab, tab, tab, tab);
      } else if (off == 1) {
        mlib_v_ImageLookUp_U8_U16_124_SrcOff1_D1(sp, dp, size, tab, tab, tab, tab);
      } else if (off == 2) {
        mlib_v_ImageLookUp_U8_U16_124_SrcOff2_D1(sp, dp, size, tab, tab, tab, tab);
      } else {
        mlib_v_ImageLookUp_U8_U16_124_SrcOff3_D1(sp, dp, size, tab, tab, tab, tab);
      }
    }

    sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_u16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_U16_2(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_u16       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u16 **table)
{
  mlib_u8   *sl;
  mlib_u16  *dl;
  const mlib_u16  *tab;
  mlib_s32  j, i;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j ++) {
    mlib_u8   *sp = sl;
    mlib_u16  *dp = dl;
    mlib_s32  off, size = xsize * 2;
    const mlib_u16  *tab0 = table[0];
    const mlib_u16  *tab1 = table[1];

    off = ((8 - ((mlib_addr)dp & 7)) & 7) >> 1;

    off = (off < size) ? off : size;

    for (i = 0; i < off - 1; i+=2) {
      *dp++ = tab0[(*sp++)];
      *dp++ = tab1[(*sp++)];
      size-=2;
    }

    if ((off & 1) != 0) {
      *dp++ = tab0[(*sp++)];
      size--;
      tab = tab0; tab0 = tab1; tab1 = tab;
    }

    if (size > 0) {

      off = (mlib_addr)sp & 3;

      if (off == 0) {
        mlib_v_ImageLookUp_U8_U16_124_SrcOff0_D1(sp, dp, size, tab0, tab1, tab0, tab1);
      } else if (off == 1) {
        mlib_v_ImageLookUp_U8_U16_124_SrcOff1_D1(sp, dp, size, tab0, tab1, tab0, tab1);
      } else if (off == 2) {
        mlib_v_ImageLookUp_U8_U16_124_SrcOff2_D1(sp, dp, size, tab0, tab1, tab0, tab1);
      } else {
        mlib_v_ImageLookUp_U8_U16_124_SrcOff3_D1(sp, dp, size, tab0, tab1, tab0, tab1);
      }
    }

    sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_u16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_U16_4(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_u16       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u16 **table)
{
  mlib_u8   *sl;
  mlib_u16  *dl;
  const mlib_u16  *tab;
  mlib_s32  j;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j ++) {
    mlib_u8   *sp = sl;
    mlib_u16  *dp = dl;
    const mlib_u16  *tab0 = table[0];
    const mlib_u16  *tab1 = table[1];
    const mlib_u16  *tab2 = table[2];
    const mlib_u16  *tab3 = table[3];
    mlib_s32  off, size = xsize * 4;

    off = ((8 - ((mlib_addr)dp & 7)) & 7) >> 1;

    off = (off < size) ? off : size;

    if (off == 1) {
      *dp++ = tab0[(*sp++)];
      tab = tab0; tab0 = tab1;
      tab1 = tab2; tab2 = tab3; tab3 = tab;
      size--;
    } else if (off == 2) {
      *dp++ = tab0[(*sp++)];
      *dp++ = tab1[(*sp++)];
      tab = tab0; tab0 = tab2; tab2 = tab;
      tab = tab1; tab1 = tab3; tab3 = tab;
      size-=2;
    } else if (off == 3) {
      *dp++ = tab0[(*sp++)];
      *dp++ = tab1[(*sp++)];
      *dp++ = tab2[(*sp++)];
      tab = tab3; tab3 = tab2;
      tab2 = tab1; tab1 = tab0; tab0 = tab;
      size-=3;
    }

    if (size > 0) {

      off = (mlib_addr)sp & 3;

      if (off == 0) {
        mlib_v_ImageLookUp_U8_U16_124_SrcOff0_D1(sp, dp, size, tab0, tab1, tab2, tab3);
      } else if (off == 1) {
        mlib_v_ImageLookUp_U8_U16_124_SrcOff1_D1(sp, dp, size, tab0, tab1, tab2, tab3);
      } else if (off == 2) {
        mlib_v_ImageLookUp_U8_U16_124_SrcOff2_D1(sp, dp, size, tab0, tab1, tab2, tab3);
      } else {
        mlib_v_ImageLookUp_U8_U16_124_SrcOff3_D1(sp, dp, size, tab0, tab1, tab2, tab3);
      }
    }

    sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_u16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_U16_3_SrcOff0_D1(const mlib_u8  *src,
                                            mlib_u16       *dst,
                                            mlib_s32       xsize,
                                            const mlib_u16 *table0,
                                            const mlib_u16 *table1,
                                            const mlib_u16 *table2)
{
  mlib_u32 *sa;              /* aligned pointer to source data */
  mlib_u8  *sp;              /* pointer to source data */
  mlib_u32 s0, s1, s2;       /* source data */
  mlib_u16 *dl;              /* pointer to start of destination */
  mlib_u16 *dend;            /* pointer to end of destination */
  mlib_d64 *dp;              /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;       /* destination data */
  mlib_d64 t3, t4, t5;       /* destination data */
  mlib_d64 t6, t7, t8;       /* destination data */
  mlib_d64 t9, t10, t11;     /* destination data */
  mlib_d64 acc0, acc1, acc2; /* destination data */
  mlib_s32 emask;            /* edge mask */
  mlib_s32 i, num;           /* loop variable */
  const mlib_u16 *table;

  sa   = (mlib_u32*)src;
  dl   = dst;
  dp   = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *) 0, 6);

  i = 0;

  if (xsize >= 12) {

    s0 = sa[0];
    s1 = sa[1];
    s2 = sa[2];
    sa += 3;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 24; i+=12, sa += 3, dp += 3) {
      t3 = VIS_LD_U16_I(table0, (s0 << 1) & 0x1FE);
      t2 = VIS_LD_U16_I(table2, (s0 >> 7) & 0x1FE);
      t1 = VIS_LD_U16_I(table1, (s0 >> 15) & 0x1FE);
      t0 = VIS_LD_U16_I(table0, (s0 >> 23) & 0x1FE);
      t7 = VIS_LD_U16_I(table1, (s1 << 1) & 0x1FE);
      t6 = VIS_LD_U16_I(table0, (s1 >> 7) & 0x1FE);
      t5 = VIS_LD_U16_I(table2, (s1 >> 15) & 0x1FE);
      t4 = VIS_LD_U16_I(table1, (s1 >> 23) & 0x1FE);
      t11 = VIS_LD_U16_I(table2, (s2 << 1) & 0x1FE);
      t10 = VIS_LD_U16_I(table1, (s2 >> 7) & 0x1FE);
      t9 = VIS_LD_U16_I(table0, (s2 >> 15) & 0x1FE);
      t8 = VIS_LD_U16_I(table2, (s2 >> 23) & 0x1FE);
      acc0 = vis_faligndata(t3, acc0);
      acc0 = vis_faligndata(t2, acc0);
      acc0 = vis_faligndata(t1, acc0);
      acc0 = vis_faligndata(t0, acc0);
      acc1 = vis_faligndata(t7, acc1);
      acc1 = vis_faligndata(t6, acc1);
      acc1 = vis_faligndata(t5, acc1);
      acc1 = vis_faligndata(t4, acc1);
      acc2 = vis_faligndata(t11, acc2);
      acc2 = vis_faligndata(t10, acc2);
      acc2 = vis_faligndata(t9, acc2);
      acc2 = vis_faligndata(t8, acc2);
      s0 = sa[0];
      s1 = sa[1];
      s2 = sa[2];
      dp[0] = acc0;
      dp[1] = acc1;
      dp[2] = acc2;
    }

    t3 = VIS_LD_U16_I(table0, (s0 << 1) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s0 >> 7) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s0 >> 15) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 >> 23) & 0x1FE);
    t7 = VIS_LD_U16_I(table1, (s1 << 1) & 0x1FE);
    t6 = VIS_LD_U16_I(table0, (s1 >> 7) & 0x1FE);
    t5 = VIS_LD_U16_I(table2, (s1 >> 15) & 0x1FE);
    t4 = VIS_LD_U16_I(table1, (s1 >> 23) & 0x1FE);
    t11 = VIS_LD_U16_I(table2, (s2 << 1) & 0x1FE);
    t10 = VIS_LD_U16_I(table1, (s2 >> 7) & 0x1FE);
    t9 = VIS_LD_U16_I(table0, (s2 >> 15) & 0x1FE);
    t8 = VIS_LD_U16_I(table2, (s2 >> 23) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    acc1 = vis_faligndata(t7, acc1);
    acc1 = vis_faligndata(t6, acc1);
    acc1 = vis_faligndata(t5, acc1);
    acc1 = vis_faligndata(t4, acc1);
    acc2 = vis_faligndata(t11, acc2);
    acc2 = vis_faligndata(t10, acc2);
    acc2 = vis_faligndata(t9, acc2);
    acc2 = vis_faligndata(t8, acc2);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    dp += 3; i += 12;
  }

  if (i <= xsize - 8) {
    s0 = sa[0];
    s1 = sa[1];
    t3 = VIS_LD_U16_I(table0, (s0 << 1) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s0 >> 7) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s0 >> 15) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 >> 23) & 0x1FE);
    t7 = VIS_LD_U16_I(table1, (s1 << 1) & 0x1FE);
    t6 = VIS_LD_U16_I(table0, (s1 >> 7) & 0x1FE);
    t5 = VIS_LD_U16_I(table2, (s1 >> 15) & 0x1FE);
    t4 = VIS_LD_U16_I(table1, (s1 >> 23) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    acc1 = vis_faligndata(t7, acc1);
    acc1 = vis_faligndata(t6, acc1);
    acc1 = vis_faligndata(t5, acc1);
    acc1 = vis_faligndata(t4, acc1);
    dp[0] = acc0;
    dp[1] = acc1;
    table = table0; table0 = table2;
    table2 = table1; table1 = table;
    sa += 2; i += 8; dp += 2;
  }

  if (i <= xsize - 4) {
    s0 = sa[0];
    t3 = VIS_LD_U16_I(table0, (s0 << 1) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s0 >> 7) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s0 >> 15) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 >> 23) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    dp[0] = acc0;
    table = table0; table0 = table1;
    table1 = table2; table2 = table;
    sa++; i += 4; dp++;
  }

  sp = (mlib_u8*)sa;

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_u16*) dend - (mlib_u16*) dp;
    sp  += num;
    num ++;

    if (num == 1) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num  == 2) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num == 3) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table2, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc0, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_U16_3_SrcOff1_D1(const mlib_u8  *src,
                                            mlib_u16       *dst,
                                            mlib_s32       xsize,
                                            const mlib_u16 *table0,
                                            const mlib_u16 *table1,
                                            const mlib_u16 *table2)
{
  mlib_u32 *sa;              /* aligned pointer to source data */
  mlib_u8  *sp;              /* pointer to source data */
  mlib_u32 s0, s1, s2, s3;   /* source data */
  mlib_u16 *dl;              /* pointer to start of destination */
  mlib_u16 *dend;            /* pointer to end of destination */
  mlib_d64 *dp;              /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;       /* destination data */
  mlib_d64 t3, t4, t5;       /* destination data */
  mlib_d64 t6, t7, t8;       /* destination data */
  mlib_d64 t9, t10, t11;     /* destination data */
  mlib_d64 acc0, acc1, acc2; /* destination data */
  mlib_s32 emask;            /* edge mask */
  mlib_s32 i, num;           /* loop variable */
  const mlib_u16 *table;

  sa   = (mlib_u32*)(src - 1);
  dl   = dst;
  dp   = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *) 0, 6);

  i = 0;

  s0 = *sa++;

  if (xsize >= 12) {

    s1 = sa[0];
    s2 = sa[1];
    s3 = sa[2];
    sa += 3;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 24; i+=12, sa += 3, dp += 3) {
      t3 = VIS_LD_U16_I(table0, (s1 >> 23) & 0x1FE);
      t2 = VIS_LD_U16_I(table2, (s0 << 1) & 0x1FE);
      t1 = VIS_LD_U16_I(table1, (s0 >> 7) & 0x1FE);
      t0 = VIS_LD_U16_I(table0, (s0 >> 15) & 0x1FE);
      t7 = VIS_LD_U16_I(table1, (s2 >> 23) & 0x1FE);
      t6 = VIS_LD_U16_I(table0, (s1 << 1) & 0x1FE);
      t5 = VIS_LD_U16_I(table2, (s1 >> 7) & 0x1FE);
      t4 = VIS_LD_U16_I(table1, (s1 >> 15) & 0x1FE);
      t11 = VIS_LD_U16_I(table2, (s3 >> 23) & 0x1FE);
      t10 = VIS_LD_U16_I(table1, (s2 << 1) & 0x1FE);
      t9 = VIS_LD_U16_I(table0, (s2 >> 7) & 0x1FE);
      t8 = VIS_LD_U16_I(table2, (s2 >> 15) & 0x1FE);
      acc0 = vis_faligndata(t3, acc0);
      acc0 = vis_faligndata(t2, acc0);
      acc0 = vis_faligndata(t1, acc0);
      acc0 = vis_faligndata(t0, acc0);
      acc1 = vis_faligndata(t7, acc1);
      acc1 = vis_faligndata(t6, acc1);
      acc1 = vis_faligndata(t5, acc1);
      acc1 = vis_faligndata(t4, acc1);
      acc2 = vis_faligndata(t11, acc2);
      acc2 = vis_faligndata(t10, acc2);
      acc2 = vis_faligndata(t9, acc2);
      acc2 = vis_faligndata(t8, acc2);
      s0 = s3;
      s1 = sa[0];
      s2 = sa[1];
      s3 = sa[2];
      dp[0] = acc0;
      dp[1] = acc1;
      dp[2] = acc2;
    }

    t3 = VIS_LD_U16_I(table0, (s1 >> 23) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s0 << 1) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s0 >> 7) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 >> 15) & 0x1FE);
    t7 = VIS_LD_U16_I(table1, (s2 >> 23) & 0x1FE);
    t6 = VIS_LD_U16_I(table0, (s1 << 1) & 0x1FE);
    t5 = VIS_LD_U16_I(table2, (s1 >> 7) & 0x1FE);
    t4 = VIS_LD_U16_I(table1, (s1 >> 15) & 0x1FE);
    t11 = VIS_LD_U16_I(table2, (s3 >> 23) & 0x1FE);
    t10 = VIS_LD_U16_I(table1, (s2 << 1) & 0x1FE);
    t9 = VIS_LD_U16_I(table0, (s2 >> 7) & 0x1FE);
    t8 = VIS_LD_U16_I(table2, (s2 >> 15) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    acc1 = vis_faligndata(t7, acc1);
    acc1 = vis_faligndata(t6, acc1);
    acc1 = vis_faligndata(t5, acc1);
    acc1 = vis_faligndata(t4, acc1);
    acc2 = vis_faligndata(t11, acc2);
    acc2 = vis_faligndata(t10, acc2);
    acc2 = vis_faligndata(t9, acc2);
    acc2 = vis_faligndata(t8, acc2);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    s0 = s3;
    dp += 3; i += 12;
  }

  if (i <= xsize - 8) {
    s1 = sa[0];
    s2 = sa[1];
    t3 = VIS_LD_U16_I(table0, (s1 >> 23) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s0 << 1) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s0 >> 7) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 >> 15) & 0x1FE);
    t7 = VIS_LD_U16_I(table1, (s2 >> 23) & 0x1FE);
    t6 = VIS_LD_U16_I(table0, (s1 << 1) & 0x1FE);
    t5 = VIS_LD_U16_I(table2, (s1 >> 7) & 0x1FE);
    t4 = VIS_LD_U16_I(table1, (s1 >> 15) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    acc1 = vis_faligndata(t7, acc1);
    acc1 = vis_faligndata(t6, acc1);
    acc1 = vis_faligndata(t5, acc1);
    acc1 = vis_faligndata(t4, acc1);
    dp[0] = acc0;
    dp[1] = acc1;
    table = table0; table0 = table2;
    table2 = table1; table1 = table;
    sa += 2; i += 8; dp += 2;
    s0 = s2;
  }

  if (i <= xsize - 4) {
    s1 = sa[0];
    t3 = VIS_LD_U16_I(table0, (s1 >> 23) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s0 << 1) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s0 >> 7) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 >> 15) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    dp[0] = acc0;
    table = table0; table0 = table1;
    table1 = table2; table2 = table;
    sa++; i += 4; dp++;
    s0 = s1;
  }

  sp = (mlib_u8*)sa;
  sp -= 3;

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_u16*) dend - (mlib_u16*) dp;
    sp  += num;
    num ++;

    if (num == 1) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num  == 2) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num == 3) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table2, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc0, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_U16_3_SrcOff2_D1(const mlib_u8  *src,
                                            mlib_u16       *dst,
                                            mlib_s32       xsize,
                                            const mlib_u16 *table0,
                                            const mlib_u16 *table1,
                                            const mlib_u16 *table2)
{
  mlib_u32 *sa;              /* aligned pointer to source data */
  mlib_u8  *sp;              /* pointer to source data */
  mlib_u32 s0, s1, s2, s3;   /* source data */
  mlib_u16 *dl;              /* pointer to start of destination */
  mlib_u16 *dend;            /* pointer to end of destination */
  mlib_d64 *dp;              /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;       /* destination data */
  mlib_d64 t3, t4, t5;       /* destination data */
  mlib_d64 t6, t7, t8;       /* destination data */
  mlib_d64 t9, t10, t11;     /* destination data */
  mlib_d64 acc0, acc1, acc2; /* destination data */
  mlib_s32 emask;            /* edge mask */
  mlib_s32 i, num;           /* loop variable */
  const mlib_u16 *table;

  sa   = (mlib_u32*)(src - 2);
  dl   = dst;
  dp   = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *) 0, 6);

  i = 0;

  s0 = *sa++;

  if (xsize >= 12) {

    s1 = sa[0];
    s2 = sa[1];
    s3 = sa[2];
    sa += 3;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 24; i+=12, sa += 3, dp += 3) {
      t3 = VIS_LD_U16_I(table0, (s1 >> 15) & 0x1FE);
      t2 = VIS_LD_U16_I(table2, (s1 >> 23) & 0x1FE);
      t1 = VIS_LD_U16_I(table1, (s0 << 1) & 0x1FE);
      t0 = VIS_LD_U16_I(table0, (s0 >> 7) & 0x1FE);
      t7 = VIS_LD_U16_I(table1, (s2 >> 15) & 0x1FE);
      t6 = VIS_LD_U16_I(table0, (s2 >> 23) & 0x1FE);
      t5 = VIS_LD_U16_I(table2, (s1 << 1) & 0x1FE);
      t4 = VIS_LD_U16_I(table1, (s1 >> 7) & 0x1FE);
      t11 = VIS_LD_U16_I(table2, (s3 >> 15) & 0x1FE);
      t10 = VIS_LD_U16_I(table1, (s3 >> 23) & 0x1FE);
      t9 = VIS_LD_U16_I(table0, (s2 << 1) & 0x1FE);
      t8 = VIS_LD_U16_I(table2, (s2 >> 7) & 0x1FE);
      acc0 = vis_faligndata(t3, acc0);
      acc0 = vis_faligndata(t2, acc0);
      acc0 = vis_faligndata(t1, acc0);
      acc0 = vis_faligndata(t0, acc0);
      acc1 = vis_faligndata(t7, acc1);
      acc1 = vis_faligndata(t6, acc1);
      acc1 = vis_faligndata(t5, acc1);
      acc1 = vis_faligndata(t4, acc1);
      acc2 = vis_faligndata(t11, acc2);
      acc2 = vis_faligndata(t10, acc2);
      acc2 = vis_faligndata(t9, acc2);
      acc2 = vis_faligndata(t8, acc2);
      s0 = s3;
      s1 = sa[0];
      s2 = sa[1];
      s3 = sa[2];
      dp[0] = acc0;
      dp[1] = acc1;
      dp[2] = acc2;
    }

    t3 = VIS_LD_U16_I(table0, (s1 >> 15) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s1 >> 23) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s0 << 1) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 >> 7) & 0x1FE);
    t7 = VIS_LD_U16_I(table1, (s2 >> 15) & 0x1FE);
    t6 = VIS_LD_U16_I(table0, (s2 >> 23) & 0x1FE);
    t5 = VIS_LD_U16_I(table2, (s1 << 1) & 0x1FE);
    t4 = VIS_LD_U16_I(table1, (s1 >> 7) & 0x1FE);
    t11 = VIS_LD_U16_I(table2, (s3 >> 15) & 0x1FE);
    t10 = VIS_LD_U16_I(table1, (s3 >> 23) & 0x1FE);
    t9 = VIS_LD_U16_I(table0, (s2 << 1) & 0x1FE);
    t8 = VIS_LD_U16_I(table2, (s2 >> 7) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    acc1 = vis_faligndata(t7, acc1);
    acc1 = vis_faligndata(t6, acc1);
    acc1 = vis_faligndata(t5, acc1);
    acc1 = vis_faligndata(t4, acc1);
    acc2 = vis_faligndata(t11, acc2);
    acc2 = vis_faligndata(t10, acc2);
    acc2 = vis_faligndata(t9, acc2);
    acc2 = vis_faligndata(t8, acc2);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    s0 = s3;
    dp += 3; i += 12;
  }

  if (i <= xsize - 8) {
    s1 = sa[0];
    s2 = sa[1];
    t3 = VIS_LD_U16_I(table0, (s1 >> 15) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s1 >> 23) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s0 << 1) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 >> 7) & 0x1FE);
    t7 = VIS_LD_U16_I(table1, (s2 >> 15) & 0x1FE);
    t6 = VIS_LD_U16_I(table0, (s2 >> 23) & 0x1FE);
    t5 = VIS_LD_U16_I(table2, (s1 << 1) & 0x1FE);
    t4 = VIS_LD_U16_I(table1, (s1 >> 7) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    acc1 = vis_faligndata(t7, acc1);
    acc1 = vis_faligndata(t6, acc1);
    acc1 = vis_faligndata(t5, acc1);
    acc1 = vis_faligndata(t4, acc1);
    dp[0] = acc0;
    dp[1] = acc1;
    table = table0; table0 = table2;
    table2 = table1; table1 = table;
    sa += 2; i += 8; dp += 2;
    s0 = s2;
  }

  if (i <= xsize - 4) {
    s1 = sa[0];
    t3 = VIS_LD_U16_I(table0, (s1 >> 15) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s1 >> 23) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s0 << 1) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 >> 7) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    dp[0] = acc0;
    table = table0; table0 = table1;
    table1 = table2; table2 = table;
    sa++; i += 4; dp++;
    s0 = s1;
  }

  sp = (mlib_u8*)sa;
  sp -= 2;

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_u16*) dend - (mlib_u16*) dp;
    sp  += num;
    num ++;

    if (num == 1) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num  == 2) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num == 3) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table2, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc0, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_U16_3_SrcOff3_D1(const mlib_u8  *src,
                                            mlib_u16       *dst,
                                            mlib_s32       xsize,
                                            const mlib_u16 *table0,
                                            const mlib_u16 *table1,
                                            const mlib_u16 *table2)
{
  mlib_u32 *sa;              /* aligned pointer to source data */
  mlib_u8  *sp;              /* pointer to source data */
  mlib_u32 s0, s1, s2, s3;   /* source data */
  mlib_u16 *dl;              /* pointer to start of destination */
  mlib_u16 *dend;            /* pointer to end of destination */
  mlib_d64 *dp;              /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;       /* destination data */
  mlib_d64 t3, t4, t5;       /* destination data */
  mlib_d64 t6, t7, t8;       /* destination data */
  mlib_d64 t9, t10, t11;     /* destination data */
  mlib_d64 acc0, acc1, acc2; /* destination data */
  mlib_s32 emask;            /* edge mask */
  mlib_s32 i, num;           /* loop variable */
  const mlib_u16 *table;

  sa   = (mlib_u32*)(src - 3);
  dl   = dst;
  dp   = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *) 0, 6);

  i = 0;

  s0 = *sa++;

  if (xsize >= 12) {

    s1 = sa[0];
    s2 = sa[1];
    s3 = sa[2];
    sa += 3;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 24; i+=12, sa += 3, dp += 3) {
      t3 = VIS_LD_U16_I(table0, (s1 >> 7) & 0x1FE);
      t2 = VIS_LD_U16_I(table2, (s1 >> 15) & 0x1FE);
      t1 = VIS_LD_U16_I(table1, (s1 >> 23) & 0x1FE);
      t0 = VIS_LD_U16_I(table0, (s0 << 1) & 0x1FE);
      t7 = VIS_LD_U16_I(table1, (s2 >> 7) & 0x1FE);
      t6 = VIS_LD_U16_I(table0, (s2 >> 15) & 0x1FE);
      t5 = VIS_LD_U16_I(table2, (s2 >> 23) & 0x1FE);
      t4 = VIS_LD_U16_I(table1, (s1 << 1) & 0x1FE);
      t11 = VIS_LD_U16_I(table2, (s3 >> 7) & 0x1FE);
      t10 = VIS_LD_U16_I(table1, (s3 >> 15) & 0x1FE);
      t9 = VIS_LD_U16_I(table0, (s3 >> 23) & 0x1FE);
      t8 = VIS_LD_U16_I(table2, (s2 << 1) & 0x1FE);
      acc0 = vis_faligndata(t3, acc0);
      acc0 = vis_faligndata(t2, acc0);
      acc0 = vis_faligndata(t1, acc0);
      acc0 = vis_faligndata(t0, acc0);
      acc1 = vis_faligndata(t7, acc1);
      acc1 = vis_faligndata(t6, acc1);
      acc1 = vis_faligndata(t5, acc1);
      acc1 = vis_faligndata(t4, acc1);
      acc2 = vis_faligndata(t11, acc2);
      acc2 = vis_faligndata(t10, acc2);
      acc2 = vis_faligndata(t9, acc2);
      acc2 = vis_faligndata(t8, acc2);
      s0 = s3;
      s1 = sa[0];
      s2 = sa[1];
      s3 = sa[2];
      dp[0] = acc0;
      dp[1] = acc1;
      dp[2] = acc2;
    }

    t3 = VIS_LD_U16_I(table0, (s1 >> 7) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s1 >> 15) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s1 >> 23) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 << 1) & 0x1FE);
    t7 = VIS_LD_U16_I(table1, (s2 >> 7) & 0x1FE);
    t6 = VIS_LD_U16_I(table0, (s2 >> 15) & 0x1FE);
    t5 = VIS_LD_U16_I(table2, (s2 >> 23) & 0x1FE);
    t4 = VIS_LD_U16_I(table1, (s1 << 1) & 0x1FE);
    t11 = VIS_LD_U16_I(table2, (s3 >> 7) & 0x1FE);
    t10 = VIS_LD_U16_I(table1, (s3 >> 15) & 0x1FE);
    t9 = VIS_LD_U16_I(table0, (s3 >> 23) & 0x1FE);
    t8 = VIS_LD_U16_I(table2, (s2 << 1) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    acc1 = vis_faligndata(t7, acc1);
    acc1 = vis_faligndata(t6, acc1);
    acc1 = vis_faligndata(t5, acc1);
    acc1 = vis_faligndata(t4, acc1);
    acc2 = vis_faligndata(t11, acc2);
    acc2 = vis_faligndata(t10, acc2);
    acc2 = vis_faligndata(t9, acc2);
    acc2 = vis_faligndata(t8, acc2);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    s0 = s3;
    dp += 3; i += 12;
  }

  if (i <= xsize - 8) {
    s1 = sa[0];
    s2 = sa[1];
    t3 = VIS_LD_U16_I(table0, (s1 >> 7) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s1 >> 15) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s1 >> 23) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 << 1) & 0x1FE);
    t7 = VIS_LD_U16_I(table1, (s2 >> 7) & 0x1FE);
    t6 = VIS_LD_U16_I(table0, (s2 >> 15) & 0x1FE);
    t5 = VIS_LD_U16_I(table2, (s2 >> 23) & 0x1FE);
    t4 = VIS_LD_U16_I(table1, (s1 << 1) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    acc1 = vis_faligndata(t7, acc1);
    acc1 = vis_faligndata(t6, acc1);
    acc1 = vis_faligndata(t5, acc1);
    acc1 = vis_faligndata(t4, acc1);
    dp[0] = acc0;
    dp[1] = acc1;
    table = table0; table0 = table2;
    table2 = table1; table1 = table;
    sa += 2; i += 8; dp += 2;
    s0 = s2;
  }

  if (i <= xsize - 4) {
    s1 = sa[0];
    t3 = VIS_LD_U16_I(table0, (s1 >> 7) & 0x1FE);
    t2 = VIS_LD_U16_I(table2, (s1 >> 15) & 0x1FE);
    t1 = VIS_LD_U16_I(table1, (s1 >> 23) & 0x1FE);
    t0 = VIS_LD_U16_I(table0, (s0 << 1) & 0x1FE);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    dp[0] = acc0;
    table = table0; table0 = table1;
    table1 = table2; table2 = table;
    sa++; i += 4; dp++;
    s0 = s1;
  }

  sp = (mlib_u8*)sa;
  sp -= 1;

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_u16*) dend - (mlib_u16*) dp;
    sp  += num;
    num ++;

    if (num == 1) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num  == 2) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    } else if (num == 3) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table2, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table1, 2*s0);
      acc0 = vis_faligndata(t0, acc0);

      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table0, 2*s0);
      acc0 = vis_faligndata(t0, acc0);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc0, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_U16_3(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_u16       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u16 **table)
{
  mlib_u8  *sl;
  mlib_u16 *dl;
  const mlib_u16 *tab;
  mlib_s32 j, i;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j ++) {
    mlib_u8   *sp = sl;
    mlib_u16  *dp = dl;
    const mlib_u16  *tab0 = table[0];
    const mlib_u16  *tab1 = table[1];
    const mlib_u16  *tab2 = table[2];
    mlib_s32  off, size = xsize * 3;

    off = ((8 - ((mlib_addr)dp & 7)) & 7) >> 1;

    off = (off < size) ? off : size;

    for (i = 0; i < off - 2; i += 3) {
      *dp++ = tab0[(*sp++)];
      *dp++ = tab1[(*sp++)];
      *dp++ = tab2[(*sp++)];
      size-=3;
    }

    off -= i;

    if (off == 1) {
      *dp++ = tab0[(*sp++)];
      tab = tab0; tab0 = tab1;
      tab1 = tab2; tab2 = tab;
      size--;
    } else if (off == 2) {
      *dp++ = tab0[(*sp++)];
      *dp++ = tab1[(*sp++)];
      tab = tab2; tab2 = tab1;
      tab1 = tab0; tab0 = tab;
      size-=2;
    }

    if (size > 0) {

      off = (mlib_addr)sp & 3;

      if (off == 0) {
        mlib_v_ImageLookUp_U8_U16_3_SrcOff0_D1(sp, dp, size, tab0, tab1, tab2);
      } else if (off == 1) {
        mlib_v_ImageLookUp_U8_U16_3_SrcOff1_D1(sp, dp, size, tab0, tab1, tab2);
      } else if (off == 2) {
        mlib_v_ImageLookUp_U8_U16_3_SrcOff2_D1(sp, dp, size, tab0, tab1, tab2);
      } else {
        mlib_v_ImageLookUp_U8_U16_3_SrcOff3_D1(sp, dp, size, tab0, tab1, tab2);
      }
    }

    sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_u16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
