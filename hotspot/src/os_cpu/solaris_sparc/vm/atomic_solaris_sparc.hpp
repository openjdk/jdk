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

#ifndef OS_CPU_SOLARIS_SPARC_VM_ATOMIC_SOLARIS_SPARC_HPP
#define OS_CPU_SOLARIS_SPARC_VM_ATOMIC_SOLARIS_SPARC_HPP

// Implementation of class atomic

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


inline void Atomic::store(jlong store_value, jlong* dest) { *dest = store_value; }
inline void Atomic::store(jlong store_value, volatile jlong* dest) { *dest = store_value; }
inline jlong Atomic::load(const volatile jlong* src) { return *src; }


// This is the interface to the atomic instructions in solaris_sparc.il.
// It's very messy because we need to support v8 and these instructions
// are illegal there.  When sparc v8 is dropped, we can drop out lots of
// this code.  Also compiler2 does not support v8 so the conditional code
// omits the instruction set check.

extern "C" jint     _Atomic_swap32(jint     exchange_value, volatile jint*     dest);
extern "C" intptr_t _Atomic_swap64(intptr_t exchange_value, volatile intptr_t* dest);

// Implement ADD using a CAS loop.
template<size_t byte_size>
struct Atomic::PlatformAdd VALUE_OBJ_CLASS_SPEC {
  template<typename I, typename D>
  inline D operator()(I add_value, D volatile* dest) const {
    D old_value = *dest;
    while (true) {
      D new_value = old_value + add_value;
      D result = cmpxchg(new_value, dest, old_value);
      if (result == old_value) break;
      old_value = result;
    }
    return old_value + add_value;
  }
};

inline jint     Atomic::xchg    (jint     exchange_value, volatile jint*     dest) {
  return _Atomic_swap32(exchange_value, dest);
}

inline intptr_t Atomic::xchg_ptr(intptr_t exchange_value, volatile intptr_t* dest) {
  return _Atomic_swap64(exchange_value, dest);
}

inline void*    Atomic::xchg_ptr(void*    exchange_value, volatile void*     dest) {
  return (void*)xchg_ptr((intptr_t)exchange_value, (volatile intptr_t*)dest);
}

// No direct support for cmpxchg of bytes; emulate using int.
template<>
struct Atomic::PlatformCmpxchg<1> : Atomic::CmpxchgByteUsingInt {};

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<4>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                cmpxchg_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));
  T rv;
  __asm__ volatile(
    " cas    [%2], %3, %0"
    : "=r" (rv)
    : "0" (exchange_value), "r" (dest), "r" (compare_value)
    : "memory");
  return rv;
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<8>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                cmpxchg_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
  T rv;
  __asm__ volatile(
    " casx   [%2], %3, %0"
    : "=r" (rv)
    : "0" (exchange_value), "r" (dest), "r" (compare_value)
    : "memory");
  return rv;
}

#endif // OS_CPU_SOLARIS_SPARC_VM_ATOMIC_SOLARIS_SPARC_HPP
