//   Copyright Naoki Shibata and contributors 2010 - 2023.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#define DENORMAL_DBL_MIN (4.9406564584124654418e-324)
#define POSITIVE_INFINITY INFINITY
#define NEGATIVE_INFINITY (-INFINITY)

#define DENORMAL_FLT_MIN (1.4012984643248170709e-45f)
#define POSITIVE_INFINITYf ((float)INFINITY)
#define NEGATIVE_INFINITYf (-(float)INFINITY)

#ifndef M_PIf
# define M_PIf ((float)M_PI)
#endif

extern int enableFlushToZero;
double flushToZero(double y);

int isnumber(double x);
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
  union {
    double f;
    uint64_t i;
  } tmp;
  tmp.i = u;
  return tmp.f;
}

static uint64_t d2u(double d) {
  union {
    double f;
    uint64_t i;
  } tmp;
  tmp.f = d;
  return tmp.i;
}

static float u2f(uint32_t u) {
  union {
    float f;
    uint32_t i;
  } tmp;
  tmp.i = u;
  return tmp.f;
}

static uint32_t f2u(float d) {
  union {
    float f;
    uint32_t i;
  } tmp;
  tmp.f = d;
  return tmp.i;
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
