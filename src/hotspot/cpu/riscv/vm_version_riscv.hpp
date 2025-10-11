/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

  class RVExtFeatures;

  class RVFeatureValue {
    const char* const _pretty;
    const bool        _feature_string;
    const uint64_t    _linux_feature_bit;
    int64_t           _value;
   public:
    RVFeatureValue(const char* pretty, int linux_bit_num, bool fstring) :
      _pretty(pretty), _feature_string(fstring), _linux_feature_bit(nth_bit(linux_bit_num)),
      _value(-1) {
    }
    virtual void enable_feature(int64_t value = 0) {
      _value = value;
    }
    virtual void disable_feature() {
      _value = -1;
    }
    const char* pretty()         { return _pretty; }
    uint64_t feature_bit()       { return _linux_feature_bit; }
    bool feature_string()        { return _feature_string; }
    int64_t value()              { return _value; }
    virtual bool enabled() = 0;
    virtual void update_flag() = 0;

   protected:
    bool deps_all_enabled(RVFeatureValue* dep0, ...) {
      assert(dep0 != nullptr, "must not");

      va_list va;
      va_start(va, dep0);
      RVFeatureValue* next = dep0;
      bool enabled = true;
      while (next != nullptr && enabled) {
        enabled = next->enabled();
        next = va_arg(va, RVFeatureValue*);
      }
      va_end(va);
      return enabled;
    }

    void deps_string(stringStream& ss, RVFeatureValue* dep0, ...) {
      assert(dep0 != nullptr, "must not");
      ss.print("%s (%s)", dep0->pretty(), dep0->enabled() ? "enabled" : "disabled");

      va_list va;
      va_start(va, dep0);
      RVFeatureValue* next = nullptr;
      while ((next = va_arg(va, RVFeatureValue*)) != nullptr) {
        ss.print(", %s (%s)", next->pretty(), next->enabled() ? "enabled" : "disabled");
      }
      va_end(va);
    }
  };

  #define UPDATE_DEFAULT(flag)           \
  void update_flag() {                   \
    assert(enabled(), "Must be.");       \
    if (FLAG_IS_DEFAULT(flag)) {         \
      FLAG_SET_DEFAULT(flag, true);      \
    } else {                             \
      /* Sync CPU features with flags */ \
      if (!flag) {                       \
        disable_feature();               \
      }                                  \
    }                                    \
  }                                      \

  #define UPDATE_DEFAULT_DEP(flag, dep0, ...)                                                               \
  void update_flag() {                                                                                      \
      assert(enabled(), "Must be.");                                                                        \
      if (FLAG_IS_DEFAULT(flag)) {                                                                          \
        if (this->deps_all_enabled(dep0, ##__VA_ARGS__)) {                                                  \
          FLAG_SET_DEFAULT(flag, true);                                                                     \
        } else {                                                                                            \
          FLAG_SET_DEFAULT(flag, false);                                                                    \
          stringStream ss;                                                                                  \
          deps_string(ss, dep0, ##__VA_ARGS__);                                                             \
          warning("Cannot enable " #flag ", it's missing dependent extension(s) %s", ss.as_string(true));   \
          /* Sync CPU features with flags */                                                                \
          disable_feature();                                                                                \
        }                                                                                                   \
      } else {                                                                                              \
        /* Sync CPU features with flags */                                                                  \
        if (!flag) {                                                                                        \
          disable_feature();                                                                                \
        } else if (!deps_all_enabled(dep0, ##__VA_ARGS__)) {                                                \
          FLAG_SET_DEFAULT(flag, false);                                                                    \
          stringStream ss;                                                                                  \
          deps_string(ss, dep0, ##__VA_ARGS__);                                                             \
          warning("Cannot enable " #flag ", it's missing dependent extension(s) %s", ss.as_string(true));   \
          /* Sync CPU features with flags */                                                                \
          disable_feature();                                                                                \
        }                                                                                                   \
      }                                                                                                     \
  }                                                                                                         \

  #define NO_UPDATE_DEFAULT                \
  void update_flag() {}                    \


  class RVExtFeatureValue : public RVFeatureValue {
    const uint32_t _cpu_feature_index;
   public:
    RVExtFeatureValue(const char* pretty, int linux_bit_num, uint32_t cpu_feature_index, bool fstring) :
      RVFeatureValue(pretty, linux_bit_num, fstring),
      _cpu_feature_index(cpu_feature_index) {
    }
    bool enabled() {
      return RVExtFeatures::current()->support_feature(_cpu_feature_index);
    }
    void enable_feature(int64_t value = 0) {
      RVFeatureValue::enable_feature(value);
      RVExtFeatures::current()->set_feature(_cpu_feature_index);
    }
    void disable_feature() {
      RVFeatureValue::disable_feature();
      RVExtFeatures::current()->clear_feature(_cpu_feature_index);
    }
  };

  class RVNonExtFeatureValue : public RVFeatureValue {
    bool _enabled;
   public:
    RVNonExtFeatureValue(const char* pretty, int linux_bit_num, bool fstring) :
      RVFeatureValue(pretty, linux_bit_num, fstring),
      _enabled(false) {
    }
    bool enabled()               { return _enabled; }
    void enable_feature(int64_t value = 0) {
      RVFeatureValue::enable_feature(value);
      _enabled = true;
    }
    void disable_feature() {
      RVFeatureValue::disable_feature();
      _enabled = false;
    }
  };

 public:

  #define RV_NO_FLAG_BIT (BitsPerWord+1) // nth_bit will return 0 on values larger than BitsPerWord

  // Note: the order matters, depender should be after their dependee. E.g. ext_V before ext_Zvbb.
  //
  // Fields description in `decl`:
  //    declaration name, extension name, bit value from linux, feature string?, mapped flag)
  #define RV_EXT_FEATURE_FLAGS(decl)                                                                   \
  /* A Atomic Instructions */                                                                          \
  decl(a           ,     ('A' - 'A'),  true ,  NO_UPDATE_DEFAULT)                                      \
  /* C Compressed Instructions */                                                                      \
  decl(c           ,     ('C' - 'A'),  true ,  UPDATE_DEFAULT(UseRVC))                                 \
  /* D Single-Precision Floating-Point */                                                              \
  decl(d           ,     ('D' - 'A'),  true ,  NO_UPDATE_DEFAULT)                                      \
  /* F Single-Precision Floating-Point */                                                              \
  decl(f           ,     ('F' - 'A'),  true ,  NO_UPDATE_DEFAULT)                                      \
  /* H Hypervisor */                                                                                   \
  decl(h           ,     ('H' - 'A'),  true ,  NO_UPDATE_DEFAULT)                                      \
  /* I RV64I */                                                                                        \
  decl(i           ,     ('I' - 'A'),  true ,  NO_UPDATE_DEFAULT)                                      \
  /* M Integer Multiplication and Division */                                                          \
  decl(m           ,     ('M' - 'A'),  true ,  NO_UPDATE_DEFAULT)                                      \
  /* Q Quad-Precision Floating-Point */                                                                \
  decl(q           ,     ('Q' - 'A'),  true ,  NO_UPDATE_DEFAULT)                                      \
  /* V Vector */                                                                                       \
  decl(v           ,     ('V' - 'A'),  true ,  UPDATE_DEFAULT(UseRVV))                                 \
                                                                                                       \
  /* ----------------------- Other extensions ----------------------- */                               \
                                                                                                       \
  /* Atomic compare-and-swap (CAS) instructions */                                                     \
  decl(Zacas       ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZacas))                               \
  /* Zba Address generation instructions */                                                            \
  decl(Zba         ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZba))                                 \
  /* Zbb Basic bit-manipulation */                                                                     \
  decl(Zbb         ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZbb))                                 \
  /* Zbc Carry-less multiplication */                                                                  \
  decl(Zbc         ,  RV_NO_FLAG_BIT,  true ,  NO_UPDATE_DEFAULT)                                      \
  /* Bitmanip instructions for Cryptography */                                                         \
  decl(Zbkb        ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZbkb))                                \
  /* Zbs Single-bit instructions */                                                                    \
  decl(Zbs         ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZbs))                                 \
  /* Zcb Simple code-size saving instructions */                                                       \
  decl(Zcb         ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZcb))                                 \
  /* Additional Floating-Point instructions */                                                         \
  decl(Zfa         ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZfa))                                 \
  /* Zfh Half-Precision Floating-Point instructions */                                                 \
  decl(Zfh         ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZfh))                                 \
  /* Zfhmin Minimal Half-Precision Floating-Point instructions */                                      \
  decl(Zfhmin      ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZfhmin))                              \
  /* Zicbom Cache Block Management Operations */                                                       \
  decl(Zicbom      ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZicbom))                              \
  /* Zicbop Cache Block Prefetch Operations */                                                         \
  decl(Zicbop      ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZicbop))                              \
  /* Zicboz Cache Block Zero Operations */                                                             \
  decl(Zicboz      ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZicboz))                              \
  /* Base Counters and Timers */                                                                       \
  decl(Zicntr      ,  RV_NO_FLAG_BIT,  true ,  NO_UPDATE_DEFAULT)                                      \
  /* Zicond Conditional operations */                                                                  \
  decl(Zicond      ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZicond))                              \
  /* Zicsr Control and Status Register (CSR) Instructions */                                           \
  decl(Zicsr       ,  RV_NO_FLAG_BIT,  true ,  NO_UPDATE_DEFAULT)                                      \
  /* Zic64b Cache blocks must be 64 bytes in size, naturally aligned in the address space. */          \
  decl(Zic64b      ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZic64b))                              \
  /* Zifencei Instruction-Fetch Fence */                                                               \
  decl(Zifencei    ,  RV_NO_FLAG_BIT,  true ,  NO_UPDATE_DEFAULT)                                      \
  /* Zihintpause Pause instruction HINT */                                                             \
  decl(Zihintpause ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZihintpause))                         \
  /* Total Store Ordering */                                                                           \
  decl(Ztso        ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT(UseZtso))                                \
  /* Vector Basic Bit-manipulation */                                                                  \
  decl(Zvbb        ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT_DEP(UseZvbb, &ext_v, nullptr))           \
  /* Vector Carryless Multiplication */                                                                \
  decl(Zvbc        ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT_DEP(UseZvbc, &ext_v, nullptr))           \
  /* Vector Extension for Half-Precision Floating-Point */                                             \
  decl(Zvfh        ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT_DEP(UseZvfh, &ext_v, &ext_Zfh, nullptr)) \
  /* Shorthand for Zvkned + Zvknhb + Zvkb + Zvkt */                                                    \
  decl(Zvkn        ,  RV_NO_FLAG_BIT,  true ,  UPDATE_DEFAULT_DEP(UseZvkn, &ext_v, nullptr))           \

  #define DECLARE_RV_EXT_FEATURE(PRETTY, LINUX_BIT, FSTRING, FLAGF)                             \
  struct ext_##PRETTY##RVExtFeatureValue : public RVExtFeatureValue {                           \
    ext_##PRETTY##RVExtFeatureValue() :                                                         \
      RVExtFeatureValue(#PRETTY, LINUX_BIT, RVExtFeatures::CPU_##ext_##PRETTY, FSTRING) {}      \
    FLAGF;                                                                                      \
  };                                                                                            \
  static ext_##PRETTY##RVExtFeatureValue ext_##PRETTY;                                          \

  RV_EXT_FEATURE_FLAGS(DECLARE_RV_EXT_FEATURE)
  #undef DECLARE_RV_EXT_FEATURE

  // Non-extension features
  //
  #define RV_NON_EXT_FEATURE_FLAGS(decl)                                                       \
  /* Id for microarch. Mvendorid plus marchid uniquely identify the microarch. */              \
  decl(marchid           ,  RV_NO_FLAG_BIT,  false,  NO_UPDATE_DEFAULT)                        \
  /* A unique encoding of the version of the processor implementation. */                      \
  decl(mimpid            ,  RV_NO_FLAG_BIT,  false,  NO_UPDATE_DEFAULT)                        \
  /* SATP bits (number of virtual addr bits) mbare, sv39, sv48, sv57, sv64 */                  \
  decl(satp_mode         ,  RV_NO_FLAG_BIT,  false,  NO_UPDATE_DEFAULT)                        \
  /* Performance of misaligned scalar accesses (unknown, emulated, slow, fast, unsupported) */ \
  decl(unaligned_scalar  ,  RV_NO_FLAG_BIT,  false,  NO_UPDATE_DEFAULT)                        \
  /* Performance of misaligned vector accesses (unknown, unspported, slow, fast) */            \
  decl(unaligned_vector  ,  RV_NO_FLAG_BIT,  false,  NO_UPDATE_DEFAULT)                        \
  /* Manufactory JEDEC id encoded, ISA vol 2 3.1.2.. */                                        \
  decl(mvendorid         ,  RV_NO_FLAG_BIT,  false,  NO_UPDATE_DEFAULT)                        \
  decl(zicboz_block_size ,  RV_NO_FLAG_BIT,  false,  NO_UPDATE_DEFAULT)                        \

  #define DECLARE_RV_NON_EXT_FEATURE(PRETTY, LINUX_BIT, FSTRING, FLAGF)            \
  struct PRETTY##RVNonExtFeatureValue : public RVNonExtFeatureValue {              \
    PRETTY##RVNonExtFeatureValue() :                                               \
      RVNonExtFeatureValue(#PRETTY, LINUX_BIT, FSTRING) {}                         \
    FLAGF;                                                                         \
  };                                                                               \
  static PRETTY##RVNonExtFeatureValue PRETTY;                                      \

  RV_NON_EXT_FEATURE_FLAGS(DECLARE_RV_NON_EXT_FEATURE)
  #undef DECLARE_RV_NON_EXT_FEATURE

private:
  // Utility for AOT CPU feature store/check.
  class RVExtFeatures : public CHeapObj<mtCode> {
   public:
    enum RVFeatureIndex {
      #define DECLARE_RV_FEATURE_ENUM(PRETTY, LINUX_BIT, FSTRING, FLAGF) CPU_##ext_##PRETTY,

      RV_EXT_FEATURE_FLAGS(DECLARE_RV_FEATURE_ENUM)
      MAX_CPU_FEATURE_INDEX
      #undef DECLARE_RV_FEATURE_ENUM
    };
   private:
    uint64_t _features_bitmap[(MAX_CPU_FEATURE_INDEX / BitsPerLong) + 1];
    STATIC_ASSERT(sizeof(_features_bitmap) * BitsPerByte >= MAX_CPU_FEATURE_INDEX);

    // Number of 8-byte elements in _features_bitmap.
    constexpr static int element_count() {
      return sizeof(_features_bitmap) / sizeof(uint64_t);
    }

    static int element_index(RVFeatureIndex feature) {
      int idx = feature / BitsPerLong;
      assert(idx < element_count(), "Features array index out of bounds");
      return idx;
    }

    static uint64_t feature_bit(RVFeatureIndex feature) {
      return (1ULL << (feature % BitsPerLong));
    }

    static RVFeatureIndex convert(uint32_t index) {
      assert(index < MAX_CPU_FEATURE_INDEX, "must");
      return (RVFeatureIndex)index;
    }

   public:
    static RVExtFeatures* current() {
      return _rv_ext_features;
    }

    RVExtFeatures() {
      for (int i = 0; i < element_count(); i++) {
        _features_bitmap[i] = 0;
      }
    }

    void set_feature(uint32_t feature) {
      RVFeatureIndex f = convert(feature);
      int idx = element_index(f);
      _features_bitmap[idx] |= feature_bit(f);
    }

    void clear_feature(uint32_t feature) {
      RVFeatureIndex f = convert(feature);
      int idx = element_index(f);
      _features_bitmap[idx] &= ~feature_bit(f);
    }

    bool support_feature(uint32_t feature) {
      RVFeatureIndex f = convert(feature);
      int idx = element_index(f);
      return (_features_bitmap[idx] & feature_bit(f)) != 0;
    }
  };

  // enable extensions based on profile, current supported profiles:
  //  RVA20U64
  //  RVA22U64
  //  RVA23U64
  // NOTE: we only enable the mandatory extensions, not optional extension.
  #define RV_ENABLE_EXTENSION(UseExtension)     \
    if (FLAG_IS_DEFAULT(UseExtension)) {        \
      FLAG_SET_DEFAULT(UseExtension, true);     \
    }                                           \

  // https://github.com/riscv/riscv-profiles/blob/main/src/profiles.adoc#rva20-profiles
  #define RV_USE_RVA20U64                            \
    RV_ENABLE_EXTENSION(UseRVC)                      \

  static void useRVA20U64Profile();

  // https://github.com/riscv/riscv-profiles/blob/main/src/profiles.adoc#rva22-profiles
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

  // https://github.com/riscv/riscv-profiles/blob/main/src/rva23-profile.adoc#rva23u64-profile
  #define RV_USE_RVA23U64                           \
    RV_ENABLE_EXTENSION(UseRVC)                     \
    RV_ENABLE_EXTENSION(UseRVV)                     \
    RV_ENABLE_EXTENSION(UseZba)                     \
    RV_ENABLE_EXTENSION(UseZbb)                     \
    RV_ENABLE_EXTENSION(UseZbs)                     \
    RV_ENABLE_EXTENSION(UseZcb)                     \
    RV_ENABLE_EXTENSION(UseZfa)                     \
    RV_ENABLE_EXTENSION(UseZfhmin)                  \
    RV_ENABLE_EXTENSION(UseZic64b)                  \
    RV_ENABLE_EXTENSION(UseZicbom)                  \
    RV_ENABLE_EXTENSION(UseZicbop)                  \
    RV_ENABLE_EXTENSION(UseZicboz)                  \
    RV_ENABLE_EXTENSION(UseZicond)                  \
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
  enum UNALIGNED_SCALAR_ACCESS : int {
    MISALIGNED_SCALAR_UNKNOWN     = 0,
    MISALIGNED_SCALAR_EMULATED    = 1,
    MISALIGNED_SCALAR_SLOW        = 2,
    MISALIGNED_SCALAR_FAST        = 3,
    MISALIGNED_SCALAR_UNSUPPORTED = 4
  };

  enum UNALIGNED_VECTOR_ACCESS : int {
    MISALIGNED_VECTOR_UNKNOWN     = 0,
    MISALIGNED_VECTOR_SLOW        = 2,
    MISALIGNED_VECTOR_FAST        = 3,
    MISALIGNED_VECTOR_UNSUPPORTED = 4
  };

  // Null terminated list
  static RVFeatureValue* _feature_list[];
  static RVExtFeatures* _rv_ext_features;

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
  static bool supports_fencei_barrier() { return ext_Zifencei.enabled(); }

  static bool supports_float16_float_conversion() {
    return UseZfh || UseZfhmin;
  }

  // Check intrinsic support
  static bool is_intrinsic_supported(vmIntrinsicID id);
};

#endif // CPU_RISCV_VM_VERSION_RISCV_HPP
