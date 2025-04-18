/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZVIRTUALMEMORY_INLINE_HPP
#define SHARE_GC_Z_ZVIRTUALMEMORY_INLINE_HPP

#include "gc/z/zVirtualMemory.hpp"

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zRange.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

inline ZVirtualMemory::ZVirtualMemory()
  : ZRange() {}

inline ZVirtualMemory::ZVirtualMemory(zoffset start, size_t size)
  : ZRange(start, size) {
  // ZVirtualMemory is only used for ZGranuleSize multiple ranges
  assert(is_aligned(untype(start), ZGranuleSize), "must be multiple of ZGranuleSize");
  assert(is_aligned(size, ZGranuleSize), "must be multiple of ZGranuleSize");
}

inline ZVirtualMemory::ZVirtualMemory(const ZRange<zoffset, zoffset_end>& range)
  : ZVirtualMemory(range.start(), range.size()) {}

inline int ZVirtualMemory::granule_count() const {
  const size_t granule_count = size() >> ZGranuleSizeShift;

  assert(granule_count <= static_cast<size_t>(std::numeric_limits<int>::max()),
         "must not overflow an int %zu", granule_count);

  return static_cast<int>(granule_count);
}

#endif // SHARE_GC_Z_ZVIRTUALMEMORY_INLINE_HPP
