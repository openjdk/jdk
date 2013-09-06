/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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

#ifndef OS_CPU_AIX_OJDKPPC_VM_ORDERACCESS_AIX_PPC_INLINE_HPP
#define OS_CPU_AIX_OJDKPPC_VM_ORDERACCESS_AIX_PPC_INLINE_HPP

#include "runtime/orderAccess.hpp"
#include "vm_version_ppc.hpp"

// Implementation of class OrderAccess.

//
// Machine barrier instructions:
//
// - ppc_sync            Two-way memory barrier, aka fence.
// - ppc_lwsync          orders  Store|Store,
//                                Load|Store,
//                                Load|Load,
//                       but not Store|Load
// - ppc_eieio           orders  Store|Store
// - ppc_isync           Invalidates speculatively executed instructions,
//                       but isync may complete before storage accesses
//                       associated with instructions preceding isync have
//                       been performed.
//
// Semantic barrier instructions:
// (as defined in orderAccess.hpp)
//
// - ppc_release         orders Store|Store,       (maps to ppc_lwsync)
//                               Load|Store
// - ppc_acquire         orders  Load|Store,       (maps to ppc_lwsync)
//                               Load|Load
// - ppc_fence           orders Store|Store,       (maps to ppc_sync)
//                               Load|Store,
//                               Load|Load,
//                              Store|Load
//

#define inlasm_ppc_sync()     __asm__ __volatile__ ("sync"   : : : "memory");
#define inlasm_ppc_lwsync()   __asm__ __volatile__ ("lwsync" : : : "memory");
#define inlasm_ppc_eieio()    __asm__ __volatile__ ("eieio"  : : : "memory");
#define inlasm_ppc_isync()    __asm__ __volatile__ ("isync"  : : : "memory");
#define inlasm_ppc_release()  inlasm_ppc_lwsync();
#define inlasm_ppc_acquire()  inlasm_ppc_lwsync();
// Use twi-isync for load_acquire (faster than lwsync).
// ATTENTION: seems like xlC 10.1 has problems with this inline assembler macro (VerifyMethodHandles found "bad vminfo in AMH.conv"):
// #define inlasm_ppc_acquire_reg(X) __asm__ __volatile__ ("twi 0,%0,0\n isync\n" : : "r" (X) : "memory");
#define inlasm_ppc_acquire_reg(X) inlasm_ppc_lwsync();
#define inlasm_ppc_fence()    inlasm_ppc_sync();

inline void     OrderAccess::loadload()   { inlasm_ppc_lwsync();  }
inline void     OrderAccess::storestore() { inlasm_ppc_lwsync();  }
inline void     OrderAccess::loadstore()  { inlasm_ppc_lwsync();  }
inline void     OrderAccess::storeload()  { inlasm_ppc_fence();   }

inline void     OrderAccess::acquire()    { inlasm_ppc_acquire(); }
inline void     OrderAccess::release()    { inlasm_ppc_release(); }
inline void     OrderAccess::fence()      { inlasm_ppc_fence();   }

inline jbyte    OrderAccess::load_acquire(volatile jbyte*   p) { register jbyte t = *p;   inlasm_ppc_acquire_reg(t); return t; }
inline jshort   OrderAccess::load_acquire(volatile jshort*  p) { register jshort t = *p;  inlasm_ppc_acquire_reg(t); return t; }
inline jint     OrderAccess::load_acquire(volatile jint*    p) { register jint t = *p;    inlasm_ppc_acquire_reg(t); return t; }
inline jlong    OrderAccess::load_acquire(volatile jlong*   p) { register jlong t = *p;   inlasm_ppc_acquire_reg(t); return t; }
inline jubyte   OrderAccess::load_acquire(volatile jubyte*  p) { register jubyte t = *p;  inlasm_ppc_acquire_reg(t); return t; }
inline jushort  OrderAccess::load_acquire(volatile jushort* p) { register jushort t = *p; inlasm_ppc_acquire_reg(t); return t; }
inline juint    OrderAccess::load_acquire(volatile juint*   p) { register juint t = *p;   inlasm_ppc_acquire_reg(t); return t; }
inline julong   OrderAccess::load_acquire(volatile julong*  p) { return (julong)load_acquire((volatile jlong*)p); }
inline jfloat   OrderAccess::load_acquire(volatile jfloat*  p) { register jfloat t = *p;  inlasm_ppc_acquire(); return t; }
inline jdouble  OrderAccess::load_acquire(volatile jdouble* p) { register jdouble t = *p; inlasm_ppc_acquire(); return t; }

inline intptr_t OrderAccess::load_ptr_acquire(volatile intptr_t*   p) { return (intptr_t)load_acquire((volatile jlong*)p); }
inline void*    OrderAccess::load_ptr_acquire(volatile void*       p) { return (void*)   load_acquire((volatile jlong*)p); }
inline void*    OrderAccess::load_ptr_acquire(const volatile void* p) { return (void*)   load_acquire((volatile jlong*)p); }

inline void     OrderAccess::release_store(volatile jbyte*   p, jbyte   v) { inlasm_ppc_release(); *p = v; }
inline void     OrderAccess::release_store(volatile jshort*  p, jshort  v) { inlasm_ppc_release(); *p = v; }
inline void     OrderAccess::release_store(volatile jint*    p, jint    v) { inlasm_ppc_release(); *p = v; }
inline void     OrderAccess::release_store(volatile jlong*   p, jlong   v) { inlasm_ppc_release(); *p = v; }
inline void     OrderAccess::release_store(volatile jubyte*  p, jubyte  v) { inlasm_ppc_release(); *p = v; }
inline void     OrderAccess::release_store(volatile jushort* p, jushort v) { inlasm_ppc_release(); *p = v; }
inline void     OrderAccess::release_store(volatile juint*   p, juint   v) { inlasm_ppc_release(); *p = v; }
inline void     OrderAccess::release_store(volatile julong*  p, julong  v) { inlasm_ppc_release(); *p = v; }
inline void     OrderAccess::release_store(volatile jfloat*  p, jfloat  v) { inlasm_ppc_release(); *p = v; }
inline void     OrderAccess::release_store(volatile jdouble* p, jdouble v) { inlasm_ppc_release(); *p = v; }

inline void     OrderAccess::release_store_ptr(volatile intptr_t* p, intptr_t v) { inlasm_ppc_release(); *p = v; }
inline void     OrderAccess::release_store_ptr(volatile void*     p, void*    v) { inlasm_ppc_release(); *(void* volatile *)p = v; }

inline void     OrderAccess::store_fence(jbyte*   p, jbyte   v) { *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::store_fence(jshort*  p, jshort  v) { *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::store_fence(jint*    p, jint    v) { *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::store_fence(jlong*   p, jlong   v) { *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::store_fence(jubyte*  p, jubyte  v) { *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::store_fence(jushort* p, jushort v) { *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::store_fence(juint*   p, juint   v) { *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::store_fence(julong*  p, julong  v) { *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::store_fence(jfloat*  p, jfloat  v) { *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::store_fence(jdouble* p, jdouble v) { *p = v; inlasm_ppc_fence(); }

inline void     OrderAccess::store_ptr_fence(intptr_t* p, intptr_t v) { *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::store_ptr_fence(void**    p, void*    v) { *p = v; inlasm_ppc_fence(); }

inline void     OrderAccess::release_store_fence(volatile jbyte*   p, jbyte   v) { inlasm_ppc_release(); *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::release_store_fence(volatile jshort*  p, jshort  v) { inlasm_ppc_release(); *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::release_store_fence(volatile jint*    p, jint    v) { inlasm_ppc_release(); *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::release_store_fence(volatile jlong*   p, jlong   v) { inlasm_ppc_release(); *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::release_store_fence(volatile jubyte*  p, jubyte  v) { inlasm_ppc_release(); *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::release_store_fence(volatile jushort* p, jushort v) { inlasm_ppc_release(); *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::release_store_fence(volatile juint*   p, juint   v) { inlasm_ppc_release(); *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::release_store_fence(volatile julong*  p, julong  v) { inlasm_ppc_release(); *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::release_store_fence(volatile jfloat*  p, jfloat  v) { inlasm_ppc_release(); *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::release_store_fence(volatile jdouble* p, jdouble v) { inlasm_ppc_release(); *p = v; inlasm_ppc_fence(); }

inline void     OrderAccess::release_store_ptr_fence(volatile intptr_t* p, intptr_t v) { inlasm_ppc_release(); *p = v; inlasm_ppc_fence(); }
inline void     OrderAccess::release_store_ptr_fence(volatile void*     p, void*    v) { inlasm_ppc_release(); *(void* volatile *)p = v; inlasm_ppc_fence(); }

#undef inlasm_ppc_sync
#undef inlasm_ppc_lwsync
#undef inlasm_ppc_eieio
#undef inlasm_ppc_isync
#undef inlasm_ppc_release
#undef inlasm_ppc_acquire
#undef inlasm_ppc_fence

#endif // OS_CPU_AIX_OJDKPPC_VM_ORDERACCESS_AIX_PPC_INLINE_HPP
