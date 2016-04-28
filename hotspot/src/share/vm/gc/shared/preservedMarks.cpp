/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/gcTaskManager.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/workgroup.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"

void PreservedMarks::restore() {
  while (!_stack.is_empty()) {
    const OopAndMarkOop elem = _stack.pop();
    elem.set_mark();
  }
  assert_empty();
}

#ifndef PRODUCT
void PreservedMarks::assert_empty() {
  assert(_stack.is_empty(), "stack expected to be empty, size = "SIZE_FORMAT,
         _stack.size());
  assert(_stack.cache_size() == 0,
         "stack expected to have no cached segments, cache size = "SIZE_FORMAT,
         _stack.cache_size());
}
#endif // ndef PRODUCT

void RemoveForwardedPointerClosure::do_object(oop obj) {
  if (obj->is_forwarded()) {
    PreservedMarks::init_forwarded_mark(obj);
  }
}

void PreservedMarksSet::init(uint num) {
  assert(_stacks == NULL && _num == 0, "do not re-initialize");
  assert(num > 0, "pre-condition");
  if (_in_c_heap) {
    _stacks = NEW_C_HEAP_ARRAY(Padded<PreservedMarks>, num, mtGC);
  } else {
    _stacks = NEW_RESOURCE_ARRAY(Padded<PreservedMarks>, num);
  }
  for (uint i = 0; i < num; i += 1) {
    ::new (_stacks + i) PreservedMarks();
  }
  _num = num;

  assert_empty();
}

class ParRestoreTask : public AbstractGangTask {
private:
  PreservedMarksSet* const _preserved_marks_set;
  SequentialSubTasksDone _sub_tasks;
  volatile size_t* const _total_size_addr;

public:
  virtual void work(uint worker_id) {
    uint task_id = 0;
    while (!_sub_tasks.is_task_claimed(/* reference */ task_id)) {
      PreservedMarks* const preserved_marks = _preserved_marks_set->get(task_id);
      const size_t size = preserved_marks->size();
      preserved_marks->restore();
      // Only do the atomic add if the size is > 0.
      if (size > 0) {
        Atomic::add(size, _total_size_addr);
      }
    }
    _sub_tasks.all_tasks_completed();
  }

  ParRestoreTask(uint worker_num,
                 PreservedMarksSet* preserved_marks_set,
                 volatile size_t* total_size_addr)
      : AbstractGangTask("Parallel Preserved Mark Restoration"),
        _preserved_marks_set(preserved_marks_set),
        _total_size_addr(total_size_addr) {
    _sub_tasks.set_n_threads(worker_num);
    _sub_tasks.set_n_tasks(preserved_marks_set->num());
  }
};

void PreservedMarksSet::restore_internal(WorkGang* workers,
                                         volatile size_t* total_size_addr) {
  assert(workers != NULL, "pre-condition");
  ParRestoreTask task(workers->active_workers(), this, total_size_addr);
  workers->run_task(&task);
}

class ParRestoreGCTask : public GCTask {
private:
  const uint _id;
  PreservedMarksSet* const _preserved_marks_set;
  volatile size_t* const _total_size_addr;

public:
  virtual char* name() { return (char*) "preserved mark restoration task"; }

  virtual void do_it(GCTaskManager* manager, uint which) {
    PreservedMarks* const preserved_marks = _preserved_marks_set->get(_id);
    const size_t size = preserved_marks->size();
    preserved_marks->restore();
    // Only do the atomic add if the size is > 0.
    if (size > 0) {
      Atomic::add(size, _total_size_addr);
    }
  }

  ParRestoreGCTask(uint id,
                   PreservedMarksSet* preserved_marks_set,
                   volatile size_t* total_size_addr)
    : _id(id),
      _preserved_marks_set(preserved_marks_set),
      _total_size_addr(total_size_addr) { }
};

void PreservedMarksSet::restore_internal(GCTaskManager* gc_task_manager,
                                         volatile size_t* total_size_addr) {
  // GCTask / GCTaskQueue are ResourceObjs
  ResourceMark rm;

  GCTaskQueue* q = GCTaskQueue::create();
  for (uint i = 0; i < num(); i += 1) {
    q->enqueue(new ParRestoreGCTask(i, this, total_size_addr));
  }
  gc_task_manager->execute_and_wait(q);
}

void PreservedMarksSet::reclaim() {
  assert_empty();

  for (uint i = 0; i < _num; i += 1) {
    _stacks[i].~Padded<PreservedMarks>();
  }

  if (_in_c_heap) {
    FREE_C_HEAP_ARRAY(Padded<PreservedMarks>, _stacks);
  } else {
    // the array was resource-allocated, so nothing to do
  }
  _stacks = NULL;
  _num = 0;
}

#ifndef PRODUCT
void PreservedMarksSet::assert_empty() {
  assert(_stacks != NULL && _num > 0, "should have been initialized");
  for (uint i = 0; i < _num; i += 1) {
    get(i)->assert_empty();
  }
}
#endif // ndef PRODUCT
