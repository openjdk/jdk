//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
#include <float.h>
#include <inttypes.h>
#include <stdarg.h>
#include <ctype.h>
#include <assert.h>
#include <cuda.h>

#include "sleefquadinline_cuda.h"
#include "sleefquadinline_purec_scalar.h"

#define STDIN_FILENO 0

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

//

__global__ void xaddq_u05(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_addq1_u05cuda(*a0, *a1); }
__global__ void xsubq_u05(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_subq1_u05cuda(*a0, *a1); }
__global__ void xmulq_u05(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_mulq1_u05cuda(*a0, *a1); }
__global__ void xdivq_u05(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_divq1_u05cuda(*a0, *a1); }
__global__ void xnegq(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_negq1_cuda(*a0); }

__global__ void xicmpltq(int *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_icmpltq1_cuda(*a0, *a1); }
__global__ void xicmpgtq(int *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_icmpgtq1_cuda(*a0, *a1); }
__global__ void xicmpleq(int *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_icmpleq1_cuda(*a0, *a1); }
__global__ void xicmpgeq(int *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_icmpgeq1_cuda(*a0, *a1); }
__global__ void xicmpeqq(int *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_icmpeqq1_cuda(*a0, *a1); }
__global__ void xicmpneq(int *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_icmpneq1_cuda(*a0, *a1); }
__global__ void xicmpq(int *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_icmpq1_cuda(*a0, *a1); }
__global__ void xiunordq(int *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_iunordq1_cuda(*a0, *a1); }

__global__ void xcast_from_doubleq(Sleef_quadx1 *r0, double *d0) { *r0 = Sleef_cast_from_doubleq1_cuda(*d0); }
__global__ void xcast_to_doubleq(double *r0, Sleef_quadx1 *a0) { *r0 = Sleef_cast_to_doubleq1_cuda(*a0); }
__global__ void xcast_from_int64q(Sleef_quadx1 *r0, int64_t *i0) { *r0 = Sleef_cast_from_int64q1_cuda(*i0); }
__global__ void xcast_to_int64q(int64_t *r0, Sleef_quadx1 *a0) { *r0 = Sleef_cast_to_int64q1_cuda(*a0); }
__global__ void xcast_from_uint64q(Sleef_quadx1 *r0, uint64_t *u0) { *r0 = Sleef_cast_from_uint64q1_cuda(*u0); }
__global__ void xcast_to_uint64q(uint64_t *r0, Sleef_quadx1 *a0) { *r0 = Sleef_cast_to_uint64q1_cuda(*a0); }

__global__ void xsqrtq_u05(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_sqrtq1_u05cuda(*a0); }
__global__ void xcbrtq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_cbrtq1_u10cuda(*a0); }
__global__ void xsinq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_sinq1_u10cuda(*a0); }
__global__ void xcosq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_cosq1_u10cuda(*a0); }
__global__ void xtanq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_tanq1_u10cuda(*a0); }
__global__ void xasinq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_asinq1_u10cuda(*a0); }
__global__ void xacosq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_acosq1_u10cuda(*a0); }
__global__ void xatanq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_atanq1_u10cuda(*a0); }
__global__ void xatan2q_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_atan2q1_u10cuda(*a0, *a1); }
__global__ void xexpq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_expq1_u10cuda(*a0); }
__global__ void xexp2q_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_exp2q1_u10cuda(*a0); }
__global__ void xexp10q_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_exp10q1_u10cuda(*a0); }
__global__ void xexpm1q_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_expm1q1_u10cuda(*a0); }
__global__ void xlogq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_logq1_u10cuda(*a0); }
__global__ void xlog2q_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_log2q1_u10cuda(*a0); }
__global__ void xlog10q_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_log10q1_u10cuda(*a0); }
__global__ void xlog1pq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_log1pq1_u10cuda(*a0); }
__global__ void xpowq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_powq1_u10cuda(*a0, *a1); }
__global__ void xsinhq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_sinhq1_u10cuda(*a0); }
__global__ void xcoshq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_coshq1_u10cuda(*a0); }
__global__ void xtanhq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_tanhq1_u10cuda(*a0); }
__global__ void xasinhq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_asinhq1_u10cuda(*a0); }
__global__ void xacoshq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_acoshq1_u10cuda(*a0); }
__global__ void xatanhq_u10(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_atanhq1_u10cuda(*a0); }

__global__ void xfabsq(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_fabsq1_cuda(*a0); }
__global__ void xcopysignq(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_copysignq1_cuda(*a0, *a1); }
__global__ void xfmaxq(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_fmaxq1_cuda(*a0, *a1); }
__global__ void xfminq(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_fminq1_cuda(*a0, *a1); }
__global__ void xfdimq_u05(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_fdimq1_u05cuda(*a0, *a1); }
__global__ void xfmodq(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_fmodq1_cuda(*a0, *a1); }
__global__ void xremainderq(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_remainderq1_cuda(*a0, *a1); }
__global__ void xfrexpq(Sleef_quadx1 *r, Sleef_quadx1 *a0, int *i0) { *r = Sleef_frexpq1_cuda(*a0, i0); }
__global__ void xmodfq(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_modfq1_cuda(*a0, a1); }
__global__ void xfmaq_u05(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1, Sleef_quadx1 *a2) { *r = Sleef_fmaq1_u05cuda(*a0, *a1, *a2); }
__global__ void xhypotq_u05(Sleef_quadx1 *r, Sleef_quadx1 *a0, Sleef_quadx1 *a1) { *r = Sleef_hypotq1_u05cuda(*a0, *a1); }
__global__ void xilogbq(int *r, Sleef_quadx1 *a0) { *r = Sleef_ilogbq1_cuda(*a0); }
__global__ void xldexpq(Sleef_quadx1 *r, Sleef_quadx1 *a0, int *i0) { *r = Sleef_ldexpq1_cuda(*a0, *i0); }

__global__ void xtruncq(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_truncq1_cuda(*a0); }
__global__ void xfloorq(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_floorq1_cuda(*a0); }
__global__ void xceilq(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_ceilq1_cuda(*a0); }
__global__ void xroundq(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_roundq1_cuda(*a0); }
__global__ void xrintq(Sleef_quadx1 *r, Sleef_quadx1 *a0) { *r = Sleef_rintq1_cuda(*a0); }

//

typedef union {
  Sleef_quad q;
  struct {
    uint64_t l, h;
  };
} cnv128;

#define BUFSIZE 1024

#define func_q_q(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      *a0 = Sleef_setq1_cuda(*a0, 0, c0.q);				\
      funcName<<<1, 1>>>(r, a0);					\
      cudaDeviceSynchronize();						\
      c0.q = Sleef_getq1_cuda(*r, 0);					\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);			\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_q_q_q(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0, c1;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64 " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l, &c1.h, &c1.l); \
      *a0 = Sleef_setq1_cuda(*a0, 0, c0.q);				\
      *a1 = Sleef_setq1_cuda(*a1, 0, c1.q);				\
      funcName<<<1, 1>>>(r, a0, a1);					\
      cudaDeviceSynchronize();						\
      c0.q = Sleef_getq1_cuda(*r, 0);					\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);			\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_q_q_q_q(funcStr, funcName) {				\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0, c1, c2;						\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64 " %" PRIx64 ":%" PRIx64 " %" PRIx64 ":%" PRIx64, \
	     &c0.h, &c0.l, &c1.h, &c1.l, &c2.h, &c2.l);			\
      *a0 = Sleef_setq1_cuda(*a0, 0, c0.q);				\
      *a1 = Sleef_setq1_cuda(*a1, 0, c1.q);				\
      *a2 = Sleef_setq1_cuda(*a2, 0, c2.q);				\
      funcName<<<1, 1>>>(r, a0, a1, a2);				\
      cudaDeviceSynchronize();						\
      c0.q = Sleef_getq1_cuda(*r, 0);					\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);			\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_i_q(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l); \
      *a0 = Sleef_setq1_cuda(*a0, 0, c0.q);				\
      funcName<<<1, 1>>>(i0, a0);					\
      cudaDeviceSynchronize();						\
      printf("%d\n", *i0);						\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_i_q_q(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0, c1;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64 " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l, &c1.h, &c1.l); \
      *a0 = Sleef_setq1_cuda(*a0, 0, c0.q);				\
      *a1 = Sleef_setq1_cuda(*a1, 0, c1.q);				\
      funcName<<<1, 1>>>(i0, a0, a1);					\
      cudaDeviceSynchronize();						\
      printf("%d\n", *i0);						\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_q_q_i(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0;							\
      int k;								\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64 " %d", &c0.h, &c0.l, &k); \
      *a0 = Sleef_setq1_cuda(*a0, 0, c0.q);				\
      *i0 = k;								\
      funcName<<<1, 1>>>(r, a0, i0);					\
      cudaDeviceSynchronize();						\
      c0.q = Sleef_getq1_cuda(*r, 0);					\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);			\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_d_q(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      *a0 = Sleef_setq1_cuda(*a0, 0, c0.q);				\
      funcName<<<1, 1>>>(d0, a0);					\
      cudaDeviceSynchronize();						\
      printf("%" PRIx64 "\n", d2u(*d0));				\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_q_d(funcStr, funcName) {			\
    while (startsWith(buf, funcStr " ")) {		\
      sentinel = 0;					\
      uint64_t u;					\
      sscanf(buf, funcStr " %" PRIx64, &u);		\
      *d0 = u2d(u);					\
      funcName<<<1, 1>>>(r, d0);			\
      cudaDeviceSynchronize();				\
      cnv128 c0;					\
      c0.q = Sleef_getq1_cuda(*r, 0);			\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);	\
      fflush(stdout);					\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;	\
    }							\
  }

#define func_i64_q(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      *a0 = Sleef_setq1_cuda(*a0, 0, c0.q);				\
      funcName<<<1, 1>>>(i64, a0);					\
      cudaDeviceSynchronize();						\
      printf("%" PRIx64 "\n", *i64);					\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_q_i64(funcStr, funcName) {			\
    while (startsWith(buf, funcStr " ")) {		\
      sentinel = 0;					\
      sscanf(buf, funcStr " %" PRIx64, i64);		\
      funcName<<<1, 1>>>(r, i64);			\
      cudaDeviceSynchronize();				\
      cnv128 c0;					\
      c0.q = Sleef_getq1_cuda(*r, 0);			\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);	\
      fflush(stdout);					\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;	\
    }							\
  }

#define func_u64_q(funcStr, funcName) {					\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      *a0 = Sleef_setq1_cuda(*a0, 0, c0.q);				\
      funcName<<<1, 1>>>(u64, a0);					\
      cudaDeviceSynchronize();						\
      printf("%" PRIx64 "\n", *u64);					\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_q_u64(funcStr, funcName) {			\
    while (startsWith(buf, funcStr " ")) {		\
      sentinel = 0;					\
      sscanf(buf, funcStr " %" PRIx64, u64);		\
      funcName<<<1, 1>>>(r, u64);			\
      cudaDeviceSynchronize();				\
      cnv128 c0;					\
      c0.q = Sleef_getq1_cuda(*r, 0);			\
      printf("%" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l);	\
      fflush(stdout);					\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;	\
    }							\
  }

#define func_q_q_pi(funcStr, funcName) {				\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      *a0 = Sleef_setq1_cuda(*a0, 0, c0.q);				\
      funcName<<<1, 1>>>(r, a0, i0);					\
      cudaDeviceSynchronize();						\
      c0.q = Sleef_getq1_cuda(*r, 0);					\
      printf("%" PRIx64 ":%" PRIx64 " %d\n", c0.h, c0.l, *i0);		\
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

#define func_q_q_pq(funcStr, funcName) {				\
    while (startsWith(buf, funcStr " ")) {				\
      sentinel = 0;							\
      cnv128 c0, c1;							\
      sscanf(buf, funcStr " %" PRIx64 ":%" PRIx64, &c0.h, &c0.l);	\
      *a0 = Sleef_setq1_cuda(*a0, 0, c0.q);				\
      funcName<<<1, 1>>>(r, a0, a1);					\
      cudaDeviceSynchronize();						\
      c0.q = Sleef_getq1_cuda(*r, 0);					\
      c1.q = Sleef_getq1_cuda(*a1, 0);					\
      printf("%" PRIx64 ":%" PRIx64 " %" PRIx64 ":%" PRIx64 "\n", c0.h, c0.l, c1.h, c1.l); \
      fflush(stdout);							\
      if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;			\
    }									\
  }

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

  Sleef_quadx1 *r, *a0, *a1, *a2;
  double *d0;
  int *i0;
  int64_t *i64;
  uint64_t *u64;
  cudaMallocManaged(&r ,  1*sizeof(Sleef_quadx1));
  cudaMallocManaged(&a0,  1*sizeof(Sleef_quadx1));
  cudaMallocManaged(&a1,  1*sizeof(Sleef_quadx1));
  cudaMallocManaged(&a2,  1*sizeof(Sleef_quadx1));
  cudaMallocManaged(&d0,  1*sizeof(double));
  cudaMallocManaged(&i0,  1*sizeof(int));
  cudaMallocManaged(&i64, 1*sizeof(int64_t));
  cudaMallocManaged(&u64, 1*sizeof(uint64_t));

  //

  printf("1\n");
  fflush(stdout);

  //

  {
    *a0 = Sleef_setq1_cuda(*a0, 0, SLEEF_M_PIq);
    *a1 = Sleef_setq1_cuda(*a1, 0, Sleef_strtoq("2.718281828459045235360287471352662498", NULL));
    xmulq_u05<<<1, 1>>>(r, a0, a1);
    cudaDeviceSynchronize();
    Sleef_quad v0 = Sleef_getq1_cuda(*r, 0);
    if (Sleef_icmpneq1_purec(v0, sleef_q(+0x1114580b45d47LL, 0x49e6108579a2d0caULL, 3))) {
      fprintf(stderr, "Testing with Sleef_mulq1_u05cuda failed\n");
      exit(-1);
    }
  }

  //

  char buf[BUFSIZE];
  if (fgets(buf, BUFSIZE-1, stdin)) {}
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

    sentinel++;
  }

  //

  return 0;
}
