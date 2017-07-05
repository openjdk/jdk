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
#include "gc_implementation/g1/heapRegionSets.hpp"

//////////////////// FreeRegionList ////////////////////

const char* FreeRegionList::verify_region_extra(HeapRegion* hr) {
  if (hr->is_young()) {
    return "the region should not be young";
  }
  // The superclass will check that the region is empty and
  // not-humongous.
  return HeapRegionLinkedList::verify_region_extra(hr);
}

//////////////////// MasterFreeRegionList ////////////////////

bool MasterFreeRegionList::check_mt_safety() {
  // Master Free List MT safety protocol:
  // (a) If we're at a safepoint, operations on the master free list
  // should be invoked by either the VM thread (which will serialize
  // them) or by the GC workers while holding the
  // FreeList_lock.
  // (b) If we're not at a safepoint, operations on the master free
  // list should be invoked while holding the Heap_lock.

  guarantee((SafepointSynchronize::is_at_safepoint() &&
               (Thread::current()->is_VM_thread() ||
                                            FreeList_lock->owned_by_self())) ||
            (!SafepointSynchronize::is_at_safepoint() &&
                                                Heap_lock->owned_by_self()),
            hrs_ext_msg(this, "master free list MT safety protocol"));

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

  guarantee((SafepointSynchronize::is_at_safepoint() &&
               (Thread::current()->is_VM_thread() ||
                                             OldSets_lock->owned_by_self())) ||
            (!SafepointSynchronize::is_at_safepoint() &&
                                                 Heap_lock->owned_by_self()),
            hrs_ext_msg(this, "master humongous set MT safety protocol"));
  return HumongousRegionSet::check_mt_safety();
}
