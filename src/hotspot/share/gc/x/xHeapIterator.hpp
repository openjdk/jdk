/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XHEAPITERATOR_HPP
#define SHARE_GC_X_XHEAPITERATOR_HPP

#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "gc/shared/taskqueue.hpp"
#include "gc/x/xGranuleMap.hpp"
#include "gc/x/xLock.hpp"
#include "gc/x/xRootsIterator.hpp"
#include "gc/x/xStat.hpp"

class XHeapIteratorBitMap;
class XHeapIteratorContext;

using XHeapIteratorBitMaps = XGranuleMap<XHeapIteratorBitMap*>;
using XHeapIteratorBitMapsIterator = XGranuleMapIterator<XHeapIteratorBitMap*>;
using XHeapIteratorQueue = OverflowTaskQueue<oop, mtGC>;
using XHeapIteratorQueues = GenericTaskQueueSet<XHeapIteratorQueue, mtGC>;
using XHeapIteratorArrayQueue = OverflowTaskQueue<ObjArrayTask, mtGC>;
using XHeapIteratorArrayQueues = GenericTaskQueueSet<XHeapIteratorArrayQueue, mtGC>;

class XHeapIterator : public ParallelObjectIteratorImpl {
  friend class XHeapIteratorContext;

private:
  const bool               _visit_weaks;
  XStatTimerDisable        _timer_disable;
  XHeapIteratorBitMaps     _bitmaps;
  XLock                    _bitmaps_lock;
  XHeapIteratorQueues      _queues;
  XHeapIteratorArrayQueues _array_queues;
  XRootsIterator           _roots;
  XWeakRootsIterator       _weak_roots;
  TaskTerminator           _terminator;

  XHeapIteratorBitMap* object_bitmap(oop obj);

  bool mark_object(oop obj);

  void push_strong_roots(const XHeapIteratorContext& context);
  void push_weak_roots(const XHeapIteratorContext& context);

  template <bool VisitWeaks>
  void push_roots(const XHeapIteratorContext& context);

  template <bool VisitReferents>
  void follow_object(const XHeapIteratorContext& context, oop obj);

  void follow_array(const XHeapIteratorContext& context, oop obj);
  void follow_array_chunk(const XHeapIteratorContext& context, const ObjArrayTask& array);

  template <bool VisitWeaks>
  void visit_and_follow(const XHeapIteratorContext& context, ObjectClosure* cl, oop obj);

  template <bool VisitWeaks>
  void drain(const XHeapIteratorContext& context, ObjectClosure* cl);

  template <bool VisitWeaks>
  void steal(const XHeapIteratorContext& context, ObjectClosure* cl);

  template <bool VisitWeaks>
  void drain_and_steal(const XHeapIteratorContext& context, ObjectClosure* cl);

  template <bool VisitWeaks>
  void object_iterate_inner(const XHeapIteratorContext& context, ObjectClosure* cl);

public:
  XHeapIterator(uint nworkers, bool visit_weaks);
  virtual ~XHeapIterator();

  virtual void object_iterate(ObjectClosure* cl, uint worker_id);
};

#endif // SHARE_GC_X_XHEAPITERATOR_HPP
