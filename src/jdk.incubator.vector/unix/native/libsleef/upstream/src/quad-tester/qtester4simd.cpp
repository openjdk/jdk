//   Copyright Naoki Shibata and contributors 2010 - 2025.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <iostream>
#include <vector>
#include <chrono>
#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <cmath>
#include <cfloat>
#include <climits>
#include <cassert>

#include "misc.h"
#include "qtesterutil.h"

using namespace std;

//

#if !defined(USE_INLINE_HEADER)
#include "sleef.h"
#include "sleefquad.h"
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
#include "sleefquadinline_purecfma_scalar.h"
#endif

#endif // #if !defined(USE_INLINE_HEADER)

//

#ifdef ENABLE_PUREC_SCALAR
#include "qrenamepurec_scalar.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperpurec_scalar.h"
#define VARGQUAD Sleef_quad
#endif
#endif

#ifdef ENABLE_PURECFMA_SCALAR
#include "qrenamepurecfma_scalar.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 2
#include "helperpurec_scalar.h"
#define VARGQUAD Sleef_quad
#endif
#endif

#ifdef ENABLE_DSPSCALAR
#include "qrenamedspscalar.h"
#define CONFIG 1
#include "helperpurec_scalar.h"
#define VARGQUAD Sleef_quad
#endif

#ifdef ENABLE_SSE2
#include "qrenamesse2.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 2
#include "helpersse2.h"
#define VARGQUAD Sleef_quadx2
#endif
#endif

#ifdef ENABLE_AVX2128
#include "qrenameavx2128.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperavx2_128.h"
#define VARGQUAD Sleef_quadx2
#endif
#endif

#ifdef ENABLE_DSPX2_X86
#include "qrenamedspx2.h"
#define CONFIG 2
#include "helpersse2.h"
#define VARGQUAD Sleef_quadx2
#endif

#ifdef ENABLE_AVX2
#include "qrenameavx2.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperavx2.h"
#define VARGQUAD Sleef_quadx4
#endif
#endif

#ifdef ENABLE_AVX512F
#include "qrenameavx512f.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperavx512f.h"
#define VARGQUAD Sleef_quadx8
#endif
#endif

#ifdef ENABLE_ADVSIMD
#include "qrenameadvsimd.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperadvsimd.h"
#define VARGQUAD Sleef_quadx2
#endif
#endif

#ifdef ENABLE_DSPX2_AARCH64
#include "qrenamedspx2.h"
#define CONFIG 2
#include "helperadvsimd.h"
#define VARGQUAD Sleef_quadx2
#endif

#ifdef ENABLE_SVE
#include "qrenamesve.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helpersve.h"
#define VARGQUAD Sleef_svquad
#endif
#define SIZEOF_VARGQUAD (svcntd()*8)
#endif

#ifdef ENABLE_VSX
#include "qrenamevsx.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperpower_128.h"
#define VARGQUAD Sleef_quadx2
#endif
#endif

#ifdef ENABLE_VSX3
#include "qrenamevsx3.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 3
#include "helperpower_128.h"
#define VARGQUAD Sleef_quadx2
#endif
#endif

#ifdef ENABLE_DSPX2_PPC64
#include "qrenamedspx2.h"
#define CONFIG 1
#include "helperpower_128.h"
#define VARGQUAD Sleef_quadx2
#endif

#ifdef ENABLE_VXE
#include "qrenamevxe.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 140
#include "helpers390x_128.h"
#define VARGQUAD Sleef_quadx2
#endif
#endif

#ifdef ENABLE_VXE2
#include "qrenamevxe2.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 150
#include "helpers390x_128.h"
#define VARGQUAD Sleef_quadx2
#endif
#endif

#ifdef ENABLE_DSPX2_S390X
#include "qrenamedspx2.h"
#define CONFIG 140
#include "helpers390x_128.h"
#define VARGQUAD Sleef_quadx2
#endif

#ifdef ENABLE_RVVM1
#include "qrenamervvm1.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#define ENABLE_RVV_DP
#include "helperrvv.h"
#define VARGQUAD Sleef_rvvm1quad
#endif
#define SIZEOF_VARGQUAD (__riscv_vsetvlmax_e64m1()*8)
#endif

#ifdef ENABLE_RVVM2
#include "qrenamervvm2.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#define ENABLE_RVV_DP
#include "helperrvv.h"
#define VARGQUAD Sleef_rvvm2quad
#endif
#define SIZEOF_VARGQUAD (__riscv_vsetvlmax_e64m2()*8)
#endif


#ifndef VARGQUAD
#define VARGQUAD vargquad
#endif

#ifndef SIZEOF_VARGQUAD
#define SIZEOF_VARGQUAD sizeof(VARGQUAD)
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
#define vargquad CONCAT_SIMD_SUFFIX(vargquad, SIMD_SUFFIX)
#define vint CONCAT_SIMD_SUFFIX(vint, SIMD_SUFFIX)
#define vint2 CONCAT_SIMD_SUFFIX(vint2, SIMD_SUFFIX)
#define vdouble2 CONCAT_SIMD_SUFFIX(vdouble2, SIMD_SUFFIX)
#define vd2getx_vd_vd2 CONCAT_SIMD_SUFFIX(vd2getx_vd_vd2, SIMD_SUFFIX)
#define vd2gety_vd_vd2 CONCAT_SIMD_SUFFIX(vd2gety_vd_vd2, SIMD_SUFFIX)
#define vloadu_vd_p CONCAT_SIMD_SUFFIX(vloadu_vd_p, SIMD_SUFFIX)
#define vstoreu_v_p_vd CONCAT_SIMD_SUFFIX(vstoreu_v_p_vd, SIMD_SUFFIX)
#define vloadu_vi_p CONCAT_SIMD_SUFFIX(vloadu_vi_p, SIMD_SUFFIX)
#define vstoreu_v_p_vi CONCAT_SIMD_SUFFIX(vstoreu_v_p_vi, SIMD_SUFFIX)
#define vreinterpret_vm_vu64 CONCAT_SIMD_SUFFIX(vreinterpret_vm_vu64, SIMD_SUFFIX)
#define vreinterpret_vu64_vm CONCAT_SIMD_SUFFIX(vreinterpret_vu64_vm, SIMD_SUFFIX)
#define vreinterpret_vm_vi64 CONCAT_SIMD_SUFFIX(vreinterpret_vm_vi64, SIMD_SUFFIX)
#define vreinterpret_vi64_vm CONCAT_SIMD_SUFFIX(vreinterpret_vi64_vm, SIMD_SUFFIX)
#define vreinterpret_vm_vd CONCAT_SIMD_SUFFIX(vreinterpret_vm_vd, SIMD_SUFFIX)
#define vreinterpret_vd_vm CONCAT_SIMD_SUFFIX(vreinterpret_vd_vm, SIMD_SUFFIX)
#endif

//

extern "C" {
  int check_feature(double d, float f) {
    double s[VECTLENDP];
    for(int i=0;i<(int)VECTLENDP;i++) s[i] = d;
    VARGQUAD a = xcast_from_doubleq(vloadu_vd_p(s));
    a = xpowq_u10(a, a);
    vint vi = xicmpeqq(a, xsplatq(sleef_q(+0x1000000000000LL, 0x0000000000000000ULL, 0)));
    int t[VECTLENDP*2];
    memset(t, 0, sizeof(t));
    vstoreu_v_p_vi(t, vi);
    return t[0];
  }
}

//

static double maxULP = 0;

static bool check_q_q(const char *msg, VARGQUAD (*vfunc)(VARGQUAD), tlfloat_octuple (*tlfunc)(const tlfloat_octuple),
                      const tlfloat_quad *a0, size_t z, double tol, bool checkSignedZero) {
  VARGQUAD v0;
  for(size_t i=0;i<z;i++) {
    memrand(&v0, SIZEOF_VARGQUAD);
    int idx = xrand() % VECTLENDP;
    v0 = xsetq(v0, idx, (Sleef_quad)a0[i]);
    v0 = (*vfunc)(v0);
    tlfloat_octuple t = (tlfloat_quad)xgetq(v0, idx), c = (*tlfunc)(a0[i]);
    double u = countULP<tlfloat_octuple>(t, c, TLFLOAT_FLT128_MANT_DIG,
                                         TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, checkSignedZero);
    // tlfloat_printf("t = %.35Og, c = %.35Og, ulp = %g\n", t, c, u);
    if (u > maxULP) maxULP = u;
    if (u > tol) {
      tlfloat_printf("%s : arg = %Qa (%.35Qg), ulp = %g, t = %.35Og, c = %.35Og\n", msg, a0[i], a0[i], u, t, c);
      return false;
    }
  }
  return true;
}

static bool check_q_q(const char *msg, VARGQUAD (*vfunc)(VARGQUAD), tlfloat_octuple (*tlfunc)(const tlfloat_octuple),
                      const char *minStr, const char *maxStr, bool sign, int nLoop, uint64_t seed, double tol, bool checkSignedZero) {
  xsrand(seed);
  tlfloat_quad min = tlfloat_strtoq(minStr, nullptr), max = tlfloat_strtoq(maxStr, nullptr);
  VARGQUAD v0;
  for(int i=0;i<nLoop;i++) {
    tlfloat_quad x = rndf128((Sleef_quad)min, (Sleef_quad)max, sign);
    memrand(&v0, SIZEOF_VARGQUAD);
    int idx = xrand() % VECTLENDP;
    v0 = xsetq(v0, idx, (Sleef_quad)x);
    v0 = (*vfunc)(v0);
    tlfloat_octuple t = (tlfloat_quad)xgetq(v0, idx), c = (*tlfunc)(x);
    double u = countULP<tlfloat_octuple>(t, c, TLFLOAT_FLT128_MANT_DIG,
                                         TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, checkSignedZero);
    // tlfloat_printf("t = %.35Og, c = %.35Og, ulp = %g\n", t, c, u);
    if (u > maxULP) maxULP = u;
    if (u > tol) {
      tlfloat_printf("%s : arg = %Qa (%.35Qg), ulp = %g, t = %.35Og, c = %.35Og\n", msg, x, x, u, t, c);
      return false;
    }
  }
  return true;
}

static bool check_q_q_q(const char *msg, VARGQUAD (*vfunc)(VARGQUAD, VARGQUAD),
                        tlfloat_octuple (*tlfunc)(const tlfloat_octuple, const tlfloat_octuple),
                        const tlfloat_quad *a, size_t z, double tol, bool checkSignedZero) {
  VARGQUAD v0, v1;
  for(size_t i=0;i<z;i++) {
    for(size_t j=0;j<z;j++) {
      memrand(&v0, SIZEOF_VARGQUAD);
      memrand(&v1, SIZEOF_VARGQUAD);
      int idx = xrand() % VECTLENDP;
      v0 = xsetq(v0, idx, (Sleef_quad)a[i]);
      v1 = xsetq(v1, idx, (Sleef_quad)a[j]);
      v0 = (*vfunc)(v0, v1);
      tlfloat_octuple t = (tlfloat_quad)xgetq(v0, idx), c = (*tlfunc)(a[i], a[j]);
      double u = countULP<tlfloat_octuple>(t, c, TLFLOAT_FLT128_MANT_DIG,
                                           TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, checkSignedZero);
      //tlfloat_printf("t = %.35Og, c = %.35Og, ulp = %g\n", t, c, u);
      if (u > maxULP) maxULP = u;
      if (u > tol) {
        tlfloat_printf("%s : arg0 = %Qa (%.35Qg), arg1 = %Qa (%.35Qg), ulp = %g, t = %Oa (%.35Og), c = %Oa (%.35Og)\n", msg, a[i], a[i], a[j], a[j], u, t, t, c, c);
        tlfloat_printf("c = %Qa (%.35Qg)\n", (tlfloat_quad)c, (tlfloat_quad)c);
        return false;
      }
    }
  }
  return true;
}

static bool check_q_q_q(const char *msg, VARGQUAD (*vfunc)(VARGQUAD, VARGQUAD),
                        tlfloat_octuple (*tlfunc)(const tlfloat_octuple, const tlfloat_octuple),
                        const char *minStr, const char *maxStr, bool sign, int nLoop, uint64_t seed, double tol, bool checkSignedZero) {
  xsrand(seed);
  tlfloat_quad min = tlfloat_strtoq(minStr, nullptr), max = tlfloat_strtoq(maxStr, nullptr);
  VARGQUAD v0, v1;
  for(int i=0;i<nLoop;i++) {
    int idx = xrand() % VECTLENDP;
    memrand(&v0, SIZEOF_VARGQUAD);
    tlfloat_quad x = rndf128((Sleef_quad)min, (Sleef_quad)max, sign);
    v0 = xsetq(v0, idx, (Sleef_quad)x);
    memrand(&v1, SIZEOF_VARGQUAD);
    tlfloat_quad y = rndf128((Sleef_quad)min, (Sleef_quad)max, sign);
    v1 = xsetq(v1, idx, (Sleef_quad)y);
    v0 = (*vfunc)(v0, v1);
    tlfloat_octuple t = (tlfloat_quad)xgetq(v0, idx), c = (*tlfunc)(x, y);
    double u = countULP<tlfloat_octuple>(t, c, TLFLOAT_FLT128_MANT_DIG,
                                         TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, checkSignedZero);
    //tlfloat_printf("t = %.35Og, c = %.35Og, ulp = %g\n", t, c, u);
    if (u > maxULP) maxULP = u;
    if (u > tol) {
      tlfloat_printf("%s : arg0 = %Qa (%.35Qg), arg1 = %Qa (%.35Qg), ulp = %g, t = %Oa (%.35Og), c = %Oa (%.35Og)\n", msg, x, x, y, y, u, t, t, c, c);
      return false;
    }
  }
  return true;
}

static bool check_q_q_q_q(const char *msg, VARGQUAD (*vfunc)(VARGQUAD, VARGQUAD, VARGQUAD),
                          tlfloat_octuple (*tlfunc)(const tlfloat_octuple, const tlfloat_octuple, const tlfloat_octuple),
                          const tlfloat_quad *a, size_t z, double tol, bool checkSignedZero) {
  VARGQUAD v0, v1, v2;
  for(size_t i=0;i<z;i++) {
    for(size_t j=0;j<z;j++) {
      for(size_t k=0;k<z;k++) {
        memrand(&v0, SIZEOF_VARGQUAD);
        memrand(&v1, SIZEOF_VARGQUAD);
        memrand(&v2, SIZEOF_VARGQUAD);
        int idx = xrand() % VECTLENDP;
        v0 = xsetq(v0, idx, (Sleef_quad)a[i]);
        v1 = xsetq(v1, idx, (Sleef_quad)a[j]);
        v2 = xsetq(v2, idx, (Sleef_quad)a[k]);
        v0 = (*vfunc)(v0, v1, v2);
        tlfloat_octuple t = (tlfloat_quad)xgetq(v0, idx), c = (*tlfunc)(a[i], a[j], a[k]);
        double u = countULP<tlfloat_octuple>(t, c, TLFLOAT_FLT128_MANT_DIG,
                                             TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, checkSignedZero);
        //tlfloat_printf("t = %.35Og, c = %.35Og, ulp = %g\n", t, c, u);
        if (u > maxULP) maxULP = u;
        if (u > tol) {
          tlfloat_printf("%s : arg0 = %Qa (%.35Qg), arg1 = %Qa (%.35Qg), arg2 = %Qa (%.35Qg), ulp = %g, t = %Oa (%.35Og), c = %Oa (%.35Og)\n", msg, a[i], a[i], a[j], a[j], a[k], a[k], u, t, t, c, c);
          return false;
        }
      }
    }
  }
  return true;
}

static bool check_q_q_q_q(const char *msg, VARGQUAD (*vfunc)(VARGQUAD, VARGQUAD, VARGQUAD),
                          tlfloat_octuple (*tlfunc)(const tlfloat_octuple, const tlfloat_octuple, const tlfloat_octuple),
                          const char *minStr, const char *maxStr, bool sign, int nLoop, uint64_t seed, double tol, bool checkSignedZero) {
  xsrand(seed);
  tlfloat_quad min = tlfloat_strtoq(minStr, nullptr), max = tlfloat_strtoq(maxStr, nullptr);
  VARGQUAD v0, v1, v2;
  for(int i=0;i<nLoop;i++) {
    int idx = xrand() % VECTLENDP;
    memrand(&v0, SIZEOF_VARGQUAD);
    tlfloat_quad x = rndf128((Sleef_quad)min, (Sleef_quad)max, sign);
    v0 = xsetq(v0, idx, (Sleef_quad)x);
    memrand(&v1, SIZEOF_VARGQUAD);
    tlfloat_quad y = rndf128((Sleef_quad)min, (Sleef_quad)max, sign);
    v1 = xsetq(v1, idx, (Sleef_quad)y);
    memrand(&v2, SIZEOF_VARGQUAD);
    tlfloat_quad z = rndf128((Sleef_quad)min, (Sleef_quad)max, sign);
    v2 = xsetq(v2, idx, (Sleef_quad)z);
    v0 = (*vfunc)(v0, v1, v2);
    tlfloat_octuple t = (tlfloat_quad)xgetq(v0, idx), c = (*tlfunc)(x, y, z);
    double u = countULP<tlfloat_octuple>(t, c, TLFLOAT_FLT128_MANT_DIG,
                                         TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, checkSignedZero);
    //tlfloat_printf("t = %.35Og, c = %.35Og, ulp = %g\n", t, c, u);
    if (u > maxULP) maxULP = u;
    if (u > tol) {
      tlfloat_printf("%s : arg0 = %Qa (%.35Qg), arg1 = %Qa (%.35Qg), arg1 = %Qa (%.35Qg), ulp = %g, t = %Oa (%.35Og), c = %Oa (%.35Og)\n", msg, x, x, y, y, z, z, u, t, t, c, c);
      return false;
    }
  }
  return true;
}

static bool check_i_q_q(const char *msg, vint (*vfunc)(VARGQUAD, VARGQUAD), int (*tlfunc)(const tlfloat_octuple, const tlfloat_octuple),
                        const tlfloat_quad *a, size_t z) {
  VARGQUAD v0, v1;
  for(size_t i=0;i<z;i++) {
    for(size_t j=0;j<z;j++) {
      memrand(&v0, SIZEOF_VARGQUAD);
      memrand(&v1, SIZEOF_VARGQUAD);
      int idx = xrand() % VECTLENDP;
      v0 = xsetq(v0, idx, (Sleef_quad)a[i]);
      v1 = xsetq(v1, idx, (Sleef_quad)a[j]);
      int t0[VECTLENDP*2];
      vstoreu_v_p_vi(t0, (*vfunc)(v0, v1));
      int t = t0[idx];
      int c = (*tlfunc)(a[i], a[j]);
      if (t != c) {
        tlfloat_printf("%s : arg0 = %Qa (%.35Qg), arg1 = %Qa (%.35Qg), t = %d, c = %d\n", msg, a[i], a[i], a[j], a[j], t, c);
        return false;
      }
    }
  }
  return true;
}

void check(const tlfloat_quad t, const tlfloat_quad c, int nbmant,
           const tlfloat_quad flmin, const tlfloat_quad flmax, const double culp) {
  double tulp = countULP<tlfloat_octuple>(t, c, nbmant, flmin, flmax, true);
  if (tulp != culp) {
    cout << "NG" << endl;
    tlfloat_printf("t = %Oa %.35Og\n", t, t);
    tlfloat_printf("c = %Oa %.35Og\n", c, c);
    printf("tulp = %g\n", tulp);
    printf("culp = %g\n", culp);
    exit(-1);
  }
}

void showULP(bool success) {
  printf("%s (%g ulp)\n", success ? "OK" : "NG", maxULP);
  maxULP = 0;
}

//

extern "C" {
  int main2(int argc, char **argv);
}

int main2(int argc, char **argv) {
  bool success = true;
  const int64_t NTEST = argc == 1 ? 1000 : strtoll(argv[1], NULL, 10);

  // Tests if counting ulp numbers is correct

  check(+0.0, +0.0, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX,     0);
  check(-0.0, +0.0, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 10002);
  check(+0.0, -0.0, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 10002);
  check(-0.0, -0.0, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX,     0);

  check(+1.0, +1.0, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX,     0);
  check(tlfloat_nextafterq(+1.0, +INFINITY), +1.0, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 1.0);
  check(tlfloat_nextafterq(+1.0, -INFINITY), +1.0, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 0.5);

  check(-1.0, -1.0, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX,     0);
  check(tlfloat_nextafterq(-1.0, +INFINITY), -1.0, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 0.5);
  check(tlfloat_nextafterq(-1.0, -INFINITY), -1.0, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 1.0);

  check(INFINITY, INFINITY, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 0);
  check(tlfloat_nextafterq(INFINITY, 0), INFINITY, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, INFINITY);
  check(INFINITY, tlfloat_nextafterq(INFINITY, 0), TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 1.0);

  check(-INFINITY, -INFINITY, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 0);
  check(tlfloat_nextafterq(-INFINITY, 0), -INFINITY, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, INFINITY);
  check(-INFINITY, tlfloat_nextafterq(-INFINITY, 0), TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 1.0);

  check(TLFLOAT_FLT128_MIN, TLFLOAT_FLT128_MIN, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 0);
  check(tlfloat_nextafterq(TLFLOAT_FLT128_MIN, 0.0), TLFLOAT_FLT128_MIN, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 1.0);
  check(tlfloat_nextafterq(TLFLOAT_FLT128_MIN, 1.0), TLFLOAT_FLT128_MIN, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 1.0);

  check(-TLFLOAT_FLT128_MIN, -TLFLOAT_FLT128_MIN, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 0);
  check(tlfloat_nextafterq(-TLFLOAT_FLT128_MIN, 0.0), -TLFLOAT_FLT128_MIN, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 1.0);
  check(tlfloat_nextafterq(-TLFLOAT_FLT128_MIN, 1.0), -TLFLOAT_FLT128_MIN, TLFLOAT_FLT128_MANT_DIG, TLFLOAT_FLT128_DENORM_MIN, TLFLOAT_FLT128_MAX, 1.0);

  //

#if !defined(ENABLE_PUREC_SCALAR) && !defined(ENABLE_PURECFMA_SCALAR) && !defined(ENABLE_DSPSCALAR)
  // Do simple testing on splat, select and sleef_q
  {
    VARGQUAD v0 = xsplatq(sleef_q(+0x1921fb54442d1LL, 0x8469898cc51701b8ULL, 1));
    VARGQUAD v1 = xsplatq(sleef_q(+0x0000000000000LL, 0x0000000000000000ULL, 0));
    v1 = xsetq(v1, 1, sleef_q(+0x15bf0a8b14576LL, 0x95355fb8ac404e7aULL, 1));
    v1 = xmulq_u05(v0, v1);

    vint vi = xicmpeqq(v1, xsplatq(sleef_q(+0x1114580b45d47LL, 0x49e6108579a2d0caULL, 3)));
    int t[VECTLENDP*2];
    memset(t, 0, sizeof(t));
    vstoreu_v_p_vi(t, vi);

    if (!(t[0] == 0 && t[1] == 1)) {
      fprintf(stderr, "Testing on splat and select failed\n");
      exit(-1);
    }
  }
#endif

#if defined(SLEEF_QUAD_C)
  {
    VARGQUAD v0 = xsplatq(SLEEF_QUAD_C(3.141592653589793238462643383279502884));
    VARGQUAD v1 = xsplatq(sleef_q(+0x1921fb54442d1LL, 0x8469898cc51701b8ULL, 1));
    if (Sleef_icmpneq1_purecfma(xgetq(v0, 0), xgetq(v1, 0))) {
      fprintf(stderr, "Testing on SLEEF_QUAD_C failed\n");
      exit(-1);
    }
  }
#endif

  {
    VARGQUAD v0 = xsplatq(SLEEF_M_PIq);
    VARGQUAD v1 = xsplatq((Sleef_quad)tlfloat_strtoq("2.718281828459045235360287471352662498", NULL));
    Sleef_quad q = xgetq(xmulq_u05(v0, v1), 0);
    if (Sleef_icmpneq1_purecfma(q, (Sleef_quad)tlfloat_strtoq("8.539734222673567065463550869546573820", NULL))) {
      tlfloat_printf("Testing with xgetq failed : %.35Qg\n", q);
      exit(-1);
    }
  }

  //

#define STR_QUAD_MIN "3.36210314311209350626267781732175260e-4932"
#define STR_QUAD_MAX "1.18973149535723176508575932662800702e+4932"
#define STR_QUAD_DENORM_MIN "6.475175119438025110924438958227646552e-4966"

  static const char *stdCheckValsStr[] = {
    "-0.0", "0.0", "+0.25", "-0.25", "+0.5", "-0.5", "+0.75", "-0.75", "+1.0", "-1.0",
    "+1.25", "-1.25", "+1.5", "-1.5", "+2.0", "-2.0", "+2.5", "-2.5", "+3.0", "-3.0",
    "+4.0", "-4.0", "+5.0", "-5.0", "+6.0", "-6.0", "+7.0", "-7.0",
    "1.234", "-1.234", "+1.234e+100", "-1.234e+100", "+1.234e-100", "-1.234e-100",
    "+1.234e+3000", "-1.234e+3000", "+1.234e-3000", "-1.234e-3000",
    "3.1415926535897932384626433832795028841971693993751058209749445923078164",
    "+" STR_QUAD_MIN, "-" STR_QUAD_MIN,
    "+" STR_QUAD_DENORM_MIN, "-" STR_QUAD_DENORM_MIN,
    "Inf", "-Inf", "NaN"
  };

  static const char *noInfCheckValsStr[] = {
    "-0.0", "0.0", "+0.25", "-0.25", "+0.5", "-0.5", "+0.75", "-0.75", "+1.0", "-1.0",
    "+1.25", "-1.25", "+1.5", "-1.5", "+2.0", "-2.0", "+2.5", "-2.5", "+3.0", "-3.0",
    "+4.0", "-4.0", "+5.0", "-5.0", "+6.0", "-6.0", "+7.0", "-7.0",
    "1.234", "-1.234", "+1.234e+100", "-1.234e+100", "+1.234e-100", "-1.234e-100",
    "+1.234e+3000", "-1.234e+3000", "+1.234e-3000", "-1.234e-3000",
    "3.1415926535897932384626433832795028841971693993751058209749445923078164",
    "+" STR_QUAD_MIN, "-" STR_QUAD_MIN,
    "+" STR_QUAD_DENORM_MIN, "-" STR_QUAD_DENORM_MIN,
    "NaN"
  };

  static const char *trigCheckValsStr[] = {
    "3.141592653589793238462643383279502884197169399375105820974944592307",
    "6.283185307179586476925286766559005768394338798750211641949889184615",
    "25.13274122871834590770114706623602307357735519500084656779955673846",
    "402.1238596594935345232183530597763691772376831200135450847929078154",
    "102943.7080728303448379438983833027505093728468787234675417069844007",
    "6746518852.261009479299491324448129057382258893044021168813308929687",
    "28976077832308491369.53730422794043954984410931622923280838485698255",
    "534514292032483373929840186580935391650.3203828374578833308216124114",
    "1.8188578844588316214011747138886493132669668866419621497938607555896e+77"
    "3.141592653589793238462643383279502884197169399375105820974944592307e+1000",
    "3.141592653589793238462643383279502884197169399375105820974944592307e+2000",
  };

  static const char *bigIntCheckValsStr[] = {
    "+5192296858534827628530496329220094.0",
    "+5192296858534827628530496329220094.25",
    "+5192296858534827628530496329220094.5",
    "+5192296858534827628530496329220094.75",
    "+5192296858534827628530496329220095.0",
    "+5192296858534827628530496329220095.25",
    "+5192296858534827628530496329220095.5",
    "+5192296858534827628530496329220095.75",
    "+5192296858534827628530496329220096.0",
    "+5192296858534827628530496329220097.0",
    "+5192296858534827628530496329220098.0",
    "-5192296858534827628530496329220094.0",
    "-5192296858534827628530496329220094.25",
    "-5192296858534827628530496329220094.5",
    "-5192296858534827628530496329220094.75",
    "-5192296858534827628530496329220095.0",
    "-5192296858534827628530496329220095.25",
    "-5192296858534827628530496329220095.5",
    "-5192296858534827628530496329220095.75",
    "-5192296858534827628530496329220096.0",
    "-5192296858534827628530496329220097.0",
    "-5192296858534827628530496329220098.0",
  };

  static const char *log1pCheckValsStr[] = {
    "-.9", "-.99999999", "-.9999999999999999", "-.9999999999999999999999999999999999"
  };

#define DEFCHECKVALS(ASTR, AVAL)                                        \
  static tlfloat_quad AVAL[sizeof(ASTR)/sizeof(ASTR[0])];                \
  for(unsigned i=0;i<sizeof(ASTR)/sizeof(ASTR[0]);i++)                        \
    AVAL[i] = tlfloat_strtoq(ASTR[i], nullptr)

  DEFCHECKVALS(stdCheckValsStr, stdCheckVals);
  DEFCHECKVALS(noInfCheckValsStr, noInfCheckVals);
  DEFCHECKVALS(trigCheckValsStr, trigCheckVals);
  DEFCHECKVALS(bigIntCheckValsStr, bigIntCheckVals);
  DEFCHECKVALS(log1pCheckValsStr, log1pCheckVals);

  //

  const double arithEB = 0.5000000001;

  cout << "addq_u05 ";
  success = check_q_q_q("addq_u05", xaddq_u05, tlfloat_addo,
                        stdCheckVals, sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]),
                        arithEB, true) && success;
  success = check_q_q_q("addq_u05", xaddq_u05, tlfloat_addo, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q_q("addq_u05", xaddq_u05, tlfloat_addo, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "subq_u05 ";
  success = check_q_q_q("subq_u05", xsubq_u05, tlfloat_subo,
                        stdCheckVals, sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]),
                        arithEB, true) && success;
  success = check_q_q_q("subq_u05", xsubq_u05, tlfloat_subo, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q_q("subq_u05", xsubq_u05, tlfloat_subo, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "mulq_u05 ";
  success = check_q_q_q("mulq_u05", xmulq_u05, tlfloat_mulo,
                        stdCheckVals, sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]),
                        arithEB, true) && success;
  success = check_q_q_q("mulq_u05", xmulq_u05, tlfloat_mulo, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q_q("mulq_u05", xmulq_u05, tlfloat_mulo, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "divq_u05 ";
  success = check_q_q_q("divq_u05", xdivq_u05, tlfloat_divo,
                        stdCheckVals, sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]),
                        arithEB, true) && success;
  success = check_q_q_q("divq_u05", xdivq_u05, tlfloat_divo, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q_q("divq_u05", xdivq_u05, tlfloat_divo, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "fmaq_u05 ";
  success = check_q_q_q_q("fmaq_u05", xfmaq_u05, tlfloat_fmao,
                          stdCheckVals, sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]),
                          arithEB, true) && success;
  success = check_q_q_q_q("fmaq_u05", xfmaq_u05, tlfloat_fmao, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q_q_q("fmaq_u05", xfmaq_u05, tlfloat_fmao, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "negq ";
  success = check_q_q("negq", xnegq, tlfloat_nego, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), arithEB, true) && success;
  success = check_q_q("negq", xnegq, tlfloat_nego, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q("negq", xnegq, tlfloat_nego, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "fabsq ";
  success = check_q_q("fabsq", xfabsq, tlfloat_fabso, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), arithEB, true) && success;
  success = check_q_q("fabsq", xfabsq, tlfloat_fabso, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q("fabsq", xfabsq, tlfloat_fabso, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "fmaxq ";
  success = check_q_q_q("fmaxq", xfmaxq, tlfloat_fmaxo,
                        stdCheckVals, sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]),
                        arithEB, false) && success;
  success = check_q_q_q("fmaxq", xfmaxq, tlfloat_fmaxo, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q_q("fmaxq", xfmaxq, tlfloat_fmaxo, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "fminq ";
  success = check_q_q_q("fminq", xfminq, tlfloat_fmino,
                        stdCheckVals, sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]),
                        arithEB, false) && success;
  success = check_q_q_q("fminq", xfminq, tlfloat_fmino, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q_q("fminq", xfminq, tlfloat_fmino, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "copysignq ";
  success = check_q_q_q("copysignq", xcopysignq, tlfloat_copysigno,
                        stdCheckVals, sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]),
                        arithEB, true) && success;
  success = check_q_q_q("copysignq", xcopysignq, tlfloat_copysigno, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q_q("copysignq", xcopysignq, tlfloat_copysigno, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "fdimq_u05 ";
  success = check_q_q_q("fdimq_u05", xfdimq_u05, tlfloat_fdimo,
                        noInfCheckVals, sizeof(noInfCheckValsStr)/sizeof(noInfCheckValsStr[0]),
                        arithEB, true) && success;
  success = check_q_q_q("fdimq_u05", xfdimq_u05, tlfloat_fdimo, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q_q("fdimq_u05", xfdimq_u05, tlfloat_fdimo, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "fmodq ";
  success = check_q_q_q("fmodq", xfmodq, tlfloat_fmodo,
                        stdCheckVals, sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]),
                        arithEB, true) && success;
  success = check_q_q_q("fmodq", xfmodq, tlfloat_fmodo, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q_q("fmodq", xfmodq, tlfloat_fmodo, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "remainderq ";
  success = check_q_q_q("remainderq", xremainderq, tlfloat_remaindero,
                        stdCheckVals, sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]),
                        arithEB, true) && success;
  success = check_q_q_q("remainderq", xremainderq, tlfloat_remaindero, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q_q("remainderq", xremainderq, tlfloat_remaindero, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "hypotq_u05 ";
  success = check_q_q_q("hypotq_u05", xhypotq_u05, tlfloat_hypoto,
                        stdCheckVals, sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]),
                        arithEB, true) && success;
  success = check_q_q_q("hypotq_u05", xhypotq_u05, tlfloat_hypoto, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q_q("hypotq_u05", xhypotq_u05, tlfloat_hypoto, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "truncq ";
  success = check_q_q("truncq", xtruncq, tlfloat_trunco, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), arithEB, true) && success;
  success = check_q_q("truncq", xtruncq, tlfloat_trunco, bigIntCheckVals,
                      sizeof(bigIntCheckValsStr)/sizeof(bigIntCheckValsStr[0]), arithEB, true) && success;
  success = check_q_q("truncq", xtruncq, tlfloat_trunco, "1e-1" , "1e+100" , true, 10 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "floorq ";
  success = check_q_q("floorq", xfloorq, tlfloat_flooro, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), arithEB, true) && success;
  success = check_q_q("floorq", xfloorq, tlfloat_flooro, bigIntCheckVals,
                      sizeof(bigIntCheckValsStr)/sizeof(bigIntCheckValsStr[0]), arithEB, true) && success;
  success = check_q_q("floorq", xfloorq, tlfloat_flooro, "1e-1" , "1e+100" , true, 10 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "ceilq ";
  success = check_q_q("ceilq", xceilq, tlfloat_ceilo, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), arithEB, true) && success;
  success = check_q_q("ceilq", xceilq, tlfloat_ceilo, bigIntCheckVals,
                      sizeof(bigIntCheckValsStr)/sizeof(bigIntCheckValsStr[0]), arithEB, true) && success;
  success = check_q_q("ceilq", xceilq, tlfloat_ceilo, "1e-1" , "1e+100" , true, 10 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "roundq ";
  success = check_q_q("roundq", xroundq, tlfloat_roundo, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), arithEB, true) && success;
  success = check_q_q("roundq", xroundq, tlfloat_roundo, bigIntCheckVals,
                      sizeof(bigIntCheckValsStr)/sizeof(bigIntCheckValsStr[0]), arithEB, true) && success;
  success = check_q_q("roundq", xroundq, tlfloat_roundo, "1e-1" , "1e+100" , true, 10 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "rintq ";
  success = check_q_q("rintq", xrintq, tlfloat_rinto, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), arithEB, true) && success;
  success = check_q_q("rintq", xrintq, tlfloat_rinto, bigIntCheckVals,
                      sizeof(bigIntCheckValsStr)/sizeof(bigIntCheckValsStr[0]), arithEB, true) && success;
  success = check_q_q("rintq", xrintq, tlfloat_rinto, "1e-1" , "1e+100" , true, 10 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  cout << "sqrtq_u10 ";
  success = check_q_q("sqrtq_u10", xsqrtq_u05, tlfloat_sqrto, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), arithEB, true) && success;
  success = check_q_q("sqrtq_u10", xsqrtq_u05, tlfloat_sqrto, "1e-100" , "1e+100" , false, 5 * NTEST, 1ULL, arithEB, true) && success;
  success = check_q_q("sqrtq_u10", xsqrtq_u05, tlfloat_sqrto, "1e-4000", "1e+4000", false, 5 * NTEST, 1ULL, arithEB, true) && success;
  showULP(success);

  //

  fprintf(stderr, "icmpltq : ");
  success = check_i_q_q("icmpltq", xicmpltq, tlfloat_lt_o_o, stdCheckVals,
                        sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0])) && success;
  printf("%s\n", success ? "OK" : "NG");

  fprintf(stderr, "icmpgtq : ");
  success = check_i_q_q("icmpgtq", xicmpgtq, tlfloat_gt_o_o, stdCheckVals,
                        sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0])) && success;
  printf("%s\n", success ? "OK" : "NG");

  fprintf(stderr, "icmpleq : ");
  success = check_i_q_q("icmpleq", xicmpleq, tlfloat_le_o_o, stdCheckVals,
                        sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0])) && success;
  printf("%s\n", success ? "OK" : "NG");

  fprintf(stderr, "icmpgeq : ");
  success = check_i_q_q("icmpgeq", xicmpgeq, tlfloat_ge_o_o, stdCheckVals,
                        sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0])) && success;
  printf("%s\n", success ? "OK" : "NG");

  fprintf(stderr, "icmpeqq : ");
  success = check_i_q_q("icmpeqq", xicmpeqq, tlfloat_eq_o_o, stdCheckVals,
                        sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0])) && success;
  printf("%s\n", success ? "OK" : "NG");

  fprintf(stderr, "icmpneq : ");
  success = check_i_q_q("icmpneq", xicmpneq, tlfloat_ne_o_o, stdCheckVals,
                        sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0])) && success;
  printf("%s\n", success ? "OK" : "NG");

  //

  cout << "cbrtq_u10 ";
  success = check_q_q("cbrtq_u10", xcbrtq_u10, tlfloat_cbrto, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("cbrtq_u10", xcbrtq_u10, tlfloat_cbrto, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, 1.0, true) && success;
  success = check_q_q("cbrtq_u10", xcbrtq_u10, tlfloat_cbrto, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "sinq_u10 ";
  success = check_q_q("sinq_u10", xsinq_u10, tlfloat_sino, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("sinq_u10", xsinq_u10, tlfloat_sino, trigCheckVals,
                      sizeof(trigCheckValsStr)/sizeof(trigCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("sinq_u10", xsinq_u10, tlfloat_sino, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, 1.0, true) && success;
  success = check_q_q("sinq_u10", xsinq_u10, tlfloat_sino, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "cosq_u10 ";
  success = check_q_q("cosq_u10", xcosq_u10, tlfloat_coso, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("cosq_u10", xcosq_u10, tlfloat_coso, trigCheckVals,
                      sizeof(trigCheckValsStr)/sizeof(trigCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("cosq_u10", xcosq_u10, tlfloat_coso, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, 1.0, true) && success;
  success = check_q_q("cosq_u10", xcosq_u10, tlfloat_coso, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "tanq_u10 ";
  success = check_q_q("tanq_u10", xtanq_u10, tlfloat_tano, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("tanq_u10", xtanq_u10, tlfloat_tano, trigCheckVals,
                      sizeof(trigCheckValsStr)/sizeof(trigCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("tanq_u10", xtanq_u10, tlfloat_tano, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, 1.0, true) && success;
  success = check_q_q("tanq_u10", xtanq_u10, tlfloat_tano, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "asinq_u10 ";
  success = check_q_q("asinq_u10", xasinq_u10, tlfloat_asino, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("asinq_u10", xasinq_u10, tlfloat_asino, "1e-100" , "1", true, 10 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "acosq_u10 ";
  success = check_q_q("acosq_u10", xacosq_u10, tlfloat_acoso, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("acosq_u10", xacosq_u10, tlfloat_acoso, "1e-100" , "1", true, 10 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "atanq_u10 ";
  success = check_q_q("atanq_u10", xatanq_u10, tlfloat_atano, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("atanq_u10", xatanq_u10, tlfloat_atano, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, 1.0, true) && success;
  success = check_q_q("atanq_u10", xatanq_u10, tlfloat_atano, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "atan2q_u10 ";
  success = check_q_q_q("atan2q_u10", xatan2q_u10, tlfloat_atan2o,
                        stdCheckVals, sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]),
                        1.0, true) && success;
  success = check_q_q_q("atan2q_u10", xatan2q_u10, tlfloat_atan2o, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, 1.0, true) && success;
  success = check_q_q_q("atan2q_u10", xatan2q_u10, tlfloat_atan2o, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "expq_u10 ";
  success = check_q_q("expq_u10", xexpq_u10, tlfloat_expo, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("expq_u10", xexpq_u10, tlfloat_expo, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, 1.0, true) && success;
  success = check_q_q("expq_u10", xexpq_u10, tlfloat_expo, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "exp2q_u10 ";
  success = check_q_q("exp2q_u10", xexp2q_u10, tlfloat_exp2o, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("exp2q_u10", xexp2q_u10, tlfloat_exp2o, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, 1.0, true) && success;
  success = check_q_q("exp2q_u10", xexp2q_u10, tlfloat_exp2o, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "expm1q_u10 ";
  success = check_q_q("expm1q_u10", xexpm1q_u10, tlfloat_expm1o, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("expm1q_u10", xexpm1q_u10, tlfloat_expm1o, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, 1.0, true) && success;
  success = check_q_q("expm1q_u10", xexpm1q_u10, tlfloat_expm1o, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "logq_u10 ";
  success = check_q_q("logq_u10", xlogq_u10, tlfloat_logo, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("logq_u10", xlogq_u10, tlfloat_logo, "1e-100" , "1e+100" , false, 5 * NTEST, 1ULL, 1.0, true) && success;
  success = check_q_q("logq_u10", xlogq_u10, tlfloat_logo, "1e-4000", "1e+4000", false, 5 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "log2q_u10 ";
  success = check_q_q("log2q_u10", xlog2q_u10, tlfloat_log2o, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("log2q_u10", xlog2q_u10, tlfloat_log2o, "1e-100" , "1e+100" , false, 5 * NTEST, 1ULL, 1.0, true) && success;
  success = check_q_q("log2q_u10", xlog2q_u10, tlfloat_log2o, "1e-4000", "1e+4000", false, 5 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "log10q_u10 ";
  success = check_q_q("log10q_u10", xlog10q_u10, tlfloat_log10o, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("log10q_u10", xlog10q_u10, tlfloat_log10o, "1e-100" , "1e+100" , false, 5 * NTEST, 1ULL, 1.0, true) && success;
  success = check_q_q("log10q_u10", xlog10q_u10, tlfloat_log10o, "1e-4000", "1e+4000", false, 5 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "log1pq_u10 ";
  success = check_q_q("log1pq_u10", xlog1pq_u10, tlfloat_log1po, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("log1pq_u10", xlog1pq_u10, tlfloat_log1po, log1pCheckVals,
                      sizeof(log1pCheckValsStr)/sizeof(log1pCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("log1pq_u10", xlog1pq_u10, tlfloat_log1po, "1e-100" , "1e+100" , false, 5 * NTEST, 1ULL, 1.0, true) && success;
  success = check_q_q("log1pq_u10", xlog1pq_u10, tlfloat_log1po, "1e-4000", "1e+4000", false, 5 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "powq_u10 ";
  success = check_q_q_q("powq_u10", xpowq_u10, tlfloat_powo,
                        stdCheckVals, sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]),
                        1.0, true) && success;
  success = check_q_q_q("powq_u10", xpowq_u10, tlfloat_powo, "1e-100" , "1e+100" , true, 5 * NTEST, 1ULL, 1.0, true) && success;
  success = check_q_q_q("powq_u10", xpowq_u10, tlfloat_powo, "1e-4000", "1e+4000", true, 5 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "sinhq_u10 ";
  success = check_q_q("sinhq_u10", xsinhq_u10, tlfloat_sinho, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("sinhq_u10", xsinhq_u10, tlfloat_sinho, "1e-15" , "20000" , true, 10 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "coshq_u10 ";
  success = check_q_q("coshq_u10", xcoshq_u10, tlfloat_cosho, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("coshq_u10", xcoshq_u10, tlfloat_cosho, "1e-15" , "20000" , true, 10 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "tanhq_u10 ";
  success = check_q_q("tanhq_u10", xtanhq_u10, tlfloat_tanho, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("tanhq_u10", xtanhq_u10, tlfloat_tanho, "1e-15" , "40" , true, 10 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "asinhq_u10 ";
  success = check_q_q("asinhq_u10", xasinhq_u10, tlfloat_asinho, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("asinhq_u10", xasinhq_u10, tlfloat_asinho, "1e-15" , "20000" , true, 10 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "acoshq_u10 ";
  success = check_q_q("acoshq_u10", xacoshq_u10, tlfloat_acosho, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("acoshq_u10", xacoshq_u10, tlfloat_acosho, "1" , "20000" , true, 10 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  cout << "atanhq_u10 ";
  success = check_q_q("atanhq_u10", xatanhq_u10, tlfloat_atanho, stdCheckVals,
                      sizeof(stdCheckValsStr)/sizeof(stdCheckValsStr[0]), 1.0, true) && success;
  success = check_q_q("atanhq_u10", xatanhq_u10, tlfloat_atanho, "1e-15" , "1" , true, 10 * NTEST, 1ULL, 1.0, true) && success;
  showULP(success);

  {
    printf("ldexp : ");

    static const int ldexpCheckVals[] = {
      -40000, -32770, -32769, -32768, -32767, -32766, -32765, -16386, -16385, -16384, -16383, -16382, -5, -4, -3, -2, -1, 0,
      +40000, +32770, +32769, +32768, +32767, +32766, +32765, +16386, +16385, +16384, +16383, +16382, +5, +4, +3, +2, +1
    };

    for(int i=0;i<int(sizeof(ldexpCheckVals)/sizeof(ldexpCheckVals[0]));i++) {
      for(int j=0;j<int(sizeof(stdCheckVals)/sizeof(stdCheckVals[0]));j++) {
        tlfloat_quad a0 = stdCheckVals[j];
        tlfloat_quad t = 0;
        {
          int idx = xrand() % VECTLENDP;
          VARGQUAD vq0;
          memrand(&vq0, SIZEOF_VARGQUAD);
          vq0 = xsetq(vq0, idx, (Sleef_quad)a0);
          int tmp[VECTLENDP*2];
          memrand(tmp, sizeof(tmp));
          tmp[idx] = ldexpCheckVals[i];
          vint vi1 = vloadu_vi_p(tmp);
          vq0 = xldexpq(vq0, vi1);
          t = (tlfloat_quad)xgetq(vq0, idx);
        }
        tlfloat_quad c = tlfloat_ldexpq(a0, ldexpCheckVals[i]);
        if (!((tlfloat_isnanq(t) && tlfloat_isnanq(c)) || tlfloat_quad(t) == c)) {
          tlfloat_printf("arg0 = %Qa (%.35Qg), arg1 = %d, t = %.35Qg, c = %.25Qg\n",
                         a0, a0, ldexpCheckVals[i], t, c);
          success = false;
          break;
        }
      }
    }

    printf("%s\n", success ? "OK" : "NG");
  }

  {
    printf("ilogb : ");

    for(int i=0;i<int(sizeof(stdCheckVals)/sizeof(stdCheckVals[0]));i++) {
      tlfloat_quad a0 = stdCheckVals[i];
      int c = tlfloat_ilogbq(a0);
      int t = 0;
      {
        int idx = xrand() % VECTLENDP;
        VARGQUAD vq0;
        memrand(&vq0, SIZEOF_VARGQUAD);
        vq0 = xsetq(vq0, idx, (Sleef_quad)a0);
        vint vi1 = xilogbq(vq0);
        int tmp[VECTLENDP*2];
        vstoreu_v_p_vi(tmp, vi1);
        t = tmp[idx];
      }
      if (t != c) {
        tlfloat_printf("arg0 = %Qa (%.35Qg), t = %d, c = %d\n",
                       a0, a0, t, c);
        success = false;
        break;
      }
    }

    printf("%s\n", success ? "OK" : "NG");
  }

  {
    printf("cast_from_doubleq : ");

    xsrand(1);
    for(int i=0;i<10 * NTEST;i++) {
      double d;
      switch(i) {
      case 0: d = +0.0; break;
      case 1: d = -0.0; break;
      case 2: d = +SLEEF_INFINITY; break;
      case 3: d = -SLEEF_INFINITY; break;
      case 4: d = SLEEF_NAN; break;
      default : memrand(&d, sizeof(d));
      }
      tlfloat_quad c = tlfloat_quad(d);
      tlfloat_quad t = 0;
      {
        int idx = xrand() % VECTLENDP;
        double s[VECTLENDP];
        memrand(s, sizeof(s));
        s[idx] = d;
        VARGQUAD q = xcast_from_doubleq(vloadu_vd_p(s));
        t = (tlfloat_quad)xgetq(q, idx);
      }
      if (!((tlfloat_isnanq(t) && tlfloat_isnanq(c)) || t == c)) {
        tlfloat_printf("arg0 = %a (%.16g), t = %Qa (%.35Qg), c = %Qa (%.35Qg)\n",
                       d, d, t, t, c, c);
        success = false;
        break;
      }
    }

    printf("%s\n", success ? "OK" : "NG");
  }

  {
    printf("cast_to_doubleq : ");

    xsrand(1);
    Sleef_quad min = (Sleef_quad)tlfloat_strtoq("0", nullptr), max = (Sleef_quad)tlfloat_strtoq("1e+20", nullptr);
    for(int i=0;i<10 * NTEST;i++) {
      Sleef_quad a;
      if (i < int(sizeof(stdCheckVals)/sizeof(stdCheckVals[0]))) {
        a = (Sleef_quad)stdCheckVals[i];
      } else {
        a = rndf128(min, max, true);
      }
      double t = 0, c = (double)(tlfloat_quad)a;
      {
        int idx = xrand() % VECTLENDP;
        VARGQUAD v0;
        memrand(&v0, SIZEOF_VARGQUAD);
        v0 = xsetq(v0, idx, a);
        vdouble vd = xcast_to_doubleq(v0);
        double s[VECTLENDP];
        vstoreu_v_p_vd(s, vd);
        t = s[idx];
      }
      double u = countULP<tlfloat_quad>(t, c, DBL_MANT_DIG,
                                        SLEEF_DBL_DENORM_MIN, DBL_MAX, true);
      if (!((tlfloat_isnan(t) && tlfloat_isnan(c)) || (fabs(t) <= DBL_MIN && u <= 1.0) || t == c)) {
        tlfloat_printf("arg0 = %Qa (%.35Qg), t = %a (%.16g), c = %a (%.16g), u = %g\n",
                       a, a, t, t, c, c, u);
        success = false;
        break;
      }
    }

    printf("%s\n", success ? "OK" : "NG");
  }

  {
    printf("cast_from_int64q : ");

    xsrand(1);
    for(int i=0;i<10 * NTEST;i++) {
      int64_t d;
      switch(i) {
      case 0: d = 0; break;
      case 1: d = +0x7fffffffffffffffL; break;
      case 2: d = -0x8000000000000000L; break;
      default : memrand(&d, sizeof(d));
      }
      tlfloat_quad c = tlfloat_quad(d);
      tlfloat_quad t = 0;
      {
        int idx = xrand() % VECTLENDP;
        int64_t s[VECTLENDP];
        memrand(s, sizeof(s));
        s[idx] = d;
        VARGQUAD q = xcast_from_int64q(vreinterpret_vi64_vm(vreinterpret_vm_vd(vloadu_vd_p((double *)s))));
        t = (tlfloat_quad)xgetq(q, idx);
      }
      if (t != c) {
        tlfloat_printf("arg0 = %016llx (%lld), t = %Qa (%.35Qg), c = %Qa (%.35Qg)\n",
                       (long long)d, (long long)d, t, t, c, c);
        success = false;
        break;
      }
    }

    printf("%s\n", success ? "OK" : "NG");
  }

  {
    printf("cast_to_int64q : ");

    xsrand(1);
    Sleef_quad min = (Sleef_quad)tlfloat_strtoq("0", nullptr), max = (Sleef_quad)tlfloat_strtoq("1e+20", nullptr);
    for(int i=0;i<10 * NTEST;i++) {
      Sleef_quad a;
      if (i < int(sizeof(stdCheckVals)/sizeof(stdCheckVals[0])-1)) {
        a = (Sleef_quad)stdCheckVals[i];
      } else {
        a = rndf128(min, max, true);
      }
      int64_t t = 0, c = (int64_t)(tlfloat_quad)a;
      {
        int idx = xrand() % VECTLENDP;
        VARGQUAD v0;
        memrand(&v0, SIZEOF_VARGQUAD);
        v0 = xsetq(v0, idx, a);
        int64_t s[VECTLENDP];
        vstoreu_v_p_vd((double *)s, vreinterpret_vd_vm(vreinterpret_vm_vi64(xcast_to_int64q(v0))));
        t = s[idx];
      }
      if (-ldexp(1, 63) < a && a < ldexp(1, 63) && t != c) {
        tlfloat_printf("arg0 = %Qa (%.35Qg), t = %016llx (%lld), c = %016llx (%lld)\n",
                       a, a, (long long)t, (long long)t, (long long)c, (long long)c);
        success = false;
        break;
      }
    }

    printf("%s\n", success ? "OK" : "NG");
  }

  {
    printf("cast_from_uint64q : ");

    xsrand(1);
    for(int i=0;i<10 * NTEST;i++) {
      uint64_t d;
      switch(i) {
      case 0: d = 0; break;
      case 1: d = +0x7fffffffffffffffL; break;
      case 2: d = -0x8000000000000000L; break;
      default : memrand(&d, sizeof(d));
      }
      tlfloat_quad c = tlfloat_quad(d);
      tlfloat_quad t = 0;
      {
        int idx = xrand() % VECTLENDP;
        uint64_t s[VECTLENDP];
        memrand(s, sizeof(s));
        s[idx] = d;
        VARGQUAD q = xcast_from_uint64q(vreinterpret_vu64_vm(vreinterpret_vm_vd(vloadu_vd_p((double *)s))));
        t = (tlfloat_quad)xgetq(q, idx);
      }
      if (t != c) {
        tlfloat_printf("arg0 = %016llx (%lld), t = %Qa (%.35Qg), c = %Qa (%.35Qg)\n",
                       (long long)d, (long long)d, t, t, c, c);
        success = false;
        break;
      }
    }

    printf("%s\n", success ? "OK" : "NG");
  }

  {
    printf("cast_to_uint64q : ");

    xsrand(1);
    Sleef_quad min = (Sleef_quad)tlfloat_strtoq("0", nullptr), max = (Sleef_quad)tlfloat_strtoq("1e+20", nullptr);
    for(int i=0;i<10 * NTEST;i++) {
      Sleef_quad a;
      if (i < int(sizeof(stdCheckVals)/sizeof(stdCheckVals[0])-1)) {
        a = (Sleef_quad)stdCheckVals[i];
      } else {
        a = rndf128(min, max, true);
      }
      uint64_t t = 0, c = (uint64_t)(tlfloat_quad)a;
      {
        int idx = xrand() % VECTLENDP;
        VARGQUAD v0;
        memrand(&v0, SIZEOF_VARGQUAD);
        v0 = xsetq(v0, idx, a);
        uint64_t s[VECTLENDP];
        vstoreu_v_p_vd((double *)s, vreinterpret_vd_vm(vreinterpret_vm_vu64(xcast_to_uint64q(v0))));
        t = s[idx];
      }
      if (0 <= a && a < ldexp(1, 64) && t != c) {
        tlfloat_printf("arg0 = %Qa (%.35Qg), t = %016llx (%lld), c = %016llx (%lld)\n",
                       a, a, (long long)t, (long long)t, (long long)c, (long long)c);
        success = false;
        break;
      }
    }

    printf("%s\n", success ? "OK" : "NG");
  }

  //

  if (success) {
    cout << "OK" << endl;
  } else {
    cout << "NG" << endl;
  }

  return success ? 0 : -1;
}
