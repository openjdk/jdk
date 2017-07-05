/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSET_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSET_INLINE_HPP

#include "gc_implementation/g1/heapRegionSet.hpp"

//////////////////// HeapRegionSetBase ////////////////////

inline void HeapRegionSetBase::update_for_addition(HeapRegion* hr) {
  // Assumes the caller has already verified the region.

  _length           += 1;
  _region_num       += hr->region_num();
  _total_used_bytes += hr->used();
}

inline void HeapRegionSetBase::add_internal(HeapRegion* hr) {
  hrs_assert_region_ok(this, hr, NULL);
  assert(hr->next() == NULL, hrs_ext_msg(this, "should not already be linked"));

  update_for_addition(hr);
  hr->set_containing_set(this);
}

inline void HeapRegionSetBase::update_for_removal(HeapRegion* hr) {
  // Assumes the caller has already verified the region.
  assert(_length > 0, hrs_ext_msg(this, "pre-condition"));
  _length -= 1;

  uint region_num_diff = hr->region_num();
  assert(region_num_diff <= _region_num,
         hrs_err_msg("[%s] region's region num: %u "
                     "should be <= region num: %u",
                     name(), region_num_diff, _region_num));
  _region_num -= region_num_diff;

  size_t used_bytes = hr->used();
  assert(used_bytes <= _total_used_bytes,
         hrs_err_msg("[%s] region's used bytes: "SIZE_FORMAT" "
                     "should be <= used bytes: "SIZE_FORMAT,
                     name(), used_bytes, _total_used_bytes));
  _total_used_bytes -= used_bytes;
}

inline void HeapRegionSetBase::remove_internal(HeapRegion* hr) {
  hrs_assert_region_ok(this, hr, this);
  assert(hr->next() == NULL, hrs_ext_msg(this, "should already be unlinked"));

  hr->set_containing_set(NULL);
  update_for_removal(hr);
}

//////////////////// HeapRegionSet ////////////////////

inline void HeapRegionSet::add(HeapRegion* hr) {
  hrs_assert_mt_safety_ok(this);
  // add_internal() will verify the region.
  add_internal(hr);
}

inline void HeapRegionSet::remove(HeapRegion* hr) {
  hrs_assert_mt_safety_ok(this);
  // remove_internal() will verify the region.
  remove_internal(hr);
}

inline void HeapRegionSet::remove_with_proxy(HeapRegion* hr,
                                             HeapRegionSet* proxy_set) {
  // No need to fo the MT safety check here given that this method
  // does not update the contents of the set but instead accumulates
  // the changes in proxy_set which is assumed to be thread-local.
  hrs_assert_sets_match(this, proxy_set);
  hrs_assert_region_ok(this, hr, this);

  hr->set_containing_set(NULL);
  proxy_set->update_for_addition(hr);
}

//////////////////// HeapRegionLinkedList ////////////////////

inline void HeapRegionLinkedList::add_as_head(HeapRegion* hr) {
  hrs_assert_mt_safety_ok(this);
  assert((length() == 0 && _head == NULL && _tail == NULL) ||
         (length() >  0 && _head != NULL && _tail != NULL),
         hrs_ext_msg(this, "invariant"));
  // add_internal() will verify the region.
  add_internal(hr);

  // Now link the region.
  if (_head != NULL) {
    hr->set_next(_head);
  } else {
    _tail = hr;
  }
  _head = hr;
}

inline void HeapRegionLinkedList::add_as_tail(HeapRegion* hr) {
  hrs_assert_mt_safety_ok(this);
  assert((length() == 0 && _head == NULL && _tail == NULL) ||
         (length() >  0 && _head != NULL && _tail != NULL),
         hrs_ext_msg(this, "invariant"));
  // add_internal() will verify the region.
  add_internal(hr);

  // Now link the region.
  if (_tail != NULL) {
    _tail->set_next(hr);
  } else {
    _head = hr;
  }
  _tail = hr;
}

inline HeapRegion* HeapRegionLinkedList::remove_head() {
  hrs_assert_mt_safety_ok(this);
  assert(!is_empty(), hrs_ext_msg(this, "the list should not be empty"));
  assert(length() > 0 && _head != NULL && _tail != NULL,
         hrs_ext_msg(this, "invariant"));

  // We need to unlink it first.
  HeapRegion* hr = _head;
  _head = hr->next();
  if (_head == NULL) {
    _tail = NULL;
  }
  hr->set_next(NULL);

  // remove_internal() will verify the region.
  remove_internal(hr);
  return hr;
}

inline HeapRegion* HeapRegionLinkedList::remove_head_or_null() {
  hrs_assert_mt_safety_ok(this);

  if (!is_empty()) {
    return remove_head();
  } else {
    return NULL;
  }
}

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSET_INLINE_HPP
