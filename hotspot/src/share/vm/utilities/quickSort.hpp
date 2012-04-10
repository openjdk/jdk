/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#ifndef SHARE_VM_UTILITIES_QUICKSORT_HPP
#define SHARE_VM_UTILITIES_QUICKSORT_HPP

#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"

class QuickSort : AllStatic {

 private:
  template<class T>
  static void swap(T* array, int x, int y) {
    T tmp = array[x];
    array[x] = array[y];
    array[y] = tmp;
  }

  // As pivot we use the median of the first, last and middle elements.
  // We swap in these three values at the right place in the array. This
  // means that this method not only returns the index of the pivot
  // element. It also alters the array so that:
  //     array[first] <= array[middle] <= array[last]
  // A side effect of this is that arrays of length <= 3 are sorted.
  template<class T, class C>
  static int find_pivot(T* array, int length, C comparator) {
    assert(length > 1, "length of array must be > 0");

    int middle_index = length / 2;
    int last_index = length - 1;

    if (comparator(array[0], array[middle_index]) == 1) {
      swap(array, 0, middle_index);
    }
    if (comparator(array[0], array[last_index]) == 1) {
      swap(array, 0, last_index);
    }
    if (comparator(array[middle_index], array[last_index]) == 1) {
      swap(array, middle_index, last_index);
    }
    // Now the value in the middle of the array is the median
    // of the fist, last and middle values. Use this as pivot.
    return middle_index;
  }

  template<class T, class C, bool idempotent>
  static int partition(T* array, int pivot, int length, C comparator) {
    int left_index = -1;
    int right_index = length;
    T pivot_val = array[pivot];

    while (true) {
      do {
        left_index++;
      } while (comparator(array[left_index], pivot_val) == -1);
      do {
        right_index--;
      } while (comparator(array[right_index], pivot_val) == 1);

      if (left_index < right_index) {
        if (!idempotent || comparator(array[left_index], array[right_index]) != 0) {
          swap(array, left_index, right_index);
        }
      } else {
        return right_index;
      }
    }

    ShouldNotReachHere();
    return 0;
  }

  template<class T, class C, bool idempotent>
  static void inner_sort(T* array, int length, C comparator) {
    if (length < 2) {
      return;
    }
    int pivot = find_pivot(array, length, comparator);
    if (length < 4) {
      // arrays up to length 3 will be sorted after finding the pivot
      return;
    }
    int split = partition<T, C, idempotent>(array, pivot, length, comparator);
    int first_part_length = split + 1;
    inner_sort<T, C, idempotent>(array, first_part_length, comparator);
    inner_sort<T, C, idempotent>(&array[first_part_length], length - first_part_length, comparator);
  }

 public:
  // The idempotent parameter prevents the sort from
  // reordering a previous valid sort by not swapping
  // fields that compare as equal. This requires extra
  // calls to the comparator, so the performance
  // impact depends on the comparator.
  template<class T, class C>
  static void sort(T* array, int length, C comparator, bool idempotent) {
    // Switch "idempotent" from function paramter to template parameter
    if (idempotent) {
      inner_sort<T, C, true>(array, length, comparator);
    } else {
      inner_sort<T, C, false>(array, length, comparator);
    }
  }

  // for unit testing
#ifndef PRODUCT
  static void print_array(const char* prefix, int* array, int length);
  static bool compare_arrays(int* actual, int* expected, int length);
  template <class C> static bool sort_and_compare(int* arrayToSort, int* expectedResult, int length, C comparator, bool idempotent = false);
  static void test_quick_sort();
#endif
};


#endif //SHARE_VM_UTILITIES_QUICKSORT_HPP
