/*
 * Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/heapRegion.hpp"
#include "services/g1MemoryPool.hpp"

G1MemoryPoolSuper::G1MemoryPoolSuper(G1CollectedHeap* g1h,
                                     const char* name,
                                     size_t init_size,
                                     size_t max_size,
                                     bool support_usage_threshold) :
  _g1mm(g1h->g1mm()), CollectedMemoryPool(name,
                                          MemoryPool::Heap,
                                          init_size,
                                          max_size,
                                          support_usage_threshold) {
  assert(UseG1GC, "sanity");
}

G1EdenPool::G1EdenPool(G1CollectedHeap* g1h) :
  G1MemoryPoolSuper(g1h,
                    "G1 Eden Space",
                    g1h->g1mm()->eden_space_committed(), /* init_size */
                    _undefined_max,
                    false /* support_usage_threshold */) { }

MemoryUsage G1EdenPool::get_memory_usage() {
  size_t initial_sz = initial_size();
  size_t max_sz     = max_size();
  size_t used       = used_in_bytes();
  size_t committed  = _g1mm->eden_space_committed();

  return MemoryUsage(initial_sz, used, committed, max_sz);
}

G1SurvivorPool::G1SurvivorPool(G1CollectedHeap* g1h) :
  G1MemoryPoolSuper(g1h,
                    "G1 Survivor Space",
                    g1h->g1mm()->survivor_space_committed(), /* init_size */
                    _undefined_max,
                    false /* support_usage_threshold */) { }

MemoryUsage G1SurvivorPool::get_memory_usage() {
  size_t initial_sz = initial_size();
  size_t max_sz     = max_size();
  size_t used       = used_in_bytes();
  size_t committed  = _g1mm->survivor_space_committed();

  return MemoryUsage(initial_sz, used, committed, max_sz);
}

G1OldGenPool::G1OldGenPool(G1CollectedHeap* g1h) :
  G1MemoryPoolSuper(g1h,
                    "G1 Old Gen",
                    g1h->g1mm()->old_space_committed(), /* init_size */
                    g1h->g1mm()->old_gen_max(),
                    true /* support_usage_threshold */) { }

MemoryUsage G1OldGenPool::get_memory_usage() {
  size_t initial_sz = initial_size();
  size_t max_sz     = max_size();
  size_t used       = used_in_bytes();
  size_t committed  = _g1mm->old_space_committed();

  return MemoryUsage(initial_sz, used, committed, max_sz);
}
