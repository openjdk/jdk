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
#define ENABLE_RVV_SP
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
#define ENABLE_RVV_SP
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
static vfloat vf2getx_vf_vf2(vfloat2 v) { return v.x; }
static vfloat vf2gety_vf_vf2(vfloat2 v) { return v.y; }
#endif

//

#define DENORMAL_FLT_MIN (1.4012984643248170709e-45f)
#define POSITIVE_INFINITYf ((float)INFINITY)
#define NEGATIVE_INFINITYf (-(float)INFINITY)

typedef union {
  double d;
  uint64_t u64;
  int64_t i64;
} conv64_t;

typedef union {
  float f;
  uint32_t u32;
  int32_t i32;
} conv32_t;

static float nexttoward0f(float x, int n) {
  union {
    float f;
    int32_t u;
  } cx;
  cx.f = x;
  cx.u -= n;
  return x == 0 ? 0 : cx.f;
}

float rnd() {
  conv32_t c;
  switch(random() & 63) {
  case 0: return nexttoward0f( 0.0, -(random() & ((1 << (random() & 31)) - 1)));
  case 1: return nexttoward0f(-0.0, -(random() & ((1 << (random() & 31)) - 1)));
  case 2: return nexttoward0f( INFINITY, (random() & ((1 << (random() & 31)) - 1)));
  case 3: return nexttoward0f(-INFINITY, (random() & ((1 << (random() & 31)) - 1)));
  }
#ifdef ENABLE_SYS_getrandom
  syscall(SYS_getrandom, &c.u32, sizeof(c.u32), 0);
#else
  c.u32 = (uint32_t)random() | ((uint32_t)random() << 31);
#endif
  return c.f;
}

float rnd_fr() {
  conv32_t c;
  do {
#ifdef ENABLE_SYS_getrandom
    syscall(SYS_getrandom, &c.u32, sizeof(c.u32), 0);
#else
    c.u32 = (uint32_t)random() | ((uint32_t)random() << 31);
#endif
  } while(!isnumber(c.f));
  return c.f;
}

float rnd_zo() {
  conv32_t c;
  do {
#ifdef ENABLE_SYS_getrandom
    syscall(SYS_getrandom, &c.u32, sizeof(c.u32), 0);
#else
    c.u32 = (uint32_t)random() | ((uint32_t)random() << 31);
#endif
  } while(!isnumber(c.f) || c.f < -1 || 1 < c.f);
  return c.f;
}

vfloat vset(vfloat v, int idx, float d) {
  float a[VECTLENSP];
  vstoreu_v_p_vf(a, v);
  a[idx] = d;
  return vloadu_vf_p(a);
}

float vget(vfloat v, int idx) {
  float a[VECTLENSP];
  vstoreu_v_p_vf(a, v);
  return a[idx];
}

int main(int argc,char **argv)
{
  mpfr_t frw, frx, fry, frz;

  mpfr_set_default_prec(256);
  mpfr_inits(frw, frx, fry, frz, NULL);

  conv32_t cd;
  float d, t;
  float d2, d3, zo;
  vfloat vd = vcast_vf_f(0);
  vfloat vd2 = vcast_vf_f(0);
  vfloat vd3 = vcast_vf_f(0);
  vfloat vzo = vcast_vf_f(0);
  vfloat vad = vcast_vf_f(0);
  vfloat2 sc, sc2;
  int cnt, ecnt = 0;

  srandom(time(NULL));

  for(cnt = 0;ecnt < 1000;cnt++) {
    int e = cnt % VECTLENSP;
    switch(cnt & 7) {
    case 0:
      d = rnd();
      d2 = rnd();
      d3 = rnd();
      zo = rnd();
      break;
    case 1:
      cd.f = rint(rnd_zo() * 1e+10) * M_PI_4;
      cd.i32 += (random() & 0xff) - 0x7f;
      d = cd.f;
      d2 = rnd();
      d3 = rnd();
      zo = rnd();
      break;
    case 2:
      cd.f = rnd_fr() * M_PI_4;
      cd.i32 += (random() & 0xf) - 0x7;
      d = cd.f;
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

    vd = vset(vd, e, d);
    vd2 = vset(vd2, e, d2);
    vd3 = vset(vd3, e, d3);
    vzo = vset(vzo, e, zo);
    vad = vset(vad, e, fabs(d));

    sc  = xsincospif_u05(vd);
    sc2 = xsincospif_u35(vd);

    {
      const double rangemax2 = 1e+7/4;

      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sinpi(frx, frx, GMP_RNDN);

      double u0 = countULP2sp(t = vget(vf2getx_vf_vf2(sc), e), frx);

      if (u0 != 0 && ((fabs(d) <= rangemax2 && u0 > 0.505) || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincospif_u05 sin arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2sp(t = vget(vf2getx_vf_vf2(sc2), e), frx);

      if (u1 != 0 && ((fabs(d) <= rangemax2 && u1 > 2.0) || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincospif_u35 sin arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      double u2 = countULP2sp(t = vget(xsinpif_u05(vd), e), frx);

      if (u2 != 0 && ((fabs(d) <= rangemax2 && u2 > 0.506) || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sinpif_u05 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }
    }

    {
      const double rangemax2 = 1e+7/4;

      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cospi(frx, frx, GMP_RNDN);

      double u0 = countULP2sp(t = vget(vf2gety_vf_vf2(sc), e), frx);

      if (u0 != 0 && ((fabs(d) <= rangemax2 && u0 > 0.505) || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincospif_u05 cos arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2sp(t = vget(vf2gety_vf_vf2(sc), e), frx);

      if (u1 != 0 && ((fabs(d) <= rangemax2 && u1 > 2.0) || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincospif_u35 cos arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      double u2 = countULP2sp(t = vget(xcospif_u05(vd), e), frx);

      if (u2 != 0 && ((fabs(d) <= rangemax2 && u2 > 0.506) || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " cospif_u05 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }
    }

    sc = xsincosf(vd);
    sc2 = xsincosf_u1(vd);

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sin(frx, frx, GMP_RNDN);

      float u0 = countULPsp(t = vget(xsinf(vd), e), frx);

      if (u0 != 0 && (u0 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sinf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      float u1 = countULPsp(t = vget(vf2getx_vf_vf2(sc), e), frx);

      if (u1 != 0 && (u1 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincosf sin arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      float u2 = countULPsp(t = vget(xsinf_u1(vd), e), frx);

      if (u2 != 0 && (u2 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sinf_u1 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }

      float u3 = countULPsp(t = vget(vf2getx_vf_vf2(sc2), e), frx);

      if (u3 != 0 && (u3 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincosf_u1 sin arg=%.20g ulp=%.20g\n", d, u3);
        fflush(stdout); ecnt++;
      }

      float u4 = countULPsp(t = vget(xfastsinf_u3500(vd), e), frx);
      double ae4 = fabs(mpfr_get_d(frx, GMP_RNDN) - t);

      if (u4 > 350 && ae4 > 2e-6) {
        printf(ISANAME " fastsinf_u3500 arg=%.20g ulp=%.20g\n", d, u4);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cos(frx, frx, GMP_RNDN);

      float u0 = countULPsp(t = vget(xcosf(vd), e), frx);

      if (u0 != 0 && (u0 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " cosf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      float u1 = countULPsp(t = vget(vf2gety_vf_vf2(sc), e), frx);

      if (u1 != 0 && (u1 > 3.5 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincosf cos arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }

      float u2 = countULPsp(t = vget(xcosf_u1(vd), e), frx);

      if (u2 != 0 && (u2 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " cosf_u1 arg=%.20g ulp=%.20g\n", d, u2);
        fflush(stdout); ecnt++;
      }

      float u3 = countULPsp(t = vget(vf2gety_vf_vf2(sc2), e), frx);

      if (u3 != 0 && (u3 > 1 || fabs(t) > 1 || !isnumber(t))) {
        printf(ISANAME " sincosf_u1 cos arg=%.20g ulp=%.20g\n", d, u3);
        fflush(stdout); ecnt++;
      }

      float u4 = countULPsp(t = vget(xfastcosf_u3500(vd), e), frx);
      double ae4 = fabs(mpfr_get_d(frx, GMP_RNDN) - t);

      if (u4 > 350 && ae4 > 2e-6) {
        printf(ISANAME " fastcosf_u3500 arg=%.20g ulp=%.20g\n", d, u4);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_tan(frx, frx, GMP_RNDN);

      float u0 = countULPsp(t = vget(xtanf(vd), e), frx);

      if (u0 != 0 && (u0 > 3.5 || isnan(t))) {
        printf(ISANAME " tanf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      float u1 = countULPsp(t = vget(xtanf_u1(vd), e), frx);

      if (u1 != 0 && (u1 > 1 || isnan(t))) {
        printf(ISANAME " tanf_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, fabsf(d), GMP_RNDN);
      mpfr_log(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xlogf(vad), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " logf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = vget(xlogf_u1(vad), e), frx);

      if (u1 > 1) {
        printf(ISANAME " logf_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, fabsf(d), GMP_RNDN);
      mpfr_log10(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xlog10f(vad), e), frx);

      if (u0 > 1) {
        printf(ISANAME " log10f arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, fabsf(d), GMP_RNDN);
      mpfr_log2(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xlog2f(vad), e), frx);

      if (u0 > 1) {
        printf(ISANAME " log2f arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = vget(xlog2f_u35(vad), e), frx);

      if (u1 > 3.5) {
        printf(ISANAME " log2f_u35 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_log1p(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xlog1pf(vd), e), frx);

      if ((-1 <= d && d <= 1e+38 && u0 > 1) ||
          (d < -1 && !isnan(t)) ||
          (d > 1e+38 && !(u0 <= 1 || isinf(t)))) {
        printf(ISANAME " log1pf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xexpf(vd), e), frx);

      if (u0 > 1) {
        printf(ISANAME " expf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp2(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xexp2f(vd), e), frx);

      if (u0 > 1) {
        printf(ISANAME " exp2f arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = vget(xexp2f_u35(vd), e), frx);

      if (u1 > 3.5) {
        printf(ISANAME " exp2f_u35 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_exp10(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xexp10f(vd), e), frx);

      if (u0 > 1) {
        printf(ISANAME " exp10f arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = vget(xexp10f_u35(vd), e), frx);

      if (u1 > 3.5) {
        printf(ISANAME " exp10f_u35 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_expm1(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xexpm1f(vd), e), frx);

      if (u0 > 1) {
        printf(ISANAME " expm1f arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_pow(frx, fry, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xpowf(vd2, vd), e), frx);

      if (u0 > 1) {
        printf(ISANAME " powf arg=%.20g, %.20g ulp=%.20g\n", d2, d, u0);
        printf("correct = %g, test = %g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }

      if (isnumber(d) && isnumber(d2)) {
        double u1 = countULPsp(t = vget(xfastpowf_u3500(vd2, vd), e), frx);

        if (isnumber((float)mpfr_get_d(frx, GMP_RNDN)) && u1 > 350) {
          printf(ISANAME " fastpowf_u3500 arg=%.20g, %.20g ulp=%.20g\n", d2, d, u1);
          printf("correct = %g, test = %g\n", mpfr_get_d(frx, GMP_RNDN), t);
          fflush(stdout); ecnt++;
        }
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cbrt(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xcbrtf(vd), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " cbrtf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = vget(xcbrtf_u1(vd), e), frx);

      if (u1 > 1) {
        printf(ISANAME " cbrtf_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, zo, GMP_RNDN);
      mpfr_asin(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xasinf(vzo), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " asinf arg=%.20g ulp=%.20g\n", zo, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = vget(xasinf_u1(vzo), e), frx);

      if (u1 > 1) {
        printf(ISANAME " asinf_u1 arg=%.20g ulp=%.20g\n", zo, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, zo, GMP_RNDN);
      mpfr_acos(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xacosf(vzo), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " acosf arg=%.20g ulp=%.20g\n", zo, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = vget(xacosf_u1(vzo), e), frx);

      if (u1 > 1) {
        printf(ISANAME " acosf_u1 arg=%.20g ulp=%.20g\n", zo, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_atan(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xatanf(vd), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " atanf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULPsp(t = vget(xatanf_u1(vd), e), frx);

      if (u1 > 1) {
        printf(ISANAME " atanf_u1 arg=%.20g ulp=%.20g\n", d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_atan2(frx, fry, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xatan2f(vd2, vd), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " atan2f arg=%.20g, %.20g ulp=%.20g\n", d2, d, u0);
        fflush(stdout); ecnt++;
      }

      double u1 = countULP2sp(t = vget(xatan2f_u1(vd2, vd), e), frx);

      if (u1 > 1) {
        printf(ISANAME " atan2f_u1 arg=%.20g, %.20g ulp=%.20g\n", d2, d, u1);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sinh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xsinhf(vd), e), frx);

      if ((fabs(d) <= 88.5 && u0 > 1) ||
          (d >  88.5 && !(u0 <= 1 || (isinf(t) && t > 0))) ||
          (d < -88.5 && !(u0 <= 1 || (isinf(t) && t < 0)))) {
        printf(ISANAME " sinhf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cosh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xcoshf(vd), e), frx);

      if ((fabs(d) <= 88.5 && u0 > 1) || !(u0 <= 1 || (isinf(t) && t > 0))) {
        printf(ISANAME " coshf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_tanh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xtanhf(vd), e), frx);

      if (u0 > 1.0001) {
        printf(ISANAME " tanhf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sinh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xsinhf_u35(vd), e), frx);

      if ((fabs(d) <= 88 && u0 > 3.5) ||
          (d >  88 && !(u0 <= 3.5 || (isinf(t) && t > 0))) ||
          (d < -88 && !(u0 <= 3.5 || (isinf(t) && t < 0)))) {
        printf(ISANAME " sinhf_u35 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_cosh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xcoshf_u35(vd), e), frx);

      if ((fabs(d) <= 88 && u0 > 3.5) || !(u0 <= 3.5 || (isinf(t) && t > 0))) {
        printf(ISANAME " coshf_u35 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_tanh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xtanhf_u35(vd), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " tanhf_u35 arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_asinh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xasinhf(vd), e), frx);

      if ((fabs(d) < sqrt(FLT_MAX) && u0 > 1.0001) ||
          (d >=  sqrt(FLT_MAX) && !(u0 <= 1.0001 || (isinf(t) && t > 0))) ||
          (d <= -sqrt(FLT_MAX) && !(u0 <= 1.0001 || (isinf(t) && t < 0)))) {
        printf(ISANAME " asinhf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_acosh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xacoshf(vd), e), frx);

      if ((fabs(d) < sqrt(FLT_MAX) && u0 > 1.0001) ||
          (d >=  sqrt(FLT_MAX) && !(u0 <= 1.0001 || (isinff(t) && t > 0))) ||
          (d <= -sqrt(FLT_MAX) && !isnan(t))) {
        printf(ISANAME " acoshf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_atanh(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xatanhf(vd), e), frx);

      if (u0 > 1.0001) {
        printf(ISANAME " atanhf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    //

    /*
    {
      int exp = (random() & 8191) - 4096;
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_exp(frx, mpfr_get_exp(frx) + exp);

      double u0 = countULPsp(t = vget(xldexpf(d, exp)), frx);

      if (u0 > 0.5001) {
        printf("Pure C ldexpf arg=%.20g %d ulp=%.20g\n", d, exp, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }
    */

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_abs(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xfabsf(vd), e), frx);

      if (u0 != 0) {
        printf(ISANAME " fabsf arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_copysign(frx, frx, fry, GMP_RNDN);

      double u0 = countULPsp(t = vget(xcopysignf(vd, vd2), e), frx);

      if (u0 != 0 && !isnan(d2)) {
        printf(ISANAME " copysignf arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %g, test = %g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_max(frx, frx, fry, GMP_RNDN);

      double u0 = countULPsp(t = vget(xfmaxf(vd, vd2), e), frx);

      if (u0 != 0) {
        printf(ISANAME " fmaxf arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_min(frx, frx, fry, GMP_RNDN);

      double u0 = countULPsp(t = vget(xfminf(vd, vd2), e), frx);

      if (u0 != 0) {
        printf(ISANAME " fminf arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_dim(frx, frx, fry, GMP_RNDN);

      double u0 = countULPsp(t = vget(xfdimf(vd, vd2), e), frx);

      if (u0 > 0.5) {
        printf(ISANAME " fdimf arg=%.20g, %.20g ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_trunc(frx, frx);

      double u0 = countULPsp(t = vget(xtruncf(vd), e), frx);

      if (u0 != 0) {
        printf(ISANAME " truncf arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_floor(frx, frx);

      double u0 = countULPsp(t = vget(xfloorf(vd), e), frx);

      if (u0 != 0) {
        printf(ISANAME " floorf arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_ceil(frx, frx);

      double u0 = countULPsp(t = vget(xceilf(vd), e), frx);

      if (u0 != 0) {
        printf(ISANAME " ceilf arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_round(frx, frx);

      double u0 = countULPsp(t = vget(xroundf(vd), e), frx);

      if (u0 != 0) {
        printf(ISANAME " roundf arg=%.24g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_rint(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xrintf(vd), e), frx);

      if (u0 != 0) {
        printf(ISANAME " rintf arg=%.24g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_modf(fry, frz, frx, GMP_RNDN);

      vfloat2 t2 = xmodff(vd);
      double u0 = countULPsp(vget(vf2getx_vf_vf2(t2), e), frz);
      double u1 = countULPsp(vget(vf2gety_vf_vf2(t2), e), fry);

      if (u0 != 0 || u1 != 0) {
        printf(ISANAME " modff arg=%.20g ulp=%.20g %.20g\n", d, u0, u1);
        printf("correct = %.20g, %.20g\n", mpfr_get_d(frz, GMP_RNDN), mpfr_get_d(fry, GMP_RNDN));
        printf("test    = %.20g, %.20g\n", vget(vf2getx_vf_vf2(t2), e), vget(vf2gety_vf_vf2(t2), e));
        fflush(stdout); ecnt++;
      }
    }

    {
      t = vget(xnextafterf(vd, vd2), e);
      double c = nextafterf(d, d2);

      if (!(isnan(t) && isnan(c)) && t != c) {
        printf(ISANAME " nextafterf arg=%.20g, %.20g\n", d, d2);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_exp(frx, 0);

      double u0 = countULPsp(t = vget(xfrfrexpf(vd), e), frx);

      if (d != 0 && isnumber(d) && u0 != 0) {
        printf(ISANAME " frfrexpf arg=%.20g ulp=%.20g\n", d, u0);
        fflush(stdout); ecnt++;
      }
    }

    /*
    {
      mpfr_set_d(frx, d, GMP_RNDN);
      int cexp = mpfr_get_exp(frx);

      int texp = xexpfrexpf(d);

      if (d != 0 && isnumber(d) && cexp != texp) {
        printf(ISANAME " expfrexpf arg=%.20g\n", d);
        fflush(stdout); ecnt++;
      }
    }
    */

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_hypot(frx, frx, fry, GMP_RNDN);

      double u0 = countULP2sp(t = vget(xhypotf_u05(vd, vd2), e), frx);

      if (u0 > 0.5001) {
        printf(ISANAME " hypotf_u05 arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_hypot(frx, frx, fry, GMP_RNDN);

      double u0 = countULP2sp(t = vget(xhypotf_u35(vd, vd2), e), frx);

      if (u0 >= 3.5) {
        printf(ISANAME " hypotf_u35 arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_fmod(frx, frx, fry, GMP_RNDN);

      double u0 = countULPsp(t = vget(xfmodf(vd, vd2), e), frx);

      if (fabs((double)d / d2) < 1e+38 && u0 > 0.5) {
        printf(ISANAME " fmodf arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_remainder(frx, frx, fry, GMP_RNDN);

      double u0 = countULPsp(t = vget(xremainderf(vd, vd2), e), frx);

      if (fabs((double)d / d2) < 1e+38 && u0 > 0.5) {
        printf(ISANAME " remainderf arg=%.20g, %.20g  ulp=%.20g\n", d, d2, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_set_d(fry, d2, GMP_RNDN);
      mpfr_set_d(frz, d3, GMP_RNDN);
      mpfr_fma(frx, frx, fry, frz, GMP_RNDN);

      double u0 = countULP2sp(t = vget(xfmaf(vd, vd2, vd3), e), frx);
      double c = mpfr_get_d(frx, GMP_RNDN);

      if ((-1e+34 < c && c < 1e+33 && u0 > 0.5001) ||
          !(u0 <= 0.5001 || isinf(t))) {
        printf(ISANAME " fmaf arg=%.20g, %.20g, %.20g  ulp=%.20g\n", d, d2, d3, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

#ifndef DETERMINISTIC
    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sqrt(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xsqrtf(vd), e), frx);

      if (u0 > 1.0) {
        printf(ISANAME " sqrtf arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sqrt(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xsqrtf_u05(vd), e), frx);

      if (u0 > 0.5001) {
        printf(ISANAME " sqrtf_u05 arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_sqrt(frx, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xsqrtf_u35(vd), e), frx);

      if (u0 > 3.5) {
        printf(ISANAME " sqrtf_u35 arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }
#endif // #ifndef DETERMINISTIC

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_erfc(frx, frx, GMP_RNDN);

      double u0 = countULP2sp(t = vget(xerfcf_u15(vd), e), frx);

      if (u0 > 1.5) {
        printf(ISANAME " erfcf_u15 arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_erf(frx, frx, GMP_RNDN);

      double u0 = countULP2sp(t = vget(xerff_u1(vd), e), frx);

      if (u0 > 0.75) {
        printf(ISANAME " erff_u1 arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", mpfr_get_d(frx, GMP_RNDN), t);
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      int s;
      mpfr_lgamma(frx, &s, frx, GMP_RNDN);

      double u0 = countULPsp(t = vget(xlgammaf_u1(vd), e), frx);

      if (((d < 0 && fabsl(t - mpfr_get_ld(frx, GMP_RNDN)) > 1e-8 && u0 > 1) ||
           (0 <= d && d < 4e+36 && u0 > 1) || (4e+36 <= d && !(u0 <= 1 || isinf(t))))) {
        printf(ISANAME " xlgammaf_u1 arg=%.20g ulp=%.20g\n", d, u0);
        printf("correct = %.20g, test = %.20g\n", (float)mpfr_get_d(frx, GMP_RNDN), t);
        printf("Diff = %.20Lg\n", fabsl(t - mpfr_get_ld(frx, GMP_RNDN)));
        fflush(stdout); ecnt++;
      }
    }

    {
      mpfr_set_d(frx, d, GMP_RNDN);
      mpfr_gamma(frx, frx, GMP_RNDN);

      double u0 = countULP2sp(t = vget(xtgammaf_u1(vd), e), frx);
      double c = mpfr_get_d(frx, GMP_RNDN);

      if (isnumber(c) || isnumber(t)) {
        if (u0 > 1.0) {
          printf(ISANAME " xtgammaf_u1 arg=%.20g ulp=%.20g\n", d, u0);
          printf("correct = %.20g, test = %.20g\n", (float)mpfr_get_d(frx, GMP_RNDN), t);
          fflush(stdout); ecnt++;
        }
      }
    }

#if 0
    if (cnt % 1000 == 0) {
      printf("cnt = %d \r", cnt);
      fflush(stdout);
    }
#endif
  }
  mpfr_clears(frw, frx, fry, frz, NULL);
}
