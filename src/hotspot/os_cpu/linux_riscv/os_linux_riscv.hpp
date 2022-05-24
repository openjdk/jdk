/*
 * Copyright (c) 1999, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_LINUX_RISCV_VM_OS_LINUX_RISCV_HPP
#define OS_CPU_LINUX_RISCV_VM_OS_LINUX_RISCV_HPP

  static void setup_fpu();

  // Used to register dynamic code cache area with the OS
  // Note: Currently only used in 64 bit Windows implementations
  static bool register_code_area(char *low, char *high) { return true; }

  // Atomically copy 64 bits of data
  static void atomic_copy64(const volatile void *src, volatile void *dst) {
    *(jlong *) dst = *(const jlong *) src;
  }

  // SYSCALL_RISCV_FLUSH_ICACHE is used to flush instruction cache. The "fence.i" instruction
  // only work on the current hart, so kernel provides the icache flush syscall to flush icache
  // on each hart. You can pass a flag to determine a global or local icache flush.
  static void icache_flush(long int start, long int end)
  {
    const int SYSCALL_RISCV_FLUSH_ICACHE = 259;
    register long int __a7 asm ("a7") = SYSCALL_RISCV_FLUSH_ICACHE;
    register long int __a0 asm ("a0") = start;
    register long int __a1 asm ("a1") = end;
    // the flush can be applied to either all threads or only the current.
    // 0 means a global icache flush, and the icache flush will be applied
    // to other harts concurrently executing.
    register long int __a2 asm ("a2") = 0;
    __asm__ volatile ("ecall\n\t"
                      : "+r" (__a0)
                      : "r" (__a0), "r" (__a1), "r" (__a2), "r" (__a7)
                      : "memory");
  }

#endif // OS_CPU_LINUX_RISCV_VM_OS_LINUX_RISCV_HPP
