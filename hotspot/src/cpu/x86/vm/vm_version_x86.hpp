/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates.  All Rights Reserved.
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

class VM_Version : public Abstract_VM_Version {
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
                        : 2,
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
                        : 8;
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
                        : 7,
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
                            : 4,
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

  union ExtCpuid8Ecx {
    uint32_t value;
    struct {
      uint32_t cores_per_cpu : 8,
                             : 24;
    } bits;
  };

protected:
   static int _cpu;
   static int _model;
   static int _stepping;
   static int _cpuFeatures;     // features returned by the "cpuid" instruction
                                // 0 if this instruction is not available
   static const char* _features_str;

   enum {
     CPU_CX8    = (1 << 0), // next bits are from cpuid 1 (EDX)
     CPU_CMOV   = (1 << 1),
     CPU_FXSR   = (1 << 2),
     CPU_HT     = (1 << 3),
     CPU_MMX    = (1 << 4),
     CPU_3DNOW  = (1 << 5), // 3DNow comes from cpuid 0x80000001 (EDX)
     CPU_SSE    = (1 << 6),
     CPU_SSE2   = (1 << 7),
     CPU_SSE3   = (1 << 8), // SSE3 comes from cpuid 1 (ECX)
     CPU_SSSE3  = (1 << 9),
     CPU_SSE4A  = (1 << 10),
     CPU_SSE4_1 = (1 << 11),
     CPU_SSE4_2 = (1 << 12),
     CPU_POPCNT = (1 << 13),
     CPU_LZCNT  = (1 << 14)
   } cpuFeatureFlags;

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

    // cpuid function 0x80000005 //AMD L1, Intel reserved
    uint32_t     ext_cpuid5_eax; // unused currently
    uint32_t     ext_cpuid5_ebx; // reserved
    ExtCpuid5Ex  ext_cpuid5_ecx; // L1 data cache info (AMD)
    ExtCpuid5Ex  ext_cpuid5_edx; // L1 instruction cache info (AMD)

    // cpuid function 0x80000008
    uint32_t     ext_cpuid8_eax; // unused currently
    uint32_t     ext_cpuid8_ebx; // reserved
    ExtCpuid8Ecx ext_cpuid8_ecx;
    uint32_t     ext_cpuid8_edx; // reserved
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
  static uint32_t feature_flags() {
    uint32_t result = 0;
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

    // AMD features.
    if (is_amd()) {
      if (_cpuid_info.ext_cpuid1_edx.bits.tdnow != 0)
        result |= CPU_3DNOW;
      if (_cpuid_info.ext_cpuid1_ecx.bits.lzcnt != 0)
        result |= CPU_LZCNT;
      if (_cpuid_info.ext_cpuid1_ecx.bits.sse4a != 0)
        result |= CPU_SSE4A;
    }

    return result;
  }

  static void get_processor_features();

public:
  // Offsets for cpuid asm stub
  static ByteSize std_cpuid0_offset() { return byte_offset_of(CpuidInfo, std_max_function); }
  static ByteSize std_cpuid1_offset() { return byte_offset_of(CpuidInfo, std_cpuid1_eax); }
  static ByteSize dcp_cpuid4_offset() { return byte_offset_of(CpuidInfo, dcp_cpuid4_eax); }
  static ByteSize ext_cpuid1_offset() { return byte_offset_of(CpuidInfo, ext_cpuid1_eax); }
  static ByteSize ext_cpuid5_offset() { return byte_offset_of(CpuidInfo, ext_cpuid5_eax); }
  static ByteSize ext_cpuid8_offset() { return byte_offset_of(CpuidInfo, ext_cpuid8_eax); }
  static ByteSize tpl_cpuidB0_offset() { return byte_offset_of(CpuidInfo, tpl_cpuidB0_eax); }
  static ByteSize tpl_cpuidB1_offset() { return byte_offset_of(CpuidInfo, tpl_cpuidB1_eax); }
  static ByteSize tpl_cpuidB2_offset() { return byte_offset_of(CpuidInfo, tpl_cpuidB2_eax); }

  // Initialization
  static void initialize();

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
           ((_cpuid_info.tpl_cpuidB0_eax & 0x1f | _cpuid_info.tpl_cpuidB0_ebx.bits.logical_cpus) != 0);
  }

  static uint cores_per_cpu()  {
    uint result = 1;
    if (is_intel()) {
      if (supports_processor_topology()) {
        result = _cpuid_info.tpl_cpuidB1_ebx.bits.logical_cpus /
                 _cpuid_info.tpl_cpuidB0_ebx.bits.logical_cpus;
      } else {
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
    return result;
  }

  static intx L1_data_cache_line_size()  {
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
  //
  // AMD features
  //
  static bool supports_3dnow()    { return (_cpuFeatures & CPU_3DNOW) != 0; }
  static bool supports_mmx_ext()  { return is_amd() && _cpuid_info.ext_cpuid1_edx.bits.mmx_amd != 0; }
  static bool supports_3dnow2()   { return is_amd() && _cpuid_info.ext_cpuid1_edx.bits.tdnow2 != 0; }
  static bool supports_lzcnt()    { return (_cpuFeatures & CPU_LZCNT) != 0; }
  static bool supports_sse4a()    { return (_cpuFeatures & CPU_SSE4A) != 0; }

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
};
