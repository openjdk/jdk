/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

inline size_t G1RemSet::n_workers() {
  if (_g1->workers() != NULL) {
    return _g1->workers()->total_workers();
  } else {
    return 1;
  }
}

template <class T>
inline void G1RemSet::write_ref(HeapRegion* from, T* p) {
  par_write_ref(from, p, 0);
}

template <class T>
inline void G1RemSet::par_write_ref(HeapRegion* from, T* p, int tid) {
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

  assert(from == NULL || from->is_in_reserved(p), "p is not in from");

  HeapRegion* to = _g1->heap_region_containing(obj);
  if (to != NULL && from != to) {
#if G1_REM_SET_LOGGING
    gclog_or_tty->print_cr("Adding " PTR_FORMAT " (" PTR_FORMAT ") to RS"
                           " for region [" PTR_FORMAT ", " PTR_FORMAT ")",
                           p, obj,
                           to->bottom(), to->end());
#endif
    assert(to->rem_set() != NULL, "Need per-region 'into' remsets.");
    to->rem_set()->add_reference(p, tid);
  }
}

template <class T>
inline void UpdateRSOopClosure::do_oop_work(T* p) {
  assert(_from != NULL, "from region must be non-NULL");
  _rs->par_write_ref(_from, p, _worker_i);
}

template <class T>
inline void UpdateRSetImmediate::do_oop_work(T* p) {
  assert(_from->is_in_reserved(p), "paranoia");
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop) && !_from->is_survivor()) {
    _g1_rem_set->par_write_ref(_from, p, 0);
  }
}

template <class T>
inline void UpdateRSOrPushRefOopClosure::do_oop_work(T* p) {
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

  HeapRegion* to = _g1->heap_region_containing(obj);
  if (to != NULL && _from != to) {
    // The _record_refs_into_cset flag is true during the RSet
    // updating part of an evacuation pause. It is false at all
    // other times:
    //  * rebuilding the rembered sets after a full GC
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
      _g1_rem_set->par_write_ref(_from, p, _worker_i);
    }
  }
}

