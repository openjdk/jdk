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

/*
 * This really ought to be an inline function, but apparently the C++
 * compiler sometimes sees fit to ignore inline declarations.  Sigh.
 */

// This must a ifdef'ed because the counting it controls is in a
// perf-critical inner loop.
#define FILTERINTOCSCLOSURE_DOHISTOGRAMCOUNT 0

template <class T> inline void FilterIntoCSClosure::do_oop_nv(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop) &&
      _g1->obj_in_cs(oopDesc::decode_heap_oop_not_null(heap_oop))) {
    _oc->do_oop(p);
#if FILTERINTOCSCLOSURE_DOHISTOGRAMCOUNT
    if (_dcto_cl != NULL)
      _dcto_cl->incr_count();
#endif
  }
}

#define FILTEROUTOFREGIONCLOSURE_DOHISTOGRAMCOUNT 0

template <class T> inline void FilterOutOfRegionClosure::do_oop_nv(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    HeapWord* obj_hw = (HeapWord*)oopDesc::decode_heap_oop_not_null(heap_oop);
    if (obj_hw < _r_bottom || obj_hw >= _r_end) {
      _oc->do_oop(p);
#if FILTEROUTOFREGIONCLOSURE_DOHISTOGRAMCOUNT
      _out_of_region++;
#endif
    }
  }
}

template <class T> inline void FilterInHeapRegionAndIntoCSClosure::do_oop_nv(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop) &&
      _g1->obj_in_cs(oopDesc::decode_heap_oop_not_null(heap_oop)))
    _oc->do_oop(p);
}

template <class T> inline void FilterAndMarkInHeapRegionAndIntoCSClosure::do_oop_nv(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    HeapRegion* hr = _g1->heap_region_containing((HeapWord*) obj);
    if (hr != NULL) {
      if (hr->in_collection_set())
        _oc->do_oop(p);
      else if (!hr->is_young())
        _cm->grayRoot(obj);
    }
  }
}

// This closure is applied to the fields of the objects that have just been copied.
template <class T> inline void G1ParScanClosure::do_oop_nv(T* p) {
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
      assert(obj == oopDesc::load_decode_heap_oop(p),
             "p should still be pointing to obj");
      _par_scan_state->push_on_queue(p);
    } else {
      _par_scan_state->update_rs(_from, p, _par_scan_state->queue_num());
    }
  }
}

template <class T> inline void G1ParPushHeapRSClosure::do_oop_nv(T* p) {
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

