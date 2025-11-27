/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016, 2023 SAP SE. All rights reserved.
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

#ifndef CPU_S390_REGISTER_S390_HPP
#define CPU_S390_REGISTER_S390_HPP

#include "asm/register.hpp"
#include "runtime/vm_version.hpp"

#define NOREG_ENCODING -1

// forward declaration
class VMRegImpl;
typedef VMRegImpl* VMReg;


// z/Architecture registers, see "LINUX for zSeries ELF ABI Supplement", IBM March 2001
//
//   r0-r1     General purpose (volatile)
//   r2        Parameter and return value (volatile)
//   r3        TOC pointer (volatile)
//   r3-r5     Parameters (volatile)
//   r6        Parameter (nonvolatile)
//   r7-r11    Locals (nonvolatile)
//   r12       Local, often used as GOT pointer (nonvolatile)
//   r13       Local, often used as toc (nonvolatile)
//   r14       return address (volatile)
//   r15       stack pointer (nonvolatile)
//
//   f0,f2,f4,f6 Parameters (volatile)
//   f1,f3,f5,f7 General purpose (volatile)
//   f8-f15      General purpose (nonvolatile)


//===========================
//===  Integer Registers  ===
//===========================

// The implementation of integer registers for z/Architecture.
class Register {
  int _encoding;
public:
  enum {
    number_of_registers     = 16,
    max_slots_per_register = 2,
    number_of_arg_registers = 5
  };

  constexpr Register(int encoding = NOREG_ENCODING) : _encoding(encoding) {}
  bool operator==(const Register rhs) const { return _encoding == rhs._encoding; }
  bool operator!=(const Register rhs) const { return _encoding != rhs._encoding; }
  const Register* operator->()        const { return this; }

  // general construction
  inline constexpr friend Register as_Register(int encoding);

  // accessors
  const char* name() const;
  inline VMReg as_VMReg() const;
  constexpr int encoding() const { assert(is_valid(), "invalid register"); return _encoding; }

  // derived registers, offsets, and addresses
  Register predecessor() const { return Register((encoding() - 1) & (number_of_registers - 1)); }
  Register successor()   const { return Register((encoding() + 1) & (number_of_registers - 1)); }

  // testers
  constexpr bool is_valid()       const { return (0 <= _encoding && _encoding < number_of_registers); }
  constexpr bool is_even()        const { return (_encoding & 1) == 0; }
  constexpr bool is_volatile()    const { return (0 <= _encoding && _encoding <= 5) || _encoding == 14; }
  constexpr bool is_nonvolatile() const { return is_valid() && !is_volatile(); }
};

inline constexpr Register as_Register(int encoding) {
  assert(encoding == NOREG_ENCODING ||
        (0 <= encoding && encoding < Register::number_of_registers), "bad register encoding");
  return Register(encoding);
}

// The integer registers of the z/Architecture.
constexpr Register noreg = as_Register(NOREG_ENCODING);

constexpr Register  Z_R0 = as_Register( 0);
constexpr Register  Z_R1 = as_Register( 1);
constexpr Register  Z_R2 = as_Register( 2);
constexpr Register  Z_R3 = as_Register( 3);
constexpr Register  Z_R4 = as_Register( 4);
constexpr Register  Z_R5 = as_Register( 5);
constexpr Register  Z_R6 = as_Register( 6);
constexpr Register  Z_R7 = as_Register( 7);
constexpr Register  Z_R8 = as_Register( 8);
constexpr Register  Z_R9 = as_Register( 9);
constexpr Register Z_R10 = as_Register(10);
constexpr Register Z_R11 = as_Register(11);
constexpr Register Z_R12 = as_Register(12);
constexpr Register Z_R13 = as_Register(13);
constexpr Register Z_R14 = as_Register(14);
constexpr Register Z_R15 = as_Register(15);


//=============================
//===  Condition Registers  ===
//=============================

// The implementation of condition register(s) for the z/Architecture.

class ConditionRegister {
  int _encoding;
public:
  enum {
    number_of_registers = 1
  };

  constexpr ConditionRegister(int encoding = NOREG_ENCODING) : _encoding(encoding) {}
  bool operator==(const ConditionRegister rhs) const { return _encoding == rhs._encoding; }
  bool operator!=(const ConditionRegister rhs) const { return _encoding != rhs._encoding; }
  const ConditionRegister* operator->()        const { return this; }

  // accessors
  constexpr int encoding() const { assert(is_valid(), "invalid register"); return _encoding; }
  inline VMReg  as_VMReg() const;

  // testers
  constexpr bool is_valid()       const { return (0 <= _encoding && _encoding < number_of_registers); }
  constexpr bool is_volatile()    const { return true; }
  constexpr bool is_nonvolatile() const { return false;}

  // construction.
  inline constexpr friend ConditionRegister as_ConditionRegister(int encoding);
};

inline constexpr ConditionRegister as_ConditionRegister(int encoding) {
  assert(encoding == NOREG_ENCODING ||
        (encoding >= 0 && encoding < ConditionRegister::number_of_registers), "bad condition register encoding");
  return ConditionRegister(encoding);
}

// The condition register of the z/Architecture.

constexpr ConditionRegister Z_CR = as_ConditionRegister(0);

//=========================
//===  Float Registers  ===
//=========================

// The implementation of float registers for the z/Architecture.
class VectorRegister;
class FloatRegister {
  int _encoding;
public:
  enum {
    number_of_registers     = 16,
    max_slots_per_register = 2,
    number_of_arg_registers = 4
  };

  constexpr FloatRegister(int encoding = NOREG_ENCODING) : _encoding(encoding) {}
  bool operator==(const FloatRegister rhs) const { return _encoding == rhs._encoding; }
  bool operator!=(const FloatRegister rhs) const { return _encoding != rhs._encoding; }
  const FloatRegister* operator->()        const { return this; }

  // construction
  inline constexpr friend FloatRegister as_FloatRegister(int encoding);

  // accessors
  constexpr int encoding()  const { assert(is_valid(), "invalid register"); return _encoding; }
  inline VMReg  as_VMReg()  const;
  FloatRegister successor() const { return FloatRegister((encoding() + 1) & (number_of_registers - 1)); }

  // tester
  constexpr bool is_valid()       const { return 0 <= _encoding && _encoding < number_of_registers; }
  constexpr bool is_volatile()    const { return (0 <= _encoding && _encoding <= 7); }
  constexpr bool is_nonvolatile() const { return (8 <= _encoding && _encoding <= 15); }

  const char* name() const;
  // convert to VR
  VectorRegister to_vr() const;
};

inline constexpr FloatRegister as_FloatRegister(int encoding) {
  assert(encoding == NOREG_ENCODING ||
        (encoding >= 0 && encoding < FloatRegister::number_of_registers), "bad float register encoding");
  return FloatRegister(encoding);
}

// The float registers of z/Architecture.
constexpr FloatRegister fnoreg = as_FloatRegister(NOREG_ENCODING);

constexpr FloatRegister  Z_F0 = as_FloatRegister( 0);
constexpr FloatRegister  Z_F1 = as_FloatRegister( 1);
constexpr FloatRegister  Z_F2 = as_FloatRegister( 2);
constexpr FloatRegister  Z_F3 = as_FloatRegister( 3);
constexpr FloatRegister  Z_F4 = as_FloatRegister( 4);
constexpr FloatRegister  Z_F5 = as_FloatRegister( 5);
constexpr FloatRegister  Z_F6 = as_FloatRegister( 6);
constexpr FloatRegister  Z_F7 = as_FloatRegister( 7);
constexpr FloatRegister  Z_F8 = as_FloatRegister( 8);
constexpr FloatRegister  Z_F9 = as_FloatRegister( 9);
constexpr FloatRegister Z_F10 = as_FloatRegister(10);
constexpr FloatRegister Z_F11 = as_FloatRegister(11);
constexpr FloatRegister Z_F12 = as_FloatRegister(12);
constexpr FloatRegister Z_F13 = as_FloatRegister(13);
constexpr FloatRegister Z_F14 = as_FloatRegister(14);
constexpr FloatRegister Z_F15 = as_FloatRegister(15);

// Single, Double and Quad fp reg classes. These exist to map the ADLC
// encoding for a floating point register, to the FloatRegister number
// desired by the macroAssembler. A FloatRegister is a number between
// 0 and 31 passed around as a pointer. For ADLC, an fp register encoding
// is the actual bit encoding used by the z/Architecture hardware. When ADLC used
// the macroAssembler to generate an instruction that references, e.g., a
// double fp reg, it passed the bit encoding to the macroAssembler via
// as_FloatRegister, which, for double regs > 30, returns an illegal
// register number.
//
// Therefore we provide the following classes for use by ADLC. Their
// sole purpose is to convert from z/Architecture register encodings to FloatRegisters.
// At some future time, we might replace FloatRegister with these classes,
// hence the definitions of as_xxxFloatRegister as class methods rather
// than as external inline routines.

class SingleFloatRegister {
public:
  enum {
    number_of_registers = 32
  };
  const SingleFloatRegister* operator->() const { return this; }

  inline constexpr friend FloatRegister as_SingleFloatRegister(int encoding) {
    assert(encoding < number_of_registers, "bad single float register encoding");
    return as_FloatRegister(encoding);
  }
};

class DoubleFloatRegister {
public:

  const DoubleFloatRegister* operator->() const { return this; }

  inline constexpr friend FloatRegister as_DoubleFloatRegister(int encoding) {
    return as_FloatRegister(((encoding & 1) << 5) | (encoding & 0x1e));
  }
};

class QuadFloatRegister {
public:
  enum {
    number_of_registers = 32
  };

  const QuadFloatRegister* operator->() const { return this; }

  inline constexpr friend FloatRegister as_QuadFloatRegister(int encoding) {
    assert(encoding < QuadFloatRegister::number_of_registers && ((encoding & 2) == 0), "bad quad float register encoding");
    return as_FloatRegister(((encoding & 1) << 5) | (encoding & 0x1c));
  }
};


//==========================
//===  Vector Registers  ===
//==========================

// The implementation of vector registers for z/Architecture.

class VectorRegister {
  int _encoding;
public:
  enum {
    number_of_registers     = 32,
    max_slots_per_register = 4,
    number_of_arg_registers = 0
  };

  constexpr VectorRegister(int encoding = NOREG_ENCODING) : _encoding(encoding) {}
  bool operator==(const VectorRegister rhs) const { return _encoding == rhs._encoding; }
  bool operator!=(const VectorRegister rhs) const { return _encoding != rhs._encoding; }
  const VectorRegister* operator->()        const { return this; }

  // construction
  inline constexpr friend VectorRegister as_VectorRegister(int encoding);

  inline VMReg as_VMReg() const;

  // accessors
  constexpr int  encoding()  const { assert(is_valid(), "invalid register"); return _encoding; }
  VectorRegister successor() const { return VectorRegister((encoding() + 1) & (number_of_registers - 1)); }


  // tester
  constexpr bool is_valid()       const { return  0 <= _encoding && _encoding < number_of_registers; }
  constexpr bool is_volatile()    const { return true; }
  constexpr bool is_nonvolatile() const { return false; }

  // Register fields in z/Architecture instructions are 4 bits wide, restricting the
  // addressable register set size to 16.
  // The vector register set size is 32, requiring an extension, by one bit, of the
  // register encoding. This is accomplished by the introduction of a RXB field in the
  // instruction. RXB = Register eXtension Bits.
  // The RXB field contains the MSBs (most significant bit) of the vector register numbers
  // used for this instruction. Assignment of MSB in RBX is by bit position of the
  // register field in the instruction.
  // Example:
  //   The register field starting at bit position 12 in the instruction is assigned RXB bit 0b0100.
  int64_t RXB_mask(int pos) const {
    if (encoding() >= number_of_registers/2) {
      switch (pos) {
        case 8:   return ((int64_t)0b1000) << 8; // actual bit pos: 36
        case 12:  return ((int64_t)0b0100) << 8; // actual bit pos: 37
        case 16:  return ((int64_t)0b0010) << 8; // actual bit pos: 38
        case 32:  return ((int64_t)0b0001) << 8; // actual bit pos: 39
        default:
          ShouldNotReachHere();
      }
    }
    return 0;
  }

  const char* name() const;
};

inline constexpr VectorRegister as_VectorRegister(int encoding) {
  assert(encoding == NOREG_ENCODING ||
        (encoding >= 0 && encoding < VectorRegister::number_of_registers), "bad vector register encoding");
  return VectorRegister(encoding);
}

// The Vector registers of z/Architecture.
constexpr VectorRegister vnoreg = as_VectorRegister(NOREG_ENCODING);

constexpr VectorRegister  Z_V0 = as_VectorRegister( 0);
constexpr VectorRegister  Z_V1 = as_VectorRegister( 1);
constexpr VectorRegister  Z_V2 = as_VectorRegister( 2);
constexpr VectorRegister  Z_V3 = as_VectorRegister( 3);
constexpr VectorRegister  Z_V4 = as_VectorRegister( 4);
constexpr VectorRegister  Z_V5 = as_VectorRegister( 5);
constexpr VectorRegister  Z_V6 = as_VectorRegister( 6);
constexpr VectorRegister  Z_V7 = as_VectorRegister( 7);
constexpr VectorRegister  Z_V8 = as_VectorRegister( 8);
constexpr VectorRegister  Z_V9 = as_VectorRegister( 9);
constexpr VectorRegister Z_V10 = as_VectorRegister(10);
constexpr VectorRegister Z_V11 = as_VectorRegister(11);
constexpr VectorRegister Z_V12 = as_VectorRegister(12);
constexpr VectorRegister Z_V13 = as_VectorRegister(13);
constexpr VectorRegister Z_V14 = as_VectorRegister(14);
constexpr VectorRegister Z_V15 = as_VectorRegister(15);
constexpr VectorRegister Z_V16 = as_VectorRegister(16);
constexpr VectorRegister Z_V17 = as_VectorRegister(17);
constexpr VectorRegister Z_V18 = as_VectorRegister(18);
constexpr VectorRegister Z_V19 = as_VectorRegister(19);
constexpr VectorRegister Z_V20 = as_VectorRegister(20);
constexpr VectorRegister Z_V21 = as_VectorRegister(21);
constexpr VectorRegister Z_V22 = as_VectorRegister(22);
constexpr VectorRegister Z_V23 = as_VectorRegister(23);
constexpr VectorRegister Z_V24 = as_VectorRegister(24);
constexpr VectorRegister Z_V25 = as_VectorRegister(25);
constexpr VectorRegister Z_V26 = as_VectorRegister(26);
constexpr VectorRegister Z_V27 = as_VectorRegister(27);
constexpr VectorRegister Z_V28 = as_VectorRegister(28);
constexpr VectorRegister Z_V29 = as_VectorRegister(29);
constexpr VectorRegister Z_V30 = as_VectorRegister(30);
constexpr VectorRegister Z_V31 = as_VectorRegister(31);

// Need to know the total number of registers of all sorts for SharedInfo.
// Define a class that exports it.
class ConcreteRegisterImpl : public AbstractRegisterImpl {
 public:
  enum {
    max_gpr = Register::number_of_registers * Register::max_slots_per_register,
    max_fpr = max_gpr + FloatRegister::number_of_registers * FloatRegister::max_slots_per_register,
    max_vr  = max_fpr + VectorRegister::number_of_registers * VectorRegister::max_slots_per_register,
    // A big enough number for C2: all the registers plus flags
    // This number must be large enough to cover REG_COUNT (defined by c2) registers.
    // There is no requirement that any ordering here matches any ordering c2 gives
    // it's optoregs.
    number_of_registers = max_vr + 1 // gpr/fpr/vr + flags
  };
};

// Common register declarations used in assembler code.
constexpr Register       Z_EXC_OOP = Z_R2;
constexpr Register       Z_EXC_PC  = Z_R3;
constexpr Register       Z_RET     = Z_R2;
constexpr Register       Z_ARG1    = Z_R2;
constexpr Register       Z_ARG2    = Z_R3;
constexpr Register       Z_ARG3    = Z_R4;
constexpr Register       Z_ARG4    = Z_R5;
constexpr Register       Z_ARG5    = Z_R6;
constexpr Register       Z_SP      = Z_R15;
constexpr FloatRegister  Z_FRET    = Z_F0;
constexpr FloatRegister  Z_FARG1   = Z_F0;
constexpr FloatRegister  Z_FARG2   = Z_F2;
constexpr FloatRegister  Z_FARG3   = Z_F4;
constexpr FloatRegister  Z_FARG4   = Z_F6;

// Register declarations to be used in template interpreter assembly code.
// Use only non-volatile registers in order to keep values across C-calls.

// Register to cache the integer value on top of the operand stack.
constexpr Register      Z_tos          = Z_R2;
// Register to cache the fp value on top of the operand stack.
constexpr FloatRegister Z_ftos         = Z_F0;
// Expression stack pointer in interpreted java frame.
constexpr Register      Z_esp          = Z_R7;
// Address of current thread.
constexpr Register      Z_thread       = Z_R8;
// Address of current method. only valid in interpreter_entry.
constexpr Register      Z_method       = Z_R9;
// Inline cache register. used by c1 and c2.
constexpr Register      Z_inline_cache = Z_R9;
// Frame pointer of current interpreter frame. only valid while
// executing bytecodes.
constexpr Register      Z_fp           = Z_R9;
// Address of the locals array in an interpreted java frame.
constexpr Register      Z_locals       = Z_R12;
// Bytecode pointer.
constexpr Register      Z_bcp          = Z_R13;
// Bytecode which is dispatched (short lived!).
constexpr Register      Z_bytecode     = Z_R14;

// Temporary registers to be used within template interpreter. We can use
// the nonvolatile ones because the call stub has saved them.
// Use only non-volatile registers in order to keep values across C-calls.
constexpr Register Z_tmp_1 =  Z_R10;
constexpr Register Z_tmp_2 =  Z_R11;
constexpr Register Z_tmp_3 =  Z_R12;
constexpr Register Z_tmp_4 =  Z_R13;

// Scratch registers are volatile.
constexpr Register      Z_R0_scratch = Z_R0;
constexpr Register      Z_R1_scratch = Z_R1;
constexpr FloatRegister Z_fscratch_1 = Z_F1;

typedef AbstractRegSet<Register> RegSet;

template <>
inline Register AbstractRegSet<Register>::first() {
  if (_bitset == 0) { return noreg; }
  return as_Register(count_trailing_zeros(_bitset));
}

#endif // CPU_S390_REGISTER_S390_HPP
