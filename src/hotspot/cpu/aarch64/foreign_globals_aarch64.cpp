/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited. All rights reserved.
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
#include "code/vmreg.inline.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "oops/oopCast.inline.hpp"
#include "opto/matcher.hpp"
#include "prims/foreign_globals.hpp"
#include "prims/foreign_globals.inline.hpp"
#include "utilities/formatBuffer.hpp"

bool ABIDescriptor::is_volatile_reg(Register reg) const {
  return _integer_argument_registers.contains(reg)
    || _integer_additional_volatile_registers.contains(reg);
}

bool ABIDescriptor::is_volatile_reg(FloatRegister reg) const {
    return _vector_argument_registers.contains(reg)
        || _vector_additional_volatile_registers.contains(reg);
}

#define INTEGER_TYPE 0
#define VECTOR_TYPE 1

const ABIDescriptor ForeignGlobals::parse_abi_descriptor_impl(jobject jabi) const {
  oop abi_oop = JNIHandles::resolve_non_null(jabi);
  ABIDescriptor abi;
  constexpr Register (*to_Register)(int) = as_Register;

  objArrayOop inputStorage = oop_cast<objArrayOop>(abi_oop->obj_field(ABI.inputStorage_offset));
  loadArray(inputStorage, INTEGER_TYPE, abi._integer_argument_registers, to_Register);
  loadArray(inputStorage, VECTOR_TYPE, abi._vector_argument_registers, as_FloatRegister);

  objArrayOop outputStorage = oop_cast<objArrayOop>(abi_oop->obj_field(ABI.outputStorage_offset));
  loadArray(outputStorage, INTEGER_TYPE, abi._integer_return_registers, to_Register);
  loadArray(outputStorage, VECTOR_TYPE, abi._vector_return_registers, as_FloatRegister);

  objArrayOop volatileStorage = oop_cast<objArrayOop>(abi_oop->obj_field(ABI.volatileStorage_offset));
  loadArray(volatileStorage, INTEGER_TYPE, abi._integer_additional_volatile_registers, to_Register);
  loadArray(volatileStorage, VECTOR_TYPE, abi._vector_additional_volatile_registers, as_FloatRegister);

  abi._stack_alignment_bytes = abi_oop->int_field(ABI.stackAlignment_offset);
  abi._shadow_space_bytes = abi_oop->int_field(ABI.shadowSpace_offset);

  return abi;
}

const BufferLayout ForeignGlobals::parse_buffer_layout_impl(jobject jlayout) const {
  oop layout_oop = JNIHandles::resolve_non_null(jlayout);
  BufferLayout layout;

  layout.stack_args_bytes = layout_oop->long_field(BL.stack_args_bytes_offset);
  layout.stack_args = layout_oop->long_field(BL.stack_args_offset);
  layout.arguments_next_pc = layout_oop->long_field(BL.arguments_next_pc_offset);

  typeArrayOop input_offsets = oop_cast<typeArrayOop>(layout_oop->obj_field(BL.input_type_offsets_offset));
  layout.arguments_integer = (size_t) input_offsets->long_at(INTEGER_TYPE);
  layout.arguments_vector = (size_t) input_offsets->long_at(VECTOR_TYPE);

  typeArrayOop output_offsets = oop_cast<typeArrayOop>(layout_oop->obj_field(BL.output_type_offsets_offset));
  layout.returns_integer = (size_t) output_offsets->long_at(INTEGER_TYPE);
  layout.returns_vector = (size_t) output_offsets->long_at(VECTOR_TYPE);

  layout.buffer_size = layout_oop->long_field(BL.size_offset);

  return layout;
}

const CallRegs ForeignGlobals::parse_call_regs_impl(jobject jconv) const {
  ShouldNotCallThis();
  return {};
}

enum class RegType {
  INTEGER = 0,
  VECTOR = 1,
  STACK = 3
};

VMReg ForeignGlobals::vmstorage_to_vmreg(int type, int index) {
  switch(static_cast<RegType>(type)) {
    case RegType::INTEGER: return ::as_Register(index)->as_VMReg();
    case RegType::VECTOR: return ::as_FloatRegister(index)->as_VMReg();
    case RegType::STACK: return VMRegImpl::stack2reg(index LP64_ONLY(* 2));
  }
  return VMRegImpl::Bad();
}

int RegSpiller::pd_reg_size(VMReg reg) {
  if (reg->is_Register()) {
    return 8;
  } else if (reg->is_FloatRegister()) {
    bool use_sve = Matcher::supports_scalable_vector();
    if (use_sve) {
      return Matcher::scalable_vector_reg_size(T_BYTE);
    }
    return 16;
  }
  return 0; // stack and BAD
}

void RegSpiller::pd_store_reg(MacroAssembler* masm, int offset, VMReg reg) {
  if (reg->is_Register()) {
    masm->spill(reg->as_Register(), true, offset);
  } else if (reg->is_FloatRegister()) {
    bool use_sve = Matcher::supports_scalable_vector();
    if (use_sve) {
      masm->spill_sve_vector(reg->as_FloatRegister(), offset, Matcher::scalable_vector_reg_size(T_BYTE));
    } else {
      masm->spill(reg->as_FloatRegister(), masm->Q, offset);
    }
  } else {
    // stack and BAD
  }
}

void RegSpiller::pd_load_reg(MacroAssembler* masm, int offset, VMReg reg) {
  if (reg->is_Register()) {
    masm->unspill(reg->as_Register(), true, offset);
  } else if (reg->is_FloatRegister()) {
    bool use_sve = Matcher::supports_scalable_vector();
    if (use_sve) {
      masm->unspill_sve_vector(reg->as_FloatRegister(), offset, Matcher::scalable_vector_reg_size(T_BYTE));
    } else {
      masm->unspill(reg->as_FloatRegister(), masm->Q, offset);
    }
  } else {
    // stack and BAD
  }
}

void ArgumentShuffle::pd_generate(MacroAssembler* masm, VMReg tmp, int in_stk_bias, int out_stk_bias) const {
  assert(in_stk_bias == 0 && out_stk_bias == 0, "bias not implemented");
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
        masm->move32_64(from_vmreg, to_vmreg, tmp_reg);
        break;

      case T_FLOAT:
        masm->float_move(from_vmreg, to_vmreg, tmp_reg);
        break;

      case T_DOUBLE:
        masm->double_move(from_vmreg, to_vmreg, tmp_reg);
        break;

      case T_LONG :
        masm->long_move(from_vmreg, to_vmreg, tmp_reg);
        break;

      default:
        fatal("found in upcall args: %s", type2name(arg_bt));
    }
  }
}
