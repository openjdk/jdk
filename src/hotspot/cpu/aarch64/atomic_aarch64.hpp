/* Copyright (c) 2021, Red Hat Inc. All rights reserved.
 * Copyright (c) 2026, IBM Inc. All rights reserved.
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

#ifndef CPU_AARCH64_ATOMIC_AARCH64_HPP
#define CPU_AARCH64_ATOMIC_AARCH64_HPP

#ifdef __cplusplus

#define DEFAULT_ATOMIC_OP(OPNAME, SIZE, MEMORDER)                        \
  extern "C" aarch64_atomic_stub_t aarch64_atomic_ ## OPNAME ## _ ## SIZE ## MEMORDER ## _impl;

typedef uint64_t (*aarch64_atomic_stub_t)(volatile void *ptr, uint64_t arg1, uint64_t arg2);

#define ATOMIC_OPS_START                        \
  extern address aarch64_atomic_ops[], lse_aarch64_atomic_ops[];
#define ATOMIC_OPS_END                          \
  extern address aarch64_atomic_ops_end[], lse_aarch64_atomic_ops_end[];

#elif defined(__ASSEMBLER__)

#else

#error One of these must be defined

#endif

// Atomic stub implementation.
// Default implementations are in atomic_linux_aarch64.S
//
// All stubs pass arguments the same way
// x0: src/dest address
// x1: arg1
// x2: arg2 (optional)
// x3, x8, x9: scratch

// DEFAULT_ATOMIC_OP(name, bytesize, memory_order)
// memory_order is blank (i.e. conservative), _acquire, _release, _acq_rel, _seq_cst

// Pointers to stubs

#define DO_ATOMIC_STUBS                         \
DEFAULT_ATOMIC_OP(fetch_add, 4, )               \
DEFAULT_ATOMIC_OP(fetch_add, 8, )               \
DEFAULT_ATOMIC_OP(fetch_add, 4, _relaxed)       \
DEFAULT_ATOMIC_OP(fetch_add, 8, _relaxed)       \
DEFAULT_ATOMIC_OP(xchg, 1, )                    \
DEFAULT_ATOMIC_OP(xchg, 4, )                    \
DEFAULT_ATOMIC_OP(xchg, 8, )                    \
DEFAULT_ATOMIC_OP(cmpxchg, 1, )                 \
DEFAULT_ATOMIC_OP(cmpxchg, 4, )                 \
DEFAULT_ATOMIC_OP(cmpxchg, 8, )                 \
DEFAULT_ATOMIC_OP(cmpxchg, 1, _relaxed)         \
DEFAULT_ATOMIC_OP(cmpxchg, 4, _relaxed)         \
DEFAULT_ATOMIC_OP(cmpxchg, 8, _relaxed)         \
DEFAULT_ATOMIC_OP(cmpxchg, 4, _release)         \
DEFAULT_ATOMIC_OP(cmpxchg, 8, _release)         \
DEFAULT_ATOMIC_OP(cmpxchg, 4, _seq_cst)         \
DEFAULT_ATOMIC_OP(cmpxchg, 8, _seq_cst)

ATOMIC_OPS_START
DO_ATOMIC_STUBS
ATOMIC_OPS_END

#endif // CPU_AARCH64_ATOMIC_AARCH64_HPP
