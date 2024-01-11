/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2023, Huawei Technologies Co., Ltd. All rights reserved.
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

#include "precompiled.hpp"
#include "code/vmreg.inline.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "oops/oopCast.inline.hpp"
#include "prims/foreignGlobals.hpp"
#include "prims/foreignGlobals.inline.hpp"
#include "prims/vmstorage.hpp"
#include "utilities/formatBuffer.hpp"

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
    masm->sd(as_Register(reg), Address(sp, offset));
  } else if (reg.type() == StorageType::FLOAT) {
    masm->fsd(as_FloatRegister(reg), Address(sp, offset));
  } else {
    // stack and BAD
  }
}

void RegSpiller::pd_load_reg(MacroAssembler* masm, int offset, VMStorage reg) {
  if (reg.type() == StorageType::INTEGER) {
    masm->ld(as_Register(reg), Address(sp, offset));
  } else if (reg.type() == StorageType::FLOAT) {
    masm->fld(as_FloatRegister(reg), Address(sp, offset));
  } else {
    // stack and BAD
  }
}

static constexpr int FP_BIAS = 0; // sender_sp_offset is 0 on RISCV

static void move_reg64(MacroAssembler* masm, int out_stk_bias,
                       Register from_reg, VMStorage to_reg) {
  int out_bias = 0;
  switch (to_reg.type()) {
    case StorageType::INTEGER:
      assert(to_reg.segment_mask() == REG64_MASK, "only moves to 64-bit integer registers supported");
      masm->mv(as_Register(to_reg), from_reg);
      break;
    case StorageType::STACK:
      out_bias = out_stk_bias;
    case StorageType::FRAME_DATA: {
      Address dest(sp, to_reg.offset() + out_bias);
      masm->sd(from_reg, dest);
    } break;
    default: ShouldNotReachHere();
  }
}

static void move_stack(MacroAssembler* masm, Register tmp_reg, int in_stk_bias, int out_stk_bias,
                       VMStorage from_reg, VMStorage to_reg) {
  Address from_addr(fp, FP_BIAS + from_reg.offset() + in_stk_bias);
  int out_bias = 0;
  switch (to_reg.type()) {
    case StorageType::INTEGER:
      assert(to_reg.segment_mask() == REG64_MASK, "only moves to 64-bit integer registers supported");
      masm->ld(as_Register(to_reg), from_addr);
      break;
    case StorageType::FLOAT:
      assert(to_reg.segment_mask() == FP_MASK, "only moves to floating-point registers supported");
      masm->fld(as_FloatRegister(to_reg), from_addr);
      break;
    case StorageType::STACK:
      out_bias = out_stk_bias;
    case StorageType::FRAME_DATA: {
      masm->ld(tmp_reg, from_addr);
      Address dest(sp, to_reg.offset() + out_bias);
      masm->sd(tmp_reg, dest); break;
    } break;
    default: ShouldNotReachHere();
  }
}

static void move_fp(MacroAssembler* masm, int out_stk_bias,
                    FloatRegister from_reg, VMStorage to_reg) {
  switch (to_reg.type()) {
    case StorageType::INTEGER:
      assert(to_reg.segment_mask() == REG64_MASK, "only moves to 64-bit integer registers supported");
      masm->fmv_x_d(as_Register(to_reg), from_reg);
      break;
    case StorageType::FLOAT:
      assert(to_reg.segment_mask() == FP_MASK, "only moves to floating-point registers supported");
      masm->fmv_d(as_FloatRegister(to_reg), from_reg); break;
      break;
    case StorageType::STACK: {
      Address dest(sp, to_reg.offset() + out_stk_bias);
      masm->fsd(from_reg, dest); break;
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
        assert(from_reg.segment_mask() == REG64_MASK, "only 64-bit integer register supported");
        move_reg64(masm, out_stk_bias, as_Register(from_reg), to_reg);
        break;
      case StorageType::FLOAT:
        assert(from_reg.segment_mask() == FP_MASK, "only floating-point register supported");
        move_fp(masm, out_stk_bias, as_FloatRegister(from_reg), to_reg);
        break;
      case StorageType::STACK:
        move_stack(masm, tmp_reg, in_stk_bias, out_stk_bias, from_reg, to_reg);
        break;
      default: ShouldNotReachHere();
    }
  }
}
