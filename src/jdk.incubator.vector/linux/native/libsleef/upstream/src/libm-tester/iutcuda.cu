//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <math.h>
#include <float.h>
#include <stdint.h>
#include <cuda.h>

#include "sleefinline_purec_scalar.h"
#include "sleefinline_cuda.h"

#define STDIN_FILENO 0

#define SIMD_SUFFIX _cuda_sleef
#define CONCAT_SIMD_SUFFIX_(keyword, suffix) keyword ## suffix
#define CONCAT_SIMD_SUFFIX(keyword, suffix) CONCAT_SIMD_SUFFIX_(keyword, suffix)

#define vdouble2 CONCAT_SIMD_SUFFIX(vdouble2, SIMD_SUFFIX)
#define vfloat2 CONCAT_SIMD_SUFFIX(vfloat2, SIMD_SUFFIX)

//

static int startsWith(const char *str, const char *prefix) {
  while(*prefix != '\0') if (*str++ != *prefix++) return 0;
  return *prefix == '\0';
}

static double u2d(uint64_t u) {
  union {
    double f;
    uint64_t i;
  } tmp;
  tmp.i = u;
  return tmp.f;
}

static uint64_t d2u(double d) {
  union {
    double f;
    uint64_t i;
  } tmp;
  tmp.f = d;
  return tmp.i;
}

static float u2f(uint32_t u) {
  union {
    float f;
    uint32_t i;
  } tmp;
  tmp.i = u;
  return tmp.f;
}

static uint32_t f2u(float d) {
  union {
    float f;
    uint32_t i;
  } tmp;
  tmp.f = d;
  return tmp.i;
}

//

__global__ void xsin(double *r, double *a0) { *r = Sleef_sind1_u35cuda(*a0); }
__global__ void xcos(double *r, double *a0) { *r = Sleef_cosd1_u35cuda(*a0); }
__global__ void xsincos(vdouble2 *r, double *a0) { *r = Sleef_sincosd1_u35cuda(*a0); }
__global__ void xtan(double *r, double *a0) { *r = Sleef_tand1_u35cuda(*a0); }
__global__ void xasin(double *r, double *a0) { *r = Sleef_asind1_u35cuda(*a0); }
__global__ void xacos(double *r, double *a0) { *r = Sleef_acosd1_u35cuda(*a0); }
__global__ void xatan(double *r, double *a0) { *r = Sleef_atand1_u35cuda(*a0); }
__global__ void xatan2(double *r, double *a0, double *a1) { *r = Sleef_atan2d1_u35cuda(*a0, *a1); }
__global__ void xlog(double *r, double *a0) { *r = Sleef_logd1_u35cuda(*a0); }
__global__ void xcbrt(double *r, double *a0) { *r = Sleef_cbrtd1_u35cuda(*a0); }
__global__ void xsin_u1(double *r, double *a0) { *r = Sleef_sind1_u10cuda(*a0); }
__global__ void xcos_u1(double *r, double *a0) { *r = Sleef_cosd1_u10cuda(*a0); }
__global__ void xsincos_u1(vdouble2 *r, double *a0) { *r = Sleef_sincosd1_u10cuda(*a0); }
__global__ void xtan_u1(double *r, double *a0) { *r = Sleef_tand1_u10cuda(*a0); }
__global__ void xasin_u1(double *r, double *a0) { *r = Sleef_asind1_u10cuda(*a0); }
__global__ void xacos_u1(double *r, double *a0) { *r = Sleef_acosd1_u10cuda(*a0); }
__global__ void xatan_u1(double *r, double *a0) { *r = Sleef_atand1_u10cuda(*a0); }
__global__ void xatan2_u1(double *r, double *a0, double *a1) { *r = Sleef_atan2d1_u10cuda(*a0, *a1); }
__global__ void xlog_u1(double *r, double *a0) { *r = Sleef_logd1_u10cuda(*a0); }
__global__ void xcbrt_u1(double *r, double *a0) { *r = Sleef_cbrtd1_u10cuda(*a0); }
__global__ void xexp(double *r, double *a0) { *r = Sleef_expd1_u10cuda(*a0); }
__global__ void xpow(double *r, double *a0, double *a1) { *r = Sleef_powd1_u10cuda(*a0, *a1); }
__global__ void xsinh(double *r, double *a0) { *r = Sleef_sinhd1_u10cuda(*a0); }
__global__ void xcosh(double *r, double *a0) { *r = Sleef_coshd1_u10cuda(*a0); }
__global__ void xtanh(double *r, double *a0) { *r = Sleef_tanhd1_u10cuda(*a0); }
__global__ void xsinh_u35(double *r, double *a0) { *r = Sleef_sinhd1_u35cuda(*a0); }
__global__ void xcosh_u35(double *r, double *a0) { *r = Sleef_coshd1_u35cuda(*a0); }
__global__ void xtanh_u35(double *r, double *a0) { *r = Sleef_tanhd1_u35cuda(*a0); }
__global__ void xasinh(double *r, double *a0) { *r = Sleef_asinhd1_u10cuda(*a0); }
__global__ void xacosh(double *r, double *a0) { *r = Sleef_acoshd1_u10cuda(*a0); }
__global__ void xatanh(double *r, double *a0) { *r = Sleef_atanhd1_u10cuda(*a0); }
__global__ void xexp2(double *r, double *a0) { *r = Sleef_exp2d1_u10cuda(*a0); }
__global__ void xexp2_u35(double *r, double *a0) { *r = Sleef_exp2d1_u35cuda(*a0); }
__global__ void xexp10(double *r, double *a0) { *r = Sleef_exp10d1_u10cuda(*a0); }
__global__ void xexp10_u35(double *r, double *a0) { *r = Sleef_exp10d1_u35cuda(*a0); }
__global__ void xexpm1(double *r, double *a0) { *r = Sleef_expm1d1_u10cuda(*a0); }
__global__ void xlog10(double *r, double *a0) { *r = Sleef_log10d1_u10cuda(*a0); }
__global__ void xlog2(double *r, double *a0) { *r = Sleef_log2d1_u10cuda(*a0); }
__global__ void xlog2_u35(double *r, double *a0) { *r = Sleef_log2d1_u35cuda(*a0); }
__global__ void xlog1p(double *r, double *a0) { *r = Sleef_log1pd1_u10cuda(*a0); }
__global__ void xsincospi_u05(vdouble2 *r, double *a0) { *r = Sleef_sincospid1_u05cuda(*a0); }
__global__ void xsincospi_u35(vdouble2 *r, double *a0) { *r = Sleef_sincospid1_u35cuda(*a0); }
__global__ void xsinpi_u05(double *r, double *a0) { *r = Sleef_sinpid1_u05cuda(*a0); }
__global__ void xcospi_u05(double *r, double *a0) { *r = Sleef_cospid1_u05cuda(*a0); }
__global__ void xldexp(double *r, double *a0, int *a1) { *r = Sleef_ldexpd1_cuda(*a0, *a1); }
__global__ void xilogb(int *r, double *a0) { *r = Sleef_ilogbd1_cuda(*a0); }
__global__ void xfma(double *r, double *a0, double *a1, double *a2) { *r = Sleef_fmad1_cuda(*a0, *a1, *a2); }
__global__ void xsqrt(double *r, double *a0) { *r = Sleef_sqrtd1_cuda(*a0); }
__global__ void xsqrt_u05(double *r, double *a0) { *r = Sleef_sqrtd1_u05cuda(*a0); }
__global__ void xsqrt_u35(double *r, double *a0) { *r = Sleef_sqrtd1_u35cuda(*a0); }
__global__ void xhypot_u05(double *r, double *a0, double *a1) { *r = Sleef_hypotd1_u05cuda(*a0, *a1); }
__global__ void xhypot_u35(double *r, double *a0, double *a1) { *r = Sleef_hypotd1_u35cuda(*a0, *a1); }
__global__ void xfabs(double *r, double *a0) { *r = Sleef_fabsd1_cuda(*a0); }
__global__ void xcopysign(double *r, double *a0, double *a1) { *r = Sleef_copysignd1_cuda(*a0, *a1); }
__global__ void xfmax(double *r, double *a0, double *a1) { *r = Sleef_fmaxd1_cuda(*a0, *a1); }
__global__ void xfmin(double *r, double *a0, double *a1) { *r = Sleef_fmind1_cuda(*a0, *a1); }
__global__ void xfdim(double *r, double *a0, double *a1) { *r = Sleef_fdimd1_cuda(*a0, *a1); }
__global__ void xtrunc(double *r, double *a0) { *r = Sleef_truncd1_cuda(*a0); }
__global__ void xfloor(double *r, double *a0) { *r = Sleef_floord1_cuda(*a0); }
__global__ void xceil(double *r, double *a0) { *r = Sleef_ceild1_cuda(*a0); }
__global__ void xround(double *r, double *a0) { *r = Sleef_roundd1_cuda(*a0); }
__global__ void xrint(double *r, double *a0) { *r = Sleef_rintd1_cuda(*a0); }
__global__ void xnextafter(double *r, double *a0, double *a1) { *r = Sleef_nextafterd1_cuda(*a0, *a1); }
__global__ void xfrfrexp(double *r, double *a0) { *r = Sleef_frfrexpd1_cuda(*a0); }
__global__ void xexpfrexp(int *r, double *a0) { *r = Sleef_expfrexpd1_cuda(*a0); }
__global__ void xfmod(double *r, double *a0, double *a1) { *r = Sleef_fmodd1_cuda(*a0, *a1); }
__global__ void xremainder(double *r, double *a0, double *a1) { *r = Sleef_remainderd1_cuda(*a0, *a1); }
__global__ void xmodf(vdouble2 *r, double *a0) { *r = Sleef_modfd1_cuda(*a0); }
__global__ void xlgamma_u1(double *r, double *a0) { *r = Sleef_lgammad1_u10cuda(*a0); }
__global__ void xtgamma_u1(double *r, double *a0) { *r = Sleef_tgammad1_u10cuda(*a0); }
__global__ void xerf_u1(double *r, double *a0) { *r = Sleef_erfd1_u10cuda(*a0); }
__global__ void xerfc_u15(double *r, double *a0) { *r = Sleef_erfcd1_u15cuda(*a0); }

__global__ void xsinf(float *r, float *a0) { *r = Sleef_sinf1_u35cuda(*a0); }
__global__ void xcosf(float *r, float *a0) { *r = Sleef_cosf1_u35cuda(*a0); }
__global__ void xsincosf(vfloat2 *r, float *a0) { *r = Sleef_sincosf1_u35cuda(*a0); }
__global__ void xtanf(float *r, float *a0) { *r = Sleef_tanf1_u35cuda(*a0); }
__global__ void xasinf(float *r, float *a0) { *r = Sleef_asinf1_u35cuda(*a0); }
__global__ void xacosf(float *r, float *a0) { *r = Sleef_acosf1_u35cuda(*a0); }
__global__ void xatanf(float *r, float *a0) { *r = Sleef_atanf1_u35cuda(*a0); }
__global__ void xatan2f(float *r, float *a0, float *a1) { *r = Sleef_atan2f1_u35cuda(*a0, *a1); }
__global__ void xlogf(float *r, float *a0) { *r = Sleef_logf1_u35cuda(*a0); }
__global__ void xcbrtf(float *r, float *a0) { *r = Sleef_cbrtf1_u35cuda(*a0); }
__global__ void xsinf_u1(float *r, float *a0) { *r = Sleef_sinf1_u10cuda(*a0); }
__global__ void xcosf_u1(float *r, float *a0) { *r = Sleef_cosf1_u10cuda(*a0); }
__global__ void xsincosf_u1(vfloat2 *r, float *a0) { *r = Sleef_sincosf1_u10cuda(*a0); }
__global__ void xtanf_u1(float *r, float *a0) { *r = Sleef_tanf1_u10cuda(*a0); }
__global__ void xasinf_u1(float *r, float *a0) { *r = Sleef_asinf1_u10cuda(*a0); }
__global__ void xacosf_u1(float *r, float *a0) { *r = Sleef_acosf1_u10cuda(*a0); }
__global__ void xatanf_u1(float *r, float *a0) { *r = Sleef_atanf1_u10cuda(*a0); }
__global__ void xatan2f_u1(float *r, float *a0, float *a1) { *r = Sleef_atan2f1_u10cuda(*a0, *a1); }
__global__ void xlogf_u1(float *r, float *a0) { *r = Sleef_logf1_u10cuda(*a0); }
__global__ void xcbrtf_u1(float *r, float *a0) { *r = Sleef_cbrtf1_u10cuda(*a0); }
__global__ void xexpf(float *r, float *a0) { *r = Sleef_expf1_u10cuda(*a0); }
__global__ void xpowf(float *r, float *a0, float *a1) { *r = Sleef_powf1_u10cuda(*a0, *a1); }
__global__ void xsinhf(float *r, float *a0) { *r = Sleef_sinhf1_u10cuda(*a0); }
__global__ void xcoshf(float *r, float *a0) { *r = Sleef_coshf1_u10cuda(*a0); }
__global__ void xtanhf(float *r, float *a0) { *r = Sleef_tanhf1_u10cuda(*a0); }
__global__ void xsinhf_u35(float *r, float *a0) { *r = Sleef_sinhf1_u35cuda(*a0); }
__global__ void xcoshf_u35(float *r, float *a0) { *r = Sleef_coshf1_u35cuda(*a0); }
__global__ void xtanhf_u35(float *r, float *a0) { *r = Sleef_tanhf1_u35cuda(*a0); }
__global__ void xfastsinf_u3500(float *r, float *a0) { *r = Sleef_fastsinf1_u3500cuda(*a0); }
__global__ void xfastcosf_u3500(float *r, float *a0) { *r = Sleef_fastcosf1_u3500cuda(*a0); }
__global__ void xfastpowf_u3500(float *r, float *a0, float *a1) { *r = Sleef_fastpowf1_u3500cuda(*a0, *a1); }
__global__ void xasinhf(float *r, float *a0) { *r = Sleef_asinhf1_u10cuda(*a0); }
__global__ void xacoshf(float *r, float *a0) { *r = Sleef_acoshf1_u10cuda(*a0); }
__global__ void xatanhf(float *r, float *a0) { *r = Sleef_atanhf1_u10cuda(*a0); }
__global__ void xexp2f(float *r, float *a0) { *r = Sleef_exp2f1_u10cuda(*a0); }
__global__ void xexp2f_u35(float *r, float *a0) { *r = Sleef_exp2f1_u35cuda(*a0); }
__global__ void xexp10f(float *r, float *a0) { *r = Sleef_exp10f1_u10cuda(*a0); }
__global__ void xexp10f_u35(float *r, float *a0) { *r = Sleef_exp10f1_u35cuda(*a0); }
__global__ void xexpm1f(float *r, float *a0) { *r = Sleef_expm1f1_u10cuda(*a0); }
__global__ void xlog10f(float *r, float *a0) { *r = Sleef_log10f1_u10cuda(*a0); }
__global__ void xlog2f(float *r, float *a0) { *r = Sleef_log2f1_u10cuda(*a0); }
__global__ void xlog2f_u35(float *r, float *a0) { *r = Sleef_log2f1_u35cuda(*a0); }
__global__ void xlog1pf(float *r, float *a0) { *r = Sleef_log1pf1_u10cuda(*a0); }
__global__ void xsincospif_u05(vfloat2 *r, float *a0) { *r = Sleef_sincospif1_u05cuda(*a0); }
__global__ void xsincospif_u35(vfloat2 *r, float *a0) { *r = Sleef_sincospif1_u35cuda(*a0); }
__global__ void xsinpif_u05(float *r, float *a0) { *r = Sleef_sinpif1_u05cuda(*a0); }
__global__ void xcospif_u05(float *r, float *a0) { *r = Sleef_cospif1_u05cuda(*a0); }
__global__ void xldexpf(float *r, float *a0, int *a1) { *r = Sleef_ldexpf1_cuda(*a0, *a1); }
__global__ void xilogbf(int *r, float *a0) { *r = Sleef_ilogbf1_cuda(*a0); }
__global__ void xfmaf(float *r, float *a0, float *a1, float *a2) { *r = Sleef_fmaf1_cuda(*a0, *a1, *a2); }
__global__ void xsqrtf(float *r, float *a0) { *r = Sleef_sqrtf1_cuda(*a0); }
__global__ void xsqrtf_u05(float *r, float *a0) { *r = Sleef_sqrtf1_u05cuda(*a0); }
__global__ void xsqrtf_u35(float *r, float *a0) { *r = Sleef_sqrtf1_u35cuda(*a0); }
__global__ void xhypotf_u05(float *r, float *a0, float *a1) { *r = Sleef_hypotf1_u05cuda(*a0, *a1); }
__global__ void xhypotf_u35(float *r, float *a0, float *a1) { *r = Sleef_hypotf1_u35cuda(*a0, *a1); }
__global__ void xfabsf(float *r, float *a0) { *r = Sleef_fabsf1_cuda(*a0); }
__global__ void xcopysignf(float *r, float *a0, float *a1) { *r = Sleef_copysignf1_cuda(*a0, *a1); }
__global__ void xfmaxf(float *r, float *a0, float *a1) { *r = Sleef_fmaxf1_cuda(*a0, *a1); }
__global__ void xfminf(float *r, float *a0, float *a1) { *r = Sleef_fminf1_cuda(*a0, *a1); }
__global__ void xfdimf(float *r, float *a0, float *a1) { *r = Sleef_fdimf1_cuda(*a0, *a1); }
__global__ void xtruncf(float *r, float *a0) { *r = Sleef_truncf1_cuda(*a0); }
__global__ void xfloorf(float *r, float *a0) { *r = Sleef_floorf1_cuda(*a0); }
__global__ void xceilf(float *r, float *a0) { *r = Sleef_ceilf1_cuda(*a0); }
__global__ void xroundf(float *r, float *a0) { *r = Sleef_roundf1_cuda(*a0); }
__global__ void xrintf(float *r, float *a0) { *r = Sleef_rintf1_cuda(*a0); }
__global__ void xnextafterf(float *r, float *a0, float *a1) { *r = Sleef_nextafterf1_cuda(*a0, *a1); }
__global__ void xfrfrexpf(float *r, float *a0) { *r = Sleef_frfrexpf1_cuda(*a0); }
__global__ void xexpfrexpf(float *r, float *a0) { *r = Sleef_expfrexpf1_cuda(*a0); }
__global__ void xfmodf(float *r, float *a0, float *a1) { *r = Sleef_fmodf1_cuda(*a0, *a1); }
__global__ void xremainderf(float *r, float *a0, float *a1) { *r = Sleef_remainderf1_cuda(*a0, *a1); }
__global__ void xmodff(vfloat2 *r, float *a0) { *r = Sleef_modff1_cuda(*a0); }
__global__ void xlgammaf_u1(float *r, float *a0) { *r = Sleef_lgammaf1_u10cuda(*a0); }
__global__ void xtgammaf_u1(float *r, float *a0) { *r = Sleef_tgammaf1_u10cuda(*a0); }
__global__ void xerff_u1(float *r, float *a0) { *r = Sleef_erff1_u10cuda(*a0); }
__global__ void xerfcf_u15(float *r, float *a0) { *r = Sleef_erfcf1_u15cuda(*a0); }

//

#define func_d_d(funcStr, funcName) {                           \
    while (startsWith(buf, funcStr " ")) {                      \
      uint64_t u;                                               \
      sscanf(buf, funcStr " %" PRIx64, &u);                     \
      *a0 = u2d(u);                                             \
      funcName<<<1, 1>>>(r, a0);                                \
      cudaDeviceSynchronize();                                  \
      printf("%" PRIx64 "\n", d2u(*r));                         \
      fflush(stdout);                                           \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;          \
    }                                                           \
  }

#define func_d2_d(funcStr, funcName) {                                  \
    while (startsWith(buf, funcStr " ")) {                              \
      uint64_t u;                                                       \
      sscanf(buf, funcStr " %" PRIx64, &u);                             \
      *a0 = u2d(u);                                                     \
      funcName<<<1, 1>>>(r2, a0);                                       \
      cudaDeviceSynchronize();                                          \
      printf("%" PRIx64 " %" PRIx64 "\n", d2u(r2->x), d2u(r2->y));      \
      fflush(stdout);                                                   \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;                  \
    }                                                                   \
  }

#define func_d_d_d(funcStr, funcName) {                         \
    while (startsWith(buf, funcStr " ")) {                      \
      uint64_t u, v;                                            \
      sscanf(buf, funcStr " %" PRIx64 " %" PRIx64, &u, &v);     \
      *a0 = u2d(u);                                             \
      *a1 = u2d(v);                                             \
      funcName<<<1, 1>>>(r, a0, a1);                            \
      cudaDeviceSynchronize();                                  \
      printf("%" PRIx64 "\n", d2u(*r));                         \
      fflush(stdout);                                           \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;          \
    }                                                           \
  }

#define func_d_d_i(funcStr, funcName) {                                 \
    while (startsWith(buf, funcStr " ")) {                              \
      uint64_t u, v;                                                    \
      sscanf(buf, funcStr " %" PRIx64 " %" PRIx64, &u, &v);             \
      *a0 = u2d(u);                                                     \
      *i0 = (int)u2d(v);                                                \
      funcName<<<1, 1>>>(r, a0, i0);                                    \
      cudaDeviceSynchronize();                                          \
      printf("%" PRIx64 "\n", d2u(*r));                                 \
      fflush(stdout);                                                   \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;                  \
    }                                                                   \
  }

#define func_i_d(funcStr, funcName) {                   \
    while (startsWith(buf, funcStr " ")) {              \
      uint64_t u;                                       \
      sscanf(buf, funcStr " %" PRIx64, &u);             \
      *a0 = u2d(u);                                     \
      funcName<<<1, 1>>>(i0, a0);                       \
      cudaDeviceSynchronize();                          \
      printf("%d\n", *i0);                              \
      fflush(stdout);                                   \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;  \
    }                                                   \
  }

//

#define func_f_f(funcStr, funcName) {                           \
    while (startsWith(buf, funcStr " ")) {                      \
      uint32_t u;                                               \
      sscanf(buf, funcStr " %x", &u);                           \
      *b0 = u2f(u);                                             \
      funcName<<<1, 1>>>(s, b0);                                \
      cudaDeviceSynchronize();                                  \
      printf("%x\n", f2u(*s));                                  \
      fflush(stdout);                                           \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;          \
    }                                                           \
  }

#define func_f2_f(funcStr, funcName) {                          \
    while (startsWith(buf, funcStr " ")) {                      \
      uint32_t u;                                               \
      sscanf(buf, funcStr " %x", &u);                           \
      *b0 = u2f(u);                                             \
      funcName<<<1, 1>>>(s2, b0);                               \
      cudaDeviceSynchronize();                                  \
      printf("%x %x\n", f2u(s2->x), f2u(s2->y));                \
      fflush(stdout);                                           \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;          \
    }                                                           \
  }

#define func_f_f_f(funcStr, funcName) {                         \
    while (startsWith(buf, funcStr " ")) {                      \
      uint32_t u, v;                                            \
      sscanf(buf, funcStr " %x %x", &u, &v);                    \
      *b0 = u2f(u);                                             \
      *b1 = u2f(v);                                             \
      funcName<<<1, 1>>>(s, b0, b1);                            \
      cudaDeviceSynchronize();                                  \
      printf("%x\n", f2u(*s));                                  \
      fflush(stdout);                                           \
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;          \
    }                                                           \
  }

//

#define BUFSIZE 1024

int main(int argc, char **argv) {
#if 0
  cuInit(0);

  int ndevice;
  cuDeviceGetCount(&ndevice);
  if (ndevice == 0) {
    fprintf(stderr, "No cuda device available\n");
    exit(0);
  }

  CUdevice device;
  char deviceName[1024];
  cuDeviceGet(&device, 0);
  cuDeviceGetName(deviceName, 1000, device);
  fprintf(stderr, "Device : %s\n", deviceName);
#endif

  cudaSetDeviceFlags(cudaDeviceScheduleSpin);

  vdouble2 *r2;
  vfloat2 *s2;
  double *r, *a0, *a1, *a2;
  float *s, *b0, *b1, *b2;
  int *i0;
  cudaMallocManaged(&r , 1*sizeof(double));
  cudaMallocManaged(&r2, 1*sizeof(vdouble2));
  cudaMallocManaged(&a0, 1*sizeof(double));
  cudaMallocManaged(&a1, 1*sizeof(double));
  cudaMallocManaged(&a2, 1*sizeof(double));
  cudaMallocManaged(&s , 1*sizeof(float));
  cudaMallocManaged(&s2, 1*sizeof(vfloat2));
  cudaMallocManaged(&b0, 1*sizeof(float));
  cudaMallocManaged(&b1, 1*sizeof(float));
  cudaMallocManaged(&b2, 1*sizeof(float));
  cudaMallocManaged(&i0, 1*sizeof(int));

  printf("3\n");
  fflush(stdout);

  char buf[BUFSIZE];
  if (fgets(buf, BUFSIZE-1, stdin)) {}

  while(!feof(stdin)) {
    func_d_d("sin", xsin);
    func_d_d("cos", xcos);
    func_d_d("tan", xtan);
    func_d_d("asin", xasin);
    func_d_d("acos", xacos);
    func_d_d("atan", xatan);
    func_d_d("log", xlog);
    func_d_d("exp", xexp);

    func_d_d("sqrt", xsqrt);
    func_d_d("sqrt_u05", xsqrt_u05);
    func_d_d("sqrt_u35", xsqrt_u35);
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
    func_d_d("fabs", xfabs);
    func_d_d("trunc", xtrunc);
    func_d_d("floor", xfloor);
    func_d_d("ceil", xceil);
    func_d_d("round", xround);
    func_d_d("rint", xrint);
    func_d_d("frfrexp", xfrfrexp);
    func_d_d("tgamma_u1", xtgamma_u1);
    func_d_d("lgamma_u1", xlgamma_u1);
    func_d_d("erf_u1", xerf_u1);
    func_d_d("erfc_u15", xerfc_u15);

    func_d2_d("sincos", xsincos);
    func_d2_d("sincos_u1", xsincos_u1);
    func_d2_d("sincospi_u35", xsincospi_u35);
    func_d2_d("sincospi_u05", xsincospi_u05);
    func_d2_d("modf", xmodf);

    func_d_d_d("pow", xpow);
    func_d_d_d("atan2", xatan2);
    func_d_d_d("atan2_u1", xatan2_u1);
    func_d_d_d("hypot_u05", xhypot_u05);
    func_d_d_d("hypot_u35", xhypot_u35);
    func_d_d_d("copysign", xcopysign);
    func_d_d_d("fmax", xfmax);
    func_d_d_d("fmin", xfmin);
    func_d_d_d("fdim", xfdim);
    func_d_d_d("nextafter", xnextafter);
    func_d_d_d("fmod", xfmod);
    func_d_d_d("remainder", xremainder);

    func_d_d_i("ldexp", xldexp);
    func_i_d("ilogb", xilogb);
    func_i_d("expfrexp", xexpfrexp);

    //

    func_f_f("sinf", xsinf);
    func_f_f("cosf", xcosf);
    func_f_f("tanf", xtanf);
    func_f_f("asinf", xasinf);
    func_f_f("acosf", xacosf);
    func_f_f("atanf", xatanf);
    func_f_f("logf", xlogf);
    func_f_f("expf", xexpf);

    func_f_f("sqrtf", xsqrtf);
    func_f_f("sqrtf_u05", xsqrtf_u05);
    func_f_f("sqrtf_u35", xsqrtf_u35);
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
  }

  return 0;
}
