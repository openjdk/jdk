/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHTRAVERSALGC_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHTRAVERSALGC_HPP

#include "memory/allocation.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.hpp"
#include "runtime/thread.hpp"

class ShenandoahTraversalGC : public CHeapObj<mtGC> {
private:
  ShenandoahHeap* const _heap;
  ShenandoahObjToScanQueueSet* const _task_queues;
  ShenandoahHeapRegionSet _traversal_set;

public:
  ShenandoahTraversalGC(ShenandoahHeap* heap, size_t num_regions);
  ~ShenandoahTraversalGC();

  ShenandoahHeapRegionSet* traversal_set() { return &_traversal_set; }

  void reset();
  void prepare();
  void init_traversal_collection();
  void concurrent_traversal_collection();
  void final_traversal_collection();

  template <class T, bool STRING_DEDUP, bool DEGEN>
  inline void process_oop(T* p, Thread* thread, ShenandoahObjToScanQueue* queue, ShenandoahMarkingContext* const mark_context);

  bool check_and_handle_cancelled_gc(ShenandoahTaskTerminator* terminator, bool sts_yield);

  ShenandoahObjToScanQueueSet* task_queues();

  void main_loop(uint worker_id, ShenandoahTaskTerminator* terminator, bool sts_yield);

private:
  void prepare_regions();

  template <class T>
  void main_loop_work(T* cl, jushort* live_data, uint worker_id, ShenandoahTaskTerminator* terminator, bool sts_yield);

  void preclean_weak_refs();
  void weak_refs_work();
  void weak_refs_work_doit();

  void fixup_roots();
};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHTRAVERSALGC_HPP
