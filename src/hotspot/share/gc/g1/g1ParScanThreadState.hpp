/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1PARSCANTHREADSTATE_HPP
#define SHARE_GC_G1_G1PARSCANTHREADSTATE_HPP

#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1RedirtyCardsQueue.hpp"
#include "gc/g1/g1OopClosures.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/ageTable.hpp"
#include "memory/allocation.hpp"
#include "oops/oop.hpp"
#include "utilities/ticks.hpp"

class G1OopStarChunkedList;
class G1PLABAllocator;
class G1EvacuationRootClosures;
class HeapRegion;
class outputStream;

class G1ParScanThreadState : public CHeapObj<mtGC> {
  G1CollectedHeap* _g1h;
  RefToScanQueue* _refs;
  G1RedirtyCardsQueue _rdcq;
  G1CardTable* _ct;
  G1EvacuationRootClosures* _closures;

  G1PLABAllocator* _plab_allocator;

  AgeTable _age_table;
  G1HeapRegionAttr _dest[G1HeapRegionAttr::Num];
  // Local tenuring threshold.
  uint _tenuring_threshold;
  G1ScanEvacuatedObjClosure  _scanner;

  uint _worker_id;

  // Remember the last enqueued card to avoid enqueuing the same card over and over;
  // since we only ever scan a card once, this is sufficient.
  size_t _last_enqueued_card;

  // Upper and lower threshold to start and end work queue draining.
  uint const _stack_trim_upper_threshold;
  uint const _stack_trim_lower_threshold;

  Tickspan _trim_ticks;
  // Map from young-age-index (0 == not young, 1 is youngest) to
  // surviving words. base is what we get back from the malloc call
  size_t* _surviving_young_words_base;
  // this points into the array, as we use the first few entries for padding
  size_t* _surviving_young_words;
  // Number of elements in the array above.
  size_t _surviving_words_length;
  // Indicates whether in the last generation (old) there is no more space
  // available for allocation.
  bool _old_gen_is_full;

#define PADDING_ELEM_NUM (DEFAULT_CACHE_LINE_SIZE / sizeof(size_t))

  G1RedirtyCardsQueue& redirty_cards_queue()     { return _rdcq; }
  G1CardTable* ct()                              { return _ct; }

  G1HeapRegionAttr dest(G1HeapRegionAttr original) const {
    assert(original.is_valid(),
           "Original region attr invalid: %s", original.get_type_str());
    assert(_dest[original.type()].is_valid_gen(),
           "Dest region attr is invalid: %s", _dest[original.type()].get_type_str());
    return _dest[original.type()];
  }

  size_t _num_optional_regions;
  G1OopStarChunkedList* _oops_into_optional_regions;

  G1NUMA* _numa;

  // Records how many object allocations happened at each node during copy to survivor.
  // Only starts recording when log of gc+heap+numa is enabled and its data is
  // transferred when flushed.
  size_t* _obj_alloc_stat;

public:
  G1ParScanThreadState(G1CollectedHeap* g1h,
                       G1RedirtyCardsQueueSet* rdcqs,
                       uint worker_id,
                       size_t young_cset_length,
                       size_t optional_cset_length);
  virtual ~G1ParScanThreadState();

  void set_ref_discoverer(ReferenceDiscoverer* rd) { _scanner.set_ref_discoverer(rd); }

#ifdef ASSERT
  bool queue_is_empty() const { return _refs->is_empty(); }

  bool verify_ref(narrowOop* ref) const;
  bool verify_ref(oop* ref) const;
  bool verify_task(StarTask ref) const;
#endif // ASSERT

  template <class T> void do_oop_ext(T* ref);
  template <class T> void push_on_queue(T* ref);

  template <class T> void enqueue_card_if_tracked(G1HeapRegionAttr region_attr, T* p, oop o) {
    assert(!HeapRegion::is_in_same_region(p, o), "Should have filtered out cross-region references already.");
    assert(!_g1h->heap_region_containing(p)->is_young(), "Should have filtered out from-young references already.");

#ifdef ASSERT
    HeapRegion* const hr_obj = _g1h->heap_region_containing((HeapWord*)o);
    assert(region_attr.needs_remset_update() == hr_obj->rem_set()->is_tracked(),
           "State flag indicating remset tracking disagrees (%s) with actual remembered set (%s) for region %u",
           BOOL_TO_STR(region_attr.needs_remset_update()),
           BOOL_TO_STR(hr_obj->rem_set()->is_tracked()),
           hr_obj->hrm_index());
#endif
    if (!region_attr.needs_remset_update()) {
      return;
    }
    size_t card_index = ct()->index_for(p);
    // If the card hasn't been added to the buffer, do it.
    if (_last_enqueued_card != card_index) {
      redirty_cards_queue().enqueue(ct()->byte_for_index(card_index));
      _last_enqueued_card = card_index;
    }
  }

  G1EvacuationRootClosures* closures() { return _closures; }
  uint worker_id() { return _worker_id; }

  size_t lab_waste_words() const;
  size_t lab_undo_waste_words() const;

  // Pass locally gathered statistics to global state. Returns the total number of
  // HeapWords copied.
  size_t flush(size_t* surviving_young_words);

private:
  #define G1_PARTIAL_ARRAY_MASK 0x2

  inline bool has_partial_array_mask(oop* ref) const {
    return ((uintptr_t)ref & G1_PARTIAL_ARRAY_MASK) == G1_PARTIAL_ARRAY_MASK;
  }

  // We never encode partial array oops as narrowOop*, so return false immediately.
  // This allows the compiler to create optimized code when popping references from
  // the work queue.
  inline bool has_partial_array_mask(narrowOop* ref) const {
    assert(((uintptr_t)ref & G1_PARTIAL_ARRAY_MASK) != G1_PARTIAL_ARRAY_MASK, "Partial array oop reference encoded as narrowOop*");
    return false;
  }

  // Only implement set_partial_array_mask() for regular oops, not for narrowOops.
  // We always encode partial arrays as regular oop, to allow the
  // specialization for has_partial_array_mask() for narrowOops above.
  // This means that unintentional use of this method with narrowOops are caught
  // by the compiler.
  inline oop* set_partial_array_mask(oop obj) const {
    assert(((uintptr_t)(void *)obj & G1_PARTIAL_ARRAY_MASK) == 0, "Information loss!");
    return (oop*) ((uintptr_t)(void *)obj | G1_PARTIAL_ARRAY_MASK);
  }

  inline oop clear_partial_array_mask(oop* ref) const {
    return cast_to_oop((intptr_t)ref & ~G1_PARTIAL_ARRAY_MASK);
  }

  inline void do_oop_partial_array(oop* p);

  // This method is applied to the fields of the objects that have just been copied.
  template <class T> inline void do_oop_evac(T* p);

  inline void deal_with_reference(oop* ref_to_scan);
  inline void deal_with_reference(narrowOop* ref_to_scan);

  inline void dispatch_reference(StarTask ref);

  // Tries to allocate word_sz in the PLAB of the next "generation" after trying to
  // allocate into dest. Previous_plab_refill_failed indicates whether previous
  // PLAB refill for the original (source) object failed.
  // Returns a non-NULL pointer if successful, and updates dest if required.
  // Also determines whether we should continue to try to allocate into the various
  // generations or just end trying to allocate.
  HeapWord* allocate_in_next_plab(G1HeapRegionAttr* dest,
                                  size_t word_sz,
                                  bool previous_plab_refill_failed,
                                  uint node_index);

  inline G1HeapRegionAttr next_region_attr(G1HeapRegionAttr const region_attr, markWord const m, uint& age);

  void report_promotion_event(G1HeapRegionAttr const dest_attr,
                              oop const old, size_t word_sz, uint age,
                              HeapWord * const obj_ptr, uint node_index) const;

  inline bool needs_partial_trimming() const;
  inline bool is_partially_trimmed() const;

  inline void trim_queue_to_threshold(uint threshold);

  // NUMA statistics related methods.
  inline void initialize_numa_stats();
  inline void flush_numa_stats();
  inline void update_numa_stats(uint node_index);

public:
  oop copy_to_survivor_space(G1HeapRegionAttr const region_attr, oop const obj, markWord const old_mark);

  void trim_queue();
  void trim_queue_partially();

  Tickspan trim_ticks() const;
  void reset_trim_ticks();

  inline void steal_and_trim_queue(RefToScanQueueSet *task_queues);

  // An attempt to evacuate "obj" has failed; take necessary steps.
  oop handle_evacuation_failure_par(oop obj, markWord m);

  template <typename T>
  inline void remember_root_into_optional_region(T* p);
  template <typename T>
  inline void remember_reference_into_optional_region(T* p);

  inline G1OopStarChunkedList* oops_into_optional_region(const HeapRegion* hr);
};

class G1ParScanThreadStateSet : public StackObj {
  G1CollectedHeap* _g1h;
  G1RedirtyCardsQueueSet* _rdcqs;
  G1ParScanThreadState** _states;
  size_t* _surviving_young_words_total;
  size_t _young_cset_length;
  size_t _optional_cset_length;
  uint _n_workers;
  bool _flushed;

 public:
  G1ParScanThreadStateSet(G1CollectedHeap* g1h,
                          G1RedirtyCardsQueueSet* rdcqs,
                          uint n_workers,
                          size_t young_cset_length,
                          size_t optional_cset_length);
  ~G1ParScanThreadStateSet();

  void flush();
  void record_unused_optional_region(HeapRegion* hr);

  G1ParScanThreadState* state_for_worker(uint worker_id);

  const size_t* surviving_young_words() const;

 private:
  G1ParScanThreadState* new_par_scan_state(uint worker_id, size_t young_cset_length);
};

#endif // SHARE_GC_G1_G1PARSCANTHREADSTATE_HPP
