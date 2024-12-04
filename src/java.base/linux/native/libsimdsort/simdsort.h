#ifndef SIMDSORT_H
#define SIMDSORT_H

#include <stdint.h>
#include "jni.h"

#ifdef __cplusplus
extern "C" {
#endif

struct library {
  void (*sort_jint)   (jint*,    jint, jint);
  void (*sort_jlong)  (jlong*,   jint, jint);
  void (*sort_jfloat) (jfloat*,  jint, jint);
  void (*sort_jdouble)(jdouble*, jint, jint);

  void (*partition_jint)   (jint*,    jint, jint, jint*, jint, jint);
  void (*partition_jlong)  (jlong*,   jint, jint, jint*, jint, jint);
  void (*partition_jfloat) (jfloat*,  jint, jint, jint*, jint, jint);
  void (*partition_jdouble)(jdouble*, jint, jint, jint*, jint, jint);
};

void simdsort_link(struct library* lib, int64_t vm_features);


#ifdef __cplusplus
}
#endif

#endif // SIMDSORT_H
