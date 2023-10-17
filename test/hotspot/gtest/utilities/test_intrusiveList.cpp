/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/intrusiveList.hpp"
#include "utilities/macros.hpp"
#include <type_traits>
#include "unittest.hpp"

// Note: Hotspot gtest integration doesn't yet support typed tests, so
// a lot of the tests of the different iterator types are done by hand.

using Entry = IntrusiveListEntry;

struct TestIntrusiveListValue : public CHeapObj<mtInternal> {
  size_t _value;

  // Note: Entry members need to be public, to work around VS2013 bug.

  Entry _entry1;                // Entry for first list.

  // Used to prove we can have an object in two different kinds of
  // list.  We only use _entry1 for most other tests.
  Entry _entry2;                // Entry for second list.

  TestIntrusiveListValue(size_t value) : _value(value) { }

  NONCOPYABLE(TestIntrusiveListValue);

  using Value = TestIntrusiveListValue; // convenience

  size_t value() const { return _value; }
  static const Entry& entry1(const Value& v) { return v._entry1; }
  static const Entry& entry2(const Value& v) { return v._entry2; }
  bool is_attached1() const { return entry1(*this).is_attached(); }
  bool is_attached2() const { return entry2(*this).is_attached(); }
  Value* This() { return this; }
  const Value* This() const { return this; }
};

// Convenience type aliases.
using Value = TestIntrusiveListValue;

using List1 = IntrusiveList<Value, &Value::entry1>;
using List2 = IntrusiveList<Value, &Value::entry2>;

using CList1 = IntrusiveList<const Value, &Value::entry1>;
using CList2 = IntrusiveList<const Value, &Value::entry2>;

////////////////////
// Some preliminary tests.

struct IntrusiveListImpl::TestSupport {
  // Verify expected iterator conversions.
  using L1Iterator = typename List1::iterator;
  using L1CIterator = typename List1::const_iterator;
  using C1Iterator = typename CList1::iterator;
  using C1CIterator = typename CList1::const_iterator;
  static_assert(std::is_convertible<L1Iterator, L1Iterator>::value,
                "not convertible: List1::iterator -> List1::iterator");
  static_assert(std::is_convertible<L1CIterator, L1CIterator>::value,
                "not convertible: List1::const_iterator -> List1::const_iterator");
  static_assert(std::is_convertible<L1CIterator, L1CIterator>::value,
                "not convertible: List1::const_iterator -> List1::const_iterator");
  static_assert(std::is_convertible<L1Iterator, L1CIterator>::value,
                "not convertible: List1::iterator -> List1::const_iterator");
  static_assert(!std::is_convertible<L1CIterator, L1Iterator>::value,
                "convertible: List1::iterator -> List1::const_iterator");

  using L2Iterator = typename List2::iterator;
  static_assert(!std::is_convertible<L1Iterator, L2Iterator>::value,
                "convertible: List1::iterator -> List2::iterator");
  static_assert(!std::is_convertible<L2Iterator, L1Iterator>::value,
                "convertible: List2::iterator -> List1::iterator");

  // Verify can_splice_from for pairwise combinations of const/non-const value type.
  static_assert(List1::can_splice_from<List1>(), "cannot splice List1 -> List1");
  static_assert(CList1::can_splice_from<CList1>(), "cannot splice CList1 -> CList1");
  static_assert(!List1::can_splice_from<CList1>(), "can splice CList1 -> List1");
  static_assert(CList1::can_splice_from<List1>(), "cannot splice List1 -> CList1");

  // Verify CanSplice is false for different list entries.
  static_assert(!List2::can_splice_from<List1>(), "can splice List1 -> List2");

};

////////////////////
// Test fixtures.

class IntrusiveListTestWithValues : public ::testing::Test {
public:
  IntrusiveListTestWithValues() {
    for (size_t i = 0; i < nvalues; ++i) {
      values[i] = new Value(i);
    }
  }

  ~IntrusiveListTestWithValues() {
    for (size_t i = 0; i < nvalues; ++i) {
      delete values[i];
    }
  }

  static const size_t nvalues = 10;
  Value* values[nvalues];
};

const size_t IntrusiveListTestWithValues::nvalues;

class IntrusiveListTestWithList1 : public IntrusiveListTestWithValues {
public:
  IntrusiveListTestWithList1() {
    fill_list();
  }

  ~IntrusiveListTestWithList1() {
    list1.clear();
  }

  List1 list1;

  // Add all values[] to list1, in the same order in values[] and list.
  void fill_list() {
    for (size_t i = 0; i < nvalues; ++i) {
      list1.push_back(*values[i]);
    }
  }
};

class IntrusiveListTestWithCList1 : public IntrusiveListTestWithValues {
public:
  IntrusiveListTestWithCList1() {
    fill_list();
  }

  ~IntrusiveListTestWithCList1() {
    list1.clear();
  }

  CList1 list1;

  // Add all values[] to list1, in the same order in values[] and list.
  void fill_list() {
    for (size_t i = 0; i < nvalues; ++i) {
      list1.push_back(*values[i]);
    }
  }
};

class IntrusiveListTestWithDisposal : public IntrusiveListTestWithList1 {
public:
  IntrusiveListTestWithDisposal() : ndisposed(0), disposed() { }

  size_t ndisposed;
  const Value* disposed[nvalues];

  class CollectingDisposer {
  public:
    CollectingDisposer(IntrusiveListTestWithDisposal* test) : _test(test) { }

    void operator()(const Value* value) const {
      _test->disposed[_test->ndisposed++] = value;
    }

  private:
    IntrusiveListTestWithDisposal* _test;
  };
};

////////////////////
// Helper functions

// Doesn't distinguish between reference and non-reference types.
template<typename Expected, typename T>
static bool is_expected_type(T) {
  return std::is_same<Expected, T>::value;
}

// This lets us distinguish between non-const reference and const
// reference or value.
template<typename Expected, typename T>
static bool is_expected_ref_type(T&) {
  return std::is_same<Expected, T&>::value;
};

template<typename It>
static It step_iterator(It it, ptrdiff_t n) {
  if (n < 0) {
    for (ptrdiff_t i = 0; i > n; --i) {
      --it;
    }
  } else {
    for (ptrdiff_t i = 0; i < n; ++i) {
      ++it;
    }
  }
  return it;
}

template<typename List>
static typename List::reference list_elt(List& list, size_t n) {
  return *step_iterator(list.begin(), n);
}

////////////////////
// push_front(), pop_front(), length(), empty()
// front(), back()

TEST_F(IntrusiveListTestWithValues, push_front) {
  List1 list1;
  for (size_t i = 0; i < nvalues; ++i) {
    EXPECT_FALSE(values[i]->is_attached1());
    EXPECT_FALSE(values[i]->is_attached2());
    list1.push_front(*values[i]);
    EXPECT_TRUE(values[i]->is_attached1());
    EXPECT_FALSE(values[i]->is_attached2());
    EXPECT_FALSE(list1.empty());
    EXPECT_EQ(i + 1, list1.length());
    EXPECT_EQ(values[i]->value(), list1.front().value());
    EXPECT_EQ(values[0]->value(), list1.back().value());
  }
  {
    size_t i = nvalues;
    for (const Value& v : list1) {
      EXPECT_EQ(--i, v.value());
    }
  }
  list1.clear();
}

// Basic test of using list with const elements.
TEST_F(IntrusiveListTestWithValues, push_front_const) {
  CList1 list1;

  // Verify we can add a const object.  This doesn't compile for List1.
  const Value& v0 = *values[0];
  list1.push_front(v0);
  list1.clear();

  for (size_t i = 0; i < nvalues; ++i) {
    list1.push_front(*values[i]);
    EXPECT_FALSE(list1.empty());
    EXPECT_EQ(i + 1, list1.length());
    EXPECT_EQ(values[i]->value(), list1.front().value());
    EXPECT_EQ(values[0]->value(), list1.back().value());
  }
  {
    size_t i = nvalues;
    for (const Value& v : list1) {
      EXPECT_EQ(--i, v.value());
    }
  }
  list1.clear();
}

TEST_F(IntrusiveListTestWithValues, push_back) {
  List2 list2;
  for (size_t i = 0; i < nvalues; ++i) {
    list2.push_back(*values[i]);
    EXPECT_FALSE(list2.empty());
    EXPECT_EQ(i + 1, list2.length());
    EXPECT_EQ(values[i]->value(), list2.back().value());
    EXPECT_EQ(values[0]->value(), list2.front().value());
  }
  {
    size_t i = 0;
    for (const Value& v : list2) {
      EXPECT_EQ(i++, v.value());
    }
  }
  list2.clear();
}

TEST_F(IntrusiveListTestWithValues, push_back_const) {
  CList2 list2;

  // Verify we can add a const object.  This doesn't compile for List1.
  const Value& v0 = *values[0];
  list2.push_back(v0);
  list2.clear();

  for (size_t i = 0; i < nvalues; ++i) {
    list2.push_back(*values[i]);
    EXPECT_FALSE(list2.empty());
    EXPECT_EQ(i + 1, list2.length());
    EXPECT_EQ(values[i]->value(), list2.back().value());
    EXPECT_EQ(values[0]->value(), list2.front().value());
  }
  {
    size_t i = 0;
    for (const Value& v : list2) {
      EXPECT_EQ(i++, v.value());
    }
  }
  list2.clear();
}

////////////////////
// Verify we can construct a singular iterator of each type.

template<typename T> static void ignore(const T&) { }

template<typename It>
static void construct_singular_iterator() {
  It it;
  // There's not much we can do with a singular iterator to test further.
  ignore(it);
}

TEST(IntrusiveListBasics, construct_singular_iterators) {
  construct_singular_iterator<List1::iterator>();
  construct_singular_iterator<List1::const_iterator>();
  construct_singular_iterator<List1::reverse_iterator>();
  construct_singular_iterator<List1::const_reverse_iterator>();
}

// normal constructor and destructor are tested in the normal course
// of testing other things.

////////////////////
// copy construction

template<typename To, typename It>
static void test_copy_constructor2(const It& it) {
  To copy(it);
  EXPECT_EQ(it, copy);
  EXPECT_EQ(it->This(), copy->This());
  ++copy;
  EXPECT_NE(it, copy);
}

template<typename It>
static void test_copy_constructor(const It& it) {
  test_copy_constructor2<It>(it);
}

TEST_F(IntrusiveListTestWithList1, copy_construct) {
  test_copy_constructor(list1.begin());
  test_copy_constructor(list1.cbegin());
  test_copy_constructor(list1.rbegin());
  test_copy_constructor(list1.crbegin());
}

////////////////////
// copy assign

template<typename To, typename It>
static void test_copy_assign2(const It& it) {
  It tmp(it);
  EXPECT_EQ(it, tmp);

  To copy;
  // Can't compare against singular copy.
  copy = ++tmp;
  EXPECT_NE(it, copy);
  EXPECT_EQ(tmp, copy);
  EXPECT_EQ(tmp->This(), copy->This());
}

template<typename It>
static void test_copy_assign(const It& it) {
  test_copy_assign2<It>(it);
}

TEST_F(IntrusiveListTestWithList1, copy_assign) {
  test_copy_assign(list1.begin());
  test_copy_assign(list1.cbegin());
  test_copy_assign(list1.rbegin());
  test_copy_assign(list1.crbegin());
}

////////////////////
// copy conversion
// conversion assign

TEST_F(IntrusiveListTestWithList1, copy_conversion) {
  test_copy_constructor2<List1::const_iterator>(list1.begin());
  test_copy_constructor2<List1::const_reverse_iterator>(list1.rbegin());
}

TEST_F(IntrusiveListTestWithList1, conversion_assign) {
  test_copy_assign2<List1::const_iterator>(list1.begin());
  test_copy_assign2<List1::const_reverse_iterator>(list1.rbegin());
}

////////////////////
// operator*
// operator->

TEST_F(IntrusiveListTestWithList1, reference_type) {
  EXPECT_TRUE(is_expected_ref_type<List1::reference>(*list1.begin()));
  EXPECT_TRUE(is_expected_ref_type<List1::const_reference>(*list1.cbegin()));
  EXPECT_TRUE(is_expected_ref_type<List1::reference>(*list1.rbegin()));
  EXPECT_TRUE(is_expected_ref_type<List1::const_reference>(*list1.crbegin()));
}

TEST_F(IntrusiveListTestWithList1, pointer_type) {
  EXPECT_TRUE(is_expected_type<List1::pointer>(list1.begin()->This()));
  EXPECT_TRUE(is_expected_type<List1::const_pointer>(list1.cbegin()->This()));
  EXPECT_TRUE(is_expected_type<List1::pointer>(list1.rbegin()->This()));
  EXPECT_TRUE(is_expected_type<List1::const_pointer>(list1.cbegin()->This()));
}

TEST_F(IntrusiveListTestWithList1, dereference) {
  EXPECT_EQ(0u, (*list1.begin()).value());
  EXPECT_EQ(0u, (*list1.cbegin()).value());
  EXPECT_EQ(nvalues - 1, (*list1.rbegin()).value());
  EXPECT_EQ(nvalues - 1, (*list1.crbegin()).value());
}

TEST_F(IntrusiveListTestWithList1, get_pointer) {
  EXPECT_EQ(0u, list1.begin()->value());
  EXPECT_EQ(0u, list1.begin()->value());
  EXPECT_EQ(nvalues - 1, list1.rbegin()->value());
  EXPECT_EQ(nvalues - 1, list1.crbegin()->value());
}

////////////////////
// operator++()
// operator++(int)
// operator--()
// operator--(int)

TEST_F(IntrusiveListTestWithList1, preincrement_type) {
  EXPECT_TRUE(is_expected_ref_type<List1::iterator&>(++list1.begin()));
  EXPECT_TRUE(is_expected_ref_type<List1::const_iterator&>(++list1.cbegin()));
  EXPECT_TRUE(is_expected_ref_type<List1::reverse_iterator&>(++list1.rbegin()));
  EXPECT_TRUE(is_expected_ref_type<List1::const_reverse_iterator&>(++list1.crbegin()));
}

TEST_F(IntrusiveListTestWithList1, postincrement_type) {
  EXPECT_TRUE(is_expected_type<List1::iterator>(list1.begin()++));
  EXPECT_TRUE(is_expected_type<List1::const_iterator>(list1.cbegin()++));
  EXPECT_TRUE(is_expected_type<List1::reverse_iterator>(list1.rbegin()++));
  EXPECT_TRUE(is_expected_type<List1::const_reverse_iterator>(list1.crbegin()++));
}

TEST_F(IntrusiveListTestWithList1, predecrement_type) {
  EXPECT_TRUE(is_expected_ref_type<List1::iterator&>(--list1.end()));
  EXPECT_TRUE(is_expected_ref_type<List1::const_iterator&>(--list1.cend()));
  EXPECT_TRUE(is_expected_ref_type<List1::reverse_iterator&>(--list1.rend()));
  EXPECT_TRUE(is_expected_ref_type<List1::const_reverse_iterator&>(--list1.crend()));
}

TEST_F(IntrusiveListTestWithList1, postdecrement_type) {
  EXPECT_TRUE(is_expected_type<List1::iterator>(list1.end()--));
  EXPECT_TRUE(is_expected_type<List1::const_iterator>(list1.cend()--));
  EXPECT_TRUE(is_expected_type<List1::reverse_iterator>(list1.rend()--));
  EXPECT_TRUE(is_expected_type<List1::const_reverse_iterator>(list1.crend()--));
}

class IntrusiveListTestPreStepper : public IntrusiveListTestWithList1 {
public:
  template<typename Stepper, typename It>
  void test_prestepper(Stepper step, It it, size_t idx, size_t idx1) {
    It it1 = it;
    EXPECT_EQ(it, it1);
    EXPECT_EQ(values[idx], it->This());
    EXPECT_EQ(values[idx], it1->This());

    It it2 = step(it);
    EXPECT_NE(it, it1);
    EXPECT_EQ(it, it2);
    EXPECT_NE(it1, it2);
    EXPECT_EQ(values[idx1], it->This());
    EXPECT_EQ(values[idx], it1->This());
    EXPECT_EQ(values[idx1], it2->This());

    It it3 = step(it1);
    EXPECT_EQ(it, it1);
    EXPECT_EQ(it, it2);
    EXPECT_EQ(it, it3);
    EXPECT_EQ(values[idx1], it->This());
    EXPECT_EQ(values[idx1], it1->This());
    EXPECT_EQ(values[idx1], it2->This());
    EXPECT_EQ(values[idx1], it3->This());
  }

  template<bool increment> struct PreStepper;
};

template<bool increment>
struct IntrusiveListTestPreStepper::PreStepper {
  template<typename It> It& operator()(It& it) const { return ++it; }
};

template<>
struct IntrusiveListTestPreStepper::PreStepper<false> {
  template<typename It> It& operator()(It& it) const { return --it; }
};

TEST_F(IntrusiveListTestPreStepper, preincrement) {
  PreStepper<true> step;
  {
    SCOPED_TRACE("forward non-const iterator");
    test_prestepper(step, list1.begin(), 0, 1);
  }
  {
    SCOPED_TRACE("forward const iterator");
    test_prestepper(step, list1.cbegin(), 0, 1);
  }
  {
    SCOPED_TRACE("reverse non-const iterator");
    test_prestepper(step, list1.rbegin(), nvalues - 1, nvalues - 2);
  }
  {
    SCOPED_TRACE("reverse const iterator");
    test_prestepper(step, list1.crbegin(), nvalues - 1, nvalues - 2);
  }
}

TEST_F(IntrusiveListTestPreStepper, predecrement) {
  PreStepper<false> step;
  {
    SCOPED_TRACE("forward non-const iterator");
    test_prestepper(step, ++list1.begin(), 1, 0);
  }
  {
    SCOPED_TRACE("forward const iterator");
    test_prestepper(step, ++list1.cbegin(), 1, 0);
  }
  {
    SCOPED_TRACE("reverse non-const iterator");
    test_prestepper(step, ++list1.rbegin(), nvalues - 2, nvalues - 1);
  }
  {
    SCOPED_TRACE("reverse const iterator");
    test_prestepper(step, ++list1.crbegin(), nvalues - 2, nvalues - 1);
  }
}

class IntrusiveListTestPostStepper : public IntrusiveListTestWithList1 {
public:
  template<typename Stepper, typename It>
  void test_poststepper(Stepper step, It it, size_t idx, size_t idx1) {
    It it1 = it;
    EXPECT_EQ(it, it1);
    EXPECT_EQ(values[idx], it->This());
    EXPECT_EQ(values[idx], it1->This());

    It it2 = step(it);
    EXPECT_NE(it, it2);
    EXPECT_EQ(it1, it2);
    EXPECT_EQ(values[idx1], it->This());
    EXPECT_EQ(values[idx], it1->This());
    EXPECT_EQ(values[idx], it2->This());

    It it3 = step(it1);
    EXPECT_EQ(it, it1);
    EXPECT_EQ(it2, it3);
    EXPECT_NE(it, it2);
    EXPECT_NE(it, it3);
    EXPECT_NE(it1, it2);
    EXPECT_NE(it1, it3);
    EXPECT_EQ(values[idx1], it->This());
    EXPECT_EQ(values[idx1], it1->This());
    EXPECT_EQ(values[idx], it2->This());
    EXPECT_EQ(values[idx], it3->This());
  }

  template<bool increment> struct PostStepper;
};

template<bool increment>
struct IntrusiveListTestPostStepper::PostStepper {
  template<typename It> It operator()(It& it) const { return it++; }
};

template<>
struct IntrusiveListTestPostStepper::PostStepper<false> {
  template<typename It> It operator()(It& it) const { return it--; }
};

TEST_F(IntrusiveListTestPostStepper, postincrement) {
  PostStepper<true> step;
  {
    SCOPED_TRACE("forward non-const iterator");
    test_poststepper(step, list1.begin(), 0, 1);
  }
  {
    SCOPED_TRACE("forward const iterator");
    test_poststepper(step, list1.cbegin(), 0, 1);
  }
  {
    SCOPED_TRACE("reverse non-const iterator");
    test_poststepper(step, list1.rbegin(), nvalues - 1, nvalues - 2);
  }
  {
    SCOPED_TRACE("reverse const iterator");
    test_poststepper(step, list1.crbegin(), nvalues - 1, nvalues - 2);
  }
}

TEST_F(IntrusiveListTestPostStepper, postdecrement) {
  PostStepper<false> step;
  {
    SCOPED_TRACE("forward non-const iterator");
    test_poststepper(step, ++list1.begin(), 1, 0);
  }
  {
    SCOPED_TRACE("forward const iterator");
    test_poststepper(step, ++list1.cbegin(), 1, 0);
  }
  {
    SCOPED_TRACE("reverse non-const iterator");
    test_poststepper(step, ++list1.rbegin(), nvalues - 2, nvalues - 1);
  }
  {
    SCOPED_TRACE("reverse const iterator");
    test_poststepper(step, ++list1.crbegin(), nvalues - 2, nvalues - 1);
  }
}

//////////////////////////////////////////////////////////////////////////////
// operator==
// operator!=
// operator== with peer, both argument orders
// operator!= with peer, both argument orders

template<typename It, typename CIt>
static void test_iterator_compare(It it, CIt cit) {
  It it1 = it;
  It it2 = it1;
  ++it2;

  CIt cit1 = cit;
  CIt cit2 = cit1;
  ++cit2;

  EXPECT_EQ(it, it1);
  EXPECT_NE(it, it2);

  EXPECT_EQ(cit, cit1);
  EXPECT_NE(cit, cit2);

  EXPECT_EQ(it, cit);
  EXPECT_EQ(cit, it);
  EXPECT_NE(it, cit2);
  EXPECT_NE(cit2, it);
}

TEST_F(IntrusiveListTestWithList1, compare) {
  test_iterator_compare(list1.begin(), list1.cbegin());
  test_iterator_compare(list1.rbegin(), list1.crbegin());
}

//////////////////////////////////////////////////////////////////////////////
// pop_front
// pop_back
// pop_front_and_dispose
// pop_back_and_dispose

TEST_F(IntrusiveListTestWithDisposal, pop) {
  ASSERT_EQ(nvalues, list1.length());
  ASSERT_EQ(values[0], list1.front().This());
  ASSERT_EQ(values[nvalues - 1], list1.back().This());

  list1.pop_front();
  EXPECT_EQ(nvalues - 1, list1.length());
  EXPECT_EQ(values[1], list1.front().This());

  list1.pop_front_and_dispose(CollectingDisposer(this));
  EXPECT_EQ(nvalues - 2, list1.length());
  EXPECT_EQ(values[2], list1.front().This());
  EXPECT_EQ(1u, ndisposed);
  EXPECT_EQ(values[1], disposed[0]);

  list1.pop_back();
  EXPECT_EQ(nvalues - 3, list1.length());
  EXPECT_EQ(values[nvalues - 2], list1.back().This());

  list1.pop_back_and_dispose(CollectingDisposer(this));
  EXPECT_EQ(nvalues - 4, list1.length());
  EXPECT_EQ(values[nvalues - 3], list1.back().This());
  EXPECT_EQ(2u, ndisposed);
  EXPECT_EQ(values[nvalues - 2], disposed[1]);
}

//////////////////////////////////////////////////////////////////////////////
// front -- const and non-const
// back -- const and non-const

TEST_F(IntrusiveListTestWithList1, end_access) {
  const List1& clist1 = list1;

  EXPECT_TRUE(is_expected_ref_type<List1::reference>(list1.front()));
  EXPECT_TRUE(is_expected_ref_type<List1::reference>(list1.back()));

  EXPECT_TRUE(is_expected_ref_type<List1::const_reference>(clist1.front()));
  EXPECT_TRUE(is_expected_ref_type<List1::const_reference>(clist1.back()));

  EXPECT_EQ(values[0], list1.front().This());
  EXPECT_EQ(values[0], clist1.front().This());

  EXPECT_EQ(values[nvalues - 1], list1.back().This());
  EXPECT_EQ(values[nvalues - 1], clist1.back().This());
}

//////////////////////////////////////////////////////////////////////////////
// begin -- const and non-const
// cbegin
// end -- const and non-const
// cend
// rbegin -- const and non-const
// crbegin
// rend -- const and non-const
// crend

TEST_F(IntrusiveListTestWithList1, iter_type) {
  const List1& clist1 = list1;

  EXPECT_TRUE(is_expected_type<List1::iterator>(list1.begin()));
  EXPECT_TRUE(is_expected_type<List1::const_iterator>(clist1.begin()));
  EXPECT_TRUE(is_expected_type<List1::const_iterator>(list1.cbegin()));

  EXPECT_TRUE(is_expected_type<List1::iterator>(list1.end()));
  EXPECT_TRUE(is_expected_type<List1::const_iterator>(clist1.end()));
  EXPECT_TRUE(is_expected_type<List1::const_iterator>(list1.cend()));

  EXPECT_TRUE(is_expected_type<List1::reverse_iterator>(list1.rbegin()));
  EXPECT_TRUE(is_expected_type<List1::const_reverse_iterator>(clist1.rbegin()));
  EXPECT_TRUE(is_expected_type<List1::const_reverse_iterator>(list1.crbegin()));

  EXPECT_TRUE(is_expected_type<List1::reverse_iterator>(list1.rbegin()));
  EXPECT_TRUE(is_expected_type<List1::const_reverse_iterator>(clist1.rbegin()));
  EXPECT_TRUE(is_expected_type<List1::const_reverse_iterator>(list1.crbegin()));
}

TEST_F(IntrusiveListTestWithList1, iters) {
  const List1& clist1 = list1;

  List1::pointer front = values[0];
  List1::pointer back = values[nvalues - 1];

  EXPECT_EQ(front, list1.begin()->This());
  EXPECT_EQ(front, clist1.begin()->This());
  EXPECT_EQ(front, list1.cbegin()->This());

  EXPECT_EQ(back, (--list1.end())->This());
  EXPECT_EQ(back, (--clist1.end())->This());
  EXPECT_EQ(back, (--list1.cend())->This());

  EXPECT_EQ(back, list1.rbegin()->This());
  EXPECT_EQ(back, clist1.rbegin()->This());
  EXPECT_EQ(back, list1.crbegin()->This());

  EXPECT_EQ(front, (--list1.rend())->This());
  EXPECT_EQ(front, (--clist1.rend())->This());
  EXPECT_EQ(front, (--list1.crend())->This());
}

//////////////////////////////////////////////////////////////////////////////
// erase -- one and range, forward and reversed
// erase_and_dispose -- one and range, forward and reversed

TEST_F(IntrusiveListTestWithList1, erase1) {
  EXPECT_EQ(nvalues, list1.length());

  int step = 2;
  unsigned index = step;
  List1::const_iterator it = step_iterator(list1.begin(), step);
  List1::const_reference value = *it;
  EXPECT_EQ(index, value.value());
  EXPECT_EQ(values[index], value.This());

  List1::iterator nit = list1.erase(it);
  EXPECT_EQ(nvalues - 1, list1.length());
  EXPECT_EQ(index + 1, nit->value());
  nit = step_iterator(nit, -step);
  EXPECT_EQ(nit, list1.begin());
}

TEST_F(IntrusiveListTestWithList1, erase1_reversed) {
  EXPECT_EQ(nvalues, list1.length());

  int step = 2;
  unsigned index = (nvalues - 1) - step;
  List1::const_reverse_iterator it = step_iterator(list1.rbegin(), step);
  List1::const_reference value = *it;
  EXPECT_EQ(index, value.value());
  EXPECT_EQ(values[index], value.This());

  List1::reverse_iterator nit = list1.erase(it);
  EXPECT_EQ(nvalues - 1, list1.length());
  EXPECT_EQ(index - 1, nit->value());
  nit = step_iterator(nit, -step);
  EXPECT_EQ(nit, list1.rbegin());
}

TEST_F(IntrusiveListTestWithDisposal, erase1_dispose) {
  EXPECT_EQ(nvalues, list1.length());

  int step = 2;
  unsigned index = step;
  List1::const_iterator it = step_iterator(list1.begin(), step);
  List1::const_reference value = *it;
  EXPECT_EQ(index, value.value());
  EXPECT_EQ(values[index], value.This());

  List1::iterator nit = list1.erase_and_dispose(it, CollectingDisposer(this));
  EXPECT_EQ(nvalues - 1, list1.length());
  EXPECT_EQ(index + 1, nit->value());

  EXPECT_EQ(1u, ndisposed);
  EXPECT_EQ(value.value(), disposed[0]->value());
  EXPECT_EQ(value.This(), disposed[0]);

  nit = step_iterator(nit, -step);
  EXPECT_EQ(nit, list1.begin());
}

TEST_F(IntrusiveListTestWithList1, erase_element) {
  EXPECT_EQ(nvalues, list1.length());

  int step = 2;
  unsigned index = step;
  List1::const_iterator it = step_iterator(list1.begin(), step);
  List1::const_reference value = *it;
  EXPECT_EQ(index, value.value());
  EXPECT_EQ(values[index], value.This());

  List1::iterator nit = list1.erase(value);
  EXPECT_EQ(nvalues - 1, list1.length());
  EXPECT_EQ(index + 1, nit->value());
  nit = step_iterator(nit, -step);
  EXPECT_EQ(nit, list1.begin());
}

TEST_F(IntrusiveListTestWithDisposal, erase1_dispose_reversed) {
  EXPECT_EQ(nvalues, list1.length());

  int step = 2;
  unsigned index = (nvalues - 1) - step;
  List1::const_reverse_iterator it = step_iterator(list1.rbegin(), step);
  List1::const_reference value = *it;
  EXPECT_EQ(index, value.value());
  EXPECT_EQ(values[index], value.This());

  List1::reverse_iterator nit = list1.erase_and_dispose(it, CollectingDisposer(this));
  EXPECT_EQ(nvalues - 1, list1.length());
  EXPECT_EQ(index - 1, nit->value());

  EXPECT_EQ(1u, ndisposed);
  EXPECT_EQ(value.value(), disposed[0]->value());
  EXPECT_EQ(value.This(), disposed[0]);

  nit = step_iterator(nit, -step);
  EXPECT_EQ(nit, list1.rbegin());
}

TEST_F(IntrusiveListTestWithList1, erase2) {
  EXPECT_EQ(nvalues, list1.length());

  int step1 = 2;
  unsigned index1 = step1;
  List1::const_iterator it1 = step_iterator(list1.begin(), step1);
  List1::const_reference value1 = *it1;
  EXPECT_EQ(index1, value1.value());

  int step2 = 2;
  unsigned index2 = index1 + step2;
  List1::const_iterator it2 = step_iterator(it1, step2);
  List1::const_reference value2 = *it2;
  EXPECT_EQ(index2, value2.value());

  List1::iterator nit = list1.erase(it1, it2);
  EXPECT_EQ(nvalues - step2, list1.length());
  EXPECT_EQ(index2, nit->value());
  EXPECT_EQ(it2, nit);

  nit = step_iterator(nit, -step1);
  EXPECT_EQ(nit, list1.begin());
}

TEST_F(IntrusiveListTestWithList1, erase2_reversed) {
  EXPECT_EQ(nvalues, list1.length());

  int step1 = 2;
  unsigned index1 = (nvalues - 1) - step1;
  List1::const_reverse_iterator it1 = step_iterator(list1.rbegin(), step1);
  List1::const_reference value1 = *it1;
  EXPECT_EQ(index1, value1.value());

  int step2 = 2;
  unsigned index2 = index1 - step2;
  List1::const_reverse_iterator it2 = step_iterator(it1, step2);
  List1::const_reference value2 = *it2;
  EXPECT_EQ(index2, value2.value());

  List1::reverse_iterator nit = list1.erase(it1, it2);
  EXPECT_EQ(nvalues - step2, list1.length());
  EXPECT_EQ(index2, nit->value());
  EXPECT_EQ(it2, nit);

  nit = step_iterator(nit, -step1);
  EXPECT_EQ(nit, list1.rbegin());
}

TEST_F(IntrusiveListTestWithDisposal, erase2_dispose) {
  EXPECT_EQ(nvalues, list1.length());

  int step1 = 2;
  unsigned index1 = step1;
  List1::const_iterator it1 = step_iterator(list1.begin(), step1);
  List1::const_reference value1 = *it1;
  EXPECT_EQ(index1, value1.value());

  int step2 = 1;
  unsigned index2 = index1 + step2;
  List1::const_iterator it2 = step_iterator(it1, step2);
  List1::const_reference value2 = *it2;
  EXPECT_EQ(index2, value2.value());

  int step3 = step2 + 1;
  unsigned index3 = index1 + step3;
  ++it2;
  List1::iterator nit = list1.erase_and_dispose(it1, it2, CollectingDisposer(this));
  EXPECT_EQ(nvalues - step3, list1.length());
  EXPECT_EQ(index3, nit->value());
  EXPECT_EQ(it2, nit);

  EXPECT_EQ(unsigned(step3), ndisposed);
  EXPECT_EQ(value1.value(), disposed[0]->value());
  EXPECT_EQ(value1.This(), disposed[0]);
  EXPECT_EQ(value2.value(), disposed[1]->value());
  EXPECT_EQ(value2.This(), disposed[1]);

  nit = step_iterator(nit, -step1);
  EXPECT_EQ(nit, list1.begin());
}

TEST_F(IntrusiveListTestWithDisposal, erase2_dispose_reversed) {
  EXPECT_EQ(nvalues, list1.length());

  int step1 = 2;
  unsigned index1 = (nvalues - 1) - step1;
  List1::const_reverse_iterator it1 = step_iterator(list1.rbegin(), step1);
  List1::const_reference value1 = *it1;
  EXPECT_EQ(index1, value1.value());

  int step2 = 1;
  unsigned index2 = index1 - step2;
  List1::const_reverse_iterator it2 = step_iterator(it1, step2);
  List1::const_reference value2 = *it2;
  EXPECT_EQ(index2, value2.value());

  int step3 = step2 + 1;
  unsigned index3 = index1 - step3;
  ++it2;
  List1::reverse_iterator nit = list1.erase_and_dispose(it1, it2, CollectingDisposer(this));
  EXPECT_EQ(nvalues - step3, list1.length());
  EXPECT_EQ(index3, nit->value());
  EXPECT_EQ(it2, nit);

  EXPECT_EQ(unsigned(step3), ndisposed);
  EXPECT_EQ(value1.value(), disposed[0]->value());
  EXPECT_EQ(value1.This(), disposed[0]);
  EXPECT_EQ(value2.value(), disposed[1]->value());
  EXPECT_EQ(value2.This(), disposed[1]);

  nit = step_iterator(nit, -step1);
  EXPECT_EQ(nit, list1.rbegin());
}

//////////////////////////////////////////////////////////////////////////////
// erase_if
// erase_and_dispose_if

TEST_F(IntrusiveListTestWithList1, erase_if) {
  EXPECT_EQ(nvalues, list1.length());
  EXPECT_TRUE(is_even(nvalues));

  auto has_even_value = [&](List1::const_reference v) { return is_even(v.value()); };
  List1::size_type removed = list1.erase_if(has_even_value);

  EXPECT_EQ(nvalues / 2, removed);
  EXPECT_EQ(nvalues / 2, list1.length());

  size_t i = 0;
  for (List1::const_reference v : list1) {
    EXPECT_EQ(++i, v.value());
    i += 1;
  }
  EXPECT_EQ(i, nvalues);
}

TEST_F(IntrusiveListTestWithDisposal, erase_and_dispose_if) {
  EXPECT_EQ(nvalues, list1.length());
  EXPECT_TRUE((nvalues & 1) == 0);

  auto has_even_value = [&](List1::const_reference v) { return is_even(v.value()); };
  List1::size_type removed
    = list1.erase_and_dispose_if(has_even_value, CollectingDisposer(this));

  EXPECT_EQ(nvalues / 2, removed);
  EXPECT_EQ(nvalues / 2, list1.length());

  {
    size_t i = 0;
    for (List1::const_reference v : list1) {
      EXPECT_EQ(++i, v.value());
      i += 1;
    }
    EXPECT_EQ(i, nvalues);
  }
  {
    EXPECT_EQ(nvalues / 2, ndisposed);
    size_t i = 0;
    for ( ; i < ndisposed; ++i) {
      EXPECT_TRUE(disposed[i] != nullptr);
      EXPECT_EQ(disposed[i]->value(), 2 * i);
    }
    for ( ; i < nvalues; ++i) {
      EXPECT_TRUE(disposed[i] == nullptr);
    }
  }
}

//////////////////////////////////////////////////////////////////////////////
// clear
// clear_and_dispose

TEST_F(IntrusiveListTestWithList1, clear) {
  EXPECT_FALSE(list1.empty());
  EXPECT_EQ(nvalues, list1.length());

  list1.clear();
  EXPECT_TRUE(list1.empty());
  EXPECT_EQ(0u, list1.length());

  // verify all values can be reinserted.
  fill_list();
}

TEST_F(IntrusiveListTestWithDisposal, clear_dispose) {
  EXPECT_FALSE(list1.empty());
  EXPECT_EQ(nvalues, list1.length());

  list1.clear_and_dispose(CollectingDisposer(this));
  EXPECT_TRUE(list1.empty());
  EXPECT_EQ(0u, list1.length());
  EXPECT_EQ(nvalues, ndisposed);

  for (size_t i = 0; i < nvalues; ++i) {
    EXPECT_EQ(values[i], disposed[i]);
  }

  // verify all values can be reinserted.
  fill_list();
}

//////////////////////////////////////////////////////////////////////////////
// insert

TEST_F(IntrusiveListTestWithList1, insert) {
  List1::pointer pvalue = values[0];
  EXPECT_EQ(pvalue, list1.begin()->This());
  list1.pop_front();
  EXPECT_EQ(nvalues - 1, list1.length());
  EXPECT_NE(pvalue, list1.begin()->This());

  List1::iterator it = step_iterator(list1.begin(), 3);
  EXPECT_EQ(values[4], it->This());

  List1::iterator nit = list1.insert(it, *pvalue);
  EXPECT_EQ(values[4], it->This());
  EXPECT_EQ(nvalues, list1.length());
  EXPECT_EQ(pvalue, nit->This());
  EXPECT_NE(it, nit);
  EXPECT_EQ(it, ++nit);
  nit = step_iterator(nit, -4);
  EXPECT_EQ(nit, list1.begin());
}

//////////////////////////////////////////////////////////////////////////////
// splice

class IntrusiveListTestSplice : public IntrusiveListTestWithValues {
public:
  IntrusiveListTestSplice() {
    fill_lists();
  }

  ~IntrusiveListTestSplice() {
    clear_lists();
  }

  static const size_t group_size = nvalues / 2;
  List1 list_a;
  List1 list_b;

  void fill_lists() {
    for (size_t i = 0; i < group_size; ++i) {
      list_a.push_back(*values[i]);
      list_b.push_back(*values[i + group_size]);
    }
  }

  void clear_lists() {
    list_a.clear();
    list_b.clear();
  }

  template<typename Iterator1, typename Iterator2>
  void check(Iterator1 start, Iterator2 end, size_t index) const {
    for (Iterator1 it = start; it != end; ++it) {
      ASSERT_EQ(values[index++], &*it);
    }
  }
};

TEST_F(IntrusiveListTestSplice, splice_all_front) {
  size_t a_size = list_a.length();
  size_t b_size = list_b.length();
  List1::iterator a_begin = list_a.begin();
  List1::iterator a_end = list_a.end();
  List1::iterator b_begin = list_b.begin();

  List1::iterator sresult = list_a.splice(a_begin, list_b);
  EXPECT_EQ(list_a.begin(), sresult);
  EXPECT_EQ(list_a.length(), a_size + b_size);
  EXPECT_TRUE(list_b.empty());
  EXPECT_EQ(b_begin, list_a.cbegin());
  EXPECT_EQ(a_end, list_a.cend());
  {
    SCOPED_TRACE("check new values");
    check(list_a.cbegin(), a_begin, group_size);
  }
  {
    SCOPED_TRACE("check old values");
    check(a_begin, list_a.cend(), 0);
  }
}

TEST_F(IntrusiveListTestSplice, splice_all_back) {
  size_t a_size = list_a.length();
  size_t b_size = list_b.length();
  List1::iterator a_begin = list_a.begin();
  List1::iterator a_end = list_a.end();
  List1::iterator a_penult = --List1::iterator(a_end);
  List1::iterator b_begin = list_b.begin();

  List1::iterator sresult = list_a.splice(list_a.end(), list_b);
  EXPECT_EQ(++a_penult, List1::iterator(sresult));
  EXPECT_EQ(list_a.length(), a_size + b_size);
  EXPECT_TRUE(list_b.empty());
  EXPECT_EQ(a_begin, list_a.cbegin());
  EXPECT_EQ(a_end, list_a.cend());
  {
    SCOPED_TRACE("check old values");
    check(list_a.cbegin(), b_begin, 0);
  }
  {
    SCOPED_TRACE("check new values");
    check(b_begin, list_a.cend(), group_size);
  }
}

TEST_F(IntrusiveListTestSplice, splice_all_middle) {
  const size_t middle_distance = 2;
  STATIC_ASSERT(middle_distance < group_size);
  size_t a_size = list_a.length();
  size_t b_size = list_b.length();
  List1::iterator a_begin = list_a.begin();
  List1::iterator a_middle = step_iterator(a_begin, middle_distance);
  List1::iterator a_pre_middle = --List1::iterator(a_middle);
  List1::iterator a_end = list_a.end();
  List1::iterator b_begin = list_b.begin();

  List1::iterator sresult = list_a.splice(a_middle, list_b);
  EXPECT_EQ(++a_pre_middle, List1::iterator(sresult));
  EXPECT_EQ(list_a.length(), a_size + b_size);
  EXPECT_TRUE(list_b.empty());
  EXPECT_EQ(a_begin, list_a.cbegin());
  EXPECT_EQ(a_end, list_a.cend());
  {
    SCOPED_TRACE("check initial old values");
    check(a_begin, b_begin, 0);
  }
  {
    SCOPED_TRACE("check new values");
    check(b_begin, a_middle, group_size);
  }
  {
    SCOPED_TRACE("check trailing old values");
    check(a_middle, a_end, middle_distance);
  }
}

TEST_F(IntrusiveListTestSplice, splice_some_middle) {
  const size_t middle_distance = 2;
  STATIC_ASSERT(middle_distance < group_size);
  const size_t move_start = 1;
  STATIC_ASSERT(move_start < group_size);
  const size_t move_size = 2;
  STATIC_ASSERT(move_start + move_size < group_size);
  size_t a_size = list_a.length();
  size_t b_size = list_b.length();
  List1::iterator a_begin = list_a.begin();
  List1::iterator a_middle = step_iterator(a_begin, middle_distance);
  List1::iterator a_pre_middle = --List1::iterator(a_middle);
  List1::iterator a_end = list_a.end();
  List1::iterator b_begin = list_b.begin();
  List1::iterator b_move_start = step_iterator(b_begin, move_start);
  List1::iterator b_move_end = step_iterator(b_move_start, move_size);
  List1::iterator b_end = list_b.end();

  List1::iterator sresult = list_a.splice(a_middle, list_b, b_move_start, b_move_end);
  EXPECT_EQ(++a_pre_middle, List1::iterator(sresult));
  EXPECT_EQ(list_a.length(), a_size + move_size);
  EXPECT_EQ(list_b.length(), b_size - move_size);
  EXPECT_EQ(a_begin, list_a.cbegin());
  EXPECT_EQ(a_end, list_a.cend());
  EXPECT_EQ(b_begin, list_b.cbegin());
  EXPECT_EQ(b_end, list_b.cend());
  {
    SCOPED_TRACE("check initial a values");
    check(list_a.cbegin(), b_move_start, 0);
  }
  {
    SCOPED_TRACE("check new a values");
    check(b_move_start, a_middle, group_size + move_start);
  }
  {
    SCOPED_TRACE("check trailing a values");
    check(a_middle, list_a.cend(), middle_distance);
  }
  {
    SCOPED_TRACE("check initial b values");
    check(b_begin, b_move_end, group_size);
  }
  {
    SCOPED_TRACE("check trailing b values");
    check(b_move_end, b_end, group_size + move_start + move_size);
  }
}

TEST_F(IntrusiveListTestSplice, splice_one_front) {
  const size_t move_start = 1;
  size_t a_size = list_a.length();
  size_t b_size = list_b.length();
  List1::iterator a_begin = list_a.begin();
  List1::iterator a_end = list_a.end();
  List1::iterator b_begin = list_b.begin();
  List1::iterator b_move_start = step_iterator(b_begin, move_start);
  List1::iterator b_move_end = step_iterator(b_move_start, 1);
  List1::iterator b_end = list_b.end();

  List1::iterator sresult = list_a.splice(a_begin, list_b, b_move_start);
  EXPECT_EQ(list_a.begin(), sresult);
  EXPECT_EQ(list_a.length(), a_size + 1);
  EXPECT_EQ(list_b.length(), b_size - 1);
  EXPECT_EQ(a_begin, ++list_a.begin());
  EXPECT_EQ(a_end, list_a.end());
  EXPECT_EQ(b_begin, list_b.begin());
  EXPECT_EQ(b_end, list_b.end());
  {
    SCOPED_TRACE("check new leading a values");
    check(list_a.cbegin(), a_begin, group_size + move_start);
  }
  {
    SCOPED_TRACE("check trailing a values");
    check(a_begin, a_end, 0);
  }
  {
    SCOPED_TRACE("check initial b values");
    check(b_begin, b_move_end, group_size);
  }
  {
    SCOPED_TRACE("check trailing b values");
    check(b_move_end, b_end, group_size + move_start + 1);
  }
}

TEST_F(IntrusiveListTestSplice, splice_one_back) {
  const size_t move_start = 1;
  size_t a_size = list_a.length();
  size_t b_size = list_b.length();
  List1::iterator a_begin = list_a.begin();
  List1::iterator a_end = list_a.end();
  List1::iterator a_penult = --List1::iterator(a_end);
  List1::iterator b_begin = list_b.begin();
  List1::iterator b_move_start = step_iterator(b_begin, move_start);
  List1::iterator b_move_end = step_iterator(b_move_start, 1);
  List1::iterator b_end = list_b.end();

  List1::iterator sresult = list_a.splice(a_end, list_b, b_move_start);
  EXPECT_EQ(++a_penult, List1::const_iterator(sresult));
  EXPECT_EQ(++a_penult, a_end);
  EXPECT_EQ(list_a.length(), a_size + 1);
  EXPECT_EQ(list_b.length(), b_size - 1);
  EXPECT_EQ(a_begin, list_a.begin());
  EXPECT_EQ(a_end, list_a.end());
  {
    SCOPED_TRACE("check old values");
    check(list_a.cbegin(), b_move_start, 0);
  }
  {
    SCOPED_TRACE("check new values");
    check(b_move_start, list_a.cend(), group_size + move_start);
  }
  {
    SCOPED_TRACE("check initial a values");
    check(b_begin, b_move_end, group_size);
  }
  {
    SCOPED_TRACE("check trailing b values");
    check(b_move_end, b_end, group_size + move_start + 1);
  }
}

TEST_F(IntrusiveListTestSplice, splice_one_in_place) {
  const size_t move_start = 1;
  size_t a_size = list_a.length();
  List1::iterator a_begin = list_a.begin();
  List1::iterator a_end = list_a.end();
  List1::iterator a_move_start = step_iterator(a_begin, move_start);
  List1::iterator a_move_end = step_iterator(a_move_start, 1);

  List1::iterator sresult = list_a.splice(a_move_end, list_a, a_move_start);
  EXPECT_EQ(a_move_start, List1::const_iterator(sresult));
  EXPECT_EQ(list_a.length(), a_size);
  EXPECT_EQ(list_a.begin(), a_begin);
  EXPECT_EQ(list_a.end(), a_end);
  EXPECT_EQ(a_move_start, step_iterator(a_begin, move_start));
  EXPECT_EQ(a_move_end, step_iterator(a_begin, move_start + 1));
  {
    SCOPED_TRACE("check values");
    check(a_begin, a_end, 0);
  }
}

TEST_F(IntrusiveListTestSplice, splice_into_const) {
  CList1 clist{};
  size_t a_size = list_a.length();
  size_t b_size = list_b.length();
  CList1::iterator sresult_a = clist.splice(clist.end(), list_a);
  CList1::iterator sresult_b = clist.splice(clist.end(), list_b);
  EXPECT_EQ(clist.length(), a_size + b_size);
  EXPECT_EQ(sresult_a, clist.begin());
  {
    SCOPED_TRACE("check values");
    check(clist.begin(), clist.end(), 0);
  }
  // This doesn't compile, which is as expected.  Transfer from list with
  // const elements to a list with non-const elements is disallowed, because
  // it implicitly casts away const.
  // List1::iterator sresult_c = list_a.splice(list_a.end(), clist);
  clist.clear();
}

TEST_F(IntrusiveListTestSplice, swap) {
  List1::reference front_a = list_a.front();
  List1::reference front_b = list_b.front();
  list_a.swap(list_b);
  EXPECT_EQ(&front_a, &list_b.front());
  EXPECT_EQ(&front_b, &list_a.front());
}

//////////////////////////////////////////////////////////////////////////////
// iterator_to - const and non-const

#define CHECK_ITERATOR_TYPE(the_iterator, expected_type)                    \
  static_assert(std::is_same<decltype(the_iterator), expected_type>::value, \
                "unexpected iterator type")                                 \
  /* */

TEST_F(IntrusiveListTestWithList1, iterator_to) {
  {
    List1::pointer pvalue = values[3];
    auto it = list1.iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, List1::iterator);
    EXPECT_EQ(pvalue, it->This());
  }

  {
    List1::const_pointer pvalue = values[3];
    auto it = list1.iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, List1::const_iterator);
    EXPECT_EQ(pvalue, it->This());
  }

  {
    List1::pointer pvalue = values[3];
    auto it = list1.const_iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, List1::const_iterator);
    EXPECT_EQ(pvalue, it->This());
  }

  {
    const List1& clist1 = list1;
    List1::pointer pvalue = values[3];
    auto it = clist1.iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, List1::const_iterator);
    EXPECT_EQ(pvalue, it->This());
  }

  {
    List1::pointer pvalue = values[3];
    auto it = list1.reverse_iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, List1::reverse_iterator);
    EXPECT_EQ(pvalue, it->This());
  }

  {
    List1::const_pointer pvalue = values[3];
    auto it = list1.reverse_iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, List1::const_reverse_iterator);
    EXPECT_EQ(pvalue, it->This());
  }

  {
    List1::pointer pvalue = values[3];
    auto it = list1.const_reverse_iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, List1::const_reverse_iterator);
    EXPECT_EQ(pvalue, it->This());
  }

  {
    const List1& clist1 = list1;
    List1::pointer pvalue = values[3];
    auto it = clist1.reverse_iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, List1::const_reverse_iterator);
    EXPECT_EQ(pvalue, it->This());
  }
}

TEST_F(IntrusiveListTestWithCList1, iterator_to_const) {
  {
    CList1::pointer pvalue = values[3];
    auto it = list1.iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, CList1::iterator);
    EXPECT_EQ(pvalue, it->This());
  }

  {
    CList1::const_pointer pvalue = values[3];
    auto it = list1.iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, CList1::const_iterator);
    EXPECT_EQ(pvalue, it->This());
  }

  {
    CList1::pointer pvalue = values[3];
    auto it = list1.const_iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, CList1::const_iterator);
    EXPECT_EQ(pvalue, it->This());
  }

  {
    const CList1& clist1 = list1;
    CList1::pointer pvalue = values[3];
    auto it = clist1.iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, CList1::const_iterator);
    EXPECT_EQ(pvalue, it->This());
  }

  {
    CList1::pointer pvalue = values[3];
    auto it = list1.reverse_iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, CList1::reverse_iterator);
    EXPECT_EQ(pvalue, it->This());
  }

  {
    CList1::const_pointer pvalue = values[3];
    auto it = list1.reverse_iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, CList1::const_reverse_iterator);
    EXPECT_EQ(pvalue, it->This());
  }

  {
    CList1::pointer pvalue = values[3];
    auto it = list1.const_reverse_iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, CList1::const_reverse_iterator);
    EXPECT_EQ(pvalue, it->This());
  }

  {
    const CList1& clist1 = list1;
    CList1::pointer pvalue = values[3];
    auto it = clist1.reverse_iterator_to(*pvalue);
    CHECK_ITERATOR_TYPE(it, CList1::const_reverse_iterator);
    EXPECT_EQ(pvalue, it->This());
  }
}

#undef CHECK_ITERATOR_TYPE

//////////////////////////////////////////////////////////////////////////////
// optional constant-time size

class IntrusiveListTestWithSize : public IntrusiveListTestWithValues {
  typedef IntrusiveListTestWithValues super;

public:
  typedef IntrusiveList<Value, &Value::entry1, true> ListWithSize;

  void SetUp() override {
    super::SetUp();
    fill_list();
  }

  void TearDown() override {
    list.clear();
    EXPECT_EQ(0u, list.size());
    EXPECT_EQ(list.length(), list.size());
    super::TearDown();
  }

  void fill_list() {
    for (size_t i = 0; i < nvalues; ++i) {
      if (is_even(i)) {
        list.push_back(*values[i]);
      } else {
        list.push_front(*values[i]);
      }
      EXPECT_EQ(i + 1, list.size());
      EXPECT_EQ(list.length(), list.size());
    }
  }

  struct NopDisposer {
    void operator()(const Value* value) const {}
  };

  ListWithSize list;
};

// Test push_front/back and clear.
// Everything is in the setup/teardown
TEST_F(IntrusiveListTestWithSize, basics) {}

TEST_F(IntrusiveListTestWithSize, pop) {
  size_t expected = nvalues;
  STATIC_ASSERT(4 <= nvalues);

  list.pop_back();
  --expected;
  EXPECT_EQ(expected, list.size());
  EXPECT_EQ(list.length(), list.size());

  list.pop_back_and_dispose(NopDisposer());
  --expected;
  EXPECT_EQ(expected, list.size());
  EXPECT_EQ(list.length(), list.size());

  list.pop_front();
  --expected;
  EXPECT_EQ(expected, list.size());
  EXPECT_EQ(list.length(), list.size());

  list.pop_front_and_dispose(NopDisposer());
  --expected;
  EXPECT_EQ(expected, list.size());
  EXPECT_EQ(list.length(), list.size());
}

TEST_F(IntrusiveListTestWithSize, erase) {
  typedef ListWithSize::const_iterator const_iterator;

  size_t expected = nvalues;
  STATIC_ASSERT(7 <= nvalues);

  list.erase(++list.begin());
  --expected;
  EXPECT_EQ(expected, list.size());
  EXPECT_EQ(list.length(), list.size());

  list.erase_and_dispose(++list.begin(), NopDisposer());
  --expected;
  EXPECT_EQ(expected, list.size());
  EXPECT_EQ(list.length(), list.size());

  {
    const_iterator start = ++list.begin();
    const_iterator end = step_iterator(start, 2);
    list.erase(start, end);
    expected -= 2;
    EXPECT_EQ(expected, list.size());
    EXPECT_EQ(list.length(), list.size());
  }

  {
    const_iterator start = ++list.begin();
    const_iterator end = step_iterator(start, 2);
    list.erase_and_dispose(start, end, NopDisposer());
    expected -= 2;
    EXPECT_EQ(expected, list.size());
    EXPECT_EQ(list.length(), list.size());
  }
}

TEST_F(IntrusiveListTestWithSize, splice) {
  using iterator = ListWithSize::iterator;
  List1 list1;

  // Transfer part of list to list1.
  iterator from = step_iterator(list.begin(), 2);
  iterator to = step_iterator(from, 4);
  list1.splice(list1.end(), list, from, to);
  EXPECT_EQ(nvalues - 4, list.size());
  EXPECT_EQ(list.length(), list.size());
  EXPECT_EQ(4u, list1.length());

  // Transfer all of list1 back to list.
  list.splice(to, list1);
  EXPECT_EQ(nvalues, list.size());
  EXPECT_EQ(list.length(), list.size());
  EXPECT_TRUE(list1.empty());

  // Transfer all of list to list1.
  // Transferring entire list having size() operation is special-cased.
  list1.splice(list1.end(), list);
  EXPECT_EQ(0u, list.size());
  EXPECT_EQ(list.length(), list.size());
  EXPECT_TRUE(list.empty());
  EXPECT_EQ(nvalues, list1.length());

  list1.clear();
}
