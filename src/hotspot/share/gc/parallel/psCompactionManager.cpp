/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/objectStartArray.hpp"
#include "gc/parallel/parMarkBitMap.inline.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psCompactionManager.inline.hpp"
#include "gc/parallel/psOldGen.hpp"
#include "gc/parallel/psParallelCompact.inline.hpp"
#include "gc/shared/partialArrayState.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/instanceMirrorKlass.inline.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/checkedCast.hpp"

PSOldGen*               ParCompactionManager::_old_gen = nullptr;
ParCompactionManager**  ParCompactionManager::_manager_array = nullptr;

ParCompactionManager::PSMarkTasksQueueSet*  ParCompactionManager::_marking_stacks = nullptr;
ParCompactionManager::RegionTaskQueueSet*   ParCompactionManager::_region_task_queues = nullptr;
PartialArrayStateAllocator* ParCompactionManager::_partial_array_state_allocator = nullptr;

ObjectStartArray*    ParCompactionManager::_start_array = nullptr;
ParMarkBitMap*       ParCompactionManager::_mark_bitmap = nullptr;
GrowableArray<size_t >* ParCompactionManager::_shadow_region_array = nullptr;
Monitor*                ParCompactionManager::_shadow_region_monitor = nullptr;

PreservedMarksSet* ParCompactionManager::_preserved_marks_set = nullptr;

ParCompactionManager::ParCompactionManager(PreservedMarks* preserved_marks,
                                           ReferenceProcessor* ref_processor)
  : _partial_array_stepper(ParallelGCThreads, ObjArrayMarkingStride),
    _mark_and_push_closure(this, ref_processor) {

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  _old_gen = heap->old_gen();
  _start_array = old_gen()->start_array();

  _preserved_marks = preserved_marks;
  _marking_stats_cache = nullptr;

 // Initialize to a bad value; fixed by initialize().
  _partial_array_state_allocator_index = UINT_MAX;

  TASKQUEUE_STATS_ONLY(reset_stats());
}

void ParCompactionManager::initialize(ParMarkBitMap* mbm) {
  assert(ParallelScavengeHeap::heap() != nullptr, "Needed for initialization");
  assert(PSParallelCompact::ref_processor() != nullptr, "precondition");
  assert(ParallelScavengeHeap::heap()->workers().max_workers() != 0, "Not initialized?");

  _mark_bitmap = mbm;

  uint parallel_gc_threads = ParallelScavengeHeap::heap()->workers().max_workers();

  assert(_manager_array == nullptr, "Attempt to initialize twice");
  _manager_array = NEW_C_HEAP_ARRAY(ParCompactionManager*, parallel_gc_threads, mtGC);

  assert(_partial_array_state_allocator == nullptr, "Attempt to initialize twice");
  _partial_array_state_allocator = new PartialArrayStateAllocator(ParallelGCThreads);
  _marking_stacks = new PSMarkTasksQueueSet(parallel_gc_threads);
  _region_task_queues = new RegionTaskQueueSet(parallel_gc_threads);

  _preserved_marks_set = new PreservedMarksSet(true);
  _preserved_marks_set->init(parallel_gc_threads);

  // Create and register the ParCompactionManager(s) for the worker threads.
  for(uint i=0; i<parallel_gc_threads; i++) {
    _manager_array[i] = new ParCompactionManager(_preserved_marks_set->get(i),
                                                 PSParallelCompact::ref_processor());
    marking_stacks()->register_queue(i, _manager_array[i]->marking_stack());
    region_task_queues()->register_queue(i, _manager_array[i]->region_stack());
    _manager_array[i]->_partial_array_state_allocator_index = i;
  }

  _shadow_region_array = new (mtGC) GrowableArray<size_t >(10, mtGC);

  _shadow_region_monitor = new Monitor(Mutex::nosafepoint, "CompactionManager_lock");
}

void ParCompactionManager::flush_all_string_dedup_requests() {
  uint parallel_gc_threads = ParallelScavengeHeap::heap()->workers().max_workers();
  for (uint i=0; i<parallel_gc_threads; i++) {
    _manager_array[i]->flush_string_dedup_requests();
  }
}

ParCompactionManager*
ParCompactionManager::gc_thread_compaction_manager(uint index) {
  assert(index < ParallelGCThreads, "index out of range");
  assert(_manager_array != nullptr, "Sanity");
  return _manager_array[index];
}

void ParCompactionManager::push_objArray(oop obj) {
  assert(obj->is_objArray(), "precondition");
  _mark_and_push_closure.do_klass(obj->klass());

  size_t array_length = objArrayOop(obj)->length();
  PartialArrayTaskStepper::Step step = _partial_array_stepper.start(array_length);
  if (step._ncreate > 0) {
    TASKQUEUE_STATS_ONLY(++_arrays_chunked);
    PartialArrayState* state =
    _partial_array_state_allocator->allocate(_partial_array_state_allocator_index,
                                             obj, nullptr,
                                             step._index,
                                             array_length,
                                             step._ncreate);
    for (uint i = 0; i < step._ncreate; ++i) {
      marking_stack()->push(ScannerTask(state));
    }
    TASKQUEUE_STATS_ONLY(_array_chunk_pushes += step._ncreate);
  }
  follow_array(objArrayOop(obj), 0, checked_cast<int>(step._index));
}

void ParCompactionManager::process_array_chunk(PartialArrayState* state) {
  TASKQUEUE_STATS_ONLY(++_array_chunks_processed);

  // Claim a chunk.  Push additional tasks before processing the claimed
  // chunk to allow other workers to steal while we're processing.
  PartialArrayTaskStepper::Step step = _partial_array_stepper.next(state);
  if (step._ncreate > 0) {
    state->add_references(step._ncreate);
    for (uint i = 0; i < step._ncreate; ++i) {
      push(state);
    }
    TASKQUEUE_STATS_ONLY(_array_chunk_pushes += step._ncreate);
  }
  int start = checked_cast<int>(step._index);
  int end = checked_cast<int>(step._index + _partial_array_stepper.chunk_size());
  assert(start < end, "invariant");
  follow_array(objArrayOop(state->source()), start, end);

  // Release reference to state, now that we're done with it.
  _partial_array_state_allocator->release(_partial_array_state_allocator_index, state);
}

void ParCompactionManager::follow_marking_stacks() {
  ScannerTask task;
  do {
    // First, try to move tasks from the overflow stack into the shared buffer, so
    // that other threads can steal. Otherwise process the overflow stack first.
    while (marking_stack()->pop_overflow(task)) {
      if (!marking_stack()->try_push_to_taskqueue(task)) {
        follow_contents(task);
      }
    }
    while (marking_stack()->pop_local(task)) {
      follow_contents(task);
    }
  } while (!marking_stack_empty());

  assert(marking_stack_empty(), "Sanity");
}

void ParCompactionManager::drain_region_stacks() {
  do {
    // Drain overflow stack first so other threads can steal.
    size_t region_index;
    while (region_stack()->pop_overflow(region_index)) {
      PSParallelCompact::fill_and_update_region(this, region_index);
    }

    while (region_stack()->pop_local(region_index)) {
      PSParallelCompact::fill_and_update_region(this, region_index);
    }
  } while (!region_stack()->is_empty());
}

size_t ParCompactionManager::pop_shadow_region_mt_safe(PSParallelCompact::RegionData* region_ptr) {
  MonitorLocker ml(_shadow_region_monitor, Mutex::_no_safepoint_check_flag);
  while (true) {
    if (!_shadow_region_array->is_empty()) {
      return _shadow_region_array->pop();
    }
    // Check if the corresponding heap region is available now.
    // If so, we don't need to get a shadow region anymore, and
    // we return InvalidShadow to indicate such a case.
    if (region_ptr->claimed()) {
      return InvalidShadow;
    }
    ml.wait(1);
  }
}

void ParCompactionManager::push_shadow_region_mt_safe(size_t shadow_region) {
  MonitorLocker ml(_shadow_region_monitor, Mutex::_no_safepoint_check_flag);
  _shadow_region_array->push(shadow_region);
  ml.notify();
}

void ParCompactionManager::push_shadow_region(size_t shadow_region) {
  _shadow_region_array->push(shadow_region);
}

void ParCompactionManager::remove_all_shadow_regions() {
  _shadow_region_array->clear();
}


#if TASKQUEUE_STATS
void ParCompactionManager::print_local_stats(outputStream* const out, uint i) const {
  #define FMT " " SIZE_FORMAT_W(10)
  out->print_cr("%3u" FMT FMT FMT FMT,
                i, _array_chunk_pushes, _array_chunk_steals,
                _arrays_chunked, _array_chunks_processed);
  #undef FMT
}

static const char* const pm_stats_hdr[] = {
  "    ----partial array----     arrays      array",
  "thr       push      steal    chunked     chunks",
  "--- ---------- ---------- ---------- ----------"
};

void ParCompactionManager::print_and_reset_taskqueue_stats() {
  if (!log_is_enabled(Trace, gc, task, stats)) {
    return;
  }
  Log(gc, task, stats) log;
  ResourceMark rm;
  LogStream ls(log.trace());

  marking_stacks()->print_and_reset_taskqueue_stats("Marking Stacks");

  const uint hlines = sizeof(pm_stats_hdr) / sizeof(pm_stats_hdr[0]);
  for (uint i = 0; i < hlines; ++i) ls.print_cr("%s", pm_stats_hdr[i]);
  for (uint i = 0; i < ParallelGCThreads; ++i) {
    _manager_array[i]->print_local_stats(&ls, i);
    _manager_array[i]->reset_stats();
  }
}

void ParCompactionManager::reset_stats() {
  _array_chunk_pushes = _array_chunk_steals = 0;
  _arrays_chunked = _array_chunks_processed = 0;
}
#endif // TASKQUEUE_STATS

#ifdef ASSERT
void ParCompactionManager::verify_all_marking_stack_empty() {
  uint parallel_gc_threads = ParallelGCThreads;
  for (uint i = 0; i < parallel_gc_threads; i++) {
    assert(_manager_array[i]->marking_stack_empty(), "Marking stack should be empty");
  }
}

void ParCompactionManager::verify_all_region_stack_empty() {
  uint parallel_gc_threads = ParallelGCThreads;
  for (uint i = 0; i < parallel_gc_threads; i++) {
    assert(_manager_array[i]->region_stack()->is_empty(), "Region stack should be empty");
  }
}
#endif
