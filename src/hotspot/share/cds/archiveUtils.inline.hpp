/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_ARCHIVEUTILS_INLINE_HPP
#define SHARE_CDS_ARCHIVEUTILS_INLINE_HPP

#include "cds/archiveUtils.hpp"

#include "cds/archiveBuilder.hpp"
#include "oops/array.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/growableArray.hpp"

inline bool SharedDataRelocator::do_bit(size_t offset) {
  address* p = _patch_base + offset;
  assert(_patch_base <= p && p < _patch_end, "must be");

  address old_ptr = *p;
  assert(_valid_old_base <= old_ptr && old_ptr < _valid_old_end, "must be");
  assert(old_ptr != nullptr, "bits for null pointers should have been cleaned at dump time");

  address new_ptr = old_ptr + _delta;
  assert(new_ptr != nullptr, "don't point to the bottom of the archive"); // See ArchivePtrMarker::mark_pointer().
  assert(_valid_new_base <= new_ptr && new_ptr < _valid_new_end, "must be");

  DEBUG_ONLY(log_trace(cds, reloc)("Patch2: @%8d [" PTR_FORMAT "] " PTR_FORMAT " -> " PTR_FORMAT,
                                   (int)offset, p2i(p), p2i(old_ptr), p2i(new_ptr)));
  *p = new_ptr;
  return true; // keep iterating
}

// Returns the address of an Array<T> that's allocated in the ArchiveBuilder "buffer" space.
template <typename T>
Array<T>* ArchiveUtils::archive_array(GrowableArray<T>* tmp_array) {
  Array<T>* archived_array = ArchiveBuilder::new_ro_array<T>(tmp_array->length());
  for (int i = 0; i < tmp_array->length(); i++) {
    archived_array->at_put(i, tmp_array->at(i));
    if (std::is_pointer<T>::value) {
      ArchivePtrMarker::mark_pointer(archived_array->adr_at(i));
    }
  }

  return archived_array;
}


#endif // SHARE_CDS_ARCHIVEUTILS_INLINE_HPP
