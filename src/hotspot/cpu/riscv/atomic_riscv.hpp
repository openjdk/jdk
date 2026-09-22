/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_RISCV_ATOMIC_RISCV_HPP
#define CPU_RISCV_ATOMIC_RISCV_HPP

// Atomic stub implementation.
// Default implementations are in atomic_linux_riscv.S.
//
// All stubs use the C calling convention:
// a0: destination address and result
// a1: compare value
// a2: exchange value
typedef uint64_t (*riscv_atomic_stub_t)(volatile void* ptr, uint64_t compare_value,
                                        uint64_t exchange_value);

extern riscv_atomic_stub_t riscv_atomic_cmpxchg_1_impl;
extern riscv_atomic_stub_t riscv_atomic_cmpxchg_4_impl;
extern riscv_atomic_stub_t riscv_atomic_cmpxchg_8_impl;
extern riscv_atomic_stub_t riscv_atomic_cmpxchg_1_relaxed_impl;
extern riscv_atomic_stub_t riscv_atomic_cmpxchg_4_relaxed_impl;
extern riscv_atomic_stub_t riscv_atomic_cmpxchg_8_relaxed_impl;
extern riscv_atomic_stub_t riscv_atomic_cmpxchg_4_release_impl;
extern riscv_atomic_stub_t riscv_atomic_cmpxchg_8_release_impl;
extern riscv_atomic_stub_t riscv_atomic_cmpxchg_4_seq_cst_impl;
extern riscv_atomic_stub_t riscv_atomic_cmpxchg_8_seq_cst_impl;

#endif // CPU_RISCV_ATOMIC_RISCV_HPP
