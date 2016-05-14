/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "vm_version_sparc.hpp"

unsigned int VM_Version::_L2_data_cache_line_size = 0;

void VM_Version::initialize() {
  assert(_features != 0, "System pre-initialization is not complete.");
  guarantee(VM_Version::has_v9(), "only SPARC v9 is supported");

  PrefetchCopyIntervalInBytes = prefetch_copy_interval_in_bytes();
  PrefetchScanIntervalInBytes = prefetch_scan_interval_in_bytes();
  PrefetchFieldsAhead         = prefetch_fields_ahead();

  // Allocation prefetch settings
  intx cache_line_size = prefetch_data_size();
  if( cache_line_size > AllocatePrefetchStepSize )
    AllocatePrefetchStepSize = cache_line_size;

  AllocatePrefetchDistance = allocate_prefetch_distance();
  AllocatePrefetchStyle    = allocate_prefetch_style();

  if (!has_blk_init()) {
    if (AllocatePrefetchInstr == 1) {
      warning("BIS instructions required for AllocatePrefetchInstr 1 unavailable");
      FLAG_SET_DEFAULT(AllocatePrefetchInstr, 0);
    }
  }

  UseSSE = 0; // Only on x86 and x64

  _supports_cx8 = has_v9();
  _supports_atomic_getset4 = true; // swap instruction

  if (is_niagara()) {
    // Indirect branch is the same cost as direct
    if (FLAG_IS_DEFAULT(UseInlineCaches)) {
      FLAG_SET_DEFAULT(UseInlineCaches, false);
    }
    // Align loops on a single instruction boundary.
    if (FLAG_IS_DEFAULT(OptoLoopAlignment)) {
      FLAG_SET_DEFAULT(OptoLoopAlignment, 4);
    }
#ifdef _LP64
    // 32-bit oops don't make sense for the 64-bit VM on sparc
    // since the 32-bit VM has the same registers and smaller objects.
    Universe::set_narrow_oop_shift(LogMinObjAlignmentInBytes);
    Universe::set_narrow_klass_shift(LogKlassAlignmentInBytes);
#endif // _LP64
#ifdef COMPILER2
    // Indirect branch is the same cost as direct
    if (FLAG_IS_DEFAULT(UseJumpTables)) {
      FLAG_SET_DEFAULT(UseJumpTables, true);
    }
    // Single-issue, so entry and loop tops are
    // aligned on a single instruction boundary
    if (FLAG_IS_DEFAULT(InteriorEntryAlignment)) {
      FLAG_SET_DEFAULT(InteriorEntryAlignment, 4);
    }
    if (is_niagara_plus()) {
      if (has_blk_init() && UseTLAB &&
          FLAG_IS_DEFAULT(AllocatePrefetchInstr)) {
        // Use BIS instruction for TLAB allocation prefetch.
        FLAG_SET_DEFAULT(AllocatePrefetchInstr, 1);
      }
      if (FLAG_IS_DEFAULT(AllocatePrefetchDistance)) {
        if (AllocatePrefetchInstr == 0) {
          // Use different prefetch distance without BIS
          FLAG_SET_DEFAULT(AllocatePrefetchDistance, 256);
        } else {
          // Use smaller prefetch distance with BIS
          FLAG_SET_DEFAULT(AllocatePrefetchDistance, 64);
        }
      }
      if (is_T4()) {
        // Double number of prefetched cache lines on T4
        // since L2 cache line size is smaller (32 bytes).
        if (FLAG_IS_DEFAULT(AllocatePrefetchLines)) {
          FLAG_SET_ERGO(intx, AllocatePrefetchLines, AllocatePrefetchLines*2);
        }
        if (FLAG_IS_DEFAULT(AllocateInstancePrefetchLines)) {
          FLAG_SET_ERGO(intx, AllocateInstancePrefetchLines, AllocateInstancePrefetchLines*2);
        }
      }
    }

    if (AllocatePrefetchInstr == 1) {
      // Use allocation prefetch style 3 because BIS instructions
      // require aligned memory addresses.
      FLAG_SET_DEFAULT(AllocatePrefetchStyle, 3);
    }
#endif /* COMPILER2 */
  }

  // Use hardware population count instruction if available.
  if (has_hardware_popc()) {
    if (FLAG_IS_DEFAULT(UsePopCountInstruction)) {
      FLAG_SET_DEFAULT(UsePopCountInstruction, true);
    }
  } else if (UsePopCountInstruction) {
    warning("POPC instruction is not available on this CPU");
    FLAG_SET_DEFAULT(UsePopCountInstruction, false);
  }

  // T4 and newer Sparc cpus have new compare and branch instruction.
  if (has_cbcond()) {
    if (FLAG_IS_DEFAULT(UseCBCond)) {
      FLAG_SET_DEFAULT(UseCBCond, true);
    }
  } else if (UseCBCond) {
    warning("CBCOND instruction is not available on this CPU");
    FLAG_SET_DEFAULT(UseCBCond, false);
  }

  assert(BlockZeroingLowLimit > 0, "invalid value");
  if (has_block_zeroing() && cache_line_size > 0) {
    if (FLAG_IS_DEFAULT(UseBlockZeroing)) {
      FLAG_SET_DEFAULT(UseBlockZeroing, true);
    }
  } else if (UseBlockZeroing) {
    warning("BIS zeroing instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseBlockZeroing, false);
  }

  assert(BlockCopyLowLimit > 0, "invalid value");
  if (has_block_zeroing() && cache_line_size > 0) { // has_blk_init() && is_T4(): core's local L2 cache
    if (FLAG_IS_DEFAULT(UseBlockCopy)) {
      FLAG_SET_DEFAULT(UseBlockCopy, true);
    }
  } else if (UseBlockCopy) {
    warning("BIS instructions are not available or expensive on this CPU");
    FLAG_SET_DEFAULT(UseBlockCopy, false);
  }

#ifdef COMPILER2
  // T4 and newer Sparc cpus have fast RDPC.
  if (has_fast_rdpc() && FLAG_IS_DEFAULT(UseRDPCForConstantTableBase)) {
    FLAG_SET_DEFAULT(UseRDPCForConstantTableBase, true);
  }

  // Currently not supported anywhere.
  FLAG_SET_DEFAULT(UseFPUForSpilling, false);

  MaxVectorSize = 8;

  assert((InteriorEntryAlignment % relocInfo::addr_unit()) == 0, "alignment is not a multiple of NOP size");
#endif

  assert((CodeEntryAlignment % relocInfo::addr_unit()) == 0, "alignment is not a multiple of NOP size");
  assert((OptoLoopAlignment % relocInfo::addr_unit()) == 0, "alignment is not a multiple of NOP size");

  char buf[512];
  jio_snprintf(buf, sizeof(buf), "%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s",
               (has_v9() ? ", v9" : (has_v8() ? ", v8" : "")),
               (has_hardware_popc() ? ", popc" : ""),
               (has_vis1() ? ", vis1" : ""),
               (has_vis2() ? ", vis2" : ""),
               (has_vis3() ? ", vis3" : ""),
               (has_blk_init() ? ", blk_init" : ""),
               (has_cbcond() ? ", cbcond" : ""),
               (has_aes() ? ", aes" : ""),
               (has_sha1() ? ", sha1" : ""),
               (has_sha256() ? ", sha256" : ""),
               (has_sha512() ? ", sha512" : ""),
               (has_crc32c() ? ", crc32c" : ""),
               (is_ultra3() ? ", ultra3" : ""),
               (is_sun4v() ? ", sun4v" : ""),
               (is_niagara_plus() ? ", niagara_plus" : (is_niagara() ? ", niagara" : "")),
               (is_sparc64() ? ", sparc64" : ""),
               (!has_hardware_mul32() ? ", no-mul32" : ""),
               (!has_hardware_div32() ? ", no-div32" : ""),
               (!has_hardware_fsmuld() ? ", no-fsmuld" : ""));

  // buf is started with ", " or is empty
  _features_string = os::strdup(strlen(buf) > 2 ? buf + 2 : buf);

  // UseVIS is set to the smallest of what hardware supports and what
  // the command line requires.  I.e., you cannot set UseVIS to 3 on
  // older UltraSparc which do not support it.
  if (UseVIS > 3) UseVIS=3;
  if (UseVIS < 0) UseVIS=0;
  if (!has_vis3()) // Drop to 2 if no VIS3 support
    UseVIS = MIN2((intx)2,UseVIS);
  if (!has_vis2()) // Drop to 1 if no VIS2 support
    UseVIS = MIN2((intx)1,UseVIS);
  if (!has_vis1()) // Drop to 0 if no VIS1 support
    UseVIS = 0;

  // SPARC T4 and above should have support for AES instructions
  if (has_aes()) {
    if (FLAG_IS_DEFAULT(UseAES)) {
      FLAG_SET_DEFAULT(UseAES, true);
    }
    if (!UseAES) {
      if (UseAESIntrinsics && !FLAG_IS_DEFAULT(UseAESIntrinsics)) {
        warning("AES intrinsics require UseAES flag to be enabled. Intrinsics will be disabled.");
      }
      FLAG_SET_DEFAULT(UseAESIntrinsics, false);
    } else {
      // The AES intrinsic stubs require AES instruction support (of course)
      // but also require VIS3 mode or higher for instructions it use.
      if (UseVIS > 2) {
        if (FLAG_IS_DEFAULT(UseAESIntrinsics)) {
          FLAG_SET_DEFAULT(UseAESIntrinsics, true);
        }
      } else {
        if (UseAESIntrinsics && !FLAG_IS_DEFAULT(UseAESIntrinsics)) {
          warning("SPARC AES intrinsics require VIS3 instructions. Intrinsics will be disabled.");
        }
        FLAG_SET_DEFAULT(UseAESIntrinsics, false);
      }
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
  }

  if (UseAESCTRIntrinsics) {
    warning("AES/CTR intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseAESCTRIntrinsics, false);
  }

  // GHASH/GCM intrinsics
  if (has_vis3() && (UseVIS > 2)) {
    if (FLAG_IS_DEFAULT(UseGHASHIntrinsics)) {
      UseGHASHIntrinsics = true;
    }
  } else if (UseGHASHIntrinsics) {
    if (!FLAG_IS_DEFAULT(UseGHASHIntrinsics))
      warning("GHASH intrinsics require VIS3 instruction support. Intrinsics will be disabled");
    FLAG_SET_DEFAULT(UseGHASHIntrinsics, false);
  }

  // SHA1, SHA256, and SHA512 instructions were added to SPARC T-series at different times
  if (has_sha1() || has_sha256() || has_sha512()) {
    if (UseVIS > 0) { // SHA intrinsics use VIS1 instructions
      if (FLAG_IS_DEFAULT(UseSHA)) {
        FLAG_SET_DEFAULT(UseSHA, true);
      }
    } else {
      if (UseSHA) {
        warning("SPARC SHA intrinsics require VIS1 instruction support. Intrinsics will be disabled.");
        FLAG_SET_DEFAULT(UseSHA, false);
      }
    }
  } else if (UseSHA) {
    warning("SHA instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseSHA, false);
  }

  if (UseSHA && has_sha1()) {
    if (FLAG_IS_DEFAULT(UseSHA1Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA1Intrinsics, true);
    }
  } else if (UseSHA1Intrinsics) {
    warning("Intrinsics for SHA-1 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA1Intrinsics, false);
  }

  if (UseSHA && has_sha256()) {
    if (FLAG_IS_DEFAULT(UseSHA256Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA256Intrinsics, true);
    }
  } else if (UseSHA256Intrinsics) {
    warning("Intrinsics for SHA-224 and SHA-256 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA256Intrinsics, false);
  }

  if (UseSHA && has_sha512()) {
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

  // SPARC T4 and above should have support for CRC32C instruction
  if (has_crc32c()) {
    if (UseVIS > 2) { // CRC32C intrinsics use VIS3 instructions
      if (FLAG_IS_DEFAULT(UseCRC32CIntrinsics)) {
        FLAG_SET_DEFAULT(UseCRC32CIntrinsics, true);
      }
    } else {
      if (UseCRC32CIntrinsics) {
        warning("SPARC CRC32C intrinsics require VIS3 instruction support. Intrinsics will be disabled.");
        FLAG_SET_DEFAULT(UseCRC32CIntrinsics, false);
      }
    }
  } else if (UseCRC32CIntrinsics) {
    warning("CRC32C instruction is not available on this CPU");
    FLAG_SET_DEFAULT(UseCRC32CIntrinsics, false);
  }

  if (UseVIS > 2) {
    if (FLAG_IS_DEFAULT(UseAdler32Intrinsics)) {
      FLAG_SET_DEFAULT(UseAdler32Intrinsics, true);
    }
  } else if (UseAdler32Intrinsics) {
    warning("SPARC Adler32 intrinsics require VIS3 instruction support. Intrinsics will be disabled.");
    FLAG_SET_DEFAULT(UseAdler32Intrinsics, false);
  }

  if (UseVIS > 2) {
    if (FLAG_IS_DEFAULT(UseCRC32Intrinsics)) {
      FLAG_SET_DEFAULT(UseCRC32Intrinsics, true);
    }
  } else if (UseCRC32Intrinsics) {
    warning("SPARC CRC32 intrinsics require VIS3 insructions support. Intriniscs will be disabled");
    FLAG_SET_DEFAULT(UseCRC32Intrinsics, false);
  }

  if (UseVectorizedMismatchIntrinsic) {
    warning("UseVectorizedMismatchIntrinsic specified, but not available on this CPU.");
    FLAG_SET_DEFAULT(UseVectorizedMismatchIntrinsic, false);
  }

  if (FLAG_IS_DEFAULT(ContendedPaddingWidth) &&
    (cache_line_size > ContendedPaddingWidth))
    ContendedPaddingWidth = cache_line_size;

  // This machine does not allow unaligned memory accesses
  if (UseUnalignedAccesses) {
    if (!FLAG_IS_DEFAULT(UseUnalignedAccesses))
      warning("Unaligned memory access is not available on this CPU");
    FLAG_SET_DEFAULT(UseUnalignedAccesses, false);
  }

  if (log_is_enabled(Info, os, cpu)) {
    ResourceMark rm;
    outputStream* log = Log(os, cpu)::info_stream();
    log->print_cr("L1 data cache line size: %u", L1_data_cache_line_size());
    log->print_cr("L2 data cache line size: %u", L2_data_cache_line_size());
    log->print("Allocation");
    if (AllocatePrefetchStyle <= 0) {
      log->print(": no prefetching");
    } else {
      log->print(" prefetching: ");
      if (AllocatePrefetchInstr == 0) {
          log->print("PREFETCH");
      } else if (AllocatePrefetchInstr == 1) {
          log->print("BIS");
      }
      if (AllocatePrefetchLines > 1) {
        log->print_cr(" at distance %d, %d lines of %d bytes", (int) AllocatePrefetchDistance, (int) AllocatePrefetchLines, (int) AllocatePrefetchStepSize);
      } else {
        log->print_cr(" at distance %d, one line of %d bytes", (int) AllocatePrefetchDistance, (int) AllocatePrefetchStepSize);
      }
    }
    if (PrefetchCopyIntervalInBytes > 0) {
      log->print_cr("PrefetchCopyIntervalInBytes %d", (int) PrefetchCopyIntervalInBytes);
    }
    if (PrefetchScanIntervalInBytes > 0) {
      log->print_cr("PrefetchScanIntervalInBytes %d", (int) PrefetchScanIntervalInBytes);
    }
    if (PrefetchFieldsAhead > 0) {
      log->print_cr("PrefetchFieldsAhead %d", (int) PrefetchFieldsAhead);
    }
    if (ContendedPaddingWidth > 0) {
      log->print_cr("ContendedPaddingWidth %d", (int) ContendedPaddingWidth);
    }
  }
}

void VM_Version::print_features() {
  tty->print_cr("Version:%s", _features);
}

int VM_Version::determine_features() {
  if (UseV8InstrsOnly) {
    log_info(os, cpu)("Version is Forced-V8");
    return generic_v8_m;
  }

  int features = platform_features(unknown_m); // platform_features() is os_arch specific

  if (features == unknown_m) {
    features = generic_v9_m;
    log_info(os)("Cannot recognize SPARC version. Default to V9");
  }

  assert(is_T_family(features) == is_niagara(features), "Niagara should be T series");
  if (UseNiagaraInstrs) { // Force code generation for Niagara
    if (is_T_family(features)) {
      // Happy to accomodate...
    } else {
      log_info(os, cpu)("Version is Forced-Niagara");
      features |= T_family_m;
    }
  } else {
    if (is_T_family(features) && !FLAG_IS_DEFAULT(UseNiagaraInstrs)) {
      log_info(os, cpu)("Version is Forced-Not-Niagara");
      features &= ~(T_family_m | T1_model_m);
    } else {
      // Happy to accomodate...
    }
  }

  return features;
}

static uint64_t saved_features = 0;

void VM_Version::allow_all() {
  saved_features = _features;
  _features      = all_features_m;
}

void VM_Version::revert() {
  _features = saved_features;
}

unsigned int VM_Version::calc_parallel_worker_threads() {
  unsigned int result;
  if (is_M_series()) {
    // for now, use same gc thread calculation for M-series as for niagara-plus
    // in future, we may want to tweak parameters for nof_parallel_worker_thread
    result = nof_parallel_worker_threads(5, 16, 8);
  } else if (is_niagara_plus()) {
    result = nof_parallel_worker_threads(5, 16, 8);
  } else {
    result = nof_parallel_worker_threads(5, 8, 8);
  }
  return result;
}


int VM_Version::parse_features(const char* implementation) {
  int features = unknown_m;
  // Convert to UPPER case before compare.
  char* impl = os::strdup_check_oom(implementation);

  for (int i = 0; impl[i] != 0; i++)
    impl[i] = (char)toupper((uint)impl[i]);

  if (strstr(impl, "SPARC64") != NULL) {
    features |= sparc64_family_m;
  } else if (strstr(impl, "SPARC-M") != NULL) {
    // M-series SPARC is based on T-series.
    features |= (M_family_m | T_family_m);
  } else if (strstr(impl, "SPARC-T") != NULL) {
    features |= T_family_m;
    if (strstr(impl, "SPARC-T1") != NULL) {
      features |= T1_model_m;
    }
  } else {
    if (strstr(impl, "SPARC") == NULL) {
#ifndef PRODUCT
      // kstat on Solaris 8 virtual machines (branded zones)
      // returns "(unsupported)" implementation. Solaris 8 is not
      // supported anymore, but include this check to be on the
      // safe side.
      warning("Can't parse CPU implementation = '%s', assume generic SPARC", impl);
#endif
    }
  }
  os::free((void*)impl);
  return features;
}
