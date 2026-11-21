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

#define COPY_SMALL(from, to, count) do {                                       \
  assert((count) <= 8, "small copy only");                                     \
  julong tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7;                       \
  __asm__ volatile(                                                            \
    "li %[t0], 4\n"                                                            \
    "bgeu %[cnt], %[t0], 4f\n"                                                 \
    "li %[t0], 2\n"                                                            \
    "bgeu %[cnt], %[t0], 2f\n"                                                 \
    "beqz %[cnt], 9f\n"                                                        \
    "ld %[t0], 0(%[s])\n"                                                      \
    "sd %[t0], 0(%[d])\n"                                                      \
    "j 9f\n"                                                                   \
    "2:\n"                                                                     \
    "bne %[cnt], %[t0], 3f\n"                                                  \
    "ld %[t0], 0(%[s])\n"                                                      \
    "ld %[t1], 8(%[s])\n"                                                      \
    "sd %[t0], 0(%[d])\n"                                                      \
    "sd %[t1], 8(%[d])\n"                                                      \
    "j 9f\n"                                                                   \
    "3:\n"                                                                     \
    "ld %[t0], 0(%[s])\n"                                                      \
    "ld %[t1], 8(%[s])\n"                                                      \
    "ld %[t2], 16(%[s])\n"                                                     \
    "sd %[t0], 0(%[d])\n"                                                      \
    "sd %[t1], 8(%[d])\n"                                                      \
    "sd %[t2], 16(%[d])\n"                                                     \
    "j 9f\n"                                                                   \
    "4:\n"                                                                     \
    "li %[t0], 6\n"                                                            \
    "bgeu %[cnt], %[t0], 6f\n"                                                 \
    "li %[t0], 4\n"                                                            \
    "bne %[cnt], %[t0], 5f\n"                                                  \
    "ld %[t0], 0(%[s])\n"                                                      \
    "ld %[t1], 8(%[s])\n"                                                      \
    "ld %[t2], 16(%[s])\n"                                                     \
    "ld %[t3], 24(%[s])\n"                                                     \
    "sd %[t0], 0(%[d])\n"                                                      \
    "sd %[t1], 8(%[d])\n"                                                      \
    "sd %[t2], 16(%[d])\n"                                                     \
    "sd %[t3], 24(%[d])\n"                                                     \
    "j 9f\n"                                                                   \
    "5:\n"                                                                     \
    "ld %[t0], 0(%[s])\n"                                                      \
    "ld %[t1], 8(%[s])\n"                                                      \
    "ld %[t2], 16(%[s])\n"                                                     \
    "ld %[t3], 24(%[s])\n"                                                     \
    "ld %[t4], 32(%[s])\n"                                                     \
    "sd %[t0], 0(%[d])\n"                                                      \
    "sd %[t1], 8(%[d])\n"                                                      \
    "sd %[t2], 16(%[d])\n"                                                     \
    "sd %[t3], 24(%[d])\n"                                                     \
    "sd %[t4], 32(%[d])\n"                                                     \
    "j 9f\n"                                                                   \
    "6:\n"                                                                     \
    "li %[t0], 8\n"                                                            \
    "beq %[cnt], %[t0], 8f\n"                                                  \
    "li %[t0], 6\n"                                                            \
    "bne %[cnt], %[t0], 7f\n"                                                  \
    "ld %[t0], 0(%[s])\n"                                                      \
    "ld %[t1], 8(%[s])\n"                                                      \
    "ld %[t2], 16(%[s])\n"                                                     \
    "ld %[t3], 24(%[s])\n"                                                     \
    "ld %[t4], 32(%[s])\n"                                                     \
    "ld %[t5], 40(%[s])\n"                                                     \
    "sd %[t0], 0(%[d])\n"                                                      \
    "sd %[t1], 8(%[d])\n"                                                      \
    "sd %[t2], 16(%[d])\n"                                                     \
    "sd %[t3], 24(%[d])\n"                                                     \
    "sd %[t4], 32(%[d])\n"                                                     \
    "sd %[t5], 40(%[d])\n"                                                     \
    "j 9f\n"                                                                   \
    "7:\n"                                                                     \
    "ld %[t0], 0(%[s])\n"                                                      \
    "ld %[t1], 8(%[s])\n"                                                      \
    "ld %[t2], 16(%[s])\n"                                                     \
    "ld %[t3], 24(%[s])\n"                                                     \
    "ld %[t4], 32(%[s])\n"                                                     \
    "ld %[t5], 40(%[s])\n"                                                     \
    "ld %[t6], 48(%[s])\n"                                                     \
    "sd %[t0], 0(%[d])\n"                                                      \
    "sd %[t1], 8(%[d])\n"                                                      \
    "sd %[t2], 16(%[d])\n"                                                     \
    "sd %[t3], 24(%[d])\n"                                                     \
    "sd %[t4], 32(%[d])\n"                                                     \
    "sd %[t5], 40(%[d])\n"                                                     \
    "sd %[t6], 48(%[d])\n"                                                     \
    "j 9f\n"                                                                   \
    "8:\n"                                                                     \
    "ld %[t0], 0(%[s])\n"                                                      \
    "ld %[t1], 8(%[s])\n"                                                      \
    "ld %[t2], 16(%[s])\n"                                                     \
    "ld %[t3], 24(%[s])\n"                                                     \
    "ld %[t4], 32(%[s])\n"                                                     \
    "ld %[t5], 40(%[s])\n"                                                     \
    "ld %[t6], 48(%[s])\n"                                                     \
    "ld %[t7], 56(%[s])\n"                                                     \
    "sd %[t0], 0(%[d])\n"                                                      \
    "sd %[t1], 8(%[d])\n"                                                      \
    "sd %[t2], 16(%[d])\n"                                                     \
    "sd %[t3], 24(%[d])\n"                                                     \
    "sd %[t4], 32(%[d])\n"                                                     \
    "sd %[t5], 40(%[d])\n"                                                     \
    "sd %[t6], 48(%[d])\n"                                                     \
    "sd %[t7], 56(%[d])\n"                                                     \
    "9:\n"                                                                     \
    : [t0] "=&r" (tmp0), [t1] "=&r" (tmp1),                                    \
      [t2] "=&r" (tmp2), [t3] "=&r" (tmp3),                                    \
      [t4] "=&r" (tmp4), [t5] "=&r" (tmp5),                                    \
      [t6] "=&r" (tmp6), [t7] "=&r" (tmp7)                                     \
    : [s] "r" (from), [d] "r" (to), [cnt] "r" (count)                          \
    : "memory");                                                               \
} while (false)

static void pd_prefetch_words(const HeapWord* from) {
  // Encode prefetch.r as ORI for assemblers without Zicbop support.
  // CPUs without Zicbop treat this HINT as a NOP.
  __asm__ volatile("ori zero, %[s], 1" :: [s] "r" (from) : "memory");
}

static void pd_conjoint_words(const HeapWord* from, HeapWord* to, size_t count) {
  pd_prefetch_words(from);
  if (__builtin_expect(count <= 8, 1)) {
    COPY_SMALL(from, to, count);
  } else {
    _Copy_conjoint_words(from, to, count);
  }
}

static void pd_disjoint_words(const HeapWord* from, HeapWord* to, size_t count) {
  if (__builtin_constant_p(count)) {
    memcpy(to, from, count * HeapWordSize);
    return;
  }
  pd_prefetch_words(from);
  if (__builtin_expect(count <= 8, 1)) {
    COPY_SMALL(from, to, count);
  } else {
    _Copy_disjoint_words(from, to, count);
  }
}

static void pd_disjoint_words_atomic(const HeapWord* from, HeapWord* to, size_t count) {
  pd_prefetch_words(from);
  if (__builtin_expect(count <= 8, 1)) {
    COPY_SMALL(from, to, count);
  } else {
    _Copy_disjoint_words(from, to, count);
  }
}

#undef COPY_SMALL

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
