/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/g1/g1CollectionSetCandidates.hpp"
#include "gc/g1/g1CollectionSetChooser.hpp"
#include "gc/g1/heapRegion.inline.hpp"

HeapRegion* G1CollectionSetCandidates::pop_front() {
  assert(_front_idx < _num_regions, "pre-condition");
  HeapRegion* hr = _regions[_front_idx];
  assert(hr != NULL, "pre-condition");
  _regions[_front_idx] = NULL;
  assert(hr->reclaimable_bytes() <= _remaining_reclaimable_bytes,
         "Remaining reclaimable bytes inconsistent "
         "from region: " SIZE_FORMAT " remaining: " SIZE_FORMAT,
         hr->reclaimable_bytes(), _remaining_reclaimable_bytes);
  _remaining_reclaimable_bytes -= hr->reclaimable_bytes();
  _front_idx++;
  return hr;
}

void G1CollectionSetCandidates::push_front(HeapRegion* hr) {
  assert(hr != NULL, "Can't put back a NULL region");
  assert(_front_idx >= 1, "Too many regions have been put back.");
  _front_idx--;
  _regions[_front_idx] = hr;
  _remaining_reclaimable_bytes += hr->reclaimable_bytes();
}

void G1CollectionSetCandidates::iterate(HeapRegionClosure* cl) {
  for (uint i = _front_idx; i < _num_regions; i++) {
    HeapRegion* r = _regions[i];
    if (cl->do_heap_region(r)) {
      cl->set_incomplete();
      break;
    }
  }
}

#ifndef PRODUCT
void G1CollectionSetCandidates::verify() const {
  guarantee(_front_idx <= _num_regions, "Index: %u Num_regions: %u", _front_idx, _num_regions);
  uint idx = 0;
  size_t sum_of_reclaimable_bytes = 0;
  while (idx < _front_idx) {
    guarantee(_regions[idx] == NULL, "All entries before _front_idx %u should be NULL, but %u is not",
              _front_idx, idx);
    idx++;
  }
  HeapRegion *prev = NULL;
  for (; idx < _num_regions; idx++) {
    HeapRegion *cur = _regions[idx];
    guarantee(cur != NULL, "Regions after _front_idx %u cannot be NULL but %u is", _front_idx, idx);
    guarantee(G1CollectionSetChooser::should_add(cur), "Region %u should be eligible for addition.", cur->hrm_index());
    if (prev != NULL) {
      guarantee(prev->gc_efficiency() >= cur->gc_efficiency(),
                "GC efficiency for region %u: %1.4f smaller than for region %u: %1.4f",
                prev->hrm_index(), prev->gc_efficiency(), cur->hrm_index(), cur->gc_efficiency());
    }
    sum_of_reclaimable_bytes += cur->reclaimable_bytes();
    prev = cur;
  }
  guarantee(sum_of_reclaimable_bytes == _remaining_reclaimable_bytes,
            "Inconsistent remaining_reclaimable bytes, remaining " SIZE_FORMAT " calculated " SIZE_FORMAT,
            _remaining_reclaimable_bytes, sum_of_reclaimable_bytes);
}
#endif // !PRODUCT
