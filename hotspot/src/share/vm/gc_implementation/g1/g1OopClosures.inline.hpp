/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1OOPCLOSURES_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1OOPCLOSURES_INLINE_HPP

#include "gc_implementation/g1/concurrentMark.inline.hpp"
#include "gc_implementation/g1/g1CollectedHeap.hpp"
#include "gc_implementation/g1/g1OopClosures.hpp"
#include "gc_implementation/g1/g1RemSet.hpp"
#include "gc_implementation/g1/g1RemSet.inline.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"

/*
 * This really ought to be an inline function, but apparently the C++
 * compiler sometimes sees fit to ignore inline declarations.  Sigh.
 */

template <class T>
inline void FilterIntoCSClosure::do_oop_nv(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop) &&
      _g1->obj_in_cs(oopDesc::decode_heap_oop_not_null(heap_oop))) {
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
    if (_g1->in_cset_fast_test(obj)) {
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
      _par_scan_state->update_rs(_from, p, _worker_id);
    }
  }
}

template <class T>
inline void G1ParPushHeapRSClosure::do_oop_nv(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);

  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    if (_g1->in_cset_fast_test(obj)) {
      Prefetch::write(obj->mark_addr(), 0);
      Prefetch::read(obj->mark_addr(), (HeapWordSize*2));

      // Place on the references queue
      _par_scan_state->push_on_queue(p);
    }
  }
}

template <class T>
inline void G1CMOopClosure::do_oop_nv(T* p) {
  assert(_g1h->is_in_g1_reserved((HeapWord*) p), "invariant");
  assert(!_g1h->is_on_master_free_list(
                    _g1h->heap_region_containing((HeapWord*) p)), "invariant");

  oop obj = oopDesc::load_decode_heap_oop(p);
  if (_cm->verbose_high()) {
    gclog_or_tty->print_cr("[%u] we're looking at location "
                           "*"PTR_FORMAT" = "PTR_FORMAT,
                           _task->worker_id(), p, (void*) obj);
  }
  _task->deal_with_reference(obj);
}

template <class T>
inline void G1RootRegionScanClosure::do_oop_nv(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    HeapRegion* hr = _g1h->heap_region_containing((HeapWord*) obj);
    if (hr != NULL) {
      _cm->grayRoot(obj, obj->size(), _worker_id, hr);
    }
  }
}

template <class T>
inline void G1Mux2Closure::do_oop_nv(T* p) {
  // Apply first closure; then apply the second.
  _c1->do_oop(p);
  _c2->do_oop(p);
}

template <class T>
inline void G1TriggerClosure::do_oop_nv(T* p) {
  // Record that this closure was actually applied (triggered).
  _triggered = true;
}

template <class T>
inline void G1InvokeIfNotTriggeredClosure::do_oop_nv(T* p) {
  if (!_trigger_cl->triggered()) {
    _oop_cl->do_oop(p);
  }
}

template <class T>
inline void G1UpdateRSOrPushRefOopClosure::do_oop_nv(T* p) {
  oop obj = oopDesc::load_decode_heap_oop(p);
#ifdef ASSERT
  // can't do because of races
  // assert(obj == NULL || obj->is_oop(), "expected an oop");

  // Do the safe subset of is_oop
  if (obj != NULL) {
#ifdef CHECK_UNHANDLED_OOPS
    oopDesc* o = obj.obj();
#else
    oopDesc* o = obj;
#endif // CHECK_UNHANDLED_OOPS
    assert((intptr_t)o % MinObjAlignmentInBytes == 0, "not oop aligned");
    assert(Universe::heap()->is_in_reserved(obj), "must be in heap");
  }
#endif // ASSERT

  assert(_from != NULL, "from region must be non-NULL");
  assert(_from->is_in_reserved(p), "p is not in from");

  HeapRegion* to = _g1->heap_region_containing(obj);
  if (to != NULL && _from != to) {
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
      return;
    }

    // We either don't care about pushing references that point into the
    // collection set (i.e. we're not during an evacuation pause) _or_
    // the reference doesn't point into the collection set. Either way
    // we add the reference directly to the RSet of the region containing
    // the referenced object.
    assert(to->rem_set() != NULL, "Need per-region 'into' remsets.");
    to->rem_set()->add_reference(p, _worker_i);
  }
}

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1OOPCLOSURES_INLINE_HPP
