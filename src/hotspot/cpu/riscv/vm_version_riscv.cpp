/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "utilities/formatBuffer.hpp"
#include "utilities/macros.hpp"

#include OS_HEADER_INLINE(os)

const char* VM_Version::_uarch = "";
const char* VM_Version::_vm_mode = "";
uint32_t VM_Version::_initial_vector_length = 0;

void VM_Version::initialize() {
  _supports_cx8 = true;
  _supports_atomic_getset4 = true;
  _supports_atomic_getadd4 = true;
  _supports_atomic_getset8 = true;
  _supports_atomic_getadd8 = true;

  get_os_cpu_info();

  // check if satp.mode is supported, currently supports up to SV48(RV64)
  if (get_satp_mode() > VM_SV48) {
    vm_exit_during_initialization(
      err_msg("Unsupported satp mode: %s. Only satp modes up to sv48 are supported for now.",
              _vm_mode));
  }

  if (FLAG_IS_DEFAULT(UseFMA)) {
    FLAG_SET_DEFAULT(UseFMA, true);
  }

  if (FLAG_IS_DEFAULT(AllocatePrefetchDistance)) {
    FLAG_SET_DEFAULT(AllocatePrefetchDistance, 0);
  }

  if (UseAES || UseAESIntrinsics) {
    if (UseAES && !FLAG_IS_DEFAULT(UseAES)) {
      warning("AES instructions are not available on this CPU");
      FLAG_SET_DEFAULT(UseAES, false);
    }
    if (UseAESIntrinsics && !FLAG_IS_DEFAULT(UseAESIntrinsics)) {
      warning("AES intrinsics are not available on this CPU");
      FLAG_SET_DEFAULT(UseAESIntrinsics, false);
    }
  }

  if (UseAESCTRIntrinsics) {
    warning("AES/CTR intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseAESCTRIntrinsics, false);
  }

  if (UseSHA) {
    warning("SHA instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseSHA, false);
  }

  if (UseSHA1Intrinsics) {
    warning("Intrinsics for SHA-1 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA1Intrinsics, false);
  }

  if (UseSHA256Intrinsics) {
    warning("Intrinsics for SHA-224 and SHA-256 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA256Intrinsics, false);
  }

  if (UseSHA512Intrinsics) {
    warning("Intrinsics for SHA-384 and SHA-512 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA512Intrinsics, false);
  }

  if (UseSHA3Intrinsics) {
    warning("Intrinsics for SHA3-224, SHA3-256, SHA3-384 and SHA3-512 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA3Intrinsics, false);
  }

  if (UseCRC32Intrinsics) {
    warning("CRC32 intrinsics are not available on this CPU.");
    FLAG_SET_DEFAULT(UseCRC32Intrinsics, false);
  }

  if (UseCRC32CIntrinsics) {
    warning("CRC32C intrinsics are not available on this CPU.");
    FLAG_SET_DEFAULT(UseCRC32CIntrinsics, false);
  }

  if (UseVectorizedMismatchIntrinsic) {
    warning("VectorizedMismatch intrinsic is not available on this CPU.");
    FLAG_SET_DEFAULT(UseVectorizedMismatchIntrinsic, false);
  }

  if (FLAG_IS_DEFAULT(UseMD5Intrinsics)) {
    FLAG_SET_DEFAULT(UseMD5Intrinsics, true);
  }

  if (UseRVV) {
    if (!(_features & CPU_V)) {
      warning("RVV is not supported on this CPU");
      FLAG_SET_DEFAULT(UseRVV, false);
    } else {
      // read vector length from vector CSR vlenb
      _initial_vector_length = get_current_vector_length();
    }
  }

  if (UseRVC && !(_features & CPU_C)) {
    warning("RVC is not supported on this CPU");
    FLAG_SET_DEFAULT(UseRVC, false);
  }

  if (FLAG_IS_DEFAULT(AvoidUnalignedAccesses)) {
    FLAG_SET_DEFAULT(AvoidUnalignedAccesses, true);
  }

  if (UseZbb) {
    if (FLAG_IS_DEFAULT(UsePopCountInstruction)) {
      FLAG_SET_DEFAULT(UsePopCountInstruction, true);
    }
  } else {
    FLAG_SET_DEFAULT(UsePopCountInstruction, false);
  }

  char buf[512];
  buf[0] = '\0';
  if (_uarch != NULL && strcmp(_uarch, "") != 0) snprintf(buf, sizeof(buf), "%s,", _uarch);
  strcat(buf, "rv64");
#define ADD_FEATURE_IF_SUPPORTED(id, name, bit) if (_features & CPU_##id) strcat(buf, name);
  CPU_FEATURE_FLAGS(ADD_FEATURE_IF_SUPPORTED)
#undef ADD_FEATURE_IF_SUPPORTED

  _features_string = os::strdup(buf);

#ifdef COMPILER2
  c2_initialize();
#endif // COMPILER2
}

#ifdef COMPILER2
void VM_Version::c2_initialize() {
  if (UseCMoveUnconditionally) {
    FLAG_SET_DEFAULT(UseCMoveUnconditionally, false);
  }

  if (ConditionalMoveLimit > 0) {
    FLAG_SET_DEFAULT(ConditionalMoveLimit, 0);
  }

  if (!UseRVV) {
    FLAG_SET_DEFAULT(SpecialEncodeISOArray, false);
  }

  if (!UseRVV && MaxVectorSize) {
    FLAG_SET_DEFAULT(MaxVectorSize, 0);
  }

  if (!UseRVV) {
    FLAG_SET_DEFAULT(UseRVVForBigIntegerShiftIntrinsics, false);
  }

  if (UseRVV) {
    if (FLAG_IS_DEFAULT(MaxVectorSize)) {
      MaxVectorSize = _initial_vector_length;
    } else if (MaxVectorSize < 16) {
      warning("RVV does not support vector length less than 16 bytes. Disabling RVV.");
      UseRVV = false;
    } else if (is_power_of_2(MaxVectorSize)) {
      if (MaxVectorSize > _initial_vector_length) {
        warning("Current system only supports max RVV vector length %d. Set MaxVectorSize to %d",
                _initial_vector_length, _initial_vector_length);
      }
      MaxVectorSize = _initial_vector_length;
    } else {
      vm_exit_during_initialization(err_msg("Unsupported MaxVectorSize: %d", (int)MaxVectorSize));
    }
  }

  // disable prefetch
  if (FLAG_IS_DEFAULT(AllocatePrefetchStyle)) {
    FLAG_SET_DEFAULT(AllocatePrefetchStyle, 0);
  }

  if (FLAG_IS_DEFAULT(UseMulAddIntrinsic)) {
    FLAG_SET_DEFAULT(UseMulAddIntrinsic, true);
  }

  if (FLAG_IS_DEFAULT(UseMultiplyToLenIntrinsic)) {
    FLAG_SET_DEFAULT(UseMultiplyToLenIntrinsic, true);
  }

  if (FLAG_IS_DEFAULT(UseSquareToLenIntrinsic)) {
    FLAG_SET_DEFAULT(UseSquareToLenIntrinsic, true);
  }

  if (FLAG_IS_DEFAULT(UseMontgomeryMultiplyIntrinsic)) {
    FLAG_SET_DEFAULT(UseMontgomeryMultiplyIntrinsic, true);
  }

  if (FLAG_IS_DEFAULT(UseMontgomerySquareIntrinsic)) {
    FLAG_SET_DEFAULT(UseMontgomerySquareIntrinsic, true);
  }
}
#endif // COMPILER2
