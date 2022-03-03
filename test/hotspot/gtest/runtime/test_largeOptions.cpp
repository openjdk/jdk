/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "compiler/compiler_globals.hpp"
#include "runtime/arguments.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/globals.hpp"
#include "unittest.hpp"

class LargeOptionsTest : public ::testing::Test {
public:
  static bool test_option_value(const char* option, intx value) {
    char buffer[100];
    UnlockDiagnosticVMOptions = true;
    os::snprintf(buffer, 100, "%s=" INTX_FORMAT, option, value);
    return Arguments::parse_argument(buffer, JVMFlagOrigin::COMMAND_LINE);
  }

  static bool test_option_value(const char* option) {
    UnlockDiagnosticVMOptions = true;
    return Arguments::parse_argument(option, JVMFlagOrigin::COMMAND_LINE);
  }
};


// CompilerDirectivesLimit is a diagnostic int option.
TEST_VM(LARGE_OPTION, large_ints) {
  for (intx x = max_jint - 1; x <= (intx)max_jint + 1; x++) {
    bool result = LargeOptionsTest::test_option_value("CompilerDirectivesLimit", x);
    if (x > max_jint) {
      ASSERT_FALSE(result);
    } else {
      ASSERT_TRUE(result);
      ASSERT_EQ(CompilerDirectivesLimit, x);
    }
  }
}


TEST_VM(LARGE_OPTION, small_ints) {
  for (intx x = min_jint + 1; x >= (intx)min_jint - 1; x--) {
    bool result = LargeOptionsTest::test_option_value("CompilerDirectivesLimit", x);
    if (x < min_jint) {
      ASSERT_FALSE(result);
    } else {
      ASSERT_TRUE(result);
      ASSERT_EQ(CompilerDirectivesLimit, x);
    }
  }
}


TEST_VM(LARGE_OPTION, large_int_overflow) { // Test 0x100000000
  ASSERT_FALSE(LargeOptionsTest::test_option_value("CompilerDirectivesLimit", 4294967296));
}


// HandshakeTimeout is a diagnostic uint option.
TEST_VM(LARGE_OPTION, large_uints) {
  for (uintx x = max_juint - 1; x <= (uintx)max_juint + 1; x++) {
    bool result = LargeOptionsTest::test_option_value("HandshakeTimeout", x);
    if (x <= max_juint) {
      ASSERT_TRUE(result);
      ASSERT_EQ(HandshakeTimeout, x);
    } else {
      ASSERT_FALSE(result);
    }
  }
}


// MaxJNILocalCapacity is an intx option.
TEST_VM(LARGE_OPTION, large_intxs) {
  // max_intx + 1 equals min_intx!
  for (julong x = max_intx - 1; x <= (julong)max_intx + 1; x++) {
    ASSERT_TRUE(LargeOptionsTest::test_option_value("MaxJNILocalCapacity", x));
    ASSERT_EQ((julong)MaxJNILocalCapacity, x);
  }
}


TEST_VM(LARGE_OPTION, small_intxs) {
  ASSERT_TRUE(LargeOptionsTest::test_option_value("MaxJNILocalCapacity", min_intx + 1));
  ASSERT_EQ(MaxJNILocalCapacity, -9223372036854775807);
  ASSERT_TRUE(LargeOptionsTest::test_option_value("MaxJNILocalCapacity", min_intx));
  ASSERT_EQ(MaxJNILocalCapacity, min_intx);
  // Test value that's less than min_intx (-0x8000000000000001).
  ASSERT_FALSE(LargeOptionsTest::test_option_value("MaxJNILocalCapacity=-9223372036854775809"));
}
