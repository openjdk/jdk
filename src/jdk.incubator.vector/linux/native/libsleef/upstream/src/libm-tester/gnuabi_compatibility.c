//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

/// This program makes sure that all the symbols that a
/// GNUABI-compatible compiler (clang or gcc) can generate when
/// vectorizing functions call from `#include <math.h>` are present in
/// `libsleefgnuabi.so`.
///
/// The header `math.h` is not the same on all systems, and different
/// macros can activate different sets of functions. The list provide
/// here shoudl cover the union of all possible systems that we want
/// to support. In particular, the test is checking that the "finite"
/// symmbols from `#include <bits/math-finite.h>` are present for
/// those systems supporting them.

#include <setjmp.h>
#include <stdio.h>
#include <string.h>

#if defined(ENABLE_SSE4) || defined(ENABLE_SSE2)
#include <x86intrin.h>

#define ISA_TOKEN b
#define VLEN_SP 4
#define VLEN_DP 2
#define VECTOR_CC

typedef __m128i vopmask;
typedef __m128d vdouble;
typedef __m128  vfloat;
typedef __m128i vint;
typedef __m128i vint2;
#endif /* defined(ENABLE_SSE4) || defined(ENABLE_SSE2) */

#ifdef ENABLE_AVX
#include <x86intrin.h>

#define ISA_TOKEN c
#define VLEN_SP 8
#define VLEN_DP 4
#define VECTOR_CC

typedef __m256i vopmask;
typedef __m256d vdouble;
typedef __m256 vfloat;
typedef __m128i vint;
typedef struct { __m128i x, y; } vint2;
#endif /* ENABLE_AVX */

#ifdef ENABLE_AVX2
#include <x86intrin.h>

#define ISA_TOKEN d
#define VLEN_SP 8
#define VLEN_DP 4
#define VECTOR_CC

typedef __m256i vopmask;
typedef __m256d vdouble;
typedef __m256 vfloat;
typedef __m128i vint;
typedef __m256i vint2;
#endif /* ENABLE_AVX2 */

#ifdef ENABLE_AVX512F
#include <x86intrin.h>

#define ISA_TOKEN e
#define VLEN_SP 16
#define VLEN_DP 8
#define VECTOR_CC

typedef __mmask16 vopmask;
typedef __m512d vdouble;
typedef __m512 vfloat;
typedef __m256i vint;
typedef __m512i vint2;
#endif /* ENABLE_AVX512F */

#ifdef ENABLE_ADVSIMD
#include <arm_neon.h>
#define ISA_TOKEN n
#define VLEN_DP 2
#define VLEN_SP 4

#ifdef ENABLE_AAVPCS
#define VECTOR_CC __attribute__((aarch64_vector_pcs))
#else
#define VECTOR_CC
#endif

typedef uint32x4_t vopmask;
typedef float64x2_t vdouble;
typedef float32x4_t vfloat;
typedef int32x2_t vint;
typedef int32x4_t vint2;
#endif /* ENABLE_ADVSIMDF */

#ifdef ENABLE_SVE
#include <arm_sve.h>
#define ISA_TOKEN s
#define VLEN_SP (svcntw())
#define VLEN_DP (svcntd())
#define VLA_TOKEN x
#define VECTOR_CC

typedef svbool_t vopmask;
typedef svfloat64_t vdouble;
typedef svfloat32_t vfloat;
typedef svint32_t vint;
typedef svint32_t vint2;
#endif /* ENABLE_SVE */

// GNUABI name mangling macro.
#ifndef MASKED_GNUABI

#define __MAKE_FN_NAME(name, t, vl, p) _ZGV##t##N##vl##p##_##name

#define __DECLARE_vd_vd(name, t, vl, p)					\
  extern vdouble VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vdouble)
#define __CALL_vd_vd(name, t, vl, p)				\
  do { vd0 = __MAKE_FN_NAME(name, t, vl, p)(vd1); } while(0)

#define __DECLARE_vi_vd(name, t, vl, p)				\
  extern vint VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vdouble)
#define __CALL_vi_vd(name, t, vl, p)				\
  do { vi0 = __MAKE_FN_NAME(name, t, vl, p)(vd1); } while(0)

#define __DECLARE_vd_vd_vi(name, t, vl, p)				\
  extern vdouble VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vdouble, vint)
#define __CALL_vd_vd_vi(name, t, vl, p)					\
  do { vd0 = __MAKE_FN_NAME(name, t, vl, p)(vd1, vi2); } while(0)

#define __DECLARE_vd_vd_vd(name, t, vl, p)				\
  extern vdouble VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vdouble, vdouble)
#define __CALL_vd_vd_vd(name, t, vl, p)					\
  do { vd0 = __MAKE_FN_NAME(name, t, vl, p)(vd1, vd2); } while(0)

#define __DECLARE_vd_vd_vd_vd(name, t, vl, p)				\
  extern vdouble VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vdouble, vdouble, vdouble)
#define __CALL_vd_vd_vd_vd(name, t, vl, p)				\
  do { vd0 = __MAKE_FN_NAME(name, t, vl, p)(vd1, vd2, vd3); } while(0)

#define __DECLARE_vd_vd_pvd(name, t, vl, p)				\
  extern vdouble VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vdouble, vdouble *)
#define __CALL_vd_vd_pvd(name, t, vl, p)				\
  do { vd0 = __MAKE_FN_NAME(name, t, vl, p)(vd1, &vd2); } while(0)

#define __DECLARE_v_vd_pvd_pvd(name, t, vl, p)				\
  extern void VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vdouble, vdouble *, vdouble *)
#define __CALL_v_vd_pvd_pvd(name, t, vl, p)				\
  do { __MAKE_FN_NAME(name, t, vl, p)(vd0, &vd1, &vd2); } while(0)

#define __DECLARE_vf_vf(name, t, vl, p)					\
  extern vfloat VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vfloat)
#define __CALL_vf_vf(name, t, vl, p)				\
  do { vf0 = __MAKE_FN_NAME(name, t, vl, p)(vf1); } while(0)

#define __DECLARE_vf_vf_vf(name, t, vl, p)				\
  extern vfloat VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vfloat, vfloat)
#define __CALL_vf_vf_vf(name, t, vl, p)					\
  do { vf0 = __MAKE_FN_NAME(name, t, vl, p)(vf1, vf2); } while(0)

#define __DECLARE_vf_vf_vf_vf(name, t, vl, p)				\
  extern vfloat VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vfloat, vfloat, vfloat)
#define __CALL_vf_vf_vf_vf(name, t, vl, p)				\
  do { vf0 = __MAKE_FN_NAME(name, t, vl, p)(vf1, vf2, vf3); } while(0)

#define __DECLARE_vf_vf_pvf(name, t, vl, p)				\
  extern vfloat VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vfloat, vfloat *)
#define __CALL_vf_vf_pvf(name, t, vl, p)				\
  do { vf0 = __MAKE_FN_NAME(name, t, vl, p)(vf1, &vf2); } while(0)

#define __DECLARE_vi_vf(name, t, vl, p)				\
  extern vint2 VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vfloat)
#define __CALL_vi_vf(name, t, vl, p)				\
  do { vi20 = __MAKE_FN_NAME(name, t, vl, p)(vf1); } while(0)

#define __DECLARE_vf_vf_vi(name, t, vl, p)				\
  extern vfloat VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vfloat, vint2)
#define __CALL_vf_vf_vi(name, t, vl, p)					\
  do { vf0 = __MAKE_FN_NAME(name, t, vl, p)(vf1, vi22); } while(0)

#define __DECLARE_v_vf_pvf_pvf(name, t, vl, p)				\
  extern vfloat VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vfloat, vfloat *, vfloat*)
#define __CALL_v_vf_pvf_pvf(name, t, vl, p)				\
  do { __MAKE_FN_NAME(name, t, vl, p)(vf0, &vf1, &vf2); } while(0)

#else /******************** MASKED_GNUABI *****************************/

#define __MAKE_FN_NAME(name, t, vl, p) _ZGV##t##M##vl##p##_##name

#define __DECLARE_vd_vd(name, t, vl, p)					\
  extern vdouble VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vdouble, vopmask)
#define __CALL_vd_vd(name, t, vl, p)					\
  do { vd0 = __MAKE_FN_NAME(name, t, vl, p)(vd1, mask); } while(0)

#define __DECLARE_vi_vd(name, t, vl, p)					\
  extern vint VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vdouble, vopmask)
#define __CALL_vi_vd(name, t, vl, p)					\
  do { vi0 = __MAKE_FN_NAME(name, t, vl, p)(vd1, mask); } while(0)

#define __DECLARE_vd_vd_vi(name, t, vl, p)				\
  extern vdouble VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vdouble, vint, vopmask)
#define __CALL_vd_vd_vi(name, t, vl, p)					\
  do { vd0 = __MAKE_FN_NAME(name, t, vl, p)(vd1, vi2, mask); } while(0)

#define __DECLARE_vd_vd_vd(name, t, vl, p)				\
  extern vdouble VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vdouble, vdouble, vopmask)
#define __CALL_vd_vd_vd(name, t, vl, p)					\
  do { vd0 = __MAKE_FN_NAME(name, t, vl, p)(vd1, vd2, mask); } while(0)

#define __DECLARE_vd_vd_vd_vd(name, t, vl, p)				\
  extern vdouble VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vdouble, vdouble, vdouble, vopmask)
#define __CALL_vd_vd_vd_vd(name, t, vl, p)				\
  do { vd0 = __MAKE_FN_NAME(name, t, vl, p)(vd1, vd2, vd3, mask); } while(0)

#define __DECLARE_vd_vd_pvd(name, t, vl, p)				\
  extern vdouble VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vdouble, vdouble *, vopmask)
#define __CALL_vd_vd_pvd(name, t, vl, p)				\
  do { vd0 = __MAKE_FN_NAME(name, t, vl, p)(vd1, &vd2, mask); } while(0)

#define __DECLARE_v_vd_pvd_pvd(name, t, vl, p)				\
  extern void VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vdouble, vdouble *, vdouble *, vopmask)
#define __CALL_v_vd_pvd_pvd(name, t, vl, p)				\
  do { __MAKE_FN_NAME(name, t, vl, p)(vd0, &vd1, &vd2, mask); } while(0)

#define __DECLARE_vf_vf(name, t, vl, p)					\
  extern vfloat VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vfloat, vopmask)
#define __CALL_vf_vf(name, t, vl, p)					\
  do { vf0 = __MAKE_FN_NAME(name, t, vl, p)(vf1, mask); } while(0)

#define __DECLARE_vf_vf_vf(name, t, vl, p)				\
  extern vfloat VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vfloat, vfloat, vopmask)
#define __CALL_vf_vf_vf(name, t, vl, p)					\
  do { vf0 = __MAKE_FN_NAME(name, t, vl, p)(vf1, vf2, mask); } while(0)

#define __DECLARE_vf_vf_vf_vf(name, t, vl, p)				\
  extern vfloat VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vfloat, vfloat, vfloat, vopmask)
#define __CALL_vf_vf_vf_vf(name, t, vl, p)				\
  do { vf0 = __MAKE_FN_NAME(name, t, vl, p)(vf1, vf2, vf3, mask); } while(0)

#define __DECLARE_vf_vf_pvf(name, t, vl, p)				\
  extern vfloat VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vfloat, vfloat *, vopmask)
#define __CALL_vf_vf_pvf(name, t, vl, p)				\
  do { vf0 = __MAKE_FN_NAME(name, t, vl, p)(vf1, &vf2, mask); } while(0)

#define __DECLARE_vi_vf(name, t, vl, p)					\
  extern vint2 VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vfloat, vopmask)
#define __CALL_vi_vf(name, t, vl, p)					\
  do { vi20 = __MAKE_FN_NAME(name, t, vl, p)(vf1, mask); } while(0)

#define __DECLARE_vf_vf_vi(name, t, vl, p)				\
  extern vfloat VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vfloat, vint2, vopmask)
#define __CALL_vf_vf_vi(name, t, vl, p)					\
  do { vf0 = __MAKE_FN_NAME(name, t, vl, p)(vf1, vi22, mask); } while(0)

#define __DECLARE_v_vf_pvf_pvf(name, t, vl, p)				\
  extern vfloat VECTOR_CC __MAKE_FN_NAME(name, t, vl, p)(vfloat, vfloat *, vfloat*, vopmask)
#define __CALL_v_vf_pvf_pvf(name, t, vl, p)				\
  do { __MAKE_FN_NAME(name, t, vl, p)(vf0, &vf1, &vf2, mask); } while(0)

#endif /* MASKED_GNUABI */
// Level-1 expansion macros for declaration and call. The signature of
// each function has three input paramters to avoid segfaults of
// sincos-like functions that are effectively loading data from
// memory.


// Make sure that the architectural macros are defined for each vector
// extension.
#ifndef ISA_TOKEN
#error "Missing ISA token"
#endif

#ifndef VLEN_DP
#error "Missing VLEN_DP"
#endif

#ifndef VLEN_DP
#error "Missing VLEN_SP"
#endif

#if defined(ENABLE_SVE) && !defined(VLA_TOKEN)
#error "Missing VLA_TOKEN"
#endif /* defined(ENABLE_SVE) && !defined(VLA_TOKEN) */

// Declaration and call, first level expantion to pick up the
// ISA_TOKEN and VLEN_* architectural macros.
#ifndef ENABLE_SVE

#define DECLARE_DP_vd_vd(name, p) __DECLARE_vd_vd(name, ISA_TOKEN, VLEN_DP, p)
#define CALL_DP_vd_vd(name, p) __CALL_vd_vd(name, ISA_TOKEN, VLEN_DP, p)

#define DECLARE_DP_vd_vd_vd(name, p) __DECLARE_vd_vd_vd(name, ISA_TOKEN, VLEN_DP, p)
#define CALL_DP_vd_vd_vd(name, p) __CALL_vd_vd_vd(name, ISA_TOKEN, VLEN_DP, p)

#define DECLARE_DP_vd_vd_vd_vd(name, p) __DECLARE_vd_vd_vd_vd(name, ISA_TOKEN, VLEN_DP, p)
#define CALL_DP_vd_vd_vd_vd(name, p) __CALL_vd_vd_vd_vd(name, ISA_TOKEN, VLEN_DP, p)

#define DECLARE_DP_vd_vd_pvd(name, p) __DECLARE_vd_vd_pvd(name, ISA_TOKEN, VLEN_DP, p)
#define CALL_DP_vd_vd_pvd(name, p) __CALL_vd_vd_pvd(name, ISA_TOKEN, VLEN_DP, p)

#define DECLARE_DP_vi_vd(name, p) __DECLARE_vi_vd(name, ISA_TOKEN, VLEN_DP, p)
#define CALL_DP_vi_vd(name, p) __CALL_vi_vd(name, ISA_TOKEN, VLEN_DP, p)

#define DECLARE_DP_vd_vd_vi(name, p) __DECLARE_vd_vd_vi(name, ISA_TOKEN, VLEN_DP, p)
#define CALL_DP_vd_vd_vi(name, p) __CALL_vd_vd_vi(name, ISA_TOKEN, VLEN_DP, p)

#define DECLARE_DP_v_vd_pvd_pvd(name, p) __DECLARE_v_vd_pvd_pvd(name, ISA_TOKEN, VLEN_DP, p)
#define CALL_DP_v_vd_pvd_pvd(name, p) __CALL_v_vd_pvd_pvd(name, ISA_TOKEN, VLEN_DP, p)

#define DECLARE_SP_vf_vf(name, p) __DECLARE_vf_vf(name, ISA_TOKEN, VLEN_SP, p)
#define CALL_SP_vf_vf(name, p) __CALL_vf_vf(name, ISA_TOKEN, VLEN_SP, p)

#define DECLARE_SP_vf_vf_vf(name, p) __DECLARE_vf_vf_vf(name, ISA_TOKEN, VLEN_SP, p)
#define CALL_SP_vf_vf_vf(name, p) __CALL_vf_vf_vf(name, ISA_TOKEN, VLEN_SP, p)

#define DECLARE_SP_vf_vf_vf_vf(name, p) __DECLARE_vf_vf_vf_vf(name, ISA_TOKEN, VLEN_SP, p)
#define CALL_SP_vf_vf_vf_vf(name, p) __CALL_vf_vf_vf_vf(name, ISA_TOKEN, VLEN_SP, p)

#define DECLARE_SP_vf_vf_pvf(name, p) __DECLARE_vf_vf_pvf(name, ISA_TOKEN, VLEN_SP, p)
#define CALL_SP_vf_vf_pvf(name, p) __CALL_vf_vf_pvf(name, ISA_TOKEN, VLEN_SP, p)

#define DECLARE_SP_vi_vf(name, p) __DECLARE_vi_vf(name, ISA_TOKEN, VLEN_SP, p)
#define CALL_SP_vi_vf(name, p) __CALL_vi_vf(name, ISA_TOKEN, VLEN_SP, p)

#define DECLARE_SP_vf_vf_vi(name, p) __DECLARE_vf_vf_vi(name, ISA_TOKEN, VLEN_SP, p)
#define CALL_SP_vf_vf_vi(name, p) __CALL_vf_vf_vi(name, ISA_TOKEN, VLEN_SP, p)

#define DECLARE_SP_v_vf_pvf_pvf(name, p) __DECLARE_v_vf_pvf_pvf(name, ISA_TOKEN, VLEN_SP, p)
#define CALL_SP_v_vf_pvf_pvf(name, p) __CALL_v_vf_pvf_pvf(name, ISA_TOKEN, VLEN_SP, p)

#else /* ENABLE_SVE */

#define DECLARE_DP_vd_vd(name, p) __DECLARE_vd_vd(name, ISA_TOKEN, VLA_TOKEN, p)
#define CALL_DP_vd_vd(name, p) __CALL_vd_vd(name, ISA_TOKEN, VLA_TOKEN, p); svst1_f64(svptrue_b8(), (double *)outbuf, vd0)

#define DECLARE_DP_vd_vd_vd(name, p) __DECLARE_vd_vd_vd(name, ISA_TOKEN, VLA_TOKEN, p)
#define CALL_DP_vd_vd_vd(name, p) __CALL_vd_vd_vd(name, ISA_TOKEN, VLA_TOKEN, p); svst1_f64(svptrue_b8(), (double *)outbuf, vd0)

#define DECLARE_DP_vd_vd_vd_vd(name, p) __DECLARE_vd_vd_vd_vd(name, ISA_TOKEN, VLA_TOKEN, p)
#define CALL_DP_vd_vd_vd_vd(name, p) __CALL_vd_vd_vd_vd(name, ISA_TOKEN, VLA_TOKEN, p); svst1_f64(svptrue_b8(), (double *)outbuf, vd0)

#define DECLARE_DP_vd_vd_pvd(name, p) __DECLARE_vd_vd_pvd(name, ISA_TOKEN, VLA_TOKEN, p)
#define CALL_DP_vd_vd_pvd(name, p) __CALL_vd_vd_pvd(name, ISA_TOKEN, VLA_TOKEN, p); svst1_f64(svptrue_b8(), (double *)outbuf, vd2)

#define DECLARE_DP_vi_vd(name, p) __DECLARE_vi_vd(name, ISA_TOKEN, VLA_TOKEN, p)
#define CALL_DP_vi_vd(name, p) __CALL_vi_vd(name, ISA_TOKEN, VLA_TOKEN, p); svst1_s32(svptrue_b8(), (int *)outbuf, vi0)

#define DECLARE_DP_vd_vd_vi(name, p) __DECLARE_vd_vd_vi(name, ISA_TOKEN, VLA_TOKEN, p)
#define CALL_DP_vd_vd_vi(name, p) __CALL_vd_vd_vi(name, ISA_TOKEN, VLA_TOKEN, p); svst1_f64(svptrue_b8(), (double *)outbuf, vd0)

#define DECLARE_DP_v_vd_pvd_pvd(name, p) __DECLARE_v_vd_pvd_pvd(name, ISA_TOKEN, VLA_TOKEN, p)
#define CALL_DP_v_vd_pvd_pvd(name, p) __CALL_v_vd_pvd_pvd(name, ISA_TOKEN, VLA_TOKEN, p); svst1_f64(svptrue_b8(), (double *)outbuf, vd2)

#define DECLARE_SP_vf_vf(name, p) __DECLARE_vf_vf(name, ISA_TOKEN, VLA_TOKEN, p)
#define CALL_SP_vf_vf(name, p) __CALL_vf_vf(name, ISA_TOKEN, VLA_TOKEN, p); svst1_f32(svptrue_b8(), (float *)outbuf, vf0)

#define DECLARE_SP_vf_vf_vf(name, p) __DECLARE_vf_vf_vf(name, ISA_TOKEN, VLA_TOKEN, p)
#define CALL_SP_vf_vf_vf(name, p) __CALL_vf_vf_vf(name, ISA_TOKEN, VLA_TOKEN, p); svst1_f32(svptrue_b8(), (float *)outbuf, vf0)

#define DECLARE_SP_vf_vf_vf_vf(name, p) __DECLARE_vf_vf_vf_vf(name, ISA_TOKEN, VLA_TOKEN, p)
#define CALL_SP_vf_vf_vf_vf(name, p) __CALL_vf_vf_vf_vf(name, ISA_TOKEN, VLA_TOKEN, p); svst1_f32(svptrue_b8(), (float *)outbuf, vf0)

#define DECLARE_SP_vf_vf_pvf(name, p) __DECLARE_vf_vf_pvf(name, ISA_TOKEN, VLA_TOKEN, p)
#define CALL_SP_vf_vf_pvf(name, p) __CALL_vf_vf_pvf(name, ISA_TOKEN, VLA_TOKEN, p); svst1_f32(svptrue_b8(), (float *)outbuf, vf2)

#define DECLARE_SP_vi_vf(name, p) __DECLARE_vi_vf(name, ISA_TOKEN, VLA_TOKEN, p)
#define CALL_SP_vi_vf(name, p) __CALL_vi_vf(name, ISA_TOKEN, VLA_TOKEN, p); svst1_s32(svptrue_b8(), (int *)outbuf, vi20)

#define DECLARE_SP_vf_vf_vi(name, p) __DECLARE_vf_vf_vi(name, ISA_TOKEN, VLA_TOKEN, p)
#define CALL_SP_vf_vf_vi(name, p) __CALL_vf_vf_vi(name, ISA_TOKEN, VLA_TOKEN, p); svst1_f32(svptrue_b8(), (float *)outbuf, vf0)

#define DECLARE_SP_v_vf_pvf_pvf(name, p) __DECLARE_v_vf_pvf_pvf(name, ISA_TOKEN, VLA_TOKEN, p)
#define CALL_SP_v_vf_pvf_pvf(name, p) __CALL_v_vf_pvf_pvf(name, ISA_TOKEN, VLA_TOKEN, p); svst1_f32(svptrue_b8(), (float *)outbuf, vf2)

#endif /* ENABLE_SVE */

//

// Douple precision function declarations.
DECLARE_DP_vd_vd(__acos_finite, v);
DECLARE_DP_vd_vd(__acosh_finite, v);
DECLARE_DP_vd_vd(__asin_finite, v);
DECLARE_DP_vd_vd_vd(__atan2_finite, vv);
DECLARE_DP_vd_vd(__atanh_finite, v);
DECLARE_DP_vd_vd(__cosh_finite, v);
DECLARE_DP_vd_vd(__exp10_finite, v);
DECLARE_DP_vd_vd(__exp2_finite, v);
DECLARE_DP_vd_vd(__exp_finite, v);
DECLARE_DP_vd_vd_vd(__fmod_finite, vv);
DECLARE_DP_vd_vd_pvd(__modf_finite, vl8);
DECLARE_DP_vd_vd_vd(__hypot_finite, vv);
DECLARE_DP_vd_vd(__log10_finite, v);
// DECLARE_DP_vd_vd(__log2_finite,v);
DECLARE_DP_vd_vd(__log_finite, v);
DECLARE_DP_vd_vd_vd(__pow_finite, vv);
DECLARE_DP_vd_vd(__sinh_finite, v);
DECLARE_DP_vd_vd(__sqrt_finite, v);
DECLARE_DP_vd_vd(acos, v);
DECLARE_DP_vd_vd(acosh, v);
DECLARE_DP_vd_vd(asin, v);
DECLARE_DP_vd_vd(asinh, v);
DECLARE_DP_vd_vd(atan, v);
DECLARE_DP_vd_vd_vd(atan2, vv);
DECLARE_DP_vd_vd_vd(__atan2_finite, vv);
DECLARE_DP_vd_vd(atanh, v);
DECLARE_DP_vd_vd(cbrt, v);
DECLARE_DP_vd_vd(ceil, v);
DECLARE_DP_vd_vd_vd(copysign, vv);
DECLARE_DP_vd_vd(cos, v);
DECLARE_DP_vd_vd(cosh, v);
DECLARE_DP_vd_vd(cospi, v);
DECLARE_DP_vd_vd(erf, v);
DECLARE_DP_vd_vd(erfc, v);
DECLARE_DP_vd_vd(exp, v);
DECLARE_DP_vd_vd(exp10, v);
DECLARE_DP_vd_vd(exp2, v);
DECLARE_DP_vi_vd(expfrexp, v);
DECLARE_DP_vd_vd(expm1, v);
DECLARE_DP_vd_vd(fabs, v);
DECLARE_DP_vd_vd_vd(fdim, vv);
DECLARE_DP_vd_vd(floor, v);
DECLARE_DP_vd_vd_vd_vd(fma, vvv);
DECLARE_DP_vd_vd_vd(fmax, vv);
DECLARE_DP_vd_vd_vd(fmin, vv);
DECLARE_DP_vd_vd_vd(fmod, vv);
DECLARE_DP_vd_vd(frfrexp, v);
DECLARE_DP_vd_vd_vd(hypot, vv);
DECLARE_DP_vi_vd(ilogb, v);
DECLARE_DP_vd_vd_vi(ldexp, vv);
DECLARE_DP_vd_vd(lgamma, v);
DECLARE_DP_vd_vd(log, v);
DECLARE_DP_vd_vd(log10, v);
DECLARE_DP_vd_vd(log1p, v);
DECLARE_DP_vd_vd(log2, v);
DECLARE_DP_vd_vd_pvd(modf, vl8);
DECLARE_DP_vd_vd_vd(nextafter, vv);
DECLARE_DP_vd_vd_vd(pow, vv);
DECLARE_DP_vd_vd(rint, v);
DECLARE_DP_vd_vd(round, v);
DECLARE_DP_vd_vd(sin, v);
DECLARE_DP_v_vd_pvd_pvd(sincos, vl8l8);
DECLARE_DP_v_vd_pvd_pvd(sincospi, vl8l8);
DECLARE_DP_vd_vd(sinh, v);
DECLARE_DP_vd_vd(sinpi, v);
DECLARE_DP_vd_vd(sqrt, v);
DECLARE_DP_vd_vd(tan, v);
DECLARE_DP_vd_vd(tanh, v);
DECLARE_DP_vd_vd(tgamma, v);
DECLARE_DP_vd_vd(trunc, v);

// Single precision function declarations.
DECLARE_SP_vf_vf(__acosf_finite, v);
DECLARE_SP_vf_vf(__acoshf_finite, v);
DECLARE_SP_vf_vf(__asinf_finite, v);
DECLARE_SP_vf_vf_vf(__atan2f_finite, vv);
DECLARE_SP_vf_vf(__atanhf_finite, v);
DECLARE_SP_vf_vf(__coshf_finite, v);
DECLARE_SP_vf_vf(__exp10f_finite, v);
DECLARE_SP_vf_vf(__exp2f_finite, v);
DECLARE_SP_vf_vf(__expf_finite, v);
DECLARE_SP_vf_vf_vf(__fmodf_finite, vv);
DECLARE_SP_vf_vf_pvf(__modff_finite, vl4);
DECLARE_SP_vf_vf_vf(__hypotf_finite, vv);
DECLARE_SP_vf_vf(__log10f_finite, v);
// DECLARE_SP_vf_vf(__log2f_finite,v);
DECLARE_SP_vf_vf(__logf_finite, v);
DECLARE_SP_vf_vf_vf(__powf_finite, vv);
DECLARE_SP_vf_vf(__sinhf_finite, v);
DECLARE_SP_vf_vf(__sqrtf_finite, v);
DECLARE_SP_vf_vf(acosf, v);
DECLARE_SP_vf_vf(acoshf, v);
DECLARE_SP_vf_vf(asinf, v);
DECLARE_SP_vf_vf(asinhf, v);
DECLARE_SP_vf_vf(atanf, v);
DECLARE_SP_vf_vf_vf(atan2f, vv);
DECLARE_SP_vf_vf(atanhf, v);
DECLARE_SP_vf_vf(cbrtf, v);
DECLARE_SP_vf_vf(ceilf, v);
DECLARE_SP_vf_vf_vf(copysignf, vv);
DECLARE_SP_vf_vf(cosf, v);
DECLARE_SP_vf_vf(coshf, v);
DECLARE_SP_vf_vf(cospif, v);
DECLARE_SP_vf_vf(erff, v);
DECLARE_SP_vf_vf(erfcf, v);
DECLARE_SP_vf_vf(expf, v);
DECLARE_SP_vf_vf(exp10f, v);
DECLARE_SP_vf_vf(exp2f, v);
DECLARE_SP_vf_vf(expm1f, v);
DECLARE_SP_vf_vf(fabsf, v);
DECLARE_SP_vf_vf_vf(fdimf, vv);
DECLARE_SP_vf_vf(floorf, v);
DECLARE_SP_vf_vf_vf_vf(fmaf, vvv);
DECLARE_SP_vf_vf_vf(fmaxf, vv);
DECLARE_SP_vf_vf_vf(fminf, vv);
DECLARE_SP_vf_vf_vf(fmodf, vv);
DECLARE_SP_vf_vf(frfrexpf, v);
DECLARE_SP_vf_vf_vf(hypotf, vv);
#ifndef ENABLE_AVX
// These two functions are not checked in some configurations due to
// the issue in https://github.com/shibatch/sleef/issues/221
DECLARE_SP_vi_vf(expfrexpf, v);
DECLARE_SP_vi_vf(ilogbf, v);
#endif
DECLARE_SP_vf_vf_vi(ldexpf, vv);
DECLARE_SP_vf_vf(lgammaf, v);
DECLARE_SP_vf_vf(logf, v);
DECLARE_SP_vf_vf(log10f, v);
DECLARE_SP_vf_vf(log1pf, v);
DECLARE_SP_vf_vf(log2f, v);
DECLARE_SP_vf_vf_pvf(modff, vl4);
DECLARE_SP_vf_vf_vf(nextafterf, vv);
DECLARE_SP_vf_vf_vf(powf, vv);
DECLARE_SP_vf_vf(rintf, v);
DECLARE_SP_vf_vf(roundf, v);
DECLARE_SP_vf_vf(sinf, v);
DECLARE_SP_v_vf_pvf_pvf(sincosf, vl4l4);
DECLARE_SP_v_vf_pvf_pvf(sincospif, vl4l4);
DECLARE_SP_vf_vf(sinhf, v);
DECLARE_SP_vf_vf(sinpif, v);
DECLARE_SP_vf_vf(sqrtf, v);
DECLARE_SP_vf_vf(tanf, v);
DECLARE_SP_vf_vf(tanhf, v);
DECLARE_SP_vf_vf(tgammaf, v);
DECLARE_SP_vf_vf(truncf, v);

#ifndef ENABLE_SVE
vdouble vd0, vd1, vd2, vd3;
vfloat vf0, vf1, vf2, vf3;
vint vi0, vi1, vi2, vi3;
vint2 vi20, vi21, vi22, vi23;
vopmask mask;
#else
volatile char outbuf[1024];
#endif

int check_feature(double d, float f) {
#ifdef ENABLE_SVE
  vdouble vd0 = svdup_n_f64(d), vd1 = svdup_n_f64(d);
#ifdef MASKED_GNUABI
  vopmask mask = svcmpne_s32(svptrue_b8(), svdup_n_s32(f), svdup_n_s32(0));
#endif
#endif

  CALL_DP_vd_vd(__acos_finite, v);
#ifdef ENABLE_SVE
  svst1_f64(svptrue_b8(), (double *)outbuf, vd0);
#endif
  return 1;
}

int main2(int argc, char **argv) {
#ifdef ENABLE_SVE
  vdouble vd0 = svdup_n_f64(argc), vd1 = svdup_n_f64(argc), vd2 = svdup_n_f64(argc), vd3 = svdup_n_f64(argc);
  vfloat vf0 = svdup_n_f32(argc), vf1 = svdup_n_f32(argc), vf2 = svdup_n_f32(argc), vf3 = svdup_n_f32(argc);
  vint vi0 = svdup_n_s32(argc), vi2 = svdup_n_s32(argc);
  vint2 vi20 = svdup_n_s32(argc), vi22 = svdup_n_s32(argc);
#ifdef MASKED_GNUABI
  vopmask mask = svcmpne_s32(svptrue_b8(), svdup_n_s32(argc), svdup_n_s32(0));
#endif
#endif

  // Double precision function call.
  CALL_DP_vd_vd(__acos_finite, v);
  CALL_DP_vd_vd(__acosh_finite, v);
  CALL_DP_vd_vd(__asin_finite, v);
  CALL_DP_vd_vd_vd(__atan2_finite, vv);
  CALL_DP_vd_vd(__atanh_finite, v);
  CALL_DP_vd_vd(__cosh_finite, v);
  CALL_DP_vd_vd(__exp10_finite, v);
  CALL_DP_vd_vd(__exp2_finite, v);
  CALL_DP_vd_vd(__exp_finite, v);
  CALL_DP_vd_vd_vd(__fmod_finite, vv);
  CALL_DP_vd_vd_pvd(__modf_finite, vl8);
  CALL_DP_vd_vd_vd(__hypot_finite, vv);
  CALL_DP_vd_vd(__log10_finite, v);
  // CALL_DP_vd_vd(__log2_finite,v);
  CALL_DP_vd_vd(__log_finite, v);
  CALL_DP_vd_vd_vd(__pow_finite, vv);
  CALL_DP_vd_vd(__sinh_finite, v);
  CALL_DP_vd_vd(__sqrt_finite, v);
  CALL_DP_vd_vd(acos, v);
  CALL_DP_vd_vd(acosh, v);
  CALL_DP_vd_vd(asin, v);
  CALL_DP_vd_vd(asinh, v);
  CALL_DP_vd_vd(atan, v);
  CALL_DP_vd_vd_vd(atan2, vv);
  CALL_DP_vd_vd(atanh, v);
  CALL_DP_vd_vd(cbrt, v);
  CALL_DP_vd_vd(ceil, v);
  CALL_DP_vd_vd_vd(copysign, vv);
  CALL_DP_vd_vd(cos, v);
  CALL_DP_vd_vd(cosh, v);
  CALL_DP_vd_vd(cospi, v);
  CALL_DP_vd_vd(erf, v);
  CALL_DP_vd_vd(erfc, v);
  CALL_DP_vd_vd(exp, v);
  CALL_DP_vd_vd(exp10, v);
  CALL_DP_vd_vd(exp2, v);
  CALL_DP_vi_vd(expfrexp, v);
  CALL_DP_vd_vd(expm1, v);
  CALL_DP_vd_vd(fabs, v);
  CALL_DP_vd_vd_vd(fdim, vv);
  CALL_DP_vd_vd(floor, v);
  CALL_DP_vd_vd_vd_vd(fma, vvv);
  CALL_DP_vd_vd_vd(fmax, vv);
  CALL_DP_vd_vd_vd(fmin, vv);
  CALL_DP_vd_vd_vd(fmod, vv);
  CALL_DP_vd_vd(frfrexp, v);
  CALL_DP_vd_vd_vd(hypot, vv);
  CALL_DP_vi_vd(ilogb, v);
  CALL_DP_vd_vd_vi(ldexp, vv);
  CALL_DP_vd_vd(lgamma, v);
  CALL_DP_vd_vd(log, v);
  CALL_DP_vd_vd(log10, v);
  CALL_DP_vd_vd(log1p, v);
  CALL_DP_vd_vd(log2, v);
  CALL_DP_vd_vd_pvd(modf, vl8);
  CALL_DP_vd_vd_vd(nextafter, vv);
  CALL_DP_vd_vd_vd(pow, vv);
  CALL_DP_vd_vd(rint, v);
  CALL_DP_vd_vd(round, v);
  CALL_DP_vd_vd(sin, v);
  CALL_DP_v_vd_pvd_pvd(sincos, vl8l8);
  CALL_DP_v_vd_pvd_pvd(sincospi, vl8l8);
  CALL_DP_vd_vd(sinh, v);
  CALL_DP_vd_vd(sinpi, v);
  CALL_DP_vd_vd(sqrt, v);
  CALL_DP_vd_vd(tan, v);
  CALL_DP_vd_vd(tanh, v);
  CALL_DP_vd_vd(tgamma, v);
  CALL_DP_vd_vd(trunc, v);

  // Single precision function call.
  CALL_SP_vf_vf(__acosf_finite, v);
  CALL_SP_vf_vf(__acoshf_finite, v);
  CALL_SP_vf_vf(__asinf_finite, v);
  CALL_SP_vf_vf_vf(__atan2f_finite, vv);
  CALL_SP_vf_vf(__atanhf_finite, v);
  CALL_SP_vf_vf(__coshf_finite, v);
  CALL_SP_vf_vf(__exp10f_finite, v);
  CALL_SP_vf_vf(__exp2f_finite, v);
  CALL_SP_vf_vf(__expf_finite, v);
  CALL_SP_vf_vf_vf(__fmodf_finite, vv);
  CALL_SP_vf_vf_pvf(__modff_finite, vl4);
  CALL_SP_vf_vf_vf(__hypotf_finite, vv);
  CALL_SP_vf_vf(__log10f_finite, v);
  // CALL_SP_vf_vf(__log2f_finite,v);
  CALL_SP_vf_vf(__logf_finite, v);
  CALL_SP_vf_vf_vf(__powf_finite, vv);
  CALL_SP_vf_vf(__sinhf_finite, v);
  CALL_SP_vf_vf(__sqrtf_finite, v);
  CALL_SP_vf_vf(acosf, v);
  CALL_SP_vf_vf(acoshf, v);
  CALL_SP_vf_vf(asinf, v);
  CALL_SP_vf_vf(asinhf, v);
  CALL_SP_vf_vf(atanf, v);
  CALL_SP_vf_vf_vf(atan2f, vv);
  CALL_SP_vf_vf(atanhf, v);
  CALL_SP_vf_vf(cbrtf, v);
  CALL_SP_vf_vf(ceilf, v);
  CALL_SP_vf_vf_vf(copysignf, vv);
  CALL_SP_vf_vf(cosf, v);
  CALL_SP_vf_vf(coshf, v);
  CALL_SP_vf_vf(cospif, v);
  CALL_SP_vf_vf(erff, v);
  CALL_SP_vf_vf(erfcf, v);
  CALL_SP_vf_vf(expf, v);
  CALL_SP_vf_vf(exp10f, v);
  CALL_SP_vf_vf(exp2f, v);
  CALL_SP_vf_vf(expm1f, v);
  CALL_SP_vf_vf(fabsf, v);
  CALL_SP_vf_vf_vf(fdimf, vv);
  CALL_SP_vf_vf(floorf, v);
  CALL_SP_vf_vf_vf_vf(fmaf, vvv);
  CALL_SP_vf_vf_vf(fmaxf, vv);
  CALL_SP_vf_vf_vf(fminf, vv);
  CALL_SP_vf_vf_vf(fmodf, vv);
  CALL_SP_vf_vf(frfrexpf, v);
  CALL_SP_vf_vf_vf(hypotf, vv);
#ifndef ENABLE_AVX
// These two functions are not checked in some configurations due to
// the issue in https://github.com/shibatch/sleef/issues/221
  CALL_SP_vi_vf(expfrexpf, v);
  CALL_SP_vi_vf(ilogbf, v);
#endif
  CALL_SP_vf_vf_vi(ldexpf, vv);
  CALL_SP_vf_vf(lgammaf, v);
  CALL_SP_vf_vf(logf, v);
  CALL_SP_vf_vf(log10f, v);
  CALL_SP_vf_vf(log1pf, v);
  CALL_SP_vf_vf(log2f, v);
  CALL_SP_vf_vf_pvf(modff, vl4);
  CALL_SP_vf_vf_vf(nextafterf, vv);
  CALL_SP_vf_vf_vf(powf, vv);
  CALL_SP_vf_vf(rintf, v);
  CALL_SP_vf_vf(roundf, v);
  CALL_SP_vf_vf(sinf, v);
  CALL_SP_v_vf_pvf_pvf(sincosf, vl4l4);
  CALL_SP_v_vf_pvf_pvf(sincospif, vl4l4);
  CALL_SP_vf_vf(sinhf, v);
  CALL_SP_vf_vf(sinpif, v);
  CALL_SP_vf_vf(sqrtf, v);
  CALL_SP_vf_vf(tanf, v);
  CALL_SP_vf_vf(tanhf, v);
  CALL_SP_vf_vf(tgammaf, v);
  CALL_SP_vf_vf(truncf, v);

  return 0;
}
