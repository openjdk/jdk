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


#ifndef __MLIB_V_IMAGECHANNELINSERT_H
#define __MLIB_V_IMAGECHANNELINSERT_H


#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

void mlib_v_ImageChannelInsert_U8(const mlib_u8 *src,
                                  mlib_s32      slb,
                                  mlib_u8       *dst,
                                  mlib_s32      dlb,
                                  mlib_s32      channels,
                                  mlib_s32      channeld,
                                  mlib_s32      width,
                                  mlib_s32      height,
                                  mlib_s32      cmask);

void mlib_v_ImageChannelInsert_D64(const mlib_d64 *src,
                                   mlib_s32       slb,
                                   mlib_d64       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       channels,
                                   mlib_s32       channeld,
                                   mlib_s32       width,
                                   mlib_s32       height,
                                   mlib_s32       cmask);

void mlib_v_ImageChannelInsert_S16(const mlib_s16 *src,
                                   mlib_s32       slb,
                                   mlib_s16       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       channels,
                                   mlib_s32       channeld,
                                   mlib_s32       width,
                                   mlib_s32       height,
                                   mlib_s32       cmask);

void mlib_v_ImageChannelInsert_S32(const mlib_s32 *src,
                                   mlib_s32       slb,
                                   mlib_s32       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       channels,
                                   mlib_s32       channeld,
                                   mlib_s32       width,
                                   mlib_s32       height,
                                   mlib_s32       cmask);

void mlib_v_ImageChannelInsert_U8_12_A8D1X8(const mlib_u8 *src,
                                            mlib_u8       *dst,
                                            mlib_s32      dsize,
                                            mlib_s32      cmask);

void mlib_v_ImageChannelInsert_U8_12_A8D2X8(const mlib_u8 *src,
                                            mlib_s32      slb,
                                            mlib_u8       *dst,
                                            mlib_s32      dlb,
                                            mlib_s32      xsize,
                                            mlib_s32      ysize,
                                            mlib_s32      cmask);

void mlib_v_ImageChannelInsert_U8_12_D1(const mlib_u8 *src,
                                        mlib_u8       *dst,
                                        mlib_s32      dsize,
                                        mlib_s32      cmask);

void mlib_v_ImageChannelInsert_U8_12(const mlib_u8 *src,
                                     mlib_s32      slb,
                                     mlib_u8       *dst,
                                     mlib_s32      dlb,
                                     mlib_s32      xsize,
                                     mlib_s32      ysize,
                                     mlib_s32      cmask);

void mlib_v_ImageChannelInsert_U8_13_A8D1X8(const mlib_u8 *src,
                                            mlib_u8       *dst,
                                            mlib_s32      dsize,
                                            mlib_s32      cmask);

void mlib_v_ImageChannelInsert_U8_13_A8D2X8(const mlib_u8 *src,
                                            mlib_s32      slb,
                                            mlib_u8       *dst,
                                            mlib_s32      dlb,
                                            mlib_s32      xsize,
                                            mlib_s32      ysize,
                                            mlib_s32      cmask);

void mlib_v_ImageChannelInsert_U8_13_D1(const mlib_u8 *src,
                                        mlib_u8       *dst,
                                        mlib_s32      dsize,
                                        mlib_s32      cmask);

void mlib_v_ImageChannelInsert_U8_13(const mlib_u8 *src,
                                     mlib_s32      slb,
                                     mlib_u8       *dst,
                                     mlib_s32      dlb,
                                     mlib_s32      xsize,
                                     mlib_s32      ysize,
                                     mlib_s32      cmask);

void mlib_v_ImageChannelInsert_U8_14_A8D1X8(const mlib_u8 *src,
                                            mlib_u8       *dst,
                                            mlib_s32      dsize,
                                            mlib_s32      cmask);

void mlib_v_ImageChannelInsert_U8_14_A8D2X8(const mlib_u8 *src,
                                            mlib_s32      slb,
                                            mlib_u8       *dst,
                                            mlib_s32      dlb,
                                            mlib_s32      xsize,
                                            mlib_s32      ysize,
                                            mlib_s32      cmask);

void mlib_v_ImageChannelInsert_U8_14_D1(const mlib_u8 *src,
                                        mlib_u8       *dst,
                                        mlib_s32      dsize,
                                        mlib_s32      cmask);

void mlib_v_ImageChannelInsert_U8_14(const mlib_u8 *src,
                                     mlib_s32      slb,
                                     mlib_u8       *dst,
                                     mlib_s32      dlb,
                                     mlib_s32      xsize,
                                     mlib_s32      ysize,
                                     mlib_s32      cmask);

void mlib_v_ImageChannelInsert_S16_12_A8D1X4(const mlib_s16 *src,
                                             mlib_s16       *dst,
                                             mlib_s32       dsize,
                                             mlib_s32       cmask);

void mlib_v_ImageChannelInsert_S16_12_A8D2X4(const mlib_s16 *src,
                                             mlib_s32       slb,
                                             mlib_s16       *dst,
                                             mlib_s32       dlb,
                                             mlib_s32       xsize,
                                             mlib_s32       ysize,
                                             mlib_s32       cmask);

void mlib_v_ImageChannelInsert_S16_12_D1(const mlib_s16 *src,
                                         mlib_s16       *dst,
                                         mlib_s32       dsize,
                                         mlib_s32       cmask);

void mlib_v_ImageChannelInsert_S16_12(const mlib_s16 *src,
                                      mlib_s32       slb,
                                      mlib_s16       *dst,
                                      mlib_s32       dlb,
                                      mlib_s32       xsize,
                                      mlib_s32       ysize,
                                      mlib_s32       cmask);

void mlib_v_ImageChannelInsert_S16_13_A8D1X4(const mlib_s16 *src,
                                             mlib_s16       *dst,
                                             mlib_s32       dsize,
                                             mlib_s32       cmask);

void mlib_v_ImageChannelInsert_S16_13_A8D2X4(const mlib_s16 *src,
                                             mlib_s32       slb,
                                             mlib_s16       *dst,
                                             mlib_s32       dlb,
                                             mlib_s32       xsize,
                                             mlib_s32       ysize,
                                             mlib_s32       cmask);

void mlib_v_ImageChannelInsert_S16_13_D1(const mlib_s16 *src,
                                         mlib_s16       *dst,
                                         mlib_s32       dsize,
                                         mlib_s32       cmask);

void mlib_v_ImageChannelInsert_S16_13(const mlib_s16 *src,
                                      mlib_s32       slb,
                                      mlib_s16       *dst,
                                      mlib_s32       dlb,
                                      mlib_s32       xsize,
                                      mlib_s32       ysize,
                                      mlib_s32       cmask);

void mlib_v_ImageChannelInsert_S16_14_A8D1X4(const mlib_s16 *src,
                                             mlib_s16       *dst,
                                             mlib_s32       dsize,
                                             mlib_s32       cmask);

void mlib_v_ImageChannelInsert_S16_14_A8D2X4(const mlib_s16 *src,
                                             mlib_s32       slb,
                                             mlib_s16       *dst,
                                             mlib_s32       dlb,
                                             mlib_s32       xsize,
                                             mlib_s32       ysize,
                                             mlib_s32       cmask);

void mlib_v_ImageChannelInsert_S16_14_D1(const mlib_s16 *src,
                                         mlib_s16       *dst,
                                         mlib_s32       dsize,
                                         mlib_s32       cmask);

void mlib_v_ImageChannelInsert_S16_14(const mlib_s16 *src,
                                      mlib_s32       slb,
                                      mlib_s16       *dst,
                                      mlib_s32       dlb,
                                      mlib_s32       xsize,
                                      mlib_s32       ysize,
                                      mlib_s32       cmask);

void mlib_v_ImageChannelInsert_U8_34R_A8D1X8(const mlib_u8 *src,
                                             mlib_u8       *dst,
                                             mlib_s32      dsize);

void mlib_v_ImageChannelInsert_U8_34R_A8D2X8(const mlib_u8 *src,
                                             mlib_s32      slb,
                                             mlib_u8       *dst,
                                             mlib_s32      dlb,
                                             mlib_s32      xsize,
                                             mlib_s32      ysize);

void mlib_v_ImageChannelInsert_U8_34R_D1(const mlib_u8 *src,
                                         mlib_u8       *dst,
                                         mlib_s32      dsize);

void mlib_v_ImageChannelInsert_U8_34R(const mlib_u8 *src,
                                      mlib_s32      slb,
                                      mlib_u8       *dst,
                                      mlib_s32      dlb,
                                      mlib_s32      xsize,
                                      mlib_s32      ysize);

void mlib_v_ImageChannelInsert_S16_34R_A8D1X4(const mlib_s16 *src,
                                              mlib_s16       *dst,
                                              mlib_s32       dsize);

void mlib_v_ImageChannelInsert_S16_34R_A8D2X4(const mlib_s16 *src,
                                              mlib_s32       slb,
                                              mlib_s16       *dst,
                                              mlib_s32       dlb,
                                              mlib_s32       xsize,
                                              mlib_s32       ysize);

void mlib_v_ImageChannelInsert_S16_34R_D1(const mlib_s16 *src,
                                          mlib_s16       *dst,
                                          mlib_s32       dsize);

void mlib_v_ImageChannelInsert_S16_34R(const mlib_s16 *src,
                                       mlib_s32       slb,
                                       mlib_s16       *dst,
                                       mlib_s32       dlb,
                                       mlib_s32       xsize,
                                       mlib_s32       ysize);

void mlib_v_ImageChannelInsert_U8_34L_A8D1X8(const mlib_u8 *src,
                                             mlib_u8       *dst,
                                             mlib_s32      dsize);

void mlib_v_ImageChannelInsert_U8_34L_A8D2X8(const mlib_u8 *src,
                                             mlib_s32      slb,
                                             mlib_u8       *dst,
                                             mlib_s32      dlb,
                                             mlib_s32      xsize,
                                             mlib_s32      ysize);

void mlib_v_ImageChannelInsert_U8_34L_D1(const mlib_u8 *src,
                                         mlib_u8       *dst,
                                         mlib_s32      dsize);

void mlib_v_ImageChannelInsert_U8_34L(const mlib_u8 *src,
                                      mlib_s32      slb,
                                      mlib_u8       *dst,
                                      mlib_s32      dlb,
                                      mlib_s32      xsize,
                                      mlib_s32      ysize);

void mlib_v_ImageChannelInsert_S16_34L_A8D1X4(const mlib_s16 *src,
                                              mlib_s16       *dst,
                                              mlib_s32       dsize);

void mlib_v_ImageChannelInsert_S16_34L_A8D2X4(const mlib_s16 *src,
                                              mlib_s32       slb,
                                              mlib_s16       *dst,
                                              mlib_s32       dlb,
                                              mlib_s32       xsize,
                                              mlib_s32       ysize);

void mlib_v_ImageChannelInsert_S16_34L_D1(const mlib_s16 *src,
                                          mlib_s16       *dst,
                                          mlib_s32       dsize);

void mlib_v_ImageChannelInsert_S16_34L(const mlib_s16 *src,
                                       mlib_s32       slb,
                                       mlib_s16       *dst,
                                       mlib_s32       dlb,
                                       mlib_s32       xsize,
                                       mlib_s32       ysize);

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __MLIB_V_IMAGECHANNELINSERT_H */
