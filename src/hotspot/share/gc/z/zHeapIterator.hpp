/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZHEAPITERATOR_HPP
#define SHARE_GC_Z_ZHEAPITERATOR_HPP

#include "gc/shared/taskqueue.inline.hpp"
#include "gc/z/zGranuleMap.hpp"
#include "gc/z/zLock.inline.hpp"
#include "memory/allocation.hpp"
#include "utilities/stack.hpp"

class ObjectClosure;
class ZHeapIteratorBitMap;
// Queue and QueueSet for parallel iteration
typedef OverflowTaskQueue<oop, mtGC>                     ZHeapIterTaskQueue;
typedef GenericTaskQueueSet<ZHeapIterTaskQueue, mtGC>    ZHeapIterTaskQueueSet;

class ZHeapIterator : public StackObj {
  template<bool Concurrent, bool Weak> friend class ZHeapIteratorRootOopClosure;
  template<bool VisitReferents, bool ParallelIter> friend class ZHeapIteratorOopClosure;

private:
  typedef ZGranuleMap<ZHeapIteratorBitMap*>         ZVisitMap;
  typedef ZGranuleMapIterator<ZHeapIteratorBitMap*> ZVisitMapIterator;
  typedef Stack<oop, mtGC>                          ZVisitStack;
  // For parallel iteration, _visit_stack only contains roots.
  // For serial iteration, _visit_stack contains all references reached.
  ZVisitStack _visit_stack;
  ZVisitMap   _visit_map;
  // For parallel iteration
  uint            _num_workers;
  ZLock           _map_lock;
  ZHeapIterTaskQueueSet* _task_queues;

  ZHeapIteratorBitMap* object_map(oop obj);
  void push(oop obj);

  template <typename RootsIterator, bool Concurrent, bool Weak> void push_roots();
  // push_fields is different for serial iterate and parallel iterate.
  template <bool VisitReferents, bool ParallelIter = false>
  void push_fields(oop obj, ZHeapIterTaskQueue* queue = NULL);
  template <bool VisitReferents> void objects_do(ObjectClosure* cl);

public:
  ZHeapIterator(uint num_workers = 0);
  ~ZHeapIterator();

  void objects_do(ObjectClosure* cl, bool visit_weaks);
  // For parallel iteration
  void par_enqueue(oop obj, ZHeapIterTaskQueue* q);
  void enqueue_roots(bool visit_weaks);
  void drain_queue(ObjectClosure* cl, bool visit_weaks, uint worker_id);
};

#endif // SHARE_GC_Z_ZHEAPITERATOR_HPP
