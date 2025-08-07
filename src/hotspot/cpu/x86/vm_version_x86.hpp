/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/debug.hpp"
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
                           : 2,
              avx512_fp16  : 1,
                           : 8;
    } bits;
  };

  union SefCpuid7SubLeaf1Eax {
    uint32_t value;
    struct {
      uint32_t    sha512   : 1,
                           : 22,
                  avx_ifma : 1,
                           : 8;
    } bits;
  };

  union SefCpuid7SubLeaf1Edx {
    uint32_t value;
    struct {
      uint32_t       : 19,
              avx10  : 1,
                     : 1,
              apx_f  : 1,
                     : 10;
    } bits;
  };

  union StdCpuid24MainLeafEax {
    uint32_t value;
    struct {
      uint32_t  sub_leaves_cnt  : 31;
    } bits;
  };

  union StdCpuid24MainLeafEbx {
    uint32_t value;
    struct {
      uint32_t  avx10_converged_isa_version  : 8,
                                             : 8,
                                             : 2,
                avx10_vlen_512               : 1,
                                             : 13;
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
                       : 11,
               apx_f   : 1,
                       : 12;
    } bits;
  };

protected:
  static int _cpu;
  static int _model;
  static int _stepping;

  static bool _has_intel_jcc_erratum;

  static address   _cpuinfo_segv_addr;     // address of instruction which causes SEGV
  static address   _cpuinfo_cont_addr;     // address of instruction after the one which causes SEGV
  static address   _cpuinfo_segv_addr_apx; // address of instruction which causes APX specific SEGV
  static address   _cpuinfo_cont_addr_apx; // address of instruction after the one which causes APX specific SEGV

  /*
   * Update following files when declaring new flags:
   * test/lib-test/jdk/test/whitebox/CPUInfoTest.java
   * src/jdk.internal.vm.ci/share/classes/jdk/vm/ci/amd64/AMD64.java
   */
  enum Feature_Flag {
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
    decl(AVX512_IFMA,       "avx512_ifma",       58) /* Integer Vector FMA instructions*/ \
    decl(AVX_IFMA,          "avx_ifma",          59) /* 256-bit VEX-coded variant of AVX512-IFMA*/ \
    decl(APX_F,             "apx_f",             60) /* Intel Advanced Performance Extensions*/ \
    decl(SHA512,            "sha512",            61) /* SHA512 instructions*/ \
    decl(AVX512_FP16,       "avx512_fp16",       62) /* AVX512 FP16 ISA support*/ \
    decl(AVX10_1,           "avx10_1",           63) /* AVX10 512 bit vector ISA Version 1 support*/ \
    decl(AVX10_2,           "avx10_2",           64) /* AVX10 512 bit vector ISA Version 2 support*/

#define DECLARE_CPU_FEATURE_FLAG(id, name, bit) CPU_##id = (bit),
    CPU_FEATURE_FLAGS(DECLARE_CPU_FEATURE_FLAG)
#undef DECLARE_CPU_FEATURE_FLAG
    MAX_CPU_FEATURES
  };

  class VM_Features {
    friend class VMStructs;
    friend class JVMCIVMStructs;

   private:
    uint64_t _features_bitmap[(MAX_CPU_FEATURES / BitsPerLong) + 1];

    STATIC_ASSERT(sizeof(_features_bitmap) * BitsPerByte >= MAX_CPU_FEATURES);

    // Number of 8-byte elements in _bitmap.
    constexpr static int features_bitmap_element_count() {
      return sizeof(_features_bitmap) / sizeof(uint64_t);
    }

    constexpr static int features_bitmap_element_shift_count() {
      return LogBitsPerLong;
    }

    constexpr static uint64_t features_bitmap_element_mask() {
      return (1ULL << features_bitmap_element_shift_count()) - 1;
    }

    static int index(Feature_Flag feature) {
      int idx = feature >> features_bitmap_element_shift_count();
      assert(idx < features_bitmap_element_count(), "Features array index out of bounds");
      return idx;
    }

    static uint64_t bit_mask(Feature_Flag feature) {
      return (1ULL << (feature & features_bitmap_element_mask()));
    }

    static int _features_bitmap_size; // for JVMCI purposes
   public:
    VM_Features() {
      for (int i = 0; i < features_bitmap_element_count(); i++) {
        _features_bitmap[i] = 0;
      }
    }

    void set_feature(Feature_Flag feature) {
      int idx = index(feature);
      _features_bitmap[idx] |= bit_mask(feature);
    }

    void clear_feature(VM_Version::Feature_Flag feature) {
      int idx = index(feature);
      _features_bitmap[idx] &= ~bit_mask(feature);
    }

    bool supports_feature(VM_Version::Feature_Flag feature) {
      int idx = index(feature);
      return (_features_bitmap[idx] & bit_mask(feature)) != 0;
    }
  };

  // CPU feature flags vector, can be affected by VM settings.
  static VM_Features _features;

  // Original CPU feature flags vector, not affected by VM settings.
  static VM_Features _cpu_features;

  static const char* _features_names[];

  static void clear_cpu_features() {
    _features = VM_Features();
    _cpu_features = VM_Features();
  }

  enum Extended_Family {
    // AMD
    CPU_FAMILY_AMD_11H       = 0x11,
    CPU_FAMILY_AMD_17H       = 0x17, /* Zen1 & Zen2 */
    CPU_FAMILY_AMD_19H       = 0x19, /* Zen3 & Zen4 */
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
  class CpuidInfo {
  public:
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

    // cpuid function 7 (structured extended features enumeration leaf)
    // eax = 7, ecx = 0
    SefCpuid7Eax sef_cpuid7_eax;
    SefCpuid7Ebx sef_cpuid7_ebx;
    SefCpuid7Ecx sef_cpuid7_ecx;
    SefCpuid7Edx sef_cpuid7_edx;

    // cpuid function 7 (structured extended features enumeration sub-leaf 1)
    // eax = 7, ecx = 1
    SefCpuid7SubLeaf1Eax sefsl1_cpuid7_eax;
    SefCpuid7SubLeaf1Edx sefsl1_cpuid7_edx;

    // cpuid function 24 converged vector ISA main leaf
    // eax = 24, ecx = 0
    StdCpuid24MainLeafEax std_cpuid24_eax;
    StdCpuid24MainLeafEbx std_cpuid24_ebx;

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

    // Space to save apx registers after signal handle
    jlong        apx_save[2]; // Save r16 and r31

    VM_Features feature_flags() const;

    // Asserts
    void assert_is_initialized() const {
      assert(std_cpuid1_eax.bits.family != 0, "VM_Version not initialized");
    }

    // Extractors
    uint32_t extended_cpu_family() const {
      uint32_t result = std_cpuid1_eax.bits.family;
      result += std_cpuid1_eax.bits.ext_family;
      return result;
    }

    uint32_t extended_cpu_model() const {
      uint32_t result = std_cpuid1_eax.bits.model;
      result |= std_cpuid1_eax.bits.ext_model << 4;
      return result;
    }

    uint32_t cpu_stepping() const {
      uint32_t result = std_cpuid1_eax.bits.stepping;
      return result;
    }
  };

private:
  // The actual cpuid info block
  static CpuidInfo _cpuid_info;

  // Extractors and predicates
  static uint logical_processor_count() {
    uint result = threads_per_core();
    return result;
  }

  static bool compute_has_intel_jcc_erratum();

  static bool os_supports_avx_vectors();
  static bool os_supports_apx_egprs();
  static void get_processor_features();

public:
  // Offsets for cpuid asm stub
  static ByteSize std_cpuid0_offset() { return byte_offset_of(CpuidInfo, std_max_function); }
  static ByteSize std_cpuid1_offset() { return byte_offset_of(CpuidInfo, std_cpuid1_eax); }
  static ByteSize std_cpuid24_offset() { return byte_offset_of(CpuidInfo, std_cpuid24_eax); }
  static ByteSize dcp_cpuid4_offset() { return byte_offset_of(CpuidInfo, dcp_cpuid4_eax); }
  static ByteSize sef_cpuid7_offset() { return byte_offset_of(CpuidInfo, sef_cpuid7_eax); }
  static ByteSize sefsl1_cpuid7_offset() { return byte_offset_of(CpuidInfo, sefsl1_cpuid7_eax); }
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
  static ByteSize apx_save_offset() { return byte_offset_of(CpuidInfo, apx_save); }

  // The value used to check ymm register after signal handle
  static int ymm_test_value()    { return 0xCAFEBABE; }
  static jlong egpr_test_value()   { return 0xCAFEBABECAFEBABELL; }

  static void get_cpu_info_wrapper();
  static void set_cpuinfo_segv_addr(address pc) { _cpuinfo_segv_addr = pc; }
  static bool  is_cpuinfo_segv_addr(address pc) { return _cpuinfo_segv_addr == pc; }
  static void set_cpuinfo_cont_addr(address pc) { _cpuinfo_cont_addr = pc; }
  static address  cpuinfo_cont_addr()           { return _cpuinfo_cont_addr; }

  static void set_cpuinfo_segv_addr_apx(address pc) { _cpuinfo_segv_addr_apx = pc; }
  static bool  is_cpuinfo_segv_addr_apx(address pc) { return _cpuinfo_segv_addr_apx == pc; }
  static void set_cpuinfo_cont_addr_apx(address pc) { _cpuinfo_cont_addr_apx = pc; }
  static address  cpuinfo_cont_addr_apx()           { return _cpuinfo_cont_addr_apx; }

  static void clear_apx_test_state();

  static void clean_cpuFeatures()   {
    VM_Version::clear_cpu_features();
  }
  static void set_avx_cpuFeatures() {
    _features.set_feature(CPU_SSE);
    _features.set_feature(CPU_SSE2);
    _features.set_feature(CPU_AVX);
    _features.set_feature(CPU_VZEROUPPER);
  }
  static void set_evex_cpuFeatures() {
    _features.set_feature(CPU_AVX10_1);
    _features.set_feature(CPU_AVX512F);
    _features.set_feature(CPU_SSE);
    _features.set_feature(CPU_SSE2);
    _features.set_feature(CPU_VZEROUPPER);
  }
  static void set_apx_cpuFeatures() { _features.set_feature(CPU_APX_F); }
  static void set_bmi_cpuFeatures() {
    _features.set_feature(CPU_BMI1);
    _features.set_feature(CPU_BMI2);
    _features.set_feature(CPU_LZCNT);
    _features.set_feature(CPU_POPCNT);
  }

  // Initialization
  static void initialize();

  // Override Abstract_VM_Version implementation
  static void print_platform_virtualization_info(outputStream*);

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
  static void     assert_is_initialized() { _cpuid_info.assert_is_initialized(); }
  static uint32_t extended_cpu_family()   { return _cpuid_info.extended_cpu_family(); }
  static uint32_t extended_cpu_model()    { return _cpuid_info.extended_cpu_model(); }
  static uint32_t cpu_stepping()          { return _cpuid_info.cpu_stepping(); }
  static int  cpu_family()        { return _cpu;}
  static bool is_P6()             { return cpu_family() >= 6; }
  static bool is_intel_server_family()    { return cpu_family() == 6 || cpu_family() == 19; }
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
  static bool supports_cmov()         { return _features.supports_feature(CPU_CMOV); }
  static bool supports_fxsr()         { return _features.supports_feature(CPU_FXSR); }
  static bool supports_ht()           { return _features.supports_feature(CPU_HT); }
  static bool supports_mmx()          { return _features.supports_feature(CPU_MMX); }
  static bool supports_sse()          { return _features.supports_feature(CPU_SSE); }
  static bool supports_sse2()         { return _features.supports_feature(CPU_SSE2); }
  static bool supports_sse3()         { return _features.supports_feature(CPU_SSE3); }
  static bool supports_ssse3()        { return _features.supports_feature(CPU_SSSE3); }
  static bool supports_sse4_1()       { return _features.supports_feature(CPU_SSE4_1); }
  static bool supports_sse4_2()       { return _features.supports_feature(CPU_SSE4_2); }
  static bool supports_popcnt()       { return _features.supports_feature(CPU_POPCNT); }
  static bool supports_avx()          { return _features.supports_feature(CPU_AVX); }
  static bool supports_avx2()         { return _features.supports_feature(CPU_AVX2); }
  static bool supports_tsc()          { return _features.supports_feature(CPU_TSC); }
  static bool supports_rdtscp()       { return _features.supports_feature(CPU_RDTSCP); }
  static bool supports_rdpid()        { return _features.supports_feature(CPU_RDPID); }
  static bool supports_aes()          { return _features.supports_feature(CPU_AES); }
  static bool supports_erms()         { return _features.supports_feature(CPU_ERMS); }
  static bool supports_fsrm()         { return _features.supports_feature(CPU_FSRM); }
  static bool supports_clmul()        { return _features.supports_feature(CPU_CLMUL); }
  static bool supports_rtm()          { return _features.supports_feature(CPU_RTM); }
  static bool supports_bmi1()         { return _features.supports_feature(CPU_BMI1); }
  static bool supports_bmi2()         { return _features.supports_feature(CPU_BMI2); }
  static bool supports_adx()          { return _features.supports_feature(CPU_ADX); }
  static bool supports_evex()         { return _features.supports_feature(CPU_AVX512F); }
  static bool supports_avx512dq()     { return _features.supports_feature(CPU_AVX512DQ); }
  static bool supports_avx512ifma()   { return _features.supports_feature(CPU_AVX512_IFMA); }
  static bool supports_avxifma()      { return _features.supports_feature(CPU_AVX_IFMA); }
  static bool supports_avx512pf()     { return _features.supports_feature(CPU_AVX512PF); }
  static bool supports_avx512er()     { return _features.supports_feature(CPU_AVX512ER); }
  static bool supports_avx512cd()     { return _features.supports_feature(CPU_AVX512CD); }
  static bool supports_avx512bw()     { return _features.supports_feature(CPU_AVX512BW); }
  static bool supports_avx512vl()     { return _features.supports_feature(CPU_AVX512VL); }
  static bool supports_avx512vlbw()   { return (supports_evex() && supports_avx512bw() && supports_avx512vl()); }
  static bool supports_avx512bwdq()   { return (supports_evex() && supports_avx512bw() && supports_avx512dq()); }
  static bool supports_avx512vldq()   { return (supports_evex() && supports_avx512dq() && supports_avx512vl()); }
  static bool supports_avx512vlbwdq() { return (supports_evex() && supports_avx512vl() &&
                                                supports_avx512bw() && supports_avx512dq()); }
  static bool supports_avx512novl()   { return (supports_evex() && !supports_avx512vl()); }
  static bool supports_avx512nobw()   { return (supports_evex() && !supports_avx512bw()); }
  static bool supports_avx256only()   { return (supports_avx2() && !supports_evex()); }
  static bool supports_apx_f()        { return _features.supports_feature(CPU_APX_F); }
  static bool supports_avxonly()      { return ((supports_avx2() || supports_avx()) && !supports_evex()); }
  static bool supports_sha()          { return _features.supports_feature(CPU_SHA); }
  static bool supports_fma()          { return _features.supports_feature(CPU_FMA) && supports_avx(); }
  static bool supports_vzeroupper()   { return _features.supports_feature(CPU_VZEROUPPER); }
  static bool supports_avx512_vpopcntdq()  { return _features.supports_feature(CPU_AVX512_VPOPCNTDQ); }
  static bool supports_avx512_vpclmulqdq() { return _features.supports_feature(CPU_AVX512_VPCLMULQDQ); }
  static bool supports_avx512_vaes()  { return _features.supports_feature(CPU_AVX512_VAES); }
  static bool supports_gfni()         { return _features.supports_feature(CPU_GFNI); }
  static bool supports_avx512_vnni()  { return _features.supports_feature(CPU_AVX512_VNNI); }
  static bool supports_avx512_bitalg()  { return _features.supports_feature(CPU_AVX512_BITALG); }
  static bool supports_avx512_vbmi()  { return _features.supports_feature(CPU_AVX512_VBMI); }
  static bool supports_avx512_vbmi2() { return _features.supports_feature(CPU_AVX512_VBMI2); }
  static bool supports_avx512_fp16()  { return _features.supports_feature(CPU_AVX512_FP16); }
  static bool supports_hv()           { return _features.supports_feature(CPU_HV); }
  static bool supports_serialize()    { return _features.supports_feature(CPU_SERIALIZE); }
  static bool supports_f16c()         { return _features.supports_feature(CPU_F16C); }
  static bool supports_pku()          { return _features.supports_feature(CPU_PKU); }
  static bool supports_ospke()        { return _features.supports_feature(CPU_OSPKE); }
  static bool supports_cet_ss()       { return _features.supports_feature(CPU_CET_SS); }
  static bool supports_cet_ibt()      { return _features.supports_feature(CPU_CET_IBT); }
  static bool supports_sha512()       { return _features.supports_feature(CPU_SHA512); }

  // Intel® AVX10 introduces a versioned approach for enumeration that is monotonically increasing, inclusive,
  // and supporting all vector lengths. Feature set supported by an AVX10 vector ISA version is also supported
  // by all the versions above it.
  static bool supports_avx10_1()      { return _features.supports_feature(CPU_AVX10_1);}
  static bool supports_avx10_2()      { return _features.supports_feature(CPU_AVX10_2);}

  //
  // Feature identification not affected by VM flags
  //
  static bool cpu_supports_evex()     { return _cpu_features.supports_feature(CPU_AVX512F); }

  static bool supports_avx512_simd_sort() {
    if (supports_avx512dq()) {
      // Disable AVX512 version of SIMD Sort on AMD Zen4 Processors.
      if (is_amd() && cpu_family() == CPU_FAMILY_AMD_19H) {
        return false;
      }
      return true;
    }
    return false;
  }

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

  static void insert_features_names(VM_Version::VM_Features features, char* buf, size_t buflen);

  // This checks if the JVM is potentially affected by an erratum on Intel CPUs (SKX102)
  // that causes unpredictable behaviour when jcc crosses 64 byte boundaries. Its microcode
  // mitigation causes regressions when jumps or fused conditional branches cross or end at
  // 32 byte boundaries.
  static bool has_intel_jcc_erratum() { return _has_intel_jcc_erratum; }

  // AMD features
  static bool supports_3dnow_prefetch()    { return _features.supports_feature(CPU_3DNOW_PREFETCH); }
  static bool supports_lzcnt()    { return _features.supports_feature(CPU_LZCNT); }
  static bool supports_sse4a()    { return _features.supports_feature(CPU_SSE4A); }

  static bool is_amd_Barcelona()  { return is_amd() &&
                                           extended_cpu_family() == CPU_FAMILY_AMD_11H; }

  // Intel and AMD newer cores support fast timestamps well
  static bool supports_tscinv_bit() {
    return _features.supports_feature(CPU_TSCINV_BIT);
  }
  static bool supports_tscinv() {
    return _features.supports_feature(CPU_TSCINV);
  }

  // Intel Core and newer cpus have fast IDIV instruction (excluding Atom).
  static bool has_fast_idiv()     { return is_intel() && is_intel_server_family() &&
                                           supports_sse3() && _model != 0x1C; }

  static bool supports_compare_and_exchange() { return true; }

  static int allocate_prefetch_distance(bool use_watermark_prefetch);

  // SSE2 and later processors implement a 'pause' instruction
  // that can be used for efficient implementation of
  // the intrinsic for java.lang.Thread.onSpinWait()
  static bool supports_on_spin_wait() { return supports_sse2(); }

  // x86_64 supports fast class initialization checks
  static bool supports_fast_class_init_checks() {
    return true;
  }

  // x86_64 supports secondary supers table
  constexpr static bool supports_secondary_supers_table() {
    return true;
  }

  constexpr static bool supports_stack_watermark_barrier() {
    return true;
  }

  constexpr static bool supports_recursive_lightweight_locking() {
    return true;
  }

  // For AVX CPUs only. f16c support is disabled if UseAVX == 0.
  static bool supports_float16() {
    return supports_f16c() || supports_avx512vl() || supports_avx512_fp16();
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

  static bool supports_clflush(); // Can't inline due to header file conflict

  // Note: CPU_FLUSHOPT and CPU_CLWB bits should always be zero for 32-bit
  static bool supports_clflushopt() { return (_features.supports_feature(CPU_FLUSHOPT)); }
  static bool supports_clwb() { return (_features.supports_feature(CPU_CLWB)); }

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
