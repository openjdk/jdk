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
#include "utilities/growableArray.hpp"
#include "utilities/sizes.hpp"

class RiscvHwprobe;

class VM_Version : public Abstract_VM_Version {
  friend RiscvHwprobe;
 private:
  class RVFeatureValue {
    const char* _pretty;
    bool        _feature_string;
    uint64_t    _feature_bit;
    bool        _enabled;
    int64_t     _value;
   public:
    RVFeatureValue(const char* pretty, uint64_t bit, bool fstring) :
      _pretty(pretty), _feature_string(fstring), _feature_bit(0),
      _enabled(false), _value(-1) {
        _feature_bit = (1ULL << bit);
    }
    void enable_feature(int64_t value = 0) {
      _enabled = true;
      _value = value;
    }
    const char* pretty()   { return _pretty; }
    uint64_t feature_bit() { return _feature_bit; }
    bool feature_string()  { return _feature_string; }
    bool enabled()         { return _enabled; }
    int64_t value()        { return _value; }
    virtual void update_flag() = 0;
  };

  #define UPDATE_DEFAULT(flag)        \
  void update_flag() {                \
      assert(enabled(), "Must be.");  \
      if (FLAG_IS_DEFAULT(flag)) {    \
        FLAG_SET_DEFAULT(flag, true); \
      }                               \
  }                                   \

  #define NO_UPDATE_DEFAULT           \
  void update_flag() {}               \

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
  // Zicsr Control and Status Register (CSR) Instructions
  // Zifencei Instruction-Fetch Fence
  // Zic64b Cache blocks must be 64 bytes in size, naturally aligned in the address space.
  // Zihintpause Pause instruction HINT
  //
  // Other features and settings
  // mvendorid Manufactory JEDEC
  // marchid   Manufactory prop.
  // mimpid    Manufactory prop.
  // unaligned_access Unaligned memory accesses (unknown, unspported, emulated, slow, firmware, fast)
  // satp mode SATP bitsm mbare, sv39, sv48, sv57, sv64

  // declaration name  , extension name,    bit pos ,in str, mapped flag)
  #define RV_FEATURE_FLAGS(decl)                                                             \
  decl(ext_I           , "i"           , ('I' - 'A'), true , NO_UPDATE_DEFAULT)              \
  decl(ext_M           , "m"           , ('M' - 'A'), true , NO_UPDATE_DEFAULT)              \
  decl(ext_A           , "a"           , ('A' - 'A'), true , NO_UPDATE_DEFAULT)              \
  decl(ext_F           , "f"           , ('F' - 'A'), true , NO_UPDATE_DEFAULT)              \
  decl(ext_D           , "d"           , ('D' - 'A'), true , NO_UPDATE_DEFAULT)              \
  decl(ext_C           , "c"           , ('C' - 'A'), true , UPDATE_DEFAULT(UseRVC))         \
  decl(ext_Q           , "q"           , ('Q' - 'A'), true , NO_UPDATE_DEFAULT)              \
  decl(ext_H           , "h"           , ('H' - 'A'), true , NO_UPDATE_DEFAULT)              \
  decl(ext_V           , "v"           , ('V' - 'A'), true , UPDATE_DEFAULT(UseRVV))         \
  decl(ext_Zicbom      , "Zicbom"      ,           0, true , UPDATE_DEFAULT(UseZicbom))      \
  decl(ext_Zicboz      , "Zicboz"      ,           0, true , UPDATE_DEFAULT(UseZicboz))      \
  decl(ext_Zicbop      , "Zicbop"      ,           0, true , UPDATE_DEFAULT(UseZicbop))      \
  decl(ext_Zba         , "Zba"         ,           0, true , UPDATE_DEFAULT(UseZba))         \
  decl(ext_Zbb         , "Zbb"         ,           0, true , UPDATE_DEFAULT(UseZbb))         \
  decl(ext_Zbc         , "Zbc"         ,           0, true , NO_UPDATE_DEFAULT)              \
  decl(ext_Zbs         , "Zbs"         ,           0, true , UPDATE_DEFAULT(UseZbs))         \
  decl(ext_Zicsr       , "Zicsr"       ,           0, true , NO_UPDATE_DEFAULT)              \
  decl(ext_Zifencei    , "Zifencei"    ,           0, true , NO_UPDATE_DEFAULT)              \
  decl(ext_Zic64b      , "Zic64b"      ,           0, true , UPDATE_DEFAULT(UseZic64b))      \
  decl(ext_Zihintpause , "Zihintpause" ,           0, true , UPDATE_DEFAULT(UseZihintpause)) \
  decl(mvendorid       , "VendorId"    ,           0, false, NO_UPDATE_DEFAULT)              \
  decl(marchid         , "ArchId"      ,           0, false, NO_UPDATE_DEFAULT)              \
  decl(mimpid          , "ImpId"       ,           0, false, NO_UPDATE_DEFAULT)              \
  decl(unaligned_access, "Unaligned"   ,           0, false, NO_UPDATE_DEFAULT)              \
  decl(satp_mode       , "SATP"        ,           0, false, NO_UPDATE_DEFAULT)              \

  #define DECLARE_RV_FEATURE(NAME, PRETTY, BIT, FSTRING, FLAGF)   \
  struct NAME##RVFeatureValue : public RVFeatureValue {                \
    NAME##RVFeatureValue(const char* pretty, int bit, bool fstring) :  \
      RVFeatureValue(pretty, bit, fstring) {}                          \
    FLAGF;                                                             \
  };                                                                   \
  static NAME##RVFeatureValue NAME;                                    \

  RV_FEATURE_FLAGS(DECLARE_RV_FEATURE)
  #undef DECLARE_RV_FEATURE

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
  static const char* os_uarch_additional_features();
  static void vendor_features();
  // Vendors specific features
  static void rivos_features();

  // Determine vector length iff ext_C/UseRVC
  static uint32_t cpu_vector_length();
  static uint32_t _initial_vector_length;

#ifdef COMPILER2
  static void c2_initialize();
#endif // COMPILER2

 public:
  // Initialization
  static void initialize();
  static void initialize_cpu_information();

  constexpr static bool supports_stack_watermark_barrier() { return true; }

  static bool supports_on_spin_wait() { return UseZihintpause; }
};

#endif // CPU_RISCV_VM_VERSION_RISCV_HPP
