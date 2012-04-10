/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#include "gc_implementation/g1/heapRegionSets.hpp"

// Note on the check_mt_safety() methods below:
//
// Verification of the "master" heap region sets / lists that are
// maintained by G1CollectedHeap is always done during a STW pause and
// by the VM thread at the start / end of the pause. The standard
// verification methods all assert check_mt_safety(). This is
// important as it ensures that verification is done without
// concurrent updates taking place at the same time. It follows, that,
// for the "master" heap region sets / lists, the check_mt_safety()
// method should include the VM thread / STW case.

//////////////////// FreeRegionList ////////////////////

const char* FreeRegionList::verify_region_extra(HeapRegion* hr) {
  if (hr->is_young()) {
    return "the region should not be young";
  }
  // The superclass will check that the region is empty and
  // not humongous.
  return HeapRegionLinkedList::verify_region_extra(hr);
}

//////////////////// MasterFreeRegionList ////////////////////

const char* MasterFreeRegionList::verify_region_extra(HeapRegion* hr) {
  // We should reset the RSet for parallel iteration before we add it
  // to the master free list so that it is ready when the region is
  // re-allocated.
  if (!hr->rem_set()->verify_ready_for_par_iteration()) {
    return "the region's RSet should be ready for parallel iteration";
  }
  return FreeRegionList::verify_region_extra(hr);
}

bool MasterFreeRegionList::check_mt_safety() {
  // Master Free List MT safety protocol:
  // (a) If we're at a safepoint, operations on the master free list
  // should be invoked by either the VM thread (which will serialize
  // them) or by the GC workers while holding the
  // FreeList_lock.
  // (b) If we're not at a safepoint, operations on the master free
  // list should be invoked while holding the Heap_lock.

  if (SafepointSynchronize::is_at_safepoint()) {
    guarantee(Thread::current()->is_VM_thread() ||
              FreeList_lock->owned_by_self(),
              hrs_ext_msg(this, "master free list MT safety protocol "
                                "at a safepoint"));
  } else {
    guarantee(Heap_lock->owned_by_self(),
              hrs_ext_msg(this, "master free list MT safety protocol "
                                "outside a safepoint"));
  }

  return FreeRegionList::check_mt_safety();
}

//////////////////// SecondaryFreeRegionList ////////////////////

bool SecondaryFreeRegionList::check_mt_safety() {
  // Secondary Free List MT safety protocol:
  // Operations on the secondary free list should always be invoked
  // while holding the SecondaryFreeList_lock.

  guarantee(SecondaryFreeList_lock->owned_by_self(),
            hrs_ext_msg(this, "secondary free list MT safety protocol"));

  return FreeRegionList::check_mt_safety();
}

//////////////////// OldRegionSet ////////////////////

const char* OldRegionSet::verify_region_extra(HeapRegion* hr) {
  if (hr->is_young()) {
    return "the region should not be young";
  }
  // The superclass will check that the region is not empty and not
  // humongous.
  return HeapRegionSet::verify_region_extra(hr);
}

//////////////////// MasterOldRegionSet ////////////////////

bool MasterOldRegionSet::check_mt_safety() {
  // Master Old Set MT safety protocol:
  // (a) If we're at a safepoint, operations on the master old set
  // should be invoked:
  // - by the VM thread (which will serialize them), or
  // - by the GC workers while holding the FreeList_lock, if we're
  //   at a safepoint for an evacuation pause (this lock is taken
  //   anyway when an GC alloc region is retired so that a new one
  //   is allocated from the free list), or
  // - by the GC workers while holding the OldSets_lock, if we're at a
  //   safepoint for a cleanup pause.
  // (b) If we're not at a safepoint, operations on the master old set
  // should be invoked while holding the Heap_lock.

  if (SafepointSynchronize::is_at_safepoint()) {
    guarantee(Thread::current()->is_VM_thread() ||
              _phase == HRSPhaseEvacuation && FreeList_lock->owned_by_self() ||
              _phase == HRSPhaseCleanup && OldSets_lock->owned_by_self(),
              hrs_ext_msg(this, "master old set MT safety protocol "
                                "at a safepoint"));
  } else {
    guarantee(Heap_lock->owned_by_self(),
              hrs_ext_msg(this, "master old set MT safety protocol "
                                "outside a safepoint"));
  }

  return OldRegionSet::check_mt_safety();
}

//////////////////// HumongousRegionSet ////////////////////

const char* HumongousRegionSet::verify_region_extra(HeapRegion* hr) {
  if (hr->is_young()) {
    return "the region should not be young";
  }
  // The superclass will check that the region is not empty and
  // humongous.
  return HeapRegionSet::verify_region_extra(hr);
}

//////////////////// MasterHumongousRegionSet ////////////////////

bool MasterHumongousRegionSet::check_mt_safety() {
  // Master Humongous Set MT safety protocol:
  // (a) If we're at a safepoint, operations on the master humongous
  // set should be invoked by either the VM thread (which will
  // serialize them) or by the GC workers while holding the
  // OldSets_lock.
  // (b) If we're not at a safepoint, operations on the master
  // humongous set should be invoked while holding the Heap_lock.

  if (SafepointSynchronize::is_at_safepoint()) {
    guarantee(Thread::current()->is_VM_thread() ||
              OldSets_lock->owned_by_self(),
              hrs_ext_msg(this, "master humongous set MT safety protocol "
                                "at a safepoint"));
  } else {
    guarantee(Heap_lock->owned_by_self(),
              hrs_ext_msg(this, "master humongous set MT safety protocol "
                                "outside a safepoint"));
  }

  return HumongousRegionSet::check_mt_safety();
}
