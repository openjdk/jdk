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

int  VM_Version::_stored_pc_adjustment = 4;
int  VM_Version::_arm_arch             = 5;
bool VM_Version::_is_initialized       = false;
int VM_Version::_kuser_helper_version  = 0;

extern "C" {
  typedef int (*get_cpu_info_t)();
  typedef bool (*check_vfp_t)(double *d);
  typedef bool (*check_simd_t)();
}

#define __ _masm->

class VM_Version_StubGenerator: public StubCodeGenerator {
 public:

  VM_Version_StubGenerator(CodeBuffer *c) : StubCodeGenerator(c) {}

  address generate_get_cpu_info() {
    StubCodeMark mark(this, "VM_Version", "get_cpu_info");
    address start = __ pc();

    __ mov(R0, PC);
    __ push(PC);
    __ pop(R1);
    __ sub(R0, R1, R0);
    // return the result in R0
    __ bx(LR);

    return start;
  };

  address generate_check_vfp() {
    StubCodeMark mark(this, "VM_Version", "check_vfp");
    address start = __ pc();

    __ fstd(D0, Address(R0));
    __ mov(R0, 1);
    __ bx(LR);

    return start;
  };

  address generate_check_vfp3_32() {
    StubCodeMark mark(this, "VM_Version", "check_vfp3_32");
    address start = __ pc();

    __ fstd(D16, Address(R0));
    __ mov(R0, 1);
    __ bx(LR);

    return start;
  };

  address generate_check_simd() {
    StubCodeMark mark(this, "VM_Version", "check_simd");
    address start = __ pc();

    __ vcnt(Stemp, Stemp);
    __ mov(R0, 1);
    __ bx(LR);

    return start;
  };
};

#undef __


extern "C" address check_vfp3_32_fault_instr;
extern "C" address check_vfp_fault_instr;
extern "C" address check_simd_fault_instr;

void VM_Version::initialize() {
  ResourceMark rm;

  // Making this stub must be FIRST use of assembler
  const int stub_size = 128;
  BufferBlob* stub_blob = BufferBlob::create("get_cpu_info", stub_size);
  if (stub_blob == NULL) {
    vm_exit_during_initialization("Unable to allocate get_cpu_info stub");
  }

  CodeBuffer c(stub_blob);
  VM_Version_StubGenerator g(&c);
  address get_cpu_info_pc = g.generate_get_cpu_info();
  get_cpu_info_t get_cpu_info = CAST_TO_FN_PTR(get_cpu_info_t, get_cpu_info_pc);

  int pc_adjustment = get_cpu_info();

  VM_Version::_stored_pc_adjustment = pc_adjustment;

#ifndef __SOFTFP__
  address check_vfp_pc = g.generate_check_vfp();
  check_vfp_t check_vfp = CAST_TO_FN_PTR(check_vfp_t, check_vfp_pc);

  check_vfp_fault_instr = (address)check_vfp;
  double dummy;
  if (check_vfp(&dummy)) {
    _features |= vfp_m;
  }

#ifdef COMPILER2
  if (has_vfp()) {
    address check_vfp3_32_pc = g.generate_check_vfp3_32();
    check_vfp_t check_vfp3_32 = CAST_TO_FN_PTR(check_vfp_t, check_vfp3_32_pc);
    check_vfp3_32_fault_instr = (address)check_vfp3_32;
    double dummy;
    if (check_vfp3_32(&dummy)) {
      _features |= vfp3_32_m;
    }

    address check_simd_pc =g.generate_check_simd();
    check_simd_t check_simd = CAST_TO_FN_PTR(check_simd_t, check_simd_pc);
    check_simd_fault_instr = (address)check_simd;
    if (check_simd()) {
      _features |= simd_m;
    }
  }
#endif
#endif


  if (UseAESIntrinsics && !FLAG_IS_DEFAULT(UseAESIntrinsics)) {
    warning("AES intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseAESIntrinsics, false);
  }

  if (UseAES && !FLAG_IS_DEFAULT(UseAES)) {
    warning("AES instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseAES, false);
  }

  if (UseAESCTRIntrinsics) {
    warning("AES/CTR intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseAESCTRIntrinsics, false);
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

  get_os_cpu_info();

  _kuser_helper_version = *(int*)KUSER_HELPER_VERSION_ADDR;

#ifdef COMPILER2
  // C2 is only supported on v7+ VFP at this time
  if (_arm_arch < 7 || !has_vfp()) {
    vm_exit_during_initialization("Server VM is only supported on ARMv7+ VFP");
  }
#endif

  // armv7 has the ldrexd instruction that can be used to implement cx8
  // armv5 with linux >= 3.1 can use kernel helper routine
  _supports_cx8 = (supports_ldrexd() || supports_kuser_cmpxchg64());
  // ARM doesn't have special instructions for these but ldrex/ldrexd
  // enable shorter instruction sequences that the ones based on cas.
  _supports_atomic_getset4 = supports_ldrex();
  _supports_atomic_getadd4 = supports_ldrex();
  _supports_atomic_getset8 = supports_ldrexd();
  _supports_atomic_getadd8 = supports_ldrexd();

#ifdef COMPILER2
  assert(_supports_cx8 && _supports_atomic_getset4 && _supports_atomic_getadd4
         && _supports_atomic_getset8 && _supports_atomic_getadd8, "C2: atomic operations must be supported");
#endif
  char buf[512];
  jio_snprintf(buf, sizeof(buf), "(ARMv%d)%s%s%s",
               _arm_arch,
               (has_vfp() ? ", vfp" : ""),
               (has_vfp3_32() ? ", vfp3-32" : ""),
               (has_simd() ? ", simd" : ""));

  // buf is started with ", " or is empty
  _features_string = os::strdup(buf);

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
  get_os_cpu_info();
  // The cost of CAS on uniprocessor ARM v6 and later is low compared to the
  // overhead related to slightly longer Biased Locking execution path.
  // Testing shows no improvement when running with Biased Locking enabled
  // on an ARMv6 and higher uniprocessor systems.  The situation is different on
  // ARMv5 and MP systems.
  //
  // Therefore the Biased Locking is enabled on ARMv5 and ARM MP only.
  //
  return (!os::is_MP() && (arm_arch() > 5)) ? false : true;
}

#define EXP

// Temporary override for experimental features
// Copied from Abstract_VM_Version
const char* VM_Version::vm_info_string() {
  switch (Arguments::mode()) {
    case Arguments::_int:
      return UseSharedSpaces ? "interpreted mode, sharing" EXP : "interpreted mode" EXP;
    case Arguments::_mixed:
      return UseSharedSpaces ? "mixed mode, sharing" EXP    :  "mixed mode" EXP;
    case Arguments::_comp:
      return UseSharedSpaces ? "compiled mode, sharing" EXP   : "compiled mode" EXP;
  };
  ShouldNotReachHere();
  return "";
}
