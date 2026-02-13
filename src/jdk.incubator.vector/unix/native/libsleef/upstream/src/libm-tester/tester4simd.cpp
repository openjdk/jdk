//   Copyright Naoki Shibata and contributors 2010 - 2025.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <iostream>
#include <vector>
#include <bit>
#include <chrono>
#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <cmath>
#include <cfloat>
#include <climits>

#include "testerutil.h"

using namespace std;

//

#if !defined(USE_INLINE_HEADER)
#include "sleef.h"
#else // #if !defined(USE_INLINE_HEADER)
#include <stddef.h>
#include <stdint.h>
#include <float.h>
#include <limits.h>

#if defined(__AVX2__) || defined(__aarch64__) || defined(__powerpc64__)
#ifndef __FMA__
#define __FMA__
#endif
#endif

#if defined(_MSC_VER) && !defined(__STDC__)
#define __STDC__ 1
#endif

#if (defined(__GNUC__) || defined(__CLANG__)) && defined(__x86_64__)
#include <x86intrin.h>
#endif

#if (defined(_MSC_VER))
#include <intrin.h>
#endif

#if defined(__ARM_NEON__) || defined(__ARM_NEON)
#include <arm_neon.h>
#endif

#if defined(__ARM_FEATURE_SVE)
#include <arm_sve.h>
#endif

#if defined(__riscv) && defined(__riscv_v)
#include <riscv_vector.h>
#endif

#if defined(__VSX__)
#include <altivec.h>
#endif

#if defined(__VX__)
#include <vecintrin.h>
#endif

#define SLEEF_ALWAYS_INLINE inline
#define SLEEF_INLINE
#define SLEEF_CONST
#include USE_INLINE_HEADER
#include MACRO_ONLY_HEADER

#ifndef ENABLE_PURECFMA_SCALAR
#include "sleefinline_purecfma_scalar.h"
#endif

#endif // #if !defined(USE_INLINE_HEADER)


#ifdef ENABLE_SSE2
#include "renamesse2.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 2
#include "helpersse2.h"
typedef Sleef___m128d_2 vdouble2;
typedef Sleef___m128_2 vfloat2;
#endif
#endif

#ifdef ENABLE_AVX2
#include "renameavx2.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperavx2.h"
typedef Sleef___m256d_2 vdouble2;
typedef Sleef___m256_2 vfloat2;
#endif
#endif

#ifdef ENABLE_AVX2128
#include "renameavx2128.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperavx2_128.h"
typedef Sleef___m128d_2 vdouble2;
typedef Sleef___m128_2 vfloat2;
#endif
#endif

#ifdef ENABLE_AVX512F
#include "renameavx512f.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperavx512f.h"
typedef Sleef___m512d_2 vdouble2;
typedef Sleef___m512_2 vfloat2;
#endif
#endif

#ifdef ENABLE_PUREC
#define CONFIG 1
#include "helperpurec.h"
#include "norename.h"
#endif

#ifdef ENABLE_ADVSIMD
#include "renameadvsimd.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperadvsimd.h"
typedef Sleef_float64x2_t_2 vdouble2;
typedef Sleef_float32x4_t_2 vfloat2;
#endif
#endif

#ifdef ENABLE_DSP128
#define CONFIG 2
#include "helpersse2.h"
#include "renamedsp128.h"
typedef Sleef___m128d_2 vdouble2;
typedef Sleef___m128_2 vfloat2;
#endif

#ifdef ENABLE_SVE
#include "renamesve.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helpersve.h"
#endif
#endif

#ifdef ENABLE_DSP256
#define CONFIG 1
#include "helperavx.h"
#include "renamedsp256.h"
typedef Sleef___m256d_2 vdouble2;
typedef Sleef___m256_2 vfloat2;
#endif

#ifdef ENABLE_VSX
#include "renamevsx.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperpower_128.h"
#include "renamevsx.h"
typedef Sleef_SLEEF_VECTOR_DOUBLE_2 vdouble2;
typedef Sleef_SLEEF_VECTOR_FLOAT_2 vfloat2;
#endif
#endif

#ifdef ENABLE_VSX3
#include "renamevsx3.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 3
#include "helperpower_128.h"
#include "renamevsx3.h"
typedef Sleef_SLEEF_VECTOR_DOUBLE_2 vdouble2;
typedef Sleef_SLEEF_VECTOR_FLOAT_2 vfloat2;
#endif
#endif

#ifdef ENABLE_VXE
#include "renamevxe.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 140
#include "helpers390x_128.h"
typedef Sleef_SLEEF_VECTOR_DOUBLE_2 vdouble2;
typedef Sleef_SLEEF_VECTOR_FLOAT_2 vfloat2;
#endif
#endif

#ifdef ENABLE_VXE2
#include "renamevxe2.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 150
#include "helpers390x_128.h"
typedef Sleef_SLEEF_VECTOR_DOUBLE_2 vdouble2;
typedef Sleef_SLEEF_VECTOR_FLOAT_2 vfloat2;
#endif
#endif

#ifdef ENABLE_DSPPOWER_128
#define CONFIG 1
#include "helperpower_128.h"
#include "renamedsp128.h"
typedef Sleef_SLEEF_VECTOR_DOUBLE_2 vdouble2;
typedef Sleef_SLEEF_VECTOR_FLOAT_2 vfloat2;
#endif

#ifdef ENABLE_DSPS390X_128
#define CONFIG 140
#include "helpers390x_128.h"
#include "renamedsp128.h"
typedef Sleef_SLEEF_VECTOR_DOUBLE_2 vdouble2;
typedef Sleef_SLEEF_VECTOR_FLOAT_2 vfloat2;
#endif

#ifdef ENABLE_RVVM1
#include "renamervvm1.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperrvv.h"
#endif
#endif

#ifdef ENABLE_RVVM2
#include "renamervvm2.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperrvv.h"
#endif
#endif

#ifdef ENABLE_PUREC_SCALAR
#include "renamepurec_scalar.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperpurec_scalar.h"
typedef Sleef_double_2 vdouble2;
typedef Sleef_float_2 vfloat2;
#endif
#endif

#ifdef ENABLE_PURECFMA_SCALAR
#include "renamepurecfma_scalar.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 2
#include "helperpurec_scalar.h"
typedef Sleef_double_2 vdouble2;
typedef Sleef_float_2 vfloat2;
#endif
#endif

#ifdef ENABLE_DSP_SCALAR
#include "renamedspscalar.h"
#define CONFIG 1
#include "helperpurec_scalar.h"
typedef Sleef_double_2 vdouble2;
typedef Sleef_float_2 vfloat2;
#endif

#ifdef USE_INLINE_HEADER
#ifdef vopmask
#undef vopmask
#endif

#define CONCAT_SIMD_SUFFIX_(keyword, suffix) keyword ## suffix
#define CONCAT_SIMD_SUFFIX(keyword, suffix) CONCAT_SIMD_SUFFIX_(keyword, suffix)
#define vmask CONCAT_SIMD_SUFFIX(vmask, SIMD_SUFFIX)
#define vopmask CONCAT_SIMD_SUFFIX(vopmask, SIMD_SUFFIX)
#define vdouble CONCAT_SIMD_SUFFIX(vdouble, SIMD_SUFFIX)
#define vint CONCAT_SIMD_SUFFIX(vint, SIMD_SUFFIX)
#define vfloat CONCAT_SIMD_SUFFIX(vfloat, SIMD_SUFFIX)
#define vint2 CONCAT_SIMD_SUFFIX(vint2, SIMD_SUFFIX)
#define vdouble2 CONCAT_SIMD_SUFFIX(vdouble2, SIMD_SUFFIX)
#define vfloat2 CONCAT_SIMD_SUFFIX(vfloat2, SIMD_SUFFIX)
#define vd2getx_vd_vd2 CONCAT_SIMD_SUFFIX(vd2getx_vd_vd2, SIMD_SUFFIX)
#define vd2gety_vd_vd2 CONCAT_SIMD_SUFFIX(vd2gety_vd_vd2, SIMD_SUFFIX)
#define vf2getx_vf_vf2 CONCAT_SIMD_SUFFIX(vf2getx_vf_vf2, SIMD_SUFFIX)
#define vf2gety_vf_vf2 CONCAT_SIMD_SUFFIX(vf2gety_vf_vf2, SIMD_SUFFIX)
#define vloadu_vd_p CONCAT_SIMD_SUFFIX(vloadu_vd_p, SIMD_SUFFIX)
#define vstoreu_v_p_vd CONCAT_SIMD_SUFFIX(vstoreu_v_p_vd, SIMD_SUFFIX)
#define vloadu_vf_p CONCAT_SIMD_SUFFIX(vloadu_vf_p, SIMD_SUFFIX)
#define vstoreu_v_p_vf CONCAT_SIMD_SUFFIX(vstoreu_v_p_vf, SIMD_SUFFIX)
#define vloadu_vi_p CONCAT_SIMD_SUFFIX(vloadu_vi_p, SIMD_SUFFIX)
#define vstoreu_v_p_vi CONCAT_SIMD_SUFFIX(vstoreu_v_p_vi, SIMD_SUFFIX)
#endif

//

#if defined(ENABLE_DP) && !(defined(ENABLE_SVE) || defined(ENABLE_RVVM1) || defined(ENABLE_RVVM2) || defined(USE_INLINE_HEADER))
static vdouble vd2getx_vd_vd2(vdouble2 v) { return v.x; }
static vdouble vd2gety_vd_vd2(vdouble2 v) { return v.y; }
#endif

#if defined(ENABLE_SP) && !(defined(ENABLE_SVE) || defined(ENABLE_RVVM1) || defined(ENABLE_RVVM2) || defined(USE_INLINE_HEADER))
static vfloat vf2getx_vf_vf2(vfloat2 v) { return v.x; }
static vfloat vf2gety_vf_vf2(vfloat2 v) { return v.y; }
#endif

#ifndef SLEEF_DBL_DENORM_MIN
#define SLEEF_DBL_DENORM_MIN 4.9406564584124654e-324
#define SLEEF_FLT_DENORM_MIN 1.40129846e-45F
#endif

//

extern "C" {
  int check_feature(double d, float f) {
#ifdef ENABLE_DP
    {
      double s[VECTLENDP];
      int i;
      for(i=0;i<(int)VECTLENDP;i++) {
        s[i] = d;
      }
      vdouble a = vloadu_vd_p(s);
      a = xpow(a, a);
      vstoreu_v_p_vd(s, a);
      if (s[0] == s[0]) return 1;
    }
#endif
#ifdef ENABLE_SP
    {
      float s[VECTLENSP];
      int i;
      for(i=0;i<(int)VECTLENSP;i++) {
        s[i] = d;
      }
      vfloat a = vloadu_vf_p(s);
      a = xpowf(a, a);
      vstoreu_v_p_vf(s, a);
      if (s[0] == s[0]) return 1;
    }
#endif
    return 0;
  }
}

//

#if defined(ENABLE_DP)
template<typename T>
static bool check_d_d(const char *msg, vdouble (*vfunc)(vdouble), T (*tlfunc)(const T),
                      const double *a0, size_t z, double tol, bool checkSignedZero) {
  double s0[VECTLENDP];
  for(size_t i=0;i<z;i++) {
    memrand(s0, sizeof(s0));
    int idx = xrand() & (VECTLENDP-1);
    s0[idx] = a0[i];
    vdouble v0 = vloadu_vd_p(s0);
    v0 = (*vfunc)(v0);
    vstoreu_v_p_vd(s0, v0);
    double u = countULP<T>(s0[idx], (*tlfunc)(a0[i]), DBL_MANT_DIG,
                           SLEEF_DBL_DENORM_MIN, DBL_MAX, checkSignedZero);
    if (u > tol) {
      printf("%s : arg = %a (%g), ulp = %g, t = %.16g, ", msg, a0[i], a0[i], u, s0[idx]);
      cout << "c = " << tlfloat::to_string((*tlfunc)(a0[i])) << endl;
      return false;
    }
  }
  return true;
}

template<typename T>
static bool check_d_d(const char *msg, vdouble (*vfunc)(vdouble), T (*tlfunc)(const T),
                      double start, double end, double step, double tol, bool checkSignedZero) {
  double s0[VECTLENDP];
  for(size_t i=0;start + i * step <= end;i++) {
    double a0 = start + i * step;
    memrand(s0, sizeof(s0));
    int idx = xrand() & (VECTLENDP-1);
    s0[idx] = a0;
    vdouble v0 = vloadu_vd_p(s0);
    v0 = (*vfunc)(v0);
    vstoreu_v_p_vd(s0, v0);
    double u = countULP<T>(s0[idx], (*tlfunc)(a0), DBL_MANT_DIG,
                           SLEEF_DBL_DENORM_MIN, DBL_MAX, checkSignedZero);
    if (u > tol) {
      printf("%s : arg = %a (%g), ulp = %g, t = %.16g, ", msg, a0, a0, u, s0[idx]);
      cout << "c = " << tlfloat::to_string((*tlfunc)(a0)) << endl;
      return false;
    }
  }
  return true;
}

template<typename T>
static bool check_d_d_d(const char *msg, vdouble (*vfunc)(vdouble, vdouble), T (*tlfunc)(const T, const T),
                        const double *a0, size_t z0, const double *a1, size_t z1,
                        double tol, bool checkSignedZero) {
  double s0[VECTLENDP], s1[VECTLENDP];
  for(size_t i=0;i<z0;i++) {
    for(size_t j=0;j<z1;j++) {
      memrand(s0, sizeof(s0));
      memrand(s1, sizeof(s1));
      int idx = xrand() & (VECTLENDP-1);
      s0[idx] = a0[i];
      s1[idx] = a1[j];
      vdouble v0 = vloadu_vd_p(s0);
      vdouble v1 = vloadu_vd_p(s1);
      v0 = (*vfunc)(v0, v1);
      vstoreu_v_p_vd(s0, v0);
      double u = countULP<T>(s0[idx], (*tlfunc)(a0[i], a1[j]), DBL_MANT_DIG,
                             SLEEF_DBL_DENORM_MIN, DBL_MAX, checkSignedZero);
      if (u > tol) {
        printf("%s : arg0 = %a (%g), arg1 = %a (%g), ulp = %g, t = %.16g, ",
               msg, a0[i], a0[i], a1[j], a1[j], u, s0[idx]);
        cout << "c = " << tlfloat::to_string((*tlfunc)(a0[i], a1[j])) << endl;
        return false;
      }
    }
  }
  return true;
}

template<typename T>
static bool check_d_d_d(const char *msg, vdouble (*vfunc)(vdouble, vdouble), T (*tlfunc)(const T, const T),
                        double startx, double endx, double stepx, double starty, double endy, double stepy,
                        double tol, bool checkSignedZero) {
  double s0[VECTLENDP], s1[VECTLENDP];
  bool ret = true;
  for(size_t i=0;startx + i * stepx <= endx;i++) {
    double a0 = startx + i * stepx;
    for(size_t j=0;starty + j * stepy <= endy;j++) {
      double a1 = starty + j * stepy;
      memrand(s0, sizeof(s0));
      memrand(s1, sizeof(s1));
      int idx = xrand() & (VECTLENDP-1);
      s0[idx] = a0;
      s1[idx] = a1;
      vdouble v0 = vloadu_vd_p(s0);
      vdouble v1 = vloadu_vd_p(s1);
      v0 = (*vfunc)(v0, v1);
      vstoreu_v_p_vd(s0, v0);
      double u = countULP<T>(s0[idx], (*tlfunc)(a0, a1), DBL_MANT_DIG,
                             SLEEF_DBL_DENORM_MIN, DBL_MAX, checkSignedZero);
      if (u > tol) {
        printf("%s : arg0 = %a (%g), arg1 = %a (%g), ulp = %g, t = %.16g, ",
               msg, a0, a0, a1, a1, u, s0[idx]);
        cout << "c = " << tlfloat::to_string((*tlfunc)(a0, a1)) << endl;
        ret = false;
      }
    }
  }
  return ret;
}

template<typename T>
static bool checkX_d_d(const char *msg, vdouble2 (*vfunc)(vdouble), T (*tlfunc)(const T),
                      const double *a0, size_t z, double tol, bool checkSignedZero) {
  double s0[VECTLENDP];
  for(size_t i=0;i<z;i++) {
    memrand(s0, sizeof(s0));
    int idx = xrand() & (VECTLENDP-1);
    s0[idx] = a0[i];
    vdouble v0 = vloadu_vd_p(s0);
    v0 = vd2getx_vd_vd2((*vfunc)(v0));
    vstoreu_v_p_vd(s0, v0);
    double u = countULP<T>(s0[idx], (*tlfunc)(a0[i]), DBL_MANT_DIG,
                           SLEEF_DBL_DENORM_MIN, DBL_MAX, checkSignedZero);
    if (u > tol) {
      printf("%s : arg = %a (%g), ulp = %g, t = %.16g, ", msg, a0[i], a0[i], u, s0[idx]);
      cout << "c = " << tlfloat::to_string((*tlfunc)(a0[i])) << endl;
      return false;
    }
  }
  return true;
}

template<typename T>
static bool checkX_d_d(const char *msg, vdouble2 (*vfunc)(vdouble), T (*tlfunc)(const T),
                      double start, double end, double step, double tol, bool checkSignedZero) {
  double s0[VECTLENDP];
  for(size_t i=0;start + i * step <= end;i++) {
    double a0 = start + i * step;
    memrand(s0, sizeof(s0));
    int idx = xrand() & (VECTLENDP-1);
    s0[idx] = a0;
    vdouble v0 = vloadu_vd_p(s0);
    v0 = vd2getx_vd_vd2((*vfunc)(v0));
    vstoreu_v_p_vd(s0, v0);
    double u = countULP<T>(s0[idx], (*tlfunc)(a0), DBL_MANT_DIG,
                           SLEEF_DBL_DENORM_MIN, DBL_MAX, checkSignedZero);
    if (u > tol) {
      printf("%s : arg = %a (%g), ulp = %g, t = %.16g, ", msg, a0, a0, u, s0[idx]);
      cout << "c = " << tlfloat::to_string((*tlfunc)(a0)) << endl;
      return false;
    }
  }
  return true;
}

template<typename T>
static bool checkY_d_d(const char *msg, vdouble2 (*vfunc)(vdouble), T (*tlfunc)(const T),
                      const double *a0, size_t z, double tol, bool checkSignedZero) {
  double s0[VECTLENDP];
  for(size_t i=0;i<z;i++) {
    memrand(s0, sizeof(s0));
    int idx = xrand() & (VECTLENDP-1);
    s0[idx] = a0[i];
    vdouble v0 = vloadu_vd_p(s0);
    v0 = vd2gety_vd_vd2((*vfunc)(v0));
    vstoreu_v_p_vd(s0, v0);
    double u = countULP<T>(s0[idx], (*tlfunc)(a0[i]), DBL_MANT_DIG,
                           SLEEF_DBL_DENORM_MIN, DBL_MAX, checkSignedZero);
    if (u > tol) {
      printf("%s : arg = %a (%g), ulp = %g, t = %.16g, ", msg, a0[i], a0[i], u, s0[idx]);
      cout << "c = " << tlfloat::to_string((*tlfunc)(a0[i])) << endl;
      return false;
    }
  }
  return true;
}

template<typename T>
static bool checkY_d_d(const char *msg, vdouble2 (*vfunc)(vdouble), T (*tlfunc)(const T),
                      double start, double end, double step, double tol, bool checkSignedZero) {
  double s0[VECTLENDP];
  for(size_t i=0;start + i * step <= end;i++) {
    double a0 = start + i * step;
    memrand(s0, sizeof(s0));
    int idx = xrand() & (VECTLENDP-1);
    s0[idx] = a0;
    vdouble v0 = vloadu_vd_p(s0);
    v0 = vd2gety_vd_vd2((*vfunc)(v0));
    vstoreu_v_p_vd(s0, v0);
    double u = countULP<T>(s0[idx], (*tlfunc)(a0), DBL_MANT_DIG,
                           SLEEF_DBL_DENORM_MIN, DBL_MAX, checkSignedZero);
    if (u > tol) {
      printf("%s : arg = %a (%g), ulp = %g, t = %.16g, ", msg, a0, a0, u, s0[idx]);
      cout << "c = " << tlfloat::to_string((*tlfunc)(a0)) << endl;
      return false;
    }
  }
  return true;
}

static int32_t func_i_d(vint (*vfunc)(vdouble), double a) {
  int idx = xrand() & (VECTLENDP-1);
  double s0[VECTLENDP];
  memrand(s0, sizeof(s0));
  s0[idx] = a;
  vdouble v0 = vloadu_vd_p(s0);
  vint vi0 = (*vfunc)(v0);
  int t0[VECTLENDP*2];
  vstoreu_v_p_vi(t0, vi0);
  return t0[idx];
}

static double func_d_d_i(vdouble (*vfunc)(vdouble, vint), double a, int i) {
  int idx = xrand() & (VECTLENDP-1);
  double s0[VECTLENDP];
  memrand(s0, sizeof(s0));
  s0[idx] = a;
  int t0[VECTLENDP*2];
  memrand(t0, sizeof(t0));
  t0[idx] = i;
  vdouble v0 = vloadu_vd_p(s0);
  vint vi0 = vloadu_vi_p(t0);
  v0 = (*vfunc)(v0, vi0);
  vstoreu_v_p_vd(s0, v0);
  return s0[idx];
}
#endif // #if defined(ENABLE_DP)

template<typename T>
static bool check_f_f(const char *msg, vfloat (*vfunc)(vfloat), T (*tlfunc)(const T),
                      const float *a0, size_t z, double tol, bool checkSignedZero) {
  float s0[VECTLENSP];
  for(size_t i=0;i<z;i++) {
    memrand(s0, sizeof(s0));
    int idx = xrand() & (VECTLENSP-1);
    float a = a0[i];
    s0[idx] = a;
    vfloat v0 = vloadu_vf_p(s0);
    v0 = (*vfunc)(v0);
    vstoreu_v_p_vf(s0, v0);
    double u = countULP<T>(s0[idx], (*tlfunc)(a), FLT_MANT_DIG,
                           SLEEF_FLT_DENORM_MIN,
                           FLT_MAX, checkSignedZero);
    if (u > tol) {
      printf("%s : arg = %a (%g), ulp = %g, t = %.16g, ", msg, a, a, u, s0[idx]);
      cout << "c = " << tlfloat::to_string((*tlfunc)(a)) << endl;
      return false;
    }
  }
  return true;
}

template<typename T>
static bool check_f_f(const char *msg, vfloat (*vfunc)(vfloat), T (*tlfunc)(const T),
                      double start, double end, double step, double tol, bool checkSignedZero, double abound = 0.0) {
  float s0[VECTLENSP];
  for(size_t i=0;start + i * step <= end;i++) {
    float a0 = start + i * step;
    memrand(s0, sizeof(s0));
    int idx = xrand() & (VECTLENSP-1);
    s0[idx] = a0;
    vfloat v0 = vloadu_vf_p(s0);
    v0 = (*vfunc)(v0);
    vstoreu_v_p_vf(s0, v0);
    double u = countULP<T>(s0[idx], (*tlfunc)(a0), FLT_MANT_DIG,
                           SLEEF_FLT_DENORM_MIN,
                           FLT_MAX, checkSignedZero, abound);
    if (u > tol) {
      printf("%s : arg = %a (%g), ulp = %g, t = %.16g, ", msg, a0, a0, u, s0[idx]);
      cout << "c = " << tlfloat::to_string((*tlfunc)(a0)) << endl;
      return false;
    }
  }
  return true;
}

template<typename T>
static bool checkX_f_f(const char *msg, vfloat2 (*vfunc)(vfloat), T (*tlfunc)(const T),
                      const float *a0, size_t z, double tol, bool checkSignedZero) {
  float s0[VECTLENSP];
  for(size_t i=0;i<z;i++) {
    memrand(s0, sizeof(s0));
    int idx = xrand() & (VECTLENSP-1);
    float a = a0[i];
    s0[idx] = a;
    vfloat v0 = vloadu_vf_p(s0);
    v0 = vf2getx_vf_vf2((*vfunc)(v0));
    vstoreu_v_p_vf(s0, v0);
    double u = countULP<T>(s0[idx], (*tlfunc)(a), FLT_MANT_DIG,
                           SLEEF_FLT_DENORM_MIN,
                           FLT_MAX, checkSignedZero);
    if (u > tol) {
      printf("%s : arg = %a (%g), ulp = %g, t = %.16g, ", msg, a, a, u, s0[idx]);
      cout << "c = " << tlfloat::to_string((*tlfunc)(a)) << endl;
      return false;
    }
  }
  return true;
}

template<typename T>
static bool checkX_f_f(const char *msg, vfloat2 (*vfunc)(vfloat), T (*tlfunc)(const T),
                      double start, double end, double step, double tol, bool checkSignedZero) {
  float s0[VECTLENSP];
  for(size_t i=0;start + i * step <= end;i++) {
    float a0 = start + i * step;
    memrand(s0, sizeof(s0));
    int idx = xrand() & (VECTLENSP-1);
    s0[idx] = a0;
    vfloat v0 = vloadu_vf_p(s0);
    v0 = vf2getx_vf_vf2((*vfunc)(v0));
    vstoreu_v_p_vf(s0, v0);
    double u = countULP<T>(s0[idx], (*tlfunc)(a0), FLT_MANT_DIG,
                           SLEEF_FLT_DENORM_MIN,
                           FLT_MAX, checkSignedZero);
    if (u > tol) {
      printf("%s : arg = %a (%g), ulp = %g, t = %.16g, ", msg, a0, a0, u, s0[idx]);
      cout << "c = " << tlfloat::to_string((*tlfunc)(a0)) << endl;
      return false;
    }
  }
  return true;
}

template<typename T>
static bool checkY_f_f(const char *msg, vfloat2 (*vfunc)(vfloat), T (*tlfunc)(const T),
                      const float *a0, size_t z, double tol, bool checkSignedZero) {
  float s0[VECTLENSP];
  for(size_t i=0;i<z;i++) {
    memrand(s0, sizeof(s0));
    int idx = xrand() & (VECTLENSP-1);
    float a = a0[i];
    s0[idx] = a;
    vfloat v0 = vloadu_vf_p(s0);
    v0 = vf2gety_vf_vf2((*vfunc)(v0));
    vstoreu_v_p_vf(s0, v0);
    double u = countULP<T>(s0[idx], (*tlfunc)(a), FLT_MANT_DIG,
                           SLEEF_FLT_DENORM_MIN,
                           FLT_MAX, checkSignedZero);
    if (u > tol) {
      printf("%s : arg = %a (%g), ulp = %g, t = %.16g, ", msg, a, a, u, s0[idx]);
      cout << "c = " << tlfloat::to_string((*tlfunc)(a)) << endl;
      return false;
    }
  }
  return true;
}

template<typename T>
static bool checkY_f_f(const char *msg, vfloat2 (*vfunc)(vfloat), T (*tlfunc)(const T),
                      double start, double end, double step, double tol, bool checkSignedZero) {
  float s0[VECTLENSP];
  for(size_t i=0;start + i * step <= end;i++) {
    float a0 = start + i * step;
    memrand(s0, sizeof(s0));
    int idx = xrand() & (VECTLENSP-1);
    s0[idx] = a0;
    vfloat v0 = vloadu_vf_p(s0);
    v0 = vf2gety_vf_vf2((*vfunc)(v0));
    vstoreu_v_p_vf(s0, v0);
    double u = countULP<T>(s0[idx], (*tlfunc)(a0), FLT_MANT_DIG,
                           SLEEF_FLT_DENORM_MIN,
                           FLT_MAX, checkSignedZero);
    if (u > tol) {
      printf("%s : arg = %a (%g), ulp = %g, t = %.16g, ", msg, a0, a0, u, s0[idx]);
      cout << "c = " << tlfloat::to_string((*tlfunc)(a0)) << endl;
      return false;
    }
  }
  return true;
}

template<typename T>
static bool check_f_f_f(const char *msg, vfloat (*vfunc)(vfloat, vfloat), T (*tlfunc)(const T, const T),
                        const float *a0, size_t z0, const float *a1, size_t z1,
                        double tol, bool checkSignedZero) {
  float s0[VECTLENSP], s1[VECTLENSP];
  for(size_t i=0;i<z0;i++) {
    for(size_t j=0;j<z1;j++) {
      memrand(s0, sizeof(s0));
      memrand(s1, sizeof(s1));
      int idx = xrand() & (VECTLENSP-1);
      s0[idx] = a0[i];
      s1[idx] = a1[j];
      vfloat v0 = vloadu_vf_p(s0);
      vfloat v1 = vloadu_vf_p(s1);
      v0 = (*vfunc)(v0, v1);
      vstoreu_v_p_vf(s0, v0);
      double u = countULP<T>(s0[idx], (*tlfunc)(a0[i], a1[j]), FLT_MANT_DIG,
                             SLEEF_FLT_DENORM_MIN,
                             FLT_MAX, checkSignedZero);
      if (u > tol) {
        printf("%s : arg0 = %a (%g), arg1 = %a (%g), ulp = %g, t = %.16g, ",
               msg, a0[i], a0[i], a1[j], a1[j], u, s0[idx]);
        cout << "c = " << tlfloat::to_string((*tlfunc)(a0[i], a1[j])) << endl;
        return false;
      }
    }
  }
  return true;
}

template<typename T>
static bool check_f_f_f(const char *msg, vfloat (*vfunc)(vfloat, vfloat), T (*tlfunc)(const T, const T),
                        double startx, double endx, double stepx, double starty, double endy, double stepy,
                        double tol, bool checkSignedZero) {
  float s0[VECTLENSP], s1[VECTLENSP];
  for(size_t i=0;startx + i * stepx <= endx;i++) {
    float a0 = startx + i * stepx;
    for(size_t j=0;starty + j * stepy <= endy;j++) {
      float a1 = starty + j * stepy;
      memrand(s0, sizeof(s0));
      memrand(s1, sizeof(s1));
      int idx = xrand() & (VECTLENSP-1);
      s0[idx] = a0;
      s1[idx] = a1;
      vfloat v0 = vloadu_vf_p(s0);
      vfloat v1 = vloadu_vf_p(s1);
      v0 = (*vfunc)(v0, v1);
      vstoreu_v_p_vf(s0, v0);
      double u = countULP<T>(s0[idx], (*tlfunc)(a0, a1), FLT_MANT_DIG,
                             SLEEF_FLT_DENORM_MIN, FLT_MAX, checkSignedZero);
      if (u > tol) {
        printf("%s : arg0 = %a (%g), arg1 = %a (%g), ulp = %g, t = %.16g, ",
               msg, a0, a0, a1, a1, u, s0[idx]);
        cout << "c = " << tlfloat::to_string((*tlfunc)(a0, a1)) << endl;
        return false;
      }
    }
  }
  return true;
}

template<typename T>
void check(const double t, const double c, int nbmant, const double flmin, const double flmax, const double culp) {
  double tulp = countULP<T>(t, c, nbmant, flmin, flmax, true);
  if (tulp != culp) {
    cout << "NG" << endl;
    printf("t = %a\n", t);
    printf("c = %a\n", c);
    printf("tulp = %g\n", tulp);
    printf("culp = %g\n", culp);
    exit(-1);
  }
}

//

extern "C" {
  int main2(int argc, char **argv);
}

int main2(int argc, char **argv) {
  bool success = true;

  // Tests if counting ulp numbers is correct

  check<tlfloat_quad>(+0.0, +0.0, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX,     0);
  check<tlfloat_quad>(-0.0, +0.0, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 10002);
  check<tlfloat_quad>(+0.0, -0.0, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 10002);
  check<tlfloat_quad>(-0.0, -0.0, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX,     0);

  check<tlfloat_quad>(+1.0, +1.0, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX,     0);
  check<tlfloat_quad>(nextafter(+1.0, +INFINITY), +1.0, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 1.0);
  check<tlfloat_quad>(nextafter(+1.0, -INFINITY), +1.0, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 0.5);

  check<tlfloat_quad>(-1.0, -1.0, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX,     0);
  check<tlfloat_quad>(nextafter(-1.0, +INFINITY), -1.0, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 0.5);
  check<tlfloat_quad>(nextafter(-1.0, -INFINITY), -1.0, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 1.0);

  check<tlfloat_quad>(INFINITY, INFINITY, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 0);
  check<tlfloat_quad>(nextafter(INFINITY, 0), INFINITY, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, INFINITY);
  check<tlfloat_quad>(INFINITY, nextafter(INFINITY, 0), DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 1.0);

  check<tlfloat_quad>(-INFINITY, -INFINITY, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 0);
  check<tlfloat_quad>(nextafter(-INFINITY, 0), -INFINITY, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, INFINITY);
  check<tlfloat_quad>(-INFINITY, nextafter(-INFINITY, 0), DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 1.0);

  check<tlfloat_quad>(DBL_MIN, DBL_MIN, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 0);
  check<tlfloat_quad>(nextafter(DBL_MIN, 0.0), DBL_MIN, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 1.0);
  check<tlfloat_quad>(nextafter(DBL_MIN, 1.0), DBL_MIN, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 1.0);

  check<tlfloat_quad>(-DBL_MIN, -DBL_MIN, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 0);
  check<tlfloat_quad>(nextafter(-DBL_MIN, 0.0), -DBL_MIN, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 1.0);
  check<tlfloat_quad>(nextafter(-DBL_MIN, 1.0), -DBL_MIN, DBL_MANT_DIG, SLEEF_DBL_DENORM_MIN, DBL_MAX, 1.0);

  check<double>(+0.0, +0.0, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX,     0);
  check<double>(-0.0, +0.0, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 10002);
  check<double>(+0.0, -0.0, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 10002);
  check<double>(-0.0, -0.0, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX,     0);

  check<double>(+1.0, +1.0, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX,     0);
  check<double>(nextafterf(+1.0, +INFINITY), +1.0, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 1.0);
  check<double>(nextafterf(+1.0, -INFINITY), +1.0, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 0.5);

  check<double>(-1.0, -1.0, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX,     0);
  check<double>(nextafterf(-1.0, +INFINITY), -1.0, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 0.5);
  check<double>(nextafterf(-1.0, -INFINITY), -1.0, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 1.0);

  check<double>(INFINITY, INFINITY, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 0);
  check<double>(nextafterf(INFINITY, 0), INFINITY, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, INFINITY);
  check<double>(INFINITY, nextafterf(INFINITY, 0), FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 1.0);

  check<double>(-INFINITY, -INFINITY, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 0);
  check<double>(nextafterf(-INFINITY, 0), -INFINITY, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, INFINITY);
  check<double>(-INFINITY, nextafterf(-INFINITY, 0), FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 1.0);

  check<double>(FLT_MIN, FLT_MIN, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 0);
  check<double>(nextafterf(FLT_MIN, 0.0), FLT_MIN, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 1.0);
  check<double>(nextafterf(FLT_MIN, 1.0), FLT_MIN, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 1.0);

  check<double>(-FLT_MIN, -FLT_MIN, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 0);
  check<double>(nextafterf(-FLT_MIN, 0.0), -FLT_MIN, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 1.0);
  check<double>(nextafterf(-FLT_MIN, 1.0), -FLT_MIN, FLT_MANT_DIG, SLEEF_FLT_DENORM_MIN, FLT_MAX, 1.0);

  //

#if defined(ENABLE_DP)
  static const double ad[] = { NAN,
    -INFINITY, -DBL_MAX, -DBL_MIN, -SLEEF_DBL_DENORM_MIN, -0.0,
    +0.0, SLEEF_DBL_DENORM_MIN, DBL_MIN, DBL_MAX, +INFINITY,
    -M_PI*2, -M_PI, -M_PI/2, -M_PI/4, M_PI/4, M_PI/2, M_PI, M_PI*2,
    -1e+100, -1e+10, -100001, -100000.5, -100000, -7.0, -5.0, -4.0, -3.0, -2.5, -2.0, -1.5, -1.0, -0.999, -0.5,
    +0.5, +0.999, +1.0, +1.5, +2.0, +2.5, +3.0, +4.0, +5.0, +7.0, +100000, +100000.5, +100001, +1e+10, +1e+100,
    nextafter(-1, -2), nextafter(+1, +2)
  };

  //

  {
    vector<double> v;
    for(int i = 0;i < 920;i++) v.push_back(pow(2.16, i));
    for(int64_t i64=(int64_t)-1e+14;i64<(int64_t)1e+14;i64+=(int64_t)1e+12) {
      double start = u2d(d2u(M_PI / 4 * i64)-20), end = u2d(d2u(M_PI / 4 * i64)+20);
      for(double d = start;d <= end;d = u2d(d2u(d)+1)) v.push_back(d);
    }

    cout << "sin" << endl;
    success = check_d_d<tlfloat_quad>("sin", xsin, tlfloat_sinq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
    success = check_d_d<tlfloat_quad>("sin", xsin, tlfloat_sinq,
                                      -10, 10, 0.002, 3.5, false) && success;
    success = check_d_d<tlfloat_quad>("sin", xsin, tlfloat_sinq,
                                      -1e+14, 1e+14, 1e+10 + 0.1, 3.5, false) && success;
    success = check_d_d<tlfloat_quad>("sin", xsin, tlfloat_sinq,
                                      v.data(), v.size(), 3.5, false) && success;

    cout << "sin in sincos" << endl;
    success = checkX_d_d<tlfloat_quad>("sin in sincos", xsincos, tlfloat_sinq,
                                       ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
    success = checkX_d_d<tlfloat_quad>("sin in sincos", xsincos, tlfloat_sinq,
                                       -10, 10, 0.002, 3.5, false) && success;
    success = checkX_d_d<tlfloat_quad>("sin in sincos", xsincos, tlfloat_sinq,
                                       -1e+14, 1e+14, 1e+10 + 0.1, 3.5, false) && success;
    success = checkX_d_d<tlfloat_quad>("sin in sincos", xsincos, tlfloat_sinq,
                                       v.data(), v.size(), 3.5, false) && success;

    cout << "sin_u1" << endl;
    success = check_d_d<tlfloat_quad>("sin_u1", xsin_u1, tlfloat_sinq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
    success = check_d_d<tlfloat_quad>("sin_u1", xsin_u1, tlfloat_sinq,
                                      -10, 10, 0.002, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("sin_u1", xsin_u1, tlfloat_sinq,
                                      -1e+14, 1e+14, 1e+10 + 0.1, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("sin_u1", xsin_u1, tlfloat_sinq,
                                      v.data(), v.size(), 1.0, false) && success;

    cout << "sin in sincos_u1" << endl;
    success = checkX_d_d<tlfloat_quad>("sin in sincos_u1", xsincos_u1, tlfloat_sinq,
                                       ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
    success = checkX_d_d<tlfloat_quad>("sin in sincos_u1", xsincos_u1, tlfloat_sinq,
                                       -10, 10, 0.002, 1.0, false) && success;
    success = checkX_d_d<tlfloat_quad>("sin in sincos_u1", xsincos_u1, tlfloat_sinq,
                                       -1e+14, 1e+14, 1e+10 + 0.1, 1.0, false) && success;
    success = checkX_d_d<tlfloat_quad>("sin in sincos_u1", xsincos_u1, tlfloat_sinq,
                                       v.data(), v.size(), 1.0, false) && success;

    cout << "cos" << endl;
    success = check_d_d<tlfloat_quad>("cos", xcos, tlfloat_cosq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
    success = check_d_d<tlfloat_quad>("cos", xcos, tlfloat_cosq,
                                      -10, 10, 0.002, 3.5, false) && success;
    success = check_d_d<tlfloat_quad>("cos", xcos, tlfloat_cosq,
                                      -1e+14, 1e+14, 1e+10 + 0.1, 3.5, false) && success;
    success = check_d_d<tlfloat_quad>("cos", xcos, tlfloat_cosq,
                                      v.data(), v.size(), 3.5, false) && success;

    cout << "cos in sincos" << endl;
    success = checkY_d_d<tlfloat_quad>("cos in sincos", xsincos, tlfloat_cosq,
                                       ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
    success = checkY_d_d<tlfloat_quad>("cos in sincos", xsincos, tlfloat_cosq,
                                       -10, 10, 0.002, 3.5, false) && success;
    success = checkY_d_d<tlfloat_quad>("cos in sincos", xsincos, tlfloat_cosq,
                                       -1e+14, 1e+14, 1e+10 + 0.1, 3.5, false) && success;
    success = checkY_d_d<tlfloat_quad>("cos in sincos", xsincos, tlfloat_cosq,
                                       v.data(), v.size(), 3.5, false) && success;

    cout << "cos_u1" << endl;
    success = check_d_d<tlfloat_quad>("cos_u1", xcos_u1, tlfloat_cosq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
    success = check_d_d<tlfloat_quad>("cos_u1", xcos_u1, tlfloat_cosq,
                                      -10, 10, 0.002, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("cos_u1", xcos_u1, tlfloat_cosq,
                                      -1e+14, 1e+14, 1e+10 + 0.1, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("cos_u1", xcos_u1, tlfloat_cosq,
                                      v.data(), v.size(), 1.0, false) && success;

    cout << "cos in sincos_u1" << endl;
    success = checkY_d_d<tlfloat_quad>("cos in sincos_u1", xsincos_u1, tlfloat_cosq,
                                       ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
    success = checkY_d_d<tlfloat_quad>("cos in sincos_u1", xsincos_u1, tlfloat_cosq,
                                       -10, 10, 0.002, 1.0, false) && success;
    success = checkY_d_d<tlfloat_quad>("cos in sincos_u1", xsincos_u1, tlfloat_cosq,
                                       -1e+14, 1e+14, 1e+10 + 0.1, 1.0, false) && success;
    success = checkY_d_d<tlfloat_quad>("cos in sincos_u1", xsincos_u1, tlfloat_cosq,
                                       v.data(), v.size(), 1.0, false) && success;
  }

  //

  {
    static const double ad2[] = { +0.0, -0.0, INFINITY, -INFINITY, NAN };

    vector<double> v;
    for(int i=1;i<10000 && success;i+=31) {
      double start = u2d(d2u(i)-20), end = u2d(d2u(i)+20);
      for(double d = start;d <= end;d = u2d(d2u(d)+1)) v.push_back(d);
    }
    for(int i=1;i<=20 && success;i++) {
      double start = u2d(d2u(0.25 * i)-20), end = u2d(d2u(0.25 * i)+20);
      for(double d = start;d <= end;d = u2d(d2u(d)+1)) v.push_back(d);
    }

    cout << "sinpi_u05" << endl;
    success = check_d_d<tlfloat_quad>("sinpi_u05", xsinpi_u05, tlfloat_sinpiq,
                                      ad2, sizeof(ad2)/sizeof(ad2[0]), 0.506, true) && success;
    success = check_d_d<tlfloat_quad>("sinpi_u05", xsinpi_u05, tlfloat_sinpiq,
                                      -10.1, 10, 0.0021, 0.506, false) && success;
    success = check_d_d<tlfloat_quad>("sinpi_u05", xsinpi_u05, tlfloat_sinpiq,
                                      -1e+8-0.1, 1e+8, 1e+10 + 0.1, 0.506, false) && success;
    success = check_d_d<tlfloat_quad>("sinpi_u05", xsinpi_u05, tlfloat_sinpiq,
                                      v.data(), v.size(), 0.506, false) && success;

    cout << "sin in sincospi_u35" << endl;
    success = checkX_d_d<tlfloat_quad>("sin in sincospi_u35", xsincospi_u35, tlfloat_sinpiq,
                                       ad2, sizeof(ad2)/sizeof(ad2[0]), 3.5, true) && success;
    success = checkX_d_d<tlfloat_quad>("sin in sincospi_u35", xsincospi_u35, tlfloat_sinpiq,
                                       -10.1, 10, 0.0021, 3.5, false) && success;
    success = checkX_d_d<tlfloat_quad>("sin in sincospi_u35", xsincospi_u35, tlfloat_sinpiq,
                                       -1e+8-0.1, 1e+8, 1e+10 + 0.1, 3.5, false) && success;
    success = checkX_d_d<tlfloat_quad>("sin in sincospi_u35", xsincospi_u35, tlfloat_sinpiq,
                                       v.data(), v.size(), 3.5, false) && success;

    cout << "sin in sincospi_u05" << endl;
    success = checkX_d_d<tlfloat_quad>("sin in sincospi_u05", xsincospi_u05, tlfloat_sinpiq,
                                       ad2, sizeof(ad2)/sizeof(ad2[0]), 0.506, true) && success;
    success = checkX_d_d<tlfloat_quad>("sin in sincospi_u05", xsincospi_u05, tlfloat_sinpiq,
                                       -10.1, 10, 0.0021, 0.506, false) && success;
    success = checkX_d_d<tlfloat_quad>("sin in sincospi_u05", xsincospi_u05, tlfloat_sinpiq,
                                       -1e+8-0.1, 1e+8, 1e+10 + 0.1, 0.506, false) && success;
    success = checkX_d_d<tlfloat_quad>("sin in sincospi_u05", xsincospi_u05, tlfloat_sinpiq,
                                       v.data(), v.size(), 0.506, false) && success;

    cout << "cospi_u05" << endl;
    success = check_d_d<tlfloat_quad>("cospi_u05", xcospi_u05, tlfloat_cospiq,
                                      ad2, sizeof(ad2)/sizeof(ad2[0]), 0.506, true) && success;
    success = check_d_d<tlfloat_quad>("cospi_u05", xcospi_u05, tlfloat_cospiq,
                                      -10.1, 10, 0.0021, 0.506, false) && success;
    success = check_d_d<tlfloat_quad>("cospi_u05", xcospi_u05, tlfloat_cospiq,
                                      -1e+8-0.1, 1e+8, 1e+10 + 0.1, 0.506, false) && success;
    success = check_d_d<tlfloat_quad>("cospi_u05", xcospi_u05, tlfloat_cospiq,
                                      v.data(), v.size(), 0.506, false) && success;

    cout << "cos in sincospi_u35" << endl;
    success = checkY_d_d<tlfloat_quad>("cos in sincospi_u35", xsincospi_u35, tlfloat_cospiq,
                                       ad2, sizeof(ad2)/sizeof(ad2[0]), 3.5, true) && success;
    success = checkY_d_d<tlfloat_quad>("cos in sincospi_u35", xsincospi_u35, tlfloat_cospiq,
                                       -10.1, 10, 0.0021, 3.5, false) && success;
    success = checkY_d_d<tlfloat_quad>("cos in sincospi_u35", xsincospi_u35, tlfloat_cospiq,
                                       -1e+8-0.1, 1e+8, 1e+10 + 0.1, 3.5, false) && success;
    success = checkY_d_d<tlfloat_quad>("cos in sincospi_u35", xsincospi_u35, tlfloat_cospiq,
                                       v.data(), v.size(), 3.5, false) && success;

    cout << "cos in sincospi_u05" << endl;
    success = checkY_d_d<tlfloat_quad>("cos in sincospi_u05", xsincospi_u05, tlfloat_cospiq,
                                       ad2, sizeof(ad2)/sizeof(ad2[0]), 0.506, true) && success;
    success = checkY_d_d<tlfloat_quad>("cos in sincospi_u05", xsincospi_u05, tlfloat_cospiq,
                                       -10.1, 10, 0.0021, 0.506, false) && success;
    success = checkY_d_d<tlfloat_quad>("cos in sincospi_u05", xsincospi_u05, tlfloat_cospiq,
                                       -1e+8-0.1, 1e+8, 1e+10 + 0.1, 0.506, false) && success;
    success = checkY_d_d<tlfloat_quad>("cos in sincospi_u05", xsincospi_u05, tlfloat_cospiq,
                                       v.data(), v.size(), 0.506, false) && success;
  }

  {
    vector<double> v;
    for(int i = 0;i < 920;i++) v.push_back(pow(2.16, i));
    for(int i=1;i<10000 && success;i+=31) {
      double start = u2d(d2u(M_PI / 4 * i)-20), end = u2d(d2u(M_PI / 4 * i)+20);
      for(double d = start;d <= end;d = u2d(d2u(d)+1)) v.push_back(d);
    }

    cout << "tan" << endl;
    success = check_d_d<tlfloat_quad>("tan", xtan, tlfloat_tanq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
    success = check_d_d<tlfloat_quad>("tan", xtan, tlfloat_tanq,
                                      -10, 10, 0.002, 3.5, false) && success;
    success = check_d_d<tlfloat_quad>("tan", xtan, tlfloat_tanq,
                                      -1e+7, 1e+7, 100.1, 3.5, false) && success;
    success = check_d_d<tlfloat_quad>("tan", xtan, tlfloat_tanq,
                                      -1e+14, 1e+14, 1e+10 + 0.1, 3.5, false) && success;
    success = check_d_d<tlfloat_quad>("tan", xtan, tlfloat_tanq,
                                      v.data(), v.size(), 3.5, false) && success;

    cout << "tan_u1" << endl;
    success = check_d_d<tlfloat_quad>("tan_u1", xtan_u1, tlfloat_tanq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
    success = check_d_d<tlfloat_quad>("tan_u1", xtan_u1, tlfloat_tanq,
                                      -10, 10, 0.002, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("tan_u1", xtan_u1, tlfloat_tanq,
                                      -1e+7, 1e+7, 100.1, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("tan_u1", xtan_u1, tlfloat_tanq,
                                      -1e+14, 1e+14, 1e+10 + 0.1, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("tan_u1", xtan_u1, tlfloat_tanq,
                                      v.data(), v.size(), 1.0, false) && success;
  }

  {
    vector<double> v;
    for(int i = -1000;i <= 1000 && success;i+=10) v.push_back(pow(2.1, i));
    for(int i=0;i<10000 && success;i+=10) v.push_back(DBL_MAX * pow(0.9314821319758632, i));
    for(int i=0;i<10000 && success;i+=10) v.push_back(pow(0.933254300796991, i));
    for(int i=0;i<10000 && success;i+=10) v.push_back(DBL_MIN * pow(0.996323, i));

    cout << "log" << endl;
    success = check_d_d<tlfloat_quad>("log", xlog, tlfloat_logq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
    success = check_d_d<tlfloat_quad>("log", xlog, tlfloat_logq,
                                      0.0001, 10, 0.001, 3.5, false) && success;
    success = check_d_d<tlfloat_quad>("log", xlog, tlfloat_logq,
                                      0.0001, 10000, 1.1, 3.5, false) && success;
    success = check_d_d<tlfloat_quad>("log", xlog, tlfloat_logq,
                                      v.data(), v.size(), 3.5, false) && success;

    cout << "log_u1" << endl;
    success = check_d_d<tlfloat_quad>("log_u1", xlog_u1, tlfloat_logq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
    success = check_d_d<tlfloat_quad>("log_u1", xlog_u1, tlfloat_logq,
                                      0.0001, 10, 0.001, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("log_u1", xlog_u1, tlfloat_logq,
                                      0.0001, 10000, 1.1, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("log_u1", xlog_u1, tlfloat_logq,
                                      v.data(), v.size(), 1.0, false) && success;

    cout << "log10" << endl;
    success = check_d_d<tlfloat_quad>("log10", xlog10, tlfloat_log10q,
                                      ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
    success = check_d_d<tlfloat_quad>("log10", xlog10, tlfloat_log10q,
                                      0.0001, 10, 0.001, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("log10", xlog10, tlfloat_log10q,
                                      0.0001, 10000, 1.1, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("log10", xlog10, tlfloat_log10q,
                                      v.data(), v.size(), 1.0, false) && success;

    cout << "log2" << endl;
    success = check_d_d<tlfloat_quad>("log2", xlog2, tlfloat_log2q,
                                      ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
    success = check_d_d<tlfloat_quad>("log2", xlog2, tlfloat_log2q,
                                      0.0001, 10, 0.001, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("log2", xlog2, tlfloat_log2q,
                                      0.0001, 10000, 1.1, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("log2", xlog2, tlfloat_log2q,
                                      v.data(), v.size(), 1.0, false) && success;

    cout << "log2_u35" << endl;
    success = check_d_d<tlfloat_quad>("log2_u35", xlog2_u35, tlfloat_log2q,
                                      ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
    success = check_d_d<tlfloat_quad>("log2_u35", xlog2_u35, tlfloat_log2q,
                                      0.0001, 10, 0.001, 3.5, false) && success;
    success = check_d_d<tlfloat_quad>("log2_u35", xlog2_u35, tlfloat_log2q,
                                      0.0001, 10000, 1.1, 3.5, false) && success;
    success = check_d_d<tlfloat_quad>("log2_u35", xlog2_u35, tlfloat_log2q,
                                      v.data(), v.size(), 3.5, false) && success;

    static const double ad2[] = {
      +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MIN, -DBL_MIN,
      INFINITY, -INFINITY, NAN, nextafter(-1, -2), -2 };

    cout << "log1p" << endl;
    success = check_d_d<tlfloat_quad>("log1p", xlog1p, tlfloat_log1pq,
                                      ad2, sizeof(ad2)/sizeof(ad2[0]), 1.0, true) && success;
    success = check_d_d<tlfloat_quad>("log1p", xlog1p, tlfloat_log1pq,
                                      0.0001, 10, 0.001, 1.0, false) && success;
  }

  cout << "exp" << endl;
  success = check_d_d<tlfloat_quad>("exp", xexp, tlfloat_expq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
  success = check_d_d<tlfloat_quad>("exp", xexp, tlfloat_expq,
                                    -10, 10, 0.002, 1.0, false) && success;
  success = check_d_d<tlfloat_quad>("exp", xexp, tlfloat_expq,
                                    -1000, 1000, 1.1, 1.0, false) && success;
  cout << "exp2" << endl;
  success = check_d_d<tlfloat_quad>("exp2", xexp2, tlfloat_exp2q,
                                    ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
  success = check_d_d<tlfloat_quad>("exp2", xexp2, tlfloat_exp2q,
                                    -10, 10, 0.002, 1.0, false) && success;
  success = check_d_d<tlfloat_quad>("exp2", xexp2, tlfloat_exp2q,
                                    -1000, 1000, 0.2, 1.0, false) && success;

  cout << "exp10" << endl;
  success = check_d_d<tlfloat_quad>("exp10", xexp10, tlfloat_exp10q,
                                    ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
  success = check_d_d<tlfloat_quad>("exp10", xexp10, tlfloat_exp10q,
                                    -10, 10, 0.002, 1.0, false) && success;
  success = check_d_d<tlfloat_quad>("exp10", xexp10, tlfloat_exp10q,
                                    -300, 300, 0.1, 1.0, false) && success;

  cout << "exp2_u35" << endl;
  success = check_d_d<tlfloat_quad>("exp2_u35", xexp2_u35, tlfloat_exp2q,
                                    ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
  success = check_d_d<tlfloat_quad>("exp2_u35", xexp2_u35, tlfloat_exp2q,
                                    -10, 10, 0.002, 3.5, false) && success;
  success = check_d_d<tlfloat_quad>("exp2_u35", xexp2_u35, tlfloat_exp2q,
                                    -1000, 1000, 0.2, 3.5, false) && success;

  cout << "exp10_u35" << endl;
  success = check_d_d<tlfloat_quad>("exp10_u35", xexp10_u35, tlfloat_exp10q,
                                    ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
  success = check_d_d<tlfloat_quad>("exp10_u35", xexp10_u35, tlfloat_exp10q,
                                    -10, 10, 0.002, 3.5, false) && success;
  success = check_d_d<tlfloat_quad>("exp10_u35", xexp10_u35, tlfloat_exp10q,
                                    -300, 300, 0.1, 3.5, false) && success;

  {
    vector<double> v;
    for(double d = 0;d < 300 && success;d += 0.21) {
      v.push_back(+pow(10, -d));
      v.push_back(-pow(10, -d));
    }

    cout << "expm1" << endl;
    success = check_d_d<tlfloat_quad>("expm1", xexpm1, tlfloat_expm1q,
                                      ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
    success = check_d_d<tlfloat_quad>("expm1", xexpm1, tlfloat_expm1q,
                                      -10, 10, 0.002, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("expm1", xexpm1, tlfloat_expm1q,
                                      -1000, 1000, 0.21, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("expm1", xexpm1, tlfloat_expm1q,
                                      v.data(), v.size(), 1.0, false) && success;
  }

  {
    vector<double> v, w;
    for(double y = -1000;y < 1000;y += 0.1) v.push_back(y);
    w.push_back(2.1);

    cout << "pow" << endl;
    success = check_d_d_d<tlfloat_quad>("pow", xpow, tlfloat_powq,
                                        ad, sizeof(ad)/sizeof(ad[0]), ad, sizeof(ad)/sizeof(ad[0]),
                                        1.0, true) && success;
    success = check_d_d_d<tlfloat_quad>("pow", xpow, tlfloat_powq,
                                        -100, 100, 0.6, 0.1, 100, 0.6, 1.0, false) && success;
    success = check_d_d_d<tlfloat_quad>("pow", xpow, tlfloat_powq,
                                        w.data(), w.size(), v.data(), v.size(), 1.0, false) && success;

    static const double regx[] = {
      0x1.7fed001e5f0edp-1, 0x1.7f136e35a1af6p-1, 0x1.7e7a67798b72dp-1, 0x1.7f5c8e80a3cf7p-1, 0x1.7ff1b57d71188p-1, 0x1.7ff1b57d71188p-1
    };
    static const double regy[] = {
      0x1.1b5ce4d1fb0aep+11, 0x1.2c2f3c91cf6c5p+11, 0x1.e0157ee6672fbp+10, 0x1.235db085e49b7p+11, 0x1.2e8d51b04ab8p+11, 0x1.2e8d51b04ab8p+11
    };
    success = check_d_d_d<tlfloat_quad>("pow", xpow, tlfloat_powq,
                                        regx, sizeof(regx)/sizeof(regx[0]), regy, sizeof(regy)/sizeof(regy[0]),
                                        1.25, true) && success;
  }

  {
    vector<double> v;
    for(int i = -1000;i <= 1000 && success;i+=10) v.push_back(pow(2.1, i));

#ifndef DETERMINISTIC
    cout << "sqrt" << endl;
    success = check_d_d<tlfloat_quad>("sqrt", xsqrt, tlfloat_sqrtq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
    success = check_d_d<tlfloat_quad>("sqrt", xsqrt, tlfloat_sqrtq,
                                      -10000, 10000, 2.1, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("sqrt", xsqrt, tlfloat_sqrtq,
                                      v.data(), v.size(), 1.0, false) && success;

    cout << "sqrt_u05" << endl;
    success = check_d_d<tlfloat_quad>("sqrt", xsqrt_u05, tlfloat_sqrtq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 0.506, true) && success;
    success = check_d_d<tlfloat_quad>("sqrt", xsqrt_u05, tlfloat_sqrtq,
                                      -10000, 10000, 2.1, 0.506, false) && success;
    success = check_d_d<tlfloat_quad>("sqrt", xsqrt_u05, tlfloat_sqrtq,
                                      v.data(), v.size(), 0.506, false) && success;

    cout << "sqrt_u35" << endl;
    success = check_d_d<tlfloat_quad>("sqrt", xsqrt_u35, tlfloat_sqrtq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
    success = check_d_d<tlfloat_quad>("sqrt", xsqrt_u35, tlfloat_sqrtq,
                                      -10000, 10000, 2.1, 3.5, false) && success;
    success = check_d_d<tlfloat_quad>("sqrt", xsqrt_u35, tlfloat_sqrtq,
                                      v.data(), v.size(), 3.5, false) && success;
#endif

    cout << "cbrt" << endl;
    success = check_d_d<tlfloat_quad>("cbrt", xcbrt, tlfloat_cbrtq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
    success = check_d_d<tlfloat_quad>("cbrt", xcbrt, tlfloat_cbrtq,
                                      -10000, 10000, 2.1, 3.5, false) && success;
    success = check_d_d<tlfloat_quad>("cbrt", xcbrt, tlfloat_cbrtq,
                                      v.data(), v.size(), 3.5, false) && success;

    cout << "cbrt_u1" << endl;
    success = check_d_d<tlfloat_quad>("cbrt_u1", xcbrt_u1, tlfloat_cbrtq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
    success = check_d_d<tlfloat_quad>("cbrt_u1", xcbrt_u1, tlfloat_cbrtq,
                                      -10000, 10000, 2.1, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("cbrt_u1", xcbrt_u1, tlfloat_cbrtq,
                                      v.data(), v.size(), 1.0, false) && success;
  }

  cout << "hypot_u35" << endl;
  success = check_d_d_d<tlfloat_quad>("hypot_u35", xhypot_u35, tlfloat_hypotq,
                                      ad, sizeof(ad)/sizeof(ad[0]), ad, sizeof(ad)/sizeof(ad[0]),
                                      3.5, true) && success;
  success = check_d_d_d<tlfloat_quad>("hypot_u35", xhypot_u35, tlfloat_hypotq,
                                      -10, 10, 0.15, -10, 10, 0.15, 3.5, false) && success;
  success = check_d_d_d<tlfloat_quad>("hypot_u35", xhypot_u35, tlfloat_hypotq,
                                      -1e+10, 1e+10, 1.51e+8, -1e+10, 1e+10, 1.51e+8, 3.5, false) && success;

  cout << "hypot_u05" << endl;
  success = check_d_d_d<tlfloat_quad>("hypot_u05", xhypot_u05, tlfloat_hypotq,
                                      ad, sizeof(ad)/sizeof(ad[0]), ad, sizeof(ad)/sizeof(ad[0]),
                                      0.5, true) && success;
  success = check_d_d_d<tlfloat_quad>("hypot_u05", xhypot_u05, tlfloat_hypotq,
                                      -10, 10, 0.15, -10, 10, 0.15, 0.5, false) && success;
  success = check_d_d_d<tlfloat_quad>("hypot_u05", xhypot_u05, tlfloat_hypotq,
                                      -1e+10, 1e+10, 1.51e+8, -1e+10, 1e+10, 1.51e+8, 0.5, false) && success;

  cout << "asin" << endl;
  success = check_d_d<tlfloat_quad>("asin", xasin, tlfloat_asinq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
  success = check_d_d<tlfloat_quad>("asin", xasin, tlfloat_asinq,
                                    -1, 1, 0.0002, 3.5, false) && success;

  cout << "asin_u1" << endl;
  success = check_d_d<tlfloat_quad>("asin_u1", xasin_u1, tlfloat_asinq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
  success = check_d_d<tlfloat_quad>("asin_u1", xasin_u1, tlfloat_asinq,
                                    -1, 1, 0.0002, 1.0, false) && success;

  cout << "acos" << endl;
  success = check_d_d<tlfloat_quad>("acos", xacos, tlfloat_acosq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
  success = check_d_d<tlfloat_quad>("acos", xacos, tlfloat_acosq,
                                    -1, 1, 0.0002, 3.5, false) && success;

  cout << "acos_u1" << endl;
  success = check_d_d<tlfloat_quad>("acos_u1", xacos_u1, tlfloat_acosq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
  success = check_d_d<tlfloat_quad>("acos_u1", xacos_u1, tlfloat_acosq,
                                    -1, 1, 0.0002, 1.0, false) && success;

  cout << "atan" << endl;
  success = check_d_d<tlfloat_quad>("atan", xatan, tlfloat_atanq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
  success = check_d_d<tlfloat_quad>("atan", xatan, tlfloat_atanq,
                                    -10, 10, 0.002, 3.5, false) && success;
  success = check_d_d<tlfloat_quad>("atan", xatan, tlfloat_atanq,
                                    -10000, 10000, 2.1, 3.5, false) && success;

  cout << "atan_u1" << endl;
  success = check_d_d<tlfloat_quad>("atan_u1", xatan_u1, tlfloat_atanq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
  success = check_d_d<tlfloat_quad>("atan_u1", xatan_u1, tlfloat_atanq,
                                    -10, 10, 0.002, 1.0, false) && success;
  success = check_d_d<tlfloat_quad>("atan_u1", xatan_u1, tlfloat_atanq,
                                    -10000, 10000, 2.1, 1.0, false) && success;

  cout << "atan2" << endl;
  success = check_d_d_d<tlfloat_quad>("atan2", xatan2, tlfloat_atan2q,
                                      ad, sizeof(ad)/sizeof(ad[0]), ad, sizeof(ad)/sizeof(ad[0]),
                                      3.5, true) && success;
  success = check_d_d_d<tlfloat_quad>("atan2", xatan2, tlfloat_atan2q,
                                      -10, 10, 0.15, -10, 10, 0.15, 3.5, false) && success;
  success = check_d_d_d<tlfloat_quad>("atan2", xatan2, tlfloat_atan2q,
                                      -100, 100, 1.51, -100, 100, 1.51, 3.5, false) && success;

  cout << "atan2_u1" << endl;
  success = check_d_d_d<tlfloat_quad>("atan2_u1", xatan2_u1, tlfloat_atan2q,
                                      ad, sizeof(ad)/sizeof(ad[0]), ad, sizeof(ad)/sizeof(ad[0]),
                                      1.0, true) && success;
  success = check_d_d_d<tlfloat_quad>("atan2_u1", xatan2_u1, tlfloat_atan2q,
                                      -10, 10, 0.15, -10, 10, 0.15, 1.0, false) && success;
  success = check_d_d_d<tlfloat_quad>("atan2_u1", xatan2_u1, tlfloat_atan2q,
                                      -100, 100, 1.51, -100, 100, 1.51, 1.0, false) && success;

  cout << "sinh" << endl;
  success = check_d_d<tlfloat_quad>("sinh", xsinh, tlfloat_sinhq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
  success = check_d_d<tlfloat_quad>("sinh", xsinh, tlfloat_sinhq,
                                    -10, 10, 0.002, 1.0, false) && success;
  success = check_d_d<tlfloat_quad>("sinh", xsinh, tlfloat_sinhq,
                                    -709, 709, 0.2, 1.0, false) && success;

  cout << "cosh" << endl;
  success = check_d_d<tlfloat_quad>("cosh", xcosh, tlfloat_coshq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
  success = check_d_d<tlfloat_quad>("cosh", xcosh, tlfloat_coshq,
                                    -10, 10, 0.002, 1.0, false) && success;
  success = check_d_d<tlfloat_quad>("cosh", xcosh, tlfloat_coshq,
                                    -709, 709, 0.2, 1.0, false) && success;

  cout << "tanh" << endl;
  success = check_d_d<tlfloat_quad>("tanh", xtanh, tlfloat_tanhq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
  success = check_d_d<tlfloat_quad>("tanh", xtanh, tlfloat_tanhq,
                                    -10, 10, 0.002, 1.0, false) && success;
  success = check_d_d<tlfloat_quad>("tanh", xtanh, tlfloat_tanhq,
                                    -1000, 1000, 0.2, 1.0, false) && success;

  cout << "sinh_u35" << endl;
  success = check_d_d<tlfloat_quad>("sinh_u35", xsinh_u35, tlfloat_sinhq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
  success = check_d_d<tlfloat_quad>("sinh_u35", xsinh_u35, tlfloat_sinhq,
                                    -10, 10, 0.002, 3.5, false) && success;
  success = check_d_d<tlfloat_quad>("sinh_u35", xsinh_u35, tlfloat_sinhq,
                                    -709, 709, 0.2, 3.5, false) && success;

  cout << "cosh_u35" << endl;
  success = check_d_d<tlfloat_quad>("cosh_u35", xcosh_u35, tlfloat_coshq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
  success = check_d_d<tlfloat_quad>("cosh_u35", xcosh_u35, tlfloat_coshq,
                                    -10, 10, 0.002, 3.5, false) && success;
  success = check_d_d<tlfloat_quad>("cosh_u35", xcosh_u35, tlfloat_coshq,
                                    -709, 709, 0.2, 3.5, false) && success;

  cout << "tanh_u35" << endl;
  success = check_d_d<tlfloat_quad>("tanh_u35", xtanh_u35, tlfloat_tanhq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 3.5, true) && success;
  success = check_d_d<tlfloat_quad>("tanh_u35", xtanh_u35, tlfloat_tanhq,
                                    -10, 10, 0.002, 3.5, false) && success;
  success = check_d_d<tlfloat_quad>("tanh_u35", xtanh_u35, tlfloat_tanhq,
                                    -1000, 1000, 0.2, 3.5, false) && success;

  {
    static const double ad2[] = {
      +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MIN, -DBL_MIN, INFINITY, -INFINITY, NAN
    };

    cout << "asinh" << endl;
    success = check_d_d<tlfloat_quad>("asinh", xasinh, tlfloat_asinhq,
                                      ad2, sizeof(ad2)/sizeof(ad2[0]), 1.0, true) && success;
    success = check_d_d<tlfloat_quad>("asinh", xasinh, tlfloat_asinhq,
                                      -10, 10, 0.002, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("asinh", xasinh, tlfloat_asinhq,
                                      -1000, 1000, 0.2, 1.0, false) && success;

    cout << "acosh" << endl;
    success = check_d_d<tlfloat_quad>("acosh", xacosh, tlfloat_acoshq,
                                      ad2, sizeof(ad2)/sizeof(ad2[0]), 1.0, true) && success;
    success = check_d_d<tlfloat_quad>("acosh", xacosh, tlfloat_acoshq,
                                      1, 10, 0.002, 1.0, false) && success;
    success = check_d_d<tlfloat_quad>("acosh", xacosh, tlfloat_acoshq,
                                      1, 1000, 0.2, 1.0, false) && success;
  }

  cout << "atanh" << endl;
  success = check_d_d<tlfloat_quad>("atanh", xatanh, tlfloat_atanhq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
  success = check_d_d<tlfloat_quad>("atanh", xatanh, tlfloat_atanhq,
                                    -10, 10, 0.002, 1.0, false) && success;
  success = check_d_d<tlfloat_quad>("atanh", xatanh, tlfloat_atanhq,
                                    -1000, 1000, 0.2, 1.0, false) && success;

  cout << "copysign" << endl;
  success = check_d_d_d<double>("copysign", xcopysign, tlfloat_copysign,
                                ad, sizeof(ad)/sizeof(ad[0]), ad, sizeof(ad)/sizeof(ad[0]),
                                0.0, true) && success;
  success = check_d_d_d<double>("copysign", xcopysign, tlfloat_copysign,
                                -10, 10, 0.15, -10, 10, 0.15, 0.0, false) && success;
  success = check_d_d_d<double>("copysign", xcopysign, tlfloat_copysign,
                                -1e+10, 1e+10, 1.51e+8, -1e+10, 1e+10, 1.51e+8, 0.0, false) && success;

  cout << "fmax" << endl;
  success = check_d_d_d<tlfloat_quad>("fmax", xfmax, tlfloat_fmaxq,
                                      ad, sizeof(ad)/sizeof(ad[0]), ad, sizeof(ad)/sizeof(ad[0]),
                                      0.0, false) && success;
  success = check_d_d_d<tlfloat_quad>("fmax", xfmax, tlfloat_fmaxq,
                                      -10, 10, 0.15, -10, 10, 0.15, 0.0, false) && success;
  success = check_d_d_d<tlfloat_quad>("fmax", xfmax, tlfloat_fmaxq,
                                      -1e+10, 1e+10, 1.51e+8, -1e+10, 1e+10, 1.51e+8, 0.0, false) && success;

  cout << "fmin" << endl;
  success = check_d_d_d<tlfloat_quad>("fmin", xfmin, tlfloat_fminq,
                                      ad, sizeof(ad)/sizeof(ad[0]), ad, sizeof(ad)/sizeof(ad[0]),
                                      0.0, false) && success;
  success = check_d_d_d<tlfloat_quad>("fmin", xfmin, tlfloat_fminq,
                                      -10, 10, 0.15, -10, 10, 0.15, 0.0, false) && success;
  success = check_d_d_d<tlfloat_quad>("fmin", xfmin, tlfloat_fminq,
                                      -1e+10, 1e+10, 1.51e+8, -1e+10, 1e+10, 1.51e+8, 0.0, false) && success;

  cout << "fdim" << endl;
  success = check_d_d_d<tlfloat_quad>("fdim", xfdim, tlfloat_fdimq,
                                      ad, sizeof(ad)/sizeof(ad[0]), ad, sizeof(ad)/sizeof(ad[0]),
                                      0.5, true) && success;
  success = check_d_d_d<tlfloat_quad>("fdim", xfdim, tlfloat_fdimq,
                                      -10, 10, 0.15, -10, 10, 0.15, 0.5, false) && success;
  success = check_d_d_d<tlfloat_quad>("fdim", xfdim, tlfloat_fdimq,
                                      -1e+10, 1e+10, 1.51e+8, -1e+10, 1e+10, 1.51e+8, 0.5, false) && success;

  cout << "fmod" << endl;
  for(int i=0;i<int(sizeof(ad)/sizeof(ad[0]));i++) {
    for(int j=0;j<int(sizeof(ad)/sizeof(ad[0]));j++) {
      if (fabs_(ad[i] / ad[j]) > 1e+300) continue;
      success = check_d_d_d<tlfloat_quad>("fmod", xfmod, tlfloat_fmodq,
                                          &ad[i], 1, &ad[j], 1, 0.5, true) && success;
    }
  }
  success = check_d_d_d<tlfloat_quad>("fmod", xfmod, tlfloat_fmodq,
                                      -10, 10, 0.15, -10, 10, 0.15, 0.5, false) && success;
  success = check_d_d_d<tlfloat_quad>("fmod", xfmod, tlfloat_fmodq,
                                      -1e+10, 1e+10, 1.51e+8, -1e+10, 1e+10, 1.51e+8, 0.5, false) && success;

  cout << "remainder" << endl;
  for(int i=0;i<int(sizeof(ad)/sizeof(ad[0]));i++) {
    for(int j=0;j<int(sizeof(ad)/sizeof(ad[0]));j++) {
      if (fabs_(ad[i] / ad[j]) > 1e+300) continue;
      success = check_d_d_d<tlfloat_quad>("remainder", xremainder, tlfloat_remainderq,
                                          &ad[i], 1, &ad[j], 1, 0.5, true) && success;
    }
  }
  success = check_d_d_d<tlfloat_quad>("remainder", xremainder, tlfloat_remainderq,
                                      -10, 10, 0.15, -10, 10, 0.15, 0.5, false) && success;
  success = check_d_d_d<tlfloat_quad>("remainder", xremainder, tlfloat_remainderq,
                                      -1e+10, 1e+10, 1.51e+8, -1e+10, 1e+10, 1.51e+8, 0.5, false) && success;

  {
    vector<double> v;
    for(double x = -100.5;x <= 100.5;x+=0.5) {
      for(double d = u2d(d2u(x)-3);d <= u2d(d2u(x)+3) && success;d = u2d(d2u(d)+1)) v.push_back(d);
      double start = u2d(d2u((double)(INT64_C(1) << 52))-20), end = u2d(d2u((double)(INT64_C(1) << 52))+20);
      for(double d = start;d <= end;d = u2d(d2u(d)+1)) { v.push_back(d); v.push_back(-d); }
    }

    cout << "trunc" << endl;
    success = check_d_d<tlfloat_quad>("trunc", xtrunc, tlfloat_truncq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 0.0, true) && success;
    success = check_d_d<tlfloat_quad>("trunc", xtrunc, tlfloat_truncq,
                                      v.data(), v.size(), 0.0, false) && success;
    success = check_d_d<tlfloat_quad>("trunc", xtrunc, tlfloat_truncq,
                                      -10000, 10000, 2.5, 0.0, false) && success;

    cout << "floor" << endl;
    success = check_d_d<tlfloat_quad>("floor", xfloor, tlfloat_floorq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 0.0, true) && success;
    success = check_d_d<tlfloat_quad>("floor", xfloor, tlfloat_floorq,
                                      v.data(), v.size(), 0.0, false) && success;
    success = check_d_d<tlfloat_quad>("floor", xfloor, tlfloat_floorq,
                                      -10000, 10000, 2.5, 0.0, false) && success;

    cout << "ceil" << endl;
    success = check_d_d<tlfloat_quad>("ceil", xceil, tlfloat_ceilq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 0.0, true) && success;
    success = check_d_d<tlfloat_quad>("ceil", xceil, tlfloat_ceilq,
                                      v.data(), v.size(), 0.0, false) && success;
    success = check_d_d<tlfloat_quad>("ceil", xceil, tlfloat_ceilq,
                                      -10000, 10000, 2.5, 0.0, false) && success;

    cout << "round" << endl;
    success = check_d_d<tlfloat_quad>("round", xround, tlfloat_roundq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 0.0, true) && success;
    success = check_d_d<tlfloat_quad>("round", xround, tlfloat_roundq,
                                      v.data(), v.size(), 0.0, false) && success;
    success = check_d_d<tlfloat_quad>("round", xround, tlfloat_roundq,
                                      -10000, 10000, 2.5, 0.0, false) && success;

    cout << "rint" << endl;
    success = check_d_d<tlfloat_quad>("rint", xrint, tlfloat_rintq,
                                      ad, sizeof(ad)/sizeof(ad[0]), 0.0, true) && success;
    success = check_d_d<tlfloat_quad>("rint", xrint, tlfloat_rintq,
                                      v.data(), v.size(), 0.0, false) && success;
    success = check_d_d<tlfloat_quad>("rint", xrint, tlfloat_rintq,
                                      -10000, 10000, 2.5, 0.0, false) && success;
  }

  {
    static const double ad2[] = {
      -4, -3, -2, -1, +0.0, -0.0, +1e+10, -1e+10, INFINITY, -INFINITY, NAN
    };

    cout << "lgamma_u1" << endl;
    success = check_d_d<tlfloat_quad>("lgamma_u1", xlgamma_u1, tlfloat_lgammaq,
                                      ad2, sizeof(ad2)/sizeof(ad2[0]), 1.0, true) && success;
    success = check_d_d<tlfloat_quad>("lgamma_u1", xlgamma_u1, tlfloat_lgammaq,
                                      -5000, 5000, 1.1, 1.0, false) && success;

    cout << "tgamma_u1" << endl;
    success = check_d_d<tlfloat_quad>("tgamma_u1", xtgamma_u1, tlfloat_tgammaq,
                                      ad2, sizeof(ad2)/sizeof(ad2[0]), 1.0, true) && success;
    success = check_d_d<tlfloat_quad>("tgamma_u1", xtgamma_u1, tlfloat_tgammaq,
                                      -10, 10, 0.002, 1.0, false) && success;
  }

  cout << "erf_u1" << endl;
  success = check_d_d<tlfloat_quad>("erf_u1", xerf_u1, tlfloat_erfq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 1.0, true) && success;
  success = check_d_d<tlfloat_quad>("erf_u1", xerf_u1, tlfloat_erfq,
                                    -100, 100, 0.02, 1.0, false) && success;

  cout << "erfc_u15" << endl;
  success = check_d_d<tlfloat_quad>("erfc_u15", xerfc_u15, tlfloat_erfcq,
                                    ad, sizeof(ad)/sizeof(ad[0]), 1.5, true) && success;
  success = check_d_d<tlfloat_quad>("erfc_u15", xerfc_u15, tlfloat_erfcq,
                                    -1, 100, 0.01, 1.5, false) && success;

  {
    cout << "ilogb" << endl;

    static const double ad2[] = { INFINITY, -INFINITY, -1 };

    for(int i=0;i<3;i++) {
      if (func_i_d(xilogb, ad2[i]) != tlfloat_ilogb(double(ad2[i]))) {
        printf("ilogb a = %g, t = %d, c = %d\n", ad2[i], func_i_d(xilogb, ad2[i]), tlfloat_ilogb(ad2[i]));
        success = false;
      }
    }

    if (func_i_d(xilogb, NAN) != INT_MAX && func_i_d(xilogb, NAN) != INT_MIN) {
      printf("ilogb a = %g, t = %d\n", NAN, func_i_d(xilogb, NAN));
      success = false;
    }

    if (func_i_d(xilogb, 0) != INT_MIN && func_i_d(xilogb, 0) != -INT_MAX) {
      printf("ilogb a = %g, t = %d\n", 0.0, func_i_d(xilogb, 0));
      success = false;
    }

    for(double d = 0.0001;d < 10;d += 0.001) {
      if (func_i_d(xilogb, d) != tlfloat_ilogb(double(d))) {
        printf("ilogb a = %a (%g), t = %d, c = %d\n", d, d, func_i_d(xilogb, d), tlfloat_ilogb(d));
        success = false;
      }
    }

    for(double d = 0.0001;d < 10000;d += 1.1) {
      if (func_i_d(xilogb, d) != tlfloat_ilogb(double(d))) {
        printf("ilogb a = %a (%g), t = %d, c = %d\n", d, d, func_i_d(xilogb, d), tlfloat_ilogb(d));
        success = false;
      }
    }

    for(int i=0;i<10000;i+=10) {
      double d = DBL_MIN * pow(0.996323, i);
      if (func_i_d(xilogb, d) != tlfloat_ilogb(double(d))) {
        printf("ilogb a = %a (%g), t = %d, c = %d\n", d, d, func_i_d(xilogb, d), tlfloat_ilogb(d));
        success = false;
      }
    }

    for(int i=0;i<10000;i+=10) {
      double d = pow(0.933254300796991, i);
      if (func_i_d(xilogb, d) != tlfloat_ilogb(double(d))) {
        printf("ilogb a = %a (%g), t = %d, c = %d\n", d, d, func_i_d(xilogb, d), tlfloat_ilogb(d));
        success = false;
      }
    }
  }

  {
    cout << "ldexp" << endl;

    for(int i=-10000;i<=10000 && success;i++) {
      double t = func_d_d_i(xldexp, 1.0, i);
      double c = (double)ldexp_(1.0, i);

      if (c != t) {
        fprintf(stderr, "ldexp args = (1.0, %d), t = %g, c = %g\n", i, t, c);
        success = false;
      }
    }
  }
#endif // #if defined(ENABLE_DP)

  //

  static const float af[] = { NAN,
    -INFINITY, -FLT_MAX, -FLT_MIN, -SLEEF_FLT_DENORM_MIN, -0.0,
    +0.0, SLEEF_FLT_DENORM_MIN, FLT_MIN, FLT_MAX, +INFINITY,
    -M_PI*2, -M_PI, -M_PI/2, -M_PI/4, M_PI/4, M_PI/2, M_PI, M_PI*2,
    -1e+30, -1e+10, -100001, -100000.5, -100000, -7.0, -5.0, -4.0, -3.0, -2.5, -2.0, -1.5, -1.0, -0.999, -0.5,
    +0.5, +0.999, +1.0, +1.5, +2.0, +2.5, +3.0, +4.0, +5.0, +7.0, +100000, +100000.5, +100001, +1e+10, +1e+30,
    nextafterf(-1, -2), nextafterf(+1, +2)
  };

  //

  {
    vector<float> v;
    for(int i = 0;i < 1000;i++) v.push_back(pow(1.092, i));
    for(int64_t i64=(int64_t)-1000;i64<(int64_t)1000 && success;i64+=(int64_t)1) {
      double start = u2f(f2u(M_PI / 4 * i64)-20), end = u2f(f2u(M_PI / 4 * i64)+20);
      for(double d = start;d <= end;d = u2f(f2u(d)+1)) v.push_back(d);
    }

    cout << "sinf" << endl;
    success = check_f_f<double>("sinf", xsinf, tlfloat_sin,
                                af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
    success = check_f_f<double>("sinf", xsinf, tlfloat_sin,
                                -10, 10, 0.002, 3.5, false) && success;
    success = check_f_f<double>("sinf", xsinf, tlfloat_sin,
                                -10000, 10000, 1.1, 3.5, false) && success;
    success = check_f_f<double>("sinf", xsinf, tlfloat_sin,
                                v.data(), v.size(), 3.5, false) && success;

    cout << "sin in sincosf" << endl;
    success = checkX_f_f<double>("sin in sincosf", xsincosf, tlfloat_sin,
                                 af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
    success = checkX_f_f<double>("sin in sincosf", xsincosf, tlfloat_sin,
                                 -10, 10, 0.002, 3.5, false) && success;
    success = checkX_f_f<double>("sin in sincosf", xsincosf, tlfloat_sin,
                                 -10000, 10000, 1.1, 3.5, false) && success;
    success = checkX_f_f<double>("sin in sincosf", xsincosf, tlfloat_sin,
                                 v.data(), v.size(), 3.5, false) && success;

    cout << "sinf_u1" << endl;
    success = check_f_f<double>("sinf_u1", xsinf_u1, tlfloat_sin,
                                af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
    success = check_f_f<double>("sinf_u1", xsinf_u1, tlfloat_sin,
                                -10, 10, 0.002, 1.0, false) && success;
    success = check_f_f<double>("sinf_u1", xsinf_u1, tlfloat_sin,
                                -10000, 10000, 1.1, 1.0, false) && success;
    success = check_f_f<double>("sinf_u1", xsinf_u1, tlfloat_sin,
                                v.data(), v.size(), 1.0, false) && success;

    cout << "sin in sincosf_u1" << endl;
    success = checkX_f_f<double>("sin in sincosf_u1", xsincosf_u1, tlfloat_sin,
                                 af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
    success = checkX_f_f<double>("sin in sincosf_u1", xsincosf_u1, tlfloat_sin,
                                 -10, 10, 0.002, 1.0, false) && success;
    success = checkX_f_f<double>("sin in sincosf_u1", xsincosf_u1, tlfloat_sin,
                                 -10000, 10000, 1.1, 1.0, false) && success;
    success = checkX_f_f<double>("sin in sincosf_u1", xsincosf_u1, tlfloat_sin,
                                 v.data(), v.size(), 1.0, false) && success;

    cout << "fastsinf_u3500" << endl;
    success = check_f_f<double>("fastsinf_u3500", xfastsinf_u3500, tlfloat_sin,
                                -32, 32, 0.001, 350.0, false, 2e-6) && success;

    cout << "cosf" << endl;
    success = check_f_f<double>("cosf", xcosf, tlfloat_cos,
                                af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
    success = check_f_f<double>("cosf", xcosf, tlfloat_cos,
                                -10, 10, 0.002, 3.5, false) && success;
    success = check_f_f<double>("cosf", xcosf, tlfloat_cos,
                                -10000, 10000, 1.1, 3.5, false) && success;
    success = check_f_f<double>("cosf", xcosf, tlfloat_cos,
                                v.data(), v.size(), 3.5, false) && success;

    cout << "cos in sincosf" << endl;
    success = checkY_f_f<double>("cos in sincosf", xsincosf, tlfloat_cos,
                                 af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
    success = checkY_f_f<double>("cos in sincosf", xsincosf, tlfloat_cos,
                                 -10, 10, 0.002, 3.5, false) && success;
    success = checkY_f_f<double>("cos in sincosf", xsincosf, tlfloat_cos,
                                 -10000, 10000, 1.1, 3.5, false) && success;
    success = checkY_f_f<double>("cos in sincosf", xsincosf, tlfloat_cos,
                                 v.data(), v.size(), 3.5, false) && success;

    cout << "cosf_u1" << endl;
    success = check_f_f<double>("cosf_u1", xcosf_u1, tlfloat_cos,
                                af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
    success = check_f_f<double>("cosf_u1", xcosf_u1, tlfloat_cos,
                                -10, 10, 0.002, 1.0, false) && success;
    success = check_f_f<double>("cosf_u1", xcosf_u1, tlfloat_cos,
                                -10000, 10000, 1.1, 1.0, false) && success;
    success = check_f_f<double>("cosf_u1", xcosf_u1, tlfloat_cos,
                                v.data(), v.size(), 1.0, false) && success;

    cout << "cos in sincosf_u1" << endl;
    success = checkY_f_f<double>("cos in sincosf_u1", xsincosf_u1, tlfloat_cos,
                                 af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
    success = checkY_f_f<double>("cos in sincosf_u1", xsincosf_u1, tlfloat_cos,
                                 -10, 10, 0.002, 1.0, false) && success;
    success = checkY_f_f<double>("cos in sincosf_u1", xsincosf_u1, tlfloat_cos,
                                 -10000, 10000, 1.1, 1.0, false) && success;
    success = checkY_f_f<double>("cos in sincosf_u1", xsincosf_u1, tlfloat_cos,
                                 v.data(), v.size(), 1.0, false) && success;

    cout << "fastcosf_u3500" << endl;
    success = check_f_f<double>("fastcosf_u3500", xfastcosf_u3500, tlfloat_cos,
                                -32, 32, 0.001, 350.0, false, 2e-6) && success;
  }

  {
    static const float af2[] = { +0.0, -0.0, INFINITY, -INFINITY, NAN };

    vector<float> v;
    for(int i=1;i<10000 && success;i+=31) {
      double start = u2f(f2u(i)-20), end = u2f(f2u(i)+20);
      for(double d = start;d <= end;d = u2f(f2u(d)+1)) v.push_back(d);
    }
    for(int i=1;i<=20 && success;i++) {
      double start = u2f(f2u(0.25 * i)-20), end = u2f(f2u(0.25 * i)+20);
      for(double d = start;d <= end;d = u2f(f2u(d)+1)) v.push_back(d);
    }

    cout << "sinpif_u05" << endl;
    success = check_f_f<double>("sinpif_u05", xsinpif_u05, tlfloat_sinpi,
                                af2, sizeof(af2)/sizeof(af2[0]), 0.506, true) && success;
    success = check_f_f<double>("sinpif_u05", xsinpif_u05, tlfloat_sinpi,
                                -10.1, 10, 0.0021, 0.506, false) && success;
    success = check_f_f<double>("sinpif_u05", xsinpif_u05, tlfloat_sinpi,
                                -10000-0.1, 10000, 1.1, 0.506, false) && success;
    success = check_f_f<double>("sinpif_u05", xsinpif_u05, tlfloat_sinpi,
                                v.data(), v.size(), 0.506, false) && success;

    cout << "sin in sincospif_u35" << endl;
    success = checkX_f_f<double>("sin in sincospif_u35", xsincospif_u35, tlfloat_sinpi,
                                 af2, sizeof(af2)/sizeof(af2[0]), 3.5, true) && success;
    success = checkX_f_f<double>("sin in sincospif_u35", xsincospif_u35, tlfloat_sinpi,
                                 -10.1, 10, 0.0021, 3.5, false) && success;
    success = checkX_f_f<double>("sin in sincospif_u35", xsincospif_u35, tlfloat_sinpi,
                                 -10000-0.1, 10000, 1.1, 3.5, false) && success;
    success = checkX_f_f<double>("sin in sincospif_u35", xsincospif_u35, tlfloat_sinpi,
                                 v.data(), v.size(), 3.5, false) && success;

    cout << "sin in sincospif_u05" << endl;
    success = checkX_f_f<double>("sin in sincospif_u05", xsincospif_u05, tlfloat_sinpi,
                                 af2, sizeof(af2)/sizeof(af2[0]), 0.506, true) && success;
    success = checkX_f_f<double>("sin in sincospif_u05", xsincospif_u05, tlfloat_sinpi,
                                 -10.1, 10, 0.0021, 0.506, false) && success;
    success = checkX_f_f<double>("sin in sincospif_u05", xsincospif_u05, tlfloat_sinpi,
                                 -10000-0.1, 10000, 1.1, 0.506, false) && success;
    success = checkX_f_f<double>("sin in sincospif_u05", xsincospif_u05, tlfloat_sinpi,
                                 v.data(), v.size(), 0.506, false) && success;

    cout << "cospif_u05" << endl;
    success = check_f_f<double>("cospif_u05", xcospif_u05, tlfloat_cospi,
                                af2, sizeof(af2)/sizeof(af2[0]), 0.506, true) && success;
    success = check_f_f<double>("cospif_u05", xcospif_u05, tlfloat_cospi,
                                -10.1, 10, 0.0021, 0.506, false) && success;
    success = check_f_f<double>("cospif_u05", xcospif_u05, tlfloat_cospi,
                                -10000-0.1, 10000, 1.1, 0.506, false) && success;
    success = check_f_f<double>("cospif_u05", xcospif_u05, tlfloat_cospi,
                                v.data(), v.size(), 0.506, false) && success;

    cout << "cos in sincospif_u35" << endl;
    success = checkY_f_f<double>("cos in sincospif_u35", xsincospif_u35, tlfloat_cospi,
                                 af2, sizeof(af2)/sizeof(af2[0]), 3.5, true) && success;
    success = checkY_f_f<double>("cos in sincospif_u35", xsincospif_u35, tlfloat_cospi,
                                 -10.1, 10, 0.0021, 3.5, false) && success;
    success = checkY_f_f<double>("cos in sincospif_u35", xsincospif_u35, tlfloat_cospi,
                                 -10000-0.1, 10000, 1.1, 3.5, false) && success;
    success = checkY_f_f<double>("cos in sincospif_u35", xsincospif_u35, tlfloat_cospi,
                                 v.data(), v.size(), 3.5, false) && success;

    cout << "cos in sincospif_u05" << endl;
    success = checkY_f_f<double>("cos in sincospif_u05", xsincospif_u05, tlfloat_cospi,
                                 af2, sizeof(af2)/sizeof(af2[0]), 0.506, true) && success;
    success = checkY_f_f<double>("cos in sincospif_u05", xsincospif_u05, tlfloat_cospi,
                                 -10.1, 10, 0.0021, 0.506, false) && success;
    success = checkY_f_f<double>("cos in sincospif_u05", xsincospif_u05, tlfloat_cospi,
                                 -10000-0.1, 10000, 1.1, 0.506, false) && success;
    success = checkY_f_f<double>("cos in sincospif_u05", xsincospif_u05, tlfloat_cospi,
                                 v.data(), v.size(), 0.506, false) && success;
  }

  {
    vector<float> v;
    v.push_back(70.936981201171875);
    for(int i = 0;i < 1000;i++) v.push_back(pow(1.092, i));
    for(int i=1;i<10000 && success;i+=31) {
      double start = u2f(f2u(M_PI / 4 * i)-20), end = u2f(f2u(M_PI / 4 * i)+20);
      for(double d = start;d <= end;d = u2f(f2u(d)+1)) v.push_back(d);
    }

    cout << "tanf" << endl;
    success = check_f_f<double>("tanf", xtanf, tlfloat_tan,
                                af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
    success = check_f_f<double>("tanf", xtanf, tlfloat_tan,
                                -10, 10, 0.002, 3.5, false) && success;
    success = check_f_f<double>("tanf", xtanf, tlfloat_tan,
                                -10000, 10000, 1.1, 3.5, false) && success;
    success = check_f_f<double>("tanf", xtanf, tlfloat_tan,
                                v.data(), v.size(), 3.5, false) && success;

    cout << "tanf_u1" << endl;
    success = check_f_f<double>("tanf_u1", xtanf_u1, tlfloat_tan,
                                af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
    success = check_f_f<double>("tanf_u1", xtanf_u1, tlfloat_tan,
                                -10, 10, 0.002, 1.0, false) && success;
    success = check_f_f<double>("tanf_u1", xtanf_u1, tlfloat_tan,
                                -10000, 10000, 1.1, 1.0, false) && success;
    success = check_f_f<double>("tanf_u1", xtanf_u1, tlfloat_tan,
                                v.data(), v.size(), 1.0, false) && success;
  }

  {
    vector<float> v;
    for(int i = -1000;i <= 1000 && success;i+=10) v.push_back(pow(2.1, i));
    for(int i=0;i<10000 && success;i+=10) v.push_back(FLT_MAX * pow(0.9314821319758632, i));
    for(int i=0;i<10000 && success;i+=10) v.push_back(pow(0.933254300796991, i));
    for(int i=0;i<10000 && success;i+=10) v.push_back(FLT_MIN * pow(0.996323, i));

    cout << "logf" << endl;
    success = check_f_f<double>("logf", xlogf, tlfloat_log,
                                af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
    success = check_f_f<double>("logf", xlogf, tlfloat_log,
                                0.0001, 10, 0.001, 3.5, false) && success;
    success = check_f_f<double>("logf", xlogf, tlfloat_log,
                                0.0001, 10000, 1.1, 3.5, false) && success;
    success = check_f_f<double>("logf", xlogf, tlfloat_log,
                                v.data(), v.size(), 3.5, false) && success;

    cout << "logf_u1" << endl;
    success = check_f_f<double>("logf_u1", xlogf_u1, tlfloat_log,
                                af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
    success = check_f_f<double>("logf_u1", xlogf_u1, tlfloat_log,
                                0.0001, 10, 0.001, 1.0, false) && success;
    success = check_f_f<double>("logf_u1", xlogf_u1, tlfloat_log,
                                0.0001, 10000, 1.1, 1.0, false) && success;
    success = check_f_f<double>("logf_u1", xlogf_u1, tlfloat_log,
                                v.data(), v.size(), 1.0, false) && success;

    cout << "log10f" << endl;
    success = check_f_f<double>("log10f", xlog10f, tlfloat_log10,
                                af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
    success = check_f_f<double>("log10f", xlog10f, tlfloat_log10,
                                0.0001, 10, 0.001, 1.0, false) && success;
    success = check_f_f<double>("log10f", xlog10f, tlfloat_log10,
                                0.0001, 10000, 1.1, 1.0, false) && success;
    success = check_f_f<double>("log10f", xlog10f, tlfloat_log10,
                                v.data(), v.size(), 1.0, false) && success;

    cout << "log2f" << endl;
    success = check_f_f<double>("log2f", xlog2f, tlfloat_log2,
                                af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
    success = check_f_f<double>("log2f", xlog2f, tlfloat_log2,
                                0.0001, 10, 0.001, 1.0, false) && success;
    success = check_f_f<double>("log2f", xlog2f, tlfloat_log2,
                                0.0001, 10000, 1.1, 1.0, false) && success;
    success = check_f_f<double>("log2f", xlog2f, tlfloat_log2,
                                v.data(), v.size(), 1.0, false) && success;

    cout << "log2f_u35" << endl;
    success = check_f_f<double>("log2f_u35", xlog2f_u35, tlfloat_log2,
                                af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
    success = check_f_f<double>("log2f_u35", xlog2f_u35, tlfloat_log2,
                                0.0001, 10, 0.001, 3.5, false) && success;
    success = check_f_f<double>("log2f_u35", xlog2f_u35, tlfloat_log2,
                                0.0001, 10000, 1.1, 3.5, false) && success;
    success = check_f_f<double>("log2f_u35", xlog2f_u35, tlfloat_log2,
                                v.data(), v.size(), 3.5, false) && success;

    static const float af2[] = {
      +0.0, -0.0, +1, -1, +1e+10, -1e+10, FLT_MIN, -FLT_MIN,
      INFINITY, -INFINITY, NAN, nextafterf(-1, -2), -2 };

    cout << "log1pf" << endl;
    success = check_f_f<double>("log1pf", xlog1pf, tlfloat_log1p,
                                af2, sizeof(af2)/sizeof(af2[0]), 1.0, true) && success;
    success = check_f_f<double>("log1pf", xlog1pf, tlfloat_log1p,
                                0.0001, 10, 0.001, 1.0, false) && success;
  }

  cout << "expf" << endl;
  success = check_f_f<double>("expf", xexpf, tlfloat_exp,
                              af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
  success = check_f_f<double>("expf", xexpf, tlfloat_exp,
                              -10, 10, 0.002, 1.0, false) && success;
  success = check_f_f<double>("expf", xexpf, tlfloat_exp,
                              -1000, 1000, 1.1, 1.0, false) && success;

  cout << "exp2f" << endl;
  success = check_f_f<double>("exp2f", xexp2f, tlfloat_exp2,
                              af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
  success = check_f_f<double>("exp2f", xexp2f, tlfloat_exp2,
                              -10, 10, 0.002, 1.0, false) && success;
  success = check_f_f<double>("exp2f", xexp2f, tlfloat_exp2,
                              -1000, 1000, 0.2, 1.0, false) && success;

  cout << "exp10" << endl;
  success = check_f_f<double>("exp10", xexp10f, tlfloat_exp10,
                              af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
  success = check_f_f<double>("exp10", xexp10f, tlfloat_exp10,
                              -10, 10, 0.002, 1.0, false) && success;
  success = check_f_f<double>("exp10", xexp10f, tlfloat_exp10,
                              -300, 300, 0.1, 1.0, false) && success;

  cout << "exp2f_u35" << endl;
  success = check_f_f<double>("exp2f_u35", xexp2f_u35, tlfloat_exp2,
                              af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
  success = check_f_f<double>("exp2f_u35", xexp2f_u35, tlfloat_exp2,
                              -10, 10, 0.002, 3.5, false) && success;
  success = check_f_f<double>("exp2f_u35", xexp2f_u35, tlfloat_exp2,
                              -1000, 1000, 0.2, 3.5, false) && success;

  cout << "exp10f_u35" << endl;
  success = check_f_f<double>("exp10f_u35", xexp10f_u35, tlfloat_exp10,
                              af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
  success = check_f_f<double>("exp10f_u35", xexp10f_u35, tlfloat_exp10,
                              -10, 10, 0.002, 3.5, false) && success;
  success = check_f_f<double>("exp10f_u35", xexp10f_u35, tlfloat_exp10,
                              -300, 300, 0.1, 3.5, false) && success;

  {
    vector<float> v;
    for(double d = 0;d < 300 && success;d += 0.21) {
      v.push_back(+pow(10, -d));
      v.push_back(-pow(10, -d));
    }

    cout << "expm1f" << endl;
    success = check_f_f<double>("expm1f", xexpm1f, tlfloat_expm1,
                                af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
    success = check_f_f<double>("expm1f", xexpm1f, tlfloat_expm1,
                                -10, 10, 0.002, 1.0, false) && success;
    success = check_f_f<double>("expm1f", xexpm1f, tlfloat_expm1,
                                -1000, 1000, 0.21, 1.0, false) && success;
    success = check_f_f<double>("expm1f", xexpm1f, tlfloat_expm1,
                                v.data(), v.size(), 1.0, false) && success;
  }

  cout << "powf" << endl;
  success = check_f_f_f<double>("powf", xpowf, tlfloat_pow,
                                af, sizeof(af)/sizeof(af[0]), af, sizeof(af)/sizeof(af[0]),
                                1.0, true) && success;
  success = check_f_f_f<double>("powf", xpowf, tlfloat_pow,
                                -100, 100, 0.6, 0.1, 100, 0.6, 1.0, false) && success;
  vector<float> v, w;
  for(double y = -1000;y < 1000;y += 0.1) v.push_back(y);
  w.push_back(2.1);
  success = check_f_f_f<double>("powf", xpowf, tlfloat_pow,
                                w.data(), w.size(), v.data(), v.size(), 1.0, false) && success;

  cout << "fastpowf_u3500" << endl;
  success = check_f_f_f<double>("fastpowf_u3500", xfastpowf_u3500, tlfloat_pow,
                                0.1, 25, 0.251, -25, 25, 0.121, 350.0, false) && success;

  {
    vector<float> v;
    for(int i = -1000;i <= 1000 && success;i+=10) v.push_back(pow(2.1, i));

#ifndef DETERMINISTIC
    cout << "sqrtf" << endl;
    success = check_f_f<double>("sqrtf", xsqrtf, tlfloat_sqrt,
                                af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
    success = check_f_f<double>("sqrtf", xsqrtf, tlfloat_sqrt,
                                -10000, 10000, 2.1, 1.0, false) && success;
    success = check_f_f<double>("sqrtf", xsqrtf, tlfloat_sqrt,
                                v.data(), v.size(), 1.0, false) && success;

    cout << "sqrtf_u05" << endl;
    success = check_f_f<double>("sqrtf", xsqrtf_u05, tlfloat_sqrt,
                                af, sizeof(af)/sizeof(af[0]), 0.506, true) && success;
    success = check_f_f<double>("sqrtf", xsqrtf_u05, tlfloat_sqrt,
                                -10000, 10000, 2.1, 0.506, false) && success;
    success = check_f_f<double>("sqrtf", xsqrtf_u05, tlfloat_sqrt,
                                v.data(), v.size(), 0.506, false) && success;

    cout << "sqrtf_u35" << endl;
    success = check_f_f<double>("sqrtf", xsqrtf_u35, tlfloat_sqrt,
                                af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
    success = check_f_f<double>("sqrtf", xsqrtf_u35, tlfloat_sqrt,
                                -10000, 10000, 2.1, 3.5, false) && success;
    success = check_f_f<double>("sqrtf", xsqrtf_u35, tlfloat_sqrt,
                                v.data(), v.size(), 3.5, false) && success;
#endif

    cout << "cbrtf" << endl;
    success = check_f_f<double>("cbrtf", xcbrtf, tlfloat_cbrt,
                                af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
    success = check_f_f<double>("cbrtf", xcbrtf, tlfloat_cbrt,
                                -10000, 10000, 2.1, 3.5, false) && success;
    success = check_f_f<double>("cbrtf", xcbrtf, tlfloat_cbrt,
                                v.data(), v.size(), 3.5, false) && success;

    cout << "cbrtf_u1" << endl;
    success = check_f_f<double>("cbrtf_u1", xcbrtf_u1, tlfloat_cbrt,
                                af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
    success = check_f_f<double>("cbrtf_u1", xcbrtf_u1, tlfloat_cbrt,
                                -10000, 10000, 2.1, 1.0, false) && success;
    success = check_f_f<double>("cbrtf_u1", xcbrtf_u1, tlfloat_cbrt,
                                v.data(), v.size(), 1.0, false) && success;
  }

  cout << "hypotf_u35" << endl;
  success = check_f_f_f<double>("hypotf_u35", xhypotf_u35, hypot,
                                af, sizeof(af)/sizeof(af[0]), af, sizeof(af)/sizeof(af[0]),
                                3.5, true) && success;
  success = check_f_f_f<double>("hypotf_u35", xhypotf_u35, hypot,
                                -10, 10, 0.15, -10, 10, 0.15, 3.5, false) && success;
  success = check_f_f_f<double>("hypotf_u35", xhypotf_u35, hypot,
                                -1e+10, 1e+10, 1.51e+8, -1e+10, 1e+10, 1.51e+8, 3.5, false) && success;

  cout << "hypotf_u05" << endl;
  success = check_f_f_f<double>("hypotf_u05", xhypotf_u05, hypot,
                                af, sizeof(af)/sizeof(af[0]), af, sizeof(af)/sizeof(af[0]),
                                0.5, true) && success;
  success = check_f_f_f<double>("hypotf_u05", xhypotf_u05, hypot,
                                -10, 10, 0.15, -10, 10, 0.15, 0.5, false) && success;
  success = check_f_f_f<double>("hypotf_u05", xhypotf_u05, hypot,
                                -1e+10, 1e+10, 1.51e+8, -1e+10, 1e+10, 1.51e+8, 0.5, false) && success;

  cout << "asinf" << endl;
  success = check_f_f<double>("asinf", xasinf, tlfloat_asin,
                              af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
  success = check_f_f<double>("asinf", xasinf, tlfloat_asin,
                              -1, 1, 0.0002, 3.5, false) && success;

  cout << "asinf_u1" << endl;
  success = check_f_f<double>("asinf_u1", xasinf_u1, tlfloat_asin,
                              af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
  success = check_f_f<double>("asinf_u1", xasinf_u1, tlfloat_asin,
                              -1, 1, 0.0002, 3.5, false) && success;

  cout << "acosf" << endl;
  success = check_f_f<double>("acosf", xacosf, tlfloat_acos,
                              af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
  success = check_f_f<double>("acosf", xacosf, tlfloat_acos,
                              -1, 1, 0.0002, 3.5, false) && success;

  cout << "acosf_u1" << endl;
  success = check_f_f<double>("acosf_u1", xacosf_u1, tlfloat_acos,
                              af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
  success = check_f_f<double>("acosf_u1", xacosf_u1, tlfloat_acos,
                              -1, 1, 0.0002, 1.0, false) && success;

  cout << "atanf" << endl;
  success = check_f_f<double>("atanf", xatanf, tlfloat_atan,
                              af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
  success = check_f_f<double>("atanf", xatanf, tlfloat_atan,
                              -10, 10, 0.002, 3.5, false) && success;
  success = check_f_f<double>("atanf", xatanf, tlfloat_atan,
                              -10000, 10000, 2.1, 3.5, false) && success;

  cout << "atanf_u1" << endl;
  success = check_f_f<double>("atanf_u1", xatanf_u1, tlfloat_atan,
                              af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
  success = check_f_f<double>("atanf_u1", xatanf_u1, tlfloat_atan,
                              -10, 10, 0.002, 1.0, false) && success;
  success = check_f_f<double>("atanf_u1", xatanf_u1, tlfloat_atan,
                              -10000, 10000, 2.1, 1.0, false) && success;

  cout << "atan2f" << endl;
  success = check_f_f_f<double>("atan2f", xatan2f, tlfloat_atan2,
                                af, sizeof(af)/sizeof(af[0]), af, sizeof(af)/sizeof(af[0]),
                                3.5, true) && success;
  success = check_f_f_f<double>("atan2f", xatan2f, tlfloat_atan2,
                                -10, 10, 0.15, -10, 10, 0.15, 3.5, false) && success;
  success = check_f_f_f<double>("atan2f", xatan2f, tlfloat_atan2,
                                -100, 100, 1.51, -100, 100, 1.51, 3.5, false) && success;

  cout << "atan2f_u1" << endl;
  success = check_f_f_f<double>("atan2f_u1", xatan2f_u1, tlfloat_atan2,
                                af, sizeof(af)/sizeof(af[0]), af, sizeof(af)/sizeof(af[0]),
                                1.0, true) && success;
  success = check_f_f_f<double>("atan2f_u1", xatan2f_u1, tlfloat_atan2,
                                -10, 10, 0.15, -10, 10, 0.15, 1.0, false) && success;
  success = check_f_f_f<double>("atan2f_u1", xatan2f_u1, tlfloat_atan2,
                                -100, 100, 1.51, -100, 100, 1.51, 1.0, false) && success;

  cout << "sinhf" << endl;
  success = check_f_f<double>("sinhf", xsinhf, tlfloat_sinh,
                              af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
  success = check_f_f<double>("sinhf", xsinhf, tlfloat_sinh,
                              -10, 10, 0.002, 1.0, false) && success;
  success = check_f_f<double>("sinhf", xsinhf, tlfloat_sinh,
                              -88, 88, 0.2, 1.0, false) && success;

  cout << "coshf" << endl;
  success = check_f_f<double>("coshf", xcoshf, tlfloat_cosh,
                              af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
  success = check_f_f<double>("coshf", xcoshf, tlfloat_cosh,
                              -10, 10, 0.002, 1.0, false) && success;
  success = check_f_f<double>("coshf", xcoshf, tlfloat_cosh,
                              -88, 88, 0.2, 1.0, false) && success;

  cout << "tanhf" << endl;
  success = check_f_f<double>("tanhf", xtanhf, tlfloat_tanh,
                              af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
  success = check_f_f<double>("tanhf", xtanhf, tlfloat_tanh,
                              -10, 10, 0.002, 1.0, false) && success;
  success = check_f_f<double>("tanhf", xtanhf, tlfloat_tanh,
                              -1000, 1000, 0.2, 1.0, false) && success;

  cout << "sinhf_u35" << endl;
  success = check_f_f<double>("sinhf_u35", xsinhf_u35, tlfloat_sinh,
                              af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;

  success = check_f_f<double>("sinhf_u35", xsinhf_u35, tlfloat_sinh,
                              -10, 10, 0.002, 3.5, false) && success;
  success = check_f_f<double>("sinhf_u35", xsinhf_u35, tlfloat_sinh,
                              -88, 88, 0.2, 3.5, false) && success;

  cout << "coshf_u35" << endl;
  success = check_f_f<double>("coshf_u35", xcoshf_u35, tlfloat_cosh,
                              af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
  success = check_f_f<double>("coshf_u35", xcoshf_u35, tlfloat_cosh,
                              -10, 10, 0.002, 3.5, false) && success;
  success = check_f_f<double>("coshf_u35", xcoshf_u35, tlfloat_cosh,
                              -88, 88, 0.2, 3.5, false) && success;

  cout << "tanhf_u35" << endl;
  success = check_f_f<double>("tanhf_u35", xtanhf_u35, tlfloat_tanh,
                              af, sizeof(af)/sizeof(af[0]), 3.5, true) && success;
  success = check_f_f<double>("tanhf_u35", xtanhf_u35, tlfloat_tanh,
                              -10, 10, 0.002, 3.5, false) && success;
  success = check_f_f<double>("tanhf_u35", xtanhf_u35, tlfloat_tanh,
                              -1000, 1000, 0.2, 3.5, false) && success;

  {
    static const float af2[] = {
      +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MIN, -DBL_MIN, INFINITY, -INFINITY, NAN
    };

    cout << "asinhf" << endl;
    success = check_f_f<double>("asinhf", xasinhf, tlfloat_asinh,
                                af2, sizeof(af2)/sizeof(af2[0]), 1.0, true) && success;
    success = check_f_f<double>("asinhf", xasinhf, tlfloat_asinh,
                                -10, 10, 0.002, 1.0, false) && success;
    success = check_f_f<double>("asinhf", xasinhf, tlfloat_asinh,
                                -1000, 1000, 0.2, 1.0, false) && success;

    cout << "acoshf" << endl;
    success = check_f_f<double>("acoshf", xacoshf, tlfloat_acosh,
                                af2, sizeof(af2)/sizeof(af2[0]), 1.0, true) && success;
    success = check_f_f<double>("acoshf", xacoshf, tlfloat_acosh,
                                1, 10, 0.002, 1.0, false) && success;
    success = check_f_f<double>("acoshf", xacoshf, tlfloat_acosh,
                                1, 1000, 0.2, 1.0, false) && success;
  }

  cout << "atanhf" << endl;
  success = check_f_f<double>("atanhf", xatanhf, tlfloat_atanh,
                              af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
  success = check_f_f<double>("atanhf", xatanhf, tlfloat_atanh,
                              -10, 10, 0.002, 1.0, false) && success;
  success = check_f_f<double>("atanhf", xatanhf, tlfloat_atanh,
                              -1000, 1000, 0.2, 1.0, false) && success;

  cout << "copysignf" << endl;
  success = check_f_f_f<double>("copysignf", xcopysignf, tlfloat_copysign,
                                af, sizeof(af)/sizeof(af[0]), af, sizeof(af)/sizeof(af[0]),
                                0.0, true) && success;
  success = check_f_f_f<double>("copysignf", xcopysignf, tlfloat_copysign,
                                -10, 10, 0.15, -10, 10, 0.15, 0.0, false) && success;
  success = check_f_f_f<double>("copysignf", xcopysignf, tlfloat_copysign,
                                -1e+7, 1e+7, 1.51e+5, -1e+7, 1e+7, 1.51e+5, 0.0, false) && success;

  cout << "fmaxf" << endl;
  success = check_f_f_f<double>("fmaxf", xfmaxf, tlfloat_fmax,
                                af, sizeof(af)/sizeof(af[0]), af, sizeof(af)/sizeof(af[0]),
                                0.0, false) && success;
  success = check_f_f_f<double>("fmaxf", xfmaxf, tlfloat_fmax,
                                -10, 10, 0.15, -10, 10, 0.15, 0.0, false) && success;
  success = check_f_f_f<double>("fmaxf", xfmaxf, tlfloat_fmax,
                                -1e+7, 1e+7, 1.51e+5, -1e+7, 1e+7, 1.51e+5, 0.0, false) && success;

  cout << "fminf" << endl;
  success = check_f_f_f<double>("fminf", xfminf, tlfloat_fmin,
                                af, sizeof(af)/sizeof(af[0]), af, sizeof(af)/sizeof(af[0]),
                                0.0, false) && success;
  success = check_f_f_f<double>("fminf", xfminf, tlfloat_fmin,
                                -10, 10, 0.15, -10, 10, 0.15, 0.0, false) && success;
  success = check_f_f_f<double>("fminf", xfminf, tlfloat_fmin,
                                -1e+7, 1e+7, 1.51e+5, -1e+7, 1e+7, 1.51e+5, 0.0, false) && success;

  cout << "fdimf" << endl;
  success = check_f_f_f<double>("fdimf", xfdimf, tlfloat_fdim,
                                af, sizeof(af)/sizeof(af[0]), af, sizeof(af)/sizeof(af[0]),
                                0.5, true) && success;
  success = check_f_f_f<double>("fdimf", xfdimf, tlfloat_fdim,
                                -10, 10, 0.15, -10, 10, 0.15, 0.5, false) && success;
  success = check_f_f_f<double>("fdimf", xfdimf, tlfloat_fdim,
                                -1e+7, 1e+7, 1.51e+5, -1e+7, 1e+7, 1.51e+5, 0.5, false) && success;

  cout << "fmodf" << endl;
  for(int i=0;i<int(sizeof(af)/sizeof(af[0]));i++) {
    for(int j=0;j<int(sizeof(af)/sizeof(af[0]));j++) {
      if (fabs_(af[i] / af[j]) > 1e+300) continue;
      success = check_f_f_f<double>("fmodf", xfmodf, tlfloat_fmod,
                                    &af[i], 1, &af[j], 1, 0.5, true) && success;
    }
  }
  success = check_f_f_f<double>("fmodf", xfmodf, tlfloat_fmod,
                                -10, 10, 0.15, -10, 10, 0.15, 0.5, false) && success;
  success = check_f_f_f<double>("fmodf", xfmodf, tlfloat_fmod,
                                -1e+7, 1e+7, 1.51e+5, -1e+7, 1e+7, 1.51e+5, 0.5, false) && success;

  cout << "remainderf" << endl;
  for(int i=0;i<int(sizeof(af)/sizeof(af[0]));i++) {
    for(int j=0;j<int(sizeof(af)/sizeof(af[0]));j++) {
      if (fabs_(af[i] / af[j]) > 1e+300) continue;
      success = check_f_f_f<double>("remainderf", xremainderf, tlfloat_remainder,
                                    &af[i], 1, &af[j], 1, 0.5, true) && success;
    }
  }
  {
    float af3x = 11114942644092928.0, af3y = 224544296009728.0;
    success = check_f_f_f<double>("remainderf", xremainderf, tlfloat_remainder,
                                  &af3x, 1, &af3y, 1, 0.5, false) && success;
  }
  success = check_f_f_f<double>("remainderf", xremainderf, tlfloat_remainder,
                                -10, 10, 0.15, -10, 10, 0.15, 0.5, false) && success;
  success = check_f_f_f<double>("remainderf", xremainderf, tlfloat_remainder,
                                -1e+7, 1e+7, 1.51e+5, -1e+7, 1e+7, 1.51e+5, 0.5, false) && success;

  {
    vector<float> v;
    for(double x = -100.5;x <= 100.5;x+=0.5) {
      for(double d = u2f(f2u(x)-3);d <= u2f(f2u(x)+3) && success;d = u2f(f2u(d)+1)) v.push_back(d);
      double start = u2f(f2u((double)(INT64_C(1) << 23))-20), end = u2f(f2u((double)(INT64_C(1) << 23))+20);
      for(double d = start;d <= end;d = u2f(f2u(d)+1)) { v.push_back(d); v.push_back(-d); }
    }

    cout << "truncf" << endl;
    success = check_f_f<double>("truncf", xtruncf, tlfloat_trunc,
                                af, sizeof(af)/sizeof(af[0]), 0.0, true) && success;
    success = check_f_f<double>("truncf", xtruncf, tlfloat_trunc,
                                v.data(), v.size(), 0.0, false) && success;
    success = check_f_f<double>("truncf", xtruncf, tlfloat_trunc,
                                -10000, 10000, 2.5, 0.0, false) && success;

    cout << "floorf" << endl;
    success = check_f_f<double>("floorf", xfloorf, tlfloat_floor,
                                af, sizeof(af)/sizeof(af[0]), 0.0, true) && success;
    success = check_f_f<double>("floorf", xfloorf, tlfloat_floor,
                                v.data(), v.size(), 0.0, false) && success;
    success = check_f_f<double>("floorf", xfloorf, tlfloat_floor,
                                -10000, 10000, 2.5, 0.0, false) && success;

    cout << "ceilf" << endl;
    success = check_f_f<double>("ceilf", xceilf, tlfloat_ceil,
                                af, sizeof(af)/sizeof(af[0]), 0.0, true) && success;
    success = check_f_f<double>("ceilf", xceilf, tlfloat_ceil,
                                v.data(), v.size(), 0.0, false) && success;
    success = check_f_f<double>("ceilf", xceilf, tlfloat_ceil,
                                -10000, 10000, 2.5, 0.0, false) && success;

    cout << "roundf" << endl;
    success = check_f_f<double>("roundf", xroundf, tlfloat_round,
                                af, sizeof(af)/sizeof(af[0]), 0.0, true) && success;
    success = check_f_f<double>("roundf", xroundf, tlfloat_round,
                                v.data(), v.size(), 0.0, false) && success;
    success = check_f_f<double>("roundf", xroundf, tlfloat_round,
                                -10000, 10000, 2.5, 0.0, false) && success;

    cout << "rintf" << endl;
    success = check_f_f<double>("rintf", xrintf, tlfloat_rint,
                                af, sizeof(af)/sizeof(af[0]), 0.0, true) && success;
    success = check_f_f<double>("rintf", xrintf, tlfloat_rint,
                                v.data(), v.size(), 0.0, false) && success;
    success = check_f_f<double>("rintf", xrintf, tlfloat_rint,
                                -10000, 10000, 2.5, 0.0, false) && success;
  }

  {
    static const float af2[] = {
      -4, -3, -2, -1, +0.0, -0.0, +1e+10, -1e+10, INFINITY, -INFINITY, NAN
    };

    cout << "lgammaf_u1" << endl;
    success = check_f_f<double>("lgammaf_u1", xlgammaf_u1, tlfloat_lgamma,
                                af2, sizeof(af2)/sizeof(af2[0]), 1.0, true) && success;
    success = check_f_f<double>("lgammaf_u1", xlgammaf_u1, tlfloat_lgamma,
                                -5000, 5000, 1.1, 1.0, false) && success;

    cout << "tgammaf_u1" << endl;
    success = check_f_f<double>("tgammaf_u1", xtgammaf_u1, tlfloat_tgamma,
                                af2, sizeof(af2)/sizeof(af2[0]), 1.0, true) && success;
    success = check_f_f<double>("tgammaf_u1", xtgammaf_u1, tlfloat_tgamma,
                                -10, 10, 0.002, 1.0, false) && success;
  }

  cout << "erff_u1" << endl;
  success = check_f_f<double>("erff_u1", xerff_u1, tlfloat_erf,
                              af, sizeof(af)/sizeof(af[0]), 1.0, true) && success;
  success = check_f_f<double>("erff_u1", xerff_u1, tlfloat_erf,
                              -100, 100, 0.02, 1.0, false) && success;

  cout << "erfcf_u15" << endl;
  success = check_f_f<double>("erfcf_u15", xerfcf_u15, tlfloat_erfc,
                              af, sizeof(af)/sizeof(af[0]), 1.5, true) && success;

  success = check_f_f<double>("erfcf_u15", xerfcf_u15, tlfloat_erfc,
                              -1, 8, 0.001, 1.5, false) && success;

  //

  if (success) {
    cout << "OK" << endl;
  } else {
    cout << "NG" << endl;
  }

  return success ? 0 : -1;
}
