//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <inttypes.h>
#include <assert.h>

#include <math.h>

#if defined(__MINGW32__) || defined(__MINGW64__) || defined(_MSC_VER)
#define STDIN_FILENO 0
#else
#include <unistd.h>
#include <sys/types.h>
#endif

#include "sleef.h"
#include "testerutil.h"

#define DORENAME
#include "rename.h"

#define BUFSIZE 1024

int main(int argc, char **argv) {
  char buf[BUFSIZE];

  printf("3\n");
  fflush(stdout);

  for(;;) {
    if (fgets(buf, BUFSIZE-1, stdin) == NULL) break;

    if (startsWith(buf, "sin ")) {
      uint64_t u;
      sscanf(buf, "sin %" PRIx64, &u);
      u = d2u(xsin(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "sin_u1 ")) {
      uint64_t u;
      sscanf(buf, "sin_u1 %" PRIx64, &u);
      u = d2u(xsin_u1(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "cos ")) {
      uint64_t u;
      sscanf(buf, "cos %" PRIx64, &u);
      u = d2u(xcos(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "cos_u1 ")) {
      uint64_t u;
      sscanf(buf, "cos_u1 %" PRIx64, &u);
      u = d2u(xcos_u1(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "sincos ")) {
      uint64_t u;
      sscanf(buf, "sincos %" PRIx64, &u);
      Sleef_double2 x = xsincos(u2d(u));
      printf("%" PRIx64 " %" PRIx64 "\n", d2u(x.x), d2u(x.y));
    } else if (startsWith(buf, "sincos_u1 ")) {
      uint64_t u;
      sscanf(buf, "sincos_u1 %" PRIx64, &u);
      Sleef_double2 x = xsincos_u1(u2d(u));
      printf("%" PRIx64 " %" PRIx64 "\n", d2u(x.x), d2u(x.y));
    } else if (startsWith(buf, "sincospi_u05 ")) {
      uint64_t u;
      sscanf(buf, "sincospi_u05 %" PRIx64, &u);
      Sleef_double2 x = xsincospi_u05(u2d(u));
      printf("%" PRIx64 " %" PRIx64 "\n", d2u(x.x), d2u(x.y));
    } else if (startsWith(buf, "sincospi_u35 ")) {
      uint64_t u;
      sscanf(buf, "sincospi_u35 %" PRIx64, &u);
      Sleef_double2 x = xsincospi_u35(u2d(u));
      printf("%" PRIx64 " %" PRIx64 "\n", d2u(x.x), d2u(x.y));
    } else if (startsWith(buf, "sinpi_u05 ")) {
      uint64_t u;
      sscanf(buf, "sinpi_u05 %" PRIx64, &u);
      u = d2u(xsinpi_u05(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "cospi_u05 ")) {
      uint64_t u;
      sscanf(buf, "cospi_u05 %" PRIx64, &u);
      u = d2u(xcospi_u05(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "tan ")) {
      uint64_t u;
      sscanf(buf, "tan %" PRIx64, &u);
      u = d2u(xtan(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "tan_u1 ")) {
      uint64_t u;
      sscanf(buf, "tan_u1 %" PRIx64, &u);
      u = d2u(xtan_u1(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "asin ")) {
      uint64_t u;
      sscanf(buf, "asin %" PRIx64, &u);
      u = d2u(xasin(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "acos ")) {
      uint64_t u;
      sscanf(buf, "acos %" PRIx64, &u);
      u = d2u(xacos(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "atan ")) {
      uint64_t u;
      sscanf(buf, "atan %" PRIx64, &u);
      u = d2u(xatan(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "log ")) {
      uint64_t u;
      sscanf(buf, "log %" PRIx64, &u);
      u = d2u(xlog(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "exp ")) {
      uint64_t u;
      sscanf(buf, "exp %" PRIx64, &u);
      u = d2u(xexp(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "atan2 ")) {
      uint64_t u, v;
      sscanf(buf, "atan2 %" PRIx64 " %" PRIx64, &u, &v);
      u = d2u(xatan2(u2d(u), u2d(v)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "asin_u1 ")) {
      uint64_t u;
      sscanf(buf, "asin_u1 %" PRIx64, &u);
      u = d2u(xasin_u1(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "acos_u1 ")) {
      uint64_t u;
      sscanf(buf, "acos_u1 %" PRIx64, &u);
      u = d2u(xacos_u1(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "atan_u1 ")) {
      uint64_t u;
      sscanf(buf, "atan_u1 %" PRIx64, &u);
      u = d2u(xatan_u1(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "atan2_u1 ")) {
      uint64_t u, v;
      sscanf(buf, "atan2_u1 %" PRIx64 " %" PRIx64, &u, &v);
      u = d2u(xatan2_u1(u2d(u), u2d(v)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "log_u1 ")) {
      uint64_t u;
      sscanf(buf, "log_u1 %" PRIx64, &u);
      u = d2u(xlog_u1(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "pow ")) {
      uint64_t u, v;
      sscanf(buf, "pow %" PRIx64 " %" PRIx64, &u, &v);
      u = d2u(xpow(u2d(u), u2d(v)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "sinh ")) {
      uint64_t u;
      sscanf(buf, "sinh %" PRIx64, &u);
      u = d2u(xsinh(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "cosh ")) {
      uint64_t u;
      sscanf(buf, "cosh %" PRIx64, &u);
      u = d2u(xcosh(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "tanh ")) {
      uint64_t u;
      sscanf(buf, "tanh %" PRIx64, &u);
      u = d2u(xtanh(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "sinh_u35 ")) {
      uint64_t u;
      sscanf(buf, "sinh_u35 %" PRIx64, &u);
      u = d2u(xsinh_u35(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "cosh_u35 ")) {
      uint64_t u;
      sscanf(buf, "cosh_u35 %" PRIx64, &u);
      u = d2u(xcosh_u35(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "tanh_u35 ")) {
      uint64_t u;
      sscanf(buf, "tanh_u35 %" PRIx64, &u);
      u = d2u(xtanh_u35(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "asinh ")) {
      uint64_t u;
      sscanf(buf, "asinh %" PRIx64, &u);
      u = d2u(xasinh(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "acosh ")) {
      uint64_t u;
      sscanf(buf, "acosh %" PRIx64, &u);
      u = d2u(xacosh(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "atanh ")) {
      uint64_t u;
      sscanf(buf, "atanh %" PRIx64, &u);
      u = d2u(xatanh(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "fma ")) {
      uint64_t u, v, w;
      sscanf(buf, "fma %" PRIx64 " %" PRIx64 " %" PRIx64, &u, &v, &w);
      u = d2u(xfma(u2d(u), u2d(v), u2d(w)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "sqrt ")) {
      uint64_t u;
      sscanf(buf, "sqrt %" PRIx64, &u);
      u = d2u(xsqrt(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "sqrt_u05 ")) {
      uint64_t u;
      sscanf(buf, "sqrt_u05 %" PRIx64, &u);
      u = d2u(xsqrt_u05(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "sqrt_u35 ")) {
      uint64_t u;
      sscanf(buf, "sqrt_u35 %" PRIx64, &u);
      u = d2u(xsqrt_u35(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "cbrt ")) {
      uint64_t u;
      sscanf(buf, "cbrt %" PRIx64, &u);
      u = d2u(xcbrt(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "cbrt_u1 ")) {
      uint64_t u;
      sscanf(buf, "cbrt_u1 %" PRIx64, &u);
      u = d2u(xcbrt_u1(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "exp2 ")) {
      uint64_t u;
      sscanf(buf, "exp2 %" PRIx64, &u);
      u = d2u(xexp2(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "exp2_u35 ")) {
      uint64_t u;
      sscanf(buf, "exp2_u35 %" PRIx64, &u);
      u = d2u(xexp2_u35(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "exp10 ")) {
      uint64_t u;
      sscanf(buf, "exp10 %" PRIx64, &u);
      u = d2u(xexp10(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "exp10_u35 ")) {
      uint64_t u;
      sscanf(buf, "exp10_u35 %" PRIx64, &u);
      u = d2u(xexp10_u35(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "expm1 ")) {
      uint64_t u;
      sscanf(buf, "expm1 %" PRIx64, &u);
      u = d2u(xexpm1(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "log10 ")) {
      uint64_t u;
      sscanf(buf, "log10 %" PRIx64, &u);
      u = d2u(xlog10(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "log2 ")) {
      uint64_t u;
      sscanf(buf, "log2 %" PRIx64, &u);
      u = d2u(xlog2(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "log2_u35 ")) {
      uint64_t u;
      sscanf(buf, "log2_u35 %" PRIx64, &u);
      u = d2u(xlog2_u35(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "log1p ")) {
      uint64_t u;
      sscanf(buf, "log1p %" PRIx64, &u);
      u = d2u(xlog1p(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "ldexp ")) {
      uint64_t u, v;
      sscanf(buf, "ldexp %" PRIx64 " %" PRIx64, &u, &v);
      u = d2u(xldexp(u2d(u), (int)u2d(v)));
      printf("%" PRIx64 "\n", u);
    }

    else if (startsWith(buf, "hypot_u05 ")) {
      uint64_t u, v;
      sscanf(buf, "hypot_u05 %" PRIx64 " %" PRIx64, &u, &v);
      u = d2u(xhypot_u05(u2d(u), u2d(v)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "hypot_u35 ")) {
      uint64_t u, v;
      sscanf(buf, "hypot_u35 %" PRIx64 " %" PRIx64, &u, &v);
      u = d2u(xhypot_u35(u2d(u), u2d(v)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "copysign ")) {
      uint64_t u, v;
      sscanf(buf, "copysign %" PRIx64 " %" PRIx64, &u, &v);
      u = d2u(xcopysign(u2d(u), u2d(v)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "fmax ")) {
      uint64_t u, v;
      sscanf(buf, "fmax %" PRIx64 " %" PRIx64, &u, &v);
      u = d2u(xfmax(u2d(u), u2d(v)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "fmin ")) {
      uint64_t u, v;
      sscanf(buf, "fmin %" PRIx64 " %" PRIx64, &u, &v);
      u = d2u(xfmin(u2d(u), u2d(v)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "fdim ")) {
      uint64_t u, v;
      sscanf(buf, "fdim %" PRIx64 " %" PRIx64, &u, &v);
      u = d2u(xfdim(u2d(u), u2d(v)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "nextafter ")) {
      uint64_t u, v;
      sscanf(buf, "nextafter %" PRIx64 " %" PRIx64, &u, &v);
      u = d2u(xnextafter(u2d(u), u2d(v)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "fmod ")) {
      uint64_t u, v;
      sscanf(buf, "fmod %" PRIx64 " %" PRIx64, &u, &v);
      u = d2u(xfmod(u2d(u), u2d(v)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "remainder ")) {
      uint64_t u, v;
      sscanf(buf, "remainder %" PRIx64 " %" PRIx64, &u, &v);
      u = d2u(xremainder(u2d(u), u2d(v)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "fabs ")) {
      uint64_t u;
      sscanf(buf, "fabs %" PRIx64, &u);
      u = d2u(xfabs(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "trunc ")) {
      uint64_t u;
      sscanf(buf, "trunc %" PRIx64, &u);
      u = d2u(xtrunc(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "floor ")) {
      uint64_t u;
      sscanf(buf, "floor %" PRIx64, &u);
      u = d2u(xfloor(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "ceil ")) {
      uint64_t u;
      sscanf(buf, "ceil %" PRIx64, &u);
      u = d2u(xceil(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "round ")) {
      uint64_t u;
      sscanf(buf, "round %" PRIx64, &u);
      u = d2u(xround(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "rint ")) {
      uint64_t u;
      sscanf(buf, "rint %" PRIx64, &u);
      u = d2u(xrint(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "frfrexp ")) {
      uint64_t u;
      sscanf(buf, "frfrexp %" PRIx64, &u);
      u = d2u(xfrfrexp(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "modf ")) {
      uint64_t u;
      sscanf(buf, "modf %" PRIx64, &u);
      Sleef_double2 x = xmodf(u2d(u));
      printf("%" PRIx64 " %" PRIx64 "\n", d2u(x.x), d2u(x.y));
    } else if (startsWith(buf, "tgamma_u1 ")) {
      uint64_t u;
      sscanf(buf, "tgamma_u1 %" PRIx64, &u);
      u = d2u(xtgamma_u1(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "lgamma_u1 ")) {
      uint64_t u;
      sscanf(buf, "lgamma_u1 %" PRIx64, &u);
      u = d2u(xlgamma_u1(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "erf_u1 ")) {
      uint64_t u;
      sscanf(buf, "erf_u1 %" PRIx64, &u);
      u = d2u(xerf_u1(u2d(u)));
      printf("%" PRIx64 "\n", u);
    } else if (startsWith(buf, "erfc_u15 ")) {
      uint64_t u;
      sscanf(buf, "erfc_u15 %" PRIx64, &u);
      u = d2u(xerfc_u15(u2d(u)));
      printf("%" PRIx64 "\n", u);
    }

    else if (startsWith(buf, "sinf ")) {
      uint32_t u;
      sscanf(buf, "sinf %x", &u);
      u = f2u(xsinf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "cosf ")) {
      uint32_t u;
      sscanf(buf, "cosf %x", &u);
      u = f2u(xcosf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "sincosf ")) {
      uint32_t u;
      sscanf(buf, "sincosf %x", &u);
      Sleef_float2 x = xsincosf(u2f(u));
      printf("%x %x\n", f2u(x.x), f2u(x.y));
    } else if (startsWith(buf, "tanf ")) {
      uint32_t u;
      sscanf(buf, "tanf %x", &u);
      u = f2u(xtanf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "asinf ")) {
      uint32_t u;
      sscanf(buf, "asinf %x", &u);
      u = f2u(xasinf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "acosf ")) {
      uint32_t u;
      sscanf(buf, "acosf %x", &u);
      u = f2u(xacosf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "atanf ")) {
      uint32_t u;
      sscanf(buf, "atanf %x", &u);
      u = f2u(xatanf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "atan2f ")) {
      uint32_t u, v;
      sscanf(buf, "atan2f %x %x", &u, &v);
      u = f2u(xatan2f(u2f(u), u2f(v)));
      printf("%x\n", u);
    } else if (startsWith(buf, "logf ")) {
      uint32_t u;
      sscanf(buf, "logf %x", &u);
      u = f2u(xlogf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "expf ")) {
      uint32_t u;
      sscanf(buf, "expf %x", &u);
      u = f2u(xexpf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "cbrtf ")) {
      uint32_t u;
      sscanf(buf, "cbrtf %x", &u);
      u = f2u(xcbrtf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "sqrtf ")) {
      uint32_t u;
      sscanf(buf, "sqrtf %x", &u);
      u = f2u(xsqrtf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "sqrtf_u05 ")) {
      uint32_t u;
      sscanf(buf, "sqrtf_u05 %x", &u);
      u = f2u(xsqrtf_u05(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "sqrtf_u35 ")) {
      uint32_t u;
      sscanf(buf, "sqrtf_u35 %x", &u);
      u = f2u(xsqrtf_u35(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "ldexpf ")) {
      uint32_t u, v;
      sscanf(buf, "ldexpf %x %x", &u, &v);
      u = f2u(xldexpf(u2f(u), (int)u2f(v)));
      printf("%x\n", u);
    } else if (startsWith(buf, "powf ")) {
      uint32_t u, v;
      sscanf(buf, "powf %x %x", &u, &v);
      u = f2u(xpowf(u2f(u), u2f(v)));
      printf("%x\n", u);
    } else if (startsWith(buf, "fastpowf_u3500 ")) {
      uint32_t u, v;
      sscanf(buf, "fastpowf_u3500 %x %x", &u, &v);
      u = f2u(xfastpowf_u3500(u2f(u), u2f(v)));
      printf("%x\n", u);
    } else if (startsWith(buf, "sinhf ")) {
      uint32_t u;
      sscanf(buf, "sinhf %x", &u);
      u = f2u(xsinhf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "coshf ")) {
      uint32_t u;
      sscanf(buf, "coshf %x", &u);
      u = f2u(xcoshf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "tanhf ")) {
      uint32_t u;
      sscanf(buf, "tanhf %x", &u);
      u = f2u(xtanhf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "sinhf_u35 ")) {
      uint32_t u;
      sscanf(buf, "sinhf_u35 %x", &u);
      u = f2u(xsinhf_u35(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "coshf_u35 ")) {
      uint32_t u;
      sscanf(buf, "coshf_u35 %x", &u);
      u = f2u(xcoshf_u35(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "tanhf_u35 ")) {
      uint32_t u;
      sscanf(buf, "tanhf_u35 %x", &u);
      u = f2u(xtanhf_u35(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "asinhf ")) {
      uint32_t u;
      sscanf(buf, "asinhf %x", &u);
      u = f2u(xasinhf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "acoshf ")) {
      uint32_t u;
      sscanf(buf, "acoshf %x", &u);
      u = f2u(xacoshf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "atanhf ")) {
      uint32_t u;
      sscanf(buf, "atanhf %x", &u);
      u = f2u(xatanhf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "exp2f ")) {
      uint32_t u;
      sscanf(buf, "exp2f %x", &u);
      u = f2u(xexp2f(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "exp10f ")) {
      uint32_t u;
      sscanf(buf, "exp10f %x", &u);
      u = f2u(xexp10f(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "exp2f_u35 ")) {
      uint32_t u;
      sscanf(buf, "exp2f_u35 %x", &u);
      u = f2u(xexp2f_u35(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "exp10f_u35 ")) {
      uint32_t u;
      sscanf(buf, "exp10f_u35 %x", &u);
      u = f2u(xexp10f_u35(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "expm1f ")) {
      uint32_t u;
      sscanf(buf, "expm1f %x", &u);
      u = f2u(xexpm1f(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "log10f ")) {
      uint32_t u;
      sscanf(buf, "log10f %x", &u);
      u = f2u(xlog10f(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "log2f ")) {
      uint32_t u;
      sscanf(buf, "log2f %x", &u);
      u = f2u(xlog2f(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "log2f_u35 ")) {
      uint32_t u;
      sscanf(buf, "log2f_u35 %x", &u);
      u = f2u(xlog2f_u35(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "log1pf ")) {
      uint32_t u;
      sscanf(buf, "log1pf %x", &u);
      u = f2u(xlog1pf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "sinf_u1 ")) {
      uint32_t u;
      sscanf(buf, "sinf_u1 %x", &u);
      u = f2u(xsinf_u1(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "cosf_u1 ")) {
      uint32_t u;
      sscanf(buf, "cosf_u1 %x", &u);
      u = f2u(xcosf_u1(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "sincosf_u1 ")) {
      uint32_t u;
      sscanf(buf, "sincosf_u1 %x", &u);
      Sleef_float2 x = xsincosf_u1(u2f(u));
      printf("%x %x\n", f2u(x.x), f2u(x.y));
    } else if (startsWith(buf, "sincospif_u05 ")) {
      uint32_t u;
      sscanf(buf, "sincospif_u05 %x", &u);
      Sleef_float2 x = xsincospif_u05(u2f(u));
      printf("%x %x\n", f2u(x.x), f2u(x.y));
    } else if (startsWith(buf, "sincospif_u35 ")) {
      uint32_t u;
      sscanf(buf, "sincospif_u35 %x", &u);
      Sleef_float2 x = xsincospif_u35(u2f(u));
      printf("%x %x\n", f2u(x.x), f2u(x.y));
    } else if (startsWith(buf, "sinpif_u05 ")) {
      uint32_t u;
      sscanf(buf, "sinpif_u05 %x", &u);
      u = f2u(xsinpif_u05(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "cospif_u05 ")) {
      uint32_t u;
      sscanf(buf, "cospif_u05 %x", &u);
      u = f2u(xcospif_u05(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "fastsinf_u3500 ")) {
      uint32_t u;
      sscanf(buf, "fastsinf_u3500 %x", &u);
      u = f2u(xfastsinf_u3500(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "fastcosf_u3500 ")) {
      uint32_t u;
      sscanf(buf, "fastcosf_u3500 %x", &u);
      u = f2u(xfastcosf_u3500(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "tanf_u1 ")) {
      uint32_t u;
      sscanf(buf, "tanf_u1 %x", &u);
      u = f2u(xtanf_u1(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "asinf_u1 ")) {
      uint32_t u;
      sscanf(buf, "asinf_u1 %x", &u);
      u = f2u(xasinf_u1(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "acosf_u1 ")) {
      uint32_t u;
      sscanf(buf, "acosf_u1 %x", &u);
      u = f2u(xacosf_u1(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "atanf_u1 ")) {
      uint32_t u;
      sscanf(buf, "atanf_u1 %x", &u);
      u = f2u(xatanf_u1(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "atan2f_u1 ")) {
      uint32_t u, v;
      sscanf(buf, "atan2f_u1 %x %x", &u, &v);
      u = f2u(xatan2f_u1(u2f(u), u2f(v)));
      printf("%x\n", u);
    } else if (startsWith(buf, "logf_u1 ")) {
      uint32_t u;
      sscanf(buf, "logf_u1 %x", &u);
      u = f2u(xlogf_u1(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "cbrtf_u1 ")) {
      uint32_t u;
      sscanf(buf, "cbrtf_u1 %x", &u);
      u = f2u(xcbrtf_u1(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "ilogb ")) {
      uint64_t u;
      int i;
      sscanf(buf, "ilogb %" PRIx64, &u);
      i = xilogb(u2d(u));
      printf("%d\n", i);
    } else if (startsWith(buf, "ilogbf ")) {
      uint32_t u;
      int i;
      sscanf(buf, "ilogbf %x", &u);
      i = xilogbf(u2f(u));
      printf("%d\n", i);
    }

    else if (startsWith(buf, "hypotf_u05 ")) {
      uint32_t u, v;
      sscanf(buf, "hypotf_u05 %x %x", &u, &v);
      u = f2u(xhypotf_u05(u2f(u), u2f(v)));
      printf("%x\n", u);
    } else if (startsWith(buf, "hypotf_u35 ")) {
      uint32_t u, v;
      sscanf(buf, "hypotf_u35 %x %x", &u, &v);
      u = f2u(xhypotf_u35(u2f(u), u2f(v)));
      printf("%x\n", u);
    } else if (startsWith(buf, "copysignf ")) {
      uint32_t u, v;
      sscanf(buf, "copysignf %x %x", &u, &v);
      u = f2u(xcopysignf(u2f(u), u2f(v)));
      printf("%x\n", u);
    } else if (startsWith(buf, "fmaxf ")) {
      uint32_t u, v;
      sscanf(buf, "fmaxf %x %x", &u, &v);
      u = f2u(xfmaxf(u2f(u), u2f(v)));
      printf("%x\n", u);
    } else if (startsWith(buf, "fminf ")) {
      uint32_t u, v;
      sscanf(buf, "fminf %x %x", &u, &v);
      u = f2u(xfminf(u2f(u), u2f(v)));
      printf("%x\n", u);
    } else if (startsWith(buf, "fdimf ")) {
      uint32_t u, v;
      sscanf(buf, "fdimf %x %x", &u, &v);
      u = f2u(xfdimf(u2f(u), u2f(v)));
      printf("%x\n", u);
    } else if (startsWith(buf, "nextafterf ")) {
      uint32_t u, v;
      sscanf(buf, "nextafterf %x %x", &u, &v);
      u = f2u(xnextafterf(u2f(u), u2f(v)));
      printf("%x\n", u);
    } else if (startsWith(buf, "fmodf ")) {
      uint32_t u, v;
      sscanf(buf, "fmodf %x %x", &u, &v);
      u = f2u(xfmodf(u2f(u), u2f(v)));
      printf("%x\n", u);
    } else if (startsWith(buf, "remainderf ")) {
      uint32_t u, v;
      sscanf(buf, "remainderf %x %x", &u, &v);
      u = f2u(xremainderf(u2f(u), u2f(v)));
      printf("%x\n", u);
    } else if (startsWith(buf, "fabsf ")) {
      uint32_t u;
      sscanf(buf, "fabsf %x", &u);
      u = f2u(xfabsf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "truncf ")) {
      uint32_t u;
      sscanf(buf, "truncf %x", &u);
      u = f2u(xtruncf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "floorf ")) {
      uint32_t u;
      sscanf(buf, "floorf %x", &u);
      u = f2u(xfloorf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "ceilf ")) {
      uint32_t u;
      sscanf(buf, "ceilf %x", &u);
      u = f2u(xceilf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "roundf ")) {
      uint32_t u;
      sscanf(buf, "roundf %x", &u);
      u = f2u(xroundf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "rintf ")) {
      uint32_t u;
      sscanf(buf, "rintf %x", &u);
      u = f2u(xrintf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "frfrexpf ")) {
      uint32_t u;
      sscanf(buf, "frfrexpf %x", &u);
      u = f2u(xfrfrexpf(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "modff ")) {
      uint32_t u;
      sscanf(buf, "modff %x", &u);
      Sleef_float2 x = xmodff(u2f(u));
      printf("%x %x\n", f2u(x.x), f2u(x.y));
    } else if (startsWith(buf, "tgammaf_u1 ")) {
      uint32_t u;
      sscanf(buf, "tgammaf_u1 %x", &u);
      u = f2u(xtgammaf_u1(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "lgammaf_u1 ")) {
      uint32_t u;
      sscanf(buf, "lgammaf_u1 %x", &u);
      u = f2u(xlgammaf_u1(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "erff_u1 ")) {
      uint32_t u;
      sscanf(buf, "erff_u1 %x", &u);
      u = f2u(xerff_u1(u2f(u)));
      printf("%x\n", u);
    } else if (startsWith(buf, "erfcf_u15 ")) {
      uint32_t u;
      sscanf(buf, "erfcf_u15 %x", &u);
      u = f2u(xerfcf_u15(u2f(u)));
      printf("%x\n", u);
    }

    else {
      break;
    }

    fflush(stdout);
  }

  return 0;
}
