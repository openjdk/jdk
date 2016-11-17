/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016 SAP SE. All rights reserved.
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

#ifndef OS_CPU_LINUX_S390_VM_ATOMIC_LINUX_S390_INLINE_HPP
#define OS_CPU_LINUX_S390_VM_ATOMIC_LINUX_S390_INLINE_HPP

#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "vm_version_s390.hpp"

// Note that the compare-and-swap instructions on System z perform
// a serialization function before the storage operand is fetched
// and again after the operation is completed.
//
// Used constraint modifiers:
// = write-only access: Value on entry to inline-assembler code irrelevant.
// + read/write access: Value on entry is used; on exit value is changed.
//   read-only  access: Value on entry is used and never changed.
// & early-clobber access: Might be modified before all read-only operands
//                         have been used.
// a address register operand (not GR0).
// d general register operand (including GR0)
// Q memory operand w/o index register.
// 0..9 operand reference (by operand position).
//      Used for operands that fill multiple roles. One example would be a
//      write-only operand receiving its initial value from a read-only operand.
//      Refer to cmpxchg(..) operand #0 and variable cmp_val for a real-life example.
//

// On System z, all store operations are atomic if the address where the data is stored into
// is an integer multiple of the data length. Furthermore, all stores are ordered:
// a store which occurs conceptually before another store becomes visible to other CPUs
// before the other store becomes visible.
inline void Atomic::store    (jbyte    store_value, jbyte*    dest) { *dest = store_value; }
inline void Atomic::store    (jshort   store_value, jshort*   dest) { *dest = store_value; }
inline void Atomic::store    (jint     store_value, jint*     dest) { *dest = store_value; }
inline void Atomic::store    (jlong    store_value, jlong*    dest) { *dest = store_value; }
inline void Atomic::store_ptr(intptr_t store_value, intptr_t* dest) { *dest = store_value; }
inline void Atomic::store_ptr(void*    store_value, void*     dest) { *(void**)dest = store_value; }

inline void Atomic::store    (jbyte    store_value, volatile jbyte*    dest) { *dest = store_value; }
inline void Atomic::store    (jshort   store_value, volatile jshort*   dest) { *dest = store_value; }
inline void Atomic::store    (jint     store_value, volatile jint*     dest) { *dest = store_value; }
inline void Atomic::store    (jlong    store_value, volatile jlong*    dest) { *dest = store_value; }
inline void Atomic::store_ptr(intptr_t store_value, volatile intptr_t* dest) { *dest = store_value; }
inline void Atomic::store_ptr(void*    store_value, volatile void*     dest) { *(void* volatile *)dest = store_value; }


//------------
// Atomic::add
//------------
// These methods force the value in memory to be augmented by the passed increment.
// Both, memory value and increment, are treated as 32bit signed binary integers.
// No overflow exceptions are recognized, and the condition code does not hold
// information about the value in memory.
//
// The value in memory is updated by using a compare-and-swap instruction. The
// instruction is retried as often as required.
//
// The return value of the method is the value that was successfully stored. At the
// time the caller receives back control, the value in memory may have changed already.

inline jint Atomic::add(jint inc, volatile jint*dest) {
  unsigned int old, upd;

  if (VM_Version::has_LoadAndALUAtomicV1()) {
    __asm__ __volatile__ (
      "   LGFR     0,%[inc]                \n\t" // save increment
      "   LA       3,%[mem]                \n\t" // force data address into ARG2
//    "   LAA      %[upd],%[inc],%[mem]    \n\t" // increment and get old value
//    "   LAA      2,0,0(3)                \n\t" // actually coded instruction
      "   .byte    0xeb                    \n\t" // LAA main opcode
      "   .byte    0x20                    \n\t" // R1,R3
      "   .byte    0x30                    \n\t" // R2,disp1
      "   .byte    0x00                    \n\t" // disp2,disp3
      "   .byte    0x00                    \n\t" // disp4,disp5
      "   .byte    0xf8                    \n\t" // LAA minor opcode
      "   AR       2,0                     \n\t" // calc new value in register
      "   LR       %[upd],2                \n\t" // move to result register
      //---<  outputs  >---
      : [upd]  "=&d" (upd)    // write-only, updated counter value
      , [mem]  "+Q"  (*dest)  // read/write, memory to be updated atomically
      //---<  inputs  >---
      : [inc]  "a"   (inc)    // read-only.
      //---<  clobbered  >---
      : "cc", "r0", "r2", "r3"
    );
  } else {
    __asm__ __volatile__ (
      "   LLGF     %[old],%[mem]           \n\t" // get old value
      "0: LA       %[upd],0(%[inc],%[old]) \n\t" // calc result
      "   CS       %[old],%[upd],%[mem]    \n\t" // try to xchg res with mem
      "   JNE      0b                      \n\t" // no success? -> retry
      //---<  outputs  >---
      : [old] "=&a" (old)    // write-only, old counter value
      , [upd] "=&d" (upd)    // write-only, updated counter value
      , [mem] "+Q"  (*dest)  // read/write, memory to be updated atomically
      //---<  inputs  >---
      : [inc] "a"   (inc)    // read-only.
      //---<  clobbered  >---
      : "cc"
    );
  }

  return (jint)upd;
}


inline intptr_t Atomic::add_ptr(intptr_t inc, volatile intptr_t* dest) {
  unsigned long old, upd;

  if (VM_Version::has_LoadAndALUAtomicV1()) {
    __asm__ __volatile__ (
      "   LGR      0,%[inc]                \n\t" // save increment
      "   LA       3,%[mem]                \n\t" // force data address into ARG2
//    "   LAAG     %[upd],%[inc],%[mem]    \n\t" // increment and get old value
//    "   LAAG     2,0,0(3)                \n\t" // actually coded instruction
      "   .byte    0xeb                    \n\t" // LAA main opcode
      "   .byte    0x20                    \n\t" // R1,R3
      "   .byte    0x30                    \n\t" // R2,disp1
      "   .byte    0x00                    \n\t" // disp2,disp3
      "   .byte    0x00                    \n\t" // disp4,disp5
      "   .byte    0xe8                    \n\t" // LAA minor opcode
      "   AGR      2,0                     \n\t" // calc new value in register
      "   LGR      %[upd],2                \n\t" // move to result register
      //---<  outputs  >---
      : [upd]  "=&d" (upd)    // write-only, updated counter value
      , [mem]  "+Q"  (*dest)  // read/write, memory to be updated atomically
      //---<  inputs  >---
      : [inc]  "a"   (inc)    // read-only.
      //---<  clobbered  >---
      : "cc", "r0", "r2", "r3"
    );
  } else {
    __asm__ __volatile__ (
      "   LG       %[old],%[mem]           \n\t" // get old value
      "0: LA       %[upd],0(%[inc],%[old]) \n\t" // calc result
      "   CSG      %[old],%[upd],%[mem]    \n\t" // try to xchg res with mem
      "   JNE      0b                      \n\t" // no success? -> retry
      //---<  outputs  >---
      : [old] "=&a" (old)    // write-only, old counter value
      , [upd] "=&d" (upd)    // write-only, updated counter value
      , [mem] "+Q"  (*dest)  // read/write, memory to be updated atomically
      //---<  inputs  >---
      : [inc] "a"   (inc)    // read-only.
      //---<  clobbered  >---
      : "cc"
    );
  }

  return (intptr_t)upd;
}

inline void* Atomic::add_ptr(intptr_t add_value, volatile void* dest) {
  return (void*)add_ptr(add_value, (volatile intptr_t*)dest);
}


//------------
// Atomic::inc
//------------
// These methods force the value in memory to be incremented (augmented by 1).
// Both, memory value and increment, are treated as 32bit signed binary integers.
// No overflow exceptions are recognized, and the condition code does not hold
// information about the value in memory.
//
// The value in memory is updated by using a compare-and-swap instruction. The
// instruction is retried as often as required.

inline void Atomic::inc(volatile jint* dest) {
  unsigned int old, upd;

  if (VM_Version::has_LoadAndALUAtomicV1()) {
//  tty->print_cr("Atomic::inc     called... dest @%p", dest);
    __asm__ __volatile__ (
      "   LGHI     2,1                     \n\t" // load increment
      "   LA       3,%[mem]                \n\t" // force data address into ARG2
//    "   LAA      %[upd],%[inc],%[mem]    \n\t" // increment and get old value
//    "   LAA      2,2,0(3)                \n\t" // actually coded instruction
      "   .byte    0xeb                    \n\t" // LAA main opcode
      "   .byte    0x22                    \n\t" // R1,R3
      "   .byte    0x30                    \n\t" // R2,disp1
      "   .byte    0x00                    \n\t" // disp2,disp3
      "   .byte    0x00                    \n\t" // disp4,disp5
      "   .byte    0xf8                    \n\t" // LAA minor opcode
      "   AGHI     2,1                     \n\t" // calc new value in register
      "   LR       %[upd],2                \n\t" // move to result register
      //---<  outputs  >---
      : [upd]  "=&d" (upd)    // write-only, updated counter value
      , [mem]  "+Q"  (*dest)  // read/write, memory to be updated atomically
      //---<  inputs  >---
      :
//    : [inc]  "a"   (inc)    // read-only.
      //---<  clobbered  >---
      : "cc", "r2", "r3"
    );
  } else {
    __asm__ __volatile__ (
      "   LLGF     %[old],%[mem]           \n\t" // get old value
      "0: LA       %[upd],1(,%[old])       \n\t" // calc result
      "   CS       %[old],%[upd],%[mem]    \n\t" // try to xchg res with mem
      "   JNE      0b                      \n\t" // no success? -> retry
      //---<  outputs  >---
      : [old] "=&a" (old)    // write-only, old counter value
      , [upd] "=&d" (upd)    // write-only, updated counter value
      , [mem] "+Q"  (*dest)  // read/write, memory to be updated atomically
      //---<  inputs  >---
      :
      //---<  clobbered  >---
      : "cc"
    );
  }
}

inline void Atomic::inc_ptr(volatile intptr_t* dest) {
  unsigned long old, upd;

  if (VM_Version::has_LoadAndALUAtomicV1()) {
    __asm__ __volatile__ (
      "   LGHI     2,1                     \n\t" // load increment
      "   LA       3,%[mem]                \n\t" // force data address into ARG2
//    "   LAAG     %[upd],%[inc],%[mem]    \n\t" // increment and get old value
//    "   LAAG     2,2,0(3)                \n\t" // actually coded instruction
      "   .byte    0xeb                    \n\t" // LAA main opcode
      "   .byte    0x22                    \n\t" // R1,R3
      "   .byte    0x30                    \n\t" // R2,disp1
      "   .byte    0x00                    \n\t" // disp2,disp3
      "   .byte    0x00                    \n\t" // disp4,disp5
      "   .byte    0xe8                    \n\t" // LAA minor opcode
      "   AGHI     2,1                     \n\t" // calc new value in register
      "   LR       %[upd],2                \n\t" // move to result register
      //---<  outputs  >---
      : [upd]  "=&d" (upd)    // write-only, updated counter value
      , [mem]  "+Q"  (*dest)  // read/write, memory to be updated atomically
      //---<  inputs  >---
      :
//    : [inc]  "a"   (inc)    // read-only.
      //---<  clobbered  >---
      : "cc", "r2", "r3"
    );
  } else {
    __asm__ __volatile__ (
      "   LG       %[old],%[mem]           \n\t" // get old value
      "0: LA       %[upd],1(,%[old])       \n\t" // calc result
      "   CSG      %[old],%[upd],%[mem]    \n\t" // try to xchg res with mem
      "   JNE      0b                      \n\t" // no success? -> retry
      //---<  outputs  >---
      : [old] "=&a" (old)    // write-only, old counter value
      , [upd] "=&d" (upd)    // write-only, updated counter value
      , [mem] "+Q"  (*dest)  // read/write, memory to be updated atomically
      //---<  inputs  >---
      :
      //---<  clobbered  >---
      : "cc"
    );
  }
}

inline void Atomic::inc_ptr(volatile void* dest) {
  inc_ptr((volatile intptr_t*)dest);
}

//------------
// Atomic::dec
//------------
// These methods force the value in memory to be decremented (augmented by -1).
// Both, memory value and decrement, are treated as 32bit signed binary integers.
// No overflow exceptions are recognized, and the condition code does not hold
// information about the value in memory.
//
// The value in memory is updated by using a compare-and-swap instruction. The
// instruction is retried as often as required.

inline void Atomic::dec(volatile jint* dest) {
  unsigned int old, upd;

  if (VM_Version::has_LoadAndALUAtomicV1()) {
    __asm__ __volatile__ (
      "   LGHI     2,-1                    \n\t" // load increment
      "   LA       3,%[mem]                \n\t" // force data address into ARG2
//    "   LAA      %[upd],%[inc],%[mem]    \n\t" // increment and get old value
//    "   LAA      2,2,0(3)                \n\t" // actually coded instruction
      "   .byte    0xeb                    \n\t" // LAA main opcode
      "   .byte    0x22                    \n\t" // R1,R3
      "   .byte    0x30                    \n\t" // R2,disp1
      "   .byte    0x00                    \n\t" // disp2,disp3
      "   .byte    0x00                    \n\t" // disp4,disp5
      "   .byte    0xf8                    \n\t" // LAA minor opcode
      "   AGHI     2,-1                    \n\t" // calc new value in register
      "   LR       %[upd],2                \n\t" // move to result register
      //---<  outputs  >---
      : [upd]  "=&d" (upd)    // write-only, updated counter value
      , [mem]  "+Q"  (*dest)  // read/write, memory to be updated atomically
      //---<  inputs  >---
      :
//    : [inc]  "a"   (inc)    // read-only.
      //---<  clobbered  >---
      : "cc", "r2", "r3"
    );
  } else {
    __asm__ __volatile__ (
      "   LLGF     %[old],%[mem]           \n\t" // get old value
  // LAY not supported by inline assembler
  //  "0: LAY      %[upd],-1(,%[old])      \n\t" // calc result
      "0: LR       %[upd],%[old]           \n\t" // calc result
      "   AHI      %[upd],-1               \n\t"
      "   CS       %[old],%[upd],%[mem]    \n\t" // try to xchg res with mem
      "   JNE      0b                      \n\t" // no success? -> retry
      //---<  outputs  >---
      : [old] "=&a" (old)    // write-only, old counter value
      , [upd] "=&d" (upd)    // write-only, updated counter value
      , [mem] "+Q"  (*dest)  // read/write, memory to be updated atomically
      //---<  inputs  >---
      :
      //---<  clobbered  >---
      : "cc"
    );
  }
}

inline void Atomic::dec_ptr(volatile intptr_t* dest) {
  unsigned long old, upd;

  if (VM_Version::has_LoadAndALUAtomicV1()) {
    __asm__ __volatile__ (
      "   LGHI     2,-1                    \n\t" // load increment
      "   LA       3,%[mem]                \n\t" // force data address into ARG2
//    "   LAAG     %[upd],%[inc],%[mem]    \n\t" // increment and get old value
//    "   LAAG     2,2,0(3)                \n\t" // actually coded instruction
      "   .byte    0xeb                    \n\t" // LAA main opcode
      "   .byte    0x22                    \n\t" // R1,R3
      "   .byte    0x30                    \n\t" // R2,disp1
      "   .byte    0x00                    \n\t" // disp2,disp3
      "   .byte    0x00                    \n\t" // disp4,disp5
      "   .byte    0xe8                    \n\t" // LAA minor opcode
      "   AGHI     2,-1                    \n\t" // calc new value in register
      "   LR       %[upd],2                \n\t" // move to result register
      //---<  outputs  >---
      : [upd]  "=&d" (upd)    // write-only, updated counter value
      , [mem]  "+Q"  (*dest)  // read/write, memory to be updated atomically
      //---<  inputs  >---
      :
//    : [inc]  "a"   (inc)    // read-only.
      //---<  clobbered  >---
      : "cc", "r2", "r3"
    );
  } else {
    __asm__ __volatile__ (
      "   LG       %[old],%[mem]           \n\t" // get old value
//    LAY not supported by inline assembler
//    "0: LAY      %[upd],-1(,%[old])      \n\t" // calc result
      "0: LGR      %[upd],%[old]           \n\t" // calc result
      "   AGHI     %[upd],-1               \n\t"
      "   CSG      %[old],%[upd],%[mem]    \n\t" // try to xchg res with mem
      "   JNE      0b                      \n\t" // no success? -> retry
      //---<  outputs  >---
      : [old] "=&a" (old)    // write-only, old counter value
      , [upd] "=&d" (upd)    // write-only, updated counter value
      , [mem] "+Q"  (*dest)  // read/write, memory to be updated atomically
      //---<  inputs  >---
      :
      //---<  clobbered  >---
      : "cc"
    );
  }
}

inline void Atomic::dec_ptr(volatile void* dest) {
  dec_ptr((volatile intptr_t*)dest);
}

//-------------
// Atomic::xchg
//-------------
// These methods force the value in memory to be replaced by the new value passed
// in as argument.
//
// The value in memory is replaced by using a compare-and-swap instruction. The
// instruction is retried as often as required. This makes sure that the new
// value can be seen, at least for a very short period of time, by other CPUs.
//
// If we would use a normal "load(old value) store(new value)" sequence,
// the new value could be lost unnoticed, due to a store(new value) from
// another thread.
//
// The return value is the (unchanged) value from memory as it was when the
// replacement succeeded.
inline jint Atomic::xchg (jint xchg_val, volatile jint* dest) {
  unsigned int  old;

  __asm__ __volatile__ (
    "   LLGF     %[old],%[mem]           \n\t" // get old value
    "0: CS       %[old],%[upd],%[mem]    \n\t" // try to xchg upd with mem
    "   JNE      0b                      \n\t" // no success? -> retry
    //---<  outputs  >---
    : [old] "=&d" (old)      // write-only, prev value irrelevant
    , [mem] "+Q"  (*dest)    // read/write, memory to be updated atomically
    //---<  inputs  >---
    : [upd] "d"   (xchg_val) // read-only, value to be written to memory
    //---<  clobbered  >---
    : "cc"
  );

  return (jint)old;
}

inline intptr_t Atomic::xchg_ptr(intptr_t xchg_val, volatile intptr_t* dest) {
  unsigned long old;

  __asm__ __volatile__ (
    "   LG       %[old],%[mem]           \n\t" // get old value
    "0: CSG      %[old],%[upd],%[mem]    \n\t" // try to xchg upd with mem
    "   JNE      0b                      \n\t" // no success? -> retry
    //---<  outputs  >---
    : [old] "=&d" (old)      // write-only, init from memory
    , [mem] "+Q"  (*dest)    // read/write, memory to be updated atomically
    //---<  inputs  >---
    : [upd] "d"   (xchg_val) // read-only, value to be written to memory
    //---<  clobbered  >---
    : "cc"
  );

  return (intptr_t)old;
}

inline void *Atomic::xchg_ptr(void *exchange_value, volatile void *dest) {
  return (void*)xchg_ptr((intptr_t)exchange_value, (volatile intptr_t*)dest);
}

//----------------
// Atomic::cmpxchg
//----------------
// These methods compare the value in memory with a given compare value.
// If both values compare equal, the value in memory is replaced with
// the exchange value.
//
// The value in memory is compared and replaced by using a compare-and-swap
// instruction. The instruction is NOT retried (one shot only).
//
// The return value is the (unchanged) value from memory as it was when the
// compare-and-swap instruction completed. A successful exchange operation
// is indicated by (return value == compare_value). If unsuccessful, a new
// exchange value can be calculated based on the return value which is the
// latest contents of the memory location.
//
// Inspecting the return value is the only way for the caller to determine
// if the compare-and-swap instruction was successful:
// - If return value and compare value compare equal, the compare-and-swap
//   instruction was successful and the value in memory was replaced by the
//   exchange value.
// - If return value and compare value compare unequal, the compare-and-swap
//   instruction was not successful. The value in memory was left unchanged.
//
// The s390 processors always fence before and after the csg instructions.
// Thus we ignore the memory ordering argument. The docu says: "A serialization
// function is performed before the operand is fetched and again after the
// operation is completed."

jint Atomic::cmpxchg(jint xchg_val, volatile jint* dest, jint cmp_val, cmpxchg_memory_order unused) {
  unsigned long old;

  __asm__ __volatile__ (
    "   CS       %[old],%[upd],%[mem]    \n\t" // Try to xchg upd with mem.
    // outputs
    : [old] "=&d" (old)      // Write-only, prev value irrelevant.
    , [mem] "+Q"  (*dest)    // Read/write, memory to be updated atomically.
    // inputs
    : [upd] "d"   (xchg_val)
    ,       "0"   (cmp_val)  // Read-only, initial value for [old] (operand #0).
    // clobbered
    : "cc"
  );

  return (jint)old;
}

jlong Atomic::cmpxchg(jlong xchg_val, volatile jlong* dest, jlong cmp_val, cmpxchg_memory_order unused) {
  unsigned long old;

  __asm__ __volatile__ (
    "   CSG      %[old],%[upd],%[mem]    \n\t" // Try to xchg upd with mem.
    // outputs
    : [old] "=&d" (old)      // Write-only, prev value irrelevant.
    , [mem] "+Q"  (*dest)    // Read/write, memory to be updated atomically.
    // inputs
    : [upd] "d"   (xchg_val)
    ,       "0"   (cmp_val)  // Read-only, initial value for [old] (operand #0).
    // clobbered
    : "cc"
  );

  return (jlong)old;
}

void* Atomic::cmpxchg_ptr(void *xchg_val, volatile void* dest, void* cmp_val, cmpxchg_memory_order unused) {
  return (void*)cmpxchg((jlong)xchg_val, (volatile jlong*)dest, (jlong)cmp_val, unused);
}

intptr_t Atomic::cmpxchg_ptr(intptr_t xchg_val, volatile intptr_t* dest, intptr_t cmp_val, cmpxchg_memory_order unused) {
  return (intptr_t)cmpxchg((jlong)xchg_val, (volatile jlong*)dest, (jlong)cmp_val, unused);
}

inline jlong Atomic::load(volatile jlong* src) { return *src; }

#endif // OS_CPU_LINUX_S390_VM_ATOMIC_LINUX_S390_INLINE_HPP
