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
#include "riscv_hwprobe.hpp"
#include "runtime/os.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/debug.hpp"

#include <sched.h>
#include <sys/syscall.h>
#include <unistd.h>

// Syscall defined in kernel 6.4 and the defines will be in asm/hwprobe.h
#define RISCV_HWPROBE_KEY_MVENDORID     0
#define RISCV_HWPROBE_KEY_MARCHID       1
#define RISCV_HWPROBE_KEY_MIMPID        2

#define RISCV_HWPROBE_KEY_BASE_BEHAVIOR 3
#define   RISCV_HWPROBE_BASE_BEHAVIOR_IMA       (1 << 0)

#define RISCV_HWPROBE_KEY_IMA_EXT_0     4
#define   RISCV_HWPROBE_IMA_FD                  (1 << 0)
#define   RISCV_HWPROBE_IMA_C                   (1 << 1)
#define   RISCV_HWPROBE_IMA_V                   (1 << 2)
#define   RISCV_HWPROBE_EXT_ZBA                 (1 << 3)
#define   RISCV_HWPROBE_EXT_ZBB                 (1 << 4)
#define   RISCV_HWPROBE_EXT_ZBS                 (1 << 5)
#define   RISCV_HWPROBE_EXT_ZICBOZ              (1 << 6)
#define   RISCV_HWPROBE_EXT_ZBC                 (1 << 7)
#define   RISCV_HWPROBE_EXT_ZBKB                (1 << 8)
#define   RISCV_HWPROBE_EXT_ZBKC                (1 << 9)
#define   RISCV_HWPROBE_EXT_ZBKX                (1 << 10)
#define   RISCV_HWPROBE_EXT_ZKND                (1 << 11)
#define   RISCV_HWPROBE_EXT_ZKNE                (1 << 12)
#define   RISCV_HWPROBE_EXT_ZKNH                (1 << 13)
#define   RISCV_HWPROBE_EXT_ZKSED               (1 << 14)
#define   RISCV_HWPROBE_EXT_ZKSH                (1 << 15)
#define   RISCV_HWPROBE_EXT_ZKT                 (1 << 16)
#define   RISCV_HWPROBE_EXT_ZVBB                (1 << 17)
#define   RISCV_HWPROBE_EXT_ZVBC                (1 << 18)
#define   RISCV_HWPROBE_EXT_ZVKB                (1 << 19)
#define   RISCV_HWPROBE_EXT_ZVKG                (1 << 20)
#define   RISCV_HWPROBE_EXT_ZVKNED              (1 << 21)
#define   RISCV_HWPROBE_EXT_ZVKNHA              (1 << 22)
#define   RISCV_HWPROBE_EXT_ZVKNHB              (1 << 23)
#define   RISCV_HWPROBE_EXT_ZVKSED              (1 << 24)
#define   RISCV_HWPROBE_EXT_ZVKSH               (1 << 25)
#define   RISCV_HWPROBE_EXT_ZVKT                (1 << 26)
#define   RISCV_HWPROBE_EXT_ZFH                 (1 << 27)
#define   RISCV_HWPROBE_EXT_ZFHMIN              (1 << 28)
#define   RISCV_HWPROBE_EXT_ZIHINTNTL           (1 << 29)
#define   RISCV_HWPROBE_EXT_ZVFH                (1 << 30)
#define   RISCV_HWPROBE_EXT_ZVFHMIN             (1 << 31)
#define   RISCV_HWPROBE_EXT_ZFA                 (1ULL << 32)
#define   RISCV_HWPROBE_EXT_ZTSO                (1ULL << 33)
#define   RISCV_HWPROBE_EXT_ZACAS               (1ULL << 34)
#define   RISCV_HWPROBE_EXT_ZICOND              (1ULL << 35)

#define RISCV_HWPROBE_KEY_CPUPERF_0     5
#define   RISCV_HWPROBE_MISALIGNED_UNKNOWN      (0 << 0)
#define   RISCV_HWPROBE_MISALIGNED_EMULATED     (1 << 0)
#define   RISCV_HWPROBE_MISALIGNED_SLOW         (2 << 0)
#define   RISCV_HWPROBE_MISALIGNED_FAST         (3 << 0)
#define   RISCV_HWPROBE_MISALIGNED_UNSUPPORTED  (4 << 0)
#define   RISCV_HWPROBE_MISALIGNED_MASK         (7 << 0)

#ifndef NR_riscv_hwprobe
#ifndef NR_arch_specific_syscall
#define NR_arch_specific_syscall 244
#endif
#define NR_riscv_hwprobe (NR_arch_specific_syscall + 14)
#endif

struct riscv_hwprobe {
    int64_t key;
    uint64_t value;
};

long sys_riscv_hwprobe(struct riscv_hwprobe *pairs, size_t pairc,
                       size_t cpuc, cpu_set_t *cpus,
                       unsigned int flags) {
  return syscall(NR_riscv_hwprobe, pairs, pairc, cpuc, cpus, 0 /* flags*/);
}

static bool rw_hwprobe_completed = false;

static struct riscv_hwprobe query[] = {{RISCV_HWPROBE_KEY_MVENDORID, 0},
                                       {RISCV_HWPROBE_KEY_MARCHID,   0},
                                       {RISCV_HWPROBE_KEY_MIMPID,    0},
                                       {RISCV_HWPROBE_KEY_BASE_BEHAVIOR, 0},
                                       {RISCV_HWPROBE_KEY_IMA_EXT_0,     0},
                                       {RISCV_HWPROBE_KEY_CPUPERF_0,     0}};

bool RiscvHwprobe::probe_features() {
  assert(!rw_hwprobe_completed, "Called twice.");
  int ret = sys_riscv_hwprobe(&query[0], sizeof(query) / sizeof(query[0]), 0, nullptr, 0);
  rw_hwprobe_completed = true;
  if (ret != 0) {
    log_debug(os, cpu)("riscv_hwprobe unsupported");
    return false;
  }
  log_debug(os, cpu)("riscv_hwprobe supported");
  add_features_from_query_result();
  return true;
}

static bool is_valid(int64_t key) {
  return query[key].key != -1;
}

static bool is_set(int64_t key, uint64_t value_mask) {
  if (is_valid(key)) {
    return (query[key].value & value_mask) != 0;
  }
  return false;
}

void RiscvHwprobe::add_features_from_query_result() {
  assert(rw_hwprobe_completed, "hwprobe not init yet.");

  if (is_valid(RISCV_HWPROBE_KEY_MVENDORID)) {
    VM_Version::mvendorid.enable_feature(query[RISCV_HWPROBE_KEY_MVENDORID].value);
  }
  if (is_valid(RISCV_HWPROBE_KEY_MARCHID)) {
    VM_Version::marchid.enable_feature(query[RISCV_HWPROBE_KEY_MARCHID].value);
  }
  if (is_valid(RISCV_HWPROBE_KEY_MIMPID)) {
    VM_Version::mimpid.enable_feature(query[RISCV_HWPROBE_KEY_MIMPID].value);
  }
  if (is_set(RISCV_HWPROBE_KEY_BASE_BEHAVIOR, RISCV_HWPROBE_BASE_BEHAVIOR_IMA)) {
    VM_Version::ext_I.enable_feature();
    VM_Version::ext_M.enable_feature();
    VM_Version::ext_A.enable_feature();
  }
  if (is_set(RISCV_HWPROBE_KEY_IMA_EXT_0, RISCV_HWPROBE_IMA_FD)) {
    VM_Version::ext_F.enable_feature();
    VM_Version::ext_D.enable_feature();
  }
  if (is_set(RISCV_HWPROBE_KEY_IMA_EXT_0, RISCV_HWPROBE_IMA_C)) {
    VM_Version::ext_C.enable_feature();
  }
  if (is_set(RISCV_HWPROBE_KEY_IMA_EXT_0, RISCV_HWPROBE_IMA_V)) {
    VM_Version::ext_V.enable_feature();
  }
  if (is_set(RISCV_HWPROBE_KEY_IMA_EXT_0, RISCV_HWPROBE_EXT_ZBA)) {
    VM_Version::ext_Zba.enable_feature();
  }
  if (is_set(RISCV_HWPROBE_KEY_IMA_EXT_0, RISCV_HWPROBE_EXT_ZBB)) {
    VM_Version::ext_Zbb.enable_feature();
  }
  if (is_set(RISCV_HWPROBE_KEY_IMA_EXT_0, RISCV_HWPROBE_EXT_ZBS)) {
    VM_Version::ext_Zbs.enable_feature();
  }
  if (is_set(RISCV_HWPROBE_KEY_IMA_EXT_0, RISCV_HWPROBE_EXT_ZFH)) {
    VM_Version::ext_Zfh.enable_feature();
  }
  if (is_valid(RISCV_HWPROBE_KEY_CPUPERF_0)) {
    VM_Version::unaligned_access.enable_feature(
       query[RISCV_HWPROBE_KEY_CPUPERF_0].value & RISCV_HWPROBE_MISALIGNED_MASK);
  }
}
