//   Copyright Naoki Shibata and contributors 2010 - 2023.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <float.h>
#include <limits.h>
#include <string.h>
#include <time.h>

#include <openssl/evp.h>

#include "sleef.h"
#include "misc.h"
#include "testerutil.h"

#ifdef __VSX__
#include <altivec.h>
#undef vector
#undef bool
typedef __vector double __vector_double;
typedef __vector float  __vector_float;
#endif

#if defined(__VX__) && defined(__VEC__)
#ifndef SLEEF_VECINTRIN_H_INCLUDED
#include <vecintrin.h>
#define SLEEF_VECINTRIN_H_INCLUDED
#endif
typedef __attribute__((vector_size(16))) double vector_double;
typedef __attribute__((vector_size(16))) float  vector_float;
#endif

//

#define XNAN  (((union { int64_t u; double d; })  { .u = INT64_C(0xffffffffffffffff) }).d)
#define XNANf (((union { int32_t u; float d; })  { .u = 0xffffffff }).d)

static INLINE double unifyValue(double x) { x = !(x == x) ? XNAN  : x; return x; }
static INLINE float unifyValuef(float  x) { x = !(x == x) ? XNANf : x; return x; }

static INLINE double setdouble(double d, int r) { return d; }
static INLINE double getdouble(double v, int r) { return unifyValue(v); }
static INLINE float setfloat(float d, int r) { return d; }
static INLINE float getfloat(float v, int r) { return unifyValuef(v); }

#if defined(__i386__) || defined(__x86_64__) || defined(_MSC_VER)
static INLINE __m128d set__m128d(double d, int r) { static double a[2]; memrand(a, sizeof(a)); a[r & 1] = d; return _mm_loadu_pd(a); }
static INLINE double get__m128d(__m128d v, int r) { static double a[2]; _mm_storeu_pd(a, v); return unifyValue(a[r & 1]); }
static INLINE __m128 set__m128(float d, int r) { static float a[4]; memrand(a, sizeof(a)); a[r & 3] = d; return _mm_loadu_ps(a); }
static INLINE float get__m128(__m128 v, int r) { static float a[4]; _mm_storeu_ps(a, v); return unifyValuef(a[r & 3]); }

#if defined(__AVX__)
static INLINE __m256d set__m256d(double d, int r) { static double a[4]; memrand(a, sizeof(a)); a[r & 3] = d; return _mm256_loadu_pd(a); }
static INLINE double get__m256d(__m256d v, int r) { static double a[4]; _mm256_storeu_pd(a, v); return unifyValue(a[r & 3]); }
static INLINE __m256 set__m256(float d, int r) { static float a[8]; memrand(a, sizeof(a)); a[r & 7] = d; return _mm256_loadu_ps(a); }
static INLINE float get__m256(__m256 v, int r) { static float a[8]; _mm256_storeu_ps(a, v); return unifyValuef(a[r & 7]); }
#endif

#if defined(__AVX512F__)
static INLINE __m512d set__m512d(double d, int r) { static double a[8]; memrand(a, sizeof(a)); a[r & 7] = d; return _mm512_loadu_pd(a); }
static INLINE double get__m512d(__m512d v, int r) { static double a[8]; _mm512_storeu_pd(a, v); return unifyValue(a[r & 7]); }
static INLINE __m512 set__m512(float d, int r) { static float a[16]; memrand(a, sizeof(a)); a[r & 15] = d; return _mm512_loadu_ps(a); }
static INLINE float get__m512(__m512 v, int r) { static float a[16]; _mm512_storeu_ps(a, v); return unifyValuef(a[r & 15]); }
#endif
#endif // #if defined(__i386__) || defined(__x86_64__) || defined(_MSC_VER)

#if defined(__aarch64__) && defined(__ARM_NEON)
static INLINE VECTOR_CC float64x2_t setfloat64x2_t(double d, int r) { double a[2]; memrand(a, sizeof(a)); a[r & 1] = d; return vld1q_f64(a); }
static INLINE VECTOR_CC double getfloat64x2_t(float64x2_t v, int r) { double a[2]; vst1q_f64(a, v); return unifyValue(a[r & 1]); }
static INLINE VECTOR_CC float32x4_t setfloat32x4_t(float d, int r) { float a[4]; memrand(a, sizeof(a)); a[r & 3] = d; return vld1q_f32(a); }
static INLINE VECTOR_CC float getfloat32x4_t(float32x4_t v, int r) { float a[4]; vst1q_f32(a, v); return unifyValuef(a[r & 3]); }
#endif

#ifdef __ARM_FEATURE_SVE
static INLINE svfloat64_t setsvfloat64_t(double d, int r) { double a[svcntd()]; memrand(a, sizeof(a)); a[r & (svcntd()-1)] = d; return svld1_f64(svptrue_b8(), a); }
static INLINE double getsvfloat64_t(svfloat64_t v, int r) { double a[svcntd()]; svst1_f64(svptrue_b8(), a, v); return unifyValue(a[r & (svcntd()-1)]); }
static INLINE svfloat32_t setsvfloat32_t(float d, int r)  { float  a[svcntw()]; memrand(a, sizeof(a)); a[r & (svcntw()-1)] = d; return svld1_f32(svptrue_b8(), a); }
static INLINE float getsvfloat32_t(svfloat32_t v, int r)  { float  a[svcntw()]; svst1_f32(svptrue_b8(), a, v); return unifyValuef(a[r & (svcntw()-1)]); }

static svfloat64_t vd2getx_vd_vd2(svfloat64x2_t v) { return svget2_f64(v, 0); }
static svfloat64_t vd2gety_vd_vd2(svfloat64x2_t v) { return svget2_f64(v, 1); }
static svfloat32_t vf2getx_vf_vf2(svfloat32x2_t v) { return svget2_f32(v, 0); }
static svfloat32_t vf2gety_vf_vf2(svfloat32x2_t v) { return svget2_f32(v, 1); }
#endif

#ifdef __VSX__
static INLINE __vector double setSLEEF_VECTOR_DOUBLE(double d, int r) { double a[2]; memrand(a, sizeof(a)); a[r & 1] = d; return vec_vsx_ld(0, a); }
static INLINE double getSLEEF_VECTOR_DOUBLE(__vector double v, int r) { double a[2]; vec_vsx_st(v, 0, a); return unifyValue(a[r & 1]); }
static INLINE __vector float setSLEEF_VECTOR_FLOAT(float d, int r) { float a[4]; memrand(a, sizeof(a)); a[r & 3] = d; return vec_vsx_ld(0, a); }
static INLINE float getSLEEF_VECTOR_FLOAT(__vector float v, int r) { float a[4]; vec_vsx_st(v, 0, a); return unifyValuef(a[r & 3]); }
#endif

#ifdef __VX__
static INLINE __attribute__((vector_size(16))) double setSLEEF_VECTOR_DOUBLE(double d, int r) { double a[2]; memrand(a, sizeof(a)); a[r & 1] = d; return (__attribute__((vector_size(16))) double) { a[0], a[1] }; }
static INLINE double getSLEEF_VECTOR_DOUBLE(__attribute__((vector_size(16))) double v, int r) { return unifyValue(v[r & 1]); }
static INLINE __attribute__((vector_size(16))) float setSLEEF_VECTOR_FLOAT(float d, int r) { float a[4]; memrand(a, sizeof(a)); a[r & 3] = d; return (__attribute__((vector_size(16))) float) { a[0], a[1], a[2], a[3] }; }
static INLINE float getSLEEF_VECTOR_FLOAT(__attribute__((vector_size(16))) float v, int r) { return unifyValuef(v[r & 3]); }
#endif

#if __riscv && __riscv_v

#if defined(ENABLE_RVVM1)
#define VECTLENSP (1 * __riscv_vlenb() / sizeof(float))
#define VECTLENDP (1 * __riscv_vlenb() / sizeof(double))

static INLINE vfloat32m1_t setvfloat32m1_t(float d, int r)  { float  a[VECTLENSP]; memrand(a, sizeof(a)); a[r & (VECTLENSP-1)] = d; return __riscv_vle32_v_f32m1(a, VECTLENSP); }
static INLINE float getvfloat32m1_t(vfloat32m1_t v, int r)  { float  a[VECTLENSP]; __riscv_vse32(a, v, VECTLENSP); return unifyValuef(a[r & (VECTLENSP-1)]); }
static INLINE vfloat64m1_t setvfloat64m1_t(double d, int r) { double a[VECTLENDP]; memrand(a, sizeof(a)); a[r & (VECTLENDP-1)] = d; return __riscv_vle64_v_f64m1(a, VECTLENDP); }
static INLINE double getvfloat64m1_t(vfloat64m1_t v, int r) { double a[VECTLENDP]; __riscv_vse64(a, v, VECTLENDP); return unifyValue(a[r & (VECTLENDP-1)]); }

static vfloat32m1_t vf2getx_vf_vf2(vfloat32m2_t v) { return __riscv_vget_f32m1(v, 0); }
static vfloat32m1_t vf2gety_vf_vf2(vfloat32m2_t v) { return __riscv_vget_f32m1(v, 1); }
static vfloat64m1_t vd2getx_vd_vd2(vfloat64m2_t v) { return __riscv_vget_f64m1(v, 0); }
static vfloat64m1_t vd2gety_vd_vd2(vfloat64m2_t v) { return __riscv_vget_f64m1(v, 1); }

#elif defined(ENABLE_RVVM2)
#define VECTLENSP (2 * __riscv_vlenb() / sizeof(float))
#define VECTLENDP (2 * __riscv_vlenb() / sizeof(double))

static INLINE vfloat32m2_t setvfloat32m2_t(float d, int r)  { float  a[VECTLENSP]; memrand(a, sizeof(a)); a[r & (VECTLENSP-1)] = d; return __riscv_vle32_v_f32m2(a, VECTLENSP); }
static INLINE float getvfloat32m2_t(vfloat32m2_t v, int r)  { float  a[VECTLENSP]; __riscv_vse32(a, v, VECTLENSP); return unifyValuef(a[r & (VECTLENSP-1)]); }
static INLINE vfloat64m2_t setvfloat64m2_t(double d, int r) { double a[VECTLENDP]; memrand(a, sizeof(a)); a[r & (VECTLENDP-1)] = d; return __riscv_vle64_v_f64m2(a, VECTLENDP); }
static INLINE double getvfloat64m2_t(vfloat64m2_t v, int r) { double a[VECTLENDP]; __riscv_vse64(a, v, VECTLENDP); return unifyValue(a[r & (VECTLENDP-1)]); }

static vfloat32m2_t vf2getx_vf_vf2(vfloat32m4_t v) { return __riscv_vget_f32m2(v, 0); }
static vfloat32m2_t vf2gety_vf_vf2(vfloat32m4_t v) { return __riscv_vget_f32m2(v, 1); }
static vfloat64m2_t vd2getx_vd_vd2(vfloat64m4_t v) { return __riscv_vget_f64m2(v, 0); }
static vfloat64m2_t vd2gety_vd_vd2(vfloat64m4_t v) { return __riscv_vget_f64m2(v, 1); }

#else
#error "unknown RVV"
#endif

#undef VECTLENSP
#undef VECTLENDP
#endif

//

// ATR = cinz_, NAME = sin, TYPE = d2, ULP = u35, EXT = sse2
#define FUNC(ATR, NAME, TYPE, ULP, EXT) Sleef_ ## ATR ## NAME ## TYPE ## _ ## ULP ## EXT
#define _TYPE2(TYPE) Sleef_ ## TYPE ## _2
#define TYPE2(TYPE) _TYPE2(TYPE)
#define SET(TYPE) set ## TYPE
#define GET(TYPE) get ## TYPE

#if !defined(__ARM_FEATURE_SVE) && !(defined(__riscv) && defined(__riscv_v))
static DPTYPE vd2getx_vd_vd2(TYPE2(DPTYPE) v) { return v.x; }
static DPTYPE vd2gety_vd_vd2(TYPE2(DPTYPE) v) { return v.y; }
static SPTYPE vf2getx_vf_vf2(TYPE2(SPTYPE) v) { return v.x; }
static SPTYPE vf2gety_vf_vf2(TYPE2(SPTYPE) v) { return v.y; }
#endif

//

#define initDigest 					\
    EVP_MD_CTX *ctx; ctx = EVP_MD_CTX_new();		\
    if (!ctx) {						\
        fprintf(stderr, "Error creating context.\n");	\
        return 0;					\
    }							\
    if (!EVP_DigestInit_ex(ctx, EVP_md5(), NULL)) {	\
        fprintf(stderr, "Error initializing context.\n"); \
        return 0;					\
    }

#define checkDigest(NAME, ULP) do {				\
    unsigned int md5_digest_len = EVP_MD_size(EVP_md5());	\
    unsigned char *md5_digest;					\
    md5_digest = (unsigned char *)malloc(md5_digest_len);	\
    if (!EVP_DigestFinal_ex(ctx, md5_digest, &md5_digest_len)) { \
      fprintf(stderr, "Error finalizing digest.\n");		\
      return 0;							\
    }								\
    EVP_MD_CTX_free(ctx);					\
    unsigned char mes[64], buf[64];				\
    memset(mes, 0, 64);						\
    sprintf((char *)mes, "%s ", #NAME " " #ULP);		\
    char tmp[3] = { 0 };					\
    for (int i = 0; i < md5_digest_len; i++) {			\
        sprintf(tmp, "%02x", md5_digest[i]);			\
        strcat((char *)mes, tmp);				\
    }								\
    free(md5_digest);						\
    if (fp != NULL) {						\
      fgets((char *)buf, 60, fp);				\
      if (strncmp((char *)mes, (char *)buf, strlen((char *)mes)) != 0) { \
	puts((char *)mes);					\
	puts((char *)buf);					\
	success = 0;						\
      }								\
    } else puts((char *)mes);					\
  } while(0)

#if defined(__BYTE_ORDER__) && (__BYTE_ORDER__ == __ORDER_BIG_ENDIAN__)
#define convertEndianness(ptr, len) do {				\
  for(int k=0;k<len/2;k++) {						\
    unsigned char t = ((unsigned char *)ptr)[k];			\
    ((unsigned char *)ptr)[k] = ((unsigned char *)ptr)[len-1-k];	\
    ((unsigned char *)ptr)[len-1-k] = t;				\
  }									\
  } while(0)
#else
#define convertEndianness(ptr, len)
#endif

#define exec_d_d(ATR, NAME, ULP, TYPE, TSX, EXT, arg) do {		\
    int r = xrand() & 0xffff;						\
    DPTYPE vx = FUNC(ATR, NAME, TSX, ULP, EXT) (SET(TYPE) (arg, r));	\
    double fx = GET(TYPE)(vx, r);					\
    convertEndianness(&fx, sizeof(double));				\
    EVP_DigestUpdate(ctx, &fx, sizeof(double));				\
  } while(0)

#define test_d_d(NAME, ULP, START, END, NSTEP) do {		\
    initDigest							\
    double step = ((double)(END) - (double)(START))/NSTEP;	\
    for(double d = (START);d < (END);d += step)			\
      exec_d_d(ATR, NAME, ULP, DPTYPE, DPTYPESPEC, EXTSPEC, d);	\
    checkDigest(NAME, ULP);					\
  } while(0)

#define testu_d_d(NAME, ULP, START, END, NSTEP) do {			\
    initDigest								\
    uint64_t step = (d2u(END) - d2u(START))/NSTEP;			\
    for(uint64_t u = d2u(START);u < d2u(END);u += step)			\
      exec_d_d(ATR, NAME, ULP, DPTYPE, DPTYPESPEC, EXTSPEC, u2d(u));	\
    checkDigest(NAME, ULP);						\
  } while(0)

//

#define exec_d2_d(ATR, NAME, ULP, TYPE, TSX, EXT, arg) do {		\
    int r = xrand() & 0xffff;						\
    TYPE2(TYPE) vx2 = FUNC(ATR, NAME, TSX, ULP, EXT) (SET(TYPE)(arg, r)); \
    double fxx = GET(TYPE)(vd2getx_vd_vd2(vx2), r), fxy = GET(TYPE)(vd2gety_vd_vd2(vx2), r); \
    convertEndianness(&fxx, sizeof(double));				\
    EVP_DigestUpdate(ctx, &fxx, sizeof(double));			\
    convertEndianness(&fxy, sizeof(double));				\
    EVP_DigestUpdate(ctx, &fxy, sizeof(double));			\
  } while(0)

#define test_d2_d(NAME, ULP, START, END, NSTEP) do {			\
    initDigest								\
    double step = ((double)(END) - (double)(START))/NSTEP;		\
    for(double d = (START);d < (END);d += step)				\
      exec_d2_d(ATR, NAME, ULP, DPTYPE, DPTYPESPEC, EXTSPEC, d);	\
    checkDigest(NAME, ULP);						\
  } while(0)

//

#define exec_d_d_d(ATR, NAME, ULP, TYPE, TSX, EXT, argu, argv) do {	\
    int r = xrand() & 0xffff;						\
    DPTYPE vx = FUNC(ATR, NAME, TSX, ULP, EXT) (SET(TYPE) (argu, r), SET(TYPE) (argv, r)); \
    double fx = GET(TYPE)(vx, r);					\
    convertEndianness(&fx, sizeof(double));				\
    EVP_DigestUpdate(ctx, &fx, sizeof(double));				\
  } while(0)

#define test_d_d_d(NAME, ULP, STARTU, ENDU, NSTEPU, STARTV, ENDV, NSTEPV) do { \
    initDigest								\
    double stepu = ((double)(ENDU) - (double)(STARTU))/NSTEPU;		\
    double stepv = ((double)(ENDV) - (double)(STARTV))/NSTEPV;		\
    for(double u = (STARTU);u < (ENDU);u += stepu)			\
      for(double v = (STARTV);v < (ENDV);v += stepv)			\
	exec_d_d_d(ATR, NAME, ULP, DPTYPE, DPTYPESPEC, EXTSPEC, u, v);	\
    checkDigest(NAME, ULP);						\
  } while(0)

//

#define exec_f_f(ATR, NAME, ULP, TYPE, TSX, EXT, arg) do {		\
    int r = xrand() & 0xffff;						\
    SPTYPE vx = FUNC(ATR, NAME, TSX, ULP, EXT) (SET(TYPE) (arg, r));	\
    float fx = GET(TYPE)(vx, r);					\
    convertEndianness(&fx, sizeof(float));				\
    EVP_DigestUpdate(ctx, &fx, sizeof(float));				\
  } while(0)

#define test_f_f(NAME, ULP, START, END, NSTEP) do {		\
    initDigest							\
    float step = ((double)(END) - (double)(START))/NSTEP;	\
    for(float d = (START);d < (END);d += step)			\
      exec_f_f(ATR, NAME, ULP, SPTYPE, SPTYPESPEC, EXTSPEC, d);	\
    checkDigest(NAME ## f, ULP);				\
  } while(0)

#define testu_f_f(NAME, ULP, START, END, NSTEP) do {			\
    initDigest								\
    uint32_t step = (f2u(END) - f2u(START))/NSTEP;			\
    for(uint32_t u = f2u(START);u < f2u(END);u += step)			\
      exec_f_f(ATR, NAME, ULP, SPTYPE, SPTYPESPEC, EXTSPEC, u2f(u));	\
    checkDigest(NAME ## f, ULP);					\
  } while(0)

//

#define exec_f2_f(ATR, NAME, ULP, TYPE, TSX, EXT, arg) do {		\
    int r = xrand() & 0xffff;						\
    TYPE2(TYPE) vx2 = FUNC(ATR, NAME, TSX, ULP, EXT) (SET(TYPE) (arg, r)); \
    float fxx = GET(TYPE)(vf2getx_vf_vf2(vx2), r), fxy = GET(TYPE)(vf2gety_vf_vf2(vx2), r); \
    convertEndianness(&fxx, sizeof(float));				\
    EVP_DigestUpdate(ctx, &fxx, sizeof(float));				\
    convertEndianness(&fxy, sizeof(float));				\
    EVP_DigestUpdate(ctx, &fxy, sizeof(float));				\
  } while(0)

#define test_f2_f(NAME, ULP, START, END, NSTEP) do {			\
    initDigest								\
    float step = ((float)(END) - (float)(START))/NSTEP;			\
    for(float d = (START);d < (END);d += step)				\
      exec_f2_f(ATR, NAME, ULP, SPTYPE, SPTYPESPEC, EXTSPEC, d);	\
    checkDigest(NAME ## f, ULP);					\
  } while(0)

//

#define exec_f_f_f(ATR, NAME, ULP, TYPE, TSX, EXT, argu, argv) do {	\
    int r = xrand() & 0xffff;						\
    SPTYPE vx = FUNC(ATR, NAME, TSX, ULP, EXT) (SET(TYPE) (argu, r), SET(TYPE) (argv, r)); \
    float fx = GET(TYPE)(vx, r);					\
    convertEndianness(&fx, sizeof(float));				\
    EVP_DigestUpdate(ctx, &fx, sizeof(float));				\
  } while(0)

#define test_f_f_f(NAME, ULP, STARTU, ENDU, NSTEPU, STARTV, ENDV, NSTEPV) do { \
    initDigest								\
    float stepu = ((float)(ENDU) - (float)(STARTU))/NSTEPU;		\
    float stepv = ((float)(ENDV) - (float)(STARTV))/NSTEPV;		\
    for(float u = (STARTU);u < (ENDU);u += stepu)			\
      for(float v = (STARTV);v < (ENDV);v += stepv)			\
	exec_f_f_f(ATR, NAME, ULP, SPTYPE, SPTYPESPEC, EXTSPEC, u, v);	\
    checkDigest(NAME ## f, ULP);					\
  } while(0)

//

#define try_feature(TYPE, ATR_, TSX, EXT, arg)				\
  GET(TYPE) (FUNC(ATR_, pow, TSX, u10, EXT) (SET(TYPE) (arg, 0), SET(TYPE) (arg, 0)), 0)

int check_feature(double d, float f) {
  d = try_feature(DPTYPE, ATR, DPTYPESPEC, EXTSPEC, d);
  return d == d;
}

//

int success = 1;

int main2(int argc, char **argv)
{
  FILE *fp = NULL;

  if (argc != 1) {
    fp = fopen(argv[1], "r");
    if (fp == NULL) {
      fprintf(stderr, "Could not open %s\n", argv[1]);
      exit(-1);
    }
  }

  xsrand(time(NULL));

  //

  testu_d_d(sin, u35, 1e-300, 1e+8, 200001);
  testu_d_d(sin, u10, 1e-300, 1e+8, 200001);
  testu_d_d(cos, u35, 1e-300, 1e+8, 200001);
  testu_d_d(cos, u10, 1e-300, 1e+8, 200001);
  testu_d_d(tan, u35, 1e-300, 1e+8, 200001);
  testu_d_d(tan, u10, 1e-300, 1e+8, 200001);
  test_d2_d(sincos, u10, -1e+14, 1e+14, 200001);
  test_d2_d(sincos, u35, -1e+14, 1e+14, 200001);
  test_d2_d(sincospi, u05, -1e+14, 1e+14, 200001);
  test_d2_d(sincospi, u35, -1e+14, 1e+14, 200001);

  testu_d_d(log, u10, 1e-300, 1e+14, 200001);
  testu_d_d(log, u35, 1e-300, 1e+14, 200001);
  testu_d_d(log2, u10, 1e-300, 1e+14, 200001);
  testu_d_d(log2, u35, 1e-300, 1e+14, 200001);
  testu_d_d(log10, u10, 1e-300, 1e+14, 200001);
  testu_d_d(log1p, u10, 1e-300, 1e+14, 200001);
  test_d_d(exp, u10, -1000, 1000, 200001);
  test_d_d(exp2, u10, -1000, 1000, 200001);
  test_d_d(exp2, u35, -1000, 1000, 200001);
  test_d_d(exp10, u10, -1000, 1000, 200001);
  test_d_d(exp10, u35, -1000, 1000, 200001);
  test_d_d(expm1, u10, -1000, 1000, 200001);
  test_d_d_d(pow, u10, -100, 100, 451, -100, 100, 451);

  testu_d_d(cbrt, u10, 1e-14, 1e+14, 100001);
  testu_d_d(cbrt, u10, -1e-14, -1e+14, 100001);
  testu_d_d(cbrt, u35, 1e-14, 1e+14, 100001);
  testu_d_d(cbrt, u35, -1e-14, -1e+14, 100001);
  test_d_d_d(hypot, u05, -1e7, 1e7, 451, -1e7, 1e7, 451);
  test_d_d_d(hypot, u35, -1e7, 1e7, 451, -1e7, 1e7, 451);

  test_d_d(asin, u10, -1, 1, 200001);
  test_d_d(asin, u35, -1, 1, 200001);
  test_d_d(acos, u10, -1, 1, 200001);
  test_d_d(acos, u35, -1, 1, 200001);
  testu_d_d(atan, u10,  1e-3,  1e+7, 100001);
  testu_d_d(atan, u10, -1e-2, -1e+8, 100001);
  testu_d_d(atan, u35,  1e-3,  1e+7, 100001);
  testu_d_d(atan, u35, -1e-2, -1e+8, 100001);
  test_d_d_d(atan2, u10, -10, 10, 451, -10, 10, 451);
  test_d_d_d(atan2, u35, -10, 10, 451, -10, 10, 451);

  test_d_d(sinh, u10, -700, 700, 200001);
  test_d_d(cosh, u10, -700, 700, 200001);
  test_d_d(tanh, u10, -700, 700, 200001);
  test_d_d(asinh, u10, -700, 700, 200001);
  test_d_d(acosh, u10, 1, 700, 200001);
  test_d_d(atanh, u10, -700, 700, 200001);

  test_d_d(lgamma, u10, -5000, 5000, 200001);
  test_d_d(tgamma, u10, -10, 10, 200001);
  test_d_d(erf, u10, -100, 100, 200001);
  test_d_d(erfc, u15, -1, 100, 200001);

  test_d_d(fabs, , -100.5, 100.5, 200001);
  test_d_d_d(copysign, , -1e+10, 1e+10, 451, -1e+10, 1e+10, 451);
  test_d_d_d(fmax, , -1e+10, 1e+10, 451, -1e+10, 1e+10, 451);
  test_d_d_d(fmin, , -1e+10, 1e+10, 451, -1e+10, 1e+10, 451);
  test_d_d_d(fdim, , -1e+10, 1e+10, 451, -1e+10, 1e+10, 451);
  test_d_d_d(fmod, , -1e+10, 1e+10, 451, -1e+10, 1e+10, 451);
  test_d_d_d(remainder, , -1e+10, 1e+10, 451, -1e+10, 1e+10, 451);
  test_d2_d(modf, , -1e+14, 1e+14, 200001);
  test_d_d_d(nextafter, , -1e+10, 1e+10, 451, -1e+10, 1e+10, 451);

  test_d_d(trunc, , -100, 100, 800);
  test_d_d(floor, , -100, 100, 800);
  test_d_d(ceil, , -100, 100, 800);
  test_d_d(round, , -100, 100, 800);
  test_d_d(rint, , -100, 100, 800);

  //

  testu_f_f(sin, u35, 1e-30, 1e+8, 200001);
  testu_f_f(sin, u10, 1e-30, 1e+8, 200001);
  testu_f_f(cos, u35, 1e-30, 1e+8, 200001);
  testu_f_f(cos, u10, 1e-30, 1e+8, 200001);
  testu_f_f(tan, u35, 1e-30, 1e+8, 200001);
  testu_f_f(tan, u10, 1e-30, 1e+8, 200001);
  test_f2_f(sincos, u10, -1e+14, 1e+14, 200001);
  test_f2_f(sincos, u35, -1e+14, 1e+14, 200001);
  test_f2_f(sincospi, u05, -10000, 10000, 200001);
  test_f2_f(sincospi, u35, -10000, 10000, 200001);

  testu_f_f(log, u10, 1e-30, 1e+14, 200001);
  testu_f_f(log, u35, 1e-30, 1e+14, 200001);
  testu_f_f(log2, u10, 1e-30, 1e+14, 200001);
  testu_f_f(log2, u35, 1e-30, 1e+14, 200001);
  testu_f_f(log10, u10, 1e-30, 1e+14, 200001);
  testu_f_f(log1p, u10, 1e-30, 1e+14, 200001);
  test_f_f(exp, u10, -1000, 1000, 200001);
  test_f_f(exp2, u10, -1000, 1000, 200001);
  test_f_f(exp2, u35, -1000, 1000, 200001);
  test_f_f(exp10, u10, -1000, 1000, 200001);
  test_f_f(exp10, u35, -1000, 1000, 200001);
  test_f_f(expm1, u10, -1000, 1000, 200001);
  test_f_f_f(pow, u10, -100, 100, 451, -100, 100, 451);

  testu_f_f(cbrt, u10, 1e-14, 1e+14, 100001);
  testu_f_f(cbrt, u10, -1e-14, -1e+14, 100001);
  testu_f_f(cbrt, u35, 1e-14, 1e+14, 100001);
  testu_f_f(cbrt, u35, -1e-14, -1e+14, 100001);
  test_f_f_f(hypot, u05, -1e7, 1e7, 451, -1e7, 1e7, 451);
  test_f_f_f(hypot, u35, -1e7, 1e7, 451, -1e7, 1e7, 451);

  test_f_f(asin, u10, -1, 1, 200001);
  test_f_f(asin, u35, -1, 1, 200001);
  test_f_f(acos, u10, -1, 1, 200001);
  test_f_f(acos, u35, -1, 1, 200001);
  testu_f_f(atan, u10,  1e-3,  1e+7, 100001);
  testu_f_f(atan, u10, -1e-2, -1e+8, 100001);
  testu_f_f(atan, u35,  1e-3,  1e+7, 100001);
  testu_f_f(atan, u35, -1e-2, -1e+8, 100001);
  test_f_f_f(atan2, u10, -10, 10, 451, -10, 10, 451);
  test_f_f_f(atan2, u35, -10, 10, 451, -10, 10, 451);

  test_f_f(sinh, u10, -88, 88, 200001);
  test_f_f(cosh, u10, -88, 88, 200001);
  test_f_f(tanh, u10, -88, 88, 200001);
  test_f_f(asinh, u10, -88, 88, 200001);
  test_f_f(acosh, u10, 1, 88, 200001);
  test_f_f(atanh, u10, -88, 88, 200001);

  test_f_f(lgamma, u10, -5000, 5000, 200001);
  test_f_f(tgamma, u10, -10, 10, 200001);
  test_f_f(erf, u10, -100, 100, 200001);
  test_f_f(erfc, u15, -1, 100, 200001);

  test_f_f(fabs, , -100.5, 100.5, 200001);
  test_f_f_f(copysign, , -1e+10, 1e+10, 451, -1e+10, 1e+10, 451);
  test_f_f_f(fmax, , -1e+10, 1e+10, 451, -1e+10, 1e+10, 451);
  test_f_f_f(fmin, , -1e+10, 1e+10, 451, -1e+10, 1e+10, 451);
  test_f_f_f(fdim, , -1e+10, 1e+10, 451, -1e+10, 1e+10, 451);
  test_f_f_f(fmod, , -1e+10, 1e+10, 451, -1e+10, 1e+10, 451);
  test_f_f_f(remainder, , -1e+10, 1e+10, 451, -1e+10, 1e+10, 451);
  test_f2_f(modf, , -1e+14, 1e+14, 200001);
  test_f_f_f(nextafter, , -1e+10, 1e+10, 451, -1e+10, 1e+10, 451);

  test_f_f(trunc, , -100, 100, 800);
  test_f_f(floor, , -100, 100, 800);
  test_f_f(ceil, , -100, 100, 800);
  test_f_f(round, , -100, 100, 800);
  test_f_f(rint, , -100, 100, 800);

  test_f_f(fastsin, u3500, 1e-30, 100, 200001);
  test_f_f(fastcos, u3500, 1e-30, 100, 200001);
  test_f_f_f(fastpow, u3500, 0, 25, 451, -25, 25, 451);

  //

  if (fp != NULL) fclose(fp);

  exit(success ? 0 : -1);
}
