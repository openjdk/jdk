/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1CARDLIVEDATA_HPP
#define SHARE_VM_GC_G1_G1CARDLIVEDATA_HPP

#include "gc/g1/g1CollectedHeap.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/globalDefinitions.hpp"

class G1CollectedHeap;
class G1CMBitMap;
class WorkGang;

// Information about object liveness on the Java heap on a "card" basis.
// Can be used for various purposes, like as remembered set for completely
// coarsened remembered sets, scrubbing remembered sets or estimating liveness.
// This information is created as part of the concurrent marking cycle.
class G1CardLiveData VALUE_OBJ_CLASS_SPEC {
  friend class G1CardLiveDataHelper;
  friend class G1VerifyCardLiveDataTask;
private:
  typedef BitMap::bm_word_t bm_word_t;
  // Store some additional information about the covered area to be able to test.
  size_t _max_capacity;
  size_t _cards_per_region;

  // Regions may be reclaimed while concurrently creating live data (e.g. due to humongous
  // eager reclaim). This results in wrong live data for these regions at the end.
  // So we need to somehow detect these regions, and during live data finalization completely
  // recreate their information.
  // This _gc_timestamp_at_create tracks the global timestamp when live data creation
  // has started. Any regions with a higher time stamp have been cleared after that
  // point in time, and need re-finalization.
  // Unsynchronized access to this variable is okay, since this value is only set during a
  // concurrent phase, and read only at the Cleanup safepoint. I.e. there is always
  // full memory synchronization inbetween.
  uint _gc_timestamp_at_create;
  // The per-card liveness bitmap.
  bm_word_t* _live_cards;
  size_t _live_cards_size_in_bits;
  // The per-region liveness bitmap.
  bm_word_t* _live_regions;
  size_t _live_regions_size_in_bits;
  // The bits in this bitmap contain for every card whether it contains
  // at least part of at least one live object.
  BitMapView live_cards_bm() const { return BitMapView(_live_cards, _live_cards_size_in_bits); }
  // The bits in this bitmap indicate that a given region contains some live objects.
  BitMapView live_regions_bm() const { return BitMapView(_live_regions, _live_regions_size_in_bits); }

  // Allocate a "large" bitmap from virtual memory with the given size in bits.
  bm_word_t* allocate_large_bitmap(size_t size_in_bits);
  void free_large_bitmap(bm_word_t* map, size_t size_in_bits);

  inline BitMapView live_card_bitmap(uint region);

  inline bool is_card_live_at(BitMap::idx_t idx) const;

  size_t live_region_bitmap_size_in_bits() const;
  size_t live_card_bitmap_size_in_bits() const;
public:
  uint gc_timestamp_at_create() const { return _gc_timestamp_at_create; }

  inline bool is_region_live(uint region) const;

  inline void remove_nonlive_cards(uint region, BitMap* bm);
  inline void remove_nonlive_regions(BitMap* bm);

  G1CardLiveData();
  ~G1CardLiveData();

  void initialize(size_t max_capacity, uint num_max_regions);
  void pretouch();

  // Create the initial liveness data based on the marking result from the bottom
  // to the ntams of every region in the heap and the marks in the given bitmap.
  void create(WorkGang* workers, G1CMBitMap* mark_bitmap);
  // Finalize the liveness data.
  void finalize(WorkGang* workers, G1CMBitMap* mark_bitmap);

  // Verify that the liveness count data created concurrently matches one created
  // during this safepoint.
  void verify(WorkGang* workers, G1CMBitMap* actual_bitmap);
  // Clear all data structures, prepare for next processing.
  void clear(WorkGang* workers);

  void verify_is_clear() PRODUCT_RETURN;
};

#endif /* SHARE_VM_GC_G1_G1CARDLIVEDATA_HPP */

