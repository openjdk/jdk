/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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

#ifndef CPU_AARCH64_STUBDECLARATIONS_HPP
#define CPU_AARCH64_STUBDECLARATIONS_HPP

#define STUBGEN_PREUNIVERSE_BLOBS_ARCH_DO(do_stub,                      \
                                          do_arch_blob,                 \
                                          do_arch_entry,                \
                                          do_arch_entry_init)           \
  do_arch_blob(preuniverse, 0)                                          \


#define STUBGEN_INITIAL_BLOBS_ARCH_DO(do_stub,                          \
                                      do_arch_blob,                     \
                                      do_arch_entry,                    \
                                      do_arch_entry_init)               \
  do_arch_blob(initial, 10000)                                          \


#define STUBGEN_CONTINUATION_BLOBS_ARCH_DO(do_stub,                     \
                                           do_arch_blob,                \
                                           do_arch_entry,               \
                                           do_arch_entry_init)          \
  do_arch_blob(continuation, 2000)                                      \


#define STUBGEN_COMPILER_BLOBS_ARCH_DO(do_stub,                         \
                                       do_arch_blob,                    \
                                       do_arch_entry,                   \
                                       do_arch_entry_init)              \
  do_arch_blob(compiler, 70000)                                         \
  do_stub(compiler, vector_iota_indices)                                \
  do_arch_entry(aarch64, compiler, vector_iota_indices,                 \
                vector_iota_indices, vector_iota_indices)               \
  do_stub(compiler, large_array_equals)                                 \
  do_arch_entry(aarch64, compiler, large_array_equals,                  \
                large_array_equals, large_array_equals)                 \
  do_stub(compiler, large_arrays_hashcode_boolean)                      \
  do_arch_entry(aarch64, compiler, large_arrays_hashcode_boolean,       \
                large_arrays_hashcode_boolean,                          \
                large_arrays_hashcode_boolean)                          \
  do_stub(compiler, large_arrays_hashcode_byte)                         \
  do_arch_entry(aarch64, compiler, large_arrays_hashcode_byte,          \
                large_arrays_hashcode_byte,                             \
                large_arrays_hashcode_byte)                             \
  do_stub(compiler, large_arrays_hashcode_char)                         \
  do_arch_entry(aarch64, compiler, large_arrays_hashcode_char,          \
                large_arrays_hashcode_char,                             \
                large_arrays_hashcode_char)                             \
  do_stub(compiler, large_arrays_hashcode_short)                        \
  do_arch_entry(aarch64, compiler, large_arrays_hashcode_short,         \
                large_arrays_hashcode_short,                            \
                large_arrays_hashcode_short)                            \
  do_stub(compiler, large_arrays_hashcode_int)                          \
  do_arch_entry(aarch64, compiler, large_arrays_hashcode_int,           \
                large_arrays_hashcode_int,                              \
                large_arrays_hashcode_int)                              \
  do_stub(compiler, large_byte_array_inflate)                           \
  do_arch_entry(aarch64, compiler, large_byte_array_inflate,            \
                large_byte_array_inflate, large_byte_array_inflate)     \
  do_stub(compiler, count_positives)                                    \
  do_arch_entry(aarch64, compiler, count_positives, count_positives,    \
                count_positives)                                        \
  do_stub(compiler, count_positives_long)                               \
  do_arch_entry(aarch64, compiler, count_positives_long,                \
                count_positives_long, count_positives_long)             \
  do_stub(compiler, compare_long_string_LL)                             \
  do_arch_entry(aarch64, compiler, compare_long_string_LL,              \
                compare_long_string_LL, compare_long_string_LL)         \
  do_stub(compiler, compare_long_string_UU)                             \
  do_arch_entry(aarch64, compiler, compare_long_string_UU,              \
                compare_long_string_UU, compare_long_string_UU)         \
  do_stub(compiler, compare_long_string_LU)                             \
  do_arch_entry(aarch64, compiler, compare_long_string_LU,              \
                compare_long_string_LU, compare_long_string_LU)         \
  do_stub(compiler, compare_long_string_UL)                             \
  do_arch_entry(aarch64, compiler, compare_long_string_UL,              \
                compare_long_string_UL, compare_long_string_UL)         \
  do_stub(compiler, string_indexof_linear_ll)                           \
  do_arch_entry(aarch64, compiler, string_indexof_linear_ll,            \
                string_indexof_linear_ll, string_indexof_linear_ll)     \
  do_stub(compiler, string_indexof_linear_uu)                           \
  do_arch_entry(aarch64, compiler, string_indexof_linear_uu,            \
                string_indexof_linear_uu, string_indexof_linear_uu)     \
  do_stub(compiler, string_indexof_linear_ul)                           \
  do_arch_entry(aarch64, compiler, string_indexof_linear_ul,            \
                string_indexof_linear_ul, string_indexof_linear_ul)     \
  /* this uses the entry for ghash_processBlocks */                     \
  do_stub(compiler, ghash_processBlocks_wide)                           \


#define STUBGEN_FINAL_BLOBS_ARCH_DO(do_stub,                            \
                                    do_arch_blob,                       \
                                    do_arch_entry,                      \
                                    do_arch_entry_init)                 \
  do_arch_blob(final, 20000 ZGC_ONLY(+85000))                           \
  do_stub(final, copy_byte_f)                                           \
  do_arch_entry(aarch64, final, copy_byte_f, copy_byte_f,               \
                copy_byte_f)                                            \
  do_stub(final, copy_byte_b)                                           \
  do_arch_entry(aarch64, final, copy_byte_b, copy_byte_b,               \
                  copy_byte_b)                                          \
  do_stub(final, copy_oop_f)                                            \
  do_arch_entry(aarch64, final, copy_oop_f, copy_oop_f, copy_oop_f)     \
  do_stub(final, copy_oop_b)                                            \
  do_arch_entry(aarch64, final, copy_oop_b, copy_oop_b, copy_oop_b)     \
  do_stub(final, copy_oop_uninit_f)                                     \
  do_arch_entry(aarch64, final, copy_oop_uninit_f, copy_oop_uninit_f,   \
                copy_oop_uninit_f)                                      \
  do_stub(final, copy_oop_uninit_b)                                     \
  do_arch_entry(aarch64, final, copy_oop_uninit_b, copy_oop_uninit_b,   \
                copy_oop_uninit_b)                                      \
  do_stub(final, zero_blocks)                                           \
  do_arch_entry(aarch64, final, zero_blocks, zero_blocks,               \
                zero_blocks)                                            \
  do_stub(final, spin_wait)                                             \
  do_arch_entry_init(aarch64, final, spin_wait, spin_wait,              \
                     spin_wait, empty_spin_wait)                        \
  /* stub only -- entries are not stored in StubRoutines::aarch64 */    \
  /* n.b. these are not the same as the generic atomic stubs */         \
  do_stub(final, atomic_entry_points)                                   \


#endif // CPU_AARCH64_STUBDECLARATIONS_HPP
