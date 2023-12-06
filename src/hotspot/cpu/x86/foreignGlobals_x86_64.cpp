/*
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
#include "classfile/javaClasses.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "oops/oopCast.inline.hpp"
#include "prims/foreignGlobals.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/formatBuffer.hpp"

bool ForeignGlobals::is_foreign_linker_supported() {
  return true;
}

bool ABIDescriptor::is_volatile_reg(Register reg) const {
    return _integer_argument_registers.contains(reg)
        || _integer_additional_volatile_registers.contains(reg);
}

bool ABIDescriptor::is_volatile_reg(XMMRegister reg) const {
    return _vector_argument_registers.contains(reg)
        || _vector_additional_volatile_registers.contains(reg);
}

const ABIDescriptor ForeignGlobals::parse_abi_descriptor(jobject jabi) {
  oop abi_oop = JNIHandles::resolve_non_null(jabi);
  ABIDescriptor abi;

  objArrayOop inputStorage = jdk_internal_foreign_abi_ABIDescriptor::inputStorage(abi_oop);
  parse_register_array(inputStorage, StorageType::INTEGER, abi._integer_argument_registers, as_Register);
  parse_register_array(inputStorage, StorageType::VECTOR, abi._vector_argument_registers, as_XMMRegister);

  objArrayOop outputStorage = jdk_internal_foreign_abi_ABIDescriptor::outputStorage(abi_oop);
  parse_register_array(outputStorage, StorageType::INTEGER, abi._integer_return_registers, as_Register);
  parse_register_array(outputStorage, StorageType::VECTOR, abi._vector_return_registers, as_XMMRegister);
  objArrayOop subarray = oop_cast<objArrayOop>(outputStorage->obj_at((int) StorageType::X87));
  abi._X87_return_registers_noof = subarray->length();

  objArrayOop volatileStorage = jdk_internal_foreign_abi_ABIDescriptor::volatileStorage(abi_oop);
  parse_register_array(volatileStorage, StorageType::INTEGER, abi._integer_additional_volatile_registers, as_Register);
  parse_register_array(volatileStorage, StorageType::VECTOR, abi._vector_additional_volatile_registers, as_XMMRegister);

  abi._stack_alignment_bytes = jdk_internal_foreign_abi_ABIDescriptor::stackAlignment(abi_oop);
  abi._shadow_space_bytes = jdk_internal_foreign_abi_ABIDescriptor::shadowSpace(abi_oop);

  abi._scratch1 = parse_vmstorage(jdk_internal_foreign_abi_ABIDescriptor::scratch1(abi_oop));
  abi._scratch2 = parse_vmstorage(jdk_internal_foreign_abi_ABIDescriptor::scratch2(abi_oop));

  return abi;
}

int RegSpiller::pd_reg_size(VMStorage reg) {
  if (reg.type() == StorageType::INTEGER) {
    return 8;
  } else if (reg.type() == StorageType::VECTOR) {
    return 16;
  }
  return 0; // stack and BAD
}

void RegSpiller::pd_store_reg(MacroAssembler* masm, int offset, VMStorage reg) {
  if (reg.type() == StorageType::INTEGER) {
    masm->movptr(Address(rsp, offset), as_Register(reg));
  } else if (reg.type() == StorageType::VECTOR) {
    masm->movdqu(Address(rsp, offset), as_XMMRegister(reg));
  } else {
    // stack and BAD
  }
}

void RegSpiller::pd_load_reg(MacroAssembler* masm, int offset, VMStorage reg) {
  if (reg.type() == StorageType::INTEGER) {
    masm->movptr(as_Register(reg), Address(rsp, offset));
  } else if (reg.type() == StorageType::VECTOR) {
    masm->movdqu(as_XMMRegister(reg), Address(rsp, offset));
  } else {
    // stack and BAD
  }
}

static constexpr int RBP_BIAS = 16; // skip old rbp and return address

static void move_reg64(MacroAssembler* masm, int out_stk_bias,
                       Register from_reg, VMStorage to_reg) {
  int out_bias = 0;
  switch (to_reg.type()) {
    case StorageType::INTEGER:
      assert(to_reg.segment_mask() == REG64_MASK, "only moves to 64-bit registers supported");
      masm->movq(as_Register(to_reg), from_reg);
      break;
    case StorageType::STACK:
      out_bias = out_stk_bias;
    case StorageType::FRAME_DATA:
      assert(to_reg.stack_size() == 8, "only moves with 64-bit targets supported");
      masm->movq(Address(rsp, to_reg.offset() + out_bias), from_reg);
      break;
    default: ShouldNotReachHere();
  }
}

static void move_stack64(MacroAssembler* masm, Register tmp_reg, int out_stk_bias,
                         Address from_address, VMStorage to_reg) {
  int out_bias = 0;
  switch (to_reg.type()) {
    case StorageType::INTEGER:
      assert(to_reg.segment_mask() == REG64_MASK, "only moves to 64-bit registers supported");
      masm->movq(as_Register(to_reg), from_address);
      break;
    case StorageType::VECTOR:
      assert(to_reg.segment_mask() == XMM_MASK, "only moves to xmm registers supported");
      masm->movdqu(as_XMMRegister(to_reg), from_address);
      break;
    case StorageType::STACK:
      out_bias = out_stk_bias;
    case StorageType::FRAME_DATA:
      assert(to_reg.stack_size() == 8, "only moves with 64-bit targets supported");
      masm->movq(tmp_reg, from_address);
      masm->movq(Address(rsp, to_reg.offset() + out_bias), tmp_reg);
      break;
    default: ShouldNotReachHere();
  }
}

static void move_xmm(MacroAssembler* masm, int out_stk_bias,
                     XMMRegister from_reg, VMStorage to_reg) {
  switch (to_reg.type()) {
    case StorageType::INTEGER: // windows vargarg floats
      assert(to_reg.segment_mask() == REG64_MASK, "only moves to 64-bit registers supported");
      masm->movq(as_Register(to_reg), from_reg);
      break;
    case StorageType::VECTOR:
      assert(to_reg.segment_mask() == XMM_MASK, "only moves to xmm registers supported");
      masm->movdqu(as_XMMRegister(to_reg), from_reg);
      break;
    case StorageType::STACK:
      assert(to_reg.stack_size() == 8, "only moves with 64-bit targets supported");
      masm->movq(Address(rsp, to_reg.offset() + out_stk_bias), from_reg);
      break;
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
        assert(from_reg.segment_mask() == REG64_MASK, "only 64-bit register supported");
        move_reg64(masm, out_stk_bias, as_Register(from_reg), to_reg);
        break;
      case StorageType::VECTOR:
        assert(from_reg.segment_mask() == XMM_MASK, "only xmm register supported");
        move_xmm(masm, out_stk_bias, as_XMMRegister(from_reg), to_reg);
        break;
      case StorageType::STACK: {
        assert(from_reg.stack_size() == 8, "only stack_size 8 supported");
        Address from_addr(rbp, RBP_BIAS + from_reg.offset() + in_stk_bias);
        move_stack64(masm, tmp_reg, out_stk_bias, from_addr, to_reg);
      } break;
      default: ShouldNotReachHere();
    }
  }
}
