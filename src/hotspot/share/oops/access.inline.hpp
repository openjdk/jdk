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

#ifndef SHARE_VM_RUNTIME_ACCESS_INLINE_HPP
#define SHARE_VM_RUNTIME_ACCESS_INLINE_HPP

#include "gc/shared/barrierSet.inline.hpp"
#include "metaprogramming/conditional.hpp"
#include "metaprogramming/isFloatingPoint.hpp"
#include "metaprogramming/isIntegral.hpp"
#include "metaprogramming/isPointer.hpp"
#include "metaprogramming/isVolatile.hpp"
#include "oops/access.hpp"
#include "oops/accessBackend.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.inline.hpp"

// This file outlines the template pipeline of accesses going through the Access
// API. There are essentially 5 steps for each access.
// * Step 1: Set default decorators and decay types. This step gets rid of CV qualifiers
//           and sets default decorators to sensible values.
// * Step 2: Reduce types. This step makes sure there is only a single T type and not
//           multiple types. The P type of the address and T type of the value must
//           match.
// * Step 3: Pre-runtime dispatch. This step checks whether a runtime call can be
//           avoided, and in that case avoids it (calling raw accesses or
//           primitive accesses in a build that does not require primitive GC barriers)
// * Step 4: Runtime-dispatch. This step performs a runtime dispatch to the corresponding
//           BarrierSet::AccessBarrier accessor that attaches GC-required barriers
//           to the access.
// * Step 5: Post-runtime dispatch. This step now casts previously unknown types such
//           as the address type of an oop on the heap (is it oop* or narrowOop*) to
//           the appropriate type. It also splits sufficiently orthogonal accesses into
//           different functions, such as whether the access involves oops or primitives
//           and whether the access is performed on the heap or outside. Then the
//           appropriate BarrierSet::AccessBarrier is called to perform the access.

namespace AccessInternal {

  // Step 5: Post-runtime dispatch.
  // This class is the last step before calling the BarrierSet::AccessBarrier.
  // Here we make sure to figure out types that were not known prior to the
  // runtime dispatch, such as whether an oop on the heap is oop or narrowOop.
  // We also split orthogonal barriers such as handling primitives vs oops
  // and on-heap vs off-heap into different calls to the barrier set.
  template <class GCBarrierType, BarrierType type, DecoratorSet decorators>
  struct PostRuntimeDispatch: public AllStatic { };

  template <class GCBarrierType, DecoratorSet decorators>
  struct PostRuntimeDispatch<GCBarrierType, BARRIER_STORE, decorators>: public AllStatic {
    template <typename T>
    static void access_barrier(void* addr, T value) {
      GCBarrierType::store_in_heap(reinterpret_cast<T*>(addr), value);
    }

    static void oop_access_barrier(void* addr, oop value) {
      typedef typename HeapOopType<decorators>::type OopType;
      if (HasDecorator<decorators, IN_HEAP>::value) {
        GCBarrierType::oop_store_in_heap(reinterpret_cast<OopType*>(addr), value);
      } else {
        GCBarrierType::oop_store_not_in_heap(reinterpret_cast<OopType*>(addr), value);
      }
    }
  };

  template <class GCBarrierType, DecoratorSet decorators>
  struct PostRuntimeDispatch<GCBarrierType, BARRIER_LOAD, decorators>: public AllStatic {
    template <typename T>
    static T access_barrier(void* addr) {
      return GCBarrierType::load_in_heap(reinterpret_cast<T*>(addr));
    }

    static oop oop_access_barrier(void* addr) {
      typedef typename HeapOopType<decorators>::type OopType;
      if (HasDecorator<decorators, IN_HEAP>::value) {
        return GCBarrierType::oop_load_in_heap(reinterpret_cast<OopType*>(addr));
      } else {
        return GCBarrierType::oop_load_not_in_heap(reinterpret_cast<OopType*>(addr));
      }
    }
  };

  template <class GCBarrierType, DecoratorSet decorators>
  struct PostRuntimeDispatch<GCBarrierType, BARRIER_ATOMIC_XCHG, decorators>: public AllStatic {
    template <typename T>
    static T access_barrier(T new_value, void* addr) {
      return GCBarrierType::atomic_xchg_in_heap(new_value, reinterpret_cast<T*>(addr));
    }

    static oop oop_access_barrier(oop new_value, void* addr) {
      typedef typename HeapOopType<decorators>::type OopType;
      if (HasDecorator<decorators, IN_HEAP>::value) {
        return GCBarrierType::oop_atomic_xchg_in_heap(new_value, reinterpret_cast<OopType*>(addr));
      } else {
        return GCBarrierType::oop_atomic_xchg_not_in_heap(new_value, reinterpret_cast<OopType*>(addr));
      }
    }
  };

  template <class GCBarrierType, DecoratorSet decorators>
  struct PostRuntimeDispatch<GCBarrierType, BARRIER_ATOMIC_CMPXCHG, decorators>: public AllStatic {
    template <typename T>
    static T access_barrier(T new_value, void* addr, T compare_value) {
      return GCBarrierType::atomic_cmpxchg_in_heap(new_value, reinterpret_cast<T*>(addr), compare_value);
    }

    static oop oop_access_barrier(oop new_value, void* addr, oop compare_value) {
      typedef typename HeapOopType<decorators>::type OopType;
      if (HasDecorator<decorators, IN_HEAP>::value) {
        return GCBarrierType::oop_atomic_cmpxchg_in_heap(new_value, reinterpret_cast<OopType*>(addr), compare_value);
      } else {
        return GCBarrierType::oop_atomic_cmpxchg_not_in_heap(new_value, reinterpret_cast<OopType*>(addr), compare_value);
      }
    }
  };

  template <class GCBarrierType, DecoratorSet decorators>
  struct PostRuntimeDispatch<GCBarrierType, BARRIER_ARRAYCOPY, decorators>: public AllStatic {
    template <typename T>
    static bool access_barrier(arrayOop src_obj, arrayOop dst_obj, T* src, T* dst, size_t length) {
      return GCBarrierType::arraycopy_in_heap(src_obj, dst_obj, src, dst, length);
    }

    template <typename T>
    static bool oop_access_barrier(arrayOop src_obj, arrayOop dst_obj, T* src, T* dst, size_t length) {
      typedef typename HeapOopType<decorators>::type OopType;
      return GCBarrierType::oop_arraycopy_in_heap(src_obj, dst_obj,
                                                  reinterpret_cast<OopType*>(src),
                                                  reinterpret_cast<OopType*>(dst), length);
    }
  };

  template <class GCBarrierType, DecoratorSet decorators>
  struct PostRuntimeDispatch<GCBarrierType, BARRIER_STORE_AT, decorators>: public AllStatic {
    template <typename T>
    static void access_barrier(oop base, ptrdiff_t offset, T value) {
      GCBarrierType::store_in_heap_at(base, offset, value);
    }

    static void oop_access_barrier(oop base, ptrdiff_t offset, oop value) {
      GCBarrierType::oop_store_in_heap_at(base, offset, value);
    }
  };

  template <class GCBarrierType, DecoratorSet decorators>
  struct PostRuntimeDispatch<GCBarrierType, BARRIER_LOAD_AT, decorators>: public AllStatic {
    template <typename T>
    static T access_barrier(oop base, ptrdiff_t offset) {
      return GCBarrierType::template load_in_heap_at<T>(base, offset);
    }

    static oop oop_access_barrier(oop base, ptrdiff_t offset) {
      return GCBarrierType::oop_load_in_heap_at(base, offset);
    }
  };

  template <class GCBarrierType, DecoratorSet decorators>
  struct PostRuntimeDispatch<GCBarrierType, BARRIER_ATOMIC_XCHG_AT, decorators>: public AllStatic {
    template <typename T>
    static T access_barrier(T new_value, oop base, ptrdiff_t offset) {
      return GCBarrierType::atomic_xchg_in_heap_at(new_value, base, offset);
    }

    static oop oop_access_barrier(oop new_value, oop base, ptrdiff_t offset) {
      return GCBarrierType::oop_atomic_xchg_in_heap_at(new_value, base, offset);
    }
  };

  template <class GCBarrierType, DecoratorSet decorators>
  struct PostRuntimeDispatch<GCBarrierType, BARRIER_ATOMIC_CMPXCHG_AT, decorators>: public AllStatic {
    template <typename T>
    static T access_barrier(T new_value, oop base, ptrdiff_t offset, T compare_value) {
      return GCBarrierType::atomic_cmpxchg_in_heap_at(new_value, base, offset, compare_value);
    }

    static oop oop_access_barrier(oop new_value, oop base, ptrdiff_t offset, oop compare_value) {
      return GCBarrierType::oop_atomic_cmpxchg_in_heap_at(new_value, base, offset, compare_value);
    }
  };

  template <class GCBarrierType, DecoratorSet decorators>
  struct PostRuntimeDispatch<GCBarrierType, BARRIER_CLONE, decorators>: public AllStatic {
    static void access_barrier(oop src, oop dst, size_t size) {
      GCBarrierType::clone_in_heap(src, dst, size);
    }
  };

  // Resolving accessors with barriers from the barrier set happens in two steps.
  // 1. Expand paths with runtime-decorators, e.g. is UseCompressedOops on or off.
  // 2. Expand paths for each BarrierSet available in the system.
  template <DecoratorSet decorators, typename FunctionPointerT, BarrierType barrier_type>
  struct BarrierResolver: public AllStatic {
    template <DecoratorSet ds>
    static typename EnableIf<
      HasDecorator<ds, INTERNAL_VALUE_IS_OOP>::value,
      FunctionPointerT>::type
    resolve_barrier_gc() {
      BarrierSet* bs = BarrierSet::barrier_set();
      assert(bs != NULL, "GC barriers invoked before BarrierSet is set");
      switch (bs->kind()) {
#define BARRIER_SET_RESOLVE_BARRIER_CLOSURE(bs_name)                    \
        case BarrierSet::bs_name: {                                     \
          return PostRuntimeDispatch<typename BarrierSet::GetType<BarrierSet::bs_name>::type:: \
            AccessBarrier<ds>, barrier_type, ds>::oop_access_barrier; \
        }                                                               \
        break;
        FOR_EACH_CONCRETE_BARRIER_SET_DO(BARRIER_SET_RESOLVE_BARRIER_CLOSURE)
#undef BARRIER_SET_RESOLVE_BARRIER_CLOSURE

      default:
        fatal("BarrierSet AccessBarrier resolving not implemented");
        return NULL;
      };
    }

    template <DecoratorSet ds>
    static typename EnableIf<
      !HasDecorator<ds, INTERNAL_VALUE_IS_OOP>::value,
      FunctionPointerT>::type
    resolve_barrier_gc() {
      BarrierSet* bs = BarrierSet::barrier_set();
      assert(bs != NULL, "GC barriers invoked before BarrierSet is set");
      switch (bs->kind()) {
#define BARRIER_SET_RESOLVE_BARRIER_CLOSURE(bs_name)                    \
        case BarrierSet::bs_name: {                                       \
          return PostRuntimeDispatch<typename BarrierSet::GetType<BarrierSet::bs_name>::type:: \
            AccessBarrier<ds>, barrier_type, ds>::access_barrier; \
        }                                                                 \
        break;
        FOR_EACH_CONCRETE_BARRIER_SET_DO(BARRIER_SET_RESOLVE_BARRIER_CLOSURE)
#undef BARRIER_SET_RESOLVE_BARRIER_CLOSURE

      default:
        fatal("BarrierSet AccessBarrier resolving not implemented");
        return NULL;
      };
    }

    static FunctionPointerT resolve_barrier_rt() {
      if (UseCompressedOops) {
        const DecoratorSet expanded_decorators = decorators | INTERNAL_RT_USE_COMPRESSED_OOPS;
        return resolve_barrier_gc<expanded_decorators>();
      } else {
        return resolve_barrier_gc<decorators>();
      }
    }

    static FunctionPointerT resolve_barrier() {
      return resolve_barrier_rt();
    }
  };

  // Step 4: Runtime dispatch
  // The RuntimeDispatch class is responsible for performing a runtime dispatch of the
  // accessor. This is required when the access either depends on whether compressed oops
  // is being used, or it depends on which GC implementation was chosen (e.g. requires GC
  // barriers). The way it works is that a function pointer initially pointing to an
  // accessor resolution function gets called for each access. Upon first invocation,
  // it resolves which accessor to be used in future invocations and patches the
  // function pointer to this new accessor.

  template <DecoratorSet decorators, typename T, BarrierType type>
  struct RuntimeDispatch: AllStatic {};

  template <DecoratorSet decorators, typename T>
  struct RuntimeDispatch<decorators, T, BARRIER_STORE>: AllStatic {
    typedef typename AccessFunction<decorators, T, BARRIER_STORE>::type func_t;
    static func_t _store_func;

    static void store_init(void* addr, T value) {
      func_t function = BarrierResolver<decorators, func_t, BARRIER_STORE>::resolve_barrier();
      _store_func = function;
      function(addr, value);
    }

    static inline void store(void* addr, T value) {
      _store_func(addr, value);
    }
  };

  template <DecoratorSet decorators, typename T>
  struct RuntimeDispatch<decorators, T, BARRIER_STORE_AT>: AllStatic {
    typedef typename AccessFunction<decorators, T, BARRIER_STORE_AT>::type func_t;
    static func_t _store_at_func;

    static void store_at_init(oop base, ptrdiff_t offset, T value) {
      func_t function = BarrierResolver<decorators, func_t, BARRIER_STORE_AT>::resolve_barrier();
      _store_at_func = function;
      function(base, offset, value);
    }

    static inline void store_at(oop base, ptrdiff_t offset, T value) {
      _store_at_func(base, offset, value);
    }
  };

  template <DecoratorSet decorators, typename T>
  struct RuntimeDispatch<decorators, T, BARRIER_LOAD>: AllStatic {
    typedef typename AccessFunction<decorators, T, BARRIER_LOAD>::type func_t;
    static func_t _load_func;

    static T load_init(void* addr) {
      func_t function = BarrierResolver<decorators, func_t, BARRIER_LOAD>::resolve_barrier();
      _load_func = function;
      return function(addr);
    }

    static inline T load(void* addr) {
      return _load_func(addr);
    }
  };

  template <DecoratorSet decorators, typename T>
  struct RuntimeDispatch<decorators, T, BARRIER_LOAD_AT>: AllStatic {
    typedef typename AccessFunction<decorators, T, BARRIER_LOAD_AT>::type func_t;
    static func_t _load_at_func;

    static T load_at_init(oop base, ptrdiff_t offset) {
      func_t function = BarrierResolver<decorators, func_t, BARRIER_LOAD_AT>::resolve_barrier();
      _load_at_func = function;
      return function(base, offset);
    }

    static inline T load_at(oop base, ptrdiff_t offset) {
      return _load_at_func(base, offset);
    }
  };

  template <DecoratorSet decorators, typename T>
  struct RuntimeDispatch<decorators, T, BARRIER_ATOMIC_CMPXCHG>: AllStatic {
    typedef typename AccessFunction<decorators, T, BARRIER_ATOMIC_CMPXCHG>::type func_t;
    static func_t _atomic_cmpxchg_func;

    static T atomic_cmpxchg_init(T new_value, void* addr, T compare_value) {
      func_t function = BarrierResolver<decorators, func_t, BARRIER_ATOMIC_CMPXCHG>::resolve_barrier();
      _atomic_cmpxchg_func = function;
      return function(new_value, addr, compare_value);
    }

    static inline T atomic_cmpxchg(T new_value, void* addr, T compare_value) {
      return _atomic_cmpxchg_func(new_value, addr, compare_value);
    }
  };

  template <DecoratorSet decorators, typename T>
  struct RuntimeDispatch<decorators, T, BARRIER_ATOMIC_CMPXCHG_AT>: AllStatic {
    typedef typename AccessFunction<decorators, T, BARRIER_ATOMIC_CMPXCHG_AT>::type func_t;
    static func_t _atomic_cmpxchg_at_func;

    static T atomic_cmpxchg_at_init(T new_value, oop base, ptrdiff_t offset, T compare_value) {
      func_t function = BarrierResolver<decorators, func_t, BARRIER_ATOMIC_CMPXCHG_AT>::resolve_barrier();
      _atomic_cmpxchg_at_func = function;
      return function(new_value, base, offset, compare_value);
    }

    static inline T atomic_cmpxchg_at(T new_value, oop base, ptrdiff_t offset, T compare_value) {
      return _atomic_cmpxchg_at_func(new_value, base, offset, compare_value);
    }
  };

  template <DecoratorSet decorators, typename T>
  struct RuntimeDispatch<decorators, T, BARRIER_ATOMIC_XCHG>: AllStatic {
    typedef typename AccessFunction<decorators, T, BARRIER_ATOMIC_XCHG>::type func_t;
    static func_t _atomic_xchg_func;

    static T atomic_xchg_init(T new_value, void* addr) {
      func_t function = BarrierResolver<decorators, func_t, BARRIER_ATOMIC_XCHG>::resolve_barrier();
      _atomic_xchg_func = function;
      return function(new_value, addr);
    }

    static inline T atomic_xchg(T new_value, void* addr) {
      return _atomic_xchg_func(new_value, addr);
    }
  };

  template <DecoratorSet decorators, typename T>
  struct RuntimeDispatch<decorators, T, BARRIER_ATOMIC_XCHG_AT>: AllStatic {
    typedef typename AccessFunction<decorators, T, BARRIER_ATOMIC_XCHG_AT>::type func_t;
    static func_t _atomic_xchg_at_func;

    static T atomic_xchg_at_init(T new_value, oop base, ptrdiff_t offset) {
      func_t function = BarrierResolver<decorators, func_t, BARRIER_ATOMIC_XCHG_AT>::resolve_barrier();
      _atomic_xchg_at_func = function;
      return function(new_value, base, offset);
    }

    static inline T atomic_xchg_at(T new_value, oop base, ptrdiff_t offset) {
      return _atomic_xchg_at_func(new_value, base, offset);
    }
  };

  template <DecoratorSet decorators, typename T>
  struct RuntimeDispatch<decorators, T, BARRIER_ARRAYCOPY>: AllStatic {
    typedef typename AccessFunction<decorators, T, BARRIER_ARRAYCOPY>::type func_t;
    static func_t _arraycopy_func;

    static bool arraycopy_init(arrayOop src_obj, arrayOop dst_obj, T *src, T* dst, size_t length) {
      func_t function = BarrierResolver<decorators, func_t, BARRIER_ARRAYCOPY>::resolve_barrier();
      _arraycopy_func = function;
      return function(src_obj, dst_obj, src, dst, length);
    }

    static inline bool arraycopy(arrayOop src_obj, arrayOop dst_obj, T *src, T* dst, size_t length) {
      return _arraycopy_func(src_obj, dst_obj, src, dst, length);
    }
  };

  template <DecoratorSet decorators, typename T>
  struct RuntimeDispatch<decorators, T, BARRIER_CLONE>: AllStatic {
    typedef typename AccessFunction<decorators, T, BARRIER_CLONE>::type func_t;
    static func_t _clone_func;

    static void clone_init(oop src, oop dst, size_t size) {
      func_t function = BarrierResolver<decorators, func_t, BARRIER_CLONE>::resolve_barrier();
      _clone_func = function;
      function(src, dst, size);
    }

    static inline void clone(oop src, oop dst, size_t size) {
      _clone_func(src, dst, size);
    }
  };

  // Initialize the function pointers to point to the resolving function.
  template <DecoratorSet decorators, typename T>
  typename AccessFunction<decorators, T, BARRIER_STORE>::type
  RuntimeDispatch<decorators, T, BARRIER_STORE>::_store_func = &store_init;

  template <DecoratorSet decorators, typename T>
  typename AccessFunction<decorators, T, BARRIER_STORE_AT>::type
  RuntimeDispatch<decorators, T, BARRIER_STORE_AT>::_store_at_func = &store_at_init;

  template <DecoratorSet decorators, typename T>
  typename AccessFunction<decorators, T, BARRIER_LOAD>::type
  RuntimeDispatch<decorators, T, BARRIER_LOAD>::_load_func = &load_init;

  template <DecoratorSet decorators, typename T>
  typename AccessFunction<decorators, T, BARRIER_LOAD_AT>::type
  RuntimeDispatch<decorators, T, BARRIER_LOAD_AT>::_load_at_func = &load_at_init;

  template <DecoratorSet decorators, typename T>
  typename AccessFunction<decorators, T, BARRIER_ATOMIC_CMPXCHG>::type
  RuntimeDispatch<decorators, T, BARRIER_ATOMIC_CMPXCHG>::_atomic_cmpxchg_func = &atomic_cmpxchg_init;

  template <DecoratorSet decorators, typename T>
  typename AccessFunction<decorators, T, BARRIER_ATOMIC_CMPXCHG_AT>::type
  RuntimeDispatch<decorators, T, BARRIER_ATOMIC_CMPXCHG_AT>::_atomic_cmpxchg_at_func = &atomic_cmpxchg_at_init;

  template <DecoratorSet decorators, typename T>
  typename AccessFunction<decorators, T, BARRIER_ATOMIC_XCHG>::type
  RuntimeDispatch<decorators, T, BARRIER_ATOMIC_XCHG>::_atomic_xchg_func = &atomic_xchg_init;

  template <DecoratorSet decorators, typename T>
  typename AccessFunction<decorators, T, BARRIER_ATOMIC_XCHG_AT>::type
  RuntimeDispatch<decorators, T, BARRIER_ATOMIC_XCHG_AT>::_atomic_xchg_at_func = &atomic_xchg_at_init;

  template <DecoratorSet decorators, typename T>
  typename AccessFunction<decorators, T, BARRIER_ARRAYCOPY>::type
  RuntimeDispatch<decorators, T, BARRIER_ARRAYCOPY>::_arraycopy_func = &arraycopy_init;

  template <DecoratorSet decorators, typename T>
  typename AccessFunction<decorators, T, BARRIER_CLONE>::type
  RuntimeDispatch<decorators, T, BARRIER_CLONE>::_clone_func = &clone_init;

  // Step 3: Pre-runtime dispatching.
  // The PreRuntimeDispatch class is responsible for filtering the barrier strength
  // decorators. That is, for AS_RAW, it hardwires the accesses without a runtime
  // dispatch point. Otherwise it goes through a runtime check if hardwiring was
  // not possible.
  struct PreRuntimeDispatch: AllStatic {
    template<DecoratorSet decorators>
    static bool can_hardwire_raw() {
      return !HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value || // primitive access
             !HasDecorator<decorators, INTERNAL_CONVERT_COMPRESSED_OOP>::value || // don't care about compressed oops (oop* address)
             HasDecorator<decorators, INTERNAL_RT_USE_COMPRESSED_OOPS>::value; // we can infer we use compressed oops (narrowOop* address)
    }

    static const DecoratorSet convert_compressed_oops = INTERNAL_RT_USE_COMPRESSED_OOPS | INTERNAL_CONVERT_COMPRESSED_OOP;

    template<DecoratorSet decorators>
    static bool is_hardwired_primitive() {
      return !HasDecorator<decorators, INTERNAL_BT_BARRIER_ON_PRIMITIVES>::value &&
             !HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value;
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      HasDecorator<decorators, AS_RAW>::value>::type
    store(void* addr, T value) {
      typedef RawAccessBarrier<decorators & RAW_DECORATOR_MASK> Raw;
      if (can_hardwire_raw<decorators>()) {
        if (HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value) {
          Raw::oop_store(addr, value);
        } else {
          Raw::store(addr, value);
        }
      } else if (UseCompressedOops) {
        const DecoratorSet expanded_decorators = decorators | convert_compressed_oops;
        PreRuntimeDispatch::store<expanded_decorators>(addr, value);
      } else {
        const DecoratorSet expanded_decorators = decorators & ~convert_compressed_oops;
        PreRuntimeDispatch::store<expanded_decorators>(addr, value);
      }
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      !HasDecorator<decorators, AS_RAW>::value>::type
    store(void* addr, T value) {
      if (is_hardwired_primitive<decorators>()) {
        const DecoratorSet expanded_decorators = decorators | AS_RAW;
        PreRuntimeDispatch::store<expanded_decorators>(addr, value);
      } else {
        RuntimeDispatch<decorators, T, BARRIER_STORE>::store(addr, value);
      }
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      HasDecorator<decorators, AS_RAW>::value>::type
    store_at(oop base, ptrdiff_t offset, T value) {
      store<decorators>(field_addr(base, offset), value);
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      !HasDecorator<decorators, AS_RAW>::value>::type
    store_at(oop base, ptrdiff_t offset, T value) {
      if (is_hardwired_primitive<decorators>()) {
        const DecoratorSet expanded_decorators = decorators | AS_RAW;
        PreRuntimeDispatch::store_at<expanded_decorators>(base, offset, value);
      } else {
        RuntimeDispatch<decorators, T, BARRIER_STORE_AT>::store_at(base, offset, value);
      }
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      HasDecorator<decorators, AS_RAW>::value, T>::type
    load(void* addr) {
      typedef RawAccessBarrier<decorators & RAW_DECORATOR_MASK> Raw;
      if (can_hardwire_raw<decorators>()) {
        if (HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value) {
          return Raw::template oop_load<T>(addr);
        } else {
          return Raw::template load<T>(addr);
        }
      } else if (UseCompressedOops) {
        const DecoratorSet expanded_decorators = decorators | convert_compressed_oops;
        return PreRuntimeDispatch::load<expanded_decorators, T>(addr);
      } else {
        const DecoratorSet expanded_decorators = decorators & ~convert_compressed_oops;
        return PreRuntimeDispatch::load<expanded_decorators, T>(addr);
      }
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      !HasDecorator<decorators, AS_RAW>::value, T>::type
    load(void* addr) {
      if (is_hardwired_primitive<decorators>()) {
        const DecoratorSet expanded_decorators = decorators | AS_RAW;
        return PreRuntimeDispatch::load<expanded_decorators, T>(addr);
      } else {
        return RuntimeDispatch<decorators, T, BARRIER_LOAD>::load(addr);
      }
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      HasDecorator<decorators, AS_RAW>::value, T>::type
    load_at(oop base, ptrdiff_t offset) {
      return load<decorators, T>(field_addr(base, offset));
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      !HasDecorator<decorators, AS_RAW>::value, T>::type
    load_at(oop base, ptrdiff_t offset) {
      if (is_hardwired_primitive<decorators>()) {
        const DecoratorSet expanded_decorators = decorators | AS_RAW;
        return PreRuntimeDispatch::load_at<expanded_decorators, T>(base, offset);
      } else {
        return RuntimeDispatch<decorators, T, BARRIER_LOAD_AT>::load_at(base, offset);
      }
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      HasDecorator<decorators, AS_RAW>::value, T>::type
    atomic_cmpxchg(T new_value, void* addr, T compare_value) {
      typedef RawAccessBarrier<decorators & RAW_DECORATOR_MASK> Raw;
      if (can_hardwire_raw<decorators>()) {
        if (HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value) {
          return Raw::oop_atomic_cmpxchg(new_value, addr, compare_value);
        } else {
          return Raw::atomic_cmpxchg(new_value, addr, compare_value);
        }
      } else if (UseCompressedOops) {
        const DecoratorSet expanded_decorators = decorators | convert_compressed_oops;
        return PreRuntimeDispatch::atomic_cmpxchg<expanded_decorators>(new_value, addr, compare_value);
      } else {
        const DecoratorSet expanded_decorators = decorators & ~convert_compressed_oops;
        return PreRuntimeDispatch::atomic_cmpxchg<expanded_decorators>(new_value, addr, compare_value);
      }
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      !HasDecorator<decorators, AS_RAW>::value, T>::type
    atomic_cmpxchg(T new_value, void* addr, T compare_value) {
      typedef RawAccessBarrier<decorators & RAW_DECORATOR_MASK> Raw;
      if (is_hardwired_primitive<decorators>()) {
        const DecoratorSet expanded_decorators = decorators | AS_RAW;
        return PreRuntimeDispatch::atomic_cmpxchg<expanded_decorators>(new_value, addr, compare_value);
      } else {
        return RuntimeDispatch<decorators, T, BARRIER_ATOMIC_CMPXCHG>::atomic_cmpxchg(new_value, addr, compare_value);
      }
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      HasDecorator<decorators, AS_RAW>::value, T>::type
    atomic_cmpxchg_at(T new_value, oop base, ptrdiff_t offset, T compare_value) {
      return atomic_cmpxchg<decorators>(new_value, field_addr(base, offset), compare_value);
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      !HasDecorator<decorators, AS_RAW>::value, T>::type
    atomic_cmpxchg_at(T new_value, oop base, ptrdiff_t offset, T compare_value) {
      if (is_hardwired_primitive<decorators>()) {
        const DecoratorSet expanded_decorators = decorators | AS_RAW;
        return PreRuntimeDispatch::atomic_cmpxchg_at<expanded_decorators>(new_value, base, offset, compare_value);
      } else {
        return RuntimeDispatch<decorators, T, BARRIER_ATOMIC_CMPXCHG_AT>::atomic_cmpxchg_at(new_value, base, offset, compare_value);
      }
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      HasDecorator<decorators, AS_RAW>::value, T>::type
    atomic_xchg(T new_value, void* addr) {
      typedef RawAccessBarrier<decorators & RAW_DECORATOR_MASK> Raw;
      if (can_hardwire_raw<decorators>()) {
        if (HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value) {
          return Raw::oop_atomic_xchg(new_value, addr);
        } else {
          return Raw::atomic_xchg(new_value, addr);
        }
      } else if (UseCompressedOops) {
        const DecoratorSet expanded_decorators = decorators | convert_compressed_oops;
        return PreRuntimeDispatch::atomic_xchg<expanded_decorators>(new_value, addr);
      } else {
        const DecoratorSet expanded_decorators = decorators & ~convert_compressed_oops;
        return PreRuntimeDispatch::atomic_xchg<expanded_decorators>(new_value, addr);
      }
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      !HasDecorator<decorators, AS_RAW>::value, T>::type
    atomic_xchg(T new_value, void* addr) {
      if (is_hardwired_primitive<decorators>()) {
        const DecoratorSet expanded_decorators = decorators | AS_RAW;
        return PreRuntimeDispatch::atomic_xchg<expanded_decorators>(new_value, addr);
      } else {
        return RuntimeDispatch<decorators, T, BARRIER_ATOMIC_XCHG>::atomic_xchg(new_value, addr);
      }
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      HasDecorator<decorators, AS_RAW>::value, T>::type
    atomic_xchg_at(T new_value, oop base, ptrdiff_t offset) {
      return atomic_xchg<decorators>(new_value, field_addr(base, offset));
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      !HasDecorator<decorators, AS_RAW>::value, T>::type
    atomic_xchg_at(T new_value, oop base, ptrdiff_t offset) {
      if (is_hardwired_primitive<decorators>()) {
        const DecoratorSet expanded_decorators = decorators | AS_RAW;
        return PreRuntimeDispatch::atomic_xchg<expanded_decorators>(new_value, base, offset);
      } else {
        return RuntimeDispatch<decorators, T, BARRIER_ATOMIC_XCHG_AT>::atomic_xchg_at(new_value, base, offset);
      }
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      HasDecorator<decorators, AS_RAW>::value, bool>::type
    arraycopy(arrayOop src_obj, arrayOop dst_obj, T *src, T* dst, size_t length) {
      typedef RawAccessBarrier<decorators & RAW_DECORATOR_MASK> Raw;
      return Raw::arraycopy(src, dst, length);
    }

    template <DecoratorSet decorators, typename T>
    inline static typename EnableIf<
      !HasDecorator<decorators, AS_RAW>::value, bool>::type
    arraycopy(arrayOop src_obj, arrayOop dst_obj, T *src, T* dst, size_t length) {
      typedef RawAccessBarrier<decorators & RAW_DECORATOR_MASK> Raw;
      if (is_hardwired_primitive<decorators>()) {
        const DecoratorSet expanded_decorators = decorators | AS_RAW;
        return PreRuntimeDispatch::arraycopy<expanded_decorators>(src_obj, dst_obj, src, dst, length);
      } else {
        return RuntimeDispatch<decorators, T, BARRIER_ARRAYCOPY>::arraycopy(src_obj, dst_obj, src, dst, length);
      }
    }

    template <DecoratorSet decorators>
    inline static typename EnableIf<
      HasDecorator<decorators, AS_RAW>::value>::type
    clone(oop src, oop dst, size_t size) {
      typedef RawAccessBarrier<decorators & RAW_DECORATOR_MASK> Raw;
      Raw::clone(src, dst, size);
    }

    template <DecoratorSet decorators>
    inline static typename EnableIf<
      !HasDecorator<decorators, AS_RAW>::value>::type
    clone(oop src, oop dst, size_t size) {
      RuntimeDispatch<decorators, oop, BARRIER_CLONE>::clone(src, dst, size);
    }
  };

  // This class adds implied decorators that follow according to decorator rules.
  // For example adding default reference strength and default memory ordering
  // semantics.
  template <DecoratorSet input_decorators>
  struct DecoratorFixup: AllStatic {
    // If no reference strength has been picked, then strong will be picked
    static const DecoratorSet ref_strength_default = input_decorators |
      (((ON_DECORATOR_MASK & input_decorators) == 0 && (INTERNAL_VALUE_IS_OOP & input_decorators) != 0) ?
       ON_STRONG_OOP_REF : INTERNAL_EMPTY);
    // If no memory ordering has been picked, unordered will be picked
    static const DecoratorSet memory_ordering_default = ref_strength_default |
      ((MO_DECORATOR_MASK & ref_strength_default) == 0 ? MO_UNORDERED : INTERNAL_EMPTY);
    // If no barrier strength has been picked, normal will be used
    static const DecoratorSet barrier_strength_default = memory_ordering_default |
      ((AS_DECORATOR_MASK & memory_ordering_default) == 0 ? AS_NORMAL : INTERNAL_EMPTY);
    // Heap array accesses imply it is a heap access
    static const DecoratorSet heap_array_is_in_heap = barrier_strength_default |
      ((IN_HEAP_ARRAY & barrier_strength_default) != 0 ? IN_HEAP : INTERNAL_EMPTY);
    static const DecoratorSet conc_root_is_root = heap_array_is_in_heap |
      ((IN_CONCURRENT_ROOT & heap_array_is_in_heap) != 0 ? IN_ROOT : INTERNAL_EMPTY);
    static const DecoratorSet value = conc_root_is_root | BT_BUILDTIME_DECORATORS;
  };

  // Step 2: Reduce types.
  // Enforce that for non-oop types, T and P have to be strictly the same.
  // P is the type of the address and T is the type of the values.
  // As for oop types, it is allow to send T in {narrowOop, oop} and
  // P in {narrowOop, oop, HeapWord*}. The following rules apply according to
  // the subsequent table. (columns are P, rows are T)
  // |           | HeapWord  |   oop   | narrowOop |
  // |   oop     |  rt-comp  | hw-none |  hw-comp  |
  // | narrowOop |     x     |    x    |  hw-none  |
  //
  // x means not allowed
  // rt-comp means it must be checked at runtime whether the oop is compressed.
  // hw-none means it is statically known the oop will not be compressed.
  // hw-comp means it is statically known the oop will be compressed.

  template <DecoratorSet decorators, typename T>
  inline void store_reduce_types(T* addr, T value) {
    PreRuntimeDispatch::store<decorators>(addr, value);
  }

  template <DecoratorSet decorators>
  inline void store_reduce_types(narrowOop* addr, oop value) {
    const DecoratorSet expanded_decorators = decorators | INTERNAL_CONVERT_COMPRESSED_OOP |
                                             INTERNAL_RT_USE_COMPRESSED_OOPS;
    PreRuntimeDispatch::store<expanded_decorators>(addr, value);
  }

  template <DecoratorSet decorators>
  inline void store_reduce_types(HeapWord* addr, oop value) {
    const DecoratorSet expanded_decorators = decorators | INTERNAL_CONVERT_COMPRESSED_OOP;
    PreRuntimeDispatch::store<expanded_decorators>(addr, value);
  }

  template <DecoratorSet decorators, typename T>
  inline T atomic_cmpxchg_reduce_types(T new_value, T* addr, T compare_value) {
    return PreRuntimeDispatch::atomic_cmpxchg<decorators>(new_value, addr, compare_value);
  }

  template <DecoratorSet decorators>
  inline oop atomic_cmpxchg_reduce_types(oop new_value, narrowOop* addr, oop compare_value) {
    const DecoratorSet expanded_decorators = decorators | INTERNAL_CONVERT_COMPRESSED_OOP |
                                             INTERNAL_RT_USE_COMPRESSED_OOPS;
    return PreRuntimeDispatch::atomic_cmpxchg<expanded_decorators>(new_value, addr, compare_value);
  }

  template <DecoratorSet decorators>
  inline oop atomic_cmpxchg_reduce_types(oop new_value, HeapWord* addr, oop compare_value) {
    const DecoratorSet expanded_decorators = decorators | INTERNAL_CONVERT_COMPRESSED_OOP;
    return PreRuntimeDispatch::atomic_cmpxchg<expanded_decorators>(new_value, addr, compare_value);
  }

  template <DecoratorSet decorators, typename T>
  inline T atomic_xchg_reduce_types(T new_value, T* addr) {
    const DecoratorSet expanded_decorators = decorators;
    return PreRuntimeDispatch::atomic_xchg<expanded_decorators>(new_value, addr);
  }

  template <DecoratorSet decorators>
  inline oop atomic_xchg_reduce_types(oop new_value, narrowOop* addr) {
    const DecoratorSet expanded_decorators = decorators | INTERNAL_CONVERT_COMPRESSED_OOP |
                                             INTERNAL_RT_USE_COMPRESSED_OOPS;
    return PreRuntimeDispatch::atomic_xchg<expanded_decorators>(new_value, addr);
  }

  template <DecoratorSet decorators>
  inline oop atomic_xchg_reduce_types(oop new_value, HeapWord* addr) {
    const DecoratorSet expanded_decorators = decorators | INTERNAL_CONVERT_COMPRESSED_OOP;
    return PreRuntimeDispatch::atomic_xchg<expanded_decorators>(new_value, addr);
  }

  template <DecoratorSet decorators, typename T>
  inline T load_reduce_types(T* addr) {
    return PreRuntimeDispatch::load<decorators, T>(addr);
  }

  template <DecoratorSet decorators, typename T>
  inline oop load_reduce_types(narrowOop* addr) {
    const DecoratorSet expanded_decorators = decorators | INTERNAL_CONVERT_COMPRESSED_OOP | INTERNAL_RT_USE_COMPRESSED_OOPS;
    return PreRuntimeDispatch::load<expanded_decorators, oop>(addr);
  }

  template <DecoratorSet decorators, typename T>
  inline oop load_reduce_types(HeapWord* addr) {
    const DecoratorSet expanded_decorators = decorators | INTERNAL_CONVERT_COMPRESSED_OOP;
    return PreRuntimeDispatch::load<expanded_decorators, oop>(addr);
  }

  // Step 1: Set default decorators. This step remembers if a type was volatile
  // and then sets the MO_VOLATILE decorator by default. Otherwise, a default
  // memory ordering is set for the access, and the implied decorator rules
  // are applied to select sensible defaults for decorators that have not been
  // explicitly set. For example, default object referent strength is set to strong.
  // This step also decays the types passed in (e.g. getting rid of CV qualifiers
  // and references from the types). This step also perform some type verification
  // that the passed in types make sense.

  template <DecoratorSet decorators, typename T>
  static void verify_types(){
    // If this fails to compile, then you have sent in something that is
    // not recognized as a valid primitive type to a primitive Access function.
    STATIC_ASSERT((HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value || // oops have already been validated
                   (IsPointer<T>::value || IsIntegral<T>::value) ||
                    IsFloatingPoint<T>::value)); // not allowed primitive type
  }

  template <DecoratorSet decorators, typename P, typename T>
  inline void store(P* addr, T value) {
    verify_types<decorators, T>();
    typedef typename Decay<P>::type DecayedP;
    typedef typename Decay<T>::type DecayedT;
    DecayedT decayed_value = value;
    // If a volatile address is passed in but no memory ordering decorator,
    // set the memory ordering to MO_VOLATILE by default.
    const DecoratorSet expanded_decorators = DecoratorFixup<
      (IsVolatile<P>::value && !HasDecorator<decorators, MO_DECORATOR_MASK>::value) ?
      (MO_VOLATILE | decorators) : decorators>::value;
    store_reduce_types<expanded_decorators>(const_cast<DecayedP*>(addr), decayed_value);
  }

  template <DecoratorSet decorators, typename T>
  inline void store_at(oop base, ptrdiff_t offset, T value) {
    verify_types<decorators, T>();
    typedef typename Decay<T>::type DecayedT;
    DecayedT decayed_value = value;
    const DecoratorSet expanded_decorators = DecoratorFixup<decorators |
                                             (HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value ?
                                              INTERNAL_CONVERT_COMPRESSED_OOP : INTERNAL_EMPTY)>::value;
    PreRuntimeDispatch::store_at<expanded_decorators>(base, offset, decayed_value);
  }

  template <DecoratorSet decorators, typename P, typename T>
  inline T load(P* addr) {
    verify_types<decorators, T>();
    typedef typename Decay<P>::type DecayedP;
    typedef typename Conditional<HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value,
                                 typename OopOrNarrowOop<T>::type,
                                 typename Decay<T>::type>::type DecayedT;
    // If a volatile address is passed in but no memory ordering decorator,
    // set the memory ordering to MO_VOLATILE by default.
    const DecoratorSet expanded_decorators = DecoratorFixup<
      (IsVolatile<P>::value && !HasDecorator<decorators, MO_DECORATOR_MASK>::value) ?
      (MO_VOLATILE | decorators) : decorators>::value;
    return load_reduce_types<expanded_decorators, DecayedT>(const_cast<DecayedP*>(addr));
  }

  template <DecoratorSet decorators, typename T>
  inline T load_at(oop base, ptrdiff_t offset) {
    verify_types<decorators, T>();
    typedef typename Conditional<HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value,
                                 typename OopOrNarrowOop<T>::type,
                                 typename Decay<T>::type>::type DecayedT;
    // Expand the decorators (figure out sensible defaults)
    // Potentially remember if we need compressed oop awareness
    const DecoratorSet expanded_decorators = DecoratorFixup<decorators |
                                             (HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value ?
                                              INTERNAL_CONVERT_COMPRESSED_OOP : INTERNAL_EMPTY)>::value;
    return PreRuntimeDispatch::load_at<expanded_decorators, DecayedT>(base, offset);
  }

  template <DecoratorSet decorators, typename P, typename T>
  inline T atomic_cmpxchg(T new_value, P* addr, T compare_value) {
    verify_types<decorators, T>();
    typedef typename Decay<P>::type DecayedP;
    typedef typename Decay<T>::type DecayedT;
    DecayedT new_decayed_value = new_value;
    DecayedT compare_decayed_value = compare_value;
    const DecoratorSet expanded_decorators = DecoratorFixup<
      (!HasDecorator<decorators, MO_DECORATOR_MASK>::value) ?
      (MO_SEQ_CST | decorators) : decorators>::value;
    return atomic_cmpxchg_reduce_types<expanded_decorators>(new_decayed_value,
                                                            const_cast<DecayedP*>(addr),
                                                            compare_decayed_value);
  }

  template <DecoratorSet decorators, typename T>
  inline T atomic_cmpxchg_at(T new_value, oop base, ptrdiff_t offset, T compare_value) {
    verify_types<decorators, T>();
    typedef typename Decay<T>::type DecayedT;
    DecayedT new_decayed_value = new_value;
    DecayedT compare_decayed_value = compare_value;
    // Determine default memory ordering
    const DecoratorSet expanded_decorators = DecoratorFixup<
      (!HasDecorator<decorators, MO_DECORATOR_MASK>::value) ?
      (MO_SEQ_CST | decorators) : decorators>::value;
    // Potentially remember that we need compressed oop awareness
    const DecoratorSet final_decorators = expanded_decorators |
                                          (HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value ?
                                           INTERNAL_CONVERT_COMPRESSED_OOP : INTERNAL_EMPTY);
    return PreRuntimeDispatch::atomic_cmpxchg_at<final_decorators>(new_decayed_value, base,
                                                                   offset, compare_decayed_value);
  }

  template <DecoratorSet decorators, typename P, typename T>
  inline T atomic_xchg(T new_value, P* addr) {
    verify_types<decorators, T>();
    typedef typename Decay<P>::type DecayedP;
    typedef typename Decay<T>::type DecayedT;
    DecayedT new_decayed_value = new_value;
    // atomic_xchg is only available in SEQ_CST flavour.
    const DecoratorSet expanded_decorators = DecoratorFixup<decorators | MO_SEQ_CST>::value;
    return atomic_xchg_reduce_types<expanded_decorators>(new_decayed_value,
                                                         const_cast<DecayedP*>(addr));
  }

  template <DecoratorSet decorators, typename T>
  inline T atomic_xchg_at(T new_value, oop base, ptrdiff_t offset) {
    verify_types<decorators, T>();
    typedef typename Decay<T>::type DecayedT;
    DecayedT new_decayed_value = new_value;
    // atomic_xchg is only available in SEQ_CST flavour.
    const DecoratorSet expanded_decorators = DecoratorFixup<decorators | MO_SEQ_CST |
                                             (HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value ?
                                              INTERNAL_CONVERT_COMPRESSED_OOP : INTERNAL_EMPTY)>::value;
    return PreRuntimeDispatch::atomic_xchg_at<expanded_decorators>(new_decayed_value, base, offset);
  }

  template <DecoratorSet decorators, typename T>
  inline bool arraycopy(arrayOop src_obj, arrayOop dst_obj, T *src, T *dst, size_t length) {
    verify_types<decorators, T>();
    typedef typename Decay<T>::type DecayedT;
    const DecoratorSet expanded_decorators = DecoratorFixup<decorators | IN_HEAP_ARRAY | IN_HEAP |
                                             (HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value ?
                                              INTERNAL_CONVERT_COMPRESSED_OOP : INTERNAL_EMPTY)>::value;
    return PreRuntimeDispatch::arraycopy<expanded_decorators>(src_obj, dst_obj,
                                                              const_cast<DecayedT*>(src),
                                                              const_cast<DecayedT*>(dst),
                                                              length);
  }

  template <DecoratorSet decorators>
  inline void clone(oop src, oop dst, size_t size) {
    const DecoratorSet expanded_decorators = DecoratorFixup<decorators>::value;
    PreRuntimeDispatch::clone<expanded_decorators>(src, dst, size);
  }
}

template <DecoratorSet decorators>
template <DecoratorSet expected_decorators>
void Access<decorators>::verify_decorators() {
  STATIC_ASSERT((~expected_decorators & decorators) == 0); // unexpected decorator used
  const DecoratorSet barrier_strength_decorators = decorators & AS_DECORATOR_MASK;
  STATIC_ASSERT(barrier_strength_decorators == 0 || ( // make sure barrier strength decorators are disjoint if set
    (barrier_strength_decorators ^ AS_NO_KEEPALIVE) == 0 ||
    (barrier_strength_decorators ^ AS_RAW) == 0 ||
    (barrier_strength_decorators ^ AS_NORMAL) == 0
  ));
  const DecoratorSet ref_strength_decorators = decorators & ON_DECORATOR_MASK;
  STATIC_ASSERT(ref_strength_decorators == 0 || ( // make sure ref strength decorators are disjoint if set
    (ref_strength_decorators ^ ON_STRONG_OOP_REF) == 0 ||
    (ref_strength_decorators ^ ON_WEAK_OOP_REF) == 0 ||
    (ref_strength_decorators ^ ON_PHANTOM_OOP_REF) == 0 ||
    (ref_strength_decorators ^ ON_UNKNOWN_OOP_REF) == 0
  ));
  const DecoratorSet memory_ordering_decorators = decorators & MO_DECORATOR_MASK;
  STATIC_ASSERT(memory_ordering_decorators == 0 || ( // make sure memory ordering decorators are disjoint if set
    (memory_ordering_decorators ^ MO_UNORDERED) == 0 ||
    (memory_ordering_decorators ^ MO_VOLATILE) == 0 ||
    (memory_ordering_decorators ^ MO_RELAXED) == 0 ||
    (memory_ordering_decorators ^ MO_ACQUIRE) == 0 ||
    (memory_ordering_decorators ^ MO_RELEASE) == 0 ||
    (memory_ordering_decorators ^ MO_SEQ_CST) == 0
  ));
  const DecoratorSet location_decorators = decorators & IN_DECORATOR_MASK;
  STATIC_ASSERT(location_decorators == 0 || ( // make sure location decorators are disjoint if set
    (location_decorators ^ IN_ROOT) == 0 ||
    (location_decorators ^ IN_HEAP) == 0 ||
    (location_decorators ^ (IN_HEAP | IN_HEAP_ARRAY)) == 0 ||
    (location_decorators ^ (IN_ROOT | IN_CONCURRENT_ROOT)) == 0
  ));
}

#endif // SHARE_VM_RUNTIME_ACCESS_INLINE_HPP
