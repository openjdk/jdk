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
 *
 */

#include "utilities/debug.hpp"
#include "utilities/deferredStatic.hpp"
#include "utilities/globalDefinitions.hpp"

#include <type_traits>

#include "unittest.hpp"

class DeferredStaticTestClass {
public:
  static unsigned _initialized_count;
  int _value;
  bool _allow_destruction;

  DeferredStaticTestClass() : DeferredStaticTestClass(0) {}

  DeferredStaticTestClass(int value, bool allow_destruction = false)
    : _value(value), _allow_destruction(allow_destruction)
  {
    ++_initialized_count;
  }

  ~DeferredStaticTestClass() {
    if (!_allow_destruction) {
      ShouldNotReachHere();
    }
  }
};

unsigned DeferredStaticTestClass::_initialized_count = 0;

using TC = DeferredStaticTestClass;

DeferredStatic<TC> default_constructed;

static_assert(std::is_same<TC*, decltype(default_constructed.get())>::value,
              "expected");

TEST(DeferredStaticTest, default_constructed) {
  unsigned init_count = TC::_initialized_count;
  default_constructed.initialize();
  ASSERT_EQ(init_count + 1, TC::_initialized_count);
  ASSERT_EQ(0, default_constructed->_value);
  ASSERT_EQ(0, default_constructed.get()->_value);
  ASSERT_EQ(0, (*default_constructed)._value);

  int new_value = 5;
  *default_constructed.get() = TC(new_value, true /* allow_destruction */);
  ASSERT_EQ(init_count + 2, TC::_initialized_count);
  ASSERT_EQ(new_value, default_constructed->_value);
  ASSERT_EQ(new_value, default_constructed.get()->_value);
  ASSERT_EQ(new_value, (*default_constructed)._value);

  int new_value2 = 8;
  default_constructed->_value = new_value2;
  ASSERT_EQ(init_count + 2, TC::_initialized_count);
  ASSERT_EQ(new_value2, default_constructed->_value);
  ASSERT_EQ(new_value2, default_constructed.get()->_value);
  ASSERT_EQ(new_value2, (*default_constructed)._value);
}

DeferredStatic<TC> arg_constructed;

TEST(DeferredStaticTest, arg_constructed) {
  unsigned init_count = TC::_initialized_count;
  int arg = 10;
  arg_constructed.initialize(arg);
  ASSERT_EQ(init_count + 1, TC::_initialized_count);
  ASSERT_EQ(arg, arg_constructed->_value);
  ASSERT_EQ(arg, arg_constructed.get()->_value);
  ASSERT_EQ(arg, (*arg_constructed)._value);
}

DeferredStatic<const TC> const_test_object;

static_assert(std::is_same<const TC*, decltype(const_test_object.get())>::value,
              "expected");

static_assert(std::is_same<const int*, decltype(&const_test_object->_value)>::value,
              "expected");

TEST(DeferredStaticTest, const_test_object) {
  unsigned init_count = TC::_initialized_count;
  int arg = 20;
  const_test_object.initialize(arg);
  ASSERT_EQ(init_count + 1, TC::_initialized_count);
  ASSERT_EQ(arg, const_test_object->_value);
  ASSERT_EQ(arg, const_test_object.get()->_value);
  ASSERT_EQ(arg, (*const_test_object)._value);

  // Doesn't compile, as expected.
  // *const_test_object.get() = TC(0, true /* allow_destruction */);

  // Doesn't compile, as expected.
  // const_test_object->_value = 0;
}
