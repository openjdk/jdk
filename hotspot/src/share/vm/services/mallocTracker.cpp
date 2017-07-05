/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/atomic.hpp"
#include "runtime/atomic.inline.hpp"
#include "services/mallocSiteTable.hpp"
#include "services/mallocTracker.hpp"
#include "services/mallocTracker.inline.hpp"
#include "services/memTracker.hpp"

size_t MallocMemorySummary::_snapshot[CALC_OBJ_SIZE_IN_TYPE(MallocMemorySnapshot, size_t)];

// Total malloc'd memory amount
size_t MallocMemorySnapshot::total() const {
  size_t amount = 0;
  for (int index = 0; index < mt_number_of_types; index ++) {
    amount += _malloc[index].malloc_size();
  }
  amount += _tracking_header.size() + total_arena();
  return amount;
}

// Total malloc'd memory used by arenas
size_t MallocMemorySnapshot::total_arena() const {
  size_t amount = 0;
  for (int index = 0; index < mt_number_of_types; index ++) {
    amount += _malloc[index].arena_size();
  }
  return amount;
}


void MallocMemorySnapshot::reset() {
  _tracking_header.reset();
  for (int index = 0; index < mt_number_of_types; index ++) {
    _malloc[index].reset();
  }
}

// Make adjustment by subtracting chunks used by arenas
// from total chunks to get total free chunck size
void MallocMemorySnapshot::make_adjustment() {
  size_t arena_size = total_arena();
  int chunk_idx = NMTUtil::flag_to_index(mtChunk);
  _malloc[chunk_idx].record_free(arena_size);
}


void MallocMemorySummary::initialize() {
  assert(sizeof(_snapshot) >= sizeof(MallocMemorySnapshot), "Sanity Check");
  // Uses placement new operator to initialize static area.
  ::new ((void*)_snapshot)MallocMemorySnapshot();
}

void MallocHeader::release() const {
  // Tracking already shutdown, no housekeeping is needed anymore
  if (MemTracker::tracking_level() <= NMT_minimal) return;

  MallocMemorySummary::record_free(size(), flags());
  MallocMemorySummary::record_free_malloc_header(sizeof(MallocHeader));
  if (tracking_level() == NMT_detail) {
    MallocSiteTable::deallocation_at(size(), _bucket_idx, _pos_idx);
  }
}

bool MallocHeader::record_malloc_site(const NativeCallStack& stack, size_t size,
  size_t* bucket_idx, size_t* pos_idx) const {
  bool ret =  MallocSiteTable::allocation_at(stack, size, bucket_idx, pos_idx);

  // Something went wrong, could be OOM or overflow malloc site table.
  // We want to keep tracking data under OOM circumstance, so transition to
  // summary tracking.
  if (!ret) {
    MemTracker::transition_to(NMT_summary);
  }
  return ret;
}

bool MallocHeader::get_stack(NativeCallStack& stack) const {
  return MallocSiteTable::access_stack(stack, _bucket_idx, _pos_idx);
}

bool MallocTracker::initialize(NMT_TrackingLevel level) {
  if (level >= NMT_summary) {
    MallocMemorySummary::initialize();
  }

  if (level == NMT_detail) {
    return MallocSiteTable::initialize();
  }
  return true;
}

bool MallocTracker::transition(NMT_TrackingLevel from, NMT_TrackingLevel to) {
  assert(from != NMT_off, "Can not transition from off state");
  assert(to != NMT_off, "Can not transition to off state");
  if (from == NMT_minimal) {
    MallocMemorySummary::reset();
  }

  if (to == NMT_detail) {
    assert(from == NMT_minimal || from == NMT_summary, "Just check");
    return MallocSiteTable::initialize();
  } else if (from == NMT_detail) {
    assert(to == NMT_minimal || to == NMT_summary, "Just check");
    MallocSiteTable::shutdown();
  }
  return true;
}

// Record a malloc memory allocation
void* MallocTracker::record_malloc(void* malloc_base, size_t size, MEMFLAGS flags,
  const NativeCallStack& stack, NMT_TrackingLevel level) {
  void*         memblock;      // the address for user data
  MallocHeader* header = NULL;

  if (malloc_base == NULL) {
    return NULL;
  }

  // Check malloc size, size has to <= MAX_MALLOC_SIZE. This is only possible on 32-bit
  // systems, when malloc size >= 1GB, but is is safe to assume it won't happen.
  if (size > MAX_MALLOC_SIZE) {
    fatal("Should not use malloc for big memory block, use virtual memory instead");
  }
  // Uses placement global new operator to initialize malloc header
  switch(level) {
    case NMT_off:
      return malloc_base;
    case NMT_minimal: {
      MallocHeader* hdr = ::new (malloc_base) MallocHeader();
      break;
    }
    case NMT_summary: {
      header = ::new (malloc_base) MallocHeader(size, flags);
      break;
    }
    case NMT_detail: {
      header = ::new (malloc_base) MallocHeader(size, flags, stack);
      break;
    }
    default:
      ShouldNotReachHere();
  }
  memblock = (void*)((char*)malloc_base + sizeof(MallocHeader));

  // The alignment check: 8 bytes alignment for 32 bit systems.
  //                      16 bytes alignment for 64-bit systems.
  assert(((size_t)memblock & (sizeof(size_t) * 2 - 1)) == 0, "Alignment check");

  // Sanity check
  assert(get_memory_tracking_level(memblock) == level,
    "Wrong tracking level");

#ifdef ASSERT
  if (level > NMT_minimal) {
    // Read back
    assert(get_size(memblock) == size,   "Wrong size");
    assert(get_flags(memblock) == flags, "Wrong flags");
  }
#endif

  return memblock;
}

void* MallocTracker::record_free(void* memblock) {
  // Never turned on
  if (MemTracker::tracking_level() == NMT_off ||
      memblock == NULL) {
    return memblock;
  }
  MallocHeader* header = malloc_header(memblock);
  header->release();

  return (void*)header;
}


