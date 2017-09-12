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
static void mlib_v_ImageLookUp_U8_S32_124_D1(const mlib_u8  *src,
                                             mlib_f32       *dst,
                                             mlib_s32       xsize,
                                             const mlib_f32 *table0,
                                             const mlib_f32 *table1,
                                             const mlib_f32 *table2,
                                             const mlib_f32 *table3);

static void mlib_v_ImageLookUp_U8_S32_3_D1(const mlib_u8  *src,
                                           mlib_f32       *dst,
                                           mlib_s32       xsize,
                                           const mlib_f32 *table0,
                                           const mlib_f32 *table1,
                                           const mlib_f32 *table2);

/***************************************************************/
void mlib_v_ImageLookUp_U8_S32_124_D1(const mlib_u8  *src,
                                      mlib_f32       *dst,
                                      mlib_s32       xsize,
                                      const mlib_f32 *table0,
                                      const mlib_f32 *table1,
                                      const mlib_f32 *table2,
                                      const mlib_f32 *table3)
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
  dp = dst;

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 22) & 0x3FC;
    s01 = (s0 >> 14) & 0x3FC;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 8; i += 4, dp += 4) {
      s02 = (s0 >> 6) & 0x3FC;
      s03 = (s0 << 2) & 0x3FC;
      acc0 = *(mlib_f32 *) ((mlib_u8 *) table0 + s00);
      acc1 = *(mlib_f32 *) ((mlib_u8 *) table1 + s01);
      acc2 = *(mlib_f32 *) ((mlib_u8 *) table2 + s02);
      acc3 = *(mlib_f32 *) ((mlib_u8 *) table3 + s03);
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
    acc0 = *(mlib_f32 *) ((mlib_u8 *) table0 + s00);
    acc1 = *(mlib_f32 *) ((mlib_u8 *) table1 + s01);
    acc2 = *(mlib_f32 *) ((mlib_u8 *) table2 + s02);
    acc3 = *(mlib_f32 *) ((mlib_u8 *) table3 + s03);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    dp[3] = acc3;
    dp += 4;
    i += 4;
  }

  sp = (mlib_u8 *) sa;

  if (i < xsize) {
    *dp++ = table0[sp[0]];
    i++;
    sp++;
  }

  if (i < xsize) {
    *dp++ = table1[sp[0]];
    i++;
    sp++;
  }

  if (i < xsize) {
    *dp++ = table2[sp[0]];
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_S32_1(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_s32       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_s32 **table)
{
  mlib_u8 *sl;
  mlib_s32 *dl;
  mlib_f32 *tab = (mlib_f32 *) table[0];
  mlib_s32 j, i;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    mlib_u8 *sp = sl;
    mlib_f32 *dp = (mlib_f32 *) dl;
    mlib_s32 off, size = xsize;

    off = (mlib_s32) ((4 - ((mlib_addr) sp & 3)) & 3);

    off = (off < size) ? off : size;

    for (i = 0; i < off; i++) {
      *dp++ = tab[(*sp++)];
      size--;
    }

    if (size > 0) {
      mlib_v_ImageLookUp_U8_S32_124_D1(sp, dp, size, tab, tab, tab, tab);
    }

    sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_s32 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_S32_2(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_s32       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_s32 **table)
{
  mlib_u8 *sl;
  mlib_s32 *dl;
  mlib_f32 *tab;
  mlib_s32 j, i;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    mlib_u8 *sp = sl;
    mlib_f32 *dp = (mlib_f32 *) dl;
    mlib_s32 off, size = xsize * 2;
    mlib_f32 *tab0 = (mlib_f32 *) table[0];
    mlib_f32 *tab1 = (mlib_f32 *) table[1];

    off = (mlib_s32) ((4 - ((mlib_addr) sp & 3)) & 3);

    off = (off < size) ? off : size;

    for (i = 0; i < off - 1; i += 2) {
      *dp++ = tab0[(*sp++)];
      *dp++ = tab1[(*sp++)];
      size -= 2;
    }

    if ((off & 1) != 0) {
      *dp++ = tab0[(*sp++)];
      size--;
      tab = tab0;
      tab0 = tab1;
      tab1 = tab;
    }

    if (size > 0) {
      mlib_v_ImageLookUp_U8_S32_124_D1(sp, dp, size, tab0, tab1, tab0, tab1);
    }

    sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_s32 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_S32_4(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_s32       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_s32 **table)
{
  mlib_u8 *sl;
  mlib_s32 *dl;
  mlib_f32 *tab;
  mlib_s32 j;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    mlib_u8 *sp = sl;
    mlib_f32 *dp = (mlib_f32 *) dl;
    mlib_f32 *tab0 = (mlib_f32 *) table[0];
    mlib_f32 *tab1 = (mlib_f32 *) table[1];
    mlib_f32 *tab2 = (mlib_f32 *) table[2];
    mlib_f32 *tab3 = (mlib_f32 *) table[3];
    mlib_s32 off, size = xsize * 4;

    off = (mlib_s32) ((4 - ((mlib_addr) sp & 3)) & 3);

    off = (off < size) ? off : size;

    if (off == 1) {
      *dp++ = tab0[(*sp++)];
      tab = tab0;
      tab0 = tab1;
      tab1 = tab2;
      tab2 = tab3;
      tab3 = tab;
      size--;
    }
    else if (off == 2) {
      *dp++ = tab0[(*sp++)];
      *dp++ = tab1[(*sp++)];
      tab = tab0;
      tab0 = tab2;
      tab2 = tab;
      tab = tab1;
      tab1 = tab3;
      tab3 = tab;
      size -= 2;
    }
    else if (off == 3) {
      *dp++ = tab0[(*sp++)];
      *dp++ = tab1[(*sp++)];
      *dp++ = tab2[(*sp++)];
      tab = tab3;
      tab3 = tab2;
      tab2 = tab1;
      tab1 = tab0;
      tab0 = tab;
      size -= 3;
    }

    if (size > 0) {
      mlib_v_ImageLookUp_U8_S32_124_D1(sp, dp, size, tab0, tab1, tab2, tab3);
    }

    sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_s32 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_S32_3_D1(const mlib_u8  *src,
                                    mlib_f32       *dst,
                                    mlib_s32       xsize,
                                    const mlib_f32 *table0,
                                    const mlib_f32 *table1,
                                    const mlib_f32 *table2)
{
  mlib_u32 *sa;                        /* aligned pointer to source data */
  mlib_u8 *sp;                         /* pointer to source data */
  mlib_u32 s0;                         /* source data */
  mlib_f32 *dp;                        /* aligned pointer to destination */
  mlib_f32 acc0, acc1;                 /* destination data */
  mlib_f32 acc2, acc3;                 /* destination data */
  mlib_s32 i;                          /* loop variable */
  const mlib_f32 *table;
  mlib_u32 s00, s01, s02, s03;

  sa = (mlib_u32 *) src;
  dp = dst;

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 22) & 0x3FC;
    s01 = (s0 >> 14) & 0x3FC;

#pragma pipeloop(0)
    for (i = 0; i <= xsize - 8; i += 4, dp += 4) {
      s02 = (s0 >> 6) & 0x3FC;
      s03 = (s0 << 2) & 0x3FC;
      acc0 = *(mlib_f32 *) ((mlib_u8 *) table0 + s00);
      acc1 = *(mlib_f32 *) ((mlib_u8 *) table1 + s01);
      acc2 = *(mlib_f32 *) ((mlib_u8 *) table2 + s02);
      acc3 = *(mlib_f32 *) ((mlib_u8 *) table0 + s03);
      s0 = *sa++;
      s00 = (s0 >> 22) & 0x3FC;
      s01 = (s0 >> 14) & 0x3FC;
      table = table0;
      table0 = table1;
      table1 = table2;
      table2 = table;
      dp[0] = acc0;
      dp[1] = acc1;
      dp[2] = acc2;
      dp[3] = acc3;
    }

    s02 = (s0 >> 6) & 0x3FC;
    s03 = (s0 << 2) & 0x3FC;
    acc0 = *(mlib_f32 *) ((mlib_u8 *) table0 + s00);
    acc1 = *(mlib_f32 *) ((mlib_u8 *) table1 + s01);
    acc2 = *(mlib_f32 *) ((mlib_u8 *) table2 + s02);
    acc3 = *(mlib_f32 *) ((mlib_u8 *) table0 + s03);
    dp[0] = acc0;
    dp[1] = acc1;
    dp[2] = acc2;
    dp[3] = acc3;
    table = table0;
    table0 = table1;
    table1 = table2;
    table2 = table;
    dp += 4;
    i += 4;
  }

  sp = (mlib_u8 *) sa;

  if (i < xsize) {
    *dp++ = table0[sp[0]];
    i++;
    sp++;
  }

  if (i < xsize) {
    *dp++ = table1[sp[0]];
    i++;
    sp++;
  }

  if (i < xsize) {
    *dp++ = table2[sp[0]];
  }
}

/***************************************************************/
void mlib_v_ImageLookUp_U8_S32_3(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_s32       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_s32 **table)
{
  mlib_u8 *sl;
  mlib_s32 *dl;
  mlib_f32 *tab;
  mlib_s32 j;

  sl = (void *)src;
  dl = dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    mlib_u8 *sp = sl;
    mlib_f32 *dp = (mlib_f32 *) dl;
    mlib_f32 *tab0 = (mlib_f32 *) table[0];
    mlib_f32 *tab1 = (mlib_f32 *) table[1];
    mlib_f32 *tab2 = (mlib_f32 *) table[2];
    mlib_s32 off, size = xsize * 3;

    off = (mlib_s32) ((4 - ((mlib_addr) sp & 3)) & 3);

    off = (off < size) ? off : size;

    if (off == 1) {
      *dp++ = tab0[(*sp++)];
      tab = tab0;
      tab0 = tab1;
      tab1 = tab2;
      tab2 = tab;
      size--;
    }
    else if (off == 2) {
      *dp++ = tab0[(*sp++)];
      *dp++ = tab1[(*sp++)];
      tab = tab2;
      tab2 = tab1;
      tab1 = tab0;
      tab0 = tab;
      size -= 2;
    }
    else if (off == 3) {
      *dp++ = tab0[(*sp++)];
      *dp++ = tab1[(*sp++)];
      *dp++ = tab2[(*sp++)];
      size -= 3;
    }

    if (size > 0) {
      mlib_v_ImageLookUp_U8_S32_3_D1(sp, dp, size, tab0, tab1, tab2);
    }

    sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
    dl = (mlib_s32 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
