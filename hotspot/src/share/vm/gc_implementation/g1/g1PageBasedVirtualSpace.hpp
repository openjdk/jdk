/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1PAGEBASEDVIRTUALSPACE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1PAGEBASEDVIRTUALSPACE_HPP

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "runtime/virtualspace.hpp"
#include "utilities/bitMap.hpp"

// Virtual space management helper for a virtual space with an OS page allocation
// granularity.
// (De-)Allocation requests are always OS page aligned by passing a page index
// and multiples of pages.
// The implementation gives an error when trying to commit or uncommit pages that
// have already been committed or uncommitted.
class G1PageBasedVirtualSpace VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
 private:
  // Reserved area addresses.
  char* _low_boundary;
  char* _high_boundary;

  // The commit/uncommit granularity in bytes.
  size_t _page_size;

  // Bitmap used for verification of commit/uncommit operations.
  BitMap _committed;

  // Bitmap used to keep track of which pages are dirty or not for _special
  // spaces. This is needed because for those spaces the underlying memory
  // will only be zero filled the first time it is committed. Calls to commit
  // will use this bitmap and return whether or not the memory is zero filled.
  BitMap _dirty;

  // Indicates that the entire space has been committed and pinned in memory,
  // os::commit_memory() or os::uncommit_memory() have no function.
  bool _special;

  // Indicates whether the committed space should be executable.
  bool _executable;

  // Returns the index of the page which contains the given address.
  uintptr_t  addr_to_page_index(char* addr) const;
  // Returns the address of the given page index.
  char*  page_start(uintptr_t index);
  // Returns the byte size of the given number of pages.
  size_t byte_size_for_pages(size_t num);

  // Returns true if the entire area is backed by committed memory.
  bool is_area_committed(uintptr_t start, size_t size_in_pages) const;
  // Returns true if the entire area is not backed by committed memory.
  bool is_area_uncommitted(uintptr_t start, size_t size_in_pages) const;

 public:

  // Commit the given area of pages starting at start being size_in_pages large.
  // Returns true if the given area is zero filled upon completion.
  bool commit(uintptr_t start, size_t size_in_pages);

  // Uncommit the given area of pages starting at start being size_in_pages large.
  void uncommit(uintptr_t start, size_t size_in_pages);

  // Initialization
  G1PageBasedVirtualSpace();
  bool initialize_with_granularity(ReservedSpace rs, size_t page_size);

  // Destruction
  ~G1PageBasedVirtualSpace();

  // Amount of reserved memory.
  size_t reserved_size() const;
  // Memory used in this virtual space.
  size_t committed_size() const;
  // Memory left to use/expand in this virtual space.
  size_t uncommitted_size() const;

  bool contains(const void* p) const;

  MemRegion reserved() {
    MemRegion x((HeapWord*)_low_boundary, reserved_size() / HeapWordSize);
    return x;
  }

  void release();

  void check_for_contiguity() PRODUCT_RETURN;

  // Debugging
  void print_on(outputStream* out) PRODUCT_RETURN;
  void print();
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1PAGEBASEDVIRTUALSPACE_HPP
