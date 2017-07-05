/*
 * Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PARALLELSCAVENGEHEAP_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PARALLELSCAVENGEHEAP_INLINE_HPP

#include "gc_implementation/parallelScavenge/parallelScavengeHeap.hpp"
#include "gc_implementation/parallelScavenge/psMarkSweep.hpp"
#include "gc_implementation/parallelScavenge/psParallelCompact.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.hpp"

inline size_t ParallelScavengeHeap::total_invocations()
{
  return UseParallelOldGC ? PSParallelCompact::total_invocations() :
    PSMarkSweep::total_invocations();
}

inline bool ParallelScavengeHeap::should_alloc_in_eden(const size_t size) const
{
  const size_t eden_size = young_gen()->eden_space()->capacity_in_words();
  return size < eden_size / 2;
}

inline void ParallelScavengeHeap::invoke_scavenge()
{
  PSScavenge::invoke();
}

inline bool ParallelScavengeHeap::is_in_young(oop p) {
  // Assumes the the old gen address range is lower than that of the young gen.
  const void* loc = (void*) p;
  bool result = ((HeapWord*)p) >= young_gen()->reserved().start();
  assert(result == young_gen()->is_in_reserved(p),
        err_msg("incorrect test - result=%d, p=" PTR_FORMAT, result, (void*)p));
  return result;
}
#endif // SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PARALLELSCAVENGEHEAP_INLINE_HPP
