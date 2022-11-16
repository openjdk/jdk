/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

inline oop ArchiveHeapLoader::decode_from_archive(narrowOop v) {
  assert(!CompressedOops::is_null(v), "narrow oop value can never be zero");
  assert(_narrow_oop_base_initialized, "relocation information must have been initialized");
  uintptr_t p = ((uintptr_t)_narrow_oop_base) + ((uintptr_t)v << _narrow_oop_shift);
  if (p >= _dumptime_base_0) {
    assert(p < _dumptime_top, "must be");
    if (p >= _dumptime_base_3) {
      p += _runtime_offset_3;
    } else if (p >= _dumptime_base_2) {
      p += _runtime_offset_2;
    } else if (p >= _dumptime_base_1) {
      p += _runtime_offset_1;
    } else {
      p += _runtime_offset_0;
    }
  }

  oop result = cast_to_oop((uintptr_t)p);
  assert(is_object_aligned(result), "address not aligned: " INTPTR_FORMAT, p2i((void*) result));
  return result;
}

#endif

#endif // SHARE_CDS_ARCHIVEHEAPLOADER_INLINE_HPP
