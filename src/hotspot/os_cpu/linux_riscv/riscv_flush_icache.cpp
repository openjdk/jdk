/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Rivos Inc. All rights reserved.
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
#include "riscv_flush_icache.hpp"
#include "runtime/os.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/debug.hpp"

#include <sys/syscall.h>
#include <unistd.h>

#define check_with_errno(check_type, cond, msg)                             \
  do {                                                                      \
    int err = errno;                                                        \
    check_type(cond, "%s; error='%s' (errno=%s)", msg, os::strerror(err),   \
               os::errno_name(err));                                        \
} while (false)

#define assert_with_errno(cond, msg)    check_with_errno(assert, cond, msg)
#define guarantee_with_errno(cond, msg) check_with_errno(guarantee, cond, msg)

#ifndef NR_riscv_flush_icache
#ifndef NR_arch_specific_syscall
#define NR_arch_specific_syscall 244
#endif
#define NR_riscv_flush_icache (NR_arch_specific_syscall + 15)
#endif

#define SYS_RISCV_FLUSH_ICACHE_LOCAL 1UL
#define SYS_RISCV_FLUSH_ICACHE_ALL   0UL

static long sys_flush_icache(uintptr_t start, uintptr_t end , uintptr_t flags) {
  return syscall(NR_riscv_flush_icache, start, end, flags);
}

bool RiscvFlushIcache::test() {
  alignas(64) char memory[64];
  long ret = sys_flush_icache((uintptr_t)&memory[0],
                              (uintptr_t)&memory[sizeof(memory) - 1],
                              SYS_RISCV_FLUSH_ICACHE_ALL);
  if (ret == 0) {
    return true;
  }
  int err = errno;                                                        \
  log_error(os)("Syscall: RISCV_FLUSH_ICACHE not available; error='%s' (errno=%s)",
                os::strerror(err), os::errno_name(err));
  return false;
}

void RiscvFlushIcache::flush(uintptr_t start, uintptr_t end) {
  long ret = sys_flush_icache(start, end, SYS_RISCV_FLUSH_ICACHE_ALL);
  guarantee_with_errno(ret == 0, "riscv_flush_icache failed");
}
