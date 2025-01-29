#include "jni.h"

#define DLL_PUBLIC __attribute__((visibility("default")))
#define INSERTION_SORT_THRESHOLD_32BIT 16
#define INSERTION_SORT_THRESHOLD_64BIT 20

#ifdef __cplusplus
extern "C" {
#endif

void avx2_sort_int  (jint*  array, jint from_index, jint to_index);
void avx2_sort_float(float* array, jint from_index, jint to_index);

void avx512_sort_int   (jint*     array, jint from_index, jint to_index);
void avx512_sort_long  (jlong*    array, jint from_index, jint to_index);
void avx512_sort_float (jfloat*   array, jint from_index, jint to_index);
void avx512_sort_double(jdouble*  array, jint from_index, jint to_index);

void avx2_partition_int  (jint*   array, jint from_index, jint to_index, jint* pivot_indices, jint index_pivot1, jint index_pivot2);
void avx2_partition_float(jfloat* array, jint from_index, jint to_index, jint* pivot_indices, jint index_pivot1, jint index_pivot2);

void avx512_partition_int   (jint*    array, jint from_index, jint to_index, jint* pivot_indices, jint index_pivot1, jint index_pivot2);
void avx512_partition_long  (jlong*   array, jint from_index, jint to_index, jint* pivot_indices, jint index_pivot1, jint index_pivot2);
void avx512_partition_float (jfloat*  array, jint from_index, jint to_index, jint* pivot_indices, jint index_pivot1, jint index_pivot2);
void avx512_partition_double(jdouble* array, jint from_index, jint to_index, jint* pivot_indices, jint index_pivot1, jint index_pivot2);

#ifdef __cplusplus
}
#endif
