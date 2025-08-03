/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018 SAP SE. All rights reserved.
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

#include "asm/macroAssembler.inline.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "interpreter/interp_masm.hpp"
#include "oops/compressedOops.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER2
#include "gc/shared/c2/barrierSetC2.hpp"
#endif // COMPILER2

#define __ masm->

void BarrierSetAssembler::arraycopy_epilogue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                             Register dst, Register count, bool do_return) {
  if (do_return) { __ z_br(Z_R14); }
}

void BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                  const Address& addr, Register dst, Register tmp1, Register tmp2, Label *L_handle_null) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  bool not_null = (decorators & IS_NOT_NULL) != 0;
  assert(in_heap || in_native, "where?");

  switch (type) {
  case T_ARRAY:
  case T_OBJECT: {
    if (UseCompressedOops && in_heap) {
      __ z_llgf(dst, addr);
      if (L_handle_null != nullptr) { // Label provided.
        __ compareU32_and_branch(dst, (intptr_t)0, Assembler::bcondEqual, *L_handle_null);
        __ oop_decoder(dst, dst, false);
      } else {
        __ oop_decoder(dst, dst, !not_null);
      }
    } else {
      __ z_lg(dst, addr);
      if (L_handle_null != nullptr) {
        __ compareU64_and_branch(dst, (intptr_t)0, Assembler::bcondEqual, *L_handle_null);
      }
    }
    break;
  }
  default: Unimplemented();
  }
}

void BarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                   const Address& addr, Register val, Register tmp1, Register tmp2, Register tmp3) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  bool not_null = (decorators & IS_NOT_NULL) != 0;
  assert(in_heap || in_native, "where?");
  assert_different_registers(val, tmp1, tmp2);

  switch (type) {
  case T_ARRAY:
  case T_OBJECT: {
    if (UseCompressedOops && in_heap) {
      if (val == noreg) {
        __ clear_mem(addr, 4);
      } else if (CompressedOops::mode() == CompressedOops::UnscaledNarrowOop) {
        __ z_st(val, addr);
      } else {
        Register tmp = (tmp1 != Z_R1) ? tmp1 : tmp2; // Avoid tmp == Z_R1 (see oop_encoder).
        __ oop_encoder(tmp, val, !not_null);
        __ z_st(tmp, addr);
      }
    } else {
      if (val == noreg) {
        __ clear_mem(addr, 8);
      } else {
        __ z_stg(val, addr);
      }
    }
    break;
  }
  default: Unimplemented();
  }
}

// Generic implementation. GCs can provide an optimized one.
void BarrierSetAssembler::resolve_jobject(MacroAssembler* masm, Register value, Register tmp1, Register tmp2) {

  assert_different_registers(value, tmp1, tmp2);
  NearLabel done, weak_tag, verify, tagged;
  __ z_ltgr(value, value);
  __ z_bre(done);          // Use null result as-is.

  __ z_tmll(value, JNIHandles::tag_mask);
  __ z_btrue(tagged); // not zero

  // Resolve Local handle
  __ access_load_at(T_OBJECT, IN_NATIVE | AS_RAW, Address(value, 0), value, tmp1, tmp2);
  __ z_bru(verify);

  __ bind(tagged);
  __ testbit(value, exact_log2(JNIHandles::TypeTag::weak_global)); // test for weak tag
  __ z_btrue(weak_tag);

  // resolve global handle
  __ access_load_at(T_OBJECT, IN_NATIVE, Address(value, -JNIHandles::TypeTag::global), value, tmp1, tmp2);
  __ z_bru(verify);

  __ bind(weak_tag);
  // resolve jweak.
  __ access_load_at(T_OBJECT, IN_NATIVE | ON_PHANTOM_OOP_REF,
                    Address(value, -JNIHandles::TypeTag::weak_global), value, tmp1, tmp2);
  __ bind(verify);
  __ verify_oop(value, FILE_AND_LINE);
  __ bind(done);
}

// Generic implementation. GCs can provide an optimized one.
void BarrierSetAssembler::resolve_global_jobject(MacroAssembler* masm, Register value, Register tmp1, Register tmp2) {
  assert_different_registers(value, tmp1, tmp2);
  NearLabel done;

  __ z_ltgr(value, value);
  __ z_bre(done); // use null as-is.

#ifdef ASSERT
  {
    NearLabel valid_global_tag;
    __ testbit(value, exact_log2(JNIHandles::TypeTag::global)); // test for global tag
    __ z_btrue(valid_global_tag);
    __ stop("non global jobject using resolve_global_jobject");
    __ bind(valid_global_tag);
  }
#endif // ASSERT

  // Resolve global handle
  __ access_load_at(T_OBJECT, IN_NATIVE, Address(value, -JNIHandles::TypeTag::global), value, tmp1, tmp2);
  __ verify_oop(value, FILE_AND_LINE);
  __ bind(done);
}

void BarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm, Register jni_env,
                                                        Register obj, Register tmp, Label& slowpath) {
  __ z_nill(obj, ~JNIHandles::tag_mask);
  __ z_lg(obj, 0, obj); // Resolve (untagged) jobject.
}

void BarrierSetAssembler::nmethod_entry_barrier(MacroAssembler* masm) {
  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  __ block_comment("nmethod_entry_barrier (nmethod_entry_barrier) {");

    // Load jump addr:
    __ load_const(Z_R1_scratch, (uint64_t)StubRoutines::method_entry_barrier()); // 2*6 bytes

    // Load value from current java object:
    __ z_lg(Z_R0_scratch, in_bytes(bs_nm->thread_disarmed_guard_value_offset()), Z_thread); // 6 bytes

    // Compare to current patched value:
    __ z_cfi(Z_R0_scratch, /* to be patched */ 0); // 6 bytes (2 + 4 byte imm val)

    // Conditional Jump
    __ z_larl(Z_R14, (Assembler::instr_len((unsigned long)LARL_ZOPC) + Assembler::instr_len((unsigned long)BCR_ZOPC)) / 2); // 6 bytes
    __ z_bcr(Assembler::bcondNotEqual, Z_R1_scratch); // 2 bytes

    // Fall through to method body.
  __ block_comment("} nmethod_entry_barrier (nmethod_entry_barrier)");
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

  _frame_size = align_up(register_save_size, frame::alignment_in_bytes) + frame::z_abi_160_size;

  __ save_return_pc();
  __ push_frame(_frame_size, Z_R14);

  __ z_lg(Z_R14, _z_common_abi(return_pc) + _frame_size, Z_SP);

  iterate_over_register_mask(ACTION_SAVE, _frame_size);
}

SaveLiveRegisters::~SaveLiveRegisters() {
  iterate_over_register_mask(ACTION_RESTORE, _frame_size);

  __ pop_frame();

  __ restore_return_pc();
}

int SaveLiveRegisters::iterate_over_register_mask(IterationAction action, int offset) {
  int reg_save_index = 0;
  RegMaskIterator live_regs_iterator(_reg_mask);

  // Going to preserve the volatile registers which can be used by Register Allocator.
  while(live_regs_iterator.has_next()) {
    const OptoReg::Name opto_reg = live_regs_iterator.next();

    // Filter out stack slots (spilled registers, i.e., stack-allocated registers).
    if (!OptoReg::is_reg(opto_reg)) {
      continue;
    }

    const VMReg vm_reg = OptoReg::as_VMReg(opto_reg);
    if (vm_reg->is_Register()) {
      Register std_reg = vm_reg->as_Register();
      // Z_R0 and Z_R1 will not be allocated by the register allocator, see s390.ad (Integer Register Classes)
      // Z_R6 to Z_R15 are saved registers, except Z_R14 (see Z-Abi)
      if (std_reg->encoding() == Z_R14->encoding() ||
         (std_reg->encoding() >= Z_R2->encoding()  &&
          std_reg->encoding() <= Z_R5->encoding())) {
        reg_save_index++;

        if (action == ACTION_SAVE) {
          __ z_stg(std_reg, offset - reg_save_index * BytesPerWord, Z_SP);
        } else if (action == ACTION_RESTORE) {
          __ z_lg(std_reg, offset - reg_save_index * BytesPerWord, Z_SP);
        } else {
          assert(action == ACTION_COUNT_ONLY, "Sanity");
        }
      }
    } else if (vm_reg->is_FloatRegister()) {
      FloatRegister fp_reg = vm_reg->as_FloatRegister();
      // Z_R1 will not be allocated by the register allocator, see s390.ad (Float Register Classes)
      if (fp_reg->encoding() >= Z_F0->encoding() &&
          fp_reg->encoding() <= Z_F7->encoding() &&
          fp_reg->encoding() != Z_F1->encoding()) {
        reg_save_index++;

        if (action == ACTION_SAVE) {
          __ z_std(fp_reg, offset - reg_save_index * BytesPerWord, Z_SP);
        } else if (action == ACTION_RESTORE) {
          __ z_ld(fp_reg, offset - reg_save_index * BytesPerWord, Z_SP);
        } else {
          assert(action == ACTION_COUNT_ONLY, "Sanity");
        }
      }
    } else if (vm_reg->is_VectorRegister()) {
      VectorRegister vs_reg = vm_reg->as_VectorRegister();
      // Z_V0 to Z_V15 will not be allocated by the register allocator, see s390.ad (reg class z_v_reg)
      if (vs_reg->encoding() >= Z_V16->encoding() &&
          vs_reg->encoding() <= Z_V31->encoding()) {
        reg_save_index += 2;
        if (action == ACTION_SAVE) {
          __ z_vst(vs_reg, Address(Z_SP, offset - reg_save_index * BytesPerWord));
        } else if (action == ACTION_RESTORE) {
          __ z_vl(vs_reg, Address(Z_SP, offset - reg_save_index * BytesPerWord));
        } else {
          assert(action == ACTION_COUNT_ONLY, "Sanity");
        }
      }
    } else {
      fatal("Register type is not known");
    }
  }
  return reg_save_index;
}

#endif // COMPILER2
