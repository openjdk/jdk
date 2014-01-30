/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_VM_VERSION_SPARC_HPP
#define CPU_SPARC_VM_VM_VERSION_SPARC_HPP

#include "runtime/globals_extension.hpp"
#include "runtime/vm_version.hpp"

class VM_Version: public Abstract_VM_Version {
protected:
  enum Feature_Flag {
    v8_instructions      = 0,
    hardware_mul32       = 1,
    hardware_div32       = 2,
    hardware_fsmuld      = 3,
    hardware_popc        = 4,
    v9_instructions      = 5,
    vis1_instructions    = 6,
    vis2_instructions    = 7,
    sun4v_instructions   = 8,
    blk_init_instructions = 9,
    fmaf_instructions    = 10,
    fmau_instructions    = 11,
    vis3_instructions    = 12,
    cbcond_instructions  = 13,
    sparc64_family       = 14,
    M_family             = 15,
    T_family             = 16,
    T1_model             = 17,
    sparc5_instructions  = 18,
    aes_instructions     = 19
  };

  enum Feature_Flag_Set {
    unknown_m           = 0,
    all_features_m      = -1,

    v8_instructions_m       = 1 << v8_instructions,
    hardware_mul32_m        = 1 << hardware_mul32,
    hardware_div32_m        = 1 << hardware_div32,
    hardware_fsmuld_m       = 1 << hardware_fsmuld,
    hardware_popc_m         = 1 << hardware_popc,
    v9_instructions_m       = 1 << v9_instructions,
    vis1_instructions_m     = 1 << vis1_instructions,
    vis2_instructions_m     = 1 << vis2_instructions,
    sun4v_m                 = 1 << sun4v_instructions,
    blk_init_instructions_m = 1 << blk_init_instructions,
    fmaf_instructions_m     = 1 << fmaf_instructions,
    fmau_instructions_m     = 1 << fmau_instructions,
    vis3_instructions_m     = 1 << vis3_instructions,
    cbcond_instructions_m   = 1 << cbcond_instructions,
    sparc64_family_m        = 1 << sparc64_family,
    M_family_m              = 1 << M_family,
    T_family_m              = 1 << T_family,
    T1_model_m              = 1 << T1_model,
    sparc5_instructions_m   = 1 << sparc5_instructions,
    aes_instructions_m      = 1 << aes_instructions,

    generic_v8_m        = v8_instructions_m | hardware_mul32_m | hardware_div32_m | hardware_fsmuld_m,
    generic_v9_m        = generic_v8_m | v9_instructions_m,
    ultra3_m            = generic_v9_m | vis1_instructions_m | vis2_instructions_m,

    // Temporary until we have something more accurate
    niagara1_unique_m   = sun4v_m,
    niagara1_m          = generic_v9_m | niagara1_unique_m
  };

  static int  _features;
  static const char* _features_str;

  static void print_features();
  static int  determine_features();
  static int  platform_features(int features);

  // Returns true if the platform is in the niagara line (T series)
  static bool is_M_family(int features) { return (features & M_family_m) != 0; }
  static bool is_T_family(int features) { return (features & T_family_m) != 0; }
  static bool is_niagara() { return is_T_family(_features); }
#ifdef ASSERT
  static bool is_niagara(int features)  {
    // 'sun4v_m' may be defined on both Sun/Oracle Sparc CPUs as well as
    // on Fujitsu Sparc64 CPUs, but only Sun/Oracle Sparcs can be 'niagaras'.
    return (features & sun4v_m) != 0 && (features & sparc64_family_m) == 0;
  }
#endif

  // Returns true if it is niagara1 (T1).
  static bool is_T1_model(int features) { return is_T_family(features) && ((features & T1_model_m) != 0); }

  static int maximum_niagara1_processor_count() { return 32; }

public:
  // Initialization
  static void initialize();

  // Instruction support
  static bool has_v8()                  { return (_features & v8_instructions_m) != 0; }
  static bool has_v9()                  { return (_features & v9_instructions_m) != 0; }
  static bool has_hardware_mul32()      { return (_features & hardware_mul32_m) != 0; }
  static bool has_hardware_div32()      { return (_features & hardware_div32_m) != 0; }
  static bool has_hardware_fsmuld()     { return (_features & hardware_fsmuld_m) != 0; }
  static bool has_hardware_popc()       { return (_features & hardware_popc_m) != 0; }
  static bool has_vis1()                { return (_features & vis1_instructions_m) != 0; }
  static bool has_vis2()                { return (_features & vis2_instructions_m) != 0; }
  static bool has_vis3()                { return (_features & vis3_instructions_m) != 0; }
  static bool has_blk_init()            { return (_features & blk_init_instructions_m) != 0; }
  static bool has_cbcond()              { return (_features & cbcond_instructions_m) != 0; }
  static bool has_sparc5_instr()        { return (_features & sparc5_instructions_m) != 0; }
  static bool has_aes()                 { return (_features & aes_instructions_m) != 0; }

  static bool supports_compare_and_exchange()
                                        { return has_v9(); }

  // Returns true if the platform is in the niagara line (T series)
  // and newer than the niagara1.
  static bool is_niagara_plus()         { return is_T_family(_features) && !is_T1_model(_features); }

  static bool is_M_series()             { return is_M_family(_features); }
  static bool is_T4()                   { return is_T_family(_features) && has_cbcond(); }
  static bool is_T7()                   { return is_T_family(_features) && has_sparc5_instr(); }

  // Fujitsu SPARC64
  static bool is_sparc64()              { return (_features & sparc64_family_m) != 0; }

  static bool is_sun4v()                { return (_features & sun4v_m) != 0; }
  static bool is_ultra3()               { return (_features & ultra3_m) == ultra3_m && !is_sun4v() && !is_sparc64(); }

  static bool has_fast_fxtof()          { return is_niagara() || is_sparc64() || has_v9() && !is_ultra3(); }
  static bool has_fast_idiv()           { return is_niagara_plus() || is_sparc64(); }

  // T4 and newer Sparc have fast RDPC instruction.
  static bool has_fast_rdpc()           { return is_T4(); }

  // On T4 and newer Sparc BIS to the beginning of cache line always zeros it.
  static bool has_block_zeroing()       { return has_blk_init() && is_T4(); }

  static const char* cpu_features()     { return _features_str; }

  static intx prefetch_data_size()  {
    return is_T4() && !is_T7() ? 32 : 64;  // default prefetch block size on sparc
  }

  // Prefetch
  static intx prefetch_copy_interval_in_bytes() {
    intx interval = PrefetchCopyIntervalInBytes;
    return interval >= 0 ? interval : (has_v9() ? 512 : 0);
  }
  static intx prefetch_scan_interval_in_bytes() {
    intx interval = PrefetchScanIntervalInBytes;
    return interval >= 0 ? interval : (has_v9() ? 512 : 0);
  }
  static intx prefetch_fields_ahead() {
    intx count = PrefetchFieldsAhead;
    return count >= 0 ? count : (is_ultra3() ? 1 : 0);
  }

  static intx allocate_prefetch_distance() {
    // This method should be called before allocate_prefetch_style().
    intx count = AllocatePrefetchDistance;
    if (count < 0) { // default is not defined ?
      count = 512;
    }
    return count;
  }
  static intx allocate_prefetch_style() {
    assert(AllocatePrefetchStyle >= 0, "AllocatePrefetchStyle should be positive");
    // Return 0 if AllocatePrefetchDistance was not defined.
    return AllocatePrefetchDistance > 0 ? AllocatePrefetchStyle : 0;
  }

  // Assembler testing
  static void allow_all();
  static void revert();

  // Override the Abstract_VM_Version implementation.
  static uint page_size_count() { return is_sun4v() ? 4 : 2; }

  // Calculates the number of parallel threads
  static unsigned int calc_parallel_worker_threads();
};

#endif // CPU_SPARC_VM_VM_VERSION_SPARC_HPP
