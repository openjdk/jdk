//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <assert.h>
#include <math.h>
#include <time.h>
#include <unistd.h>
#include <x86intrin.h>

uint64_t Sleef_currentTimeMicros();
void fillDP(double *buf, double min, double max);
void fillSP(float *buf, double min, double max);

extern char x86BrandString[256], versionString[1024];
extern int veclen;
extern int enableLogExp;
extern double *abufdp, *bbufdp;
extern float *abufsp, *bbufsp;
extern FILE *fp;

#include "bench.h"

#ifdef __SSE2__
typedef __m128d vdouble;
typedef __m128 vfloat;
#define ENABLED
#endif

#ifdef ENABLED
void benchSVML128_DPTrig() {
  fillDP(abufdp, 0, 6.28);

  callFuncSVML1_1(_mm_sin_pd   , "sin, DP, 128", 0, 6.28, abufdp, vdouble);
  callFuncSVML1_1(_mm_cos_pd   , "cos, DP, 128", 0, 6.28, abufdp, vdouble);
  callFuncSVML1_1(_mm_tan_pd   , "tan, DP, 128", 0, 6.28, abufdp, vdouble);
  callFuncSVML2_1(_mm_sincos_pd, "sincos, DP, 128", 0, 6.28, abufdp, vdouble);

  fillDP(abufdp, 0, 1e+6);

  callFuncSVML1_1(_mm_sin_pd   , "sin, DP, 128", 0, 1e+6, abufdp, vdouble);
  callFuncSVML1_1(_mm_cos_pd   , "cos, DP, 128", 0, 1e+6, abufdp, vdouble);
  callFuncSVML1_1(_mm_tan_pd   , "tan, DP, 128", 0, 1e+6, abufdp, vdouble);
  callFuncSVML2_1(_mm_sincos_pd, "sincos, DP, 128", 0, 1e+6, abufdp, vdouble);

  fillDP(abufdp, 0, 1e+100);

  callFuncSVML1_1(_mm_sin_pd   , "sin, DP, 128", 0, 1e+100, abufdp, vdouble);
  callFuncSVML1_1(_mm_cos_pd   , "cos, DP, 128", 0, 1e+100, abufdp, vdouble);
  callFuncSVML1_1(_mm_tan_pd   , "tan, DP, 128", 0, 1e+100, abufdp, vdouble);
  callFuncSVML2_1(_mm_sincos_pd, "sincos, DP, 128", 0, 1e+100, abufdp, vdouble);
}

void benchSVML128_DPNontrig() {
  fillDP(abufdp, 0, 1e+300);

  callFuncSVML1_1(_mm_log_pd  , "log, DP, 128", 0, 1e+300, abufdp, vdouble);

  if (enableLogExp) {
    callFuncSVML1_1(_mm_log10_pd, "log10, DP, 128", 0, 1e+300, abufdp, vdouble);
    callFuncSVML1_1(_mm_log1p_pd, "log1p, DP, 128", 0, 1e+300, abufdp, vdouble);

    fillDP(abufdp, -700, 700);

    callFuncSVML1_1(_mm_exp_pd  , "exp, DP, 128", -700, 700, abufdp, vdouble);
    callFuncSVML1_1(_mm_exp2_pd , "exp2, DP, 128", -700, 700, abufdp, vdouble);
    callFuncSVML1_1(_mm_exp10_pd, "exp10, DP, 128", -700, 700, abufdp, vdouble);

    fillDP(abufdp, -30, 30);
    fillDP(bbufdp, -30, 30);

    callFuncSVML1_2(_mm_pow_pd, "pow, DP, 128", -30, 30, -30, 30, abufdp, bbufdp, vdouble);
  }

  fillDP(abufdp, -1.0, 1.0);

  callFuncSVML1_1(_mm_asin_pd, "asin, DP, 128", -1.0, 1.0, abufdp, vdouble);
  callFuncSVML1_1(_mm_acos_pd, "acos, DP, 128", -1.0, 1.0, abufdp, vdouble);

  fillDP(abufdp, -10, 10);
  fillDP(bbufdp, -10, 10);

  callFuncSVML1_1(_mm_atan_pd, "atan, DP, 128", -10, 10, abufdp, vdouble);
  callFuncSVML1_2(_mm_atan2_pd, "atan2, DP, 128", -10, 10, -10, 10, abufdp, bbufdp, vdouble);
}

void benchSVML128_SPTrig() {
  fillSP(abufsp, 0, 6.28);

  callFuncSVML1_1(_mm_sin_ps   , "sin, SP, 128", 0, 6.28, abufsp, vfloat);
  callFuncSVML1_1(_mm_cos_ps   , "cos, SP, 128", 0, 6.28, abufsp, vfloat);
  callFuncSVML1_1(_mm_tan_ps   , "tan, SP, 128", 0, 6.28, abufsp, vfloat);
  callFuncSVML2_1(_mm_sincos_ps, "sincos, SP, 128", 0, 6.28, abufsp, vfloat);

  fillSP(abufsp, 0, 1e+20);

  callFuncSVML1_1(_mm_sin_ps   , "sin, SP, 128", 0, 1e+20, abufsp, vfloat);
  callFuncSVML1_1(_mm_cos_ps   , "cos, SP, 128", 0, 1e+20, abufsp, vfloat);
  callFuncSVML1_1(_mm_tan_ps   , "tan, SP, 128", 0, 1e+20, abufsp, vfloat);
  callFuncSVML2_1(_mm_sincos_ps, "sincos, SP, 128", 0, 1e+20, abufsp, vfloat);
}

void benchSVML128_SPNontrig() {
  fillSP(abufsp, 0, 1e+38);

  callFuncSVML1_1(_mm_log_ps  , "log, SP, 128", 0, 1e+38, abufsp, vfloat);

  if (enableLogExp) {
    callFuncSVML1_1(_mm_log10_ps, "log10, SP, 128", 0, 1e+38, abufsp, vfloat);
    //callFuncSVML1_1(_mm_log1p_ps, "log1p, SP, 128", 0, 1e+38, abufsp, vfloat);

    fillSP(abufsp, -100, 100);

    callFuncSVML1_1(_mm_exp_ps  , "exp, SP, 128", -100, 100, abufsp, vfloat);
    callFuncSVML1_1(_mm_exp2_ps , "exp2, SP, 128", -100, 100, abufsp, vfloat);
    callFuncSVML1_1(_mm_exp10_ps, "exp10, SP, 128", -100, 100, abufsp, vfloat);

    fillSP(abufsp, -30, 30);
    fillSP(bbufsp, -30, 30);

    callFuncSVML1_2(_mm_pow_ps, "pow, SP, 128", -30, 30, -30, 30, abufsp, bbufsp, vfloat);
  }

  fillSP(abufsp, -1.0, 1.0);

  callFuncSVML1_1(_mm_asin_ps, "asin, SP, 128", -1.0, 1, abufsp, vfloat);
  callFuncSVML1_1(_mm_acos_ps, "acos, SP, 128", -1.0, 1, abufsp, vfloat);

  fillSP(abufsp, -10, 10);
  fillSP(bbufsp, -10, 10);

  callFuncSVML1_1(_mm_atan_ps, "atan, SP, 128", -10, 10, abufsp, vfloat);
  callFuncSVML1_2(_mm_atan2_ps, "atan2, SP, 128", -10, 10, -10, 10, abufsp, bbufsp, vfloat);
}
#else // #ifdef ENABLED
void benchSVML128_DPTrig() {}
void benchSVML128_DPNontrig() {}
void benchSVML128_SPTrig() {}
void benchSVML128_SPNontrig() {}
#endif // #ifdef ENABLED
