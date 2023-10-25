/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
    T value = Atomic::fetch_then_add(&_test_value, five);
    EXPECT_EQ(zero, value);
    EXPECT_EQ(five, Atomic::load(&_test_value));
  }
};

TEST_VM(AtomicAddTest, int32) {
  using Support = AtomicAddTestSupport<int32_t>;
  Support().test_add();
  Support().test_fetch_add();
}

TEST_VM(AtomicAddTest, int64) {
  // Check if 64-bit atomics are available on the machine.
  if (!VM_Version::supports_cx8()) return;

  using Support = AtomicAddTestSupport<int64_t>;
  Support().test_add();
  Support().test_fetch_add();
}

TEST_VM(AtomicAddTest, ptr) {
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
  value = Atomic::fetch_then_add(&_test_value, 6);
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

TEST_VM(AtomicXchgTest, int32) {
  using Support = AtomicXchgTestSupport<int32_t>;
  Support().test();
}

TEST_VM(AtomicXchgTest, int64) {
  // Check if 64-bit atomics are available on the machine.
  if (!VM_Version::supports_cx8()) return;

  using Support = AtomicXchgTestSupport<int64_t>;
  Support().test();
}

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

TEST_VM(AtomicCmpxchgTest, int32) {
  using Support = AtomicCmpxchgTestSupport<int32_t>;
  Support().test();
}

TEST_VM(AtomicCmpxchgTest, int64) {
  // Check if 64-bit atomics are available on the machine.
  if (!VM_Version::supports_cx8()) return;

  using Support = AtomicCmpxchgTestSupport<int64_t>;
  Support().test();
}

struct AtomicCmpxchg1ByteStressSupport {
  char _default_val;
  int  _base;
  char _array[7+32+7];

  AtomicCmpxchg1ByteStressSupport() : _default_val(0x7a), _base(7), _array{} {}

  void validate(char val, char val2, int index) {
    for (int i = 0; i < 7; i++) {
      EXPECT_EQ(_array[i], _default_val);
    }
    for (int i = 7; i < (7+32); i++) {
      if (i == index) {
        EXPECT_EQ(_array[i], val2);
      } else {
        EXPECT_EQ(_array[i], val);
      }
    }
    for (int i = 0; i < 7; i++) {
      EXPECT_EQ(_array[i], _default_val);
    }
  }

  void test_index(int index) {
    char one = 1;
    Atomic::cmpxchg(&_array[index], _default_val, one);
    validate(_default_val, one, index);

    Atomic::cmpxchg(&_array[index], one, _default_val);
    validate(_default_val, _default_val, index);
  }

  void test() {
    memset(_array, _default_val, sizeof(_array));
    for (int i = _base; i < (_base+32); i++) {
      test_index(i);
    }
  }
};

TEST_VM(AtomicCmpxchg1Byte, stress) {
  AtomicCmpxchg1ByteStressSupport support;
  support.test();
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

TEST_VM(AtomicEnumTest, unscoped_enum) {
  using namespace AtomicEnumTestUnscoped;
  using Support = AtomicEnumTestSupport<TestEnum>;

  Support().test_store_load(B);
  Support().test_cmpxchg(B, C);
  Support().test_xchg(B, C);
}

enum class AtomicEnumTestScoped { A, B, C };

TEST_VM(AtomicEnumTest, scoped_enum) {
  const AtomicEnumTestScoped B = AtomicEnumTestScoped::B;
  const AtomicEnumTestScoped C = AtomicEnumTestScoped::C;
  using Support = AtomicEnumTestSupport<AtomicEnumTestScoped>;

  Support().test_store_load(B);
  Support().test_cmpxchg(B, C);
  Support().test_xchg(B, C);
}

template<typename T>
struct AtomicBitopsTestSupport {
  volatile T _test_value;

  // At least one byte differs between _old_value and _old_value op _change_value.
  static const T _old_value =    static_cast<T>(UCONST64(0x7f5300007f530044));
  static const T _change_value = static_cast<T>(UCONST64(0x3800530038005322));

  AtomicBitopsTestSupport() : _test_value(0) {}

  void fetch_then_and() {
    Atomic::store(&_test_value, _old_value);
    T expected = _old_value & _change_value;
    EXPECT_NE(_old_value, expected);
    T result = Atomic::fetch_then_and(&_test_value, _change_value);
    EXPECT_EQ(_old_value, result);
    EXPECT_EQ(expected, Atomic::load(&_test_value));
  }

  void fetch_then_or() {
    Atomic::store(&_test_value, _old_value);
    T expected = _old_value | _change_value;
    EXPECT_NE(_old_value, expected);
    T result = Atomic::fetch_then_or(&_test_value, _change_value);
    EXPECT_EQ(_old_value, result);
    EXPECT_EQ(expected, Atomic::load(&_test_value));
  }

  void fetch_then_xor() {
    Atomic::store(&_test_value, _old_value);
    T expected = _old_value ^ _change_value;
    EXPECT_NE(_old_value, expected);
    T result = Atomic::fetch_then_xor(&_test_value, _change_value);
    EXPECT_EQ(_old_value, result);
    EXPECT_EQ(expected, Atomic::load(&_test_value));
  }

  void and_then_fetch() {
    Atomic::store(&_test_value, _old_value);
    T expected = _old_value & _change_value;
    EXPECT_NE(_old_value, expected);
    T result = Atomic::and_then_fetch(&_test_value, _change_value);
    EXPECT_EQ(expected, result);
    EXPECT_EQ(expected, Atomic::load(&_test_value));
  }

  void or_then_fetch() {
    Atomic::store(&_test_value, _old_value);
    T expected = _old_value | _change_value;
    EXPECT_NE(_old_value, expected);
    T result = Atomic::or_then_fetch(&_test_value, _change_value);
    EXPECT_EQ(expected, result);
    EXPECT_EQ(expected, Atomic::load(&_test_value));
  }

  void xor_then_fetch() {
    Atomic::store(&_test_value, _old_value);
    T expected = _old_value ^ _change_value;
    EXPECT_NE(_old_value, expected);
    T result = Atomic::xor_then_fetch(&_test_value, _change_value);
    EXPECT_EQ(expected, result);
    EXPECT_EQ(expected, Atomic::load(&_test_value));
  }

#define TEST_BITOP(name) { SCOPED_TRACE(XSTR(name)); name(); }

  void operator()() {
    TEST_BITOP(fetch_then_and)
    TEST_BITOP(fetch_then_or)
    TEST_BITOP(fetch_then_xor)
    TEST_BITOP(and_then_fetch)
    TEST_BITOP(or_then_fetch)
    TEST_BITOP(xor_then_fetch)
  }

#undef TEST_BITOP
};

template<typename T>
const T AtomicBitopsTestSupport<T>::_old_value;

template<typename T>
const T AtomicBitopsTestSupport<T>::_change_value;

TEST_VM(AtomicBitopsTest, int8) {
  AtomicBitopsTestSupport<int8_t>()();
}

TEST_VM(AtomicBitopsTest, uint8) {
  AtomicBitopsTestSupport<uint8_t>()();
}

TEST_VM(AtomicBitopsTest, int32) {
  AtomicBitopsTestSupport<int32_t>()();
}

TEST_VM(AtomicBitopsTest, uint32) {
  AtomicBitopsTestSupport<uint32_t>()();
}

TEST_VM(AtomicBitopsTest, int64) {
  // Check if 64-bit atomics are available on the machine.
  if (!VM_Version::supports_cx8()) return;

  AtomicBitopsTestSupport<int64_t>()();
}

TEST_VM(AtomicBitopsTest, uint64) {
  // Check if 64-bit atomics are available on the machine.
  if (!VM_Version::supports_cx8()) return;

  AtomicBitopsTestSupport<uint64_t>()();
}
