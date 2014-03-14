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
#include "gc_implementation/g1/heapRegionSet.hpp"

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

void FreeRegionList::verify_list() {
  HeapRegion* curr = head();
  HeapRegion* prev1 = NULL;
  HeapRegion* prev0 = NULL;
  uint count = 0;
  size_t capacity = 0;
  while (curr != NULL) {
    verify_region(curr);

    count++;
    guarantee(count < _unrealistically_long_length,
        hrs_err_msg("[%s] the calculated length: %u seems very long, is there maybe a cycle? curr: "PTR_FORMAT" prev0: "PTR_FORMAT" " "prev1: "PTR_FORMAT" length: %u", name(), count, curr, prev0, prev1, length()));

    capacity += curr->capacity();

    prev1 = prev0;
    prev0 = curr;
    curr = curr->next();
  }

  guarantee(tail() == prev0, err_msg("Expected %s to end with %u but it ended with %u.", name(), tail()->hrs_index(), prev0->hrs_index()));

  guarantee(length() == count, err_msg("%s count mismatch. Expected %u, actual %u.", name(), length(), count));
  guarantee(total_capacity_bytes() == capacity, err_msg("%s capacity mismatch. Expected " SIZE_FORMAT ", actual " SIZE_FORMAT,
      name(), total_capacity_bytes(), capacity));
}

void MasterFreeRegionListMtSafeChecker::check() {
  // Master Free List MT safety protocol:
  // (a) If we're at a safepoint, operations on the master free list
  // should be invoked by either the VM thread (which will serialize
  // them) or by the GC workers while holding the
  // FreeList_lock.
  // (b) If we're not at a safepoint, operations on the master free
  // list should be invoked while holding the Heap_lock.

  if (SafepointSynchronize::is_at_safepoint()) {
    guarantee(Thread::current()->is_VM_thread() ||
              FreeList_lock->owned_by_self(), "master free list MT safety protocol at a safepoint");
  } else {
    guarantee(Heap_lock->owned_by_self(), "master free list MT safety protocol outside a safepoint");
  }
}

void SecondaryFreeRegionListMtSafeChecker::check() {
  // Secondary Free List MT safety protocol:
  // Operations on the secondary free list should always be invoked
  // while holding the SecondaryFreeList_lock.

  guarantee(SecondaryFreeList_lock->owned_by_self(), "secondary free list MT safety protocol");
}

void OldRegionSetMtSafeChecker::check() {
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
    guarantee(Thread::current()->is_VM_thread()
        || FreeList_lock->owned_by_self() || OldSets_lock->owned_by_self(),
        "master old set MT safety protocol at a safepoint");
  } else {
    guarantee(Heap_lock->owned_by_self(), "master old set MT safety protocol outside a safepoint");
  }
}

void HumongousRegionSetMtSafeChecker::check() {
  // Humongous Set MT safety protocol:
  // (a) If we're at a safepoint, operations on the master humongous
  // set should be invoked by either the VM thread (which will
  // serialize them) or by the GC workers while holding the
  // OldSets_lock.
  // (b) If we're not at a safepoint, operations on the master
  // humongous set should be invoked while holding the Heap_lock.

  if (SafepointSynchronize::is_at_safepoint()) {
    guarantee(Thread::current()->is_VM_thread() ||
              OldSets_lock->owned_by_self(),
              "master humongous set MT safety protocol at a safepoint");
  } else {
    guarantee(Heap_lock->owned_by_self(),
              "master humongous set MT safety protocol outside a safepoint");
  }
}
