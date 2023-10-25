/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023, Red Hat, Inc. and/or its affiliates.
 *
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
#include "jvm_io.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "runtime/safefetch.hpp"
#include "services/mallocHeader.inline.hpp"
#include "services/mallocLimit.hpp"
#include "services/mallocSiteTable.hpp"
#include "services/mallocTracker.hpp"
#include "services/memTracker.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"
#include "utilities/vmError.hpp"

size_t MallocMemorySummary::_snapshot[CALC_OBJ_SIZE_IN_TYPE(MallocMemorySnapshot, size_t)];

void MemoryCounter::update_peak(size_t size, size_t cnt) {
  size_t peak_sz = peak_size();
  while (peak_sz < size) {
    size_t old_sz = Atomic::cmpxchg(&_peak_size, peak_sz, size, memory_order_relaxed);
    if (old_sz == peak_sz) {
      // I won
      _peak_count = cnt;
      break;
    } else {
      peak_sz = old_sz;
    }
  }
}

// Total malloc'd memory used by arenas
size_t MallocMemorySnapshot::total_arena() const {
  size_t amount = 0;
  for (int index = 0; index < mt_number_of_types; index ++) {
    amount += _malloc[index].arena_size();
  }
  return amount;
}

// Make adjustment by subtracting chunks used by arenas
// from total chunks to get total free chunk size
void MallocMemorySnapshot::make_adjustment() {
  size_t arena_size = total_arena();
  int chunk_idx = NMTUtil::flag_to_index(mtChunk);
  _malloc[chunk_idx].record_free(arena_size);
  _all_mallocs.deallocate(arena_size);
}

void MallocMemorySummary::initialize() {
  assert(sizeof(_snapshot) >= sizeof(MallocMemorySnapshot), "Sanity Check");
  // Uses placement new operator to initialize static area.
  ::new ((void*)_snapshot)MallocMemorySnapshot();
  MallocLimitHandler::initialize(MallocLimit);
}

bool MallocMemorySummary::total_limit_reached(size_t s, size_t so_far, const malloclimit* limit) {

  // Ignore the limit break during error reporting to prevent secondary errors.
  if (VMError::is_error_reported()) {
    return false;
  }

#define FORMATTED \
  "MallocLimit: reached global limit (triggering allocation size: " PROPERFMT ", allocated so far: " PROPERFMT ", limit: " PROPERFMT ") ", \
  PROPERFMTARGS(s), PROPERFMTARGS(so_far), PROPERFMTARGS(limit->sz)

  if (limit->mode == MallocLimitMode::trigger_fatal) {
    fatal(FORMATTED);
  } else {
    log_warning(nmt)(FORMATTED);
  }
#undef FORMATTED

  return true;
}

bool MallocMemorySummary::category_limit_reached(MEMFLAGS f, size_t s, size_t so_far, const malloclimit* limit) {

  // Ignore the limit break during error reporting to prevent secondary errors.
  if (VMError::is_error_reported()) {
    return false;
  }

#define FORMATTED \
  "MallocLimit: reached category \"%s\" limit (triggering allocation size: " PROPERFMT ", allocated so far: " PROPERFMT ", limit: " PROPERFMT ") ", \
  NMTUtil::flag_to_enum_name(f), PROPERFMTARGS(s), PROPERFMTARGS(so_far), PROPERFMTARGS(limit->sz)

  if (limit->mode == MallocLimitMode::trigger_fatal) {
    fatal(FORMATTED);
  } else {
    log_warning(nmt)(FORMATTED);
  }
#undef FORMATTED

  return true;
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

// Record a malloc memory allocation
void* MallocTracker::record_malloc(void* malloc_base, size_t size, MEMFLAGS flags,
  const NativeCallStack& stack)
{
  assert(MemTracker::enabled(), "precondition");
  assert(malloc_base != nullptr, "precondition");

  MallocMemorySummary::record_malloc(size, flags);
  uint32_t mst_marker = 0;
  if (MemTracker::tracking_level() == NMT_detail) {
    MallocSiteTable::allocation_at(stack, size, &mst_marker, flags);
  }

  // Uses placement global new operator to initialize malloc header
  MallocHeader* const header = ::new (malloc_base)MallocHeader(size, flags, mst_marker);
  void* const memblock = (void*)((char*)malloc_base + sizeof(MallocHeader));

  // The alignment check: 8 bytes alignment for 32 bit systems.
  //                      16 bytes alignment for 64-bit systems.
  assert(((size_t)memblock & (sizeof(size_t) * 2 - 1)) == 0, "Alignment check");

#ifdef ASSERT
  // Read back
  {
    const MallocHeader* header2 = MallocHeader::resolve_checked(memblock);
    assert(header2->size() == size, "Wrong size");
    assert(header2->flags() == flags, "Wrong flags");
  }
#endif

  return memblock;
}

void* MallocTracker::record_free_block(void* memblock) {
  assert(MemTracker::enabled(), "Sanity");
  assert(memblock != nullptr, "precondition");

  MallocHeader* header = MallocHeader::resolve_checked(memblock);

  deaccount(header->free_info());

  header->mark_block_as_dead();

  return (void*)header;
}

void MallocTracker::deaccount(MallocHeader::FreeInfo free_info) {
  MallocMemorySummary::record_free(free_info.size, free_info.flags);
  if (MemTracker::tracking_level() == NMT_detail) {
    MallocSiteTable::deallocation_at(free_info.size, free_info.mst_marker);
  }
}

// Given a pointer, look for the containing malloc block.
// Print the block. Note that since there is very low risk of memory looking
// accidentally like a valid malloc block header (canaries and all) so this is not
// totally failproof and may give a wrong answer. It is safe in that it will never
// crash, even when encountering unmapped memory.
bool MallocTracker::print_pointer_information(const void* p, outputStream* st) {
  assert(MemTracker::enabled(), "NMT not enabled");

  address addr = (address)p;

  // Carefully feel your way upwards and try to find a malloc header. Then check if
  // we are within the block.
  // We give preference to found live blocks; but if no live block had been found,
  // but the pointer points into remnants of a dead block, print that instead.
  const MallocHeader* likely_dead_block = nullptr;
  const MallocHeader* likely_live_block = nullptr;
  {
    const size_t smallest_possible_alignment = sizeof(void*);
    const uint8_t* here = align_down(addr, smallest_possible_alignment);
    const uint8_t* const end = here - (0x1000 + sizeof(MallocHeader)); // stop searching after 4k
    for (; here >= end; here -= smallest_possible_alignment) {
      if (!os::is_readable_pointer(here)) {
        // Probably OOB, give up
        break;
      }
      const MallocHeader* const candidate = (const MallocHeader*)here;
      if (!candidate->looks_valid()) {
        // This is definitely not a header, go on to the next candidate.
        continue;
      }

      // fudge factor:
      // We don't report blocks for which p is clearly outside of. That would cause us to return true and possibly prevent
      // subsequent tests of p, see os::print_location(). But if p is just outside of the found block, this may be a
      // narrow oob error and we'd like to know that.
      const int fudge = 8;
      const address start_block = (address)candidate;
      const address start_payload = (address)(candidate + 1);
      const address end_payload = start_payload + candidate->size();
      const address end_payload_plus_fudge = end_payload + fudge;
      if (addr >= start_block && addr < end_payload_plus_fudge) {
        // We found a block the pointer is pointing into, or almost into.
        // If its a live block, we have our info. If its a dead block, we still
        // may be within the borders of a larger live block we have not found yet -
        // continue search.
        if (candidate->is_live()) {
          likely_live_block = candidate;
          break;
        } else {
          likely_dead_block = candidate;
          continue;
        }
      }
    }
  }

  // If we've found a reasonable candidate. Print the info.
  const MallocHeader* block = likely_live_block != nullptr ? likely_live_block : likely_dead_block;
  if (block != nullptr) {
    const char* where = nullptr;
    const address start_block = (address)block;
    const address start_payload = (address)(block + 1);
    const address end_payload = start_payload + block->size();
    if (addr < start_payload) {
      where = "into header of";
    } else if (addr < end_payload) {
      where = "into";
    } else {
      where = "just outside of";
    }
    st->print_cr(PTR_FORMAT " %s %s malloced block starting at " PTR_FORMAT ", size " SIZE_FORMAT ", tag %s",
                 p2i(p), where,
                 (block->is_dead() ? "dead" : "live"),
                 p2i(block + 1), // lets print the payload start, not the header
                 block->size(), NMTUtil::flag_to_enum_name(block->flags()));
    if (MemTracker::tracking_level() == NMT_detail) {
      NativeCallStack ncs;
      if (block->get_stack(ncs)) {
        ncs.print_on(st);
        st->cr();
      }
    }
    return true;
  }
  return false;
}
