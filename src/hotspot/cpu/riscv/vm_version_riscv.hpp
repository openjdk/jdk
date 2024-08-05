/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2023, Huawei Technologies Co., Ltd. All rights reserved.
 * Copyright (c) 2023, Rivos Inc. All rights reserved.
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

#ifndef CPU_RISCV_VM_VERSION_RISCV_HPP
#define CPU_RISCV_VM_VERSION_RISCV_HPP

#include "runtime/abstract_vm_version.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/sizes.hpp"

class RiscvHwprobe;

class VM_Version : public Abstract_VM_Version {
  friend RiscvHwprobe;
 private:

  // JEDEC encoded as ((bank - 1) << 7) | (0x7f & JEDEC)
  enum VendorId {
    RIVOS = 0x6cf, // JEDEC: 0x4f, Bank: 14
  };

  class RVFeatureValue {
    const char* const _pretty;
    const bool        _feature_string;
    const uint64_t    _feature_bit;
    bool              _enabled;
    int64_t           _value;
   public:
    RVFeatureValue(const char* pretty, int bit_num, bool fstring) :
      _pretty(pretty), _feature_string(fstring), _feature_bit(nth_bit(bit_num)),
      _enabled(false), _value(-1) {
    }
    void enable_feature(int64_t value = 0) {
      _enabled = true;
      _value = value;
    }
    void disable_feature() {
      _enabled = false;
      _value = -1;
    }
    const char* pretty()         { return _pretty; }
    uint64_t feature_bit()       { return _feature_bit; }
    bool feature_string()        { return _feature_string; }
    bool enabled()               { return _enabled; }
    int64_t value()              { return _value; }
    virtual void update_flag() = 0;
  };

  #define UPDATE_DEFAULT(flag)             \
  void update_flag() {                     \
      assert(enabled(), "Must be.");       \
      if (FLAG_IS_DEFAULT(flag)) {         \
        FLAG_SET_DEFAULT(flag, true);      \
      } else {                             \
        /* Sync CPU features with flags */ \
        if (!flag) {                       \
          disable_feature();               \
        }                                  \
      }                                    \
  }                                        \

  #define NO_UPDATE_DEFAULT                \
  void update_flag() {}                    \

  // Frozen standard extensions
  // I RV64I
  // M Integer Multiplication and Division
  // A Atomic Instructions
  // F Single-Precision Floating-Point
  // D Single-Precision Floating-Point
  // (G = M + A + F + D)
  // Q Quad-Precision Floating-Point
  // C Compressed Instructions
  // H Hypervisor
  //
  // Others, open and non-standard
  // V Vector
  //
  // Cache Management Operations
  // Zicbom Cache Block Management Operations
  // Zicboz Cache Block Zero Operations
  // Zicbop Cache Block Prefetch Operations
  //
  // Bit-manipulation
  // Zba Address generation instructions
  // Zbb Basic bit-manipulation
  // Zbc Carry-less multiplication
  // Zbs Single-bit instructions
  //
  // Zfh Half-Precision Floating-Point instructions
  //
  // Zicsr Control and Status Register (CSR) Instructions
  // Zifencei Instruction-Fetch Fence
  // Zic64b Cache blocks must be 64 bytes in size, naturally aligned in the address space.
  // Zihintpause Pause instruction HINT
  //
  // Zc  Code Size Reduction - Additional compressed instructions.
  // Zcb Simple code-size saving instructions
  //
  // Other features and settings
  // mvendorid Manufactory JEDEC id encoded, ISA vol 2 3.1.2..
  // marchid   Id for microarch. Mvendorid plus marchid uniquely identify the microarch.
  // mimpid    A unique encoding of the version of the processor implementation.
  // unaligned_access Unaligned memory accesses (unknown, unspported, emulated, slow, firmware, fast)
  // satp mode SATP bits (number of virtual addr bits) mbare, sv39, sv48, sv57, sv64

 public:

  #define RV_NO_FLAG_BIT (BitsPerWord+1) // nth_bit will return 0 on values larger than BitsPerWord

  // declaration name  , extension name,    bit pos ,in str, mapped flag)
  #define RV_FEATURE_FLAGS(decl)                                                                \
  decl(ext_I           , "i"           ,    ('I' - 'A'), true , NO_UPDATE_DEFAULT)              \
  decl(ext_M           , "m"           ,    ('M' - 'A'), true , NO_UPDATE_DEFAULT)              \
  decl(ext_A           , "a"           ,    ('A' - 'A'), true , NO_UPDATE_DEFAULT)              \
  decl(ext_F           , "f"           ,    ('F' - 'A'), true , NO_UPDATE_DEFAULT)              \
  decl(ext_D           , "d"           ,    ('D' - 'A'), true , NO_UPDATE_DEFAULT)              \
  decl(ext_C           , "c"           ,    ('C' - 'A'), true , UPDATE_DEFAULT(UseRVC))         \
  decl(ext_Q           , "q"           ,    ('Q' - 'A'), true , NO_UPDATE_DEFAULT)              \
  decl(ext_H           , "h"           ,    ('H' - 'A'), true , NO_UPDATE_DEFAULT)              \
  decl(ext_V           , "v"           ,    ('V' - 'A'), true , UPDATE_DEFAULT(UseRVV))         \
  decl(ext_Zicbom      , "Zicbom"      , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZicbom))      \
  decl(ext_Zicboz      , "Zicboz"      , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZicboz))      \
  decl(ext_Zicbop      , "Zicbop"      , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZicbop))      \
  decl(ext_Zba         , "Zba"         , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZba))         \
  decl(ext_Zbb         , "Zbb"         , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZbb))         \
  decl(ext_Zbc         , "Zbc"         , RV_NO_FLAG_BIT, true , NO_UPDATE_DEFAULT)              \
  decl(ext_Zbs         , "Zbs"         , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZbs))         \
  decl(ext_Zcb         , "Zcb"         , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZcb))         \
  decl(ext_Zfh         , "Zfh"         , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZfh))         \
  decl(ext_Zicsr       , "Zicsr"       , RV_NO_FLAG_BIT, true , NO_UPDATE_DEFAULT)              \
  decl(ext_Zifencei    , "Zifencei"    , RV_NO_FLAG_BIT, true , NO_UPDATE_DEFAULT)              \
  decl(ext_Zic64b      , "Zic64b"      , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZic64b))      \
  decl(ext_Ztso        , "Ztso"        , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZtso))        \
  decl(ext_Zihintpause , "Zihintpause" , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZihintpause)) \
  decl(ext_Zacas       , "Zacas"       , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZacas))       \
  decl(ext_Zvbb        , "Zvbb"        , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZvbb))        \
  decl(ext_Zvfh        , "Zvfh"        , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZvfh))        \
  decl(ext_Zvkn        , "Zvkn"        , RV_NO_FLAG_BIT, true , UPDATE_DEFAULT(UseZvkn))        \
  decl(mvendorid       , "VendorId"    , RV_NO_FLAG_BIT, false, NO_UPDATE_DEFAULT)              \
  decl(marchid         , "ArchId"      , RV_NO_FLAG_BIT, false, NO_UPDATE_DEFAULT)              \
  decl(mimpid          , "ImpId"       , RV_NO_FLAG_BIT, false, NO_UPDATE_DEFAULT)              \
  decl(unaligned_access, "Unaligned"   , RV_NO_FLAG_BIT, false, NO_UPDATE_DEFAULT)              \
  decl(satp_mode       , "SATP"        , RV_NO_FLAG_BIT, false, NO_UPDATE_DEFAULT)              \

  #define DECLARE_RV_FEATURE(NAME, PRETTY, BIT, FSTRING, FLAGF)        \
  struct NAME##RVFeatureValue : public RVFeatureValue {                \
    NAME##RVFeatureValue(const char* pretty, int bit, bool fstring) :  \
      RVFeatureValue(pretty, bit, fstring) {}                          \
    FLAGF;                                                             \
  };                                                                   \
  static NAME##RVFeatureValue NAME;                                    \

  RV_FEATURE_FLAGS(DECLARE_RV_FEATURE)
  #undef DECLARE_RV_FEATURE

  // enable extensions based on profile, current supported profiles:
  //  RVA20U64
  //  RVA22U64
  //  RVA23U64
  // NOTE: we only enable the mandatory extensions, not optional extension.
  #define RV_ENABLE_EXTENSION(UseExtension)     \
    if (FLAG_IS_DEFAULT(UseExtension)) {        \
      FLAG_SET_DEFAULT(UseExtension, true);     \
    }                                           \

  // https://github.com/riscv/riscv-profiles/blob/main/profiles.adoc#rva20-profiles
  #define RV_USE_RVA20U64                            \
    RV_ENABLE_EXTENSION(UseRVC)                      \

  static void useRVA20U64Profile();

  // https://github.com/riscv/riscv-profiles/blob/main/profiles.adoc#rva22-profiles
  #define RV_USE_RVA22U64                            \
    RV_ENABLE_EXTENSION(UseRVC)                      \
    RV_ENABLE_EXTENSION(UseZba)                      \
    RV_ENABLE_EXTENSION(UseZbb)                      \
    RV_ENABLE_EXTENSION(UseZbs)                      \
    RV_ENABLE_EXTENSION(UseZic64b)                   \
    RV_ENABLE_EXTENSION(UseZicbom)                   \
    RV_ENABLE_EXTENSION(UseZicbop)                   \
    RV_ENABLE_EXTENSION(UseZicboz)                   \
    RV_ENABLE_EXTENSION(UseZihintpause)              \

  static void useRVA22U64Profile();

  // https://github.com/riscv/riscv-profiles/blob/main/rva23-profile.adoc#rva23u64-profile
  #define RV_USE_RVA23U64                           \
    RV_ENABLE_EXTENSION(UseRVC)                     \
    RV_ENABLE_EXTENSION(UseRVV)                     \
    RV_ENABLE_EXTENSION(UseZba)                     \
    RV_ENABLE_EXTENSION(UseZbb)                     \
    RV_ENABLE_EXTENSION(UseZbs)                     \
    RV_ENABLE_EXTENSION(UseZcb)                     \
    RV_ENABLE_EXTENSION(UseZic64b)                  \
    RV_ENABLE_EXTENSION(UseZicbom)                  \
    RV_ENABLE_EXTENSION(UseZicbop)                  \
    RV_ENABLE_EXTENSION(UseZicboz)                  \
    RV_ENABLE_EXTENSION(UseZihintpause)             \

  static void useRVA23U64Profile();

  // VM modes (satp.mode) privileged ISA 1.10
  enum VM_MODE : int {
    VM_NOTSET = -1,
    VM_MBARE  = 0,
    VM_SV39   = 39,
    VM_SV48   = 48,
    VM_SV57   = 57,
    VM_SV64   = 64
  };

  static VM_MODE parse_satp_mode(const char* vm_mode);

  // Values from riscv_hwprobe()
  enum UNALIGNED_ACCESS : int {
    MISALIGNED_UNKNOWN     = 0,
    MISALIGNED_EMULATED    = 1,
    MISALIGNED_SLOW        = 2,
    MISALIGNED_FAST        = 3,
    MISALIGNED_UNSUPPORTED = 4
  };

  // Null terminated list
  static RVFeatureValue* _feature_list[];

  // Enables features in _feature_list
  static void setup_cpu_available_features();
  // Helper for specific queries
  static void os_aux_features();
  static char* os_uarch_additional_features();
  static void vendor_features();
  // Vendors specific features
  static void rivos_features();

  // Determine vector length iff ext_V/UseRVV
  static uint32_t cpu_vector_length();
  static uint32_t _initial_vector_length;

  static void common_initialize();

#ifdef COMPILER2
  static void c2_initialize();
#endif // COMPILER2

 public:
  // Initialization
  static void initialize();
  static void initialize_cpu_information();

  constexpr static bool supports_stack_watermark_barrier() { return true; }

  constexpr static bool supports_recursive_lightweight_locking() { return true; }

  constexpr static bool supports_secondary_supers_table() { return true; }

  static bool supports_on_spin_wait() { return UseZihintpause; }

  // RISCV64 supports fast class initialization checks
  static bool supports_fast_class_init_checks() { return true; }
};

#endif // CPU_RISCV_VM_VERSION_RISCV_HPP
