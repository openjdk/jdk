/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSCOMPACTIONMANAGER_HPP
#define SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSCOMPACTIONMANAGER_HPP

#include "memory/allocation.hpp"
#include "utilities/stack.hpp"
#include "utilities/taskqueue.hpp"

// Move to some global location
#define HAS_BEEN_MOVED 0x1501d01d
// End move to some global location


class MutableSpace;
class PSOldGen;
class ParCompactionManager;
class ObjectStartArray;
class ParallelCompactData;
class ParMarkBitMap;

class ParCompactionManager : public CHeapObj<mtGC> {
  friend class ParallelTaskTerminator;
  friend class ParMarkBitMap;
  friend class PSParallelCompact;
  friend class StealRegionCompactionTask;
  friend class UpdateAndFillClosure;
  friend class RefProcTaskExecutor;
  friend class IdleGCTask;

 public:

// ------------------------  Don't putback if not needed
  // Actions that the compaction manager should take.
  enum Action {
    Update,
    Copy,
    UpdateAndCopy,
    CopyAndUpdate,
    NotValid
  };
// ------------------------  End don't putback if not needed

 private:
  // 32-bit:  4K * 8 = 32KiB; 64-bit:  8K * 16 = 128KiB
  #define QUEUE_SIZE (1 << NOT_LP64(12) LP64_ONLY(13))
  typedef OverflowTaskQueue<ObjArrayTask, mtGC, QUEUE_SIZE> ObjArrayTaskQueue;
  typedef GenericTaskQueueSet<ObjArrayTaskQueue, mtGC>      ObjArrayTaskQueueSet;
  #undef QUEUE_SIZE

  static ParCompactionManager** _manager_array;
  static OopTaskQueueSet*       _stack_array;
  static ObjArrayTaskQueueSet*  _objarray_queues;
  static ObjectStartArray*      _start_array;
  static RegionTaskQueueSet*    _region_array;
  static PSOldGen*              _old_gen;

private:
  OverflowTaskQueue<oop, mtGC>        _marking_stack;
  ObjArrayTaskQueue             _objarray_stack;

  // Is there a way to reuse the _marking_stack for the
  // saving empty regions?  For now just create a different
  // type of TaskQueue.
  RegionTaskQueue*             _region_stack;

  static RegionTaskQueue**     _region_list;
  // Index in _region_list for current _region_stack.
  uint _region_stack_index;

  // Indexes of recycled region stacks/overflow stacks
  // Stacks of regions to be compacted are embedded in the tasks doing
  // the compaction.  A thread that executes the task extracts the
  // region stack and drains it.  These threads keep these region
  // stacks for use during compaction task stealing.  If a thread
  // gets a second draining task, it pushed its current region stack
  // index into the array _recycled_stack_index and gets a new
  // region stack from the task.  A thread that is executing a
  // compaction stealing task without ever having executing a
  // draining task, will get a region stack from _recycled_stack_index.
  //
  // Array of indexes into the array of region stacks.
  static uint*                    _recycled_stack_index;
  // The index into _recycled_stack_index of the last region stack index
  // pushed.  If -1, there are no entries into _recycled_stack_index.
  static int                      _recycled_top;
  // The index into _recycled_stack_index of the last region stack index
  // popped.  If -1, there has not been any entry popped.
  static int                      _recycled_bottom;

  static ParMarkBitMap* _mark_bitmap;

  Action _action;

  static PSOldGen* old_gen()             { return _old_gen; }
  static ObjectStartArray* start_array() { return _start_array; }
  static OopTaskQueueSet* stack_array()  { return _stack_array; }

  static void initialize(ParMarkBitMap* mbm);

 protected:
  // Array of tasks.  Needed by the ParallelTaskTerminator.
  static RegionTaskQueueSet* region_array()      { return _region_array; }
  OverflowTaskQueue<oop, mtGC>*  marking_stack()       { return &_marking_stack; }

  // Pushes onto the marking stack.  If the marking stack is full,
  // pushes onto the overflow stack.
  void stack_push(oop obj);
  // Do not implement an equivalent stack_pop.  Deal with the
  // marking stack and overflow stack directly.

 public:
  Action action() { return _action; }
  void set_action(Action v) { _action = v; }

  RegionTaskQueue* region_stack()                { return _region_stack; }
  void set_region_stack(RegionTaskQueue* v)       { _region_stack = v; }

  inline static ParCompactionManager* manager_array(int index);

  inline static RegionTaskQueue* region_list(int index) {
    return _region_list[index];
  }

  uint region_stack_index() { return _region_stack_index; }
  void set_region_stack_index(uint v) { _region_stack_index = v; }

  // Pop and push unique reusable stack index
  static int pop_recycled_stack_index();
  static void push_recycled_stack_index(uint v);
  static void reset_recycled_stack_index() {
    _recycled_bottom = _recycled_top = -1;
  }

  ParCompactionManager();
  ~ParCompactionManager();

  // Pushes onto the region stack at the given index.  If the
  // region stack is full,
  // pushes onto the region overflow stack.
  static void region_list_push(uint stack_index, size_t region_index);
  static void verify_region_list_empty(uint stack_index);
  ParMarkBitMap* mark_bitmap() { return _mark_bitmap; }

  // void drain_stacks();

  bool should_update();
  bool should_copy();

  // Save for later processing.  Must not fail.
  inline void push(oop obj) { _marking_stack.push(obj); }
  inline void push_objarray(oop objarray, size_t index);
  inline void push_region(size_t index);

  // Access function for compaction managers
  static ParCompactionManager* gc_thread_compaction_manager(int index);

  static bool steal(int queue_num, int* seed, oop& t) {
    return stack_array()->steal(queue_num, seed, t);
  }

  static bool steal_objarray(int queue_num, int* seed, ObjArrayTask& t) {
    return _objarray_queues->steal(queue_num, seed, t);
  }

  static bool steal(int queue_num, int* seed, size_t& region) {
    return region_array()->steal(queue_num, seed, region);
  }

  // Process tasks remaining on any marking stack
  void follow_marking_stacks();
  inline bool marking_stacks_empty() const;

  // Process tasks remaining on any stack
  void drain_region_stacks();

};

inline ParCompactionManager* ParCompactionManager::manager_array(int index) {
  assert(_manager_array != NULL, "access of NULL manager_array");
  assert(index >= 0 && index <= (int)ParallelGCThreads,
    "out of range manager_array access");
  return _manager_array[index];
}

bool ParCompactionManager::marking_stacks_empty() const {
  return _marking_stack.is_empty() && _objarray_stack.is_empty();
}

#endif // SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSCOMPACTIONMANAGER_HPP
