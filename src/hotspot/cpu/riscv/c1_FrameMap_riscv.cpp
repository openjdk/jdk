/*
 * Copyright (c) 1999, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "c1/c1_FrameMap.hpp"
#include "c1/c1_LIR.hpp"
#include "runtime/sharedRuntime.hpp"
#include "vmreg_riscv.inline.hpp"

LIR_Opr FrameMap::map_to_opr(BasicType type, VMRegPair* reg, bool) {
  LIR_Opr opr = LIR_OprFact::illegalOpr;
  VMReg r_1 = reg->first();
  VMReg r_2 = reg->second();
  if (r_1->is_stack()) {
    // Convert stack slot to an SP offset
    // The calling convention does not count the SharedRuntime::out_preserve_stack_slots() value
    // so we must add it in here.
    int st_off = (r_1->reg2stack() + SharedRuntime::out_preserve_stack_slots()) * VMRegImpl::stack_slot_size;
    opr = LIR_OprFact::address(new LIR_Address(sp_opr, st_off, type));
  } else if (r_1->is_Register()) {
    Register reg1 = r_1->as_Register();
    if (r_2->is_Register() && (type == T_LONG || type == T_DOUBLE)) {
      Register reg2 = r_2->as_Register();
      assert(reg2 == reg1, "must be same register");
      opr = as_long_opr(reg1);
    } else if (is_reference_type(type)) {
      opr = as_oop_opr(reg1);
    } else if (type == T_METADATA) {
      opr = as_metadata_opr(reg1);
    } else if (type == T_ADDRESS) {
      opr = as_address_opr(reg1);
    } else {
      opr = as_opr(reg1);
    }
  } else if (r_1->is_FloatRegister()) {
    assert(type == T_DOUBLE || type == T_FLOAT, "wrong type");
    int num = r_1->as_FloatRegister()->encoding();
    if (type == T_FLOAT) {
      opr = LIR_OprFact::single_fpu(num);
    } else {
      opr = LIR_OprFact::double_fpu(num);
    }
  } else {
    ShouldNotReachHere();
  }
  return opr;
}

LIR_Opr FrameMap::zr_opr;
LIR_Opr FrameMap::r1_opr;
LIR_Opr FrameMap::r2_opr;
LIR_Opr FrameMap::r3_opr;
LIR_Opr FrameMap::r4_opr;
LIR_Opr FrameMap::r5_opr;
LIR_Opr FrameMap::r6_opr;
LIR_Opr FrameMap::r7_opr;
LIR_Opr FrameMap::r8_opr;
LIR_Opr FrameMap::r9_opr;
LIR_Opr FrameMap::r10_opr;
LIR_Opr FrameMap::r11_opr;
LIR_Opr FrameMap::r12_opr;
LIR_Opr FrameMap::r13_opr;
LIR_Opr FrameMap::r14_opr;
LIR_Opr FrameMap::r15_opr;
LIR_Opr FrameMap::r16_opr;
LIR_Opr FrameMap::r17_opr;
LIR_Opr FrameMap::r18_opr;
LIR_Opr FrameMap::r19_opr;
LIR_Opr FrameMap::r20_opr;
LIR_Opr FrameMap::r21_opr;
LIR_Opr FrameMap::r22_opr;
LIR_Opr FrameMap::r23_opr;
LIR_Opr FrameMap::r24_opr;
LIR_Opr FrameMap::r25_opr;
LIR_Opr FrameMap::r26_opr;
LIR_Opr FrameMap::r27_opr;
LIR_Opr FrameMap::r28_opr;
LIR_Opr FrameMap::r29_opr;
LIR_Opr FrameMap::r30_opr;
LIR_Opr FrameMap::r31_opr;

LIR_Opr FrameMap::fp_opr;
LIR_Opr FrameMap::sp_opr;

LIR_Opr FrameMap::receiver_opr;

LIR_Opr FrameMap::zr_oop_opr;
LIR_Opr FrameMap::r1_oop_opr;
LIR_Opr FrameMap::r2_oop_opr;
LIR_Opr FrameMap::r3_oop_opr;
LIR_Opr FrameMap::r4_oop_opr;
LIR_Opr FrameMap::r5_oop_opr;
LIR_Opr FrameMap::r6_oop_opr;
LIR_Opr FrameMap::r7_oop_opr;
LIR_Opr FrameMap::r8_oop_opr;
LIR_Opr FrameMap::r9_oop_opr;
LIR_Opr FrameMap::r10_oop_opr;
LIR_Opr FrameMap::r11_oop_opr;
LIR_Opr FrameMap::r12_oop_opr;
LIR_Opr FrameMap::r13_oop_opr;
LIR_Opr FrameMap::r14_oop_opr;
LIR_Opr FrameMap::r15_oop_opr;
LIR_Opr FrameMap::r16_oop_opr;
LIR_Opr FrameMap::r17_oop_opr;
LIR_Opr FrameMap::r18_oop_opr;
LIR_Opr FrameMap::r19_oop_opr;
LIR_Opr FrameMap::r20_oop_opr;
LIR_Opr FrameMap::r21_oop_opr;
LIR_Opr FrameMap::r22_oop_opr;
LIR_Opr FrameMap::r23_oop_opr;
LIR_Opr FrameMap::r24_oop_opr;
LIR_Opr FrameMap::r25_oop_opr;
LIR_Opr FrameMap::r26_oop_opr;
LIR_Opr FrameMap::r27_oop_opr;
LIR_Opr FrameMap::r28_oop_opr;
LIR_Opr FrameMap::r29_oop_opr;
LIR_Opr FrameMap::r30_oop_opr;
LIR_Opr FrameMap::r31_oop_opr;

LIR_Opr FrameMap::t0_opr;
LIR_Opr FrameMap::t1_opr;
LIR_Opr FrameMap::t0_long_opr;
LIR_Opr FrameMap::t1_long_opr;

LIR_Opr FrameMap::r10_metadata_opr;
LIR_Opr FrameMap::r11_metadata_opr;
LIR_Opr FrameMap::r12_metadata_opr;
LIR_Opr FrameMap::r13_metadata_opr;
LIR_Opr FrameMap::r14_metadata_opr;
LIR_Opr FrameMap::r15_metadata_opr;

LIR_Opr FrameMap::long10_opr;
LIR_Opr FrameMap::long11_opr;
LIR_Opr FrameMap::fpu10_float_opr;
LIR_Opr FrameMap::fpu10_double_opr;

LIR_Opr FrameMap::_caller_save_cpu_regs[] = {};
LIR_Opr FrameMap::_caller_save_fpu_regs[] = {};

//--------------------------------------------------------
//               FrameMap
//--------------------------------------------------------
// |---f31--|
// |---..---|
// |---f28--|
// |---f27--|<---pd_last_callee_saved_fpu_reg_2
// |---..---|
// |---f18--|<---pd_first_callee_saved_fpu_reg_2
// |---f17--|
// |---..---|
// |---f10--|
// |---f9---|<---pd_last_callee_saved_fpu_reg_1
// |---f8---|<---pd_first_callee_saved_fpu_reg_1
// |---f7---|
// |---..---|
// |---f0---|
// |---x27--|
// |---x23--|
// |---x8---|
// |---x4---|
// |---x3---|
// |---x2---|
// |---x1---|
// |---x0---|
// |---x26--|<---pd_last_callee_saved_reg
// |---..---|
// |---x18--|
// |---x9---|<---pd_first_callee_saved_reg
// |---x31--|
// |---..---|
// |---x28--|
// |---x17--|
// |---..---|
// |---x10--|
// |---x7---|

void FrameMap::initialize() {
  assert(!_init_done, "once");

  int i = 0;

  // caller save register
  map_register(i, x7);  r7_opr  = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x10); r10_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x11); r11_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x12); r12_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x13); r13_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x14); r14_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x15); r15_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x16); r16_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x17); r17_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x28); r28_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x29); r29_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x30); r30_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x31); r31_opr = LIR_OprFact::single_cpu(i); i++;

  // callee save register
  map_register(i, x9);  r9_opr  = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x18); r18_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x19); r19_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x20); r20_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x21); r21_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x22); r22_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x24); r24_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x25); r25_opr = LIR_OprFact::single_cpu(i); i++;
  map_register(i, x26); r26_opr = LIR_OprFact::single_cpu(i); i++;

  // special register
  map_register(i, x0);  zr_opr  = LIR_OprFact::single_cpu(i); i++;  // zr
  map_register(i, x1);  r1_opr  = LIR_OprFact::single_cpu(i); i++;  // ra
  map_register(i, x2);  r2_opr  = LIR_OprFact::single_cpu(i); i++;  // sp
  map_register(i, x3);  r3_opr  = LIR_OprFact::single_cpu(i); i++;  // gp
  map_register(i, x4);  r4_opr  = LIR_OprFact::single_cpu(i); i++;  // thread
  map_register(i, x8);  r8_opr  = LIR_OprFact::single_cpu(i); i++;  // fp
  map_register(i, x23); r23_opr = LIR_OprFact::single_cpu(i); i++;  // java thread
  map_register(i, x27); r27_opr = LIR_OprFact::single_cpu(i); i++;  // heapbase

  // tmp register
  map_register(i, x5);  r5_opr  = LIR_OprFact::single_cpu(i); i++;  // t0
  map_register(i, x6);  r6_opr  = LIR_OprFact::single_cpu(i); i++;  // t1

  t0_opr = r5_opr;
  t1_opr = r6_opr;
  t0_long_opr = LIR_OprFact::double_cpu(r5_opr->cpu_regnr(), r5_opr->cpu_regnr());
  t1_long_opr = LIR_OprFact::double_cpu(r6_opr->cpu_regnr(), r6_opr->cpu_regnr());

  long10_opr  = LIR_OprFact::double_cpu(r10_opr->cpu_regnr(), r10_opr->cpu_regnr());
  long11_opr  = LIR_OprFact::double_cpu(r11_opr->cpu_regnr(), r11_opr->cpu_regnr());

  fpu10_float_opr   = LIR_OprFact::single_fpu(10);
  fpu10_double_opr  = LIR_OprFact::double_fpu(10);

  i = 0;
  _caller_save_cpu_regs[i++]  = r7_opr;
  _caller_save_cpu_regs[i++]  = r10_opr;
  _caller_save_cpu_regs[i++]  = r11_opr;
  _caller_save_cpu_regs[i++]  = r12_opr;
  _caller_save_cpu_regs[i++]  = r13_opr;
  _caller_save_cpu_regs[i++]  = r14_opr;
  _caller_save_cpu_regs[i++]  = r15_opr;
  _caller_save_cpu_regs[i++]  = r16_opr;
  _caller_save_cpu_regs[i++]  = r17_opr;
  _caller_save_cpu_regs[i++]  = r28_opr;
  _caller_save_cpu_regs[i++]  = r29_opr;
  _caller_save_cpu_regs[i++]  = r30_opr;
  _caller_save_cpu_regs[i++]  = r31_opr;

  _init_done = true;

  zr_oop_opr  = as_oop_opr(x0);
  r1_oop_opr  = as_oop_opr(x1);
  r2_oop_opr  = as_oop_opr(x2);
  r3_oop_opr  = as_oop_opr(x3);
  r4_oop_opr  = as_oop_opr(x4);
  r5_oop_opr  = as_oop_opr(x5);
  r6_oop_opr  = as_oop_opr(x6);
  r7_oop_opr  = as_oop_opr(x7);
  r8_oop_opr  = as_oop_opr(x8);
  r9_oop_opr  = as_oop_opr(x9);
  r10_oop_opr = as_oop_opr(x10);
  r11_oop_opr = as_oop_opr(x11);
  r12_oop_opr = as_oop_opr(x12);
  r13_oop_opr = as_oop_opr(x13);
  r14_oop_opr = as_oop_opr(x14);
  r15_oop_opr = as_oop_opr(x15);
  r16_oop_opr = as_oop_opr(x16);
  r17_oop_opr = as_oop_opr(x17);
  r18_oop_opr = as_oop_opr(x18);
  r19_oop_opr = as_oop_opr(x19);
  r20_oop_opr = as_oop_opr(x20);
  r21_oop_opr = as_oop_opr(x21);
  r22_oop_opr = as_oop_opr(x22);
  r23_oop_opr = as_oop_opr(x23);
  r24_oop_opr = as_oop_opr(x24);
  r25_oop_opr = as_oop_opr(x25);
  r26_oop_opr = as_oop_opr(x26);
  r27_oop_opr = as_oop_opr(x27);
  r28_oop_opr = as_oop_opr(x28);
  r29_oop_opr = as_oop_opr(x29);
  r30_oop_opr = as_oop_opr(x30);
  r31_oop_opr = as_oop_opr(x31);

  r10_metadata_opr = as_metadata_opr(x10);
  r11_metadata_opr = as_metadata_opr(x11);
  r12_metadata_opr = as_metadata_opr(x12);
  r13_metadata_opr = as_metadata_opr(x13);
  r14_metadata_opr = as_metadata_opr(x14);
  r15_metadata_opr = as_metadata_opr(x15);

  sp_opr = as_pointer_opr(sp);
  fp_opr = as_pointer_opr(fp);

  VMRegPair regs;
  BasicType sig_bt = T_OBJECT;
  SharedRuntime::java_calling_convention(&sig_bt, &regs, 1);
  receiver_opr = as_oop_opr(regs.first()->as_Register());

  for (i = 0; i < nof_caller_save_fpu_regs; i++) {
    _caller_save_fpu_regs[i] = LIR_OprFact::single_fpu(i);
  }
}


Address FrameMap::make_new_address(ByteSize sp_offset) const {
  return Address(sp, in_bytes(sp_offset));
}


// ----------------mapping-----------------------
// all mapping is based on fp addressing, except for simple leaf methods where we access
// the locals sp based (and no frame is built)


// Frame for simple leaf methods (quick entries)
//
//   +----------+
//   | ret addr |   <- TOS
//   +----------+
//   | args     |
//   | ......   |

// Frame for standard methods
//
//   | .........|  <- TOS
//   | locals   |
//   +----------+
//   |  old fp, |
//   +----------+
//   | ret addr |
//   +----------+
//   |  args    |  <- FP
//   | .........|


// For OopMaps, map a local variable or spill index to an VMRegImpl name.
// This is the offset from sp() in the frame of the slot for the index,
// skewed by VMRegImpl::stack0 to indicate a stack location (vs.a register.)
//
//           framesize +
//           stack0         stack0          0  <- VMReg
//             |              | <registers> |
//  ...........|..............|.............|
//      0 1 2 3 x x 4 5 6 ... |                <- local indices
//      ^           ^        sp()                 ( x x indicate link
//      |           |                               and return addr)
//  arguments   non-argument locals


VMReg FrameMap::fpu_regname (int n) {
  // Return the OptoReg name for the fpu stack slot "n"
  // A spilled fpu stack slot comprises to two single-word OptoReg's.
  return as_FloatRegister(n)->as_VMReg();
}

LIR_Opr FrameMap::stack_pointer() {
  return FrameMap::sp_opr;
}

// JSR 292
LIR_Opr FrameMap::method_handle_invoke_SP_save_opr() {
  return LIR_OprFact::illegalOpr;  // Not needed on riscv
}

bool FrameMap::validate_frame() {
  return true;
}
