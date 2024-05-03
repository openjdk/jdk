/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "interpreter/interp_masm.hpp"
#include "oops/compressedOops.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/macros.hpp"

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

void BarrierSetAssembler::resolve_jobject(MacroAssembler* masm, Register value, Register tmp1, Register tmp2) {
  NearLabel Ldone;
  __ z_ltgr(tmp1, value);
  __ z_bre(Ldone);          // Use null result as-is.

  __ z_nill(value, ~JNIHandles::tag_mask);
  __ z_lg(value, 0, value); // Resolve (untagged) jobject.

  __ verify_oop(value, FILE_AND_LINE);
  __ bind(Ldone);
}

void BarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm, Register jni_env,
                                                        Register obj, Register tmp, Label& slowpath) {
  __ z_nill(obj, ~JNIHandles::tag_mask);
  __ z_lg(obj, 0, obj); // Resolve (untagged) jobject.
}

void BarrierSetAssembler::nmethod_entry_barrier(MacroAssembler* masm) {
  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  if (bs_nm == nullptr) {
    return;
  }

  __ block_comment("nmethod_entry_barrier (nmethod_entry_barrier) {");

    // Load jump addr:
    __ load_const(Z_R1_scratch, (uint64_t)StubRoutines::method_entry_barrier()); // 2*6 bytes

    // Load value from current java object:
    __ z_lg(Z_R0_scratch, in_bytes(bs_nm->thread_disarmed_guard_value_offset()), Z_thread); // 6 bytes

    // Compare to current patched value:
    __ z_cfi(Z_R0_scratch, /* to be patched */ -1); // 6 bytes (2 + 4 byte imm val)

    // Conditional Jump
    __ z_larl(Z_R14, (Assembler::instr_len((unsigned long)LARL_ZOPC) + Assembler::instr_len((unsigned long)BCR_ZOPC)) / 2); // 6 bytes
    __ z_bcr(Assembler::bcondNotEqual, Z_R1_scratch); // 2 bytes

    // Fall through to method body.
  __ block_comment("} nmethod_entry_barrier (nmethod_entry_barrier)");
}

#ifdef COMPILER2

OptoReg::Name BarrierSetAssembler::refine_register(const Node* node, OptoReg::Name opto_reg) {
  Unimplemented(); // This must be implemented to support late barrier expansion.
}

#endif // COMPILER2
