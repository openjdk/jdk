/*
 * Copyright (c) 2018, 2022, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2012, 2022 SAP SE. All rights reserved.
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

#ifndef CPU_PPC_GC_SHENANDOAH_SHENANDOAHBARRIERSETASSEMBLER_PPC_HPP
#define CPU_PPC_GC_SHENANDOAH_SHENANDOAHBARRIERSETASSEMBLER_PPC_HPP

#include "asm/macroAssembler.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"

#ifdef COMPILER1

class LIR_Assembler;
class ShenandoahPreBarrierStub;
class ShenandoahLoadReferenceBarrierStub;
class StubAssembler;

#endif

class StubCodeGenerator;

class ShenandoahBarrierSetAssembler: public BarrierSetAssembler {
private:

  /* ==== Actual barrier implementations ==== */
  void satb_barrier_impl(MacroAssembler* masm, DecoratorSet decorators,
                         Register base, RegisterOrConstant ind_or_offs,
                         Register pre_val,
                         Register tmp1, Register tmp2,
                         MacroAssembler::PreservationLevel preservation_level);

  void card_barrier(MacroAssembler* masm,
                    Register base, RegisterOrConstant ind_or_offs,
                    Register tmp);

  void load_reference_barrier_impl(MacroAssembler* masm, DecoratorSet decorators,
                                   Register base, RegisterOrConstant ind_or_offs,
                                   Register dst,
                                   Register tmp1, Register tmp2,
                                   MacroAssembler::PreservationLevel preservation_level);

  /* ==== Helper methods for barrier implementations ==== */
  void resolve_forward_pointer_not_null(MacroAssembler* masm, Register dst, Register tmp);

  void gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                        Register addr, Register count,
                                        Register preserve);

public:
  virtual NMethodPatchingType nmethod_patching_type() { return NMethodPatchingType::conc_instruction_and_data_patch; }

  /* ==== C1 stubs ==== */
#ifdef COMPILER1

  void gen_pre_barrier_stub(LIR_Assembler* ce, ShenandoahPreBarrierStub* stub);

  void gen_load_reference_barrier_stub(LIR_Assembler* ce, ShenandoahLoadReferenceBarrierStub* stub);

  void generate_c1_pre_barrier_runtime_stub(StubAssembler* sasm);

  void generate_c1_load_reference_barrier_runtime_stub(StubAssembler* sasm, DecoratorSet decorators);

#endif

  /* ==== Available barriers (facades of the actual implementations) ==== */
  void satb_barrier(MacroAssembler* masm,
                    Register base, RegisterOrConstant ind_or_offs,
                    Register tmp1, Register tmp2, Register tmp3,
                    MacroAssembler::PreservationLevel preservation_level);

  void load_reference_barrier(MacroAssembler* masm, DecoratorSet decorators,
                              Register base, RegisterOrConstant ind_or_offs,
                              Register dst,
                              Register tmp1, Register tmp2,
                              MacroAssembler::PreservationLevel preservation_level);

  /* ==== Helper methods used by C1 and C2 ==== */
  void cmpxchg_oop(MacroAssembler* masm, Register base_addr, Register expected, Register new_val,
                   Register tmp1, Register tmp2,
                   bool is_cae, Register result);

  /* ==== Access api ==== */
  virtual void arraycopy_prologue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                  Register src, Register dst, Register count,
                                  Register preserve1, Register preserve2);
  virtual void arraycopy_epilogue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                  Register dst, Register count,
                                  Register preserve);

  virtual void store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                        Register base, RegisterOrConstant ind_or_offs, Register val,
                        Register tmp1, Register tmp2, Register tmp3,
                        MacroAssembler::PreservationLevel preservation_level);

  virtual void load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                       Register base, RegisterOrConstant ind_or_offs, Register dst,
                       Register tmp1, Register tmp2,
                       MacroAssembler::PreservationLevel preservation_level, Label* L_handle_null = nullptr);

  virtual void try_resolve_jobject_in_native(MacroAssembler* masm, Register dst, Register jni_env,
                                             Register obj, Register tmp, Label& slowpath);
};

#endif // CPU_PPC_GC_SHENANDOAH_SHENANDOAHBARRIERSETASSEMBLER_PPC_HPP
