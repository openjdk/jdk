extern "C" {
  void avx2_sort_int  (int32_t* array, int32_t from_index, int32_t to_index);
  void avx2_sort_float(float*   array, int32_t from_index, int32_t to_index);
  
  void avx512_sort_int   (int32_t* array, int32_t from_index, int32_t to_index);
  void avx512_sort_long  (int64_t* array, int32_t from_index, int32_t to_index);
  void avx512_sort_float (float*   array, int32_t from_index, int32_t to_index);
  void avx512_sort_double(double*  array, int32_t from_index, int32_t to_index);
  
  void avx2_partition_int  (int32_t* array, int32_t from_index, int32_t to_index, int32_t* pivot_indices, int32_t index_pivot1, int32_t index_pivot2);
  void avx2_partition_float(float*   array, int32_t from_index, int32_t to_index, int32_t* pivot_indices, int32_t index_pivot1, int32_t index_pivot2);

  void avx512_partition_int   (int32_t* array, int32_t from_index, int32_t to_index, int32_t* pivot_indices, int32_t index_pivot1, int32_t index_pivot2);
  void avx512_partition_long  (int64_t* array, int32_t from_index, int32_t to_index, int32_t* pivot_indices, int32_t index_pivot1, int32_t index_pivot2);
  void avx512_partition_float (float*   array, int32_t from_index, int32_t to_index, int32_t* pivot_indices, int32_t index_pivot1, int32_t index_pivot2);
  void avx512_partition_double(double*  array, int32_t from_index, int32_t to_index, int32_t* pivot_indices, int32_t index_pivot1, int32_t index_pivot2);
}

#define DLL_PUBLIC __attribute__((visibility("default")))
#define INSERTION_SORT_THRESHOLD_32BIT 16
#define INSERTION_SORT_THRESHOLD_64BIT 20
