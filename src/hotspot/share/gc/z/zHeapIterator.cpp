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

#include "precompiled.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zGranuleMap.inline.hpp"
#include "gc/z/zHeapIterator.hpp"
#include "gc/z/zOop.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStat.hpp"
#include "memory/iterator.inline.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/stack.inline.hpp"

class ZHeapIteratorBitMap : public CHeapObj<mtGC> {
private:
  CHeapBitMap _map;

public:
  ZHeapIteratorBitMap(size_t size_in_bits) :
      _map(size_in_bits) {}

  bool try_set_bit(size_t index) {
    if (_map.at(index)) {
      return false;
    }

    _map.set_bit(index);
    return true;
  }

  inline bool par_try_set_bit(size_t index) {
    return _map.par_set_bit(index);
  }
};

template <bool Concurrent, bool Weak>
class ZHeapIteratorRootOopClosure : public ZRootsIteratorClosure {
private:
  ZHeapIterator* const _iter;

  oop load_oop(oop* p) {
    if (Weak) {
      return NativeAccess<AS_NO_KEEPALIVE | ON_PHANTOM_OOP_REF>::oop_load(p);
    }

    if (Concurrent) {
      return NativeAccess<AS_NO_KEEPALIVE>::oop_load(p);
    }

    return RawAccess<>::oop_load(p);
  }

public:
  ZHeapIteratorRootOopClosure(ZHeapIterator* iter) :
      _iter(iter) {}

  virtual void do_oop(oop* p) {
    const oop obj = load_oop(p);
    _iter->push(obj);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

template <bool VisitReferents, bool ParallelIter>
class ZHeapIteratorOopClosure : public ClaimMetadataVisitingOopIterateClosure {
private:
  ZHeapIterator* const _iter;
  const oop            _base;
  ZHeapIterTaskQueue*     _queue;

  oop load_oop(oop* p) {
    if (VisitReferents) {
      return HeapAccess<AS_NO_KEEPALIVE | ON_UNKNOWN_OOP_REF>::oop_load_at(_base, _base->field_offset(p));
    }

    return HeapAccess<AS_NO_KEEPALIVE>::oop_load(p);
  }

public:
  ZHeapIteratorOopClosure(ZHeapIterator* iter, oop base, ZHeapIterTaskQueue* q = NULL) :
      ClaimMetadataVisitingOopIterateClosure(ClassLoaderData::_claim_other),
      _iter(iter),
      _base(base),
      _queue(q) {}

  virtual ReferenceIterationMode reference_iteration_mode() {
    return VisitReferents ? DO_FIELDS : DO_FIELDS_EXCEPT_REFERENT;
  }

  virtual void do_oop(oop* p) {
    const oop obj = load_oop(p);
    if (ParallelIter == false) {
      _iter->push(obj);
    } else {
      _iter->par_enqueue(obj, _queue);
    }
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }

#ifdef ASSERT
  virtual bool should_verify_oops() {
    return false;
  }
#endif
};

ZHeapIterator::ZHeapIterator(uint num_workers) :
    _visit_stack(),
    _visit_map(ZAddressOffsetMax),
    _num_workers(num_workers),
    _map_lock(),
    _task_queues(NULL) {
  if (_num_workers > 1) {
    // prepare process queue.
    _task_queues = new ZHeapIterTaskQueueSet((int) _num_workers);
    for (uint i = 0; i < _num_workers; i++) {
      ZHeapIterTaskQueue* q = new ZHeapIterTaskQueue();
      q->initialize();
      _task_queues->register_queue(i, q);
    }
  }
}

ZHeapIterator::~ZHeapIterator() {
  ZVisitMapIterator iter(&_visit_map);
  for (ZHeapIteratorBitMap* map; iter.next(&map);) {
    delete map;
  }
  // reclaim task queues
  if (_task_queues != NULL) {
    for (uint i = 0; i < _num_workers; i++) {
      ZHeapIterTaskQueue* q = _task_queues->queue(i);
      if (q != NULL) {
        delete q;
        q = NULL;
      }
    }
    delete _task_queues;
    _task_queues = NULL;
  }
  ClassLoaderDataGraph::clear_claimed_marks(ClassLoaderData::_claim_other);
}

static size_t object_index_max() {
  return ZGranuleSize >> ZObjectAlignmentSmallShift;
}

static size_t object_index(oop obj) {
  const uintptr_t addr = ZOop::to_address(obj);
  const uintptr_t offset = ZAddress::offset(addr);
  const uintptr_t mask = ZGranuleSize - 1;
  return (offset & mask) >> ZObjectAlignmentSmallShift;
}

ZHeapIteratorBitMap* ZHeapIterator::object_map(oop obj) {
  const uintptr_t offset = ZAddress::offset(ZOop::to_address(obj));
  ZHeapIteratorBitMap* map = _visit_map.get(offset);
  if (map == NULL) {
    if (_num_workers > 1) {
      // Parallel iterate, holding lock to update _visit_map
      ZLocker<ZLock> locker(&_map_lock);
      if (map == NULL) {
        map = new ZHeapIteratorBitMap(object_index_max());
        _visit_map.put(offset, map);
      }
    } else {
      map = new ZHeapIteratorBitMap(object_index_max());
      _visit_map.put(offset, map);
    }
  }
  return map;
}

// push objects in to _visit_stack, used by RootOopClosure.
void ZHeapIterator::push(oop obj) {
  if (obj == NULL) {
    // Ignore
    return;
  }

  ZHeapIteratorBitMap* const map = object_map(obj);
  const size_t index = object_index(obj);
  if (!map->try_set_bit(index)) {
    // Already pushed
    return;
  }

  // Push
  _visit_stack.push(obj);
}

template <typename RootsIterator, bool Concurrent, bool Weak>
void ZHeapIterator::push_roots() {
  ZHeapIteratorRootOopClosure<Concurrent, Weak> cl(this);
  RootsIterator roots;
  roots.oops_do(&cl);
}

template <bool VisitReferents, bool ParallelIter>
void ZHeapIterator::push_fields(oop obj, ZHeapIterTaskQueue* queue) {
  ZHeapIteratorOopClosure<VisitReferents, ParallelIter> cl(this, obj, queue);
  obj->oop_iterate(&cl);
}

template <bool VisitWeaks>
void ZHeapIterator::objects_do(ObjectClosure* cl) {
  ZStatTimerDisable disable;

  // Push roots to visit
  push_roots<ZRootsIterator,                     false /* Concurrent */, false /* Weak */>();
  push_roots<ZConcurrentRootsIteratorClaimOther, true  /* Concurrent */, false /* Weak */>();
  if (VisitWeaks) {
    push_roots<ZWeakRootsIterator,           false /* Concurrent */, true  /* Weak */>();
    push_roots<ZConcurrentWeakRootsIterator, true  /* Concurrent */, true  /* Weak */>();
  }

  // Drain stack
  while (!_visit_stack.is_empty()) {
    const oop obj = _visit_stack.pop();

    // Visit object
    cl->do_object(obj);

    // Push fields to visit
    push_fields<VisitWeaks>(obj);
  }
}

// Used only in serial iteration
void ZHeapIterator::objects_do(ObjectClosure* cl, bool visit_weaks) {
  if (visit_weaks) {
    objects_do<true /* VisitWeaks */>(cl);
  } else {
    objects_do<false /* VisitWeaks */>(cl);
  }
}

// Parallel iteration support
void ZHeapIterator::enqueue_roots(bool visit_weaks) {
  ZStatTimerDisable disable;
  // Push roots to visit
  push_roots<ZRootsIterator,                     false /* Concurrent */, false /* Weak */>();
  push_roots<ZConcurrentRootsIteratorClaimOther, true  /* Concurrent */, false /* Weak */>();
  if (visit_weaks) {
    push_roots<ZWeakRootsIterator,           false /* Concurrent */, true  /* Weak */>();
    push_roots<ZConcurrentWeakRootsIterator, true  /* Concurrent */, true  /* Weak */>();
  }
  // Divide roots into thread queue.
  size_t roots_num = _visit_stack.size();
  for (uint i = 0; i < roots_num; i++) {
    uint worker_id = i % _num_workers;
    oop obj = _visit_stack.pop();
    _task_queues->queue(worker_id)->push(obj);
  }
}

void ZHeapIterator::drain_queue(ObjectClosure* cl, bool visit_weaks, uint worker_id) {
  ZStatTimerDisable disable;
  ZHeapIterTaskQueue* q = _task_queues->queue(worker_id);
  assert(q != NULL, "Heap iteration task queue must not NULL");
  // Drain stack
  oop obj;
  while (q->pop_overflow(obj) || q->pop_local(obj) || _task_queues->steal(worker_id, obj)) {
    // Visit object
    cl->do_object(obj);
    // Push fields to visit
    if (visit_weaks = true) {
      push_fields<true, true /* ParallelIter */>(obj, q);
    } else {
      push_fields<false, true /* ParallelIter */>(obj, q);
    }
  }
}

void ZHeapIterator::par_enqueue(oop obj, ZHeapIterTaskQueue* q) {
   if (obj == NULL) {
    // Ignore
    return;
  }

  ZHeapIteratorBitMap* const map = object_map(obj);
  const size_t index = object_index(obj);
  if (!map->par_try_set_bit(index)) {
    // Already pushed
    return;
  }

  // Push
  q->push(obj);
}
