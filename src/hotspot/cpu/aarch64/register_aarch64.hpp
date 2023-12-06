/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/checkedCast.hpp"
#include "utilities/powerOfTwo.hpp"

class VMRegImpl;
typedef VMRegImpl* VMReg;

class Register {
 private:
  int _encoding;

  constexpr explicit Register(int encoding) : _encoding(encoding) {}

 public:
  enum {
    number_of_registers          = 32,
    number_of_declared_registers = 34,  // Including SP and ZR.
    max_slots_per_register       =  2
  };

  class RegisterImpl: public AbstractRegisterImpl {
    friend class Register;

    static constexpr const RegisterImpl* first();

   public:
    // accessors
    constexpr int raw_encoding() const { return checked_cast<int>(this - first()); }
    constexpr int     encoding() const { assert(is_valid(), "invalid register"); return raw_encoding(); }
    constexpr bool    is_valid() const { return 0 <= raw_encoding() && raw_encoding() < number_of_registers; }

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

extern Register::RegisterImpl all_RegisterImpls[Register::number_of_declared_registers + 1] INTERNAL_VISIBILITY;

inline constexpr const Register::RegisterImpl* Register::RegisterImpl::first() {
  return all_RegisterImpls + 1;
}

constexpr Register noreg = Register();

inline constexpr Register as_Register(int encoding) {
  if (0 <= encoding && encoding < Register::number_of_declared_registers) {
    return Register(encoding);
  }
  return noreg;
}

inline Register Register::RegisterImpl::successor() const {
  assert(is_valid(), "sanity");
  return as_Register(encoding() + 1);
}

// The integer registers of the AArch64 architecture
constexpr Register r0  = as_Register( 0);
constexpr Register r1  = as_Register( 1);
constexpr Register r2  = as_Register( 2);
constexpr Register r3  = as_Register( 3);
constexpr Register r4  = as_Register( 4);
constexpr Register r5  = as_Register( 5);
constexpr Register r6  = as_Register( 6);
constexpr Register r7  = as_Register( 7);
constexpr Register r8  = as_Register( 8);
constexpr Register r9  = as_Register( 9);
constexpr Register r10 = as_Register(10);
constexpr Register r11 = as_Register(11);
constexpr Register r12 = as_Register(12);
constexpr Register r13 = as_Register(13);
constexpr Register r14 = as_Register(14);
constexpr Register r15 = as_Register(15);
constexpr Register r16 = as_Register(16);
constexpr Register r17 = as_Register(17);

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
constexpr Register r18_tls = as_Register(18);
constexpr Register r19     = as_Register(19);
constexpr Register r20     = as_Register(20);
constexpr Register r21     = as_Register(21);
constexpr Register r22     = as_Register(22);
constexpr Register r23     = as_Register(23);
constexpr Register r24     = as_Register(24);
constexpr Register r25     = as_Register(25);
constexpr Register r26     = as_Register(26);
constexpr Register r27     = as_Register(27);
constexpr Register r28     = as_Register(28);
constexpr Register r29     = as_Register(29);
constexpr Register r30     = as_Register(30);


// r31 is not a general purpose register, but represents either the
// stack pointer or the zero/discard register depending on the
// instruction.
constexpr Register r31_sp = as_Register(31);
constexpr Register zr     = as_Register(32);
constexpr Register sp     = as_Register(33);

// Used as a filler in instructions where a register field is unused.
constexpr Register dummy_reg = r31_sp;


// The implementation of floating point registers for the architecture
class FloatRegister {
 private:
  int _encoding;

  constexpr explicit FloatRegister(int encoding) : _encoding(encoding) {}

 public:
  inline friend constexpr FloatRegister as_FloatRegister(int encoding);

  enum {
    number_of_registers     = 32,
    max_slots_per_register  =  4,
    save_slots_per_register =  2,
    slots_per_neon_register =  4,
    extra_save_slots_per_neon_register = slots_per_neon_register - save_slots_per_register
  };

  class FloatRegisterImpl: public AbstractRegisterImpl {
    friend class FloatRegister;

    static constexpr const FloatRegisterImpl* first();

   public:
    // accessors
    constexpr int raw_encoding() const { return checked_cast<int>(this - first()); }
    constexpr int     encoding() const { assert(is_valid(), "invalid register"); return raw_encoding(); }
    constexpr bool    is_valid() const { return 0 <= raw_encoding() && raw_encoding() < number_of_registers; }

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
  return as_FloatRegister((encoding() + 1) % number_of_registers);
}

// The float registers of the AArch64 architecture
constexpr FloatRegister v0  = as_FloatRegister( 0);
constexpr FloatRegister v1  = as_FloatRegister( 1);
constexpr FloatRegister v2  = as_FloatRegister( 2);
constexpr FloatRegister v3  = as_FloatRegister( 3);
constexpr FloatRegister v4  = as_FloatRegister( 4);
constexpr FloatRegister v5  = as_FloatRegister( 5);
constexpr FloatRegister v6  = as_FloatRegister( 6);
constexpr FloatRegister v7  = as_FloatRegister( 7);
constexpr FloatRegister v8  = as_FloatRegister( 8);
constexpr FloatRegister v9  = as_FloatRegister( 9);
constexpr FloatRegister v10 = as_FloatRegister(10);
constexpr FloatRegister v11 = as_FloatRegister(11);
constexpr FloatRegister v12 = as_FloatRegister(12);
constexpr FloatRegister v13 = as_FloatRegister(13);
constexpr FloatRegister v14 = as_FloatRegister(14);
constexpr FloatRegister v15 = as_FloatRegister(15);
constexpr FloatRegister v16 = as_FloatRegister(16);
constexpr FloatRegister v17 = as_FloatRegister(17);
constexpr FloatRegister v18 = as_FloatRegister(18);
constexpr FloatRegister v19 = as_FloatRegister(19);
constexpr FloatRegister v20 = as_FloatRegister(20);
constexpr FloatRegister v21 = as_FloatRegister(21);
constexpr FloatRegister v22 = as_FloatRegister(22);
constexpr FloatRegister v23 = as_FloatRegister(23);
constexpr FloatRegister v24 = as_FloatRegister(24);
constexpr FloatRegister v25 = as_FloatRegister(25);
constexpr FloatRegister v26 = as_FloatRegister(26);
constexpr FloatRegister v27 = as_FloatRegister(27);
constexpr FloatRegister v28 = as_FloatRegister(28);
constexpr FloatRegister v29 = as_FloatRegister(29);
constexpr FloatRegister v30 = as_FloatRegister(30);
constexpr FloatRegister v31 = as_FloatRegister(31);

// SVE vector registers, shared with the SIMD&FP v0-v31. Vn maps to Zn[127:0].
constexpr FloatRegister z0  = v0;
constexpr FloatRegister z1  = v1;
constexpr FloatRegister z2  = v2;
constexpr FloatRegister z3  = v3;
constexpr FloatRegister z4  = v4;
constexpr FloatRegister z5  = v5;
constexpr FloatRegister z6  = v6;
constexpr FloatRegister z7  = v7;
constexpr FloatRegister z8  = v8;
constexpr FloatRegister z9  = v9;
constexpr FloatRegister z10 = v10;
constexpr FloatRegister z11 = v11;
constexpr FloatRegister z12 = v12;
constexpr FloatRegister z13 = v13;
constexpr FloatRegister z14 = v14;
constexpr FloatRegister z15 = v15;
constexpr FloatRegister z16 = v16;
constexpr FloatRegister z17 = v17;
constexpr FloatRegister z18 = v18;
constexpr FloatRegister z19 = v19;
constexpr FloatRegister z20 = v20;
constexpr FloatRegister z21 = v21;
constexpr FloatRegister z22 = v22;
constexpr FloatRegister z23 = v23;
constexpr FloatRegister z24 = v24;
constexpr FloatRegister z25 = v25;
constexpr FloatRegister z26 = v26;
constexpr FloatRegister z27 = v27;
constexpr FloatRegister z28 = v28;
constexpr FloatRegister z29 = v29;
constexpr FloatRegister z30 = v30;
constexpr FloatRegister z31 = v31;


// The implementation of predicate registers for the architecture
class PRegister {
  int _encoding;

  constexpr explicit PRegister(int encoding) : _encoding(encoding) {}

public:
  inline friend constexpr PRegister as_PRegister(int encoding);

  enum {
    number_of_registers = 16,
    number_of_governing_registers = 8,
    max_slots_per_register = 1
  };

  constexpr PRegister() : _encoding(-1) {} // pnoreg

  class PRegisterImpl: public AbstractRegisterImpl {
    friend class PRegister;

    static constexpr const PRegisterImpl* first();

   public:
    // accessors
    int raw_encoding() const  { return checked_cast<int>(this - first()); }
    int encoding() const      { assert(is_valid(), "invalid register"); return raw_encoding(); }
    bool is_valid() const     { return 0 <= raw_encoding() && raw_encoding() < number_of_registers; }
    bool is_governing() const { return 0 <= raw_encoding() && raw_encoding() < number_of_governing_registers; }

    // derived registers, offsets, and addresses
    inline PRegister successor() const;

    VMReg as_VMReg() const;

    const char* name() const;
  };

  int operator==(const PRegister r) const { return _encoding == r._encoding; }
  int operator!=(const PRegister r) const { return _encoding != r._encoding; }

  const PRegisterImpl* operator->() const { return PRegisterImpl::first() + _encoding; }
};

extern PRegister::PRegisterImpl all_PRegisterImpls[PRegister::number_of_registers + 1] INTERNAL_VISIBILITY;

inline constexpr const PRegister::PRegisterImpl* PRegister::PRegisterImpl::first() {
  return all_PRegisterImpls + 1;
}

constexpr PRegister pnoreg = PRegister();

inline constexpr PRegister as_PRegister(int encoding) {
  if (0 <= encoding && encoding < PRegister::number_of_registers) {
    return PRegister(encoding);
  }
  return pnoreg;
}

inline PRegister PRegister::PRegisterImpl::successor() const {
  assert(is_valid(), "sanity");
  return as_PRegister(encoding() + 1);
}

// The predicate registers of SVE.
constexpr PRegister p0  = as_PRegister( 0);
constexpr PRegister p1  = as_PRegister( 1);
constexpr PRegister p2  = as_PRegister( 2);
constexpr PRegister p3  = as_PRegister( 3);
constexpr PRegister p4  = as_PRegister( 4);
constexpr PRegister p5  = as_PRegister( 5);
constexpr PRegister p6  = as_PRegister( 6);
constexpr PRegister p7  = as_PRegister( 7);
constexpr PRegister p8  = as_PRegister( 8);
constexpr PRegister p9  = as_PRegister( 9);
constexpr PRegister p10 = as_PRegister(10);
constexpr PRegister p11 = as_PRegister(11);
constexpr PRegister p12 = as_PRegister(12);
constexpr PRegister p13 = as_PRegister(13);
constexpr PRegister p14 = as_PRegister(14);
constexpr PRegister p15 = as_PRegister(15);

// Need to know the total number of registers of all sorts for SharedInfo.
// Define a class that exports it.
class ConcreteRegisterImpl : public AbstractRegisterImpl {
 public:
  enum {
    max_gpr = Register::number_of_registers * Register::max_slots_per_register,
    max_fpr = max_gpr + FloatRegister::number_of_registers * FloatRegister::max_slots_per_register,
    max_pr  = max_fpr + PRegister::number_of_registers * PRegister::max_slots_per_register,

    // A big enough number for C2: all the registers plus flags
    // This number must be large enough to cover REG_COUNT (defined by c2) registers.
    // There is no requirement that any ordering here matches any ordering c2 gives
    // it's optoregs.
    number_of_registers = max_pr + 1 // gpr/fpr/pr + flags
  };
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
