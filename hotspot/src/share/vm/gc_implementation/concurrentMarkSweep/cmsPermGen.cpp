/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_cmsPermGen.cpp.incl"

CMSPermGen::CMSPermGen(ReservedSpace rs, size_t initial_byte_size,
             CardTableRS* ct,
             FreeBlockDictionary::DictionaryChoice dictionaryChoice) {
  CMSPermGenGen* g =
    new CMSPermGenGen(rs, initial_byte_size, -1, ct);
  if (g == NULL) {
    vm_exit_during_initialization("Could not allocate a CompactingPermGen");
  }

  g->initialize_performance_counters();

  _gen = g;
}

HeapWord* CMSPermGen::mem_allocate(size_t size) {
  Mutex* lock = _gen->freelistLock();
  bool lock_owned = lock->owned_by_self();
  if (lock_owned) {
    MutexUnlocker mul(lock);
    return mem_allocate_work(size);
  } else {
    return mem_allocate_work(size);
  }
}

HeapWord* CMSPermGen::mem_allocate_work(size_t size) {
  assert(!_gen->freelistLock()->owned_by_self(), "Potetntial deadlock");

  MutexLocker ml(Heap_lock);
  HeapWord* obj = NULL;

  obj = _gen->allocate(size, false);
  // Since we want to minimize pause times, we will prefer
  // expanding the perm gen rather than doing a stop-world
  // collection to satisfy the allocation request.
  if (obj == NULL) {
    // Try to expand the perm gen and allocate space.
    obj = _gen->expand_and_allocate(size, false, false);
    if (obj == NULL) {
      // Let's see if a normal stop-world full collection will
      // free up enough space.
      SharedHeap::heap()->collect_locked(GCCause::_permanent_generation_full);
      obj = _gen->allocate(size, false);
      if (obj == NULL) {
        // The collection above may have shrunk the space, so try
        // to expand again and allocate space.
        obj = _gen->expand_and_allocate(size, false, false);
      }
      if (obj == NULL) {
        // We have not been able to allocate space despite a
        // full stop-world collection. We now make a last-ditch collection
        // attempt (in which soft refs are all aggressively freed)
        // that will try to reclaim as much space as possible.
        SharedHeap::heap()->collect_locked(GCCause::_last_ditch_collection);
        obj = _gen->allocate(size, false);
        if (obj == NULL) {
          // Expand generation in case it was shrunk following the collection.
          obj = _gen->expand_and_allocate(size, false, false);
        }
      }
    }
  }
  return obj;
}

void CMSPermGen::compute_new_size() {
  _gen->compute_new_size();
}

void CMSPermGenGen::initialize_performance_counters() {

  const char* gen_name = "perm";

  // Generation Counters - generation 2, 1 subspace
  _gen_counters = new GenerationCounters(gen_name, 2, 1, &_virtual_space);

  _gc_counters = NULL;

  _space_counters = new GSpaceCounters(gen_name, 0,
                                       _virtual_space.reserved_size(),
                                       this, _gen_counters);
}
