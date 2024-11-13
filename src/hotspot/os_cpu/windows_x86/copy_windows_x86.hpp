/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_WINDOWS_X86_COPY_WINDOWS_X86_HPP
#define OS_CPU_WINDOWS_X86_COPY_WINDOWS_X86_HPP

#include "runtime/atomic.hpp"

template <typename T>
static void pd_conjoint_atomic_helper(const T* from, T* to, size_t count) {
  if (from > to) {
    while (count-- > 0) {
      // Copy forwards
      Atomic::store(to++, Atomic::load(from++));
    }
  } else {
    from += count - 1;
    to   += count - 1;
    while (count-- > 0) {
      // Copy backwards
      Atomic::store(to--, Atomic::load(from--));
    }
  }
}

static void pd_conjoint_jshorts_atomic(const jshort* from, jshort* to, size_t count) {
  pd_conjoint_atomic_helper(from, to, count);
}

static void pd_conjoint_jints_atomic(const jint* from, jint* to, size_t count) {
  pd_conjoint_atomic_helper(from, to, count);
}

static void pd_conjoint_jlongs_atomic(const jlong* from, jlong* to, size_t count) {
  pd_conjoint_atomic_helper(from, to, count);
}

static void pd_conjoint_oops_atomic(const oop* from, oop* to, size_t count) {
  pd_conjoint_atomic_helper(from, to, count);
}

static void pd_arrayof_conjoint_bytes(const HeapWord* from, HeapWord* to, size_t count) {
  pd_conjoint_bytes_atomic(from, to, count);
}

static void pd_arrayof_conjoint_jshorts(const HeapWord* from, HeapWord* to, size_t count) {
  pd_conjoint_jshorts_atomic((const jshort*)from, (jshort*)to, count);
}

static void pd_arrayof_conjoint_jints(const HeapWord* from, HeapWord* to, size_t count) {
  pd_conjoint_jints_atomic((const jint*)from, (jint*)to, count);
}

static void pd_arrayof_conjoint_jlongs(const HeapWord* from, HeapWord* to, size_t count) {
  pd_conjoint_jlongs_atomic((const jlong*)from, (jlong*)to, count);
}

static void pd_arrayof_conjoint_oops(const HeapWord* from, HeapWord* to, size_t count) {
  pd_conjoint_oops_atomic((const oop*)from, (oop*)to, count);
}

#endif // OS_CPU_WINDOWS_X86_COPY_WINDOWS_X86_HPP
