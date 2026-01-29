/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1FULLGCMARKER_HPP
#define SHARE_GC_G1_G1FULLGCMARKER_HPP

#include "gc/g1/g1FullGCOopClosures.hpp"
#include "gc/g1/g1OopClosures.hpp"
#include "gc/g1/g1RegionMarkStatsCache.hpp"
#include "gc/shared/partialArraySplitter.hpp"
#include "gc/shared/partialArrayState.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/taskqueue.hpp"
#include "memory/iterator.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.hpp"
#include "runtime/timer.hpp"
#include "utilities/chunkedList.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/stack.hpp"



class G1CMBitMap;
class G1FullCollector;
class TaskTerminator;

typedef OverflowTaskQueue<ScannerTask, mtGC>        G1MarkTasksQueue;
typedef GenericTaskQueueSet<G1MarkTasksQueue, mtGC> G1MarkTasksQueueSet;

class G1FullGCMarker : public CHeapObj<mtGC> {
  G1FullCollector*   _collector;

  uint               _worker_id;
  // Backing mark bitmap
  G1CMBitMap*        _bitmap;

  // Mark stack
  G1MarkTasksQueue     _task_queue;
  PartialArraySplitter _partial_array_splitter;

  // Marking closures
  G1MarkAndPushClosure  _mark_closure;
  G1MarkStackClosure    _stack_closure;
  CLDToOopClosure       _cld_closure;
  StringDedup::Requests _string_dedup_requests;


  G1RegionMarkStatsCache _mark_stats_cache;

  inline bool is_task_queue_empty();
  inline bool mark_object(oop obj);

  // Marking helpers
  inline void process_array_chunk(objArrayOop obj, size_t start, size_t end);
  inline void dispatch_task(const ScannerTask& task, bool stolen);
  // Start processing the given objArrayOop by first pushing its continuations and
  // then scanning the first chunk.
  void start_partial_array_processing(objArrayOop obj);
  // Process the given continuation.
  void process_partial_array(PartialArrayState* state, bool stolen);

  inline void publish_and_drain_oop_tasks();
public:
  G1FullGCMarker(G1FullCollector* collector,
                 uint worker_id,
                 G1RegionMarkStats* mark_stats);
  ~G1FullGCMarker();

  G1MarkTasksQueue* task_queue() { return &_task_queue; }

  // Marking entry points
  template <class T> inline void mark_and_push(T* p);

  inline void process_marking_stacks();
  void complete_marking(G1MarkTasksQueueSet* task_queues,
                        TaskTerminator* terminator);

  // Closure getters
  CLDToOopClosure*      cld_closure()   { return &_cld_closure; }
  G1MarkAndPushClosure* mark_closure()  { return &_mark_closure; }
  G1MarkStackClosure*   stack_closure() { return &_stack_closure; }

  // Flush live bytes to regions
  void flush_mark_stats_cache();
};

#endif // SHARE_GC_G1_G1FULLGCMARKER_HPP
