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
#include <sleef.h>

void fillDP(double *buf, double min, double max);
void fillSP(float *buf, double min, double max);

extern char x86BrandString[256], versionString[1024];
extern int veclen;
extern double *abufdp, *bbufdp;
extern float *abufsp, *bbufsp;
extern FILE *fp;

#include "bench.h"

#ifdef __AVX512F__
#if defined(_MSC_VER)
#include <intrin.h>
#else
#include <x86intrin.h>
#endif
typedef __m512d vdouble;
typedef __m512 vfloat;
#define ENABLED
#endif

#ifdef ENABLED
void benchSleef512_DPTrig() {
  fillDP(abufdp, 0, 6.28);

  callFuncSLEEF1_1(Sleef_sind8_u10   , "sin, DP, 512", 0, 6.28, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd8_u10   , "cos, DP, 512", 0, 6.28, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand8_u10   , "tan, DP, 512", 0, 6.28, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd8_u10, "sincos, DP, 512", 0, 6.28, 1.0, abufdp, vdouble);

  callFuncSLEEF1_1(Sleef_sind8_u35   , "sin, DP, 512", 0, 6.28, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd8_u35   , "cos, DP, 512", 0, 6.28, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand8_u35   , "tan, DP, 512", 0, 6.28, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd8_u35, "sincos, DP, 512", 0, 6.28, 4.0, abufdp, vdouble);

  fillDP(abufdp, 0, 1e+6);

  callFuncSLEEF1_1(Sleef_sind8_u10   , "sin, DP, 512", 0, 1e+6, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd8_u10   , "cos, DP, 512", 0, 1e+6, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand8_u10   , "tan, DP, 512", 0, 1e+6, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd8_u10, "sincos, DP, 512", 0, 1e+6, 1.0, abufdp, vdouble);

  callFuncSLEEF1_1(Sleef_sind8_u35   , "sin, DP, 512", 0, 1e+6, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd8_u35   , "cos, DP, 512", 0, 1e+6, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand8_u35   , "tan, DP, 512", 0, 1e+6, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd8_u35, "sincos, DP, 512", 0, 1e+6, 4.0, abufdp, vdouble);

  fillDP(abufdp, 0, 1e+100);

  callFuncSLEEF1_1(Sleef_sind8_u10   , "sin, DP, 512", 0, 1e+100, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd8_u10   , "cos, DP, 512", 0, 1e+100, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand8_u10   , "tan, DP, 512", 0, 1e+100, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd8_u10, "sincos, DP, 512", 0, 1e+100, 1.0, abufdp, vdouble);

  callFuncSLEEF1_1(Sleef_sind8_u35   , "sin, DP, 512", 0, 1e+100, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd8_u35   , "cos, DP, 512", 0, 1e+100, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand8_u35   , "tan, DP, 512", 0, 1e+100, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd8_u35, "sincos, DP, 512", 0, 1e+100, 4.0, abufdp, vdouble);
}

void benchSleef512_DPNontrig() {
  fillDP(abufdp, 0, 1e+300);

  callFuncSLEEF1_1(Sleef_logd8_u10  , "log, DP, 512", 0, 1e+300, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_log10d8_u10, "log10, DP, 512", 0, 1e+300, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_log1pd8_u10, "log1p, DP, 512", 0, 1e+300, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_logd8_u35  , "log, DP, 512", 0, 1e+300, 4.0, abufdp, vdouble);

  fillDP(abufdp, -700, 700);

  callFuncSLEEF1_1(Sleef_expd8_u10  , "exp, DP, 512", -700, 700, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_exp2d8_u10 , "exp2, DP, 512", -700, 700, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_exp10d8_u10, "exp10, DP, 512", -700, 700, 1.0, abufdp, vdouble);

  fillDP(abufdp, -30, 30);
  fillDP(bbufdp, -30, 30);

  callFuncSLEEF1_2(Sleef_powd8_u10, "pow, DP, 512", -30, 30, -30, 30, 1.0, abufdp, bbufdp, vdouble);

  fillDP(abufdp, -1.0, 1.0);

  callFuncSLEEF1_1(Sleef_asind8_u10, "asin, DP, 512", -1.0, 1.0, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_acosd8_u10, "acos, DP, 512", -1.0, 1.0, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_asind8_u35, "asin, DP, 512", -1.0, 1.0, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_acosd8_u35, "acos, DP, 512", -1.0, 1.0, 4.0, abufdp, vdouble);

  fillDP(abufdp, -10, 10);
  fillDP(bbufdp, -10, 10);

  callFuncSLEEF1_1(Sleef_atand8_u10, "atan, DP, 512", -10, 10, 1.0, abufdp, vdouble);
  callFuncSLEEF1_2(Sleef_atan2d8_u10, "atan2, DP, 512", -10, 10, -10, 10, 1.0, abufdp, bbufdp, vdouble);
  callFuncSLEEF1_1(Sleef_atand8_u35, "atan, DP, 512", -10, 10, 4.0, abufdp, vdouble);
  callFuncSLEEF1_2(Sleef_atan2d8_u35, "atan2, DP, 512", -10, 10, -10, 10, 4.0, abufdp, bbufdp, vdouble);
}

void benchSleef512_SPTrig() {
  fillSP(abufsp, 0, 6.28);

  callFuncSLEEF1_1(Sleef_sinf16_u10   , "sin, SP, 512", 0, 6.28, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_cosf16_u10   , "cos, SP, 512", 0, 6.28, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_tanf16_u10   , "tan, SP, 512", 0, 6.28, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_sincosf16_u10, "sincos, SP, 512", 0, 6.28, 1.0, abufsp, vfloat);

  callFuncSLEEF1_1(Sleef_sinf16_u35   , "sin, SP, 512", 0, 6.28, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_cosf16_u35   , "cos, SP, 512", 0, 6.28, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_tanf16_u35   , "tan, SP, 512", 0, 6.28, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_sincosf16_u35, "sincos, SP, 512", 0, 6.28, 4.0, abufsp, vfloat);

  fillSP(abufsp, 0, 1e+20);

  callFuncSLEEF1_1(Sleef_sinf16_u10   , "sin, SP, 512", 0, 1e+20, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_cosf16_u10   , "cos, SP, 512", 0, 1e+20, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_tanf16_u10   , "tan, SP, 512", 0, 1e+20, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_sincosf16_u10, "sincos, SP, 512", 0, 1e+20, 1.0, abufsp, vfloat);

  callFuncSLEEF1_1(Sleef_sinf16_u35   , "sin, SP, 512", 0, 1e+20, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_cosf16_u35   , "cos, SP, 512", 0, 1e+20, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_tanf16_u35   , "tan, SP, 512", 0, 1e+20, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_sincosf16_u35, "sincos, SP, 512", 0, 1e+20, 4.0, abufsp, vfloat);
}

void benchSleef512_SPNontrig() {
  fillSP(abufsp, 0, 1e+38);

  callFuncSLEEF1_1(Sleef_logf16_u10  , "log, SP, 512", 0, 1e+38, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_log10f16_u10, "log10, SP, 512", 0, 1e+38, 1.0, abufsp, vfloat);
  //callFuncSLEEF1_1(Sleef_log1pf16_u10, "log1p, SP, 512", 0, 1e+38, 1.0, abufsp, vfloat);

  callFuncSLEEF1_1(Sleef_logf16_u35  , "log, SP, 512", 0, 1e+38, 4.0, abufsp, vfloat);
  //callFuncSLEEF1_1(Sleef_log10f16_u35, "log10, SP, 512", 0, 1e+38, 4.0, abufsp, vfloat);
  //callFuncSLEEF1_1(Sleef_log1pf16_u35, "log1p, SP, 512", 0, 1e+38, 4.0, abufsp, vfloat);

  fillSP(abufsp, -100, 100);

  callFuncSLEEF1_1(Sleef_expf16_u10  , "exp, SP, 512", -100, 100, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_exp2f16_u10 , "exp2, SP, 512", -100, 100, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_exp10f16_u10, "exp10, SP, 512", -100, 100, 1.0, abufsp, vfloat);

  fillSP(abufsp, -30, 30);
  fillSP(bbufsp, -30, 30);

  callFuncSLEEF1_2(Sleef_powf16_u10, "pow, SP, 512", -30, 30, -30, 30, 1.0, abufsp, bbufsp, vfloat);

  fillSP(abufsp, -1.0, 1.0);

  callFuncSLEEF1_1(Sleef_asinf16_u10, "asin, SP, 512", -1.0, 1, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_acosf16_u10, "acos, SP, 512", -1.0, 1, 1.0, abufsp, vfloat);

  callFuncSLEEF1_1(Sleef_asinf16_u35, "asin, SP, 512", -1.0, 1.0, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_acosf16_u35, "acos, SP, 512", -1.0, 1.0, 4.0, abufsp, vfloat);

  fillSP(abufsp, -10, 10);
  fillSP(bbufsp, -10, 10);

  callFuncSLEEF1_1(Sleef_atanf16_u10, "atan, SP, 512", -10, 10, 1.0, abufsp, vfloat);
  callFuncSLEEF1_2(Sleef_atan2f16_u10, "atan2, SP, 512", -10, 10, -10, 10, 1.0, abufsp, bbufsp, vfloat);

  callFuncSLEEF1_1(Sleef_atanf16_u35, "atan, SP, 512", -10, 10, 4.0, abufsp, vfloat);
  callFuncSLEEF1_2(Sleef_atan2f16_u35, "atan2, SP, 512", -10, 10, -10, 10, 4.0, abufsp, bbufsp, vfloat);
}
#else // #ifdef ENABLED
void benchSleef512_DPTrig() {}
void benchSleef512_DPNontrig() {}
void benchSleef512_SPTrig() {}
void benchSleef512_SPNontrig() {}
#endif // #ifdef ENABLED
