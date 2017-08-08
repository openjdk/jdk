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

#ifndef OS_CPU_SOLARIS_X86_VM_ATOMIC_SOLARIS_X86_HPP
#define OS_CPU_SOLARIS_X86_VM_ATOMIC_SOLARIS_X86_HPP

#include "runtime/os.hpp"

inline void Atomic::store    (jbyte    store_value, jbyte*    dest) { *dest = store_value; }
inline void Atomic::store    (jshort   store_value, jshort*   dest) { *dest = store_value; }
inline void Atomic::store    (jint     store_value, jint*     dest) { *dest = store_value; }


inline void Atomic::store_ptr(intptr_t store_value, intptr_t* dest) { *dest = store_value; }
inline void Atomic::store_ptr(void*    store_value, void*     dest) { *(void**)dest = store_value; }

inline void Atomic::store    (jbyte    store_value, volatile jbyte*    dest) { *dest = store_value; }
inline void Atomic::store    (jshort   store_value, volatile jshort*   dest) { *dest = store_value; }
inline void Atomic::store    (jint     store_value, volatile jint*     dest) { *dest = store_value; }
inline void Atomic::store_ptr(intptr_t store_value, volatile intptr_t* dest) { *dest = store_value; }
inline void Atomic::store_ptr(void*    store_value, volatile void*     dest) { *(void* volatile *)dest = store_value; }

inline void Atomic::inc    (volatile jint*     dest) { (void)add    (1, dest); }
inline void Atomic::inc_ptr(volatile intptr_t* dest) { (void)add_ptr(1, dest); }
inline void Atomic::inc_ptr(volatile void*     dest) { (void)add_ptr(1, dest); }

inline void Atomic::dec    (volatile jint*     dest) { (void)add    (-1, dest); }
inline void Atomic::dec_ptr(volatile intptr_t* dest) { (void)add_ptr(-1, dest); }
inline void Atomic::dec_ptr(volatile void*     dest) { (void)add_ptr(-1, dest); }

// For Sun Studio - implementation is in solaris_x86_[32/64].il.
// For gcc - implementation is just below.

extern "C" {
  jint _Atomic_add(jint add_value, volatile jint* dest);
  jint _Atomic_xchg(jint exchange_value, volatile jint* dest);
  jbyte _Atomic_cmpxchg_byte(jbyte exchange_value, volatile jbyte* dest,
                             jbyte compare_value);
  jint _Atomic_cmpxchg(jint exchange_value, volatile jint* dest,
                       jint compare_value);
  jlong _Atomic_cmpxchg_long(jlong exchange_value, volatile jlong* dest,
                             jlong compare_value);
}

inline jint     Atomic::add    (jint     add_value, volatile jint*     dest) {
  return _Atomic_add(add_value, dest);
}

inline jint     Atomic::xchg       (jint     exchange_value, volatile jint*     dest) {
  return _Atomic_xchg(exchange_value, dest);
}

#define VM_HAS_SPECIALIZED_CMPXCHG_BYTE
inline jbyte    Atomic::cmpxchg    (jbyte    exchange_value, volatile jbyte*    dest, jbyte    compare_value, cmpxchg_memory_order order) {
  return _Atomic_cmpxchg_byte(exchange_value, dest, compare_value);
}

inline jint     Atomic::cmpxchg    (jint     exchange_value, volatile jint*     dest, jint     compare_value, cmpxchg_memory_order order) {
  return _Atomic_cmpxchg(exchange_value, dest, compare_value);
}

inline jlong    Atomic::cmpxchg    (jlong    exchange_value, volatile jlong*    dest, jlong    compare_value, cmpxchg_memory_order order) {
  return _Atomic_cmpxchg_long(exchange_value, dest, compare_value);
}


#ifdef AMD64
inline void Atomic::store    (jlong    store_value, jlong*             dest) { *dest = store_value; }
inline void Atomic::store    (jlong    store_value, volatile jlong*    dest) { *dest = store_value; }
extern "C" jlong _Atomic_add_long(jlong add_value, volatile jlong* dest);
extern "C" jlong _Atomic_xchg_long(jlong exchange_value, volatile jlong* dest);

inline intptr_t Atomic::add_ptr(intptr_t add_value, volatile intptr_t* dest) {
  return (intptr_t)_Atomic_add_long((jlong)add_value, (volatile jlong*)dest);
}

inline void*    Atomic::add_ptr(intptr_t add_value, volatile void*     dest) {
  return (void*)_Atomic_add_long((jlong)add_value, (volatile jlong*)dest);
}

inline intptr_t Atomic::xchg_ptr(intptr_t exchange_value, volatile intptr_t* dest) {
  return (intptr_t)_Atomic_xchg_long((jlong)exchange_value, (volatile jlong*)dest);
}

inline void*    Atomic::xchg_ptr(void*    exchange_value, volatile void*     dest) {
  return (void*)_Atomic_xchg_long((jlong)exchange_value, (volatile jlong*)dest);
}

inline intptr_t Atomic::cmpxchg_ptr(intptr_t exchange_value, volatile intptr_t* dest, intptr_t compare_value, cmpxchg_memory_order order) {
  return (intptr_t)_Atomic_cmpxchg_long((jlong)exchange_value, (volatile jlong*)dest, (jlong)compare_value);
}

inline void*    Atomic::cmpxchg_ptr(void*    exchange_value, volatile void*     dest, void*    compare_value, cmpxchg_memory_order order) {
  return (void*)_Atomic_cmpxchg_long((jlong)exchange_value, (volatile jlong*)dest, (jlong)compare_value);
}

inline jlong Atomic::load(const volatile jlong* src) { return *src; }

#else // !AMD64

inline intptr_t Atomic::add_ptr(intptr_t add_value, volatile intptr_t* dest) {
  return (intptr_t)add((jint)add_value, (volatile jint*)dest);
}

inline void*    Atomic::add_ptr(intptr_t add_value, volatile void*     dest) {
  return (void*)add((jint)add_value, (volatile jint*)dest);
}

inline intptr_t Atomic::xchg_ptr(intptr_t exchange_value, volatile intptr_t* dest) {
  return (intptr_t)xchg((jint)exchange_value, (volatile jint*)dest);
}

inline void*    Atomic::xchg_ptr(void*    exchange_value, volatile void*     dest) {
  return (void*)xchg((jint)exchange_value, (volatile jint*)dest);
}

inline intptr_t Atomic::cmpxchg_ptr(intptr_t exchange_value, volatile intptr_t* dest, intptr_t compare_value, cmpxchg_memory_order order) {
  return (intptr_t)cmpxchg((jint)exchange_value, (volatile jint*)dest, (jint)compare_value, order);
}

inline void*    Atomic::cmpxchg_ptr(void*    exchange_value, volatile void*     dest, void*    compare_value, cmpxchg_memory_order order) {
  return (void*)cmpxchg((jint)exchange_value, (volatile jint*)dest, (jint)compare_value, order);
}

extern "C" void _Atomic_move_long(const volatile jlong* src, volatile jlong* dst);

inline jlong Atomic::load(const volatile jlong* src) {
  volatile jlong dest;
  _Atomic_move_long(src, &dest);
  return dest;
}

inline void Atomic::store(jlong store_value, jlong* dest) {
  _Atomic_move_long((volatile jlong*)&store_value, (volatile jlong*)dest);
}

inline void Atomic::store(jlong store_value, volatile jlong* dest) {
  _Atomic_move_long((volatile jlong*)&store_value, dest);
}

#endif // AMD64


#endif // OS_CPU_SOLARIS_X86_VM_ATOMIC_SOLARIS_X86_HPP
