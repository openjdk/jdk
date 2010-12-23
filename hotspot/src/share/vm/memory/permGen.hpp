/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_PERMGEN_HPP
#define SHARE_VM_MEMORY_PERMGEN_HPP

#include "gc_interface/gcCause.hpp"
#include "memory/generation.hpp"
#include "memory/iterator.hpp"
#include "runtime/handles.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/virtualspace.hpp"

// All heaps contains a "permanent generation," containing permanent
// (reflective) objects.  This is like a regular generation in some ways,
// but unlike one in others, and so is split apart.

class Generation;
class GenRemSet;
class CSpaceCounters;

// PermGen models the part of the heap used to allocate class meta-data.

class PermGen : public CHeapObj {
  friend class VMStructs;
 protected:
  size_t _capacity_expansion_limit;  // maximum expansion allowed without a
                                     // full gc occurring
  void set_capacity_expansion_limit(size_t limit) {
    assert_locked_or_safepoint(Heap_lock);
    _capacity_expansion_limit = limit;
  }

  HeapWord* mem_allocate_in_gen(size_t size, Generation* gen);
  // Along with mem_allocate_in_gen() above, implements policy for
  // "scheduling" allocation/expansion/collection of the perm gen.
  // The virtual method request_...() below can be overridden by
  // subtypes that want to implement a different expansion/collection
  // policy from the default provided.
  virtual HeapWord* request_expand_and_allocate(Generation* gen, size_t size,
                                                GCCause::Cause prev_cause);

 public:
  enum Name {
    MarkSweepCompact, MarkSweep, ConcurrentMarkSweep
  };

  // Permanent allocation (initialized)
  virtual HeapWord* mem_allocate(size_t size) = 0;

  // Mark sweep support
  virtual void compute_new_size() = 0;

  // Ideally, we would use MI (IMHO) but we'll do delegation instead.
  virtual Generation* as_gen() const = 0;

  virtual void oop_iterate(OopClosure* cl) {
    Generation* g = as_gen();
    assert(g != NULL, "as_gen() NULL");
    g->oop_iterate(cl);
  }

  virtual void object_iterate(ObjectClosure* cl) {
    Generation* g = as_gen();
    assert(g != NULL, "as_gen() NULL");
    g->object_iterate(cl);
  }

  // Performance Counter support
  virtual void update_counters() {
    Generation* g = as_gen();
    assert(g != NULL, "as_gen() NULL");
    g->update_counters();
  }
};

#endif // SHARE_VM_MEMORY_PERMGEN_HPP
