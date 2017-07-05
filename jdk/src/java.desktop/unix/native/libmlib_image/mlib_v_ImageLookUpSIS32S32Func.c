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



#include "mlib_image.h"
#include "mlib_v_ImageLookUpFunc.h"

/***************************************************************/
#define HALF_U64        (MLIB_U64_CONST(2147483648) * sizeof(table[0][0]))

/***************************************************************/
void mlib_v_ImageLookUpSI_S32_S32(const mlib_s32 *src,
                                  mlib_s32       slb,
                                  mlib_s32       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s32 **table,
                                  mlib_s32       csize)
{
  mlib_s32 i, j, k;

  dlb >>= 2; slb >>= 2;

  if (xsize < 2) {
    for(j = 0; j < ysize; j++, dst += dlb, src += slb){
      for(k = 0; k < csize; k++) {
        mlib_s32 *da = dst + k;
        mlib_s32 *sa = (void *)src;
        const mlib_s32 *tab = (void *)&(((mlib_u8 **)table)[k][HALF_U64]);

        for(i = 0; i < xsize; i++, da += csize, sa++)
        *da=tab[*sa];
      }
    }

  } else {
    for(j = 0; j < ysize; j++, dst += dlb, src += slb) {
#pragma pipeloop(0)
      for(k = 0; k < csize; k++) {
        mlib_s32 *da = dst + k;
        mlib_s32 *sa = (void *)src;
        const mlib_s32 *tab = (void *)&(((mlib_u8 **)table)[k][HALF_U64]);
        mlib_s32 s0, t0, s1, t1;

        s0 = sa[0];
        s1 = sa[1];
        sa += 2;

        for(i = 0; i < xsize - 3; i+=2, da += 2*csize, sa += 2) {
          t0 = tab[s0];
          t1 = tab[s1];
          s0 = sa[0];
          s1 = sa[1];
          da[0] = t0;
          da[csize] = t1;
        }

        t0 = tab[s0];
        t1 = tab[s1];
        da[0] = t0;
        da[csize] = t1;

        if (xsize & 1) da[2*csize] = tab[sa[0]];
      }
    }
  }
}

/***************************************************************/
