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

#include <math.h>

#include "misc.h"

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

#define DENORMAL_LDBL_MIN (3.6451995318824746025284059336194e-4951L)
#define XLDBL_MIN (3.3621031431120935062626778173218e-4932L)

#ifndef M_PIl
#define M_PIl 3.141592653589793238462643383279502884L
#endif

#ifndef M_PI_4l
#define M_PI_4l .785398163397448309615660845819875721049292L
#endif

#define POSITIVE_INFINITY INFINITY
#define NEGATIVE_INFINITY (-INFINITY)

int isnumberl(long double x) { return x != SLEEF_INFINITYl && x != -SLEEF_INFINITYl && x == x; }
int isPlusZerol(long double x) { return x == 0 && copysignl(1, x) == 1; }
int isMinusZerol(long double x) { return x == 0 && copysignl(1, x) == -1; }

mpfr_t fra, frb, frd;

double countULP(long double d, mpfr_t c) {
  long double c2 = mpfr_get_ld(c, GMP_RNDN);
  if (c2 == 0 && d != 0) return 10000;
  //if (isPlusZerol(c2) && !isPlusZerol(d)) return 10003;
  //if (isMinusZerol(c2) && !isMinusZerol(d)) return 10004;
  if (isnanl(c2) && isnanl(d)) return 0;
  if (isnanl(c2) || isnanl(d)) return 10001;
  if (c2 == POSITIVE_INFINITY && d == POSITIVE_INFINITY) return 0;
  if (c2 == NEGATIVE_INFINITY && d == NEGATIVE_INFINITY) return 0;
  if (!isnumberl(c2) && !isnumberl(d)) return 0;

  int e;
  frexpl(mpfr_get_ld(c, GMP_RNDN), &e);
  mpfr_set_ld(frb, fmaxl(ldexpl(1.0, e-64), DENORMAL_LDBL_MIN), GMP_RNDN);

  mpfr_set_ld(frd, d, GMP_RNDN);
  mpfr_sub(fra, frd, c, GMP_RNDN);
  mpfr_div(fra, fra, frb, GMP_RNDN);
  double u = fabs(mpfr_get_d(fra, GMP_RNDN));

  return u;
}

double countULP2(long double d, mpfr_t c) {
  long double c2 = mpfr_get_ld(c, GMP_RNDN);
  if (c2 == 0 && d != 0) return 10000;
  //if (isPlusZerol(c2) && !isPlusZerol(d)) return 10003;
  //if (isMinusZerol(c2) && !isMinusZerol(d)) return 10004;
  if (isnanl(c2) && isnanl(d)) return 0;
  if (isnanl(c2) || isnanl(d)) return 10001;
  if (c2 == POSITIVE_INFINITY && d == POSITIVE_INFINITY) return 0;
  if (c2 == NEGATIVE_INFINITY && d == NEGATIVE_INFINITY) return 0;
  if (!isnumberl(c2) && !isnumberl(d)) return 0;

  int e;
  frexpl(mpfr_get_ld(c, GMP_RNDN), &e);
  mpfr_set_ld(frb, fmaxl(ldexpl(1.0, e-64), LDBL_MIN), GMP_RNDN);

  mpfr_set_ld(frd, d, GMP_RNDN);
  mpfr_sub(fra, frd, c, GMP_RNDN);
  mpfr_div(fra, fra, frb, GMP_RNDN);
  double u = fabs(mpfr_get_d(fra, GMP_RNDN));

  return u;
}

typedef union {
  long double d;
  __int128 u128;
} conv_t;

long double rnd() {
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

long double rnd_fr() {
  conv_t c;
  do {
#ifdef ENABLE_SYS_getrandom
    syscall(SYS_getrandom, &c.u128, sizeof(c.u128), 0);
#else
    c.u128 = random() | ((__int128)random() << 31) | ((__int128)random() << (31*2)) | ((__int128)random() << (31*3)) | ((__int128)random() << (31*4));
#endif
  } while(!isnumberl(c.d));
  return c.d;
}

long double rnd_zo() {
  conv_t c;
  do {
#ifdef ENABLE_SYS_getrandom
    syscall(SYS_getrandom, &c.u128, sizeof(c.u128), 0);
#else
    c.u128 = random() | ((__int128)random() << 31) | ((__int128)random() << (31*2)) | ((__int128)random() << (31*3)) | ((__int128)random() << (31*4));
#endif
  } while(!isnumberl(c.d) || c.d < -1 || 1 < c.d);
  return c.d;
}

void sinpifr(mpfr_t ret, long double d) {
  mpfr_t frpi, frd;
  mpfr_inits(frpi, frd, NULL);

  mpfr_const_pi(frpi, GMP_RNDN);
  mpfr_set_d(frd, 1.0, GMP_RNDN);
  mpfr_mul(frpi, frpi, frd, GMP_RNDN);
  mpfr_set_ld(frd, d, GMP_RNDN);
  mpfr_mul(frd, frpi, frd, GMP_RNDN);
  mpfr_sin(ret, frd, GMP_RNDN);

  mpfr_clears(frpi, frd, NULL);
}

void cospifr(mpfr_t ret, long double d) {
  mpfr_t frpi, frd;
  mpfr_inits(frpi, frd, NULL);

  mpfr_const_pi(frpi, GMP_RNDN);
  mpfr_set_d(frd, 1.0, GMP_RNDN);
  mpfr_mul(frpi, frpi, frd, GMP_RNDN);
  mpfr_set_ld(frd, d, GMP_RNDN);
  mpfr_mul(frd, frpi, frd, GMP_RNDN);
  mpfr_cos(ret, frd, GMP_RNDN);

  mpfr_clears(frpi, frd, NULL);
}

int main(int argc,char **argv)
{
  mpfr_t frx;

  mpfr_set_default_prec(256);
  mpfr_inits(fra, frb, frd, frx, NULL);

  conv_t cd;
  long double d, t;

  int cnt, ecnt = 0;

  srandom(time(NULL));

  for(cnt = 0;ecnt < 1000;cnt++) {
    switch(cnt & 7) {
    case 0:
      d = rnd();
      break;
    case 1:
      cd.d = rint((2 * (double)random() / RAND_MAX - 1) * 1e+10) * M_PI_4;
      cd.u128 += (random() & 0xff) - 0x7f;
      d = cd.d;
      break;
    default:
      d = rnd_fr();
      break;
    }

    Sleef_longdouble2 sc  = xsincospil_u05(d);
    Sleef_longdouble2 sc2 = xsincospil_u35(d);

    {
      const double rangemax2 = 1e+9;

      sinpifr(frx, d);

      double u0 = countULP2(t = sc.x, frx);

      if (u0 != 0 && ((fabsl(d) <= rangemax2 && u0 > 0.505) || fabsl(t) > 1 || !isnumberl(t))) {
        printf("Pure C sincospil_u05 sin arg=%.30Lg ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2(t = sc2.x, frx);

      if (u1 != 0 && ((fabsl(d) <= rangemax2 && u1 > 1.5) || fabsl(t) > 1 || !isnumberl(t))) {
        printf("Pure C sincospil_u35 sin arg=%.30Lg ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      const double rangemax2 = 1e+9;

      cospifr(frx, d);

      double u0 = countULP2(t = sc.y, frx);

      if (u0 != 0 && ((fabsl(d) <= rangemax2 && u0 > 0.505) || fabsl(t) > 1 || !isnumberl(t))) {
        printf("Pure C sincospil_u05 cos arg=%.30Lg ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2(t = sc.y, frx);

      if (u1 != 0 && ((fabsl(d) <= rangemax2 && u1 > 1.5) || fabsl(t) > 1 || !isnumberl(t))) {
        printf("Pure C sincospil_u35 cos arg=%.30Lg ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

  }
}
