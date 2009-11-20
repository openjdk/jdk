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

# include "incls/_precompiled.incl"
# include "incls/_g1MemoryPool.cpp.incl"

G1MemoryPoolSuper::G1MemoryPoolSuper(G1CollectedHeap* g1h,
                                     const char* name,
                                     size_t init_size,
                                     size_t max_size,
                                     bool support_usage_threshold) :
  _g1h(g1h), CollectedMemoryPool(name,
                                 MemoryPool::Heap,
                                 init_size,
                                 max_size,
                                 support_usage_threshold) {
  assert(UseG1GC, "sanity");
}

// See the comment at the top of g1MemoryPool.hpp
size_t G1MemoryPoolSuper::eden_space_committed(G1CollectedHeap* g1h) {
  return eden_space_used(g1h);
}

// See the comment at the top of g1MemoryPool.hpp
size_t G1MemoryPoolSuper::eden_space_used(G1CollectedHeap* g1h) {
  size_t young_list_length = g1h->young_list_length();
  size_t eden_used = young_list_length * HeapRegion::GrainBytes;
  size_t survivor_used = survivor_space_used(g1h);
  eden_used = subtract_up_to_zero(eden_used, survivor_used);
  return eden_used;
}

// See the comment at the top of g1MemoryPool.hpp
size_t G1MemoryPoolSuper::eden_space_max(G1CollectedHeap* g1h) {
  return eden_space_committed(g1h);
}

// See the comment at the top of g1MemoryPool.hpp
size_t G1MemoryPoolSuper::survivor_space_committed(G1CollectedHeap* g1h) {
  return survivor_space_used(g1h);
}

// See the comment at the top of g1MemoryPool.hpp
size_t G1MemoryPoolSuper::survivor_space_used(G1CollectedHeap* g1h) {
  size_t survivor_num = g1h->g1_policy()->recorded_survivor_regions();
  size_t survivor_used = survivor_num * HeapRegion::GrainBytes;
  return survivor_used;
}

// See the comment at the top of g1MemoryPool.hpp
size_t G1MemoryPoolSuper::survivor_space_max(G1CollectedHeap* g1h) {
  return survivor_space_committed(g1h);
}

// See the comment at the top of g1MemoryPool.hpp
size_t G1MemoryPoolSuper::old_space_committed(G1CollectedHeap* g1h) {
  size_t committed = overall_committed(g1h);
  size_t eden_committed = eden_space_committed(g1h);
  size_t survivor_committed = survivor_space_committed(g1h);
  committed = subtract_up_to_zero(committed, eden_committed);
  committed = subtract_up_to_zero(committed, survivor_committed);
  return committed;
}

// See the comment at the top of g1MemoryPool.hpp
size_t G1MemoryPoolSuper::old_space_used(G1CollectedHeap* g1h) {
  size_t used = overall_used(g1h);
  size_t eden_used = eden_space_used(g1h);
  size_t survivor_used = survivor_space_used(g1h);
  used = subtract_up_to_zero(used, eden_used);
  used = subtract_up_to_zero(used, survivor_used);
  return used;
}

// See the comment at the top of g1MemoryPool.hpp
size_t G1MemoryPoolSuper::old_space_max(G1CollectedHeap* g1h) {
  size_t max = g1h->g1_reserved_obj_bytes();
  size_t eden_max = eden_space_max(g1h);
  size_t survivor_max = survivor_space_max(g1h);
  max = subtract_up_to_zero(max, eden_max);
  max = subtract_up_to_zero(max, survivor_max);
  return max;
}

G1EdenPool::G1EdenPool(G1CollectedHeap* g1h) :
  G1MemoryPoolSuper(g1h,
                    "G1 Eden",
                    eden_space_committed(g1h), /* init_size */
                    eden_space_max(g1h), /* max_size */
                    false /* support_usage_threshold */) {
}

MemoryUsage G1EdenPool::get_memory_usage() {
  size_t maxSize   = max_size();
  size_t used      = used_in_bytes();
  size_t committed = eden_space_committed();

  return MemoryUsage(initial_size(), used, committed, maxSize);
}

G1SurvivorPool::G1SurvivorPool(G1CollectedHeap* g1h) :
  G1MemoryPoolSuper(g1h,
                    "G1 Survivor",
                    survivor_space_committed(g1h), /* init_size */
                    survivor_space_max(g1h), /* max_size */
                    false /* support_usage_threshold */) {
}

MemoryUsage G1SurvivorPool::get_memory_usage() {
  size_t maxSize   = max_size();
  size_t used      = used_in_bytes();
  size_t committed = survivor_space_committed();

  return MemoryUsage(initial_size(), used, committed, maxSize);
}

G1OldGenPool::G1OldGenPool(G1CollectedHeap* g1h) :
  G1MemoryPoolSuper(g1h,
                    "G1 Old Gen",
                    old_space_committed(g1h), /* init_size */
                    old_space_max(g1h), /* max_size */
                    true /* support_usage_threshold */) {
}

MemoryUsage G1OldGenPool::get_memory_usage() {
  size_t maxSize   = max_size();
  size_t used      = used_in_bytes();
  size_t committed = old_space_committed();

  return MemoryUsage(initial_size(), used, committed, maxSize);
}
