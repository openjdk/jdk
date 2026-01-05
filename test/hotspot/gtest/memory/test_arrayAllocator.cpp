/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 *
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

#include "memory/allocation.inline.hpp"
#include "unittest.hpp"

using Element = struct TestArrayAllocatorElement {
  double a;
  int b;
};

static void fill(Element* elements, int start, int size) {
  for (int i = 0; i < size; i++) {
    new (&elements[start + i]) Element{0.0, start + i};
  }
}

static Element* allocate_and_fill(int size) {
  Element* const elements = MallocArrayAllocator<Element>::allocate(size, mtTest);

  fill(elements, 0, size);

  return elements;
}

TEST_VM(ArrayAllocator, allocate) {
  const int size = 10;

  Element* const elements = allocate_and_fill(size);

  for (int i = 0; i < size; i++) {
    ASSERT_EQ(elements[i].b, i);
  }

  MallocArrayAllocator<Element>::free(elements);
}

TEST_VM(ArrayAllocator, reallocate_0) {
  const int size = 10;

  Element* const elements = allocate_and_fill(size);

  Element* const ret = MallocArrayAllocator<Element>::reallocate(elements, 0, mtTest);
  ASSERT_NE(ret, nullptr) << "We've chosen to NOT return nullptr when reallcting with 0";

  MallocArrayAllocator<Element>::free(ret);
}

TEST_VM(ArrayAllocator, reallocate_shrink) {
  const int size = 10;

  Element* const elements = allocate_and_fill(size);

  Element* const ret = MallocArrayAllocator<Element>::reallocate(elements, size / 2, mtTest);

  for (int i = 0; i < size / 2; i++) {
    ASSERT_EQ(ret[i].b, i);
  }

  MallocArrayAllocator<Element>::free(ret);
}

TEST_VM(ArrayAllocator, reallocate_grow) {
  const int size = 10;

  Element* const elements = allocate_and_fill(size);

  Element* const ret = MallocArrayAllocator<Element>::reallocate(elements, size * 2, mtTest);

  fill(ret, size, size);

  for (int i = 0; i < size * 2; i++) {
    ASSERT_EQ(ret[i].b, i);
  }

  MallocArrayAllocator<Element>::free(ret);
}
