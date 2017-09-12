/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved.
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

// The test function is only available in debug builds
#ifdef ASSERT

#include "unittest.hpp"

void ChunkManager_test_list_index();

TEST(ChunkManager, list_index) {
  // The ChunkManager is only available in metaspace.cpp,
  // so the test code is located in that file.
  ChunkManager_test_list_index();

}

extern void* setup_chunkmanager_returntests();
extern void teardown_chunkmanager_returntests(void*);
extern void run_chunkmanager_returntests(void* p, float phase_length_factor);

class ChunkManagerReturnTest : public ::testing::Test {
protected:
  void* _test;
  virtual void SetUp() {
    _test = setup_chunkmanager_returntests();
  }
  virtual void TearDown() {
    teardown_chunkmanager_returntests(_test);
  }
};

TEST_VM_F(ChunkManagerReturnTest, test00) { run_chunkmanager_returntests(_test, 0.0f); }
TEST_VM_F(ChunkManagerReturnTest, test05) { run_chunkmanager_returntests(_test, 0.5f); }
TEST_VM_F(ChunkManagerReturnTest, test10) { run_chunkmanager_returntests(_test, 1.0f); }

#endif // ASSERT
