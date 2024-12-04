#ifndef SIMDSORT_H
#define SIMDSORT_H

#include <stdint.h>
#include "jni.h"

#ifdef __cplusplus
extern "C" {
#endif


//typedef void (*sort_jint_func)(jint*, jint, jint);
//typedef void (*sort_jlong_func)(jlong*, jint, jint);
//typedef void (*sort_jfloat_func)(jfloat*, jint, jint);
//typedef void (*sort_jdouble_func)(jdouble*, jint, jint);


//typedef struct partition (*partition_jint_func)(jint*, jint, jint, jint, jint);
//typedef struct partition (*partition_jlong_func)(jlong*, jint, jint, jint, jint);
//typedef struct partition (*partition_jfloat_func)(jfloat*, jint, jint, jint, jint);
//typedef struct partition (*partition_jdouble_func)(jdouble*, jint, jint, jint, jint);
//typedef void (*partition_jint_func)(jint*, jint, jint, jint*, jint, jint);
//typedef void (*partition_jlong_func)(jlong*, jint, jint, jint*, jint, jint);
//typedef void (*partition_jfloat_func)(jfloat*, jint, jint, jint*, jint, jint);
//typedef void (*partition_jdouble_func)(jdouble*, jint, jint, jint*, jint, jint);

//struct partition { 
//  jint lower;
//  jint upper;
//};
  
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

void simdsort_link(struct library* lib, int config);


#ifdef __cplusplus
}
#endif

#endif // SIMDSORT_H
