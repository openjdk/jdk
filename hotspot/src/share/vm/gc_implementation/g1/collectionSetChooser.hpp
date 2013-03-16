/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_COLLECTIONSETCHOOSER_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_COLLECTIONSETCHOOSER_HPP

#include "gc_implementation/g1/heapRegion.hpp"
#include "utilities/growableArray.hpp"

class CollectionSetChooser: public CHeapObj<mtGC> {

  GrowableArray<HeapRegion*> _regions;

  // Unfortunately, GrowableArray uses ints for length and indexes. To
  // avoid excessive casting in the rest of the class the following
  // wrapper methods are provided that use uints.

  uint regions_length()          { return (uint) _regions.length(); }
  HeapRegion* regions_at(uint i) { return _regions.at((int) i);     }
  void regions_at_put(uint i, HeapRegion* hr) {
    _regions.at_put((int) i, hr);
  }
  void regions_at_put_grow(uint i, HeapRegion* hr) {
    _regions.at_put_grow((int) i, hr);
  }
  void regions_trunc_to(uint i)  { _regions.trunc_to((uint) i); }

  // The index of the next candidate old region to be considered for
  // addition to the CSet.
  uint _curr_index;

  // The number of candidate old regions added to the CSet chooser.
  // Note: this is not updated when removing a region using
  // remove_and_move_to_next() below.
  uint _length;

  // Keeps track of the start of the next array chunk to be claimed by
  // parallel GC workers.
  uint _first_par_unreserved_idx;

  // If a region has more live bytes than this threshold, it will not
  // be added to the CSet chooser and will not be a candidate for
  // collection.
  size_t _region_live_threshold_bytes;

  // The sum of reclaimable bytes over all the regions in the CSet chooser.
  size_t _remaining_reclaimable_bytes;

public:

  // Return the current candidate region to be considered for
  // collection without removing it from the CSet chooser.
  HeapRegion* peek() {
    HeapRegion* res = NULL;
    if (_curr_index < _length) {
      res = regions_at(_curr_index);
      assert(res != NULL,
             err_msg("Unexpected NULL hr in _regions at index %u",
                     _curr_index));
    }
    return res;
  }

  // Remove the given region from the CSet chooser and move to the
  // next one. The given region should be the current candidate region
  // in the CSet chooser.
  void remove_and_move_to_next(HeapRegion* hr) {
    assert(hr != NULL, "pre-condition");
    assert(_curr_index < _length, "pre-condition");
    assert(regions_at(_curr_index) == hr, "pre-condition");
    regions_at_put(_curr_index, NULL);
    assert(hr->reclaimable_bytes() <= _remaining_reclaimable_bytes,
           err_msg("remaining reclaimable bytes inconsistent "
                   "from region: "SIZE_FORMAT" remaining: "SIZE_FORMAT,
                   hr->reclaimable_bytes(), _remaining_reclaimable_bytes));
    _remaining_reclaimable_bytes -= hr->reclaimable_bytes();
    _curr_index += 1;
  }

  CollectionSetChooser();

  void sort_regions();

  // Determine whether to add the given region to the CSet chooser or
  // not. Currently, we skip humongous regions (we never add them to
  // the CSet, we only reclaim them during cleanup) and regions whose
  // live bytes are over the threshold.
  bool should_add(HeapRegion* hr) {
    assert(hr->is_marked(), "pre-condition");
    assert(!hr->is_young(), "should never consider young regions");
    return !hr->isHumongous() &&
            hr->live_bytes() < _region_live_threshold_bytes;
  }

  // Returns the number candidate old regions added
  uint length() { return _length; }

  // Serial version.
  void add_region(HeapRegion *hr);

  // Must be called before calls to claim_array_chunk().
  // n_regions is the number of regions, chunk_size the chunk size.
  void prepare_for_par_region_addition(uint n_regions, uint chunk_size);
  // Returns the first index in a contiguous chunk of chunk_size indexes
  // that the calling thread has reserved.  These must be set by the
  // calling thread using set_region() (to NULL if necessary).
  uint claim_array_chunk(uint chunk_size);
  // Set the marked array entry at index to hr.  Careful to claim the index
  // first if in parallel.
  void set_region(uint index, HeapRegion* hr);
  // Atomically increment the number of added regions by region_num
  // and the amount of reclaimable bytes by reclaimable_bytes.
  void update_totals(uint region_num, size_t reclaimable_bytes);

  void clear();

  // Return the number of candidate regions that remain to be collected.
  uint remaining_regions() { return _length - _curr_index; }

  // Determine whether the CSet chooser has more candidate regions or not.
  bool is_empty() { return remaining_regions() == 0; }

  // Return the reclaimable bytes that remain to be collected on
  // all the candidate regions in the CSet chooser.
  size_t remaining_reclaimable_bytes() { return _remaining_reclaimable_bytes; }

  // Returns true if the used portion of "_regions" is properly
  // sorted, otherwise asserts false.
  void verify() PRODUCT_RETURN;
};

class CSetChooserParUpdater : public StackObj {
private:
  CollectionSetChooser* _chooser;
  bool _parallel;
  uint _chunk_size;
  uint _cur_chunk_idx;
  uint _cur_chunk_end;
  uint _regions_added;
  size_t _reclaimable_bytes_added;

public:
  CSetChooserParUpdater(CollectionSetChooser* chooser,
                        bool parallel, uint chunk_size) :
    _chooser(chooser), _parallel(parallel), _chunk_size(chunk_size),
    _cur_chunk_idx(0), _cur_chunk_end(0),
    _regions_added(0), _reclaimable_bytes_added(0) { }

  ~CSetChooserParUpdater() {
    if (_parallel && _regions_added > 0) {
      _chooser->update_totals(_regions_added, _reclaimable_bytes_added);
    }
  }

  void add_region(HeapRegion* hr) {
    if (_parallel) {
      if (_cur_chunk_idx == _cur_chunk_end) {
        _cur_chunk_idx = _chooser->claim_array_chunk(_chunk_size);
        _cur_chunk_end = _cur_chunk_idx + _chunk_size;
      }
      assert(_cur_chunk_idx < _cur_chunk_end, "invariant");
      _chooser->set_region(_cur_chunk_idx, hr);
      _cur_chunk_idx += 1;
    } else {
      _chooser->add_region(hr);
    }
    _regions_added += 1;
    _reclaimable_bytes_added += hr->reclaimable_bytes();
  }

  bool should_add(HeapRegion* hr) { return _chooser->should_add(hr); }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_COLLECTIONSETCHOOSER_HPP

