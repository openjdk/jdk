#include <vecintrin.h>

__vector float sleef_cpuid_VXE2;
__vector int sleef_cpuid_VXE1;

void sleef_tryVXE2() {
  sleef_cpuid_VXE2 = vec_float(sleef_cpuid_VXE1);
}
