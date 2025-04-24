/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/parallel/objectStartArray.hpp"
#include "gc/parallel/parMarkBitMap.inline.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psCompactionManagerNew.inline.hpp"
#include "gc/parallel/psOldGen.hpp"
#include "gc/parallel/psParallelCompactNew.inline.hpp"
#include "gc/shared/partialArraySplitter.inline.hpp"
#include "gc/shared/partialArrayState.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "memory/iterator.inline.hpp"

PSOldGen*                  ParCompactionManagerNew::_old_gen = nullptr;
ParCompactionManagerNew**  ParCompactionManagerNew::_manager_array = nullptr;

ParCompactionManagerNew::PSMarkTasksQueueSet*  ParCompactionManagerNew::_marking_stacks = nullptr;
PartialArrayStateManager* ParCompactionManagerNew::_partial_array_state_manager = nullptr;

ObjectStartArray*    ParCompactionManagerNew::_start_array = nullptr;
ParMarkBitMap*       ParCompactionManagerNew::_mark_bitmap = nullptr;

PreservedMarksSet* ParCompactionManagerNew::_preserved_marks_set = nullptr;

ParCompactionManagerNew::ParCompactionManagerNew(PreservedMarks* preserved_marks,
                                           ReferenceProcessor* ref_processor,
                                           uint parallel_gc_threads)
  :_partial_array_splitter(_partial_array_state_manager, parallel_gc_threads),
   _mark_and_push_closure(this, ref_processor) {

  _old_gen = ParallelScavengeHeap::old_gen();
  _start_array = old_gen()->start_array();

  _preserved_marks = preserved_marks;
}

void ParCompactionManagerNew::initialize(ParMarkBitMap* mbm) {
  assert(ParallelScavengeHeap::heap() != nullptr, "Needed for initialization");
  assert(PSParallelCompactNew::ref_processor() != nullptr, "precondition");
  assert(ParallelScavengeHeap::heap()->workers().max_workers() != 0, "Not initialized?");

  _mark_bitmap = mbm;

  uint parallel_gc_threads = ParallelScavengeHeap::heap()->workers().max_workers();

  assert(_manager_array == nullptr, "Attempt to initialize twice");
  _manager_array = NEW_C_HEAP_ARRAY(ParCompactionManagerNew*, parallel_gc_threads, mtGC);

  assert(_partial_array_state_manager == nullptr, "Attempt to initialize twice");
  _partial_array_state_manager
    = new PartialArrayStateManager(parallel_gc_threads);
  _marking_stacks = new PSMarkTasksQueueSet(parallel_gc_threads);

  _preserved_marks_set = new PreservedMarksSet(true);
  _preserved_marks_set->init(parallel_gc_threads);

  // Create and register the ParCompactionManagerNew(s) for the worker threads.
  for(uint i=0; i<parallel_gc_threads; i++) {
    _manager_array[i] = new ParCompactionManagerNew(_preserved_marks_set->get(i),
                                                 PSParallelCompactNew::ref_processor(),
                                                 parallel_gc_threads);
    marking_stacks()->register_queue(i, _manager_array[i]->marking_stack());
  }
}

void ParCompactionManagerNew::flush_all_string_dedup_requests() {
  uint parallel_gc_threads = ParallelScavengeHeap::heap()->workers().max_workers();
  for (uint i=0; i<parallel_gc_threads; i++) {
    _manager_array[i]->flush_string_dedup_requests();
  }
}

ParCompactionManagerNew*
ParCompactionManagerNew::gc_thread_compaction_manager(uint index) {
  assert(index < ParallelGCThreads, "index out of range");
  assert(_manager_array != nullptr, "Sanity");
  return _manager_array[index];
}

void ParCompactionManagerNew::push_objArray(oop obj) {
  assert(obj->is_objArray(), "precondition");
  _mark_and_push_closure.do_klass(obj->klass());

  objArrayOop obj_array = objArrayOop(obj);
  size_t array_length = obj_array->length();
  size_t initial_chunk_size =
    _partial_array_splitter.start(&_marking_stack, obj_array, nullptr, array_length);
  follow_array(obj_array, 0, initial_chunk_size);
}

void ParCompactionManagerNew::process_array_chunk(PartialArrayState* state, bool stolen) {
  // Access before release by claim().
  oop obj = state->source();
  PartialArraySplitter::Claim claim =
    _partial_array_splitter.claim(state, &_marking_stack, stolen);
  follow_array(objArrayOop(obj), claim._start, claim._end);
}

void ParCompactionManagerNew::follow_marking_stacks() {
  ScannerTask task;
  do {
    // First, try to move tasks from the overflow stack into the shared buffer, so
    // that other threads can steal. Otherwise process the overflow stack first.
    while (marking_stack()->pop_overflow(task)) {
      if (!marking_stack()->try_push_to_taskqueue(task)) {
        follow_contents(task, false);
      }
    }
    while (marking_stack()->pop_local(task)) {
      follow_contents(task, false);
    }
  } while (!marking_stack_empty());

  assert(marking_stack_empty(), "Sanity");
}

#if TASKQUEUE_STATS
void ParCompactionManagerNew::print_and_reset_taskqueue_stats() {
  marking_stacks()->print_and_reset_taskqueue_stats("Marking Stacks");

  auto get_pa_stats = [&](uint i) {
    return _manager_array[i]->partial_array_task_stats();
  };
  PartialArrayTaskStats::log_set(ParallelGCThreads, get_pa_stats,
                                 "Partial Array Task Stats");
  uint parallel_gc_threads = ParallelScavengeHeap::heap()->workers().max_workers();
  for (uint i = 0; i < parallel_gc_threads; ++i) {
    get_pa_stats(i)->reset();
  }
}

PartialArrayTaskStats* ParCompactionManagerNew::partial_array_task_stats() {
  return _partial_array_splitter.stats();
}
#endif // TASKQUEUE_STATS

#ifdef ASSERT
void ParCompactionManagerNew::verify_all_marking_stack_empty() {
  uint parallel_gc_threads = ParallelGCThreads;
  for (uint i = 0; i < parallel_gc_threads; i++) {
    assert(_manager_array[i]->marking_stack_empty(), "Marking stack should be empty");
  }
}
#endif
