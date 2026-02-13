//   Copyright Naoki Shibata and contributors 2010 - 2025.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <string.h>

#ifdef __cplusplus
#include <tlfloat/tlfloat.h>
using namespace tlfloat;
#endif

#if defined(__GNUC__) && !defined(__clang__)
#pragma GCC diagnostic ignored "-Wuninitialized"
#pragma GCC diagnostic ignored "-Wmaybe-uninitialized"
#pragma GCC diagnostic ignored "-Wattributes"
#endif

#if defined(__clang__)
#pragma clang diagnostic ignored "-Wvla-cxx-extension"
#pragma clang diagnostic ignored "-Wuninitialized"
#pragma clang diagnostic ignored "-Wtautological-compare"
#endif

#define DENORMAL_DBL_MIN (4.9406564584124654418e-324)
#define POSITIVE_INFINITY INFINITY
#define NEGATIVE_INFINITY (-INFINITY)

#define DENORMAL_FLT_MIN (1.4012984643248170709e-45f)
#define POSITIVE_INFINITYf ((float)INFINITY)
#define NEGATIVE_INFINITYf (-(float)INFINITY)

#ifndef M_PIf
# define M_PIf ((float)M_PI)
#endif

#ifdef __cplusplus
extern "C" {
#endif

int xisnumber(double x);
int isPlusZero(double x);
int isMinusZero(double x);
int xisnan(double x);
double sign(double d);

int isnumberf(float x);
int isPlusZerof(float x);
int isMinusZerof(float x);
int xisnanf(float x);
float signf(float d);

int readln(int fd, char *buf, int cnt);

#define XRAND_MAX (INT64_C(0x100000000) * (double)INT64_C(0x100000000))

void xsrand(uint64_t s);
uint64_t xrand();
void memrand(void *p, int size);

// The following functions are meant to be inlined

static double u2d(uint64_t u) {
  double d = 0;
  memcpy(&d, &u, sizeof(d));
  return d;
}

static uint64_t d2u(double d) {
  uint64_t u = 0;
  memcpy(&u, &d, sizeof(u));
  return u;
}

static float u2f(uint32_t u) {
  float f = 0;
  memcpy(&f, &u, sizeof(f));
  return f;
}

static uint32_t f2u(float d) {
  uint32_t u = 0;
  memcpy(&u, &d, sizeof(u));
  return u;
}

static int startsWith(char *str, char *prefix) {
  while(*prefix != '\0') if (*str++ != *prefix++) return 0;
  return *prefix == '\0';
}

//

#ifdef USEMPFR
int cmpDenormdp(double x, mpfr_t fry);
double countULPdp(double d, mpfr_t c);
double countULP2dp(double d, mpfr_t c);

int cmpDenormsp(float x, mpfr_t fry);
double countULPsp(float d, mpfr_t c);
double countULP2sp(float d, mpfr_t c);

#if MPFR_VERSION < MPFR_VERSION_NUM(4, 2, 0)
void mpfr_sinpi(mpfr_t ret, mpfr_t arg, mpfr_rnd_t rnd);
void mpfr_cospi(mpfr_t ret, mpfr_t arg, mpfr_rnd_t rnd);
#endif
void mpfr_lgamma_nosign(mpfr_t ret, mpfr_t arg, mpfr_rnd_t rnd);
#endif

#ifdef __cplusplus
}

template<typename T>
static double countULP(T ot, const T& oc,
                       const int nbmant, const T& fltmin, const T& fltmax,
                       const bool checkSignedZero=false, const double abound=0.0) {
  if (isnan_(oc) && isnan_(ot)) return 0;
  if (isnan_(oc) || isnan_(ot)) return 10001;
  if (isinf_(oc) && !isinf_(ot)) return INFINITY;

  const T halffltmin = mul_(fltmin, T(0.5));
  const bool ciszero = fabs_(oc) < halffltmin, cisinf = fabs_(oc) > fltmax;

  if (cisinf && isinf_(ot) && signbit_(oc) == signbit_(ot)) return 0;
  if (ciszero && ot != 0) return 10000;
  if (checkSignedZero && ciszero && ot == 0 && signbit_(oc) != signbit_(ot)) return 10002;

  double v = 0;
  if (isinf_(ot) && !isinf_(oc)) {
    ot = copysign_(fltmax, ot);
    v = 1;
  }

  const int ec = ilogb_(oc);

  auto e = fabs_(oc - ot);
  if (e < abound) return 0;

  return double(div_(e, fmax_(ldexp_(T(1), ec + 1 - nbmant), fltmin))) + v;
}
#endif
