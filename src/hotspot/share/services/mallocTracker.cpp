/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 SAP SE. All rights reserved.
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
#include "runtime/arguments.hpp"
#include "runtime/os.hpp"
#include "runtime/safefetch.hpp"
#include "services/mallocHeader.inline.hpp"
#include "services/mallocSiteTable.hpp"
#include "services/mallocTracker.hpp"
#include "services/memTracker.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"
#include "utilities/vmError.hpp"

size_t MallocMemorySummary::_snapshot[CALC_OBJ_SIZE_IN_TYPE(MallocMemorySnapshot, size_t)];
size_t MallocMemorySummary::_limits_per_category[mt_number_of_types] = { 0 };
size_t MallocMemorySummary::_total_limit = 0;

#ifdef ASSERT
void MemoryCounter::update_peak_count(size_t count) {
  size_t peak_cnt = peak_count();
  while (peak_cnt < count) {
    size_t old_cnt = Atomic::cmpxchg(&_peak_count, peak_cnt, count, memory_order_relaxed);
    if (old_cnt != peak_cnt) {
      peak_cnt = old_cnt;
    }
  }
}

void MemoryCounter::update_peak_size(size_t sz) {
  size_t peak_sz = peak_size();
  while (peak_sz < sz) {
    size_t old_sz = Atomic::cmpxchg(&_peak_size, peak_sz, sz, memory_order_relaxed);
    if (old_sz != peak_sz) {
      peak_sz = old_sz;
    }
  }
}

size_t MemoryCounter::peak_count() const {
  return Atomic::load(&_peak_count);
}

size_t MemoryCounter::peak_size() const {
  return Atomic::load(&_peak_size);
}
#endif

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
  initialize_limit_handling();
}

void MallocMemorySummary::initialize_limit_handling() {
  // Initialize limit handling.
  Arguments::parse_malloc_limits(&_total_limit, _limits_per_category);

  if (_total_limit > 0) {
    log_info(nmt)("MallocLimit: total limit: " SIZE_FORMAT "%s",
                  byte_size_in_proper_unit(_total_limit),
                  proper_unit_for_byte_size(_total_limit));
  } else {
    for (int i = 0; i < mt_number_of_types; i ++) {
      size_t catlim = _limits_per_category[i];
      if (catlim > 0) {
        log_info(nmt)("MallocLimit: category \"%s\" limit: " SIZE_FORMAT "%s",
                      NMTUtil::flag_to_name((MEMFLAGS)i),
                      byte_size_in_proper_unit(catlim),
                      proper_unit_for_byte_size(catlim));
      }
    }
  }
}

void MallocMemorySummary::total_limit_reached(size_t size, size_t limit) {
  // Assert in both debug and release, but allow error reporting to malloc beyond limits.
  if (!VMError::is_error_reported()) {
    fatal("MallocLimit: reached limit (size: " SIZE_FORMAT ", limit: " SIZE_FORMAT ") ",
          size, limit);
  }
}

void MallocMemorySummary::category_limit_reached(size_t size, size_t limit, MEMFLAGS flag) {
  // Assert in both debug and release, but allow error reporting to malloc beyond limits.
  if (!VMError::is_error_reported()) {
    fatal("MallocLimit: category \"%s\" reached limit (size: " SIZE_FORMAT ", limit: " SIZE_FORMAT ") ",
          NMTUtil::flag_to_name(flag), size, limit);
  }
}

void MallocMemorySummary::print_limits(outputStream* st) {
  if (_total_limit != 0) {
    st->print("MallocLimit: " SIZE_FORMAT, _total_limit);
  } else {
    bool first = true;
    for (int i = 0; i < mt_number_of_types; i ++) {
      if (_limits_per_category[i] > 0) {
        st->print("%s%s:" SIZE_FORMAT, (first ? "MallocLimit: " : ", "),
                  NMTUtil::flag_to_name((MEMFLAGS)i), _limits_per_category[i]);
        first = false;
      }
    }
  }
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
  assert(malloc_base != NULL, "precondition");

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

void* MallocTracker::record_free(void* memblock) {
  assert(MemTracker::enabled(), "Sanity");
  assert(memblock != NULL, "precondition");

  MallocHeader* const header = malloc_header(memblock);
  header->assert_block_integrity();

  MallocMemorySummary::record_free(header->size(), header->flags());
  if (MemTracker::tracking_level() == NMT_detail) {
    MallocSiteTable::deallocation_at(header->size(), header->mst_marker());
  }

  header->mark_block_as_dead();

  return (void*)header;
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
