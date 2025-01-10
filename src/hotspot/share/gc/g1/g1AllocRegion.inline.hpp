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

#ifndef SHARE_GC_G1_G1ALLOCREGION_INLINE_HPP
#define SHARE_GC_G1_G1ALLOCREGION_INLINE_HPP

#include "gc/g1/g1AllocRegion.hpp"

#include "gc/g1/g1HeapRegion.inline.hpp"

#define assert_alloc_region(p, message)                                  \
  do {                                                                   \
    assert((p), "[%s] %s c: %u r: " PTR_FORMAT,                          \
           _name, (message), _count, p2i(_alloc_region)                  \
          );                                                             \
  } while (0)


inline void G1AllocRegion::reset_alloc_region() {
  _alloc_region = _dummy_region;
}

inline HeapWord* G1AllocRegion::par_allocate(G1HeapRegion* alloc_region, size_t word_size) {
  assert(alloc_region != nullptr, "pre-condition");
  assert(!alloc_region->is_empty(), "pre-condition");
  size_t temp;
  return alloc_region->par_allocate(word_size, word_size, &temp);
}

inline HeapWord* G1AllocRegion::attempt_allocation(size_t min_word_size,
                                                   size_t desired_word_size,
                                                   size_t* actual_word_size) {
  G1HeapRegion* alloc_region = _alloc_region;
  assert_alloc_region(alloc_region != nullptr && !alloc_region->is_empty(), "not initialized properly");

  HeapWord* result = alloc_region->par_allocate(min_word_size, desired_word_size, actual_word_size);

  if (result != nullptr) {
    trace("alloc", min_word_size, desired_word_size, *actual_word_size, result);
  } else {
    trace("alloc failed", min_word_size, desired_word_size);
  }
  return result;
}

inline HeapWord* G1AllocRegion::attempt_allocation_locked(size_t word_size) {
  size_t temp;
  return attempt_allocation_locked(word_size, word_size, &temp);
}

inline HeapWord* G1AllocRegion::attempt_allocation_locked(size_t min_word_size,
                                                          size_t desired_word_size,
                                                          size_t* actual_word_size) {
  HeapWord* result = attempt_allocation(min_word_size, desired_word_size, actual_word_size);
  if (result != nullptr) {
    return result;
  }

  return attempt_allocation_using_new_region(min_word_size, desired_word_size, actual_word_size);
}

inline HeapWord* G1AllocRegion::attempt_allocation_using_new_region(size_t min_word_size,
                                                                    size_t desired_word_size,
                                                                    size_t* actual_word_size) {
  retire(true /* fill_up */);
  HeapWord* result = new_alloc_region_and_allocate(desired_word_size);
  if (result != nullptr) {
    *actual_word_size = desired_word_size;
    trace("alloc locked (second attempt)", min_word_size, desired_word_size, *actual_word_size, result);
    return result;
  }
  trace("alloc locked failed", min_word_size, desired_word_size);
  return nullptr;
}

inline HeapWord* MutatorAllocRegion::attempt_retained_allocation(size_t min_word_size,
                                                                 size_t desired_word_size,
                                                                 size_t* actual_word_size) {
  if (_retained_alloc_region != nullptr) {
    HeapWord* result = _retained_alloc_region->par_allocate(min_word_size, desired_word_size, actual_word_size);
    if (result != nullptr) {
      trace("alloc retained", min_word_size, desired_word_size, *actual_word_size, result);
      return result;
    }
  }
  return nullptr;
}

#endif // SHARE_GC_G1_G1ALLOCREGION_INLINE_HPP
