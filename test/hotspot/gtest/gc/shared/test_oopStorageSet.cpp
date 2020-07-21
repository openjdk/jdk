/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/oopStorage.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "memory/allocation.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "unittest.hpp"

class OopStorageSetTest : public ::testing::Test {
protected:
  // Returns index of s in storages, or size if not found.
  template <uint count>
  static size_t find_storage(OopStorage* s, OopStorage* storages[count]) {
    for (uint i = 0; i < count; ++i) {
      if (s == storages[i]) {
        return i;
      }
    }
    return count;
  }

  template <uint count>
  static void check_iterator(OopStorageSet::Iterator it,
      OopStorage* storages[count]) {
    OopStorageSet::Iterator start = it;
    ASSERT_EQ(start, it);
    for ( ; !it.is_end(); ++it) {
      size_t index = find_storage<count>(*it, storages);
      ASSERT_LT(index, count);
      storages[index] = NULL;
    }
    ASSERT_NE(start, it);
    const OopStorage* null_storage = NULL;
    for (uint i = 0; i < count; ++i) {
      ASSERT_EQ(null_storage, storages[i]);
    }
  }

  template <uint count>
  static void test_iterator(OopStorageSet::Iterator iterator,
      void (*fill)(OopStorage*[count])) {
    OopStorage* storages[count];
    fill(storages);
    check_iterator<count>(iterator, storages);
  }

  static void test_strong_iterator() {
    test_iterator<OopStorageSet::strong_count>(
        OopStorageSet::strong_iterator(),
        &OopStorageSet::fill_strong);

  }
  static void test_weak_iterator() {
    test_iterator<OopStorageSet::weak_count>(
        OopStorageSet::weak_iterator(),
        &OopStorageSet::fill_weak);

  }
  static void test_all_iterator() {
    test_iterator<OopStorageSet::all_count>(
        OopStorageSet::all_iterator(),
        &OopStorageSet::fill_all);
  }
};

TEST_VM_F(OopStorageSetTest, iterators) {
  test_strong_iterator();
  test_weak_iterator();
  test_all_iterator();
}
