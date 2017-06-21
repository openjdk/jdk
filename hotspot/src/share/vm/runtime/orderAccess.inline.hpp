/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2016 SAP SE. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_ORDERACCESS_INLINE_HPP
#define SHARE_VM_RUNTIME_ORDERACCESS_INLINE_HPP

#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/macros.hpp"

#include OS_CPU_HEADER_INLINE(orderAccess)

#ifdef VM_HAS_GENERALIZED_ORDER_ACCESS

template<> inline void ScopedFenceGeneral<X_ACQUIRE>::postfix()       { OrderAccess::acquire(); }
template<> inline void ScopedFenceGeneral<RELEASE_X>::prefix()        { OrderAccess::release(); }
template<> inline void ScopedFenceGeneral<RELEASE_X_FENCE>::prefix()  { OrderAccess::release(); }
template<> inline void ScopedFenceGeneral<RELEASE_X_FENCE>::postfix() { OrderAccess::fence();   }


template <typename FieldType, ScopedFenceType FenceType>
inline void OrderAccess::ordered_store(volatile FieldType* p, FieldType v) {
  ScopedFence<FenceType> f((void*)p);
  store(p, v);
}

template <typename FieldType, ScopedFenceType FenceType>
inline FieldType OrderAccess::ordered_load(const volatile FieldType* p) {
  ScopedFence<FenceType> f((void*)p);
  return load(p);
}

inline jbyte    OrderAccess::load_acquire(const volatile jbyte*   p) { return specialized_load_acquire(p); }
inline jshort   OrderAccess::load_acquire(const volatile jshort*  p) { return specialized_load_acquire(p); }
inline jint     OrderAccess::load_acquire(const volatile jint*    p) { return specialized_load_acquire(p); }
inline jlong    OrderAccess::load_acquire(const volatile jlong*   p) { return specialized_load_acquire(p); }
inline jfloat   OrderAccess::load_acquire(const volatile jfloat*  p) { return specialized_load_acquire(p); }
inline jdouble  OrderAccess::load_acquire(const volatile jdouble* p) { return specialized_load_acquire(p); }
inline jubyte   OrderAccess::load_acquire(const volatile jubyte*  p) { return (jubyte) specialized_load_acquire((const volatile jbyte*)p);  }
inline jushort  OrderAccess::load_acquire(const volatile jushort* p) { return (jushort)specialized_load_acquire((const volatile jshort*)p); }
inline juint    OrderAccess::load_acquire(const volatile juint*   p) { return (juint)  specialized_load_acquire((const volatile jint*)p);   }
inline julong   OrderAccess::load_acquire(const volatile julong*  p) { return (julong) specialized_load_acquire((const volatile jlong*)p);  }

inline intptr_t OrderAccess::load_ptr_acquire(const volatile intptr_t*   p) { return (intptr_t)specialized_load_acquire(p); }
inline void*    OrderAccess::load_ptr_acquire(const volatile void*       p) { return (void*)specialized_load_acquire((const volatile intptr_t*)p); }

inline void     OrderAccess::release_store(volatile jbyte*   p, jbyte   v) { specialized_release_store(p, v); }
inline void     OrderAccess::release_store(volatile jshort*  p, jshort  v) { specialized_release_store(p, v); }
inline void     OrderAccess::release_store(volatile jint*    p, jint    v) { specialized_release_store(p, v); }
inline void     OrderAccess::release_store(volatile jlong*   p, jlong   v) { specialized_release_store(p, v); }
inline void     OrderAccess::release_store(volatile jfloat*  p, jfloat  v) { specialized_release_store(p, v); }
inline void     OrderAccess::release_store(volatile jdouble* p, jdouble v) { specialized_release_store(p, v); }
inline void     OrderAccess::release_store(volatile jubyte*  p, jubyte  v) { specialized_release_store((volatile jbyte*) p, (jbyte) v); }
inline void     OrderAccess::release_store(volatile jushort* p, jushort v) { specialized_release_store((volatile jshort*)p, (jshort)v); }
inline void     OrderAccess::release_store(volatile juint*   p, juint   v) { specialized_release_store((volatile jint*)  p, (jint)  v); }
inline void     OrderAccess::release_store(volatile julong*  p, julong  v) { specialized_release_store((volatile jlong*) p, (jlong) v); }

inline void     OrderAccess::release_store_ptr(volatile intptr_t* p, intptr_t v) { specialized_release_store(p, v); }
inline void     OrderAccess::release_store_ptr(volatile void*     p, void*    v) { specialized_release_store((volatile intptr_t*)p, (intptr_t)v); }

inline void     OrderAccess::release_store_fence(volatile jbyte*   p, jbyte   v) { specialized_release_store_fence(p, v); }
inline void     OrderAccess::release_store_fence(volatile jshort*  p, jshort  v) { specialized_release_store_fence(p, v); }
inline void     OrderAccess::release_store_fence(volatile jint*    p, jint    v) { specialized_release_store_fence(p, v); }
inline void     OrderAccess::release_store_fence(volatile jlong*   p, jlong   v) { specialized_release_store_fence(p, v); }
inline void     OrderAccess::release_store_fence(volatile jfloat*  p, jfloat  v) { specialized_release_store_fence(p, v); }
inline void     OrderAccess::release_store_fence(volatile jdouble* p, jdouble v) { specialized_release_store_fence(p, v); }
inline void     OrderAccess::release_store_fence(volatile jubyte*  p, jubyte  v) { specialized_release_store_fence((volatile jbyte*) p, (jbyte) v); }
inline void     OrderAccess::release_store_fence(volatile jushort* p, jushort v) { specialized_release_store_fence((volatile jshort*)p, (jshort)v); }
inline void     OrderAccess::release_store_fence(volatile juint*   p, juint   v) { specialized_release_store_fence((volatile jint*)  p, (jint)  v); }
inline void     OrderAccess::release_store_fence(volatile julong*  p, julong  v) { specialized_release_store_fence((volatile jlong*) p, (jlong) v); }

inline void     OrderAccess::release_store_ptr_fence(volatile intptr_t* p, intptr_t v) { specialized_release_store_fence(p, v); }
inline void     OrderAccess::release_store_ptr_fence(volatile void*     p, void*    v) { specialized_release_store_fence((volatile intptr_t*)p, (intptr_t)v); }

// The following methods can be specialized using simple template specialization
// in the platform specific files for optimization purposes. Otherwise the
// generalized variant is used.
template<typename T> inline T    OrderAccess::specialized_load_acquire       (const volatile T* p)       { return ordered_load<T, X_ACQUIRE>(p);    }
template<typename T> inline void OrderAccess::specialized_release_store      (volatile T* p, T v)  { ordered_store<T, RELEASE_X>(p, v);       }
template<typename T> inline void OrderAccess::specialized_release_store_fence(volatile T* p, T v)  { ordered_store<T, RELEASE_X_FENCE>(p, v); }

// Generalized atomic volatile accesses valid in OrderAccess
// All other types can be expressed in terms of these.
inline void OrderAccess::store(volatile jbyte*   p, jbyte   v) { *p = v; }
inline void OrderAccess::store(volatile jshort*  p, jshort  v) { *p = v; }
inline void OrderAccess::store(volatile jint*    p, jint    v) { *p = v; }
inline void OrderAccess::store(volatile jlong*   p, jlong   v) { Atomic::store(v, p); }
inline void OrderAccess::store(volatile jdouble* p, jdouble v) { Atomic::store(jlong_cast(v), (volatile jlong*)p); }
inline void OrderAccess::store(volatile jfloat*  p, jfloat  v) { *p = v; }

inline jbyte   OrderAccess::load(const volatile jbyte*   p) { return *p; }
inline jshort  OrderAccess::load(const volatile jshort*  p) { return *p; }
inline jint    OrderAccess::load(const volatile jint*    p) { return *p; }
inline jlong   OrderAccess::load(const volatile jlong*   p) { return Atomic::load(p); }
inline jdouble OrderAccess::load(const volatile jdouble* p) { return jdouble_cast(Atomic::load((const volatile jlong*)p)); }
inline jfloat  OrderAccess::load(const volatile jfloat*  p) { return *p; }

#endif // VM_HAS_GENERALIZED_ORDER_ACCESS

#endif // SHARE_VM_RUNTIME_ORDERACCESS_INLINE_HPP
