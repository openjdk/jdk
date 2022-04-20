/*
 * Copyright (c) 2000, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2021, Red Hat Inc. All rights reserved.
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

#ifndef CPU_AARCH64_REGISTER_AARCH64_HPP
#define CPU_AARCH64_REGISTER_AARCH64_HPP

#include "asm/register.hpp"
#include "utilities/powerOfTwo.hpp"

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
    number_of_registers         =   32,
    number_of_declared_registers  = 34,  // Including SP and ZR.
    max_slots_per_register = 2
  };

  // derived registers, offsets, and addresses
  const Register successor() const { return this + 1; }

  // construction
  inline friend constexpr Register as_Register(int encoding);

  VMReg as_VMReg() const;

  // accessors
  int encoding() const             { assert(is_valid(), "invalid register"); return encoding_nocheck(); }
  bool is_valid() const            { return (unsigned)encoding_nocheck() < number_of_registers; }
  const char* name() const;
  int encoding_nocheck() const     { return this - first(); }
};


REGISTER_IMPL_DECLARATION(Register, RegisterImpl, RegisterImpl::number_of_declared_registers);

// The integer registers of the aarch64 architecture

CONSTANT_REGISTER_DECLARATION(Register, noreg, (-1));

CONSTANT_REGISTER_DECLARATION(Register, r0,    (0));
CONSTANT_REGISTER_DECLARATION(Register, r1,    (1));
CONSTANT_REGISTER_DECLARATION(Register, r2,    (2));
CONSTANT_REGISTER_DECLARATION(Register, r3,    (3));
CONSTANT_REGISTER_DECLARATION(Register, r4,    (4));
CONSTANT_REGISTER_DECLARATION(Register, r5,    (5));
CONSTANT_REGISTER_DECLARATION(Register, r6,    (6));
CONSTANT_REGISTER_DECLARATION(Register, r7,    (7));
CONSTANT_REGISTER_DECLARATION(Register, r8,    (8));
CONSTANT_REGISTER_DECLARATION(Register, r9,    (9));
CONSTANT_REGISTER_DECLARATION(Register, r10,  (10));
CONSTANT_REGISTER_DECLARATION(Register, r11,  (11));
CONSTANT_REGISTER_DECLARATION(Register, r12,  (12));
CONSTANT_REGISTER_DECLARATION(Register, r13,  (13));
CONSTANT_REGISTER_DECLARATION(Register, r14,  (14));
CONSTANT_REGISTER_DECLARATION(Register, r15,  (15));
CONSTANT_REGISTER_DECLARATION(Register, r16,  (16));
CONSTANT_REGISTER_DECLARATION(Register, r17,  (17));

// In the ABI for Windows+AArch64 the register r18 is used to store the pointer
// to the current thread's TEB (where TLS variables are stored). We could
// carefully save and restore r18 at key places, however Win32 Structured
// Exception Handling (SEH) is using TLS to unwind the stack. If r18 is used
// for any other purpose at the time of an exception happening, SEH would not
// be able to unwind the stack properly and most likely crash.
//
// It's easier to avoid allocating r18 altogether.
//
// See https://docs.microsoft.com/en-us/cpp/build/arm64-windows-abi-conventions?view=vs-2019#integer-registers
CONSTANT_REGISTER_DECLARATION(Register, r18_tls,  (18));
CONSTANT_REGISTER_DECLARATION(Register, r19,  (19));
CONSTANT_REGISTER_DECLARATION(Register, r20,  (20));
CONSTANT_REGISTER_DECLARATION(Register, r21,  (21));
CONSTANT_REGISTER_DECLARATION(Register, r22,  (22));
CONSTANT_REGISTER_DECLARATION(Register, r23,  (23));
CONSTANT_REGISTER_DECLARATION(Register, r24,  (24));
CONSTANT_REGISTER_DECLARATION(Register, r25,  (25));
CONSTANT_REGISTER_DECLARATION(Register, r26,  (26));
CONSTANT_REGISTER_DECLARATION(Register, r27,  (27));
CONSTANT_REGISTER_DECLARATION(Register, r28,  (28));
CONSTANT_REGISTER_DECLARATION(Register, r29,  (29));
CONSTANT_REGISTER_DECLARATION(Register, r30,  (30));


// r31 is not a general purpose register, but represents either the
// stack pointer or the zero/discard register depending on the
// instruction.
CONSTANT_REGISTER_DECLARATION(Register, r31_sp, (31));
CONSTANT_REGISTER_DECLARATION(Register, zr,  (32));
CONSTANT_REGISTER_DECLARATION(Register, sp,  (33));

// Used as a filler in instructions where a register field is unused.
const Register dummy_reg = r31_sp;

// Use FloatRegister as shortcut
class FloatRegisterImpl;
typedef const FloatRegisterImpl* FloatRegister;

inline constexpr FloatRegister as_FloatRegister(int encoding);

// The implementation of floating point registers for the architecture
class FloatRegisterImpl: public AbstractRegisterImpl {
  static constexpr FloatRegister first();

public:
  enum {
    number_of_registers = 32,
    max_slots_per_register = 8,
    save_slots_per_register = 2,
    slots_per_neon_register = 4,
    extra_save_slots_per_neon_register = slots_per_neon_register - save_slots_per_register
  };

  // construction
  inline friend constexpr FloatRegister as_FloatRegister(int encoding);

  VMReg as_VMReg() const;

  // derived registers, offsets, and addresses
  FloatRegister successor() const {
    return as_FloatRegister((encoding() + 1) % (unsigned)number_of_registers);
  }

  // accessors
  int encoding() const             { assert(is_valid(), "invalid register"); return encoding_nocheck(); }
  bool is_valid() const            { return (unsigned)encoding_nocheck() < number_of_registers; }
  const char* name() const;
  int encoding_nocheck() const     { return this - first(); }
};

REGISTER_IMPL_DECLARATION(FloatRegister, FloatRegisterImpl, FloatRegisterImpl::number_of_registers);


// The float registers of the AARCH64 architecture

CONSTANT_REGISTER_DECLARATION(FloatRegister, fnoreg , (-1));

CONSTANT_REGISTER_DECLARATION(FloatRegister, v0     , ( 0));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v1     , ( 1));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v2     , ( 2));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v3     , ( 3));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v4     , ( 4));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v5     , ( 5));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v6     , ( 6));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v7     , ( 7));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v8     , ( 8));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v9     , ( 9));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v10    , (10));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v11    , (11));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v12    , (12));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v13    , (13));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v14    , (14));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v15    , (15));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v16    , (16));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v17    , (17));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v18    , (18));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v19    , (19));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v20    , (20));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v21    , (21));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v22    , (22));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v23    , (23));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v24    , (24));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v25    , (25));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v26    , (26));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v27    , (27));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v28    , (28));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v29    , (29));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v30    , (30));
CONSTANT_REGISTER_DECLARATION(FloatRegister, v31    , (31));

// SVE vector registers, shared with the SIMD&FP v0-v31. Vn maps to Zn[127:0].
CONSTANT_REGISTER_DECLARATION(FloatRegister, z0     , ( 0));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z1     , ( 1));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z2     , ( 2));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z3     , ( 3));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z4     , ( 4));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z5     , ( 5));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z6     , ( 6));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z7     , ( 7));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z8     , ( 8));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z9     , ( 9));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z10    , (10));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z11    , (11));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z12    , (12));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z13    , (13));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z14    , (14));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z15    , (15));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z16    , (16));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z17    , (17));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z18    , (18));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z19    , (19));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z20    , (20));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z21    , (21));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z22    , (22));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z23    , (23));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z24    , (24));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z25    , (25));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z26    , (26));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z27    , (27));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z28    , (28));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z29    , (29));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z30    , (30));
CONSTANT_REGISTER_DECLARATION(FloatRegister, z31    , (31));


class PRegisterImpl;
typedef const PRegisterImpl* PRegister;
inline constexpr PRegister as_PRegister(int encoding);

// The implementation of predicate registers for the architecture
class PRegisterImpl: public AbstractRegisterImpl {
  static constexpr PRegister first();

 public:
  enum {
    number_of_registers = 16,
    number_of_governing_registers = 8,
    // p0-p7 are governing predicates for load/store and arithmetic, but p7 is
    // preserved as an all-true predicate in OpenJDK. And since we don't support
    // non-governing predicate registers allocation for non-temp register, the
    // predicate registers to be saved are p0-p6.
    number_of_saved_registers = number_of_governing_registers - 1,
    max_slots_per_register = 1
  };

  // construction
  inline friend constexpr PRegister as_PRegister(int encoding);

  VMReg as_VMReg() const;

  // derived registers, offsets, and addresses
  PRegister successor() const     { return this + 1; }

  // accessors
  int encoding() const            { assert(is_valid(), "invalid register"); return encoding_nocheck(); }
  int encoding_nocheck() const    { return this - first(); }
  bool is_valid() const           { return (unsigned)encoding_nocheck() < number_of_registers; }
  bool is_governing() const       { return first() <= this && this - first() < number_of_governing_registers; }
  const char* name() const;
};


REGISTER_IMPL_DECLARATION(PRegister, PRegisterImpl, PRegisterImpl::number_of_registers);

// The predicate registers of SVE.
CONSTANT_REGISTER_DECLARATION(PRegister, p0,  ( 0));
CONSTANT_REGISTER_DECLARATION(PRegister, p1,  ( 1));
CONSTANT_REGISTER_DECLARATION(PRegister, p2,  ( 2));
CONSTANT_REGISTER_DECLARATION(PRegister, p3,  ( 3));
CONSTANT_REGISTER_DECLARATION(PRegister, p4,  ( 4));
CONSTANT_REGISTER_DECLARATION(PRegister, p5,  ( 5));
CONSTANT_REGISTER_DECLARATION(PRegister, p6,  ( 6));
CONSTANT_REGISTER_DECLARATION(PRegister, p7,  ( 7));
CONSTANT_REGISTER_DECLARATION(PRegister, p8,  ( 8));
CONSTANT_REGISTER_DECLARATION(PRegister, p9,  ( 9));
CONSTANT_REGISTER_DECLARATION(PRegister, p10, (10));
CONSTANT_REGISTER_DECLARATION(PRegister, p11, (11));
CONSTANT_REGISTER_DECLARATION(PRegister, p12, (12));
CONSTANT_REGISTER_DECLARATION(PRegister, p13, (13));
CONSTANT_REGISTER_DECLARATION(PRegister, p14, (14));
CONSTANT_REGISTER_DECLARATION(PRegister, p15, (15));

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
                           PRegisterImpl::max_slots_per_register * PRegisterImpl::number_of_registers +
                           1) // flags
  };

  // added to make it compile
  static const int max_gpr;
  static const int max_fpr;
  static const int max_pr;
};

typedef AbstractRegSet<Register> RegSet;
typedef AbstractRegSet<FloatRegister> FloatRegSet;
typedef AbstractRegSet<PRegister> PRegSet;

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

inline Register as_Register(FloatRegister reg) {
  return as_Register(reg->encoding());
}

#endif // CPU_AARCH64_REGISTER_AARCH64_HPP
