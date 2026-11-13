/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/sizes.hpp"

class RiscvHwprobe;
class stringStream;

class VM_Version : public Abstract_VM_Version {
  friend RiscvHwprobe;

 public:
  enum class AtomicAccessMode : uint8_t {
    RVWMO,
    RVWMO_ZALASR,
    ZTSO
  };

  enum class AtomicCasMode : uint8_t {
    BASELINE,
    ZACAS,
    ZACAS_ZABHA
  };

 private:

  // JEDEC encoded as ((bank - 1) << 7) | (0x7f & JEDEC)
  enum VendorId {
    RIVOS   = 0x6cf, // JEDEC: 0x4f, Bank: 14
    XUANTIE = 0x5b7, // JEDEC: 0x37, Bank: 12
  };

  enum XuantieArchitectureId : uint64_t {
    C925_MARCHID = 0x80000000091c1600ULL,
    C930_MARCHID = 0x8000000009201600ULL,
    C950_MARCHID = 0x8000000009241600ULL,
  };

  class RVExtFeatureSet;

  class RVFeatureValue {
    const char* const _pretty;
    const bool        _feature_string;
    const uint64_t    _linux_feature_bit;

   public:
    RVFeatureValue(const char* pretty, int linux_bit_num, bool fstring) :
      _pretty(pretty), _feature_string(fstring), _linux_feature_bit(nth_bit<uint64_t>(linux_bit_num)) {
    }
    virtual void enable_feature(int64_t value = 0) = 0;
    virtual void disable_feature() = 0;
    const char* pretty()         { return _pretty; }
    uint64_t feature_bit()       { return _linux_feature_bit; }
    bool feature_string()        { return _feature_string; }
    virtual bool feature_enabled() = 0;
    virtual void log_enabled() = 0;
  };

  class RVExtFeatureValue : public RVFeatureValue {
    const uint32_t _cpu_feature_index;
    const JVMFlagsEnum _flag;
    RVExtFeatureValue* const* const _dependencies;

    bool deps_all_enabled() const;
    void deps_string(stringStream& ss) const;
#ifdef ASSERT
    void verify_deps() const;
#endif // ASSERT

   public:
    RVExtFeatureValue(const char* pretty, int linux_bit_num, uint32_t cpu_feature_index, bool fstring,
                      JVMFlagsEnum flag, RVExtFeatureValue* const* dependencies) :
      RVFeatureValue(pretty, linux_bit_num, fstring),
      _cpu_feature_index(cpu_feature_index), _flag(flag), _dependencies(dependencies) {
    }
    int cpu_feature_index() const {
      // Can be used to check, for example, v is declared before Zvfh in RV_EXT_FEATURE_FLAGS.
      return _cpu_feature_index;
    }
    bool feature_enabled() {
      return RVExtFeatureSet::current()->support_feature(_cpu_feature_index);
    }
    void enable_feature(int64_t value = 0) {
      RVExtFeatureSet::current()->set_feature(_cpu_feature_index);
    }
    void disable_feature() {
      RVExtFeatureSet::current()->clear_feature(_cpu_feature_index);
    }
    void update_flag();
    void log_enabled();
    void log_disabled(const char* reason);
  };

  class RVNonExtFeatureValue : public RVFeatureValue {
    static const int64_t DEFAULT_VALUE = -1;
    int64_t _value;

   public:
    RVNonExtFeatureValue(const char* pretty, int linux_bit_num, bool fstring) :
      RVFeatureValue(pretty, linux_bit_num, fstring),
      _value(DEFAULT_VALUE) {
    }
    bool feature_enabled() { return _value != DEFAULT_VALUE; }
    void enable_feature(int64_t value) {
      assert(value != DEFAULT_VALUE, "Sanity");
      _value = value;
    }
    void disable_feature() { _value = DEFAULT_VALUE; }
    int64_t value() { return _value; }
    void log_enabled();
  };

 public:

  #define RV_NO_FLAG_BIT (BitsPerWord+1) // nth_bit will return 0 on values larger than BitsPerWord

  // Note: the order matters, depender should be after their dependee. E.g. ext_V before ext_Zvbb.
  //
  // Fields description in `decl`:
  //    declaration name, bit value from linux, feature string?, mapped flag, dependencies (null terminated)
  #define RV_EXT_FEATURE_FLAGS(decl)                                                                         \
  /* A Atomic Instructions */                                                                                \
  decl(a           ,     ('A' - 'A'),  true , INVALID_JVMFlagsEnum,            nullptr)                      \
  /* C Compressed Instructions */                                                                            \
  decl(c           ,     ('C' - 'A'),  true , FLAG_MEMBER_ENUM(UseRVC),        nullptr)                      \
  /* D Single-Precision Floating-Point */                                                                    \
  decl(d           ,     ('D' - 'A'),  true , INVALID_JVMFlagsEnum,            nullptr)                      \
  /* F Single-Precision Floating-Point */                                                                    \
  decl(f           ,     ('F' - 'A'),  true , INVALID_JVMFlagsEnum,            nullptr)                      \
  /* H Hypervisor */                                                                                         \
  decl(h           ,     ('H' - 'A'),  true , INVALID_JVMFlagsEnum,            nullptr)                      \
  /* I RV64I */                                                                                              \
  decl(i           ,     ('I' - 'A'),  true , INVALID_JVMFlagsEnum,            nullptr)                      \
  /* M Integer Multiplication and Division */                                                                \
  decl(m           ,     ('M' - 'A'),  true , INVALID_JVMFlagsEnum,            nullptr)                      \
  /* Q Quad-Precision Floating-Point */                                                                      \
  decl(q           ,     ('Q' - 'A'),  true , INVALID_JVMFlagsEnum,            nullptr)                      \
  /* V Vector */                                                                                             \
  decl(v           ,     ('V' - 'A'),  true , FLAG_MEMBER_ENUM(UseRVV),        nullptr)                      \
                                                                                                             \
  /* ----------------------- Other extensions ----------------------- */                                     \
                                                                                                             \
  /* Atomic compare-and-swap (CAS) instructions */                                                           \
  decl(Zacas       ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZacas),      nullptr)                      \
  /* Byte and Halfword Atomic Memory instructions */                                                         \
  decl(Zabha       ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZabha),      nullptr)                      \
  /* Load-acquire and store-release instructions */                                                          \
  decl(Zalasr      ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZalasr),     nullptr)                      \
  /* Wait-on-Reservation-Set instructions */                                                                 \
  decl(Zawrs       ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZawrs),      nullptr)                      \
  /* Zba Address generation instructions */                                                                  \
  decl(Zba         ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZba),        nullptr)                      \
  /* Zbb Basic bit-manipulation */                                                                           \
  decl(Zbb         ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZbb),        nullptr)                      \
  /* Zbc Carry-less multiplication */                                                                        \
  decl(Zbc         ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZbc),        nullptr)                      \
  /* Bitmanip instructions for Cryptography */                                                               \
  decl(Zbkb        ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZbkb),       nullptr)                      \
  /* Zbs Single-bit instructions */                                                                          \
  decl(Zbs         ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZbs),        nullptr)                      \
  /* Zcb Simple code-size saving instructions */                                                             \
  decl(Zcb         ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZcb),        nullptr)                      \
  /* Additional Floating-Point instructions */                                                               \
  decl(Zfa         ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZfa),        nullptr)                      \
  /* Zfh Half-Precision Floating-Point instructions */                                                       \
  decl(Zfh         ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZfh),        nullptr)                      \
  /* Zfhmin Minimal Half-Precision Floating-Point instructions */                                            \
  decl(Zfhmin      ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZfhmin),     nullptr)                      \
  /* Zicbop Cache Block Prefetch Operations */                                                               \
  decl(Zicbop      ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZicbop),     nullptr)                      \
  /* Ziccid Instruction/Data Coherence and Consistency */                                                    \
  decl(Ziccid      ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZiccid),     nullptr)                      \
  /* Base Counters and Timers */                                                                             \
  decl(Zicntr      ,  RV_NO_FLAG_BIT,  true , INVALID_JVMFlagsEnum,            nullptr)                      \
  /* Zicond Conditional operations */                                                                        \
  decl(Zicond      ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZicond),     nullptr)                      \
  /* Zicsr Control and Status Register (CSR) Instructions */                                                 \
  decl(Zicsr       ,  RV_NO_FLAG_BIT,  true , INVALID_JVMFlagsEnum,            nullptr)                      \
  /* Zifencei Instruction-Fetch Fence */                                                                     \
  decl(Zifencei    ,  RV_NO_FLAG_BIT,  true , INVALID_JVMFlagsEnum,            nullptr)                      \
  /* Zihintpause Pause instruction HINT */                                                                   \
  decl(Zihintpause ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZihintpause), nullptr)                     \
  /* Total Store Ordering */                                                                                 \
  decl(Ztso        ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZtso),       nullptr)                      \
  /* Vector Basic Bit-manipulation */                                                                        \
  decl(Zvbb        ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZvbb),       &ext_v, nullptr)              \
  /* Vector Carryless Multiplication */                                                                      \
  decl(Zvbc        ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZvbc),       &ext_v, nullptr)              \
  /* Vector Extension for Half-Precision Floating-Point */                                                   \
  decl(Zvfh        ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZvfh),       &ext_v, &ext_Zfhmin, nullptr) \
  /* Vector Extension for Minimal Half-Precision Floating-Point */                                           \
  decl(Zvfhmin     ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZvfhmin),    &ext_v, nullptr)              \
  /* Shorthand for Zvkned + Zvknhb + Zvkb + Zvkt */                                                          \
  decl(Zvkn        ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZvkn),       &ext_v, nullptr)              \
  /* Zvkg crypto extension for ghash and gcm */                                                              \
  decl(Zvkg        ,  RV_NO_FLAG_BIT,  true , FLAG_MEMBER_ENUM(UseZvkg),       &ext_v, nullptr)              \

  #define DECLARE_RV_EXT_FEATURE(PRETTY, LINUX_BIT, FSTRING, FLAG, ...) \
  static RVExtFeatureValue ext_##PRETTY;

  RV_EXT_FEATURE_FLAGS(DECLARE_RV_EXT_FEATURE)
  #undef DECLARE_RV_EXT_FEATURE

  // Non-extension features
  //
  #define RV_NON_EXT_FEATURE_FLAGS(decl)                                                       \
  /* Id for microarch. Mvendorid plus marchid uniquely identify the microarch. */              \
  decl(marchid           ,  RV_NO_FLAG_BIT,  false)                                            \
  /* A unique encoding of the version of the processor implementation. */                      \
  decl(mimpid            ,  RV_NO_FLAG_BIT,  false)                                            \
  /* Manufactory JEDEC id encoded, ISA vol 2 3.1.2.. */                                        \
  decl(mvendorid         ,  RV_NO_FLAG_BIT,  false)                                            \
  /* SATP bits (number of virtual addr bits) mbare, sv39, sv48, sv57, sv64 */                  \
  decl(satp_mode         ,  RV_NO_FLAG_BIT,  false)                                            \
  /* Performance of misaligned scalar accesses (unknown, emulated, slow, fast, unsupported) */ \
  decl(unaligned_scalar  ,  RV_NO_FLAG_BIT,  false)                                            \
  /* Performance of misaligned vector accesses (unknown, unspported, slow, fast) */            \
  decl(unaligned_vector  ,  RV_NO_FLAG_BIT,  false)                                            \

  #define DECLARE_RV_NON_EXT_FEATURE(PRETTY, LINUX_BIT, FSTRING) \
  static RVNonExtFeatureValue PRETTY;

  RV_NON_EXT_FEATURE_FLAGS(DECLARE_RV_NON_EXT_FEATURE)
  #undef DECLARE_RV_NON_EXT_FEATURE

private:
  // Utility for AOT CPU feature store/check.
  class RVExtFeatureSet : public CHeapObj<mtCode> {
   public:
    enum RVFeatureIndex {
      #define DECLARE_RV_FEATURE_ENUM(PRETTY, LINUX_BIT, FSTRING, FLAG, ...) CPU_##ext_##PRETTY,

      RV_EXT_FEATURE_FLAGS(DECLARE_RV_FEATURE_ENUM)
      MAX_CPU_FEATURE_INDEX
      #undef DECLARE_RV_FEATURE_ENUM
    };
   private:
    uint64_t _features_bitmap[(MAX_CPU_FEATURE_INDEX / BitsPerLong) + 1];
    static_assert(sizeof(_features_bitmap) * BitsPerByte >= MAX_CPU_FEATURE_INDEX);

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
    static RVExtFeatureSet* current() {
      return &_rv_ext_features;
    }

    RVExtFeatureSet() {
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

    bool verify_aot_code_cache_features(RVExtFeatureSet* features_to_test) const {
      for (int i = 0; i < element_count(); i++) {
        if (_features_bitmap[i] != features_to_test->_features_bitmap[i]) {
          return false;
        }
      }
      return true;
    }

    // Is this a super set of features_to_test?
    bool super_set_of(RVExtFeatureSet* features_to_test) {
      for (int i = 0; i < element_count(); i++) {
        if ((_features_bitmap[i] | features_to_test->_features_bitmap[i]) != _features_bitmap[i]) {
          return false;
        }
      }
      return true;
    }

    // Returns features present in this but not in features_to_test
    RVExtFeatureSet features_not_in(RVExtFeatureSet* features_to_test) {
      RVExtFeatureSet result;
      for (int i = 0; i < element_count(); i++) {
        result._features_bitmap[i] = _features_bitmap[i] & (~(features_to_test->_features_bitmap[i]));
      }
      return result;
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
    RV_ENABLE_EXTENSION(UseZicbop)                   \
    RV_ENABLE_EXTENSION(UseZihintpause)              \

  static void useRVA22U64Profile();

  // https://github.com/riscv/riscv-profiles/blob/main/src/rva23-profile.adoc#rva23u64-profile
  #define RV_USE_RVA23U64                           \
    RV_ENABLE_EXTENSION(UseRVC)                     \
    RV_ENABLE_EXTENSION(UseRVV)                     \
    RV_ENABLE_EXTENSION(UseZawrs)                   \
    RV_ENABLE_EXTENSION(UseZba)                     \
    RV_ENABLE_EXTENSION(UseZbb)                     \
    RV_ENABLE_EXTENSION(UseZbs)                     \
    RV_ENABLE_EXTENSION(UseZcb)                     \
    RV_ENABLE_EXTENSION(UseZfa)                     \
    RV_ENABLE_EXTENSION(UseZfhmin)                  \
    RV_ENABLE_EXTENSION(UseZicbop)                  \
    RV_ENABLE_EXTENSION(UseZicond)                  \
    RV_ENABLE_EXTENSION(UseZihintpause)             \
    RV_ENABLE_EXTENSION(UseZvfhmin)                 \
    RV_ENABLE_EXTENSION(UseZvbb)                    \

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

  #define DECLARE_RV_EXT_DEPENDENCIES(PRETTY, LINUX_BIT, FSTRING, FLAG, ...) \
  static RVExtFeatureValue* const _ext_##PRETTY##_dependencies[];

  RV_EXT_FEATURE_FLAGS(DECLARE_RV_EXT_DEPENDENCIES)
  #undef DECLARE_RV_EXT_DEPENDENCIES

  // Null-terminated; the extension prefix must match _ext_feature_list.
  static RVFeatureValue* const _feature_list[];
  // Indexed by RVExtFeatureSet::RVFeatureIndex; not null-terminated.
  static RVExtFeatureValue* const _ext_feature_list[];

  // Mutable runtime feature set, not a snapshot of raw hardware capabilities.
  static RVExtFeatureSet _rv_ext_features;
  // Features removed by AOT cache restrictions, retained for diagnostics.
  static RVExtFeatureSet _rv_ext_features_disabled_by_aot_cache;

  // Enables features in _feature_list
  static void setup_cpu_available_features();

  static void apply_aot_cache_cpu_features();

  // Helper for specific queries
  static void os_aux_features();
  static char* os_uarch_additional_features();
  static void vendor_features();
  // Vendors specific features
  static void rivos_features();
  static void xuantie_features();

  // Determine vector length iff ext_V/UseRVV
  static uint32_t cpu_vector_length();
  static uint32_t _initial_vector_length;

  // Native AtomicAccess dispatches on this instead of reading UseZalasr and
  // UseZtso separately. UseZalasr is set from command-line and extension
  // detection before VM_Version::initialize() runs the toolchain-psABI and
  // UseZtso mutual-exclusion checks, so during startup it may be transiently
  // true even on configurations where those checks would later disable it.
  // AtomicAccess is invoked from os::init_2, vm_init_globals,
  // ObjectSynchronizer::initialize and other subsystems that run before
  // init_globals() calls VM_Version_init(), so dispatching on UseZalasr
  // directly can either SIGILL (CPU lacks the extension) or emit Zalasr
  // sequences that later get paired with the incompatible pre-psABI atomics
  // mapping. This mode is RVWMO by default and is latched to the final flag
  // values after all validation completes.
  static AtomicAccessMode _atomic_access_mode;

  // Keep native CAS on the baseline ISA until CPU feature initialization
  // completes, then latch the final Zacas/Zabha configuration.
  static AtomicCasMode _atomic_cas_mode;

  static void common_initialize();

#ifdef COMPILER2
  static void c2_initialize();
#endif // COMPILER2

 public:
  // Initialization
  static void initialize();
  static void initialize_cpu_information();

  static uint32_t initial_vector_length() { return _initial_vector_length; }

  static AtomicAccessMode atomic_access_mode() { return _atomic_access_mode; }
  static AtomicCasMode atomic_cas_mode() { return _atomic_cas_mode; }

  constexpr static bool supports_stack_watermark_barrier() { return true; }

  constexpr static bool supports_recursive_fast_locking() { return true; }

  constexpr static bool supports_secondary_supers_table() { return true; }

  static bool supports_on_spin_wait() { return UseZihintpause; }

  // RISCV64 supports fast class initialization checks
  static bool supports_fast_class_init_checks() { return true; }
  static bool supports_fencei_barrier() { return ext_Zifencei.feature_enabled(); }

  // Zfhmin only provides half-precision load/store/convert instructions,
  // Zfh additionally provides half-precision arithmetic. Either one is
  // enough to convert between float16 and float.
  static bool supports_float16_float_conversion() {
    return UseZfh || UseZfhmin;
  }

  // Shared code gates the Float.float16ToFloat/floatToFloat16 intrinsics on
  // this, and those only need the conversion instructions. Note that it does
  // not imply half-precision arithmetic is available, which needs UseZfh.
  static bool supports_float16() {
    return supports_float16_float_conversion();
  }

  // Check intrinsic support
  static bool is_intrinsic_supported(vmIntrinsicID id);

  // AOT Code Cache support
  static int  cpu_features_size();
  static void store_cpu_features(void* buf);
  static void get_cpu_features_disabled_by_aot_cache(void* buf);
  static bool verify_aot_code_cache_features(void* features_buffer);
  static void get_cpu_features_name(void* features_buffer, stringStream& ss);
  static void get_missing_features_name(void* features_set1, void* features_set2, stringStream& ss);
  static int cpu_features_alignment() { return alignof(RVExtFeatureSet); }

 private:
  static void print_feature_name(stringStream& ss, RVFeatureValue* feature);
  static void insert_features_names(RVExtFeatureSet* features, stringStream& ss);
};

#endif // CPU_RISCV_VM_VERSION_RISCV_HPP
