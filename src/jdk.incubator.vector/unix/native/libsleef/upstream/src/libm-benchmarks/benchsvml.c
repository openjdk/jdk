//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <assert.h>
#include <math.h>
#include <time.h>
#include <unistd.h>
#include <x86intrin.h>

#include "bench.h"

int veclen = 16;
int enableLogExp;
double *abufdp, *bbufdp;
float *abufsp, *bbufsp;
FILE *fp;

#if defined(__i386__) || defined(__x86_64__)
void x86CpuID(int32_t out[4], uint32_t eax, uint32_t ecx) {
  uint32_t a, b, c, d;
  __asm__ __volatile__ ("cpuid" : "=a" (a), "=b" (b), "=c" (c), "=d" (d) : "a" (eax), "c"(ecx));
  out[0] = a; out[1] = b; out[2] = c; out[3] = d;
}

int cpuSupportsAVX() {
    int32_t reg[4];
    x86CpuID(reg, 1, 0);
    return (reg[2] & (1 << 28)) != 0;
}

int cpuSupportsAVX512F() {
    int32_t reg[4];
    x86CpuID(reg, 7, 0);
    return (reg[1] & (1 << 16)) != 0;
}
#endif

uint64_t Sleef_currentTimeMicros() {
  struct timespec tp;
  clock_gettime(CLOCK_MONOTONIC, &tp);
  return (uint64_t)tp.tv_sec * 1000000LL + ((uint64_t)tp.tv_nsec/1000);
}

void fillDP(double *buf, double min, double max) {
  for(int i=0;i<NITER1*veclen;i++) {
    double r = ((double)random() + RAND_MAX * (double)random()) / (RAND_MAX * (double)RAND_MAX);
    buf[i] = r * (max - min) + min;
  }
}

void fillSP(float *buf, double min, double max) {
  for(int i=0;i<NITER1*veclen;i++) {
    double r = ((double)random() + RAND_MAX * (double)random()) / (RAND_MAX * (double)RAND_MAX);
    buf[i] = r * (max - min) + min;
  }
}

void zeroupper256();
void benchSVML128_DPTrig();
void benchSVML256_DPTrig();
void benchSVML512_DPTrig();
void benchSVML128_DPNontrig();
void benchSVML256_DPNontrig();
void benchSVML512_DPNontrig();
void benchSVML128_SPTrig();
void benchSVML256_SPTrig();
void benchSVML512_SPTrig();
void benchSVML128_SPNontrig();
void benchSVML256_SPNontrig();
void benchSVML512_SPNontrig();

//

int main(int argc, char **argv) {
  char *columnTitle = "SVML", *fnBase = "svml";
  char fn[1024];

  if (argc != 1) columnTitle = argv[1];
  if (argc >= 3) fnBase = argv[2];

  srandom(time(NULL));

#if defined(__i386__) || defined(__x86_64__)
  int do128bit = 1;
  int do256bit = cpuSupportsAVX();
  int do512bit = cpuSupportsAVX512F();
#elif defined(__ARM_NEON)
  int do128bit = 1;
  int do256bit = 0;
  int do512bit = 0;
#else
#error Unsupported architecture
#endif

  posix_memalign((void **)&abufdp, veclen*sizeof(double), NITER1*veclen*sizeof(double));
  posix_memalign((void **)&bbufdp, veclen*sizeof(double), NITER1*veclen*sizeof(double));

  abufsp = (float *)abufdp;
  bbufsp = (float *)bbufdp;

  enableLogExp = SVMLULP < 2;

  sprintf(fn, "%sdptrig%gulp.out", fnBase, (double)SVMLULP);
  fp = fopen(fn, "w");
  fprintf(fp, "%s\n", columnTitle);

  if (do256bit) zeroupper256();
  if (do128bit) benchSVML128_DPTrig();
  if (do256bit) benchSVML256_DPTrig();
  if (do512bit) benchSVML512_DPTrig();

  fclose(fp);

  sprintf(fn, "%sdpnontrig%gulp.out", fnBase, (double)SVMLULP);
  fp = fopen(fn, "w");
  fprintf(fp, "%s\n", columnTitle);

  if (do256bit) zeroupper256();
  if (do128bit) benchSVML128_DPNontrig();
  if (do256bit) benchSVML256_DPNontrig();
  if (do512bit) benchSVML512_DPNontrig();

  fclose(fp);

  sprintf(fn, "%ssptrig%gulp.out", fnBase, (double)SVMLULP);
  fp = fopen(fn, "w");
  fprintf(fp, "%s\n", columnTitle);

  if (do256bit) zeroupper256();
  if (do128bit) benchSVML128_SPTrig();
  if (do256bit) benchSVML256_SPTrig();
  if (do512bit) benchSVML512_SPTrig();

  fclose(fp);

  sprintf(fn, "%sspnontrig%gulp.out", fnBase, (double)SVMLULP);
  fp = fopen(fn, "w");
  fprintf(fp, "%s\n", columnTitle);

  if (do256bit) zeroupper256();
  if (do128bit) benchSVML128_SPNontrig();
  if (do256bit) benchSVML256_SPNontrig();
  if (do512bit) benchSVML512_SPNontrig();

  fclose(fp);

  exit(0);
}
