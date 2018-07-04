/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zForwardingTable.inline.hpp"
#include "unittest.hpp"

using namespace testing;

#define CAPTURE_DELIM "\n"
#define CAPTURE1(expression) #expression << " evaluates to " << expression
#define CAPTURE2(e0, e1)                 CAPTURE1(e0) << CAPTURE_DELIM << CAPTURE1(e1)

#define CAPTURE(expression) CAPTURE1(expression)

class ZForwardingTableTest : public Test {
public:
  // Helper functions

  static bool is_power_of_2(size_t value) {
    return ::is_power_of_2((intptr_t)value);
  }

  class SequenceToFromIndex : AllStatic {
  public:
    static uintptr_t even(uint32_t sequence_number) {
      return sequence_number * 2;
    }
    static uintptr_t odd(uint32_t sequence_number) {
      return even(sequence_number) + 1;
    }
    static uintptr_t one_to_one(uint32_t sequence_number) {
      return sequence_number;
    }
  };

  // Test functions

  static void setup(ZForwardingTable& table) {
    EXPECT_PRED1(is_power_of_2, table._size) << CAPTURE(table._size);
  }

  static void find_empty(ZForwardingTable& table) {
    size_t size = table._size;
    size_t entries_to_check = size * 2;

    for (uint32_t i = 0; i < entries_to_check; i++) {
      uintptr_t from_index = SequenceToFromIndex::one_to_one(i);

      EXPECT_TRUE(table.find(from_index).is_empty()) << CAPTURE2(from_index, size);
    }

    EXPECT_TRUE(table.find(uintptr_t(-1)).is_empty()) << CAPTURE(size);
  }

  static void find_full(ZForwardingTable& table) {
    size_t size = table._size;
    size_t entries_to_populate = size;

    // Populate
    for (uint32_t i = 0; i < entries_to_populate; i++) {
      uintptr_t from_index = SequenceToFromIndex::one_to_one(i);

      ZForwardingTableCursor cursor;
      ZForwardingTableEntry entry = table.find(from_index, &cursor);
      ASSERT_TRUE(entry.is_empty()) << CAPTURE2(from_index, size);

      table.insert(from_index, from_index, &cursor);
    }

    // Verify
    for (uint32_t i = 0; i < entries_to_populate; i++) {
      uintptr_t from_index = SequenceToFromIndex::one_to_one(i);

      ZForwardingTableEntry entry = table.find(from_index);
      ASSERT_FALSE(entry.is_empty()) << CAPTURE2(from_index, size);

      ASSERT_EQ(entry.from_index(), from_index) << CAPTURE(size);
      ASSERT_EQ(entry.to_offset(), from_index) << CAPTURE(size);
    }
  }

  static void find_every_other(ZForwardingTable& table) {
    size_t size = table._size;
    size_t entries_to_populate = size / 2;

    // Populate even from indices
    for (uint32_t i = 0; i < entries_to_populate; i++) {
      uintptr_t from_index = SequenceToFromIndex::even(i);

      ZForwardingTableCursor cursor;
      ZForwardingTableEntry entry = table.find(from_index, &cursor);
      ASSERT_TRUE(entry.is_empty()) << CAPTURE2(from_index, size);

      table.insert(from_index, from_index, &cursor);
    }

    // Verify populated even indices
    for (uint32_t i = 0; i < entries_to_populate; i++) {
      uintptr_t from_index = SequenceToFromIndex::even(i);

      ZForwardingTableCursor cursor;
      ZForwardingTableEntry entry = table.find(from_index, &cursor);
      ASSERT_FALSE(entry.is_empty()) << CAPTURE2(from_index, size);

      ASSERT_EQ(entry.from_index(), from_index) << CAPTURE(size);
      ASSERT_EQ(entry.to_offset(), from_index) << CAPTURE(size);
    }

    // Verify empty odd indices
    //
    // This check could be done on a larger range of sequence numbers,
    // but currently entries_to_populate is used.
    for (uint32_t i = 0; i < entries_to_populate; i++) {
      uintptr_t from_index = SequenceToFromIndex::odd(i);

      ZForwardingTableEntry entry = table.find(from_index);

      ASSERT_TRUE(entry.is_empty()) << CAPTURE2(from_index, size);
    }
  }

  static void test(void (*function)(ZForwardingTable&), uint32_t size) {
    // Setup
    ZForwardingTable table;
    table.setup(size);
    ASSERT_FALSE(table.is_null());

    // Actual test function
    (*function)(table);

    // Teardown
    table.reset();
    ASSERT_TRUE(table.is_null());
  }

  // Run the given function with a few different input values.
  static void test(void (*function)(ZForwardingTable&)) {
    test(function, 1);
    test(function, 2);
    test(function, 3);
    test(function, 4);
    test(function, 7);
    test(function, 8);
    test(function, 1023);
    test(function, 1024);
    test(function, 1025);
  }
};

TEST_F(ZForwardingTableTest, setup) {
  test(&ZForwardingTableTest::setup);
}

TEST_F(ZForwardingTableTest, find_empty) {
  test(&ZForwardingTableTest::find_empty);
}

TEST_F(ZForwardingTableTest, find_full) {
  test(&ZForwardingTableTest::find_full);
}

TEST_F(ZForwardingTableTest, find_every_other) {
  test(&ZForwardingTableTest::find_every_other);
}
