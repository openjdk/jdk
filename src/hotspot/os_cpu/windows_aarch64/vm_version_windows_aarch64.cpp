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

int VM_Version::get_current_sve_vector_length() {
  assert(VM_Version::supports_sve(), "should not call this");
  // TODO: This is not available in the Windows SDK yet , so conservatively go with the lowest value (128 bits)
  // https://developer.arm.com/documentation/101427/0102/Register-descriptions/Scalable-vector-extensions--SVE--registers/ZCR-EL1--SVE-Control-Register--EL1
  return VM_Version::supports_sve() ? 128 / 8 : 0; // This value is in bytes
}

int VM_Version::set_and_get_current_sve_vector_length(int length) {
  assert(VM_Version::supports_sve(), "should not call this");
  // TODO: This is not available in the Windows SDK yet , so conservatively go with the lowest value (128 bits)
  // https://developer.arm.com/documentation/101427/0102/Register-descriptions/Scalable-vector-extensions--SVE--registers/ZCR-EL1--SVE-Control-Register--EL1
  return VM_Version::supports_sve() ? 128 / 8 : 0; // This value is in bytes
}

void VM_Version::get_os_cpu_info() {

  if (IsProcessorFeaturePresent(PF_ARM_V8_CRC32_INSTRUCTIONS_AVAILABLE))   _features |= BIT_MASK(CPU_CRC32);
  if (IsProcessorFeaturePresent(PF_ARM_V8_CRYPTO_INSTRUCTIONS_AVAILABLE))  _features |= BIT_MASK(CPU_AES) | BIT_MASK(CPU_SHA1) | BIT_MASK(CPU_SHA2) | BIT_MASK(CPU_PMULL);
  if (IsProcessorFeaturePresent(PF_ARM_VFP_32_REGISTERS_AVAILABLE))        _features |= BIT_MASK(CPU_ASIMD);
  if (IsProcessorFeaturePresent(PF_ARM_V81_ATOMIC_INSTRUCTIONS_AVAILABLE)) _features |= BIT_MASK(CPU_LSE);
  if (IsProcessorFeaturePresent(PF_ARM_SVE_INSTRUCTIONS_AVAILABLE))        _features |= BIT_MASK(CPU_SVE);
  if (IsProcessorFeaturePresent(PF_ARM_SVE2_INSTRUCTIONS_AVAILABLE))       _features |= BIT_MASK(CPU_SVE2);
  if (IsProcessorFeaturePresent(PF_ARM_SVE_BITPERM_INSTRUCTIONS_AVAILABLE))  _features |= BIT_MASK(CPU_SVEBITPERM);
  if (IsProcessorFeaturePresent(PF_ARM_SHA3_INSTRUCTIONS_AVAILABLE))        _features |= BIT_MASK(CPU_SHA3);
  if (IsProcessorFeaturePresent(PF_ARM_SHA512_INSTRUCTIONS_AVAILABLE))      _features |= BIT_MASK(CPU_SHA512);


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
