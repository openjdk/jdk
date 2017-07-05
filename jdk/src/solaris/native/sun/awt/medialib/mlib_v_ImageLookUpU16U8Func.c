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
static void mlib_v_ImageLookUp_U16_U8_124_D1(const mlib_u16 *src,
                                             mlib_u8        *dst,
                                             mlib_s32       xsize,
                                             const mlib_u8  *table0,
                                             const mlib_u8  *table1,
                                             const mlib_u8  *table2,
                                             const mlib_u8  *table3);

static void mlib_v_ImageLookUp_U16_U8_3_D1(const mlib_u16 *src,
                                           mlib_u8        *dst,
                                           mlib_s32       xsize,
                                           const mlib_u8  *table0,
                                           const mlib_u8  *table1,
                                           const mlib_u8  *table2);

/***************************************************************/
#define VIS_LD_U8_I(X, Y)       vis_ld_u8_i((void *)(X), (Y))

/***************************************************************/
void mlib_v_ImageLookUp_U16_U8_124_D1(const mlib_u16 *src,
                                      mlib_u8        *dst,
                                      mlib_s32       xsize,
                                      const mlib_u8  *table0,
                                      const mlib_u8  *table1,
                                      const mlib_u8  *table2,
                                      const mlib_u8  *table3)
{
  mlib_u16 *sp;                        /* pointer to source data */
  mlib_s32 s0, s1, s2, s3;             /* source data */
  mlib_s32 s4, s5, s6, s7;             /* source data */
  mlib_u8 *dl;                         /* pointer to start of destination */
  mlib_u8 *dend;                       /* pointer to end of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;                 /* destination data */
  mlib_d64 t3, t4, t5;                 /* destination data */
  mlib_d64 t6, t7, acc;                /* destination data */
  mlib_s32 emask;                      /* edge mask */
  mlib_s32 i, num;                     /* loop variable */

  dl = dst;
  dp = (mlib_d64 *) dl;
  dend = dl + xsize - 1;
  sp = (void *)src;

  vis_alignaddr((void *)0, 7);

  if (xsize >= 8) {

    s0 = sp[0];
    s1 = sp[1];
    s2 = sp[2];
    s3 = sp[3];
    s4 = sp[4];
    s5 = sp[5];
    s6 = sp[6];
    s7 = sp[7];
    sp += 8;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 16; i += 8, sp += 8) {
      t7 = VIS_LD_U8_I(table3, s7);
      t6 = VIS_LD_U8_I(table2, s6);
      t5 = VIS_LD_U8_I(table1, s5);
      t4 = VIS_LD_U8_I(table0, s4);
      t3 = VIS_LD_U8_I(table3, s3);
      t2 = VIS_LD_U8_I(table2, s2);
      t1 = VIS_LD_U8_I(table1, s1);
      t0 = VIS_LD_U8_I(table0, s0);
      acc = vis_faligndata(t7, acc);
      acc = vis_faligndata(t6, acc);
      acc = vis_faligndata(t5, acc);
      acc = vis_faligndata(t4, acc);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = sp[0];
      s1 = sp[1];
      s2 = sp[2];
      s3 = sp[3];
      s4 = sp[4];
      s5 = sp[5];
      s6 = sp[6];
      s7 = sp[7];
      *dp++ = acc;
    }

    t7 = VIS_LD_U8_I(table3, s7);
    t6 = VIS_LD_U8_I(table2, s6);
    t5 = VIS_LD_U8_I(table1, s5);
    t4 = VIS_LD_U8_I(table0, s4);
    t3 = VIS_LD_U8_I(table3, s3);
    t2 = VIS_LD_U8_I(table2, s2);
    t1 = VIS_LD_U8_I(table1, s1);
    t0 = VIS_LD_U8_I(table0, s0);
    acc = vis_faligndata(t7, acc);
    acc = vis_faligndata(t6, acc);
    acc = vis_faligndata(t5, acc);
    acc = vis_faligndata(t4, acc);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    *dp++ = acc;
  }

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_addr) dend - (mlib_addr) dp;
    sp += num;
    num++;

    if ((num & 3) == 1) {
      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table0, s0);
      acc = vis_faligndata(t0, acc);
      num--;
    }
    else if ((num & 3) == 2) {
      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table1, s0);
      acc = vis_faligndata(t0, acc);

      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table0, s0);
      acc = vis_faligndata(t0, acc);
      num -= 2;
    }
    else if ((num & 3) == 3) {
      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table2, s0);
      acc = vis_faligndata(t0, acc);

      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table1, s0);
      acc = vis_faligndata(t0, acc);

      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table0, s0);
      acc = vis_faligndata(t0, acc);
      num -= 3;
    }

    if (num != 0) {
      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table3, s0);
      acc = vis_faligndata(t0, acc);

      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table2, s0);
      acc = vis_faligndata(t0, acc);

      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table1, s0);
      acc = vis_faligndata(t0, acc);

      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table0, s0);
      acc = vis_faligndata(t0, acc);
    }

    emask = vis_edge8(dp, dend);
    vis_pst_8(acc, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U16_U8_1(const mlib_u16 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table)
{
  mlib_u16 *sl;
  mlib_u8 *dl;
  const mlib_u8 *tab = &table[0][0];
  mlib_s32 j, i;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    mlib_u16 *sp = sl;
    mlib_u8 *dp = dl;
    mlib_s32 off, size = xsize;

    off = (8 - ((mlib_addr) dp & 7)) & 7;

    off = (off < size) ? off : size;

    for (i = 0; i < off; i++, sp++) {
      *dp++ = tab[sp[0]];
      size--;
    }

    if (size > 0) {
      mlib_v_ImageLookUp_U16_U8_124_D1(sp, dp, size, tab, tab, tab, tab);
    }

    sl = (mlib_u16 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_u8 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U16_U8_2(const mlib_u16 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table)
{
  mlib_u16 *sl;
  mlib_u8 *dl;
  const mlib_u8 *tab;
  mlib_s32 j, i;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    mlib_u16 *sp = sl;
    mlib_u8 *dp = dl;
    mlib_s32 off, size = xsize * 2;
    const mlib_u8 *tab0 = &table[0][0];
    const mlib_u8 *tab1 = &table[1][0];

    off = (8 - ((mlib_addr) dp & 7)) & 7;

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
      mlib_v_ImageLookUp_U16_U8_124_D1(sp, dp, size, tab0, tab1, tab0, tab1);
    }

    sl = (mlib_u16 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_u8 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U16_U8_4(const mlib_u16 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table)
{
  mlib_u16 *sl;
  mlib_u8 *dl;
  const mlib_u8 *tab;
  mlib_s32 j;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    mlib_u16 *sp = sl;
    mlib_u8 *dp = dl;
    const mlib_u8 *tab0 = &table[0][0];
    const mlib_u8 *tab1 = &table[1][0];
    const mlib_u8 *tab2 = &table[2][0];
    const mlib_u8 *tab3 = &table[3][0];
    mlib_s32 off, size = xsize * 4;

    off = (8 - ((mlib_addr) dp & 7)) & 7;

    off = (off < size) ? off : size;

    if (off >= 4) {
      *dp++ = tab0[sp[0]];
      *dp++ = tab1[sp[1]];
      *dp++ = tab2[sp[2]];
      *dp++ = tab3[sp[3]];
      size -= 4;
      off -= 4;
      sp += 4;
    }

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
      mlib_v_ImageLookUp_U16_U8_124_D1(sp, dp, size, tab0, tab1, tab2, tab3);
    }

    sl = (mlib_u16 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_u8 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U16_U8_3_D1(const mlib_u16 *src,
                                    mlib_u8        *dst,
                                    mlib_s32       xsize,
                                    const mlib_u8  *table0,
                                    const mlib_u8  *table1,
                                    const mlib_u8  *table2)
{
  mlib_u16 *sp;                        /* pointer to source data */
  mlib_s32 s0, s1, s2, s3;             /* source data */
  mlib_s32 s4, s5, s6, s7;             /* source data */
  mlib_u8 *dl;                         /* pointer to start of destination */
  mlib_u8 *dend;                       /* pointer to end of destination */
  mlib_d64 *dp;                        /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;                 /* destination data */
  mlib_d64 t3, t4, t5;                 /* destination data */
  mlib_d64 t6, t7, acc;                /* destination data */
  mlib_s32 emask;                      /* edge mask */
  mlib_s32 i, num;                     /* loop variable */
  const mlib_u8 *table;

  dl = dst;
  sp = (void *)src;
  dp = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *)0, 7);

  if (xsize >= 8) {

    s0 = sp[0];
    s1 = sp[1];
    s2 = sp[2];
    s3 = sp[3];
    s4 = sp[4];
    s5 = sp[5];
    s6 = sp[6];
    s7 = sp[7];
    sp += 8;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 16; i += 8, sp += 8) {
      t7 = VIS_LD_U8_I(table1, s7);
      t6 = VIS_LD_U8_I(table0, s6);
      t5 = VIS_LD_U8_I(table2, s5);
      t4 = VIS_LD_U8_I(table1, s4);
      t3 = VIS_LD_U8_I(table0, s3);
      t2 = VIS_LD_U8_I(table2, s2);
      t1 = VIS_LD_U8_I(table1, s1);
      t0 = VIS_LD_U8_I(table0, s0);
      acc = vis_faligndata(t7, acc);
      acc = vis_faligndata(t6, acc);
      acc = vis_faligndata(t5, acc);
      acc = vis_faligndata(t4, acc);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      table = table0;
      table0 = table2;
      table2 = table1;
      table1 = table;
      s0 = sp[0];
      s1 = sp[1];
      s2 = sp[2];
      s3 = sp[3];
      s4 = sp[4];
      s5 = sp[5];
      s6 = sp[6];
      s7 = sp[7];
      *dp++ = acc;
    }

    t7 = VIS_LD_U8_I(table1, s7);
    t6 = VIS_LD_U8_I(table0, s6);
    t5 = VIS_LD_U8_I(table2, s5);
    t4 = VIS_LD_U8_I(table1, s4);
    t3 = VIS_LD_U8_I(table0, s3);
    t2 = VIS_LD_U8_I(table2, s2);
    t1 = VIS_LD_U8_I(table1, s1);
    t0 = VIS_LD_U8_I(table0, s0);
    acc = vis_faligndata(t7, acc);
    acc = vis_faligndata(t6, acc);
    acc = vis_faligndata(t5, acc);
    acc = vis_faligndata(t4, acc);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    table = table0;
    table0 = table2;
    table2 = table1;
    table1 = table;
    *dp++ = acc;
  }

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_addr) dend - (mlib_addr) dp;
    sp += num;
    num++;
    i = num - 3 * (num / 3);

    if (i == 2) {
      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table1, s0);
      acc = vis_faligndata(t0, acc);

      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table0, s0);
      acc = vis_faligndata(t0, acc);
      num -= 2;
    }
    else if (i == 1) {
      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table0, s0);
      acc = vis_faligndata(t0, acc);
      num--;
    }

#pragma pipeloop(0)
    for (i = 0; i < num; i += 3) {
      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table2, s0);
      acc = vis_faligndata(t0, acc);

      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table1, s0);
      acc = vis_faligndata(t0, acc);

      s0 = (mlib_s32) * sp;
      sp--;

      t0 = VIS_LD_U8_I(table0, s0);
      acc = vis_faligndata(t0, acc);
    }

    emask = vis_edge8(dp, dend);
    vis_pst_8(acc, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U16_U8_3(const mlib_u16 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table)
{
  mlib_u16 *sl;
  mlib_u8 *dl;
  const mlib_u8 *tab;
  mlib_s32 j, i;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    mlib_u16 *sp = sl;
    mlib_u8 *dp = dl;
    const mlib_u8 *tab0 = &table[0][0];
    const mlib_u8 *tab1 = &table[1][0];
    const mlib_u8 *tab2 = &table[2][0];
    mlib_s32 off, size = xsize * 3;

    off = (8 - ((mlib_addr) dp & 7)) & 7;

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
      mlib_v_ImageLookUp_U16_U8_3_D1(sp, dp, size, tab0, tab1, tab2);
    }

    sl = (mlib_u16 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_u8 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
