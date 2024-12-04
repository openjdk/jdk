#include <stdbool.h>
#include "simdsort.h"
#include "library_entries.h"

#define DLL_PUBLIC __attribute__((visibility("default")))

const int64_t VM_AVX2     = (1ULL << 19);
const int64_t VM_AVX512DQ = (1ULL << 28);

DLL_PUBLIC
void simdsort_link(struct library* lib, int64_t vm_features) {
    bool has_avx512dq = (vm_features & VM_AVX512DQ) != 0;
    bool has_avx2     = (vm_features & VM_AVX2) != 0;

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
