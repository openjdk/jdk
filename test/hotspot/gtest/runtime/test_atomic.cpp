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
 *
 */

#include "precompiled.hpp"
#include "runtime/atomic.hpp"
#include "unittest.hpp"

// These tests of Atomic only verify functionality.  They don't verify atomicity.

template<typename T>
struct AtomicAddTestSupport {
  volatile T _test_value;

  AtomicAddTestSupport() : _test_value{} {}

  void test_add() {
    T zero = 0;
    T five = 5;
    Atomic::store(&_test_value, zero);
    T value = Atomic::add(&_test_value, five);
    EXPECT_EQ(five, value);
    EXPECT_EQ(five, Atomic::load(&_test_value));
  }

  void test_fetch_add() {
    T zero = 0;
    T five = 5;
    Atomic::store(&_test_value, zero);
    T value = Atomic::fetch_and_add(&_test_value, five);
    EXPECT_EQ(zero, value);
    EXPECT_EQ(five, Atomic::load(&_test_value));
  }
};

TEST(AtomicAddTest, int32) {
  using Support = AtomicAddTestSupport<int32_t>;
  Support().test_add();
  Support().test_fetch_add();
}

// 64bit Atomic::add is only supported on 64bit platforms.
#ifdef _LP64
TEST(AtomicAddTest, int64) {
  using Support = AtomicAddTestSupport<int64_t>;
  Support().test_add();
  Support().test_fetch_add();
}
#endif // _LP64

TEST(AtomicAddTest, ptr) {
  uint _test_values[10] = {};
  uint* volatile _test_value{};

  uint* zero = &_test_values[0];
  uint* five = &_test_values[5];
  uint* six  = &_test_values[6];

  Atomic::store(&_test_value, zero);
  uint* value = Atomic::add(&_test_value, 5);
  EXPECT_EQ(five, value);
  EXPECT_EQ(five, Atomic::load(&_test_value));

  Atomic::store(&_test_value, zero);
  value = Atomic::fetch_and_add(&_test_value, 6);
  EXPECT_EQ(zero, value);
  EXPECT_EQ(six, Atomic::load(&_test_value));
};

template<typename T>
struct AtomicXchgTestSupport {
  volatile T _test_value;

  AtomicXchgTestSupport() : _test_value{} {}

  void test() {
    T zero = 0;
    T five = 5;
    Atomic::store(&_test_value, zero);
    T res = Atomic::xchg(&_test_value, five);
    EXPECT_EQ(zero, res);
    EXPECT_EQ(five, Atomic::load(&_test_value));
  }
};

TEST(AtomicXchgTest, int32) {
  using Support = AtomicXchgTestSupport<int32_t>;
  Support().test();
}

// 64bit Atomic::xchg is only supported on 64bit platforms.
#ifdef _LP64
TEST(AtomicXchgTest, int64) {
  using Support = AtomicXchgTestSupport<int64_t>;
  Support().test();
}
#endif // _LP64

template<typename T>
struct AtomicCmpxchgTestSupport {
  volatile T _test_value;

  AtomicCmpxchgTestSupport() : _test_value{} {}

  void test() {
    T zero = 0;
    T five = 5;
    T ten = 10;
    Atomic::store(&_test_value, zero);
    T res = Atomic::cmpxchg(&_test_value, five, ten);
    EXPECT_EQ(zero, res);
    EXPECT_EQ(zero, Atomic::load(&_test_value));
    res = Atomic::cmpxchg(&_test_value, zero, ten);
    EXPECT_EQ(zero, res);
    EXPECT_EQ(ten, Atomic::load(&_test_value));
  }
};

TEST(AtomicCmpxchgTest, int32) {
  using Support = AtomicCmpxchgTestSupport<int32_t>;
  Support().test();
}

TEST(AtomicCmpxchgTest, int64) {
  using Support = AtomicCmpxchgTestSupport<int64_t>;
  Support().test();
}

template<typename T>
struct AtomicEnumTestSupport {
  volatile T _test_value;

  AtomicEnumTestSupport() : _test_value{} {}

  void test_store_load(T value) {
    EXPECT_NE(value, Atomic::load(&_test_value));
    Atomic::store(&_test_value, value);
    EXPECT_EQ(value, Atomic::load(&_test_value));
  }

  void test_cmpxchg(T value1, T value2) {
    EXPECT_NE(value1, Atomic::load(&_test_value));
    Atomic::store(&_test_value, value1);
    EXPECT_EQ(value1, Atomic::cmpxchg(&_test_value, value2, value2));
    EXPECT_EQ(value1, Atomic::load(&_test_value));
    EXPECT_EQ(value1, Atomic::cmpxchg(&_test_value, value1, value2));
    EXPECT_EQ(value2, Atomic::load(&_test_value));
  }

  void test_xchg(T value1, T value2) {
    EXPECT_NE(value1, Atomic::load(&_test_value));
    Atomic::store(&_test_value, value1);
    EXPECT_EQ(value1, Atomic::xchg(&_test_value, value2));
    EXPECT_EQ(value2, Atomic::load(&_test_value));
  }
};

namespace AtomicEnumTestUnscoped {       // Scope the enumerators.
  enum TestEnum { A, B, C };
}

TEST(AtomicEnumTest, unscoped_enum) {
  using namespace AtomicEnumTestUnscoped;
  using Support = AtomicEnumTestSupport<TestEnum>;

  Support().test_store_load(B);
  Support().test_cmpxchg(B, C);
  Support().test_xchg(B, C);
}

enum class AtomicEnumTestScoped { A, B, C };

TEST(AtomicEnumTest, scoped_enum) {
  const AtomicEnumTestScoped B = AtomicEnumTestScoped::B;
  const AtomicEnumTestScoped C = AtomicEnumTestScoped::C;
  using Support = AtomicEnumTestSupport<AtomicEnumTestScoped>;

  Support().test_store_load(B);
  Support().test_cmpxchg(B, C);
  Support().test_xchg(B, C);
}
