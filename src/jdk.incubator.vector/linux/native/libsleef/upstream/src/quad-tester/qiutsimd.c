//   Copyright Naoki Shibata and contributors 2010 - 2021.
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
#include "sleefquad.h"
#else // #if !defined(USE_INLINE_HEADER)
#include <stddef.h>
#include <stdint.h>
#include <float.h>
#include <limits.h>
#include <stdarg.h>
#include <ctype.h>

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
#include "sleefquadinline_purec_scalar.h"
#endif

#endif // #if !defined(USE_INLINE_HEADER)

#include "qtesterutil.h"

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

int check_feature(double d, float f) {
  double s[VECTLENDP];
  for(int i=0;i<VECTLENDP;i++) s[i] = d;
  VARGQUAD a = xcast_from_doubleq(vloadu_vd_p(s));
  a = xpowq_u10(a, a);
  vint vi = xicmpeqq(a, xsplatq(sleef_q(+0x1000000000000LL, 0x0000000000000000ULL, 0)));
  int t[VECTLENDP*2];
  memset(t, 0, sizeof(t));
  vstoreu_v_p_vi(t, vi);
  return t[0];
}

//

#if defined(__BYTE_ORDER__) && (__BYTE_ORDER__ == __ORDER_BIG_ENDIAN__)
typedef union {
  Sleef_quad q;
  struct {
    uint64_t h, l;
  };
} cnv128;
#else
typedef union {
  Sleef_quad q;
  struct {
    uint64_t l, h;
  };
} cnv128;
#endif

#define BUFSIZE 1024

#define func_q_q(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      int lane = xrand() % VECTLENDP;					\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      VARGQUAD a0;							\
      memrand(&a0, SIZEOF_VARGQUAD);					\
      a0 = xsetq(a0, lane, c0.q);					\
      a0 = funcName(a0);						\
      c0.q = xgetq(a0, lane);						\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);			\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_q_q_q(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      int lane = xrand() % VECTLENDP;					\
      cnv128 c0, c1;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64 " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l, &c1.h, &c1.l); \
      VARGQUAD a0, a1;							\
      memrand(&a0, SIZEOF_VARGQUAD);					\
      memrand(&a1, SIZEOF_VARGQUAD);					\
      a0 = xsetq(a0, lane, c0.q);					\
      a1 = xsetq(a1, lane, c1.q);					\
      a0 = funcName(a0, a1);						\
      c0.q = xgetq(a0, lane);						\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);			\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_q_q_q_q(funcStr, funcName) {				\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      int lane = xrand() % VECTLENDP;					\
      cnv128 c0, c1, c2;						\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64 " %" PRIx64 ":%" PRIx64 " %" PRIx64 ":%" PRIx64, \
	     &c0.h, &c0.l, &c1.h, &c1.l, &c2.h, &c2.l);			\
      VARGQUAD a0, a1, a2;						\
      memrand(&a0, SIZEOF_VARGQUAD);					\
      memrand(&a1, SIZEOF_VARGQUAD);					\
      memrand(&a2, SIZEOF_VARGQUAD);					\
      a0 = xsetq(a0, lane, c0.q);					\
      a1 = xsetq(a1, lane, c1.q);					\
      a2 = xsetq(a2, lane, c2.q);					\
      a0 = funcName(a0, a1, a2);					\
      c0.q = xgetq(a0, lane);						\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);			\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_i_q(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      int lane = xrand() % VECTLENDP;					\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      VARGQUAD a0;							\
      memrand(&a0, SIZEOF_VARGQUAD);					\
      a0 = xsetq(a0, lane, c0.q);					\
      vint vi = funcName(a0);						\
      int t[VECTLENDP*2];						\
      vstoreu_v_p_vi(t, vi);						\
      printf("%d\n", t[lane]);						\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_i_q_q(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      int lane = xrand() % VECTLENDP;					\
      cnv128 c0, c1;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64 " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l, &c1.h, &c1.l); \
      VARGQUAD a0, a1;							\
      memrand(&a0, SIZEOF_VARGQUAD);					\
      memrand(&a1, SIZEOF_VARGQUAD);					\
      a0 = xsetq(a0, lane, c0.q);					\
      a1 = xsetq(a1, lane, c1.q);					\
      vint vi = funcName(a0, a1);					\
      int t[VECTLENDP*2];						\
      vstoreu_v_p_vi(t, vi);						\
      printf("%d\n", t[lane]);						\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_q_q_i(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      int lane = xrand() % VECTLENDP;					\
      cnv128 c0;							\
      int k;								\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64 " %d", &c0.h, &c0.l, &k); \
      VARGQUAD a0;							\
      memrand(&a0, SIZEOF_VARGQUAD);					\
      a0 = xsetq(a0, lane, c0.q);					\
      int t[VECTLENDP*2];						\
      memrand(t, sizeof(t));						\
      t[lane] = k;							\
      a0 = funcName(a0, vloadu_vi_p(t));				\
      c0.q = xgetq(a0, lane);						\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);			\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_d_q(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      int lane = xrand() % VECTLENDP;					\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      VARGQUAD a0;							\
      memrand(&a0, SIZEOF_VARGQUAD);					\
      a0 = xsetq(a0, lane, c0.q);					\
      double d[VECTLENDP];						\
      vstoreu_v_p_vd(d, funcName(a0));					\
      printf("%" PRIx64 "\n", d2u(d[lane]));				\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_q_d(funcStr, funcName) {			\
    while (startsWith(buf, funcStr " ")) {		\
      sentinel = 0;					\
      int lane = xrand() % VECTLENDP;			\
      uint64_t u;					\
      sscanf(buf, funcStr " %" PRIx64, &u);		\
      double s[VECTLENDP];				\
      memrand(s, sizeof(s));				\
      s[lane] = u2d(u);					\
      VARGQUAD a0 = funcName(vloadu_vd_p(s));		\
      cnv128 c0;					\
      c0.q = xgetq(a0, lane);				\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);	\
      fflush(stdout);					\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;	\
    }							\
  }

#define func_i64_q(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      int lane = xrand() % VECTLENDP;					\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      VARGQUAD a0;							\
      memrand(&a0, SIZEOF_VARGQUAD);					\
      a0 = xsetq(a0, lane, c0.q);					\
      double d[VECTLENDP];						\
      vstoreu_v_p_vd(d, vreinterpret_vd_vm(vreinterpret_vm_vi64(funcName(a0)))); \
      printf("%" PRIx64 "\n", d2u(d[lane]));				\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_q_i64(funcStr, funcName) {			\
    while (startsWith(buf, funcStr " ")) {		\
      sentinel = 0;					\
      int lane = xrand() % VECTLENDP;			\
      uint64_t u;					\
      sscanf(buf, funcStr " %" PRIx64, &u);		\
      double s[VECTLENDP];				\
      memrand(s, sizeof(s));				\
      s[lane] = u2d(u);					\
      VARGQUAD a0 = funcName(vreinterpret_vi64_vm(vreinterpret_vm_vd(vloadu_vd_p(s))));	\
      cnv128 c0;					\
      c0.q = xgetq(a0, lane);				\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);	\
      fflush(stdout);					\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;	\
    }							\
  }

#define func_u64_q(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      int lane = xrand() % VECTLENDP;					\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      VARGQUAD a0;							\
      memrand(&a0, SIZEOF_VARGQUAD);					\
      a0 = xsetq(a0, lane, c0.q);					\
      double d[VECTLENDP];						\
      vstoreu_v_p_vd(d, vreinterpret_vd_vm(vreinterpret_vm_vu64(funcName(a0)))); \
      printf("%" PRIx64 "\n", d2u(d[lane]));				\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_q_u64(funcStr, funcName) {			\
    while (startsWith(buf, funcStr " ")) {		\
      sentinel = 0;					\
      int lane = xrand() % VECTLENDP;			\
      uint64_t u;					\
      sscanf(buf, funcStr " %" PRIx64, &u);		\
      double s[VECTLENDP];				\
      memrand(s, sizeof(s));				\
      s[lane] = u2d(u);					\
      VARGQUAD a0 = funcName(vreinterpret_vu64_vm(vreinterpret_vm_vd(vloadu_vd_p(s))));	\
      cnv128 c0;					\
      c0.q = xgetq(a0, lane);				\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);	\
      fflush(stdout);					\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;	\
    }							\
  }

#define func_q_q_pi(funcStr, funcName) {				\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      int lane = xrand() % VECTLENDP;					\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      VARGQUAD a0;							\
      memrand(&a0, SIZEOF_VARGQUAD);					\
      a0 = xsetq(a0, lane, c0.q);					\
      vint vi;								\
      a0 = funcName(a0, &vi);						\
      c0.q = xgetq(a0, lane);						\
      int t[VECTLENDP*2];						\
      vstoreu_v_p_vi(t, vi);						\
      printf("%" PRIx64 ":%" PRIx64 " %d\n", c0.h, c0.l, t[lane]);	\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_q_q_pq(funcStr, funcName) {				\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      int lane = xrand() % VECTLENDP;					\
      cnv128 c0, c1;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      VARGQUAD a0, a1;							\
      memrand(&a0, SIZEOF_VARGQUAD);					\
      a0 = xsetq(a0, lane, c0.q);					\
      a0 = funcName(a0, &a1);						\
      c0.q = xgetq(a0, lane);						\
      c1.q = xgetq(a1, lane);						\
      printf("%" PRIx64 ":%" PRIx64 " %" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l, c1.h, c1.l); \
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_strtoq(funcStr) {						\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      char s[64];							\
      sscanf(buf, funcStr " %63s", s);					\
      VARGQUAD a0;							\
      a0 = Sleef_strtoq(s, NULL);					\
      cnv128 c0;							\
      c0.q = xgetq(a0, 0);						\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);			\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#if !(defined(ENABLEFLOAT128) && defined(__clang__))
#define func_snprintf_40Qg(funcStr) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      VARGQUAD a0;							\
      memset(&a0, 0, sizeof(a0));					\
      a0 = xsetq(a0, 0, c0.q);						\
      char s[64];							\
      Sleef_snprintf(s, 63, "%.40Qg", a0);				\
      printf("%s\n", s);						\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_snprintf_Qa(funcStr) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      VARGQUAD a0;							\
      memset(&a0, 0, sizeof(a0));					\
      a0 = xsetq(a0, 0, c0.q);						\
      char s[64];							\
      Sleef_snprintf(s, 63, "%Qa", a0);					\
      printf("%s\n", s);						\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }
#else
#define func_snprintf_40Qg(funcStr) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      VARGQUAD a0;							\
      memset(&a0, 0, sizeof(a0));					\
      a0 = xsetq(a0, 0, c0.q);						\
      char s[64];							\
      Sleef_snprintf(s, 63, "%.40Pg", &a0);				\
      printf("%s\n", s);						\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_snprintf_Qa(funcStr) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      VARGQUAD a0;							\
      memset(&a0, 0, sizeof(a0));					\
      a0 = xsetq(a0, 0, c0.q);						\
      char s[64];							\
      Sleef_snprintf(s, 63, "%Pa", &a0);				\
      printf("%s\n", s);						\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }
#endif

int main2(int argc, char **argv) {
  xsrand(time(NULL));

  {
    int k = 0;
    k += 1;
#if defined(ENABLE_PUREC_SCALAR)
    k += 2; // Enable string testing
#endif
    printf("%d\n", k);
    fflush(stdout);
  }

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
    if (Sleef_icmpneq1_purec(xgetq(v0, 0), xgetq(v1, 0))) {
      fprintf(stderr, "Testing on SLEEF_QUAD_C failed\n");
      exit(-1);
    }
  }
#elif defined(ENABLE_PUREC_SCALAR)
#pragma message ("SLEEF_QUAD_C not defined")
#endif

  {
    VARGQUAD v0 = xsplatq(SLEEF_M_PIq);
    VARGQUAD v1 = xsplatq(Sleef_strtoq("2.718281828459045235360287471352662498", NULL));
    Sleef_quad q = xgetq(xmulq_u05(v0, v1), 0);
    if (Sleef_icmpneq1_purec(q, Sleef_strtoq("8.539734222673567065463550869546573820", NULL))) {
      fprintf(stderr, "Testing with xgetq failed\n");
      exit(-1);
    }
  }

  char buf[BUFSIZE];
  fgets(buf, BUFSIZE-1, stdin);
  int sentinel = 0;

  while(!feof(stdin) && sentinel < 2) {
    func_q_q_q("addq_u05", xaddq_u05);
    func_q_q_q("subq_u05", xsubq_u05);
    func_q_q_q("mulq_u05", xmulq_u05);
    func_q_q_q("divq_u05", xdivq_u05);
    func_q_q("sqrtq_u05", xsqrtq_u05);
    func_q_q("cbrtq_u10", xcbrtq_u10);
    func_q_q("sinq_u10", xsinq_u10);
    func_q_q("cosq_u10", xcosq_u10);
    func_q_q("tanq_u10", xtanq_u10);
    func_q_q("asinq_u10", xasinq_u10);
    func_q_q("acosq_u10", xacosq_u10);
    func_q_q("atanq_u10", xatanq_u10);
    func_q_q_q("atan2q_u10", xatan2q_u10);
    func_q_q("expq_u10", xexpq_u10);
    func_q_q("exp2q_u10", xexp2q_u10);
    func_q_q("exp10q_u10", xexp10q_u10);
    func_q_q("expm1q_u10", xexpm1q_u10);
    func_q_q("logq_u10", xlogq_u10);
    func_q_q("log2q_u10", xlog2q_u10);
    func_q_q("log10q_u10", xlog10q_u10);
    func_q_q("log1pq_u10", xlog1pq_u10);
    func_q_q_q("powq_u10", xpowq_u10);
    func_q_q("sinhq_u10", xsinhq_u10);
    func_q_q("coshq_u10", xcoshq_u10);
    func_q_q("tanhq_u10", xtanhq_u10);
    func_q_q("asinhq_u10", xasinhq_u10);
    func_q_q("acoshq_u10", xacoshq_u10);
    func_q_q("atanhq_u10", xatanhq_u10);

    func_q_q("negq", xnegq);
    func_q_q("fabsq", xfabsq);
    func_q_q_q("copysignq", xcopysignq);
    func_q_q_q("fmaxq", xfmaxq);
    func_q_q_q("fminq", xfminq);
    func_q_q_q("fdimq_u05", xfdimq_u05);
    func_q_q_q("fmodq", xfmodq);
    func_q_q_q("remainderq", xremainderq);
    func_q_q_pi("frexpq", xfrexpq);
    func_q_q_pq("modfq", xmodfq);
    func_i_q("ilogbq", xilogbq);
    func_q_q_i("ldexpq", xldexpq);
    func_q_q_q_q("fmaq_u05", xfmaq_u05);
    func_q_q_q("hypotq_u05", xhypotq_u05);

    func_q_q("truncq", xtruncq);
    func_q_q("floorq", xfloorq);
    func_q_q("ceilq", xceilq);
    func_q_q("roundq", xroundq);
    func_q_q("rintq", xrintq);

    func_q_d("cast_from_doubleq", xcast_from_doubleq);
    func_d_q("cast_to_doubleq", xcast_to_doubleq);
    func_q_i64("cast_from_int64q", xcast_from_int64q);
    func_i64_q("cast_to_int64q", xcast_to_int64q);
    func_q_u64("cast_from_uint64q", xcast_from_uint64q);
    func_u64_q("cast_to_uint64q", xcast_to_uint64q);

    func_i_q_q("icmpltq", xicmpltq);
    func_i_q_q("icmpgtq", xicmpgtq);
    func_i_q_q("icmpleq", xicmpleq);
    func_i_q_q("icmpgeq", xicmpgeq);
    func_i_q_q("icmpeqq", xicmpeqq);
    func_i_q_q("icmpneq", xicmpneq);
    func_i_q_q("icmpq", xicmpq);
    func_i_q_q("iunordq", xiunordq);

#ifdef ENABLE_PUREC_SCALAR
    func_strtoq("strtoq");
    func_snprintf_40Qg("snprintf_40Qg");
    func_snprintf_Qa("snprintf_Qa");
#endif

    sentinel++;
  }

  //

  return 0;
}
