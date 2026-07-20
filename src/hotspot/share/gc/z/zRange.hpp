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

#ifndef SHARE_GC_Z_ZRANGE_HPP
#define SHARE_GC_Z_ZRANGE_HPP

#include "utilities/globalDefinitions.hpp"

template <typename Start, typename End>
class ZRange {
  friend class VMStructs;

public:
  using offset     = Start;
  using offset_end = End;

private:
  End    _start;
  size_t _size;

  // Used internally to create a ZRange.
  //
  // The end parameter is only used for verification and to distinguish
  // the constructors if End == Start.
  ZRange(End start, size_t size, End end);

public:
  ZRange();
  ZRange(Start start, size_t size);

  bool is_null() const;

  Start start() const;
  End end() const;

  size_t size() const;

  bool operator==(const ZRange& other) const;
  bool operator!=(const ZRange& other) const;

  bool contains(const ZRange& other) const;

  void grow_from_front(size_t size);
  void grow_from_back(size_t size);

  ZRange shrink_from_front(size_t size);
  ZRange shrink_from_back(size_t size);

  ZRange partition(size_t offset, size_t partition_size) const;
  ZRange first_part(size_t split_offset) const;
  ZRange last_part(size_t split_offset) const;

  bool adjacent_to(const ZRange& other) const;
};

#endif // SHARE_GC_Z_ZRANGE_HPP
