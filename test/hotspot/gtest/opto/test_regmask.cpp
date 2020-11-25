/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/regmask.hpp"
#include "unittest.hpp"

// Sanity tests for RegMask and RegMaskIterator
TEST_VM(opto, regmask_empty) {
  RegMask rm;
  ASSERT_TRUE(rm.Size() == 0);
  ASSERT_TRUE(!rm.is_NotEmpty());
  ASSERT_TRUE(!rm.is_AllStack());
  RegMaskIterator rmi(rm);
  ASSERT_FALSE(rmi.has_next());
  ASSERT_EQ(OptoReg::Bad, rmi.next());
}

TEST_VM(opto, regmask_set_all) {
  // Check that Set_All doesn't add bits outside of CHUNK_SIZE
  RegMask rm;
  rm.Set_All();
  ASSERT_TRUE(rm.Size() == RegMask::CHUNK_SIZE);
  ASSERT_TRUE(rm.is_NotEmpty());
  // Set_All sets AllStack bit
  ASSERT_TRUE(rm.is_AllStack());

  RegMaskIterator rmi(rm);
  ASSERT_TRUE(rmi.has_next());
  int count = 0;
  OptoReg::Name reg = OptoReg::Bad;
  while (rmi.has_next()) {
    reg = rmi.next();
    ASSERT_TRUE(OptoReg::is_valid(reg));
    count++;
  }
  ASSERT_EQ(OptoReg::Bad, rmi.next());
  ASSERT_TRUE(count == RegMask::CHUNK_SIZE);
}

TEST_VM(opto, regmask_clear) {
  // Check that Clear doesn't leave any stray bits
  RegMask rm;
  rm.Set_All();
  rm.Clear();
  ASSERT_TRUE(rm.Size() == 0);
  ASSERT_TRUE(!rm.is_NotEmpty());
  ASSERT_TRUE(!rm.is_AllStack());

  RegMaskIterator rmi(rm);
  ASSERT_FALSE(rmi.has_next());
  ASSERT_EQ(OptoReg::Bad, rmi.next());
}