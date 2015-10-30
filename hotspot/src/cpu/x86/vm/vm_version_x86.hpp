/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_VM_VM_VERSION_X86_HPP
#define CPU_X86_VM_VM_VERSION_X86_HPP

#include "runtime/globals_extension.hpp"
#include "runtime/vm_version.hpp"

class VM_Version : public Abstract_VM_Version {
  friend class VMStructs;
public:
  // cpuid result register layouts.  These are all unions of a uint32_t
  // (in case anyone wants access to the register as a whole) and a bitfield.

  union StdCpuid1Eax {
    uint32_t value;
    struct {
      uint32_t stepping   : 4,
               model      : 4,
               family     : 4,
               proc_type  : 2,
                          : 2,
               ext_model  : 4,
               ext_family : 8,
                          : 4;
    } bits;
  };

  union StdCpuid1Ebx { // example, unused
    uint32_t value;
    struct {
      uint32_t brand_id         : 8,
               clflush_size     : 8,
               threads_per_cpu  : 8,
               apic_id          : 8;
    } bits;
  };

  union StdCpuid1Ecx {
    uint32_t value;
    struct {
      uint32_t sse3     : 1,
               clmul    : 1,
                        : 1,
               monitor  : 1,
                        : 1,
               vmx      : 1,
                        : 1,
               est      : 1,
                        : 1,
               ssse3    : 1,
               cid      : 1,
                        : 2,
               cmpxchg16: 1,
                        : 4,
               dca      : 1,
               sse4_1   : 1,
               sse4_2   : 1,
                        : 2,
               popcnt   : 1,
                        : 1,
               aes      : 1,
                        : 1,
               osxsave  : 1,
               avx      : 1,
                        : 3;
    } bits;
  };

  union StdCpuid1Edx {
    uint32_t value;
    struct {
      uint32_t          : 4,
               tsc      : 1,
                        : 3,
               cmpxchg8 : 1,
                        : 6,
               cmov     : 1,
                        : 3,
               clflush  : 1,
                        : 3,
               mmx      : 1,
               fxsr     : 1,
               sse      : 1,
               sse2     : 1,
                        : 1,
               ht       : 1,
                        : 3;
    } bits;
  };

  union DcpCpuid4Eax {
    uint32_t value;
    struct {
      uint32_t cache_type    : 5,
                             : 21,
               cores_per_cpu : 6;
    } bits;
  };

  union DcpCpuid4Ebx {
    uint32_t value;
    struct {
      uint32_t L1_line_size  : 12,
               partitions    : 10,
               associativity : 10;
    } bits;
  };

  union TplCpuidBEbx {
    uint32_t value;
    struct {
      uint32_t logical_cpus : 16,
                            : 16;
    } bits;
  };

  union ExtCpuid1Ecx {
    uint32_t value;
    struct {
      uint32_t LahfSahf     : 1,
               CmpLegacy    : 1,
                            : 3,
               lzcnt_intel  : 1,
               lzcnt        : 1,
               sse4a        : 1,
               misalignsse  : 1,
               prefetchw    : 1,
                            : 22;
    } bits;
  };

  union ExtCpuid1Edx {
    uint32_t value;
    struct {
      uint32_t           : 22,
               mmx_amd   : 1,
               mmx       : 1,
               fxsr      : 1,
                         : 4,
               long_mode : 1,
               tdnow2    : 1,
               tdnow     : 1;
    } bits;
  };

  union ExtCpuid5Ex {
    uint32_t value;
    struct {
      uint32_t L1_line_size : 8,
               L1_tag_lines : 8,
               L1_assoc     : 8,
               L1_size      : 8;
    } bits;
  };

  union ExtCpuid7Edx {
    uint32_t value;
    struct {
      uint32_t               : 8,
              tsc_invariance : 1,
                             : 23;
    } bits;
  };

  union ExtCpuid8Ecx {
    uint32_t value;
    struct {
      uint32_t cores_per_cpu : 8,
                             : 24;
    } bits;
  };

  union SefCpuid7Eax {
    uint32_t value;
  };

  union SefCpuid7Ebx {
    uint32_t value;
    struct {
      uint32_t fsgsbase : 1,
                        : 2,
                   bmi1 : 1,
                        : 1,
                   avx2 : 1,
                        : 2,
                   bmi2 : 1,
                   erms : 1,
                        : 1,
                    rtm : 1,
                        : 4,
                avx512f : 1,
               avx512dq : 1,
                        : 1,
                    adx : 1,
                        : 6,
               avx512pf : 1,
               avx512er : 1,
               avx512cd : 1,
                        : 1,
               avx512bw : 1,
               avx512vl : 1;
    } bits;
  };

  union XemXcr0Eax {
    uint32_t value;
    struct {
      uint32_t x87     : 1,
               sse     : 1,
               ymm     : 1,
               bndregs : 1,
               bndcsr  : 1,
               opmask  : 1,
               zmm512  : 1,
               zmm32   : 1,
                       : 24;
    } bits;
  };

protected:
  static int _cpu;
  static int _model;
  static int _stepping;
  static uint64_t _cpuFeatures; // features returned by the "cpuid" instruction
                                // 0 if this instruction is not available
  static const char* _features_str;

  static address   _cpuinfo_segv_addr; // address of instruction which causes SEGV
  static address   _cpuinfo_cont_addr; // address of instruction after the one which causes SEGV

  enum {
    CPU_CX8      = (1 << 0), // next bits are from cpuid 1 (EDX)
    CPU_CMOV     = (1 << 1),
    CPU_FXSR     = (1 << 2),
    CPU_HT       = (1 << 3),
    CPU_MMX      = (1 << 4),
    CPU_3DNOW_PREFETCH = (1 << 5), // Processor supports 3dnow prefetch and prefetchw instructions
                                   // may not necessarily support other 3dnow instructions
    CPU_SSE      = (1 << 6),
    CPU_SSE2     = (1 << 7),
    CPU_SSE3     = (1 << 8),  // SSE3 comes from cpuid 1 (ECX)
    CPU_SSSE3    = (1 << 9),
    CPU_SSE4A    = (1 << 10),
    CPU_SSE4_1   = (1 << 11),
    CPU_SSE4_2   = (1 << 12),
    CPU_POPCNT   = (1 << 13),
    CPU_LZCNT    = (1 << 14),
    CPU_TSC      = (1 << 15),
    CPU_TSCINV   = (1 << 16),
    CPU_AVX      = (1 << 17),
    CPU_AVX2     = (1 << 18),
    CPU_AES      = (1 << 19),
    CPU_ERMS     = (1 << 20), // enhanced 'rep movsb/stosb' instructions
    CPU_CLMUL    = (1 << 21), // carryless multiply for CRC
    CPU_BMI1     = (1 << 22),
    CPU_BMI2     = (1 << 23),
    CPU_RTM      = (1 << 24), // Restricted Transactional Memory instructions
    CPU_ADX      = (1 << 25),
    CPU_AVX512F  = (1 << 26), // AVX 512bit foundation instructions
    CPU_AVX512DQ = (1 << 27),
    CPU_AVX512PF = (1 << 28),
    CPU_AVX512ER = (1 << 29),
    CPU_AVX512CD = (1 << 30),
    CPU_AVX512BW = (1 << 31)
  } cpuFeatureFlags;

#define CPU_AVX512VL UCONST64(0x100000000) // EVEX instructions with smaller vector length : enums are limited to 32bit

  enum {
    // AMD
    CPU_FAMILY_AMD_11H       = 0x11,
    // Intel
    CPU_FAMILY_INTEL_CORE    = 6,
    CPU_MODEL_NEHALEM        = 0x1e,
    CPU_MODEL_NEHALEM_EP     = 0x1a,
    CPU_MODEL_NEHALEM_EX     = 0x2e,
    CPU_MODEL_WESTMERE       = 0x25,
    CPU_MODEL_WESTMERE_EP    = 0x2c,
    CPU_MODEL_WESTMERE_EX    = 0x2f,
    CPU_MODEL_SANDYBRIDGE    = 0x2a,
    CPU_MODEL_SANDYBRIDGE_EP = 0x2d,
    CPU_MODEL_IVYBRIDGE_EP   = 0x3a,
    CPU_MODEL_HASWELL_E3     = 0x3c,
    CPU_MODEL_HASWELL_E7     = 0x3f,
    CPU_MODEL_BROADWELL      = 0x3d,
    CPU_MODEL_SKYLAKE        = CPU_MODEL_HASWELL_E3
  } cpuExtendedFamily;

  // cpuid information block.  All info derived from executing cpuid with
  // various function numbers is stored here.  Intel and AMD info is
  // merged in this block: accessor methods disentangle it.
  //
  // The info block is laid out in subblocks of 4 dwords corresponding to
  // eax, ebx, ecx and edx, whether or not they contain anything useful.
  struct CpuidInfo {
    // cpuid function 0
    uint32_t std_max_function;
    uint32_t std_vendor_name_0;
    uint32_t std_vendor_name_1;
    uint32_t std_vendor_name_2;

    // cpuid function 1
    StdCpuid1Eax std_cpuid1_eax;
    StdCpuid1Ebx std_cpuid1_ebx;
    StdCpuid1Ecx std_cpuid1_ecx;
    StdCpuid1Edx std_cpuid1_edx;

    // cpuid function 4 (deterministic cache parameters)
    DcpCpuid4Eax dcp_cpuid4_eax;
    DcpCpuid4Ebx dcp_cpuid4_ebx;
    uint32_t     dcp_cpuid4_ecx; // unused currently
    uint32_t     dcp_cpuid4_edx; // unused currently

    // cpuid function 7 (structured extended features)
    SefCpuid7Eax sef_cpuid7_eax;
    SefCpuid7Ebx sef_cpuid7_ebx;
    uint32_t     sef_cpuid7_ecx; // unused currently
    uint32_t     sef_cpuid7_edx; // unused currently

    // cpuid function 0xB (processor topology)
    // ecx = 0
    uint32_t     tpl_cpuidB0_eax;
    TplCpuidBEbx tpl_cpuidB0_ebx;
    uint32_t     tpl_cpuidB0_ecx; // unused currently
    uint32_t     tpl_cpuidB0_edx; // unused currently

    // ecx = 1
    uint32_t     tpl_cpuidB1_eax;
    TplCpuidBEbx tpl_cpuidB1_ebx;
    uint32_t     tpl_cpuidB1_ecx; // unused currently
    uint32_t     tpl_cpuidB1_edx; // unused currently

    // ecx = 2
    uint32_t     tpl_cpuidB2_eax;
    TplCpuidBEbx tpl_cpuidB2_ebx;
    uint32_t     tpl_cpuidB2_ecx; // unused currently
    uint32_t     tpl_cpuidB2_edx; // unused currently

    // cpuid function 0x80000000 // example, unused
    uint32_t ext_max_function;
    uint32_t ext_vendor_name_0;
    uint32_t ext_vendor_name_1;
    uint32_t ext_vendor_name_2;

    // cpuid function 0x80000001
    uint32_t     ext_cpuid1_eax; // reserved
    uint32_t     ext_cpuid1_ebx; // reserved
    ExtCpuid1Ecx ext_cpuid1_ecx;
    ExtCpuid1Edx ext_cpuid1_edx;

    // cpuid functions 0x80000002 thru 0x80000004: example, unused
    uint32_t proc_name_0, proc_name_1, proc_name_2, proc_name_3;
    uint32_t proc_name_4, proc_name_5, proc_name_6, proc_name_7;
    uint32_t proc_name_8, proc_name_9, proc_name_10,proc_name_11;

    // cpuid function 0x80000005 // AMD L1, Intel reserved
    uint32_t     ext_cpuid5_eax; // unused currently
    uint32_t     ext_cpuid5_ebx; // reserved
    ExtCpuid5Ex  ext_cpuid5_ecx; // L1 data cache info (AMD)
    ExtCpuid5Ex  ext_cpuid5_edx; // L1 instruction cache info (AMD)

    // cpuid function 0x80000007
    uint32_t     ext_cpuid7_eax; // reserved
    uint32_t     ext_cpuid7_ebx; // reserved
    uint32_t     ext_cpuid7_ecx; // reserved
    ExtCpuid7Edx ext_cpuid7_edx; // tscinv

    // cpuid function 0x80000008
    uint32_t     ext_cpuid8_eax; // unused currently
    uint32_t     ext_cpuid8_ebx; // reserved
    ExtCpuid8Ecx ext_cpuid8_ecx;
    uint32_t     ext_cpuid8_edx; // reserved

    // extended control register XCR0 (the XFEATURE_ENABLED_MASK register)
    XemXcr0Eax   xem_xcr0_eax;
    uint32_t     xem_xcr0_edx; // reserved

    // Space to save ymm registers after signal handle
    int          ymm_save[8*4]; // Save ymm0, ymm7, ymm8, ymm15

    // Space to save zmm registers after signal handle
    int          zmm_save[16*4]; // Save zmm0, zmm7, zmm8, zmm31
  };

  // The actual cpuid info block
  static CpuidInfo _cpuid_info;

  // Extractors and predicates
  static uint32_t extended_cpu_family() {
    uint32_t result = _cpuid_info.std_cpuid1_eax.bits.family;
    result += _cpuid_info.std_cpuid1_eax.bits.ext_family;
    return result;
  }

  static uint32_t extended_cpu_model() {
    uint32_t result = _cpuid_info.std_cpuid1_eax.bits.model;
    result |= _cpuid_info.std_cpuid1_eax.bits.ext_model << 4;
    return result;
  }

  static uint32_t cpu_stepping() {
    uint32_t result = _cpuid_info.std_cpuid1_eax.bits.stepping;
    return result;
  }

  static uint logical_processor_count() {
    uint result = threads_per_core();
    return result;
  }

  static uint64_t feature_flags() {
    uint64_t result = 0;
    if (_cpuid_info.std_cpuid1_edx.bits.cmpxchg8 != 0)
      result |= CPU_CX8;
    if (_cpuid_info.std_cpuid1_edx.bits.cmov != 0)
      result |= CPU_CMOV;
    if (_cpuid_info.std_cpuid1_edx.bits.fxsr != 0 || (is_amd() &&
        _cpuid_info.ext_cpuid1_edx.bits.fxsr != 0))
      result |= CPU_FXSR;
    // HT flag is set for multi-core processors also.
    if (threads_per_core() > 1)
      result |= CPU_HT;
    if (_cpuid_info.std_cpuid1_edx.bits.mmx != 0 || (is_amd() &&
        _cpuid_info.ext_cpuid1_edx.bits.mmx != 0))
      result |= CPU_MMX;
    if (_cpuid_info.std_cpuid1_edx.bits.sse != 0)
      result |= CPU_SSE;
    if (_cpuid_info.std_cpuid1_edx.bits.sse2 != 0)
      result |= CPU_SSE2;
    if (_cpuid_info.std_cpuid1_ecx.bits.sse3 != 0)
      result |= CPU_SSE3;
    if (_cpuid_info.std_cpuid1_ecx.bits.ssse3 != 0)
      result |= CPU_SSSE3;
    if (_cpuid_info.std_cpuid1_ecx.bits.sse4_1 != 0)
      result |= CPU_SSE4_1;
    if (_cpuid_info.std_cpuid1_ecx.bits.sse4_2 != 0)
      result |= CPU_SSE4_2;
    if (_cpuid_info.std_cpuid1_ecx.bits.popcnt != 0)
      result |= CPU_POPCNT;
    if (_cpuid_info.std_cpuid1_ecx.bits.avx != 0 &&
        _cpuid_info.std_cpuid1_ecx.bits.osxsave != 0 &&
        _cpuid_info.xem_xcr0_eax.bits.sse != 0 &&
        _cpuid_info.xem_xcr0_eax.bits.ymm != 0) {
      result |= CPU_AVX;
      if (_cpuid_info.sef_cpuid7_ebx.bits.avx2 != 0)
        result |= CPU_AVX2;
      if (_cpuid_info.sef_cpuid7_ebx.bits.avx512f != 0 &&
          _cpuid_info.xem_xcr0_eax.bits.opmask != 0 &&
          _cpuid_info.xem_xcr0_eax.bits.zmm512 != 0 &&
          _cpuid_info.xem_xcr0_eax.bits.zmm32 != 0) {
        result |= CPU_AVX512F;
        if (_cpuid_info.sef_cpuid7_ebx.bits.avx512cd != 0)
          result |= CPU_AVX512CD;
        if (_cpuid_info.sef_cpuid7_ebx.bits.avx512dq != 0)
          result |= CPU_AVX512DQ;
        if (_cpuid_info.sef_cpuid7_ebx.bits.avx512pf != 0)
          result |= CPU_AVX512PF;
        if (_cpuid_info.sef_cpuid7_ebx.bits.avx512er != 0)
          result |= CPU_AVX512ER;
        if (_cpuid_info.sef_cpuid7_ebx.bits.avx512bw != 0)
          result |= CPU_AVX512BW;
        if (_cpuid_info.sef_cpuid7_ebx.bits.avx512vl != 0)
          result |= CPU_AVX512VL;
      }
    }
    if(_cpuid_info.sef_cpuid7_ebx.bits.bmi1 != 0)
      result |= CPU_BMI1;
    if (_cpuid_info.std_cpuid1_edx.bits.tsc != 0)
      result |= CPU_TSC;
    if (_cpuid_info.ext_cpuid7_edx.bits.tsc_invariance != 0)
      result |= CPU_TSCINV;
    if (_cpuid_info.std_cpuid1_ecx.bits.aes != 0)
      result |= CPU_AES;
    if (_cpuid_info.sef_cpuid7_ebx.bits.erms != 0)
      result |= CPU_ERMS;
    if (_cpuid_info.std_cpuid1_ecx.bits.clmul != 0)
      result |= CPU_CLMUL;
    if (_cpuid_info.sef_cpuid7_ebx.bits.rtm != 0)
      result |= CPU_RTM;

    // AMD features.
    if (is_amd()) {
      if ((_cpuid_info.ext_cpuid1_edx.bits.tdnow != 0) ||
          (_cpuid_info.ext_cpuid1_ecx.bits.prefetchw != 0))
        result |= CPU_3DNOW_PREFETCH;
      if (_cpuid_info.ext_cpuid1_ecx.bits.lzcnt != 0)
        result |= CPU_LZCNT;
      if (_cpuid_info.ext_cpuid1_ecx.bits.sse4a != 0)
        result |= CPU_SSE4A;
    }
    // Intel features.
    if(is_intel()) {
      if(_cpuid_info.sef_cpuid7_ebx.bits.adx != 0)
         result |= CPU_ADX;
      if(_cpuid_info.sef_cpuid7_ebx.bits.bmi2 != 0)
        result |= CPU_BMI2;
      if(_cpuid_info.ext_cpuid1_ecx.bits.lzcnt_intel != 0)
        result |= CPU_LZCNT;
      // for Intel, ecx.bits.misalignsse bit (bit 8) indicates support for prefetchw
      if (_cpuid_info.ext_cpuid1_ecx.bits.misalignsse != 0) {
        result |= CPU_3DNOW_PREFETCH;
      }
    }

    return result;
  }

  static bool os_supports_avx_vectors() {
    bool retVal = false;
    if (supports_evex()) {
      // Verify that OS save/restore all bits of EVEX registers
      // during signal processing.
      int nreg = 2 LP64_ONLY(+2);
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
      int nreg = 2 LP64_ONLY(+2);
      retVal = true;
      for (int i = 0; i < 8 * nreg; i++) { // 32 bytes per ymm register
        if (_cpuid_info.ymm_save[i] != ymm_test_value()) {
          retVal = false;
          break;
        }
      }
    }
    return retVal;
  }

  static void get_processor_features();

public:
  // Offsets for cpuid asm stub
  static ByteSize std_cpuid0_offset() { return byte_offset_of(CpuidInfo, std_max_function); }
  static ByteSize std_cpuid1_offset() { return byte_offset_of(CpuidInfo, std_cpuid1_eax); }
  static ByteSize dcp_cpuid4_offset() { return byte_offset_of(CpuidInfo, dcp_cpuid4_eax); }
  static ByteSize sef_cpuid7_offset() { return byte_offset_of(CpuidInfo, sef_cpuid7_eax); }
  static ByteSize ext_cpuid1_offset() { return byte_offset_of(CpuidInfo, ext_cpuid1_eax); }
  static ByteSize ext_cpuid5_offset() { return byte_offset_of(CpuidInfo, ext_cpuid5_eax); }
  static ByteSize ext_cpuid7_offset() { return byte_offset_of(CpuidInfo, ext_cpuid7_eax); }
  static ByteSize ext_cpuid8_offset() { return byte_offset_of(CpuidInfo, ext_cpuid8_eax); }
  static ByteSize tpl_cpuidB0_offset() { return byte_offset_of(CpuidInfo, tpl_cpuidB0_eax); }
  static ByteSize tpl_cpuidB1_offset() { return byte_offset_of(CpuidInfo, tpl_cpuidB1_eax); }
  static ByteSize tpl_cpuidB2_offset() { return byte_offset_of(CpuidInfo, tpl_cpuidB2_eax); }
  static ByteSize xem_xcr0_offset() { return byte_offset_of(CpuidInfo, xem_xcr0_eax); }
  static ByteSize ymm_save_offset() { return byte_offset_of(CpuidInfo, ymm_save); }
  static ByteSize zmm_save_offset() { return byte_offset_of(CpuidInfo, zmm_save); }

  // The value used to check ymm register after signal handle
  static int ymm_test_value()    { return 0xCAFEBABE; }

  static void get_cpu_info_wrapper();
  static void set_cpuinfo_segv_addr(address pc) { _cpuinfo_segv_addr = pc; }
  static bool  is_cpuinfo_segv_addr(address pc) { return _cpuinfo_segv_addr == pc; }
  static void set_cpuinfo_cont_addr(address pc) { _cpuinfo_cont_addr = pc; }
  static address  cpuinfo_cont_addr()           { return _cpuinfo_cont_addr; }

  static void clean_cpuFeatures()   { _cpuFeatures = 0; }
  static void set_avx_cpuFeatures() { _cpuFeatures = (CPU_SSE | CPU_SSE2 | CPU_AVX); }
  static void set_evex_cpuFeatures() { _cpuFeatures = (CPU_AVX512F | CPU_SSE | CPU_SSE2 ); }


  // Initialization
  static void initialize();

  // Override Abstract_VM_Version implementation
  static bool use_biased_locking();

  // Asserts
  static void assert_is_initialized() {
    assert(_cpuid_info.std_cpuid1_eax.bits.family != 0, "VM_Version not initialized");
  }

  //
  // Processor family:
  //       3   -  386
  //       4   -  486
  //       5   -  Pentium
  //       6   -  PentiumPro, Pentium II, Celeron, Xeon, Pentium III, Athlon,
  //              Pentium M, Core Solo, Core Duo, Core2 Duo
  //    family 6 model:   9,        13,       14,        15
  //    0x0f   -  Pentium 4, Opteron
  //
  // Note: The cpu family should be used to select between
  //       instruction sequences which are valid on all Intel
  //       processors.  Use the feature test functions below to
  //       determine whether a particular instruction is supported.
  //
  static int  cpu_family()        { return _cpu;}
  static bool is_P6()             { return cpu_family() >= 6; }
  static bool is_amd()            { assert_is_initialized(); return _cpuid_info.std_vendor_name_0 == 0x68747541; } // 'htuA'
  static bool is_intel()          { assert_is_initialized(); return _cpuid_info.std_vendor_name_0 == 0x756e6547; } // 'uneG'

  static bool supports_processor_topology() {
    return (_cpuid_info.std_max_function >= 0xB) &&
           // eax[4:0] | ebx[0:15] == 0 indicates invalid topology level.
           // Some cpus have max cpuid >= 0xB but do not support processor topology.
           (((_cpuid_info.tpl_cpuidB0_eax & 0x1f) | _cpuid_info.tpl_cpuidB0_ebx.bits.logical_cpus) != 0);
  }

  static uint cores_per_cpu()  {
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
    } else if (is_amd()) {
      result = (_cpuid_info.ext_cpuid8_ecx.bits.cores_per_cpu + 1);
    }
    return result;
  }

  static uint threads_per_core()  {
    uint result = 1;
    if (is_intel() && supports_processor_topology()) {
      result = _cpuid_info.tpl_cpuidB0_ebx.bits.logical_cpus;
    } else if (_cpuid_info.std_cpuid1_edx.bits.ht != 0) {
      result = _cpuid_info.std_cpuid1_ebx.bits.threads_per_cpu /
               cores_per_cpu();
    }
    return (result == 0 ? 1 : result);
  }

  static intx L1_line_size()  {
    intx result = 0;
    if (is_intel()) {
      result = (_cpuid_info.dcp_cpuid4_ebx.bits.L1_line_size + 1);
    } else if (is_amd()) {
      result = _cpuid_info.ext_cpuid5_ecx.bits.L1_line_size;
    }
    if (result < 32) // not defined ?
      result = 32;   // 32 bytes by default on x86 and other x64
    return result;
  }

  static intx prefetch_data_size()  {
    return L1_line_size();
  }

  //
  // Feature identification
  //
  static bool supports_cpuid()    { return _cpuFeatures  != 0; }
  static bool supports_cmpxchg8() { return (_cpuFeatures & CPU_CX8) != 0; }
  static bool supports_cmov()     { return (_cpuFeatures & CPU_CMOV) != 0; }
  static bool supports_fxsr()     { return (_cpuFeatures & CPU_FXSR) != 0; }
  static bool supports_ht()       { return (_cpuFeatures & CPU_HT) != 0; }
  static bool supports_mmx()      { return (_cpuFeatures & CPU_MMX) != 0; }
  static bool supports_sse()      { return (_cpuFeatures & CPU_SSE) != 0; }
  static bool supports_sse2()     { return (_cpuFeatures & CPU_SSE2) != 0; }
  static bool supports_sse3()     { return (_cpuFeatures & CPU_SSE3) != 0; }
  static bool supports_ssse3()    { return (_cpuFeatures & CPU_SSSE3)!= 0; }
  static bool supports_sse4_1()   { return (_cpuFeatures & CPU_SSE4_1) != 0; }
  static bool supports_sse4_2()   { return (_cpuFeatures & CPU_SSE4_2) != 0; }
  static bool supports_popcnt()   { return (_cpuFeatures & CPU_POPCNT) != 0; }
  static bool supports_avx()      { return (_cpuFeatures & CPU_AVX) != 0; }
  static bool supports_avx2()     { return (_cpuFeatures & CPU_AVX2) != 0; }
  static bool supports_tsc()      { return (_cpuFeatures & CPU_TSC)    != 0; }
  static bool supports_aes()      { return (_cpuFeatures & CPU_AES) != 0; }
  static bool supports_erms()     { return (_cpuFeatures & CPU_ERMS) != 0; }
  static bool supports_clmul()    { return (_cpuFeatures & CPU_CLMUL) != 0; }
  static bool supports_rtm()      { return (_cpuFeatures & CPU_RTM) != 0; }
  static bool supports_bmi1()     { return (_cpuFeatures & CPU_BMI1) != 0; }
  static bool supports_bmi2()     { return (_cpuFeatures & CPU_BMI2) != 0; }
  static bool supports_adx()      { return (_cpuFeatures & CPU_ADX) != 0; }
  static bool supports_evex()     { return (_cpuFeatures & CPU_AVX512F) != 0; }
  static bool supports_avx512dq() { return (_cpuFeatures & CPU_AVX512DQ) != 0; }
  static bool supports_avx512pf() { return (_cpuFeatures & CPU_AVX512PF) != 0; }
  static bool supports_avx512er() { return (_cpuFeatures & CPU_AVX512ER) != 0; }
  static bool supports_avx512cd() { return (_cpuFeatures & CPU_AVX512CD) != 0; }
  static bool supports_avx512bw() { return (_cpuFeatures & CPU_AVX512BW) != 0; }
  static bool supports_avx512vl() { return (_cpuFeatures & CPU_AVX512VL) != 0; }
  static bool supports_avx512vlbw() { return (supports_avx512bw() && supports_avx512vl()); }
  static bool supports_avx512novl() { return (supports_evex() && !supports_avx512vl()); }
  // Intel features
  static bool is_intel_family_core() { return is_intel() &&
                                       extended_cpu_family() == CPU_FAMILY_INTEL_CORE; }

  static bool is_intel_tsc_synched_at_init()  {
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

  // AMD features
  static bool supports_3dnow_prefetch()    { return (_cpuFeatures & CPU_3DNOW_PREFETCH) != 0; }
  static bool supports_mmx_ext()  { return is_amd() && _cpuid_info.ext_cpuid1_edx.bits.mmx_amd != 0; }
  static bool supports_lzcnt()    { return (_cpuFeatures & CPU_LZCNT) != 0; }
  static bool supports_sse4a()    { return (_cpuFeatures & CPU_SSE4A) != 0; }

  static bool is_amd_Barcelona()  { return is_amd() &&
                                           extended_cpu_family() == CPU_FAMILY_AMD_11H; }

  // Intel and AMD newer cores support fast timestamps well
  static bool supports_tscinv_bit() {
    return (_cpuFeatures & CPU_TSCINV) != 0;
  }
  static bool supports_tscinv() {
    return supports_tscinv_bit() &&
           ( (is_amd() && !is_amd_Barcelona()) ||
             is_intel_tsc_synched_at_init() );
  }

  // Intel Core and newer cpus have fast IDIV instruction (excluding Atom).
  static bool has_fast_idiv()     { return is_intel() && cpu_family() == 6 &&
                                           supports_sse3() && _model != 0x1C; }

  static bool supports_compare_and_exchange() { return true; }

  static const char* cpu_features()           { return _features_str; }

  static intx allocate_prefetch_distance() {
    // This method should be called before allocate_prefetch_style().
    //
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

    intx count = AllocatePrefetchDistance;
    if (count < 0) {   // default ?
      if (is_amd()) {  // AMD
        if (supports_sse2())
          count = 256; // Opteron
        else
          count = 128; // Athlon
      } else {         // Intel
        if (supports_sse2())
          if (cpu_family() == 6) {
            count = 256; // Pentium M, Core, Core2
          } else {
            count = 512; // Pentium 4
          }
        else
          count = 128; // Pentium 3 (and all other old CPUs)
      }
    }
    return count;
  }
  static intx allocate_prefetch_style() {
    assert(AllocatePrefetchStyle >= 0, "AllocatePrefetchStyle should be positive");
    // Return 0 if AllocatePrefetchDistance was not defined.
    return AllocatePrefetchDistance > 0 ? AllocatePrefetchStyle : 0;
  }

  // Prefetch interval for gc copy/scan == 9 dcache lines.  Derived from
  // 50-warehouse specjbb runs on a 2-way 1.8ghz opteron using a 4gb heap.
  // Tested intervals from 128 to 2048 in increments of 64 == one cache line.
  // 256 bytes (4 dcache lines) was the nearest runner-up to 576.

  // gc copy/scan is disabled if prefetchw isn't supported, because
  // Prefetch::write emits an inlined prefetchw on Linux.
  // Do not use the 3dnow prefetchw instruction.  It isn't supported on em64t.
  // The used prefetcht0 instruction works for both amd64 and em64t.
  static intx prefetch_copy_interval_in_bytes() {
    intx interval = PrefetchCopyIntervalInBytes;
    return interval >= 0 ? interval : 576;
  }
  static intx prefetch_scan_interval_in_bytes() {
    intx interval = PrefetchScanIntervalInBytes;
    return interval >= 0 ? interval : 576;
  }
  static intx prefetch_fields_ahead() {
    intx count = PrefetchFieldsAhead;
    return count >= 0 ? count : 1;
  }
  static uint32_t get_xsave_header_lower_segment() {
    return _cpuid_info.xem_xcr0_eax.value;
  }
  static uint32_t get_xsave_header_upper_segment() {
    return _cpuid_info.xem_xcr0_edx;
  }
};

#endif // CPU_X86_VM_VM_VERSION_X86_HPP
