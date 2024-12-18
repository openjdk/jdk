/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "nmt/memTag.hpp"
#include "nmt/contiguousAllocator.hpp"
#include "unittest.hpp"

TEST_VM(ContiguousAllocatorTest, AllocatingManySmallPiecesShouldSucceed) {
  NMTContiguousAllocator nca{os::vm_page_size(), mtTest};
  EXPECT_TRUE(nca.is_reserved());

  const size_t num_pieces = 1024;
  const size_t piece_size = os::vm_page_size() / num_pieces;
  char* r[num_pieces];
  for (size_t i = 0; i < num_pieces; i++) {
    r[i] = nca.alloc(piece_size);
  }
  for (size_t i = 0; i < num_pieces; i++) {
    if (r[i] == nullptr) {
      EXPECT_FALSE(r[i] == nullptr) << "Allocation number " << i << " failed";
      break;
    } else {
      // Write to each byte, this should not crash.
      for (size_t j = 0; j < piece_size; j++) {
        *(r[i] + j) = 'a';
      }
    }
  }
}

TEST_VM(ContiguousAllocatorTest, AllocatingMoreThanReservedShouldFail) {
  NMTContiguousAllocator nca{os::vm_page_size(), mtTest};
  EXPECT_TRUE(nca.is_reserved());
  char* no1 = nca.alloc(os::vm_page_size());
  EXPECT_NE(nullptr, no1);
  char* no2 = nca.alloc(1);
  EXPECT_EQ(nullptr, no2);
}

TEST_VM(ContiguousAllocatorTest, CopyingConstructorGivesSeparateMemory) {
  NMTContiguousAllocator nca(os::vm_page_size(), mtTest);
  NMTContiguousAllocator nca_copy(nca);
  char* ncap = nca.alloc(os::vm_page_size());
  char* ncacp = nca_copy.alloc(os::vm_page_size());
  EXPECT_NE(nullptr, ncap);
  EXPECT_NE(nullptr, ncacp);
  EXPECT_NE(ncap, ncacp);
}

TEST_VM(ContiguousAllocatorTest, CopyingConstructorCopiesTheMemory) {
  NMTContiguousAllocator nca(os::vm_page_size(), mtTest);
  char* ncap = nca.alloc(os::vm_page_size());
  strcpy(ncap, "Hello, world");
  NMTContiguousAllocator nca_copy(nca);
  char* str = nca_copy.at_offset(0);
  EXPECT_EQ(0, strcmp("Hello, world", str));
}
