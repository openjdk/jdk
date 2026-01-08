//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#if CONFIG == 2

#if !defined(__SSE2__) && !defined(SLEEF_GENHEADER)
#error Please specify -msse2.
#endif

#elif CONFIG == 3

#if (!defined(__SSE2__) || !defined(__SSE3__)) && !defined(SLEEF_GENHEADER)
#error Please specify -msse2 and -msse3
#endif

#elif CONFIG == 4

#if (!defined(__SSE2__) || !defined(__SSE3__) || !defined(__SSE4_1__)) && !defined(SLEEF_GENHEADER)
#error Please specify -msse2, -msse3 and -msse4.1
#endif

#else
#error CONFIG macro invalid or not defined
#endif

#define ENABLE_DP
//@#define ENABLE_DP
#define LOG2VECTLENDP 1
//@#define LOG2VECTLENDP 1
#define VECTLENDP (1 << LOG2VECTLENDP)
//@#define VECTLENDP (1 << LOG2VECTLENDP)

#define ENABLE_SP
//@#define ENABLE_SP
#define LOG2VECTLENSP (LOG2VECTLENDP+1)
//@#define LOG2VECTLENSP (LOG2VECTLENDP+1)
#define VECTLENSP (1 << LOG2VECTLENSP)
//@#define VECTLENSP (1 << LOG2VECTLENSP)

#define ACCURATE_SQRT
//@#define ACCURATE_SQRT

#if !defined(SLEEF_GENHEADER)
#if defined(_MSC_VER)
#include <intrin.h>
#else
#include <x86intrin.h>
#endif

#include <stdint.h>
#include "misc.h"
#endif // #if !defined(SLEEF_GENHEADER)

typedef __m128i vmask;
typedef __m128i vopmask;

typedef __m128d vdouble;
typedef __m128i vint;

typedef __m128  vfloat;
typedef __m128i vint2;

typedef __m128i vint64;
typedef __m128i vuint64;

typedef struct {
  vmask x, y;
} vquad;

typedef vquad vargquad;

//

#if !defined(SLEEF_GENHEADER)

#ifndef __SLEEF_H__
void Sleef_x86CpuID(int32_t out[4], uint32_t eax, uint32_t ecx);
#endif

static INLINE int cpuSupportsSSE2() {
    int32_t reg[4];
    Sleef_x86CpuID(reg, 1, 0);
    return (reg[3] & (1 << 26)) != 0;
}

static INLINE int cpuSupportsSSE3() {
    int32_t reg[4];
    Sleef_x86CpuID(reg, 1, 0);
    return (reg[2] & (1 << 0)) != 0;
}

static INLINE int cpuSupportsSSE4_1() {
    int32_t reg[4];
    Sleef_x86CpuID(reg, 1, 0);
    return (reg[2] & (1 << 19)) != 0;
}

#if defined(__SSE2__) && defined(__SSE3__) && defined(__SSE4_1__)
static INLINE int vavailability_i(int name) {
  //int d = __builtin_cpu_supports("sse2") && __builtin_cpu_supports("sse3") && __builtin_cpu_supports("sse4.1");
  int d = cpuSupportsSSE2() && cpuSupportsSSE3() && cpuSupportsSSE4_1();
  return d ? 3 : 0;
}
#define ISANAME "SSE4.1"
#define DFTPRIORITY 12
#elif defined(__SSE2__) && defined(__SSE3__)
static INLINE int vavailability_i(int name) {
  //int d = __builtin_cpu_supports("sse2") && __builtin_cpu_supports("sse3");
  int d = cpuSupportsSSE2() && cpuSupportsSSE3();
  return d ? 3 : 0;
}
#define ISANAME "SSE3"
#define DFTPRIORITY 11
#else
static INLINE int vavailability_i(int name) {
  int d = cpuSupportsSSE2();
  return d ? 3 : 0;
}
#define ISANAME "SSE2"
#define DFTPRIORITY 10
#endif

#endif // #if !defined(SLEEF_GENHEADER)

static INLINE void vprefetch_v_p(const void *ptr) { _mm_prefetch(ptr, _MM_HINT_T0); }

static INLINE int vtestallones_i_vo32(vopmask g) { return _mm_movemask_epi8(g) == 0xFFFF; }
static INLINE int vtestallones_i_vo64(vopmask g) { return _mm_movemask_epi8(g) == 0xFFFF; }

//

static vint2 vloadu_vi2_p(int32_t *p) { return _mm_loadu_si128((__m128i *)p); }
static void vstoreu_v_p_vi2(int32_t *p, vint2 v) { _mm_storeu_si128((__m128i *)p, v); }

static vint vloadu_vi_p(int32_t *p) { return _mm_loadu_si128((__m128i *)p); }
static void vstoreu_v_p_vi(int32_t *p, vint v) { _mm_storeu_si128((__m128i *)p, v); }

//

static INLINE vmask vand_vm_vm_vm(vmask x, vmask y) { return _mm_and_si128(x, y); }
static INLINE vmask vandnot_vm_vm_vm(vmask x, vmask y) { return _mm_andnot_si128(x, y); }
static INLINE vmask vor_vm_vm_vm(vmask x, vmask y) { return _mm_or_si128(x, y); }
static INLINE vmask vxor_vm_vm_vm(vmask x, vmask y) { return _mm_xor_si128(x, y); }

static INLINE vopmask vand_vo_vo_vo(vopmask x, vopmask y) { return _mm_and_si128(x, y); }
static INLINE vopmask vandnot_vo_vo_vo(vopmask x, vopmask y) { return _mm_andnot_si128(x, y); }
static INLINE vopmask vor_vo_vo_vo(vopmask x, vopmask y) { return _mm_or_si128(x, y); }
static INLINE vopmask vxor_vo_vo_vo(vopmask x, vopmask y) { return _mm_xor_si128(x, y); }

static INLINE vmask vand_vm_vo64_vm(vopmask x, vmask y) { return _mm_and_si128(x, y); }
static INLINE vmask vor_vm_vo64_vm(vopmask x, vmask y) { return _mm_or_si128(x, y); }
static INLINE vmask vandnot_vm_vo64_vm(vmask x, vmask y) { return _mm_andnot_si128(x, y); }
static INLINE vmask vxor_vm_vo64_vm(vmask x, vmask y) { return _mm_xor_si128(x, y); }

static INLINE vmask vand_vm_vo32_vm(vopmask x, vmask y) { return _mm_and_si128(x, y); }
static INLINE vmask vor_vm_vo32_vm(vopmask x, vmask y) { return _mm_or_si128(x, y); }
static INLINE vmask vandnot_vm_vo32_vm(vmask x, vmask y) { return _mm_andnot_si128(x, y); }
static INLINE vmask vxor_vm_vo32_vm(vmask x, vmask y) { return _mm_xor_si128(x, y); }

static INLINE vopmask vcast_vo32_vo64(vopmask m) { return _mm_shuffle_epi32(m, 0x08); }
static INLINE vopmask vcast_vo64_vo32(vopmask m) { return _mm_shuffle_epi32(m, 0x50); }

static INLINE vopmask vcast_vo_i(int i) { return _mm_set1_epi64x(i ? -1 : 0); }

//

static INLINE vint vrint_vi_vd(vdouble vd) { return _mm_cvtpd_epi32(vd); }
static INLINE vint vtruncate_vi_vd(vdouble vd) { return _mm_cvttpd_epi32(vd); }
static INLINE vdouble vcast_vd_vi(vint vi) { return _mm_cvtepi32_pd(vi); }
static INLINE vint vcast_vi_i(int i) { return _mm_set_epi32(0, 0, i, i); }
static INLINE vint2 vcastu_vm_vi(vint vi) { return _mm_and_si128(_mm_shuffle_epi32(vi, 0x73), _mm_set_epi32(-1, 0, -1, 0)); }
static INLINE vint vcastu_vi_vm(vint2 vi) { return _mm_shuffle_epi32(vi, 0x0d); }

#if CONFIG == 4
static INLINE vdouble vtruncate_vd_vd(vdouble vd) { return _mm_round_pd(vd, _MM_FROUND_TO_ZERO |_MM_FROUND_NO_EXC); }
static INLINE vdouble vrint_vd_vd(vdouble vd) { return _mm_round_pd(vd, _MM_FROUND_TO_NEAREST_INT |_MM_FROUND_NO_EXC); }
static INLINE vfloat vtruncate_vf_vf(vfloat vf) { return _mm_round_ps(vf, _MM_FROUND_TO_ZERO |_MM_FROUND_NO_EXC); }
static INLINE vfloat vrint_vf_vf(vfloat vd) { return _mm_round_ps(vd, _MM_FROUND_TO_NEAREST_INT |_MM_FROUND_NO_EXC); }
static INLINE vopmask veq64_vo_vm_vm(vmask x, vmask y) { return _mm_cmpeq_epi64(x, y); }
#define FULL_FP_ROUNDING
//@#define FULL_FP_ROUNDING
#else
static INLINE vdouble vtruncate_vd_vd(vdouble vd) { return vcast_vd_vi(vtruncate_vi_vd(vd)); }
static INLINE vdouble vrint_vd_vd(vdouble vd) { return vcast_vd_vi(vrint_vi_vd(vd)); }
static INLINE vopmask veq64_vo_vm_vm(vmask x, vmask y) {
  vmask t = _mm_cmpeq_epi32(x, y);
  return vand_vm_vm_vm(t, _mm_shuffle_epi32(t, 0xb1));
}
#endif

static INLINE vmask vadd64_vm_vm_vm(vmask x, vmask y) { return _mm_add_epi64(x, y); }

static INLINE vmask vcast_vm_i_i(int i0, int i1) { return _mm_set_epi32(i0, i1, i0, i1); }

static INLINE vmask vcast_vm_i64(int64_t i) { return _mm_set1_epi64x(i); }
static INLINE vmask vcast_vm_u64(uint64_t i) { return _mm_set1_epi64x((uint64_t)i); }

//

static INLINE vdouble vcast_vd_d(double d) { return _mm_set1_pd(d); }
static INLINE vmask vreinterpret_vm_vd(vdouble vd) { return _mm_castpd_si128(vd); }
static INLINE vdouble vreinterpret_vd_vm(vmask vm) { return _mm_castsi128_pd(vm); }

static INLINE vdouble vadd_vd_vd_vd(vdouble x, vdouble y) { return _mm_add_pd(x, y); }
static INLINE vdouble vsub_vd_vd_vd(vdouble x, vdouble y) { return _mm_sub_pd(x, y); }
static INLINE vdouble vmul_vd_vd_vd(vdouble x, vdouble y) { return _mm_mul_pd(x, y); }
static INLINE vdouble vdiv_vd_vd_vd(vdouble x, vdouble y) { return _mm_div_pd(x, y); }
static INLINE vdouble vrec_vd_vd(vdouble x) { return _mm_div_pd(_mm_set1_pd(1), x); }
static INLINE vdouble vsqrt_vd_vd(vdouble x) { return _mm_sqrt_pd(x); }
static INLINE vdouble vabs_vd_vd(vdouble d) { return _mm_andnot_pd(_mm_set1_pd(-0.0), d); }
static INLINE vdouble vneg_vd_vd(vdouble d) { return _mm_xor_pd(_mm_set1_pd(-0.0), d); }
static INLINE vdouble vmla_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return vadd_vd_vd_vd(vmul_vd_vd_vd(x, y), z); }
static INLINE vdouble vmlapn_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return vsub_vd_vd_vd(vmul_vd_vd_vd(x, y), z); }
static INLINE vdouble vmlanp_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return vsub_vd_vd_vd(z, vmul_vd_vd_vd(x, y)); }
static INLINE vdouble vmax_vd_vd_vd(vdouble x, vdouble y) { return _mm_max_pd(x, y); }
static INLINE vdouble vmin_vd_vd_vd(vdouble x, vdouble y) { return _mm_min_pd(x, y); }

static INLINE vopmask veq_vo_vd_vd(vdouble x, vdouble y) { return _mm_castpd_si128(_mm_cmpeq_pd(x, y)); }
static INLINE vopmask vneq_vo_vd_vd(vdouble x, vdouble y) { return _mm_castpd_si128(_mm_cmpneq_pd(x, y)); }
static INLINE vopmask vlt_vo_vd_vd(vdouble x, vdouble y) { return _mm_castpd_si128(_mm_cmplt_pd(x, y)); }
static INLINE vopmask vle_vo_vd_vd(vdouble x, vdouble y) { return _mm_castpd_si128(_mm_cmple_pd(x, y)); }
static INLINE vopmask vgt_vo_vd_vd(vdouble x, vdouble y) { return _mm_castpd_si128(_mm_cmpgt_pd(x, y)); }
static INLINE vopmask vge_vo_vd_vd(vdouble x, vdouble y) { return _mm_castpd_si128(_mm_cmpge_pd(x, y)); }

static INLINE vint vadd_vi_vi_vi(vint x, vint y) { return _mm_add_epi32(x, y); }
static INLINE vint vsub_vi_vi_vi(vint x, vint y) { return _mm_sub_epi32(x, y); }
static INLINE vint vneg_vi_vi(vint e) { return vsub_vi_vi_vi(vcast_vi_i(0), e); }

static INLINE vint vand_vi_vi_vi(vint x, vint y) { return _mm_and_si128(x, y); }
static INLINE vint vandnot_vi_vi_vi(vint x, vint y) { return _mm_andnot_si128(x, y); }
static INLINE vint vor_vi_vi_vi(vint x, vint y) { return _mm_or_si128(x, y); }
static INLINE vint vxor_vi_vi_vi(vint x, vint y) { return _mm_xor_si128(x, y); }

static INLINE vint vand_vi_vo_vi(vopmask x, vint y) { return _mm_and_si128(x, y); }
static INLINE vint vandnot_vi_vo_vi(vopmask x, vint y) { return _mm_andnot_si128(x, y); }

static INLINE vint vsll_vi_vi_i(vint x, int c) { return _mm_slli_epi32(x, c); }
static INLINE vint vsrl_vi_vi_i(vint x, int c) { return _mm_srli_epi32(x, c); }
static INLINE vint vsra_vi_vi_i(vint x, int c) { return _mm_srai_epi32(x, c); }

static INLINE vint veq_vi_vi_vi(vint x, vint y) { return _mm_cmpeq_epi32(x, y); }
static INLINE vint vgt_vi_vi_vi(vint x, vint y) { return _mm_cmpgt_epi32(x, y); }

static INLINE vopmask veq_vo_vi_vi(vint x, vint y) { return _mm_cmpeq_epi32(x, y); }
static INLINE vopmask vgt_vo_vi_vi(vint x, vint y) { return _mm_cmpgt_epi32(x, y); }

#if CONFIG == 4
static INLINE vint vsel_vi_vo_vi_vi(vopmask m, vint x, vint y) { return _mm_blendv_epi8(y, x, m); }

static INLINE vdouble vsel_vd_vo_vd_vd(vopmask m, vdouble x, vdouble y) { return _mm_blendv_pd(y, x, _mm_castsi128_pd(m)); }
#else
static INLINE vint vsel_vi_vo_vi_vi(vopmask m, vint x, vint y) { return vor_vm_vm_vm(vand_vm_vm_vm(m, x), vandnot_vm_vm_vm(m, y)); }

static INLINE vdouble vsel_vd_vo_vd_vd(vopmask opmask, vdouble x, vdouble y) {
  return _mm_or_pd(_mm_and_pd(_mm_castsi128_pd(opmask), x), _mm_andnot_pd(_mm_castsi128_pd(opmask), y));
}
#endif

static INLINE CONST vdouble vsel_vd_vo_d_d(vopmask o, double v1, double v0) {
  return vsel_vd_vo_vd_vd(o, vcast_vd_d(v1), vcast_vd_d(v0));
}

static INLINE vdouble vsel_vd_vo_vo_d_d_d(vopmask o0, vopmask o1, double d0, double d1, double d2) {
  return vsel_vd_vo_vd_vd(o0, vcast_vd_d(d0), vsel_vd_vo_d_d(o1, d1, d2));
}

static INLINE vdouble vsel_vd_vo_vo_vo_d_d_d_d(vopmask o0, vopmask o1, vopmask o2, double d0, double d1, double d2, double d3) {
  return vsel_vd_vo_vd_vd(o0, vcast_vd_d(d0), vsel_vd_vo_vd_vd(o1, vcast_vd_d(d1), vsel_vd_vo_d_d(o2, d2, d3)));
}

static INLINE vopmask visinf_vo_vd(vdouble d) {
  return vreinterpret_vm_vd(_mm_cmpeq_pd(vabs_vd_vd(d), _mm_set1_pd(SLEEF_INFINITY)));
}

static INLINE vopmask vispinf_vo_vd(vdouble d) {
  return vreinterpret_vm_vd(_mm_cmpeq_pd(d, _mm_set1_pd(SLEEF_INFINITY)));
}

static INLINE vopmask visminf_vo_vd(vdouble d) {
  return vreinterpret_vm_vd(_mm_cmpeq_pd(d, _mm_set1_pd(-SLEEF_INFINITY)));
}

static INLINE vopmask visnan_vo_vd(vdouble d) {
  return vreinterpret_vm_vd(_mm_cmpneq_pd(d, d));
}

//

static INLINE vdouble vload_vd_p(const double *ptr) { return _mm_load_pd(ptr); }
static INLINE vdouble vloadu_vd_p(const double *ptr) { return _mm_loadu_pd(ptr); }

static INLINE void vstore_v_p_vd(double *ptr, vdouble v) { _mm_store_pd(ptr, v); }
static INLINE void vstoreu_v_p_vd(double *ptr, vdouble v) { _mm_storeu_pd(ptr, v); }

static INLINE vdouble vgather_vd_p_vi(const double *ptr, vint vi) {
  int a[sizeof(vint)/sizeof(int)];
  vstoreu_v_p_vi(a, vi);
  return _mm_set_pd(ptr[a[1]], ptr[a[0]]);
}

// This function is for debugging
static INLINE double vcast_d_vd(vdouble v) {
  double a[VECTLENDP];
  vstoreu_v_p_vd(a, v);
  return a[0];
}

//

static INLINE vint2 vcast_vi2_vm(vmask vm) { return vm; }
static INLINE vmask vcast_vm_vi2(vint2 vi) { return vi; }
static INLINE vint2 vrint_vi2_vf(vfloat vf) { return _mm_cvtps_epi32(vf); }
static INLINE vint2 vtruncate_vi2_vf(vfloat vf) { return _mm_cvttps_epi32(vf); }
static INLINE vfloat vcast_vf_vi2(vint2 vi) { return _mm_cvtepi32_ps(vcast_vm_vi2(vi)); }
static INLINE vfloat vcast_vf_f(float f) { return _mm_set1_ps(f); }
static INLINE vint2 vcast_vi2_i(int i) { return _mm_set1_epi32(i); }
static INLINE vmask vreinterpret_vm_vf(vfloat vf) { return _mm_castps_si128(vf); }
static INLINE vfloat vreinterpret_vf_vm(vmask vm) { return _mm_castsi128_ps(vm); }
static INLINE vfloat vreinterpret_vf_vi2(vint2 vm) { return _mm_castsi128_ps(vm); }
static INLINE vint2 vreinterpret_vi2_vf(vfloat vf) { return _mm_castps_si128(vf); }

#if CONFIG != 4
static INLINE vfloat vtruncate_vf_vf(vfloat vd) { return vcast_vf_vi2(vtruncate_vi2_vf(vd)); }
static INLINE vfloat vrint_vf_vf(vfloat vf) { return vcast_vf_vi2(vrint_vi2_vf(vf)); }
#endif

static INLINE vfloat vadd_vf_vf_vf(vfloat x, vfloat y) { return _mm_add_ps(x, y); }
static INLINE vfloat vsub_vf_vf_vf(vfloat x, vfloat y) { return _mm_sub_ps(x, y); }
static INLINE vfloat vmul_vf_vf_vf(vfloat x, vfloat y) { return _mm_mul_ps(x, y); }
static INLINE vfloat vdiv_vf_vf_vf(vfloat x, vfloat y) { return _mm_div_ps(x, y); }
static INLINE vfloat vrec_vf_vf(vfloat x) { return vdiv_vf_vf_vf(vcast_vf_f(1.0f), x); }
static INLINE vfloat vsqrt_vf_vf(vfloat x) { return _mm_sqrt_ps(x); }
static INLINE vfloat vabs_vf_vf(vfloat f) { return vreinterpret_vf_vm(vandnot_vm_vm_vm(vreinterpret_vm_vf(vcast_vf_f(-0.0f)), vreinterpret_vm_vf(f))); }
static INLINE vfloat vneg_vf_vf(vfloat d) { return vreinterpret_vf_vm(vxor_vm_vm_vm(vreinterpret_vm_vf(vcast_vf_f(-0.0f)), vreinterpret_vm_vf(d))); }
static INLINE vfloat vmla_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vadd_vf_vf_vf(vmul_vf_vf_vf(x, y), z); }
static INLINE vfloat vmlapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vsub_vf_vf_vf(vmul_vf_vf_vf(x, y), z); }
static INLINE vfloat vmlanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vsub_vf_vf_vf(z, vmul_vf_vf_vf(x, y)); }
static INLINE vfloat vmax_vf_vf_vf(vfloat x, vfloat y) { return _mm_max_ps(x, y); }
static INLINE vfloat vmin_vf_vf_vf(vfloat x, vfloat y) { return _mm_min_ps(x, y); }

static INLINE vopmask veq_vo_vf_vf(vfloat x, vfloat y) { return vreinterpret_vm_vf(_mm_cmpeq_ps(x, y)); }
static INLINE vopmask vneq_vo_vf_vf(vfloat x, vfloat y) { return vreinterpret_vm_vf(_mm_cmpneq_ps(x, y)); }
static INLINE vopmask vlt_vo_vf_vf(vfloat x, vfloat y) { return vreinterpret_vm_vf(_mm_cmplt_ps(x, y)); }
static INLINE vopmask vle_vo_vf_vf(vfloat x, vfloat y) { return vreinterpret_vm_vf(_mm_cmple_ps(x, y)); }
static INLINE vopmask vgt_vo_vf_vf(vfloat x, vfloat y) { return vreinterpret_vm_vf(_mm_cmpgt_ps(x, y)); }
static INLINE vopmask vge_vo_vf_vf(vfloat x, vfloat y) { return vreinterpret_vm_vf(_mm_cmpge_ps(x, y)); }

static INLINE vint2 vadd_vi2_vi2_vi2(vint2 x, vint2 y) { return vadd_vi_vi_vi(x, y); }
static INLINE vint2 vsub_vi2_vi2_vi2(vint2 x, vint2 y) { return vsub_vi_vi_vi(x, y); }
static INLINE vint2 vneg_vi2_vi2(vint2 e) { return vsub_vi2_vi2_vi2(vcast_vi2_i(0), e); }

static INLINE vint2 vand_vi2_vi2_vi2(vint2 x, vint2 y) { return vand_vi_vi_vi(x, y); }
static INLINE vint2 vandnot_vi2_vi2_vi2(vint2 x, vint2 y) { return vandnot_vi_vi_vi(x, y); }
static INLINE vint2 vor_vi2_vi2_vi2(vint2 x, vint2 y) { return vor_vi_vi_vi(x, y); }
static INLINE vint2 vxor_vi2_vi2_vi2(vint2 x, vint2 y) { return vxor_vi_vi_vi(x, y); }

static INLINE vint2 vand_vi2_vo_vi2(vopmask x, vint2 y) { return vand_vi_vo_vi(x, y); }
static INLINE vint2 vandnot_vi2_vo_vi2(vopmask x, vint2 y) { return vandnot_vi_vo_vi(x, y); }

static INLINE vint2 vsll_vi2_vi2_i(vint2 x, int c) { return vsll_vi_vi_i(x, c); }
static INLINE vint2 vsrl_vi2_vi2_i(vint2 x, int c) { return vsrl_vi_vi_i(x, c); }
static INLINE vint2 vsra_vi2_vi2_i(vint2 x, int c) { return vsra_vi_vi_i(x, c); }

static INLINE vopmask veq_vo_vi2_vi2(vint2 x, vint2 y) { return _mm_cmpeq_epi32(x, y); }
static INLINE vopmask vgt_vo_vi2_vi2(vint2 x, vint2 y) { return _mm_cmpgt_epi32(x, y); }
static INLINE vint2 veq_vi2_vi2_vi2(vint2 x, vint2 y) { return _mm_cmpeq_epi32(x, y); }
static INLINE vint2 vgt_vi2_vi2_vi2(vint2 x, vint2 y) { return _mm_cmpgt_epi32(x, y); }

#if CONFIG == 4
static INLINE vint2 vsel_vi2_vo_vi2_vi2(vopmask m, vint2 x, vint2 y) { return _mm_blendv_epi8(y, x, m); }

static INLINE vfloat vsel_vf_vo_vf_vf(vopmask m, vfloat x, vfloat y) { return _mm_blendv_ps(y, x, _mm_castsi128_ps(m)); }
#else
static INLINE vint2 vsel_vi2_vo_vi2_vi2(vopmask m, vint2 x, vint2 y) {
  return vor_vi2_vi2_vi2(vand_vi2_vi2_vi2(m, x), vandnot_vi2_vi2_vi2(m, y));
}

static INLINE vfloat vsel_vf_vo_vf_vf(vopmask opmask, vfloat x, vfloat y) {
  return _mm_or_ps(_mm_and_ps(_mm_castsi128_ps(opmask), x), _mm_andnot_ps(_mm_castsi128_ps(opmask), y));
}
#endif

static INLINE CONST vfloat vsel_vf_vo_f_f(vopmask o, float v1, float v0) {
  return vsel_vf_vo_vf_vf(o, vcast_vf_f(v1), vcast_vf_f(v0));
}

static INLINE vfloat vsel_vf_vo_vo_f_f_f(vopmask o0, vopmask o1, float d0, float d1, float d2) {
  return vsel_vf_vo_vf_vf(o0, vcast_vf_f(d0), vsel_vf_vo_f_f(o1, d1, d2));
}

static INLINE vfloat vsel_vf_vo_vo_vo_f_f_f_f(vopmask o0, vopmask o1, vopmask o2, float d0, float d1, float d2, float d3) {
  return vsel_vf_vo_vf_vf(o0, vcast_vf_f(d0), vsel_vf_vo_vf_vf(o1, vcast_vf_f(d1), vsel_vf_vo_f_f(o2, d2, d3)));
}

static INLINE vopmask visinf_vo_vf(vfloat d) { return veq_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(SLEEF_INFINITYf)); }
static INLINE vopmask vispinf_vo_vf(vfloat d) { return veq_vo_vf_vf(d, vcast_vf_f(SLEEF_INFINITYf)); }
static INLINE vopmask visminf_vo_vf(vfloat d) { return veq_vo_vf_vf(d, vcast_vf_f(-SLEEF_INFINITYf)); }
static INLINE vopmask visnan_vo_vf(vfloat d) { return vneq_vo_vf_vf(d, d); }

static INLINE vfloat vload_vf_p(const float *ptr) { return _mm_load_ps(ptr); }
static INLINE vfloat vloadu_vf_p(const float *ptr) { return _mm_loadu_ps(ptr); }

static INLINE void vstore_v_p_vf(float *ptr, vfloat v) { _mm_store_ps(ptr, v); }
static INLINE void vstoreu_v_p_vf(float *ptr, vfloat v) { _mm_storeu_ps(ptr, v); }

static INLINE vfloat vgather_vf_p_vi2(const float *ptr, vint2 vi) {
  int a[VECTLENSP];
  vstoreu_v_p_vi2(a, vi);
  return _mm_set_ps(ptr[a[3]], ptr[a[2]], ptr[a[1]], ptr[a[0]]);
}

// This function is for debugging
static INLINE float vcast_f_vf(vfloat v) {
  float a[VECTLENSP];
  vstoreu_v_p_vf(a, v);
  return a[0];
}

//

#define PNMASK ((vdouble) { +0.0, -0.0 })
#define NPMASK ((vdouble) { -0.0, +0.0 })
#define PNMASKf ((vfloat) { +0.0f, -0.0f, +0.0f, -0.0f })
#define NPMASKf ((vfloat) { -0.0f, +0.0f, -0.0f, +0.0f })

static INLINE vdouble vposneg_vd_vd(vdouble d) { return vreinterpret_vd_vm(vxor_vm_vm_vm(vreinterpret_vm_vd(d), vreinterpret_vm_vd(PNMASK))); }
static INLINE vdouble vnegpos_vd_vd(vdouble d) { return vreinterpret_vd_vm(vxor_vm_vm_vm(vreinterpret_vm_vd(d), vreinterpret_vm_vd(NPMASK))); }
static INLINE vfloat vposneg_vf_vf(vfloat d) { return vreinterpret_vf_vm(vxor_vm_vm_vm(vreinterpret_vm_vf(d), vreinterpret_vm_vf(PNMASKf))); }
static INLINE vfloat vnegpos_vf_vf(vfloat d) { return vreinterpret_vf_vm(vxor_vm_vm_vm(vreinterpret_vm_vf(d), vreinterpret_vm_vf(NPMASKf))); }

#if CONFIG >= 3
static INLINE vdouble vsubadd_vd_vd_vd(vdouble x, vdouble y) { return _mm_addsub_pd(x, y); }
static INLINE vfloat vsubadd_vf_vf_vf(vfloat x, vfloat y) { return _mm_addsub_ps(x, y); }
#else
static INLINE vdouble vsubadd_vd_vd_vd(vdouble x, vdouble y) { return vadd_vd_vd_vd(x, vnegpos_vd_vd(y)); }
static INLINE vfloat vsubadd_vf_vf_vf(vfloat x, vfloat y) { return vadd_vf_vf_vf(x, vnegpos_vf_vf(y)); }
#endif
static INLINE vdouble vmlsubadd_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return vsubadd_vd_vd_vd(vmul_vd_vd_vd(x, y), z); }
static INLINE vfloat vmlsubadd_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vsubadd_vf_vf_vf(vmul_vf_vf_vf(x, y), z); }

static INLINE vdouble vrev21_vd_vd(vdouble d0) { return _mm_shuffle_pd(d0, d0, 1); }
static INLINE vdouble vreva2_vd_vd(vdouble vd) { return vd; }

static INLINE void vstream_v_p_vd(double *ptr, vdouble v) { _mm_stream_pd(ptr, v); }
static INLINE void vscatter2_v_p_i_i_vd(double *ptr, int offset, int step, vdouble v) { vstore_v_p_vd((double *)(&ptr[2*offset]), v); }
static INLINE void vsscatter2_v_p_i_i_vd(double *ptr, int offset, int step, vdouble v) { _mm_stream_pd((double *)(&ptr[2*offset]), v); }

//

static INLINE vfloat vrev21_vf_vf(vfloat d0) { return _mm_shuffle_ps(d0, d0, (2 << 6) | (3 << 4) | (0 << 2) | (1 << 0)); }
static INLINE vfloat vreva2_vf_vf(vfloat d0) { return _mm_shuffle_ps(d0, d0, (1 << 6) | (0 << 4) | (3 << 2) | (2 << 0)); }

static INLINE void vstream_v_p_vf(float *ptr, vfloat v) { _mm_stream_ps(ptr, v); }

static INLINE void vscatter2_v_p_i_i_vf(float *ptr, int offset, int step, vfloat v) {
  _mm_storel_pd((double *)(ptr+(offset + step * 0)*2), vreinterpret_vd_vm(vreinterpret_vm_vf(v)));
  _mm_storeh_pd((double *)(ptr+(offset + step * 1)*2), vreinterpret_vd_vm(vreinterpret_vm_vf(v)));
}

static INLINE void vsscatter2_v_p_i_i_vf(float *ptr, int offset, int step, vfloat v) {
  _mm_storel_pd((double *)(ptr+(offset + step * 0)*2), vreinterpret_vd_vm(vreinterpret_vm_vf(v)));
  _mm_storeh_pd((double *)(ptr+(offset + step * 1)*2), vreinterpret_vd_vm(vreinterpret_vm_vf(v)));
}

//

static vquad loadu_vq_p(void *p) {
  vquad vq;
  memcpy(&vq, p, VECTLENDP * 16);
  return vq;
}

static INLINE vquad cast_vq_aq(vargquad aq) {
  vquad vq;
  memcpy(&vq, &aq, VECTLENDP * 16);
  return vq;
}

static INLINE vargquad cast_aq_vq(vquad vq) {
  vargquad aq;
  memcpy(&aq, &vq, VECTLENDP * 16);
  return aq;
}

static INLINE int vtestallzeros_i_vo64(vopmask g) { return _mm_movemask_epi8(g) == 0; }

static INLINE vmask vsel_vm_vo64_vm_vm(vopmask o, vmask x, vmask y) {
  return vor_vm_vm_vm(vand_vm_vm_vm(o, x), vandnot_vm_vm_vm(o, y));
}

static INLINE vmask vsub64_vm_vm_vm(vmask x, vmask y) { return _mm_sub_epi64(x, y); }
static INLINE vmask vneg64_vm_vm(vmask x) { return _mm_sub_epi64(vcast_vm_i_i(0, 0), x); }

#define vsll64_vm_vm_i(x, c) _mm_slli_epi64(x, c)
#define vsrl64_vm_vm_i(x, c) _mm_srli_epi64(x, c)
//@#define vsll64_vm_vm_i(x, c) _mm_slli_epi64(x, c)
//@#define vsrl64_vm_vm_i(x, c) _mm_srli_epi64(x, c)

static INLINE vopmask vgt64_vo_vm_vm(vmask x, vmask y) {
  int64_t ax[2], ay[2];
  _mm_storeu_si128((__m128i *)ax, x);
  _mm_storeu_si128((__m128i *)ay, y);
  return _mm_set_epi64x(ax[1] > ay[1] ? -1 : 0, ax[0] > ay[0] ? -1 : 0);
}

static INLINE vmask vcast_vm_vi(vint vi) {
  vmask m = _mm_and_si128(_mm_shuffle_epi32(vi, (0 << 6) | (1 << 4) | (0 << 2) | (0 << 0)), _mm_set_epi32(0, -1, 0, -1));
  return vor_vm_vm_vm(vcastu_vm_vi(vgt_vo_vi_vi(vcast_vi_i(0), vi)), m);
}
static INLINE vint vcast_vi_vm(vmask vm) { return _mm_shuffle_epi32(vm, 0x08); }

static INLINE vmask vreinterpret_vm_vi64(vint64 v) { return v; }
static INLINE vint64 vreinterpret_vi64_vm(vmask m) { return m; }
static INLINE vmask vreinterpret_vm_vu64(vuint64 v) { return v; }
static INLINE vuint64 vreinterpret_vu64_vm(vmask m) { return m; }
