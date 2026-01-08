//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <assert.h>

#include "misc.h"

#if defined(__MINGW32__) || defined(__MINGW64__) || defined(_MSC_VER)
#include <sys/timeb.h>

EXPORT void *Sleef_malloc(size_t z) { return _aligned_malloc(z, 256); }
EXPORT void Sleef_free(void *ptr) { _aligned_free(ptr); }

EXPORT uint64_t Sleef_currentTimeMicros() {
  struct __timeb64 t;
  _ftime64(&t);
  return t.time * INT64_C(1000000) + t.millitm*1000;
}
#elif defined(__APPLE__)
#include <sys/time.h>

EXPORT void *Sleef_malloc(size_t z) { void *ptr = NULL; posix_memalign(&ptr, 256, z); return ptr; }
EXPORT void Sleef_free(void *ptr) { free(ptr); }

EXPORT uint64_t Sleef_currentTimeMicros() {
  struct timeval time;
  gettimeofday(&time, NULL);
  return (uint64_t)((time.tv_sec * INT64_C(1000000)) + time.tv_usec);
}
#else // #if defined(__MINGW32__) || defined(__MINGW64__) || defined(_MSC_VER)
#include <time.h>
#include <unistd.h>
#if defined(__FreeBSD__) || defined(__OpenBSD__)
#include <stdlib.h>
#else
#include <malloc.h>
#endif

EXPORT void *Sleef_malloc(size_t z) { void *ptr = NULL; posix_memalign(&ptr, 4096, z); return ptr; }
EXPORT void Sleef_free(void *ptr) { free(ptr); }

EXPORT uint64_t Sleef_currentTimeMicros() {
  struct timespec tp;
  clock_gettime(CLOCK_MONOTONIC, &tp);
  return (uint64_t)tp.tv_sec * INT64_C(1000000) + ((uint64_t)tp.tv_nsec/1000);
}
#endif // #if defined(__MINGW32__) || defined(__MINGW64__) || defined(_MSC_VER)

#ifdef _MSC_VER
#include <intrin.h>
EXPORT void Sleef_x86CpuID(int32_t out[4], uint32_t eax, uint32_t ecx) {
  __cpuidex(out, eax, ecx);
}
#else
#if defined(__x86_64__) || defined(__i386__)
EXPORT void Sleef_x86CpuID(int32_t out[4], uint32_t eax, uint32_t ecx) {
  uint32_t a, b, c, d;
  __asm__ __volatile__ ("cpuid" : "=a" (a), "=b" (b), "=c" (c), "=d" (d) : "a" (eax), "c"(ecx));
  out[0] = a; out[1] = b; out[2] = c; out[3] = d;
}
#endif
#endif

#if defined(__i386__) || defined(__x86_64__) || defined(_MSC_VER)
static char x86BrandString[256];

EXPORT char *Sleef_getCpuIdString() {
  union {
    int32_t info[4];
    uint8_t str[16];
  } u;
  int i,j;
  char *p;

  p = x86BrandString;

  for(i=0;i<3;i++) {
    Sleef_x86CpuID(u.info, i + 0x80000002, 0);

    for(j=0;j<16;j++) {
      *p++ = u.str[j];
    }
  }

  *p++ = '\n';

  return x86BrandString;
}
#else
EXPORT char *Sleef_getCpuIdString() {
  return "Unknown architecture";
}
#endif
