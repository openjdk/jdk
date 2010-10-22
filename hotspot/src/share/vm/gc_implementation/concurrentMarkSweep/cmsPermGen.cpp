/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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
    return mem_allocate_in_gen(size, _gen);
  } else {
    return mem_allocate_in_gen(size, _gen);
  }
}

HeapWord* CMSPermGen::request_expand_and_allocate(Generation* gen,
                                                  size_t size,
                                                  GCCause::Cause prev_cause /* ignored */) {
  HeapWord* obj = gen->expand_and_allocate(size, false);
  if (gen->capacity() >= _capacity_expansion_limit) {
    set_capacity_expansion_limit(gen->capacity() + MaxPermHeapExpansion);
    assert(((ConcurrentMarkSweepGeneration*)gen)->should_concurrent_collect(),
           "Should kick off a collection if one not in progress");
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
