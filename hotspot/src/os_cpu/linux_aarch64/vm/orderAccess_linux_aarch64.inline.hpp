/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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

#ifndef OS_CPU_LINUX_AARCH64_VM_ORDERACCESS_LINUX_AARCH64_INLINE_HPP
#define OS_CPU_LINUX_AARCH64_VM_ORDERACCESS_LINUX_AARCH64_INLINE_HPP

#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.hpp"
#include "vm_version_aarch64.hpp"

// Implementation of class OrderAccess.

inline void OrderAccess::loadload()   { acquire(); }
inline void OrderAccess::storestore() { release(); }
inline void OrderAccess::loadstore()  { acquire(); }
inline void OrderAccess::storeload()  { fence(); }

inline void OrderAccess::acquire() {
  READ_MEM_BARRIER;
}

inline void OrderAccess::release() {
  WRITE_MEM_BARRIER;
}

inline void OrderAccess::fence() {
  FULL_MEM_BARRIER;
}

inline jbyte    OrderAccess::load_acquire(volatile jbyte*   p)
{ jbyte data; __atomic_load(p, &data, __ATOMIC_ACQUIRE); return data; }
inline jshort   OrderAccess::load_acquire(volatile jshort*  p)
{ jshort data; __atomic_load(p, &data, __ATOMIC_ACQUIRE); return data; }
inline jint     OrderAccess::load_acquire(volatile jint*    p)
{ jint data; __atomic_load(p, &data, __ATOMIC_ACQUIRE); return data; }
inline jlong    OrderAccess::load_acquire(volatile jlong*   p)
{ jlong data; __atomic_load(p, &data, __ATOMIC_ACQUIRE); return data; }
inline jubyte    OrderAccess::load_acquire(volatile jubyte*   p)
{ jubyte data; __atomic_load(p, &data, __ATOMIC_ACQUIRE); return data; }
inline jushort   OrderAccess::load_acquire(volatile jushort*  p)
{ jushort data; __atomic_load(p, &data, __ATOMIC_ACQUIRE); return data; }
inline juint     OrderAccess::load_acquire(volatile juint*    p)
{ juint data; __atomic_load(p, &data, __ATOMIC_ACQUIRE); return data; }
inline julong   OrderAccess::load_acquire(volatile julong*  p)
{ julong data; __atomic_load(p, &data, __ATOMIC_ACQUIRE); return data; }
inline jfloat   OrderAccess::load_acquire(volatile jfloat*  p)
{ jfloat data; __atomic_load(p, &data, __ATOMIC_ACQUIRE); return data; }
inline jdouble  OrderAccess::load_acquire(volatile jdouble* p)
{ jdouble data; __atomic_load(p, &data, __ATOMIC_ACQUIRE); return data; }
inline intptr_t OrderAccess::load_ptr_acquire(volatile intptr_t*   p)
{ intptr_t data; __atomic_load(p, &data, __ATOMIC_ACQUIRE); return data; }
inline void*    OrderAccess::load_ptr_acquire(volatile void*       p)
{ void* data; __atomic_load((void* volatile *)p, &data, __ATOMIC_ACQUIRE); return data; }
inline void*    OrderAccess::load_ptr_acquire(const volatile void* p)
{ void* data; __atomic_load((void* const volatile *)p, &data, __ATOMIC_ACQUIRE); return data; }

inline void     OrderAccess::release_store(volatile jbyte*   p, jbyte   v)
{ __atomic_store(p, &v, __ATOMIC_RELEASE); }
inline void     OrderAccess::release_store(volatile jshort*  p, jshort  v)
{ __atomic_store(p, &v, __ATOMIC_RELEASE); }
inline void     OrderAccess::release_store(volatile jint*    p, jint    v)
{ __atomic_store(p, &v, __ATOMIC_RELEASE); }
inline void     OrderAccess::release_store(volatile jlong*   p, jlong   v)
{ __atomic_store(p, &v, __ATOMIC_RELEASE); }
inline void     OrderAccess::release_store(volatile jubyte*  p, jubyte  v)
{ __atomic_store(p, &v, __ATOMIC_RELEASE); }
inline void     OrderAccess::release_store(volatile jushort* p, jushort v)
{ __atomic_store(p, &v, __ATOMIC_RELEASE); }
inline void     OrderAccess::release_store(volatile juint*   p, juint   v)
{ __atomic_store(p, &v, __ATOMIC_RELEASE); }
inline void     OrderAccess::release_store(volatile julong*  p, julong  v)
{ __atomic_store(p, &v, __ATOMIC_RELEASE); }
inline void     OrderAccess::release_store(volatile jfloat*  p, jfloat  v)
{ __atomic_store(p, &v, __ATOMIC_RELEASE); }
inline void     OrderAccess::release_store(volatile jdouble* p, jdouble v)
{ __atomic_store(p, &v, __ATOMIC_RELEASE); }
inline void     OrderAccess::release_store_ptr(volatile intptr_t* p, intptr_t v)
{ __atomic_store(p, &v, __ATOMIC_RELEASE); }
inline void     OrderAccess::release_store_ptr(volatile void*     p, void*    v)
{ __atomic_store((void* volatile *)p, &v, __ATOMIC_RELEASE); }

inline void     OrderAccess::store_fence(jbyte*   p, jbyte   v)
{ __atomic_store(p, &v, __ATOMIC_RELAXED); fence(); }
inline void     OrderAccess::store_fence(jshort*  p, jshort  v)
{ __atomic_store(p, &v, __ATOMIC_RELAXED); fence(); }
inline void     OrderAccess::store_fence(jint*    p, jint    v)
{ __atomic_store(p, &v, __ATOMIC_RELAXED); fence(); }
inline void     OrderAccess::store_fence(jlong*   p, jlong   v)
{ __atomic_store(p, &v, __ATOMIC_RELAXED); fence(); }
inline void     OrderAccess::store_fence(jubyte*  p, jubyte  v)
{ __atomic_store(p, &v, __ATOMIC_RELAXED); fence(); }
inline void     OrderAccess::store_fence(jushort* p, jushort v)
{ __atomic_store(p, &v, __ATOMIC_RELAXED); fence(); }
inline void     OrderAccess::store_fence(juint*   p, juint   v)
{ __atomic_store(p, &v, __ATOMIC_RELAXED); fence(); }
inline void     OrderAccess::store_fence(julong*  p, julong  v)
{ __atomic_store(p, &v, __ATOMIC_RELAXED); fence(); }
inline void     OrderAccess::store_fence(jfloat*  p, jfloat  v)
{ __atomic_store(p, &v, __ATOMIC_RELAXED); fence(); }
inline void     OrderAccess::store_fence(jdouble* p, jdouble v)
{ __atomic_store(p, &v, __ATOMIC_RELAXED); fence(); }
inline void     OrderAccess::store_ptr_fence(intptr_t* p, intptr_t v)
{ __atomic_store(p, &v, __ATOMIC_RELAXED); fence(); }
inline void     OrderAccess::store_ptr_fence(void**    p, void*    v)
{ __atomic_store(p, &v, __ATOMIC_RELAXED); fence(); }

inline void     OrderAccess::release_store_fence(volatile jbyte*   p, jbyte   v) { release_store(p, v); fence(); }
inline void     OrderAccess::release_store_fence(volatile jshort*  p, jshort  v) { release_store(p, v); fence(); }
inline void     OrderAccess::release_store_fence(volatile jint*    p, jint    v) { release_store(p, v); fence(); }
inline void     OrderAccess::release_store_fence(volatile jlong*   p, jlong   v) { release_store(p, v); fence(); }
inline void     OrderAccess::release_store_fence(volatile jubyte*  p, jubyte  v) { release_store(p, v); fence(); }
inline void     OrderAccess::release_store_fence(volatile jushort* p, jushort v) { release_store(p, v); fence(); }
inline void     OrderAccess::release_store_fence(volatile juint*   p, juint   v) { release_store(p, v); fence(); }
inline void     OrderAccess::release_store_fence(volatile julong*  p, julong  v) { release_store(p, v); fence(); }
inline void     OrderAccess::release_store_fence(volatile jfloat*  p, jfloat  v) { release_store(p, v); fence(); }
inline void     OrderAccess::release_store_fence(volatile jdouble* p, jdouble v) { release_store(p, v); fence(); }

inline void     OrderAccess::release_store_ptr_fence(volatile intptr_t* p, intptr_t v) { release_store_ptr(p, v); fence(); }
inline void     OrderAccess::release_store_ptr_fence(volatile void*     p, void*    v) { release_store_ptr(p, v); fence(); }

#endif // OS_CPU_LINUX_AARCH64_VM_ORDERACCESS_LINUX_AARCH64_INLINE_HPP
