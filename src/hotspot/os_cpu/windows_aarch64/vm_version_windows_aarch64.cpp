/*
 * Copyright (c) 2020, 2021, Microsoft Corporation. All rights reserved.
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

#include "logging/log.hpp"
#include "runtime/os.hpp"
#include "runtime/vm_version.hpp"

// Since PF_ARM_SVE_INSTRUCTIONS_AVAILABLE and related constants were added in
// Windows 11 (version 24H2) and in Windows Server 2025, we define them here for
// compatibility with older SDK versions.
#ifndef PF_ARM_SVE_INSTRUCTIONS_AVAILABLE
#define PF_ARM_SVE_INSTRUCTIONS_AVAILABLE 46
#endif

#ifndef PF_ARM_SVE2_INSTRUCTIONS_AVAILABLE
#define PF_ARM_SVE2_INSTRUCTIONS_AVAILABLE 47
#endif

#ifndef PF_ARM_SVE_BITPERM_INSTRUCTIONS_AVAILABLE
#define PF_ARM_SVE_BITPERM_INSTRUCTIONS_AVAILABLE 51
#endif

#ifndef PF_ARM_SHA3_INSTRUCTIONS_AVAILABLE
#define PF_ARM_SHA3_INSTRUCTIONS_AVAILABLE 64
#endif

#ifndef PF_ARM_SHA512_INSTRUCTIONS_AVAILABLE
#define PF_ARM_SHA512_INSTRUCTIONS_AVAILABLE 65
#endif

#ifndef PF_ARM_V82_FP16_INSTRUCTIONS_AVAILABLE
#define PF_ARM_V82_FP16_INSTRUCTIONS_AVAILABLE 67
#endif

// Assembly function to get SVE vector length using RDVL instruction
extern "C" int get_sve_vector_length();

int VM_Version::get_current_sve_vector_length() {
  assert(VM_Version::supports_sve(), "should not call this");
  return VM_Version::supports_sve() ? get_sve_vector_length() : 0;
}

int VM_Version::set_and_get_current_sve_vector_length(int length) {
  assert(VM_Version::supports_sve(), "should not call this");

  // Unlike Linux, Windows does not present a way to modify the VL (the
  // rationale is that the OS expects the application to use the maximum vector
  // length supported by the hardware), so we simply return the current VL.  If
  // the user sets `MaxVectorSize` that is not the same as the maximum possible
  // vector length, then the caller (`VM_Version::initialize()`) will print a
  // warning, set `MaxVectorSize` to the value returned by this function, and
  // move on.
  return VM_Version::supports_sve() ? get_sve_vector_length() : 0;
}

void VM_Version::get_os_cpu_info() {

  if (IsProcessorFeaturePresent(PF_ARM_V8_CRC32_INSTRUCTIONS_AVAILABLE)) {
    set_feature(CPU_CRC32);
  }
  if (IsProcessorFeaturePresent(PF_ARM_V8_CRYPTO_INSTRUCTIONS_AVAILABLE)) {
    set_feature(CPU_AES);
    set_feature(CPU_SHA1);
    set_feature(CPU_SHA2);
    set_feature(CPU_PMULL);
  }
  if (IsProcessorFeaturePresent(PF_ARM_VFP_32_REGISTERS_AVAILABLE)) {
    set_feature(CPU_ASIMD);
  }
  if (IsProcessorFeaturePresent(PF_ARM_V81_ATOMIC_INSTRUCTIONS_AVAILABLE)) {
    set_feature(CPU_LSE);
  }
  if (IsProcessorFeaturePresent(PF_ARM_SVE_INSTRUCTIONS_AVAILABLE)) {
    set_feature(CPU_SVE);
  }
  if (IsProcessorFeaturePresent(PF_ARM_SVE2_INSTRUCTIONS_AVAILABLE)) {
    set_feature(CPU_SVE2);
  }
  if (IsProcessorFeaturePresent(PF_ARM_SVE_BITPERM_INSTRUCTIONS_AVAILABLE)) {
    set_feature(CPU_SVEBITPERM);
  }
  if (IsProcessorFeaturePresent(PF_ARM_V82_FP16_INSTRUCTIONS_AVAILABLE)) {
    set_feature(CPU_FPHP);
    set_feature(CPU_ASIMDHP);
  }
  if (IsProcessorFeaturePresent(PF_ARM_SHA3_INSTRUCTIONS_AVAILABLE)) {
    set_feature(CPU_SHA3);
  }
  if (IsProcessorFeaturePresent(PF_ARM_SHA512_INSTRUCTIONS_AVAILABLE)) {
    set_feature(CPU_SHA512);
  }

  __int64 dczid_el0 = _ReadStatusReg(0x5807 /* ARM64_DCZID_EL0 */);

  if (!(dczid_el0 & 0x10)) {
    _zva_length = 4 << (dczid_el0 & 0xf);
  }

  {
    PSYSTEM_LOGICAL_PROCESSOR_INFORMATION buffer = nullptr;
    DWORD returnLength = 0;

    // See https://docs.microsoft.com/en-us/windows/win32/api/sysinfoapi/nf-sysinfoapi-getlogicalprocessorinformation
    GetLogicalProcessorInformation(nullptr, &returnLength);
    assert(GetLastError() == ERROR_INSUFFICIENT_BUFFER, "Unexpected return from GetLogicalProcessorInformation");

    buffer = (PSYSTEM_LOGICAL_PROCESSOR_INFORMATION)os::malloc(returnLength, mtInternal);
    BOOL rc = GetLogicalProcessorInformation(buffer, &returnLength);
    assert(rc, "Unexpected return from GetLogicalProcessorInformation");

    _icache_line_size = _dcache_line_size = -1;
    for (PSYSTEM_LOGICAL_PROCESSOR_INFORMATION ptr = buffer; ptr < buffer + returnLength / sizeof(SYSTEM_LOGICAL_PROCESSOR_INFORMATION); ptr++) {
      switch (ptr->Relationship) {
      case RelationCache:
        // Cache data is in ptr->Cache, one CACHE_DESCRIPTOR structure for each cache.
        PCACHE_DESCRIPTOR Cache = &ptr->Cache;
        if (Cache->Level == 1) {
            _icache_line_size = _dcache_line_size = Cache->LineSize;
        }
        break;
      }
    }
    os::free(buffer);
  }

  {
    char* buf = ::getenv("PROCESSOR_IDENTIFIER");
    if (buf && strstr(buf, "Ampere(TM)") != nullptr) {
      _cpu = CPU_AMCC;
    } else if (buf && strstr(buf, "Cavium Inc.") != nullptr) {
      _cpu = CPU_CAVIUM;
    } else if (buf && strstr(buf, "Qualcomm Technologies Inc") != nullptr) {
      _cpu = CPU_QUALCOMM;
    } else {
      log_info(os)("VM_Version: unknown CPU model");
    }

    if (_cpu) {
      SYSTEM_INFO si;
      GetSystemInfo(&si);
      _model = si.wProcessorLevel;
      _variant = (si.wProcessorRevision >> 8) & 0xFF; // Variant is the upper byte of wProcessorRevision
      _revision = si.wProcessorRevision & 0xFF; // Revision is the lower byte of wProcessorRevision
    }
  }
}

void VM_Version::get_compatible_board(char *buf, int buflen) {
  assert(buf != nullptr, "invalid argument");
  assert(buflen >= 1, "invalid argument");
  *buf = '\0';
}
