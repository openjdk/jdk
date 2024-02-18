/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/vmIntrinsics.hpp"
#include "code/codeBlob.hpp"
#include "compiler/compilerDefinitions.inline.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/virtualizationSupport.hpp"

int VM_Version::_cpu;
int VM_Version::_model;
int VM_Version::_stepping;
bool VM_Version::_has_intel_jcc_erratum;
VM_Version::CpuidInfo VM_Version::_cpuid_info = { 0, };

#define DECLARE_CPU_FEATURE_NAME(id, name, bit) name,
const char* VM_Version::_features_names[] = { CPU_FEATURE_FLAGS(DECLARE_CPU_FEATURE_NAME)};
#undef DECLARE_CPU_FEATURE_FLAG

// Address of instruction which causes SEGV
address VM_Version::_cpuinfo_segv_addr = 0;
// Address of instruction after the one which causes SEGV
address VM_Version::_cpuinfo_cont_addr = 0;

static BufferBlob* stub_blob;
static const int stub_size = 2000;

extern "C" {
  typedef void (*get_cpu_info_stub_t)(void*);
  typedef void (*detect_virt_stub_t)(uint32_t, uint32_t*);
}
static get_cpu_info_stub_t get_cpu_info_stub = nullptr;
static detect_virt_stub_t detect_virt_stub = nullptr;

#ifdef _LP64

bool VM_Version::supports_clflush() {
  // clflush should always be available on x86_64
  // if not we are in real trouble because we rely on it
  // to flush the code cache.
  // Unfortunately, Assembler::clflush is currently called as part
  // of generation of the code cache flush routine. This happens
  // under Universe::init before the processor features are set
  // up. Assembler::flush calls this routine to check that clflush
  // is allowed. So, we give the caller a free pass if Universe init
  // is still in progress.
  assert ((!Universe::is_fully_initialized() || (_features & CPU_FLUSH) != 0), "clflush should be available");
  return true;
}
#endif

#define CPUID_STANDARD_FN   0x0
#define CPUID_STANDARD_FN_1 0x1
#define CPUID_STANDARD_FN_4 0x4
#define CPUID_STANDARD_FN_B 0xb

#define CPUID_EXTENDED_FN   0x80000000
#define CPUID_EXTENDED_FN_1 0x80000001
#define CPUID_EXTENDED_FN_2 0x80000002
#define CPUID_EXTENDED_FN_3 0x80000003
#define CPUID_EXTENDED_FN_4 0x80000004
#define CPUID_EXTENDED_FN_7 0x80000007
#define CPUID_EXTENDED_FN_8 0x80000008

class VM_Version_StubGenerator: public StubCodeGenerator {
 public:

  VM_Version_StubGenerator(CodeBuffer *c) : StubCodeGenerator(c) {}

  address generate_get_cpu_info() {
    // Flags to test CPU type.
    const uint32_t HS_EFL_AC = 0x40000;
    const uint32_t HS_EFL_ID = 0x200000;
    // Values for when we don't have a CPUID instruction.
    const int      CPU_FAMILY_SHIFT = 8;
    const uint32_t CPU_FAMILY_386 = (3 << CPU_FAMILY_SHIFT);
    const uint32_t CPU_FAMILY_486 = (4 << CPU_FAMILY_SHIFT);
    bool use_evex = FLAG_IS_DEFAULT(UseAVX) || (UseAVX > 2);

    Label detect_486, cpu486, detect_586, std_cpuid1, std_cpuid4;
    Label sef_cpuid, ext_cpuid, ext_cpuid1, ext_cpuid5, ext_cpuid7, ext_cpuid8, done, wrapup;
    Label legacy_setup, save_restore_except, legacy_save_restore, start_simd_check;

    StubCodeMark mark(this, "VM_Version", "get_cpu_info_stub");
#   define __ _masm->

    address start = __ pc();

    //
    // void get_cpu_info(VM_Version::CpuidInfo* cpuid_info);
    //
    // LP64: rcx and rdx are first and second argument registers on windows

    __ push(rbp);
#ifdef _LP64
    __ mov(rbp, c_rarg0); // cpuid_info address
#else
    __ movptr(rbp, Address(rsp, 8)); // cpuid_info address
#endif
    __ push(rbx);
    __ push(rsi);
    __ pushf();          // preserve rbx, and flags
    __ pop(rax);
    __ push(rax);
    __ mov(rcx, rax);
    //
    // if we are unable to change the AC flag, we have a 386
    //
    __ xorl(rax, HS_EFL_AC);
    __ push(rax);
    __ popf();
    __ pushf();
    __ pop(rax);
    __ cmpptr(rax, rcx);
    __ jccb(Assembler::notEqual, detect_486);

    __ movl(rax, CPU_FAMILY_386);
    __ movl(Address(rbp, in_bytes(VM_Version::std_cpuid1_offset())), rax);
    __ jmp(done);

    //
    // If we are unable to change the ID flag, we have a 486 which does
    // not support the "cpuid" instruction.
    //
    __ bind(detect_486);
    __ mov(rax, rcx);
    __ xorl(rax, HS_EFL_ID);
    __ push(rax);
    __ popf();
    __ pushf();
    __ pop(rax);
    __ cmpptr(rcx, rax);
    __ jccb(Assembler::notEqual, detect_586);

    __ bind(cpu486);
    __ movl(rax, CPU_FAMILY_486);
    __ movl(Address(rbp, in_bytes(VM_Version::std_cpuid1_offset())), rax);
    __ jmp(done);

    //
    // At this point, we have a chip which supports the "cpuid" instruction
    //
    __ bind(detect_586);
    __ xorl(rax, rax);
    __ cpuid();
    __ orl(rax, rax);
    __ jcc(Assembler::equal, cpu486);   // if cpuid doesn't support an input
                                        // value of at least 1, we give up and
                                        // assume a 486
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::std_cpuid0_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi,12), rdx);

    __ cmpl(rax, 0xa);                  // Is cpuid(0xB) supported?
    __ jccb(Assembler::belowEqual, std_cpuid4);

    //
    // cpuid(0xB) Processor Topology
    //
    __ movl(rax, 0xb);
    __ xorl(rcx, rcx);   // Threads level
    __ cpuid();

    __ lea(rsi, Address(rbp, in_bytes(VM_Version::tpl_cpuidB0_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi,12), rdx);

    __ movl(rax, 0xb);
    __ movl(rcx, 1);     // Cores level
    __ cpuid();
    __ push(rax);
    __ andl(rax, 0x1f);  // Determine if valid topology level
    __ orl(rax, rbx);    // eax[4:0] | ebx[0:15] == 0 indicates invalid level
    __ andl(rax, 0xffff);
    __ pop(rax);
    __ jccb(Assembler::equal, std_cpuid4);

    __ lea(rsi, Address(rbp, in_bytes(VM_Version::tpl_cpuidB1_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi,12), rdx);

    __ movl(rax, 0xb);
    __ movl(rcx, 2);     // Packages level
    __ cpuid();
    __ push(rax);
    __ andl(rax, 0x1f);  // Determine if valid topology level
    __ orl(rax, rbx);    // eax[4:0] | ebx[0:15] == 0 indicates invalid level
    __ andl(rax, 0xffff);
    __ pop(rax);
    __ jccb(Assembler::equal, std_cpuid4);

    __ lea(rsi, Address(rbp, in_bytes(VM_Version::tpl_cpuidB2_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi,12), rdx);

    //
    // cpuid(0x4) Deterministic cache params
    //
    __ bind(std_cpuid4);
    __ movl(rax, 4);
    __ cmpl(rax, Address(rbp, in_bytes(VM_Version::std_cpuid0_offset()))); // Is cpuid(0x4) supported?
    __ jccb(Assembler::greater, std_cpuid1);

    __ xorl(rcx, rcx);   // L1 cache
    __ cpuid();
    __ push(rax);
    __ andl(rax, 0x1f);  // Determine if valid cache parameters used
    __ orl(rax, rax);    // eax[4:0] == 0 indicates invalid cache
    __ pop(rax);
    __ jccb(Assembler::equal, std_cpuid1);

    __ lea(rsi, Address(rbp, in_bytes(VM_Version::dcp_cpuid4_offset())));
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
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::std_cpuid1_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi,12), rdx);

    //
    // Check if OS has enabled XGETBV instruction to access XCR0
    // (OSXSAVE feature flag) and CPU supports AVX
    //
    __ andl(rcx, 0x18000000); // cpuid1 bits osxsave | avx
    __ cmpl(rcx, 0x18000000);
    __ jccb(Assembler::notEqual, sef_cpuid); // jump if AVX is not supported

    //
    // XCR0, XFEATURE_ENABLED_MASK register
    //
    __ xorl(rcx, rcx);   // zero for XCR0 register
    __ xgetbv();
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::xem_xcr0_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rdx);

    //
    // cpuid(0x7) Structured Extended Features
    //
    __ bind(sef_cpuid);
    __ movl(rax, 7);
    __ cmpl(rax, Address(rbp, in_bytes(VM_Version::std_cpuid0_offset()))); // Is cpuid(0x7) supported?
    __ jccb(Assembler::greater, ext_cpuid);

    __ xorl(rcx, rcx);
    __ cpuid();
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::sef_cpuid7_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi, 12), rdx);

    //
    // Extended cpuid(0x80000000)
    //
    __ bind(ext_cpuid);
    __ movl(rax, 0x80000000);
    __ cpuid();
    __ cmpl(rax, 0x80000000);     // Is cpuid(0x80000001) supported?
    __ jcc(Assembler::belowEqual, done);
    __ cmpl(rax, 0x80000004);     // Is cpuid(0x80000005) supported?
    __ jcc(Assembler::belowEqual, ext_cpuid1);
    __ cmpl(rax, 0x80000006);     // Is cpuid(0x80000007) supported?
    __ jccb(Assembler::belowEqual, ext_cpuid5);
    __ cmpl(rax, 0x80000007);     // Is cpuid(0x80000008) supported?
    __ jccb(Assembler::belowEqual, ext_cpuid7);
    __ cmpl(rax, 0x80000008);     // Is cpuid(0x80000009 and above) supported?
    __ jccb(Assembler::belowEqual, ext_cpuid8);
    __ cmpl(rax, 0x8000001E);     // Is cpuid(0x8000001E) supported?
    __ jccb(Assembler::below, ext_cpuid8);
    //
    // Extended cpuid(0x8000001E)
    //
    __ movl(rax, 0x8000001E);
    __ cpuid();
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::ext_cpuid1E_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi,12), rdx);

    //
    // Extended cpuid(0x80000008)
    //
    __ bind(ext_cpuid8);
    __ movl(rax, 0x80000008);
    __ cpuid();
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::ext_cpuid8_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi,12), rdx);

    //
    // Extended cpuid(0x80000007)
    //
    __ bind(ext_cpuid7);
    __ movl(rax, 0x80000007);
    __ cpuid();
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::ext_cpuid7_offset())));
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
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::ext_cpuid5_offset())));
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
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::ext_cpuid1_offset())));
    __ movl(Address(rsi, 0), rax);
    __ movl(Address(rsi, 4), rbx);
    __ movl(Address(rsi, 8), rcx);
    __ movl(Address(rsi,12), rdx);

    //
    // Check if OS has enabled XGETBV instruction to access XCR0
    // (OSXSAVE feature flag) and CPU supports AVX
    //
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::std_cpuid1_offset())));
    __ movl(rcx, 0x18000000); // cpuid1 bits osxsave | avx
    __ andl(rcx, Address(rsi, 8)); // cpuid1 bits osxsave | avx
    __ cmpl(rcx, 0x18000000);
    __ jccb(Assembler::notEqual, done); // jump if AVX is not supported

    __ movl(rax, 0x6);
    __ andl(rax, Address(rbp, in_bytes(VM_Version::xem_xcr0_offset()))); // xcr0 bits sse | ymm
    __ cmpl(rax, 0x6);
    __ jccb(Assembler::equal, start_simd_check); // return if AVX is not supported

    // we need to bridge farther than imm8, so we use this island as a thunk
    __ bind(done);
    __ jmp(wrapup);

    __ bind(start_simd_check);
    //
    // Some OSs have a bug when upper 128/256bits of YMM/ZMM
    // registers are not restored after a signal processing.
    // Generate SEGV here (reference through null)
    // and check upper YMM/ZMM bits after it.
    //
    int saved_useavx = UseAVX;
    int saved_usesse = UseSSE;

    // If UseAVX is uninitialized or is set by the user to include EVEX
    if (use_evex) {
      // check _cpuid_info.sef_cpuid7_ebx.bits.avx512f
      __ lea(rsi, Address(rbp, in_bytes(VM_Version::sef_cpuid7_offset())));
      __ movl(rax, 0x10000);
      __ andl(rax, Address(rsi, 4)); // xcr0 bits sse | ymm
      __ cmpl(rax, 0x10000);
      __ jccb(Assembler::notEqual, legacy_setup); // jump if EVEX is not supported
      // check _cpuid_info.xem_xcr0_eax.bits.opmask
      // check _cpuid_info.xem_xcr0_eax.bits.zmm512
      // check _cpuid_info.xem_xcr0_eax.bits.zmm32
      __ movl(rax, 0xE0);
      __ andl(rax, Address(rbp, in_bytes(VM_Version::xem_xcr0_offset()))); // xcr0 bits sse | ymm
      __ cmpl(rax, 0xE0);
      __ jccb(Assembler::notEqual, legacy_setup); // jump if EVEX is not supported

      if (FLAG_IS_DEFAULT(UseAVX)) {
        __ lea(rsi, Address(rbp, in_bytes(VM_Version::std_cpuid1_offset())));
        __ movl(rax, Address(rsi, 0));
        __ cmpl(rax, 0x50654);              // If it is Skylake
        __ jcc(Assembler::equal, legacy_setup);
      }
      // EVEX setup: run in lowest evex mode
      VM_Version::set_evex_cpuFeatures(); // Enable temporary to pass asserts
      UseAVX = 3;
      UseSSE = 2;
#ifdef _WINDOWS
      // xmm5-xmm15 are not preserved by caller on windows
      // https://msdn.microsoft.com/en-us/library/9z1stfyw.aspx
      __ subptr(rsp, 64);
      __ evmovdqul(Address(rsp, 0), xmm7, Assembler::AVX_512bit);
#ifdef _LP64
      __ subptr(rsp, 64);
      __ evmovdqul(Address(rsp, 0), xmm8, Assembler::AVX_512bit);
      __ subptr(rsp, 64);
      __ evmovdqul(Address(rsp, 0), xmm31, Assembler::AVX_512bit);
#endif // _LP64
#endif // _WINDOWS

      // load value into all 64 bytes of zmm7 register
      __ movl(rcx, VM_Version::ymm_test_value());
      __ movdl(xmm0, rcx);
      __ vpbroadcastd(xmm0, xmm0, Assembler::AVX_512bit);
      __ evmovdqul(xmm7, xmm0, Assembler::AVX_512bit);
#ifdef _LP64
      __ evmovdqul(xmm8, xmm0, Assembler::AVX_512bit);
      __ evmovdqul(xmm31, xmm0, Assembler::AVX_512bit);
#endif
      VM_Version::clean_cpuFeatures();
      __ jmp(save_restore_except);
    }

    __ bind(legacy_setup);
    // AVX setup
    VM_Version::set_avx_cpuFeatures(); // Enable temporary to pass asserts
    UseAVX = 1;
    UseSSE = 2;
#ifdef _WINDOWS
    __ subptr(rsp, 32);
    __ vmovdqu(Address(rsp, 0), xmm7);
#ifdef _LP64
    __ subptr(rsp, 32);
    __ vmovdqu(Address(rsp, 0), xmm8);
    __ subptr(rsp, 32);
    __ vmovdqu(Address(rsp, 0), xmm15);
#endif // _LP64
#endif // _WINDOWS

    // load value into all 32 bytes of ymm7 register
    __ movl(rcx, VM_Version::ymm_test_value());

    __ movdl(xmm0, rcx);
    __ pshufd(xmm0, xmm0, 0x00);
    __ vinsertf128_high(xmm0, xmm0);
    __ vmovdqu(xmm7, xmm0);
#ifdef _LP64
    __ vmovdqu(xmm8, xmm0);
    __ vmovdqu(xmm15, xmm0);
#endif
    VM_Version::clean_cpuFeatures();

    __ bind(save_restore_except);
    __ xorl(rsi, rsi);
    VM_Version::set_cpuinfo_segv_addr(__ pc());
    // Generate SEGV
    __ movl(rax, Address(rsi, 0));

    VM_Version::set_cpuinfo_cont_addr(__ pc());
    // Returns here after signal. Save xmm0 to check it later.

    // If UseAVX is uninitialized or is set by the user to include EVEX
    if (use_evex) {
      // check _cpuid_info.sef_cpuid7_ebx.bits.avx512f
      __ lea(rsi, Address(rbp, in_bytes(VM_Version::sef_cpuid7_offset())));
      __ movl(rax, 0x10000);
      __ andl(rax, Address(rsi, 4));
      __ cmpl(rax, 0x10000);
      __ jcc(Assembler::notEqual, legacy_save_restore);
      // check _cpuid_info.xem_xcr0_eax.bits.opmask
      // check _cpuid_info.xem_xcr0_eax.bits.zmm512
      // check _cpuid_info.xem_xcr0_eax.bits.zmm32
      __ movl(rax, 0xE0);
      __ andl(rax, Address(rbp, in_bytes(VM_Version::xem_xcr0_offset()))); // xcr0 bits sse | ymm
      __ cmpl(rax, 0xE0);
      __ jcc(Assembler::notEqual, legacy_save_restore);

      if (FLAG_IS_DEFAULT(UseAVX)) {
        __ lea(rsi, Address(rbp, in_bytes(VM_Version::std_cpuid1_offset())));
        __ movl(rax, Address(rsi, 0));
        __ cmpl(rax, 0x50654);              // If it is Skylake
        __ jcc(Assembler::equal, legacy_save_restore);
      }
      // EVEX check: run in lowest evex mode
      VM_Version::set_evex_cpuFeatures(); // Enable temporary to pass asserts
      UseAVX = 3;
      UseSSE = 2;
      __ lea(rsi, Address(rbp, in_bytes(VM_Version::zmm_save_offset())));
      __ evmovdqul(Address(rsi, 0), xmm0, Assembler::AVX_512bit);
      __ evmovdqul(Address(rsi, 64), xmm7, Assembler::AVX_512bit);
#ifdef _LP64
      __ evmovdqul(Address(rsi, 128), xmm8, Assembler::AVX_512bit);
      __ evmovdqul(Address(rsi, 192), xmm31, Assembler::AVX_512bit);
#endif

#ifdef _WINDOWS
#ifdef _LP64
      __ evmovdqul(xmm31, Address(rsp, 0), Assembler::AVX_512bit);
      __ addptr(rsp, 64);
      __ evmovdqul(xmm8, Address(rsp, 0), Assembler::AVX_512bit);
      __ addptr(rsp, 64);
#endif // _LP64
      __ evmovdqul(xmm7, Address(rsp, 0), Assembler::AVX_512bit);
      __ addptr(rsp, 64);
#endif // _WINDOWS
      generate_vzeroupper(wrapup);
      VM_Version::clean_cpuFeatures();
      UseAVX = saved_useavx;
      UseSSE = saved_usesse;
      __ jmp(wrapup);
   }

    __ bind(legacy_save_restore);
    // AVX check
    VM_Version::set_avx_cpuFeatures(); // Enable temporary to pass asserts
    UseAVX = 1;
    UseSSE = 2;
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::ymm_save_offset())));
    __ vmovdqu(Address(rsi, 0), xmm0);
    __ vmovdqu(Address(rsi, 32), xmm7);
#ifdef _LP64
    __ vmovdqu(Address(rsi, 64), xmm8);
    __ vmovdqu(Address(rsi, 96), xmm15);
#endif

#ifdef _WINDOWS
#ifdef _LP64
    __ vmovdqu(xmm15, Address(rsp, 0));
    __ addptr(rsp, 32);
    __ vmovdqu(xmm8, Address(rsp, 0));
    __ addptr(rsp, 32);
#endif // _LP64
    __ vmovdqu(xmm7, Address(rsp, 0));
    __ addptr(rsp, 32);
#endif // _WINDOWS
    generate_vzeroupper(wrapup);
    VM_Version::clean_cpuFeatures();
    UseAVX = saved_useavx;
    UseSSE = saved_usesse;

    __ bind(wrapup);
    __ popf();
    __ pop(rsi);
    __ pop(rbx);
    __ pop(rbp);
    __ ret(0);

#   undef __

    return start;
  };
  void generate_vzeroupper(Label& L_wrapup) {
#   define __ _masm->
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::std_cpuid0_offset())));
    __ cmpl(Address(rsi, 4), 0x756e6547);  // 'uneG'
    __ jcc(Assembler::notEqual, L_wrapup);
    __ movl(rcx, 0x0FFF0FF0);
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::std_cpuid1_offset())));
    __ andl(rcx, Address(rsi, 0));
    __ cmpl(rcx, 0x00050670);              // If it is Xeon Phi 3200/5200/7200
    __ jcc(Assembler::equal, L_wrapup);
    __ cmpl(rcx, 0x00080650);              // If it is Future Xeon Phi
    __ jcc(Assembler::equal, L_wrapup);
    // vzeroupper() will use a pre-computed instruction sequence that we
    // can't compute until after we've determined CPU capabilities. Use
    // uncached variant here directly to be able to bootstrap correctly
    __ vzeroupper_uncached();
#   undef __
  }
  address generate_detect_virt() {
    StubCodeMark mark(this, "VM_Version", "detect_virt_stub");
#   define __ _masm->

    address start = __ pc();

    // Evacuate callee-saved registers
    __ push(rbp);
    __ push(rbx);
    __ push(rsi); // for Windows

#ifdef _LP64
    __ mov(rax, c_rarg0); // CPUID leaf
    __ mov(rsi, c_rarg1); // register array address (eax, ebx, ecx, edx)
#else
    __ movptr(rax, Address(rsp, 16)); // CPUID leaf
    __ movptr(rsi, Address(rsp, 20)); // register array address
#endif

    __ cpuid();

    // Store result to register array
    __ movl(Address(rsi,  0), rax);
    __ movl(Address(rsi,  4), rbx);
    __ movl(Address(rsi,  8), rcx);
    __ movl(Address(rsi, 12), rdx);

    // Epilogue
    __ pop(rsi);
    __ pop(rbx);
    __ pop(rbp);
    __ ret(0);

#   undef __

    return start;
  };


  address generate_getCPUIDBrandString(void) {
    // Flags to test CPU type.
    const uint32_t HS_EFL_AC           = 0x40000;
    const uint32_t HS_EFL_ID           = 0x200000;
    // Values for when we don't have a CPUID instruction.
    const int      CPU_FAMILY_SHIFT = 8;
    const uint32_t CPU_FAMILY_386   = (3 << CPU_FAMILY_SHIFT);
    const uint32_t CPU_FAMILY_486   = (4 << CPU_FAMILY_SHIFT);

    Label detect_486, cpu486, detect_586, done, ext_cpuid;

    StubCodeMark mark(this, "VM_Version", "getCPUIDNameInfo_stub");
#   define __ _masm->

    address start = __ pc();

    //
    // void getCPUIDBrandString(VM_Version::CpuidInfo* cpuid_info);
    //
    // LP64: rcx and rdx are first and second argument registers on windows

    __ push(rbp);
#ifdef _LP64
    __ mov(rbp, c_rarg0); // cpuid_info address
#else
    __ movptr(rbp, Address(rsp, 8)); // cpuid_info address
#endif
    __ push(rbx);
    __ push(rsi);
    __ pushf();          // preserve rbx, and flags
    __ pop(rax);
    __ push(rax);
    __ mov(rcx, rax);
    //
    // if we are unable to change the AC flag, we have a 386
    //
    __ xorl(rax, HS_EFL_AC);
    __ push(rax);
    __ popf();
    __ pushf();
    __ pop(rax);
    __ cmpptr(rax, rcx);
    __ jccb(Assembler::notEqual, detect_486);

    __ movl(rax, CPU_FAMILY_386);
    __ jmp(done);

    //
    // If we are unable to change the ID flag, we have a 486 which does
    // not support the "cpuid" instruction.
    //
    __ bind(detect_486);
    __ mov(rax, rcx);
    __ xorl(rax, HS_EFL_ID);
    __ push(rax);
    __ popf();
    __ pushf();
    __ pop(rax);
    __ cmpptr(rcx, rax);
    __ jccb(Assembler::notEqual, detect_586);

    __ bind(cpu486);
    __ movl(rax, CPU_FAMILY_486);
    __ jmp(done);

    //
    // At this point, we have a chip which supports the "cpuid" instruction
    //
    __ bind(detect_586);
    __ xorl(rax, rax);
    __ cpuid();
    __ orl(rax, rax);
    __ jcc(Assembler::equal, cpu486);   // if cpuid doesn't support an input
                                        // value of at least 1, we give up and
                                        // assume a 486

    //
    // Extended cpuid(0x80000000) for processor brand string detection
    //
    __ bind(ext_cpuid);
    __ movl(rax, CPUID_EXTENDED_FN);
    __ cpuid();
    __ cmpl(rax, CPUID_EXTENDED_FN_4);
    __ jcc(Assembler::below, done);

    //
    // Extended cpuid(0x80000002)  // first 16 bytes in brand string
    //
    __ movl(rax, CPUID_EXTENDED_FN_2);
    __ cpuid();
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::proc_name_0_offset())));
    __ movl(Address(rsi, 0), rax);
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::proc_name_1_offset())));
    __ movl(Address(rsi, 0), rbx);
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::proc_name_2_offset())));
    __ movl(Address(rsi, 0), rcx);
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::proc_name_3_offset())));
    __ movl(Address(rsi,0), rdx);

    //
    // Extended cpuid(0x80000003) // next 16 bytes in brand string
    //
    __ movl(rax, CPUID_EXTENDED_FN_3);
    __ cpuid();
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::proc_name_4_offset())));
    __ movl(Address(rsi, 0), rax);
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::proc_name_5_offset())));
    __ movl(Address(rsi, 0), rbx);
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::proc_name_6_offset())));
    __ movl(Address(rsi, 0), rcx);
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::proc_name_7_offset())));
    __ movl(Address(rsi,0), rdx);

    //
    // Extended cpuid(0x80000004) // last 16 bytes in brand string
    //
    __ movl(rax, CPUID_EXTENDED_FN_4);
    __ cpuid();
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::proc_name_8_offset())));
    __ movl(Address(rsi, 0), rax);
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::proc_name_9_offset())));
    __ movl(Address(rsi, 0), rbx);
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::proc_name_10_offset())));
    __ movl(Address(rsi, 0), rcx);
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::proc_name_11_offset())));
    __ movl(Address(rsi,0), rdx);

    //
    // return
    //
    __ bind(done);
    __ popf();
    __ pop(rsi);
    __ pop(rbx);
    __ pop(rbp);
    __ ret(0);

#   undef __

    return start;
  };
};

void VM_Version::get_processor_features() {

  _cpu = 4; // 486 by default
  _model = 0;
  _stepping = 0;
  _features = 0;
  _logical_processors_per_package = 1;
  // i486 internal cache is both I&D and has a 16-byte line size
  _L1_data_cache_line_size = 16;

  // Get raw processor info

  get_cpu_info_stub(&_cpuid_info);

  assert_is_initialized();
  _cpu = extended_cpu_family();
  _model = extended_cpu_model();
  _stepping = cpu_stepping();

  if (cpu_family() > 4) { // it supports CPUID
    _features = _cpuid_info.feature_flags(); // These can be changed by VM settings
    _cpu_features = _features;   // Preserve features
    // Logical processors are only available on P4s and above,
    // and only if hyperthreading is available.
    _logical_processors_per_package = logical_processor_count();
    _L1_data_cache_line_size = L1_line_size();
  }

  // xchg and xadd instructions
  _supports_atomic_getset4 = true;
  _supports_atomic_getadd4 = true;
  LP64_ONLY(_supports_atomic_getset8 = true);
  LP64_ONLY(_supports_atomic_getadd8 = true);

#ifdef _LP64
  // OS should support SSE for x64 and hardware should support at least SSE2.
  if (!VM_Version::supports_sse2()) {
    vm_exit_during_initialization("Unknown x64 processor: SSE2 not supported");
  }
  // in 64 bit the use of SSE2 is the minimum
  if (UseSSE < 2) UseSSE = 2;
#endif

#ifdef AMD64
  // flush_icache_stub have to be generated first.
  // That is why Icache line size is hard coded in ICache class,
  // see icache_x86.hpp. It is also the reason why we can't use
  // clflush instruction in 32-bit VM since it could be running
  // on CPU which does not support it.
  //
  // The only thing we can do is to verify that flushed
  // ICache::line_size has correct value.
  guarantee(_cpuid_info.std_cpuid1_edx.bits.clflush != 0, "clflush is not supported");
  // clflush_size is size in quadwords (8 bytes).
  guarantee(_cpuid_info.std_cpuid1_ebx.bits.clflush_size == 8, "such clflush size is not supported");
#endif

#ifdef _LP64
  // assigning this field effectively enables Unsafe.writebackMemory()
  // by initing UnsafeConstant.DATA_CACHE_LINE_FLUSH_SIZE to non-zero
  // that is only implemented on x86_64 and only if the OS plays ball
  if (os::supports_map_sync()) {
    // publish data cache line flush size to generic field, otherwise
    // let if default to zero thereby disabling writeback
    _data_cache_line_flush_size = _cpuid_info.std_cpuid1_ebx.bits.clflush_size * 8;
  }
#endif

  // Check if processor has Intel Ecore
  if (FLAG_IS_DEFAULT(EnableX86ECoreOpts) && is_intel() && cpu_family() == 6 &&
    (_model == 0x97 || _model == 0xAA || _model == 0xAC || _model == 0xAF)) {
    FLAG_SET_DEFAULT(EnableX86ECoreOpts, true);
  }

  if (UseSSE < 4) {
    _features &= ~CPU_SSE4_1;
    _features &= ~CPU_SSE4_2;
  }

  if (UseSSE < 3) {
    _features &= ~CPU_SSE3;
    _features &= ~CPU_SSSE3;
    _features &= ~CPU_SSE4A;
  }

  if (UseSSE < 2)
    _features &= ~CPU_SSE2;

  if (UseSSE < 1)
    _features &= ~CPU_SSE;

  //since AVX instructions is slower than SSE in some ZX cpus, force USEAVX=0.
  if (is_zx() && ((cpu_family() == 6) || (cpu_family() == 7))) {
    UseAVX = 0;
  }

  // UseSSE is set to the smaller of what hardware supports and what
  // the command line requires.  I.e., you cannot set UseSSE to 2 on
  // older Pentiums which do not support it.
  int use_sse_limit = 0;
  if (UseSSE > 0) {
    if (UseSSE > 3 && supports_sse4_1()) {
      use_sse_limit = 4;
    } else if (UseSSE > 2 && supports_sse3()) {
      use_sse_limit = 3;
    } else if (UseSSE > 1 && supports_sse2()) {
      use_sse_limit = 2;
    } else if (UseSSE > 0 && supports_sse()) {
      use_sse_limit = 1;
    } else {
      use_sse_limit = 0;
    }
  }
  if (FLAG_IS_DEFAULT(UseSSE)) {
    FLAG_SET_DEFAULT(UseSSE, use_sse_limit);
  } else if (UseSSE > use_sse_limit) {
    warning("UseSSE=%d is not supported on this CPU, setting it to UseSSE=%d", UseSSE, use_sse_limit);
    FLAG_SET_DEFAULT(UseSSE, use_sse_limit);
  }

  // first try initial setting and detect what we can support
  int use_avx_limit = 0;
  if (UseAVX > 0) {
    if (UseSSE < 4) {
      // Don't use AVX if SSE is unavailable or has been disabled.
      use_avx_limit = 0;
    } else if (UseAVX > 2 && supports_evex()) {
      use_avx_limit = 3;
    } else if (UseAVX > 1 && supports_avx2()) {
      use_avx_limit = 2;
    } else if (UseAVX > 0 && supports_avx()) {
      use_avx_limit = 1;
    } else {
      use_avx_limit = 0;
    }
  }
  if (FLAG_IS_DEFAULT(UseAVX)) {
    // Don't use AVX-512 on older Skylakes unless explicitly requested.
    if (use_avx_limit > 2 && is_intel_skylake() && _stepping < 5) {
      FLAG_SET_DEFAULT(UseAVX, 2);
    } else {
      FLAG_SET_DEFAULT(UseAVX, use_avx_limit);
    }
  }
  if (UseAVX > use_avx_limit) {
    if (UseSSE < 4) {
      warning("UseAVX=%d requires UseSSE=4, setting it to UseAVX=0", UseAVX);
    } else {
      warning("UseAVX=%d is not supported on this CPU, setting it to UseAVX=%d", UseAVX, use_avx_limit);
    }
    FLAG_SET_DEFAULT(UseAVX, use_avx_limit);
  }

  if (UseAVX < 3) {
    _features &= ~CPU_AVX512F;
    _features &= ~CPU_AVX512DQ;
    _features &= ~CPU_AVX512CD;
    _features &= ~CPU_AVX512BW;
    _features &= ~CPU_AVX512VL;
    _features &= ~CPU_AVX512_VPOPCNTDQ;
    _features &= ~CPU_AVX512_VPCLMULQDQ;
    _features &= ~CPU_AVX512_VAES;
    _features &= ~CPU_AVX512_VNNI;
    _features &= ~CPU_AVX512_VBMI;
    _features &= ~CPU_AVX512_VBMI2;
    _features &= ~CPU_AVX512_BITALG;
    _features &= ~CPU_AVX512_IFMA;
  }

  if (UseAVX < 2)
    _features &= ~CPU_AVX2;

  if (UseAVX < 1) {
    _features &= ~CPU_AVX;
    _features &= ~CPU_VZEROUPPER;
    _features &= ~CPU_F16C;
  }

  if (logical_processors_per_package() == 1) {
    // HT processor could be installed on a system which doesn't support HT.
    _features &= ~CPU_HT;
  }

  if (is_intel()) { // Intel cpus specific settings
    if (is_knights_family()) {
      _features &= ~CPU_VZEROUPPER;
      _features &= ~CPU_AVX512BW;
      _features &= ~CPU_AVX512VL;
      _features &= ~CPU_AVX512DQ;
      _features &= ~CPU_AVX512_VNNI;
      _features &= ~CPU_AVX512_VAES;
      _features &= ~CPU_AVX512_VPOPCNTDQ;
      _features &= ~CPU_AVX512_VPCLMULQDQ;
      _features &= ~CPU_AVX512_VBMI;
      _features &= ~CPU_AVX512_VBMI2;
      _features &= ~CPU_CLWB;
      _features &= ~CPU_FLUSHOPT;
      _features &= ~CPU_GFNI;
      _features &= ~CPU_AVX512_BITALG;
      _features &= ~CPU_AVX512_IFMA;
    }
  }

  if (FLAG_IS_DEFAULT(IntelJccErratumMitigation)) {
    _has_intel_jcc_erratum = compute_has_intel_jcc_erratum();
  } else {
    _has_intel_jcc_erratum = IntelJccErratumMitigation;
  }

  char buf[1024];
  int res = jio_snprintf(
              buf, sizeof(buf),
              "(%u cores per cpu, %u threads per core) family %d model %d stepping %d microcode 0x%x",
              cores_per_cpu(), threads_per_core(),
              cpu_family(), _model, _stepping, os::cpu_microcode_revision());
  assert(res > 0, "not enough temporary space allocated");
  insert_features_names(buf + res, sizeof(buf) - res, _features_names);

  _features_string = os::strdup(buf);

  // Use AES instructions if available.
  if (supports_aes()) {
    if (FLAG_IS_DEFAULT(UseAES)) {
      FLAG_SET_DEFAULT(UseAES, true);
    }
    if (!UseAES) {
      if (UseAESIntrinsics && !FLAG_IS_DEFAULT(UseAESIntrinsics)) {
        warning("AES intrinsics require UseAES flag to be enabled. Intrinsics will be disabled.");
      }
      FLAG_SET_DEFAULT(UseAESIntrinsics, false);
    } else {
      if (UseSSE > 2) {
        if (FLAG_IS_DEFAULT(UseAESIntrinsics)) {
          FLAG_SET_DEFAULT(UseAESIntrinsics, true);
        }
      } else {
        // The AES intrinsic stubs require AES instruction support (of course)
        // but also require sse3 mode or higher for instructions it use.
        if (UseAESIntrinsics && !FLAG_IS_DEFAULT(UseAESIntrinsics)) {
          warning("X86 AES intrinsics require SSE3 instructions or higher. Intrinsics will be disabled.");
        }
        FLAG_SET_DEFAULT(UseAESIntrinsics, false);
      }

      // --AES-CTR begins--
      if (!UseAESIntrinsics) {
        if (UseAESCTRIntrinsics && !FLAG_IS_DEFAULT(UseAESCTRIntrinsics)) {
          warning("AES-CTR intrinsics require UseAESIntrinsics flag to be enabled. Intrinsics will be disabled.");
          FLAG_SET_DEFAULT(UseAESCTRIntrinsics, false);
        }
      } else {
        if (supports_sse4_1()) {
          if (FLAG_IS_DEFAULT(UseAESCTRIntrinsics)) {
            FLAG_SET_DEFAULT(UseAESCTRIntrinsics, true);
          }
        } else {
           // The AES-CTR intrinsic stubs require AES instruction support (of course)
           // but also require sse4.1 mode or higher for instructions it use.
          if (UseAESCTRIntrinsics && !FLAG_IS_DEFAULT(UseAESCTRIntrinsics)) {
             warning("X86 AES-CTR intrinsics require SSE4.1 instructions or higher. Intrinsics will be disabled.");
           }
           FLAG_SET_DEFAULT(UseAESCTRIntrinsics, false);
        }
      }
      // --AES-CTR ends--
    }
  } else if (UseAES || UseAESIntrinsics || UseAESCTRIntrinsics) {
    if (UseAES && !FLAG_IS_DEFAULT(UseAES)) {
      warning("AES instructions are not available on this CPU");
      FLAG_SET_DEFAULT(UseAES, false);
    }
    if (UseAESIntrinsics && !FLAG_IS_DEFAULT(UseAESIntrinsics)) {
      warning("AES intrinsics are not available on this CPU");
      FLAG_SET_DEFAULT(UseAESIntrinsics, false);
    }
    if (UseAESCTRIntrinsics && !FLAG_IS_DEFAULT(UseAESCTRIntrinsics)) {
      warning("AES-CTR intrinsics are not available on this CPU");
      FLAG_SET_DEFAULT(UseAESCTRIntrinsics, false);
    }
  }

  // Use CLMUL instructions if available.
  if (supports_clmul()) {
    if (FLAG_IS_DEFAULT(UseCLMUL)) {
      UseCLMUL = true;
    }
  } else if (UseCLMUL) {
    if (!FLAG_IS_DEFAULT(UseCLMUL))
      warning("CLMUL instructions not available on this CPU (AVX may also be required)");
    FLAG_SET_DEFAULT(UseCLMUL, false);
  }

  if (UseCLMUL && (UseSSE > 2)) {
    if (FLAG_IS_DEFAULT(UseCRC32Intrinsics)) {
      UseCRC32Intrinsics = true;
    }
  } else if (UseCRC32Intrinsics) {
    if (!FLAG_IS_DEFAULT(UseCRC32Intrinsics))
      warning("CRC32 Intrinsics requires CLMUL instructions (not available on this CPU)");
    FLAG_SET_DEFAULT(UseCRC32Intrinsics, false);
  }

#ifdef _LP64
  if (supports_avx2()) {
    if (FLAG_IS_DEFAULT(UseAdler32Intrinsics)) {
      UseAdler32Intrinsics = true;
    }
  } else if (UseAdler32Intrinsics) {
    if (!FLAG_IS_DEFAULT(UseAdler32Intrinsics)) {
      warning("Adler32 Intrinsics requires avx2 instructions (not available on this CPU)");
    }
    FLAG_SET_DEFAULT(UseAdler32Intrinsics, false);
  }
#else
  if (UseAdler32Intrinsics) {
    warning("Adler32Intrinsics not available on this CPU.");
    FLAG_SET_DEFAULT(UseAdler32Intrinsics, false);
  }
#endif

  if (supports_sse4_2() && supports_clmul()) {
    if (FLAG_IS_DEFAULT(UseCRC32CIntrinsics)) {
      UseCRC32CIntrinsics = true;
    }
  } else if (UseCRC32CIntrinsics) {
    if (!FLAG_IS_DEFAULT(UseCRC32CIntrinsics)) {
      warning("CRC32C intrinsics are not available on this CPU");
    }
    FLAG_SET_DEFAULT(UseCRC32CIntrinsics, false);
  }

  // GHASH/GCM intrinsics
  if (UseCLMUL && (UseSSE > 2)) {
    if (FLAG_IS_DEFAULT(UseGHASHIntrinsics)) {
      UseGHASHIntrinsics = true;
    }
  } else if (UseGHASHIntrinsics) {
    if (!FLAG_IS_DEFAULT(UseGHASHIntrinsics))
      warning("GHASH intrinsic requires CLMUL and SSE2 instructions on this CPU");
    FLAG_SET_DEFAULT(UseGHASHIntrinsics, false);
  }

#ifdef _LP64
  // ChaCha20 Intrinsics
  // As long as the system supports AVX as a baseline we can do a
  // SIMD-enabled block function.  StubGenerator makes the determination
  // based on the VM capabilities whether to use an AVX2 or AVX512-enabled
  // version.
  if (UseAVX >= 1) {
      if (FLAG_IS_DEFAULT(UseChaCha20Intrinsics)) {
          UseChaCha20Intrinsics = true;
      }
  } else if (UseChaCha20Intrinsics) {
      if (!FLAG_IS_DEFAULT(UseChaCha20Intrinsics)) {
          warning("ChaCha20 intrinsic requires AVX instructions");
      }
      FLAG_SET_DEFAULT(UseChaCha20Intrinsics, false);
  }
#else
  // No support currently for ChaCha20 intrinsics on 32-bit platforms
  if (UseChaCha20Intrinsics) {
      warning("ChaCha20 intrinsics are not available on this CPU.");
      FLAG_SET_DEFAULT(UseChaCha20Intrinsics, false);
  }
#endif // _LP64

  // Base64 Intrinsics (Check the condition for which the intrinsic will be active)
  if (UseAVX >= 2) {
    if (FLAG_IS_DEFAULT(UseBASE64Intrinsics)) {
      UseBASE64Intrinsics = true;
    }
  } else if (UseBASE64Intrinsics) {
     if (!FLAG_IS_DEFAULT(UseBASE64Intrinsics))
      warning("Base64 intrinsic requires EVEX instructions on this CPU");
    FLAG_SET_DEFAULT(UseBASE64Intrinsics, false);
  }

  if (supports_fma() && UseSSE >= 2) { // Check UseSSE since FMA code uses SSE instructions
    if (FLAG_IS_DEFAULT(UseFMA)) {
      UseFMA = true;
    }
  } else if (UseFMA) {
    warning("FMA instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseFMA, false);
  }

  if (FLAG_IS_DEFAULT(UseMD5Intrinsics)) {
    UseMD5Intrinsics = true;
  }

  if (supports_sha() LP64_ONLY(|| (supports_avx2() && supports_bmi2()))) {
    if (FLAG_IS_DEFAULT(UseSHA)) {
      UseSHA = true;
    }
  } else if (UseSHA) {
    warning("SHA instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseSHA, false);
  }

  if (supports_sha() && supports_sse4_1() && UseSHA) {
    if (FLAG_IS_DEFAULT(UseSHA1Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA1Intrinsics, true);
    }
  } else if (UseSHA1Intrinsics) {
    warning("Intrinsics for SHA-1 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA1Intrinsics, false);
  }

  if (supports_sse4_1() && UseSHA) {
    if (FLAG_IS_DEFAULT(UseSHA256Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA256Intrinsics, true);
    }
  } else if (UseSHA256Intrinsics) {
    warning("Intrinsics for SHA-224 and SHA-256 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA256Intrinsics, false);
  }

#ifdef _LP64
  // These are only supported on 64-bit
  if (UseSHA && supports_avx2() && supports_bmi2()) {
    if (FLAG_IS_DEFAULT(UseSHA512Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA512Intrinsics, true);
    }
  } else
#endif
  if (UseSHA512Intrinsics) {
    warning("Intrinsics for SHA-384 and SHA-512 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA512Intrinsics, false);
  }

  if (UseSHA3Intrinsics) {
    warning("Intrinsics for SHA3-224, SHA3-256, SHA3-384 and SHA3-512 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA3Intrinsics, false);
  }

  if (!(UseSHA1Intrinsics || UseSHA256Intrinsics || UseSHA512Intrinsics)) {
    FLAG_SET_DEFAULT(UseSHA, false);
  }

  if (!supports_rtm() && UseRTMLocking) {
    vm_exit_during_initialization("RTM instructions are not available on this CPU");
  }

#if INCLUDE_RTM_OPT
  if (UseRTMLocking) {
    if (!CompilerConfig::is_c2_enabled()) {
      // Only C2 does RTM locking optimization.
      vm_exit_during_initialization("RTM locking optimization is not supported in this VM");
    }
    if (is_intel_family_core()) {
      if ((_model == CPU_MODEL_HASWELL_E3) ||
          (_model == CPU_MODEL_HASWELL_E7 && _stepping < 3) ||
          (_model == CPU_MODEL_BROADWELL  && _stepping < 4)) {
        // currently a collision between SKL and HSW_E3
        if (!UnlockExperimentalVMOptions && UseAVX < 3) {
          vm_exit_during_initialization("UseRTMLocking is only available as experimental option on this "
                                        "platform. It must be enabled via -XX:+UnlockExperimentalVMOptions flag.");
        } else {
          warning("UseRTMLocking is only available as experimental option on this platform.");
        }
      }
    }
    if (!FLAG_IS_CMDLINE(UseRTMLocking)) {
      // RTM locking should be used only for applications with
      // high lock contention. For now we do not use it by default.
      vm_exit_during_initialization("UseRTMLocking flag should be only set on command line");
    }
  } else { // !UseRTMLocking
    if (UseRTMForStackLocks) {
      if (!FLAG_IS_DEFAULT(UseRTMForStackLocks)) {
        warning("UseRTMForStackLocks flag should be off when UseRTMLocking flag is off");
      }
      FLAG_SET_DEFAULT(UseRTMForStackLocks, false);
    }
    if (UseRTMDeopt) {
      FLAG_SET_DEFAULT(UseRTMDeopt, false);
    }
    if (PrintPreciseRTMLockingStatistics) {
      FLAG_SET_DEFAULT(PrintPreciseRTMLockingStatistics, false);
    }
  }
#else
  if (UseRTMLocking) {
    // Only C2 does RTM locking optimization.
    vm_exit_during_initialization("RTM locking optimization is not supported in this VM");
  }
#endif

#ifdef COMPILER2
  if (UseFPUForSpilling) {
    if (UseSSE < 2) {
      // Only supported with SSE2+
      FLAG_SET_DEFAULT(UseFPUForSpilling, false);
    }
  }
#endif

#if COMPILER2_OR_JVMCI
  int max_vector_size = 0;
  if (UseSSE < 2) {
    // Vectors (in XMM) are only supported with SSE2+
    // SSE is always 2 on x64.
    max_vector_size = 0;
  } else if (UseAVX == 0 || !os_supports_avx_vectors()) {
    // 16 byte vectors (in XMM) are supported with SSE2+
    max_vector_size = 16;
  } else if (UseAVX == 1 || UseAVX == 2) {
    // 32 bytes vectors (in YMM) are only supported with AVX+
    max_vector_size = 32;
  } else if (UseAVX > 2) {
    // 64 bytes vectors (in ZMM) are only supported with AVX 3
    max_vector_size = 64;
  }

#ifdef _LP64
  int min_vector_size = 4; // We require MaxVectorSize to be at least 4 on 64bit
#else
  int min_vector_size = 0;
#endif

  if (!FLAG_IS_DEFAULT(MaxVectorSize)) {
    if (MaxVectorSize < min_vector_size) {
      warning("MaxVectorSize must be at least %i on this platform", min_vector_size);
      FLAG_SET_DEFAULT(MaxVectorSize, min_vector_size);
    }
    if (MaxVectorSize > max_vector_size) {
      warning("MaxVectorSize must be at most %i on this platform", max_vector_size);
      FLAG_SET_DEFAULT(MaxVectorSize, max_vector_size);
    }
    if (!is_power_of_2(MaxVectorSize)) {
      warning("MaxVectorSize must be a power of 2, setting to default: %i", max_vector_size);
      FLAG_SET_DEFAULT(MaxVectorSize, max_vector_size);
    }
  } else {
    // If default, use highest supported configuration
    FLAG_SET_DEFAULT(MaxVectorSize, max_vector_size);
  }

#if defined(COMPILER2) && defined(ASSERT)
  if (MaxVectorSize > 0) {
    if (supports_avx() && PrintMiscellaneous && Verbose && TraceNewVectors) {
      tty->print_cr("State of YMM registers after signal handle:");
      int nreg = 2 LP64_ONLY(+2);
      const char* ymm_name[4] = {"0", "7", "8", "15"};
      for (int i = 0; i < nreg; i++) {
        tty->print("YMM%s:", ymm_name[i]);
        for (int j = 7; j >=0; j--) {
          tty->print(" %x", _cpuid_info.ymm_save[i*8 + j]);
        }
        tty->cr();
      }
    }
  }
#endif // COMPILER2 && ASSERT

#ifdef _LP64
  if (supports_avx512ifma() && supports_avx512vlbw() && MaxVectorSize >= 64) {
    if (FLAG_IS_DEFAULT(UsePoly1305Intrinsics)) {
      FLAG_SET_DEFAULT(UsePoly1305Intrinsics, true);
    }
  } else
#endif
  if (UsePoly1305Intrinsics) {
    warning("Intrinsics for Poly1305 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UsePoly1305Intrinsics, false);
  }

#ifdef _LP64
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
#else
  if (UseMultiplyToLenIntrinsic) {
    if (!FLAG_IS_DEFAULT(UseMultiplyToLenIntrinsic)) {
      warning("multiplyToLen intrinsic is not available in 32-bit VM");
    }
    FLAG_SET_DEFAULT(UseMultiplyToLenIntrinsic, false);
  }
  if (UseMontgomeryMultiplyIntrinsic) {
    if (!FLAG_IS_DEFAULT(UseMontgomeryMultiplyIntrinsic)) {
      warning("montgomeryMultiply intrinsic is not available in 32-bit VM");
    }
    FLAG_SET_DEFAULT(UseMontgomeryMultiplyIntrinsic, false);
  }
  if (UseMontgomerySquareIntrinsic) {
    if (!FLAG_IS_DEFAULT(UseMontgomerySquareIntrinsic)) {
      warning("montgomerySquare intrinsic is not available in 32-bit VM");
    }
    FLAG_SET_DEFAULT(UseMontgomerySquareIntrinsic, false);
  }
  if (UseSquareToLenIntrinsic) {
    if (!FLAG_IS_DEFAULT(UseSquareToLenIntrinsic)) {
      warning("squareToLen intrinsic is not available in 32-bit VM");
    }
    FLAG_SET_DEFAULT(UseSquareToLenIntrinsic, false);
  }
  if (UseMulAddIntrinsic) {
    if (!FLAG_IS_DEFAULT(UseMulAddIntrinsic)) {
      warning("mulAdd intrinsic is not available in 32-bit VM");
    }
    FLAG_SET_DEFAULT(UseMulAddIntrinsic, false);
  }
#endif // _LP64
#endif // COMPILER2_OR_JVMCI

  // On new cpus instructions which update whole XMM register should be used
  // to prevent partial register stall due to dependencies on high half.
  //
  // UseXmmLoadAndClearUpper == true  --> movsd(xmm, mem)
  // UseXmmLoadAndClearUpper == false --> movlpd(xmm, mem)
  // UseXmmRegToRegMoveAll == true  --> movaps(xmm, xmm), movapd(xmm, xmm).
  // UseXmmRegToRegMoveAll == false --> movss(xmm, xmm),  movsd(xmm, xmm).


  if (is_zx()) { // ZX cpus specific settings
    if (FLAG_IS_DEFAULT(UseStoreImmI16)) {
      UseStoreImmI16 = false; // don't use it on ZX cpus
    }
    if ((cpu_family() == 6) || (cpu_family() == 7)) {
      if (FLAG_IS_DEFAULT(UseAddressNop)) {
        // Use it on all ZX cpus
        UseAddressNop = true;
      }
    }
    if (FLAG_IS_DEFAULT(UseXmmLoadAndClearUpper)) {
      UseXmmLoadAndClearUpper = true; // use movsd on all ZX cpus
    }
    if (FLAG_IS_DEFAULT(UseXmmRegToRegMoveAll)) {
      if (supports_sse3()) {
        UseXmmRegToRegMoveAll = true; // use movaps, movapd on new ZX cpus
      } else {
        UseXmmRegToRegMoveAll = false;
      }
    }
    if (((cpu_family() == 6) || (cpu_family() == 7)) && supports_sse3()) { // new ZX cpus
#ifdef COMPILER2
      if (FLAG_IS_DEFAULT(MaxLoopPad)) {
        // For new ZX cpus do the next optimization:
        // don't align the beginning of a loop if there are enough instructions
        // left (NumberOfLoopInstrToAlign defined in c2_globals.hpp)
        // in current fetch line (OptoLoopAlignment) or the padding
        // is big (> MaxLoopPad).
        // Set MaxLoopPad to 11 for new ZX cpus to reduce number of
        // generated NOP instructions. 11 is the largest size of one
        // address NOP instruction '0F 1F' (see Assembler::nop(i)).
        MaxLoopPad = 11;
      }
#endif // COMPILER2
      if (FLAG_IS_DEFAULT(UseXMMForArrayCopy)) {
        UseXMMForArrayCopy = true; // use SSE2 movq on new ZX cpus
      }
      if (supports_sse4_2()) { // new ZX cpus
        if (FLAG_IS_DEFAULT(UseUnalignedLoadStores)) {
          UseUnalignedLoadStores = true; // use movdqu on newest ZX cpus
        }
      }
      if (supports_sse4_2()) {
        if (FLAG_IS_DEFAULT(UseSSE42Intrinsics)) {
          FLAG_SET_DEFAULT(UseSSE42Intrinsics, true);
        }
      } else {
        if (UseSSE42Intrinsics && !FLAG_IS_DEFAULT(UseAESIntrinsics)) {
          warning("SSE4.2 intrinsics require SSE4.2 instructions or higher. Intrinsics will be disabled.");
        }
        FLAG_SET_DEFAULT(UseSSE42Intrinsics, false);
      }
    }

    if (FLAG_IS_DEFAULT(AllocatePrefetchInstr) && supports_3dnow_prefetch()) {
      FLAG_SET_DEFAULT(AllocatePrefetchInstr, 3);
    }
  }

  if (is_amd_family()) { // AMD cpus specific settings
    if (supports_sse2() && FLAG_IS_DEFAULT(UseAddressNop)) {
      // Use it on new AMD cpus starting from Opteron.
      UseAddressNop = true;
    }
    if (supports_sse2() && FLAG_IS_DEFAULT(UseNewLongLShift)) {
      // Use it on new AMD cpus starting from Opteron.
      UseNewLongLShift = true;
    }
    if (FLAG_IS_DEFAULT(UseXmmLoadAndClearUpper)) {
      if (supports_sse4a()) {
        UseXmmLoadAndClearUpper = true; // use movsd only on '10h' Opteron
      } else {
        UseXmmLoadAndClearUpper = false;
      }
    }
    if (FLAG_IS_DEFAULT(UseXmmRegToRegMoveAll)) {
      if (supports_sse4a()) {
        UseXmmRegToRegMoveAll = true; // use movaps, movapd only on '10h'
      } else {
        UseXmmRegToRegMoveAll = false;
      }
    }
    if (FLAG_IS_DEFAULT(UseXmmI2F)) {
      if (supports_sse4a()) {
        UseXmmI2F = true;
      } else {
        UseXmmI2F = false;
      }
    }
    if (FLAG_IS_DEFAULT(UseXmmI2D)) {
      if (supports_sse4a()) {
        UseXmmI2D = true;
      } else {
        UseXmmI2D = false;
      }
    }
    if (supports_sse4_2()) {
      if (FLAG_IS_DEFAULT(UseSSE42Intrinsics)) {
        FLAG_SET_DEFAULT(UseSSE42Intrinsics, true);
      }
    } else {
      if (UseSSE42Intrinsics && !FLAG_IS_DEFAULT(UseAESIntrinsics)) {
        warning("SSE4.2 intrinsics require SSE4.2 instructions or higher. Intrinsics will be disabled.");
      }
      FLAG_SET_DEFAULT(UseSSE42Intrinsics, false);
    }

    // some defaults for AMD family 15h
    if (cpu_family() == 0x15) {
      // On family 15h processors default is no sw prefetch
      if (FLAG_IS_DEFAULT(AllocatePrefetchStyle)) {
        FLAG_SET_DEFAULT(AllocatePrefetchStyle, 0);
      }
      // Also, if some other prefetch style is specified, default instruction type is PREFETCHW
      if (FLAG_IS_DEFAULT(AllocatePrefetchInstr)) {
        FLAG_SET_DEFAULT(AllocatePrefetchInstr, 3);
      }
      // On family 15h processors use XMM and UnalignedLoadStores for Array Copy
      if (supports_sse2() && FLAG_IS_DEFAULT(UseXMMForArrayCopy)) {
        FLAG_SET_DEFAULT(UseXMMForArrayCopy, true);
      }
      if (supports_sse2() && FLAG_IS_DEFAULT(UseUnalignedLoadStores)) {
        FLAG_SET_DEFAULT(UseUnalignedLoadStores, true);
      }
    }

#ifdef COMPILER2
    if (cpu_family() < 0x17 && MaxVectorSize > 16) {
      // Limit vectors size to 16 bytes on AMD cpus < 17h.
      FLAG_SET_DEFAULT(MaxVectorSize, 16);
    }
#endif // COMPILER2

    // Some defaults for AMD family >= 17h && Hygon family 18h
    if (cpu_family() >= 0x17) {
      // On family >=17h processors use XMM and UnalignedLoadStores
      // for Array Copy
      if (supports_sse2() && FLAG_IS_DEFAULT(UseXMMForArrayCopy)) {
        FLAG_SET_DEFAULT(UseXMMForArrayCopy, true);
      }
      if (supports_sse2() && FLAG_IS_DEFAULT(UseUnalignedLoadStores)) {
        FLAG_SET_DEFAULT(UseUnalignedLoadStores, true);
      }
#ifdef COMPILER2
      if (supports_sse4_2() && FLAG_IS_DEFAULT(UseFPUForSpilling)) {
        FLAG_SET_DEFAULT(UseFPUForSpilling, true);
      }
#endif
    }
  }

  if (is_intel()) { // Intel cpus specific settings
    if (FLAG_IS_DEFAULT(UseStoreImmI16)) {
      UseStoreImmI16 = false; // don't use it on Intel cpus
    }
    if (cpu_family() == 6 || cpu_family() == 15) {
      if (FLAG_IS_DEFAULT(UseAddressNop)) {
        // Use it on all Intel cpus starting from PentiumPro
        UseAddressNop = true;
      }
    }
    if (FLAG_IS_DEFAULT(UseXmmLoadAndClearUpper)) {
      UseXmmLoadAndClearUpper = true; // use movsd on all Intel cpus
    }
    if (FLAG_IS_DEFAULT(UseXmmRegToRegMoveAll)) {
      if (supports_sse3()) {
        UseXmmRegToRegMoveAll = true; // use movaps, movapd on new Intel cpus
      } else {
        UseXmmRegToRegMoveAll = false;
      }
    }
    if (cpu_family() == 6 && supports_sse3()) { // New Intel cpus
#ifdef COMPILER2
      if (FLAG_IS_DEFAULT(MaxLoopPad)) {
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

      if (FLAG_IS_DEFAULT(UseXMMForArrayCopy)) {
        UseXMMForArrayCopy = true; // use SSE2 movq on new Intel cpus
      }
      if ((supports_sse4_2() && supports_ht()) || supports_avx()) { // Newest Intel cpus
        if (FLAG_IS_DEFAULT(UseUnalignedLoadStores)) {
          UseUnalignedLoadStores = true; // use movdqu on newest Intel cpus
        }
      }
      if (supports_sse4_2()) {
        if (FLAG_IS_DEFAULT(UseSSE42Intrinsics)) {
          FLAG_SET_DEFAULT(UseSSE42Intrinsics, true);
        }
      } else {
        if (UseSSE42Intrinsics && !FLAG_IS_DEFAULT(UseAESIntrinsics)) {
          warning("SSE4.2 intrinsics require SSE4.2 instructions or higher. Intrinsics will be disabled.");
        }
        FLAG_SET_DEFAULT(UseSSE42Intrinsics, false);
      }
    }
    if (is_atom_family() || is_knights_family()) {
#ifdef COMPILER2
      if (FLAG_IS_DEFAULT(OptoScheduling)) {
        OptoScheduling = true;
      }
#endif
      if (supports_sse4_2()) { // Silvermont
        if (FLAG_IS_DEFAULT(UseUnalignedLoadStores)) {
          UseUnalignedLoadStores = true; // use movdqu on newest Intel cpus
        }
      }
      if (FLAG_IS_DEFAULT(UseIncDec)) {
        FLAG_SET_DEFAULT(UseIncDec, false);
      }
    }
    if (FLAG_IS_DEFAULT(AllocatePrefetchInstr) && supports_3dnow_prefetch()) {
      FLAG_SET_DEFAULT(AllocatePrefetchInstr, 3);
    }
#ifdef COMPILER2
    if (UseAVX > 2) {
      if (FLAG_IS_DEFAULT(ArrayOperationPartialInlineSize) ||
          (!FLAG_IS_DEFAULT(ArrayOperationPartialInlineSize) &&
           ArrayOperationPartialInlineSize != 0 &&
           ArrayOperationPartialInlineSize != 16 &&
           ArrayOperationPartialInlineSize != 32 &&
           ArrayOperationPartialInlineSize != 64)) {
        int inline_size = 0;
        if (MaxVectorSize >= 64 && AVX3Threshold == 0) {
          inline_size = 64;
        } else if (MaxVectorSize >= 32) {
          inline_size = 32;
        } else if (MaxVectorSize >= 16) {
          inline_size = 16;
        }
        if(!FLAG_IS_DEFAULT(ArrayOperationPartialInlineSize)) {
          warning("Setting ArrayOperationPartialInlineSize as %d", inline_size);
        }
        ArrayOperationPartialInlineSize = inline_size;
      }

      if (ArrayOperationPartialInlineSize > MaxVectorSize) {
        ArrayOperationPartialInlineSize = MaxVectorSize >= 16 ? MaxVectorSize : 0;
        if (ArrayOperationPartialInlineSize) {
          warning("Setting ArrayOperationPartialInlineSize as MaxVectorSize" INTX_FORMAT ")", MaxVectorSize);
        } else {
          warning("Setting ArrayOperationPartialInlineSize as " INTX_FORMAT, ArrayOperationPartialInlineSize);
        }
      }
    }
#endif
  }

#ifdef COMPILER2
  if (FLAG_IS_DEFAULT(OptimizeFill)) {
    if (MaxVectorSize < 32 || !VM_Version::supports_avx512vlbw()) {
      OptimizeFill = false;
    }
  }
#endif

#ifdef _LP64
  if (UseSSE42Intrinsics) {
    if (FLAG_IS_DEFAULT(UseVectorizedMismatchIntrinsic)) {
      UseVectorizedMismatchIntrinsic = true;
    }
  } else if (UseVectorizedMismatchIntrinsic) {
    if (!FLAG_IS_DEFAULT(UseVectorizedMismatchIntrinsic))
      warning("vectorizedMismatch intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseVectorizedMismatchIntrinsic, false);
  }
  if (UseAVX >= 2) {
    FLAG_SET_DEFAULT(UseVectorizedHashCodeIntrinsic, true);
  } else if (UseVectorizedHashCodeIntrinsic) {
    if (!FLAG_IS_DEFAULT(UseVectorizedHashCodeIntrinsic))
      warning("vectorizedHashCode intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseVectorizedHashCodeIntrinsic, false);
  }
#else
  if (UseVectorizedMismatchIntrinsic) {
    if (!FLAG_IS_DEFAULT(UseVectorizedMismatchIntrinsic)) {
      warning("vectorizedMismatch intrinsic is not available in 32-bit VM");
    }
    FLAG_SET_DEFAULT(UseVectorizedMismatchIntrinsic, false);
  }
  if (UseVectorizedHashCodeIntrinsic) {
    if (!FLAG_IS_DEFAULT(UseVectorizedHashCodeIntrinsic)) {
      warning("vectorizedHashCode intrinsic is not available in 32-bit VM");
    }
    FLAG_SET_DEFAULT(UseVectorizedHashCodeIntrinsic, false);
  }
#endif // _LP64

  // Use count leading zeros count instruction if available.
  if (supports_lzcnt()) {
    if (FLAG_IS_DEFAULT(UseCountLeadingZerosInstruction)) {
      UseCountLeadingZerosInstruction = true;
    }
   } else if (UseCountLeadingZerosInstruction) {
    warning("lzcnt instruction is not available on this CPU");
    FLAG_SET_DEFAULT(UseCountLeadingZerosInstruction, false);
  }

  // Use count trailing zeros instruction if available
  if (supports_bmi1()) {
    // tzcnt does not require VEX prefix
    if (FLAG_IS_DEFAULT(UseCountTrailingZerosInstruction)) {
      if (!UseBMI1Instructions && !FLAG_IS_DEFAULT(UseBMI1Instructions)) {
        // Don't use tzcnt if BMI1 is switched off on command line.
        UseCountTrailingZerosInstruction = false;
      } else {
        UseCountTrailingZerosInstruction = true;
      }
    }
  } else if (UseCountTrailingZerosInstruction) {
    warning("tzcnt instruction is not available on this CPU");
    FLAG_SET_DEFAULT(UseCountTrailingZerosInstruction, false);
  }

  // BMI instructions (except tzcnt) use an encoding with VEX prefix.
  // VEX prefix is generated only when AVX > 0.
  if (supports_bmi1() && supports_avx()) {
    if (FLAG_IS_DEFAULT(UseBMI1Instructions)) {
      UseBMI1Instructions = true;
    }
  } else if (UseBMI1Instructions) {
    warning("BMI1 instructions are not available on this CPU (AVX is also required)");
    FLAG_SET_DEFAULT(UseBMI1Instructions, false);
  }

  if (supports_bmi2() && supports_avx()) {
    if (FLAG_IS_DEFAULT(UseBMI2Instructions)) {
      UseBMI2Instructions = true;
    }
  } else if (UseBMI2Instructions) {
    warning("BMI2 instructions are not available on this CPU (AVX is also required)");
    FLAG_SET_DEFAULT(UseBMI2Instructions, false);
  }

  // Use population count instruction if available.
  if (supports_popcnt()) {
    if (FLAG_IS_DEFAULT(UsePopCountInstruction)) {
      UsePopCountInstruction = true;
    }
  } else if (UsePopCountInstruction) {
    warning("POPCNT instruction is not available on this CPU");
    FLAG_SET_DEFAULT(UsePopCountInstruction, false);
  }

  // Use fast-string operations if available.
  if (supports_erms()) {
    if (FLAG_IS_DEFAULT(UseFastStosb)) {
      UseFastStosb = true;
    }
  } else if (UseFastStosb) {
    warning("fast-string operations are not available on this CPU");
    FLAG_SET_DEFAULT(UseFastStosb, false);
  }

  // For AMD Processors use XMM/YMM MOVDQU instructions
  // for Object Initialization as default
  if (is_amd() && cpu_family() >= 0x19) {
    if (FLAG_IS_DEFAULT(UseFastStosb)) {
      UseFastStosb = false;
    }
  }

#ifdef COMPILER2
  if (is_intel() && MaxVectorSize > 16) {
    if (FLAG_IS_DEFAULT(UseFastStosb)) {
      UseFastStosb = false;
    }
  }
#endif

  // Use XMM/YMM MOVDQU instruction for Object Initialization
  if (!UseFastStosb && UseSSE >= 2 && UseUnalignedLoadStores) {
    if (FLAG_IS_DEFAULT(UseXMMForObjInit)) {
      UseXMMForObjInit = true;
    }
  } else if (UseXMMForObjInit) {
    warning("UseXMMForObjInit requires SSE2 and unaligned load/stores. Feature is switched off.");
    FLAG_SET_DEFAULT(UseXMMForObjInit, false);
  }

#ifdef COMPILER2
  if (FLAG_IS_DEFAULT(AlignVector)) {
    // Modern processors allow misaligned memory operations for vectors.
    AlignVector = !UseUnalignedLoadStores;
  }
#endif // COMPILER2

  if (FLAG_IS_DEFAULT(AllocatePrefetchInstr)) {
    if (AllocatePrefetchInstr == 3 && !supports_3dnow_prefetch()) {
      FLAG_SET_DEFAULT(AllocatePrefetchInstr, 0);
    } else if (!supports_sse() && supports_3dnow_prefetch()) {
      FLAG_SET_DEFAULT(AllocatePrefetchInstr, 3);
    }
  }

  // Allocation prefetch settings
  int cache_line_size = checked_cast<int>(prefetch_data_size());
  if (FLAG_IS_DEFAULT(AllocatePrefetchStepSize) &&
      (cache_line_size > AllocatePrefetchStepSize)) {
    FLAG_SET_DEFAULT(AllocatePrefetchStepSize, cache_line_size);
  }

  if ((AllocatePrefetchDistance == 0) && (AllocatePrefetchStyle != 0)) {
    assert(!FLAG_IS_DEFAULT(AllocatePrefetchDistance), "default value should not be 0");
    if (!FLAG_IS_DEFAULT(AllocatePrefetchStyle)) {
      warning("AllocatePrefetchDistance is set to 0 which disable prefetching. Ignoring AllocatePrefetchStyle flag.");
    }
    FLAG_SET_DEFAULT(AllocatePrefetchStyle, 0);
  }

  if (FLAG_IS_DEFAULT(AllocatePrefetchDistance)) {
    bool use_watermark_prefetch = (AllocatePrefetchStyle == 2);
    FLAG_SET_DEFAULT(AllocatePrefetchDistance, allocate_prefetch_distance(use_watermark_prefetch));
  }

  if (is_intel() && cpu_family() == 6 && supports_sse3()) {
    if (FLAG_IS_DEFAULT(AllocatePrefetchLines) &&
        supports_sse4_2() && supports_ht()) { // Nehalem based cpus
      FLAG_SET_DEFAULT(AllocatePrefetchLines, 4);
    }
#ifdef COMPILER2
    if (FLAG_IS_DEFAULT(UseFPUForSpilling) && supports_sse4_2()) {
      FLAG_SET_DEFAULT(UseFPUForSpilling, true);
    }
#endif
  }

  if (is_zx() && ((cpu_family() == 6) || (cpu_family() == 7)) && supports_sse4_2()) {
#ifdef COMPILER2
    if (FLAG_IS_DEFAULT(UseFPUForSpilling)) {
      FLAG_SET_DEFAULT(UseFPUForSpilling, true);
    }
#endif
  }

#ifdef _LP64
  // Prefetch settings

  // Prefetch interval for gc copy/scan == 9 dcache lines.  Derived from
  // 50-warehouse specjbb runs on a 2-way 1.8ghz opteron using a 4gb heap.
  // Tested intervals from 128 to 2048 in increments of 64 == one cache line.
  // 256 bytes (4 dcache lines) was the nearest runner-up to 576.

  // gc copy/scan is disabled if prefetchw isn't supported, because
  // Prefetch::write emits an inlined prefetchw on Linux.
  // Do not use the 3dnow prefetchw instruction.  It isn't supported on em64t.
  // The used prefetcht0 instruction works for both amd64 and em64t.

  if (FLAG_IS_DEFAULT(PrefetchCopyIntervalInBytes)) {
    FLAG_SET_DEFAULT(PrefetchCopyIntervalInBytes, 576);
  }
  if (FLAG_IS_DEFAULT(PrefetchScanIntervalInBytes)) {
    FLAG_SET_DEFAULT(PrefetchScanIntervalInBytes, 576);
  }
#endif

  if (FLAG_IS_DEFAULT(ContendedPaddingWidth) &&
     (cache_line_size > ContendedPaddingWidth))
     ContendedPaddingWidth = cache_line_size;

  // This machine allows unaligned memory accesses
  if (FLAG_IS_DEFAULT(UseUnalignedAccesses)) {
    FLAG_SET_DEFAULT(UseUnalignedAccesses, true);
  }

#ifndef PRODUCT
  if (log_is_enabled(Info, os, cpu)) {
    LogStream ls(Log(os, cpu)::info());
    outputStream* log = &ls;
    log->print_cr("Logical CPUs per core: %u",
                  logical_processors_per_package());
    log->print_cr("L1 data cache line size: %u", L1_data_cache_line_size());
    log->print("UseSSE=%d", UseSSE);
    if (UseAVX > 0) {
      log->print("  UseAVX=%d", UseAVX);
    }
    if (UseAES) {
      log->print("  UseAES=1");
    }
#ifdef COMPILER2
    if (MaxVectorSize > 0) {
      log->print("  MaxVectorSize=%d", (int) MaxVectorSize);
    }
#endif
    log->cr();
    log->print("Allocation");
    if (AllocatePrefetchStyle <= 0 || (UseSSE == 0 && !supports_3dnow_prefetch())) {
      log->print_cr(": no prefetching");
    } else {
      log->print(" prefetching: ");
      if (UseSSE == 0 && supports_3dnow_prefetch()) {
        log->print("PREFETCHW");
      } else if (UseSSE >= 1) {
        if (AllocatePrefetchInstr == 0) {
          log->print("PREFETCHNTA");
        } else if (AllocatePrefetchInstr == 1) {
          log->print("PREFETCHT0");
        } else if (AllocatePrefetchInstr == 2) {
          log->print("PREFETCHT2");
        } else if (AllocatePrefetchInstr == 3) {
          log->print("PREFETCHW");
        }
      }
      if (AllocatePrefetchLines > 1) {
        log->print_cr(" at distance %d, %d lines of %d bytes", AllocatePrefetchDistance, AllocatePrefetchLines, AllocatePrefetchStepSize);
      } else {
        log->print_cr(" at distance %d, one line of %d bytes", AllocatePrefetchDistance, AllocatePrefetchStepSize);
      }
    }

    if (PrefetchCopyIntervalInBytes > 0) {
      log->print_cr("PrefetchCopyIntervalInBytes %d", (int) PrefetchCopyIntervalInBytes);
    }
    if (PrefetchScanIntervalInBytes > 0) {
      log->print_cr("PrefetchScanIntervalInBytes %d", (int) PrefetchScanIntervalInBytes);
    }
    if (ContendedPaddingWidth > 0) {
      log->print_cr("ContendedPaddingWidth %d", (int) ContendedPaddingWidth);
    }
  }
#endif // !PRODUCT
  if (FLAG_IS_DEFAULT(UseSignumIntrinsic)) {
      FLAG_SET_DEFAULT(UseSignumIntrinsic, true);
  }
  if (FLAG_IS_DEFAULT(UseCopySignIntrinsic)) {
      FLAG_SET_DEFAULT(UseCopySignIntrinsic, true);
  }
}

void VM_Version::print_platform_virtualization_info(outputStream* st) {
  VirtualizationType vrt = VM_Version::get_detected_virtualization();
  if (vrt == XenHVM) {
    st->print_cr("Xen hardware-assisted virtualization detected");
  } else if (vrt == KVM) {
    st->print_cr("KVM virtualization detected");
  } else if (vrt == VMWare) {
    st->print_cr("VMWare virtualization detected");
    VirtualizationSupport::print_virtualization_info(st);
  } else if (vrt == HyperV) {
    st->print_cr("Hyper-V virtualization detected");
  } else if (vrt == HyperVRole) {
    st->print_cr("Hyper-V role detected");
  }
}

bool VM_Version::compute_has_intel_jcc_erratum() {
  if (!is_intel_family_core()) {
    // Only Intel CPUs are affected.
    return false;
  }
  // The following table of affected CPUs is based on the following document released by Intel:
  // https://www.intel.com/content/dam/support/us/en/documents/processors/mitigations-jump-conditional-code-erratum.pdf
  switch (_model) {
  case 0x8E:
    // 06_8EH | 9 | 8th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Amber Lake Y
    // 06_8EH | 9 | 7th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Kaby Lake U
    // 06_8EH | 9 | 7th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Kaby Lake U 23e
    // 06_8EH | 9 | 7th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Kaby Lake Y
    // 06_8EH | A | 8th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Coffee Lake U43e
    // 06_8EH | B | 8th Generation Intel(R) Core(TM) Processors based on microarchitecture code name Whiskey Lake U
    // 06_8EH | C | 8th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Amber Lake Y
    // 06_8EH | C | 10th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Comet Lake U42
    // 06_8EH | C | 8th Generation Intel(R) Core(TM) Processors based on microarchitecture code name Whiskey Lake U
    return _stepping == 0x9 || _stepping == 0xA || _stepping == 0xB || _stepping == 0xC;
  case 0x4E:
    // 06_4E  | 3 | 6th Generation Intel(R) Core(TM) Processors based on microarchitecture code name Skylake U
    // 06_4E  | 3 | 6th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Skylake U23e
    // 06_4E  | 3 | 6th Generation Intel(R) Core(TM) Processors based on microarchitecture code name Skylake Y
    return _stepping == 0x3;
  case 0x55:
    // 06_55H | 4 | Intel(R) Xeon(R) Processor D Family based on microarchitecture code name Skylake D, Bakerville
    // 06_55H | 4 | Intel(R) Xeon(R) Scalable Processors based on microarchitecture code name Skylake Server
    // 06_55H | 4 | Intel(R) Xeon(R) Processor W Family based on microarchitecture code name Skylake W
    // 06_55H | 4 | Intel(R) Core(TM) X-series Processors based on microarchitecture code name Skylake X
    // 06_55H | 4 | Intel(R) Xeon(R) Processor E3 v5 Family based on microarchitecture code name Skylake Xeon E3
    // 06_55  | 7 | 2nd Generation Intel(R) Xeon(R) Scalable Processors based on microarchitecture code name Cascade Lake (server)
    return _stepping == 0x4 || _stepping == 0x7;
  case 0x5E:
    // 06_5E  | 3 | 6th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Skylake H
    // 06_5E  | 3 | 6th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Skylake S
    return _stepping == 0x3;
  case 0x9E:
    // 06_9EH | 9 | 8th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Kaby Lake G
    // 06_9EH | 9 | 7th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Kaby Lake H
    // 06_9EH | 9 | 7th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Kaby Lake S
    // 06_9EH | 9 | Intel(R) Core(TM) X-series Processors based on microarchitecture code name Kaby Lake X
    // 06_9EH | 9 | Intel(R) Xeon(R) Processor E3 v6 Family Kaby Lake Xeon E3
    // 06_9EH | A | 8th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Coffee Lake H
    // 06_9EH | A | 8th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Coffee Lake S
    // 06_9EH | A | 8th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Coffee Lake S (6+2) x/KBP
    // 06_9EH | A | Intel(R) Xeon(R) Processor E Family based on microarchitecture code name Coffee Lake S (6+2)
    // 06_9EH | A | Intel(R) Xeon(R) Processor E Family based on microarchitecture code name Coffee Lake S (4+2)
    // 06_9EH | B | 8th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Coffee Lake S (4+2)
    // 06_9EH | B | Intel(R) Celeron(R) Processor G Series based on microarchitecture code name Coffee Lake S (4+2)
    // 06_9EH | D | 9th Generation Intel(R) Core(TM) Processor Family based on microarchitecturecode name Coffee Lake H (8+2)
    // 06_9EH | D | 9th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Coffee Lake S (8+2)
    return _stepping == 0x9 || _stepping == 0xA || _stepping == 0xB || _stepping == 0xD;
  case 0xA5:
    // Not in Intel documentation.
    // 06_A5H |    | 10th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Comet Lake S/H
    return true;
  case 0xA6:
    // 06_A6H | 0  | 10th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Comet Lake U62
    return _stepping == 0x0;
  case 0xAE:
    // 06_AEH | A | 8th Generation Intel(R) Core(TM) Processor Family based on microarchitecture code name Kaby Lake Refresh U (4+2)
    return _stepping == 0xA;
  default:
    // If we are running on another intel machine not recognized in the table, we are okay.
    return false;
  }
}

// On Xen, the cpuid instruction returns
//  eax / registers[0]: Version of Xen
//  ebx / registers[1]: chars 'XenV'
//  ecx / registers[2]: chars 'MMXe'
//  edx / registers[3]: chars 'nVMM'
//
// On KVM / VMWare / MS Hyper-V, the cpuid instruction returns
//  ebx / registers[1]: chars 'KVMK' / 'VMwa' / 'Micr'
//  ecx / registers[2]: chars 'VMKV' / 'reVM' / 'osof'
//  edx / registers[3]: chars 'M'    / 'ware' / 't Hv'
//
// more information :
// https://kb.vmware.com/s/article/1009458
//
void VM_Version::check_virtualizations() {
  uint32_t registers[4] = {0};
  char signature[13] = {0};

  // Xen cpuid leaves can be found 0x100 aligned boundary starting
  // from 0x40000000 until 0x40010000.
  //   https://lists.linuxfoundation.org/pipermail/virtualization/2012-May/019974.html
  for (int leaf = 0x40000000; leaf < 0x40010000; leaf += 0x100) {
    detect_virt_stub(leaf, registers);
    memcpy(signature, &registers[1], 12);

    if (strncmp("VMwareVMware", signature, 12) == 0) {
      Abstract_VM_Version::_detected_virtualization = VMWare;
      // check for extended metrics from guestlib
      VirtualizationSupport::initialize();
    } else if (strncmp("Microsoft Hv", signature, 12) == 0) {
      Abstract_VM_Version::_detected_virtualization = HyperV;
#ifdef _WINDOWS
      // CPUID leaf 0x40000007 is available to the root partition only.
      // See Hypervisor Top Level Functional Specification section 2.4.8 for more details.
      //   https://github.com/MicrosoftDocs/Virtualization-Documentation/raw/master/tlfs/Hypervisor%20Top%20Level%20Functional%20Specification%20v6.0b.pdf
      detect_virt_stub(0x40000007, registers);
      if ((registers[0] != 0x0) ||
          (registers[1] != 0x0) ||
          (registers[2] != 0x0) ||
          (registers[3] != 0x0)) {
        Abstract_VM_Version::_detected_virtualization = HyperVRole;
      }
#endif
    } else if (strncmp("KVMKVMKVM", signature, 9) == 0) {
      Abstract_VM_Version::_detected_virtualization = KVM;
    } else if (strncmp("XenVMMXenVMM", signature, 12) == 0) {
      Abstract_VM_Version::_detected_virtualization = XenHVM;
    }
  }
}

#ifdef COMPILER2
// Determine if it's running on Cascade Lake using default options.
bool VM_Version::is_default_intel_cascade_lake() {
  return FLAG_IS_DEFAULT(UseAVX) &&
         FLAG_IS_DEFAULT(MaxVectorSize) &&
         UseAVX > 2 &&
         is_intel_cascade_lake();
}
#endif

bool VM_Version::is_intel_cascade_lake() {
  return is_intel_skylake() && _stepping >= 5;
}

// avx3_threshold() sets the threshold at which 64-byte instructions are used
// for implementing the array copy and clear operations.
// The Intel platforms that supports the serialize instruction
// has improved implementation of 64-byte load/stores and so the default
// threshold is set to 0 for these platforms.
int VM_Version::avx3_threshold() {
  return (is_intel_family_core() &&
          supports_serialize() &&
          FLAG_IS_DEFAULT(AVX3Threshold)) ? 0 : AVX3Threshold;
}

static bool _vm_version_initialized = false;

void VM_Version::initialize() {
  ResourceMark rm;
  // Making this stub must be FIRST use of assembler
  stub_blob = BufferBlob::create("VM_Version stub", stub_size);
  if (stub_blob == nullptr) {
    vm_exit_during_initialization("Unable to allocate stub for VM_Version");
  }
  CodeBuffer c(stub_blob);
  VM_Version_StubGenerator g(&c);

  get_cpu_info_stub = CAST_TO_FN_PTR(get_cpu_info_stub_t,
                                     g.generate_get_cpu_info());
  detect_virt_stub = CAST_TO_FN_PTR(detect_virt_stub_t,
                                     g.generate_detect_virt());

  get_processor_features();

  LP64_ONLY(Assembler::precompute_instructions();)

  if (VM_Version::supports_hv()) { // Supports hypervisor
    check_virtualizations();
  }
  _vm_version_initialized = true;
}

typedef enum {
   CPU_FAMILY_8086_8088  = 0,
   CPU_FAMILY_INTEL_286  = 2,
   CPU_FAMILY_INTEL_386  = 3,
   CPU_FAMILY_INTEL_486  = 4,
   CPU_FAMILY_PENTIUM    = 5,
   CPU_FAMILY_PENTIUMPRO = 6,    // Same family several models
   CPU_FAMILY_PENTIUM_4  = 0xF
} FamilyFlag;

typedef enum {
  RDTSCP_FLAG  = 0x08000000, // bit 27
  INTEL64_FLAG = 0x20000000  // bit 29
} _featureExtendedEdxFlag;

typedef enum {
   FPU_FLAG     = 0x00000001,
   VME_FLAG     = 0x00000002,
   DE_FLAG      = 0x00000004,
   PSE_FLAG     = 0x00000008,
   TSC_FLAG     = 0x00000010,
   MSR_FLAG     = 0x00000020,
   PAE_FLAG     = 0x00000040,
   MCE_FLAG     = 0x00000080,
   CX8_FLAG     = 0x00000100,
   APIC_FLAG    = 0x00000200,
   SEP_FLAG     = 0x00000800,
   MTRR_FLAG    = 0x00001000,
   PGE_FLAG     = 0x00002000,
   MCA_FLAG     = 0x00004000,
   CMOV_FLAG    = 0x00008000,
   PAT_FLAG     = 0x00010000,
   PSE36_FLAG   = 0x00020000,
   PSNUM_FLAG   = 0x00040000,
   CLFLUSH_FLAG = 0x00080000,
   DTS_FLAG     = 0x00200000,
   ACPI_FLAG    = 0x00400000,
   MMX_FLAG     = 0x00800000,
   FXSR_FLAG    = 0x01000000,
   SSE_FLAG     = 0x02000000,
   SSE2_FLAG    = 0x04000000,
   SS_FLAG      = 0x08000000,
   HTT_FLAG     = 0x10000000,
   TM_FLAG      = 0x20000000
} FeatureEdxFlag;

static BufferBlob* cpuid_brand_string_stub_blob;
static const int   cpuid_brand_string_stub_size = 550;

extern "C" {
  typedef void (*getCPUIDBrandString_stub_t)(void*);
}

static getCPUIDBrandString_stub_t getCPUIDBrandString_stub = nullptr;

// VM_Version statics
enum {
  ExtendedFamilyIdLength_INTEL = 16,
  ExtendedFamilyIdLength_AMD   = 24
};

const size_t VENDOR_LENGTH = 13;
const size_t CPU_EBS_MAX_LENGTH = (3 * 4 * 4 + 1);
static char* _cpu_brand_string = nullptr;
static int64_t _max_qualified_cpu_frequency = 0;

static int _no_of_threads = 0;
static int _no_of_cores = 0;

const char* const _family_id_intel[ExtendedFamilyIdLength_INTEL] = {
  "8086/8088",
  "",
  "286",
  "386",
  "486",
  "Pentium",
  "Pentium Pro",   //or Pentium-M/Woodcrest depending on model
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "Pentium 4"
};

const char* const _family_id_amd[ExtendedFamilyIdLength_AMD] = {
  "",
  "",
  "",
  "",
  "5x86",
  "K5/K6",
  "Athlon/AthlonXP",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "Opteron/Athlon64",
  "Opteron QC/Phenom",  // Barcelona et.al.
  "",
  "",
  "",
  "",
  "",
  "",
  "Zen"
};
// Partially from Intel 64 and IA-32 Architecture Software Developer's Manual,
// September 2013, Vol 3C Table 35-1
const char* const _model_id_pentium_pro[] = {
  "",
  "Pentium Pro",
  "",
  "Pentium II model 3",
  "",
  "Pentium II model 5/Xeon/Celeron",
  "Celeron",
  "Pentium III/Pentium III Xeon",
  "Pentium III/Pentium III Xeon",
  "Pentium M model 9",    // Yonah
  "Pentium III, model A",
  "Pentium III, model B",
  "",
  "Pentium M model D",    // Dothan
  "",
  "Core 2",               // 0xf Woodcrest/Conroe/Merom/Kentsfield/Clovertown
  "",
  "",
  "",
  "",
  "",
  "",
  "Celeron",              // 0x16 Celeron 65nm
  "Core 2",               // 0x17 Penryn / Harpertown
  "",
  "",
  "Core i7",              // 0x1A CPU_MODEL_NEHALEM_EP
  "Atom",                 // 0x1B Z5xx series Silverthorn
  "",
  "Core 2",               // 0x1D Dunnington (6-core)
  "Nehalem",              // 0x1E CPU_MODEL_NEHALEM
  "",
  "",
  "",
  "",
  "",
  "",
  "Westmere",             // 0x25 CPU_MODEL_WESTMERE
  "",
  "",
  "",                     // 0x28
  "",
  "Sandy Bridge",         // 0x2a "2nd Generation Intel Core i7, i5, i3"
  "",
  "Westmere-EP",          // 0x2c CPU_MODEL_WESTMERE_EP
  "Sandy Bridge-EP",      // 0x2d CPU_MODEL_SANDYBRIDGE_EP
  "Nehalem-EX",           // 0x2e CPU_MODEL_NEHALEM_EX
  "Westmere-EX",          // 0x2f CPU_MODEL_WESTMERE_EX
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "Ivy Bridge",           // 0x3a
  "",
  "Haswell",              // 0x3c "4th Generation Intel Core Processor"
  "",                     // 0x3d "Next Generation Intel Core Processor"
  "Ivy Bridge-EP",        // 0x3e "Next Generation Intel Xeon Processor E7 Family"
  "",                     // 0x3f "Future Generation Intel Xeon Processor"
  "",
  "",
  "",
  "",
  "",
  "Haswell",              // 0x45 "4th Generation Intel Core Processor"
  "Haswell",              // 0x46 "4th Generation Intel Core Processor"
  nullptr
};

/* Brand ID is for back compatibility
 * Newer CPUs uses the extended brand string */
const char* const _brand_id[] = {
  "",
  "Celeron processor",
  "Pentium III processor",
  "Intel Pentium III Xeon processor",
  "",
  "",
  "",
  "",
  "Intel Pentium 4 processor",
  nullptr
};


const char* const _feature_edx_id[] = {
  "On-Chip FPU",
  "Virtual Mode Extensions",
  "Debugging Extensions",
  "Page Size Extensions",
  "Time Stamp Counter",
  "Model Specific Registers",
  "Physical Address Extension",
  "Machine Check Exceptions",
  "CMPXCHG8B Instruction",
  "On-Chip APIC",
  "",
  "Fast System Call",
  "Memory Type Range Registers",
  "Page Global Enable",
  "Machine Check Architecture",
  "Conditional Mov Instruction",
  "Page Attribute Table",
  "36-bit Page Size Extension",
  "Processor Serial Number",
  "CLFLUSH Instruction",
  "",
  "Debug Trace Store feature",
  "ACPI registers in MSR space",
  "Intel Architecture MMX Technology",
  "Fast Float Point Save and Restore",
  "Streaming SIMD extensions",
  "Streaming SIMD extensions 2",
  "Self-Snoop",
  "Hyper Threading",
  "Thermal Monitor",
  "",
  "Pending Break Enable"
};

const char* const _feature_extended_edx_id[] = {
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "SYSCALL/SYSRET",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "Execute Disable Bit",
  "",
  "",
  "",
  "",
  "",
  "",
  "RDTSCP",
  "",
  "Intel 64 Architecture",
  "",
  ""
};

const char* const _feature_ecx_id[] = {
  "Streaming SIMD Extensions 3",
  "PCLMULQDQ",
  "64-bit DS Area",
  "MONITOR/MWAIT instructions",
  "CPL Qualified Debug Store",
  "Virtual Machine Extensions",
  "Safer Mode Extensions",
  "Enhanced Intel SpeedStep technology",
  "Thermal Monitor 2",
  "Supplemental Streaming SIMD Extensions 3",
  "L1 Context ID",
  "",
  "Fused Multiply-Add",
  "CMPXCHG16B",
  "xTPR Update Control",
  "Perfmon and Debug Capability",
  "",
  "Process-context identifiers",
  "Direct Cache Access",
  "Streaming SIMD extensions 4.1",
  "Streaming SIMD extensions 4.2",
  "x2APIC",
  "MOVBE",
  "Popcount instruction",
  "TSC-Deadline",
  "AESNI",
  "XSAVE",
  "OSXSAVE",
  "AVX",
  "F16C",
  "RDRAND",
  ""
};

const char* const _feature_extended_ecx_id[] = {
  "LAHF/SAHF instruction support",
  "Core multi-processor legacy mode",
  "",
  "",
  "",
  "Advanced Bit Manipulations: LZCNT",
  "SSE4A: MOVNTSS, MOVNTSD, EXTRQ, INSERTQ",
  "Misaligned SSE mode",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  ""
};

void VM_Version::initialize_tsc(void) {
  ResourceMark rm;

  cpuid_brand_string_stub_blob = BufferBlob::create("getCPUIDBrandString_stub", cpuid_brand_string_stub_size);
  if (cpuid_brand_string_stub_blob == nullptr) {
    vm_exit_during_initialization("Unable to allocate getCPUIDBrandString_stub");
  }
  CodeBuffer c(cpuid_brand_string_stub_blob);
  VM_Version_StubGenerator g(&c);
  getCPUIDBrandString_stub = CAST_TO_FN_PTR(getCPUIDBrandString_stub_t,
                                   g.generate_getCPUIDBrandString());
}

const char* VM_Version::cpu_model_description(void) {
  uint32_t cpu_family = extended_cpu_family();
  uint32_t cpu_model = extended_cpu_model();
  const char* model = nullptr;

  if (cpu_family == CPU_FAMILY_PENTIUMPRO) {
    for (uint32_t i = 0; i <= cpu_model; i++) {
      model = _model_id_pentium_pro[i];
      if (model == nullptr) {
        break;
      }
    }
  }
  return model;
}

const char* VM_Version::cpu_brand_string(void) {
  if (_cpu_brand_string == nullptr) {
    _cpu_brand_string = NEW_C_HEAP_ARRAY_RETURN_NULL(char, CPU_EBS_MAX_LENGTH, mtInternal);
    if (nullptr == _cpu_brand_string) {
      return nullptr;
    }
    int ret_val = cpu_extended_brand_string(_cpu_brand_string, CPU_EBS_MAX_LENGTH);
    if (ret_val != OS_OK) {
      FREE_C_HEAP_ARRAY(char, _cpu_brand_string);
      _cpu_brand_string = nullptr;
    }
  }
  return _cpu_brand_string;
}

const char* VM_Version::cpu_brand(void) {
  const char*  brand  = nullptr;

  if ((_cpuid_info.std_cpuid1_ebx.value & 0xFF) > 0) {
    int brand_num = _cpuid_info.std_cpuid1_ebx.value & 0xFF;
    brand = _brand_id[0];
    for (int i = 0; brand != nullptr && i <= brand_num; i += 1) {
      brand = _brand_id[i];
    }
  }
  return brand;
}

bool VM_Version::cpu_is_em64t(void) {
  return ((_cpuid_info.ext_cpuid1_edx.value & INTEL64_FLAG) == INTEL64_FLAG);
}

bool VM_Version::is_netburst(void) {
  return (is_intel() && (extended_cpu_family() == CPU_FAMILY_PENTIUM_4));
}

bool VM_Version::supports_tscinv_ext(void) {
  if (!supports_tscinv_bit()) {
    return false;
  }

  if (is_intel()) {
    return true;
  }

  if (is_amd()) {
    return !is_amd_Barcelona();
  }

  if (is_hygon()) {
    return true;
  }

  return false;
}

void VM_Version::resolve_cpu_information_details(void) {

  // in future we want to base this information on proper cpu
  // and cache topology enumeration such as:
  // Intel 64 Architecture Processor Topology Enumeration
  // which supports system cpu and cache topology enumeration
  // either using 2xAPICIDs or initial APICIDs

  // currently only rough cpu information estimates
  // which will not necessarily reflect the exact configuration of the system

  // this is the number of logical hardware threads
  // visible to the operating system
  _no_of_threads = os::processor_count();

  // find out number of threads per cpu package
  int threads_per_package = threads_per_core() * cores_per_cpu();

  // use amount of threads visible to the process in order to guess number of sockets
  _no_of_sockets = _no_of_threads / threads_per_package;

  // process might only see a subset of the total number of threads
  // from a single processor package. Virtualization/resource management for example.
  // If so then just write a hard 1 as num of pkgs.
  if (0 == _no_of_sockets) {
    _no_of_sockets = 1;
  }

  // estimate the number of cores
  _no_of_cores = cores_per_cpu() * _no_of_sockets;
}


const char* VM_Version::cpu_family_description(void) {
  int cpu_family_id = extended_cpu_family();
  if (is_amd()) {
    if (cpu_family_id < ExtendedFamilyIdLength_AMD) {
      return _family_id_amd[cpu_family_id];
    }
  }
  if (is_intel()) {
    if (cpu_family_id == CPU_FAMILY_PENTIUMPRO) {
      return cpu_model_description();
    }
    if (cpu_family_id < ExtendedFamilyIdLength_INTEL) {
      return _family_id_intel[cpu_family_id];
    }
  }
  if (is_hygon()) {
    return "Dhyana";
  }
  return "Unknown x86";
}

int VM_Version::cpu_type_description(char* const buf, size_t buf_len) {
  assert(buf != nullptr, "buffer is null!");
  assert(buf_len >= CPU_TYPE_DESC_BUF_SIZE, "buffer len should at least be == CPU_TYPE_DESC_BUF_SIZE!");

  const char* cpu_type = nullptr;
  const char* x64 = nullptr;

  if (is_intel()) {
    cpu_type = "Intel";
    x64 = cpu_is_em64t() ? " Intel64" : "";
  } else if (is_amd()) {
    cpu_type = "AMD";
    x64 = cpu_is_em64t() ? " AMD64" : "";
  } else if (is_hygon()) {
    cpu_type = "Hygon";
    x64 = cpu_is_em64t() ? " AMD64" : "";
  } else {
    cpu_type = "Unknown x86";
    x64 = cpu_is_em64t() ? " x86_64" : "";
  }

  jio_snprintf(buf, buf_len, "%s %s%s SSE SSE2%s%s%s%s%s%s%s%s",
    cpu_type,
    cpu_family_description(),
    supports_ht() ? " (HT)" : "",
    supports_sse3() ? " SSE3" : "",
    supports_ssse3() ? " SSSE3" : "",
    supports_sse4_1() ? " SSE4.1" : "",
    supports_sse4_2() ? " SSE4.2" : "",
    supports_sse4a() ? " SSE4A" : "",
    is_netburst() ? " Netburst" : "",
    is_intel_family_core() ? " Core" : "",
    x64);

  return OS_OK;
}

int VM_Version::cpu_extended_brand_string(char* const buf, size_t buf_len) {
  assert(buf != nullptr, "buffer is null!");
  assert(buf_len >= CPU_EBS_MAX_LENGTH, "buffer len should at least be == CPU_EBS_MAX_LENGTH!");
  assert(getCPUIDBrandString_stub != nullptr, "not initialized");

  // invoke newly generated asm code to fetch CPU Brand String
  getCPUIDBrandString_stub(&_cpuid_info);

  // fetch results into buffer
  *((uint32_t*) &buf[0])  = _cpuid_info.proc_name_0;
  *((uint32_t*) &buf[4])  = _cpuid_info.proc_name_1;
  *((uint32_t*) &buf[8])  = _cpuid_info.proc_name_2;
  *((uint32_t*) &buf[12]) = _cpuid_info.proc_name_3;
  *((uint32_t*) &buf[16]) = _cpuid_info.proc_name_4;
  *((uint32_t*) &buf[20]) = _cpuid_info.proc_name_5;
  *((uint32_t*) &buf[24]) = _cpuid_info.proc_name_6;
  *((uint32_t*) &buf[28]) = _cpuid_info.proc_name_7;
  *((uint32_t*) &buf[32]) = _cpuid_info.proc_name_8;
  *((uint32_t*) &buf[36]) = _cpuid_info.proc_name_9;
  *((uint32_t*) &buf[40]) = _cpuid_info.proc_name_10;
  *((uint32_t*) &buf[44]) = _cpuid_info.proc_name_11;

  return OS_OK;
}

size_t VM_Version::cpu_write_support_string(char* const buf, size_t buf_len) {
  guarantee(buf != nullptr, "buffer is null!");
  guarantee(buf_len > 0, "buffer len not enough!");

  unsigned int flag = 0;
  unsigned int fi = 0;
  size_t       written = 0;
  const char*  prefix = "";

#define WRITE_TO_BUF(string)                                                          \
  {                                                                                   \
    int res = jio_snprintf(&buf[written], buf_len - written, "%s%s", prefix, string); \
    if (res < 0) {                                                                    \
      return buf_len - 1;                                                             \
    }                                                                                 \
    written += res;                                                                   \
    if (prefix[0] == '\0') {                                                          \
      prefix = ", ";                                                                  \
    }                                                                                 \
  }

  for (flag = 1, fi = 0; flag <= 0x20000000 ; flag <<= 1, fi++) {
    if (flag == HTT_FLAG && (((_cpuid_info.std_cpuid1_ebx.value >> 16) & 0xff) <= 1)) {
      continue; /* no hyperthreading */
    } else if (flag == SEP_FLAG && (cpu_family() == CPU_FAMILY_PENTIUMPRO && ((_cpuid_info.std_cpuid1_eax.value & 0xff) < 0x33))) {
      continue; /* no fast system call */
    }
    if ((_cpuid_info.std_cpuid1_edx.value & flag) && strlen(_feature_edx_id[fi]) > 0) {
      WRITE_TO_BUF(_feature_edx_id[fi]);
    }
  }

  for (flag = 1, fi = 0; flag <= 0x20000000; flag <<= 1, fi++) {
    if ((_cpuid_info.std_cpuid1_ecx.value & flag) && strlen(_feature_ecx_id[fi]) > 0) {
      WRITE_TO_BUF(_feature_ecx_id[fi]);
    }
  }

  for (flag = 1, fi = 0; flag <= 0x20000000 ; flag <<= 1, fi++) {
    if ((_cpuid_info.ext_cpuid1_ecx.value & flag) && strlen(_feature_extended_ecx_id[fi]) > 0) {
      WRITE_TO_BUF(_feature_extended_ecx_id[fi]);
    }
  }

  for (flag = 1, fi = 0; flag <= 0x20000000; flag <<= 1, fi++) {
    if ((_cpuid_info.ext_cpuid1_edx.value & flag) && strlen(_feature_extended_edx_id[fi]) > 0) {
      WRITE_TO_BUF(_feature_extended_edx_id[fi]);
    }
  }

  if (supports_tscinv_bit()) {
      WRITE_TO_BUF("Invariant TSC");
  }

  return written;
}

/**
 * Write a detailed description of the cpu to a given buffer, including
 * feature set.
 */
int VM_Version::cpu_detailed_description(char* const buf, size_t buf_len) {
  assert(buf != nullptr, "buffer is null!");
  assert(buf_len >= CPU_DETAILED_DESC_BUF_SIZE, "buffer len should at least be == CPU_DETAILED_DESC_BUF_SIZE!");

  static const char* unknown = "<unknown>";
  char               vendor_id[VENDOR_LENGTH];
  const char*        family = nullptr;
  const char*        model = nullptr;
  const char*        brand = nullptr;
  int                outputLen = 0;

  family = cpu_family_description();
  if (family == nullptr) {
    family = unknown;
  }

  model = cpu_model_description();
  if (model == nullptr) {
    model = unknown;
  }

  brand = cpu_brand_string();

  if (brand == nullptr) {
    brand = cpu_brand();
    if (brand == nullptr) {
      brand = unknown;
    }
  }

  *((uint32_t*) &vendor_id[0]) = _cpuid_info.std_vendor_name_0;
  *((uint32_t*) &vendor_id[4]) = _cpuid_info.std_vendor_name_2;
  *((uint32_t*) &vendor_id[8]) = _cpuid_info.std_vendor_name_1;
  vendor_id[VENDOR_LENGTH-1] = '\0';

  outputLen = jio_snprintf(buf, buf_len, "Brand: %s, Vendor: %s\n"
    "Family: %s (0x%x), Model: %s (0x%x), Stepping: 0x%x\n"
    "Ext. family: 0x%x, Ext. model: 0x%x, Type: 0x%x, Signature: 0x%8.8x\n"
    "Features: ebx: 0x%8.8x, ecx: 0x%8.8x, edx: 0x%8.8x\n"
    "Ext. features: eax: 0x%8.8x, ebx: 0x%8.8x, ecx: 0x%8.8x, edx: 0x%8.8x\n"
    "Supports: ",
    brand,
    vendor_id,
    family,
    extended_cpu_family(),
    model,
    extended_cpu_model(),
    cpu_stepping(),
    _cpuid_info.std_cpuid1_eax.bits.ext_family,
    _cpuid_info.std_cpuid1_eax.bits.ext_model,
    _cpuid_info.std_cpuid1_eax.bits.proc_type,
    _cpuid_info.std_cpuid1_eax.value,
    _cpuid_info.std_cpuid1_ebx.value,
    _cpuid_info.std_cpuid1_ecx.value,
    _cpuid_info.std_cpuid1_edx.value,
    _cpuid_info.ext_cpuid1_eax,
    _cpuid_info.ext_cpuid1_ebx,
    _cpuid_info.ext_cpuid1_ecx,
    _cpuid_info.ext_cpuid1_edx);

  if (outputLen < 0 || (size_t) outputLen >= buf_len - 1) {
    if (buf_len > 0) { buf[buf_len-1] = '\0'; }
    return OS_ERR;
  }

  cpu_write_support_string(&buf[outputLen], buf_len - outputLen);

  return OS_OK;
}


// Fill in Abstract_VM_Version statics
void VM_Version::initialize_cpu_information() {
  assert(_vm_version_initialized, "should have initialized VM_Version long ago");
  assert(!_initialized, "shouldn't be initialized yet");
  resolve_cpu_information_details();

  // initialize cpu_name and cpu_desc
  cpu_type_description(_cpu_name, CPU_TYPE_DESC_BUF_SIZE);
  cpu_detailed_description(_cpu_desc, CPU_DETAILED_DESC_BUF_SIZE);
  _initialized = true;
}

/**
 *  For information about extracting the frequency from the cpu brand string, please see:
 *
 *    Intel Processor Identification and the CPUID Instruction
 *    Application Note 485
 *    May 2012
 *
 * The return value is the frequency in Hz.
 */
int64_t VM_Version::max_qualified_cpu_freq_from_brand_string(void) {
  const char* const brand_string = cpu_brand_string();
  if (brand_string == nullptr) {
    return 0;
  }
  const int64_t MEGA = 1000000;
  int64_t multiplier = 0;
  int64_t frequency = 0;
  uint8_t idx = 0;
  // The brand string buffer is at most 48 bytes.
  // -2 is to prevent buffer overrun when looking for y in yHz, as z is +2 from y.
  for (; idx < 48-2; ++idx) {
    // Format is either "x.xxyHz" or "xxxxyHz", where y=M, G, T and x are digits.
    // Search brand string for "yHz" where y is M, G, or T.
    if (brand_string[idx+1] == 'H' && brand_string[idx+2] == 'z') {
      if (brand_string[idx] == 'M') {
        multiplier = MEGA;
      } else if (brand_string[idx] == 'G') {
        multiplier = MEGA * 1000;
      } else if (brand_string[idx] == 'T') {
        multiplier = MEGA * MEGA;
      }
      break;
    }
  }
  if (multiplier > 0) {
    // Compute frequency (in Hz) from brand string.
    if (brand_string[idx-3] == '.') { // if format is "x.xx"
      frequency =  (brand_string[idx-4] - '0') * multiplier;
      frequency += (brand_string[idx-2] - '0') * multiplier / 10;
      frequency += (brand_string[idx-1] - '0') * multiplier / 100;
    } else { // format is "xxxx"
      frequency =  (brand_string[idx-4] - '0') * 1000;
      frequency += (brand_string[idx-3] - '0') * 100;
      frequency += (brand_string[idx-2] - '0') * 10;
      frequency += (brand_string[idx-1] - '0');
      frequency *= multiplier;
    }
  }
  return frequency;
}


int64_t VM_Version::maximum_qualified_cpu_frequency(void) {
  if (_max_qualified_cpu_frequency == 0) {
    _max_qualified_cpu_frequency = max_qualified_cpu_freq_from_brand_string();
  }
  return _max_qualified_cpu_frequency;
}

uint64_t VM_Version::CpuidInfo::feature_flags() const {
  uint64_t result = 0;
  if (std_cpuid1_edx.bits.cmpxchg8 != 0)
    result |= CPU_CX8;
  if (std_cpuid1_edx.bits.cmov != 0)
    result |= CPU_CMOV;
  if (std_cpuid1_edx.bits.clflush != 0)
    result |= CPU_FLUSH;
#ifdef _LP64
  // clflush should always be available on x86_64
  // if not we are in real trouble because we rely on it
  // to flush the code cache.
  assert ((result & CPU_FLUSH) != 0, "clflush should be available");
#endif
  if (std_cpuid1_edx.bits.fxsr != 0 || (is_amd_family() &&
      ext_cpuid1_edx.bits.fxsr != 0))
    result |= CPU_FXSR;
  // HT flag is set for multi-core processors also.
  if (threads_per_core() > 1)
    result |= CPU_HT;
  if (std_cpuid1_edx.bits.mmx != 0 || (is_amd_family() &&
      ext_cpuid1_edx.bits.mmx != 0))
    result |= CPU_MMX;
  if (std_cpuid1_edx.bits.sse != 0)
    result |= CPU_SSE;
  if (std_cpuid1_edx.bits.sse2 != 0)
    result |= CPU_SSE2;
  if (std_cpuid1_ecx.bits.sse3 != 0)
    result |= CPU_SSE3;
  if (std_cpuid1_ecx.bits.ssse3 != 0)
    result |= CPU_SSSE3;
  if (std_cpuid1_ecx.bits.sse4_1 != 0)
    result |= CPU_SSE4_1;
  if (std_cpuid1_ecx.bits.sse4_2 != 0)
    result |= CPU_SSE4_2;
  if (std_cpuid1_ecx.bits.popcnt != 0)
    result |= CPU_POPCNT;
  if (std_cpuid1_ecx.bits.avx != 0 &&
      std_cpuid1_ecx.bits.osxsave != 0 &&
      xem_xcr0_eax.bits.sse != 0 &&
      xem_xcr0_eax.bits.ymm != 0) {
    result |= CPU_AVX;
    result |= CPU_VZEROUPPER;
    if (std_cpuid1_ecx.bits.f16c != 0)
      result |= CPU_F16C;
    if (sef_cpuid7_ebx.bits.avx2 != 0)
      result |= CPU_AVX2;
    if (sef_cpuid7_ebx.bits.avx512f != 0 &&
        xem_xcr0_eax.bits.opmask != 0 &&
        xem_xcr0_eax.bits.zmm512 != 0 &&
        xem_xcr0_eax.bits.zmm32 != 0) {
      result |= CPU_AVX512F;
      if (sef_cpuid7_ebx.bits.avx512cd != 0)
        result |= CPU_AVX512CD;
      if (sef_cpuid7_ebx.bits.avx512dq != 0)
        result |= CPU_AVX512DQ;
      if (sef_cpuid7_ebx.bits.avx512ifma != 0)
        result |= CPU_AVX512_IFMA;
      if (sef_cpuid7_ebx.bits.avx512pf != 0)
        result |= CPU_AVX512PF;
      if (sef_cpuid7_ebx.bits.avx512er != 0)
        result |= CPU_AVX512ER;
      if (sef_cpuid7_ebx.bits.avx512bw != 0)
        result |= CPU_AVX512BW;
      if (sef_cpuid7_ebx.bits.avx512vl != 0)
        result |= CPU_AVX512VL;
      if (sef_cpuid7_ecx.bits.avx512_vpopcntdq != 0)
        result |= CPU_AVX512_VPOPCNTDQ;
      if (sef_cpuid7_ecx.bits.avx512_vpclmulqdq != 0)
        result |= CPU_AVX512_VPCLMULQDQ;
      if (sef_cpuid7_ecx.bits.vaes != 0)
        result |= CPU_AVX512_VAES;
      if (sef_cpuid7_ecx.bits.gfni != 0)
        result |= CPU_GFNI;
      if (sef_cpuid7_ecx.bits.avx512_vnni != 0)
        result |= CPU_AVX512_VNNI;
      if (sef_cpuid7_ecx.bits.avx512_bitalg != 0)
        result |= CPU_AVX512_BITALG;
      if (sef_cpuid7_ecx.bits.avx512_vbmi != 0)
        result |= CPU_AVX512_VBMI;
      if (sef_cpuid7_ecx.bits.avx512_vbmi2 != 0)
        result |= CPU_AVX512_VBMI2;
    }
  }
  if (std_cpuid1_ecx.bits.hv != 0)
    result |= CPU_HV;
  if (sef_cpuid7_ebx.bits.bmi1 != 0)
    result |= CPU_BMI1;
  if (std_cpuid1_edx.bits.tsc != 0)
    result |= CPU_TSC;
  if (ext_cpuid7_edx.bits.tsc_invariance != 0)
    result |= CPU_TSCINV_BIT;
  if (std_cpuid1_ecx.bits.aes != 0)
    result |= CPU_AES;
  if (sef_cpuid7_ebx.bits.erms != 0)
    result |= CPU_ERMS;
  if (sef_cpuid7_edx.bits.fast_short_rep_mov != 0)
    result |= CPU_FSRM;
  if (std_cpuid1_ecx.bits.clmul != 0)
    result |= CPU_CLMUL;
  if (sef_cpuid7_ebx.bits.rtm != 0)
    result |= CPU_RTM;
  if (sef_cpuid7_ebx.bits.adx != 0)
     result |= CPU_ADX;
  if (sef_cpuid7_ebx.bits.bmi2 != 0)
    result |= CPU_BMI2;
  if (sef_cpuid7_ebx.bits.sha != 0)
    result |= CPU_SHA;
  if (std_cpuid1_ecx.bits.fma != 0)
    result |= CPU_FMA;
  if (sef_cpuid7_ebx.bits.clflushopt != 0)
    result |= CPU_FLUSHOPT;
  if (ext_cpuid1_edx.bits.rdtscp != 0)
    result |= CPU_RDTSCP;
  if (sef_cpuid7_ecx.bits.rdpid != 0)
    result |= CPU_RDPID;

  // AMD|Hygon features.
  if (is_amd_family()) {
    if ((ext_cpuid1_edx.bits.tdnow != 0) ||
        (ext_cpuid1_ecx.bits.prefetchw != 0))
      result |= CPU_3DNOW_PREFETCH;
    if (ext_cpuid1_ecx.bits.lzcnt != 0)
      result |= CPU_LZCNT;
    if (ext_cpuid1_ecx.bits.sse4a != 0)
      result |= CPU_SSE4A;
  }

  // Intel features.
  if (is_intel()) {
    if (ext_cpuid1_ecx.bits.lzcnt != 0) {
      result |= CPU_LZCNT;
    }
    if (ext_cpuid1_ecx.bits.prefetchw != 0) {
      result |= CPU_3DNOW_PREFETCH;
    }
    if (sef_cpuid7_ebx.bits.clwb != 0) {
      result |= CPU_CLWB;
    }
    if (sef_cpuid7_edx.bits.serialize != 0)
      result |= CPU_SERIALIZE;
  }

  // ZX features.
  if (is_zx()) {
    if (ext_cpuid1_ecx.bits.lzcnt != 0) {
      result |= CPU_LZCNT;
    }
    if (ext_cpuid1_ecx.bits.prefetchw != 0) {
      result |= CPU_3DNOW_PREFETCH;
    }
  }

  // Protection key features.
  if (sef_cpuid7_ecx.bits.pku != 0) {
    result |= CPU_PKU;
  }
  if (sef_cpuid7_ecx.bits.ospke != 0) {
    result |= CPU_OSPKE;
  }

  // Control flow enforcement (CET) features.
  if (sef_cpuid7_ecx.bits.cet_ss != 0) {
    result |= CPU_CET_SS;
  }
  if (sef_cpuid7_edx.bits.cet_ibt != 0) {
    result |= CPU_CET_IBT;
  }

  // Composite features.
  if (supports_tscinv_bit() &&
      ((is_amd_family() && !is_amd_Barcelona()) ||
       is_intel_tsc_synched_at_init())) {
    result |= CPU_TSCINV;
  }

  return result;
}

bool VM_Version::os_supports_avx_vectors() {
  bool retVal = false;
  int nreg = 2 LP64_ONLY(+2);
  if (supports_evex()) {
    // Verify that OS save/restore all bits of EVEX registers
    // during signal processing.
    retVal = true;
    for (int i = 0; i < 16 * nreg; i++) { // 64 bytes per zmm register
      if (_cpuid_info.zmm_save[i] != ymm_test_value()) {
        retVal = false;
        break;
      }
    }
  } else if (supports_avx()) {
    // Verify that OS save/restore all bits of AVX registers
    // during signal processing.
    retVal = true;
    for (int i = 0; i < 8 * nreg; i++) { // 32 bytes per ymm register
      if (_cpuid_info.ymm_save[i] != ymm_test_value()) {
        retVal = false;
        break;
      }
    }
    // zmm_save will be set on a EVEX enabled machine even if we choose AVX code gen
    if (retVal == false) {
      // Verify that OS save/restore all bits of EVEX registers
      // during signal processing.
      retVal = true;
      for (int i = 0; i < 16 * nreg; i++) { // 64 bytes per zmm register
        if (_cpuid_info.zmm_save[i] != ymm_test_value()) {
          retVal = false;
          break;
        }
      }
    }
  }
  return retVal;
}

uint VM_Version::cores_per_cpu() {
  uint result = 1;
  if (is_intel()) {
    bool supports_topology = supports_processor_topology();
    if (supports_topology) {
      result = _cpuid_info.tpl_cpuidB1_ebx.bits.logical_cpus /
               _cpuid_info.tpl_cpuidB0_ebx.bits.logical_cpus;
    }
    if (!supports_topology || result == 0) {
      result = (_cpuid_info.dcp_cpuid4_eax.bits.cores_per_cpu + 1);
    }
  } else if (is_amd_family()) {
    result = (_cpuid_info.ext_cpuid8_ecx.bits.cores_per_cpu + 1);
  } else if (is_zx()) {
    bool supports_topology = supports_processor_topology();
    if (supports_topology) {
      result = _cpuid_info.tpl_cpuidB1_ebx.bits.logical_cpus /
               _cpuid_info.tpl_cpuidB0_ebx.bits.logical_cpus;
    }
    if (!supports_topology || result == 0) {
      result = (_cpuid_info.dcp_cpuid4_eax.bits.cores_per_cpu + 1);
    }
  }
  return result;
}

uint VM_Version::threads_per_core() {
  uint result = 1;
  if (is_intel() && supports_processor_topology()) {
    result = _cpuid_info.tpl_cpuidB0_ebx.bits.logical_cpus;
  } else if (is_zx() && supports_processor_topology()) {
    result = _cpuid_info.tpl_cpuidB0_ebx.bits.logical_cpus;
  } else if (_cpuid_info.std_cpuid1_edx.bits.ht != 0) {
    if (cpu_family() >= 0x17) {
      result = _cpuid_info.ext_cpuid1E_ebx.bits.threads_per_core + 1;
    } else {
      result = _cpuid_info.std_cpuid1_ebx.bits.threads_per_cpu /
                 cores_per_cpu();
    }
  }
  return (result == 0 ? 1 : result);
}

uint VM_Version::L1_line_size() {
  uint result = 0;
  if (is_intel()) {
    result = (_cpuid_info.dcp_cpuid4_ebx.bits.L1_line_size + 1);
  } else if (is_amd_family()) {
    result = _cpuid_info.ext_cpuid5_ecx.bits.L1_line_size;
  } else if (is_zx()) {
    result = (_cpuid_info.dcp_cpuid4_ebx.bits.L1_line_size + 1);
  }
  if (result < 32) // not defined ?
    result = 32;   // 32 bytes by default on x86 and other x64
  return result;
}

bool VM_Version::is_intel_tsc_synched_at_init() {
  if (is_intel_family_core()) {
    uint32_t ext_model = extended_cpu_model();
    if (ext_model == CPU_MODEL_NEHALEM_EP     ||
        ext_model == CPU_MODEL_WESTMERE_EP    ||
        ext_model == CPU_MODEL_SANDYBRIDGE_EP ||
        ext_model == CPU_MODEL_IVYBRIDGE_EP) {
      // <= 2-socket invariant tsc support. EX versions are usually used
      // in > 2-socket systems and likely don't synchronize tscs at
      // initialization.
      // Code that uses tsc values must be prepared for them to arbitrarily
      // jump forward or backward.
      return true;
    }
  }
  return false;
}

int VM_Version::allocate_prefetch_distance(bool use_watermark_prefetch) {
  // Hardware prefetching (distance/size in bytes):
  // Pentium 3 -  64 /  32
  // Pentium 4 - 256 / 128
  // Athlon    -  64 /  32 ????
  // Opteron   - 128 /  64 only when 2 sequential cache lines accessed
  // Core      - 128 /  64
  //
  // Software prefetching (distance in bytes / instruction with best score):
  // Pentium 3 - 128 / prefetchnta
  // Pentium 4 - 512 / prefetchnta
  // Athlon    - 128 / prefetchnta
  // Opteron   - 256 / prefetchnta
  // Core      - 256 / prefetchnta
  // It will be used only when AllocatePrefetchStyle > 0

  if (is_amd_family()) { // AMD | Hygon
    if (supports_sse2()) {
      return 256; // Opteron
    } else {
      return 128; // Athlon
    }
  } else { // Intel
    if (supports_sse3() && cpu_family() == 6) {
      if (supports_sse4_2() && supports_ht()) { // Nehalem based cpus
        return 192;
      } else if (use_watermark_prefetch) { // watermark prefetching on Core
#ifdef _LP64
        return 384;
#else
        return 320;
#endif
      }
    }
    if (supports_sse2()) {
      if (cpu_family() == 6) {
        return 256; // Pentium M, Core, Core2
      } else {
        return 512; // Pentium 4
      }
    } else {
      return 128; // Pentium 3 (and all other old CPUs)
    }
  }
}

bool VM_Version::is_intrinsic_supported(vmIntrinsicID id) {
  assert(id != vmIntrinsics::_none, "must be a VM intrinsic");
  switch (id) {
  case vmIntrinsics::_floatToFloat16:
  case vmIntrinsics::_float16ToFloat:
    if (!supports_float16()) {
      return false;
    }
    break;
  default:
    break;
  }
  return true;
}
