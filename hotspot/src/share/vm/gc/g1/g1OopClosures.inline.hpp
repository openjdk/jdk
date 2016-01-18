/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/concurrentMark.inline.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1OopClosures.hpp"
#include "gc/g1/g1ParScanThreadState.inline.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1RemSet.inline.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "memory/iterator.inline.hpp"
#include "runtime/prefetch.inline.hpp"

/*
 * This really ought to be an inline function, but apparently the C++
 * compiler sometimes sees fit to ignore inline declarations.  Sigh.
 */

template <class T>
inline void FilterIntoCSClosure::do_oop_work(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop) &&
      _g1->is_in_cset_or_humongous(oopDesc::decode_heap_oop_not_null(heap_oop))) {
    _oc->do_oop(p);
  }
}

template <class T>
inline void FilterOutOfRegionClosure::do_oop_nv(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    HeapWord* obj_hw = (HeapWord*)oopDesc::decode_heap_oop_not_null(heap_oop);
    if (obj_hw < _r_bottom || obj_hw >= _r_end) {
      _oc->do_oop(p);
    }
  }
}

// This closure is applied to the fields of the objects that have just been copied.
template <class T>
inline void G1ParScanClosure::do_oop_nv(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);

  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    const InCSetState state = _g1->in_cset_state(obj);
    if (state.is_in_cset()) {
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
    } else {
      if (state.is_humongous()) {
        _g1->set_humongous_is_live(obj);
      } else if (state.is_ext()) {
        _par_scan_state->do_oop_ext(p);
      }
      _par_scan_state->update_rs(_from, p, obj);
    }
  }
}

template <class T>
inline void G1ParPushHeapRSClosure::do_oop_nv(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);

  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    const InCSetState state = _g1->in_cset_state(obj);
    if (state.is_in_cset_or_humongous()) {
      Prefetch::write(obj->mark_addr(), 0);
      Prefetch::read(obj->mark_addr(), (HeapWordSize*2));

      // Place on the references queue
      _par_scan_state->push_on_queue(p);
    } else if (state.is_ext()) {
      _par_scan_state->do_oop_ext(p);
    } else {
      assert(!_g1->obj_in_cs(obj), "checking");
    }
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
    _cm->grayRoot(obj, obj->size(), _worker_id, hr);
  }
}

template <class T>
inline void G1Mux2Closure::do_oop_work(T* p) {
  // Apply first closure; then apply the second.
  _c1->do_oop(p);
  _c2->do_oop(p);
}
void G1Mux2Closure::do_oop(oop* p)       { do_oop_work(p); }
void G1Mux2Closure::do_oop(narrowOop* p) { do_oop_work(p); }

template <class T>
inline void G1TriggerClosure::do_oop_work(T* p) {
  // Record that this closure was actually applied (triggered).
  _triggered = true;
}
void G1TriggerClosure::do_oop(oop* p)       { do_oop_work(p); }
void G1TriggerClosure::do_oop(narrowOop* p) { do_oop_work(p); }

template <class T>
inline void G1InvokeIfNotTriggeredClosure::do_oop_work(T* p) {
  if (!_trigger_cl->triggered()) {
    _oop_cl->do_oop(p);
  }
}
void G1InvokeIfNotTriggeredClosure::do_oop(oop* p)       { do_oop_work(p); }
void G1InvokeIfNotTriggeredClosure::do_oop(narrowOop* p) { do_oop_work(p); }

template <class T>
inline void G1UpdateRSOrPushRefOopClosure::do_oop_work(T* p) {
  oop obj = oopDesc::load_decode_heap_oop(p);
  if (obj == NULL) {
    return;
  }

#ifdef ASSERT
  // can't do because of races
  // assert(obj == NULL || obj->is_oop(), "expected an oop");

  // Do the safe subset of is_oop
#ifdef CHECK_UNHANDLED_OOPS
  oopDesc* o = obj.obj();
#else
  oopDesc* o = obj;
#endif // CHECK_UNHANDLED_OOPS
  assert((intptr_t)o % MinObjAlignmentInBytes == 0, "not oop aligned");
  assert(_g1->is_in_reserved(obj), "must be in heap");
#endif // ASSERT

  assert(_from != NULL, "from region must be non-NULL");
  assert(_from->is_in_reserved(p), "p is not in from");

  HeapRegion* to = _g1->heap_region_containing(obj);
  if (_from == to) {
    // Normally this closure should only be called with cross-region references.
    // But since Java threads are manipulating the references concurrently and we
    // reload the values things may have changed.
    return;
  }

  // The _record_refs_into_cset flag is true during the RSet
  // updating part of an evacuation pause. It is false at all
  // other times:
  //  * rebuilding the remembered sets after a full GC
  //  * during concurrent refinement.
  //  * updating the remembered sets of regions in the collection
  //    set in the event of an evacuation failure (when deferred
  //    updates are enabled).

  if (_record_refs_into_cset && to->in_collection_set()) {
    // We are recording references that point into the collection
    // set and this particular reference does exactly that...
    // If the referenced object has already been forwarded
    // to itself, we are handling an evacuation failure and
    // we have already visited/tried to copy this object
    // there is no need to retry.
    if (!self_forwarded(obj)) {
      assert(_push_ref_cl != NULL, "should not be null");
      // Push the reference in the refs queue of the G1ParScanThreadState
      // instance for this worker thread.
      _push_ref_cl->do_oop(p);
    }

    // Deferred updates to the CSet are either discarded (in the normal case),
    // or processed (if an evacuation failure occurs) at the end
    // of the collection.
    // See G1RemSet::cleanup_after_oops_into_collection_set_do().
  } else {
    // We either don't care about pushing references that point into the
    // collection set (i.e. we're not during an evacuation pause) _or_
    // the reference doesn't point into the collection set. Either way
    // we add the reference directly to the RSet of the region containing
    // the referenced object.
    assert(to->rem_set() != NULL, "Need per-region 'into' remsets.");
    to->rem_set()->add_reference(p, _worker_i);
  }
}
void G1UpdateRSOrPushRefOopClosure::do_oop(oop* p)       { do_oop_work(p); }
void G1UpdateRSOrPushRefOopClosure::do_oop(narrowOop* p) { do_oop_work(p); }

template <class T>
void G1ParCopyHelper::do_klass_barrier(T* p, oop new_obj) {
  if (_g1->heap_region_containing(new_obj)->is_young()) {
    _scanned_klass->record_modified_oops();
  }
}

void G1ParCopyHelper::mark_object(oop obj) {
  assert(!_g1->heap_region_containing(obj)->in_collection_set(), "should not mark objects in the CSet");

  // We know that the object is not moving so it's safe to read its size.
  _cm->grayRoot(obj, (size_t) obj->size(), _worker_id);
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
  _cm->grayRoot(to_obj, (size_t) from_obj->size(), _worker_id);
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
