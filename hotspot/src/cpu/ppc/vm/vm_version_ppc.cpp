/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2015 SAP AG. All rights reserved.
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
#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "compiler/disassembler.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/globalDefinitions.hpp"
#include "vm_version_ppc.hpp"

# include <sys/sysinfo.h>

int VM_Version::_features = VM_Version::unknown_m;
int VM_Version::_measured_cache_line_size = 32; // pessimistic init value
const char* VM_Version::_features_str = "";
bool VM_Version::_is_determine_features_test_running = false;


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
    if (VM_Version::has_lqarx()) {
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
  guarantee(PowerArchitecturePPC64 == 0 || PowerArchitecturePPC64 == 5 ||
            PowerArchitecturePPC64 == 6 || PowerArchitecturePPC64 == 7 ||
            PowerArchitecturePPC64 == 8,
            "PowerArchitecturePPC64 should be 0, 5, 6, 7, or 8");

  // Power 8: Configure Data Stream Control Register.
  if (PowerArchitecturePPC64 >= 8) {
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

  MaxVectorSize = 8;
#endif

  // Create and print feature-string.
  char buf[(num_features+1) * 16]; // Max 16 chars per feature.
  jio_snprintf(buf, sizeof(buf),
               "ppc64%s%s%s%s%s%s%s%s%s%s%s%s",
               (has_fsqrt()   ? " fsqrt"   : ""),
               (has_isel()    ? " isel"    : ""),
               (has_lxarxeh() ? " lxarxeh" : ""),
               (has_cmpb()    ? " cmpb"    : ""),
               //(has_mftgpr()? " mftgpr"  : ""),
               (has_popcntb() ? " popcntb" : ""),
               (has_popcntw() ? " popcntw" : ""),
               (has_fcfids()  ? " fcfids"  : ""),
               (has_vand()    ? " vand"    : ""),
               (has_lqarx()   ? " lqarx"   : ""),
               (has_vcipher() ? " vcipher" : ""),
               (has_vpmsumb() ? " vpmsumb" : ""),
               (has_tcheck()  ? " tcheck"  : "")
               // Make sure number of %s matches num_features!
              );
  _features_str = os::strdup(buf);
  if (Verbose) {
    print_features();
  }

  // PPC64 supports 8-byte compare-exchange operations (see
  // Atomic::cmpxchg and StubGenerator::generate_atomic_cmpxchg_ptr)
  // and 'atomic long memory ops' (see Unsafe_GetLongVolatile).
  _supports_cx8 = true;

  UseSSE = 0; // Only on x86 and x64

  intx cache_line_size = _measured_cache_line_size;

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

  // Implementation does not use any of the vector instructions
  // available with Power8. Their exploitation is still pending.
  if (!UseCRC32Intrinsics) {
    if (FLAG_IS_DEFAULT(UseCRC32Intrinsics)) {
      FLAG_SET_DEFAULT(UseCRC32Intrinsics, true);
    }
  }

  if (UseCRC32CIntrinsics) {
    if (!FLAG_IS_DEFAULT(UseCRC32CIntrinsics))
      warning("CRC32C intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseCRC32CIntrinsics, false);
  }

  // The AES intrinsic stubs require AES instruction support.
  if (UseAES) {
    warning("AES instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseAES, false);
  }
  if (UseAESIntrinsics) {
    if (!FLAG_IS_DEFAULT(UseAESIntrinsics))
      warning("AES intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseAESIntrinsics, false);
  }

  if (UseGHASHIntrinsics) {
    warning("GHASH intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseGHASHIntrinsics, false);
  }

  if (UseSHA) {
    warning("SHA instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseSHA, false);
  }
  if (UseSHA1Intrinsics || UseSHA256Intrinsics || UseSHA512Intrinsics) {
    warning("SHA intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseSHA1Intrinsics, false);
    FLAG_SET_DEFAULT(UseSHA256Intrinsics, false);
    FLAG_SET_DEFAULT(UseSHA512Intrinsics, false);
  }

  if (UseAdler32Intrinsics) {
    warning("Adler32Intrinsics not available on this CPU.");
    FLAG_SET_DEFAULT(UseAdler32Intrinsics, false);
  }

  if (FLAG_IS_DEFAULT(UseMultiplyToLenIntrinsic)) {
    UseMultiplyToLenIntrinsic = true;
  }

  // Adjust RTM (Restricted Transactional Memory) flags.
  if (!has_tcheck() && UseRTMLocking) {
    // Can't continue because UseRTMLocking affects UseBiasedLocking flag
    // setting during arguments processing. See use_biased_locking().
    // VM_Version_init() is executed after UseBiasedLocking is used
    // in Thread::allocate().
    vm_exit_during_initialization("RTM instructions are not available on this CPU");
  }

  if (UseRTMLocking) {
#if INCLUDE_RTM_OPT
    if (!UnlockExperimentalVMOptions) {
      vm_exit_during_initialization("UseRTMLocking is only available as experimental option on this platform. "
                                    "It must be enabled via -XX:+UnlockExperimentalVMOptions flag.");
    } else {
      warning("UseRTMLocking is only available as experimental option on this platform.");
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
    guarantee(RTMSpinLoopCount > 0, "unsupported");
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

  // This machine does not allow unaligned memory accesses
  if (UseUnalignedAccesses) {
    if (!FLAG_IS_DEFAULT(UseUnalignedAccesses))
      warning("Unaligned memory access is not available on this CPU");
    FLAG_SET_DEFAULT(UseUnalignedAccesses, false);
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
  tty->print_cr("Version: %s cache_line_size = %d", cpu_features(), (int) get_cache_line_size());
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
  a->tcheck(0);                                // code[12] -> tcheck
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
  _measured_cache_line_size = count;

  // Execute code. Illegal instructions will be replaced by 0 in the signal handler.
  VM_Version::_is_determine_features_test_running = true;
  // We must align the first argument to 16 bytes because of the lqarx check.
  (*test)((address)align_size_up((intptr_t)mid_of_test_area, 16), (uint64_t)0);
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
  if (code[feature_cntr++]) features |= tcheck_m;

  // Print the detection code.
  if (PrintAssembly) {
    ttyLocker ttyl;
    tty->print_cr("Decoding cpu-feature detection stub at " INTPTR_FORMAT " after execution:", p2i(code));
    Disassembler::decode((u_char*)code, (u_char*)code_end, tty);
  }

  _features = features;
}

// Power 8: Configure Data Stream Control Register.
void VM_Version::config_dscr() {
  assert(has_tcheck(), "Only execute on Power 8 or later!");

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
  uint64_t dscr_val = (*get_dscr)();
  if (Verbose) {
    tty->print_cr("dscr value was 0x%lx" , dscr_val);
  }
  bool change_requested = false;
  if (DSCR_PPC64 != (uintx)-1) {
    dscr_val = DSCR_PPC64;
    change_requested = true;
  }
  if (DSCR_DPFD_PPC64 <= 7) {
    uint64_t mask = 0x7;
    if ((dscr_val & mask) != DSCR_DPFD_PPC64) {
      dscr_val = (dscr_val & ~mask) | (DSCR_DPFD_PPC64);
      change_requested = true;
    }
  }
  if (DSCR_URG_PPC64 <= 7) {
    uint64_t mask = 0x7 << 6;
    if ((dscr_val & mask) != DSCR_DPFD_PPC64 << 6) {
      dscr_val = (dscr_val & ~mask) | (DSCR_URG_PPC64 << 6);
      change_requested = true;
    }
  }
  if (change_requested) {
    (*set_dscr)(dscr_val);
    if (Verbose) {
      tty->print_cr("dscr was set to 0x%lx" , (*get_dscr)());
    }
  }
}

static int saved_features = 0;

void VM_Version::allow_all() {
  saved_features = _features;
  _features      = all_features_m;
}

void VM_Version::revert() {
  _features = saved_features;
}
