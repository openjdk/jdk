/*
 * Copyright 1997-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_vm_version_x86_32.cpp.incl"


int VM_Version::_cpu;
int VM_Version::_model;
int VM_Version::_stepping;
int VM_Version::_cpuFeatures;
const char*           VM_Version::_features_str = "";
VM_Version::CpuidInfo VM_Version::_cpuid_info   = { 0, };

static BufferBlob* stub_blob;
static const int stub_size = 300;

extern "C" {
  typedef void (*getPsrInfo_stub_t)(void*);
}
static getPsrInfo_stub_t getPsrInfo_stub = NULL;


class VM_Version_StubGenerator: public StubCodeGenerator {
 public:

  VM_Version_StubGenerator(CodeBuffer *c) : StubCodeGenerator(c) {}

  address generate_getPsrInfo() {
    // Flags to test CPU type.
    const uint32_t EFL_AC           = 0x40000;
    const uint32_t EFL_ID           = 0x200000;
    // Values for when we don't have a CPUID instruction.
    const int      CPU_FAMILY_SHIFT = 8;
    const uint32_t CPU_FAMILY_386   = (3 << CPU_FAMILY_SHIFT);
    const uint32_t CPU_FAMILY_486   = (4 << CPU_FAMILY_SHIFT);

    Label detect_486, cpu486, detect_586, std_cpuid1;
    Label ext_cpuid1, ext_cpuid5, done;

    StubCodeMark mark(this, "VM_Version", "getPsrInfo_stub");
#   define __ _masm->

    address start = __ pc();

    //
    // void getPsrInfo(VM_Version::CpuidInfo* cpuid_info);
    //
    __ pushl(rbp);
    __ movl(rbp, Address(rsp, 8)); // cpuid_info address
    __ pushl(rbx);
    __ pushl(rsi);
    __ pushfd();                // preserve rbx, and flags
    __ popl(rax);
    __ pushl(rax);
    __ movl(rcx, rax);
    //
    // if we are unable to change the AC flag, we have a 386
    //
    __ xorl(rax, EFL_AC);
    __ pushl(rax);
    __ popfd();
    __ pushfd();
    __ popl(rax);
    __ cmpl(rax, rcx);
    __ jccb(Assembler::notEqual, detect_486);

    __ movl(rax, CPU_FAMILY_386);
    __ movl(Address(rbp, in_bytes(VM_Version::std_cpuid1_offset())), rax);
    __ jmp(done);

    //
    // If we are unable to change the ID flag, we have a 486 which does
    // not support the "cpuid" instruction.
    //
    __ bind(detect_486);
    __ movl(rax, rcx);
    __ xorl(rax, EFL_ID);
    __ pushl(rax);
    __ popfd();
    __ pushfd();
    __ popl(rax);
    __ cmpl(rcx, rax);
    __ jccb(Assembler::notEqual, detect_586);

    __ bind(cpu486);
    __ movl(rax, CPU_FAMILY_486);
    __ movl(Address(rbp, in_bytes(VM_Version::std_cpuid1_offset())), rax);
    __ jmp(done);

    //
    // at this point, we have a chip which supports the "cpuid" instruction
    //
    __ bind(detect_586);
    __ xorl(rax, rax);
    __ cpuid();
    __ orl(rax, rax);
    __ jcc(Assembler::equal, cpu486);   // if cpuid doesn't support an input
                                        // value of at least 1, we give up and
                                        // assume a 486
    __ leal(rsi, Address(rbp, in_bytes(VM_Version::std_cpuid0_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi,12), rdx);

    __ cmpl(rax, 3);     // Is cpuid(0x4) supported?
    __ jccb(Assembler::belowEqual, std_cpuid1);

    //
    // cpuid(0x4) Deterministic cache params
    //
    __ movl(rax, 4);     // and rcx already set to 0x0
    __ xorl(rcx, rcx);
    __ cpuid();
    __ pushl(rax);
    __ andl(rax, 0x1f);  // Determine if valid cache parameters used
    __ orl(rax, rax);    // rax,[4:0] == 0 indicates invalid cache
    __ popl(rax);
    __ jccb(Assembler::equal, std_cpuid1);

    __ leal(rsi, Address(rbp, in_bytes(VM_Version::dcp_cpuid4_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi,12), rdx);

    //
    // Standard cpuid(0x1)
    //
    __ bind(std_cpuid1);
    __ movl(rax, 1);
    __ cpuid();
    __ leal(rsi, Address(rbp, in_bytes(VM_Version::std_cpuid1_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi,12), rdx);

    __ movl(rax, 0x80000000);
    __ cpuid();
    __ cmpl(rax, 0x80000000);     // Is cpuid(0x80000001) supported?
    __ jcc(Assembler::belowEqual, done);
    __ cmpl(rax, 0x80000004);     // Is cpuid(0x80000005) supported?
    __ jccb(Assembler::belowEqual, ext_cpuid1);
    __ cmpl(rax, 0x80000007);     // Is cpuid(0x80000008) supported?
    __ jccb(Assembler::belowEqual, ext_cpuid5);
    //
    // Extended cpuid(0x80000008)
    //
    __ movl(rax, 0x80000008);
    __ cpuid();
    __ leal(rsi, Address(rbp, in_bytes(VM_Version::ext_cpuid8_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi,12), rdx);

    //
    // Extended cpuid(0x80000005)
    //
    __ bind(ext_cpuid5);
    __ movl(rax, 0x80000005);
    __ cpuid();
    __ leal(rsi, Address(rbp, in_bytes(VM_Version::ext_cpuid5_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi,12), rdx);

    //
    // Extended cpuid(0x80000001)
    //
    __ bind(ext_cpuid1);
    __ movl(rax, 0x80000001);
    __ cpuid();
    __ leal(rsi, Address(rbp, in_bytes(VM_Version::ext_cpuid1_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi,12), rdx);

    //
    // return
    //
    __ bind(done);
    __ popfd();
    __ popl(rsi);
    __ popl(rbx);
    __ popl(rbp);
    __ ret(0);

#   undef __

    return start;
  };
};


void VM_Version::get_processor_features() {

  _cpu = 4; // 486 by default
  _model = 0;
  _stepping = 0;
  _cpuFeatures = 0;
  _logical_processors_per_package = 1;
  if (!Use486InstrsOnly) {
    // Get raw processor info
    getPsrInfo_stub(&_cpuid_info);
    assert_is_initialized();
    _cpu = extended_cpu_family();
    _model = extended_cpu_model();
    _stepping = cpu_stepping();
    if (cpu_family() > 4) { // it supports CPUID
      _cpuFeatures = feature_flags();
      // Logical processors are only available on P4s and above,
      // and only if hyperthreading is available.
      _logical_processors_per_package = logical_processor_count();
    }
  }
  _supports_cx8 = supports_cmpxchg8();
  // if the OS doesn't support SSE, we can't use this feature even if the HW does
  if( !os::supports_sse())
    _cpuFeatures &= ~(CPU_SSE|CPU_SSE2|CPU_SSE3|CPU_SSSE3|CPU_SSE4|CPU_SSE4A);
  if (UseSSE < 4)
    _cpuFeatures &= ~CPU_SSE4;
  if (UseSSE < 3) {
    _cpuFeatures &= ~CPU_SSE3;
    _cpuFeatures &= ~CPU_SSSE3;
    _cpuFeatures &= ~CPU_SSE4A;
  }
  if (UseSSE < 2)
    _cpuFeatures &= ~CPU_SSE2;
  if (UseSSE < 1)
    _cpuFeatures &= ~CPU_SSE;

  if (logical_processors_per_package() == 1) {
    // HT processor could be installed on a system which doesn't support HT.
    _cpuFeatures &= ~CPU_HT;
  }

  char buf[256];
  jio_snprintf(buf, sizeof(buf), "(%u cores per cpu, %u threads per core) family %d model %d stepping %d%s%s%s%s%s%s%s%s%s%s%s%s%s%s",
               cores_per_cpu(), threads_per_core(),
               cpu_family(), _model, _stepping,
               (supports_cmov() ? ", cmov" : ""),
               (supports_cmpxchg8() ? ", cx8" : ""),
               (supports_fxsr() ? ", fxsr" : ""),
               (supports_mmx()  ? ", mmx"  : ""),
               (supports_sse()  ? ", sse"  : ""),
               (supports_sse2() ? ", sse2" : ""),
               (supports_sse3() ? ", sse3" : ""),
               (supports_ssse3()? ", ssse3": ""),
               (supports_sse4() ? ", sse4" : ""),
               (supports_mmx_ext() ? ", mmxext" : ""),
               (supports_3dnow()   ? ", 3dnow"  : ""),
               (supports_3dnow2()  ? ", 3dnowext" : ""),
               (supports_sse4a()   ? ", sse4a": ""),
               (supports_ht() ? ", ht": ""));
  _features_str = strdup(buf);

  // UseSSE is set to the smaller of what hardware supports and what
  // the command line requires.  I.e., you cannot set UseSSE to 2 on
  // older Pentiums which do not support it.
  if( UseSSE > 4 ) UseSSE=4;
  if( UseSSE < 0 ) UseSSE=0;
  if( !supports_sse4() ) // Drop to 3 if no SSE4 support
    UseSSE = MIN2((intx)3,UseSSE);
  if( !supports_sse3() ) // Drop to 2 if no SSE3 support
    UseSSE = MIN2((intx)2,UseSSE);
  if( !supports_sse2() ) // Drop to 1 if no SSE2 support
    UseSSE = MIN2((intx)1,UseSSE);
  if( !supports_sse () ) // Drop to 0 if no SSE  support
    UseSSE = 0;

  // On new cpus instructions which update whole XMM register should be used
  // to prevent partial register stall due to dependencies on high half.
  //
  // UseXmmLoadAndClearUpper == true  --> movsd(xmm, mem)
  // UseXmmLoadAndClearUpper == false --> movlpd(xmm, mem)
  // UseXmmRegToRegMoveAll == true  --> movaps(xmm, xmm), movapd(xmm, xmm).
  // UseXmmRegToRegMoveAll == false --> movss(xmm, xmm),  movsd(xmm, xmm).

  if( is_amd() ) { // AMD cpus specific settings
    if( supports_sse2() && FLAG_IS_DEFAULT(UseAddressNop) ) {
      // Use it on new AMD cpus starting from Opteron.
      UseAddressNop = true;
    }
    if( FLAG_IS_DEFAULT(UseXmmLoadAndClearUpper) ) {
      if( supports_sse4a() ) {
        UseXmmLoadAndClearUpper = true; // use movsd only on '10h' Opteron
      } else {
        UseXmmLoadAndClearUpper = false;
      }
    }
    if( FLAG_IS_DEFAULT(UseXmmRegToRegMoveAll) ) {
      if( supports_sse4a() ) {
        UseXmmRegToRegMoveAll = true; // use movaps, movapd only on '10h'
      } else {
        UseXmmRegToRegMoveAll = false;
      }
    }
    if( FLAG_IS_DEFAULT(UseXmmI2F) ) {
      if( supports_sse4a() ) {
        UseXmmI2F = true;
      } else {
        UseXmmI2F = false;
      }
    }
    if( FLAG_IS_DEFAULT(UseXmmI2D) ) {
      if( supports_sse4a() ) {
        UseXmmI2D = true;
      } else {
        UseXmmI2D = false;
      }
    }
  }

  if( is_intel() ) { // Intel cpus specific settings
    if( FLAG_IS_DEFAULT(UseStoreImmI16) ) {
      UseStoreImmI16 = false; // don't use it on Intel cpus
    }
    if( cpu_family() == 6 || cpu_family() == 15 ) {
      if( FLAG_IS_DEFAULT(UseAddressNop) ) {
        // Use it on all Intel cpus starting from PentiumPro
        UseAddressNop = true;
      }
    }
    if( FLAG_IS_DEFAULT(UseXmmLoadAndClearUpper) ) {
      UseXmmLoadAndClearUpper = true; // use movsd on all Intel cpus
    }
    if( FLAG_IS_DEFAULT(UseXmmRegToRegMoveAll) ) {
      if( supports_sse3() ) {
        UseXmmRegToRegMoveAll = true; // use movaps, movapd on new Intel cpus
      } else {
        UseXmmRegToRegMoveAll = false;
      }
    }
    if( cpu_family() == 6 && supports_sse3() ) { // New Intel cpus
#ifdef COMPILER2
      if( FLAG_IS_DEFAULT(MaxLoopPad) ) {
        // For new Intel cpus do the next optimization:
        // don't align the beginning of a loop if there are enough instructions
        // left (NumberOfLoopInstrToAlign defined in c2_globals.hpp)
        // in current fetch line (OptoLoopAlignment) or the padding
        // is big (> MaxLoopPad).
        // Set MaxLoopPad to 11 for new Intel cpus to reduce number of
        // generated NOP instructions. 11 is the largest size of one
        // address NOP instruction '0F 1F' (see Assembler::nop(i)).
        MaxLoopPad = 11;
      }
#endif // COMPILER2
    }
  }

  assert(0 <= ReadPrefetchInstr && ReadPrefetchInstr <= 3, "invalid value");
  assert(0 <= AllocatePrefetchInstr && AllocatePrefetchInstr <= 3, "invalid value");

  // set valid Prefetch instruction
  if( ReadPrefetchInstr < 0 ) ReadPrefetchInstr = 0;
  if( ReadPrefetchInstr > 3 ) ReadPrefetchInstr = 3;
  if( ReadPrefetchInstr == 3 && !supports_3dnow() ) ReadPrefetchInstr = 0;
  if( !supports_sse() && supports_3dnow() ) ReadPrefetchInstr = 3;

  if( AllocatePrefetchInstr < 0 ) AllocatePrefetchInstr = 0;
  if( AllocatePrefetchInstr > 3 ) AllocatePrefetchInstr = 3;
  if( AllocatePrefetchInstr == 3 && !supports_3dnow() ) AllocatePrefetchInstr=0;
  if( !supports_sse() && supports_3dnow() ) AllocatePrefetchInstr = 3;

  // Allocation prefetch settings
  intx cache_line_size = L1_data_cache_line_size();
  if( cache_line_size > AllocatePrefetchStepSize )
    AllocatePrefetchStepSize = cache_line_size;
  if( FLAG_IS_DEFAULT(AllocatePrefetchLines) )
    AllocatePrefetchLines = 3; // Optimistic value
  assert(AllocatePrefetchLines > 0, "invalid value");
  if( AllocatePrefetchLines < 1 ) // set valid value in product VM
    AllocatePrefetchLines = 1; // Conservative value

  AllocatePrefetchDistance = allocate_prefetch_distance();
  AllocatePrefetchStyle    = allocate_prefetch_style();

  if( AllocatePrefetchStyle == 2 && is_intel() &&
      cpu_family() == 6 && supports_sse3() ) { // watermark prefetching on Core
    AllocatePrefetchDistance = 320;
  }
  assert(AllocatePrefetchDistance % AllocatePrefetchStepSize == 0, "invalid value");

#ifndef PRODUCT
  if (PrintMiscellaneous && Verbose) {
    tty->print_cr("Logical CPUs per package: %u",
                  logical_processors_per_package());
    tty->print_cr("UseSSE=%d",UseSSE);
    tty->print("Allocation: ");
    if (AllocatePrefetchStyle <= 0 || UseSSE == 0 && !supports_3dnow()) {
      tty->print_cr("no prefetching");
    } else {
      if (UseSSE == 0 && supports_3dnow()) {
        tty->print("PREFETCHW");
      } else if (UseSSE >= 1) {
        if (AllocatePrefetchInstr == 0) {
          tty->print("PREFETCHNTA");
        } else if (AllocatePrefetchInstr == 1) {
          tty->print("PREFETCHT0");
        } else if (AllocatePrefetchInstr == 2) {
          tty->print("PREFETCHT2");
        } else if (AllocatePrefetchInstr == 3) {
          tty->print("PREFETCHW");
        }
      }
      if (AllocatePrefetchLines > 1) {
        tty->print_cr(" %d, %d lines with step %d bytes", AllocatePrefetchDistance, AllocatePrefetchLines, AllocatePrefetchStepSize);
      } else {
        tty->print_cr(" %d, one line", AllocatePrefetchDistance);
      }
    }
  }
#endif // !PRODUCT
}

void VM_Version::initialize() {
  ResourceMark rm;
  // Making this stub must be FIRST use of assembler

  stub_blob = BufferBlob::create("getPsrInfo_stub", stub_size);
  if (stub_blob == NULL) {
    vm_exit_during_initialization("Unable to allocate getPsrInfo_stub");
  }
  CodeBuffer c(stub_blob->instructions_begin(),
               stub_blob->instructions_size());
  VM_Version_StubGenerator g(&c);
  getPsrInfo_stub = CAST_TO_FN_PTR(getPsrInfo_stub_t,
                                   g.generate_getPsrInfo());

  get_processor_features();
}
