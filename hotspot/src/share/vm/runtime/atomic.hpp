/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_ATOMIC_HPP
#define SHARE_VM_RUNTIME_ATOMIC_HPP

#include "memory/allocation.hpp"

class Atomic : AllStatic {
 public:
  // Atomically store to a location
  static void store    (jbyte    store_value, jbyte*    dest);
  static void store    (jshort   store_value, jshort*   dest);
  static void store    (jint     store_value, jint*     dest);
  static void store    (jlong    store_value, jlong*    dest);
  static void store_ptr(intptr_t store_value, intptr_t* dest);
  static void store_ptr(void*    store_value, void*     dest);

  static void store    (jbyte    store_value, volatile jbyte*    dest);
  static void store    (jshort   store_value, volatile jshort*   dest);
  static void store    (jint     store_value, volatile jint*     dest);
  static void store    (jlong    store_value, volatile jlong*    dest);
  static void store_ptr(intptr_t store_value, volatile intptr_t* dest);
  static void store_ptr(void*    store_value, volatile void*     dest);

  static jlong load(volatile jlong* src);

  // Atomically add to a location, return updated value
  static jint     add    (jint     add_value, volatile jint*     dest);
  static intptr_t add_ptr(intptr_t add_value, volatile intptr_t* dest);
  static void*    add_ptr(intptr_t add_value, volatile void*     dest);

  // Atomically increment location
  static void inc    (volatile jint*     dest);
  static void inc_ptr(volatile intptr_t* dest);
  static void inc_ptr(volatile void*     dest);

  // Atomically decrement a location
  static void dec    (volatile jint*     dest);
  static void dec_ptr(volatile intptr_t* dest);
  static void dec_ptr(volatile void*     dest);

  // Performs atomic exchange of *dest with exchange_value.  Returns old prior value of *dest.
  static jint         xchg(jint     exchange_value, volatile jint*     dest);
  static unsigned int xchg(unsigned int exchange_value,
                           volatile unsigned int* dest);

  static intptr_t xchg_ptr(intptr_t exchange_value, volatile intptr_t* dest);
  static void*    xchg_ptr(void*    exchange_value, volatile void*   dest);

  // Performs atomic compare of *dest and compare_value, and exchanges *dest with exchange_value
  // if the comparison succeeded.  Returns prior value of *dest.  Guarantees a two-way memory
  // barrier across the cmpxchg.  I.e., it's really a 'fence_cmpxchg_acquire'.
  static jbyte    cmpxchg    (jbyte    exchange_value, volatile jbyte*    dest, jbyte    compare_value);
  static jint     cmpxchg    (jint     exchange_value, volatile jint*     dest, jint     compare_value);
  static jlong    cmpxchg    (jlong    exchange_value, volatile jlong*    dest, jlong    compare_value);

  static unsigned int cmpxchg(unsigned int exchange_value,
                              volatile unsigned int* dest,
                              unsigned int compare_value);

  static intptr_t cmpxchg_ptr(intptr_t exchange_value, volatile intptr_t* dest, intptr_t compare_value);
  static void*    cmpxchg_ptr(void*    exchange_value, volatile void*     dest, void*    compare_value);
};

#endif // SHARE_VM_RUNTIME_ATOMIC_HPP
