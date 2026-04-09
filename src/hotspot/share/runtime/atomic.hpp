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

#ifndef SHARE_RUNTIME_ATOMIC_HPP
#define SHARE_RUNTIME_ATOMIC_HPP

#include "cppstdlib/type_traits.hpp"
#include "metaprogramming/dependentAlwaysFalse.hpp"
#include "metaprogramming/primitiveConversions.hpp"
#include "runtime/atomicAccess.hpp"
#include "utilities/globalDefinitions.hpp"

// Atomic<T> is used to declare a variable of type T with atomic access.
//
// The following value types T are supported:
//
// (1) Integers with sizeof the same as sizeof int32_t or int64_t. These are
// referred to as atomic integers below.
//
// (2) Integers with sizeof 1, including bool. These are referred to as atomic
// bytes below.
//
// (3) Pointers. These are referred to as atomic pointers below.
//
// (4) Types with a PrimitiveValues::Translate definition. These are referred
// to as atomic translated types below. The atomic value for the associated
// decayed type is referred to as the atomic decayed type.
//
// The interface provided by an Atomic<T> depends on the value type.
//
// If T is the value type, v is an Atomic<T>, x and y are instances of T, i is
// an integer, and o is an atomic_memory_order, then:
//
// (1) All Atomic types provide
//
//   nested types:
//     ValueType -> T
//
//   special functions:
//     constexpr constructor()             // See (2) below
//     explicit constexpr constructor(T)
//     noncopyable
//     destructor                          // Trivial
//
//   static member functions:
//     value_offset_in_bytes() -> int   // constexpr
//     value_size_in_bytes() -> int     // constexpr
//       These provide the compiler and the like with direct access to the
//       value field. They shouldn't be used directly to bypass normal access.
//
//   member functions:
//     v.load_relaxed() -> T
//     v.load_acquire() -> T
//     v.store_relaxed(x) -> void
//     v.release_store(x) -> void
//     v.release_store_fence(x) -> void
//     v.compare_exchange(x, y [, o]) -> T
//     v.compare_set(x, y [, o]) -> bool
//     v.exchange(x [, o]) -> T
//
// (2) All atomic types are default constructible.
//
// Default construction of an atomic integer or atomic byte initializes the
// value to zero. Default construction of an atomic pointer initializes the
// value to null.
//
// If the value type of an atomic translated type is default constructible,
// then default construction of the atomic translated type will initialize the
// value to a default constructed object of the value type. Otherwise, the
// value will be initialized as if by translating the value that would be
// provided by default constructing an atomic type for the value type's
// decayed type.
//
// (3) Constructors for all atomic types are constexpr, to ensure non-local
// atomic variables are constant initialized (C++17 6.6.2) when initialized
// with suitable arguments.
//
// (4) Atomic pointers and atomic integers additionally provide
//
//   member functions:
//     v.add_then_fetch(i [, o]) -> T
//     v.sub_then_fetch(i [, o]) -> T
//     v.fetch_then_add(i [, o]) -> T
//     v.fetch_then_sub(i [, o]) -> T
//
// sizeof(i) must not exceed sizeof(T). For atomic integers, both T and the
// type of i must be signed, or both must be unsigned. Atomic pointers perform
// element arithmetic.
//
// (5) Atomic integers additionally provide
//
//   member functions:
//     v.and_then_fetch(x [, o]) -> T
//     v.or_then_fetch(x [, o]) -> T
//     v.xor_then_fetch(x [, o]) -> T
//     v.fetch_then_and(x [, o]) -> T
//     v.fetch_then_or(x [, o]) -> T
//     v.fetch_then_xor(x [, o]) -> T
//
// (6) Atomic pointers additionally provide
//
//   nested types:
//     ElementType -> std::remove_pointer_t<T>
//
// Some of the function names provided by (some variants of) Atomic<T> differ
// from the corresponding functions provided by the AtomicAccess class. In
// some cases this is done for regularity; there are some inconsistencies in
// the AtomicAccess names. Some of the naming choices are also to make them
// stand out a little more when used in surrounding non-atomic code. Without
// the "AtomicAccess::" qualifier, some of those names are easily overlooked.
//
// Atomic for 2 byte integers is not supported. This is because atomic
// operations of that size have not been implemented. There haven't been
// required use-cases. Many platforms don't provide hardware support.
//
// Atomic translated types don't provide the full interface of the associated
// atomic decayed type. They could do so, perhaps under the control of an
// associated type trait.
//
// Atomic<T> is not intended to be anything approaching a drop-in replacement
// for std::atomic<T>. Rather, it's wrapping up a long-established HotSpot
// idiom in a tidier and more rigorous package. Some of the differences from
// std::atomic<T> include
//
// * Atomic<T> supports a much more limited set of value types.
//
// * All supported Atomic<T> types are "lock free", so the standard mechanisms
// for testing for that are not provided. (There might have been some types on
// some platforms that used a lock long-ago, but that's no longer the case.)
//
// * Rather than load and store operations with a memory order parameter,
// Atomic<T> provides load_relaxed(), load_acquire(), release_store(),
// store_relaxed(), and release_store_fence() operations.
//
// * Atomic<T> doesn't provide operator overloads that perform various
// operations with sequentially consistent ordering semantics. The rationale
// for not providing these is similar to that for having different (often
// longer) names for some operations than the corresponding AtomicAccess
// functions.

// Implementation support for Atomic<T>.
class AtomicImpl {
  enum class Category {
    Integer,
    Byte,
    Pointer,
    Translated
  };

#if defined(__GNUC__) && !defined(__clang__)
  // Workaround for gcc bug. Make category() public, else we get this error
  //   error: 'static constexpr AtomicImpl::Category AtomicImpl::category()
  //     [with T = unsigned int]' is private within this context
  // The only reference is the default template parameter value in the Atomic
  // class a couple lines below, in this same class!
  // https://gcc.gnu.org/bugzilla/show_bug.cgi?id=122098
public:
#endif
  // Selection of Atomic<T> category, based on T.
  template<typename T>
  static constexpr Category category();
private:

  // Helper base classes, providing various parts of the APIs.
  template<typename T> class CommonCore;
  template<typename T> class SupportsArithmetic;

public:
  template<typename T, Category = category<T>()>
  class Atomic;
};

// The Atomic<T> type.
template<typename T>
using Atomic = AtomicImpl::Atomic<T>;

template<typename T>
constexpr auto AtomicImpl::category() -> Category {
  static_assert(std::is_same_v<T, std::remove_cv_t<T>>,
                "Value type must not be cv-qualified");
  if constexpr (std::is_integral_v<T>) {
    if constexpr ((sizeof(T) == sizeof(int32_t)) || (sizeof(T) == sizeof(int64_t))) {
      return Category::Integer;
    } else if constexpr (sizeof(T) == 1) {
      return Category::Byte;
    } else {
      static_assert(DependentAlwaysFalse<T>, "Invalid atomic integer type");
    }
  } else if constexpr (std::is_pointer_v<T>) {
    return Category::Pointer;
  } else if constexpr (PrimitiveConversions::Translate<T>::value) {
    return Category::Translated;
  } else {
    static_assert(DependentAlwaysFalse<T>, "Invalid atomic value type");
  }
}

// Atomic<T> implementation classes.

template<typename T>
class AtomicImpl::CommonCore {
  T volatile _value;

protected:
  explicit constexpr CommonCore(T value) : _value(value) {}
  ~CommonCore() = default;

  T volatile* value_ptr() { return &_value; }
  T const volatile* value_ptr() const { return &_value; }

  // Support for value_offset_in_bytes.
  template<typename Derived>
  static constexpr int value_offset_in_bytes_impl() {
    return offsetof(Derived, _value);
  }

public:
  NONCOPYABLE(CommonCore);

  static constexpr int value_size_in_bytes() {
    return sizeof(_value);
  }

  // Common core Atomic<T> operations.

  T load_relaxed() const {
    return AtomicAccess::load(value_ptr());
  }

  T load_acquire() const {
    return AtomicAccess::load_acquire(value_ptr());
  }

  void store_relaxed(T value) {
    AtomicAccess::store(value_ptr(), value);
  }

  void release_store(T value) {
    AtomicAccess::release_store(value_ptr(), value);
  }

  void release_store_fence(T value) {
    AtomicAccess::release_store_fence(value_ptr(), value);
  }

  T compare_exchange(T compare_value, T new_value,
                     atomic_memory_order order = memory_order_conservative) {
    return AtomicAccess::cmpxchg(value_ptr(), compare_value, new_value, order);
  }

  bool compare_set(T compare_value, T new_value,
                   atomic_memory_order order = memory_order_conservative) {
    return compare_exchange(compare_value, new_value, order) == compare_value;
  }

  T exchange(T new_value,
             atomic_memory_order order = memory_order_conservative) {
    return AtomicAccess::xchg(this->value_ptr(), new_value, order);
  }
};

template<typename T>
class AtomicImpl::SupportsArithmetic : public CommonCore<T> {
  // Guarding the AtomicAccess calls with constexpr checking of Offset produces
  // better compile-time error messages.
  template<typename Offset>
  static constexpr bool check_offset_type() {
    static_assert(std::is_integral_v<Offset>, "offset must be integral");
    static_assert(sizeof(Offset) <= sizeof(T), "offset size exceeds value size");
    if constexpr (!std::is_integral_v<T>) {
      static_assert(std::is_pointer_v<T>, "must be");
    } else if constexpr (std::is_signed_v<T>) {
      static_assert(std::is_signed_v<Offset>,
                    "value is signed but offset is unsigned");
    } else {
      static_assert(std::is_unsigned_v<Offset>,
                    "value is unsigned but offset is signed");
    }
    return true;
  }

protected:
  explicit constexpr SupportsArithmetic(T value) : CommonCore<T>(value) {}
  ~SupportsArithmetic() = default;

public:
  template<typename Offset>
  T add_then_fetch(Offset add_value,
                   atomic_memory_order order = memory_order_conservative) {
    if constexpr (check_offset_type<Offset>()) {
      return AtomicAccess::add(this->value_ptr(), add_value, order);
    }
  }

  template<typename Offset>
  T fetch_then_add(Offset add_value,
                   atomic_memory_order order = memory_order_conservative) {
    if constexpr (check_offset_type<Offset>()) {
      return AtomicAccess::fetch_then_add(this->value_ptr(), add_value, order);
    }
  }

  template<typename Offset>
  T sub_then_fetch(Offset sub_value,
                   atomic_memory_order order = memory_order_conservative) {
    if constexpr (check_offset_type<Offset>()) {
      return AtomicAccess::sub(this->value_ptr(), sub_value, order);
    }
  }

  template<typename Offset>
  T fetch_then_sub(Offset sub_value,
                   atomic_memory_order order = memory_order_conservative) {
    if constexpr (check_offset_type<Offset>()) {
      // AtomicAccess doesn't currently provide fetch_then_sub.
      return sub_then_fetch(sub_value, order) + sub_value;
    }
  }
};

template<typename T>
class AtomicImpl::Atomic<T, AtomicImpl::Category::Integer>
  : public SupportsArithmetic<T>
{
public:
  constexpr Atomic() : Atomic(0) {}
  explicit constexpr Atomic(T value) : SupportsArithmetic<T>(value) {}

  NONCOPYABLE(Atomic);

  using ValueType = T;

  static constexpr int value_offset_in_bytes() {
    return CommonCore<T>::template value_offset_in_bytes_impl<Atomic>();
  }

  T fetch_then_and(T bits, atomic_memory_order order = memory_order_conservative) {
    return AtomicAccess::fetch_then_and(this->value_ptr(), bits, order);
  }

  T fetch_then_or(T bits, atomic_memory_order order = memory_order_conservative) {
    return AtomicAccess::fetch_then_or(this->value_ptr(), bits, order);
  }

  T fetch_then_xor(T bits, atomic_memory_order order = memory_order_conservative) {
    return AtomicAccess::fetch_then_xor(this->value_ptr(), bits, order);
  }

  T and_then_fetch(T bits, atomic_memory_order order = memory_order_conservative) {
    return AtomicAccess::and_then_fetch(this->value_ptr(), bits, order);
  }

  T or_then_fetch(T bits, atomic_memory_order order = memory_order_conservative) {
    return AtomicAccess::or_then_fetch(this->value_ptr(), bits, order);
  }

  T xor_then_fetch(T bits, atomic_memory_order order = memory_order_conservative) {
    return AtomicAccess::xor_then_fetch(this->value_ptr(), bits, order);
  }
};

template<typename T>
class AtomicImpl::Atomic<T, AtomicImpl::Category::Byte>
  : public CommonCore<T>
{
public:
  constexpr Atomic() : Atomic(0) {}
  explicit constexpr Atomic(T value) : CommonCore<T>(value) {}

  NONCOPYABLE(Atomic);

  using ValueType = T;

  static constexpr int value_offset_in_bytes() {
    return CommonCore<T>::template value_offset_in_bytes_impl<Atomic>();
  }
};

template<typename T>
class AtomicImpl::Atomic<T, AtomicImpl::Category::Pointer>
  : public SupportsArithmetic<T>
{
public:
  constexpr Atomic() : Atomic(nullptr) {}
  explicit constexpr Atomic(T value) : SupportsArithmetic<T>(value) {}

  NONCOPYABLE(Atomic);

  using ValueType = T;
  using ElementType = std::remove_pointer_t<T>;

  static constexpr int value_offset_in_bytes() {
    return CommonCore<T>::template value_offset_in_bytes_impl<Atomic>();
  }
};

// Atomic translated type

template<typename T>
class AtomicImpl::Atomic<T, AtomicImpl::Category::Translated> {
  using Translator = PrimitiveConversions::Translate<T>;
  using Decayed = typename Translator::Decayed;

  Atomic<Decayed> _value;

  // The decay function and the constructors are constexpr so that a non-local
  // atomic object constructed with constant arguments will be a constant
  // initialization.  One might ask why it's not a problem that some
  // specializations of these functions are not constant expressions. The
  // answer lies in C++17 10.1.5/6, along with us having *some* constexpr
  // translator decay functions, constexpr ctors for some translated types,
  // and constexpr ctors for some decayed types.  Also, C++23 removes those
  // restrictions on constexpr functions and ctors.

  static constexpr Decayed decay(T x) { return Translator::decay(x); }
  static T recover(Decayed x) { return Translator::recover(x); }

  // Support for default construction via the default construction of _value.
  struct UseDecayedCtor {};
  explicit constexpr Atomic(UseDecayedCtor) : _value() {}
  using DefaultCtorSelect =
    std::conditional_t<std::is_default_constructible_v<T>, T, UseDecayedCtor>;

public:
  using ValueType = T;

  // If T is default constructible, construct from a default constructed T.
  // Otherwise, default construct the underlying Atomic<Decayed>.
  constexpr Atomic() : Atomic(DefaultCtorSelect()) {}

  explicit constexpr Atomic(T value) : _value(decay(value)) {}

  NONCOPYABLE(Atomic);

  static constexpr int value_offset_in_bytes() {
    return (offsetof(Atomic, _value) +
            Atomic<Decayed>::value_offset_in_bytes());
  }

  static constexpr int value_size_in_bytes() {
    return Atomic<Decayed>::value_size_in_bytes();
  }

  T load_relaxed() const {
    return recover(_value.load_relaxed());
  }

  T load_acquire() const {
    return recover(_value.load_acquire());
  }

  void store_relaxed(T value) {
    _value.store_relaxed(decay(value));
  }

  void release_store(T value) {
    _value.release_store(decay(value));
  }

  void release_store_fence(T value) {
    _value.release_store_fence(decay(value));
  }

  T compare_exchange(T compare_value, T new_value,
                     atomic_memory_order order = memory_order_conservative) {
    return recover(_value.compare_exchange(decay(compare_value),
                                           decay(new_value),
                                           order));
  }

  bool compare_set(T compare_value, T new_value,
                   atomic_memory_order order = memory_order_conservative) {
    return _value.compare_set(decay(compare_value),
                              decay(new_value),
                              order);
  }

  T exchange(T new_value, atomic_memory_order order = memory_order_conservative) {
    return recover(_value.exchange(decay(new_value), order));
  }
};

#endif // SHARE_RUNTIME_ATOMIC_HPP
