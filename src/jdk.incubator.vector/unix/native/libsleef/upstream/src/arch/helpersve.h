/*********************************************************************/
/*          Copyright ARM Ltd. 2010 - 2024.                          */
/* Distributed under the Boost Software License, Version 1.0.        */
/*    (See accompanying file LICENSE.txt or copy at                  */
/*          http://www.boost.org/LICENSE_1_0.txt)                    */
/*********************************************************************/

#if !defined(__ARM_FEATURE_SVE) && !defined(SLEEF_GENHEADER)
#error Please specify SVE flags.
#endif

#if !defined(SLEEF_GENHEADER)
#include <arm_sve.h>
#include <stdint.h>

#include "misc.h"
#endif // #if !defined(SLEEF_GENHEADER)

#if defined(VECTLENDP) || defined(VECTLENSP)
#error VECTLENDP or VECTLENSP already defined
#endif

#if CONFIG == 1 || CONFIG == 2
// Vector length agnostic
#define VECTLENSP (svcntw())
//@#define VECTLENSP (svcntw())
#define VECTLENDP (svcntd())
//@#define VECTLENDP (svcntd())
#define ISANAME "AArch64 SVE"
#define ptrue svptrue_b8()
//@#define ptrue svptrue_b8()
#elif CONFIG == 8
// 256-bit vector length
#define ISANAME "AArch64 SVE 256-bit"
#define LOG2VECTLENDP 2
#define ptrue svptrue_pat_b8(SV_VL32)
#define DFTPRIORITY 20
#elif CONFIG == 9
// 512-bit vector length
#define ISANAME "AArch64 SVE 512-bit"
#define LOG2VECTLENDP 3
#define ptrue svptrue_pat_b8(SV_VL64)
#define DFTPRIORITY 21
#elif CONFIG == 10
// 1024-bit vector length
#define ISANAME "AArch64 SVE 1024-bit"
#define LOG2VECTLENDP 4
#define ptrue svptrue_pat_b8(SV_VL128)
#define DFTPRIORITY 22
#elif CONFIG == 11
// 2048-bit vector length
#define ISANAME "AArch64 SVE 2048-bit"
#define LOG2VECTLENDP 5
#define ptrue svptrue_pat_b8(SV_VL256)
#define DFTPRIORITY 23
#else
#error CONFIG macro invalid or not defined
#endif

#ifdef LOG2VECTLENDP
// For DFT, VECTLENDP and VECTLENSP are not the size of the available
// vector length, but the size of the partial vectors utilized in the
// computation. The appropriate VECTLENDP and VECTLENSP are chosen by
// the dispatcher according to the value of svcntd().

#define LOG2VECTLENSP (LOG2VECTLENDP+1)
#define VECTLENDP (1 << LOG2VECTLENDP)
#define VECTLENSP (1 << LOG2VECTLENSP)
static INLINE int vavailability_i(int name) { return svcntd() >= VECTLENDP ? 3 : 0; }
#else
static INLINE int vavailability_i(int name) { return 3; }
#endif

#define ENABLE_SP
//@#define ENABLE_SP
#define ENABLE_DP
//@#define ENABLE_DP

#if CONFIG != 2
#define ENABLE_FMA_SP
//@#define ENABLE_FMA_SP
#define ENABLE_FMA_DP
//@#define ENABLE_FMA_DP
//#define SPLIT_KERNEL // Benchmark comparison is needed to determine whether this option should be enabled.
#endif

#define FULL_FP_ROUNDING
//@#define FULL_FP_ROUNDING
#define ACCURATE_SQRT
//@#define ACCURATE_SQRT

// Type definitions

// Mask definition
typedef svint32_t vmask;
typedef svbool_t vopmask;

// Single precision definitions
typedef svfloat32_t vfloat;
typedef svint32_t vint2;

// Double precision definitions
typedef svfloat64_t vdouble;
typedef svint32_t vint;

typedef svint64_t vint64;
typedef svuint64_t vuint64;

// Double-double data type with setter/getter functions
typedef svfloat64x2_t vdouble2;
static INLINE vdouble  vd2getx_vd_vd2(vdouble2 v) { return svget2_f64(v, 0); }
static INLINE vdouble  vd2gety_vd_vd2(vdouble2 v) { return svget2_f64(v, 1); }
static INLINE vdouble2 vd2setxy_vd2_vd_vd(vdouble x, vdouble y)  { return svcreate2_f64(x, y); }
static INLINE vdouble2 vd2setx_vd2_vd2_vd(vdouble2 v, vdouble d) { return svset2_f64(v, 0, d); }
static INLINE vdouble2 vd2sety_vd2_vd2_vd(vdouble2 v, vdouble d) { return svset2_f64(v, 1, d); }

// Double-float data type with setter/getter functions
typedef svfloat32x2_t vfloat2;
static INLINE vfloat  vf2getx_vf_vf2(vfloat2 v) { return svget2_f32(v, 0); }
static INLINE vfloat  vf2gety_vf_vf2(vfloat2 v) { return svget2_f32(v, 1); }
static INLINE vfloat2 vf2setxy_vf2_vf_vf(vfloat x, vfloat y)  { return svcreate2_f32(x, y); }
static INLINE vfloat2 vf2setx_vf2_vf2_vf(vfloat2 v, vfloat d) { return svset2_f32(v, 0, d); }
static INLINE vfloat2 vf2sety_vf2_vf2_vf(vfloat2 v, vfloat d) { return svset2_f32(v, 1, d); }

typedef svint32x2_t vquad;
static INLINE vmask vqgetx_vm_vq(vquad v) { return svget2_s32(v, 0); }
static INLINE vmask vqgety_vm_vq(vquad v) { return svget2_s32(v, 1); }
static INLINE vquad vqsetxy_vq_vm_vm(vmask x, vmask y) { return svcreate2_s32(x, y); }
static INLINE vquad vqsetx_vq_vq_vm(vquad v, vmask x) { return svset2_s32(v, 0, x); }
static INLINE vquad vqsety_vq_vq_vm(vquad v, vmask y) { return svset2_s32(v, 1, y); }

typedef vquad vargquad;

// Auxiliary data types

typedef svfloat64x2_t di_t;

static INLINE vdouble digetd_vd_di(di_t d) { return svget2_f64(d, 0); }
static INLINE vint digeti_vi_di(di_t d) { return svreinterpret_s32_f64(svget2_f64(d, 1)); }
static INLINE di_t disetdi_di_vd_vi(vdouble d, vint i) {
  return svcreate2_f64(d, svreinterpret_f64_s32(i));
}

//

typedef svfloat32x2_t fi_t;

static INLINE vfloat figetd_vf_di(fi_t d) { return svget2_f32(d, 0); }
static INLINE vint2 figeti_vi2_di(fi_t d) { return svreinterpret_s32_f32(svget2_f32(d, 1)); }
static INLINE fi_t fisetdi_fi_vf_vi2(vfloat d, vint2 i) {
  return svcreate2_f32(d, svreinterpret_f32_s32(i));
}

//

typedef svfloat64x3_t ddi_t;

static INLINE vdouble2 ddigetdd_vd2_ddi(ddi_t d) {
  return svcreate2_f64(svget3_f64(d, 0), svget3_f64(d, 1));
}
static INLINE vint ddigeti_vi_ddi(ddi_t d) { return svreinterpret_s32_f64(svget3_f64(d, 2)); }
static INLINE ddi_t ddisetddi_ddi_vd2_vi(vdouble2 v, vint i) {
  return svcreate3_f64(svget2_f64(v, 0), svget2_f64(v, 1),
                       svreinterpret_f64_s32(i));
}
static INLINE ddi_t ddisetdd_ddi_ddi_vd2(ddi_t ddi, vdouble2 v) {
  return svcreate3_f64(svget2_f64(v, 0), svget2_f64(v, 1), svget3_f64(ddi, 2));
}

//

typedef svfloat32x3_t dfi_t;

static INLINE vfloat2 dfigetdf_vf2_dfi(dfi_t d) {
  return svcreate2_f32(svget3_f32(d, 0), svget3_f32(d, 1));
}
static INLINE vint2 dfigeti_vi2_dfi(dfi_t d) { return svreinterpret_s32_f32(svget3_f32(d, 2)); }
static INLINE dfi_t dfisetdfi_dfi_vf2_vi2(vfloat2 v, vint2 i) {
  return svcreate3_f32(svget2_f32(v, 0), svget2_f32(v, 1),
                       svreinterpret_f32_s32(i));
}
static INLINE dfi_t dfisetdf_dfi_dfi_vf2(dfi_t dfi, vfloat2 v) {
  return svcreate3_f32(svget2_f32(v, 0), svget2_f32(v, 1), svget3_f32(dfi, 2));
}

//

typedef svfloat64x4_t dd2;

static INLINE dd2 dd2setab_dd2_vd2_vd2(vdouble2 a, vdouble2 b) {
  return svcreate4_f64(svget2_f64(a, 0), svget2_f64(a, 1),
                       svget2_f64(b, 0), svget2_f64(b, 1));
}
static INLINE vdouble2 dd2geta_vd2_dd2(dd2 d) {
  return svcreate2_f64(svget4_f64(d, 0), svget4_f64(d, 1));
}
static INLINE vdouble2 dd2getb_vd2_dd2(dd2 d) {
  return svcreate2_f64(svget4_f64(d, 2), svget4_f64(d, 3));
}

//

typedef svfloat32x4_t df2;

static INLINE df2 df2setab_df2_vf2_vf2(vfloat2 a, vfloat2 b) {
  return svcreate4_f32(svget2_f32(a, 0), svget2_f32(a, 1),
                       svget2_f32(b, 0), svget2_f32(b, 1));
}
static INLINE vfloat2 df2geta_vf2_df2(df2 d) {
  return svcreate2_f32(svget4_f32(d, 0), svget4_f32(d, 1));
}
static INLINE vfloat2 df2getb_vf2_df2(df2 d) {
  return svcreate2_f32(svget4_f32(d, 2), svget4_f32(d, 3));
}

//

typedef svfloat64x3_t vdouble3;

static INLINE vdouble  vd3getx_vd_vd3(vdouble3 v) { return svget3_f64(v, 0); }
static INLINE vdouble  vd3gety_vd_vd3(vdouble3 v) { return svget3_f64(v, 1); }
static INLINE vdouble  vd3getz_vd_vd3(vdouble3 v) { return svget3_f64(v, 2); }
static INLINE vdouble3 vd3setxyz_vd3_vd_vd_vd(vdouble x, vdouble y, vdouble z)  { return svcreate3_f64(x, y, z); }
static INLINE vdouble3 vd3setx_vd3_vd3_vd(vdouble3 v, vdouble d) { return svset3_f64(v, 0, d); }
static INLINE vdouble3 vd3sety_vd3_vd3_vd(vdouble3 v, vdouble d) { return svset3_f64(v, 1, d); }
static INLINE vdouble3 vd3setz_vd3_vd3_vd(vdouble3 v, vdouble d) { return svset3_f64(v, 2, d); }

//

typedef svfloat64x4_t tdx;

static INLINE vmask tdxgete_vm_tdx(tdx t) {
  return svreinterpret_s32_f64(svget4_f64(t, 0));
}
static INLINE vdouble3 tdxgetd3_vd3_tdx(tdx t) {
  return svcreate3_f64(svget4_f64(t, 1), svget4_f64(t, 2), svget4_f64(t, 3));
}
static INLINE vdouble tdxgetd3x_vd_tdx(tdx t) { return svget4_f64(t, 1); }
static INLINE vdouble tdxgetd3y_vd_tdx(tdx t) { return svget4_f64(t, 2); }
static INLINE vdouble tdxgetd3z_vd_tdx(tdx t) { return svget4_f64(t, 3); }
static INLINE tdx tdxsete_tdx_tdx_vm(tdx t, vmask e) {
  return svset4_f64(t, 0, svreinterpret_f64_s32(e));
}
static INLINE tdx tdxsetd3_tdx_tdx_vd3(tdx t, vdouble3 d3) {
  return svcreate4_f64(svget4_f64(t, 0), svget3_f64(d3, 0), svget3_f64(d3, 1), svget3_f64(d3, 2));
}
static INLINE tdx tdxsetx_tdx_tdx_vd(tdx t, vdouble x) { return svset4_f64(t, 1, x); }
static INLINE tdx tdxsety_tdx_tdx_vd(tdx t, vdouble y) { return svset4_f64(t, 2, y); }
static INLINE tdx tdxsetz_tdx_tdx_vd(tdx t, vdouble z) { return svset4_f64(t, 3, z); }
static INLINE tdx tdxsetxyz_tdx_tdx_vd_vd_vd(tdx t, vdouble x, vdouble y, vdouble z) {
  return svcreate4_f64(svget4_f64(t, 0), x, y, z);
}

static INLINE tdx tdxseted3_tdx_vm_vd3(vmask e, vdouble3 d3) {
  return svcreate4_f64(svreinterpret_f64_s32(e), svget3_f64(d3, 0), svget3_f64(d3, 1), svget3_f64(d3, 2));
}
static INLINE tdx tdxsetexyz_tdx_vm_vd_vd_vd(vmask e, vdouble x, vdouble y, vdouble z) {
  return svcreate4_f64(svreinterpret_f64_s32(e), x, y, z);
}

//

typedef svfloat64x4_t tdi_t;

static INLINE vdouble3 tdigettd_vd3_tdi(tdi_t d) {
  return svcreate3_f64(svget4_f64(d, 0), svget4_f64(d, 1), svget4_f64(d, 2));
}
static INLINE vdouble tdigetx_vd_tdi(tdi_t d) { return svget4_f64(d, 0); }
static INLINE vint tdigeti_vi_tdi(tdi_t d) { return svreinterpret_s32_f64(svget4_f64(d, 3)); }
static INLINE tdi_t tdisettdi_tdi_vd3_vi(vdouble3 v, vint i) {
  return svcreate4_f64(svget3_f64(v, 0), svget3_f64(v, 1), svget3_f64(v, 2),
                       svreinterpret_f64_s32(i));
}
static INLINE tdi_t tdisettd_tdi_tdi_vd3(tdi_t tdi, vdouble3 v) {
  return svcreate4_f64(svget3_f64(v, 0), svget3_f64(v, 1), svget3_f64(v, 2), svget4_f64(tdi, 3));
}

//

// masking predicates
#define ALL_TRUE_MASK svdup_n_s32(0xffffffff)
#define ALL_FALSE_MASK svdup_n_s32(0x0)
//@#define ALL_TRUE_MASK svdup_n_s32(0xffffffff)
//@#define ALL_FALSE_MASK svdup_n_s32(0x0)

static INLINE void vprefetch_v_p(const void *ptr) {}

//
//
//
// Test if all lanes are active
//
//
//
static INLINE int vtestallones_i_vo32(vopmask g) {
  svbool_t pg = svptrue_b32();
  return (svcntp_b32(pg, g) == svcntw());
}

static INLINE int vtestallones_i_vo64(vopmask g) {
  svbool_t pg = svptrue_b64();
  return (svcntp_b64(pg, g) == svcntd());
}
//
//
//
//
//
//

// Vector load / store
static INLINE void vstoreu_v_p_vi2(int32_t *p, vint2 v) { svst1_s32(ptrue, p, v); }

static INLINE vfloat vload_vf_p(const float *ptr) {
  return svld1_f32(ptrue, ptr);
}
static INLINE vfloat vloadu_vf_p(const float *ptr) {
  return svld1_f32(ptrue, ptr);
}
static INLINE void vstoreu_v_p_vf(float *ptr, vfloat v) {
  svst1_f32(ptrue, ptr, v);
}

// Basic logical operations for mask
static INLINE vmask vand_vm_vm_vm(vmask x, vmask y) {
  return svand_s32_x(ptrue, x, y);
}
static INLINE vmask vandnot_vm_vm_vm(vmask x, vmask y) {
  return svbic_s32_x(ptrue, y, x);
}
static INLINE vmask vor_vm_vm_vm(vmask x, vmask y) {
  return svorr_s32_x(ptrue, x, y);
}
static INLINE vmask vxor_vm_vm_vm(vmask x, vmask y) {
  return sveor_s32_x(ptrue, x, y);
}

static INLINE vmask vadd64_vm_vm_vm(vmask x, vmask y) {
  return svreinterpret_s32_s64(
           svadd_s64_x(ptrue, svreinterpret_s64_s32(x),
                              svreinterpret_s64_s32(y)));
}

// Mask <--> single precision reinterpret
static INLINE vmask vreinterpret_vm_vf(vfloat vf) {
  return svreinterpret_s32_f32(vf);
}
static INLINE vfloat vreinterpret_vf_vm(vmask vm) {
  return svreinterpret_f32_s32(vm);
}
static INLINE vfloat vreinterpret_vf_vi2(vint2 vm) {
  return svreinterpret_f32_s32(vm);
}
static INLINE vint2 vreinterpret_vi2_vf(vfloat vf) {
  return svreinterpret_s32_f32(vf);
}
static INLINE vint2 vcast_vi2_vm(vmask vm) { return vm; }
static INLINE vmask vcast_vm_vi2(vint2 vi) { return vi; }

// Conditional select
static INLINE vint2 vsel_vi2_vm_vi2_vi2(vmask m, vint2 x, vint2 y) {
  return svsel_s32(svcmpeq_s32(ptrue, m, ALL_TRUE_MASK), x, y);
}

/****************************************/
/* Single precision FP operations */
/****************************************/
// Broadcast
static INLINE vfloat vcast_vf_f(float f) { return svdup_n_f32(f); }

// Add, Sub, Mul
static INLINE vfloat vadd_vf_vf_vf(vfloat x, vfloat y) {
  return svadd_f32_x(ptrue, x, y);
}
static INLINE vfloat vsub_vf_vf_vf(vfloat x, vfloat y) {
  return svsub_f32_x(ptrue, x, y);
}
static INLINE vfloat vmul_vf_vf_vf(vfloat x, vfloat y) {
  return svmul_f32_x(ptrue, x, y);
}

// |x|, -x
static INLINE vfloat vabs_vf_vf(vfloat f) { return svabs_f32_x(ptrue, f); }
static INLINE vfloat vneg_vf_vf(vfloat f) { return svneg_f32_x(ptrue, f); }

// max, min
static INLINE vfloat vmax_vf_vf_vf(vfloat x, vfloat y) {
  return svmax_f32_x(ptrue, x, y);
}
static INLINE vfloat vmin_vf_vf_vf(vfloat x, vfloat y) {
  return svmin_f32_x(ptrue, x, y);
}

// int <--> float conversions
static INLINE vint2 vtruncate_vi2_vf(vfloat vf) {
  return svcvt_s32_f32_x(ptrue, vf);
}
static INLINE vfloat vcast_vf_vi2(vint2 vi) {
  return svcvt_f32_s32_x(ptrue, vi);
}
static INLINE vint2 vcast_vi2_i(int i) { return svdup_n_s32(i); }
static INLINE vint2 vrint_vi2_vf(vfloat d) {
  return svcvt_s32_f32_x(ptrue, svrintn_f32_x(ptrue, d));
}

#if CONFIG == 1
// Multiply accumulate: z = z + x * y
static INLINE vfloat vmla_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) {
  return svmad_f32_x(ptrue, x, y, z);
}
// Multiply subtract: z = z - x * y
static INLINE vfloat vmlanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) {
  return svmsb_f32_x(ptrue, x, y, z);
}
static INLINE vfloat vmlapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) {
  return svnmsb_f32_x(ptrue, x, y, z);
}
#else
static INLINE vfloat vmla_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vadd_vf_vf_vf(vmul_vf_vf_vf(x, y), z); }
static INLINE vfloat vmlanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vsub_vf_vf_vf(z, vmul_vf_vf_vf(x, y)); }
static INLINE vfloat vmlapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vsub_vf_vf_vf(vmul_vf_vf_vf(x, y), z); }
#endif

// fused multiply add / sub
static INLINE vfloat vfma_vf_vf_vf_vf(vfloat x, vfloat y,
                                      vfloat z) { // z + x * y
  return svmad_f32_x(ptrue, x, y, z);
}
static INLINE vfloat vfmanp_vf_vf_vf_vf(vfloat x, vfloat y,
                                        vfloat z) { // z - x * y
  return svmsb_f32_x(ptrue, x, y, z);
}
static INLINE vfloat vfmapn_vf_vf_vf_vf(vfloat x, vfloat y,
                                        vfloat z) { // x * y - z
  return svnmsb_f32_x(ptrue, x, y, z);
}

// conditional select
static INLINE vfloat vsel_vf_vo_vf_vf(vopmask mask, vfloat x, vfloat y) {
  return svsel_f32(mask, x, y);
}

// Reciprocal 1/x, Division, Square root
static INLINE vfloat vdiv_vf_vf_vf(vfloat n, vfloat d) {
#ifndef SLEEF_ENABLE_ALTDIV
  return svdiv_f32_x(ptrue, n, d);
#else
  // Finite numbers (including denormal) only, gives mostly correctly rounded result
  vfloat t, u, x, y;
  svuint32_t i0, i1;
  i0 = svand_u32_x(ptrue, svreinterpret_u32_f32(n), svdup_n_u32(0x7c000000));
  i1 = svand_u32_x(ptrue, svreinterpret_u32_f32(d), svdup_n_u32(0x7c000000));
  i0 = svsub_u32_x(ptrue, svdup_n_u32(0x7d000000), svlsr_n_u32_x(ptrue, svadd_u32_x(ptrue, i0, i1), 1));
  t = svreinterpret_f32_u32(i0);
  y = svmul_f32_x(ptrue, d, t);
  x = svmul_f32_x(ptrue, n, t);
  t = svrecpe_f32(y);
  t = svmul_f32_x(ptrue, t, svrecps_f32(y, t));
  t = svmul_f32_x(ptrue, t, svrecps_f32(y, t));
  u = svmul_f32_x(ptrue, x, t);
  u = svmad_f32_x(ptrue, svmsb_f32_x(ptrue, y, u, x), t, u);
  return u;
#endif
}
static INLINE vfloat vrec_vf_vf(vfloat d) {
#ifndef SLEEF_ENABLE_ALTDIV
  return svdivr_n_f32_x(ptrue, d, 1.0f);
#else
  return vsel_vf_vo_vf_vf(svcmpeq_f32(ptrue, vabs_vf_vf(d), vcast_vf_f(SLEEF_INFINITYf)),
                          vcast_vf_f(0), vdiv_vf_vf_vf(vcast_vf_f(1.0f), d));
#endif
}
static INLINE vfloat vsqrt_vf_vf(vfloat d) {
#ifndef SLEEF_ENABLE_ALTSQRT
  return svsqrt_f32_x(ptrue, d);
#else
  // Gives correctly rounded result for all input range
  vfloat w, x, y, z;

  y = svrsqrte_f32(d);
  x = vmul_vf_vf_vf(d, y);         w = vmul_vf_vf_vf(vcast_vf_f(0.5), y);
  y = vfmanp_vf_vf_vf_vf(x, w, vcast_vf_f(0.5));
  x = vfma_vf_vf_vf_vf(x, y, x);   w = vfma_vf_vf_vf_vf(w, y, w);

  y = vfmanp_vf_vf_vf_vf(x, w, vcast_vf_f(1.5));  w = vadd_vf_vf_vf(w, w);
  w = vmul_vf_vf_vf(w, y);
  x = vmul_vf_vf_vf(w, d);
  y = vfmapn_vf_vf_vf_vf(w, d, x); z = vfmanp_vf_vf_vf_vf(w, x, vcast_vf_f(1));
  z = vfmanp_vf_vf_vf_vf(w, y, z); w = vmul_vf_vf_vf(vcast_vf_f(0.5), x);
  w = vfma_vf_vf_vf_vf(w, z, y);
  w = vadd_vf_vf_vf(w, x);

  return svsel_f32(svorr_b_z(ptrue, svcmpeq_f32(ptrue, d, vcast_vf_f(0)),
                             svcmpeq_f32(ptrue, d, vcast_vf_f(SLEEF_INFINITYf))), d, w);
#endif
}
//
//
//
//
//
//
static INLINE CONST vfloat vsel_vf_vo_f_f(vopmask o, float v1, float v0) {
  return vsel_vf_vo_vf_vf(o, vcast_vf_f(v1), vcast_vf_f(v0));
}

static INLINE vfloat vsel_vf_vo_vo_f_f_f(vopmask o0, vopmask o1, float d0, float d1, float d2) {
  return vsel_vf_vo_vf_vf(o0, vcast_vf_f(d0), vsel_vf_vo_f_f(o1, d1, d2));
}

static INLINE vfloat vsel_vf_vo_vo_vo_f_f_f_f(vopmask o0, vopmask o1, vopmask o2, float d0, float d1, float d2, float d3) {
  return vsel_vf_vo_vf_vf(o0, vcast_vf_f(d0), vsel_vf_vo_vf_vf(o1, vcast_vf_f(d1), vsel_vf_vo_f_f(o2, d2, d3)));
}
//
//
//
//
//
//

// truncate
static INLINE vfloat vtruncate_vf_vf(vfloat vd) {
  return svrintz_f32_x(ptrue, vd);
}

//
//
//
// Round float
//
//
//
static INLINE vfloat vrint_vf_vf(vfloat vf) {
  return svrintn_f32_x(svptrue_b32(), vf);
}
//
//
//
//
//
//

/***************************************/
/* Single precision integer operations */
/***************************************/

// Add, Sub, Neg (-x)
static INLINE vint2 vadd_vi2_vi2_vi2(vint2 x, vint2 y) {
  return svadd_s32_x(ptrue, x, y);
}
static INLINE vint2 vsub_vi2_vi2_vi2(vint2 x, vint2 y) {
  return svsub_s32_x(ptrue, x, y);
}
static INLINE vint2 vneg_vi2_vi2(vint2 e) { return svneg_s32_x(ptrue, e); }

// Logical operations
static INLINE vint2 vand_vi2_vi2_vi2(vint2 x, vint2 y) {
  return svand_s32_x(ptrue, x, y);
}
static INLINE vint2 vandnot_vi2_vi2_vi2(vint2 x, vint2 y) {
  return svbic_s32_x(ptrue, y, x);
}
static INLINE vint2 vor_vi2_vi2_vi2(vint2 x, vint2 y) {
  return svorr_s32_x(ptrue, x, y);
}
static INLINE vint2 vxor_vi2_vi2_vi2(vint2 x, vint2 y) {
  return sveor_s32_x(ptrue, x, y);
}

// Shifts
#define vsll_vi2_vi2_i(x, c) svlsl_n_s32_x(ptrue, x, c)
//@#define vsll_vi2_vi2_i(x, c) svlsl_n_s32_x(ptrue, x, c)
#define vsrl_vi2_vi2_i(x, c)                                                   \
  svreinterpret_s32_u32(svlsr_n_u32_x(ptrue, svreinterpret_u32_s32(x), c))
//@#define vsrl_vi2_vi2_i(x, c) svreinterpret_s32_u32(svlsr_n_u32_x(ptrue, svreinterpret_u32_s32(x), c))
#define vsra_vi2_vi2_i(x, c) svasr_n_s32_x(ptrue, x, c)
//@#define vsra_vi2_vi2_i(x, c) svasr_n_s32_x(ptrue, x, c)

// Comparison returning integers
static INLINE vint2 vgt_vi2_vi2_vi2(vint2 x, vint2 y) {
  return svsel_s32(svcmpgt_s32(ptrue, x, y), ALL_TRUE_MASK, ALL_FALSE_MASK);
}

// conditional select
static INLINE vint2 vsel_vi2_vo_vi2_vi2(vopmask m, vint2 x, vint2 y) {
  return svsel_s32(m, x, y);
}

/****************************************/
/* opmask operations                    */
/****************************************/
// single precision FP
static INLINE vopmask veq_vo_vf_vf(vfloat x, vfloat y) {
  return svcmpeq_f32(ptrue, x, y);
}
static INLINE vopmask vneq_vo_vf_vf(vfloat x, vfloat y) {
  return svcmpne_f32(ptrue, x, y);
}
static INLINE vopmask vlt_vo_vf_vf(vfloat x, vfloat y) {
  return svcmplt_f32(ptrue, x, y);
}
static INLINE vopmask vle_vo_vf_vf(vfloat x, vfloat y) {
  return svcmple_f32(ptrue, x, y);
}
static INLINE vopmask vgt_vo_vf_vf(vfloat x, vfloat y) {
  return svcmpgt_f32(ptrue, x, y);
}
static INLINE vopmask vge_vo_vf_vf(vfloat x, vfloat y) {
  return svcmpge_f32(ptrue, x, y);
}
static INLINE vopmask visinf_vo_vf(vfloat d) {
  return svcmpeq_n_f32(ptrue, vabs_vf_vf(d), SLEEF_INFINITYf);
}
static INLINE vopmask vispinf_vo_vf(vfloat d) {
  return svcmpeq_n_f32(ptrue, d, SLEEF_INFINITYf);
}
static INLINE vopmask visminf_vo_vf(vfloat d) {
  return svcmpeq_n_f32(ptrue, d, -SLEEF_INFINITYf);
}
static INLINE vopmask visnan_vo_vf(vfloat d) { return vneq_vo_vf_vf(d, d); }

// integers
static INLINE vopmask veq_vo_vi2_vi2(vint2 x, vint2 y) {
  return svcmpeq_s32(ptrue, x, y);
}
static INLINE vopmask vgt_vo_vi2_vi2(vint2 x, vint2 y) {
  return svcmpgt_s32(ptrue, x, y);
}

// logical opmask
static INLINE vopmask vand_vo_vo_vo(vopmask x, vopmask y) {
  return svand_b_z(ptrue, x, y);
}
static INLINE vopmask vandnot_vo_vo_vo(vopmask x, vopmask y) {
  return svbic_b_z(ptrue, y, x);
}
static INLINE vopmask vor_vo_vo_vo(vopmask x, vopmask y) {
  return svorr_b_z(ptrue, x, y);
}
static INLINE vopmask vxor_vo_vo_vo(vopmask x, vopmask y) {
  return sveor_b_z(ptrue, x, y);
}

static INLINE vint2 vand_vi2_vo_vi2(vopmask x, vint2 y) {
  // This needs to be zeroing to prevent asinf and atanf denormal test
  // failing.
  return svand_s32_z(x, y, y);
}

// bitmask logical operations
static INLINE vmask vand_vm_vo32_vm(vopmask x, vmask y) {
  return svsel_s32(x, y, ALL_FALSE_MASK);
}
static INLINE vmask vandnot_vm_vo32_vm(vopmask x, vmask y) {
  return svsel_s32(x, ALL_FALSE_MASK, y);
}
static INLINE vmask vor_vm_vo32_vm(vopmask x, vmask y) {
  return svsel_s32(x, ALL_TRUE_MASK, y);
}

// broadcast bitmask
static INLINE vmask vcast_vm_i_i(int i0, int i1) {
  return svreinterpret_s32_u64(
      svdup_n_u64((0xffffffff & (uint64_t)i1) | (((uint64_t)i0) << 32)));
}

static INLINE vmask vcast_vm_i64(int64_t i) {
  return svreinterpret_s32_u64(svdup_n_u64((uint64_t)i));
}
static INLINE vmask vcast_vm_u64(uint64_t i) {
  return svreinterpret_s32_u64(svdup_n_u64(i));
}

/*********************************/
/* SVE for double precision math */
/*********************************/

// Vector load/store
static INLINE vdouble vload_vd_p(const double *ptr) {
  return svld1_f64(ptrue, ptr);
}
static INLINE vdouble vloadu_vd_p(const double *ptr) {
  return svld1_f64(ptrue, ptr);
}
static INLINE void vstoreu_v_p_vd(double *ptr, vdouble v) {
  svst1_f64(ptrue, ptr, v);
}

static INLINE void vstoreu_v_p_vi(int *ptr, vint v) {
  svst1w_s64(ptrue, ptr, svreinterpret_s64_s32(v));
}
static vint vloadu_vi_p(int32_t *p) {
  return svreinterpret_s32_s64(svld1uw_s64(ptrue, (uint32_t *)p));
}

// Reinterpret
static INLINE vdouble vreinterpret_vd_vm(vmask vm) {
  return svreinterpret_f64_s32(vm);
}
static INLINE vmask vreinterpret_vm_vd(vdouble vd) {
  return svreinterpret_s32_f64(vd);
}
static INLINE vint2 vcastu_vm_vi(vint x) {
  return svreinterpret_s32_s64(
      svlsl_n_s64_x(ptrue, svreinterpret_s64_s32(x), 32));
}
static INLINE vint vcastu_vi_vm(vint2 x) {
  return svreinterpret_s32_u64(
      svlsr_n_u64_x(ptrue, svreinterpret_u64_s32(x), 32));
}
static INLINE vdouble vcast_vd_vi(vint vi) {
  return svcvt_f64_s32_x(ptrue, vi);
}

// Splat
static INLINE vdouble vcast_vd_d(double d) { return svdup_n_f64(d); }

// Conditional select
static INLINE vdouble vsel_vd_vo_vd_vd(vopmask o, vdouble x, vdouble y) {
  return svsel_f64(o, x, y);
}

static INLINE CONST vdouble vsel_vd_vo_d_d(vopmask o, double v1, double v0) {
  return vsel_vd_vo_vd_vd(o, vcast_vd_d(v1), vcast_vd_d(v0));
}

static INLINE vdouble vsel_vd_vo_vo_d_d_d(vopmask o0, vopmask o1, double d0, double d1, double d2) {
  return vsel_vd_vo_vd_vd(o0, vcast_vd_d(d0), vsel_vd_vo_d_d(o1, d1, d2));
}

static INLINE vdouble vsel_vd_vo_vo_vo_d_d_d_d(vopmask o0, vopmask o1, vopmask o2, double d0, double d1, double d2, double d3) {
  return vsel_vd_vo_vd_vd(o0, vcast_vd_d(d0), vsel_vd_vo_vd_vd(o1, vcast_vd_d(d1), vsel_vd_vo_d_d(o2, d2, d3)));
}

static INLINE vint vsel_vi_vo_vi_vi(vopmask o, vint x, vint y) {
  return svsel_s32(o, x, y);
}
// truncate
static INLINE vdouble vtruncate_vd_vd(vdouble vd) {
  return svrintz_f64_x(ptrue, vd);
}
static INLINE vint vtruncate_vi_vd(vdouble vd) {
  return svcvt_s32_f64_x(ptrue, vd);
}
static INLINE vint vrint_vi_vd(vdouble vd) {
  return svcvt_s32_f64_x(ptrue, svrintn_f64_x(ptrue, vd));
}
static INLINE vdouble vrint_vd_vd(vdouble vd) {
  return svrintn_f64_x(ptrue, vd);
}

// FP math operations
static INLINE vdouble vadd_vd_vd_vd(vdouble x, vdouble y) {
  return svadd_f64_x(ptrue, x, y);
}
static INLINE vdouble vsub_vd_vd_vd(vdouble x, vdouble y) {
  return svsub_f64_x(ptrue, x, y);
}
static INLINE vdouble vneg_vd_vd(vdouble x) { return svneg_f64_x(ptrue, x); }
static INLINE vdouble vmul_vd_vd_vd(vdouble x, vdouble y) {
  return svmul_f64_x(ptrue, x, y);
}
static INLINE vdouble vabs_vd_vd(vdouble x) { return svabs_f64_x(ptrue, x); }
static INLINE vdouble vmax_vd_vd_vd(vdouble x, vdouble y) {
  return svmax_f64_x(ptrue, x, y);
}
static INLINE vdouble vmin_vd_vd_vd(vdouble x, vdouble y) {
  return svmin_f64_x(ptrue, x, y);
}

#if CONFIG == 1
// Multiply accumulate / subtract
static INLINE vdouble vmla_vd_vd_vd_vd(vdouble x, vdouble y,
                                       vdouble z) { // z = x*y + z
  return svmad_f64_x(ptrue, x, y, z);
}
static INLINE vdouble vmlapn_vd_vd_vd_vd(vdouble x, vdouble y,
                                         vdouble z) { // z = x * y - z
  return svnmsb_f64_x(ptrue, x, y, z);
}
static INLINE vdouble vmlanp_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) {
  return svmsb_f64_x(ptrue, x, y, z);
}
#else
static INLINE vdouble vmla_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return vadd_vd_vd_vd(vmul_vd_vd_vd(x, y), z); }
static INLINE vdouble vmlapn_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return vsub_vd_vd_vd(vmul_vd_vd_vd(x, y), z); }
#endif

static INLINE vdouble vfma_vd_vd_vd_vd(vdouble x, vdouble y,
                                       vdouble z) { // z + x * y
  return svmad_f64_x(ptrue, x, y, z);
}
static INLINE vdouble vfmanp_vd_vd_vd_vd(vdouble x, vdouble y,
                                         vdouble z) { // z - x * y
  return svmsb_f64_x(ptrue, x, y, z);
}
static INLINE vdouble vfmapn_vd_vd_vd_vd(vdouble x, vdouble y,
                                         vdouble z) { // x * y - z
  return svnmsb_f64_x(ptrue, x, y, z);
}

// Reciprocal 1/x, Division, Square root
static INLINE vdouble vdiv_vd_vd_vd(vdouble n, vdouble d) {
#ifndef SLEEF_ENABLE_ALTDIV
  return svdiv_f64_x(ptrue, n, d);
#else
  // Finite numbers (including denormal) only, gives mostly correctly rounded result
  vdouble t, u, x, y;
  svuint64_t i0, i1;
  i0 = svand_u64_x(ptrue, svreinterpret_u64_f64(n), svdup_n_u64(0x7fc0000000000000L));
  i1 = svand_u64_x(ptrue, svreinterpret_u64_f64(d), svdup_n_u64(0x7fc0000000000000L));
  i0 = svsub_u64_x(ptrue, svdup_n_u64(0x7fd0000000000000L), svlsr_n_u64_x(ptrue, svadd_u64_x(ptrue, i0, i1), 1));
  t = svreinterpret_f64_u64(i0);
  y = svmul_f64_x(ptrue, d, t);
  x = svmul_f64_x(ptrue, n, t);
  t = svrecpe_f64(y);
  t = svmul_f64_x(ptrue, t, svrecps_f64(y, t));
  t = svmul_f64_x(ptrue, t, svrecps_f64(y, t));
  t = svmul_f64_x(ptrue, t, svrecps_f64(y, t));
  u = svmul_f64_x(ptrue, x, t);
  u = svmad_f64_x(ptrue, svmsb_f64_x(ptrue, y, u, x), t, u);
  return u;
#endif
}
static INLINE vdouble vrec_vd_vd(vdouble d) {
#ifndef SLEEF_ENABLE_ALTDIV
  return svdivr_n_f64_x(ptrue, d, 1.0);
#else
  return vsel_vd_vo_vd_vd(svcmpeq_f64(ptrue, vabs_vd_vd(d), vcast_vd_d(SLEEF_INFINITY)),
                          vcast_vd_d(0), vdiv_vd_vd_vd(vcast_vd_d(1.0f), d));
#endif
}
static INLINE vdouble vsqrt_vd_vd(vdouble d) {
#ifndef SLEEF_ENABLE_ALTSQRT
  return svsqrt_f64_x(ptrue, d);
#else
  // Gives correctly rounded result for all input range
  vdouble w, x, y, z;

  y = svrsqrte_f64(d);
  x = vmul_vd_vd_vd(d, y);         w = vmul_vd_vd_vd(vcast_vd_d(0.5), y);
  y = vfmanp_vd_vd_vd_vd(x, w, vcast_vd_d(0.5));
  x = vfma_vd_vd_vd_vd(x, y, x);   w = vfma_vd_vd_vd_vd(w, y, w);
  y = vfmanp_vd_vd_vd_vd(x, w, vcast_vd_d(0.5));
  x = vfma_vd_vd_vd_vd(x, y, x);   w = vfma_vd_vd_vd_vd(w, y, w);

  y = vfmanp_vd_vd_vd_vd(x, w, vcast_vd_d(1.5));  w = vadd_vd_vd_vd(w, w);
  w = vmul_vd_vd_vd(w, y);
  x = vmul_vd_vd_vd(w, d);
  y = vfmapn_vd_vd_vd_vd(w, d, x); z = vfmanp_vd_vd_vd_vd(w, x, vcast_vd_d(1));
  z = vfmanp_vd_vd_vd_vd(w, y, z); w = vmul_vd_vd_vd(vcast_vd_d(0.5), x);
  w = vfma_vd_vd_vd_vd(w, z, y);
  w = vadd_vd_vd_vd(w, x);

  return svsel_f64(svorr_b_z(ptrue, svcmpeq_f64(ptrue, d, vcast_vd_d(0)),
                             svcmpeq_f64(ptrue, d, vcast_vd_d(SLEEF_INFINITY))), d, w);
#endif
}

// Float comparison
static INLINE vopmask vlt_vo_vd_vd(vdouble x, vdouble y) {
  return svcmplt_f64(ptrue, x, y);
}
static INLINE vopmask veq_vo_vd_vd(vdouble x, vdouble y) {
  return svcmpeq_f64(ptrue, x, y);
}
static INLINE vopmask vgt_vo_vd_vd(vdouble x, vdouble y) {
  return svcmpgt_f64(ptrue, x, y);
}
static INLINE vopmask vge_vo_vd_vd(vdouble x, vdouble y) {
  return svcmpge_f64(ptrue, x, y);
}
static INLINE vopmask vneq_vo_vd_vd(vdouble x, vdouble y) {
  return svcmpne_f64(ptrue, x, y);
}
static INLINE vopmask vle_vo_vd_vd(vdouble x, vdouble y) {
  return svcmple_f64(ptrue, x, y);
}

// predicates
static INLINE vopmask visnan_vo_vd(vdouble vd) {
  return svcmpne_f64(ptrue, vd, vd);
}
static INLINE vopmask visinf_vo_vd(vdouble vd) {
  return svcmpeq_n_f64(ptrue, svabs_f64_x(ptrue, vd), SLEEF_INFINITY);
}
static INLINE vopmask vispinf_vo_vd(vdouble vd) {
  return svcmpeq_n_f64(ptrue, vd, SLEEF_INFINITY);
}
static INLINE vopmask visminf_vo_vd(vdouble vd) {
  return svcmpeq_n_f64(ptrue, vd, -SLEEF_INFINITY);
}

// Comparing bit masks
static INLINE vopmask veq64_vo_vm_vm(vmask x, vmask y) {
  return svcmpeq_s64(ptrue, svreinterpret_s64_s32(x), svreinterpret_s64_s32(y));
}

// pure predicate operations
static INLINE vopmask vcast_vo32_vo64(vopmask o) { return o; }
static INLINE vopmask vcast_vo64_vo32(vopmask o) { return o; }
static INLINE vopmask vcast_vo_i(int i) { return svcmpne_s32(ptrue, svdup_n_s32(i), svdup_n_s32(0)); }

// logical integer operations
static INLINE vint vand_vi_vo_vi(vopmask x, vint y) {
  // This needs to be a zeroing instruction because we need to make
  // sure that the inactive elements for the unpacked integers vector
  // are zero.
  return svand_s32_z(x, y, y);
}

static INLINE vint vandnot_vi_vo_vi(vopmask x, vint y) {
  return svsel_s32(x, ALL_FALSE_MASK, y);
}
#define vsra_vi_vi_i(x, c) svasr_n_s32_x(ptrue, x, c)
//@#define vsra_vi_vi_i(x, c) svasr_n_s32_x(ptrue, x, c)
#define vsll_vi_vi_i(x, c) svlsl_n_s32_x(ptrue, x, c)
//@#define vsll_vi_vi_i(x, c) svlsl_n_s32_x(ptrue, x, c)

static INLINE vint vsrl_vi_vi_i(vint x, int c) {
  return svreinterpret_s32_u32(svlsr_n_u32_x(ptrue, svreinterpret_u32_s32(x), c));
}

static INLINE vint vand_vi_vi_vi(vint x, vint y) {
  return svand_s32_x(ptrue, x, y);
}
static INLINE vint vandnot_vi_vi_vi(vint x, vint y) {
  return svbic_s32_x(ptrue, y, x);
}
static INLINE vint vxor_vi_vi_vi(vint x, vint y) {
  return sveor_s32_x(ptrue, x, y);
}

// integer math
static INLINE vint vadd_vi_vi_vi(vint x, vint y) {
  return svadd_s32_x(ptrue, x, y);
}
static INLINE vint vsub_vi_vi_vi(vint x, vint y) {
  return svsub_s32_x(ptrue, x, y);
}
static INLINE vint vneg_vi_vi(vint x) { return svneg_s32_x(ptrue, x); }

// integer comparison
static INLINE vopmask vgt_vo_vi_vi(vint x, vint y) {
  return svcmpgt_s32(ptrue, x, y);
}
static INLINE vopmask veq_vo_vi_vi(vint x, vint y) {
  return svcmpeq_s32(ptrue, x, y);
}

// Splat
static INLINE vint vcast_vi_i(int i) { return svdup_n_s32(i); }

// bitmask logical operations
static INLINE vmask vand_vm_vo64_vm(vopmask x, vmask y) {
  // This needs to be a zeroing instruction because we need to make
  // sure that the inactive elements for the unpacked integers vector
  // are zero.
  return svreinterpret_s32_s64(
      svand_s64_z(x, svreinterpret_s64_s32(y), svreinterpret_s64_s32(y)));
}
static INLINE vmask vandnot_vm_vo64_vm(vopmask x, vmask y) {
  return svreinterpret_s32_s64(svsel_s64(
      x, svreinterpret_s64_s32(ALL_FALSE_MASK), svreinterpret_s64_s32(y)));
}
static INLINE vmask vor_vm_vo64_vm(vopmask x, vmask y) {
  return svreinterpret_s32_s64(svsel_s64(
      x, svreinterpret_s64_s32(ALL_TRUE_MASK), svreinterpret_s64_s32(y)));
}

static INLINE vfloat vrev21_vf_vf(vfloat vf) {
  return svreinterpret_f32_u64(svrevw_u64_x(ptrue, svreinterpret_u64_f32(vf)));
}

// Comparison returning integer
static INLINE vint2 veq_vi2_vi2_vi2(vint2 x, vint2 y) {
  return svsel_s32(svcmpeq_s32(ptrue, x, y), ALL_TRUE_MASK, ALL_FALSE_MASK);
}

// Gather

static INLINE vdouble vgather_vd_p_vi(const double *ptr, vint vi) {
  return svld1_gather_s64index_f64(ptrue, ptr, svreinterpret_s64_s32(vi));
}

static INLINE vfloat vgather_vf_p_vi2(const float *ptr, vint2 vi2) {
  return svld1_gather_s32index_f32(ptrue, ptr, vi2);
}

// Operations for DFT

static INLINE vdouble vposneg_vd_vd(vdouble d) {
  return svneg_f64_m(d, svdupq_n_b64(0, 1), d);
}

static INLINE vdouble vnegpos_vd_vd(vdouble d) {
  return svneg_f64_m(d, svdupq_n_b64(1, 0), d);
}

static INLINE vfloat vposneg_vf_vf(vfloat d) {
  return svneg_f32_m(d, svdupq_n_b32(0, 1, 0, 1), d);
}

static INLINE vfloat vnegpos_vf_vf(vfloat d) {
  return svneg_f32_m(d, svdupq_n_b32(1, 0, 1, 0), d);
}

static INLINE vdouble vsubadd_vd_vd_vd(vdouble x, vdouble y) { return vadd_vd_vd_vd(x, vnegpos_vd_vd(y)); }
static INLINE vfloat vsubadd_vf_vf_vf(vfloat d0, vfloat d1) { return vadd_vf_vf_vf(d0, vnegpos_vf_vf(d1)); }
static INLINE vdouble vmlsubadd_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return vfma_vd_vd_vd_vd(x, y, vnegpos_vd_vd(z)); }
static INLINE vfloat vmlsubadd_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vfma_vf_vf_vf_vf(x, y, vnegpos_vf_vf(z)); }

//

static INLINE vdouble vrev21_vd_vd(vdouble x) { return svzip1_f64(svuzp2_f64(x, x), svuzp1_f64(x, x)); }

static INLINE vdouble vreva2_vd_vd(vdouble vd) {
  svint64_t x = svindex_s64((VECTLENDP-1), -1);
  x = svzip1_s64(svuzp2_s64(x, x), svuzp1_s64(x, x));
  return svtbl_f64(vd, svreinterpret_u64_s64(x));
}

static INLINE vfloat vreva2_vf_vf(vfloat vf) {
  svint32_t x = svindex_s32((VECTLENSP-1), -1);
  x = svzip1_s32(svuzp2_s32(x, x), svuzp1_s32(x, x));
  return svtbl_f32(vf, svreinterpret_u32_s32(x));
}

//

static INLINE void vscatter2_v_p_i_i_vd(double *ptr, int offset, int step, vdouble v) {
  svst1_scatter_u64index_f64(ptrue, ptr + offset*2, svzip1_u64(svindex_u64(0, step*2), svindex_u64(1, step*2)), v);
}

static INLINE void vscatter2_v_p_i_i_vf(float *ptr, int offset, int step, vfloat v) {
  svst1_scatter_u32index_f32(ptrue, ptr + offset*2, svzip1_u32(svindex_u32(0, step*2), svindex_u32(1, step*2)), v);
}

static INLINE void vstore_v_p_vd(double *ptr, vdouble v) { vstoreu_v_p_vd(ptr, v); }
static INLINE void vstream_v_p_vd(double *ptr, vdouble v) { vstore_v_p_vd(ptr, v); }
static INLINE void vstore_v_p_vf(float *ptr, vfloat v) { vstoreu_v_p_vf(ptr, v); }
static INLINE void vstream_v_p_vf(float *ptr, vfloat v) { vstore_v_p_vf(ptr, v); }
static INLINE void vsscatter2_v_p_i_i_vd(double *ptr, int offset, int step, vdouble v) { vscatter2_v_p_i_i_vd(ptr, offset, step, v); }
static INLINE void vsscatter2_v_p_i_i_vf(float *ptr, int offset, int step, vfloat v) { vscatter2_v_p_i_i_vf(ptr, offset, step, v); }

// These functions are for debugging
static double vcast_d_vd(vdouble v) {
  double a[svcntd()];
  vstoreu_v_p_vd(a, v);
  return a[0];
}

static float vcast_f_vf(vfloat v) {
  float a[svcntw()];
  vstoreu_v_p_vf(a, v);
  return a[0];
}

static int vcast_i_vi(vint v) {
  int a[svcntw()];
  vstoreu_v_p_vi(a, v);
  return a[0];
}

static int vcast_i_vi2(vint2 v) {
  int a[svcntw()];
  vstoreu_v_p_vi2(a, v);
  return a[0];
}

//

static vquad loadu_vq_p(const int32_t *ptr) {
  int32_t a[svcntw()*2];
  memcpy(a, ptr, svcntw()*8);
  return svld2_s32(ptrue, a);
}

static INLINE vquad cast_vq_aq(vargquad aq) { return aq; }
static INLINE vargquad cast_aq_vq(vquad vq) { return vq; }

static INLINE int vtestallzeros_i_vo64(vopmask g) {
  return svcntp_b64(svptrue_b64(), g) == 0;
}

static INLINE vmask vsel_vm_vo64_vm_vm(vopmask o, vmask x, vmask y) {
  return svreinterpret_s32_s64(svsel_s64(o, svreinterpret_s64_s32(x), svreinterpret_s64_s32(y)));
}

static INLINE vmask vsub64_vm_vm_vm(vmask x, vmask y) {
  return svreinterpret_s32_s64(
           svsub_s64_x(ptrue, svreinterpret_s64_s32(x),
                              svreinterpret_s64_s32(y)));
}

static INLINE vmask vneg64_vm_vm(vmask x) {
  return svreinterpret_s32_s64(svneg_s64_x(ptrue, svreinterpret_s64_s32(x)));
}

static INLINE vopmask vgt64_vo_vm_vm(vmask x, vmask y) {
  return svcmpgt_s64(ptrue, svreinterpret_s64_s32(x), svreinterpret_s64_s32(y));
}

#define vsll64_vm_vm_i(x, c) svreinterpret_s32_u64(svlsl_n_u64_x(ptrue, svreinterpret_u64_s32(x), c))
//@#define vsll64_vm_vm_i(x, c) svreinterpret_s32_u64(svlsl_n_u64_x(ptrue, svreinterpret_u64_s32(x), c))
#define vsrl64_vm_vm_i(x, c) svreinterpret_s32_u64(svlsr_n_u64_x(ptrue, svreinterpret_u64_s32(x), c))
//@#define vsrl64_vm_vm_i(x, c) svreinterpret_s32_u64(svlsr_n_u64_x(ptrue, svreinterpret_u64_s32(x), c))

static INLINE vmask vcast_vm_vi(vint vi) { return svreinterpret_s32_s64(svextw_s64_z(ptrue, svreinterpret_s64_s32(vi))); }
static INLINE vint vcast_vi_vm(vmask vm) { return vand_vm_vm_vm(vm, vcast_vm_i_i(0, 0xffffffff)); }

static INLINE vmask vreinterpret_vm_vi64(vint64 v)  { return svreinterpret_s32_s64(v); }
static INLINE vint64 vreinterpret_vi64_vm(vmask m)  { return svreinterpret_s64_s32(m); }
static INLINE vmask vreinterpret_vm_vu64(vuint64 v) { return svreinterpret_s32_u64(v); }
static INLINE vuint64 vreinterpret_vu64_vm(vmask m) { return svreinterpret_u64_s32(m); }
