/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_HEAPREGIONREMSET_INLINE_HPP
#define SHARE_VM_GC_G1_HEAPREGIONREMSET_INLINE_HPP

#include "gc/g1/heapRegionRemSet.hpp"

#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/sparsePRT.hpp"
#include "runtime/atomic.hpp"
#include "utilities/bitMap.inline.hpp"

template <class Closure>
inline void HeapRegionRemSet::iterate_prts(Closure& cl) {
  _other_regions.iterate(cl);
}

inline bool PerRegionTable::add_card(CardIdx_t from_card_index) {
  if (_bm.par_set_bit(from_card_index)) {
    Atomic::inc(&_occupied, memory_order_relaxed);
    return true;
  }
  return false;
}

inline bool PerRegionTable::add_reference(OopOrNarrowOopStar from) {
  // Must make this robust in case "from" is not in "_hr", because of
  // concurrency.

  HeapRegion* loc_hr = hr();
  // If the test below fails, then this table was reused concurrently
  // with this operation.  This is OK, since the old table was coarsened,
  // and adding a bit to the new table is never incorrect.
  if (loc_hr->is_in_reserved(from)) {
    CardIdx_t from_card = OtherRegionsTable::card_within_region(from, loc_hr);
    return add_card(from_card);
  }
  return false;
}

inline void PerRegionTable::init(HeapRegion* hr, bool clear_links_to_all_list) {
  if (clear_links_to_all_list) {
    set_next(NULL);
  }
  _collision_list_next = NULL;
  _occupied = 0;
  _bm.clear();
  // Make sure that the bitmap clearing above has been finished before publishing
  // this PRT to concurrent threads.
  Atomic::release_store(&_hr, hr);
}

template <class Closure>
void OtherRegionsTable::iterate(Closure& cl) {
  if (Atomic::load(&_has_coarse_entries)) {
    BitMap::idx_t cur = _coarse_map.get_next_one_offset(0);
    while (cur != _coarse_map.size()) {
      cl.next_coarse_prt((uint)cur);
      cur = _coarse_map.get_next_one_offset(cur + 1);
    }
  }
  {
    PerRegionTable* cur = _first_all_fine_prts;
    while (cur != NULL) {
      cl.next_fine_prt(cur->hr()->hrm_index(), cur->bm());
      cur = cur->next();
    }
  }
  {
    SparsePRTBucketIter iter(&_sparse_table);
    SparsePRTEntry* cur;
    while (iter.has_next(cur)) {
      cl.next_sparse_prt(cur->r_ind(), cur->cards(), cur->num_valid_cards());
    }
  }
}

#endif // SHARE_VM_GC_G1_HEAPREGIONREMSET_INLINE_HPP
