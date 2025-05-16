//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <quadmath.h>
#include <inttypes.h>

static __float128 mpfr_get_f128(mpfr_t m, mpfr_rnd_t rnd) {
  if (isnan(mpfr_get_d(m, GMP_RNDN))) return __builtin_nan("");

  mpfr_t frr, frd;
  mpfr_inits(frr, frd, NULL);

  mpfr_exp_t e;
  mpfr_frexp(&e, frr, m, GMP_RNDN);

  double d0 = mpfr_get_d(frr, GMP_RNDN);
  mpfr_set_d(frd, d0, GMP_RNDN);
  mpfr_sub(frr, frr, frd, GMP_RNDN);

  double d1 = mpfr_get_d(frr, GMP_RNDN);
  mpfr_set_d(frd, d1, GMP_RNDN);
  mpfr_sub(frr, frr, frd, GMP_RNDN);

  double d2 = mpfr_get_d(frr, GMP_RNDN);

  mpfr_clears(frr, frd, NULL);
  return ldexpq((__float128)d2 + (__float128)d1 + (__float128)d0, e);
}

static void mpfr_set_f128(mpfr_t frx, __float128 f, mpfr_rnd_t rnd) {
  char s[128];
  quadmath_snprintf(s, 120, "%.50Qg", f);
  mpfr_set_str(frx, s, 10, rnd);
}

static void printf128(__float128 f) {
  char s[128];
  quadmath_snprintf(s, 120, "%.50Qg", f);
  printf("%s", s);
}

static char frstr[16][1000];
static int frstrcnt = 0;

static char *toBC(double d) {
  union {
    double d;
    uint64_t u64;
    int64_t i64;
  } cnv;

  cnv.d = d;

  int64_t l = cnv.i64;
  int e = (int)((l >> 52) & ~(-1L << 11));
  int s = (int)(l >> 63);
  l = d == 0 ? 0 : ((l & ~((-1L) << 52)) | (1L << 52));

  char *ptr = frstr[(frstrcnt++) & 15];

  sprintf(ptr, "%s%lld*2^%d", s != 0 ? "-" : "", (long long int)l, (e-0x3ff-52));
  return ptr;
}

static char *toBCq(__float128 d) {
  union {
    __float128 d;
    __uint128_t u128;
  } cnv;

  cnv.d = d;

  __uint128_t m = cnv.u128;
  int e = (int)((m >> 112) & ~(-1L << 15));
  int s = (int)(m >> 127);
  m = d == 0 ? 0 : ((m & ((((__uint128_t)1) << 112)-1)) | ((__uint128_t)1 << 112));

  uint64_t h = m / UINT64_C(10000000000000000000);
  uint64_t l = m % UINT64_C(10000000000000000000);

  char *ptr = frstr[(frstrcnt++) & 15];

  sprintf(ptr, "%s%" PRIu64 "%019" PRIu64 "*2^%d", s != 0 ? "-" : "", h, l, (e-0x3fff-112));

  return ptr;
}

static int xisnanq(Sleef_quad x) { return x != x; }
static int xisinfq(Sleef_quad x) { return x == (Sleef_quad)__builtin_inf() || x == -(Sleef_quad)__builtin_inf(); }
static int xisfiniteq(Sleef_quad x) { return !xisnanq(x) && !isinfq(x); }
