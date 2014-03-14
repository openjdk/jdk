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

inline void HeapRegionSetBase::add(HeapRegion* hr) {
  check_mt_safety();
  assert(hr->containing_set() == NULL, hrs_ext_msg(this, "should not already have a containing set %u"));
  assert(hr->next() == NULL, hrs_ext_msg(this, "should not already be linked"));

  _count.increment(1u, hr->capacity());
  hr->set_containing_set(this);
  verify_region(hr);
}

inline void HeapRegionSetBase::remove(HeapRegion* hr) {
  check_mt_safety();
  verify_region(hr);
  assert(hr->next() == NULL, hrs_ext_msg(this, "should already be unlinked"));

  hr->set_containing_set(NULL);
  assert(_count.length() > 0, hrs_ext_msg(this, "pre-condition"));
  _count.decrement(1u, hr->capacity());
}

inline void FreeRegionList::add_as_head(HeapRegion* hr) {
  assert((length() == 0 && _head == NULL && _tail == NULL) ||
         (length() >  0 && _head != NULL && _tail != NULL),
         hrs_ext_msg(this, "invariant"));
  // add() will verify the region and check mt safety.
  add(hr);

  // Now link the region.
  if (_head != NULL) {
    hr->set_next(_head);
  } else {
    _tail = hr;
  }
  _head = hr;
}

inline void FreeRegionList::add_as_tail(HeapRegion* hr) {
  check_mt_safety();
  assert((length() == 0 && _head == NULL && _tail == NULL) ||
         (length() >  0 && _head != NULL && _tail != NULL),
         hrs_ext_msg(this, "invariant"));
  // add() will verify the region and check mt safety
  add(hr);

  // Now link the region.
  if (_tail != NULL) {
    _tail->set_next(hr);
  } else {
    _head = hr;
  }
  _tail = hr;
}

inline HeapRegion* FreeRegionList::remove_head() {
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

  // remove() will verify the region and check mt safety
  remove(hr);
  return hr;
}

inline HeapRegion* FreeRegionList::remove_head_or_null() {
  check_mt_safety();
  if (!is_empty()) {
    return remove_head();
  } else {
    return NULL;
  }
}

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSET_INLINE_HPP
