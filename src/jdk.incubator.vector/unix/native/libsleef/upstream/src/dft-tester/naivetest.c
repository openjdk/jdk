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
#include "misc.h"

#ifndef MODE
#define MODE SLEEF_MODE_DEBUG
#endif

#define THRES 1e-4

#if BASETYPEID == 1
#define SleefDFT_init SleefDFT_double_init1d
#define SleefDFT_execute SleefDFT_double_execute
typedef double real;

typedef double complex cmpl;

cmpl omega(double n, double kn) {
  return cexp((-2 * M_PIl * _Complex_I / n) * kn);
}
#elif BASETYPEID == 2
#define SleefDFT_init SleefDFT_float_init1d
#define SleefDFT_execute SleefDFT_float_execute
typedef float real;

typedef double complex cmpl;

cmpl omega(double n, double kn) {
  return cexp((-2 * M_PIl * _Complex_I / n) * kn);
}
#elif BASETYPEID == 3
#define SleefDFT_init SleefDFT_longdouble_init1d
#define SleefDFT_execute SleefDFT_longdouble_execute
typedef double real;

typedef double complex cmpl;

cmpl omega(double n, double kn) {
  return cexp((-2 * M_PIl * _Complex_I / n) * kn);
}
#elif BASETYPEID == 4
#include <quadmath.h>

#define SleefDFT_init SleefDFT_quad_init1d
#define SleefDFT_execute SleefDFT_quad_execute
typedef Sleef_quad real;

typedef double complex cmpl;

cmpl omega(double n, double kn) {
  return cexp((-2 * M_PIl * _Complex_I / n) * kn);
}
#else
#error No BASETYPEID specified
#endif

void forward(cmpl *ts, cmpl *fs, int len) {
  int k, n;

  for(k=0;k<len;k++) {
    fs[k] = 0;

    for(n=0;n<len;n++) {
      fs[k] += ts[n] * omega(len, n*k);
    }
  }
}

void backward(cmpl *fs, cmpl *ts, int len) {
  int k, n;

  for(k=0;k<len;k++) {
    ts[k] = 0;

    for(n=0;n<len;n++) {
      ts[k] += fs[n] * omega(-len, n*k);
    }
  }
}

// complex forward
int check_cf(int n) {
  int i;

  real *sx = (real *)Sleef_malloc(n*2 * sizeof(real));
  real *sy = (real *)Sleef_malloc(n*2 * sizeof(real));

  cmpl *ts = (cmpl *)malloc(sizeof(cmpl)*n);
  cmpl *fs = (cmpl *)malloc(sizeof(cmpl)*n);

  //

  for(i=0;i<n;i++) {
    ts[i] = 0.5 * ((2.0 * (rand() / (double)RAND_MAX) - 1) + (2.0 * (rand() / (double)RAND_MAX) - 1) * _Complex_I);
    sx[(i*2+0)] = creal(ts[i]);
    sx[(i*2+1)] = cimag(ts[i]);
  }

  //

  forward(ts, fs, n);

  struct SleefDFT *p = SleefDFT_init(n, NULL, NULL, MODE | SLEEF_MODE_VERBOSE);

  if (p == NULL) {
    printf("SleefDFT initialization failed\n");
    return 0;
  }

  SleefDFT_execute(p, sx, sy);

  //

  int success = 1;
  double rmsn = 0, rmsd = 0;

  for(i=0;i<n;i++) {
    if ((fabs(sy[(i*2+0)] - creal(fs[i])) > THRES) ||
        (fabs(sy[(i*2+1)] - cimag(fs[i])) > THRES)) {
      success = 0;
    }

    double t;
    t = (sy[(i*2+0)] - creal(fs[i]));
    rmsn += t*t;
    t = (sy[(i*2+1)] - cimag(fs[i]));
    rmsn += t*t;
    rmsd += creal(fs[i]) * creal(fs[i]) + cimag(fs[i]) * cimag(fs[i]);
  }

  //

  free(fs);
  free(ts);

  Sleef_free(sx);
  Sleef_free(sy);
  SleefDFT_dispose(p);

  //

  return success;
}

// complex backward
int check_cb(int n) {
  int i;

  real *sx = (real *)Sleef_malloc(sizeof(real)*n*2);
  real *sy = (real *)Sleef_malloc(sizeof(real)*n*2);

  cmpl *ts = (cmpl *)malloc(sizeof(cmpl)*n);
  cmpl *fs = (cmpl *)malloc(sizeof(cmpl)*n);

  //

  for(i=0;i<n;i++) {
    fs[i] = (2.0 * (rand() / (double)RAND_MAX) - 1) + (2.0 * (rand() / (double)RAND_MAX) - 1) * _Complex_I;
    sx[(i*2+0)] = creal(fs[i]);
    sx[(i*2+1)] = cimag(fs[i]);
  }

  backward(fs, ts, n);

  struct SleefDFT *p = SleefDFT_init(n, NULL, NULL, SLEEF_MODE_BACKWARD | MODE);

  if (p == NULL) {
    printf("SleefDFT initialization failed\n");
    return 0;
  }

  SleefDFT_execute(p, sx, sy);

  //

  int success = 1;

  for(i=0;i<n;i++) {
    if ((fabs(sy[(i*2+0)] - creal(ts[i])) > THRES) ||
        (fabs(sy[(i*2+1)] - cimag(ts[i])) > THRES)) {
      success = 0;
    }
  }

  //

  free(fs);
  free(ts);

  Sleef_free(sx);
  Sleef_free(sy);
  SleefDFT_dispose(p);

  //

  return success;
}

// real forward
int check_rf(int n) {
  int i;

  real *sx = (real *)Sleef_malloc(n * sizeof(real));
  real *sy = (real *)Sleef_malloc((n/2+1)*sizeof(real)*2);

  cmpl *ts = (cmpl *)malloc(sizeof(cmpl)*n);
  cmpl *fs = (cmpl *)malloc(sizeof(cmpl)*n);

  //

  for(i=0;i<n;i++) {
    ts[i] = (2.0 * (rand() / (double)RAND_MAX) - 1);
    sx[i] = creal(ts[i]);
  }

  //

  forward(ts, fs, n);

  struct SleefDFT *p = SleefDFT_init(n, NULL, NULL, SLEEF_MODE_NO_MT | SLEEF_MODE_REAL | MODE);

  if (p == NULL) {
    printf("SleefDFT initialization failed\n");
    return 0;
  }

  SleefDFT_execute(p, sx, sy);

  //

  int success = 1;

  for(i=0;i<n/2+1;i++) {
    if (fabs(sy[(2*i+0)] - creal(fs[i])) > THRES) success = 0;
    if (fabs(sy[(2*i+1)] - cimag(fs[i])) > THRES) success = 0;
  }

  //

  free(fs);
  free(ts);

  Sleef_free(sx);
  Sleef_free(sy);
  SleefDFT_dispose(p);

  //

  return success;
}

// real backward
int check_rb(int n) {
  int i;

  cmpl *ts = (cmpl *)malloc(sizeof(cmpl)*n);
  cmpl *fs = (cmpl *)malloc(sizeof(cmpl)*n);

  //

  for(i=0;i<n/2;i++) {
    if (i == 0) {
      fs[0  ] = (2.0 * (rand() / (double)RAND_MAX) - 1);
      fs[n/2] = (2.0 * (rand() / (double)RAND_MAX) - 1);
    } else {
      fs[i  ] = (2.0 * (rand() / (double)RAND_MAX) - 1) + (2.0 * (rand() / (double)RAND_MAX) - 1) * _Complex_I;
      fs[n-i] = conj(fs[i]);
    }
  }

  real *sx = (real *)Sleef_malloc((n/2+1) * sizeof(real)*2);
  real *sy = (real *)Sleef_malloc(sizeof(real)*n);

  for(i=0;i<n/2+1;i++) {
    sx[2*i+0] = creal(fs[i]);
    sx[2*i+1] = cimag(fs[i]);
  }

  //

  backward(fs, ts, n);

  struct SleefDFT *p = SleefDFT_init(n, NULL, NULL, SLEEF_MODE_REAL | SLEEF_MODE_BACKWARD | MODE);

  if (p == NULL) {
    printf("SleefDFT initialization failed\n");
    return 0;
  }

  SleefDFT_execute(p, sx, sy);

  //

  int success = 1;

  for(i=0;i<n;i++) {
    if (fabs(cimag(ts[i])) > THRES) {
      success = 0;
    }

    if ((fabs(sy[i] - creal(ts[i])) > THRES)) {
      success = 0;
    }
  }

  //

  free(fs);
  free(ts);

  Sleef_free(sx);
  Sleef_free(sy);
  SleefDFT_dispose(p);

  //

  return success;
}

int check_arf(int n) {
  int i;

  real *sx = (real *)Sleef_malloc(n * sizeof(real));
  real *sy = (real *)Sleef_malloc(n * sizeof(real));

  cmpl *ts = (cmpl *)malloc(sizeof(cmpl)*n);
  cmpl *fs = (cmpl *)malloc(sizeof(cmpl)*n);

  //

  for(i=0;i<n;i++) {
    ts[i] = 2 * (rand() / (real)RAND_MAX) - 1;
    sx[i] = creal(ts[i]);
  }

  //

  backward(ts, fs, n);

  struct SleefDFT *p = SleefDFT_init(n, NULL, NULL, SLEEF_MODE_REAL | SLEEF_MODE_ALT | MODE);

  if (p == NULL) {
    printf("SleefDFT initialization failed\n");
    return 0;
  }

  SleefDFT_execute(p, sx, sy);

  //

  int success = 1;

  for(i=0;i<n/2;i++) {
    if (i == 0) {
      if (fabs(sy[(2*0+0)] - creal(fs[0  ])) > THRES) success = 0;
      if (fabs(sy[(2*0+1)] - creal(fs[n/2])) > THRES) success = 0;
    } else {
      if (fabs(sy[(2*i+0)] - creal(fs[i])) > THRES) success = 0;
      if (fabs(sy[(2*i+1)] - cimag(fs[i])) > THRES) success = 0;
    }
  }

  //

  Sleef_free(sx);
  Sleef_free(sy);
  SleefDFT_dispose(p);

  //

  return success;
}

int check_arb(int n) {
  int i;

  real *sx = (real *)Sleef_malloc(n * sizeof(real));
  real *sy = (real *)Sleef_malloc(n * sizeof(real));

  cmpl *ts = (cmpl *)malloc(sizeof(cmpl)*n);
  cmpl *fs = (cmpl *)malloc(sizeof(cmpl)*n);

  //

  for(i=0;i<n/2;i++) {
    if (i == 0) {
      fs[0  ] = (2.0 * (rand() / (double)RAND_MAX) - 1);
      fs[n/2] = (2.0 * (rand() / (double)RAND_MAX) - 1);
    } else {
      fs[i  ] = (2.0 * (rand() / (double)RAND_MAX) - 1) + (2.0 * (rand() / (double)RAND_MAX) - 1) * _Complex_I;
      fs[n-i] = conj(fs[i]);
    }
  }

  for(i=0;i<n/2;i++) {
    if (i == 0) {
      sx[2*0+0] = creal(fs[0  ]);
      sx[2*0+1] = creal(fs[n/2]);
    } else {
      sx[2*i+0] = creal(fs[i]);
      sx[2*i+1] = cimag(fs[i]);
    }
  }

  //

  forward(fs, ts, n);

  struct SleefDFT *p = SleefDFT_init(n, NULL, NULL, SLEEF_MODE_REAL | SLEEF_MODE_BACKWARD | SLEEF_MODE_ALT | MODE);

  if (p == NULL) {
    printf("SleefDFT initialization failed\n");
    return 0;
  }

  SleefDFT_execute(p, sx, sy);

  //

  int success = 1;

  for(i=0;i<n;i++) {
    if (fabs(cimag(ts[i])) > THRES) {
      success = 0;
    }

    if ((fabs(sy[i]*2 - creal(ts[i])) > THRES)) {
      success = 0;
    }
  }

  //

  free(fs);
  free(ts);

  Sleef_free(sx);
  Sleef_free(sy);
  SleefDFT_dispose(p);

  //

  return success;
}

int main(int argc, char **argv) {
  if (argc != 2) {
    fprintf(stderr, "%s <log2n>\n", argv[0]);
    exit(-1);
  }

  const int n = 1 << atoi(argv[1]);

  srand((unsigned int)time(NULL));

  SleefDFT_setPlanFilePath(NULL, NULL, SLEEF_PLAN_RESET | SLEEF_PLAN_READONLY);

  //

  int success = 1;

  printf("complex  forward   : %s\n", (success &= check_cf(n))  ? "OK" : "NG");
  printf("complex  backward  : %s\n", (success &= check_cb(n))  ? "OK" : "NG");
  printf("real     forward   : %s\n", (success &= check_rf(n))  ? "OK" : "NG");
  printf("real     backward  : %s\n", (success &= check_rb(n))  ? "OK" : "NG");
  printf("real alt forward   : %s\n", (success &= check_arf(n)) ? "OK" : "NG");
  printf("real alt backward  : %s\n", (success &= check_arb(n)) ? "OK" : "NG");

  exit(!success);
}
