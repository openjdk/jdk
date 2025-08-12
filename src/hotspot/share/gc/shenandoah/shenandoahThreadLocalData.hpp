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
#include "gc/shenandoah/shenandoahSATBMarkQueueSet.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/debug.hpp"
#include "utilities/sizes.hpp"

class ShenandoahThreadLocalData {
private:
  char _gc_state;
  // Evacuation OOM state
  uint8_t                 _oom_scope_nesting_level;
  bool                    _oom_during_evac;

  SATBMarkQueue           _satb_mark_queue;

  // Current active CardTable's byte_map_base for this thread.
  CardTable::CardValue*   _card_table;

  // Thread-local allocation buffer for object evacuations.
  // In generational mode, it is exclusive to the young generation.
  PLAB* _gclab;
  size_t _gclab_size;

  double _paced_time;

  // Thread-local allocation buffer only used in generational mode.
  // Used both by mutator threads and by GC worker threads
  // for evacuations within the old generation and
  // for promotions from the young generation into the old generation.
  PLAB* _plab;

  // Heuristics will grow the desired size of plabs.
  size_t _plab_desired_size;

  // Once the plab has been allocated, and we know the actual size, we record it here.
  size_t _plab_actual_size;

  // As the plab is used for promotions, this value is incremented. When the plab is
  // retired, the difference between 'actual_size' and 'promoted' will be returned to
  // the old generation's promotion reserve (i.e., it will be 'unexpended').
  size_t _plab_promoted;

  // If false, no more promotion by this thread during this evacuation phase.
  bool   _plab_allows_promotion;

  // If true, evacuations may attempt to allocate a smaller plab if the original size fails.
  bool   _plab_retries_enabled;

  ShenandoahEvacuationStats* _evacuation_stats;

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

  static void set_gc_state(Thread* thread, char gc_state) {
    data(thread)->_gc_state = gc_state;
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
      data(thread)->_plab = new PLAB(align_up(PLAB::min_size(), CardTable::card_size_in_words()));
      data(thread)->_plab_desired_size = 0;
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

  static void record_age(Thread* thread, size_t bytes, uint age) {
    data(thread)->_evacuation_stats->record_age(bytes, age);
  }

  static ShenandoahEvacuationStats* evacuation_stats(Thread* thread) {
    return data(thread)->_evacuation_stats;
  }

  static PLAB* plab(Thread* thread) {
    return data(thread)->_plab;
  }

  static size_t plab_size(Thread* thread) {
    return data(thread)->_plab_desired_size;
  }

  static void set_plab_size(Thread* thread, size_t v) {
    data(thread)->_plab_desired_size = v;
  }

  static void enable_plab_retries(Thread* thread) {
    data(thread)->_plab_retries_enabled = true;
  }

  static void disable_plab_retries(Thread* thread) {
    data(thread)->_plab_retries_enabled = false;
  }

  static bool plab_retries_enabled(Thread* thread) {
    return data(thread)->_plab_retries_enabled;
  }

  static void enable_plab_promotions(Thread* thread) {
    data(thread)->_plab_allows_promotion = true;
  }

  static void disable_plab_promotions(Thread* thread) {
    data(thread)->_plab_allows_promotion = false;
  }

  static bool allow_plab_promotions(Thread* thread) {
    return data(thread)->_plab_allows_promotion;
  }

  static void reset_plab_promoted(Thread* thread) {
    data(thread)->_plab_promoted = 0;
  }

  static void add_to_plab_promoted(Thread* thread, size_t increment) {
    data(thread)->_plab_promoted += increment;
  }

  static void subtract_from_plab_promoted(Thread* thread, size_t increment) {
    assert(data(thread)->_plab_promoted >= increment, "Cannot subtract more than remaining promoted");
    data(thread)->_plab_promoted -= increment;
  }

  static size_t get_plab_promoted(Thread* thread) {
    return data(thread)->_plab_promoted;
  }

  static void set_plab_actual_size(Thread* thread, size_t value) {
    data(thread)->_plab_actual_size = value;
  }

  static size_t get_plab_actual_size(Thread* thread) {
    return data(thread)->_plab_actual_size;
  }

  static void add_paced_time(Thread* thread, double v) {
    data(thread)->_paced_time += v;
  }

  static double paced_time(Thread* thread) {
    return data(thread)->_paced_time;
  }

  static void reset_paced_time(Thread* thread) {
    data(thread)->_paced_time = 0;
  }

  // Evacuation OOM handling
  static bool is_oom_during_evac(Thread* thread) {
    return data(thread)->_oom_during_evac;
  }

  static void set_oom_during_evac(Thread* thread, bool oom) {
    data(thread)->_oom_during_evac = oom;
  }

  static uint8_t evac_oom_scope_level(Thread* thread) {
    return data(thread)->_oom_scope_nesting_level;
  }

  // Push the scope one level deeper, return previous level
  static uint8_t push_evac_oom_scope(Thread* thread) {
    uint8_t level = evac_oom_scope_level(thread);
    assert(level < 254, "Overflow nesting level"); // UINT8_MAX = 255
    data(thread)->_oom_scope_nesting_level = level + 1;
    return level;
  }

  // Pop the scope by one level, return previous level
  static uint8_t pop_evac_oom_scope(Thread* thread) {
    uint8_t level = evac_oom_scope_level(thread);
    assert(level > 0, "Underflow nesting level");
    data(thread)->_oom_scope_nesting_level = level - 1;
    return level;
  }

  static bool is_evac_allowed(Thread* thread) {
    return evac_oom_scope_level(thread) > 0;
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

  static ByteSize card_table_offset() {
    return Thread::gc_data_offset() + byte_offset_of(ShenandoahThreadLocalData, _card_table);
  }
};

STATIC_ASSERT(sizeof(ShenandoahThreadLocalData) <= sizeof(GCThreadLocalData));

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHTHREADLOCALDATA_HPP
