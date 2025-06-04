/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef OS_CPU_LINUX_RISCV_ORDERACCESS_LINUX_RISCV_HPP
#define OS_CPU_LINUX_RISCV_ORDERACCESS_LINUX_RISCV_HPP

// Included in orderAccess.hpp header file.

#include "runtime/vm_version.hpp"

// Implementation of class OrderAccess.

inline void OrderAccess::loadload()   { acquire(); }
inline void OrderAccess::storestore() { release(); }
inline void OrderAccess::loadstore()  { acquire(); }
inline void OrderAccess::storeload()  { fence(); }

#define FULL_MEM_BARRIER  __atomic_thread_fence(__ATOMIC_SEQ_CST);
#define READ_MEM_BARRIER  __atomic_thread_fence(__ATOMIC_ACQUIRE);
#define WRITE_MEM_BARRIER __atomic_thread_fence(__ATOMIC_RELEASE);

inline void OrderAccess::acquire() {
  READ_MEM_BARRIER;
}

inline void OrderAccess::release() {
  WRITE_MEM_BARRIER;
}

inline void OrderAccess::fence() {
  FULL_MEM_BARRIER;
}

inline void OrderAccess::cross_modify_fence_impl() {
  // From 3 "Zifencei" Instruction-Fetch Fence, Version 2.0
  // "RISC-V does not guarantee that stores to instruction memory will be made
  // visible to instruction fetches on a RISC-V hart until that hart executes a
  // FENCE.I instruction. A FENCE.I instruction ensures that a subsequent
  // instruction fetch on a RISC-V hart will see any previous data stores
  // already visible to the same RISC-V hart. FENCE.I does not ensure that other
  // RISC-V harts' instruction fetches will observe the local hart's stores in a
  // multiprocessor system."
  //
  // Hence to be able to use fence.i directly we need a kernel that supports
  // PR_RISCV_CTX_SW_FENCEI_ON. Thus if context switch to another hart we are
  // ensured that instruction fetch will see any previous data stores
  //
  // The alternative is using full system IPI (system wide icache sync) then
  // this barrier is not strictly needed. As this is emitted in runtime slow-path
  // we will just always emit it, typically after a safepoint.
  guarantee(VM_Version::supports_fencei_barrier(), "Linux kernel require fence.i");
  __asm__ volatile("fence.i" : : : "memory");
}

#endif // OS_CPU_LINUX_RISCV_ORDERACCESS_LINUX_RISCV_HPP
