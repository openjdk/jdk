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


#ifndef __MLIB_C_IMAGETHRESH1_H
#define __MLIB_C_IMAGETHRESH1_H

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/***************************************************************/
#define PARAMS                                                  \
  void     *psrc,                                               \
  void     *pdst,                                               \
  mlib_s32 src_stride,                                          \
  mlib_s32 dst_stride,                                          \
  mlib_s32 width,                                               \
  mlib_s32 height,                                              \
  void     *__thresh,                                           \
  void     *__ghigh,                                            \
  void     *__glow

void mlib_c_ImageThresh1_D641(PARAMS);
void mlib_c_ImageThresh1_D642(PARAMS);
void mlib_c_ImageThresh1_D643(PARAMS);
void mlib_c_ImageThresh1_D644(PARAMS);
void mlib_c_ImageThresh1_D641_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_D642_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_D643_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_D644_1B(PARAMS, mlib_s32 dbit_off);

void mlib_c_ImageThresh1_F321(PARAMS);
void mlib_c_ImageThresh1_F322(PARAMS);
void mlib_c_ImageThresh1_F323(PARAMS);
void mlib_c_ImageThresh1_F324(PARAMS);
void mlib_c_ImageThresh1_F321_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_F322_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_F323_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_F324_1B(PARAMS, mlib_s32 dbit_off);

void mlib_c_ImageThresh1_S321(PARAMS);
void mlib_c_ImageThresh1_S322(PARAMS);
void mlib_c_ImageThresh1_S323(PARAMS);
void mlib_c_ImageThresh1_S324(PARAMS);
void mlib_c_ImageThresh1_S321_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_S322_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_S323_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_S324_1B(PARAMS, mlib_s32 dbit_off);

void mlib_c_ImageThresh1_S161(PARAMS);
void mlib_c_ImageThresh1_S162(PARAMS);
void mlib_c_ImageThresh1_S163(PARAMS);
void mlib_c_ImageThresh1_S164(PARAMS);
void mlib_c_ImageThresh1_S161_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_S162_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_S163_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_S164_1B(PARAMS, mlib_s32 dbit_off);

void mlib_c_ImageThresh1_U161(PARAMS);
void mlib_c_ImageThresh1_U162(PARAMS);
void mlib_c_ImageThresh1_U163(PARAMS);
void mlib_c_ImageThresh1_U164(PARAMS);
void mlib_c_ImageThresh1_U161_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_U162_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_U163_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_U164_1B(PARAMS, mlib_s32 dbit_off);

void mlib_c_ImageThresh1_U81(PARAMS);
void mlib_c_ImageThresh1_U82(PARAMS);
void mlib_c_ImageThresh1_U83(PARAMS);
void mlib_c_ImageThresh1_U84(PARAMS);
void mlib_c_ImageThresh1_U81_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_U82_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_U83_1B(PARAMS, mlib_s32 dbit_off);
void mlib_c_ImageThresh1_U84_1B(PARAMS, mlib_s32 dbit_off);

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __MLIB_C_IMAGETHRESH1_H */
