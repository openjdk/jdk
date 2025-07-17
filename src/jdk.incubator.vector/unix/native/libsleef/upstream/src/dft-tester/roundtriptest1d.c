//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <assert.h>
#include <time.h>

#include <math.h>
#include <complex.h>

#include "sleef.h"
#include "sleefdft.h"

#ifndef MODE
#define MODE (SLEEF_MODE_DEBUG | SLEEF_MODE_VERBOSE)
#endif

#if BASETYPEID == 1
#define THRES 1e-30
#define SleefDFT_init SleefDFT_double_init1d
#define SleefDFT_execute SleefDFT_double_execute
typedef double real;
#elif BASETYPEID == 2
#define THRES 1e-13
#define SleefDFT_init SleefDFT_float_init1d
#define SleefDFT_execute SleefDFT_float_execute
typedef float real;
#else
#error BASETYPEID not set
#endif

static double squ(double x) { return x * x; }

// complex transforms
double check_c(int n) {
  struct SleefDFT *p;

  real *sx = (real *)Sleef_malloc(n*2 * sizeof(real));
  real *sy = (real *)Sleef_malloc(n*2 * sizeof(real));
  real *sz = (real *)Sleef_malloc(n*2 * sizeof(real));

  for(int i=0;i<n*2;i++) sx[i] = (real)(2.0 * (rand() / (double)RAND_MAX) - 1);

  //

  p = SleefDFT_init(n, NULL, NULL, MODE);

  if (p == NULL) {
    printf("SleefDFT initialization failed\n");
    exit(-1);
  }

  SleefDFT_execute(p, sx, sy);
  SleefDFT_dispose(p);

  //

  p = SleefDFT_init(n, NULL, NULL, MODE | SLEEF_MODE_BACKWARD);

  if (p == NULL) {
    printf("SleefDFT initialization failed\n");
    exit(-1);
  }

  SleefDFT_execute(p, sy, sz);
  SleefDFT_dispose(p);

  //

  double rmsn = 0, rmsd = 0, scale = 1 / (double)n;

  for(int i=0;i<n;i++) {
    rmsn += squ(scale * sz[i*2+0] - sx[i*2+0]) + squ(scale * sz[i*2+1] - sx[i*2+1]);
    rmsd += squ(                    sx[i*2+0]) + squ(                    sx[i*2+1]);
  }

  //

  Sleef_free(sx);
  Sleef_free(sy);
  Sleef_free(sz);

  //

  return rmsn / rmsd;
}

// real transforms
double check_r(int n) {
  struct SleefDFT *p;

  real *sx = (real *)Sleef_malloc(n * sizeof(real));
  real *sy = (real *)Sleef_malloc((n/2+1)*sizeof(real)*2);
  real *sz = (real *)Sleef_malloc(n * sizeof(real));

  for(int i=0;i<n;i++) sx[i] = (real)(2.0 * (rand() / (double)RAND_MAX) - 1);

  //

  p = SleefDFT_init(n, NULL, NULL, SLEEF_MODE_REAL | MODE);

  if (p == NULL) {
    printf("SleefDFT initialization failed\n");
    return 0;
  }

  SleefDFT_execute(p, sx, sy);
  SleefDFT_dispose(p);

  //

  p = SleefDFT_init(n, NULL, NULL, SLEEF_MODE_REAL | SLEEF_MODE_BACKWARD | MODE);

  if (p == NULL) {
    printf("SleefDFT initialization failed\n");
    return 0;
  }

  SleefDFT_execute(p, sy, sz);
  SleefDFT_dispose(p);

  //

  double rmsn = 0, rmsd = 0, scale = 1 / (double)n;

  for(int i=0;i<n;i++) {
    rmsn += squ(scale * sz[i] - sx[i]);
    rmsd += squ(                sx[i]);
  }

  //

  Sleef_free(sx);
  Sleef_free(sy);
  Sleef_free(sz);

  //

  return rmsn / rmsd;
}

int main(int argc, char **argv) {
  if (argc < 2) {
    fprintf(stderr, "%s <log2n> [<nloop>]\n", argv[0]);
    exit(-1);
  }

  const int n = 1 << atoi(argv[1]);
  const int nloop = argc >= 3 ? atoi(argv[2]) : 1;

  srand((unsigned int)time(NULL));

  SleefDFT_setPlanFilePath(NULL, NULL, SLEEF_PLAN_RESET | SLEEF_PLAN_READONLY);

  //

  int success = 1;
  double e;

  for(int i=0;(nloop < 0 || i < nloop) && success;i++) {
    e = check_c(n);
    success = success && e < THRES;
    printf("complex : %s (%g)\n", e < THRES ? "OK" : "NG", e);
    e = check_r(n);
    success = success && e < THRES;
    printf("real    : %s (%g)\n", e < THRES ? "OK" : "NG", e);
  }

  exit(!success);
}
