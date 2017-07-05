/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/java.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "vm_version_arm.hpp"
#include <sys/auxv.h>
#include <asm/hwcap.h>

#ifndef HWCAP_AES
#define HWCAP_AES 1 << 3
#endif

bool VM_Version::_is_initialized = false;
bool VM_Version::_has_simd = false;

extern "C" {
  typedef bool (*check_simd_t)();
}


#ifdef COMPILER2

#define __ _masm->

class VM_Version_StubGenerator: public StubCodeGenerator {
 public:

  VM_Version_StubGenerator(CodeBuffer *c) : StubCodeGenerator(c) {}

  address generate_check_simd() {
    StubCodeMark mark(this, "VM_Version", "check_simd");
    address start = __ pc();

    __ vcnt(Stemp, Stemp);
    __ mov(R0, 1);
    __ ret(LR);

    return start;
  };
};

#undef __

#endif



extern "C" address check_simd_fault_instr;


void VM_Version::initialize() {
  ResourceMark rm;

  // Making this stub must be FIRST use of assembler
  const int stub_size = 128;
  BufferBlob* stub_blob = BufferBlob::create("get_cpu_info", stub_size);
  if (stub_blob == NULL) {
    vm_exit_during_initialization("Unable to allocate get_cpu_info stub");
  }

  if (UseFMA) {
    warning("FMA instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseFMA, false);
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

  if (UseCRC32Intrinsics) {
    if (!FLAG_IS_DEFAULT(UseCRC32Intrinsics))
      warning("CRC32 intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseCRC32Intrinsics, false);
  }

  if (UseCRC32CIntrinsics) {
    if (!FLAG_IS_DEFAULT(UseCRC32CIntrinsics))
      warning("CRC32C intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseCRC32CIntrinsics, false);
  }

  if (UseAdler32Intrinsics) {
    warning("Adler32 intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseAdler32Intrinsics, false);
  }

  if (UseVectorizedMismatchIntrinsic) {
    warning("vectorizedMismatch intrinsic is not available on this CPU.");
    FLAG_SET_DEFAULT(UseVectorizedMismatchIntrinsic, false);
  }

  CodeBuffer c(stub_blob);

#ifdef COMPILER2
  VM_Version_StubGenerator g(&c);

  address check_simd_pc = g.generate_check_simd();
  if (check_simd_pc != NULL) {
    check_simd_t check_simd = CAST_TO_FN_PTR(check_simd_t, check_simd_pc);
    check_simd_fault_instr = (address)check_simd;
    _has_simd = check_simd();
  } else {
    assert(! _has_simd, "default _has_simd value must be 'false'");
  }
#endif

  unsigned long auxv = getauxval(AT_HWCAP);

  char buf[512];
  jio_snprintf(buf, sizeof(buf), "AArch64%s",
               ((auxv & HWCAP_AES) ? ", aes" : ""));

  _features_string = os::strdup(buf);

#ifdef COMPILER2
  if (auxv & HWCAP_AES) {
    if (FLAG_IS_DEFAULT(UseAES)) {
      FLAG_SET_DEFAULT(UseAES, true);
    }
    if (!UseAES) {
      if (UseAESIntrinsics && !FLAG_IS_DEFAULT(UseAESIntrinsics)) {
        warning("AES intrinsics require UseAES flag to be enabled. Intrinsics will be disabled.");
      }
      FLAG_SET_DEFAULT(UseAESIntrinsics, false);
    } else {
      if (FLAG_IS_DEFAULT(UseAESIntrinsics)) {
        FLAG_SET_DEFAULT(UseAESIntrinsics, true);
      }
    }
  } else
#endif
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

  _supports_cx8 = true;
  _supports_atomic_getset4 = true;
  _supports_atomic_getadd4 = true;
  _supports_atomic_getset8 = true;
  _supports_atomic_getadd8 = true;

  // TODO-AARCH64 revise C2 flags

  if (has_simd()) {
    if (FLAG_IS_DEFAULT(UsePopCountInstruction)) {
      FLAG_SET_DEFAULT(UsePopCountInstruction, true);
    }
  }

  AllocatePrefetchDistance = 128;

#ifdef COMPILER2
  FLAG_SET_DEFAULT(UseFPUForSpilling, true);

  if (FLAG_IS_DEFAULT(MaxVectorSize)) {
    // FLAG_SET_DEFAULT(MaxVectorSize, has_simd() ? 16 : 8);
    // SIMD/NEON can use 16, but default is 8 because currently
    // larger than 8 will disable instruction scheduling
    FLAG_SET_DEFAULT(MaxVectorSize, 8);
  }

  if (MaxVectorSize > 16) {
    FLAG_SET_DEFAULT(MaxVectorSize, 8);
  }
#endif

  if (FLAG_IS_DEFAULT(Tier4CompileThreshold)) {
    Tier4CompileThreshold = 10000;
  }
  if (FLAG_IS_DEFAULT(Tier3InvocationThreshold)) {
    Tier3InvocationThreshold = 1000;
  }
  if (FLAG_IS_DEFAULT(Tier3CompileThreshold)) {
    Tier3CompileThreshold = 5000;
  }
  if (FLAG_IS_DEFAULT(Tier3MinInvocationThreshold)) {
    Tier3MinInvocationThreshold = 500;
  }

  FLAG_SET_DEFAULT(TypeProfileLevel, 0); // unsupported

  // This machine does not allow unaligned memory accesses
  if (UseUnalignedAccesses) {
    if (!FLAG_IS_DEFAULT(UseUnalignedAccesses))
      warning("Unaligned memory access is not available on this CPU");
    FLAG_SET_DEFAULT(UseUnalignedAccesses, false);
  }

  _is_initialized = true;
}

bool VM_Version::use_biased_locking() {
  // TODO-AARCH64 measure performance and revise

  // The cost of CAS on uniprocessor ARM v6 and later is low compared to the
  // overhead related to slightly longer Biased Locking execution path.
  // Testing shows no improvement when running with Biased Locking enabled
  // on an ARMv6 and higher uniprocessor systems.  The situation is different on
  // ARMv5 and MP systems.
  //
  // Therefore the Biased Locking is enabled on ARMv5 and ARM MP only.
  //
  return os::is_MP();
}
