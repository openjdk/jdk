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
#define SQRTF sqrtf
#else
#define SQRTF __builtin_sqrtf
#endif

#include "misc.h"

extern const float Sleef_rempitabsp[];

#ifdef DORENAME
#include "rename.h"
#endif

#if defined(_MSC_VER) && !defined (__clang__)
#pragma fp_contract (off)
#else
#pragma STDC FP_CONTRACT OFF
#endif

#define MLA mlaf
#define C2V(x) (x)
#include "estrin.h"

static INLINE CONST int32_t floatToRawIntBits(float d) {
  int32_t ret;
  memcpy(&ret, &d, sizeof(ret));
  return ret;
}

static INLINE CONST float intBitsToFloat(int32_t i) {
  float ret;
  memcpy(&ret, &i, sizeof(ret));
  return ret;
}

static INLINE CONST float fabsfk(float x) {
  return intBitsToFloat(0x7fffffffL & floatToRawIntBits(x));
}

static INLINE CONST float mulsignf(float x, float y) {
  return intBitsToFloat(floatToRawIntBits(x) ^ (floatToRawIntBits(y) & (1 << 31)));
}

static INLINE CONST float copysignfk(float x, float y) {
  return intBitsToFloat((floatToRawIntBits(x) & ~(1 << 31)) ^ (floatToRawIntBits(y) & (1 << 31)));
}

static INLINE CONST float signf(float d) { return mulsignf(1, d); }
static INLINE CONST float mlaf(float x, float y, float z) { return x * y + z; }
static INLINE CONST float rintfk(float x) { return x < 0 ? (int)(x - 0.5f) : (int)(x + 0.5f); }
static INLINE CONST int ceilfk(float x) { return (int)x + (x < 0 ? 0 : 1); }
static INLINE CONST float fminfk(float x, float y) { return x < y ? x : y; }
static INLINE CONST float fmaxfk(float x, float y) { return x > y ? x : y; }
static INLINE CONST int xisintf(float x) { return (x == (int)x); }

static INLINE CONST int xsignbitf(double d) { return (floatToRawIntBits(d) & floatToRawIntBits(-0.0)) == floatToRawIntBits(-0.0); }
static INLINE CONST int xisnanf(float x) { return x != x; }
static INLINE CONST int xisinff(float x) { return x == SLEEF_INFINITYf || x == -SLEEF_INFINITYf; }
static INLINE CONST int xisminff(float x) { return x == -SLEEF_INFINITYf; }
static INLINE CONST int xispinff(float x) { return x == SLEEF_INFINITYf; }
static INLINE CONST int xisnegzerof(float x) { return floatToRawIntBits(x) == floatToRawIntBits(-0.0); }
static INLINE CONST int xisnumberf(float x) { return !xisinff(x) && !xisnanf(x); }

static INLINE CONST int ilogbkf(float d) {
  int m = d < 5.421010862427522E-20f;
  d = m ? 1.8446744073709552E19f * d : d;
  int q = (floatToRawIntBits(d) >> 23) & 0xff;
  q = m ? q - (64 + 0x7f) : q - 0x7f;
  return q;
}

// vilogb2kf is similar to ilogbkf, but the argument has to be a
// normalized FP value.
static INLINE CONST int ilogb2kf(float d) {
  return ((floatToRawIntBits(d) >> 23) & 0xff) - 0x7f;
}

EXPORT CONST int xilogbf(float d) {
  int e = ilogbkf(fabsfk(d));
  e = d == 0.0f  ? SLEEF_FP_ILOGB0 : e;
  e = xisnanf(d) ? SLEEF_FP_ILOGBNAN : e;
  e = xisinff(d) ? INT_MAX : e;
  return e;
}

static INLINE CONST float pow2if(int q) {
  return intBitsToFloat(((int32_t)(q + 0x7f)) << 23);
}

static INLINE CONST float ldexpkf(float x, int q) {
  float u;
  int m;
  m = q >> 31;
  m = (((m + q) >> 6) - m) << 4;
  q = q - (m << 2);
  m += 127;
  m = m <   0 ?   0 : m;
  m = m > 255 ? 255 : m;
  u = intBitsToFloat(((int32_t)m) << 23);
  x = x * u * u * u * u;
  u = intBitsToFloat(((int32_t)(q + 0x7f)) << 23);
  return x * u;
}

static INLINE CONST float ldexp2kf(float d, int e) { // faster than ldexpkf, short reach
  return d * pow2if(e >> 1) * pow2if(e - (e >> 1));
}

static INLINE CONST float ldexp3kf(float d, int e) { // very fast, no denormal
  return intBitsToFloat(floatToRawIntBits(d) + (e << 23));
}

//

#ifndef NDEBUG
static int checkfp(float x) {
  if (xisinff(x) || xisnanf(x)) return 1;
  return 0;
}
#endif

static INLINE CONST float upperf(float d) {
  return intBitsToFloat(floatToRawIntBits(d) & 0xfffff000);
}

static INLINE CONST Sleef_float2 df(float h, float l) {
  Sleef_float2 ret;
  ret.x = h; ret.y = l;
  return ret;
}

static INLINE CONST Sleef_float2 dfx(double d) {
  Sleef_float2 ret;
  ret.x = d; ret.y = d - ret.x;
  return ret;
}

static INLINE CONST Sleef_float2 dfnormalize_f2_f2(Sleef_float2 t) {
  Sleef_float2 s;

  s.x = t.x + t.y;
  s.y = t.x - s.x + t.y;

  return s;
}

static INLINE CONST Sleef_float2 dfscale_f2_f2_f(Sleef_float2 d, float s) {
  Sleef_float2 r;

  r.x = d.x * s;
  r.y = d.y * s;

  return r;
}

static INLINE CONST Sleef_float2 dfneg_f2_f2(Sleef_float2 d) {
  Sleef_float2 r;

  r.x = -d.x;
  r.y = -d.y;

  return r;
}

static INLINE CONST Sleef_float2 dfabs_f2_f2(Sleef_float2 x) {
  return df(x.x < 0 ? -x.x : x.x, x.x < 0 ? -x.y : x.y);
}

static INLINE CONST Sleef_float2 dfadd_f2_f_f(float x, float y) {
  // |x| >= |y|

  Sleef_float2 r;

#ifndef NDEBUG
  if (!(checkfp(x) || checkfp(y) || fabsfk(x) >= fabsfk(y))) fprintf(stderr, "[dfadd_f2_f_f : %g, %g]", x, y);
#endif

  r.x = x + y;
  r.y = x - r.x + y;

  return r;
}

static INLINE CONST Sleef_float2 dfadd2_f2_f_f(float x, float y) {
  Sleef_float2 r;

  r.x = x + y;
  float v = r.x - x;
  r.y = (x - (r.x - v)) + (y - v);

  return r;
}

static INLINE CONST Sleef_float2 dfadd_f2_f2_f(Sleef_float2 x, float y) {
  // |x| >= |y|

  Sleef_float2 r;

#ifndef NDEBUG
  if (!(checkfp(x.x) || checkfp(y) || fabsfk(x.x) >= fabsfk(y))) fprintf(stderr, "[dfadd_f2_f2_f : %g %g]", x.x, y);
#endif

  r.x = x.x + y;
  r.y = x.x - r.x + y + x.y;

  return r;
}

static INLINE CONST Sleef_float2 dfadd_f2_f_f2(float x, Sleef_float2 y) {
  // |x| >= |y|

  Sleef_float2 r;

#ifndef NDEBUG
  if (!(checkfp(x) || checkfp(y.x) || fabsfk(x) >= fabsfk(y.x))) {
    fprintf(stderr, "[dfadd_f2_f_f2 : %g %g]\n", x, y.x);
    fflush(stderr);
  }
#endif

  r.x = x + y.x;
  r.y = x - r.x + y.x + y.y;

  return r;
}

static INLINE CONST Sleef_float2 dfadd2_f2_f2_f(Sleef_float2 x, float y) {
  // |x| >= |y|

  Sleef_float2 r;

  r.x  = x.x + y;
  float v = r.x - x.x;
  r.y = (x.x - (r.x - v)) + (y - v);
  r.y += x.y;

  return r;
}

static INLINE CONST Sleef_float2 dfadd2_f2_f_f2(float x, Sleef_float2 y) {
  Sleef_float2 r;

  r.x  = x + y.x;
  float v = r.x - x;
  r.y = (x - (r.x - v)) + (y.x - v) + y.y;

  return r;
}

static INLINE CONST Sleef_float2 dfadd_f2_f2_f2(Sleef_float2 x, Sleef_float2 y) {
  // |x| >= |y|

  Sleef_float2 r;

#ifndef NDEBUG
  if (!(checkfp(x.x) || checkfp(y.x) || fabsfk(x.x) >= fabsfk(y.x))) fprintf(stderr, "[dfadd_f2_f2_f2 : %g %g]", x.x, y.x);
#endif

  r.x = x.x + y.x;
  r.y = x.x - r.x + y.x + x.y + y.y;

  return r;
}

static INLINE CONST Sleef_float2 dfadd2_f2_f2_f2(Sleef_float2 x, Sleef_float2 y) {
  Sleef_float2 r;

  r.x  = x.x + y.x;
  float v = r.x - x.x;
  r.y = (x.x - (r.x - v)) + (y.x - v);
  r.y += x.y + y.y;

  return r;
}

static INLINE CONST Sleef_float2 dfsub_f2_f2_f2(Sleef_float2 x, Sleef_float2 y) {
  // |x| >= |y|

  Sleef_float2 r;

#ifndef NDEBUG
  if (!(checkfp(x.x) || checkfp(y.x) || fabsfk(x.x) >= fabsfk(y.x))) fprintf(stderr, "[dfsub_f2_f2_f2 : %g %g]", x.x, y.x);
#endif

  r.x = x.x - y.x;
  r.y = x.x - r.x - y.x + x.y - y.y;

  return r;
}

static INLINE CONST Sleef_float2 dfdiv_f2_f2_f2(Sleef_float2 n, Sleef_float2 d) {
  float t = 1.0f / d.x;
  float dh  = upperf(d.x), dl  = d.x - dh;
  float th  = upperf(t  ), tl  = t   - th;
  float nhh = upperf(n.x), nhl = n.x - nhh;

  Sleef_float2 q;

  q.x = n.x * t;

  float u = -q.x + nhh * th + nhh * tl + nhl * th + nhl * tl +
    q.x * (1 - dh * th - dh * tl - dl * th - dl * tl);

  q.y = t * (n.y - q.x * d.y) + u;

  return q;
}

static INLINE CONST Sleef_float2 dfmul_f2_f_f(float x, float y) {
  float xh = upperf(x), xl = x - xh;
  float yh = upperf(y), yl = y - yh;
  Sleef_float2 r;

  r.x = x * y;
  r.y = xh * yh - r.x + xl * yh + xh * yl + xl * yl;

  return r;
}

static INLINE CONST Sleef_float2 dfmul_f2_f2_f(Sleef_float2 x, float y) {
  float xh = upperf(x.x), xl = x.x - xh;
  float yh = upperf(y  ), yl = y   - yh;
  Sleef_float2 r;

  r.x = x.x * y;
  r.y = xh * yh - r.x + xl * yh + xh * yl + xl * yl + x.y * y;

  return r;
}

static INLINE CONST Sleef_float2 dfmul_f2_f2_f2(Sleef_float2 x, Sleef_float2 y) {
  float xh = upperf(x.x), xl = x.x - xh;
  float yh = upperf(y.x), yl = y.x - yh;
  Sleef_float2 r;

  r.x = x.x * y.x;
  r.y = xh * yh - r.x + xl * yh + xh * yl + xl * yl + x.x * y.y + x.y * y.x;

  return r;
}

static INLINE CONST float dfmul_f_f2_f2(Sleef_float2 x, Sleef_float2 y) {
  float xh = upperf(x.x), xl = x.x - xh;
  float yh = upperf(y.x), yl = y.x - yh;

  return x.y * yh + xh * y.y + xl * yl + xh * yl + xl * yh + xh * yh;
}

static INLINE CONST Sleef_float2 dfsqu_f2_f2(Sleef_float2 x) {
  float xh = upperf(x.x), xl = x.x - xh;
  Sleef_float2 r;

  r.x = x.x * x.x;
  r.y = xh * xh - r.x + (xh + xh) * xl + xl * xl + x.x * (x.y + x.y);

  return r;
}

static INLINE CONST float dfsqu_f_f2(Sleef_float2 x) {
  float xh = upperf(x.x), xl = x.x - xh;

  return xh * x.y + xh * x.y + xl * xl + (xh * xl + xh * xl) + xh * xh;
}

static INLINE CONST Sleef_float2 dfrec_f2_f(float d) {
  float t = 1.0f / d;
  float dh = upperf(d), dl = d - dh;
  float th = upperf(t), tl = t - th;
  Sleef_float2 q;

  q.x = t;
  q.y = t * (1 - dh * th - dh * tl - dl * th - dl * tl);

  return q;
}

static INLINE CONST Sleef_float2 dfrec_f2_f2(Sleef_float2 d) {
  float t = 1.0f / d.x;
  float dh = upperf(d.x), dl = d.x - dh;
  float th = upperf(t  ), tl = t   - th;
  Sleef_float2 q;

  q.x = t;
  q.y = t * (1 - dh * th - dh * tl - dl * th - dl * tl - d.y * t);

  return q;
}

static INLINE CONST Sleef_float2 dfsqrt_f2_f2(Sleef_float2 d) {
  float t = SQRTF(d.x + d.y);
  return dfscale_f2_f2_f(dfmul_f2_f2_f2(dfadd2_f2_f2_f2(d, dfmul_f2_f_f(t, t)), dfrec_f2_f(t)), 0.5f);
}

static INLINE CONST Sleef_float2 dfsqrt_f2_f(float d) {
  float t = SQRTF(d);
  return dfscale_f2_f2_f(dfmul_f2_f2_f2(dfadd2_f2_f_f2(d, dfmul_f2_f_f(t, t)), dfrec_f2_f(t)), 0.5);
}

//

typedef struct {
  float d;
  int32_t i;
} fi_t;

typedef struct {
  Sleef_float2 df;
  int32_t i;
} dfi_t;

static CONST fi_t rempisubf(float x) {
  fi_t ret;
  float fr = x - (float)(INT64_C(1) << 10) * (int32_t)(x * (1.0f / (INT64_C(1) << 10)));
  ret.i = ((7 & ((x > 0 ? 4 : 3) + (int32_t)(fr * 8))) - 3) >> 1;
  fr = fr - 0.25f * (int32_t)(fr * 4 + mulsignf(0.5f, x));
  fr = fabsfk(fr) > 0.125f ? (fr - mulsignf(0.5f, x)) : fr;
  fr = fabsfk(fr) > 1e+10f ? 0 : fr;
  if (fabsfk(x) == 0.12499999254941940308f) { fr = x; ret.i = 0; }
  ret.d = fr;
  return ret;
}

static CONST dfi_t rempif(float a) {
  Sleef_float2 x, y;
  fi_t di;
  int ex = ilogb2kf(a) - 25, q = ex > (90 - 25) ? -64 : 0;
  a = ldexp3kf(a, q);
  if (ex < 0) ex = 0;
  ex *= 4;
  x = dfmul_f2_f_f(a, Sleef_rempitabsp[ex]);
  di = rempisubf(x.x);
  q = di.i;
  x.x = di.d;
  x = dfnormalize_f2_f2(x);
  y = dfmul_f2_f_f(a, Sleef_rempitabsp[ex+1]);
  x = dfadd2_f2_f2_f2(x, y);
  di = rempisubf(x.x);
  q += di.i;
  x.x = di.d;
  x = dfnormalize_f2_f2(x);
  y = dfmul_f2_f2_f(df(Sleef_rempitabsp[ex+2], Sleef_rempitabsp[ex+3]), a);
  x = dfadd2_f2_f2_f2(x, y);
  x = dfnormalize_f2_f2(x);
  x = dfmul_f2_f2_f2(x, df(3.1415927410125732422f*2, -8.7422776573475857731e-08f*2));
  dfi_t ret = { fabsfk(a) < 0.7f ? df(a, 0) : x, q };
  return ret;
}

EXPORT CONST float xsinf(float d) {
  int q;
  float u, s, t = d;

  if (fabsfk(d) < TRIGRANGEMAX2f) {
    q = (int)rintfk(d * (float)M_1_PI);
    d = mlaf(q, -PI_A2f, d);
    d = mlaf(q, -PI_B2f, d);
    d = mlaf(q, -PI_C2f, d);
  } else if (fabsfk(d) < TRIGRANGEMAXf) {
    q = (int)rintfk(d * (float)M_1_PI);
    d = mlaf(q, -PI_Af, d);
    d = mlaf(q, -PI_Bf, d);
    d = mlaf(q, -PI_Cf, d);
    d = mlaf(q, -PI_Df, d);
  } else {
    dfi_t dfi = rempif(t);
    q = ((dfi.i & 3) * 2 + (dfi.df.x > 0) + 1) >> 2;
    if ((dfi.i & 1) != 0) {
      dfi.df = dfadd2_f2_f2_f2(dfi.df, df(mulsignf(3.1415927410125732422f*-0.5, dfi.df.x),
                                          mulsignf(-8.7422776573475857731e-08f*-0.5, dfi.df.x)));
    }
    d = dfi.df.x + dfi.df.y;
    if (xisinff(t) || xisnanf(t)) d = SLEEF_NANf;
  }

  s = d * d;

  if ((q & 1) != 0) d = -d;

  u = 2.6083159809786593541503e-06f;
  u = mlaf(u, s, -0.0001981069071916863322258f);
  u = mlaf(u, s, 0.00833307858556509017944336f);
  u = mlaf(u, s, -0.166666597127914428710938f);

  u = mlaf(s, u * d, d);

  if (xisnegzerof(t)) u = -0.0f;

  return u;
}

EXPORT CONST float xsinf_u1(float d) {
  int q;
  float u;
  Sleef_float2 s, t, x;

  if (fabsfk(d) < TRIGRANGEMAX2f) {
    q = (int)rintfk(d * (float)M_1_PI);
    u = mlaf(q, -PI_A2f, d);
    s = dfadd2_f2_f_f(u, q * (-PI_B2f));
    s = dfadd_f2_f2_f(s, q * (-PI_C2f));
  } else {
    dfi_t dfi = rempif(d);
    q = ((dfi.i & 3) * 2 + (dfi.df.x > 0) + 1) >> 2;
    if ((dfi.i & 1) != 0) {
      dfi.df = dfadd2_f2_f2_f2(dfi.df, df(mulsignf(3.1415927410125732422f*-0.5, dfi.df.x),
                                          mulsignf(-8.7422776573475857731e-08f*-0.5, dfi.df.x)));
    }
    s = dfnormalize_f2_f2(dfi.df);
    if (xisinff(d) || xisnanf(d)) s.x = SLEEF_NANf;
  }

  t = s;
  s = dfsqu_f2_f2(s);

  u = 2.6083159809786593541503e-06f;
  u = mlaf(u, s.x, -0.0001981069071916863322258f);
  u = mlaf(u, s.x, 0.00833307858556509017944336f);

  x = dfadd_f2_f_f2(1, dfmul_f2_f2_f2(dfadd_f2_f_f(-0.166666597127914428710938f, u * s.x), s));

  u = dfmul_f_f2_f2(t, x);

  if ((q & 1) != 0) u = -u;
  if (xisnegzerof(d)) u = d;

  return u;
}

EXPORT CONST float xcosf(float d) {
  int q;
  float u, s, t = d;

  if (fabsfk(d) < TRIGRANGEMAX2f) {
    q = 1 + 2*(int)rintfk(d * (float)M_1_PI - 0.5f);
    d = mlaf(q, -PI_A2f*0.5f, d);
    d = mlaf(q, -PI_B2f*0.5f, d);
    d = mlaf(q, -PI_C2f*0.5f, d);
  } else if (fabsfk(d) < TRIGRANGEMAXf) {
    q = 1 + 2*(int)rintfk(d * (float)M_1_PI - 0.5f);
    d = mlaf(q, -PI_Af*0.5f, d);
    d = mlaf(q, -PI_Bf*0.5f, d);
    d = mlaf(q, -PI_Cf*0.5f, d);
    d = mlaf(q, -PI_Df*0.5f, d);
  } else {
    dfi_t dfi = rempif(t);
    q = ((dfi.i & 3) * 2 + (dfi.df.x > 0) + 7) >> 1;
    if ((dfi.i & 1) == 0) {
      dfi.df = dfadd2_f2_f2_f2(dfi.df, df(mulsignf(3.1415927410125732422f*-0.5, dfi.df.x > 0 ? 1 : -1),
                                          mulsignf(-8.7422776573475857731e-08f*-0.5, dfi.df.x > 0 ? 1 : -1)));
    }
    d = dfi.df.x + dfi.df.y;
    if (xisinff(t) || xisnanf(t)) d = SLEEF_NANf;
  }

  s = d * d;

  if ((q & 2) == 0) d = -d;

  u = 2.6083159809786593541503e-06f;
  u = mlaf(u, s, -0.0001981069071916863322258f);
  u = mlaf(u, s, 0.00833307858556509017944336f);
  u = mlaf(u, s, -0.166666597127914428710938f);

  u = mlaf(s, u * d, d);

  return u;
}

EXPORT CONST float xcosf_u1(float d) {
  float u;
  Sleef_float2 s, t, x;
  int q;

  if (fabsfk(d) < TRIGRANGEMAX2f) {
    d = fabsfk(d);
    float dq = mlaf(rintfk(d * (float)M_1_PI - 0.5f), 2, 1);
    q = (int)dq;
    s = dfadd2_f2_f_f (d, dq * (-PI_A2f*0.5f));
    s = dfadd2_f2_f2_f(s, dq * (-PI_B2f*0.5f));
    s = dfadd2_f2_f2_f(s, dq * (-PI_C2f*0.5f));
  } else {
    dfi_t dfi = rempif(d);
    q = ((dfi.i & 3) * 2 + (dfi.df.x > 0) + 7) >> 1;
    if ((dfi.i & 1) == 0) {
      dfi.df = dfadd2_f2_f2_f2(dfi.df, df(mulsignf(3.1415927410125732422f*-0.5, dfi.df.x > 0 ? 1 : -1),
                                          mulsignf(-8.7422776573475857731e-08f*-0.5, dfi.df.x > 0 ? 1 : -1)));
    }
    s = dfnormalize_f2_f2(dfi.df);
    if (xisinff(d) || xisnanf(d)) s.x = SLEEF_NANf;
  }

  t = s;
  s = dfsqu_f2_f2(s);

  u = 2.6083159809786593541503e-06f;
  u = mlaf(u, s.x, -0.0001981069071916863322258f);
  u = mlaf(u, s.x, 0.00833307858556509017944336f);

  x = dfadd_f2_f_f2(1, dfmul_f2_f2_f2(dfadd_f2_f_f(-0.166666597127914428710938f, u * s.x), s));

  u = dfmul_f_f2_f2(t, x);

  if ((((int)q) & 2) == 0) u = -u;

  return u;
}

EXPORT CONST float xfastsinf_u3500(float d) {
  int q;
  float u, s, t = d;

  q = rintfk(d * (float)M_1_PI);
  d = mlaf(q, -(float)M_PI, d);

  s = d * d;

  u = -0.1881748176e-3;
  u = mlaf(u, s, +0.8323502727e-2);
  u = mlaf(u, s, -0.1666651368e+0);
  u = mlaf(s * d, u, d);

  if ((q & 1) != 0) u = -u;

  if (UNLIKELY(fabsfk(t) > 30.0f)) return xsinf(t);

  return u;
}

EXPORT CONST float xfastcosf_u3500(float d) {
  int q;
  float u, s, t = d;

  q = rintfk(mlaf(d, (float)M_1_PI, -0.5f));
  d = mlaf(q, -(float)M_PI, d - (float)M_PI*0.5f);

  s = d * d;

  u = -0.1881748176e-3;
  u = mlaf(u, s, +0.8323502727e-2);
  u = mlaf(u, s, -0.1666651368e+0);
  u = mlaf(s * d, u, d);

  if ((q & 1) == 0) u = -u;

  if (UNLIKELY(fabsfk(t) > 30.0f)) return xcosf(t);

  return u;
}

EXPORT CONST Sleef_float2 xsincosf(float d) {
  int q;
  float u, s, t;
  Sleef_float2 r;

  s = d;

  if (fabsfk(d) < TRIGRANGEMAX2f) {
    q = (int)rintfk(d * ((float)(2 * M_1_PI)));
    s = mlaf(q, -PI_A2f*0.5f, s);
    s = mlaf(q, -PI_B2f*0.5f, s);
    s = mlaf(q, -PI_C2f*0.5f, s);
  } else if (fabsfk(d) < TRIGRANGEMAXf) {
    q = (int)rintfk(d * ((float)(2 * M_1_PI)));
    s = mlaf(q, -PI_Af*0.5f, s);
    s = mlaf(q, -PI_Bf*0.5f, s);
    s = mlaf(q, -PI_Cf*0.5f, s);
    s = mlaf(q, -PI_Df*0.5f, s);
  } else {
    dfi_t dfi = rempif(d);
    q = dfi.i;
    s = dfi.df.x + dfi.df.y;
    if (xisinff(d) || xisnanf(d)) s = SLEEF_NANf;
  }

  t = s;

  s = s * s;

  u = -0.000195169282960705459117889f;
  u = mlaf(u, s, 0.00833215750753879547119141f);
  u = mlaf(u, s, -0.166666537523269653320312f);
  u = u * s * t;

  r.x = t + u;

  if (xisnegzerof(d)) r.x = -0.0f;

  u = -2.71811842367242206819355e-07f;
  u = mlaf(u, s, 2.47990446951007470488548e-05f);
  u = mlaf(u, s, -0.00138888787478208541870117f);
  u = mlaf(u, s, 0.0416666641831398010253906f);
  u = mlaf(u, s, -0.5f);

  r.y = u * s + 1;

  if ((q & 1) != 0) { s = r.y; r.y = r.x; r.x = s; }
  if ((q & 2) != 0) { r.x = -r.x; }
  if (((q+1) & 2) != 0) { r.y = -r.y; }

  return r;
}

EXPORT CONST Sleef_float2 xsincosf_u1(float d) {
  int q;
  float u;
  Sleef_float2 r, s, t, x;

  if (fabsfk(d) < TRIGRANGEMAX2f) {
    q = (int)rintfk(d * (float)(2 * M_1_PI));
    u = mlaf(q, -PI_A2f*0.5f, d);
    s = dfadd2_f2_f_f(u, q * (-PI_B2f*0.5f));
    s = dfadd_f2_f2_f(s, q * (-PI_C2f*0.5f));
  } else {
    dfi_t dfi = rempif(d);
    q = dfi.i;
    s = dfi.df;
    if (xisinff(d) || xisnanf(d)) s.x = SLEEF_NANf;
  }

  t = s;
  s.x = dfsqu_f_f2(s);

  u = -0.000195169282960705459117889f;
  u = mlaf(u, s.x, 0.00833215750753879547119141f);
  u = mlaf(u, s.x, -0.166666537523269653320312f);

  u *= s.x * t.x;

  x = dfadd_f2_f2_f(t, u);
  r.x = x.x + x.y;
  if (xisnegzerof(d)) r.x = -0.0f;

  u = -2.71811842367242206819355e-07f;
  u = mlaf(u, s.x, 2.47990446951007470488548e-05f);
  u = mlaf(u, s.x, -0.00138888787478208541870117f);
  u = mlaf(u, s.x, 0.0416666641831398010253906f);
  u = mlaf(u, s.x, -0.5f);

  x = dfadd_f2_f_f2(1, dfmul_f2_f_f(s.x, u));
  r.y = x.x + x.y;

  if ((q & 1) != 0) { u = r.y; r.y = r.x; r.x = u; }
  if ((q & 2) != 0) { r.x = -r.x; }
  if (((q+1) & 2) != 0) { r.y = -r.y; }

  return r;
}

EXPORT CONST Sleef_float2 xsincospif_u05(float d) {
  float u, s, t;
  Sleef_float2 r, x, s2;

  u = d * 4;
  int q = ceilfk(u) & ~(int)1;

  s = u - (float)q;
  t = s;
  s = s * s;
  s2 = dfmul_f2_f_f(t, t);

  //

  u = +0.3093842054e-6;
  u = mlaf(u, s, -0.3657307388e-4);
  u = mlaf(u, s, +0.2490393585e-2);
  x = dfadd2_f2_f_f2(u * s, df(-0.080745510756969451904, -1.3373665339076936258e-09));
  x = dfadd2_f2_f2_f2(dfmul_f2_f2_f2(s2, x), df(0.78539818525314331055, -2.1857338617566484855e-08));

  x = dfmul_f2_f2_f(x, t);
  r.x = x.x + x.y;
  if (xisnegzerof(d)) r.x = -0.0f;

  u = -0.2430611801e-7;
  u = mlaf(u, s, +0.3590577080e-5);
  u = mlaf(u, s, -0.3259917721e-3);
  x = dfadd2_f2_f_f2(u * s, df(0.015854343771934509277, 4.4940051354032242811e-10));
  x = dfadd2_f2_f2_f2(dfmul_f2_f2_f2(s2, x), df(-0.30842512845993041992, -9.0728339030733922277e-09));

  x = dfadd2_f2_f2_f(dfmul_f2_f2_f2(x, s2), 1);
  r.y = x.x + x.y;

  if ((q & 2) != 0) { s = r.y; r.y = r.x; r.x = s; }
  if ((q & 4) != 0) { r.x = -r.x; }
  if (((q+2) & 4) != 0) { r.y = -r.y; }

  if (fabsfk(d) > 1e+7f) { r.x = 0; r.y = 1; }
  if (xisinff(d)) { r.x = r.y = SLEEF_NANf; }

  return r;
}

EXPORT CONST Sleef_float2 xsincospif_u35(float d) {
  float u, s, t;
  Sleef_float2 r;

  u = d * 4;
  int q = ceilfk(u) & ~(int)1;

  s = u - (float)q;
  t = s;
  s = s * s;

  //

  u = -0.3600925265e-4;
  u = mlaf(u, s, +0.2490088111e-2);
  u = mlaf(u, s, -0.8074551076e-1);
  u = mlaf(u, s, +0.7853981853e+0);

  r.x = u * t;

  u = +0.3539815225e-5;
  u = mlaf(u, s, -0.3259574005e-3);
  u = mlaf(u, s, +0.1585431583e-1);
  u = mlaf(u, s, -0.3084251285e+0);
  u = mlaf(u, s, 1);

  r.y = u;

  if ((q & 2) != 0) { s = r.y; r.y = r.x; r.x = s; }
  if ((q & 4) != 0) { r.x = -r.x; }
  if (((q+2) & 4) != 0) { r.y = -r.y; }

  if (fabsfk(d) > 1e+7f) { r.x = 0; r.y = 1; }
  if (xisinff(d)) { r.x = r.y = SLEEF_NANf; }

  return r;
}

EXPORT CONST float xtanf(float d) {
  int q;
  float u, s, x;

  x = d;

  if (fabsfk(d) < TRIGRANGEMAX2f*0.5f) {
    q = (int)rintfk(d * (float)(2 * M_1_PI));
    x = mlaf(q, -PI_A2f*0.5f, x);
    x = mlaf(q, -PI_B2f*0.5f, x);
    x = mlaf(q, -PI_C2f*0.5f, x);
  } else if (fabsfk(d) < TRIGRANGEMAXf) {
    q = (int)rintfk(d * (float)(2 * M_1_PI));
    x = mlaf(q, -PI_Af*0.5f, x);
    x = mlaf(q, -PI_Bf*0.5f, x);
    x = mlaf(q, -PI_Cf*0.5f, x);
    x = mlaf(q, -PI_Df*0.5f, x);
  } else {
    dfi_t dfi = rempif(d);
    q = dfi.i;
    x = dfi.df.x + dfi.df.y;
    if (xisinff(d) || xisnanf(d)) x = SLEEF_NANf;
  }

  s = x * x;

  if ((q & 1) != 0) x = -x;

  float s2 = s * s, s4 = s2 * s2;
  u = POLY6(s, s2, s4,
            0.00927245803177356719970703f,
            0.00331984995864331722259521f,
            0.0242998078465461730957031f,
            0.0534495301544666290283203f,
            0.133383005857467651367188f,
            0.333331853151321411132812f);

  u = mlaf(s, u * x, x);

  if ((q & 1) != 0) u = 1.0f / u;

  return u;
}

EXPORT CONST float xtanf_u1(float d) {
  int q;
  float u;
  Sleef_float2 s, t, x;

  if (fabsfk(d) < TRIGRANGEMAX2f) {
    q = (int)rintfk(d * (float)(2 * M_1_PI));
    u = mlaf(q, -PI_A2f*0.5f, d);
    s = dfadd2_f2_f_f(u, q * (-PI_B2f*0.5f));
    s = dfadd_f2_f2_f(s, q * (-PI_C2f*0.5f));
  } else {
    dfi_t dfi = rempif(d);
    q = dfi.i;
    s = dfi.df;
    if (xisinff(d) || xisnanf(d)) s.x = SLEEF_NANf;
  }

  if ((q & 1) != 0) s = dfneg_f2_f2(s);

  t = s;
  s = dfsqu_f2_f2(s);
  s = dfnormalize_f2_f2(s);

  u = 0.00446636462584137916564941f;
  u = mlaf(u, s.x, -8.3920182078145444393158e-05f);
  u = mlaf(u, s.x, 0.0109639242291450500488281f);
  u = mlaf(u, s.x, 0.0212360303848981857299805f);
  u = mlaf(u, s.x, 0.0540687143802642822265625f);

  x = dfadd_f2_f_f(0.133325666189193725585938f, u * s.x);
  x = dfadd_f2_f_f2(1, dfmul_f2_f2_f2(dfadd_f2_f_f2(0.33333361148834228515625f, dfmul_f2_f2_f2(s, x)), s));
  x = dfmul_f2_f2_f2(t, x);

  if ((q & 1) != 0) x = dfrec_f2_f2(x);

  u = x.x + x.y;

  if (xisnegzerof(d)) u = -0.0f;

  return u;
}

EXPORT CONST float xatanf(float s) {
  float t, u;
  int q = 0;

  if (signf(s) == -1) { s = -s; q = 2; }
  if (s > 1) { s = 1.0f / s; q |= 1; }

  t = s * s;

  float t2 = t * t, t4 = t2 * t2;
  u = POLY8(t, t2, t4,
            0.00282363896258175373077393f,
            -0.0159569028764963150024414f,
            0.0425049886107444763183594f,
            -0.0748900920152664184570312f,
            0.106347933411598205566406f,
            -0.142027363181114196777344f,
            0.199926957488059997558594f,
            -0.333331018686294555664062f);

  t = s + s * (t * u);

  if ((q & 1) != 0) t = 1.570796326794896557998982f - t;
  if ((q & 2) != 0) t = -t;

  return t;
}

static INLINE CONST float atan2kf(float y, float x) {
  float s, t, u;
  int q = 0;

  if (x < 0) { x = -x; q = -2; }
  if (y > x) { t = x; x = y; y = -t; q += 1; }

  s = y / x;
  t = s * s;

  float t2 = t * t, t4 = t2 * t2;
  u = POLY8(t, t2, t4,
            0.00282363896258175373077393f,
            -0.0159569028764963150024414f,
            0.0425049886107444763183594f,
            -0.0748900920152664184570312f,
            0.106347933411598205566406f,
            -0.142027363181114196777344f,
            0.199926957488059997558594f,
            -0.333331018686294555664062f);

  t = u * t * s + s;
  t = q * (float)(M_PI/2) + t;

  return t;
}

EXPORT CONST float xatan2f(float y, float x) {
  float r = atan2kf(fabsfk(y), x);

  r = mulsignf(r, x);
  if (xisinff(x) || x == 0) r = M_PIf/2 - (xisinff(x) ? (signf(x) * (float)(M_PI  /2)) : 0);
  if (xisinff(y)          ) r = M_PIf/2 - (xisinff(x) ? (signf(x) * (float)(M_PI*1/4)) : 0);
  if (              y == 0) r = (signf(x) == -1 ? M_PIf : 0);

  return xisnanf(x) || xisnanf(y) ? SLEEF_NANf : mulsignf(r, y);
}

EXPORT CONST float xasinf(float d) {
  int o = fabsfk(d) < 0.5f;
  float x2 = o ? (d*d) : ((1-fabsfk(d))*0.5f), x = o ? fabsfk(d) : SQRTF(x2), u;

  u = +0.4197454825e-1;
  u = mlaf(u, x2, +0.2424046025e-1);
  u = mlaf(u, x2, +0.4547423869e-1);
  u = mlaf(u, x2, +0.7495029271e-1);
  u = mlaf(u, x2, +0.1666677296e+0);
  u = mlaf(u, x * x2, x);

  float r = o ? u : (M_PIf/2 - 2*u);
  r = mulsignf(r, d);

  return r;
}

EXPORT CONST float xacosf(float d) {
  int o = fabsfk(d) < 0.5f;
  float x2 = o ? (d*d) : ((1-fabsfk(d))*0.5f), u;
  float x = o ? fabsfk(d) : SQRTF(x2);
  x = fabsfk(d) == 1.0 ? 0 : x;

  u = +0.4197454825e-1;
  u = mlaf(u, x2, +0.2424046025e-1);
  u = mlaf(u, x2, +0.4547423869e-1);
  u = mlaf(u, x2, +0.7495029271e-1);
  u = mlaf(u, x2, +0.1666677296e+0);

  u *= x * x2;

  float y = 3.1415926535897932f/2 - (mulsignf(x, d) + mulsignf(u, d));
  x += u;
  float r = o ? y : (x*2);
  if (!o && d < 0) r = dfadd_f2_f2_f(df(3.1415927410125732422f,-8.7422776573475857731e-08f), -r).x;

  return r;
}

static Sleef_float2 atan2kf_u1(Sleef_float2 y, Sleef_float2 x) {
  float u;
  Sleef_float2 s, t;
  int q = 0;

  if (x.x < 0) { x.x = -x.x; x.y = -x.y; q = -2; }
  if (y.x > x.x) { t = x; x = y; y.x = -t.x; y.y = -t.y; q += 1; }

  s = dfdiv_f2_f2_f2(y, x);
  t = dfsqu_f2_f2(s);
  t = dfnormalize_f2_f2(t);

  u = -0.00176397908944636583328247f;
  u = mlaf(u, t.x, 0.0107900900766253471374512f);
  u = mlaf(u, t.x, -0.0309564601629972457885742f);
  u = mlaf(u, t.x, 0.0577365085482597351074219f);
  u = mlaf(u, t.x, -0.0838950723409652709960938f);
  u = mlaf(u, t.x, 0.109463557600975036621094f);
  u = mlaf(u, t.x, -0.142626821994781494140625f);
  u = mlaf(u, t.x, 0.199983194470405578613281f);

  t = dfmul_f2_f2_f2(t, dfadd_f2_f_f(-0.333332866430282592773438f, u * t.x));
  t = dfmul_f2_f2_f2(s, dfadd_f2_f_f2(1, t));
  t = dfadd2_f2_f2_f2(dfmul_f2_f2_f(df(1.5707963705062866211f, -4.3711388286737928865e-08f), q), t);

  return t;
}

EXPORT CONST float xatan2f_u1(float y, float x) {
  if (fabsfk(x) < 2.9387372783541830947e-39f) { y *= (UINT64_C(1) << 24); x *= (UINT64_C(1) << 24); } // nexttowardf((1.0 / FLT_MAX), 1)
  Sleef_float2 d = atan2kf_u1(df(fabsfk(y), 0), df(x, 0));
  float r = d.x + d.y;

  r = mulsignf(r, x);
  if (xisinff(x) || x == 0) r = (float)M_PI/2 - (xisinff(x) ? (signf(x) * (float)(M_PI  /2)) : 0.0f);
  if (xisinff(y)          ) r = (float)M_PI/2 - (xisinff(x) ? (signf(x) * (float)(M_PI*1/4)) : 0.0f);
  if (              y == 0) r = (signf(x) == -1 ? (float)M_PI : 0.0f);

  return xisnanf(x) || xisnanf(y) ? SLEEF_NANf : mulsignf(r, y);
}

EXPORT CONST float xasinf_u1(float d) {
  int o = fabsfk(d) < 0.5f;
  float x2 = o ? (d*d) : ((1-fabsfk(d))*0.5f), u;
  Sleef_float2 x = o ? df(fabsfk(d), 0) : dfsqrt_f2_f(x2);
  x = fabsfk(d) == 1.0f ? df(0, 0) : x;

  u = +0.4197454825e-1;
  u = mlaf(u, x2, +0.2424046025e-1);
  u = mlaf(u, x2, +0.4547423869e-1);
  u = mlaf(u, x2, +0.7495029271e-1);
  u = mlaf(u, x2, +0.1666677296e+0);
  u *= x2 * x.x;

  Sleef_float2 y = dfadd_f2_f2_f(dfsub_f2_f2_f2(df(3.1415927410125732422f/4,-8.7422776573475857731e-08f/4), x), -u);
  float r = o ? (u + x.x) : ((y.x + y.y)*2);
  r = mulsignf(r, d);

  return r;
}

EXPORT CONST float xacosf_u1(float d) {
  int o = fabsfk(d) < 0.5f;
  float x2 = o ? (d*d) : ((1-fabsfk(d))*0.5f), u;
  Sleef_float2 x = o ? df(fabsfk(d), 0) : dfsqrt_f2_f(x2);
  x = fabsfk(d) == 1.0 ? df(0, 0) : x;

  u = +0.4197454825e-1;
  u = mlaf(u, x2, +0.2424046025e-1);
  u = mlaf(u, x2, +0.4547423869e-1);
  u = mlaf(u, x2, +0.7495029271e-1);
  u = mlaf(u, x2, +0.1666677296e+0);

  u = u * x.x * x2;

  Sleef_float2 y = dfsub_f2_f2_f2(df(3.1415927410125732422f/2,-8.7422776573475857731e-08f/2),
                                  dfadd_f2_f_f(mulsignf(x.x, d), mulsignf(u, d)));
  x = dfadd_f2_f2_f(x, u);
  y = o ? y : dfscale_f2_f2_f(x, 2);
  if (!o && d < 0) y = dfsub_f2_f2_f2(df(3.1415927410125732422f,-8.7422776573475857731e-08f), y);

  return y.x + y.y;
}

EXPORT CONST float xatanf_u1(float d) {
  Sleef_float2 d2 = atan2kf_u1(df(fabsfk(d), 0.0f), df(1.0f, 0.0f));
  float r = d2.x + d2.y;
  if (xisinff(d)) r = 1.570796326794896557998982f;
  return mulsignf(r, d);
}

EXPORT CONST float xlogf(float d) {
  float x, x2, t, m;
  int e;

  int o = d < FLT_MIN;
  if (o) d *= (float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32);

  e = ilogb2kf(d * (1.0f/0.75f));
  m = ldexp3kf(d, -e);

  if (o) e -= 64;

  x = (m-1.0f) / (m+1.0f);
  x2 = x * x;

  t = 0.2392828464508056640625f;
  t = mlaf(t, x2, 0.28518211841583251953125f);
  t = mlaf(t, x2, 0.400005877017974853515625f);
  t = mlaf(t, x2, 0.666666686534881591796875f);
  t = mlaf(t, x2, 2.0f);

  x = x * t + 0.693147180559945286226764f * e;

  if (xisinff(d)) x = SLEEF_INFINITYf;
  if (d < 0 || xisnanf(d)) x = SLEEF_NANf;
  if (d == 0) x = -SLEEF_INFINITYf;

  return x;
}

EXPORT CONST float xexpf(float d) {
  int q = (int)rintfk(d * R_LN2f);
  float s, u;

  s = mlaf(q, -L2Uf, d);
  s = mlaf(q, -L2Lf, s);

  u = 0.000198527617612853646278381;
  u = mlaf(u, s, 0.00139304355252534151077271);
  u = mlaf(u, s, 0.00833336077630519866943359);
  u = mlaf(u, s, 0.0416664853692054748535156);
  u = mlaf(u, s, 0.166666671633720397949219);
  u = mlaf(u, s, 0.5);

  u = s * s * u + s + 1.0f;
  u = ldexp2kf(u, q);

  if (d < -104) u = 0;
  if (d >  104) u = SLEEF_INFINITYf;

  return u;
}

static INLINE CONST float expkf(Sleef_float2 d) {
  int q = (int)rintfk((d.x + d.y) * R_LN2f);
  Sleef_float2 s, t;
  float u;

  s = dfadd2_f2_f2_f(d, q * -L2Uf);
  s = dfadd2_f2_f2_f(s, q * -L2Lf);

  s = dfnormalize_f2_f2(s);

  u = 0.00136324646882712841033936f;
  u = mlaf(u, s.x, 0.00836596917361021041870117f);
  u = mlaf(u, s.x, 0.0416710823774337768554688f);
  u = mlaf(u, s.x, 0.166665524244308471679688f);
  u = mlaf(u, s.x, 0.499999850988388061523438f);

  t = dfadd_f2_f2_f2(s, dfmul_f2_f2_f(dfsqu_f2_f2(s), u));

  t = dfadd_f2_f_f2(1, t);

  u = ldexpkf(t.x + t.y, q);

  if (d.x < -104) u = 0;

  return u;
}

static INLINE CONST float expm1kf(float d) {
  int q = (int)rintfk(d * R_LN2f);
  float s, u;

  s = mlaf(q, -L2Uf, d);
  s = mlaf(q, -L2Lf, s);

  float s2 = s * s, s4 = s2 * s2;
  u = POLY6(s, s2, s4,
            0.000198527617612853646278381,
            0.00139304355252534151077271,
            0.00833336077630519866943359,
            0.0416664853692054748535156,
            0.166666671633720397949219,
            0.5);

  u = s * s * u + s;

  if (q != 0) u = ldexp2kf(u + 1, q) - 1;

  return u;
}

static INLINE CONST Sleef_float2 logkf(float d) {
  Sleef_float2 x, x2, s;
  float m, t;
  int e;

  int o = d < FLT_MIN;
  if (o) d *= (float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32);

  e = ilogb2kf(d * (1.0f/0.75f));
  m = ldexp3kf(d, -e);

  if (o) e -= 64;

  x = dfdiv_f2_f2_f2(dfadd2_f2_f_f(-1, m), dfadd2_f2_f_f(1, m));
  x2 = dfsqu_f2_f2(x);

  t = 0.240320354700088500976562;
  t = mlaf(t, x2.x, 0.285112679004669189453125);
  t = mlaf(t, x2.x, 0.400007992982864379882812);
  Sleef_float2 c = df(0.66666662693023681640625f, 3.69183861259614332084311e-09f);

  s = dfmul_f2_f2_f(df(0.69314718246459960938f, -1.904654323148236017e-09f), e);
  s = dfadd_f2_f2_f2(s, dfscale_f2_f2_f(x, 2));
  s = dfadd_f2_f2_f2(s, dfmul_f2_f2_f2(dfmul_f2_f2_f2(x2, x),
                                       dfadd2_f2_f2_f2(dfmul_f2_f2_f(x2, t), c)));
  return s;
}

EXPORT CONST float xlogf_u1(float d) {
  Sleef_float2 x, s;
  float m, t, x2;
  int e;

  int o = d < FLT_MIN;
  if (o) d *= (float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32);

  e = ilogb2kf(d * (1.0f/0.75f));
  m = ldexp3kf(d, -e);

  if (o) e -= 64;

  x = dfdiv_f2_f2_f2(dfadd2_f2_f_f(-1, m), dfadd2_f2_f_f(1, m));
  x2 = x.x * x.x;

  t = +0.3027294874e+0f;
  t = mlaf(t, x2, +0.3996108174e+0f);
  t = mlaf(t, x2, +0.6666694880e+0f);

  s = dfmul_f2_f2_f(df(0.69314718246459960938f, -1.904654323148236017e-09f), (float)e);
  s = dfadd_f2_f2_f2(s, dfscale_f2_f2_f(x, 2));
  s = dfadd_f2_f2_f(s, x2 * x.x * t);

  float r = s.x + s.y;

  if (xisinff(d)) r = SLEEF_INFINITYf;
  if (d < 0 || xisnanf(d)) r = SLEEF_NANf;
  if (d == 0) r = -SLEEF_INFINITYf;

  return r;
}

static INLINE CONST Sleef_float2 expk2f(Sleef_float2 d) {
  int q = (int)rintfk((d.x + d.y) * R_LN2f);
  Sleef_float2 s, t;
  float u;

  s = dfadd2_f2_f2_f(d, q * -L2Uf);
  s = dfadd2_f2_f2_f(s, q * -L2Lf);

  u = +0.1980960224e-3f;
  u = mlaf(u, s.x, +0.1394256484e-2f);
  u = mlaf(u, s.x, +0.8333456703e-2f);
  u = mlaf(u, s.x, +0.4166637361e-1f);

  t = dfadd2_f2_f2_f(dfmul_f2_f2_f(s, u), +0.166666659414234244790680580464e+0f);
  t = dfadd2_f2_f2_f(dfmul_f2_f2_f2(s, t), 0.5);
  t = dfadd2_f2_f2_f2(s, dfmul_f2_f2_f2(dfsqu_f2_f2(s), t));

  t = dfadd2_f2_f_f2(1, t);

  t.x = ldexp2kf(t.x, q);
  t.y = ldexp2kf(t.y, q);

  return d.x < -104 ? df(0, 0) : t;
}

EXPORT CONST float xpowf(float x, float y) {
  int yisint = (y == (int)y) || (fabsfk(y) >= (float)(INT64_C(1) << 24));
  int yisodd = (1 & (int)y) != 0 && yisint && fabsfk(y) < (float)(INT64_C(1) << 24);

  float result = expkf(dfmul_f2_f2_f(logkf(fabsfk(x)), y));

  result = xisnanf(result) ? SLEEF_INFINITYf : result;
  result *= (x >= 0 ? 1 : (yisint ? (yisodd ? -1 : 1) : SLEEF_NANf));

  float efx = mulsignf(fabsfk(x) - 1, y);
  if (xisinff(y)) result = efx < 0 ? 0.0f : (efx == 0 ? 1.0f : SLEEF_INFINITYf);
  if (xisinff(x) || x == 0) result = mulsignf((xsignbitf(y) ^ (x == 0)) ? 0 : SLEEF_INFINITYf, yisodd ? x : 1);
  if (xisnanf(x) || xisnanf(y)) result = SLEEF_NANf;
  if (y == 0 || x == 1) result = 1;

  return result;
}

static INLINE CONST float logk3f(float d) {
  float x, x2, t, m;
  int e;

  int o = d < FLT_MIN;
  if (o) d *= (float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32);

  e = ilogb2kf(d * (1.0f/0.75f));
  m = ldexp3kf(d, -e);

  if (o) e -= 64;

  x = (m-1) / (m+1);
  x2 = x * x;

  t = 0.2392828464508056640625f;
  t = mlaf(t, x2, 0.28518211841583251953125f);
  t = mlaf(t, x2, 0.400005877017974853515625f);
  t = mlaf(t, x2, 0.666666686534881591796875f);
  t = mlaf(t, x2, 2.0f);

  x = mlaf(x, t, 0.693147180559945286226764f * e);

  return x;
}

static INLINE CONST float expk3f(float d) {
  int q = (int)rintfk(d * R_LN2f);
  float s, u;

  s = mlaf(q, -L2Uf, d);
  s = mlaf(q, -L2Lf, s);

  u = 0.000198527617612853646278381;
  u = mlaf(u, s, 0.00139304355252534151077271);
  u = mlaf(u, s, 0.00833336077630519866943359);
  u = mlaf(u, s, 0.0416664853692054748535156);
  u = mlaf(u, s, 0.166666671633720397949219);
  u = mlaf(u, s, 0.5);

  u = mlaf(s * s, u, s + 1.0f);
  u = ldexpkf(u, q);

  if (d < -104) u = 0;

  return u;
}

EXPORT CONST float xfastpowf_u3500(float x, float y) {
  float result = expk3f(logk3f(fabsfk(x)) * y);

  int yisint = (y == (int)y) || (fabsfk(y) >= (float)(INT64_C(1) << 24));
  int yisodd = (1 & (int)y) != 0 && yisint && fabsfk(y) < (float)(INT64_C(1) << 24);

  result *= (x < 0 && yisodd) ? -1 : 1;
  if (x == 0) result = 0;
  if (y == 0) result = 1;

  return result;
}

EXPORT CONST float xsinhf(float x) {
  float y = fabsfk(x);
  Sleef_float2 d = expk2f(df(y, 0));
  d = dfsub_f2_f2_f2(d, dfrec_f2_f2(d));
  y = (d.x + d.y) * 0.5f;

  y = fabsfk(x) > 89 ? SLEEF_INFINITYf : y;
  y = xisnanf(y) ? SLEEF_INFINITYf : y;
  y = mulsignf(y, x);
  y = xisnanf(x) ? SLEEF_NANf : y;

  return y;
}

EXPORT CONST float xcoshf(float x) {
  float y = fabsfk(x);
  Sleef_float2 d = expk2f(df(y, 0));
  d = dfadd_f2_f2_f2(d, dfrec_f2_f2(d));
  y = (d.x + d.y) * 0.5f;

  y = fabsfk(x) > 89 ? SLEEF_INFINITYf : y;
  y = xisnanf(y) ? SLEEF_INFINITYf : y;
  y = xisnanf(x) ? SLEEF_NANf : y;

  return y;
}

EXPORT CONST float xtanhf(float x) {
  float y = fabsfk(x);
  Sleef_float2 d = expk2f(df(y, 0));
  Sleef_float2 e = dfrec_f2_f2(d);
  d = dfdiv_f2_f2_f2(dfsub_f2_f2_f2(d, e), dfadd_f2_f2_f2(d, e));
  y = d.x + d.y;

  y = fabsfk(x) > 18.714973875f ? 1.0f : y;
  y = xisnanf(y) ? 1.0f : y;
  y = mulsignf(y, x);
  y = xisnanf(x) ? SLEEF_NANf : y;

  return y;
}

EXPORT CONST float xsinhf_u35(float x) {
  float e = expm1kf(fabsfk(x));
  float y = (e + 2) / (e + 1) * (0.5f * e);

  y = fabsfk(x) > 88 ? SLEEF_INFINITYf : y;
  y = xisnanf(y) ? SLEEF_INFINITYf : y;
  y = mulsignf(y, x);
  y = xisnanf(x) ? SLEEF_NANf : y;

  return y;
}

EXPORT CONST float xcoshf_u35(float x) {
  float e = xexpf(fabsfk(x));
  float y = 0.5f * e + 0.5f / e;

  y = fabsfk(x) > 88 ? SLEEF_INFINITYf : y;
  y = xisnanf(y) ? SLEEF_INFINITYf : y;
  y = xisnanf(x) ? SLEEF_NANf : y;

  return y;
}

EXPORT CONST float xtanhf_u35(float x) {
  float y = fabsfk(x);
  float d = expm1kf(2*y);
  y = d / (d + 2);

  y = fabsfk(x) > 18.714973875f ? 1.0f : y;
  y = xisnanf(y) ? 1.0f : y;
  y = mulsignf(y, x);
  y = xisnanf(x) ? SLEEF_NANf : y;

  return y;
}

static INLINE CONST Sleef_float2 logk2f(Sleef_float2 d) {
  Sleef_float2 x, x2, m, s;
  float t;
  int e;

  e = ilogbkf(d.x * (1.0f/0.75f));
  m = dfscale_f2_f2_f(d, pow2if(-e));

  x = dfdiv_f2_f2_f2(dfadd2_f2_f2_f(m, -1), dfadd2_f2_f2_f(m, 1));
  x2 = dfsqu_f2_f2(x);

  t = 0.2392828464508056640625f;
  t = mlaf(t, x2.x, 0.28518211841583251953125f);
  t = mlaf(t, x2.x, 0.400005877017974853515625f);
  t = mlaf(t, x2.x, 0.666666686534881591796875f);

  s = dfmul_f2_f2_f(df(0.69314718246459960938f, -1.904654323148236017e-09f), e);
  s = dfadd_f2_f2_f2(s, dfscale_f2_f2_f(x, 2));
  s = dfadd_f2_f2_f2(s, dfmul_f2_f2_f(dfmul_f2_f2_f2(x2, x), t));

  return s;
}

EXPORT CONST float xasinhf(float x) {
  float y = fabsfk(x);
  Sleef_float2 d;

  d = y > 1 ? dfrec_f2_f(x) : df(y, 0);
  d = dfsqrt_f2_f2(dfadd2_f2_f2_f(dfsqu_f2_f2(d), 1));
  d = y > 1 ? dfmul_f2_f2_f(d, y) : d;

  d = logk2f(dfnormalize_f2_f2(dfadd_f2_f2_f(d, x)));
  y = d.x + d.y;

  y = (fabsfk(x) > SQRT_FLT_MAX || xisnanf(y)) ? mulsignf(SLEEF_INFINITYf, x) : y;
  y = xisnanf(x) ? SLEEF_NANf : y;
  y = xisnegzerof(x) ? -0.0f : y;

  return y;
}

EXPORT CONST float xacoshf(float x) {
  Sleef_float2 d = logk2f(dfadd2_f2_f2_f(dfmul_f2_f2_f2(dfsqrt_f2_f2(dfadd2_f2_f_f(x, 1)), dfsqrt_f2_f2(dfadd2_f2_f_f(x, -1))), x));
  float y = d.x + d.y;

  y = (x > SQRT_FLT_MAX || xisnanf(y)) ? SLEEF_INFINITYf : y;
  y = x == 1.0f ? 0.0f : y;
  y = x < 1.0f ? SLEEF_NANf : y;
  y = xisnanf(x) ? SLEEF_NANf : y;

  return y;
}

EXPORT CONST float xatanhf(float x) {
  float y = fabsfk(x);
  Sleef_float2 d = logk2f(dfdiv_f2_f2_f2(dfadd2_f2_f_f(1, y), dfadd2_f2_f_f(1, -y)));
  y = y > 1.0f ? SLEEF_NANf : (y == 1.0f ? SLEEF_INFINITYf : (d.x + d.y) * 0.5f);

  y = xisinff(x) || xisnanf(y) ? SLEEF_NANf : y;
  y = mulsignf(y, x);
  y = xisnanf(x) ? SLEEF_NANf : y;

  return y;
}

EXPORT CONST float xexp2f(float d) {
  int q = (int)rintfk(d);
  float s, u;

  s = d - q;

  u = +0.1535920892e-3;
  u = mlaf(u, s, +0.1339262701e-2);
  u = mlaf(u, s, +0.9618384764e-2);
  u = mlaf(u, s, +0.5550347269e-1);
  u = mlaf(u, s, +0.2402264476e+0);
  u = mlaf(u, s, +0.6931471825e+0);
  u = dfnormalize_f2_f2(dfadd_f2_f_f2(1, dfmul_f2_f_f(u, s))).x;

  u = ldexp2kf(u, q);

  if (d >= 128) u = SLEEF_INFINITYf;
  if (d < -150) u = 0;

  return u;
}

EXPORT CONST float xexp2f_u35(float d) {
  int q = (int)rintfk(d);
  float s, u;

  s = d - q;

  u = +0.1535920892e-3;
  u = mlaf(u, s, +0.1339262701e-2);
  u = mlaf(u, s, +0.9618384764e-2);
  u = mlaf(u, s, +0.5550347269e-1);
  u = mlaf(u, s, +0.2402264476e+0);
  u = mlaf(u, s, +0.6931471825e+0);
  u = mlaf(u, s, +0.1000000000e+1);

  u = ldexp2kf(u, q);

  if (d >= 128) u = SLEEF_INFINITYf;
  if (d < -150) u = 0;

  return u;
}

EXPORT CONST float xexp10f(float d) {
  int q = (int)rintfk(d * (float)LOG10_2);
  float s, u;

  s = mlaf(q, -L10Uf, d);
  s = mlaf(q, -L10Lf, s);

  u = +0.6802555919e-1;
  u = mlaf(u, s, +0.2078080326e+0);
  u = mlaf(u, s, +0.5393903852e+0);
  u = mlaf(u, s, +0.1171245337e+1);
  u = mlaf(u, s, +0.2034678698e+1);
  u = mlaf(u, s, +0.2650949001e+1);
  Sleef_float2 x = dfadd_f2_f2_f(df(2.3025851249694824219, -3.1705172516493593157e-08), u * s);
  u = dfnormalize_f2_f2(dfadd_f2_f_f2(1, dfmul_f2_f2_f(x, s))).x;

  u = ldexp2kf(u, q);

  if (d > 38.5318394191036238941387f) u = SLEEF_INFINITYf; // log10(FLT_MAX)
  if (d < -50) u = 0;

  return u;
}

EXPORT CONST float xexp10f_u35(float d) {
  int q = (int)rintfk(d * (float)LOG10_2);
  float s, u;

  s = mlaf(q, -L10Uf, d);
  s = mlaf(q, -L10Lf, s);

  u = +0.2064004987e+0;
  u = mlaf(u, s, +0.5417877436e+0);
  u = mlaf(u, s, +0.1171286821e+1);
  u = mlaf(u, s, +0.2034656048e+1);
  u = mlaf(u, s, +0.2650948763e+1);
  u = mlaf(u, s, +0.2302585125e+1);
  u = mlaf(u, s, +0.1000000000e+1);

  u = ldexp2kf(u, q);

  if (d > 38.5318394191036238941387f) u = SLEEF_INFINITYf; // log10(FLT_MAX)
  if (d < -50) u = 0;

  return u;
}

EXPORT CONST float xexpm1f(float a) {
  Sleef_float2 d = dfadd2_f2_f2_f(expk2f(df(a, 0)), -1.0f);
  float x = d.x + d.y;
  if (a > 88.72283172607421875f) x = SLEEF_INFINITYf;
  if (a < -16.635532333438687426013570f) x = -1;
  if (xisnegzerof(a)) x = -0.0f;
  return x;
}

EXPORT CONST float xlog10f(float d) {
  Sleef_float2 x, s;
  float m, t, x2;
  int e;

  int o = d < FLT_MIN;
  if (o) d *= (float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32);

  e = ilogb2kf(d * (1.0f/0.75f));
  m = ldexp3kf(d, -e);

  if (o) e -= 64;

  x = dfdiv_f2_f2_f2(dfadd2_f2_f_f(-1, m), dfadd2_f2_f_f(1, m));
  x2 = x.x * x.x;

  t = +0.1314289868e+0;
  t = mlaf(t, x2, +0.1735493541e+0);
  t = mlaf(t, x2, +0.2895309627e+0);

  s = dfmul_f2_f2_f(df(0.30103001, -1.432098889e-08), (float)e);
  s = dfadd_f2_f2_f2(s, dfmul_f2_f2_f2(x, df(0.868588984, -2.170757285e-08)));
  s = dfadd_f2_f2_f(s, x2 * x.x * t);

  float r = s.x + s.y;

  if (xisinff(d)) r = SLEEF_INFINITYf;
  if (d < 0 || xisnanf(d)) r = SLEEF_NANf;
  if (d == 0) r = -SLEEF_INFINITYf;

  return r;
}

EXPORT CONST float xlog2f(float d) {
  Sleef_float2 x, s;
  float m, t, x2;
  int e;

  int o = d < FLT_MIN;
  if (o) d *= (float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32);

  e = ilogb2kf(d * (1.0f/0.75f));
  m = ldexp3kf(d, -e);

  if (o) e -= 64;

  x = dfdiv_f2_f2_f2(dfadd2_f2_f_f(-1, m), dfadd2_f2_f_f(1, m));
  x2 = x.x * x.x;

  t = +0.4374550283e+0f;
  t = mlaf(t, x2, +0.5764790177e+0f);
  t = mlaf(t, x2, +0.9618012905120f);

  s = dfadd2_f2_f_f2(e, dfmul_f2_f2_f2(x, df(2.8853900432586669922, 3.2734474483568488616e-08)));
  s = dfadd2_f2_f2_f(s, x2 * x.x * t);

  float r = s.x + s.y;

  if (xisinff(d)) r = SLEEF_INFINITYf;
  if (d < 0 || xisnanf(d)) r = SLEEF_NANf;
  if (d == 0) r = -SLEEF_INFINITYf;

  return r;
}

EXPORT CONST float xlog2f_u35(float d) {
  float m, t, x, x2;
  int e;

  int o = d < FLT_MIN;
  if (o) d *= (float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32);

  e = ilogb2kf(d * (1.0f/0.75f));
  m = ldexp3kf(d, -e);

  if (o) e -= 64;

  x = (m - 1) / (m + 1);
  x2 = x * x;

  t = +0.4374088347e+0;
  t = mlaf(t, x2, +0.5764843822e+0);
  t = mlaf(t, x2, +0.9618024230e+0);

  float r = mlaf(x2 * x, t, mlaf(x, +0.2885390043e+1, e));

  if (xisinff(d)) r = SLEEF_INFINITYf;
  if (d < 0 || xisnanf(d)) r = SLEEF_NANf;
  if (d == 0) r = -SLEEF_INFINITYf;

  return r;
}

EXPORT CONST float xlog1pf(float d) {
  Sleef_float2 x, s;
  float m, t, x2;
  int e;

  float dp1 = d + 1;

  int o = dp1 < FLT_MIN;
  if (o) dp1 *= (float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32);

  e = ilogb2kf(dp1 * (1.0f/0.75f));

  t = ldexp3kf(1, -e);
  m = mlaf(d, t, t-1);

  if (o) e -= 64;

  x = dfdiv_f2_f2_f2(df(m, 0), dfadd_f2_f_f(2, m));
  x2 = x.x * x.x;

  t = +0.3027294874e+0f;
  t = mlaf(t, x2, +0.3996108174e+0f);
  t = mlaf(t, x2, +0.6666694880e+0f);

  s = dfmul_f2_f2_f(df(0.69314718246459960938f, -1.904654323148236017e-09f), (float)e);
  s = dfadd_f2_f2_f2(s, dfscale_f2_f2_f(x, 2));
  s = dfadd_f2_f2_f(s, x2 * x.x * t);

  float r = s.x + s.y;

  if (d > 1e+38) r = SLEEF_INFINITYf;
  if (d < -1) r = SLEEF_NANf;
  if (d == -1) r = -SLEEF_INFINITYf;
  if (xisnegzerof(d)) r = -0.0f;

  return r;
}

EXPORT CONST float xcbrtf(float d) {
  float x, y, q = 1.0f;
  int e, r;

  e = ilogbkf(fabsfk(d))+1;
  d = ldexp2kf(d, -e);
  r = (e + 6144) % 3;
  q = (r == 1) ? 1.2599210498948731647672106f : q;
  q = (r == 2) ? 1.5874010519681994747517056f : q;
  q = ldexp2kf(q, (e + 6144) / 3 - 2048);

  q = mulsignf(q, d);
  d = fabsfk(d);

  x = -0.601564466953277587890625f;
  x = mlaf(x, d, 2.8208892345428466796875f);
  x = mlaf(x, d, -5.532182216644287109375f);
  x = mlaf(x, d, 5.898262500762939453125f);
  x = mlaf(x, d, -3.8095417022705078125f);
  x = mlaf(x, d, 2.2241256237030029296875f);

  y = d * x * x;
  y = (y - (2.0f / 3.0f) * y * (y * x - 1.0f)) * q;

  return y;
}

EXPORT CONST float xcbrtf_u1(float d) {
  float x, y, z;
  Sleef_float2 q2 = df(1, 0), u, v;
  int e, r;

  e = ilogbkf(fabsfk(d))+1;
  d = ldexp2kf(d, -e);
  r = (e + 6144) % 3;
  q2 = (r == 1) ? df(1.2599210739135742188, -2.4018701694217270415e-08) : q2;
  q2 = (r == 2) ? df(1.5874010324478149414,  1.9520385308169352356e-08) : q2;

  q2.x = mulsignf(q2.x, d); q2.y = mulsignf(q2.y, d);
  d = fabsfk(d);

  x = -0.601564466953277587890625f;
  x = mlaf(x, d, 2.8208892345428466796875f);
  x = mlaf(x, d, -5.532182216644287109375f);
  x = mlaf(x, d, 5.898262500762939453125f);
  x = mlaf(x, d, -3.8095417022705078125f);
  x = mlaf(x, d, 2.2241256237030029296875f);

  y = x * x; y = y * y; x -= (d * y - x) * (1.0 / 3.0f);

  z = x;

  u = dfmul_f2_f_f(x, x);
  u = dfmul_f2_f2_f2(u, u);
  u = dfmul_f2_f2_f(u, d);
  u = dfadd2_f2_f2_f(u, -x);
  y = u.x + u.y;

  y = -2.0 / 3.0 * y * z;
  v = dfadd2_f2_f2_f(dfmul_f2_f_f(z, z), y);
  v = dfmul_f2_f2_f(v, d);
  v = dfmul_f2_f2_f2(v, q2);
  z = ldexp2kf(v.x + v.y, (e + 6144) / 3 - 2048);

  if (xisinff(d)) { z = mulsignf(SLEEF_INFINITYf, q2.x); }
  if (d == 0) { z = mulsignf(0, q2.x); }

  return z;
}

//

EXPORT CONST float xfabsf(float x) { return fabsfk(x); }

EXPORT CONST float xcopysignf(float x, float y) { return copysignfk(x, y); }

EXPORT CONST float xfmaxf(float x, float y) {
  return y != y ? x : (x > y ? x : y);
}

EXPORT CONST float xfminf(float x, float y) {
  return y != y ? x : (x < y ? x : y);
}

EXPORT CONST float xfdimf(float x, float y) {
  float ret = x - y;
  if (ret < 0 || x == y) ret = 0;
  return ret;
}

EXPORT CONST float xtruncf(float x) {
  float fr = x - (int32_t)x;
  return (xisinff(x) || fabsfk(x) >= (float)(INT64_C(1) << 23)) ? x : copysignfk(x - fr, x);
}

EXPORT CONST float xfloorf(float x) {
  float fr = x - (int32_t)x;
  fr = fr < 0 ? fr+1.0f : fr;
  return (xisinff(x) || fabsfk(x) >= (float)(INT64_C(1) << 23)) ? x : copysignfk(x - fr, x);
}

EXPORT CONST float xceilf(float x) {
  float fr = x - (int32_t)x;
  fr = fr <= 0 ? fr : fr-1.0f;
  return (xisinff(x) || fabsfk(x) >= (float)(INT64_C(1) << 23)) ? x : copysignfk(x - fr, x);
}

EXPORT CONST float xroundf(float d) {
  float x = d + 0.5f;
  float fr = x - (int32_t)x;
  if (fr == 0 && x <= 0) x--;
  fr = fr < 0 ? fr+1.0f : fr;
  x = d == 0.4999999701976776123f ? 0 : x;  // nextafterf(0.5, 0)
  return (xisinff(d) || fabsfk(d) >= (float)(INT64_C(1) << 23)) ? d : copysignfk(x - fr, d);
}

EXPORT CONST float xrintf(float d) {
  float x = d + 0.5f;
  int32_t isodd = (1 & (int32_t)x) != 0;
  float fr = x - (int32_t)x;
  fr = (fr < 0 || (fr == 0 && isodd)) ? fr+1.0f : fr;
  x = d == 0.50000005960464477539f ? 0 : x;  // nextafterf(0.5, 1)
  return (xisinff(d) || fabsfk(d) >= (float)(INT64_C(1) << 23)) ? d : copysignfk(x - fr, d);
}

EXPORT CONST Sleef_float2 xmodff(float x) {
  float fr = x - (int32_t)x;
  fr = fabsfk(x) > (float)(INT64_C(1) << 23) ? 0 : fr;
  Sleef_float2 ret = { copysignfk(fr, x), copysignfk(x - fr, x) };
  return ret;
}

EXPORT CONST float xldexpf(float x, int exp) {
  if (exp >  300) exp =  300;
  if (exp < -300) exp = -300;

  int e0 = exp >> 2;
  if (exp < 0) e0++;
  if (-50 < exp && exp < 50) e0 = 0;
  int e1 = exp - (e0 << 2);

  float p = pow2if(e0);
  float ret = x * pow2if(e1) * p * p * p * p;

  return ret;
}

EXPORT CONST float xnextafterf(float x, float y) {
  float cxf;
  int32_t cxi;

  cxf = x == 0 ? mulsignf(0, y) : x;
  memcpy(&cxi, &cxf, sizeof(cxi));
  int c = (cxi < 0) == (y < x);
  if (c) cxi = -(cxi ^ (1 << 31));

  if (x != y) cxi--;

  if (c) cxi = -(cxi ^ (1 << 31));

  memcpy(&cxf, &cxi, sizeof(cxf));
  if (cxf == 0 && x != 0) cxf = mulsignf(0, x);
  if (x == 0 && y == 0) cxf = y;
  if (xisnanf(x) || xisnanf(y)) cxf = SLEEF_NANf;

  return cxf;
}

EXPORT CONST float xfrfrexpf(float x) {
  float cxf;
  int32_t cxu;

  if (fabsfk(x) < FLT_MIN) x *= (1 << 30);

  cxf = x;
  memcpy(&cxu, &cxf, sizeof(cxu));

  cxu &= ~0x7f800000U;
  cxu |=  0x3f000000U;

  memcpy(&cxf, &cxu, sizeof(cxf));
  if (xisinff(x)) cxf = mulsignf(SLEEF_INFINITYf, x);
  if (x == 0) cxf = x;

  return cxf;
}

EXPORT CONST int xexpfrexpf(float x) {
  float cxf;
  uint32_t cxu;

  int ret = 0;

  if (fabsfk(x) < FLT_MIN) { x *= (1 << 30); ret = -30; }

  cxf = x;
  memcpy(&cxu, &cxf, sizeof(cxu));
  ret += (int32_t)(((cxu >> 23) & 0xff)) - 0x7e;

  if (x == 0 || xisnanf(x) || xisinff(x)) ret = 0;

  return ret;
}

EXPORT CONST float xhypotf_u05(float x, float y) {
  x = fabsfk(x);
  y = fabsfk(y);
  float min = fminfk(x, y), n = min;
  float max = fmaxfk(x, y), d = max;

  if (max < FLT_MIN) { n *= UINT64_C(1) << 24; d *= UINT64_C(1) << 24; }
  Sleef_float2 t = dfdiv_f2_f2_f2(df(n, 0), df(d, 0));
  t = dfmul_f2_f2_f(dfsqrt_f2_f2(dfadd2_f2_f2_f(dfsqu_f2_f2(t), 1)), max);
  float ret = t.x + t.y;
  if (xisnanf(ret)) ret = SLEEF_INFINITYf;
  if (min == 0) ret = max;
  if (xisnanf(x) || xisnanf(y)) ret = SLEEF_NANf;
  if (x == SLEEF_INFINITYf || y == SLEEF_INFINITYf) ret = SLEEF_INFINITYf;
  return ret;
}

EXPORT CONST float xhypotf_u35(float x, float y) {
  x = fabsfk(x);
  y = fabsfk(y);
  float min = fminfk(x, y);
  float max = fmaxfk(x, y);

  float t = min / max;
  float ret = max * SQRTF(1 + t*t);
  if (min == 0) ret = max;
  if (xisnanf(x) || xisnanf(y)) ret = SLEEF_NANf;
  if (x == SLEEF_INFINITYf || y == SLEEF_INFINITYf) ret = SLEEF_INFINITYf;
  return ret;
}

static INLINE CONST float toward0f(float d) {
  return d == 0 ? 0 : intBitsToFloat(floatToRawIntBits(d)-1);
}

static INLINE CONST float ptruncf(float x) {
  return fabsfk(x) >= (float)(INT64_C(1) << 23) ? x : (x - (x - (int32_t)x));
}

EXPORT CONST float xfmodf(float x, float y) {
  float nu = fabsfk(x), de = fabsfk(y), s = 1, q;
  if (de < FLT_MIN) { nu *= UINT64_C(1) << 25; de *= UINT64_C(1) << 25; s = 1.0f / (UINT64_C(1) << 25); }
  Sleef_float2 r = df(nu, 0);
  float rde = toward0f(1.0f / de);

  for(int i=0;i<8;i++) { // ceil(log2(FLT_MAX) / 22)+1
    q = ptruncf(toward0f(r.x) * rde);
    q = (3*de > r.x && r.x >= de) ? 2 : q;
    q = (2*de > r.x && r.x >= de) ? 1 : q;
    r = dfnormalize_f2_f2(dfadd2_f2_f2_f2(r, dfmul_f2_f_f(q, -de)));
    if (r.x < de) break;
  }

  float ret = (r.x + r.y) * s;
  if (r.x + r.y == de) ret = 0;
  ret = mulsignf(ret, x);
  if (nu < de) ret = x;
  if (de == 0) ret = SLEEF_NANf;

  return ret;
}

static INLINE CONST float rintfk2(float d) {
  float x = d + 0.5f;
  int32_t isodd = (1 & (int32_t)x) != 0;
  float fr = x - (int32_t)x;
  fr = (fr < 0 || (fr == 0 && isodd)) ? fr+1.0f : fr;
  return (fabsfk(d) >= (float)(INT64_C(1) << 23)) ? d : copysignfk(x - fr, d);
}

EXPORT CONST float xremainderf(float x, float y) {
  float n = fabsfk(x), d = fabsfk(y), s = 1, q;
  if (d < FLT_MIN*2) { n *= UINT64_C(1) << 25; d *= UINT64_C(1) << 25; s = 1.0f / (UINT64_C(1) << 25); }
  float rd = 1.0f / d;
  Sleef_float2 r = df(n, 0);
  int qisodd = 0;

  for(int i=0;i<8;i++) { // ceil(log2(FLT_MAX) / 22)+1
    q = rintfk2(r.x * rd);
    if (fabsfk(r.x) < 1.5f * d) q = r.x < 0 ? -1 : 1;
    if (fabsfk(r.x) < 0.5f * d || (fabsfk(r.x) == 0.5f * d && !qisodd)) q = 0;
    if (q == 0) break;
    if (xisinff(q * -d)) q = q + mulsignf(-1, r.x);
    qisodd ^= (1 & (int)q) != 0 && fabsfk(q) < (float)(INT64_C(1) << 24);
    r = dfnormalize_f2_f2(dfadd2_f2_f2_f2(r, dfmul_f2_f_f(q, -d)));
  }

  float ret = r.x * s;
  ret = mulsignf(ret, x);
  if (xisinff(y)) ret = xisinff(x) ? SLEEF_NANf : x;
  if (d == 0) ret = SLEEF_NANf;

  return ret;
}

EXPORT CONST float xsqrtf_u05(float d) {
  float q = 0.5f;

  d = d < 0 ? SLEEF_NANf : d;

  if (d < 5.2939559203393770e-23f) {
    d *= 1.8889465931478580e+22f;
    q = 7.2759576141834260e-12f * 0.5f;
  }

  if (d > 1.8446744073709552e+19f) {
    d *= 5.4210108624275220e-20f;
    q = 4294967296.0f * 0.5f;
  }

  // http://en.wikipedia.org/wiki/Fast_inverse_square_root
  float x = intBitsToFloat(0x5f375a86 - (floatToRawIntBits(d + 1e-45f) >> 1));

  x = x * (1.5f - 0.5f * d * x * x);
  x = x * (1.5f - 0.5f * d * x * x);
  x = x * (1.5f - 0.5f * d * x * x) * d;

  Sleef_float2 d2 = dfmul_f2_f2_f2(dfadd2_f2_f_f2(d, dfmul_f2_f_f(x, x)), dfrec_f2_f(x));

  float ret = (d2.x + d2.y) * q;

  ret = d == SLEEF_INFINITYf ? SLEEF_INFINITYf : ret;
  ret = d == 0 ? d : ret;

  return ret;
}

EXPORT CONST float xsqrtf_u35(float d) {
  float q = 1.0f;

  d = d < 0 ? SLEEF_NANf : d;

  if (d < 5.2939559203393770e-23f) {
    d *= 1.8889465931478580e+22f;
    q = 7.2759576141834260e-12f;
  }

  if (d > 1.8446744073709552e+19f) {
    d *= 5.4210108624275220e-20f;
    q = 4294967296.0f;
  }

  // http://en.wikipedia.org/wiki/Fast_inverse_square_root
  float x = intBitsToFloat(0x5f375a86 - (floatToRawIntBits(d + 1e-45) >> 1));

  x = x * (1.5f - 0.5f * d * x * x);
  x = x * (1.5f - 0.5f * d * x * x);
  x = x * (1.5f - 0.5f * d * x * x);
  x = x * (1.5f - 0.5f * d * x * x);

  return d == SLEEF_INFINITYf ? SLEEF_INFINITYf : (x * d * q);
}

EXPORT CONST float xsqrtf(float d) { return SQRTF(d); }

EXPORT CONST float xfmaf(float x, float y, float z) {
  float h2 = x * y + z, q = 1;
  if (fabsfk(h2) < 1e-38f) {
    const float c0 = 1 << 25, c1 = c0 * c0, c2 = c1 * c1;
    x *= c1;
    y *= c1;
    z *= c2;
    q = 1.0f / c2;
  }
  if (fabsfk(h2) > 1e+38f) {
    const float c0 = 1 << 25, c1 = c0 * c0, c2 = c1 * c1;
    x *= 1.0 / c1;
    y *= 1.0 / c1;
    z *= 1.0 / c2;
    q = c2;
  }
  Sleef_float2 d = dfmul_f2_f_f(x, y);
  d = dfadd2_f2_f2_f(d, z);
  float ret = (x == 0 || y == 0) ? z : (d.x + d.y);
  if (xisinff(z) && !xisinff(x) && !xisnanf(x) && !xisinff(y) && !xisnanf(y)) h2 = z;
  return (xisinff(h2) || xisnanf(h2)) ? h2 : ret*q;
}

//

static INLINE CONST Sleef_float2 sinpifk(float d) {
  float u, s, t;
  Sleef_float2 x, s2;

  u = d * 4;
  int q = ceilfk(u) & ~1;
  int o = (q & 2) != 0;

  s = u - (float)q;
  t = s;
  s = s * s;
  s2 = dfmul_f2_f_f(t, t);

  //

  u = o ? -0.2430611801e-7f : +0.3093842054e-6f;
  u = mlaf(u, s, o ? +0.3590577080e-5f : -0.3657307388e-4f);
  u = mlaf(u, s, o ? -0.3259917721e-3f : +0.2490393585e-2f);
  x = dfadd2_f2_f_f2(u * s, o ? df(0.015854343771934509277, 4.4940051354032242811e-10) :
                     df(-0.080745510756969451904, -1.3373665339076936258e-09));
  x = dfadd2_f2_f2_f2(dfmul_f2_f2_f2(s2, x), o ? df(-0.30842512845993041992, -9.0728339030733922277e-09) :
                      df(0.78539818525314331055, -2.1857338617566484855e-08));

  x = dfmul_f2_f2_f2(x, o ? s2 : df(t, 0));
  x = o ? dfadd2_f2_f2_f(x, 1) : x;

  //

  if ((q & 4) != 0) { x.x = -x.x; x.y = -x.y; }

  return x;
}

EXPORT CONST float xsinpif_u05(float d) {
  Sleef_float2 x = sinpifk(d);
  float r = x.x + x.y;

  if (xisnegzerof(d)) r = -0.0;
  if (fabsfk(d) > TRIGRANGEMAX4f) r = 0;
  if (xisinff(d)) r = SLEEF_NANf;

  return r;
}

static INLINE CONST Sleef_float2 cospifk(float d) {
  float u, s, t;
  Sleef_float2 x, s2;

  u = d * 4;
  int q = ceilfk(u) & ~1;
  int o = (q & 2) == 0;

  s = u - (float)q;
  t = s;
  s = s * s;
  s2 = dfmul_f2_f_f(t, t);

  //

  u = o ? -0.2430611801e-7f : +0.3093842054e-6f;
  u = mlaf(u, s, o ? +0.3590577080e-5f : -0.3657307388e-4f);
  u = mlaf(u, s, o ? -0.3259917721e-3f : +0.2490393585e-2f);
  x = dfadd2_f2_f_f2(u * s, o ? df(0.015854343771934509277, 4.4940051354032242811e-10) :
                     df(-0.080745510756969451904, -1.3373665339076936258e-09));
  x = dfadd2_f2_f2_f2(dfmul_f2_f2_f2(s2, x), o ? df(-0.30842512845993041992, -9.0728339030733922277e-09) :
                      df(0.78539818525314331055, -2.1857338617566484855e-08));

  x = dfmul_f2_f2_f2(x, o ? s2 : df(t, 0));
  x = o ? dfadd2_f2_f2_f(x, 1) : x;

  //

  if (((q+2) & 4) != 0) { x.x = -x.x; x.y = -x.y; }

  return x;
}

EXPORT CONST float xcospif_u05(float d) {
  Sleef_float2 x = cospifk(d);
  float r = x.x + x.y;

  if (fabsfk(d) > TRIGRANGEMAX4f) r = 1;
  if (xisinff(d)) r = SLEEF_NANf;

  return r;
}

typedef struct {
  Sleef_float2 a, b;
} df2;

static CONST df2 gammafk(float a) {
  Sleef_float2 clc = df(0, 0), clln = df(1, 0), clld = df(1, 0), x, y, z;
  float t, u;

  int otiny = fabsfk(a) < 1e-30f, oref = a < 0.5f;

  x = otiny ? df(0, 0) : (oref ? dfadd2_f2_f_f(1, -a) : df(a, 0));

  int o0 = (0.5f <= x.x && x.x <= 1.2), o2 = 2.3 < x.x;

  y = dfnormalize_f2_f2(dfmul_f2_f2_f2(dfadd2_f2_f2_f(x, 1), x));
  y = dfnormalize_f2_f2(dfmul_f2_f2_f2(dfadd2_f2_f2_f(x, 2), y));

  clln = (o2 && x.x <= 7) ? y : clln;

  x = (o2 && x.x <= 7) ? dfadd2_f2_f2_f(x, 3) : x;
  t = o2 ? (1.0 / x.x) : dfnormalize_f2_f2(dfadd2_f2_f2_f(x, o0 ? -1 : -2)).x;

  u = o2 ? +0.000839498720672087279971000786 : (o0 ? +0.9435157776e+0f : +0.1102489550e-3f);
  u = mlaf(u, t, o2 ? -5.17179090826059219329394422e-05 : (o0 ? +0.8670063615e+0f : +0.8160019934e-4f));
  u = mlaf(u, t, o2 ? -0.000592166437353693882857342347 : (o0 ? +0.4826702476e+0f : +0.1528468856e-3f));
  u = mlaf(u, t, o2 ? +6.97281375836585777403743539e-05 : (o0 ? -0.8855129778e-1f : -0.2355068718e-3f));
  u = mlaf(u, t, o2 ? +0.000784039221720066627493314301 : (o0 ? +0.1013825238e+0f : +0.4962242092e-3f));
  u = mlaf(u, t, o2 ? -0.000229472093621399176949318732 : (o0 ? -0.1493408978e+0f : -0.1193488017e-2f));
  u = mlaf(u, t, o2 ? -0.002681327160493827160473958490 : (o0 ? +0.1697509140e+0f : +0.2891599433e-2f));
  u = mlaf(u, t, o2 ? +0.003472222222222222222175164840 : (o0 ? -0.2072454542e+0f : -0.7385451812e-2f));
  u = mlaf(u, t, o2 ? +0.083333333333333333335592087900 : (o0 ? +0.2705872357e+0f : +0.2058077045e-1f));

  y = dfmul_f2_f2_f2(dfadd2_f2_f2_f(x, -0.5), logk2f(x));
  y = dfadd2_f2_f2_f2(y, dfneg_f2_f2(x));
  y = dfadd2_f2_f2_f2(y, dfx(0.91893853320467278056)); // 0.5*log(2*M_PI)

  z = dfadd2_f2_f2_f(dfmul_f2_f_f (u, t), o0 ? -0.400686534596170958447352690395e+0f : -0.673523028297382446749257758235e-1f);
  z = dfadd2_f2_f2_f(dfmul_f2_f2_f(z, t), o0 ? +0.822466960142643054450325495997e+0f : +0.322467033928981157743538726901e+0f);
  z = dfadd2_f2_f2_f(dfmul_f2_f2_f(z, t), o0 ? -0.577215665946766039837398973297e+0f : +0.422784335087484338986941629852e+0f);
  z = dfmul_f2_f2_f(z, t);

  clc = o2 ? y : z;

  clld = o2 ? dfadd2_f2_f2_f(dfmul_f2_f_f(u, t), 1) : clld;

  y = clln;

  clc = otiny ? dfx(41.58883083359671856503) : // log(2^60)
    (oref ? dfadd2_f2_f2_f2(dfx(1.1447298858494001639), dfneg_f2_f2(clc)) : clc); // log(M_PI)
  clln = otiny ? df(1, 0) : (oref ? clln : clld);

  if (oref) x = dfmul_f2_f2_f2(clld, sinpifk(a - (float)(INT64_C(1) << 12) * (int32_t)(a * (1.0 / (INT64_C(1) << 12)))));

  clld = otiny ? df(a*((INT64_C(1) << 30)*(float)(INT64_C(1) << 30)), 0) : (oref ? x : y);

  df2 ret = { clc, dfdiv_f2_f2_f2(clln, clld) };

  return ret;
}

EXPORT CONST float xtgammaf_u1(float a) {
  df2 d = gammafk(a);
  Sleef_float2 y = dfmul_f2_f2_f2(expk2f(d.a), d.b);
  float r = y.x + y.y;
  r = (a == -SLEEF_INFINITYf || (a < 0 && xisintf(a)) || (xisnumberf(a) && a < 0 && xisnanf(r))) ? SLEEF_NANf : r;
  r = ((a == SLEEF_INFINITYf || xisnumberf(a)) && a >= -FLT_MIN && (a == 0 || a > 36 || xisnanf(r))) ? mulsignf(SLEEF_INFINITYf, a) : r;
  return r;
}

EXPORT CONST float xlgammaf_u1(float a) {
  df2 d = gammafk(a);
  Sleef_float2 y = dfadd2_f2_f2_f2(d.a, logk2f(dfabs_f2_f2(d.b)));
  float r = y.x + y.y;
  r = (xisinff(a) || (a <= 0 && xisintf(a)) || (xisnumberf(a) && xisnanf(r))) ? SLEEF_INFINITYf : r;
  return r;
}

static INLINE CONST Sleef_float2 dfmla(float x, Sleef_float2 y, Sleef_float2 z) {
  return dfadd2_f2_f2_f2(z, dfmul_f2_f2_f(y, x));
}
static INLINE CONST Sleef_float2 poly2df_b(float x, Sleef_float2 c1, Sleef_float2 c0) { return dfmla(x, c1, c0); }
static INLINE CONST Sleef_float2 poly2df(float x, float c1, Sleef_float2 c0) { return dfmla(x, df(c1, 0), c0); }
static INLINE CONST Sleef_float2 poly4df(float x, float c3, Sleef_float2 c2, Sleef_float2 c1, Sleef_float2 c0) {
  return dfmla(x*x, poly2df(x, c3, c2), poly2df_b(x, c1, c0));
}

EXPORT CONST float xerff_u1(float a) {
  float t, x = fabsfk(a);
  Sleef_float2 t2;
  float x2 = x * x, x4 = x2 * x2;

  if (x < 2.5) {
    // Abramowitz and Stegun
    t = POLY6(x, x2, x4,
              -0.4360447008e-6,
              +0.6867515367e-5,
              -0.3045156700e-4,
              +0.9808536561e-4,
              +0.2395523916e-3,
              +0.1459901541e-3);
    t2 = poly4df(x, t,
                 df(0.0092883445322513580322, -2.7863745897025330755e-11),
                 df(0.042275499552488327026, 1.3461399289988106057e-09),
                 df(0.070523701608180999756, -3.6616309318707365163e-09));
    t2 = dfadd_f2_f_f2(1, dfmul_f2_f2_f(t2, x));
    t2 = dfsqu_f2_f2(t2);
    t2 = dfsqu_f2_f2(t2);
    t2 = dfsqu_f2_f2(t2);
    t2 = dfsqu_f2_f2(t2);
    t2 = dfrec_f2_f2(t2);
  } else if (x > 4.0) {
    t2 = df(0, 0);
  } else {
    t = POLY6(x, x2, x4,
              -0.1130012848e-6,
              +0.4115272986e-5,
              -0.6928304356e-4,
              +0.7172692567e-3,
              -0.5131045356e-2,
              +0.2708637156e-1);
    t2 = poly4df(x, t,
                 df(-0.11064319312572479248, 3.7050452777225283007e-09),
                 df(-0.63192230463027954102, -2.0200432585073177859e-08),
                 df(-1.1296638250350952148, 2.5515120196453259252e-08));
    t2 = dfmul_f2_f2_f(t2, x);
    t2 = df(expkf(t2), 0);
  }

  t2 = dfadd2_f2_f2_f(t2, -1);

  if (x < 1e-4) t2 = dfmul_f2_f2_f(df(-1.1283792257308959961, 5.8635383422197591097e-08), x);
  return mulsignf(a == 0 ? 0 : (xisinff(a) ? 1 : (-t2.x - t2.y)), a);
}

EXPORT CONST float xerfcf_u15(float a) {
  float s = a, r = 0, t;
  Sleef_float2 u, d, x;
  a = fabsfk(a);
  int o0 = a < 1.0f, o1 = a < 2.2f, o2 = a < 4.3f, o3 = a < 10.1f;
  u = o1 ? df(a, 0) : dfdiv_f2_f2_f2(df(1, 0), df(a, 0));

  t = o0 ? -0.8638041618e-4f : o1 ? -0.6236977242e-5f : o2 ? -0.3869504035e+0f : +0.1115344167e+1f;
  t = mlaf(t, u.x, o0 ? +0.6000166177e-3f : o1 ? +0.5749821503e-4f : o2 ? +0.1288077235e+1f : -0.9454904199e+0f);
  t = mlaf(t, u.x, o0 ? -0.1665703603e-2f : o1 ? +0.6002851478e-5f : o2 ? -0.1816803217e+1f : -0.3667259514e+0f);
  t = mlaf(t, u.x, o0 ? +0.1795156277e-3f : o1 ? -0.2851036377e-2f : o2 ? +0.1249150872e+1f : +0.7155663371e+0f);
  t = mlaf(t, u.x, o0 ? +0.1914106123e-1f : o1 ? +0.2260518074e-1f : o2 ? -0.1328857988e+0f : -0.1262947265e-1f);

  d = dfmul_f2_f2_f(u, t);
  d = dfadd2_f2_f2_f2(d, o0 ? dfx(-0.102775359343930288081655368891e+0) :
                      o1 ? dfx(-0.105247583459338632253369014063e+0) :
                      o2 ? dfx(-0.482365310333045318680618892669e+0) :
                      dfx(-0.498961546254537647970305302739e+0));
  d = dfmul_f2_f2_f2(d, u);
  d = dfadd2_f2_f2_f2(d, o0 ? dfx(-0.636619483208481931303752546439e+0) :
                      o1 ? dfx(-0.635609463574589034216723775292e+0) :
                      o2 ? dfx(-0.134450203224533979217859332703e-2) :
                      dfx(-0.471199543422848492080722832666e-4));
  d = dfmul_f2_f2_f2(d, u);
  d = dfadd2_f2_f2_f2(d, o0 ? dfx(-0.112837917790537404939545770596e+1) :
                      o1 ? dfx(-0.112855987376668622084547028949e+1) :
                      o2 ? dfx(-0.572319781150472949561786101080e+0) :
                      dfx(-0.572364030327966044425932623525e+0));

  x = dfmul_f2_f2_f(o1 ? d : df(-a, 0), a);
  x = o1 ? x : dfadd2_f2_f2_f2(x, d);

  x = expk2f(x);
  x = o1 ? x : dfmul_f2_f2_f2(x, u);

  r = o3 ? (x.x + x.y) : 0;
  if (s < 0) r = 2 - r;
  r = xisnanf(s) ? SLEEF_NANf : r;
  return r;
}

//

#ifdef ENABLE_MAIN
// gcc -w -DENABLE_MAIN -I../common sleefsp.c rempitab.c -lm
#include <stdlib.h>
int main(int argc, char **argv) {
  float d1 = atof(argv[1]);
  //float d2 = atof(argv[2]);
  //float d3 = atof(argv[3]);
  //printf("%.20g, %.20g\n", (double)d1, (double)d2);
  //float i2 = atoi(argv[2]);
  //float c = xatan2f_u1(d1, d2);
  //printf("round %.20g\n", (double)d1);
  printf("test    = %.20g\n", (double)xsqrtf_u05(d1));
  //printf("correct = %.20g\n", (double)roundf(d1));
  //printf("rint %.20g\n", (double)d1);
  //printf("test    = %.20g\n", (double)xrintf(d1));
  //printf("correct = %.20g\n", (double)rintf(d1));
  //Sleef_float2 r = xsincospif_u35(d);
  //printf("%g, %g\n", (double)r.x, (double)r.y);
}
#endif
