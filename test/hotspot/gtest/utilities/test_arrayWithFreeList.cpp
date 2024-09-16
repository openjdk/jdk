/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "unittest.hpp"
#include "utilities/arrayWithFreeList.hpp"

using A = ArrayWithFreeList<int, mtTest>;

class ArrayWithFreeListTest  : public testing::Test {
};

TEST_VM_F(ArrayWithFreeListTest, FreeingShouldReuseMemory) {
  A alloc;
  A::I i = alloc.allocate(1);
  int* x = &alloc.at(i);
  alloc.deallocate(i);
  i = alloc.allocate(1);
  int* y = &alloc.at(i);
  EXPECT_EQ(x, y);
}

TEST_VM_F(ArrayWithFreeListTest, FreeingInTheMiddleWorks) {
  A alloc;
  A::I i0 = alloc.allocate(0);
  A::I i1 = alloc.allocate(0);
  A::I i2 = alloc.allocate(0);
  int* p1 = &alloc.at(i1);
  alloc.deallocate(i1);
  A::I i3 = alloc.allocate(0);
  EXPECT_EQ(p1, &alloc.at(i3));
}

TEST_VM_F(ArrayWithFreeListTest, MakeVerySmallArray) {
  using Elem = int; using Index = uint8_t;
  using SmallArray = ArrayWithFreeList<Elem, mtTest, Index>;
  SmallArray a;

  int success_count = 0;
  int failure_count = 0;
  for (int i = 0; i < 128; i++) {
    SmallArray::I x = a.allocate(0);
    if (x != SmallArray::nil) success_count++;
    else failure_count++;
  }
  EXPECT_EQ(0, failure_count);
  EXPECT_EQ(128, success_count);

  for (int i = 0; i < 128; i++) {
    SmallArray::I x = a.allocate(0);
    if (x != SmallArray::nil) success_count++;
    else failure_count++;
  }
  EXPECT_EQ(1, failure_count);
  EXPECT_EQ(255, success_count);
}

TEST_VM_F(ArrayWithFreeListTest, BackedByFixedArray) {
  A::BackingElement data[8];
  A a(data, 8);

  int success_count = 0;
  int failure_count = 0;
  for (int i = 0; i < 8; i++) {
    A::I x = a.allocate(0);
    if (x != A::nil) success_count++;
    else failure_count++;
  }
  EXPECT_EQ(8, success_count);
  EXPECT_EQ(0, failure_count);
  A::I x = a.allocate(0);
  A::I n = A::nil;
  EXPECT_EQ(n, x);
}
