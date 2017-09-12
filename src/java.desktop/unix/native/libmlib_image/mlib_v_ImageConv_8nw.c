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
 */

#include "vis_proto.h"
#include "mlib_image.h"
#include "mlib_ImageCheck.h"
#include "mlib_ImageCopy.h"
#include "mlib_ImageConv.h"
#include "mlib_v_ImageConv.h"

/***************************************************************/
#define DTYPE mlib_u8

/***************************************************************/
#define NCHAN  nchan

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
#define DEF_EXTRA_VARS                                          \
  mlib_s32 nchan = mlib_ImageGetChannels(dst)

/***************************************************************/
static const mlib_s32 mlib_round_8[16] = {
  0x00400040, 0x00200020, 0x00100010, 0x00080008,
  0x00040004, 0x00020002, 0x00010001, 0x00000000,
  0x00000000, 0x00000000, 0x00000000, 0x00000000,
  0x00000000, 0x00000000, 0x00000000, 0x00000000
};

/***************************************************************/
#define MAX_N   11

mlib_status mlib_convMxN_8nw_f(mlib_image       *dst,
                               const mlib_image *src,
                               mlib_s32         m,
                               mlib_s32         n,
                               mlib_s32         dm,
                               mlib_s32         dn,
                               const mlib_s32   *kern,
                               mlib_s32         scale)
{
  mlib_d64 *buffs_local[3 * (MAX_N + 1)], **buffs = buffs_local, **buff;
  mlib_d64 *buff0, *buff1, *buff2, *buff3, *buffn, *buffd, *buffe;
  mlib_d64 s00, s01, s10, s11, s20, s21, s30, s31, s0, s1, s2, s3;
  mlib_d64 d00, d01, d10, d11, d20, d21, d30, d31;
  mlib_d64 dd, d0, d1;
  mlib_s32 ik, jk, ik_last, jk_size, coff, off, doff;
  DEF_VARS;
  DEF_EXTRA_VARS;

  if (n > MAX_N) {
    buffs = mlib_malloc(3 * (n + 1) * sizeof(mlib_d64 *));

    if (buffs == NULL)
      return MLIB_FAILURE;
  }

  buff = buffs + 2 * (n + 1);

  sl = adr_src;
  dl = adr_dst + dn * dll + dm * NCHAN;

  ssize = NCHAN * wid;
  dsize = (ssize + 7) / 8;
  esize = dsize + 4;
  pbuff = mlib_malloc((n + 4) * esize * sizeof(mlib_d64));

  if (pbuff == NULL) {
    if (buffs != buffs_local)
      mlib_free(buffs);
    return MLIB_FAILURE;
  }

  for (i = 0; i < (n + 1); i++)
    buffs[i] = pbuff + i * esize;
  for (i = 0; i < (n + 1); i++)
    buffs[(n + 1) + i] = buffs[i];
  buffd = buffs[n] + esize;
  buffe = buffd + 2 * esize;

  wid -= (m - 1);
  hgt -= (n - 1);
  xsize = ssize - NCHAN * (m - 1);
  emask = (0xFF00 >> (xsize & 7)) & 0xFF;

  vis_write_gsr(gsr_scale + 7);

  for (l = 0; l < n; l++) {
    mlib_d64 *buffn = buffs[l];
    sp = sl + l * sll;

    if ((mlib_addr) sp & 7)
      mlib_ImageCopy_na((void *)sp, (void *)buffn, ssize);
  }

  /* init buffer */
#pragma pipeloop(0)
  for (i = 0; i < (xsize + 7) / 8; i++) {
    buffd[2 * i] = drnd;
    buffd[2 * i + 1] = drnd;
  }

  for (j = 0; j < hgt; j++) {
    mlib_d64 **buffc = buffs + buff_ind;
    mlib_f32 *pk = karr, k0, k1, k2, k3;
    sp = sl + n * sll;

    for (l = 0; l < n; l++) {
      buff[l] = buffc[l];
    }

    buffn = buffc[n];

    for (l = 0; l < n; l++) {
      if ((((mlib_addr) (sl + l * sll)) & 7) == 0)
        buff[l] = (mlib_d64 *) (sl + l * sll);
    }

    if ((mlib_addr) sp & 7)
      mlib_ImageCopy_na((void *)sp, (void *)buffn, ssize);

    ik_last = (m - 1);

    for (jk = 0; jk < n; jk += jk_size) {
      jk_size = n - jk;

      if (jk_size >= 6)
        jk_size = 4;

      if (jk_size == 5)
        jk_size = 3;

      coff = 0;

      if (jk_size == 1) {

        for (ik = 0; ik < m; ik++, coff += NCHAN) {
          if (!jk && ik == ik_last)
            continue;

          k0 = pk[ik];

          doff = coff / 8;
          buff0 = buff[jk] + doff;

          off = coff & 7;
          vis_write_gsr(gsr_scale + off);

          s01 = buff0[0];
#pragma pipeloop(0)
          for (i = 0; i < (xsize + 7) / 8; i++) {
            s00 = s01;
            s01 = buff0[i + 1];
            s0 = vis_faligndata(s00, s01);

            d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
            d01 = vis_fmul8x16au(vis_read_lo(s0), k0);

            d0 = buffd[2 * i];
            d1 = buffd[2 * i + 1];
            d0 = vis_fpadd16(d00, d0);
            d1 = vis_fpadd16(d01, d1);
            buffd[2 * i] = d0;
            buffd[2 * i + 1] = d1;
          }
        }

        pk += m;
      }
      else if (jk_size == 2) {

        for (ik = 0; ik < m; ik++, coff += NCHAN) {
          if (!jk && ik == ik_last)
            continue;

          k0 = pk[ik];
          k1 = pk[ik + m];

          doff = coff / 8;
          buff0 = buff[jk] + doff;
          buff1 = buff[jk + 1] + doff;

          off = coff & 7;
          vis_write_gsr(gsr_scale + off);

          s01 = buff0[0];
          s11 = buff1[0];
#pragma pipeloop(0)
          for (i = 0; i < (xsize + 7) / 8; i++) {
            s00 = s01;
            s10 = s11;
            s01 = buff0[i + 1];
            s11 = buff1[i + 1];
            s0 = vis_faligndata(s00, s01);
            s1 = vis_faligndata(s10, s11);

            d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
            d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
            d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
            d11 = vis_fmul8x16au(vis_read_lo(s1), k1);

            d0 = buffd[2 * i];
            d1 = buffd[2 * i + 1];
            d0 = vis_fpadd16(d00, d0);
            d0 = vis_fpadd16(d10, d0);
            d1 = vis_fpadd16(d01, d1);
            d1 = vis_fpadd16(d11, d1);
            buffd[2 * i] = d0;
            buffd[2 * i + 1] = d1;
          }
        }

        pk += 2 * m;
      }
      else if (jk_size == 3) {

        for (ik = 0; ik < m; ik++, coff += NCHAN) {
          if (!jk && ik == ik_last)
            continue;

          k0 = pk[ik];
          k1 = pk[ik + m];
          k2 = pk[ik + 2 * m];

          doff = coff / 8;
          buff0 = buff[jk] + doff;
          buff1 = buff[jk + 1] + doff;
          buff2 = buff[jk + 2] + doff;

          off = coff & 7;
          vis_write_gsr(gsr_scale + off);

          if (off == 0) {
#pragma pipeloop(0)
            for (i = 0; i < (xsize + 7) / 8; i++) {
              d0 = buffd[2 * i];
              d1 = buffd[2 * i + 1];

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
              d0 = vis_fpadd16(d20, d0);
              d0 = vis_fpadd16(d00, d0);
              d01 = vis_fpadd16(d01, d11);
              d1 = vis_fpadd16(d21, d1);
              d1 = vis_fpadd16(d01, d1);
              buffd[2 * i] = d0;
              buffd[2 * i + 1] = d1;
            }
          }
          else if (off == 4) {
            s01 = buff0[0];
            s11 = buff1[0];
            s21 = buff2[0];
#pragma pipeloop(0)
            for (i = 0; i < (xsize + 7) / 8; i++) {
              d0 = buffd[2 * i];
              d1 = buffd[2 * i + 1];

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
              d0 = vis_fpadd16(d20, d0);
              d0 = vis_fpadd16(d00, d0);
              d01 = vis_fpadd16(d01, d11);
              d1 = vis_fpadd16(d21, d1);
              d1 = vis_fpadd16(d01, d1);
              buffd[2 * i] = d0;
              buffd[2 * i + 1] = d1;
            }
          }
          else {
            s01 = buff0[0];
            s11 = buff1[0];
            s21 = buff2[0];
#pragma pipeloop(0)
            for (i = 0; i < (xsize + 7) / 8; i++) {
              d0 = buffd[2 * i];
              d1 = buffd[2 * i + 1];

              s00 = s01;
              s10 = s11;
              s20 = s21;
              s01 = buff0[i + 1];
              s11 = buff1[i + 1];
              s21 = buff2[i + 1];
              s0 = vis_faligndata(s00, s01);
              s1 = vis_faligndata(s10, s11);
              s2 = vis_faligndata(s20, s21);

              d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
              d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
              d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
              d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
              d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
              d21 = vis_fmul8x16au(vis_read_lo(s2), k2);

              d00 = vis_fpadd16(d00, d10);
              d0 = vis_fpadd16(d20, d0);
              d0 = vis_fpadd16(d00, d0);
              d01 = vis_fpadd16(d01, d11);
              d1 = vis_fpadd16(d21, d1);
              d1 = vis_fpadd16(d01, d1);
              buffd[2 * i] = d0;
              buffd[2 * i + 1] = d1;
            }
          }
        }

        pk += 3 * m;
      }
      else {                                /* jk_size == 4 */

        for (ik = 0; ik < m; ik++, coff += NCHAN) {
          if (!jk && ik == ik_last)
            continue;

          k0 = pk[ik];
          k1 = pk[ik + m];
          k2 = pk[ik + 2 * m];
          k3 = pk[ik + 3 * m];

          doff = coff / 8;
          buff0 = buff[jk] + doff;
          buff1 = buff[jk + 1] + doff;
          buff2 = buff[jk + 2] + doff;
          buff3 = buff[jk + 3] + doff;

          off = coff & 7;
          vis_write_gsr(gsr_scale + off);

          if (off == 0) {

#pragma pipeloop(0)
            for (i = 0; i < (xsize + 7) / 8; i++) {
              d0 = buffd[2 * i];
              d1 = buffd[2 * i + 1];

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
              d0 = vis_fpadd16(d0, d00);
              d0 = vis_fpadd16(d0, d20);
              d01 = vis_fpadd16(d01, d11);
              d21 = vis_fpadd16(d21, d31);
              d1 = vis_fpadd16(d1, d01);
              d1 = vis_fpadd16(d1, d21);
              buffd[2 * i] = d0;
              buffd[2 * i + 1] = d1;
            }
          }
          else if (off == 4) {

            s01 = buff0[0];
            s11 = buff1[0];
            s21 = buff2[0];
            s31 = buff3[0];
#pragma pipeloop(0)
            for (i = 0; i < (xsize + 7) / 8; i++) {
              d0 = buffd[2 * i];
              d1 = buffd[2 * i + 1];

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
              d0 = vis_fpadd16(d0, d00);
              d0 = vis_fpadd16(d0, d20);
              d01 = vis_fpadd16(d01, d11);
              d21 = vis_fpadd16(d21, d31);
              d1 = vis_fpadd16(d1, d01);
              d1 = vis_fpadd16(d1, d21);
              buffd[2 * i] = d0;
              buffd[2 * i + 1] = d1;
            }
          }
          else {

            s01 = buff0[0];
            s11 = buff1[0];
            s21 = buff2[0];
            s31 = buff3[0];
#pragma pipeloop(0)
            for (i = 0; i < (xsize + 7) / 8; i++) {
              d0 = buffd[2 * i];
              d1 = buffd[2 * i + 1];

              s00 = s01;
              s10 = s11;
              s20 = s21;
              s30 = s31;
              s01 = buff0[i + 1];
              s11 = buff1[i + 1];
              s21 = buff2[i + 1];
              s31 = buff3[i + 1];
              s0 = vis_faligndata(s00, s01);
              s1 = vis_faligndata(s10, s11);
              s2 = vis_faligndata(s20, s21);
              s3 = vis_faligndata(s30, s31);

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
              d0 = vis_fpadd16(d0, d00);
              d0 = vis_fpadd16(d0, d20);
              d01 = vis_fpadd16(d01, d11);
              d21 = vis_fpadd16(d21, d31);
              d1 = vis_fpadd16(d1, d01);
              d1 = vis_fpadd16(d1, d21);
              buffd[2 * i] = d0;
              buffd[2 * i + 1] = d1;
            }
          }
        }

        pk += 4 * m;
      }
    }

    /*****************************************
     *****************************************
     **          Final iteration            **
     *****************************************
     *****************************************/

    jk_size = n;

    if (jk_size >= 6)
      jk_size = 4;

    if (jk_size == 5)
      jk_size = 3;

    k0 = karr[ik_last];
    k1 = karr[ik_last + m];
    k2 = karr[ik_last + 2 * m];
    k3 = karr[ik_last + 3 * m];

    off = ik_last * NCHAN;
    doff = off / 8;
    off &= 7;
    buff0 = buff[0] + doff;
    buff1 = buff[1] + doff;
    buff2 = buff[2] + doff;
    buff3 = buff[3] + doff;
    vis_write_gsr(gsr_scale + off);

    if (jk_size == 1) {
      dp = ((mlib_addr) dl & 7) ? buffe : (mlib_d64 *) dl;

      s01 = buff0[0];
#pragma pipeloop(0)
      for (i = 0; i < xsize / 8; i++) {
        s00 = s01;
        s01 = buff0[i + 1];
        s0 = vis_faligndata(s00, s01);

        d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s0), k0);

        d0 = buffd[2 * i];
        d1 = buffd[2 * i + 1];
        d0 = vis_fpadd16(d0, d00);
        d1 = vis_fpadd16(d1, d01);

        dd = vis_fpack16_pair(d0, d1);
        dp[i] = dd;

        buffd[2 * i] = drnd;
        buffd[2 * i + 1] = drnd;
      }

      if (emask) {
        s00 = s01;
        s01 = buff0[i + 1];
        s0 = vis_faligndata(s00, s01);

        d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s0), k0);

        d0 = buffd[2 * i];
        d1 = buffd[2 * i + 1];
        d0 = vis_fpadd16(d0, d00);
        d1 = vis_fpadd16(d1, d01);

        dd = vis_fpack16_pair(d0, d1);
        vis_pst_8(dd, dp + i, emask);

        buffd[2 * i] = drnd;
        buffd[2 * i + 1] = drnd;
      }

      if ((mlib_u8 *) dp != dl)
        mlib_ImageCopy_na((void *)buffe, dl, xsize);
    }
    else if (jk_size == 2) {
      dp = ((mlib_addr) dl & 7) ? buffe : (mlib_d64 *) dl;

      s01 = buff0[0];
      s11 = buff1[0];
#pragma pipeloop(0)
      for (i = 0; i < xsize / 8; i++) {
        s00 = s01;
        s10 = s11;
        s01 = buff0[i + 1];
        s11 = buff1[i + 1];
        s0 = vis_faligndata(s00, s01);
        s1 = vis_faligndata(s10, s11);

        d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
        d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
        d11 = vis_fmul8x16au(vis_read_lo(s1), k1);

        d0 = buffd[2 * i];
        d1 = buffd[2 * i + 1];
        d0 = vis_fpadd16(d0, d00);
        d0 = vis_fpadd16(d0, d10);
        d1 = vis_fpadd16(d1, d01);
        d1 = vis_fpadd16(d1, d11);

        dd = vis_fpack16_pair(d0, d1);
        dp[i] = dd;

        buffd[2 * i] = drnd;
        buffd[2 * i + 1] = drnd;
      }

      if (emask) {
        s00 = s01;
        s10 = s11;
        s01 = buff0[i + 1];
        s11 = buff1[i + 1];
        s0 = vis_faligndata(s00, s01);
        s1 = vis_faligndata(s10, s11);

        d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
        d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
        d11 = vis_fmul8x16au(vis_read_lo(s1), k1);

        d0 = buffd[2 * i];
        d1 = buffd[2 * i + 1];
        d0 = vis_fpadd16(d0, d00);
        d0 = vis_fpadd16(d0, d10);
        d1 = vis_fpadd16(d1, d01);
        d1 = vis_fpadd16(d1, d11);

        dd = vis_fpack16_pair(d0, d1);
        vis_pst_8(dd, dp + i, emask);

        buffd[2 * i] = drnd;
        buffd[2 * i + 1] = drnd;
      }

      if ((mlib_u8 *) dp != dl)
        mlib_ImageCopy_na((void *)buffe, dl, xsize);
    }
    else if (jk_size == 3) {

      dp = ((mlib_addr) dl & 7) ? buffe : (mlib_d64 *) dl;

      s01 = buff0[0];
      s11 = buff1[0];
      s21 = buff2[0];
#pragma pipeloop(0)
      for (i = 0; i < xsize / 8; i++) {
        s00 = s01;
        s10 = s11;
        s20 = s21;
        s01 = buff0[i + 1];
        s11 = buff1[i + 1];
        s21 = buff2[i + 1];
        s0 = vis_faligndata(s00, s01);
        s1 = vis_faligndata(s10, s11);
        s2 = vis_faligndata(s20, s21);

        d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
        d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
        d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
        d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
        d21 = vis_fmul8x16au(vis_read_lo(s2), k2);

        d0 = buffd[2 * i];
        d1 = buffd[2 * i + 1];
        d0 = vis_fpadd16(d0, d00);
        d0 = vis_fpadd16(d0, d10);
        d0 = vis_fpadd16(d0, d20);
        d1 = vis_fpadd16(d1, d01);
        d1 = vis_fpadd16(d1, d11);
        d1 = vis_fpadd16(d1, d21);

        dd = vis_fpack16_pair(d0, d1);
        dp[i] = dd;

        buffd[2 * i] = drnd;
        buffd[2 * i + 1] = drnd;
      }

      if (emask) {
        s00 = s01;
        s10 = s11;
        s20 = s21;
        s01 = buff0[i + 1];
        s11 = buff1[i + 1];
        s21 = buff2[i + 1];
        s0 = vis_faligndata(s00, s01);
        s1 = vis_faligndata(s10, s11);
        s2 = vis_faligndata(s20, s21);

        d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
        d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
        d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
        d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
        d21 = vis_fmul8x16au(vis_read_lo(s2), k2);

        d0 = buffd[2 * i];
        d1 = buffd[2 * i + 1];
        d0 = vis_fpadd16(d0, d00);
        d0 = vis_fpadd16(d0, d10);
        d0 = vis_fpadd16(d0, d20);
        d1 = vis_fpadd16(d1, d01);
        d1 = vis_fpadd16(d1, d11);
        d1 = vis_fpadd16(d1, d21);

        dd = vis_fpack16_pair(d0, d1);
        vis_pst_8(dd, dp + i, emask);

        buffd[2 * i] = drnd;
        buffd[2 * i + 1] = drnd;
      }

      if ((mlib_u8 *) dp != dl)
        mlib_ImageCopy_na((void *)buffe, dl, xsize);
    }
    else {                                  /* if (jk_size == 4) */

      dp = ((mlib_addr) dl & 7) ? buffe : (mlib_d64 *) dl;

      s01 = buff0[0];
      s11 = buff1[0];
      s21 = buff2[0];
      s31 = buff3[0];
#pragma pipeloop(0)
      for (i = 0; i < xsize / 8; i++) {
        s00 = s01;
        s10 = s11;
        s20 = s21;
        s30 = s31;
        s01 = buff0[i + 1];
        s11 = buff1[i + 1];
        s21 = buff2[i + 1];
        s31 = buff3[i + 1];
        s0 = vis_faligndata(s00, s01);
        s1 = vis_faligndata(s10, s11);
        s2 = vis_faligndata(s20, s21);
        s3 = vis_faligndata(s30, s31);

        d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
        d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
        d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
        d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
        d21 = vis_fmul8x16au(vis_read_lo(s2), k2);
        d30 = vis_fmul8x16au(vis_read_hi(s3), k3);
        d31 = vis_fmul8x16au(vis_read_lo(s3), k3);

        d0 = buffd[2 * i];
        d1 = buffd[2 * i + 1];
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

        buffd[2 * i] = drnd;
        buffd[2 * i + 1] = drnd;
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
        s0 = vis_faligndata(s00, s01);
        s1 = vis_faligndata(s10, s11);
        s2 = vis_faligndata(s20, s21);
        s3 = vis_faligndata(s30, s31);

        d00 = vis_fmul8x16au(vis_read_hi(s0), k0);
        d01 = vis_fmul8x16au(vis_read_lo(s0), k0);
        d10 = vis_fmul8x16au(vis_read_hi(s1), k1);
        d11 = vis_fmul8x16au(vis_read_lo(s1), k1);
        d20 = vis_fmul8x16au(vis_read_hi(s2), k2);
        d21 = vis_fmul8x16au(vis_read_lo(s2), k2);
        d30 = vis_fmul8x16au(vis_read_hi(s3), k3);
        d31 = vis_fmul8x16au(vis_read_lo(s3), k3);

        d0 = buffd[2 * i];
        d1 = buffd[2 * i + 1];
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

        buffd[2 * i] = drnd;
        buffd[2 * i + 1] = drnd;
      }

      if ((mlib_u8 *) dp != dl)
        mlib_ImageCopy_na((void *)buffe, dl, xsize);
    }

    sl += sll;
    dl += dll;

    buff_ind++;

    if (buff_ind >= (n + 1))
      buff_ind = 0;
  }

  mlib_free(pbuff);

  if (buffs != buffs_local)
    mlib_free(buffs);

  return MLIB_SUCCESS;
}

/***************************************************************/
