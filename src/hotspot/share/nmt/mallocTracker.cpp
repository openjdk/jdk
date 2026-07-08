/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023, 2024, Red Hat, Inc. and/or its affiliates.
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

#include "jvm_io.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/arena.hpp"
#include "nmt/mallocHeader.inline.hpp"
#include "nmt/mallocLimit.hpp"
#include "nmt/mallocSiteTable.hpp"
#include "nmt/mallocTracker.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "runtime/safefetch.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "utilities/vmError.hpp"

DeferredStatic<MallocMemorySnapshot> MallocMemorySummary::_snapshot;

void MemoryCounter::update_peak(size_t size, size_t cnt) {
  size_t peak_sz = peak_size();
  while (peak_sz < size) {
    size_t old_sz = AtomicAccess::cmpxchg(&_peak_size, peak_sz, size, memory_order_relaxed);
    if (old_sz == peak_sz) {
      // I won
      _peak_count = cnt;
      break;
    } else {
      peak_sz = old_sz;
    }
  }
}

void MallocMemorySnapshot::copy_to(MallocMemorySnapshot* s) {
  // Use lock to make sure that mtChunks don't get deallocated while the
  // copy is going on, because their size is adjusted using this
  // buffer in make_adjustment().
  ChunkPoolLocker::LockStrategy ls = ChunkPoolLocker::LockStrategy::Lock;
  if (VMError::is_error_reported() && VMError::is_error_reported_in_current_thread()) {
    ls = ChunkPoolLocker::LockStrategy::Try;
  }
  ChunkPoolLocker cpl(ls);
  s->_all_mallocs = _all_mallocs;
  size_t total_size = 0;
  size_t total_count = 0;
  for (int index = 0; index < mt_number_of_tags; index ++) {
    s->_malloc[index] = _malloc[index];
    total_size += s->_malloc[index].malloc_size();
    total_count += s->_malloc[index].malloc_count();
  }
  // malloc counters may be updated concurrently
  s->_all_mallocs.set_size_and_count(total_size, total_count);
}

// Total malloc'd memory used by arenas
size_t MallocMemorySnapshot::total_arena() const {
  size_t amount = 0;
  for (int index = 0; index < mt_number_of_tags; index ++) {
    amount += _malloc[index].arena_size();
  }
  return amount;
}

// Make adjustment by subtracting chunks used by arenas
// from total chunks to get total free chunk size
void MallocMemorySnapshot::make_adjustment() {
  size_t arena_size = total_arena();
  int chunk_idx = NMTUtil::tag_to_index(mtChunk);
  _malloc[chunk_idx].record_free(arena_size);
  _all_mallocs.deallocate(arena_size);
}

void MallocMemorySummary::initialize() {
  _snapshot.initialize();
  MallocLimitHandler::initialize(MallocLimit);
}

bool MallocMemorySummary::total_limit_reached(size_t s, size_t so_far, const malloclimit* limit) {

#define FORMATTED \
  "MallocLimit: reached global limit (triggering allocation size: " PROPERFMT ", allocated so far: " PROPERFMT ", limit: " PROPERFMT ") ", \
  PROPERFMTARGS(s), PROPERFMTARGS(so_far), PROPERFMTARGS(limit->sz)

  // If we hit the limit during error reporting, we print a short warning but otherwise ignore it.
  // We don't want to risk recursive assertion or torn hs-err logs.
  if (VMError::is_error_reported()) {
    // Print warning, but only the first n times to avoid flooding output.
    static int stopafter = 10;
    if (stopafter-- > 0) {
      log_warning(nmt)(FORMATTED);
    }
    return false;
  }

  if (limit->mode == MallocLimitMode::trigger_fatal) {
    fatal(FORMATTED);
  } else {
    log_warning(nmt)(FORMATTED);
  }
#undef FORMATTED

  return true;
}

bool MallocMemorySummary::category_limit_reached(MemTag mem_tag, size_t s, size_t so_far, const malloclimit* limit) {

#define FORMATTED \
  "MallocLimit: reached category \"%s\" limit (triggering allocation size: " PROPERFMT ", allocated so far: " PROPERFMT ", limit: " PROPERFMT ") ", \
  NMTUtil::tag_to_enum_name(mem_tag), PROPERFMTARGS(s), PROPERFMTARGS(so_far), PROPERFMTARGS(limit->sz)

  // If we hit the limit during error reporting, we print a short warning but otherwise ignore it.
  // We don't want to risk recursive assertion or torn hs-err logs.
  if (VMError::is_error_reported()) {
    // Print warning, but only the first n times to avoid flooding output.
    static int stopafter = 10;
    if (stopafter-- > 0) {
      log_warning(nmt)(FORMATTED);
    }
    return false;
  }

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
void* MallocTracker::record_malloc(void* malloc_base, size_t size, MemTag mem_tag,
  const NativeCallStack& stack)
{
  assert(MemTracker::enabled(), "precondition");
  assert(malloc_base != nullptr, "precondition");

  MallocMemorySummary::record_malloc(size, mem_tag);
  uint32_t mst_marker = 0;
  if (MemTracker::tracking_level() == NMT_detail) {
    MallocSiteTable::allocation_at(stack, size, &mst_marker, mem_tag);
  }

  // Uses placement global new operator to initialize malloc header
  MallocHeader* const header = ::new (malloc_base)MallocHeader(size, mem_tag, mst_marker);
  void* const memblock = (void*)((char*)malloc_base + sizeof(MallocHeader));

  // The alignment check: 8 bytes alignment for 32 bit systems.
  //                      16 bytes alignment for 64-bit systems.
  assert(((size_t)memblock & (sizeof(size_t) * 2 - 1)) == 0, "Alignment check");

#ifdef ASSERT
  // Read back
  {
    const MallocHeader* header2 = MallocHeader::resolve_checked(memblock);
    assert(header2->size() == size, "Wrong size");
    assert(header2->mem_tag() == mem_tag, "Wrong memory tag");
  }
#endif
  MallocHeader::revive_block(memblock);
  return memblock;
}

void* MallocTracker::record_free_block(void* memblock) {
  assert(MemTracker::enabled(), "Sanity");
  assert(memblock != nullptr, "precondition");

  MallocHeader* header = MallocHeader::kill_block(memblock);

  deaccount(header->free_info());

  if (ZapCHeap) {
    // To do this zapping, we need to know the block size.
    // This is why we have to do it here, and not in os::free.
    memset(memblock, freeBlockPad, header->size());
  }

  header->mark_block_as_dead();
  return (void*)header;
}

void MallocTracker::deaccount(MallocHeader::FreeInfo free_info) {
  MallocMemorySummary::record_free(free_info.size, free_info.mem_tag);
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

#if !INCLUDE_ASAN

  address addr = (address)p;

  if (p2u(addr) < MAX2(os::vm_min_address(), (size_t)16 * M)) {
    return false; // bail out
  }

  // Carefully feel your way upwards and try to find a malloc header. Then check if
  // we are within the block.
  // We give preference to found live blocks; but if no live block had been found,
  // but the pointer points into remnants of a dead block, print that instead.
  const MallocHeader* likely_dead_block = nullptr;
  const MallocHeader* likely_live_block = nullptr;
  {
    const size_t smallest_possible_alignment = sizeof(void*);
    uintptr_t here = (uintptr_t)align_down(addr, smallest_possible_alignment);
    uintptr_t end = MAX2(smallest_possible_alignment, here - (0x1000 + sizeof(MallocHeader))); // stop searching after 4k
    for (; here >= end; here -= smallest_possible_alignment) {
      // JDK-8306561: cast to a MallocHeader needs to guarantee it can reside in readable memory
      if (!os::is_readable_range((void*)here, (void*)(here + sizeof(MallocHeader)))) {
        break; // Probably OOB, give up
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
    st->print_cr(PTR_FORMAT " %s %s malloced block starting at " PTR_FORMAT ", size %zu, tag %s",
                 p2i(p), where,
                 (block->is_dead() ? "dead" : "live"),
                 p2i(block + 1), // lets print the payload start, not the header
                 block->size(), NMTUtil::tag_to_enum_name(block->mem_tag()));
    if (MemTracker::tracking_level() == NMT_detail) {
      NativeCallStack ncs;
      if (MallocSiteTable::access_stack(ncs, *block)) {
        ncs.print_on(st);
        st->cr();
      }
    }
    return true;
  }

#endif // !INCLUDE_ASAN

  return false;
}
