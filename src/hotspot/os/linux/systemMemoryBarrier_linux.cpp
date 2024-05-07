/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "logging/log.hpp"
#include "os_linux.hpp"
#include "utilities/debug.hpp"
#include "utilities/systemMemoryBarrier.hpp"

#include <sys/syscall.h>

// Syscall defined in kernel 4.3
// Oracle x64 builds may use old sysroot (pre 4.3)
#ifndef SYS_membarrier
  #if defined(AMD64)
  #define SYS_membarrier 324
  #elif defined(X86)
  #define SYS_membarrier 375
  #elif defined(PPC64)
  #define SYS_membarrier 365
  #elif defined(AARCH64)
  #define SYS_membarrier 283
  #elif defined(ALPHA)
  #define SYS_membarrier 517
  #else
  #error define SYS_membarrier for the arch
  #endif
#endif // SYS_membarrier

// Expedited defined in kernel 4.14
// Therefore we define it here instead of including linux/membarrier.h
enum membarrier_cmd {
  MEMBARRIER_CMD_QUERY                      = 0,
  MEMBARRIER_CMD_PRIVATE_EXPEDITED          = (1 << 3),
  MEMBARRIER_CMD_REGISTER_PRIVATE_EXPEDITED = (1 << 4),
};

static long membarrier(int cmd, unsigned int flags, int cpu_id) {
  return syscall(SYS_membarrier, cmd, flags, cpu_id); // cpu_id only on >= 5.10
}

bool LinuxSystemMemoryBarrier::initialize() {
#if defined(RISCV)
// RISCV port was introduced in kernel 4.4.
// 4.4 also made membar private expedited mandatory.
// But RISCV actually don't support it until 6.9.
  long major, minor;
  os::Linux::kernel_version(&major, &minor);
  if (!(major > 6 || (major == 6 && minor >= 9))) {
    log_info(os)("Linux kernel %ld.%ld does not support MEMBARRIER PRIVATE_EXPEDITED on RISC-V.",
                 major, minor);
    return false;
  }
#endif
  long ret = membarrier(MEMBARRIER_CMD_QUERY, 0, 0);
  if (ret < 0) {
    log_info(os)("MEMBARRIER_CMD_QUERY unsupported");
    return false;
  }
  if (!(ret & MEMBARRIER_CMD_PRIVATE_EXPEDITED) ||
      !(ret & MEMBARRIER_CMD_REGISTER_PRIVATE_EXPEDITED)) {
    log_info(os)("MEMBARRIER PRIVATE_EXPEDITED unsupported");
    return false;
  }
  ret = membarrier(MEMBARRIER_CMD_REGISTER_PRIVATE_EXPEDITED, 0, 0);
  guarantee_with_errno(ret == 0, "MEMBARRIER_CMD_REGISTER_PRIVATE_EXPEDITED failed");
  log_info(os)("Using MEMBARRIER PRIVATE_EXPEDITED");
  return true;
}

void LinuxSystemMemoryBarrier::emit() {
  long s = membarrier(MEMBARRIER_CMD_PRIVATE_EXPEDITED, 0, 0);
  guarantee_with_errno(s >= 0, "MEMBARRIER_CMD_PRIVATE_EXPEDITED failed");
}
