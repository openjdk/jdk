/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_ARCHIVEUTILS_INLINE_HPP
#define SHARE_MEMORY_ARCHIVEUTILS_INLINE_HPP

#include "memory/archiveUtils.hpp"
#include "utilities/bitMap.inline.hpp"

template <bool COMPACTING>
inline bool SharedDataRelocator<COMPACTING>::do_bit(size_t offset) {
  address* p = _patch_base + offset;
  assert(_patch_base <= p && p < _patch_end, "must be");

  address old_ptr = *p;
  assert(_valid_old_base <= old_ptr && old_ptr < _valid_old_end, "must be");

  if (COMPACTING) {
    // Start-up performance: use a template parameter to elide this block for run-time archive
    // relocation.
    assert(Arguments::is_dumping_archive(), "Don't do this during run-time archive loading!");
    if (old_ptr == NULL) {
      _ptrmap->clear_bit(offset);
      DEBUG_ONLY(log_trace(cds, reloc)("Clearing pointer [" PTR_FORMAT  "] -> NULL @ " SIZE_FORMAT_W(9), p2i(p), offset));
      return true;
    } else {
      _max_non_null_offset = offset;
    }
  } else {
    assert(old_ptr != NULL, "bits for NULL pointers should have been cleaned at dump time");
  }

  address new_ptr = old_ptr + _delta;
  assert(new_ptr != NULL, "don't point to the bottom of the archive"); // See ArchivePtrMarker::mark_pointer().
  assert(_valid_new_base <= new_ptr && new_ptr < _valid_new_end, "must be");

  DEBUG_ONLY(log_trace(cds, reloc)("Patch2: @%8d [" PTR_FORMAT "] " PTR_FORMAT " -> " PTR_FORMAT,
                                   (int)offset, p2i(p), p2i(old_ptr), p2i(new_ptr)));
  *p = new_ptr;
  return true; // keep iterating
}

#endif // SHARE_MEMORY_ARCHIVEUTILS_INLINE_HPP
