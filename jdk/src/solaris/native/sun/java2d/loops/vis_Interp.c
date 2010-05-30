/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

#include <vis_proto.h>
#include "java2d_Mlib.h"

/*#define USE_TWO_BC_TABLES*/ /* a little more precise, but slow on Ultra-III */

/***************************************************************/

#define MUL_16x16(src1, src2)                   \
  vis_fpadd16(vis_fmul8sux16((src1), (src2)),   \
              vis_fmul8ulx16((src1), (src2)))

#define BILINEAR                                                \
  xf = vis_fand(xf, mask7fff);                                  \
  yf = vis_fand(yf, mask7fff);                                  \
  xr = vis_fpsub32(mask7fff, xf);                               \
  yf0 = vis_fmul8x16au(mask80, vis_read_hi(yf));                \
  yf1 = vis_fmul8x16au(mask80, vis_read_lo(yf));                \
                                                                \
  a0 = vis_fmul8x16au(vis_read_hi(a01), vis_read_hi(xr));       \
  a1 = vis_fmul8x16au(vis_read_lo(a01), vis_read_hi(xf));       \
  a2 = vis_fmul8x16au(vis_read_hi(a23), vis_read_hi(xr));       \
  a3 = vis_fmul8x16au(vis_read_lo(a23), vis_read_hi(xf));       \
  a0 = vis_fpadd16(a0, a1);                                     \
  a2 = vis_fpadd16(a2, a3);                                     \
  a2 = vis_fpsub16(a2, a0);                                     \
  a2 = MUL_16x16(a2, yf0);                                      \
  a0 = vis_fmul8x16(mask40, a0);                                \
  a0 = vis_fpadd16(a0, a2);                                     \
  a0 = vis_fpadd16(a0, d_rnd);                                  \
                                                                \
  b0 = vis_fmul8x16au(vis_read_hi(b01), vis_read_lo(xr));       \
  b1 = vis_fmul8x16au(vis_read_lo(b01), vis_read_lo(xf));       \
  b2 = vis_fmul8x16au(vis_read_hi(b23), vis_read_lo(xr));       \
  b3 = vis_fmul8x16au(vis_read_lo(b23), vis_read_lo(xf));       \
  b0 = vis_fpadd16(b0, b1);                                     \
  b2 = vis_fpadd16(b2, b3);                                     \
  b2 = vis_fpsub16(b2, b0);                                     \
  b2 = MUL_16x16(b2, yf1);                                      \
  b0 = vis_fmul8x16(mask40, b0);                                \
  b0 = vis_fpadd16(b0, b2);                                     \
  b0 = vis_fpadd16(b0, d_rnd);                                  \
                                                                \
  xf = vis_fpadd32(xf, dx);                                     \
  yf = vis_fpadd32(yf, dy)

void
vis_BilinearBlend(jint *pRGB, jint numpix,
                  jint xfract, jint dxfract,
                  jint yfract, jint dyfract)
{
  mlib_d64 *p_src = (void*)pRGB;
  mlib_f32 *p_dst = (void*)pRGB;
  mlib_d64 a01, a23, a0, a1, a2, a3;
  mlib_d64 b01, b23, b0, b1, b2, b3;
  mlib_d64 xf, xr, dx, yf, yf0, yf1, dy;
  mlib_d64 mask7fff, d_rnd;
  mlib_f32 mask80, mask40;
  mlib_s32 i;

  vis_write_gsr(2 << 3);

  xf = vis_to_double(xfract >> 1, (xfract + dxfract) >> 1);
  yf = vis_to_double(yfract >> 1, (yfract + dyfract) >> 1);
  dx = vis_to_double_dup(dxfract);
  dy = vis_to_double_dup(dyfract);

  mask7fff = vis_to_double_dup(0x7fffffff);
  d_rnd = vis_to_double_dup(0x00100010);
  mask80 = vis_to_float(0x80808080);
  mask40 = vis_to_float(0x40404040);

#pragma pipeloop(0)
  for (i = 0; i < numpix/2; i++) {
    a01 = p_src[0];
    a23 = p_src[1];
    b01 = p_src[2];
    b23 = p_src[3];
    p_src += 4;

    BILINEAR;

    ((mlib_d64*)p_dst)[0] = vis_fpack16_pair(a0, b0);
    p_dst += 2;
  }

  if (numpix & 1) {
    a01 = p_src[0];
    a23 = p_src[1];

    BILINEAR;

    p_dst[0] = vis_fpack16(a0);
  }
}

/***************************************************************/

static jboolean vis_bicubic_table_inited = 0;
static mlib_d64 vis_bicubic_coeff[256 + 1];
#ifdef USE_TWO_BC_TABLES
static mlib_d64 vis_bicubic_coeff2[512 + 1];
#endif

/*
 * REMIND: The following formulas are designed to give smooth
 * results when 'A' is -0.5 or -1.0.
 */

static void
init_vis_bicubic_table(jdouble A)
{
  mlib_s16 *p_tbl = (void*)vis_bicubic_coeff;
#ifdef USE_TWO_BC_TABLES
  mlib_s16 *p_tbl2 = (void*)vis_bicubic_coeff2;
#endif
  mlib_d64 x, y;
  int i;

  for (i = 0; i <= 256; i++) {
    x = i*(1.0/256.0);

    /* r(x) = (A + 2)|x|^3 - (A + 3)|x|^2 + 1 , 0 <= |x| < 1 */
    y = ((A+2)*x - (A+3))*x*x + 1;
    y *= 16384;
    p_tbl[4*i + 1] = p_tbl[4*(256 - i) + 2] = (mlib_s16)y;
#ifdef USE_TWO_BC_TABLES
    y *= 2;
    if (y >= 32767) y = 32767;
    p_tbl2[4*i] = p_tbl2[4*i + 1] =
    p_tbl2[4*i + 2] = p_tbl2[4*i + 3] = (mlib_s16)y;
#endif

    /* r(x) = A|x|^3 - 5A|x|^2 + 8A|x| - 4A , 1 <= |x| < 2 */
    x += 1.0;
    y = ((A*x - 5*A)*x + 8*A)*x - 4*A;
    y *= 16384;
    p_tbl[4*i] = p_tbl[4*(256 - i) + 3] = (mlib_s16)y;
#ifdef USE_TWO_BC_TABLES
    y *= 2;
    if (y >= 32767) y = 32767;
    p_tbl2[4*i + 1024] = p_tbl2[4*i + 1025] =
    p_tbl2[4*i + 1026] = p_tbl2[4*i + 1027] = (mlib_s16)y;
#endif
  }
  vis_bicubic_table_inited = 1;
}

/***************************************************************/

#define MUL_BC_COEFF(x0, x1, coeff)                                     \
  vis_fpadd16(vis_fmul8x16au(x0, coeff), vis_fmul8x16al(x1, coeff))

#define SAT(val, max) \
    do { \
        val -= max;           /* only overflows are now positive */ \
        val &= (val >> 31);   /* positives become 0 */ \
        val += max;           /* range is now [0 -> max] */ \
    } while (0)

void
vis_BicubicBlend(jint *pRGB, jint numpix,
                 jint xfract, jint dxfract,
                 jint yfract, jint dyfract)
{
  mlib_d64 *p_src = (void*)pRGB;
  union {
      jint     theInt;
      mlib_f32 theF32;
  } p_dst;
  mlib_d64 a0, a1, a2, a3, a4, a5, a6, a7;
  mlib_d64 xf, yf, yf0, yf1, yf2, yf3;
  mlib_d64 d_rnd;
  mlib_f32 mask80;
  mlib_s32 i;

  if (!vis_bicubic_table_inited) {
    init_vis_bicubic_table(-0.5);
  }

#ifdef USE_TWO_BC_TABLES
  vis_write_gsr(2 << 3);
  d_rnd = vis_to_double_dup(0x000f000f);
#else
  vis_write_gsr(4 << 3);
  d_rnd = vis_to_double_dup(0x00030003);
#endif

  mask80 = vis_to_float(0x80808080);

#pragma pipeloop(0)
  for (i = 0; i < numpix; i++) {
    jint xfactor, yfactor;

    xfactor = URShift(xfract, 32-8);
    xfract += dxfract;
    xf = vis_bicubic_coeff[xfactor];

    a0 = p_src[0];
    a1 = p_src[1];
    a2 = p_src[2];
    a3 = p_src[3];
    a4 = p_src[4];
    a5 = p_src[5];
    a6 = p_src[6];
    a7 = p_src[7];
    p_src += 8;

    a0 = MUL_BC_COEFF(vis_read_hi(a0), vis_read_lo(a0), vis_read_hi(xf));
    a1 = MUL_BC_COEFF(vis_read_hi(a1), vis_read_lo(a1), vis_read_lo(xf));
    a2 = MUL_BC_COEFF(vis_read_hi(a2), vis_read_lo(a2), vis_read_hi(xf));
    a3 = MUL_BC_COEFF(vis_read_hi(a3), vis_read_lo(a3), vis_read_lo(xf));
    a4 = MUL_BC_COEFF(vis_read_hi(a4), vis_read_lo(a4), vis_read_hi(xf));
    a5 = MUL_BC_COEFF(vis_read_hi(a5), vis_read_lo(a5), vis_read_lo(xf));
    a6 = MUL_BC_COEFF(vis_read_hi(a6), vis_read_lo(a6), vis_read_hi(xf));
    a7 = MUL_BC_COEFF(vis_read_hi(a7), vis_read_lo(a7), vis_read_lo(xf));

    a0 = vis_fpadd16(a0, a1);
    a1 = vis_fpadd16(a2, a3);
    a2 = vis_fpadd16(a4, a5);
    a3 = vis_fpadd16(a6, a7);

    yfactor = URShift(yfract, 32-8);
    yfract += dyfract;
#ifdef USE_TWO_BC_TABLES
    yf0 = vis_bicubic_coeff2[256 + yfactor];
    yf1 = vis_bicubic_coeff2[yfactor];
    yf2 = vis_bicubic_coeff2[256 - yfactor];
    yf3 = vis_bicubic_coeff2[512 - yfactor];
#else
    yf = vis_bicubic_coeff[yfactor];
    yf0 = vis_fmul8x16au(mask80, vis_read_hi(yf));
    yf1 = vis_fmul8x16al(mask80, vis_read_hi(yf));
    yf2 = vis_fmul8x16au(mask80, vis_read_lo(yf));
    yf3 = vis_fmul8x16al(mask80, vis_read_lo(yf));
#endif

    a0 = MUL_16x16(a0, yf0);
    a1 = MUL_16x16(a1, yf1);
    a2 = MUL_16x16(a2, yf2);
    a3 = MUL_16x16(a3, yf3);
    a0 = vis_fpadd16(a0, d_rnd);

    a0 = vis_fpadd16(vis_fpadd16(a0, a1), vis_fpadd16(a2, a3));

    p_dst.theF32 = vis_fpack16(a0);
    {
        int a, r, g, b;
        b = p_dst.theInt;
        a = (b >> 24) & 0xff;
        r = (b >> 16) & 0xff;
        g = (b >>  8) & 0xff;
        b = (b      ) & 0xff;
        SAT(r, a);
        SAT(g, a);
        SAT(b, a);
        *pRGB++ = ((a << 24) | (r << 16) | (g << 8) | (b));
    }
  }
}

/***************************************************************/
