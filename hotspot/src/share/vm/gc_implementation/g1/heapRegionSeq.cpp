/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/heapRegion.hpp"
#include "gc_implementation/g1/heapRegionSeq.inline.hpp"
#include "gc_implementation/g1/heapRegionSets.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "memory/allocation.hpp"

// Private

uint HeapRegionSeq::find_contiguous_from(uint from, uint num) {
  uint len = length();
  assert(num > 1, "use this only for sequences of length 2 or greater");
  assert(from <= len,
         err_msg("from: %u should be valid and <= than %u", from, len));

  uint curr = from;
  uint first = G1_NULL_HRS_INDEX;
  uint num_so_far = 0;
  while (curr < len && num_so_far < num) {
    if (at(curr)->is_empty()) {
      if (first == G1_NULL_HRS_INDEX) {
        first = curr;
        num_so_far = 1;
      } else {
        num_so_far += 1;
      }
    } else {
      first = G1_NULL_HRS_INDEX;
      num_so_far = 0;
    }
    curr += 1;
  }
  assert(num_so_far <= num, "post-condition");
  if (num_so_far == num) {
    // we found enough space for the humongous object
    assert(from <= first && first < len, "post-condition");
    assert(first < curr && (curr - first) == num, "post-condition");
    for (uint i = first; i < first + num; ++i) {
      assert(at(i)->is_empty(), "post-condition");
    }
    return first;
  } else {
    // we failed to find enough space for the humongous object
    return G1_NULL_HRS_INDEX;
  }
}

// Public

void HeapRegionSeq::initialize(HeapWord* bottom, HeapWord* end) {
  assert((uintptr_t) bottom % HeapRegion::GrainBytes == 0,
         "bottom should be heap region aligned");
  assert((uintptr_t) end % HeapRegion::GrainBytes == 0,
         "end should be heap region aligned");

  _next_search_index = 0;
  _allocated_length = 0;

  _regions.initialize(bottom, end, HeapRegion::GrainBytes);
}

MemRegion HeapRegionSeq::expand_by(HeapWord* old_end,
                                   HeapWord* new_end,
                                   FreeRegionList* list) {
  assert(old_end < new_end, "don't call it otherwise");
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  HeapWord* next_bottom = old_end;
  assert(heap_bottom() <= next_bottom, "invariant");
  while (next_bottom < new_end) {
    assert(next_bottom < heap_end(), "invariant");
    uint index = length();

    assert(index < max_length(), "otherwise we cannot expand further");
    if (index == 0) {
      // We have not allocated any regions so far
      assert(next_bottom == heap_bottom(), "invariant");
    } else {
      // next_bottom should match the end of the last/previous region
      assert(next_bottom == at(index - 1)->end(), "invariant");
    }

    if (index == _allocated_length) {
      // We have to allocate a new HeapRegion.
      HeapRegion* new_hr = g1h->new_heap_region(index, next_bottom);
      if (new_hr == NULL) {
        // allocation failed, we bail out and return what we have done so far
        return MemRegion(old_end, next_bottom);
      }
      assert(_regions.get_by_index(index) == NULL, "invariant");
      _regions.set_by_index(index, new_hr);
      increment_allocated_length();
    }
    // Have to increment the length first, otherwise we will get an
    // assert failure at(index) below.
    increment_length();
    HeapRegion* hr = at(index);
    list->add_as_tail(hr);

    next_bottom = hr->end();
  }
  assert(next_bottom == new_end, "post-condition");
  return MemRegion(old_end, next_bottom);
}

uint HeapRegionSeq::free_suffix() {
  uint res = 0;
  uint index = length();
  while (index > 0) {
    index -= 1;
    if (!at(index)->is_empty()) {
      break;
    }
    res += 1;
  }
  return res;
}

uint HeapRegionSeq::find_contiguous(uint num) {
  assert(num > 1, "use this only for sequences of length 2 or greater");
  assert(_next_search_index <= length(),
         err_msg("_next_search_index: %u should be valid and <= than %u",
                 _next_search_index, length()));

  uint start = _next_search_index;
  uint res = find_contiguous_from(start, num);
  if (res == G1_NULL_HRS_INDEX && start > 0) {
    // Try starting from the beginning. If _next_search_index was 0,
    // no point in doing this again.
    res = find_contiguous_from(0, num);
  }
  if (res != G1_NULL_HRS_INDEX) {
    assert(res < length(), err_msg("res: %u should be valid", res));
    _next_search_index = res + num;
    assert(_next_search_index <= length(),
           err_msg("_next_search_index: %u should be valid and <= than %u",
                   _next_search_index, length()));
  }
  return res;
}

void HeapRegionSeq::iterate(HeapRegionClosure* blk) const {
  iterate_from((HeapRegion*) NULL, blk);
}

void HeapRegionSeq::iterate_from(HeapRegion* hr, HeapRegionClosure* blk) const {
  uint hr_index = 0;
  if (hr != NULL) {
    hr_index = hr->hrs_index();
  }

  uint len = length();
  for (uint i = hr_index; i < len; i += 1) {
    bool res = blk->doHeapRegion(at(i));
    if (res) {
      blk->incomplete();
      return;
    }
  }
  for (uint i = 0; i < hr_index; i += 1) {
    bool res = blk->doHeapRegion(at(i));
    if (res) {
      blk->incomplete();
      return;
    }
  }
}

uint HeapRegionSeq::shrink_by(uint num_regions_to_remove) {
  // Reset this in case it's currently pointing into the regions that
  // we just removed.
  _next_search_index = 0;

  assert(length() > 0, "the region sequence should not be empty");
  assert(length() <= _allocated_length, "invariant");
  assert(_allocated_length > 0, "we should have at least one region committed");
  assert(num_regions_to_remove < length(), "We should never remove all regions");

  uint i = 0;
  for (; i < num_regions_to_remove; i++) {
    HeapRegion* cur = at(length() - 1);

    if (!cur->is_empty()) {
      // We have to give up if the region can not be moved
      break;
  }
    assert(!cur->isHumongous(), "Humongous regions should not be empty");

    decrement_length();
  }
  return i;
}

#ifndef PRODUCT
void HeapRegionSeq::verify_optional() {
  guarantee(length() <= _allocated_length,
            err_msg("invariant: _length: %u _allocated_length: %u",
                    length(), _allocated_length));
  guarantee(_allocated_length <= max_length(),
            err_msg("invariant: _allocated_length: %u _max_length: %u",
                    _allocated_length, max_length()));
  guarantee(_next_search_index <= length(),
            err_msg("invariant: _next_search_index: %u _length: %u",
                    _next_search_index, length()));

  HeapWord* prev_end = heap_bottom();
  for (uint i = 0; i < _allocated_length; i += 1) {
    HeapRegion* hr = _regions.get_by_index(i);
    guarantee(hr != NULL, err_msg("invariant: i: %u", i));
    guarantee(hr->bottom() == prev_end,
              err_msg("invariant i: %u "HR_FORMAT" prev_end: "PTR_FORMAT,
                      i, HR_FORMAT_PARAMS(hr), prev_end));
    guarantee(hr->hrs_index() == i,
              err_msg("invariant: i: %u hrs_index(): %u", i, hr->hrs_index()));
    if (i < length()) {
      // Asserts will fire if i is >= _length
      HeapWord* addr = hr->bottom();
      guarantee(addr_to_region(addr) == hr, "sanity");
      guarantee(addr_to_region_unsafe(addr) == hr, "sanity");
    } else {
      guarantee(hr->is_empty(), "sanity");
      guarantee(!hr->isHumongous(), "sanity");
      // using assert instead of guarantee here since containing_set()
      // is only available in non-product builds.
      assert(hr->containing_set() == NULL, "sanity");
    }
    if (hr->startsHumongous()) {
      prev_end = hr->orig_end();
    } else {
      prev_end = hr->end();
    }
  }
  for (uint i = _allocated_length; i < max_length(); i += 1) {
    guarantee(_regions.get_by_index(i) == NULL, err_msg("invariant i: %u", i));
  }
}
#endif // PRODUCT
