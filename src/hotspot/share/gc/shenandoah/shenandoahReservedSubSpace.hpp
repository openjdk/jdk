/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHRESERVEDSUBSPACE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHRESERVEDSUBSPACE_HPP

#include "memory/memRegion.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// Memory region that may or may not be its own page-aligned reserved space.
// If "special", it may share a page with adjacent data and will be pre-committed.
// If not "special", it is page-aligned and committable.
class SubSpace : private MemRegion {

  bool _special;
  size_t _pagesize;

public:

  SubSpace() : MemRegion(), _special(false), _pagesize(0) {}
  //SubSpace(const SubSpace& other) : MemRegion(other), _special(other._special), _pagesize(other._pagesize) {}
  SubSpace(MemRegion mr, bool special, size_t pagesize) :
    MemRegion(mr), _special(special), _pagesize(pagesize) {}

  using MemRegion::is_empty;
  using MemRegion::byte_size;
  using MemRegion::word_size;
  using MemRegion::start;
  using MemRegion::end;

  bool is_null() const      { return start() == nullptr; }
  bool special() const      { return _special; }
  size_t pagesize() const   { return _pagesize; }

  MemRegion mr() const  { return (MemRegion) (*this); }

  // Split region such that first_part=[orig.start, x), last_part=[x, orig.end).
  // Splitting an empty regions results in two null regions.
  // If x is end, last_part will be null. If x is 0, first part will be null.
  void split(size_t byte_size, SubSpace& left, SubSpace& right) const;

  // Returns a region whose start address is aligned up to alignment. If region
  // is too small (or null), returns null region.
  SubSpace aligned_start(size_t alignment) const;

  SubSpace first_part(size_t byte_size) const;

#ifdef ASSERT
  void verify() const;
  void verify_not_null() const;
#endif
};


#endif // SHARE_GC_SHENANDOAH_SHENANDOAHRESERVEDSUBSPACE_HPP
