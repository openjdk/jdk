/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zAddressRangeMap.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHeapIterator.hpp"
#include "gc/z/zOop.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/oop.inline.hpp"
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

class ZHeapIteratorRootOopClosure : public OopClosure {
private:
  ZHeapIterator* const _iter;
  ObjectClosure* const _cl;

public:
  ZHeapIteratorRootOopClosure(ZHeapIterator* iter, ObjectClosure* cl) :
      _iter(iter),
      _cl(cl) {}

  virtual void do_oop(oop* p) {
    // Load barrier needed here for the same reason we
    // need fixup_partial_loads() in ZHeap::mark_end()
    const oop obj = ZBarrier::load_barrier_on_oop_field(p);
    _iter->push(obj);
    _iter->drain(_cl);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

class ZHeapIteratorPushOopClosure : public BasicOopIterateClosure {
private:
  ZHeapIterator* const _iter;
  const oop            _base;
  const bool           _visit_referents;

public:
  ZHeapIteratorPushOopClosure(ZHeapIterator* iter, oop base) :
      _iter(iter),
      _base(base),
      _visit_referents(iter->visit_referents()) {}

  oop load_oop(oop* p) {
    if (_visit_referents) {
      return HeapAccess<ON_UNKNOWN_OOP_REF>::oop_load_at(_base, _base->field_offset(p));
    } else {
      return HeapAccess<>::oop_load(p);
    }
  }

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

size_t ZHeapIterator::object_index_max() const {
  return ZPageSizeMin >> ZObjectAlignmentSmallShift;
}

size_t ZHeapIterator::object_index(oop obj) const {
  const uintptr_t addr = ZOop::to_address(obj);
  const uintptr_t offset = ZAddress::offset(addr);
  const uintptr_t mask = (1 << ZPageSizeMinShift) - 1;
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

void ZHeapIterator::drain(ObjectClosure* cl) {
  while (!_visit_stack.is_empty()) {
    const oop obj = _visit_stack.pop();

    // Visit
    cl->do_object(obj);

    // Push members to visit
    ZHeapIteratorPushOopClosure push_cl(this, obj);
    obj->oop_iterate(&push_cl);
  }
}

bool ZHeapIterator::visit_referents() const {
  return _visit_referents;
}

void ZHeapIterator::objects_do(ObjectClosure* cl) {
  ZHeapIteratorRootOopClosure root_cl(this, cl);
  ZRootsIterator roots;

  // Follow roots. Note that we also visit the JVMTI weak tag map
  // as if they were strong roots to make sure we visit all tagged
  // objects, even those that might now have become unreachable.
  // If we didn't do this the user would have expected to see
  // ObjectFree events for unreachable objects in the tag map.
  roots.oops_do(&root_cl, true /* visit_jvmti_weak_export */);
}
