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

#include "gc/g1/g1BarrierSet.inline.hpp"
#include "gc/g1/g1BarrierSetAssembler.hpp"
#include "gc/g1/g1CardTable.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#include "gc/g1/g1RegionPinCache.inline.hpp"
#include "gc/g1/g1SATBMarkQueueSet.hpp"
#include "gc/g1/g1ThreadLocalData.hpp"
#include "gc/shared/satbMarkQueue.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/threads.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "gc/g1/c1/g1BarrierSetC1.hpp"
#endif
#ifdef COMPILER2
#include "gc/g1/c2/g1BarrierSetC2.hpp"
#endif

class G1BarrierSetC1;
class G1BarrierSetC2;

G1BarrierSet::G1BarrierSet(G1CardTable* card_table,
                           G1CardTable* refinement_table) :
  CardTableBarrierSet(make_barrier_set_assembler<G1BarrierSetAssembler>(),
                      make_barrier_set_c1<G1BarrierSetC1>(),
                      make_barrier_set_c2<G1BarrierSetC2>(),
                      card_table,
                      BarrierSet::FakeRtti(BarrierSet::G1BarrierSet)),
  _satb_mark_queue_buffer_allocator("SATB Buffer Allocator", G1SATBBufferSize),
  _satb_mark_queue_set(&_satb_mark_queue_buffer_allocator),
  _refinement_table(refinement_table)
{}

G1BarrierSet::~G1BarrierSet() {
  delete refinement_table();
}

void G1BarrierSet::swap_global_card_table() {
  G1CardTable* temp = static_cast<G1CardTable*>(card_table());
  _card_table.store_relaxed(refinement_table());
  _refinement_table.store_relaxed(temp);
}

void G1BarrierSet::update_card_table_base(Thread* thread) {
#ifdef ASSERT
  {
    ResourceMark rm;
    assert(thread->is_Java_thread(), "may only update card table base of JavaThreads, not %s", thread->name());
  }
#endif
  G1ThreadLocalData::set_byte_map_base(thread, card_table()->byte_map_base());
}

template <class T> void
G1BarrierSet::write_ref_array_pre_work(T* dst, size_t count) {
  G1SATBMarkQueueSet& queue_set = G1BarrierSet::satb_mark_queue_set();
  if (!queue_set.is_active()) return;

  SATBMarkQueue& queue = G1ThreadLocalData::satb_mark_queue(Thread::current());

  T* elem_ptr = dst;
  for (size_t i = 0; i < count; i++, elem_ptr++) {
    T heap_oop = RawAccess<>::oop_load(elem_ptr);
    if (!CompressedOops::is_null(heap_oop)) {
      queue_set.enqueue_known_active(queue, CompressedOops::decode_not_null(heap_oop));
    }
  }
}

void G1BarrierSet::write_ref_array_pre(oop* dst, size_t count, bool dest_uninitialized) {
  if (!dest_uninitialized) {
    write_ref_array_pre_work(dst, count);
  }
}

void G1BarrierSet::write_ref_array_pre(narrowOop* dst, size_t count, bool dest_uninitialized) {
  if (!dest_uninitialized) {
    write_ref_array_pre_work(dst, count);
  }
}

void G1BarrierSet::write_region(MemRegion mr) {
  if (mr.is_empty()) {
    return;
  }

  // Skip writes to young gen.
  if (G1CollectedHeap::heap()->heap_region_containing(mr.start())->is_young()) {
    // MemRegion should not span multiple regions for arrays in young gen.
    DEBUG_ONLY(G1HeapRegion* containing_hr = G1CollectedHeap::heap()->heap_region_containing(mr.start());)
    assert(containing_hr->is_young(), "it should be young");
    assert(containing_hr->is_in(mr.start()), "it should contain start");
    assert(containing_hr->is_in(mr.last()), "it should also contain last");
    return;
  }

  // We need to make sure that we get the start/end byte information for the area
  // to mark from the same card table to avoid getting confused in the mark loop
  // further below - we might execute while the global card table is being switched.
  //
  // It does not matter which card table we write to: at worst we may write to the
  // new card table (after the switching), which means that we will catch the
  // marks next time.
  // If we write to the old card table (after the switching, then the refinement
  // table) the oncoming handshake will do the memory synchronization.
  CardTable* local_card_table = card_table();

  volatile CardValue* byte = local_card_table->byte_for(mr.start());
  CardValue* last_byte = local_card_table->byte_for(mr.last());

  // Dirty cards only if necessary.
  for (; byte <= last_byte; byte++) {
    CardValue bv = *byte;
    if (bv == G1CardTable::clean_card_val()) {
      *byte = G1CardTable::dirty_card_val();
    }
  }
}

void G1BarrierSet::on_thread_create(Thread* thread) {
  // Create thread local data
  G1ThreadLocalData::create(thread);
}

void G1BarrierSet::on_thread_destroy(Thread* thread) {
  // Destroy thread local data
  G1ThreadLocalData::destroy(thread);
}

void G1BarrierSet::on_thread_attach(Thread* thread) {
  BarrierSet::on_thread_attach(thread);
  SATBMarkQueue& satbq = G1ThreadLocalData::satb_mark_queue(thread);
  assert(!satbq.is_active(), "SATB queue should not be active");
  assert(satbq.buffer() == nullptr, "SATB queue should not have a buffer");
  assert(satbq.index() == 0, "SATB queue index should be zero");
  // If we are creating the thread during a marking cycle, we should
  // set the active field of the SATB queue to true.  That involves
  // copying the global is_active value to this thread's queue.
  satbq.set_active(_satb_mark_queue_set.is_active());

  if (thread->is_Java_thread()) {
    assert(Threads_lock->is_locked(), "must be, synchronization with refinement.");
    update_card_table_base(thread);
  }
}

void G1BarrierSet::on_thread_detach(Thread* thread) {
  // Flush any deferred card marks.
  CardTableBarrierSet::on_thread_detach(thread);
  {
    SATBMarkQueue& queue = G1ThreadLocalData::satb_mark_queue(thread);
    G1BarrierSet::satb_mark_queue_set().flush_queue(queue);
  }
  {
    G1RegionPinCache& cache = G1ThreadLocalData::pin_count_cache(thread);
    cache.flush();
  }
}

void G1BarrierSet::print_on(outputStream* st) const {
  card_table()->print_on(st, "Card");
  refinement_table()->print_on(st, "Refinement");
}
