/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1PARSCANTHREADSTATE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1PARSCANTHREADSTATE_HPP

#include "gc_implementation/g1/dirtyCardQueue.hpp"
#include "gc_implementation/g1/g1SATBCardTableModRefBS.hpp"
#include "gc_implementation/g1/g1CollectedHeap.hpp"
#include "gc_implementation/g1/g1CollectorPolicy.hpp"
#include "gc_implementation/g1/g1OopClosures.hpp"
#include "gc_implementation/g1/g1RemSet.hpp"
#include "gc_implementation/shared/ageTable.hpp"
#include "memory/allocation.hpp"
#include "oops/oop.hpp"

class HeapRegion;
class outputStream;

class G1ParScanThreadState : public StackObj {
 private:
  G1CollectedHeap* _g1h;
  RefToScanQueue*  _refs;
  DirtyCardQueue   _dcq;
  G1SATBCardTableModRefBS* _ct_bs;
  G1RemSet* _g1_rem;

  G1ParGCAllocBuffer  _surviving_alloc_buffer;
  G1ParGCAllocBuffer  _tenured_alloc_buffer;
  G1ParGCAllocBuffer* _alloc_buffers[GCAllocPurposeCount];
  ageTable            _age_table;

  G1ParScanClosure    _scanner;

  size_t           _alloc_buffer_waste;
  size_t           _undo_waste;

  OopsInHeapRegionClosure*      _evac_failure_cl;

  int  _hash_seed;
  uint _queue_num;

  size_t _term_attempts;

  double _start;
  double _start_strong_roots;
  double _strong_roots_time;
  double _start_term;
  double _term_time;

  // Map from young-age-index (0 == not young, 1 is youngest) to
  // surviving words. base is what we get back from the malloc call
  size_t* _surviving_young_words_base;
  // this points into the array, as we use the first few entries for padding
  size_t* _surviving_young_words;

#define PADDING_ELEM_NUM (DEFAULT_CACHE_LINE_SIZE / sizeof(size_t))

  void   add_to_alloc_buffer_waste(size_t waste) { _alloc_buffer_waste += waste; }

  void   add_to_undo_waste(size_t waste)         { _undo_waste += waste; }

  DirtyCardQueue& dirty_card_queue()             { return _dcq;  }
  G1SATBCardTableModRefBS* ctbs()                { return _ct_bs; }

  template <class T> inline void immediate_rs_update(HeapRegion* from, T* p, int tid);

  template <class T> void deferred_rs_update(HeapRegion* from, T* p, int tid) {
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

 public:
  G1ParScanThreadState(G1CollectedHeap* g1h, uint queue_num, ReferenceProcessor* rp);
  ~G1ParScanThreadState();

  ageTable*         age_table()       { return &_age_table;       }

  G1ParGCAllocBuffer* alloc_buffer(GCAllocPurpose purpose) {
    return _alloc_buffers[purpose];
  }

  size_t alloc_buffer_waste() const              { return _alloc_buffer_waste; }
  size_t undo_waste() const                      { return _undo_waste; }

#ifdef ASSERT
  bool queue_is_empty() const { return _refs->is_empty(); }

  bool verify_ref(narrowOop* ref) const;
  bool verify_ref(oop* ref) const;
  bool verify_task(StarTask ref) const;
#endif // ASSERT

  template <class T> void push_on_queue(T* ref) {
    assert(verify_ref(ref), "sanity");
    _refs->push(ref);
  }

  template <class T> inline void update_rs(HeapRegion* from, T* p, int tid);

 private:

  inline HeapWord* allocate(GCAllocPurpose purpose, size_t word_sz);
  inline HeapWord* allocate_slow(GCAllocPurpose purpose, size_t word_sz);
  inline void undo_allocation(GCAllocPurpose purpose, HeapWord* obj, size_t word_sz);

 public:

  void set_evac_failure_closure(OopsInHeapRegionClosure* evac_failure_cl) {
    _evac_failure_cl = evac_failure_cl;
  }

  OopsInHeapRegionClosure* evac_failure_closure() { return _evac_failure_cl; }

  int* hash_seed() { return &_hash_seed; }
  uint queue_num() { return _queue_num; }

  size_t term_attempts() const  { return _term_attempts; }
  void note_term_attempt() { _term_attempts++; }

  void start_strong_roots() {
    _start_strong_roots = os::elapsedTime();
  }
  void end_strong_roots() {
    _strong_roots_time += (os::elapsedTime() - _start_strong_roots);
  }
  double strong_roots_time() const { return _strong_roots_time; }

  void start_term_time() {
    note_term_attempt();
    _start_term = os::elapsedTime();
  }
  void end_term_time() {
    _term_time += (os::elapsedTime() - _start_term);
  }
  double term_time() const { return _term_time; }

  double elapsed_time() const {
    return os::elapsedTime() - _start;
  }

  static void print_termination_stats_hdr(outputStream* const st = gclog_or_tty);
  void print_termination_stats(int i, outputStream* const st = gclog_or_tty) const;

  size_t* surviving_young_words() {
    // We add on to hide entry 0 which accumulates surviving words for
    // age -1 regions (i.e. non-young ones)
    return _surviving_young_words;
  }

 private:
  void retire_alloc_buffers();

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
 public:

  oop copy_to_survivor_space(oop const obj);

  void trim_queue();

  inline void steal_and_trim_queue(RefToScanQueueSet *task_queues);
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1PARSCANTHREADSTATE_HPP
