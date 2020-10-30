/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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

#include "gc/shared/taskTerminator.hpp"
#include "gc/shenandoah/shenandoahOopClosures.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.hpp"

class ShenandoahCMDrainMarkingStackClosure;

class ShenandoahInitMarkRootsClosure : public OopClosure {
private:
  ShenandoahObjToScanQueue* const _queue;
  ShenandoahHeap*           const _heap;
  ShenandoahMarkingContext* const _mark_context;

  template <class T>
  inline void do_oop_work(T* p);

public:
  ShenandoahInitMarkRootsClosure(ShenandoahObjToScanQueue* q);

  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(oop* p)       { do_oop_work(p); }
};

class ShenandoahCMKeepAliveClosure : public OopClosure {
private:
  ShenandoahObjToScanQueue* _queue;
  ShenandoahHeap* _heap;
  ShenandoahMarkingContext* const _mark_context;

  template <class T>
  inline void do_oop_work(T* p);

public:
  ShenandoahCMKeepAliveClosure(ShenandoahObjToScanQueue* q) :
    _queue(q),
    _heap(ShenandoahHeap::heap()),
    _mark_context(_heap->marking_context()) {}

  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(oop* p)       { do_oop_work(p); }
};

// Base class for mark
// Mark class does not maintain states. Instead, mark states are
// maintained by task queues, mark bitmap and SATB buffers (concurrent mark)
class ShenandoahMark: public StackObj {
  friend class ShenandoahCMDrainMarkingStackClosure;

protected:
  ShenandoahHeap* const              _heap;
  ShenandoahObjToScanQueueSet* const _task_queues;

protected:
  ShenandoahMark();

public:
  template<class T, UpdateRefsMode UPDATE_REFS, StringDedupMode STRING_DEDUP>
  static inline void mark_through_ref(T* p, ShenandoahHeap* heap, ShenandoahObjToScanQueue* q, ShenandoahMarkingContext* const mark_context);

  static void clear();

  // Helpers
  inline ShenandoahObjToScanQueueSet* task_queues() const;
  inline ShenandoahObjToScanQueue* get_queue(uint index) const;

// ---------- Marking loop and tasks
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

protected:
  void mark_loop(uint worker_id, TaskTerminator* terminator, ReferenceProcessor *rp,
                 bool cancellable, bool strdedup);

  // Reference processing
  void process_weak_refs(bool full_gc);
  void process_weak_refs_work(bool full_gc);

  inline ShenandoahHeap* heap() const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHMARK_HPP

