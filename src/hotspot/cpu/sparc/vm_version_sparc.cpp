/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "vm_version_sparc.hpp"

#include <sys/mman.h>

uint VM_Version::_L2_data_cache_line_size = 0;

void VM_Version::initialize() {
  assert(_features != 0, "System pre-initialization is not complete.");
  guarantee(VM_Version::has_v9(), "only SPARC v9 is supported");

  PrefetchCopyIntervalInBytes = prefetch_copy_interval_in_bytes();
  PrefetchScanIntervalInBytes = prefetch_scan_interval_in_bytes();
  PrefetchFieldsAhead         = prefetch_fields_ahead();

  // Allocation prefetch settings

  AllocatePrefetchDistance = allocate_prefetch_distance();
  AllocatePrefetchStyle    = allocate_prefetch_style();

  intx cache_line_size = prefetch_data_size();

  if (FLAG_IS_DEFAULT(AllocatePrefetchStepSize)) {
    AllocatePrefetchStepSize = MAX2(AllocatePrefetchStepSize, cache_line_size);
  }

  if (AllocatePrefetchInstr == 1) {
    if (!has_blk_init()) {
      warning("BIS instructions required for AllocatePrefetchInstr 1 unavailable");
      FLAG_SET_DEFAULT(AllocatePrefetchInstr, 0);
    }
    if (cache_line_size <= 0) {
      warning("Cache-line size must be known for AllocatePrefetchInstr 1 to work");
      FLAG_SET_DEFAULT(AllocatePrefetchInstr, 0);
    }
  }

  UseSSE = false;                   // Only used on x86 and x64.

  _supports_cx8 = true;             // All SPARC V9 implementations.
  _supports_atomic_getset4 = true;  // Using the 'swap' instruction.

  if (has_fast_ind_br() && FLAG_IS_DEFAULT(UseInlineCaches)) {
    // Indirect and direct branches are cost equivalent.
    FLAG_SET_DEFAULT(UseInlineCaches, false);
  }
  // Align loops on the proper instruction boundary to fill the instruction
  // fetch buffer.
  if (FLAG_IS_DEFAULT(OptoLoopAlignment)) {
    FLAG_SET_DEFAULT(OptoLoopAlignment, VM_Version::insn_fetch_alignment);
  }

  // 32-bit oops don't make sense for the 64-bit VM on SPARC since the 32-bit
  // VM has the same registers and smaller objects.
  Universe::set_narrow_oop_shift(LogMinObjAlignmentInBytes);
  Universe::set_narrow_klass_shift(LogKlassAlignmentInBytes);

#ifdef COMPILER2
  if (has_fast_ind_br() && FLAG_IS_DEFAULT(UseJumpTables)) {
    // Indirect and direct branches are cost equivalent.
    FLAG_SET_DEFAULT(UseJumpTables, true);
  }
  // Entry and loop tops are aligned to fill the instruction fetch buffer.
  if (FLAG_IS_DEFAULT(InteriorEntryAlignment)) {
    FLAG_SET_DEFAULT(InteriorEntryAlignment, VM_Version::insn_fetch_alignment);
  }
  if (UseTLAB && cache_line_size > 0 &&
      FLAG_IS_DEFAULT(AllocatePrefetchInstr)) {
    if (has_fast_bis()) {
      // Use BIS instruction for TLAB allocation prefetch.
      FLAG_SET_DEFAULT(AllocatePrefetchInstr, 1);
    }
    else if (has_sparc5()) {
      // Use prefetch instruction to avoid partial RAW issue on Core C4 processors,
      // also use prefetch style 3.
      FLAG_SET_DEFAULT(AllocatePrefetchInstr, 0);
      if (FLAG_IS_DEFAULT(AllocatePrefetchStyle)) {
        FLAG_SET_DEFAULT(AllocatePrefetchStyle, 3);
      }
    }
  }
  if (AllocatePrefetchInstr == 1) {
    // Use allocation prefetch style 3 because BIS instructions require
    // aligned memory addresses.
    FLAG_SET_DEFAULT(AllocatePrefetchStyle, 3);
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

  // We increase the number of prefetched cache lines, to use just a bit more
  // aggressive approach, when the L2-cache line size is small (32 bytes), or
  // when running on newer processor implementations, such as the Core C4.
  bool inc_prefetch = cache_line_size > 0 && (cache_line_size < 64 || has_sparc5());

  if (inc_prefetch) {
    // We use a factor two for small cache line sizes (as before) but a slightly
    // more conservative increase when running on more recent hardware that will
    // benefit from just a bit more aggressive prefetching.
    if (FLAG_IS_DEFAULT(AllocatePrefetchLines)) {
      const int ap_lns = AllocatePrefetchLines;
      const int ap_inc = cache_line_size < 64 ? ap_lns : (ap_lns + 1) / 2;
      FLAG_SET_ERGO(intx, AllocatePrefetchLines, ap_lns + ap_inc);
    }
    if (FLAG_IS_DEFAULT(AllocateInstancePrefetchLines)) {
      const int ip_lns = AllocateInstancePrefetchLines;
      const int ip_inc = cache_line_size < 64 ? ip_lns : (ip_lns + 1) / 2;
      FLAG_SET_ERGO(intx, AllocateInstancePrefetchLines, ip_lns + ip_inc);
    }
  }
#endif /* COMPILER2 */

  // Use hardware population count instruction if available.
  if (has_popc()) {
    if (FLAG_IS_DEFAULT(UsePopCountInstruction)) {
      FLAG_SET_DEFAULT(UsePopCountInstruction, true);
    }
  } else if (UsePopCountInstruction) {
    warning("POPC instruction is not available on this CPU");
    FLAG_SET_DEFAULT(UsePopCountInstruction, false);
  }

  // Use compare and branch instructions if available.
  if (has_cbcond()) {
    if (FLAG_IS_DEFAULT(UseCBCond)) {
      FLAG_SET_DEFAULT(UseCBCond, true);
    }
  } else if (UseCBCond) {
    warning("CBCOND instruction is not available on this CPU");
    FLAG_SET_DEFAULT(UseCBCond, false);
  }

  // Use 'mpmul' instruction if available.
  if (has_mpmul()) {
    if (FLAG_IS_DEFAULT(UseMPMUL)) {
      FLAG_SET_DEFAULT(UseMPMUL, true);
    }
  } else if (UseMPMUL) {
    warning("MPMUL instruction is not available on this CPU");
    FLAG_SET_DEFAULT(UseMPMUL, false);
  }

  assert(BlockZeroingLowLimit > 0, "invalid value");

  if (has_blk_zeroing() && cache_line_size > 0) {
    if (FLAG_IS_DEFAULT(UseBlockZeroing)) {
      FLAG_SET_DEFAULT(UseBlockZeroing, true);
    }
  } else if (UseBlockZeroing) {
    warning("BIS zeroing instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseBlockZeroing, false);
  }

  assert(BlockCopyLowLimit > 0, "invalid value");

  if (has_blk_zeroing() && cache_line_size > 0) {
    if (FLAG_IS_DEFAULT(UseBlockCopy)) {
      FLAG_SET_DEFAULT(UseBlockCopy, true);
    }
  } else if (UseBlockCopy) {
    warning("BIS instructions are not available or expensive on this CPU");
    FLAG_SET_DEFAULT(UseBlockCopy, false);
  }

#ifdef COMPILER2
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
  jio_snprintf(buf, sizeof(buf),
               "%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s"
               "%s%s%s%s%s%s%s%s%s" "%s%s%s%s%s%s%s%s%s"
               "%s%s%s%s%s%s%s",
               (has_v9()          ? "v9" : ""),
               (has_popc()        ? ", popc" : ""),
               (has_vis1()        ? ", vis1" : ""),
               (has_vis2()        ? ", vis2" : ""),
               (has_blk_init()    ? ", blk_init" : ""),
               (has_fmaf()        ? ", fmaf" : ""),
               (has_hpc()         ? ", hpc" : ""),
               (has_ima()         ? ", ima" : ""),
               (has_aes()         ? ", aes" : ""),
               (has_des()         ? ", des" : ""),
               (has_kasumi()      ? ", kas" : ""),
               (has_camellia()    ? ", cam" : ""),
               (has_md5()         ? ", md5" : ""),
               (has_sha1()        ? ", sha1" : ""),
               (has_sha256()      ? ", sha256" : ""),
               (has_sha512()      ? ", sha512" : ""),
               (has_mpmul()       ? ", mpmul" : ""),
               (has_mont()        ? ", mont" : ""),
               (has_pause()       ? ", pause" : ""),
               (has_cbcond()      ? ", cbcond" : ""),
               (has_crc32c()      ? ", crc32c" : ""),

               (has_athena_plus() ? ", athena_plus" : ""),
               (has_vis3b()       ? ", vis3b" : ""),
               (has_adi()         ? ", adi" : ""),
               (has_sparc5()      ? ", sparc5" : ""),
               (has_mwait()       ? ", mwait" : ""),
               (has_xmpmul()      ? ", xmpmul" : ""),
               (has_xmont()       ? ", xmont" : ""),
               (has_pause_nsec()  ? ", pause_nsec" : ""),
               (has_vamask()      ? ", vamask" : ""),

               (has_sparc6()      ? ", sparc6" : ""),
               (has_dictunp()     ? ", dictunp" : ""),
               (has_fpcmpshl()    ? ", fpcmpshl" : ""),
               (has_rle()         ? ", rle" : ""),
               (has_sha3()        ? ", sha3" : ""),
               (has_athena_plus2()? ", athena_plus2" : ""),
               (has_vis3c()       ? ", vis3c" : ""),
               (has_sparc5b()     ? ", sparc5b" : ""),
               (has_mme()         ? ", mme" : ""),

               (has_fast_idiv()   ? ", *idiv" : ""),
               (has_fast_rdpc()   ? ", *rdpc" : ""),
               (has_fast_bis()    ? ", *bis" : ""),
               (has_fast_ld()     ? ", *ld" : ""),
               (has_fast_cmove()  ? ", *cmove" : ""),
               (has_fast_ind_br() ? ", *ind_br" : ""),
               (has_blk_zeroing() ? ", *blk_zeroing" : ""));

  assert(strlen(buf) >= 2, "must be");

  _features_string = os::strdup(buf);

  log_info(os, cpu)("SPARC features detected: %s", _features_string);

  // UseVIS is set to the smallest of what hardware supports and what the command
  // line requires, i.e. you cannot set UseVIS to 3 on older UltraSparc which do
  // not support it.

  if (UseVIS > 3) UseVIS = 3;
  if (UseVIS < 0) UseVIS = 0;
  if (!has_vis3()) // Drop to 2 if no VIS3 support
    UseVIS = MIN2((intx)2, UseVIS);
  if (!has_vis2()) // Drop to 1 if no VIS2 support
    UseVIS = MIN2((intx)1, UseVIS);
  if (!has_vis1()) // Drop to 0 if no VIS1 support
    UseVIS = 0;

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

  if (has_fmaf()) {
    if (FLAG_IS_DEFAULT(UseFMA)) {
      UseFMA = true;
    }
  } else if (UseFMA) {
    warning("FMA instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseFMA, false);
  }

  // SHA1, SHA256, and SHA512 instructions were added to SPARC at different times
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
    warning("SPARC CRC32 intrinsics require VIS3 instructions support. Intrinsics will be disabled");
    FLAG_SET_DEFAULT(UseCRC32Intrinsics, false);
  }

  if (UseVIS > 2) {
    if (FLAG_IS_DEFAULT(UseMultiplyToLenIntrinsic)) {
      FLAG_SET_DEFAULT(UseMultiplyToLenIntrinsic, true);
    }
  } else if (UseMultiplyToLenIntrinsic) {
    warning("SPARC multiplyToLen intrinsics require VIS3 instructions support. Intrinsics will be disabled");
    FLAG_SET_DEFAULT(UseMultiplyToLenIntrinsic, false);
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
    LogStream ls(Log(os, cpu)::info());
    outputStream* log = &ls;
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
  tty->print("ISA features [0x%0" PRIx64 "]:", _features);
  if (_features_string != NULL) {
    tty->print(" %s", _features_string);
  }
  tty->cr();
}

void VM_Version::determine_features() {
  platform_features();      // platform_features() is os_arch specific.

  assert(has_v9(), "must be");

  if (UseNiagaraInstrs) {   // Limit code generation to Niagara.
    _features &= niagara1_msk;
  }
}

static uint64_t saved_features = 0;

void VM_Version::allow_all() {
  saved_features = _features;
  _features      = full_feature_msk;
}

void VM_Version::revert() {
  _features = saved_features;
}
