/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "code/nmethod.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/g1/g1NMethodClosure.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"

template <typename T>
void G1NMethodClosure::HeapRegionGatheringOopClosure::do_oop_work(T* p) {
  T old_oop_or_narrowoop = RawAccess<>::oop_load(p);

  _work->do_oop(p);
  T oop_or_narrowoop = RawAccess<>::oop_load(p);
  // If the oop moved, we need to update the code root set at the new location. If it did not
  // change, it is either in the existing code root set, or an earlier evacuation round already
  // enqueued it for deferred update.
  //
  // We defer actual update to the code roots to later. This can, in presence of optional
  // collections, ultimately result in duplicates in the per-thread code root set update list.
  // We consider this negligible, given that optional collection is rare and typically does
  // not cover many regions/nmethods.
  if (oop_or_narrowoop != old_oop_or_narrowoop) {
    // If the oop moved, it must not have been null.
    assert(!CompressedOops::is_null(oop_or_narrowoop), "must be");
    oop o = CompressedOops::decode_not_null(oop_or_narrowoop);
    assert(!_g1h->is_in_cset(o), "must be");

    G1HeapRegion* hr = _g1h->heap_region_containing(o);
    _affected_regions.append_if_missing(hr);
  } else {
    // We could be tempted to verify that for a non-null oop, the _nm is already in the target code root
    // set or in one of the deferred code root set update lists. It would not be sufficient to verify the
    // current thread's list, because across evacuation rounds (i.e. initial/multiple optional) different
    // threads may have worked on a given oop from an nmethod.
    // This is rather expensive, not only requiring looking at all threads' lists, but also making sure
    // that there are no memory ordering issues when doing that. So we skip it.
  }
}

G1NMethodClosure::HeapRegionGatheringOopClosure::HeapRegionGatheringOopClosure(OopClosure* oc, G1ParScanThreadState* pss) :
  _g1h(G1CollectedHeap::heap()),
  _work(oc),
  _pss(pss),
  _nm(nullptr),
  _affected_regions(5) {
}

void G1NMethodClosure::HeapRegionGatheringOopClosure::add_to_remsets() {
  while (!_affected_regions.is_empty()) {
    _pss->remember_nmethod_into_region(_affected_regions.pop(), _nm);
  }
}

void G1NMethodClosure::HeapRegionGatheringOopClosure::do_oop(oop* o) {
  do_oop_work(o);
}

void G1NMethodClosure::HeapRegionGatheringOopClosure::do_oop(narrowOop* o) {
  do_oop_work(o);
}

template<typename T>
void G1NMethodClosure::MarkingOopClosure::do_oop_work(T* p) {
  T oop_or_narrowoop = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(oop_or_narrowoop)) {
    oop o = CompressedOops::decode_not_null(oop_or_narrowoop);
    _cm->mark_in_bitmap(_worker_id, o);
  }
}

G1NMethodClosure::MarkingOopClosure::MarkingOopClosure(uint worker_id) :
  _cm(G1CollectedHeap::heap()->concurrent_mark()), _worker_id(worker_id) { }

void G1NMethodClosure::MarkingOopClosure::do_oop(oop* o) {
  do_oop_work(o);
}

void G1NMethodClosure::MarkingOopClosure::do_oop(narrowOop* o) {
  do_oop_work(o);
}

void G1NMethodClosure::do_evacuation_and_fixup(nmethod* nm) {
  _oc.set_nmethod(nm);

  // Evacuate objects pointed to by the nmethod
  nm->oops_do(&_oc);

  _oc.add_to_remsets();

  if (_strong) {
    // CodeCache unloading support
    nm->mark_as_maybe_on_stack();

    BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
    bs_nm->disarm(nm);
  }

  nm->fix_oop_relocations();
}

void G1NMethodClosure::do_marking(nmethod* nm) {
  // Mark through oops in the nmethod
  nm->oops_do(&_marking_oc);

  // CodeCache unloading support
  nm->mark_as_maybe_on_stack();

  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  bs_nm->disarm(nm);

  // The oops were only marked, no need to update oop relocations.
}

class G1NmethodProcessor : public nmethod::OopsDoProcessor {
  G1NMethodClosure* _cl;

public:
  G1NmethodProcessor(G1NMethodClosure* cl) : _cl(cl) { }

  void do_regular_processing(nmethod* nm) {
    _cl->do_evacuation_and_fixup(nm);
  }

  void do_remaining_strong_processing(nmethod* nm) {
    _cl->do_marking(nm);
  }
};

void G1NMethodClosure::do_nmethod(nmethod* nm) {
  assert(nm != nullptr, "Sanity");

  G1NmethodProcessor cl(this);

  if (_strong) {
    nm->oops_do_process_strong(&cl);
  } else {
    nm->oops_do_process_weak(&cl);
  }
}
