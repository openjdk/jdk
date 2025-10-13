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
#include <sleef.h>

#include "bench.h"

int veclen = 16;
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

void benchSleef128_DPTrig();
void benchSleef256_DPTrig();
void benchSleef512_DPTrig();
void benchSleef128_DPNontrig();
void benchSleef256_DPNontrig();
void benchSleef512_DPNontrig();
void benchSleef128_SPTrig();
void benchSleef256_SPTrig();
void benchSleef512_SPTrig();
void benchSleef128_SPNontrig();
void benchSleef256_SPNontrig();
void benchSleef512_SPNontrig();

//

int main(int argc, char **argv) {
  char *columnTitle = "SLEEF", *fnBase = "sleef";
  char fn[1024];

  if (argc != 1) columnTitle = argv[1];
  if (argc >= 3) fnBase = argv[2];

  srandom(time(NULL));

#if defined(__i386__) || defined(__x86_64__)
  int do128bit = 1;
  int do256bit = cpuSupportsAVX();
  int do512bit = cpuSupportsAVX512F();
#elif defined(__ARM_NEON) || defined(__VSX__) || defined(__VX__)
  int do128bit = 1;
#else
#error Unsupported architecture
#endif

  posix_memalign((void **)&abufdp, veclen*sizeof(double), NITER1*veclen*sizeof(double));
  posix_memalign((void **)&bbufdp, veclen*sizeof(double), NITER1*veclen*sizeof(double));

  abufsp = (float *)abufdp;
  bbufsp = (float *)bbufdp;

  sprintf(fn, "%sdptrig.out", fnBase);
  fp = fopen(fn, "w");
  fprintf(fp, "%s\n", columnTitle);

  if (do128bit) benchSleef128_DPTrig();
#if defined(__i386__) || defined(__x86_64__)
  if (do256bit) benchSleef256_DPTrig();
  if (do512bit) benchSleef512_DPTrig();
#endif

  fclose(fp);

  sprintf(fn, "%sdpnontrig.out", fnBase);
  fp = fopen(fn, "w");
  fprintf(fp, "%s\n", columnTitle);

  if (do128bit) benchSleef128_DPNontrig();
#if defined(__i386__) || defined(__x86_64__)
  if (do256bit) benchSleef256_DPNontrig();
  if (do512bit) benchSleef512_DPNontrig();
#endif

  fclose(fp);

  sprintf(fn, "%ssptrig.out", fnBase);
  fp = fopen(fn, "w");
  fprintf(fp, "%s\n", columnTitle);

  if (do128bit) benchSleef128_SPTrig();
#if defined(__i386__) || defined(__x86_64__)
  if (do256bit) benchSleef256_SPTrig();
  if (do512bit) benchSleef512_SPTrig();
#endif

  fclose(fp);

  sprintf(fn, "%sspnontrig.out", fnBase);
  fp = fopen(fn, "w");
  fprintf(fp, "%s\n", columnTitle);

  if (do128bit) benchSleef128_SPNontrig();
#if defined(__i386__) || defined(__x86_64__)
  if (do256bit) benchSleef256_SPNontrig();
  if (do512bit) benchSleef512_SPNontrig();
#endif

  fclose(fp);

  exit(0);
}
