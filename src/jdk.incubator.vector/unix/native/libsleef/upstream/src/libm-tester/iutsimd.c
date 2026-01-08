//   Copyright Naoki Shibata and contributors 2010 - 2023.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <time.h>
#include <inttypes.h>
#include <assert.h>

#include <math.h>

#if defined(_MSC_VER)
#define STDIN_FILENO 0
#else
#include <unistd.h>
#include <sys/types.h>
#endif

#include "quaddef.h"
#include "misc.h"

#if !defined(USE_INLINE_HEADER)
#include "sleef.h"
#else // #if !defined(USE_INLINE_HEADER)
#include <stddef.h>
#include <stdint.h>
#include <float.h>
#include <limits.h>

#if defined(__AVX2__) || defined(__aarch64__) || defined(__arm__) || defined(__powerpc64__)
#ifndef FP_FAST_FMA
#define FP_FAST_FMA
#endif
#endif

#if defined(_MSC_VER) && !defined(__STDC__)
#define __STDC__ 1
#endif

#if (defined(__GNUC__) || defined(__CLANG__)) && (defined(__i386__) || defined(__x86_64__))
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

#ifndef ENABLE_PUREC_SCALAR
#include "sleefinline_purec_scalar.h"
#endif

#endif // #if !defined(USE_INLINE_HEADER)

#include "testerutil.h"

#define DORENAME

#ifdef ENABLE_SSE2
#include "renamesse2.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 2
#include "helpersse2.h"
typedef Sleef___m128d_2 vdouble2;
typedef Sleef___m128_2 vfloat2;
#endif
#endif

#ifdef ENABLE_SSE4
#include "renamesse4.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 4
#include "helpersse2.h"
typedef Sleef___m128d_2 vdouble2;
typedef Sleef___m128_2 vfloat2;
#endif
#endif

#ifdef ENABLE_AVX
#include "renameavx.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperavx.h"
typedef Sleef___m256d_2 vdouble2;
typedef Sleef___m256_2 vfloat2;
#endif
#endif

#ifdef ENABLE_FMA4
#include "renamefma4.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 4
#include "helperavx.h"
typedef Sleef___m256d_2 vdouble2;
typedef Sleef___m256_2 vfloat2;
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

#ifdef ENABLE_AVX512FNOFMA
#include "renameavx512fnofma.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 2
#include "helperavx512f.h"
typedef Sleef___m512d_2 vdouble2;
typedef Sleef___m512_2 vfloat2;
#endif
#endif

#ifdef ENABLE_VECEXT
#define CONFIG 1
#include "helpervecext.h"
#include "norename.h"
#endif

#ifdef ENABLE_PUREC
#define CONFIG 1
#include "helperpurec.h"
#include "norename.h"
#endif

#ifdef ENABLE_NEON32
#include "renameneon32.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 1
#include "helperneon32.h"
typedef Sleef_float32x4_t_2 vfloat2;
#endif
#endif

#ifdef ENABLE_NEON32VFPV4
#include "renameneon32vfpv4.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 4
#include "helperneon32.h"
typedef Sleef_float32x4_t_2 vfloat2;
#endif
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

#ifdef ENABLE_ADVSIMDNOFMA
#include "renameadvsimdnofma.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 2
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

#ifdef ENABLE_SVENOFMA
#include "renamesvenofma.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 2
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

#ifdef ENABLE_VSXNOFMA
#include "renamevsxnofma.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 2
#include "helperpower_128.h"
#include "renamevsxnofma.h"
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

#ifdef ENABLE_VSX3NOFMA
#include "renamevsx3nofma.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 4
#include "helperpower_128.h"
#include "renamevsx3nofma.h"
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

#ifdef ENABLE_VXENOFMA
#include "renamevxenofma.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 141
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

#ifdef ENABLE_VXE2NOFMA
#include "renamevxe2nofma.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 151
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

#ifdef ENABLE_RVVM1NOFMA
#include "renamervvm1nofma.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 2
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

#ifdef ENABLE_RVVM2NOFMA
#include "renamervvm2nofma.h"
#if !defined(USE_INLINE_HEADER)
#define CONFIG 2
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

int check_feature(double d, float f) {
#ifdef ENABLE_DP
  {
    double s[VECTLENDP];
    int i;
    for(i=0;i<VECTLENDP;i++) {
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
    for(i=0;i<VECTLENSP;i++) {
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

#if defined(ENABLE_DP) && !(defined(ENABLE_SVE) || defined(ENABLE_SVENOFMA) || defined(ENABLE_RVVM1) || defined(ENABLE_RVVM1NOFMA) || defined(ENABLE_RVVM2) || defined(ENABLE_RVVM2NOFMA) || defined(USE_INLINE_HEADER))
static vdouble vd2getx_vd_vd2(vdouble2 v) { return v.x; }
static vdouble vd2gety_vd_vd2(vdouble2 v) { return v.y; }
#endif

#if defined(ENABLE_SP) && !(defined(ENABLE_SVE) || defined(ENABLE_SVENOFMA) || defined(ENABLE_RVVM1) || defined(ENABLE_RVVM1NOFMA) || defined(ENABLE_RVVM2) || defined(ENABLE_RVVM2NOFMA) || defined(USE_INLINE_HEADER))
static vfloat vf2getx_vf_vf2(vfloat2 v) { return v.x; }
static vfloat vf2gety_vf_vf2(vfloat2 v) { return v.y; }
#endif

//

#define func_d_d(funcStr, funcName) {                           \
    while (startsWith(buf, funcStr " ")) {                      \
      uint64_t u;                                               \
      sscanf(buf, funcStr " %" PRIx64, &u);                     \
      double s[VECTLENDP];                                      \
      memrand(s, sizeof(s));                                    \
      int idx = xrand() & (VECTLENDP-1);                        \
      s[idx] = u2d(u);                                          \
      vdouble a = vloadu_vd_p(s);                               \
      a = funcName(a);                                          \
      vstoreu_v_p_vd(s, a);                                     \
      u = d2u(s[idx]);                                          \
      printf("%" PRIx64 "\n", u);                               \
      fflush(stdout);                                           \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;          \
    }                                                           \
  }

#define func_d2_d(funcStr, funcName) {                                  \
    while (startsWith(buf, funcStr " ")) {                              \
      uint64_t u;                                                       \
      sscanf(buf, funcStr " %" PRIx64, &u);                             \
      double s[VECTLENDP], t[VECTLENDP];                                \
      memrand(s, sizeof(s));                                            \
      memrand(t, sizeof(t));                                            \
      int idx = xrand() & (VECTLENDP-1);                                \
      s[idx] = u2d(u);                                                  \
      vdouble2 v;                                                       \
      vdouble a = vloadu_vd_p(s);                                       \
      v = funcName(a);                                                  \
      vstoreu_v_p_vd(s, vd2getx_vd_vd2(v));                             \
      vstoreu_v_p_vd(t, vd2gety_vd_vd2(v));                             \
      Sleef_double2 d2;                                                 \
      d2.x = s[idx];                                                    \
      d2.y = t[idx];                                                    \
      printf("%" PRIx64 " %" PRIx64 "\n", d2u(d2.x), d2u(d2.y));        \
      fflush(stdout);                                                   \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;                  \
    }                                                                   \
  }

#define func_d_d_d(funcStr, funcName) {                         \
    while (startsWith(buf, funcStr " ")) {                      \
      uint64_t u, v;                                            \
      sscanf(buf, funcStr " %" PRIx64 " %" PRIx64, &u, &v);     \
      double s[VECTLENDP], t[VECTLENDP];                        \
      memrand(s, sizeof(s));                                    \
      memrand(t, sizeof(t));                                    \
      int idx = xrand() & (VECTLENDP-1);                        \
      s[idx] = u2d(u);                                          \
      t[idx] = u2d(v);                                          \
      vdouble a, b;                                             \
      a = vloadu_vd_p(s);                                       \
      b = vloadu_vd_p(t);                                       \
      a = funcName(a, b);                                       \
      vstoreu_v_p_vd(s, a);                                     \
      u = d2u(s[idx]);                                          \
      printf("%" PRIx64 "\n", u);                               \
      fflush(stdout);                                           \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;          \
    }                                                           \
  }

#define func_d_d_i(funcStr, funcName) {                                 \
    while (startsWith(buf, funcStr " ")) {                              \
      uint64_t u, v;                                                    \
      sscanf(buf, funcStr " %" PRIx64 " %" PRIx64, &u, &v);             \
      double s[VECTLENDP];                                              \
      int t[VECTLENDP*2];                                               \
      memrand(s, sizeof(s));                                            \
      memrand(t, sizeof(t));                                            \
      int idx = xrand() & (VECTLENDP-1);                                \
      s[idx] = u2d(u);                                                  \
      t[idx] = (int)u2d(v);                                             \
      vstoreu_v_p_vd(s, funcName(vloadu_vd_p(s), vloadu_vi_p(t)));      \
      u = d2u(s[idx]);                                                  \
      printf("%" PRIx64 "\n", u);                                       \
      fflush(stdout);                                                   \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;                  \
    }                                                                   \
  }

#define func_i_d(funcStr, funcName) {                           \
    while (startsWith(buf, funcStr " ")) {                      \
      uint64_t u;                                               \
      int i;                                                    \
      sscanf(buf, funcStr " %" PRIx64, &u);                     \
      double s[VECTLENDP];                                      \
      int t[VECTLENDP*2];                                       \
      memrand(s, sizeof(s));                                    \
      memrand(t, sizeof(t));                                    \
      int idx = xrand() & (VECTLENDP-1);                        \
      s[idx] = u2d(u);                                          \
      vdouble a = vloadu_vd_p(s);                               \
      vint vi = funcName(a);                                    \
      vstoreu_v_p_vi(t, vi);                                    \
      i = t[idx];                                               \
      printf("%d\n", i);                                        \
      fflush(stdout);                                           \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;          \
    }                                                           \
  }

//

#define func_f_f(funcStr, funcName) {                   \
    while (startsWith(buf, funcStr " ")) {              \
      uint32_t u;                                       \
      sscanf(buf, funcStr " %x", &u);                   \
      float s[VECTLENSP];                               \
      memrand(s, sizeof(s));                            \
      int idx = xrand() & (VECTLENSP-1);                \
      s[idx] = u2f(u);                                  \
      vfloat a = vloadu_vf_p(s);                        \
      a = funcName(a);                                  \
      vstoreu_v_p_vf(s, a);                             \
      u = f2u(s[idx]);                                  \
      printf("%x\n", u);                                \
      fflush(stdout);                                   \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;  \
    }                                                   \
  }

#define func_f2_f(funcStr, funcName) {                  \
    while (startsWith(buf, funcStr " ")) {              \
      uint32_t u;                                       \
      sscanf(buf, funcStr " %x", &u);                   \
      float s[VECTLENSP], t[VECTLENSP];                 \
      memrand(s, sizeof(s));                            \
      memrand(t, sizeof(t));                            \
      int idx = xrand() & (VECTLENSP-1);                \
      s[idx] = u2f(u);                                  \
      vfloat2 v;                                        \
      vfloat a = vloadu_vf_p(s);                        \
      v = funcName(a);                                  \
      vstoreu_v_p_vf(s, vf2getx_vf_vf2(v));             \
      vstoreu_v_p_vf(t, vf2gety_vf_vf2(v));             \
      Sleef_float2 d2;                                  \
      d2.x = s[idx];                                    \
      d2.y = t[idx];                                    \
      printf("%x %x\n", f2u(d2.x), f2u(d2.y));          \
      fflush(stdout);                                   \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;  \
    }                                                   \
  }

#define func_f_f_f(funcStr, funcName) {                 \
    while (startsWith(buf, funcStr " ")) {              \
      uint32_t u, v;                                    \
      sscanf(buf, funcStr " %x %x", &u, &v);            \
      float s[VECTLENSP], t[VECTLENSP];                 \
      memrand(s, sizeof(s));                            \
      memrand(t, sizeof(t));                            \
      int idx = xrand() & (VECTLENSP-1);                \
      s[idx] = u2f(u);                                  \
      t[idx] = u2f(v);                                  \
      vfloat a, b;                                      \
      a = vloadu_vf_p(s);                               \
      b = vloadu_vf_p(t);                               \
      a = funcName(a, b);                               \
      vstoreu_v_p_vf(s, a);                             \
      u = f2u(s[idx]);                                  \
      printf("%x\n", u);                                \
      fflush(stdout);                                   \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;  \
    }                                                   \
  }

//

#define BUFSIZE 1024

int main2(int argc, char **argv) {
  xsrand(time(NULL));

  {
    int k = 0;
#ifdef ENABLE_DP
    k += 1;
#endif
#ifdef ENABLE_SP
    k += 2;
#endif
#if defined(ENABLE_NEON32) || defined(ENABLE_NEON32VFPV4)
    k += 4; // flush to zero
#elif defined(ENABLE_VECEXT)
    if (vcast_f_vf(xpowf(vcast_vf_f(0.5f), vcast_vf_f(140))) == 0) k += 4;
#endif
#if defined(DETERMINISTIC)
    k += 8;
#endif

    printf("%d\n", k);
    fflush(stdout);
  }

#if !defined(USE_INLINE_HEADER)
  fprintf(stderr, "IUT : %s\n", (const char *)xgetPtrf(0));
#endif
  fflush(stderr);

  char buf[BUFSIZE];
  fgets(buf, BUFSIZE-1, stdin);

  while(!feof(stdin)) {
#ifdef ENABLE_DP
    func_d_d("sin", xsin);
    func_d_d("cos", xcos);
    func_d_d("tan", xtan);
    func_d_d("asin", xasin);
    func_d_d("acos", xacos);
    func_d_d("atan", xatan);
    func_d_d("log", xlog);
    func_d_d("exp", xexp);

#ifndef DETERMINISTIC
    func_d_d("sqrt", xsqrt);
    func_d_d("sqrt_u05", xsqrt_u05);
    func_d_d("sqrt_u35", xsqrt_u35);
#endif
    func_d_d("cbrt", xcbrt);
    func_d_d("cbrt_u1", xcbrt_u1);

    func_d_d("sinh", xsinh);
    func_d_d("cosh", xcosh);
    func_d_d("tanh", xtanh);
    func_d_d("sinh_u35", xsinh_u35);
    func_d_d("cosh_u35", xcosh_u35);
    func_d_d("tanh_u35", xtanh_u35);
    func_d_d("asinh", xasinh);
    func_d_d("acosh", xacosh);
    func_d_d("atanh", xatanh);

    func_d_d("sin_u1", xsin_u1);
    func_d_d("cos_u1", xcos_u1);
    func_d_d("tan_u1", xtan_u1);
    func_d_d("sinpi_u05", xsinpi_u05);
    func_d_d("cospi_u05", xcospi_u05);
    func_d_d("asin_u1", xasin_u1);
    func_d_d("acos_u1", xacos_u1);
    func_d_d("atan_u1", xatan_u1);
    func_d_d("log_u1", xlog_u1);

    func_d_d("exp2", xexp2);
    func_d_d("exp10", xexp10);
    func_d_d("exp2_u35", xexp2_u35);
    func_d_d("exp10_u35", xexp10_u35);
    func_d_d("expm1", xexpm1);
    func_d_d("log10", xlog10);
    func_d_d("log2", xlog2);
    func_d_d("log2_u35", xlog2_u35);
    func_d_d("log1p", xlog1p);

    func_d2_d("sincos", xsincos);
    func_d2_d("sincos_u1", xsincos_u1);
    func_d2_d("sincospi_u35", xsincospi_u35);
    func_d2_d("sincospi_u05", xsincospi_u05);

    func_d_d_d("pow", xpow);
    func_d_d_d("atan2", xatan2);
    func_d_d_d("atan2_u1", xatan2_u1);

    func_d_d_i("ldexp", xldexp);

    func_i_d("ilogb", xilogb);

    func_d_d("fabs", xfabs);
    func_d_d("trunc", xtrunc);
    func_d_d("floor", xfloor);
    func_d_d("ceil", xceil);
    func_d_d("round", xround);
    func_d_d("rint", xrint);
    func_d_d("frfrexp", xfrfrexp);
    func_i_d("expfrexp", xexpfrexp);

    func_d_d_d("hypot_u05", xhypot_u05);
    func_d_d_d("hypot_u35", xhypot_u35);
    func_d_d_d("copysign", xcopysign);
    func_d_d_d("fmax", xfmax);
    func_d_d_d("fmin", xfmin);
    func_d_d_d("fdim", xfdim);
    func_d_d_d("nextafter", xnextafter);
    func_d_d_d("fmod", xfmod);
    func_d_d_d("remainder", xremainder);

    func_d2_d("modf", xmodf);

    func_d_d("tgamma_u1", xtgamma_u1);
    func_d_d("lgamma_u1", xlgamma_u1);
    func_d_d("erf_u1", xerf_u1);
    func_d_d("erfc_u15", xerfc_u15);
#endif

#ifdef ENABLE_SP
    func_f_f("sinf", xsinf);
    func_f_f("cosf", xcosf);
    func_f_f("tanf", xtanf);
    func_f_f("asinf", xasinf);
    func_f_f("acosf", xacosf);
    func_f_f("atanf", xatanf);
    func_f_f("logf", xlogf);
    func_f_f("expf", xexpf);

#ifndef DETERMINISTIC
    func_f_f("sqrtf", xsqrtf);
    func_f_f("sqrtf_u05", xsqrtf_u05);
    func_f_f("sqrtf_u35", xsqrtf_u35);
#endif
    func_f_f("cbrtf", xcbrtf);
    func_f_f("cbrtf_u1", xcbrtf_u1);

    func_f_f("sinhf", xsinhf);
    func_f_f("coshf", xcoshf);
    func_f_f("tanhf", xtanhf);
    func_f_f("sinhf_u35", xsinhf_u35);
    func_f_f("coshf_u35", xcoshf_u35);
    func_f_f("tanhf_u35", xtanhf_u35);
    func_f_f("asinhf", xasinhf);
    func_f_f("acoshf", xacoshf);
    func_f_f("atanhf", xatanhf);

    func_f_f("sinf_u1", xsinf_u1);
    func_f_f("cosf_u1", xcosf_u1);
    func_f_f("tanf_u1", xtanf_u1);
    func_f_f("sinpif_u05", xsinpif_u05);
    func_f_f("cospif_u05", xcospif_u05);
    func_f_f("asinf_u1", xasinf_u1);
    func_f_f("acosf_u1", xacosf_u1);
    func_f_f("atanf_u1", xatanf_u1);
    func_f_f("logf_u1", xlogf_u1);

    func_f_f("exp2f", xexp2f);
    func_f_f("exp10f", xexp10f);
    func_f_f("exp2f_u35", xexp2f_u35);
    func_f_f("exp10f_u35", xexp10f_u35);
    func_f_f("expm1f", xexpm1f);
    func_f_f("log10f", xlog10f);
    func_f_f("log2f", xlog2f);
    func_f_f("log2f_u35", xlog2f_u35);
    func_f_f("log1pf", xlog1pf);

    func_f2_f("sincosf", xsincosf);
    func_f2_f("sincosf_u1", xsincosf_u1);
    func_f2_f("sincospif_u35", xsincospif_u35);
    func_f2_f("sincospif_u05", xsincospif_u05);

    func_f_f_f("powf", xpowf);
    func_f_f_f("atan2f", xatan2f);
    func_f_f_f("atan2f_u1", xatan2f_u1);

    func_f_f("fabsf", xfabsf);
    func_f_f("truncf", xtruncf);
    func_f_f("floorf", xfloorf);
    func_f_f("ceilf", xceilf);
    func_f_f("roundf", xroundf);
    func_f_f("rintf", xrintf);
    func_f_f("frfrexpf", xfrfrexpf);

    func_f_f_f("hypotf_u05", xhypotf_u05);
    func_f_f_f("hypotf_u35", xhypotf_u35);
    func_f_f_f("copysignf", xcopysignf);
    func_f_f_f("fmaxf", xfmaxf);
    func_f_f_f("fminf", xfminf);
    func_f_f_f("fdimf", xfdimf);
    func_f_f_f("nextafterf", xnextafterf);
    func_f_f_f("fmodf", xfmodf);
    func_f_f_f("remainderf", xremainderf);

    func_f2_f("modff", xmodff);

    func_f_f("tgammaf_u1", xtgammaf_u1);
    func_f_f("lgammaf_u1", xlgammaf_u1);
    func_f_f("erff_u1", xerff_u1);
    func_f_f("erfcf_u15", xerfcf_u15);

    func_f_f("fastsinf_u3500", xfastsinf_u3500);
    func_f_f("fastcosf_u3500", xfastcosf_u3500);
    func_f_f_f("fastpowf_u3500", xfastpowf_u3500);
#endif
  }

  return 0;
}
