/*
 * Copyright (c) 2018, 2022, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHTHREADLOCALDATA_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHTHREADLOCALDATA_HPP

#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcThreadLocalData.hpp"
#include "gc/shared/plab.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "gc/shenandoah/shenandoahAffiliation.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahCardTable.hpp"
#include "gc/shenandoah/shenandoahCodeRoots.hpp"
#include "gc/shenandoah/shenandoahEvacTracker.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahPLAB.hpp"
#include "gc/shenandoah/shenandoahSATBMarkQueueSet.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/debug.hpp"
#include "utilities/sizes.hpp"

class ShenandoahThreadLocalData {
private:
  // Thread-local mirror for global GC state
  char _gc_state;

  // Quickened version of GC state.
  // This allows all architectures to quickly check the group of states by using a single byte load.
  enum FastGCState {
    FORWARDED               = ShenandoahHeap::HAS_FORWARDED,
    MARKING                 = ShenandoahHeap::MARKING,
    WEAK                    = ShenandoahHeap::WEAK_ROOTS,
    FORWARDED_MARKING       = ShenandoahHeap::HAS_FORWARDED | ShenandoahHeap::MARKING,
    FORWARDED_WEAK          = ShenandoahHeap::HAS_FORWARDED | ShenandoahHeap::WEAK_ROOTS,
    MARKING_WEAK            = ShenandoahHeap::MARKING       | ShenandoahHeap::WEAK_ROOTS,
    FORWARDED_MARKING_WEAK  = ShenandoahHeap::HAS_FORWARDED | ShenandoahHeap::MARKING    | ShenandoahHeap::WEAK_ROOTS
  };

  enum FastGCStatePos {
    POS_FORWARDED               = 0,
    POS_MARKING                 = 1,
    POS_WEAK                    = 2,
    POS_FORWARDED_MARKING       = 3,
    POS_FORWARDED_WEAK          = 4,
    POS_MARKING_WEAK            = 5,
    POS_FORWARDED_MARKING_WEAK  = 6,
    POS_MAX
  };

  char _gc_state_fast_array[POS_MAX];

  SATBMarkQueue           _satb_mark_queue;

  // Current active CardTable's byte_map_base for this thread.
  CardTable::CardValue*   _card_table;

  // Thread-local allocation buffer for object evacuations.
  // In generational mode, it is exclusive to the young generation.
  PLAB* _gclab;
  size_t _gclab_size;

  // Thread-local allocation buffer only used in generational mode.
  // Used both by mutator threads and by GC worker threads
  // for evacuations within the old generation and
  // for promotions from the young generation into the old generation.
  ShenandoahPLAB* _shenandoah_plab;

  ShenandoahEvacuationStats* _evacuation_stats;

  Atomic<HeapWord*> _invisible_root;
  Atomic<size_t> _invisible_root_word_size;

  ShenandoahThreadLocalData();
  ~ShenandoahThreadLocalData();

  static ShenandoahThreadLocalData* data(Thread* thread) {
    assert(UseShenandoahGC, "Sanity");
    return thread->gc_data<ShenandoahThreadLocalData>();
  }

  static ByteSize satb_mark_queue_offset() {
    return Thread::gc_data_offset() + byte_offset_of(ShenandoahThreadLocalData, _satb_mark_queue);
  }

public:
  static void create(Thread* thread) {
    new (data(thread)) ShenandoahThreadLocalData();
  }

  static void destroy(Thread* thread) {
    data(thread)->~ShenandoahThreadLocalData();
  }

  static SATBMarkQueue& satb_mark_queue(Thread* thread) {
    return data(thread)->_satb_mark_queue;
  }

  static char gc_state_to_fast_array_index(char gc_state) {
    if (gc_state == FORWARDED)              return POS_FORWARDED;
    if (gc_state == MARKING)                return POS_MARKING;
    if (gc_state == WEAK)                   return POS_WEAK;
    if (gc_state == FORWARDED_MARKING)      return POS_FORWARDED_MARKING;
    if (gc_state == FORWARDED_WEAK)         return POS_FORWARDED_WEAK;
    if (gc_state == MARKING_WEAK)           return POS_MARKING_WEAK;
    if (gc_state == FORWARDED_MARKING_WEAK) return POS_FORWARDED_MARKING_WEAK;
    ShouldNotReachHere();
    return 0;
  }

  static void set_gc_state(Thread* thread, char gc_state) {
    ShenandoahThreadLocalData* d = data(thread);
    d->_gc_state = gc_state;
    d->_gc_state_fast_array[POS_FORWARDED]              = (gc_state & FORWARDED) != 0;
    d->_gc_state_fast_array[POS_MARKING]                = (gc_state & MARKING) != 0;
    d->_gc_state_fast_array[POS_WEAK]                   = (gc_state & WEAK) != 0;
    d->_gc_state_fast_array[POS_FORWARDED_MARKING]      = (gc_state & FORWARDED_MARKING) != 0;
    d->_gc_state_fast_array[POS_FORWARDED_WEAK]         = (gc_state & FORWARDED_WEAK) != 0;
    d->_gc_state_fast_array[POS_MARKING_WEAK]           = (gc_state & MARKING_WEAK) != 0;
    d->_gc_state_fast_array[POS_FORWARDED_MARKING_WEAK] = (gc_state & FORWARDED_MARKING_WEAK) != 0;
  }

  static char gc_state(Thread* thread) {
    return data(thread)->_gc_state;
  }

  static bool is_gc_state(Thread* thread, ShenandoahHeap::GCState state) {
    return (gc_state(thread) & state) != 0;
  }

  static bool is_gc_state(ShenandoahHeap::GCState state) {
    return is_gc_state(Thread::current(), state);
  }

  static void set_card_table(Thread* thread, CardTable::CardValue* ct) {
    assert(ct != nullptr, "trying to set thread local card_table pointer to nullptr.");
    data(thread)->_card_table = ct;
  }

  static CardTable::CardValue* card_table(Thread* thread) {
    CardTable::CardValue* ct = data(thread)->_card_table;
    assert(ct != nullptr, "returning a null thread local card_table pointer.");
    return ct;
  }

  static void initialize_gclab(Thread* thread) {
    assert(data(thread)->_gclab == nullptr, "Only initialize once");
    data(thread)->_gclab = new PLAB(PLAB::min_size());
    data(thread)->_gclab_size = 0;

    if (ShenandoahHeap::heap()->mode()->is_generational()) {
      data(thread)->_shenandoah_plab = new ShenandoahPLAB();
    }
  }

  static PLAB* gclab(Thread* thread) {
    return data(thread)->_gclab;
  }

  static size_t gclab_size(Thread* thread) {
    return data(thread)->_gclab_size;
  }

  static void set_gclab_size(Thread* thread, size_t v) {
    data(thread)->_gclab_size = v;
  }

  static void begin_evacuation(Thread* thread, size_t bytes, ShenandoahAffiliation from, ShenandoahAffiliation to) {
    data(thread)->_evacuation_stats->begin_evacuation(bytes, from, to);
  }

  static void end_evacuation(Thread* thread, size_t bytes, ShenandoahAffiliation from, ShenandoahAffiliation to) {
    data(thread)->_evacuation_stats->end_evacuation(bytes, from, to);
  }

  static ShenandoahEvacuationStats* evacuation_stats(Thread* thread) {
    return data(thread)->_evacuation_stats;
  }

  static ShenandoahPLAB* shenandoah_plab(Thread* thread) {
    return data(thread)->_shenandoah_plab;
  }

  // Offsets
  static ByteSize satb_mark_queue_index_offset() {
    return satb_mark_queue_offset() + SATBMarkQueue::byte_offset_of_index();
  }

  static ByteSize satb_mark_queue_buffer_offset() {
    return satb_mark_queue_offset() + SATBMarkQueue::byte_offset_of_buf();
  }

  static ByteSize gc_state_offset() {
    return Thread::gc_data_offset() + byte_offset_of(ShenandoahThreadLocalData, _gc_state);
  }

  static ByteSize gc_state_fast_array_offset(char gc_state) {
    return Thread::gc_data_offset() + byte_offset_of(ShenandoahThreadLocalData, _gc_state_fast_array) + in_ByteSize(gc_state_to_fast_array_index(gc_state));
  }

  static ByteSize card_table_offset() {
    return Thread::gc_data_offset() + byte_offset_of(ShenandoahThreadLocalData, _card_table);
  }

  // invisible root are the partially initialized obj array set by ShenandoahObjArrayAllocator
  static void set_invisible_root(Thread* thread, HeapWord* invisible_root, size_t word_size) {
    data(thread)->_invisible_root.store_relaxed(invisible_root);
    data(thread)->_invisible_root_word_size.store_relaxed(word_size);
  }

  static void clear_invisible_root(Thread* thread) {
    data(thread)->_invisible_root.store_relaxed(nullptr);
    data(thread)->_invisible_root_word_size.store_relaxed(0);
  }

  static HeapWord* get_invisible_root(Thread* thread) {
    return data(thread)->_invisible_root.load_relaxed();
  }

  static size_t get_invisible_root_word_size(Thread* thread) {
    return data(thread)->_invisible_root_word_size.load_relaxed();
  }
};

STATIC_ASSERT(sizeof(ShenandoahThreadLocalData) <= sizeof(GCThreadLocalData));

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHTHREADLOCALDATA_HPP
