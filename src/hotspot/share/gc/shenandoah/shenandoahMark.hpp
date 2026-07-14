/*
 * Copyright (c) 2021, 2022, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHMARK_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHMARK_HPP

#include "gc/shared/ageTable.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationType.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.hpp"

class ShenandoahMarkingContext;

// Base class for mark
// Mark class does not maintain states. Instead, mark states are
// maintained by task queues, mark bitmap and SATB buffers (concurrent mark)
class ShenandoahMark: public StackObj {
protected:
  ShenandoahGeneration* const _generation;
  ShenandoahObjToScanQueueSet* const _task_queues;
  ShenandoahObjToScanQueueSet* const _old_gen_task_queues;
  bool const _string_dedup;

protected:
  ShenandoahMark(ShenandoahGeneration* generation);

public:
  template<class T, ShenandoahGenerationType GENERATION>
  ALWAYSINLINE
  static void mark_through_ref(T* p, ShenandoahObjToScanQueue* q, ShenandoahObjToScanQueue* old_q, ShenandoahMarkingContext* const mark_context, bool weak);

  // Loom support
  void start_mark();
  void end_mark();

  // Helpers
  inline ShenandoahObjToScanQueueSet* task_queues() const;
  ShenandoahObjToScanQueueSet* old_task_queues() {
    return _old_gen_task_queues;
  }

  inline ShenandoahObjToScanQueue* get_queue(uint index) const;
  inline ShenandoahObjToScanQueue* get_old_queue(uint index) const;

  ShenandoahGeneration* generation() const { return _generation; };

private:
// ---------- Marking loop and tasks

  template <class T, ShenandoahGenerationType GENERATION, bool STRING_DEDUP>
  ALWAYSINLINE
  static void do_task(ShenandoahObjToScanQueue* q, T* cl, ShenandoahLiveData* live_data, StringDedup::Requests* const req, ShenandoahMarkTask* task, uint worker_id);

  template <class T>
  ALWAYSINLINE
  static void do_chunked_array_start(ShenandoahObjToScanQueue* q, T* cl, oop array, Klass* klass, bool weak);

  template <class T>
  ALWAYSINLINE
  static void do_chunked_array(ShenandoahObjToScanQueue* q, T* cl, oop array, int chunk, int pow, bool weak);

  template <ShenandoahGenerationType GENERATION>
  ALWAYSINLINE
  static void count_liveness(ShenandoahLiveData* live_data, oop obj, Klass* klass, uint worker_id);

  template <ShenandoahGenerationType GENERATION>
  ALWAYSINLINE
  static bool in_generation(ShenandoahHeap* const heap, oop obj);

  template <class T>
  ALWAYSINLINE
  static void mark_non_generational_ref(T *p, ShenandoahObjToScanQueue* q, ShenandoahMarkingContext* const mark_context, bool weak);

  ALWAYSINLINE
  static void mark_ref(ShenandoahObjToScanQueue* q, ShenandoahMarkingContext* const mark_context, bool weak, oop obj);

  ALWAYSINLINE
  static void dedup_string(oop obj, StringDedup::Requests* const req);

  template <ShenandoahGenerationType GENERATION, bool CANCELLABLE, bool STRING_DEDUP>
  void mark_loop_prework(uint worker_id, TaskTerminator *terminator, StringDedup::Requests* const req, bool update_refs);

  template <class T, ShenandoahGenerationType GENERATION, bool CANCELLABLE, bool STRING_DEDUP>
  NOINLINE // Main hot loop, start inlining from here
  void mark_loop_work(T* cl, ShenandoahLiveData* live_data, uint worker_id, TaskTerminator *t, StringDedup::Requests* const req);

  template <bool CANCELLABLE>
  NOINLINE // Utility loop, maybe hot, start inlining from here
  void mark_drain_extra_queues(ShenandoahObjToScanQueueSet* queues, ShenandoahObjToScanQueue* local_q);

protected:
  template<bool CANCELLABLE, bool STRING_DEDUP>
  void mark_loop(uint worker_id, TaskTerminator* terminator, ShenandoahGenerationType generation_type,
                StringDedup::Requests* const req);

  void mark_loop(uint worker_id, TaskTerminator* terminator, ShenandoahGenerationType generation_type,
                 bool cancellable);
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHMARK_HPP
