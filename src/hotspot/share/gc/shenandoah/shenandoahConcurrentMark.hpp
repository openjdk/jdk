/*
 * Copyright (c) 2013, 2020, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHCONCURRENTMARK_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHCONCURRENTMARK_HPP

#include "gc/shared/taskqueue.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "gc/shenandoah/shenandoahOopClosures.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.hpp"

class ShenandoahStrDedupQueue;

class ShenandoahConcurrentMark: public CHeapObj<mtGC> {
private:
  ShenandoahHeap* _heap;
  ShenandoahObjToScanQueueSet* _task_queues;

public:
  void initialize(uint workers);
  void cancel();

// ---------- Marking loop and tasks
//
private:
  template <class T>
  inline void do_task(ShenandoahObjToScanQueue* q, T* cl, ShenandoahLiveData* live_data, ShenandoahMarkTask* task);

  template <class T>
  inline void do_chunked_array_start(ShenandoahObjToScanQueue* q, T* cl, oop array);

  template <class T>
  inline void do_chunked_array(ShenandoahObjToScanQueue* q, T* cl, oop array, int chunk, int pow);

  inline void count_liveness(ShenandoahLiveData* live_data, oop obj);

  template <class T, bool CANCELLABLE>
  void mark_loop_work(T* cl, ShenandoahLiveData* live_data, uint worker_id, TaskTerminator *t);

  template <bool CANCELLABLE>
  void mark_loop_prework(uint worker_id, TaskTerminator *terminator, ReferenceProcessor *rp, bool strdedup);

public:
  void mark_loop(uint worker_id, TaskTerminator* terminator, ReferenceProcessor *rp,
                 bool cancellable, bool strdedup) {
    if (cancellable) {
      mark_loop_prework<true>(worker_id, terminator, rp, strdedup);
    } else {
      mark_loop_prework<false>(worker_id, terminator, rp, strdedup);
    }
  }

  template<class T, UpdateRefsMode UPDATE_REFS, StringDedupMode STRING_DEDUP>
  static inline void mark_through_ref(T* p, ShenandoahHeap* heap, ShenandoahObjToScanQueue* q, ShenandoahMarkingContext* const mark_context);

  void mark_from_roots();
  void finish_mark_from_roots(bool full_gc);

  void mark_roots(ShenandoahPhaseTimings::Phase root_phase);
  void update_roots(ShenandoahPhaseTimings::Phase root_phase);
  void update_thread_roots(ShenandoahPhaseTimings::Phase root_phase);

// ---------- Weak references
//
private:
  void weak_refs_work(bool full_gc);
  void weak_refs_work_doit(bool full_gc);

public:
  void preclean_weak_refs();

// ---------- Helpers
// Used from closures, need to be public
//
public:
  ShenandoahObjToScanQueue* get_queue(uint worker_id);
  ShenandoahObjToScanQueueSet* task_queues() { return _task_queues; }

};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCONCURRENTMARK_HPP
