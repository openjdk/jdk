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
#include "unittest.hpp"
#include "memory/allocation.hpp"
#include "memory/resourceArea.inline.hpp"
#include "runtime/thread.hpp"

TEST(absTest, sanity) {
  // Simple integer cases
  EXPECT_EQ(0, ABS(0));
  EXPECT_EQ(1, ABS(1));
  EXPECT_EQ(1, ABS(-1));

  // Simple floating point cases, should be exactly representable
  EXPECT_EQ(0.0f, ABS(0.0f));
  EXPECT_EQ(1.0f, ABS(1.0f));
  EXPECT_EQ(1.0f, ABS(-1.0f));

  EXPECT_EQ(0.0, ABS(0.0));
  EXPECT_EQ(1.0, ABS(1.0));
  EXPECT_EQ(1.0, ABS(-1.0));

  // Upper bounds for unsigned integers
  EXPECT_EQ(max_jubyte,  ABS(max_jubyte));
  EXPECT_EQ(max_jushort, ABS(max_jushort));
  EXPECT_EQ(max_juint,   ABS(max_juint));
  EXPECT_EQ(max_julong,  ABS(max_julong));

  // Upper bounds for signed integers
  EXPECT_EQ(max_jbyte,  ABS(max_jbyte));
  EXPECT_EQ(max_jshort, ABS(max_jshort));
  EXPECT_EQ(max_jint,   ABS(max_jint));
  EXPECT_EQ(max_jlong,  ABS(max_jlong));

  // Lower valid bounds for signed integers
  EXPECT_EQ(max_jbyte,  ABS(min_jbyte + 1));
  EXPECT_EQ(max_jshort, ABS(min_jshort + 1));
  EXPECT_EQ(max_jint,   ABS(min_jint + 1));
  EXPECT_EQ(max_jlong,  ABS(min_jlong + 1));

  // Lower bounds for signed integers after explicit FP cast
  EXPECT_TRUE(ABS((float)min_jbyte)  > 0);
  EXPECT_TRUE(ABS((float)min_jshort) > 0);
  EXPECT_TRUE(ABS((float)min_jint)   > 0);
  EXPECT_TRUE(ABS((float)min_jlong)  > 0);
}

// Now check what happens when we feed invalid arguments.

#ifndef ASSERT

// In release builds, ABS would return incorrect values.

TEST(absTest, release_sanity) {
  EXPECT_EQ(min_jbyte,  ABS(min_jbyte));
  EXPECT_EQ(min_jshort, ABS(min_jshort));
  EXPECT_EQ(min_jint,   ABS(min_jint));
  EXPECT_EQ(min_jlong,  ABS(min_jlong));
}

#else

// In debug builds, ABS would assert.

TEST_VM_ASSERT_MSG(absTest, debug_sanity_min_jbyte,
  "Error: ABS: argument should not allow overflow") {

  jbyte r = ABS(min_jbyte); // should fail
  EXPECT_TRUE(r > 0); // should not be normally reachable
}

TEST_VM_ASSERT_MSG(absTest, debug_sanity_min_jshort,
  "Error: ABS: argument should not allow overflow") {

  jshort r = ABS(min_jshort); // should fail
  EXPECT_TRUE(r > 0); // should not be normally reachable
}

TEST_VM_ASSERT_MSG(absTest, debug_sanity_min_jint,
  "Error: ABS: argument should not allow overflow") {

  jint r = ABS(min_jint); // should fail
  EXPECT_TRUE(r > 0); // should not be normally reachable
}

TEST_VM_ASSERT_MSG(absTest, debug_sanity_min_jlong,
  "Error: ABS: argument should not allow overflow") {

  jlong r = ABS(min_jlong); // should fail
  EXPECT_TRUE(r > 0); // should not be normally reachable
}

#endif
