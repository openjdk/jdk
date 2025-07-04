/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/vmClasses.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/jmethodIDTable.hpp"
#include "oops/method.hpp"
#include "unittest.hpp"
#include "utilities/growableArray.hpp"

// Tests for creating and deleting jmethodIDs
TEST_VM(jmethodIDTable, test_jmethod_ids) {
  InstanceKlass* klass = vmClasses::ClassLoader_klass();
  Array<Method*>* methods = klass->methods();
  int length = methods->length();
  // How many entries are in the jmethodID table?
  uint64_t initial_entries = JmethodIDTable::get_entry_count();
  ResourceMark rm;
  GrowableArray<uint64_t> ints(10);
  for (int i = 0; i < length; i++) {
    Method* m = methods->at(i);
    jmethodID mid = m->jmethod_id();
    ints.push((uint64_t)mid);
  }
  uint64_t entries_now = JmethodIDTable::get_entry_count();
  ASSERT_TRUE(entries_now == initial_entries + length) << "should have more entries " << entries_now << " " << initial_entries + length;

  // Test that new entries aren't created, and the values are the same.
  for (int i = 0; i < length; i++) {
    Method* m = methods->at(i);
    jmethodID mid = m->jmethod_id();
    ASSERT_TRUE(ints.at(i) == (uint64_t)mid) << "should be the same";
  }
  // should have the same number of entries
  entries_now = JmethodIDTable::get_entry_count();
  ASSERT_TRUE(entries_now == initial_entries + length) << "should have more entries " << entries_now << " " << initial_entries + length;
}

