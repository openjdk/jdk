/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "code/vmreg.hpp"
#include "prims/foreignGlobals.hpp"
#include "prims/foreignGlobals.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "utilities/debug.hpp"

class MacroAssembler;

static constexpr int INTEGER_TYPE = 0;
static constexpr int FLOAT_TYPE = 1;

const ABIDescriptor ForeignGlobals::parse_abi_descriptor(jobject jabi) {
  oop abi_oop = JNIHandles::resolve_non_null(jabi);
  ABIDescriptor abi;

  objArrayOop inputStorage = jdk_internal_foreign_abi_ABIDescriptor::inputStorage(abi_oop);
  parse_register_array(inputStorage, INTEGER_TYPE, abi._integer_argument_registers, as_Register);
  parse_register_array(inputStorage, FLOAT_TYPE, abi._float_argument_registers, as_FloatRegister);

  objArrayOop outputStorage = jdk_internal_foreign_abi_ABIDescriptor::outputStorage(abi_oop);
  parse_register_array(outputStorage, INTEGER_TYPE, abi._integer_return_registers, as_Register);
  parse_register_array(outputStorage, FLOAT_TYPE, abi._float_return_registers, as_FloatRegister);

  objArrayOop volatileStorage = jdk_internal_foreign_abi_ABIDescriptor::volatileStorage(abi_oop);
  parse_register_array(volatileStorage, INTEGER_TYPE, abi._integer_additional_volatile_registers, as_Register);
  parse_register_array(volatileStorage, FLOAT_TYPE, abi._float_additional_volatile_registers, as_FloatRegister);

  abi._stack_alignment_bytes = jdk_internal_foreign_abi_ABIDescriptor::stackAlignment(abi_oop);
  abi._shadow_space_bytes = jdk_internal_foreign_abi_ABIDescriptor::shadowSpace(abi_oop);

  abi._target_addr_reg = parse_vmstorage(
          jdk_internal_foreign_abi_ABIDescriptor::targetAddrStorage(abi_oop))->as_Register();
  abi._ret_buf_addr_reg = parse_vmstorage(
          jdk_internal_foreign_abi_ABIDescriptor::retBufAddrStorage(abi_oop))->as_Register();
  return abi;
}

static RegType get_regtype(int regtype_or_storageclass) {
  if (regtype_or_storageclass <= static_cast<int>(RegType::STACK)) {
    return static_cast<RegType>(regtype_or_storageclass);
  }

  switch (static_cast<StorageClass>(regtype_or_storageclass)) {
    case StorageClass::INTEGER_8:
    case StorageClass::INTEGER_16:
    case StorageClass::INTEGER_32:
    case StorageClass::INTEGER_64:
      return RegType::INTEGER;
    case StorageClass::FLOAT_32:
    case StorageClass::FLOAT_64:
      return RegType::FLOAT;
    default:
      ShouldNotReachHere();
      return static_cast<RegType>(-1);
  }
}

VMReg ForeignGlobals::vmstorage_to_vmreg(int type, int index) {
  switch (get_regtype(type)) {
    case RegType::INTEGER:
      return ::as_Register(index)->as_VMReg();
    case RegType::FLOAT:
      return ::as_FloatRegister(index)->as_VMReg();
    case RegType::STACK:
      return VMRegImpl::stack2reg(index LP64_ONLY(* 2));
    default:
      return VMRegImpl::Bad();
  }
}

int RegSpiller::pd_reg_size(VMReg reg) {
  if (reg->is_Register() || reg->is_FloatRegister()) {
    return 8;
  }
  return 0; // stack and BAD
}

// pd_* are used to perfrom upcall, do not impelment them now.
void RegSpiller::pd_store_reg(MacroAssembler* masm, int offset, VMReg reg) {
  if (reg->is_Register()) {
    masm->sd(reg->as_Register(), Address(sp, offset));
  } else if (reg->is_FloatRegister()) {
    masm->fsd(reg->as_FloatRegister(), Address(sp, offset));
  } else {
    // stack and BAD
  }
}

void RegSpiller::pd_load_reg(MacroAssembler* masm, int offset, VMReg reg) {
  if (reg->is_Register()) {
    masm->ld(reg->as_Register(), Address(sp, offset));
  } else if (reg->is_FloatRegister()) {
    masm->fld(reg->as_FloatRegister(), Address(sp, offset));
  } else {
    // stack and BAD
  }
}

#define __ masm->

void ArgumentShuffle::pd_generate(MacroAssembler* masm, VMReg tmp, int in_stk_bias, int out_stk_bias) const {
  assert(in_stk_bias == 0 && out_stk_bias == 0, "bias not implemented");
  Register tmp_reg = tmp->as_Register();
  for (int i = 0; i < _moves.length(); i++) {
    Move move = _moves.at(i);
    BasicType arg_bt = move.bt;
    VMRegPair from_vmreg = move.from;
    VMRegPair to_vmreg = move.to;

    masm->block_comment(err_msg("bt=%s", null_safe_string(type2name(arg_bt))));
    switch (arg_bt) {
      case T_BOOLEAN:
      case T_BYTE:
      case T_SHORT:
      case T_CHAR:
      case T_INT:
        __ move32_64(from_vmreg, to_vmreg);
        break;
      case T_FLOAT: {
        if (!to_vmreg.first()->is_FloatRegister()) {
          __ move_float_to_integer_or_stack(from_vmreg, to_vmreg);
        } else {
          __ float_move(from_vmreg, to_vmreg);
        }
        break;
      }
      case T_DOUBLE: {
        if (!to_vmreg.first()->is_FloatRegister()) {
          __ move_double_to_integer_or_stack(from_vmreg, to_vmreg);
        } else {
          __ double_move(from_vmreg, to_vmreg);
        }
        break;
      }
      case T_LONG:
        __ long_move(from_vmreg, to_vmreg);
        break;
      default:
        fatal("found in upcall args: %s", type2name(arg_bt));
    }
  }
}

#undef __

bool ABIDescriptor::is_volatile_reg(Register reg) const {
  return _integer_argument_registers.contains(reg)
         || _integer_additional_volatile_registers.contains(reg);
}

bool ABIDescriptor::is_volatile_reg(FloatRegister reg) const {
  return _float_argument_registers.contains(reg)
         || _float_additional_volatile_registers.contains(reg);
}
