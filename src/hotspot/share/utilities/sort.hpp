/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_UTILITIES_SORT_HPP
#define SHARE_UTILITIES_SORT_HPP

#include "memory/allStatic.hpp"

// An insertion sort that is stable and inplace.
// This algorithm should be the ideal solution to sort a sequence with few elements. Arrays::sort
// uses insertion sort for arrays up to around 50 elements.
// comp should return a value > 0 iff the first argument is larger than the second argument. A full
// comparison function satisfies this requirement but a simple a > b ? 1 : 0 also satisfies it.
class InsertionSort : AllStatic {
public:
  template <class T, class Compare>
  static void sort(T* data, int size, Compare comp) {
    if (size == 0) {
      return;
    }

    T* begin = data;
    T* end = data + size;
    for (T* current = begin + 1; current < end; current++) {
      T current_elem = *current;

      // Elements in [begin, current) has already been sorted, we search backward to find a
      // location to insert the element at current. In the meantime, shift all elements on the way
      // up by 1.
      T* pos = current;
      while (pos > begin) {
        // Because the sort is stable, we must insert the current element at the first location at
        // which the element is not greater than the current element (note that we are traversing
        // backward)
        T* prev = pos - 1;
        if (comp(*prev, current_elem) <= 0) {
          break;
        }

        *pos = *prev;
        pos = prev;
      }

      // Move current_elem to pos since all elements in [pos, current) have been shifted up by 1
      if (pos < current) {
        *pos = current_elem;
      }
    }
  }
};

#endif // SHARE_UTILITIES_SORT_HPP
