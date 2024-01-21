/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 SAP SE. All rights reserved.
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
#include "asm/register.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "code/codeBlob.hpp"
#include "code/vmreg.inline.hpp"
#include "gc/x/xBarrier.inline.hpp"
#include "gc/x/xBarrierSet.hpp"
#include "gc/x/xBarrierSetAssembler.hpp"
#include "gc/x/xBarrierSetRuntime.hpp"
#include "gc/x/xThreadLocalData.hpp"
#include "memory/resourceArea.hpp"
#include "register_ppc.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/x/c1/xBarrierSetC1.hpp"
#endif // COMPILER1
#ifdef COMPILER2
#include "gc/x/c2/xBarrierSetC2.hpp"
#endif // COMPILER2

#undef __
#define __ masm->

void XBarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                   Register base, RegisterOrConstant ind_or_offs, Register dst,
                                   Register tmp1, Register tmp2,
                                   MacroAssembler::PreservationLevel preservation_level, Label *L_handle_null) {
  __ block_comment("load_at (zgc) {");

  // Check whether a special gc barrier is required for this particular load
  // (e.g. whether it's a reference load or not)
  if (!XBarrierSet::barrier_needed(decorators, type)) {
    BarrierSetAssembler::load_at(masm, decorators, type, base, ind_or_offs, dst,
                                 tmp1, tmp2, preservation_level, L_handle_null);
    return;
  }

  if (ind_or_offs.is_register()) {
    assert_different_registers(base, ind_or_offs.as_register(), tmp1, tmp2, R0, noreg);
    assert_different_registers(dst, ind_or_offs.as_register(), tmp1, tmp2, R0, noreg);
  } else {
    assert_different_registers(base, tmp1, tmp2, R0, noreg);
    assert_different_registers(dst, tmp1, tmp2, R0, noreg);
  }

  /* ==== Load the pointer using the standard implementation for the actual heap access
          and the decompression of compressed pointers ==== */
  // Result of 'load_at' (standard implementation) will be written back to 'dst'.
  // As 'base' is required for the C-call, it must be reserved in case of a register clash.
  Register saved_base = base;
  if (base == dst) {
    __ mr(tmp2, base);
    saved_base = tmp2;
  }

  BarrierSetAssembler::load_at(masm, decorators, type, base, ind_or_offs, dst,
                               tmp1, noreg, preservation_level, L_handle_null);

  /* ==== Check whether pointer is dirty ==== */
  Label skip_barrier;

  // Load bad mask into scratch register.
  __ ld(tmp1, (intptr_t) XThreadLocalData::address_bad_mask_offset(), R16_thread);

  // The color bits of the to-be-tested pointer do not have to be equivalent to the 'bad_mask' testing bits.
  // A pointer is classified as dirty if any of the color bits that also match the bad mask is set.
  // Conversely, it follows that the logical AND of the bad mask and the pointer must be zero
  // if the pointer is not dirty.
  // Only dirty pointers must be processed by this barrier, so we can skip it in case the latter condition holds true.
  __ and_(tmp1, tmp1, dst);
  __ beq(CCR0, skip_barrier);

  /* ==== Invoke barrier ==== */
  int nbytes_save = 0;

  const bool needs_frame = preservation_level >= MacroAssembler::PRESERVATION_FRAME_LR;
  const bool preserve_gp_registers = preservation_level >= MacroAssembler::PRESERVATION_FRAME_LR_GP_REGS;
  const bool preserve_fp_registers = preservation_level >= MacroAssembler::PRESERVATION_FRAME_LR_GP_FP_REGS;

  const bool preserve_R3 = dst != R3_ARG1;

  if (needs_frame) {
    if (preserve_gp_registers) {
      nbytes_save = (preserve_fp_registers
                     ? MacroAssembler::num_volatile_gp_regs + MacroAssembler::num_volatile_fp_regs
                     : MacroAssembler::num_volatile_gp_regs) * BytesPerWord;
      nbytes_save -= preserve_R3 ? 0 : BytesPerWord;
      __ save_volatile_gprs(R1_SP, -nbytes_save, preserve_fp_registers, preserve_R3);
    }

    __ save_LR_CR(tmp1);
    __ push_frame_reg_args(nbytes_save, tmp1);
  }

  // Setup arguments
  if (saved_base != R3_ARG1) {
    __ mr_if_needed(R3_ARG1, dst);
    __ add(R4_ARG2, ind_or_offs, saved_base);
  } else if (dst != R4_ARG2) {
    __ add(R4_ARG2, ind_or_offs, saved_base);
    __ mr(R3_ARG1, dst);
  } else {
    __ add(R0, ind_or_offs, saved_base);
    __ mr(R3_ARG1, dst);
    __ mr(R4_ARG2, R0);
  }

  __ call_VM_leaf(XBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators));

  Register result = R3_RET;
  if (needs_frame) {
    __ pop_frame();
    __ restore_LR_CR(tmp1);

    if (preserve_R3) {
      __ mr(R0, R3_RET);
      result = R0;
    }

    if (preserve_gp_registers) {
      __ restore_volatile_gprs(R1_SP, -nbytes_save, preserve_fp_registers, preserve_R3);
    }
  }
  __ mr_if_needed(dst, result);

  __ bind(skip_barrier);
  __ block_comment("} load_at (zgc)");
}

#ifdef ASSERT
// The Z store barrier only verifies the pointers it is operating on and is thus a sole debugging measure.
void XBarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                    Register base, RegisterOrConstant ind_or_offs, Register val,
                                    Register tmp1, Register tmp2, Register tmp3,
                                    MacroAssembler::PreservationLevel preservation_level) {
  __ block_comment("store_at (zgc) {");

  // If the 'val' register is 'noreg', the to-be-stored value is a null pointer.
  if (is_reference_type(type) && val != noreg) {
    __ ld(tmp1, in_bytes(XThreadLocalData::address_bad_mask_offset()), R16_thread);
    __ and_(tmp1, tmp1, val);
    __ asm_assert_eq("Detected dirty pointer on the heap in Z store barrier");
  }

  // Store value
  BarrierSetAssembler::store_at(masm, decorators, type, base, ind_or_offs, val, tmp1, tmp2, tmp3, preservation_level);

  __ block_comment("} store_at (zgc)");
}
#endif // ASSERT

void XBarrierSetAssembler::arraycopy_prologue(MacroAssembler *masm, DecoratorSet decorators, BasicType component_type,
                                              Register src, Register dst, Register count,
                                              Register preserve1, Register preserve2) {
  __ block_comment("arraycopy_prologue (zgc) {");

  /* ==== Check whether a special gc barrier is required for this particular load ==== */
  if (!is_reference_type(component_type)) {
    return;
  }

  Label skip_barrier;

  // Fast path: Array is of length zero
  __ cmpdi(CCR0, count, 0);
  __ beq(CCR0, skip_barrier);

  /* ==== Ensure register sanity ==== */
  Register tmp_R11 = R11_scratch1;

  assert_different_registers(src, dst, count, tmp_R11, noreg);
  if (preserve1 != noreg) {
    // Not technically required, but unlikely being intended.
    assert_different_registers(preserve1, preserve2);
  }

  /* ==== Invoke barrier (slowpath) ==== */
  int nbytes_save = 0;

  {
    assert(!noreg->is_volatile(), "sanity");

    if (preserve1->is_volatile()) {
      __ std(preserve1, -BytesPerWord * ++nbytes_save, R1_SP);
    }

    if (preserve2->is_volatile() && preserve1 != preserve2) {
      __ std(preserve2, -BytesPerWord * ++nbytes_save, R1_SP);
    }

    __ std(src, -BytesPerWord * ++nbytes_save, R1_SP);
    __ std(dst, -BytesPerWord * ++nbytes_save, R1_SP);
    __ std(count, -BytesPerWord * ++nbytes_save, R1_SP);

    __ save_LR_CR(tmp_R11);
    __ push_frame_reg_args(nbytes_save, tmp_R11);
  }

  // XBarrierSetRuntime::load_barrier_on_oop_array_addr(src, count)
  if (count == R3_ARG1) {
    if (src == R4_ARG2) {
      // Arguments are provided in reverse order
      __ mr(tmp_R11, count);
      __ mr(R3_ARG1, src);
      __ mr(R4_ARG2, tmp_R11);
    } else {
      __ mr(R4_ARG2, count);
      __ mr(R3_ARG1, src);
    }
  } else {
    __ mr_if_needed(R3_ARG1, src);
    __ mr_if_needed(R4_ARG2, count);
  }

  __ call_VM_leaf(XBarrierSetRuntime::load_barrier_on_oop_array_addr());

  __ pop_frame();
  __ restore_LR_CR(tmp_R11);

  {
    __ ld(count, -BytesPerWord * nbytes_save--, R1_SP);
    __ ld(dst, -BytesPerWord * nbytes_save--, R1_SP);
    __ ld(src, -BytesPerWord * nbytes_save--, R1_SP);

    if (preserve2->is_volatile() && preserve1 != preserve2) {
      __ ld(preserve2, -BytesPerWord * nbytes_save--, R1_SP);
    }

    if (preserve1->is_volatile()) {
      __ ld(preserve1, -BytesPerWord * nbytes_save--, R1_SP);
    }
  }

  __ bind(skip_barrier);

  __ block_comment("} arraycopy_prologue (zgc)");
}

void XBarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm, Register dst, Register jni_env,
                                                         Register obj, Register tmp, Label& slowpath) {
  __ block_comment("try_resolve_jobject_in_native (zgc) {");

  assert_different_registers(jni_env, obj, tmp);

  // Resolve the pointer using the standard implementation for weak tag handling and pointer verification.
  BarrierSetAssembler::try_resolve_jobject_in_native(masm, dst, jni_env, obj, tmp, slowpath);

  // Check whether pointer is dirty.
  __ ld(tmp,
        in_bytes(XThreadLocalData::address_bad_mask_offset() - JavaThread::jni_environment_offset()),
        jni_env);

  __ and_(tmp, obj, tmp);
  __ bne(CCR0, slowpath);

  __ block_comment("} try_resolve_jobject_in_native (zgc)");
}

#undef __

#ifdef COMPILER1
#define __ ce->masm()->

// Code emitted by LIR node "LIR_OpXLoadBarrierTest" which in turn is emitted by XBarrierSetC1::load_barrier.
// The actual compare and branch instructions are represented as stand-alone LIR nodes.
void XBarrierSetAssembler::generate_c1_load_barrier_test(LIR_Assembler* ce,
                                                         LIR_Opr ref) const {
  __ block_comment("load_barrier_test (zgc) {");

  __ ld(R0, in_bytes(XThreadLocalData::address_bad_mask_offset()), R16_thread);
  __ andr(R0, R0, ref->as_pointer_register());
  __ cmpdi(CCR5 /* as mandated by LIR node */, R0, 0);

  __ block_comment("} load_barrier_test (zgc)");
}

// Code emitted by code stub "XLoadBarrierStubC1" which in turn is emitted by XBarrierSetC1::load_barrier.
// Invokes the runtime stub which is defined just below.
void XBarrierSetAssembler::generate_c1_load_barrier_stub(LIR_Assembler* ce,
                                                         XLoadBarrierStubC1* stub) const {
  __ block_comment("c1_load_barrier_stub (zgc) {");

  __ bind(*stub->entry());

  /* ==== Determine relevant data registers and ensure register sanity ==== */
  Register ref = stub->ref()->as_register();
  Register ref_addr = noreg;

  // Determine reference address
  if (stub->tmp()->is_valid()) {
    // 'tmp' register is given, so address might have an index or a displacement.
    ce->leal(stub->ref_addr(), stub->tmp());
    ref_addr = stub->tmp()->as_pointer_register();
  } else {
    // 'tmp' register is not given, so address must have neither an index nor a displacement.
    // The address' base register is thus usable as-is.
    assert(stub->ref_addr()->as_address_ptr()->disp() == 0, "illegal displacement");
    assert(!stub->ref_addr()->as_address_ptr()->index()->is_valid(), "illegal index");

    ref_addr = stub->ref_addr()->as_address_ptr()->base()->as_pointer_register();
  }

  assert_different_registers(ref, ref_addr, R0, noreg);

  /* ==== Invoke stub ==== */
  // Pass arguments via stack. The stack pointer will be bumped by the stub.
  __ std(ref, (intptr_t) -1 * BytesPerWord, R1_SP);
  __ std(ref_addr, (intptr_t) -2 * BytesPerWord, R1_SP);

  __ load_const_optimized(R0, stub->runtime_stub());
  __ call_stub(R0);

  // The runtime stub passes the result via the R0 register, overriding the previously-loaded stub address.
  __ mr_if_needed(ref, R0);
  __ b(*stub->continuation());

  __ block_comment("} c1_load_barrier_stub (zgc)");
}

#undef __
#define __ sasm->

// Code emitted by runtime code stub which in turn is emitted by XBarrierSetC1::generate_c1_runtime_stubs.
void XBarrierSetAssembler::generate_c1_load_barrier_runtime_stub(StubAssembler* sasm,
                                                                 DecoratorSet decorators) const {
  __ block_comment("c1_load_barrier_runtime_stub (zgc) {");

  const int stack_parameters = 2;
  const int nbytes_save = (MacroAssembler::num_volatile_regs + stack_parameters) * BytesPerWord;

  __ save_volatile_gprs(R1_SP, -nbytes_save);
  __ save_LR_CR(R0);

  // Load arguments back again from the stack.
  __ ld(R3_ARG1, (intptr_t) -1 * BytesPerWord, R1_SP); // ref
  __ ld(R4_ARG2, (intptr_t) -2 * BytesPerWord, R1_SP); // ref_addr

  __ push_frame_reg_args(nbytes_save, R0);

  __ call_VM_leaf(XBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators));

  __ verify_oop(R3_RET, "Bad pointer after barrier invocation");
  __ mr(R0, R3_RET);

  __ pop_frame();
  __ restore_LR_CR(R3_RET);
  __ restore_volatile_gprs(R1_SP, -nbytes_save);

  __ blr();

  __ block_comment("} c1_load_barrier_runtime_stub (zgc)");
}

#undef __
#endif // COMPILER1

#ifdef COMPILER2

OptoReg::Name XBarrierSetAssembler::refine_register(const Node* node, OptoReg::Name opto_reg) const {
  if (!OptoReg::is_reg(opto_reg)) {
    return OptoReg::Bad;
  }

  VMReg vm_reg = OptoReg::as_VMReg(opto_reg);
  if ((vm_reg->is_Register() || vm_reg ->is_FloatRegister()) && (opto_reg & 1) != 0) {
    return OptoReg::Bad;
  }

  return opto_reg;
}

#define __ _masm->

class XSaveLiveRegisters {
  MacroAssembler* _masm;
  RegMask _reg_mask;
  Register _result_reg;
  int _frame_size;

 public:
  XSaveLiveRegisters(MacroAssembler *masm, XLoadBarrierStubC2 *stub)
      : _masm(masm), _reg_mask(stub->live()), _result_reg(stub->ref()) {

    const int register_save_size = iterate_over_register_mask(ACTION_COUNT_ONLY) * BytesPerWord;
    _frame_size = align_up(register_save_size, frame::alignment_in_bytes)
                  + frame::native_abi_reg_args_size;

    __ save_LR_CR(R0);
    __ push_frame(_frame_size, R0);

    iterate_over_register_mask(ACTION_SAVE, _frame_size);
  }

  ~XSaveLiveRegisters() {
    iterate_over_register_mask(ACTION_RESTORE, _frame_size);

    __ addi(R1_SP, R1_SP, _frame_size);
    __ restore_LR_CR(R0);
  }

 private:
  enum IterationAction : int {
    ACTION_SAVE,
    ACTION_RESTORE,
    ACTION_COUNT_ONLY
  };

  int iterate_over_register_mask(IterationAction action, int offset = 0) {
    int reg_save_index = 0;
    RegMaskIterator live_regs_iterator(_reg_mask);

    while(live_regs_iterator.has_next()) {
      const OptoReg::Name opto_reg = live_regs_iterator.next();

      // Filter out stack slots (spilled registers, i.e., stack-allocated registers).
      if (!OptoReg::is_reg(opto_reg)) {
        continue;
      }

      const VMReg vm_reg = OptoReg::as_VMReg(opto_reg);
      if (vm_reg->is_Register()) {
        Register std_reg = vm_reg->as_Register();

        // '_result_reg' will hold the end result of the operation. Its content must thus not be preserved.
        if (std_reg == _result_reg) {
          continue;
        }

        if (std_reg->encoding() >= R2->encoding() && std_reg->encoding() <= R12->encoding()) {
          reg_save_index++;

          if (action == ACTION_SAVE) {
            _masm->std(std_reg, offset - reg_save_index * BytesPerWord, R1_SP);
          } else if (action == ACTION_RESTORE) {
            _masm->ld(std_reg, offset - reg_save_index * BytesPerWord, R1_SP);
          } else {
            assert(action == ACTION_COUNT_ONLY, "Sanity");
          }
        }
      } else if (vm_reg->is_FloatRegister()) {
        FloatRegister fp_reg = vm_reg->as_FloatRegister();
        if (fp_reg->encoding() >= F0->encoding() && fp_reg->encoding() <= F13->encoding()) {
          reg_save_index++;

          if (action == ACTION_SAVE) {
            _masm->stfd(fp_reg, offset - reg_save_index * BytesPerWord, R1_SP);
          } else if (action == ACTION_RESTORE) {
            _masm->lfd(fp_reg, offset - reg_save_index * BytesPerWord, R1_SP);
          } else {
            assert(action == ACTION_COUNT_ONLY, "Sanity");
          }
        }
      } else if (vm_reg->is_ConditionRegister()) {
        // NOP. Conditions registers are covered by save_LR_CR
      } else if (vm_reg->is_VectorSRegister()) {
        assert(SuperwordUseVSX, "or should not reach here");
        VectorSRegister vs_reg = vm_reg->as_VectorSRegister();
        if (vs_reg->encoding() >= VSR32->encoding() && vs_reg->encoding() <= VSR51->encoding()) {
          reg_save_index += 2;

          Register spill_addr = R0;
          if (action == ACTION_SAVE) {
            _masm->addi(spill_addr, R1_SP, offset - reg_save_index * BytesPerWord);
            _masm->stxvd2x(vs_reg, spill_addr);
          } else if (action == ACTION_RESTORE) {
            _masm->addi(spill_addr, R1_SP, offset - reg_save_index * BytesPerWord);
            _masm->lxvd2x(vs_reg, spill_addr);
          } else {
            assert(action == ACTION_COUNT_ONLY, "Sanity");
          }
        }
      } else {
        if (vm_reg->is_SpecialRegister()) {
          fatal("Special registers are unsupported. Found register %s", vm_reg->name());
        } else {
          fatal("Register type is not known");
        }
      }
    }

    return reg_save_index;
  }
};

#undef __
#define __ _masm->

class XSetupArguments {
  MacroAssembler* const _masm;
  const Register        _ref;
  const Address         _ref_addr;

 public:
  XSetupArguments(MacroAssembler* masm, XLoadBarrierStubC2* stub) :
      _masm(masm),
      _ref(stub->ref()),
      _ref_addr(stub->ref_addr()) {

    // Desired register/argument configuration:
    // _ref: R3_ARG1
    // _ref_addr: R4_ARG2

    // '_ref_addr' can be unspecified. In that case, the barrier will not heal the reference.
    if (_ref_addr.base() == noreg) {
      assert_different_registers(_ref, R0, noreg);

      __ mr_if_needed(R3_ARG1, _ref);
      __ li(R4_ARG2, 0);
    } else {
      assert_different_registers(_ref, _ref_addr.base(), R0, noreg);
      assert(!_ref_addr.index()->is_valid(), "reference addresses must not contain an index component");

      if (_ref != R4_ARG2) {
        // Calculate address first as the address' base register might clash with R4_ARG2
        __ addi(R4_ARG2, _ref_addr.base(), _ref_addr.disp());
        __ mr_if_needed(R3_ARG1, _ref);
      } else if (_ref_addr.base() != R3_ARG1) {
        __ mr(R3_ARG1, _ref);
        __ addi(R4_ARG2, _ref_addr.base(), _ref_addr.disp()); // Clobbering _ref
      } else {
        // Arguments are provided in inverse order (i.e. _ref == R4_ARG2, _ref_addr == R3_ARG1)
        __ mr(R0, _ref);
        __ addi(R4_ARG2, _ref_addr.base(), _ref_addr.disp());
        __ mr(R3_ARG1, R0);
      }
    }
  }
};

#undef __
#define __ masm->

void XBarrierSetAssembler::generate_c2_load_barrier_stub(MacroAssembler* masm, XLoadBarrierStubC2* stub) const {
  __ block_comment("generate_c2_load_barrier_stub (zgc) {");

  __ bind(*stub->entry());

  Register ref = stub->ref();
  Address ref_addr = stub->ref_addr();

  assert_different_registers(ref, ref_addr.base());

  {
    XSaveLiveRegisters save_live_registers(masm, stub);
    XSetupArguments setup_arguments(masm, stub);

    __ call_VM_leaf(stub->slow_path());
    __ mr_if_needed(ref, R3_RET);
  }

  __ b(*stub->continuation());

  __ block_comment("} generate_c2_load_barrier_stub (zgc)");
}

#undef __
#endif // COMPILER2
