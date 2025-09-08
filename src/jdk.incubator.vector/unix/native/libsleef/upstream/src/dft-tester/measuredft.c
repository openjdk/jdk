//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#define _DEFAULT_SOURCE
#define _XOPEN_SOURCE 700

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <assert.h>
#include <time.h>
#include <unistd.h>
#include <sys/time.h>

#include <math.h>
#include <complex.h>

#include "sleef.h"
#include "sleefdft.h"

static uint64_t gettime() {
  struct timespec tp;
  clock_gettime(CLOCK_MONOTONIC, &tp);
  return (uint64_t)tp.tv_sec * 1000000000 + ((uint64_t)tp.tv_nsec);
}

int mode[] = { SLEEF_MODE_MEASURE | SLEEF_MODE_NO_MT, SLEEF_MODE_MEASURE};

#define ENABLE_SP
//#define ROUNDTRIP
#define REPEAT 2
//#define ENABLE_SLEEP
//#define WARMUP

int main(int argc, char **argv) {
  int start = 1, end = 18;
  if (argc > 1) start = atoi(argv[1]);
  if (argc > 2) end = atoi(argv[2]);

  double *din  = (double *)Sleef_malloc((1 << 18)*2 * sizeof(double));
  double *dout = (double *)Sleef_malloc((1 << 18)*2 * sizeof(double));
  float *sin  = (float *)Sleef_malloc((1 << 18)*2 * sizeof(float));
  float *sout = (float *)Sleef_malloc((1 << 18)*2 * sizeof(float));

  SleefDFT_setPlanFilePath(NULL, NULL, SLEEF_PLAN_RESET | SLEEF_PLAN_READONLY);

  for(int log2n=start;log2n<=end;log2n++) {
    const int n = 1 << log2n;
    int64_t niter = (int64_t)(1000000000.0 / REPEAT / n / log2n);

    printf("%d ", n);

    for(int m=0;m<2;m++) {
#ifdef ENABLE_SLEEP
      sleep(1);
#endif

      struct SleefDFT *pf = SleefDFT_double_init1d(n, NULL, NULL, mode[m]);
#ifdef ROUNDTRIP
      struct SleefDFT *pb = SleefDFT_double_init1d(n, NULL, NULL, mode[m] | SLEEF_MODE_BACKWARD);
#endif

      for(int i=0;i<n*2;i++) {
        din[i] = 0;
      }

#ifdef ENABLE_SLEEP
      sleep(1);
#endif

#ifdef WARMUP
      for(int64_t i=0;i<niter/2;i++) {
        SleefDFT_double_execute(pf, din, dout);
#ifdef ROUNDTRIP
        SleefDFT_double_execute(pb, dout, din);
#endif
      }
#endif

      uint64_t best = 1LL << 62;

      //printf("\n");
      for(int rep=0;rep<REPEAT;rep++) {
        uint64_t tm0 = gettime();
        for(int64_t i=0;i<niter;i++) {
          SleefDFT_double_execute(pf, din, dout);
#ifdef ROUNDTRIP
          SleefDFT_double_execute(pb, dout, din);
#endif
        }
        uint64_t tm1 = gettime();
        if (tm1 - tm0 < best) best = tm1 - tm0;
        //printf("%g\n", (double)(tm1 - tm0));
      }

      SleefDFT_dispose(pf);
#ifdef ROUNDTRIP
      SleefDFT_dispose(pb);
#endif

      double timeus = best / ((double)niter * 1000);

#ifdef ROUNDTRIP
      double mflops = 10 * n * log2n / timeus;
#else
      double mflops = 5 * n * log2n / timeus;
#endif

      printf("%g ", mflops);
    }

#ifdef ENABLE_SP
    for(int m=0;m<2;m++) {
#ifdef ENABLE_SLEEP
      sleep(1);
#endif

      struct SleefDFT *pf = SleefDFT_float_init1d(n, NULL, NULL, mode[m]);
#ifdef ROUNDTRIP
      struct SleefDFT *pb = SleefDFT_float_init1d(n, NULL, NULL, mode[m] | SLEEF_MODE_BACKWARD);
#endif

      for(int i=0;i<n*2;i++) {
        sin[i] = 0;
      }

#ifdef ENABLE_SLEEP
      sleep(1);
#endif

#ifdef WARMUP
      for(int64_t i=0;i<niter/2;i++) {
        SleefDFT_float_execute(pf, sin, sout);
#ifdef OUNDTRIP
        SleefDFT_float_execute(pb, sout, sin);
#endif
      }
#endif

      uint64_t best = 1LL << 62;

      for(int rep=0;rep<REPEAT;rep++) {
        uint64_t tm0 = gettime();
        for(int64_t i=0;i<niter;i++) {
          SleefDFT_float_execute(pf, sin, sout);
#ifdef ROUNDTRIP
          SleefDFT_float_execute(pb, sout, sin);
#endif
        }
        uint64_t tm1 = gettime();
        if (tm1 - tm0 < best) best = tm1 - tm0;
      }

      SleefDFT_dispose(pf);
#ifdef ROUNDTRIP
      SleefDFT_dispose(pb);
#endif

      double timeus = best / ((double)niter * 1000);

#ifdef ROUNDTRIP
      double mflops = 10 * n * log2n / timeus;
#else
      double mflops = 5 * n * log2n / timeus;
#endif

      printf("%g ", mflops);
    }
#endif

    printf("\n");
  }
}
