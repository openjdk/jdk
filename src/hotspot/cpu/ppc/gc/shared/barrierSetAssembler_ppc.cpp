/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, 2022 SAP SE. All rights reserved.
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
#include "classfile/classLoaderData.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "interpreter/interp_masm.hpp"
#include "oops/compressedOops.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER2
#include "gc/shared/c2/barrierSetC2.hpp"
#endif // COMPILER2

#define __ masm->

void BarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                   Register base, RegisterOrConstant ind_or_offs, Register val,
                                   Register tmp1, Register tmp2, Register tmp3,
                                   MacroAssembler::PreservationLevel preservation_level) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  bool not_null = (decorators & IS_NOT_NULL) != 0;
  assert(in_heap || in_native, "where?");
  assert_different_registers(base, val, tmp1, tmp2, R0);

  switch (type) {
  case T_ARRAY:
  case T_OBJECT: {
    if (UseCompressedOops && in_heap) {
      Register co = tmp1;
      if (val == noreg) {
        __ li(co, 0);
      } else {
        co = not_null ? __ encode_heap_oop_not_null(tmp1, val) : __ encode_heap_oop(tmp1, val);
      }
      __ stw(co, ind_or_offs, base, tmp2);
    } else {
      if (val == noreg) {
        val = tmp1;
        __ li(val, 0);
      }
      __ std(val, ind_or_offs, base, tmp2);
    }
    break;
  }
  default: Unimplemented();
  }
}

void BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                  Register base, RegisterOrConstant ind_or_offs, Register dst,
                                  Register tmp1, Register tmp2,
                                  MacroAssembler::PreservationLevel preservation_level, Label *L_handle_null) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  bool not_null = (decorators & IS_NOT_NULL) != 0;
  assert(in_heap || in_native, "where?");
  assert_different_registers(ind_or_offs.register_or_noreg(), dst, R0);

  switch (type) {
  case T_ARRAY:
  case T_OBJECT: {
    if (UseCompressedOops && in_heap) {
      if (L_handle_null != nullptr) { // Label provided.
        __ lwz(dst, ind_or_offs, base);
        __ cmpwi(CCR0, dst, 0);
        __ beq(CCR0, *L_handle_null);
        __ decode_heap_oop_not_null(dst);
      } else if (not_null) { // Guaranteed to be not null.
        Register narrowOop = (tmp1 != noreg && CompressedOops::base_disjoint()) ? tmp1 : dst;
        __ lwz(narrowOop, ind_or_offs, base);
        __ decode_heap_oop_not_null(dst, narrowOop);
      } else { // Any oop.
        __ lwz(dst, ind_or_offs, base);
        __ decode_heap_oop(dst);
      }
    } else {
      __ ld(dst, ind_or_offs, base);
      if (L_handle_null != nullptr) {
        __ cmpdi(CCR0, dst, 0);
        __ beq(CCR0, *L_handle_null);
      }
    }
    break;
  }
  default: Unimplemented();
  }
}

// Generic implementation. GCs can provide an optimized one.
void BarrierSetAssembler::resolve_jobject(MacroAssembler* masm, Register value,
                                          Register tmp1, Register tmp2,
                                          MacroAssembler::PreservationLevel preservation_level) {
  Label done, tagged, weak_tagged, verify;
  __ cmpdi(CCR0, value, 0);
  __ beq(CCR0, done);         // Use null as-is.

  __ andi_(tmp1, value, JNIHandles::tag_mask);
  __ bne(CCR0, tagged);       // Test for tag.

  __ access_load_at(T_OBJECT, IN_NATIVE | AS_RAW, // no uncoloring
                    value, (intptr_t)0, value, tmp1, tmp2, preservation_level);
  __ b(verify);

  __ bind(tagged);
  __ andi_(tmp1, value, JNIHandles::TypeTag::weak_global);
  __ clrrdi(value, value, JNIHandles::tag_size); // Untag.
  __ bne(CCR0, weak_tagged);   // Test for jweak tag.

  __ access_load_at(T_OBJECT, IN_NATIVE,
                    value, (intptr_t)0, value, tmp1, tmp2, preservation_level);
  __ b(verify);

  __ bind(weak_tagged);
  __ access_load_at(T_OBJECT, IN_NATIVE | ON_PHANTOM_OOP_REF,
                    value, (intptr_t)0, value, tmp1, tmp2, preservation_level);

  __ bind(verify);
  __ verify_oop(value, FILE_AND_LINE);
  __ bind(done);
}

// Generic implementation. GCs can provide an optimized one.
void BarrierSetAssembler::resolve_global_jobject(MacroAssembler* masm, Register value,
                                          Register tmp1, Register tmp2,
                                          MacroAssembler::PreservationLevel preservation_level) {
  Label done;

  __ cmpdi(CCR0, value, 0);
  __ beq(CCR0, done);         // Use null as-is.

#ifdef ASSERT
  {
    Label valid_global_tag;
    __ andi_(tmp1, value, JNIHandles::TypeTag::global);
    __ bne(CCR0, valid_global_tag);       // Test for global tag.
    __ stop("non global jobject using resolve_global_jobject");
    __ bind(valid_global_tag);
  }
#endif

  __ clrrdi(value, value, JNIHandles::tag_size); // Untag.
  __ access_load_at(T_OBJECT, IN_NATIVE,
                    value, (intptr_t)0, value, tmp1, tmp2, preservation_level);
  __ verify_oop(value, FILE_AND_LINE);

  __ bind(done);
}

void BarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm, Register dst, Register jni_env,
                                                        Register obj, Register tmp, Label& slowpath) {
  __ clrrdi(dst, obj, JNIHandles::tag_size);
  __ ld(dst, 0, dst);         // Resolve (untagged) jobject.
}

void BarrierSetAssembler::nmethod_entry_barrier(MacroAssembler* masm, Register tmp) {
  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  if (bs_nm == nullptr) {
    return;
  }

  assert_different_registers(tmp, R0);

  __ block_comment("nmethod_entry_barrier (nmethod_entry_barrier) {");

  // Load stub address using toc (fixed instruction size, unlike load_const_optimized)
  __ calculate_address_from_global_toc(tmp, StubRoutines::method_entry_barrier(),
                                       true, true, false); // 2 instructions
  __ mtctr(tmp);

  // This is a compound instruction. Patching support is provided by NativeMovRegMem.
  // Actual patching is done in (platform-specific part of) BarrierSetNMethod.
  __ load_const32(tmp, 0 /* Value is patched */); // 2 instructions

  // Low order half of 64 bit value is currently used.
  __ ld(R0, in_bytes(bs_nm->thread_disarmed_guard_value_offset()), R16_thread);
  __ cmpw(CCR0, R0, tmp);

  __ bnectrl(CCR0);

  // Oops may have been changed. Make those updates observable.
  // "isync" can serve both, data and instruction patching.
  // But, many GCs don't modify nmethods during a concurrent phase.
  if (nmethod_patching_type() != NMethodPatchingType::stw_instruction_and_data_patch) {
    __ isync();
  }

  __ block_comment("} nmethod_entry_barrier (nmethod_entry_barrier)");
}

void BarrierSetAssembler::c2i_entry_barrier(MacroAssembler *masm, Register tmp1, Register tmp2, Register tmp3) {
  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  if (bs_nm == nullptr) {
    return;
  }

  assert_different_registers(tmp1, tmp2, tmp3);

  __ block_comment("c2i_entry_barrier (c2i_entry_barrier) {");

  Register tmp1_class_loader_data = tmp1;

  Label bad_call, skip_barrier;

  // Fast path: If no method is given, the call is definitely bad.
  __ cmpdi(CCR0, R19_method, 0);
  __ beq(CCR0, bad_call);

  // Load class loader data to determine whether the method's holder is concurrently unloading.
  __ load_method_holder(tmp1, R19_method);
  __ ld(tmp1_class_loader_data, in_bytes(InstanceKlass::class_loader_data_offset()), tmp1);

  // Fast path: If class loader is strong, the holder cannot be unloaded.
  __ lwz(tmp2, in_bytes(ClassLoaderData::keep_alive_offset()), tmp1_class_loader_data);
  __ cmpdi(CCR0, tmp2, 0);
  __ bne(CCR0, skip_barrier);

  // Class loader is weak. Determine whether the holder is still alive.
  __ ld(tmp2, in_bytes(ClassLoaderData::holder_offset()), tmp1_class_loader_data);
  __ resolve_weak_handle(tmp2, tmp1, tmp3, MacroAssembler::PreservationLevel::PRESERVATION_FRAME_LR_GP_FP_REGS);
  __ cmpdi(CCR0, tmp2, 0);
  __ bne(CCR0, skip_barrier);

  __ bind(bad_call);

  __ calculate_address_from_global_toc(tmp1, SharedRuntime::get_handle_wrong_method_stub(), true, true, false);
  __ mtctr(tmp1);
  __ bctr();

  __ bind(skip_barrier);

  __ block_comment("} c2i_entry_barrier (c2i_entry_barrier)");
}

void BarrierSetAssembler::check_oop(MacroAssembler *masm, Register oop, const char* msg) {
  __ verify_oop(oop, msg);
}

#ifdef COMPILER2

OptoReg::Name BarrierSetAssembler::refine_register(const Node* node, OptoReg::Name opto_reg) const {
  if (!OptoReg::is_reg(opto_reg)) {
    return OptoReg::Bad;
  }

  VMReg vm_reg = OptoReg::as_VMReg(opto_reg);
  if ((vm_reg->is_Register() || vm_reg ->is_FloatRegister()) && (opto_reg & 1) != 0) {
    return OptoReg::Bad;
  }

  return opto_reg;
}

#undef __
#define __ _masm->

SaveLiveRegisters::SaveLiveRegisters(MacroAssembler *masm, BarrierStubC2 *stub)
  : _masm(masm), _reg_mask(stub->preserve_set()) {

  const int register_save_size = iterate_over_register_mask(ACTION_COUNT_ONLY) * BytesPerWord;
  _frame_size = align_up(register_save_size, frame::alignment_in_bytes)
                + frame::native_abi_reg_args_size;

  __ save_LR_CR(R0);
  __ push_frame(_frame_size, R0);

  iterate_over_register_mask(ACTION_SAVE, _frame_size);
}

SaveLiveRegisters::~SaveLiveRegisters() {
  iterate_over_register_mask(ACTION_RESTORE, _frame_size);

  __ addi(R1_SP, R1_SP, _frame_size);
  __ restore_LR_CR(R0);
}

int SaveLiveRegisters::iterate_over_register_mask(IterationAction action, int offset) {
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

#endif // COMPILER2
