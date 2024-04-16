/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zGenerationId.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zGranuleMap.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zHeapIterator.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zNMethod.hpp"
#include "memory/iterator.inline.hpp"
#include "utilities/bitMap.inline.hpp"

class ZHeapIteratorBitMap : public CHeapObj<mtGC> {
private:
  CHeapBitMap _bitmap;

public:
  ZHeapIteratorBitMap(size_t size_in_bits)
    : _bitmap(size_in_bits, mtGC) {}

  bool try_set_bit(size_t index) {
    return _bitmap.par_set_bit(index);
  }
};

class ZHeapIteratorContext {
private:
  ObjectClosure* const                _object_cl;
  OopFieldClosure* const              _field_cl;
  const uint                          _worker_id;
  ZHeapIteratorQueue* const           _queue;
  ZHeapIteratorArrayChunkQueue* const _array_chunk_queue;

public:
  ZHeapIteratorContext(ObjectClosure* object_cl,
                       OopFieldClosure* field_cl,
                       uint worker_id,
                       ZHeapIteratorQueue* queue,
                       ZHeapIteratorArrayChunkQueue* array_chunk_queue)
    : _object_cl(object_cl),
      _field_cl(field_cl),
      _worker_id(worker_id),
      _queue(queue),
      _array_chunk_queue(array_chunk_queue) {}

  uint worker_id() const {
    return _worker_id;
  }

  void visit_field(oop base, oop* p) const {
    if (_field_cl != nullptr) {
      _field_cl->do_field(base, p);
    }
  }

  void visit_object(oop obj) const {
    _object_cl->do_object(obj);
  }

  void push(oop obj) const {
    _queue->push(obj);
  }

  void push_array_chunk(const ObjArrayTask& array_chunk) const {
    _array_chunk_queue->push(array_chunk);
  }

  bool pop(oop& obj) const {
    return _queue->pop_overflow(obj) || _queue->pop_local(obj);
  }

  bool pop_array_chunk(ObjArrayTask& array_chunk) const {
    return _array_chunk_queue->pop_overflow(array_chunk) || _array_chunk_queue->pop_local(array_chunk);
  }

  bool is_drained() const {
    return _queue->is_empty() && _array_chunk_queue->is_empty();
  }
};

template <bool Weak>
class ZHeapIteratorColoredRootOopClosure : public OopClosure {
private:
  ZHeapIterator* const        _iter;
  const ZHeapIteratorContext& _context;

  oop load_oop(oop* p) {
    if (Weak) {
      return NativeAccess<AS_NO_KEEPALIVE | ON_PHANTOM_OOP_REF>::oop_load(p);
    }

    return NativeAccess<AS_NO_KEEPALIVE>::oop_load(p);
  }

public:
  ZHeapIteratorColoredRootOopClosure(ZHeapIterator* iter,
                                     const ZHeapIteratorContext& context)
    : _iter(iter),
      _context(context) {}

  virtual void do_oop(oop* p) {
    _context.visit_field(nullptr, p);
    const oop obj = load_oop(p);
    _iter->mark_visit_and_push(_context, obj);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

class ZHeapIteratorUncoloredRootOopClosure : public OopClosure {
private:
  ZHeapIterator* const        _iter;
  const ZHeapIteratorContext& _context;

  oop load_oop(oop* p) {
    const oop o = Atomic::load(p);
    assert_is_valid(to_zaddress(o));
    return RawAccess<>::oop_load(p);
  }

public:
  ZHeapIteratorUncoloredRootOopClosure(ZHeapIterator* iter,
                                       const ZHeapIteratorContext& context)
    : _iter(iter),
      _context(context) {}

  virtual void do_oop(oop* p) {
    _context.visit_field(nullptr, p);
    const oop obj = load_oop(p);
    _iter->mark_visit_and_push(_context, obj);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

class ZHeapIteratorCLDOopClosure : public OopClosure {
private:
  ZHeapIterator* const        _iter;
  const ZHeapIteratorContext& _context;

  oop load_oop(oop* p) {
    assert(!ZCollectedHeap::heap()->is_in(p), "Should not be in heap");
    return NativeAccess<AS_NO_KEEPALIVE>::oop_load(p);
  }

public:
  ZHeapIteratorCLDOopClosure(ZHeapIterator* iter,
                             const ZHeapIteratorContext& context)
    : _iter(iter),
      _context(context) {}

  virtual void do_oop(oop* p) {
    const oop obj = load_oop(p);
    _iter->mark_visit_and_push(_context, obj);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

template <bool VisitReferents>
class ZHeapIteratorOopClosure : public OopIterateClosure {
private:
  ZHeapIterator* const        _iter;
  const ZHeapIteratorContext& _context;
  const oop                   _base;

  oop load_oop(oop* p) {
    assert(ZCollectedHeap::heap()->is_in(p), "Should be in heap");

    if (VisitReferents) {
      return HeapAccess<AS_NO_KEEPALIVE | ON_UNKNOWN_OOP_REF>::oop_load_at(_base, _base->field_offset(p));
    }

    return HeapAccess<AS_NO_KEEPALIVE>::oop_load(p);
  }

public:
  ZHeapIteratorOopClosure(ZHeapIterator* iter,
                          const ZHeapIteratorContext& context,
                          oop base)
    : OopIterateClosure(),
      _iter(iter),
      _context(context),
      _base(base) {}

  virtual ReferenceIterationMode reference_iteration_mode() {
    return VisitReferents ? DO_FIELDS : DO_FIELDS_EXCEPT_REFERENT;
  }

  virtual void do_oop(oop* p) {
    _context.visit_field(_base, p);
    const oop obj = load_oop(p);
    _iter->mark_visit_and_push(_context, obj);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }

  virtual bool do_metadata() {
    return true;
  }

  virtual void do_klass(Klass* k) {
    ClassLoaderData* const cld = k->class_loader_data();
    ZHeapIteratorOopClosure::do_cld(cld);
  }

  virtual void do_cld(ClassLoaderData* cld) {
    ZHeapIteratorCLDOopClosure cl(_iter, _context);
    cld->oops_do(&cl, ClassLoaderData::_claim_other);
  }

  // Don't follow loom stack metadata; it's already followed in other ways through CLDs
  virtual void do_nmethod(nmethod* nm) {}
  virtual void do_method(Method* m) {}
};

ZHeapIterator::ZHeapIterator(uint nworkers,
                             bool visit_weaks,
                             bool for_verify)
  : _visit_weaks(visit_weaks),
    _for_verify(for_verify),
    _bitmaps(ZAddressOffsetMax),
    _bitmaps_lock(),
    _queues(nworkers),
    _array_chunk_queues(nworkers),
    _roots_colored(ZGenerationIdOptional::none),
    _roots_uncolored(ZGenerationIdOptional::none),
    _roots_weak_colored(ZGenerationIdOptional::none),
    _terminator(nworkers, &_queues) {

  // Create queues
  for (uint i = 0; i < _queues.size(); i++) {
    ZHeapIteratorQueue* const queue = new ZHeapIteratorQueue();
    _queues.register_queue(i, queue);
  }

  // Create array chunk queues
  for (uint i = 0; i < _array_chunk_queues.size(); i++) {
    ZHeapIteratorArrayChunkQueue* const array_chunk_queue = new ZHeapIteratorArrayChunkQueue();
    _array_chunk_queues.register_queue(i, array_chunk_queue);
  }
}

ZHeapIterator::~ZHeapIterator() {
  // Destroy bitmaps
  ZHeapIteratorBitMapsIterator iter(&_bitmaps);
  for (ZHeapIteratorBitMap* bitmap; iter.next(&bitmap);) {
    delete bitmap;
  }

  // Destroy array chunk queues
  for (uint i = 0; i < _array_chunk_queues.size(); i++) {
    delete _array_chunk_queues.queue(i);
  }

  // Destroy queues
  for (uint i = 0; i < _queues.size(); i++) {
    delete _queues.queue(i);
  }

  // Clear claimed CLD bits
  ClassLoaderDataGraph::clear_claimed_marks(ClassLoaderData::_claim_other);
}

static size_t object_index_max() {
  return ZGranuleSize >> ZObjectAlignmentSmallShift;
}

static size_t object_index(oop obj) {
  const zaddress addr = to_zaddress(obj);
  const zoffset offset = ZAddress::offset(addr);
  const uintptr_t mask = ZGranuleSize - 1;
  return (untype(offset) & mask) >> ZObjectAlignmentSmallShift;
}

ZHeapIteratorBitMap* ZHeapIterator::object_bitmap(oop obj) {
  const zoffset offset = ZAddress::offset(to_zaddress(obj));
  ZHeapIteratorBitMap* bitmap = _bitmaps.get_acquire(offset);
  if (bitmap == nullptr) {
    ZLocker<ZLock> locker(&_bitmaps_lock);
    bitmap = _bitmaps.get(offset);
    if (bitmap == nullptr) {
      // Install new bitmap
      bitmap = new ZHeapIteratorBitMap(object_index_max());
      _bitmaps.release_put(offset, bitmap);
    }
  }

  return bitmap;
}

bool ZHeapIterator::should_visit_object_at_mark() const {
  // Verify wants to visit objects as soon as they are found.
  return _for_verify;
}

bool ZHeapIterator::should_visit_object_at_follow() const {
  // Non-verify code needs to be careful and visit the objects
  // during the follow stage, where we've completed the root
  // iteration. This prevents lock-ordering problems between
  // the root iterator and the visit closures.
  return !_for_verify;
}

bool ZHeapIterator::mark_object(oop obj) {
  if (obj == nullptr) {
    return false;
  }

  ZHeapIteratorBitMap* const bitmap = object_bitmap(obj);
  const size_t index = object_index(obj);
  return bitmap->try_set_bit(index);
}

typedef ClaimingCLDToOopClosure<ClassLoaderData::_claim_other> ZHeapIteratorCLDClosure;

class ZHeapIteratorNMethodClosure : public NMethodClosure {
private:
  OopClosure* const        _cl;
  BarrierSetNMethod* const _bs_nm;

public:
  ZHeapIteratorNMethodClosure(OopClosure* cl)
    : _cl(cl),
      _bs_nm(BarrierSet::barrier_set()->barrier_set_nmethod()) {}

  virtual void do_nmethod(nmethod* nm) {
    // If ClassUnloading is turned off, all nmethods are considered strong,
    // not only those on the call stacks. The heap iteration might happen
    // before the concurrent processign of the code cache, make sure that
    // all nmethods have been processed before visiting the oops.
    _bs_nm->nmethod_entry_barrier(nm);

    ZNMethod::nmethod_oops_do(nm, _cl);
  }
};

class ZHeapIteratorThreadClosure : public ThreadClosure {
private:
  OopClosure* const     _cl;
  NMethodClosure* const _nm_cl;

public:
  ZHeapIteratorThreadClosure(OopClosure* cl, NMethodClosure* nm_cl)
    : _cl(cl),
      _nm_cl(nm_cl) {}

  void do_thread(Thread* thread) {
    thread->oops_do(_cl, _nm_cl);
  }
};

void ZHeapIterator::push_strong_roots(const ZHeapIteratorContext& context) {
  {
    ZHeapIteratorColoredRootOopClosure<false /* Weak */> cl(this, context);
    ZHeapIteratorCLDClosure cld_cl(&cl);

    _roots_colored.apply(&cl,
                         &cld_cl);
  }

  {
    ZHeapIteratorUncoloredRootOopClosure cl(this, context);
    ZHeapIteratorNMethodClosure nm_cl(&cl);
    ZHeapIteratorThreadClosure thread_cl(&cl, &nm_cl);
    _roots_uncolored.apply(&thread_cl,
                           &nm_cl);
  }
}

void ZHeapIterator::push_weak_roots(const ZHeapIteratorContext& context) {
  ZHeapIteratorColoredRootOopClosure<true  /* Weak */> cl(this, context);
  _roots_weak_colored.apply(&cl);
}

template <bool VisitWeaks>
void ZHeapIterator::push_roots(const ZHeapIteratorContext& context) {
  push_strong_roots(context);
  if (VisitWeaks) {
    push_weak_roots(context);
  }
}

void ZHeapIterator::mark_visit_and_push(const ZHeapIteratorContext& context, oop obj) {
  if (mark_object(obj)) {
    if (should_visit_object_at_mark()) {
      context.visit_object(obj);
    }
    context.push(obj);
  }
}

template <bool VisitReferents>
void ZHeapIterator::follow_object(const ZHeapIteratorContext& context, oop obj) {
  ZHeapIteratorOopClosure<VisitReferents> cl(this, context, obj);
  ZIterator::oop_iterate(obj, &cl);
}

void ZHeapIterator::follow_array(const ZHeapIteratorContext& context, oop obj) {
  // Follow klass
  ZHeapIteratorOopClosure<false /* VisitReferents */> cl(this, context, obj);
  cl.do_klass(obj->klass());

  // Push array chunk
  context.push_array_chunk(ObjArrayTask(obj, 0 /* index */));
}

void ZHeapIterator::follow_array_chunk(const ZHeapIteratorContext& context, const ObjArrayTask& array) {
  const objArrayOop obj = objArrayOop(array.obj());
  const int length = obj->length();
  const int start = array.index();
  const int stride = MIN2<int>(length - start, ObjArrayMarkingStride);
  const int end = start + stride;

  // Push remaining array chunk first
  if (end < length) {
    context.push_array_chunk(ObjArrayTask(obj, end));
  }

  // Follow array chunk
  ZHeapIteratorOopClosure<false /* VisitReferents */> cl(this, context, obj);
  ZIterator::oop_iterate_range(obj, &cl, start, end);
}

template <bool VisitWeaks>
void ZHeapIterator::follow(const ZHeapIteratorContext& context, oop obj) {
  // Follow
  if (obj->is_objArray()) {
    follow_array(context, obj);
  } else {
    follow_object<VisitWeaks>(context, obj);
  }
}

template <bool VisitWeaks>
void ZHeapIterator::visit_and_follow(const ZHeapIteratorContext& context, oop obj) {
  if (should_visit_object_at_follow()) {
    context.visit_object(obj);
  }

  follow<VisitWeaks>(context, obj);
}

template <bool VisitWeaks>
void ZHeapIterator::drain(const ZHeapIteratorContext& context) {
  ObjArrayTask array;
  oop obj;

  do {
    while (context.pop(obj)) {
      visit_and_follow<VisitWeaks>(context, obj);
    }

    if (context.pop_array_chunk(array)) {
      follow_array_chunk(context, array);
    }
  } while (!context.is_drained());
}

template <bool VisitWeaks>
void ZHeapIterator::steal(const ZHeapIteratorContext& context) {
  ObjArrayTask array;
  oop obj;

  if (steal_array_chunk(context, array)) {
    follow_array_chunk(context, array);
  } else if (steal(context, obj)) {
    visit_and_follow<VisitWeaks>(context, obj);
  }
}

bool ZHeapIterator::steal(const ZHeapIteratorContext& context, oop& obj) {
  return _queues.steal(context.worker_id(), obj);
}

bool ZHeapIterator::steal_array_chunk(const ZHeapIteratorContext& context, ObjArrayTask& array) {
  return _array_chunk_queues.steal(context.worker_id(), array);
}

template <bool VisitWeaks>
void ZHeapIterator::drain_and_steal(const ZHeapIteratorContext& context) {
  do {
    drain<VisitWeaks>(context);
    steal<VisitWeaks>(context);
  } while (!context.is_drained() || !_terminator.offer_termination());
}

template <bool VisitWeaks>
void ZHeapIterator::object_iterate_inner(const ZHeapIteratorContext& context) {
  push_roots<VisitWeaks>(context);
  drain_and_steal<VisitWeaks>(context);
}

void ZHeapIterator::object_iterate(ObjectClosure* object_cl, uint worker_id) {
  object_and_field_iterate(object_cl, nullptr /* field_cl */, worker_id);
}

void ZHeapIterator::object_and_field_iterate(ObjectClosure* object_cl, OopFieldClosure* field_cl, uint worker_id) {
  const ZHeapIteratorContext context(object_cl,
                                     field_cl,
                                     worker_id,
                                     _queues.queue(worker_id),
                                     _array_chunk_queues.queue(worker_id));

  if (_visit_weaks) {
    object_iterate_inner<true /* VisitWeaks */>(context);
  } else {
    object_iterate_inner<false /* VisitWeaks */>(context);
  }
}
