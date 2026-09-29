/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_RESERVEDSPACE_HPP
#define SHARE_MEMORY_RESERVEDSPACE_HPP

#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// ReservedSpace is a data structure for describing a reserved contiguous address range.

class ReservedSpace {
  char*  _base;
  size_t _size;
  size_t _alignment;
  size_t _page_size;
  bool   _executable;
  bool   _special;

  void sanity_checks() NOT_DEBUG_RETURN;

public:
  // Constructor for non-reserved memory.
  ReservedSpace()
    : _base(nullptr),
      _size(0),
      _alignment(0),
      _page_size(0),
      _executable(false),
      _special(false) {}

  // Main constructor
  ReservedSpace(char*  base,
                size_t size,
                size_t alignment,
                size_t page_size,
                bool   executable,
                bool   special)
    : _base(base),
      _size(size),
      _alignment(alignment),
      _page_size(page_size),
      _executable(executable),
      _special(special) {
    sanity_checks();
  }

  bool is_reserved() const {
    return _base != nullptr;
  }

  char* base() const {
    return _base;
  }

  size_t size() const {
    return _size;
  }

  char* end() const {
    return _base + _size;
  }

  size_t alignment() const {
    return _alignment;
  }

  size_t page_size() const {
    return _page_size;
  }

  bool executable() const {
    return _executable;
  }

  bool special() const {
    return _special;
  }

  ReservedSpace partition(size_t offset, size_t partition_size, size_t alignment) const {
    assert(offset + partition_size <= size(), "partition failed");

    char* const partition_base = base() + offset;
    assert(is_aligned(partition_base, alignment), "partition base must be aligned");

    return ReservedSpace(partition_base,
                         partition_size,
                         alignment,
                         _page_size,
                         _executable,
                         _special);
  }

  ReservedSpace partition(size_t offset, size_t partition_size) const {
    return partition(offset, partition_size, _alignment);
  }

  ReservedSpace first_part(size_t split_offset, size_t alignment) const {
    return partition(0, split_offset, alignment);
  }

  ReservedSpace first_part(size_t split_offset) const {
    return first_part(split_offset, _alignment);
  }

  ReservedSpace last_part (size_t split_offset, size_t alignment) const {
    return partition(split_offset, _size - split_offset, alignment);
  }

  ReservedSpace last_part (size_t split_offset) const {
    return last_part(split_offset, _alignment);
  }
};

// Class encapsulating behavior specific to memory reserved for the Java heap.
class ReservedHeapSpace : public ReservedSpace {
private:
  const size_t _noaccess_prefix;

public:
  // Constructor for non-reserved memory.
  ReservedHeapSpace()
    : ReservedSpace(),
      _noaccess_prefix() {}

  ReservedHeapSpace(const ReservedSpace& reserved, size_t noaccess_prefix)
    : ReservedSpace(reserved),
      _noaccess_prefix(noaccess_prefix) {}

  size_t noaccess_prefix() const { return _noaccess_prefix; }

  // Returns the base to be used for compression, i.e. so that null can be
  // encoded safely and implicit null checks can work.
  char* compressed_oop_base() const { return base() - _noaccess_prefix; }
};

#endif // SHARE_MEMORY_RESERVEDSPACE_HPP
