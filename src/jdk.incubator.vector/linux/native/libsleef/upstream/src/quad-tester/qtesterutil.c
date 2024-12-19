//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#if defined(__MINGW32__) || defined(__MINGW64__)
#define __USE_MINGW_ANSI_STDIO
#endif

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <time.h>
#include <float.h>
#include <limits.h>
#include <assert.h>

#include <math.h>

#ifdef USEMPFR
#include <mpfr.h>
#endif

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
#include "qtesterutil.h"

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

int startsWith(char *str, char *prefix) {
  return strncmp(str, prefix, strlen(prefix)) == 0;
}

//

xuint128 xu(uint64_t h, uint64_t l) {
  xuint128 r = { .l = l, .h = h };
  return r;
}

xuint128 sll128(uint64_t u, int c) {
  if (c < 64) {
    xuint128 r = { .l = u << c, .h = u >> (64 - c) };
    return r;
  }

  xuint128 r = { .l = 0, .h = u << (c - 64) };
  return r;
}

xuint128 add128(xuint128 x, xuint128 y) {
  xuint128 r = { .l = x.l + y.l, .h = x.h + y.h };
  if (r.l < x.l) r.h++;
  return r;
}

static xuint128 cmpcnv(xuint128 cx) {
  if ((cx.h & 0x8000000000000000ULL) != 0) {
    cx.h ^= 0x7fffffffffffffffULL;
    cx.l = ~cx.l;
    cx.l++;
    if (cx.l == 0) cx.h++;
  }

  cx.h ^= 0x8000000000000000ULL;

  return cx;
}

int lt128(xuint128 x, xuint128 y) {
  xuint128 cx = cmpcnv(x), cy = cmpcnv(y);
  if (cx.h < cy.h) return 1;
  if (cx.h == cy.h && cx.l < cy.l) return 1;
  return 0;
}

//

typedef union {
  Sleef_quad q;
  xuint128 x;
  struct {
#if defined(__BYTE_ORDER__) && (__BYTE_ORDER__ == __ORDER_BIG_ENDIAN__)
    uint64_t h, l;
#else
    uint64_t l, h;
#endif
  };
} cnv_t;

int iszerof128(Sleef_quad a) {
  cnv_t c128 = { .q = a };
  return (((c128.h & UINT64_C(0x7fffffffffffffff)) == 0) && c128.l == 0);
}

int isnegf128(Sleef_quad a) {
  cnv_t c128 = { .q = a };
  return c128.h >> 63;
}

int isinff128(Sleef_quad a) {
  cnv_t c128 = { .q = a };
  return (((c128.h & UINT64_C(0x7fffffffffffffff)) == UINT64_C(0x7fff000000000000)) && c128.l == 0);
}

int isnonnumberf128(Sleef_quad a) {
  cnv_t c128 = { .q = a };
  return (c128.h & UINT64_C(0x7fff000000000000)) == UINT64_C(0x7fff000000000000);
}

int isnanf128(Sleef_quad a) {
  return isnonnumberf128(a) && !isinff128(a);
}

//

static uint64_t xseed;

uint64_t xrand() {
  uint64_t u = xseed;
  xseed = xseed * UINT64_C(6364136223846793005) + 1;
  u = (u & ((~UINT64_C(0)) << 32)) | (xseed >> 32);
  xseed = xseed * UINT64_C(6364136223846793005) + 1;
  return u;
}

void xsrand(uint64_t s) {
  xseed = s;
  xrand();
  xrand();
  xrand();
}

void memrand(void *p, int size) {
  uint64_t *q = (uint64_t *)p;
  int i;
  for(i=0;i<size;i+=8) *q++ = xrand();
  uint8_t *r = (uint8_t *)q;
  for(;i<size;i++) *r++ = xrand() & 0xff;
}

Sleef_quad rndf128(Sleef_quad min, Sleef_quad max, int setSignRandomly) {
  cnv_t cmin = { .q = min }, cmax = { .q = max }, c;
  do {
    memrand(&c.q, sizeof(Sleef_quad));
  } while(isnonnumberf128(c.q) || lt128(c.x, cmin.x) || lt128(cmax.x, c.x));

  if (setSignRandomly && (xrand() & 1)) c.h ^= UINT64_C(0x8000000000000000);

  return c.q;
}

Sleef_quad rndf128x() {
  Sleef_quad r;
  memrand(&r, sizeof(Sleef_quad));
  return r;
}

typedef struct {
  double x, y, z;
} double3;

typedef struct {
  int32_t e;
  double3 dd;
} TDX_t;

#ifdef USEMPFR
double countULPf128(Sleef_quad d, mpfr_t c, int checkNegZero) {
  static mpfr_t fr_denorm_min, fr_denorm_mino2, fr_f128_max;
  static int is_first = 1;
  if (is_first) {
    is_first = 0;
    mpfr_inits(fr_denorm_min, fr_denorm_mino2, fr_f128_max, NULL);
    mpfr_set_str(fr_denorm_min, "6.475175119438025110924438958227646552e-4966", 10, GMP_RNDN);
    mpfr_mul_d(fr_denorm_mino2, fr_denorm_min, 0.5, GMP_RNDN);
    mpfr_set_str(fr_f128_max  , "1.18973149535723176508575932662800702e+4932", 10, GMP_RNDN);
  }

  mpfr_t fra, frb, frc, frd;
  mpfr_inits(fra, frb, frc, frd, NULL);
  double ret = 0;

  mpfr_abs(fra, c, GMP_RNDN);

  int csign = mpfr_signbit(c), dsign = isnegf128(d);
  int ciszero = mpfr_cmp(fra, fr_denorm_mino2) < 0, diszero = iszerof128(d);
  int cisnan = mpfr_nan_p(c), disnan = isnanf128(d);
  int cisinf = mpfr_cmp(fra, fr_f128_max) > 0, disinf = isinff128(d);

  if (ciszero && !diszero) {
    ret = 10000;
  } else if (ciszero && diszero) {
    ret = 0;
    if (checkNegZero && csign != dsign) ret = 10003;
  } else if (cisnan && disnan) {
    ret = 0;
  } else if (cisnan || disnan) {
    ret = 10001;
  } else if (cisinf && disinf) {
    ret = csign == dsign ? 0 : 10002;
  } else {
    mpfr_set_f128(frd, d, GMP_RNDN);
    int e = mpfr_get_exp(frd);
    mpfr_set_d(frb, 1, GMP_RNDN);
    assert(!mpfr_zero_p(frb));
    mpfr_set_exp(frb, e-113+1);
    mpfr_max(frb, frb, fr_denorm_min, GMP_RNDN);
    mpfr_sub(fra, frd, c, GMP_RNDN);
    mpfr_div(fra, fra, frb, GMP_RNDN);
    ret = fabs(mpfr_get_d(fra, GMP_RNDN));
  }

  mpfr_clears(fra, frb, frc, frd, NULL);
  return ret;
}

//

char *sprintfr(mpfr_t fr) {
  int digits = 51;
  mpfr_t t;
  mpfr_inits(t, NULL);
  int sign = mpfr_signbit(fr) ? -1 : 1;
  char *s = malloc(digits + 10);
  if (mpfr_inf_p(fr)) {
    sprintf(s, "%cinf", sign < 0 ? '-' : '+');
  } else if (mpfr_nan_p(fr)) {
    sprintf(s, "nan");
  } else {
    mpfr_exp_t e;
    s[0] = sign < 0 ? '-' : '+';
    s[1] = '0';
    s[2] = '.';
    mpfr_abs(t, fr, GMP_RNDN);
    mpfr_get_str(s+3, &e, 10, digits, t, GMP_RNDN);
    int ie = e;
    char es[32];
    snprintf(es, 30, "e%c%d", ie >= 0 ? '+' : '-', ie >= 0 ? ie : -ie);
    strncat(s, es, digits+10);
  }

  mpfr_clears(t, NULL);
  return s;
}

//

#if MPFR_VERSION_MAJOR >= 4 && defined(SLEEF_FLOAT128_IS_IEEEQP) && !defined(__PPC64__) && !defined(__i386__) && !(defined(__APPLE__) && defined(__MACH__))
void mpfr_set_f128(mpfr_t frx, Sleef_quad q, mpfr_rnd_t rnd) {
  int mpfr_set_float128(mpfr_t rop, __float128 op, mpfr_rnd_t rnd);
  union {
    Sleef_quad q;
    __float128 f;
  } c;
  c.q = q;
  mpfr_set_float128(frx, c.f, rnd);
}

Sleef_quad mpfr_get_f128(mpfr_t m, mpfr_rnd_t rnd) {
  __float128 mpfr_get_float128(mpfr_t op, mpfr_rnd_t rnd);
  union {
    Sleef_quad q;
    __float128 f;
  } c;
  c.f = mpfr_get_float128(m, rnd);
  return c.q;
}
#elif defined(SLEEF_LONGDOUBLE_IS_IEEEQP)
void mpfr_set_f128(mpfr_t frx, Sleef_quad q, mpfr_rnd_t rnd) {
  union {
    Sleef_quad q;
    long double f;
  } c;
  c.q = q;
  mpfr_set_ld(frx, c.f, rnd);
}

Sleef_quad mpfr_get_f128(mpfr_t m, mpfr_rnd_t rnd) {
  union {
    Sleef_quad q;
    long double f;
  } c;
  c.f = mpfr_get_ld(m, rnd);
  return c.q;
}
#else
#pragma message ( "Internal MPFR<->float128 conversion is used" )
void mpfr_set_f128(mpfr_t frx, Sleef_quad a, mpfr_rnd_t rnd) {
  union {
    Sleef_quad u;
    struct {
#if defined(__BYTE_ORDER__) && (__BYTE_ORDER__ == __ORDER_BIG_ENDIAN__)
      uint64_t h, l;
#else
      uint64_t l, h;
#endif
    };
  } c128 = { .u = a };

  int sign = (int)(c128.h >> 63);
  int exp = ((int)(c128.h >> 48)) & 0x7fff;

  if (isnanf128(a)) {
    mpfr_set_nan(frx);
  } else if (isinff128(a)) {
    mpfr_set_inf(frx, sign ? -1 : 1);
  } else if (exp == 0) {
    c128.h &= UINT64_C(0xffffffffffff);
    mpfr_set_d(frx, ldexp((double)c128.h, 64), GMP_RNDN);
    mpfr_add_d(frx, frx, (double)(c128.l & UINT64_C(0xffffffff00000000)), GMP_RNDN);
    mpfr_add_d(frx, frx, (double)(c128.l & UINT64_C(0xffffffff)), GMP_RNDN);
    mpfr_set_exp(frx, mpfr_get_exp(frx) - 16382 - 112);
    mpfr_setsign(frx, frx, sign, GMP_RNDN);
  } else {
    c128.h &= UINT64_C(0xffffffffffff);
    mpfr_set_d(frx, ldexp(1, 112), GMP_RNDN);
    mpfr_add_d(frx, frx, ldexp((double)c128.h, 64), GMP_RNDN);
    mpfr_add_d(frx, frx, (double)(c128.l & UINT64_C(0xffffffff00000000)), GMP_RNDN);
    mpfr_add_d(frx, frx, (double)(c128.l & UINT64_C(0xffffffff)), GMP_RNDN);
    mpfr_set_exp(frx, exp - 16382);
    mpfr_setsign(frx, frx, sign, GMP_RNDN);
  }
}

static double3 mpfr_get_d3(mpfr_t fr, mpfr_rnd_t rnd) {
  double3 ret;
  mpfr_t t;
  mpfr_inits(t, NULL);
  ret.x = mpfr_get_d(fr, GMP_RNDN);
  mpfr_sub_d(t, fr, ret.x, GMP_RNDN);
  ret.y = mpfr_get_d(t, GMP_RNDN);
  mpfr_sub_d(t, t, ret.y, GMP_RNDN);
  ret.z = mpfr_get_d(t, GMP_RNDN);
  mpfr_clears(t, NULL);
  return ret;
}

static TDX_t mpfr_get_tdx(mpfr_t fr, mpfr_rnd_t rnd) {
  TDX_t td;

  if (mpfr_nan_p(fr)) {
    td.dd.x = NAN;
    td.dd.y = 0;
    td.dd.z = 0;
    td.e = 0;
    return td;
  }

  if (mpfr_inf_p(fr)) {
    td.dd.x = copysign(INFINITY, mpfr_cmp_d(fr, 0));
    td.dd.y = 0;
    td.dd.z = 0;
    td.e = 0;
    return td;
  }

  if (mpfr_zero_p(fr)) {
    td.dd.x = copysign(0, mpfr_signbit(fr) ? -1 : 1);
    td.dd.y = 0;
    td.dd.z = 0;
    td.e = 0;
    return td;
  }

  mpfr_t t;
  mpfr_inits(t, NULL);

  mpfr_set(t, fr, GMP_RNDN);
  td.e = mpfr_get_exp(fr) + 16382;
  assert(!mpfr_zero_p(t));
  mpfr_set_exp(t, 1);
  mpfr_setsign(t, t, mpfr_signbit(fr), GMP_RNDN);
  td.dd = mpfr_get_d3(t, GMP_RNDN);

  if (fabs(td.dd.x) == 2.0) {
    td.dd.x *= 0.5;
    td.dd.y *= 0.5;
    td.dd.z *= 0.5;
    td.e++;
  }

  mpfr_clears(t, NULL);

  return td;
}

#define HBX 1.0
#define LOGXSCALE 1
#define XSCALE (1 << LOGXSCALE)
#define SX 61
#define HBY (1.0 / (UINT64_C(1) << 53))
#define LOGYSCALE 4
#define YSCALE (1 << LOGYSCALE)
#define SY 11
#define HBZ (1.0 / ((UINT64_C(1) << 53) * (double)(UINT64_C(1) << 53)))
#define LOGZSCALE 10
#define ZSCALE (1 << LOGZSCALE)
#define SZ 36
#define HBR (1.0 / (UINT64_C(1) << 60))

static int64_t doubleToRawLongBits(double d) {
  union {
    double f;
    int64_t i;
  } tmp;
  tmp.f = d;
  return tmp.i;
}

static double longBitsToDouble(int64_t i) {
  union {
    double f;
    int64_t i;
  } tmp;
  tmp.i = i;
  return tmp.f;
}

static int xisnonnumber(double x) {
  return (doubleToRawLongBits(x) & UINT64_C(0x7ff0000000000000)) == UINT64_C(0x7ff0000000000000);
}

static double xordu(double x, uint64_t y) {
  union {
    double d;
    uint64_t u;
  } cx;
  cx.d = x;
  cx.u ^= y;
  return cx.d;
}

static double pow2i(int q) {
  return longBitsToDouble(((int64_t)(q + 0x3ff)) << 52);
}

static double ldexp2k(double d, int e) { // faster than ldexpk, short reach
  return d * pow2i(e >> 1) * pow2i(e - (e >> 1));
}

Sleef_quad mpfr_get_f128(mpfr_t a, mpfr_rnd_t rnd) {
  TDX_t f = mpfr_get_tdx(a, rnd);

  cnv_t c128;

  union {
    double d;
    uint64_t u;
  } c64;

  c64.d = f.dd.x;
  uint64_t signbit = c64.u & UINT64_C(0x8000000000000000);
  int isZero = (f.dd.x == 0.0), denorm = 0;

  f.dd.x = xordu(f.dd.x, signbit);
  f.dd.y = xordu(f.dd.y, signbit);
  f.dd.z = xordu(f.dd.z, signbit);

  double t = 1;

  if (f.e <= 0) {
    t = ldexp2k(0.5, f.e);
    if (f.e < -120) t = 0;
    f.e = 1;
    denorm = 1;
  }

  if ((fabs(f.dd.x) == 1.0 && f.dd.y <= -pow(2, -114)) && f.e != 1) {
    t = 2;
    f.e--;
  }

  f.dd.x *= t;
  f.dd.y *= t;
  f.dd.z *= t;

  c64.d = f.dd.y + HBY * YSCALE;
  c64.u &= UINT64_C(0xffffffffffffffff) << LOGYSCALE;
  f.dd.z += f.dd.y - (c64.d - (HBZ * ZSCALE + HBY * YSCALE));
  f.dd.y = c64.d;

  double c = denorm ? (HBX * XSCALE + HBX) : (HBX * XSCALE);
  c64.d = f.dd.x + c;
  c64.u &= UINT64_C(0xffffffffffffffff) << LOGXSCALE;
  t = f.dd.y + (f.dd.x - (c64.d - c));
  f.dd.z += f.dd.y - t + (f.dd.x - (c64.d - c));
  f.dd.x = c64.d;

  c64.d = t;
  c64.u &= UINT64_C(0xffffffffffffffff) << LOGYSCALE;
  f.dd.z += t - c64.d;
  f.dd.y = c64.d;

  t = f.dd.z - HBZ * ZSCALE < 0 ? HBZ * (ZSCALE/2) : 0;
  f.dd.y -= t;
  f.dd.z += t;

  t = f.dd.y - HBY * YSCALE < 0 ? HBY * (YSCALE/2) : 0;
  f.dd.x -= t;
  f.dd.y += t;

  f.dd.z = f.dd.z + HBR - HBR;

  //

  c64.d = f.dd.x;
  c64.u &= UINT64_C(0xfffffffffffff);
  c128.x = sll128(c64.u, SX);

  c64.d = f.dd.z;
  c64.u &= UINT64_C(0xfffffffffffff);
  c128.l |= c64.u >> SZ;

  c64.d = f.dd.y;
  c64.u &= UINT64_C(0xfffffffffffff);
  c128.x = add128(c128.x, sll128(c64.u, SY));

  c128.h &= denorm ? UINT64_C(0xffffffffffff) : UINT64_C(0x3ffffffffffff);
  c128.h += ((f.e-1) & ~((uint64_t)-1UL << 15)) << 48;

  if (isZero) { c128.l = c128.h = 0; }
  if (f.e >= 32767 || f.dd.x == INFINITY) {
    c128.h = UINT64_C(0x7fff000000000000);
    c128.l = 0;
  }
  if (xisnonnumber(f.dd.x) && f.dd.x != INFINITY) c128.h = c128.l = UINT64_C(0xffffffffffffffff);

  c128.h |= signbit;

  return c128.q;
}
#endif // #if MPFR_VERSION_MAJOR >= 4

char *sprintf128(Sleef_quad q) {
  mpfr_t fr;
  mpfr_inits(fr, NULL);
  mpfr_set_f128(fr, q, GMP_RNDN);
  char *f = sprintfr(fr);
  mpfr_clears(fr, NULL);
  cnv_t c128 = { .q = q };
  char *ret = malloc(128);
  sprintf(ret, "%016llx%016llx (%s)", (unsigned long long)c128.h, (unsigned long long)c128.l, f);
  free(f);
  return ret;
}

double cast_d_q(Sleef_quad q) {
  mpfr_t fr;
  mpfr_inits(fr, NULL);
  mpfr_set_f128(fr, q, GMP_RNDN);
  double ret = mpfr_get_d(fr, GMP_RNDN);
  mpfr_clears(fr, NULL);
  return ret;
}

Sleef_quad add_q_d(Sleef_quad q, double d) {
  mpfr_t fr;
  mpfr_inits(fr, NULL);
  mpfr_set_f128(fr, q, GMP_RNDN);
  mpfr_add_d(fr, fr, d, GMP_RNDN);
  q = mpfr_get_f128(fr, GMP_RNDN);
  mpfr_clears(fr, NULL);
  return q;
}

Sleef_quad cast_q_str(const char *s) {
  mpfr_t fr;
  mpfr_inits(fr, NULL);
  mpfr_set_str(fr, s, 10, GMP_RNDN);
  Sleef_quad q = mpfr_get_f128(fr, GMP_RNDN);
  mpfr_clears(fr, NULL);
  return q;
}

Sleef_quad cast_q_str_hex(const char *s) {
  mpfr_t fr;
  mpfr_inits(fr, NULL);
  mpfr_set_str(fr, s, 16, GMP_RNDN);
  Sleef_quad q = mpfr_get_f128(fr, GMP_RNDN);
  mpfr_clears(fr, NULL);
  return q;
}

Sleef_quad add_q_q(Sleef_quad q, Sleef_quad r) {
  mpfr_t fr0, fr1;
  mpfr_inits(fr0, fr1, NULL);
  mpfr_set_f128(fr0, q, GMP_RNDN);
  mpfr_set_f128(fr1, r, GMP_RNDN);
  mpfr_add(fr0, fr0, fr1, GMP_RNDN);
  q = mpfr_get_f128(fr0, GMP_RNDN);
  mpfr_clears(fr0, fr1, NULL);
  return q;
}
#else // #ifdef USEMPFR
char *sprintf128(Sleef_quad x) {
  cnv_t c128 = { .q = x };
  char *s = malloc(128);
  sprintf(s, "%016llx%016llx", (unsigned long long)c128.h, (unsigned long long)c128.l);
  return s;
}
#endif // #ifdef USEMPFR
