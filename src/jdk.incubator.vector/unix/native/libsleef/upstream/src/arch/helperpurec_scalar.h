//   Copyright Naoki Shibata and contributors 2010 - 2023.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#if !defined(SLEEF_GENHEADER)
#include <stdint.h>
#endif

#ifndef ENABLE_BUILTIN_MATH

#if !defined(SLEEF_GENHEADER)
#include <math.h>
#endif

#define SQRT sqrt
#define SQRTF sqrtf
#define FMA fma
#define FMAF fmaf
#define RINT rint
#define RINTF rintf
#define TRUNC trunc
#define TRUNCF truncf

#else

#define SQRT __builtin_sqrt
#define SQRTF __builtin_sqrtf
#define FMA __builtin_fma
#define FMAF __builtin_fmaf
#define RINT __builtin_rint
#define RINTF __builtin_rintf
#define TRUNC __builtin_trunc
#define TRUNCF __builtin_truncf

#endif

#if !defined(SLEEF_GENHEADER)
#include "misc.h"
#endif

#ifndef CONFIG
#error CONFIG macro not defined
#endif

#define ENABLE_DP
//@#define ENABLE_DP
#define ENABLE_SP
//@#define ENABLE_SP

#if CONFIG == 2 || CONFIG == 3
#define ENABLE_FMA_DP
//@#define ENABLE_FMA_DP
#define ENABLE_FMA_SP
//@#define ENABLE_FMA_SP

#if defined(__AVX2__) || defined(__aarch64__) || defined(__arm__) || defined(__powerpc64__) || defined(__zarch__) || defined(__riscv) || CONFIG == 3
#ifndef FP_FAST_FMA
//@#ifndef FP_FAST_FMA
#define FP_FAST_FMA
//@#define FP_FAST_FMA
#endif
//@#endif
#ifndef FP_FAST_FMAF
//@#ifndef FP_FAST_FMAF
#define FP_FAST_FMAF
//@#define FP_FAST_FMAF
#endif
//@#endif
#endif

#if (!defined(FP_FAST_FMA) || !defined(FP_FAST_FMAF)) && !defined(SLEEF_GENHEADER)
#error FP_FAST_FMA or FP_FAST_FMAF not defined
#endif

#define ISANAME "Pure C scalar with FMA"

#else // #if CONFIG == 2 || CONFIG == 3
#define ISANAME "Pure C scalar"
#endif // #if CONFIG == 2 || CONFIG == 3

#define LOG2VECTLENDP 0
//@#define LOG2VECTLENDP 0
#define VECTLENDP (1 << LOG2VECTLENDP)
//@#define VECTLENDP (1 << LOG2VECTLENDP)
#define LOG2VECTLENSP 0
//@#define LOG2VECTLENSP 0
#define VECTLENSP (1 << LOG2VECTLENSP)
//@#define VECTLENSP (1 << LOG2VECTLENSP)

#define ACCURATE_SQRT
//@#define ACCURATE_SQRT

#if defined(__SSE4_1__) || defined(__aarch64__) || CONFIG == 3
#define FULL_FP_ROUNDING
//@#define FULL_FP_ROUNDING
#endif

#define DFTPRIORITY LOG2VECTLENDP

typedef uint64_t vmask;
typedef uint32_t vopmask;
typedef double vdouble;
typedef int32_t vint;
typedef float vfloat;
typedef int32_t vint2;

typedef int64_t vint64;
typedef uint64_t vuint64;

typedef Sleef_uint64_2t vquad;

#if CONFIG != 3
typedef Sleef_quad vargquad;
#else
typedef Sleef_uint64_2t vargquad;
#endif

//

static INLINE int vavailability_i(int name) { return -1; }
static INLINE void vprefetch_v_p(const void *ptr) {}

static INLINE int vtestallones_i_vo64(vopmask g) { return g; }
static INLINE int vtestallones_i_vo32(vopmask g) { return g; }

//

static vint2 vloadu_vi2_p(int32_t *p) { return *p; }
static void vstoreu_v_p_vi2(int32_t *p, vint2 v) { *p = v; }
static vint vloadu_vi_p(int32_t *p) { return *p; }
static void vstoreu_v_p_vi(int32_t *p, vint v) { *p = v; }

//

static INLINE vopmask vcast_vo32_vo64(vopmask m) { return m; }
static INLINE vopmask vcast_vo64_vo32(vopmask m) { return m; }
static INLINE vopmask vcast_vo_i(int i) { return i ? -1 : 0; }
static INLINE vmask vcast_vm_i_i(int h, int l) { return (((uint64_t)h) << 32) | (uint32_t)l; }

static INLINE vmask vcast_vm_i64(int64_t i) { return (int64_t)i; }
static INLINE vmask vcast_vm_u64(uint64_t i) { return i; }

static INLINE vmask vcastu_vm_vi(vint vi) { return ((uint64_t)vi) << 32; }
static INLINE vint vcastu_vi_vm(vmask vm) { return (int32_t)(vm >> 32); }

static INLINE vdouble vcast_vd_d(double d) { return d; }

//

static INLINE vopmask vand_vo_vo_vo   (vopmask x, vopmask y) { return x & y; }
static INLINE vopmask vandnot_vo_vo_vo(vopmask x, vopmask y) { return y & ~x; }
static INLINE vopmask vor_vo_vo_vo    (vopmask x, vopmask y) { return x | y; }
static INLINE vopmask vxor_vo_vo_vo   (vopmask x, vopmask y) { return x ^ y; }

static INLINE vmask vand_vm_vm_vm     (vmask x, vmask y)     { return x & y; }
static INLINE vmask vandnot_vm_vm_vm  (vmask x, vmask y)     { return y & ~x; }
static INLINE vmask vor_vm_vm_vm      (vmask x, vmask y)     { return x | y; }
static INLINE vmask vxor_vm_vm_vm     (vmask x, vmask y)     { return x ^ y; }

static INLINE vmask vcast_vm_vo(vopmask o) { return (vmask)o | (((vmask)o) << 32); }

static INLINE vmask vand_vm_vo64_vm(vopmask x, vmask y)      { return vcast_vm_vo(x) & y; }
static INLINE vmask vandnot_vm_vo64_vm(vopmask x, vmask y)   { return y & ~vcast_vm_vo(x); }
static INLINE vmask vor_vm_vo64_vm(vopmask x, vmask y)       { return vcast_vm_vo(x) | y; }
static INLINE vmask vxor_vm_vo64_vm(vopmask x, vmask y)      { return vcast_vm_vo(x) ^ y; }

static INLINE vmask vand_vm_vo32_vm(vopmask x, vmask y)      { return vcast_vm_vo(x) & y; }
static INLINE vmask vandnot_vm_vo32_vm(vopmask x, vmask y)   { return y & ~vcast_vm_vo(x); }
static INLINE vmask vor_vm_vo32_vm(vopmask x, vmask y)       { return vcast_vm_vo(x) | y; }
static INLINE vmask vxor_vm_vo32_vm(vopmask x, vmask y)      { return vcast_vm_vo(x) ^ y; }

//

static INLINE vdouble vsel_vd_vo_vd_vd   (vopmask o, vdouble x, vdouble y) { return o ? x : y; }
static INLINE vint2   vsel_vi2_vo_vi2_vi2(vopmask o, vint2 x, vint2 y)     { return o ? x : y; }

static INLINE CONST vdouble vsel_vd_vo_d_d(vopmask o, double v1, double v0) { return o ? v1 : v0; }

static INLINE vdouble vsel_vd_vo_vo_d_d_d(vopmask o0, vopmask o1, double d0, double d1, double d2) {
  return vsel_vd_vo_vd_vd(o0, vcast_vd_d(d0), vsel_vd_vo_d_d(o1, d1, d2));
}

static INLINE vdouble vsel_vd_vo_vo_vo_d_d_d_d(vopmask o0, vopmask o1, vopmask o2, double d0, double d1, double d2, double d3) {
  return vsel_vd_vo_vd_vd(o0, vcast_vd_d(d0), vsel_vd_vo_vd_vd(o1, vcast_vd_d(d1), vsel_vd_vo_d_d(o2, d2, d3)));
}

static INLINE vdouble vcast_vd_vi(vint vi) { return vi; }
static INLINE vint vcast_vi_i(int j) { return j; }

#ifdef FULL_FP_ROUNDING
static INLINE vint vrint_vi_vd(vdouble d) { return (int32_t)RINT(d); }
static INLINE vdouble vrint_vd_vd(vdouble vd) { return RINT(vd); }
static INLINE vdouble vtruncate_vd_vd(vdouble vd) { return TRUNC(vd); }
static INLINE vint vtruncate_vi_vd(vdouble vd) { return (int32_t)TRUNC(vd); }
#else
static INLINE vint vrint_vi_vd(vdouble a) {
  a += a > 0 ? 0.5 : -0.5;
  uint64_t vx;
  memcpy(&vx, &a, sizeof(vx));
  vx -= 1 & (int)a;
  memcpy(&a, &vx, sizeof(a));
  return a;
}
static INLINE vdouble vrint_vd_vd(vdouble vd) { return vcast_vd_vi(vrint_vi_vd(vd)); }
static INLINE vint vtruncate_vi_vd(vdouble vd) { return vd; }
static INLINE vdouble vtruncate_vd_vd(vdouble vd) { return vcast_vd_vi(vtruncate_vi_vd(vd)); }
#endif

static INLINE vopmask veq64_vo_vm_vm(vmask x, vmask y) { return x == y ? ~(uint32_t)0 : 0; }
static INLINE vmask vadd64_vm_vm_vm(vmask x, vmask y) { return x + y; }

//

static INLINE vmask vreinterpret_vm_vd(vdouble vd) { vmask vm; memcpy(&vm, &vd, sizeof(vm)); return vm; }
static INLINE vdouble vreinterpret_vd_vm(vmask vm) { vdouble vd; memcpy(&vd, &vm, sizeof(vd)); return vd; }

static INLINE vdouble vadd_vd_vd_vd(vdouble x, vdouble y) { return x + y; }
static INLINE vdouble vsub_vd_vd_vd(vdouble x, vdouble y) { return x - y; }
static INLINE vdouble vmul_vd_vd_vd(vdouble x, vdouble y) { return x * y; }
static INLINE vdouble vdiv_vd_vd_vd(vdouble x, vdouble y) { return x / y; }
static INLINE vdouble vrec_vd_vd(vdouble x)               { return 1 / x; }

static INLINE vdouble vabs_vd_vd(vdouble d) {
  uint64_t vx;
  memcpy(&vx, &d, sizeof(vx));
  vx &= UINT64_C(0x7fffffffffffffff);
  memcpy(&d, &vx, sizeof(d));
  return d;
}
static INLINE vdouble vneg_vd_vd(vdouble d) { return -d; }

static INLINE vdouble vmax_vd_vd_vd(vdouble x, vdouble y) { return x > y ? x : y; }
static INLINE vdouble vmin_vd_vd_vd(vdouble x, vdouble y) { return x < y ? x : y; }

#ifndef ENABLE_FMA_DP
static INLINE vdouble vmla_vd_vd_vd_vd  (vdouble x, vdouble y, vdouble z) { return x * y + z; }
static INLINE vdouble vmlapn_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return x * y - z; }
static INLINE vdouble vmlanp_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return -x * y + z; }
#else
static INLINE vdouble vmla_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return FMA(x, y, z); }
static INLINE vdouble vmlapn_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return FMA(x, y, -z); }
static INLINE vdouble vmlanp_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return FMA(-x, y, z); }
static INLINE vdouble vfma_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return FMA(x, y, z); }
static INLINE vdouble vfmapp_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return FMA(x, y, z); }
static INLINE vdouble vfmapn_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return FMA(x, y, -z); }
static INLINE vdouble vfmanp_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return FMA(-x, y, z); }
static INLINE vdouble vfmann_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return FMA(-x, y, -z); }
#endif

static INLINE vopmask veq_vo_vd_vd(vdouble x, vdouble y)  { return x == y ? ~(uint32_t)0 : 0; }
static INLINE vopmask vneq_vo_vd_vd(vdouble x, vdouble y) { return x != y ? ~(uint32_t)0 : 0; }
static INLINE vopmask vlt_vo_vd_vd(vdouble x, vdouble y)  { return x <  y ? ~(uint32_t)0 : 0; }
static INLINE vopmask vle_vo_vd_vd(vdouble x, vdouble y)  { return x <= y ? ~(uint32_t)0 : 0; }
static INLINE vopmask vgt_vo_vd_vd(vdouble x, vdouble y)  { return x >  y ? ~(uint32_t)0 : 0; }
static INLINE vopmask vge_vo_vd_vd(vdouble x, vdouble y)  { return x >= y ? ~(uint32_t)0 : 0; }

static INLINE vint vadd_vi_vi_vi(vint x, vint y) { return x + y; }
static INLINE vint vsub_vi_vi_vi(vint x, vint y) { return x - y; }
static INLINE vint vneg_vi_vi   (vint x)         { return   - x; }

static INLINE vint vand_vi_vi_vi(vint x, vint y)    { return x & y; }
static INLINE vint vandnot_vi_vi_vi(vint x, vint y) { return y & ~x; }
static INLINE vint vor_vi_vi_vi(vint x, vint y)     { return x | y; }
static INLINE vint vxor_vi_vi_vi(vint x, vint y)    { return x ^ y; }

static INLINE vint vand_vi_vo_vi(vopmask x, vint y)    { return x & y; }
static INLINE vint vandnot_vi_vo_vi(vopmask x, vint y) { return y & ~x; }

static INLINE vint vsll_vi_vi_i(vint x, int c) { return (uint32_t)x << c; }
static INLINE vint vsrl_vi_vi_i(vint x, int c) { return (uint32_t)x >> c; }
static INLINE vint vsra_vi_vi_i(vint x, int c) { return x >> c; }

static INLINE vopmask veq_vo_vi_vi(vint x, vint y) { return x == y ? ~(uint32_t)0 : 0; }
static INLINE vopmask vgt_vo_vi_vi(vint x, vint y) { return x >  y ? ~(uint32_t)0 : 0; }

static INLINE vint vsel_vi_vo_vi_vi(vopmask m, vint x, vint y) { return m ? x : y; }

static INLINE vopmask visinf_vo_vd(vdouble d)  { return (d == SLEEF_INFINITY || d == -SLEEF_INFINITY) ? ~(uint32_t)0 : 0; }
static INLINE vopmask vispinf_vo_vd(vdouble d) { return d == SLEEF_INFINITY ? ~(uint32_t)0 : 0; }
static INLINE vopmask visminf_vo_vd(vdouble d) { return d == -SLEEF_INFINITY ? ~(uint32_t)0 : 0; }
static INLINE vopmask visnan_vo_vd(vdouble d)  { return d != d ? ~(uint32_t)0 : 0; }

static INLINE vdouble vsqrt_vd_vd(vdouble d) { return SQRT(d); }
static INLINE vfloat vsqrt_vf_vf(vfloat x) { return SQRTF(x); }

static INLINE double vcast_d_vd(vdouble v) { return v; }

static INLINE vdouble vload_vd_p(const double *ptr) { return *ptr; }
static INLINE vdouble vloadu_vd_p(const double *ptr) { return *ptr; }
static INLINE vdouble vgather_vd_p_vi(const double *ptr, vint vi) { return ptr[vi]; }

static INLINE void vstore_v_p_vd(double *ptr, vdouble v) { *ptr = v; }
static INLINE void vstoreu_v_p_vd(double *ptr, vdouble v) { *ptr = v; }
static INLINE void vstream_v_p_vd(double *ptr, vdouble v) { *ptr = v; }

//

static INLINE vint2 vcast_vi2_vm(vmask vm) { return (int32_t)vm; }
static INLINE vmask vcast_vm_vi2(vint2 vi) { return (uint32_t)vi; }

static INLINE vfloat vcast_vf_vi2(vint2 vi) { return (int32_t)vi; }
static INLINE vint2 vcast_vi2_i(int j) { return j; }

#ifdef FULL_FP_ROUNDING
static INLINE vint2 vrint_vi2_vf(vfloat d) { return (int)RINTF(d); }
static INLINE vfloat vrint_vf_vf(vfloat vd) { return RINTF(vd); }
static INLINE vfloat vtruncate_vf_vf(vfloat vd) { return TRUNCF(vd); }
static INLINE vint2 vtruncate_vi2_vf(vfloat vf) { return (int32_t)TRUNCF(vf); }
#else
static INLINE vint2 vrint_vi2_vf(vfloat a) {
  a += a > 0 ? 0.5f : -0.5f;
  uint32_t vu[1];
  memcpy(vu, &a, sizeof(vu));
  vu[0] -= 1 & (int)a;
  memcpy(&a, vu, sizeof(a));
  return (int32_t)a;
}
static INLINE vfloat vrint_vf_vf(vfloat vd) { return vcast_vf_vi2(vrint_vi2_vf(vd)); }
static INLINE vint2 vtruncate_vi2_vf(vfloat vf) { return vf; }
static INLINE vfloat vtruncate_vf_vf(vfloat vd) { return vcast_vf_vi2(vtruncate_vi2_vf(vd)); }
#endif

static INLINE vfloat vcast_vf_f(float f) { return f; }
static INLINE vmask vreinterpret_vm_vf(vfloat f) { vfloat vf[2] = { f, 0 }; vmask vm; memcpy(&vm, &vf, sizeof(vm)); return vm; }
static INLINE vfloat vreinterpret_vf_vm(vmask vm) { vfloat vf[2]; memcpy(&vf, &vm, sizeof(vf)); return vf[0]; }
static INLINE vfloat vreinterpret_vf_vi2(vint2 vi) { vfloat vf; memcpy(&vf, &vi, sizeof(vf)); return vf; }
static INLINE vint2 vreinterpret_vi2_vf(vfloat f) { vint2 vi2; memcpy(&vi2, &f, sizeof(vi2)); return vi2; }

static INLINE vfloat vadd_vf_vf_vf(vfloat x, vfloat y) { return x + y; }
static INLINE vfloat vsub_vf_vf_vf(vfloat x, vfloat y) { return x - y; }
static INLINE vfloat vmul_vf_vf_vf(vfloat x, vfloat y) { return x * y; }
static INLINE vfloat vdiv_vf_vf_vf(vfloat x, vfloat y) { return x / y; }
static INLINE vfloat vrec_vf_vf   (vfloat x)           { return 1 / x; }

static INLINE vfloat vabs_vf_vf(vfloat x) {
  int32_t vi[1];
  memcpy(vi, &x, sizeof(vi));
  vi[0] &= 0x7fffffff;
  memcpy(&x, vi, sizeof(x));
  return x;
}
static INLINE vfloat vneg_vf_vf(vfloat x) { return -x; }

static INLINE vfloat vmax_vf_vf_vf(vfloat x, vfloat y) { return x > y ? x : y; }
static INLINE vfloat vmin_vf_vf_vf(vfloat x, vfloat y) { return x < y ? x : y; }

#ifndef ENABLE_FMA_SP
static INLINE vfloat vmla_vf_vf_vf_vf  (vfloat x, vfloat y, vfloat z) { return x * y + z; }
static INLINE vfloat vmlanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return - x * y + z; }
static INLINE vfloat vmlapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return x * y - z; }
#else
static INLINE vfloat vmla_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return FMAF(x, y, z); }
static INLINE vfloat vmlapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return FMAF(x, y, -z); }
static INLINE vfloat vmlanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return FMAF(-x, y, z); }
static INLINE vfloat vfma_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return FMAF(x, y, z); }
static INLINE vfloat vfmapp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return FMAF(x, y, z); }
static INLINE vfloat vfmapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return FMAF(x, y, -z); }
static INLINE vfloat vfmanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return FMAF(-x, y, z); }
static INLINE vfloat vfmann_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return FMAF(-x, y, -z); }
#endif

static INLINE vopmask veq_vo_vf_vf(vfloat x, vfloat y)  { return x == y ? ~(uint32_t)0 : 0; }
static INLINE vopmask vneq_vo_vf_vf(vfloat x, vfloat y) { return x != y ? ~(uint32_t)0 : 0; }
static INLINE vopmask vlt_vo_vf_vf(vfloat x, vfloat y)  { return x <  y ? ~(uint32_t)0 : 0; }
static INLINE vopmask vle_vo_vf_vf(vfloat x, vfloat y)  { return x <= y ? ~(uint32_t)0 : 0; }
static INLINE vopmask vgt_vo_vf_vf(vfloat x, vfloat y)  { return x >  y ? ~(uint32_t)0 : 0; }
static INLINE vopmask vge_vo_vf_vf(vfloat x, vfloat y)  { return x >= y ? ~(uint32_t)0 : 0; }

static INLINE vint2 vadd_vi2_vi2_vi2(vint2 x, vint2 y) { return x + y; }
static INLINE vint2 vsub_vi2_vi2_vi2(vint2 x, vint2 y) { return x - y; }
static INLINE vint2 vneg_vi2_vi2(vint2 x) { return -x; }

static INLINE vint2 vand_vi2_vi2_vi2(vint2 x, vint2 y)    { return x & y; }
static INLINE vint2 vandnot_vi2_vi2_vi2(vint2 x, vint2 y) { return y & ~x; }
static INLINE vint2 vor_vi2_vi2_vi2(vint2 x, vint2 y)     { return x | y; }
static INLINE vint2 vxor_vi2_vi2_vi2(vint2 x, vint2 y)    { return x ^ y; }

static INLINE vfloat vsel_vf_vo_vf_vf(vopmask o, vfloat x, vfloat y) { return o ? x : y; }
static INLINE vfloat vsel_vf_vo_f_f(vopmask o, float v1, float v0) { return o ? v1 : v0; }

static INLINE vfloat vsel_vf_vo_vo_f_f_f(vopmask o0, vopmask o1, float d0, float d1, float d2) {
  return vsel_vf_vo_vf_vf(o0, vcast_vf_f(d0), vsel_vf_vo_f_f(o1, d1, d2));
}

static INLINE vfloat vsel_vf_vo_vo_vo_f_f_f_f(vopmask o0, vopmask o1, vopmask o2, float d0, float d1, float d2, float d3) {
  return vsel_vf_vo_vf_vf(o0, vcast_vf_f(d0), vsel_vf_vo_vf_vf(o1, vcast_vf_f(d1), vsel_vf_vo_f_f(o2, d2, d3)));
}

static INLINE vint2 vand_vi2_vo_vi2(vopmask x, vint2 y) { return vcast_vm_vo(x) & y; }
static INLINE vint2 vandnot_vi2_vo_vi2(vopmask x, vint2 y) { return y & ~vcast_vm_vo(x); }

static INLINE vint2 vsll_vi2_vi2_i(vint2 x, int c) {
  return x << c;
}
static INLINE vint2 vsrl_vi2_vi2_i(vint2 x, int c) {
  return ((uint32_t)x) >> c;
}
static INLINE vint2 vsra_vi2_vi2_i(vint2 x, int c) {
  return x >> c;
}

static INLINE vopmask visinf_vo_vf (vfloat d) { return (d == SLEEF_INFINITYf || d == -SLEEF_INFINITYf) ? ~(uint32_t)0 : 0; }
static INLINE vopmask vispinf_vo_vf(vfloat d) { return d == SLEEF_INFINITYf ? ~(uint32_t)0 : 0; }
static INLINE vopmask visminf_vo_vf(vfloat d) { return d == -SLEEF_INFINITYf ? ~(uint32_t)0 : 0; }
static INLINE vopmask visnan_vo_vf (vfloat d) { return d != d ? ~(uint32_t)0 : 0; }

static INLINE vopmask veq_vo_vi2_vi2 (vint2 x, vint2 y) { return (int32_t)x == (int32_t)y ? ~(uint32_t)0 : 0; }
static INLINE vopmask vgt_vo_vi2_vi2 (vint2 x, vint2 y) { return (int32_t)x >  (int32_t)y ? ~(uint32_t)0 : 0; }
static INLINE vint2   veq_vi2_vi2_vi2(vint2 x, vint2 y) { return (int32_t)x == (int32_t)y ? ~(uint32_t)0 : 0; }
static INLINE vint2   vgt_vi2_vi2_vi2(vint2 x, vint2 y) { return (int32_t)x >  (int32_t)y ? ~(uint32_t)0 : 0; }

static INLINE float vcast_f_vf(vfloat v) { return v; }

static INLINE vfloat vload_vf_p(const float *ptr) { return *ptr; }
static INLINE vfloat vloadu_vf_p(const float *ptr) { return *ptr; }
static INLINE vfloat vgather_vf_p_vi2(const float *ptr, vint2 vi) { return ptr[vi]; }

static INLINE void vstore_v_p_vf(float *ptr, vfloat v) { *ptr = v; }
static INLINE void vstoreu_v_p_vf(float *ptr, vfloat v) { *ptr = v; }
static INLINE void vstream_v_p_vf(float *ptr, vfloat v) { *ptr = v; }

//

#if defined(__BYTE_ORDER__) && (__BYTE_ORDER__ == __ORDER_BIG_ENDIAN__)
static vquad loadu_vq_p(void *p) {
  vquad vq;
  memcpy(8 + (char *)&vq, p, 8);
  memcpy((char *)&vq, 8 + p, 8);
  return vq;
}

static INLINE vquad cast_vq_aq(vargquad aq) {
  vquad vq;
  memcpy(8 + (char *)&vq, (char *)&aq, 8);
  memcpy((char *)&vq, 8 + (char *)&aq, 8);
  return vq;
}

static INLINE vargquad cast_aq_vq(vquad vq) {
  vargquad aq;
  memcpy(8 + (char *)&aq, (char *)&vq, 8);
  memcpy((char *)&aq, 8 + (char *)&vq, 8);
  return aq;
}
#else
static vquad loadu_vq_p(void *p) {
  vquad vq;
  memcpy(&vq, p, sizeof(vq));
  return vq;
}

static INLINE vquad cast_vq_aq(vargquad aq) {
  vquad vq;
  memcpy(&vq, &aq, sizeof(vq));
  return vq;
}

static INLINE vargquad cast_aq_vq(vquad vq) {
  vargquad aq;
  memcpy(&aq, &vq, sizeof(aq));
  return aq;
}
#endif

//

static INLINE int vtestallzeros_i_vo64(vopmask g) { return !g ? ~(uint32_t)0 : 0; }
static INLINE vmask vsel_vm_vo64_vm_vm(vopmask o, vmask x, vmask y) { return o ? x : y; }

static INLINE vmask vsub64_vm_vm_vm(vmask x, vmask y) { return (int64_t)x - (int64_t)y; }
static INLINE vmask vneg64_vm_vm(vmask x) { return -(int64_t)x; }

#define vsll64_vm_vm_i(x, c) ((uint64_t)(x) << (c))
#define vsrl64_vm_vm_i(x, c) ((uint64_t)(x) >> (c))
//@#define vsll64_vm_vm_i(x, c) ((uint64_t)(x) << (c))
//@#define vsrl64_vm_vm_i(x, c) ((uint64_t)(x) >> (c))

static INLINE vopmask vgt64_vo_vm_vm(vmask x, vmask y) { return (int64_t)x > (int64_t)y ? ~(uint32_t)0 : 0; }

static INLINE vmask vcast_vm_vi(vint vi) { return vi; }
static INLINE vint vcast_vi_vm(vmask vm) { return vm; }

static INLINE vmask vreinterpret_vm_vi64(vint64 v) { return v; }
static INLINE vint64 vreinterpret_vi64_vm(vmask m) { return m; }
static INLINE vmask vreinterpret_vm_vu64(vuint64 v) { return v; }
static INLINE vuint64 vreinterpret_vu64_vm(vmask m) { return m; }
