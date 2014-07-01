/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/parallelScavenge/parMarkBitMap.hpp"
#include "gc_implementation/parallelScavenge/psParallelCompact.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/bitMap.inline.hpp"
#include "services/memTracker.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "os_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "os_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "os_windows.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_aix
# include "os_aix.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "os_bsd.inline.hpp"
#endif

bool
ParMarkBitMap::initialize(MemRegion covered_region)
{
  const idx_t bits = bits_required(covered_region);
  // The bits will be divided evenly between two bitmaps; each of them should be
  // an integral number of words.
  assert(bits % (BitsPerWord * 2) == 0, "region size unaligned");

  const size_t words = bits / BitsPerWord;
  const size_t raw_bytes = words * sizeof(idx_t);
  const size_t page_sz = os::page_size_for_region(raw_bytes, raw_bytes, 10);
  const size_t granularity = os::vm_allocation_granularity();
  _reserved_byte_size = align_size_up(raw_bytes, MAX2(page_sz, granularity));

  const size_t rs_align = page_sz == (size_t) os::vm_page_size() ? 0 :
    MAX2(page_sz, granularity);
  ReservedSpace rs(_reserved_byte_size, rs_align, rs_align > 0);
  os::trace_page_sizes("par bitmap", raw_bytes, raw_bytes, page_sz,
                       rs.base(), rs.size());

  MemTracker::record_virtual_memory_type((address)rs.base(), mtGC);

  _virtual_space = new PSVirtualSpace(rs, page_sz);
  if (_virtual_space != NULL && _virtual_space->expand_by(_reserved_byte_size)) {
    _region_start = covered_region.start();
    _region_size = covered_region.word_size();
    BitMap::bm_word_t* map = (BitMap::bm_word_t*)_virtual_space->reserved_low_addr();
    _beg_bits.set_map(map);
    _beg_bits.set_size(bits / 2);
    _end_bits.set_map(map + words / 2);
    _end_bits.set_size(bits / 2);
    return true;
  }

  _region_start = 0;
  _region_size = 0;
  if (_virtual_space != NULL) {
    delete _virtual_space;
    _virtual_space = NULL;
    // Release memory reserved in the space.
    rs.release();
  }
  return false;
}

#ifdef ASSERT
extern size_t mark_bitmap_count;
extern size_t mark_bitmap_size;
#endif  // #ifdef ASSERT

bool
ParMarkBitMap::mark_obj(HeapWord* addr, size_t size)
{
  const idx_t beg_bit = addr_to_bit(addr);
  if (_beg_bits.par_set_bit(beg_bit)) {
    const idx_t end_bit = addr_to_bit(addr + size - 1);
    bool end_bit_ok = _end_bits.par_set_bit(end_bit);
    assert(end_bit_ok, "concurrency problem");
    DEBUG_ONLY(Atomic::inc_ptr(&mark_bitmap_count));
    DEBUG_ONLY(Atomic::add_ptr(size, &mark_bitmap_size));
    return true;
  }
  return false;
}

size_t ParMarkBitMap::live_words_in_range(HeapWord* beg_addr, oop end_obj) const
{
  assert(beg_addr <= (HeapWord*)end_obj, "bad range");
  assert(is_marked(end_obj), "end_obj must be live");

  idx_t live_bits = 0;

  // The bitmap routines require the right boundary to be word-aligned.
  const idx_t end_bit = addr_to_bit((HeapWord*)end_obj);
  const idx_t range_end = BitMap::word_align_up(end_bit);

  idx_t beg_bit = find_obj_beg(addr_to_bit(beg_addr), range_end);
  while (beg_bit < end_bit) {
    idx_t tmp_end = find_obj_end(beg_bit, range_end);
    assert(tmp_end < end_bit, "missing end bit");
    live_bits += tmp_end - beg_bit + 1;
    beg_bit = find_obj_beg(tmp_end + 1, range_end);
  }
  return bits_to_words(live_bits);
}

ParMarkBitMap::IterationStatus
ParMarkBitMap::iterate(ParMarkBitMapClosure* live_closure,
                       idx_t range_beg, idx_t range_end) const
{
  DEBUG_ONLY(verify_bit(range_beg);)
  DEBUG_ONLY(verify_bit(range_end);)
  assert(range_beg <= range_end, "live range invalid");

  // The bitmap routines require the right boundary to be word-aligned.
  const idx_t search_end = BitMap::word_align_up(range_end);

  idx_t cur_beg = find_obj_beg(range_beg, search_end);
  while (cur_beg < range_end) {
    const idx_t cur_end = find_obj_end(cur_beg, search_end);
    if (cur_end >= range_end) {
      // The obj ends outside the range.
      live_closure->set_source(bit_to_addr(cur_beg));
      return incomplete;
    }

    const size_t size = obj_size(cur_beg, cur_end);
    IterationStatus status = live_closure->do_addr(bit_to_addr(cur_beg), size);
    if (status != incomplete) {
      assert(status == would_overflow || status == full, "sanity");
      return status;
    }

    // Successfully processed the object; look for the next object.
    cur_beg = find_obj_beg(cur_end + 1, search_end);
  }

  live_closure->set_source(bit_to_addr(range_end));
  return complete;
}

ParMarkBitMap::IterationStatus
ParMarkBitMap::iterate(ParMarkBitMapClosure* live_closure,
                       ParMarkBitMapClosure* dead_closure,
                       idx_t range_beg, idx_t range_end,
                       idx_t dead_range_end) const
{
  DEBUG_ONLY(verify_bit(range_beg);)
  DEBUG_ONLY(verify_bit(range_end);)
  DEBUG_ONLY(verify_bit(dead_range_end);)
  assert(range_beg <= range_end, "live range invalid");
  assert(range_end <= dead_range_end, "dead range invalid");

  // The bitmap routines require the right boundary to be word-aligned.
  const idx_t live_search_end = BitMap::word_align_up(range_end);
  const idx_t dead_search_end = BitMap::word_align_up(dead_range_end);

  idx_t cur_beg = range_beg;
  if (range_beg < range_end && is_unmarked(range_beg)) {
    // The range starts with dead space.  Look for the next object, then fill.
    cur_beg = find_obj_beg(range_beg + 1, dead_search_end);
    const idx_t dead_space_end = MIN2(cur_beg - 1, dead_range_end - 1);
    const size_t size = obj_size(range_beg, dead_space_end);
    dead_closure->do_addr(bit_to_addr(range_beg), size);
  }

  while (cur_beg < range_end) {
    const idx_t cur_end = find_obj_end(cur_beg, live_search_end);
    if (cur_end >= range_end) {
      // The obj ends outside the range.
      live_closure->set_source(bit_to_addr(cur_beg));
      return incomplete;
    }

    const size_t size = obj_size(cur_beg, cur_end);
    IterationStatus status = live_closure->do_addr(bit_to_addr(cur_beg), size);
    if (status != incomplete) {
      assert(status == would_overflow || status == full, "sanity");
      return status;
    }

    // Look for the start of the next object.
    const idx_t dead_space_beg = cur_end + 1;
    cur_beg = find_obj_beg(dead_space_beg, dead_search_end);
    if (cur_beg > dead_space_beg) {
      // Found dead space; compute the size and invoke the dead closure.
      const idx_t dead_space_end = MIN2(cur_beg - 1, dead_range_end - 1);
      const size_t size = obj_size(dead_space_beg, dead_space_end);
      dead_closure->do_addr(bit_to_addr(dead_space_beg), size);
    }
  }

  live_closure->set_source(bit_to_addr(range_end));
  return complete;
}

#ifdef ASSERT
void ParMarkBitMap::verify_clear() const
{
  const idx_t* const beg = (const idx_t*)_virtual_space->committed_low_addr();
  const idx_t* const end = (const idx_t*)_virtual_space->committed_high_addr();
  for (const idx_t* p = beg; p < end; ++p) {
    assert(*p == 0, "bitmap not clear");
  }
}
#endif  // #ifdef ASSERT
