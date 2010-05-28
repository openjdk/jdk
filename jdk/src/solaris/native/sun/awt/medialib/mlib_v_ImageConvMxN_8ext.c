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



/*
 * FUNCTION
 *      mlib_v_convMxN_8ext - convolve a 8-bit image, MxN kernel,
 *                            edge = src extended
 *
 * SYNOPSIS
 *      mlib_status mlib_v_convMxNext_u8(mlib_image       *dst,
 *                                       cosmt mlib_image *dst,
 *                                       mlib_s32         kwid,
 *                                       mlib_s32         khgt,
 *                                       mlib_s32         dx_l,
 *                                       mlib_s32         dx_r,
 *                                       mlib_s32         dy_t,
 *                                       mlib_s32         dy_b,
 *                                       const mlib_s32   *skernel,
 *                                       mlib_s32         discardbits,
 *                                       mlib_s32         cmask)
 *
 * ARGUMENT
 *      src       Ptr to source image structure
 *      dst       Ptr to destination image structure
 *      khgt         Kernel height (# of rows)
 *      kwid         Kernel width (# of cols)
 *      skernel      Ptr to convolution kernel
 *      discardbits  The number of LSBits of the 32-bit accumulator that
 *                   are discarded when the 32-bit accumulator is converted
 *                   to 16-bit output data; discardbits must be 1-15 (it
 *                   cannot be zero). Same as exponent N for scalefac=2**N.
 *      cmask        Channel mask to indicate the channels to be convolved.
 *                   Each bit of which represents a channel in the image. The
 *                   channels corresponded to 1 bits are those to be processed.
 *
 * DESCRIPTION
 *      A 2-D convolution (MxN kernel) for 8-bit images.
 *
 */

#include "vis_proto.h"
#include "mlib_image.h"
#include "mlib_ImageCopy.h"
#include "mlib_ImageConv.h"
#include "mlib_c_ImageConv.h"
#include "mlib_v_ImageChannelExtract.h"
#include "mlib_v_ImageChannelInsert.h"

/***************************************************************/
static mlib_status mlib_convMxN_8ext_f(mlib_image       *dst,
                                       const mlib_image *src,
                                       mlib_s32         m,
                                       mlib_s32         n,
                                       mlib_s32         dx_l,
                                       mlib_s32         dx_r,
                                       mlib_s32         dy_t,
                                       mlib_s32         dy_b,
                                       const mlib_s32   *kern,
                                       mlib_s32         scale);

static mlib_status mlib_convMxN_8ext_mask(mlib_image       *dst,
                                          const mlib_image *src,
                                          mlib_s32         m,
                                          mlib_s32         n,
                                          mlib_s32         dx_l,
                                          mlib_s32         dx_r,
                                          mlib_s32         dy_t,
                                          mlib_s32         dy_b,
                                          const mlib_s32   *kern,
                                          mlib_s32         scale,
                                          mlib_s32         cmask);

/***************************************************************/
static mlib_s32 mlib_round_8[16] = {
  0x00400040, 0x00200020, 0x00100010, 0x00080008,
  0x00040004, 0x00020002, 0x00010001, 0x00000000,
  0x00000000, 0x00000000, 0x00000000, 0x00000000,
  0x00000000, 0x00000000, 0x00000000, 0x00000000
};

/***************************************************************/
mlib_status mlib_convMxNext_u8(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kernel,
                               mlib_s32         kwid,
                               mlib_s32         khgt,
                               mlib_s32         dx_l,
                               mlib_s32         dx_r,
                               mlib_s32         dy_t,
                               mlib_s32         dy_b,
                               mlib_s32         discardbits,
                               mlib_s32         cmask)
{
  mlib_s32 nchannel, amask;

  if (mlib_ImageConvVersion(kwid, khgt, discardbits, MLIB_BYTE) == 0)
    return mlib_c_convMxNext_u8(dst, src, kernel, kwid, khgt,
                                dx_l, dx_r, dy_t, dy_b, discardbits, cmask);

  nchannel = mlib_ImageGetChannels(src);

  if (nchannel == 1)
    cmask = 1;
  amask = (1 << nchannel) - 1;

  if ((cmask & amask) == amask) {
    return mlib_convMxN_8ext_f(dst, src, kwid, khgt, dx_l, dx_r, dy_t, dy_b, kernel,
                               discardbits);
  }
  else {
    return mlib_convMxN_8ext_mask(dst, src, kwid, khgt, dx_l, dx_r, dy_t, dy_b, kernel,
                                  discardbits, cmask);
  }
}

#define MAX_N   11

/***************************************************************/
mlib_status mlib_convMxN_8ext_f(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         m,
                                mlib_s32         n,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale)
{
  mlib_d64 *buffs_local[3 * (MAX_N + 1)], **buffs = buffs_local, **buff;
  mlib_d64 *buff0, *buff1, *buff2, *buff3, *buffn, *buffd, *buffe;
  mlib_d64 s00, s01, s10, s11, s20, s21, s30, s31, s0, s1, s2, s3;
  mlib_d64 d00, d01, d10, d11, d20, d21, d30, d31;
  mlib_d64 dd, d0, d1;
  mlib_s32 ik, jk, ik_last, jk_size, coff, off, doff;
  mlib_u8 *sl, *dl;
  mlib_s32 hgt = mlib_ImageGetHeight(src);
  mlib_s32 wid = mlib_ImageGetWidth(src);
  mlib_s32 sll = mlib_ImageGetStride(src);
  mlib_s32 dll = mlib_ImageGetStride(dst);
  mlib_u8 *adr_src = (mlib_u8 *) mlib_ImageGetData(src);
  mlib_u8 *adr_dst = (mlib_u8 *) mlib_ImageGetData(dst);
  mlib_s32 ssize, xsize, dsize, esize, buff_ind = 0;
  mlib_d64 *pbuff, *dp;
  mlib_f32 *karr = (mlib_f32 *) kern;
  mlib_s32 gsr_scale = (31 - scale) << 3;
  mlib_d64 drnd = vis_to_double_dup(mlib_round_8[31 - scale]);
  mlib_s32 i, j, l, ii;
  mlib_s32 nchan = mlib_ImageGetChannels(dst);

  if (n > MAX_N) {
    buffs = mlib_malloc(3 * (n + 1) * sizeof(mlib_d64 *));

    if (buffs == NULL)
      return MLIB_FAILURE;
  }

  buff = buffs + 2 * (n + 1);

  sl = adr_src;
  dl = adr_dst;

  ssize = nchan * (wid + (m - 1));
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

  xsize = ssize - nchan * (m - 1);
  ssize -= nchan * (dx_l + dx_r);

  vis_write_gsr(gsr_scale + 7);

  for (l = 0; l < n; l++) {
    mlib_d64 *buffn = buffs[l];

    mlib_ImageCopy_na((mlib_u8 *) sl, (mlib_u8 *) buffn + dx_l * nchan, ssize);

    for (i = 0; i < nchan; i++) {
      for (ii = 0; ii < dx_l; ii++) {
        *((mlib_u8 *) buffn + i + nchan * ii) = *((mlib_u8 *) buffn + i + nchan * dx_l);
      }
    }

    for (i = 0; i < nchan; i++) {
      for (ii = 0; ii < dx_r; ii++) {
        *((mlib_u8 *) buffn + i + nchan * ii + ssize + dx_l * nchan) =
          *((mlib_u8 *) buffn + i + nchan * (dx_l - 1) + ssize);
      }
    }

    if ((l >= dy_t) && (l < hgt + n - dy_b - 2))
      sl += sll;
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

    for (l = 0; l < n; l++) {
      buff[l] = buffc[l];
    }

    buffn = buffc[n];

    mlib_ImageCopy_na((mlib_u8 *) sl, (mlib_u8 *) buffn + dx_l * nchan, ssize);

    for (i = 0; i < nchan; i++) {
      for (ii = 0; ii < dx_l; ii++) {
        *((mlib_u8 *) buffn + i + nchan * ii) = *((mlib_u8 *) buffn + i + nchan * dx_l);
      }
    }

    for (i = 0; i < nchan; i++) {
      for (ii = 0; ii < dx_r; ii++) {
        *((mlib_u8 *) buffn + i + nchan * ii + ssize + dx_l * nchan) =
          *((mlib_u8 *) buffn + i + nchan * (dx_l - 1) + ssize);
      }
    }

    ik_last = (m - 1);

    for (jk = 0; jk < n; jk += jk_size) {
      jk_size = n - jk;

      if (jk_size >= 6)
        jk_size = 4;
      if (jk_size == 5)
        jk_size = 3;

      coff = 0;

      if (jk_size == 1) {

        for (ik = 0; ik < m; ik++, coff += nchan) {
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

        for (ik = 0; ik < m; ik++, coff += nchan) {
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

        for (ik = 0; ik < m; ik++, coff += nchan) {
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

        for (ik = 0; ik < m; ik++, coff += nchan) {
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

    off = ik_last * nchan;
    doff = off / 8;
    off &= 7;
    buff0 = buff[0] + doff;
    buff1 = buff[1] + doff;
    buff2 = buff[2] + doff;
    buff3 = buff[3] + doff;
    vis_write_gsr(gsr_scale + off);

    if (jk_size == 1) {
      dp = buffe;

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
        d0 = vis_fpadd16(d0, d00);
        d1 = vis_fpadd16(d1, d01);

        dd = vis_fpack16_pair(d0, d1);
        dp[i] = dd;

        buffd[2 * i] = drnd;
        buffd[2 * i + 1] = drnd;
      }

    }
    else if (jk_size == 2) {
      dp = buffe;

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
        d0 = vis_fpadd16(d0, d00);
        d0 = vis_fpadd16(d0, d10);
        d1 = vis_fpadd16(d1, d01);
        d1 = vis_fpadd16(d1, d11);

        dd = vis_fpack16_pair(d0, d1);
        dp[i] = dd;

        buffd[2 * i] = drnd;
        buffd[2 * i + 1] = drnd;
      }

    }
    else if (jk_size == 3) {

      dp = buffe;

      s01 = buff0[0];
      s11 = buff1[0];
      s21 = buff2[0];
#pragma pipeloop(0)
      for (i = 0; i < (xsize + 7) / 8; i++) {
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

    }
    else {                                  /* if (jk_size == 4) */

      dp = buffe;

      s01 = buff0[0];
      s11 = buff1[0];
      s21 = buff2[0];
      s31 = buff3[0];
#pragma pipeloop(0)
      for (i = 0; i < (xsize + 7) / 8; i++) {
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
    }

    mlib_ImageCopy_na((mlib_u8 *) buffe, dl, xsize);

    if (j < hgt - dy_b - 2)
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
mlib_status mlib_convMxN_8ext_mask(mlib_image       *dst,
                                   const mlib_image *src,
                                   mlib_s32         m,
                                   mlib_s32         n,
                                   mlib_s32         dx_l,
                                   mlib_s32         dx_r,
                                   mlib_s32         dy_t,
                                   mlib_s32         dy_b,
                                   const mlib_s32   *kern,
                                   mlib_s32         scale,
                                   mlib_s32         cmask)
{
  mlib_d64 *buffs_local[3 * (MAX_N + 1)], **buffs = buffs_local, **buff;
  mlib_d64 *buff0, *buff1, *buff2, *buff3, *buffn, *buffd, *buffe;
  mlib_d64 s00, s01, s10, s11, s20, s21, s30, s31, s0, s1, s2, s3;
  mlib_d64 d00, d01, d10, d11, d20, d21, d30, d31;
  mlib_d64 dd, d0, d1;
  mlib_s32 ik, jk, ik_last, jk_size, coff, off, doff;
  mlib_u8 *sl, *dl;
  mlib_s32 hgt = mlib_ImageGetHeight(src);
  mlib_s32 wid = mlib_ImageGetWidth(src);
  mlib_s32 sll = mlib_ImageGetStride(src);
  mlib_s32 dll = mlib_ImageGetStride(dst);
  mlib_u8 *adr_src = (mlib_u8 *) mlib_ImageGetData(src);
  mlib_u8 *adr_dst = (mlib_u8 *) mlib_ImageGetData(dst);
  mlib_s32 ssize, xsize, dsize, esize, buff_ind;
  mlib_d64 *pbuff, *dp;
  mlib_f32 *karr = (mlib_f32 *) kern;
  mlib_s32 gsr_scale = (31 - scale) << 3;
  mlib_d64 drnd = vis_to_double_dup(mlib_round_8[31 - scale]);
  mlib_s32 i, j, l, chan, testchan;
  mlib_s32 nchan = mlib_ImageGetChannels(dst);
  void (*p_proc_load) (const mlib_u8 *, mlib_u8 *, mlib_s32, mlib_s32);
  void (*p_proc_store) (const mlib_u8 *, mlib_u8 *, mlib_s32, mlib_s32);

  if (n > MAX_N) {
    buffs = mlib_malloc(3 * (n + 1) * sizeof(mlib_d64 *));

    if (buffs == NULL)
      return MLIB_FAILURE;
  }

  buff = buffs + 2 * (n + 1);

  ssize = (wid + (m - 1));
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

  xsize = wid;
  ssize -= (dx_l + dx_r);

  vis_write_gsr(gsr_scale + 7);

  if (nchan == 2) {
    p_proc_load = &mlib_v_ImageChannelExtract_U8_21_D1;
    p_proc_store = &mlib_v_ImageChannelInsert_U8_12_D1;
  }
  else if (nchan == 3) {
    p_proc_load = &mlib_v_ImageChannelExtract_U8_31_D1;
    p_proc_store = &mlib_v_ImageChannelInsert_U8_13_D1;
  }
  else {
    p_proc_load = &mlib_v_ImageChannelExtract_U8_41_D1;
    p_proc_store = &mlib_v_ImageChannelInsert_U8_14_D1;
  }

  testchan = 1;
  for (chan = 0; chan < nchan; chan++) {
    buff_ind = 0;
    sl = adr_src;
    dl = adr_dst;

    if ((cmask & testchan) == 0) {
      testchan <<= 1;
      continue;
    }

    for (l = 0; l < n; l++) {
      mlib_d64 *buffn = buffs[l];

      (*p_proc_load) ((mlib_u8 *) sl, (mlib_u8 *) buffn + dx_l, ssize, testchan);

      for (i = 0; i < dx_l; i++) {
        *((mlib_u8 *) buffn + i) = *((mlib_u8 *) buffn + dx_l);
      }

      for (i = 0; i < dx_r; i++) {
        *((mlib_u8 *) buffn + i + ssize + dx_l) =
          *((mlib_u8 *) buffn + (dx_l - 1) + ssize);
      }

      if ((l >= dy_t) && (l < hgt + n - dy_b - 2))
        sl += sll;
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

      for (l = 0; l < n; l++) {
        buff[l] = buffc[l];
      }

      buffn = buffc[n];

      (*p_proc_load) ((mlib_u8 *) sl, (mlib_u8 *) buffn + dx_l, ssize, testchan);

      for (i = 0; i < dx_l; i++) {
        *((mlib_u8 *) buffn + i) = *((mlib_u8 *) buffn + dx_l);
      }

      for (i = 0; i < dx_r; i++) {
        *((mlib_u8 *) buffn + i + ssize + dx_l) =
          *((mlib_u8 *) buffn + (dx_l - 1) + ssize);
      }

      ik_last = (m - 1);

      for (jk = 0; jk < n; jk += jk_size) {
        jk_size = n - jk;

        if (jk_size >= 6)
          jk_size = 4;
        if (jk_size == 5)
          jk_size = 3;

        coff = 0;

        if (jk_size == 1) {

          for (ik = 0; ik < m; ik++, coff++) {
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

          for (ik = 0; ik < m; ik++, coff++) {
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

          for (ik = 0; ik < m; ik++, coff++) {
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
        else {                              /* jk_size == 4 */

          for (ik = 0; ik < m; ik++, coff++) {
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

      off = ik_last;
      doff = off / 8;
      off &= 7;
      buff0 = buff[0] + doff;
      buff1 = buff[1] + doff;
      buff2 = buff[2] + doff;
      buff3 = buff[3] + doff;
      vis_write_gsr(gsr_scale + off);

      if (jk_size == 1) {
        dp = buffe;

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
          d0 = vis_fpadd16(d0, d00);
          d1 = vis_fpadd16(d1, d01);

          dd = vis_fpack16_pair(d0, d1);
          dp[i] = dd;

          buffd[2 * i] = drnd;
          buffd[2 * i + 1] = drnd;
        }

      }
      else if (jk_size == 2) {
        dp = buffe;

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
          d0 = vis_fpadd16(d0, d00);
          d0 = vis_fpadd16(d0, d10);
          d1 = vis_fpadd16(d1, d01);
          d1 = vis_fpadd16(d1, d11);

          dd = vis_fpack16_pair(d0, d1);
          dp[i] = dd;

          buffd[2 * i] = drnd;
          buffd[2 * i + 1] = drnd;
        }

      }
      else if (jk_size == 3) {

        dp = buffe;

        s01 = buff0[0];
        s11 = buff1[0];
        s21 = buff2[0];
#pragma pipeloop(0)
        for (i = 0; i < (xsize + 7) / 8; i++) {
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

      }
      else {                                /* if (jk_size == 4) */

        dp = buffe;

        s01 = buff0[0];
        s11 = buff1[0];
        s21 = buff2[0];
        s31 = buff3[0];
#pragma pipeloop(0)
        for (i = 0; i < (xsize + 7) / 8; i++) {
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
      }

      (*p_proc_store) ((mlib_u8 *) buffe, (mlib_u8 *) dl, xsize, testchan);

      if (j < hgt - dy_b - 2)
        sl += sll;
      dl += dll;

      buff_ind++;

      if (buff_ind >= (n + 1))
        buff_ind = 0;
    }

    testchan <<= 1;
  }

  mlib_free(pbuff);

  if (buffs != buffs_local)
    mlib_free(buffs);

  return MLIB_SUCCESS;
}

/***************************************************************/
