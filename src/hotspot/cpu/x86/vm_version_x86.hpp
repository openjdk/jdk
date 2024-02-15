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

#ifndef CPU_X86_VM_VERSION_X86_HPP
#define CPU_X86_VM_VERSION_X86_HPP

#include "runtime/abstract_vm_version.hpp"
#include "utilities/macros.hpp"
#include "utilities/sizes.hpp"

class VM_Version : public Abstract_VM_Version {
  friend class VMStructs;
  friend class JVMCIVMStructs;

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
                        : 1,
               fma      : 1,
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
               f16c     : 1,
                        : 1,
               hv       : 1;
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
               lzcnt        : 1,
               sse4a        : 1,
               misalignsse  : 1,
               prefetchw    : 1,
                            : 23;
    } bits;
  };

  union ExtCpuid1Edx {
    uint32_t value;
    struct {
      uint32_t           : 22,
               mmx_amd   : 1,
               mmx       : 1,
               fxsr      : 1,
               fxsr_opt  : 1,
               pdpe1gb   : 1,
               rdtscp    : 1,
                         : 1,
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
                        : 1,
             avx512ifma : 1,
                        : 1,
             clflushopt : 1,
                   clwb : 1,
                        : 1,
               avx512pf : 1,
               avx512er : 1,
               avx512cd : 1,
                    sha : 1,
               avx512bw : 1,
               avx512vl : 1;
    } bits;
  };

  union SefCpuid7Ecx {
    uint32_t value;
    struct {
      uint32_t prefetchwt1 : 1,
               avx512_vbmi : 1,
                      umip : 1,
                       pku : 1,
                     ospke : 1,
                           : 1,
              avx512_vbmi2 : 1,
                    cet_ss : 1,
                      gfni : 1,
                      vaes : 1,
         avx512_vpclmulqdq : 1,
               avx512_vnni : 1,
             avx512_bitalg : 1,
                           : 1,
          avx512_vpopcntdq : 1,
                           : 1,
                           : 1,
                     mawau : 5,
                     rdpid : 1,
                           : 9;
    } bits;
  };

  union SefCpuid7Edx {
    uint32_t value;
    struct {
      uint32_t             : 2,
             avx512_4vnniw : 1,
             avx512_4fmaps : 1,
        fast_short_rep_mov : 1,
                           : 9,
                 serialize : 1,
                           : 5,
                   cet_ibt : 1,
                           : 11;
    } bits;
  };

  union ExtCpuid1EEbx {
    uint32_t value;
    struct {
      uint32_t                  : 8,
               threads_per_core : 8,
                                : 16;
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

  static bool _has_intel_jcc_erratum;

  static address   _cpuinfo_segv_addr; // address of instruction which causes SEGV
  static address   _cpuinfo_cont_addr; // address of instruction after the one which causes SEGV

  /*
   * Update following files when declaring new flags:
   * test/lib-test/jdk/test/whitebox/CPUInfoTest.java
   * src/jdk.internal.vm.ci/share/classes/jdk.vm.ci.amd64/src/jdk/vm/ci/amd64/AMD64.java
   */
  enum Feature_Flag : uint64_t {
#define CPU_FEATURE_FLAGS(decl) \
    decl(CX8,               "cx8",               0)  /*  next bits are from cpuid 1 (EDX) */ \
    decl(CMOV,              "cmov",              1)  \
    decl(FXSR,              "fxsr",              2)  \
    decl(HT,                "ht",                3)  \
                                                     \
    decl(MMX,               "mmx",               4)  \
    decl(3DNOW_PREFETCH,    "3dnowpref",         5)  /* Processor supports 3dnow prefetch and prefetchw instructions */ \
                                                     /* may not necessarily support other 3dnow instructions */ \
    decl(SSE,               "sse",               6)  \
    decl(SSE2,              "sse2",              7)  \
                                                     \
    decl(SSE3,              "sse3",              8 ) /* SSE3 comes from cpuid 1 (ECX) */ \
    decl(SSSE3,             "ssse3",             9 ) \
    decl(SSE4A,             "sse4a",             10) \
    decl(SSE4_1,            "sse4.1",            11) \
                                                     \
    decl(SSE4_2,            "sse4.2",            12) \
    decl(POPCNT,            "popcnt",            13) \
    decl(LZCNT,             "lzcnt",             14) \
    decl(TSC,               "tsc",               15) \
                                                     \
    decl(TSCINV_BIT,        "tscinvbit",         16) \
    decl(TSCINV,            "tscinv",            17) \
    decl(AVX,               "avx",               18) \
    decl(AVX2,              "avx2",              19) \
                                                     \
    decl(AES,               "aes",               20) \
    decl(ERMS,              "erms",              21) /* enhanced 'rep movsb/stosb' instructions */ \
    decl(CLMUL,             "clmul",             22) /* carryless multiply for CRC */ \
    decl(BMI1,              "bmi1",              23) \
                                                     \
    decl(BMI2,              "bmi2",              24) \
    decl(RTM,               "rtm",               25) /* Restricted Transactional Memory instructions */ \
    decl(ADX,               "adx",               26) \
    decl(AVX512F,           "avx512f",           27) /* AVX 512bit foundation instructions */ \
                                                     \
    decl(AVX512DQ,          "avx512dq",          28) \
    decl(AVX512PF,          "avx512pf",          29) \
    decl(AVX512ER,          "avx512er",          30) \
    decl(AVX512CD,          "avx512cd",          31) \
                                                     \
    decl(AVX512BW,          "avx512bw",          32) /* Byte and word vector instructions */ \
    decl(AVX512VL,          "avx512vl",          33) /* EVEX instructions with smaller vector length */ \
    decl(SHA,               "sha",               34) /* SHA instructions */ \
    decl(FMA,               "fma",               35) /* FMA instructions */ \
                                                     \
    decl(VZEROUPPER,        "vzeroupper",        36) /* Vzeroupper instruction */ \
    decl(AVX512_VPOPCNTDQ,  "avx512_vpopcntdq",  37) /* Vector popcount */ \
    decl(AVX512_VPCLMULQDQ, "avx512_vpclmulqdq", 38) /* Vector carryless multiplication */ \
    decl(AVX512_VAES,       "avx512_vaes",       39) /* Vector AES instruction */ \
                                                     \
    decl(AVX512_VNNI,       "avx512_vnni",       40) /* Vector Neural Network Instructions */ \
    decl(FLUSH,             "clflush",           41) /* flush instruction */ \
    decl(FLUSHOPT,          "clflushopt",        42) /* flusopth instruction */ \
    decl(CLWB,              "clwb",              43) /* clwb instruction */ \
                                                     \
    decl(AVX512_VBMI2,      "avx512_vbmi2",      44) /* VBMI2 shift left double instructions */ \
    decl(AVX512_VBMI,       "avx512_vbmi",       45) /* Vector BMI instructions */ \
    decl(HV,                "hv",                46) /* Hypervisor instructions */ \
    decl(SERIALIZE,         "serialize",         47) /* CPU SERIALIZE */ \
    decl(RDTSCP,            "rdtscp",            48) /* RDTSCP instruction */ \
    decl(RDPID,             "rdpid",             49) /* RDPID instruction */ \
    decl(FSRM,              "fsrm",              50) /* Fast Short REP MOV */ \
    decl(GFNI,              "gfni",              51) /* Vector GFNI instructions */ \
    decl(AVX512_BITALG,     "avx512_bitalg",     52) /* Vector sub-word popcount and bit gather instructions */\
    decl(F16C,              "f16c",              53) /* Half-precision and single precision FP conversion instructions*/ \
    decl(PKU,               "pku",               54) /* Protection keys for user-mode pages */ \
    decl(OSPKE,             "ospke",             55) /* OS enables protection keys */ \
    decl(CET_IBT,           "cet_ibt",           56) /* Control Flow Enforcement - Indirect Branch Tracking */ \
    decl(CET_SS,            "cet_ss",            57) /* Control Flow Enforcement - Shadow Stack */ \
    decl(AVX512_IFMA,       "avx512_ifma",       58) /* Integer Vector FMA instructions*/

#define DECLARE_CPU_FEATURE_FLAG(id, name, bit) CPU_##id = (1ULL << bit),
    CPU_FEATURE_FLAGS(DECLARE_CPU_FEATURE_FLAG)
#undef DECLARE_CPU_FEATURE_FLAG
  };

  static const char* _features_names[];

  enum Extended_Family {
    // AMD
    CPU_FAMILY_AMD_11H       = 0x11,
    // ZX
    CPU_FAMILY_ZX_CORE_F6    = 6,
    CPU_FAMILY_ZX_CORE_F7    = 7,
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
    CPU_MODEL_SKYLAKE        = 0x55
  };

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
    SefCpuid7Ecx sef_cpuid7_ecx;
    SefCpuid7Edx sef_cpuid7_edx;

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

    // cpuid function 0x8000001E // AMD 17h
    uint32_t      ext_cpuid1E_eax;
    ExtCpuid1EEbx ext_cpuid1E_ebx; // threads per core (AMD17h)
    uint32_t      ext_cpuid1E_ecx;
    uint32_t      ext_cpuid1E_edx; // unused currently

    // extended control register XCR0 (the XFEATURE_ENABLED_MASK register)
    XemXcr0Eax   xem_xcr0_eax;
    uint32_t     xem_xcr0_edx; // reserved

    // Space to save ymm registers after signal handle
    int          ymm_save[8*4]; // Save ymm0, ymm7, ymm8, ymm15

    // Space to save zmm registers after signal handle
    int          zmm_save[16*4]; // Save zmm0, zmm7, zmm8, zmm31
  };

private:
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

  static bool compute_has_intel_jcc_erratum();

  static uint64_t feature_flags();
  static bool os_supports_avx_vectors();
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
  static ByteSize ext_cpuid1E_offset() { return byte_offset_of(CpuidInfo, ext_cpuid1E_eax); }
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

  static void clean_cpuFeatures()   { _features = 0; }
  static void set_avx_cpuFeatures() { _features = (CPU_SSE | CPU_SSE2 | CPU_AVX | CPU_VZEROUPPER ); }
  static void set_evex_cpuFeatures() { _features = (CPU_AVX512F | CPU_SSE | CPU_SSE2 | CPU_VZEROUPPER ); }

  // Initialization
  static void initialize();

  // Override Abstract_VM_Version implementation
  static void print_platform_virtualization_info(outputStream*);

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
  static bool is_hygon()          { assert_is_initialized(); return _cpuid_info.std_vendor_name_0 == 0x6F677948; } // 'ogyH'
  static bool is_amd_family()     { return is_amd() || is_hygon(); }
  static bool is_intel()          { assert_is_initialized(); return _cpuid_info.std_vendor_name_0 == 0x756e6547; } // 'uneG'
  static bool is_zx()             { assert_is_initialized(); return (_cpuid_info.std_vendor_name_0 == 0x746e6543) || (_cpuid_info.std_vendor_name_0 == 0x68532020); } // 'tneC'||'hS  '
  static bool is_atom_family()    { return ((cpu_family() == 0x06) && ((extended_cpu_model() == 0x36) || (extended_cpu_model() == 0x37) || (extended_cpu_model() == 0x4D))); } //Silvermont and Centerton
  static bool is_knights_family() { return UseKNLSetting || ((cpu_family() == 0x06) && ((extended_cpu_model() == 0x57) || (extended_cpu_model() == 0x85))); } // Xeon Phi 3200/5200/7200 and Future Xeon Phi

  static bool supports_processor_topology() {
    return (_cpuid_info.std_max_function >= 0xB) &&
           // eax[4:0] | ebx[0:15] == 0 indicates invalid topology level.
           // Some cpus have max cpuid >= 0xB but do not support processor topology.
           (((_cpuid_info.tpl_cpuidB0_eax & 0x1f) | _cpuid_info.tpl_cpuidB0_ebx.bits.logical_cpus) != 0);
  }

  static uint cores_per_cpu();
  static uint threads_per_core();
  static uint L1_line_size();

  static uint prefetch_data_size()  {
    return L1_line_size();
  }

  //
  // Feature identification which can be affected by VM settings
  //
  static bool supports_cpuid()        { return _features  != 0; }
  static bool supports_cmov()         { return (_features & CPU_CMOV) != 0; }
  static bool supports_fxsr()         { return (_features & CPU_FXSR) != 0; }
  static bool supports_ht()           { return (_features & CPU_HT) != 0; }
  static bool supports_mmx()          { return (_features & CPU_MMX) != 0; }
  static bool supports_sse()          { return (_features & CPU_SSE) != 0; }
  static bool supports_sse2()         { return (_features & CPU_SSE2) != 0; }
  static bool supports_sse3()         { return (_features & CPU_SSE3) != 0; }
  static bool supports_ssse3()        { return (_features & CPU_SSSE3)!= 0; }
  static bool supports_sse4_1()       { return (_features & CPU_SSE4_1) != 0; }
  static bool supports_sse4_2()       { return (_features & CPU_SSE4_2) != 0; }
  static bool supports_popcnt()       { return (_features & CPU_POPCNT) != 0; }
  static bool supports_avx()          { return (_features & CPU_AVX) != 0; }
  static bool supports_avx2()         { return (_features & CPU_AVX2) != 0; }
  static bool supports_tsc()          { return (_features & CPU_TSC) != 0; }
  static bool supports_rdtscp()       { return (_features & CPU_RDTSCP) != 0; }
  static bool supports_rdpid()        { return (_features & CPU_RDPID) != 0; }
  static bool supports_aes()          { return (_features & CPU_AES) != 0; }
  static bool supports_erms()         { return (_features & CPU_ERMS) != 0; }
  static bool supports_fsrm()         { return (_features & CPU_FSRM) != 0; }
  static bool supports_clmul()        { return (_features & CPU_CLMUL) != 0; }
  static bool supports_rtm()          { return (_features & CPU_RTM) != 0; }
  static bool supports_bmi1()         { return (_features & CPU_BMI1) != 0; }
  static bool supports_bmi2()         { return (_features & CPU_BMI2) != 0; }
  static bool supports_adx()          { return (_features & CPU_ADX) != 0; }
  static bool supports_evex()         { return (_features & CPU_AVX512F) != 0; }
  static bool supports_avx512dq()     { return (_features & CPU_AVX512DQ) != 0; }
  static bool supports_avx512ifma()   { return (_features & CPU_AVX512_IFMA) != 0; }
  static bool supports_avx512pf()     { return (_features & CPU_AVX512PF) != 0; }
  static bool supports_avx512er()     { return (_features & CPU_AVX512ER) != 0; }
  static bool supports_avx512cd()     { return (_features & CPU_AVX512CD) != 0; }
  static bool supports_avx512bw()     { return (_features & CPU_AVX512BW) != 0; }
  static bool supports_avx512vl()     { return (_features & CPU_AVX512VL) != 0; }
  static bool supports_avx512vlbw()   { return (supports_evex() && supports_avx512bw() && supports_avx512vl()); }
  static bool supports_avx512bwdq()   { return (supports_evex() && supports_avx512bw() && supports_avx512dq()); }
  static bool supports_avx512vldq()   { return (supports_evex() && supports_avx512dq() && supports_avx512vl()); }
  static bool supports_avx512vlbwdq() { return (supports_evex() && supports_avx512vl() &&
                                                supports_avx512bw() && supports_avx512dq()); }
  static bool supports_avx512novl()   { return (supports_evex() && !supports_avx512vl()); }
  static bool supports_avx512nobw()   { return (supports_evex() && !supports_avx512bw()); }
  static bool supports_avx256only()   { return (supports_avx2() && !supports_evex()); }
  static bool supports_avxonly()      { return ((supports_avx2() || supports_avx()) && !supports_evex()); }
  static bool supports_sha()          { return (_features & CPU_SHA) != 0; }
  static bool supports_fma()          { return (_features & CPU_FMA) != 0 && supports_avx(); }
  static bool supports_vzeroupper()   { return (_features & CPU_VZEROUPPER) != 0; }
  static bool supports_avx512_vpopcntdq()  { return (_features & CPU_AVX512_VPOPCNTDQ) != 0; }
  static bool supports_avx512_vpclmulqdq() { return (_features & CPU_AVX512_VPCLMULQDQ) != 0; }
  static bool supports_avx512_vaes()  { return (_features & CPU_AVX512_VAES) != 0; }
  static bool supports_gfni()         { return (_features & CPU_GFNI) != 0; }
  static bool supports_avx512_vnni()  { return (_features & CPU_AVX512_VNNI) != 0; }
  static bool supports_avx512_bitalg()  { return (_features & CPU_AVX512_BITALG) != 0; }
  static bool supports_avx512_vbmi()  { return (_features & CPU_AVX512_VBMI) != 0; }
  static bool supports_avx512_vbmi2() { return (_features & CPU_AVX512_VBMI2) != 0; }
  static bool supports_hv()           { return (_features & CPU_HV) != 0; }
  static bool supports_serialize()    { return (_features & CPU_SERIALIZE) != 0; }
  static bool supports_f16c()         { return (_features & CPU_F16C) != 0; }
  static bool supports_pku()          { return (_features & CPU_PKU) != 0; }
  static bool supports_ospke()        { return (_features & CPU_OSPKE) != 0; }
  static bool supports_cet_ss()       { return (_features & CPU_CET_SS) != 0; }
  static bool supports_cet_ibt()      { return (_features & CPU_CET_IBT) != 0; }

  //
  // Feature identification not affected by VM flags
  //
  static bool cpu_supports_evex()     { return (_cpu_features & CPU_AVX512F) != 0; }

  // Intel features
  static bool is_intel_family_core() { return is_intel() &&
                                       extended_cpu_family() == CPU_FAMILY_INTEL_CORE; }

  static bool is_intel_skylake() { return is_intel_family_core() &&
                                          extended_cpu_model() == CPU_MODEL_SKYLAKE; }

#ifdef COMPILER2
  // Determine if it's running on Cascade Lake using default options.
  static bool is_default_intel_cascade_lake();
#endif

  static bool is_intel_cascade_lake();

  static int avx3_threshold();

  static bool is_intel_tsc_synched_at_init();

  // This checks if the JVM is potentially affected by an erratum on Intel CPUs (SKX102)
  // that causes unpredictable behaviour when jcc crosses 64 byte boundaries. Its microcode
  // mitigation causes regressions when jumps or fused conditional branches cross or end at
  // 32 byte boundaries.
  static bool has_intel_jcc_erratum() { return _has_intel_jcc_erratum; }

  // AMD features
  static bool supports_3dnow_prefetch()    { return (_features & CPU_3DNOW_PREFETCH) != 0; }
  static bool supports_lzcnt()    { return (_features & CPU_LZCNT) != 0; }
  static bool supports_sse4a()    { return (_features & CPU_SSE4A) != 0; }

  static bool is_amd_Barcelona()  { return is_amd() &&
                                           extended_cpu_family() == CPU_FAMILY_AMD_11H; }

  // Intel and AMD newer cores support fast timestamps well
  static bool supports_tscinv_bit() {
    return (_features & CPU_TSCINV_BIT) != 0;
  }
  static bool supports_tscinv() {
    return (_features & CPU_TSCINV) != 0;
  }

  // Intel Core and newer cpus have fast IDIV instruction (excluding Atom).
  static bool has_fast_idiv()     { return is_intel() && cpu_family() == 6 &&
                                           supports_sse3() && _model != 0x1C; }

  static bool supports_compare_and_exchange() { return true; }

  static int allocate_prefetch_distance(bool use_watermark_prefetch);

  // SSE2 and later processors implement a 'pause' instruction
  // that can be used for efficient implementation of
  // the intrinsic for java.lang.Thread.onSpinWait()
  static bool supports_on_spin_wait() { return supports_sse2(); }

  // x86_64 supports fast class initialization checks
  static bool supports_fast_class_init_checks() {
    return LP64_ONLY(true) NOT_LP64(false); // not implemented on x86_32
  }

  constexpr static bool supports_stack_watermark_barrier() {
    return true;
  }

  constexpr static bool supports_recursive_lightweight_locking() {
    return true;
  }

  // For AVX CPUs only. f16c support is disabled if UseAVX == 0.
  static bool supports_float16() {
    return supports_f16c() || supports_avx512vl();
  }

  // Check intrinsic support
  static bool is_intrinsic_supported(vmIntrinsicID id);

  // there are several insns to force cache line sync to memory which
  // we can use to ensure mapped non-volatile memory is up to date with
  // pending in-cache changes.
  //
  // 64 bit cpus always support clflush which writes back and evicts
  // on 32 bit cpus support is recorded via a feature flag
  //
  // clflushopt is optional and acts like clflush except it does
  // not synchronize with other memory ops. it needs a preceding
  // and trailing StoreStore fence
  //
  // clwb is an optional intel-specific instruction which
  // writes back without evicting the line. it also does not
  // synchronize with other memory ops. so, it needs preceding
  // and trailing StoreStore fences.

#ifdef _LP64
  static bool supports_clflush(); // Can't inline due to header file conflict
#else
  static bool supports_clflush() { return  ((_features & CPU_FLUSH) != 0); }
#endif // _LP64

  // Note: CPU_FLUSHOPT and CPU_CLWB bits should always be zero for 32-bit
  static bool supports_clflushopt() { return ((_features & CPU_FLUSHOPT) != 0); }
  static bool supports_clwb() { return ((_features & CPU_CLWB) != 0); }

  // Old CPUs perform lea on AGU which causes additional latency transferring the
  // value from/to ALU for other operations
  static bool supports_fast_2op_lea() {
    return (is_intel() && supports_avx()) || // Sandy Bridge and above
           (is_amd()   && supports_avx());   // Jaguar and Bulldozer and above
  }

  // Pre Icelake Intels suffer inefficiency regarding 3-operand lea, which contains
  // all of base register, index register and displacement immediate, with 3 latency.
  // Note that when the address contains no displacement but the base register is
  // rbp or r13, the machine code must contain a zero displacement immediate,
  // effectively transform a 2-operand lea into a 3-operand lea. This can be
  // replaced by add-add or lea-add
  static bool supports_fast_3op_lea() {
    return supports_fast_2op_lea() &&
           ((is_intel() && supports_clwb() && !is_intel_skylake()) || // Icelake and above
            is_amd());
  }

#ifdef __APPLE__
  // Is the CPU running emulated (for example macOS Rosetta running x86_64 code on M1 ARM (aarch64)
  static bool is_cpu_emulated();
#endif

  // support functions for virtualization detection
 private:
  static void check_virtualizations();

  static const char* cpu_family_description(void);
  static const char* cpu_model_description(void);
  static const char* cpu_brand(void);
  static const char* cpu_brand_string(void);

  static int cpu_type_description(char* const buf, size_t buf_len);
  static int cpu_detailed_description(char* const buf, size_t buf_len);
  static int cpu_extended_brand_string(char* const buf, size_t buf_len);

  static bool cpu_is_em64t(void);
  static bool is_netburst(void);

  // Returns bytes written excluding termninating null byte.
  static size_t cpu_write_support_string(char* const buf, size_t buf_len);
  static void resolve_cpu_information_details(void);
  static int64_t max_qualified_cpu_freq_from_brand_string(void);

 public:
  // Offsets for cpuid asm stub brand string
  static ByteSize proc_name_0_offset() { return byte_offset_of(CpuidInfo, proc_name_0); }
  static ByteSize proc_name_1_offset() { return byte_offset_of(CpuidInfo, proc_name_1); }
  static ByteSize proc_name_2_offset() { return byte_offset_of(CpuidInfo, proc_name_2); }
  static ByteSize proc_name_3_offset() { return byte_offset_of(CpuidInfo, proc_name_3); }
  static ByteSize proc_name_4_offset() { return byte_offset_of(CpuidInfo, proc_name_4); }
  static ByteSize proc_name_5_offset() { return byte_offset_of(CpuidInfo, proc_name_5); }
  static ByteSize proc_name_6_offset() { return byte_offset_of(CpuidInfo, proc_name_6); }
  static ByteSize proc_name_7_offset() { return byte_offset_of(CpuidInfo, proc_name_7); }
  static ByteSize proc_name_8_offset() { return byte_offset_of(CpuidInfo, proc_name_8); }
  static ByteSize proc_name_9_offset() { return byte_offset_of(CpuidInfo, proc_name_9); }
  static ByteSize proc_name_10_offset() { return byte_offset_of(CpuidInfo, proc_name_10); }
  static ByteSize proc_name_11_offset() { return byte_offset_of(CpuidInfo, proc_name_11); }

  static int64_t maximum_qualified_cpu_frequency(void);

  static bool supports_tscinv_ext(void);

  static void initialize_tsc();
  static void initialize_cpu_information(void);
};

#endif // CPU_X86_VM_VERSION_X86_HPP
