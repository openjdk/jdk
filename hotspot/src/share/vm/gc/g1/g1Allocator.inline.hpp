/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1ALLOCATOR_INLINE_HPP
#define SHARE_VM_GC_G1_G1ALLOCATOR_INLINE_HPP

#include "gc/g1/g1Allocator.hpp"
#include "gc/g1/g1AllocRegion.inline.hpp"
#include "gc/shared/plab.inline.hpp"

HeapWord* G1Allocator::attempt_allocation(size_t word_size, AllocationContext_t context) {
  return mutator_alloc_region(context)->attempt_allocation(word_size, false /* bot_updates */);
}

HeapWord* G1Allocator::attempt_allocation_locked(size_t word_size, AllocationContext_t context) {
  HeapWord* result = mutator_alloc_region(context)->attempt_allocation_locked(word_size, false /* bot_updates */);
  assert(result != NULL || mutator_alloc_region(context)->get() == NULL,
         "Must not have a mutator alloc region if there is no memory, but is " PTR_FORMAT, p2i(mutator_alloc_region(context)->get()));
  return result;
}

HeapWord* G1Allocator::attempt_allocation_force(size_t word_size, AllocationContext_t context) {
  return mutator_alloc_region(context)->attempt_allocation_force(word_size, false /* bot_updates */);
}

inline HeapWord* G1PLABAllocator::plab_allocate(InCSetState dest,
                                                size_t word_sz,
                                                AllocationContext_t context) {
  G1PLAB* buffer = alloc_buffer(dest, context);
  if (_survivor_alignment_bytes == 0 || !dest.is_young()) {
    return buffer->allocate(word_sz);
  } else {
    return buffer->allocate_aligned(word_sz, _survivor_alignment_bytes);
  }
}

// Create the _archive_region_map which is used to identify archive objects.
inline void G1ArchiveAllocator::enable_archive_object_check() {
  assert(!_archive_check_enabled, "archive range check already enabled");
  _archive_check_enabled = true;
  size_t length = Universe::heap()->max_capacity();
  _archive_region_map.initialize((HeapWord*)Universe::heap()->base(),
                                 (HeapWord*)Universe::heap()->base() + length,
                                 HeapRegion::GrainBytes);
}

// Set the regions containing the specified address range as archive/non-archive.
inline void G1ArchiveAllocator::set_range_archive(MemRegion range, bool is_archive) {
  assert(_archive_check_enabled, "archive range check not enabled");
  _archive_region_map.set_by_address(range, is_archive);
}

// Check if an object is in an archive region using the _archive_region_map.
inline bool G1ArchiveAllocator::in_archive_range(oop object) {
  // This is the out-of-line part of is_archive_object test, done separately
  // to avoid additional performance impact when the check is not enabled.
  return _archive_region_map.get_by_address((HeapWord*)object);
}

// Check if archive object checking is enabled, to avoid calling in_archive_range
// unnecessarily.
inline bool G1ArchiveAllocator::archive_check_enabled() {
  return _archive_check_enabled;
}

inline bool G1ArchiveAllocator::is_archive_object(oop object) {
  return (archive_check_enabled() && in_archive_range(object));
}

#endif // SHARE_VM_GC_G1_G1ALLOCATOR_HPP
