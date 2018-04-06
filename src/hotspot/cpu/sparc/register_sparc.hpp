/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_REGISTER_SPARC_HPP
#define CPU_SPARC_VM_REGISTER_SPARC_HPP

#include "asm/register.hpp"

// forward declaration
class Address;
class VMRegImpl;
typedef VMRegImpl* VMReg;


// Use Register as shortcut
class RegisterImpl;
typedef RegisterImpl* Register;


inline Register as_Register(int encoding) {
  return (Register)(intptr_t) encoding;
}

// The implementation of integer registers for the SPARC architecture
class RegisterImpl: public AbstractRegisterImpl {
 public:
  enum {
    log_set_size        = 3,                          // the number of bits to encode the set register number
    number_of_sets      = 4,                          // the number of registers sets (in, local, out, global)
    number_of_registers = number_of_sets << log_set_size,

    iset_no = 3,  ibase = iset_no << log_set_size,    // the in     register set
    lset_no = 2,  lbase = lset_no << log_set_size,    // the local  register set
    oset_no = 1,  obase = oset_no << log_set_size,    // the output register set
    gset_no = 0,  gbase = gset_no << log_set_size     // the global register set
  };


  friend Register as_Register(int encoding);
  // set specific construction
  friend Register as_iRegister(int number);
  friend Register as_lRegister(int number);
  friend Register as_oRegister(int number);
  friend Register as_gRegister(int number);

  inline VMReg as_VMReg();

  // accessors
  int   encoding() const                              { assert(is_valid(), "invalid register"); return value(); }
  const char* name() const;

  // testers
  bool is_valid() const                               { return (0 <= (value()&0x7F) && (value()&0x7F) < number_of_registers); }
  bool is_even() const                                { return (encoding() & 1) == 0; }
  bool is_in() const                                  { return (encoding() >> log_set_size) == iset_no; }
  bool is_local() const                               { return (encoding() >> log_set_size) == lset_no; }
  bool is_out() const                                 { return (encoding() >> log_set_size) == oset_no; }
  bool is_global() const                              { return (encoding() >> log_set_size) == gset_no; }

  // derived registers, offsets, and addresses
  Register successor() const                          { return as_Register(encoding() + 1); }

  int input_number() const {
    assert(is_in(), "must be input register");
    return encoding() - ibase;
  }

  Register after_save() const {
    assert(is_out() || is_global(), "register not visible after save");
    return is_out() ? as_Register(encoding() + (ibase - obase)) : (const Register)this;
  }

  Register after_restore() const {
    assert(is_in() || is_global(), "register not visible after restore");
    return is_in() ? as_Register(encoding() + (obase - ibase)) : (const Register)this;
  }

  int sp_offset_in_saved_window() const {
    assert(is_in() || is_local(), "only i and l registers are saved in frame");
    return encoding() - lbase;
  }

  inline Address address_in_saved_window() const;     // implemented in assembler_sparc.hpp
};


// set specific construction
inline Register as_iRegister(int number)            { return as_Register(RegisterImpl::ibase + number); }
inline Register as_lRegister(int number)            { return as_Register(RegisterImpl::lbase + number); }
inline Register as_oRegister(int number)            { return as_Register(RegisterImpl::obase + number); }
inline Register as_gRegister(int number)            { return as_Register(RegisterImpl::gbase + number); }

// The integer registers of the SPARC architecture

CONSTANT_REGISTER_DECLARATION(Register, noreg , (-1));

CONSTANT_REGISTER_DECLARATION(Register, G0    , (RegisterImpl::gbase + 0));
CONSTANT_REGISTER_DECLARATION(Register, G1    , (RegisterImpl::gbase + 1));
CONSTANT_REGISTER_DECLARATION(Register, G2    , (RegisterImpl::gbase + 2));
CONSTANT_REGISTER_DECLARATION(Register, G3    , (RegisterImpl::gbase + 3));
CONSTANT_REGISTER_DECLARATION(Register, G4    , (RegisterImpl::gbase + 4));
CONSTANT_REGISTER_DECLARATION(Register, G5    , (RegisterImpl::gbase + 5));
CONSTANT_REGISTER_DECLARATION(Register, G6    , (RegisterImpl::gbase + 6));
CONSTANT_REGISTER_DECLARATION(Register, G7    , (RegisterImpl::gbase + 7));

CONSTANT_REGISTER_DECLARATION(Register, O0    , (RegisterImpl::obase + 0));
CONSTANT_REGISTER_DECLARATION(Register, O1    , (RegisterImpl::obase + 1));
CONSTANT_REGISTER_DECLARATION(Register, O2    , (RegisterImpl::obase + 2));
CONSTANT_REGISTER_DECLARATION(Register, O3    , (RegisterImpl::obase + 3));
CONSTANT_REGISTER_DECLARATION(Register, O4    , (RegisterImpl::obase + 4));
CONSTANT_REGISTER_DECLARATION(Register, O5    , (RegisterImpl::obase + 5));
CONSTANT_REGISTER_DECLARATION(Register, O6    , (RegisterImpl::obase + 6));
CONSTANT_REGISTER_DECLARATION(Register, O7    , (RegisterImpl::obase + 7));

CONSTANT_REGISTER_DECLARATION(Register, L0    , (RegisterImpl::lbase + 0));
CONSTANT_REGISTER_DECLARATION(Register, L1    , (RegisterImpl::lbase + 1));
CONSTANT_REGISTER_DECLARATION(Register, L2    , (RegisterImpl::lbase + 2));
CONSTANT_REGISTER_DECLARATION(Register, L3    , (RegisterImpl::lbase + 3));
CONSTANT_REGISTER_DECLARATION(Register, L4    , (RegisterImpl::lbase + 4));
CONSTANT_REGISTER_DECLARATION(Register, L5    , (RegisterImpl::lbase + 5));
CONSTANT_REGISTER_DECLARATION(Register, L6    , (RegisterImpl::lbase + 6));
CONSTANT_REGISTER_DECLARATION(Register, L7    , (RegisterImpl::lbase + 7));

CONSTANT_REGISTER_DECLARATION(Register, I0    , (RegisterImpl::ibase + 0));
CONSTANT_REGISTER_DECLARATION(Register, I1    , (RegisterImpl::ibase + 1));
CONSTANT_REGISTER_DECLARATION(Register, I2    , (RegisterImpl::ibase + 2));
CONSTANT_REGISTER_DECLARATION(Register, I3    , (RegisterImpl::ibase + 3));
CONSTANT_REGISTER_DECLARATION(Register, I4    , (RegisterImpl::ibase + 4));
CONSTANT_REGISTER_DECLARATION(Register, I5    , (RegisterImpl::ibase + 5));
CONSTANT_REGISTER_DECLARATION(Register, I6    , (RegisterImpl::ibase + 6));
CONSTANT_REGISTER_DECLARATION(Register, I7    , (RegisterImpl::ibase + 7));

CONSTANT_REGISTER_DECLARATION(Register, FP    , (RegisterImpl::ibase + 6));
CONSTANT_REGISTER_DECLARATION(Register, SP    , (RegisterImpl::obase + 6));

// Use FloatRegister as shortcut
class FloatRegisterImpl;
typedef FloatRegisterImpl* FloatRegister;


// construction
inline FloatRegister as_FloatRegister(int encoding) {
  return (FloatRegister)(intptr_t)encoding;
}

// The implementation of float registers for the SPARC architecture

class FloatRegisterImpl: public AbstractRegisterImpl {
 public:
  enum {
    number_of_registers = 64
  };

  enum Width {
    S = 1,  D = 2,  Q = 3
  };

  // construction
  inline VMReg as_VMReg( );

  // accessors
  int encoding() const { assert(is_valid(), "invalid register"); return value(); }

 public:
  int encoding(Width w) const {
    const int c = encoding();
    switch (w) {
      case S:
        assert(c < 32, "bad single float register");
        return c;

      case D:
        assert(c < 64  &&  (c & 1) == 0, "bad double float register");
        return (c & 0x1e) | ((c & 0x20) >> 5);

      case Q:
        assert(c < 64  &&  (c & 3) == 0, "bad quad float register");
        return (c & 0x1c) | ((c & 0x20) >> 5);
    }
    ShouldNotReachHere();
    return -1;
  }

  bool is_valid() const { return 0 <= value() && value() < number_of_registers; }
  bool is_even()  const { return (encoding() & 1) == 0; }

  const char* name() const;

  FloatRegister successor() const { return as_FloatRegister(encoding() + 1); }
};


// The float registers of the SPARC architecture

CONSTANT_REGISTER_DECLARATION(FloatRegister, fnoreg , (-1));

CONSTANT_REGISTER_DECLARATION(FloatRegister, F0     , ( 0));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F1     , ( 1));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F2     , ( 2));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F3     , ( 3));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F4     , ( 4));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F5     , ( 5));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F6     , ( 6));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F7     , ( 7));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F8     , ( 8));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F9     , ( 9));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F10    , (10));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F11    , (11));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F12    , (12));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F13    , (13));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F14    , (14));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F15    , (15));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F16    , (16));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F17    , (17));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F18    , (18));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F19    , (19));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F20    , (20));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F21    , (21));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F22    , (22));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F23    , (23));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F24    , (24));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F25    , (25));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F26    , (26));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F27    , (27));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F28    , (28));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F29    , (29));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F30    , (30));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F31    , (31));

CONSTANT_REGISTER_DECLARATION(FloatRegister, F32    , (32));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F34    , (34));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F36    , (36));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F38    , (38));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F40    , (40));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F42    , (42));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F44    , (44));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F46    , (46));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F48    , (48));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F50    , (50));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F52    , (52));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F54    , (54));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F56    , (56));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F58    , (58));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F60    , (60));
CONSTANT_REGISTER_DECLARATION(FloatRegister, F62    , (62));

// Maximum number of incoming arguments that can be passed in i registers.
const int SPARC_ARGS_IN_REGS_NUM = 6;

class ConcreteRegisterImpl : public AbstractRegisterImpl {
 public:
  enum {
    // This number must be large enough to cover REG_COUNT (defined by c2) registers.
    // There is no requirement that any ordering here matches any ordering c2 gives
    // it's optoregs.
    number_of_registers = 2*RegisterImpl::number_of_registers +
                            FloatRegisterImpl::number_of_registers +
                            1 + // ccr
                            4  //  fcc
  };
  static const int max_gpr;
  static const int max_fpr;

};

// Single, Double and Quad fp reg classes.  These exist to map the ADLC
// encoding for a floating point register, to the FloatRegister number
// desired by the macroassembler.  A FloatRegister is a number between
// 0 and 63 passed around as a pointer.  For ADLC, an fp register encoding
// is the actual bit encoding used by the sparc hardware.  When ADLC used
// the macroassembler to generate an instruction that references, e.g., a
// double fp reg, it passed the bit encoding to the macroassembler via
// as_FloatRegister, which, for double regs > 30, returns an illegal
// register number.
//
// Therefore we provide the following classes for use by ADLC.  Their
// sole purpose is to convert from sparc register encodings to FloatRegisters.
// At some future time, we might replace FloatRegister with these classes,
// hence the definitions of as_xxxFloatRegister as class methods rather
// than as external inline routines.

class SingleFloatRegisterImpl;
typedef SingleFloatRegisterImpl *SingleFloatRegister;

inline FloatRegister as_SingleFloatRegister(int encoding);
class SingleFloatRegisterImpl {
 public:
  friend inline FloatRegister as_SingleFloatRegister(int encoding) {
    assert(encoding < 32, "bad single float register encoding");
    return as_FloatRegister(encoding);
  }
};


class DoubleFloatRegisterImpl;
typedef DoubleFloatRegisterImpl *DoubleFloatRegister;

inline FloatRegister as_DoubleFloatRegister(int encoding);
class DoubleFloatRegisterImpl {
 public:
  friend inline FloatRegister as_DoubleFloatRegister(int encoding) {
    assert(encoding < 32, "bad double float register encoding");
    return as_FloatRegister( ((encoding & 1) << 5) | (encoding & 0x1e) );
  }
};


class QuadFloatRegisterImpl;
typedef QuadFloatRegisterImpl *QuadFloatRegister;

class QuadFloatRegisterImpl {
 public:
  friend FloatRegister as_QuadFloatRegister(int encoding) {
    assert(encoding < 32 && ((encoding & 2) == 0), "bad quad float register encoding");
    return as_FloatRegister( ((encoding & 1) << 5) | (encoding & 0x1c) );
  }
};

#endif // CPU_SPARC_VM_REGISTER_SPARC_HPP
