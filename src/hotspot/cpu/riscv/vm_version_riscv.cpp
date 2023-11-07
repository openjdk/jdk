/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2023, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "runtime/java.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/macros.hpp"

#include <ctype.h>

uint32_t VM_Version::_initial_vector_length = 0;

#define DEF_RV_FEATURE(NAME, PRETTY, BIT, FSTRING, FLAGF)       \
VM_Version::NAME##RVFeatureValue VM_Version::NAME(PRETTY, BIT, FSTRING);
RV_FEATURE_FLAGS(DEF_RV_FEATURE)

#define ADD_RV_FEATURE_IN_LIST(NAME, PRETTY, BIT, FSTRING, FLAGF) \
    &VM_Version::NAME,
VM_Version::RVFeatureValue* VM_Version::_feature_list[] = {
RV_FEATURE_FLAGS(ADD_RV_FEATURE_IN_LIST)
  nullptr};

void VM_Version::initialize() {
  _supports_cx8 = true;
  _supports_atomic_getset4 = true;
  _supports_atomic_getadd4 = true;
  _supports_atomic_getset8 = true;
  _supports_atomic_getadd8 = true;

  setup_cpu_available_features();

  // check if satp.mode is supported, currently supports up to SV48(RV64)
  if (satp_mode.value() > VM_SV48 || satp_mode.value() < VM_MBARE) {
    vm_exit_during_initialization(
      err_msg(
         "Unsupported satp mode: SV%d. Only satp modes up to sv48 are supported for now.",
         (int)satp_mode.value()));
  }

  // https://github.com/riscv/riscv-profiles/blob/main/profiles.adoc#rva20-profiles
  if (UseRVA20U64) {
    if (FLAG_IS_DEFAULT(UseRVC)) {
      FLAG_SET_DEFAULT(UseRVC, true);
    }
  }
  // https://github.com/riscv/riscv-profiles/blob/main/profiles.adoc#rva22-profiles
  if (UseRVA22U64) {
    if (FLAG_IS_DEFAULT(UseRVC)) {
      FLAG_SET_DEFAULT(UseRVC, true);
    }
    if (FLAG_IS_DEFAULT(UseZba)) {
      FLAG_SET_DEFAULT(UseZba, true);
    }
    if (FLAG_IS_DEFAULT(UseZbb)) {
      FLAG_SET_DEFAULT(UseZbb, true);
    }
    if (FLAG_IS_DEFAULT(UseZbs)) {
      FLAG_SET_DEFAULT(UseZbs, true);
    }
    if (FLAG_IS_DEFAULT(UseZic64b)) {
      FLAG_SET_DEFAULT(UseZic64b, true);
    }
    if (FLAG_IS_DEFAULT(UseZicbom)) {
      FLAG_SET_DEFAULT(UseZicbom, true);
    }
    if (FLAG_IS_DEFAULT(UseZicbop)) {
      FLAG_SET_DEFAULT(UseZicbop, true);
    }
    if (FLAG_IS_DEFAULT(UseZicboz)) {
      FLAG_SET_DEFAULT(UseZicboz, true);
    }
    if (FLAG_IS_DEFAULT(UseZihintpause)) {
      FLAG_SET_DEFAULT(UseZihintpause, true);
    }
  }

  // Enable vendor specific features

  if (mvendorid.enabled()) {
    // Rivos
    if (mvendorid.value() == RIVOS) {
      if (FLAG_IS_DEFAULT(UseConservativeFence)) {
        FLAG_SET_DEFAULT(UseConservativeFence, false);
      }
    }
  }

  if (UseZic64b) {
    if (CacheLineSize != 64) {
      assert(!FLAG_IS_DEFAULT(CacheLineSize), "default cache line size should be 64 bytes");
      warning("CacheLineSize is assumed to be 64 bytes because Zic64b is enabled");
      FLAG_SET_DEFAULT(CacheLineSize, 64);
    }
  } else {
    if (!FLAG_IS_DEFAULT(CacheLineSize) && !is_power_of_2(CacheLineSize)) {
      warning("CacheLineSize must be a power of 2");
      FLAG_SET_DEFAULT(CacheLineSize, DEFAULT_CACHE_LINE_SIZE);
    }
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

  if (FLAG_IS_DEFAULT(UseCopySignIntrinsic)) {
      FLAG_SET_DEFAULT(UseCopySignIntrinsic, true);
  }

  if (FLAG_IS_DEFAULT(UseSignumIntrinsic)) {
      FLAG_SET_DEFAULT(UseSignumIntrinsic, true);
  }

  if (UseRVV) {
    if (!ext_V.enabled()) {
      warning("RVV is not supported on this CPU");
      FLAG_SET_DEFAULT(UseRVV, false);
    } else {
      // read vector length from vector CSR vlenb
      _initial_vector_length = cpu_vector_length();
    }
  }

  if (UseRVC && !ext_C.enabled()) {
    warning("RVC is not supported on this CPU");
    FLAG_SET_DEFAULT(UseRVC, false);

    if (UseRVA20U64) {
      warning("UseRVA20U64 is not supported on this CPU");
      FLAG_SET_DEFAULT(UseRVA20U64, false);
    }
  }

  if (FLAG_IS_DEFAULT(AvoidUnalignedAccesses)) {
    if (unaligned_access.value() != MISALIGNED_FAST) {
      FLAG_SET_DEFAULT(AvoidUnalignedAccesses, true);
    } else {
      FLAG_SET_DEFAULT(AvoidUnalignedAccesses, false);
    }
  }

  // See JDK-8026049
  // This machine has fast unaligned memory accesses
  if (FLAG_IS_DEFAULT(UseUnalignedAccesses)) {
    FLAG_SET_DEFAULT(UseUnalignedAccesses,
      unaligned_access.value() == MISALIGNED_FAST);
  }

#ifdef __riscv_ztso
  // Hotspot is compiled with TSO support, it will only run on hardware which
  // supports Ztso
  if (FLAG_IS_DEFAULT(UseZtso)) {
    FLAG_SET_DEFAULT(UseZtso, true);
  }
#endif

  if (UseZbb) {
    if (FLAG_IS_DEFAULT(UsePopCountInstruction)) {
      FLAG_SET_DEFAULT(UsePopCountInstruction, true);
    }
  } else {
    FLAG_SET_DEFAULT(UsePopCountInstruction, false);
  }

  if (UseZicboz) {
    if (FLAG_IS_DEFAULT(UseBlockZeroing)) {
      FLAG_SET_DEFAULT(UseBlockZeroing, true);
    }
    if (FLAG_IS_DEFAULT(BlockZeroingLowLimit)) {
      FLAG_SET_DEFAULT(BlockZeroingLowLimit, 2 * CacheLineSize);
    }
  } else if (UseBlockZeroing) {
    warning("Block zeroing is not available");
    FLAG_SET_DEFAULT(UseBlockZeroing, false);
  }
  if (UseRVV) {
    if (FLAG_IS_DEFAULT(UseChaCha20Intrinsics)) {
      FLAG_SET_DEFAULT(UseChaCha20Intrinsics, true);
    }
  } else if (UseChaCha20Intrinsics) {
    if (!FLAG_IS_DEFAULT(UseChaCha20Intrinsics)) {
      warning("Chacha20 intrinsic requires RVV instructions (not available on this CPU)");
    }
    FLAG_SET_DEFAULT(UseChaCha20Intrinsics, false);
  }

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
    } else if (!is_power_of_2(MaxVectorSize)) {
      vm_exit_during_initialization(err_msg("Unsupported MaxVectorSize: %d, must be a power of 2", (int)MaxVectorSize));
    } else if (MaxVectorSize > _initial_vector_length) {
      warning("Current system only supports max RVV vector length %d. Set MaxVectorSize to %d",
              _initial_vector_length, _initial_vector_length);
      MaxVectorSize = _initial_vector_length;
    }
    if (MaxVectorSize < 16) {
      warning("RVV does not support vector length less than 16 bytes. Disabling RVV.");
      UseRVV = false;
      FLAG_SET_DEFAULT(MaxVectorSize, 0);
    }
  }

  if (!UseZicbop) {
    if (!FLAG_IS_DEFAULT(AllocatePrefetchStyle)) {
      warning("Zicbop is not available on this CPU");
    }
    FLAG_SET_DEFAULT(AllocatePrefetchStyle, 0);
  } else {
    // Limit AllocatePrefetchDistance so that it does not exceed the
    // static constraint of 512 defined in runtime/globals.hpp.
    if (FLAG_IS_DEFAULT(AllocatePrefetchDistance)) {
      FLAG_SET_DEFAULT(AllocatePrefetchDistance, MIN2(512, 3 * (int)CacheLineSize));
    }
    if (FLAG_IS_DEFAULT(AllocatePrefetchStepSize)) {
      FLAG_SET_DEFAULT(AllocatePrefetchStepSize, (int)CacheLineSize);
    }
    if (FLAG_IS_DEFAULT(PrefetchScanIntervalInBytes)) {
      FLAG_SET_DEFAULT(PrefetchScanIntervalInBytes, 3 * (int)CacheLineSize);
    }
    if (FLAG_IS_DEFAULT(PrefetchCopyIntervalInBytes)) {
      FLAG_SET_DEFAULT(PrefetchCopyIntervalInBytes, 3 * (int)CacheLineSize);
    }

    if (PrefetchCopyIntervalInBytes != -1 &&
        ((PrefetchCopyIntervalInBytes & 7) || (PrefetchCopyIntervalInBytes >= 32768))) {
      warning("PrefetchCopyIntervalInBytes must be -1, or a multiple of 8 and < 32768");
      PrefetchCopyIntervalInBytes &= ~7;
      if (PrefetchCopyIntervalInBytes >= 32768) {
        PrefetchCopyIntervalInBytes = 32760;
      }
    }
    if (AllocatePrefetchDistance !=-1 && (AllocatePrefetchDistance & 7)) {
      warning("AllocatePrefetchDistance must be multiple of 8");
      AllocatePrefetchDistance &= ~7;
    }
    if (AllocatePrefetchStepSize & 7) {
      warning("AllocatePrefetchStepSize must be multiple of 8");
      AllocatePrefetchStepSize &= ~7;
    }
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

void VM_Version::initialize_cpu_information(void) {
  // do nothing if cpu info has been initialized
  if (_initialized) {
    return;
  }

  _no_of_cores  = os::processor_count();
  _no_of_threads = _no_of_cores;
  _no_of_sockets = _no_of_cores;
  snprintf(_cpu_name, CPU_TYPE_DESC_BUF_SIZE - 1, "RISCV64");
  snprintf(_cpu_desc, CPU_DETAILED_DESC_BUF_SIZE, "RISCV64 %s", features_string());
  _initialized = true;
}
