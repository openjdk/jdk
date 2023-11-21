/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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
  return _integer_volatile_registers.contains(reg);
}

bool ABIDescriptor::is_volatile_reg(FloatRegister reg) const {
  return _float_argument_registers.contains(reg)
          || _float_additional_volatile_registers.contains(reg);
}

bool ForeignGlobals::is_foreign_linker_supported() {
  return true;
}

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
  parse_register_array(volatileStorage, StorageType::INTEGER, abi._integer_volatile_registers, as_Register);
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
    __ reg2mem_opt(as_Register(reg), Address(Z_SP, offset), true);
  } else if (reg.type() == StorageType::FLOAT) {
    __ freg2mem_opt(as_FloatRegister(reg), Address(Z_SP, offset), true);
  } else {
    // stack and BAD
  }
}

void RegSpiller::pd_load_reg(MacroAssembler* masm, int offset, VMStorage reg) {
  if (reg.type() == StorageType::INTEGER) {
    __ mem2reg_opt(as_Register(reg), Address(Z_SP, offset), true);
  } else if (reg.type() == StorageType::FLOAT) {
    __ mem2freg_opt(as_FloatRegister(reg), Address(Z_SP, offset), true);
  } else {
    // stack and BAD
  }
}

static int reg2offset(VMStorage vms, int stk_bias) {
  assert(!vms.is_reg(), "wrong usage");
  return vms.index_or_offset() + stk_bias;
}

static void move_reg(MacroAssembler* masm, int out_stk_bias,
                       VMStorage from_reg, VMStorage to_reg) {
  int out_bias = 0;
  switch (to_reg.type()) {
    case StorageType::INTEGER:
      if (to_reg.segment_mask() == REG64_MASK && from_reg.segment_mask() == REG32_MASK ) {
        // see CCallingConventionRequiresIntsAsLongs
        __ z_lgfr(as_Register(to_reg), as_Register(from_reg));
      } else {
        __ lgr_if_needed(as_Register(to_reg), as_Register(from_reg));
      }
      break;
    case StorageType::STACK:
      out_bias = out_stk_bias;  //fallthrough
    case StorageType::FRAME_DATA: {
      // Integer types always get a 64 bit slot in C.
      if (from_reg.segment_mask() == REG32_MASK) {
        // see CCallingConventionRequiresIntsAsLongs
        __ z_lgfr(as_Register(from_reg), as_Register(from_reg));
      }
      switch (to_reg.stack_size()) {
        case 8: __ reg2mem_opt(as_Register(from_reg), Address(Z_SP, reg2offset(to_reg, out_bias)), true); break;
        case 4: __ reg2mem_opt(as_Register(from_reg), Address(Z_SP, reg2offset(to_reg, out_bias)), false); break;
        default: ShouldNotReachHere();
      }
    } break;
    default: ShouldNotReachHere();
  }
}

static void move_float(MacroAssembler* masm, int out_stk_bias,
                       VMStorage from_reg, VMStorage to_reg) {
  switch (to_reg.type()) {
    case StorageType::FLOAT:
      if (from_reg.segment_mask() == REG64_MASK)
        __ move_freg_if_needed(as_FloatRegister(to_reg), T_DOUBLE, as_FloatRegister(from_reg), T_DOUBLE);
      else
        __ move_freg_if_needed(as_FloatRegister(to_reg), T_FLOAT, as_FloatRegister(from_reg), T_FLOAT);
      break;
    case StorageType::STACK:
      if (from_reg.segment_mask() == REG64_MASK) {
        assert(to_reg.stack_size() == 8, "size should match");
        __ freg2mem_opt(as_FloatRegister(from_reg), Address(Z_SP, reg2offset(to_reg, out_stk_bias)), true);
      } else {
        assert(to_reg.stack_size() == 4, "size should match");
        __ freg2mem_opt(as_FloatRegister(from_reg), Address(Z_SP, reg2offset(to_reg, out_stk_bias)), false);
      }
      break;
    default: ShouldNotReachHere();
  }
}

static void move_stack(MacroAssembler* masm, Register tmp_reg, int in_stk_bias, int out_stk_bias,
                       VMStorage from_reg, VMStorage to_reg) {
  int out_bias = 0;
  Address from_addr(Z_R11, reg2offset(from_reg, in_stk_bias));
  switch (to_reg.type()) {
    case StorageType::INTEGER:
      switch (from_reg.stack_size()) {
        case 8: __ mem2reg_opt(as_Register(to_reg), from_addr, true);break;
        case 4: __ mem2reg_opt(as_Register(to_reg), from_addr, false);break;
        default: ShouldNotReachHere();
      }
      break;
    case StorageType::FLOAT:
      switch (from_reg.stack_size()) {
        case 8: __ mem2freg_opt(as_FloatRegister(to_reg), from_addr, true);break;
        case 4: __ mem2freg_opt(as_FloatRegister(to_reg), from_addr, false);break;
        default: ShouldNotReachHere();
      }
      break;
    case StorageType::STACK:
      out_bias = out_stk_bias; // fallthrough
    case StorageType::FRAME_DATA: {
      switch (from_reg.stack_size()) {
        case 8: __ mem2reg_opt(tmp_reg, from_addr, true); break;
        case 4: if (to_reg.stack_size() == 8) {
                  __ mem2reg_signed_opt(tmp_reg, from_addr);
                } else {
                  __ mem2reg_opt(tmp_reg, from_addr, false);
                }
                break;
        default: ShouldNotReachHere();
      }
      switch (to_reg.stack_size()) {
        case 8: __ reg2mem_opt(tmp_reg, Address (Z_SP, reg2offset(to_reg, out_bias)), true); break;
        case 4: __ reg2mem_opt(tmp_reg, Address (Z_SP, reg2offset(to_reg, out_bias)), false); break;
        default: ShouldNotReachHere();
      }
    } break;
    default: ShouldNotReachHere();
  }
}

void ArgumentShuffle::pd_generate(MacroAssembler* masm, VMStorage tmp, int in_stk_bias, int out_stk_bias) const {
  Register tmp_reg = as_Register(tmp);
  for (int i = 0; i < _moves.length(); i++) {
    Move move = _moves.at(i);
    VMStorage from_reg = move.from;
    VMStorage to_reg   = move.to;

    switch (from_reg.type()) {
      case StorageType::INTEGER:
        move_reg(masm, out_stk_bias, from_reg, to_reg);
        break;
      case StorageType::FLOAT:
        move_float(masm, out_stk_bias, from_reg, to_reg);
        break;
      case StorageType::STACK:
        move_stack(masm, tmp_reg, in_stk_bias, out_stk_bias, from_reg, to_reg);
        break;
      default: ShouldNotReachHere();
    }
  }
}
