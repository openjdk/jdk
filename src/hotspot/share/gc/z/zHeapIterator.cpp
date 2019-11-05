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

template <bool VisitReferents>
class ZHeapIteratorOopClosure : public ClaimMetadataVisitingOopIterateClosure {
private:
  ZHeapIterator* const _iter;
  const oop            _base;

  oop load_oop(oop* p) {
    if (VisitReferents) {
      return HeapAccess<AS_NO_KEEPALIVE | ON_UNKNOWN_OOP_REF>::oop_load_at(_base, _base->field_offset(p));
    }

    return HeapAccess<AS_NO_KEEPALIVE>::oop_load(p);
  }

public:
  ZHeapIteratorOopClosure(ZHeapIterator* iter, oop base) :
      ClaimMetadataVisitingOopIterateClosure(ClassLoaderData::_claim_other),
      _iter(iter),
      _base(base) {}

  virtual ReferenceIterationMode reference_iteration_mode() {
    return VisitReferents ? DO_FIELDS : DO_FIELDS_EXCEPT_REFERENT;
  }

  virtual void do_oop(oop* p) {
    const oop obj = load_oop(p);
    _iter->push(obj);
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

ZHeapIterator::ZHeapIterator() :
    _visit_stack(),
    _visit_map(ZAddressOffsetMax) {}

ZHeapIterator::~ZHeapIterator() {
  ZVisitMapIterator iter(&_visit_map);
  for (ZHeapIteratorBitMap* map; iter.next(&map);) {
    delete map;
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
    map = new ZHeapIteratorBitMap(object_index_max());
    _visit_map.put(offset, map);
  }

  return map;
}

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

template <bool VisitReferents>
void ZHeapIterator::push_fields(oop obj) {
  ZHeapIteratorOopClosure<VisitReferents> cl(this, obj);
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

void ZHeapIterator::objects_do(ObjectClosure* cl, bool visit_weaks) {
  if (visit_weaks) {
    objects_do<true /* VisitWeaks */>(cl);
  } else {
    objects_do<false /* VisitWeaks */>(cl);
  }
}
