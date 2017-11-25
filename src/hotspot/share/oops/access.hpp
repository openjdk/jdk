/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_ACCESS_HPP
#define SHARE_VM_RUNTIME_ACCESS_HPP

#include "memory/allocation.hpp"
#include "metaprogramming/decay.hpp"
#include "metaprogramming/integralConstant.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// = GENERAL =
// Access is an API for performing accesses with declarative semantics. Each access can have a number of "decorators".
// A decorator is an attribute or property that affects the way a memory access is performed in some way.
// There are different groups of decorators. Some have to do with memory ordering, others to do with,
// e.g. strength of references, strength of GC barriers, or whether compression should be applied or not.
// Some decorators are set at buildtime, such as whether primitives require GC barriers or not, others
// at callsites such as whether an access is in the heap or not, and others are resolved at runtime
// such as GC-specific barriers and encoding/decoding compressed oops.
// By pipelining handling of these decorators, the design of the Access API allows separation of concern
// over the different orthogonal concerns of decorators, while providing a powerful way of
// expressing these orthogonal semantic properties in a unified way.

// == OPERATIONS ==
// * load: Load a value from an address.
// * load_at: Load a value from an internal pointer relative to a base object.
// * store: Store a value at an address.
// * store_at: Store a value in an internal pointer relative to a base object.
// * atomic_cmpxchg: Atomically compare-and-swap a new value at an address if previous value matched the compared value.
// * atomic_cmpxchg_at: Atomically compare-and-swap a new value at an internal pointer address if previous value matched the compared value.
// * atomic_xchg: Atomically swap a new value at an address if previous value matched the compared value.
// * atomic_xchg_at: Atomically swap a new value at an internal pointer address if previous value matched the compared value.
// * arraycopy: Copy data from one heap array to another heap array.
// * clone: Clone the contents of an object to a newly allocated object.

typedef uint64_t DecoratorSet;

// == Internal Decorators - do not use ==
// * INTERNAL_EMPTY: This is the name for the empty decorator set (in absence of other decorators).
// * INTERNAL_CONVERT_COMPRESSED_OOPS: This is an oop access that will require converting an oop
//   to a narrowOop or vice versa, if UseCompressedOops is known to be set.
// * INTERNAL_VALUE_IS_OOP: Remember that the involved access is on oop rather than primitive.
const DecoratorSet INTERNAL_EMPTY                    = UCONST64(0);
const DecoratorSet INTERNAL_CONVERT_COMPRESSED_OOP   = UCONST64(1) << 1;
const DecoratorSet INTERNAL_VALUE_IS_OOP             = UCONST64(1) << 2;

// == Internal build-time Decorators ==
// * INTERNAL_BT_BARRIER_ON_PRIMITIVES: This is set in the barrierSetConfig.hpp file.
const DecoratorSet INTERNAL_BT_BARRIER_ON_PRIMITIVES = UCONST64(1) << 3;

// == Internal run-time Decorators ==
// * INTERNAL_RT_USE_COMPRESSED_OOPS: This decorator will be set in runtime resolved
//   access backends iff UseCompressedOops is true.
const DecoratorSet INTERNAL_RT_USE_COMPRESSED_OOPS   = UCONST64(1) << 4;

const DecoratorSet INTERNAL_DECORATOR_MASK           = INTERNAL_CONVERT_COMPRESSED_OOP | INTERNAL_VALUE_IS_OOP |
                                                       INTERNAL_BT_BARRIER_ON_PRIMITIVES | INTERNAL_RT_USE_COMPRESSED_OOPS;

// == Memory Ordering Decorators ==
// The memory ordering decorators can be described in the following way:
// === Decorator Rules ===
// The different types of memory ordering guarantees have a strict order of strength.
// Explicitly specifying the stronger ordering implies that the guarantees of the weaker
// property holds too. The names come from the C++11 atomic operations, and typically
// have a JMM equivalent property.
// The equivalence may be viewed like this:
// MO_UNORDERED is equivalent to JMM plain.
// MO_VOLATILE has no equivalence in JMM, because it's a C++ thing.
// MO_RELAXED is equivalent to JMM opaque.
// MO_ACQUIRE is equivalent to JMM acquire.
// MO_RELEASE is equivalent to JMM release.
// MO_SEQ_CST is equivalent to JMM volatile.
//
// === Stores ===
//  * MO_UNORDERED (Default): No guarantees.
//    - The compiler and hardware are free to reorder aggressively. And they will.
//  * MO_VOLATILE: Volatile stores (in the C++ sense).
//    - The stores are not reordered by the compiler (but possibly the HW) w.r.t. other
//      volatile accesses in program order (but possibly non-volatile accesses).
//  * MO_RELAXED: Relaxed atomic stores.
//    - The stores are atomic.
//    - Guarantees from volatile stores hold.
//  * MO_RELEASE: Releasing stores.
//    - The releasing store will make its preceding memory accesses observable to memory accesses
//      subsequent to an acquiring load observing this releasing store.
//    - Guarantees from relaxed stores hold.
//  * MO_SEQ_CST: Sequentially consistent stores.
//    - The stores are observed in the same order by MO_SEQ_CST loads on other processors
//    - Preceding loads and stores in program order are not reordered with subsequent loads and stores in program order.
//    - Guarantees from releasing stores hold.
// === Loads ===
//  * MO_UNORDERED (Default): No guarantees
//    - The compiler and hardware are free to reorder aggressively. And they will.
//  * MO_VOLATILE: Volatile loads (in the C++ sense).
//    - The loads are not reordered by the compiler (but possibly the HW) w.r.t. other
//      volatile accesses in program order (but possibly non-volatile accesses).
//  * MO_RELAXED: Relaxed atomic loads.
//    - The stores are atomic.
//    - Guarantees from volatile loads hold.
//  * MO_ACQUIRE: Acquiring loads.
//    - An acquiring load will make subsequent memory accesses observe the memory accesses
//      preceding the releasing store that the acquiring load observed.
//    - Guarantees from relaxed loads hold.
//  * MO_SEQ_CST: Sequentially consistent loads.
//    - These loads observe MO_SEQ_CST stores in the same order on other processors
//    - Preceding loads and stores in program order are not reordered with subsequent loads and stores in program order.
//    - Guarantees from acquiring loads hold.
// === Atomic Cmpxchg ===
//  * MO_RELAXED: Atomic but relaxed cmpxchg.
//    - Guarantees from MO_RELAXED loads and MO_RELAXED stores hold unconditionally.
//  * MO_SEQ_CST: Sequentially consistent cmpxchg.
//    - Guarantees from MO_SEQ_CST loads and MO_SEQ_CST stores hold unconditionally.
// === Atomic Xchg ===
//  * MO_RELAXED: Atomic but relaxed atomic xchg.
//    - Guarantees from MO_RELAXED loads and MO_RELAXED stores hold.
//  * MO_SEQ_CST: Sequentially consistent xchg.
//    - Guarantees from MO_SEQ_CST loads and MO_SEQ_CST stores hold.
const DecoratorSet MO_UNORDERED      = UCONST64(1) << 5;
const DecoratorSet MO_VOLATILE       = UCONST64(1) << 6;
const DecoratorSet MO_RELAXED        = UCONST64(1) << 7;
const DecoratorSet MO_ACQUIRE        = UCONST64(1) << 8;
const DecoratorSet MO_RELEASE        = UCONST64(1) << 9;
const DecoratorSet MO_SEQ_CST        = UCONST64(1) << 10;
const DecoratorSet MO_DECORATOR_MASK = MO_UNORDERED | MO_VOLATILE | MO_RELAXED |
                                       MO_ACQUIRE | MO_RELEASE | MO_SEQ_CST;

// === Barrier Strength Decorators ===
// * AS_RAW: The access will translate into a raw memory access, hence ignoring all semantic concerns
//   except memory ordering and compressed oops. This will bypass runtime function pointer dispatching
//   in the pipeline and hardwire to raw accesses without going trough the GC access barriers.
//  - Accesses on oop* translate to raw memory accesses without runtime checks
//  - Accesses on narrowOop* translate to encoded/decoded memory accesses without runtime checks
//  - Accesses on HeapWord* translate to a runtime check choosing one of the above
//  - Accesses on other types translate to raw memory accesses without runtime checks
// * AS_NO_KEEPALIVE: The barrier is used only on oop references and will not keep any involved objects
//   alive, regardless of the type of reference being accessed. It will however perform the memory access
//   in a consistent way w.r.t. e.g. concurrent compaction, so that the right field is being accessed,
//   or maintain, e.g. intergenerational or interregional pointers if applicable. This should be used with
//   extreme caution in isolated scopes.
// * AS_NORMAL: The accesses will be resolved to an accessor on the BarrierSet class, giving the
//   responsibility of performing the access and what barriers to be performed to the GC. This is the default.
//   Note that primitive accesses will only be resolved on the barrier set if the appropriate build-time
//   decorator for enabling primitive barriers is enabled for the build.
const DecoratorSet AS_RAW            = UCONST64(1) << 11;
const DecoratorSet AS_NO_KEEPALIVE   = UCONST64(1) << 12;
const DecoratorSet AS_NORMAL         = UCONST64(1) << 13;
const DecoratorSet AS_DECORATOR_MASK = AS_RAW | AS_NO_KEEPALIVE | AS_NORMAL;

// === Reference Strength Decorators ===
// These decorators only apply to accesses on oop-like types (oop/narrowOop).
// * ON_STRONG_OOP_REF: Memory access is performed on a strongly reachable reference.
// * ON_WEAK_OOP_REF: The memory access is performed on a weakly reachable reference.
// * ON_PHANTOM_OOP_REF: The memory access is performed on a phantomly reachable reference.
//   This is the same ring of strength as jweak and weak oops in the VM.
// * ON_UNKNOWN_OOP_REF: The memory access is performed on a reference of unknown strength.
//   This could for example come from the unsafe API.
// * Default (no explicit reference strength specified): ON_STRONG_OOP_REF
const DecoratorSet ON_STRONG_OOP_REF  = UCONST64(1) << 14;
const DecoratorSet ON_WEAK_OOP_REF    = UCONST64(1) << 15;
const DecoratorSet ON_PHANTOM_OOP_REF = UCONST64(1) << 16;
const DecoratorSet ON_UNKNOWN_OOP_REF = UCONST64(1) << 17;
const DecoratorSet ON_DECORATOR_MASK  = ON_STRONG_OOP_REF | ON_WEAK_OOP_REF |
                                        ON_PHANTOM_OOP_REF | ON_UNKNOWN_OOP_REF;

// === Access Location ===
// Accesses can take place in, e.g. the heap, old or young generation and different native roots.
// The location is important to the GC as it may imply different actions. The following decorators are used:
// * IN_HEAP: The access is performed in the heap. Many barriers such as card marking will
//   be omitted if this decorator is not set.
// * IN_HEAP_ARRAY: The access is performed on a heap allocated array. This is sometimes a special case
//   for some GCs, and implies that it is an IN_HEAP.
// * IN_ROOT: The access is performed in an off-heap data structure pointing into the Java heap.
// * IN_CONCURRENT_ROOT: The access is performed in an off-heap data structure pointing into the Java heap,
//   but is notably not scanned during safepoints. This is sometimes a special case for some GCs and
//   implies that it is also an IN_ROOT.
const DecoratorSet IN_HEAP            = UCONST64(1) << 18;
const DecoratorSet IN_HEAP_ARRAY      = UCONST64(1) << 19;
const DecoratorSet IN_ROOT            = UCONST64(1) << 20;
const DecoratorSet IN_CONCURRENT_ROOT = UCONST64(1) << 21;
const DecoratorSet IN_DECORATOR_MASK  = IN_HEAP | IN_HEAP_ARRAY |
                                        IN_ROOT | IN_CONCURRENT_ROOT;

// == Value Decorators ==
// * OOP_NOT_NULL: This property can make certain barriers faster such as compressing oops.
const DecoratorSet OOP_NOT_NULL       = UCONST64(1) << 22;
const DecoratorSet OOP_DECORATOR_MASK = OOP_NOT_NULL;

// == Arraycopy Decorators ==
// * ARRAYCOPY_DEST_NOT_INITIALIZED: This property can be important to e.g. SATB barriers by
//   marking that the previous value uninitialized nonsense rather than a real value.
// * ARRAYCOPY_CHECKCAST: This property means that the class of the objects in source
//   are not guaranteed to be subclasses of the class of the destination array. This requires
//   a check-cast barrier during the copying operation. If this is not set, it is assumed
//   that the array is covariant: (the source array type is-a destination array type)
// * ARRAYCOPY_DISJOINT: This property means that it is known that the two array ranges
//   are disjoint.
// * ARRAYCOPY_ARRAYOF: The copy is in the arrayof form.
// * ARRAYCOPY_ATOMIC: The accesses have to be atomic over the size of its elements.
// * ARRAYCOPY_ALIGNED: The accesses have to be aligned on a HeapWord.
const DecoratorSet ARRAYCOPY_DEST_NOT_INITIALIZED = UCONST64(1) << 24;
const DecoratorSet ARRAYCOPY_CHECKCAST            = UCONST64(1) << 25;
const DecoratorSet ARRAYCOPY_DISJOINT             = UCONST64(1) << 26;
const DecoratorSet ARRAYCOPY_ARRAYOF              = UCONST64(1) << 27;
const DecoratorSet ARRAYCOPY_ATOMIC               = UCONST64(1) << 28;
const DecoratorSet ARRAYCOPY_ALIGNED              = UCONST64(1) << 29;
const DecoratorSet ARRAYCOPY_DECORATOR_MASK       = ARRAYCOPY_DEST_NOT_INITIALIZED |
                                                    ARRAYCOPY_CHECKCAST | ARRAYCOPY_DISJOINT |
                                                    ARRAYCOPY_DISJOINT | ARRAYCOPY_ARRAYOF |
                                                    ARRAYCOPY_ATOMIC | ARRAYCOPY_ALIGNED;

// The HasDecorator trait can help at compile-time determining whether a decorator set
// has an intersection with a certain other decorator set
template <DecoratorSet decorators, DecoratorSet decorator>
struct HasDecorator: public IntegralConstant<bool, (decorators & decorator) != 0> {};

namespace AccessInternal {
  template <typename T>
  struct OopOrNarrowOopInternal: AllStatic {
    typedef oop type;
  };

  template <>
  struct OopOrNarrowOopInternal<narrowOop>: AllStatic {
    typedef narrowOop type;
  };

  // This metafunction returns a canonicalized oop/narrowOop type for a passed
  // in oop-like types passed in from oop_* overloads where the user has sworn
  // that the passed in values should be oop-like (e.g. oop, oopDesc*, arrayOop,
  // narrowOoop, instanceOopDesc*, and random other things).
  // In the oop_* overloads, it must hold that if the passed in type T is not
  // narrowOop, then it by contract has to be one of many oop-like types implicitly
  // convertible to oop, and hence returns oop as the canonical oop type.
  // If it turns out it was not, then the implicit conversion to oop will fail
  // to compile, as desired.
  template <typename T>
  struct OopOrNarrowOop: AllStatic {
    typedef typename OopOrNarrowOopInternal<typename Decay<T>::type>::type type;
  };

  inline void* field_addr(oop base, ptrdiff_t byte_offset) {
    return reinterpret_cast<void*>(reinterpret_cast<intptr_t>((void*)base) + byte_offset);
  }

  template <DecoratorSet decorators, typename T>
  void store_at(oop base, ptrdiff_t offset, T value);

  template <DecoratorSet decorators, typename T>
  T load_at(oop base, ptrdiff_t offset);

  template <DecoratorSet decorators, typename T>
  T atomic_cmpxchg_at(T new_value, oop base, ptrdiff_t offset, T compare_value);

  template <DecoratorSet decorators, typename T>
  T atomic_xchg_at(T new_value, oop base, ptrdiff_t offset);

  template <DecoratorSet decorators, typename P, typename T>
  void store(P* addr, T value);

  template <DecoratorSet decorators, typename P, typename T>
  T load(P* addr);

  template <DecoratorSet decorators, typename P, typename T>
  T atomic_cmpxchg(T new_value, P* addr, T compare_value);

  template <DecoratorSet decorators, typename P, typename T>
  T atomic_xchg(T new_value, P* addr);

  template <DecoratorSet decorators, typename T>
  bool arraycopy(arrayOop src_obj, arrayOop dst_obj, T *src, T *dst, size_t length);

  template <DecoratorSet decorators>
  void clone(oop src, oop dst, size_t size);

  // Infer the type that should be returned from a load.
  template <typename P, DecoratorSet decorators>
  class LoadProxy: public StackObj {
  private:
    P *const _addr;
  public:
    LoadProxy(P* addr) : _addr(addr) {}

    template <typename T>
    inline operator T() {
      return load<decorators, P, T>(_addr);
    }

    inline operator P() {
      return load<decorators, P, P>(_addr);
    }
  };

  // Infer the type that should be returned from a load_at.
  template <DecoratorSet decorators>
  class LoadAtProxy: public StackObj {
  private:
    const oop _base;
    const ptrdiff_t _offset;
  public:
    LoadAtProxy(oop base, ptrdiff_t offset) : _base(base), _offset(offset) {}

    template <typename T>
    inline operator T() const {
      return load_at<decorators, T>(_base, _offset);
    }
  };
}

template <DecoratorSet decorators = INTERNAL_EMPTY>
class Access: public AllStatic {
  // This function asserts that if an access gets passed in a decorator outside
  // of the expected_decorators, then something is wrong. It additionally checks
  // the consistency of the decorators so that supposedly disjoint decorators are indeed
  // disjoint. For example, an access can not be both in heap and on root at the
  // same time.
  template <DecoratorSet expected_decorators>
  static void verify_decorators();

  template <DecoratorSet expected_mo_decorators>
  static void verify_primitive_decorators() {
    const DecoratorSet primitive_decorators = (AS_DECORATOR_MASK ^ AS_NO_KEEPALIVE) | IN_HEAP |
                                               IN_HEAP_ARRAY | MO_DECORATOR_MASK;
    verify_decorators<expected_mo_decorators | primitive_decorators>();
  }

  template <DecoratorSet expected_mo_decorators>
  static void verify_oop_decorators() {
    const DecoratorSet oop_decorators = AS_DECORATOR_MASK | IN_DECORATOR_MASK |
                                        (ON_DECORATOR_MASK ^ ON_UNKNOWN_OOP_REF) | // no unknown oop refs outside of the heap
                                        OOP_DECORATOR_MASK | MO_DECORATOR_MASK;
    verify_decorators<expected_mo_decorators | oop_decorators>();
  }

  template <DecoratorSet expected_mo_decorators>
  static void verify_heap_oop_decorators() {
    const DecoratorSet heap_oop_decorators = AS_DECORATOR_MASK | ON_DECORATOR_MASK |
                                             OOP_DECORATOR_MASK | (IN_DECORATOR_MASK ^
                                                                  (IN_ROOT ^ IN_CONCURRENT_ROOT)) | // no root accesses in the heap
                                             MO_DECORATOR_MASK;
    verify_decorators<expected_mo_decorators | heap_oop_decorators>();
  }

  static const DecoratorSet load_mo_decorators = MO_UNORDERED | MO_VOLATILE | MO_RELAXED | MO_ACQUIRE | MO_SEQ_CST;
  static const DecoratorSet store_mo_decorators = MO_UNORDERED | MO_VOLATILE | MO_RELAXED | MO_RELEASE | MO_SEQ_CST;
  static const DecoratorSet atomic_xchg_mo_decorators = MO_SEQ_CST;
  static const DecoratorSet atomic_cmpxchg_mo_decorators = MO_RELAXED | MO_SEQ_CST;

public:
  // Primitive heap accesses
  static inline AccessInternal::LoadAtProxy<decorators> load_at(oop base, ptrdiff_t offset) {
    verify_primitive_decorators<load_mo_decorators>();
    return AccessInternal::LoadAtProxy<decorators>(base, offset);
  }

  template <typename T>
  static inline void store_at(oop base, ptrdiff_t offset, T value) {
    verify_primitive_decorators<store_mo_decorators>();
    AccessInternal::store_at<decorators>(base, offset, value);
  }

  template <typename T>
  static inline T atomic_cmpxchg_at(T new_value, oop base, ptrdiff_t offset, T compare_value) {
    verify_primitive_decorators<atomic_cmpxchg_mo_decorators>();
    return AccessInternal::atomic_cmpxchg_at<decorators>(new_value, base, offset, compare_value);
  }

  template <typename T>
  static inline T atomic_xchg_at(T new_value, oop base, ptrdiff_t offset) {
    verify_primitive_decorators<atomic_xchg_mo_decorators>();
    return AccessInternal::atomic_xchg_at<decorators>(new_value, base, offset);
  }

  template <typename T>
  static inline bool arraycopy(arrayOop src_obj, arrayOop dst_obj, T *src, T *dst, size_t length) {
    verify_decorators<ARRAYCOPY_DECORATOR_MASK | IN_HEAP |
                      AS_DECORATOR_MASK>();
    return AccessInternal::arraycopy<decorators>(src_obj, dst_obj, src, dst, length);
  }

  // Oop heap accesses
  static inline AccessInternal::LoadAtProxy<decorators | INTERNAL_VALUE_IS_OOP> oop_load_at(oop base, ptrdiff_t offset) {
    verify_heap_oop_decorators<load_mo_decorators>();
    return AccessInternal::LoadAtProxy<decorators | INTERNAL_VALUE_IS_OOP>(base, offset);
  }

  template <typename T>
  static inline void oop_store_at(oop base, ptrdiff_t offset, T value) {
    verify_heap_oop_decorators<store_mo_decorators>();
    typedef typename AccessInternal::OopOrNarrowOop<T>::type OopType;
    OopType oop_value = value;
    AccessInternal::store_at<decorators | INTERNAL_VALUE_IS_OOP>(base, offset, oop_value);
  }

  template <typename T>
  static inline T oop_atomic_cmpxchg_at(T new_value, oop base, ptrdiff_t offset, T compare_value) {
    verify_heap_oop_decorators<atomic_cmpxchg_mo_decorators>();
    typedef typename AccessInternal::OopOrNarrowOop<T>::type OopType;
    OopType new_oop_value = new_value;
    OopType compare_oop_value = compare_value;
    return AccessInternal::atomic_cmpxchg_at<decorators | INTERNAL_VALUE_IS_OOP>(new_oop_value, base, offset, compare_oop_value);
  }

  template <typename T>
  static inline T oop_atomic_xchg_at(T new_value, oop base, ptrdiff_t offset) {
    verify_heap_oop_decorators<atomic_xchg_mo_decorators>();
    typedef typename AccessInternal::OopOrNarrowOop<T>::type OopType;
    OopType new_oop_value = new_value;
    return AccessInternal::atomic_xchg_at<decorators | INTERNAL_VALUE_IS_OOP>(new_oop_value, base, offset);
  }

  template <typename T>
  static inline bool oop_arraycopy(arrayOop src_obj, arrayOop dst_obj, T *src, T *dst, size_t length) {
    verify_decorators<ARRAYCOPY_DECORATOR_MASK | IN_HEAP | AS_DECORATOR_MASK>();
    return AccessInternal::arraycopy<decorators | INTERNAL_VALUE_IS_OOP>(src_obj, dst_obj, src, dst, length);
  }

  // Clone an object from src to dst
  static inline void clone(oop src, oop dst, size_t size) {
    verify_decorators<IN_HEAP>();
    AccessInternal::clone<decorators>(src, dst, size);
  }

  // Primitive accesses
  template <typename P>
  static inline P load(P* addr) {
    verify_primitive_decorators<load_mo_decorators>();
    return AccessInternal::load<decorators, P, P>(addr);
  }

  template <typename P, typename T>
  static inline void store(P* addr, T value) {
    verify_primitive_decorators<store_mo_decorators>();
    AccessInternal::store<decorators>(addr, value);
  }

  template <typename P, typename T>
  static inline T atomic_cmpxchg(T new_value, P* addr, T compare_value) {
    verify_primitive_decorators<atomic_cmpxchg_mo_decorators>();
    return AccessInternal::atomic_cmpxchg<decorators>(new_value, addr, compare_value);
  }

  template <typename P, typename T>
  static inline T atomic_xchg(T new_value, P* addr) {
    verify_primitive_decorators<atomic_xchg_mo_decorators>();
    return AccessInternal::atomic_xchg<decorators>(new_value, addr);
  }

  // Oop accesses
  template <typename P>
  static inline AccessInternal::LoadProxy<P, decorators | INTERNAL_VALUE_IS_OOP> oop_load(P* addr) {
    verify_oop_decorators<load_mo_decorators>();
    return AccessInternal::LoadProxy<P, decorators | INTERNAL_VALUE_IS_OOP>(addr);
  }

  template <typename P, typename T>
  static inline void oop_store(P* addr, T value) {
    verify_oop_decorators<store_mo_decorators>();
    typedef typename AccessInternal::OopOrNarrowOop<T>::type OopType;
    OopType oop_value = value;
    AccessInternal::store<decorators | INTERNAL_VALUE_IS_OOP>(addr, oop_value);
  }

  template <typename P, typename T>
  static inline T oop_atomic_cmpxchg(T new_value, P* addr, T compare_value) {
    verify_oop_decorators<atomic_cmpxchg_mo_decorators>();
    typedef typename AccessInternal::OopOrNarrowOop<T>::type OopType;
    OopType new_oop_value = new_value;
    OopType compare_oop_value = compare_value;
    return AccessInternal::atomic_cmpxchg<decorators | INTERNAL_VALUE_IS_OOP>(new_oop_value, addr, compare_oop_value);
  }

  template <typename P, typename T>
  static inline T oop_atomic_xchg(T new_value, P* addr) {
    verify_oop_decorators<atomic_xchg_mo_decorators>();
    typedef typename AccessInternal::OopOrNarrowOop<T>::type OopType;
    OopType new_oop_value = new_value;
    return AccessInternal::atomic_xchg<decorators | INTERNAL_VALUE_IS_OOP>(new_oop_value, addr);
  }
};

// Helper for performing raw accesses (knows only of memory ordering
// atomicity decorators as well as compressed oops)
template <DecoratorSet decorators = INTERNAL_EMPTY>
class RawAccess: public Access<AS_RAW | decorators> {};

// Helper for performing normal accesses on the heap. These accesses
// may resolve an accessor on a GC barrier set
template <DecoratorSet decorators = INTERNAL_EMPTY>
class HeapAccess: public Access<IN_HEAP | decorators> {};

// Helper for performing normal accesses in roots. These accesses
// may resolve an accessor on a GC barrier set
template <DecoratorSet decorators = INTERNAL_EMPTY>
class RootAccess: public Access<IN_ROOT | decorators> {};

#endif // SHARE_VM_RUNTIME_ACCESS_HPP
