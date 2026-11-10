/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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

inline void G1HeapRegionSetBase::add(G1HeapRegion* hr) {
  check_mt_safety();
  assert_heap_region_set(hr->containing_set() == nullptr, "should not already have a containing set");
  assert_heap_region_set(hr->next() == nullptr, "should not already be linked");
  assert_heap_region_set(hr->prev() == nullptr, "should not already be linked");

  _num_regions++;
  hr->set_containing_set(this);
  verify_region(hr);
}

inline void G1HeapRegionSetBase::remove(G1HeapRegion* hr) {
  check_mt_safety();
  verify_region(hr);
  assert_heap_region_set(hr->next() == nullptr, "should already be unlinked");
  assert_heap_region_set(hr->prev() == nullptr, "should already be unlinked");

  hr->set_containing_set(nullptr);
  assert_heap_region_set(_num_regions > 0, "pre-condition");
  _num_regions--;
}

inline void G1FreeRegionList::add_to_tail(G1HeapRegion* region_to_add) {
  assert_free_region_list((num_regions() == 0 && _head == nullptr && _tail == nullptr && _last == nullptr) ||
                          (num_regions() >  0 && _head != nullptr && _tail != nullptr && _tail->hrm_index() < region_to_add->hrm_index()),
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
  increase_num_regions(region_to_add->node_index());
}

inline void G1FreeRegionList::add_ordered(G1HeapRegion* hr) {
  assert_free_region_list((num_regions() == 0 && _head == nullptr && _tail == nullptr && _last == nullptr) ||
                          (num_regions() >  0 && _head != nullptr && _tail != nullptr),
                          "invariant");
  // add() will verify the region and check mt safety.
  add(hr);

  // Now link the region
  if (_head != nullptr) {
    G1HeapRegion* curr;

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

  increase_num_regions(hr->node_index());
}

inline void G1FreeRegionList::unlink_from_list(G1HeapRegion* r) {
  precond(r != nullptr);
  // Splice the region out of the list.
  G1HeapRegion* prev = r->prev();
  G1HeapRegion* next = r->next();
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
  r->set_prev(nullptr);
  r->set_next(nullptr);
}

inline void G1FreeRegionList::remove_from_list(G1HeapRegion* r) {
  precond(r != nullptr);

  unlink_from_list(r);
  if (_last == r) {
    _last = nullptr;
  }

  remove(r);
  decrease_num_regions(r->node_index());
}

inline G1HeapRegion* G1FreeRegionList::find_region_to_remove(bool from_head, uint requested_node_index) {
  G1HeapRegion* cur = nullptr;

  if (G1NUMA::numa()->is_enabled() && requested_node_index != G1NUMA::AnyNodeIndex) {
    const uint max_search_depth = G1NUMA::numa()->max_search_depth();
    // Find the region to use, searching from _head or _tail as requested.
    uint cur_depth = 0;
    if (from_head) {
      for (cur = _head;
          cur != nullptr && cur_depth < max_search_depth;
          cur = cur->next(), ++cur_depth) {
        if (requested_node_index == cur->node_index()) {
          return cur;
        }
      }
    } else {
      for (cur = _tail;
          cur != nullptr && cur_depth < max_search_depth;
          cur = cur->prev(), ++cur_depth) {
        if (requested_node_index == cur->node_index()) {
          return cur;
        }
      }
    }
  }
  // Either we could not find a region on the correct NUMA node within the search depth, or
  // we do not care about the node placement. In either case take the next region from the
  // requested end of the list.
  cur = from_head ? _head : _tail;
  return cur;
}

inline G1HeapRegion* G1FreeRegionList::remove_region(bool from_head, uint requested_node_index) {
  check_mt_safety();
  verify_optional();

  if (is_empty()) {
    return nullptr;
  }
  assert_free_region_list(num_regions() > 0 && _head != nullptr && _tail != nullptr, "invariant");

  G1HeapRegion* r = find_region_to_remove(from_head, requested_node_index);
  remove_from_list(r);
  return r;
}

inline void G1FreeRegionList::NodeInfo::increase_num_regions(uint node_index) {
  if (node_index < _num_nodes) {
    _num_regions_on_node[node_index] += 1;
  }
}

inline void G1FreeRegionList::NodeInfo::decrease_num_regions(uint node_index) {
  if (node_index < _num_nodes) {
    assert(_num_regions_on_node[node_index] > 0,
           "Current num_regions %u should be greater than zero for node %u",
           _num_regions_on_node[node_index], node_index);
    _num_regions_on_node[node_index] -= 1;
  }
}

inline uint G1FreeRegionList::NodeInfo::num_regions_on_node(uint node_index) const {
  return _num_regions_on_node[node_index];
}

inline void G1FreeRegionList::increase_num_regions(uint node_index) {
  if (_node_info != nullptr) {
    return _node_info->increase_num_regions(node_index);
  }
}

inline void G1FreeRegionList::decrease_num_regions(uint node_index) {
  if (_node_info != nullptr) {
    return _node_info->decrease_num_regions(node_index);
  }
}

inline uint G1FreeRegionList::num_regions_on_node(uint node_index) const {
  if (_node_info != nullptr) {
    return _node_info->num_regions_on_node(node_index);
  } else {
    return 0;
  }
}

#endif // SHARE_GC_G1_G1HEAPREGIONSET_INLINE_HPP
