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

#include "precompiled.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1CardLiveData.inline.hpp"
#include "gc/g1/suspendibleThreadSet.hpp"
#include "gc/shared/workgroup.hpp"
#include "memory/universe.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/debug.hpp"

G1CardLiveData::G1CardLiveData() :
  _max_capacity(0),
  _cards_per_region(0),
  _live_regions(NULL),
  _live_regions_size_in_bits(0),
  _live_cards(NULL),
  _live_cards_size_in_bits(0) {
}

G1CardLiveData::~G1CardLiveData()  {
  free_large_bitmap(_live_cards, _live_cards_size_in_bits);
  free_large_bitmap(_live_regions, _live_regions_size_in_bits);
}

G1CardLiveData::bm_word_t* G1CardLiveData::allocate_large_bitmap(size_t size_in_bits) {
  size_t size_in_words = BitMap::calc_size_in_words(size_in_bits);

  bm_word_t* map = MmapArrayAllocator<bm_word_t, mtGC>::allocate(size_in_words);

  return map;
}

void G1CardLiveData::free_large_bitmap(bm_word_t* bitmap, size_t size_in_bits) {
  MmapArrayAllocator<bm_word_t, mtGC>::free(bitmap, size_in_bits / BitsPerWord);
}

void G1CardLiveData::initialize(size_t max_capacity, uint num_max_regions) {
  assert(max_capacity % num_max_regions == 0,
         "Given capacity must be evenly divisible by region size.");
  size_t region_size = max_capacity / num_max_regions;
  assert(region_size % (G1SATBCardTableModRefBS::card_size * BitsPerWord) == 0,
         "Region size must be evenly divisible by area covered by a single word.");
  _max_capacity = max_capacity;
  _cards_per_region = region_size / G1SATBCardTableModRefBS::card_size;

  _live_regions_size_in_bits = live_region_bitmap_size_in_bits();
  _live_regions = allocate_large_bitmap(_live_regions_size_in_bits);
  _live_cards_size_in_bits = live_card_bitmap_size_in_bits();
  _live_cards = allocate_large_bitmap(_live_cards_size_in_bits);
}

void G1CardLiveData::pretouch() {
  live_cards_bm().pretouch();
  live_regions_bm().pretouch();
}

size_t G1CardLiveData::live_region_bitmap_size_in_bits() const {
  return _max_capacity / (_cards_per_region << G1SATBCardTableModRefBS::card_shift);
}

size_t G1CardLiveData::live_card_bitmap_size_in_bits() const {
  return _max_capacity >> G1SATBCardTableModRefBS::card_shift;
}

// Helper class that provides functionality to generate the Live Data Count
// information.
class G1CardLiveDataHelper VALUE_OBJ_CLASS_SPEC {
private:
  BitMap _region_bm;
  BitMap _card_bm;

  // The card number of the bottom of the G1 heap.
  // Used in biasing indices into accounting card bitmaps.
  BitMap::idx_t _heap_card_bias;

  // Utility routine to set an exclusive range of bits on the given
  // bitmap, optimized for very small ranges.
  // There must be at least one bit to set.
  void set_card_bitmap_range(BitMap::idx_t start_idx,
                             BitMap::idx_t end_idx) {

    // Set the exclusive bit range [start_idx, end_idx).
    assert((end_idx - start_idx) > 0, "at least one bit");

    // For small ranges use a simple loop; otherwise use set_range.
    // The range is made up of the cards that are spanned by an object/mem
    // region so 8 cards will allow up to object sizes up to 4K to be handled
    // using the loop.
    if ((end_idx - start_idx) <= 8) {
      for (BitMap::idx_t i = start_idx; i < end_idx; i += 1) {
        _card_bm.set_bit(i);
      }
    } else {
      _card_bm.set_range(start_idx, end_idx);
    }
  }

  // We cache the last mark set. This avoids setting the same bit multiple times.
  // This is particularly interesting for dense bitmaps, as this avoids doing
  // lots of work most of the time.
  BitMap::idx_t _last_marked_bit_idx;

  // Mark the card liveness bitmap for the object spanning from start to end.
  void mark_card_bitmap_range(HeapWord* start, HeapWord* end) {
    BitMap::idx_t start_idx = card_live_bitmap_index_for(start);
    BitMap::idx_t end_idx = card_live_bitmap_index_for((HeapWord*)align_ptr_up(end, CardTableModRefBS::card_size));

    assert((end_idx - start_idx) > 0, "Trying to mark zero sized range.");

    if (start_idx == _last_marked_bit_idx) {
      start_idx++;
    }
    if (start_idx == end_idx) {
      return;
    }

    // Set the bits in the card bitmap for the cards spanned by this object.
    set_card_bitmap_range(start_idx, end_idx);
    _last_marked_bit_idx = end_idx - 1;
  }

  void reset_mark_cache() {
    _last_marked_bit_idx = (BitMap::idx_t)-1;
  }

public:
  // Returns the index in the per-card liveness count bitmap
  // for the given address
  inline BitMap::idx_t card_live_bitmap_index_for(HeapWord* addr) {
    // Below, the term "card num" means the result of shifting an address
    // by the card shift -- address 0 corresponds to card number 0.  One
    // must subtract the card num of the bottom of the heap to obtain a
    // card table index.
    BitMap::idx_t card_num = uintptr_t(addr) >> CardTableModRefBS::card_shift;
    return card_num - _heap_card_bias;
  }

  // Takes a region that's not empty (i.e., it has at least one
  // live object in it and sets its corresponding bit on the region
  // bitmap to 1.
  void set_bit_for_region(HeapRegion* hr) {
    _region_bm.par_set_bit(hr->hrm_index());
  }

  // Mark the range of bits covered by allocations done since the last marking
  // in the given heap region, i.e. from NTAMS to top of the given region.
  // Returns if there has been some allocation in this region since the last marking.
  bool mark_allocated_since_marking(HeapRegion* hr) {
    reset_mark_cache();

    HeapWord* ntams = hr->next_top_at_mark_start();
    HeapWord* top   = hr->top();

    assert(hr->bottom() <= ntams && ntams <= hr->end(), "Preconditions.");

    // Mark the allocated-since-marking portion...
    if (ntams < top) {
      mark_card_bitmap_range(ntams, top);
      return true;
    } else {
      return false;
    }
  }

  // Mark the range of bits covered by live objects on the mark bitmap between
  // bottom and NTAMS of the given region.
  // Returns the number of live bytes marked within that area for the given
  // heap region.
  size_t mark_marked_during_marking(G1CMBitMap* mark_bitmap, HeapRegion* hr) {
    reset_mark_cache();

    size_t marked_bytes = 0;

    HeapWord* ntams = hr->next_top_at_mark_start();
    HeapWord* start = hr->bottom();

    if (ntams <= start) {
      // Skip empty regions.
      return 0;
    }
    if (hr->is_humongous()) {
      mark_card_bitmap_range(start, hr->top());
      return pointer_delta(hr->top(), start, 1);
    }

    assert(start <= hr->end() && start <= ntams && ntams <= hr->end(),
           "Preconditions not met - "
           "start: " PTR_FORMAT ", ntams: " PTR_FORMAT ", end: " PTR_FORMAT,
           p2i(start), p2i(ntams), p2i(hr->end()));

    // Find the first marked object at or after "start".
    start = mark_bitmap->getNextMarkedWordAddress(start, ntams);
    while (start < ntams) {
      oop obj = oop(start);
      size_t obj_size = obj->size();
      HeapWord* obj_end = start + obj_size;

      assert(obj_end <= hr->end(), "Humongous objects must have been handled elsewhere.");

      mark_card_bitmap_range(start, obj_end);

      // Add the size of this object to the number of marked bytes.
      marked_bytes += obj_size * HeapWordSize;

      // Find the next marked object after this one.
      start = mark_bitmap->getNextMarkedWordAddress(obj_end, ntams);
    }

    return marked_bytes;
  }

  G1CardLiveDataHelper(G1CardLiveData* live_data, HeapWord* base_address) :
    _region_bm(live_data->live_regions_bm()),
    _card_bm(live_data->live_cards_bm()) {
    // Calculate the card number for the bottom of the heap. Used
    // in biasing indexes into the accounting card bitmaps.
    _heap_card_bias =
      uintptr_t(base_address) >> CardTableModRefBS::card_shift;
  }
};

class G1CreateCardLiveDataTask: public AbstractGangTask {
  // Aggregate the counting data that was constructed concurrently
  // with marking.
  class G1CreateLiveDataClosure : public HeapRegionClosure {
    G1CardLiveDataHelper _helper;

    G1CMBitMap* _mark_bitmap;

    G1ConcurrentMark* _cm;
  public:
    G1CreateLiveDataClosure(G1CollectedHeap* g1h,
                            G1ConcurrentMark* cm,
                            G1CMBitMap* mark_bitmap,
                            G1CardLiveData* live_data) :
      HeapRegionClosure(),
      _helper(live_data, g1h->reserved_region().start()),
      _mark_bitmap(mark_bitmap),
      _cm(cm) { }

    bool doHeapRegion(HeapRegion* hr) {
      size_t marked_bytes = _helper.mark_marked_during_marking(_mark_bitmap, hr);
      if (marked_bytes > 0) {
        hr->add_to_marked_bytes(marked_bytes);
      }

      return (_cm->do_yield_check() && _cm->has_aborted());
    }
  };

  G1ConcurrentMark* _cm;
  G1CardLiveData* _live_data;
  HeapRegionClaimer _hr_claimer;

public:
  G1CreateCardLiveDataTask(G1CMBitMap* bitmap,
                           G1CardLiveData* live_data,
                           uint n_workers) :
      AbstractGangTask("G1 Create Live Data"),
      _live_data(live_data),
      _hr_claimer(n_workers) {
  }

  void work(uint worker_id) {
    SuspendibleThreadSetJoiner sts_join;

    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    G1ConcurrentMark* cm = g1h->concurrent_mark();
    G1CreateLiveDataClosure cl(g1h, cm, cm->nextMarkBitMap(), _live_data);
    g1h->heap_region_par_iterate(&cl, worker_id, &_hr_claimer);
  }
};

void G1CardLiveData::create(WorkGang* workers, G1CMBitMap* mark_bitmap) {
  uint n_workers = workers->active_workers();

  G1CreateCardLiveDataTask cl(mark_bitmap,
                              this,
                              n_workers);
  workers->run_task(&cl);
}

class G1FinalizeCardLiveDataTask: public AbstractGangTask {
  // Finalizes the liveness counting data.
  // Sets the bits corresponding to the interval [NTAMS, top]
  // (which contains the implicitly live objects) in the
  // card liveness bitmap. Also sets the bit for each region
  // containing live data, in the region liveness bitmap.
  class G1FinalizeCardLiveDataClosure: public HeapRegionClosure {
  private:
    G1CardLiveDataHelper _helper;
  public:
    G1FinalizeCardLiveDataClosure(G1CollectedHeap* g1h,
                                  G1CMBitMap* bitmap,
                                  G1CardLiveData* live_data) :
      HeapRegionClosure(),
      _helper(live_data, g1h->reserved_region().start()) { }

    bool doHeapRegion(HeapRegion* hr) {
      bool allocated_since_marking = _helper.mark_allocated_since_marking(hr);
      if (allocated_since_marking || hr->next_marked_bytes() > 0) {
        _helper.set_bit_for_region(hr);
      }
      return false;
    }
  };

  G1CMBitMap* _bitmap;

  G1CardLiveData* _live_data;

  HeapRegionClaimer _hr_claimer;

public:
  G1FinalizeCardLiveDataTask(G1CMBitMap* bitmap, G1CardLiveData* live_data, uint n_workers) :
    AbstractGangTask("G1 Finalize Card Live Data"),
    _bitmap(bitmap),
    _live_data(live_data),
    _hr_claimer(n_workers) {
  }

  void work(uint worker_id) {
    G1FinalizeCardLiveDataClosure cl(G1CollectedHeap::heap(), _bitmap, _live_data);

    G1CollectedHeap::heap()->heap_region_par_iterate(&cl, worker_id, &_hr_claimer);
  }
};

void G1CardLiveData::finalize(WorkGang* workers, G1CMBitMap* mark_bitmap) {
  // Finalize the live data.
  G1FinalizeCardLiveDataTask cl(mark_bitmap,
                                this,
                                workers->active_workers());
  workers->run_task(&cl);
}

class G1ClearCardLiveDataTask : public AbstractGangTask {
  BitMap _bitmap;
  size_t _num_chunks;
  size_t _cur_chunk;
public:
  G1ClearCardLiveDataTask(BitMap bitmap, size_t num_tasks) :
    AbstractGangTask("G1 Clear Card Live Data"),
    _bitmap(bitmap),
    _num_chunks(num_tasks),
    _cur_chunk(0) {
  }

  static size_t chunk_size() { return M; }

  virtual void work(uint worker_id) {
    while (true) {
      size_t to_process = Atomic::add(1, &_cur_chunk) - 1;
      if (to_process >= _num_chunks) {
        break;
      }

      BitMap::idx_t start = M * BitsPerByte * to_process;
      BitMap::idx_t end = MIN2(start + M * BitsPerByte, _bitmap.size());
      _bitmap.clear_range(start, end);
    }
  }
};

void G1CardLiveData::clear(WorkGang* workers) {
  guarantee(Universe::is_fully_initialized(), "Should not call this during initialization.");

  size_t const num_chunks = align_size_up(live_cards_bm().size_in_bytes(), G1ClearCardLiveDataTask::chunk_size()) / G1ClearCardLiveDataTask::chunk_size();

  G1ClearCardLiveDataTask cl(live_cards_bm(), num_chunks);
  workers->run_task(&cl);

  // The region live bitmap is always very small, even for huge heaps. Clear
  // directly.
  live_regions_bm().clear();
}

class G1VerifyCardLiveDataTask: public AbstractGangTask {
  // Heap region closure used for verifying the live count data
  // that was created concurrently and finalized during
  // the remark pause. This closure is applied to the heap
  // regions during the STW cleanup pause.
  class G1VerifyCardLiveDataClosure: public HeapRegionClosure {
  private:
    G1CollectedHeap* _g1h;
    G1CMBitMap* _mark_bitmap;
    G1CardLiveDataHelper _helper;

    G1CardLiveData* _act_live_data;

    G1CardLiveData* _exp_live_data;

    int _failures;

    // Completely recreates the live data count for the given heap region and
    // returns the number of bytes marked.
    size_t create_live_data_count(HeapRegion* hr) {
      size_t bytes_marked = _helper.mark_marked_during_marking(_mark_bitmap, hr);
      bool allocated_since_marking = _helper.mark_allocated_since_marking(hr);
      if (allocated_since_marking || bytes_marked > 0) {
        _helper.set_bit_for_region(hr);
      }
      return bytes_marked;
    }
  public:
    G1VerifyCardLiveDataClosure(G1CollectedHeap* g1h,
                                G1CMBitMap* mark_bitmap,
                                G1CardLiveData* act_live_data,
                                G1CardLiveData* exp_live_data) :
      _g1h(g1h),
      _mark_bitmap(mark_bitmap),
      _helper(exp_live_data, g1h->reserved_region().start()),
      _act_live_data(act_live_data),
      _exp_live_data(exp_live_data),
      _failures(0) { }

    int failures() const { return _failures; }

    bool doHeapRegion(HeapRegion* hr) {
      int failures = 0;

      // Walk the marking bitmap for this region and set the corresponding bits
      // in the expected region and card bitmaps.
      size_t exp_marked_bytes = create_live_data_count(hr);
      size_t act_marked_bytes = hr->next_marked_bytes();
      // Verify the marked bytes for this region.

      if (exp_marked_bytes != act_marked_bytes) {
        failures += 1;
      } else if (exp_marked_bytes > HeapRegion::GrainBytes) {
        failures += 1;
      }

      // Verify the bit, for this region, in the actual and expected
      // (which was just calculated) region bit maps.
      // We're not OK if the bit in the calculated expected region
      // bitmap is set and the bit in the actual region bitmap is not.
      uint index = hr->hrm_index();

      bool expected = _exp_live_data->is_region_live(index);
      bool actual = _act_live_data->is_region_live(index);
      if (expected && !actual) {
        failures += 1;
      }

      // Verify that the card bit maps for the cards spanned by the current
      // region match. We have an error if we have a set bit in the expected
      // bit map and the corresponding bit in the actual bitmap is not set.

      BitMap::idx_t start_idx = _helper.card_live_bitmap_index_for(hr->bottom());
      BitMap::idx_t end_idx = _helper.card_live_bitmap_index_for(hr->top());

      for (BitMap::idx_t i = start_idx; i < end_idx; i+=1) {
        expected = _exp_live_data->is_card_live_at(i);
        actual = _act_live_data->is_card_live_at(i);

        if (expected && !actual) {
          failures += 1;
        }
      }

      _failures += failures;

      // We could stop iteration over the heap when we
      // find the first violating region by returning true.
      return false;
    }
  };
protected:
  G1CollectedHeap* _g1h;
  G1CMBitMap* _mark_bitmap;

  G1CardLiveData* _act_live_data;

  G1CardLiveData _exp_live_data;

  int  _failures;

  HeapRegionClaimer _hr_claimer;

public:
  G1VerifyCardLiveDataTask(G1CMBitMap* bitmap,
                           G1CardLiveData* act_live_data,
                           uint n_workers)
  : AbstractGangTask("G1 Verify Card Live Data"),
    _g1h(G1CollectedHeap::heap()),
    _mark_bitmap(bitmap),
    _act_live_data(act_live_data),
    _exp_live_data(),
    _failures(0),
    _hr_claimer(n_workers) {
    assert(VerifyDuringGC, "don't call this otherwise");
    _exp_live_data.initialize(_g1h->max_capacity(), _g1h->max_regions());
  }

  void work(uint worker_id) {
    G1VerifyCardLiveDataClosure cl(_g1h,
                                   _mark_bitmap,
                                   _act_live_data,
                                   &_exp_live_data);
    _g1h->heap_region_par_iterate(&cl, worker_id, &_hr_claimer);

    Atomic::add(cl.failures(), &_failures);
  }

  int failures() const { return _failures; }
};

void G1CardLiveData::verify(WorkGang* workers, G1CMBitMap* actual_bitmap) {
    ResourceMark rm;

    G1VerifyCardLiveDataTask cl(actual_bitmap,
                                this,
                                workers->active_workers());
    workers->run_task(&cl);

    guarantee(cl.failures() == 0, "Unexpected accounting failures");
}

#ifndef PRODUCT
void G1CardLiveData::verify_is_clear() {
  assert(live_cards_bm().count_one_bits() == 0, "Live cards bitmap must be clear.");
  assert(live_regions_bm().count_one_bits() == 0, "Live regions bitmap must be clear.");
}
#endif
