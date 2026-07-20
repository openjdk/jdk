/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_ARM_REGISTER_ARM_HPP
#define CPU_ARM_REGISTER_ARM_HPP

#include "asm/register.hpp"
#include "runtime/vm_version.hpp"

class VMRegImpl;
typedef VMRegImpl* VMReg;

/////////////////////////////////
// Support for different ARM ABIs
// Note: default ABI is for linux


// R9_IS_SCRATCHED
//
// The ARM ABI does not guarantee that R9 is callee saved.
// Set R9_IS_SCRATCHED to 1 to ensure it is properly saved/restored by
// the caller.
#ifndef R9_IS_SCRATCHED
// Default: R9 is callee saved
#define R9_IS_SCRATCHED 0
#endif

// FP_REG_NUM
//
// The ARM ABI does not state which register is used for the frame pointer.
// Note: for the ABIs we are currently aware of, FP is currently
// either R7 or R11. Code may have to be extended if a third register
// register must be supported (see altFP_7_11).
#ifndef FP_REG_NUM
// Default: FP is R11
#define FP_REG_NUM 11
#endif

// ALIGN_WIDE_ARGUMENTS
//
// The ARM ABI requires 64-bits arguments to be aligned on 4 words
// or on even registers. Set ALIGN_WIDE_ARGUMENTS to 1 for that behavior.
//
// Unfortunately, some platforms do not endorse that part of the ABI.
//
// We are aware of one which expects 64-bit arguments to only be 4
// bytes aligned and can for instance use R3 + a stack slot for such
// an argument.
//
// This is the behavor implemented if (ALIGN_WIDE_ARGUMENTS == 0)
#ifndef  ALIGN_WIDE_ARGUMENTS
// Default: align on 8 bytes and avoid using <r3+stack>
#define ALIGN_WIDE_ARGUMENTS 1
#endif

class Register {
 private:
  int _encoding;

  constexpr explicit Register(int encoding) : _encoding(encoding) {}

 public:
  enum {
    number_of_registers = 16,
    max_slots_per_register = 1
  };

  class RegisterImpl : public AbstractRegisterImpl {
    friend class Register;

    static constexpr const RegisterImpl* first();

   public:

    // accessors and testers
    int raw_encoding() const { return this - first(); }
    int encoding() const     { assert(is_valid(), "invalid register"); return raw_encoding(); }
    bool is_valid() const    { return 0 <= raw_encoding() && raw_encoding() < number_of_registers; }

    inline Register successor() const;

    VMReg as_VMReg() const;

    const char* name() const;
  };


  inline friend constexpr Register as_Register(int encoding);

  constexpr Register() : _encoding(-1) {} //noreg

  int operator==(const Register r) const { return _encoding == r._encoding; }
  int operator!=(const Register r) const { return _encoding != r._encoding; }

  const RegisterImpl* operator->() const { return RegisterImpl::first() + _encoding; }
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
  assert(is_valid(), "sainty");
  return as_Register(encoding() + 1);
}

constexpr Register R0  = as_Register( 0);
constexpr Register R1  = as_Register( 1);
constexpr Register R2  = as_Register( 2);
constexpr Register R3  = as_Register( 3);
constexpr Register R4  = as_Register( 4);
constexpr Register R5  = as_Register( 5);
constexpr Register R6  = as_Register( 6);
constexpr Register R7  = as_Register( 7);
constexpr Register R8  = as_Register( 8);
constexpr Register R9  = as_Register( 9);
constexpr Register R10 = as_Register(10);
constexpr Register R11 = as_Register(11);
constexpr Register R12 = as_Register(12);
constexpr Register R13 = as_Register(13);
constexpr Register R14 = as_Register(14);
constexpr Register R15 = as_Register(15);

constexpr Register FP = as_Register(FP_REG_NUM);

// Safe use of registers which may be FP on some platforms.
//
// altFP_7_11: R7 if not equal to FP, else R11 (the default FP)
//
// Note: add additional altFP_#_11 for each register potentially used
// as FP on supported ABIs (and replace R# by altFP_#_11). altFP_#_11
// must be #define to R11 if and only if # is FP_REG_NUM.
#if (FP_REG_NUM == 7)
constexpr Register altFP_7_11 = R11;
#else
constexpr Register altFP_7_11 = R7;
#endif
constexpr Register SP = R13;
constexpr Register LR = R14;
constexpr Register PC = R15;



class FloatRegister {
 private:
  int _encoding;

  constexpr explicit FloatRegister(int encoding) : _encoding(encoding) {}

 public:
  enum {
    number_of_registers = NOT_COMPILER2(32) COMPILER2_PRESENT(64),
    max_slots_per_register = 1
  };

  class FloatRegisterImpl : public AbstractRegisterImpl {
    friend class FloatRegister;

    static constexpr const FloatRegisterImpl* first();

   public:

    // accessors and testers
    int raw_encoding() const { return this - first(); }
    int encoding() const     { assert(is_valid(), "invalid register"); return raw_encoding(); }
    bool is_valid() const    { return 0 <= raw_encoding() && raw_encoding() < number_of_registers; }

    inline FloatRegister successor() const;

    VMReg as_VMReg() const;

    int hi_bits() const {
      return (encoding() >> 1) & 0xf;
    }

    int lo_bit() const {
      return encoding() & 1;
    }

    int hi_bit() const {
      return encoding() >> 5;
    }

    const char* name() const;
  };

  inline friend constexpr FloatRegister as_FloatRegister(int encoding);

  constexpr FloatRegister() : _encoding(-1) {} // fnoreg

  int operator==(const FloatRegister r) const { return _encoding == r._encoding; }
  int operator!=(const FloatRegister r) const { return _encoding != r._encoding; }

  const FloatRegisterImpl* operator->() const { return FloatRegisterImpl::first() + _encoding; }
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
  assert(is_valid(), "sainty");
  return as_FloatRegister(encoding() + 1);
}


/*
 * S1-S6 are named with "_reg" suffix to avoid conflict with
 * constants defined in sharedRuntimeTrig.cpp
 */
constexpr FloatRegister S0  = as_FloatRegister( 0);
constexpr FloatRegister S1_reg = as_FloatRegister(1);
constexpr FloatRegister S2_reg = as_FloatRegister(2);
constexpr FloatRegister S3_reg = as_FloatRegister(3);
constexpr FloatRegister S4_reg = as_FloatRegister(4);
constexpr FloatRegister S5_reg = as_FloatRegister(5);
constexpr FloatRegister S6_reg = as_FloatRegister(6);
constexpr FloatRegister S7  = as_FloatRegister( 7);
constexpr FloatRegister S8  = as_FloatRegister( 8);
constexpr FloatRegister S9  = as_FloatRegister( 9);
constexpr FloatRegister S10 = as_FloatRegister(10);
constexpr FloatRegister S11 = as_FloatRegister(11);
constexpr FloatRegister S12 = as_FloatRegister(12);
constexpr FloatRegister S13 = as_FloatRegister(13);
constexpr FloatRegister S14 = as_FloatRegister(14);
constexpr FloatRegister S15 = as_FloatRegister(15);
constexpr FloatRegister S16 = as_FloatRegister(16);
constexpr FloatRegister S17 = as_FloatRegister(17);
constexpr FloatRegister S18 = as_FloatRegister(18);
constexpr FloatRegister S19 = as_FloatRegister(19);
constexpr FloatRegister S20 = as_FloatRegister(20);
constexpr FloatRegister S21 = as_FloatRegister(21);
constexpr FloatRegister S22 = as_FloatRegister(22);
constexpr FloatRegister S23 = as_FloatRegister(23);
constexpr FloatRegister S24 = as_FloatRegister(24);
constexpr FloatRegister S25 = as_FloatRegister(25);
constexpr FloatRegister S26 = as_FloatRegister(26);
constexpr FloatRegister S27 = as_FloatRegister(27);
constexpr FloatRegister S28 = as_FloatRegister(28);
constexpr FloatRegister S29 = as_FloatRegister(29);
constexpr FloatRegister S30 = as_FloatRegister(30);
constexpr FloatRegister S31 = as_FloatRegister(31);
constexpr FloatRegister Stemp = S30;

constexpr FloatRegister D0  = as_FloatRegister( 0);
constexpr FloatRegister D1  = as_FloatRegister( 2);
constexpr FloatRegister D2  = as_FloatRegister( 4);
constexpr FloatRegister D3  = as_FloatRegister( 6);
constexpr FloatRegister D4  = as_FloatRegister( 8);
constexpr FloatRegister D5  = as_FloatRegister(10);
constexpr FloatRegister D6  = as_FloatRegister(12);
constexpr FloatRegister D7  = as_FloatRegister(14);
constexpr FloatRegister D8  = as_FloatRegister(16);
constexpr FloatRegister D9  = as_FloatRegister(18);
constexpr FloatRegister D10 = as_FloatRegister(20);
constexpr FloatRegister D11 = as_FloatRegister(22);
constexpr FloatRegister D12 = as_FloatRegister(24);
constexpr FloatRegister D13 = as_FloatRegister(26);
constexpr FloatRegister D14 = as_FloatRegister(28);
constexpr FloatRegister D15 = as_FloatRegister(30);
constexpr FloatRegister D16 = as_FloatRegister(32);
constexpr FloatRegister D17 = as_FloatRegister(34);
constexpr FloatRegister D18 = as_FloatRegister(36);
constexpr FloatRegister D19 = as_FloatRegister(38);
constexpr FloatRegister D20 = as_FloatRegister(40);
constexpr FloatRegister D21 = as_FloatRegister(42);
constexpr FloatRegister D22 = as_FloatRegister(44);
constexpr FloatRegister D23 = as_FloatRegister(46);
constexpr FloatRegister D24 = as_FloatRegister(48);
constexpr FloatRegister D25 = as_FloatRegister(50);
constexpr FloatRegister D26 = as_FloatRegister(52);
constexpr FloatRegister D27 = as_FloatRegister(54);
constexpr FloatRegister D28 = as_FloatRegister(56);
constexpr FloatRegister D29 = as_FloatRegister(58);
constexpr FloatRegister D30 = as_FloatRegister(60);
constexpr FloatRegister D31 = as_FloatRegister(62);


class ConcreteRegisterImpl : public AbstractRegisterImpl {
 public:
  enum {
    max_gpr = Register::number_of_registers * Register::max_slots_per_register,
    max_fpr = max_gpr + FloatRegister::number_of_registers * FloatRegister::max_slots_per_register,

    number_of_registers = max_fpr + 1+1 // APSR and FPSCR so that c2's REG_COUNT <= ConcreteRegisterImpl::number_of_registers
  };
};

typedef AbstractRegSet<Register> RegSet;
typedef AbstractRegSet<FloatRegister> FloatRegSet;

template <>
inline Register AbstractRegSet<Register>::first() {
  if (_bitset == 0) { return noreg; }
  return as_Register(count_trailing_zeros(_bitset));
}


template <>
inline FloatRegister AbstractRegSet<FloatRegister>::first() {
  uint32_t first = _bitset & -_bitset;
  return first ? as_FloatRegister(exact_log2(first)) : fnoreg;
}

template <>
inline FloatRegister AbstractRegSet<FloatRegister>::last() {
  if (_bitset == 0) { return fnoreg; }
  int last = max_size() - 1 - count_leading_zeros(_bitset);
  return as_FloatRegister(last);
}



class VFPSystemRegister {
 private:
  int _store_idx;

  constexpr explicit VFPSystemRegister(int store_idx) : _store_idx(store_idx) {}

  enum {
    _FPSID_store_idx = 0,
    _FPSCR_store_idx = 1,
    _MVFR0_store_idx = 2,
    _MVFR1_store_idx = 3
  };

 public:
  enum {
    FPSID = 0,
    FPSCR = 1,
    MVFR0 = 6,
    MVFR1 = 7,
    number_of_registers = 4
  };

  class VFPSystemRegisterImpl : public AbstractRegisterImpl {
    friend class VFPSystemRegister;

    int _encoding;

    static constexpr const VFPSystemRegisterImpl* first();

   public:
    constexpr VFPSystemRegisterImpl(int encoding) : _encoding(encoding) {}

    int   encoding() const { return _encoding; }
  };

  inline friend constexpr VFPSystemRegister as_VFPSystemRegister(int encoding);

  constexpr VFPSystemRegister() : _store_idx(-1) {} // vfpsnoreg

  int operator==(const VFPSystemRegister r) const { return _store_idx == r._store_idx; }
  int operator!=(const VFPSystemRegister r) const { return _store_idx != r._store_idx; }

  const VFPSystemRegisterImpl* operator->() const { return VFPSystemRegisterImpl::first() + _store_idx; }
};

extern VFPSystemRegister::VFPSystemRegisterImpl all_VFPSystemRegisterImpls[VFPSystemRegister::number_of_registers + 1] INTERNAL_VISIBILITY;

inline constexpr const VFPSystemRegister::VFPSystemRegisterImpl* VFPSystemRegister::VFPSystemRegisterImpl::first() {
  return all_VFPSystemRegisterImpls + 1;
}

constexpr VFPSystemRegister vfpsnoreg = VFPSystemRegister();

inline constexpr VFPSystemRegister as_VFPSystemRegister(int encoding) {
  switch (encoding) {
    case VFPSystemRegister::FPSID: return VFPSystemRegister(VFPSystemRegister::_FPSID_store_idx);
    case VFPSystemRegister::FPSCR: return VFPSystemRegister(VFPSystemRegister::_FPSCR_store_idx);
    case VFPSystemRegister::MVFR0: return VFPSystemRegister(VFPSystemRegister::_MVFR0_store_idx);
    case VFPSystemRegister::MVFR1: return VFPSystemRegister(VFPSystemRegister::_MVFR1_store_idx);
    default: return vfpsnoreg;
  }
}

constexpr VFPSystemRegister FPSID = as_VFPSystemRegister(VFPSystemRegister::FPSID);
constexpr VFPSystemRegister FPSCR = as_VFPSystemRegister(VFPSystemRegister::FPSCR);
constexpr VFPSystemRegister MVFR0 = as_VFPSystemRegister(VFPSystemRegister::MVFR0);
constexpr VFPSystemRegister MVFR1 = as_VFPSystemRegister(VFPSystemRegister::MVFR1);

/*
 * Register definitions shared across interpreter and compiler
 */
constexpr Register Rexception_obj = R4;
constexpr Register Rexception_pc = R5;

/*
 * Interpreter register definitions common to C++ and template interpreters.
 */
constexpr Register Rlocals = R8;
constexpr Register Rmethod = R9;
constexpr Register Rthread = R10;
constexpr Register Rtemp = R12;

// Interpreter calling conventions

constexpr Register Rparams = SP;
constexpr Register Rsender_sp = R4;

// JSR292
//  Note: R5_mh is needed only during the call setup, including adapters
//  This does not seem to conflict with Rexception_pc
//  In case of issues, R3 might be OK but adapters calling the runtime would have to save it
constexpr Register R5_mh = R5; // MethodHandle register, used during the call setup

/*
 * C++ Interpreter Register Defines
 */
constexpr Register Rsave0 = R4;
constexpr Register Rsave1 = R5;
constexpr Register Rsave2 = R6;
constexpr Register Rstate = altFP_7_11; // R7 or R11
constexpr Register Ricklass = R8;

/*
 * TemplateTable Interpreter Register Usage
 */

// Temporary registers
constexpr Register R0_tmp = R0;
constexpr Register R1_tmp = R1;
constexpr Register R2_tmp = R2;
constexpr Register R3_tmp = R3;
constexpr Register R4_tmp = R4;
constexpr Register R5_tmp = R5;
constexpr Register R12_tmp = R12;
constexpr Register LR_tmp = LR;

constexpr FloatRegister S0_tmp = S0;
constexpr FloatRegister S1_tmp = S1_reg;

constexpr FloatRegister D0_tmp = D0;
constexpr FloatRegister D1_tmp = D1;

// Temporary registers saved across VM calls (according to C calling conventions)
constexpr Register Rtmp_save0 = R4;
constexpr Register Rtmp_save1 = R5;

// Cached TOS value
constexpr Register R0_tos = R0;

constexpr Register R0_tos_lo = R0;
constexpr Register R1_tos_hi = R1;

constexpr FloatRegister S0_tos = S0;
constexpr FloatRegister D0_tos = D0;

// Dispatch table
constexpr Register RdispatchTable = R6;

// Bytecode pointer
constexpr Register Rbcp = altFP_7_11;

// Pre-loaded next bytecode for the dispatch
constexpr Register R3_bytecode = R3;

// Conventions between bytecode templates and stubs
constexpr Register R2_ClassCastException_obj = R2;
constexpr Register R4_ArrayIndexOutOfBounds_index = R4;

// Interpreter expression stack top
constexpr Register Rstack_top = SP;

/*
 * Linux 32-bit ARM C ABI Register calling conventions
 *
 *   REG         use                     callee/caller saved
 *
 *   R0         First argument reg            caller
 *              result register
 *   R1         Second argument reg           caller
 *              result register
 *   R2         Third argument reg            caller
 *   R3         Fourth argument reg           caller
 *
 *   R4 - R8    Local variable registers      callee
 *   R9
 *   R10, R11   Local variable registers      callee
 *
 *   R12 (IP)   Scratch register used in inter-procedural calling
 *   R13 (SP)   Stack Pointer                 callee
 *   R14 (LR)   Link register
 *   R15 (PC)   Program Counter
 */

constexpr Register c_rarg0 = R0;
constexpr Register c_rarg1 = R1;
constexpr Register c_rarg2 = R2;
constexpr Register c_rarg3 = R3;


#define GPR_PARAMS    4


// Java ABI
// XXX Is this correct?
constexpr Register j_rarg0 = c_rarg0;
constexpr Register j_rarg1 = c_rarg1;
constexpr Register j_rarg2 = c_rarg2;
constexpr Register j_rarg3 = c_rarg3;


#endif // CPU_ARM_REGISTER_ARM_HPP
