/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_ATOMIC_INLINE_HPP
#define SHARE_VM_RUNTIME_ATOMIC_INLINE_HPP

#include "runtime/atomic.hpp"

// Linux
#ifdef TARGET_OS_ARCH_linux_x86
# include "atomic_linux_x86.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_sparc
# include "atomic_linux_sparc.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_zero
# include "atomic_linux_zero.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_arm
# include "atomic_linux_arm.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_ppc
# include "atomic_linux_ppc.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_aarch64
# include "atomic_linux_aarch64.inline.hpp"
#endif

// Solaris
#ifdef TARGET_OS_ARCH_solaris_x86
# include "atomic_solaris_x86.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_solaris_sparc
# include "atomic_solaris_sparc.inline.hpp"
#endif

// Windows
#ifdef TARGET_OS_ARCH_windows_x86
# include "atomic_windows_x86.inline.hpp"
#endif

// AIX
#ifdef TARGET_OS_ARCH_aix_ppc
# include "atomic_aix_ppc.inline.hpp"
#endif

// BSD
#ifdef TARGET_OS_ARCH_bsd_x86
# include "atomic_bsd_x86.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_bsd_zero
# include "atomic_bsd_zero.inline.hpp"
#endif

// size_t casts...
#if (SIZE_MAX != UINTPTR_MAX)
#error size_t is not WORD_SIZE, interesting platform, but missing implementation here
#endif

inline size_t Atomic::add(size_t add_value, volatile size_t* dest) {
  return (size_t) add_ptr((intptr_t) add_value, (volatile intptr_t*) dest);
}

inline void Atomic::inc(volatile size_t* dest) {
  inc_ptr((volatile intptr_t*) dest);
}

inline void Atomic::dec(volatile size_t* dest) {
  dec_ptr((volatile intptr_t*) dest);
}

#ifndef VM_HAS_SPECIALIZED_CMPXCHG_BYTE
/*
 * This is the default implementation of byte-sized cmpxchg. It emulates jbyte-sized cmpxchg
 * in terms of jint-sized cmpxchg. Platforms may override this by defining their own inline definition
 * as well as defining VM_HAS_SPECIALIZED_CMPXCHG_BYTE. This will cause the platform specific
 * implementation to be used instead.
 */
inline jbyte Atomic::cmpxchg(jbyte exchange_value, volatile jbyte *dest, jbyte comparand, cmpxchg_memory_order order)
{
  assert(sizeof(jbyte) == 1, "assumption.");
  uintptr_t dest_addr = (uintptr_t)dest;
  uintptr_t offset = dest_addr % sizeof(jint);
  volatile jint* dest_int = (volatile jint*)(dest_addr - offset);
  jint cur = *dest_int;
  jbyte* cur_as_bytes = (jbyte*)(&cur);
  jint new_val = cur;
  jbyte* new_val_as_bytes = (jbyte*)(&new_val);
  new_val_as_bytes[offset] = exchange_value;
  while (cur_as_bytes[offset] == comparand) {
    jint res = cmpxchg(new_val, dest_int, cur, order);
    if (res == cur) break;
    cur = res;
    new_val = cur;
    new_val_as_bytes[offset] = exchange_value;
  }
  return cur_as_bytes[offset];
}
#endif // VM_HAS_SPECIALIZED_CMPXCHG_BYTE

inline unsigned Atomic::xchg(unsigned int exchange_value, volatile unsigned int* dest) {
  assert(sizeof(unsigned int) == sizeof(jint), "more work to do");
  return (unsigned int)Atomic::xchg((jint)exchange_value, (volatile jint*)dest);
}

inline unsigned Atomic::cmpxchg(unsigned int exchange_value,
                         volatile unsigned int* dest, unsigned int compare_value,
                         cmpxchg_memory_order order) {
  assert(sizeof(unsigned int) == sizeof(jint), "more work to do");
  return (unsigned int)Atomic::cmpxchg((jint)exchange_value, (volatile jint*)dest,
                                       (jint)compare_value, order);
}

inline jlong Atomic::add(jlong    add_value, volatile jlong*    dest) {
  jlong old = load(dest);
  jlong new_value = old + add_value;
  while (old != cmpxchg(new_value, dest, old)) {
    old = load(dest);
    new_value = old + add_value;
  }
  return old;
}

inline void Atomic::inc(volatile short* dest) {
  // Most platforms do not support atomic increment on a 2-byte value. However,
  // if the value occupies the most significant 16 bits of an aligned 32-bit
  // word, then we can do this with an atomic add of 0x10000 to the 32-bit word.
  //
  // The least significant parts of this 32-bit word will never be affected, even
  // in case of overflow/underflow.
  //
  // Use the ATOMIC_SHORT_PAIR macro to get the desired alignment.
#ifdef VM_LITTLE_ENDIAN
  assert((intx(dest) & 0x03) == 0x02, "wrong alignment");
  (void)Atomic::add(0x10000, (volatile int*)(dest-1));
#else
  assert((intx(dest) & 0x03) == 0x00, "wrong alignment");
  (void)Atomic::add(0x10000, (volatile int*)(dest));
#endif
}

inline void Atomic::dec(volatile short* dest) {
#ifdef VM_LITTLE_ENDIAN
  assert((intx(dest) & 0x03) == 0x02, "wrong alignment");
  (void)Atomic::add(-0x10000, (volatile int*)(dest-1));
#else
  assert((intx(dest) & 0x03) == 0x00, "wrong alignment");
  (void)Atomic::add(-0x10000, (volatile int*)(dest));
#endif
}

#endif // SHARE_VM_RUNTIME_ATOMIC_INLINE_HPP
