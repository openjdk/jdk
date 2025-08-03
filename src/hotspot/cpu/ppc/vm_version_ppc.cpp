/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2025 SAP SE. All rights reserved.
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

#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "compiler/disassembler.hpp"
#include "jvm.h"
#include "memory/resourceArea.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/align.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

#include <sys/sysinfo.h>
#if defined(_AIX)
#include "os_aix.hpp"
#include <libperfstat.h>
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
    if (VM_Version::has_brw()) {
      FLAG_SET_ERGO(PowerArchitecturePPC64, 10);
    } else if (VM_Version::has_darn()) {
      FLAG_SET_ERGO(PowerArchitecturePPC64, 9);
    } else {
      FLAG_SET_ERGO(PowerArchitecturePPC64, 8);
    }
  }

  bool PowerArchitecturePPC64_ok = false;
  switch (PowerArchitecturePPC64) {
    case 10: if (!VM_Version::has_brw()    ) break;
    case  9: if (!VM_Version::has_darn()   ) break;
    case  8: PowerArchitecturePPC64_ok = true; break;
    default: break;
  }
  guarantee(PowerArchitecturePPC64_ok, "PowerArchitecturePPC64 cannot be set to "
            "%zu on this machine", PowerArchitecturePPC64);

  // Power 8: Configure Data Stream Control Register.
  if (VM_Version::has_mfdscr()) {
    config_dscr();
  }

  if (!UseSIGTRAP) {
    MSG(TrapBasedICMissChecks);
    MSG(TrapBasedNullChecks);
    FLAG_SET_ERGO(TrapBasedNullChecks,       false);
    FLAG_SET_ERGO(TrapBasedICMissChecks,     false);
  }

#ifdef COMPILER2
  if (!UseSIGTRAP) {
    MSG(TrapBasedRangeChecks);
    FLAG_SET_ERGO(TrapBasedRangeChecks, false);
  }

  if (PowerArchitecturePPC64 >= 9) {
    // Performance is good since Power9.
    if (FLAG_IS_DEFAULT(SuperwordUseVSX)) {
      FLAG_SET_ERGO(SuperwordUseVSX, true);
    }
  }

  MaxVectorSize = SuperwordUseVSX ? 16 : 8;
  if (FLAG_IS_DEFAULT(AlignVector)) {
    FLAG_SET_ERGO(AlignVector, false);
  }

  if (PowerArchitecturePPC64 >= 9) {
    if (FLAG_IS_DEFAULT(UseCountTrailingZerosInstructionsPPC64)) {
      FLAG_SET_ERGO(UseCountTrailingZerosInstructionsPPC64, true);
    }
    if (FLAG_IS_DEFAULT(UseCharacterCompareIntrinsics)) {
      FLAG_SET_ERGO(UseCharacterCompareIntrinsics, true);
    }
    if (SuperwordUseVSX) {
      if (FLAG_IS_DEFAULT(UseVectorByteReverseInstructionsPPC64)) {
        FLAG_SET_ERGO(UseVectorByteReverseInstructionsPPC64, true);
      }
    } else if (UseVectorByteReverseInstructionsPPC64) {
      warning("UseVectorByteReverseInstructionsPPC64 specified, but needs SuperwordUseVSX.");
      FLAG_SET_DEFAULT(UseVectorByteReverseInstructionsPPC64, false);
    }
    if (FLAG_IS_DEFAULT(UseBASE64Intrinsics)) {
      FLAG_SET_ERGO(UseBASE64Intrinsics, true);
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
    if (UseVectorByteReverseInstructionsPPC64) {
      warning("UseVectorByteReverseInstructionsPPC64 specified, but needs at least Power9.");
      FLAG_SET_DEFAULT(UseVectorByteReverseInstructionsPPC64, false);
    }
    if (UseBASE64Intrinsics) {
      warning("UseBASE64Intrinsics specified, but needs at least Power9.");
      FLAG_SET_DEFAULT(UseBASE64Intrinsics, false);
    }
  }

  if (PowerArchitecturePPC64 >= 10) {
    if (FLAG_IS_DEFAULT(UseByteReverseInstructions)) {
        FLAG_SET_ERGO(UseByteReverseInstructions, true);
    }
  } else {
    if (UseByteReverseInstructions) {
      warning("UseByteReverseInstructions specified, but needs at least Power10.");
      FLAG_SET_DEFAULT(UseByteReverseInstructions, false);
    }
  }

  if (OptimizeFill) {
    warning("OptimizeFill is not supported on this CPU.");
    FLAG_SET_DEFAULT(OptimizeFill, false);
  }

  if (OptoScheduling) {
    // The OptoScheduling information is not maintained in ppd.ad.
    warning("OptoScheduling is not supported on this CPU.");
    FLAG_SET_DEFAULT(OptoScheduling, false);
  }
#endif

  // Create and print feature-string.
  char buf[(num_features+1) * 16]; // Max 16 chars per feature.
  jio_snprintf(buf, sizeof(buf),
               "ppc64 sha aes%s%s%s",
               (has_mfdscr()  ? " mfdscr"  : ""),
               (has_darn()    ? " darn"    : ""),
               (has_brw()     ? " brw"     : "")
               // Make sure number of %s matches num_features!
              );
  _cpu_info_string = os::strdup(buf);
  if (Verbose) {
    print_features();
  }

  // Used by C1.
  _supports_atomic_getset4 = true;
  _supports_atomic_getadd4 = true;
  _supports_atomic_getset8 = true;
  _supports_atomic_getadd8 = true;

  intx cache_line_size = L1_data_cache_line_size();

  if (PowerArchitecturePPC64 >= 9) {
    if (os::supports_map_sync() == true) {
      _data_cache_line_flush_size = cache_line_size;
    }
  }

  if (FLAG_IS_DEFAULT(AllocatePrefetchStyle)) AllocatePrefetchStyle = 1;

  if (cache_line_size > AllocatePrefetchStepSize) AllocatePrefetchStepSize = cache_line_size;
  // PPC processors have an automatic prefetch engine.
  if (FLAG_IS_DEFAULT(AllocatePrefetchLines)) AllocatePrefetchLines = 1;
  if (AllocatePrefetchDistance < 0) AllocatePrefetchDistance = 3 * cache_line_size;

  assert(AllocatePrefetchLines > 0, "invalid value");
  if (AllocatePrefetchLines < 1) { // Set valid value in product VM.
    AllocatePrefetchLines = 1; // Conservative value.
  }

  if (AllocatePrefetchStyle == 3 && AllocatePrefetchDistance < cache_line_size) {
    AllocatePrefetchStyle = 1; // Fall back if inappropriate.
  }

  assert(AllocatePrefetchStyle >= 0, "AllocatePrefetchStyle should be positive");

  if (FLAG_IS_DEFAULT(ContendedPaddingWidth) && (cache_line_size > ContendedPaddingWidth)) {
    ContendedPaddingWidth = cache_line_size;
  }

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
  if (FLAG_IS_DEFAULT(UseAES)) {
    UseAES = true;
  }

  if (FLAG_IS_DEFAULT(UseAESIntrinsics)) {
    UseAESIntrinsics = true;
  }

  if (UseAESCTRIntrinsics) {
    warning("AES/CTR intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseAESCTRIntrinsics, false);
  }

  if (FLAG_IS_DEFAULT(UseGHASHIntrinsics)) {
    UseGHASHIntrinsics = true;
  }

  if (FLAG_IS_DEFAULT(UseFMA)) {
    FLAG_SET_DEFAULT(UseFMA, true);
  }

  if (UseMD5Intrinsics) {
    warning("MD5 intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseMD5Intrinsics, false);
  }

  if (FLAG_IS_DEFAULT(UseSHA)) {
    UseSHA = true;
  }

  if (UseSHA1Intrinsics) {
    warning("Intrinsics for SHA-1 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA1Intrinsics, false);
  }

  if (UseSHA) {
    if (FLAG_IS_DEFAULT(UseSHA256Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA256Intrinsics, true);
    }
  } else if (UseSHA256Intrinsics) {
    warning("Intrinsics for SHA-224 and SHA-256 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA256Intrinsics, false);
  }

  if (UseSHA) {
    if (FLAG_IS_DEFAULT(UseSHA512Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA512Intrinsics, true);
    }
  } else if (UseSHA512Intrinsics) {
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


#ifdef COMPILER2
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
#endif

  if (UseVectorizedMismatchIntrinsic) {
    warning("UseVectorizedMismatchIntrinsic specified, but not available on this CPU.");
    FLAG_SET_DEFAULT(UseVectorizedMismatchIntrinsic, false);
  }

  // This machine allows unaligned memory accesses
  if (FLAG_IS_DEFAULT(UseUnalignedAccesses)) {
    FLAG_SET_DEFAULT(UseUnalignedAccesses, true);
  }

  check_virtualizations();
}

void VM_Version::check_virtualizations() {
#if defined(_AIX)
  int rc = 0;
  perfstat_partition_total_t pinfo;
  rc = perfstat_partition_total(nullptr, &pinfo, sizeof(perfstat_partition_total_t), 1);
  if (rc == 1) {
    Abstract_VM_Version::_detected_virtualization = PowerVM;
  }
#else
  const char* info_file = "/proc/ppc64/lparcfg";
  // system_type=...qemu indicates PowerKVM
  // e.g. system_type=IBM pSeries (emulated by qemu)
  char line[500];
  FILE* fp = os::fopen(info_file, "r");
  if (fp == nullptr) {
    return;
  }
  const char* system_type="system_type=";  // in case this line contains qemu, it is KVM
  const char* num_lpars="NumLpars="; // in case of non-KVM : if this line is found it is PowerVM
  bool num_lpars_found = false;

  while (fgets(line, sizeof(line), fp) != nullptr) {
    if (strncmp(line, system_type, strlen(system_type)) == 0) {
      if (strstr(line, "qemu") != nullptr) {
        Abstract_VM_Version::_detected_virtualization = PowerKVM;
        fclose(fp);
        return;
      }
    }
    if (strncmp(line, num_lpars, strlen(num_lpars)) == 0) {
      num_lpars_found = true;
    }
  }
  if (num_lpars_found) {
    Abstract_VM_Version::_detected_virtualization = PowerVM;
  } else {
    Abstract_VM_Version::_detected_virtualization = PowerFullPartitionMode;
  }
  fclose(fp);
#endif
}

void VM_Version::print_platform_virtualization_info(outputStream* st) {
#if defined(_AIX)
  // more info about perfstat API see
  // https://www.ibm.com/support/knowledgecenter/en/ssw_aix_72/com.ibm.aix.prftools/idprftools_perfstat_glob_partition.htm
  int rc = 0;
  perfstat_partition_total_t pinfo;
  memset(&pinfo, 0, sizeof(perfstat_partition_total_t));
  rc = perfstat_partition_total(nullptr, &pinfo, sizeof(perfstat_partition_total_t), 1);
  if (rc != 1) {
    return;
  } else {
    st->print_cr("Virtualization type   : PowerVM");
  }
  // CPU information
  perfstat_cpu_total_t cpuinfo;
  memset(&cpuinfo, 0, sizeof(perfstat_cpu_total_t));
  rc = perfstat_cpu_total(nullptr, &cpuinfo, sizeof(perfstat_cpu_total_t), 1);
  if (rc != 1) {
    return;
  }

  st->print_cr("Processor description : %s", cpuinfo.description);
  st->print_cr("Processor speed       : %llu Hz", cpuinfo.processorHZ);

  st->print_cr("LPAR partition name           : %s", pinfo.name);
  st->print_cr("LPAR partition number         : %u", pinfo.lpar_id);
  st->print_cr("LPAR partition type           : %s", pinfo.type.b.shared_enabled ? "shared" : "dedicated");
  st->print_cr("LPAR mode                     : %s", pinfo.type.b.donate_enabled ? "donating" : pinfo.type.b.capped ? "capped" : "uncapped");
  st->print_cr("LPAR partition group ID       : %u", pinfo.group_id);
  st->print_cr("LPAR shared pool ID           : %u", pinfo.pool_id);

  st->print_cr("AMS (active memory sharing)   : %s", pinfo.type.b.ams_capable ? "capable" : "not capable");
  st->print_cr("AMS (active memory sharing)   : %s", pinfo.type.b.ams_enabled ? "on" : "off");
  st->print_cr("AME (active memory expansion) : %s", pinfo.type.b.ame_enabled ? "on" : "off");

  if (pinfo.type.b.ame_enabled) {
    st->print_cr("AME true memory in bytes      : %llu", pinfo.true_memory);
    st->print_cr("AME expanded memory in bytes  : %llu", pinfo.expanded_memory);
  }

  st->print_cr("SMT : %s", pinfo.type.b.smt_capable ? "capable" : "not capable");
  st->print_cr("SMT : %s", pinfo.type.b.smt_enabled ? "on" : "off");
  int ocpus = pinfo.online_cpus > 0 ?  pinfo.online_cpus : 1;
  st->print_cr("LPAR threads              : %d", cpuinfo.ncpus/ocpus);
  st->print_cr("LPAR online virtual cpus  : %d", pinfo.online_cpus);
  st->print_cr("LPAR logical cpus         : %d", cpuinfo.ncpus);
  st->print_cr("LPAR maximum virtual cpus : %u", pinfo.max_cpus);
  st->print_cr("LPAR minimum virtual cpus : %u", pinfo.min_cpus);
  st->print_cr("LPAR entitled capacity    : %4.2f", (double) (pinfo.entitled_proc_capacity/100.0));
  st->print_cr("LPAR online memory        : %llu MB", pinfo.online_memory);
  st->print_cr("LPAR maximum memory       : %llu MB", pinfo.max_memory);
  st->print_cr("LPAR minimum memory       : %llu MB", pinfo.min_memory);
#else
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
                       nullptr };
  if (!print_matching_lines_from_file(info_file, st, kw)) {
    st->print_cr("  <%s Not Available>", info_file);
  }
#endif
}

void VM_Version::print_features() {
  tty->print_cr("Version: %s L1_data_cache_line_size=%d", cpu_info_string(), L1_data_cache_line_size());

  if (Verbose) {
    if (ContendedPaddingWidth > 0) {
      tty->cr();
      tty->print_cr("ContendedPaddingWidth %d", ContendedPaddingWidth);
    }
  }
}

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
  // Keep R3_ARG1 unmodified, it contains &field (see below).
  // Keep R4_ARG2 unmodified, it contains offset = 0 (see below).
  a->mfdscr(R0);
  a->darn(R7);
  a->brw(R5, R6);
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
  if (code[feature_cntr++]) features |= mfdscr_m;
  if (code[feature_cntr++]) features |= darn_m;
  if (code[feature_cntr++]) features |= brw_m;

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

// get cpu information.
void VM_Version::initialize_cpu_information(void) {
  // do nothing if cpu info has been initialized
  if (_initialized) {
    return;
  }

  _no_of_cores  = os::processor_count();
  _no_of_threads = _no_of_cores;
  _no_of_sockets = _no_of_cores;
  snprintf(_cpu_name, CPU_TYPE_DESC_BUF_SIZE, "PowerPC POWER%lu", PowerArchitecturePPC64);
  snprintf(_cpu_desc, CPU_DETAILED_DESC_BUF_SIZE, "PPC %s", cpu_info_string());
  _initialized = true;
}
