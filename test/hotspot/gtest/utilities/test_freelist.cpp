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
#include "utilities/freeList.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

#include "unittest.hpp"
#include "testutils.hpp"

#define ASSERT_LIST_COUNT(list, n)            \
  if (list.counting()) {                      \
    ASSERT_EQ(list.count(), (uintx)n);        \
  }

#define ASSERT_LIST_PEAK(list, n)             \
  if (list.counting()) {                      \
    ASSERT_EQ(list.peak_count(), (uintx)n);   \
  }

#define ASSERT_LIST_EMPTY(list)    \
  ASSERT_TRUE(list.empty());       \
  ASSERT_LIST_COUNT(list, 0)

template <class T>
static void prepend_all_with_checks(FreeList<T>& list, T* elems, int num, int expected_start_count) {
  ASSERT_LIST_COUNT(list, expected_start_count);
  if (expected_start_count == 0) {
    ASSERT_TRUE(list.empty());
  }
  for (int i = 0; i < num; i ++) {
    list.prepend(elems + i);
    ASSERT_LIST_COUNT(list, expected_start_count + i + 1);
    ASSERT_FALSE(list.empty());
  }
}

template <class T>
static void safely_print_list(FreeList<T>& list) {
  char tmp[1024];
  stringStream ss(tmp, sizeof(tmp));
  list.print_on(&ss, true);
  printf("%s\n", tmp);
}


#define NUM_ELEMS 30

template <class T>
static void test_empty_list() {
  FreeList<T> list;
  ASSERT_LIST_EMPTY(list);
  DEBUG_ONLY(list.verify(true);)
}

template <class T>
static void prepare_new_list_with_checks(FreeList<T>& list, T* elems) {
  prepend_all_with_checks(list, elems, NUM_ELEMS, 0);
  ASSERT_LIST_COUNT(list, NUM_ELEMS);
  ASSERT_LIST_PEAK(list, NUM_ELEMS);
  DEBUG_ONLY(list.verify(true);)
}

template <class T>
static void test_single_prepend() {

  FreeList<T> list;
  ASSERT_LIST_EMPTY(list);
  DEBUG_ONLY(list.verify(true);)

  T t[NUM_ELEMS];
  prepare_new_list_with_checks<T>(list, t);

  for (int i = NUM_ELEMS - 1; i >= 0; i --) {
    T* p = list.take_top();
    ASSERT_EQ(p, t + i);
    ASSERT_LIST_COUNT(list, i);
  }
  ASSERT_LIST_EMPTY(list);
  ASSERT_LIST_PEAK(list, NUM_ELEMS);
  DEBUG_ONLY(list.verify(true);)
}

template <class T, int max_expected>
struct TestIterator : public FreeList<T>::Closure {

  const T* t[max_expected];
  const int _stop_after;
  int _found;

  TestIterator(int stop_after = max_expected * 2)
    : _stop_after(stop_after),
      _found(0)
  {
    for (int i = 0; i < max_expected; i ++) {
      t[i] = NULL;
    }
  }

  bool do_it(const T* p) override {
    if (_found == max_expected) {
     return false;
    }
    t[_found] = p;
    _found ++;
    return _found < _stop_after;
  }

};

template <class T>
static void test_iteration(bool premature_stop) {

  FreeList<T> list;
  ASSERT_LIST_EMPTY(list);
  DEBUG_ONLY(list.verify(true);)

  T t[NUM_ELEMS];
  prepare_new_list_with_checks<T>(list, t);

  const int stop_after = premature_stop ? 3 : INT_MAX;
  const int expected_stop_at = premature_stop ? 3 : NUM_ELEMS;
  TestIterator<T, NUM_ELEMS> it(stop_after);

  ASSERT_EQ(list.iterate(it), (uintx)expected_stop_at);
  ASSERT_EQ(it._found, expected_stop_at);
  for (int i = 0; i < NUM_ELEMS; i++) {
//safely_print_list<T>(list);
    if (i < expected_stop_at) {
      ASSERT_EQ(it.t[i], t + (NUM_ELEMS - i - 1)) << i; // we prepended, so FIFO
    } else {
      ASSERT_NULL(it.t[i]) << i;
    }
  }
}

template <class T>
static void test_iteration_full()         { test_iteration<T>(false); }

template <class T>
static void test_iteration_interrupted()  { test_iteration<T>(true); }

template <class T>
static void test_reset() {
  FreeList<T> list;

  T t[NUM_ELEMS];
  prepare_new_list_with_checks<T>(list, t);

  list.reset();
  ASSERT_LIST_EMPTY(list);
  ASSERT_LIST_PEAK(list, 0); // Reset also should reset peak
  DEBUG_ONLY(list.verify(true);)
}

template <class T>
static void test_take_over() {
  FreeList<T> list1;
  ASSERT_LIST_EMPTY(list1);

  FreeList<T> list2;
  T t[NUM_ELEMS];
  prepare_new_list_with_checks<T>(list2, t);

  list1.take_elements(list2);
  ASSERT_LIST_EMPTY(list2);
  ASSERT_LIST_COUNT(list1, NUM_ELEMS);
  ASSERT_LIST_PEAK(list1, NUM_ELEMS);
}

template <class T>
static void test_prepend_list(bool empty_receiver, bool empty_donor) {
  FreeList<T> list1;
  FreeList<T> list2;

  T t1[NUM_ELEMS];
  uintx num1 = 0;
  T t2[NUM_ELEMS];
  uintx num2 = 0;

  if (!empty_receiver) {
    prepare_new_list_with_checks<T>(list1, t1);
    num1 = NUM_ELEMS;
  }

  if (!empty_donor) {
    prepare_new_list_with_checks<T>(list2, t2);
    num2 = NUM_ELEMS;
  }

  list1.prepend_list(list2);
  ASSERT_LIST_COUNT(list1, num1 + num2);
  ASSERT_LIST_PEAK(list1, num1 + num2);
  DEBUG_ONLY(list1.verify(true);)

  ASSERT_LIST_EMPTY(list2);
  DEBUG_ONLY(list2.verify(true);)

  // Prepends prepends the list2 elems in front of list1
  // and since prepare_new_list_with_checks also prepends the individual
  // elements, we expect elements to be in inverse address order
  for (int i = num2 - 1; i >= 0; i --) {
    T* p = list1.take_top();
    ASSERT_EQ(p, t2 + i);
    ASSERT_LIST_COUNT(list1, num1 + i);
  }

  for (int i = num1 - 1; i >= 0; i --) {
    T* p = list1.take_top();
    ASSERT_EQ(p, t1 + i);
    ASSERT_LIST_COUNT(list1, i);
  }

  ASSERT_LIST_COUNT(list1, 0);
  ASSERT_LIST_PEAK(list1, num1 + num2);
  DEBUG_ONLY(list1.verify(true);)
}

template <class T> static void test_prepend_list_both_empty()     { test_prepend_list<T>(true, true); }
template <class T> static void test_prepend_list_both_nonempty()  { test_prepend_list<T>(false, false); }
template <class T> static void test_prepend_list_receiver_empty() { test_prepend_list<T>(true, false); }
template <class T> static void test_prepend_list_donor_empty()    { test_prepend_list<T>(false, true); }

#define DO_ONE_TEST(T, testname)      \
TEST(FreeList, test_##testname##_##T) \
{                                     \
  testname<T>();                      \
}

#define DO_ALL_TESTS(T)                               \
  DO_ONE_TEST(T, test_empty_list)                     \
  DO_ONE_TEST(T, test_single_prepend)                 \
  DO_ONE_TEST(T, test_reset)                          \
  DO_ONE_TEST(T, test_prepend_list_both_empty)        \
  DO_ONE_TEST(T, test_prepend_list_both_nonempty)     \
  DO_ONE_TEST(T, test_prepend_list_receiver_empty)    \
  DO_ONE_TEST(T, test_prepend_list_donor_empty)       \
  DO_ONE_TEST(T, test_iteration_full)                 \
  DO_ONE_TEST(T, test_iteration_interrupted)          \

DO_ALL_TESTS(uint64_t);

struct s3 { void* p[3]; };
DO_ALL_TESTS(s3);

struct s216 { char p[216]; };
DO_ALL_TESTS(s216);
