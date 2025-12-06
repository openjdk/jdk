#include <altivec.h>

__vector double sleef_cpuid_VSX0;
__vector unsigned long long sleef_cpuid_VSX1, sleef_cpuid_VSX3;

void sleef_tryVSX3() {
  sleef_cpuid_VSX0 = vec_insert_exp(sleef_cpuid_VSX1, sleef_cpuid_VSX3);
}
