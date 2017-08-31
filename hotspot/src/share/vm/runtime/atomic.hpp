/*
 * Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_ATOMIC_HPP
#define SHARE_VM_RUNTIME_ATOMIC_HPP

#include "memory/allocation.hpp"
#include "metaprogramming/conditional.hpp"
#include "metaprogramming/enableIf.hpp"
#include "metaprogramming/isIntegral.hpp"
#include "metaprogramming/isPointer.hpp"
#include "metaprogramming/isSame.hpp"
#include "metaprogramming/primitiveConversions.hpp"
#include "metaprogramming/removeCV.hpp"
#include "metaprogramming/removePointer.hpp"
#include "utilities/align.hpp"
#include "utilities/macros.hpp"

enum cmpxchg_memory_order {
  memory_order_relaxed,
  // Use value which doesn't interfere with C++2011. We need to be more conservative.
  memory_order_conservative = 8
};

class Atomic : AllStatic {
 public:
  // Atomic operations on jlong types are not available on all 32-bit
  // platforms. If atomic ops on jlongs are defined here they must only
  // be used from code that verifies they are available at runtime and
  // can provide an alternative action if not - see supports_cx8() for
  // a means to test availability.

  // The memory operations that are mentioned with each of the atomic
  // function families come from src/share/vm/runtime/orderAccess.hpp,
  // e.g., <fence> is described in that file and is implemented by the
  // OrderAccess::fence() function. See that file for the gory details
  // on the Memory Access Ordering Model.

  // All of the atomic operations that imply a read-modify-write action
  // guarantee a two-way memory barrier across that operation. Historically
  // these semantics reflect the strength of atomic operations that are
  // provided on SPARC/X86. We assume that strength is necessary unless
  // we can prove that a weaker form is sufficiently safe.

  // Atomically store to a location
  inline static void store    (jbyte    store_value, jbyte*    dest);
  inline static void store    (jshort   store_value, jshort*   dest);
  inline static void store    (jint     store_value, jint*     dest);
  // See comment above about using jlong atomics on 32-bit platforms
  inline static void store    (jlong    store_value, jlong*    dest);
  inline static void store_ptr(intptr_t store_value, intptr_t* dest);
  inline static void store_ptr(void*    store_value, void*     dest);

  inline static void store    (jbyte    store_value, volatile jbyte*    dest);
  inline static void store    (jshort   store_value, volatile jshort*   dest);
  inline static void store    (jint     store_value, volatile jint*     dest);
  // See comment above about using jlong atomics on 32-bit platforms
  inline static void store    (jlong    store_value, volatile jlong*    dest);
  inline static void store_ptr(intptr_t store_value, volatile intptr_t* dest);
  inline static void store_ptr(void*    store_value, volatile void*     dest);

  // See comment above about using jlong atomics on 32-bit platforms
  inline static jlong load(const volatile jlong* src);

  // Atomically add to a location. Returns updated value. add*() provide:
  // <fence> add-value-to-dest <membar StoreLoad|StoreStore>

  template<typename I, typename D>
  inline static D add(I add_value, D volatile* dest);

  inline static intptr_t add_ptr(intptr_t add_value, volatile intptr_t* dest) {
    return add(add_value, dest);
  }

  inline static void* add_ptr(intptr_t add_value, volatile void* dest) {
    return add(add_value, reinterpret_cast<char* volatile*>(dest));
  }

  // Atomically increment location. inc*() provide:
  // <fence> increment-dest <membar StoreLoad|StoreStore>
  inline static void inc    (volatile jint*     dest);
  inline static void inc    (volatile jshort*   dest);
  inline static void inc    (volatile size_t*   dest);
  inline static void inc_ptr(volatile intptr_t* dest);
  inline static void inc_ptr(volatile void*     dest);

  // Atomically decrement a location. dec*() provide:
  // <fence> decrement-dest <membar StoreLoad|StoreStore>
  inline static void dec    (volatile jint*     dest);
  inline static void dec    (volatile jshort*   dest);
  inline static void dec    (volatile size_t*   dest);
  inline static void dec_ptr(volatile intptr_t* dest);
  inline static void dec_ptr(volatile void*     dest);

  // Performs atomic exchange of *dest with exchange_value. Returns old
  // prior value of *dest. xchg*() provide:
  // <fence> exchange-value-with-dest <membar StoreLoad|StoreStore>
  inline static jint         xchg    (jint         exchange_value, volatile jint*         dest);
  inline static unsigned int xchg    (unsigned int exchange_value, volatile unsigned int* dest);
  inline static intptr_t     xchg_ptr(intptr_t     exchange_value, volatile intptr_t*     dest);
  inline static void*        xchg_ptr(void*        exchange_value, volatile void*         dest);

  // Performs atomic compare of *dest and compare_value, and exchanges
  // *dest with exchange_value if the comparison succeeded. Returns prior
  // value of *dest. cmpxchg*() provide:
  // <fence> compare-and-exchange <membar StoreLoad|StoreStore>

  template<typename T, typename D, typename U>
  inline static D cmpxchg(T exchange_value,
                          D volatile* dest,
                          U compare_value,
                          cmpxchg_memory_order order = memory_order_conservative);

  // Performs atomic compare of *dest and NULL, and replaces *dest
  // with exchange_value if the comparison succeeded.  Returns true if
  // the comparison succeeded and the exchange occurred.  This is
  // often used as part of lazy initialization, as a lock-free
  // alternative to the Double-Checked Locking Pattern.
  template<typename T, typename D>
  inline static bool replace_if_null(T* value, D* volatile* dest,
                                     cmpxchg_memory_order order = memory_order_conservative);

  inline static intptr_t cmpxchg_ptr(intptr_t exchange_value,
                                     volatile intptr_t* dest,
                                     intptr_t compare_value,
                                     cmpxchg_memory_order order = memory_order_conservative) {
    return cmpxchg(exchange_value, dest, compare_value, order);
  }

  inline static void* cmpxchg_ptr(void* exchange_value,
                                  volatile void* dest,
                                  void* compare_value,
                                  cmpxchg_memory_order order = memory_order_conservative) {
    return cmpxchg(exchange_value,
                   reinterpret_cast<void* volatile*>(dest),
                   compare_value,
                   order);
  }

private:
  // Test whether From is implicitly convertible to To.
  // From and To must be pointer types.
  // Note: Provides the limited subset of C++11 std::is_convertible
  // that is needed here.
  template<typename From, typename To> struct IsPointerConvertible;

  // Dispatch handler for add.  Provides type-based validity checking
  // and limited conversions around calls to the platform-specific
  // implementation layer provided by PlatformAdd.
  template<typename I, typename D, typename Enable = void>
  struct AddImpl;

  // Platform-specific implementation of add.  Support for sizes of 4
  // bytes and (if different) pointer size bytes are required.  The
  // class is a function object that must be default constructable,
  // with these requirements:
  //
  // - dest is of type D*, an integral or pointer type.
  // - add_value is of type I, an integral type.
  // - sizeof(I) == sizeof(D).
  // - if D is an integral type, I == D.
  // - platform_add is an object of type PlatformAdd<sizeof(D)>.
  //
  // Then
  //   platform_add(add_value, dest)
  // must be a valid expression, returning a result convertible to D.
  //
  // No definition is provided; all platforms must explicitly define
  // this class and any needed specializations.
  template<size_t byte_size> struct PlatformAdd;

  // Helper base classes for defining PlatformAdd.  To use, define
  // PlatformAdd or a specialization that derives from one of these,
  // and include in the PlatformAdd definition the support function
  // (described below) required by the base class.
  //
  // These classes implement the required function object protocol for
  // PlatformAdd, using a support function template provided by the
  // derived class.  Let add_value (of type I) and dest (of type D) be
  // the arguments the object is called with.  If D is a pointer type
  // P*, then let addend (of type I) be add_value * sizeof(P);
  // otherwise, addend is add_value.
  //
  // FetchAndAdd requires the derived class to provide
  //   fetch_and_add(addend, dest)
  // atomically adding addend to the value of dest, and returning the
  // old value.
  //
  // AddAndFetch requires the derived class to provide
  //   add_and_fetch(addend, dest)
  // atomically adding addend to the value of dest, and returning the
  // new value.
  //
  // When D is a pointer type P*, both fetch_and_add and add_and_fetch
  // treat it as if it were a uintptr_t; they do not perform any
  // scaling of the addend, as that has already been done by the
  // caller.
public: // Temporary, can't be private: C++03 11.4/2. Fixed by C++11.
  template<typename Derived> struct FetchAndAdd;
  template<typename Derived> struct AddAndFetch;
private:

  // Support for platforms that implement some variants of add using a
  // (typically out of line) non-template helper function.  The
  // generic arguments passed to PlatformAdd need to be translated to
  // the appropriate type for the helper function, the helper function
  // invoked on the translated arguments, and the result translated
  // back.  Type is the parameter / return type of the helper
  // function.  No scaling of add_value is performed when D is a pointer
  // type, so this function can be used to implement the support function
  // required by AddAndFetch.
  template<typename Type, typename Fn, typename I, typename D>
  static D add_using_helper(Fn fn, I add_value, D volatile* dest);

  // Dispatch handler for cmpxchg.  Provides type-based validity
  // checking and limited conversions around calls to the
  // platform-specific implementation layer provided by
  // PlatformCmpxchg.
  template<typename T, typename D, typename U, typename Enable = void>
  struct CmpxchgImpl;

  // Platform-specific implementation of cmpxchg.  Support for sizes
  // of 1, 4, and 8 are required.  The class is a function object that
  // must be default constructable, with these requirements:
  //
  // - dest is of type T*.
  // - exchange_value and compare_value are of type T.
  // - order is of type cmpxchg_memory_order.
  // - platform_cmpxchg is an object of type PlatformCmpxchg<sizeof(T)>.
  //
  // Then
  //   platform_cmpxchg(exchange_value, dest, compare_value, order)
  // must be a valid expression, returning a result convertible to T.
  //
  // A default definition is provided, which declares a function template
  //   T operator()(T, T volatile*, T, cmpxchg_memory_order) const
  //
  // For each required size, a platform must either provide an
  // appropriate definition of that function, or must entirely
  // specialize the class template for that size.
  template<size_t byte_size> struct PlatformCmpxchg;

  // Support for platforms that implement some variants of cmpxchg
  // using a (typically out of line) non-template helper function.
  // The generic arguments passed to PlatformCmpxchg need to be
  // translated to the appropriate type for the helper function, the
  // helper invoked on the translated arguments, and the result
  // translated back.  Type is the parameter / return type of the
  // helper function.
  template<typename Type, typename Fn, typename T>
  static T cmpxchg_using_helper(Fn fn,
                                T exchange_value,
                                T volatile* dest,
                                T compare_value);

  // Support platforms that do not provide Read-Modify-Write
  // byte-level atomic access. To use, derive PlatformCmpxchg<1> from
  // this class.
public: // Temporary, can't be private: C++03 11.4/2. Fixed by C++11.
  struct CmpxchgByteUsingInt;
private:
};

template<typename From, typename To>
struct Atomic::IsPointerConvertible<From*, To*> : AllStatic {
  // Determine whether From* is implicitly convertible to To*, using
  // the "sizeof trick".
  typedef char yes;
  typedef char (&no)[2];

  static yes test(To*);
  static no test(...);
  static From* test_value;

  static const bool value = (sizeof(yes) == sizeof(test(test_value)));
};

// Define FetchAndAdd and AddAndFetch helper classes before including
// platform file, which may use these as base classes, requiring they
// be complete.

template<typename Derived>
struct Atomic::FetchAndAdd VALUE_OBJ_CLASS_SPEC {
  template<typename I, typename D>
  D operator()(I add_value, D volatile* dest) const;
};

template<typename Derived>
struct Atomic::AddAndFetch VALUE_OBJ_CLASS_SPEC {
  template<typename I, typename D>
  D operator()(I add_value, D volatile* dest) const;
};

// Define the class before including platform file, which may specialize
// the operator definition.  No generic definition of specializations
// of the operator template are provided, nor are there any generic
// specializations of the class.  The platform file is responsible for
// providing those.
template<size_t byte_size>
struct Atomic::PlatformCmpxchg VALUE_OBJ_CLASS_SPEC {
  template<typename T>
  T operator()(T exchange_value,
               T volatile* dest,
               T compare_value,
               cmpxchg_memory_order order) const;
};

// Define the class before including platform file, which may use this
// as a base class, requiring it be complete.  The definition is later
// in this file, near the other definitions related to cmpxchg.
struct Atomic::CmpxchgByteUsingInt VALUE_OBJ_CLASS_SPEC {
  template<typename T>
  T operator()(T exchange_value,
               T volatile* dest,
               T compare_value,
               cmpxchg_memory_order order) const;
};

// platform specific in-line definitions - must come before shared definitions

#include OS_CPU_HEADER(atomic)

// shared in-line definitions

// size_t casts...
#if (SIZE_MAX != UINTPTR_MAX)
#error size_t is not WORD_SIZE, interesting platform, but missing implementation here
#endif

template<typename I, typename D>
inline D Atomic::add(I add_value, D volatile* dest) {
  return AddImpl<I, D>()(add_value, dest);
}

template<typename I, typename D>
struct Atomic::AddImpl<
  I, D,
  typename EnableIf<IsIntegral<I>::value &&
                    IsIntegral<D>::value &&
                    (sizeof(I) <= sizeof(D)) &&
                    (IsSigned<I>::value == IsSigned<D>::value)>::type>
  VALUE_OBJ_CLASS_SPEC
{
  D operator()(I add_value, D volatile* dest) const {
    D addend = add_value;
    return PlatformAdd<sizeof(D)>()(addend, dest);
  }
};

template<typename I, typename P>
struct Atomic::AddImpl<
  I, P*,
  typename EnableIf<IsIntegral<I>::value && (sizeof(I) <= sizeof(P*))>::type>
  VALUE_OBJ_CLASS_SPEC
{
  P* operator()(I add_value, P* volatile* dest) const {
    STATIC_ASSERT(sizeof(intptr_t) == sizeof(P*));
    STATIC_ASSERT(sizeof(uintptr_t) == sizeof(P*));
    typedef typename Conditional<IsSigned<I>::value,
                                 intptr_t,
                                 uintptr_t>::type CI;
    CI addend = add_value;
    return PlatformAdd<sizeof(P*)>()(addend, dest);
  }
};

// Most platforms do not support atomic add on a 2-byte value. However,
// if the value occupies the most significant 16 bits of an aligned 32-bit
// word, then we can do this with an atomic add of (add_value << 16)
// to the 32-bit word.
//
// The least significant parts of this 32-bit word will never be affected, even
// in case of overflow/underflow.
//
// Use the ATOMIC_SHORT_PAIR macro (see macros.hpp) to get the desired alignment.
template<>
struct Atomic::AddImpl<jshort, jshort> VALUE_OBJ_CLASS_SPEC {
  jshort operator()(jshort add_value, jshort volatile* dest) const {
#ifdef VM_LITTLE_ENDIAN
    assert((intx(dest) & 0x03) == 0x02, "wrong alignment");
    jint new_value = Atomic::add(add_value << 16, (volatile jint*)(dest-1));
#else
    assert((intx(dest) & 0x03) == 0x00, "wrong alignment");
    jint new_value = Atomic::add(add_value << 16, (volatile jint*)(dest));
#endif
    return (jshort)(new_value >> 16); // preserves sign
  }
};

template<typename Derived>
template<typename I, typename D>
inline D Atomic::FetchAndAdd<Derived>::operator()(I add_value, D volatile* dest) const {
  I addend = add_value;
  // If D is a pointer type P*, scale by sizeof(P).
  if (IsPointer<D>::value) {
    addend *= sizeof(typename RemovePointer<D>::type);
  }
  D old = static_cast<const Derived*>(this)->fetch_and_add(addend, dest);
  return old + add_value;
}

template<typename Derived>
template<typename I, typename D>
inline D Atomic::AddAndFetch<Derived>::operator()(I add_value, D volatile* dest) const {
  // If D is a pointer type P*, scale by sizeof(P).
  if (IsPointer<D>::value) {
    add_value *= sizeof(typename RemovePointer<D>::type);
  }
  return static_cast<const Derived*>(this)->add_and_fetch(add_value, dest);
}

template<typename Type, typename Fn, typename I, typename D>
inline D Atomic::add_using_helper(Fn fn, I add_value, D volatile* dest) {
  return PrimitiveConversions::cast<D>(
    fn(PrimitiveConversions::cast<Type>(add_value),
       reinterpret_cast<Type volatile*>(dest)));
}

inline void Atomic::inc(volatile size_t* dest) {
  inc_ptr((volatile intptr_t*) dest);
}

inline void Atomic::dec(volatile size_t* dest) {
  dec_ptr((volatile intptr_t*) dest);
}

template<typename T, typename D, typename U>
inline D Atomic::cmpxchg(T exchange_value,
                         D volatile* dest,
                         U compare_value,
                         cmpxchg_memory_order order) {
  return CmpxchgImpl<T, D, U>()(exchange_value, dest, compare_value, order);
}

template<typename T, typename D>
inline bool Atomic::replace_if_null(T* value, D* volatile* dest,
                                    cmpxchg_memory_order order) {
  // Presently using a trivial implementation in terms of cmpxchg.
  // Consider adding platform support, to permit the use of compiler
  // intrinsics like gcc's __sync_bool_compare_and_swap.
  D* expected_null = NULL;
  return expected_null == cmpxchg(value, dest, expected_null, order);
}

// Handle cmpxchg for integral and enum types.
//
// All the involved types must be identical.
template<typename T>
struct Atomic::CmpxchgImpl<
  T, T, T,
  typename EnableIf<IsIntegral<T>::value || IsRegisteredEnum<T>::value>::type>
  VALUE_OBJ_CLASS_SPEC
{
  T operator()(T exchange_value, T volatile* dest, T compare_value,
               cmpxchg_memory_order order) const {
    // Forward to the platform handler for the size of T.
    return PlatformCmpxchg<sizeof(T)>()(exchange_value,
                                        dest,
                                        compare_value,
                                        order);
  }
};

// Handle cmpxchg for pointer types.
//
// The destination's type and the compare_value type must be the same,
// ignoring cv-qualifiers; we don't care about the cv-qualifiers of
// the compare_value.
//
// The exchange_value must be implicitly convertible to the
// destination's type; it must be type-correct to store the
// exchange_value in the destination.
template<typename T, typename D, typename U>
struct Atomic::CmpxchgImpl<
  T*, D*, U*,
  typename EnableIf<Atomic::IsPointerConvertible<T*, D*>::value &&
                    IsSame<typename RemoveCV<D>::type,
                           typename RemoveCV<U>::type>::value>::type>
  VALUE_OBJ_CLASS_SPEC
{
  D* operator()(T* exchange_value, D* volatile* dest, U* compare_value,
               cmpxchg_memory_order order) const {
    // Allow derived to base conversion, and adding cv-qualifiers.
    D* new_value = exchange_value;
    // Don't care what the CV qualifiers for compare_value are,
    // but we need to match D* when calling platform support.
    D* old_value = const_cast<D*>(compare_value);
    return PlatformCmpxchg<sizeof(D*)>()(new_value, dest, old_value, order);
  }
};

// Handle cmpxchg for types that have a translator.
//
// All the involved types must be identical.
//
// This translates the original call into a call on the decayed
// arguments, and returns the recovered result of that translated
// call.
template<typename T>
struct Atomic::CmpxchgImpl<
  T, T, T,
  typename EnableIf<PrimitiveConversions::Translate<T>::value>::type>
  VALUE_OBJ_CLASS_SPEC
{
  T operator()(T exchange_value, T volatile* dest, T compare_value,
               cmpxchg_memory_order order) const {
    typedef PrimitiveConversions::Translate<T> Translator;
    typedef typename Translator::Decayed Decayed;
    STATIC_ASSERT(sizeof(T) == sizeof(Decayed));
    return Translator::recover(
      cmpxchg(Translator::decay(exchange_value),
              reinterpret_cast<Decayed volatile*>(dest),
              Translator::decay(compare_value),
              order));
  }
};

template<typename Type, typename Fn, typename T>
inline T Atomic::cmpxchg_using_helper(Fn fn,
                                      T exchange_value,
                                      T volatile* dest,
                                      T compare_value) {
  STATIC_ASSERT(sizeof(Type) == sizeof(T));
  return PrimitiveConversions::cast<T>(
    fn(PrimitiveConversions::cast<Type>(exchange_value),
       reinterpret_cast<Type volatile*>(dest),
       PrimitiveConversions::cast<Type>(compare_value)));
}

template<typename T>
inline T Atomic::CmpxchgByteUsingInt::operator()(T exchange_value,
                                                 T volatile* dest,
                                                 T compare_value,
                                                 cmpxchg_memory_order order) const {
  STATIC_ASSERT(sizeof(T) == sizeof(uint8_t));
  uint8_t canon_exchange_value = exchange_value;
  uint8_t canon_compare_value = compare_value;
  volatile uint32_t* aligned_dest
    = reinterpret_cast<volatile uint32_t*>(align_down(dest, sizeof(uint32_t)));
  size_t offset = pointer_delta(dest, aligned_dest, 1);
  uint32_t cur = *aligned_dest;
  uint8_t* cur_as_bytes = reinterpret_cast<uint8_t*>(&cur);

  // current value may not be what we are looking for, so force it
  // to that value so the initial cmpxchg will fail if it is different
  cur_as_bytes[offset] = canon_compare_value;

  // always execute a real cmpxchg so that we get the required memory
  // barriers even on initial failure
  do {
    // value to swap in matches current value ...
    uint32_t new_value = cur;
    // ... except for the one jbyte we want to update
    reinterpret_cast<uint8_t*>(&new_value)[offset] = canon_exchange_value;

    uint32_t res = cmpxchg(new_value, aligned_dest, cur, order);
    if (res == cur) break;      // success

    // at least one byte in the int changed value, so update
    // our view of the current int
    cur = res;
    // if our byte is still as cur we loop and try again
  } while (cur_as_bytes[offset] == canon_compare_value);

  return PrimitiveConversions::cast<T>(cur_as_bytes[offset]);
}

inline unsigned Atomic::xchg(unsigned int exchange_value, volatile unsigned int* dest) {
  assert(sizeof(unsigned int) == sizeof(jint), "more work to do");
  return (unsigned int)Atomic::xchg((jint)exchange_value, (volatile jint*)dest);
}

inline void Atomic::inc(volatile jshort* dest) {
  (void)add(jshort(1), dest);
}

inline void Atomic::dec(volatile jshort* dest) {
  (void)add(jshort(-1), dest);
}

#endif // SHARE_VM_RUNTIME_ATOMIC_HPP
