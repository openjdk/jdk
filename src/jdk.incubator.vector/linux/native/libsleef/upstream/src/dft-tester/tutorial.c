//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

// gcc tutorial.c -lsleef -lsleefdft -lm
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <math.h>
#include <complex.h>

#include "sleef.h"
#include "sleefdft.h"

#define THRES 1e-4

typedef double complex cmpl;

cmpl omega(double n, double kn) {
  return cexp((-2 * M_PI * _Complex_I / n) * kn);
}

void forward(cmpl *ts, cmpl *fs, int len) {
  for(int k=0;k<len;k++) {
    fs[k] = 0;
    for(int n=0;n<len;n++) fs[k] += ts[n] * omega(len, n*k);
  }
}

int main(int argc, char **argv) {
  int n = 256;
  if (argc == 2) n = 1 << atoi(argv[1]);

  SleefDFT_setPlanFilePath("plan.txt", NULL, SLEEF_PLAN_AUTOMATIC);

  double *sx = (double *)Sleef_malloc(n*2 * sizeof(double));
  double *sy = (double *)Sleef_malloc(n*2 * sizeof(double));

  struct SleefDFT *p = SleefDFT_double_init1d(n, sx, sy, SLEEF_MODE_FORWARD);

  if (p == NULL) {
    printf("SleefDFT initialization failed\n");
    exit(-1);
  }

  cmpl *ts = (cmpl *)malloc(sizeof(cmpl)*n);
  cmpl *fs = (cmpl *)malloc(sizeof(cmpl)*n);

  for(int i=0;i<n;i++) {
    ts[i] =
      (2.0 * (rand() / (double)RAND_MAX) - 1) * 1.0 +
      (2.0 * (rand() / (double)RAND_MAX) - 1) * _Complex_I;

    sx[(i*2+0)] = creal(ts[i]);
    sx[(i*2+1)] = cimag(ts[i]);
  }

  forward(ts, fs, n);

  SleefDFT_double_execute(p, NULL, NULL);

  int success = 1;

  for(int i=0;i<n;i++) {
    if ((fabs(sy[(i*2+0)] - creal(fs[i])) > THRES) ||
        (fabs(sy[(i*2+1)] - cimag(fs[i])) > THRES)) {
      success = 0;
    }
  }

  printf("%s\n", success ? "OK" : "NG");

  free(fs); free(ts);
  Sleef_free(sy); Sleef_free(sx);

  SleefDFT_dispose(p);

  exit(success);
}
