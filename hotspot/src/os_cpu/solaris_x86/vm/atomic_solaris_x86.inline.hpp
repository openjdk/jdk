/*
 * Copyright (c) 1999, 2009, Oracle and/or its affiliates. All rights reserved.
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

// The lock prefix can be omitted for certain instructions on uniprocessors; to
// facilitate this, os::is_MP() is passed as an additional argument.  64-bit
// processors are assumed to be multi-threaded and/or multi-core, so the extra
// argument is unnecessary.
#ifndef _LP64
#define IS_MP_DECL() , int is_mp
#define IS_MP_ARG()  , (int) os::is_MP()
#else
#define IS_MP_DECL()
#define IS_MP_ARG()
#endif // _LP64

extern "C" {
  jint _Atomic_add(jint add_value, volatile jint* dest IS_MP_DECL());
  jint _Atomic_xchg(jint exchange_value, volatile jint* dest);
  jint _Atomic_cmpxchg(jint exchange_value, volatile jint* dest,
                       jint compare_value IS_MP_DECL());
  jlong _Atomic_cmpxchg_long(jlong exchange_value, volatile jlong* dest,
                             jlong compare_value IS_MP_DECL());
}

inline jint     Atomic::add    (jint     add_value, volatile jint*     dest) {
  return _Atomic_add(add_value, dest IS_MP_ARG());
}

inline jint     Atomic::xchg       (jint     exchange_value, volatile jint*     dest) {
  return _Atomic_xchg(exchange_value, dest);
}

inline jint     Atomic::cmpxchg    (jint     exchange_value, volatile jint*     dest, jint     compare_value) {
  return _Atomic_cmpxchg(exchange_value, dest, compare_value IS_MP_ARG());
}

inline jlong    Atomic::cmpxchg    (jlong    exchange_value, volatile jlong*    dest, jlong    compare_value) {
  return _Atomic_cmpxchg_long(exchange_value, dest, compare_value IS_MP_ARG());
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

inline intptr_t Atomic::cmpxchg_ptr(intptr_t exchange_value, volatile intptr_t* dest, intptr_t compare_value) {
  return (intptr_t)_Atomic_cmpxchg_long((jlong)exchange_value, (volatile jlong*)dest, (jlong)compare_value);
}

inline void*    Atomic::cmpxchg_ptr(void*    exchange_value, volatile void*     dest, void*    compare_value) {
  return (void*)_Atomic_cmpxchg_long((jlong)exchange_value, (volatile jlong*)dest, (jlong)compare_value);
}

inline jlong Atomic::load(volatile jlong* src) { return *src; }

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

inline intptr_t Atomic::cmpxchg_ptr(intptr_t exchange_value, volatile intptr_t* dest, intptr_t compare_value) {
  return (intptr_t)cmpxchg((jint)exchange_value, (volatile jint*)dest, (jint)compare_value);
}

inline void*    Atomic::cmpxchg_ptr(void*    exchange_value, volatile void*     dest, void*    compare_value) {
  return (void*)cmpxchg((jint)exchange_value, (volatile jint*)dest, (jint)compare_value);
}

extern "C" void _Atomic_load_long(volatile jlong* src, volatile jlong* dst);

inline jlong Atomic::load(volatile jlong* src) {
  volatile jlong dest;
  _Atomic_load_long(src, &dest);
  return dest;
}

#endif // AMD64

#ifdef _GNU_SOURCE
// Add a lock prefix to an instruction on an MP machine
#define LOCK_IF_MP(mp) "cmp $0, " #mp "; je 1f; lock; 1: "

extern "C" {
  inline jint _Atomic_add(jint add_value, volatile jint* dest, int mp) {
    jint addend = add_value;
    __asm__ volatile (  LOCK_IF_MP(%3) "xaddl %0,(%2)"
                    : "=r" (addend)
                    : "0" (addend), "r" (dest), "r" (mp)
                    : "cc", "memory");
    return addend + add_value;
  }

#ifdef AMD64
  inline jlong _Atomic_add_long(jlong add_value, volatile jlong* dest, int mp) {
    intptr_t addend = add_value;
    __asm__ __volatile__ (LOCK_IF_MP(%3) "xaddq %0,(%2)"
                        : "=r" (addend)
                        : "0" (addend), "r" (dest), "r" (mp)
                        : "cc", "memory");
    return addend + add_value;
  }

  inline jlong _Atomic_xchg_long(jlong exchange_value, volatile jlong* dest) {
    __asm__ __volatile__ ("xchgq (%2),%0"
                        : "=r" (exchange_value)
                        : "0" (exchange_value), "r" (dest)
                        : "memory");
    return exchange_value;
  }

#endif // AMD64

  inline jint _Atomic_xchg(jint exchange_value, volatile jint* dest) {
    __asm__ __volatile__ ("xchgl (%2),%0"
                          : "=r" (exchange_value)
                        : "0" (exchange_value), "r" (dest)
                        : "memory");
    return exchange_value;
  }

  inline jint _Atomic_cmpxchg(jint exchange_value, volatile jint* dest, jint compare_value, int mp) {
    __asm__ volatile (LOCK_IF_MP(%4) "cmpxchgl %1,(%3)"
                    : "=a" (exchange_value)
                    : "r" (exchange_value), "a" (compare_value), "r" (dest), "r" (mp)
                    : "cc", "memory");
    return exchange_value;
  }

  // This is the interface to the atomic instruction in solaris_i486.s.
  jlong _Atomic_cmpxchg_long_gcc(jlong exchange_value, volatile jlong* dest, jlong compare_value, int mp);

  inline jlong _Atomic_cmpxchg_long(jlong exchange_value, volatile jlong* dest, jlong compare_value, int mp) {
#ifdef AMD64
    __asm__ __volatile__ (LOCK_IF_MP(%4) "cmpxchgq %1,(%3)"
                        : "=a" (exchange_value)
                        : "r" (exchange_value), "a" (compare_value), "r" (dest), "r" (mp)
                        : "cc", "memory");
    return exchange_value;
#else
    return _Atomic_cmpxchg_long_gcc(exchange_value, dest, compare_value, os::is_MP());

    #if 0
    // The code below does not work presumably because of the bug in gcc
    // The error message says:
    //   can't find a register in class BREG while reloading asm
    // However I want to save this code and later replace _Atomic_cmpxchg_long_gcc
    // with such inline asm code:

    volatile jlong_accessor evl, cvl, rv;
    evl.long_value = exchange_value;
    cvl.long_value = compare_value;
    int mp = os::is_MP();

    __asm__ volatile ("cmp $0, %%esi\n\t"
       "je 1f \n\t"
       "lock\n\t"
       "1: cmpxchg8b (%%edi)\n\t"
       : "=a"(cvl.words[0]),   "=d"(cvl.words[1])
       : "a"(cvl.words[0]), "d"(cvl.words[1]),
         "b"(evl.words[0]), "c"(evl.words[1]),
         "D"(dest), "S"(mp)
       :  "cc", "memory");
    return cvl.long_value;
    #endif // if 0
#endif // AMD64
  }
}
#undef LOCK_IF_MP

#endif // _GNU_SOURCE
