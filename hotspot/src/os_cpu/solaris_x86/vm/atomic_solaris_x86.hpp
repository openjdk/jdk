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

// For Sun Studio - implementation is in solaris_x86_64.il.

extern "C" {
  jint _Atomic_add(jint add_value, volatile jint* dest);
  jlong _Atomic_add_long(jlong add_value, volatile jlong* dest);

  jint _Atomic_xchg(jint exchange_value, volatile jint* dest);
  jbyte _Atomic_cmpxchg_byte(jbyte exchange_value, volatile jbyte* dest,
                             jbyte compare_value);
  jint _Atomic_cmpxchg(jint exchange_value, volatile jint* dest,
                       jint compare_value);
  jlong _Atomic_cmpxchg_long(jlong exchange_value, volatile jlong* dest,
                             jlong compare_value);
}

template<size_t byte_size>
struct Atomic::PlatformAdd
  : Atomic::AddAndFetch<Atomic::PlatformAdd<byte_size> >
{
  template<typename I, typename D>
  D add_and_fetch(I add_value, D volatile* dest) const;
};

// Not using add_using_helper; see comment for cmpxchg.
template<>
template<typename I, typename D>
inline D Atomic::PlatformAdd<4>::add_and_fetch(I add_value, D volatile* dest) const {
  STATIC_ASSERT(4 == sizeof(I));
  STATIC_ASSERT(4 == sizeof(D));
  return PrimitiveConversions::cast<D>(
    _Atomic_add(PrimitiveConversions::cast<jint>(add_value),
                reinterpret_cast<jint volatile*>(dest)));
}

// Not using add_using_helper; see comment for cmpxchg.
template<>
template<typename I, typename D>
inline D Atomic::PlatformAdd<8>::add_and_fetch(I add_value, D volatile* dest) const {
  STATIC_ASSERT(8 == sizeof(I));
  STATIC_ASSERT(8 == sizeof(D));
  return PrimitiveConversions::cast<D>(
    _Atomic_add_long(PrimitiveConversions::cast<jlong>(add_value),
                     reinterpret_cast<jlong volatile*>(dest)));
}

inline jint     Atomic::xchg       (jint     exchange_value, volatile jint*     dest) {
  return _Atomic_xchg(exchange_value, dest);
}

// Not using cmpxchg_using_helper here, because some configurations of
// Solaris compiler don't deal well with passing a "defined in .il"
// function as an argument.  We *should* switch to using gcc-style
// inline assembly, but attempting to do so with Studio 12.4 ran into
// segfaults.

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<1>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                cmpxchg_memory_order order) const {
  STATIC_ASSERT(1 == sizeof(T));
  return PrimitiveConversions::cast<T>(
    _Atomic_cmpxchg_byte(PrimitiveConversions::cast<jbyte>(exchange_value),
                         reinterpret_cast<jbyte volatile*>(dest),
                         PrimitiveConversions::cast<jbyte>(compare_value)));
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<4>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                cmpxchg_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));
  return PrimitiveConversions::cast<T>(
    _Atomic_cmpxchg(PrimitiveConversions::cast<jint>(exchange_value),
                    reinterpret_cast<jint volatile*>(dest),
                    PrimitiveConversions::cast<jint>(compare_value)));
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<8>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                cmpxchg_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
  return PrimitiveConversions::cast<T>(
    _Atomic_cmpxchg_long(PrimitiveConversions::cast<jlong>(exchange_value),
                         reinterpret_cast<jlong volatile*>(dest),
                         PrimitiveConversions::cast<jlong>(compare_value)));
}

inline void Atomic::store    (jlong    store_value, jlong*             dest) { *dest = store_value; }
inline void Atomic::store    (jlong    store_value, volatile jlong*    dest) { *dest = store_value; }
extern "C" jlong _Atomic_xchg_long(jlong exchange_value, volatile jlong* dest);

inline intptr_t Atomic::xchg_ptr(intptr_t exchange_value, volatile intptr_t* dest) {
  return (intptr_t)_Atomic_xchg_long((jlong)exchange_value, (volatile jlong*)dest);
}

inline void*    Atomic::xchg_ptr(void*    exchange_value, volatile void*     dest) {
  return (void*)_Atomic_xchg_long((jlong)exchange_value, (volatile jlong*)dest);
}

inline jlong Atomic::load(const volatile jlong* src) { return *src; }

#endif // OS_CPU_SOLARIS_X86_VM_ATOMIC_SOLARIS_X86_HPP
