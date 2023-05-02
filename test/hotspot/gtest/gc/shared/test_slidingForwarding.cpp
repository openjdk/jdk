/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/slidingForwarding.inline.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"
#include "unittest.hpp"

#ifdef _LP64

static uintptr_t make_mark(uintptr_t target_region, uintptr_t offset) {
  return (target_region) << 3 | (offset << 4) | 3 /* forwarded */;
}

static uintptr_t make_fallback() {
  return ((uintptr_t(1) << 2) /* fallback */ | 3 /* forwarded */);
}

// Test simple forwarding within the same region.
TEST_VM(SlidingForwarding, simple) {
  HeapWord heap[16] = { nullptr };
  oop obj1 = cast_to_oop(&heap[2]);
  oop obj2 = cast_to_oop(&heap[0]);
  SlidingForwarding sf(MemRegion(&heap[0], &heap[16]), 8);
  obj1->set_mark(markWord::prototype());
  sf.begin();

  sf.forward_to(obj1, obj2);
  ASSERT_EQ(obj1->mark().value(), make_mark(0 /* target_region */, 0 /* offset */));
  ASSERT_EQ(sf.forwardee(obj1), obj2);

  sf.end();
}

// Test forwardings crossing 2 regions.
TEST_VM(SlidingForwarding, tworegions) {
  HeapWord heap[16] = { nullptr };
  oop obj1 = cast_to_oop(&heap[14]);
  oop obj2 = cast_to_oop(&heap[2]);
  oop obj3 = cast_to_oop(&heap[10]);
  SlidingForwarding sf(MemRegion(&heap[0], &heap[16]), 8);
  obj1->set_mark(markWord::prototype());
  sf.begin();

  sf.forward_to(obj1, obj2);
  ASSERT_EQ(obj1->mark().value(), make_mark(0 /* target_region */, 2 /* offset */));
  ASSERT_EQ(sf.forwardee(obj1), obj2);

  sf.forward_to(obj1, obj3);
  ASSERT_EQ(obj1->mark().value(), make_mark(1 /* target_region */, 2 /* offset */));
  ASSERT_EQ(sf.forwardee(obj1), obj3);

  sf.end();
}

// Test fallback forwardings crossing 4 regions.
TEST_VM(SlidingForwarding, fallback) {
  HeapWord heap[16] = { nullptr };
  oop obj1 = cast_to_oop(&heap[14]);
  oop obj2 = cast_to_oop(&heap[2]);
  oop obj3 = cast_to_oop(&heap[4]);
  oop obj4 = cast_to_oop(&heap[10]);
  oop obj5 = cast_to_oop(&heap[12]);
  SlidingForwarding sf(MemRegion(&heap[0], &heap[16]), 4);
  obj1->set_mark(markWord::prototype());
  sf.begin();

  sf.forward_to(obj1, obj2);
  ASSERT_EQ(obj1->mark().value(), make_mark(0 /* target_region */, 2 /* offset */));
  ASSERT_EQ(sf.forwardee(obj1), obj2);

  sf.forward_to(obj1, obj3);
  ASSERT_EQ(obj1->mark().value(), make_mark(1 /* target_region */, 0 /* offset */));
  ASSERT_EQ(sf.forwardee(obj1), obj3);

  sf.forward_to(obj1, obj4);
  ASSERT_EQ(obj1->mark().value(), make_fallback());
  ASSERT_EQ(sf.forwardee(obj1), obj4);

  sf.forward_to(obj1, obj5);
  ASSERT_EQ(obj1->mark().value(), make_fallback());
  ASSERT_EQ(sf.forwardee(obj1), obj5);

  sf.end();
}

#endif // _LP64
