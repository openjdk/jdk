//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <mpfr.h>
#include <time.h>
#include <float.h>
#include <limits.h>

#include <math.h>

#ifdef ENABLE_SYS_getrandom
#define _GNU_SOURCE
#include <unistd.h>
#include <sys/syscall.h>
#include <linux/random.h>
#endif

#include "sleef.h"
#include "testerutil.h"

#define DORENAME
#include "rename.h"

#if defined(__APPLE__)
static int isinff(float x) { return x == __builtin_inff() || x == -__builtin_inff(); }
#endif

#if defined(__FreeBSD__)
#define isinff(x) ((x) == (float)(1e+300) || (x) == -(float)(1e+300))
#endif

#define DENORMAL_FLT_MIN (1.4012984643248170709e-45f)
#define POSITIVE_INFINITYf ((float)INFINITY)
#define NEGATIVE_INFINITYf (-(float)INFINITY)

typedef union {
  double d;
  uint64_t u64;
  int64_t i64;
} conv64_t;

typedef union {
  float f;
  uint32_t u32;
  int32_t i32;
} conv32_t;

static float nexttoward0f(float x, int n) {
  union {
    float f;
    int32_t u;
  } cx;
  cx.f = x;
  cx.u -= n;
  return x == 0 ? 0 : cx.f;
}

float rnd() {
  conv32_t c;
  switch(random() & 63) {
  case 0: return nexttoward0f( 0.0, -(random() & ((1 << (random() & 31)) - 1)));
  case 1: return nexttoward0f(-0.0, -(random() & ((1 << (random() & 31)) - 1)));
  case 2: return nexttoward0f( INFINITY, (random() & ((1 << (random() & 31)) - 1)));
  case 3: return nexttoward0f(-INFINITY, (random() & ((1 << (random() & 31)) - 1)));
  }
#ifdef ENABLE_SYS_getrandom
  syscall(SYS_getrandom, &c.u32, sizeof(c.u32), 0);
#else
  c.u32 = (uint32_t)random() | ((uint32_t)random() << 31);
#endif
  return c.f;
}

float rnd_fr() {
  conv32_t c;
  do {
#ifdef ENABLE_SYS_getrandom
    syscall(SYS_getrandom, &c.u32, sizeof(c.u32), 0);
#else
    c.u32 = (uint32_t)random() | ((uint32_t)random() << 31);
#endif
  } while(!isnumber(c.f));
  return c.f;
}

float rnd_zo() {
  conv32_t c;
  do {
#ifdef ENABLE_SYS_getrandom
    syscall(SYS_getrandom, &c.u32, sizeof(c.u32), 0);
#else
    c.u32 = (uint32_t)random() | ((uint32_t)random() << 31);
#endif
  } while(!isnumber(c.f) || c.f < -1 || 1 < c.f);
  return c.f;
}

int main(int argc,char **argv)
{
  mpfr_t frw, frx, fry, frz;

  mpfr_set_default_prec(256);
  mpfr_inits(frw, frx, fry, frz, NULL);

  conv32_t cd;
  float d, t;
  float d2, d3, zo;

  int cnt, ecnt = 0;

  srandom(time(NULL));

  for(cnt = 0;ecnt < 1000;cnt++) {
    switch(cnt & 7) {
    case 0:
      d = rnd();
      d2 = rnd();
      d3 = rnd();
      zo = rnd();
      break;
    case 1:
      cd.f = rint(rnd_zo() * 1e+10) * M_PI_4;
      cd.i32 += (random() & 0xff) - 0x7f;
      d = cd.f;
      d2 = rnd();
      d3 = rnd();
      zo = rnd();
      break;
    case 2:
      cd.f = rnd_fr() * M_PI_4;
      cd.i32 += (random() & 0xf) - 0x7;
      d = cd.f;
      d2 = rnd();
      d3 = rnd();
      zo = rnd();
      break;
    default:
      d = rnd_fr();
      d2 = rnd_fr();
      d3 = rnd_fr();
      zo = rnd_zo();
      break;
    }

    Sleef_float2 sc  = xsincospif_u05(d);
    Sleef_float2 sc2 = xsincospif_u35(d);

    {
      const float rangemax2 = 1e+7/4;

      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sinpi(frx, frx, GMP_RNDN);

      double u0 = countULP2sp(t = sc.x, frx);

      if (u0 != 0 && ((fabs(d) <= rangemax2 && u0 > 0.505) || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincospif_u05 sin arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2sp(t = sc2.x, frx);

      if (u1 != 0 && ((fabs(d) <= rangemax2 && u1 > 2.0) || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincospif_u35 sin arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      double u2 = countULP2sp(t = xsinpif_u05(d), frx);

      if (u2 != 0 && ((fabs(d) <= rangemax2 && u2 > 0.506) || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sinpif_u05 arg=%.20g ulp=%.20g\n", d, u2);
        printf("correct = %g, test = %g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      const float rangemax2 = 1e+7/4;

      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cospi(frx, frx, GMP_RNDN);

      double u0 = countULP2sp(t = sc.y, frx);

      if (u0 != 0 && ((fabs(d) <= rangemax2 && u0 > 0.505) || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincospif_u05 cos arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2sp(t = sc.y, frx);

      if (u1 != 0 && ((fabs(d) <= rangemax2 && u1 > 2.0) || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincospif_u35 cos arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      double u2 = countULP2sp(t = xcospif_u05(d), frx);

      if (u2 != 0 && ((fabs(d) <= rangemax2 && u2 > 0.506) || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C cospif_u05 arg=%.20g ulp=%.20g\n", d, u2);
        printf("correct = %g, test = %g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    sc = xsincosf(d);
    sc2 = xsincosf_u1(d);

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sin(frx, frx, GMP_RNDN);

      float u0 = countULPsp(t = xsinf(d), frx);

      if (u0 != 0 && (u0 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sinf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      float u1 = countULPsp(t = sc.x, frx);

      if (u1 != 0 && (u1 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincosf sin arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      float u2 = countULPsp(t = xsinf_u1(d), frx);

      if (u2 != 0 && (u2 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sinf_u1 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }

      float u3 = countULPsp(t = sc2.x, frx);

      if (u3 != 0 && (u3 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincosf_u1 sin arg=%.20g ulp=%.20g\n", d, u3);
        fflush(stdout); ecnt++;
      }

      float u4 = countULPsp(t = xfastsinf_u3500(d), frx);
      double ae4 = fabs(mpfr_get_d(frx, GMP_RNDN) - t);

      if (u4 > 350 && ae4 > 2e-6) {
        printf("Pure C fastsinf_u3500 arg=%.20g ulp=%.20g\n", d, u4);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cos(frx, frx, GMP_RNDN);

      float u0 = countULPsp(t = xcosf(d), frx);

      if (u0 != 0 && (u0 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C cosf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      float u1 = countULPsp(t = sc.y, frx);

      if (u1 != 0 && (u1 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincosf cos arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      float u2 = countULPsp(t = xcosf_u1(d), frx);

      if (u2 != 0 && (u2 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C cosf_u1 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }

      float u3 = countULPsp(t = sc2.y, frx);

      if (u3 != 0 && (u3 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincosf_u1 cos arg=%.20g ulp=%.20g\n", d, u3);
        fflush(stdout); ecnt++;
      }

      float u4 = countULPsp(t = xfastcosf_u3500(d), frx);
      double ae4 = fabs(mpfr_get_d(frx, GMP_RNDN) - t);

      if (u4 > 350 && ae4 > 2e-6) {
        printf("Pure C fastcosf_u3500 arg=%.20g ulp=%.20g\n", d, u4);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_tan(frx, frx, GMP_RNDN);

      float u0 = countULPsp(t = xtanf(d), frx);

      if (u0 != 0 && (u0 > 3.5 || isnan(t))) {
        printf("Pure C tanf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      float u1 = countULPsp(t = xtanf_u1(d), frx);

      if (u1 != 0 && (u1 > 1 || isnan(t))) {
        printf("Pure C tanf_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, fabsf(d), GMP_RNDN);
      mpfr_log(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xlogf(fabsf(d)), frx);

      if (u0 > 3.5) {
        printf("Pure C logf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = xlogf_u1(fabsf(d)), frx);

      if (u1 > 1) {
        printf("Pure C logf_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, fabsf(d), GMP_RNDN);
      mpfr_log10(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xlog10f(fabsf(d)), frx);

      if (u0 > 1) {
        printf("Pure C log10f arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, fabsf(d), GMP_RNDN);
      mpfr_log2(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xlog2f(fabsf(d)), frx);

      if (u0 > 1) {
        printf("Pure C log2f arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = xlog2f_u35(fabsf(d)), frx);

      if (u1 > 3.5) {
        printf("Pure C log2f_u35 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_log1p(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xlog1pf(d), frx);

      if ((-1 <= d && d <= 1e+38 && u0 > 1) ||
          (d < -1 && !isnan(t)) ||
          (d > 1e+38 && !(u0 <= 1 || isinf(t)))) {
        printf("Pure C log1pf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xexpf(d), frx);

      if (u0 > 1) {
        printf("Pure C expf arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %g, test = %g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp2(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xexp2f(d), frx);

      if (u0 > 1) {
        printf("Pure C exp2f arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = xexp2f_u35(d), frx);

      if (u1 > 3.5) {
        printf("Pure C exp2f_u35 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp10(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xexp10f(d), frx);

      if (u0 > 1) {
        printf("Pure C exp10f arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = xexp10f_u35(d), frx);

      if (u1 > 3.5) {
        printf("Pure C exp10f_u35 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_expm1(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xexpm1f(d), frx);

      if (u0 > 1) {
        printf("Pure C expm1f arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_pow(frx, fry, frx, GMP_RNDN);

      double u0 = countULPsp(t = xpowf(d2, d), frx);

      if (u0 > 1) {
        printf("Pure C powf arg=%.20g, %.20g ulp=%.20g\n", d2, d, u0);
        fflush(stdout); ecnt++;
      }

      if (isnumber(d) && isnumber(d2)) {
        double u1 = countULPsp(t = xfastpowf_u3500(d2, d), frx);

        if (isnumber((float)mpfr_get_d(frx, GMP_RNDN)) && u1 > 350) {
          printf("Pure C fastpowf_u3500 arg=%.20g, %.20g ulp=%.20g\n", d2, d, u1);
          fflush(stdout); ecnt++;
        }
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cbrt(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xcbrtf(d), frx);

      if (u0 > 3.5) {
        printf("Pure C cbrtf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = xcbrtf_u1(d), frx);

      if (u1 > 1) {
        printf("Pure C cbrtf_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, zo, GMP_RNDN);
      mpfr_asin(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xasinf(zo), frx);

      if (u0 > 3.5) {
        printf("Pure C asinf arg=%.20g ulp=%.20g\n", zo, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = xasinf_u1(zo), frx);

      if (u1 > 1) {
        printf("Pure C asinf_u1 arg=%.20g ulp=%.20g\n", zo, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, zo, GMP_RNDN);
      mpfr_acos(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xacosf(zo), frx);

      if (u0 > 3.5) {
        printf("Pure C acosf arg=%.20g ulp=%.20g\n", zo, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = xacosf_u1(zo), frx);

      if (u1 > 1) {
        printf("Pure C acosf_u1 arg=%.20g ulp=%.20g\n", zo, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_atan(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xatanf(d), frx);

      if (u0 > 3.5) {
        printf("Pure C atanf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = xatanf_u1(d), frx);

      if (u1 > 1) {
        printf("Pure C atanf_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_atan2(frx, fry, frx, GMP_RNDN);

      double u0 = countULPsp(t = xatan2f(d2, d), frx);

      if (u0 > 3.5) {
        printf("Pure C atan2f arg=%.20g, %.20g ulp=%.20g\n", d2, d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2sp(t = xatan2f_u1(d2, d), frx);

      if (u1 > 1) {
        printf("Pure C atan2f_u1 arg=%.20g, %.20g ulp=%.20g\n", d2, d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sinh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xsinhf(d), frx);

      if ((fabs(d) <= 88.5 && u0 > 1) ||
          (d >  88.5 && !(u0 <= 1 || (isinf(t) && t > 0))) ||
          (d < -88.5 && !(u0 <= 1 || (isinf(t) && t < 0)))) {
        printf("Pure C sinhf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cosh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xcoshf(d), frx);

      if ((fabs(d) <= 88.5 && u0 > 1) || !(u0 <= 1 || (isinf(t) && t > 0))) {
        printf("Pure C coshf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_tanh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xtanhf(d), frx);

      if (u0 > 1.0001) {
        printf("Pure C tanhf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sinh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xsinhf_u35(d), frx);

      if ((fabs(d) <= 88 && u0 > 3.5) ||
          (d >  88 && !(u0 <= 3.5 || (isinf(t) && t > 0))) ||
          (d < -88 && !(u0 <= 3.5 || (isinf(t) && t < 0)))) {
        printf("Pure C sinhf_u35 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cosh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xcoshf_u35(d), frx);

      if ((fabs(d) <= 88 && u0 > 3.5) || !(u0 <= 3.5 || (isinf(t) && t > 0))) {
        printf("Pure C coshf_u35 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_tanh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xtanhf_u35(d), frx);

      if (u0 > 3.5) {
        printf("Pure C tanhf_u35 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_asinh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xasinhf(d), frx);

      if ((fabs(d) < sqrt(FLT_MAX) && u0 > 1.0001) ||
          (d >=  sqrt(FLT_MAX) && !(u0 <= 1.0001 || (isinf(t) && t > 0))) ||
          (d <= -sqrt(FLT_MAX) && !(u0 <= 1.0001 || (isinf(t) && t < 0)))) {
        printf("Pure C asinhf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_acosh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xacoshf(d), frx);

      if ((fabs(d) < sqrt(FLT_MAX) && u0 > 1.0001) ||
          (d >=  sqrt(FLT_MAX) && !(u0 <= 1.0001 || (isinff(t) && t > 0))) ||
          (d <= -sqrt(FLT_MAX) && !isnan(t))) {
        printf("Pure C acoshf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_atanh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xatanhf(d), frx);

      if (u0 > 1.0001) {
        printf("Pure C atanhf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    //

    {
      int exp = (random() & 8191) - 4096;
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_exp(frx, mpfr_get_exp(frx) + exp);

      double u0 = countULPsp(t = xldexpf(d, exp), frx);

      if (u0 > 0.5002) {
        printf("Pure C ldexpf arg=%.20g %d ulp=%.20g\n", d, exp, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_abs(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xfabsf(d), frx);

      if (u0 != 0) {
        printf("Pure C fabsf arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_copysign(frx, frx, fry, GMP_RNDN);

      double u0 = countULPsp(t = xcopysignf(d, d2), frx);

      if (u0 != 0 && !isnan(d2)) {
        printf("Pure C copysignf arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %g, test = %g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_max(frx, frx, fry, GMP_RNDN);

      double u0 = countULPsp(t = xfmaxf(d, d2), frx);

      if (u0 != 0) {
        printf("Pure C fmaxf arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_min(frx, frx, fry, GMP_RNDN);

      double u0 = countULPsp(t = xfminf(d, d2), frx);

      if (u0 != 0) {
        printf("Pure C fminf arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_dim(frx, frx, fry, GMP_RNDN);

      double u0 = countULPsp(t = xfdimf(d, d2), frx);

      if (u0 > 0.5) {
        printf("Pure C fdimf arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_trunc(frx, frx);

      double u0 = countULPsp(t = xtruncf(d), frx);

      if (u0 != 0) {
        printf("Pure C truncf arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_floor(frx, frx);

      double u0 = countULPsp(t = xfloorf(d), frx);

      if (u0 != 0) {
        printf("Pure C floorf arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_ceil(frx, frx);

      double u0 = countULPsp(t = xceilf(d), frx);

      if (u0 != 0) {
        printf("Pure C ceilf arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_round(frx, frx);

      double u0 = countULPsp(t = xroundf(d), frx);

      if (u0 != 0) {
        printf("Pure C roundf arg=%.24g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_rint(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xrintf(d), frx);

      if (u0 != 0) {
        printf("Pure C rintf arg=%.24g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_modf(fry, frz, frx, GMP_RNDN);

      Sleef_float2 t2 = xmodff(d);
      double u0 = countULPsp(t2.x, frz);
      double u1 = countULPsp(t2.y, fry);

      if (u0 != 0 || u1 != 0) {
        printf("Pure C modff arg=%.20g ulp=%.20g %.20g\n", d, u0, u1);
        printf("correct = %.20g, %.20g\n", mpfr_get_d(frz, GMP_RNDN), mpfr_get_d(fry, GMP_RNDN));
        printf("test    = %.20g, %.20g\n", t2.x, t2.y);
        fflush(stdout); ecnt++;
      }
    }


    {
      t = xnextafterf(d, d2);
      double c = nextafterf(d, d2);

      if (!(isnan(t) && isnan(c)) && t != c) {
        printf("Pure C nextafterf arg=%.20g, %.20g\n", d, d2);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_exp(frx, 0);

      double u0 = countULPsp(t = xfrfrexpf(d), frx);

      if (d != 0 && isnumber(d) && u0 != 0) {
        printf("Pure C frfrexpf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      int cexp = mpfr_get_exp(frx);

      int texp = xexpfrexpf(d);

      if (d != 0 && isnumber(d) && cexp != texp) {
        printf("Pure C expfrexpf arg=%.20g\n", d);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_hypot(frx, frx, fry, GMP_RNDN);

      double u0 = countULP2sp(t = xhypotf_u05(d, d2), frx);

      if (u0 > 0.5001) {
        printf("Pure C hypotf_u05 arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_hypot(frx, frx, fry, GMP_RNDN);

      double u0 = countULP2sp(t = xhypotf_u35(d, d2), frx);

      if (u0 >= 3.5) {
        printf("Pure C hypotf_u35 arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_fmod(frx, frx, fry, GMP_RNDN);

      double u0 = countULPsp(t = xfmodf(d, d2), frx);

      if (fabs((double)d / d2) < 1e+38 && u0 > 0.5) {
        printf("Pure C fmodf arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_remainder(frx, frx, fry, GMP_RNDN);

      double u0 = countULPsp(t = xremainderf(d, d2), frx);

      if (fabs((double)d / d2) < 1e+38 && u0 > 0.5) {
        printf("Pure C remainderf arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_set_d(frz, d3, GMP_RNDN);
      mpfr_fma(frx, frx, fry, frz, GMP_RNDN);

      double u0 = countULP2sp(t = xfmaf(d, d2, d3), frx);
      double c = mpfr_get_d(frx, GMP_RNDN);

      if ((-1e+34 < c && c < 1e+33 && u0 > 0.5001) ||
          !(u0 <= 0.5001 || isinf(t))) {
        printf("Pure C fmaf arg=%.20g, %.20g, %.20g  ulp=%.20g\n", d, d2, d3, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sqrt(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xsqrtf_u05(d), frx);

      if (u0 > 0.5001) {
        printf("Pure C sqrtf_u05 arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sqrt(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = xsqrtf_u35(d), frx);

      if (u0 > 3.5) {
        printf("Pure C sqrtf_u35 arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_erfc(frx, frx, GMP_RNDN);

      double u0 = countULP2sp(t = xerfcf_u15(d), frx);

      if (u0 > 1.5) {
        printf("Pure C erfcf arg=%.20g ulp=%.20g\n", d, u0);
        printf("Correct = %.20Lg, test = %.20g\n", mpfr_get_ld(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_erf(frx, frx, GMP_RNDN);

      double u0 = countULP2sp(t = xerff_u1(d), frx);

      if (u0 > 0.75) {
        printf("Pure C erff arg=%.20g ulp=%.20g\n", d, u0);
        printf("Correct = %.20Lg, test = %.20g\n", mpfr_get_ld(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      int s;
      mpfr_lgamma(frx, &s, frx, GMP_RNDN);

      double u0 = countULPsp(t = xlgammaf_u1(d), frx);

      if (((d < 0 && fabsl(t - mpfr_get_ld(frx, GMP_RNDN)) > 1e-8 && u0 > 1) ||
           (0 <= d && d < 4e+36 && u0 > 1) || (4e+36 <= d && !(u0 <= 1 || isinf(t))))) {
        printf("Pure C xlgammaf arg=%.20g ulp=%.20g\n", d, u0);
        printf("Correct = %.20Lg, test = %.20g\n", mpfr_get_ld(frx, GMP_RNDN), t);
        printf("Diff = %.20Lg\n", fabsl(t - mpfr_get_ld(frx, GMP_RNDN)));
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_gamma(frx, frx, GMP_RNDN);

      double u0 = countULP2sp(t = xtgammaf_u1(d), frx);
      double c = mpfr_get_d(frx, GMP_RNDN);

      if (isnumber(c) || isnumber(t)) {
        if (u0 > 1.0) {
          printf("Pure C xtgamma arg=%.20g ulp=%.20g\n", d, u0);
          printf("Correct = %.20Lg, test = %.20g\n", mpfr_get_ld(frx, GMP_RNDN), t);
          fflush(stdout); ecnt++;
        }
      }
    }
  }
  mpfr_clears(frw, frx, fry, frz, NULL);

  exit(0);
}
