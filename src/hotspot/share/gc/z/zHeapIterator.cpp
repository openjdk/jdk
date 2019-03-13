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
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zGranuleMap.inline.hpp"
#include "gc/z/zHeapIterator.hpp"
#include "gc/z/zOop.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
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

class ZHeapIteratorRootOopClosure : public ZRootsIteratorClosure {
private:
  ZHeapIterator* const _iter;

public:
  ZHeapIteratorRootOopClosure(ZHeapIterator* iter) :
      _iter(iter) {}

  virtual void do_oop(oop* p) {
    // Load barrier needed here, even on non-concurrent strong roots,
    // for the same reason we need fixup_partial_loads() in ZHeap::mark_end().
    const oop obj = NativeAccess<AS_NO_KEEPALIVE>::oop_load(p);
    _iter->push(obj);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

class ZHeapIteratorOopClosure : public BasicOopIterateClosure {
private:
  ZHeapIterator* const _iter;
  const oop            _base;
  const bool           _visit_referents;

  oop load_oop(oop* p) const {
    if (_visit_referents) {
      return HeapAccess<ON_UNKNOWN_OOP_REF | AS_NO_KEEPALIVE>::oop_load_at(_base, _base->field_offset(p));
    } else {
      return HeapAccess<AS_NO_KEEPALIVE>::oop_load(p);
    }
  }

public:
  ZHeapIteratorOopClosure(ZHeapIterator* iter, oop base, bool visit_referents) :
      _iter(iter),
      _base(base),
      _visit_referents(visit_referents) {}

  virtual ReferenceIterationMode reference_iteration_mode() {
    return _visit_referents ? DO_FIELDS : DO_FIELDS_EXCEPT_REFERENT;
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

ZHeapIterator::ZHeapIterator(bool visit_referents) :
    _visit_stack(),
    _visit_map(),
    _visit_referents(visit_referents) {}

ZHeapIterator::~ZHeapIterator() {
  ZVisitMapIterator iter(&_visit_map);
  for (ZHeapIteratorBitMap* map; iter.next(&map);) {
    delete map;
  }
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
  const uintptr_t addr = ZOop::to_address(obj);
  ZHeapIteratorBitMap* map = _visit_map.get(addr);
  if (map == NULL) {
    map = new ZHeapIteratorBitMap(object_index_max());
    _visit_map.put(addr, map);
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

void ZHeapIterator::objects_do(ObjectClosure* cl) {
  // Note that the heap iterator visits all reachable objects, including
  // objects that might be unreachable from the application, such as a
  // not yet cleared JNIWeakGloablRef. However, also note that visiting
  // the JVMTI tag map is a requirement to make sure we visit all tagged
  // objects, even those that might now have become phantom reachable.
  // If we didn't do this the application would have expected to see
  // ObjectFree events for phantom reachable objects in the tag map.

  ZHeapIteratorRootOopClosure root_cl(this);

  // Push strong roots onto stack
  {
    ZRootsIterator roots;
    roots.oops_do(&root_cl);
  }

  {
    ZConcurrentRootsIterator roots;
    roots.oops_do(&root_cl);
  }

  // Push weak roots onto stack
  {
    ZWeakRootsIterator roots;
    roots.oops_do(&root_cl);
  }

  {
    ZConcurrentWeakRootsIterator roots;
    roots.oops_do(&root_cl);
  }

  // Drain stack
  while (!_visit_stack.is_empty()) {
    const oop obj = _visit_stack.pop();

    // Visit
    cl->do_object(obj);

    // Push members to visit
    ZHeapIteratorOopClosure push_cl(this, obj, _visit_referents);
    obj->oop_iterate(&push_cl);
  }
}
