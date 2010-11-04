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
static void mlib_v_ImageLookUp_S32_S16_124_D1(const mlib_s32 * src,
                                              mlib_s16 * dst,
                                              mlib_s32 xsize,
                                              const mlib_s16 * table0,
                                              const mlib_s16 * table1,
                                              const mlib_s16 * table2,
                                              const mlib_s16 * table3);

static void mlib_v_ImageLookUp_S32_S16_3_D1(const mlib_s32 * src,
                                            mlib_s16 * dst,
                                            mlib_s32 xsize,
                                            const mlib_s16 * table0,
                                            const mlib_s16 * table1,
                                            const mlib_s16 * table2);

/***************************************************************/

#define VIS_LD_U16_I(X, Y)      vis_ld_u16_i((void *)(X), (Y))

/***************************************************************/
void mlib_v_ImageLookUp_S32_S16_124_D1(const mlib_s32 * src,
                                       mlib_s16 * dst,
                                       mlib_s32 xsize,
                                       const mlib_s16 * table0,
                                       const mlib_s16 * table1,
                                       const mlib_s16 * table2,
                                       const mlib_s16 * table3)
{
  mlib_s32 *sp;                        /* pointer to source data */
  mlib_s32 s0, s1, s2, s3;             /* source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_s16 *dend;                      /* pointer to end of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;                 /* destination data */
  mlib_d64 t3, acc0;                   /* destination data */
  mlib_s32 emask;                      /* edge mask */
  mlib_s32 i, num;                     /* loop variable */

  dl = dst;
  sp = (void *)src;
  dp = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *)0, 6);

  if (xsize >= 4) {

    s0 = sp[0];
    s1 = sp[1];
    s2 = sp[2];
    s3 = sp[3];
    sp += 4;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 8; i += 4, sp += 4) {
      t3 = VIS_LD_U16_I(table3, ((mlib_addr) 2 * s3));
      t2 = VIS_LD_U16_I(table2, ((mlib_addr) 2 * s2));
      t1 = VIS_LD_U16_I(table1, ((mlib_addr) 2 * s1));
      t0 = VIS_LD_U16_I(table0, ((mlib_addr) 2 * s0));
      acc0 = vis_faligndata(t3, acc0);
      acc0 = vis_faligndata(t2, acc0);
      acc0 = vis_faligndata(t1, acc0);
      acc0 = vis_faligndata(t0, acc0);
      s0 = sp[0];
      s1 = sp[1];
      s2 = sp[2];
      s3 = sp[3];
      *dp++ = acc0;
    }

    t3 = VIS_LD_U16_I(table3, ((mlib_addr) 2 * s3));
    t2 = VIS_LD_U16_I(table2, ((mlib_addr) 2 * s2));
    t1 = VIS_LD_U16_I(table1, ((mlib_addr) 2 * s1));
    t0 = VIS_LD_U16_I(table0, ((mlib_addr) 2 * s0));
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    *dp++ = acc0;
  }

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_s32) ((mlib_s16 *) dend - (mlib_s16 *) dp);
    sp += num;
    num++;

    if (num == 1) {
      s0 = *sp;

      t0 = VIS_LD_U16_I(table0, ((mlib_addr) 2 * s0));
      acc0 = vis_faligndata(t0, acc0);
    }
    else if (num == 2) {
      s0 = *sp;
      sp--;

      t0 = VIS_LD_U16_I(table1, ((mlib_addr) 2 * s0));
      acc0 = vis_faligndata(t0, acc0);

      s0 = *sp;

      t0 = VIS_LD_U16_I(table0, ((mlib_addr) 2 * s0));
      acc0 = vis_faligndata(t0, acc0);
    }
    else if (num == 3) {
      s0 = *sp;
      sp--;

      t0 = VIS_LD_U16_I(table2, ((mlib_addr) 2 * s0));
      acc0 = vis_faligndata(t0, acc0);

      s0 = *sp;
      sp--;

      t0 = VIS_LD_U16_I(table1, ((mlib_addr) 2 * s0));
      acc0 = vis_faligndata(t0, acc0);

      s0 = *sp;

      t0 = VIS_LD_U16_I(table0, ((mlib_addr) 2 * s0));
      acc0 = vis_faligndata(t0, acc0);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc0, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_S32_S16_1(const mlib_s32 * src,
                                  mlib_s32 slb,
                                  mlib_s16 * dst,
                                  mlib_s32 dlb,
                                  mlib_s32 xsize,
                                  mlib_s32 ysize, const mlib_s16 ** table)
{
  mlib_s32 *sl;
  mlib_s16 *dl;
  mlib_u32 shift = 2147483648u;
  const mlib_s16 *tab = &table[0][shift];
  mlib_s32 j, i;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    mlib_s32 *sp = sl;
    mlib_s16 *dp = dl;
    mlib_s32 off, size = xsize;

    off = (mlib_s32) (((8 - ((mlib_addr) dp & 7)) & 7) >> 1);

    off = (off < size) ? off : size;

    for (i = 0; i < off; i++, sp++) {
      *dp++ = tab[sp[0]];
      size--;
    }

    if (size > 0) {
      mlib_v_ImageLookUp_S32_S16_124_D1(sp, dp, size, tab, tab, tab, tab);
    }

    sl = (mlib_s32 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_S32_S16_2(const mlib_s32 * src,
                                  mlib_s32 slb,
                                  mlib_s16 * dst,
                                  mlib_s32 dlb,
                                  mlib_s32 xsize,
                                  mlib_s32 ysize, const mlib_s16 ** table)
{
  mlib_s32 *sl;
  mlib_s16 *dl;
  mlib_u32 shift = 2147483648u;
  const mlib_s16 *tab;
  mlib_s32 j, i;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    mlib_s32 *sp = sl;
    mlib_s16 *dp = dl;
    mlib_s32 off, size = xsize * 2;
    const mlib_s16 *tab0 = &table[0][shift];
    const mlib_s16 *tab1 = &table[1][shift];

    off = (mlib_s32) (((8 - ((mlib_addr) dp & 7)) & 7) >> 1);

    off = (off < size) ? off : size;

    for (i = 0; i < off - 1; i += 2, sp += 2) {
      *dp++ = tab0[sp[0]];
      *dp++ = tab1[sp[1]];
      size -= 2;
    }

    if ((off & 1) != 0) {
      *dp++ = tab0[sp[0]];
      size--;
      sp++;
      tab = tab0;
      tab0 = tab1;
      tab1 = tab;
    }

    if (size > 0) {
      mlib_v_ImageLookUp_S32_S16_124_D1(sp, dp, size, tab0, tab1, tab0, tab1);
    }

    sl = (mlib_s32 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_S32_S16_4(const mlib_s32 * src,
                                  mlib_s32 slb,
                                  mlib_s16 * dst,
                                  mlib_s32 dlb,
                                  mlib_s32 xsize,
                                  mlib_s32 ysize, const mlib_s16 ** table)
{
  mlib_s32 *sl;
  mlib_s16 *dl;
  mlib_u32 shift = 2147483648u;
  const mlib_s16 *tab;
  mlib_s32 j;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    mlib_s32 *sp = sl;
    mlib_s16 *dp = dl;
    const mlib_s16 *tab0 = &table[0][shift];
    const mlib_s16 *tab1 = &table[1][shift];
    const mlib_s16 *tab2 = &table[2][shift];
    const mlib_s16 *tab3 = &table[3][shift];
    mlib_s32 off, size = xsize * 4;

    off = (mlib_s32) (((8 - ((mlib_addr) dp & 7)) & 7) >> 1);

    off = (off < size) ? off : size;

    if (off == 1) {
      *dp++ = tab0[sp[0]];
      tab = tab0;
      tab0 = tab1;
      tab1 = tab2;
      tab2 = tab3;
      tab3 = tab;
      size--;
      sp++;
    }
    else if (off == 2) {
      *dp++ = tab0[sp[0]];
      *dp++ = tab1[sp[1]];
      tab = tab0;
      tab0 = tab2;
      tab2 = tab;
      tab = tab1;
      tab1 = tab3;
      tab3 = tab;
      size -= 2;
      sp += 2;
    }
    else if (off == 3) {
      *dp++ = tab0[sp[0]];
      *dp++ = tab1[sp[1]];
      *dp++ = tab2[sp[2]];
      tab = tab3;
      tab3 = tab2;
      tab2 = tab1;
      tab1 = tab0;
      tab0 = tab;
      size -= 3;
      sp += 3;
    }

    if (size > 0) {
      mlib_v_ImageLookUp_S32_S16_124_D1(sp, dp, size, tab0, tab1, tab2, tab3);
    }

    sl = (mlib_s32 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_S32_S16_3_D1(const mlib_s32 * src,
                                     mlib_s16 * dst,
                                     mlib_s32 xsize,
                                     const mlib_s16 * table0,
                                     const mlib_s16 * table1,
                                     const mlib_s16 * table2)
{
  mlib_s32 *sp;                        /* pointer to source data */
  mlib_s32 s0, s1, s2, s3;             /* source data */
  mlib_s16 *dl;                        /* pointer to start of destination */
  mlib_s16 *dend;                      /* pointer to end of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3;             /* destination data */
  mlib_d64 acc0;                       /* destination data */
  mlib_s32 emask;                      /* edge mask */
  mlib_s32 i, num;                     /* loop variable */
  const mlib_s16 *table;

  dl = dst;
  sp = (void *)src;
  dp = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *)0, 6);

  if (xsize >= 4) {

    s0 = sp[0];
    s1 = sp[1];
    s2 = sp[2];
    s3 = sp[3];
    sp += 4;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 8; i += 4, sp += 4) {
      t3 = VIS_LD_U16_I(table0, ((mlib_addr) 2 * s3));
      t2 = VIS_LD_U16_I(table2, ((mlib_addr) 2 * s2));
      t1 = VIS_LD_U16_I(table1, ((mlib_addr) 2 * s1));
      t0 = VIS_LD_U16_I(table0, ((mlib_addr) 2 * s0));
      acc0 = vis_faligndata(t3, acc0);
      acc0 = vis_faligndata(t2, acc0);
      acc0 = vis_faligndata(t1, acc0);
      acc0 = vis_faligndata(t0, acc0);
      s0 = sp[0];
      s1 = sp[1];
      s2 = sp[2];
      s3 = sp[3];
      *dp++ = acc0;
      table = table0;
      table0 = table1;
      table1 = table2;
      table2 = table;
    }

    t3 = VIS_LD_U16_I(table0, ((mlib_addr) 2 * s3));
    t2 = VIS_LD_U16_I(table2, ((mlib_addr) 2 * s2));
    t1 = VIS_LD_U16_I(table1, ((mlib_addr) 2 * s1));
    t0 = VIS_LD_U16_I(table0, ((mlib_addr) 2 * s0));
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    *dp++ = acc0;
    table = table0;
    table0 = table1;
    table1 = table2;
    table2 = table;
  }

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_s32) ((mlib_s16 *) dend - (mlib_s16 *) dp);
    sp += num;
    num++;

    if (num == 1) {
      s0 = *sp;

      t0 = VIS_LD_U16_I(table0, ((mlib_addr) 2 * s0));
      acc0 = vis_faligndata(t0, acc0);
    }
    else if (num == 2) {
      s0 = *sp;
      sp--;

      t0 = VIS_LD_U16_I(table1, ((mlib_addr) 2 * s0));
      acc0 = vis_faligndata(t0, acc0);

      s0 = *sp;

      t0 = VIS_LD_U16_I(table0, ((mlib_addr) 2 * s0));
      acc0 = vis_faligndata(t0, acc0);
    }
    else if (num == 3) {
      s0 = *sp;
      sp--;

      t0 = VIS_LD_U16_I(table2, ((mlib_addr) 2 * s0));
      acc0 = vis_faligndata(t0, acc0);

      s0 = *sp;
      sp--;

      t0 = VIS_LD_U16_I(table1, ((mlib_addr) 2 * s0));
      acc0 = vis_faligndata(t0, acc0);

      s0 = *sp;

      t0 = VIS_LD_U16_I(table0, ((mlib_addr) 2 * s0));
      acc0 = vis_faligndata(t0, acc0);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc0, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_S32_S16_3(const mlib_s32 * src,
                                  mlib_s32 slb,
                                  mlib_s16 * dst,
                                  mlib_s32 dlb,
                                  mlib_s32 xsize,
                                  mlib_s32 ysize, const mlib_s16 ** table)
{
  mlib_s32 *sl;
  mlib_s16 *dl;
  mlib_u32 shift = 2147483648u;
  const mlib_s16 *tab;
  mlib_s32 j, i;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    mlib_s32 *sp = sl;
    mlib_s16 *dp = dl;
    const mlib_s16 *tab0 = &table[0][shift];
    const mlib_s16 *tab1 = &table[1][shift];
    const mlib_s16 *tab2 = &table[2][shift];
    mlib_s32 off, size = xsize * 3;

    off = (mlib_s32) (((8 - ((mlib_addr) dp & 7)) & 7) >> 1);

    off = (off < size) ? off : size;

    for (i = 0; i < off - 2; i += 3, sp += 3) {
      *dp++ = tab0[sp[0]];
      *dp++ = tab1[sp[1]];
      *dp++ = tab2[sp[2]];
      size -= 3;
    }

    off -= i;

    if (off == 1) {
      *dp++ = tab0[sp[0]];
      tab = tab0;
      tab0 = tab1;
      tab1 = tab2;
      tab2 = tab;
      size--;
      sp++;
    }
    else if (off == 2) {
      *dp++ = tab0[sp[0]];
      *dp++ = tab1[sp[1]];
      tab = tab2;
      tab2 = tab1;
      tab1 = tab0;
      tab0 = tab;
      size -= 2;
      sp += 2;
    }

    if (size > 0) {
      mlib_v_ImageLookUp_S32_S16_3_D1(sp, dp, size, tab0, tab1, tab2);
    }

    sl = (mlib_s32 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
