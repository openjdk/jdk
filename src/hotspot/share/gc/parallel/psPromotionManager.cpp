/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/javaClasses.inline.hpp"
#include "gc/parallel/mutableSpace.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psOldGen.hpp"
#include "gc/parallel/psPromotionManager.inline.hpp"
#include "gc/parallel/psScavenge.inline.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/partialArraySplitter.inline.hpp"
#include "gc/shared/partialArrayState.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/memRegion.hpp"
#include "memory/padded.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "utilities/checkedCast.hpp"

PaddedEnd<PSPromotionManager>* PSPromotionManager::_manager_array = nullptr;
PSPromotionManager::PSScannerTasksQueueSet* PSPromotionManager::_stack_array_depth = nullptr;
PreservedMarksSet*             PSPromotionManager::_preserved_marks_set = nullptr;
PSOldGen*                      PSPromotionManager::_old_gen = nullptr;
MutableSpace*                  PSPromotionManager::_young_space = nullptr;
PartialArrayStateManager*      PSPromotionManager::_partial_array_state_manager = nullptr;

void PSPromotionManager::initialize() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  _old_gen = heap->old_gen();
  _young_space = heap->young_gen()->to_space();

  const uint promotion_manager_num = ParallelGCThreads;

  assert(_partial_array_state_manager == nullptr, "Attempt to initialize twice");
  _partial_array_state_manager
    = new PartialArrayStateManager(promotion_manager_num);

  // To prevent false sharing, we pad the PSPromotionManagers
  // and make sure that the first instance starts at a cache line.
  assert(_manager_array == nullptr, "Attempt to initialize twice");
  _manager_array = PaddedArray<PSPromotionManager, mtGC>::create_unfreeable(promotion_manager_num);

  _stack_array_depth = new PSScannerTasksQueueSet(promotion_manager_num);

  // Create and register the PSPromotionManager(s) for the worker threads.
  for(uint i=0; i<ParallelGCThreads; i++) {
    stack_array_depth()->register_queue(i, _manager_array[i].claimed_stack_depth());
  }
  // The VMThread gets its own PSPromotionManager, which is not available
  // for work stealing.

  assert(_preserved_marks_set == nullptr, "Attempt to initialize twice");
  _preserved_marks_set = new PreservedMarksSet(true /* in_c_heap */);
  _preserved_marks_set->init(promotion_manager_num);
  for (uint i = 0; i < promotion_manager_num; i += 1) {
    _manager_array[i].register_preserved_marks(_preserved_marks_set->get(i));
  }
}

// Helper functions to get around the circular dependency between
// psScavenge.inline.hpp and psPromotionManager.inline.hpp.
bool PSPromotionManager::should_scavenge(oop* p, bool check_to_space) {
  return PSScavenge::should_scavenge(p, check_to_space);
}
bool PSPromotionManager::should_scavenge(narrowOop* p, bool check_to_space) {
  return PSScavenge::should_scavenge(p, check_to_space);
}

PSPromotionManager* PSPromotionManager::gc_thread_promotion_manager(uint index) {
  assert(index < ParallelGCThreads, "index out of range");
  assert(_manager_array != nullptr, "Sanity");
  return &_manager_array[index];
}

PSPromotionManager* PSPromotionManager::vm_thread_promotion_manager() {
  assert(_manager_array != nullptr, "Sanity");
  return &_manager_array[0];
}

void PSPromotionManager::pre_scavenge() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  _preserved_marks_set->assert_empty();
  _young_space = heap->young_gen()->to_space();

  for(uint i=0; i<ParallelGCThreads; i++) {
    manager_array(i)->reset();
  }
}

bool PSPromotionManager::post_scavenge(YoungGCTracer& gc_tracer) {
  bool promotion_failure_occurred = false;

  TASKQUEUE_STATS_ONLY(print_and_reset_taskqueue_stats());
  for (uint i = 0; i < ParallelGCThreads; i++) {
    PSPromotionManager* manager = manager_array(i);
    assert(manager->claimed_stack_depth()->is_empty(), "should be empty");
    if (manager->_promotion_failed_info.has_failed()) {
      gc_tracer.report_promotion_failed(manager->_promotion_failed_info);
      promotion_failure_occurred = true;
    }
    manager->flush_labs();
    manager->flush_string_dedup_requests();
  }
  // All PartialArrayStates have been returned to the allocator, since the
  // claimed_stack_depths are all empty.  Leave them there for use by future
  // collections.

  if (!promotion_failure_occurred) {
    // If there was no promotion failure, the preserved mark stacks
    // should be empty.
    _preserved_marks_set->assert_empty();
  }
  return promotion_failure_occurred;
}

#if TASKQUEUE_STATS

void PSPromotionManager::print_and_reset_taskqueue_stats() {
  stack_array_depth()->print_and_reset_taskqueue_stats("Oop Queue");

  auto get_pa_stats = [&](uint i) {
    return manager_array(i)->partial_array_task_stats();
  };
  PartialArrayTaskStats::log_set(ParallelGCThreads, get_pa_stats,
                                 "Partial Array Task Stats");
  for (uint i = 0; i < ParallelGCThreads; ++i) {
    get_pa_stats(i)->reset();
  }
}

PartialArrayTaskStats* PSPromotionManager::partial_array_task_stats() {
  return _partial_array_splitter.stats();
}

#endif // TASKQUEUE_STATS

// Most members are initialized either by initialize() or reset().
PSPromotionManager::PSPromotionManager()
  : _partial_array_splitter(_partial_array_state_manager, ParallelGCThreads, ParGCArrayScanChunk)
{
  // We set the old lab's start array.
  _old_lab.set_start_array(old_gen()->start_array());

  if (ParallelGCThreads == 1) {
    _target_stack_size = 0;
  } else {
    _target_stack_size = GCDrainStackTargetSize;
  }

  // let's choose 1.5x the chunk size
  _min_array_size_for_chunking = (3 * ParGCArrayScanChunk / 2);

  _preserved_marks = nullptr;

  reset();
}

void PSPromotionManager::reset() {
  assert(stacks_empty(), "reset of non-empty stack");

  // We need to get an assert in here to make sure the labs are always flushed.

  // Do not prefill the LAB's, save heap wastage!
  HeapWord* lab_base = young_space()->top();
  _young_lab.initialize(MemRegion(lab_base, (size_t)0));
  _young_gen_has_alloc_failure = false;
  _young_gen_is_full = false;

  lab_base = old_gen()->object_space()->top();
  _old_lab.initialize(MemRegion(lab_base, (size_t)0));
  _old_gen_is_full = false;

  _promotion_failed_info.reset();
}

void PSPromotionManager::register_preserved_marks(PreservedMarks* preserved_marks) {
  assert(_preserved_marks == nullptr, "do not set it twice");
  _preserved_marks = preserved_marks;
}

void PSPromotionManager::restore_preserved_marks() {
  _preserved_marks_set->restore(&ParallelScavengeHeap::heap()->workers());
}

void PSPromotionManager::drain_stacks_depth(bool totally_drain) {
  const uint threshold = totally_drain ? 0
                                       : _target_stack_size;

  PSScannerTasksQueue* const tq = claimed_stack_depth();
  do {
    ScannerTask task;

    // Drain overflow stack first, so other threads can steal from
    // claimed stack while we work.
    while (tq->pop_overflow(task)) {
      if (!tq->try_push_to_taskqueue(task)) {
        process_popped_location_depth(task, false);
      }
    }

    while (tq->pop_local(task, threshold)) {
      process_popped_location_depth(task, false);
    }
  } while (!tq->overflow_empty());

  assert(!totally_drain || tq->taskqueue_empty(), "Sanity");
  assert(totally_drain || tq->size() <= _target_stack_size, "Sanity");
  assert(tq->overflow_empty(), "Sanity");
}

void PSPromotionManager::flush_labs() {
  assert(stacks_empty(), "Attempt to flush lab with live stack");

  // If either promotion lab fills up, we can flush the
  // lab but not refill it, so check first.
  assert(!_young_lab.is_flushed() || _young_gen_is_full, "Sanity");
  if (!_young_lab.is_flushed())
    _young_lab.flush();

  assert(!_old_lab.is_flushed() || _old_gen_is_full, "Sanity");
  if (!_old_lab.is_flushed())
    _old_lab.flush();

  // Let PSScavenge know if we overflowed
  if (_young_gen_is_full || _young_gen_has_alloc_failure) {
    PSScavenge::set_survivor_overflow(true);
  }
}

template <class T>
void PSPromotionManager::process_array_chunk_work(oop obj, int start, int end) {
  assert(start <= end, "invariant");
  T* const base      = (T*)objArrayOop(obj)->base();
  T* p               = base + start;
  T* const chunk_end = base + end;
  while (p < chunk_end) {
    claim_or_forward_depth(p);
    ++p;
  }
}

void PSPromotionManager::process_array_chunk(PartialArrayState* state, bool stolen) {
  // Access before release by claim().
  oop new_obj = state->destination();
  PartialArraySplitter::Claim claim =
    _partial_array_splitter.claim(state, &_claimed_stack_depth, stolen);
  int start = checked_cast<int>(claim._start);
  int end = checked_cast<int>(claim._end);
  if (UseCompressedOops) {
    process_array_chunk_work<narrowOop>(new_obj, start, end);
  } else {
    process_array_chunk_work<oop>(new_obj, start, end);
  }
}

void PSPromotionManager::push_objArray(oop old_obj, oop new_obj) {
  assert(old_obj->is_forwarded(), "precondition");
  assert(old_obj->forwardee() == new_obj, "precondition");
  assert(new_obj->is_objArray(), "precondition");

  objArrayOop to_array = objArrayOop(new_obj);
  size_t array_length = to_array->length();
  size_t initial_chunk_size =
    // The source array is unused when processing states.
    _partial_array_splitter.start(&_claimed_stack_depth, nullptr, to_array, array_length);
  int end = checked_cast<int>(initial_chunk_size);
  if (UseCompressedOops) {
    process_array_chunk_work<narrowOop>(to_array, 0, end);
  } else {
    process_array_chunk_work<oop>(to_array, 0, end);
  }
}

oop PSPromotionManager::oop_promotion_failed(oop obj, markWord obj_mark) {
  assert(_old_gen_is_full || PromotionFailureALot, "Sanity");

  // Attempt to CAS in the header.
  // This tests if the header is still the same as when
  // this started.  If it is the same (i.e., no forwarding
  // pointer has been installed), then this thread owns
  // it.
  if (obj->forward_to_self_atomic(obj_mark) == nullptr) {
    // We won any races, we "own" this object.
    assert(obj == obj->forwardee(), "Sanity");

    _promotion_failed_info.register_copy_failure(obj->size());

    ContinuationGCSupport::transform_stack_chunk(obj);

    push_contents(obj);

    // Save the markWord of promotion-failed objs in _preserved_marks for later
    // restoration. This way we don't have to walk the young-gen to locate
    // these promotion-failed objs.
    _preserved_marks->push_always(obj, obj_mark);
  }  else {
    // We lost, someone else "owns" this object
    guarantee(obj->is_forwarded(), "Object must be forwarded if the cas failed.");

    // No unallocation to worry about.
    obj = obj->forwardee();
  }

  return obj;
}
