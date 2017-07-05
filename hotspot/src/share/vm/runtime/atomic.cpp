/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "runtime/atomic.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "os_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "os_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "os_windows.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "os_bsd.inline.hpp"
#endif

#include "runtime/atomic.inline.hpp"

jbyte Atomic::cmpxchg(jbyte exchange_value, volatile jbyte* dest, jbyte compare_value) {
  assert(sizeof(jbyte) == 1, "assumption.");
  uintptr_t dest_addr = (uintptr_t)dest;
  uintptr_t offset = dest_addr % sizeof(jint);
  volatile jint* dest_int = (volatile jint*)(dest_addr - offset);
  jint cur = *dest_int;
  jbyte* cur_as_bytes = (jbyte*)(&cur);
  jint new_val = cur;
  jbyte* new_val_as_bytes = (jbyte*)(&new_val);
  new_val_as_bytes[offset] = exchange_value;
  while (cur_as_bytes[offset] == compare_value) {
    jint res = cmpxchg(new_val, dest_int, cur);
    if (res == cur) break;
    cur = res;
    new_val = cur;
    new_val_as_bytes[offset] = exchange_value;
  }
  return cur_as_bytes[offset];
}

unsigned Atomic::xchg(unsigned int exchange_value, volatile unsigned int* dest) {
  assert(sizeof(unsigned int) == sizeof(jint), "more work to do");
  return (unsigned int)Atomic::xchg((jint)exchange_value, (volatile jint*)dest);
}

unsigned Atomic::cmpxchg(unsigned int exchange_value,
                         volatile unsigned int* dest, unsigned int compare_value) {
  assert(sizeof(unsigned int) == sizeof(jint), "more work to do");
  return (unsigned int)Atomic::cmpxchg((jint)exchange_value, (volatile jint*)dest,
                                       (jint)compare_value);
}

jlong Atomic::add(jlong    add_value, volatile jlong*    dest) {
  jlong old = load(dest);
  jlong new_value = old + add_value;
  while (old != cmpxchg(new_value, dest, old)) {
    old = load(dest);
    new_value = old + add_value;
  }
  return old;
}

void Atomic::inc(volatile short* dest) {
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

void Atomic::dec(volatile short* dest) {
#ifdef VM_LITTLE_ENDIAN
  assert((intx(dest) & 0x03) == 0x02, "wrong alignment");
  (void)Atomic::add(-0x10000, (volatile int*)(dest-1));
#else
  assert((intx(dest) & 0x03) == 0x00, "wrong alignment");
  (void)Atomic::add(-0x10000, (volatile int*)(dest));
#endif
}

