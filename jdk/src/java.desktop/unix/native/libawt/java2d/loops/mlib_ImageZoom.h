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

#ifndef __MLIB_IMAGEZOOM_H
#define __MLIB_IMAGEZOOM_H

#include <mlib_types.h>
#include <mlib_image_types.h>
#include <mlib_status.h>
#include <mlib_ImageCopy.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef void (*mlib_pack_func)(void *, void *, mlib_s32, void *);

/***************************************************************/
typedef struct {
  mlib_s32  width, height,
            srcX, srcY,
            dstX, dstY;
  void      *sp, *dp;
} mlib_clipping;

/***************************************************************/
typedef struct {
  void     *dp;
  mlib_s32 w, h;
  mlib_s32 dlb;
} mlib_edge_box;

/***************************************************************/
typedef struct mlib_work_image {
  mlib_clipping
                *nearest,        /* nearest neighbor state of image */
                *current;        /* current state of image*/
  mlib_s32
                channels,        /* channels in image */
                src_stride, dst_stride,
                width, height,   /* vertical and horizontal size src image */
                DX, DY,
                color;
  void
                *sp, *dp,
                *src_end,
                *buffer_dp,
                *colormap;
  mlib_d64
                zoomx, zoomy;
  mlib_d64
                rzoomx, rzoomy;
  mlib_d64
                xstart, ystart;
  mlib_s32      tshift;           /* shift for size of data type */
  mlib_s32      filter;
  mlib_u8       *filter1, *filter3, *filter4;
  mlib_s32      alpha;
  mlib_edge_box edges[4];
  mlib_edge_box edges_blend[4];
  mlib_s32      chan_d;
  mlib_s32      alp_ind;
  mlib_s32      sline_size;
  mlib_s32      y_max;
} mlib_work_image;

/***************************************************************/
#define GetElemSubStruct(struct, par)          (param->struct->par)
#define GetElemStruct(x)                       (param->x)

/***************************************************************/
#define SetElemSubStruct(struct, par, val)     (param->struct->par = val)
#define SetElemStruct(x, val)                  (param->x = val)

/***************************************************************/

#define VARIABLE_EDGE(FORMAT)                           \
  mlib_edge_box *edges = param->edges;                  \
  mlib_s32 i, j, ch;                                    \
  mlib_s32 channels = param->channels;                  \
  mlib_s32 w1 = edges[0].w;                             \
  mlib_s32 w2 = edges[1].w;                             \
  mlib_s32 w3 = edges[2].w;                             \
  mlib_s32 h1 = edges[0].h;                             \
  mlib_s32 h2 = edges[1].h;                             \
  mlib_s32 h3 = edges[3].h;                             \
  mlib_s32 stride_dp0 = edges[0].dlb;                   \
  mlib_s32 stride_dp1 = edges[1].dlb;                   \
  mlib_s32 stride_dp2 = edges[2].dlb;                   \
  mlib_s32 stride_dp3 = edges[3].dlb;                   \
  mlib_s32 dst_stride = GetElemStruct(dst_stride);      \
  FORMAT *dp0 = edges[0].dp;                            \
  FORMAT *dp1 = edges[1].dp;                            \
  FORMAT *dp2 = edges[2].dp;                            \
  FORMAT *dp3 = edges[3].dp

/***************************************************************/

#define  MLIB_SHIFT                     16
#define  MLIB_PREC                      (1 << MLIB_SHIFT)
#define  MLIB_MASK                      (MLIB_PREC - 1)
#define  MLIB_SCALE                     (1.0 / MLIB_PREC)
#define  MLIB_SIGN_SHIFT                31

/***************************************************************/
#define  MLIB_SCALE_BC_U8               (1.0 / (1 << 28))
#define  MLIB_SCALE_BC_S16              (1.0 / (1 << 30))

/***************************************************************/
typedef mlib_status (*mlib_zoom_fun_type)(mlib_work_image *param);

typedef mlib_status (*mlib_zoom_fun2type)(mlib_work_image *param,
                                          const mlib_f32  *flt_table);

/***************************************************************/
mlib_status mlib_ImageZoom_BIT_1_Nearest(mlib_work_image *param,
                                         mlib_s32        s_bitoff,
                                         mlib_s32        d_bitoff);

mlib_status mlib_ImageZoom_BitToGray_1_Nearest(mlib_work_image *param,
                                               mlib_s32        s_bitoff,
                                               const mlib_s32  *ghigh,
                                               const mlib_s32  *glow);

mlib_status mlib_ImageZoom_U8_1_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_U8_2_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_U8_3_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_U8_4_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_S16_1_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_S16_2_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_S16_3_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_S16_4_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_S32_1_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_S32_2_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_S32_3_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_S32_4_Nearest(mlib_work_image *param);

mlib_status mlib_ImageZoom_S32_1_Bilinear(mlib_work_image *param);
mlib_status mlib_ImageZoom_S32_2_Bilinear(mlib_work_image *param);
mlib_status mlib_ImageZoom_S32_3_Bilinear(mlib_work_image *param);
mlib_status mlib_ImageZoom_S32_4_Bilinear(mlib_work_image *param);

mlib_status mlib_ImageZoom_S32_1_1_Bilinear(mlib_work_image *param);
mlib_status mlib_ImageZoom_S32_2_1_Bilinear(mlib_work_image *param);
mlib_status mlib_ImageZoom_S32_3_1_Bilinear(mlib_work_image *param);
mlib_status mlib_ImageZoom_S32_4_1_Bilinear(mlib_work_image *param);

mlib_status mlib_ImageZoom_S32_1_Bicubic(mlib_work_image *param);
mlib_status mlib_ImageZoom_S32_2_Bicubic(mlib_work_image *param);
mlib_status mlib_ImageZoom_S32_3_Bicubic(mlib_work_image *param);
mlib_status mlib_ImageZoom_S32_4_Bicubic(mlib_work_image *param);

/***************************************************************/
#define FUNC_PROT(NAME)                                         \
  mlib_status NAME##_1(mlib_work_image *param);                 \
  mlib_status NAME##_2(mlib_work_image *param);                 \
  mlib_status NAME##_3(mlib_work_image *param);                 \
  mlib_status NAME##_4(mlib_work_image *param);                 \
  mlib_status NAME##_1s(mlib_work_image *param);                \
  mlib_status NAME##_2s(mlib_work_image *param);                \
  mlib_status NAME##_3s(mlib_work_image *param);                \
  mlib_status NAME##_4s(mlib_work_image *param)

/***************************************************************/
#define FUNC_PROT_WO_S_FUNC(NAME)                               \
  mlib_status NAME##_1(mlib_work_image *param);                 \
  mlib_status NAME##_2(mlib_work_image *param);                 \
  mlib_status NAME##_3(mlib_work_image *param);                 \
  mlib_status NAME##_4(mlib_work_image *param)

/***************************************************************/
#define FUNC_PROT_BC(NAME)                                                  \
  mlib_status NAME##_1(mlib_work_image *param,  const mlib_f32 *flt_table); \
  mlib_status NAME##_2(mlib_work_image *param,  const mlib_f32 *flt_table); \
  mlib_status NAME##_3(mlib_work_image *param,  const mlib_f32 *flt_table); \
  mlib_status NAME##_4(mlib_work_image *param,  const mlib_f32 *flt_table); \
  mlib_status NAME##_1s(mlib_work_image *param, const mlib_f32 *flt_table); \
  mlib_status NAME##_2s(mlib_work_image *param, const mlib_f32 *flt_table); \
  mlib_status NAME##_3s(mlib_work_image *param, const mlib_f32 *flt_table); \
  mlib_status NAME##_4s(mlib_work_image *param, const mlib_f32 *flt_table)

FUNC_PROT(mlib_c_ImageZoomBilinear_U8);
FUNC_PROT(mlib_c_ImageZoomBilinear_S16);
FUNC_PROT(mlib_c_ImageZoomBilinear_U16);

FUNC_PROT_BC(mlib_c_ImageZoomBicubic_U8);
FUNC_PROT_BC(mlib_c_ImageZoomBicubic_S16);
FUNC_PROT_BC(mlib_c_ImageZoomBicubic_U16);

FUNC_PROT(mlib_v_ImageZoomBilinear_U8);
FUNC_PROT(mlib_v_ImageZoomBilinear_S16);
FUNC_PROT(mlib_v_ImageZoomBilinear_U16);

FUNC_PROT(mlib_v_ImageZoomBicubic_U8);
FUNC_PROT(mlib_v_ImageZoomBicubic_S16);
FUNC_PROT(mlib_v_ImageZoomBicubic_U16);

FUNC_PROT(mlib_ImageZoomBilinear_S32);
FUNC_PROT(mlib_ImageZoomBicubic_S32);

FUNC_PROT(mlib_ImageZoomBilinear_F32);
FUNC_PROT_WO_S_FUNC(mlib_ImageZoomBicubic_F32);

FUNC_PROT(mlib_ImageZoomBilinear_D64);
FUNC_PROT_WO_S_FUNC(mlib_ImageZoomBicubic_D64);

/***************************************************************/
/* Index image part */
mlib_status mlib_c_ImageZoomIndex_U8_U8_3_Bilinear(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_U8_S16_3_Bilinear(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_S16_U8_3_Bilinear(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_S16_S16_3_Bilinear(mlib_work_image *param);

mlib_status mlib_c_ImageZoomIndex_U8_U8_4_Bilinear(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_U8_S16_4_Bilinear(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_S16_U8_4_Bilinear(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_S16_S16_4_Bilinear(mlib_work_image *param);

mlib_status mlib_c_ImageZoomIndex_U8_U8_3_Bicubic(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_U8_S16_3_Bicubic(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_S16_U8_3_Bicubic(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_S16_S16_3_Bicubic(mlib_work_image *param);

mlib_status mlib_c_ImageZoomIndex_U8_U8_4_Bicubic(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_U8_S16_4_Bicubic(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_S16_U8_4_Bicubic(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_S16_S16_4_Bicubic(mlib_work_image *param);

mlib_status mlib_c_ImageZoomIndex_U8_U8_3_Bicubic2(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_U8_S16_3_Bicubic2(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_S16_U8_3_Bicubic2(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_S16_S16_3_Bicubic2(mlib_work_image *param);

mlib_status mlib_c_ImageZoomIndex_U8_U8_4_Bicubic2(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_U8_S16_4_Bicubic2(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_S16_U8_4_Bicubic2(mlib_work_image *param);
mlib_status mlib_c_ImageZoomIndex_S16_S16_4_Bicubic2(mlib_work_image *param);

mlib_status mlib_v_ImageZoomIndex_U8_U8_Bilinear(mlib_work_image *param);
mlib_status mlib_v_ImageZoomIndex_U8_S16_Bilinear(mlib_work_image *param);
mlib_status mlib_v_ImageZoomIndex_S16_U8_Bilinear(mlib_work_image *param);
mlib_status mlib_v_ImageZoomIndex_S16_S16_Bilinear(mlib_work_image *param);

mlib_status mlib_v_ImageZoomIndex_U8_U8_Bicubic(mlib_work_image *param);
mlib_status mlib_v_ImageZoomIndex_U8_S16_Bicubic(mlib_work_image *param);
mlib_status mlib_v_ImageZoomIndex_S16_U8_Bicubic(mlib_work_image *param);
mlib_status mlib_v_ImageZoomIndex_S16_S16_Bicubic(mlib_work_image *param);

/***************************************************************/
/*  Define function and rules for computing edges  */
#define MLIB_EDGE_RULES                                 \
  switch(edge) {                                        \
                                                        \
    case MLIB_EDGE_DST_FILL_ZERO:                       \
                                                        \
      switch(mlib_ImageGetType(src)) {                  \
        case MLIB_BYTE:                                 \
          mlib_ImageZoomZeroEdge_U8(param);             \
          break;                                        \
                                                        \
        case MLIB_SHORT:                                \
        case MLIB_USHORT:                               \
          mlib_ImageZoomZeroEdge_S16(param);            \
          break;                                        \
                                                        \
        case MLIB_INT:                                  \
          mlib_ImageZoomZeroEdge_S32(param);            \
          break;                                        \
      }                                                 \
      break;                                            \
                                                        \
    case MLIB_EDGE_OP_NEAREST:                          \
                                                        \
      switch(mlib_ImageGetType(src)) {                  \
        case MLIB_BYTE:                                 \
          mlib_ImageZoomUpNearest_U8(param);            \
          break;                                        \
                                                        \
        case MLIB_SHORT:                                \
        case MLIB_USHORT:                               \
          mlib_ImageZoomUpNearest_S16(param);           \
          break;                                        \
                                                        \
        case MLIB_INT:                                  \
          mlib_ImageZoomUpNearest_S32(param);           \
          break;                                        \
      }                                                 \
      break;                                            \
                                                        \
    case MLIB_EDGE_SRC_EXTEND:                          \
                                                        \
      switch(mlib_ImageGetType(src)) {                  \
        case MLIB_BYTE:                                 \
                                                        \
          switch(filter) {                              \
            case MLIB_BILINEAR:                         \
              mlib_ImageZoomExtend_U8_Bilinear(param);  \
              break;                                    \
                                                        \
            case MLIB_BICUBIC:                          \
              mlib_ImageZoomExtend_U8_Bicubic(param);   \
              break;                                    \
                                                        \
            case MLIB_BICUBIC2:                         \
              mlib_ImageZoomExtend_U8_Bicubic2(param);  \
              break;                                    \
          }                                             \
        break;                                          \
                                                        \
        case MLIB_SHORT:                                \
          switch(filter) {                              \
            case MLIB_BILINEAR:                         \
              mlib_ImageZoomExtend_S16_Bilinear(param); \
              break;                                    \
                                                        \
            case MLIB_BICUBIC:                          \
              mlib_ImageZoomExtend_S16_Bicubic(param);  \
              break;                                    \
                                                        \
            case MLIB_BICUBIC2:                         \
              mlib_ImageZoomExtend_S16_Bicubic2(param); \
              break;                                    \
          }                                             \
        break;                                          \
                                                        \
        case MLIB_USHORT:                               \
          switch(filter) {                              \
            case MLIB_BILINEAR:                         \
              mlib_ImageZoomExtend_U16_Bilinear(param); \
              break;                                    \
                                                        \
            case MLIB_BICUBIC:                          \
              mlib_ImageZoomExtend_U16_Bicubic(param);  \
              break;                                    \
                                                        \
            case MLIB_BICUBIC2:                         \
              mlib_ImageZoomExtend_U16_Bicubic2(param); \
              break;                                    \
          }                                             \
        break;                                          \
                                                        \
        case MLIB_INT:                                  \
          switch(filter) {                              \
            case MLIB_BILINEAR:                         \
              mlib_ImageZoomExtend_S32_Bilinear(param); \
              break;                                    \
                                                        \
            case MLIB_BICUBIC:                          \
              mlib_ImageZoomExtend_S32_Bicubic(param);  \
              break;                                    \
                                                        \
            case MLIB_BICUBIC2:                         \
              mlib_ImageZoomExtend_S32_Bicubic2(param); \
              break;                                    \
          }                                             \
        break;                                          \
      }                                                 \
    break;                                              \
                                                        \
    default:                                            \
      return MLIB_SUCCESS;                              \
  }

/***************************************************************/

void mlib_ImageZoomZeroEdge_U8(mlib_work_image *param);
void mlib_ImageZoomZeroEdge_S16(mlib_work_image *param);
void mlib_ImageZoomZeroEdge_S32(mlib_work_image *param);

void mlib_ImageZoomUpNearest_U8(mlib_work_image *param);
void mlib_ImageZoomUpNearest_S16(mlib_work_image *param);
void mlib_ImageZoomUpNearest_S32(mlib_work_image *param);

void mlib_ImageZoomExtend_U8_Bilinear(mlib_work_image *param);
void mlib_ImageZoomExtend_S16_Bilinear(mlib_work_image *param);
void mlib_ImageZoomExtend_U16_Bilinear(mlib_work_image *param);
void mlib_ImageZoomExtend_S32_Bilinear(mlib_work_image *param);

void mlib_ImageZoomExtend_U8_Bicubic(mlib_work_image *param);
void mlib_ImageZoomExtend_S16_Bicubic(mlib_work_image *param);
void mlib_ImageZoomExtend_U16_Bicubic(mlib_work_image *param);
void mlib_ImageZoomExtend_S32_Bicubic(mlib_work_image *param);

void mlib_ImageZoomExtend_U8_Bicubic2(mlib_work_image *param);
void mlib_ImageZoomExtend_S16_Bicubic2(mlib_work_image *param);
void mlib_ImageZoomExtend_U16_Bicubic2(mlib_work_image *param);
void mlib_ImageZoomExtend_S32_Bicubic2(mlib_work_image *param);

void mlib_ImageZoomIndexExtend_U8_Bilinear(mlib_work_image *param);
void mlib_ImageZoomIndexExtend_S16_Bilinear(mlib_work_image *param);

void mlib_ImageZoomIndexExtend_U8_Bicubic(mlib_work_image *param);
void mlib_ImageZoomIndexExtend_S16_Bicubic(mlib_work_image *param);
void mlib_ImageZoomIndexExtend_U8_Bicubic2(mlib_work_image *param);
void mlib_ImageZoomIndexExtend_S16_Bicubic2(mlib_work_image *param);

/* Float image part */
mlib_status mlib_ImageZoom_F32_1_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_F32_1_Bilinear(mlib_work_image *param);
mlib_status mlib_ImageZoom_F32_1_Bicubic(mlib_work_image *param);
mlib_status mlib_ImageZoom_F32_1_Bicubic2(mlib_work_image *param);

mlib_status mlib_ImageZoom_F32_2_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_F32_2_Bilinear(mlib_work_image *param);
mlib_status mlib_ImageZoom_F32_2_Bicubic(mlib_work_image *param);
mlib_status mlib_ImageZoom_F32_2_Bicubic2(mlib_work_image *param);

mlib_status mlib_ImageZoom_F32_3_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_F32_3_Bilinear(mlib_work_image *param);
mlib_status mlib_ImageZoom_F32_3_Bicubic(mlib_work_image *param);
mlib_status mlib_ImageZoom_F32_3_Bicubic2(mlib_work_image *param);

mlib_status mlib_ImageZoom_F32_4_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_F32_4_Bilinear(mlib_work_image *param);
mlib_status mlib_ImageZoom_F32_4_Bicubic(mlib_work_image *param);
mlib_status mlib_ImageZoom_F32_4_Bicubic2(mlib_work_image *param);

/* Double image part*/
mlib_status mlib_ImageZoom_D64_1_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_D64_1_Bilinear(mlib_work_image *param);
mlib_status mlib_ImageZoom_D64_1_Bicubic(mlib_work_image *param);
mlib_status mlib_ImageZoom_D64_1_Bicubic2(mlib_work_image *param);

mlib_status mlib_ImageZoom_D64_2_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_D64_2_Bilinear(mlib_work_image *param);
mlib_status mlib_ImageZoom_D64_2_Bicubic(mlib_work_image *param);
mlib_status mlib_ImageZoom_D64_2_Bicubic2(mlib_work_image *param);

mlib_status mlib_ImageZoom_D64_3_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_D64_3_Bilinear(mlib_work_image *param);
mlib_status mlib_ImageZoom_D64_3_Bicubic(mlib_work_image *param);
mlib_status mlib_ImageZoom_D64_3_Bicubic2(mlib_work_image *param);

mlib_status mlib_ImageZoom_D64_4_Nearest(mlib_work_image *param);
mlib_status mlib_ImageZoom_D64_4_Bilinear(mlib_work_image *param);
mlib_status mlib_ImageZoom_D64_4_Bicubic(mlib_work_image *param);
mlib_status mlib_ImageZoom_D64_4_Bicubic2(mlib_work_image *param);

/* Edge's */
void mlib_ImageZoomZeroEdge_F32(mlib_work_image *param);
void mlib_ImageZoomZeroEdge_D64(mlib_work_image *param);

void mlib_ImageZoomUpNearest_F32(mlib_work_image *param);
void mlib_ImageZoomUpNearest_D64(mlib_work_image *param);

void mlib_ImageZoomExtend_F32_Bilinear(mlib_work_image *param);
void mlib_ImageZoomExtend_D64_Bilinear(mlib_work_image *param);

void mlib_ImageZoomExtend_F32_Bicubic(mlib_work_image *param);
void mlib_ImageZoomExtend_D64_Bicubic(mlib_work_image *param);

void mlib_ImageZoomExtend_F32_Bicubic2(mlib_work_image *param);
void mlib_ImageZoomExtend_D64_Bicubic2(mlib_work_image *param);

/***************************************************************/

typedef mlib_status (*mlib_zoomblend_fun_type)(mlib_work_image *param, mlib_s32 alp_ind);
typedef mlib_status (*mlib_zoomblend_bc_type)(mlib_work_image *param,
                                              const mlib_f32  *flt_table,
                                              mlib_s32 alp);

mlib_status mlib_ImageZoom_U8_33_Nearest(mlib_work_image *param, mlib_s32 alp_ind);
mlib_status mlib_ImageZoom_U8_43_Nearest(mlib_work_image *param, mlib_s32 alp_ind);
mlib_status mlib_ImageZoom_U8_34_Nearest(mlib_work_image *param, mlib_s32 alp_ind);
mlib_status mlib_ImageZoom_U8_44_Nearest(mlib_work_image *param, mlib_s32 alp_ind);

mlib_status mlib_c_ImageZoomBilinear_U8_3to34(mlib_work_image *param);
mlib_status mlib_c_ImageZoomBilinear_U8_4to34(mlib_work_image *param);

mlib_status mlib_c_ImageZoomBilinear_U8_33(mlib_work_image *param, mlib_s32 alp_ind);
mlib_status mlib_c_ImageZoomBilinear_U8_43(mlib_work_image *param, mlib_s32 alp_ind);
mlib_status mlib_c_ImageZoomBilinear_U8_34(mlib_work_image *param, mlib_s32 alp_ind);
mlib_status mlib_c_ImageZoomBilinear_U8_44(mlib_work_image *param, mlib_s32 alp_ind);

mlib_status mlib_c_ImageZoomBicubic_U8_33(mlib_work_image *param,
                                          const mlib_f32  *flt_table,
                                          mlib_s32 alp);
mlib_status mlib_c_ImageZoomBicubic_U8_43(mlib_work_image *param,
                                          const mlib_f32  *flt_table,
                                          mlib_s32 alp);
mlib_status mlib_c_ImageZoomBicubic_U8_34(mlib_work_image *param,
                                          const mlib_f32  *flt_table,
                                          mlib_s32 alp);
mlib_status mlib_c_ImageZoomBicubic_U8_44(mlib_work_image *param,
                                          const mlib_f32  *flt_table,
                                          mlib_s32 alp);

/***************************************************************/

mlib_status mlib_ZoomBlendEdge(mlib_image *dst,
                               const mlib_image *src,
                               mlib_work_image *param,
                               mlib_filter filter,
                               mlib_edge   edge,
                               mlib_s32    alp_ind);

mlib_status mlib_ImageZoomClipping(mlib_image       *dst,
                                   const mlib_image *src,
                                   mlib_d64         zoomx,
                                   mlib_d64         zoomy,
                                   mlib_d64         tx,
                                   mlib_d64         ty,
                                   mlib_filter      filter,
                                   mlib_edge        edge,
                                   mlib_work_image  *param);

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __MLIB_IMAGEZOOM_H */
