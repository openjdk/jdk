/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_PSCOMPACTIONMANAGERNEW_HPP
#define SHARE_GC_PARALLEL_PSCOMPACTIONMANAGERNEW_HPP

#include "classfile/classLoaderData.hpp"
#include "gc/parallel/psParallelCompactNew.hpp"
#include "gc/shared/partialArraySplitter.hpp"
#include "gc/shared/partialArrayTaskStats.hpp"
#include "gc/shared/partialArrayState.hpp"
#include "gc/shared/preservedMarks.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/taskqueue.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "memory/allocation.hpp"
#include "utilities/stack.hpp"

class MutableSpace;
class PSOldGen;
class ParCompactionManagerNew;
class ObjectStartArray;
class ParMarkBitMap;

class PCMarkAndPushClosureNew: public ClaimMetadataVisitingOopIterateClosure {
  ParCompactionManagerNew* _compaction_manager;

  template <typename T> void do_oop_work(T* p);
public:
  PCMarkAndPushClosureNew(ParCompactionManagerNew* cm, ReferenceProcessor* rp) :
    ClaimMetadataVisitingOopIterateClosure(ClassLoaderData::_claim_stw_fullgc_mark, rp),
    _compaction_manager(cm) { }

  void do_oop(oop* p) final               { do_oop_work(p); }
  void do_oop(narrowOop* p) final         { do_oop_work(p); }
};

class ParCompactionManagerNew : public CHeapObj<mtGC> {
  friend class MarkFromRootsTaskNew;
  friend class ParallelCompactRefProcProxyTaskNew;
  friend class ParallelScavengeRefProcProxyTask;
  friend class ParMarkBitMap;
  friend class PSParallelCompactNew;
  friend class PCAddThreadRootsMarkingTaskClosureNew;

 private:
  typedef OverflowTaskQueue<ScannerTask, mtGC>           PSMarkTaskQueue;
  typedef GenericTaskQueueSet<PSMarkTaskQueue, mtGC>     PSMarkTasksQueueSet;

  static ParCompactionManagerNew** _manager_array;
  static PSMarkTasksQueueSet*   _marking_stacks;
  static ObjectStartArray*      _start_array;
  static PSOldGen*              _old_gen;

  static PartialArrayStateManager*  _partial_array_state_manager;
  PartialArraySplitter              _partial_array_splitter;

  PSMarkTaskQueue               _marking_stack;

  PCMarkAndPushClosureNew _mark_and_push_closure;

  static PreservedMarksSet* _preserved_marks_set;
  PreservedMarks* _preserved_marks;

  static ParMarkBitMap* _mark_bitmap;

  StringDedup::Requests _string_dedup_requests;

  static PSOldGen* old_gen()             { return _old_gen; }
  static ObjectStartArray* start_array() { return _start_array; }
  static PSMarkTasksQueueSet* marking_stacks()  { return _marking_stacks; }

  static void initialize(ParMarkBitMap* mbm);

  ParCompactionManagerNew(PreservedMarks* preserved_marks,
                       ReferenceProcessor* ref_processor,
                       uint parallel_gc_threads);

  inline PSMarkTaskQueue*  marking_stack() { return &_marking_stack; }
  inline void push(PartialArrayState* stat);
  void push_objArray(oop obj);

#if TASKQUEUE_STATS
  static void print_and_reset_taskqueue_stats();
  PartialArrayTaskStats* partial_array_task_stats();
#endif // TASKQUEUE_STATS

public:
  void flush_string_dedup_requests() {
    _string_dedup_requests.flush();
  }

  static void flush_all_string_dedup_requests();

  // Get the compaction manager when doing evacuation work from the VM thread.
  // Simply use the first compaction manager here.
  static ParCompactionManagerNew* get_vmthread_cm() { return _manager_array[0]; }

  PreservedMarks* preserved_marks() const {
    return _preserved_marks;
  }

  static ParMarkBitMap* mark_bitmap() { return _mark_bitmap; }

  // Save for later processing.  Must not fail.
  inline void push(oop obj);

  // Check mark and maybe push on marking stack.
  template <typename T> inline void mark_and_push(T* p);

  // Access function for compaction managers
  static ParCompactionManagerNew* gc_thread_compaction_manager(uint index);

  static bool steal(uint queue_num, ScannerTask& t);

  // Process tasks remaining on marking stack
  void follow_marking_stacks();
  inline bool marking_stack_empty() const;

  inline void follow_contents(const ScannerTask& task, bool stolen);
  inline void follow_array(objArrayOop array, size_t start, size_t end);
  void process_array_chunk(PartialArrayState* state, bool stolen);

  class FollowStackClosure: public VoidClosure {
   private:
    ParCompactionManagerNew* _compaction_manager;
    TaskTerminator* _terminator;
    uint _worker_id;
   public:
    FollowStackClosure(ParCompactionManagerNew* cm, TaskTerminator* terminator, uint worker_id)
      : _compaction_manager(cm), _terminator(terminator), _worker_id(worker_id) { }
    void do_void() final;
  };

  // Called after marking.
  static void verify_all_marking_stack_empty() NOT_DEBUG_RETURN;
};

bool ParCompactionManagerNew::marking_stack_empty() const {
  return _marking_stack.is_empty();
}

#endif // SHARE_GC_PARALLEL_PSCOMPACTIONMANAGERNEW_HPP
