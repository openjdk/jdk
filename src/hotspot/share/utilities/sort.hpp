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
// For the requirements for the parameters, see those of std::sort.
class InsertionSort : AllStatic {
public:
  template <class RandomIt, class Compare>
  static void sort(RandomIt begin, RandomIt end, Compare comp) {
    if (begin == end) {
      // Empty array
      return;
    }

    for (RandomIt current = begin + 1; current < end; current++) {
      typename RandomIt::value_type current_elem = *current;

      // Elements in [begin, current) has already been sorted, we search backward to find a
      // location to insert the element at current
      RandomIt pos = current;
      while (pos > begin) {
        // Since the sort is stable, we must insert the current element at the first location at
        // which the element is not greater than the current element (note that we are traversing
        // backward)
        RandomIt prev = pos - 1;
        if (!comp(current_elem, *prev)) {
          break;
        }

        pos = prev;
      }

      // Shift all elements in [pos, current) up by one then move current_elem to pos
      if (pos < current) {
        for (RandomIt i = current; i > pos; i--) {
          *i = *(i - 1);
        }
        *pos = current_elem;
      }
    }
  }
};

#endif // SHARE_UTILITIES_SORT_HPP
