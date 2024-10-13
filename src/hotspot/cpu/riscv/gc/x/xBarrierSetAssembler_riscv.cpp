/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "code/codeBlob.hpp"
#include "code/vmreg.inline.hpp"
#include "gc/x/xBarrier.inline.hpp"
#include "gc/x/xBarrierSet.hpp"
#include "gc/x/xBarrierSetAssembler.hpp"
#include "gc/x/xBarrierSetRuntime.hpp"
#include "gc/x/xThreadLocalData.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/x/c1/xBarrierSetC1.hpp"
#endif // COMPILER1
#ifdef COMPILER2
#include "gc/x/c2/xBarrierSetC2.hpp"
#endif // COMPILER2

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#undef __
#define __ masm->

void XBarrierSetAssembler::load_at(MacroAssembler* masm,
                                   DecoratorSet decorators,
                                   BasicType type,
                                   Register dst,
                                   Address src,
                                   Register tmp1,
                                   Register tmp2) {
  if (!XBarrierSet::barrier_needed(decorators, type)) {
    // Barrier not needed
    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp2);
    return;
  }

  assert_different_registers(t1, src.base());
  assert_different_registers(t0, t1, dst);

  Label done;

  // Load bad mask into temp register.
  __ la(t0, src);
  __ ld(t1, address_bad_mask_from_thread(xthread));
  __ ld(dst, Address(t0));

  // Test reference against bad mask. If mask bad, then we need to fix it up.
  __ andr(t1, dst, t1);
  __ beqz(t1, done);

  __ enter();

  __ push_call_clobbered_registers_except(RegSet::of(dst));

  if (c_rarg0 != dst) {
    __ mv(c_rarg0, dst);
  }

  __ mv(c_rarg1, t0);

  __ call_VM_leaf(XBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators), 2);

  // Make sure dst has the return value.
  if (dst != x10) {
    __ mv(dst, x10);
  }

  __ pop_call_clobbered_registers_except(RegSet::of(dst));
  __ leave();

  __ bind(done);
}

#ifdef ASSERT

void XBarrierSetAssembler::store_at(MacroAssembler* masm,
                                    DecoratorSet decorators,
                                    BasicType type,
                                    Address dst,
                                    Register val,
                                    Register tmp1,
                                    Register tmp2,
                                    Register tmp3) {
  // Verify value
  if (is_reference_type(type)) {
    // Note that src could be noreg, which means we
    // are storing null and can skip verification.
    if (val != noreg) {
      Label done;

      // tmp1, tmp2 and tmp3 are often set to noreg.
      RegSet savedRegs = RegSet::of(t0);
      __ push_reg(savedRegs, sp);

      __ ld(t0, address_bad_mask_from_thread(xthread));
      __ andr(t0, val, t0);
      __ beqz(t0, done);
      __ stop("Verify oop store failed");
      __ should_not_reach_here();
      __ bind(done);
      __ pop_reg(savedRegs, sp);
    }
  }

  // Store value
  BarrierSetAssembler::store_at(masm, decorators, type, dst, val, tmp1, tmp2, noreg);
}

#endif // ASSERT

void XBarrierSetAssembler::arraycopy_prologue(MacroAssembler* masm,
                                              DecoratorSet decorators,
                                              bool is_oop,
                                              Register src,
                                              Register dst,
                                              Register count,
                                              RegSet saved_regs) {
  if (!is_oop) {
    // Barrier not needed
    return;
  }

  BLOCK_COMMENT("XBarrierSetAssembler::arraycopy_prologue {");

  assert_different_registers(src, count, t0);

  __ push_reg(saved_regs, sp);

  if (count == c_rarg0 && src == c_rarg1) {
    // exactly backwards!!
    __ xorr(c_rarg0, c_rarg0, c_rarg1);
    __ xorr(c_rarg1, c_rarg0, c_rarg1);
    __ xorr(c_rarg0, c_rarg0, c_rarg1);
  } else {
    __ mv(c_rarg0, src);
    __ mv(c_rarg1, count);
  }

  __ call_VM_leaf(XBarrierSetRuntime::load_barrier_on_oop_array_addr(), 2);

  __ pop_reg(saved_regs, sp);

  BLOCK_COMMENT("} XBarrierSetAssembler::arraycopy_prologue");
}

void XBarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm,
                                                         Register jni_env,
                                                         Register robj,
                                                         Register tmp,
                                                         Label& slowpath) {
  BLOCK_COMMENT("XBarrierSetAssembler::try_resolve_jobject_in_native {");

  assert_different_registers(jni_env, robj, tmp);

  // Resolve jobject
  BarrierSetAssembler::try_resolve_jobject_in_native(masm, jni_env, robj, tmp, slowpath);

  // Compute the offset of address bad mask from the field of jni_environment
  long int bad_mask_relative_offset = (long int) (in_bytes(XThreadLocalData::address_bad_mask_offset()) -
                                                  in_bytes(JavaThread::jni_environment_offset()));

  // Load the address bad mask
  __ ld(tmp, Address(jni_env, bad_mask_relative_offset));

  // Check address bad mask
  __ andr(tmp, robj, tmp);
  __ bnez(tmp, slowpath);

  BLOCK_COMMENT("} XBarrierSetAssembler::try_resolve_jobject_in_native");
}

#ifdef COMPILER2

OptoReg::Name XBarrierSetAssembler::refine_register(const Node* node, OptoReg::Name opto_reg) {
  if (!OptoReg::is_reg(opto_reg)) {
    return OptoReg::Bad;
  }

  const VMReg vm_reg = OptoReg::as_VMReg(opto_reg);
  if (vm_reg->is_FloatRegister()) {
    return opto_reg & ~1;
  }

  return opto_reg;
}

#undef __
#define __ _masm->

class XSaveLiveRegisters {
private:
  MacroAssembler* const _masm;
  RegSet                _gp_regs;
  FloatRegSet           _fp_regs;
  VectorRegSet          _vp_regs;

public:
  void initialize(XLoadBarrierStubC2* stub) {
    // Record registers that needs to be saved/restored
    RegMaskIterator rmi(stub->live());
    while (rmi.has_next()) {
      const OptoReg::Name opto_reg = rmi.next();
      if (OptoReg::is_reg(opto_reg)) {
        const VMReg vm_reg = OptoReg::as_VMReg(opto_reg);
        if (vm_reg->is_Register()) {
          _gp_regs += RegSet::of(vm_reg->as_Register());
        } else if (vm_reg->is_FloatRegister()) {
          _fp_regs += FloatRegSet::of(vm_reg->as_FloatRegister());
        } else if (vm_reg->is_VectorRegister()) {
          const VMReg vm_reg_base = OptoReg::as_VMReg(opto_reg & ~(VectorRegister::max_slots_per_register - 1));
          _vp_regs += VectorRegSet::of(vm_reg_base->as_VectorRegister());
        } else {
          fatal("Unknown register type");
        }
      }
    }

    // Remove C-ABI SOE registers, tmp regs and _ref register that will be updated
    _gp_regs -= RegSet::range(x18, x27) + RegSet::of(x2) + RegSet::of(x8, x9) + RegSet::of(x5, stub->ref());
  }

  XSaveLiveRegisters(MacroAssembler* masm, XLoadBarrierStubC2* stub) :
      _masm(masm),
      _gp_regs(),
      _fp_regs(),
      _vp_regs() {
    // Figure out what registers to save/restore
    initialize(stub);

    // Save registers
    __ push_reg(_gp_regs, sp);
    __ push_fp(_fp_regs, sp);
    __ push_v(_vp_regs, sp);
  }

  ~XSaveLiveRegisters() {
    // Restore registers
    __ pop_v(_vp_regs, sp);
    __ pop_fp(_fp_regs, sp);
    __ pop_reg(_gp_regs, sp);
  }
};

class XSetupArguments {
private:
  MacroAssembler* const _masm;
  const Register        _ref;
  const Address         _ref_addr;

public:
  XSetupArguments(MacroAssembler* masm, XLoadBarrierStubC2* stub) :
      _masm(masm),
      _ref(stub->ref()),
      _ref_addr(stub->ref_addr()) {

    // Setup arguments
    if (_ref_addr.base() == noreg) {
      // No self healing
      if (_ref != c_rarg0) {
        __ mv(c_rarg0, _ref);
      }
      __ mv(c_rarg1, zr);
    } else {
      // Self healing
      if (_ref == c_rarg0) {
        // _ref is already at correct place
        __ la(c_rarg1, _ref_addr);
      } else if (_ref != c_rarg1) {
        // _ref is in wrong place, but not in c_rarg1, so fix it first
        __ la(c_rarg1, _ref_addr);
        __ mv(c_rarg0, _ref);
      } else if (_ref_addr.base() != c_rarg0) {
        assert(_ref == c_rarg1, "Mov ref first, vacating c_rarg0");
        __ mv(c_rarg0, _ref);
        __ la(c_rarg1, _ref_addr);
      } else {
        assert(_ref == c_rarg1, "Need to vacate c_rarg1 and _ref_addr is using c_rarg0");
        if (_ref_addr.base() == c_rarg0) {
          __ mv(t1, c_rarg1);
          __ la(c_rarg1, _ref_addr);
          __ mv(c_rarg0, t1);
        } else {
          ShouldNotReachHere();
        }
      }
    }
  }

  ~XSetupArguments() {
    // Transfer result
    if (_ref != x10) {
      __ mv(_ref, x10);
    }
  }
};

#undef __
#define __ masm->

void XBarrierSetAssembler::generate_c2_load_barrier_stub(MacroAssembler* masm, XLoadBarrierStubC2* stub) const {
  BLOCK_COMMENT("XLoadBarrierStubC2");

  // Stub entry
  __ bind(*stub->entry());

  {
    XSaveLiveRegisters save_live_registers(masm, stub);
    XSetupArguments setup_arguments(masm, stub);

    __ mv(t0, stub->slow_path());
    __ jalr(t0);
  }

  // Stub exit
  __ j(*stub->continuation());
}

#endif // COMPILER2

#ifdef COMPILER1
#undef __
#define __ ce->masm()->

void XBarrierSetAssembler::generate_c1_load_barrier_test(LIR_Assembler* ce,
                                                         LIR_Opr ref) const {
  assert_different_registers(xthread, ref->as_register(), t1);
  __ ld(t1, address_bad_mask_from_thread(xthread));
  __ andr(t1, t1, ref->as_register());
}

void XBarrierSetAssembler::generate_c1_load_barrier_stub(LIR_Assembler* ce,
                                                         XLoadBarrierStubC1* stub) const {
  // Stub entry
  __ bind(*stub->entry());

  Register ref = stub->ref()->as_register();
  Register ref_addr = noreg;
  Register tmp = noreg;

  if (stub->tmp()->is_valid()) {
    // Load address into tmp register
    ce->leal(stub->ref_addr(), stub->tmp());
    ref_addr = tmp = stub->tmp()->as_pointer_register();
  } else {
    // Address already in register
    ref_addr = stub->ref_addr()->as_address_ptr()->base()->as_pointer_register();
  }

  assert_different_registers(ref, ref_addr, noreg);

  // Save x10 unless it is the result or tmp register
  // Set up SP to accommodate parameters and maybe x10.
  if (ref != x10 && tmp != x10) {
    __ sub(sp, sp, 32);
    __ sd(x10, Address(sp, 16));
  } else {
    __ sub(sp, sp, 16);
  }

  // Setup arguments and call runtime stub
  ce->store_parameter(ref_addr, 1);
  ce->store_parameter(ref, 0);

  __ far_call(stub->runtime_stub());

  // Verify result
  __ verify_oop(x10);


  // Move result into place
  if (ref != x10) {
    __ mv(ref, x10);
  }

  // Restore x10 unless it is the result or tmp register
  if (ref != x10 && tmp != x10) {
    __ ld(x10, Address(sp, 16));
    __ add(sp, sp, 32);
  } else {
    __ add(sp, sp, 16);
  }

  // Stub exit
  __ j(*stub->continuation());
}

#undef __
#define __ sasm->

void XBarrierSetAssembler::generate_c1_load_barrier_runtime_stub(StubAssembler* sasm,
                                                                 DecoratorSet decorators) const {
  __ prologue("zgc_load_barrier stub", false);

  __ push_call_clobbered_registers_except(RegSet::of(x10));

  // Setup arguments
  __ load_parameter(0, c_rarg0);
  __ load_parameter(1, c_rarg1);

  __ call_VM_leaf(XBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators), 2);

  __ pop_call_clobbered_registers_except(RegSet::of(x10));

  __ epilogue();
}

#endif // COMPILER1

#undef __
#define __ masm->

void XBarrierSetAssembler::check_oop(MacroAssembler* masm, Register obj, Register tmp1, Register tmp2, Label& error) {
  // Check if mask is good.
  // verifies that XAddressBadMask & obj == 0
  __ ld(tmp2, Address(xthread, XThreadLocalData::address_bad_mask_offset()));
  __ andr(tmp1, obj, tmp2);
  __ bnez(tmp1, error);

  BarrierSetAssembler::check_oop(masm, obj, tmp1, tmp2, error);
}

#undef __
