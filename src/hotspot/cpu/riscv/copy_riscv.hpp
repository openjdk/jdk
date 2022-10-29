/*
 * Copyright (c) 2003, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef CPU_RISCV_COPY_RISCV_HPP
#define CPU_RISCV_COPY_RISCV_HPP

#include OS_CPU_HEADER(copy)

static void pd_fill_to_words(HeapWord* tohw, size_t count, juint value) {
  julong* to = (julong*) tohw;
  julong  v  = ((julong) value << 32) | value;
  while (count-- > 0) {
    *to++ = v;
  }
}

static void pd_fill_to_aligned_words(HeapWord* tohw, size_t count, juint value) {
  pd_fill_to_words(tohw, count, value);
}

static void pd_fill_to_bytes(void* to, size_t count, jubyte value) {
  (void)memset(to, value, count);
}

static void pd_zero_to_words(HeapWord* tohw, size_t count) {
  pd_fill_to_words(tohw, count, 0);
}

static void pd_zero_to_bytes(void* to, size_t count) {
  (void)memset(to, 0, count);
}

static void pd_conjoint_words(const HeapWord* from, HeapWord* to, size_t count) {
  (void)memmove(to, from, count * HeapWordSize);
}

static void pd_disjoint_words(const HeapWord* from, HeapWord* to, size_t count) {
  switch (count) {
    case 8:  to[7] = from[7];   // fall through
    case 7:  to[6] = from[6];   // fall through
    case 6:  to[5] = from[5];   // fall through
    case 5:  to[4] = from[4];   // fall through
    case 4:  to[3] = from[3];   // fall through
    case 3:  to[2] = from[2];   // fall through
    case 2:  to[1] = from[1];   // fall through
    case 1:  to[0] = from[0];   // fall through
    case 0:  break;
    default:
      memcpy(to, from, count * HeapWordSize);
      break;
  }
}

static void pd_disjoint_words_atomic(const HeapWord* from, HeapWord* to, size_t count) {
  shared_disjoint_words_atomic(from, to, count);
}

static void pd_aligned_conjoint_words(const HeapWord* from, HeapWord* to, size_t count) {
  pd_conjoint_words(from, to, count);
}

static void pd_aligned_disjoint_words(const HeapWord* from, HeapWord* to, size_t count) {
  pd_disjoint_words(from, to, count);
}

static void pd_conjoint_bytes(const void* from, void* to, size_t count) {
  (void)memmove(to, from, count);
}

static void pd_conjoint_bytes_atomic(const void* from, void* to, size_t count) {
  pd_conjoint_bytes(from, to, count);
}

static void pd_conjoint_jshorts_atomic(const jshort* from, jshort* to, size_t count) {
  _Copy_conjoint_jshorts_atomic(from, to, count);
}

static void pd_conjoint_jints_atomic(const jint* from, jint* to, size_t count) {
  _Copy_conjoint_jints_atomic(from, to, count);
}

static void pd_conjoint_jlongs_atomic(const jlong* from, jlong* to, size_t count) {
  _Copy_conjoint_jlongs_atomic(from, to, count);
}

static void pd_conjoint_oops_atomic(const oop* from, oop* to, size_t count) {
  assert(BytesPerLong == BytesPerOop, "jlongs and oops must be the same size.");
  _Copy_conjoint_jlongs_atomic((const jlong*)from, (jlong*)to, count);
}

static void pd_arrayof_conjoint_bytes(const HeapWord* from, HeapWord* to, size_t count) {
  _Copy_arrayof_conjoint_bytes(from, to, count);
}

static void pd_arrayof_conjoint_jshorts(const HeapWord* from, HeapWord* to, size_t count) {
  _Copy_arrayof_conjoint_jshorts(from, to, count);
}

static void pd_arrayof_conjoint_jints(const HeapWord* from, HeapWord* to, size_t count) {
  _Copy_arrayof_conjoint_jints(from, to, count);
}

static void pd_arrayof_conjoint_jlongs(const HeapWord* from, HeapWord* to, size_t count) {
  _Copy_arrayof_conjoint_jlongs(from, to, count);
}

static void pd_arrayof_conjoint_oops(const HeapWord* from, HeapWord* to, size_t count) {
  assert(!UseCompressedOops, "foo!");
  assert(BytesPerLong == BytesPerOop, "jlongs and oops must be the same size");
  _Copy_arrayof_conjoint_jlongs(from, to, count);
}

#endif // CPU_RISCV_COPY_RISCV_HPP
