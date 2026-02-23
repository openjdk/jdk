/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cppstdlib/new.hpp"
#include "cppstdlib/type_traits.hpp"
#include "metaprogramming/primitiveConversions.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"

#include "unittest.hpp"

// These tests of Atomic<T> only verify functionality.  They don't verify
// atomicity.

template<typename T>
struct AtomicInitializationTestSupport {
  struct Holder {
    Atomic<T> _explicitly_initialized;
    Atomic<T> _default_initialized;
    Atomic<T> _value_initialized;

    Holder()
      : _explicitly_initialized(T()),
        /* _default_initialized */
        _value_initialized{}
    {}
  };

  struct HolderNoConstructor {
    Atomic<T> _default_initialized;
  };

  void test() {
    T t = T();

    {
      Holder h;

      EXPECT_EQ(t, h._explicitly_initialized.load_relaxed());
      EXPECT_EQ(t, h._default_initialized.load_relaxed());
      EXPECT_EQ(t, h._value_initialized.load_relaxed());
    }

    {
      Holder h{};

      EXPECT_EQ(t, h._explicitly_initialized.load_relaxed());
      EXPECT_EQ(t, h._default_initialized.load_relaxed());
      EXPECT_EQ(t, h._value_initialized.load_relaxed());
    }

    {
      alignas(Holder) char mem[sizeof(Holder)];
      memset(mem, 0xFF, sizeof(Holder));
      Holder* h = new (mem) Holder();

      EXPECT_EQ(t, h->_explicitly_initialized.load_relaxed());
      EXPECT_EQ(t, h->_default_initialized.load_relaxed());
      EXPECT_EQ(t, h->_value_initialized.load_relaxed());
    }

    // No-constructor variant

    {
      HolderNoConstructor h;

      EXPECT_EQ(t, h._default_initialized.load_relaxed());
    }

    {
      HolderNoConstructor h{};

      EXPECT_EQ(t, h._default_initialized.load_relaxed());
    }

    {
      alignas(HolderNoConstructor) char mem[sizeof(HolderNoConstructor)];
      memset(mem, 0xFF, sizeof(HolderNoConstructor));
      HolderNoConstructor* h = new (mem) HolderNoConstructor();

      EXPECT_EQ(t, h->_default_initialized.load_relaxed());
    }
  }
};

TEST_VM(AtomicInitializationTest, byte) {
  AtomicInitializationTestSupport<char>().test();
}

TEST_VM(AtomicInitializationTest, integer) {
  AtomicInitializationTestSupport<int32_t>().test();
}

TEST_VM(AtomicInitializationTest, pointer) {
  AtomicInitializationTestSupport<void*>().test();
}

template<typename T>
struct AtomicIntegerArithmeticTestSupport {
  Atomic<T> _test_value;

  static constexpr T _old_value =    static_cast<T>(UCONST64(0x2000000020000));
  static constexpr T _change_value = static_cast<T>(UCONST64(    0x100000001));

  AtomicIntegerArithmeticTestSupport() : _test_value(0) {}

  void fetch_then_add() {
    _test_value.store_relaxed(_old_value);
    T expected = _old_value + _change_value;
    T result = _test_value.fetch_then_add(_change_value);
    EXPECT_EQ(_old_value, result);
    EXPECT_EQ(expected, _test_value.load_relaxed());
  }

  void fetch_then_sub() {
    _test_value.store_relaxed(_old_value);
    T expected = _old_value - _change_value;
    T result = _test_value.fetch_then_sub(_change_value);
    EXPECT_EQ(_old_value, result);
    EXPECT_EQ(expected, _test_value.load_relaxed());
  }

  void add_then_fetch() {
    _test_value.store_relaxed(_old_value);
    T expected = _old_value + _change_value;
    T result = _test_value.add_then_fetch(_change_value);
    EXPECT_EQ(expected, result);
    EXPECT_EQ(expected, _test_value.load_relaxed());
  }

  void sub_then_fetch() {
    _test_value.store_relaxed(_old_value);
    T expected = _old_value - _change_value;
    T result = _test_value.sub_then_fetch(_change_value);
    EXPECT_EQ(expected, result);
    EXPECT_EQ(expected, _test_value.load_relaxed());
  }

#define TEST_ARITHMETIC(name) { SCOPED_TRACE(XSTR(name)); name(); }

  void operator()() {
    TEST_ARITHMETIC(fetch_then_add)
    TEST_ARITHMETIC(fetch_then_sub)
    TEST_ARITHMETIC(add_then_fetch)
    TEST_ARITHMETIC(sub_then_fetch)
  }

#undef TEST_ARITHMETIC
};

TEST_VM(AtomicIntegerTest, arith_int32) {
  AtomicIntegerArithmeticTestSupport<int32_t>()();
}

TEST_VM(AtomicIntegerTest, arith_uint32) {
  AtomicIntegerArithmeticTestSupport<uint32_t>()();
}

TEST_VM(AtomicIntegerTest, arith_int64) {
  AtomicIntegerArithmeticTestSupport<int64_t>()();
}

TEST_VM(AtomicIntegerTest, arith_uint64) {
  AtomicIntegerArithmeticTestSupport<uint64_t>()();
}

template<typename T>
struct AtomicByteAndIntegerXchgTestSupport {
  Atomic<T> _test_value;

  AtomicByteAndIntegerXchgTestSupport() : _test_value{} {}

  void test() {
    T zero = 0;
    T five = 5;
    _test_value.store_relaxed(zero);
    T res = _test_value.exchange(five);
    EXPECT_EQ(zero, res);
    EXPECT_EQ(five, _test_value.load_relaxed());
  }
};

TEST_VM(AtomicIntegerTest, xchg_char) {
  using Support = AtomicByteAndIntegerXchgTestSupport<char>;
  Support().test();
}

TEST_VM(AtomicIntegerTest, xchg_int32) {
  using Support = AtomicByteAndIntegerXchgTestSupport<int32_t>;
  Support().test();
}

TEST_VM(AtomicIntegerTest, xchg_int64) {
  using Support = AtomicByteAndIntegerXchgTestSupport<int64_t>;
  Support().test();
}

template<typename T>
struct AtomicIntegerCmpxchgTestSupport {
  Atomic<T> _test_value;

  AtomicIntegerCmpxchgTestSupport() : _test_value{} {}

  void test() {
    T zero = 0;
    T five = 5;
    T ten = 10;
    _test_value.store_relaxed(zero);
    T res = _test_value.compare_exchange(five, ten);
    EXPECT_EQ(zero, res);
    EXPECT_EQ(zero, _test_value.load_relaxed());
    res = _test_value.compare_exchange(zero, ten);
    EXPECT_EQ(zero, res);
    EXPECT_EQ(ten, _test_value.load_relaxed());
  }
};

TEST_VM(AtomicIntegerTest, cmpxchg_int32) {
  using Support = AtomicIntegerCmpxchgTestSupport<int32_t>;
  Support().test();
}

TEST_VM(AtomicIntegerTest, cmpxchg_int64) {
  // Check if 64-bit atomics are available on the machine.
  using Support = AtomicIntegerCmpxchgTestSupport<int64_t>;
  Support().test();
}

template<typename T>
struct AtomicIntegerCmpsetTestSupport {
  Atomic<T> _test_value;

  AtomicIntegerCmpsetTestSupport() : _test_value{} {}

  void test() {
    T zero = 0;
    T five = 5;
    T ten = 10;
    _test_value.store_relaxed(zero);
    EXPECT_FALSE(_test_value.compare_set(five, ten));
    EXPECT_EQ(zero, _test_value.load_relaxed());
    EXPECT_TRUE(_test_value.compare_set(zero, ten));
    EXPECT_EQ(ten, _test_value.load_relaxed());
  }
};

TEST_VM(AtomicIntegerTest, cmpset_int32) {
  using Support = AtomicIntegerCmpsetTestSupport<int32_t>;
  Support().test();
}

TEST_VM(AtomicIntegerTest, cmpset_int64) {
  // Check if 64-bit atomics are available on the machine.
  using Support = AtomicIntegerCmpsetTestSupport<int64_t>;
  Support().test();
}

struct AtomicXchgAndCmpxchg1ByteStressSupport {
  char _default_val;
  int  _base;
  Atomic<char> _array[7+32+7];

  AtomicXchgAndCmpxchg1ByteStressSupport() : _default_val(0x7a), _base(7) {}

  void validate(char val, char val2, int index) {
    for (int i = 0; i < 7; i++) {
      EXPECT_EQ(_array[i].load_relaxed(), _default_val);
    }
    for (int i = 7; i < (7+32); i++) {
      if (i == index) {
        EXPECT_EQ(_array[i].load_relaxed(), val2);
      } else {
        EXPECT_EQ(_array[i].load_relaxed(), val);
      }
    }
    for (int i = 0; i < 7; i++) {
      EXPECT_EQ(_array[i].load_relaxed(), _default_val);
    }
  }

  template <typename Exchange>
  void test_index(int index) {
    Exchange exchange;
    char one = 1;
    exchange(_array[index], _default_val, one);
    validate(_default_val, one, index);

    exchange(_array[index], one, _default_val);
    validate(_default_val, _default_val, index);
  }

  template <typename Exchange>
  void test() {
    for (size_t i = 0; i < ARRAY_SIZE(_array); ++i) {
      _array[i].store_relaxed(_default_val);
    }
    for (int i = _base; i < (_base+32); i++) {
      test_index<Exchange>(i);
    }
  }
  void test_exchange() {
    struct StressWithExchange {
      void operator()(Atomic<char>& atomic, char compare_value, char new_value) {
        EXPECT_EQ(compare_value, atomic.exchange(new_value));
      }
    };
    test<StressWithExchange>();
  }

  void test_compare_exchange() {
    struct StressWithCompareExchange {
      void operator()(Atomic<char>& atomic, char compare_value, char new_value) {
        EXPECT_EQ(compare_value, atomic.compare_exchange(compare_value, new_value));
      }
    };
    test<StressWithCompareExchange>();
  }
};

TEST_VM(AtomicByteTest, stress_xchg) {
  AtomicXchgAndCmpxchg1ByteStressSupport support;
  support.test_exchange();
}

TEST_VM(AtomicByteTest, stress_cmpxchg) {
  AtomicXchgAndCmpxchg1ByteStressSupport support;
  support.test_compare_exchange();
}

template<typename T>
struct AtomicTestSupport {
  Atomic<T> _test_value;

  AtomicTestSupport() : _test_value{} {}

  void test_store_load(T value) {
    EXPECT_NE(value, _test_value.load_relaxed());
    _test_value.store_relaxed(value);
    EXPECT_EQ(value, _test_value.load_relaxed());
  }

  void test_cmpxchg(T value1, T value2) {
    EXPECT_NE(value1, _test_value.load_relaxed());
    _test_value.store_relaxed(value1);
    EXPECT_EQ(value1, _test_value.compare_exchange(value2, value2));
    EXPECT_EQ(value1, _test_value.load_relaxed());
    EXPECT_EQ(value1, _test_value.compare_exchange(value1, value2));
    EXPECT_EQ(value2, _test_value.load_relaxed());
  }

  void test_xchg(T value1, T value2) {
    EXPECT_NE(value1, _test_value.load_relaxed());
    _test_value.store_relaxed(value1);
    EXPECT_EQ(value1, _test_value.exchange(value2));
    EXPECT_EQ(value2, _test_value.load_relaxed());
  }

  template <T B, T C>
  static void test() {
    AtomicTestSupport().test_store_load(B);
    AtomicTestSupport().test_cmpxchg(B, C);
    AtomicTestSupport().test_xchg(B, C);
  }
};

namespace AtomicEnumTestUnscoped {       // Scope the enumerators.
  enum TestEnum { A, B, C };
}

TEST_VM(AtomicEnumTest, unscoped_enum) {
  using namespace AtomicEnumTestUnscoped;
  AtomicTestSupport<TestEnum>::test<B, C>();
}

enum class AtomicEnumTestScoped { A, B, C };

TEST_VM(AtomicEnumTest, scoped_enum) {
  const AtomicEnumTestScoped B = AtomicEnumTestScoped::B;
  const AtomicEnumTestScoped C = AtomicEnumTestScoped::C;
  AtomicTestSupport<AtomicEnumTestScoped>::test<B, C>();
}

enum class AtomicEnumTestScoped64Bit : uint64_t { A, B, C };

TEST_VM(AtomicEnumTest, scoped_enum_64_bit) {
  const AtomicEnumTestScoped64Bit B = AtomicEnumTestScoped64Bit::B;
  const AtomicEnumTestScoped64Bit C = AtomicEnumTestScoped64Bit::C;
  AtomicTestSupport<AtomicEnumTestScoped64Bit>::test<B, C>();
}

enum class AtomicEnumTestScoped8Bit : uint8_t { A, B, C };

TEST_VM(AtomicEnumTest, scoped_enum_8_bit) {
  const AtomicEnumTestScoped8Bit B = AtomicEnumTestScoped8Bit::B;
  const AtomicEnumTestScoped8Bit C = AtomicEnumTestScoped8Bit::C;
  AtomicTestSupport<AtomicEnumTestScoped8Bit>::test<B, C>();
}

TEST_VM(AtomicByteTest, char_test) {
  const char B = 0xB;
  const char C = 0xC;
  AtomicTestSupport<char>::test<B, C>();
}

TEST_VM(AtomicByteTest, bool_test) {
  const bool B = true;
  const bool C = false;
  AtomicTestSupport<bool>::test<B, C>();
}

template<typename T>
struct AtomicBitopsTestSupport {
  Atomic<T> _test_value;

  // At least one byte differs between _old_value and _old_value op _change_value.
  static constexpr T _old_value =    static_cast<T>(UCONST64(0x7f5300007f530044));
  static constexpr T _change_value = static_cast<T>(UCONST64(0x3800530038005322));

  AtomicBitopsTestSupport() : _test_value(0) {}

  void fetch_then_and() {
    _test_value.store_relaxed(_old_value);
    T expected = _old_value & _change_value;
    EXPECT_NE(_old_value, expected);
    T result = _test_value.fetch_then_and(_change_value);
    EXPECT_EQ(_old_value, result);
    EXPECT_EQ(expected, _test_value.load_relaxed());
  }

  void fetch_then_or() {
    _test_value.store_relaxed(_old_value);
    T expected = _old_value | _change_value;
    EXPECT_NE(_old_value, expected);
    T result = _test_value.fetch_then_or(_change_value);
    EXPECT_EQ(_old_value, result);
    EXPECT_EQ(expected, _test_value.load_relaxed());
  }

  void fetch_then_xor() {
    _test_value.store_relaxed(_old_value);
    T expected = _old_value ^ _change_value;
    EXPECT_NE(_old_value, expected);
    T result = _test_value.fetch_then_xor(_change_value);
    EXPECT_EQ(_old_value, result);
    EXPECT_EQ(expected, _test_value.load_relaxed());
  }

  void and_then_fetch() {
    _test_value.store_relaxed(_old_value);
    T expected = _old_value & _change_value;
    EXPECT_NE(_old_value, expected);
    T result = _test_value.and_then_fetch(_change_value);
    EXPECT_EQ(expected, result);
    EXPECT_EQ(expected, _test_value.load_relaxed());
  }

  void or_then_fetch() {
    _test_value.store_relaxed(_old_value);
    T expected = _old_value | _change_value;
    EXPECT_NE(_old_value, expected);
    T result = _test_value.or_then_fetch(_change_value);
    EXPECT_EQ(expected, result);
    EXPECT_EQ(expected, _test_value.load_relaxed());
  }

  void xor_then_fetch() {
    _test_value.store_relaxed(_old_value);
    T expected = _old_value ^ _change_value;
    EXPECT_NE(_old_value, expected);
    T result = _test_value.xor_then_fetch(_change_value);
    EXPECT_EQ(expected, result);
    EXPECT_EQ(expected, _test_value.load_relaxed());
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

TEST_VM(AtomicBitopsTest, int32) {
  AtomicBitopsTestSupport<int32_t>()();
}

TEST_VM(AtomicBitopsTest, uint32) {
  AtomicBitopsTestSupport<uint32_t>()();
}

TEST_VM(AtomicBitopsTest, int64) {
  AtomicBitopsTestSupport<int64_t>()();
}

TEST_VM(AtomicBitopsTest, uint64) {
  AtomicBitopsTestSupport<uint64_t>()();
}

template<typename T>
struct AtomicPointerTestSupport {
  static T _test_values[10];
  static T* _initial_ptr;

  Atomic<T*> _test_value;

  AtomicPointerTestSupport() : _test_value(nullptr) {}

  void fetch_then_add() {
    _test_value.store_relaxed(_initial_ptr);
    T* expected = _initial_ptr + 2;
    T* result = _test_value.fetch_then_add(2);
    EXPECT_EQ(_initial_ptr, result);
    EXPECT_EQ(expected, _test_value.load_relaxed());
  }

  void fetch_then_sub() {
    _test_value.store_relaxed(_initial_ptr);
    T* expected = _initial_ptr - 2;
    T* result = _test_value.fetch_then_sub(2);
    EXPECT_EQ(_initial_ptr, result);
    EXPECT_EQ(expected, _test_value.load_relaxed());
  }

  void add_then_fetch() {
    _test_value.store_relaxed(_initial_ptr);
    T* expected = _initial_ptr + 2;
    T* result = _test_value.add_then_fetch(2);
    EXPECT_EQ(expected, result);
    EXPECT_EQ(expected, _test_value.load_relaxed());
  }

  void sub_then_fetch() {
    _test_value.store_relaxed(_initial_ptr);
    T* expected = _initial_ptr - 2;
    T* result = _test_value.sub_then_fetch(2);
    EXPECT_EQ(expected, result);
    EXPECT_EQ(expected, _test_value.load_relaxed());
  }

  void exchange() {
    _test_value.store_relaxed(_initial_ptr);
    T* replace = _initial_ptr + 3;
    T* result = _test_value.exchange(replace);
    EXPECT_EQ(_initial_ptr, result);
    EXPECT_EQ(replace, _test_value.load_relaxed());
  }

  void compare_exchange() {
    _test_value.store_relaxed(_initial_ptr);
    T* not_initial_ptr = _initial_ptr - 1;
    T* replace = _initial_ptr + 3;

    T* result = _test_value.compare_exchange(not_initial_ptr, replace);
    EXPECT_EQ(_initial_ptr, result);
    EXPECT_EQ(_initial_ptr, _test_value.load_relaxed());

    result = _test_value.compare_exchange(_initial_ptr, replace);
    EXPECT_EQ(_initial_ptr, result);
    EXPECT_EQ(replace, _test_value.load_relaxed());
  }

#define TEST_OP(name) { SCOPED_TRACE(XSTR(name)); name(); }

  void operator()() {
    TEST_OP(fetch_then_add)
    TEST_OP(fetch_then_sub)
    TEST_OP(add_then_fetch)
    TEST_OP(sub_then_fetch)
    TEST_OP(exchange)
    TEST_OP(compare_exchange)
  }

#undef TEST_OP
};

template<typename T>
T AtomicPointerTestSupport<T>::_test_values[10] = {};

template<typename T>
T* AtomicPointerTestSupport<T>::_initial_ptr = &_test_values[5];

TEST_VM(AtomicPointerTest, ptr_to_char) {
  AtomicPointerTestSupport<char>()();
}

TEST_VM(AtomicPointerTest, ptr_to_int32) {
  AtomicPointerTestSupport<int32_t>()();
}

TEST_VM(AtomicPointerTest, ptr_to_int64) {
  AtomicPointerTestSupport<int64_t>()();
}

// Test translation, including chaining.

struct TranslatedAtomicTestObject1 {
  int _value;

  // NOT default constructible.

  explicit TranslatedAtomicTestObject1(int value)
    : _value(value) {}
};

template<>
struct PrimitiveConversions::Translate<TranslatedAtomicTestObject1>
  : public std::true_type
{
  using Value = TranslatedAtomicTestObject1;
  using Decayed = int;

  static Decayed decay(Value x) { return x._value; }
  static Value recover(Decayed x) { return Value(x); }
};

struct TranslatedAtomicTestObject2 {
  TranslatedAtomicTestObject1 _value;

  static constexpr int DefaultObject1Value = 3;

  TranslatedAtomicTestObject2()
    : TranslatedAtomicTestObject2(TranslatedAtomicTestObject1(DefaultObject1Value))
  {}

  explicit TranslatedAtomicTestObject2(TranslatedAtomicTestObject1 value)
    : _value(value) {}
};

template<>
struct PrimitiveConversions::Translate<TranslatedAtomicTestObject2>
  : public std::true_type
{
  using Value = TranslatedAtomicTestObject2;
  using Decayed = TranslatedAtomicTestObject1;

  static Decayed decay(Value x) { return x._value; }
  static Value recover(Decayed x) { return Value(x); }
};

struct TranslatedAtomicByteObject {
  uint8_t _value;

  // NOT default constructible.

  explicit TranslatedAtomicByteObject(uint8_t value = 0) : _value(value) {}
};

template<>
struct PrimitiveConversions::Translate<TranslatedAtomicByteObject>
  : public std::true_type
{
  using Value = TranslatedAtomicByteObject;
  using Decayed = uint8_t;

  static Decayed decay(Value x) { return x._value; }
  static Value recover(Decayed x) { return Value(x); }
};

template<typename T>
static void test_atomic_translated_type() {
  // This works even if T is not default constructible.
  Atomic<T> _test_value{};

  using Translated = PrimitiveConversions::Translate<T>;

  EXPECT_EQ(0, Translated::decay(_test_value.load_relaxed()));
  _test_value.store_relaxed(Translated::recover(5));
  EXPECT_EQ(5, Translated::decay(_test_value.load_relaxed()));
  EXPECT_EQ(5, Translated::decay(_test_value.compare_exchange(Translated::recover(5),
                                                              Translated::recover(10))));
  EXPECT_EQ(10, Translated::decay(_test_value.load_relaxed()));

  EXPECT_EQ(10, Translated::decay(_test_value.exchange(Translated::recover(20))));
  EXPECT_EQ(20, Translated::decay(_test_value.load_relaxed()));
}

TEST_VM(AtomicTranslatedTypeTest, int_test) {
  test_atomic_translated_type<TranslatedAtomicTestObject1>();
}

TEST_VM(AtomicTranslatedTypeTest, byte_test) {
  test_atomic_translated_type<TranslatedAtomicByteObject>();
}

TEST_VM(AtomicTranslatedTypeTest, chain) {
  Atomic<TranslatedAtomicTestObject2> _test_value{};

  using Translated1 = PrimitiveConversions::Translate<TranslatedAtomicTestObject1>;
  using Translated2 = PrimitiveConversions::Translate<TranslatedAtomicTestObject2>;

  auto resolve = [&](TranslatedAtomicTestObject2 x) {
    return Translated1::decay(Translated2::decay(x));
  };

  auto construct = [&](int x) {
    return Translated2::recover(Translated1::recover(x));
  };

  EXPECT_EQ(TranslatedAtomicTestObject2::DefaultObject1Value,
            resolve(_test_value.load_relaxed()));
  _test_value.store_relaxed(construct(5));
  EXPECT_EQ(5, resolve(_test_value.load_relaxed()));
  EXPECT_EQ(5, resolve(_test_value.compare_exchange(construct(5), construct(10))));
  EXPECT_EQ(10, resolve(_test_value.load_relaxed()));
  EXPECT_EQ(10, resolve(_test_value.exchange(construct(20))));
  EXPECT_EQ(20, resolve(_test_value.load_relaxed()));
};

template<typename T>
static void test_value_access() {
  using AT = Atomic<T>;
  // In addition to verifying values are as expected, also verify the
  // operations are constexpr.
  static_assert(sizeof(T) == AT::value_size_in_bytes(), "value size differs");
  static_assert(0 == AT::value_offset_in_bytes(), "unexpected offset");
  // Also verify no unexpected increase in size for Atomic wrapper.
  static_assert(sizeof(T) == sizeof(AT), "unexpected size difference");
};

TEST_VM(AtomicValueAccessTest, access_char) {
  test_value_access<char>();
}

TEST_VM(AtomicValueAccessTest, access_bool) {
  test_value_access<bool>();
}

TEST_VM(AtomicValueAccessTest, access_int32) {
  test_value_access<int32_t>();
}

TEST_VM(AtomicValueAccessTest, access_int64) {
  test_value_access<int64_t>();
}

TEST_VM(AtomicValueAccessTest, access_ptr) {
  test_value_access<char*>();
}

TEST_VM(AtomicValueAccessTest, access_trans1) {
  test_value_access<TranslatedAtomicTestObject1>();
}

TEST_VM(AtomicValueAccessTest, access_trans2) {
  test_value_access<TranslatedAtomicTestObject2>();
}
