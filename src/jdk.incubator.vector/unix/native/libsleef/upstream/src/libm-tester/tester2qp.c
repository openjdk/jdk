//   Copyright Naoki Shibata and contributors 2010 - 2024.
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
#include <assert.h>

#include <math.h>
#include <quadmath.h>

#ifdef ENABLE_SYS_getrandom
#define _GNU_SOURCE
#include <unistd.h>
#include <sys/syscall.h>
#include <linux/random.h>
#endif

#include "sleef.h"

#include "f128util.h"

#define DORENAME
#include "rename.h"

#define POSITIVE_INFINITY INFINITY
#define NEGATIVE_INFINITY (-INFINITY)

int isnumberq(Sleef_quad x) { return !isinfq(x) && !isnanq(x); }
int isPlusZeroq(Sleef_quad x) { return x == 0 && copysignq(1, x) == 1; }
int isMinusZeroq(Sleef_quad x) { return x == 0 && copysignq(1, x) == -1; }

mpfr_t fra, frb, frc, frd;

double countULP(Sleef_quad d, mpfr_t c) {
  Sleef_quad c2 = mpfr_get_f128(c, GMP_RNDN);
  if (c2 == 0 && d != 0) return 10000;
  //if (isPlusZeroq(c2) && !isPlusZeroq(d)) return 10003;
  //if (isMinusZeroq(c2) && !isMinusZeroq(d)) return 10004;
  if (isnanq(c2) && isnanq(d)) return 0;
  if (isnanq(c2) || isnanq(d)) return 10001;
  if (c2 == POSITIVE_INFINITY && d == POSITIVE_INFINITY) return 0;
  if (c2 == NEGATIVE_INFINITY && d == NEGATIVE_INFINITY) return 0;
  if (!isnumberq(c2) && !isnumberq(d)) return 0;

  int e;
  frexpq(mpfr_get_f128(c, GMP_RNDN), &e);
  mpfr_set_f128(frb, fmaxq(ldexpq(1.0, e-113), FLT128_DENORM_MIN), GMP_RNDN);

  mpfr_set_f128(frd, d, GMP_RNDN);
  mpfr_sub(fra, frd, c, GMP_RNDN);
  mpfr_div(fra, fra, frb, GMP_RNDN);
  double u = fabs(mpfr_get_d(fra, GMP_RNDN));

  return u;
}

double countULP2(Sleef_quad d, mpfr_t c) {
  Sleef_quad c2 = mpfr_get_f128(c, GMP_RNDN);
  if (c2 == 0 && d != 0) return 10000;
  //if (isPlusZeroq(c2) && !isPlusZeroq(d)) return 10003;
  //if (isMinusZeroq(c2) && !isMinusZeroq(d)) return 10004;
  if (isnanq(c2) && isnanq(d)) return 0;
  if (isnanq(c2) || isnanq(d)) return 10001;
  if (c2 == POSITIVE_INFINITY && d == POSITIVE_INFINITY) return 0;
  if (c2 == NEGATIVE_INFINITY && d == NEGATIVE_INFINITY) return 0;
  if (!isnumberq(c2) && !isnumberq(d)) return 0;

  int e;
  frexpq(mpfr_get_f128(c, GMP_RNDN), &e);
  mpfr_set_f128(frb, fmaxq(ldexpq(1.0, e-113), FLT128_MIN), GMP_RNDN);

  mpfr_set_f128(frd, d, GMP_RNDN);
  mpfr_sub(fra, frd, c, GMP_RNDN);
  mpfr_div(fra, fra, frb, GMP_RNDN);
  double u = fabs(mpfr_get_d(fra, GMP_RNDN));

  return u;
}

typedef union {
  Sleef_quad d;
  __int128 u128;
  uint64_t u[2];
} conv_t;

Sleef_quad rnd() {
  conv_t c;
  switch(random() & 15) {
  case 0: return  INFINITY;
  case 1: return -INFINITY;
  }
#ifdef ENABLE_SYS_getrandom
  syscall(SYS_getrandom, &c.u128, sizeof(c.u128), 0);
#else
  c.u128 = random() | ((__int128)random() << 31) | ((__int128)random() << (31*2)) | ((__int128)random() << (31*3)) | ((__int128)random() << (31*4));
#endif
  return c.d;

}

Sleef_quad rnd_fr() {
  conv_t c;
  do {
#ifdef ENABLE_SYS_getrandom
    syscall(SYS_getrandom, &c.u128, sizeof(c.u128), 0);
#else
    c.u128 = random() | ((__int128)random() << 31) | ((__int128)random() << (31*2)) | ((__int128)random() << (31*3)) | ((__int128)random() << (31*4));
#endif
  } while(!isnumberq(c.d));
  return c.d;
}

Sleef_quad rnd_zo() {
  conv_t c;
  do {
#ifdef ENABLE_SYS_getrandom
    syscall(SYS_getrandom, &c.u128, sizeof(c.u128), 0);
#else
    c.u128 = random() | ((__int128)random() << 31) | ((__int128)random() << (31*2)) | ((__int128)random() << (31*3)) | ((__int128)random() << (31*4));
#endif
  } while(!isnumberq(c.d) || c.d < -1 || 1 < c.d);
  return c.d;
}

void sinpifr(mpfr_t ret, Sleef_quad d) {
  mpfr_t frpi, frd;
  mpfr_inits(frpi, frd, NULL);

  mpfr_const_pi(frpi, GMP_RNDN);
  mpfr_set_d(frd, 1.0, GMP_RNDN);
  mpfr_mul(frpi, frpi, frd, GMP_RNDN);
  mpfr_set_f128(frd, d, GMP_RNDN);
  mpfr_mul(frd, frpi, frd, GMP_RNDN);
  mpfr_sin(ret, frd, GMP_RNDN);

  mpfr_clears(frpi, frd, NULL);
}

void cospifr(mpfr_t ret, Sleef_quad d) {
  mpfr_t frpi, frd;
  mpfr_inits(frpi, frd, NULL);

  mpfr_const_pi(frpi, GMP_RNDN);
  mpfr_set_d(frd, 1.0, GMP_RNDN);
  mpfr_mul(frpi, frpi, frd, GMP_RNDN);
  mpfr_set_f128(frd, d, GMP_RNDN);
  mpfr_mul(frd, frpi, frd, GMP_RNDN);
  mpfr_cos(ret, frd, GMP_RNDN);

  mpfr_clears(frpi, frd, NULL);
}

int main(int argc,char **argv)
{
  mpfr_t frw, frx, fry, frz;

  mpfr_set_default_prec(2048);
  mpfr_inits(fra, frb, frc, frd, frw, frx, fry, frz, NULL);

  conv_t cd;
  Sleef_quad d, t, d2, zo;

  int cnt, ecnt = 0;

  srandom(time(NULL));

#if 0
  cd.d = M_PIq;
  mpfr_set_f128(frx, cd.d, GMP_RNDN);
  cd.u128 += 3;
  printf("%g\n", countULP2(cd.d, frx));
#endif

  const Sleef_quad rangemax = 1e+9;

  for(cnt = 0;ecnt < 1000;cnt++) {
    switch(cnt & 7) {
    case 0:
      d = rnd();
      d2 = rnd();
      zo = rnd();
      break;
    case 1:
      cd.d = rint((2 * (double)random() / RAND_MAX - 1) * 1e+10) * M_PI_4;
      cd.u128 += (random() & 0xff) - 0x7f;
      d = cd.d;
      d2 = rnd();
      zo = rnd();
      break;
    default:
      d = rnd_fr();
      d2 = rnd_fr();
      zo = rnd_zo();
      break;
    }

    Sleef_quad2 sc  = xsincospiq_u05(d);
    Sleef_quad2 sc2 = xsincospiq_u35(d);

    {
      const double rangemax2 = 1e+9;

      sinpifr(frx, d);

      double u0 = countULP2(t = sc.x, frx);

      if (u0 != 0 && ((fabs(d) <= rangemax2 && u0 > 0.505) || fabs(t) > 1 || !isnumberq(t))) {
        printf("Pure C sincospiq_u05 sin arg="); printf128(d); printf(" ulp=%.20g\n", u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2(t = sc2.x, frx);

      if (u1 != 0 && ((fabs(d) <= rangemax2 && u1 > 2.0) || fabs(t) > 1 || !isnumberq(t))) {
        printf("Pure C sincospiq_u35 sin arg=%.30Lg ulp=%.20g\n", (long double)d, u1);
        fflush(stdout); ecnt++;
      }

    }

    {
      const double rangemax2 = 1e+9;

      cospifr(frx, d);

      double u0 = countULP2(t = sc.y, frx);

      if (u0 != 0 && ((fabs(d) <= rangemax2 && u0 > 0.505) || fabs(t) > 1 || !isnumberq(t))) {
        printf("Pure C sincospiq_u05 cos arg=%.30Lg ulp=%.20g\n", (long double)d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2(t = sc.y, frx);

      if (u1 != 0 && ((fabs(d) <= rangemax2 && u1 > 2.0) || fabs(t) > 1 || !isnumberq(t))) {
        printf("Pure C sincospiq_u35 cos arg=%.30Lg ulp=%.20g\n", (long double)d, u1);
        fflush(stdout); ecnt++;
      }

    }

#if 0
    double2 sc = xsincos(d);
    double2 sc2 = xsincos_u1(d);

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sin(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xsin(d), frx);

      if ((fabs(d) <= rangemax && u0 > 3.5) || fabs(t) > 1 || !isnumberq(t)) {
        printf("Pure C sin arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP(sc.x, frx);

      if ((fabs(d) <= rangemax && u1 > 3.5) || fabs(t) > 1 || !isnumberq(t)) {
        printf("Pure C sincos sin arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      double u2 = countULP(t = xsin_u1(d), frx);

      if ((fabs(d) <= rangemax && u2 > 1) || fabs(t) > 1 || !isnumberq(t)) {
        printf("Pure C sin_u1 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }

      double u3 = countULP(t = sc2.x, frx);

      if ((fabs(d) <= rangemax && u3 > 1) || fabs(t) > 1 || !isnumberq(t)) {
        printf("Pure C sincos_u1 sin arg=%.20g ulp=%.20g\n", d, u3);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cos(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xcos(d), frx);

      if ((fabs(d) <= rangemax && u0 > 3.5) || fabs(t) > 1 || !isnumberq(t)) {
        printf("Pure C cos arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP(t = sc.y, frx);

      if ((fabs(d) <= rangemax && u1 > 3.5) || fabs(t) > 1 || !isnumberq(t)) {
        printf("Pure C sincos cos arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      double u2 = countULP(t = xcos_u1(d), frx);

      if ((fabs(d) <= rangemax && u2 > 1) || fabs(t) > 1 || !isnumberq(t)) {
        printf("Pure C cos_u1 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }

      double u3 = countULP(t = sc2.y, frx);

      if ((fabs(d) <= rangemax && u3 > 1) || fabs(t) > 1 || !isnumberq(t)) {
        printf("Pure C sincos_u1 cos arg=%.20g ulp=%.20g\n", d, u3);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_tan(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xtan(d), frx);

      if ((fabs(d) < 1e+7 && u0 > 3.5) || (fabs(d) <= rangemax && u0 > 5) || isnan(t)) {
        printf("Pure C tan arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP(t = xtan_u1(d), frx);

      if ((fabs(d) <= rangemax && u1 > 1) || isnan(t)) {
        printf("Pure C tan_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    d = rnd_fr();
    double d2 = rnd_fr(), zo = rnd_zo();

    {
      mpfr_set_d(frx, fabs(d), GMP_RNDN);
      mpfr_log(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xlog(fabs(d)), frx);

      if (u0 > 3.5) {
        printf("Pure C log arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP(t = xlog_u1(fabs(d)), frx);

      if (u1 > 1) {
        printf("Pure C log_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, fabs(d), GMP_RNDN);
      mpfr_log10(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xlog10(fabs(d)), frx);

      if (u0 > 1) {
        printf("Pure C log10 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_log1p(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xlog1p(d), frx);

      if ((-1 <= d && d <= 1e+307 && u0 > 1) ||
          (d < -1 && !isnan(t)) ||
          (d > 1e+307 && !(u0 <= 1 || isinf(t)))) {
        printf("Pure C log1p arg=%.20g ulp=%.20g\n", d, u0);
        printf("%g\n", t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xexp(d), frx);

      if (u0 > 1) {
        printf("Pure C exp arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp2(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xexp2(d), frx);

      if (u0 > 1) {
        printf("Pure C exp2 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp10(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xexp10(d), frx);

      if (u0 > 1) {
        printf("Pure C exp10 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_expm1(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xexpm1(d), frx);

      if (u0 > 1) {
        printf("Pure C expm1 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_pow(frx, fry, frx, GMP_RNDN);

      double u0 = countULP(t = xpow(d2, d), frx);

      if (u0 > 1) {
        printf("Pure C pow arg=%.20g, %.20g ulp=%.20g\n", d2, d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cbrt(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xcbrt(d), frx);

      if (u0 > 3.5) {
        printf("Pure C cbrt arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP(t = xcbrt_u1(d), frx);

      if (u1 > 1) {
        printf("Pure C cbrt_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, zo, GMP_RNDN);
      mpfr_asin(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xasin(zo), frx);

      if (u0 > 3.5) {
        printf("Pure C asin arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP(t = xasin_u1(zo), frx);

      if (u1 > 1) {
        printf("Pure C asin_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, zo, GMP_RNDN);
      mpfr_acos(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xacos(zo), frx);

      if (u0 > 3.5) {
        printf("Pure C acos arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP(t = xacos_u1(zo), frx);

      if (u1 > 1) {
        printf("Pure C acos_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_atan(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xatan(d), frx);

      if (u0 > 3.5) {
        printf("Pure C atan arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP(t = xatan_u1(d), frx);

      if (u1 > 1) {
        printf("Pure C atan_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_atan2(frx, fry, frx, GMP_RNDN);

      double u0 = countULP(t = xatan2(d2, d), frx);

      if (u0 > 3.5) {
        printf("Pure C atan2 arg=%.20g, %.20g ulp=%.20g\n", d2, d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2(t = xatan2_u1(d2, d), frx);

      if (u1 > 1) {
        printf("Pure C atan2_u1 arg=%.20g, %.20g ulp=%.20g\n", d2, d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sinh(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xsinh(d), frx);

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

      double u0 = countULP(t = xcosh(d), frx);

      if ((fabs(d) <= 709 && u0 > 1) || !(u0 <= 1 || (isinf(t) && t > 0))) {
        printf("Pure C cosh arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_tanh(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xtanh(d), frx);

      if (u0 > 1) {
        printf("Pure C tanh arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_asinh(frx, frx, GMP_RNDN);

      double u0 = countULP(t = xasinh(d), frx);

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

      double u0 = countULP(t = xacosh(d), frx);

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

      double u0 = countULP(t = xatanh(d), frx);

      if (u0 > 1) {
        printf("Pure C atanh arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }
#endif
  }
}
