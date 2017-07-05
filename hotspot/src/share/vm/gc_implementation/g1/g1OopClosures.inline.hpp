/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

/*
 * This really ought to be an inline function, but apparently the C++
 * compiler sometimes sees fit to ignore inline declarations.  Sigh.
 */

// This must a ifdef'ed because the counting it controls is in a
// perf-critical inner loop.
#define FILTERINTOCSCLOSURE_DOHISTOGRAMCOUNT 0

inline void FilterIntoCSClosure::do_oop_nv(oop* p) {
  oop obj = *p;
  if (obj != NULL && _g1->obj_in_cs(obj)) {
    _oc->do_oop(p);
#if FILTERINTOCSCLOSURE_DOHISTOGRAMCOUNT
    _dcto_cl->incr_count();
#endif
  }
}

inline void FilterIntoCSClosure::do_oop(oop* p)
{
  do_oop_nv(p);
}

#define FILTEROUTOFREGIONCLOSURE_DOHISTOGRAMCOUNT 0

inline void FilterOutOfRegionClosure::do_oop_nv(oop* p) {
  oop obj = *p;
  HeapWord* obj_hw = (HeapWord*)obj;
  if (obj_hw != NULL && (obj_hw < _r_bottom || obj_hw >= _r_end)) {
    _oc->do_oop(p);
#if FILTEROUTOFREGIONCLOSURE_DOHISTOGRAMCOUNT
    _out_of_region++;
#endif
  }
}

inline void FilterOutOfRegionClosure::do_oop(oop* p)
{
  do_oop_nv(p);
}

inline void FilterInHeapRegionAndIntoCSClosure::do_oop_nv(oop* p) {
  oop obj = *p;
  if (obj != NULL && _g1->obj_in_cs(obj))
    _oc->do_oop(p);
}

inline void FilterInHeapRegionAndIntoCSClosure::do_oop(oop* p)
{
  do_oop_nv(p);
}


inline void FilterAndMarkInHeapRegionAndIntoCSClosure::do_oop_nv(oop* p) {
  oop obj = *p;
  if (obj != NULL) {
    HeapRegion* hr = _g1->heap_region_containing((HeapWord*) obj);
    if (hr != NULL) {
      if (hr->in_collection_set())
        _oc->do_oop(p);
      else if (!hr->is_young())
        _cm->grayRoot(obj);
    }
  }
}

inline void FilterAndMarkInHeapRegionAndIntoCSClosure::do_oop(oop* p)
{
  do_oop_nv(p);
}

inline void G1ScanAndBalanceClosure::do_oop_nv(oop* p) {
  RefToScanQueue* q;
  if (ParallelGCThreads > 0) {
    // Deal the work out equally.
    _nq = (_nq + 1) % ParallelGCThreads;
    q = _g1->task_queue(_nq);
  } else {
    q = _g1->task_queue(0);
  }
  bool nooverflow = q->push(p);
  guarantee(nooverflow, "Overflow during poplularity region processing");
}

inline void G1ScanAndBalanceClosure::do_oop(oop* p) {
  do_oop_nv(p);
}
