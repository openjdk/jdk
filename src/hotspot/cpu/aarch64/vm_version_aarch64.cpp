/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2015, Red Hat Inc. All rights reserved.
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
#include "asm/macroAssembler.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/java.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "utilities/macros.hpp"
#include "vm_version_aarch64.hpp"

#include OS_HEADER_INLINE(os)

#ifndef BUILTIN_SIM
#include <sys/auxv.h>
#include <asm/hwcap.h>
#else
#define getauxval(hwcap) 0
#endif

#ifndef HWCAP_AES
#define HWCAP_AES   (1<<3)
#endif

#ifndef HWCAP_PMULL
#define HWCAP_PMULL (1<<4)
#endif

#ifndef HWCAP_SHA1
#define HWCAP_SHA1  (1<<5)
#endif

#ifndef HWCAP_SHA2
#define HWCAP_SHA2  (1<<6)
#endif

#ifndef HWCAP_CRC32
#define HWCAP_CRC32 (1<<7)
#endif

#ifndef HWCAP_ATOMICS
#define HWCAP_ATOMICS (1<<8)
#endif

int VM_Version::_cpu;
int VM_Version::_model;
int VM_Version::_model2;
int VM_Version::_variant;
int VM_Version::_revision;
int VM_Version::_stepping;
VM_Version::PsrInfo VM_Version::_psr_info   = { 0, };

static BufferBlob* stub_blob;
static const int stub_size = 550;

extern "C" {
  typedef void (*getPsrInfo_stub_t)(void*);
}
static getPsrInfo_stub_t getPsrInfo_stub = NULL;


class VM_Version_StubGenerator: public StubCodeGenerator {
 public:

  VM_Version_StubGenerator(CodeBuffer *c) : StubCodeGenerator(c) {}

  address generate_getPsrInfo() {
    StubCodeMark mark(this, "VM_Version", "getPsrInfo_stub");
#   define __ _masm->
    address start = __ pc();

#ifdef BUILTIN_SIM
    __ c_stub_prolog(1, 0, MacroAssembler::ret_type_void);
#endif

    // void getPsrInfo(VM_Version::PsrInfo* psr_info);

    address entry = __ pc();

    __ enter();

    __ get_dczid_el0(rscratch1);
    __ strw(rscratch1, Address(c_rarg0, in_bytes(VM_Version::dczid_el0_offset())));

    __ get_ctr_el0(rscratch1);
    __ strw(rscratch1, Address(c_rarg0, in_bytes(VM_Version::ctr_el0_offset())));

    __ leave();
    __ ret(lr);

#   undef __

    return start;
  }
};


void VM_Version::get_processor_features() {
  _supports_cx8 = true;
  _supports_atomic_getset4 = true;
  _supports_atomic_getadd4 = true;
  _supports_atomic_getset8 = true;
  _supports_atomic_getadd8 = true;

  getPsrInfo_stub(&_psr_info);

  int dcache_line = VM_Version::dcache_line_size();

  if (FLAG_IS_DEFAULT(AllocatePrefetchDistance))
    FLAG_SET_DEFAULT(AllocatePrefetchDistance, 3*dcache_line);
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

  if (AllocatePrefetchDistance !=-1 && (AllocatePrefetchDistance & 7)) {
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

  unsigned long auxv = getauxval(AT_HWCAP);

  char buf[512];

  _features = auxv;

  int cpu_lines = 0;
  if (FILE *f = fopen("/proc/cpuinfo", "r")) {
    char buf[128], *p;
    while (fgets(buf, sizeof (buf), f) != NULL) {
      if (p = strchr(buf, ':')) {
        long v = strtol(p+1, NULL, 0);
        if (strncmp(buf, "CPU implementer", sizeof "CPU implementer" - 1) == 0) {
          _cpu = v;
          cpu_lines++;
        } else if (strncmp(buf, "CPU variant", sizeof "CPU variant" - 1) == 0) {
          _variant = v;
        } else if (strncmp(buf, "CPU part", sizeof "CPU part" - 1) == 0) {
          if (_model != v)  _model2 = _model;
          _model = v;
        } else if (strncmp(buf, "CPU revision", sizeof "CPU revision" - 1) == 0) {
          _revision = v;
        }
      }
    }
    fclose(f);
  }

  // Enable vendor specific features

  // ThunderX
  if (_cpu == CPU_CAVIUM && (_model == 0xA1)) {
    if (_variant == 0) _features |= CPU_DMB_ATOMICS;
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

  // Cortex A53
  if (_cpu == CPU_ARM && (_model == 0xd03 || _model2 == 0xd03)) {
    _features |= CPU_A53MAC;
    if (FLAG_IS_DEFAULT(UseSIMDForArrayEquals)) {
      FLAG_SET_DEFAULT(UseSIMDForArrayEquals, false);
    }
  }

  // Cortex A73
  if (_cpu == CPU_ARM && (_model == 0xd09 || _model2 == 0xd09)) {
    if (FLAG_IS_DEFAULT(SoftwarePrefetchHintDistance)) {
      FLAG_SET_DEFAULT(SoftwarePrefetchHintDistance, -1);
    }
    // A73 is faster with short-and-easy-for-speculative-execution-loop
    if (FLAG_IS_DEFAULT(UseSimpleArrayEquals)) {
      FLAG_SET_DEFAULT(UseSimpleArrayEquals, true);
    }
  }

  if (_cpu == CPU_ARM && (_model == 0xd07 || _model2 == 0xd07)) _features |= CPU_STXR_PREFETCH;
  // If an olde style /proc/cpuinfo (cpu_lines == 1) then if _model is an A57 (0xd07)
  // we assume the worst and assume we could be on a big little system and have
  // undisclosed A53 cores which we could be swapped to at any stage
  if (_cpu == CPU_ARM && cpu_lines == 1 && _model == 0xd07) _features |= CPU_A53MAC;

  sprintf(buf, "0x%02x:0x%x:0x%03x:%d", _cpu, _variant, _model, _revision);
  if (_model2) sprintf(buf+strlen(buf), "(0x%03x)", _model2);
  if (auxv & HWCAP_ASIMD) strcat(buf, ", simd");
  if (auxv & HWCAP_CRC32) strcat(buf, ", crc");
  if (auxv & HWCAP_AES)   strcat(buf, ", aes");
  if (auxv & HWCAP_SHA1)  strcat(buf, ", sha1");
  if (auxv & HWCAP_SHA2)  strcat(buf, ", sha256");
  if (auxv & HWCAP_ATOMICS) strcat(buf, ", lse");

  _features_string = os::strdup(buf);

  if (FLAG_IS_DEFAULT(UseCRC32)) {
    UseCRC32 = (auxv & HWCAP_CRC32) != 0;
  }
  if (UseCRC32 && (auxv & HWCAP_CRC32) == 0) {
    warning("UseCRC32 specified, but not supported on this CPU");
  }

  if (FLAG_IS_DEFAULT(UseAdler32Intrinsics)) {
    FLAG_SET_DEFAULT(UseAdler32Intrinsics, true);
  }

  if (UseVectorizedMismatchIntrinsic) {
    warning("UseVectorizedMismatchIntrinsic specified, but not available on this CPU.");
    FLAG_SET_DEFAULT(UseVectorizedMismatchIntrinsic, false);
  }

  if (auxv & HWCAP_ATOMICS) {
    if (FLAG_IS_DEFAULT(UseLSE))
      FLAG_SET_DEFAULT(UseLSE, true);
  } else {
    if (UseLSE) {
      warning("UseLSE specified, but not supported on this CPU");
    }
  }

  if (auxv & HWCAP_AES) {
    UseAES = UseAES || FLAG_IS_DEFAULT(UseAES);
    UseAESIntrinsics =
        UseAESIntrinsics || (UseAES && FLAG_IS_DEFAULT(UseAESIntrinsics));
    if (UseAESIntrinsics && !UseAES) {
      warning("UseAESIntrinsics enabled, but UseAES not, enabling");
      UseAES = true;
    }
  } else {
    if (UseAES) {
      warning("UseAES specified, but not supported on this CPU");
    }
    if (UseAESIntrinsics) {
      warning("UseAESIntrinsics specified, but not supported on this CPU");
    }
  }

  if (UseAESCTRIntrinsics) {
    warning("AES/CTR intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseAESCTRIntrinsics, false);
  }

  if (FLAG_IS_DEFAULT(UseCRC32Intrinsics)) {
    UseCRC32Intrinsics = true;
  }

  if (auxv & HWCAP_CRC32) {
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

  if (auxv & (HWCAP_SHA1 | HWCAP_SHA2)) {
    if (FLAG_IS_DEFAULT(UseSHA)) {
      FLAG_SET_DEFAULT(UseSHA, true);
    }
  } else if (UseSHA) {
    warning("SHA instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseSHA, false);
  }

  if (UseSHA && (auxv & HWCAP_SHA1)) {
    if (FLAG_IS_DEFAULT(UseSHA1Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA1Intrinsics, true);
    }
  } else if (UseSHA1Intrinsics) {
    warning("Intrinsics for SHA-1 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA1Intrinsics, false);
  }

  if (UseSHA && (auxv & HWCAP_SHA2)) {
    if (FLAG_IS_DEFAULT(UseSHA256Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA256Intrinsics, true);
    }
  } else if (UseSHA256Intrinsics) {
    warning("Intrinsics for SHA-224 and SHA-256 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA256Intrinsics, false);
  }

  if (UseSHA512Intrinsics) {
    warning("Intrinsics for SHA-384 and SHA-512 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA512Intrinsics, false);
  }

  if (!(UseSHA1Intrinsics || UseSHA256Intrinsics || UseSHA512Intrinsics)) {
    FLAG_SET_DEFAULT(UseSHA, false);
  }

  if (auxv & HWCAP_PMULL) {
    if (FLAG_IS_DEFAULT(UseGHASHIntrinsics)) {
      FLAG_SET_DEFAULT(UseGHASHIntrinsics, true);
    }
  } else if (UseGHASHIntrinsics) {
    warning("GHASH intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseGHASHIntrinsics, false);
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

  // This machine allows unaligned memory accesses
  if (FLAG_IS_DEFAULT(UseUnalignedAccesses)) {
    FLAG_SET_DEFAULT(UseUnalignedAccesses, true);
  }

  if (FLAG_IS_DEFAULT(UseBarriersForVolatile)) {
    UseBarriersForVolatile = (_features & CPU_DMB_ATOMICS) != 0;
  }

  if (FLAG_IS_DEFAULT(UsePopCountInstruction)) {
    UsePopCountInstruction = true;
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

  if (FLAG_IS_DEFAULT(OptoScheduling)) {
    OptoScheduling = true;
  }
#endif
}

void VM_Version::initialize() {
  ResourceMark rm;

  stub_blob = BufferBlob::create("getPsrInfo_stub", stub_size);
  if (stub_blob == NULL) {
    vm_exit_during_initialization("Unable to allocate getPsrInfo_stub");
  }

  CodeBuffer c(stub_blob);
  VM_Version_StubGenerator g(&c);
  getPsrInfo_stub = CAST_TO_FN_PTR(getPsrInfo_stub_t,
                                   g.generate_getPsrInfo());

  get_processor_features();

  UNSUPPORTED_OPTION(CriticalJNINatives);
}
