#include <stdbool.h>
#include "simdsort.h"
#include "library_entries.h"

#define DLL_PUBLIC __attribute__((visibility("default")))

DLL_PUBLIC
void simdsort_link(struct library* lib, int config) {
    bool has_avx512dq = config > 3;
    bool has_avx2     = config > 1;

    lib->sort_jint    = has_avx512dq ? &avx512_sort_int :
                        has_avx2     ? &avx2_sort_int : 0;
    lib->sort_jfloat  = has_avx512dq ? &avx512_sort_float:
                        has_avx2     ? &avx2_sort_float: 0;

    lib->sort_jlong   = has_avx512dq ? &avx512_sort_long   : 0;
    lib->sort_jdouble = has_avx512dq ? &avx512_sort_double : 0;

    lib->partition_jint    = has_avx512dq ? &avx512_partition_int :
                             has_avx2     ? &avx2_partition_int : 0;
    lib->partition_jfloat  = has_avx512dq ? &avx512_partition_float:
                             has_avx2     ? &avx2_partition_float: 0;

    lib->partition_jlong   = has_avx512dq ? &avx512_partition_long   : 0;
    lib->partition_jdouble = has_avx512dq ? &avx512_partition_double : 0;
}
