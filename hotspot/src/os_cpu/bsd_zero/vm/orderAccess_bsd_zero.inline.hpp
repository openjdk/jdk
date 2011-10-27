/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2007, 2008, 2009 Red Hat, Inc.
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

#ifndef OS_CPU_BSD_ZERO_VM_ORDERACCESS_BSD_ZERO_INLINE_HPP
#define OS_CPU_BSD_ZERO_VM_ORDERACCESS_BSD_ZERO_INLINE_HPP

#include "runtime/orderAccess.hpp"
#include "vm_version_zero.hpp"

#ifdef ARM

/*
 * ARM Kernel helper for memory barrier.
 * Using __asm __volatile ("":::"memory") does not work reliable on ARM
 * and gcc __sync_synchronize(); implementation does not use the kernel
 * helper for all gcc versions so it is unreliable to use as well.
 */
typedef void (__kernel_dmb_t) (void);
#define __kernel_dmb (*(__kernel_dmb_t *) 0xffff0fa0)

#define FULL_MEM_BARRIER __kernel_dmb()
#define READ_MEM_BARRIER __kernel_dmb()
#define WRITE_MEM_BARRIER __kernel_dmb()

#else // ARM

#define FULL_MEM_BARRIER __sync_synchronize()

#ifdef PPC

#ifdef __NO_LWSYNC__
#define READ_MEM_BARRIER __asm __volatile ("sync":::"memory")
#define WRITE_MEM_BARRIER __asm __volatile ("sync":::"memory")
#else
#define READ_MEM_BARRIER __asm __volatile ("lwsync":::"memory")
#define WRITE_MEM_BARRIER __asm __volatile ("lwsync":::"memory")
#endif

#else // PPC

#define READ_MEM_BARRIER __asm __volatile ("":::"memory")
#define WRITE_MEM_BARRIER __asm __volatile ("":::"memory")

#endif // PPC

#endif // ARM


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

inline jbyte    OrderAccess::load_acquire(volatile jbyte*   p) { jbyte data = *p; acquire(); return data; }
inline jshort   OrderAccess::load_acquire(volatile jshort*  p) { jshort data = *p; acquire(); return data; }
inline jint     OrderAccess::load_acquire(volatile jint*    p) { jint data = *p; acquire(); return data; }
inline jlong    OrderAccess::load_acquire(volatile jlong*   p) {
  jlong tmp;
  os::atomic_copy64(p, &tmp);
  acquire();
  return tmp;
}
inline jubyte    OrderAccess::load_acquire(volatile jubyte*   p) { jubyte data = *p; acquire(); return data; }
inline jushort   OrderAccess::load_acquire(volatile jushort*  p) { jushort data = *p; acquire(); return data; }
inline juint     OrderAccess::load_acquire(volatile juint*    p) { juint data = *p; acquire(); return data; }
inline julong   OrderAccess::load_acquire(volatile julong*  p) {
  julong tmp;
  os::atomic_copy64(p, &tmp);
  acquire();
  return tmp;
}
inline jfloat   OrderAccess::load_acquire(volatile jfloat*  p) { jfloat data = *p; acquire(); return data; }
inline jdouble  OrderAccess::load_acquire(volatile jdouble* p) {
  jdouble tmp;
  os::atomic_copy64(p, &tmp);
  acquire();
  return tmp;
}

inline intptr_t OrderAccess::load_ptr_acquire(volatile intptr_t*   p) {
  intptr_t data = *p;
  acquire();
  return data;
}
inline void*    OrderAccess::load_ptr_acquire(volatile void*       p) {
  void *data = *(void* volatile *)p;
  acquire();
  return data;
}
inline void*    OrderAccess::load_ptr_acquire(const volatile void* p) {
  void *data = *(void* const volatile *)p;
  acquire();
  return data;
}

inline void     OrderAccess::release_store(volatile jbyte*   p, jbyte   v) { release(); *p = v; }
inline void     OrderAccess::release_store(volatile jshort*  p, jshort  v) { release(); *p = v; }
inline void     OrderAccess::release_store(volatile jint*    p, jint    v) { release(); *p = v; }
inline void     OrderAccess::release_store(volatile jlong*   p, jlong   v)
{ release(); os::atomic_copy64(&v, p); }
inline void     OrderAccess::release_store(volatile jubyte*  p, jubyte  v) { release(); *p = v; }
inline void     OrderAccess::release_store(volatile jushort* p, jushort v) { release(); *p = v; }
inline void     OrderAccess::release_store(volatile juint*   p, juint   v) { release(); *p = v; }
inline void     OrderAccess::release_store(volatile julong*  p, julong  v)
{ release(); os::atomic_copy64(&v, p); }
inline void     OrderAccess::release_store(volatile jfloat*  p, jfloat  v) { release(); *p = v; }
inline void     OrderAccess::release_store(volatile jdouble* p, jdouble v)
{ release(); os::atomic_copy64(&v, p); }

inline void     OrderAccess::release_store_ptr(volatile intptr_t* p, intptr_t v) { release(); *p = v; }
inline void     OrderAccess::release_store_ptr(volatile void*     p, void*    v)
{ release(); *(void* volatile *)p = v; }

inline void     OrderAccess::store_fence(jbyte*   p, jbyte   v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(jshort*  p, jshort  v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(jint*    p, jint    v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(jlong*   p, jlong   v) { os::atomic_copy64(&v, p); fence(); }
inline void     OrderAccess::store_fence(jubyte*  p, jubyte  v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(jushort* p, jushort v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(juint*   p, juint   v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(julong*  p, julong  v) { os::atomic_copy64(&v, p); fence(); }
inline void     OrderAccess::store_fence(jfloat*  p, jfloat  v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(jdouble* p, jdouble v) { os::atomic_copy64(&v, p); fence(); }

inline void     OrderAccess::store_ptr_fence(intptr_t* p, intptr_t v) { *p = v; fence(); }
inline void     OrderAccess::store_ptr_fence(void**    p, void*    v) { *p = v; fence(); }

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

#endif // OS_CPU_BSD_ZERO_VM_ORDERACCESS_BSD_ZERO_INLINE_HPP
