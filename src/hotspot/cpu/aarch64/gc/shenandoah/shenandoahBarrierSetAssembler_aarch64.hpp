/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

#ifndef CPU_AARCH64_GC_SHENANDOAH_SHENANDOAHBARRIERSETASSEMBLER_AARCH64_HPP
#define CPU_AARCH64_GC_SHENANDOAH_SHENANDOAHBARRIERSETASSEMBLER_AARCH64_HPP

#include "asm/macroAssembler.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#ifdef COMPILER1
class LIR_Assembler;
class ShenandoahPreBarrierStub;
class ShenandoahWriteBarrierStub;
class StubAssembler;
class StubCodeGenerator;
#endif

class ShenandoahBarrierSetAssembler: public BarrierSetAssembler {
private:

  static address _shenandoah_wb;

  void satb_write_barrier_pre(MacroAssembler* masm,
                              Register obj,
                              Register pre_val,
                              Register thread,
                              Register tmp,
                              bool tosca_live,
                              bool expand_call);
  void shenandoah_write_barrier_pre(MacroAssembler* masm,
                                    Register obj,
                                    Register pre_val,
                                    Register thread,
                                    Register tmp,
                                    bool tosca_live,
                                    bool expand_call);

  void read_barrier(MacroAssembler* masm, Register dst);
  void read_barrier_impl(MacroAssembler* masm, Register dst);
  void read_barrier_not_null(MacroAssembler* masm, Register dst);
  void read_barrier_not_null_impl(MacroAssembler* masm, Register dst);
  void write_barrier(MacroAssembler* masm, Register dst);
  void write_barrier_impl(MacroAssembler* masm, Register dst);
  void storeval_barrier(MacroAssembler* masm, Register dst, Register tmp);
  void asm_acmp_barrier(MacroAssembler* masm, Register op1, Register op2);

  address generate_shenandoah_wb(StubCodeGenerator* cgen);

public:
  static address shenandoah_wb();

#ifdef COMPILER1
  void gen_pre_barrier_stub(LIR_Assembler* ce, ShenandoahPreBarrierStub* stub);
  void gen_write_barrier_stub(LIR_Assembler* ce, ShenandoahWriteBarrierStub* stub);
  void generate_c1_pre_barrier_runtime_stub(StubAssembler* sasm);
#endif

  virtual void arraycopy_prologue(MacroAssembler* masm, DecoratorSet decorators, bool is_oop,
                                  Register addr, Register count, RegSet saved_regs);
  virtual void arraycopy_epilogue(MacroAssembler* masm, DecoratorSet decorators, bool is_oop,
                                  Register start, Register end, Register tmp, RegSet saved_regs);
  virtual void load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                       Register dst, Address src, Register tmp1, Register tmp_thread);
  virtual void store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                        Address dst, Register val, Register tmp1, Register tmp2);
  virtual void obj_equals(MacroAssembler* masm, Register src1, Register src2);
  virtual void resolve(MacroAssembler* masm, DecoratorSet decorators, Register obj);
  virtual void tlab_allocate(MacroAssembler* masm, Register obj,
                             Register var_size_in_bytes,
                             int con_size_in_bytes,
                             Register t1,
                             Register t2,
                             Label& slow_case);

  void cmpxchg_oop(MacroAssembler* masm, Register addr, Register expected, Register new_val,
                   bool acquire, bool release, bool weak, bool encode,
                   Register tmp1, Register tmp2, Register tmp3 = rscratch2,
                   Register result = noreg);

  virtual void barrier_stubs_init();
};

#endif // CPU_AARCH64_GC_SHENANDOAH_SHENANDOAHBARRIERSETASSEMBLER_AARCH64_HPP
