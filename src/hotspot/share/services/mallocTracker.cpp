/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/os.hpp"
#include "runtime/safefetch.inline.hpp"
#include "services/mallocSiteTable.hpp"
#include "services/mallocTracker.hpp"
#include "services/mallocTracker.inline.hpp"
#include "services/memTracker.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"

#include "jvm_io.h"

size_t MallocMemorySummary::_snapshot[CALC_OBJ_SIZE_IN_TYPE(MallocMemorySnapshot, size_t)];

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

// Total malloc invocation count
size_t MallocMemorySnapshot::total_count() const {
  size_t amount = 0;
  for (int index = 0; index < mt_number_of_types; index ++) {
    amount += _malloc[index].malloc_count();
  }
  return amount;
}

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

// Make adjustment by subtracting chunks used by arenas
// from total chunks to get total free chunk size
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

void MallocHeader::mark_block_as_dead() {
  _canary = _header_canary_dead_mark;
  NOT_LP64(_alt_canary = _header_alt_canary_dead_mark);
  set_footer(_footer_canary_dead_mark);
}

void MallocHeader::release() {
  assert(MemTracker::enabled(), "Sanity");

  assert_block_integrity();

  MallocMemorySummary::record_free(size(), flags());
  MallocMemorySummary::record_free_malloc_header(sizeof(MallocHeader));
  if (MemTracker::tracking_level() == NMT_detail) {
    MallocSiteTable::deallocation_at(size(), _bucket_idx, _pos_idx);
  }

  mark_block_as_dead();
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
void MallocHeader::assert_block_integrity() const {
  char msg[256];
  address corruption = NULL;
  if (!check_block_integrity(msg, sizeof(msg), &corruption)) {
    if (corruption != NULL) {
      print_block_on_error(tty, (address)this);
    }
    fatal("NMT corruption: Block at " PTR_FORMAT ": %s", p2i(this), msg);
  }
}

bool MallocHeader::check_block_integrity(char* msg, size_t msglen, address* p_corruption) const {
  // Note: if you modify the error messages here, make sure you
  // adapt the associated gtests too.

  // Weed out obviously wrong block addresses of NULL or very low
  // values. Note that we should not call this for ::free(NULL),
  // which should be handled by os::free() above us.
  if (((size_t)p2i(this)) < K) {
    jio_snprintf(msg, msglen, "invalid block address");
    return false;
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
    *p_corruption = (address)this;
    jio_snprintf(msg, msglen, "block address is unaligned");
    return false;
  }

  // Check header canary
  if (_canary != _header_canary_life_mark) {
    *p_corruption = (address)this;
    jio_snprintf(msg, msglen, "header canary broken");
    return false;
  }

#ifndef _LP64
  // On 32-bit we have a second canary, check that one too.
  if (_alt_canary != _header_alt_canary_life_mark) {
    *p_corruption = (address)this;
    jio_snprintf(msg, msglen, "header canary broken");
    return false;
  }
#endif

  // Does block size seems reasonable?
  if (_size >= max_reasonable_malloc_size) {
    *p_corruption = (address)this;
    jio_snprintf(msg, msglen, "header looks invalid (weirdly large block size)");
    return false;
  }

  // Check footer canary
  if (get_footer() != _footer_canary_life_mark) {
    *p_corruption = footer_address();
    jio_snprintf(msg, msglen, "footer canary broken at " PTR_FORMAT " (buffer overflow?)",
                p2i(footer_address()));
    return false;
  }
  return true;
}

bool MallocHeader::record_malloc_site(const NativeCallStack& stack, size_t size,
  size_t* bucket_idx, size_t* pos_idx, MEMFLAGS flags) const {
  return MallocSiteTable::allocation_at(stack, size, bucket_idx, pos_idx, flags);
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

// Record a malloc memory allocation
void* MallocTracker::record_malloc(void* malloc_base, size_t size, MEMFLAGS flags,
  const NativeCallStack& stack, NMT_TrackingLevel level) {
  assert(level != NMT_off, "precondition");
  void*         memblock;      // the address for user data
  MallocHeader* header = NULL;

  if (malloc_base == NULL) {
    return NULL;
  }

  // Uses placement global new operator to initialize malloc header

  header = ::new (malloc_base)MallocHeader(size, flags, stack, level);
  memblock = (void*)((char*)malloc_base + sizeof(MallocHeader));

  // The alignment check: 8 bytes alignment for 32 bit systems.
  //                      16 bytes alignment for 64-bit systems.
  assert(((size_t)memblock & (sizeof(size_t) * 2 - 1)) == 0, "Alignment check");

#ifdef ASSERT
  if (level > NMT_off) {
    // Read back
    assert(get_size(memblock) == size,   "Wrong size");
    assert(get_flags(memblock) == flags, "Wrong flags");
  }
#endif

  return memblock;
}

void* MallocTracker::record_free(void* memblock) {
  assert(MemTracker::tracking_level() != NMT_off && memblock != NULL, "precondition");
  MallocHeader* header = malloc_header(memblock);
  header->release();
  return (void*)header;
}

// Given a pointer, if it seems to point to the start of a valid malloced block,
// print the block. Note that since there is very low risk of memory looking
// accidentally like a valid malloc block header (canaries and all) this is not
// totally failproof. Only use this during debugging or when you can afford
// signals popping up, e.g. when writing an hs_err file.
bool MallocTracker::print_pointer_information(const void* p, outputStream* st) {
  assert(MemTracker::enabled(), "NMT must be enabled");
  if (CanUseSafeFetch32() && os::is_readable_pointer(p)) {
    const NMT_TrackingLevel tracking_level = MemTracker::tracking_level();
    const MallocHeader* mhdr = (const MallocHeader*)MallocTracker::get_base(const_cast<void*>(p), tracking_level);
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
