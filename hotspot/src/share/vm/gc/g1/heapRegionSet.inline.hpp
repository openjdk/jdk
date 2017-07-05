/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_HEAPREGIONSET_INLINE_HPP
#define SHARE_VM_GC_G1_HEAPREGIONSET_INLINE_HPP

#include "gc/g1/heapRegionSet.hpp"

inline void HeapRegionSetBase::add(HeapRegion* hr) {
  check_mt_safety();
  assert_heap_region_set(hr->containing_set() == NULL, "should not already have a containing set");
  assert_heap_region_set(hr->next() == NULL, "should not already be linked");
  assert_heap_region_set(hr->prev() == NULL, "should not already be linked");

  _count.increment(1u, hr->capacity());
  hr->set_containing_set(this);
  verify_region(hr);
}

inline void HeapRegionSetBase::remove(HeapRegion* hr) {
  check_mt_safety();
  verify_region(hr);
  assert_heap_region_set(hr->next() == NULL, "should already be unlinked");
  assert_heap_region_set(hr->prev() == NULL, "should already be unlinked");

  hr->set_containing_set(NULL);
  assert_heap_region_set(_count.length() > 0, "pre-condition");
  _count.decrement(1u, hr->capacity());
}

inline void FreeRegionList::add_ordered(HeapRegion* hr) {
  assert_free_region_list((length() == 0 && _head == NULL && _tail == NULL && _last == NULL) ||
                          (length() >  0 && _head != NULL && _tail != NULL),
                          "invariant");
  // add() will verify the region and check mt safety.
  add(hr);

  // Now link the region
  if (_head != NULL) {
    HeapRegion* curr;

    if (_last != NULL && _last->hrm_index() < hr->hrm_index()) {
      curr = _last;
    } else {
      curr = _head;
    }

    // Find first entry with a Region Index larger than entry to insert.
    while (curr != NULL && curr->hrm_index() < hr->hrm_index()) {
      curr = curr->next();
    }

    hr->set_next(curr);

    if (curr == NULL) {
      // Adding at the end
      hr->set_prev(_tail);
      _tail->set_next(hr);
      _tail = hr;
    } else if (curr->prev() == NULL) {
      // Adding at the beginning
      hr->set_prev(NULL);
      _head = hr;
      curr->set_prev(hr);
    } else {
      hr->set_prev(curr->prev());
      hr->prev()->set_next(hr);
      curr->set_prev(hr);
    }
  } else {
    // The list was empty
    _tail = hr;
    _head = hr;
  }
  _last = hr;
}

inline HeapRegion* FreeRegionList::remove_from_head_impl() {
  HeapRegion* result = _head;
  _head = result->next();
  if (_head == NULL) {
    _tail = NULL;
  } else {
    _head->set_prev(NULL);
  }
  result->set_next(NULL);
  return result;
}

inline HeapRegion* FreeRegionList::remove_from_tail_impl() {
  HeapRegion* result = _tail;

  _tail = result->prev();
  if (_tail == NULL) {
    _head = NULL;
  } else {
    _tail->set_next(NULL);
  }
  result->set_prev(NULL);
  return result;
}

inline HeapRegion* FreeRegionList::remove_region(bool from_head) {
  check_mt_safety();
  verify_optional();

  if (is_empty()) {
    return NULL;
  }
  assert_free_region_list(length() > 0 && _head != NULL && _tail != NULL, "invariant");

  HeapRegion* hr;

  if (from_head) {
    hr = remove_from_head_impl();
  } else {
    hr = remove_from_tail_impl();
  }

  if (_last == hr) {
    _last = NULL;
  }

  // remove() will verify the region and check mt safety.
  remove(hr);
  return hr;
}

#endif // SHARE_VM_GC_G1_HEAPREGIONSET_INLINE_HPP

