/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "memory/universe.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/stubRoutines.hpp"

#ifdef COMPILER2
#include "gc/shared/c2/barrierSetC2.hpp"
#endif // COMPILER2

#define __ masm->

void BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                  Register dst, Address src, Register tmp1, Register tmp2, Register tmp3) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  switch (type) {
  case T_OBJECT:
  case T_ARRAY: {
    if (in_heap) {
      {
        __ ldr(dst, src);
      }
    } else {
      assert(in_native, "why else?");
      __ ldr(dst, src);
    }
    break;
  }
  case T_BOOLEAN: __ ldrb      (dst, src); break;
  case T_BYTE:    __ ldrsb     (dst, src); break;
  case T_CHAR:    __ ldrh      (dst, src); break;
  case T_SHORT:   __ ldrsh     (dst, src); break;
  case T_INT:     __ ldr_s32   (dst, src); break;
  case T_ADDRESS: __ ldr       (dst, src); break;
  case T_LONG:
    assert(dst == noreg, "only to ltos");
    __ add                     (src.index(), src.index(), src.base());
    __ ldmia                   (src.index(), RegisterSet(R0_tos_lo) | RegisterSet(R1_tos_hi));
    break;
#ifdef __SOFTFP__
  case T_FLOAT:
    assert(dst == noreg, "only to ftos");
    __ ldr                     (R0_tos, src);
    break;
  case T_DOUBLE:
    assert(dst == noreg, "only to dtos");
    __ add                     (src.index(), src.index(), src.base());
    __ ldmia                   (src.index(), RegisterSet(R0_tos_lo) | RegisterSet(R1_tos_hi));
    break;
#else
  case T_FLOAT:
    assert(dst == noreg, "only to ftos");
    __ add(src.index(), src.index(), src.base());
    __ ldr_float               (S0_tos, src.index());
    break;
  case T_DOUBLE:
    assert(dst == noreg, "only to dtos");
    __ add                     (src.index(), src.index(), src.base());
    __ ldr_double              (D0_tos, src.index());
    break;
#endif
  default: Unimplemented();
  }

}

void BarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                   Address obj, Register val, Register tmp1, Register tmp2, Register tmp3, bool is_null) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  switch (type) {
  case T_OBJECT:
  case T_ARRAY: {
    if (in_heap) {
      {
      __ str(val, obj);
      }
    } else {
      assert(in_native, "why else?");
      __ str(val, obj);
    }
    break;
  }
  case T_BOOLEAN:
    __ and_32(val, val, 1);
    __ strb(val, obj);
    break;
  case T_BYTE:    __ strb      (val, obj); break;
  case T_CHAR:    __ strh      (val, obj); break;
  case T_SHORT:   __ strh      (val, obj); break;
  case T_INT:     __ str       (val, obj); break;
  case T_ADDRESS: __ str       (val, obj); break;
  case T_LONG:
    assert(val == noreg, "only tos");
    __ add                     (obj.index(), obj.index(), obj.base());
    __ stmia                   (obj.index(), RegisterSet(R0_tos_lo) | RegisterSet(R1_tos_hi));
    break;
#ifdef __SOFTFP__
  case T_FLOAT:
    assert(val == noreg, "only tos");
    __ str (R0_tos,  obj);
    break;
  case T_DOUBLE:
    assert(val == noreg, "only tos");
    __ add                     (obj.index(), obj.index(), obj.base());
    __ stmia                   (obj.index(), RegisterSet(R0_tos_lo) | RegisterSet(R1_tos_hi));
    break;
#else
  case T_FLOAT:
    assert(val == noreg, "only tos");
    __ add                     (obj.index(), obj.index(), obj.base());
    __ str_float               (S0_tos,  obj.index());
    break;
  case T_DOUBLE:
    assert(val == noreg, "only tos");
    __ add                     (obj.index(), obj.index(), obj.base());
    __ str_double              (D0_tos,  obj.index());
    break;
#endif
  default: Unimplemented();
  }
}

// Puts address of allocated object into register `obj` and end of allocated object into register `obj_end`.
void BarrierSetAssembler::tlab_allocate(MacroAssembler* masm, Register obj, Register obj_end, Register tmp1,
                                 RegisterOrConstant size_expression, Label& slow_case) {
  const Register tlab_end = tmp1;
  assert_different_registers(obj, obj_end, tlab_end);

  __ ldr(obj, Address(Rthread, JavaThread::tlab_top_offset()));
  __ ldr(tlab_end, Address(Rthread, JavaThread::tlab_end_offset()));
  __ add_rc(obj_end, obj, size_expression);
  __ cmp(obj_end, tlab_end);
  __ b(slow_case, hi);
  __ str(obj_end, Address(Rthread, JavaThread::tlab_top_offset()));
}

void BarrierSetAssembler::nmethod_entry_barrier(MacroAssembler* masm) {

  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();

  Register tmp0 = Rtemp;
  Register tmp1 = R5; // must be callee-save register

  // The are no GCs that require memory barrier on arm32 now
#ifdef ASSERT
  NMethodPatchingType patching_type = nmethod_patching_type();
  assert(patching_type == NMethodPatchingType::stw_instruction_and_data_patch, "Unsupported patching type");
#endif

  Label skip, guard;
  Address thread_disarmed_addr(Rthread, in_bytes(bs_nm->thread_disarmed_guard_value_offset()));

  __ block_comment("nmethod_barrier begin");
  __ ldr_label(tmp0, guard);

  // No memory barrier here
  __ ldr(tmp1, thread_disarmed_addr);
  __ cmp(tmp0, tmp1);
  __ b(skip, eq);

  __ mov_address(tmp0, StubRoutines::method_entry_barrier());
  __ call(tmp0);
  __ b(skip);

  __ bind(guard);

  // nmethod guard value. Skipped over in common case.
  __ emit_int32(0); // initial armed value, will be reset later

  __ bind(skip);
  __ block_comment("nmethod_barrier end");
}

#ifdef COMPILER2

OptoReg::Name BarrierSetAssembler::refine_register(const Node* node, OptoReg::Name opto_reg) {
  if (!OptoReg::is_reg(opto_reg)) {
    return OptoReg::Bad;
  }

  const VMReg vm_reg = OptoReg::as_VMReg(opto_reg);
  if (!vm_reg->is_valid()){
    // skip APSR and FPSCR
    return OptoReg::Bad;
  }

  return opto_reg;
}

void SaveLiveRegisters::initialize(BarrierStubC2* stub) {
  // Record registers that needs to be saved/restored
  RegMaskIterator rmi(stub->preserve_set());
  while (rmi.has_next()) {
    const OptoReg::Name opto_reg = rmi.next();
    if (OptoReg::is_reg(opto_reg)) {
      const VMReg vm_reg = OptoReg::as_VMReg(opto_reg);
      if (vm_reg->is_Register()) {
        gp_regs += RegSet::of(vm_reg->as_Register());
      } else if (vm_reg->is_FloatRegister()) {
        fp_regs += FloatRegSet::of(vm_reg->as_FloatRegister());
      } else {
        fatal("Unknown register type");
      }
    }
  }
  // Remove C-ABI SOE registers that will be updated
  gp_regs -= RegSet::range(R4, R11) + RegSet::of(R13, R15);

  // Remove C-ABI SOE fp registers
  fp_regs -= FloatRegSet::range(S16, S31);
}

SaveLiveRegisters::SaveLiveRegisters(MacroAssembler* masm, BarrierStubC2* stub)
  : masm(masm),
    gp_regs(),
    fp_regs() {
  // Figure out what registers to save/restore
  initialize(stub);

  // Save registers
  if (gp_regs.size() > 0) __ push(RegisterSet::from(gp_regs));
  if (fp_regs.size() > 0) __ fpush(FloatRegisterSet::from(fp_regs));
}

SaveLiveRegisters::~SaveLiveRegisters() {
  // Restore registers
  if (fp_regs.size() > 0) __ fpop(FloatRegisterSet::from(fp_regs));
  if (gp_regs.size() > 0) __ pop(RegisterSet::from(gp_regs));
}
#endif // COMPILER2
