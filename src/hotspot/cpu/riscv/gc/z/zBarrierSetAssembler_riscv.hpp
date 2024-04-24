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

#ifndef CPU_RISCV_GC_Z_ZBARRIERSETASSEMBLER_RISCV_HPP
#define CPU_RISCV_GC_Z_ZBARRIERSETASSEMBLER_RISCV_HPP

#include "code/vmreg.hpp"
#include "oops/accessDecorators.hpp"
#ifdef COMPILER1
#include "c1/c1_LIR.hpp"
#endif // COMPILER1
#ifdef COMPILER2
#include "gc/z/c2/zBarrierSetC2.hpp"
#include "opto/optoreg.hpp"
#endif // COMPILER2

#ifdef COMPILER1
class LIR_Address;
class LIR_Assembler;
class LIR_Opr;
class StubAssembler;
class ZLoadBarrierStubC1;
class ZStoreBarrierStubC1;
#endif // COMPILER1

#ifdef COMPILER2
class MachNode;
class Node;
#endif // COMPILER2

const int ZBarrierRelocationFormatLoadBadMask   = 0;
const int ZBarrierRelocationFormatMarkBadMask   = 1;
const int ZBarrierRelocationFormatStoreGoodBits = 2;
const int ZBarrierRelocationFormatStoreBadMask  = 3;

class ZBarrierSetAssembler : public ZBarrierSetAssemblerBase {
public:
  virtual void load_at(MacroAssembler* masm,
                       DecoratorSet decorators,
                       BasicType type,
                       Register dst,
                       Address src,
                       Register tmp1,
                       Register tmp2);

  void store_barrier_fast(MacroAssembler* masm,
                          Address ref_addr,
                          Register rnew_zaddress,
                          Register rnew_zpointer,
                          Register rtmp,
                          bool in_nmethod,
                          bool is_atomic,
                          Label& medium_path,
                          Label& medium_path_continuation) const;

  void store_barrier_medium(MacroAssembler* masm,
                            Address ref_addr,
                            Register rtmp1,
                            Register rtmp2,
                            Register rtmp3,
                            bool is_native,
                            bool is_atomic,
                            Label& medium_path_continuation,
                            Label& slow_path,
                            Label& slow_path_continuation) const;

  virtual void store_at(MacroAssembler* masm,
                        DecoratorSet decorators,
                        BasicType type,
                        Address dst,
                        Register val,
                        Register tmp1,
                        Register tmp2,
                        Register tmp3);

  virtual void arraycopy_prologue(MacroAssembler* masm,
                                  DecoratorSet decorators,
                                  bool is_oop,
                                  Register src,
                                  Register dst,
                                  Register count,
                                  RegSet saved_regs);

  virtual void copy_load_at(MacroAssembler* masm,
                            DecoratorSet decorators,
                            BasicType type,
                            size_t bytes,
                            Register dst,
                            Address src,
                            Register tmp);

  virtual void copy_store_at(MacroAssembler* masm,
                             DecoratorSet decorators,
                             BasicType type,
                             size_t bytes,
                             Address dst,
                             Register src,
                             Register tmp1,
                             Register tmp2,
                             Register tmp3);

  virtual bool supports_rvv_arraycopy();

  virtual void try_resolve_jobject_in_native(MacroAssembler* masm,
                                             Register jni_env,
                                             Register robj,
                                             Register tmp,
                                             Label& slowpath);

  virtual NMethodPatchingType nmethod_patching_type() { return NMethodPatchingType::conc_instruction_and_data_patch; }

  void patch_barrier_relocation(address addr, int format);

  void patch_barriers() {}

#ifdef COMPILER1
  void generate_c1_color(LIR_Assembler* ce, LIR_Opr ref) const;
  void generate_c1_uncolor(LIR_Assembler* ce, LIR_Opr ref) const;

  void generate_c1_load_barrier_test(LIR_Assembler* ce,
                                     LIR_Opr ref) const;
  void generate_c1_load_barrier(LIR_Assembler* ce,
                                LIR_Opr ref,
                                ZLoadBarrierStubC1* stub,
                                bool on_non_strong) const;

  void generate_c1_load_barrier_stub(LIR_Assembler* ce,
                                     ZLoadBarrierStubC1* stub) const;

  void generate_c1_load_barrier_runtime_stub(StubAssembler* sasm,
                                             DecoratorSet decorators) const;

  void generate_c1_store_barrier(LIR_Assembler* ce,
                                 LIR_Address* addr,
                                 LIR_Opr new_zaddress,
                                 LIR_Opr new_zpointer,
                                 ZStoreBarrierStubC1* stub) const;

  void generate_c1_store_barrier_stub(LIR_Assembler* ce,
                                      ZStoreBarrierStubC1* stub) const;

  void generate_c1_store_barrier_runtime_stub(StubAssembler* sasm,
                                              bool self_healing) const;
#endif // COMPILER1

#ifdef COMPILER2
  void generate_c2_load_barrier_stub(MacroAssembler* masm,
                                     ZLoadBarrierStubC2* stub) const;
  void generate_c2_store_barrier_stub(MacroAssembler* masm,
                                      ZStoreBarrierStubC2* stub) const;
#endif // COMPILER2

  void check_oop(MacroAssembler* masm, Register obj, Register tmp1, Register tmp2, Label& error);
};

#endif // CPU_RISCV_GC_Z_ZBARRIERSETASSEMBLER_RISCV_HPP
