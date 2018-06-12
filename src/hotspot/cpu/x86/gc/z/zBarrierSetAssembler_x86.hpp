/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_GC_Z_ZBARRIERSETASSEMBLER_X86_HPP
#define CPU_X86_GC_Z_ZBARRIERSETASSEMBLER_X86_HPP

#ifdef COMPILER1
class LIR_Assembler;
class LIR_OprDesc;
typedef LIR_OprDesc* LIR_Opr;
class StubAssembler;
class ZLoadBarrierStubC1;
#endif // COMPILER1

class ZBarrierSetAssembler : public ZBarrierSetAssemblerBase {
  address _load_barrier_slow_stub[RegisterImpl::number_of_registers];
  address _load_barrier_weak_slow_stub[RegisterImpl::number_of_registers];

public:
  ZBarrierSetAssembler() :
    _load_barrier_slow_stub(),
    _load_barrier_weak_slow_stub() {}

  address load_barrier_slow_stub(Register reg) { return _load_barrier_slow_stub[reg->encoding()]; }
  address load_barrier_weak_slow_stub(Register reg) { return _load_barrier_weak_slow_stub[reg->encoding()]; }

  virtual void load_at(MacroAssembler* masm,
                       DecoratorSet decorators,
                       BasicType type,
                       Register dst,
                       Address src,
                       Register tmp1,
                       Register tmp_thread);

#ifdef ASSERT
  virtual void store_at(MacroAssembler* masm,
                        DecoratorSet decorators,
                        BasicType type,
                        Address dst,
                        Register src,
                        Register tmp1,
                        Register tmp2);
#endif // ASSERT

  virtual void arraycopy_prologue(MacroAssembler* masm,
                                  DecoratorSet decorators,
                                  BasicType type,
                                  Register src,
                                  Register dst,
                                  Register count);

  virtual void try_resolve_jobject_in_native(MacroAssembler* masm,
                                             Register jni_env,
                                             Register obj,
                                             Register tmp,
                                             Label& slowpath);

#ifdef COMPILER1
  void generate_c1_load_barrier_test(LIR_Assembler* ce,
                                     LIR_Opr ref) const;

  void generate_c1_load_barrier_stub(LIR_Assembler* ce,
                                     ZLoadBarrierStubC1* stub) const;

  void generate_c1_load_barrier_runtime_stub(StubAssembler* sasm,
                                             DecoratorSet decorators) const;
#endif // COMPILER1

  virtual void barrier_stubs_init();
};

#endif // CPU_X86_GC_Z_ZBARRIERSETASSEMBLER_X86_HPP
