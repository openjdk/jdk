//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

// This define is needed to prevent the `execvpe` function to raise a
// warning at compile time. For more information, see
// https://linux.die.net/man/3/execvp.
#define _GNU_SOURCE

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <float.h>
#include <limits.h>
#include <errno.h>
#include <inttypes.h>
#include <sys/wait.h>

#include <math.h>
#include <mpfr.h>

#include <unistd.h>
#include <assert.h>
#include <sys/types.h>

#include "misc.h"
#include "testerutil.h"

#ifndef NANf
#define NANf ((float)NAN)
#endif

void stop(char *mes) {
  fprintf(stderr, "%s\n", mes);
  exit(-1);
}

int ptoc[2], ctop[2];
int pid;
FILE *fpctop;

extern char **environ;

void startChild(const char *path, char *const argv[]) {
  pipe(ptoc);
  pipe(ctop);

  pid = fork();

  assert(pid != -1);

  if (pid == 0) {
    // child process
    char buf0[1], buf1[1];

    close(ptoc[1]);
    close(ctop[0]);

    fflush(stdin);
    fflush(stdout);

    if (dup2(ptoc[0], fileno(stdin)) == -1) exit(-1);
    if (dup2(ctop[1], fileno(stdout)) == -1) exit(-1);

    setvbuf(stdin, buf0, _IONBF,0);
    setvbuf(stdout, buf1, _IONBF,0);

    fflush(stdin);
    fflush(stdout);

#if !defined(__APPLE__) && !defined(__FreeBSD__)
    execvpe(path, argv, environ);
#else
    execvp(path, argv);
#endif

    fprintf(stderr, "execvp in startChild : %s\n", strerror(errno));

    exit(-1);
  }

  // parent process

  close(ptoc[0]);
  close(ctop[1]);
}

//

#define child_d_d(funcStr, arg) do {                                    \
    char str[256];                                                      \
    uint64_t u;                                                         \
    sprintf(str, funcStr " %" PRIx64 "\n", d2u(arg));                   \
    write(ptoc[1], str, strlen(str));                                   \
    if (fgets(str, 255, fpctop) == NULL) stop("child " funcStr);        \
    sscanf(str, "%" PRIx64, &u);                                        \
    return u2d(u);                                                      \
  } while(0)

#define child_d2_d(funcStr, arg) do {                                   \
    char str[256];                                                      \
    uint64_t u, v;                                                      \
    sprintf(str, funcStr " %" PRIx64 "\n", d2u(arg));                   \
    write(ptoc[1], str, strlen(str));                                   \
    if (fgets(str, 255, fpctop) == NULL) stop("child " funcStr);        \
    sscanf(str, "%" PRIx64 " %" PRIx64, &u, &v);                        \
    Sleef_double2 ret;                                                  \
    ret.x = u2d(u);                                                     \
    ret.y = u2d(v);                                                     \
    return ret;                                                         \
  } while(0)

#define child_d_d_d(funcStr, arg1, arg2) do {                           \
    char str[256];                                                      \
    uint64_t u;                                                         \
    sprintf(str, funcStr " %" PRIx64 " %" PRIx64 "\n", d2u(arg1), d2u(arg2)); \
    write(ptoc[1], str, strlen(str));                                   \
    if (fgets(str, 255, fpctop) == NULL) stop("child " funcStr);        \
    sscanf(str, "%" PRIx64, &u);                                        \
    return u2d(u);                                                      \
  } while(0)

double child_sin(double x) { child_d_d("sin", x); }
double child_cos(double x) { child_d_d("cos", x); }
double child_tan(double x) { child_d_d("tan", x); }
double child_asin(double x) { child_d_d("asin", x); }
double child_acos(double x) { child_d_d("acos", x); }
double child_atan(double x) { child_d_d("atan", x); }
double child_log(double x) { child_d_d("log", x); }
double child_exp(double x) { child_d_d("exp", x); }
double child_cbrt(double x) { child_d_d("cbrt", x); }
double child_atan2(double y, double x) { child_d_d_d("atan2", y, x); }
Sleef_double2 child_sincos(double x) { child_d2_d("sincos", x); }

double child_sin_u1(double x) { child_d_d("sin_u1", x); }
double child_cos_u1(double x) { child_d_d("cos_u1", x); }
double child_tan_u1(double x) { child_d_d("tan_u1", x); }
double child_asin_u1(double x) { child_d_d("asin_u1", x); }
double child_acos_u1(double x) { child_d_d("acos_u1", x); }
double child_atan_u1(double x) { child_d_d("atan_u1", x); }
double child_log_u1(double x) { child_d_d("log_u1", x); }
double child_exp_u1(double x) { child_d_d("exp_u1", x); }
double child_cbrt_u1(double x) { child_d_d("cbrt_u1", x); }
double child_atan2_u1(double y, double x) { child_d_d_d("atan2_u1", y, x); }
Sleef_double2 child_sincos_u1(double x) { child_d2_d("sincos_u1", x); }

double child_pow(double x, double y) { child_d_d_d("pow", x, y); }
double child_sqrt(double x) { child_d_d("sqrt", x); }
double child_sqrt_u05(double x) { child_d_d("sqrt_u05", x); }
double child_sqrt_u35(double x) { child_d_d("sqrt_u35", x); }

double child_sinh(double x) { child_d_d("sinh", x); }
double child_cosh(double x) { child_d_d("cosh", x); }
double child_tanh(double x) { child_d_d("tanh", x); }
double child_sinh_u35(double x) { child_d_d("sinh_u35", x); }
double child_cosh_u35(double x) { child_d_d("cosh_u35", x); }
double child_tanh_u35(double x) { child_d_d("tanh_u35", x); }
double child_asinh(double x) { child_d_d("asinh", x); }
double child_acosh(double x) { child_d_d("acosh", x); }
double child_atanh(double x) { child_d_d("atanh", x); }

double child_log10(double x) { child_d_d("log10", x); }
double child_log2(double x) { child_d_d("log2", x); }
double child_log2_u35(double x) { child_d_d("log2_u35", x); }
double child_log1p(double x) { child_d_d("log1p", x); }
double child_exp2(double x) { child_d_d("exp2", x); }
double child_exp10(double x) { child_d_d("exp10", x); }
double child_exp2_u35(double x) { child_d_d("exp2_u35", x); }
double child_exp10_u35(double x) { child_d_d("exp10_u35", x); }
double child_expm1(double x) { child_d_d("expm1", x); }

Sleef_double2 child_sincospi_u05(double x) { child_d2_d("sincospi_u05", x); }
Sleef_double2 child_sincospi_u35(double x) { child_d2_d("sincospi_u35", x); }
double child_sinpi_u05(double x) { child_d_d("sinpi_u05", x); }
double child_cospi_u05(double x) { child_d_d("cospi_u05", x); }

double child_hypot_u05(double x, double y) { child_d_d_d("hypot_u05", x, y); }
double child_hypot_u35(double x, double y) { child_d_d_d("hypot_u35", x, y); }
double child_copysign(double x, double y) { child_d_d_d("copysign", x, y); }
double child_fmax(double x, double y) { child_d_d_d("fmax", x, y); }
double child_fmin(double x, double y) { child_d_d_d("fmin", x, y); }
double child_fdim(double x, double y) { child_d_d_d("fdim", x, y); }
double child_nextafter(double x, double y) { child_d_d_d("nextafter", x, y); }
double child_fmod(double x, double y) { child_d_d_d("fmod", x, y); }
double child_remainder(double x, double y) { child_d_d_d("remainder", x, y); }
double child_fabs(double x) { child_d_d("fabs", x); }
double child_trunc(double x) { child_d_d("trunc", x); }
double child_floor(double x) { child_d_d("floor", x); }
double child_ceil(double x) { child_d_d("ceil", x); }
double child_round(double x) { child_d_d("round", x); }
double child_rint(double x) { child_d_d("rint", x); }
double child_frfrexp(double x) { child_d_d("frfrexp", x); }
Sleef_double2 child_modf(double x) { child_d2_d("modf", x); }
double child_tgamma_u1(double x) { child_d_d("tgamma_u1", x); }
double child_lgamma_u1(double x) { child_d_d("lgamma_u1", x); }
double child_erf_u1(double x) { child_d_d("erf_u1", x); }
double child_erfc_u15(double x) { child_d_d("erfc_u15", x); }

//

double child_ldexp(double x, int q) {
  char str[256];
  uint64_t u;

  sprintf(str, "ldexp %" PRIx64 " %" PRIx64 "\n", d2u(x), d2u(q));
  write(ptoc[1], str, strlen(str));
  if (fgets(str, 255, fpctop) == NULL) stop("child_ldexp");
  sscanf(str, "%" PRIx64, &u);
  return u2d(u);
}

int child_ilogb(double x) {
  char str[256];
  int i;

  sprintf(str, "ilogb %" PRIx64 "\n", d2u(x));
  write(ptoc[1], str, strlen(str));
  if (fgets(str, 255, fpctop) == NULL) stop("child_ilogb");
  sscanf(str, "%d", &i);
  return i;
}

//

#define child_f_f(funcStr, arg) do {                                    \
    char str[256];                                                      \
    uint32_t u;                                                         \
    sprintf(str, funcStr " %x\n", f2u(arg));                            \
    write(ptoc[1], str, strlen(str));                                   \
    if (fgets(str, 255, fpctop) == NULL) stop("child " funcStr);        \
    sscanf(str, "%x", &u);                                              \
    return u2f(u);                                                      \
  } while(0)

#define child_f2_f(funcStr, arg) do {                                   \
    char str[256];                                                      \
    uint32_t u, v;                                                      \
    sprintf(str, funcStr " %x\n", f2u(arg));                            \
    write(ptoc[1], str, strlen(str));                                   \
    if (fgets(str, 255, fpctop) == NULL) stop("child " funcStr);        \
    sscanf(str, "%x %x", &u, &v);                                       \
    Sleef_float2 ret;                                                   \
    ret.x = u2f(u);                                                     \
    ret.y = u2f(v);                                                     \
    return ret;                                                         \
  } while(0)

#define child_f_f_f(funcStr, arg1, arg2) do {                           \
    char str[256];                                                      \
    uint32_t u;                                                         \
    sprintf(str, funcStr " %x %x\n", f2u(arg1), f2u(arg2));             \
    write(ptoc[1], str, strlen(str));                                   \
    if (fgets(str, 255, fpctop) == NULL) stop("child " funcStr);        \
    sscanf(str, "%x", &u);                                              \
    return u2f(u);                                                      \
  } while(0)

float child_sinf(float x) { child_f_f("sinf", x); }
float child_cosf(float x) { child_f_f("cosf", x); }
float child_tanf(float x) { child_f_f("tanf", x); }
float child_asinf(float x) { child_f_f("asinf", x); }
float child_acosf(float x) { child_f_f("acosf", x); }
float child_atanf(float x) { child_f_f("atanf", x); }
float child_logf(float x) { child_f_f("logf", x); }
float child_expf(float x) { child_f_f("expf", x); }
float child_cbrtf(float x) { child_f_f("cbrtf", x); }
float child_atan2f(float y, float x) { child_f_f_f("atan2f", y, x); }
Sleef_float2 child_sincosf(float x) { child_f2_f("sincosf", x); }

float child_sinf_u1(float x) { child_f_f("sinf_u1", x); }
float child_cosf_u1(float x) { child_f_f("cosf_u1", x); }
float child_tanf_u1(float x) { child_f_f("tanf_u1", x); }
float child_asinf_u1(float x) { child_f_f("asinf_u1", x); }
float child_acosf_u1(float x) { child_f_f("acosf_u1", x); }
float child_atanf_u1(float x) { child_f_f("atanf_u1", x); }
float child_logf_u1(float x) { child_f_f("logf_u1", x); }
float child_expf_u1(float x) { child_f_f("expf_u1", x); }
float child_cbrtf_u1(float x) { child_f_f("cbrtf_u1", x); }
float child_atan2f_u1(float y, float x) { child_f_f_f("atan2f_u1", y, x); }
Sleef_float2 child_sincosf_u1(float x) { child_f2_f("sincosf_u1", x); }

float child_powf(float x, float y) { child_f_f_f("powf", x, y); }
float child_sqrtf(float x) { child_f_f("sqrtf", x); }
float child_sqrtf_u05(float x) { child_f_f("sqrtf_u05", x); }
float child_sqrtf_u35(float x) { child_f_f("sqrtf_u35", x); }

float child_sinhf(float x) { child_f_f("sinhf", x); }
float child_coshf(float x) { child_f_f("coshf", x); }
float child_tanhf(float x) { child_f_f("tanhf", x); }
float child_sinhf_u35(float x) { child_f_f("sinhf_u35", x); }
float child_coshf_u35(float x) { child_f_f("coshf_u35", x); }
float child_tanhf_u35(float x) { child_f_f("tanhf_u35", x); }
float child_asinhf(float x) { child_f_f("asinhf", x); }
float child_acoshf(float x) { child_f_f("acoshf", x); }
float child_atanhf(float x) { child_f_f("atanhf", x); }

float child_log10f(float x) { child_f_f("log10f", x); }
float child_log2f(float x) { child_f_f("log2f", x); }
float child_log2f_u35(float x) { child_f_f("log2f_u35", x); }
float child_log1pf(float x) { child_f_f("log1pf", x); }
float child_exp2f(float x) { child_f_f("exp2f", x); }
float child_exp10f(float x) { child_f_f("exp10f", x); }
float child_exp2f_u35(float x) { child_f_f("exp2f_u35", x); }
float child_exp10f_u35(float x) { child_f_f("exp10f_u35", x); }
float child_expm1f(float x) { child_f_f("expm1f", x); }

Sleef_float2 child_sincospif_u05(float x) { child_f2_f("sincospif_u05", x); }
Sleef_float2 child_sincospif_u35(float x) { child_f2_f("sincospif_u35", x); }
float child_sinpif_u05(float x) { child_f_f("sinpif_u05", x); }
float child_cospif_u05(float x) { child_f_f("cospif_u05", x); }

float child_hypotf_u05(float x, float y) { child_f_f_f("hypotf_u05", x, y); }
float child_hypotf_u35(float x, float y) { child_f_f_f("hypotf_u35", x, y); }
float child_copysignf(float x, float y) { child_f_f_f("copysignf", x, y); }
float child_fmaxf(float x, float y) { child_f_f_f("fmaxf", x, y); }
float child_fminf(float x, float y) { child_f_f_f("fminf", x, y); }
float child_fdimf(float x, float y) { child_f_f_f("fdimf", x, y); }
float child_nextafterf(float x, float y) { child_f_f_f("nextafterf", x, y); }
float child_fmodf(float x, float y) { child_f_f_f("fmodf", x, y); }
float child_remainderf(float x, float y) { child_f_f_f("remainderf", x, y); }
float child_fabsf(float x) { child_f_f("fabsf", x); }
float child_truncf(float x) { child_f_f("truncf", x); }
float child_floorf(float x) { child_f_f("floorf", x); }
float child_ceilf(float x) { child_f_f("ceilf", x); }
float child_roundf(float x) { child_f_f("roundf", x); }
float child_rintf(float x) { child_f_f("rintf", x); }
float child_frfrexpf(float x) { child_f_f("frfrexpf", x); }
Sleef_float2 child_modff(float x) { child_f2_f("modff", x); }
float child_tgammaf_u1(float x) { child_f_f("tgammaf_u1", x); }
float child_lgammaf_u1(float x) { child_f_f("lgammaf_u1", x); }
float child_erff_u1(float x) { child_f_f("erff_u1", x); }
float child_erfcf_u15(float x) { child_f_f("erfcf_u15", x); }

float child_fastsinf_u3500(float x) { child_f_f("fastsinf_u3500", x); }
float child_fastcosf_u3500(float x) { child_f_f("fastcosf_u3500", x); }
float child_fastpowf_u3500(float x, float y) { child_f_f_f("fastpowf_u3500", x, y); }

float child_ldexpf(float x, int q) {
  char str[256];
  uint32_t u;

  sprintf(str, "ldexpf %x %x\n", f2u(x), f2u(q));
  write(ptoc[1], str, strlen(str));
  if (fgets(str, 255, fpctop) == NULL) stop("child_powf");
  sscanf(str, "%x", &u);
  return u2f(u);
}

int child_ilogbf(float x) {
  char str[256];
  int i;

  sprintf(str, "ilogbf %x\n", f2u(x));
  write(ptoc[1], str, strlen(str));
  if (fgets(str, 255, fpctop) == NULL) stop("child_ilogbf");
  sscanf(str, "%d", &i);
  return i;
}

//

int allTestsPassed = 1;

void showResult(int success) {
  if (!success) allTestsPassed = 0;
  fprintf(stderr, "%s\n", success ? "OK" : "NG **************");

  if (!success) {
    fprintf(stderr, "\n\n*** Test failed\n");
    exit(-1);
  }
}

int enableDP = 0, enableSP = 0, deterministicMode = 0;

void do_test() {
  mpfr_t frc, frt, frx, fry, frz;
  mpfr_inits(frc, frt, frx, fry, frz, NULL);

  int i, j;
  int64_t i64;
  double d, x, y;
  int success = 1;

  if (enableDP) {
    fprintf(stderr, "Denormal/nonnumber test atan2(y, x)\n\n");

    fprintf(stderr, "If y is +0 and x is -0, +pi is returned : ");
    showResult(child_atan2(+0.0, -0.0) == M_PI);

    fprintf(stderr, "If y is -0 and x is -0, -pi is returned : ");
    showResult(child_atan2(-0.0, -0.0) == -M_PI);

    fprintf(stderr, "If y is +0 and x is +0, +0 is returned : ");
    showResult(isPlusZero(child_atan2(+0.0, +0.0)));

    fprintf(stderr, "If y is -0 and x is +0, -0 is returned : ");
    showResult(isMinusZero(child_atan2(-0.0, +0.0)));

    fprintf(stderr, "If y is positive infinity and x is negative infinity, +3*pi/4 is returned : ");
    showResult(child_atan2(POSITIVE_INFINITY, NEGATIVE_INFINITY) == 3*M_PI/4);

    fprintf(stderr, "If y is negative infinity and x is negative infinity, -3*pi/4 is returned : ");
    showResult(child_atan2(NEGATIVE_INFINITY, NEGATIVE_INFINITY) == -3*M_PI/4);

    fprintf(stderr, "If y is positive infinity and x is positive infinity, +pi/4 is returned : ");
    showResult(child_atan2(POSITIVE_INFINITY, POSITIVE_INFINITY) == M_PI/4);

    fprintf(stderr, "If y is negative infinity and x is positive infinity, -pi/4 is returned : ");
    showResult(child_atan2(NEGATIVE_INFINITY, POSITIVE_INFINITY) == -M_PI/4);

    {
      fprintf(stderr, "If y is +0 and x is less than 0, +pi is returned : ");

      double ya[] = { +0.0 };
      double xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2(ya[j], xa[i]) != M_PI) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is -0 and x is less than 0, -pi is returned : ");

      double ya[] = { -0.0 };
      double xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2(ya[j], xa[i]) != -M_PI) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is less than 0 and x is 0, -pi/2 is returned : ");

      double ya[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };
      double xa[] = { +0.0, -0.0 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2(ya[j], xa[i]) != -M_PI/2) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is greater than 0 and x is 0, pi/2 is returned : ");

      double ya[] = { 100000.5, 100000, 3, 2.5, 2, 1.5, 1.0, 0.5 };
      double xa[] = { +0.0, -0.0 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2(ya[j], xa[i]) != M_PI/2) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is greater than 0 and x is -0, pi/2 is returned : ");

      double ya[] = { 100000.5, 100000, 3, 2.5, 2, 1.5, 1.0, 0.5 };
      double xa[] = { -0.0 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2(ya[j], xa[i]) != M_PI/2) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is positive infinity, and x is finite, pi/2 is returned : ");

      double ya[] = { POSITIVE_INFINITY };
      double xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2(ya[j], xa[i]) != M_PI/2) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is negative infinity, and x is finite, -pi/2 is returned : ");

      double ya[] = { NEGATIVE_INFINITY };
      double xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2(ya[j], xa[i]) != -M_PI/2) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value greater than 0, and x is negative infinity, +pi is returned : ");

      double ya[] = { 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };
      double xa[] = { NEGATIVE_INFINITY };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2(ya[j], xa[i]) != M_PI) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value less than 0, and x is negative infinity, -pi is returned : ");

      double ya[] = { -0.5, -1.5, -2.0, -2.5, -3.0, -100000, -100000.5 };
      double xa[] = { NEGATIVE_INFINITY };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2(ya[j], xa[i]) != -M_PI) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value greater than 0, and x is positive infinity, +0 is returned : ");

      double ya[] = { 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };
      double xa[] = { POSITIVE_INFINITY };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!isPlusZero(child_atan2(ya[j], xa[i]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value less than 0, and x is positive infinity, -0 is returned : ");

      double ya[] = { -0.5, -1.5, -2.0, -2.5, -3.0, -100000, -100000.5 };
      double xa[] = { POSITIVE_INFINITY };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!isMinusZero(child_atan2(ya[j], xa[i]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is NaN, a NaN is returned : ");

      double ya[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5, NAN };
      double xa[] = { NAN };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!xisnan(child_atan2(ya[j], xa[i]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a NaN, the result is a NaN : ");

      double ya[] = { NAN };
      double xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5, NAN };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!xisnan(child_atan2(ya[j], xa[i]))) success = 0;
        }
      }

      showResult(success);
    }

    fprintf(stderr, "\nend of atan2 denormal/nonnumber test\n");

    //

    fprintf(stderr, "\nDenormal/nonnumber test atan2_u1(y, x)\n\n");

    fprintf(stderr, "If y is +0 and x is -0, +pi is returned : ");
    showResult(child_atan2_u1(+0.0, -0.0) == M_PI);

    fprintf(stderr, "If y is -0 and x is -0, -pi is returned : ");
    showResult(child_atan2_u1(-0.0, -0.0) == -M_PI);

    fprintf(stderr, "If y is +0 and x is +0, +0 is returned : ");
    showResult(isPlusZero(child_atan2_u1(+0.0, +0.0)));

    fprintf(stderr, "If y is -0 and x is +0, -0 is returned : ");
    showResult(isMinusZero(child_atan2_u1(-0.0, +0.0)));

    fprintf(stderr, "If y is positive infinity and x is negative infinity, +3*pi/4 is returned : ");
    showResult(child_atan2_u1(POSITIVE_INFINITY, NEGATIVE_INFINITY) == 3*M_PI/4);

    fprintf(stderr, "If y is negative infinity and x is negative infinity, -3*pi/4 is returned : ");
    showResult(child_atan2_u1(NEGATIVE_INFINITY, NEGATIVE_INFINITY) == -3*M_PI/4);

    fprintf(stderr, "If y is positive infinity and x is positive infinity, +pi/4 is returned : ");
    showResult(child_atan2_u1(POSITIVE_INFINITY, POSITIVE_INFINITY) == M_PI/4);

    fprintf(stderr, "If y is negative infinity and x is positive infinity, -pi/4 is returned : ");
    showResult(child_atan2_u1(NEGATIVE_INFINITY, POSITIVE_INFINITY) == -M_PI/4);

    {
      fprintf(stderr, "If y is +0 and x is less than 0, +pi is returned : ");

      double ya[] = { +0.0 };
      double xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2_u1(ya[j], xa[i]) != M_PI) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is -0 and x is less than 0, -pi is returned : ");

      double ya[] = { -0.0 };
      double xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2_u1(ya[j], xa[i]) != -M_PI) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is less than 0 and x is 0, -pi/2 is returned : ");

      double ya[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };
      double xa[] = { +0.0, -0.0 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2_u1(ya[j], xa[i]) != -M_PI/2) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is greater than 0 and x is 0, pi/2 is returned : ");


      double ya[] = { 100000.5, 100000, 3, 2.5, 2, 1.5, 1.0, 0.5 };
      double xa[] = { +0.0, -0.0 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2_u1(ya[j], xa[i]) != M_PI/2) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is greater than 0 and x is -0, pi/2 is returned : ");

      double ya[] = { 100000.5, 100000, 3, 2.5, 2, 1.5, 1.0, 0.5 };
      double xa[] = { -0.0 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2_u1(ya[j], xa[i]) != M_PI/2) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is positive infinity, and x is finite, pi/2 is returned : ");

      double ya[] = { POSITIVE_INFINITY };
      double xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2_u1(ya[j], xa[i]) != M_PI/2) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is negative infinity, and x is finite, -pi/2 is returned : ");

      double ya[] = { NEGATIVE_INFINITY };
      double xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2_u1(ya[j], xa[i]) != -M_PI/2) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value greater than 0, and x is negative infinity, +pi is returned : ");

      double ya[] = { 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };
      double xa[] = { NEGATIVE_INFINITY };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2_u1(ya[j], xa[i]) != M_PI) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value less than 0, and x is negative infinity, -pi is returned : ");

      double ya[] = { -0.5, -1.5, -2.0, -2.5, -3.0, -100000, -100000.5 };
      double xa[] = { NEGATIVE_INFINITY };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_atan2_u1(ya[j], xa[i]) != -M_PI) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value greater than 0, and x is positive infinity, +0 is returned : ");

      double ya[] = { 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };
      double xa[] = { POSITIVE_INFINITY };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!isPlusZero(child_atan2_u1(ya[j], xa[i]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value less than 0, and x is positive infinity, -0 is returned : ");

      double ya[] = { -0.5, -1.5, -2.0, -2.5, -3.0, -100000, -100000.5 };
      double xa[] = { POSITIVE_INFINITY };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!isMinusZero(child_atan2_u1(ya[j], xa[i]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is NaN, a NaN is returned : ");

      double ya[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5, NAN };
      double xa[] = { NAN };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!xisnan(child_atan2_u1(ya[j], xa[i]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a NaN, the result is a NaN : ");

      double ya[] = { NAN };
      double xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5, NAN };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!xisnan(child_atan2_u1(ya[j], xa[i]))) success = 0;
        }
      }

      showResult(success);
    }

    fprintf(stderr, "\nend of atan2_u1 denormal/nonnumber test\n");

    //

    fprintf(stderr, "\nDenormal/nonnumber test pow(x, y)\n\n");

    fprintf(stderr, "If x is +1 and y is a NaN, the result is 1.0 : ");
    showResult(child_pow(1, NAN) == 1.0);

    fprintf(stderr, "If y is 0 and x is a NaN, the result is 1.0 : ");
    showResult(child_pow(NAN, 0) == 1.0);

    fprintf(stderr, "If x is -1, and y is positive infinity, the result is 1.0 : ");
    showResult(child_pow(-1, POSITIVE_INFINITY) == 1.0);

    fprintf(stderr, "If x is -1, and y is negative infinity, the result is 1.0 : ");
    showResult(child_pow(-1, NEGATIVE_INFINITY) == 1.0);

    {
      fprintf(stderr, "If x is a finite value less than 0, and y is a finite non-integer, a NaN is returned : ");

      double xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };
      double ya[] = { -100000.5, -2.5, -1.5, -0.5, 0.5, 1.5, 2.5, 100000.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!xisnan(child_pow(xa[i], ya[j]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is a NaN, the result is a NaN : ");

      double xa[] = { NAN };
      double ya[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!xisnan(child_pow(xa[i], ya[j]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a NaN, the result is a NaN : ");

      double xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };
      double ya[] = { NAN };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!xisnan(child_pow(xa[i], ya[j]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is +0, and y is an odd integer greater than 0, the result is +0 : ");

      double xa[] = { +0.0 };
      double ya[] = { 1, 3, 5, 7, 100001 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!isPlusZero(child_pow(xa[i], ya[j]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is -0, and y is an odd integer greater than 0, the result is -0 : ");

      double xa[] = { -0.0 };
      double ya[] = { 1, 3, 5, 7, 100001 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          double test = child_pow(xa[i], ya[j]);
          if (!isMinusZero(test)) {
            fprintf(stderr, "arg = %.20g, %.20g, test = %.20g, correct = %.20g\n", xa[i], ya[j], test, -0.0);
            success = 0;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is 0, and y greater than 0 and not an odd integer, the result is +0 : ");

      double xa[] = { +0.0, -0.0 };
      double ya[] = { 0.5, 1.5, 2.0, 2.5, 4.0, 100000, 100000.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!isPlusZero(child_pow(xa[i], ya[j]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If the absolute value of x is less than 1, and y is negative infinity, the result is positive infinity : ");

      double xa[] = { -0.999, -0.5, -0.0, +0.0, +0.5, +0.999 };
      double ya[] = { NEGATIVE_INFINITY };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_pow(xa[i], ya[j]) != POSITIVE_INFINITY) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If the absolute value of x is greater than 1, and y is negative infinity, the result is +0 : ");

      double xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };
      double ya[] = { NEGATIVE_INFINITY };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!isPlusZero(child_pow(xa[i], ya[j]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If the absolute value of x is less than 1, and y is positive infinity, the result is +0 : ");

      double xa[] = { -0.999, -0.5, -0.0, +0.0, +0.5, +0.999 };
      double ya[] = { POSITIVE_INFINITY };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!isPlusZero(child_pow(xa[i], ya[j]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If the absolute value of x is greater than 1, and y is positive infinity, the result is positive infinity : ");

      double xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };
      double ya[] = { POSITIVE_INFINITY };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_pow(xa[i], ya[j]) != POSITIVE_INFINITY) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is negative infinity, and y is an odd integer less than 0, the result is -0 : ");

      double xa[] = { NEGATIVE_INFINITY };
      double ya[] = { -100001, -5, -3, -1 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!isMinusZero(child_pow(xa[i], ya[j]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is negative infinity, and y less than 0 and not an odd integer, the result is +0 : ");

      double xa[] = { NEGATIVE_INFINITY };
      double ya[] = { -100000.5, -100000, -4, -2.5, -2, -1.5, -0.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!isPlusZero(child_pow(xa[i], ya[j]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is negative infinity, and y is an odd integer greater than 0, the result is negative infinity : ");

      double xa[] = { NEGATIVE_INFINITY };
      double ya[] = { 1, 3, 5, 7, 100001 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_pow(xa[i], ya[j]) != NEGATIVE_INFINITY) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is negative infinity, and y greater than 0 and not an odd integer, the result is positive infinity : ");

      double xa[] = { NEGATIVE_INFINITY };
      double ya[] = { 0.5, 1.5, 2, 2.5, 3.5, 4, 100000, 100000.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_pow(xa[i], ya[j]) != POSITIVE_INFINITY) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is positive infinity, and y less than 0, the result is +0 : ");

      double xa[] = { POSITIVE_INFINITY };
      double ya[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (!isPlusZero(child_pow(xa[i], ya[j]))) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is positive infinity, and y greater than 0, the result is positive infinity : ");

      double xa[] = { POSITIVE_INFINITY };
      double ya[] = { 0.5, 1, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_pow(xa[i], ya[j]) != POSITIVE_INFINITY) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is +0, and y is an odd integer less than 0, +HUGE_VAL is returned : ");

      double xa[] = { +0.0 };
      double ya[] = { -100001, -5, -3, -1 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_pow(xa[i], ya[j]) != POSITIVE_INFINITY) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is -0, and y is an odd integer less than 0, -HUGE_VAL is returned : ");

      double xa[] = { -0.0 };
      double ya[] = { -100001, -5, -3, -1 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_pow(xa[i], ya[j]) != NEGATIVE_INFINITY) success = 0;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is 0, and y is less than 0 and not an odd integer, +HUGE_VAL is returned : ");

      double xa[] = { +0.0, -0.0 };
      double ya[] = { -100000.5, -100000, -4, -2.5, -2, -1.5, -0.5 };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (child_pow(xa[i], ya[j]) != POSITIVE_INFINITY) success = 0;
        }
      }

      showResult(success);
    }
  }

  //

#define cmpDenorm_f(mpfrFunc, childFunc, argx) do {                     \
    mpfr_set_d(frx, (float)flushToZero(argx), GMP_RNDN);                \
    mpfrFunc(frc, frx, GMP_RNDN);                                       \
    if (!cmpDenormsp(childFunc((float)flushToZero(argx)), frc)) {       \
      fprintf(stderr, "arg = %.20g, test = %.20g, correct = %.20g\n",   \
              (float)flushToZero(argx), childFunc((float)flushToZero(argx)), flushToZero(mpfr_get_d(frc, GMP_RNDN))); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

#define cmpDenormNR_f(mpfrFunc, childFunc, argx) do {                   \
    mpfr_set_d(frx, (float)flushToZero(argx), GMP_RNDN);                \
    mpfrFunc(frc, frx);                                                 \
    if (!cmpDenormsp(childFunc((float)flushToZero(argx)), frc)) {       \
      fprintf(stderr, "arg = %.20g, test = %.20g, correct = %.20g\n",   \
              (float)flushToZero(argx), childFunc((float)flushToZero(argx)), mpfr_get_d(frc, GMP_RNDN)); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

#define cmpDenorm_f_f(mpfrFunc, childFunc, argx, argy) do {             \
    mpfr_set_d(frx, (float)flushToZero(argx), GMP_RNDN);                \
    mpfr_set_d(fry, (float)flushToZero(argy), GMP_RNDN);                \
    mpfrFunc(frc, frx, fry, GMP_RNDN);                                  \
    if (!cmpDenormsp(childFunc((float)flushToZero(argx), (float)flushToZero(argy)), frc)) { \
      fprintf(stderr, "arg = %.20g, %.20g, test = %.20g, correct = %.20g\n", \
              (float)flushToZero(argx), (float)flushToZero(argy), childFunc((float)flushToZero(argx), (float)flushToZero(argy)), mpfr_get_d(frc, GMP_RNDN)); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

#define cmpDenormX_f(mpfrFunc, childFunc, argx) do {                    \
    mpfr_set_d(frx, (float)flushToZero(argx), GMP_RNDN);                \
    mpfrFunc(frc, frx, GMP_RNDN);                                       \
    Sleef_float2 d2 = childFunc((float)flushToZero(argx));              \
    if (!cmpDenormsp(d2.x, frc)) {                                      \
      fprintf(stderr, "arg = %.20g, test = %.20g, correct = %.20g\n",   \
              (float)flushToZero(argx), d2.x, mpfr_get_d(frc, GMP_RNDN)); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

#define cmpDenormY_f(mpfrFunc, childFunc, argx) do {                    \
    mpfr_set_d(frx, (float)flushToZero(argx), GMP_RNDN);                \
    mpfrFunc(frc, frx, GMP_RNDN);                                       \
    Sleef_float2 d2 = childFunc((float)flushToZero(argx));              \
    if (!cmpDenormsp(d2.y, frc)) {                                      \
      fprintf(stderr, "arg = %.20g, test = %.20g, correct = %.20g\n",   \
              (float)flushToZero(argx), d2.y, mpfr_get_d(frc, GMP_RNDN)); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

  //

  if (enableSP) {
    fprintf(stderr, "\nDenormal/nonnumber test atan2f(y, x)\n\n");

    fprintf(stderr, "If y is +0 and x is -0, +pi is returned ... ");
    showResult(child_atan2f(+0.0, -0.0) == M_PIf);

    fprintf(stderr, "If y is -0 and x is -0, -pi is returned ... ");
    showResult(child_atan2f(-0.0, -0.0) == -M_PIf);

    fprintf(stderr, "If y is +0 and x is +0, +0 is returned ... ");
    showResult(isPlusZerof(child_atan2f(+0.0, +0.0)));

    fprintf(stderr, "If y is -0 and x is +0, -0 is returned ... ");
    showResult(isMinusZerof(child_atan2f(-0.0, +0.0)));

    fprintf(stderr, "If y is positive infinity and x is negative infinity, +3*pi/4 is returned ... ");
    showResult(child_atan2f(POSITIVE_INFINITYf, NEGATIVE_INFINITYf) == 3*M_PIf/4);

    fprintf(stderr, "If y is negative infinity and x is negative infinity, -3*pi/4 is returned ... ");
    showResult(child_atan2f(NEGATIVE_INFINITYf, NEGATIVE_INFINITYf) == -3*M_PIf/4);

    fprintf(stderr, "If y is positive infinity and x is positive infinity, +pi/4 is returned ... ");
    showResult(child_atan2f(POSITIVE_INFINITYf, POSITIVE_INFINITYf) == M_PIf/4);

    fprintf(stderr, "If y is negative infinity and x is positive infinity, -pi/4 is returned ... ");
    showResult(child_atan2f(NEGATIVE_INFINITYf, POSITIVE_INFINITYf) == -M_PIf/4);

    {
      fprintf(stderr, "If y is +0 and x is less than 0, +pi is returned ... ");

      float ya[] = { +0.0 };
      float xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };

      int success = 1;

      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f(ya[j], xa[i]) != M_PIf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is -0 and x is less than 0, -pi is returned ... ");

      float ya[] = { -0.0 };
      float xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f(ya[j], xa[i]) != -M_PIf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is less than 0 and x is 0, -pi/2 is returned ... ");

      float ya[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };
      float xa[] = { +0.0, -0.0 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f(ya[j], xa[i]) != -M_PIf/2) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is greater than 0 and x is 0, pi/2 is returned ... ");


      float ya[] = { 100000.5, 100000, 3, 2.5, 2, 1.5, 1.0, 0.5 };
      float xa[] = { +0.0, -0.0 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f(ya[j], xa[i]) != M_PIf/2) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is greater than 0 and x is -0, pi/2 is returned ... ");

      float ya[] = { 100000.5, 100000, 3, 2.5, 2, 1.5, 1.0, 0.5 };
      float xa[] = { -0.0 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f(ya[j], xa[i]) != M_PIf/2) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is positive infinity, and x is finite, pi/2 is returned ... ");

      float ya[] = { POSITIVE_INFINITYf };
      float xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f(ya[j], xa[i]) != M_PIf/2) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is negative infinity, and x is finite, -pi/2 is returned ... ");

      float ya[] = { NEGATIVE_INFINITYf };
      float xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f(ya[j], xa[i]) != -M_PIf/2) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value greater than 0, and x is negative infinity, +pi is returned ... ");

      float ya[] = { 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };
      float xa[] = { NEGATIVE_INFINITYf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f(ya[j], xa[i]) != M_PIf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value less than 0, and x is negative infinity, -pi is returned ... ");

      float ya[] = { -0.5, -1.5, -2.0, -2.5, -3.0, -100000, -100000.5 };
      float xa[] = { NEGATIVE_INFINITYf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f(ya[j], xa[i]) != -M_PIf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value greater than 0, and x is positive infinity, +0 is returned ... ");

      float ya[] = { 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };
      float xa[] = { POSITIVE_INFINITYf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!isPlusZerof(child_atan2f(ya[j], xa[i]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value less than 0, and x is positive infinity, -0 is returned ... ");

      float ya[] = { -0.5, -1.5, -2.0, -2.5, -3.0, -100000, -100000.5 };
      float xa[] = { POSITIVE_INFINITYf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!isMinusZerof(child_atan2f(ya[j], xa[i]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is NaN, a NaN is returned ... ");

      float ya[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5, NANf };
      float xa[] = { NANf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!xisnanf(child_atan2f(ya[j], xa[i]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a NaN, the result is a NaN ... ");

      float ya[] = { NANf };
      float xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5, NANf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!xisnanf(child_atan2f(ya[j], xa[i]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    fprintf(stderr, "\nend of atan2f denormal/nonnumber test\n\n");

    //

    fprintf(stderr, "\nDenormal/nonnumber test atan2f_u1(y, x)\n\n");

    fprintf(stderr, "If y is +0 and x is -0, +pi is returned ... ");
    showResult(child_atan2f_u1(+0.0, -0.0) == M_PIf);

    fprintf(stderr, "If y is -0 and x is -0, -pi is returned ... ");
    showResult(child_atan2f_u1(-0.0, -0.0) == -M_PIf);

    fprintf(stderr, "If y is +0 and x is +0, +0 is returned ... ");
    showResult(isPlusZerof(child_atan2f_u1(+0.0, +0.0)));

    fprintf(stderr, "If y is -0 and x is +0, -0 is returned ... ");
    showResult(isMinusZerof(child_atan2f_u1(-0.0, +0.0)));

    fprintf(stderr, "If y is positive infinity and x is negative infinity, +3*pi/4 is returned ... ");
    showResult(child_atan2f_u1(POSITIVE_INFINITYf, NEGATIVE_INFINITYf) == 3*M_PIf/4);

    fprintf(stderr, "If y is negative infinity and x is negative infinity, -3*pi/4 is returned ... ");
    showResult(child_atan2f_u1(NEGATIVE_INFINITYf, NEGATIVE_INFINITYf) == -3*M_PIf/4);

    fprintf(stderr, "If y is positive infinity and x is positive infinity, +pi/4 is returned ... ");
    showResult(child_atan2f_u1(POSITIVE_INFINITYf, POSITIVE_INFINITYf) == M_PIf/4);

    fprintf(stderr, "If y is negative infinity and x is positive infinity, -pi/4 is returned ... ");
    showResult(child_atan2f_u1(NEGATIVE_INFINITYf, POSITIVE_INFINITYf) == -M_PIf/4);

    {
      fprintf(stderr, "If y is +0 and x is less than 0, +pi is returned ... ");

      float ya[] = { +0.0 };
      float xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };

      int success = 1;

      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f_u1(ya[j], xa[i]) != M_PIf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is -0 and x is less than 0, -pi is returned ... ");

      float ya[] = { -0.0 };
      float xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f_u1(ya[j], xa[i]) != -M_PIf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is less than 0 and x is 0, -pi/2 is returned ... ");

      float ya[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };
      float xa[] = { +0.0, -0.0 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f_u1(ya[j], xa[i]) != -M_PIf/2) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is greater than 0 and x is 0, pi/2 is returned ... ");


      float ya[] = { 100000.5, 100000, 3, 2.5, 2, 1.5, 1.0, 0.5 };
      float xa[] = { +0.0, -0.0 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f_u1(ya[j], xa[i]) != M_PIf/2) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is greater than 0 and x is -0, pi/2 is returned ... ");

      float ya[] = { 100000.5, 100000, 3, 2.5, 2, 1.5, 1.0, 0.5 };
      float xa[] = { -0.0 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f_u1(ya[j], xa[i]) != M_PIf/2) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is positive infinity, and x is finite, pi/2 is returned ... ");

      float ya[] = { POSITIVE_INFINITYf };
      float xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f_u1(ya[j], xa[i]) != M_PIf/2) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is negative infinity, and x is finite, -pi/2 is returned ... ");

      float ya[] = { NEGATIVE_INFINITYf };
      float xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f_u1(ya[j], xa[i]) != -M_PIf/2) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value greater than 0, and x is negative infinity, +pi is returned ... ");

      float ya[] = { 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };
      float xa[] = { NEGATIVE_INFINITYf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f_u1(ya[j], xa[i]) != M_PIf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value less than 0, and x is negative infinity, -pi is returned ... ");

      float ya[] = { -0.5, -1.5, -2.0, -2.5, -3.0, -100000, -100000.5 };
      float xa[] = { NEGATIVE_INFINITYf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_atan2f_u1(ya[j], xa[i]) != -M_PIf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value greater than 0, and x is positive infinity, +0 is returned ... ");

      float ya[] = { 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };
      float xa[] = { POSITIVE_INFINITYf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!isPlusZerof(child_atan2f_u1(ya[j], xa[i]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a finite value less than 0, and x is positive infinity, -0 is returned ... ");

      float ya[] = { -0.5, -1.5, -2.0, -2.5, -3.0, -100000, -100000.5 };
      float xa[] = { POSITIVE_INFINITYf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!isMinusZerof(child_atan2f_u1(ya[j], xa[i]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is NaN, a NaN is returned ... ");

      float ya[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5, NANf };
      float xa[] = { NANf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!xisnanf(child_atan2f_u1(ya[j], xa[i]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a NaN, the result is a NaN ... ");

      float ya[] = { NANf };
      float xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5, NANf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!xisnanf(child_atan2f_u1(ya[j], xa[i]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    fprintf(stderr, "\nend of atan2f_u1 denormal/nonnumber test\n\n");

    //

    fprintf(stderr, "\nDenormal/nonnumber test powf(x, y)\n\n");

    fprintf(stderr, "If x is +1 and y is a NaN, the result is 1.0 ... ");
    showResult(child_powf(1, NANf) == 1.0);

    fprintf(stderr, "If y is 0 and x is a NaN, the result is 1.0 ... ");
    showResult(child_powf(NANf, 0) == 1.0);

    fprintf(stderr, "If x is -1, and y is positive infinity, the result is 1.0 ... ");
    showResult(child_powf(-1, POSITIVE_INFINITYf) == 1.0);

    fprintf(stderr, "If x is -1, and y is negative infinity, the result is 1.0 ... ");
    showResult(child_powf(-1, NEGATIVE_INFINITYf) == 1.0);

    {
      fprintf(stderr, "If x is a finite value less than 0, and y is a finite non-integer, a NaN is returned ... ");

      float xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };
      float ya[] = { -100000.5, -2.5, -1.5, -0.5, 0.5, 1.5, 2.5, 100000.5 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!xisnanf(child_powf(xa[i], ya[j]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is a NaN, the result is a NaN ... ");

      float xa[] = { NANf };
      float ya[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!xisnanf(child_powf(xa[i], ya[j]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If y is a NaN, the result is a NaN ... ");

      float xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5, -0.0, +0.0, 0.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };
      float ya[] = { NANf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!xisnanf(child_powf(xa[i], ya[j]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is +0, and y is an odd integer greater than 0, the result is +0 ... ");

      float xa[] = { +0.0 };
      float ya[] = { 1, 3, 5, 7, 100001 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!isPlusZerof(child_powf(xa[i], ya[j]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is -0, and y is an odd integer greater than 0, the result is -0 ... ");

      float xa[] = { -0.0 };
      float ya[] = { 1, 3, 5, 7, 100001 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!isMinusZerof(child_powf(xa[i], ya[j]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is 0, and y greater than 0 and not an odd integer, the result is +0 ... ");

      float xa[] = { +0.0, -0.0 };
      float ya[] = { 0.5, 1.5, 2.0, 2.5, 4.0, 100000, 100000.5 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!isPlusZerof(child_powf(xa[i], ya[j]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If the absolute value of x is less than 1, and y is negative infinity, the result is positive infinity ... ");

      float xa[] = { -0.999, -0.5, -0.0, +0.0, +0.5, +0.999 };
      float ya[] = { NEGATIVE_INFINITYf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_powf(xa[i], ya[j]) != POSITIVE_INFINITYf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If the absolute value of x is greater than 1, and y is negative infinity, the result is +0 ... ");

      float xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };
      float ya[] = { NEGATIVE_INFINITYf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!isPlusZerof(child_powf(xa[i], ya[j]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If the absolute value of x is less than 1, and y is positive infinity, the result is +0 ... ");

      float xa[] = { -0.999, -0.5, -0.0, +0.0, +0.5, +0.999 };
      float ya[] = { POSITIVE_INFINITYf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!isPlusZerof(child_powf(xa[i], ya[j]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If the absolute value of x is greater than 1, and y is positive infinity, the result is positive infinity ... ");

      float xa[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };
      float ya[] = { POSITIVE_INFINITYf };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_powf(xa[i], ya[j]) != POSITIVE_INFINITYf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is negative infinity, and y is an odd integer less than 0, the result is -0 ... ");

      float xa[] = { NEGATIVE_INFINITYf };
      float ya[] = { -100001, -5, -3, -1 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!isMinusZerof(child_powf(xa[i], ya[j]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is negative infinity, and y less than 0 and not an odd integer, the result is +0 ... ");

      float xa[] = { NEGATIVE_INFINITYf };
      float ya[] = { -100000.5, -100000, -4, -2.5, -2, -1.5, -0.5 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!isPlusZerof(child_powf(xa[i], ya[j]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is negative infinity, and y is an odd integer greater than 0, the result is negative infinity ... ");

      float xa[] = { NEGATIVE_INFINITYf };
      float ya[] = { 1, 3, 5, 7, 100001 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_powf(xa[i], ya[j]) != NEGATIVE_INFINITYf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is negative infinity, and y greater than 0 and not an odd integer, the result is positive infinity ... ");

      float xa[] = { NEGATIVE_INFINITYf };
      float ya[] = { 0.5, 1.5, 2, 2.5, 3.5, 4, 100000, 100000.5 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_powf(xa[i], ya[j]) != POSITIVE_INFINITYf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is positive infinity, and y less than 0, the result is +0 ... ");

      float xa[] = { POSITIVE_INFINITYf };
      float ya[] = { -100000.5, -100000, -3, -2.5, -2, -1.5, -1.0, -0.5 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (!isPlusZerof(child_powf(xa[i], ya[j]))) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is positive infinity, and y greater than 0, the result is positive infinity ... ");

      float xa[] = { POSITIVE_INFINITYf };
      float ya[] = { 0.5, 1, 1.5, 2.0, 2.5, 3.0, 100000, 100000.5 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_powf(xa[i], ya[j]) != POSITIVE_INFINITYf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is +0, and y is an odd integer less than 0, +HUGE_VAL is returned ... ");

      float xa[] = { +0.0 };
      float ya[] = { -100001, -5, -3, -1 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_powf(xa[i], ya[j]) != POSITIVE_INFINITYf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is -0, and y is an odd integer less than 0, -HUGE_VAL is returned ... ");

      float xa[] = { -0.0 };
      float ya[] = { -100001, -5, -3, -1 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_powf(xa[i], ya[j]) != NEGATIVE_INFINITYf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If x is 0, and y is less than 0 and not an odd integer, +HUGE_VAL is returned ... ");

      float xa[] = { +0.0, -0.0 };
      float ya[] = { -100000.5, -100000, -4, -2.5, -2, -1.5, -0.5 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          if (child_powf(xa[i], ya[j]) != POSITIVE_INFINITYf) {
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "If the result overflows, the functions return HUGE_VAL with the mathematically correct sign ... ");

      float xa[] = { 1000, -1000 };
      float ya[] = { 1000, 1000.5, 1001 };

      int success = 1;
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          cmpDenorm_f_f(mpfr_pow, child_powf, xa[i], ya[i]);
        }
      }

      showResult(success);
    }

    fprintf(stderr, "\nEnd of pow denormal/nonnumber test\n\n");
  }

  //

#define cmpDenorm_d(mpfrFunc, childFunc, argx) do {                     \
      mpfr_set_d(frx, argx, GMP_RNDN);                                  \
      mpfrFunc(frc, frx, GMP_RNDN);                                     \
      if (!cmpDenormdp(childFunc(argx), frc)) {                         \
        fprintf(stderr, "arg = %.20g, test = %.20g, correct = %.20g\n", argx, childFunc(argx), mpfr_get_d(frc, GMP_RNDN)); \
        success = 0;                                                    \
        break;                                                          \
      }                                                                 \
    } while(0)

#define cmpDenormNR_d(mpfrFunc, childFunc, argx) do {                   \
      mpfr_set_d(frx, argx, GMP_RNDN);                                  \
      mpfrFunc(frc, frx);                                               \
      if (!cmpDenormdp(childFunc(argx), frc)) {                         \
        fprintf(stderr, "arg = %.20g, test = %.20g, correct = %.20g\n", argx, childFunc(argx), mpfr_get_d(frc, GMP_RNDN)); \
        success = 0;                                                    \
        break;                                                          \
      }                                                                 \
    } while(0)

#define cmpDenorm_d_d(mpfrFunc, childFunc, argx, argy) do {             \
      mpfr_set_d(frx, argx, GMP_RNDN);                                  \
      mpfr_set_d(fry, argy, GMP_RNDN);                                  \
      mpfrFunc(frc, frx, fry, GMP_RNDN);                                \
      if (!cmpDenormdp(childFunc(argx, argy), frc)) {                   \
        fprintf(stderr, "arg = %.20g, %.20g, test = %.20g, correct = %.20g\n", argx, argy, childFunc(argx, argy), mpfr_get_d(frc, GMP_RNDN)); \
        success = 0;                                                    \
        break;                                                          \
      }                                                                 \
    } while(0)

#define cmpDenormX_d(mpfrFunc, childFunc, argx) do {                    \
      mpfr_set_d(frx, argx, GMP_RNDN);                                  \
      mpfrFunc(frc, frx, GMP_RNDN);                                     \
      Sleef_double2 d2 = childFunc(argx);                               \
      if (!cmpDenormdp(d2.x, frc)) {                                    \
        fprintf(stderr, "arg = %.20g, test = %.20g, correct = %.20g\n", argx, d2.x, mpfr_get_d(frc, GMP_RNDN)); \
        success = 0;                                                    \
        break;                                                          \
      }                                                                 \
    } while(0)

#define cmpDenormY_d(mpfrFunc, childFunc, argx) do {                    \
      mpfr_set_d(frx, argx, GMP_RNDN);                                  \
      mpfrFunc(frc, frx, GMP_RNDN);                                     \
      Sleef_double2 d2 = childFunc(argx);                               \
      if (!cmpDenormdp(d2.y, frc)) {                                    \
        fprintf(stderr, "arg = %.20g, test = %.20g, correct = %.20g\n", argx, d2.y, mpfr_get_d(frc, GMP_RNDN)); \
        success = 0;                                                    \
        break;                                                          \
      }                                                                 \
    } while(0)

  //

  if (enableDP) {
    {
      fprintf(stderr, "sin denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_sin, child_sin, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sin_u1 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_sin, child_sin_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sin in sincos denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenormX_d(mpfr_sin, child_sincos, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sin in sincos_u1 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenormX_d(mpfr_sin, child_sincos_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sin in sincospi_u05 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenormX_d(mpfr_sinpi, child_sincospi_u05, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sin in sincospi_u35 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenormX_d(mpfr_sinpi, child_sincospi_u35, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sinpi_u05 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_sinpi, child_sinpi_u05, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cospi_u05 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_cospi, child_cospi_u05, xa[i]);
      showResult(success);
    }

    //

    {
      fprintf(stderr, "cos denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_cos, child_cos, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cos_u1 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_cos, child_cos_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cos in sincos denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenormY_d(mpfr_cos, child_sincos, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cos in sincos_u1 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenormY_d(mpfr_cos, child_sincos_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cos in sincospi_u05 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenormY_d(mpfr_cospi, child_sincospi_u05, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cos in sincospi_u35 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenormY_d(mpfr_cospi, child_sincospi_u35, xa[i]);
      showResult(success);
    }

    //

    {
      fprintf(stderr, "tan denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN, M_PI/2, -M_PI/2 };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_tan, child_tan, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "tan_u1 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN, M_PI/2, -M_PI/2 };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_tan, child_tan_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "asin denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN,
                      POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN, nextafter(1, 2), nextafter(-1, -2) };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_asin, child_asin, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "asin_u1 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN,
                      POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN, nextafter(1, 2), nextafter(-1, -2) };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_asin, child_asin_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "acos denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN,
                      POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN, nextafter(1, 2), nextafter(-1, -2) };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_acos, child_acos, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "acos_u1 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN,
                      POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN, nextafter(1, 2), nextafter(-1, -2) };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_acos, child_acos_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "atan denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_atan, child_atan, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "atan_u1 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_atan, child_atan_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "log denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN, nextafter(0, -1) };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_log, child_log, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "log_u1 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN, nextafter(0, -1) };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_log, child_log_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "exp denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_exp, child_exp, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sinh denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_sinh, child_sinh, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cosh denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_cosh, child_cosh, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "tanh denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_tanh, child_tanh, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sinh_u35 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_sinh, child_sinh_u35, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cosh_u35 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_cosh, child_cosh_u35, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "tanh_u35 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_tanh, child_tanh_u35, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "asinh denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_asinh, child_asinh, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "acosh denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_acosh, child_acosh, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "atanh denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_atanh, child_atanh, xa[i]);
      showResult(success);
    }

    if (!deterministicMode) {
      fprintf(stderr, "sqrt denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_sqrt, child_sqrt, xa[i]);
      showResult(success);
    }

    if (!deterministicMode) {
      fprintf(stderr, "sqrt_u05 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_sqrt, child_sqrt_u05, xa[i]);
      showResult(success);
    }

    if (!deterministicMode) {
      fprintf(stderr, "sqrt_u35 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_sqrt, child_sqrt_u35, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cbrt denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_cbrt, child_cbrt, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cbrt_u1 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_cbrt, child_cbrt_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "exp2 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_exp2, child_exp2, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "exp10 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_exp10, child_exp10, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "exp2_u35 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_exp2, child_exp2_u35, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "exp10_u35 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_exp10, child_exp10_u35, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "expm1 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_expm1, child_expm1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "log10 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_log10, child_log10, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "log2 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_log2, child_log2, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "log2_u35 denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_log2, child_log2_u35, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "log1p denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN, nextafter(-1, -2), -2 };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_log1p, child_log1p, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "ldexp denormal/nonnumber test : ");

      for(i=-10000;i<=10000 && success;i++) {
        d = child_ldexp(1.0, i);
        mpfr_set_d(frx, 1.0, GMP_RNDN);
        mpfr_set_exp(frx, mpfr_get_exp(frx) + i);
        double c = mpfr_get_d(frx, GMP_RNDN);

        if (c != d) {
          fprintf(stderr, "arg = %.20g, correct = %.20g, test = %.20g\n", (double)i, c, d);
          success = 0;
          break;
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "ilogb test : ");

      double xa[] = { POSITIVE_INFINITY, NEGATIVE_INFINITY, -1, };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        if (child_ilogb(xa[i]) != ilogb(xa[i])) {
          fprintf(stderr, "arg = %.20g, correct = %d, test = %d\n", xa[i], ilogb(xa[i]), child_ilogb(xa[i]));
          success = 0;
          break;
        }
      }

      {
        int t = child_ilogb(NAN);
        if (t != INT_MAX && t != INT_MIN) success = 0;
      }

      {
        int t = child_ilogb(0);
        if (t != INT_MIN && t != -INT_MAX) success = 0;
      }

      showResult(success);
    }

    {
      fprintf(stderr, "nextafter test : ");

      double xa[] = { NEGATIVE_INFINITY, -DBL_MAX, -1, -DBL_MIN, -SLEEF_DBL_DENORM_MIN, -0, +0, SLEEF_DBL_DENORM_MIN, DBL_MIN, 1 , DBL_MAX, POSITIVE_INFINITY, NAN };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(xa)/sizeof(double) && success;j++) {
          double t = child_nextafter(xa[i], xa[j]), c = nextafter(xa[i], xa[j]);
          if (!((t != 0 && !isnan(t) && !isnan(c) && t == c) || (t == 0 && c == 0 && signbit(t) == signbit(c)) || (isnan(t) && isnan(c)))) {
            fprintf(stderr, "arg = %.20g, %.20g, correct = %.20g, test = %.20g\n", xa[i], xa[j], c, t);
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "hypot_u35 denormal/nonnumber test : ");

      double xa[] = { +0.0, -0.0, +1, -1, +1e+100, -1e+100, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      double ya[] = { +0.0, -0.0, +1, -1, +1e+100, -1e+100, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          cmpDenorm_d_d(mpfr_hypot, child_hypot_u35, xa[i], ya[j]);
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "hypot_u05 denormal/nonnumber test : ");

      double xa[] = { +0.0, -0.0, +1, -1, +1e+100, -1e+100, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      double ya[] = { +0.0, -0.0, +1, -1, +1e+100, -1e+100, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          cmpDenorm_d_d(mpfr_hypot, child_hypot_u05, xa[i], ya[j]);
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "copysign denormal/nonnumber test : ");

      double xa[] = { +0.0, -0.0, +1, -1, +1e+100, -1e+100, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY };
      double ya[] = { +0.0, -0.0, +1, -1, +1e+100, -1e+100, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          cmpDenorm_d_d(mpfr_copysign, child_copysign, xa[i], ya[j]);
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "fmax denormal/nonnumber test : ");

      double xa[] = { +0.0, +1, -1, +1e+100, -1e+100, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN, SLEEF_SNAN };
      double ya[] = { +0.0, +1, -1, +1e+100, -1e+100, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN, SLEEF_SNAN };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          cmpDenorm_d_d(mpfr_max, child_fmax, xa[i], ya[j]);
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "fmin denormal/nonnumber test : ");

      double xa[] = { +0.0, +1, -1, +1e+100, -1e+100, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN, SLEEF_SNAN };
      double ya[] = { +0.0, +1, -1, +1e+100, -1e+100, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN, SLEEF_SNAN };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          cmpDenorm_d_d(mpfr_min, child_fmin, xa[i], ya[j]);
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "fdim denormal/nonnumber test : ");

      double xa[] = { +0.0, -0.0, +1, -1, +1e+100, -1e+100, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      double ya[] = { +0.0, -0.0, +1, -1, +1e+100, -1e+100, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          cmpDenorm_d_d(mpfr_dim, child_fdim, xa[i], ya[j]);
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "fmod denormal/nonnumber test : ");

      double xa[] = { +0.0, -0.0, +1, -1, +1e+100, -1e+100, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      double ya[] = { +0.0, -0.0, +1, -1, +1e+100, -1e+100, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (fabs(xa[i] / ya[j]) > 1e+300) continue;
          cmpDenorm_d_d(mpfr_fmod, child_fmod, xa[i], ya[j]);
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "remainder denormal/nonnumber test : ");

      double xa[] = { +0.0, -0.0, +1, -1, +1e+100, -1e+100, 1.7e+308, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      double ya[] = { +0.0, -0.0, +1, -1, +1e+100, -1e+100, 1.0e+308, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };

      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(double) && success;j++) {
          if (fabs(xa[i] / ya[j]) > 1e+300) continue;
          cmpDenorm_d_d(mpfr_remainder, child_remainder, xa[i], ya[j]);
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "trunc denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenormNR_d(mpfr_trunc, child_trunc, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "floor denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenormNR_d(mpfr_floor, child_floor, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "ceil denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenormNR_d(mpfr_ceil, child_ceil, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "round denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenormNR_d(mpfr_round, child_round, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "rint denormal/nonnumber test : ");
      double xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, DBL_MAX, -DBL_MAX, DBL_MIN, -DBL_MIN, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_rint, child_rint, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "lgamma_u1 denormal/nonnumber test : ");
      double xa[] = { -4, -3, -2, -1, +0.0, -0.0, +1, +2, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_lgamma_nosign, child_lgamma_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "tgamma_u1 denormal/nonnumber test : ");
      double xa[] = { -4, -3, -2, -1, +0.0, -0.0, +1, +2, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_gamma, child_tgamma_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "erf_u1 denormal/nonnumber test : ");
      double xa[] = { -1, +0.0, -0.0, +1, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_erf, child_erf_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "erfc_u15 denormal/nonnumber test : ");
      double xa[] = { -1, +0.0, -0.0, +1, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(double) && success;i++) cmpDenorm_d(mpfr_erfc, child_erfc_u15, xa[i]);
      showResult(success);
    }
  }

  if (enableSP) {
    {
      fprintf(stderr, "sinf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_sin, child_sinf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sinf_u1 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_sin, child_sinf_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sin in sincosf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenormX_f(mpfr_sin, child_sincosf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sin in sincosf_u1 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenormX_f(mpfr_sin, child_sincosf_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sin in sincospif_u05 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenormX_f(mpfr_sinpi, child_sincospif_u05, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sin in sincospif_u35 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenormX_f(mpfr_sinpi, child_sincospif_u35, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sinpif_u05 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_sinpi, child_sinpif_u05, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cospif_u05 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_cospi, child_cospif_u05, xa[i]);
      showResult(success);
    }

    //

    {
      fprintf(stderr, "cosf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_cos, child_cosf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cosf_u1 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_cos, child_cosf_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cosf in sincos denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenormY_f(mpfr_cos, child_sincosf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cosf in sincos_u1 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenormY_f(mpfr_cos, child_sincosf_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cosf in sincospif_u05 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenormY_f(mpfr_cospi, child_sincospif_u05, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cosf in sincospif_u35 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenormY_f(mpfr_cospi, child_sincospif_u35, xa[i]);
      showResult(success);
    }

    //

    {
      fprintf(stderr, "tanf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, M_PI/2, -M_PI/2 };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_tan, child_tanf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "tanf_u1 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, M_PI/2, -M_PI/2 };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_tan, child_tanf_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "asinf denormal/nonnumber test : ");
      if (enableFlushToZero) {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX,
                       POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, nextafterf(1, 2), nextafterf(-1, -2) };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_asin, child_asinf, xa[i]);
      } else {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN,
                       POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, nextafterf(1, 2), nextafterf(-1, -2) };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_asin, child_asinf, xa[i]);
      }
      showResult(success);
    }

    {
      fprintf(stderr, "asinf_u1 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN,
                     POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, nextafterf(1, 2), nextafterf(-1, -2) };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_asin, child_asinf_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "acosf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN,
                     POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, nextafterf(1, 2), nextafterf(-1, -2) };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_acos, child_acosf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "acosf_u1 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN,
                     POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, nextafterf(1, 2), nextafterf(-1, -2) };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_acos, child_acosf_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "atanf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_atan, child_atanf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "atanf_u1 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_atan, child_atanf_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "logf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, nextafterf(0, -1) };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_log, child_logf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "logf_u1 denormal/nonnumber test : ");
      if (enableFlushToZero) {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, nextafterf(0, -1) };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_log, child_logf_u1, xa[i]);
      } else {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, nextafterf(0, -1) };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_log, child_logf_u1, xa[i]);
      }
      showResult(success);
    }

    {
      fprintf(stderr, "expf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_exp, child_expf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sinhf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_sinh, child_sinhf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "coshf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_cosh, child_coshf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "tanhf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_tanh, child_tanhf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "sinhf_u35 denormal/nonnumber test : ");
      if (enableFlushToZero) {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_sinh, child_sinhf_u35, xa[i]);
      } else {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_sinh, child_sinhf_u35, xa[i]);
      }
      showResult(success);
    }

    {
      fprintf(stderr, "coshf_u35 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_cosh, child_coshf_u35, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "tanhf_u35 denormal/nonnumber test : ");
      if (enableFlushToZero) {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_tanh, child_tanhf_u35, xa[i]);
      } else {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_tanh, child_tanhf_u35, xa[i]);
      }
      showResult(success);
    }

    {
      fprintf(stderr, "asinhf denormal/nonnumber test : ");
      if (enableFlushToZero) {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_asinh, child_asinhf, xa[i]);
      } else {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_asinh, child_asinhf, xa[i]);
      }
      showResult(success);
    }

    {
      fprintf(stderr, "acoshf denormal/nonnumber test : ");
      if (enableFlushToZero) {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_acosh, child_acoshf, xa[i]);
      } else {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_acosh, child_acoshf, xa[i]);
      }
      showResult(success);
    }

    {
      fprintf(stderr, "atanhf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_atanh, child_atanhf, xa[i]);
      showResult(success);
    }

    if (!deterministicMode) {
      fprintf(stderr, "sqrtf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_sqrt, child_sqrtf, xa[i]);
      showResult(success);
    }

    if (!deterministicMode) {
      fprintf(stderr, "sqrtf_u05 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_sqrt, child_sqrtf_u05, xa[i]);
      showResult(success);
    }

    if (!deterministicMode) {
      fprintf(stderr, "sqrtf_u35 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_sqrt, child_sqrtf_u35, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cbrtf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_cbrt, child_cbrtf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "cbrtf_u1 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_cbrt, child_cbrtf_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "exp2f denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_exp2, child_exp2f, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "exp10f denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_exp10, child_exp10f, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "exp2f_u35 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_exp2, child_exp2f_u35, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "exp10f_u35 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_exp10, child_exp10f_u35, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "expm1f denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_expm1, child_expm1f, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "log10f denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_log10, child_log10f, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "log2f denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_log2, child_log2f, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "log2f_u35 denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_log2, child_log2f_u35, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "log1pf denormal/nonnumber test : ");
      if (enableFlushToZero) {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, nextafterf(-1, -2), -2 };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_log1p, child_log1pf, xa[i]);
      } else {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+7, -1e+7, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, nextafterf(-1, -2), -2 };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_log1p, child_log1pf, xa[i]);
      }
      showResult(success);
    }

    {
      fprintf(stderr, "hypotf_u35 denormal/nonnumber test : ");

      if (enableFlushToZero) {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        float ya[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
          for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
            cmpDenorm_f_f(mpfr_hypot, child_hypotf_u35, xa[i], ya[j]);
          }
        }
      } else {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        float ya[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
          for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
            cmpDenorm_f_f(mpfr_hypot, child_hypotf_u35, xa[i], ya[j]);
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "hypotf_u05 denormal/nonnumber test : ");

      if (enableFlushToZero) {
        float xa[] = { +0.0, -0.0, +1, -1, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        float ya[] = { +0.0, -0.0, +1, -1, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
          for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
            cmpDenorm_f_f(mpfr_hypot, child_hypotf_u05, xa[i], ya[j]);
          }
        }
      } else {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        float ya[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
          for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
            cmpDenorm_f_f(mpfr_hypot, child_hypotf_u05, xa[i], ya[j]);
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "copysignf denormal/nonnumber test : ");

      float xa[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf };
      float ya[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf };

      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          cmpDenorm_f_f(mpfr_copysign, child_copysignf, xa[i], ya[j]);
        }
      }

      showResult(success);
    }

    if (!enableFlushToZero) {
      fprintf(stderr, "nextafterf test : ");

      float xa[] = { NEGATIVE_INFINITY, -FLT_MAX, -1, -FLT_MIN, -SLEEF_FLT_DENORM_MIN, -0, +0, SLEEF_FLT_DENORM_MIN, FLT_MIN, 1 , FLT_MAX, POSITIVE_INFINITY, NAN };

      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(xa)/sizeof(float) && success;j++) {
          float t = child_nextafterf(xa[i], xa[j]), c = nextafterf(xa[i], xa[j]);
          if (!((t != 0 && !isnan(t) && !isnan(c) && t == c) || (t == 0 && c == 0 && signbit(t) == signbit(c)) || (isnan(t) && isnan(c)))) {
            fprintf(stderr, "arg = %.20g, %.20g, correct = %.20g, test = %.20g\n", xa[i], xa[j], c, t);
            success = 0;
            break;
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "fmaxf denormal/nonnumber test : ");

      float xa[] = { +0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, SLEEF_SNANf };
      float ya[] = { +0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, SLEEF_SNANf };

      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          cmpDenorm_f_f(mpfr_max, child_fmaxf, xa[i], ya[j]);
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "fminf denormal/nonnumber test : ");

      float xa[] = { +0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, SLEEF_SNANf };
      float ya[] = { +0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN, SLEEF_SNANf };

      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          cmpDenorm_f_f(mpfr_min, child_fminf, xa[i], ya[j]);
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "fdimf denormal/nonnumber test : ");

      float xa[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      float ya[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };

      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
        for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
          cmpDenorm_f_f(mpfr_dim, child_fdimf, xa[i], ya[j]);
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "fmodf denormal/nonnumber test : ");

      if (enableFlushToZero) {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        float ya[] = { +0.0, -0.0, +1, -1, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
          for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
            if (fabs(xa[i] / ya[j]) > 1e+38) continue;
            cmpDenorm_f_f(mpfr_fmod, child_fmodf, xa[i], ya[j]);
          }
        }
      } else {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        float ya[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
          for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
            if (fabs(xa[i] / ya[j]) > 1e+38) continue;
            cmpDenorm_f_f(mpfr_fmod, child_fmodf, xa[i], ya[j]);
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "remainderf denormal/nonnumber test : ");

      if (enableFlushToZero) {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        float ya[] = { +0.0, -0.0, +1, -1, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
          for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
            if (fabs(xa[i] / ya[j]) > 1e+38) continue;
            cmpDenorm_f_f(mpfr_remainder, child_remainderf, xa[i], ya[j]);
          }
        }
      } else {
        float xa[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        float ya[] = { +0.0, -0.0, +1, -1, +1e+30, -1e+30, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
        for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) {
          for(j=0;j<sizeof(ya)/sizeof(float) && success;j++) {
            if (fabs(xa[i] / ya[j]) > 1e+38) continue;
            cmpDenorm_f_f(mpfr_remainder, child_remainderf, xa[i], ya[j]);
          }
        }
      }

      showResult(success);
    }

    {
      fprintf(stderr, "truncf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenormNR_f(mpfr_trunc, child_truncf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "floorf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenormNR_f(mpfr_floor, child_floorf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "ceilf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenormNR_f(mpfr_ceil, child_ceilf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "roundf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenormNR_f(mpfr_round, child_roundf, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "rintf denormal/nonnumber test : ");
      float xa[] = { +0.0, -0.0, +1, -1, +1e+10, -1e+10, FLT_MAX, -FLT_MAX, FLT_MIN, -FLT_MIN, POSITIVE_INFINITYf, NEGATIVE_INFINITYf, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_rint, child_rintf, xa[i]);
      showResult(success);
    }

    //

    {
      fprintf(stderr, "lgammaf_u1 denormal/nonnumber test : ");
      float xa[] = { -4, -3, -2, -1, +0.0, -0.0, +1, +2, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_lgamma_nosign, child_lgammaf_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "tgammaf_u1 denormal/nonnumber test : ");
      float xa[] = { -4, -3, -2, -1, +0.0, -0.0, +1, +2, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_gamma, child_tgammaf_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "erff_u1 denormal/nonnumber test : ");
      float xa[] = { -1, +0.0, -0.0, +1, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_erf, child_erff_u1, xa[i]);
      showResult(success);
    }

    {
      fprintf(stderr, "erfcf_u15 denormal/nonnumber test : ");
      float xa[] = { -1, +0.0, -0.0, +1, +1e+10, -1e+10, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN };
      for(i=0;i<sizeof(xa)/sizeof(float) && success;i++) cmpDenorm_f(mpfr_erfc, child_erfcf_u15, xa[i]);
      showResult(success);
    }
  }

  //

#define checkAccuracy_d(mpfrFunc, childFunc, argx, bound) do {          \
    mpfr_set_d(frx, argx, GMP_RNDN);                                    \
    mpfrFunc(frc, frx, GMP_RNDN);                                       \
    if (countULPdp(childFunc(argx), frc) > bound) {                     \
      fprintf(stderr, "\narg = %.20g, test = %.20g, correct = %.20g, ULP = %lf\n", argx, childFunc(argx), mpfr_get_d(frc, GMP_RNDN), countULPdp(childFunc(argx), frc)); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

#define checkAccuracyNR_d(mpfrFunc, childFunc, argx, bound) do {        \
    mpfr_set_d(frx, argx, GMP_RNDN);                                    \
    mpfrFunc(frc, frx);                                                 \
    if (countULPdp(childFunc(argx), frc) > bound) {                     \
      fprintf(stderr, "\narg = %.20g, test = %.20g, correct = %.20g, ULP = %lf\n", argx, childFunc(argx), mpfr_get_d(frc, GMP_RNDN), countULPdp(childFunc(argx), frc)); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

#define checkAccuracy_d_d(mpfrFunc, childFunc, argx, argy, bound) do {  \
    mpfr_set_d(frx, argx, GMP_RNDN);                                    \
    mpfr_set_d(fry, argy, GMP_RNDN);                                    \
    mpfrFunc(frc, frx, fry, GMP_RNDN);                                  \
    if (countULPdp(childFunc(argx, argy), frc) > bound) {               \
      fprintf(stderr, "\narg = %.20g, %.20g, test = %.20g, correct = %.20g, ULP = %lf\n", \
              argx, argy, childFunc(argx, argy), mpfr_get_d(frc, GMP_RNDN), countULPdp(childFunc(argx, argy), frc)); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

#define checkAccuracyX_d(mpfrFunc, childFunc, argx, bound) do {         \
    mpfr_set_d(frx, argx, GMP_RNDN);                                    \
    mpfrFunc(frc, frx, GMP_RNDN);                                       \
    Sleef_double2 d2 = childFunc(argx);                                 \
    if (countULPdp(d2.x, frc) > bound) {                                \
      fprintf(stderr, "\narg = %.20g, test = %.20g, correct = %.20g, ULP = %lf\n", argx, d2.x, mpfr_get_d(frc, GMP_RNDN), countULPdp(d2.x, frc)); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

#define checkAccuracyY_d(mpfrFunc, childFunc, argx, bound) do {         \
    mpfr_set_d(frx, argx, GMP_RNDN);                                    \
    mpfrFunc(frc, frx, GMP_RNDN);                                       \
    Sleef_double2 d2 = childFunc(argx);                                 \
    if (countULPdp(d2.y, frc) > bound) {                                \
      fprintf(stderr, "\narg = %.20g, test = %.20g, correct = %.20g, ULP = %lf\n", argx, d2.y, mpfr_get_d(frc, GMP_RNDN), countULPdp(d2.y, frc)); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

  //

  fprintf(stderr, "\nAccuracy test\n");

  //

  if (enableDP) {
    // 64 > 53(=number of bits in DP mantissa)
    mpfr_set_default_prec(64);

    fprintf(stderr, "hypot_u35 : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_d_d(mpfr_hypot, child_hypot_u35, y, x, 3.5);
    }
    for(y = -1e+10;y < 1e+10 && success;y += 1.51e+8) {
      for(x = -1e+10;x < 1e+10 && success;x += 1.51e+8) checkAccuracy_d_d(mpfr_hypot, child_hypot_u35, y, x, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "hypot_u05 : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_d_d(mpfr_hypot, child_hypot_u05, y, x, 0.5);
    }
    for(y = -1e+10;y < 1e+10 && success;y += 1.51e+8) {
      for(x = -1e+10;x < 1e+10 && success;x += 1.51e+8) checkAccuracy_d_d(mpfr_hypot, child_hypot_u05, y, x, 0.5);
    }
    showResult(success);

    //

    fprintf(stderr, "copysign : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_d_d(mpfr_copysign, child_copysign, y, x, 0);
    }
    for(y = -1e+10;y < 1e+10 && success;y += 1.51e+8) {
      for(x = -1e+10;x < 1e+10 && success;x += 1.51e+8) checkAccuracy_d_d(mpfr_copysign, child_copysign, y, x, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "fmax : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_d_d(mpfr_max, child_fmax, y, x, 0);
    }
    for(y = -1e+10;y < 1e+10 && success;y += 1.51e+8) {
      for(x = -1e+10;x < 1e+10 && success;x += 1.51e+8) checkAccuracy_d_d(mpfr_max, child_fmax, y, x, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "fmin : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_d_d(mpfr_min, child_fmin, y, x, 0);
    }
    for(y = -1e+10;y < 1e+10 && success;y += 1.51e+8) {
      for(x = -1e+10;x < 1e+10 && success;x += 1.51e+8) checkAccuracy_d_d(mpfr_min, child_fmin, y, x, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "fdim : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_d_d(mpfr_dim, child_fdim, y, x, 0.5);
    }
    for(y = -1e+10;y < 1e+10 && success;y += 1.51e+8) {
      for(x = -1e+10;x < 1e+10 && success;x += 1.51e+8) checkAccuracy_d_d(mpfr_dim, child_fdim, y, x, 0.5);
    }
    showResult(success);

    //

    fprintf(stderr, "fmod : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_d_d(mpfr_fmod, child_fmod, y, x, 0.5);
    }
    for(y = -1e+10;y < 1e+10 && success;y += 1.51e+8) {
      for(x = -1e+10;x < 1e+10 && success;x += 1.51e+8) checkAccuracy_d_d(mpfr_fmod, child_fmod, y, x, 0.5);
    }
    showResult(success);

    //

    fprintf(stderr, "remainder : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_d_d(mpfr_remainder, child_remainder, y, x, 0.5);
    }
    for(y = -1e+10;y < 1e+10 && success;y += 1.51e+8) {
      for(x = -1e+10;x < 1e+10 && success;x += 1.51e+8) checkAccuracy_d_d(mpfr_remainder, child_remainder, y, x, 0.5);
    }
    showResult(success);

    //

    fprintf(stderr, "trunc : ");
    for(x = -100.5;x <= 100.5;x+=0.5) {
      for(d = u2d(d2u(x)-3);d <= u2d(d2u(x)+3) && success;d = u2d(d2u(d)+1)) checkAccuracyNR_d(mpfr_trunc, child_trunc, d, 0);
    }
    for(d = -10000;d < 10000 && success;d += 2.5) checkAccuracyNR_d(mpfr_trunc, child_trunc, d, 0);
    {
      double start = u2d(d2u((double)(INT64_C(1) << 52))-20), end = u2d(d2u((double)(INT64_C(1) << 52))+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyNR_d(mpfr_trunc, child_trunc,  d, 0);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyNR_d(mpfr_trunc, child_trunc, -d, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "floor : ");
    for(x = -100.5;x <= 100.5;x+=0.5) {
      for(d = u2d(d2u(x)-3);d <= u2d(d2u(x)+3) && success;d = u2d(d2u(d)+1)) checkAccuracyNR_d(mpfr_floor, child_floor, d, 0);
    }
    for(d = -10000;d < 10000 && success;d += 2.5) checkAccuracyNR_d(mpfr_floor, child_floor, d, 0);
    {
      double start = u2d(d2u((double)(INT64_C(1) << 52))-20), end = u2d(d2u((double)(INT64_C(1) << 52))+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyNR_d(mpfr_floor, child_floor,  d, 0);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyNR_d(mpfr_floor, child_floor, -d, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "ceil : ");
    for(x = -100.5;x <= 100.5;x+=0.5) {
      for(d = u2d(d2u(x)-3);d <= u2d(d2u(x)+3) && success;d = u2d(d2u(d)+1)) checkAccuracyNR_d(mpfr_ceil, child_ceil, d, 0);
    }
    for(d = -10000;d < 10000 && success;d += 2.5) checkAccuracyNR_d(mpfr_ceil, child_ceil, d, 0);
    {
      double start = u2d(d2u((double)(INT64_C(1) << 52))-20), end = u2d(d2u((double)(INT64_C(1) << 52))+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyNR_d(mpfr_ceil, child_ceil,  d, 0);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyNR_d(mpfr_ceil, child_ceil, -d, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "round : ");
    for(x = -100.5;x <= 100.5;x+=0.5) {
      for(d = u2d(d2u(x)-3);d <= u2d(d2u(x)+3) && success;d = u2d(d2u(d)+1)) checkAccuracyNR_d(mpfr_round, child_round, d, 0);
    }
    for(d = -10000;d < 10000 && success;d += 2.5) checkAccuracyNR_d(mpfr_round, child_round, d, 0);
    {
      double start = u2d(d2u((double)(INT64_C(1) << 52))-20), end = u2d(d2u((double)(INT64_C(1) << 52))+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyNR_d(mpfr_round, child_round,  d, 0);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyNR_d(mpfr_round, child_round, -d, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "rint : ");
    for(x = -100.5;x <= 100.5;x+=0.5) {
      for(d = u2d(d2u(x)-3);d <= u2d(d2u(x)+3) && success;d = u2d(d2u(d)+1)) checkAccuracy_d(mpfr_rint, child_rint, d, 0);
    }
    for(d = -10000;d < 10000 && success;d += 2.5) checkAccuracy_d(mpfr_rint, child_rint, d, 0);
    {
      double start = u2d(d2u((double)(INT64_C(1) << 52))-20), end = u2d(d2u((double)(INT64_C(1) << 52))+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracy_d(mpfr_rint, child_rint,  d, 0);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracy_d(mpfr_rint, child_rint, -d, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "sin : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_sin, child_sin, d, 3.5);
    for(d = -1e+14;d < 1e+14 && success;d += (1e+10 + 0.1)) checkAccuracy_d(mpfr_sin, child_sin, d, 3.5);
    for(i = 0;i < 920 && success;i++) checkAccuracy_d(mpfr_sin, child_sin, pow(2.16, i), 3.5);
    for(i64=(int64_t)-1e+14;i64<(int64_t)1e+14 && success;i64+=(int64_t)1e+12) {
      double start = u2d(d2u(M_PI_4 * i64)-20), end = u2d(d2u(M_PI_4 * i64)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracy_d(mpfr_sin, child_sin, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "sin_u1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_sin, child_sin_u1, d, 1.0);
    for(d = -1e+14;d < 1e+14 && success;d += (1e+10 + 0.1)) checkAccuracy_d(mpfr_sin, child_sin_u1, d, 1.0);
    for(i = 0;i < 920 && success;i++) checkAccuracy_d(mpfr_sin, child_sin_u1, pow(2.16, i), 1.0);
    for(i64=(int64_t)-1e+14;i64<(int64_t)1e+14 && success;i64+=(int64_t)1e+12) {
      double start = u2d(d2u(M_PI_4 * i64)-20), end = u2d(d2u(M_PI_4 * i64)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracy_d(mpfr_sin, child_sin_u1, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "sin in sincos : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracyX_d(mpfr_sin, child_sincos, d, 3.5);
    for(d = -1e+14;d < 1e+14 && success;d += (1e+10 + 0.1)) checkAccuracyX_d(mpfr_sin, child_sincos, d, 3.5);
    for(i = 0;i < 920 && success;i++) checkAccuracyX_d(mpfr_sin, child_sincos, pow(2.16, i), 3.5);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2d(d2u(M_PI_4 * i)-20), end = u2d(d2u(M_PI_4 * i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyX_d(mpfr_sin, child_sincos, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "sin in sincos_u1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracyX_d(mpfr_sin, child_sincos_u1, d, 1.0);
    for(d = -1e+14;d < 1e+14 && success;d += (1e+10 + 0.1)) checkAccuracyX_d(mpfr_sin, child_sincos_u1, d, 1.0);
    for(i = 0;i < 920 && success;i++) checkAccuracyX_d(mpfr_sin, child_sincos_u1, pow(2.16, i), 1.0);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2d(d2u(M_PI_4 * i)-20), end = u2d(d2u(M_PI_4 * i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyX_d(mpfr_sin, child_sincos_u1, d, 1.0);
    }
    showResult(success);

    //

    // 1280 > 1024(=maximum DP exponent) + 53(=number of bits in DP mantissa)
    mpfr_set_default_prec(1280);

    fprintf(stderr, "sin in sincospi_u35 : ");
    for(d = -10.1;d < 10 && success;d += 0.0021) checkAccuracyX_d(mpfr_sinpi, child_sincospi_u35, d, 3.5);
    for(d = -1e+8-0.1;d < 1e+8 && success;d += (1e+10 + 0.1)) checkAccuracyX_d(mpfr_sinpi, child_sincospi_u35, d, 3.5);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2d(d2u(i)-20), end = u2d(d2u(i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyX_d(mpfr_sinpi, child_sincospi_u35, d, 3.5);
    }
    for(i=1;i<=20 && success;i++) {
      double start = u2d(d2u(0.25 * i)-20), end = u2d(d2u(0.25 * i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyX_d(mpfr_sinpi, child_sincospi_u35, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "sin in sincospi_u05 : ");
    for(d = -10.1;d < 10 && success;d += 0.0021) checkAccuracyX_d(mpfr_sinpi, child_sincospi_u05, d, 0.506);
    for(d = -1e+8-0.1;d < 1e+8 && success;d += (1e+10 + 0.1)) checkAccuracyX_d(mpfr_sinpi, child_sincospi_u05, d, 0.506);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2d(d2u(i)-20), end = u2d(d2u(i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyX_d(mpfr_sinpi, child_sincospi_u05, d, 0.506);
    }
    for(i=1;i<=20 && success;i++) {
      double start = u2d(d2u(0.25 * i)-20), end = u2d(d2u(0.25 * i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyX_d(mpfr_sinpi, child_sincospi_u05, d, 0.506);
    }
    showResult(success);

    //

    fprintf(stderr, "sinpi_u05 : ");
    for(d = -10.1;d < 10 && success;d += 0.0021) checkAccuracy_d(mpfr_sinpi, child_sinpi_u05, d, 0.506);
    for(d = -1e+8-0.1;d < 1e+8 && success;d += (1e+10 + 0.1)) checkAccuracy_d(mpfr_sinpi, child_sinpi_u05, d, 0.506);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2d(d2u(i)-20), end = u2d(d2u(i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracy_d(mpfr_sinpi, child_sinpi_u05, d, 0.506);
    }
    for(i=1;i<=20 && success;i++) {
      double start = u2d(d2u(0.25 * i)-20), end = u2d(d2u(0.25 * i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracy_d(mpfr_sinpi, child_sinpi_u05, d, 0.506);
    }
    showResult(success);

    //

    fprintf(stderr, "cospi_u05 : ");
    for(d = -10.1;d < 10 && success;d += 0.0021) checkAccuracy_d(mpfr_cospi, child_cospi_u05, d, 0.506);
    for(d = -1e+8-0.1;d < 1e+8 && success;d += (1e+10 + 0.1)) checkAccuracy_d(mpfr_cospi, child_cospi_u05, d, 0.506);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2d(d2u(i)-20), end = u2d(d2u(i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracy_d(mpfr_cospi, child_cospi_u05, d, 0.506);
    }
    for(i=1;i<=20 && success;i++) {
      double start = u2d(d2u(0.25 * i)-20), end = u2d(d2u(0.25 * i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracy_d(mpfr_cospi, child_cospi_u05, d, 0.506);
    }
    showResult(success);

    mpfr_set_default_prec(64);

    //

    fprintf(stderr, "cos : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_cos, child_cos, d, 3.5);
    for(d = -1e+14;d < 1e+14 && success;d += (1e+10 + 0.1)) checkAccuracy_d(mpfr_cos, child_cos, d, 3.5);
    for(i = 0;i < 920 && success;i++) checkAccuracy_d(mpfr_cos, child_cos, pow(2.16, i), 3.5);
    for(i64=(int64_t)-1e+14;i64<(int64_t)1e+14 && success;i64+=(int64_t)1e+12) {
      double start = u2d(d2u(M_PI_4 * i64)-20), end = u2d(d2u(M_PI_4 * i64)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracy_d(mpfr_cos, child_cos, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "cos_u1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_cos, child_cos_u1, d, 1.0);
    for(d = -1e+14;d < 1e+14 && success;d += (1e+10 + 0.1)) checkAccuracy_d(mpfr_cos, child_cos_u1, d, 1.0);
    for(i = 0;i < 920 && success;i++) checkAccuracy_d(mpfr_cos, child_cos_u1, pow(2.16, i), 1.0);
    for(i64=(int64_t)-1e+14;i64<(int64_t)1e+14 && success;i64+=(int64_t)1e+12) {
      double start = u2d(d2u(M_PI_4 * i64)-20), end = u2d(d2u(M_PI_4 * i64)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracy_d(mpfr_cos, child_cos_u1, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "cos in sincos : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracyY_d(mpfr_cos, child_sincos, d, 3.5);
    for(d = -1e+14;d < 1e+14 && success;d += (1e+10 + 0.1)) checkAccuracyY_d(mpfr_cos, child_sincos, d, 3.5);
    for(i = 0;i < 920 && success;i++) checkAccuracyY_d(mpfr_cos, child_sincos, pow(2.16, i), 3.5);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2d(d2u(M_PI_4 * i)-20), end = u2d(d2u(M_PI_4 * i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyY_d(mpfr_cos, child_sincos, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "cos in sincos_u1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracyY_d(mpfr_cos, child_sincos_u1, d, 1.0);
    for(d = -1e+14;d < 1e+14 && success;d += (1e+10 + 0.1)) checkAccuracyY_d(mpfr_cos, child_sincos_u1, d, 1.0);
    for(i = 0;i < 920 && success;i++) checkAccuracyY_d(mpfr_cos, child_sincos_u1, pow(2.16, i), 1.0);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2d(d2u(M_PI_4 * i)-20), end = u2d(d2u(M_PI_4 * i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyY_d(mpfr_cos, child_sincos_u1, d, 1.0);
    }
    showResult(success);

    //

    mpfr_set_default_prec(1280);

    fprintf(stderr, "cos in sincospi_u35 : ");
    for(d = -10.1;d < 10 && success;d += 0.0021) checkAccuracyY_d(mpfr_cospi, child_sincospi_u35, d, 3.5);
    for(d = -1e+8-0.1;d < 1e+8 && success;d += (1e+10 + 0.1)) checkAccuracyY_d(mpfr_cospi, child_sincospi_u35, d, 3.5);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2d(d2u(i)-20), end = u2d(d2u(i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyY_d(mpfr_cospi, child_sincospi_u35, d, 3.5);
    }
    for(i=1;i<=20 && success;i++) {
      double start = u2d(d2u(0.25 * i)-20), end = u2d(d2u(0.25 * i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyY_d(mpfr_cospi, child_sincospi_u35, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "cos in sincospi_u05 : ");
    for(d = -10.1;d < 10 && success;d += 0.0021) checkAccuracyY_d(mpfr_cospi, child_sincospi_u05, d, 0.506);
    for(d = -1e+8-0.1;d < 1e+8 && success;d += (1e+10 + 0.1)) checkAccuracyY_d(mpfr_cospi, child_sincospi_u05, d, 0.506);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2d(d2u(i)-20), end = u2d(d2u(i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyY_d(mpfr_cospi, child_sincospi_u05, d, 0.506);
    }
    for(i=1;i<=20 && success;i++) {
      double start = u2d(d2u(0.25 * i)-20), end = u2d(d2u(0.25 * i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracyY_d(mpfr_cospi, child_sincospi_u05, d, 0.506);
    }
    showResult(success);

    mpfr_set_default_prec(64);

    //

    fprintf(stderr, "tan : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_tan, child_tan, d, 3.5);
    for(d = -1e+7;d < 1e+7 && success;d += 1000.1) checkAccuracy_d(mpfr_tan, child_tan, d, 3.5);
    for(d = -1e+14;d < 1e+14 && success;d += (1e+10 + 0.1)) checkAccuracy_d(mpfr_tan, child_tan, d, 3.5);
    for(i = 0;i < 920 && success;i++) checkAccuracy_d(mpfr_tan, child_tan, pow(2.16, i), 3.5);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2d(d2u(M_PI_4 * i)-20), end = u2d(d2u(M_PI_4 * i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracy_d(mpfr_tan, child_tan, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "tan_u1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_tan, child_tan_u1, d, 1.0);
    for(d = -1e+7;d < 1e+7 && success;d += 1000.1) checkAccuracy_d(mpfr_tan, child_tan_u1, d, 1.0);
    for(d = -1e+14;d < 1e+14 && success;d += (1e+10 + 0.1)) checkAccuracy_d(mpfr_tan, child_tan_u1, d, 1.0);
    for(i = 0;i < 920 && success;i++) checkAccuracy_d(mpfr_tan, child_tan_u1, pow(2.16, i), 1.0);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2d(d2u(M_PI_4 * i)-20), end = u2d(d2u(M_PI_4 * i)+20);
      for(d = start;d <= end;d = u2d(d2u(d)+1)) checkAccuracy_d(mpfr_tan, child_tan_u1, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "log : ");
    for(d = 0.0001;d < 10 && success;d += 0.001) checkAccuracy_d(mpfr_log, child_log, d, 3.5);
    for(d = 0.0001;d < 10000 && success;d += 1.1) checkAccuracy_d(mpfr_log, child_log, d, 3.5);
    for(i = -1000;i <= 1000 && success;i+=10) checkAccuracy_d(mpfr_log, child_log, pow(2.1, i), 3.5);
    for(i=0;i<10000 && success;i+=10) checkAccuracy_d(mpfr_log, child_log, DBL_MAX * pow(0.9314821319758632, i), 3.5);
    for(i=0;i<10000 && success;i+=10) checkAccuracy_d(mpfr_log, child_log, pow(0.933254300796991, i), 3.5);
    for(i=0;i<10000 && success;i+=10) checkAccuracy_d(mpfr_log, child_log, DBL_MIN * pow(0.996323, i), 3.5);
    showResult(success);

    //

    fprintf(stderr, "log_u1 : ");
    for(d = 0.0001;d < 10 && success;d += 0.001) checkAccuracy_d(mpfr_log, child_log_u1, d, 1.0);
    for(d = 0.0001;d < 10000 && success;d += 1.1) checkAccuracy_d(mpfr_log, child_log_u1, d, 1.0);
    for(i = -1000;i <= 1000 && success;i+=10) checkAccuracy_d(mpfr_log, child_log_u1, pow(2.1, i), 1.0);
    for(i=0;i<10000 && success;i+=10) checkAccuracy_d(mpfr_log, child_log_u1, DBL_MAX * pow(0.9314821319758632, i), 1.0);
    for(i=0;i<10000 && success;i+=10) checkAccuracy_d(mpfr_log, child_log_u1, pow(0.933254300796991, i), 1.0);
    for(i=0;i<10000 && success;i+=10) checkAccuracy_d(mpfr_log, child_log_u1, DBL_MIN * pow(0.996323, i), 1.0);
    showResult(success);

    //

    fprintf(stderr, "exp : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_exp, child_exp, d, 1.0);
    for(d = -1000;d < 1000 && success;d += 1.1) checkAccuracy_d(mpfr_exp, child_exp, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "pow : ");
    for(y = 0.1;y < 100 && success;y += 0.6) {
      for(x = -100;x < 100 && success;x += 0.6) {
        checkAccuracy_d_d(mpfr_pow, child_pow, x, y, 1.0);
      }
    }
    for(y = -1000;y < 1000 && success;y += 0.1) checkAccuracy_d_d(mpfr_pow, child_pow, 2.1, y, 1.0);
    showResult(success);

    //

    if (!deterministicMode) {
      fprintf(stderr, "sqrt : ");
      for(d = -10000;d < 10000 && success;d += 2.1) checkAccuracy_d(mpfr_sqrt, child_sqrt, d, 1.0);
      for(i = -1000;i <= 1000 && success;i+=10) checkAccuracy_d(mpfr_sqrt, child_sqrt, pow(2.1, d), 1.0);
      showResult(success);

      //

      fprintf(stderr, "sqrt_u05 : ");
      for(d = -10000;d < 10000 && success;d += 2.1) checkAccuracy_d(mpfr_sqrt, child_sqrt_u05, d, 0.506);
      for(i = -1000;i <= 1000 && success;i+=10) checkAccuracy_d(mpfr_sqrt, child_sqrt_u05, pow(2.1, d), 0.506);
      showResult(success);

      //

      fprintf(stderr, "sqrt_u35 : ");
      for(d = -10000;d < 10000 && success;d += 2.1) checkAccuracy_d(mpfr_sqrt, child_sqrt_u35, d, 3.5);
      for(i = -1000;i <= 1000 && success;i+=10) checkAccuracy_d(mpfr_sqrt, child_sqrt_u35, pow(2.1, d), 3.5);
      showResult(success);
    }

    //

    fprintf(stderr, "cbrt : ");
    for(d = -10000;d < 10000 && success;d += 2.1) checkAccuracy_d(mpfr_cbrt, child_cbrt, d, 3.5);
    for(i = -1000;i <= 1000 && success;i+=10) checkAccuracy_d(mpfr_cbrt, child_cbrt, pow(2.1, d), 3.5);
    showResult(success);

    //

    fprintf(stderr, "cbrt_u1 : ");
    for(d = -10000;d < 10000 && success;d += 2.1) checkAccuracy_d(mpfr_cbrt, child_cbrt_u1, d, 1.0);
    for(i = -1000;i <= 1000 && success;i+=10) checkAccuracy_d(mpfr_cbrt, child_cbrt_u1, pow(2.1, d), 1.0);
    showResult(success);

    //

    fprintf(stderr, "asin : ");
    for(d = -1;d < 1 && success;d += 0.0002) checkAccuracy_d(mpfr_asin, child_asin, d, 3.5);
    showResult(success);

    //

    fprintf(stderr, "asin_u1 : ");
    for(d = -1;d < 1 && success;d += 0.0002) checkAccuracy_d(mpfr_asin, child_asin_u1, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "acos : ");
    for(d = -1;d < 1 && success;d += 0.0002) checkAccuracy_d(mpfr_acos, child_acos, d, 3.5);
    showResult(success);

    //

    fprintf(stderr, "acos_u1 : ");
    for(d = -1;d < 1 && success;d += 0.0002) checkAccuracy_d(mpfr_acos, child_acos_u1, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "atan : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_atan, child_atan, d, 3.5);
    for(d = -10000;d < 10000 && success;d += 2.1) checkAccuracy_d(mpfr_atan, child_atan, d, 3.5);
    showResult(success);

    //

    fprintf(stderr, "atan_u1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_atan, child_atan_u1, d, 1.0);
    for(d = -10000;d < 10000 && success;d += 2.1) checkAccuracy_d(mpfr_atan, child_atan_u1, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "atan2 : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_d_d(mpfr_atan2, child_atan2, y, x, 3.5);
    }
    for(y = -100;y < 100 && success;y += 1.51) {
      for(x = -100;x < 100 && success;x += 1.51) checkAccuracy_d_d(mpfr_atan2, child_atan2, y, x, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "atan2_u1 : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_d_d(mpfr_atan2, child_atan2_u1, y, x, 1.0);
    }
    for(y = -100;y < 100 && success;y += 1.51) {
      for(x = -100;x < 100 && success;x += 1.51) checkAccuracy_d_d(mpfr_atan2, child_atan2_u1, y, x, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "sinh : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_sinh, child_sinh, d, 1.0);
    for(d = -709;d < 709 && success;d += 0.2) checkAccuracy_d(mpfr_sinh, child_sinh, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "cosh : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_cosh, child_cosh, d, 1.0);
    for(d = -709;d < 709 && success;d += 0.2) checkAccuracy_d(mpfr_cosh, child_cosh, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "tanh : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_tanh, child_tanh, d, 1.0);
    for(d = -1000;d < 1000 && success;d += 0.2) checkAccuracy_d(mpfr_tanh, child_tanh, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "sinh_u35 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_sinh, child_sinh_u35, d, 3.5);
    for(d = -709;d < 709 && success;d += 0.2) checkAccuracy_d(mpfr_sinh, child_sinh_u35, d, 3.5);
    showResult(success);

    //

    fprintf(stderr, "cosh_u35 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_cosh, child_cosh_u35, d, 3.5);
    for(d = -709;d < 709 && success;d += 0.2) checkAccuracy_d(mpfr_cosh, child_cosh_u35, d, 3.5);
    showResult(success);

    //

    fprintf(stderr, "tanh_u35 : ");
    for(d = -10;d < 10 && success;d += 0.002)   checkAccuracy_d(mpfr_tanh, child_tanh_u35, d, 3.5);
    for(d = -1000;d < 1000 && success;d += 0.2) checkAccuracy_d(mpfr_tanh, child_tanh_u35, d, 3.5);
    showResult(success);

    //

    fprintf(stderr, "asinh : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_asinh, child_asinh, d, 1.0);
    for(d = -1000;d < 1000 && success;d += 0.2) checkAccuracy_d(mpfr_asinh, child_asinh, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "acosh : ");
    for(d = 1;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_acosh, child_acosh, d, 1.0);
    for(d = 1;d < 1000 && success;d += 0.2) checkAccuracy_d(mpfr_acosh, child_acosh, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "atanh : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_atanh, child_atanh, d, 1.0);
    for(d = -1000;d < 1000 && success;d += 0.2) checkAccuracy_d(mpfr_atanh, child_atanh, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "exp2 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_exp2, child_exp2, d, 1.0);
    for(d = -1000;d < 1000 && success;d += 0.2) checkAccuracy_d(mpfr_exp2, child_exp2, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "exp10 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_exp10, child_exp10, d, 1.0);
    for(d = -300;d < 300 && success;d += 0.1) checkAccuracy_d(mpfr_exp10, child_exp10, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "exp2_u35 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_exp2, child_exp2_u35, d, 3.5);
    for(d = -1000;d < 1000 && success;d += 0.2) checkAccuracy_d(mpfr_exp2, child_exp2_u35, d, 3.5);
    showResult(success);

    //

    fprintf(stderr, "exp10_u35 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_exp10, child_exp10_u35, d, 3.5);
    for(d = -300;d < 300 && success;d += 0.1) checkAccuracy_d(mpfr_exp10, child_exp10_u35, d, 3.5);
    showResult(success);

    //

    fprintf(stderr, "expm1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_expm1, child_expm1, d, 1.0);
    for(d = -1000;d < 1000 && success;d += 0.21) checkAccuracy_d(mpfr_expm1, child_expm1, d, 1.0);
    for(d = 0;d < 300 && success;d += 0.21) checkAccuracy_d(mpfr_expm1, child_expm1, pow(10, -d), 1.0);
    for(d = 0;d < 300 && success;d += 0.21) checkAccuracy_d(mpfr_expm1, child_expm1, (-pow(10, -d)), 1.0);
    showResult(success);

    //

    fprintf(stderr, "log10 : ");
    for(d = 0.0001;d < 10 && success;d += 0.001) checkAccuracy_d(mpfr_log10, child_log10, d, 1.0);
    for(d = 0.0001;d < 10000 && success;d += 1.1) checkAccuracy_d(mpfr_log10, child_log10, d, 1.0);
    for(i=0;i<10000 && success;i++) checkAccuracy_d(mpfr_log10, child_log10, (DBL_MIN * pow(0.996323, i)), 1.0);
    showResult(success);

    //

    fprintf(stderr, "log2 : ");
    for(d = 0.0001;d < 10 && success;d += 0.001) checkAccuracy_d(mpfr_log2, child_log2, d, 1.0);
    for(d = 0.0001;d < 10000 && success;d += 1.1) checkAccuracy_d(mpfr_log2, child_log2, d, 1.0);
    for(i=0;i<10000 && success;i++) checkAccuracy_d(mpfr_log2, child_log2, (DBL_MIN * pow(0.996323, i)), 1.0);
    showResult(success);

    //

    fprintf(stderr, "log2_u35 : ");
    for(d = 0.0001;d < 10 && success;d += 0.001) checkAccuracy_d(mpfr_log2, child_log2_u35, d, 3.5);
    for(d = 0.0001;d < 10000 && success;d += 1.1) checkAccuracy_d(mpfr_log2, child_log2_u35, d, 3.5);
    for(i=0;i<10000 && success;i++) checkAccuracy_d(mpfr_log2, child_log2_u35, (DBL_MIN * pow(0.996323, i)), 3.5);
    showResult(success);

    //

    fprintf(stderr, "log1p : ");
    for(d = 0.0001;d < 10 && success;d += 0.001) checkAccuracy_d(mpfr_log1p, child_log1p, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "lgamma_u1 : ");
    for(d = -5000;d < 5000 && success;d += 1.1) checkAccuracy_d(mpfr_lgamma_nosign, child_lgamma_u1, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "tgamma_u1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_d(mpfr_gamma, child_tgamma_u1, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "erf_u1 : ");
    for(d = -100;d < 100 && success;d += 0.02) checkAccuracy_d(mpfr_erf, child_erf_u1, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "erfc_u15 : ");
    for(d = -1;d < 100 && success;d += 0.01) checkAccuracy_d(mpfr_erfc, child_erfc_u15, d, 1.5);
    showResult(success);

    //

    {
      fprintf(stderr, "ilogb : ");

      for(d = 0.0001;d < 10;d += 0.001) {
        int q = child_ilogb(d);
        int c = ilogb(d);
        if (q != c) {
          fprintf(stderr, "ilogb : arg = %.20g, test = %d, correct = %d\n", d, ilogb(d), child_ilogb(d));
          success = 0;
          showResult(success);
        }
      }

      for(d = 0.0001;d < 10000;d += 1.1) {
        int q = child_ilogb(d);
        int c = ilogb(d);
        if (q != c) {
          fprintf(stderr, "ilogb : arg = %.20g, test = %d, correct = %d\n", d, ilogb(d), child_ilogb(d));
          success = 0;
          showResult(success);
        }
      }

      for(i=0;i<10000;i+=10) {
        d = DBL_MIN * pow(0.996323, i);
        if (d == 0) continue;
        int q = child_ilogb(d);
        int c = ilogb(d);
        if (q != c) {
          fprintf(stderr, "ilogb : arg = %.20g, test = %d, correct = %d\n", d, ilogb(d), child_ilogb(d));
          success = 0;
          showResult(success);
        }
      }

      for(i=0;i<10000;i+=10) {
        d = pow(0.933254300796991, i);
        if (d == 0) continue;
        int q = child_ilogb(d);
        int c = ilogb(d);
        if (q != c) {
          fprintf(stderr, "ilogb : arg = %.20g, test = %d, correct = %d\n", d, ilogb(d), child_ilogb(d));
          success = 0;
          showResult(success);
        }
      }

      showResult(success);
    }
  }

  //

#define checkAccuracy_f(mpfrFunc, childFunc, argx, bound) do {          \
    mpfr_set_d(frx, (float)flushToZero(argx), GMP_RNDN);                \
    mpfrFunc(frc, frx, GMP_RNDN);                                       \
    if (countULPsp(childFunc((float)flushToZero(argx)), frc) > bound) { \
      fprintf(stderr, "\narg = %.20g, test = %.20g, correct = %.20g, ULP = %lf\n", \
              (float)flushToZero(argx), (double)childFunc((float)flushToZero(argx)), mpfr_get_d(frc, GMP_RNDN), countULPsp(childFunc((float)flushToZero(argx)), frc)); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

#define checkAccuracyNR_f(mpfrFunc, childFunc, argx, bound) do {        \
    mpfr_set_d(frx, (float)flushToZero(argx), GMP_RNDN);                \
    mpfrFunc(frc, frx);                                                 \
    if (countULPsp(childFunc((float)flushToZero(argx)), frc) > bound) { \
      fprintf(stderr, "\narg = %.20g, test = %.20g, correct = %.20g, ULP = %lf\n", \
              (float)flushToZero(argx), (double)childFunc((float)flushToZero(argx)), mpfr_get_d(frc, GMP_RNDN), countULPsp(childFunc((float)flushToZero(argx)), frc)); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

#define checkAccuracy_f_f(mpfrFunc, childFunc, argx, argy, bound) do {  \
    mpfr_set_d(frx, (float)flushToZero(argx), GMP_RNDN);                \
    mpfr_set_d(fry, (float)flushToZero(argy), GMP_RNDN);                \
    mpfrFunc(frc, frx, fry, GMP_RNDN);                                  \
    if (countULPsp(childFunc((float)flushToZero(argx), (float)flushToZero(argy)), frc) > bound) {       \
      fprintf(stderr, "\narg = %.20g, %.20g, test = %.20g, correct = %.20g, ULP = %lf\n", \
              (float)flushToZero(argx), (float)flushToZero(argy), childFunc((float)flushToZero(argx), (float)flushToZero(argy)), mpfr_get_d(frc, GMP_RNDN), countULPsp(childFunc((float)flushToZero(argx), (float)flushToZero(argy)), frc)); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

#define checkAccuracyX_f(mpfrFunc, childFunc, argx, bound) do {         \
    mpfr_set_d(frx, (float)flushToZero(argx), GMP_RNDN);                                \
    mpfrFunc(frc, frx, GMP_RNDN);                                       \
    Sleef_float2 d2 = childFunc((float)flushToZero(argx));                              \
    if (countULPsp(d2.x, frc) > bound) {                                \
      fprintf(stderr, "\narg = %.20g, test = %.20g, correct = %.20g, ULP = %lf\n", (float)flushToZero(argx), (double)d2.x, mpfr_get_d(frc, GMP_RNDN), countULPsp(d2.x, frc)); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

#define checkAccuracyY_f(mpfrFunc, childFunc, argx, bound) do {         \
    mpfr_set_d(frx, (float)flushToZero(argx), GMP_RNDN);                                \
    mpfrFunc(frc, frx, GMP_RNDN);                                       \
    Sleef_float2 d2 = childFunc((float)flushToZero(argx));                              \
    if (countULPsp(d2.y, frc) > bound) {                                \
      fprintf(stderr, "\narg = %.20g, test = %.20g, correct = %.20g, ULP = %lf\n", (float)flushToZero(argx), (double)d2.y, mpfr_get_d(frc, GMP_RNDN), countULPsp(d2.y, frc)); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

#define checkAccuracy2_f(mpfrFunc, childFunc, argx, bound, abound) do { \
    mpfr_set_d(frx, (float)flushToZero(argx), GMP_RNDN);                \
    mpfrFunc(frc, frx, GMP_RNDN);                                       \
    double t = childFunc((float)flushToZero(argx));                     \
    double ae = fabs(mpfr_get_d(frc, GMP_RNDN) - t);                    \
    if (countULPsp(t, frc) > bound && ae > abound) {                    \
      fprintf(stderr, "\narg = %.20g, test = %.20g, correct = %.20g, ULP = %lf, abserror = %g\n", \
              (float)flushToZero(argx), (double)childFunc((float)flushToZero(argx)), mpfr_get_d(frc, GMP_RNDN), countULPsp(childFunc((float)flushToZero(argx)), frc), ae); \
      success = 0;                                                      \
      break;                                                            \
    }                                                                   \
  } while(0)

  //

  if (enableSP) {
    // 53 > 24(=number of bits in SP mantissa)
    mpfr_set_default_prec(53);

    fprintf(stderr, "hypotf_u35 : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_f_f(mpfr_hypot, child_hypotf_u35, y, x, 3.5);
    }
    for(y = -1e+7;y < 1e+7 && success;y += 1.51e+5) {
      for(x = -1e+7;x < 1e+7 && success;x += 1.51e+5) checkAccuracy_f_f(mpfr_hypot, child_hypotf_u35, y, x, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "hypotf_u05 : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_f_f(mpfr_hypot, child_hypotf_u05, y, x, 0.5);
    }
    for(y = -1e+7;y < 1e+7 && success;y += 1.51e+5) {
      for(x = -1e+7;x < 1e+7 && success;x += 1.51e+5) checkAccuracy_f_f(mpfr_hypot, child_hypotf_u05, y, x, 0.5);
    }
    showResult(success);

    //

    fprintf(stderr, "copysignf : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_f_f(mpfr_copysign, child_copysignf, y, x, 0);
    }
    for(y = -1e+7;y < 1e+7 && success;y += 1.51e+5) {
      for(x = -1e+7;x < 1e+7 && success;x += 1.51e+5) checkAccuracy_f_f(mpfr_copysign, child_copysignf, y, x, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "fmaxf : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_f_f(mpfr_max, child_fmaxf, y, x, 0);
    }
    for(y = -1e+7;y < 1e+7 && success;y += 1.51e+5) {
      for(x = -1e+7;x < 1e+7 && success;x += 1.51e+5) checkAccuracy_f_f(mpfr_max, child_fmaxf, y, x, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "fminf : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_f_f(mpfr_min, child_fminf, y, x, 0);
    }
    for(y = -1e+7;y < 1e+7 && success;y += 1.51e+5) {
      for(x = -1e+7;x < 1e+7 && success;x += 1.51e+5) checkAccuracy_f_f(mpfr_min, child_fminf, y, x, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "fdimf : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_f_f(mpfr_dim, child_fdimf, y, x, 0.5);
    }
    for(y = -1e+7;y < 1e+7 && success;y += 1.51e+5) {
      for(x = -1e+7;x < 1e+7 && success;x += 1.51e+5) checkAccuracy_f_f(mpfr_dim, child_fdimf, y, x, 0.5);
    }
    showResult(success);

    //

    fprintf(stderr, "fmodf : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_f_f(mpfr_fmod, child_fmodf, y, x, 0.5);
    }
    for(y = -1e+7;y < 1e+7 && success;y += 1.51e+5) {
      for(x = -1e+7;x < 1e+7 && success;x += 1.51e+5) checkAccuracy_f_f(mpfr_fmod, child_fmodf, y, x, 0.5);
    }
    showResult(success);

    //

    fprintf(stderr, "remainderf : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_f_f(mpfr_remainder, child_remainderf, y, x, 0.5);
    }
    for(y = -1e+7;y < 1e+7 && success;y += 1.51e+5) {
      for(x = -1e+7;x < 1e+7 && success;x += 1.51e+5) checkAccuracy_f_f(mpfr_remainder, child_remainderf, y, x, 0.5);
    }
    checkAccuracy_f_f(mpfr_remainder, child_remainderf, 11114942644092928.0, 224544296009728.0, 0.5);
    showResult(success);

    //

    fprintf(stderr, "truncf : ");
    for(x = -100.5;x <= 100.5;x+=0.5) {
      for(d = u2d(d2u(x)-3);d <= u2d(d2u(x)+3) && success;d = u2d(d2u(d)+1)) checkAccuracyNR_f(mpfr_trunc, child_truncf, d, 0);
    }
    for(d = -10000;d < 10000 && success;d += 2.5) checkAccuracyNR_f(mpfr_trunc, child_truncf, d, 0);
    {
      double start = u2f(f2u((double)(INT64_C(1) << 23))-20), end = u2f(f2u((double)(INT64_C(1) << 23))+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyNR_f(mpfr_trunc, child_truncf,  d, 0);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyNR_f(mpfr_trunc, child_truncf, -d, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "floorf : ");
    for(x = -100.5;x <= 100.5;x+=0.5) {
      for(d = u2d(d2u(x)-3);d <= u2d(d2u(x)+3) && success;d = u2d(d2u(d)+1)) checkAccuracyNR_f(mpfr_floor, child_floorf, d, 0);
    }
    for(d = -10000;d < 10000 && success;d += 2.5) checkAccuracyNR_f(mpfr_floor, child_floorf, d, 0);
    {
      double start = u2f(f2u((double)(INT64_C(1) << 23))-20), end = u2f(f2u((double)(INT64_C(1) << 23))+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyNR_f(mpfr_floor, child_floorf,  d, 0);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyNR_f(mpfr_floor, child_floorf, -d, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "ceilf : ");
    for(x = -100.5;x <= 100.5;x+=0.5) {
      for(d = u2d(d2u(x)-3);d <= u2d(d2u(x)+3) && success;d = u2d(d2u(d)+1)) checkAccuracyNR_f(mpfr_ceil, child_ceilf, d, 0);
    }
    for(d = -10000;d < 10000 && success;d += 2.5) checkAccuracyNR_f(mpfr_ceil, child_ceilf, d, 0);
    {
      double start = u2f(f2u((double)(INT64_C(1) << 23))-20), end = u2f(f2u((double)(INT64_C(1) << 23))+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyNR_f(mpfr_ceil, child_ceilf,  d, 0);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyNR_f(mpfr_ceil, child_ceilf, -d, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "roundf : ");
    for(x = -100.5;x <= 100.5;x+=0.5) {
      for(d = u2d(d2u(x)-3);d <= u2d(d2u(x)+3) && success;d = u2d(d2u(d)+1)) checkAccuracyNR_f(mpfr_round, child_roundf, d, 0);
    }
    for(d = -10000;d < 10000 && success;d += 2.5) checkAccuracyNR_f(mpfr_round, child_roundf, d, 0);
    {
      double start = u2f(f2u((double)(INT64_C(1) << 23))-20), end = u2f(f2u((double)(INT64_C(1) << 23))+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyNR_f(mpfr_round, child_roundf,  d, 0);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyNR_f(mpfr_round, child_roundf, -d, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "rintf : ");
    for(x = -100.5;x <= 100.5;x+=0.5) {
      for(d = u2d(d2u(x)-3);d <= u2d(d2u(x)+3) && success;d = u2d(d2u(d)+1)) checkAccuracy_f(mpfr_rint, child_rintf, d, 0);
    }
    for(d = -10000;d < 10000 && success;d += 2.5) checkAccuracy_f(mpfr_rint, child_rintf, d, 0);
    {
      double start = u2f(f2u((double)(INT64_C(1) << 23))-20), end = u2f(f2u((double)(INT64_C(1) << 23))+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracy_f(mpfr_rint, child_rintf,  d, 0);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracy_f(mpfr_rint, child_rintf, -d, 0);
    }
    showResult(success);

    //

    fprintf(stderr, "sinf : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_sin, child_sinf, d, 3.5);
    for(d = -10000;d < 10000 && success;d += 1.1) checkAccuracy_f(mpfr_sin, child_sinf, d, 3.5);
    for(i = 0;i < 1000 && success;i++) checkAccuracy_f(mpfr_sin, child_sinf, pow(1.092, i), 3.5);
    for(i64=(int64_t)-1000;i64<(int64_t)1000 && success;i64+=(int64_t)1) {
      double start = u2f(f2u(M_PI_4 * i64)-20), end = u2f(f2u(M_PI_4 * i64)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracy_f(mpfr_sin, child_sinf, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "sinf_u1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_sin, child_sinf_u1, d, 1.0);
    for(d = -10000;d < 10000 && success;d += 1.1) checkAccuracy_f(mpfr_sin, child_sinf_u1, d, 1.0);
    for(i = 0;i < 1000 && success;i++) checkAccuracy_f(mpfr_sin, child_sinf_u1, pow(1.092, i), 1.0);
    for(i64=(int64_t)-1000;i64<(int64_t)1000 && success;i64+=(int64_t)1) {
      double start = u2f(f2u(M_PI_4 * i64)-20), end = u2f(f2u(M_PI_4 * i64)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracy_f(mpfr_sin, child_sinf_u1, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "sin in sincosf : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracyX_f(mpfr_sin, child_sincosf, d, 3.5);
    for(d = -10000;d < 10000 && success;d += 1.1) checkAccuracyX_f(mpfr_sin, child_sincosf, d, 3.5);
    for(i = 0;i < 1000 && success;i++) checkAccuracyX_f(mpfr_sin, child_sincosf, pow(1.092, i), 3.5);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2f(f2u(M_PI_4 * i)-20), end = u2f(f2u(M_PI_4 * i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyX_f(mpfr_sin, child_sincosf, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "sin in sincosf_u1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracyX_f(mpfr_sin, child_sincosf_u1, d, 1.0);
    for(d = -10000;d < 10000 && success;d += 1.1) checkAccuracyX_f(mpfr_sin, child_sincosf_u1, d, 1.0);
    for(i = 0;i < 1000 && success;i++) checkAccuracyX_f(mpfr_sin, child_sincosf_u1, pow(1.092, i), 1.0);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2f(f2u(M_PI_4 * i)-20), end = u2f(f2u(M_PI_4 * i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyX_f(mpfr_sin, child_sincosf_u1, d, 1.0);
    }
    showResult(success);

    //

    // 256 > 128(=maximum SP exponent) + 24(=number of bits in SP mantissa)
    mpfr_set_default_prec(256);

    fprintf(stderr, "sin in sincospif_u35 : ");
    for(d = -10.1;d < 10 && success;d += 0.0021) checkAccuracyX_f(mpfr_sinpi, child_sincospif_u35, d, 3.5);
    for(d = -10000-0.1;d < 10000 && success;d += 1.1) checkAccuracyX_f(mpfr_sinpi, child_sincospif_u35, d, 3.5);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2f(f2u(i)-20), end = u2f(f2u(i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyX_f(mpfr_sinpi, child_sincospif_u35, d, 3.5);
    }
    for(i=1;i<=20 && success;i++) {
      double start = u2f(f2u(0.25 * i)-20), end = u2f(f2u(0.25 * i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyX_f(mpfr_sinpi, child_sincospif_u35, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "sin in sincospif_u05 : ");
    for(d = -10.1;d < 10 && success;d += 0.0021) checkAccuracyX_f(mpfr_sinpi, child_sincospif_u05, d, 0.506);
    for(d = -10000-0.1;d < 10000 && success;d += 1.1) checkAccuracyX_f(mpfr_sinpi, child_sincospif_u05, d, 0.506);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2f(f2u(i)-20), end = u2f(f2u(i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyX_f(mpfr_sinpi, child_sincospif_u05, d, 0.506);
    }
    for(i=1;i<=20 && success;i++) {
      double start = u2f(f2u(0.25 * i)-20), end = u2f(f2u(0.25 * i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyX_f(mpfr_sinpi, child_sincospif_u05, d, 0.506);
    }
    showResult(success);

    //

    fprintf(stderr, "sinpif_u05 : ");
    for(d = -10.1;d < 10 && success;d += 0.0021) checkAccuracy_f(mpfr_sinpi, child_sinpif_u05, d, 0.506);
    for(d = -10000-0.1;d < 10000 && success;d += 1.1) checkAccuracy_f(mpfr_sinpi, child_sinpif_u05, d, 0.506);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2f(f2u(i)-20), end = u2f(f2u(i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracy_f(mpfr_sinpi, child_sinpif_u05, d, 0.506);
    }
    for(i=1;i<=20 && success;i++) {
      double start = u2f(f2u(0.25 * i)-20), end = u2f(f2u(0.25 * i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracy_f(mpfr_sinpi, child_sinpif_u05, d, 0.506);
    }
    showResult(success);

    //

    fprintf(stderr, "cospif_u05 : ");
    for(d = -10.1;d < 10 && success;d += 0.0021) checkAccuracy_f(mpfr_cospi, child_cospif_u05, d, 0.506);
    for(d = -10000-0.1;d < 10000 && success;d += 1.1) checkAccuracy_f(mpfr_cospi, child_cospif_u05, d, 0.506);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2f(f2u(i)-20), end = u2f(f2u(i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracy_f(mpfr_cospi, child_cospif_u05, d, 0.506);
    }
    for(i=1;i<=20 && success;i++) {
      double start = u2f(f2u(0.25 * i)-20), end = u2f(f2u(0.25 * i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracy_f(mpfr_cospi, child_cospif_u05, d, 0.506);
    }
    showResult(success);

    mpfr_set_default_prec(53);

    //

    fprintf(stderr, "cosf : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_cos, child_cosf, d, 3.5);
    for(d = -10000;d < 10000 && success;d += 1.1) checkAccuracy_f(mpfr_cos, child_cosf, d, 3.5);
    for(i = 0;i < 1000 && success;i++) checkAccuracy_f(mpfr_cos, child_cosf, pow(1.092, i), 3.5);
    for(i64=(int64_t)-1000;i64<(int64_t)1000 && success;i64+=(int64_t)1) {
      double start = u2f(f2u(M_PI_4 * i64)-20), end = u2f(f2u(M_PI_4 * i64)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracy_f(mpfr_cos, child_cosf, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "cosf_u1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_cos, child_cosf_u1, d, 1.0);
    for(d = -10000;d < 10000 && success;d += 1.1) checkAccuracy_f(mpfr_cos, child_cosf_u1, d, 1.0);
    for(i = 0;i < 1000 && success;i++) checkAccuracy_f(mpfr_cos, child_cosf_u1, pow(1.092, i), 1.0);
    for(i64=(int64_t)-1000;i64<(int64_t)1000 && success;i64+=(int64_t)1) {
      double start = u2f(f2u(M_PI_4 * i64)-20), end = u2f(f2u(M_PI_4 * i64)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracy_f(mpfr_cos, child_cosf_u1, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "cos in sincosf : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracyY_f(mpfr_cos, child_sincosf, d, 3.5);
    for(d = -10000;d < 10000 && success;d += 1.1) checkAccuracyY_f(mpfr_cos, child_sincosf, d, 3.5);
    for(i = 0;i < 1000 && success;i++) checkAccuracyY_f(mpfr_cos, child_sincosf, pow(1.092, i), 3.5);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2f(f2u(M_PI_4 * i)-20), end = u2f(f2u(M_PI_4 * i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyY_f(mpfr_cos, child_sincosf, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "cos in sincosf_u1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracyY_f(mpfr_cos, child_sincosf_u1, d, 1.0);
    for(d = -10000;d < 10000 && success;d += 1.1) checkAccuracyY_f(mpfr_cos, child_sincosf_u1, d, 1.0);
    for(i = 0;i < 1000 && success;i++) checkAccuracyY_f(mpfr_cos, child_sincosf_u1, pow(1.092, i), 1.0);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2f(f2u(M_PI_4 * i)-20), end = u2f(f2u(M_PI_4 * i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyY_f(mpfr_cos, child_sincosf_u1, d, 1.0);
    }
    showResult(success);

    //

    mpfr_set_default_prec(256);

    fprintf(stderr, "cos in sincospif_u35 : ");
    for(d = -10.1;d < 10 && success;d += 0.0021) checkAccuracyY_f(mpfr_cospi, child_sincospif_u35, d, 3.5);
    for(d = -10000-0.1;d < 10000 && success;d += 1.1) checkAccuracyY_f(mpfr_cospi, child_sincospif_u35, d, 3.5);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2f(f2u(i)-20), end = u2f(f2u(i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyY_f(mpfr_cospi, child_sincospif_u35, d, 3.5);
    }
    for(i=1;i<=20 && success;i++) {
      double start = u2f(f2u(0.25 * i)-20), end = u2f(f2u(0.25 * i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyY_f(mpfr_cospi, child_sincospif_u35, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "cos in sincospif_u05 : ");
    for(d = -10.1;d < 10 && success;d += 0.0021) checkAccuracyY_f(mpfr_cospi, child_sincospif_u05, d, 0.506);
    for(d = -10000-0.1;d < 10000 && success;d += 1.1) checkAccuracyY_f(mpfr_cospi, child_sincospif_u05, d, 0.506);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2f(f2u(i)-20), end = u2f(f2u(i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyY_f(mpfr_cospi, child_sincospif_u05, d, 0.506);
    }
    for(i=1;i<=20 && success;i++) {
      double start = u2f(f2u(0.25 * i)-20), end = u2f(f2u(0.25 * i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracyY_f(mpfr_cospi, child_sincospif_u05, d, 0.506);
    }
    showResult(success);

    mpfr_set_default_prec(53);

    //

    fprintf(stderr, "fastsinf_u3500 : ");
    for(d = -32;d < 32 && success;d += 0.001) checkAccuracy2_f(mpfr_sin, child_fastsinf_u3500, d, 350, 2e-6);
    showResult(success);

    fprintf(stderr, "fastcosf_u3500 : ");
    for(d = -32;d < 32 && success;d += 0.001) checkAccuracy2_f(mpfr_cos, child_fastcosf_u3500, d, 350, 2e-6);
    showResult(success);

    //

    fprintf(stderr, "tanf : ");
    checkAccuracy_f(mpfr_tan, child_tanf, 70.936981201171875, 3.5);
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_tan, child_tanf, d, 3.5);
    for(d = -10000;d < 10000 && success;d += 1.1) checkAccuracy_f(mpfr_tan, child_tanf, d, 3.5);
    for(i = 0;i < 1000 && success;i++) checkAccuracy_f(mpfr_tan, child_tanf, pow(1.092, i), 3.5);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2f(f2u(M_PI_4 * i)-20), end = u2f(f2u(M_PI_4 * i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracy_f(mpfr_tan, child_tanf, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "tanf_u1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_tan, child_tanf_u1, d, 1.0);
    for(d = -10000;d < 10000 && success;d += 1.1) checkAccuracy_f(mpfr_tan, child_tanf_u1, d, 1.0);
    for(i = 0;i < 1000 && success;i++) checkAccuracy_f(mpfr_tan, child_tanf_u1, pow(1.092, i), 1.0);
    for(i=1;i<10000 && success;i+=31) {
      double start = u2f(f2u(M_PI_4 * i)-20), end = u2f(f2u(M_PI_4 * i)+20);
      for(d = start;d <= end;d = u2f(f2u(d)+1)) checkAccuracy_f(mpfr_tan, child_tanf_u1, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "logf : ");
    for(d = 0.0001;d < 10 && success;d += 0.001) checkAccuracy_f(mpfr_log, child_logf, d, 3.5);
    for(d = 0.0001;d < 10000 && success;d += 1.1) checkAccuracy_f(mpfr_log, child_logf, d, 3.5);
    for(i = -1000;i <= 1000 && success;i+=10) checkAccuracy_f(mpfr_log, child_logf, pow(2.1, i), 3.5);
    for(i=0;i<10000 && success;i+=10) checkAccuracy_f(mpfr_log, child_logf, FLT_MAX * pow(0.9314821319758632, i), 3.5);
    for(i=0;i<10000 && success;i+=10) checkAccuracy_f(mpfr_log, child_logf, pow(0.933254300796991, i), 3.5);
    for(i=0;i<10000 && success;i+=10) checkAccuracy_f(mpfr_log, child_logf, FLT_MIN * pow(0.996323, i), 3.5);
    showResult(success);

    //

    fprintf(stderr, "logf_u1 : ");
    for(d = 0.0001;d < 10 && success;d += 0.001) checkAccuracy_f(mpfr_log, child_logf_u1, d, 1.0);
    for(d = 0.0001;d < 10000 && success;d += 1.1) checkAccuracy_f(mpfr_log, child_logf_u1, d, 1.0);

    if (!enableFlushToZero) {
      for(i=0;i<10000 && success;i+=10) checkAccuracy_f(mpfr_log, child_logf_u1, FLT_MAX * pow(0.9314821319758632, i), 1.0);
      for(i = -1000;i <= 1000 && success;i+=10) checkAccuracy_f(mpfr_log, child_logf_u1, pow(2.1, i), 1.0);
      for(i=0;i<10000 && success;i+=10) checkAccuracy_f(mpfr_log, child_logf_u1, pow(0.933254300796991, i), 1.0);
      for(i=0;i<10000 && success;i+=10) checkAccuracy_f(mpfr_log, child_logf_u1, FLT_MIN * pow(0.996323, i), 1.0);
      for(d = 0.0001;d < 10 && success;d += 0.001) checkAccuracy_f(mpfr_log, child_logf_u1, d, 1.0);
      for(d = 0.0001;d < 10000 && success;d += 1.1) checkAccuracy_f(mpfr_log, child_logf_u1, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "expf : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_exp, child_expf, d, 1.0);
    if (!enableFlushToZero) {
      for(d = -1000;d < 1000 && success;d += 1.1) checkAccuracy_f(mpfr_exp, child_expf, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "powf : ");
    if (!enableFlushToZero) {
      for(y = 0.1;y < 100 && success;y += 0.6) {
        for(x = -100;x < 100 && success;x += 0.6) {
          checkAccuracy_f_f(mpfr_pow, child_powf, x, y, 1.0);
        }
      }
      for(y = -1000;y < 1000 && success;y += 0.1) checkAccuracy_f_f(mpfr_pow, child_powf, 2.1, y, 1.0);
    } else {
      for(y = 0.1;y < 10 && success;y += 0.06) {
        for(x = -100;x < 10 && success;x += 0.06) {
          checkAccuracy_f_f(mpfr_pow, child_powf, x, y, 1.0);
        }
      }
    }
    showResult(success);

    //

    fprintf(stderr, "fastpowf_u3500 : ");
    for(y = -25;y < 25 && success;y += 0.121) {
      for(x = 0.1;x < 25 && success;x += 0.251) {
        checkAccuracy_f_f(mpfr_pow, child_fastpowf_u3500, x, y, 350);
      }
    }
    showResult(success);

    //

    if (!deterministicMode) {
      fprintf(stderr, "sqrtf : ");
      if (!enableFlushToZero) {
        for(d = -10000;d < 10000 && success;d += 2.1) checkAccuracy_f(mpfr_sqrt, child_sqrtf, d, 1.0);
      }
      for(i = -1000;i <= 1000 && success;i+=10) checkAccuracy_f(mpfr_sqrt, child_sqrtf, pow(2.1, d), 1.0);
      showResult(success);

      //

      fprintf(stderr, "sqrtf_u05 : ");
      if (!enableFlushToZero) {
        for(d = -10000;d < 10000 && success;d += 2.1) checkAccuracy_f(mpfr_sqrt, child_sqrtf_u05, d, 0.506);
      }
      for(i = -1000;i <= 1000 && success;i+=10) checkAccuracy_f(mpfr_sqrt, child_sqrtf_u05, pow(2.1, d), 0.506);
      showResult(success);

      //

      fprintf(stderr, "sqrtf_u35 : ");
      if (!enableFlushToZero) {
        for(d = -10000;d < 10000 && success;d += 2.1) checkAccuracy_f(mpfr_sqrt, child_sqrtf_u35, d, 3.5);
      }
      for(i = -1000;i <= 1000 && success;i+=10) checkAccuracy_f(mpfr_sqrt, child_sqrtf_u35, pow(2.1, d), 3.5);
      showResult(success);
    }

    //

    fprintf(stderr, "cbrtf : ");
    if (!enableFlushToZero) {
      for(d = -10000;d < 10000 && success;d += 2.1) checkAccuracy_f(mpfr_cbrt, child_cbrtf, d, 3.5);
    }
    for(i = -1000;i <= 1000 && success;i+=10) checkAccuracy_f(mpfr_cbrt, child_cbrtf, pow(2.1, d), 3.5);
    showResult(success);

    //

    fprintf(stderr, "cbrtf_u1 : ");
    if (!enableFlushToZero) {
      for(d = -10000;d < 10000 && success;d += 2.1) checkAccuracy_f(mpfr_cbrt, child_cbrtf_u1, d, 1.0);
    }
    for(i = -1000;i <= 1000 && success;i+=10) checkAccuracy_f(mpfr_cbrt, child_cbrtf_u1, pow(2.1, d), 1.0);
    showResult(success);

    //

    fprintf(stderr, "asinf : ");
    for(d = -1;d < 1 && success;d += 0.0002) checkAccuracy_f(mpfr_asin, child_asinf, d, 3.5);
    showResult(success);

    //

    fprintf(stderr, "asinf_u1 : ");
    for(d = -1;d < 1 && success;d += 0.0002) checkAccuracy_f(mpfr_asin, child_asinf_u1, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "acosf : ");
    for(d = -1;d < 1 && success;d += 0.0002) checkAccuracy_f(mpfr_acos, child_acosf, d, 3.5);
    showResult(success);

    //

    fprintf(stderr, "acosf_u1 : ");
    for(d = -1;d < 1 && success;d += 0.0002) checkAccuracy_f(mpfr_acos, child_acosf_u1, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "atanf : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_atan, child_atanf, d, 3.5);
    for(d = -10000;d < 10000 && success;d += 2.1) checkAccuracy_f(mpfr_atan, child_atanf, d, 3.5);
    showResult(success);

    //

    fprintf(stderr, "atanf_u1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_atan, child_atanf_u1, d, 1.0);
    for(d = -10000;d < 10000 && success;d += 2.1) checkAccuracy_f(mpfr_atan, child_atanf_u1, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "atan2f : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_f_f(mpfr_atan2, child_atan2f, y, x, 3.5);
    }
    for(y = -100;y < 100 && success;y += 1.51) {
      for(x = -100;x < 100 && success;x += 1.51) checkAccuracy_f_f(mpfr_atan2, child_atan2f, y, x, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "atan2f_u1 : ");
    for(y = -10;y < 10 && success;y += 0.15) {
      for(x = -10;x < 10 && success;x += 0.15) checkAccuracy_f_f(mpfr_atan2, child_atan2f_u1, y, x, 1.0);
    }
    for(y = -100;y < 100 && success;y += 1.51) {
      for(x = -100;x < 100 && success;x += 1.51) checkAccuracy_f_f(mpfr_atan2, child_atan2f_u1, y, x, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "sinhf : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_sinh, child_sinhf, d, 1.0);
    if (!enableFlushToZero) {
      for(d = -88;d < 88 && success;d += 0.2) checkAccuracy_f(mpfr_sinh, child_sinhf, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "coshf : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_cosh, child_coshf, d, 1.0);
    if (!enableFlushToZero) {
      for(d = -88;d < 88 && success;d += 0.2) checkAccuracy_f(mpfr_cosh, child_coshf, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "tanhf : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_tanh, child_tanhf, d, 1.0);
    if (!enableFlushToZero) {
      for(d = -1000;d < 1000 && success;d += 0.2) checkAccuracy_f(mpfr_tanh, child_tanhf, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "sinhf_u35 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_sinh, child_sinhf_u35, d, 3.5);
    if (!enableFlushToZero) {
      for(d = -88;d < 88 && success;d += 0.2) checkAccuracy_f(mpfr_sinh, child_sinhf_u35, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "coshf_u35 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_cosh, child_coshf_u35, d, 3.5);
    if (!enableFlushToZero) {
      for(d = -88;d < 88 && success;d += 0.2) checkAccuracy_f(mpfr_cosh, child_coshf_u35, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "tanhf_u35 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_tanh, child_tanhf_u35, d, 3.5);
    if (!enableFlushToZero) {
      for(d = -1000;d < 1000 && success;d += 0.2) checkAccuracy_f(mpfr_tanh, child_tanhf_u35, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "asinhf : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_asinh, child_asinhf, d, 1.0);
    if (!enableFlushToZero) {
      for(d = -1000;d < 1000 && success;d += 0.2) checkAccuracy_f(mpfr_asinh, child_asinhf, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "acoshf : ");
    for(d = 1;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_acosh, child_acoshf, d, 1.0);
    if (!enableFlushToZero) {
      for(d = 1;d < 1000 && success;d += 0.2) checkAccuracy_f(mpfr_acosh, child_acoshf, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "atanhf : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_atanh, child_atanhf, d, 1.0);
    if (!enableFlushToZero) {
      for(d = -1000;d < 1000 && success;d += 0.2) checkAccuracy_f(mpfr_atanh, child_atanhf, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "exp2f : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_exp2, child_exp2f, d, 1.0);
    if (!enableFlushToZero) {
      for(d = -1000;d < 1000 && success;d += 0.2) checkAccuracy_f(mpfr_exp2, child_exp2f, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "exp10f : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_exp10, child_exp10f, d, 1.0);
    if (!enableFlushToZero) {
      for(d = -300;d < 300 && success;d += 0.1) checkAccuracy_f(mpfr_exp10, child_exp10f, d, 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "exp2f_u35 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_exp2, child_exp2f_u35, d, 3.5);
    if (!enableFlushToZero) {
      for(d = -1000;d < 1000 && success;d += 0.2) checkAccuracy_f(mpfr_exp2, child_exp2f_u35, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "exp10f_u35 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_exp10, child_exp10f_u35, d, 3.5);
    if (!enableFlushToZero) {
      for(d = -300;d < 300 && success;d += 0.1) checkAccuracy_f(mpfr_exp10, child_exp10f_u35, d, 3.5);
    }
    showResult(success);

    //

    fprintf(stderr, "expm1f : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_expm1, child_expm1f, d, 1.0);
    if (!enableFlushToZero) {
      for(d = -1000;d < 1000 && success;d += 0.21) checkAccuracy_f(mpfr_expm1, child_expm1f, d, 1.0);
      for(d = 0;d < 300 && success;d += 0.21) checkAccuracy_f(mpfr_expm1, child_expm1f, pow(10, -d), 1.0);
      for(d = 0;d < 300 && success;d += 0.21) checkAccuracy_f(mpfr_expm1, child_expm1f, (-pow(10, -d)), 1.0);
    }
    showResult(success);

    //

    fprintf(stderr, "log10f : ");
    for(d = 0.0001;d < 10 && success;d += 0.001) checkAccuracy_f(mpfr_log10, child_log10f, d, 1.0);
    for(d = 0.0001;d < 10000 && success;d += 1.1) checkAccuracy_f(mpfr_log10, child_log10f, d, 1.0);
    for(i=0;i<10000 && success;i++) checkAccuracy_f(mpfr_log10, child_log10f, (FLT_MIN * pow(0.996323, i)), 1.0);
    showResult(success);

    //

    fprintf(stderr, "log2f : ");
    for(d = 0.0001;d < 10 && success;d += 0.001) checkAccuracy_f(mpfr_log2, child_log2f, d, 1.0);
    for(d = 0.0001;d < 10000 && success;d += 1.1) checkAccuracy_f(mpfr_log2, child_log2f, d, 1.0);
    for(i=0;i<10000 && success;i++) checkAccuracy_f(mpfr_log2, child_log2f, (FLT_MIN * pow(0.996323, i)), 1.0);
    showResult(success);

    //

    fprintf(stderr, "log2f_u35 : ");
    for(d = 0.0001;d < 10 && success;d += 0.001) checkAccuracy_f(mpfr_log2, child_log2f_u35, d, 3.5);
    for(d = 0.0001;d < 10000 && success;d += 1.1) checkAccuracy_f(mpfr_log2, child_log2f_u35, d, 3.5);
    for(i=0;i<10000 && success;i++) checkAccuracy_f(mpfr_log2, child_log2f_u35, (FLT_MIN * pow(0.996323, i)), 3.5);
    showResult(success);

    //

    fprintf(stderr, "log1pf : ");
    for(d = 0.0001;d < 10 && success;d += 0.001) checkAccuracy_f(mpfr_log1p, child_log1pf, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "lgammaf_u1 : ");
    for(d = -5000;d < 5000 && success;d += 1.1) checkAccuracy_f(mpfr_lgamma_nosign, child_lgammaf_u1, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "tgammaf_u1 : ");
    for(d = -10;d < 10 && success;d += 0.002) checkAccuracy_f(mpfr_gamma, child_tgammaf_u1, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "erff_u1 : ");
    for(d = -100;d < 100 && success;d += 0.02) checkAccuracy_f(mpfr_erf, child_erff_u1, d, 1.0);
    showResult(success);

    //

    fprintf(stderr, "erfcf_u15 : ");
    for(d = -1;d < 8 && success;d += 0.001) checkAccuracy_f(mpfr_erfc, child_erfcf_u15, d, 1.5);
    showResult(success);
  }
  mpfr_clears(frc, frt, frx, fry, frz, NULL);
}

int main(int argc, char **argv) {
  char *argv2[argc+2], *commandSde = NULL, *commandQEmu = NULL;
  int i, a2s;

  // BUGFIX: this flush is to prevent incorrect syncing with the
  // `iut*` executable that causes failures in the CPU detection on
  // some CI systems.
  fflush(stdout);

  for(a2s=1;a2s<argc;a2s++) {
    if (strcmp(argv[a2s], "--flushtozero") == 0) {
      enableFlushToZero = 1;
    } else if (a2s+1 < argc && strcmp(argv[a2s], "--sde") == 0) {
      commandSde = argv[a2s+1];
      a2s++;
    } else if (a2s+1 < argc && strcmp(argv[a2s], "--qemu") == 0) {
      commandQEmu = argv[a2s+1];
      a2s++;
    } else {
      break;
    }
  }

  printf("\n\n*** Now testing %s\n", argv[a2s]);

  for(i=a2s;i<argc;i++) argv2[i-a2s] = argv[i];
  argv2[argc-a2s] = NULL;

  mpfr_set_default_prec(64);

  startChild(argv2[0], argv2);
  fflush(stdin);
  // BUGFIX: this flush is to prevent incorrect syncing with the
  // `iut*` executable that causes failures in the CPU detection on
  // some CI systems.
  fflush(stdin);

  {
    char str[256];
    int u;

    if (readln(ctop[0], str, 255) < 1 ||
        sscanf(str, "%d", &u) != 1 ||
        (u & 3) == 0) {
      if (commandSde != NULL || commandQEmu != NULL) {
        close(ctop[0]);
        close(ptoc[1]);

        if (commandSde) {
          argv2[0] = commandSde;
          argv2[1] = "--";
          for(i=a2s;i<argc;i++) argv2[i-a2s+2] = argv[i];
          argv2[argc-a2s+2] = NULL;
        } else {
          argv2[0] = commandQEmu;
          for(i=a2s;i<argc;i++) argv2[i-a2s+1] = argv[i];
          argv2[argc-a2s+1] = NULL;
        }

        startChild(argv2[0], argv2);

        if (readln(ctop[0], str, 255) < 1) stop("Feature detection(sde, readln)");
        if (sscanf(str, "%d", &u) != 1) stop("Feature detection(sde, sscanf)");
        if ((u & 3) == 0) {
          fprintf(stderr, "\n\nTester : *** CPU does not support the necessary feature(SDE)\n");
          return 0;
        }

        printf("*** Using emulator\n");
      } else {
        int status;
        waitpid(pid, &status, 0);
        if (WIFSIGNALED(status)) {
          fprintf(stderr, "\n\nTester : *** Child process has crashed\n");
          return -1;
        }

        fprintf(stderr, "\n\nTester : *** CPU does not support the necessary feature\n");
        return 0;
      }
    }

    enableDP = (u & 1) != 0;
    enableSP = (u & 2) != 0;
    enableFlushToZero |= ((u & 4) != 0);
    deterministicMode |= ((u & 8) != 0);
  }

  if (enableFlushToZero) fprintf(stderr, "\n\n*** Flush to zero enabled\n");

  fpctop = fdopen(ctop[0], "r");

  do_test();

  if (allTestsPassed) {
    fprintf(stderr, "\n\n*** All tests passed\n");
  } else {
    fprintf(stderr, "\n\n*** There were errors in some tests\n");
  }

  if (allTestsPassed) return 0;

  return -1;
}
