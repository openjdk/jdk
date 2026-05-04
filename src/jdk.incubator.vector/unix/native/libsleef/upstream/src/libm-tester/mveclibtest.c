#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <x86intrin.h>

#include <sleef.h>

#define N 64
#define M 256

double r0[N], a0[N], a1[N], a2[N];

void do_libm() { for(int i=0;i<N;i++) r0[i] = sin(a0[i]); }

#if defined(__SSE2__)
void do_sleef_sse2() { _mm_storeu_pd(r0, Sleef_sind2_u10sse2(_mm_loadu_pd(a0))); }
#endif

#if defined(__AVX__)
void do_sleef_avx() { _mm256_storeu_pd(r0, Sleef_sind4_u10avx(_mm256_loadu_pd(a0))); }
#endif

#if defined(__AVX2__)
void do_sleef_avx2() { _mm256_storeu_pd(r0, Sleef_sind4_u10avx2(_mm256_loadu_pd(a0))); }
#endif

#if defined(__AVX512F__)
void do_sleef_avx512f() { _mm512_storeu_pd(r0, Sleef_sind8_u10avx512f(_mm512_loadu_pd(a0))); }
#endif

int do_test_once(double d) {
  for(int i=0;i<N;i++) a0[i] = d;
  do_libm();
  double rm = r0[0];

#if defined(__SSE2__)
  for(int i=0;i<N;i++) a0[i] = d;
  do_sleef_sse2();
  if (rm == r0[0]) return 1;
#endif

#if defined(__AVX__)
  for(int i=0;i<N;i++) a0[i] = d;
  do_sleef_avx();
  if (rm == r0[0]) return 1;
#endif

#if defined(__AVX2__)
  for(int i=0;i<N;i++) a0[i] = d;
  do_sleef_avx2();
  if (rm == r0[0]) return 1;
#endif

#if defined(__AVX512F__)
  for(int i=0;i<N;i++) a0[i] = d;
  do_sleef_avx512f();
  if (rm == r0[0]) return 1;
#endif

  return 0;
}

int check_feature(double d, float f) {
#if defined(__SSE2__)
  do_sleef_sse2();
#endif

#if defined(__AVX__)
  do_sleef_avx();
#endif

#if defined(__AVX2__)
  do_sleef_avx2();
#endif

#if defined(__AVX512F__)
  do_sleef_avx512f();
#endif

  return 1;
}

int main2(int argc, char **argv) {
  for(int i=0;i<M;i++) {
    if (!do_test_once(10.0 * ((2.0 * rand() / RAND_MAX) - 1))) {
      printf("fail\n");
      exit(-1);
    }
  }
  printf("pass\n");
  exit(0);
}
