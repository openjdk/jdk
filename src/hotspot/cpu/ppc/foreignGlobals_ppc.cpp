/*
 * Copyright (c) 2020, 2023, SAP SE. All rights reserved.
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "code/vmreg.inline.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "oops/oopCast.inline.hpp"
#include "prims/foreignGlobals.hpp"
#include "prims/foreignGlobals.inline.hpp"
#include "prims/vmstorage.hpp"
#include "utilities/formatBuffer.hpp"

#define __ masm->

bool ABIDescriptor::is_volatile_reg(Register reg) const {
  return _integer_argument_registers.contains(reg)
    || _integer_additional_volatile_registers.contains(reg);
}

bool ABIDescriptor::is_volatile_reg(FloatRegister reg) const {
    return _float_argument_registers.contains(reg)
        || _float_additional_volatile_registers.contains(reg);
}

bool ForeignGlobals::is_foreign_linker_supported() {
  return true;
}

// Stubbed out, implement later
const ABIDescriptor ForeignGlobals::parse_abi_descriptor(jobject jabi) {
  oop abi_oop = JNIHandles::resolve_non_null(jabi);
  ABIDescriptor abi;

  objArrayOop inputStorage = jdk_internal_foreign_abi_ABIDescriptor::inputStorage(abi_oop);
  parse_register_array(inputStorage, StorageType::INTEGER, abi._integer_argument_registers, as_Register);
  parse_register_array(inputStorage, StorageType::FLOAT, abi._float_argument_registers, as_FloatRegister);

  objArrayOop outputStorage = jdk_internal_foreign_abi_ABIDescriptor::outputStorage(abi_oop);
  parse_register_array(outputStorage, StorageType::INTEGER, abi._integer_return_registers, as_Register);
  parse_register_array(outputStorage, StorageType::FLOAT, abi._float_return_registers, as_FloatRegister);

  objArrayOop volatileStorage = jdk_internal_foreign_abi_ABIDescriptor::volatileStorage(abi_oop);
  parse_register_array(volatileStorage, StorageType::INTEGER, abi._integer_additional_volatile_registers, as_Register);
  parse_register_array(volatileStorage, StorageType::FLOAT, abi._float_additional_volatile_registers, as_FloatRegister);

  abi._stack_alignment_bytes = jdk_internal_foreign_abi_ABIDescriptor::stackAlignment(abi_oop);
  abi._shadow_space_bytes = jdk_internal_foreign_abi_ABIDescriptor::shadowSpace(abi_oop);

  abi._scratch1 = parse_vmstorage(jdk_internal_foreign_abi_ABIDescriptor::scratch1(abi_oop));
  abi._scratch2 = parse_vmstorage(jdk_internal_foreign_abi_ABIDescriptor::scratch2(abi_oop));

  return abi;
}

int RegSpiller::pd_reg_size(VMStorage reg) {
  if (reg.type() == StorageType::INTEGER || reg.type() == StorageType::FLOAT) {
    return 8;
  }
  return 0; // stack and BAD
}

void RegSpiller::pd_store_reg(MacroAssembler* masm, int offset, VMStorage reg) {
  if (reg.type() == StorageType::INTEGER) {
    __ std(as_Register(reg), offset, R1_SP);
  } else if (reg.type() == StorageType::FLOAT) {
    __ stfd(as_FloatRegister(reg), offset, R1_SP);
  } else {
    // stack and BAD
  }
}

void RegSpiller::pd_load_reg(MacroAssembler* masm, int offset, VMStorage reg) {
  if (reg.type() == StorageType::INTEGER) {
    __ ld(as_Register(reg), offset, R1_SP);
  } else if (reg.type() == StorageType::FLOAT) {
    __ lfd(as_FloatRegister(reg), offset, R1_SP);
  } else {
    // stack and BAD
  }
}

static int reg2offset(VMStorage vms, int stk_bias) {
  assert(!vms.is_reg(), "wrong usage");
  return vms.index_or_offset() + stk_bias;
}

static void move_reg64(MacroAssembler* masm, int out_stk_bias,
                       VMStorage from_reg, VMStorage to_reg) {
  int out_bias = 0;
  switch (to_reg.type()) {
    case StorageType::INTEGER:
      if (to_reg.segment_mask() == REG64_MASK && from_reg.segment_mask() == REG32_MASK) {
        // see CCallingConventionRequiresIntsAsLongs
        __ extsw(as_Register(to_reg), as_Register(from_reg));
      } else {
        __ mr_if_needed(as_Register(to_reg), as_Register(from_reg));
      }
      break;
    case StorageType::FLOAT:
      // FP arguments can get passed in GP reg! (Only in Upcall with HFA usage.)
      assert(from_reg.segment_mask() == to_reg.segment_mask(), "sanity");
      if (to_reg.segment_mask() == REG32_MASK) {
        __ stw(as_Register(from_reg), -8, R1_SP);
        __ lfs(as_FloatRegister(to_reg), -8, R1_SP); // convert to double precision format
      } else {
        if (VM_Version::has_mtfprd()) {
          __ mtfprd(as_FloatRegister(to_reg), as_Register(from_reg));
        } else {
          __ std(as_Register(from_reg), -8, R1_SP);
          __ lfd(as_FloatRegister(to_reg), -8, R1_SP);
        }
      }
      break;
    case StorageType::STACK:
      out_bias = out_stk_bias; // fallthrough
    case StorageType::FRAME_DATA: {
      // Integer types always get a 64 bit slot in C.
      Register storeval = as_Register(from_reg);
      if (from_reg.segment_mask() == REG32_MASK) {
        // see CCallingConventionRequiresIntsAsLongs
        __ extsw(R0, as_Register(from_reg));
        storeval = R0;
      }
      switch (to_reg.stack_size()) {
        case 8: __ std(storeval, reg2offset(to_reg, out_bias), R1_SP); break;
        case 4: __ stw(storeval, reg2offset(to_reg, out_bias), R1_SP); break;
        default: ShouldNotReachHere();
      }
    } break;
    default: ShouldNotReachHere();
  }
}

static void move_float(MacroAssembler* masm, int out_stk_bias,
                       VMStorage from_reg, VMStorage to_reg) {
  switch (to_reg.type()) {
    case StorageType::INTEGER:
      // FP arguments can get passed in GP reg! (Only for VarArgs for which we don't use FP regs.)
      assert(from_reg.segment_mask() == to_reg.segment_mask(), "sanity");
      if (from_reg.segment_mask() == REG32_MASK) {
        __ stfs(as_FloatRegister(from_reg), -8, R1_SP); // convert to single precision format
        __ lwa(as_Register(to_reg), -8, R1_SP);
      } else {
        if (VM_Version::has_mtfprd()) {
          __ mffprd(as_Register(to_reg), as_FloatRegister(from_reg));
        } else {
          __ stfd(as_FloatRegister(from_reg), -8, R1_SP);
          __ ld(as_Register(to_reg), -8, R1_SP);
        }
      }
      break;
    case StorageType::FLOAT:
      __ fmr_if_needed(as_FloatRegister(to_reg), as_FloatRegister(from_reg));
      break;
    case StorageType::STACK:
      if (from_reg.segment_mask() == REG32_MASK) {
        assert(to_reg.stack_size() == 4, "size should match");
        // TODO: Check if AIX needs 4 Byte offset
        __ stfs(as_FloatRegister(from_reg), reg2offset(to_reg, out_stk_bias), R1_SP);
      } else {
        assert(to_reg.stack_size() == 8, "size should match");
        __ stfd(as_FloatRegister(from_reg), reg2offset(to_reg, out_stk_bias), R1_SP);
      }
      break;
    default: ShouldNotReachHere();
  }
}

static void move_stack(MacroAssembler* masm, Register callerSP, int in_stk_bias, int out_stk_bias,
                       VMStorage from_reg, VMStorage to_reg) {
  int out_bias = 0;
  switch (to_reg.type()) {
    case StorageType::INTEGER:
      switch (from_reg.stack_size()) {
        case 8: __ ld( as_Register(to_reg), reg2offset(from_reg, in_stk_bias), callerSP); break;
        case 4: __ lwa(as_Register(to_reg), reg2offset(from_reg, in_stk_bias), callerSP); break;
        default: ShouldNotReachHere();
      }
      break;
    case StorageType::FLOAT:
      switch (from_reg.stack_size()) {
        case 8: __ lfd(as_FloatRegister(to_reg), reg2offset(from_reg, in_stk_bias), callerSP); break;
        case 4: __ lfs(as_FloatRegister(to_reg), reg2offset(from_reg, in_stk_bias), callerSP); break;
        default: ShouldNotReachHere();
      }
      break;
    case StorageType::STACK:
      out_bias = out_stk_bias; // fallthrough
    case StorageType::FRAME_DATA: {
      switch (from_reg.stack_size()) {
        case 8: __ ld( R0, reg2offset(from_reg, in_stk_bias), callerSP); break;
        case 4: __ lwa(R0, reg2offset(from_reg, in_stk_bias), callerSP); break;
        default: ShouldNotReachHere();
      }
      switch (to_reg.stack_size()) {
        case 8: __ std(R0, reg2offset(to_reg, out_bias), R1_SP); break;
        case 4: __ stw(R0, reg2offset(to_reg, out_bias), R1_SP); break;
        default: ShouldNotReachHere();
      }
    } break;
    default: ShouldNotReachHere();
  }
}

void ArgumentShuffle::pd_generate(MacroAssembler* masm, VMStorage tmp, int in_stk_bias, int out_stk_bias, const StubLocations& locs) const {
  Register callerSP = as_Register(tmp); // preset
  for (int i = 0; i < _moves.length(); i++) {
    Move move = _moves.at(i);
    VMStorage from_reg = move.from;
    VMStorage to_reg   = move.to;

    // replace any placeholders
    if (from_reg.type() == StorageType::PLACEHOLDER) {
      from_reg = locs.get(from_reg);
    }
    if (to_reg.type() == StorageType::PLACEHOLDER) {
      to_reg = locs.get(to_reg);
    }

    switch (from_reg.type()) {
      case StorageType::INTEGER:
        move_reg64(masm, out_stk_bias, from_reg, to_reg);
        break;
      case StorageType::FLOAT:
        move_float(masm, out_stk_bias, from_reg, to_reg);
        break;
      case StorageType::STACK:
        move_stack(masm, callerSP, in_stk_bias, out_stk_bias, from_reg, to_reg);
        break;
      default: ShouldNotReachHere();
    }
  }
}
