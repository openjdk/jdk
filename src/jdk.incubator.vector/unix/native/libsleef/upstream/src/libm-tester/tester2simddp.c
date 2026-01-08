//   Copyright Naoki Shibata and contributors 2010 - 2023.
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

#ifdef ENABLE_SYS_getrandom
#define _GNU_SOURCE
#include <unistd.h>
#include <sys/syscall.h>
#endif

#include "sleef.h"
#include "quaddef.h"
#include "testerutil.h"

#ifdef ENABLE_SSE2
#define CONFIG 2
#include "helpersse2.h"
#include "renamesse2.h"
typedef Sleef___m128d_2 vdouble2;
typedef Sleef___m128_2 vfloat2;
#endif

#ifdef ENABLE_SSE4
#define CONFIG 4
#include "helpersse2.h"
#include "renamesse4.h"
typedef Sleef___m128d_2 vdouble2;
typedef Sleef___m128_2 vfloat2;
#endif

#ifdef ENABLE_AVX
#define CONFIG 1
#include "helperavx.h"
#include "renameavx.h"
typedef Sleef___m256d_2 vdouble2;
typedef Sleef___m256_2 vfloat2;
#endif

#ifdef ENABLE_FMA4
#define CONFIG 4
#include "helperavx.h"
#include "renamefma4.h"
typedef Sleef___m256d_2 vdouble2;
typedef Sleef___m256_2 vfloat2;
#endif

#ifdef ENABLE_AVX2
#define CONFIG 1
#include "helperavx2.h"
#include "renameavx2.h"
typedef Sleef___m256d_2 vdouble2;
typedef Sleef___m256_2 vfloat2;
#endif

#ifdef ENABLE_AVX2128
#define CONFIG 1
#include "helperavx2_128.h"
#include "renameavx2128.h"
typedef Sleef___m128d_2 vdouble2;
typedef Sleef___m128_2 vfloat2;
#endif

#ifdef ENABLE_AVX512F
#define CONFIG 1
#include "helperavx512f.h"
#include "renameavx512f.h"
typedef Sleef___m512d_2 vdouble2;
typedef Sleef___m512_2 vfloat2;
#endif

#ifdef ENABLE_AVX512FNOFMA
#define CONFIG 2
#include "helperavx512f.h"
#include "renameavx512fnofma.h"
typedef Sleef___m512d_2 vdouble2;
typedef Sleef___m512_2 vfloat2;
#endif

#ifdef ENABLE_VECEXT
#define CONFIG 1
#include "helpervecext.h"
#include "norename.h"
#endif

#ifdef ENABLE_PUREC
#define CONFIG 1
#include "helperpurec.h"
#include "norename.h"
#endif

#ifdef ENABLE_ADVSIMD
#define CONFIG 1
#include "helperadvsimd.h"
#include "renameadvsimd.h"
typedef Sleef_float64x2_t_2 vdouble2;
typedef Sleef_float32x4_t_2 vfloat2;
#endif

#ifdef ENABLE_ADVSIMDNOFMA
#define CONFIG 2
#include "helperadvsimd.h"
#include "renameadvsimdnofma.h"
typedef Sleef_float64x2_t_2 vdouble2;
typedef Sleef_float32x4_t_2 vfloat2;
#endif

#ifdef ENABLE_SVE
#define CONFIG 1
#include "helpersve.h"
#include "renamesve.h"
#endif /* ENABLE_SVE */

#ifdef ENABLE_SVENOFMA
#define CONFIG 2
#include "helpersve.h"
#include "renamesvenofma.h"
#endif

#ifdef ENABLE_VSX
#define CONFIG 1
#include "helperpower_128.h"
#include "renamevsx.h"
typedef Sleef_SLEEF_VECTOR_DOUBLE_2 vdouble2;
typedef Sleef_SLEEF_VECTOR_FLOAT_2 vfloat2;
#endif

#ifdef ENABLE_VSXNOFMA
#define CONFIG 2
#include "helperpower_128.h"
#include "renamevsxnofma.h"
typedef Sleef_SLEEF_VECTOR_DOUBLE_2 vdouble2;
typedef Sleef_SLEEF_VECTOR_FLOAT_2 vfloat2;
#endif

#ifdef ENABLE_VSX3
#define CONFIG 3
#include "helperpower_128.h"
#include "renamevsx3.h"
typedef Sleef_SLEEF_VECTOR_DOUBLE_2 vdouble2;
typedef Sleef_SLEEF_VECTOR_FLOAT_2 vfloat2;
#endif

#ifdef ENABLE_VSX3NOFMA
#define CONFIG 4
#include "helperpower_128.h"
#include "renamevsx3nofma.h"
typedef Sleef_SLEEF_VECTOR_DOUBLE_2 vdouble2;
typedef Sleef_SLEEF_VECTOR_FLOAT_2 vfloat2;
#endif

#ifdef ENABLE_VXE
#define CONFIG 140
#include "helpers390x_128.h"
#include "renamevxe.h"
typedef Sleef_SLEEF_VECTOR_DOUBLE_2 vdouble2;
typedef Sleef_SLEEF_VECTOR_FLOAT_2 vfloat2;
#endif

#ifdef ENABLE_VXENOFMA
#define CONFIG 141
#include "helpers390x_128.h"
#include "renamevxenofma.h"
typedef Sleef_SLEEF_VECTOR_DOUBLE_2 vdouble2;
typedef Sleef_SLEEF_VECTOR_FLOAT_2 vfloat2;
#endif

#ifdef ENABLE_VXE2
#define CONFIG 150
#include "helpers390x_128.h"
#include "renamevxe2.h"
typedef Sleef_SLEEF_VECTOR_DOUBLE_2 vdouble2;
typedef Sleef_SLEEF_VECTOR_FLOAT_2 vfloat2;
#endif

#ifdef ENABLE_VXE2NOFMA
#define CONFIG 151
#include "helpers390x_128.h"
#include "renamevxe2nofma.h"
typedef Sleef_SLEEF_VECTOR_DOUBLE_2 vdouble2;
typedef Sleef_SLEEF_VECTOR_FLOAT_2 vfloat2;
#endif

#ifdef ENABLE_RVVM1
#define CONFIG 1
#define ENABLE_RVV_DP
#include "helperrvv.h"
#include "renamervvm1.h"
#include "sleef.h"
#endif

#ifdef ENABLE_RVVM1NOFMA
#define CONFIG 2
#define ENABLE_RVV_DP
#include "helperrvv.h"
#include "renamervvm1nofma.h"
#include "sleef.h"
#endif

#ifdef ENABLE_RVVM2
#define CONFIG 1
#define ENABLE_RVV_DP
#include "helperrvv.h"
#include "renamervvm2.h"
#include "sleef.h"
#endif

#ifdef ENABLE_RVVM2NOFMA
#define CONFIG 2
#define ENABLE_RVV_DP
#include "helperrvv.h"
#include "renamervvm2nofma.h"
#include "sleef.h"
#endif

#ifdef ENABLE_PUREC_SCALAR
#define CONFIG 1
#include "helperpurec_scalar.h"
#include "renamepurec_scalar.h"
typedef Sleef_double_2 vdouble2;
typedef Sleef_float_2 vfloat2;
#endif

#ifdef ENABLE_PURECFMA_SCALAR
#define CONFIG 2
#include "helperpurec_scalar.h"
#include "renamepurecfma_scalar.h"
typedef Sleef_double_2 vdouble2;
typedef Sleef_float_2 vfloat2;
#endif

//

#if !(defined(ENABLE_SVE) || defined(ENABLE_SVENOFMA) || defined(ENABLE_RVVM1) || defined(ENABLE_RVVM1NOFMA) || defined(ENABLE_RVVM2) || defined(ENABLE_RVVM2NOFMA))
static vdouble vd2getx_vd_vd2(vdouble2 v) { return v.x; }
static vdouble vd2gety_vd_vd2(vdouble2 v) { return v.y; }
#endif

//

#define DENORMAL_DBL_MIN (4.9406564584124654418e-324)

#define POSITIVE_INFINITY INFINITY
#define NEGATIVE_INFINITY (-INFINITY)

typedef union {
  double d;
  uint64_t u64;
  int64_t i64;
} conv_t;

double nexttoward0(double x, int n) {
  union {
    double f;
    uint64_t u;
  } cx;
  cx.f = x;
  cx.u -= n;
  return cx.f;
}

double rnd() {
  conv_t c;
  switch(random() & 63) {
  case 0: return nexttoward0( 0.0, -(random() & ((1 << (random() & 31)) - 1)));
  case 1: return nexttoward0(-0.0, -(random() & ((1 << (random() & 31)) - 1)));
  case 2: return nexttoward0( INFINITY, (random() & ((1 << (random() & 31)) - 1)));
  case 3: return nexttoward0(-INFINITY, (random() & ((1 << (random() & 31)) - 1)));
  }
#ifdef ENABLE_SYS_getrandom
  syscall(SYS_getrandom, &c.u64, sizeof(c.u64), 0);
#else
  c.u64 = random() | ((uint64_t)random() << 31) | ((uint64_t)random() << 62);
#endif
  return c.d;
}

double rnd_fr() {
  conv_t c;
  do {
#ifdef ENABLE_SYS_getrandom
    syscall(SYS_getrandom, &c.u64, sizeof(c.u64), 0);
#else
    c.u64 = random() | ((uint64_t)random() << 31) | ((uint64_t)random() << 62);
#endif
  } while(!isnumber(c.d));
  return c.d;
}

double rnd_zo() {
  conv_t c;
  do {
#ifdef ENABLE_SYS_getrandom
    syscall(SYS_getrandom, &c.u64, sizeof(c.u64), 0);
#else
    c.u64 = random() | ((uint64_t)random() << 31) | ((uint64_t)random() << 62);
#endif
  } while(!isnumber(c.d) || c.d < -1 || 1 < c.d);
  return c.d;
}

void sinpifr(mpfr_t ret, double d) {
  mpfr_t frpi, frd;
  mpfr_inits(frpi, frd, NULL);

  mpfr_const_pi(frpi, GMP_RNDN);
  mpfr_set_d(frd, 1.0, GMP_RNDN);
  mpfr_mul(frpi, frpi, frd, GMP_RNDN);
  mpfr_set_d(frd, d, GMP_RNDN);
  mpfr_mul(frd, frpi, frd, GMP_RNDN);
  mpfr_sin(ret, frd, GMP_RNDN);

  mpfr_clears(frpi, frd, NULL);
}

void cospifr(mpfr_t ret, double d) {
  mpfr_t frpi, frd;
  mpfr_inits(frpi, frd, NULL);

  mpfr_const_pi(frpi, GMP_RNDN);
  mpfr_set_d(frd, 1.0, GMP_RNDN);
  mpfr_mul(frpi, frpi, frd, GMP_RNDN);
  mpfr_set_d(frd, d, GMP_RNDN);
  mpfr_mul(frd, frpi, frd, GMP_RNDN);
  mpfr_cos(ret, frd, GMP_RNDN);

  mpfr_clears(frpi, frd, NULL);
}

vdouble vset(vdouble v, int idx, double d) {
  double a[VECTLENDP];
  vstoreu_v_p_vd(a, v);
  a[idx] = d;
  return vloadu_vd_p(a);
}

double vget(vdouble v, int idx) {
  double a[VECTLENDP];
  vstoreu_v_p_vd(a, v);
  return a[idx];
}

int vgeti(vint v, int idx) {
  int a[VECTLENDP*2];
  vstoreu_v_p_vi(a, v);
  return a[idx];
}

int main(int argc,char **argv)
{
  mpfr_t frw, frx, fry, frz;

  mpfr_set_default_prec(256);
  mpfr_inits(frw, frx, fry, frz, NULL);

  conv_t cd;
  double d, t;
  double d2, d3, zo;
  vdouble vd = vcast_vd_d(0);
  vdouble vd2 = vcast_vd_d(0);
  vdouble vd3 = vcast_vd_d(0);
  vdouble vzo = vcast_vd_d(0);
  vdouble vad = vcast_vd_d(0);
  vdouble2 sc, sc2;
  int cnt, ecnt = 0;

  srandom(time(NULL));

  for(cnt = 0;ecnt < 1000;cnt++) {
    int e = cnt % VECTLENDP;
    switch(cnt & 7) {
    case 0:
      d = rnd();
      d2 = rnd();
      d3 = rnd();
      zo = rnd();
      break;
    case 1:
      cd.d = rint(rnd_zo() * 1e+10) * M_PI_4;
      cd.i64 += (random() & 0xff) - 0x7f;
      d = cd.d;
      d2 = rnd();
      d3 = rnd();
      zo = rnd();
      break;
    case 2:
      cd.d = rnd_fr() * M_PI_4;
      cd.i64 += (random() & 0xf) - 0x7;
      d = cd.d;
      d2 = rnd();
      d3 = rnd();
      zo = rnd();
      break;
    default:
      d = rnd_fr();
      d2 = rnd_fr();
      d3 = rnd_fr();
      zo = rnd_zo();
      break;
    }

    vd  = vset(vd, e, d);
    vd2 = vset(vd2, e, d2);
    vd3 = vset(vd3, e, d3);
    vzo = vset(vzo, e, zo);
    vad = vset(vad, e, fabs(d));

    //

    sc  = xsincospi_u05(vd);
    sc2 = xsincospi_u35(vd);

    {
      const double rangemax2 = 1e+9/4;

      sinpifr(frx, d);

      double u0 = countULP2dp(t = vget(vd2getx_vd_vd2(sc), e), frx);

      if (u0 != 0 && ((fabs(d) <= rangemax2 && u0 > 0.506) || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincospi_u05 sin arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2dp(t = vget(vd2getx_vd_vd2(sc2), e), frx);

      if (u1 != 0 && ((fabs(d) <= rangemax2 && u1 > 1.5) || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincospi_u35 sin arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      double u2 = countULP2dp(t = vget(xsinpi_u05(vd), e), frx);

      if (u2 != 0 && ((fabs(d) <= rangemax2 && u2 > 0.506) || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sinpi_u05 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }
    }

    {
      const double rangemax2 = 1e+9/4;

      cospifr(frx, d);

      double u0 = countULP2dp(t = vget(vd2gety_vd_vd2(sc), e), frx);

      if (u0 != 0 && ((fabs(d) <= rangemax2 && u0 > 0.506) || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincospi_u05 cos arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2dp(t = vget(vd2gety_vd_vd2(sc), e), frx);

      if (u1 != 0 && ((fabs(d) <= rangemax2 && u1 > 1.5) || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincospi_u35 cos arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      double u2 = countULP2dp(t = vget(xcospi_u05(vd), e), frx);

      if (u2 != 0 && ((fabs(d) <= rangemax2 && u2 > 0.506) || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " cospi_u05 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }
    }

    sc = xsincos(vd);
    sc2 = xsincos_u1(vd);

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sin(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xsin(vd), e), frx);

      if (u0 != 0 && (u0 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sin arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = vget(vd2getx_vd_vd2(sc), e), frx);

      if (u1 != 0 && (u1 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincos sin arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      double u2 = countULPdp(t = vget(xsin_u1(vd), e), frx);

      if (u2 != 0 && (u2 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sin_u1 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }

      double u3 = countULPdp(t = vget(vd2getx_vd_vd2(sc2), e), frx);

      if (u3 != 0 && (u3 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincos_u1 sin arg=%.20g ulp=%.20g\n", d, u3);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cos(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xcos(vd), e), frx);

      if (u0 != 0 && (u0 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " cos arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = vget(vd2gety_vd_vd2(sc), e), frx);

      if (u1 != 0 && (u1 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincos cos arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      double u2 = countULPdp(t = vget(xcos_u1(vd), e), frx);

      if (u2 != 0 && (u2 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " cos_u1 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }

      double u3 = countULPdp(t = vget(vd2gety_vd_vd2(sc2), e), frx);

      if (u3 != 0 && (u3 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincos_u1 cos arg=%.20g ulp=%.20g\n", d, u3);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_tan(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xtan(vd), e), frx);

      if (u0 != 0 && (u0 > 3.5 || isnan(t))) {
        printf(ISANAME " tan arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = vget(xtan_u1(vd), e), frx);

      if (u1 != 0 && (u1 > 1 || isnan(t))) {
        printf(ISANAME " tan_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, fabs(d), GMP_RNDN);
      mpfr_log(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xlog(vad), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " log arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = vget(xlog_u1(vad), e), frx);

      if (u1 > 1) {
        printf(ISANAME " log_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, fabs(d), GMP_RNDN);
      mpfr_log10(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xlog10(vad), e), frx);

      if (u0 > 1) {
        printf(ISANAME " log10 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, fabs(d), GMP_RNDN);
      mpfr_log2(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xlog2(vad), e), frx);

      if (u0 > 1) {
        printf(ISANAME " log2 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = vget(xlog2_u35(vad), e), frx);

      if (u1 > 3.5) {
        printf(ISANAME " log2_u35 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_log1p(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xlog1p(vd), e), frx);

      if ((-1 <= d && d <= 1e+307 && u0 > 1) ||
          (d < -1 && !isnan(t)) ||
          (d > 1e+307 && !(u0 <= 1 || isinf(t)))) {
        printf(ISANAME " log1p arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xexp(vd), e), frx);

      if (u0 > 1) {
        printf(ISANAME " exp arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp2(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xexp2(vd), e), frx);

      if (u0 > 1) {
        printf(ISANAME " exp2 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = vget(xexp2_u35(vd), e), frx);

      if (u1 > 3.5) {
        printf(ISANAME " exp2_u35 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp10(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xexp10(vd), e), frx);

      if (u0 > 1.09) {
        printf(ISANAME " exp10 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = vget(xexp10_u35(vd), e), frx);

      if (u1 > 3.5) {
        printf(ISANAME " exp10_u35 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_expm1(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xexpm1(vd), e), frx);

      if (u0 > 1) {
        printf(ISANAME " expm1 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_pow(frx, fry, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xpow(vd2, vd), e), frx);

      if (u0 > 1) {
        printf(ISANAME " pow arg=%.20g, %.20g ulp=%.20g\n", d2, d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cbrt(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xcbrt(vd), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " cbrt arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = vget(xcbrt_u1(vd), e), frx);

      if (u1 > 1) {
        printf(ISANAME " cbrt_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, zo, GMP_RNDN);
      mpfr_asin(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xasin(vzo), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " asin arg=%.20g ulp=%.20g\n", zo, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = vget(xasin_u1(vzo), e), frx);

      if (u1 > 1) {
        printf(ISANAME " asin_u1 arg=%.20g ulp=%.20g\n", zo, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, zo, GMP_RNDN);
      mpfr_acos(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xacos(vzo), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " acos arg=%.20g ulp=%.20g\n", zo, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = vget(xacos_u1(vzo), e), frx);

      if (u1 > 1) {
        printf(ISANAME " acos_u1 arg=%.20g ulp=%.20g\n", zo, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_atan(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xatan(vd), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " atan arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPdp(t = vget(xatan_u1(vd), e), frx);

      if (u1 > 1) {
        printf(ISANAME " atan_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_atan2(frx, fry, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xatan2(vd2, vd), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " atan2 arg=%.20g, %.20g ulp=%.20g\n", d2, d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2dp(t = vget(xatan2_u1(vd2, vd), e), frx);

      if (u1 > 1) {
        printf(ISANAME " atan2_u1 arg=%.20g, %.20g ulp=%.20g\n", d2, d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sinh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xsinh(vd), e), frx);

      if ((fabs(d) <= 709 && u0 > 1) ||
          (d >  709 && !(u0 <= 1 || (isinf(t) && t > 0))) ||
          (d < -709 && !(u0 <= 1 || (isinf(t) && t < 0)))) {
        printf(ISANAME " sinh arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cosh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xcosh(vd), e), frx);

      if ((fabs(d) <= 709 && u0 > 1) || !(u0 <= 1 || (isinf(t) && t > 0))) {
        printf(ISANAME " cosh arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_tanh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xtanh(vd), e), frx);

      if (u0 > 1) {
        printf(ISANAME " tanh arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sinh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xsinh_u35(vd), e), frx);

      if ((fabs(d) <= 709 && u0 > 3.5) ||
          (d >  709 && !(u0 <= 3.5 || (isinf(t) && t > 0))) ||
          (d < -709 && !(u0 <= 3.5 || (isinf(t) && t < 0)))) {
        printf(ISANAME " sinh_u35 arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cosh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xcosh_u35(vd), e), frx);

      if ((fabs(d) <= 709 && u0 > 3.5) || !(u0 <= 3.5 || (isinf(t) && t > 0))) {
        printf(ISANAME " cosh_u35 arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_tanh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xtanh_u35(vd), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " tanh_u35 arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_asinh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xasinh(vd), e), frx);

      if ((fabs(d) < sqrt(DBL_MAX) && u0 > 1) ||
          (d >=  sqrt(DBL_MAX) && !(u0 <= 1 || (isinf(t) && t > 0))) ||
          (d <= -sqrt(DBL_MAX) && !(u0 <= 1 || (isinf(t) && t < 0)))) {
        printf(ISANAME " asinh arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_acosh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xacosh(vd), e), frx);

      if ((fabs(d) < sqrt(DBL_MAX) && u0 > 1) ||
          (d >=  sqrt(DBL_MAX) && !(u0 <= 1 || (isinf(t) && t > 0))) ||
          (d <= -sqrt(DBL_MAX) && !isnan(t))) {
        printf(ISANAME " acosh arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_atanh(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xatanh(vd), e), frx);

      if (u0 > 1) {
        printf(ISANAME " atanh arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    //

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_abs(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xfabs(vd), e), frx);

      if (u0 != 0) {
        printf(ISANAME " fabs arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_copysign(frx, frx, fry, GMP_RNDN);

      double u0 = countULPdp(t = vget(xcopysign(vd, vd2), e), frx);

      if (u0 != 0 && !isnan(d2)) {
        printf(ISANAME " copysign arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %g, test = %g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_max(frx, frx, fry, GMP_RNDN);

      double u0 = countULPdp(t = vget(xfmax(vd, vd2), e), frx);

      if (u0 != 0) {
        printf(ISANAME " fmax arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_min(frx, frx, fry, GMP_RNDN);

      double u0 = countULPdp(t = vget(xfmin(vd, vd2), e), frx);

      if (u0 != 0) {
        printf(ISANAME " fmin arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_dim(frx, frx, fry, GMP_RNDN);

      double u0 = countULPdp(t = vget(xfdim(vd, vd2), e), frx);

      if (u0 > 0.5) {
        printf(ISANAME " fdim arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_trunc(frx, frx);

      double u0 = countULPdp(t = vget(xtrunc(vd), e), frx);

      if (u0 != 0) {
        printf(ISANAME " trunc arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_floor(frx, frx);

      double u0 = countULPdp(t = vget(xfloor(vd), e), frx);

      if (u0 != 0) {
        printf(ISANAME " floor arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_ceil(frx, frx);

      double u0 = countULPdp(t = vget(xceil(vd), e), frx);

      if (u0 != 0) {
        printf(ISANAME " ceil arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_round(frx, frx);

      double u0 = countULPdp(t = vget(xround(vd), e), frx);

      if (u0 != 0) {
        printf(ISANAME " round arg=%.24g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_rint(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xrint(vd), e), frx);

      if (u0 != 0) {
        printf(ISANAME " rint arg=%.24g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_set_d(frz, d3, GMP_RNDN);
      mpfr_fma(frx, frx, fry, frz, GMP_RNDN);

      double u0 = countULP2dp(t = vget(xfma(vd, vd2, vd3), e), frx);
      double c = mpfr_get_d(frx, GMP_RNDN);

      if ((-1e+303 < c && c < 1e+303 && u0 > 0.5) ||
          !(u0 <= 0.5 || isinf(t))) {
        printf(ISANAME " fma arg=%.20g, %.20g, %.20g  ulp=%.20g\n", d, d2, d3, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

#ifndef DETERMINISTIC
    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sqrt(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xsqrt(vd), e), frx);

      if (u0 > 1.0) {
        printf(ISANAME " sqrt arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sqrt(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xsqrt_u05(vd), e), frx);

      if (u0 > 0.50001) {
        printf(ISANAME " sqrt_u05 arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sqrt(frx, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xsqrt_u35(vd), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " sqrt_u35 arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }
#endif // #ifndef DETERMINISTIC

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_hypot(frx, frx, fry, GMP_RNDN);

      double u0 = countULP2dp(t = vget(xhypot_u05(vd, vd2), e), frx);

      if (u0 > 0.5) {
        printf(ISANAME " hypot_u05 arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_hypot(frx, frx, fry, GMP_RNDN);

      double u0 = countULP2dp(t = vget(xhypot_u35(vd, vd2), e), frx);
      double c = mpfr_get_d(frx, GMP_RNDN);

      if ((-1e+308 < c && c < 1e+308 && u0 > 3.5) ||
          !(u0 <= 3.5 || isinf(t))) {
        if (!(isinf(c) && t == 1.7976931348623157081e+308)) {
          printf(ISANAME " hypot_u35 arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
          printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
          fflush(stdout); ecnt++;
        }
      }
    }

    {
      t = vget(xnextafter(vd, vd2), e);
      double c = nextafter(d, d2);

      if (!(isnan(t) && isnan(c)) && t != c) {
        printf(ISANAME " nextafter arg=%.20g, %.20g\n", d, d2);
        printf("correct = %.20g, test = %.20g\n", c, t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_exp(frx, 0);

      double u0 = countULPdp(t = vget(xfrfrexp(vd), e), frx);

      if (d != 0 && isnumber(d) && u0 != 0) {
        printf(ISANAME " frfrexp arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_fmod(frx, frx, fry, GMP_RNDN);

      double u0 = countULPdp(t = vget(xfmod(vd, vd2), e), frx);

      if (fabsl((long double)d / d2) < 1e+300 && u0 > 0.5) {
        printf(ISANAME " fmod arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_remainder(frx, frx, fry, GMP_RNDN);

      double u0 = countULPdp(t = vget(xremainder(vd, vd2), e), frx);

      if (fabsl((long double)d / d2) < 1e+300 && u0 > 0.5) {
        printf(ISANAME " remainder arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    /*
    {
      mpfr_set_d(frx, d, GMP_RNDN);
      int cexp = mpfr_get_exp(frx);

      int texp = vgeti(xexpfrexp(vd), e);

      if (isnumber(d) && cexp != texp) {
        printf(ISANAME " expfrexp arg=%.20g\n", d);
        fflush(stdout); ecnt++;
      }
    }

    {
      int exp = (random() & 8191) - 4096;
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_exp(frx, mpfr_get_exp(frx) + exp);

      double u0 = countULPdp(t = vget(xldexp(d, exp), e), frx);

      if (u0 > 0.5) {
        printf(ISANAME " ldexp arg=%.20g %d ulp=%.20g\n", d, exp, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }
    */

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_modf(fry, frz, frx, GMP_RNDN);

      vdouble2 t2 = xmodf(vd);
      double u0 = countULPdp(vget(vd2getx_vd_vd2(t2), e), frz);
      double u1 = countULPdp(vget(vd2gety_vd_vd2(t2), e), fry);

      if (u0 != 0 || u1 != 0) {
        printf(ISANAME " modf arg=%.20g ulp=%.20g %.20g\n", d, u0, u1);
        printf("correct = %.20g, %.20g\n", mpfr_get_d(frz, GMP_RNDN), mpfr_get_d(fry, GMP_RNDN));
        printf("test    = %.20g, %.20g\n", vget(vd2getx_vd_vd2(t2), e), vget(vd2gety_vd_vd2(t2), e));
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      int s;
      mpfr_lgamma(frx, &s, frx, GMP_RNDN);

      double u0 = countULPdp(t = vget(xlgamma_u1(vd), e), frx);

      if (((d < 0 && fabsl(t - mpfr_get_ld(frx, GMP_RNDN)) > 1e-15 && u0 > 1) || (0 <= d && d < 2e+305 && u0 > 1) || (2e+305 <= d && !(u0 <= 1 || isinf(t))))) {
        printf(ISANAME " xlgamma_u1 arg=%.20g ulp=%.20g\n", d, u0);
        printf("Correct = %.20Lg, test = %.20g\n", mpfr_get_ld(frx, GMP_RNDN), t);
        printf("Diff = %.20Lg\n", fabsl(t - mpfr_get_ld(frx, GMP_RNDN)));
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_gamma(frx, frx, GMP_RNDN);

      double u0 = countULP2dp(t = vget(xtgamma_u1(vd), e), frx);

      if (u0 > 1.0) {
        printf(ISANAME " xtgamma_u1 arg=%.20g ulp=%.20g\n", d, u0);
        printf("Correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        printf("Diff = %.20Lg\n", fabsl(t - mpfr_get_ld(frx, GMP_RNDN)));
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_erfc(frx, frx, GMP_RNDN);

      static double ebz = 9.8813129168249308835e-324; // nextafter(nextafter(0, 1), 1);

      double u0 = countULP2dp(t = vget(xerfc_u15(vd), e), frx);

      if ((d > 26.2 && u0 > 2.5 && !(mpfr_get_d(frx, GMP_RNDN) == 0 && t <= ebz)) || (d <= 26.2 && u0 > 1.5)) {
        printf(ISANAME " xerfc_u15 arg=%.20g ulp=%.20g\n", d, u0);
        printf("Correct = %.20Lg, test = %.20g\n", mpfr_get_ld(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_erf(frx, frx, GMP_RNDN);

      double u0 = countULP2dp(t = vget(xerf_u1(vd), e), frx);

      if (u0 > 0.75) {
        printf(ISANAME " xerf_u1 arg=%.20g ulp=%.20g\n", d, u0);
        printf("Correct = %.20Lg, test = %.20g\n", mpfr_get_ld(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }
  }
  mpfr_clears(frw, frx, fry, frz, NULL);
}
