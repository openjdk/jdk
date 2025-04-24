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

#ifdef __SSE2__
#if defined(_MSC_VER)
#include <intrin.h>
#else
#include <x86intrin.h>
#endif
typedef __m128d vdouble;
typedef __m128 vfloat;
#define ENABLED
#elif defined(__ARM_NEON)
#include <arm_neon.h>
typedef float64x2_t vdouble;
typedef float32x4_t vfloat;
#define ENABLED
#elif defined(__VSX__)
#include <altivec.h>
typedef __vector double vdouble;
typedef __vector float  vfloat;
#define ENABLED
#elif defined(__VX__)
#include <vecintrin.h>
typedef __vector double vdouble;
typedef __vector float  vfloat;
#define ENABLED
#endif

#ifdef ENABLED
void benchSleef128_DPTrig() {
  fillDP(abufdp, 0, 6.28);

  callFuncSLEEF1_1(Sleef_sind2_u10   , "sin, DP, 128", 0, 6.28, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd2_u10   , "cos, DP, 128", 0, 6.28, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand2_u10   , "tan, DP, 128", 0, 6.28, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd2_u10, "sincos, DP, 128", 0, 6.28, 1.0, abufdp, vdouble);

  callFuncSLEEF1_1(Sleef_sind2_u35   , "sin, DP, 128", 0, 6.28, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd2_u35   , "cos, DP, 128", 0, 6.28, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand2_u35   , "tan, DP, 128", 0, 6.28, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd2_u35, "sincos, DP, 128", 0, 6.28, 4.0, abufdp, vdouble);

  fillDP(abufdp, 0, 1e+6);

  callFuncSLEEF1_1(Sleef_sind2_u10   , "sin, DP, 128", 0, 1e+6, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd2_u10   , "cos, DP, 128", 0, 1e+6, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand2_u10   , "tan, DP, 128", 0, 1e+6, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd2_u10, "sincos, DP, 128", 0, 1e+6, 1.0, abufdp, vdouble);

  callFuncSLEEF1_1(Sleef_sind2_u35   , "sin, DP, 128", 0, 1e+6, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd2_u35   , "cos, DP, 128", 0, 1e+6, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand2_u35   , "tan, DP, 128", 0, 1e+6, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd2_u35, "sincos, DP, 128", 0, 1e+6, 4.0, abufdp, vdouble);

  fillDP(abufdp, 0, 1e+100);

  callFuncSLEEF1_1(Sleef_sind2_u10   , "sin, DP, 128", 0, 1e+100, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd2_u10   , "cos, DP, 128", 0, 1e+100, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand2_u10   , "tan, DP, 128", 0, 1e+100, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd2_u10, "sincos, DP, 128", 0, 1e+100, 1.0, abufdp, vdouble);

  callFuncSLEEF1_1(Sleef_sind2_u35   , "sin, DP, 128", 0, 1e+100, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd2_u35   , "cos, DP, 128", 0, 1e+100, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand2_u35   , "tan, DP, 128", 0, 1e+100, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd2_u35, "sincos, DP, 128", 0, 1e+100, 4.0, abufdp, vdouble);
}

void benchSleef128_DPNontrig() {
  fillDP(abufdp, 0, 1e+300);

  callFuncSLEEF1_1(Sleef_logd2_u10  , "log, DP, 128", 0, 1e+300, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_log10d2_u10, "log10, DP, 128", 0, 1e+300, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_log1pd2_u10, "log1p, DP, 128", 0, 1e+300, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_logd2_u35  , "log, DP, 128", 0, 1e+300, 4.0, abufdp, vdouble);

  fillDP(abufdp, -700, 700);

  callFuncSLEEF1_1(Sleef_expd2_u10  , "exp, DP, 128", -700, 700, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_exp2d2_u10 , "exp2, DP, 128", -700, 700, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_exp10d2_u10, "exp10, DP, 128", -700, 700, 1.0, abufdp, vdouble);

  fillDP(abufdp, -30, 30);
  fillDP(bbufdp, -30, 30);

  callFuncSLEEF1_2(Sleef_powd2_u10, "pow, DP, 128", -30, 30, -30, 30, 1.0, abufdp, bbufdp, vdouble);

  fillDP(abufdp, -1.0, 1.0);

  callFuncSLEEF1_1(Sleef_asind2_u10, "asin, DP, 128", -1.0, 1.0, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_acosd2_u10, "acos, DP, 128", -1.0, 1.0, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_asind2_u35, "asin, DP, 128", -1.0, 1.0, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_acosd2_u35, "acos, DP, 128", -1.0, 1.0, 4.0, abufdp, vdouble);

  fillDP(abufdp, -10, 10);
  fillDP(bbufdp, -10, 10);

  callFuncSLEEF1_1(Sleef_atand2_u10, "atan, DP, 128", -10, 10, 1.0, abufdp, vdouble);
  callFuncSLEEF1_2(Sleef_atan2d2_u10, "atan2, DP, 128", -10, 10, -10, 10, 1.0, abufdp, bbufdp, vdouble);
  callFuncSLEEF1_1(Sleef_atand2_u35, "atan, DP, 128", -10, 10, 4.0, abufdp, vdouble);
  callFuncSLEEF1_2(Sleef_atan2d2_u35, "atan2, DP, 128", -10, 10, -10, 10, 4.0, abufdp, bbufdp, vdouble);
}

void benchSleef128_SPTrig() {
  fillSP(abufsp, 0, 6.28);

  callFuncSLEEF1_1(Sleef_sinf4_u10   , "sin, SP, 128", 0, 6.28, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_cosf4_u10   , "cos, SP, 128", 0, 6.28, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_tanf4_u10   , "tan, SP, 128", 0, 6.28, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_sincosf4_u10, "sincos, SP, 128", 0, 6.28, 1.0, abufsp, vfloat);

  callFuncSLEEF1_1(Sleef_sinf4_u35   , "sin, SP, 128", 0, 6.28, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_cosf4_u35   , "cos, SP, 128", 0, 6.28, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_tanf4_u35   , "tan, SP, 128", 0, 6.28, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_sincosf4_u35, "sincos, SP, 128", 0, 6.28, 4.0, abufsp, vfloat);

  fillSP(abufsp, 0, 1e+20);

  callFuncSLEEF1_1(Sleef_sinf4_u10   , "sin, SP, 128", 0, 1e+20, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_cosf4_u10   , "cos, SP, 128", 0, 1e+20, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_tanf4_u10   , "tan, SP, 128", 0, 1e+20, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_sincosf4_u10, "sincos, SP, 128", 0, 1e+20, 1.0, abufsp, vfloat);

  callFuncSLEEF1_1(Sleef_sinf4_u35   , "sin, SP, 128", 0, 1e+20, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_cosf4_u35   , "cos, SP, 128", 0, 1e+20, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_tanf4_u35   , "tan, SP, 128", 0, 1e+20, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_sincosf4_u35, "sincos, SP, 128", 0, 1e+20, 4.0, abufsp, vfloat);
}

void benchSleef128_SPNontrig() {
  fillSP(abufsp, 0, 1e+38);

  callFuncSLEEF1_1(Sleef_logf4_u10  , "log, SP, 128", 0, 1e+38, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_log10f4_u10, "log10, SP, 128", 0, 1e+38, 1.0, abufsp, vfloat);
  //callFuncSLEEF1_1(Sleef_log1pf4_u10, "log1p, SP, 128", 0, 1e+38, 1.0, abufsp, vfloat);

  callFuncSLEEF1_1(Sleef_logf4_u35  , "log, SP, 128", 0, 1e+38, 4.0, abufsp, vfloat);
  //callFuncSLEEF1_1(Sleef_log10f4_u35, "log10, SP, 128", 0, 1e+38, 4.0, abufsp, vfloat);
  //callFuncSLEEF1_1(Sleef_log1pf4_u35, "log1p, SP, 128", 0, 1e+38, 4.0, abufsp, vfloat);

  fillSP(abufsp, -100, 100);

  callFuncSLEEF1_1(Sleef_expf4_u10  , "exp, SP, 128", -100, 100, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_exp2f4_u10 , "exp2, SP, 128", -100, 100, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_exp10f4_u10, "exp10, SP, 128", -100, 100, 1.0, abufsp, vfloat);

  fillSP(abufsp, -30, 30);
  fillSP(bbufsp, -30, 30);

  callFuncSLEEF1_2(Sleef_powf4_u10, "pow, SP, 128", -30, 30, -30, 30, 1.0, abufsp, bbufsp, vfloat);

  fillSP(abufsp, -1.0, 1.0);

  callFuncSLEEF1_1(Sleef_asinf4_u10, "asin, SP, 128", -1.0, 1, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_acosf4_u10, "acos, SP, 128", -1.0, 1, 1.0, abufsp, vfloat);

  callFuncSLEEF1_1(Sleef_asinf4_u35, "asin, SP, 128", -1.0, 1.0, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_acosf4_u35, "acos, SP, 128", -1.0, 1.0, 4.0, abufsp, vfloat);

  fillSP(abufsp, -10, 10);
  fillSP(bbufsp, -10, 10);

  callFuncSLEEF1_1(Sleef_atanf4_u10, "atan, SP, 128", -10, 10, 1.0, abufsp, vfloat);
  callFuncSLEEF1_2(Sleef_atan2f4_u10, "atan2, SP, 128", -10, 10, -10, 10, 1.0, abufsp, bbufsp, vfloat);

  callFuncSLEEF1_1(Sleef_atanf4_u35, "atan, SP, 128", -10, 10, 4.0, abufsp, vfloat);
  callFuncSLEEF1_2(Sleef_atan2f4_u35, "atan2, SP, 128", -10, 10, -10, 10, 4.0, abufsp, bbufsp, vfloat);
}
#else // #ifdef ENABLED
void benchSleef128_DPTrig() {}
void benchSleef128_DPNontrig() {}
void benchSleef128_SPTrig() {}
void benchSleef128_SPNontrig() {}
#endif // #ifdef ENABLED
