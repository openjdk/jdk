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
#include "nmt/nmtNativeCallStackStorage.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

using NCSS = NativeCallStackStorage;

class NMTNativeCallStackStorageTest : public testing::Test {};

TEST_VM_F(NMTNativeCallStackStorageTest, DoNotStoreStackIfNotDetailed) {
  NativeCallStack ncs{};
  NCSS ncss(false);
  NCSS::StackIndex si = ncss.push(ncs);
  EXPECT_TRUE(si.is_invalid());
  NativeCallStack ncs_received = ncss.get(si);
  EXPECT_TRUE(ncs_received.is_empty());
}

TEST_VM_F(NMTNativeCallStackStorageTest, CollisionsReceiveDifferentIndexes) {
  constexpr const int nr_of_stacks = 10;
  NativeCallStack ncs_arr[nr_of_stacks];
  for (int i = 0; i < nr_of_stacks; i++) {
    ncs_arr[i] = NativeCallStack((address*)(&i), 1);
  }

  NCSS ncss(true, 1);
  NCSS::StackIndex si_arr[nr_of_stacks];
  for (int i = 0; i < nr_of_stacks; i++) {
    si_arr[i] = ncss.push(ncs_arr[i]);
  }

  // Every SI should be different as every sack is different
  for (int i = 0; i < nr_of_stacks; i++) {
    for (int j = 0; j < nr_of_stacks; j++) {
      if (i == j) continue;
      EXPECT_FALSE(NCSS::StackIndex::equals(si_arr[i],si_arr[j]));
    }
  }
}
