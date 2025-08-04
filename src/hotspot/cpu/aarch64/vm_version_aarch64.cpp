/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2015, 2020, Red Hat Inc. All rights reserved.
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

#include "pauth_aarch64.hpp"
#include "register_aarch64.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/macros.hpp"

int VM_Version::_cpu;
int VM_Version::_model;
int VM_Version::_model2;
int VM_Version::_variant;
int VM_Version::_revision;
int VM_Version::_stepping;

int VM_Version::_zva_length;
int VM_Version::_dcache_line_size;
int VM_Version::_icache_line_size;
int VM_Version::_initial_sve_vector_length;
int VM_Version::_max_supported_sve_vector_length;
bool VM_Version::_rop_protection;
uintptr_t VM_Version::_pac_mask;

SpinWait VM_Version::_spin_wait;

static SpinWait get_spin_wait_desc() {
  SpinWait spin_wait(OnSpinWaitInst, OnSpinWaitInstCount);
  if (spin_wait.inst() == SpinWait::SB && !VM_Version::supports_sb()) {
    vm_exit_during_initialization("OnSpinWaitInst is SB but current CPU does not support SB instruction");
  }

  return spin_wait;
}

void VM_Version::initialize() {
  _supports_atomic_getset4 = true;
  _supports_atomic_getadd4 = true;
  _supports_atomic_getset8 = true;
  _supports_atomic_getadd8 = true;

  get_os_cpu_info();

  int dcache_line = VM_Version::dcache_line_size();

  // Limit AllocatePrefetchDistance so that it does not exceed the
  // static constraint of 512 defined in runtime/globals.hpp.
  if (FLAG_IS_DEFAULT(AllocatePrefetchDistance))
    FLAG_SET_DEFAULT(AllocatePrefetchDistance, MIN2(512, 3*dcache_line));

  if (FLAG_IS_DEFAULT(AllocatePrefetchStepSize))
    FLAG_SET_DEFAULT(AllocatePrefetchStepSize, dcache_line);
  if (FLAG_IS_DEFAULT(PrefetchScanIntervalInBytes))
    FLAG_SET_DEFAULT(PrefetchScanIntervalInBytes, 3*dcache_line);
  if (FLAG_IS_DEFAULT(PrefetchCopyIntervalInBytes))
    FLAG_SET_DEFAULT(PrefetchCopyIntervalInBytes, 3*dcache_line);
  if (FLAG_IS_DEFAULT(SoftwarePrefetchHintDistance))
    FLAG_SET_DEFAULT(SoftwarePrefetchHintDistance, 3*dcache_line);

  if (PrefetchCopyIntervalInBytes != -1 &&
       ((PrefetchCopyIntervalInBytes & 7) || (PrefetchCopyIntervalInBytes >= 32768))) {
    warning("PrefetchCopyIntervalInBytes must be -1, or a multiple of 8 and < 32768");
    PrefetchCopyIntervalInBytes &= ~7;
    if (PrefetchCopyIntervalInBytes >= 32768)
      PrefetchCopyIntervalInBytes = 32760;
  }

  if (AllocatePrefetchDistance != -1 && (AllocatePrefetchDistance & 7)) {
    warning("AllocatePrefetchDistance must be multiple of 8");
    AllocatePrefetchDistance &= ~7;
  }

  if (AllocatePrefetchStepSize & 7) {
    warning("AllocatePrefetchStepSize must be multiple of 8");
    AllocatePrefetchStepSize &= ~7;
  }

  if (SoftwarePrefetchHintDistance != -1 &&
       (SoftwarePrefetchHintDistance & 7)) {
    warning("SoftwarePrefetchHintDistance must be -1, or a multiple of 8");
    SoftwarePrefetchHintDistance &= ~7;
  }

  if (FLAG_IS_DEFAULT(ContendedPaddingWidth) && (dcache_line > ContendedPaddingWidth)) {
    ContendedPaddingWidth = dcache_line;
  }

  if (os::supports_map_sync()) {
    // if dcpop is available publish data cache line flush size via
    // generic field, otherwise let if default to zero thereby
    // disabling writeback
    if (VM_Version::supports_dcpop()) {
      _data_cache_line_flush_size = dcache_line;
    }
  }

  // Enable vendor specific features

  // Ampere eMAG
  if (_cpu == CPU_AMCC && (_model == CPU_MODEL_EMAG) && (_variant == 0x3)) {
    if (FLAG_IS_DEFAULT(AvoidUnalignedAccesses)) {
      FLAG_SET_DEFAULT(AvoidUnalignedAccesses, true);
    }
    if (FLAG_IS_DEFAULT(UseSIMDForMemoryOps)) {
      FLAG_SET_DEFAULT(UseSIMDForMemoryOps, true);
    }
    if (FLAG_IS_DEFAULT(UseSIMDForArrayEquals)) {
      FLAG_SET_DEFAULT(UseSIMDForArrayEquals, !(_revision == 1 || _revision == 2));
    }
  }

  // Ampere CPUs
  if (_cpu == CPU_AMPERE && ((_model == CPU_MODEL_AMPERE_1)  ||
                             (_model == CPU_MODEL_AMPERE_1A) ||
                             (_model == CPU_MODEL_AMPERE_1B))) {
    if (FLAG_IS_DEFAULT(UseSIMDForMemoryOps)) {
      FLAG_SET_DEFAULT(UseSIMDForMemoryOps, true);
    }
    if (FLAG_IS_DEFAULT(OnSpinWaitInst)) {
      FLAG_SET_DEFAULT(OnSpinWaitInst, "isb");
    }
    if (FLAG_IS_DEFAULT(OnSpinWaitInstCount)) {
      FLAG_SET_DEFAULT(OnSpinWaitInstCount, 2);
    }
    if (FLAG_IS_DEFAULT(CodeEntryAlignment) &&
        (_model == CPU_MODEL_AMPERE_1A || _model == CPU_MODEL_AMPERE_1B)) {
      FLAG_SET_DEFAULT(CodeEntryAlignment, 32);
    }
    if (FLAG_IS_DEFAULT(AlwaysMergeDMB)) {
      FLAG_SET_DEFAULT(AlwaysMergeDMB, false);
    }
  }

  // ThunderX
  if (_cpu == CPU_CAVIUM && (_model == 0xA1)) {
    guarantee(_variant != 0, "Pre-release hardware no longer supported.");
    if (FLAG_IS_DEFAULT(AvoidUnalignedAccesses)) {
      FLAG_SET_DEFAULT(AvoidUnalignedAccesses, true);
    }
    if (FLAG_IS_DEFAULT(UseSIMDForMemoryOps)) {
      FLAG_SET_DEFAULT(UseSIMDForMemoryOps, (_variant > 0));
    }
    if (FLAG_IS_DEFAULT(UseSIMDForArrayEquals)) {
      FLAG_SET_DEFAULT(UseSIMDForArrayEquals, false);
    }
  }

  // ThunderX2
  if ((_cpu == CPU_CAVIUM && (_model == 0xAF)) ||
      (_cpu == CPU_BROADCOM && (_model == 0x516))) {
    if (FLAG_IS_DEFAULT(AvoidUnalignedAccesses)) {
      FLAG_SET_DEFAULT(AvoidUnalignedAccesses, true);
    }
    if (FLAG_IS_DEFAULT(UseSIMDForMemoryOps)) {
      FLAG_SET_DEFAULT(UseSIMDForMemoryOps, true);
    }
  }

  // HiSilicon TSV110
  if (_cpu == CPU_HISILICON && _model == 0xd01) {
    if (FLAG_IS_DEFAULT(AvoidUnalignedAccesses)) {
      FLAG_SET_DEFAULT(AvoidUnalignedAccesses, true);
    }
    if (FLAG_IS_DEFAULT(UseSIMDForMemoryOps)) {
      FLAG_SET_DEFAULT(UseSIMDForMemoryOps, true);
    }
  }

  // Cortex A53
  if (_cpu == CPU_ARM && model_is(0xd03)) {
    _features |= CPU_A53MAC;
    if (FLAG_IS_DEFAULT(UseSIMDForArrayEquals)) {
      FLAG_SET_DEFAULT(UseSIMDForArrayEquals, false);
    }
  }

  // Cortex A73
  if (_cpu == CPU_ARM && model_is(0xd09)) {
    if (FLAG_IS_DEFAULT(SoftwarePrefetchHintDistance)) {
      FLAG_SET_DEFAULT(SoftwarePrefetchHintDistance, -1);
    }
    // A73 is faster with short-and-easy-for-speculative-execution-loop
    if (FLAG_IS_DEFAULT(UseSimpleArrayEquals)) {
      FLAG_SET_DEFAULT(UseSimpleArrayEquals, true);
    }
  }

  // Neoverse
  //   N1: 0xd0c
  //   N2: 0xd49
  //   V1: 0xd40
  //   V2: 0xd4f
  if (_cpu == CPU_ARM && (model_is(0xd0c) || model_is(0xd49) ||
                          model_is(0xd40) || model_is(0xd4f))) {
    if (FLAG_IS_DEFAULT(UseSIMDForMemoryOps)) {
      FLAG_SET_DEFAULT(UseSIMDForMemoryOps, true);
    }

    if (FLAG_IS_DEFAULT(OnSpinWaitInst)) {
      FLAG_SET_DEFAULT(OnSpinWaitInst, "isb");
    }

    if (FLAG_IS_DEFAULT(OnSpinWaitInstCount)) {
      FLAG_SET_DEFAULT(OnSpinWaitInstCount, 1);
    }
    if (FLAG_IS_DEFAULT(AlwaysMergeDMB)) {
      FLAG_SET_DEFAULT(AlwaysMergeDMB, false);
    }
  }

  if (_features & (CPU_FP | CPU_ASIMD)) {
    if (FLAG_IS_DEFAULT(UseSignumIntrinsic)) {
      FLAG_SET_DEFAULT(UseSignumIntrinsic, true);
    }
  }

  if (FLAG_IS_DEFAULT(UseCRC32)) {
    UseCRC32 = VM_Version::supports_crc32();
  }

  if (UseCRC32 && !VM_Version::supports_crc32()) {
    warning("UseCRC32 specified, but not supported on this CPU");
    FLAG_SET_DEFAULT(UseCRC32, false);
  }

  // Neoverse
  //   V1: 0xd40
  //   V2: 0xd4f
  if (_cpu == CPU_ARM && (model_is(0xd40) || model_is(0xd4f))) {
    if (FLAG_IS_DEFAULT(UseCryptoPmullForCRC32)) {
      FLAG_SET_DEFAULT(UseCryptoPmullForCRC32, true);
    }
    if (FLAG_IS_DEFAULT(CodeEntryAlignment)) {
      FLAG_SET_DEFAULT(CodeEntryAlignment, 32);
    }
  }

  if (UseCryptoPmullForCRC32 && (!VM_Version::supports_pmull() || !VM_Version::supports_sha3() || !VM_Version::supports_crc32())) {
    warning("UseCryptoPmullForCRC32 specified, but not supported on this CPU");
    FLAG_SET_DEFAULT(UseCryptoPmullForCRC32, false);
  }

  if (FLAG_IS_DEFAULT(UseAdler32Intrinsics)) {
    FLAG_SET_DEFAULT(UseAdler32Intrinsics, true);
  }

  if (UseVectorizedMismatchIntrinsic) {
    warning("UseVectorizedMismatchIntrinsic specified, but not available on this CPU.");
    FLAG_SET_DEFAULT(UseVectorizedMismatchIntrinsic, false);
  }

  if (VM_Version::supports_lse()) {
    if (FLAG_IS_DEFAULT(UseLSE))
      FLAG_SET_DEFAULT(UseLSE, true);
  } else {
    if (UseLSE) {
      warning("UseLSE specified, but not supported on this CPU");
      FLAG_SET_DEFAULT(UseLSE, false);
    }
  }

  if (VM_Version::supports_aes()) {
    UseAES = UseAES || FLAG_IS_DEFAULT(UseAES);
    UseAESIntrinsics =
        UseAESIntrinsics || (UseAES && FLAG_IS_DEFAULT(UseAESIntrinsics));
    if (UseAESIntrinsics && !UseAES) {
      warning("UseAESIntrinsics enabled, but UseAES not, enabling");
      UseAES = true;
    }
    if (FLAG_IS_DEFAULT(UseAESCTRIntrinsics)) {
      FLAG_SET_DEFAULT(UseAESCTRIntrinsics, true);
    }
  } else {
    if (UseAES) {
      warning("AES instructions are not available on this CPU");
      FLAG_SET_DEFAULT(UseAES, false);
    }
    if (UseAESIntrinsics) {
      warning("AES intrinsics are not available on this CPU");
      FLAG_SET_DEFAULT(UseAESIntrinsics, false);
    }
    if (UseAESCTRIntrinsics) {
      warning("AES/CTR intrinsics are not available on this CPU");
      FLAG_SET_DEFAULT(UseAESCTRIntrinsics, false);
    }
  }


  if (FLAG_IS_DEFAULT(UseCRC32Intrinsics)) {
    UseCRC32Intrinsics = true;
  }

  if (VM_Version::supports_crc32()) {
    if (FLAG_IS_DEFAULT(UseCRC32CIntrinsics)) {
      FLAG_SET_DEFAULT(UseCRC32CIntrinsics, true);
    }
  } else if (UseCRC32CIntrinsics) {
    warning("CRC32C is not available on the CPU");
    FLAG_SET_DEFAULT(UseCRC32CIntrinsics, false);
  }

  if (FLAG_IS_DEFAULT(UseFMA)) {
    FLAG_SET_DEFAULT(UseFMA, true);
  }

  if (FLAG_IS_DEFAULT(UseMD5Intrinsics)) {
    UseMD5Intrinsics = true;
  }

  if (VM_Version::supports_sha1() || VM_Version::supports_sha256() ||
      VM_Version::supports_sha3() || VM_Version::supports_sha512()) {
    if (FLAG_IS_DEFAULT(UseSHA)) {
      FLAG_SET_DEFAULT(UseSHA, true);
    }
  } else if (UseSHA) {
    warning("SHA instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseSHA, false);
  }

  if (UseSHA && VM_Version::supports_sha1()) {
    if (FLAG_IS_DEFAULT(UseSHA1Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA1Intrinsics, true);
    }
  } else if (UseSHA1Intrinsics) {
    warning("Intrinsics for SHA-1 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA1Intrinsics, false);
  }

  if (UseSHA && VM_Version::supports_sha256()) {
    if (FLAG_IS_DEFAULT(UseSHA256Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA256Intrinsics, true);
    }
  } else if (UseSHA256Intrinsics) {
    warning("Intrinsics for SHA-224 and SHA-256 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA256Intrinsics, false);
  }

  if (UseSHA && VM_Version::supports_sha3()) {
    // Auto-enable UseSHA3Intrinsics on hardware with performance benefit.
    // Note that the evaluation of UseSHA3Intrinsics shows better performance
    // on Apple silicon but worse performance on Neoverse V1 and N2.
    if (_cpu == CPU_APPLE) {  // Apple silicon
      if (FLAG_IS_DEFAULT(UseSHA3Intrinsics)) {
        FLAG_SET_DEFAULT(UseSHA3Intrinsics, true);
      }
    }
  } else if (UseSHA3Intrinsics && UseSIMDForSHA3Intrinsic) {
    warning("Intrinsics for SHA3-224, SHA3-256, SHA3-384 and SHA3-512 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA3Intrinsics, false);
  }

  if (UseSHA && VM_Version::supports_sha512()) {
    if (FLAG_IS_DEFAULT(UseSHA512Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA512Intrinsics, true);
    }
  } else if (UseSHA512Intrinsics) {
    warning("Intrinsics for SHA-384 and SHA-512 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA512Intrinsics, false);
  }

  if (!(UseSHA1Intrinsics || UseSHA256Intrinsics || UseSHA3Intrinsics || UseSHA512Intrinsics)) {
    FLAG_SET_DEFAULT(UseSHA, false);
  }

  if (VM_Version::supports_pmull()) {
    if (FLAG_IS_DEFAULT(UseGHASHIntrinsics)) {
      FLAG_SET_DEFAULT(UseGHASHIntrinsics, true);
    }
  } else if (UseGHASHIntrinsics) {
    warning("GHASH intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseGHASHIntrinsics, false);
  }

  if (_features & CPU_ASIMD) {
    if (FLAG_IS_DEFAULT(UseChaCha20Intrinsics)) {
      UseChaCha20Intrinsics = true;
    }
  } else if (UseChaCha20Intrinsics) {
    if (!FLAG_IS_DEFAULT(UseChaCha20Intrinsics)) {
      warning("ChaCha20 intrinsic requires ASIMD instructions");
    }
    FLAG_SET_DEFAULT(UseChaCha20Intrinsics, false);
  }

  if (_features & CPU_ASIMD) {
      if (FLAG_IS_DEFAULT(UseKyberIntrinsics)) {
          UseKyberIntrinsics = true;
      }
  } else if (UseKyberIntrinsics) {
      if (!FLAG_IS_DEFAULT(UseKyberIntrinsics)) {
          warning("Kyber intrinsics require ASIMD instructions");
      }
      FLAG_SET_DEFAULT(UseKyberIntrinsics, false);
  }

  if (_features & CPU_ASIMD) {
      if (FLAG_IS_DEFAULT(UseDilithiumIntrinsics)) {
          UseDilithiumIntrinsics = true;
      }
  } else if (UseDilithiumIntrinsics) {
      if (!FLAG_IS_DEFAULT(UseDilithiumIntrinsics)) {
          warning("Dilithium intrinsics require ASIMD instructions");
      }
      FLAG_SET_DEFAULT(UseDilithiumIntrinsics, false);
  }

  if (FLAG_IS_DEFAULT(UseBASE64Intrinsics)) {
    UseBASE64Intrinsics = true;
  }

  if (is_zva_enabled()) {
    if (FLAG_IS_DEFAULT(UseBlockZeroing)) {
      FLAG_SET_DEFAULT(UseBlockZeroing, true);
    }
    if (FLAG_IS_DEFAULT(BlockZeroingLowLimit)) {
      FLAG_SET_DEFAULT(BlockZeroingLowLimit, 4 * VM_Version::zva_length());
    }
  } else if (UseBlockZeroing) {
    warning("DC ZVA is not available on this CPU");
    FLAG_SET_DEFAULT(UseBlockZeroing, false);
  }

  if (VM_Version::supports_sve2()) {
    if (FLAG_IS_DEFAULT(UseSVE)) {
      FLAG_SET_DEFAULT(UseSVE, 2);
    }
  } else if (VM_Version::supports_sve()) {
    if (FLAG_IS_DEFAULT(UseSVE)) {
      FLAG_SET_DEFAULT(UseSVE, 1);
    } else if (UseSVE > 1) {
      warning("SVE2 specified, but not supported on current CPU. Using SVE.");
      FLAG_SET_DEFAULT(UseSVE, 1);
    }
  } else if (UseSVE > 0) {
    warning("UseSVE specified, but not supported on current CPU. Disabling SVE.");
    FLAG_SET_DEFAULT(UseSVE, 0);
  }

  if (UseSVE > 0) {
    int vl = get_current_sve_vector_length();
    if (vl < 0) {
      warning("Unable to get SVE vector length on this system. "
              "Disabling SVE. Specify -XX:UseSVE=0 to shun this warning.");
      FLAG_SET_DEFAULT(UseSVE, 0);
    } else if ((vl == 0) || ((vl % FloatRegister::sve_vl_min) != 0) || !is_power_of_2(vl)) {
      warning("Detected SVE vector length (%d) should be a power of two and a multiple of %d. "
              "Disabling SVE. Specify -XX:UseSVE=0 to shun this warning.",
              vl, FloatRegister::sve_vl_min);
      FLAG_SET_DEFAULT(UseSVE, 0);
    } else {
      _initial_sve_vector_length = vl;
    }
  }

  // This machine allows unaligned memory accesses
  if (FLAG_IS_DEFAULT(UseUnalignedAccesses)) {
    FLAG_SET_DEFAULT(UseUnalignedAccesses, true);
  }

  if (FLAG_IS_DEFAULT(UsePopCountInstruction)) {
    FLAG_SET_DEFAULT(UsePopCountInstruction, true);
  }

  if (!UsePopCountInstruction) {
    warning("UsePopCountInstruction is always enabled on this CPU");
    UsePopCountInstruction = true;
  }

  if (UseBranchProtection == nullptr || strcmp(UseBranchProtection, "none") == 0) {
    _rop_protection = false;
  } else if (strcmp(UseBranchProtection, "standard") == 0 ||
             strcmp(UseBranchProtection, "pac-ret") == 0) {
    _rop_protection = false;
    // Enable ROP-protection if
    // 1) this code has been built with branch-protection and
    // 2) the CPU/OS supports it
#ifdef __ARM_FEATURE_PAC_DEFAULT
    if (!VM_Version::supports_paca()) {
      // Disable PAC to prevent illegal instruction crashes.
      warning("ROP-protection specified, but not supported on this CPU. Disabling ROP-protection.");
    } else {
      _rop_protection = true;
    }
#else
    warning("ROP-protection specified, but this VM was built without ROP-protection support. Disabling ROP-protection.");
#endif
  } else {
    vm_exit_during_initialization(err_msg("Unsupported UseBranchProtection: %s", UseBranchProtection));
  }

  if (_rop_protection == true) {
    // Determine the mask of address bits used for PAC. Clear bit 55 of
    // the input to make it look like a user address.
    _pac_mask = (uintptr_t)pauth_strip_pointer((address)~(UINT64_C(1) << 55));
  }

#ifdef COMPILER2
  if (FLAG_IS_DEFAULT(UseMultiplyToLenIntrinsic)) {
    UseMultiplyToLenIntrinsic = true;
  }

  if (FLAG_IS_DEFAULT(UseSquareToLenIntrinsic)) {
    UseSquareToLenIntrinsic = true;
  }

  if (FLAG_IS_DEFAULT(UseMulAddIntrinsic)) {
    UseMulAddIntrinsic = true;
  }

  if (FLAG_IS_DEFAULT(UseMontgomeryMultiplyIntrinsic)) {
    UseMontgomeryMultiplyIntrinsic = true;
  }
  if (FLAG_IS_DEFAULT(UseMontgomerySquareIntrinsic)) {
    UseMontgomerySquareIntrinsic = true;
  }

  if (UseSVE > 0) {
    if (FLAG_IS_DEFAULT(MaxVectorSize)) {
      MaxVectorSize = _initial_sve_vector_length;
    } else if (MaxVectorSize < FloatRegister::sve_vl_min) {
      warning("SVE does not support vector length less than %d bytes. Disabling SVE.",
              FloatRegister::sve_vl_min);
      UseSVE = 0;
    } else if (!((MaxVectorSize % FloatRegister::sve_vl_min) == 0 && is_power_of_2(MaxVectorSize))) {
      vm_exit_during_initialization(err_msg("Unsupported MaxVectorSize: %d", (int)MaxVectorSize));
    }

    if (UseSVE > 0) {
      // Acquire the largest supported vector length of this machine
      _max_supported_sve_vector_length = set_and_get_current_sve_vector_length(FloatRegister::sve_vl_max);

      if (MaxVectorSize != _max_supported_sve_vector_length) {
        int new_vl = set_and_get_current_sve_vector_length(MaxVectorSize);
        if (new_vl < 0) {
          vm_exit_during_initialization(
            err_msg("Current system does not support SVE vector length for MaxVectorSize: %d",
                    (int)MaxVectorSize));
        } else if (new_vl != MaxVectorSize) {
          warning("Current system only supports max SVE vector length %d. Set MaxVectorSize to %d",
                  new_vl, new_vl);
        }
        MaxVectorSize = new_vl;
      }
      _initial_sve_vector_length = MaxVectorSize;
    }
  }

  if (UseSVE == 0) {  // NEON
    int min_vector_size = 8;
    int max_vector_size = FloatRegister::neon_vl;
    if (!FLAG_IS_DEFAULT(MaxVectorSize)) {
      if (!is_power_of_2(MaxVectorSize)) {
        vm_exit_during_initialization(err_msg("Unsupported MaxVectorSize: %d", (int)MaxVectorSize));
      } else if (MaxVectorSize < min_vector_size) {
        warning("MaxVectorSize must be at least %i on this platform", min_vector_size);
        FLAG_SET_DEFAULT(MaxVectorSize, min_vector_size);
      } else if (MaxVectorSize > max_vector_size) {
        warning("MaxVectorSize must be at most %i on this platform", max_vector_size);
        FLAG_SET_DEFAULT(MaxVectorSize, max_vector_size);
      }
    } else {
      FLAG_SET_DEFAULT(MaxVectorSize, FloatRegister::neon_vl);
    }
  }

  int inline_size = (UseSVE > 0 && MaxVectorSize >= FloatRegister::sve_vl_min) ? MaxVectorSize : 0;
  if (FLAG_IS_DEFAULT(ArrayOperationPartialInlineSize)) {
    FLAG_SET_DEFAULT(ArrayOperationPartialInlineSize, inline_size);
  } else if (ArrayOperationPartialInlineSize != 0 && ArrayOperationPartialInlineSize != inline_size) {
    warning("Setting ArrayOperationPartialInlineSize to %d", inline_size);
    ArrayOperationPartialInlineSize = inline_size;
  }

  if (FLAG_IS_DEFAULT(OptoScheduling)) {
    OptoScheduling = true;
  }

  if (FLAG_IS_DEFAULT(AlignVector)) {
    AlignVector = AvoidUnalignedAccesses;
  }

  if (FLAG_IS_DEFAULT(UsePoly1305Intrinsics)) {
    FLAG_SET_DEFAULT(UsePoly1305Intrinsics, true);
  }

  if (FLAG_IS_DEFAULT(UseVectorizedHashCodeIntrinsic)) {
    FLAG_SET_DEFAULT(UseVectorizedHashCodeIntrinsic, true);
  }
#endif

  _spin_wait = get_spin_wait_desc();

  check_virtualizations();

  // Sync SVE related CPU features with flags
  if (UseSVE < 2) {
    _features &= ~CPU_SVE2;
    _features &= ~CPU_SVEBITPERM;
  }
  if (UseSVE < 1) {
    _features &= ~CPU_SVE;
  }

  // Construct the "features" string
  char buf[512];
  int buf_used_len = os::snprintf_checked(buf, sizeof(buf), "0x%02x:0x%x:0x%03x:%d", _cpu, _variant, _model, _revision);
  if (_model2) {
    os::snprintf_checked(buf + buf_used_len, sizeof(buf) - buf_used_len, "(0x%03x)", _model2);
  }
  size_t features_offset = strnlen(buf, sizeof(buf));
#define ADD_FEATURE_IF_SUPPORTED(id, name, bit)                 \
  do {                                                          \
    if (VM_Version::supports_##name()) strcat(buf, ", " #name); \
  } while(0);
  CPU_FEATURE_FLAGS(ADD_FEATURE_IF_SUPPORTED)
#undef ADD_FEATURE_IF_SUPPORTED

  _cpu_info_string = os::strdup(buf);

  _features_string = extract_features_string(_cpu_info_string,
                                             strnlen(_cpu_info_string, sizeof(buf)),
                                             features_offset);
}

#if defined(LINUX)
static bool check_info_file(const char* fpath,
                            const char* virt1, VirtualizationType vt1,
                            const char* virt2, VirtualizationType vt2) {
  char line[500];
  FILE* fp = os::fopen(fpath, "r");
  if (fp == nullptr) {
    return false;
  }
  while (fgets(line, sizeof(line), fp) != nullptr) {
    if (strcasestr(line, virt1) != nullptr) {
      Abstract_VM_Version::_detected_virtualization = vt1;
      fclose(fp);
      return true;
    }
    if (virt2 != nullptr && strcasestr(line, virt2) != nullptr) {
      Abstract_VM_Version::_detected_virtualization = vt2;
      fclose(fp);
      return true;
    }
  }
  fclose(fp);
  return false;
}
#endif

void VM_Version::check_virtualizations() {
#if defined(LINUX)
  const char* pname_file = "/sys/devices/virtual/dmi/id/product_name";
  const char* tname_file = "/sys/hypervisor/type";
  if (check_info_file(pname_file, "KVM", KVM, "VMWare", VMWare)) {
    return;
  }
  check_info_file(tname_file, "Xen", XenPVHVM, nullptr, NoDetectedVirtualization);
#endif
}

void VM_Version::print_platform_virtualization_info(outputStream* st) {
#if defined(LINUX)
  VirtualizationType vrt = VM_Version::get_detected_virtualization();
  if (vrt == KVM) {
    st->print_cr("KVM virtualization detected");
  } else if (vrt == VMWare) {
    st->print_cr("VMWare virtualization detected");
  } else if (vrt == XenPVHVM) {
    st->print_cr("Xen virtualization detected");
  }
#endif
}

void VM_Version::initialize_cpu_information(void) {
  // do nothing if cpu info has been initialized
  if (_initialized) {
    return;
  }

  _no_of_cores  = os::processor_count();
  _no_of_threads = _no_of_cores;
  _no_of_sockets = _no_of_cores;
  snprintf(_cpu_name, CPU_TYPE_DESC_BUF_SIZE - 1, "AArch64");

  int desc_len = snprintf(_cpu_desc, CPU_DETAILED_DESC_BUF_SIZE, "AArch64 ");
  get_compatible_board(_cpu_desc + desc_len, CPU_DETAILED_DESC_BUF_SIZE - desc_len);
  desc_len = (int)strlen(_cpu_desc);
  snprintf(_cpu_desc + desc_len, CPU_DETAILED_DESC_BUF_SIZE - desc_len, " %s", _cpu_info_string);

  _initialized = true;
}
