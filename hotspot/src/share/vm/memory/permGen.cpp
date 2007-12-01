/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_permGen.cpp.incl"

CompactingPermGen::CompactingPermGen(ReservedSpace rs,
                                     ReservedSpace shared_rs,
                                     size_t initial_byte_size,
                                     GenRemSet* remset,
                                     PermanentGenerationSpec* perm_spec)
{
  CompactingPermGenGen* g =
    new CompactingPermGenGen(rs, shared_rs, initial_byte_size, -1, remset,
                             NULL, perm_spec);
  if (g == NULL)
    vm_exit_during_initialization("Could not allocate a CompactingPermGen");
  _gen = g;

  g->initialize_performance_counters();

  _capacity_expansion_limit = g->capacity() + MaxPermHeapExpansion;
}

HeapWord* CompactingPermGen::mem_allocate(size_t size) {
  MutexLocker ml(Heap_lock);
  HeapWord* obj = _gen->allocate(size, false);
  bool tried_collection = false;
  bool tried_expansion = false;
  while (obj == NULL) {
    if (_gen->capacity() >= _capacity_expansion_limit || tried_expansion) {
      // Expansion limit reached, try collection before expanding further
      // For now we force a full collection, this could be changed
      SharedHeap::heap()->collect_locked(GCCause::_permanent_generation_full);
      obj = _gen->allocate(size, false);
      tried_collection = true;
      tried_expansion =  false;    // ... following the collection:
                                   // the collection may have shrunk the space.
    }
    if (obj == NULL && !tried_expansion) {
      obj = _gen->expand_and_allocate(size, false);
      tried_expansion = true;
    }
    if (obj == NULL && tried_collection && tried_expansion) {
      // We have not been able to allocate despite a collection and
      // an attempted space expansion. We now make a last-ditch collection
      // attempt that will try to reclaim as much space as possible (for
      // example by aggressively clearing all soft refs).
      SharedHeap::heap()->collect_locked(GCCause::_last_ditch_collection);
      obj = _gen->allocate(size, false);
      if (obj == NULL) {
        // An expansion attempt is necessary since the previous
        // collection may have shrunk the space.
        obj = _gen->expand_and_allocate(size, false);
      }
      break;
    }
  }
  return obj;
}

void CompactingPermGen::compute_new_size() {
  size_t desired_capacity = align_size_up(_gen->used(), MinPermHeapExpansion);
  if (desired_capacity < PermSize) {
    desired_capacity = PermSize;
  }
  if (_gen->capacity() > desired_capacity) {
    _gen->shrink(_gen->capacity() - desired_capacity);
  }
  _capacity_expansion_limit = _gen->capacity() + MaxPermHeapExpansion;
}
