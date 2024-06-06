/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_REGISTER_X86_HPP
#define CPU_X86_REGISTER_X86_HPP

#include "asm/register.hpp"
#include "runtime/globals.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/count_leading_zeros.hpp"
#include "utilities/powerOfTwo.hpp"

class VMRegImpl;
typedef VMRegImpl* VMReg;

// The implementation of integer registers for the x86/x64 architectures.
class Register {
private:
  int _encoding;

  constexpr Register(int encoding, bool unused) : _encoding(encoding) {}

public:
  inline friend constexpr Register as_Register(int encoding);

  enum {
    number_of_registers      = LP64_ONLY( 16 ) NOT_LP64( 8 ),
    number_of_byte_registers = LP64_ONLY( 16 ) NOT_LP64( 4 ),
    max_slots_per_register   = LP64_ONLY(  2 ) NOT_LP64( 1 )
  };

  class RegisterImpl: public AbstractRegisterImpl {
    friend class Register;

    static constexpr const RegisterImpl* first();

  public:
    // accessors
    constexpr int   raw_encoding() const { return checked_cast<int>(this - first()); }
    constexpr int       encoding() const { assert(is_valid(), "invalid register"); return raw_encoding(); }
    constexpr bool      is_valid() const { return 0 <= raw_encoding() && raw_encoding() < number_of_registers; }
    bool  has_byte_register() const { return 0 <= raw_encoding() && raw_encoding() < number_of_byte_registers; }

    // derived registers, offsets, and addresses
    inline Register successor() const;

    inline VMReg as_VMReg() const;

    const char* name() const;
  };

  constexpr Register() : _encoding(-1) {} // noreg

  int operator==(const Register r) const { return _encoding == r._encoding; }
  int operator!=(const Register r) const { return _encoding != r._encoding; }

  constexpr const RegisterImpl* operator->() const { return RegisterImpl::first() + _encoding; }
};

extern const Register::RegisterImpl all_RegisterImpls[Register::number_of_registers + 1] INTERNAL_VISIBILITY;

inline constexpr const Register::RegisterImpl* Register::RegisterImpl::first() {
  return all_RegisterImpls + 1;
}

constexpr Register noreg = Register();

inline constexpr Register as_Register(int encoding) {
  if (0 <= encoding && encoding < Register::number_of_registers) {
    return Register(encoding, false);
  }
  return noreg;
}

inline Register Register::RegisterImpl::successor() const {
  assert(is_valid(), "sanity");
  return as_Register(encoding() + 1);
}

constexpr Register rax = as_Register(0);
constexpr Register rcx = as_Register(1);
constexpr Register rdx = as_Register(2);
constexpr Register rbx = as_Register(3);
constexpr Register rsp = as_Register(4);
constexpr Register rbp = as_Register(5);
constexpr Register rsi = as_Register(6);
constexpr Register rdi = as_Register(7);
#ifdef _LP64
constexpr Register r8  = as_Register( 8);
constexpr Register r9  = as_Register( 9);
constexpr Register r10 = as_Register(10);
constexpr Register r11 = as_Register(11);
constexpr Register r12 = as_Register(12);
constexpr Register r13 = as_Register(13);
constexpr Register r14 = as_Register(14);
constexpr Register r15 = as_Register(15);
#endif // _LP64


// The implementation of x87 floating point registers for the ia32 architecture.
class FloatRegister {
private:
  int _encoding;

  constexpr FloatRegister(int encoding, bool unused) : _encoding(encoding) {}

public:
  inline friend constexpr FloatRegister as_FloatRegister(int encoding);

  enum {
    number_of_registers    = 8,
    max_slots_per_register = 2
  };

  class FloatRegisterImpl: public AbstractRegisterImpl {
    friend class FloatRegister;

    static constexpr const FloatRegisterImpl* first();

  public:
    // accessors
    int   raw_encoding() const { return checked_cast<int>(this - first()); }
    int   encoding() const     { assert(is_valid(), "invalid register"); return raw_encoding(); }
    bool  is_valid() const     { return 0 <= raw_encoding() && raw_encoding() < number_of_registers; }

    // derived registers, offsets, and addresses
    inline FloatRegister successor() const;

    inline VMReg as_VMReg() const;

    const char* name() const;
  };

  constexpr FloatRegister() : _encoding(-1) {} // fnoreg

  int operator==(const FloatRegister r) const { return _encoding == r._encoding; }
  int operator!=(const FloatRegister r) const { return _encoding != r._encoding; }

  const FloatRegisterImpl* operator->() const { return FloatRegisterImpl::first() + _encoding; }
};

extern const FloatRegister::FloatRegisterImpl all_FloatRegisterImpls[FloatRegister::number_of_registers + 1] INTERNAL_VISIBILITY;

inline constexpr const FloatRegister::FloatRegisterImpl* FloatRegister::FloatRegisterImpl::first() {
  return all_FloatRegisterImpls + 1;
}

constexpr FloatRegister fnoreg = FloatRegister();

inline constexpr FloatRegister as_FloatRegister(int encoding) {
  if (0 <= encoding && encoding < FloatRegister::number_of_registers) {
    return FloatRegister(encoding, false);
  }
  return fnoreg;
}

inline FloatRegister FloatRegister::FloatRegisterImpl::successor() const {
  assert(is_valid(), "sanity");
  return as_FloatRegister(encoding() + 1);
}


// The implementation of XMM registers.
class XMMRegister {
private:
  int _encoding;

  constexpr XMMRegister(int encoding, bool unused) : _encoding(encoding) {}

public:
  inline friend constexpr XMMRegister as_XMMRegister(int encoding);

  enum {
    number_of_registers    = LP64_ONLY( 32 ) NOT_LP64(  8 ),
    max_slots_per_register = LP64_ONLY( 16 ) NOT_LP64( 16 )   // 512-bit
  };

  class XMMRegisterImpl: public AbstractRegisterImpl {
    friend class XMMRegister;

    static constexpr const XMMRegisterImpl* first();

  public:
    // accessors
    constexpr int raw_encoding() const { return checked_cast<int>(this - first()); }
    constexpr int     encoding() const { assert(is_valid(), "invalid register"); return raw_encoding(); }
    constexpr bool    is_valid() const { return 0 <= raw_encoding() && raw_encoding() < number_of_registers; }

    // derived registers, offsets, and addresses
    inline XMMRegister successor() const;

    inline VMReg as_VMReg() const;

    const char* name() const;
  };

  constexpr XMMRegister() : _encoding(-1) {} // xnoreg

  int operator==(const XMMRegister r) const { return _encoding == r._encoding; }
  int operator!=(const XMMRegister r) const { return _encoding != r._encoding; }

  constexpr const XMMRegisterImpl* operator->() const { return XMMRegisterImpl::first() + _encoding; }

  // Actually available XMM registers for use, depending on actual CPU capabilities and flags.
  static int available_xmm_registers() {
#ifdef _LP64
    if (UseAVX < 3) {
      return number_of_registers / 2;
    }
#endif // _LP64
    return number_of_registers;
  }
};

extern const XMMRegister::XMMRegisterImpl all_XMMRegisterImpls[XMMRegister::number_of_registers + 1] INTERNAL_VISIBILITY;

inline constexpr const XMMRegister::XMMRegisterImpl* XMMRegister::XMMRegisterImpl::first() {
  return all_XMMRegisterImpls + 1;
}

constexpr XMMRegister xnoreg = XMMRegister();

inline constexpr XMMRegister as_XMMRegister(int encoding) {
  if (0 <= encoding && encoding < XMMRegister::number_of_registers) {
    return XMMRegister(encoding, false);
  }
  return xnoreg;
}

inline XMMRegister XMMRegister::XMMRegisterImpl::successor() const {
  assert(is_valid(), "sanity");
  return as_XMMRegister(encoding() + 1);
}

constexpr XMMRegister xmm0  = as_XMMRegister( 0);
constexpr XMMRegister xmm1  = as_XMMRegister( 1);
constexpr XMMRegister xmm2  = as_XMMRegister( 2);
constexpr XMMRegister xmm3  = as_XMMRegister( 3);
constexpr XMMRegister xmm4  = as_XMMRegister( 4);
constexpr XMMRegister xmm5  = as_XMMRegister( 5);
constexpr XMMRegister xmm6  = as_XMMRegister( 6);
constexpr XMMRegister xmm7  = as_XMMRegister( 7);
#ifdef _LP64
constexpr XMMRegister xmm8  = as_XMMRegister( 8);
constexpr XMMRegister xmm9  = as_XMMRegister( 9);
constexpr XMMRegister xmm10 = as_XMMRegister(10);
constexpr XMMRegister xmm11 = as_XMMRegister(11);
constexpr XMMRegister xmm12 = as_XMMRegister(12);
constexpr XMMRegister xmm13 = as_XMMRegister(13);
constexpr XMMRegister xmm14 = as_XMMRegister(14);
constexpr XMMRegister xmm15 = as_XMMRegister(15);
constexpr XMMRegister xmm16 = as_XMMRegister(16);
constexpr XMMRegister xmm17 = as_XMMRegister(17);
constexpr XMMRegister xmm18 = as_XMMRegister(18);
constexpr XMMRegister xmm19 = as_XMMRegister(19);
constexpr XMMRegister xmm20 = as_XMMRegister(20);
constexpr XMMRegister xmm21 = as_XMMRegister(21);
constexpr XMMRegister xmm22 = as_XMMRegister(22);
constexpr XMMRegister xmm23 = as_XMMRegister(23);
constexpr XMMRegister xmm24 = as_XMMRegister(24);
constexpr XMMRegister xmm25 = as_XMMRegister(25);
constexpr XMMRegister xmm26 = as_XMMRegister(26);
constexpr XMMRegister xmm27 = as_XMMRegister(27);
constexpr XMMRegister xmm28 = as_XMMRegister(28);
constexpr XMMRegister xmm29 = as_XMMRegister(29);
constexpr XMMRegister xmm30 = as_XMMRegister(30);
constexpr XMMRegister xmm31 = as_XMMRegister(31);
#endif // _LP64


// The implementation of AVX-512 opmask registers.
class KRegister {
private:
  int _encoding;

  constexpr KRegister(int encoding, bool unused) : _encoding(encoding) {}

public:
  inline friend constexpr KRegister as_KRegister(int encoding);

  enum {
    number_of_registers = 8,
    // opmask registers are 64bit wide on both 32 and 64 bit targets.
    // thus two slots are reserved per register.
    max_slots_per_register = 2
  };

  class KRegisterImpl: public AbstractRegisterImpl {
    friend class KRegister;

    static constexpr const KRegisterImpl* first();

  public:

    // accessors
    int   raw_encoding() const { return checked_cast<int>(this - first()); }
    int   encoding() const     { assert(is_valid(), "invalid register"); return raw_encoding(); }
    bool  is_valid() const     { return 0 <= raw_encoding() && raw_encoding() < number_of_registers; }

    // derived registers, offsets, and addresses
    inline KRegister successor() const;

    inline VMReg as_VMReg() const;

    const char* name() const;
  };

  constexpr KRegister() : _encoding(-1) {} // knoreg

  int operator==(const KRegister r) const { return _encoding == r._encoding; }
  int operator!=(const KRegister r) const { return _encoding != r._encoding; }

  const KRegisterImpl* operator->() const { return KRegisterImpl::first() + _encoding; }
};

extern const KRegister::KRegisterImpl all_KRegisterImpls[KRegister::number_of_registers + 1] INTERNAL_VISIBILITY;

inline constexpr const KRegister::KRegisterImpl* KRegister::KRegisterImpl::first() {
  return all_KRegisterImpls + 1;
}

constexpr KRegister knoreg = KRegister();

inline constexpr KRegister as_KRegister(int encoding) {
  if (0 <= encoding && encoding < KRegister::number_of_registers) {
    return KRegister(encoding, false);
  }
  return knoreg;
}

inline KRegister KRegister::KRegisterImpl::successor() const {
  assert(is_valid(), "sanity");
  return as_KRegister(encoding() + 1);
}

constexpr KRegister k0 = as_KRegister(0);
constexpr KRegister k1 = as_KRegister(1);
constexpr KRegister k2 = as_KRegister(2);
constexpr KRegister k3 = as_KRegister(3);
constexpr KRegister k4 = as_KRegister(4);
constexpr KRegister k5 = as_KRegister(5);
constexpr KRegister k6 = as_KRegister(6);
constexpr KRegister k7 = as_KRegister(7);


// Need to know the total number of registers of all sorts for SharedInfo.
// Define a class that exports it.
class ConcreteRegisterImpl : public AbstractRegisterImpl {
 public:
  enum {
    max_gpr = Register::number_of_registers * Register::max_slots_per_register,
    max_fpr = max_gpr + FloatRegister::number_of_registers * FloatRegister::max_slots_per_register,
    max_xmm = max_fpr + XMMRegister::number_of_registers * XMMRegister::max_slots_per_register,
    max_kpr = max_xmm + KRegister::number_of_registers * KRegister::max_slots_per_register,

    // A big enough number for C2: all the registers plus flags
    // This number must be large enough to cover REG_COUNT (defined by c2) registers.
    // There is no requirement that any ordering here matches any ordering c2 gives
    // it's optoregs.

    // x86_32.ad defines additional dummy FILL0-FILL7 registers, in order to tally
    // REG_COUNT (computed by ADLC based on the number of reg_defs seen in .ad files)
    // with ConcreteRegisterImpl::number_of_registers additional count of 8 is being
    // added for 32 bit jvm.
    number_of_registers = max_kpr +       // gpr/fpr/xmm/kpr
                          NOT_LP64( 8 + ) // FILL0-FILL7 in x86_32.ad
                          1               // eflags
  };
};

template <>
inline Register AbstractRegSet<Register>::first() {
  if (_bitset == 0) { return noreg; }
  return as_Register(count_trailing_zeros(_bitset));
}

template <>
inline Register AbstractRegSet<Register>::last() {
  if (_bitset == 0) { return noreg; }
  int last = max_size() - 1 - count_leading_zeros(_bitset);
  return as_Register(last);
}

template <>
inline XMMRegister AbstractRegSet<XMMRegister>::first() {
  if (_bitset == 0) { return xnoreg; }
  return as_XMMRegister(count_trailing_zeros(_bitset));
}

template <>
inline XMMRegister AbstractRegSet<XMMRegister>::last() {
  if (_bitset == 0) { return xnoreg; }
  int last = max_size() - 1 - count_leading_zeros(_bitset);
  return as_XMMRegister(last);
}

typedef AbstractRegSet<Register> RegSet;
typedef AbstractRegSet<XMMRegister> XMMRegSet;

#endif // CPU_X86_REGISTER_X86_HPP
