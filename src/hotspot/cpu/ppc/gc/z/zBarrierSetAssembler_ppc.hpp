/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_PPC_GC_Z_ZBARRIERSETASSEMBLER_PPC_HPP
#define CPU_PPC_GC_Z_ZBARRIERSETASSEMBLER_PPC_HPP

#include "code/vmreg.hpp"
#include "oops/accessDecorators.hpp"
#ifdef COMPILER2
#include "opto/optoreg.hpp"
#endif // COMPILER2

#ifdef COMPILER1
class CodeStub;
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
class ZLoadBarrierStubC2;
class ZStoreBarrierStubC2;
#endif // COMPILER2

const int ZBarrierRelocationFormatLoadBadMask = 0;
const int ZBarrierRelocationFormatMarkBadMask = 1;
const int ZBarrierRelocationFormatStoreGoodBits = 2;
const int ZBarrierRelocationFormatStoreBadMask = 3;

class ZBarrierSetAssembler : public ZBarrierSetAssemblerBase {
public:
  virtual void load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                       Register base, RegisterOrConstant ind_or_offs, Register dst,
                       Register tmp1, Register tmp2,
                       MacroAssembler::PreservationLevel preservation_level, Label *L_handle_null = nullptr);

  virtual void store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                        Register base, RegisterOrConstant ind_or_offs, Register val,
                        Register tmp1, Register tmp2, Register tmp3,
                        MacroAssembler::PreservationLevel preservation_level);

  virtual void arraycopy_prologue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                  Register src, Register dst, Register count,
                                  Register preserve1, Register preserve2);

  virtual void try_resolve_jobject_in_native(MacroAssembler* masm, Register dst, Register jni_env,
                                             Register obj, Register tmp, Label& slowpath);

  virtual void check_oop(MacroAssembler *masm, Register obj, const char* msg);

  virtual NMethodPatchingType nmethod_patching_type() { return NMethodPatchingType::conc_instruction_and_data_patch; }

#ifdef COMPILER1
  void generate_c1_load_barrier_stub(LIR_Assembler* ce,
                                     ZLoadBarrierStubC1* stub) const;

  void generate_c1_load_barrier_runtime_stub(StubAssembler* sasm,
                                             DecoratorSet decorators) const;

  void generate_c1_color(LIR_Assembler* ce, LIR_Opr ref) const;
  void generate_c1_uncolor(LIR_Assembler* ce, LIR_Opr ref) const;

  void generate_c1_load_barrier(LIR_Assembler* ce,
                                LIR_Opr ref,
                                ZLoadBarrierStubC1* stub,
                                bool on_non_strong) const;

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
  void generate_c2_load_barrier_stub(MacroAssembler* masm, ZLoadBarrierStubC2* stub) const;

  void generate_c2_store_barrier_stub(MacroAssembler* masm, ZStoreBarrierStubC2* stub) const;
#endif // COMPILER2

  void store_barrier_fast(MacroAssembler* masm,
                          Register ref_base,
                          RegisterOrConstant ind_or_offset,
                          Register rnew_persistent,
                          Register rnew_transient,
                          bool in_nmethod,
                          bool is_atomic,
                          Label& medium_path,
                          Label& medium_path_continuation) const;

  void store_barrier_medium(MacroAssembler* masm,
                            Register ref_base,
                            RegisterOrConstant ind_or_offs,
                            Register tmp,
                            bool is_atomic,
                            Label& medium_path_continuation,
                            Label& slow_path) const;

  void load_copy_masks(MacroAssembler* masm,
                       Register load_bad_mask,
                       Register store_bad_mask,
                       Register store_good_mask,
                       bool dest_uninitialized) const;
  void copy_load_at_fast(MacroAssembler* masm,
                         Register zpointer,
                         Register addr,
                         Register load_bad_mask,
                         Label& slow_path,
                         Label& continuation) const;
  void copy_load_at_slow(MacroAssembler* masm,
                         Register zpointer,
                         Register addr,
                         Register tmp,
                         Label& slow_path,
                         Label& continuation) const;
  void copy_store_at_fast(MacroAssembler* masm,
                          Register zpointer,
                          Register addr,
                          Register store_bad_mask,
                          Register store_good_mask,
                          Label& medium_path,
                          Label& continuation,
                          bool dest_uninitialized) const;
  void copy_store_at_slow(MacroAssembler* masm,
                          Register addr,
                          Register tmp,
                          Label& medium_path,
                          Label& continuation,
                          bool dest_uninitialized) const;

  void generate_disjoint_oop_copy(MacroAssembler* masm, bool dest_uninitialized);
  void generate_conjoint_oop_copy(MacroAssembler* masm, bool dest_uninitialized);

  void patch_barrier_relocation(address addr, int format);

  void patch_barriers() {}
};

#endif // CPU_PPC_GC_Z_ZBARRIERSETASSEMBLER_PPC_HPP
