/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2023 SAP SE. All rights reserved.
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

#ifdef ASSERT
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
#endif // ASSERT

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
    MallocHeader* const header2 = malloc_header(memblock);
    assert(header2->size() == size, "Wrong size");
    assert(header2->flags() == flags, "Wrong flags");
    header2->assert_block_integrity();
  }
#endif

  return memblock;
}

void* MallocTracker::record_free_block(void* memblock) {
  assert(MemTracker::enabled(), "Sanity");
  assert(memblock != nullptr, "precondition");

  MallocHeader* const header = malloc_header(memblock);
  header->assert_block_integrity();

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

// Given a pointer, if it seems to point to the start of a valid malloced block,
// print the block. Note that since there is very low risk of memory looking
// accidentally like a valid malloc block header (canaries and all) this is not
// totally failproof. Only use this during debugging or when you can afford
// signals popping up, e.g. when writing an hs_err file.
bool MallocTracker::print_pointer_information(const void* p, outputStream* st) {
  assert(MemTracker::enabled(), "NMT must be enabled");
  if (os::is_readable_pointer(p)) {
    const NMT_TrackingLevel tracking_level = MemTracker::tracking_level();
    const MallocHeader* mhdr = malloc_header(p);
    char msg[256];
    address p_corrupted;
    if (os::is_readable_pointer(mhdr) &&
        mhdr->check_block_integrity(msg, sizeof(msg), &p_corrupted)) {
      st->print_cr(PTR_FORMAT " malloc'd " SIZE_FORMAT " bytes by %s",
          p2i(p), mhdr->size(), NMTUtil::flag_to_name(mhdr->flags()));
      if (tracking_level == NMT_detail) {
        NativeCallStack ncs;
        if (mhdr->get_stack(ncs)) {
          ncs.print_on(st);
          st->cr();
        }
      }
      return true;
    }
  }
  return false;
}
