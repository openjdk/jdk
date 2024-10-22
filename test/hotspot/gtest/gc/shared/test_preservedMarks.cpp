/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/fullGCForwarding.inline.hpp"
#include "oops/oop.inline.hpp"
#include "unittest.hpp"

static markWord originalMark() { return markWord(markWord::lock_mask_in_place); }
static markWord changedMark()  { return markWord(0x4711); }

#define ASSERT_MARK_WORD_EQ(a, b) ASSERT_EQ((a).value(), (b).value())

TEST_VM(PreservedMarks, iterate_and_restore) {
  PreservedMarks pm;

  HeapWord fakeheap[32] = { nullptr };
  HeapWord* heap = align_up(fakeheap, 8 * sizeof(HeapWord));
  FullGCForwarding::initialize(MemRegion(&heap[0], &heap[16]));

  oop o1 = cast_to_oop(&heap[0]); o1->set_mark(originalMark());
  oop o2 = cast_to_oop(&heap[2]); o2->set_mark(originalMark());
  oop o3 = cast_to_oop(&heap[4]); o3->set_mark(originalMark());
  oop o4 = cast_to_oop(&heap[6]); o4->set_mark(originalMark());

  // Make sure initial marks are correct.
  ASSERT_MARK_WORD_EQ(o1->mark(), originalMark());
  ASSERT_MARK_WORD_EQ(o2->mark(), originalMark());
  ASSERT_MARK_WORD_EQ(o3->mark(), originalMark());
  ASSERT_MARK_WORD_EQ(o4->mark(), originalMark());

  // Change the marks and verify change.
  o1->set_mark(changedMark());
  o2->set_mark(changedMark());
  ASSERT_MARK_WORD_EQ(o1->mark(), changedMark());
  ASSERT_MARK_WORD_EQ(o2->mark(), changedMark());

  // Push o1 and o2 to have their marks preserved.
  pm.push_if_necessary(o1, o1->mark());
  pm.push_if_necessary(o2, o2->mark());

  // Fake a move from o1->o3 and o2->o4.
  FullGCForwarding::forward_to(o1, o3);
  FullGCForwarding::forward_to(o2, o4);
  ASSERT_EQ(FullGCForwarding::forwardee(o1), o3);
  ASSERT_EQ(FullGCForwarding::forwardee(o2), o4);
  // Adjust will update the PreservedMarks stack to
  // make sure the mark is updated at the new location.
  pm.adjust_during_full_gc();

  // Restore all preserved and verify that the changed
  // mark is now present at o3 and o4.
  pm.restore();
  ASSERT_MARK_WORD_EQ(o3->mark(), changedMark());
  ASSERT_MARK_WORD_EQ(o4->mark(), changedMark());
}
