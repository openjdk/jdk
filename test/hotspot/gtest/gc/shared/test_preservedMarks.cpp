/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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
#include "oops/oop.inline.hpp"
#include "unittest.hpp"

class ScopedDisabledBiasedLocking {
  bool _orig;
public:
  ScopedDisabledBiasedLocking() : _orig(UseBiasedLocking) { UseBiasedLocking = false; }
  ~ScopedDisabledBiasedLocking() { UseBiasedLocking = _orig; }
};

// Class to create a "fake" oop with a mark that will
// return true for calls to must_be_preserved().
class FakeOop {
  oopDesc _oop;

public:
  FakeOop() : _oop() { _oop.set_mark_raw(originalMark()); }

  oop get_oop() { return &_oop; }
  markOop mark() { return _oop.mark_raw(); }
  void set_mark(markOop m) { _oop.set_mark_raw(m); }
  void forward_to(oop obj) {
    markOop m = markOopDesc::encode_pointer_as_mark(obj);
    _oop.set_mark_raw(m);
  }

  static markOop originalMark() { return markOop(markOopDesc::lock_mask_in_place); }
  static markOop changedMark()  { return markOop(0x4711); }
};

TEST_VM(PreservedMarks, iterate_and_restore) {
  // Need to disable biased locking to easily
  // create oops that "must_be_preseved"
  ScopedDisabledBiasedLocking dbl;

  PreservedMarks pm;
  FakeOop o1;
  FakeOop o2;
  FakeOop o3;
  FakeOop o4;

  // Make sure initial marks are correct.
  ASSERT_EQ(o1.mark(), FakeOop::originalMark());
  ASSERT_EQ(o2.mark(), FakeOop::originalMark());
  ASSERT_EQ(o3.mark(), FakeOop::originalMark());
  ASSERT_EQ(o4.mark(), FakeOop::originalMark());

  // Change the marks and verify change.
  o1.set_mark(FakeOop::changedMark());
  o2.set_mark(FakeOop::changedMark());
  ASSERT_EQ(o1.mark(), FakeOop::changedMark());
  ASSERT_EQ(o2.mark(), FakeOop::changedMark());

  // Push o1 and o2 to have their marks preserved.
  pm.push(o1.get_oop(), o1.mark());
  pm.push(o2.get_oop(), o2.mark());

  // Fake a move from o1->o3 and o2->o4.
  o1.forward_to(o3.get_oop());
  o2.forward_to(o4.get_oop());
  ASSERT_EQ(o1.get_oop()->forwardee(), o3.get_oop());
  ASSERT_EQ(o2.get_oop()->forwardee(), o4.get_oop());
  // Adjust will update the PreservedMarks stack to
  // make sure the mark is updated at the new location.
  pm.adjust_during_full_gc();

  // Restore all preserved and verify that the changed
  // mark is now present at o3 and o4.
  pm.restore();
  ASSERT_EQ(o3.mark(), FakeOop::changedMark());
  ASSERT_EQ(o4.mark(), FakeOop::changedMark());
}
