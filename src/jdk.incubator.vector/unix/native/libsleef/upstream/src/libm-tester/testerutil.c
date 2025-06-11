//   Copyright Naoki Shibata and contributors 2010 - 2023.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <time.h>
#include <float.h>
#include <limits.h>

#include <math.h>

#if defined(__MINGW32__) || defined(__MINGW64__) || defined(_MSC_VER)
#define STDIN_FILENO 0
#else
#include <unistd.h>
#include <sys/types.h>
#endif

#if defined(__MINGW32__) || defined(__MINGW64__)
#include <unistd.h>
#endif

#if defined(_MSC_VER)
#include <io.h>
#endif

#include "misc.h"

#define DENORMAL_DBL_MIN (4.9406564584124654418e-324)
#define POSITIVE_INFINITY INFINITY
#define NEGATIVE_INFINITY (-INFINITY)

#define DENORMAL_FLT_MIN (1.4012984643248170709e-45f)
#define POSITIVE_INFINITYf ((float)INFINITY)
#define NEGATIVE_INFINITYf (-(float)INFINITY)

int isnumber(double x) { return !isinf(x) && !isnan(x); }
int isPlusZero(double x) { return x == 0 && copysign(1, x) == 1; }
int isMinusZero(double x) { return x == 0 && copysign(1, x) == -1; }
double sign(double d) { return d < 0 ? -1 : 1; }
int xisnan(double x) { return x != x; }

int isnumberf(float x) { return !isinf(x) && !isnan(x); }
int isPlusZerof(float x) { return x == 0 && copysignf(1, x) == 1; }
int isMinusZerof(float x) { return x == 0 && copysignf(1, x) == -1; }
float signf(float d) { return d < 0 ? -1 : 1; }
int xisnanf(float x) { return x != x; }

int enableFlushToZero = 0;

double flushToZero(double y) {
  if (enableFlushToZero && fabs(y) < FLT_MIN) y = copysign(0.0, y);
  return y;
}

//

int readln(int fd, char *buf, int cnt) {
  int i, rcnt = 0;

  if (cnt < 1) return -1;

  while(cnt >= 2) {
    i = read(fd, buf, 1);
    if (i != 1) return i;

    if (*buf == '\n') break;

    rcnt++;
    buf++;
    cnt--;
  }

  *++buf = '\0';
  rcnt++;
  return rcnt;
}

static uint64_t xseed;

uint64_t xrand() {
  xseed = xseed * UINT64_C(6364136223846793005) + 1;
  return xseed;
}

// Fill memory with random bits
void memrand(void *p, int size) {
  uint64_t *q = (uint64_t *)p;
  int i;
  for(i=0;i<size/8;i++) *q++ = xrand();
  uint8_t *r = (uint8_t *)q;
  for(i *= 8;i<size;i++) *r++ = xrand() & 0xff;
}

void xsrand(uint64_t s) { xseed = s; }

//

#ifdef USEMPFR
#include <mpfr.h>

int cmpDenormsp(float x, mpfr_t fry) {
  float y = mpfr_get_d(fry, GMP_RNDN);
  x = flushToZero(x);
  y = flushToZero(y);
  if (xisnanf(x) && xisnanf(y)) return 1;
  if (xisnanf(x) || xisnanf(y)) return 0;
  if (isinf(x) != isinf(y)) return 0;
  if (x == POSITIVE_INFINITYf && y == POSITIVE_INFINITYf) return 1;
  if (x == NEGATIVE_INFINITYf && y == NEGATIVE_INFINITYf) return 1;
  if (y == 0) {
    if (isPlusZerof(x) && isPlusZerof(y)) return 1;
    if (isMinusZerof(x) && isMinusZerof(y)) return 1;
    return 0;
  }
  if (!xisnanf(x) && !xisnanf(y) && !isinf(x) && !isinf(y)) return signf(x) == signf(y);
  return 0;
}

int cmpDenormdp(double x, mpfr_t fry) {
  double y = mpfr_get_d(fry, GMP_RNDN);
  if (xisnan(x) && xisnan(y)) return 1;
  if (xisnan(x) || xisnan(y)) return 0;
  if (isinf(x) != isinf(y)) return 0;
  if (x == POSITIVE_INFINITY && y == POSITIVE_INFINITY) return 1;
  if (x == NEGATIVE_INFINITY && y == NEGATIVE_INFINITY) return 1;
  if (y == 0) {
    if (isPlusZero(x) && isPlusZero(y)) return 1;
    if (isMinusZero(x) && isMinusZero(y)) return 1;
    return 0;
  }
  if (!xisnan(x) && !xisnan(y) && !isinf(x) && !isinf(y)) return sign(x) == sign(y);
  return 0;
}

double countULPdp(double d, mpfr_t c) {
  mpfr_t fra, frb, frc, frd;
  mpfr_inits(fra, frb, frc, frd, NULL);

  double c2 = mpfr_get_d(c, GMP_RNDN);
  if (c2 == 0 && d != 0) {
    mpfr_clears(fra, frb, frc, frd, NULL);
    return 10000;
  }
  if (isnan(c2) && isnan(d)) {
    mpfr_clears(fra, frb, frc, frd, NULL);
    return 0;
  }
  if (isnan(c2) || isnan(d)) {
    mpfr_clears(fra, frb, frc, frd, NULL);
    return 10001;
  }
  if (c2 == POSITIVE_INFINITY && d == POSITIVE_INFINITY) {
    mpfr_clears(fra, frb, frc, frd, NULL);
    return 0;
  }
  if (c2 == NEGATIVE_INFINITY && d == NEGATIVE_INFINITY) {
    mpfr_clears(fra, frb, frc, frd, NULL);
    return 0;
  }

  double v = 0;
  if (isinf(d) && !isinf(mpfr_get_d(c, GMP_RNDN))) {
    d = copysign(DBL_MAX, c2);
    v = 1;
  }

  //

  int e;
  frexp(mpfr_get_d(c, GMP_RNDN), &e);
  mpfr_set_ld(frb, fmaxl(ldexpl(1.0, e-53), DENORMAL_DBL_MIN), GMP_RNDN);

  mpfr_set_d(frd, d, GMP_RNDN);
  mpfr_sub(fra, frd, c, GMP_RNDN);
  mpfr_div(fra, fra, frb, GMP_RNDN);
  double u = fabs(mpfr_get_d(fra, GMP_RNDN));

  mpfr_clears(fra, frb, frc, frd, NULL);

  return u + v;
}

double countULP2dp(double d, mpfr_t c) {
  mpfr_t fra, frb, frc, frd;
  mpfr_inits(fra, frb, frc, frd, NULL);

  double c2 = mpfr_get_d(c, GMP_RNDN);
  if (c2 == 0 && d != 0) {
    mpfr_clears(fra, frb, frc, frd, NULL);
    return 10000;
  }
  if (isnan(c2) && isnan(d)) {
    mpfr_clears(fra, frb, frc, frd, NULL);
    return 0;
  }
  if (isnan(c2) || isnan(d)) {
    mpfr_clears(fra, frb, frc, frd, NULL);
    return 10001;
  }
  if (c2 == POSITIVE_INFINITY && d == POSITIVE_INFINITY) {
    mpfr_clears(fra, frb, frc, frd, NULL);
    return 0;
  }
  if (c2 == NEGATIVE_INFINITY && d == NEGATIVE_INFINITY) {
    mpfr_clears(fra, frb, frc, frd, NULL);
    return 0;
  }

  double v = 0;
  if (isinf(d) && !isinf(mpfr_get_d(c, GMP_RNDN))) {
    d = copysign(DBL_MAX, c2);
    v = 1;
  }

  //

  int e;
  frexp(mpfr_get_d(c, GMP_RNDN), &e);
  mpfr_set_ld(frb, fmaxl(ldexpl(1.0, e-53), DBL_MIN), GMP_RNDN);

  mpfr_set_d(frd, d, GMP_RNDN);
  mpfr_sub(fra, frd, c, GMP_RNDN);
  mpfr_div(fra, fra, frb, GMP_RNDN);
  double u = fabs(mpfr_get_d(fra, GMP_RNDN));

  mpfr_clears(fra, frb, frc, frd, NULL);

  return u + v;
}

double countULPsp(float d, mpfr_t c0) {
  double c = mpfr_get_d(c0, GMP_RNDN);

  d = flushToZero(d);
  float c2 = flushToZero(c);
  if (c2 == 0 && d != 0) return 10000;
  if (isnan(c2) && isnan(d)) return 0;
  if (isnan(c2) || isnan(d)) return 10001;
  if (c2 == POSITIVE_INFINITYf && d == POSITIVE_INFINITYf) return 0;
  if (c2 == NEGATIVE_INFINITYf && d == NEGATIVE_INFINITYf) return 0;

  double v = 0;
  if (isinf(d) && !isinf(c)) {
    d = copysign(FLT_MAX, c2);
    v = 1;
  }

  //

  int e;
  frexp(c, &e);

  double u = fabs(d - c) * fmin(ldexp(1.0, 24-e), 1.0 / DENORMAL_FLT_MIN);

  return u + v;
}

double countULP2sp(float d, mpfr_t c0) {
  double c = mpfr_get_d(c0, GMP_RNDN);

  d = flushToZero(d);
  float c2 = flushToZero(c);
  if (c2 == 0 && d != 0) return 10000;
  if (isnan(c2) && isnan(d)) return 0;
  if (isnan(c2) || isnan(d)) return 10001;
  if (c2 == POSITIVE_INFINITYf && d == POSITIVE_INFINITYf) return 0;
  if (c2 == NEGATIVE_INFINITYf && d == NEGATIVE_INFINITYf) return 0;

  double v = 0;
  if (isinf(d) && !isinf(c)) {
    d = copysign(FLT_MAX, c2);
    v = 1;
  }

  //

  int e;
  frexp(c, &e);

  double u = fabs(d - c) * fmin(ldexp(1.0, 24-e), 1.0 / FLT_MIN);

  return u + v;
}

//

#if MPFR_VERSION < MPFR_VERSION_NUM(4, 2, 0)
void mpfr_sinpi(mpfr_t ret, mpfr_t arg, mpfr_rnd_t rnd) {
  mpfr_t frpi, frd;
  mpfr_inits(frpi, frd, NULL);

  mpfr_const_pi(frpi, GMP_RNDN);
  mpfr_set_d(frd, 1.0, GMP_RNDN);
  mpfr_mul(frpi, frpi, frd, GMP_RNDN);
  mpfr_mul(frd, frpi, arg, GMP_RNDN);
  mpfr_sin(ret, frd, GMP_RNDN);

  mpfr_clears(frpi, frd, NULL);
}

void mpfr_cospi(mpfr_t ret, mpfr_t arg, mpfr_rnd_t rnd) {
  mpfr_t frpi, frd;
  mpfr_inits(frpi, frd, NULL);

  mpfr_const_pi(frpi, GMP_RNDN);
  mpfr_set_d(frd, 1.0, GMP_RNDN);
  mpfr_mul(frpi, frpi, frd, GMP_RNDN);
  mpfr_mul(frd, frpi, arg, GMP_RNDN);
  mpfr_cos(ret, frd, GMP_RNDN);

  mpfr_clears(frpi, frd, NULL);
}
#endif

void mpfr_lgamma_nosign(mpfr_t ret, mpfr_t arg, mpfr_rnd_t rnd) {
  int s;
  mpfr_lgamma(ret, &s, arg, rnd);
}
#endif // #define USEMPFR
