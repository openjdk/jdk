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
 *
 */

#include "precompiled.hpp"
#include "memory/allocation.hpp"
#include "nmt/memTracker.hpp"
#include "unittest.hpp"

class MemoryFileTrackerTest : public testing::Test {
public:
  size_t sz(int x) { return (size_t) x; }
  void basics() {
    MemoryFileTracker tracker(false);
    MemoryFileTracker::MemoryFile* file = tracker.make_file("test");
    tracker.allocate_memory(file, 0, 100, CALLER_PC, mtTest);
    EXPECT_EQ(file->_summary.by_type(mtTest)->committed(), sz(100));
    tracker.allocate_memory(file, 100, 100, CALLER_PC, mtTest);
    EXPECT_EQ(file->_summary.by_type(mtTest)->committed(), sz(200));
    tracker.allocate_memory(file, 200, 100, CALLER_PC, mtTest);
    EXPECT_EQ(file->_summary.by_type(mtTest)->committed(), sz(300));
    tracker.free_memory(file, 0, 300);
    EXPECT_EQ(file->_summary.by_type(mtTest)->committed(), sz(0));
    tracker.allocate_memory(file, 0, 100, CALLER_PC, mtTest);
    EXPECT_EQ(file->_summary.by_type(mtTest)->committed(), sz(100));
    tracker.free_memory(file, 50, 10);
    EXPECT_EQ(file->_summary.by_type(mtTest)->committed(), sz(90));
  };
};

TEST_VM_F(MemoryFileTrackerTest, Basics) {
  this->basics();
}
