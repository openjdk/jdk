//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <time.h>

#include <math.h>
#include <complex.h>

#include "sleef.h"
#include "sleefdft.h"

#include <fftw3.h>

#ifndef MODE
#define MODE SLEEF_MODE_DEBUG
#endif

#if BASETYPEID == 1
#define THRES 1e-30
#define SleefDFT_init1d SleefDFT_double_init1d
#define SleefDFT_execute SleefDFT_double_execute
typedef double real;
#elif BASETYPEID == 2
#define THRES 1e-13
#define SleefDFT_init1d SleefDFT_float_init1d
#define SleefDFT_execute SleefDFT_float_execute
typedef float real;
#else
#error BASETYPEID not set
#endif

static double squ(double x) { return x * x; }

// complex forward
double check_cf(int n) {
  fftw_complex *in  = (fftw_complex*) fftw_malloc(sizeof(fftw_complex) * n);
  fftw_complex *out = (fftw_complex*) fftw_malloc(sizeof(fftw_complex) * n);
  fftw_plan w = fftw_plan_dft_1d(n, in, out, FFTW_FORWARD, FFTW_ESTIMATE);

  real *sx = (real *)Sleef_malloc(n*2*sizeof(real));
  real *sy = (real *)Sleef_malloc(n*2*sizeof(real));
  struct SleefDFT *p = SleefDFT_init1d(n, sx, sy, MODE);

  for(int i=0;i<n;i++) {
    real re = (2.0 * random() - 1) / (real)RAND_MAX;
    real im = (2.0 * random() - 1) / (real)RAND_MAX;
    sx[(i*2+0)] = re;
    sx[(i*2+1)] = im;
    in[i] = re + im * _Complex_I;
  }

  SleefDFT_execute(p, NULL, NULL);
  fftw_execute(w);

  double rmsn = 0, rmsd = 0;

  for(int i=0;i<n;i++) {
    rmsn += squ(sy[i*2+0] - creal(out[i])) + squ(sy[i*2+1] - cimag(out[i]));
    rmsd += squ(            creal(out[i])) + squ(            cimag(out[i]));
  }

  fftw_destroy_plan(w);
  fftw_free(in);
  fftw_free(out);

  Sleef_free(sx);
  Sleef_free(sy);
  SleefDFT_dispose(p);

  return rmsn / rmsd;
}

// complex backward
double check_cb(int n) {
  fftw_complex *in  = (fftw_complex*) fftw_malloc(sizeof(fftw_complex) * n);
  fftw_complex *out = (fftw_complex*) fftw_malloc(sizeof(fftw_complex) * n);
  fftw_plan w = fftw_plan_dft_1d(n, in, out, FFTW_BACKWARD, FFTW_ESTIMATE);

  real *sx = (real *)Sleef_malloc(n*2*sizeof(real));
  real *sy = (real *)Sleef_malloc(n*2*sizeof(real));
  struct SleefDFT *p = SleefDFT_init1d(n, sx, sy, SLEEF_MODE_BACKWARD | MODE);

  for(int i=0;i<n;i++) {
    real re = (2.0 * random() - 1) / (real)RAND_MAX;
    real im = (2.0 * random() - 1) / (real)RAND_MAX;
    sx[(i*2+0)] = re;
    sx[(i*2+1)] = im;
    in[i] = re + im * _Complex_I;
  }

  SleefDFT_execute(p, NULL, NULL);
  fftw_execute(w);

  double rmsn = 0, rmsd = 0;

  for(int i=0;i<n;i++) {
    rmsn += squ(sy[i*2+0] - creal(out[i])) + squ(sy[i*2+1] - cimag(out[i]));
    rmsd += squ(            creal(out[i])) + squ(            cimag(out[i]));
  }

  fftw_destroy_plan(w);
  fftw_free(in);
  fftw_free(out);

  Sleef_free(sx);
  Sleef_free(sy);
  SleefDFT_dispose(p);

  return rmsn / rmsd;
}

// real forward
double check_rf(int n) {
  double       *in  = (double *)      fftw_malloc(sizeof(double) * n);
  fftw_complex *out = (fftw_complex*) fftw_malloc(sizeof(fftw_complex) * (n/2+1));
  fftw_plan w       = fftw_plan_dft_r2c_1d(n, in, out, FFTW_ESTIMATE);

  real *sx = (real *)Sleef_malloc(n*sizeof(real));
  real *sy = (real *)Sleef_malloc((n/2+1)*sizeof(real)*2);
  struct SleefDFT *p = SleefDFT_init1d(n, sx, sy, SLEEF_MODE_REAL | MODE);

  for(int i=0;i<n;i++) {
    real re = (2.0 * random() - 1) / (real)RAND_MAX;
    sx[i] = re;
    in[i] = re;
  }

  SleefDFT_execute(p, NULL, NULL);
  fftw_execute(w);

  double rmsn = 0, rmsd = 0;

  for(int i=0;i<n/2+1;i++) {
    rmsn += squ(sy[i*2+0] - creal(out[i])) + squ(sy[i*2+1] - cimag(out[i]));
    rmsd += squ(            creal(out[i])) + squ(            cimag(out[i]));
  }

  fftw_destroy_plan(w);
  fftw_free(in);
  fftw_free(out);

  Sleef_free(sx);
  Sleef_free(sy);
  SleefDFT_dispose(p);

  return rmsn / rmsd;
}

// real backward
double check_rb(int n) {
  fftw_complex *in  = (fftw_complex*) fftw_malloc(sizeof(fftw_complex) * (n/2+1));
  double       *out = (double *)      fftw_malloc(sizeof(double) * n);
  fftw_plan w = fftw_plan_dft_c2r_1d(n, in, out, FFTW_ESTIMATE);

  real *sx = (real *)Sleef_malloc((n/2+1) * sizeof(real)*2);
  real *sy = (real *)Sleef_malloc(sizeof(real)*n);
  struct SleefDFT *p = SleefDFT_init1d(n, sx, sy, SLEEF_MODE_REAL | SLEEF_MODE_BACKWARD | MODE);

  for(int i=0;i<n/2;i++) {
    if (i == 0) {
      in[0  ] = (2.0 * (rand() / (real)RAND_MAX) - 1);
      in[n/2] = (2.0 * (rand() / (real)RAND_MAX) - 1);
    } else {
      in[i  ] = (2.0 * (rand() / (real)RAND_MAX) - 1) + (2.0 * (rand() / (real)RAND_MAX) - 1) * _Complex_I;
    }
  }

  for(int i=0;i<n/2+1;i++) {
    sx[2*i+0] = creal(in[i]);
    sx[2*i+1] = cimag(in[i]);
  }

  SleefDFT_execute(p, NULL, NULL);
  fftw_execute(w);

  double rmsn = 0, rmsd = 0;

  for(int i=0;i<n;i++) {
    rmsn += squ(sy[i] - out[i]);
    rmsd += squ(        out[i]);
  }

  fftw_destroy_plan(w);
  fftw_free(in);
  fftw_free(out);

  Sleef_free(sx);
  Sleef_free(sy);
  SleefDFT_dispose(p);

  return rmsn / rmsd;
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
  double e;

  e = check_cf(n);
  success = success && e < THRES;
  printf("complex forward   : %s (%g)\n", e < THRES ? "OK" : "NG", e);
  e = check_cb(n);
  success = success && e < THRES;
  printf("complex backward  : %s (%g)\n", e < THRES ? "OK" : "NG", e);
  e = check_rf(n);
  success = success && e < THRES;
  printf("real forward      : %s (%g)\n", e < THRES ? "OK" : "NG", e);
  e = check_rb(n);
  success = success && e < THRES;
  printf("real backward     : %s (%g)\n", e < THRES ? "OK" : "NG", e);

  exit(success ? 0 : -1);
}
