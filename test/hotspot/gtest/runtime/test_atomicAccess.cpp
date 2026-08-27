/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/atomicAccess.hpp"
#include "unittest.hpp"

// These tests of AtomicAccess only verify functionality.  They don't verify
// atomicity.

template<typename T>
struct AtomicAccessAddTestSupport {
  volatile T _test_value;

  AtomicAccessAddTestSupport() : _test_value{} {}

  void test_add() {
    T zero = 0;
    T five = 5;
    AtomicAccess::store(&_test_value, zero);
    T value = AtomicAccess::add(&_test_value, five);
    EXPECT_EQ(five, value);
    EXPECT_EQ(five, AtomicAccess::load(&_test_value));
  }

  void test_fetch_add() {
    T zero = 0;
    T five = 5;
    AtomicAccess::store(&_test_value, zero);
    T value = AtomicAccess::fetch_then_add(&_test_value, five);
    EXPECT_EQ(zero, value);
    EXPECT_EQ(five, AtomicAccess::load(&_test_value));
  }
};

TEST_VM(AtomicAccessAddTest, int32) {
  using Support = AtomicAccessAddTestSupport<int32_t>;
  Support().test_add();
  Support().test_fetch_add();
}

TEST_VM(AtomicAccessAddTest, int64) {
  using Support = AtomicAccessAddTestSupport<int64_t>;
  Support().test_add();
  Support().test_fetch_add();
}

TEST_VM(AtomicAccessAddTest, ptr) {
  uint _test_values[10] = {};
  uint* volatile _test_value{};

  uint* zero = &_test_values[0];
  uint* five = &_test_values[5];
  uint* six  = &_test_values[6];

  AtomicAccess::store(&_test_value, zero);
  uint* value = AtomicAccess::add(&_test_value, 5);
  EXPECT_EQ(five, value);
  EXPECT_EQ(five, AtomicAccess::load(&_test_value));

  AtomicAccess::store(&_test_value, zero);
  value = AtomicAccess::fetch_then_add(&_test_value, 6);
  EXPECT_EQ(zero, value);
  EXPECT_EQ(six, AtomicAccess::load(&_test_value));
};

template<typename T>
struct AtomicAccessXchgTestSupport {
  volatile T _test_value;

  AtomicAccessXchgTestSupport() : _test_value{} {}

  void test() {
    T zero = 0;
    T five = 5;
    AtomicAccess::store(&_test_value, zero);
    T res = AtomicAccess::xchg(&_test_value, five);
    EXPECT_EQ(zero, res);
    EXPECT_EQ(five, AtomicAccess::load(&_test_value));
  }
};

TEST_VM(AtomicAccessXchgTest, int8) {
  using Support = AtomicAccessXchgTestSupport<int8_t>;
  Support().test();
}

TEST_VM(AtomicAccessXchgTest, int32) {
  using Support = AtomicAccessXchgTestSupport<int32_t>;
  Support().test();
}

TEST_VM(AtomicAccessXchgTest, int64) {
  using Support = AtomicAccessXchgTestSupport<int64_t>;
  Support().test();
}

template<typename T>
struct AtomicAccessCmpxchgTestSupport {
  volatile T _test_value;

  AtomicAccessCmpxchgTestSupport() : _test_value{} {}

  void test() {
    T zero = 0;
    T five = 5;
    T ten = 10;
    AtomicAccess::store(&_test_value, zero);
    T res = AtomicAccess::cmpxchg(&_test_value, five, ten);
    EXPECT_EQ(zero, res);
    EXPECT_EQ(zero, AtomicAccess::load(&_test_value));
    res = AtomicAccess::cmpxchg(&_test_value, zero, ten);
    EXPECT_EQ(zero, res);
    EXPECT_EQ(ten, AtomicAccess::load(&_test_value));
  }
};

TEST_VM(AtomicAccessCmpxchgTest, int32) {
  using Support = AtomicAccessCmpxchgTestSupport<int32_t>;
  Support().test();
}

TEST_VM(AtomicAccessCmpxchgTest, int64) {
  using Support = AtomicAccessCmpxchgTestSupport<int64_t>;
  Support().test();
}

struct AtomicAccessCmpxchg1ByteStressSupport {
  char _default_val;
  int  _base;
  char _array[7+32+7];

  AtomicAccessCmpxchg1ByteStressSupport() : _default_val(0x7a), _base(7), _array{} {}

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
    AtomicAccess::cmpxchg(&_array[index], _default_val, one);
    validate(_default_val, one, index);

    AtomicAccess::cmpxchg(&_array[index], one, _default_val);
    validate(_default_val, _default_val, index);
  }

  void test() {
    memset(_array, _default_val, sizeof(_array));
    for (int i = _base; i < (_base+32); i++) {
      test_index(i);
    }
  }
};

TEST_VM(AtomicAccessCmpxchg1Byte, stress) {
  AtomicAccessCmpxchg1ByteStressSupport support;
  support.test();
}

template<typename T>
struct AtomicAccessEnumTestSupport {
  volatile T _test_value;

  AtomicAccessEnumTestSupport() : _test_value{} {}

  void test_store_load(T value) {
    EXPECT_NE(value, AtomicAccess::load(&_test_value));
    AtomicAccess::store(&_test_value, value);
    EXPECT_EQ(value, AtomicAccess::load(&_test_value));
  }

  void test_cmpxchg(T value1, T value2) {
    EXPECT_NE(value1, AtomicAccess::load(&_test_value));
    AtomicAccess::store(&_test_value, value1);
    EXPECT_EQ(value1, AtomicAccess::cmpxchg(&_test_value, value2, value2));
    EXPECT_EQ(value1, AtomicAccess::load(&_test_value));
    EXPECT_EQ(value1, AtomicAccess::cmpxchg(&_test_value, value1, value2));
    EXPECT_EQ(value2, AtomicAccess::load(&_test_value));
  }

  void test_xchg(T value1, T value2) {
    EXPECT_NE(value1, AtomicAccess::load(&_test_value));
    AtomicAccess::store(&_test_value, value1);
    EXPECT_EQ(value1, AtomicAccess::xchg(&_test_value, value2));
    EXPECT_EQ(value2, AtomicAccess::load(&_test_value));
  }
};

namespace AtomicAccessEnumTestUnscoped {       // Scope the enumerators.
  enum TestEnum { A, B, C };
}

TEST_VM(AtomicAccessEnumTest, unscoped_enum) {
  using namespace AtomicAccessEnumTestUnscoped;
  using Support = AtomicAccessEnumTestSupport<TestEnum>;

  Support().test_store_load(B);
  Support().test_cmpxchg(B, C);
  Support().test_xchg(B, C);
}

enum class AtomicAccessEnumTestScoped { A, B, C };

TEST_VM(AtomicAccessEnumTest, scoped_enum) {
  const AtomicAccessEnumTestScoped B = AtomicAccessEnumTestScoped::B;
  const AtomicAccessEnumTestScoped C = AtomicAccessEnumTestScoped::C;
  using Support = AtomicAccessEnumTestSupport<AtomicAccessEnumTestScoped>;

  Support().test_store_load(B);
  Support().test_cmpxchg(B, C);
  Support().test_xchg(B, C);
}

template<typename T>
struct AtomicAccessBitopsTestSupport {
  volatile T _test_value;

  // At least one byte differs between _old_value and _old_value op _change_value.
  static const T _old_value =    static_cast<T>(UCONST64(0x7f5300007f530044));
  static const T _change_value = static_cast<T>(UCONST64(0x3800530038005322));

  AtomicAccessBitopsTestSupport() : _test_value(0) {}

  void fetch_then_and() {
    AtomicAccess::store(&_test_value, _old_value);
    T expected = _old_value & _change_value;
    EXPECT_NE(_old_value, expected);
    T result = AtomicAccess::fetch_then_and(&_test_value, _change_value);
    EXPECT_EQ(_old_value, result);
    EXPECT_EQ(expected, AtomicAccess::load(&_test_value));
  }

  void fetch_then_or() {
    AtomicAccess::store(&_test_value, _old_value);
    T expected = _old_value | _change_value;
    EXPECT_NE(_old_value, expected);
    T result = AtomicAccess::fetch_then_or(&_test_value, _change_value);
    EXPECT_EQ(_old_value, result);
    EXPECT_EQ(expected, AtomicAccess::load(&_test_value));
  }

  void fetch_then_xor() {
    AtomicAccess::store(&_test_value, _old_value);
    T expected = _old_value ^ _change_value;
    EXPECT_NE(_old_value, expected);
    T result = AtomicAccess::fetch_then_xor(&_test_value, _change_value);
    EXPECT_EQ(_old_value, result);
    EXPECT_EQ(expected, AtomicAccess::load(&_test_value));
  }

  void and_then_fetch() {
    AtomicAccess::store(&_test_value, _old_value);
    T expected = _old_value & _change_value;
    EXPECT_NE(_old_value, expected);
    T result = AtomicAccess::and_then_fetch(&_test_value, _change_value);
    EXPECT_EQ(expected, result);
    EXPECT_EQ(expected, AtomicAccess::load(&_test_value));
  }

  void or_then_fetch() {
    AtomicAccess::store(&_test_value, _old_value);
    T expected = _old_value | _change_value;
    EXPECT_NE(_old_value, expected);
    T result = AtomicAccess::or_then_fetch(&_test_value, _change_value);
    EXPECT_EQ(expected, result);
    EXPECT_EQ(expected, AtomicAccess::load(&_test_value));
  }

  void xor_then_fetch() {
    AtomicAccess::store(&_test_value, _old_value);
    T expected = _old_value ^ _change_value;
    EXPECT_NE(_old_value, expected);
    T result = AtomicAccess::xor_then_fetch(&_test_value, _change_value);
    EXPECT_EQ(expected, result);
    EXPECT_EQ(expected, AtomicAccess::load(&_test_value));
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
const T AtomicAccessBitopsTestSupport<T>::_old_value;

template<typename T>
const T AtomicAccessBitopsTestSupport<T>::_change_value;

TEST_VM(AtomicAccessBitopsTest, int8) {
  AtomicAccessBitopsTestSupport<int8_t>()();
}

TEST_VM(AtomicAccessBitopsTest, uint8) {
  AtomicAccessBitopsTestSupport<uint8_t>()();
}

TEST_VM(AtomicAccessBitopsTest, int32) {
  AtomicAccessBitopsTestSupport<int32_t>()();
}

TEST_VM(AtomicAccessBitopsTest, uint32) {
  AtomicAccessBitopsTestSupport<uint32_t>()();
}

TEST_VM(AtomicAccessBitopsTest, int64) {
  AtomicAccessBitopsTestSupport<int64_t>()();
}

TEST_VM(AtomicAccessBitopsTest, uint64) {
  AtomicAccessBitopsTestSupport<uint64_t>()();
}

// The following tests verify that atomic operations produce correct results for
// each atomic_memory_order value.  They don't verify the actual ordering
// semantics (which would require multi-threaded stress tests).

static const atomic_memory_order memory_orders[] = {
  memory_order_relaxed,
  memory_order_acquire,
  memory_order_release,
  memory_order_acq_rel,
  memory_order_seq_cst,
  memory_order_conservative,
};

template<typename T>
struct AtomicAccessOrderedAddTestSupport {
  volatile T _test_value;

  AtomicAccessOrderedAddTestSupport() : _test_value{} {}

  void test_add(atomic_memory_order order) {
    T zero = 0;
    T five = 5;
    AtomicAccess::store(&_test_value, zero);
    T value = AtomicAccess::add(&_test_value, five, order);
    EXPECT_EQ(five, value);
    EXPECT_EQ(five, AtomicAccess::load(&_test_value));
  }

  void test_fetch_add(atomic_memory_order order) {
    T zero = 0;
    T five = 5;
    AtomicAccess::store(&_test_value, zero);
    T value = AtomicAccess::fetch_then_add(&_test_value, five, order);
    EXPECT_EQ(zero, value);
    EXPECT_EQ(five, AtomicAccess::load(&_test_value));
  }
};

TEST_VM(AtomicAccessOrderedAddTest, int32) {
  using Support = AtomicAccessOrderedAddTestSupport<int32_t>;
  for (atomic_memory_order order : memory_orders) {
    Support().test_add(order);
    Support().test_fetch_add(order);
  }
}

TEST_VM(AtomicAccessOrderedAddTest, int64) {
  using Support = AtomicAccessOrderedAddTestSupport<int64_t>;
  for (atomic_memory_order order : memory_orders) {
    Support().test_add(order);
    Support().test_fetch_add(order);
  }
}

template<typename T>
struct AtomicAccessOrderedXchgTestSupport {
  volatile T _test_value;

  AtomicAccessOrderedXchgTestSupport() : _test_value{} {}

  void test(atomic_memory_order order) {
    T zero = 0;
    T five = 5;
    AtomicAccess::store(&_test_value, zero);
    T res = AtomicAccess::xchg(&_test_value, five, order);
    EXPECT_EQ(zero, res);
    EXPECT_EQ(five, AtomicAccess::load(&_test_value));
  }
};

TEST_VM(AtomicAccessOrderedXchgTest, int32) {
  using Support = AtomicAccessOrderedXchgTestSupport<int32_t>;
  for (atomic_memory_order order : memory_orders) {
    Support().test(order);
  }
}

TEST_VM(AtomicAccessOrderedXchgTest, int64) {
  using Support = AtomicAccessOrderedXchgTestSupport<int64_t>;
  for (atomic_memory_order order : memory_orders) {
    Support().test(order);
  }
}

template<typename T>
struct AtomicAccessOrderedCmpxchgTestSupport {
  volatile T _test_value;

  AtomicAccessOrderedCmpxchgTestSupport() : _test_value{} {}

  void test(atomic_memory_order order) {
    T zero = 0;
    T five = 5;
    T ten = 10;

    // Failed cmpxchg: compare_value does not match.
    AtomicAccess::store(&_test_value, zero);
    T res = AtomicAccess::cmpxchg(&_test_value, five, ten, order);
    EXPECT_EQ(zero, res);
    EXPECT_EQ(zero, AtomicAccess::load(&_test_value));

    // Successful cmpxchg: compare_value matches.
    res = AtomicAccess::cmpxchg(&_test_value, zero, ten, order);
    EXPECT_EQ(zero, res);
    EXPECT_EQ(ten, AtomicAccess::load(&_test_value));
  }
};

TEST_VM(AtomicAccessOrderedCmpxchgTest, int8) {
  using Support = AtomicAccessOrderedCmpxchgTestSupport<int8_t>;
  for (atomic_memory_order order : memory_orders) {
    Support().test(order);
  }
}

TEST_VM(AtomicAccessOrderedCmpxchgTest, int32) {
  using Support = AtomicAccessOrderedCmpxchgTestSupport<int32_t>;
  for (atomic_memory_order order : memory_orders) {
    Support().test(order);
  }
}

TEST_VM(AtomicAccessOrderedCmpxchgTest, int64) {
  using Support = AtomicAccessOrderedCmpxchgTestSupport<int64_t>;
  for (atomic_memory_order order : memory_orders) {
    Support().test(order);
  }
}

template<typename T>
struct AtomicAccessOrderedLoadStoreTestSupport {
  volatile T _test_value;

  AtomicAccessOrderedLoadStoreTestSupport() : _test_value{} {}

  void test_release_store_load_acquire(T value) {
    AtomicAccess::release_store(&_test_value, value);
    T loaded = AtomicAccess::load_acquire(&_test_value);
    EXPECT_EQ(value, loaded);
  }

  void test_release_store_fence(T value) {
    AtomicAccess::release_store_fence(&_test_value, value);
    T loaded = AtomicAccess::load_acquire(&_test_value);
    EXPECT_EQ(value, loaded);
  }
};

TEST_VM(AtomicAccessOrderedLoadStoreTest, int8) {
  using Support = AtomicAccessOrderedLoadStoreTestSupport<int8_t>;
  Support().test_release_store_load_acquire(42);
  Support().test_release_store_fence(42);
}

TEST_VM(AtomicAccessOrderedLoadStoreTest, int16) {
  using Support = AtomicAccessOrderedLoadStoreTestSupport<int16_t>;
  Support().test_release_store_load_acquire(1234);
  Support().test_release_store_fence(1234);
}

TEST_VM(AtomicAccessOrderedLoadStoreTest, int32) {
  using Support = AtomicAccessOrderedLoadStoreTestSupport<int32_t>;
  Support().test_release_store_load_acquire(123456);
  Support().test_release_store_fence(123456);
}

TEST_VM(AtomicAccessOrderedLoadStoreTest, int64) {
  using Support = AtomicAccessOrderedLoadStoreTestSupport<int64_t>;
  Support().test_release_store_load_acquire(1234567890LL);
  Support().test_release_store_fence(1234567890LL);
}

TEST_VM(AtomicAccessOrderedLoadStoreTest, ptr) {
  int dummy[10] = {};
  using Support = AtomicAccessOrderedLoadStoreTestSupport<int*>;
  Support().test_release_store_load_acquire(&dummy[5]);
  Support().test_release_store_fence(&dummy[7]);
}
