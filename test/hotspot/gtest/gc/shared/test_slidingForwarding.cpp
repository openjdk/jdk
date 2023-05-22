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
#include "utilities/align.hpp"
#include "unittest.hpp"

#ifdef _LP64
#ifndef PRODUCT

static uintptr_t make_mark(uintptr_t target_region, uintptr_t offset) {
  return (target_region) << 3 | (offset << 4) | 3 /* forwarded */;
}

static uintptr_t make_fallback() {
  return ((uintptr_t(1) << 2) /* fallback */ | 3 /* forwarded */);
}

// Test simple forwarding within the same region.
TEST_VM(SlidingForwarding, simple) {
#ifndef PRODUCT
  FlagSetting fs(UseAltGCForwarding, true);
#else
  // Should not run this test with alt GC forwarding
  if (UseAltGCForwarding) return;
#endif
  HeapWord fakeheap[32] = { nullptr };
  HeapWord* heap = align_up(fakeheap, 8 * sizeof(HeapWord));
  oop obj1 = cast_to_oop(&heap[2]);
  oop obj2 = cast_to_oop(&heap[0]);
  SlidingForwarding::initialize(MemRegion(&heap[0], &heap[16]), 8);
  obj1->set_mark(markWord::prototype());
  SlidingForwarding::begin();

  SlidingForwarding::forward_to<true>(obj1, obj2);
  ASSERT_EQ(obj1->mark().value(), make_mark(0 /* target_region */, 0 /* offset */));
  ASSERT_EQ(SlidingForwarding::forwardee<true>(obj1), obj2);

  SlidingForwarding::end();
}

// Test forwardings crossing 2 regions.
TEST_VM(SlidingForwarding, tworegions) {
#ifndef PRODUCT
  FlagSetting fs(UseAltGCForwarding, true);
#else
  // Should not run this test with alt GC forwarding
  if (UseAltGCForwarding) return;
#endif
  HeapWord fakeheap[32] = { nullptr };
  HeapWord* heap = align_up(fakeheap, 8 * sizeof(HeapWord));
  oop obj1 = cast_to_oop(&heap[14]);
  oop obj2 = cast_to_oop(&heap[2]);
  oop obj3 = cast_to_oop(&heap[10]);
  SlidingForwarding::initialize(MemRegion(&heap[0], &heap[16]), 8);
  obj1->set_mark(markWord::prototype());
  SlidingForwarding::begin();

  SlidingForwarding::forward_to<true>(obj1, obj2);
  ASSERT_EQ(obj1->mark().value(), make_mark(0 /* target_region */, 2 /* offset */));
  ASSERT_EQ(SlidingForwarding::forwardee<true>(obj1), obj2);

  SlidingForwarding::forward_to<true>(obj1, obj3);
  ASSERT_EQ(obj1->mark().value(), make_mark(1 /* target_region */, 2 /* offset */));
  ASSERT_EQ(SlidingForwarding::forwardee<true>(obj1), obj3);

  SlidingForwarding::end();
}

// Test fallback forwardings crossing 4 regions.
TEST_VM(SlidingForwarding, fallback) {
#ifndef PRODUCT
  FlagSetting fs(UseAltGCForwarding, true);
#else
  // Should not run this test with alt GC forwarding
  if (UseAltGCForwarding) return;
#endif
  HeapWord fakeheap[32] = { nullptr };
  HeapWord* heap = align_up(fakeheap, 8 * sizeof(HeapWord));
  oop s_obj1 = cast_to_oop(&heap[12]);
  oop s_obj2 = cast_to_oop(&heap[13]);
  oop s_obj3 = cast_to_oop(&heap[14]);
  oop s_obj4 = cast_to_oop(&heap[15]);
  oop t_obj1 = cast_to_oop(&heap[2]);
  oop t_obj2 = cast_to_oop(&heap[4]);
  oop t_obj3 = cast_to_oop(&heap[10]);
  oop t_obj4 = cast_to_oop(&heap[12]);
  SlidingForwarding::initialize(MemRegion(&heap[0], &heap[16]), 4);
  s_obj1->set_mark(markWord::prototype());
  s_obj2->set_mark(markWord::prototype());
  s_obj3->set_mark(markWord::prototype());
  s_obj4->set_mark(markWord::prototype());
  SlidingForwarding::begin();

  SlidingForwarding::forward_to<true>(s_obj1, t_obj1);
  ASSERT_EQ(s_obj1->mark().value(), make_mark(0 /* target_region */, 2 /* offset */));
  ASSERT_EQ(SlidingForwarding::forwardee<true>(s_obj1), t_obj1);

  SlidingForwarding::forward_to<true>(s_obj2, t_obj2);
  ASSERT_EQ(s_obj2->mark().value(), make_mark(1 /* target_region */, 0 /* offset */));
  ASSERT_EQ(SlidingForwarding::forwardee<true>(s_obj2), t_obj2);

  SlidingForwarding::forward_to<true>(s_obj3, t_obj3);
  ASSERT_EQ(s_obj3->mark().value(), make_fallback());
  ASSERT_EQ(SlidingForwarding::forwardee<true>(s_obj3), t_obj3);

  SlidingForwarding::forward_to<true>(s_obj4, t_obj4);
  ASSERT_EQ(s_obj4->mark().value(), make_fallback());
  ASSERT_EQ(SlidingForwarding::forwardee<true>(s_obj4), t_obj4);

  SlidingForwarding::end();
}

#endif // PRODUCT
#endif // _LP64
