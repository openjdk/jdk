/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CODE_VMREG_HPP
#define SHARE_CODE_VMREG_HPP

#include "asm/register.hpp"
#include "code/vmregTypes.hpp"
#include "runtime/globals.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#ifdef COMPILER2
#include "opto/adlcVMDeps.hpp"
#endif

//------------------------------VMReg------------------------------------------
// The VM uses 'unwarped' stack slots; the compiler uses 'warped' stack slots.
// Register numbers below VMRegImpl::stack0 are the same for both.  Register
// numbers above stack0 are either warped (in the compiler) or unwarped
// (in the VM).  Unwarped numbers represent stack indices, offsets from
// the current stack pointer.  Warped numbers are required during compilation
// when we do not yet know how big the frame will be.

class VMRegImpl;
typedef VMRegImpl* VMReg;

class VMRegImpl {
// friend class OopMap;
friend class VMStructs;
friend class OptoReg;
// friend class Location;
private:
  enum {
    BAD_REG = -1,
    FIRST_STACK = (ConcreteRegisterImpl::number_of_registers + 7) & ~7
  };

  // Despite being private, this field is exported to the
  // serviceability agent and our friends. It's not really a pointer,
  // but that's fine and dandy as long as no-one tries to dereference
  // it.
  static VMReg stack0;

  static constexpr VMReg first();
  // Names for registers
  static const char *regName[];
  static const int register_count;

public:

  static constexpr VMReg stack_0() {
    return first() + FIRST_STACK;
  }

  static VMReg as_VMReg(int val, bool bad_ok = false) {
    assert(val > BAD_REG || bad_ok, "invalid");
    return val + first();
  }

  const char* name() {
    if (is_reg()) {
      return regName[value()];
    } else if (!is_valid()) {
      return "BAD";
    } else {
      // shouldn't really be called with stack
      return "STACKED REG";
    }
  }
  int value() const { return checked_cast<int>(this - first()); }
  static VMReg Bad() { return BAD_REG+first(); }
  bool is_valid() const { return value() != BAD_REG; }
  bool is_stack() const { return this >= stack_0(); }
  bool is_reg()   const { return is_valid() && !is_stack(); }

  // A concrete register is a value that returns true for is_reg() and is
  // also a register you could use in the assembler. On machines with
  // 64bit registers only one half of the VMReg (and OptoReg) is considered
  // concrete.
  //  bool is_concrete();

  // VMRegs are 4 bytes wide on all platforms
  static const int stack_slot_size;
  static const int slots_per_word;


  // This really ought to check that the register is "real" in the sense that
  // we don't try and get the VMReg number of a physical register that doesn't
  // have an expressible part. That would be pd specific code
  VMReg next() {
    assert((is_reg() && this < stack_0() - 1) || is_stack(), "must be");
    return this + 1;
  }
  VMReg next(int i) {
    assert((is_reg() && this < stack_0() - i) || is_stack(), "must be");
    return this + i;
  }
  VMReg prev() {
    assert((is_stack() && this > stack_0()) || (is_reg() && value() != 0), "must be");
    return this - 1;
  }


  void print_on(outputStream* st) const;
  void print() const;

  // bias a stack slot.
  // Typically used to adjust a virtual frame slots by amounts that are offset by
  // amounts that are part of the native abi. The VMReg must be a stack slot
  // and the result must be also.

  VMReg bias(int offset) const {
    assert(is_stack(), "must be");
    // VMReg res = VMRegImpl::as_VMReg(value() + offset);
    VMReg res = stack2reg(reg2stack() + offset);
    assert(res->is_stack(), "must be");
    return res;
  }

  // Convert register numbers to stack slots and vice versa
  static VMReg stack2reg(int idx) {
    return stack_0() + idx;
  }

  int reg2stack() const {
    assert(is_stack(), "Not a stack-based register");
    return checked_cast<int>(this - stack_0());
  }

  static void set_regName();

#include CPU_HEADER(vmreg)

};

extern VMRegImpl all_VMRegs[ConcreteRegisterImpl::number_of_registers + 1] INTERNAL_VISIBILITY;
inline constexpr VMReg VMRegImpl::first() { return all_VMRegs + 1; }

//---------------------------VMRegPair-------------------------------------------
// Pairs of 32-bit registers for arguments.
// SharedRuntime::java_calling_convention will overwrite the structs with
// the calling convention's registers.  VMRegImpl::Bad is returned for any
// unused 32-bit register.  This happens for the unused high half of Int
// arguments, or for 32-bit pointers or for longs in the 32-bit sparc build
// (which are passed to natives in low 32-bits of e.g. O0/O1 and the high
// 32-bits of O0/O1 are set to VMRegImpl::Bad).  Longs in one register & doubles
// always return a high and a low register, as do 64-bit pointers.
//
class VMRegPair {
private:
  VMReg _second;
  VMReg _first;
public:
  void set_bad (                   ) { _second=VMRegImpl::Bad(); _first=VMRegImpl::Bad(); }
  void set1    (         VMReg v  ) { _second=VMRegImpl::Bad(); _first=v; }
  void set2    (         VMReg v  ) { _second=v->next();  _first=v; }
  void set_pair( VMReg second, VMReg first    ) { _second= second;    _first= first; }
  void set_ptr ( VMReg ptr ) {
#ifdef _LP64
    _second = ptr->next();
#else
    _second = VMRegImpl::Bad();
#endif
    _first = ptr;
  }
  // Return true if single register, even if the pair is really just adjacent stack slots
  bool is_single_reg() const {
    return (_first->is_valid()) && (_first->value() + 1 == _second->value());
  }

  // Return true if single stack based "register" where the slot alignment matches input alignment
  bool is_adjacent_on_stack(int alignment) const {
    return (_first->is_stack() && (_first->value() + 1 == _second->value()) && ((_first->value() & (alignment-1)) == 0));
  }

  // Return true if single stack based "register" where the slot alignment matches input alignment
  bool is_adjacent_aligned_on_stack(int alignment) const {
    return (_first->is_stack() && (_first->value() + 1 == _second->value()) && ((_first->value() & (alignment-1)) == 0));
  }

  // Return true if single register but adjacent stack slots do not count
  bool is_single_phys_reg() const {
    return (_first->is_reg() && (_first->value() + 1 == _second->value()));
  }

  VMReg second() const { return _second; }
  VMReg first()  const { return _first; }
  VMRegPair(VMReg s, VMReg f) {  _second = s; _first = f; }
  VMRegPair(VMReg f) { _second = VMRegImpl::Bad(); _first = f; }
  VMRegPair() { _second = VMRegImpl::Bad(); _first = VMRegImpl::Bad(); }
};

#endif // SHARE_CODE_VMREG_HPP
