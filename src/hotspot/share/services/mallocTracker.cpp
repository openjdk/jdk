/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"
#include "services/mallocSiteTable.hpp"
#include "services/mallocTracker.hpp"
#include "services/memTracker.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"

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
}

void MallocHeader::mark_block_as_dead() {
  _canary = _header_canary_dead_mark;
  NOT_LP64(_alt_canary = _header_alt_canary_dead_mark);
  set_footer(_footer_canary_dead_mark);
}

void MallocHeader::print_block_on_error(outputStream* st, address bad_address) const {
  assert(bad_address >= (address)this, "sanity");

  // This function prints block information, including hex dump, in case of a detected
  // corruption. The hex dump should show both block header and corruption site
  // (which may or may not be close together or identical). Plus some surrounding area.
  //
  // Note that we use os::print_hex_dump(), which is able to cope with unmapped
  // memory (it uses SafeFetch).

  st->print_cr("NMT Block at " PTR_FORMAT ", corruption at: " PTR_FORMAT ": ",
               p2i(this), p2i(bad_address));
  static const size_t min_dump_length = 256;
  address from1 = align_down((address)this, sizeof(void*)) - (min_dump_length / 2);
  address to1 = from1 + min_dump_length;
  address from2 = align_down(bad_address, sizeof(void*)) - (min_dump_length / 2);
  address to2 = from2 + min_dump_length;
  if (from2 > to1) {
    // Dump gets too large, split up in two sections.
    os::print_hex_dump(st, from1, to1, 1);
    st->print_cr("...");
    os::print_hex_dump(st, from2, to2, 1);
  } else {
    // print one hex dump
    os::print_hex_dump(st, from1, to2, 1);
  }
}

// Check block integrity. If block is broken, print out a report
// to tty (optionally with hex dump surrounding the broken block),
// then trigger a fatal error.
void MallocHeader::check_block_integrity() const {

#define PREFIX "NMT corruption: "
  // Note: if you modify the error messages here, make sure you
  // adapt the associated gtests too.

  // Weed out obviously wrong block addresses of NULL or very low
  // values. Note that we should not call this for ::free(NULL),
  // which should be handled by os::free() above us.
  if (((size_t)p2i(this)) < K) {
    fatal(PREFIX "Block at " PTR_FORMAT ": invalid block address", p2i(this));
  }

  // From here on we assume the block pointer to be valid. We could
  // use SafeFetch but since this is a hot path we don't. If we are
  // wrong, we will crash when accessing the canary, which hopefully
  // generates distinct crash report.

  // Weed out obviously unaligned addresses. NMT blocks, being the result of
  // malloc calls, should adhere to malloc() alignment. Malloc alignment is
  // specified by the standard by this requirement:
  // "malloc returns a pointer which is suitably aligned for any built-in type"
  // For us it means that it is *at least* 64-bit on all of our 32-bit and
  // 64-bit platforms since we have native 64-bit types. It very probably is
  // larger than that, since there exist scalar types larger than 64bit. Here,
  // we test the smallest alignment we know.
  // Should we ever start using std::max_align_t, this would be one place to
  // fix up.
  if (!is_aligned(this, sizeof(uint64_t))) {
    print_block_on_error(tty, (address)this);
    fatal(PREFIX "Block at " PTR_FORMAT ": block address is unaligned", p2i(this));
  }

  // Check header canary
  if (_canary != _header_canary_life_mark) {
    print_block_on_error(tty, (address)this);
    fatal(PREFIX "Block at " PTR_FORMAT ": header canary broken.", p2i(this));
  }

#ifndef _LP64
  // On 32-bit we have a second canary, check that one too.
  if (_alt_canary != _header_alt_canary_life_mark) {
    print_block_on_error(tty, (address)this);
    fatal(PREFIX "Block at " PTR_FORMAT ": header alternate canary broken.", p2i(this));
  }
#endif

  // Does block size seems reasonable?
  if (_size >= max_reasonable_malloc_size) {
    print_block_on_error(tty, (address)this);
    fatal(PREFIX "Block at " PTR_FORMAT ": header looks invalid (weirdly large block size)", p2i(this));
  }

  // Check footer canary
  if (get_footer() != _footer_canary_life_mark) {
    print_block_on_error(tty, footer_address());
    fatal(PREFIX "Block at " PTR_FORMAT ": footer canary broken at " PTR_FORMAT " (buffer overflow?)",
          p2i(this), p2i(footer_address()));
  }
#undef PREFIX
}

bool MallocHeader::get_stack(NativeCallStack& stack) const {
  return MallocSiteTable::access_stack(stack, _mst_marker);
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
  MallocHeader* const header = ::new (malloc_base)MallocHeader(size, flags, stack, mst_marker);
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
    header2->check_block_integrity();
  }
#endif

  return memblock;
}

void* MallocTracker::record_free(void* memblock) {
  assert(MemTracker::enabled(), "Sanity");
  assert(memblock != NULL, "precondition");

  MallocHeader* const header = malloc_header(memblock);
  header->check_block_integrity();

  MallocMemorySummary::record_free(header->size(), header->flags());
  if (MemTracker::tracking_level() == NMT_detail) {
    MallocSiteTable::deallocation_at(header->size(), header->mst_marker());
  }

  header->mark_block_as_dead();

  return (void*)header;
}
