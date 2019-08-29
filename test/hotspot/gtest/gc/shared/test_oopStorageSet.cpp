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

// GTEST assertions may introduce ODR-uses.  Dodge them.
template<typename T> static T no_odr(T x) { return x; }

static void fill_strong(OopStorage** storages, size_t size) {
  ASSERT_EQ(size, no_odr(OopStorageSet::strong_count));
  STATIC_ASSERT(2 == OopStorageSet::strong_count);
  storages[0] = OopStorageSet::jni_global();
  storages[1] = OopStorageSet::vm_global();
}

static void fill_weak(OopStorage** storages, size_t size) {
  ASSERT_EQ(size, no_odr(OopStorageSet::weak_count));
  STATIC_ASSERT(4 == OopStorageSet::weak_count);
  storages[0] = OopStorageSet::jni_weak();
  storages[1] = OopStorageSet::vm_weak();
  storages[2] = OopStorageSet::string_table_weak();
  storages[3] = OopStorageSet::resolved_method_table_weak();
}

static void fill_all(OopStorage** storages, size_t size) {
  ASSERT_EQ(size, no_odr(OopStorageSet::all_count));
  const uint strong_count = OopStorageSet::strong_count;
  fill_strong(storages, strong_count);
  fill_weak(storages + strong_count, size - strong_count);
}

// Returns index of s in storages, or size if not found.
static size_t find_storage(OopStorage* s, OopStorage** storages, size_t size) {
  for (uint i = 0; i < size; ++i) {
    if (s == storages[i]) {
      return i;
    }
  }
  return size;
}

static void check_iterator(OopStorageSet::Iterator it,
                           OopStorage** storages,
                           size_t size) {
  OopStorageSet::Iterator start = it;
  ASSERT_EQ(start, it);
  for ( ; !it.is_end(); ++it) {
    size_t index = find_storage(*it, storages, size);
    ASSERT_LT(index, size);
    storages[index] = NULL;
  }
  ASSERT_NE(start, it);
  const OopStorage* null_storage = NULL;
  for (uint i = 0; i < size; ++i) {
    ASSERT_EQ(null_storage, storages[i]);
  }
}

static void test_iterator(uint count,
                          OopStorageSet::Iterator iterator,
                          void (*fill)(OopStorage**, size_t)) {
  OopStorage** storages = NEW_C_HEAP_ARRAY(OopStorage*, count, mtGC);
  fill(storages, count);
  check_iterator(iterator, storages, count);
  FREE_C_HEAP_ARRAY(OopStorage*, storages);
}

#define TEST_ITERATOR(kind)                                             \
  TEST_VM(OopStorageSetTest, PASTE_TOKENS(kind, _iterator)) {           \
    test_iterator(OopStorageSet::PASTE_TOKENS(kind, _count),            \
                  OopStorageSet::PASTE_TOKENS(kind, _iterator)(),       \
                  &PASTE_TOKENS(fill_, kind));                          \
  }

TEST_ITERATOR(strong);
TEST_ITERATOR(weak)
TEST_ITERATOR(all)
