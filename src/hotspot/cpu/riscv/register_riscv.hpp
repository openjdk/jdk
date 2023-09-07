/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2023, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef CPU_RISCV_REGISTER_RISCV_HPP
#define CPU_RISCV_REGISTER_RISCV_HPP

#include "asm/register.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/powerOfTwo.hpp"

#define CSR_FFLAGS   0x001        // Floating-Point Accrued Exceptions.
#define CSR_FRM      0x002        // Floating-Point Dynamic Rounding Mode.
#define CSR_FCSR     0x003        // Floating-Point Control and Status Register (frm + fflags).
#define CSR_VSTART   0x008        // Vector start position
#define CSR_VXSAT    0x009        // Fixed-Point Saturate Flag
#define CSR_VXRM     0x00A        // Fixed-Point Rounding Mode
#define CSR_VCSR     0x00F        // Vector control and status register
#define CSR_VL       0xC20        // Vector length
#define CSR_VTYPE    0xC21        // Vector data type register
#define CSR_VLENB    0xC22        // VLEN/8 (vector register length in bytes)
#define CSR_CYCLE    0xc00        // Cycle counter for RDCYCLE instruction.
#define CSR_TIME     0xc01        // Timer for RDTIME instruction.
#define CSR_INSTRET  0xc02        // Instructions-retired counter for RDINSTRET instruction.

class VMRegImpl;
typedef VMRegImpl* VMReg;

class Register {
 private:
  int _encoding;

  constexpr explicit Register(int encoding) : _encoding(encoding) {}

 public:
  enum {
    number_of_registers      = 32,
    max_slots_per_register   = 2,

    // integer registers x8 - x15 and floating-point registers f8 - f15 are allocatable
    // for compressed instructions. See Table 17.2 in spec.
    compressed_register_base = 8,
    compressed_register_top  = 15,
  };

  class RegisterImpl: public AbstractRegisterImpl {
    friend class Register;

    static constexpr const RegisterImpl* first();

   public:
    // accessors
    constexpr int raw_encoding() const { return checked_cast<int>(this - first()); }
    constexpr int     encoding() const { assert(is_valid(), "invalid register"); return raw_encoding(); }
    constexpr bool    is_valid() const { return 0 <= raw_encoding() && raw_encoding() < number_of_registers; }

    // for rvc
    int compressed_raw_encoding() const {
      return raw_encoding() - compressed_register_base;
    }

    int compressed_encoding() const {
      assert(is_compressed_valid(), "invalid compressed register");
      return encoding() - compressed_register_base;
    }

    bool is_compressed_valid() const {
      return raw_encoding() >= compressed_register_base &&
             raw_encoding() <= compressed_register_top;
    }

    // derived registers, offsets, and addresses
    inline Register successor() const;

    VMReg as_VMReg() const;

    const char* name() const;
  };

  inline friend constexpr Register as_Register(int encoding);

  constexpr Register() : _encoding(-1) {} // noreg

  int operator==(const Register r) const { return _encoding == r._encoding; }
  int operator!=(const Register r) const { return _encoding != r._encoding; }

  constexpr const RegisterImpl* operator->() const { return RegisterImpl::first() + _encoding; }
};

extern Register::RegisterImpl all_RegisterImpls[Register::number_of_registers + 1] INTERNAL_VISIBILITY;

inline constexpr const Register::RegisterImpl* Register::RegisterImpl::first() {
  return all_RegisterImpls + 1;
}

constexpr Register noreg = Register();

inline constexpr Register as_Register(int encoding) {
  if (0 <= encoding && encoding < Register::number_of_registers) {
    return Register(encoding);
  }
  return noreg;
}

inline Register Register::RegisterImpl::successor() const {
  assert(is_valid(), "sanity");
  return as_Register(encoding() + 1);
}

// The integer registers of RISCV architecture
constexpr Register x0   = as_Register( 0);
constexpr Register x1   = as_Register( 1);
constexpr Register x2   = as_Register( 2);
constexpr Register x3   = as_Register( 3);
constexpr Register x4   = as_Register( 4);
constexpr Register x5   = as_Register( 5);
constexpr Register x6   = as_Register( 6);
constexpr Register x7   = as_Register( 7);
constexpr Register x8   = as_Register( 8);
constexpr Register x9   = as_Register( 9);
constexpr Register x10  = as_Register(10);
constexpr Register x11  = as_Register(11);
constexpr Register x12  = as_Register(12);
constexpr Register x13  = as_Register(13);
constexpr Register x14  = as_Register(14);
constexpr Register x15  = as_Register(15);
constexpr Register x16  = as_Register(16);
constexpr Register x17  = as_Register(17);
constexpr Register x18  = as_Register(18);
constexpr Register x19  = as_Register(19);
constexpr Register x20  = as_Register(20);
constexpr Register x21  = as_Register(21);
constexpr Register x22  = as_Register(22);
constexpr Register x23  = as_Register(23);
constexpr Register x24  = as_Register(24);
constexpr Register x25  = as_Register(25);
constexpr Register x26  = as_Register(26);
constexpr Register x27  = as_Register(27);
constexpr Register x28  = as_Register(28);
constexpr Register x29  = as_Register(29);
constexpr Register x30  = as_Register(30);
constexpr Register x31  = as_Register(31);

// The implementation of floating point registers for the architecture
class FloatRegister {
 private:
  int _encoding;

  constexpr explicit FloatRegister(int encoding) : _encoding(encoding) {}

 public:
  inline friend constexpr FloatRegister as_FloatRegister(int encoding);

  enum {
    number_of_registers     = 32,
    max_slots_per_register  = 2,

    // float registers in the range of [f8~f15] correspond to RVC. Please see Table 16.2 in spec.
    compressed_register_base = 8,
    compressed_register_top  = 15,
  };

  class FloatRegisterImpl: public AbstractRegisterImpl {
    friend class FloatRegister;

    static constexpr const FloatRegisterImpl* first();

   public:
    // accessors
    constexpr int raw_encoding() const { return checked_cast<int>(this - first()); }
    constexpr int     encoding() const { assert(is_valid(), "invalid register"); return raw_encoding(); }
    constexpr bool    is_valid() const { return 0 <= raw_encoding() && raw_encoding() < number_of_registers; }

    // for rvc
    int compressed_raw_encoding() const {
      return raw_encoding() - compressed_register_base;
    }

    int compressed_encoding() const {
      assert(is_compressed_valid(), "invalid compressed register");
      return encoding() - compressed_register_base;
    }

    bool is_compressed_valid() const {
      return raw_encoding() >= compressed_register_base &&
             raw_encoding() <= compressed_register_top;
    }

    // derived registers, offsets, and addresses
    inline FloatRegister successor() const;

    VMReg as_VMReg() const;

    const char* name() const;
  };

  constexpr FloatRegister() : _encoding(-1) {} // fnoreg

  int operator==(const FloatRegister r) const { return _encoding == r._encoding; }
  int operator!=(const FloatRegister r) const { return _encoding != r._encoding; }

  constexpr const FloatRegisterImpl* operator->() const { return FloatRegisterImpl::first() + _encoding; }
};

extern FloatRegister::FloatRegisterImpl all_FloatRegisterImpls[FloatRegister::number_of_registers + 1] INTERNAL_VISIBILITY;

inline constexpr const FloatRegister::FloatRegisterImpl* FloatRegister::FloatRegisterImpl::first() {
  return all_FloatRegisterImpls + 1;
}

constexpr FloatRegister fnoreg = FloatRegister();

inline constexpr FloatRegister as_FloatRegister(int encoding) {
  if (0 <= encoding && encoding < FloatRegister::number_of_registers) {
    return FloatRegister(encoding);
  }
  return fnoreg;
}

inline FloatRegister FloatRegister::FloatRegisterImpl::successor() const {
  assert(is_valid(), "sanity");
  return as_FloatRegister(encoding() + 1);
}

// The float registers of the RISCV architecture
constexpr FloatRegister f0     = as_FloatRegister( 0);
constexpr FloatRegister f1     = as_FloatRegister( 1);
constexpr FloatRegister f2     = as_FloatRegister( 2);
constexpr FloatRegister f3     = as_FloatRegister( 3);
constexpr FloatRegister f4     = as_FloatRegister( 4);
constexpr FloatRegister f5     = as_FloatRegister( 5);
constexpr FloatRegister f6     = as_FloatRegister( 6);
constexpr FloatRegister f7     = as_FloatRegister( 7);
constexpr FloatRegister f8     = as_FloatRegister( 8);
constexpr FloatRegister f9     = as_FloatRegister( 9);
constexpr FloatRegister f10    = as_FloatRegister(10);
constexpr FloatRegister f11    = as_FloatRegister(11);
constexpr FloatRegister f12    = as_FloatRegister(12);
constexpr FloatRegister f13    = as_FloatRegister(13);
constexpr FloatRegister f14    = as_FloatRegister(14);
constexpr FloatRegister f15    = as_FloatRegister(15);
constexpr FloatRegister f16    = as_FloatRegister(16);
constexpr FloatRegister f17    = as_FloatRegister(17);
constexpr FloatRegister f18    = as_FloatRegister(18);
constexpr FloatRegister f19    = as_FloatRegister(19);
constexpr FloatRegister f20    = as_FloatRegister(20);
constexpr FloatRegister f21    = as_FloatRegister(21);
constexpr FloatRegister f22    = as_FloatRegister(22);
constexpr FloatRegister f23    = as_FloatRegister(23);
constexpr FloatRegister f24    = as_FloatRegister(24);
constexpr FloatRegister f25    = as_FloatRegister(25);
constexpr FloatRegister f26    = as_FloatRegister(26);
constexpr FloatRegister f27    = as_FloatRegister(27);
constexpr FloatRegister f28    = as_FloatRegister(28);
constexpr FloatRegister f29    = as_FloatRegister(29);
constexpr FloatRegister f30    = as_FloatRegister(30);
constexpr FloatRegister f31    = as_FloatRegister(31);

// The implementation of vector registers for RVV
class VectorRegister {
  int _encoding;

  constexpr explicit VectorRegister(int encoding) : _encoding(encoding) {}

 public:
  inline friend constexpr VectorRegister as_VectorRegister(int encoding);

  enum {
    number_of_registers    = 32,
    max_slots_per_register = 4
  };

  class VectorRegisterImpl: public AbstractRegisterImpl {
    friend class VectorRegister;

    static constexpr const VectorRegisterImpl* first();

   public:
    // accessors
    constexpr int raw_encoding() const { return checked_cast<int>(this - first()); }
    constexpr int     encoding() const { assert(is_valid(), "invalid register"); return raw_encoding(); }
    constexpr bool    is_valid() const { return 0 <= raw_encoding() && raw_encoding() < number_of_registers; }

    // derived registers, offsets, and addresses
    inline VectorRegister successor() const;

    VMReg as_VMReg() const;

    const char* name() const;
  };

  constexpr VectorRegister() : _encoding(-1) {} // vnoreg

  int operator==(const VectorRegister r) const { return _encoding == r._encoding; }
  int operator!=(const VectorRegister r) const { return _encoding != r._encoding; }

  constexpr const VectorRegisterImpl* operator->() const { return VectorRegisterImpl::first() + _encoding; }
};

extern VectorRegister::VectorRegisterImpl all_VectorRegisterImpls[VectorRegister::number_of_registers + 1] INTERNAL_VISIBILITY;

inline constexpr const VectorRegister::VectorRegisterImpl* VectorRegister::VectorRegisterImpl::first() {
  return all_VectorRegisterImpls + 1;
}

constexpr VectorRegister vnoreg = VectorRegister();

inline constexpr VectorRegister as_VectorRegister(int encoding) {
  if (0 <= encoding && encoding < VectorRegister::number_of_registers) {
    return VectorRegister(encoding);
  }
  return vnoreg;
}

inline VectorRegister VectorRegister::VectorRegisterImpl::successor() const {
  assert(is_valid(), "sanity");
  return as_VectorRegister(encoding() + 1);
}

// The vector registers of RVV
constexpr VectorRegister v0     = as_VectorRegister( 0);
constexpr VectorRegister v1     = as_VectorRegister( 1);
constexpr VectorRegister v2     = as_VectorRegister( 2);
constexpr VectorRegister v3     = as_VectorRegister( 3);
constexpr VectorRegister v4     = as_VectorRegister( 4);
constexpr VectorRegister v5     = as_VectorRegister( 5);
constexpr VectorRegister v6     = as_VectorRegister( 6);
constexpr VectorRegister v7     = as_VectorRegister( 7);
constexpr VectorRegister v8     = as_VectorRegister( 8);
constexpr VectorRegister v9     = as_VectorRegister( 9);
constexpr VectorRegister v10    = as_VectorRegister(10);
constexpr VectorRegister v11    = as_VectorRegister(11);
constexpr VectorRegister v12    = as_VectorRegister(12);
constexpr VectorRegister v13    = as_VectorRegister(13);
constexpr VectorRegister v14    = as_VectorRegister(14);
constexpr VectorRegister v15    = as_VectorRegister(15);
constexpr VectorRegister v16    = as_VectorRegister(16);
constexpr VectorRegister v17    = as_VectorRegister(17);
constexpr VectorRegister v18    = as_VectorRegister(18);
constexpr VectorRegister v19    = as_VectorRegister(19);
constexpr VectorRegister v20    = as_VectorRegister(20);
constexpr VectorRegister v21    = as_VectorRegister(21);
constexpr VectorRegister v22    = as_VectorRegister(22);
constexpr VectorRegister v23    = as_VectorRegister(23);
constexpr VectorRegister v24    = as_VectorRegister(24);
constexpr VectorRegister v25    = as_VectorRegister(25);
constexpr VectorRegister v26    = as_VectorRegister(26);
constexpr VectorRegister v27    = as_VectorRegister(27);
constexpr VectorRegister v28    = as_VectorRegister(28);
constexpr VectorRegister v29    = as_VectorRegister(29);
constexpr VectorRegister v30    = as_VectorRegister(30);
constexpr VectorRegister v31    = as_VectorRegister(31);

// Need to know the total number of registers of all sorts for SharedInfo.
// Define a class that exports it.
class ConcreteRegisterImpl : public AbstractRegisterImpl {
 public:
  enum {
    max_gpr = Register::number_of_registers * Register::max_slots_per_register,
    max_fpr = max_gpr + FloatRegister::number_of_registers * FloatRegister::max_slots_per_register,
    max_vpr = max_fpr + VectorRegister::number_of_registers * VectorRegister::max_slots_per_register,

    // A big enough number for C2: all the registers plus flags
    // This number must be large enough to cover REG_COUNT (defined by c2) registers.
    // There is no requirement that any ordering here matches any ordering c2 gives
    // it's optoregs.
    number_of_registers = max_vpr // gpr/fpr/vpr
  };
};

typedef AbstractRegSet<Register> RegSet;
typedef AbstractRegSet<FloatRegister> FloatRegSet;
typedef AbstractRegSet<VectorRegister> VectorRegSet;


template <>
inline Register AbstractRegSet<Register>::first() {
  uint32_t first = _bitset & -_bitset;
  return first ? as_Register(exact_log2(first)) : noreg;
}

template <>
inline FloatRegister AbstractRegSet<FloatRegister>::first() {
  uint32_t first = _bitset & -_bitset;
  return first ? as_FloatRegister(exact_log2(first)) : fnoreg;
}

template<>
inline VectorRegister AbstractRegSet<VectorRegister>::first() {
  uint32_t first = _bitset & -_bitset;
  return first ? as_VectorRegister(exact_log2(first)) : vnoreg;
}

#endif // CPU_RISCV_REGISTER_RISCV_HPP
