/*
 * Copyright (c) 2001, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1OOPCLOSURES_INLINE_HPP
#define SHARE_VM_GC_G1_G1OOPCLOSURES_INLINE_HPP

#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1OopClosures.hpp"
#include "gc/g1/g1ParScanThreadState.inline.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1RemSet.inline.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "memory/iterator.inline.hpp"
#include "runtime/prefetch.inline.hpp"

template <class T>
inline void G1ScanClosureBase::prefetch_and_push(T* p, const oop obj) {
  // We're not going to even bother checking whether the object is
  // already forwarded or not, as this usually causes an immediate
  // stall. We'll try to prefetch the object (for write, given that
  // we might need to install the forwarding reference) and we'll
  // get back to it when pop it from the queue
  Prefetch::write(obj->mark_addr(), 0);
  Prefetch::read(obj->mark_addr(), (HeapWordSize*2));

  // slightly paranoid test; I'm trying to catch potential
  // problems before we go into push_on_queue to know where the
  // problem is coming from
  assert((obj == oopDesc::load_decode_heap_oop(p)) ||
         (obj->is_forwarded() &&
         obj->forwardee() == oopDesc::load_decode_heap_oop(p)),
         "p should still be pointing to obj or to its forwardee");

  _par_scan_state->push_on_queue(p);
}

template <class T>
inline void G1ScanClosureBase::handle_non_cset_obj_common(InCSetState const state, T* p, oop const obj) {
  if (state.is_humongous()) {
    _g1->set_humongous_is_live(obj);
  } else if (state.is_ext()) {
    _par_scan_state->do_oop_ext(p);
  }
}

template <class T>
inline void G1ScanEvacuatedObjClosure::do_oop_nv(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);

  if (oopDesc::is_null(heap_oop)) {
    return;
  }
  oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
  const InCSetState state = _g1->in_cset_state(obj);
  if (state.is_in_cset()) {
    prefetch_and_push(p, obj);
  } else {
    handle_non_cset_obj_common(state, p, obj);

    _par_scan_state->update_rs(_from, p, obj);
  }
}

template <class T>
inline void G1CMOopClosure::do_oop_nv(T* p) {
  oop obj = oopDesc::load_decode_heap_oop(p);
  _task->deal_with_reference(obj);
}

template <class T>
inline void G1RootRegionScanClosure::do_oop_nv(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    HeapRegion* hr = _g1h->heap_region_containing((HeapWord*) obj);
    _cm->grayRoot(obj, hr);
  }
}

template <class T>
inline static void check_obj_during_refinement(T* p, oop const obj) {
#ifdef ASSERT
  G1CollectedHeap* g1 = G1CollectedHeap::heap();
  // can't do because of races
  // assert(obj == NULL || obj->is_oop(), "expected an oop");
  assert(check_obj_alignment(obj), "not oop aligned");
  assert(g1->is_in_reserved(obj), "must be in heap");

  HeapRegion* from = g1->heap_region_containing(p);

  assert(from != NULL, "from region must be non-NULL");
  assert(from->is_in_reserved(p) ||
         (from->is_humongous() &&
          g1->heap_region_containing(p)->is_humongous() &&
          from->humongous_start_region() == g1->heap_region_containing(p)->humongous_start_region()),
         "p " PTR_FORMAT " is not in the same region %u or part of the correct humongous object starting at region %u.",
         p2i(p), from->hrm_index(), from->humongous_start_region()->hrm_index());
#endif // ASSERT
}

template <class T>
inline void G1ConcurrentRefineOopClosure::do_oop_nv(T* p) {
  T o = oopDesc::load_heap_oop(p);
  if (oopDesc::is_null(o)) {
    return;
  }
  oop obj = oopDesc::decode_heap_oop_not_null(o);

  check_obj_during_refinement(p, obj);

  if (HeapRegion::is_in_same_region(p, obj)) {
    // Normally this closure should only be called with cross-region references.
    // But since Java threads are manipulating the references concurrently and we
    // reload the values things may have changed.
    // Also this check lets slip through references from a humongous continues region
    // to its humongous start region, as they are in different regions, and adds a
    // remembered set entry. This is benign (apart from memory usage), as we never
    // try to either evacuate or eager reclaim humonguous arrays of j.l.O.
    return;
  }

  HeapRegion* to = _g1->heap_region_containing(obj);

  assert(to->rem_set() != NULL, "Need per-region 'into' remsets.");
  to->rem_set()->add_reference(p, _worker_i);
}

template <class T>
inline void G1ScanObjsDuringUpdateRSClosure::do_oop_nv(T* p) {
  T o = oopDesc::load_heap_oop(p);
  if (oopDesc::is_null(o)) {
    return;
  }
  oop obj = oopDesc::decode_heap_oop_not_null(o);

  check_obj_during_refinement(p, obj);

  assert(!_g1->is_in_cset((HeapWord*)p), "Oop originates from " PTR_FORMAT " (region: %u) which is in the collection set.", p2i(p), _g1->addr_to_region((HeapWord*)p));
  const InCSetState state = _g1->in_cset_state(obj);
  if (state.is_in_cset()) {
    // Since the source is always from outside the collection set, here we implicitly know
    // that this is a cross-region reference too.
    prefetch_and_push(p, obj);

    _has_refs_into_cset = true;
  } else {
    HeapRegion* to = _g1->heap_region_containing(obj);
    if (_from == to) {
      return;
    }

    handle_non_cset_obj_common(state, p, obj);

    to->rem_set()->add_reference(p, _worker_i);
  }
}

template <class T>
inline void G1ScanObjsDuringScanRSClosure::do_oop_nv(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (oopDesc::is_null(heap_oop)) {
    return;
  }
  oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);

  const InCSetState state = _g1->in_cset_state(obj);
  if (state.is_in_cset()) {
    prefetch_and_push(p, obj);
  } else {
    handle_non_cset_obj_common(state, p, obj);
  }
}

template <class T>
void G1ParCopyHelper::do_klass_barrier(T* p, oop new_obj) {
  if (_g1->heap_region_containing(new_obj)->is_young()) {
    _scanned_klass->record_modified_oops();
  }
}

void G1ParCopyHelper::mark_object(oop obj) {
  assert(!_g1->heap_region_containing(obj)->in_collection_set(), "should not mark objects in the CSet");

  // We know that the object is not moving so it's safe to read its size.
  _cm->grayRoot(obj);
}

void G1ParCopyHelper::mark_forwarded_object(oop from_obj, oop to_obj) {
  assert(from_obj->is_forwarded(), "from obj should be forwarded");
  assert(from_obj->forwardee() == to_obj, "to obj should be the forwardee");
  assert(from_obj != to_obj, "should not be self-forwarded");

  assert(_g1->heap_region_containing(from_obj)->in_collection_set(), "from obj should be in the CSet");
  assert(!_g1->heap_region_containing(to_obj)->in_collection_set(), "should not mark objects in the CSet");

  // The object might be in the process of being copied by another
  // worker so we cannot trust that its to-space image is
  // well-formed. So we have to read its size from its from-space
  // image which we know should not be changing.
  _cm->grayRoot(to_obj);
}

template <G1Barrier barrier, G1Mark do_mark_object, bool use_ext>
template <class T>
void G1ParCopyClosure<barrier, do_mark_object, use_ext>::do_oop_work(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);

  if (oopDesc::is_null(heap_oop)) {
    return;
  }

  oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);

  assert(_worker_id == _par_scan_state->worker_id(), "sanity");

  const InCSetState state = _g1->in_cset_state(obj);
  if (state.is_in_cset()) {
    oop forwardee;
    markOop m = obj->mark();
    if (m->is_marked()) {
      forwardee = (oop) m->decode_pointer();
    } else {
      forwardee = _par_scan_state->copy_to_survivor_space(state, obj, m);
    }
    assert(forwardee != NULL, "forwardee should not be NULL");
    oopDesc::encode_store_heap_oop(p, forwardee);
    if (do_mark_object != G1MarkNone && forwardee != obj) {
      // If the object is self-forwarded we don't need to explicitly
      // mark it, the evacuation failure protocol will do so.
      mark_forwarded_object(obj, forwardee);
    }

    if (barrier == G1BarrierKlass) {
      do_klass_barrier(p, forwardee);
    }
  } else {
    if (state.is_humongous()) {
      _g1->set_humongous_is_live(obj);
    }

    if (use_ext && state.is_ext()) {
      _par_scan_state->do_oop_ext(p);
    }
    // The object is not in collection set. If we're a root scanning
    // closure during an initial mark pause then attempt to mark the object.
    if (do_mark_object == G1MarkFromRoot) {
      mark_object(obj);
    }
  }
}

#endif // SHARE_VM_GC_G1_G1OOPCLOSURES_INLINE_HPP
