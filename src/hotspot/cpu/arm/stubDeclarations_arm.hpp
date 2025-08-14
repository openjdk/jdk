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

#ifndef CPU_ARM_STUBDECLARATIONS_HPP
#define CPU_ARM_STUBDECLARATIONS_HPP

#define STUBGEN_PREUNIVERSE_BLOBS_ARCH_DO(do_stub,                      \
                                          do_arch_blob,                 \
                                          do_arch_entry,                \
                                          do_arch_entry_init)           \
  do_arch_blob(preuniverse, 500)                                        \
  do_stub(preuniverse, atomic_load_long)                                \
  do_arch_entry(Arm, preuniverse, atomic_load_long,                     \
                atomic_load_long_entry, atomic_load_long_entry)         \
  do_stub(preuniverse, atomic_store_long)                               \
  do_arch_entry(Arm, preuniverse, atomic_store_long,                    \
                atomic_store_long_entry, atomic_store_long_entry)       \


#define STUBGEN_INITIAL_BLOBS_ARCH_DO(do_stub,                          \
                                      do_arch_blob,                     \
                                      do_arch_entry,                    \
                                      do_arch_entry_init)               \
  do_arch_blob(initial, 9000)                                           \
  do_stub(initial, idiv_irem)                                           \
  do_arch_entry(Arm, initial, idiv_irem,                                \
                idiv_irem_entry, idiv_irem_entry)                       \

#define STUBGEN_CONTINUATION_BLOBS_ARCH_DO(do_stub,                     \
                                           do_arch_blob,                \
                                           do_arch_entry,               \
                                           do_arch_entry_init)          \
  do_arch_blob(continuation, 2000)                                      \


#define STUBGEN_COMPILER_BLOBS_ARCH_DO(do_stub,                         \
                                       do_arch_blob,                    \
                                       do_arch_entry,                   \
                                       do_arch_entry_init)              \
  do_arch_blob(compiler, 22000)                                         \
  do_stub(compiler, partial_subtype_check)                              \
  do_arch_entry(Arm, compiler, partial_subtype_check,                   \
                partial_subtype_check, partial_subtype_check)           \


#define STUBGEN_FINAL_BLOBS_ARCH_DO(do_stub,                            \
                                    do_arch_blob,                       \
                                    do_arch_entry,                      \
                                    do_arch_entry_init)                 \
  do_arch_blob(final, 22000)                                            \


#endif // CPU_ARM_STUBDECLARATIONS_HPP
