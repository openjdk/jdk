/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2018, SAP SE. All rights reserved.
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
#include "jvm.h"
#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "compiler/disassembler.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "utilities/align.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/globalDefinitions.hpp"
#include "vm_version_ppc.hpp"

#include <sys/sysinfo.h>

#if defined(LINUX) && defined(VM_LITTLE_ENDIAN)
#include <sys/auxv.h>

#ifndef PPC_FEATURE2_HTM_NOSC
#define PPC_FEATURE2_HTM_NOSC (1 << 24)
#endif
#endif

bool VM_Version::_is_determine_features_test_running = false;
uint64_t VM_Version::_dscr_val = 0;

#define MSG(flag)   \
  if (flag && !FLAG_IS_DEFAULT(flag))                                  \
      jio_fprintf(defaultStream::error_stream(),                       \
                  "warning: -XX:+" #flag " requires -XX:+UseSIGTRAP\n" \
                  "         -XX:+" #flag " will be disabled!\n");

void VM_Version::initialize() {

  // Test which instructions are supported and measure cache line size.
  determine_features();

  // If PowerArchitecturePPC64 hasn't been specified explicitly determine from features.
  if (FLAG_IS_DEFAULT(PowerArchitecturePPC64)) {
    if (VM_Version::has_darn()) {
      FLAG_SET_ERGO(uintx, PowerArchitecturePPC64, 9);
    } else if (VM_Version::has_lqarx()) {
      FLAG_SET_ERGO(uintx, PowerArchitecturePPC64, 8);
    } else if (VM_Version::has_popcntw()) {
      FLAG_SET_ERGO(uintx, PowerArchitecturePPC64, 7);
    } else if (VM_Version::has_cmpb()) {
      FLAG_SET_ERGO(uintx, PowerArchitecturePPC64, 6);
    } else if (VM_Version::has_popcntb()) {
      FLAG_SET_ERGO(uintx, PowerArchitecturePPC64, 5);
    } else {
      FLAG_SET_ERGO(uintx, PowerArchitecturePPC64, 0);
    }
  }

  bool PowerArchitecturePPC64_ok = false;
  switch (PowerArchitecturePPC64) {
    case 9: if (!VM_Version::has_darn()   ) break;
    case 8: if (!VM_Version::has_lqarx()  ) break;
    case 7: if (!VM_Version::has_popcntw()) break;
    case 6: if (!VM_Version::has_cmpb()   ) break;
    case 5: if (!VM_Version::has_popcntb()) break;
    case 0: PowerArchitecturePPC64_ok = true; break;
    default: break;
  }
  guarantee(PowerArchitecturePPC64_ok, "PowerArchitecturePPC64 cannot be set to "
            UINTX_FORMAT " on this machine", PowerArchitecturePPC64);

  // Power 8: Configure Data Stream Control Register.
  if (PowerArchitecturePPC64 >= 8 && has_mfdscr()) {
    config_dscr();
  }

  if (!UseSIGTRAP) {
    MSG(TrapBasedICMissChecks);
    MSG(TrapBasedNotEntrantChecks);
    MSG(TrapBasedNullChecks);
    FLAG_SET_ERGO(bool, TrapBasedNotEntrantChecks, false);
    FLAG_SET_ERGO(bool, TrapBasedNullChecks,       false);
    FLAG_SET_ERGO(bool, TrapBasedICMissChecks,     false);
  }

#ifdef COMPILER2
  if (!UseSIGTRAP) {
    MSG(TrapBasedRangeChecks);
    FLAG_SET_ERGO(bool, TrapBasedRangeChecks, false);
  }

  // On Power6 test for section size.
  if (PowerArchitecturePPC64 == 6) {
    determine_section_size();
  // TODO: PPC port } else {
  // TODO: PPC port PdScheduling::power6SectorSize = 0x20;
  }

  if (PowerArchitecturePPC64 >= 8) {
    if (FLAG_IS_DEFAULT(SuperwordUseVSX)) {
      FLAG_SET_ERGO(bool, SuperwordUseVSX, true);
    }
  } else {
    if (SuperwordUseVSX) {
      warning("SuperwordUseVSX specified, but needs at least Power8.");
      FLAG_SET_DEFAULT(SuperwordUseVSX, false);
    }
  }
  MaxVectorSize = SuperwordUseVSX ? 16 : 8;

  if (PowerArchitecturePPC64 >= 9) {
    if (FLAG_IS_DEFAULT(UseCountTrailingZerosInstructionsPPC64)) {
      FLAG_SET_ERGO(bool, UseCountTrailingZerosInstructionsPPC64, true);
    }
    if (FLAG_IS_DEFAULT(UseCharacterCompareIntrinsics)) {
      FLAG_SET_ERGO(bool, UseCharacterCompareIntrinsics, true);
    }
  } else {
    if (UseCountTrailingZerosInstructionsPPC64) {
      warning("UseCountTrailingZerosInstructionsPPC64 specified, but needs at least Power9.");
      FLAG_SET_DEFAULT(UseCountTrailingZerosInstructionsPPC64, false);
    }
    if (UseCharacterCompareIntrinsics) {
      warning("UseCharacterCompareIntrinsics specified, but needs at least Power9.");
      FLAG_SET_DEFAULT(UseCharacterCompareIntrinsics, false);
    }
  }
#endif

  // Create and print feature-string.
  char buf[(num_features+1) * 16]; // Max 16 chars per feature.
  jio_snprintf(buf, sizeof(buf),
               "ppc64%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s",
               (has_fsqrt()   ? " fsqrt"   : ""),
               (has_isel()    ? " isel"    : ""),
               (has_lxarxeh() ? " lxarxeh" : ""),
               (has_cmpb()    ? " cmpb"    : ""),
               (has_popcntb() ? " popcntb" : ""),
               (has_popcntw() ? " popcntw" : ""),
               (has_fcfids()  ? " fcfids"  : ""),
               (has_vand()    ? " vand"    : ""),
               (has_lqarx()   ? " lqarx"   : ""),
               (has_vcipher() ? " aes"     : ""),
               (has_vpmsumb() ? " vpmsumb" : ""),
               (has_mfdscr()  ? " mfdscr"  : ""),
               (has_vsx()     ? " vsx"     : ""),
               (has_ldbrx()   ? " ldbrx"   : ""),
               (has_stdbrx()  ? " stdbrx"  : ""),
               (has_vshasig() ? " sha"     : ""),
               (has_tm()      ? " rtm"     : ""),
               (has_darn()    ? " darn"    : "")
               // Make sure number of %s matches num_features!
              );
  _features_string = os::strdup(buf);
  if (Verbose) {
    print_features();
  }

  // PPC64 supports 8-byte compare-exchange operations (see Atomic::cmpxchg)
  // and 'atomic long memory ops' (see Unsafe_GetLongVolatile).
  _supports_cx8 = true;

  // Used by C1.
  _supports_atomic_getset4 = true;
  _supports_atomic_getadd4 = true;
  _supports_atomic_getset8 = true;
  _supports_atomic_getadd8 = true;

  UseSSE = 0; // Only on x86 and x64

  intx cache_line_size = L1_data_cache_line_size();

  if (FLAG_IS_DEFAULT(AllocatePrefetchStyle)) AllocatePrefetchStyle = 1;

  if (AllocatePrefetchStyle == 4) {
    AllocatePrefetchStepSize = cache_line_size; // Need exact value.
    if (FLAG_IS_DEFAULT(AllocatePrefetchLines)) AllocatePrefetchLines = 12; // Use larger blocks by default.
    if (AllocatePrefetchDistance < 0) AllocatePrefetchDistance = 2*cache_line_size; // Default is not defined?
  } else {
    if (cache_line_size > AllocatePrefetchStepSize) AllocatePrefetchStepSize = cache_line_size;
    if (FLAG_IS_DEFAULT(AllocatePrefetchLines)) AllocatePrefetchLines = 3; // Optimistic value.
    if (AllocatePrefetchDistance < 0) AllocatePrefetchDistance = 3*cache_line_size; // Default is not defined?
  }

  assert(AllocatePrefetchLines > 0, "invalid value");
  if (AllocatePrefetchLines < 1) { // Set valid value in product VM.
    AllocatePrefetchLines = 1; // Conservative value.
  }

  if (AllocatePrefetchStyle == 3 && AllocatePrefetchDistance < cache_line_size) {
    AllocatePrefetchStyle = 1; // Fall back if inappropriate.
  }

  assert(AllocatePrefetchStyle >= 0, "AllocatePrefetchStyle should be positive");

  // If running on Power8 or newer hardware, the implementation uses the available vector instructions.
  // In all other cases, the implementation uses only generally available instructions.
  if (!UseCRC32Intrinsics) {
    if (FLAG_IS_DEFAULT(UseCRC32Intrinsics)) {
      FLAG_SET_DEFAULT(UseCRC32Intrinsics, true);
    }
  }

  // Implementation does not use any of the vector instructions available with Power8.
  // Their exploitation is still pending (aka "work in progress").
  if (!UseCRC32CIntrinsics) {
    if (FLAG_IS_DEFAULT(UseCRC32CIntrinsics)) {
      FLAG_SET_DEFAULT(UseCRC32CIntrinsics, true);
    }
  }

  // TODO: Provide implementation.
  if (UseAdler32Intrinsics) {
    warning("Adler32Intrinsics not available on this CPU.");
    FLAG_SET_DEFAULT(UseAdler32Intrinsics, false);
  }

  // The AES intrinsic stubs require AES instruction support.
  if (has_vcipher()) {
    if (FLAG_IS_DEFAULT(UseAES)) {
      UseAES = true;
    }
  } else if (UseAES) {
    if (!FLAG_IS_DEFAULT(UseAES))
      warning("AES instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseAES, false);
  }

  if (UseAES && has_vcipher()) {
    if (FLAG_IS_DEFAULT(UseAESIntrinsics)) {
      UseAESIntrinsics = true;
    }
  } else if (UseAESIntrinsics) {
    if (!FLAG_IS_DEFAULT(UseAESIntrinsics))
      warning("AES intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseAESIntrinsics, false);
  }

  if (UseAESCTRIntrinsics) {
    warning("AES/CTR intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseAESCTRIntrinsics, false);
  }

  if (UseGHASHIntrinsics) {
    warning("GHASH intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseGHASHIntrinsics, false);
  }

  if (FLAG_IS_DEFAULT(UseFMA)) {
    FLAG_SET_DEFAULT(UseFMA, true);
  }

  if (has_vshasig()) {
    if (FLAG_IS_DEFAULT(UseSHA)) {
      UseSHA = true;
    }
  } else if (UseSHA) {
    if (!FLAG_IS_DEFAULT(UseSHA))
      warning("SHA instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseSHA, false);
  }

  if (UseSHA1Intrinsics) {
    warning("Intrinsics for SHA-1 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA1Intrinsics, false);
  }

  if (UseSHA && has_vshasig()) {
    if (FLAG_IS_DEFAULT(UseSHA256Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA256Intrinsics, true);
    }
  } else if (UseSHA256Intrinsics) {
    warning("Intrinsics for SHA-224 and SHA-256 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA256Intrinsics, false);
  }

  if (UseSHA && has_vshasig()) {
    if (FLAG_IS_DEFAULT(UseSHA512Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA512Intrinsics, true);
    }
  } else if (UseSHA512Intrinsics) {
    warning("Intrinsics for SHA-384 and SHA-512 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA512Intrinsics, false);
  }

  if (!(UseSHA1Intrinsics || UseSHA256Intrinsics || UseSHA512Intrinsics)) {
    FLAG_SET_DEFAULT(UseSHA, false);
  }

  if (FLAG_IS_DEFAULT(UseSquareToLenIntrinsic)) {
    UseSquareToLenIntrinsic = true;
  }
  if (FLAG_IS_DEFAULT(UseMulAddIntrinsic)) {
    UseMulAddIntrinsic = true;
  }
  if (FLAG_IS_DEFAULT(UseMultiplyToLenIntrinsic)) {
    UseMultiplyToLenIntrinsic = true;
  }
  if (FLAG_IS_DEFAULT(UseMontgomeryMultiplyIntrinsic)) {
    UseMontgomeryMultiplyIntrinsic = true;
  }
  if (FLAG_IS_DEFAULT(UseMontgomerySquareIntrinsic)) {
    UseMontgomerySquareIntrinsic = true;
  }

  if (UseVectorizedMismatchIntrinsic) {
    warning("UseVectorizedMismatchIntrinsic specified, but not available on this CPU.");
    FLAG_SET_DEFAULT(UseVectorizedMismatchIntrinsic, false);
  }


  // Adjust RTM (Restricted Transactional Memory) flags.
  if (UseRTMLocking) {
    // If CPU or OS do not support TM:
    // Can't continue because UseRTMLocking affects UseBiasedLocking flag
    // setting during arguments processing. See use_biased_locking().
    // VM_Version_init() is executed after UseBiasedLocking is used
    // in Thread::allocate().
    if (PowerArchitecturePPC64 < 8) {
      vm_exit_during_initialization("RTM instructions are not available on this CPU.");
    }

    if (!has_tm()) {
      vm_exit_during_initialization("RTM is not supported on this OS version.");
    }
  }

  if (UseRTMLocking) {
#if INCLUDE_RTM_OPT
    if (!FLAG_IS_CMDLINE(UseRTMLocking)) {
      // RTM locking should be used only for applications with
      // high lock contention. For now we do not use it by default.
      vm_exit_during_initialization("UseRTMLocking flag should be only set on command line");
    }
#else
    // Only C2 does RTM locking optimization.
    // Can't continue because UseRTMLocking affects UseBiasedLocking flag
    // setting during arguments processing. See use_biased_locking().
    vm_exit_during_initialization("RTM locking optimization is not supported in this VM");
#endif
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

  // This machine allows unaligned memory accesses
  if (FLAG_IS_DEFAULT(UseUnalignedAccesses)) {
    FLAG_SET_DEFAULT(UseUnalignedAccesses, true);
  }
}

void VM_Version::print_platform_virtualization_info(outputStream* st) {
  const char* info_file = "/proc/ppc64/lparcfg";
  const char* kw[] = { "system_type=", // qemu indicates PowerKVM
                       "partition_entitled_capacity=", // entitled processor capacity percentage
                       "partition_max_entitled_capacity=",
                       "capacity_weight=", // partition CPU weight
                       "partition_active_processors=",
                       "partition_potential_processors=",
                       "entitled_proc_capacity_available=",
                       "capped=", // 0 - uncapped, 1 - vcpus capped at entitled processor capacity percentage
                       "shared_processor_mode=", // (non)dedicated partition
                       "system_potential_processors=",
                       "pool=", // CPU-pool number
                       "pool_capacity=",
                       "NumLpars=", // on non-KVM machines, NumLpars is not found for full partition mode machines
                       NULL };
  if (!print_matching_lines_from_file(info_file, st, kw)) {
    st->print_cr("  <%s Not Available>", info_file);
  }
}

bool VM_Version::use_biased_locking() {
#if INCLUDE_RTM_OPT
  // RTM locking is most useful when there is high lock contention and
  // low data contention. With high lock contention the lock is usually
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

void VM_Version::print_features() {
  tty->print_cr("Version: %s L1_data_cache_line_size=%d", features_string(), L1_data_cache_line_size());
}

#ifdef COMPILER2
// Determine section size on power6: If section size is 8 instructions,
// there should be a difference between the two testloops of ~15 %. If
// no difference is detected the section is assumed to be 32 instructions.
void VM_Version::determine_section_size() {

  int unroll = 80;

  const int code_size = (2* unroll * 32 + 100)*BytesPerInstWord;

  // Allocate space for the code.
  ResourceMark rm;
  CodeBuffer cb("detect_section_size", code_size, 0);
  MacroAssembler* a = new MacroAssembler(&cb);

  uint32_t *code = (uint32_t *)a->pc();
  // Emit code.
  void (*test1)() = (void(*)())(void *)a->function_entry();

  Label l1;

  a->li(R4, 1);
  a->sldi(R4, R4, 28);
  a->b(l1);
  a->align(CodeEntryAlignment);

  a->bind(l1);

  for (int i = 0; i < unroll; i++) {
    // Schleife 1
    // ------- sector 0 ------------
    // ;; 0
    a->nop();                   // 1
    a->fpnop0();                // 2
    a->fpnop1();                // 3
    a->addi(R4,R4, -1); // 4

    // ;;  1
    a->nop();                   // 5
    a->fmr(F6, F6);             // 6
    a->fmr(F7, F7);             // 7
    a->endgroup();              // 8
    // ------- sector 8 ------------

    // ;;  2
    a->nop();                   // 9
    a->nop();                   // 10
    a->fmr(F8, F8);             // 11
    a->fmr(F9, F9);             // 12

    // ;;  3
    a->nop();                   // 13
    a->fmr(F10, F10);           // 14
    a->fmr(F11, F11);           // 15
    a->endgroup();              // 16
    // -------- sector 16 -------------

    // ;;  4
    a->nop();                   // 17
    a->nop();                   // 18
    a->fmr(F15, F15);           // 19
    a->fmr(F16, F16);           // 20

    // ;;  5
    a->nop();                   // 21
    a->fmr(F17, F17);           // 22
    a->fmr(F18, F18);           // 23
    a->endgroup();              // 24
    // ------- sector 24  ------------

    // ;;  6
    a->nop();                   // 25
    a->nop();                   // 26
    a->fmr(F19, F19);           // 27
    a->fmr(F20, F20);           // 28

    // ;;  7
    a->nop();                   // 29
    a->fmr(F21, F21);           // 30
    a->fmr(F22, F22);           // 31
    a->brnop0();                // 32

    // ------- sector 32 ------------
  }

  // ;; 8
  a->cmpdi(CCR0, R4, unroll);   // 33
  a->bge(CCR0, l1);             // 34
  a->blr();

  // Emit code.
  void (*test2)() = (void(*)())(void *)a->function_entry();
  // uint32_t *code = (uint32_t *)a->pc();

  Label l2;

  a->li(R4, 1);
  a->sldi(R4, R4, 28);
  a->b(l2);
  a->align(CodeEntryAlignment);

  a->bind(l2);

  for (int i = 0; i < unroll; i++) {
    // Schleife 2
    // ------- sector 0 ------------
    // ;; 0
    a->brnop0();                  // 1
    a->nop();                     // 2
    //a->cmpdi(CCR0, R4, unroll);
    a->fpnop0();                  // 3
    a->fpnop1();                  // 4
    a->addi(R4,R4, -1);           // 5

    // ;; 1

    a->nop();                     // 6
    a->fmr(F6, F6);               // 7
    a->fmr(F7, F7);               // 8
    // ------- sector 8 ---------------

    // ;; 2
    a->endgroup();                // 9

    // ;; 3
    a->nop();                     // 10
    a->nop();                     // 11
    a->fmr(F8, F8);               // 12

    // ;; 4
    a->fmr(F9, F9);               // 13
    a->nop();                     // 14
    a->fmr(F10, F10);             // 15

    // ;; 5
    a->fmr(F11, F11);             // 16
    // -------- sector 16 -------------

    // ;; 6
    a->endgroup();                // 17

    // ;; 7
    a->nop();                     // 18
    a->nop();                     // 19
    a->fmr(F15, F15);             // 20

    // ;; 8
    a->fmr(F16, F16);             // 21
    a->nop();                     // 22
    a->fmr(F17, F17);             // 23

    // ;; 9
    a->fmr(F18, F18);             // 24
    // -------- sector 24 -------------

    // ;; 10
    a->endgroup();                // 25

    // ;; 11
    a->nop();                     // 26
    a->nop();                     // 27
    a->fmr(F19, F19);             // 28

    // ;; 12
    a->fmr(F20, F20);             // 29
    a->nop();                     // 30
    a->fmr(F21, F21);             // 31

    // ;; 13
    a->fmr(F22, F22);             // 32
  }

  // -------- sector 32 -------------
  // ;; 14
  a->cmpdi(CCR0, R4, unroll); // 33
  a->bge(CCR0, l2);           // 34

  a->blr();
  uint32_t *code_end = (uint32_t *)a->pc();
  a->flush();

  double loop1_seconds,loop2_seconds, rel_diff;
  uint64_t start1, stop1;

  start1 = os::current_thread_cpu_time(false);
  (*test1)();
  stop1 = os::current_thread_cpu_time(false);
  loop1_seconds = (stop1- start1) / (1000 *1000 *1000.0);


  start1 = os::current_thread_cpu_time(false);
  (*test2)();
  stop1 = os::current_thread_cpu_time(false);

  loop2_seconds = (stop1 - start1) / (1000 *1000 *1000.0);

  rel_diff = (loop2_seconds - loop1_seconds) / loop1_seconds *100;

  if (PrintAssembly) {
    ttyLocker ttyl;
    tty->print_cr("Decoding section size detection stub at " INTPTR_FORMAT " before execution:", p2i(code));
    Disassembler::decode((u_char*)code, (u_char*)code_end, tty);
    tty->print_cr("Time loop1 :%f", loop1_seconds);
    tty->print_cr("Time loop2 :%f", loop2_seconds);
    tty->print_cr("(time2 - time1) / time1 = %f %%", rel_diff);

    if (rel_diff > 12.0) {
      tty->print_cr("Section Size 8 Instructions");
    } else{
      tty->print_cr("Section Size 32 Instructions or Power5");
    }
  }

#if 0 // TODO: PPC port
  // Set sector size (if not set explicitly).
  if (FLAG_IS_DEFAULT(Power6SectorSize128PPC64)) {
    if (rel_diff > 12.0) {
      PdScheduling::power6SectorSize = 0x20;
    } else {
      PdScheduling::power6SectorSize = 0x80;
    }
  } else if (Power6SectorSize128PPC64) {
    PdScheduling::power6SectorSize = 0x80;
  } else {
    PdScheduling::power6SectorSize = 0x20;
  }
#endif
  if (UsePower6SchedulerPPC64) Unimplemented();
}
#endif // COMPILER2

void VM_Version::determine_features() {
#if defined(ABI_ELFv2)
  // 1 InstWord per call for the blr instruction.
  const int code_size = (num_features+1+2*1)*BytesPerInstWord;
#else
  // 7 InstWords for each call (function descriptor + blr instruction).
  const int code_size = (num_features+1+2*7)*BytesPerInstWord;
#endif
  int features = 0;

  // create test area
  enum { BUFFER_SIZE = 2*4*K }; // Needs to be >=2* max cache line size (cache line size can't exceed min page size).
  char test_area[BUFFER_SIZE];
  char *mid_of_test_area = &test_area[BUFFER_SIZE>>1];

  // Allocate space for the code.
  ResourceMark rm;
  CodeBuffer cb("detect_cpu_features", code_size, 0);
  MacroAssembler* a = new MacroAssembler(&cb);

  // Must be set to true so we can generate the test code.
  _features = VM_Version::all_features_m;

  // Emit code.
  void (*test)(address addr, uint64_t offset)=(void(*)(address addr, uint64_t offset))(void *)a->function_entry();
  uint32_t *code = (uint32_t *)a->pc();
  // Don't use R0 in ldarx.
  // Keep R3_ARG1 unmodified, it contains &field (see below).
  // Keep R4_ARG2 unmodified, it contains offset = 0 (see below).
  a->fsqrt(F3, F4);                            // code[0]  -> fsqrt_m
  a->fsqrts(F3, F4);                           // code[1]  -> fsqrts_m
  a->isel(R7, R5, R6, 0);                      // code[2]  -> isel_m
  a->ldarx_unchecked(R7, R3_ARG1, R4_ARG2, 1); // code[3]  -> lxarx_m
  a->cmpb(R7, R5, R6);                         // code[4]  -> cmpb
  a->popcntb(R7, R5);                          // code[5]  -> popcntb
  a->popcntw(R7, R5);                          // code[6]  -> popcntw
  a->fcfids(F3, F4);                           // code[7]  -> fcfids
  a->vand(VR0, VR0, VR0);                      // code[8]  -> vand
  // arg0 of lqarx must be an even register, (arg1 + arg2) must be a multiple of 16
  a->lqarx_unchecked(R6, R3_ARG1, R4_ARG2, 1); // code[9]  -> lqarx_m
  a->vcipher(VR0, VR1, VR2);                   // code[10] -> vcipher
  a->vpmsumb(VR0, VR1, VR2);                   // code[11] -> vpmsumb
  a->mfdscr(R0);                               // code[12] -> mfdscr
  a->lxvd2x(VSR0, R3_ARG1);                    // code[13] -> vsx
  a->ldbrx(R7, R3_ARG1, R4_ARG2);              // code[14] -> ldbrx
  a->stdbrx(R7, R3_ARG1, R4_ARG2);             // code[15] -> stdbrx
  a->vshasigmaw(VR0, VR1, 1, 0xF);             // code[16] -> vshasig
  // rtm is determined by OS
  a->darn(R7);                                 // code[17] -> darn
  a->blr();

  // Emit function to set one cache line to zero. Emit function descriptor and get pointer to it.
  void (*zero_cacheline_func_ptr)(char*) = (void(*)(char*))(void *)a->function_entry();
  a->dcbz(R3_ARG1); // R3_ARG1 = addr
  a->blr();

  uint32_t *code_end = (uint32_t *)a->pc();
  a->flush();
  _features = VM_Version::unknown_m;

  // Print the detection code.
  if (PrintAssembly) {
    ttyLocker ttyl;
    tty->print_cr("Decoding cpu-feature detection stub at " INTPTR_FORMAT " before execution:", p2i(code));
    Disassembler::decode((u_char*)code, (u_char*)code_end, tty);
  }

  // Measure cache line size.
  memset(test_area, 0xFF, BUFFER_SIZE); // Fill test area with 0xFF.
  (*zero_cacheline_func_ptr)(mid_of_test_area); // Call function which executes dcbz to the middle.
  int count = 0; // count zeroed bytes
  for (int i = 0; i < BUFFER_SIZE; i++) if (test_area[i] == 0) count++;
  guarantee(is_power_of_2(count), "cache line size needs to be a power of 2");
  _L1_data_cache_line_size = count;

  // Execute code. Illegal instructions will be replaced by 0 in the signal handler.
  VM_Version::_is_determine_features_test_running = true;
  // We must align the first argument to 16 bytes because of the lqarx check.
  (*test)(align_up((address)mid_of_test_area, 16), 0);
  VM_Version::_is_determine_features_test_running = false;

  // determine which instructions are legal.
  int feature_cntr = 0;
  if (code[feature_cntr++]) features |= fsqrt_m;
  if (code[feature_cntr++]) features |= fsqrts_m;
  if (code[feature_cntr++]) features |= isel_m;
  if (code[feature_cntr++]) features |= lxarxeh_m;
  if (code[feature_cntr++]) features |= cmpb_m;
  if (code[feature_cntr++]) features |= popcntb_m;
  if (code[feature_cntr++]) features |= popcntw_m;
  if (code[feature_cntr++]) features |= fcfids_m;
  if (code[feature_cntr++]) features |= vand_m;
  if (code[feature_cntr++]) features |= lqarx_m;
  if (code[feature_cntr++]) features |= vcipher_m;
  if (code[feature_cntr++]) features |= vpmsumb_m;
  if (code[feature_cntr++]) features |= mfdscr_m;
  if (code[feature_cntr++]) features |= vsx_m;
  if (code[feature_cntr++]) features |= ldbrx_m;
  if (code[feature_cntr++]) features |= stdbrx_m;
  if (code[feature_cntr++]) features |= vshasig_m;
  // feature rtm_m is determined by OS
  if (code[feature_cntr++]) features |= darn_m;

  // Print the detection code.
  if (PrintAssembly) {
    ttyLocker ttyl;
    tty->print_cr("Decoding cpu-feature detection stub at " INTPTR_FORMAT " after execution:", p2i(code));
    Disassembler::decode((u_char*)code, (u_char*)code_end, tty);
  }

  _features = features;

#ifdef AIX
  // To enable it on AIX it's necessary POWER8 or above and at least AIX 7.2.
  // Actually, this is supported since AIX 7.1.. Unfortunately, this first
  // contained bugs, so that it can only be enabled after AIX 7.1.3.30.
  // The Java property os.version, which is used in RTM tests to decide
  // whether the feature is available, only knows major and minor versions.
  // We don't want to change this property, as user code might depend on it.
  // So the tests can not check on subversion 3.30, and we only enable RTM
  // with AIX 7.2.
  if (has_lqarx()) { // POWER8 or above
    if (os::Aix::os_version() >= 0x07020000) { // At least AIX 7.2.
      _features |= rtm_m;
    }
  }
#endif
#if defined(LINUX) && defined(VM_LITTLE_ENDIAN)
  unsigned long auxv = getauxval(AT_HWCAP2);

  if (auxv & PPC_FEATURE2_HTM_NOSC) {
    if (auxv & PPC_FEATURE2_HAS_HTM) {
      // TM on POWER8 and POWER9 in compat mode (VM) is supported by the JVM.
      // TM on POWER9 DD2.1 NV (baremetal) is not supported by the JVM (TM on
      // POWER9 DD2.1 NV has a few issues that need a couple of firmware
      // and kernel workarounds, so there is a new mode only supported
      // on non-virtualized P9 machines called HTM with no Suspend Mode).
      // TM on POWER9 D2.2+ NV is not supported at all by Linux.
      _features |= rtm_m;
    }
  }
#endif
}

// Power 8: Configure Data Stream Control Register.
void VM_Version::config_dscr() {
  // 7 InstWords for each call (function descriptor + blr instruction).
  const int code_size = (2+2*7)*BytesPerInstWord;

  // Allocate space for the code.
  ResourceMark rm;
  CodeBuffer cb("config_dscr", code_size, 0);
  MacroAssembler* a = new MacroAssembler(&cb);

  // Emit code.
  uint64_t (*get_dscr)() = (uint64_t(*)())(void *)a->function_entry();
  uint32_t *code = (uint32_t *)a->pc();
  a->mfdscr(R3);
  a->blr();

  void (*set_dscr)(long) = (void(*)(long))(void *)a->function_entry();
  a->mtdscr(R3);
  a->blr();

  uint32_t *code_end = (uint32_t *)a->pc();
  a->flush();

  // Print the detection code.
  if (PrintAssembly) {
    ttyLocker ttyl;
    tty->print_cr("Decoding dscr configuration stub at " INTPTR_FORMAT " before execution:", p2i(code));
    Disassembler::decode((u_char*)code, (u_char*)code_end, tty);
  }

  // Apply the configuration if needed.
  _dscr_val = (*get_dscr)();
  if (Verbose) {
    tty->print_cr("dscr value was 0x%lx" , _dscr_val);
  }
  bool change_requested = false;
  if (DSCR_PPC64 != (uintx)-1) {
    _dscr_val = DSCR_PPC64;
    change_requested = true;
  }
  if (DSCR_DPFD_PPC64 <= 7) {
    uint64_t mask = 0x7;
    if ((_dscr_val & mask) != DSCR_DPFD_PPC64) {
      _dscr_val = (_dscr_val & ~mask) | (DSCR_DPFD_PPC64);
      change_requested = true;
    }
  }
  if (DSCR_URG_PPC64 <= 7) {
    uint64_t mask = 0x7 << 6;
    if ((_dscr_val & mask) != DSCR_DPFD_PPC64 << 6) {
      _dscr_val = (_dscr_val & ~mask) | (DSCR_URG_PPC64 << 6);
      change_requested = true;
    }
  }
  if (change_requested) {
    (*set_dscr)(_dscr_val);
    if (Verbose) {
      tty->print_cr("dscr was set to 0x%lx" , (*get_dscr)());
    }
  }
}

static uint64_t saved_features = 0;

void VM_Version::allow_all() {
  saved_features = _features;
  _features      = all_features_m;
}

void VM_Version::revert() {
  _features = saved_features;
}
