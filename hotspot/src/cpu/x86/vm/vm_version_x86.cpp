/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "vm_version_x86.hpp"


int VM_Version::_cpu;
int VM_Version::_model;
int VM_Version::_stepping;
VM_Version::CpuidInfo VM_Version::_cpuid_info = { 0, };

// Address of instruction which causes SEGV
address VM_Version::_cpuinfo_segv_addr = 0;
// Address of instruction after the one which causes SEGV
address VM_Version::_cpuinfo_cont_addr = 0;

static BufferBlob* stub_blob;
static const int stub_size = 1000;

extern "C" {
  typedef void (*get_cpu_info_stub_t)(void*);
}
static get_cpu_info_stub_t get_cpu_info_stub = NULL;


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

    Label detect_486, cpu486, detect_586, std_cpuid1, std_cpuid4;
    Label sef_cpuid, ext_cpuid, ext_cpuid1, ext_cpuid5, ext_cpuid7, done, wrapup;
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

    //
    // Extended cpuid(0x80000000)
    //
    __ bind(ext_cpuid);
    __ movl(rax, 0x80000000);
    __ cpuid();
    __ cmpl(rax, 0x80000000);     // Is cpuid(0x80000001) supported?
    __ jcc(Assembler::belowEqual, done);
    __ cmpl(rax, 0x80000004);     // Is cpuid(0x80000005) supported?
    __ jccb(Assembler::belowEqual, ext_cpuid1);
    __ cmpl(rax, 0x80000006);     // Is cpuid(0x80000007) supported?
    __ jccb(Assembler::belowEqual, ext_cpuid5);
    __ cmpl(rax, 0x80000007);     // Is cpuid(0x80000008) supported?
    __ jccb(Assembler::belowEqual, ext_cpuid7);
    //
    // Extended cpuid(0x80000008)
    //
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
    // Generate SEGV here (reference through NULL)
    // and check upper YMM/ZMM bits after it.
    //
    intx saved_useavx = UseAVX;
    intx saved_usesse = UseSSE;
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

    // EVEX setup: run in lowest evex mode
    VM_Version::set_evex_cpuFeatures(); // Enable temporary to pass asserts
    UseAVX = 3;
    UseSSE = 2;
    // load value into all 64 bytes of zmm7 register
    __ movl(rcx, VM_Version::ymm_test_value());
    __ movdl(xmm0, rcx);
    __ movl(rcx, 0xffff);
    __ kmovwl(k1, rcx);
    __ evpbroadcastd(xmm0, xmm0, Assembler::AVX_512bit);
    __ evmovdqul(xmm7, xmm0, Assembler::AVX_512bit);
#ifdef _LP64
    __ evmovdqul(xmm8, xmm0, Assembler::AVX_512bit);
    __ evmovdqul(xmm31, xmm0, Assembler::AVX_512bit);
#endif
    VM_Version::clean_cpuFeatures();
    __ jmp(save_restore_except);

    __ bind(legacy_setup);
    // AVX setup
    VM_Version::set_avx_cpuFeatures(); // Enable temporary to pass asserts
    UseAVX = 1;
    UseSSE = 2;
    // load value into all 32 bytes of ymm7 register
    __ movl(rcx, VM_Version::ymm_test_value());

    __ movdl(xmm0, rcx);
    __ pshufd(xmm0, xmm0, 0x00);
    __ vinsertf128h(xmm0, xmm0, xmm0);
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

    // check _cpuid_info.sef_cpuid7_ebx.bits.avx512f
    __ lea(rsi, Address(rbp, in_bytes(VM_Version::sef_cpuid7_offset())));
    __ movl(rax, 0x10000);
    __ andl(rax, Address(rsi, 4));
    __ cmpl(rax, 0x10000);
    __ jccb(Assembler::notEqual, legacy_save_restore);
    // check _cpuid_info.xem_xcr0_eax.bits.opmask
    // check _cpuid_info.xem_xcr0_eax.bits.zmm512
    // check _cpuid_info.xem_xcr0_eax.bits.zmm32
    __ movl(rax, 0xE0);
    __ andl(rax, Address(rbp, in_bytes(VM_Version::xem_xcr0_offset()))); // xcr0 bits sse | ymm
    __ cmpl(rax, 0xE0);
    __ jccb(Assembler::notEqual, legacy_save_restore);

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
    VM_Version::clean_cpuFeatures();
    UseAVX = saved_useavx;
    UseSSE = saved_usesse;
    __ jmp(wrapup);

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
    _features = feature_flags();
    // Logical processors are only available on P4s and above,
    // and only if hyperthreading is available.
    _logical_processors_per_package = logical_processor_count();
    _L1_data_cache_line_size = L1_line_size();
  }

  _supports_cx8 = supports_cmpxchg8();
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

  // If the OS doesn't support SSE, we can't use this feature even if the HW does
  if (!os::supports_sse())
    _features &= ~(CPU_SSE|CPU_SSE2|CPU_SSE3|CPU_SSSE3|CPU_SSE4A|CPU_SSE4_1|CPU_SSE4_2);

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

  // first try initial setting and detect what we can support
  if (UseAVX > 0) {
    if (UseAVX > 2 && supports_evex()) {
      UseAVX = 3;
    } else if (UseAVX > 1 && supports_avx2()) {
      UseAVX = 2;
    } else if (UseAVX > 0 && supports_avx()) {
      UseAVX = 1;
    } else {
      UseAVX = 0;
    }
  } else if (UseAVX < 0) {
    UseAVX = 0;
  }

  if (UseAVX < 3) {
    _features &= ~CPU_AVX512F;
    _features &= ~CPU_AVX512DQ;
    _features &= ~CPU_AVX512CD;
    _features &= ~CPU_AVX512BW;
    _features &= ~CPU_AVX512VL;
  }

  if (UseAVX < 2)
    _features &= ~CPU_AVX2;

  if (UseAVX < 1)
    _features &= ~CPU_AVX;

  if (!UseAES && !FLAG_IS_DEFAULT(UseAES))
    _features &= ~CPU_AES;

  if (logical_processors_per_package() == 1) {
    // HT processor could be installed on a system which doesn't support HT.
    _features &= ~CPU_HT;
  }

  char buf[256];
  jio_snprintf(buf, sizeof(buf), "(%u cores per cpu, %u threads per core) family %d model %d stepping %d%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s",
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
               (supports_sse4_1() ? ", sse4.1" : ""),
               (supports_sse4_2() ? ", sse4.2" : ""),
               (supports_popcnt() ? ", popcnt" : ""),
               (supports_avx()    ? ", avx" : ""),
               (supports_avx2()   ? ", avx2" : ""),
               (supports_aes()    ? ", aes" : ""),
               (supports_clmul()  ? ", clmul" : ""),
               (supports_erms()   ? ", erms" : ""),
               (supports_rtm()    ? ", rtm" : ""),
               (supports_mmx_ext() ? ", mmxext" : ""),
               (supports_3dnow_prefetch() ? ", 3dnowpref" : ""),
               (supports_lzcnt()   ? ", lzcnt": ""),
               (supports_sse4a()   ? ", sse4a": ""),
               (supports_ht() ? ", ht": ""),
               (supports_tsc() ? ", tsc": ""),
               (supports_tscinv_bit() ? ", tscinvbit": ""),
               (supports_tscinv() ? ", tscinv": ""),
               (supports_bmi1() ? ", bmi1" : ""),
               (supports_bmi2() ? ", bmi2" : ""),
               (supports_adx() ? ", adx" : ""),
               (supports_evex() ? ", evex" : ""));
  _features_string = os::strdup(buf);

  // UseSSE is set to the smaller of what hardware supports and what
  // the command line requires.  I.e., you cannot set UseSSE to 2 on
  // older Pentiums which do not support it.
  if (UseSSE > 4) UseSSE=4;
  if (UseSSE < 0) UseSSE=0;
  if (!supports_sse4_1()) // Drop to 3 if no SSE4 support
    UseSSE = MIN2((intx)3,UseSSE);
  if (!supports_sse3()) // Drop to 2 if no SSE3 support
    UseSSE = MIN2((intx)2,UseSSE);
  if (!supports_sse2()) // Drop to 1 if no SSE2 support
    UseSSE = MIN2((intx)1,UseSSE);
  if (!supports_sse ()) // Drop to 0 if no SSE  support
    UseSSE = 0;

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
        if(supports_sse4_1() && UseSSE >= 4) {
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
  } else if (UseAES || UseAESIntrinsics) {
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

  if (UseAESIntrinsics) {
    if (FLAG_IS_DEFAULT(UseAESCTRIntrinsics)) {
      UseAESCTRIntrinsics = true;
    }
  } else if (UseAESCTRIntrinsics) {
    if (!FLAG_IS_DEFAULT(UseAESCTRIntrinsics))
        warning("AES/CTR intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseAESCTRIntrinsics, false);
  }

  if (supports_sse4_2()) {
    if (FLAG_IS_DEFAULT(UseCRC32CIntrinsics)) {
      UseCRC32CIntrinsics = true;
    }
  }
  else if (UseCRC32CIntrinsics) {
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

  if (UseAdler32Intrinsics) {
    warning("Adler32Intrinsics not available on this CPU.");
    FLAG_SET_DEFAULT(UseAdler32Intrinsics, false);
  }

  // Adjust RTM (Restricted Transactional Memory) flags
  if (!supports_rtm() && UseRTMLocking) {
    // Can't continue because UseRTMLocking affects UseBiasedLocking flag
    // setting during arguments processing. See use_biased_locking().
    // VM_Version_init() is executed after UseBiasedLocking is used
    // in Thread::allocate().
    vm_exit_during_initialization("RTM instructions are not available on this CPU");
  }

#if INCLUDE_RTM_OPT
  if (UseRTMLocking) {
    if (is_intel_family_core()) {
      if ((_model == CPU_MODEL_HASWELL_E3) ||
          (_model == CPU_MODEL_HASWELL_E7 && _stepping < 3) ||
          (_model == CPU_MODEL_BROADWELL  && _stepping < 4)) {
        // currently a collision between SKL and HSW_E3
        if (!UnlockExperimentalVMOptions && UseAVX < 3) {
          vm_exit_during_initialization("UseRTMLocking is only available as experimental option on this platform. It must be enabled via -XX:+UnlockExperimentalVMOptions flag.");
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
    if (!is_power_of_2(RTMTotalCountIncrRate)) {
      warning("RTMTotalCountIncrRate must be a power of 2, resetting it to 64");
      FLAG_SET_DEFAULT(RTMTotalCountIncrRate, 64);
    }
    if (RTMAbortRatio < 0 || RTMAbortRatio > 100) {
      warning("RTMAbortRatio must be in the range 0 to 100, resetting it to 50");
      FLAG_SET_DEFAULT(RTMAbortRatio, 50);
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
    // Can't continue because UseRTMLocking affects UseBiasedLocking flag
    // setting during arguments processing. See use_biased_locking().
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
#if defined(COMPILER2) || INCLUDE_JVMCI
  if (MaxVectorSize > 0) {
    if (!is_power_of_2(MaxVectorSize)) {
      warning("MaxVectorSize must be a power of 2");
      FLAG_SET_DEFAULT(MaxVectorSize, 64);
    }
    if (MaxVectorSize > 64) {
      FLAG_SET_DEFAULT(MaxVectorSize, 64);
    }
    if (MaxVectorSize > 16 && (UseAVX == 0 || !os_supports_avx_vectors())) {
      // 32 bytes vectors (in YMM) are only supported with AVX+
      FLAG_SET_DEFAULT(MaxVectorSize, 16);
    }
    if (UseSSE < 2) {
      // Vectors (in XMM) are only supported with SSE2+
      FLAG_SET_DEFAULT(MaxVectorSize, 0);
    }
#if defined(COMPILER2) && defined(ASSERT)
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
#endif // COMPILER2 && ASSERT
  }
#endif // COMPILER2 || INCLUDE_JVMCI

#ifdef COMPILER2
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
#endif
#endif // COMPILER2

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
    if( supports_sse2() && FLAG_IS_DEFAULT(UseNewLongLShift) ) {
      // Use it on new AMD cpus starting from Opteron.
      UseNewLongLShift = true;
    }
    if( FLAG_IS_DEFAULT(UseXmmLoadAndClearUpper) ) {
      if (supports_sse4a()) {
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
    if (supports_sse4_2() && UseSSE >= 4) {
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
    if ( cpu_family() == 0x15 ) {
      // On family 15h processors default is no sw prefetch
      if (FLAG_IS_DEFAULT(AllocatePrefetchStyle)) {
        AllocatePrefetchStyle = 0;
      }
      // Also, if some other prefetch style is specified, default instruction type is PREFETCHW
      if (FLAG_IS_DEFAULT(AllocatePrefetchInstr)) {
        AllocatePrefetchInstr = 3;
      }
      // On family 15h processors use XMM and UnalignedLoadStores for Array Copy
      if (supports_sse2() && FLAG_IS_DEFAULT(UseXMMForArrayCopy)) {
        UseXMMForArrayCopy = true;
      }
      if (supports_sse2() && FLAG_IS_DEFAULT(UseUnalignedLoadStores)) {
        UseUnalignedLoadStores = true;
      }
    }

#ifdef COMPILER2
    if (MaxVectorSize > 16) {
      // Limit vectors size to 16 bytes on current AMD cpus.
      FLAG_SET_DEFAULT(MaxVectorSize, 16);
    }
#endif // COMPILER2
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
      if (FLAG_IS_DEFAULT(UseXMMForArrayCopy)) {
        UseXMMForArrayCopy = true; // use SSE2 movq on new Intel cpus
      }
      if (supports_sse4_2() && supports_ht()) { // Newest Intel cpus
        if (FLAG_IS_DEFAULT(UseUnalignedLoadStores)) {
          UseUnalignedLoadStores = true; // use movdqu on newest Intel cpus
        }
      }
      if (supports_sse4_2() && UseSSE >= 4) {
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
    if ((cpu_family() == 0x06) &&
        ((extended_cpu_model() == 0x36) || // Centerton
         (extended_cpu_model() == 0x37) || // Silvermont
         (extended_cpu_model() == 0x4D))) {
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
    }
    if(FLAG_IS_DEFAULT(AllocatePrefetchInstr) && supports_3dnow_prefetch()) {
      AllocatePrefetchInstr = 3;
    }
  }

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
#else
  if (UseVectorizedMismatchIntrinsic) {
    if (!FLAG_IS_DEFAULT(UseVectorizedMismatchIntrinsic)) {
      warning("vectorizedMismatch intrinsic is not available in 32-bit VM");
    }
    FLAG_SET_DEFAULT(UseVectorizedMismatchIntrinsic, false);
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

#ifdef COMPILER2
  if (FLAG_IS_DEFAULT(AlignVector)) {
    // Modern processors allow misaligned memory operations for vectors.
    AlignVector = !UseUnalignedLoadStores;
  }
#endif // COMPILER2

  if( AllocatePrefetchInstr == 3 && !supports_3dnow_prefetch() ) AllocatePrefetchInstr=0;
  if( !supports_sse() && supports_3dnow_prefetch() ) AllocatePrefetchInstr = 3;

  // Allocation prefetch settings
  intx cache_line_size = prefetch_data_size();
  if( cache_line_size > AllocatePrefetchStepSize )
    AllocatePrefetchStepSize = cache_line_size;

  assert(AllocatePrefetchLines > 0, "invalid value");
  if( AllocatePrefetchLines < 1 )     // set valid value in product VM
    AllocatePrefetchLines = 3;
  assert(AllocateInstancePrefetchLines > 0, "invalid value");
  if( AllocateInstancePrefetchLines < 1 ) // set valid value in product VM
    AllocateInstancePrefetchLines = 1;

  AllocatePrefetchDistance = allocate_prefetch_distance();
  AllocatePrefetchStyle    = allocate_prefetch_style();

  if (is_intel() && cpu_family() == 6 && supports_sse3()) {
    if (AllocatePrefetchStyle == 2) { // watermark prefetching on Core
#ifdef _LP64
      AllocatePrefetchDistance = 384;
#else
      AllocatePrefetchDistance = 320;
#endif
    }
    if (supports_sse4_2() && supports_ht()) { // Nehalem based cpus
      AllocatePrefetchDistance = 192;
      AllocatePrefetchLines = 4;
    }
#ifdef COMPILER2
    if (supports_sse4_2()) {
      if (FLAG_IS_DEFAULT(UseFPUForSpilling)) {
        FLAG_SET_DEFAULT(UseFPUForSpilling, true);
      }
    }
#endif
  }

#ifdef _LP64
  // Prefetch settings
  PrefetchCopyIntervalInBytes = prefetch_copy_interval_in_bytes();
  PrefetchScanIntervalInBytes = prefetch_scan_interval_in_bytes();
  PrefetchFieldsAhead         = prefetch_fields_ahead();
#endif

  if (FLAG_IS_DEFAULT(ContendedPaddingWidth) &&
     (cache_line_size > ContendedPaddingWidth))
     ContendedPaddingWidth = cache_line_size;

  // This machine allows unaligned memory accesses
  if (FLAG_IS_DEFAULT(UseUnalignedAccesses)) {
    FLAG_SET_DEFAULT(UseUnalignedAccesses, true);
  }

#ifndef PRODUCT
  if (PrintMiscellaneous && Verbose) {
    tty->print_cr("Logical CPUs per core: %u",
                  logical_processors_per_package());
    tty->print_cr("L1 data cache line size: %u", L1_data_cache_line_size());
    tty->print("UseSSE=%d", (int) UseSSE);
    if (UseAVX > 0) {
      tty->print("  UseAVX=%d", (int) UseAVX);
    }
    if (UseAES) {
      tty->print("  UseAES=1");
    }
#ifdef COMPILER2
    if (MaxVectorSize > 0) {
      tty->print("  MaxVectorSize=%d", (int) MaxVectorSize);
    }
#endif
    tty->cr();
    tty->print("Allocation");
    if (AllocatePrefetchStyle <= 0 || UseSSE == 0 && !supports_3dnow_prefetch()) {
      tty->print_cr(": no prefetching");
    } else {
      tty->print(" prefetching: ");
      if (UseSSE == 0 && supports_3dnow_prefetch()) {
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
        tty->print_cr(" at distance %d, %d lines of %d bytes", (int) AllocatePrefetchDistance, (int) AllocatePrefetchLines, (int) AllocatePrefetchStepSize);
      } else {
        tty->print_cr(" at distance %d, one line of %d bytes", (int) AllocatePrefetchDistance, (int) AllocatePrefetchStepSize);
      }
    }

    if (PrefetchCopyIntervalInBytes > 0) {
      tty->print_cr("PrefetchCopyIntervalInBytes %d", (int) PrefetchCopyIntervalInBytes);
    }
    if (PrefetchScanIntervalInBytes > 0) {
      tty->print_cr("PrefetchScanIntervalInBytes %d", (int) PrefetchScanIntervalInBytes);
    }
    if (PrefetchFieldsAhead > 0) {
      tty->print_cr("PrefetchFieldsAhead %d", (int) PrefetchFieldsAhead);
    }
    if (ContendedPaddingWidth > 0) {
      tty->print_cr("ContendedPaddingWidth %d", (int) ContendedPaddingWidth);
    }
  }
#endif // !PRODUCT
}

bool VM_Version::use_biased_locking() {
#if INCLUDE_RTM_OPT
  // RTM locking is most useful when there is high lock contention and
  // low data contention.  With high lock contention the lock is usually
  // inflated and biased locking is not suitable for that case.
  // RTM locking code requires that biased locking is off.
  // Note: we can't switch off UseBiasedLocking in get_processor_features()
  // because it is used by Thread::allocate() which is called before
  // VM_Version::initialize().
  if (UseRTMLocking && UseBiasedLocking) {
    if (FLAG_IS_DEFAULT(UseBiasedLocking)) {
      FLAG_SET_DEFAULT(UseBiasedLocking, false);
    } else {
      warning("Biased locking is not supported with RTM locking; ignoring UseBiasedLocking flag." );
      UseBiasedLocking = false;
    }
  }
#endif
  return UseBiasedLocking;
}

void VM_Version::initialize() {
  ResourceMark rm;
  // Making this stub must be FIRST use of assembler

  stub_blob = BufferBlob::create("get_cpu_info_stub", stub_size);
  if (stub_blob == NULL) {
    vm_exit_during_initialization("Unable to allocate get_cpu_info_stub");
  }
  CodeBuffer c(stub_blob);
  VM_Version_StubGenerator g(&c);
  get_cpu_info_stub = CAST_TO_FN_PTR(get_cpu_info_stub_t,
                                     g.generate_get_cpu_info());

  get_processor_features();
}
