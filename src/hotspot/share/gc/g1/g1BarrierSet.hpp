/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1BARRIERSET_HPP
#define SHARE_GC_G1_G1BARRIERSET_HPP

#include "gc/g1/g1SATBMarkQueueSet.hpp"
#include "gc/shared/bufferNode.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "runtime/atomic.hpp"

class G1CardTable;
class Thread;

// This barrier set is specialized to manage two card tables:
// * one the mutator is currently working on ("card table")
// * one the refinement threads or GC during pause are working on ("refinement table")
//
// The card table acts like a regular card table where the mutator dirties cards
// containing potentially interesting references.
//
// When the amount of dirty cards on the card table exceeds a threshold, G1 swaps
// the card tables and has the refinement threads reduce them by "refining"
// them.
// I.e. refinement looks at all dirty cards on the refinement table, and updates
// the remembered sets accordingly, clearing the cards on the refinement table.
//
// Meanwhile the mutator continues dirtying the now empty card table.
//
// This separation of data the mutator and refinement threads are working on
// removes the need for any fine-grained (per mutator write) synchronization between
// them, keeping the write barrier simple.
//
// The refinement threads mark cards in the current collection set specially on the
// card table - this is fine wrt synchronization with the mutator, because at
// most the mutator will overwrite it again if there is a race, as G1 will scan the
// entire card either way during the GC pause.
//
// During garbage collection, if the refinement table is known to be non-empty, G1
// merges it back (and cleaning it) to the card table which is scanned for dirty
// cards.
//
class G1BarrierSet: public CardTableBarrierSet {
 private:
  BufferNode::Allocator _satb_mark_queue_buffer_allocator;
  G1SATBMarkQueueSet _satb_mark_queue_set;

  Atomic<G1CardTable*> _refinement_table;

 public:
  G1BarrierSet(G1CardTable* card_table, G1CardTable* refinement_table);
  virtual ~G1BarrierSet();

  static G1BarrierSet* g1_barrier_set() {
    return barrier_set_cast<G1BarrierSet>(BarrierSet::barrier_set());
  }

  G1CardTable* refinement_table() const { return _refinement_table.load_relaxed(); }

  // Swap the global card table references, without synchronization.
  void swap_global_card_table();

  // Update the given thread's card table (byte map) base to the current card table's.
  void update_card_table_base(Thread* thread);

  // Add "pre_val" to a set of objects that may have been disconnected from the
  // pre-marking object graph. Prefer the version that takes location, as it
  // can avoid touching the heap unnecessarily.
  template <class T> static void enqueue(T* dst);
  static void enqueue_preloaded(oop pre_val);

  static void enqueue_preloaded_if_weak(DecoratorSet decorators, oop value);

  template <class T> void write_ref_array_pre_work(T* dst, size_t count);
  virtual void write_ref_array_pre(oop* dst, size_t count, bool dest_uninitialized);
  virtual void write_ref_array_pre(narrowOop* dst, size_t count, bool dest_uninitialized);

  template <DecoratorSet decorators, typename T>
  void write_ref_field_pre(T* field);

  virtual void write_region(MemRegion mr);

  template <DecoratorSet decorators = DECORATORS_NONE, typename T>
  void write_ref_field_post(T* field);

  virtual void on_thread_create(Thread* thread);
  virtual void on_thread_destroy(Thread* thread);
  virtual void on_thread_attach(Thread* thread);
  virtual void on_thread_detach(Thread* thread);

  static G1SATBMarkQueueSet& satb_mark_queue_set() {
    return g1_barrier_set()->_satb_mark_queue_set;
  }

  virtual void print_on(outputStream* st) const;

  // Callbacks for runtime accesses.
  template <DecoratorSet decorators, typename BarrierSetT = G1BarrierSet>
  class AccessBarrier: public CardTableBarrierSet::AccessBarrier<decorators, BarrierSetT> {
    typedef CardTableBarrierSet::AccessBarrier<decorators, BarrierSetT> CardTableBS;
    typedef BarrierSet::AccessBarrier<decorators, BarrierSetT> Raw;

  public:
    // Needed for loads on non-heap weak references
    template <typename T>
    static oop oop_load_not_in_heap(T* addr);

    // Needed for non-heap stores
    template <typename T>
    static void oop_store_not_in_heap(T* addr, oop new_value);

    // Needed for weak references
    static oop oop_load_in_heap_at(oop base, ptrdiff_t offset);

    // Defensive: will catch weak oops at addresses in heap
    template <typename T>
    static oop oop_load_in_heap(T* addr);

    template <typename T>
    static oop oop_atomic_cmpxchg_not_in_heap(T* addr, oop compare_value, oop new_value);
    template <typename T>
    static oop oop_atomic_xchg_not_in_heap(T* addr, oop new_value);
  };
};

template<>
struct BarrierSet::GetName<G1BarrierSet> {
  static const BarrierSet::Name value = BarrierSet::G1BarrierSet;
};

template<>
struct BarrierSet::GetType<BarrierSet::G1BarrierSet> {
  typedef ::G1BarrierSet type;
};

#endif // SHARE_GC_G1_G1BARRIERSET_HPP
