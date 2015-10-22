/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1PARSCANTHREADSTATE_HPP
#define SHARE_VM_GC_G1_G1PARSCANTHREADSTATE_HPP

#include "gc/g1/dirtyCardQueue.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1CollectorPolicy.hpp"
#include "gc/g1/g1OopClosures.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1SATBCardTableModRefBS.hpp"
#include "gc/shared/ageTable.hpp"
#include "memory/allocation.hpp"
#include "oops/oop.hpp"

class G1PLABAllocator;
class G1EvacuationRootClosures;
class HeapRegion;
class outputStream;

class G1ParScanThreadState : public CHeapObj<mtGC> {
 private:
  G1CollectedHeap* _g1h;
  RefToScanQueue*  _refs;
  DirtyCardQueue   _dcq;
  G1SATBCardTableModRefBS* _ct_bs;
  G1EvacuationRootClosures* _closures;

  G1PLABAllocator*  _plab_allocator;

  ageTable          _age_table;
  InCSetState       _dest[InCSetState::Num];
  // Local tenuring threshold.
  uint              _tenuring_threshold;
  G1ParScanClosure  _scanner;

  int  _hash_seed;
  uint _worker_id;

  // Map from young-age-index (0 == not young, 1 is youngest) to
  // surviving words. base is what we get back from the malloc call
  size_t* _surviving_young_words_base;
  // this points into the array, as we use the first few entries for padding
  size_t* _surviving_young_words;

  // Indicates whether in the last generation (old) there is no more space
  // available for allocation.
  bool _old_gen_is_full;

#define PADDING_ELEM_NUM (DEFAULT_CACHE_LINE_SIZE / sizeof(size_t))

  DirtyCardQueue& dirty_card_queue()             { return _dcq;  }
  G1SATBCardTableModRefBS* ctbs()                { return _ct_bs; }

  InCSetState dest(InCSetState original) const {
    assert(original.is_valid(),
           "Original state invalid: " CSETSTATE_FORMAT, original.value());
    assert(_dest[original.value()].is_valid_gen(),
           "Dest state is invalid: " CSETSTATE_FORMAT, _dest[original.value()].value());
    return _dest[original.value()];
  }

 public:
  G1ParScanThreadState(G1CollectedHeap* g1h, uint worker_id, size_t young_cset_length);
  ~G1ParScanThreadState();

  void set_ref_processor(ReferenceProcessor* rp) { _scanner.set_ref_processor(rp); }

#ifdef ASSERT
  bool queue_is_empty() const { return _refs->is_empty(); }

  bool verify_ref(narrowOop* ref) const;
  bool verify_ref(oop* ref) const;
  bool verify_task(StarTask ref) const;
#endif // ASSERT

  template <class T> void push_on_queue(T* ref);

  template <class T> void update_rs(HeapRegion* from, T* p) {
    // If the new value of the field points to the same region or
    // is the to-space, we don't need to include it in the Rset updates.
    if (!from->is_in_reserved(oopDesc::load_decode_heap_oop(p)) && !from->is_survivor()) {
      size_t card_index = ctbs()->index_for(p);
      // If the card hasn't been added to the buffer, do it.
      if (ctbs()->mark_card_deferred(card_index)) {
        dirty_card_queue().enqueue((jbyte*)ctbs()->byte_for_index(card_index));
      }
    }
  }

  G1EvacuationRootClosures* closures() { return _closures; }
  uint worker_id() { return _worker_id; }

  // Returns the current amount of waste due to alignment or not being able to fit
  // objects within LABs and the undo waste.
  virtual void waste(size_t& wasted, size_t& undo_wasted);

  size_t* surviving_young_words() {
    // We add one to hide entry 0 which accumulates surviving words for
    // age -1 regions (i.e. non-young ones)
    return _surviving_young_words + 1;
  }

  void flush(size_t* surviving_young_words);

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
  template <class T> inline void do_oop_evac(T* p, HeapRegion* from);

  template <class T> inline void deal_with_reference(T* ref_to_scan);

  inline void dispatch_reference(StarTask ref);

  // Tries to allocate word_sz in the PLAB of the next "generation" after trying to
  // allocate into dest. State is the original (source) cset state for the object
  // that is allocated for. Previous_plab_refill_failed indicates whether previously
  // a PLAB refill into "state" failed.
  // Returns a non-NULL pointer if successful, and updates dest if required.
  // Also determines whether we should continue to try to allocate into the various
  // generations or just end trying to allocate.
  HeapWord* allocate_in_next_plab(InCSetState const state,
                                  InCSetState* dest,
                                  size_t word_sz,
                                  AllocationContext_t const context,
                                  bool previous_plab_refill_failed);

  inline InCSetState next_state(InCSetState const state, markOop const m, uint& age);

  void report_promotion_event(InCSetState const dest_state,
                              oop const old, size_t word_sz, uint age,
                              HeapWord * const obj_ptr, const AllocationContext_t context) const;
 public:

  oop copy_to_survivor_space(InCSetState const state, oop const obj, markOop const old_mark);

  void trim_queue();

  inline void steal_and_trim_queue(RefToScanQueueSet *task_queues);

  // An attempt to evacuate "obj" has failed; take necessary steps.
  oop handle_evacuation_failure_par(oop obj, markOop m);
};

class G1ParScanThreadStateSet : public StackObj {
  G1CollectedHeap* _g1h;
  G1ParScanThreadState** _states;
  size_t* _surviving_young_words_total;
  size_t* _cards_scanned;
  size_t _total_cards_scanned;
  uint _n_workers;
  bool _flushed;

 public:
  G1ParScanThreadStateSet(G1CollectedHeap* g1h, uint n_workers, size_t young_cset_length) :
      _g1h(g1h),
      _states(NEW_C_HEAP_ARRAY(G1ParScanThreadState*, n_workers, mtGC)),
      _surviving_young_words_total(NEW_C_HEAP_ARRAY(size_t, young_cset_length, mtGC)),
      _cards_scanned(NEW_C_HEAP_ARRAY(size_t, n_workers, mtGC)),
      _total_cards_scanned(0),
      _n_workers(n_workers),
      _flushed(false) {
    for (uint i = 0; i < n_workers; ++i) {
      _states[i] = new_par_scan_state(i, young_cset_length);
    }
    memset(_surviving_young_words_total, 0, young_cset_length * sizeof(size_t));
    memset(_cards_scanned, 0, n_workers * sizeof(size_t));
  }

  ~G1ParScanThreadStateSet() {
    assert(_flushed, "thread local state from the per thread states should have been flushed");
    FREE_C_HEAP_ARRAY(G1ParScanThreadState*, _states);
    FREE_C_HEAP_ARRAY(size_t, _surviving_young_words_total);
    FREE_C_HEAP_ARRAY(size_t, _cards_scanned);
  }

  void flush();

  G1ParScanThreadState* state_for_worker(uint worker_id);

  void add_cards_scanned(uint worker_id, size_t cards_scanned);
  size_t total_cards_scanned() const;
  const size_t* surviving_young_words() const;

 private:
  G1ParScanThreadState* new_par_scan_state(uint worker_id, size_t young_cset_length);
};

#endif // SHARE_VM_GC_G1_G1PARSCANTHREADSTATE_HPP
