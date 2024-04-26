/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1HEAPREGIONSET_INLINE_HPP
#define SHARE_GC_G1_G1HEAPREGIONSET_INLINE_HPP

#include "gc/g1/g1HeapRegionSet.hpp"

#include "gc/g1/g1NUMA.hpp"

inline void HeapRegionSetBase::add(HeapRegion* hr) {
  check_mt_safety();
  assert_heap_region_set(hr->containing_set() == nullptr, "should not already have a containing set");
  assert_heap_region_set(hr->next() == nullptr, "should not already be linked");
  assert_heap_region_set(hr->prev() == nullptr, "should not already be linked");

  _length++;
  hr->set_containing_set(this);
  verify_region(hr);
}

inline void HeapRegionSetBase::remove(HeapRegion* hr) {
  check_mt_safety();
  verify_region(hr);
  assert_heap_region_set(hr->next() == nullptr, "should already be unlinked");
  assert_heap_region_set(hr->prev() == nullptr, "should already be unlinked");

  hr->set_containing_set(nullptr);
  assert_heap_region_set(_length > 0, "pre-condition");
  _length--;
}

inline void FreeRegionList::add_to_tail(HeapRegion* region_to_add) {
  assert_free_region_list((length() == 0 && _head == nullptr && _tail == nullptr && _last == nullptr) ||
                          (length() >  0 && _head != nullptr && _tail != nullptr && _tail->hrm_index() < region_to_add->hrm_index()),
                          "invariant");
  // add() will verify the region and check mt safety.
  add(region_to_add);

  if (_head != nullptr) {
    // Link into list, next is already null, no need to set.
    region_to_add->set_prev(_tail);
    _tail->set_next(region_to_add);
    _tail = region_to_add;
  } else {
    // Empty list, this region is now the list.
    _head = region_to_add;
    _tail = region_to_add;
  }
  increase_length(region_to_add->node_index());
}

inline void FreeRegionList::add_ordered(HeapRegion* hr) {
  assert_free_region_list((length() == 0 && _head == nullptr && _tail == nullptr && _last == nullptr) ||
                          (length() >  0 && _head != nullptr && _tail != nullptr),
                          "invariant");
  // add() will verify the region and check mt safety.
  add(hr);

  // Now link the region
  if (_head != nullptr) {
    HeapRegion* curr;

    if (_last != nullptr && _last->hrm_index() < hr->hrm_index()) {
      curr = _last;
    } else {
      curr = _head;
    }

    // Find first entry with a Region Index larger than entry to insert.
    while (curr != nullptr && curr->hrm_index() < hr->hrm_index()) {
      curr = curr->next();
    }

    hr->set_next(curr);

    if (curr == nullptr) {
      // Adding at the end
      hr->set_prev(_tail);
      _tail->set_next(hr);
      _tail = hr;
    } else if (curr->prev() == nullptr) {
      // Adding at the beginning
      hr->set_prev(nullptr);
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

  increase_length(hr->node_index());
}

inline HeapRegion* FreeRegionList::remove_from_head_impl() {
  HeapRegion* result = _head;
  _head = result->next();
  if (_head == nullptr) {
    _tail = nullptr;
  } else {
    _head->set_prev(nullptr);
  }
  result->set_next(nullptr);
  return result;
}

inline HeapRegion* FreeRegionList::remove_from_tail_impl() {
  HeapRegion* result = _tail;

  _tail = result->prev();
  if (_tail == nullptr) {
    _head = nullptr;
  } else {
    _tail->set_next(nullptr);
  }
  result->set_prev(nullptr);
  return result;
}

inline HeapRegion* FreeRegionList::remove_region(bool from_head) {
  check_mt_safety();
  verify_optional();

  if (is_empty()) {
    return nullptr;
  }
  assert_free_region_list(length() > 0 && _head != nullptr && _tail != nullptr, "invariant");

  HeapRegion* hr;

  if (from_head) {
    hr = remove_from_head_impl();
  } else {
    hr = remove_from_tail_impl();
  }

  if (_last == hr) {
    _last = nullptr;
  }

  // remove() will verify the region and check mt safety.
  remove(hr);

  decrease_length(hr->node_index());

  return hr;
}

inline HeapRegion* FreeRegionList::remove_region_with_node_index(bool from_head,
                                                                 uint requested_node_index) {
  assert(UseNUMA, "Invariant");

  const uint max_search_depth = G1NUMA::numa()->max_search_depth();
  HeapRegion* cur;

  // Find the region to use, searching from _head or _tail as requested.
  size_t cur_depth = 0;
  if (from_head) {
    for (cur = _head;
         cur != nullptr && cur_depth < max_search_depth;
         cur = cur->next(), ++cur_depth) {
      if (requested_node_index == cur->node_index()) {
        break;
      }
    }
  } else {
    for (cur = _tail;
         cur != nullptr && cur_depth < max_search_depth;
         cur = cur->prev(), ++cur_depth) {
      if (requested_node_index == cur->node_index()) {
        break;
      }
    }
  }

  // Didn't find a region to use.
  if (cur == nullptr || cur_depth >= max_search_depth) {
    return nullptr;
  }

  // Splice the region out of the list.
  HeapRegion* prev = cur->prev();
  HeapRegion* next = cur->next();
  if (prev == nullptr) {
    _head = next;
  } else {
    prev->set_next(next);
  }
  if (next == nullptr) {
    _tail = prev;
  } else {
    next->set_prev(prev);
  }
  cur->set_prev(nullptr);
  cur->set_next(nullptr);

  if (_last == cur) {
    _last = nullptr;
  }

  remove(cur);
  decrease_length(cur->node_index());

  return cur;
}

inline void FreeRegionList::NodeInfo::increase_length(uint node_index) {
  if (node_index < _num_nodes) {
    _length_of_node[node_index] += 1;
  }
}

inline void FreeRegionList::NodeInfo::decrease_length(uint node_index) {
  if (node_index < _num_nodes) {
    assert(_length_of_node[node_index] > 0,
           "Current length %u should be greater than zero for node %u",
           _length_of_node[node_index], node_index);
    _length_of_node[node_index] -= 1;
  }
}

inline uint FreeRegionList::NodeInfo::length(uint node_index) const {
  return _length_of_node[node_index];
}

inline void FreeRegionList::increase_length(uint node_index) {
  if (_node_info != nullptr) {
    return _node_info->increase_length(node_index);
  }
}

inline void FreeRegionList::decrease_length(uint node_index) {
  if (_node_info != nullptr) {
    return _node_info->decrease_length(node_index);
  }
}

inline uint FreeRegionList::length(uint node_index) const {
  if (_node_info != nullptr) {
    return _node_info->length(node_index);
  } else {
    return 0;
  }
}

#endif // SHARE_GC_G1_G1HEAPREGIONSET_INLINE_HPP
