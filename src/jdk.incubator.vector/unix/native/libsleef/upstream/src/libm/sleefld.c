//   Copyright Naoki Shibata and contributors 2010 - 2024.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

// Always use -ffp-contract=off option to compile SLEEF.

#include <stdio.h>

#include <assert.h>
#include <stdint.h>
#include <limits.h>

#include "misc.h"

#ifdef DORENAME
#include "rename.h"
#endif

#if (defined(_MSC_VER))
#pragma fp_contract (off)
#endif

static INLINE CONST long double mlal(long double x, long double y, long double z) { return x * y + z; }
static INLINE CONST long double xrintl(long double x) { return x < 0 ? (int)(x - 0.5) : (int)(x + 0.5); }
static INLINE CONST int64_t xceill(long double x) { return (int64_t)x + (x < 0 ? 0 : 1); }
static INLINE CONST long double xtruncl(long double x) { return (long double)(int)x; }

static INLINE CONST int xisnanl(long double x) { return x != x; }
static INLINE CONST int xisinfl(long double x) { return x == SLEEF_INFINITYl || x == -SLEEF_INFINITYl; }
static INLINE CONST int xisminfl(long double x) { return x == -SLEEF_INFINITYl; }
static INLINE CONST int xispinfl(long double x) { return x == SLEEF_INFINITYl; }

static INLINE CONST long double xfabsl(long double x) { return x >= 0 ? x : -x; }

//

#ifndef NDEBUG
static int checkfp(long double x) {
  if (xisinfl(x) || xisnanl(x)) return 1;
  return 0;
}
#endif

static INLINE CONST long double upperl(long double d) {
  union {
    long double ld;
    uint32_t u[4];
  } cnv;

  cnv.ld = d;
  cnv.u[0] = 0;
  return cnv.ld;
}

static INLINE CONST Sleef_longdouble2 dl(long double h, long double l) {
  Sleef_longdouble2 ret;
  ret.x = h; ret.y = l;
  return ret;
}

static INLINE CONST Sleef_longdouble2 dlnormalize_l2_l2(Sleef_longdouble2 t) {
  Sleef_longdouble2 s;

  s.x = t.x + t.y;
  s.y = t.x - s.x + t.y;

  return s;
}

static INLINE CONST Sleef_longdouble2 dlscale_l2_l2_l(Sleef_longdouble2 d, long double s) {
  Sleef_longdouble2 r;

  r.x = d.x * s;
  r.y = d.y * s;

  return r;
}

static INLINE CONST Sleef_longdouble2 dlneg_l2_l2(Sleef_longdouble2 d) {
  Sleef_longdouble2 r;

  r.x = -d.x;
  r.y = -d.y;

  return r;
}

static INLINE CONST Sleef_longdouble2 dladd_l2_l_l(long double x, long double y) {
  // |x| >= |y|

  Sleef_longdouble2 r;

#ifndef NDEBUG
  if (!(checkfp(x) || checkfp(y) || xfabsl(x) >= xfabsl(y))) {
    fprintf(stderr, "[dladd_l2_l_l : %Lg, %Lg]\n", x, y);
    fflush(stderr);
  }
#endif

  r.x = x + y;
  r.y = x - r.x + y;

  return r;
}

static INLINE CONST Sleef_longdouble2 dladd2_l2_l_l(long double x, long double y) {
  Sleef_longdouble2 r;

  r.x = x + y;
  long double v = r.x - x;
  r.y = (x - (r.x - v)) + (y - v);

  return r;
}

static INLINE CONST Sleef_longdouble2 dladd_l2_l2_l(Sleef_longdouble2 x, long double y) {
  // |x| >= |y|

  Sleef_longdouble2 r;

#ifndef NDEBUG
  if (!(checkfp(x.x) || checkfp(y) || xfabsl(x.x) >= xfabsl(y))) {
    fprintf(stderr, "[dladd_l2_l2_l : %Lg %Lg]\n", x.x, y);
    fflush(stderr);
  }
#endif

  r.x = x.x + y;
  r.y = x.x - r.x + y + x.y;

  return r;
}

static INLINE CONST Sleef_longdouble2 dladd2_l2_l2_l(Sleef_longdouble2 x, long double y) {
  // |x| >= |y|

  Sleef_longdouble2 r;

  r.x  = x.x + y;
  long double v = r.x - x.x;
  r.y = (x.x - (r.x - v)) + (y - v);
  r.y += x.y;

  return r;
}

static INLINE CONST Sleef_longdouble2 dladd_l2_l_l2(long double x, Sleef_longdouble2 y) {
  // |x| >= |y|

  Sleef_longdouble2 r;

#ifndef NDEBUG
  if (!(checkfp(x) || checkfp(y.x) || xfabsl(x) >= xfabsl(y.x))) {
    fprintf(stderr, "[dladd_l2_l_l2 : %Lg %Lg]\n", x, y.x);
    fflush(stderr);
  }
#endif

  r.x = x + y.x;
  r.y = x - r.x + y.x + y.y;

  return r;
}

static INLINE CONST Sleef_longdouble2 dladd2_l2_l_l2(long double x, Sleef_longdouble2 y) {
  Sleef_longdouble2 r;

  r.x  = x + y.x;
  long double v = r.x - x;
  r.y = (x - (r.x - v)) + (y.x - v) + y.y;

  return r;
}

static INLINE CONST Sleef_longdouble2 dladd_l2_l2_l2(Sleef_longdouble2 x, Sleef_longdouble2 y) {
  // |x| >= |y|

  Sleef_longdouble2 r;

#ifndef NDEBUG
  if (!(checkfp(x.x) || checkfp(y.x) || xfabsl(x.x) >= xfabsl(y.x))) {
    fprintf(stderr, "[dladd_l2_l2_l2 : %Lg %Lg]\n", x.x, y.x);
    fflush(stderr);
  }
#endif

  r.x = x.x + y.x;
  r.y = x.x - r.x + y.x + x.y + y.y;

  return r;
}

static INLINE CONST Sleef_longdouble2 dladd2_l2_l2_l2(Sleef_longdouble2 x, Sleef_longdouble2 y) {
  Sleef_longdouble2 r;

  r.x  = x.x + y.x;
  long double v = r.x - x.x;
  r.y = (x.x - (r.x - v)) + (y.x - v);
  r.y += x.y + y.y;

  return r;
}

static INLINE CONST Sleef_longdouble2 dlsub_l2_l2_l2(Sleef_longdouble2 x, Sleef_longdouble2 y) {
  // |x| >= |y|

  Sleef_longdouble2 r;

#ifndef NDEBUG
  if (!(checkfp(x.x) || checkfp(y.x) || xfabsl(x.x) >= xfabsl(y.x))) {
    fprintf(stderr, "[dlsub_l2_l2_l2 : %Lg %Lg]\n", x.x, y.x);
    fflush(stderr);
  }
#endif

  r.x = x.x - y.x;
  r.y = x.x - r.x - y.x + x.y - y.y;

  return r;
}

static INLINE CONST Sleef_longdouble2 dldiv_l2_l2_l2(Sleef_longdouble2 n, Sleef_longdouble2 d) {
  long double t = 1.0 / d.x;
  long double dh  = upperl(d.x), dl  = d.x - dh;
  long double th  = upperl(t  ), tl  = t   - th;
  long double nhh = upperl(n.x), nhl = n.x - nhh;

  Sleef_longdouble2 q;

  q.x = n.x * t;

  long double u = -q.x + nhh * th + nhh * tl + nhl * th + nhl * tl +
    q.x * (1 - dh * th - dh * tl - dl * th - dl * tl);

  q.y = t * (n.y - q.x * d.y) + u;

  return q;
}

static INLINE CONST Sleef_longdouble2 dlmul_l2_l_l(long double x, long double y) {
  long double xh = upperl(x), xl = x - xh;
  long double yh = upperl(y), yl = y - yh;
  Sleef_longdouble2 r;

  r.x = x * y;
  r.y = xh * yh - r.x + xl * yh + xh * yl + xl * yl;

  return r;
}

static INLINE CONST Sleef_longdouble2 dlmul_l2_l2_l(Sleef_longdouble2 x, long double y) {
  long double xh = upperl(x.x), xl = x.x - xh;
  long double yh = upperl(y  ), yl = y   - yh;
  Sleef_longdouble2 r;

  r.x = x.x * y;
  r.y = xh * yh - r.x + xl * yh + xh * yl + xl * yl + x.y * y;

  return r;
}

static INLINE CONST Sleef_longdouble2 dlmul_l2_l2_l2(Sleef_longdouble2 x, Sleef_longdouble2 y) {
  long double xh = upperl(x.x), xl = x.x - xh;
  long double yh = upperl(y.x), yl = y.x - yh;
  Sleef_longdouble2 r;

  r.x = x.x * y.x;
  r.y = xh * yh - r.x + xl * yh + xh * yl + xl * yl + x.x * y.y + x.y * y.x;

  return r;
}

static INLINE CONST Sleef_longdouble2 dlsqu_l2_l2(Sleef_longdouble2 x) {
  long double xh = upperl(x.x), xl = x.x - xh;
  Sleef_longdouble2 r;

  r.x = x.x * x.x;
  r.y = xh * xh - r.x + (xh + xh) * xl + xl * xl + x.x * (x.y + x.y);

  return r;
}

static INLINE CONST Sleef_longdouble2 dlrec_l2_l(long double d) {
  long double t = 1.0 / d;
  long double dh = upperl(d), dl = d - dh;
  long double th = upperl(t), tl = t - th;
  Sleef_longdouble2 q;

  q.x = t;
  q.y = t * (1 - dh * th - dh * tl - dl * th - dl * tl);

  return q;
}

static INLINE CONST Sleef_longdouble2 dlrec_l2_l2(Sleef_longdouble2 d) {
  long double t = 1.0 / d.x;
  long double dh = upperl(d.x), dl = d.x - dh;
  long double th = upperl(t  ), tl = t   - th;
  Sleef_longdouble2 q;

  q.x = t;
  q.y = t * (1 - dh * th - dh * tl - dl * th - dl * tl - d.y * t);

  return q;
}

/*
static INLINE CONST Sleef_longdouble2 dlsqrt_l2_l2(Sleef_longdouble2 d) {
  long double t = sqrt(d.x + d.y);
  return dlscale_l2_l2_l(dlmul_l2_l2_l2(dladd2_l2_l2_l2(d, dlmul_l2_l_l(t, t)), dlrec_l2_l(t)), 0.5);
}
*/

//

EXPORT CONST Sleef_longdouble2 xsincospil_u05(long double d) {
  long double u, s, t;
  Sleef_longdouble2 r, x, s2;

  u = d * 4;
  int64_t q = xceill(u) & ~(int64_t)1;

  s = u - (long double)q;
  t = s;
  s = s * s;
  s2 = dlmul_l2_l_l(t, t);

  //

  u = 4.59265607313529833157632e-17L;
  u = mlal(u, s, -2.04096140520547829627419e-14L);
  u = mlal(u, s, 6.94845264320316515640316e-12L);
  u = mlal(u, s, -1.75724767308629210422023e-09L);
  u = mlal(u, s, 3.13361689037693212744991e-07L);
  u = mlal(u, s, -3.65762041821772284521155e-05L);
  u = mlal(u, s, 0.00249039457019272015784594L);
  x = dladd2_l2_l_l2(u * s, dl(-0.0807455121882807817044873L, -2.40179063154839769223037e-21L));
  x = dladd2_l2_l2_l2(dlmul_l2_l2_l2(s2, x), dl(0.785398163397448309628202L, -1.25420305812534448752181e-20L));

  x = dlmul_l2_l2_l(x, t);
  r.x = x.x + x.y;

  //

  u = -2.00423964577657539380734e-18L;
  u = mlal(u, s, 1.00185574457758689324113e-15L);
  u = mlal(u, s, -3.89807283423502620989528e-13L);
  u = mlal(u, s, 1.15011591257563133685341e-10L);
  u = mlal(u, s, -2.461136950493305818105e-08L);
  u = mlal(u, s, 3.59086044859150791782134e-06L);
  u = mlal(u, s, -0.00032599188692739001335938L);
  x = dladd2_l2_l_l2(u * s, dl(0.0158543442438155008529635L, -6.97556143018517384674258e-22L));
  x = dladd2_l2_l2_l2(dlmul_l2_l2_l2(s2, x), dl(-0.308425137534042456829379L, -9.19882299434302978226668e-21L));

  x = dladd2_l2_l2_l(dlmul_l2_l2_l2(x, s2), 1);
  r.y = x.x + x.y;

  //

  if ((q & 2) != 0) { s = r.y; r.y = r.x; r.x = s; }
  if ((q & 4) != 0) { r.x = -r.x; }
  if (((q+2) & 4) != 0) { r.y = -r.y; }

  if (xisinfl(d)) { r.x = r.y = SLEEF_NAN; }
  if (!xisinfl(d) && xfabsl(d) > TRIGRANGEMAX3) { r.x = r.y = 0; }

  return r;
}

EXPORT CONST Sleef_longdouble2 xsincospil_u35(long double d) {
  long double u, s, t;
  Sleef_longdouble2 r;

  u = d * 4;
  int64_t q = xceill(u) & ~(int64_t)1;

  s = u - (long double)q;
  t = s;
  s = s * s;

  //

  u = -0.2023275819380976135024e-13L;
  u = mlal(u, s, +0.6948176964255957574946e-11L);
  u = mlal(u, s, -0.1757247450021535880723e-8L);
  u = mlal(u, s, +0.3133616889379195970541e-6L);
  u = mlal(u, s, -0.3657620418215300856408e-4L);
  u = mlal(u, s, +0.2490394570192717262476e-2L);
  u = mlal(u, s, -0.8074551218828078160284e-1L);
  u = mlal(u, s, +0.7853981633974483096282e+0L);

  r.x = u * t;

  //

  u = +0.9933418221428971922705e-15L;
  u = mlal(u, s, -0.3897923064055824005357e-12L);
  u = mlal(u, s, +0.1150115771521792692066e-9L);
  u = mlal(u, s, -0.2461136949725905367314e-7L);
  u = mlal(u, s, +0.3590860448589084195081e-5L);
  u = mlal(u, s, -0.3259918869273895914840e-3L);
  u = mlal(u, s, +0.1585434424381550079706e-1L);
  u = mlal(u, s, -0.3084251375340424568294e+0L);
  u = mlal(u, s, 1.0L);

  r.y = u;

  //

  if ((q & 2) != 0) { s = r.y; r.y = r.x; r.x = s; }
  if ((q & 4) != 0) { r.x = -r.x; }
  if (((q+2) & 4) != 0) { r.y = -r.y; }

  if (xisinfl(d)) { r.x = r.y = SLEEF_NAN; }
  if (!xisinfl(d) && xfabsl(d) > TRIGRANGEMAX3) { r.x = r.y = 0; }

  return r;
}
