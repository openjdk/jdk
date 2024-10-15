//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#define SLEEF_ENABLE_OMP_SIMD
#include "sleef.h"

#define N 1024
double a[N], b[N], c[N], d[N];
float e[N], f[N], g[N], h[N];

void testsind1_u10() {
// CHECK-SSE2: testsind1_u10
// CHECK-AVX2: testsind1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_sind1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_sind1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_sind1_u10
}

void testsind1_u35() {
// CHECK-SSE2: testsind1_u35
// CHECK-AVX2: testsind1_u35
  for(int i=0;i<N;i++) a[i] = Sleef_sind1_u35(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_sind1_u35
// CHECK-AVX2: _ZGVdN4v_Sleef_sind1_u35
}

void testsinf1_u10() {
// CHECK-SSE2: testsinf1_u10
// CHECK-AVX2: testsinf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_sinf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_sinf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_sinf1_u10
}

void testsinf1_u35() {
// CHECK-SSE2: testsinf1_u35
// CHECK-AVX2: testsinf1_u35
  for(int i=0;i<N;i++) e[i] = Sleef_sinf1_u35(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_sinf1_u35
// CHECK-AVX2: _ZGVdN8v_Sleef_sinf1_u35
}

void testcosd1_u10() {
// CHECK-SSE2: testcosd1_u10
// CHECK-AVX2: testcosd1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_cosd1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_cosd1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_cosd1_u10
}

void testcosd1_u35() {
// CHECK-SSE2: testcosd1_u35
// CHECK-AVX2: testcosd1_u35
  for(int i=0;i<N;i++) a[i] = Sleef_cosd1_u35(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_cosd1_u35
// CHECK-AVX2: _ZGVdN4v_Sleef_cosd1_u35
}

void testcosf1_u10() {
// CHECK-SSE2: testcosf1_u10
// CHECK-AVX2: testcosf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_cosf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_cosf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_cosf1_u10
}

void testcosf1_u35() {
// CHECK-SSE2: testcosf1_u35
// CHECK-AVX2: testcosf1_u35
  for(int i=0;i<N;i++) e[i] = Sleef_cosf1_u35(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_cosf1_u35
// CHECK-AVX2: _ZGVdN8v_Sleef_cosf1_u35
}

void testtand1_u10() {
// CHECK-SSE2: testtand1_u10
// CHECK-AVX2: testtand1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_tand1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_tand1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_tand1_u10
}

void testtand1_u35() {
// CHECK-SSE2: testtand1_u35
// CHECK-AVX2: testtand1_u35
  for(int i=0;i<N;i++) a[i] = Sleef_tand1_u35(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_tand1_u35
// CHECK-AVX2: _ZGVdN4v_Sleef_tand1_u35
}

void testtanf1_u10() {
// CHECK-SSE2: testtanf1_u10
// CHECK-AVX2: testtanf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_tanf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_tanf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_tanf1_u10
}

void testtanf1_u35() {
// CHECK-SSE2: testtanf1_u35
// CHECK-AVX2: testtanf1_u35
  for(int i=0;i<N;i++) e[i] = Sleef_tanf1_u35(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_tanf1_u35
// CHECK-AVX2: _ZGVdN8v_Sleef_tanf1_u35
}

void testasind1_u10() {
// CHECK-SSE2: testasind1_u10
// CHECK-AVX2: testasind1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_asind1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_asind1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_asind1_u10
}

void testasind1_u35() {
// CHECK-SSE2: testasind1_u35
// CHECK-AVX2: testasind1_u35
  for(int i=0;i<N;i++) a[i] = Sleef_asind1_u35(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_asind1_u35
// CHECK-AVX2: _ZGVdN4v_Sleef_asind1_u35
}

void testasinf1_u10() {
// CHECK-SSE2: testasinf1_u10
// CHECK-AVX2: testasinf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_asinf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_asinf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_asinf1_u10
}

void testasinf1_u35() {
// CHECK-SSE2: testasinf1_u35
// CHECK-AVX2: testasinf1_u35
  for(int i=0;i<N;i++) e[i] = Sleef_asinf1_u35(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_asinf1_u35
// CHECK-AVX2: _ZGVdN8v_Sleef_asinf1_u35
}

void testacosd1_u10() {
// CHECK-SSE2: testacosd1_u10
// CHECK-AVX2: testacosd1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_acosd1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_acosd1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_acosd1_u10
}

void testacosd1_u35() {
// CHECK-SSE2: testacosd1_u35
// CHECK-AVX2: testacosd1_u35
  for(int i=0;i<N;i++) a[i] = Sleef_acosd1_u35(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_acosd1_u35
// CHECK-AVX2: _ZGVdN4v_Sleef_acosd1_u35
}

void testacosf1_u10() {
// CHECK-SSE2: testacosf1_u10
// CHECK-AVX2: testacosf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_acosf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_acosf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_acosf1_u10
}

void testacosf1_u35() {
// CHECK-SSE2: testacosf1_u35
// CHECK-AVX2: testacosf1_u35
  for(int i=0;i<N;i++) e[i] = Sleef_acosf1_u35(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_acosf1_u35
// CHECK-AVX2: _ZGVdN8v_Sleef_acosf1_u35
}

void testatand1_u10() {
// CHECK-SSE2: testatand1_u10
// CHECK-AVX2: testatand1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_atand1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_atand1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_atand1_u10
}

void testatand1_u35() {
// CHECK-SSE2: testatand1_u35
// CHECK-AVX2: testatand1_u35
  for(int i=0;i<N;i++) a[i] = Sleef_atand1_u35(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_atand1_u35
// CHECK-AVX2: _ZGVdN4v_Sleef_atand1_u35
}

void testatanf1_u10() {
// CHECK-SSE2: testatanf1_u10
// CHECK-AVX2: testatanf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_atanf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_atanf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_atanf1_u10
}

void testatanf1_u35() {
// CHECK-SSE2: testatanf1_u35
// CHECK-AVX2: testatanf1_u35
  for(int i=0;i<N;i++) e[i] = Sleef_atanf1_u35(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_atanf1_u35
// CHECK-AVX2: _ZGVdN8v_Sleef_atanf1_u35
}

void testatan2d1_u10() {
// CHECK-SSE2: testatan2d1_u10
// CHECK-AVX2: testatan2d1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_atan2d1_u10(b[i], c[i]);
// CHECK-SSE2: _ZGVbN2vv_Sleef_atan2d1_u10
// CHECK-AVX2: _ZGVdN4vv_Sleef_atan2d1_u10
}

void testatan2d1_u35() {
// CHECK-SSE2: testatan2d1_u35
// CHECK-AVX2: testatan2d1_u35
  for(int i=0;i<N;i++) a[i] = Sleef_atan2d1_u35(b[i], c[i]);
// CHECK-SSE2: _ZGVbN2vv_Sleef_atan2d1_u35
// CHECK-AVX2: _ZGVdN4vv_Sleef_atan2d1_u35
}

void testatan2f1_u10() {
// CHECK-SSE2: testatan2f1_u10
// CHECK-AVX2: testatan2f1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_atan2f1_u10(f[i], g[i]);
// CHECK-SSE2: _ZGVbN4vv_Sleef_atan2f1_u10
// CHECK-AVX2: _ZGVdN8vv_Sleef_atan2f1_u10
}

void testatan2f1_u35() {
// CHECK-SSE2: testatan2f1_u35
// CHECK-AVX2: testatan2f1_u35
  for(int i=0;i<N;i++) e[i] = Sleef_atan2f1_u35(f[i], g[i]);
// CHECK-SSE2: _ZGVbN4vv_Sleef_atan2f1_u35
// CHECK-AVX2: _ZGVdN8vv_Sleef_atan2f1_u35
}

void testsinhd1_u10() {
// CHECK-SSE2: testsinhd1_u10
// CHECK-AVX2: testsinhd1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_sinhd1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_sinhd1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_sinhd1_u10
}

void testsinhd1_u35() {
// CHECK-SSE2: testsinhd1_u35
// CHECK-AVX2: testsinhd1_u35
  for(int i=0;i<N;i++) a[i] = Sleef_sinhd1_u35(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_sinhd1_u35
// CHECK-AVX2: _ZGVdN4v_Sleef_sinhd1_u35
}

void testsinhf1_u10() {
// CHECK-SSE2: testsinhf1_u10
// CHECK-AVX2: testsinhf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_sinhf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_sinhf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_sinhf1_u10
}

void testsinhf1_u35() {
// CHECK-SSE2: testsinhf1_u35
// CHECK-AVX2: testsinhf1_u35
  for(int i=0;i<N;i++) e[i] = Sleef_sinhf1_u35(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_sinhf1_u35
// CHECK-AVX2: _ZGVdN8v_Sleef_sinhf1_u35
}

void testcoshd1_u10() {
// CHECK-SSE2: testcoshd1_u10
// CHECK-AVX2: testcoshd1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_coshd1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_coshd1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_coshd1_u10
}

void testcoshd1_u35() {
// CHECK-SSE2: testcoshd1_u35
// CHECK-AVX2: testcoshd1_u35
  for(int i=0;i<N;i++) a[i] = Sleef_coshd1_u35(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_coshd1_u35
// CHECK-AVX2: _ZGVdN4v_Sleef_coshd1_u35
}

void testcoshf1_u10() {
// CHECK-SSE2: testcoshf1_u10
// CHECK-AVX2: testcoshf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_coshf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_coshf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_coshf1_u10
}

void testcoshf1_u35() {
// CHECK-SSE2: testcoshf1_u35
// CHECK-AVX2: testcoshf1_u35
  for(int i=0;i<N;i++) e[i] = Sleef_coshf1_u35(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_coshf1_u35
// CHECK-AVX2: _ZGVdN8v_Sleef_coshf1_u35
}

void testtanhd1_u10() {
// CHECK-SSE2: testtanhd1_u10
// CHECK-AVX2: testtanhd1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_tanhd1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_tanhd1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_tanhd1_u10
}

void testtanhd1_u35() {
// CHECK-SSE2: testtanhd1_u35
// CHECK-AVX2: testtanhd1_u35
  for(int i=0;i<N;i++) a[i] = Sleef_tanhd1_u35(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_tanhd1_u35
// CHECK-AVX2: _ZGVdN4v_Sleef_tanhd1_u35
}

void testtanhf1_u10() {
// CHECK-SSE2: testtanhf1_u10
// CHECK-AVX2: testtanhf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_tanhf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_tanhf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_tanhf1_u10
}

void testtanhf1_u35() {
// CHECK-SSE2: testtanhf1_u35
// CHECK-AVX2: testtanhf1_u35
  for(int i=0;i<N;i++) e[i] = Sleef_tanhf1_u35(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_tanhf1_u35
// CHECK-AVX2: _ZGVdN8v_Sleef_tanhf1_u35
}

void testasinhd1_u10() {
// CHECK-SSE2: testasinhd1_u10
// CHECK-AVX2: testasinhd1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_asinhd1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_asinhd1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_asinhd1_u10
}

void testasinhf1_u10() {
// CHECK-SSE2: testasinhf1_u10
// CHECK-AVX2: testasinhf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_asinhf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_asinhf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_asinhf1_u10
}

void testacoshd1_u10() {
// CHECK-SSE2: testacoshd1_u10
// CHECK-AVX2: testacoshd1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_acoshd1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_acoshd1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_acoshd1_u10
}

void testacoshf1_u10() {
// CHECK-SSE2: testacoshf1_u10
// CHECK-AVX2: testacoshf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_acoshf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_acoshf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_acoshf1_u10
}

void testatanhd1_u10() {
// CHECK-SSE2: testatanhd1_u10
// CHECK-AVX2: testatanhd1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_atanhd1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_atanhd1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_atanhd1_u10
}

void testatanhf1_u10() {
// CHECK-SSE2: testatanhf1_u10
// CHECK-AVX2: testatanhf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_atanhf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_atanhf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_atanhf1_u10
}

void testlogd1_u10() {
// CHECK-SSE2: testlogd1_u10
// CHECK-AVX2: testlogd1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_logd1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_logd1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_logd1_u10
}

void testlogd1_u35() {
// CHECK-SSE2: testlogd1_u35
// CHECK-AVX2: testlogd1_u35
  for(int i=0;i<N;i++) a[i] = Sleef_logd1_u35(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_logd1_u35
// CHECK-AVX2: _ZGVdN4v_Sleef_logd1_u35
}

void testlogf1_u10() {
// CHECK-SSE2: testlogf1_u10
// CHECK-AVX2: testlogf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_logf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_logf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_logf1_u10
}

void testlogf1_u35() {
// CHECK-SSE2: testlogf1_u35
// CHECK-AVX2: testlogf1_u35
  for(int i=0;i<N;i++) e[i] = Sleef_logf1_u35(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_logf1_u35
// CHECK-AVX2: _ZGVdN8v_Sleef_logf1_u35
}

void testlog2d1_u10() {
// CHECK-SSE2: testlog2d1_u10
// CHECK-AVX2: testlog2d1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_log2d1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_log2d1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_log2d1_u10
}

void testlog2f1_u10() {
// CHECK-SSE2: testlog2f1_u10
// CHECK-AVX2: testlog2f1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_log2f1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_log2f1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_log2f1_u10
}

void testlog10d1_u10() {
// CHECK-SSE2: testlog10d1_u10
// CHECK-AVX2: testlog10d1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_log10d1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_log10d1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_log10d1_u10
}

void testlog10f1_u10() {
// CHECK-SSE2: testlog10f1_u10
// CHECK-AVX2: testlog10f1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_log10f1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_log10f1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_log10f1_u10
}

void testlog1pd1_u10() {
// CHECK-SSE2: testlog1pd1_u10
// CHECK-AVX2: testlog1pd1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_log1pd1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_log1pd1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_log1pd1_u10
}

void testlog1pf1_u10() {
// CHECK-SSE2: testlog1pf1_u10
// CHECK-AVX2: testlog1pf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_log1pf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_log1pf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_log1pf1_u10
}

void testexpd1_u10() {
// CHECK-SSE2: testexpd1_u10
// CHECK-AVX2: testexpd1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_expd1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_expd1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_expd1_u10
}

void testexpf1_u10() {
// CHECK-SSE2: testexpf1_u10
// CHECK-AVX2: testexpf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_expf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_expf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_expf1_u10
}

void testexp2d1_u10() {
// CHECK-SSE2: testexp2d1_u10
// CHECK-AVX2: testexp2d1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_exp2d1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_exp2d1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_exp2d1_u10
}

void testexp2f1_u10() {
// CHECK-SSE2: testexp2f1_u10
// CHECK-AVX2: testexp2f1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_exp2f1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_exp2f1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_exp2f1_u10
}

void testexp10d1_u10() {
// CHECK-SSE2: testexp10d1_u10
// CHECK-AVX2: testexp10d1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_exp10d1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_exp10d1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_exp10d1_u10
}

void testexp10f1_u10() {
// CHECK-SSE2: testexp10f1_u10
// CHECK-AVX2: testexp10f1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_exp10f1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_exp10f1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_exp10f1_u10
}

void testexpm1d1_u10() {
// CHECK-SSE2: testexpm1d1_u10
// CHECK-AVX2: testexpm1d1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_expm1d1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_expm1d1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_expm1d1_u10
}

void testexpm1f1_u10() {
// CHECK-SSE2: testexpm1f1_u10
// CHECK-AVX2: testexpm1f1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_expm1f1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_expm1f1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_expm1f1_u10
}

void testpowd1_u10() {
// CHECK-SSE2: testpowd1_u10
// CHECK-AVX2: testpowd1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_powd1_u10(b[i], c[i]);
// CHECK-SSE2: _ZGVbN2vv_Sleef_powd1_u10
// CHECK-AVX2: _ZGVdN4vv_Sleef_powd1_u10
}

void testpowf1_u10() {
// CHECK-SSE2: testpowf1_u10
// CHECK-AVX2: testpowf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_powf1_u10(f[i], g[i]);
// CHECK-SSE2: _ZGVbN4vv_Sleef_powf1_u10
// CHECK-AVX2: _ZGVdN8vv_Sleef_powf1_u10
}

void testcbrtd1_u10() {
// CHECK-SSE2: testcbrtd1_u10
// CHECK-AVX2: testcbrtd1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_cbrtd1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_cbrtd1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_cbrtd1_u10
}

void testcbrtd1_u35() {
// CHECK-SSE2: testcbrtd1_u35
// CHECK-AVX2: testcbrtd1_u35
  for(int i=0;i<N;i++) a[i] = Sleef_cbrtd1_u35(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_cbrtd1_u35
// CHECK-AVX2: _ZGVdN4v_Sleef_cbrtd1_u35
}

void testcbrtf1_u10() {
// CHECK-SSE2: testcbrtf1_u10
// CHECK-AVX2: testcbrtf1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_cbrtf1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_cbrtf1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_cbrtf1_u10
}

void testcbrtf1_u35() {
// CHECK-SSE2: testcbrtf1_u35
// CHECK-AVX2: testcbrtf1_u35
  for(int i=0;i<N;i++) e[i] = Sleef_cbrtf1_u35(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_cbrtf1_u35
// CHECK-AVX2: _ZGVdN8v_Sleef_cbrtf1_u35
}

void testhypotd1_u05() {
// CHECK-SSE2: testhypotd1_u05
// CHECK-AVX2: testhypotd1_u05
  for(int i=0;i<N;i++) a[i] = Sleef_hypotd1_u05(b[i], c[i]);
// CHECK-SSE2: _ZGVbN2vv_Sleef_hypotd1_u05
// CHECK-AVX2: _ZGVdN4vv_Sleef_hypotd1_u05
}

void testhypotd1_u35() {
// CHECK-SSE2: testhypotd1_u35
// CHECK-AVX2: testhypotd1_u35
  for(int i=0;i<N;i++) a[i] = Sleef_hypotd1_u35(b[i], c[i]);
// CHECK-SSE2: _ZGVbN2vv_Sleef_hypotd1_u35
// CHECK-AVX2: _ZGVdN4vv_Sleef_hypotd1_u35
}

void testhypotf1_u05() {
// CHECK-SSE2: testhypotf1_u05
// CHECK-AVX2: testhypotf1_u05
  for(int i=0;i<N;i++) e[i] = Sleef_hypotf1_u05(f[i], g[i]);
// CHECK-SSE2: _ZGVbN4vv_Sleef_hypotf1_u05
// CHECK-AVX2: _ZGVdN8vv_Sleef_hypotf1_u05
}

void testhypotf1_u35() {
// CHECK-SSE2: testhypotf1_u35
// CHECK-AVX2: testhypotf1_u35
  for(int i=0;i<N;i++) e[i] = Sleef_hypotf1_u35(f[i], g[i]);
// CHECK-SSE2: _ZGVbN4vv_Sleef_hypotf1_u35
// CHECK-AVX2: _ZGVdN8vv_Sleef_hypotf1_u35
}

void testerfd1_u10() {
// CHECK-SSE2: testerfd1_u10
// CHECK-AVX2: testerfd1_u10
  for(int i=0;i<N;i++) a[i] = Sleef_erfd1_u10(b[i]);
// CHECK-SSE2: _ZGVbN2v_Sleef_erfd1_u10
// CHECK-AVX2: _ZGVdN4v_Sleef_erfd1_u10
}

void testerff1_u10() {
// CHECK-SSE2: testerff1_u10
// CHECK-AVX2: testerff1_u10
  for(int i=0;i<N;i++) e[i] = Sleef_erff1_u10(f[i]);
// CHECK-SSE2: _ZGVbN4v_Sleef_erff1_u10
// CHECK-AVX2: _ZGVdN8v_Sleef_erff1_u10
}

void testfmodd1() {
// CHECK-SSE2: testfmodd1
// CHECK-AVX2: testfmodd1
  for(int i=0;i<N;i++) a[i] = Sleef_fmodd1(b[i], c[i]);
// CHECK-SSE2: _ZGVbN2vv_Sleef_fmodd1
// CHECK-AVX2: _ZGVdN4vv_Sleef_fmodd1
}

void testfmodf1() {
// CHECK-SSE2: testfmodf1
// CHECK-AVX2: testfmodf1
  for(int i=0;i<N;i++) e[i] = Sleef_fmodf1(f[i], g[i]);
// CHECK-SSE2: _ZGVbN4vv_Sleef_fmodf1
// CHECK-AVX2: _ZGVdN8vv_Sleef_fmodf1
}

void testremainderd1() {
// CHECK-SSE2: testremainderd1
// CHECK-AVX2: testremainderd1
  for(int i=0;i<N;i++) a[i] = Sleef_remainderd1(b[i], c[i]);
// CHECK-SSE2: _ZGVbN2vv_Sleef_remainderd1
// CHECK-AVX2: _ZGVdN4vv_Sleef_remainderd1
}

void testremainderf1() {
// CHECK-SSE2: testremainderf1
// CHECK-AVX2: testremainderf1
  for(int i=0;i<N;i++) e[i] = Sleef_remainderf1(f[i], g[i]);
// CHECK-SSE2: _ZGVbN4vv_Sleef_remainderf1
// CHECK-AVX2: _ZGVdN8vv_Sleef_remainderf1
}
