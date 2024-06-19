/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_QUICKSORT_HPP
#define SHARE_UTILITIES_QUICKSORT_HPP

#include "memory/allStatic.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class QuickSort : AllStatic {

 private:
  template<class T>
  static void swap_elements(T* array, size_t x, size_t y) {
    swap(array[x], array[y]);
  }

  // As pivot we use the median of the first, last and middle elements.
  // We swap these three values as needed so that
  //     array[first] <= array[middle] <= array[last]
  // As a result, the first and last elements are placed in the proper
  // partition, and arrays of length <= 3 are sorted.
  // The middle index is returned, designating that element as the pivot.
  template<class T, class C>
  static size_t find_pivot(T* array, size_t length, C comparator) {
    assert(length > 1, "length of array must be > 0");

    size_t middle_index = length / 2;
    size_t last_index = length - 1;

    if (comparator(array[0], array[middle_index]) > 0) {
      swap_elements(array, 0, middle_index);
    }
    if (comparator(array[0], array[last_index]) > 0) {
      swap_elements(array, 0, last_index);
    }
    if (comparator(array[middle_index], array[last_index]) > 0) {
      swap_elements(array, middle_index, last_index);
    }
    // Now the value in the middle of the array is the median
    // of the first, last and middle values. Use this as pivot.
    return middle_index;
  }

  template<class T, class C>
  static size_t partition(T* array, size_t pivot, size_t length, C comparator) {
    size_t left_index = 0;
    size_t right_index = length - 1;
    T pivot_val = array[pivot];

    for ( ; true; ++left_index, --right_index) {
      for ( ; comparator(array[left_index], pivot_val) < 0; ++left_index) {
        assert(left_index < (length - 1), "reached end of partition");
      }
      for ( ; comparator(array[right_index], pivot_val) > 0; --right_index) {
        assert(right_index > 0, "reached start of partition");
      }
      if (left_index < right_index) {
        swap_elements(array, left_index, right_index);
      } else {
        return right_index;
      }
    }
  }

 public:
  template<class T, class C>
  static void sort(T* array, size_t length, C comparator) {
    if (length < 2) {
      return;
    }
    size_t pivot = find_pivot(array, length, comparator);
    if (length < 4) {
      // arrays up to length 3 will be sorted after finding the pivot
      return;
    }
    size_t split = partition(array, pivot, length, comparator);
    size_t first_part_length = split + 1;
    sort(array, first_part_length, comparator);
    sort(&array[first_part_length], length - first_part_length, comparator);
  }
};

#endif // SHARE_UTILITIES_QUICKSORT_HPP
