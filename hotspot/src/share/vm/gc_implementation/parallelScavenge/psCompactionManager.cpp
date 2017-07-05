/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_psCompactionManager.cpp.incl"

PSOldGen*            ParCompactionManager::_old_gen = NULL;
ParCompactionManager**  ParCompactionManager::_manager_array = NULL;
OopTaskQueueSet*     ParCompactionManager::_stack_array = NULL;
ObjectStartArray*    ParCompactionManager::_start_array = NULL;
ParMarkBitMap*       ParCompactionManager::_mark_bitmap = NULL;
ChunkTaskQueueSet*   ParCompactionManager::_chunk_array = NULL;

ParCompactionManager::ParCompactionManager() :
    _action(CopyAndUpdate) {

  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");

  _old_gen = heap->old_gen();
  _start_array = old_gen()->start_array();


  marking_stack()->initialize();

  // We want the overflow stack to be permanent
  _overflow_stack = new (ResourceObj::C_HEAP) GrowableArray<oop>(10, true);
#ifdef USE_ChunkTaskQueueWithOverflow
  chunk_stack()->initialize();
#else
  chunk_stack()->initialize();

  // We want the overflow stack to be permanent
  _chunk_overflow_stack =
    new (ResourceObj::C_HEAP) GrowableArray<size_t>(10, true);
#endif

  // Note that _revisit_klass_stack is allocated out of the
  // C heap (as opposed to out of ResourceArena).
  int size =
    (SystemDictionary::number_of_classes() * 2) * 2 / ParallelGCThreads;
  _revisit_klass_stack = new (ResourceObj::C_HEAP) GrowableArray<Klass*>(size, true);

}

ParCompactionManager::~ParCompactionManager() {
  delete _overflow_stack;
  delete _revisit_klass_stack;
  // _manager_array and _stack_array are statics
  // shared with all instances of ParCompactionManager
  // should not be deallocated.
}

void ParCompactionManager::initialize(ParMarkBitMap* mbm) {
  assert(PSParallelCompact::gc_task_manager() != NULL,
    "Needed for initialization");

  _mark_bitmap = mbm;

  uint parallel_gc_threads = PSParallelCompact::gc_task_manager()->workers();

  assert(_manager_array == NULL, "Attempt to initialize twice");
  _manager_array = NEW_C_HEAP_ARRAY(ParCompactionManager*, parallel_gc_threads+1 );
  guarantee(_manager_array != NULL, "Could not initialize promotion manager");

  _stack_array = new OopTaskQueueSet(parallel_gc_threads);
  guarantee(_stack_array != NULL, "Count not initialize promotion manager");
  _chunk_array = new ChunkTaskQueueSet(parallel_gc_threads);
  guarantee(_chunk_array != NULL, "Count not initialize promotion manager");

  // Create and register the ParCompactionManager(s) for the worker threads.
  for(uint i=0; i<parallel_gc_threads; i++) {
    _manager_array[i] = new ParCompactionManager();
    guarantee(_manager_array[i] != NULL, "Could not create ParCompactionManager");
    stack_array()->register_queue(i, _manager_array[i]->marking_stack());
#ifdef USE_ChunkTaskQueueWithOverflow
    chunk_array()->register_queue(i, _manager_array[i]->chunk_stack()->task_queue());
#else
    chunk_array()->register_queue(i, _manager_array[i]->chunk_stack());
#endif
  }

  // The VMThread gets its own ParCompactionManager, which is not available
  // for work stealing.
  _manager_array[parallel_gc_threads] = new ParCompactionManager();
  guarantee(_manager_array[parallel_gc_threads] != NULL,
    "Could not create ParCompactionManager");
  assert(PSParallelCompact::gc_task_manager()->workers() != 0,
    "Not initialized?");
}

bool ParCompactionManager::should_update() {
  assert(action() != NotValid, "Action is not set");
  return (action() == ParCompactionManager::Update) ||
         (action() == ParCompactionManager::CopyAndUpdate) ||
         (action() == ParCompactionManager::UpdateAndCopy);
}

bool ParCompactionManager::should_copy() {
  assert(action() != NotValid, "Action is not set");
  return (action() == ParCompactionManager::Copy) ||
         (action() == ParCompactionManager::CopyAndUpdate) ||
         (action() == ParCompactionManager::UpdateAndCopy);
}

bool ParCompactionManager::should_verify_only() {
  assert(action() != NotValid, "Action is not set");
  return action() == ParCompactionManager::VerifyUpdate;
}

bool ParCompactionManager::should_reset_only() {
  assert(action() != NotValid, "Action is not set");
  return action() == ParCompactionManager::ResetObjects;
}

// For now save on a stack
void ParCompactionManager::save_for_scanning(oop m) {
  stack_push(m);
}

void ParCompactionManager::stack_push(oop obj) {

  if(!marking_stack()->push(obj)) {
    overflow_stack()->push(obj);
  }
}

oop ParCompactionManager::retrieve_for_scanning() {

  // Should not be used in the parallel case
  ShouldNotReachHere();
  return NULL;
}

// Save chunk on a stack
void ParCompactionManager::save_for_processing(size_t chunk_index) {
#ifdef ASSERT
  const ParallelCompactData& sd = PSParallelCompact::summary_data();
  ParallelCompactData::ChunkData* const chunk_ptr = sd.chunk(chunk_index);
  assert(chunk_ptr->claimed(), "must be claimed");
  assert(chunk_ptr->_pushed++ == 0, "should only be pushed once");
#endif
  chunk_stack_push(chunk_index);
}

void ParCompactionManager::chunk_stack_push(size_t chunk_index) {

#ifdef USE_ChunkTaskQueueWithOverflow
  chunk_stack()->save(chunk_index);
#else
  if(!chunk_stack()->push(chunk_index)) {
    chunk_overflow_stack()->push(chunk_index);
  }
#endif
}

bool ParCompactionManager::retrieve_for_processing(size_t& chunk_index) {
#ifdef USE_ChunkTaskQueueWithOverflow
  return chunk_stack()->retrieve(chunk_index);
#else
  // Should not be used in the parallel case
  ShouldNotReachHere();
  return false;
#endif
}

ParCompactionManager*
ParCompactionManager::gc_thread_compaction_manager(int index) {
  assert(index >= 0 && index < (int)ParallelGCThreads, "index out of range");
  assert(_manager_array != NULL, "Sanity");
  return _manager_array[index];
}

void ParCompactionManager::reset() {
  for(uint i=0; i<ParallelGCThreads+1; i++) {
    manager_array(i)->revisit_klass_stack()->clear();
  }
}

void ParCompactionManager::drain_marking_stacks(OopClosure* blk) {
#ifdef ASSERT
  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");
  MutableSpace* to_space = heap->young_gen()->to_space();
  MutableSpace* old_space = heap->old_gen()->object_space();
  MutableSpace* perm_space = heap->perm_gen()->object_space();
#endif /* ASSERT */


  do {

    // Drain overflow stack first, so other threads can steal from
    // claimed stack while we work.
    while(!overflow_stack()->is_empty()) {
      oop obj = overflow_stack()->pop();
      obj->follow_contents(this);
    }

    oop obj;
    // obj is a reference!!!
    while (marking_stack()->pop_local(obj)) {
      // It would be nice to assert about the type of objects we might
      // pop, but they can come from anywhere, unfortunately.
      obj->follow_contents(this);
    }
  } while((marking_stack()->size() != 0) || (overflow_stack()->length() != 0));

  assert(marking_stack()->size() == 0, "Sanity");
  assert(overflow_stack()->length() == 0, "Sanity");
}

void ParCompactionManager::drain_chunk_overflow_stack() {
  size_t chunk_index = (size_t) -1;
  while(chunk_stack()->retrieve_from_overflow(chunk_index)) {
    PSParallelCompact::fill_and_update_chunk(this, chunk_index);
  }
}

void ParCompactionManager::drain_chunk_stacks() {
#ifdef ASSERT
  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");
  MutableSpace* to_space = heap->young_gen()->to_space();
  MutableSpace* old_space = heap->old_gen()->object_space();
  MutableSpace* perm_space = heap->perm_gen()->object_space();
#endif /* ASSERT */

#if 1 // def DO_PARALLEL - the serial code hasn't been updated
  do {

#ifdef USE_ChunkTaskQueueWithOverflow
    // Drain overflow stack first, so other threads can steal from
    // claimed stack while we work.
    size_t chunk_index = (size_t) -1;
    while(chunk_stack()->retrieve_from_overflow(chunk_index)) {
      PSParallelCompact::fill_and_update_chunk(this, chunk_index);
    }

    while (chunk_stack()->retrieve_from_stealable_queue(chunk_index)) {
      PSParallelCompact::fill_and_update_chunk(this, chunk_index);
    }
  } while (!chunk_stack()->is_empty());
#else
    // Drain overflow stack first, so other threads can steal from
    // claimed stack while we work.
    while(!chunk_overflow_stack()->is_empty()) {
      size_t chunk_index = chunk_overflow_stack()->pop();
      PSParallelCompact::fill_and_update_chunk(this, chunk_index);
    }

    size_t chunk_index = -1;
    // obj is a reference!!!
    while (chunk_stack()->pop_local(chunk_index)) {
      // It would be nice to assert about the type of objects we might
      // pop, but they can come from anywhere, unfortunately.
      PSParallelCompact::fill_and_update_chunk(this, chunk_index);
    }
  } while((chunk_stack()->size() != 0) ||
          (chunk_overflow_stack()->length() != 0));
#endif

#ifdef USE_ChunkTaskQueueWithOverflow
  assert(chunk_stack()->is_empty(), "Sanity");
#else
  assert(chunk_stack()->size() == 0, "Sanity");
  assert(chunk_overflow_stack()->length() == 0, "Sanity");
#endif
#else
  oop obj;
  while (obj = retrieve_for_scanning()) {
    obj->follow_contents(this);
  }
#endif
}

#ifdef ASSERT
bool ParCompactionManager::stacks_have_been_allocated() {
  return (revisit_klass_stack()->data_addr() != NULL);
}
#endif
