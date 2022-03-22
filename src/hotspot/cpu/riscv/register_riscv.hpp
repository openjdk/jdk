/*
 * Copyright (c) 2000, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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
#define CSR_INSTERT  0xc02        // Instructions-retired counter for RDINSTRET instruction.

class VMRegImpl;
typedef VMRegImpl* VMReg;

// Use Register as shortcut
class RegisterImpl;
typedef const RegisterImpl* Register;

inline constexpr Register as_Register(int encoding);

class RegisterImpl: public AbstractRegisterImpl {
  static constexpr Register first();

 public:
  enum {
    number_of_registers      = 32,
    max_slots_per_register   = 2,

    // integer registers x8 - x15 and floating-point registers f8 - f15 are allocatable
    // for compressed instructions. See Table 17.2 in spec.
    compressed_register_base = 8,
    compressed_register_top  = 15,
  };

  // derived registers, offsets, and addresses
  const Register successor() const { return this + 1; }

  // construction
  inline friend constexpr Register as_Register(int encoding);

  VMReg as_VMReg() const;

  // accessors
  int encoding() const            { assert(is_valid(), "invalid register"); return encoding_nocheck(); }
  int encoding_nocheck() const    { return this - first(); }
  bool is_valid() const           { return (unsigned)encoding_nocheck() < number_of_registers; }
  const char* name() const;

  // for rvc
  int compressed_encoding() const {
    assert(is_compressed_valid(), "invalid compressed register");
    return encoding() - compressed_register_base;
  }

  int compressed_encoding_nocheck() const {
    return encoding_nocheck() - compressed_register_base;
  }

  bool is_compressed_valid() const {
    return encoding_nocheck() >= compressed_register_base &&
           encoding_nocheck() <= compressed_register_top;
  }
};

REGISTER_IMPL_DECLARATION(Register, RegisterImpl, RegisterImpl::number_of_registers);

// The integer registers of the RISCV architecture

CONSTANT_REGISTER_DECLARATION(Register, noreg, (-1));

CONSTANT_REGISTER_DECLARATION(Register, x0,    (0));
CONSTANT_REGISTER_DECLARATION(Register, x1,    (1));
CONSTANT_REGISTER_DECLARATION(Register, x2,    (2));
CONSTANT_REGISTER_DECLARATION(Register, x3,    (3));
CONSTANT_REGISTER_DECLARATION(Register, x4,    (4));
CONSTANT_REGISTER_DECLARATION(Register, x5,    (5));
CONSTANT_REGISTER_DECLARATION(Register, x6,    (6));
CONSTANT_REGISTER_DECLARATION(Register, x7,    (7));
CONSTANT_REGISTER_DECLARATION(Register, x8,    (8));
CONSTANT_REGISTER_DECLARATION(Register, x9,    (9));
CONSTANT_REGISTER_DECLARATION(Register, x10,  (10));
CONSTANT_REGISTER_DECLARATION(Register, x11,  (11));
CONSTANT_REGISTER_DECLARATION(Register, x12,  (12));
CONSTANT_REGISTER_DECLARATION(Register, x13,  (13));
CONSTANT_REGISTER_DECLARATION(Register, x14,  (14));
CONSTANT_REGISTER_DECLARATION(Register, x15,  (15));
CONSTANT_REGISTER_DECLARATION(Register, x16,  (16));
CONSTANT_REGISTER_DECLARATION(Register, x17,  (17));
CONSTANT_REGISTER_DECLARATION(Register, x18,  (18));
CONSTANT_REGISTER_DECLARATION(Register, x19,  (19));
CONSTANT_REGISTER_DECLARATION(Register, x20,  (20));
CONSTANT_REGISTER_DECLARATION(Register, x21,  (21));
CONSTANT_REGISTER_DECLARATION(Register, x22,  (22));
CONSTANT_REGISTER_DECLARATION(Register, x23,  (23));
CONSTANT_REGISTER_DECLARATION(Register, x24,  (24));
CONSTANT_REGISTER_DECLARATION(Register, x25,  (25));
CONSTANT_REGISTER_DECLARATION(Register, x26,  (26));
CONSTANT_REGISTER_DECLARATION(Register, x27,  (27));
CONSTANT_REGISTER_DECLARATION(Register, x28,  (28));
CONSTANT_REGISTER_DECLARATION(Register, x29,  (29));
CONSTANT_REGISTER_DECLARATION(Register, x30,  (30));
CONSTANT_REGISTER_DECLARATION(Register, x31,  (31));

// Use FloatRegister as shortcut
class FloatRegisterImpl;
typedef const FloatRegisterImpl* FloatRegister;

inline constexpr FloatRegister as_FloatRegister(int encoding);

// The implementation of floating point registers for the architecture
class FloatRegisterImpl: public AbstractRegisterImpl {
  static constexpr FloatRegister first();

 public:
  enum {
    number_of_registers     = 32,
    max_slots_per_register  = 2,

    // float registers in the range of [f8~f15] correspond to RVC. Please see Table 16.2 in spec.
    compressed_register_base = 8,
    compressed_register_top  = 15,
  };

  // construction
  inline friend constexpr FloatRegister as_FloatRegister(int encoding);

  VMReg as_VMReg() const;

  // derived registers, offsets, and addresses
  FloatRegister successor() const {
    return as_FloatRegister((encoding() + 1) % (unsigned)number_of_registers);
  }

  // accessors
  int encoding() const            { assert(is_valid(), "invalid register"); return encoding_nocheck(); }
  int encoding_nocheck() const    { return this - first(); }
  int is_valid() const            { return (unsigned)encoding_nocheck() < number_of_registers; }
  const char* name() const;

  // for rvc
  int compressed_encoding() const {
    assert(is_compressed_valid(), "invalid compressed register");
    return encoding() - compressed_register_base;
  }

  int compressed_encoding_nocheck() const {
    return encoding_nocheck() - compressed_register_base;
  }

  bool is_compressed_valid() const {
    return encoding_nocheck() >= compressed_register_base &&
           encoding_nocheck() <= compressed_register_top;
  }
};

REGISTER_IMPL_DECLARATION(FloatRegister, FloatRegisterImpl, FloatRegisterImpl::number_of_registers);

// The float registers of the RISCV architecture

CONSTANT_REGISTER_DECLARATION(FloatRegister, fnoreg , (-1));

CONSTANT_REGISTER_DECLARATION(FloatRegister, f0     , ( 0));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f1     , ( 1));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f2     , ( 2));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f3     , ( 3));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f4     , ( 4));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f5     , ( 5));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f6     , ( 6));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f7     , ( 7));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f8     , ( 8));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f9     , ( 9));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f10    , (10));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f11    , (11));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f12    , (12));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f13    , (13));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f14    , (14));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f15    , (15));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f16    , (16));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f17    , (17));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f18    , (18));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f19    , (19));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f20    , (20));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f21    , (21));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f22    , (22));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f23    , (23));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f24    , (24));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f25    , (25));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f26    , (26));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f27    , (27));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f28    , (28));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f29    , (29));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f30    , (30));
CONSTANT_REGISTER_DECLARATION(FloatRegister, f31    , (31));

// Use VectorRegister as shortcut
class VectorRegisterImpl;
typedef const VectorRegisterImpl* VectorRegister;

inline constexpr VectorRegister as_VectorRegister(int encoding);

// The implementation of vector registers for RVV
class VectorRegisterImpl: public AbstractRegisterImpl {
  static constexpr VectorRegister first();

 public:
  enum {
    number_of_registers    = 32,
    max_slots_per_register = 4
  };

  // construction
  inline friend constexpr VectorRegister as_VectorRegister(int encoding);

  VMReg as_VMReg() const;

  // derived registers, offsets, and addresses
  VectorRegister successor() const { return this + 1; }

  // accessors
  int encoding() const            { assert(is_valid(), "invalid register"); return encoding_nocheck(); }
  int encoding_nocheck() const    { return this - first(); }
  bool is_valid() const           { return (unsigned)encoding_nocheck() < number_of_registers; }
  const char* name() const;

};

REGISTER_IMPL_DECLARATION(VectorRegister, VectorRegisterImpl, VectorRegisterImpl::number_of_registers);

// The vector registers of RVV
CONSTANT_REGISTER_DECLARATION(VectorRegister, vnoreg , (-1));

CONSTANT_REGISTER_DECLARATION(VectorRegister, v0     , ( 0));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v1     , ( 1));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v2     , ( 2));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v3     , ( 3));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v4     , ( 4));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v5     , ( 5));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v6     , ( 6));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v7     , ( 7));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v8     , ( 8));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v9     , ( 9));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v10    , (10));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v11    , (11));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v12    , (12));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v13    , (13));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v14    , (14));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v15    , (15));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v16    , (16));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v17    , (17));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v18    , (18));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v19    , (19));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v20    , (20));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v21    , (21));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v22    , (22));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v23    , (23));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v24    , (24));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v25    , (25));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v26    , (26));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v27    , (27));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v28    , (28));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v29    , (29));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v30    , (30));
CONSTANT_REGISTER_DECLARATION(VectorRegister, v31    , (31));


// Need to know the total number of registers of all sorts for SharedInfo.
// Define a class that exports it.
class ConcreteRegisterImpl : public AbstractRegisterImpl {
 public:
  enum {
  // A big enough number for C2: all the registers plus flags
  // This number must be large enough to cover REG_COUNT (defined by c2) registers.
  // There is no requirement that any ordering here matches any ordering c2 gives
  // it's optoregs.

    number_of_registers = (RegisterImpl::max_slots_per_register * RegisterImpl::number_of_registers +
                           FloatRegisterImpl::max_slots_per_register * FloatRegisterImpl::number_of_registers +
                           VectorRegisterImpl::max_slots_per_register * VectorRegisterImpl::number_of_registers)
  };

  // added to make it compile
  static const int max_gpr;
  static const int max_fpr;
  static const int max_vpr;
};

typedef AbstractRegSet<Register> RegSet;
typedef AbstractRegSet<FloatRegister> FloatRegSet;
typedef AbstractRegSet<VectorRegister> VectorRegSet;

#endif // CPU_RISCV_REGISTER_RISCV_HPP
