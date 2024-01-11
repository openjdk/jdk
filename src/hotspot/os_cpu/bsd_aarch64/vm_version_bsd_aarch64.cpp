/*
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2019, Red Hat Inc. All rights reserved.
 * Copyright (c) 2021, Azul Systems, Inc. All rights reserved.
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
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "runtime/vm_version.hpp"

#include <sys/sysctl.h>

int VM_Version::get_current_sve_vector_length() {
  ShouldNotCallThis();
  return -1;
}

int VM_Version::set_and_get_current_sve_vector_length(int length) {
  ShouldNotCallThis();
  return -1;
}

static bool cpu_has(const char* optional) {
  uint32_t val;
  size_t len = sizeof(val);
  if (sysctlbyname(optional, &val, &len, nullptr, 0)) {
    return false;
  }
  return val;
}

void VM_Version::get_os_cpu_info() {
  size_t sysctllen;

  // cpu_has() uses sysctlbyname function to check the existence of CPU
  // features. References: Apple developer document [1] and XNU kernel [2].
  // [1] https://developer.apple.com/documentation/kernel/1387446-sysctlbyname/determining_instruction_set_characteristics
  // [2] https://github.com/apple-oss-distributions/xnu/blob/main/bsd/kern/kern_mib.c
  //
  // Note that for some features (e.g., LSE, SHA512 and SHA3) there are two
  // parameters for sysctlbyname, which are invented at different times.
  // Considering backward compatibility, we check both here.
  //
  // Floating-point and Advance SIMD features are standard in Apple processors
  // beginning with M1 and A7, and don't need to be checked [1].
  // 1) hw.optional.floatingpoint always returns 1 [2].
  // 2) ID_AA64PFR0_EL1 describes AdvSIMD always equals to FP field.
  //    See the Arm ARM, section "ID_AA64PFR0_EL1, AArch64 Processor Feature
  //    Register 0".
  _features = CPU_FP | CPU_ASIMD;

  // All Apple-darwin Arm processors have AES, PMULL, SHA1 and SHA2.
  // See https://github.com/apple-oss-distributions/xnu/blob/main/osfmk/arm/commpage/commpage.c#L412
  // Note that we ought to add assertions to check sysctlbyname parameters for
  // these four CPU features, e.g., "hw.optional.arm.FEAT_AES", but the
  // corresponding string names are not available before xnu-8019 version.
  // Hence, assertions are omitted considering backward compatibility.
  _features |= CPU_AES | CPU_PMULL | CPU_SHA1 | CPU_SHA2;

  if (cpu_has("hw.optional.armv8_crc32")) {
    _features |= CPU_CRC32;
  }
  if (cpu_has("hw.optional.arm.FEAT_LSE") ||
      cpu_has("hw.optional.armv8_1_atomics")) {
    _features |= CPU_LSE;
  }
  if (cpu_has("hw.optional.arm.FEAT_SHA512") ||
      cpu_has("hw.optional.armv8_2_sha512")) {
    _features |= CPU_SHA512;
  }
  if (cpu_has("hw.optional.arm.FEAT_SHA3") ||
      cpu_has("hw.optional.armv8_2_sha3")) {
    _features |= CPU_SHA3;
  }

  int cache_line_size;
  int hw_conf_cache_line[] = { CTL_HW, HW_CACHELINE };
  sysctllen = sizeof(cache_line_size);
  if (sysctl(hw_conf_cache_line, 2, &cache_line_size, &sysctllen, nullptr, 0)) {
    cache_line_size = 16;
  }
  _icache_line_size = 16; // minimal line length CCSIDR_EL1 can hold
  _dcache_line_size = cache_line_size;

  uint64_t dczid_el0;
  __asm__ (
    "mrs %0, DCZID_EL0\n"
    : "=r"(dczid_el0)
  );
  if (!(dczid_el0 & 0x10)) {
    _zva_length = 4 << (dczid_el0 & 0xf);
  }

  int family;
  sysctllen = sizeof(family);
  if (sysctlbyname("hw.cpufamily", &family, &sysctllen, nullptr, 0)) {
    family = 0;
  }

  _model = family;
  _cpu = CPU_APPLE;
}

void VM_Version::get_compatible_board(char *buf, int buflen) {
  assert(buf != nullptr, "invalid argument");
  assert(buflen >= 1, "invalid argument");
  *buf = '\0';
}

#ifdef __APPLE__

bool VM_Version::is_cpu_emulated() {
  return false;
}

#endif
