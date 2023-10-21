/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_ARCHIVEHEAPLOADER_INLINE_HPP
#define SHARE_CDS_ARCHIVEHEAPLOADER_INLINE_HPP

#include "cds/archiveHeapLoader.hpp"

#include "oops/compressedOops.inline.hpp"
#include "utilities/align.hpp"

#if INCLUDE_CDS_JAVA_HEAP

template<bool IS_MAPPED>
inline oop ArchiveHeapLoader::decode_from_archive_impl(narrowOop v) {
  assert(!CompressedOops::is_null(v), "narrow oop value can never be zero");
  assert(_narrow_oop_base_initialized, "relocation information must have been initialized");
  uintptr_t p = ((uintptr_t)_narrow_oop_base) + ((uintptr_t)v << _narrow_oop_shift);
  if (IS_MAPPED) {
    assert(_dumptime_base == UINTPTR_MAX, "must be");
  } else if (p >= _dumptime_base) {
    assert(p < _dumptime_top, "must be");
    p += _runtime_offset;
  }

  oop result = cast_to_oop((uintptr_t)p);
  assert(is_object_aligned(result), "address not aligned: " INTPTR_FORMAT, p2i((void*) result));
  return result;
}

inline oop ArchiveHeapLoader::decode_from_archive(narrowOop v) {
  return decode_from_archive_impl<false>(v);
}

inline oop ArchiveHeapLoader::decode_from_mapped_archive(narrowOop v) {
  return decode_from_archive_impl<true>(v);
}

#endif

#endif // SHARE_CDS_ARCHIVEHEAPLOADER_INLINE_HPP
