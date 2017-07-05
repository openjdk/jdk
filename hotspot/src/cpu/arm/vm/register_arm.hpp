/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_ARM_VM_REGISTER_ARM_HPP
#define CPU_ARM_VM_REGISTER_ARM_HPP

#include "asm/register.hpp"
#include "vm_version_arm.hpp"

class VMRegImpl;
typedef VMRegImpl* VMReg;

// These are declared ucontext.h
#undef R0
#undef R1
#undef R2
#undef R3
#undef R4
#undef R5
#undef R6
#undef R7
#undef R8
#undef R9
#undef R10
#undef R11
#undef R12
#undef R13
#undef R14
#undef R15

#define R(r)   ((Register)(r))

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

#ifndef AARCH64
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
#endif // AARCH64

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

#define R0     ((Register)0)
#define R1     ((Register)1)
#define R2     ((Register)2)
#define R3     ((Register)3)
#define R4     ((Register)4)
#define R5     ((Register)5)
#define R6     ((Register)6)
#define R7     ((Register)7)
#define R8     ((Register)8)
#define R9     ((Register)9)
#define R10    ((Register)10)
#define R11    ((Register)11)
#define R12    ((Register)12)
#define R13    ((Register)13)
#define R14    ((Register)14)
#define R15    ((Register)15)

#ifdef AARCH64

#define R16    ((Register)16)
#define R17    ((Register)17)
#define R18    ((Register)18)
#define R19    ((Register)19)
#define R20    ((Register)20)
#define R21    ((Register)21)
#define R22    ((Register)22)
#define R23    ((Register)23)
#define R24    ((Register)24)
#define R25    ((Register)25)
#define R26    ((Register)26)
#define R27    ((Register)27)
#define R28    ((Register)28)
#define R29    ((Register)29)
#define R30    ((Register)30)
#define ZR     ((Register)31)
#define SP     ((Register)32)

#define FP     R29
#define LR     R30

#define altFP_7_11 R7

#else // !AARCH64

#define FP     ((Register)FP_REG_NUM)

// Safe use of registers which may be FP on some platforms.
//
// altFP_7_11: R7 if not equal to FP, else R11 (the default FP)
//
// Note: add additional altFP_#_11 for each register potentially used
// as FP on supported ABIs (and replace R# by altFP_#_11). altFP_#_11
// must be #define to R11 if and only if # is FP_REG_NUM.
#if (FP_REG_NUM == 7)
#define altFP_7_11     ((Register)11)
#else
#define altFP_7_11     ((Register)7)
#endif
#define SP     R13
#define LR     R14
#define PC     R15

#endif // !AARCH64


class RegisterImpl;
typedef RegisterImpl* Register;

inline Register as_Register(int encoding) {
  return (Register)(intptr_t)encoding;
}

class RegisterImpl : public AbstractRegisterImpl {
 public:
  enum {
#ifdef AARCH64
    number_of_gprs = 31,
    zr_sp_encoding = 31,
#endif
    number_of_registers = AARCH64_ONLY(number_of_gprs + 2) NOT_AARCH64(16)
  };

  Register successor() const      { return as_Register(encoding() + 1); }

  inline friend Register as_Register(int encoding);

  VMReg as_VMReg();

  // accessors
  int   encoding() const          { assert(is_valid(), "invalid register"); return value(); }
  const char* name() const;

#ifdef AARCH64
  int encoding_with_zr() const   { assert (is_valid_gpr_or_zr(), "invalid register"); return (this == ZR) ? zr_sp_encoding : value(); }
  int encoding_with_sp() const   { assert (is_valid_gpr_or_sp(), "invalid register"); return (this == SP) ? zr_sp_encoding : value(); }
#endif

  // testers
  bool is_valid() const           { return 0 <= value() && value() < number_of_registers; }

#ifdef AARCH64
  bool is_valid_gpr()       const  { return (0 <= value() && value() < number_of_gprs); }
  bool is_valid_gpr_or_zr() const  { return is_valid_gpr() || (this == ZR); }
  bool is_valid_gpr_or_sp() const  { return is_valid_gpr() || (this == SP); }
#endif
};

CONSTANT_REGISTER_DECLARATION(Register, noreg, (-1));


// Use FloatRegister as shortcut
class FloatRegisterImpl;
typedef FloatRegisterImpl* FloatRegister;

inline FloatRegister as_FloatRegister(int encoding) {
  return (FloatRegister)(intptr_t)encoding;
}

class FloatRegisterImpl : public AbstractRegisterImpl {
 public:
  enum {
#ifdef AARCH64
    number_of_registers = 32
#else
    number_of_registers = NOT_COMPILER2(32) COMPILER2_PRESENT(64)
#endif
  };

  inline friend FloatRegister as_FloatRegister(int encoding);

  VMReg as_VMReg();

  int   encoding() const          { assert(is_valid(), "invalid register"); return value(); }
  bool  is_valid() const          { return 0 <= (intx)this && (intx)this < number_of_registers; }
  FloatRegister successor() const { return as_FloatRegister(encoding() + 1); }

  const char* name() const;

#ifndef AARCH64
  int hi_bits() const {
    return (encoding() >> 1) & 0xf;
  }

  int lo_bit() const {
    return encoding() & 1;
  }

  int hi_bit() const {
    return encoding() >> 5;
  }
#endif // !AARCH64
};

CONSTANT_REGISTER_DECLARATION(FloatRegister, fnoreg, (-1));

#ifdef AARCH64

CONSTANT_REGISTER_DECLARATION(FloatRegister, V0,     ( 0));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V1,     ( 1));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V2,     ( 2));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V3,     ( 3));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V4,     ( 4));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V5,     ( 5));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V6,     ( 6));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V7,     ( 7));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V8,     ( 8));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V9,     ( 9));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V10,    (10));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V11,    (11));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V12,    (12));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V13,    (13));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V14,    (14));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V15,    (15));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V16,    (16));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V17,    (17));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V18,    (18));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V19,    (19));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V20,    (20));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V21,    (21));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V22,    (22));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V23,    (23));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V24,    (24));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V25,    (25));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V26,    (26));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V27,    (27));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V28,    (28));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V29,    (29));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V30,    (30));
CONSTANT_REGISTER_DECLARATION(FloatRegister, V31,    (31));

#define S0       V0
#define S1_reg   V1
#define Stemp    V31

#define D0       V0
#define D1       V1

#else // AARCH64

/*
 * S1-S6 are named with "_reg" suffix to avoid conflict with
 * constants defined in sharedRuntimeTrig.cpp
 */
CONSTANT_REGISTER_DECLARATION(FloatRegister, S0,     ( 0));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S1_reg, ( 1));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S2_reg, ( 2));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S3_reg, ( 3));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S4_reg, ( 4));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S5_reg, ( 5));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S6_reg, ( 6));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S7,     ( 7));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S8,     ( 8));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S9,     ( 9));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S10,    (10));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S11,    (11));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S12,    (12));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S13,    (13));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S14,    (14));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S15,    (15));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S16,    (16));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S17,    (17));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S18,    (18));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S19,    (19));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S20,    (20));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S21,    (21));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S22,    (22));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S23,    (23));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S24,    (24));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S25,    (25));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S26,    (26));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S27,    (27));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S28,    (28));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S29,    (29));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S30,    (30));
CONSTANT_REGISTER_DECLARATION(FloatRegister, S31,    (31));
CONSTANT_REGISTER_DECLARATION(FloatRegister, Stemp,  (30));

CONSTANT_REGISTER_DECLARATION(FloatRegister, D0,     ( 0));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D1,     ( 2));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D2,     ( 4));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D3,     ( 6));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D4,     ( 8));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D5,     ( 10));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D6,     ( 12));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D7,     ( 14));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D8,     ( 16));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D9,     ( 18));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D10,    ( 20));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D11,    ( 22));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D12,    ( 24));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D13,    ( 26));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D14,    ( 28));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D15,    (30));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D16,    (32));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D17,    (34));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D18,    (36));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D19,    (38));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D20,    (40));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D21,    (42));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D22,    (44));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D23,    (46));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D24,    (48));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D25,    (50));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D26,    (52));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D27,    (54));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D28,    (56));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D29,    (58));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D30,    (60));
CONSTANT_REGISTER_DECLARATION(FloatRegister, D31,    (62));

#endif // AARCH64

class ConcreteRegisterImpl : public AbstractRegisterImpl {
 public:
  enum {
    log_vmregs_per_word = LogBytesPerWord - LogBytesPerInt, // VMRegs are of 4-byte size
#ifdef COMPILER2
    log_bytes_per_fpr  = AARCH64_ONLY(4) NOT_AARCH64(2), // quad vectors
#else
    log_bytes_per_fpr  = AARCH64_ONLY(3) NOT_AARCH64(2), // double vectors
#endif
    log_words_per_fpr  = log_bytes_per_fpr - LogBytesPerWord,
    words_per_fpr      = 1 << log_words_per_fpr,
    log_vmregs_per_fpr = log_bytes_per_fpr - LogBytesPerInt,
    log_vmregs_per_gpr = log_vmregs_per_word,
    vmregs_per_gpr = 1 << log_vmregs_per_gpr,
    vmregs_per_fpr = 1 << log_vmregs_per_fpr,

    num_gpr  = RegisterImpl::number_of_registers << log_vmregs_per_gpr,
    max_gpr0 = num_gpr,
    num_fpr  = FloatRegisterImpl::number_of_registers << log_vmregs_per_fpr,
    max_fpr0 = max_gpr0 + num_fpr,
    number_of_registers = num_gpr + num_fpr +
                          // TODO-AARCH64 revise
                          1+1 // APSR and FPSCR so that c2's REG_COUNT <= ConcreteRegisterImpl::number_of_registers
  };

  static const int max_gpr;
  static const int max_fpr;
};

// TODO-AARCH64 revise the following definitions

class VFPSystemRegisterImpl;
typedef VFPSystemRegisterImpl* VFPSystemRegister;
class VFPSystemRegisterImpl : public AbstractRegisterImpl {
 public:
  int   encoding() const          { return value(); }
};

#define FPSID     ((VFPSystemRegister)0)
#define FPSCR     ((VFPSystemRegister)1)
#define MVFR0     ((VFPSystemRegister)0x6)
#define MVFR1     ((VFPSystemRegister)0x7)

/*
 * Register definitions shared across interpreter and compiler
 */
#define Rexception_obj   AARCH64_ONLY(R19) NOT_AARCH64(R4)
#define Rexception_pc    AARCH64_ONLY(R20) NOT_AARCH64(R5)

#ifdef AARCH64
#define Rheap_base       R27
#endif // AARCH64

/*
 * Interpreter register definitions common to C++ and template interpreters.
 */
#ifdef AARCH64
#define Rlocals          R23
#define Rmethod          R26
#define Rthread          R28
#define Rtemp            R16
#define Rtemp2           R17
#else
#define Rlocals          R8
#define Rmethod          R9
#define Rthread          R10
#define Rtemp            R12
#endif // AARCH64

// Interpreter calling conventions

#define Rparams          AARCH64_ONLY(R8)  NOT_AARCH64(SP)
#define Rsender_sp       AARCH64_ONLY(R19) NOT_AARCH64(R4)

// JSR292
//  Note: R5_mh is needed only during the call setup, including adapters
//  This does not seem to conflict with Rexception_pc
//  In case of issues, R3 might be OK but adapters calling the runtime would have to save it
#define R5_mh            R5 // MethodHandle register, used during the call setup
#define Rmh_SP_save      FP // for C1

/*
 * C++ Interpreter Register Defines
 */
#define Rsave0   R4
#define Rsave1   R5
#define Rsave2   R6
#define Rstate   altFP_7_11 // R7 or R11
#define Ricklass R8

/*
 * TemplateTable Interpreter Register Usage
 */

// Temporary registers
#define R0_tmp                 R0
#define R1_tmp                 R1
#define R2_tmp                 R2
#define R3_tmp                 R3
#define R4_tmp                 R4
#define R5_tmp                 R5
#define R12_tmp                R12
#define LR_tmp                 LR

#define S0_tmp                 S0
#define S1_tmp                 S1_reg

#define D0_tmp                 D0
#define D1_tmp                 D1

// Temporary registers saved across VM calls (according to C calling conventions)
#define Rtmp_save0             AARCH64_ONLY(R19) NOT_AARCH64(R4)
#define Rtmp_save1             AARCH64_ONLY(R20) NOT_AARCH64(R5)

// Cached TOS value
#define R0_tos                 R0

#ifndef AARCH64
#define R0_tos_lo              R0
#define R1_tos_hi              R1
#endif

#define S0_tos                 S0
#define D0_tos                 D0

// Dispatch table
#define RdispatchTable         AARCH64_ONLY(R22) NOT_AARCH64(R6)

// Bytecode pointer
#define Rbcp                   AARCH64_ONLY(R24) NOT_AARCH64(altFP_7_11)

// Pre-loaded next bytecode for the dispatch
#define R3_bytecode            R3

// Conventions between bytecode templates and stubs
#define R2_ClassCastException_obj        R2
#define R4_ArrayIndexOutOfBounds_index   R4

// Interpreter expression stack top
#define Rstack_top             AARCH64_ONLY(R25) NOT_AARCH64(SP)

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
 *
 * TODO-AARCH64: document AArch64 ABI
 *
 */
#define c_rarg0  R0
#define c_rarg1  R1
#define c_rarg2  R2
#define c_rarg3  R3

#ifdef AARCH64
#define c_rarg4  R4
#define c_rarg5  R5
#define c_rarg6  R6
#define c_rarg7  R7
#endif

#ifdef AARCH64
#define GPR_PARAMS    8
#define FPR_PARAMS    8
#else
#define GPR_PARAMS    4
#endif


// Java ABI
// XXX Is this correct?
#define j_rarg0  c_rarg0
#define j_rarg1  c_rarg1
#define j_rarg2  c_rarg2
#define j_rarg3  c_rarg3

#ifdef AARCH64
#define j_rarg4  c_rarg4
#define j_rarg5  c_rarg5
#define j_rarg6  c_rarg6
#define j_rarg7  c_rarg7
#endif

#endif // CPU_ARM_VM_REGISTER_ARM_HPP
