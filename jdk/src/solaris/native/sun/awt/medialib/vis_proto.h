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
 *----------------------------------------------------------------------
 *
 * Prototypes for the inline templates in vis_32.il (and vis_64.il)
 *
 *----------------------------------------------------------------------
 */

#ifndef VIS_PROTO_H
#define VIS_PROTO_H


#include <sys/isa_defs.h>

#ifdef __cplusplus
extern "C" {
#endif  /* __cplusplus */

/* Pure edge handling instructions */
int vis_edge8(void * /*frs1*/, void * /*frs2*/);
int vis_edge8l(void * /*frs1*/, void * /*frs2*/);
int vis_edge16(void * /*frs1*/, void * /*frs2*/);
int vis_edge16l(void * /*frs1*/, void * /*frs2*/);
int vis_edge32(void * /*frs1*/, void * /*frs2*/);
int vis_edge32l(void * /*frs1*/, void * /*frs2*/);

/* Edge handling instructions with negative return values if cc set. */
int vis_edge8cc(void * /*frs1*/, void * /*frs2*/);
int vis_edge8lcc(void * /*frs1*/, void * /*frs2*/);
int vis_edge16cc(void * /*frs1*/, void * /*frs2*/);
int vis_edge16lcc(void * /*frs1*/, void * /*frs2*/);
int vis_edge32cc(void * /*frs1*/, void * /*frs2*/);
int vis_edge32lcc(void * /*frs1*/, void * /*frs2*/);

/* Alignment instructions. */
void *vis_alignaddr(void * /*rs1*/, int /*rs2*/);
void *vis_alignaddrl(void * /*rs1*/, int /*rs2*/);
double vis_faligndata(double /*frs1*/, double /*frs2*/);

/* Partitioned comparison instructions. */
int vis_fcmple16(double /*frs1*/, double /*frs2*/);
int vis_fcmpne16(double /*frs1*/, double /*frs2*/);
int vis_fcmple32(double /*frs1*/, double /*frs2*/);
int vis_fcmpne32(double /*frs1*/, double /*frs2*/);
int vis_fcmpgt16(double /*frs1*/, double /*frs2*/);
int vis_fcmpeq16(double /*frs1*/, double /*frs2*/);
int vis_fcmpgt32(double /*frs1*/, double /*frs2*/);
int vis_fcmpeq32(double /*frs1*/, double /*frs2*/);

/* Partitioned multiplication. */
double vis_fmul8x16_dummy(float /*frs1*/, int /*dummy*/, double /*frs2*/);
#ifdef MLIB_OS64BIT
double vis_fmul8x16(float /*frs1*/, double /*frs2*/);
#else
#define vis_fmul8x16(farg,darg) vis_fmul8x16_dummy((farg),0,(darg))
#endif  /* MLIB_OS64BIT */

double vis_fmul8x16au(float /*frs1*/, float /*frs2*/);
double vis_fmul8x16al(float /*frs1*/, float /*frs2*/);
double vis_fmul8sux16(double /*frs1*/, double /*frs2*/);
double vis_fmul8ulx16(double /*frs1*/, double /*frs2*/);
double vis_fmuld8sux16(float /*frs1*/, float /*frs2*/);
double vis_fmuld8ulx16(float /*frs1*/, float /*frs2*/);

/* Partitioned addition & subtraction. */
double vis_fpadd16(double /*frs1*/, double /*frs2*/);
float vis_fpadd16s(float /*frs1*/, float /*frs2*/);
double vis_fpadd32(double /*frs1*/, double /*frs2*/);
float vis_fpadd32s(float /*frs1*/, float /*frs2*/);
double vis_fpsub16(double /*frs1*/, double /*frs2*/);
float vis_fpsub16s(float /*frs1*/, float /*frs2*/);
double vis_fpsub32(double /*frs1*/, double /*frs2*/);
float vis_fpsub32s(float /*frs1*/, float /*frs2*/);

/* Pixel packing & clamping. */
float vis_fpack16(double /*frs2*/);
double vis_fpack32(double /*frs1*/, double /*frs2*/);
float vis_fpackfix(double /*frs2*/);

/* Combined pack ops. */
double vis_fpack16_pair(double /*frs2*/, double /*frs2*/);
double vis_fpackfix_pair(double /*frs2*/, double /*frs2*/);
void vis_st2_fpack16(double, double, double *);
void vis_std_fpack16(double, double, double *);
void vis_st2_fpackfix(double, double, double *);

double vis_fpack16_to_hi(double /*frs1*/, double /*frs2*/);
double vis_fpack16_to_lo(double /*frs1*/, double /*frs2*/);

/* Motion estimation. */
#ifdef MLIB_OS64BIT
#define vis_pdist(px1,px2,acc) vis_pxldist64(acc,px1,px2)
double vis_pxldist64(double accum /*frd*/, double pxls1 /*frs1*/,
                     double pxls2 /*frs2*/);
#else
double vis_pdist(double /*frs1*/, double /*frs2*/, double /*frd*/);
#endif  /* MLIB_OS64BIT */

/* Channel merging. */
double vis_fpmerge(float /*frs1*/, float /*frs2*/);

/* Pixel expansion. */
double vis_fexpand(float /*frs2*/);
double vis_fexpand_hi(double /*frs2*/);
double vis_fexpand_lo(double /*frs2*/);

/* Bitwise logical operators. */
double vis_fnor(double /*frs1*/, double /*frs2*/);
float vis_fnors(float /*frs1*/, float /*frs2*/);
double vis_fandnot(double /*frs1*/, double /*frs2*/);
float vis_fandnots(float /*frs1*/, float /*frs2*/);
double vis_fnot(double /*frs1*/);
float vis_fnots(float /*frs1*/);
double vis_fxor(double /*frs1*/, double /*frs2*/);
float vis_fxors(float /*frs1*/, float /*frs2*/);
double vis_fnand(double /*frs1*/, double /*frs2*/);
float vis_fnands(float /*frs1*/, float /*frs2*/);
double vis_fand(double /*frs1*/, double /*frs2*/);
float vis_fands(float /*frs1*/, float /*frs2*/);
double vis_fxnor(double /*frs1*/, double /*frs2*/);
float vis_fxnors(float /*frs1*/, float /*frs2*/);
double vis_fsrc(double /*frs1*/);
float vis_fsrcs(float /*frs1*/);
double vis_fornot(double /*frs1*/, double /*frs2*/);
float vis_fornots(float /*frs1*/, float /*frs2*/);
double vis_for(double /*frs1*/, double /*frs2*/);
float vis_fors(float /*frs1*/, float /*frs2*/);
double vis_fzero(void);
float vis_fzeros(void);
double vis_fone(void);
float vis_fones(void);

/* Partial stores. */
void vis_stdfa_ASI_PST8P(double /*frd*/, void * /*rs1*/, int /*rmask*/);
void vis_stdfa_ASI_PST8PL(double /*frd*/, void * /*rs1*/, int /*rmask*/);
void vis_stdfa_ASI_PST8S(double /*frd*/, void * /*rs1*/, int /*rmask*/);
void vis_stdfa_ASI_PST8P_int_pair(void * /*rs1*/, void * /*rs2*/,
                                  void * /*rs3*/, int /*rmask*/);
void vis_stdfa_ASI_PST16P(double /*frd*/, void * /*rs1*/, int /*rmask*/);
void vis_stdfa_ASI_PST16PL(double /*frd*/, void * /*rs1*/, int /*rmask*/);
void vis_stdfa_ASI_PST16S(double /*frd*/, void * /*rs1*/, int /*rmask*/);
void vis_stdfa_ASI_PST32P(double /*frd*/, void * /*rs1*/, int /*rmask*/);
void vis_stdfa_ASI_PST32PL(double /*frd*/, void * /*rs1*/, int /*rmask*/);
void vis_stdfa_ASI_PST32S(double /*frd*/, void * /*rs1*/, int /*rmask*/);

/* Byte & short stores. */
void vis_stdfa_ASI_FL8P(double /*frd*/, void * /*rs1*/);
void vis_stdfa_ASI_FL8P_index(double /*frd*/, void * /*rs1*/, long /*index*/);
void vis_stdfa_ASI_FL8S(double /*frd*/, void * /*rs1*/);
void vis_stdfa_ASI_FL16P(double /*frd*/, void * /*rs1*/);
void vis_stdfa_ASI_FL16P_index(double /*frd*/, void * /*rs1*/, long /*index*/);
void vis_stdfa_ASI_FL16S(double /*frd*/, void * /*rs1*/);
void vis_stdfa_ASI_FL8PL(double /*frd*/, void * /*rs1*/);
void vis_stdfa_ASI_FL8PL_index(double /*frd*/, void * /*rs1*/, long /*index*/);
void vis_stdfa_ASI_FL8SL(double /*frd*/, void * /*rs1*/);
void vis_stdfa_ASI_FL16PL(double /*frd*/, void * /*rs1*/);
void vis_stdfa_ASI_FL16PL_index(double /*frd*/, void * /*rs1*/, long /*index*/);
void vis_stdfa_ASI_FL16SL(double /*frd*/, void * /*rs1*/);

/* Byte & short loads. */
double vis_lddfa_ASI_FL8P(void * /*rs1*/);
double vis_lddfa_ASI_FL8P_index(void * /*rs1*/, long /*index*/);
double vis_lddfa_ASI_FL8P_hi(void * /*rs1*/, unsigned int /*index*/);
double vis_lddfa_ASI_FL8P_lo(void * /*rs1*/, unsigned int /*index*/);
double vis_lddfa_ASI_FL8S(void * /*rs1*/);
double vis_lddfa_ASI_FL16P(void * /*rs1*/);
double vis_lddfa_ASI_FL16P_index(void * /*rs1*/, long /*index*/);
double vis_lddfa_ASI_FL16S(void * /*rs1*/);
double vis_lddfa_ASI_FL8PL(void * /*rs1*/);
double vis_lddfa_ASI_FL8PL_index(void * /*rs1*/, long /*index*/);
double vis_lddfa_ASI_FL8SL(void * /*rs1*/);
double vis_lddfa_ASI_FL16PL(void * /*rs1*/);
double vis_lddfa_ASI_FL16PL_index(void * /*rs1*/, long /*index*/);
double vis_lddfa_ASI_FL16SL(void * /*rs1*/);

/* Direct read from GSR, write to GSR. */
unsigned int vis_read_gsr32(void);
void vis_write_gsr32(unsigned int /*GSR*/);

#define vis_write_gsr     vis_write_gsr32
#define vis_read_gsr      vis_read_gsr32

/* Voxel texture mapping. */
#ifdef MLIB_OS64BIT
unsigned long vis_array8(unsigned long /*rs1*/, int /*rs2*/);
unsigned long vis_array16(unsigned long /*rs1*/, int /*rs2*/);
unsigned long vis_array32(unsigned long /*rs1*/, int /*rs2*/);
#elif __STDC__ - 0 == 0 && !defined(_NO_LONGLONG)
unsigned long vis_array8(unsigned long long /*rs1*/, int /*rs2*/);
unsigned long vis_array16(unsigned long long /*rs1*/, int /*rs2*/);
unsigned long vis_array32(unsigned long long /*rs1*/, int /*rs2*/);
#endif  /* MLIB_OS64BIT */

/* Register aliasing and type casts. */
float vis_read_hi(double /*frs1*/);
float vis_read_lo(double /*frs1*/);
double vis_write_hi(double /*frs1*/, float /*frs2*/);
double vis_write_lo(double /*frs1*/, float /*frs2*/);
double vis_freg_pair(float /*frs1*/, float /*frs2*/);
float vis_to_float(unsigned int /*value*/);
double vis_to_double(unsigned int /*value1*/, unsigned int /*value2*/);
double vis_to_double_dup(unsigned int /*value*/);

#ifdef MLIB_OS64BIT
double vis_ll_to_double(unsigned long /*value*/);
#elif __STDC__ - 0 == 0 && !defined(_NO_LONGLONG)
double vis_ll_to_double(unsigned long long /*value*/);
#endif  /* MLIB_OS64BIT */

/* Direct access to ASI. */
/* normal asi = 0x82, big endian = 0x80, little endian = 0x88 */
unsigned int vis_read_asi(void);
void vis_write_asi(unsigned int /*ASI*/);

/* Big/little endian loads. */
float vis_ldfa_ASI_REG(void * /*rs1*/);                                         /* endian according */
                                                                                /* to %asi */
float vis_ldfa_ASI_P(void * /*rs1*/);                                           /* big endian */
float vis_ldfa_ASI_P_index(void * /*rs1*/, long /*index*/);                     /* big endian */
float vis_ldfa_ASI_PL(void * /*rs1*/);                                          /* little endian */
float vis_ldfa_ASI_PL_index(void * /*rs1*/, long /*index*/);                    /* little endian */
double vis_lddfa_ASI_REG(void * /*rs1*/);                                       /* endian according */
                                                                                /* to %asi */
double vis_lddfa_ASI_P(void * /*rs1*/);                                         /* big endian */
double vis_lddfa_ASI_P_index(void * /*rs1*/, long /*index*/);                   /* big endian */
double vis_lddfa_ASI_PL(void * /*rs1*/);                                        /* little endian */
double vis_lddfa_ASI_PL_index(void * /*rs1*/, long /*index*/);                  /* little endian */

/* Big/little endian stores. */
void vis_stfa_ASI_REG(float /*frs*/, void * /*rs1*/);                           /* endian according */
                                                                                /* to %asi */
void vis_stfa_ASI_P(float /*frs*/, void * /*rs1*/);                             /* big endian */
void vis_stfa_ASI_P_index(float /*frs*/, void * /*rs1*/, long /*index*/);       /* big endian */
void vis_stfa_ASI_PL(float /*frs*/, void * /*rs1*/);                            /* little endian */
void vis_stfa_ASI_PL_index(float /*frs*/, void * /*rs1*/, long /*index*/);      /* little endian */
void vis_stdfa_ASI_REG(double /*frd*/, void * /*rs1*/);                         /* endian according */
                                                                                /* to %asi */
void vis_stdfa_ASI_P(double /*frd*/, void * /*rs1*/);                           /* big endian */
void vis_stdfa_ASI_P_index(double /*frd*/, void * /*rs1*/, long /*index*/);     /* big endian */
void vis_stdfa_ASI_PL(double /*frd*/, void * /*rs1*/);                          /* little endian */
void vis_stdfa_ASI_PL_index(double /*frd*/, void * /*rs1*/, long /*index*/);    /* little endian */

/* Unsigned short big/little endian loads. */
unsigned short vis_lduha_ASI_REG(void * /*rs1*/);
unsigned short vis_lduha_ASI_P(void * /*rs1*/);
unsigned short vis_lduha_ASI_PL(void * /*rs1*/);
unsigned short vis_lduha_ASI_P_index(void * /*rs1*/, long /*index*/);
unsigned short vis_lduha_ASI_PL_index(void * /*rs1*/, long /*index*/);

/* Nicknames for explicit ASI loads and stores. */
#define vis_st_u8       vis_stdfa_ASI_FL8P
#define vis_st_u8_i     vis_stdfa_ASI_FL8P_index
#define vis_st_u8_le    vis_stdfa_ASI_FL8PL
#define vis_st_u8_le_i  vis_stdfa_ASI_FL8PL_index
#define vis_st_u16      vis_stdfa_ASI_FL16P
#define vis_st_u16_i    vis_stdfa_ASI_FL16P_index
#define vis_st_u16_le   vis_stdfa_ASI_FL16PL
#define vis_st_u16_le_i vis_stdfa_ASI_FL16PL_index

#define vis_ld_u8       vis_lddfa_ASI_FL8P
#define vis_ld_u8_i     vis_lddfa_ASI_FL8P_index
#define vis_ld_u8_le    vis_lddfa_ASI_FL8PL
#define vis_ld_u8_le_i  vis_lddfa_ASI_FL8PL_index
#define vis_ld_u16      vis_lddfa_ASI_FL16P
#define vis_ld_u16_i    vis_lddfa_ASI_FL16P_index
#define vis_ld_u16_le   vis_lddfa_ASI_FL16PL
#define vis_ld_u16_le_i vis_lddfa_ASI_FL16PL_index

#define vis_pst_8       vis_stdfa_ASI_PST8P
#define vis_pst_16      vis_stdfa_ASI_PST16P
#define vis_pst_32      vis_stdfa_ASI_PST32P

#define vis_pst_8_le    vis_stdfa_ASI_PST8PL
#define vis_pst_16_le   vis_stdfa_ASI_PST16PL
#define vis_pst_32_le   vis_stdfa_ASI_PST32PL

#define vis_ld_f32_asi  vis_ldfa_ASI_REG
#define vis_ld_f32      vis_ldfa_ASI_P
#define vis_ld_f32_i    vis_ldfa_ASI_P_index
#define vis_ld_f32_le   vis_ldfa_ASI_PL
#define vis_ld_f32_le_i vis_ldfa_ASI_PL_index

#define vis_ld_d64_asi  vis_lddfa_ASI_REG
#define vis_ld_d64      vis_lddfa_ASI_P
#define vis_ld_d64_i    vis_lddfa_ASI_P_index
#define vis_ld_d64_le   vis_lddfa_ASI_PL
#define vis_ld_d64_le_i vis_lddfa_ASI_PL_index

#define vis_st_f32_asi  vis_stfa_ASI_REG
#define vis_st_f32      vis_stfa_ASI_P
#define vis_st_f32_i    vis_stfa_ASI_P_index
#define vis_st_f32_le   vis_stfa_ASI_PL
#define vis_st_f32_le_i vis_stfa_ASI_PL_index

#define vis_st_d64_asi  vis_stdfa_ASI_REG
#define vis_st_d64      vis_stdfa_ASI_P
#define vis_st_d64_i    vis_stdfa_ASI_P_index
#define vis_st_d64_le   vis_stdfa_ASI_PL
#define vis_st_d64_le_i vis_stdfa_ASI_PL_index

/* "<" and ">=" may be implemented in terms of ">" and "<=". */
#define vis_fcmplt16(a,b) vis_fcmpgt16((b),(a))
#define vis_fcmplt32(a,b) vis_fcmpgt32((b),(a))
#define vis_fcmpge16(a,b) vis_fcmple16((b),(a))
#define vis_fcmpge32(a,b) vis_fcmple32((b),(a))

/* Prefetch */
void vis_prefetch_read(void * /*address*/);
void vis_prefetch_write(void * /*address*/);

#pragma no_side_effect(vis_prefetch_read)
#pragma no_side_effect(vis_prefetch_write)

/* Nonfaulting load */

char vis_ldsba_ASI_PNF(void * /*rs1*/);
char vis_ldsba_ASI_PNF_index(void * /*rs1*/, long /*index*/);
char vis_ldsba_ASI_PNFL(void * /*rs1*/);
char vis_ldsba_ASI_PNFL_index(void * /*rs1*/, long /*index*/);

unsigned char vis_lduba_ASI_PNF(void * /*rs1*/);
unsigned char vis_lduba_ASI_PNF_index(void * /*rs1*/, long /*index*/);
unsigned char vis_lduba_ASI_PNFL(void * /*rs1*/);
unsigned char vis_lduba_ASI_PNFL_index(void * /*rs1*/, long /*index*/);

short vis_ldsha_ASI_PNF(void * /*rs1*/);
short vis_ldsha_ASI_PNF_index(void * /*rs1*/, long /*index*/);
short vis_ldsha_ASI_PNFL(void * /*rs1*/);
short vis_ldsha_ASI_PNFL_index(void * /*rs1*/, long /*index*/);

unsigned short vis_lduha_ASI_PNF(void * /*rs1*/);
unsigned short vis_lduha_ASI_PNF_index(void * /*rs1*/, long /*index*/);
unsigned short vis_lduha_ASI_PNFL(void * /*rs1*/);
unsigned short vis_lduha_ASI_PNFL_index(void * /*rs1*/, long /*index*/);

int vis_ldswa_ASI_PNF(void * /*rs1*/);
int vis_ldswa_ASI_PNF_index(void * /*rs1*/, long /*index*/);
int vis_ldswa_ASI_PNFL(void * /*rs1*/);
int vis_ldswa_ASI_PNFL_index(void * /*rs1*/, long /*index*/);

unsigned int vis_lduwa_ASI_PNF(void * /*rs1*/);
unsigned int vis_lduwa_ASI_PNF_index(void * /*rs1*/, long /*index*/);
unsigned int vis_lduwa_ASI_PNFL(void * /*rs1*/);
unsigned int vis_lduwa_ASI_PNFL_index(void * /*rs1*/, long /*index*/);

#ifdef MLIB_OS64BIT

long vis_ldxa_ASI_PNF(void * /*rs1*/);
long vis_ldxa_ASI_PNF_index(void * /*rs1*/, long /*index*/);
long vis_ldxa_ASI_PNFL(void * /*rs1*/);
long vis_ldxa_ASI_PNFL_index(void * /*rs1*/, long /*index*/);

#elif __STDC__ - 0 == 0 && !defined(_NO_LONGLONG)

long long vis_ldda_ASI_PNF(void * /*rs1*/);
long long vis_ldda_ASI_PNF_index(void * /*rs1*/, long /*index*/);
long long vis_ldda_ASI_PNFL(void * /*rs1*/);
long long vis_ldda_ASI_PNFL_index(void * /*rs1*/, long /*index*/);

#endif  /* MLIB_OS64BIT */

float vis_ldfa_ASI_PNF(void * /*rs1*/);
float vis_ldfa_ASI_PNF_index(void * /*rs1*/, long /*index*/);
float vis_ldfa_ASI_PNFL(void * /*rs1*/);
float vis_ldfa_ASI_PNFL_index(void * /*rs1*/, long /*index*/);

double vis_lddfa_ASI_PNF(void * /*rs1*/);
double vis_lddfa_ASI_PNF_index(void * /*rs1*/, long /*index*/);
double vis_lddfa_ASI_PNFL(void * /*rs1*/);
double vis_lddfa_ASI_PNFL_index(void * /*rs1*/, long /*index*/);

#define vis_ld_s8_nf            vis_ldsba_ASI_PNF
#define vis_ld_s8_nf_i          vis_ldsba_ASI_PNF_index
#define vis_ld_s8_nf_le         vis_ldsba_ASI_PNFL
#define vis_ld_s8_nf_le_i       vis_ldsba_ASI_PNFL_index

#define vis_ld_u8_nf            vis_lduba_ASI_PNF
#define vis_ld_u8_nf_i          vis_lduba_ASI_PNF_index
#define vis_ld_u8_nf_le         vis_lduba_ASI_PNFL
#define vis_ld_u8_nf_le_i       vis_lduba_ASI_PNFL_index

#define vis_ld_s16_nf           vis_ldsha_ASI_PNF
#define vis_ld_s16_nf_i         vis_ldsha_ASI_PNF_index
#define vis_ld_s16_nf_le        vis_ldsha_ASI_PNFL
#define vis_ld_s16_nf_le_i      vis_ldsha_ASI_PNFL_index

#define vis_ld_u16_nf           vis_lduha_ASI_PNF
#define vis_ld_u16_nf_i         vis_lduha_ASI_PNF_index
#define vis_ld_u16_nf_le        vis_lduha_ASI_PNFL
#define vis_ld_u16_nf_le_i      vis_lduha_ASI_PNFL_index

#define vis_ld_s32_nf           vis_ldswa_ASI_PNF
#define vis_ld_s32_nf_i         vis_ldswa_ASI_PNF_index
#define vis_ld_s32_nf_le        vis_ldswa_ASI_PNFL
#define vis_ld_s32_nf_le_i      vis_ldswa_ASI_PNFL_index

#define vis_ld_u32_nf           vis_lduwa_ASI_PNF
#define vis_ld_u32_nf_i         vis_lduwa_ASI_PNF_index
#define vis_ld_u32_nf_le        vis_lduwa_ASI_PNFL
#define vis_ld_u32_nf_le_i      vis_lduwa_ASI_PNFL_index

#ifdef MLIB_OS64BIT

#define vis_ld_s64_nf           vis_ldxa_ASI_PNF
#define vis_ld_s64_nf_i         vis_ldxa_ASI_PNF_index
#define vis_ld_s64_nf_le        vis_ldxa_ASI_PNFL
#define vis_ld_s64_nf_le_i      vis_ldxa_ASI_PNFL_index

#define vis_ld_u64_nf           vis_ldxa_ASI_PNF
#define vis_ld_u64_nf_i         vis_ldxa_ASI_PNF_index
#define vis_ld_u64_nf_le        vis_ldxa_ASI_PNFL
#define vis_ld_u64_nf_le_i      vis_ldxa_ASI_PNFL_index

#elif __STDC__ - 0 == 0 && !defined(_NO_LONGLONG)

#define vis_ld_s64_nf           vis_ldda_ASI_PNF
#define vis_ld_s64_nf_i         vis_ldda_ASI_PNF_index
#define vis_ld_s64_nf_le        vis_ldda_ASI_PNFL
#define vis_ld_s64_nf_le_i      vis_ldda_ASI_PNFL_index

#define vis_ld_u64_nf           vis_ldda_ASI_PNF
#define vis_ld_u64_nf_i         vis_ldda_ASI_PNF_index
#define vis_ld_u64_nf_le        vis_ldda_ASI_PNFL
#define vis_ld_u64_nf_le_i      vis_ldda_ASI_PNFL_index

#endif  /* MLIB_OS64BIT */

#define vis_ld_f32_nf           vis_ldfa_ASI_PNF
#define vis_ld_f32_nf_i         vis_ldfa_ASI_PNF_index
#define vis_ld_f32_nf_le        vis_ldfa_ASI_PNFL
#define vis_ld_f32_nf_le_i      vis_ldfa_ASI_PNFL_index

#define vis_ld_d64_nf           vis_lddfa_ASI_PNF
#define vis_ld_d64_nf_i         vis_lddfa_ASI_PNF_index
#define vis_ld_d64_nf_le        vis_lddfa_ASI_PNFL
#define vis_ld_d64_nf_le_i      vis_lddfa_ASI_PNFL_index

#if VIS >= 0x200
/* Edge handling instructions which do not set the integer condition codes */
int vis_edge8n(void * /*rs1*/, void * /*rs2*/);
int vis_edge8ln(void * /*rs1*/, void * /*rs2*/);
int vis_edge16n(void * /*rs1*/, void * /*rs2*/);
int vis_edge16ln(void * /*rs1*/, void * /*rs2*/);
int vis_edge32n(void * /*rs1*/, void * /*rs2*/);
int vis_edge32ln(void * /*rs1*/, void * /*rs2*/);

#define vis_edge8       vis_edge8n
#define vis_edge8l      vis_edge8ln
#define vis_edge16      vis_edge16n
#define vis_edge16l     vis_edge16ln
#define vis_edge32      vis_edge32n
#define vis_edge32l     vis_edge32ln

/* Byte mask and shuffle instructions */
void vis_write_bmask(unsigned int /*rs1*/, unsigned int /*rs2*/);
double vis_bshuffle(double /*frs1*/, double /*frs2*/);

/* Graphics status register */
unsigned int vis_read_bmask(void);
#ifdef MLIB_OS64BIT
unsigned long vis_read_gsr64(void);
void vis_write_gsr64(unsigned long /* GSR */);
#elif __STDC__ - 0 == 0 && !defined(_NO_LONGLONG)
unsigned long long vis_read_gsr64(void);
void vis_write_gsr64(unsigned long long /* GSR */);
#endif  /* MLIB_OS64BIT */
#endif  /* VIS >= 0x200 */

#ifdef __cplusplus
} // End of extern "C"
#endif  /* __cplusplus */

#endif  /* VIS_PROTO_H */
