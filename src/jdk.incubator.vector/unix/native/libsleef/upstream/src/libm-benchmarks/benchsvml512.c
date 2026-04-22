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

#ifdef __AVX512F__
typedef __m512d vdouble;
typedef __m512 vfloat;
#define ENABLED
#endif

#ifdef ENABLED
void benchSVML512_DPTrig() {
  fillDP(abufdp, 0, 6.28);

  callFuncSVML1_1(_mm512_sin_pd   , "sin, DP, 512", 0, 6.28, abufdp, vdouble);
  callFuncSVML1_1(_mm512_cos_pd   , "cos, DP, 512", 0, 6.28, abufdp, vdouble);
  callFuncSVML1_1(_mm512_tan_pd   , "tan, DP, 512", 0, 6.28, abufdp, vdouble);
  callFuncSVML2_1(_mm512_sincos_pd, "sincos, DP, 512", 0, 6.28, abufdp, vdouble);

  fillDP(abufdp, 0, 1e+6);

  callFuncSVML1_1(_mm512_sin_pd   , "sin, DP, 512", 0, 1e+6, abufdp, vdouble);
  callFuncSVML1_1(_mm512_cos_pd   , "cos, DP, 512", 0, 1e+6, abufdp, vdouble);
  callFuncSVML1_1(_mm512_tan_pd   , "tan, DP, 512", 0, 1e+6, abufdp, vdouble);
  callFuncSVML2_1(_mm512_sincos_pd, "sincos, DP, 512", 0, 1e+6, abufdp, vdouble);

  fillDP(abufdp, 0, 1e+100);

  callFuncSVML1_1(_mm512_sin_pd   , "sin, DP, 512", 0, 1e+100, abufdp, vdouble);
  callFuncSVML1_1(_mm512_cos_pd   , "cos, DP, 512", 0, 1e+100, abufdp, vdouble);
  callFuncSVML1_1(_mm512_tan_pd   , "tan, DP, 512", 0, 1e+100, abufdp, vdouble);
  callFuncSVML2_1(_mm512_sincos_pd, "sincos, DP, 512", 0, 1e+100, abufdp, vdouble);
}

void benchSVML512_DPNontrig() {
  fillDP(abufdp, 0, 1e+300);

  callFuncSVML1_1(_mm512_log_pd  , "log, DP, 512", 0, 1e+300, abufdp, vdouble);

  if (enableLogExp) {
    callFuncSVML1_1(_mm512_log10_pd, "log10, DP, 512", 0, 1e+300, abufdp, vdouble);
    callFuncSVML1_1(_mm512_log1p_pd, "log1p, DP, 512", 0, 1e+300, abufdp, vdouble);

    fillDP(abufdp, -700, 700);

    callFuncSVML1_1(_mm512_exp_pd  , "exp, DP, 512", -700, 700, abufdp, vdouble);
    callFuncSVML1_1(_mm512_exp2_pd , "exp2, DP, 512", -700, 700, abufdp, vdouble);
    callFuncSVML1_1(_mm512_exp10_pd, "exp10, DP, 512", -700, 700, abufdp, vdouble);

    fillDP(abufdp, -30, 30);
    fillDP(bbufdp, -30, 30);

    callFuncSVML1_2(_mm512_pow_pd, "pow, DP, 512", -30, 30, -30, 30, abufdp, bbufdp, vdouble);
  }

  fillDP(abufdp, -1.0, 1.0);

  callFuncSVML1_1(_mm512_asin_pd, "asin, DP, 512", -1.0, 1.0, abufdp, vdouble);
  callFuncSVML1_1(_mm512_acos_pd, "acos, DP, 512", -1.0, 1.0, abufdp, vdouble);

  fillDP(abufdp, -10, 10);
  fillDP(bbufdp, -10, 10);

  callFuncSVML1_1(_mm512_atan_pd, "atan, DP, 512", -10, 10, abufdp, vdouble);
  callFuncSVML1_2(_mm512_atan2_pd, "atan2, DP, 512", -10, 10, -10, 10, abufdp, bbufdp, vdouble);
}

void benchSVML512_SPTrig() {
  fillSP(abufsp, 0, 6.28);

  callFuncSVML1_1(_mm512_sin_ps   , "sin, SP, 512", 0, 6.28, abufsp, vfloat);
  callFuncSVML1_1(_mm512_cos_ps   , "cos, SP, 512", 0, 6.28, abufsp, vfloat);
  callFuncSVML1_1(_mm512_tan_ps   , "tan, SP, 512", 0, 6.28, abufsp, vfloat);
  callFuncSVML2_1(_mm512_sincos_ps, "sincos, SP, 512", 0, 6.28, abufsp, vfloat);

  fillSP(abufsp, 0, 1e+20);

  callFuncSVML1_1(_mm512_sin_ps   , "sin, SP, 512", 0, 1e+20, abufsp, vfloat);
  callFuncSVML1_1(_mm512_cos_ps   , "cos, SP, 512", 0, 1e+20, abufsp, vfloat);
  callFuncSVML1_1(_mm512_tan_ps   , "tan, SP, 512", 0, 1e+20, abufsp, vfloat);
  callFuncSVML2_1(_mm512_sincos_ps, "sincos, SP, 512", 0, 1e+20, abufsp, vfloat);
}

void benchSVML512_SPNontrig() {
  fillSP(abufsp, 0, 1e+38);

  callFuncSVML1_1(_mm512_log_ps  , "log, SP, 512", 0, 1e+38, abufsp, vfloat);

  if (enableLogExp) {
    callFuncSVML1_1(_mm512_log10_ps, "log10, SP, 512", 0, 1e+38, abufsp, vfloat);
    //callFuncSVML1_1(_mm512_log1p_ps, "log1p, SP, 512", 0, 1e+38, abufsp, vfloat);

    fillSP(abufsp, -100, 100);

    callFuncSVML1_1(_mm512_exp_ps  , "exp, SP, 512", -100, 100, abufsp, vfloat);
    callFuncSVML1_1(_mm512_exp2_ps , "exp2, SP, 512", -100, 100, abufsp, vfloat);
    callFuncSVML1_1(_mm512_exp10_ps, "exp10, SP, 512", -100, 100, abufsp, vfloat);

    fillSP(abufsp, -30, 30);
    fillSP(bbufsp, -30, 30);

    callFuncSVML1_2(_mm512_pow_ps, "pow, SP, 512", -30, 30, -30, 30, abufsp, bbufsp, vfloat);
  }

  fillSP(abufsp, -1.0, 1.0);

  callFuncSVML1_1(_mm512_asin_ps, "asin, SP, 512", -1.0, 1, abufsp, vfloat);
  callFuncSVML1_1(_mm512_acos_ps, "acos, SP, 512", -1.0, 1, abufsp, vfloat);

  fillSP(abufsp, -10, 10);
  fillSP(bbufsp, -10, 10);

  callFuncSVML1_1(_mm512_atan_ps, "atan, SP, 512", -10, 10, abufsp, vfloat);
  callFuncSVML1_2(_mm512_atan2_ps, "atan2, SP, 512", -10, 10, -10, 10, abufsp, bbufsp, vfloat);
}
#else // #ifdef ENABLED
void benchSVML512_DPTrig() {}
void benchSVML512_DPNontrig() {}
void benchSVML512_SPTrig() {}
void benchSVML512_SPNontrig() {}
#endif // #ifdef ENABLED
