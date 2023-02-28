/*
 * Copyright (c) 2000, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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

#ifndef CPU_RISCV_C1_LIRASSEMBLER_RISCV_HPP
#define CPU_RISCV_C1_LIRASSEMBLER_RISCV_HPP

// ArrayCopyStub needs access to bailout
friend class ArrayCopyStub;

private:

#include "c1_LIRAssembler_arith_riscv.hpp"
#include "c1_LIRAssembler_arraycopy_riscv.hpp"

  int array_element_size(BasicType type) const;

  static Register as_reg(LIR_Opr op) {
    return op->is_double_cpu() ? op->as_register_lo() : op->as_register();
  }

  Address as_Address(LIR_Address* addr, Register tmp);

  // helper functions which checks for overflow and sets bailout if it
  // occurs.  Always returns a valid embeddable pointer but in the
  // bailout case the pointer won't be to unique storage.
  address float_constant(float f);
  address double_constant(double d);
  address int_constant(jlong n);

  // Ensure we have a valid Address (base + offset) to a stack-slot.
  Address stack_slot_address(int index, uint shift, int adjust = 0);

  // Record the type of the receiver in ReceiverTypeData
  void type_profile_helper(Register mdo,
                           ciMethodData *md, ciProfileData *data,
                           Register recv, Label* update_done);

  void casw(Register addr, Register newval, Register cmpval);
  void caswu(Register addr, Register newval, Register cmpval);
  void casl(Register addr, Register newval, Register cmpval);

  void poll_for_safepoint(relocInfo::relocType rtype, CodeEmitInfo* info = NULL);

  void deoptimize_trap(CodeEmitInfo *info);

  enum {
    // See emit_static_call_stub for detail
    // CompiledStaticCall::to_interp_stub_size() (14) + CompiledStaticCall::to_trampoline_stub_size() (1 + 3 + address)
    _call_stub_size = 14 * NativeInstruction::instruction_size +
                      (NativeInstruction::instruction_size + NativeCallTrampolineStub::instruction_size),
    // See emit_exception_handler for detail
    // verify_not_null_oop + far_call + should_not_reach_here + invalidate_registers(DEBUG_ONLY)
    _exception_handler_size = DEBUG_ONLY(584) NOT_DEBUG(548), // or smaller
    // See emit_deopt_handler for detail
    // auipc (1) + far_jump (6 or 2)
    _deopt_handler_size = 1 * NativeInstruction::instruction_size +
                          6 * NativeInstruction::instruction_size // or smaller
  };

  void check_conflict(ciKlass* exact_klass, intptr_t current_klass, Register tmp,
                      Label &next, Label &none, Address mdo_addr);
  void check_no_conflict(ciKlass* exact_klass, intptr_t current_klass, Register tmp, Address mdo_addr, Label &next);

  void check_exact_klass(Register tmp, ciKlass* exact_klass);

  void check_null(Register tmp, Label &update, intptr_t current_klass, Address mdo_addr, bool do_update, Label &next);

  void (MacroAssembler::*add)(Register prev, RegisterOrConstant incr, Register addr);
  void (MacroAssembler::*xchg)(Register prev, Register newv, Register addr);

  void get_op(BasicType type);

  // emit_typecheck_helper sub functions
  void data_check(LIR_OpTypeCheck *op, ciMethodData **md, ciProfileData **data);
  void typecheck_helper_slowcheck(ciKlass* k, Register obj, Register Rtmp1,
                                  Register k_RInfo, Register klass_RInfo,
                                  Label* failure_target, Label* success_target);
  void profile_object(ciMethodData* md, ciProfileData* data, Register obj,
                      Register klass_RInfo, Label* obj_is_null);
  void typecheck_loaded(LIR_OpTypeCheck* op, ciKlass* k, Register k_RInfo);

  // emit_opTypeCheck sub functions
  void typecheck_lir_store(LIR_OpTypeCheck* op, bool should_profile);

  void type_profile(Register obj, ciMethodData* md, Register klass_RInfo, Register k_RInfo,
                    ciProfileData* data, Label* success, Label* failure,
                    Label& profile_cast_success, Label& profile_cast_failure);

  void lir_store_slowcheck(Register k_RInfo, Register klass_RInfo, Register Rtmp1,
                           Label* success_target, Label* failure_target);

  void const2reg_helper(LIR_Opr src);

  void emit_branch(LIR_Condition cmp_flag, LIR_Opr cmp1, LIR_Opr cmp2, Label& label, bool is_far, bool is_unordered);

  void logic_op_reg32(Register dst, Register left, Register right, LIR_Code code);
  void logic_op_reg(Register dst, Register left, Register right, LIR_Code code);
  void logic_op_imm(Register dst, Register left, int right, LIR_Code code);

public:

  void emit_cmove(LIR_Op4* op);

  void store_parameter(Register r, int offset_from_rsp_in_words);
  void store_parameter(jint c, int offset_from_rsp_in_words);

#endif // CPU_RISCV_C1_LIRASSEMBLER_RISCV_HPP
