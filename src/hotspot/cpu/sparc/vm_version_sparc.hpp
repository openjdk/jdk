/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
  friend class VMStructs;
  friend class JVMCIVMStructs;

protected:
  enum {
    ISA_V9,
    ISA_POPC,
    ISA_VIS1,
    ISA_VIS2,
    ISA_BLK_INIT,
    ISA_FMAF,
    ISA_VIS3,
    ISA_HPC,
    ISA_IMA,
    ISA_AES,
    ISA_DES,
    ISA_KASUMI,
    ISA_CAMELLIA,
    ISA_MD5,
    ISA_SHA1,
    ISA_SHA256,
    ISA_SHA512,
    ISA_MPMUL,
    ISA_MONT,
    ISA_PAUSE,
    ISA_CBCOND,
    ISA_CRC32C,

    ISA_FJATHPLUS,
    ISA_VIS3B,
    ISA_ADI,
    ISA_SPARC5,
    ISA_MWAIT,
    ISA_XMPMUL,
    ISA_XMONT,
    ISA_PAUSE_NSEC,
    ISA_VAMASK,

    ISA_SPARC6,
    ISA_DICTUNP,
    ISA_FPCMPSHL,
    ISA_RLE,
    ISA_SHA3,
    ISA_FJATHPLUS2,
    ISA_VIS3C,
    ISA_SPARC5B,
    ISA_MME,

    // Synthesised properties:

    CPU_FAST_IDIV,
    CPU_FAST_RDPC,
    CPU_FAST_BIS,
    CPU_FAST_LD,
    CPU_FAST_CMOVE,
    CPU_FAST_IND_BR,
    CPU_BLK_ZEROING
  };

private:
  enum { ISA_last_feature = ISA_MME,
         CPU_last_feature = CPU_BLK_ZEROING };

  enum {
    ISA_unknown_msk     = 0,

    ISA_v9_msk          = UINT64_C(1) << ISA_V9,

    ISA_popc_msk        = UINT64_C(1) << ISA_POPC,
    ISA_vis1_msk        = UINT64_C(1) << ISA_VIS1,
    ISA_vis2_msk        = UINT64_C(1) << ISA_VIS2,
    ISA_blk_init_msk    = UINT64_C(1) << ISA_BLK_INIT,
    ISA_fmaf_msk        = UINT64_C(1) << ISA_FMAF,
    ISA_vis3_msk        = UINT64_C(1) << ISA_VIS3,
    ISA_hpc_msk         = UINT64_C(1) << ISA_HPC,
    ISA_ima_msk         = UINT64_C(1) << ISA_IMA,
    ISA_aes_msk         = UINT64_C(1) << ISA_AES,
    ISA_des_msk         = UINT64_C(1) << ISA_DES,
    ISA_kasumi_msk      = UINT64_C(1) << ISA_KASUMI,
    ISA_camellia_msk    = UINT64_C(1) << ISA_CAMELLIA,
    ISA_md5_msk         = UINT64_C(1) << ISA_MD5,
    ISA_sha1_msk        = UINT64_C(1) << ISA_SHA1,
    ISA_sha256_msk      = UINT64_C(1) << ISA_SHA256,
    ISA_sha512_msk      = UINT64_C(1) << ISA_SHA512,
    ISA_mpmul_msk       = UINT64_C(1) << ISA_MPMUL,
    ISA_mont_msk        = UINT64_C(1) << ISA_MONT,
    ISA_pause_msk       = UINT64_C(1) << ISA_PAUSE,
    ISA_cbcond_msk      = UINT64_C(1) << ISA_CBCOND,
    ISA_crc32c_msk      = UINT64_C(1) << ISA_CRC32C,

    ISA_fjathplus_msk   = UINT64_C(1) << ISA_FJATHPLUS,
    ISA_vis3b_msk       = UINT64_C(1) << ISA_VIS3B,
    ISA_adi_msk         = UINT64_C(1) << ISA_ADI,
    ISA_sparc5_msk      = UINT64_C(1) << ISA_SPARC5,
    ISA_mwait_msk       = UINT64_C(1) << ISA_MWAIT,
    ISA_xmpmul_msk      = UINT64_C(1) << ISA_XMPMUL,
    ISA_xmont_msk       = UINT64_C(1) << ISA_XMONT,
    ISA_pause_nsec_msk  = UINT64_C(1) << ISA_PAUSE_NSEC,
    ISA_vamask_msk      = UINT64_C(1) << ISA_VAMASK,

    ISA_sparc6_msk      = UINT64_C(1) << ISA_SPARC6,
    ISA_dictunp_msk     = UINT64_C(1) << ISA_DICTUNP,
    ISA_fpcmpshl_msk    = UINT64_C(1) << ISA_FPCMPSHL,
    ISA_rle_msk         = UINT64_C(1) << ISA_RLE,
    ISA_sha3_msk        = UINT64_C(1) << ISA_SHA3,
    ISA_fjathplus2_msk  = UINT64_C(1) << ISA_FJATHPLUS2,
    ISA_vis3c_msk       = UINT64_C(1) << ISA_VIS3C,
    ISA_sparc5b_msk     = UINT64_C(1) << ISA_SPARC5B,
    ISA_mme_msk         = UINT64_C(1) << ISA_MME,

    CPU_fast_idiv_msk   = UINT64_C(1) << CPU_FAST_IDIV,
    CPU_fast_rdpc_msk   = UINT64_C(1) << CPU_FAST_RDPC,
    CPU_fast_bis_msk    = UINT64_C(1) << CPU_FAST_BIS,
    CPU_fast_ld_msk     = UINT64_C(1) << CPU_FAST_LD,
    CPU_fast_cmove_msk  = UINT64_C(1) << CPU_FAST_CMOVE,
    CPU_fast_ind_br_msk = UINT64_C(1) << CPU_FAST_IND_BR,
    CPU_blk_zeroing_msk = UINT64_C(1) << CPU_BLK_ZEROING,

    last_feature_msk    = CPU_blk_zeroing_msk,
    full_feature_msk    = (last_feature_msk << 1) - 1
  };

/* The following, previously supported, SPARC implementations are no longer
 * supported.
 *
 *  UltraSPARC I/II:
 *    SPARC-V9, VIS
 *  UltraSPARC III/+:  (Cheetah/+)
 *    SPARC-V9, VIS
 *  UltraSPARC IV:     (Jaguar)
 *    SPARC-V9, VIS
 *  UltraSPARC IV+:    (Panther)
 *    SPARC-V9, VIS, POPC
 *
 * The currently supported SPARC implementations are listed below (including
 * generic V9 support).
 *
 *  UltraSPARC T1:     (Niagara)
 *    SPARC-V9, VIS, ASI_BIS                (Crypto/hash in SPU)
 *  UltraSPARC T2:     (Niagara-2)
 *    SPARC-V9, VIS, ASI_BIS, POPC          (Crypto/hash in SPU)
 *  UltraSPARC T2+:    (Victoria Falls, etc.)
 *    SPARC-V9, VIS, VIS2, ASI_BIS, POPC    (Crypto/hash in SPU)
 *
 *  UltraSPARC T3:     (Rainbow Falls/C2)
 *    SPARC-V9, VIS, VIS2, ASI_BIS, POPC    (Crypto/hash in SPU)
 *
 *  Oracle SPARC T4/T5/M5:  (Core C3)
 *    SPARC-V9, VIS, VIS2, VIS3, ASI_BIS, HPC, POPC, FMAF, IMA, PAUSE, CBCOND,
 *    AES, DES, Kasumi, Camellia, MD5, SHA1, SHA256, SHA512, CRC32C, MONT, MPMUL
 *
 *  Oracle SPARC M7:   (Core C4)
 *    SPARC-V9, VIS, VIS2, VIS3, ASI_BIS, HPC, POPC, FMAF, IMA, PAUSE, CBCOND,
 *    AES, DES, Camellia, MD5, SHA1, SHA256, SHA512, CRC32C, MONT, MPMUL, VIS3b,
 *    ADI, SPARC5, MWAIT, XMPMUL, XMONT, PAUSE_NSEC, VAMASK
 *
 *  Oracle SPARC M8:   (Core C5)
 *    SPARC-V9, VIS, VIS2, VIS3, ASI_BIS, HPC, POPC, FMAF, IMA, PAUSE, CBCOND,
 *    AES, DES, Camellia, MD5, SHA1, SHA256, SHA512, CRC32C, MONT, MPMUL, VIS3b,
 *    ADI, SPARC5, MWAIT, XMPMUL, XMONT, PAUSE_NSEC, VAMASK, SPARC6, FPCMPSHL,
 *    DICTUNP, RLE, SHA3, MME
 *
 *    NOTE: Oracle Number support ignored.
 */
  enum {
    niagara1_msk = ISA_v9_msk | ISA_vis1_msk | ISA_blk_init_msk,
    niagara2_msk = niagara1_msk | ISA_popc_msk,

    core_C2_msk  = niagara2_msk | ISA_vis2_msk,

    core_C3_msk  = core_C2_msk | ISA_fmaf_msk | ISA_vis3_msk | ISA_hpc_msk |
        ISA_ima_msk | ISA_aes_msk | ISA_des_msk | ISA_kasumi_msk |
        ISA_camellia_msk | ISA_md5_msk | ISA_sha1_msk | ISA_sha256_msk |
        ISA_sha512_msk | ISA_mpmul_msk | ISA_mont_msk | ISA_pause_msk |
        ISA_cbcond_msk | ISA_crc32c_msk,

    core_C4_msk  = core_C3_msk - ISA_kasumi_msk |
        ISA_vis3b_msk | ISA_adi_msk | ISA_sparc5_msk | ISA_mwait_msk |
        ISA_xmpmul_msk | ISA_xmont_msk | ISA_pause_nsec_msk | ISA_vamask_msk,

    core_C5_msk = core_C4_msk | ISA_sparc6_msk | ISA_dictunp_msk |
        ISA_fpcmpshl_msk | ISA_rle_msk | ISA_sha3_msk | ISA_mme_msk,

    ultra_sparc_t1_msk = niagara1_msk,
    ultra_sparc_t2_msk = niagara2_msk,
    ultra_sparc_t3_msk = core_C2_msk,
    ultra_sparc_m5_msk = core_C3_msk,   // NOTE: First out-of-order pipeline.
    ultra_sparc_m7_msk = core_C4_msk,
    ultra_sparc_m8_msk = core_C5_msk
  };

  static uint _L2_data_cache_line_size;
  static uint L2_data_cache_line_size() { return _L2_data_cache_line_size; }

  static void determine_features();
  static void platform_features();
  static void print_features();

public:
  enum {
    // Adopt a conservative behaviour (modelling single-insn-fetch-n-issue) for
    // Niagara (and SPARC64). While there are at least two entries/slots in the
    // instruction fetch buffer on any Niagara core (and as many as eight on a
    // SPARC64), the performance improvement from keeping hot branch targets on
    // optimally aligned addresses is such a small one (if any) that we choose
    // not to use the extra code space required.

    insn_fetch_alignment = 4    // Byte alignment in L1 insn. cache.
  };

  static void initialize();

  static void init_before_ergo() { determine_features(); }

  // Instruction feature support:

  static bool has_v9()           { return (_features & ISA_v9_msk) != 0; }
  static bool has_popc()         { return (_features & ISA_popc_msk) != 0; }
  static bool has_vis1()         { return (_features & ISA_vis1_msk) != 0; }
  static bool has_vis2()         { return (_features & ISA_vis2_msk) != 0; }
  static bool has_blk_init()     { return (_features & ISA_blk_init_msk) != 0; }
  static bool has_fmaf()         { return (_features & ISA_fmaf_msk) != 0; }
  static bool has_vis3()         { return (_features & ISA_vis3_msk) != 0; }
  static bool has_hpc()          { return (_features & ISA_hpc_msk) != 0; }
  static bool has_ima()          { return (_features & ISA_ima_msk) != 0; }
  static bool has_aes()          { return (_features & ISA_aes_msk) != 0; }
  static bool has_des()          { return (_features & ISA_des_msk) != 0; }
  static bool has_kasumi()       { return (_features & ISA_kasumi_msk) != 0; }
  static bool has_camellia()     { return (_features & ISA_camellia_msk) != 0; }
  static bool has_md5()          { return (_features & ISA_md5_msk) != 0; }
  static bool has_sha1()         { return (_features & ISA_sha1_msk) != 0; }
  static bool has_sha256()       { return (_features & ISA_sha256_msk) != 0; }
  static bool has_sha512()       { return (_features & ISA_sha512_msk) != 0; }
  static bool has_mpmul()        { return (_features & ISA_mpmul_msk) != 0; }
  static bool has_mont()         { return (_features & ISA_mont_msk) != 0; }
  static bool has_pause()        { return (_features & ISA_pause_msk) != 0; }
  static bool has_cbcond()       { return (_features & ISA_cbcond_msk) != 0; }
  static bool has_crc32c()       { return (_features & ISA_crc32c_msk) != 0; }

  static bool has_athena_plus()  { return (_features & ISA_fjathplus_msk) != 0; }
  static bool has_vis3b()        { return (_features & ISA_vis3b_msk) != 0; }
  static bool has_adi()          { return (_features & ISA_adi_msk) != 0; }
  static bool has_sparc5()       { return (_features & ISA_sparc5_msk) != 0; }
  static bool has_mwait()        { return (_features & ISA_mwait_msk) != 0; }
  static bool has_xmpmul()       { return (_features & ISA_xmpmul_msk) != 0; }
  static bool has_xmont()        { return (_features & ISA_xmont_msk) != 0; }
  static bool has_pause_nsec()   { return (_features & ISA_pause_nsec_msk) != 0; }
  static bool has_vamask()       { return (_features & ISA_vamask_msk) != 0; }

  static bool has_sparc6()       { return (_features & ISA_sparc6_msk) != 0; }
  static bool has_dictunp()      { return (_features & ISA_dictunp_msk) != 0; }
  static bool has_fpcmpshl()     { return (_features & ISA_fpcmpshl_msk) != 0; }
  static bool has_rle()          { return (_features & ISA_rle_msk) != 0; }
  static bool has_sha3()         { return (_features & ISA_sha3_msk) != 0; }
  static bool has_athena_plus2() { return (_features & ISA_fjathplus2_msk) != 0; }
  static bool has_vis3c()        { return (_features & ISA_vis3c_msk) != 0; }
  static bool has_sparc5b()      { return (_features & ISA_sparc5b_msk) != 0; }
  static bool has_mme()          { return (_features & ISA_mme_msk) != 0; }

  static bool has_fast_idiv()    { return (_features & CPU_fast_idiv_msk) != 0; }
  static bool has_fast_rdpc()    { return (_features & CPU_fast_rdpc_msk) != 0; }
  static bool has_fast_bis()     { return (_features & CPU_fast_bis_msk) != 0; }
  static bool has_fast_ld()      { return (_features & CPU_fast_ld_msk) != 0; }
  static bool has_fast_cmove()   { return (_features & CPU_fast_cmove_msk) != 0; }

  // If indirect and direct branching is equally fast.
  static bool has_fast_ind_br()  { return (_features & CPU_fast_ind_br_msk) != 0; }
  // If SPARC BIS to the beginning of cache line always zeros it.
  static bool has_blk_zeroing()  { return (_features & CPU_blk_zeroing_msk) != 0; }

  static bool supports_compare_and_exchange() { return true; }

  // FIXME: To be removed.
  static bool is_post_niagara()  {
    return (_features & niagara2_msk) == niagara2_msk;
  }

  // Default prefetch block size on SPARC.
  static uint prefetch_data_size() { return L2_data_cache_line_size(); }

 private:
  // Prefetch policy and characteristics:
  //
  // These support routines are used in order to isolate any CPU/core specific
  // logic from the actual flag/option processing.  They should reflect the HW
  // characteristics for the associated options on the current platform.
  //
  // The three Prefetch* options below (assigned -1 in the configuration) are
  // treated according to (given the accepted range [-1..<maxint>]):
  //  -1: Determine a proper HW-specific value for the current HW.
  //   0: Off
  //  >0: Command-line supplied value to use.
  //
  // FIXME: The documentation string in the configuration is wrong, saying that
  //        -1 is also interpreted as off.
  //
  static intx prefetch_copy_interval_in_bytes() {
    intx bytes = PrefetchCopyIntervalInBytes;
    return bytes < 0 ? 512 : bytes;
  }
  static intx prefetch_scan_interval_in_bytes() {
    intx bytes = PrefetchScanIntervalInBytes;
    return bytes < 0 ? 512 : bytes;
  }
  static intx prefetch_fields_ahead() {
    intx count = PrefetchFieldsAhead;
    return count < 0 ? 0 : count;
  }

  // AllocatePrefetchDistance is treated under the same interpretation as the
  // Prefetch* options above (i.e., -1, 0, >0).
  static intx allocate_prefetch_distance() {
    intx count = AllocatePrefetchDistance;
    return count < 0 ? 512 : count;
  }

  // AllocatePrefetchStyle is guaranteed to be in range [0..3] defined by the
  // configuration.
  static intx allocate_prefetch_style() {
    intx distance = allocate_prefetch_distance();
    // Return 0 (off/none) if AllocatePrefetchDistance was not defined.
    return distance > 0 ? AllocatePrefetchStyle : 0;
  }

 public:
  // Assembler testing
  static void allow_all();
  static void revert();

  // Override the Abstract_VM_Version implementation.
  //
  // FIXME: Removed broken test on sun4v (always false when invoked prior to the
  //        proper capability setup), thus always returning 2. Still need to fix
  //        this properly in order to enable complete page size support.
  static uint page_size_count() { return 2; }

  // Calculates the number of parallel threads
  static unsigned int calc_parallel_worker_threads();
};

#endif // CPU_SPARC_VM_VM_VERSION_SPARC_HPP
