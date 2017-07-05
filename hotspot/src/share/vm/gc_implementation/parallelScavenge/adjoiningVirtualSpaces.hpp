/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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


// Contains two virtual spaces that each can individually span
// most of the reserved region but committed parts of which
// cannot overlap.
//
//      +-------+ <--- high_boundary for H
//      |       |
//      |   H   |
//      |       |
//      |       |
//      |       |
//      --------- <--- low for H
//      |       |
//      ========= <--- low_boundary for H, high_boundary for L
//      |       |
//      |       |
//      |       |
//      --------- <--- high for L
//      |       |
//      |   L   |
//      |       |
//      |       |
//      |       |
//      +-------+ <--- low_boundary for L
//
// Each virtual space in the AdjoiningVirtualSpaces grows and shrink
// within its reserved region (between the low_boundary and the
// boundary) independently.  If L want to grow above its high_boundary,
// then the high_boundary of L and the low_boundary of H must be
// moved up consistently.  AdjoiningVirtualSpaces provide the
// interfaces for moving the this boundary.

class AdjoiningVirtualSpaces {
  // space at the high end and the low end, respectively
  PSVirtualSpace*    _high;
  PSVirtualSpace*    _low;

  // The reserved space spanned by the two spaces.
  ReservedSpace      _reserved_space;

  // The minimum byte size for the low space.  It will not
  // be shrunk below this value.
  size_t _min_low_byte_size;
  // Same for the high space
  size_t _min_high_byte_size;

  const size_t _alignment;

 public:
  // Allocates two virtual spaces that will be located at the
  // high and low ends.  Does no initialization.
  AdjoiningVirtualSpaces(ReservedSpace rs,
                         size_t min_low_byte_size,
                         size_t min_high_byte_size,
                         size_t alignment);

  // accessors
  PSVirtualSpace* high() { return _high; }
  PSVirtualSpace* low()  { return _low; }
  ReservedSpace reserved_space() { return _reserved_space; }
  size_t min_low_byte_size() { return _min_low_byte_size; }
  size_t min_high_byte_size() { return _min_high_byte_size; }
  size_t alignment() const { return _alignment; }

  // move boundary between the two spaces up
  bool adjust_boundary_up(size_t size_in_bytes);
  // and down
  bool adjust_boundary_down(size_t size_in_bytes);

  // Maximum byte size for the high space.
  size_t high_byte_size_limit() {
    return _reserved_space.size() - _min_low_byte_size;
  }
  // Maximum byte size for the low space.
  size_t low_byte_size_limit() {
    return _reserved_space.size() - _min_high_byte_size;
  }

  // Sets the boundaries for the virtual spaces and commits and
  // initial size;
  void initialize(size_t max_low_byte_size,
                  size_t init_low_byte_size,
                  size_t init_high_byte_size);
};
