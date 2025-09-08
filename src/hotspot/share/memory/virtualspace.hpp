/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_VIRTUALSPACE_HPP
#define SHARE_MEMORY_VIRTUALSPACE_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class outputStream;
class ReservedSpace;

// VirtualSpace is data structure for committing a previously reserved address range in smaller chunks.

class VirtualSpace {
  friend class VMStructs;
 private:
  // Reserved area
  char* _low_boundary;
  char* _high_boundary;

  // Committed area
  char* _low;
  char* _high;

  // The entire space has been committed and pinned in memory, no
  // os::commit_memory() or os::uncommit_memory().
  bool _special;

  // Need to know if commit should be executable.
  bool   _executable;

  // MPSS Support
  // Each virtualspace region has a lower, middle, and upper region.
  // Each region has an end boundary and a high pointer which is the
  // high water mark for the last allocated byte.
  // The lower and upper unaligned to LargePageSizeInBytes uses default page.
  // size.  The middle region uses large page size.
  char* _lower_high;
  char* _middle_high;
  char* _upper_high;

  char* _lower_high_boundary;
  char* _middle_high_boundary;
  char* _upper_high_boundary;

  size_t _lower_alignment;
  size_t _middle_alignment;
  size_t _upper_alignment;

  // MPSS Accessors
  char* lower_high() const { return _lower_high; }
  char* middle_high() const { return _middle_high; }
  char* upper_high() const { return _upper_high; }

  char* lower_high_boundary() const { return _lower_high_boundary; }
  char* middle_high_boundary() const { return _middle_high_boundary; }
  char* upper_high_boundary() const { return _upper_high_boundary; }

  size_t lower_alignment() const { return _lower_alignment; }
  size_t middle_alignment() const { return _middle_alignment; }
  size_t upper_alignment() const { return _upper_alignment; }

 public:
  // Committed area
  char* low()  const { return _low; }
  char* high() const { return _high; }

  // Reserved area
  char* low_boundary()  const { return _low_boundary; }
  char* high_boundary() const { return _high_boundary; }

  bool special() const { return _special; }

 public:
  // Initialization
  VirtualSpace();
  bool initialize_with_granularity(ReservedSpace rs, size_t committed_byte_size, size_t max_commit_ganularity);
  bool initialize(ReservedSpace rs, size_t committed_byte_size);

  // Destruction
  ~VirtualSpace();

  // Reserved memory
  size_t reserved_size() const;
  // Actually committed OS memory
  size_t actual_committed_size() const;
  // Memory used/expanded in this virtual space
  size_t committed_size() const;
  // Memory left to use/expand in this virtual space
  size_t uncommitted_size() const;

  bool   contains(const void* p) const;

  // Operations
  // returns true on success, false otherwise
  bool expand_by(size_t bytes, bool pre_touch = false);
  void shrink_by(size_t bytes);
  void release();

  void check_for_contiguity() PRODUCT_RETURN;

  // Debugging
  void print_on(outputStream* out) const PRODUCT_RETURN;
  void print() const;

  void print_space_boundaries_on(outputStream* out) const;
};

#endif // SHARE_MEMORY_VIRTUALSPACE_HPP
