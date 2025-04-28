//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

// Always use -ffp-contract=off option to compile SLEEF.

#include <stdio.h>
#include <assert.h>
#include <stdint.h>
#include <limits.h>
#include <float.h>

#ifndef ENABLE_BUILTIN_MATH
#include <math.h>
#define SQRT sqrt
#else
#define SQRT __builtin_sqrt
#endif

#include "misc.h"

extern const double Sleef_rempitabdp[];

#ifdef DORENAME
#include "rename.h"
#endif

#if defined(_MSC_VER) && !defined (__clang__)
#pragma fp_contract (off)
#else
#pragma STDC FP_CONTRACT OFF
#endif

#define MLA mla
#define C2V(x) (x)
#include "estrin.h"

static INLINE CONST int64_t doubleToRawLongBits(double d) {
  int64_t ret;
  memcpy(&ret, &d, sizeof(ret));
  return ret;
}

static INLINE CONST double longBitsToDouble(int64_t i) {
  double ret;
  memcpy(&ret, &i, sizeof(ret));
  return ret;
}

static INLINE CONST double fabsk(double x) {
  return longBitsToDouble(INT64_C(0x7fffffffffffffff) & doubleToRawLongBits(x));
}

static INLINE CONST double mulsign(double x, double y) {
  return longBitsToDouble(doubleToRawLongBits(x) ^ (doubleToRawLongBits(y) & (INT64_C(1) << 63)));
}

static INLINE CONST double copysignk(double x, double y) {
  return longBitsToDouble((doubleToRawLongBits(x) & ~(INT64_C(1) << 63)) ^ (doubleToRawLongBits(y) & (INT64_C(1) << 63)));
}

static INLINE CONST double sign(double d) { return mulsign(1, d); }
static INLINE CONST double mla(double x, double y, double z) { return x * y + z; }
static INLINE CONST double rintk(double x) { return x < 0 ? (int)(x - 0.5) : (int)(x + 0.5); }
static INLINE CONST int ceilk(double x) { return (int)x + (x < 0 ? 0 : 1); }
static INLINE CONST double trunck(double x) { return (double)(int)x; }
static INLINE CONST double fmink(double x, double y) { return x < y ? x : y; }
static INLINE CONST double fmaxk(double x, double y) { return x > y ? x : y; }

static INLINE CONST int xsignbit(double d) { return (doubleToRawLongBits(d) & doubleToRawLongBits(-0.0)) == doubleToRawLongBits(-0.0); }
static INLINE CONST int xisnan(double x) { return x != x; }
static INLINE CONST int xisinf(double x) { return x == SLEEF_INFINITY || x == -SLEEF_INFINITY; }
static INLINE CONST int xisminf(double x) { return x == -SLEEF_INFINITY; }
static INLINE CONST int xispinf(double x) { return x == SLEEF_INFINITY; }
static INLINE CONST int xisnegzero(double x) { return doubleToRawLongBits(x) == doubleToRawLongBits(-0.0); }
static INLINE CONST int xisnumber(double x) { return !xisinf(x) && !xisnan(x); }

static INLINE CONST int xisint(double d) {
  double x = d - (double)(INT64_C(1) << 31) * (int)(d * (1.0 / (INT64_C(1) << 31)));
  return (x == (int)x) || (fabsk(d) >= (double)(INT64_C(1) << 53));
}

static INLINE CONST int xisodd(double d) {
  double x = d - (double)(INT64_C(1) << 31) * (int)(d * (1.0 / (INT64_C(1) << 31)));
  return (1 & (int)x) != 0 && fabsk(d) < (double)(INT64_C(1) << 53);
}

static INLINE CONST double pow2i(int q) {
  return longBitsToDouble(((int64_t)(q + 0x3ff)) << 52);
}

static INLINE CONST double ldexpk(double x, int q) {
  double u;
  int m;
  m = q >> 31;
  m = (((m + q) >> 9) - m) << 7;
  q = q - (m << 2);
  m += 0x3ff;
  m = m < 0     ? 0     : m;
  m = m > 0x7ff ? 0x7ff : m;
  u = longBitsToDouble(((int64_t)m) << 52);
  x = x * u * u * u * u;
  u = longBitsToDouble(((int64_t)(q + 0x3ff)) << 52);
  return x * u;
}

static INLINE CONST double ldexp2k(double d, int e) { // faster than ldexpk, short reach
  return d * pow2i(e >> 1) * pow2i(e - (e >> 1));
}

static INLINE CONST double ldexp3k(double d, int e) { // very fast, no denormal
  return longBitsToDouble(doubleToRawLongBits(d) + (((int64_t)e) << 52));
}

EXPORT CONST double xldexp(double x, int exp) {
  if (exp >  2100) exp =  2100;
  if (exp < -2100) exp = -2100;

  int e0 = exp >> 2;
  if (exp < 0) e0++;
  if (-100 < exp && exp < 100) e0 = 0;
  int e1 = exp - (e0 << 2);

  double p = pow2i(e0);
  double ret = x * pow2i(e1) * p * p * p * p;

  return ret;
}

static INLINE CONST int ilogbk(double d) {
  int m = d < 4.9090934652977266E-91;
  d = m ? 2.037035976334486E90 * d : d;
  int q = (doubleToRawLongBits(d) >> 52) & 0x7ff;
  q = m ? q - (300 + 0x03ff) : q - 0x03ff;
  return q;
}

// ilogb2k is similar to ilogbk, but the argument has to be a
// normalized FP value.
static INLINE CONST int ilogb2k(double d) {
  return ((doubleToRawLongBits(d) >> 52) & 0x7ff) - 0x3ff;
}

EXPORT CONST int xilogb(double d) {
  int e = ilogbk(fabsk(d));
  e = d == 0.0  ? SLEEF_FP_ILOGB0 : e;
  e = xisnan(d) ? SLEEF_FP_ILOGBNAN : e;
  e = xisinf(d) ? INT_MAX : e;
  return e;
}

//

#ifndef NDEBUG
static int checkfp(double x) {
  if (xisinf(x) || xisnan(x)) return 1;
  return 0;
}
#endif

static INLINE CONST double upper(double d) {
  return longBitsToDouble(doubleToRawLongBits(d) & INT64_C(0xfffffffff8000000));
}

static INLINE CONST Sleef_double2 dd(double h, double l) {
  Sleef_double2 ret;
  ret.x = h; ret.y = l;
  return ret;
}

static INLINE CONST Sleef_double2 ddnormalize_d2_d2(Sleef_double2 t) {
  Sleef_double2 s;

  s.x = t.x + t.y;
  s.y = t.x - s.x + t.y;

  return s;
}

static INLINE CONST Sleef_double2 ddscale_d2_d2_d(Sleef_double2 d, double s) {
  Sleef_double2 r;

  r.x = d.x * s;
  r.y = d.y * s;

  return r;
}

static INLINE CONST Sleef_double2 ddneg_d2_d2(Sleef_double2 d) {
  Sleef_double2 r;

  r.x = -d.x;
  r.y = -d.y;

  return r;
}

static INLINE CONST Sleef_double2 ddabs_d2_d2(Sleef_double2 x) {
  return dd(x.x < 0 ? -x.x : x.x, x.x < 0 ? -x.y : x.y);
}

/*
 * ddadd and ddadd2 are functions for double-double addition.  ddadd
 * is simpler and faster than ddadd2, but it requires the absolute
 * value of first argument to be larger than the second argument. The
 * exact condition that should be met is checked if NDEBUG macro is
 * not defined.
 *
 * Please note that if the results won't be used, it is no problem to
 * feed arguments that do not meet this condition. You will see
 * warning messages if you turn off NDEBUG macro and run tester2, but
 * this is normal.
 *
 * Please see :
 * Jonathan Richard Shewchuk, Adaptive Precision Floating-Point
 * Arithmetic and Fast Robust Geometric Predicates, Discrete &
 * Computational Geometry 18:305-363, 1997.
 */

static INLINE CONST Sleef_double2 ddadd_d2_d_d(double x, double y) {
  // |x| >= |y|

  Sleef_double2 r;

#ifndef NDEBUG
  if (!(checkfp(x) || checkfp(y) || fabsk(x) >= fabsk(y) || (fabsk(x+y) <= fabsk(x) && fabsk(x+y) <= fabsk(y)))) {
    fprintf(stderr, "[ddadd_d2_d_d : %g, %g]\n", x, y);
    fflush(stderr);
  }
#endif

  r.x = x + y;
  r.y = x - r.x + y;

  return r;
}

static INLINE CONST Sleef_double2 ddadd2_d2_d_d(double x, double y) {
  Sleef_double2 r;

  r.x = x + y;
  double v = r.x - x;
  r.y = (x - (r.x - v)) + (y - v);

  return r;
}

static INLINE CONST Sleef_double2 ddadd_d2_d2_d(Sleef_double2 x, double y) {
  // |x| >= |y|

  Sleef_double2 r;

#ifndef NDEBUG
  if (!(checkfp(x.x) || checkfp(y) || fabsk(x.x) >= fabsk(y) || (fabsk(x.x+y) <= fabsk(x.x) && fabsk(x.x+y) <= fabsk(y)))) {
    fprintf(stderr, "[ddadd_d2_d2_d : %g %g]\n", x.x, y);
    fflush(stderr);
  }
#endif

  r.x = x.x + y;
  r.y = x.x - r.x + y + x.y;

  return r;
}

static INLINE CONST Sleef_double2 ddadd2_d2_d2_d(Sleef_double2 x, double y) {
  Sleef_double2 r;

  r.x  = x.x + y;
  double v = r.x - x.x;
  r.y = (x.x - (r.x - v)) + (y - v);
  r.y += x.y;

  return r;
}

static INLINE CONST Sleef_double2 ddadd_d2_d_d2(double x, Sleef_double2 y) {
  // |x| >= |y|

  Sleef_double2 r;

#ifndef NDEBUG
  if (!(checkfp(x) || checkfp(y.x) || fabsk(x) >= fabsk(y.x) || (fabsk(x+y.x) <= fabsk(x) && fabsk(x+y.x) <= fabsk(y.x)))) {
    fprintf(stderr, "[ddadd_d2_d_d2 : %g %g]\n", x, y.x);
    fflush(stderr);
  }
#endif

  r.x = x + y.x;
  r.y = x - r.x + y.x + y.y;

  return r;
}

static INLINE CONST Sleef_double2 ddadd2_d2_d_d2(double x, Sleef_double2 y) {
  Sleef_double2 r;

  r.x  = x + y.x;
  double v = r.x - x;
  r.y = (x - (r.x - v)) + (y.x - v) + y.y;

  return r;
}

static INLINE CONST double ddadd2_d_d_d2(double x, Sleef_double2 y) { return y.y + y.x + x; }

static INLINE CONST Sleef_double2 ddadd_d2_d2_d2(Sleef_double2 x, Sleef_double2 y) {
  // |x| >= |y|

  Sleef_double2 r;

#ifndef NDEBUG
  if (!(x.x == 0 || checkfp(x.x) || checkfp(y.x) || fabsk(x.x) >= fabsk(y.x) || (fabsk(x.x+y.x) <= fabsk(x.x) && fabsk(x.x+y.x) <= fabsk(y.x)))) {
    fprintf(stderr, "[ddadd_d2_d2_d2 : %g %g]\n", x.x, y.x);
    fflush(stderr);
  }
#endif

  r.x = x.x + y.x;
  r.y = x.x - r.x + y.x + x.y + y.y;

  return r;
}

static INLINE CONST Sleef_double2 ddadd2_d2_d2_d2(Sleef_double2 x, Sleef_double2 y) {
  Sleef_double2 r;

  r.x  = x.x + y.x;
  double v = r.x - x.x;
  r.y = (x.x - (r.x - v)) + (y.x - v);
  r.y += x.y + y.y;

  return r;
}

static INLINE CONST Sleef_double2 ddsub_d2_d2_d2(Sleef_double2 x, Sleef_double2 y) {
  // |x| >= |y|

  Sleef_double2 r;

#ifndef NDEBUG
  if (!(checkfp(x.x) || checkfp(y.x) || fabsk(x.x) >= fabsk(y.x) || (fabsk(x.x-y.x) <= fabsk(x.x) && fabsk(x.x-y.x) <= fabsk(y.x)))) {
    fprintf(stderr, "[ddsub_d2_d2_d2 : %g %g]\n", x.x, y.x);
    fflush(stderr);
  }
#endif

  r.x = x.x - y.x;
  r.y = x.x - r.x - y.x + x.y - y.y;

  return r;
}

static INLINE CONST Sleef_double2 dddiv_d2_d2_d2(Sleef_double2 n, Sleef_double2 d) {
  double t = 1.0 / d.x;
  double dh  = upper(d.x), dl  = d.x - dh;
  double th  = upper(t  ), tl  = t   - th;
  double nhh = upper(n.x), nhl = n.x - nhh;

  Sleef_double2 q;

  q.x = n.x * t;

  double u = -q.x + nhh * th + nhh * tl + nhl * th + nhl * tl +
    q.x * (1 - dh * th - dh * tl - dl * th - dl * tl);

  q.y = t * (n.y - q.x * d.y) + u;

  return q;
}

static INLINE CONST Sleef_double2 ddmul_d2_d_d(double x, double y) {
  double xh = upper(x), xl = x - xh;
  double yh = upper(y), yl = y - yh;
  Sleef_double2 r;

  r.x = x * y;
  r.y = xh * yh - r.x + xl * yh + xh * yl + xl * yl;

  return r;
}

static INLINE CONST Sleef_double2 ddmul_d2_d2_d(Sleef_double2 x, double y) {
  double xh = upper(x.x), xl = x.x - xh;
  double yh = upper(y  ), yl = y   - yh;
  Sleef_double2 r;

  r.x = x.x * y;
  r.y = xh * yh - r.x + xl * yh + xh * yl + xl * yl + x.y * y;

  return r;
}

static INLINE CONST Sleef_double2 ddmul_d2_d2_d2(Sleef_double2 x, Sleef_double2 y) {
  double xh = upper(x.x), xl = x.x - xh;
  double yh = upper(y.x), yl = y.x - yh;
  Sleef_double2 r;

  r.x = x.x * y.x;
  r.y = xh * yh - r.x + xl * yh + xh * yl + xl * yl + x.x * y.y + x.y * y.x;

  return r;
}

static INLINE CONST double ddmul_d_d2_d2(Sleef_double2 x, Sleef_double2 y) {
  double xh = upper(x.x), xl = x.x - xh;
  double yh = upper(y.x), yl = y.x - yh;

  return x.y * yh + xh * y.y + xl * yl + xh * yl + xl * yh + xh * yh;
}

static INLINE CONST Sleef_double2 ddsqu_d2_d2(Sleef_double2 x) {
  double xh = upper(x.x), xl = x.x - xh;
  Sleef_double2 r;

  r.x = x.x * x.x;
  r.y = xh * xh - r.x + (xh + xh) * xl + xl * xl + x.x * (x.y + x.y);

  return r;
}

static INLINE CONST double ddsqu_d_d2(Sleef_double2 x) {
  double xh = upper(x.x), xl = x.x - xh;

  return xh * x.y + xh * x.y + xl * xl + (xh * xl + xh * xl) + xh * xh;
}

static INLINE CONST Sleef_double2 ddrec_d2_d(double d) {
  double t = 1.0 / d;
  double dh = upper(d), dl = d - dh;
  double th = upper(t), tl = t - th;
  Sleef_double2 q;

  q.x = t;
  q.y = t * (1 - dh * th - dh * tl - dl * th - dl * tl);

  return q;
}

static INLINE CONST Sleef_double2 ddrec_d2_d2(Sleef_double2 d) {
  double t = 1.0 / d.x;
  double dh = upper(d.x), dl = d.x - dh;
  double th = upper(t  ), tl = t   - th;
  Sleef_double2 q;

  q.x = t;
  q.y = t * (1 - dh * th - dh * tl - dl * th - dl * tl - d.y * t);

  return q;
}

static INLINE CONST Sleef_double2 ddsqrt_d2_d2(Sleef_double2 d) {
  double t = SQRT(d.x + d.y);
  return ddscale_d2_d2_d(ddmul_d2_d2_d2(ddadd2_d2_d2_d2(d, ddmul_d2_d_d(t, t)), ddrec_d2_d(t)), 0.5);
}

static INLINE CONST Sleef_double2 ddsqrt_d2_d(double d) {
  double t = SQRT(d);
  return ddscale_d2_d2_d(ddmul_d2_d2_d2(ddadd2_d2_d_d2(d, ddmul_d2_d_d(t, t)), ddrec_d2_d(t)), 0.5);
}

//

static INLINE CONST double atan2k(double y, double x) {
  double s, t, u;
  int q = 0;

  if (x < 0) { x = -x; q = -2; }
  if (y > x) { t = x; x = y; y = -t; q += 1; }

  s = y / x;
  t = s * s;

  double t2 = t * t, t4 = t2 * t2, t8 = t4 * t4, t16 = t8 * t8;
  u = POLY19(t, t2, t4, t8, t16,
             -1.88796008463073496563746e-05,
             0.000209850076645816976906797,
             -0.00110611831486672482563471,
             0.00370026744188713119232403,
             -0.00889896195887655491740809,
             0.016599329773529201970117,
             -0.0254517624932312641616861,
             0.0337852580001353069993897,
             -0.0407629191276836500001934,
             0.0466667150077840625632675,
             -0.0523674852303482457616113,
             0.0587666392926673580854313,
             -0.0666573579361080525984562,
             0.0769219538311769618355029,
             -0.090908995008245008229153,
             0.111111105648261418443745,
             -0.14285714266771329383765,
             0.199999999996591265594148,
             -0.333333333333311110369124);

  t = u * t * s + s;
  t = q * (M_PI/2) + t;

  return t;
}

EXPORT CONST double xatan2(double y, double x) {
  double r = atan2k(fabsk(y), x);

  r = mulsign(r, x);
  if (xisinf(x) || x == 0) r = M_PI/2 - (xisinf(x) ? (sign(x) * (M_PI  /2)) : 0);
  if (xisinf(y)          ) r = M_PI/2 - (xisinf(x) ? (sign(x) * (M_PI*1/4)) : 0);
  if (             y == 0) r = (sign(x) == -1 ? M_PI : 0);

  return xisnan(x) || xisnan(y) ? SLEEF_NAN : mulsign(r, y);
}

EXPORT CONST double xasin(double d) {
  int o = fabsk(d) < 0.5;
  double x2 = o ? (d*d) : ((1-fabsk(d))*0.5), x = o ? fabsk(d) : SQRT(x2), u;

  double x4 = x2 * x2, x8 = x4 * x4, x16 = x8 * x8;
  u = POLY12(x2, x4, x8, x16,
             +0.3161587650653934628e-1,
             -0.1581918243329996643e-1,
             +0.1929045477267910674e-1,
             +0.6606077476277170610e-2,
             +0.1215360525577377331e-1,
             +0.1388715184501609218e-1,
             +0.1735956991223614604e-1,
             +0.2237176181932048341e-1,
             +0.3038195928038132237e-1,
             +0.4464285681377102438e-1,
             +0.7500000000378581611e-1,
             +0.1666666666666497543e+0);

  u = mla(u, x * x2, x);

  double r = o ? u : (M_PI/2 - 2*u);
  r = mulsign(r, d);

  return r;
}

EXPORT CONST double xacos(double d) {
  int o = fabsk(d) < 0.5;
  double x2 = o ? (d*d) : ((1-fabsk(d))*0.5), u;
  double x = o ? fabsk(d) : SQRT(x2);
  x = fabsk(d) == 1.0 ? 0 : x;

  double x4 = x2 * x2, x8 = x4 * x4, x16 = x8 * x8;
  u = POLY12(x2, x4, x8, x16,
             +0.3161587650653934628e-1,
             -0.1581918243329996643e-1,
             +0.1929045477267910674e-1,
             +0.6606077476277170610e-2,
             +0.1215360525577377331e-1,
             +0.1388715184501609218e-1,
             +0.1735956991223614604e-1,
             +0.2237176181932048341e-1,
             +0.3038195928038132237e-1,
             +0.4464285681377102438e-1,
             +0.7500000000378581611e-1,
             +0.1666666666666497543e+0);

  u *= x * x2;

  double y = 3.1415926535897932/2 - (mulsign(x, d) + mulsign(u, d));
  x += u;
  double r = o ? y : (x*2);
  if (!o && d < 0) r = ddadd_d2_d2_d(dd(3.141592653589793116, 1.2246467991473532072e-16), -r).x;

  return r;
}

EXPORT CONST double xatan(double s) {
  double t, u;
  int q = 0;

  if (sign(s) == -1) { s = -s; q = 2; }
  if (s > 1) { s = 1.0 / s; q |= 1; }

  t = s * s;

  double t2 = t * t, t4 = t2 * t2, t8 = t4 * t4, t16 = t8 * t8;
  u = POLY19(t, t2, t4, t8, t16,
             -1.88796008463073496563746e-05,
             0.000209850076645816976906797,
             -0.00110611831486672482563471,
             0.00370026744188713119232403,
             -0.00889896195887655491740809,
             0.016599329773529201970117,
             -0.0254517624932312641616861,
             0.0337852580001353069993897,
             -0.0407629191276836500001934,
             0.0466667150077840625632675,
             -0.0523674852303482457616113,
             0.0587666392926673580854313,
             -0.0666573579361080525984562,
             0.0769219538311769618355029,
             -0.090908995008245008229153,
             0.111111105648261418443745,
             -0.14285714266771329383765,
             0.199999999996591265594148,
             -0.333333333333311110369124);

  t = s + s * (t * u);

  if ((q & 1) != 0) t = 1.570796326794896557998982 - t;
  if ((q & 2) != 0) t = -t;

  return t;
}

static Sleef_double2 atan2k_u1(Sleef_double2 y, Sleef_double2 x) {
  double u;
  Sleef_double2 s, t;
  int q = 0;

  if (x.x < 0) { x.x = -x.x; x.y = -x.y; q = -2; }
  if (y.x > x.x) { t = x; x = y; y.x = -t.x; y.y = -t.y; q += 1; }

  s = dddiv_d2_d2_d2(y, x);
  t = ddsqu_d2_d2(s);
  t = ddnormalize_d2_d2(t);

  double t2 = t.x * t.x, t4 = t2 * t2, t8 = t4 * t4;
  u = POLY16(t.x, t2, t4, t8,
             1.06298484191448746607415e-05,
             -0.000125620649967286867384336,
             0.00070557664296393412389774,
             -0.00251865614498713360352999,
             0.00646262899036991172313504,
             -0.0128281333663399031014274,
             0.0208024799924145797902497,
             -0.0289002344784740315686289,
             0.0359785005035104590853656,
             -0.041848579703592507506027,
             0.0470843011653283988193763,
             -0.0524914210588448421068719,
             0.0587946590969581003860434,
             -0.0666620884778795497194182,
             0.0769225330296203768654095,
             -0.0909090442773387574781907);
  u = mla(u, t.x, 0.111111108376896236538123);
  u = mla(u, t.x, -0.142857142756268568062339);
  u = mla(u, t.x, 0.199999999997977351284817);
  u = mla(u, t.x, -0.333333333333317605173818);

  t = ddadd_d2_d2_d2(s, ddmul_d2_d2_d(ddmul_d2_d2_d2(s, t), u));

  if (fabsk(s.x) < 1e-200) t = s;
  t = ddadd2_d2_d2_d2(ddmul_d2_d2_d(dd(1.570796326794896557998982, 6.12323399573676603586882e-17), q), t);

  return t;
}

EXPORT CONST double xatan2_u1(double y, double x) {
  if (fabsk(x) < 5.5626846462680083984e-309) { y *= (UINT64_C(1) << 53); x *= (UINT64_C(1) << 53); } // nexttoward((1.0 / DBL_MAX), 1)
  Sleef_double2 d = atan2k_u1(dd(fabsk(y), 0), dd(x, 0));
  double r = d.x + d.y;

  r = mulsign(r, x);
  if (xisinf(x) || x == 0) r = M_PI/2 - (xisinf(x) ? (sign(x) * (M_PI  /2)) : 0);
  if (xisinf(y)          ) r = M_PI/2 - (xisinf(x) ? (sign(x) * (M_PI*1/4)) : 0);
  if (             y == 0) r = (sign(x) == -1 ? M_PI : 0);

  return xisnan(x) || xisnan(y) ? SLEEF_NAN : mulsign(r, y);
}

EXPORT CONST double xasin_u1(double d) {
  int o = fabsk(d) < 0.5;
  double x2 = o ? (d*d) : ((1-fabsk(d))*0.5), u;
  Sleef_double2 x = o ? dd(fabsk(d), 0) : ddsqrt_d2_d(x2);
  x = fabsk(d) == 1.0 ? dd(0, 0) : x;

  double x4 = x2 * x2, x8 = x4 * x4, x16 = x8 * x8;
  u = POLY12(x2, x4, x8, x16,
             +0.3161587650653934628e-1,
             -0.1581918243329996643e-1,
             +0.1929045477267910674e-1,
             +0.6606077476277170610e-2,
             +0.1215360525577377331e-1,
             +0.1388715184501609218e-1,
             +0.1735956991223614604e-1,
             +0.2237176181932048341e-1,
             +0.3038195928038132237e-1,
             +0.4464285681377102438e-1,
             +0.7500000000378581611e-1,
             +0.1666666666666497543e+0);

  u *= x2 * x.x;

  Sleef_double2 y = ddadd_d2_d2_d(ddsub_d2_d2_d2(dd(3.141592653589793116/4, 1.2246467991473532072e-16/4), x), -u);
  double r = o ? (u + x.x) : ((y.x + y.y)*2);
  r = mulsign(r, d);

  return r;
}

EXPORT CONST double xacos_u1(double d) {
  int o = fabsk(d) < 0.5;
  double x2 = o ? (d*d) : ((1-fabsk(d))*0.5), u;
  Sleef_double2 x = o ? dd(fabsk(d), 0) : ddsqrt_d2_d(x2);
  x = fabsk(d) == 1.0 ? dd(0, 0) : x;

  double x4 = x2 * x2, x8 = x4 * x4, x16 = x8 * x8;
  u = POLY12(x2, x4, x8, x16,
             +0.3161587650653934628e-1,
             -0.1581918243329996643e-1,
             +0.1929045477267910674e-1,
             +0.6606077476277170610e-2,
             +0.1215360525577377331e-1,
             +0.1388715184501609218e-1,
             +0.1735956991223614604e-1,
             +0.2237176181932048341e-1,
             +0.3038195928038132237e-1,
             +0.4464285681377102438e-1,
             +0.7500000000378581611e-1,
             +0.1666666666666497543e+0);

  u *= x.x * x2;

  Sleef_double2 y = ddsub_d2_d2_d2(dd(3.141592653589793116/2, 1.2246467991473532072e-16/2),
                                   ddadd_d2_d_d(mulsign(x.x, d), mulsign(u, d)));
  x = ddadd_d2_d2_d(x, u);
  y = o ? y : ddscale_d2_d2_d(x, 2);
  if (!o && d < 0) y = ddsub_d2_d2_d2(dd(3.141592653589793116, 1.2246467991473532072e-16), y);

  return y.x + y.y;
}

EXPORT CONST double xatan_u1(double d) {
  Sleef_double2 d2 = atan2k_u1(dd(fabsk(d), 0), dd(1, 0));
  double r = d2.x + d2.y;
  if (xisinf(d)) r = 1.570796326794896557998982;
  return mulsign(r, d);
}

typedef struct {
  double d;
  int32_t i;
} di_t;

typedef struct {
  Sleef_double2 dd;
  int32_t i;
} ddi_t;

static INLINE CONST double orsign(double x, double y) {
  return longBitsToDouble(doubleToRawLongBits(x) | (doubleToRawLongBits(y) & (INT64_C(1) << 63)));
}

static CONST di_t rempisub(double x) {
  // This function is equivalent to :
  // di_t ret = { x - rint(4 * x) * 0.25, (int32_t)(rint(4 * x) - rint(x) * 4) };
  di_t ret;
  double c = mulsign(INT64_C(1) << 52, x);
  double rint4x = fabsk(4*x) > INT64_C(1) << 52 ? (4*x) : orsign(mla(4, x, c) - c, x);
  double rintx  = fabsk(  x) > INT64_C(1) << 52 ?   x   : orsign(x + c - c       , x);
  ret.d = mla(-0.25, rint4x,      x);
  ret.i = mla(-4   , rintx , rint4x);
  return ret;
}

// Payne-Hanek like argument reduction
static CONST ddi_t rempi(double a) {
  Sleef_double2 x, y;
  di_t di;
  int ex = ilogb2k(a) - 55, q = ex > (700-55) ? -64 : 0;
  a = ldexp3k(a, q);
  if (ex < 0) ex = 0;
  ex *= 4;
  x = ddmul_d2_d_d(a, Sleef_rempitabdp[ex]);
  di = rempisub(x.x);
  q = di.i;
  x.x = di.d;
  x = ddnormalize_d2_d2(x);
  y = ddmul_d2_d_d(a, Sleef_rempitabdp[ex+1]);
  x = ddadd2_d2_d2_d2(x, y);
  di = rempisub(x.x);
  q += di.i;
  x.x = di.d;
  x = ddnormalize_d2_d2(x);
  y = ddmul_d2_d2_d(dd(Sleef_rempitabdp[ex+2], Sleef_rempitabdp[ex+3]), a);
  x = ddadd2_d2_d2_d2(x, y);
  x = ddnormalize_d2_d2(x);
  x = ddmul_d2_d2_d2(x, dd(3.141592653589793116*2, 1.2246467991473532072e-16*2));
  ddi_t ret = { fabsk(a) < 0.7 ? dd(a, 0) : x, q };
  return ret;
}

EXPORT CONST double xsin(double d) {
  double u, s, t = d;
  int ql;

  if (fabsk(d) < TRIGRANGEMAX2) {
    ql = rintk(d * M_1_PI);
    d = mla(ql, -PI_A2, d);
    d = mla(ql, -PI_B2, d);
  } else if (fabsk(d) < TRIGRANGEMAX) {
    double dqh = trunck(d * (M_1_PI / (1 << 24))) * (double)(1 << 24);
    ql = rintk(mla(d, M_1_PI, -dqh));

    d = mla(dqh, -PI_A, d);
    d = mla( ql, -PI_A, d);
    d = mla(dqh, -PI_B, d);
    d = mla( ql, -PI_B, d);
    d = mla(dqh, -PI_C, d);
    d = mla( ql, -PI_C, d);
    d = mla(dqh + ql, -PI_D, d);
  } else {
    ddi_t ddi = rempi(t);
    ql = ((ddi.i & 3) * 2 + (ddi.dd.x > 0) + 1) >> 2;
    if ((ddi.i & 1) != 0) {
      ddi.dd = ddadd2_d2_d2_d2(ddi.dd, dd(mulsign(3.141592653589793116*-0.5, ddi.dd.x),
                                          mulsign(1.2246467991473532072e-16*-0.5, ddi.dd.x)));
    }
    d = ddi.dd.x + ddi.dd.y;
    if (xisinf(t) || xisnan(t)) d = SLEEF_NAN;
  }

  s = d * d;

  if ((ql & 1) != 0) d = -d;

  double s2 = s * s, s4 = s2 * s2;
  u = POLY8(s, s2, s4,
            -7.97255955009037868891952e-18,
            2.81009972710863200091251e-15,
            -7.64712219118158833288484e-13,
            1.60590430605664501629054e-10,
            -2.50521083763502045810755e-08,
            2.75573192239198747630416e-06,
            -0.000198412698412696162806809,
            0.00833333333333332974823815);
  u = mla(u, s, -0.166666666666666657414808);

  u = mla(s, u * d, d);

  if (xisnegzero(t)) u = t;

  return u;
}

EXPORT CONST double xsin_u1(double d) {
  double u;
  Sleef_double2 s, t, x;
  int ql;

  if (fabsk(d) < TRIGRANGEMAX2) {
    ql = rintk(d * M_1_PI);
    u = mla(ql, -PI_A2, d);
    s = ddadd_d2_d_d (u,  ql * -PI_B2);
  } else if (fabsk(d) < TRIGRANGEMAX) {
    const double dqh = trunck(d * (M_1_PI / (1 << 24))) * (double)(1 << 24);
    ql = rintk(mla(d, M_1_PI, -dqh));

    u = mla(dqh, -PI_A, d);
    s = ddadd_d2_d_d  (u,  ql * -PI_A);
    s = ddadd2_d2_d2_d(s, dqh * -PI_B);
    s = ddadd2_d2_d2_d(s,  ql * -PI_B);
    s = ddadd2_d2_d2_d(s, dqh * -PI_C);
    s = ddadd2_d2_d2_d(s,  ql * -PI_C);
    s = ddadd_d2_d2_d (s, (dqh + ql) * -PI_D);
  } else {
    ddi_t ddi = rempi(d);
    ql = ((ddi.i & 3) * 2 + (ddi.dd.x > 0) + 1) >> 2;
    if ((ddi.i & 1) != 0) {
      ddi.dd = ddadd2_d2_d2_d2(ddi.dd, dd(mulsign(3.141592653589793116*-0.5, ddi.dd.x),
                                          mulsign(1.2246467991473532072e-16*-0.5, ddi.dd.x)));
    }
    s = ddnormalize_d2_d2(ddi.dd);
    if (xisinf(d) || xisnan(d)) s.x = SLEEF_NAN;
  }

  t = s;
  s = ddsqu_d2_d2(s);

  double s2 = s.x * s.x, s4 = s2 * s2;
  u = POLY6(s.x, s2, s4,
            2.72052416138529567917983e-15,
            -7.6429259411395447190023e-13,
            1.60589370117277896211623e-10,
            -2.5052106814843123359368e-08,
            2.75573192104428224777379e-06,
            -0.000198412698412046454654947);
  u = mla(u, s.x, 0.00833333333333318056201922);

  x = ddadd_d2_d_d2(1, ddmul_d2_d2_d2(ddadd_d2_d_d(-0.166666666666666657414808, u * s.x), s));
  u = ddmul_d_d2_d2(t, x);

  if ((ql & 1) != 0) u = -u;
  if (xisnegzero(d)) u = d;

  return u;
}

EXPORT CONST double xcos(double d) {
  double u, s, t = d;
  int ql;

  if (fabsk(d) < TRIGRANGEMAX2) {
    ql = mla(2, rintk(d * M_1_PI - 0.5), 1);
    d = mla(ql, -PI_A2*0.5, d);
    d = mla(ql, -PI_B2*0.5, d);
  } else if (fabsk(d) < TRIGRANGEMAX) {
    double dqh = trunck(d * (M_1_PI / (INT64_C(1) << 23)) - 0.5 * (M_1_PI / (INT64_C(1) << 23)));
    ql = 2*rintk(d * M_1_PI - 0.5 - dqh * (double)(INT64_C(1) << 23))+1;
    dqh *= 1 << 24;

    d = mla(dqh, -PI_A*0.5, d);
    d = mla( ql, -PI_A*0.5, d);
    d = mla(dqh, -PI_B*0.5, d);
    d = mla( ql, -PI_B*0.5, d);
    d = mla(dqh, -PI_C*0.5, d);
    d = mla( ql, -PI_C*0.5, d);
    d = mla(dqh + ql , -PI_D*0.5, d);
  } else {
    ddi_t ddi = rempi(t);
    ql = ((ddi.i & 3) * 2 + (ddi.dd.x > 0) + 7) >> 1;
    if ((ddi.i & 1) == 0) {
      ddi.dd = ddadd2_d2_d2_d2(ddi.dd, dd(mulsign(3.141592653589793116*-0.5, ddi.dd.x > 0 ? 1 : -1),
                                          mulsign(1.2246467991473532072e-16*-0.5, ddi.dd.x > 0 ? 1 : -1)));
    }
    d = ddi.dd.x + ddi.dd.y;
    if (xisinf(t) || xisnan(t)) d = SLEEF_NAN;
  }

  s = d * d;

  if ((ql & 2) == 0) d = -d;

  double s2 = s * s, s4 = s2 * s2;
  u = POLY8(s, s2, s4,
            -7.97255955009037868891952e-18,
            2.81009972710863200091251e-15,
            -7.64712219118158833288484e-13,
            1.60590430605664501629054e-10,
            -2.50521083763502045810755e-08,
            2.75573192239198747630416e-06,
            -0.000198412698412696162806809,
            0.00833333333333332974823815);
  u = mla(u, s, -0.166666666666666657414808);

  u = mla(s, u * d, d);

  return u;
}

EXPORT CONST double xcos_u1(double d) {
  double u;
  Sleef_double2 s, t, x;
  int ql;

  d = fabsk(d);

  if (d < TRIGRANGEMAX2) {
    ql = mla(2, rintk(d * M_1_PI - 0.5), 1);
    s = ddadd2_d2_d_d(d, ql * (-PI_A2*0.5));
    s = ddadd_d2_d2_d(s, ql * (-PI_B2*0.5));
  } else if (d < TRIGRANGEMAX) {
    double dqh = trunck(d * (M_1_PI / (INT64_C(1) << 23)) - 0.5 * (M_1_PI / (INT64_C(1) << 23)));
    ql = 2*rintk(d * M_1_PI - 0.5 - dqh * (double)(INT64_C(1) << 23))+1;
    dqh *= 1 << 24;

    u = mla(dqh, -PI_A*0.5, d);
    s = ddadd2_d2_d_d (u,  ql * (-PI_A*0.5));
    s = ddadd2_d2_d2_d(s, dqh * (-PI_B*0.5));
    s = ddadd2_d2_d2_d(s,  ql * (-PI_B*0.5));
    s = ddadd2_d2_d2_d(s, dqh * (-PI_C*0.5));
    s = ddadd2_d2_d2_d(s,  ql * (-PI_C*0.5));
    s = ddadd_d2_d2_d(s, (dqh + ql) * (-PI_D*0.5));
  } else {
    ddi_t ddi = rempi(d);
    ql = ((ddi.i & 3) * 2 + (ddi.dd.x > 0) + 7) >> 1;
    if ((ddi.i & 1) == 0) {
      ddi.dd = ddadd2_d2_d2_d2(ddi.dd, dd(mulsign(3.141592653589793116*-0.5, ddi.dd.x > 0 ? 1 : -1),
                                          mulsign(1.2246467991473532072e-16*-0.5, ddi.dd.x > 0 ? 1 : -1)));
    }
    s = ddnormalize_d2_d2(ddi.dd);
    if (xisinf(d) || xisnan(d)) s.x = SLEEF_NAN;
  }

  t = s;
  s = ddsqu_d2_d2(s);

  double s2 = s.x * s.x, s4 = s2 * s2;
  u = POLY6(s.x, s2, s4,
            2.72052416138529567917983e-15,
            -7.6429259411395447190023e-13,
            1.60589370117277896211623e-10,
            -2.5052106814843123359368e-08,
            2.75573192104428224777379e-06,
            -0.000198412698412046454654947);
  u = mla(u, s.x, 0.00833333333333318056201922);

  x = ddadd_d2_d_d2(1, ddmul_d2_d2_d2(ddadd_d2_d_d(-0.166666666666666657414808, u * s.x), s));
  u = ddmul_d_d2_d2(t, x);

  if ((((int)ql) & 2) == 0) u = -u;

  return u;
}

EXPORT CONST Sleef_double2 xsincos(double d) {
  double u, s, t;
  Sleef_double2 r;
  int ql;

  s = d;

  if (fabsk(d) < TRIGRANGEMAX2) {
    ql = rintk(s * (2 * M_1_PI));
    s = mla(ql, -PI_A2*0.5, s);
    s = mla(ql, -PI_B2*0.5, s);
  } else if (fabsk(d) < TRIGRANGEMAX) {
    double dqh = trunck(d * ((2 * M_1_PI) / (1 << 24))) * (double)(1 << 24);
    ql = rintk(d * (2 * M_1_PI) - dqh);

    s = mla(dqh, -PI_A * 0.5, s);
    s = mla( ql, -PI_A * 0.5, s);
    s = mla(dqh, -PI_B * 0.5, s);
    s = mla( ql, -PI_B * 0.5, s);
    s = mla(dqh, -PI_C * 0.5, s);
    s = mla( ql, -PI_C * 0.5, s);
    s = mla(dqh + ql, -PI_D * 0.5, s);
  } else {
    ddi_t ddi = rempi(d);
    ql = ddi.i;
    s = ddi.dd.x + ddi.dd.y;
    if (xisinf(d) || xisnan(d)) s = SLEEF_NAN;
  }

  t = s;

  s = s * s;

  u = 1.58938307283228937328511e-10;
  u = mla(u, s, -2.50506943502539773349318e-08);
  u = mla(u, s, 2.75573131776846360512547e-06);
  u = mla(u, s, -0.000198412698278911770864914);
  u = mla(u, s, 0.0083333333333191845961746);
  u = mla(u, s, -0.166666666666666130709393);
  u = u * s * t;

  r.x = t + u;

  if (xisnegzero(d)) r.x = -0.0;

  u = -1.13615350239097429531523e-11;
  u = mla(u, s, 2.08757471207040055479366e-09);
  u = mla(u, s, -2.75573144028847567498567e-07);
  u = mla(u, s, 2.48015872890001867311915e-05);
  u = mla(u, s, -0.00138888888888714019282329);
  u = mla(u, s, 0.0416666666666665519592062);
  u = mla(u, s, -0.5);

  r.y = u * s + 1;

  if ((ql & 1) != 0) { s = r.y; r.y = r.x; r.x = s; }
  if ((ql & 2) != 0) { r.x = -r.x; }
  if (((ql+1) & 2) != 0) { r.y = -r.y; }

  return r;
}

EXPORT CONST Sleef_double2 xsincos_u1(double d) {
  double u;
  Sleef_double2 r, s, t, x;
  int ql;

  if (fabsk(d) < TRIGRANGEMAX2) {
    ql = rintk(d * (2 * M_1_PI));
    u = mla(ql, -PI_A2*0.5, d);
    s = ddadd_d2_d_d (u,  ql * (-PI_B2*0.5));
  } else if (fabsk(d) < TRIGRANGEMAX) {
    const double dqh = trunck(d * ((2 * M_1_PI) / (1 << 24))) * (double)(1 << 24);
    ql = rintk(d * (2 * M_1_PI) - dqh);

    u = mla(dqh, -PI_A*0.5, d);
    s = ddadd_d2_d_d(u, ql * (-PI_A*0.5));
    s = ddadd2_d2_d2_d(s, dqh * (-PI_B*0.5));
    s = ddadd2_d2_d2_d(s, ql * (-PI_B*0.5));
    s = ddadd2_d2_d2_d(s, dqh * (-PI_C*0.5));
    s = ddadd2_d2_d2_d(s, ql * (-PI_C*0.5));
    s = ddadd_d2_d2_d(s, (dqh + ql) * (-PI_D*0.5));
  } else {
    ddi_t ddi = rempi(d);
    ql = ddi.i;
    s = ddi.dd;
    if (xisinf(d) || xisnan(d)) s = dd(SLEEF_NAN, SLEEF_NAN);
  }

  t = s;

  s.x = ddsqu_d_d2(s);

  u = 1.58938307283228937328511e-10;
  u = mla(u, s.x, -2.50506943502539773349318e-08);
  u = mla(u, s.x, 2.75573131776846360512547e-06);
  u = mla(u, s.x, -0.000198412698278911770864914);
  u = mla(u, s.x, 0.0083333333333191845961746);
  u = mla(u, s.x, -0.166666666666666130709393);

  u *= s.x * t.x;

  x = ddadd_d2_d2_d(t, u);
  r.x = x.x + x.y;

  if (xisnegzero(d)) r.x = -0.0;

  u = -1.13615350239097429531523e-11;
  u = mla(u, s.x, 2.08757471207040055479366e-09);
  u = mla(u, s.x, -2.75573144028847567498567e-07);
  u = mla(u, s.x, 2.48015872890001867311915e-05);
  u = mla(u, s.x, -0.00138888888888714019282329);
  u = mla(u, s.x, 0.0416666666666665519592062);
  u = mla(u, s.x, -0.5);

  x = ddadd_d2_d_d2(1, ddmul_d2_d_d(s.x, u));
  r.y = x.x + x.y;

  if ((ql & 1) != 0) { u = r.y; r.y = r.x; r.x = u; }
  if ((ql & 2) != 0) { r.x = -r.x; }
  if (((ql+1) & 2) != 0) { r.y = -r.y; }

  return r;
}

EXPORT CONST Sleef_double2 xsincospi_u05(double d) {
  double u, s, t;
  Sleef_double2 r, x, s2;

  u = d * 4;
  int q = ceilk(u) & ~(int)1;

  s = u - (double)q;
  t = s;
  s = s * s;
  s2 = ddmul_d2_d_d(t, t);

  //

  u = -2.02461120785182399295868e-14;
  u = mla(u, s, 6.94821830580179461327784e-12);
  u = mla(u, s, -1.75724749952853179952664e-09);
  u = mla(u, s, 3.13361688966868392878422e-07);
  u = mla(u, s, -3.6576204182161551920361e-05);
  u = mla(u, s, 0.00249039457019271850274356);
  x = ddadd2_d2_d_d2(u * s, dd(-0.0807455121882807852484731, 3.61852475067037104849987e-18));
  x = ddadd2_d2_d2_d2(ddmul_d2_d2_d2(s2, x), dd(0.785398163397448278999491, 3.06287113727155002607105e-17));

  x = ddmul_d2_d2_d(x, t);
  r.x = x.x + x.y;

  if (xisnegzero(d)) r.x = -0.0;

  //

  u = 9.94480387626843774090208e-16;
  u = mla(u, s, -3.89796226062932799164047e-13);
  u = mla(u, s, 1.15011582539996035266901e-10);
  u = mla(u, s, -2.4611369501044697495359e-08);
  u = mla(u, s, 3.59086044859052754005062e-06);
  u = mla(u, s, -0.000325991886927389905997954);
  x = ddadd2_d2_d_d2(u * s, dd(0.0158543442438155018914259, -1.04693272280631521908845e-18));
  x = ddadd2_d2_d2_d2(ddmul_d2_d2_d2(s2, x), dd(-0.308425137534042437259529, -1.95698492133633550338345e-17));

  x = ddadd2_d2_d2_d(ddmul_d2_d2_d2(x, s2), 1);
  r.y = x.x + x.y;

  //

  if ((q & 2) != 0) { s = r.y; r.y = r.x; r.x = s; }
  if ((q & 4) != 0) { r.x = -r.x; }
  if (((q+2) & 4) != 0) { r.y = -r.y; }

  if (fabsk(d) > TRIGRANGEMAX3/4) { r.x = 0; r.y = 1; }
  if (xisinf(d)) { r.x = r.y = SLEEF_NAN; }

  return r;
}

EXPORT CONST Sleef_double2 xsincospi_u35(double d) {
  double u, s, t;
  Sleef_double2 r;

  u = d * 4;
  int q = ceilk(u) & ~(int)1;

  s = u - (double)q;
  t = s;
  s = s * s;

  //

  u = +0.6880638894766060136e-11;
  u = mla(u, s, -0.1757159564542310199e-8);
  u = mla(u, s, +0.3133616327257867311e-6);
  u = mla(u, s, -0.3657620416388486452e-4);
  u = mla(u, s, +0.2490394570189932103e-2);
  u = mla(u, s, -0.8074551218828056320e-1);
  u = mla(u, s, +0.7853981633974482790e+0);

  r.x = u * t;

  //

  u = -0.3860141213683794352e-12;
  u = mla(u, s, +0.1150057888029681415e-9);
  u = mla(u, s, -0.2461136493006663553e-7);
  u = mla(u, s, +0.3590860446623516713e-5);
  u = mla(u, s, -0.3259918869269435942e-3);
  u = mla(u, s, +0.1585434424381541169e-1);
  u = mla(u, s, -0.3084251375340424373e+0);
  u = mla(u, s, 1);

  r.y = u;

  //

  if ((q & 2) != 0) { s = r.y; r.y = r.x; r.x = s; }
  if ((q & 4) != 0) { r.x = -r.x; }
  if (((q+2) & 4) != 0) { r.y = -r.y; }

  if (fabsk(d) > TRIGRANGEMAX3/4) { r.x = 0; r.y = 1; }
  if (xisinf(d)) { r.x = r.y = SLEEF_NAN; }

  return r;
}

static INLINE CONST Sleef_double2 sinpik(double d) {
  double u, s, t;
  Sleef_double2 x, s2;

  u = d * 4;
  int q = ceilk(u) & ~1;
  int o = (q & 2) != 0;

  s = u - (double)q;
  t = s;
  s = s * s;
  s2 = ddmul_d2_d_d(t, t);

  //

  u = o ? 9.94480387626843774090208e-16 : -2.02461120785182399295868e-14;
  u = mla(u, s, o ? -3.89796226062932799164047e-13 : 6.94821830580179461327784e-12);
  u = mla(u, s, o ? 1.15011582539996035266901e-10 : -1.75724749952853179952664e-09);
  u = mla(u, s, o ? -2.4611369501044697495359e-08 : 3.13361688966868392878422e-07);
  u = mla(u, s, o ? 3.59086044859052754005062e-06 : -3.6576204182161551920361e-05);
  u = mla(u, s, o ? -0.000325991886927389905997954 : 0.00249039457019271850274356);
  x = ddadd2_d2_d_d2(u * s, o ? dd(0.0158543442438155018914259, -1.04693272280631521908845e-18) :
                     dd(-0.0807455121882807852484731, 3.61852475067037104849987e-18));
  x = ddadd2_d2_d2_d2(ddmul_d2_d2_d2(s2, x), o ? dd(-0.308425137534042437259529, -1.95698492133633550338345e-17) :
                      dd(0.785398163397448278999491, 3.06287113727155002607105e-17));

  x = ddmul_d2_d2_d2(x, o ? s2 : dd(t, 0));
  x = o ? ddadd2_d2_d2_d(x, 1) : x;

  //

  if ((q & 4) != 0) { x.x = -x.x; x.y = -x.y; }

  return x;
}

EXPORT CONST double xsinpi_u05(double d) {
  Sleef_double2 x = sinpik(d);
  double r = x.x + x.y;

  if (xisnegzero(d)) r = -0.0;
  if (fabsk(d) > TRIGRANGEMAX3/4) r = 0;
  if (xisinf(d)) r = SLEEF_NAN;

  return r;
}

static INLINE CONST Sleef_double2 cospik(double d) {
  double u, s, t;
  Sleef_double2 x, s2;

  u = d * 4;
  int q = ceilk(u) & ~1;
  int o = (q & 2) == 0;

  s = u - (double)q;
  t = s;
  s = s * s;
  s2 = ddmul_d2_d_d(t, t);

  //

  u = o ? 9.94480387626843774090208e-16 : -2.02461120785182399295868e-14;
  u = mla(u, s, o ? -3.89796226062932799164047e-13 : 6.94821830580179461327784e-12);
  u = mla(u, s, o ? 1.15011582539996035266901e-10 : -1.75724749952853179952664e-09);
  u = mla(u, s, o ? -2.4611369501044697495359e-08 : 3.13361688966868392878422e-07);
  u = mla(u, s, o ? 3.59086044859052754005062e-06 : -3.6576204182161551920361e-05);
  u = mla(u, s, o ? -0.000325991886927389905997954 : 0.00249039457019271850274356);
  x = ddadd2_d2_d_d2(u * s, o ? dd(0.0158543442438155018914259, -1.04693272280631521908845e-18) :
                     dd(-0.0807455121882807852484731, 3.61852475067037104849987e-18));
  x = ddadd2_d2_d2_d2(ddmul_d2_d2_d2(s2, x), o ? dd(-0.308425137534042437259529, -1.95698492133633550338345e-17) :
                      dd(0.785398163397448278999491, 3.06287113727155002607105e-17));

  x = ddmul_d2_d2_d2(x, o ? s2 : dd(t, 0));
  x = o ? ddadd2_d2_d2_d(x, 1) : x;

  //

  if (((q+2) & 4) != 0) { x.x = -x.x; x.y = -x.y; }

  return x;
}

EXPORT CONST double xcospi_u05(double d) {
  Sleef_double2 x = cospik(d);
  double r = x.x + x.y;

  if (fabsk(d) > TRIGRANGEMAX3/4) r = 1;
  if (xisinf(d)) r = SLEEF_NAN;

  return r;
}

EXPORT CONST double xtan(double d) {
  double u, s, x, y;
  int ql;

  if (fabsk(d) < TRIGRANGEMAX2) {
    ql = rintk(d * (2 * M_1_PI));
    x = mla(ql, -PI_A2*0.5, d);
    x = mla(ql, -PI_B2*0.5, x);
  } else if (fabsk(d) < 1e+6) {
    double dqh = trunck(d * ((2 * M_1_PI) / (1 << 24))) * (double)(1 << 24);
    ql = rintk(d * (2 * M_1_PI) - dqh);

    x = mla(dqh, -PI_A * 0.5, d);
    x = mla( ql, -PI_A * 0.5, x);
    x = mla(dqh, -PI_B * 0.5, x);
    x = mla( ql, -PI_B * 0.5, x);
    x = mla(dqh, -PI_C * 0.5, x);
    x = mla( ql, -PI_C * 0.5, x);
    x = mla(dqh + ql, -PI_D * 0.5, x);
  } else {
    ddi_t ddi = rempi(d);
    ql = ddi.i;
    x = ddi.dd.x + ddi.dd.y;
    if (xisinf(d) || xisnan(d)) x = SLEEF_NAN;
  }

  x *= 0.5;
  s = x * x;

  double s2 = s * s, s4 = s2 * s2;
  u = POLY8(s, s2, s4,
            +0.3245098826639276316e-3,
            +0.5619219738114323735e-3,
            +0.1460781502402784494e-2,
            +0.3591611540792499519e-2,
            +0.8863268409563113126e-2,
            +0.2186948728185535498e-1,
            +0.5396825399517272970e-1,
            +0.1333333333330500581e+0);

  u = mla(u, s, +0.3333333333333343695e+0);
  u = mla(s, u * x, x);

  y = mla(u, u, -1);
  x = -2 * u;

  if ((ql & 1) != 0) { double t = x; x = y; y = -t; }

  u = x / y;

  return u;
}

EXPORT CONST double xtan_u1(double d) {
  double u;
  Sleef_double2 s, t, x, y;
  int ql;

  if (fabsk(d) < TRIGRANGEMAX2) {
    ql = rintk(d * (2 * M_1_PI));
    u = mla(ql, -PI_A2*0.5, d);
    s = ddadd_d2_d_d(u,  ql * (-PI_B2*0.5));
  } else if (fabsk(d) < TRIGRANGEMAX) {
    const double dqh = trunck(d * (M_2_PI / (1 << 24))) * (double)(1 << 24);
    s = ddadd2_d2_d2_d(ddmul_d2_d2_d(dd(M_2_PI_H, M_2_PI_L), d), (d < 0 ? -0.5 : 0.5) - dqh);
    ql = s.x + s.y;

    u = mla(dqh, -PI_A*0.5, d);
    s = ddadd_d2_d_d  (u,  ql * (-PI_A*0.5));
    s = ddadd2_d2_d2_d(s, dqh * (-PI_B*0.5));
    s = ddadd2_d2_d2_d(s,  ql * (-PI_B*0.5));
    s = ddadd2_d2_d2_d(s, dqh * (-PI_C*0.5));
    s = ddadd2_d2_d2_d(s,  ql * (-PI_C*0.5));
    s = ddadd_d2_d2_d(s, (dqh + ql) * (-PI_D*0.5));
  } else {
    ddi_t ddi = rempi(d);
    ql = ddi.i;
    s = ddi.dd;
    if (xisinf(d) || xisnan(d)) s.x = SLEEF_NAN;
  }

  t = ddscale_d2_d2_d(s, 0.5);
  s = ddsqu_d2_d2(t);

  double s2 = s.x * s.x, s4 = s2 * s2;
  u = POLY8(s.x, s2, s4,
            +0.3245098826639276316e-3,
            +0.5619219738114323735e-3,
            +0.1460781502402784494e-2,
            +0.3591611540792499519e-2,
            +0.8863268409563113126e-2,
            +0.2186948728185535498e-1,
            +0.5396825399517272970e-1,
            +0.1333333333330500581e+0);

  u = mla(u, s.x, +0.3333333333333343695e+0);
  x = ddadd_d2_d2_d2(t, ddmul_d2_d2_d(ddmul_d2_d2_d2(s, t), u));

  y = ddadd_d2_d_d2(-1, ddsqu_d2_d2(x));
  x = ddscale_d2_d2_d(x, -2);

  if ((ql & 1) != 0) { t = x; x = y; y = ddneg_d2_d2(t); }

  x = dddiv_d2_d2_d2(x, y);

  u = x.x + x.y;

  if (xisnegzero(d)) u = d;

  return u;
}

EXPORT CONST double xlog(double d) {
  double x, x2, t, m;
  int e;

  int o = d < DBL_MIN;
  if (o) d *= (double)(INT64_C(1) << 32) * (double)(INT64_C(1) << 32);

  e = ilogb2k(d * (1.0/0.75));
  m = ldexp3k(d, -e);

  if (o) e -= 64;

  x = (m-1) / (m+1);
  x2 = x * x;

  double x4 = x2 * x2, x8 = x4 * x4;

  t = POLY7(x2, x4, x8,
            0.153487338491425068243146,
            0.152519917006351951593857,
            0.181863266251982985677316,
            0.222221366518767365905163,
            0.285714294746548025383248,
            0.399999999950799600689777,
            0.6666666666667778740063);

  x = x * 2 + 0.693147180559945286226764 * e + x * x2 * t;

  if (xisinf(d)) x = SLEEF_INFINITY;
  if (d < 0 || xisnan(d)) x = SLEEF_NAN;
  if (d == 0) x = -SLEEF_INFINITY;

  return x;
}

EXPORT CONST double xexp(double d) {
  int q = (int)rintk(d * R_LN2);
  double s, u;

  s = mla(q, -L2U, d);
  s = mla(q, -L2L, s);

  double s2 = s * s, s4 = s2 * s2, s8 = s4 * s4;
  u = POLY10(s, s2, s4, s8,
             2.08860621107283687536341e-09,
             2.51112930892876518610661e-08,
             2.75573911234900471893338e-07,
             2.75572362911928827629423e-06,
             2.4801587159235472998791e-05,
             0.000198412698960509205564975,
             0.00138888888889774492207962,
             0.00833333333331652721664984,
             0.0416666666666665047591422,
             0.166666666666666851703837);
  u = mla(u, s, +0.5);

  u = s * s * u + s + 1;
  u = ldexp2k(u, q);

  if (d > 709.78271114955742909217217426) u = SLEEF_INFINITY;
  if (d < -1000) u = 0;

  return u;
}

static INLINE CONST double expm1k(double d) {
  int q = (int)rintk(d * R_LN2);
  double s, u;

  s = mla(q, -L2U, d);
  s = mla(q, -L2L, s);

  double s2 = s * s, s4 = s2 * s2, s8 = s4 * s4;
  u = POLY10(s, s2, s4, s8,
             2.08860621107283687536341e-09,
             2.51112930892876518610661e-08,
             2.75573911234900471893338e-07,
             2.75572362911928827629423e-06,
             2.4801587159235472998791e-05,
             0.000198412698960509205564975,
             0.00138888888889774492207962,
             0.00833333333331652721664984,
             0.0416666666666665047591422,
             0.166666666666666851703837);

  u = mla(s2, 0.5, s2 * s * u) + s;

  if (q != 0) u = ldexp2k(u + 1, q) - 1;

  return u;
}

static INLINE CONST Sleef_double2 logk(double d) {
  Sleef_double2 x, x2, s;
  double m, t;
  int e;

  int o = d < DBL_MIN;
  if (o) d *= (double)(INT64_C(1) << 32) * (double)(INT64_C(1) << 32);

  e = ilogb2k(d * (1.0/0.75));
  m = ldexp3k(d, -e);

  if (o) e -= 64;

  x = dddiv_d2_d2_d2(ddadd2_d2_d_d(-1, m), ddadd2_d2_d_d(1, m));
  x2 = ddsqu_d2_d2(x);

  double x4 = x2.x * x2.x, x8 = x4 * x4, x16 = x8 * x8;
  t = POLY9(x2.x, x4, x8, x16,
            0.116255524079935043668677,
            0.103239680901072952701192,
            0.117754809412463995466069,
            0.13332981086846273921509,
            0.153846227114512262845736,
            0.181818180850050775676507,
            0.222222222230083560345903,
            0.285714285714249172087875,
            0.400000000000000077715612);

  Sleef_double2 c = dd(0.666666666666666629659233, 3.80554962542412056336616e-17);
  s = ddmul_d2_d2_d(dd(0.693147180559945286226764, 2.319046813846299558417771e-17), e);
  s = ddadd_d2_d2_d2(s, ddscale_d2_d2_d(x, 2));
  x = ddmul_d2_d2_d2(x2, x);
  s = ddadd_d2_d2_d2(s, ddmul_d2_d2_d2(x, c));
  x = ddmul_d2_d2_d2(x2, x);
  s = ddadd_d2_d2_d2(s, ddmul_d2_d2_d(x, t));

  return s;
}

EXPORT CONST double xlog_u1(double d) {
  Sleef_double2 x, s;
  double m, t, x2;
  int e;

  int o = d < DBL_MIN;
  if (o) d *= (double)(INT64_C(1) << 32) * (double)(INT64_C(1) << 32);

  e = ilogb2k(d * (1.0/0.75));
  m = ldexp3k(d, -e);

  if (o) e -= 64;

  x = dddiv_d2_d2_d2(ddadd2_d2_d_d(-1, m), ddadd2_d2_d_d(1, m));
  x2 = x.x * x.x;

  double x4 = x2 * x2, x8 = x4 * x4;
  t = POLY7(x2, x4, x8,
            0.1532076988502701353e+0,
            0.1525629051003428716e+0,
            0.1818605932937785996e+0,
            0.2222214519839380009e+0,
            0.2857142932794299317e+0,
            0.3999999999635251990e+0,
            0.6666666666667333541e+0);

  s = ddmul_d2_d2_d(dd(0.693147180559945286226764, 2.319046813846299558417771e-17), (double)e);
  s = ddadd_d2_d2_d2(s, ddscale_d2_d2_d(x, 2));
  s = ddadd_d2_d2_d(s, x2 * x.x * t);

  double r = s.x + s.y;

  if (xisinf(d)) r = SLEEF_INFINITY;
  if (d < 0 || xisnan(d)) r = SLEEF_NAN;
  if (d == 0) r = -SLEEF_INFINITY;

  return r;
}

static INLINE CONST double expk(Sleef_double2 d) {
  int q = (int)rintk((d.x + d.y) * R_LN2);
  Sleef_double2 s, t;
  double u;

  s = ddadd2_d2_d2_d(d, q * -L2U);
  s = ddadd2_d2_d2_d(s, q * -L2L);

  s = ddnormalize_d2_d2(s);

  double s2 = s.x * s.x, s4 = s2 * s2, s8 = s4 * s4;
  u = POLY10(s.x, s2, s4, s8,
             2.51069683420950419527139e-08,
             2.76286166770270649116855e-07,
             2.75572496725023574143864e-06,
             2.48014973989819794114153e-05,
             0.000198412698809069797676111,
             0.0013888888939977128960529,
             0.00833333333332371417601081,
             0.0416666666665409524128449,
             0.166666666666666740681535,
             0.500000000000000999200722);

  t = ddadd_d2_d_d2(1, s);
  t = ddadd_d2_d2_d2(t, ddmul_d2_d2_d(ddsqu_d2_d2(s), u));

  u = ldexpk(t.x + t.y, q);

  if (d.x < -1000) u = 0;

  return u;
}

EXPORT CONST double xpow(double x, double y) {
  int yisint = xisint(y);
  int yisodd = yisint && xisodd(y);

  Sleef_double2 d = ddmul_d2_d2_d(logk(fabsk(x)), y);
  double result = expk(d);

  result = (d.x > 709.78271114955742909217217426 || xisnan(result)) ? SLEEF_INFINITY : result;
  result *= (x > 0 ? 1 : (yisint ? (yisodd ? -1 : 1) : SLEEF_NAN));

  double efx = mulsign(fabsk(x) - 1, y);
  if (xisinf(y)) result = efx < 0 ? 0.0 : (efx == 0 ? 1.0 : SLEEF_INFINITY);
  if (xisinf(x) || x == 0) result = mulsign((xsignbit(y) ^ (x == 0)) ? 0 : SLEEF_INFINITY, yisodd ? x : 1);
  if (xisnan(x) || xisnan(y)) result = SLEEF_NAN;
  if (y == 0 || x == 1) result = 1;

  return result;
}

static INLINE CONST Sleef_double2 expk2(Sleef_double2 d) {
  int q = (int)rintk((d.x + d.y) * R_LN2);
  Sleef_double2 s, t;
  double u;

  s = ddadd2_d2_d2_d(d, q * -L2U);
  s = ddadd2_d2_d2_d(s, q * -L2L);

  u = +0.1602472219709932072e-9;
  u = mla(u, s.x, +0.2092255183563157007e-8);
  u = mla(u, s.x, +0.2505230023782644465e-7);
  u = mla(u, s.x, +0.2755724800902135303e-6);
  u = mla(u, s.x, +0.2755731892386044373e-5);
  u = mla(u, s.x, +0.2480158735605815065e-4);
  u = mla(u, s.x, +0.1984126984148071858e-3);
  u = mla(u, s.x, +0.1388888888886763255e-2);
  u = mla(u, s.x, +0.8333333333333347095e-2);
  u = mla(u, s.x, +0.4166666666666669905e-1);

  t = ddadd2_d2_d2_d(ddmul_d2_d2_d(s, u), +0.1666666666666666574e+0);
  t = ddadd2_d2_d2_d(ddmul_d2_d2_d2(s, t), 0.5);
  t = ddadd2_d2_d2_d2(s, ddmul_d2_d2_d2(ddsqu_d2_d2(s), t));

  t = ddadd2_d2_d_d2(1, t);

  t.x = ldexp2k(t.x, q);
  t.y = ldexp2k(t.y, q);

  return d.x < -1000 ? dd(0, 0) : t;
}

EXPORT CONST double xsinh(double x) {
  double y = fabsk(x);
  Sleef_double2 d = expk2(dd(y, 0));
  d = ddsub_d2_d2_d2(d, ddrec_d2_d2(d));
  y = (d.x + d.y) * 0.5;

  y = fabsk(x) > 710 ? SLEEF_INFINITY : y;
  y = xisnan(y) ? SLEEF_INFINITY : y;
  y = mulsign(y, x);
  y = xisnan(x) ? SLEEF_NAN : y;

  return y;
}

EXPORT CONST double xcosh(double x) {
  double y = fabsk(x);
  Sleef_double2 d = expk2(dd(y, 0));
  d = ddadd_d2_d2_d2(d, ddrec_d2_d2(d));
  y = (d.x + d.y) * 0.5;

  y = fabsk(x) > 710 ? SLEEF_INFINITY : y;
  y = xisnan(y) ? SLEEF_INFINITY : y;
  y = xisnan(x) ? SLEEF_NAN : y;

  return y;
}

EXPORT CONST double xtanh(double x) {
  double y = fabsk(x);
  Sleef_double2 d = expk2(dd(y, 0));
  Sleef_double2 e = ddrec_d2_d2(d);
  d = dddiv_d2_d2_d2(ddsub_d2_d2_d2(d, e), ddadd_d2_d2_d2(d, e));
  y = d.x + d.y;

  y = fabsk(x) > 18.714973875 ? 1.0 : y;
  y = xisnan(y) ? 1.0 : y;
  y = mulsign(y, x);
  y = xisnan(x) ? SLEEF_NAN : y;

  return y;
}

EXPORT CONST double xsinh_u35(double x) {
  double e = expm1k(fabsk(x));
  double y = (e + 2) / (e + 1) * (0.5 * e);

  y = fabsk(x) > 709 ? SLEEF_INFINITY : y;
  y = xisnan(y) ? SLEEF_INFINITY : y;
  y = mulsign(y, x);
  y = xisnan(x) ? SLEEF_NAN : y;

  return y;
}

EXPORT CONST double xcosh_u35(double x) {
  double e = xexp(fabsk(x));
  double y = 0.5 / e + 0.5 * e;

  y = fabsk(x) > 709 ? SLEEF_INFINITY : y;
  y = xisnan(y) ? SLEEF_INFINITY : y;
  y = xisnan(x) ? SLEEF_NAN : y;

  return y;
}

EXPORT CONST double xtanh_u35(double x) {
  double y = fabsk(x);
  double d = expm1k(2*y);
  y = d / (d + 2);

  y = fabsk(x) > 18.714973875 ? 1.0 : y;
  y = xisnan(y) ? 1.0 : y;
  y = mulsign(y, x);
  y = xisnan(x) ? SLEEF_NAN : y;

  return y;
}

static INLINE CONST Sleef_double2 logk2(Sleef_double2 d) {
  Sleef_double2 x, x2, m, s;
  double t;
  int e;

  e = ilogbk(d.x * (1.0/0.75));

  m.x = ldexp2k(d.x, -e);
  m.y = ldexp2k(d.y, -e);

  x = dddiv_d2_d2_d2(ddadd2_d2_d2_d(m, -1), ddadd2_d2_d2_d(m, 1));
  x2 = ddsqu_d2_d2(x);

  double x4 = x2.x * x2.x, x8 = x4 * x4;
  t = POLY7(x2.x, x4, x8,
            0.13860436390467167910856,
            0.131699838841615374240845,
            0.153914168346271945653214,
            0.181816523941564611721589,
            0.22222224632662035403996,
            0.285714285511134091777308,
            0.400000000000914013309483);
  t = mla(t, x2.x, 0.666666666666664853302393);

  s = ddmul_d2_d2_d(dd(0.693147180559945286226764, 2.319046813846299558417771e-17), e);
  s = ddadd_d2_d2_d2(s, ddscale_d2_d2_d(x, 2));
  s = ddadd_d2_d2_d2(s, ddmul_d2_d2_d(ddmul_d2_d2_d2(x2, x), t));

  return s;
}

EXPORT CONST double xasinh(double x) {
  double y = fabsk(x);
  Sleef_double2 d;

  d = y > 1 ? ddrec_d2_d(x) : dd(y, 0);
  d = ddsqrt_d2_d2(ddadd2_d2_d2_d(ddsqu_d2_d2(d), 1));
  d = y > 1 ? ddmul_d2_d2_d(d, y) : d;

  d = logk2(ddnormalize_d2_d2(ddadd_d2_d2_d(d, x)));
  y = d.x + d.y;

  y = (fabsk(x) > SQRT_DBL_MAX || xisnan(y)) ? mulsign(SLEEF_INFINITY, x) : y;
  y = xisnan(x) ? SLEEF_NAN : y;
  y = xisnegzero(x) ? -0.0 : y;

  return y;
}

EXPORT CONST double xacosh(double x) {
  Sleef_double2 d = logk2(ddadd2_d2_d2_d(ddmul_d2_d2_d2(ddsqrt_d2_d2(ddadd2_d2_d_d(x, 1)), ddsqrt_d2_d2(ddadd2_d2_d_d(x, -1))), x));
  double y = d.x + d.y;

  y = (x > SQRT_DBL_MAX || xisnan(y)) ? SLEEF_INFINITY : y;
  y = x == 1.0 ? 0.0 : y;
  y = x < 1.0 ? SLEEF_NAN : y;
  y = xisnan(x) ? SLEEF_NAN : y;

  return y;
}

EXPORT CONST double xatanh(double x) {
  double y = fabsk(x);
  Sleef_double2 d = logk2(dddiv_d2_d2_d2(ddadd2_d2_d_d(1, y), ddadd2_d2_d_d(1, -y)));
  y = y > 1.0 ? SLEEF_NAN : (y == 1.0 ? SLEEF_INFINITY : (d.x + d.y) * 0.5);

  y = mulsign(y, x);
  y = (xisinf(x) || xisnan(y)) ? SLEEF_NAN : y;

  return y;
}

//

EXPORT CONST double xcbrt(double d) { // max error : 2 ulps
  double x, y, q = 1.0;
  int e, r;

  e = ilogbk(fabsk(d))+1;
  d = ldexp2k(d, -e);
  r = (e + 6144) % 3;
  q = (r == 1) ? 1.2599210498948731647672106 : q;
  q = (r == 2) ? 1.5874010519681994747517056 : q;
  q = ldexp2k(q, (e + 6144) / 3 - 2048);

  q = mulsign(q, d);
  d = fabsk(d);

  x = -0.640245898480692909870982;
  x = mla(x, d, 2.96155103020039511818595);
  x = mla(x, d, -5.73353060922947843636166);
  x = mla(x, d, 6.03990368989458747961407);
  x = mla(x, d, -3.85841935510444988821632);
  x = mla(x, d, 2.2307275302496609725722);

  y = x * x; y = y * y; x -= (d * y - x) * (1.0 / 3.0);
  y = d * x * x;
  y = (y - (2.0 / 3.0) * y * (y * x - 1)) * q;

  return y;
}

EXPORT CONST double xcbrt_u1(double d) {
  double x, y, z;
  Sleef_double2 q2 = dd(1, 0), u, v;
  int e, r;

  e = ilogbk(fabsk(d))+1;
  d = ldexp2k(d, -e);
  r = (e + 6144) % 3;
  q2 = (r == 1) ? dd(1.2599210498948731907, -2.5899333753005069177e-17) : q2;
  q2 = (r == 2) ? dd(1.5874010519681995834, -1.0869008194197822986e-16) : q2;

  q2.x = mulsign(q2.x, d); q2.y = mulsign(q2.y, d);
  d = fabsk(d);

  x = -0.640245898480692909870982;
  x = mla(x, d, 2.96155103020039511818595);
  x = mla(x, d, -5.73353060922947843636166);
  x = mla(x, d, 6.03990368989458747961407);
  x = mla(x, d, -3.85841935510444988821632);
  x = mla(x, d, 2.2307275302496609725722);

  y = x * x; y = y * y; x -= (d * y - x) * (1.0 / 3.0);

  z = x;

  u = ddmul_d2_d_d(x, x);
  u = ddmul_d2_d2_d2(u, u);
  u = ddmul_d2_d2_d(u, d);
  u = ddadd2_d2_d2_d(u, -x);
  y = u.x + u.y;

  y = -2.0 / 3.0 * y * z;
  v = ddadd2_d2_d2_d(ddmul_d2_d_d(z, z), y);
  v = ddmul_d2_d2_d(v, d);
  v = ddmul_d2_d2_d2(v, q2);
  z = ldexp2k(v.x + v.y, (e + 6144) / 3 - 2048);

  if (xisinf(d)) { z = mulsign(SLEEF_INFINITY, q2.x); }
  if (d == 0) { z = mulsign(0, q2.x); }

  return z;
}

EXPORT CONST double xexp2(double d) {
  int q = (int)rintk(d);
  double s, u;

  s = d - q;

  double s2 = s * s, s4 = s2 * s2, s8 = s4 * s4;
  u = POLY10(s, s2, s4, s8,
             +0.4434359082926529454e-9,
             +0.7073164598085707425e-8,
             +0.1017819260921760451e-6,
             +0.1321543872511327615e-5,
             +0.1525273353517584730e-4,
             +0.1540353045101147808e-3,
             +0.1333355814670499073e-2,
             +0.9618129107597600536e-2,
             +0.5550410866482046596e-1,
             +0.2402265069591012214e+0);
  u = mla(u, s, +0.6931471805599452862e+0);

  u = ddnormalize_d2_d2(ddadd_d2_d_d2(1, ddmul_d2_d_d(u, s))).x;

  u = ldexp2k(u, q);

  if (d >= 1024) u = SLEEF_INFINITY;
  if (d < -2000) u = 0;

  return u;
}

EXPORT CONST double xexp2_u35(double d) {
  int q = (int)rintk(d);
  double s, u;

  s = d - q;

  u = +0.4434359082926529454e-9;
  u = mla(u, s, +0.7073164598085707425e-8);
  u = mla(u, s, +0.1017819260921760451e-6);
  u = mla(u, s, +0.1321543872511327615e-5);
  u = mla(u, s, +0.1525273353517584730e-4);
  u = mla(u, s, +0.1540353045101147808e-3);
  u = mla(u, s, +0.1333355814670499073e-2);
  u = mla(u, s, +0.9618129107597600536e-2);
  u = mla(u, s, +0.5550410866482046596e-1);
  u = mla(u, s, +0.2402265069591012214e+0);
  u = mla(u, s, +0.6931471805599452862e+0);
  u = mla(u, s, +0.1000000000000000000e+1);

  u = ldexp2k(u, q);

  if (d >= 1024) u = SLEEF_INFINITY;
  if (d < -2000) u = 0;

  return u;
}

EXPORT CONST double xexp10(double d) {
  int q = (int)rintk(d * LOG10_2);
  double s, u;

  s = mla(q, -L10U, d);
  s = mla(q, -L10L, s);

  u = +0.2411463498334267652e-3;
  u = mla(u, s, +0.1157488415217187375e-2);
  u = mla(u, s, +0.5013975546789733659e-2);
  u = mla(u, s, +0.1959762320720533080e-1);
  u = mla(u, s, +0.6808936399446784138e-1);
  u = mla(u, s, +0.2069958494722676234e+0);
  u = mla(u, s, +0.5393829292058536229e+0);
  u = mla(u, s, +0.1171255148908541655e+1);
  u = mla(u, s, +0.2034678592293432953e+1);
  u = mla(u, s, +0.2650949055239205876e+1);
  u = mla(u, s, +0.2302585092994045901e+1);

  u = ddnormalize_d2_d2(ddadd_d2_d_d2(1, ddmul_d2_d_d(u, s))).x;

  u = ldexp2k(u, q);

  if (d > 308.25471555991671) u = SLEEF_INFINITY; // log10(DBL_MAX)
  if (d < -350) u = 0;

  return u;
}

EXPORT CONST double xexp10_u35(double d) {
  int q = (int)rintk(d * LOG10_2);
  double s, u;

  s = mla(q, -L10U, d);
  s = mla(q, -L10L, s);

  u = +0.2411463498334267652e-3;
  u = mla(u, s, +0.1157488415217187375e-2);
  u = mla(u, s, +0.5013975546789733659e-2);
  u = mla(u, s, +0.1959762320720533080e-1);
  u = mla(u, s, +0.6808936399446784138e-1);
  u = mla(u, s, +0.2069958494722676234e+0);
  u = mla(u, s, +0.5393829292058536229e+0);
  u = mla(u, s, +0.1171255148908541655e+1);
  u = mla(u, s, +0.2034678592293432953e+1);
  u = mla(u, s, +0.2650949055239205876e+1);
  u = mla(u, s, +0.2302585092994045901e+1);
  u = mla(u, s, +0.1000000000000000000e+1);

  u = ldexp2k(u, q);

  if (d > 308.25471555991671) u = SLEEF_INFINITY;
  if (d < -350) u = 0;

  return u;
}

EXPORT CONST double xexpm1(double a) {
  Sleef_double2 d = ddadd2_d2_d2_d(expk2(dd(a, 0)), -1.0);
  double x = d.x + d.y;
  if (a > 709.782712893383996732223) x = SLEEF_INFINITY; // log(DBL_MAX)
  if (a < -36.736800569677101399113302437) x = -1; // log(1 - nexttoward(1, 0))
  if (xisnegzero(a)) x = -0.0;
  return x;
}

EXPORT CONST double xlog10(double d) {
  Sleef_double2 x, s;
  double m, t, x2;
  int e;

  int o = d < DBL_MIN;
  if (o) d *= (double)(INT64_C(1) << 32) * (double)(INT64_C(1) << 32);

  e = ilogb2k(d * (1.0/0.75));
  m = ldexp3k(d, -e);

  if (o) e -= 64;

  x = dddiv_d2_d2_d2(ddadd2_d2_d_d(-1, m), ddadd2_d2_d_d(1, m));
  x2 = x.x * x.x;

  double x4 = x2 * x2, x8 = x4 * x4;
  t = POLY7(x2, x4, x8,
            +0.6653725819576758460e-1,
            +0.6625722782820833712e-1,
            +0.7898105214313944078e-1,
            +0.9650955035715275132e-1,
            +0.1240841409721444993e+0,
            +0.1737177927454605086e+0,
            +0.2895296546021972617e+0);

  s = ddmul_d2_d2_d(dd(0.30102999566398119802, -2.803728127785170339e-18), (double)e);
  s = ddadd_d2_d2_d2(s, ddmul_d2_d2_d2(x, dd(0.86858896380650363334, 1.1430059694096389311e-17)));
  s = ddadd_d2_d2_d(s, x2 * x.x * t);

  double r = s.x + s.y;

  if (xisinf(d)) r = SLEEF_INFINITY;
  if (d < 0 || xisnan(d)) r = SLEEF_NAN;
  if (d == 0) r = -SLEEF_INFINITY;

  return r;
}

EXPORT CONST double xlog2(double d) {
  Sleef_double2 x, s;
  double m, t, x2;
  int e;

  int o = d < DBL_MIN;
  if (o) d *= (double)(INT64_C(1) << 32) * (double)(INT64_C(1) << 32);

  e = ilogb2k(d * (1.0/0.75));
  m = ldexp3k(d, -e);

  if (o) e -= 64;

  x = dddiv_d2_d2_d2(ddadd2_d2_d_d(-1, m), ddadd2_d2_d_d(1, m));
  x2 = x.x * x.x;

  double x4 = x2 * x2, x8 = x4 * x4;
  t = POLY7(x2, x4, x8,
            +0.2211941750456081490e+0,
            +0.2200768693152277689e+0,
            +0.2623708057488514656e+0,
            +0.3205977477944495502e+0,
            +0.4121985945485324709e+0,
            +0.5770780162997058982e+0,
            +0.96179669392608091449);

  s = ddadd2_d2_d_d2(e, ddmul_d2_d2_d2(x, dd(2.885390081777926774, 6.0561604995516736434e-18)));
  s = ddadd2_d2_d2_d(s, x2 * x.x * t);

  double r = s.x + s.y;

  if (xisinf(d)) r = SLEEF_INFINITY;
  if (d < 0 || xisnan(d)) r = SLEEF_NAN;
  if (d == 0) r = -SLEEF_INFINITY;

  return r;
}

EXPORT CONST double xlog2_u35(double d) {
  double m, t, x, x2;
  int e;

  int o = d < DBL_MIN;
  if (o) d *= (double)(INT64_C(1) << 32) * (double)(INT64_C(1) << 32);

  e = ilogb2k(d * (1.0/0.75));
  m = ldexp3k(d, -e);

  if (o) e -= 64;

  x = (m - 1) / (m + 1);
  x2 = x * x;

  t = +0.2211941750456081490e+0;
  t = mla(t, x2, +0.2200768693152277689e+0);
  t = mla(t, x2, +0.2623708057488514656e+0);
  t = mla(t, x2, +0.3205977477944495502e+0);
  t = mla(t, x2, +0.4121985945485324709e+0);
  t = mla(t, x2, +0.5770780162997058982e+0);
  t = mla(t, x2, +0.96179669392608091449  );

  Sleef_double2 s = ddadd_d2_d_d2(e, ddmul_d2_d_d(2.885390081777926774, x));
  double r = mla(t, x * x2, s.x + s.y);

  if (xisinf(d)) r = SLEEF_INFINITY;
  if (d < 0 || xisnan(d)) r = SLEEF_NAN;
  if (d == 0) r = -SLEEF_INFINITY;

  return r;
}

EXPORT CONST double xlog1p(double d) {
  Sleef_double2 x, s;
  double m, t, x2;
  int e;

  double dp1 = d + 1;

  int o = dp1 < DBL_MIN;
  if (o) dp1 *= (double)(INT64_C(1) << 32) * (double)(INT64_C(1) << 32);

  e = ilogb2k(dp1 * (1.0/0.75));

  t = ldexp3k(1, -e);
  m = mla(d, t, t - 1);

  if (o) e -= 64;

  x = dddiv_d2_d2_d2(dd(m, 0), ddadd_d2_d_d(2, m));
  x2 = x.x * x.x;

  double x4 = x2 * x2, x8 = x4 * x4;
  t = POLY7(x2, x4, x8,
            0.1532076988502701353e+0,
            0.1525629051003428716e+0,
            0.1818605932937785996e+0,
            0.2222214519839380009e+0,
            0.2857142932794299317e+0,
            0.3999999999635251990e+0,
            0.6666666666667333541e+0);

  s = ddmul_d2_d2_d(dd(0.693147180559945286226764, 2.319046813846299558417771e-17), (double)e);
  s = ddadd_d2_d2_d2(s, ddscale_d2_d2_d(x, 2));
  s = ddadd_d2_d2_d(s, x2 * x.x * t);

  double r = s.x + s.y;

  if (d > 1e+307) r = SLEEF_INFINITY;
  if (d < -1 || xisnan(d)) r = SLEEF_NAN;
  if (d == -1) r = -SLEEF_INFINITY;
  if (xisnegzero(d)) r = -0.0;

  return r;
}

//

EXPORT CONST double xfma(double x, double y, double z) {
  double h2 = x * y + z, q = 1;
  if (fabsk(h2) < 1e-300) {
    const double c0 = UINT64_C(1) << 54, c1 = c0 * c0, c2 = c1 * c1;
    x *= c1;
    y *= c1;
    z *= c2;
    q = 1.0 / c2;
  }
  if (fabsk(h2) > 1e+299) {
    const double c0 = UINT64_C(1) << 54, c1 = c0 * c0, c2 = c1 * c1;
    x *= 1.0 / c1;
    y *= 1.0 / c1;
    z *= 1. / c2;
    q = c2;
  }
  Sleef_double2 d = ddmul_d2_d_d(x, y);
  d = ddadd2_d2_d2_d(d, z);
  double ret = (x == 0 || y == 0) ? z : (d.x + d.y);
  if ((xisinf(z) && !xisinf(x) && !xisnan(x) && !xisinf(y) && !xisnan(y))) h2 = z;
  return (xisinf(h2) || xisnan(h2)) ? h2 : ret*q;
}

EXPORT CONST double xsqrt_u05(double d) {
  double q = 0.5;

  d = d < 0 ? SLEEF_NAN : d;

  if (d < 8.636168555094445E-78) {
    d *= 1.157920892373162E77;
    q = 2.9387358770557188E-39 * 0.5;
  }

  if (d > 1.3407807929942597e+154) {
    d *= 7.4583407312002070e-155;
    q = 1.1579208923731620e+77 * 0.5;
  }

  // http://en.wikipedia.org/wiki/Fast_inverse_square_root
  double x = longBitsToDouble(0x5fe6ec85e7de30da - (doubleToRawLongBits(d + 1e-320) >> 1));

  x = x * (1.5 - 0.5 * d * x * x);
  x = x * (1.5 - 0.5 * d * x * x);
  x = x * (1.5 - 0.5 * d * x * x) * d;

  Sleef_double2 d2 = ddmul_d2_d2_d2(ddadd2_d2_d_d2(d, ddmul_d2_d_d(x, x)), ddrec_d2_d(x));

  double ret = (d2.x + d2.y) * q;

  ret = d == SLEEF_INFINITY ? SLEEF_INFINITY : ret;
  ret = d == 0 ? d : ret;

  return ret;
}

EXPORT CONST double xsqrt_u35(double d) { return xsqrt_u05(d); }
EXPORT CONST double xsqrt(double d) { return SQRT(d); }

EXPORT CONST double xfabs(double x) { return fabsk(x); }

EXPORT CONST double xcopysign(double x, double y) { return copysignk(x, y); }

EXPORT CONST double xfmax(double x, double y) {
  return y != y ? x : (x > y ? x : y);
}

EXPORT CONST double xfmin(double x, double y) {
  return y != y ? x : (x < y ? x : y);
}

EXPORT CONST double xfdim(double x, double y) {
  double ret = x - y;
  if (ret < 0 || x == y) ret = 0;
  return ret;
}

EXPORT CONST double xtrunc(double x) {
  double fr = x - (double)(INT64_C(1) << 31) * (int32_t)(x * (1.0 / (INT64_C(1) << 31)));
  fr = fr - (int32_t)fr;
  return (xisinf(x) || fabsk(x) >= (double)(INT64_C(1) << 52)) ? x : copysignk(x - fr, x);
}

EXPORT CONST double xfloor(double x) {
  double fr = x - (double)(INT64_C(1) << 31) * (int32_t)(x * (1.0 / (INT64_C(1) << 31)));
  fr = fr - (int32_t)fr;
  fr = fr < 0 ? fr+1.0 : fr;
  return (xisinf(x) || fabsk(x) >= (double)(INT64_C(1) << 52)) ? x : copysignk(x - fr, x);
}

EXPORT CONST double xceil(double x) {
  double fr = x - (double)(INT64_C(1) << 31) * (int32_t)(x * (1.0 / (INT64_C(1) << 31)));
  fr = fr - (int32_t)fr;
  fr = fr <= 0 ? fr : fr-1.0;
  return (xisinf(x) || fabsk(x) >= (double)(INT64_C(1) << 52)) ? x : copysignk(x - fr, x);
}

EXPORT CONST double xround(double d) {
  double x = d + 0.5;
  double fr = x - (double)(INT64_C(1) << 31) * (int32_t)(x * (1.0 / (INT64_C(1) << 31)));
  fr = fr - (int32_t)fr;
  if (fr == 0 && x <= 0) x--;
  fr = fr < 0 ? fr+1.0 : fr;
  x = d == 0.49999999999999994449 ? 0 : x;  // nextafter(0.5, 0)
  return (xisinf(d) || fabsk(d) >= (double)(INT64_C(1) << 52)) ? d : copysignk(x - fr, d);
}

EXPORT CONST double xrint(double d) {
  double c = mulsign(INT64_C(1) << 52, d);
  return fabsk(d) > INT64_C(1) << 52 ? d : orsign(d + c - c, d);
}

EXPORT CONST double xhypot_u05(double x, double y) {
  x = fabsk(x);
  y = fabsk(y);
  double min = fmink(x, y), n = min;
  double max = fmaxk(x, y), d = max;

  if (max < DBL_MIN) { n *= UINT64_C(1) << 54; d *= UINT64_C(1) << 54; }
  Sleef_double2 t = dddiv_d2_d2_d2(dd(n, 0), dd(d, 0));
  t = ddmul_d2_d2_d(ddsqrt_d2_d2(ddadd2_d2_d2_d(ddsqu_d2_d2(t), 1)), max);
  double ret = t.x + t.y;
  if (xisnan(ret)) ret = SLEEF_INFINITY;
  if (min == 0) ret = max;
  if (xisnan(x) || xisnan(y)) ret = SLEEF_NAN;
  if (x == SLEEF_INFINITY || y == SLEEF_INFINITY) ret = SLEEF_INFINITY;
  return ret;
}

EXPORT CONST double xhypot_u35(double x, double y) {
  x = fabsk(x);
  y = fabsk(y);
  double min = fmink(x, y);
  double max = fmaxk(x, y);

  double t = min / max;
  double ret = max * SQRT(1 + t*t);
  if (min == 0) ret = max;
  if (xisnan(x) || xisnan(y)) ret = SLEEF_NAN;
  if (x == SLEEF_INFINITY || y == SLEEF_INFINITY) ret = SLEEF_INFINITY;
  return ret;
}

EXPORT CONST double xnextafter(double x, double y) {
  double cxf;
  int64_t cxi;

  x = x == 0 ? mulsign(0, y) : x;
  cxf = x;
  memcpy(&cxi, &cxf, sizeof(cxi));

  int c = (cxi < 0) == (y < x);
  if (c) cxi = -(cxi ^ (int64_t)(UINT64_C(1) << 63));

  if (x != y) cxi--;

  if (c) cxi = -(cxi ^ (int64_t)(UINT64_C(1) << 63));

  memcpy(&cxf, &cxi, sizeof(cxf));
  if (cxf == 0 && x != 0) cxf = mulsign(0, x);
  if (x == 0 && y == 0) cxf = y;
  if (xisnan(x) || xisnan(y)) cxf = SLEEF_NAN;

  return cxf;
}

EXPORT CONST double xfrfrexp(double x) {
  double cxf;
  uint64_t cxu;

  if (fabsk(x) < DBL_MIN) x *= (UINT64_C(1) << 63);

  cxf = x;
  memcpy(&cxu, &cxf, sizeof(cxu));

  cxu &= ~UINT64_C(0x7ff0000000000000);
  cxu |=  UINT64_C(0x3fe0000000000000);

  memcpy(&cxf, &cxu, sizeof(cxf));
  if (xisinf(x)) cxf = mulsign(SLEEF_INFINITY, x);
  if (x == 0) cxf = x;

  return cxf;
}

EXPORT CONST int xexpfrexp(double x) {
  double cxf;
  uint64_t cxu;

  int ret = 0;

  if (fabsk(x) < DBL_MIN) { x *= (UINT64_C(1) << 63); ret = -63; }

  cxf = x;
  memcpy(&cxu, &cxf, sizeof(cxu));

  ret += (int32_t)(((cxu >> 52) & 0x7ff)) - 0x3fe;

  if (x == 0 || xisnan(x) || xisinf(x)) ret = 0;

  return ret;
}

static INLINE CONST double toward0(double d) {
  return d == 0 ? 0 : longBitsToDouble(doubleToRawLongBits(d)-1);
}

static INLINE CONST double removelsb(double d) {
  return longBitsToDouble(doubleToRawLongBits(d) & INT64_C(0xfffffffffffffffe));
}

static INLINE CONST double ptrunc(double x) {
  double fr = mla(-(double)(INT64_C(1) << 31), (int32_t)(x * (1.0 / (INT64_C(1) << 31))), x);
  return fabsk(x) >= (double)(INT64_C(1) << 52) ? x : (x - (fr - (int32_t)fr));
}

EXPORT CONST double xfmod(double x, double y) {
  double n = fabsk(x), d = fabsk(y), s = 1, q;
  if (d < DBL_MIN) { n *= UINT64_C(1) << 54; d *= UINT64_C(1) << 54; s = 1.0 / (UINT64_C(1) << 54); }
  Sleef_double2 r = dd(n, 0);
  double rd = toward0(1.0 / d);

  for(int i=0;i < 21;i++) { // ceil(log2(DBL_MAX) / 52)
    q = removelsb(ptrunc(toward0(r.x) * rd));
    q = (3*d > r.x && r.x > d) ? 2 : q;
    q = (2*d > r.x && r.x > d) ? 1 : q;
    q = r.x == d ? (r.y >= 0 ? 1 : 0) : q;
    r = ddnormalize_d2_d2(ddadd2_d2_d2_d2(r, ddmul_d2_d_d(q, -d)));
    if (r.x < d) break;
  }

  double ret = r.x * s;
  if (r.x + r.y == d) ret = 0;
  ret = mulsign(ret, x);
  if (n < d) ret = x;
  if (d == 0) ret = SLEEF_NAN;

  return ret;
}

static INLINE CONST double rintk2(double d) {
  double c = mulsign(INT64_C(1) << 52, d);
  return fabsk(d) > INT64_C(1) << 52 ? d : orsign(d + c - c, d);
}

EXPORT CONST double xremainder(double x, double y) {
  double n = fabsk(x), d = fabsk(y), s = 1, q;
  if (d < DBL_MIN*2) { n *= UINT64_C(1) << 54; d *= UINT64_C(1) << 54; s = 1.0 / (UINT64_C(1) << 54); }
  double rd = 1.0 / d;
  Sleef_double2 r = dd(n, 0);
  int qisodd = 0;

  for(int i=0;i < 21;i++) { // ceil(log2(DBL_MAX) / 52)
    q = removelsb(rintk2(r.x * rd));
    if (fabsk(r.x) < 1.5 * d) q = r.x < 0 ? -1 : 1;
    if (fabsk(r.x) < 0.5 * d || (fabsk(r.x) == 0.5 * d && !qisodd)) q = 0;
    if (q == 0) break;
    if (xisinf(q * -d)) q = q + mulsign(-1, r.x);
    qisodd ^= xisodd(q);
    r = ddnormalize_d2_d2(ddadd2_d2_d2_d2(r, ddmul_d2_d_d(q, -d)));
  }

  double ret = r.x * s;
  ret = mulsign(ret, x);
  if (xisinf(y)) ret = xisinf(x) ? SLEEF_NAN : x;
  if (d == 0) ret = SLEEF_NAN;

  return ret;
}

EXPORT CONST Sleef_double2 xmodf(double x) {
  double fr = x - (double)(INT64_C(1) << 31) * (int32_t)(x * (1.0 / (INT64_C(1) << 31)));
  fr = fr - (int32_t)fr;
  fr = fabsk(x) >= (double)(INT64_C(1) << 52) ? 0 : fr;
  Sleef_double2 ret = { copysignk(fr, x), copysignk(x - fr, x) };
  return ret;
}

typedef struct {
  Sleef_double2 a, b;
} dd2;

static CONST dd2 gammak(double a) {
  Sleef_double2 clc = dd(0, 0), clln = dd(1, 0), clld = dd(1, 0), x, y, z;
  double t, u;

  int otiny = fabsk(a) < 1e-306, oref = a < 0.5;

  x = otiny ? dd(0, 0) : (oref ? ddadd2_d2_d_d(1, -a) : dd(a, 0));

  int o0 = (0.5 <= x.x && x.x <= 1.1), o2 = 2.3 < x.x;

  y = ddnormalize_d2_d2(ddmul_d2_d2_d2(ddadd2_d2_d2_d(x, 1), x));
  y = ddnormalize_d2_d2(ddmul_d2_d2_d2(ddadd2_d2_d2_d(x, 2), y));
  y = ddnormalize_d2_d2(ddmul_d2_d2_d2(ddadd2_d2_d2_d(x, 3), y));
  y = ddnormalize_d2_d2(ddmul_d2_d2_d2(ddadd2_d2_d2_d(x, 4), y));

  clln = (o2 && x.x <= 7) ? y : clln;

  x = (o2 && x.x <= 7) ? ddadd2_d2_d2_d(x, 5) : x;
  t = o2 ? (1.0 / x.x) : ddnormalize_d2_d2(ddadd2_d2_d2_d(x, o0 ? -1 : -2)).x;

  u = o2 ? -156.801412704022726379848862 : (o0 ? +0.2947916772827614196e+2 : +0.7074816000864609279e-7);
  u = mla(u, t, o2 ? +1.120804464289911606838558160000 : (o0 ? +0.1281459691827820109e+3 : +0.4009244333008730443e-6));
  u = mla(u, t, o2 ? +13.39798545514258921833306020000 : (o0 ? +0.2617544025784515043e+3 : +0.1040114641628246946e-5));
  u = mla(u, t, o2 ? -0.116546276599463200848033357000 : (o0 ? +0.3287022855685790432e+3 : +0.1508349150733329167e-5));
  u = mla(u, t, o2 ? -1.391801093265337481495562410000 : (o0 ? +0.2818145867730348186e+3 : +0.1288143074933901020e-5));
  u = mla(u, t, o2 ? +0.015056113040026424412918973400 : (o0 ? +0.1728670414673559605e+3 : +0.4744167749884993937e-6));
  u = mla(u, t, o2 ? +0.179540117061234856098844714000 : (o0 ? +0.7748735764030416817e+2 : -0.6554816306542489902e-7));
  u = mla(u, t, o2 ? -0.002481743600264997730942489280 : (o0 ? +0.2512856643080930752e+2 : -0.3189252471452599844e-6));
  u = mla(u, t, o2 ? -0.029527880945699120504851034100 : (o0 ? +0.5766792106140076868e+1 : +0.1358883821470355377e-6));
  u = mla(u, t, o2 ? +0.000540164767892604515196325186 : (o0 ? +0.7270275473996180571e+0 : -0.4343931277157336040e-6));
  u = mla(u, t, o2 ? +0.006403362833808069794787256200 : (o0 ? +0.8396709124579147809e-1 : +0.9724785897406779555e-6));
  u = mla(u, t, o2 ? -0.000162516262783915816896611252 : (o0 ? -0.8211558669746804595e-1 : -0.2036886057225966011e-5));
  u = mla(u, t, o2 ? -0.001914438498565477526465972390 : (o0 ? +0.6828831828341884458e-1 : +0.4373363141819725815e-5));
  u = mla(u, t, o2 ? +7.20489541602001055898311517e-05 : (o0 ? -0.7712481339961671511e-1 : -0.9439951268304008677e-5));
  u = mla(u, t, o2 ? +0.000839498720672087279971000786 : (o0 ? +0.8337492023017314957e-1 : +0.2050727030376389804e-4));
  u = mla(u, t, o2 ? -5.17179090826059219329394422e-05 : (o0 ? -0.9094964931456242518e-1 : -0.4492620183431184018e-4));
  u = mla(u, t, o2 ? -0.000592166437353693882857342347 : (o0 ? +0.1000996313575929358e+0 : +0.9945751236071875931e-4));
  u = mla(u, t, o2 ? +6.97281375836585777403743539e-05 : (o0 ? -0.1113342861544207724e+0 : -0.2231547599034983196e-3));
  u = mla(u, t, o2 ? +0.000784039221720066627493314301 : (o0 ? +0.1255096673213020875e+0 : +0.5096695247101967622e-3));
  u = mla(u, t, o2 ? -0.000229472093621399176949318732 : (o0 ? -0.1440498967843054368e+0 : -0.1192753911667886971e-2));
  u = mla(u, t, o2 ? -0.002681327160493827160473958490 : (o0 ? +0.1695571770041949811e+0 : +0.2890510330742210310e-2));
  u = mla(u, t, o2 ? +0.003472222222222222222175164840 : (o0 ? -0.2073855510284092762e+0 : -0.7385551028674461858e-2));
  u = mla(u, t, o2 ? +0.083333333333333333335592087900 : (o0 ? +0.2705808084277815939e+0 : +0.2058080842778455335e-1));

  y = ddmul_d2_d2_d2(ddadd2_d2_d2_d(x, -0.5), logk2(x));
  y = ddadd2_d2_d2_d2(y, ddneg_d2_d2(x));
  y = ddadd2_d2_d2_d2(y, dd(0.91893853320467278056, -3.8782941580672414498e-17)); // 0.5*log(2*M_PI)

  z = ddadd2_d2_d2_d(ddmul_d2_d_d (u, t), o0 ? -0.4006856343865314862e+0 : -0.6735230105319810201e-1);
  z = ddadd2_d2_d2_d(ddmul_d2_d2_d(z, t), o0 ? +0.8224670334241132030e+0 : +0.3224670334241132030e+0);
  z = ddadd2_d2_d2_d(ddmul_d2_d2_d(z, t), o0 ? -0.5772156649015328655e+0 : +0.4227843350984671345e+0);
  z = ddmul_d2_d2_d(z, t);

  clc = o2 ? y : z;

  clld = o2 ? ddadd2_d2_d2_d(ddmul_d2_d_d(u, t), 1) : clld;

  y = clln;

  clc = otiny ? dd(83.1776616671934334590333, 3.67103459631568507221878e-15) : // log(2^120)
    (oref ? ddadd2_d2_d2_d2(dd(1.1447298858494001639, 1.026595116270782638e-17), ddneg_d2_d2(clc)) : clc); // log(M_PI)
  clln = otiny ? dd(1, 0) : (oref ? clln : clld);

  if (oref) x = ddmul_d2_d2_d2(clld, sinpik(a - (double)(INT64_C(1) << 28) * (int32_t)(a * (1.0 / (INT64_C(1) << 28)))));

  clld = otiny ? dd(a*((INT64_C(1) << 60)*(double)(INT64_C(1) << 60)), 0) : (oref ? x : y);

  dd2 ret = { clc, dddiv_d2_d2_d2(clln, clld) };

  return ret;
}

EXPORT CONST double xtgamma_u1(double a) {
  dd2 d = gammak(a);
  Sleef_double2 y = ddmul_d2_d2_d2(expk2(d.a), d.b);
  double r = y.x + y.y;
  r = (a == -SLEEF_INFINITY || (a < 0 && xisint(a)) || (xisnumber(a) && a < 0 && xisnan(r))) ? SLEEF_NAN : r;
  r = ((a == SLEEF_INFINITY || xisnumber(a)) && a >= -DBL_MIN && (a == 0 || a > 200 || xisnan(r))) ? mulsign(SLEEF_INFINITY, a) : r;
  return r;
}

EXPORT CONST double xlgamma_u1(double a) {
  dd2 d = gammak(a);
  Sleef_double2 y = ddadd2_d2_d2_d2(d.a, logk2(ddabs_d2_d2(d.b)));
  double r = y.x + y.y;
  r = (xisinf(a) || (a <= 0 && xisint(a)) || (xisnumber(a) && xisnan(r))) ? SLEEF_INFINITY : r;
  return r;
}

static INLINE CONST Sleef_double2 ddmla(double x, Sleef_double2 y, Sleef_double2 z) {
  return ddadd2_d2_d2_d2(z, ddmul_d2_d2_d(y, x));
}
static INLINE CONST Sleef_double2 poly2dd_b(double x, Sleef_double2 c1, Sleef_double2 c0) { return ddmla(x, c1, c0); }
static INLINE CONST Sleef_double2 poly2dd(double x, double c1, Sleef_double2 c0) { return ddmla(x, dd(c1, 0), c0); }
static INLINE CONST Sleef_double2 poly4dd(double x, double c3, Sleef_double2 c2, Sleef_double2 c1, Sleef_double2 c0) {
  return ddmla(x*x, poly2dd(x, c3, c2), poly2dd_b(x, c1, c0));
}

EXPORT CONST double xerf_u1(double a) {
  double t, x = fabsk(a);
  Sleef_double2 t2;
  double x2 = x * x, x4 = x2 * x2, x8 = x4 * x4, x16 = x8 * x8;

  if (x < 2.5) {
    // Abramowitz and Stegun
    t = POLY21(x, x2, x4, x8, x16,
               -0.2083271002525222097e-14,
               +0.7151909970790897009e-13,
               -0.1162238220110999364e-11,
               +0.1186474230821585259e-10,
               -0.8499973178354613440e-10,
               +0.4507647462598841629e-9,
               -0.1808044474288848915e-8,
               +0.5435081826716212389e-8,
               -0.1143939895758628484e-7,
               +0.1215442362680889243e-7,
               +0.1669878756181250355e-7,
               -0.9808074602255194288e-7,
               +0.1389000557865837204e-6,
               +0.2945514529987331866e-6,
               -0.1842918273003998283e-5,
               +0.3417987836115362136e-5,
               +0.3860236356493129101e-5,
               -0.3309403072749947546e-4,
               +0.1060862922597579532e-3,
               +0.2323253155213076174e-3,
               +0.1490149719145544729e-3);
    t2 = poly4dd(x, t,
                 dd(0.0092877958392275604405, 7.9287559463961107493e-19),
                 dd(0.042275531758784692937, 1.3785226620501016138e-19),
                 dd(0.07052369794346953491, 9.5846628070792092842e-19));
    t2 = ddadd_d2_d_d2(1, ddmul_d2_d2_d(t2, x));
    t2 = ddsqu_d2_d2(t2);
    t2 = ddsqu_d2_d2(t2);
    t2 = ddsqu_d2_d2(t2);
    t2 = ddsqu_d2_d2(t2);
    t2 = ddrec_d2_d2(t2);
  } else if (x > 6.0) {
    t2 = dd(0, 0);
  } else {
    t = POLY21(x, x2, x4, x8, x16,
               -0.4024015130752621932e-18,
               +0.3847193332817048172e-16,
               -0.1749316241455644088e-14,
               +0.5029618322872872715e-13,
               -0.1025221466851463164e-11,
               +0.1573695559331945583e-10,
               -0.1884658558040203709e-9,
               +0.1798167853032159309e-8,
               -0.1380745342355033142e-7,
               +0.8525705726469103499e-7,
               -0.4160448058101303405e-6,
               +0.1517272660008588485e-5,
               -0.3341634127317201697e-5,
               -0.2515023395879724513e-5,
               +0.6539731269664907554e-4,
               -0.3551065097428388658e-3,
               +0.1210736097958368864e-2,
               -0.2605566912579998680e-2,
               +0.1252823202436093193e-2,
               +0.1820191395263313222e-1,
               -0.1021557155453465954e+0);
    t2 = poly4dd(x, t,
                 dd(-0.63691044383641748361, -2.4249477526539431839e-17),
                 dd(-1.1282926061803961737, -6.2970338860410996505e-17),
                 dd(-1.2261313785184804967e-05, -5.5329707514490107044e-22));
    t2 = dd(expk(t2), 0);
  }

  t2 = ddadd2_d2_d2_d(t2, -1);

  if (x < 1e-8) t2 = dd(-1.12837916709551262756245475959 * x, 0);
  return mulsign(a == 0 ? 0 : (xisinf(a) ? 1 : (-t2.x - t2.y)), a);
}

EXPORT CONST double xerfc_u15(double a) {
  double s = a, r = 0, t;
  Sleef_double2 u, d, x;
  a = fabsk(a);
  int o0 = a < 1.0, o1 = a < 2.2, o2 = a < 4.2, o3 = a < 27.3;
  u = o0 ? ddmul_d2_d_d(a, a) : o1 ? dd(a, 0) : dddiv_d2_d2_d2(dd(1, 0), dd(a, 0));

  t = o0 ? +0.6801072401395386139e-20 : o1 ? +0.3438010341362585303e-12 : o2 ? -0.5757819536420710449e+2 : +0.2334249729638701319e+5;
  t = mla(t, u.x, o0 ? -0.2161766247570055669e-18 : o1 ? -0.1237021188160598264e-10 : o2 ? +0.4669289654498104483e+3 : -0.4695661044933107769e+5);
  t = mla(t, u.x, o0 ? +0.4695919173301595670e-17 : o1 ? +0.2117985839877627852e-09 : o2 ? -0.1796329879461355858e+4 : +0.3173403108748643353e+5);
  t = mla(t, u.x, o0 ? -0.9049140419888007122e-16 : o1 ? -0.2290560929177369506e-08 : o2 ? +0.4355892193699575728e+4 : +0.3242982786959573787e+4);
  t = mla(t, u.x, o0 ? +0.1634018903557410728e-14 : o1 ? +0.1748931621698149538e-07 : o2 ? -0.7456258884965764992e+4 : -0.2014717999760347811e+5);
  t = mla(t, u.x, o0 ? -0.2783485786333451745e-13 : o1 ? -0.9956602606623249195e-07 : o2 ? +0.9553977358167021521e+4 : +0.1554006970967118286e+5);
  t = mla(t, u.x, o0 ? +0.4463221276786415752e-12 : o1 ? +0.4330010240640327080e-06 : o2 ? -0.9470019905444229153e+4 : -0.6150874190563554293e+4);
  t = mla(t, u.x, o0 ? -0.6711366622850136563e-11 : o1 ? -0.1435050600991763331e-05 : o2 ? +0.7387344321849855078e+4 : +0.1240047765634815732e+4);
  t = mla(t, u.x, o0 ? +0.9422759050232662223e-10 : o1 ? +0.3460139479650695662e-05 : o2 ? -0.4557713054166382790e+4 : -0.8210325475752699731e+2);
  t = mla(t, u.x, o0 ? -0.1229055530100229098e-08 : o1 ? -0.4988908180632898173e-05 : o2 ? +0.2207866967354055305e+4 : +0.3242443880839930870e+2);
  t = mla(t, u.x, o0 ? +0.1480719281585086512e-07 : o1 ? -0.1308775976326352012e-05 : o2 ? -0.8217975658621754746e+3 : -0.2923418863833160586e+2);
  t = mla(t, u.x, o0 ? -0.1636584469123399803e-06 : o1 ? +0.2825086540850310103e-04 : o2 ? +0.2268659483507917400e+3 : +0.3457461732814383071e+0);
  t = mla(t, u.x, o0 ? +0.1646211436588923575e-05 : o1 ? -0.6393913713069986071e-04 : o2 ? -0.4633361260318560682e+2 : +0.5489730155952392998e+1);
  t = mla(t, u.x, o0 ? -0.1492565035840623511e-04 : o1 ? -0.2566436514695078926e-04 : o2 ? +0.9557380123733945965e+1 : +0.1559934132251294134e-2);
  t = mla(t, u.x, o0 ? +0.1205533298178967851e-03 : o1 ? +0.5895792375659440364e-03 : o2 ? -0.2958429331939661289e+1 : -0.1541741566831520638e+1);
  t = mla(t, u.x, o0 ? -0.8548327023450850081e-03 : o1 ? -0.1695715579163588598e-02 : o2 ? +0.1670329508092765480e+0 : +0.2823152230558364186e-5);
  t = mla(t, u.x, o0 ? +0.5223977625442187932e-02 : o1 ? +0.2089116434918055149e-03 : o2 ? +0.6096615680115419211e+0 : +0.6249999184195342838e+0);
  t = mla(t, u.x, o0 ? -0.2686617064513125222e-01 : o1 ? +0.1912855949584917753e-01 : o2 ? +0.1059212443193543585e-2 : +0.1741749416408701288e-8);

  d = ddmul_d2_d2_d(u, t);
  d = ddadd2_d2_d2_d2(d, o0 ? dd(0.11283791670955126141, -4.0175691625932118483e-18) :
                      o1 ? dd(-0.10277263343147646779, -6.2338714083404900225e-18) :
                      o2 ? dd(-0.50005180473999022439, 2.6362140569041995803e-17) :
                      dd(-0.5000000000258444377, -4.0074044712386992281e-17));
  d = ddmul_d2_d2_d2(d, u);
  d = ddadd2_d2_d2_d2(d, o0 ? dd(-0.37612638903183753802, 1.3391897206042552387e-17) :
                      o1 ? dd(-0.63661976742916359662, 7.6321019159085724662e-18) :
                      o2 ? dd(1.601106273924963368e-06, 1.1974001857764476775e-23) :
                      dd(2.3761973137523364792e-13, -1.1670076950531026582e-29));
  d = ddmul_d2_d2_d2(d, u);
  d = ddadd2_d2_d2_d2(d, o0 ? dd(1.1283791670955125586, 1.5335459613165822674e-17) :
                      o1 ? dd(-1.1283791674717296161, 8.0896847755965377194e-17) :
                      o2 ? dd(-0.57236496645145429341, 3.0704553245872027258e-17) :
                      dd(-0.57236494292470108114, -2.3984352208056898003e-17));

  x = ddmul_d2_d2_d(o1 ? d : dd(-a, 0), a);
  x = o1 ? x : ddadd2_d2_d2_d2(x, d);
  x = o0 ? ddsub_d2_d2_d2(dd(1, 0), x) : expk2(x);
  x = o1 ? x : ddmul_d2_d2_d2(x, u);

  r = o3 ? (x.x + x.y) : 0;
  if (s < 0) r = 2 - r;
  r = xisnan(s) ? SLEEF_NAN : r;
  return r;
}

#ifdef ENABLE_MAIN
// gcc -w -DENABLE_MAIN -I../common sleefdp.c rempitab.c -lm
#include <stdlib.h>
int main(int argc, char **argv) {
  double d1 = atof(argv[1]);
  printf("arg1 = %.20g\n", d1);
  //int i1 = atoi(argv[1]);
  //double d2 = atof(argv[2]);
  //printf("arg2 = %.20g\n", d2);
  //printf("%d\n", (int)d2);
#if 0
  double d3 = atof(argv[3]);
  printf("arg3 = %.20g\n", d3);
#endif
  //printf("%g\n", pow2i(i1));
  //int exp = xexpfrexp(d1);
  //double r = xnextafter(d1, d2);
  //double r = xfma(d1, d2, d3);
  printf("test = %.20g\n", xcos_u1(d1));
  //printf("test = %.20g\n", xlog(d1));
  //r = nextafter(d1, d2);
  printf("corr = %.20g\n", cos(d1));
  //printf("%.20g %.20g\n", xround(d1), xrint(d1));
  //Sleef_double2 r = xsincospi_u35(d);
  //printf("%g, %g\n", (double)r.x, (double)r.y);
}
#endif
