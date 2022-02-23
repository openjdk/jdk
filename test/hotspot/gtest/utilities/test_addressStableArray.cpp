/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/os.hpp"
#include "utilities/addressStableArray.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

#include "unittest.hpp"
#include "testutils.hpp"

template <class T>
static void test_fill_empty_repeat(uintx max_size, uintx initialsize) {
  AddressStableHeap<T> a1(max_size, initialsize);
  T** elems = NEW_C_HEAP_ARRAY(T*, max_size, mtTest);
  memset(elems, 0, sizeof(T*) * max_size);
  if (initialsize == 0) {
    ASSERT_EQ(a1.committed_bytes(), (size_t)0);
  }
  DEBUG_ONLY(a1.verify();)
  const size_t fully_committed_size = align_up(sizeof(T) * max_size, os::vm_page_size());
  for (int cycle = 0; cycle < 3; cycle ++) {
    // (Re)fill
    for (uintx i = 0; i < max_size; i ++) {
      T* p = a1.allocate();
      if (i < max_size) {
        ASSERT_NE(p, (T*)NULL);
        elems[i] = p;
      }
    }
    // We should be right at the limit now
    ASSERT_EQ(a1.allocate(), (T*)NULL);
    ASSERT_EQ(a1.committed_bytes(), fully_committed_size);
    DEBUG_ONLY(a1.verify(true);)
    // Empty out
    for (uintx i = 0; i < max_size; i ++) {
      a1.deallocate(elems[i]);
    }
    ASSERT_EQ(a1.committed_bytes(), fully_committed_size);
    DEBUG_ONLY(a1.verify();)
  }
  FREE_C_HEAP_ARRAY(T, elems);
}

template <class T>
static void test_fill_empty_randomly(uintx max_size, uintx initialsize) {
  AddressStableHeap<T> a1(max_size, initialsize);
  T** elems = NEW_C_HEAP_ARRAY(T*, max_size, mtTest);
  memset(elems, 0, sizeof(T*) * max_size);
  DEBUG_ONLY(a1.verify();)
  for (uintx iter = 0; iter < MIN2(max_size * 4, (uintx)1024); iter ++) {
    const int idx = os::random() % max_size;
    if (elems[idx] == NULL) {
      T* p = a1.allocate();
      ASSERT_NE(p, (T*)NULL);
      elems[idx] = p;
    } else {
      a1.deallocate(elems[idx]);
      elems[idx] = NULL;
    }
    if ((iter % 256) == 0) {
      DEBUG_ONLY(a1.verify(iter % 1024 == 0);)
    }
  }
  DEBUG_ONLY(a1.verify(true);)
  // Now allocate the full complement, just what we think is the container fill grade is right
  for (uintx i = 0; i < max_size; i++) {
    if (elems[i] == 0) {
      T* p = a1.allocate();
      ASSERT_NE(p, (T*)NULL);
      elems[i] = p;
    }
  }
  // We should be right at the limit now
  ASSERT_EQ(a1.allocate(), (T*)NULL);
  FREE_C_HEAP_ARRAY(T, elems);
}

template <class T>
static void run_all_tests(uintx max_capacity, uintx initial_capacity) {
  test_fill_empty_repeat<T>(max_capacity, initial_capacity);
  test_fill_empty_randomly<T>(max_capacity, initial_capacity);
}

template <class T>
static void run_all_tests() {
  uintx max_max = (10 * M) / sizeof(T);           // don't use more than 10M in total
  max_max = MIN2(max_max, (uintx)100000);         // and limit to 100000 entries
  run_all_tests<T>(1, 0);
  run_all_tests<T>(10, 0);
  run_all_tests<T>(max_max, 0);
  run_all_tests<T>(max_max, max_max/2);
}

#define test_stable_array(T) \
TEST_VM(AddressStableArray, fill_empty_repeat_##T) \
{ \
	run_all_tests<T>(); \
}

test_stable_array(uint64_t);

struct s3 { void* p[3]; };
test_stable_array(s3);

struct s216 { char p[216]; };
test_stable_array(s216);

// almost, but not quite, a page
struct almost_one_page { char m[4096 - 8]; };
test_stable_array(almost_one_page);
