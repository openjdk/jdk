/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/jniHandles.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "oops/oopCast.inline.hpp"
#include "prims/foreign_globals.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/formatBuffer.hpp"

bool ABIDescriptor::is_volatile_reg(Register reg) const {
    return _integer_argument_registers.contains(reg)
        || _integer_additional_volatile_registers.contains(reg);
}

bool ABIDescriptor::is_volatile_reg(XMMRegister reg) const {
    return _vector_argument_registers.contains(reg)
        || _vector_additional_volatile_registers.contains(reg);
}

#define INTEGER_TYPE 0
#define VECTOR_TYPE 1
#define X87_TYPE 2

const ABIDescriptor ForeignGlobals::parse_abi_descriptor_impl(jobject jabi) const {
  oop abi_oop = JNIHandles::resolve_non_null(jabi);
  ABIDescriptor abi;

  objArrayOop inputStorage = oop_cast<objArrayOop>(abi_oop->obj_field(ABI.inputStorage_offset));
  loadArray(inputStorage, INTEGER_TYPE, abi._integer_argument_registers, as_Register);
  loadArray(inputStorage, VECTOR_TYPE, abi._vector_argument_registers, as_XMMRegister);

  objArrayOop outputStorage = oop_cast<objArrayOop>(abi_oop->obj_field(ABI.outputStorage_offset));
  loadArray(outputStorage, INTEGER_TYPE, abi._integer_return_registers, as_Register);
  loadArray(outputStorage, VECTOR_TYPE, abi._vector_return_registers, as_XMMRegister);
  objArrayOop subarray = oop_cast<objArrayOop>(outputStorage->obj_at(X87_TYPE));
  abi._X87_return_registers_noof = subarray->length();

  objArrayOop volatileStorage = oop_cast<objArrayOop>(abi_oop->obj_field(ABI.volatileStorage_offset));
  loadArray(volatileStorage, INTEGER_TYPE, abi._integer_additional_volatile_registers, as_Register);
  loadArray(volatileStorage, VECTOR_TYPE, abi._vector_additional_volatile_registers, as_XMMRegister);

  abi._stack_alignment_bytes = abi_oop->int_field(ABI.stackAlignment_offset);
  abi._shadow_space_bytes = abi_oop->int_field(ABI.shadowSpace_offset);

  abi._target_addr_reg = parse_vmstorage(abi_oop->obj_field(ABI.targetAddrStorage_offset))->as_Register();
  abi._ret_buf_addr_reg = parse_vmstorage(abi_oop->obj_field(ABI.retBufAddrStorage_offset))->as_Register();

  return abi;
}

enum class RegType {
  INTEGER = 0,
  VECTOR = 1,
  X87 = 2,
  STACK = 3
};

VMReg ForeignGlobals::vmstorage_to_vmreg(int type, int index) {
  switch(static_cast<RegType>(type)) {
    case RegType::INTEGER: return ::as_Register(index)->as_VMReg();
    case RegType::VECTOR: return ::as_XMMRegister(index)->as_VMReg();
    case RegType::STACK: return VMRegImpl::stack2reg(index LP64_ONLY(* 2)); // numbering on x64 goes per 64-bits
    case RegType::X87: break;
  }
  return VMRegImpl::Bad();
}

int RegSpiller::pd_reg_size(VMReg reg) {
  if (reg->is_Register()) {
    return 8;
  } else if (reg->is_XMMRegister()) {
    return 16;
  }
  return 0; // stack and BAD
}

void RegSpiller::pd_store_reg(MacroAssembler* masm, int offset, VMReg reg) {
  if (reg->is_Register()) {
    masm->movptr(Address(rsp, offset), reg->as_Register());
  } else if (reg->is_XMMRegister()) {
    masm->movdqu(Address(rsp, offset), reg->as_XMMRegister());
  } else {
    // stack and BAD
  }
}

void RegSpiller::pd_load_reg(MacroAssembler* masm, int offset, VMReg reg) {
  if (reg->is_Register()) {
    masm->movptr(reg->as_Register(), Address(rsp, offset));
  } else if (reg->is_XMMRegister()) {
    masm->movdqu(reg->as_XMMRegister(), Address(rsp, offset));
  } else {
    // stack and BAD
  }
}

void ArgumentShuffle::pd_generate(MacroAssembler* masm, VMReg tmp, int in_stk_bias, int out_stk_bias) const {
  Register tmp_reg = tmp->as_Register();
  for (int i = 0; i < _moves.length(); i++) {
    Move move = _moves.at(i);
    BasicType arg_bt     = move.bt;
    VMRegPair from_vmreg = move.from;
    VMRegPair to_vmreg   = move.to;

    masm->block_comment(err_msg("bt=%s", null_safe_string(type2name(arg_bt))));
    switch (arg_bt) {
      case T_BOOLEAN:
      case T_BYTE:
      case T_SHORT:
      case T_CHAR:
      case T_INT:
        masm->move32_64(from_vmreg, to_vmreg, tmp_reg, in_stk_bias, out_stk_bias);
        break;

      case T_FLOAT:
        if (to_vmreg.first()->is_Register()) { // Windows vararg call
          masm->movq(to_vmreg.first()->as_Register(), from_vmreg.first()->as_XMMRegister());
        } else {
          masm->float_move(from_vmreg, to_vmreg, tmp_reg, in_stk_bias, out_stk_bias);
        }
        break;

      case T_DOUBLE:
        if (to_vmreg.first()->is_Register()) { // Windows vararg call
          masm->movq(to_vmreg.first()->as_Register(), from_vmreg.first()->as_XMMRegister());
        } else {
          masm->double_move(from_vmreg, to_vmreg, tmp_reg, in_stk_bias, out_stk_bias);
        }
        break;

      case T_LONG:
        masm->long_move(from_vmreg, to_vmreg, tmp_reg, in_stk_bias, out_stk_bias);
        break;

      default:
        fatal("found in upcall args: %s", type2name(arg_bt));
    }
  }
}
