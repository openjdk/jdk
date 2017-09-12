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
static void mlib_v_ImageLookUpSI_U8_U8_2_SrcOff0_D1(const mlib_u8  *src,
                                                    mlib_u8        *dst,
                                                    mlib_s32       xsize,
                                                    const mlib_u16 *table);

static void mlib_v_ImageLookUpSI_U8_U8_2_SrcOff1_D1(const mlib_u8  *src,
                                                    mlib_u8        *dst,
                                                    mlib_s32       xsize,
                                                    const mlib_u16 *table);

static void mlib_v_ImageLookUpSI_U8_U8_2_SrcOff2_D1(const mlib_u8  *src,
                                                    mlib_u8        *dst,
                                                    mlib_s32       xsize,
                                                    const mlib_u16 *table);

static void mlib_v_ImageLookUpSI_U8_U8_2_SrcOff3_D1(const mlib_u8  *src,
                                                    mlib_u8        *dst,
                                                    mlib_s32       xsize,
                                                    const mlib_u16 *table);

static void mlib_v_ImageLookUpSI_U8_U8_2_DstNonAl_D1(const mlib_u8  *src,
                                                     mlib_u8        *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_u16 *table);

static void mlib_v_ImageLookUpSI_U8_U8_2_DstA8D1_SMALL(const mlib_u8 *src,
                                                       mlib_u8       *dst,
                                                       mlib_s32      xsize,
                                                       const mlib_u8 **table);

static void mlib_v_ImageLookUpSI_U8_U8_2_D1_SMALL(const mlib_u8 *src,
                                                  mlib_u8       *dst,
                                                  mlib_s32      xsize,
                                                  const mlib_u8 **table);

static void mlib_v_ImageLookUpSI_U8_U8_3_SrcOff0_D1(const mlib_u8  *src,
                                                    mlib_u8        *dst,
                                                    mlib_s32       xsize,
                                                    const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_U8_3_SrcOff1_D1(const mlib_u8  *src,
                                                    mlib_u8        *dst,
                                                    mlib_s32       xsize,
                                                    const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_U8_3_SrcOff2_D1(const mlib_u8  *src,
                                                    mlib_u8        *dst,
                                                    mlib_s32       xsize,
                                                    const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_U8_3_SrcOff3_D1(const mlib_u8  *src,
                                                    mlib_u8        *dst,
                                                    mlib_s32       xsize,
                                                    const mlib_d64 *table);

static void mlib_v_ImageLookUpSI_U8_U8_3_D1_SMALL(const mlib_u8 *src,
                                                  mlib_u8       *dst,
                                                  mlib_s32      xsize,
                                                  const mlib_u8 **table);

static void mlib_v_ImageLookUpSI_U8_U8_4_SrcOff0_D1(const mlib_u8  *src,
                                                    mlib_u8        *dst,
                                                    mlib_s32       xsize,
                                                    const mlib_f32 *table);

static void mlib_v_ImageLookUpSI_U8_U8_4_DstNonAl_D1(const mlib_u8  *src,
                                                     mlib_u8        *dst,
                                                     mlib_s32       xsize,
                                                     const mlib_f32 *table);

static void mlib_v_ImageLookUpSI_U8_U8_4_DstOff0_D1_SMALL(const mlib_u8 *src,
                                                          mlib_u8       *dst,
                                                          mlib_s32      xsize,
                                                          const mlib_u8 **table);

static void mlib_v_ImageLookUpSI_U8_U8_4_DstOff1_D1_SMALL(const mlib_u8 *src,
                                                          mlib_u8       *dst,
                                                          mlib_s32      xsize,
                                                          const mlib_u8 **table);

static void mlib_v_ImageLookUpSI_U8_U8_4_DstOff2_D1_SMALL(const mlib_u8 *src,
                                                          mlib_u8       *dst,
                                                          mlib_s32      xsize,
                                                          const mlib_u8 **table);

static void mlib_v_ImageLookUpSI_U8_U8_4_DstOff3_D1_SMALL(const mlib_u8 *src,
                                                          mlib_u8       *dst,
                                                          mlib_s32      xsize,
                                                          const mlib_u8 **table);

/***************************************************************/
#define VIS_LD_U8_I(X, Y)       vis_ld_u8_i((void *)(X), (Y))
#define VIS_LD_U16_I(X, Y)      vis_ld_u16_i((void *)(X), (Y))

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_2_SrcOff0_D1(const mlib_u8  *src,
                                             mlib_u8        *dst,
                                             mlib_s32       xsize,
                                             const mlib_u16 *table)
{
  mlib_u32 *sa;          /* aligned pointer to source data */
  mlib_u8  *sp;          /* pointer to source data */
  mlib_u32 s0;           /* source data */
  mlib_u16 *dl;          /* pointer to start of destination */
  mlib_u16 *dend;        /* pointer to end of destination */
  mlib_d64 *dp;          /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;   /* destination data */
  mlib_d64 t3, acc;      /* destination data */
  mlib_s32 emask;        /* edge mask */
  mlib_s32 i, num;       /* loop variable */

  sa   = (mlib_u32*)src;
  dl   = (mlib_u16*)dst;
  dp   = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *) 0, 6);

  if (xsize >= 4) {

    s0 = sa[0];
    sa ++;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, sa++) {
      t3 = VIS_LD_U16_I(table, (s0 << 1) & 0x1FE);
      t2 = VIS_LD_U16_I(table, (s0 >> 7) & 0x1FE);
      t1 = VIS_LD_U16_I(table, (s0 >> 15) & 0x1FE);
      t0 = VIS_LD_U16_I(table, (s0 >> 23) & 0x1FE);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = sa[0];
      *dp++ = acc;
    }

    t3 = VIS_LD_U16_I(table, (s0 << 1) & 0x1FE);
    t2 = VIS_LD_U16_I(table, (s0 >> 7) & 0x1FE);
    t1 = VIS_LD_U16_I(table, (s0 >> 15) & 0x1FE);
    t0 = VIS_LD_U16_I(table, (s0 >> 23) & 0x1FE);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    *dp++ = acc;
  }

  sp = (mlib_u8*)sa;

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_u16*) dend - (mlib_u16*) dp;
    sp  += num;
    num ++;
#pragma pipeloop(0)
    for (i = 0; i < num; i ++) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table, 2*s0);
      acc = vis_faligndata(t0, acc);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_2_SrcOff1_D1(const mlib_u8  *src,
                                             mlib_u8        *dst,
                                             mlib_s32       xsize,
                                             const mlib_u16 *table)
{
  mlib_u32 *sa;          /* aligned pointer to source data */
  mlib_u8  *sp;          /* pointer to source data */
  mlib_u32 s0, s1;       /* source data */
  mlib_u16 *dl;          /* pointer to start of destination */
  mlib_u16 *dend;        /* pointer to end of destination */
  mlib_d64 *dp;          /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;   /* destination data */
  mlib_d64 t3, acc;      /* destination data */
  mlib_s32 emask;        /* edge mask */
  mlib_s32 i, num;       /* loop variable */

  sa   = (mlib_u32*)(src-1);
  dl   = (mlib_u16*)dst;
  dp   = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *) 0, 6);

  s0 = *sa++;

  if (xsize >= 4) {

    s1 = sa[0];
    sa ++;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, sa++) {
      t3 = VIS_LD_U16_I(table, (s1 >> 23) & 0x1FE);
      t2 = VIS_LD_U16_I(table, (s0 << 1) & 0x1FE);
      t1 = VIS_LD_U16_I(table, (s0 >> 7) & 0x1FE);
      t0 = VIS_LD_U16_I(table, (s0 >> 15) & 0x1FE);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = s1;
      s1 = sa[0];
      *dp++ = acc;
    }

    t3 = VIS_LD_U16_I(table, (s1 >> 23) & 0x1FE);
    t2 = VIS_LD_U16_I(table, (s0 << 1) & 0x1FE);
    t1 = VIS_LD_U16_I(table, (s0 >> 7) & 0x1FE);
    t0 = VIS_LD_U16_I(table, (s0 >> 15) & 0x1FE);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    *dp++ = acc;
  }

  sp = (mlib_u8*)sa;
  sp -= 3;

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_u16*) dend - (mlib_u16*) dp;
    sp  += num;
    num ++;
#pragma pipeloop(0)
    for (i = 0; i < num; i ++) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table, 2*s0);
      acc = vis_faligndata(t0, acc);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_2_SrcOff2_D1(const mlib_u8  *src,
                                             mlib_u8        *dst,
                                             mlib_s32       xsize,
                                             const mlib_u16 *table)
{
  mlib_u32 *sa;          /* pointer to source data */
  mlib_u8  *sp;          /* pointer to source data */
  mlib_u32 s0, s1;       /* source data */
  mlib_u16 *dl;          /* pointer to start of destination */
  mlib_u16 *dend;        /* pointer to end of destination */
  mlib_d64 *dp;          /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;   /* destination data */
  mlib_d64 t3, acc;      /* destination data */
  mlib_s32 emask;        /* edge mask */
  mlib_s32 i, num;       /* loop variable */

  sa   = (mlib_u32*)(src-2);
  dl   = (mlib_u16*)dst;
  dp   = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *) 0, 6);

  s0 = *sa++;

  if (xsize >= 4) {

    s1 = sa[0];
    sa ++;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, sa++) {
      t3 = VIS_LD_U16_I(table, (s1 >> 15) & 0x1FE);
      t2 = VIS_LD_U16_I(table, (s1 >> 23) & 0x1FE);
      t1 = VIS_LD_U16_I(table, (s0 << 1) & 0x1FE);
      t0 = VIS_LD_U16_I(table, (s0 >> 7) & 0x1FE);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = s1;
      s1 = sa[0];
      *dp++ = acc;
    }

    t3 = VIS_LD_U16_I(table, (s1 >> 15) & 0x1FE);
    t2 = VIS_LD_U16_I(table, (s1 >> 23) & 0x1FE);
    t1 = VIS_LD_U16_I(table, (s0 << 1) & 0x1FE);
    t0 = VIS_LD_U16_I(table, (s0 >> 7) & 0x1FE);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    *dp++ = acc;
  }

  sp = (mlib_u8*)sa;
  sp -= 2;

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_u16*) dend - (mlib_u16*) dp;
    sp  += num;
    num ++;
#pragma pipeloop(0)
    for (i = 0; i < num; i ++) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table, 2*s0);
      acc = vis_faligndata(t0, acc);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_2_SrcOff3_D1(const mlib_u8  *src,
                                             mlib_u8        *dst,
                                             mlib_s32       xsize,
                                             const mlib_u16 *table)
{
  mlib_u32 *sa;          /* aligned pointer to source data */
  mlib_u8  *sp;          /* pointer to source data */
  mlib_u32 s0, s1;       /* source data */
  mlib_u16 *dl;          /* pointer to start of destination */
  mlib_u16 *dend;        /* pointer to end of destination */
  mlib_d64 *dp;          /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;   /* destination data */
  mlib_d64 t3, acc;      /* destination data */
  mlib_s32 emask;        /* edge mask */
  mlib_s32 i, num;       /* loop variable */

  sa   = (mlib_u32*)(src-3);
  dl   = (mlib_u16*)dst;
  dp   = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *) 0, 6);

  s0 = *sa++;

  if (xsize >= 4) {

    s1 = sa[0];
    sa ++;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, sa++) {
      t3 = VIS_LD_U16_I(table, (s1 >> 7) & 0x1FE);
      t2 = VIS_LD_U16_I(table, (s1 >> 15) & 0x1FE);
      t1 = VIS_LD_U16_I(table, (s1 >> 23) & 0x1FE);
      t0 = VIS_LD_U16_I(table, (s0 << 1) & 0x1FE);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = s1;
      s1 = sa[0];
      *dp++ = acc;
    }

    t3 = VIS_LD_U16_I(table, (s1 >> 7) & 0x1FE);
    t2 = VIS_LD_U16_I(table, (s1 >> 15) & 0x1FE);
    t1 = VIS_LD_U16_I(table, (s1 >> 23) & 0x1FE);
    t0 = VIS_LD_U16_I(table, (s0 << 1) & 0x1FE);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    *dp++ = acc;
  }

  sp = (mlib_u8*)sa;
  sp -= 1;

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (mlib_u16*) dend - (mlib_u16*) dp;
    sp  += num;
    num ++;
#pragma pipeloop(0)
    for (i = 0; i < num; i ++) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table, 2*s0);
      acc = vis_faligndata(t0, acc);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_2_DstNonAl_D1(const mlib_u8  *src,
                                              mlib_u8        *dst,
                                              mlib_s32       xsize,
                                              const mlib_u16 *table)
{
  mlib_u32 *sa;             /* aligned pointer to source data */
  mlib_u8  *sp;             /* pointer to source data */
  mlib_u32 s0, s1, s2, s3;  /* source data */
  mlib_u8  *dl;             /* pointer to start of destination */
  mlib_u8  *dend;           /* pointer to end of destination */
  mlib_d64 *dp;             /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;      /* destination data */
  mlib_d64 t3, t4, t5;      /* destination data */
  mlib_d64 t6, t7, acc0;    /* destination data */
  mlib_d64 acc1, acc2;      /* destination data */
  mlib_d64 acc3, acc4;      /* destination data */
  mlib_s32 emask;           /* edge mask */
  mlib_s32 i, num;          /* loop variable */
  mlib_s32 off;             /* offset */

  sa   = (mlib_u32*)src;
  dl   = dst;
  sp   = (void *)src;
  dend = dl + 2*xsize - 1;
  dp   = (mlib_d64 *) ((mlib_addr) dl & (~7));
  off  = (mlib_addr) dp - (mlib_addr) dl;

  emask = vis_edge8(dl, dend);
  num = (xsize < 4) ? xsize : 4;

  sp += (num-1);

  vis_alignaddr(dp, 6);

  for (i = 0; i < num; i ++) {
    s0 = (mlib_s32) *sp;
    sp --;

    t0  = VIS_LD_U16_I(table, 2*s0);
    acc0 = vis_faligndata(t0, acc0);
  }

  vis_alignaddr(dp, off);
  vis_pst_8(vis_faligndata(acc0, acc0), dp++, emask);

  sa++;

  xsize -= 4;

  i = 0;

  if (xsize >= 16) {

    s0 = sa[0];
    s1 = sa[1];
    s2 = sa[2];
    s3 = sa[3];
    sa += 4;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 32; i+=16, sa+=4) {
      vis_alignaddr(dp, 6);
      t3 = VIS_LD_U16_I(table, (s0 << 1) & 0x1FE);
      t2 = VIS_LD_U16_I(table, (s0 >> 7) & 0x1FE);
      t1 = VIS_LD_U16_I(table, (s0 >> 15) & 0x1FE);
      t0 = VIS_LD_U16_I(table, (s0 >> 23) & 0x1FE);
      acc1 = vis_faligndata(t3, acc1);
      acc1 = vis_faligndata(t2, acc1);
      acc1 = vis_faligndata(t1, acc1);
      acc1 = vis_faligndata(t0, acc1);
      t7 = VIS_LD_U16_I(table, (s1 << 1) & 0x1FE);
      t6 = VIS_LD_U16_I(table, (s1 >> 7) & 0x1FE);
      t5 = VIS_LD_U16_I(table, (s1 >> 15) & 0x1FE);
      t4 = VIS_LD_U16_I(table, (s1 >> 23) & 0x1FE);
      acc2 = vis_faligndata(t7, acc2);
      acc2 = vis_faligndata(t6, acc2);
      acc2 = vis_faligndata(t5, acc2);
      acc2 = vis_faligndata(t4, acc2);
      t3 = VIS_LD_U16_I(table, (s2 << 1) & 0x1FE);
      t2 = VIS_LD_U16_I(table, (s2 >> 7) & 0x1FE);
      t1 = VIS_LD_U16_I(table, (s2 >> 15) & 0x1FE);
      t0 = VIS_LD_U16_I(table, (s2 >> 23) & 0x1FE);
      acc3 = vis_faligndata(t3, acc3);
      acc3 = vis_faligndata(t2, acc3);
      acc3 = vis_faligndata(t1, acc3);
      acc3 = vis_faligndata(t0, acc3);
      t7 = VIS_LD_U16_I(table, (s3 << 1) & 0x1FE);
      t6 = VIS_LD_U16_I(table, (s3 >> 7) & 0x1FE);
      t5 = VIS_LD_U16_I(table, (s3 >> 15) & 0x1FE);
      t4 = VIS_LD_U16_I(table, (s3 >> 23) & 0x1FE);
      acc4 = vis_faligndata(t7, acc4);
      acc4 = vis_faligndata(t6, acc4);
      acc4 = vis_faligndata(t5, acc4);
      acc4 = vis_faligndata(t4, acc4);
      vis_alignaddr(dp, off);
      s0 = sa[0];
      s1 = sa[1];
      s2 = sa[2];
      s3 = sa[3];
      *dp++ = vis_faligndata(acc0, acc1);
      *dp++ = vis_faligndata(acc1, acc2);
      *dp++ = vis_faligndata(acc2, acc3);
      *dp++ = vis_faligndata(acc3, acc4);
      acc0 = acc4;
    }

    vis_alignaddr(dp, 6);
    t3 = VIS_LD_U16_I(table, (s0 << 1) & 0x1FE);
    t2 = VIS_LD_U16_I(table, (s0 >> 7) & 0x1FE);
    t1 = VIS_LD_U16_I(table, (s0 >> 15) & 0x1FE);
    t0 = VIS_LD_U16_I(table, (s0 >> 23) & 0x1FE);
    acc1 = vis_faligndata(t3, acc1);
    acc1 = vis_faligndata(t2, acc1);
    acc1 = vis_faligndata(t1, acc1);
    acc1 = vis_faligndata(t0, acc1);
    t7 = VIS_LD_U16_I(table, (s1 << 1) & 0x1FE);
    t6 = VIS_LD_U16_I(table, (s1 >> 7) & 0x1FE);
    t5 = VIS_LD_U16_I(table, (s1 >> 15) & 0x1FE);
    t4 = VIS_LD_U16_I(table, (s1 >> 23) & 0x1FE);
    acc2 = vis_faligndata(t7, acc2);
    acc2 = vis_faligndata(t6, acc2);
    acc2 = vis_faligndata(t5, acc2);
    acc2 = vis_faligndata(t4, acc2);
    t3 = VIS_LD_U16_I(table, (s2 << 1) & 0x1FE);
    t2 = VIS_LD_U16_I(table, (s2 >> 7) & 0x1FE);
    t1 = VIS_LD_U16_I(table, (s2 >> 15) & 0x1FE);
    t0 = VIS_LD_U16_I(table, (s2 >> 23) & 0x1FE);
    acc3 = vis_faligndata(t3, acc3);
    acc3 = vis_faligndata(t2, acc3);
    acc3 = vis_faligndata(t1, acc3);
    acc3 = vis_faligndata(t0, acc3);
    t7 = VIS_LD_U16_I(table, (s3 << 1) & 0x1FE);
    t6 = VIS_LD_U16_I(table, (s3 >> 7) & 0x1FE);
    t5 = VIS_LD_U16_I(table, (s3 >> 15) & 0x1FE);
    t4 = VIS_LD_U16_I(table, (s3 >> 23) & 0x1FE);
    acc4 = vis_faligndata(t7, acc4);
    acc4 = vis_faligndata(t6, acc4);
    acc4 = vis_faligndata(t5, acc4);
    acc4 = vis_faligndata(t4, acc4);
    vis_alignaddr(dp, off);
    *dp++ = vis_faligndata(acc0, acc1);
    *dp++ = vis_faligndata(acc1, acc2);
    *dp++ = vis_faligndata(acc2, acc3);
    *dp++ = vis_faligndata(acc3, acc4);
    acc0 = acc4; i+=16;
  }

  if (i <= xsize - 8) {
    s0 = sa[0];
    s1 = sa[1];
    vis_alignaddr(dp, 6);
    t3 = VIS_LD_U16_I(table, (s0 << 1) & 0x1FE);
    t2 = VIS_LD_U16_I(table, (s0 >> 7) & 0x1FE);
    t1 = VIS_LD_U16_I(table, (s0 >> 15) & 0x1FE);
    t0 = VIS_LD_U16_I(table, (s0 >> 23) & 0x1FE);
    acc1 = vis_faligndata(t3, acc1);
    acc1 = vis_faligndata(t2, acc1);
    acc1 = vis_faligndata(t1, acc1);
    acc1 = vis_faligndata(t0, acc1);
    t7 = VIS_LD_U16_I(table, (s1 << 1) & 0x1FE);
    t6 = VIS_LD_U16_I(table, (s1 >> 7) & 0x1FE);
    t5 = VIS_LD_U16_I(table, (s1 >> 15) & 0x1FE);
    t4 = VIS_LD_U16_I(table, (s1 >> 23) & 0x1FE);
    acc2 = vis_faligndata(t7, acc2);
    acc2 = vis_faligndata(t6, acc2);
    acc2 = vis_faligndata(t5, acc2);
    acc2 = vis_faligndata(t4, acc2);
    vis_alignaddr(dp, off);
    *dp++ = vis_faligndata(acc0, acc1);
    *dp++ = vis_faligndata(acc1, acc2);
    acc0 = acc2; i += 8; sa += 2;
  }

  if (i <= xsize - 4) {
    s0 = *sa++;
    vis_alignaddr(dp, 6);
    t3 = VIS_LD_U16_I(table, (s0 << 1) & 0x1FE);
    t2 = VIS_LD_U16_I(table, (s0 >> 7) & 0x1FE);
    t1 = VIS_LD_U16_I(table, (s0 >> 15) & 0x1FE);
    t0 = VIS_LD_U16_I(table, (s0 >> 23) & 0x1FE);
    acc1 = vis_faligndata(t3, acc1);
    acc1 = vis_faligndata(t2, acc1);
    acc1 = vis_faligndata(t1, acc1);
    acc1 = vis_faligndata(t0, acc1);
    vis_alignaddr(dp, off);
    *dp++ = vis_faligndata(acc0, acc1);
    acc0 = acc1;
  }

  sp = (mlib_u8*)sa;

  if ((mlib_addr) dp <= (mlib_addr) dend) {

    num = (((mlib_u8*) dend - (mlib_u8*) dp) + off + 1) >> 1;
    sp  += (num - 1);
    vis_alignaddr(dp, 6);
#pragma pipeloop(0)
    for (i = 0; i < num; i ++) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U16_I(table, 2*s0);
      acc1 = vis_faligndata(t0, acc1);
    }

    vis_alignaddr(dp, off);
    emask = vis_edge8(dp, dend);
    vis_pst_8(vis_faligndata(acc0, acc1), dp++, emask);
  }

  if ((mlib_addr) dp <= (mlib_addr) dend) {
    emask = vis_edge8(dp, dend);
    vis_pst_8(vis_faligndata(acc1, acc1), dp++, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_2_DstA8D1_SMALL(const mlib_u8 *src,
                                                mlib_u8       *dst,
                                                mlib_s32      xsize,
                                                const mlib_u8 **table)
{
  mlib_u8  *sp;              /* pointer to source data */
  mlib_u32 s0, s1, s2, s3;   /* source data */
  mlib_u16 *dl;              /* pointer to start of destination */
  mlib_u16 *dend;            /* pointer to end of destination */
  mlib_d64 *dp;              /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;       /* destination data */
  mlib_d64 t3, t4, t5;       /* destination data */
  mlib_d64 t6, t7, acc;      /* destination data */
  mlib_s32 emask;            /* edge mask */
  mlib_s32 i, num;           /* loop variable */
  const mlib_u8  *tab0 = table[0];
  const mlib_u8  *tab1 = table[1];

  sp   = (void *)src;
  dl   = (mlib_u16*)dst;
  dp   = (mlib_d64 *) dl;
  dend = dl + xsize - 1;

  vis_alignaddr((void *) 0, 7);

  if (xsize >= 4) {

    s0 = sp[0];
    s1 = sp[1];
    s2 = sp[2];
    s3 = sp[3];
    sp += 4;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, sp+=4) {
      t7 = VIS_LD_U8_I(tab1, s3);
      t6 = VIS_LD_U8_I(tab0, s3);
      t5 = VIS_LD_U8_I(tab1, s2);
      t4 = VIS_LD_U8_I(tab0, s2);
      t3 = VIS_LD_U8_I(tab1, s1);
      t2 = VIS_LD_U8_I(tab0, s1);
      t1 = VIS_LD_U8_I(tab1, s0);
      t0 = VIS_LD_U8_I(tab0, s0);
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
      *dp++ = acc;
    }

    t7 = VIS_LD_U8_I(tab1, s3);
    t6 = VIS_LD_U8_I(tab0, s3);
    t5 = VIS_LD_U8_I(tab1, s2);
    t4 = VIS_LD_U8_I(tab0, s2);
    t3 = VIS_LD_U8_I(tab1, s1);
    t2 = VIS_LD_U8_I(tab0, s1);
    t1 = VIS_LD_U8_I(tab1, s0);
    t0 = VIS_LD_U8_I(tab0, s0);
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

    num = (mlib_u16*) dend - (mlib_u16*) dp;
    sp  += num;
    num ++;
#pragma pipeloop(0)
    for (i = 0; i < num; i ++) {
      s0 = (mlib_s32) *sp;
      sp --;

      t0  = VIS_LD_U8_I(tab1, s0);
      acc = vis_faligndata(t0, acc);

      t0  = VIS_LD_U8_I(tab0, s0);
      acc = vis_faligndata(t0, acc);
    }

    emask = vis_edge16(dp, dend);
    vis_pst_16(acc, dp, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_2_D1_SMALL(const mlib_u8 *src,
                                           mlib_u8       *dst,
                                           mlib_s32      xsize,
                                           const mlib_u8 **table)
{
  mlib_u8  *sp;                /* pointer to source data */
  mlib_u32 s0, s1, s2, s3, s4; /* source data */
  mlib_u8  *dl;                /* pointer to start of destination */
  mlib_u8  *dend;              /* pointer to end of destination */
  mlib_d64 *dp;                /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;         /* destination data */
  mlib_d64 t3, t4, t5;         /* destination data */
  mlib_d64 t6, t7, acc;        /* destination data */
  mlib_s32 emask;              /* edge mask */
  mlib_s32 i, num;             /* loop variable */
  const mlib_u8  *tab0 = table[0];
  const mlib_u8  *tab1 = table[1];

  sp   = (void *)src;
  dl   = dst;

  dend = dl + 2 * xsize - 1;

  vis_alignaddr((void *) 0, 7);

  s0 = *sp++;
  *dl++ = tab0[s0];
  dp   = (mlib_d64 *) dl;
  xsize--;

  if (xsize >= 4) {

    s1 = sp[0];
    s2 = sp[1];
    s3 = sp[2];
    s4 = sp[3];
    sp += 4;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, sp+=4) {
      t7 = VIS_LD_U8_I(tab0, s4);
      t6 = VIS_LD_U8_I(tab1, s3);
      t5 = VIS_LD_U8_I(tab0, s3);
      t4 = VIS_LD_U8_I(tab1, s2);
      t3 = VIS_LD_U8_I(tab0, s2);
      t2 = VIS_LD_U8_I(tab1, s1);
      t1 = VIS_LD_U8_I(tab0, s1);
      t0 = VIS_LD_U8_I(tab1, s0);
      acc = vis_faligndata(t7, acc);
      acc = vis_faligndata(t6, acc);
      acc = vis_faligndata(t5, acc);
      acc = vis_faligndata(t4, acc);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = s4;
      s1 = sp[0];
      s2 = sp[1];
      s3 = sp[2];
      s4 = sp[3];
      *dp++ = acc;
    }

    t7 = VIS_LD_U8_I(tab0, s4);
    t6 = VIS_LD_U8_I(tab1, s3);
    t5 = VIS_LD_U8_I(tab0, s3);
    t4 = VIS_LD_U8_I(tab1, s2);
    t3 = VIS_LD_U8_I(tab0, s2);
    t2 = VIS_LD_U8_I(tab1, s1);
    t1 = VIS_LD_U8_I(tab0, s1);
    t0 = VIS_LD_U8_I(tab1, s0);
    acc = vis_faligndata(t7, acc);
    acc = vis_faligndata(t6, acc);
    acc = vis_faligndata(t5, acc);
    acc = vis_faligndata(t4, acc);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    s0 = s4;
    *dp++ = acc;
  }

  num = ((mlib_u8*) dend - (mlib_u8*) dp) >> 1;
  sp  += num;
  num ++;

#pragma pipeloop(0)
  for (i = 0; i < num; i ++) {
    s1 = (mlib_s32) *sp;
    sp --;

    t0  = VIS_LD_U8_I(tab1, s1);
    acc = vis_faligndata(t0, acc);

    t0  = VIS_LD_U8_I(tab0, s1);
    acc = vis_faligndata(t0, acc);
  }

  t0  = VIS_LD_U8_I(tab1, s0);
  acc = vis_faligndata(t0, acc);
  emask = vis_edge8(dp, dend);
  vis_pst_8(acc, dp, emask);
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_2(const mlib_u8 *src,
                                  mlib_s32      slb,
                                  mlib_u8       *dst,
                                  mlib_s32      dlb,
                                  mlib_s32      xsize,
                                  mlib_s32      ysize,
                                  const mlib_u8 **table)
{
  if ((xsize * ysize) < 650) {
    mlib_u8  *sl;
    mlib_u8  *dl;
    mlib_s32 i, j;

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j ++) {
      mlib_u8 *sp = sl;
      mlib_u8 *dp = dl;
      mlib_s32 off, s0, size = xsize;

      off = ((8 - ((mlib_addr)dp & 7)) & 7) >> 1;
      off = (off < size) ? off : size;

      for (i = 0; i < off; i++) {
        s0 = *sp++;
        *dp++ = table[0][s0];
        *dp++ = table[1][s0];
        size--;
      }

      if (size > 0) {

        if (((mlib_addr)dp & 1) == 0) {
          mlib_v_ImageLookUpSI_U8_U8_2_DstA8D1_SMALL(sp, dp, size, table);
        } else {
          mlib_v_ImageLookUpSI_U8_U8_2_D1_SMALL(sp, dp, size, table);
        }
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_u8 *) ((mlib_u8 *) dl + dlb);
    }

  } else {
    mlib_u8  *sl;
    mlib_u8  *dl;
    mlib_u16 tab[256];
    const mlib_u8  *tab0 = table[0];
    const mlib_u8  *tab1 = table[1];
    mlib_s32 i, j, s0, s1, s2;

    s0 = tab0[0];
    s1 = tab1[0];
    for (i = 1; i < 256; i++) {
      s2 = (s0 << 8) + s1;
      s0 = tab0[i];
      s1 = tab1[i];
      tab[i-1] = (mlib_u16)s2;
    }

    s2 = (s0 << 8) + s1;
    tab[255] = (mlib_u16)s2;

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j ++) {
      mlib_u8 *sp = sl;
      mlib_u8 *dp = dl;
      mlib_s32 off, s0, size = xsize;

      if (((mlib_addr)dp & 1) == 0) {

        off = ((8 - ((mlib_addr)dp & 7)) & 7) >> 1;
        off = (off < size) ? off : size;

        for (i = 0; i < off; i++) {
          *(mlib_u16*)dp = tab[(*sp)];
          dp += 2;
          size--; sp++;
        }

        if (size > 0) {

          off = (mlib_addr)sp & 3;

          if (off == 0) {
            mlib_v_ImageLookUpSI_U8_U8_2_SrcOff0_D1(sp, dp, size, tab);
          } else if (off == 1) {
            mlib_v_ImageLookUpSI_U8_U8_2_SrcOff1_D1(sp, dp, size, tab);
          } else if (off == 2) {
            mlib_v_ImageLookUpSI_U8_U8_2_SrcOff2_D1(sp, dp, size, tab);
          } else {
            mlib_v_ImageLookUpSI_U8_U8_2_SrcOff3_D1(sp, dp, size, tab);
          }
        }

      } else {

        off = ((4 - ((mlib_addr)sp & 3)) & 3);
        off = (off < size) ? off : size;

        for (i = 0; i < off; i++) {
          s0 = tab[(*sp)];
          *dp++ = (s0 >> 8);
          *dp++ = (s0 & 0xFF);
          size--; sp++;
        }

        if (size > 0) {
          mlib_v_ImageLookUpSI_U8_U8_2_DstNonAl_D1(sp, dp, size, tab);
        }
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_u8 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_3_SrcOff0_D1(const mlib_u8  *src,
                                             mlib_u8        *dst,
                                             mlib_s32       xsize,
                                             const mlib_d64 *table)
{
  mlib_u8  *sp;            /* pointer to source data */
  mlib_u32 *sa;            /* aligned pointer to source data */
  mlib_u32 s0;             /* source data */
  mlib_u8  *dl;            /* pointer to start of destination */
  mlib_f32 *dp;            /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3; /* destination data */
  mlib_d64 acc0, acc1;     /* destination data */
  mlib_s32 i;              /* loop variable */
  mlib_u8  *ptr;

  dl   =  dst;
  dp   = (mlib_f32 *) dl;
  sp = (void *)src;
  sa = (mlib_u32*)sp;

  vis_alignaddr((void *) 0, 3);

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp+=3) {
      t0 = *(mlib_d64*)((mlib_u8*)table + ((s0 >> 21) & 0x7F8 ));
      t1 = *(mlib_d64*)((mlib_u8*)table + ((s0 >> 13) & 0x7F8 ));
      t2 = *(mlib_d64*)((mlib_u8*)table + ((s0 >> 5) & 0x7F8 ));
      t3 = *(mlib_d64*)((mlib_u8*)table + ((s0 << 3) & 0x7F8 ));
      acc0 = vis_faligndata(t0, t0);
      acc0 = vis_faligndata(acc0, t1);
      acc1 = vis_faligndata(acc0, acc0);
      acc0 = vis_faligndata(acc0, t2);
      acc1 = vis_faligndata(acc1, acc0);
      acc0 = vis_faligndata(acc0, t3);
      s0 = *sa++;
      dp[0] = vis_read_lo(acc1);
      dp[1] = vis_read_hi(acc0);
      dp[2] = vis_read_lo(acc0);
    }

    t0 = *(mlib_d64*)((mlib_u8*)table + ((s0 >> 21) & 0x7F8 ));
    t1 = *(mlib_d64*)((mlib_u8*)table + ((s0 >> 13) & 0x7F8 ));
    t2 = *(mlib_d64*)((mlib_u8*)table + ((s0 >> 5) & 0x7F8 ));
    t3 = *(mlib_d64*)((mlib_u8*)table + ((s0 << 3) & 0x7F8 ));
    acc0 = vis_faligndata(t0, t0);
    acc0 = vis_faligndata(acc0, t1);
    acc1 = vis_faligndata(acc0, acc0);
    acc0 = vis_faligndata(acc0, t2);
    acc1 = vis_faligndata(acc1, acc0);
    acc0 = vis_faligndata(acc0, t3);
    dp[0] = vis_read_lo(acc1);
    dp[1] = vis_read_hi(acc0);
    dp[2] = vis_read_lo(acc0);
    dp += 3;
    i += 4;
  }

  dl = (mlib_u8*)dp;

#pragma pipeloop(0)
  for (; i < xsize; i++) {
    ptr = (mlib_u8*)(table + src[i]);
    dl[0] = ptr[0];
    dl[1] = ptr[1];
    dl[2] = ptr[2];
    dl += 3;
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_3_SrcOff1_D1(const mlib_u8  *src,
                                             mlib_u8        *dst,
                                             mlib_s32       xsize,
                                             const mlib_d64 *table)
{
  mlib_u8  *sp;            /* pointer to source data */
  mlib_u32 *sa;            /* aligned pointer to source data */
  mlib_u32 s0, s1;         /* source data */
  mlib_u8  *dl;            /* pointer to start of destination */
  mlib_f32 *dp;            /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3; /* destination data */
  mlib_d64 acc0, acc1;     /* destination data */
  mlib_s32 i;              /* loop variable */
  mlib_u8  *ptr;

  dl   =  dst;
  dp   = (mlib_f32 *) dl;
  sp = (void *)src;
  sa = (mlib_u32*)(sp - 1);

  vis_alignaddr((void *) 0, 3);

  i = 0;
  s0 = *sa++;

  if (xsize >= 4) {

    s1 = *sa++;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp+=3) {
      t0 = *(mlib_d64*)((mlib_u8*)table + ((s0 >> 13) & 0x7F8 ));
      t1 = *(mlib_d64*)((mlib_u8*)table + ((s0 >> 5) & 0x7F8 ));
      t2 = *(mlib_d64*)((mlib_u8*)table + ((s0 << 3) & 0x7F8 ));
      t3 = *(mlib_d64*)((mlib_u8*)table + ((s1 >> 21) & 0x7F8 ));
      acc0 = vis_faligndata(t0, t0);
      acc0 = vis_faligndata(acc0, t1);
      acc1 = vis_faligndata(acc0, acc0);
      acc0 = vis_faligndata(acc0, t2);
      acc1 = vis_faligndata(acc1, acc0);
      acc0 = vis_faligndata(acc0, t3);
      s0 = s1;
      s1 = *sa++;
      dp[0] = vis_read_lo(acc1);
      dp[1] = vis_read_hi(acc0);
      dp[2] = vis_read_lo(acc0);
    }

    t0 = *(mlib_d64*)((mlib_u8*)table + ((s0 >> 13) & 0x7F8 ));
    t1 = *(mlib_d64*)((mlib_u8*)table + ((s0 >> 5) & 0x7F8 ));
    t2 = *(mlib_d64*)((mlib_u8*)table + ((s0 << 3) & 0x7F8 ));
    t3 = *(mlib_d64*)((mlib_u8*)table + ((s1 >> 21) & 0x7F8 ));
    acc0 = vis_faligndata(t0, t0);
    acc0 = vis_faligndata(acc0, t1);
    acc1 = vis_faligndata(acc0, acc0);
    acc0 = vis_faligndata(acc0, t2);
    acc1 = vis_faligndata(acc1, acc0);
    acc0 = vis_faligndata(acc0, t3);
    dp[0] = vis_read_lo(acc1);
    dp[1] = vis_read_hi(acc0);
    dp[2] = vis_read_lo(acc0);
    dp += 3;
    i += 4;
  }

  dl = (mlib_u8*)dp;

#pragma pipeloop(0)
  for (; i < xsize; i++) {
    ptr = (mlib_u8*)(table + src[i]);
    dl[0] = ptr[0];
    dl[1] = ptr[1];
    dl[2] = ptr[2];
    dl += 3;
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_3_SrcOff2_D1(const mlib_u8  *src,
                                             mlib_u8        *dst,
                                             mlib_s32       xsize,
                                             const mlib_d64 *table)
{
  mlib_u8  *sp;            /* pointer to source data */
  mlib_u32 *sa;            /* aligned pointer to source data */
  mlib_u32 s0, s1;         /* source data */
  mlib_u8  *dl;            /* pointer to start of destination */
  mlib_f32 *dp;            /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3; /* destination data */
  mlib_d64 acc0, acc1;     /* destination data */
  mlib_s32 i;              /* loop variable */
  mlib_u8  *ptr;

  dl   =  dst;
  dp   = (mlib_f32 *) dl;
  sp = (void *)src;
  sa = (mlib_u32*)(sp - 2);

  vis_alignaddr((void *) 0, 3);

  i = 0;
  s0 = *sa++;

  if (xsize >= 4) {

    s1 = *sa++;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp+=3) {
      t0 = *(mlib_d64*)((mlib_u8*)table + ((s0 >> 5) & 0x7F8 ));
      t1 = *(mlib_d64*)((mlib_u8*)table + ((s0 << 3) & 0x7F8 ));
      t2 = *(mlib_d64*)((mlib_u8*)table + ((s1 >> 21) & 0x7F8 ));
      t3 = *(mlib_d64*)((mlib_u8*)table + ((s1 >> 13) & 0x7F8 ));
      acc0 = vis_faligndata(t0, t0);
      acc0 = vis_faligndata(acc0, t1);
      acc1 = vis_faligndata(acc0, acc0);
      acc0 = vis_faligndata(acc0, t2);
      acc1 = vis_faligndata(acc1, acc0);
      acc0 = vis_faligndata(acc0, t3);
      s0 = s1;
      s1 = *sa++;
      dp[0] = vis_read_lo(acc1);
      dp[1] = vis_read_hi(acc0);
      dp[2] = vis_read_lo(acc0);
    }

    t0 = *(mlib_d64*)((mlib_u8*)table + ((s0 >> 5) & 0x7F8 ));
    t1 = *(mlib_d64*)((mlib_u8*)table + ((s0 << 3) & 0x7F8 ));
    t2 = *(mlib_d64*)((mlib_u8*)table + ((s1 >> 21) & 0x7F8 ));
    t3 = *(mlib_d64*)((mlib_u8*)table + ((s1 >> 13) & 0x7F8 ));
    acc0 = vis_faligndata(t0, t0);
    acc0 = vis_faligndata(acc0, t1);
    acc1 = vis_faligndata(acc0, acc0);
    acc0 = vis_faligndata(acc0, t2);
    acc1 = vis_faligndata(acc1, acc0);
    acc0 = vis_faligndata(acc0, t3);
    dp[0] = vis_read_lo(acc1);
    dp[1] = vis_read_hi(acc0);
    dp[2] = vis_read_lo(acc0);
    dp += 3;
    i += 4;
  }

  dl = (mlib_u8*)dp;

#pragma pipeloop(0)
  for (; i < xsize; i++) {
    ptr = (mlib_u8*)(table + src[i]);
    dl[0] = ptr[0];
    dl[1] = ptr[1];
    dl[2] = ptr[2];
    dl += 3;
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_3_SrcOff3_D1(const mlib_u8  *src,
                                             mlib_u8        *dst,
                                             mlib_s32       xsize,
                                             const mlib_d64 *table)
{
  mlib_u8  *sp;            /* pointer to source data */
  mlib_u32 *sa;            /* aligned pointer to source data */
  mlib_u32 s0, s1;         /* source data */
  mlib_u8  *dl;            /* pointer to start of destination */
  mlib_f32 *dp;            /* aligned pointer to destination */
  mlib_d64 t0, t1, t2, t3; /* destination data */
  mlib_d64 acc0, acc1;     /* destination data */
  mlib_s32 i;              /* loop variable */
  mlib_u8  *ptr;

  dl   =  dst;
  dp   = (mlib_f32 *) dl;
  sp = (void *)src;
  sa = (mlib_u32*)(sp - 3);

  vis_alignaddr((void *) 0, 3);

  i = 0;
  s0 = *sa++;

  if (xsize >= 4) {

    s1 = *sa++;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp+=3) {
      t0 = *(mlib_d64*)((mlib_u8*)table + ((s0 << 3) & 0x7F8 ));
      t1 = *(mlib_d64*)((mlib_u8*)table + ((s1 >> 21) & 0x7F8 ));
      t2 = *(mlib_d64*)((mlib_u8*)table + ((s1 >> 13) & 0x7F8 ));
      t3 = *(mlib_d64*)((mlib_u8*)table + ((s1 >> 5) & 0x7F8 ));
      acc0 = vis_faligndata(t0, t0);
      acc0 = vis_faligndata(acc0, t1);
      acc1 = vis_faligndata(acc0, acc0);
      acc0 = vis_faligndata(acc0, t2);
      acc1 = vis_faligndata(acc1, acc0);
      acc0 = vis_faligndata(acc0, t3);
      s0 = s1;
      s1 = *sa++;
      dp[0] = vis_read_lo(acc1);
      dp[1] = vis_read_hi(acc0);
      dp[2] = vis_read_lo(acc0);
    }

    t0 = *(mlib_d64*)((mlib_u8*)table + ((s0 << 3) & 0x7F8 ));
    t1 = *(mlib_d64*)((mlib_u8*)table + ((s1 >> 21) & 0x7F8 ));
    t2 = *(mlib_d64*)((mlib_u8*)table + ((s1 >> 13) & 0x7F8 ));
    t3 = *(mlib_d64*)((mlib_u8*)table + ((s1 >> 5) & 0x7F8 ));
    acc0 = vis_faligndata(t0, t0);
    acc0 = vis_faligndata(acc0, t1);
    acc1 = vis_faligndata(acc0, acc0);
    acc0 = vis_faligndata(acc0, t2);
    acc1 = vis_faligndata(acc1, acc0);
    acc0 = vis_faligndata(acc0, t3);
    dp[0] = vis_read_lo(acc1);
    dp[1] = vis_read_hi(acc0);
    dp[2] = vis_read_lo(acc0);
    dp += 3;
    i += 4;
  }

  dl = (mlib_u8*)dp;

#pragma pipeloop(0)
  for (; i < xsize; i++) {
    ptr = (mlib_u8*)(table + src[i]);
    dl[0] = ptr[0];
    dl[1] = ptr[1];
    dl[2] = ptr[2];
    dl += 3;
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_3_D1_SMALL(const mlib_u8 *src,
                                           mlib_u8       *dst,
                                           mlib_s32      xsize,
                                           const mlib_u8 **table)
{
  mlib_u8  *sp;              /* pointer to source data */
  mlib_u8  *dl;              /* pointer to start of destination */
  mlib_d64 *dp;              /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;       /* destination data */
  mlib_d64 t3, t4, t5;       /* destination data */
  mlib_d64 t6, t7;           /* destination data */
  mlib_d64 acc0, acc1, acc2; /* destination data */
  mlib_s32 i;                /* loop variable */
  const mlib_u8  *tab0 = table[0];
  const mlib_u8  *tab1 = table[1];
  const mlib_u8  *tab2 = table[2];
  mlib_u32 s00, s01, s02, s03;
  mlib_u32 s10, s11, s12, s13;

  sp   = (void *)src;
  dl   = dst;
  dp   = (mlib_d64 *) dl;

  vis_alignaddr((void *) 0, 7);

  i = 0;

  if (xsize >= 8) {

    s00 = sp[0];
    s01 = sp[1];
    s02 = sp[2];
    s03 = sp[3];
    s10 = sp[4];
    s11 = sp[5];
    s12 = sp[6];
    s13 = sp[7];
    sp += 8;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 16; i+=8, sp+=8) {
      t7 = VIS_LD_U8_I(tab1, s02);
      t6 = VIS_LD_U8_I(tab0, s02);
      t5 = VIS_LD_U8_I(tab2, s01);
      t4 = VIS_LD_U8_I(tab1, s01);
      t3 = VIS_LD_U8_I(tab0, s01);
      t2 = VIS_LD_U8_I(tab2, s00);
      t1 = VIS_LD_U8_I(tab1, s00);
      t0 = VIS_LD_U8_I(tab0, s00);
      acc0 = vis_faligndata(t7, acc0);
      acc0 = vis_faligndata(t6, acc0);
      acc0 = vis_faligndata(t5, acc0);
      acc0 = vis_faligndata(t4, acc0);
      acc0 = vis_faligndata(t3, acc0);
      acc0 = vis_faligndata(t2, acc0);
      acc0 = vis_faligndata(t1, acc0);
      acc0 = vis_faligndata(t0, acc0);
      t7 = VIS_LD_U8_I(tab0, s11);
      t6 = VIS_LD_U8_I(tab2, s10);
      t5 = VIS_LD_U8_I(tab1, s10);
      t4 = VIS_LD_U8_I(tab0, s10);
      t3 = VIS_LD_U8_I(tab2, s03);
      t2 = VIS_LD_U8_I(tab1, s03);
      t1 = VIS_LD_U8_I(tab0, s03);
      t0 = VIS_LD_U8_I(tab2, s02);
      acc1 = vis_faligndata(t7, acc1);
      acc1 = vis_faligndata(t6, acc1);
      acc1 = vis_faligndata(t5, acc1);
      acc1 = vis_faligndata(t4, acc1);
      acc1 = vis_faligndata(t3, acc1);
      acc1 = vis_faligndata(t2, acc1);
      acc1 = vis_faligndata(t1, acc1);
      acc1 = vis_faligndata(t0, acc1);
      t7 = VIS_LD_U8_I(tab2, s13);
      t6 = VIS_LD_U8_I(tab1, s13);
      t5 = VIS_LD_U8_I(tab0, s13);
      t4 = VIS_LD_U8_I(tab2, s12);
      t3 = VIS_LD_U8_I(tab1, s12);
      t2 = VIS_LD_U8_I(tab0, s12);
      t1 = VIS_LD_U8_I(tab2, s11);
      t0 = VIS_LD_U8_I(tab1, s11);
      acc2 = vis_faligndata(t7, acc2);
      acc2 = vis_faligndata(t6, acc2);
      acc2 = vis_faligndata(t5, acc2);
      acc2 = vis_faligndata(t4, acc2);
      acc2 = vis_faligndata(t3, acc2);
      acc2 = vis_faligndata(t2, acc2);
      acc2 = vis_faligndata(t1, acc2);
      acc2 = vis_faligndata(t0, acc2);
      s00 = sp[0];
      s01 = sp[1];
      s02 = sp[2];
      s03 = sp[3];
      s10 = sp[4];
      s11 = sp[5];
      s12 = sp[6];
      s13 = sp[7];
      *dp++ = acc0;
      *dp++ = acc1;
      *dp++ = acc2;
    }

    t7 = VIS_LD_U8_I(tab1, s02);
    t6 = VIS_LD_U8_I(tab0, s02);
    t5 = VIS_LD_U8_I(tab2, s01);
    t4 = VIS_LD_U8_I(tab1, s01);
    t3 = VIS_LD_U8_I(tab0, s01);
    t2 = VIS_LD_U8_I(tab2, s00);
    t1 = VIS_LD_U8_I(tab1, s00);
    t0 = VIS_LD_U8_I(tab0, s00);
    acc0 = vis_faligndata(t7, acc0);
    acc0 = vis_faligndata(t6, acc0);
    acc0 = vis_faligndata(t5, acc0);
    acc0 = vis_faligndata(t4, acc0);
    acc0 = vis_faligndata(t3, acc0);
    acc0 = vis_faligndata(t2, acc0);
    acc0 = vis_faligndata(t1, acc0);
    acc0 = vis_faligndata(t0, acc0);
    t7 = VIS_LD_U8_I(tab0, s11);
    t6 = VIS_LD_U8_I(tab2, s10);
    t5 = VIS_LD_U8_I(tab1, s10);
    t4 = VIS_LD_U8_I(tab0, s10);
    t3 = VIS_LD_U8_I(tab2, s03);
    t2 = VIS_LD_U8_I(tab1, s03);
    t1 = VIS_LD_U8_I(tab0, s03);
    t0 = VIS_LD_U8_I(tab2, s02);
    acc1 = vis_faligndata(t7, acc1);
    acc1 = vis_faligndata(t6, acc1);
    acc1 = vis_faligndata(t5, acc1);
    acc1 = vis_faligndata(t4, acc1);
    acc1 = vis_faligndata(t3, acc1);
    acc1 = vis_faligndata(t2, acc1);
    acc1 = vis_faligndata(t1, acc1);
    acc1 = vis_faligndata(t0, acc1);
    t7 = VIS_LD_U8_I(tab2, s13);
    t6 = VIS_LD_U8_I(tab1, s13);
    t5 = VIS_LD_U8_I(tab0, s13);
    t4 = VIS_LD_U8_I(tab2, s12);
    t3 = VIS_LD_U8_I(tab1, s12);
    t2 = VIS_LD_U8_I(tab0, s12);
    t1 = VIS_LD_U8_I(tab2, s11);
    t0 = VIS_LD_U8_I(tab1, s11);
    acc2 = vis_faligndata(t7, acc2);
    acc2 = vis_faligndata(t6, acc2);
    acc2 = vis_faligndata(t5, acc2);
    acc2 = vis_faligndata(t4, acc2);
    acc2 = vis_faligndata(t3, acc2);
    acc2 = vis_faligndata(t2, acc2);
    acc2 = vis_faligndata(t1, acc2);
    acc2 = vis_faligndata(t0, acc2);
    *dp++ = acc0;
    *dp++ = acc1;
    *dp++ = acc2;
    i += 8;
  }

  dl = (mlib_u8*)dp;

#pragma pipeloop(0)
  for (; i < xsize; i++) {
    s00 = sp[0];
    dl[0] = tab0[s00];
    dl[1] = tab1[s00];
    dl[2] = tab2[s00];
    dl += 3; sp ++;
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_3(const mlib_u8 *src,
                                  mlib_s32      slb,
                                  mlib_u8       *dst,
                                  mlib_s32      dlb,
                                  mlib_s32      xsize,
                                  mlib_s32      ysize,
                                  const mlib_u8 **table)
{
  if ((xsize * ysize) < 650) {
    mlib_u8  *sl;
    mlib_u8  *dl;
    mlib_s32 i, j;
    const mlib_u8  *tab0 = table[0];
    const mlib_u8  *tab1 = table[1];
    const mlib_u8  *tab2 = table[2];

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j ++) {
      mlib_u8 *sp = sl;
      mlib_u8 *dp = dl;
      mlib_s32 off, s0, size = xsize;

      off = (mlib_addr)dp & 7;
      off = (off * 5) & 7;
      off = (off < size) ? off : size;

      for (i = 0; i < off; i++) {
        s0 = *sp++;
        *dp++ = tab0[s0];
        *dp++ = tab1[s0];
        *dp++ = tab2[s0];
        size--;
      }

      if (size > 0) {
        mlib_v_ImageLookUpSI_U8_U8_3_D1_SMALL(sp, dp, size, table);
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_u8 *) ((mlib_u8 *) dl + dlb);
    }

  } else {
    mlib_u8  *sl;
    mlib_u8  *dl;
    mlib_u32 tab[512];
    const mlib_u8  *tab0 = table[0];
    const mlib_u8  *tab1 = table[1];
    const mlib_u8  *tab2 = table[2];
    mlib_s32 i, j;
    mlib_u32 s0, s1, s2, s3;

    s0 = tab0[0];
    s1 = tab1[0];
    s2 = tab2[0];
    for (i = 1; i < 256; i++) {
      s3 = (s0 << 24) + (s1 << 16) + (s2 << 8);
      s0 = tab0[i];
      s1 = tab1[i];
      s2 = tab2[i];
      tab[2*i-2] = s3;
    }

    s3 = (s0 << 24) + (s1 << 16) + (s2 << 8);
    tab[510] = s3;

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j ++) {
      mlib_u8 *sp = sl;
      mlib_u8 *dp = dl;
      mlib_s32 off, size = xsize;
      mlib_u8  *ptr;

      off = ((mlib_addr)dp & 3);
      off = (off < size) ? off : size;

#pragma pipeloop(0)
      for (i = 0; i < off; i++) {
        ptr = (mlib_u8*)(tab + 2*sp[i]);
        dp[0] = ptr[0];
        dp[1] = ptr[1];
        dp[2] = ptr[2];
        dp += 3;
      }

      size -= off;
      sp += off;

      if (size > 0) {
        off = (mlib_addr)sp & 3;

        if (off == 0) {
          mlib_v_ImageLookUpSI_U8_U8_3_SrcOff0_D1(sp, dp, size, (mlib_d64*)tab);
        } else if (off == 1) {
          mlib_v_ImageLookUpSI_U8_U8_3_SrcOff1_D1(sp, dp, size, (mlib_d64*)tab);
        } else if (off == 2) {
          mlib_v_ImageLookUpSI_U8_U8_3_SrcOff2_D1(sp, dp, size, (mlib_d64*)tab);
        } else if (off == 3) {
          mlib_v_ImageLookUpSI_U8_U8_3_SrcOff3_D1(sp, dp, size, (mlib_d64*)tab);
        }
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_u8 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_4_SrcOff0_D1(const mlib_u8  *src,
                                             mlib_u8        *dst,
                                             mlib_s32       xsize,
                                             const mlib_f32 *table)
{
  mlib_u32 *sa;          /* aligned pointer to source data */
  mlib_u8  *sp;          /* pointer to source data */
  mlib_u32 s0;           /* source data */
  mlib_f32 *dp;          /* aligned pointer to destination */
  mlib_f32 acc0, acc1;   /* destination data */
  mlib_f32 acc2, acc3;   /* destination data */
  mlib_s32 i;            /* loop variable */
  mlib_u32 s00, s01, s02, s03;

  sa   = (mlib_u32*)src;
  dp   = (mlib_f32 *) dst;

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 22) & 0x3FC;
    s01 = (s0 >> 14) & 0x3FC;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp += 4) {
      s02 = (s0 >> 6) & 0x3FC;
      s03 = (s0 << 2) & 0x3FC;
      acc0 = *(mlib_f32*)((mlib_u8*)table + s00);
      acc1 = *(mlib_f32*)((mlib_u8*)table + s01);
      acc2 = *(mlib_f32*)((mlib_u8*)table + s02);
      acc3 = *(mlib_f32*)((mlib_u8*)table + s03);
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
    acc0 = *(mlib_f32*)((mlib_u8*)table + s00);
    acc1 = *(mlib_f32*)((mlib_u8*)table + s01);
    acc2 = *(mlib_f32*)((mlib_u8*)table + s02);
    acc3 = *(mlib_f32*)((mlib_u8*)table + s03);
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

  if ( i < xsize) *dp = table[sp[0]];
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_4_DstNonAl_D1(const mlib_u8  *src,
                                              mlib_u8        *dst,
                                              mlib_s32       xsize,
                                              const mlib_f32 *table)
{
  mlib_u32 *sa;              /* aligned pointer to source data */
  mlib_u8  *sp;              /* pointer to source data */
  mlib_u32 s0;               /* source data */
  mlib_u8  *dl;              /* pointer to start of destination */
  mlib_d64 *dp;              /* aligned pointer to destination */
  mlib_d64 acc0, acc1, acc2; /* destination data */
  mlib_s32 i;                /* loop variable */
  mlib_u8  *dend;            /* pointer to end of destination */
  mlib_s32 emask;            /* edge mask */
  mlib_s32 off;
  mlib_u32 s00, s01, s02, s03;

  sa   = (mlib_u32*)src;
  sp = (void *)src;
  dl = dst;
  dend = dl + (xsize << 2) - 1;
  dp   = (mlib_d64 *) ((mlib_addr) dl & (~7));
  off  = (mlib_addr) dp - (mlib_addr) dl;
  vis_alignaddr(dp, off);

  emask = vis_edge8(dl, dend);
  acc0 = vis_freg_pair(table[sp[0]], table[sp[1]]);
  vis_pst_8(vis_faligndata(acc0, acc0), dp++, emask);
  sp += 2;

  xsize -= 2;

  if (xsize >= 2) {
    acc1 = vis_freg_pair(table[sp[0]], table[sp[1]]);
    *dp++ = vis_faligndata(acc0, acc1);
    acc0 = acc1;
    sp += 2; xsize -= 2;
  }

  sa++;

  i = 0;

  if (xsize >= 4) {

    s0 = *sa++;
    s00 = (s0 >> 22) & 0x3FC;
    s01 = (s0 >> 14) & 0x3FC;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 8; i+=4, dp += 2) {
      s02 = (s0 >> 6) & 0x3FC;
      s03 = (s0 << 2) & 0x3FC;
      acc1 = vis_freg_pair(*(mlib_f32*)((mlib_u8*)table + s00),
                           *(mlib_f32*)((mlib_u8*)table + s01));
      acc2 = vis_freg_pair(*(mlib_f32*)((mlib_u8*)table + s02),
                           *(mlib_f32*)((mlib_u8*)table + s03));
      s0 = *sa++;
      s00 = (s0 >> 22) & 0x3FC;
      s01 = (s0 >> 14) & 0x3FC;
      dp[0] = vis_faligndata(acc0, acc1);
      dp[1] = vis_faligndata(acc1, acc2);
      acc0 = acc2;
    }

    s02 = (s0 >> 6) & 0x3FC;
    s03 = (s0 << 2) & 0x3FC;
    acc1 = vis_freg_pair(*(mlib_f32*)((mlib_u8*)table + s00),
                         *(mlib_f32*)((mlib_u8*)table + s01));
    acc2 = vis_freg_pair(*(mlib_f32*)((mlib_u8*)table + s02),
                         *(mlib_f32*)((mlib_u8*)table + s03));
    dp[0] = vis_faligndata(acc0, acc1);
    dp[1] = vis_faligndata(acc1, acc2);
    acc0 = acc2;
    sp = (mlib_u8*)sa;
    dp += 2;
    i += 4;
  }

  if ( i <= xsize - 2) {
    acc1 = vis_freg_pair(table[sp[0]], table[sp[1]]);
    *dp++ = vis_faligndata(acc0, acc1);
    acc0 = acc1;
    i+=2; sp += 2;
  }

  if ((mlib_addr) dp <= (mlib_addr) dend) {
    emask = vis_edge8(dp, dend);
    acc1 = vis_freg_pair(table[sp[0]], table[sp[1]]);
    vis_pst_8(vis_faligndata(acc0, acc1), dp++, emask);
  }

  if ((mlib_addr) dp <= (mlib_addr) dend) {
    emask = vis_edge8(dp, dend);
    vis_pst_8(vis_faligndata(acc1, acc1), dp++, emask);
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_4_DstOff0_D1_SMALL(const mlib_u8 *src,
                                                   mlib_u8       *dst,
                                                   mlib_s32      xsize,
                                                   const mlib_u8 **table)
{
  mlib_u8  *sp;              /* pointer to source data */
  mlib_u32 s0, s1;           /* source data */
  mlib_u8 *dl;               /* pointer to start of destination */
  mlib_d64 *dp;              /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;       /* destination data */
  mlib_d64 t3, t4, t5;       /* destination data */
  mlib_d64 t6, t7, acc;      /* destination data */
  mlib_s32 i;                /* loop variable */
  const mlib_u8  *tab0 = table[0];
  const mlib_u8  *tab1 = table[1];
  const mlib_u8  *tab2 = table[2];
  const mlib_u8  *tab3 = table[3];

  sp   = (void *)src;
  dl   = dst;
  dp   = (mlib_d64 *) dl;

  vis_alignaddr((void *) 0, 7);

  if (xsize >= 2) {

    s0 = sp[0];
    s1 = sp[1];
    sp += 2;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 4; i+=2, sp+=2) {
      t7 = VIS_LD_U8_I(tab3, s1);
      t6 = VIS_LD_U8_I(tab2, s1);
      t5 = VIS_LD_U8_I(tab1, s1);
      t4 = VIS_LD_U8_I(tab0, s1);
      t3 = VIS_LD_U8_I(tab3, s0);
      t2 = VIS_LD_U8_I(tab2, s0);
      t1 = VIS_LD_U8_I(tab1, s0);
      t0 = VIS_LD_U8_I(tab0, s0);
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
      *dp++ = acc;
    }

    t7 = VIS_LD_U8_I(tab3, s1);
    t6 = VIS_LD_U8_I(tab2, s1);
    t5 = VIS_LD_U8_I(tab1, s1);
    t4 = VIS_LD_U8_I(tab0, s1);
    t3 = VIS_LD_U8_I(tab3, s0);
    t2 = VIS_LD_U8_I(tab2, s0);
    t1 = VIS_LD_U8_I(tab1, s0);
    t0 = VIS_LD_U8_I(tab0, s0);
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

  if ((xsize & 1) != 0) {
    s0 = sp[0];
    t7 = VIS_LD_U8_I(tab3, s0);
    t6 = VIS_LD_U8_I(tab2, s0);
    t5 = VIS_LD_U8_I(tab1, s0);
    t4 = VIS_LD_U8_I(tab0, s0);
    acc = vis_faligndata(t7, acc);
    acc = vis_faligndata(t6, acc);
    acc = vis_faligndata(t5, acc);
    acc = vis_faligndata(t4, acc);
    *(mlib_f32*)dp = vis_read_hi(acc);
  }
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_4_DstOff1_D1_SMALL(const mlib_u8 *src,
                                                   mlib_u8       *dst,
                                                   mlib_s32      xsize,
                                                   const mlib_u8 **table)
{
  mlib_u8  *sp;              /* pointer to source data */
  mlib_u32 s0, s1, s2;       /* source data */
  mlib_u8  *dl;              /* pointer to start of destination */
  mlib_d64 *dp;              /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;       /* destination data */
  mlib_d64 t3, t4, t5;       /* destination data */
  mlib_d64 t6, t7, acc;      /* destination data */
  mlib_s32 i;                /* loop variable */
  const mlib_u8  *tab0 = table[0];
  const mlib_u8  *tab1 = table[1];
  const mlib_u8  *tab2 = table[2];
  const mlib_u8  *tab3 = table[3];

  sp   = (void *)src;
  dl   = dst;
  dp   = (mlib_d64 *) dl;

  vis_alignaddr((void *) 0, 7);

  s0 = *sp++;

  if (xsize >= 2) {

    s1 = sp[0];
    s2 = sp[1];
    sp += 2;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 4; i+=2, sp+=2) {
      t7 = VIS_LD_U8_I(tab0, s2);
      t6 = VIS_LD_U8_I(tab3, s1);
      t5 = VIS_LD_U8_I(tab2, s1);
      t4 = VIS_LD_U8_I(tab1, s1);
      t3 = VIS_LD_U8_I(tab0, s1);
      t2 = VIS_LD_U8_I(tab3, s0);
      t1 = VIS_LD_U8_I(tab2, s0);
      t0 = VIS_LD_U8_I(tab1, s0);
      acc = vis_faligndata(t7, acc);
      acc = vis_faligndata(t6, acc);
      acc = vis_faligndata(t5, acc);
      acc = vis_faligndata(t4, acc);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = s2;
      s1 = sp[0];
      s2 = sp[1];
      *dp++ = acc;
    }

    t7 = VIS_LD_U8_I(tab0, s2);
    t6 = VIS_LD_U8_I(tab3, s1);
    t5 = VIS_LD_U8_I(tab2, s1);
    t4 = VIS_LD_U8_I(tab1, s1);
    t3 = VIS_LD_U8_I(tab0, s1);
    t2 = VIS_LD_U8_I(tab3, s0);
    t1 = VIS_LD_U8_I(tab2, s0);
    t0 = VIS_LD_U8_I(tab1, s0);
    acc = vis_faligndata(t7, acc);
    acc = vis_faligndata(t6, acc);
    acc = vis_faligndata(t5, acc);
    acc = vis_faligndata(t4, acc);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    s0 = s2;
    *dp++ = acc;
  }

  dl = (mlib_u8*)dp;

  if ((xsize & 1) != 0) {
    s1 = sp[0];
    t7 = VIS_LD_U8_I(tab0, s1);
    t6 = VIS_LD_U8_I(tab3, s0);
    t5 = VIS_LD_U8_I(tab2, s0);
    t4 = VIS_LD_U8_I(tab1, s0);
    acc = vis_faligndata(t7, acc);
    acc = vis_faligndata(t6, acc);
    acc = vis_faligndata(t5, acc);
    acc = vis_faligndata(t4, acc);
    *(mlib_f32*)dl = vis_read_hi(acc);
    dl += 4;
    s0 = s1;
  }

  dl[0] = tab1[s0];
  dl[1] = tab2[s0];
  dl[2] = tab3[s0];
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_4_DstOff2_D1_SMALL(const mlib_u8 *src,
                                                   mlib_u8       *dst,
                                                   mlib_s32      xsize,
                                                   const mlib_u8 **table)
{
  mlib_u8  *sp;              /* pointer to source data */
  mlib_u32 s0, s1, s2;       /* source data */
  mlib_u8  *dl;              /* pointer to start of destination */
  mlib_d64 *dp;              /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;       /* destination data */
  mlib_d64 t3, t4, t5;       /* destination data */
  mlib_d64 t6, t7, acc;      /* destination data */
  mlib_s32 i;                /* loop variable */
  const mlib_u8  *tab0 = table[0];
  const mlib_u8  *tab1 = table[1];
  const mlib_u8  *tab2 = table[2];
  const mlib_u8  *tab3 = table[3];

  sp   = (void *)src;
  dl   = dst;
  dp   = (mlib_d64 *) dl;

  vis_alignaddr((void *) 0, 7);

  s0 = *sp++;

  if (xsize >= 2) {

    s1 = sp[0];
    s2 = sp[1];
    sp += 2;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 4; i+=2, sp+=2) {
      t7 = VIS_LD_U8_I(tab1, s2);
      t6 = VIS_LD_U8_I(tab0, s2);
      t5 = VIS_LD_U8_I(tab3, s1);
      t4 = VIS_LD_U8_I(tab2, s1);
      t3 = VIS_LD_U8_I(tab1, s1);
      t2 = VIS_LD_U8_I(tab0, s1);
      t1 = VIS_LD_U8_I(tab3, s0);
      t0 = VIS_LD_U8_I(tab2, s0);
      acc = vis_faligndata(t7, acc);
      acc = vis_faligndata(t6, acc);
      acc = vis_faligndata(t5, acc);
      acc = vis_faligndata(t4, acc);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = s2;
      s1 = sp[0];
      s2 = sp[1];
      *dp++ = acc;
    }

    t7 = VIS_LD_U8_I(tab1, s2);
    t6 = VIS_LD_U8_I(tab0, s2);
    t5 = VIS_LD_U8_I(tab3, s1);
    t4 = VIS_LD_U8_I(tab2, s1);
    t3 = VIS_LD_U8_I(tab1, s1);
    t2 = VIS_LD_U8_I(tab0, s1);
    t1 = VIS_LD_U8_I(tab3, s0);
    t0 = VIS_LD_U8_I(tab2, s0);
    acc = vis_faligndata(t7, acc);
    acc = vis_faligndata(t6, acc);
    acc = vis_faligndata(t5, acc);
    acc = vis_faligndata(t4, acc);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    s0 = s2;
    *dp++ = acc;
  }

  dl = (mlib_u8*)dp;

  if ((xsize & 1) != 0) {
    s1 = sp[0];
    t7 = VIS_LD_U8_I(tab1, s1);
    t6 = VIS_LD_U8_I(tab0, s1);
    t5 = VIS_LD_U8_I(tab3, s0);
    t4 = VIS_LD_U8_I(tab2, s0);
    acc = vis_faligndata(t7, acc);
    acc = vis_faligndata(t6, acc);
    acc = vis_faligndata(t5, acc);
    acc = vis_faligndata(t4, acc);
    *(mlib_f32*)dl = vis_read_hi(acc);
    dl += 4;
    s0 = s1;
  }

  dl[0] = tab2[s0];
  dl[1] = tab3[s0];
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_4_DstOff3_D1_SMALL(const mlib_u8 *src,
                                                   mlib_u8       *dst,
                                                   mlib_s32      xsize,
                                                   const mlib_u8 **table)
{
  mlib_u8  *sp;              /* pointer to source data */
  mlib_u32 s0, s1, s2;       /* source data */
  mlib_u8 *dl;               /* pointer to start of destination */
  mlib_d64 *dp;              /* aligned pointer to destination */
  mlib_d64 t0, t1, t2;       /* destination data */
  mlib_d64 t3, t4, t5;       /* destination data */
  mlib_d64 t6, t7, acc;      /* destination data */
  mlib_s32 i;                /* loop variable */
  const mlib_u8  *tab0 = table[0];
  const mlib_u8  *tab1 = table[1];
  const mlib_u8  *tab2 = table[2];
  const mlib_u8  *tab3 = table[3];

  sp   = (void *)src;
  dl   = dst;
  dp   = (mlib_d64 *) dl;

  vis_alignaddr((void *) 0, 7);

  s0 = *sp++;

  if (xsize >= 2) {

    s1 = sp[0];
    s2 = sp[1];
    sp += 2;

#pragma pipeloop(0)
    for(i = 0; i <= xsize - 4; i+=2, sp+=2) {
      t7 = VIS_LD_U8_I(tab2, s2);
      t6 = VIS_LD_U8_I(tab1, s2);
      t5 = VIS_LD_U8_I(tab0, s2);
      t4 = VIS_LD_U8_I(tab3, s1);
      t3 = VIS_LD_U8_I(tab2, s1);
      t2 = VIS_LD_U8_I(tab1, s1);
      t1 = VIS_LD_U8_I(tab0, s1);
      t0 = VIS_LD_U8_I(tab3, s0);
      acc = vis_faligndata(t7, acc);
      acc = vis_faligndata(t6, acc);
      acc = vis_faligndata(t5, acc);
      acc = vis_faligndata(t4, acc);
      acc = vis_faligndata(t3, acc);
      acc = vis_faligndata(t2, acc);
      acc = vis_faligndata(t1, acc);
      acc = vis_faligndata(t0, acc);
      s0 = s2;
      s1 = sp[0];
      s2 = sp[1];
      *dp++ = acc;
    }

    t7 = VIS_LD_U8_I(tab2, s2);
    t6 = VIS_LD_U8_I(tab1, s2);
    t5 = VIS_LD_U8_I(tab0, s2);
    t4 = VIS_LD_U8_I(tab3, s1);
    t3 = VIS_LD_U8_I(tab2, s1);
    t2 = VIS_LD_U8_I(tab1, s1);
    t1 = VIS_LD_U8_I(tab0, s1);
    t0 = VIS_LD_U8_I(tab3, s0);
    acc = vis_faligndata(t7, acc);
    acc = vis_faligndata(t6, acc);
    acc = vis_faligndata(t5, acc);
    acc = vis_faligndata(t4, acc);
    acc = vis_faligndata(t3, acc);
    acc = vis_faligndata(t2, acc);
    acc = vis_faligndata(t1, acc);
    acc = vis_faligndata(t0, acc);
    s0 = s2;
    *dp++ = acc;
  }

  dl = (mlib_u8*)dp;

  if ((xsize & 1) != 0) {
    s1 = sp[0];
    t7 = VIS_LD_U8_I(tab2, s1);
    t6 = VIS_LD_U8_I(tab1, s1);
    t5 = VIS_LD_U8_I(tab0, s1);
    t4 = VIS_LD_U8_I(tab3, s0);
    acc = vis_faligndata(t7, acc);
    acc = vis_faligndata(t6, acc);
    acc = vis_faligndata(t5, acc);
    acc = vis_faligndata(t4, acc);
    *(mlib_f32*)dl = vis_read_hi(acc);
    dl += 4;
    s0 = s1;
  }

  dl[0] = tab3[s0];
}

/***************************************************************/
void mlib_v_ImageLookUpSI_U8_U8_4(const mlib_u8 *src,
                                  mlib_s32      slb,
                                  mlib_u8       *dst,
                                  mlib_s32      dlb,
                                  mlib_s32      xsize,
                                  mlib_s32      ysize,
                                  const mlib_u8 **table)
{
  if ((xsize * ysize) < 500) {
    mlib_u8  *sl;
    mlib_u8  *dl;
    mlib_s32 j;
    const mlib_u8  *tab0 = table[0];
    const mlib_u8  *tab1 = table[1];
    const mlib_u8  *tab2 = table[2];
    const mlib_u8  *tab3 = table[3];

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j ++) {
      mlib_u8 *sp = sl;
      mlib_u8 *dp = dl;
      mlib_s32 off, s0, size = xsize;

      off =  (8 - ((mlib_addr)dp & 7)) & 7;

      if ((off >= 4) && (size > 0)) {
        s0 = *sp++;
        *dp++ = tab0[s0];
        *dp++ = tab1[s0];
        *dp++ = tab2[s0];
        *dp++ = tab3[s0];
        size--;
      }

      if (size > 0) {
        off =  (4 - ((mlib_addr)dp & 3)) & 3;

        if (off == 0) {
          mlib_v_ImageLookUpSI_U8_U8_4_DstOff0_D1_SMALL(sp, dp, size, table);
        } else if (off == 1) {
          s0 = *sp;
          *dp++ = tab0[s0];
          size--;
          mlib_v_ImageLookUpSI_U8_U8_4_DstOff1_D1_SMALL(sp, dp, size, table);
        } else if (off == 2) {
          s0 = *sp;
          *dp++ = tab0[s0];
          *dp++ = tab1[s0];
          size--;
          mlib_v_ImageLookUpSI_U8_U8_4_DstOff2_D1_SMALL(sp, dp, size, table);
        } else if (off == 3) {
          s0 = *sp;
          *dp++ = tab0[s0];
          *dp++ = tab1[s0];
          *dp++ = tab2[s0];
          size--;
          mlib_v_ImageLookUpSI_U8_U8_4_DstOff3_D1_SMALL(sp, dp, size, table);
        }
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_u8 *) ((mlib_u8 *) dl + dlb);
    }

  } else {
    mlib_u8  *sl;
    mlib_u8  *dl;
    mlib_u32 tab[256];
    const mlib_u8  *tab0 = table[0];
    const mlib_u8  *tab1 = table[1];
    const mlib_u8  *tab2 = table[2];
    const mlib_u8  *tab3 = table[3];
    mlib_s32 i, j;
    mlib_u32 s0, s1, s2, s3, s4;

    s0 = tab0[0];
    s1 = tab1[0];
    s2 = tab2[0];
    s3 = tab3[0];
    for (i = 1; i < 256; i++) {
      s4 = (s0 << 24) + (s1 << 16) + (s2 << 8) + s3;
      s0 = tab0[i];
      s1 = tab1[i];
      s2 = tab2[i];
      s3 = tab3[i];
      tab[i-1] = s4;
    }

    s4 = (s0 << 24) + (s1 << 16) + (s2 << 8) + s3;
    tab[255] = s4;

    sl = (void *)src;
    dl = dst;

    /* row loop */
    for (j = 0; j < ysize; j ++) {
      mlib_u8 *sp = sl;
      mlib_u8 *dp = dl;
      mlib_s32 off, size = xsize;

      if (((mlib_addr)dp & 3) == 0) {
        off = (4 - (mlib_addr)sp & 3) & 3;

        off = (off < size) ? off : size;

#pragma pipeloop(0)
        for (i = 0; i < off; i++) {
          *(mlib_u32*)dp = tab[(*sp)];
          dp += 4; sp++;
        }

        size -= off;

        if (size > 0) {
          mlib_v_ImageLookUpSI_U8_U8_4_SrcOff0_D1(sp, dp, size, (mlib_f32*)tab);
        }

      } else {

        off = ((4 - ((mlib_addr)sp & 3)) & 3);
        off = (off < size) ? off : size;

        for (i = 0; i < off; i++) {
          s0 = tab[(*sp)];
          *dp++ = (s0 >> 24);
          *dp++ = (s0 >> 16);
          *dp++ = (s0 >> 8);
          *dp++ = s0;
          size--; sp++;
        }

        if (size > 0) {
          mlib_v_ImageLookUpSI_U8_U8_4_DstNonAl_D1(sp, dp, size, (mlib_f32*)tab);
        }
      }

      sl = (mlib_u8 *) ((mlib_u8 *) sl + slb);
      dl = (mlib_u8 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
