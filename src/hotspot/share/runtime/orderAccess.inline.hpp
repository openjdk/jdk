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

#include "runtime/orderAccess.hpp"
#include "utilities/macros.hpp"

#include OS_CPU_HEADER_INLINE(orderAccess)

template<> inline void ScopedFenceGeneral<X_ACQUIRE>::postfix()       { OrderAccess::acquire(); }
template<> inline void ScopedFenceGeneral<RELEASE_X>::prefix()        { OrderAccess::release(); }
template<> inline void ScopedFenceGeneral<RELEASE_X_FENCE>::prefix()  { OrderAccess::release(); }
template<> inline void ScopedFenceGeneral<RELEASE_X_FENCE>::postfix() { OrderAccess::fence();   }


template <typename FieldType, ScopedFenceType FenceType>
inline void OrderAccess::ordered_store(volatile FieldType* p, FieldType v) {
  ScopedFence<FenceType> f((void*)p);
  Atomic::store(v, p);
}

template <typename FieldType, ScopedFenceType FenceType>
inline FieldType OrderAccess::ordered_load(const volatile FieldType* p) {
  ScopedFence<FenceType> f((void*)p);
  return Atomic::load(p);
}

template <typename T>
inline T OrderAccess::load_acquire(const volatile T* p) {
  return LoadImpl<T, PlatformOrderedLoad<sizeof(T), X_ACQUIRE> >()(p);
}

template <typename T, typename D>
inline void OrderAccess::release_store(volatile D* p, T v) {
  StoreImpl<T, D, PlatformOrderedStore<sizeof(D), RELEASE_X> >()(v, p);
}

template <typename T, typename D>
inline void OrderAccess::release_store_fence(volatile D* p, T v) {
  StoreImpl<T, D, PlatformOrderedStore<sizeof(D), RELEASE_X_FENCE> >()(v, p);
}
#endif // SHARE_VM_RUNTIME_ORDERACCESS_INLINE_HPP
