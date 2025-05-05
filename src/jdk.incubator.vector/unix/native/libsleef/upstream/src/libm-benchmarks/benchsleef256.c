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

#ifdef __AVX__
#if defined(_MSC_VER)
#include <intrin.h>
#else
#include <x86intrin.h>
#endif
typedef __m256d vdouble;
typedef __m256 vfloat;
#define ENABLED
#endif

#ifdef ENABLED
void benchSleef256_DPTrig() {
  fillDP(abufdp, 0, 6.28);

  callFuncSLEEF1_1(Sleef_sind4_u10   , "sin, DP, 256", 0, 6.28, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd4_u10   , "cos, DP, 256", 0, 6.28, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand4_u10   , "tan, DP, 256", 0, 6.28, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd4_u10, "sincos, DP, 256", 0, 6.28, 1.0, abufdp, vdouble);

  callFuncSLEEF1_1(Sleef_sind4_u35   , "sin, DP, 256", 0, 6.28, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd4_u35   , "cos, DP, 256", 0, 6.28, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand4_u35   , "tan, DP, 256", 0, 6.28, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd4_u35, "sincos, DP, 256", 0, 6.28, 4.0, abufdp, vdouble);

  fillDP(abufdp, 0, 1e+6);

  callFuncSLEEF1_1(Sleef_sind4_u10   , "sin, DP, 256", 0, 1e+6, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd4_u10   , "cos, DP, 256", 0, 1e+6, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand4_u10   , "tan, DP, 256", 0, 1e+6, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd4_u10, "sincos, DP, 256", 0, 1e+6, 1.0, abufdp, vdouble);

  callFuncSLEEF1_1(Sleef_sind4_u35   , "sin, DP, 256", 0, 1e+6, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd4_u35   , "cos, DP, 256", 0, 1e+6, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand4_u35   , "tan, DP, 256", 0, 1e+6, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd4_u35, "sincos, DP, 256", 0, 1e+6, 4.0, abufdp, vdouble);

  fillDP(abufdp, 0, 1e+100);

  callFuncSLEEF1_1(Sleef_sind4_u10   , "sin, DP, 256", 0, 1e+100, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd4_u10   , "cos, DP, 256", 0, 1e+100, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand4_u10   , "tan, DP, 256", 0, 1e+100, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd4_u10, "sincos, DP, 256", 0, 1e+100, 1.0, abufdp, vdouble);

  callFuncSLEEF1_1(Sleef_sind4_u35   , "sin, DP, 256", 0, 1e+100, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_cosd4_u35   , "cos, DP, 256", 0, 1e+100, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_tand4_u35   , "tan, DP, 256", 0, 1e+100, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_sincosd4_u35, "sincos, DP, 256", 0, 1e+100, 4.0, abufdp, vdouble);
}

void benchSleef256_DPNontrig() {
  fillDP(abufdp, 0, 1e+300);

  callFuncSLEEF1_1(Sleef_logd4_u10  , "log, DP, 256", 0, 1e+300, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_log10d4_u10, "log10, DP, 256", 0, 1e+300, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_log1pd4_u10, "log1p, DP, 256", 0, 1e+300, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_logd4_u35  , "log, DP, 256", 0, 1e+300, 4.0, abufdp, vdouble);

  fillDP(abufdp, -700, 700);

  callFuncSLEEF1_1(Sleef_expd4_u10  , "exp, DP, 256", -700, 700, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_exp2d4_u10 , "exp2, DP, 256", -700, 700, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_exp10d4_u10, "exp10, DP, 256", -700, 700, 1.0, abufdp, vdouble);

  fillDP(abufdp, -30, 30);
  fillDP(bbufdp, -30, 30);

  callFuncSLEEF1_2(Sleef_powd4_u10, "pow, DP, 256", -30, 30, -30, 30, 1.0, abufdp, bbufdp, vdouble);

  fillDP(abufdp, -1.0, 1.0);

  callFuncSLEEF1_1(Sleef_asind4_u10, "asin, DP, 256", -1.0, 1.0, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_acosd4_u10, "acos, DP, 256", -1.0, 1.0, 1.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_asind4_u35, "asin, DP, 256", -1.0, 1.0, 4.0, abufdp, vdouble);
  callFuncSLEEF1_1(Sleef_acosd4_u35, "acos, DP, 256", -1.0, 1.0, 4.0, abufdp, vdouble);

  fillDP(abufdp, -10, 10);
  fillDP(bbufdp, -10, 10);

  callFuncSLEEF1_1(Sleef_atand4_u10, "atan, DP, 256", -10, 10, 1.0, abufdp, vdouble);
  callFuncSLEEF1_2(Sleef_atan2d4_u10, "atan2, DP, 256", -10, 10, -10, 10, 1.0, abufdp, bbufdp, vdouble);
  callFuncSLEEF1_1(Sleef_atand4_u35, "atan, DP, 256", -10, 10, 4.0, abufdp, vdouble);
  callFuncSLEEF1_2(Sleef_atan2d4_u35, "atan2, DP, 256", -10, 10, -10, 10, 4.0, abufdp, bbufdp, vdouble);
}

void benchSleef256_SPTrig() {
  fillSP(abufsp, 0, 6.28);

  callFuncSLEEF1_1(Sleef_sinf8_u10   , "sin, SP, 256", 0, 6.28, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_cosf8_u10   , "cos, SP, 256", 0, 6.28, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_tanf8_u10   , "tan, SP, 256", 0, 6.28, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_sincosf8_u10, "sincos, SP, 256", 0, 6.28, 1.0, abufsp, vfloat);

  callFuncSLEEF1_1(Sleef_sinf8_u35   , "sin, SP, 256", 0, 6.28, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_cosf8_u35   , "cos, SP, 256", 0, 6.28, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_tanf8_u35   , "tan, SP, 256", 0, 6.28, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_sincosf8_u35, "sincos, SP, 256", 0, 6.28, 4.0, abufsp, vfloat);

  fillSP(abufsp, 0, 1e+20);

  callFuncSLEEF1_1(Sleef_sinf8_u10   , "sin, SP, 256", 0, 1e+20, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_cosf8_u10   , "cos, SP, 256", 0, 1e+20, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_tanf8_u10   , "tan, SP, 256", 0, 1e+20, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_sincosf8_u10, "sincos, SP, 256", 0, 1e+20, 1.0, abufsp, vfloat);

  callFuncSLEEF1_1(Sleef_sinf8_u35   , "sin, SP, 256", 0, 1e+20, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_cosf8_u35   , "cos, SP, 256", 0, 1e+20, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_tanf8_u35   , "tan, SP, 256", 0, 1e+20, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_sincosf8_u35, "sincos, SP, 256", 0, 1e+20, 4.0, abufsp, vfloat);
}

void benchSleef256_SPNontrig() {
  fillSP(abufsp, 0, 1e+38);

  callFuncSLEEF1_1(Sleef_logf8_u10  , "log, SP, 256", 0, 1e+38, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_log10f8_u10, "log10, SP, 256", 0, 1e+38, 1.0, abufsp, vfloat);
  //callFuncSLEEF1_1(Sleef_log1pf8_u10, "log1p, SP, 256", 0, 1e+38, 1.0, abufsp, vfloat);

  callFuncSLEEF1_1(Sleef_logf8_u35  , "log, SP, 256", 0, 1e+38, 4.0, abufsp, vfloat);
  //callFuncSLEEF1_1(Sleef_log10f8_u35, "log10, SP, 256", 0, 1e+38, 4.0, abufsp, vfloat);
  //callFuncSLEEF1_1(Sleef_log1pf8_u35, "log1p, SP, 256", 0, 1e+38, 4.0, abufsp, vfloat);

  fillSP(abufsp, -100, 100);

  callFuncSLEEF1_1(Sleef_expf8_u10  , "exp, SP, 256", -100, 100, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_exp2f8_u10 , "exp2, SP, 256", -100, 100, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_exp10f8_u10, "exp10, SP, 256", -100, 100, 1.0, abufsp, vfloat);

  fillSP(abufsp, -30, 30);
  fillSP(bbufsp, -30, 30);

  callFuncSLEEF1_2(Sleef_powf8_u10, "pow, SP, 256", -30, 30, -30, 30, 1.0, abufsp, bbufsp, vfloat);

  fillSP(abufsp, -1.0, 1.0);

  callFuncSLEEF1_1(Sleef_asinf8_u10, "asin, SP, 256", -1.0, 1, 1.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_acosf8_u10, "acos, SP, 256", -1.0, 1, 1.0, abufsp, vfloat);

  callFuncSLEEF1_1(Sleef_asinf8_u35, "asin, SP, 256", -1.0, 1.0, 4.0, abufsp, vfloat);
  callFuncSLEEF1_1(Sleef_acosf8_u35, "acos, SP, 256", -1.0, 1.0, 4.0, abufsp, vfloat);

  fillSP(abufsp, -10, 10);
  fillSP(bbufsp, -10, 10);

  callFuncSLEEF1_1(Sleef_atanf8_u10, "atan, SP, 256", -10, 10, 1.0, abufsp, vfloat);
  callFuncSLEEF1_2(Sleef_atan2f8_u10, "atan2, SP, 256", -10, 10, -10, 10, 1.0, abufsp, bbufsp, vfloat);

  callFuncSLEEF1_1(Sleef_atanf8_u35, "atan, SP, 256", -10, 10, 4.0, abufsp, vfloat);
  callFuncSLEEF1_2(Sleef_atan2f8_u35, "atan2, SP, 256", -10, 10, -10, 10, 4.0, abufsp, bbufsp, vfloat);
}
#else // #ifdef ENABLED
void zeroupper256() {}
void benchSleef256_DPTrig() {}
void benchSleef256_DPNontrig() {}
void benchSleef256_SPTrig() {}
void benchSleef256_SPNontrig() {}
#endif // #ifdef ENABLED
