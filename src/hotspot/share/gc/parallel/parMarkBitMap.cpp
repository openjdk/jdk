/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/parallel/parMarkBitMap.inline.hpp"
#include "gc/parallel/psCompactionManager.inline.hpp"
#include "gc/parallel/psParallelCompact.inline.hpp"
#include "memory/memoryReserver.hpp"
#include "nmt/memTracker.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.inline.hpp"

bool
ParMarkBitMap::initialize(MemRegion covered_region)
{
  const idx_t bits = words_to_bits(covered_region.word_size());

  const size_t words = bits / BitsPerWord;
  const size_t raw_bytes = words * sizeof(idx_t);
  const size_t page_sz = os::page_size_for_region_aligned(raw_bytes, 10);
  const size_t granularity = os::vm_allocation_granularity();
  const size_t rs_align = MAX2(page_sz, granularity);

  _reserved_byte_size = align_up(raw_bytes, rs_align);

  ReservedSpace rs = MemoryReserver::reserve(_reserved_byte_size,
                                             rs_align,
                                             page_sz,
                                             mtGC);

  if (!rs.is_reserved()) {
    // Failed to reserve memory for the bitmap,
    return false;
  }

  const size_t used_page_sz = rs.page_size();
  os::trace_page_sizes("Mark Bitmap", raw_bytes, raw_bytes,
                       rs.base(), rs.size(), used_page_sz);

  MemTracker::record_virtual_memory_tag(rs, mtGC);

  _virtual_space = new PSVirtualSpace(rs, page_sz);

  if (!_virtual_space->expand_by(_reserved_byte_size)) {
    // Failed to commit memory for the bitmap.

    delete _virtual_space;

    // Release memory reserved in the space.
    MemoryReserver::release(rs);

    return false;
  }

  _heap_start = covered_region.start();
  _heap_size = covered_region.word_size();
  BitMap::bm_word_t* map = (BitMap::bm_word_t*)_virtual_space->reserved_low_addr();
  _beg_bits = BitMapView(map, bits);

  return true;
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
