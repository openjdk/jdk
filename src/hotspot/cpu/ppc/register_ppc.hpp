/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2025 SAP SE. All rights reserved.
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

#ifndef CPU_PPC_REGISTER_PPC_HPP
#define CPU_PPC_REGISTER_PPC_HPP

#include "asm/register.hpp"
#include "utilities/count_trailing_zeros.hpp"

// forward declaration
class VMRegImpl;
typedef VMRegImpl* VMReg;

//  PPC64 registers
//
//  See "64-bit PowerPC ELF ABI Supplement 1.7", IBM Corp. (2003-10-29).
//  (http://math-atlas.sourceforge.net/devel/assembly/PPC-elf64abi-1.7.pdf)
//
//  r0        Register used in function prologs (volatile)
//  r1        Stack pointer (nonvolatile)
//  r2        TOC pointer (volatile)
//  r3        Parameter and return value (volatile)
//  r4-r10    Function parameters (volatile)
//  r11       Register used in calls by pointer and as an environment pointer for languages which require one (volatile)
//  r12       Register used for exception handling and glink code (volatile)
//  r13       Reserved for use as system thread ID
//  r14-r31   Local variables (nonvolatile)
//
//  f0        Scratch register (volatile)
//  f1-f4     Floating point parameters and return value (volatile)
//  f5-f13    Floating point parameters (volatile)
//  f14-f31   Floating point values (nonvolatile)
//
//  LR        Link register for return address (volatile)
//  CTR       Loop counter (volatile)
//  XER       Fixed point exception register (volatile)
//  FPSCR     Floating point status and control register (volatile)
//
//  CR0-CR1   Condition code fields (volatile)
//  CR2-CR4   Condition code fields (nonvolatile)
//  CR5-CR7   Condition code fields (volatile)
//
//  ----------------------------------------------
//  On processors with the VMX feature:
//  v0-v1     Volatile scratch registers
//  v2-v13    Volatile vector parameters registers
//  v14-v19   Volatile scratch registers
//  v20-v31   Non-volatile registers
//  vrsave    Non-volatile 32-bit register
//
//  ----------------------------------------------
//  On processors with VSX feature:
//  vs0-31    Alias for f0-f31 (64 bit, see above)
//  vs32-63   Alias for v0-31 (128 bit, see above)


// The implementation of integer registers for the Power architecture
class Register {
  int _encoding;
 public:
  enum {
    number_of_registers = 32
  };

  constexpr Register(int encoding = -1) : _encoding(encoding) {}
  bool operator==(const Register rhs) const { return _encoding == rhs._encoding; }
  bool operator!=(const Register rhs) const { return _encoding != rhs._encoding; }
  const Register* operator->() const { return this; }

  // general construction
  inline constexpr friend Register as_Register(int encoding);

  // accessors
  constexpr int encoding() const { assert(is_valid(), "invalid register"); return _encoding; }
  inline VMReg as_VMReg() const;
  Register successor() const { return Register(encoding() + 1); }

  // testers
  constexpr bool is_valid()       const { return ( 0 <= _encoding && _encoding <  number_of_registers); }
  constexpr bool is_volatile()    const { return ( 0 <= _encoding && _encoding <= 13); }
  constexpr bool is_nonvolatile() const { return (14 <= _encoding && _encoding <= 31); }

  const char* name() const;
};

inline constexpr Register as_Register(int encoding) {
  assert(encoding >= -1 && encoding < 32, "bad register encoding");
  return Register(encoding);
}

// The integer registers of the PPC architecture
constexpr Register noreg = as_Register(-1);

constexpr Register  R0 = as_Register( 0);
constexpr Register  R1 = as_Register( 1);
constexpr Register  R2 = as_Register( 2);
constexpr Register  R3 = as_Register( 3);
constexpr Register  R4 = as_Register( 4);
constexpr Register  R5 = as_Register( 5);
constexpr Register  R6 = as_Register( 6);
constexpr Register  R7 = as_Register( 7);
constexpr Register  R8 = as_Register( 8);
constexpr Register  R9 = as_Register( 9);
constexpr Register R10 = as_Register(10);
constexpr Register R11 = as_Register(11);
constexpr Register R12 = as_Register(12);
constexpr Register R13 = as_Register(13);
constexpr Register R14 = as_Register(14);
constexpr Register R15 = as_Register(15);
constexpr Register R16 = as_Register(16);
constexpr Register R17 = as_Register(17);
constexpr Register R18 = as_Register(18);
constexpr Register R19 = as_Register(19);
constexpr Register R20 = as_Register(20);
constexpr Register R21 = as_Register(21);
constexpr Register R22 = as_Register(22);
constexpr Register R23 = as_Register(23);
constexpr Register R24 = as_Register(24);
constexpr Register R25 = as_Register(25);
constexpr Register R26 = as_Register(26);
constexpr Register R27 = as_Register(27);
constexpr Register R28 = as_Register(28);
constexpr Register R29 = as_Register(29);
constexpr Register R30 = as_Register(30);
constexpr Register R31 = as_Register(31);


// The implementation of condition register(s) for the PPC architecture
class ConditionRegister {
  int _encoding;
 public:
  enum {
    number_of_registers = 8
  };

  constexpr ConditionRegister(int encoding = -1) : _encoding(encoding) {}
  bool operator==(const ConditionRegister rhs) const { return _encoding == rhs._encoding; }
  bool operator!=(const ConditionRegister rhs) const { return _encoding != rhs._encoding; }
  const ConditionRegister* operator->() const { return this; }

  // construction.
  inline constexpr friend ConditionRegister as_ConditionRegister(int encoding);

  // accessors
  constexpr int encoding() const { assert(is_valid(), "invalid register"); return _encoding; }
  inline VMReg as_VMReg() const;

  // testers
  constexpr bool is_valid()       const { return (0 <= _encoding && _encoding <  number_of_registers); }
  constexpr bool is_nonvolatile() const { return (2 <= _encoding && _encoding <= 4); }

  const char* name() const;
};

inline constexpr ConditionRegister as_ConditionRegister(int encoding) {
  assert(encoding >= 0 && encoding < 8, "bad condition register encoding");
  return ConditionRegister(encoding);
}

constexpr ConditionRegister CR0 = as_ConditionRegister(0);
constexpr ConditionRegister CR1 = as_ConditionRegister(1);
constexpr ConditionRegister CR2 = as_ConditionRegister(2);
constexpr ConditionRegister CR3 = as_ConditionRegister(3);
constexpr ConditionRegister CR4 = as_ConditionRegister(4);
constexpr ConditionRegister CR5 = as_ConditionRegister(5);
constexpr ConditionRegister CR6 = as_ConditionRegister(6);
constexpr ConditionRegister CR7 = as_ConditionRegister(7);


class VectorSRegister;

// The implementation of float registers for the PPC architecture
class FloatRegister {
  int _encoding;
 public:
  enum {
    number_of_registers = 32
  };

  constexpr FloatRegister(int encoding = -1) : _encoding(encoding) {}
  bool operator==(const FloatRegister rhs) const { return _encoding == rhs._encoding; }
  bool operator!=(const FloatRegister rhs) const { return _encoding != rhs._encoding; }
  const FloatRegister* operator->() const { return this; }

  // construction
  inline constexpr friend FloatRegister as_FloatRegister(int encoding);

  // accessors
  constexpr int encoding() const { assert(is_valid(), "invalid register"); return _encoding; }
  inline VMReg as_VMReg() const;
  FloatRegister successor() const { return FloatRegister(encoding() + 1); }

  // testers
  constexpr bool is_valid() const { return (0 <= _encoding && _encoding < number_of_registers); }
  constexpr bool is_nonvolatile() const { return (14 <= _encoding && _encoding <= 31); }

  const char* name() const;

  // convert to VSR
  VectorSRegister to_vsr() const;
};

inline constexpr FloatRegister as_FloatRegister(int encoding) {
  assert(encoding >= -1 && encoding < 32, "bad float register encoding");
  return FloatRegister(encoding);
}

// The float registers of the PPC architecture
constexpr FloatRegister fnoreg = as_FloatRegister(-1);

constexpr FloatRegister  F0 = as_FloatRegister( 0);
constexpr FloatRegister  F1 = as_FloatRegister( 1);
constexpr FloatRegister  F2 = as_FloatRegister( 2);
constexpr FloatRegister  F3 = as_FloatRegister( 3);
constexpr FloatRegister  F4 = as_FloatRegister( 4);
constexpr FloatRegister  F5 = as_FloatRegister( 5);
constexpr FloatRegister  F6 = as_FloatRegister( 6);
constexpr FloatRegister  F7 = as_FloatRegister( 7);
constexpr FloatRegister  F8 = as_FloatRegister( 8);
constexpr FloatRegister  F9 = as_FloatRegister( 9);
constexpr FloatRegister F10 = as_FloatRegister(10);
constexpr FloatRegister F11 = as_FloatRegister(11);
constexpr FloatRegister F12 = as_FloatRegister(12);
constexpr FloatRegister F13 = as_FloatRegister(13);
constexpr FloatRegister F14 = as_FloatRegister(14);
constexpr FloatRegister F15 = as_FloatRegister(15);
constexpr FloatRegister F16 = as_FloatRegister(16);
constexpr FloatRegister F17 = as_FloatRegister(17);
constexpr FloatRegister F18 = as_FloatRegister(18);
constexpr FloatRegister F19 = as_FloatRegister(19);
constexpr FloatRegister F20 = as_FloatRegister(20);
constexpr FloatRegister F21 = as_FloatRegister(21);
constexpr FloatRegister F22 = as_FloatRegister(22);
constexpr FloatRegister F23 = as_FloatRegister(23);
constexpr FloatRegister F24 = as_FloatRegister(24);
constexpr FloatRegister F25 = as_FloatRegister(25);
constexpr FloatRegister F26 = as_FloatRegister(26);
constexpr FloatRegister F27 = as_FloatRegister(27);
constexpr FloatRegister F28 = as_FloatRegister(28);
constexpr FloatRegister F29 = as_FloatRegister(29);
constexpr FloatRegister F30 = as_FloatRegister(30);
constexpr FloatRegister F31 = as_FloatRegister(31);


// The implementation of special registers for the Power architecture (LR, CTR and friends)
class SpecialRegister {
  int _encoding;
 public:
  enum {
    number_of_registers = 6
  };

  constexpr SpecialRegister(int encoding = -1) : _encoding(encoding) {}
  bool operator==(const SpecialRegister rhs) const { return _encoding == rhs._encoding; }
  bool operator!=(const SpecialRegister rhs) const { return _encoding != rhs._encoding; }
  const SpecialRegister* operator->() const { return this; }

  // construction
  inline constexpr friend SpecialRegister as_SpecialRegister(int encoding);

  // accessors
  constexpr int encoding() const { assert(is_valid(), "invalid register"); return _encoding; }
  inline VMReg as_VMReg() const;

  // testers
  constexpr bool is_valid() const { return (0 <= _encoding && _encoding < number_of_registers); }

  const char* name() const;
};

inline constexpr SpecialRegister as_SpecialRegister(int encoding) {
  return SpecialRegister(encoding);
}

// The special registers of the PPC architecture
constexpr SpecialRegister SR_XER     = as_SpecialRegister(0);
constexpr SpecialRegister SR_LR      = as_SpecialRegister(1);
constexpr SpecialRegister SR_CTR     = as_SpecialRegister(2);
constexpr SpecialRegister SR_VRSAVE  = as_SpecialRegister(3);
constexpr SpecialRegister SR_SPEFSCR = as_SpecialRegister(4);
constexpr SpecialRegister SR_PPR     = as_SpecialRegister(5);


// The implementation of vector registers for the Power architecture
class VectorRegister {
  int _encoding;
 public:
  enum {
    number_of_registers = 32
  };

  constexpr VectorRegister(int encoding = -1) : _encoding(encoding) {}
  bool operator==(const VectorRegister rhs) const { return _encoding == rhs._encoding; }
  bool operator!=(const VectorRegister rhs) const { return _encoding != rhs._encoding; }
  const VectorRegister* operator->() const { return this; }

  // construction
  inline constexpr friend VectorRegister as_VectorRegister(int encoding);

  // accessors
  constexpr int encoding() const { assert(is_valid(), "invalid register"); return _encoding; }
  inline VMReg as_VMReg() const;

  // testers
  constexpr bool is_valid() const { return (0 <= _encoding && _encoding < number_of_registers); }
  constexpr bool is_nonvolatile() const { return (20 <= _encoding && _encoding <= 31); }

  const char* name() const;

  // convert to VSR
  VectorSRegister to_vsr() const;
};

inline constexpr VectorRegister as_VectorRegister(int encoding) {
  return VectorRegister(encoding);
}

// The Vector registers of the Power architecture
constexpr VectorRegister vnoreg = as_VectorRegister(-1);

constexpr VectorRegister  VR0 = as_VectorRegister( 0);
constexpr VectorRegister  VR1 = as_VectorRegister( 1);
constexpr VectorRegister  VR2 = as_VectorRegister( 2);
constexpr VectorRegister  VR3 = as_VectorRegister( 3);
constexpr VectorRegister  VR4 = as_VectorRegister( 4);
constexpr VectorRegister  VR5 = as_VectorRegister( 5);
constexpr VectorRegister  VR6 = as_VectorRegister( 6);
constexpr VectorRegister  VR7 = as_VectorRegister( 7);
constexpr VectorRegister  VR8 = as_VectorRegister( 8);
constexpr VectorRegister  VR9 = as_VectorRegister( 9);
constexpr VectorRegister VR10 = as_VectorRegister(10);
constexpr VectorRegister VR11 = as_VectorRegister(11);
constexpr VectorRegister VR12 = as_VectorRegister(12);
constexpr VectorRegister VR13 = as_VectorRegister(13);
constexpr VectorRegister VR14 = as_VectorRegister(14);
constexpr VectorRegister VR15 = as_VectorRegister(15);
constexpr VectorRegister VR16 = as_VectorRegister(16);
constexpr VectorRegister VR17 = as_VectorRegister(17);
constexpr VectorRegister VR18 = as_VectorRegister(18);
constexpr VectorRegister VR19 = as_VectorRegister(19);
constexpr VectorRegister VR20 = as_VectorRegister(20);
constexpr VectorRegister VR21 = as_VectorRegister(21);
constexpr VectorRegister VR22 = as_VectorRegister(22);
constexpr VectorRegister VR23 = as_VectorRegister(23);
constexpr VectorRegister VR24 = as_VectorRegister(24);
constexpr VectorRegister VR25 = as_VectorRegister(25);
constexpr VectorRegister VR26 = as_VectorRegister(26);
constexpr VectorRegister VR27 = as_VectorRegister(27);
constexpr VectorRegister VR28 = as_VectorRegister(28);
constexpr VectorRegister VR29 = as_VectorRegister(29);
constexpr VectorRegister VR30 = as_VectorRegister(30);
constexpr VectorRegister VR31 = as_VectorRegister(31);


// The implementation of Vector-Scalar (VSX) registers on POWER architecture.
// VSR0-31 are aliases for F0-31 and VSR32-63 are aliases for VR0-31.
class VectorSRegister {
  int _encoding;
 public:
  enum {
    number_of_registers = 64
  };

  constexpr VectorSRegister(int encoding = -1) : _encoding(encoding) {}
  bool operator==(const VectorSRegister rhs) const { return _encoding == rhs._encoding; }
  bool operator!=(const VectorSRegister rhs) const { return _encoding != rhs._encoding; }
  const VectorSRegister* operator->() const { return this; }

  // construction
  inline constexpr friend VectorSRegister as_VectorSRegister(int encoding);

  // accessors
  constexpr int encoding() const { assert(is_valid(), "invalid register"); return _encoding; }
  VectorSRegister successor() const { return VectorSRegister(encoding() + 1); }

  // testers
  constexpr bool is_valid() const { return (0 <= _encoding && _encoding < number_of_registers); }

  const char* name() const;

  // convert to VR
  VectorRegister to_vr() const;
};

inline constexpr VectorSRegister as_VectorSRegister(int encoding) {
  return VectorSRegister(encoding);
}

// The Vector-Scalar (VSX) registers of the POWER architecture.
constexpr VectorSRegister vsnoreg = as_VectorSRegister(-1);

constexpr VectorSRegister  VSR0 = as_VectorSRegister( 0);
constexpr VectorSRegister  VSR1 = as_VectorSRegister( 1);
constexpr VectorSRegister  VSR2 = as_VectorSRegister( 2);
constexpr VectorSRegister  VSR3 = as_VectorSRegister( 3);
constexpr VectorSRegister  VSR4 = as_VectorSRegister( 4);
constexpr VectorSRegister  VSR5 = as_VectorSRegister( 5);
constexpr VectorSRegister  VSR6 = as_VectorSRegister( 6);
constexpr VectorSRegister  VSR7 = as_VectorSRegister( 7);
constexpr VectorSRegister  VSR8 = as_VectorSRegister( 8);
constexpr VectorSRegister  VSR9 = as_VectorSRegister( 9);
constexpr VectorSRegister VSR10 = as_VectorSRegister(10);
constexpr VectorSRegister VSR11 = as_VectorSRegister(11);
constexpr VectorSRegister VSR12 = as_VectorSRegister(12);
constexpr VectorSRegister VSR13 = as_VectorSRegister(13);
constexpr VectorSRegister VSR14 = as_VectorSRegister(14);
constexpr VectorSRegister VSR15 = as_VectorSRegister(15);
constexpr VectorSRegister VSR16 = as_VectorSRegister(16);
constexpr VectorSRegister VSR17 = as_VectorSRegister(17);
constexpr VectorSRegister VSR18 = as_VectorSRegister(18);
constexpr VectorSRegister VSR19 = as_VectorSRegister(19);
constexpr VectorSRegister VSR20 = as_VectorSRegister(20);
constexpr VectorSRegister VSR21 = as_VectorSRegister(21);
constexpr VectorSRegister VSR22 = as_VectorSRegister(22);
constexpr VectorSRegister VSR23 = as_VectorSRegister(23);
constexpr VectorSRegister VSR24 = as_VectorSRegister(24);
constexpr VectorSRegister VSR25 = as_VectorSRegister(25);
constexpr VectorSRegister VSR26 = as_VectorSRegister(26);
constexpr VectorSRegister VSR27 = as_VectorSRegister(27);
constexpr VectorSRegister VSR28 = as_VectorSRegister(28);
constexpr VectorSRegister VSR29 = as_VectorSRegister(29);
constexpr VectorSRegister VSR30 = as_VectorSRegister(30);
constexpr VectorSRegister VSR31 = as_VectorSRegister(31);
constexpr VectorSRegister VSR32 = as_VectorSRegister(32);
constexpr VectorSRegister VSR33 = as_VectorSRegister(33);
constexpr VectorSRegister VSR34 = as_VectorSRegister(34);
constexpr VectorSRegister VSR35 = as_VectorSRegister(35);
constexpr VectorSRegister VSR36 = as_VectorSRegister(36);
constexpr VectorSRegister VSR37 = as_VectorSRegister(37);
constexpr VectorSRegister VSR38 = as_VectorSRegister(38);
constexpr VectorSRegister VSR39 = as_VectorSRegister(39);
constexpr VectorSRegister VSR40 = as_VectorSRegister(40);
constexpr VectorSRegister VSR41 = as_VectorSRegister(41);
constexpr VectorSRegister VSR42 = as_VectorSRegister(42);
constexpr VectorSRegister VSR43 = as_VectorSRegister(43);
constexpr VectorSRegister VSR44 = as_VectorSRegister(44);
constexpr VectorSRegister VSR45 = as_VectorSRegister(45);
constexpr VectorSRegister VSR46 = as_VectorSRegister(46);
constexpr VectorSRegister VSR47 = as_VectorSRegister(47);
constexpr VectorSRegister VSR48 = as_VectorSRegister(48);
constexpr VectorSRegister VSR49 = as_VectorSRegister(49);
constexpr VectorSRegister VSR50 = as_VectorSRegister(50);
constexpr VectorSRegister VSR51 = as_VectorSRegister(51);
constexpr VectorSRegister VSR52 = as_VectorSRegister(52);
constexpr VectorSRegister VSR53 = as_VectorSRegister(53);
constexpr VectorSRegister VSR54 = as_VectorSRegister(54);
constexpr VectorSRegister VSR55 = as_VectorSRegister(55);
constexpr VectorSRegister VSR56 = as_VectorSRegister(56);
constexpr VectorSRegister VSR57 = as_VectorSRegister(57);
constexpr VectorSRegister VSR58 = as_VectorSRegister(58);
constexpr VectorSRegister VSR59 = as_VectorSRegister(59);
constexpr VectorSRegister VSR60 = as_VectorSRegister(60);
constexpr VectorSRegister VSR61 = as_VectorSRegister(61);
constexpr VectorSRegister VSR62 = as_VectorSRegister(62);
constexpr VectorSRegister VSR63 = as_VectorSRegister(63);


// Need to know the total number of registers of all sorts for SharedInfo.
// Define a class that exports it.
class ConcreteRegisterImpl : public AbstractRegisterImpl {
 public:
  enum {
    max_gpr = Register::number_of_registers * 2,
    max_fpr = max_gpr + FloatRegister::number_of_registers * 2,
    max_vr  = max_fpr + VectorRegister::number_of_registers * 4,
    max_cnd = max_vr  + ConditionRegister::number_of_registers,
    max_spr = max_cnd + SpecialRegister::number_of_registers,
    // This number must be large enough to cover REG_COUNT (defined by c2) registers.
    // There is no requirement that any ordering here matches any ordering c2 gives
    // it's optoregs.
    number_of_registers = max_spr
  };
};

// Common register declarations used in assembler code.
constexpr Register R0_SCRATCH = R0;  // volatile
constexpr Register R1_SP      = R1;  // non-volatile
constexpr Register R2_TOC     = R2;  // volatile
constexpr Register R3_RET     = R3;  // volatile
constexpr Register R3_ARG1    = R3;  // volatile
constexpr Register R4_ARG2    = R4;  // volatile
constexpr Register R5_ARG3    = R5;  // volatile
constexpr Register R6_ARG4    = R6;  // volatile
constexpr Register R7_ARG5    = R7;  // volatile
constexpr Register R8_ARG6    = R8;  // volatile
constexpr Register R9_ARG7    = R9;  // volatile
constexpr Register R10_ARG8   = R10; // volatile
constexpr FloatRegister F0_SCRATCH = F0;  // volatile
constexpr FloatRegister F1_RET     = F1;  // volatile
constexpr FloatRegister F1_ARG1    = F1;  // volatile
constexpr FloatRegister F2_ARG2    = F2;  // volatile
constexpr FloatRegister F3_ARG3    = F3;  // volatile
constexpr FloatRegister F4_ARG4    = F4;  // volatile
constexpr FloatRegister F5_ARG5    = F5;  // volatile
constexpr FloatRegister F6_ARG6    = F6;  // volatile
constexpr FloatRegister F7_ARG7    = F7;  // volatile
constexpr FloatRegister F8_ARG8    = F8;  // volatile
constexpr FloatRegister F9_ARG9    = F9;  // volatile
constexpr FloatRegister F10_ARG10  = F10; // volatile
constexpr FloatRegister F11_ARG11  = F11; // volatile
constexpr FloatRegister F12_ARG12  = F12; // volatile
constexpr FloatRegister F13_ARG13  = F13; // volatile

// Register declarations to be used in template interpreter assembly code.
// Use only non-volatile registers in order to keep values across C-calls.
constexpr Register R14_bcp       = R14;
constexpr Register R15_esp       = R15;      // slot below top of expression stack for ld/st with update
constexpr FloatRegister F15_ftos = F15;
constexpr Register R16_thread    = R16;      // address of current thread
constexpr Register R17_tos       = R17;      // The interpreter's top of (expression) stack cache register
constexpr Register R18_locals    = R18;      // address of first param slot (receiver).
constexpr Register R19_method    = R19;      // address of current method

// Temporary registers to be used within template interpreter. We can use
// the non-volatiles because the call stub has saved them.
// Use only non-volatile registers in order to keep values across C-calls.
constexpr Register R21_tmp1 = R21;
constexpr Register R22_tmp2 = R22;
constexpr Register R23_tmp3 = R23;
constexpr Register R24_tmp4 = R24;
constexpr Register R25_tmp5 = R25;
constexpr Register R26_tmp6 = R26;
constexpr Register R27_tmp7 = R27;
constexpr Register R28_tmp8 = R28;
constexpr Register R29_tmp9 = R29;
constexpr Register R24_dispatch_addr     = R24;
constexpr Register R25_templateTableBase = R25;
constexpr Register R26_monitor           = R26;
constexpr Register R27_constPoolCache    = R27;
constexpr Register R28_mdx               = R28;

constexpr Register R19_inline_cache_reg = R19;
constexpr Register R21_sender_SP = R21;
constexpr Register R23_method_handle = R23;
constexpr Register R29_TOC = R29;

// Scratch registers are volatile.
constexpr Register R11_scratch1 = R11;
constexpr Register R12_scratch2 = R12;

template <>
inline Register AbstractRegSet<Register>::first() {
  if (_bitset == 0) { return noreg; }
  return as_Register(count_trailing_zeros(_bitset));
}

typedef AbstractRegSet<Register> RegSet;

#endif // CPU_PPC_REGISTER_PPC_HPP
