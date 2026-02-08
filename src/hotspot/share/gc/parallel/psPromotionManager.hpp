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

#ifndef SHARE_GC_PARALLEL_PSPROMOTIONMANAGER_HPP
#define SHARE_GC_PARALLEL_PSPROMOTIONMANAGER_HPP

#include "gc/parallel/psPromotionLAB.hpp"
#include "gc/shared/copyFailedInfo.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/partialArraySplitter.hpp"
#include "gc/shared/partialArrayState.hpp"
#include "gc/shared/partialArrayTaskStats.hpp"
#include "gc/shared/preservedMarks.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/taskqueue.hpp"
#include "memory/padded.hpp"
#include "utilities/globalDefinitions.hpp"

//
// psPromotionManager is used by a single thread to manage object survival
// during a scavenge. The promotion manager contains thread local data only.
//
// NOTE! Be careful when allocating the stacks on cheap. If you are going
// to use a promotion manager in more than one thread, the stacks MUST be
// on cheap. This can lead to memory leaks, though, as they are not auto
// deallocated.
//
// FIX ME FIX ME Add a destructor, and don't rely on the user to drain/flush/deallocate!
//

class MutableSpace;
class PSOldGen;
class ParCompactionManager;

class PSPromotionManager {
  friend class PSScavenge;
  friend class ScavengeRootsTask;

 private:
  typedef OverflowTaskQueue<ScannerTask, mtGC>           PSScannerTasksQueue;
  typedef GenericTaskQueueSet<PSScannerTasksQueue, mtGC> PSScannerTasksQueueSet;

  static PaddedEnd<PSPromotionManager>* _manager_array;
  static PSScannerTasksQueueSet*        _stack_array_depth;
  static PreservedMarksSet*             _preserved_marks_set;
  static PSOldGen*                      _old_gen;
  static MutableSpace*                  _young_space;

#if TASKQUEUE_STATS
  static void print_and_reset_taskqueue_stats();
  PartialArrayTaskStats* partial_array_task_stats();
#endif // TASKQUEUE_STATS

  PSYoungPromotionLAB                 _young_lab;
  PSOldPromotionLAB                   _old_lab;
  bool                                _young_gen_has_alloc_failure;
  bool                                _young_gen_is_full;
  bool                                _old_gen_is_full;

  PSScannerTasksQueue                 _claimed_stack_depth;

  uint                                _target_stack_size;

  static PartialArrayStateManager*    _partial_array_state_manager;
  PartialArraySplitter                _partial_array_splitter;
  uint                                _min_array_size_for_chunking;

  PreservedMarks*                     _preserved_marks;
  PromotionFailedInfo                 _promotion_failed_info;

  StringDedup::Requests _string_dedup_requests;

  // Accessors
  static PSOldGen* old_gen()         { return _old_gen; }
  static MutableSpace* young_space() { return _young_space; }

  inline static PSPromotionManager* manager_array(uint index);

  void process_array_chunk(PartialArrayState* state, bool stolen);
  void process_array_chunk(objArrayOop obj, size_t start, size_t end);
  void push_objArray(oop old_obj, oop new_obj);

  inline void promotion_trace_event(oop new_obj, Klass* klass, size_t obj_size,
                                    uint age, bool tenured,
                                    const PSPromotionLAB* lab);

  static PSScannerTasksQueueSet* stack_array_depth() { return _stack_array_depth; }

  template<bool promote_immediately>
  oop copy_unmarked_to_survivor_space(oop o, markWord m);

  inline HeapWord* allocate_in_young_gen(Klass* klass,
                                         size_t obj_size,
                                         uint age);
  inline HeapWord* allocate_in_old_gen(Klass* klass,
                                       size_t obj_size,
                                       uint age);

 public:
  // Static
  static void initialize();

  static void pre_scavenge();
  static bool post_scavenge(YoungGCTracer& gc_tracer);

  static PSPromotionManager* gc_thread_promotion_manager(uint index);
  static PSPromotionManager* vm_thread_promotion_manager();

  static bool steal_depth(int queue_num, ScannerTask& t);

  PSPromotionManager();

  // Accessors
  PSScannerTasksQueue* claimed_stack_depth() {
    return &_claimed_stack_depth;
  }

  // Promotion methods
  template<bool promote_immediately> oop copy_to_survivor_space(oop o);
  oop oop_promotion_failed(oop obj, markWord obj_mark);

  void reset();
  void register_preserved_marks(PreservedMarks* preserved_marks);
  static void restore_preserved_marks();

  void flush_labs();
  void flush_string_dedup_requests() { _string_dedup_requests.flush(); }

  void drain_stacks_cond_depth() {
    if (claimed_stack_depth()->size() > _target_stack_size) {
      drain_stacks(false);
    }
  }
  void drain_stacks(bool totally_drain);

  bool stacks_empty() {
    return claimed_stack_depth()->is_empty();
  }

  inline void process_popped_location_depth(ScannerTask task, bool stolen);

  template <bool promote_immediately, class T>
  void copy_and_push_safe_barrier(T* p);

  template <class T> inline void claim_or_forward_depth(T* p);

  void push_contents(oop obj);
  void push_contents_bounded(oop obj, HeapWord* left, HeapWord* right);
};

#endif // SHARE_GC_PARALLEL_PSPROMOTIONMANAGER_HPP
