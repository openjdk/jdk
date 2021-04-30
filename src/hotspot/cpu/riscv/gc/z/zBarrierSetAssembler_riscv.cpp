/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "code/codeBlob.hpp"
#include "code/vmreg.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/zBarrierSetAssembler.hpp"
#include "gc/z/zBarrierSetRuntime.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/z/c1/zBarrierSetC1.hpp"
#endif // COMPILER1
#ifdef COMPILER2
#include "gc/z/c2/zBarrierSetC2.hpp"
#endif // COMPILER2

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#undef __
#define __ masm->

void ZBarrierSetAssembler::load_at(MacroAssembler* masm,
                                   DecoratorSet decorators,
                                   BasicType type,
                                   Register dst,
                                   Address src,
                                   Register tmp1,
                                   Register tmp_thread) {
  Unimplemented();
}

void ZBarrierSetAssembler::store_at(MacroAssembler* masm,
                                    DecoratorSet decorators,
                                    BasicType type,
                                    Address dst,
                                    Register val,
                                    Register tmp1,
                                    Register tmp2,
                                    Register tmp3) {
  Unimplemented();
}

void ZBarrierSetAssembler::arraycopy_prologue(MacroAssembler* masm,
                                              DecoratorSet decorators,
                                              bool is_oop,
                                              Register src,
                                              Register dst,
                                              Register count,
                                              RegSet saved_regs) {
  Unimplemented();
}

void ZBarrierSetAssembler::copy_load_at(MacroAssembler* masm,
                  DecoratorSet decorators,
                  BasicType type,
                  size_t bytes,
                  Register dst1,
                  Register dst2,
                  Address src,
                  Register tmp) {
  Unimplemented();
}

void ZBarrierSetAssembler::copy_store_at(MacroAssembler* masm,
                  DecoratorSet decorators,
                  BasicType type,
                  size_t bytes,
                  Address dst,
                  Register src1,
                  Register src2,
                  Register tmp1,
                  Register tmp2,
                  Register tmp3) {
  Unimplemented();
}


void ZBarrierSetAssembler::copy_load_at(MacroAssembler* masm,
                  DecoratorSet decorators,
                  BasicType type,
                  size_t bytes,
                  FloatRegister dst1,
                  FloatRegister dst2,
                  Address src,
                  Register tmp1,
                  Register tmp2,
                  FloatRegister vec_tmp) {
  Unimplemented();
}

void ZBarrierSetAssembler::copy_store_at(MacroAssembler* masm,
                  DecoratorSet decorators,
                  BasicType type,
                  size_t bytes,
                  Address dst,
                  FloatRegister src1,
                  FloatRegister src2,
                  Register tmp1,
                  Register tmp2,
                  Register tmp3,
                  FloatRegister vec_tmp1,
                  FloatRegister vec_tmp2,
                  FloatRegister vec_tmp3) {
  Unimplemented();
}

void ZBarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm,
                                                         Register jni_env,
                                                         Register robj,
                                                         Register tmp,
                                                         Label& slowpath) {
  Unimplemented();
}

void ZBarrierSetAssembler::check_oop(MacroAssembler* masm, Register obj, Register tmp1, Register tmp2, Label& error) {
  Unimplemented();
}

void ZBarrierSetAssembler::patch_barrier_relocation(address addr, int format) {
  Unimplemented();
}

#ifdef COMPILER2

OptoReg::Name ZBarrierSetAssembler::refine_register(const Node* node, OptoReg::Name opto_reg) {
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

class ZSaveLiveRegisters {
private:
  MacroAssembler* const _masm;
  RegSet                _gp_regs;
  FloatRegSet           _fp_regs;
  VectorRegSet          _vp_regs;

public:
  void initialize(ZLoadBarrierStubC2* stub) {
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

  ZSaveLiveRegisters(MacroAssembler* masm, ZLoadBarrierStubC2* stub) :
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

  ~ZSaveLiveRegisters() {
    // Restore registers
    __ pop_v(_vp_regs, sp);
    __ pop_fp(_fp_regs, sp);
    __ pop_reg(_gp_regs, sp);
  }
};

class ZSetupArguments {
private:
  MacroAssembler* const _masm;
  const Register        _ref;
  const Address         _ref_addr;

public:
  ZSetupArguments(MacroAssembler* masm, ZLoadBarrierStubC2* stub) :
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

  ~ZSetupArguments() {
    // Transfer result
    if (_ref != x10) {
      __ mv(_ref, x10);
    }
  }
};

#undef __
#define __ masm->

void ZBarrierSetAssembler::generate_c2_load_barrier_stub(MacroAssembler* masm, ZLoadBarrierStubC2* stub) const {
  Unimplemented();
}

void ZBarrierSetAssembler::generate_c2_store_barrier_stub(MacroAssembler* masm,
                                    ZStoreBarrierStubC2* stub) const {
  Unimplemented();
}

#undef __

#endif // COMPILER2

#ifdef COMPILER1
#undef __
#define __ ce->masm()->


void ZBarrierSetAssembler::generate_c1_color(LIR_Assembler* ce, LIR_Opr ref) const {
  Unimplemented();
}

void ZBarrierSetAssembler::generate_c1_uncolor(LIR_Assembler* ce, LIR_Opr ref) const {
  Unimplemented();
}

void ZBarrierSetAssembler::generate_c1_load_barrier_test(LIR_Assembler* ce,
                                                         LIR_Opr ref) const {
  Unimplemented();
}

void ZBarrierSetAssembler::generate_c1_load_barrier(LIR_Assembler* ce,
                                                    LIR_Opr ref,
                                                    ZLoadBarrierStubC1* stub,
                                                    bool on_non_strong) const {
  Unimplemented();
}

void ZBarrierSetAssembler::generate_c1_load_barrier_stub(LIR_Assembler* ce,
                                                         ZLoadBarrierStubC1* stub) const {
  Unimplemented();
}

#undef __
#define __ sasm->

void ZBarrierSetAssembler::generate_c1_load_barrier_runtime_stub(StubAssembler* sasm,
                                                                 DecoratorSet decorators) const {
  Unimplemented();
}

#undef __
#define __ ce->masm()->

void ZBarrierSetAssembler::generate_c1_store_barrier(LIR_Assembler* ce,
                                                     LIR_Address* addr,
                                                     LIR_Opr new_zaddress,
                                                     LIR_Opr new_zpointer,
                                                     ZStoreBarrierStubC1* stub) const {
  Unimplemented();
}

void ZBarrierSetAssembler::generate_c1_store_barrier_stub(LIR_Assembler* ce,
                                                          ZStoreBarrierStubC1* stub) const {
  Unimplemented();
}

#undef __
#define __ sasm->

void ZBarrierSetAssembler::generate_c1_store_barrier_runtime_stub(StubAssembler* sasm,
                                                                  bool self_healing) const {
  Unimplemented();
}

#undef __
#endif // COMPILER1
