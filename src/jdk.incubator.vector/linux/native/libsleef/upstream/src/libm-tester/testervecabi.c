#include <stdio.h>
#include <stdlib.h>

#define SLEEF_ENABLE_OMP_SIMD
#include <sleef.h>

#define N (65536 - 1)
#define THRES 1e-10
#define THRESF 0.02

double a[N], b[N], c[N], d[N];
float e[N], f[N], g[N], h[N];

static void check(char *mes, double thres) {
  double err = 0;
  for (int i = 0; i < N; i++) err += d[i] >= 0 ? d[i] : -d[i];
  if (err > thres) {
    printf("%s, error=%g\n", mes, err);
    exit(-1);
  }
}

static void checkf(char *mes, double thres) {
  double err = 0;
  for (int i = 0; i < N; i++) err += h[i] >= 0 ? h[i] : -h[i];
  if (err > thres) {
    printf("%s, error=%g\n", mes, err);
    exit(-1);
  }
}

void func00() {
// CHECK-AVX512: func00
// CHECK-AVX2: func00
// CHECK-SSE2: func00
  for (int i = 0; i < N; i++) d[i] = Sleef_asin_u10(Sleef_sin_u10(a[i])) - a[i];
  check("sin_u10, asin_u10", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_asin_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_sin_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_asin_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_sin_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_asin_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_sin_u10
}

void func01() {
// CHECK-AVX512: func01
// CHECK-AVX2: func01
// CHECK-SSE2: func01
  for (int i = 0; i < N; i++) d[i] = Sleef_asin_u35(Sleef_sin_u35(a[i])) - a[i];
  check("sin_u35, asin_u35", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_asin_u35
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_sin_u35
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_asin_u35
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_sin_u35
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_asin_u35
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_sin_u35
}

void func02() {
// CHECK-AVX512: func02
// CHECK-AVX2: func02
// CHECK-SSE2: func02
  for (int i = 0; i < N; i++) d[i] = Sleef_acos_u10(Sleef_cos_u10(a[i])) - a[i];
  check("cos_u10, acos_u10", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_acos_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_cos_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_acos_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_cos_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_acos_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_cos_u10
}

void func03() {
// CHECK-AVX512: func03
// CHECK-AVX2: func03
// CHECK-SSE2: func03
  for (int i = 0; i < N; i++) d[i] = Sleef_acos_u35(Sleef_cos_u35(a[i])) - a[i];
  check("cos_u35, acos_u35", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_acos_u35
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_cos_u35
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_acos_u35
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_cos_u35
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_acos_u35
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_cos_u35
}

void func04() {
// CHECK-AVX512: func04
// CHECK-AVX2: func04
// CHECK-SSE2: func04
  for (int i = 0; i < N; i++) d[i] = Sleef_atan_u10(Sleef_tan_u10(a[i])) - a[i];
  check("tan_u10, atan_u10", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_atan_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_tan_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_atan_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_tan_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_atan_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_tan_u10
}

void func05() {
// CHECK-AVX512: func05
// CHECK-AVX2: func05
// CHECK-SSE2: func05
  for (int i = 0; i < N; i++) d[i] = Sleef_atan_u35(Sleef_tan_u35(a[i])) - a[i];
  check("tan_u35, atan_u35", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_atan_u35
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_tan_u35
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_atan_u35
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_tan_u35
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_atan_u35
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_tan_u35
}

void func06() {
// CHECK-AVX512: func06
// CHECK-AVX2: func06
// CHECK-SSE2: func06
  for (int i = 0; i < N; i++) d[i] = Sleef_atan2_u10(b[i] * Sleef_sinpi_u05(a[i]*0.1), b[i] * Sleef_cospi_u05(a[i]*0.1)) - a[i]*0.3141592653589793;
  check("sinpi_u05, cospi_u05, atan2_u10", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_sinpi_u05
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_cospi_u05
// CHECK-AVX2-DAG: _ZGVdN4vv_Sleef_atan2_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_sinpi_u05
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_cospi_u05
// CHECK-AVX512-DAG: _ZGVeN8vv_Sleef_atan2_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_sinpi_u05
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_cospi_u05
// CHECK-SSE2-DAG: _ZGVbN2vv_Sleef_atan2_u10
}

void func07() {
// CHECK-AVX512: func07
// CHECK-AVX2: func07
// CHECK-SSE2: func07
  for (int i = 0; i < N; i++) d[i] = Sleef_atan2_u35(b[i] * Sleef_sinpi_u05(a[i]*0.1), b[i] * Sleef_cospi_u05(a[i]*0.1)) - a[i]*0.3141592653589793;
  check("sinpi_u05, cospi_u05, atan2_u35", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_sinpi_u05
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_cospi_u05
// CHECK-AVX2-DAG: _ZGVdN4vv_Sleef_atan2_u35
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_sinpi_u05
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_cospi_u05
// CHECK-AVX512-DAG: _ZGVeN8vv_Sleef_atan2_u35
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_sinpi_u05
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_cospi_u05
// CHECK-SSE2-DAG: _ZGVbN2vv_Sleef_atan2_u35
}

void func08() {
// CHECK-AVX512: func08
// CHECK-AVX2: func08
// CHECK-SSE2: func08
  for (int i = 0; i < N; i++) d[i] = Sleef_log2_u10(Sleef_exp2_u10(a[i])) - a[i];
  check("log2_u10, exp2_u10", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_log2_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_exp2_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_log2_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_exp2_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_log2_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_exp2_u10
}

void func09() {
// CHECK-AVX512: func09
// CHECK-AVX2: func09
// CHECK-SSE2: func09
  for (int i = 0; i < N; i++) d[i] = Sleef_log2_u35(Sleef_exp2_u35(a[i])) - a[i];
  check("log2_u35, exp2_u35", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_log2_u35
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_exp2_u35
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_log2_u35
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_exp2_u35
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_log2_u35
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_exp2_u35
}

void func10() {
// CHECK-AVX512: func10
// CHECK-AVX2: func10
// CHECK-SSE2: func10
  for (int i = 0; i < N; i++) d[i] = Sleef_log10_u10(Sleef_exp10_u35(a[i])) - a[i];
  check("log10_u10, exp10_u35", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_log10_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_exp10_u35
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_log10_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_exp10_u35
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_log10_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_exp10_u35
}

void func11() {
// CHECK-AVX512: func11
// CHECK-AVX2: func11
// CHECK-SSE2: func11
  for (int i = 0; i < N; i++) d[i] = Sleef_log10_u10(Sleef_exp10_u10(a[i])) - a[i];
  check("log10_u10, exp10_u10", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_log10_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_exp10_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_log10_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_exp10_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_log10_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_exp10_u10
}

void func12() {
// CHECK-AVX512: func12
// CHECK-AVX2: func12
// CHECK-SSE2: func12
  for (int i = 0; i < N; i++) d[i] = Sleef_log1p_u10(Sleef_expm1_u10(a[i])) - a[i];
  check("log1p_u10, expm1_u10", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_log1p_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_expm1_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_log1p_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_expm1_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_log1p_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_expm1_u10
}

void func13() {
// CHECK-AVX512: func13
// CHECK-AVX2: func13
// CHECK-SSE2: func13
  for (int i = 0; i < N; i++) d[i] = Sleef_pow_u10(a[i], b[i]) - Sleef_exp_u10(Sleef_log_u10(a[i]) * b[i]);
  check("pow_u10, exp_u10, log_u10", THRES);
// CHECK-AVX2-DAG: _ZGVdN4vv_Sleef_pow_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_exp_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_log_u10
// CHECK-AVX512-DAG: _ZGVeN8vv_Sleef_pow_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_exp_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_log_u10
// CHECK-SSE2-DAG: _ZGVbN2vv_Sleef_pow_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_exp_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_log_u10
}

void func14() {
// CHECK-AVX512: func14
// CHECK-AVX2: func14
// CHECK-SSE2: func14
  for (int i = 0; i < N; i++) d[i] = Sleef_pow_u10(a[i], b[i]) - Sleef_exp_u10(Sleef_log_u35(a[i]) * b[i]);
  check("pow_u10, exp_u10, log_u35", THRES);
// CHECK-AVX2-DAG: _ZGVdN4vv_Sleef_pow_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_exp_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_log_u35
// CHECK-AVX512-DAG: _ZGVeN8vv_Sleef_pow_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_exp_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_log_u35
// CHECK-SSE2-DAG: _ZGVbN2vv_Sleef_pow_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_exp_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_log_u35
}

void func15() {
// CHECK-AVX512: func15
// CHECK-AVX2: func15
// CHECK-SSE2: func15
  for (int i = 0; i < N; i++) d[i] = Sleef_cbrt_u10(a[i] * a[i] * a[i]) - a[i];
  check("cbrt_u10", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_cbrt_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_cbrt_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_cbrt_u10
}

void func16() {
// CHECK-AVX512: func16
// CHECK-AVX2: func16
// CHECK-SSE2: func16
  for (int i = 0; i < N; i++) d[i] = Sleef_cbrt_u35(a[i] * a[i] * a[i]) - a[i];
  check("cbrt_u35", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_cbrt_u35
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_cbrt_u35
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_cbrt_u35
}

void func17() {
// CHECK-AVX512: func17
// CHECK-AVX2: func17
// CHECK-SSE2: func17
  for (int i = 0; i < N; i++) d[i] = Sleef_asinh_u10(Sleef_sinh_u10(a[i])) - a[i];
  check("asinh_u10, sinh_u10", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_asinh_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_sinh_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_asinh_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_sinh_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_asinh_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_sinh_u10
}

void func18() {
// CHECK-AVX512: func18
// CHECK-AVX2: func18
// CHECK-SSE2: func18
  for (int i = 0; i < N; i++) d[i] = Sleef_asinh_u10(Sleef_sinh_u35(a[i])) - a[i];
  check("asinh_u10, sinh_u35", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_asinh_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_sinh_u35
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_asinh_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_sinh_u35
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_asinh_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_sinh_u35
}

void func19() {
// CHECK-AVX512: func19
// CHECK-AVX2: func19
// CHECK-SSE2: func19
  for (int i = 0; i < N; i++) d[i] = Sleef_acosh_u10(Sleef_cosh_u10(a[i])) - a[i];
  check("acosh_u10, cosh_u10", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_acosh_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_cosh_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_acosh_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_cosh_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_acosh_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_cosh_u10
}

void func20() {
// CHECK-AVX512: func20
// CHECK-AVX2: func20
// CHECK-SSE2: func20
  for (int i = 0; i < N; i++) d[i] = Sleef_acosh_u10(Sleef_cosh_u35(a[i])) - a[i];
  check("acosh_u10, cosh_u35", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_acosh_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_cosh_u35
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_acosh_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_cosh_u35
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_acosh_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_cosh_u35
}

void func21() {
// CHECK-AVX512: func21
// CHECK-AVX2: func21
// CHECK-SSE2: func21
  for (int i = 0; i < N; i++) d[i] = Sleef_atanh_u10(Sleef_tanh_u10(a[i])) - a[i];
  check("atanh_u10, tanh_u10", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_atanh_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_tanh_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_atanh_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_tanh_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_atanh_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_tanh_u10
}

void func22() {
// CHECK-AVX512: func22
// CHECK-AVX2: func22
// CHECK-SSE2: func22
  for (int i = 0; i < N; i++) d[i] = Sleef_atanh_u10(Sleef_tanh_u35(a[i])) - a[i];
  check("atanh_u10, tanh_u35", THRES);
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_atanh_u10
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_tanh_u35
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_atanh_u10
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_tanh_u35
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_atanh_u10
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_tanh_u35
}

void func23() {
// CHECK-AVX512: func23
// CHECK-AVX2: func23
// CHECK-SSE2: func23
  for (int i = 0; i < N; i++) d[i] = Sleef_fma(a[i], b[i], c[i]) - (a[i] * b[i] + c[i]);
  check("fma", THRES);
// CHECK-AVX2-DAG: _ZGVdN4vvv_Sleef_fma
// CHECK-AVX512-DAG: _ZGVeN8vvv_Sleef_fma
// CHECK-SSE2-DAG: _ZGVbN2vvv_Sleef_fma
}

void func24() {
// CHECK-AVX512: func24
// CHECK-AVX2: func24
// CHECK-SSE2: func24
  for (int i = 0; i < N; i++) d[i] = Sleef_hypot_u05(a[i], b[i]) - Sleef_sqrt_u05(a[i] * a[i] + b[i] * b[i]);
  check("hypot_u05, sqrt_u05", THRES);
// CHECK-AVX2-DAG: _ZGVdN4vv_Sleef_hypot_u05
// CHECK-AVX512-DAG: _ZGVeN8vv_Sleef_hypot_u05
// CHECK-SSE2-DAG: _ZGVbN2vv_Sleef_hypot_u05
}

void func25() {
// CHECK-AVX512: func25
// CHECK-AVX2: func25
// CHECK-SSE2: func25
  for (int i = 0; i < N; i++) d[i] = Sleef_hypot_u35(a[i], b[i]) - Sleef_sqrt_u05(a[i] * a[i] + b[i] * b[i]);
  check("hypot_u35, sqrt_u05", THRES);
// CHECK-AVX2-DAG: _ZGVdN4vv_Sleef_hypot_u35
// CHECK-AVX512-DAG: _ZGVeN8vv_Sleef_hypot_u35
// CHECK-SSE2-DAG: _ZGVbN2vv_Sleef_hypot_u35
}

void func26() {
// CHECK-AVX512: func26
// CHECK-AVX2: func26
// CHECK-SSE2: func26
  for (int i = 0; i < N; i++) d[i] = Sleef_fmod(a[i], b[i]) - (a[i] - Sleef_floor(a[i] / b[i]) * b[i]);
  check("fmod, floor", THRES);
// CHECK-AVX2-DAG: _ZGVdN4vv_Sleef_fmod
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_floor
// CHECK-AVX512-DAG: _ZGVeN8vv_Sleef_fmod
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_floor
// CHECK-SSE2-DAG: _ZGVbN2vv_Sleef_fmod
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_floor
}

void func27() {
// CHECK-AVX512: func27
// CHECK-AVX2: func27
// CHECK-SSE2: func27
  for (int i = 0; i < N; i++) d[i] = Sleef_remainder(a[i], b[i]) - (a[i] - Sleef_rint(a[i] / b[i]) * b[i]);
  check("remainder, rint", THRES);
// CHECK-AVX2-DAG: _ZGVdN4vv_Sleef_remainder
// CHECK-AVX2-DAG: _ZGVdN4v_Sleef_rint
// CHECK-AVX512-DAG: _ZGVeN8vv_Sleef_remainder
// CHECK-AVX512-DAG: _ZGVeN8v_Sleef_rint
// CHECK-SSE2-DAG: _ZGVbN2vv_Sleef_remainder
// CHECK-SSE2-DAG: _ZGVbN2v_Sleef_rint
}

void func28() {
// CHECK-AVX512: func28
// CHECK-AVX2: func28
// CHECK-SSE2: func28
  for (int i = 0; i < N; i++) d[i] = Sleef_nextafter(Sleef_nextafter(a[i], b[i]), -b[i]) - a[i];
  check("nextafter", THRES);
// CHECK-AVX2-DAG: _ZGVdN4vv_Sleef_nextafter
// CHECK-AVX512-DAG: _ZGVeN8vv_Sleef_nextafter
// CHECK-SSE2-DAG: _ZGVbN2vv_Sleef_nextafter
}

void func29() {
// CHECK-AVX512: func29
// CHECK-AVX2: func29
// CHECK-SSE2: func29
  for (int i = 0; i < N; i++) h[i] = Sleef_asinf_u10(Sleef_sinf_u10(e[i])) - e[i];
  checkf("sinf_u10, asinf_u10", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_asinf_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_sinf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_asinf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_sinf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_asinf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_sinf_u10
}

void func30() {
// CHECK-AVX512: func30
// CHECK-AVX2: func30
// CHECK-SSE2: func30
  for (int i = 0; i < N; i++) h[i] = Sleef_asinf_u35(Sleef_sinf_u35(e[i])) - e[i];
  checkf("sinf_u35, asinf_u35", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_asinf_u35
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_sinf_u35
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_asinf_u35
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_sinf_u35
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_asinf_u35
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_sinf_u35
}

void func31() {
// CHECK-AVX512: func31
// CHECK-AVX2: func31
// CHECK-SSE2: func31
  for (int i = 0; i < N; i++) h[i] = Sleef_acosf_u10(Sleef_cosf_u10(e[i])) - e[i];
  checkf("cosf_u10, acosf_u10", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_acosf_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_cosf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_acosf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_cosf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_acosf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_cosf_u10
}

void func32() {
// CHECK-AVX512: func32
// CHECK-AVX2: func32
// CHECK-SSE2: func32
  for (int i = 0; i < N; i++) h[i] = Sleef_acosf_u35(Sleef_cosf_u35(e[i])) - e[i];
  checkf("cosf_u35, acosf_u35", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_acosf_u35
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_cosf_u35
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_acosf_u35
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_cosf_u35
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_acosf_u35
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_cosf_u35
}

void func33() {
// CHECK-AVX512: func33
// CHECK-AVX2: func33
// CHECK-SSE2: func33
  for (int i = 0; i < N; i++) h[i] = Sleef_atanf_u10(Sleef_tanf_u10(e[i])) - e[i];
  checkf("tanf_u10, atanf_u10", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_atanf_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_tanf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_atanf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_tanf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_atanf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_tanf_u10
}

void func34() {
// CHECK-AVX512: func34
// CHECK-AVX2: func34
// CHECK-SSE2: func34
  for (int i = 0; i < N; i++) h[i] = Sleef_atanf_u35(Sleef_tanf_u35(e[i])) - e[i];
  checkf("tanf_u35, atanf_u35", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_atanf_u35
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_tanf_u35
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_atanf_u35
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_tanf_u35
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_atanf_u35
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_tanf_u35
}

void func35() {
// CHECK-AVX512: func35
// CHECK-AVX2: func35
// CHECK-SSE2: func35
  for (int i = 0; i < N; i++) h[i] = Sleef_atan2f_u10(f[i] * Sleef_sinpif_u05(e[i]*0.1), f[i] * Sleef_cospif_u05(e[i]*0.1)) - e[i]*0.3141592653589793;
  checkf("sinpif_u05, cospif_u05, atan2f_u10", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_sinpif_u05
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_cospif_u05
// CHECK-AVX2-DAG: _ZGVdN8vv_Sleef_atan2f_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_sinpif_u05
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_cospif_u05
// CHECK-AVX512-DAG: _ZGVeN16vv_Sleef_atan2f_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_sinpif_u05
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_cospif_u05
// CHECK-SSE2-DAG: _ZGVbN4vv_Sleef_atan2f_u10
}

void func36() {
// CHECK-AVX512: func36
// CHECK-AVX2: func36
// CHECK-SSE2: func36
  for (int i = 0; i < N; i++) h[i] = Sleef_atan2f_u35(f[i] * Sleef_sinpif_u05(e[i]*0.1), f[i] * Sleef_cospif_u05(e[i]*0.1)) - e[i]*0.3141592653589793;
  checkf("sinpif_u05, cospif_u05, atan2f_u35", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_sinpif_u05
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_cospif_u05
// CHECK-AVX2-DAG: _ZGVdN8vv_Sleef_atan2f_u35
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_sinpif_u05
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_cospif_u05
// CHECK-AVX512-DAG: _ZGVeN16vv_Sleef_atan2f_u35
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_sinpif_u05
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_cospif_u05
// CHECK-SSE2-DAG: _ZGVbN4vv_Sleef_atan2f_u35
}

void func37() {
// CHECK-AVX512: func37
// CHECK-AVX2: func37
// CHECK-SSE2: func37
  for (int i = 0; i < N; i++) h[i] = Sleef_log2f_u10(Sleef_exp2f_u10(e[i])) - e[i];
  checkf("log2f_u10, exp2f_u10", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_log2f_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_exp2f_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_log2f_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_exp2f_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_log2f_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_exp2f_u10
}

void func38() {
// CHECK-AVX512: func38
// CHECK-AVX2: func38
// CHECK-SSE2: func38
  for (int i = 0; i < N; i++) h[i] = Sleef_log2f_u35(Sleef_exp2f_u35(e[i])) - e[i];
  checkf("log2f_u35, exp2f_u35", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_log2f_u35
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_exp2f_u35
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_log2f_u35
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_exp2f_u35
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_log2f_u35
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_exp2f_u35
}

void func39() {
// CHECK-AVX512: func39
// CHECK-AVX2: func39
// CHECK-SSE2: func39
  for (int i = 0; i < N; i++) h[i] = Sleef_log10f_u10(Sleef_exp10f_u35(e[i])) - e[i];
  checkf("log10f_u10, exp10f_u35", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_log10f_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_exp10f_u35
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_log10f_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_exp10f_u35
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_log10f_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_exp10f_u35
}

void func40() {
// CHECK-AVX512: func40
// CHECK-AVX2: func40
// CHECK-SSE2: func40
  for (int i = 0; i < N; i++) h[i] = Sleef_log10f_u10(Sleef_exp10f_u10(e[i])) - e[i];
  checkf("log10f_u10, exp10f_u10", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_log10f_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_exp10f_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_log10f_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_exp10f_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_log10f_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_exp10f_u10
}

void func41() {
// CHECK-AVX512: func41
// CHECK-AVX2: func41
// CHECK-SSE2: func41
  for (int i = 0; i < N; i++) h[i] = Sleef_log1pf_u10(Sleef_expm1f_u10(e[i])) - e[i];
  checkf("log1pf_u10, expm1f_u10", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_log1pf_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_expm1f_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_log1pf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_expm1f_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_log1pf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_expm1f_u10
}

void func42() {
// CHECK-AVX512: func42
// CHECK-AVX2: func42
// CHECK-SSE2: func42
  for (int i = 0; i < N; i++) h[i] = Sleef_powf_u10(e[i], f[i]) - Sleef_expf_u10(Sleef_logf_u10(e[i]) * f[i]);
  checkf("powf_u10, expf_u10, logf_u10", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8vv_Sleef_powf_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_expf_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_logf_u10
// CHECK-AVX512-DAG: _ZGVeN16vv_Sleef_powf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_expf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_logf_u10
// CHECK-SSE2-DAG: _ZGVbN4vv_Sleef_powf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_expf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_logf_u10
}

void func43() {
// CHECK-AVX512: func43
// CHECK-AVX2: func43
// CHECK-SSE2: func43
  for (int i = 0; i < N; i++) h[i] = Sleef_powf_u10(e[i], f[i]) - Sleef_expf_u10(Sleef_logf_u35(e[i]) * f[i]);
  checkf("powf_u10, expf_u10, logf_u35", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8vv_Sleef_powf_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_expf_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_logf_u35
// CHECK-AVX512-DAG: _ZGVeN16vv_Sleef_powf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_expf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_logf_u35
// CHECK-SSE2-DAG: _ZGVbN4vv_Sleef_powf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_expf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_logf_u35
}

void func44() {
// CHECK-AVX512: func44
// CHECK-AVX2: func44
// CHECK-SSE2: func44
  for (int i = 0; i < N; i++) h[i] = Sleef_cbrtf_u10(e[i] * e[i] * e[i]) - e[i];
  checkf("cbrtf_u10", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_cbrtf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_cbrtf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_cbrtf_u10
}

void func45() {
// CHECK-AVX512: func45
// CHECK-AVX2: func45
// CHECK-SSE2: func45
  for (int i = 0; i < N; i++) h[i] = Sleef_cbrtf_u35(e[i] * e[i] * e[i]) - e[i];
  checkf("cbrtf_u35", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_cbrtf_u35
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_cbrtf_u35
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_cbrtf_u35
}

void func46() {
// CHECK-AVX512: func46
// CHECK-AVX2: func46
// CHECK-SSE2: func46
  for (int i = 0; i < N; i++) h[i] = Sleef_asinhf_u10(Sleef_sinhf_u10(e[i])) - e[i];
  checkf("asinhf_u10, sinhf_u10", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_asinhf_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_sinhf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_asinhf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_sinhf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_asinhf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_sinhf_u10
}

void func47() {
// CHECK-AVX512: func47
// CHECK-AVX2: func47
// CHECK-SSE2: func47
  for (int i = 0; i < N; i++) h[i] = Sleef_asinhf_u10(Sleef_sinhf_u35(e[i])) - e[i];
  checkf("asinhf_u10, sinhf_u35", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_asinhf_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_sinhf_u35
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_asinhf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_sinhf_u35
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_asinhf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_sinhf_u35
}

void func48() {
// CHECK-AVX512: func48
// CHECK-AVX2: func48
// CHECK-SSE2: func48
  for (int i = 0; i < N; i++) h[i] = Sleef_acoshf_u10(Sleef_coshf_u10(e[i])) - e[i];
  checkf("acoshf_u10, coshf_u10", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_acoshf_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_coshf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_acoshf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_coshf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_acoshf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_coshf_u10
}

void func49() {
// CHECK-AVX512: func49
// CHECK-AVX2: func49
// CHECK-SSE2: func49
  for (int i = 0; i < N; i++) h[i] = Sleef_acoshf_u10(Sleef_coshf_u35(e[i])) - e[i];
  checkf("acoshf_u10, coshf_u35", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_acoshf_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_coshf_u35
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_acoshf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_coshf_u35
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_acoshf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_coshf_u35
}

void func50() {
// CHECK-AVX512: func50
// CHECK-AVX2: func50
// CHECK-SSE2: func50
  for (int i = 0; i < N; i++) h[i] = Sleef_atanhf_u10(Sleef_tanhf_u10(e[i])) - e[i];
  checkf("atanhf_u10, tanhf_u10", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_atanhf_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_tanhf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_atanhf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_tanhf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_atanhf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_tanhf_u10
}

void func51() {
// CHECK-AVX512: func51
// CHECK-AVX2: func51
// CHECK-SSE2: func51
  for (int i = 0; i < N; i++) h[i] = Sleef_atanhf_u10(Sleef_tanhf_u35(e[i])) - e[i];
  checkf("atanhf_u10, tanhf_u35", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_atanhf_u10
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_tanhf_u35
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_atanhf_u10
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_tanhf_u35
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_atanhf_u10
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_tanhf_u35
}

void func52() {
// CHECK-AVX512: func52
// CHECK-AVX2: func52
// CHECK-SSE2: func52
  for (int i = 0; i < N; i++) h[i] = Sleef_fmaf(e[i], f[i], g[i]) - (e[i] * f[i] + g[i]);
  checkf("fmaf", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8vvv_Sleef_fma
// CHECK-AVX512-DAG: _ZGVeN16vvv_Sleef_fma
// CHECK-SSE2-DAG: _ZGVbN4vvv_Sleef_fma
}

void func53() {
// CHECK-AVX512: func53
// CHECK-AVX2: func53
// CHECK-SSE2: func53
  for (int i = 0; i < N; i++) h[i] = Sleef_hypotf_u05(e[i], f[i]) - Sleef_sqrtf_u05(e[i] * e[i] + f[i] * f[i]);
  checkf("hypotf_u05, sqrtf_u05", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8vv_Sleef_hypotf_u05
// CHECK-AVX512-DAG: _ZGVeN16vv_Sleef_hypotf_u05
// CHECK-SSE2-DAG: _ZGVbN4vv_Sleef_hypotf_u05
}

void func54() {
// CHECK-AVX512: func54
// CHECK-AVX2: func54
// CHECK-SSE2: func54
  for (int i = 0; i < N; i++) h[i] = Sleef_hypotf_u35(e[i], f[i]) - Sleef_sqrtf_u05(e[i] * e[i] + f[i] * f[i]);
  checkf("hypotf_u35, sqrtf_u05", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8vv_Sleef_hypotf_u35
// CHECK-AVX512-DAG: _ZGVeN16vv_Sleef_hypotf_u35
// CHECK-SSE2-DAG: _ZGVbN4vv_Sleef_hypotf_u35
}

void func55() {
// CHECK-AVX2: func55
// CHECK-SSE2: func55
  for (int i = 0; i < N; i++) h[i] = Sleef_fmodf(e[i], f[i]) - (e[i] - Sleef_floorf(e[i] / f[i]) * f[i]);
  checkf("fmodf, floorf", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8vv_Sleef_fmodf
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_floorf
// CHECK-AVX512-DAG: _ZGVeN16vv_Sleef_fmodf
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_floorf
// CHECK-SSE2-DAG: _ZGVbN4vv_Sleef_fmodf
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_floorf
}

void func56() {
// CHECK-AVX2: func56
// CHECK-SSE2: func56
  for (int i = 0; i < N; i++) h[i] = Sleef_remainderf(e[i], f[i]) - (e[i] - Sleef_rintf(e[i] / f[i]) * f[i]);
  checkf("remainderf, rintf", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8vv_Sleef_remainderf
// CHECK-AVX2-DAG: _ZGVdN8v_Sleef_rintf
// CHECK-AVX512-DAG: _ZGVeN16vv_Sleef_remainderf
// CHECK-AVX512-DAG: _ZGVeN16v_Sleef_rintf
// CHECK-SSE2-DAG: _ZGVbN4vv_Sleef_remainderf
// CHECK-SSE2-DAG: _ZGVbN4v_Sleef_rintf
}

void func57() {
// CHECK-AVX2: func57
// CHECK-SSE2: func57
  for (int i = 0; i < N; i++) h[i] = Sleef_nextafterf(Sleef_nextafter(e[i], f[i]), -f[i]) - e[i];
  checkf("nextafterf", THRESF);
// CHECK-AVX2-DAG: _ZGVdN8vv_Sleef_nextafterf
// CHECK-AVX512-DAG: _ZGVeN16vv_Sleef_nextafterf
// CHECK-SSE2-DAG: _ZGVbN4vv_Sleef_nextafterf
}

int main() {
  for (int i = 0; i < N; i++) {
    a[i] = 1.5 * rand() / (double)RAND_MAX + 1e-100;
    b[i] = 1.5 * rand() / (double)RAND_MAX + 1e-100;
    c[i] = 1.5 * rand() / (double)RAND_MAX + 1e-100;
  }

  for (int i = 0; i < N; i++) {
    e[i] = 1.5 * rand() / (double)RAND_MAX + 1e-100;
    f[i] = 1.5 * rand() / (double)RAND_MAX + 1e-100;
    g[i] = 1.5 * rand() / (double)RAND_MAX + 1e-100;
  }

  func00(); func01(); func02(); func03(); func04(); func05(); func06(); func07(); func08(); func09();
  func10(); func11(); func12(); func13(); func14(); func15(); func16(); func17(); func18(); func19();
  func20(); func21(); func22(); func23(); func24(); func25(); func26(); func27(); func28(); func29();
  func30(); func31(); func32(); func33(); func34(); func35(); func36(); func37(); func38(); func39();
  func40(); func41(); func42(); func43(); func44(); func45(); func46(); func47(); func48(); func49();
  func50(); func51(); func52(); func53(); func54(); func55(); func56(); func57();

  exit(0);
}
