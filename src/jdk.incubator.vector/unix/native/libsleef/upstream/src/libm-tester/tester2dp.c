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

#define DENORMAL_DBL_MIN (4.9406564584124654418e-324)
#define POSITIVE_INFINITY INFINITY
#define NEGATIVE_INFINITY (-INFINITY)

typedef union {
  double d;
  uint64_t u64;
  int64_t i64;
} conv_t;

double nexttoward0(double x, int n) {
  union {
    double f;
    uint64_t u;
  } cx;
  cx.f = x;
  cx.u -=n ;
  return cx.f;
}

double rnd() {
  conv_t c;
  switch(random() & 63) {
  case 0: return nexttoward0( 0.0, -(random() & ((1 << (random() & 31)) - 1)));
  case 1: return nexttoward0(-0.0, -(random() & ((1 << (random() & 31)) - 1)));
  case 2: return nexttoward0( INFINITY, (random() & ((1 << (random() & 31)) - 1)));
  case 3: return nexttoward0(-INFINITY, (random() & ((1 << (random() & 31)) - 1)));
  }
#ifdef ENABLE_SYS_getrandom
  syscall(SYS_getrandom, &c.u64, sizeof(c.u64), 0);
#else
  c.u64 = random() | ((uint64_t)random() << 31) | ((uint64_t)random() << 62);
#endif
  return c.d;
}

double rnd_fr() {
  conv_t c;
  do {
#ifdef ENABLE_SYS_getrandom
    syscall(SYS_getrandom, &c.u64, sizeof(c.u64), 0);
#else
    c.u64 = random() | ((uint64_t)random() << 31) | ((uint64_t)random() << 62);
#endif
  } while(!isnumber(c.d));
  return c.d;
}

double rnd_zo() {
  conv_t c;
  do {
#ifdef ENABLE_SYS_getrandom
    syscall(SYS_getrandom, &c.u64, sizeof(c.u64), 0);
#else
    c.u64 = random() | ((uint64_t)random() << 31) | ((uint64_t)random() << 62);
#endif
  } while(!isnumber(c.d) || c.d < -1 || 1 < c.d);
  return c.d;
}

int main(int argc,char **argv)
{
  mpfr_t frw, frx, fry, frz;

  mpfr_set_default_prec(1280);
  mpfr_inits(frw, frx, fry, frz, NULL);

  conv_t cd;
  double d, t;
  double d2, d3, zo;

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
      cd.d = rint(rnd_zo() * 1e+10) * M_PI_4;
      cd.i64 += (random() & 0xff) - 0x7f;
      d = cd.d;
      d2 = rnd();
      d3 = rnd();
      zo = rnd();
      break;
    case 2:
      cd.d = rnd_fr() * M_PI_4;
      cd.i64 += (random() & 0xf) - 0x7;
      d = cd.d;
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

    Sleef_double2 sc  = xsincospi_u05(d);
    Sleef_double2 sc2 = xsincospi_u35(d);

    {
      const double rangemax2 = 1e+9/4;

      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sinpi(frx, frx, GMP_RNDN);

      double u0 = countULP2dp(t = sc.x, frx);

      if (u0 != 0 && ((fabs(d) <= rangemax2 && u0 > 0.506) || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincospi_u05 sin arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2dp(t = sc2.x, frx);

      if (u1 != 0 && ((fabs(d) <= rangemax2 && u1 > 1.5) || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincospi_u35 sin arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      double u2 = countULP2dp(t = xsinpi_u05(d), frx);

      if (u2 != 0 && ((fabs(d) <= rangemax2 && u2 > 0.506) || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sinpi_u05 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }
    }

    {
      const double rangemax2 = 1e+9/4;

      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cospi(frx, frx, GMP_RNDN);

      double u0 = countULP2dp(t = sc.y, frx);

      if (u0 != 0 && ((fabs(d) <= rangemax2 && u0 > 0.506) || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincospi_u05 cos arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2dp(t = sc.y, frx);

      if (u1 != 0 && ((fabs(d) <= rangemax2 && u1 > 1.5) || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincospi_u35 cos arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      double u2 = countULP2dp(t = xcospi_u05(d), frx);

      if (u2 != 0 && ((fabs(d) <= rangemax2 && u2 > 0.506) || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C cospi_u05 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }
    }

    sc = xsincos(d);
    sc2 = xsincos_u1(d);

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sin(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xsin(d), frx);

      if (u0 != 0 && (u0 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sin arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(sc.x, frx);

      if (u1 != 0 && (u1 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincos sin arg=%.20g ulp=%.20g\n", d, u1);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }

      double u2 = countULPdp(t = xsin_u1(d), frx);

      if (u2 != 0 && (u2 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sin_u1 arg=%.20g ulp=%.20g\n", d, u2);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }

      double u3 = countULPdp(t = sc2.x, frx);

      if (u3 != 0 && (u3 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincos_u1 sin arg=%.20g ulp=%.20g\n", d, u3);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cos(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xcos(d), frx);

      if (u0 != 0 && (u0 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C cos arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = sc.y, frx);

      if (u1 != 0 && (u1 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincos cos arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      double u2 = countULPdp(t = xcos_u1(d), frx);

      if (u2 != 0 && (u2 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C cos_u1 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }

      double u3 = countULPdp(t = sc2.y, frx);

      if (u3 != 0 && (u3 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf("Pure C sincos_u1 cos arg=%.20g ulp=%.20g\n", d, u3);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_tan(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xtan(d), frx);

      if (u0 != 0 && (u0 > 3.5 || isnan(t))) {
        printf("Pure C tan arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = xtan_u1(d), frx);

      if (u1 != 0 && (u1 > 1 || isnan(t))) {
        printf("Pure C tan_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, fabs(d), GMP_RNDN);
      mpfr_log(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xlog(fabs(d)), frx);

      if (u0 > 3.5) {
        printf("Pure C log arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = xlog_u1(fabs(d)), frx);

      if (u1 > 1) {
        printf("Pure C log_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, fabs(d), GMP_RNDN);
      mpfr_log10(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xlog10(fabs(d)), frx);

      if (u0 > 1) {
        printf("Pure C log10 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, fabs(d), GMP_RNDN);
      mpfr_log2(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xlog2(fabs(d)), frx);

      if (u0 > 1) {
        printf("Pure C log2 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = xlog2_u35(fabs(d)), frx);

      if (u1 > 3.5) {
        printf("Pure C log2_u35 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_log1p(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xlog1p(d), frx);

      if ((-1 <= d && d <= 1e+307 && u0 > 1) ||
          (d < -1 && !isnan(t)) ||
          (d > 1e+307 && !(u0 <= 1 || isinf(t)))) {
        printf("Pure C log1p arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xexp(d), frx);

      if (u0 > 1) {
        printf("Pure C exp arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp2(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xexp2(d), frx);

      if (u0 > 1) {
        printf("Pure C exp2 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = xexp2_u35(d), frx);

      if (u1 > 3.5) {
        printf("Pure C exp2_u35 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp10(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xexp10(d), frx);

      if (u0 > 1.09) {
        printf("Pure C exp10 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = xexp10_u35(d), frx);

      if (u1 > 3.5) {
        printf("Pure C exp10_u35 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_expm1(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xexpm1(d), frx);

      if (u0 > 1) {
        printf("Pure C expm1 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_pow(frx, fry, frx, GMP_RNDN);

      double u0 = countULPdp(t = xpow(d2, d), frx);

      if (u0 > 1) {
        printf("Pure C pow arg=%.20g, %.20g ulp=%.20g\n", d2, d, u0);
        printf("correct = %g, test = %g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cbrt(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xcbrt(d), frx);

      if (u0 > 3.5) {
        printf("Pure C cbrt arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = xcbrt_u1(d), frx);

      if (u1 > 1) {
        printf("Pure C cbrt_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, zo, GMP_RNDN);
      mpfr_asin(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xasin(zo), frx);

      if (u0 > 3.5) {
        printf("Pure C asin arg=%.20g ulp=%.20g\n", zo, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = xasin_u1(zo), frx);

      if (u1 > 1) {
        printf("Pure C asin_u1 arg=%.20g ulp=%.20g\n", zo, u1);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, zo, GMP_RNDN);
      mpfr_acos(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xacos(zo), frx);

      if (u0 > 3.5) {
        printf("Pure C acos arg=%.20g ulp=%.20g\n", zo, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = xacos_u1(zo), frx);

      if (u1 > 1) {
        printf("Pure C acos_u1 arg=%.20g ulp=%.20g\n", zo, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_atan(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xatan(d), frx);

      if (u0 > 3.5) {
        printf("Pure C atan arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = xatan_u1(d), frx);

      if (u1 > 1) {
        printf("Pure C atan_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_atan2(frx, fry, frx, GMP_RNDN);

      double u0 = countULPdp(t = xatan2(d2, d), frx);

      if (u0 > 3.5) {
        printf("Pure C atan2 arg=%.20g, %.20g ulp=%.20g\n", d2, d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2dp(t = xatan2_u1(d2, d), frx);

      if (u1 > 1) {
        printf("Pure C atan2_u1 arg=%.20g, %.20g ulp=%.20g\n", d2, d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sinh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xsinh(d), frx);

      if ((fabs(d) <= 709 && u0 > 1) ||
          (d >  709 && !(u0 <= 1 || (isinf(t) && t > 0))) ||
          (d < -709 && !(u0 <= 1 || (isinf(t) && t < 0)))) {
        printf("Pure C sinh arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cosh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xcosh(d), frx);

      if ((fabs(d) <= 709 && u0 > 1) || !(u0 <= 1 || (isinf(t) && t > 0))) {
        printf("Pure C cosh arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_tanh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xtanh(d), frx);

      if (u0 > 1) {
        printf("Pure C tanh arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sinh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xsinh_u35(d), frx);

      if ((fabs(d) <= 709 && u0 > 3.5) ||
          (d >  709 && !(u0 <= 3.5 || (isinf(t) && t > 0))) ||
          (d < -709 && !(u0 <= 3.5 || (isinf(t) && t < 0)))) {
        printf("Pure C sinh_u35 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cosh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xcosh_u35(d), frx);

      if ((fabs(d) <= 709 && u0 > 3.5) || !(u0 <= 3.5 || (isinf(t) && t > 0))) {
        printf("Pure C cosh_u35 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_tanh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xtanh_u35(d), frx);

      if (u0 > 3.5) {
        printf("Pure C tanh_u35 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_asinh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xasinh(d), frx);

      if ((fabs(d) < sqrt(DBL_MAX) && u0 > 1) ||
          (d >=  sqrt(DBL_MAX) && !(u0 <= 1 || (isinf(t) && t > 0))) ||
          (d <= -sqrt(DBL_MAX) && !(u0 <= 1 || (isinf(t) && t < 0)))) {
        printf("Pure C asinh arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_acosh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xacosh(d), frx);

      if ((fabs(d) < sqrt(DBL_MAX) && u0 > 1) ||
          (d >=  sqrt(DBL_MAX) && !(u0 <= 1 || (isinf(t) && t > 0))) ||
          (d <= -sqrt(DBL_MAX) && !isnan(t))) {
        printf("Pure C acosh arg=%.20g ulp=%.20g\n", d, u0);
        printf("%.20g\n", t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_atanh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xatanh(d), frx);

      if (u0 > 1) {
        printf("Pure C atanh arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    //

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_abs(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xfabs(d), frx);

      if (u0 != 0) {
        printf("Pure C fabs arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_copysign(frx, frx, fry, GMP_RNDN);

      double u0 = countULPdp(t = xcopysign(d, d2), frx);

      if (u0 != 0 && !isnan(d2)) {
        printf("Pure C copysign arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %g, test = %g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_max(frx, frx, fry, GMP_RNDN);

      double u0 = countULPdp(t = xfmax(d, d2), frx);

      if (u0 != 0) {
        printf("Pure C fmax arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_min(frx, frx, fry, GMP_RNDN);

      double u0 = countULPdp(t = xfmin(d, d2), frx);

      if (u0 != 0) {
        printf("Pure C fmin arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_dim(frx, frx, fry, GMP_RNDN);

      double u0 = countULPdp(t = xfdim(d, d2), frx);

      if (u0 > 0.5) {
        printf("Pure C fdim arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_trunc(frx, frx);

      double u0 = countULPdp(t = xtrunc(d), frx);

      if (u0 != 0) {
        printf("Pure C trunc arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_floor(frx, frx);

      double u0 = countULPdp(t = xfloor(d), frx);

      if (u0 != 0) {
        printf("Pure C floor arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_ceil(frx, frx);

      double u0 = countULPdp(t = xceil(d), frx);

      if (u0 != 0) {
        printf("Pure C ceil arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_round(frx, frx);

      double u0 = countULPdp(t = xround(d), frx);

      if (u0 != 0) {
        printf("Pure C round arg=%.24g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_rint(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xrint(d), frx);

      if (u0 != 0) {
        printf("Pure C rint arg=%.24g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_set_d(frz, d3, GMP_RNDN);
      mpfr_fma(frx, frx, fry, frz, GMP_RNDN);

      double u0 = countULP2dp(t = xfma(d, d2, d3), frx);
      double c = mpfr_get_d(frx, GMP_RNDN);

      if ((-1e+303 < c && c < 1e+303 && u0 > 0.5) ||
          !(u0 <= 0.5 || isinf(t))) {
        printf("Pure C fma arg=%.20g, %.20g, %.20g  ulp=%.20g\n", d, d2, d3, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sqrt(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = xsqrt_u05(d), frx);

      if (u0 > 0.50001) {
        printf("Pure C sqrt_u05 arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_hypot(frx, frx, fry, GMP_RNDN);

      double u0 = countULP2dp(t = xhypot_u05(d, d2), frx);

      if (u0 > 0.5) {
        printf("Pure C hypot arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_hypot(frx, frx, fry, GMP_RNDN);

      double u0 = countULP2dp(t = xhypot_u35(d, d2), frx);
      double c = mpfr_get_d(frx, GMP_RNDN);

      if ((-1e+308 < c && c < 1e+308 && u0 > 3.5) ||
          !(u0 <= 3.5 || isinf(t))) {
        printf("Pure C hypot arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      t = xnextafter(d, d2);
      double c = nextafter(d, d2);

      if (!(isnan(t) && isnan(c)) && t != c) {
        printf("Pure C nextafter arg=%.20g, %.20g\n", d, d2);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_exp(frx, 0);

      double u0 = countULPdp(t = xfrfrexp(d), frx);

      if (d != 0 && isnumber(d) && u0 != 0) {
        printf("Pure C frfrexp arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      int cexp = mpfr_get_exp(frx);

      int texp = xexpfrexp(d);

      if (d != 0 && isnumber(d) && cexp != texp) {
        printf("Pure C expfrexp arg=%.20g\n", d);
        printf("correct = %d, test = %d\n", cexp, texp);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_fmod(frx, frx, fry, GMP_RNDN);

      double u0 = countULPdp(t = xfmod(d, d2), frx);

      if (fabsl((long double)d / d2) < 1e+300 && u0 > 0.5) {
        printf("Pure C fmod arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_remainder(frx, frx, fry, GMP_RNDN);

      double u0 = countULPdp(t = xremainder(d, d2), frx);

      if (fabsl((long double)d / d2) < 1e+300 && u0 > 0.5) {
        printf("Pure C remainder arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      int exp = (random() & 8191) - 4096;
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_exp(frx, mpfr_get_exp(frx) + exp);

      double u0 = countULPdp(t = xldexp(d, exp), frx);

      if (u0 > 0.5) {
        printf("Pure C ldexp arg=%.20g %d ulp=%.20g\n", d, exp, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_modf(fry, frz, frx, GMP_RNDN);

      Sleef_double2 t2 = xmodf(d);
      double u0 = countULPdp(t2.x, frz);
      double u1 = countULPdp(t2.y, fry);

      if (u0 != 0 || u1 != 0) {
        printf("Pure C modf arg=%.20g ulp=%.20g %.20g\n", d, u0, u1);
        printf("correct = %.20g, %.20g\n", mpfr_get_d(frz, GMP_RNDN), mpfr_get_d(fry, GMP_RNDN));
        printf("test    = %.20g, %.20g\n", t2.x, t2.y);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      int s;
      mpfr_lgamma(frx, &s, frx, GMP_RNDN);

      double u0 = countULPdp(t = xlgamma_u1(d), frx);

      if (((d < 0 && fabsl(t - mpfr_get_ld(frx, GMP_RNDN)) > 1e-15 && u0 > 1) || (0 <= d && d < 2e+305 && u0 > 1) || (2e+305 <= d && !(u0 <= 1 || isinf(t))))) {
        printf("Pure C xlgamma_u1 arg=%.20g ulp=%.20g\n", d, u0);
        printf("Correct = %.20Lg, test = %.20g\n", mpfr_get_ld(frx, GMP_RNDN), t);
        printf("Diff = %.20Lg\n", fabsl(t - mpfr_get_ld(frx, GMP_RNDN)));
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_gamma(frx, frx, GMP_RNDN);

      double u0 = countULP2dp(t = xtgamma_u1(d), frx);

      if (u0 > 1.0) {
        printf("Pure C xtgamma_u1 arg=%.20g ulp=%.20g\n", d, u0);
        printf("Correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        printf("Diff = %.20Lg\n", fabsl(t - mpfr_get_ld(frx, GMP_RNDN)));
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_erfc(frx, frx, GMP_RNDN);

      static double ebz = 9.8813129168249308835e-324; // nextafter(nextafter(0, 1), 1);

      double u0 = countULP2dp(t = xerfc_u15(d), frx);

      if ((d > 26.2 && u0 > 2.5 && !(mpfr_get_d(frx, GMP_RNDN) == 0 && t <= ebz)) || (d <= 26.2 && u0 > 1.5)) {
        printf("Pure C xerfc_u15 arg=%.20g ulp=%.20g\n", d, u0);
        printf("Correct = %.20Lg, test = %.20g\n", mpfr_get_ld(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_erf(frx, frx, GMP_RNDN);

      double u0 = countULP2dp(t = xerf_u1(d), frx);

      if (u0 > 0.75) {
        printf("Pure C xerf_u1 arg=%.20g ulp=%.20g\n", d, u0);
        printf("Correct = %.20Lg, test = %.20g\n", mpfr_get_ld(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }
  }
  mpfr_clears(frw, frx, fry, frz, NULL);
  exit(0);
}
