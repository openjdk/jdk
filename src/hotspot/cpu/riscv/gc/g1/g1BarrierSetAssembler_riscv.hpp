/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2024, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef CPU_RISCV_GC_G1_G1BARRIERSETASSEMBLER_RISCV_HPP
#define CPU_RISCV_GC_G1_G1BARRIERSETASSEMBLER_RISCV_HPP

#include "asm/macroAssembler.hpp"
#include "gc/shared/modRefBarrierSetAssembler.hpp"
#include "utilities/macros.hpp"

#ifdef COMPILER1
class LIR_Assembler;
#endif
class StubAssembler;
class G1PreBarrierStub;
class G1PostBarrierStub;
class G1PreBarrierStubC2;
class G1PostBarrierStubC2;

class G1BarrierSetAssembler: public ModRefBarrierSetAssembler {
protected:
  void gen_write_ref_array_pre_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                       Register addr, Register count, RegSet saved_regs);
  void gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                        Register start, Register count, Register tmp, RegSet saved_regs);

  void g1_write_barrier_pre(MacroAssembler* masm,
                            Register obj,
                            Register pre_val,
                            Register thread,
                            Register tmp1,
                            Register tmp2,
                            bool tosca_live,
                            bool expand_call);

  void g1_write_barrier_post(MacroAssembler* masm,
                             Register store_addr,
                             Register new_val,
                             Register thread,
                             Register tmp1,
                             Register tmp2);

  virtual void oop_store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                            Address dst, Register val, Register tmp1, Register tmp2, Register tmp3);

public:
#ifdef COMPILER1
  void gen_pre_barrier_stub(LIR_Assembler* ce, G1PreBarrierStub* stub);
  void gen_post_barrier_stub(LIR_Assembler* ce, G1PostBarrierStub* stub);

  void generate_c1_pre_barrier_runtime_stub(StubAssembler* sasm);
  void generate_c1_post_barrier_runtime_stub(StubAssembler* sasm);
#endif

#ifdef COMPILER2
  void g1_write_barrier_pre_c2(MacroAssembler* masm,
                               Register obj,
                               Register pre_val,
                               Register thread,
                               Register tmp1,
                               Register tmp2,
                               G1PreBarrierStubC2* c2_stub);
  void generate_c2_pre_barrier_stub(MacroAssembler* masm,
                                    G1PreBarrierStubC2* stub) const;
  void g1_write_barrier_post_c2(MacroAssembler* masm,
                                Register store_addr,
                                Register new_val,
                                Register thread,
                                Register tmp1,
                                Register tmp2,
                                G1PostBarrierStubC2* c2_stub);
  void generate_c2_post_barrier_stub(MacroAssembler* masm,
                                     G1PostBarrierStubC2* stub) const;
#endif

  void load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
               Register dst, Address src, Register tmp1, Register tmp2);
};

#endif // CPU_RISCV_GC_G1_G1BARRIERSETASSEMBLER_RISCV_HPP
